/*
 * Created on May 7, 2007
 * Created by Paul Gardner
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
 *
 */


package com.biglybt.core.speedmanager.impl.v1;

import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.speedmanager.SpeedManagerPingSource;
import com.biglybt.core.speedmanager.impl.SpeedManagerAlgorithmProvider;
import com.biglybt.core.speedmanager.impl.SpeedManagerAlgorithmProviderAdapter;
import com.biglybt.core.util.average.Average;
import com.biglybt.core.util.average.AverageFactory;

public class
SpeedManagerAlgorithmProviderV1
	implements SpeedManagerAlgorithmProvider
{
	private static final String	CONFIG_MIN_UP			= "AutoSpeed Min Upload KBs";
	private static final String	CONFIG_MAX_UP			= "AutoSpeed Max Upload KBs";
	private static final String	CONFIG_MAX_INC			= "AutoSpeed Max Increment KBs";
	private static final String	CONFIG_MAX_DEC			= "AutoSpeed Max Decrement KBs";
	private static final String	CONFIG_CHOKE_PING		= "AutoSpeed Choking Ping Millis";
	private static final String	CONFIG_DOWNADJ_ENABLE	= "AutoSpeed Download Adj Enable";
	private static final String	CONFIG_DOWNADJ_RATIO	= "AutoSpeed Download Adj Ratio";
	private static final String	CONFIG_LATENCY_FACTOR	= "AutoSpeed Latency Factor";
	private static final String	CONFIG_FORCED_MIN		= "AutoSpeed Forced Min KBs";

	private static int					PING_CHOKE_TIME;
	private static int					MIN_UP;
	private static int					MAX_UP;
	private static boolean				ADJUST_DOWNLOAD_ENABLE;
	private static float				ADJUST_DOWNLOAD_RATIO;
	private static int					MAX_INCREMENT;
	private static int					MAX_DECREMENT;
	private static int					LATENCY_FACTOR;
	private static int					FORCED_MIN_SPEED;

	private static final String[]	CONFIG_PARAMS = {
		CONFIG_MIN_UP, CONFIG_MAX_UP,
		CONFIG_MAX_INC, CONFIG_MAX_DEC,
		CONFIG_CHOKE_PING,
		CONFIG_DOWNADJ_ENABLE,
		CONFIG_DOWNADJ_RATIO,
		CONFIG_LATENCY_FACTOR,
		CONFIG_FORCED_MIN };


	static{
		COConfigurationManager.addAndFireParameterListeners(
				CONFIG_PARAMS,
				new ParameterListener()
				{
					@Override
					public void
					parameterChanged(
						String parameterName )
					{
						PING_CHOKE_TIME	= COConfigurationManager.getIntParameter( CONFIG_CHOKE_PING );
						MIN_UP			= COConfigurationManager.getIntParameter( CONFIG_MIN_UP ) * 1024;
						MAX_UP			= COConfigurationManager.getIntParameter( CONFIG_MAX_UP ) * 1024;
						MAX_INCREMENT	= COConfigurationManager.getIntParameter( CONFIG_MAX_INC ) * 1024;
						MAX_DECREMENT	= COConfigurationManager.getIntParameter( CONFIG_MAX_DEC ) * 1024;
						ADJUST_DOWNLOAD_ENABLE	= COConfigurationManager.getBooleanParameter( CONFIG_DOWNADJ_ENABLE );
						String	str 	= COConfigurationManager.getStringParameter( CONFIG_DOWNADJ_RATIO );
						LATENCY_FACTOR	= COConfigurationManager.getIntParameter( CONFIG_LATENCY_FACTOR );

						if ( LATENCY_FACTOR < 1 ){
							LATENCY_FACTOR = 1;
						}

						FORCED_MIN_SPEED	= COConfigurationManager.getIntParameter( CONFIG_FORCED_MIN ) * 1024;

						if ( FORCED_MIN_SPEED < 1024 ){
							FORCED_MIN_SPEED = 1024;
						}

						try{
							ADJUST_DOWNLOAD_RATIO = Float.parseFloat(str);
						}catch( Throwable e ){
						}
					}
				});

	}
	private static final int UNLIMITED	= Integer.MAX_VALUE;

	private static final int	MODE_RUNNING	= 0;
	private static final int	MODE_FORCED_MIN	= 1;
	private static final int	MODE_FORCED_MAX	= 2;

	private static final int	FORCED_MAX_TICKS	= 30;

	private static final int	FORCED_MIN_TICKS		= 60;			// time we'll force low upload to get baseline
	private static final int	FORCED_MIN_AT_START_TICK_LIMIT	= 60;	// how long we'll wait on start up before forcing min

	private static final int	PING_AVERAGE_HISTORY_COUNT	= 5;

	private static final int	IDLE_UPLOAD_SPEED		= 5*1024;		// speed at which upload is treated as "idle"
	private static final int	INITIAL_IDLE_AVERAGE	= 100;
	private static final int	MIN_IDLE_AVERAGE		= 50;		// any lower than this and small ping variations cause overreaction

	private static final int	INCREASING	= 1;
	private static final int	DECREASING	= 2;

	private final SpeedManagerAlgorithmProviderAdapter	adapter;


	private final Average upload_average				= AverageFactory.MovingImmediateAverage( 5 );
	private final Average upload_short_average		= AverageFactory.MovingImmediateAverage( 2 );
	private final Average upload_short_prot_average	= AverageFactory.MovingImmediateAverage( 2 );

	private final Average	ping_average_history		= AverageFactory.MovingImmediateAverage(PING_AVERAGE_HISTORY_COUNT);

	private final Average choke_speed_average			= AverageFactory.MovingImmediateAverage( 3 );

	private Map							ping_sources;
	private volatile int				replacement_contacts;

	private int					mode;
	private volatile int		mode_ticks;
	private int					saved_limit;

	private int		direction;
	private int		ticks;
	private int		idle_ticks;
	private int		idle_average;
	private boolean	idle_average_set;

	private int		max_ping;

	private int		max_upload_average;

	public
	SpeedManagerAlgorithmProviderV1(
		SpeedManagerAlgorithmProviderAdapter	_adapter )
	{
		adapter	= _adapter;
	}

    @Override
    public void
    destroy()
    {
    }

	@Override
	public void
	updateStats()
	{
		int	current_protocol_speed 	= adapter.getCurrentProtocolUploadSpeed();
		int	current_data_speed		= adapter.getCurrentDataUploadSpeed();

		int	current_speed = current_protocol_speed + current_data_speed;

		upload_average.update( current_speed );

		upload_short_average.update( current_speed );

		upload_short_prot_average.update( current_protocol_speed );

		mode_ticks++;

		ticks++;
	}

	@Override
	public void
	reset()
	{
		ticks					= 0;
		mode					= MODE_RUNNING;
		mode_ticks				= 0;
		idle_ticks				= 0;
		idle_average			= INITIAL_IDLE_AVERAGE;
		idle_average_set		= false;
		max_upload_average		= 0;
		direction				= INCREASING;
		max_ping				= 0;
		replacement_contacts	= 0;

		ping_sources			= new HashMap();

		choke_speed_average.reset();
		upload_average.reset();
		upload_short_average.reset();
		upload_short_prot_average.reset();
		ping_average_history.reset();
	}

	@Override
	public void
	pingSourceFound(
		SpeedManagerPingSource		source,
		boolean						is_replacement )
	{
		if ( is_replacement ){

			replacement_contacts++;
		}

		synchronized( ping_sources ){

			ping_sources.put( source, new pingSource( source ));
		}
	}

	@Override
	public void
	pingSourceFailed(
		SpeedManagerPingSource		source )
	{
		synchronized( ping_sources ){

			ping_sources.remove( source );
		}
	}

	@Override
	public void
	calculate(
		SpeedManagerPingSource[]	sources )
	{
		int	min_rtt	= UNLIMITED;

		for (int i=0;i<sources.length;i++){

			int	rtt =  sources[i].getPingTime();

			if ( rtt >= 0 && rtt < min_rtt ){

				min_rtt	= rtt;
			}
		}

		String	str = "";

		int	ping_total		= 0;
		int	ping_count		= 0;

		for (int i=0;i<sources.length;i++){

			pingSource	ps;

			synchronized( ping_sources ){

				ps = (pingSource)ping_sources.get( sources[i] );
			}

			int	rtt =  sources[i].getPingTime();

			str += (i==0?"":",") + rtt;

				// discount anything 5*min reported unless min is really small, in which case round
				// up as we're only trying to catch badly behaved ones

			if ( ps != null ){

				boolean	good_ping =  rtt < 5 * Math.max( min_rtt, 75 );

				ps.pingReceived( rtt, good_ping );

				if ( !good_ping ){

					rtt = -1;
				}
			}

			if ( rtt != -1 ){

				ping_total += rtt;

				ping_count++;
			}
		}

		if ( ping_count == 0 ){

				// all failed

			return;
		}

		int	ping_average = ping_total/ping_count;

			// bias towards min

		ping_average = ( ping_average + min_rtt ) / 2;

		int	running_average = (int)ping_average_history.update( ping_average );

		if ( ping_average > max_ping ){

			max_ping	= ping_average;
		}

		int	up_average = (int)upload_average.getAverage();

			// if we're uploading slowly or the current ping rate is better than our current idle average
			// then we count this towards establishing the baseline

		if ( up_average <= IDLE_UPLOAD_SPEED || ( running_average < idle_average && !idle_average_set )){

			idle_ticks++;

			if ( idle_ticks >= PING_AVERAGE_HISTORY_COUNT ){

				idle_average	= Math.max( running_average, MIN_IDLE_AVERAGE );

				log( "New idle average: " + idle_average );

				idle_average_set	= true;
			}
		}else{

			if ( up_average > max_upload_average ){

				max_upload_average	= up_average;

				log( "New max upload:" +  max_upload_average );
			}

			idle_ticks	= 0;

		}

		if ( idle_average_set && running_average < idle_average ){

				// bump down if we happen to come across lower idle values

			idle_average	= Math.max( running_average, MIN_IDLE_AVERAGE );
		}

		int	current_speed 	= adapter.getCurrentDataUploadSpeed() + adapter.getCurrentProtocolUploadSpeed();
		int	current_limit	= adapter.getCurrentUploadLimit();

		int	new_limit	= current_limit;

		log(
				"Pings: " + str + ", average=" + ping_average +", running_average=" + running_average +
				",idle_average=" + idle_average + ", speed=" + current_speed + ",limit=" + current_limit +
				",choke = " + (int)choke_speed_average.getAverage());



		if ( mode == MODE_FORCED_MAX ){

			if ( mode_ticks > FORCED_MAX_TICKS ){

				mode		= MODE_RUNNING;

				current_limit = new_limit	= saved_limit;
			}

		}else if ( mode == MODE_FORCED_MIN ){

			if ( idle_average_set || mode_ticks > FORCED_MIN_TICKS ){

				log( "Mode -> running" );

				if ( !idle_average_set ){

					idle_average	= Math.max( running_average, MIN_IDLE_AVERAGE );

					idle_average_set	= true;
				}

				mode		= MODE_RUNNING;
				mode_ticks	= 0;

				current_limit = new_limit	= saved_limit;

			}else if ( mode_ticks == 5 ){

					// we've had 5 secs of min up speed, clear out the ping average now
					// to get accurate times

				ping_average_history.reset();
			}
		}

		if ( mode == MODE_RUNNING ){

			if (	( ticks > FORCED_MIN_AT_START_TICK_LIMIT && !idle_average_set ) ||
					( replacement_contacts >= 2 && idle_average_set )){

					// we've been running a while but no min set, or we've got some new untested
					// contacts - force it

				log( "Mode -> forced min" );

				mode		= MODE_FORCED_MIN;
				mode_ticks	= 0;
				saved_limit	= current_limit;

				idle_average_set	= false;
				idle_ticks			= 0;
				replacement_contacts= 0;

				new_limit	= FORCED_MIN_SPEED;

			}else{

				int	short_up = (int)upload_short_average.getAverage();

				int	choke_speed = (int)choke_speed_average.getAverage();

				int choke_time 		= PING_CHOKE_TIME;
				int	latency_factor	= LATENCY_FACTOR;


				if ( running_average < 2* idle_average && ping_average < choke_time ){

					direction = INCREASING;

					int	diff = running_average - idle_average;

					if ( diff < 100 ){

						diff = 100;
					}

					int	increment = 1024 * ( diff / latency_factor );

						// if we're close to the last choke-speed then decrease increments

					int	max_inc	= MAX_INCREMENT;

					if ( new_limit + 2*1024 > choke_speed ){

						max_inc = 1024;

					}else if ( new_limit + 5*1024 > choke_speed ){

						max_inc += 3*1024;
					}

					new_limit += Math.min( increment, max_inc );

				}else if ( ping_average > 4*idle_average || ping_average > choke_time ){

					if ( direction == INCREASING ){

						if ( idle_average_set ){

							choke_speed_average.update( short_up );
						}
					}

					direction = DECREASING;

					int decrement = 1024 * (( ping_average - (3*idle_average )) / latency_factor );

					new_limit -= Math.min( decrement, MAX_DECREMENT );

						// don't drop below the current protocol upload speed. This is to address
						// the situation whereby it is downloading that is choking the line - killing
						// protocol upspeed kills the downspeed

					if ( new_limit < upload_short_prot_average.getAverage() + 1024 ){

						new_limit = (int)upload_short_prot_average.getAverage() + 1024;
					}
				}

				if ( new_limit < 1024 ){

					new_limit	= 1024;
				}
			}

				// final tidy up

			int	min_up	= MIN_UP;
			int	max_up	= MAX_UP;

			if ( min_up > 0 && new_limit < min_up && mode != MODE_FORCED_MIN  ){

				new_limit = min_up;

			}else if ( max_up > 0 &&  new_limit > max_up && mode != MODE_FORCED_MAX ){

				new_limit = max_up;
			}

				// if we're not achieving the current limit and the advice is to increase it, don't
				// bother

			if ( new_limit > current_limit && current_speed < ( current_limit - 10*1024 )){

				new_limit = current_limit;
			}
		}


			// round limit up to nearest K

		new_limit = (( new_limit + 1023 )/1024) * 1024;

		adapter.setCurrentUploadLimit( new_limit );

		if ( ADJUST_DOWNLOAD_ENABLE && !( Float.isInfinite( ADJUST_DOWNLOAD_RATIO ) || Float.isNaN( ADJUST_DOWNLOAD_RATIO ))){

			int	dl_limit = (int)(new_limit * ADJUST_DOWNLOAD_RATIO);

			adapter.setCurrentDownloadLimit( dl_limit );
		}
	}

	@Override
	public int
	getIdlePingMillis()
	{
		return( idle_average );
	}

	@Override
	public int
	getCurrentPingMillis()
	{
		return( (int)ping_average_history.getAverage());
	}

	@Override
	public int
	getMaxPingMillis()
	{
		return( max_ping );
	}

		/**
		 * Returns the current view of when choking occurs
		 * @return speed in bytes/sec
		 */

	@Override
	public int
	getCurrentChokeSpeed()
	{
		return((int)choke_speed_average.getAverage());
	}

	@Override
	public int
	getMaxUploadSpeed()
	{
		return( max_upload_average );
	}

	@Override
	public boolean
	getAdjustsDownloadLimits()
	{
		return( ADJUST_DOWNLOAD_ENABLE );
	}

	protected void
	log(
		String		str )
	{
		adapter.log( str );
	}

	protected static class
	pingSource
	{
		private final SpeedManagerPingSource	source;

		private int		last_good_ping;
		private int		bad_pings;

		protected
		pingSource(
			SpeedManagerPingSource	_source )
		{
			source	= _source;
		}

		public void
		pingReceived(
			int		time,
			boolean	good_ping )
		{
			if ( good_ping ){

				bad_pings = 0;

				last_good_ping	= time;

			}else{

				bad_pings++;
			}

				// three strikes and you're out!

			if ( bad_pings == 3 ){

				source.destroy();
			}
		}
	}
}
