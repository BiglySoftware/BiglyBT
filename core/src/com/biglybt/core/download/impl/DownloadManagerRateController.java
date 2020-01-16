/*
 * Created on May 12, 2010
 * Created by Paul Gardner
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */


package com.biglybt.core.download.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.networkmanager.LimitedRateGroup;
import com.biglybt.core.networkmanager.NetworkManager;
import com.biglybt.core.networkmanager.impl.WriteController;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.PEPeerManagerStats;
import com.biglybt.core.speedmanager.SpeedManager;
import com.biglybt.core.speedmanager.SpeedManagerLimitEstimate;
import com.biglybt.core.speedmanager.SpeedManagerPingMapper;
import com.biglybt.core.util.*;


public class
DownloadManagerRateController
{
	static Core core;
	static SpeedManager		speed_manager;

	static final Map<PEPeerManager,PMState>		pm_map = new HashMap<>();

	static TimerEventPeriodic	timer;

	static final AsyncDispatcher	dispatcher = new AsyncDispatcher( "DMCRateController" );

	static boolean	enable;
	static boolean 	enable_limit_handling;
	static int		slack_bytes_per_sec;

	static{
		COConfigurationManager.addAndFireParameterListeners(
			new String[]{
				"Bias Upload Enable",
				"Bias Upload Handle No Limit",
				"Bias Upload Slack KBs",
			},
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					String parameterName)
				{
					enable				 	= COConfigurationManager.getBooleanParameter( "Bias Upload Enable" );
					enable_limit_handling 	= COConfigurationManager.getBooleanParameter( "Bias Upload Handle No Limit" ) && enable;
					slack_bytes_per_sec		= COConfigurationManager.getIntParameter( "Bias Upload Slack KBs" )*1024;
				}
			});
	}


	static volatile int rate_limit	= 0;

	static final LimitedRateGroup
		limiter =
			new LimitedRateGroup()
			{
				@Override
				public String
				getName()
				{
					return( "DMRC" );
				}

				@Override
				public int
				getRateLimitBytesPerSecond()
				{
					return( rate_limit );
				}

				@Override
				public boolean
				isDisabled()
				{
					return( rate_limit == -1 );
				}

				@Override
				public void
				updateBytesUsed(
					int	used )
				{
				}
			};

	private static final int TIMER_MILLIS			= 1000;

	private static final int WAIT_AFTER_CHOKE_PERIOD	= 10*1000;
	private static final int WAIT_AFTER_CHOKE_TICKS		= WAIT_AFTER_CHOKE_PERIOD/TIMER_MILLIS;

	private static final int DEFAULT_UP_LIMIT	= 250*1024;
	private static final int MAX_UP_DIFF	= 15*1024;
	private static final int MAX_DOWN_DIFF	= 10*1024;
	private static final int MIN_DIFF	= 2*1024;

	private static final int		SAMPLE_COUNT			= 5;
	private static int				sample_num;
	private static double			incomplete_samples;
	private static double			complete_samples;

	private static int	ticks_to_sample_start;

	private static int		last_rate_limit;
	private static double	last_incomplete_average;
	private static double	last_complete_average;
	private static double	last_overall_average;



	private static int	tick_count				= 0;
	private static int	last_tick_processed 	= -1;

	private static long pm_last_bad_limit;
	private static int	latest_choke;
	private static int	wait_until_tick;

	public static String
	getString()
	{
		String result;
		
		if ( enable ){

			result = "reserved=" + DisplayFormatters.formatByteCountToKiBEtcPerSec( slack_bytes_per_sec );

			if ( enable_limit_handling ){

				result += ", limit=" + DisplayFormatters.formatByteCountToKiBEtcPerSec( rate_limit );

				result += 	", last[choke=" + DisplayFormatters.formatByteCountToKiBEtcPerSec( latest_choke ) +
						", ratio=" + DisplayFormatters.formatDecimal(last_incomplete_average/last_complete_average, 2) + "]";

			}
		}else{

			result = "Disabled";
		}
		
		List<WriteController> wcs = NetworkManager.getSingleton().getWriteControllers();
		
		String wc_str = "";
		
		for ( WriteController wc: wcs ){
			
			String str = wc.getBiasDetails();
			
			wc_str += (wc_str.isEmpty()?"":",") + str;
		}
		
		result += " [" + wc_str + "]";
		
		return( result );
	}

	public static void
	addPeerManager(
		final PEPeerManager		pm )
	{
		dispatcher.dispatch(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					if ( core == null ){

						core = CoreFactory.getSingleton();

						speed_manager = core.getSpeedManager();
					}

					boolean	is_complete = !pm.hasDownloadablePiece();

					PEPeerManagerStats pm_stats = pm.getStats();

					long	up_bytes = pm_stats.getTotalDataBytesSentNoLan() + pm_stats.getTotalProtocolBytesSentNoLan();

					if ( is_complete ){

						pm.addRateLimiter( limiter, true );
					}

					pm_map.put( pm, new PMState( pm, is_complete, up_bytes ));

					if ( timer == null ){

						timer =
							SimpleTimer.addPeriodicEvent(
								"DMRC",
								TIMER_MILLIS,
								new TimerEventPerformer()
								{
									@Override
									public void
									perform(
										TimerEvent event )
									{
										dispatcher.dispatch(
											new AERunnable()
											{
												@Override
												public void
												runSupport()
												{
													update();
												}
											});
									}
								});
					}
				}
			});
	}

	public static void
	removePeerManager(
		final PEPeerManager		pm )
	{
		dispatcher.dispatch(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					pm_map.remove( pm );

					if ( pm_map.size() == 0 ){

						timer.cancel();

						timer = null;

						rate_limit = 0;
					}
				}
			});
	}

	static void
	update()
	{
		tick_count++;

		if ( 	(!enable_limit_handling ) ||  pm_map.size() == 0 ||
				NetworkManager.isSeedingOnlyUploadRate() ||  NetworkManager.getMaxUploadRateBPSNormal() != 0 ||
				core == null || speed_manager == null || speed_manager.getSpeedTester() == null ){

			rate_limit = 0;

			return;
		}

		int	num_complete 	= 0;
		int	num_incomplete 	= 0;

		int	num_interesting = 0;

		int i_up_total	= 0;
		int c_up_total	= 0;

		long	mono_now = SystemTime.getMonotonousTime();

		for ( Map.Entry<PEPeerManager, PMState> entry: pm_map.entrySet()){

			PEPeerManager	pm 		= entry.getKey();
			PMState			state 	= entry.getValue();

			boolean	is_complete = !pm.hasDownloadablePiece();

			PEPeerManagerStats pm_stats = pm.getStats();

			long	up_bytes = pm_stats.getTotalDataBytesSentNoLan() + pm_stats.getTotalProtocolBytesSentNoLan();

			long	diff = state.setBytesUp( up_bytes );

			if ( is_complete ){

				num_complete++;

				c_up_total += diff;

			}else{

				num_incomplete++;

				i_up_total += diff;

				if ( state.isInteresting( mono_now )){

					num_interesting++;
				}
			}

			if ( state.isComplete() != is_complete ){

				if ( is_complete ){

					pm.addRateLimiter( limiter, true );

				}else{

					pm.removeRateLimiter( limiter, true );
				}

				state.setComplete( is_complete );
			}
		}

		if ( num_incomplete == 0 || num_complete == 0 || num_interesting == 0 ){

			rate_limit = 0;

			return;
		}

		boolean	skipped_tick = false;

		if ( last_tick_processed != tick_count - 1 ){

			pm_last_bad_limit 	= 0;
			latest_choke		= 0;
			wait_until_tick		= 0;

			ticks_to_sample_start	= 0;
			sample_num				= 0;
			incomplete_samples		= 0;
			complete_samples		= 0;

			skipped_tick = true;
		}

		last_tick_processed = tick_count;

		if ( skipped_tick || tick_count < wait_until_tick ){

			return;
		}

		try{
			long	real_now = SystemTime.getCurrentTime();

			SpeedManagerPingMapper mapper = speed_manager.getActiveMapper();

			if ( rate_limit == 0 ){

				rate_limit = speed_manager.getEstimatedUploadCapacityBytesPerSec().getBytesPerSec();

				if ( rate_limit == 0 ){

					rate_limit = DEFAULT_UP_LIMIT;
				}
			}

			SpeedManagerLimitEstimate last_bad = mapper.getLastBadUploadLimit();

			if ( last_bad != null ){

				int last_bad_limit = last_bad.getBytesPerSec();

				if ( last_bad_limit != pm_last_bad_limit ){

					pm_last_bad_limit = last_bad_limit;

					SpeedManagerLimitEstimate[] bad_ups = mapper.getBadUploadHistory();

					int		total 	= last_bad.getBytesPerSec();
					int		count	= 1;

					for ( SpeedManagerLimitEstimate bad: bad_ups ){

						long	t = bad.getWhen();

						if ( real_now - t <= 30*1000 && bad.getBytesPerSec() != last_bad_limit ){

							total += bad.getBytesPerSec();

							count++;
						}
					}

					latest_choke = total/count;

					int	new_rate_limit;

					if ( rate_limit == 0 ){

						new_rate_limit = latest_choke/2;

					}else{

						new_rate_limit = rate_limit/2;
					}

					if ( new_rate_limit < slack_bytes_per_sec ){

						new_rate_limit = slack_bytes_per_sec;
					}

					rate_limit = new_rate_limit;

					wait_until_tick = tick_count + WAIT_AFTER_CHOKE_TICKS;

					ticks_to_sample_start 	= 0;
					sample_num 				= 0;
					complete_samples		= 0;
					incomplete_samples		= 0;
					last_rate_limit			= 0;

					return;
				}
			}

			if ( ticks_to_sample_start > 0 ){

				ticks_to_sample_start--;

			}else if ( sample_num < SAMPLE_COUNT ){

				complete_samples 	+= c_up_total;
				incomplete_samples	+= i_up_total;

				sample_num++;

			}else{

				double	incomplete_average 	= incomplete_samples / SAMPLE_COUNT;
				double	complete_average 	= complete_samples / SAMPLE_COUNT;
				double	overall_average 	= ( complete_samples + incomplete_samples ) / SAMPLE_COUNT;

				int	action = -1;

				try{

					if ( last_rate_limit == 0 ){

						action = 1;

					}else{

						double overall_change = overall_average - last_overall_average;

						if ( overall_change < 0 ){

							if ( rate_limit < last_rate_limit ){

								// System.out.println( "average decreased" );

								action = 1;

							}else{

								action = 0;
							}
						}else{

							double last_ratio 	= last_incomplete_average / last_complete_average;
							double ratio		= incomplete_average / complete_average;

							// System.out.println( "rate=" + rate_limit + "/" + last_rate_limit + ", ratio=" + ratio + "/" + last_ratio );

							if ( rate_limit < last_rate_limit && ratio >= last_ratio ){

								action = -1;

							}else if ( rate_limit > last_rate_limit && ratio <= last_ratio ){

								double i_up_change = incomplete_average - last_incomplete_average;

								if ( i_up_change >= 1024 ){

									action = -1;

								}else{

									action = 1;
								}

							}else{

								action = 1;
							}
						}
					}

				}finally{

					int	new_rate_limit;

					if ( action > 0 ){

						int	ceiling = latest_choke==0?DEFAULT_UP_LIMIT:latest_choke;

						int	diff = ( ceiling - rate_limit )/4;

						if ( diff > MAX_UP_DIFF ){

							diff = MAX_UP_DIFF;

						}else if ( diff < MIN_DIFF ){

							diff = MIN_DIFF;
						}

						new_rate_limit = rate_limit + diff;

						if ( new_rate_limit > 100*1024*1024 ){

							new_rate_limit = 100*1024*1024;
						}
					}else if ( action < 0 ){

						int	diff = rate_limit/5;

						if ( diff > MAX_DOWN_DIFF ){

							diff = MAX_DOWN_DIFF;

						}else if ( diff < MIN_DIFF ){

							diff = MIN_DIFF;
						}

						new_rate_limit = rate_limit - diff;

						if ( new_rate_limit < slack_bytes_per_sec ){

							new_rate_limit = slack_bytes_per_sec;
						}
					}else{

						new_rate_limit = rate_limit;
					}

					last_rate_limit			= rate_limit;
					last_overall_average 	= overall_average;
					last_complete_average	= complete_average;
					last_incomplete_average	= incomplete_average;

					rate_limit = new_rate_limit;

					sample_num = 0;
					complete_samples	= 0;
					incomplete_samples	= 0;
				}
			}

		}finally{

			// System.out.println( "rate=" + DisplayFormatters.formatByteCountToKiBEtcPerSec( rate_limit ) + ", last_choke=" + latest_choke );
		}
	}

	private static class
	PMState
	{
		final private PEPeerManager	manager;

		private boolean			complete;
		private long			bytes_up;

		private boolean			interesting;
		private long			last_interesting_calc;

		PMState(
			PEPeerManager	_manager,
			boolean			_complete,
			long			_bytes_up )
		{
			manager		= _manager;
			complete 	= _complete;
			bytes_up	= _bytes_up;
		}

		boolean
		isComplete()
		{
			return( complete );
		}

		void
		setComplete(
			boolean	c )
		{
			complete = c;
		}

		long
		setBytesUp(
			long	b )
		{
			long diff = b - bytes_up;

			bytes_up = b;

			return( diff );
		}

		boolean
		isInteresting(
			long		now )
		{
			boolean	calc;

			if ( last_interesting_calc == 0 ){

				calc = true;

			}else if ( !interesting ){

				calc = now - last_interesting_calc >= 5*1000;

			}else{

				calc = now - last_interesting_calc >= 60*1000;
			}

			if ( calc ){

				last_interesting_calc = now;

				PEPeerManagerStats stats = manager.getStats();

					// not interesting if stalled downloading

				long dl_rate = stats.getDataReceiveRate();

				if ( dl_rate < 5*1024 ){

					interesting = false;

				}else{

						// not interesting if we have nobody to seed to!

					if ( manager.getNbPeersUnchoked() < 3 ){

						interesting = false;

					}else{

							// see if the download has a manually imposed upload limit and if
							// we are close to it

						int limit = manager.getEffectiveUploadRateLimitBytesPerSecond();

						if ( 	limit > 0 &&
								( stats.getDataSendRate() + stats.getProtocolSendRate() ) >= ( limit - (5*1024))){

							interesting = false;

						}else{

							interesting = true;
						}
					}
				}
			}

			return( interesting );
		}
	}
}
