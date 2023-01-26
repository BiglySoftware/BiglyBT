/*
 * Created on Feb 1, 2013
 * Created by Paul Gardner
 *
 * Copyright 2013 Azureus Software, Inc.  All rights reserved.
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


package com.biglybt.core.stats.transfer.impl;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

import com.biglybt.core.Core;
import com.biglybt.core.CoreComponent;
import com.biglybt.core.CoreLifecycleAdapter;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.dht.DHT;
import com.biglybt.core.dht.transport.DHTTransportStats;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerStats;
import com.biglybt.core.stats.transfer.LongTermStatsListener;
import com.biglybt.core.stats.transfer.StatsFactory;
import com.biglybt.core.stats.transfer.impl.LongTermStatsWrapper.LongTermStatsWrapperHelper;
import com.biglybt.core.util.*;
import com.biglybt.core.util.average.Average;
import com.biglybt.core.util.average.AverageFactory;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.PluginManager;
import com.biglybt.plugin.dht.DHTPlugin;

public class
LongTermStatsImpl
	implements LongTermStatsWrapperHelper
{
	private static final int VERSION = 1;

	private static final long MIN_IN_MILLIS		= 60*1000;
	private static final long HOUR_IN_MILLIS	= 60*60*1000;
	private static final long DAY_IN_MILLIS		= 24*60*60*1000;
	private static final long WEEK_IN_MILLIS	= 7*24*60*60*1000;

	public static final int RT_SESSION_START	= 1;
	public static final int RT_SESSION_STATS	= 2;
	public static final int RT_SESSION_END		= 3;

	private Core core;
	private GlobalManagerStats	gm_stats;
	private DHT[]				dhts;

	private static final int STAT_ENTRY_COUNT	= 6;

		// totals at start of session

	private long	st_p_sent;
	private long	st_d_sent;
	private long	st_p_received;
	private long	st_d_received;
	private long	st_dht_sent;
	private long	st_dht_received;

		// session offsets at start of session

	private long	ss_p_sent;
	private long	ss_d_sent;
	private long	ss_p_received;
	private long	ss_d_received;
	private long	ss_dht_sent;
	private long	ss_dht_received;

	private final long[] line_stats_prev = new long[STAT_ENTRY_COUNT];

	private final Average[] stat_averages = new Average[STAT_ENTRY_COUNT];

	{
		for ( int i=0;i<STAT_ENTRY_COUNT;i++){

			stat_averages[i] = AverageFactory.MovingImmediateAverage( 3 );
		}
	}

	private boolean				active;
	private boolean				closing;

	private TimerEventPeriodic	event;
	private PrintWriter			writer;
	private String				writer_rel_file;

	private DayCache			day_cache;

	private static final int MONTH_CACHE_MAX = 3;

	private final Map<String,MonthCache>	month_cache_map =
		new LinkedHashMap<String,MonthCache>(MONTH_CACHE_MAX,0.75f,true)
		{
			@Override
			protected boolean
			removeEldestEntry(
		   		Map.Entry<String,MonthCache> eldest)
			{
				return size() > MONTH_CACHE_MAX;
			}
		};

	private static final SimpleDateFormat	debug_utc_format 	= new SimpleDateFormat( "yyyy,MM,dd:HH:mm" );
	private static final SimpleDateFormat	utc_date_format 	= new SimpleDateFormat( "yyyy,MM,dd" );

	static{
		debug_utc_format.setTimeZone( TimeZone.getTimeZone( "UTC" ));
		utc_date_format.setTimeZone( TimeZone.getTimeZone( "UTC" ));
	}

	private final File stats_dir;

	private long	session_total;

	private final CopyOnWriteList<Object[]>	listeners = new CopyOnWriteList<>();

	private final AsyncDispatcher	dispatcher = new AsyncDispatcher( "lts", 5000 );

	private int	start_of_week 	= -1;
	private int start_of_month	= -1;

	private volatile boolean	destroyed;

	private
	LongTermStatsImpl(
		File	_stats_dir )
	{
		stats_dir = _stats_dir;
	}

	public
	LongTermStatsImpl(
		Core _core,
		GlobalManagerStats	_gm_stats )
	{
		core		= _core;
		gm_stats 	= _gm_stats;

		stats_dir		= FileUtil.getUserFile( "stats" );

		COConfigurationManager.addParameterListener(
			"long.term.stats.enable",
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					String name)
				{
					if ( destroyed ){

						COConfigurationManager.removeParameterListener( "long.term.stats.enable", this );

						return;
					}

					boolean	enabled = COConfigurationManager.getBooleanParameter( name );

					synchronized( LongTermStatsImpl.this ){

						if ( enabled ){

							if ( !active ){

								sessionStart();
							}
						}else{

							if ( active ){

								sessionEnd();
							}
						}
					}
				}
			});

	    _core.addLifecycleListener(
	        	new CoreLifecycleAdapter()
	        	{
	        		@Override
			        public void
	        		componentCreated(
	        			Core core,
	        			CoreComponent component )
	        		{
	        			if ( destroyed ){

	        				core.removeLifecycleListener( this );

	        				return;
	        			}

	        			if ( component instanceof GlobalManager ){

	        				synchronized( LongTermStatsImpl.this ){

	        					sessionStart();
	        				}
	        			}
	        		}

	        		@Override
			        public void
	        		stopped(
	        			Core core )
	        		{
	        			if ( destroyed ){

	        				core.removeLifecycleListener( this );

	        				return;
	        			}

	        			synchronized( LongTermStatsImpl.this ){

	        				closing	= true;

	        				if ( active ){

	        					sessionEnd();
	        				}
	        			}
	        		}
	        	});
	}

	@Override
	public boolean
	isEnabled()
	{
		synchronized( this ){

			return( active );
		}
	}

	@Override
	public void
	reset()
	{
		Debug.out( "eh?" );
	}

	@Override
	public void
	destroyAndDeleteData()
	{
		synchronized( this ){

			destroyed = true;

			if ( writer != null ){

				writer.close();

				writer = null;
			}

			File[] files = stats_dir.listFiles();

outer:
			for ( File file: files ){

				String name = file.getName();

				if ( name.length() == 4 && Character.isDigit( name.charAt(0))){

					for ( int i=0;i<4;i++){

						if ( FileUtil.recursiveDeleteNoCheck( file )){

							continue outer;
						}

						try{
							Thread.sleep( 250 );

						}catch( Throwable e ){
						}
					}

					Debug.out( "Failed to delete " + file );
				}
			}
		}
	}

	private DHT[]
	getDHTs()
	{
	    if ( dhts == null ){

		    try{
		    	PluginManager pm = core.getPluginManager();

		    	if ( pm.isInitialized()){

			        PluginInterface dht_pi = pm.getPluginInterfaceByClass( DHTPlugin.class );

			        if ( dht_pi == null ){

			        	dhts = new DHT[0];

			        }else{

			        	DHTPlugin plugin = (DHTPlugin)dht_pi.getPlugin();

			        	if ( !plugin.isInitialising()){

				        	if ( plugin.isEnabled()){

				        		dhts = ((DHTPlugin)dht_pi.getPlugin()).getDHTs();

				        	}else{

				        		dhts = new DHT[0];
				        	}
			        	}
			        }
		    	}
		    }catch( Throwable e ){

		    	dhts = new DHT[0];
		    }
	    }

	    return( dhts );
	}

	private void
	sessionStart()
	{
		OverallStatsImpl stats = (OverallStatsImpl)StatsFactory.getStats();

		synchronized( this ){

			if ( closing ){

				return;
			}

			boolean	enabled = COConfigurationManager.getBooleanParameter( "long.term.stats.enable" );

			if ( active || !enabled ){

				return;
			}

			active = true;

			long[] snap = stats.getLastSnapshot();

			ss_d_received 	= gm_stats.getTotalDataBytesReceived();
			ss_p_received 	= gm_stats.getTotalProtocolBytesReceived();

			ss_d_sent		= gm_stats.getTotalDataBytesSent();
			ss_p_sent		= gm_stats.getTotalProtocolBytesSent();

			ss_dht_sent		= 0;
			ss_dht_received	= 0;

			if ( core.isStarted()){

				DHT[]	dhts = getDHTs();

				if ( dhts != null ){

					for ( DHT dht: dhts ){

				    	DHTTransportStats dht_stats = dht.getTransport().getStats();

				    	ss_dht_sent 		+= dht_stats.getBytesSent();
				    	ss_dht_received 	+= dht_stats.getBytesReceived();
					}
				}
			}

			st_p_sent 		= snap[0] + ( ss_p_sent - snap[6]);
			st_d_sent 		= snap[1] + ( ss_d_sent - snap[7]);
			st_p_received 	= snap[2] + ( ss_p_received - snap[8]);
			st_d_received 	= snap[3] + ( ss_d_received - snap[9]);
			st_dht_sent		= snap[4] + ( ss_dht_sent - snap[10]);
			st_dht_received = snap[5] + ( ss_dht_received - snap[11]);

			write( 	RT_SESSION_START,
					new long[]{
						st_p_sent,
						st_d_sent,
						st_p_received,
						st_d_received,
						st_dht_sent,
						st_dht_received });

			if ( event == null ){	// should always be null but hey ho

			    event =
			    	SimpleTimer.addPeriodicEvent(
				    	"LongTermStats",
				    	MIN_IN_MILLIS,
				    	new TimerEventPerformer()
				    	{
				    		@Override
						    public void
				    		perform(TimerEvent event)
				    		{
				    			if ( destroyed ){

				    				event.cancel();

				    				return;
				    			}

				    			updateStats();
				    		}
				    	});
			}
		}
	}

	private void
	sessionEnd()
	{
		synchronized( this ){

			if ( !active ){

				return;
			}

			updateStats( RT_SESSION_END );

			active = false;

			if ( event != null ){

				event.cancel();

				event = null;
			}
		}
	}

	private void
	updateStats()
	{
		updateStats( RT_SESSION_STATS );
	}

	private void
	updateStats(
		int	record_type )
	{
	    long	current_d_received 	= gm_stats.getTotalDataBytesReceived();
	    long	current_p_received 	= gm_stats.getTotalProtocolBytesReceived();

	    long	current_d_sent		= gm_stats.getTotalDataBytesSent();
	    long	current_p_sent		= gm_stats.getTotalProtocolBytesSent();

		long	current_dht_sent		= 0;
		long	current_dht_received	= 0;

		DHT[]	dhts = getDHTs();

		if ( dhts != null ){

			for ( DHT dht: dhts ){

		    	DHTTransportStats dht_stats = dht.getTransport().getStats();

		    	current_dht_sent 		+= dht_stats.getBytesSent();
		    	current_dht_received 	+= dht_stats.getBytesReceived();
			}
		}

	    write(	record_type,
	    		new long[]{
		    		( current_p_sent - ss_p_sent ),
		    		( current_d_sent - ss_d_sent ),
		    		( current_p_received - ss_p_received ),
		    		( current_d_received - ss_d_received ),
		    		( current_dht_sent - ss_dht_sent ),
		    		( current_dht_received - ss_dht_received )});
	}

	private void
	write(
		int		record_type,
		long[]	line_stats )
	{
		synchronized( this ){

			if ( destroyed ){

				return;
			}

			try{
				final long	now = SystemTime.getCurrentTime();

				final long	now_mins = now/(1000*60);

				String[] bits = utc_date_format.format( new Date( now )).split( "," );

				String	year 	= bits[0];
				String	month 	= bits[1];
				String	day 	= bits[2];

				String	current_rel_file = year + File.separator + month + File.separator + day + ".dat";

				String line;

				String stats_str = "";

				if ( record_type == RT_SESSION_START ){

						// absolute values

					for ( int i=0;i<line_stats.length;i++ ){

						stats_str += "," + line_stats[i];

						line_stats_prev[i] = 0;
					}

					day_cache = null;

				}else{

						// relative values

					long[]	diffs = new long[STAT_ENTRY_COUNT];

					for ( int i=0;i<line_stats.length;i++ ){

						long diff = line_stats[i] - line_stats_prev[i];

						session_total += diff;

						diffs[i] = diff;

						stats_str += "," + diff;

						line_stats_prev[i] = line_stats[i];

						stat_averages[i].update( diff );
					}

					if ( day_cache != null ){

						if ( day_cache.isForDay( year, month, day )){

							day_cache.addRecord( now_mins, diffs );
						}
					}
				}

				if ( record_type != RT_SESSION_STATS ){

					line = (record_type==RT_SESSION_START?"s,":"e,") + VERSION + "," + now_mins + stats_str;

				}else{

					line = stats_str.substring(1);
				}


				if ( writer == null || !writer_rel_file.equals( current_rel_file )){

						// first open of a file or file switch

					if ( writer != null ){

							// file switch

						if ( record_type != RT_SESSION_START ){

							writer.println( line );
						}

						writer.close();

						if ( writer.checkError()){

							writer 			= null;

							throw( new IOException( "Write failed" ));
						}

						writer 			= null;
					}

						// no point in opening a new file just to record the session-end

					if ( record_type != RT_SESSION_END ){

						File file = FileUtil.newFile( stats_dir, current_rel_file );

						file.getParentFile().mkdirs();

						writer = new PrintWriter( new OutputStreamWriter( FileUtil.newFileOutputStream( file, true )));

						writer_rel_file = current_rel_file;

						if ( record_type == RT_SESSION_START ){

							writer.println( line );

						}else{

								// first entry in a new file, files always start with a session-start so they
								// can be processed in isolation so reset the session data and start a new one

							st_p_sent		+= line_stats[0];
							st_d_sent		+= line_stats[1];
							st_p_received	+= line_stats[2];
							st_d_received	+= line_stats[3];
							st_dht_sent		+= line_stats[4];
							st_dht_received	+= line_stats[5];

							ss_p_sent		+= line_stats[0];
							ss_d_sent		+= line_stats[1];
							ss_p_received	+= line_stats[2];
							ss_d_received	+= line_stats[3];
							ss_dht_sent		+= line_stats[4];
							ss_dht_received	+= line_stats[5];

							stats_str = "";

							long[] st_stats =  new long[]{ st_p_sent,st_d_sent, st_p_received,st_d_received, st_dht_sent, st_dht_received };

							for ( int i=0;i<st_stats.length; i++ ){

								stats_str += "," + st_stats[i];

								line_stats_prev[i] = 0;
							}

							line = "s," + VERSION + "," + now_mins + stats_str;

							writer.println( line );
						}
					}
				}else{

					writer.println( line );
				}

			}catch( Throwable e ){

				Debug.out( "Failed to write long term stats", e );

			}finally{

				if ( writer != null ){

					if ( record_type == RT_SESSION_END ){

						writer.close();
					}

					if ( writer.checkError()){

						Debug.out( "Failed to write long term stats" );

						writer.close();

						writer	= null;

					}else{

						if ( record_type == RT_SESSION_END ){

							writer	= null;
						}
					}
				}
			}
		}

		if ( record_type != RT_SESSION_END ){

			final List<LongTermStatsListener> to_fire = new ArrayList<>();

			for ( Object[] entry: listeners ){

				long	diff = session_total - (Long)entry[2];

				if ( diff >= (Long)entry[1]){

					entry[2] = session_total;

					to_fire.add((LongTermStatsListener)entry[0]);
				}
			}

			if ( to_fire.size() > 0 ){

				dispatcher.dispatch(
					new AERunnable()
					{
						@Override
						public void
						runSupport()
						{
							for ( LongTermStatsListener l: to_fire ){

								try{
									l.updated( LongTermStatsImpl.this );

								}catch( Throwable e ){

									Debug.out( e );
								}
							}
						}
					});
			}
		}
	}

	private static String
	getString(
		long[] stats )
	{
		String str = "";

		for ( long s: stats ){

			str += (str.length()==0?"":", ") + s;
		}

		return( str );
	}

	private MonthCache
	getMonthCache(
		String	year,
		String	month )
	{
		String	key = year + "_" + month;

		MonthCache cache = month_cache_map.get( key );

		if ( cache == null ){

			cache = new MonthCache( year, month );

			month_cache_map.put( key, cache );
		}

		return( cache );
	}

	@Override
	public long[]
	getTotalUsageInPeriod(
		Date				start_date,
		Date				end_date )
	{
		return( getTotalUsageInPeriod( start_date, end_date, null ));
	}

	public long[]
	getTotalUsageInPeriod(
		Date				start_date,
		Date				end_date,
		RecordAccepter		accepter )
	{
		boolean	enable_caching = accepter == null;

		synchronized( this ){

			long[] result = new long[STAT_ENTRY_COUNT];

			long start_millis 	= start_date.getTime();
			long end_millis 	= end_date.getTime();

			long	now = SystemTime.getCurrentTime();

			long	now_day	= (now/DAY_IN_MILLIS)*DAY_IN_MILLIS;

				// day cache keeps totals for the current day handy but requires the period
				// to run to "now"
			
			boolean	can_use_day_cache = end_millis >= now;
			
			if ( can_use_day_cache ){

				end_millis = now;
			}

			long start_day 	= (start_millis/DAY_IN_MILLIS)*DAY_IN_MILLIS;
			long end_day 	= (end_millis/DAY_IN_MILLIS)*DAY_IN_MILLIS;

			if ( start_day > end_day ){

				return( result );
			}

			long start_offset = start_millis - start_day;

			start_offset = start_offset/MIN_IN_MILLIS;

			boolean	offset_cachable = start_offset % 60 == 0;

			//System.out.println( "start=" + debug_utc_format.format( start_date ) + ", end=" + debug_utc_format.format( end_date ) + ", offset=" + start_offset);

			MonthCache	month_cache = null;

			for ( long this_day=start_day;this_day<=end_day;this_day+=DAY_IN_MILLIS ){

				String[] bits = utc_date_format.format( new Date( this_day )).split( "," );

				String year_str 	= bits[0];
				String month_str	= bits[1];
				String day_str		= bits[2];

				//int	year 	= Integer.parseInt( year_str );
				//int	month	= Integer.parseInt( month_str );
				int	day		= Integer.parseInt( day_str );

				long	cache_offset = this_day == start_day?start_offset:0;
				
				boolean	can_use_month_cache;

				if ( enable_caching ){

					if ( month_cache == null || !month_cache.isForMonth( year_str, month_str )){

						if ( month_cache != null && month_cache.isDirty()){

							month_cache.save();
						}

						month_cache = getMonthCache( year_str, month_str );
					}

					can_use_month_cache =
						this_day != now_day &&
						( this_day > start_day || ( this_day == start_day && offset_cachable )) &&
						this_day < end_day;

					if ( can_use_month_cache ){

						long[] cached_totals = month_cache.getTotals( day, cache_offset );

						if ( cached_totals != null ){

							for ( int i=0;i<cached_totals.length;i++){

								result[i] += cached_totals[i];
							}

							continue;
						}
					}else{

						if ( this_day == now_day ){

							if ( day_cache != null && can_use_day_cache ){

								if ( day_cache.isForDay( year_str, month_str, day_str )){

									long[] cached_totals = day_cache.getTotals( cache_offset );

									if ( cached_totals != null ){

										for ( int i=0;i<cached_totals.length;i++){

											result[i] += cached_totals[i];
										}

										continue;
									}

								}else{

									day_cache = null;
								}
							}
						}
					}
				}else{

					can_use_month_cache = false;
				}

				File stats_file = FileUtil.newFile( stats_dir, bits[0], bits[1], bits[2] + ".dat" );

				if ( !stats_file.exists()){

					if ( can_use_month_cache ){

						month_cache.setTotals( day, cache_offset, new long[0] );
					}
				}else{

					LineNumberReader lnr = null;

					try{
						//System.out.println( "Reading " + stats_file );

						lnr = new LineNumberReader( new InputStreamReader( FileUtil.newFileInputStream( stats_file )));

						long	file_start_time	= 0;

						long[]	file_totals = null;

						long[]	file_result_totals  = new long[STAT_ENTRY_COUNT];

						long[]	session_start_stats = null;
						long	session_start_time	= 0;
						long	session_time		= 0;

						while( true ){

							String line = lnr.readLine();

							if ( line == null ){

								break;
							}

							//System.out.println( line );

							String[] fields = line.split( "," );

							if ( fields.length < 6 ){

								continue;
							}

							try{
								String first_field = fields[0];

								if ( first_field.equals("s")){

									session_start_time = Long.parseLong( fields[2] )*MIN_IN_MILLIS;

									if ( file_totals == null ){

										file_totals = new long[STAT_ENTRY_COUNT];

										file_start_time = session_start_time;
									}

									session_time = session_start_time;

									session_start_stats = new long[STAT_ENTRY_COUNT];

									for ( int i=3;i<9;i++){

										session_start_stats[i-3] = Long.parseLong( fields[i] );
									}
								}else if ( session_start_time > 0 ){

									session_time += MIN_IN_MILLIS;

									int	field_offset = 0;

									if ( first_field.equals( "e" )){

										field_offset = 3;
									}

									long[] line_stats = new long[STAT_ENTRY_COUNT];

									for ( int i=0;i<6;i++){

										line_stats[i] = Long.parseLong( fields[i+field_offset] );

										file_totals[i] += line_stats[i];
									}

									if ( 	session_time >= start_millis &&
											session_time <= end_millis ){

										if ( accepter == null || accepter.acceptRecord( session_time )){

											for ( int i=0;i<6;i++){

												result[i] += line_stats[i];

												file_result_totals[i] += line_stats[i];
											}
										}
									}

									//System.out.println( getString( line_stats ));
								}
							}catch( Throwable e ){

								// skip the record, not much else that can be done
							}
						}

						if ( file_totals == null ){

							file_totals = new long[0];
						}

						//System.out.println( "File total: start=" + debug_utc_format.format(file_start_time) + ", end=" + debug_utc_format.format(session_time) + " - " + getString( file_totals ));

						if ( can_use_month_cache ){

							month_cache.setTotals( day, cache_offset, file_result_totals );

							if ( cache_offset != 0 ){

								month_cache.setTotals( day, 0, file_totals );
							}
						}else{

							if ( enable_caching ){

								if ( this_day == now_day && can_use_day_cache ){

									if ( day_cache == null ){

										//System.out.println( "Creating day cache" );

										day_cache = new DayCache( year_str, month_str, day_str );
									}

									day_cache.setTotals( cache_offset, file_result_totals );

									if ( cache_offset != 0 ){

										day_cache.setTotals( 0, file_totals );
									}
								}
							}
						}

					}catch( Throwable e ){

						Debug.out( e );

					}finally{

						if ( lnr != null ){

							try{
								lnr.close();

							}catch( Throwable e ){
							}
						}
					}
				}
			}

			if ( enable_caching ){

				if ( month_cache != null && month_cache.isDirty()){

					month_cache.save();
				}
			}

			//System.out.println( "    -> " + getString( result ));

			return( result );
		}
	}

	@Override
	public long[]
	getTotalUsageInPeriod(
		int		period_type,
		double	multiplier )
	{
		return( getTotalUsageInPeriod( period_type, multiplier, null ));
	}

	@Override
	public long[]
	getTotalUsageInPeriod(
		int					period_type,
		double				multiplier,
		RecordAccepter		accepter )
	{
		if ( start_of_week == -1 ){

			COConfigurationManager.addAndFireParameterListeners(
				new String[]{ "long.term.stats.weekstart", "long.term.stats.monthstart" },
				new ParameterListener()
				{
					@Override
					public void
					parameterChanged(
						String name )
					{
						start_of_week 	= COConfigurationManager.getIntParameter( "long.term.stats.weekstart" );
						start_of_month 	= COConfigurationManager.getIntParameter( "long.term.stats.monthstart" );
					}
				});
		}

		long	now = SystemTime.getCurrentTime();

		long top_time;
		long bottom_time;

		if ( period_type == PT_CURRENT_HOUR ){

			bottom_time = (now/HOUR_IN_MILLIS)*HOUR_IN_MILLIS;
			top_time	= bottom_time + HOUR_IN_MILLIS - 1;

		}else if ( period_type == PT_SLIDING_HOUR ){

			bottom_time = now - (long)(HOUR_IN_MILLIS*multiplier);
			top_time	= now;

		}else if ( period_type == PT_SLIDING_DAY ){

			bottom_time = now - (long)(DAY_IN_MILLIS*multiplier);
			top_time	= now;

		}else if ( period_type == PT_SLIDING_WEEK ){

			bottom_time = now - (long)(WEEK_IN_MILLIS*multiplier);
			top_time	= now;

		}else{

			Calendar calendar = new GregorianCalendar();

			calendar.setTimeInMillis( SystemTime.getCurrentTime());

			calendar.set( Calendar.MILLISECOND, 0 );
			calendar.set( Calendar.MINUTE, 0 );
			calendar.set( Calendar.HOUR_OF_DAY, 0 );

			top_time = calendar.getTimeInMillis() + DAY_IN_MILLIS - 1;

			if ( period_type == PT_CURRENT_DAY ){

			}else if ( period_type == PT_CURRENT_WEEK ){

					// sun = 1, mon = 2 etc

				int day_of_week = calendar.get( Calendar.DAY_OF_WEEK );

				if ( day_of_week == start_of_week ){

				}else if ( day_of_week > start_of_week ){

					calendar.add( Calendar.DAY_OF_WEEK, - ( day_of_week - start_of_week ));

				}else{

					calendar.add( Calendar.DAY_OF_WEEK, - ( 7 - ( start_of_week - day_of_week )));
				}

			}else{

				if ( start_of_month == 1 ){

					calendar.set( Calendar.DAY_OF_MONTH, 1 );

				}else{

					int day_of_month = calendar.get( Calendar.DAY_OF_MONTH );

					if ( day_of_month == start_of_month ){

					}else if ( day_of_month > start_of_month ){

						calendar.set( Calendar.DAY_OF_MONTH, start_of_month );

					}else{

						calendar.add( Calendar.MONTH, -1 );

						calendar.set( Calendar.DAY_OF_MONTH, start_of_month );
					}
				}
			}

			bottom_time = calendar.getTimeInMillis();
		}

		return( getTotalUsageInPeriod( new Date( bottom_time ), new Date( top_time ), accepter ));
	}

	@Override
	public long[]
	getCurrentRateBytesPerSecond()
	{
		long[] result = new long[STAT_ENTRY_COUNT];

		for ( int i=0;i<STAT_ENTRY_COUNT;i++){

			result[i] = (long)( stat_averages[i].getAverage()/60 );
		}

		return( result );
	}

	@Override
	public void
	addListener(
		long							min_delta_bytes,
		final LongTermStatsListener		listener )
	{
		listeners.add( new Object[]{ listener, min_delta_bytes, session_total });

		dispatcher.dispatch(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					listener.updated( LongTermStatsImpl.this );
				}
			});
	}

	@Override
	public void
	removeListener(
		LongTermStatsListener		listener )
	{
		for ( Object[] entry: listeners ){

			if ( entry[0] == listener ){

				listeners.remove( entry );

				break;
			}
		}
	}

	private static class
	DayCache
	{
		private final String			year;
		private final String			month;
		private final String			day;

		private final Map<Long,long[]>	contents = new HashMap<>();

		private
		DayCache(
			String		_year,
			String		_month,
			String		_day )
		{
			year	= _year;
			month	= _month;
			day		= _day;
		}

		private boolean
		isForDay(
			String	_year,
			String	_month,
			String	_day )
		{
			return( year.equals( _year ) && month.equals( _month ) && day.equals( _day ));
		}

		private void
		addRecord(
			long	offset,
			long[]	stats )
		{
			for ( Map.Entry<Long,long[]> entry: contents.entrySet()){

				if ( offset >= entry.getKey()){

					long[] old = entry.getValue();

					for ( int i=0;i<old.length;i++){

						old[i] += stats[i];
					}
				}
			}
		}

		private long[]
		getTotals(
			long	offset )
		{
			return( contents.get( offset ));
		}

		private void
		setTotals(
			long	offset,
			long[]	value )
		{
			//System.out.println( "Updating day cache for " + offset );
			
			contents.put( offset, value );
		}
	}

	private class
	MonthCache
	{
		private final String			year;
		private final String			month;

		private boolean		dirty;

		private Map<String,List<Long>>	contents;

		private
		MonthCache(
			String		_year,
			String		_month )
		{
			year	= _year;
			month	= _month;
		}

		private File
		getCacheFile()
		{
			return( FileUtil.newFile( stats_dir, year, month, "cache.dat" ));
		}

		private boolean
		isForMonth(
			String	_year,
			String	_month )
		{
			return( year.equals( _year ) && month.equals( _month ));
		}

		private Map<String,List<Long>>
		getContents()
		{
			if ( contents == null ){

				File file = getCacheFile();

				if ( file.exists()){

					//System.out.println( "Reading cache: " + file );

					contents = FileUtil.readResilientFile( file );

				}else{

					contents = new HashMap<>();
				}
			}

			return( contents );
		}

		private long[]
		getTotals(
			int		day )
		{
			List<Long> records = getContents().get( String.valueOf( day ));

			if ( records != null ){

				long[] result = new long[STAT_ENTRY_COUNT];

				if ( records.size() == STAT_ENTRY_COUNT ){

					for ( int i=0;i<STAT_ENTRY_COUNT;i++){

						result[i] = (Long)records.get(i);
					}
				}

				return( result );
			}

			return( null );
		}

		private long[]
 		getTotals(
 			int		day,
 			long	start_offset )
 		{
			if ( start_offset == 0 ){

				return( getTotals( day ));

			}else{

	 			List<Long> records = getContents().get( day + "." + start_offset );

	 			if ( records != null ){

	 				long[] result = new long[STAT_ENTRY_COUNT];

	 				if ( records.size() == STAT_ENTRY_COUNT ){

	 					for ( int i=0;i<STAT_ENTRY_COUNT;i++){

	 						result[i] = (Long)records.get(i);
	 					}
	 				}

	 				return( result );
	 			}

	 			return( null );
			}
 		}

		private void
		setTotals(
			int		day,
			long[]	totals )
		{
			//System.out.println( "Updating month cache for " + day );
			
			List<Long>	records = new ArrayList<>();

			for ( Long l: totals ){

				records.add( l );
			}

			getContents().put( String.valueOf( day ), records );

			dirty	= true;
		}

		private void
		setTotals(
			int		day,
			long	start_offset,
			long[]	totals )
		{
			//System.out.println( "Updating month cache for " + day + " / " + start_offset );
			
			if ( start_offset == 0 ){

				setTotals( day, totals );

			}else{

				List<Long>	records = new ArrayList<>();

				for ( Long l: totals ){

					records.add( l );
				}

				getContents().put( day + "." + start_offset, records );

				dirty	= true;
			}
		}

		private boolean
		isDirty()
		{
			return( dirty );
		}

		private void
		save()
		{
			File file = getCacheFile();

			file.getParentFile().mkdirs();

			//System.out.println( "Writing cache: " + file );

			FileUtil.writeResilientFile( file, contents );

			dirty = false;
		}
	}

	public static void
	main(
		String[]	args )
	{
		try{
			LongTermStatsImpl impl = new LongTermStatsImpl( new File( "C:\\Test\\plus6b\\stats" ));

			SimpleDateFormat local_format = new SimpleDateFormat( "yyyy,MM,dd" );

			Date start_date = local_format.parse( "2013,07,10" );
			Date end_date 	= local_format.parse( "2013,07,16" );

			long[] usage =
				impl.getTotalUsageInPeriod(
					start_date,
					end_date,
					new RecordAccepter()
					{
						@Override
						public boolean
						acceptRecord(
							long timestamp)
						{
							System.out.println( new Date( timestamp ));

							return( true );
						}
					});

			System.out.println( getString( usage ));

			System.out.println( getString(impl.getTotalUsageInPeriod( PT_CURRENT_HOUR, 1 )));
			System.out.println( getString(impl.getTotalUsageInPeriod( PT_SLIDING_HOUR, 1 )));
			//System.out.println( getString(impl.getTotalUsageInPeriod( PT_CURRENT_WEEK )));
			//System.out.println( getString(impl.getTotalUsageInPeriod( PT_CURRENT_MONTH )));

		}catch( Throwable e ){

			e.printStackTrace();
		}
	}
}
