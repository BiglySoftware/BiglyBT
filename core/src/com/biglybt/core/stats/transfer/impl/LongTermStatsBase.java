/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.core.stats.transfer.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.stats.transfer.LongTermStatsListener;
import com.biglybt.core.stats.transfer.impl.LongTermStatsWrapper.LongTermStatsWrapperHelper;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.AsyncDispatcher;
import com.biglybt.core.util.CopyOnWriteList;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.FileUtil;
import com.biglybt.core.util.SimpleTimer;
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.TimerEvent;
import com.biglybt.core.util.TimerEventPerformer;
import com.biglybt.core.util.TimerEventPeriodic;
import com.biglybt.core.util.average.Average;
import com.biglybt.core.util.average.AverageFactory;

public abstract class 
LongTermStatsBase
	implements LongTermStatsWrapperHelper
{
	protected static final int VERSION = 1;

	protected static final long MIN_IN_MILLIS		= 60*1000;
	protected static final long HOUR_IN_MILLIS		= 60*60*1000;
	protected static final long DAY_IN_MILLIS		= 24*60*60*1000;
	protected static final long WEEK_IN_MILLIS		= 7*24*60*60*1000;

	protected static final int RT_SESSION_START		= 1;
	protected static final int RT_SESSION_STATS		= 2;
	protected static final int RT_SESSION_END		= 3;

	protected final int STAT_ENTRY_COUNT;
	
	protected final long[] line_stats_prev;

	private final Average[] stat_averages;

	protected boolean				active;
	protected boolean				closing;
	protected volatile boolean		destroyed;

	private volatile long overall_start_time;
	
	private TimerEventPeriodic	event;
	private PrintWriter			writer;
	private String				writer_rel_file;

	private static final SimpleDateFormat	debug_utc_format 	= new SimpleDateFormat( "yyyy,MM,dd:HH:mm" );
	private static final SimpleDateFormat	utc_date_format 	= new SimpleDateFormat( "yyyy,MM,dd" );

	static{
		debug_utc_format.setTimeZone( TimeZone.getTimeZone( "UTC" ));
		utc_date_format.setTimeZone( TimeZone.getTimeZone( "UTC" ));
	}

	protected File stats_dir;

	private long	session_total;

	private final CopyOnWriteList<Object[]>	listeners = new CopyOnWriteList<>();

	private final AsyncDispatcher	dispatcher = new AsyncDispatcher( "lts", 5000 );

	private int	start_of_week 	= -1;
	private int start_of_month	= -1;
	
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
		
	protected
	LongTermStatsBase(
		int	entry_count )
	{
		STAT_ENTRY_COUNT = entry_count;
		
		line_stats_prev = new long[STAT_ENTRY_COUNT];

		stat_averages = new Average[STAT_ENTRY_COUNT];

		for ( int i=0;i<STAT_ENTRY_COUNT;i++){

			stat_averages[i] = AverageFactory.MovingImmediateAverage( 3 );
		}
		
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

						synchronized( LongTermStatsBase.this ){

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
	}
	
	protected abstract void
	sessionStart();
	
	protected void
	sessionStartComplete(
		String		name )
	{
		if ( event == null ){	// should always be null but hey ho

		    event =
		    	SimpleTimer.addPeriodicEvent(
			    	name,
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
	
	protected void
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
		
	protected void
	updateStats()
	{
		updateStats( RT_SESSION_STATS );
	}
	
	protected abstract void
	updateStats(
		int	record_type );
	
	protected abstract long[]
	getNewFileSessionStats(
		long[]		line_stats );
	
	protected void
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

							long[] st_stats = getNewFileSessionStats( line_stats );
							
							stats_str = "";

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
									l.updated( LongTermStatsBase.this );

								}catch( Throwable e ){

									Debug.out( e );
								}
							}
						}
					});
			}
		}
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

			overall_start_time = 0;
			
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
	
	@Override
	public long 
	getOverallStartTime()
	{
		if ( overall_start_time > 0 ){
			
			return( overall_start_time );
		}
		
		try{
			File[] years = stats_dir.listFiles();
			
			int earliest_year 	= Integer.MAX_VALUE;
			int earliest_month	= Integer.MAX_VALUE;
			int earliest_day	= Integer.MAX_VALUE;
						
			for ( File year: years ){
				
				String y_str = year.getName();
				
				try{
					int y = Integer.parseInt( y_str );
				
					if ( y > 1999 && y < 2199 && y <= earliest_year ){
					
						File[] months = year.listFiles();
					
						for ( File month: months ){
							
							String m_str = month.getName();
							
							if ( m_str.equals( "cache.dat" )){
								
								continue;
							}
							
							try{
								int m = Integer.parseInt( m_str );
							
								if ( m >= 1 && m <= 12 ){
									
									if ( y < earliest_year || ( y == earliest_year && m <= earliest_month )){
										
										File[] days = month.listFiles();
										
										for ( File day: days ){
											
											String d_str = day.getName();
											
											if ( m_str.equals( "d_str.dat" )){
												
												continue;
											}
											
											try{
												if ( d_str.endsWith( ".dat" )){
													
													int d = Integer.parseInt( d_str.substring( 0, d_str.length() - 4 ));
													
													if ( d >= 1 && d <= 31 ){
														
														if (	y < earliest_year || 
																( y == earliest_year && 
																	( m < earliest_month || ( m == earliest_month && d < earliest_day )))){
															
															earliest_year 	= y;
															earliest_month	= m;
															earliest_day	= d;
															
															if ( earliest_day == 1 ){
																
																break;
															}
														}
													}
												}
											}catch( Throwable e ){
											}
										}
									}
								}
							}catch( Throwable e ){
							}
						}
					}
				}catch( Throwable e ){
				}
			}
			
			if ( earliest_year != Integer.MAX_VALUE ){
				
				Date date = utc_date_format.parse( earliest_year + "," + earliest_month + "," + earliest_day );
				
				overall_start_time = date.getTime();
			}
		}catch( Throwable e ){
			
			Debug.out( e );
		}
		
		if ( overall_start_time <= 0 ){
			
			overall_start_time = SystemTime.getCurrentTime();
		}
		
		return( overall_start_time);
	}
	
	@Override
	public long[]
	getTotalUsageInPeriod(
		Date				start_date,
		Date				end_date )
	{
		return( getTotalUsageInPeriod( start_date, end_date, null ));
	}

	@Override
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

							if ( fields.length < STAT_ENTRY_COUNT ){

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

									for ( int i=3;i<3+STAT_ENTRY_COUNT;i++){

										session_start_stats[i-3] = Long.parseLong( fields[i] );
									}
								}else if ( session_start_time > 0 ){

									session_time += MIN_IN_MILLIS;

									int	field_offset = 0;

									if ( first_field.equals( "e" )){

										field_offset = 3;
									}

									long[] line_stats = new long[STAT_ENTRY_COUNT];

									for ( int i=0;i<STAT_ENTRY_COUNT;i++){

										line_stats[i] = Long.parseLong( fields[i+field_offset] );

										file_totals[i] += line_stats[i];
									}

									if ( 	session_time >= start_millis &&
											session_time <= end_millis ){

										if ( accepter == null || accepter.acceptRecord( session_time )){

											for ( int i=0;i<STAT_ENTRY_COUNT;i++){

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
		int			period_type,
		double		multiplier )
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

			bottom_time = now - (long)(multiplier*HOUR_IN_MILLIS);
			top_time	= now;

		}else if ( period_type == PT_SLIDING_DAY ){

			bottom_time = now - (long)(multiplier*DAY_IN_MILLIS);
			top_time	= now;

		}else if ( period_type == PT_SLIDING_WEEK ){

			bottom_time = now - (long)(multiplier*WEEK_IN_MILLIS);
			top_time	= now;

		}else{

			Calendar calendar = new GregorianCalendar();

			calendar.setTimeInMillis( now );

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
					listener.updated( LongTermStatsBase.this );
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
	
	protected static class
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

		protected boolean
		isForDay(
			String	_year,
			String	_month,
			String	_day )
		{
			return( year.equals( _year ) && month.equals( _month ) && day.equals( _day ));
		}

		protected void
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
}
