/*
 * Created on Jun 9, 2008
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


package com.biglybt.core.util;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.util.average.AverageFactory;
import com.biglybt.core.util.average.MovingImmediateAverage;

public class
GeneralUtils
{

	/**
	 * as above but does safe replacement of multiple strings (i.e. a match in the replacement
	 * of one string won't be substituted by another)
	 * @param str
	 * @param from_strs
	 * @param to_strs
	 * @return
	 */
	public static String
	replaceAll(
		String		str,
		String[]	from_strs,
		String[]	to_strs )
	{
		StringBuffer	res = null;

		int	pos = 0;

		while( true ){

			int	min_match_pos 	= Integer.MAX_VALUE;
			int	match_index		= -1;

			for ( int i=0;i<from_strs.length;i++ ){

				int	pt = str.indexOf( from_strs[i], pos );

				if ( pt != -1 ){

					if ( pt < min_match_pos ){

						min_match_pos		= pt;
						match_index			= i;
					}
				}
			}

			if ( match_index == -1 ){

				if ( res == null ){

					return( str );
				}

				res.append( str.substring( pos ));

				return( res.toString());

			}else{

				if ( res == null ){

					res = new StringBuffer( str.length() * 2 );
				}

				if ( min_match_pos > pos ){

					res.append( str.substring( pos, min_match_pos ));
				}

				res.append( to_strs[match_index] );

				pos = min_match_pos + from_strs[match_index].length();
			}
		}
	}

	private final static String REGEX_URLHTML = "<A HREF=\"(.+?)\">(.+?)</A>";
	public static String stripOutHyperlinks(String message) {
		return Pattern.compile(REGEX_URLHTML, Pattern.CASE_INSENSITIVE).matcher(
				message).replaceAll("$2");
	}

		/**
		 * splits space separated tokens respecting quotes (either " or ' )
		 * @param str
		 * @return
		 */

	public static String[]
	splitQuotedTokens(
		String		str )
	{
		List<String>	bits = new ArrayList<>();

		char	quote 				= ' ';
		boolean	escape 				= false;
		boolean	bit_contains_quotes = false;

		String	bit = "";

		char[] chars = str.toCharArray();

		for (int i=0;i<chars.length;i++){

			char c = chars[i];

			if ( Character.isWhitespace(c)){

				c = ' ';
			}

			if ( escape ){

				bit += c;

				escape = false;

				continue;

			}else if ( c == '\\' ){

				escape = true;

				continue;
			}

			if ( c == '"' || c == '\'' && ( i == 0 || chars[ i-1 ] != '\\' )){

				if ( quote == ' ' ){

					bit_contains_quotes = true;

					quote = c;

				}else if ( quote == c ){

					quote = ' ';

				}else{

					bit += c;
				}
			}else{

				if ( quote == ' ' ){

					if ( c == ' ' ){

						if ( bit.length() > 0 || bit_contains_quotes ){

							bit_contains_quotes = false;

							bits.add( bit );

							bit = "";
						}
					}else{

						bit += c;
					}
				}else{

					bit += c;
				}
			}
		}

		if ( quote != ' ' ){

			bit += quote;
		}

		if ( bit.length() > 0 || bit_contains_quotes ){

			bits.add( bit );
		}

		return( bits.toArray( new String[bits.size()]));
	}

	public static ProcessBuilder
	createProcessBuilder(
		File workingDir,
		String[] cmd,
		String[] extra_env)

		throws IOException
	{
		ProcessBuilder pb;

		Map<String, String> newEnv = new HashMap<>();
		newEnv.putAll(System.getenv());
		newEnv.put("LANG", "C.UTF-8");
		if (extra_env != null && extra_env.length > 1) {
			for (int i = 1; i < extra_env.length; i += 2) {
				newEnv.put(extra_env[i - 1], extra_env[i]);
			}
		}

		if ( Constants.isWindows ){
			String[] i18n = new String[cmd.length + 2];
			i18n[0] = "cmd";
			i18n[1] = "/C";
			i18n[2] = escapeDosCmd(cmd[0]);
			for (int counter = 1; counter < cmd.length; counter++) {
				if (cmd[counter].length() == 0) {
					i18n[counter + 2] = "";
				} else {
					String envName = "JENV_" + counter;
					i18n[counter + 2] = "%" + envName + "%";
					newEnv.put(envName, cmd[counter]);
				}
			}
			cmd = i18n;
		}

		pb = new ProcessBuilder(cmd);
		Map<String, String> env = pb.environment();
		env.putAll(newEnv);

		if (workingDir != null) {
			pb.directory(workingDir);
		}
		return pb;
	}

	private static String escapeDosCmd(String string) {
		String s = string.replaceAll("([&%^])", "^$1");
		s = s.replaceAll("'", "\"'\"");
		return s;
	}

	private static int SMOOTHING_UPDATE_WINDOW	 	= 60;
	private static int SMOOTHING_UPDATE_INTERVAL 	= 1;


	static{
		COConfigurationManager.addAndFireParameterListener(
			"Stats Smoothing Secs",
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					String xxx )
				{
					SMOOTHING_UPDATE_WINDOW	= COConfigurationManager.getIntParameter( "Stats Smoothing Secs" );

					if ( SMOOTHING_UPDATE_WINDOW < 30 ){

						SMOOTHING_UPDATE_WINDOW = 30;

					}else if ( SMOOTHING_UPDATE_WINDOW > 30*60 ){

						SMOOTHING_UPDATE_WINDOW = 30*60;
					}

					SMOOTHING_UPDATE_INTERVAL = SMOOTHING_UPDATE_WINDOW/60;

					if ( SMOOTHING_UPDATE_INTERVAL < 1 ){

						SMOOTHING_UPDATE_INTERVAL = 1;

					}else if ( SMOOTHING_UPDATE_INTERVAL > 20 ){

						SMOOTHING_UPDATE_INTERVAL = 20;
					}
				}
			});
	}

	public static int
	getSmoothUpdateWindow()
	{
		return( SMOOTHING_UPDATE_WINDOW );
	}

	public static int
	getSmoothUpdateInterval()
	{
		return( SMOOTHING_UPDATE_INTERVAL );
	}


	public static SmoothAverage
	getSmoothAverageForReplay()
	{
		return( new SmoothAverage2());
	}
	
	public static SmoothAverage
	getSmoothAverage()
	{
		return( new SmoothAverage1());
	}
	
	public interface
	SmoothAverage
	{
		public void
		addValue(
			long	v );
		
		public long
		getAverage();
	}
	
	private static class
	SmoothAverage1
		implements SmoothAverage
	{
			// works well with inconsistent update timings
		
		final Average		average;
		final int			interval;

		private
		SmoothAverage1()
		{
			interval = SMOOTHING_UPDATE_INTERVAL;
			
			average = Average.getInstance(interval*1000, SMOOTHING_UPDATE_WINDOW ); //average over SMOOTHING_UPDATE_INTERVAL secs, update every SMOOTHING_UPDATE_WINDOW secs
		}
		
		public void
		addValue(
			long	v )
		{
			average.addValue( v );
		}
		
		public long
		getAverage()
		{
			return( average.getAverage());
		}
	}
	
	private static class
	SmoothAverage2
		implements SmoothAverage
	{
			// doesn't work well with inconsistent update timings but good for replaying values...
		
		final MovingImmediateAverage		average;
		final int							interval;

		private
		SmoothAverage2()
		{
			interval = SMOOTHING_UPDATE_INTERVAL;
			
			average = AverageFactory.MovingImmediateAverage(SMOOTHING_UPDATE_WINDOW/interval );
		}
		
		public void
		addValue(
			long	v )
		{
			average.update( v );
		}
		
		public long
		getAverage()
		{
			return((long)( average.getAverage()/interval ));
		}
	}
	
	public static String stringJoin(Collection list, String delim) {
		StringBuilder sb = new StringBuilder();
		for (Object s : list) {
			if (s == null) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append(delim);
			}
			sb.append(s.toString());
		}
		return sb.toString();
	}
	
	
	
	public static boolean
	isDoubleQuote(
		char	c )
	{
		return( c == '"' || c == '\u201c' || c == '\u201d' );
	}
	
	public static boolean
	isSingleQuote(
		char	c )
	{
		return( c == '\'' || c == '\u2018' || c == '\u2019' );
	}

	public static boolean
	startsWithDoubleQuote(
		String	str )
	{
		return( !str.isEmpty() && isDoubleQuote( str.charAt(0)));
	}
	
	public static boolean
	endsWithDoubleQuote(
		String	str )
	{
		int	len = str.length();
		
		return( len > 0 && isDoubleQuote( str.charAt(len-1)));
	}
	
	public static boolean
	startsWithIgnoreCase(
		String 	s1,
		String	s2 )
	{
		return( s1.toLowerCase( Locale.US ).startsWith( s2.toLowerCase( Locale.US )));
	}
	
	private static Map<String,Integer>		unit_map = new HashMap<>();
	
	static{
		unit_map.put( "b",  0 );
		unit_map.put( "kb", 1 );
		unit_map.put( "mb", 2 );
		unit_map.put( "gb", 3 );
		unit_map.put( "tb", 4 );
		unit_map.put( "pb", 5 );
		unit_map.put( "eb", 6 );
				
		unit_map.put( "kib", 11 );
		unit_map.put( "mib", 12 );
		unit_map.put( "gib", 13 );
		unit_map.put( "tib", 14 );
		unit_map.put( "pib", 15 );
		unit_map.put( "eib", 16 );
		
		unit_map.put( "k"  , 11 );
		unit_map.put( "m",   12 );
		unit_map.put( "g",   13 );
		unit_map.put( "t",   14 );
		unit_map.put( "p",   15 );
		unit_map.put( "e",   16 );
	}
	
	static long[] unit_values = {
		 	1L, 
			1000L, 
			1000L*1000,
			1000L*1000*1000,
			1000L*1000*1000*1000,
			1000L*1000*1000*1000*1000,
			1000L*1000*1000*1000*1000*1000,
			0, 0, 0, 
			1L,
			1024L,
			1024L*1024,
			1024L*1024*1024,
			1024L*1024*1024*1024,
			1024L*1024*1024*1024*1024,
			1024L*1024*1024*1024*1024*1024,
		};
	
	public static long
	getUnitMultiplier(
		String		unit,
		boolean		treat_decimal_as_binary )
	{
		if ( unit.equals( "b" )){
			return( 1 );	// get a lot of these, optimize
		}
		
		unit = unit.toLowerCase( Locale.US );
		
		Integer val = unit_map.get( unit );
		
		if ( val == null ){
			
			return( -1 );
			
		}else{
			
			int index = val;
			
			if ( treat_decimal_as_binary && index < 10 ){
				
				index += 10;
			}
			
			return( unit_values[index] );
		}
	}

	public static void
	playSound(
		String			sound_file )
	{
		File	file;
		
		if ( sound_file == null ){
			
			file = null;
			
		}else{
			
			sound_file = sound_file.trim();
			
			if ( sound_file.isEmpty()){
				
				file = null;
				
			}else{
				
					// turn <default> into blank
			
				if ( sound_file.startsWith( "<" )){
				
					file = null;
					
				}else{
					
					File temp = new File( sound_file );
					
					if ( temp.exists()){
						
						file = temp;
						
					}else{
						
						Debug.out( "Audio file " + temp + " not found" );
						
						file = null;
					}
				}
			}
		}
		
		String default_sound 	= "com/biglybt/ui/icons/downloadFinished.wav";
		
		new AEThread2("SoundPlayer" ){
			@Override
			public void run() 
			{
				try{
					AudioInputStream ais;
					
					if ( file == null ){
		
						ais = AudioSystem.getAudioInputStream( 
									GeneralUtils.class.getClassLoader().getResourceAsStream( default_sound ));
				
					}else{
		
						ais  = AudioSystem.getAudioInputStream( file );
					}
					
				    Clip clip = AudioSystem.getClip();
		
				    clip.addLineListener(
				    	new LineListener(){
							
							@Override
							public void 
							update(
								LineEvent event)
							{
								if ( event.getType() == LineEvent.Type.STOP ){
									
									clip.close();
								}
							}
						});
						
				    clip.open( ais );
				        
				    clip.start();
					    
				    /* deprecated
				    	AudioClip clip;
						
						if ( file == null ){
													
							clip = Applet.newAudioClip(GeneralUtils.class.getClassLoader().getResource( default_sound ));

						}else{
							
							clip = Applet.newAudioClip(file.toURI().toURL());
						}
						
						clip.play();
					*/
					
					Thread.sleep(2500);
		
				} catch (Throwable e) {
				}
			}
		}.start();
	}
}
