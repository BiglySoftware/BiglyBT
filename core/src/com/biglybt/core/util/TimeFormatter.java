/*
 * Created on 27 juin 2003
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
package com.biglybt.core.util;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.internat.MessageText.MessageTextListener;

/**
 * @author Olivier
 *
 */
public class TimeFormatter {
 
	public static final int	TS_SECOND	= 0;
	public static final int	TS_MINUTE	= 1;
	public static final int	TS_HOUR		= 2;
	public static final int	TS_DAY		= 3;
	public static final int	TS_WEEK		= 4;
	public static final int	TS_MONTH	= 5;
	public static final int	TS_YEAR		= 6;
	
	public static final String[] 	TIME_SUFFIXES 	= { "s", "m", "h", "d", "y" };

	public static final String[] 	TIME_SUFFIXES_2 		= { "sec", "min", "hr", "day", "wk", "mo", "yr" };
	public static final String[] 	TIME_SUFFIXES_2_LONG	= { "second", "minute", "hour", "day", "week", "month", "year" };
	
	public static final long[]		TIME_SUFFIXES_2_MULT	 = { 1, 60, 60*60, 24*60*60, 7*24*60*60, 30*24*60*60, 365L*24*60*60 };

	public static String		MS_SUFFIX = " ms";
	
	public static String
	getShortSuffix(
		int		unit )
	{
		return( TIME_SUFFIXES[unit]);
	}
	
	public static String
	getLongSuffix(
		int		unit )
	{
		return( TIME_SUFFIXES_2[unit]);
	}
	public static final String[] DATEFORMATS_DESC;
	
	static{
		String[] defs = new String[] {
			"EEEE, MMMM d, yyyy GG",
			"EEEE, MMMM d, yyyy",
			"EEE, MMMM d, yyyy",
			"MMMM d, ''yy",
			"EEE, MMM d, ''yy",
			"MMM d, yyyy",
			"MMM d, ''yy",
			"yyyy/MM/dd",
			"''yy/MM/dd",
			"MMM dd",
			"MM/dd",
		};
		
		String formats = MessageText.getString( "column.date.formats" );
		
		if ( formats != null ){
			
			String[] bits = formats.split( ";" );
			
			List<String> list = new ArrayList<>( bits.length);
			
			for ( String bit: bits ){
				
				bit = bit.trim();
				
				if ( bit.length() > 0 ){
					
						// CrowdIn has this bug whereby a source string with a '' ends up being
						// ingested as ' 
					
					boolean added = false;
					
					try{
						new SimpleDateFormat( bit );
					
						list.add( bit );
					
						added = true;
						
					}catch( Throwable e ){
						
						if ( bit.contains( "'" )){
							
							bit = bit.replaceAll( "'", "''" );
							
							try{
								new SimpleDateFormat( bit );
							
								list.add( bit );
								
								added = true;
								
							}catch( Throwable f ){
							}
						}
					}
					
					if ( !added ){
						
						System.err.println( "Invalid date format: " + bit );
					}
				}
			}
			
			if ( list.size() > 0 ){
				
				DATEFORMATS_DESC = list.toArray(new String[list.size()]);
				
			}else{
				
				DATEFORMATS_DESC = defs;
			}
		}else{
			
			DATEFORMATS_DESC = defs;
		}
	}

	private static final SimpleDateFormat http_date_format =
		new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US );

	static{
			// see http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.3.1

		http_date_format.setTimeZone(TimeZone.getTimeZone("GMT"));
	}

	private static final SimpleDateFormat cookie_date_format =
		new SimpleDateFormat("EEE, dd-MMM-yyyy HH:mm:ss z", Locale.US );

	static{
			// see http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.3.1

		cookie_date_format.setTimeZone(TimeZone.getTimeZone("GMT"));
	}

	static{
		MessageText.addAndFireListener(new MessageTextListener() {
			@Override
			public void localeChanged(Locale old_locale, Locale new_locale) {
				loadMessages();
			}
		});
	}

	private static void
	loadMessages()
	{
		TIME_SUFFIXES[0]	= MessageText.getString( "ConfigView.section.stats.seconds.short" );
		TIME_SUFFIXES[1]	= MessageText.getString( "ConfigView.section.stats.minutes.short" );
		TIME_SUFFIXES[2]	= MessageText.getString( "ConfigView.section.stats.hours.short" );
		TIME_SUFFIXES[3]	= MessageText.getString( "ConfigView.section.stats.days.short" );
		TIME_SUFFIXES[4]	= MessageText.getString( "ConfigView.section.stats.years.short" );

		TIME_SUFFIXES_2[0]	= MessageText.getString( "ConfigView.section.stats.seconds" );
		TIME_SUFFIXES_2[1]	= MessageText.getString( "ConfigView.section.stats.minutes" );
		TIME_SUFFIXES_2[2]	= MessageText.getString( "ConfigView.section.stats.hours" );
		TIME_SUFFIXES_2[3]	= MessageText.getString( "ConfigView.section.stats.days" );
		TIME_SUFFIXES_2[4]	= MessageText.getString( "ConfigView.section.stats.weeks.medium" );
		TIME_SUFFIXES_2[5]	= MessageText.getString( "ConfigView.section.stats.months.medium" );
		TIME_SUFFIXES_2[6]	= MessageText.getString( "ConfigView.section.stats.years.medium" );
		
		TIME_SUFFIXES_2_LONG[0]	= MessageText.getString( "ConfigView.section.stats.seconds.full" );
		TIME_SUFFIXES_2_LONG[1]	= MessageText.getString( "ConfigView.section.stats.minutes.full" );
		TIME_SUFFIXES_2_LONG[2]	= MessageText.getString( "ConfigView.section.stats.hours.full" );
		TIME_SUFFIXES_2_LONG[3]	= MessageText.getString( "ConfigView.section.stats.days" );
		TIME_SUFFIXES_2_LONG[4]	= MessageText.getString( "ConfigView.section.stats.weeks" );
		TIME_SUFFIXES_2_LONG[5]	= MessageText.getString( "ConfigView.section.stats.months" );
		TIME_SUFFIXES_2_LONG[6]	= MessageText.getString( "ConfigView.section.stats.years" );

		
		MS_SUFFIX = " " + MessageText.getString( "ConfigView.section.stats.millis.short" );
	}

	/**
	 * Format time into two time sections, the first chunk trimmed, the second
	 * with always with 2 digits.  Sections are *d, **h, **m, **s.  Section
	 * will be skipped if 0.
	 *
	 * @param time time in seconds
	 * @return Formatted time string
	 */
	public static String format(long time_secs) {
		if (time_secs == Constants.CRAPPY_INFINITY_AS_INT || time_secs >= Constants.CRAPPY_INFINITE_AS_LONG)
			return Constants.INFINITY_STRING;

		if (time_secs < 0)
			return "";

		// secs, mins, hours, days
		int[] vals = {
			(int) time_secs % 60,
			(int) (time_secs / 60) % 60,
			(int) (time_secs / 3600) % 24,
			(int) (time_secs / 86400) % 365,
			(int) (time_secs / 31536000)
			};

		int end = vals.length - 1;
		while (vals[end] == 0 && end > 0) {
			end--;
		}

		String result = vals[end] + TIME_SUFFIXES[end];

		/* old logic removed to prefer showing consecutive units
		// skip until we have a non-zero time section
		do {
			end--;
		} while (end >= 0 && vals[end] == 0);
		*/

		end--;

		if (end >= 0)
			result += " " + twoDigits(vals[end]) + TIME_SUFFIXES[end];

		return result;
	}

	/**
	 * format seconds into significant y d h m s (e.g. 12d 02h 03m 23s) and drop secs if wanted
	 * @param time_secs
	 * @param do_seconds
	 * @return
	 */
	public static String
	format2(
		long 	time_secs,
		boolean do_seconds )
	{
		if (time_secs == Constants.CRAPPY_INFINITY_AS_INT || time_secs >= Constants.CRAPPY_INFINITE_AS_LONG)
			return Constants.INFINITY_STRING;

		if (time_secs < 0)
			return "";

		// secs, mins, hours, days
		int[] vals = {
			(int) time_secs % 60,
			(int) (time_secs / 60) % 60,
			(int) (time_secs / 3600) % 24,
			(int) (time_secs / 86400) % 365,
			(int) (time_secs / 31536000)
			};

		int start = vals.length - 1;
		while (vals[start] == 0 && start > 0) {
			start--;
		}

		int	end = do_seconds?0:1;

		if ( start==0&&!do_seconds ){
			start=1;
		}

		String result = "";

		for ( int i=start;i>=end;i--){

			result += (i==start?vals[i]:(" " + twoDigits(vals[i]))) + TIME_SUFFIXES[i];
		}

		return result;
	}

	/**
	 * format seconds into most significant time chunk (year, week etc)
	 * @param time_secs
	 * @return
	 */

	public static String
	format3(
		long 	time_secs )
	{
		return( format3( time_secs, null ));
	}
		
	public static String
	format3(
		long 	time_secs,
		long[]	sort_time )
	{
		return( format3( time_secs, sort_time, false ));
	}
	
	public static String
	format3(
		long 		time_secs,
		long[]		sort_time,
		boolean		flexible )
	{
		int[] temp = format3Support( time_secs, sort_time, flexible );
		
		int val = temp[0];
		
		if ( val == -1 ){
			
			return Constants.INFINITY_STRING;
			
		}else if ( val == -2 ){
			

			return "";

		}

		String result = val + " " + TIME_SUFFIXES_2[temp[1]];

		return result;
	}

	public static int[]
	format3Support(
		long 		time_secs,
		long[]		sort_time )
	{
		return( format3Support( time_secs, sort_time, false ));
	}
	
	public static int[]
	format3Support(
		long 		time_secs,
		long[]		sort_time,
		boolean		flexible )
	{
		if (time_secs == Constants.CRAPPY_INFINITY_AS_INT || time_secs >= Constants.CRAPPY_INFINITE_AS_LONG)
			return new int[]{ -1, 0 };

		if ( time_secs < 0 ){

			return new int[]{ -2, 0 };

		}


		int unit_index = TIME_SUFFIXES_2.length - 1;
		int unit_val;
		
		if ( flexible ){
		
			// secs, mins, hours, days, weeks, months, years (kind of...)

			int[] vals = {
				(int) time_secs,						// secs
				(int) (time_secs / 60),					// mins
				(int) (time_secs / ( 60*60)),			// hours
				(int) (time_secs / ( 60*60*24)),		// days
				(int) (time_secs / ( 60*60*24*7)),		// weeks
				(int) (time_secs / ( 60*60*24*30)),		// months
				(int) (time_secs / ( 60*60*24*365L))	// years
			};

			int[] val_max = { 60, 240, 96, 90, 104, Integer.MAX_VALUE };
			
			while ( true ){
				
				long val = vals[unit_index];
			
				if ( val != 0 ){
					
					long rem = time_secs - (val * TIME_SUFFIXES_2_MULT[unit_index]);
					
					if ( rem == 0 ){
						
						break;
						
					}else if ( unit_index > 0 && vals[unit_index-1] >= val_max[ unit_index-1 ]){
						
						break;
					}
				}
				
				if ( unit_index > 0 ){
				
					unit_index--;
					
				}else{
					
					break;
				}
			}
			
			unit_val = vals[ unit_index ];
			
		}else{
			// secs, mins, hours, days, weeks, months, years (kind of...)

			int[] vals = {
				(int) time_secs,							// secs
				(int) (time_secs / 60),					// mins
				(int) (time_secs / ( 60*60)),				// hours
				(int) (time_secs / ( 60*60*24)),			// days
				(int) (time_secs / ( 60*60*24*7)),			// weeks
				(int) (time_secs / ( 60*60*24*30)),		// months
				(int) (time_secs / ( 60*60*24*365L))			// years
			};

			while (vals[unit_index] == 0 && unit_index > 0){
			
				unit_index--;
			}
			
			unit_val = vals[ unit_index ];
		}
		
		if ( sort_time != null ){
			sort_time[0] = unit_val * TIME_SUFFIXES_2_MULT[unit_index];
		}

		return( new int[]{ unit_val, unit_index });
	}
	
	public static String format100ths(long time_millis) {

		long time_secs = time_millis / 1000;

		int  hundredths = (int)(time_millis - time_secs*1000)/10;

		if ( time_millis == 0 || time_secs >= 60 ){

			return( format( time_secs ));
		}

		return( time_secs + "." + twoDigits( hundredths) + TIME_SUFFIXES[0]);
	}

	/**
	 * @param time millis
	 */

	public static String formatColonMillis( long time )
	{
		if ( time > 0 ){
			if ( time < 1000 ){
				time = 1;
			}else{
				time = time / 1000;
			}
		}

		String str = formatColon( time );

		if ( str.startsWith( "00:" )){

			str = str.substring( 3 );
		}

		return( str );
	}

	/**
	 * Format time into "[[# y] # d] 00:00:00" format
	 *
	 * @param time time in seconds
	 * @return
	 */

    public static String formatColon(long time)
    {
      if (time == Constants.CRAPPY_INFINITY_AS_INT || time >= Constants.CRAPPY_INFINITE_AS_LONG) return Constants.INFINITY_STRING;
      if (time < 0) return "";

      int secs = (int) time % 60;
      int mins = (int) (time / 60) % 60;
      int hours = (int) (time /3600) % 24;
      int days = (int) (time / 86400) % 365;
      int years = (int) (time / 31536000);

      String result = "";
      if (years > 0) result += years + "y ";
      if (years > 0 || days > 0) result += days + "d ";
      result += twoDigits(hours) + ":" + twoDigits(mins) + ":" + twoDigits(secs);

      return result;
    }

    private static String twoDigits(int i) {
      return (i < 10) ? "0" + i : String.valueOf(i);
    }

    	/**
    	 * parse time in h:m:s format to SECONDS
    	 * @param str
    	 * @return
    	 */

    public static int
    parseColon(
    	String	str )
    {
    	final int[]	multipliers = { 1, 60, 3600, 86400, 31536000 };

    	String[]	bits = str.split( ":" );

    	int	result = 0;

    	for (int i=0;i<bits.length;i++){

    		String bit = bits[bits.length-(i+1)].trim();

    		if ( bit.length() > 0 ){

    			result += multipliers[i] * Integer.parseInt( bit );
    		}
    	}

    	return( result );
    }

    public static String
    formatNanoAsMilli(
    	long	nanos )
    {
    	final long truncator = 60*1000000000L;

    	nanos = nanos - ((nanos/truncator) * truncator);

    	return( String.valueOf(((double)nanos)/1000000) + MS_SUFFIX );
    }

    public static String
    getHTTPDate(
    	long		millis )
    {
		synchronized( http_date_format ){

			return( http_date_format.format(new Date( millis )));
		}
    }

    public static long
    parseHTTPDate(
    	String		date )
    {
    	try{
    		synchronized( http_date_format ){

    			return( http_date_format.parse( date ).getTime());
    		}
    	}catch( Throwable e ){

    		Debug.out("Failed to parse HTTP date '" + date + "'" );

    		return( 0 );
    	}
    }

    public static String
    getCookieDate(
    	long		millis )
    {
		synchronized( cookie_date_format ){

			return( cookie_date_format.format(new Date( millis )));
		}
    }

    public static String
    milliStamp()
    {
    	long nanos = SystemTime.getHighPrecisionCounter();

    	final long truncator = 60*1000000000L;

    	nanos = nanos - ((nanos/truncator) * truncator);

    	String	str = String.valueOf( nanos/1000000 );

    	while( str.length() < 5 ){

    		str = "0" + str;
    	}

    	return( str + ": " );
    }

    public static void
    milliTrace(
    	String	str )
    {
    	System.out.println( milliStamp() + str );
    }
}
