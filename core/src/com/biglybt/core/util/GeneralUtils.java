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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintWriter;
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
	
	public static String stringJoin(Collection<?> list, String delim) {
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
	
	private static Map<Integer,Integer> confusable_map_1;
	private static Map<Integer,Integer> confusable_map_2;
	private static Map<String,String>	confusable_recent_1;
	private static Map<String,String>	confusable_recent_2;
	
	public static String
	getConfusableEquivalent(
		String		str,
		boolean		is_query )
	{
		synchronized( GeneralUtils.class ){
			
			if ( confusable_map_1 == null ){
	
					// These characters are remapped in both the query string and the target string
				
				int[][] map1 = new  int[][]{
					// These pairs of characters are easily confused with each other
					{0x00BA,0x00B0},	//	( º → ° ) MASCULINE ORDINAL INDICATOR → DEGREE SIGN
					{0x00DF,0x03B2},	//	( ß → β ) LATIN SMALL LETTER SHARP S → GREEK SMALL LETTER BETA	// has less potential for confusion than the other way around
					{0x01CE,0x0103},	//	( ǎ → ă ) LATIN SMALL LETTER A WITH CARON → LATIN SMALL LETTER A WITH BREVE
					{0x01CD,0x0102},	//	( Ǎ → Ă ) LATIN CAPITAL LETTER A WITH CARON → LATIN CAPITAL LETTER A WITH BREVE
					{0x011B,0x0115},	//	( ě → ĕ ) LATIN SMALL LETTER E WITH CARON → LATIN SMALL LETTER E WITH BREVE
					{0x011A,0x0114},	//	( Ě → Ĕ ) LATIN CAPITAL LETTER E WITH CARON → LATIN CAPITAL LETTER E WITH BREVE
					{0x01E7,0x011F},	//	( ǧ → ğ ) LATIN SMALL LETTER G WITH CARON → LATIN SMALL LETTER G WITH BREVE
					{0x01E6,0x011E},	//	( Ǧ → Ğ ) LATIN CAPITAL LETTER G WITH CARON → LATIN CAPITAL LETTER G WITH BREVE
					{0x01F5,0x0123},	//	( ǵ → ģ ) LATIN SMALL LETTER G WITH ACUTE → LATIN SMALL LETTER G WITH CEDILLA
					{0x01D0,0x012D},	//	( ǐ → ĭ ) LATIN SMALL LETTER I WITH CARON → LATIN SMALL LETTER I WITH BREVE
					{0x01CF,0x012C},	//	( Ǐ → Ĭ ) LATIN CAPITAL LETTER I WITH CARON → LATIN CAPITAL LETTER I WITH BREVE
					{0x01D2,0x014F},	//	( ǒ → ŏ ) LATIN SMALL LETTER O WITH CARON → LATIN SMALL LETTER O WITH BREVE
					{0x01D1,0x014E},	//	( Ǒ → Ŏ ) LATIN CAPITAL LETTER O WITH CARON → LATIN CAPITAL LETTER O WITH BREVE
					{0x01D4,0x016D},	//	( ǔ → ŭ ) LATIN SMALL LETTER U WITH CARON → LATIN SMALL LETTER U WITH BREVE
					{0x01D3,0x016C},	//	( Ǔ → Ŭ ) LATIN CAPITAL LETTER U WITH CARON → LATIN CAPITAL LETTER U WITH BREVE
					{0x00B5,0x03BC},	//	( µ → μ ) MICRO SIGN → GREEK SMALL LETTER MU
					{0x03DB,0x03C2},	//	( ϛ → ς ) GREEK SMALL LETTER STIGMA → GREEK SMALL LETTER FINAL SIGMA
					{0x040D,0x0419},	//	( Ѝ → Й ) CYRILLIC CAPITAL LETTER I WITH GRAVE → CYRILLIC CAPITAL LETTER SHORT I
					{0x045D,0x0439},	//	( ѝ → й ) CYRILLIC SMALL LETTER I WITH GRAVE → CYRILLIC SMALL LETTER SHORT I
					// Romanian users will probably appreciate these four
					{0x0218,0x015E},	//	( Ș → Ş ) LATIN CAPITAL LETTER S WITH COMMA BELOW → LATIN CAPITAL LETTER S WITH CEDILLA
					{0x0219,0x015F},	//	( ș → ş ) LATIN SMALL LETTER S WITH COMMA BELOW → LATIN SMALL LETTER S WITH CEDILLA
					{0x021A,0x0162},	//	( Ț → Ţ ) LATIN CAPITAL LETTER T WITH COMMA BELOW → LATIN CAPITAL LETTER T WITH CEDILLA
					{0x021B,0x0163},	//	( ț → ţ ) LATIN SMALL LETTER t WITH COMMA BELOW → LATIN SMALL LETTER T WITH CEDILLA
					// Fold Arabic-Indic variant digits
					{0x06F0,0x0660},	//	( ۰ → ٠ ) EXTENDED ARABIC-INDIC DIGIT ZERO → ARABIC-INDIC DIGIT ZERO
					{0x06F1,0x0661},	//	( ۱ → ١ ) EXTENDED ARABIC-INDIC DIGIT ONE → ARABIC-INDIC DIGIT ONE
					{0x06F2,0x0662},	//	( ۲ → ٢ ) EXTENDED ARABIC-INDIC DIGIT TWO → ARABIC-INDIC DIGIT TWO
					{0x06F3,0x0663},	//	( ۳ → ٣ ) EXTENDED ARABIC-INDIC DIGIT THREE → ARABIC-INDIC DIGIT THREE
					{0x06F4,0x0664},	//	( ۴ → ٤ ) EXTENDED ARABIC-INDIC DIGIT FOUR → ARABIC-INDIC DIGIT FOUR
					{0x06F5,0x0665},	//	( ۵ → ٥ ) EXTENDED ARABIC-INDIC DIGIT FIVE → ARABIC-INDIC DIGIT FIVE
					{0x06F6,0x0666},	//	( ۶ → ٦ ) EXTENDED ARABIC-INDIC DIGIT SIX → ARABIC-INDIC DIGIT SIX
					{0x06F7,0x0667},	//	( ۷ → ٧ ) EXTENDED ARABIC-INDIC DIGIT SEVEN → ARABIC-INDIC DIGIT SEVEN
					{0x06F8,0x0668},	//	( ۸ → ٨ ) EXTENDED ARABIC-INDIC DIGIT EIGHT → ARABIC-INDIC DIGIT EIGHT
					{0x06F9,0x0669},	//	( ۹ → ٩ ) EXTENDED ARABIC-INDIC DIGIT NINE → ARABIC-INDIC DIGIT NINE
				};
					
					// These characters are remapped in the target string only
				
				int[][] map2 = new  int[][]{
					{0x2028,0x0020},	//	(  →   ) LINE SEPARATOR → SPACE	#
					{0x2029,0x0020},	//	(  →   ) PARAGRAPH SEPARATOR → SPACE	#
					{0x1680,0x0020},	//	(   →   ) OGHAM SPACE MARK → SPACE	#
					{0x2000,0x0020},	//	(   →   ) EN QUAD → SPACE	#
					{0x2001,0x0020},	//	(   →   ) EM QUAD → SPACE	#
					{0x2002,0x0020},	//	(   →   ) EN SPACE → SPACE	#
					{0x2003,0x0020},	//	(   →   ) EM SPACE → SPACE	#
					{0x2004,0x0020},	//	(   →   ) THREE-PER-EM SPACE → SPACE	#
					{0x2005,0x0020},	//	(   →   ) FOUR-PER-EM SPACE → SPACE	#
					{0x2006,0x0020},	//	(   →   ) SIX-PER-EM SPACE → SPACE	#
					{0x2008,0x0020},	//	(   →   ) PUNCTUATION SPACE → SPACE	#
					{0x2009,0x0020},	//	(   →   ) THIN SPACE → SPACE	#
					{0x200A,0x0020},	//	(   →   ) HAIR SPACE → SPACE	#
					{0x205F,0x0020},	//	(   →   ) MEDIUM MATHEMATICAL SPACE → SPACE	#
					{0x00A0,0x0020},	//	(   →   ) NO-BREAK SPACE → SPACE	#
					{0x2007,0x0020},	//	(   →   ) FIGURE SPACE → SPACE	#
					{0x202F,0x0020},	//	(   →   ) NARROW NO-BREAK SPACE → SPACE	#
					{0x07FA,0x005F},	//	( ‎ߺ‎ → _ ) NKO LAJANYALAN → LOW LINE	#
					{0xFE4D,0x005F},	//	( ﹍ → _ ) DASHED LOW LINE → LOW LINE	#
					{0xFE4E,0x005F},	//	( ﹎ → _ ) CENTRELINE LOW LINE → LOW LINE	#
					{0xFE4F,0x005F},	//	( ﹏ → _ ) WAVY LOW LINE → LOW LINE	#
					{0x2010,0x002D},	//	( ‐ → - ) HYPHEN → HYPHEN-MINUS	#
					{0x2011,0x002D},	//	( ‑ → - ) NON-BREAKING HYPHEN → HYPHEN-MINUS	#
					{0x2012,0x002D},	//	( ‒ → - ) FIGURE DASH → HYPHEN-MINUS	#
					{0x2013,0x002D},	//	( – → - ) EN DASH → HYPHEN-MINUS	#
					{0xFE58,0x002D},	//	( ﹘ → - ) SMALL EM DASH → HYPHEN-MINUS	#
					{0x06D4,0x002D},	//	( ‎۔‎ → - ) ARABIC FULL STOP → HYPHEN-MINUS	# →‐→
					{0x2043,0x002D},	//	( ⁃ → - ) HYPHEN BULLET → HYPHEN-MINUS	# →‐→
					{0x02D7,0x002D},	//	( ˗ → - ) MODIFIER LETTER MINUS SIGN → HYPHEN-MINUS	#
					{0x2212,0x002D},	//	( − → - ) MINUS SIGN → HYPHEN-MINUS	#
					{0x2796,0x002D},	//	( ➖ → - ) HEAVY MINUS SIGN → HYPHEN-MINUS	# →−→
					{0x2CBA,0x002D},	//	( Ⲻ → - ) COPTIC CAPITAL LETTER DIALECT-P NI → HYPHEN-MINUS	# →‒→
					{0x060D,0x002C},	//	( ‎؍‎ → , ) ARABIC DATE SEPARATOR → COMMA	# →‎٫‎→
					{0x066B,0x002C},	//	( ‎٫‎ → , ) ARABIC DECIMAL SEPARATOR → COMMA	#
					{0x201A,0x002C},	//	( ‚ → , ) SINGLE LOW-9 QUOTATION MARK → COMMA	#
					{0x00B8,0x002C},	//	( ¸ → , ) CEDILLA → COMMA	#
					{0xA4F9,0x002C},	//	( ꓹ → , ) LISU LETTER TONE NA PO → COMMA	#
					{0x037E,0x003B},	//	( ; → ; ) GREEK QUESTION MARK → SEMICOLON	#
					{0x0903,0x003A},	//	( ः → : ) DEVANAGARI SIGN VISARGA → COLON	#
					{0x0A83,0x003A},	//	( ઃ → : ) GUJARATI SIGN VISARGA → COLON	#
					{0x0589,0x003A},	//	( ։ → : ) ARMENIAN FULL STOP → COLON	#
					{0x0703,0x003A},	//	( ‎܃‎ → : ) SYRIAC SUPRALINEAR COLON → COLON	#
					{0x0704,0x003A},	//	( ‎܄‎ → : ) SYRIAC SUBLINEAR COLON → COLON	#
					{0x16EC,0x003A},	//	( ᛬ → : ) RUNIC MULTIPLE PUNCTUATION → COLON	#
					{0xFE30,0x003A},	//	( ︰ → : ) PRESENTATION FORM FOR VERTICAL TWO DOT LEADER → COLON	#
					{0x1803,0x003A},	//	( ᠃ → : ) MONGOLIAN FULL STOP → COLON	#
					{0x1809,0x003A},	//	( ᠉ → : ) MONGOLIAN MANCHU FULL STOP → COLON	#
					{0x205A,0x003A},	//	( ⁚ → : ) TWO DOT PUNCTUATION → COLON	#
					{0x05C3,0x003A},	//	( ‎׃‎ → : ) HEBREW PUNCTUATION SOF PASUQ → COLON	#
					{0x02F8,0x003A},	//	( ˸ → : ) MODIFIER LETTER RAISED COLON → COLON	#
					{0xA789,0x003A},	//	( ꞉ → : ) MODIFIER LETTER COLON → COLON	#
					{0x2236,0x003A},	//	( ∶ → : ) RATIO → COLON	#
					{0x02D0,0x003A},	//	( ː → : ) MODIFIER LETTER TRIANGULAR COLON → COLON	#
					{0xA4FD,0x003A},	//	( ꓽ → : ) LISU LETTER TONE MYA JEU → COLON	#
					{0x01C3,0x0021},	//	( ǃ → ! ) LATIN LETTER RETROFLEX CLICK → EXCLAMATION MARK	#
					{0x2D51,0x0021},	//	( ⵑ → ! ) TIFINAGH LETTER TUAREG YANG → EXCLAMATION MARK	#
					{0x0294,0x003F},	//	( ʔ → ? ) LATIN LETTER GLOTTAL STOP → QUESTION MARK	#
					{0x0241,0x003F},	//	( Ɂ → ? ) LATIN CAPITAL LETTER GLOTTAL STOP → QUESTION MARK	# →ʔ→
					{0x097D,0x003F},	//	( ॽ → ? ) DEVANAGARI LETTER GLOTTAL STOP → QUESTION MARK	#
					{0x13AE,0x003F},	//	( Ꭾ → ? ) CHEROKEE LETTER HE → QUESTION MARK	# →Ɂ→→ʔ→
					{0xA6EB,0x003F},	//	( ꛫ → ? ) BAMUM LETTER NTUU → QUESTION MARK	# →ʔ→
					{0x1D16D,0x002E},	//	( 𝅭 → . ) MUSICAL SYMBOL COMBINING AUGMENTATION DOT → FULL STOP	#
					{0x2024,0x002E},	//	( ․ → . ) ONE DOT LEADER → FULL STOP	#
					{0x0701,0x002E},	//	( ‎܁‎ → . ) SYRIAC SUPRALINEAR FULL STOP → FULL STOP	#
					{0x0702,0x002E},	//	( ‎܂‎ → . ) SYRIAC SUBLINEAR FULL STOP → FULL STOP	#
					{0xA60E,0x002E},	//	( ꘎ → . ) VAI FULL STOP → FULL STOP	#
					{0x10A50,0x002E},	//	( ‎𐩐‎ → . ) KHAROSHTHI PUNCTUATION DOT → FULL STOP	#
					//{0x0660,0x002E},	//	( ‎٠‎ → . ) ARABIC-INDIC DIGIT ZERO → FULL STOP	#	// users of Arabic script are unlikely to appreciate conflating these two
					//{0x06F0,0x002E},	//	( ۰ → . ) EXTENDED ARABIC-INDIC DIGIT ZERO → FULL STOP	# →‎٠‎→	// users of Arabic script are unlikely to appreciate conflating these two
					{0xA4F8,0x002E},	//	( ꓸ → . ) LISU LETTER TONE MYA TI → FULL STOP	#
					{0x30FB,0x00B7},	//	( ・ → · ) KATAKANA MIDDLE DOT → MIDDLE DOT	# →•→
					{0xFF65,0x00B7},	//	( ･ → · ) HALFWIDTH KATAKANA MIDDLE DOT → MIDDLE DOT	# →•→
					{0x16EB,0x00B7},	//	( ᛫ → · ) RUNIC SINGLE PUNCTUATION → MIDDLE DOT	#
					{0x0387,0x00B7},	//	( · → · ) GREEK ANO TELEIA → MIDDLE DOT	#
					{0x2E31,0x00B7},	//	( ⸱ → · ) WORD SEPARATOR MIDDLE DOT → MIDDLE DOT	#
					{0x10101,0x00B7},	//	( 𐄁 → · ) AEGEAN WORD SEPARATOR DOT → MIDDLE DOT	#
					{0x2022,0x00B7},	//	( • → · ) BULLET → MIDDLE DOT	#
					{0x2027,0x00B7},	//	( ‧ → · ) HYPHENATION POINT → MIDDLE DOT	#
					{0x2219,0x00B7},	//	( ∙ → · ) BULLET OPERATOR → MIDDLE DOT	#
					{0x22C5,0x00B7},	//	( ⋅ → · ) DOT OPERATOR → MIDDLE DOT	#
					{0xA78F,0x00B7},	//	( ꞏ → · ) LATIN LETTER SINOLOGICAL DOT → MIDDLE DOT	#
					{0x1427,0x00B7},	//	( ᐧ → · ) CANADIAN SYLLABICS FINAL MIDDLE DOT → MIDDLE DOT	#
					{0x055D,0x0027},	//	( ՝ → ' ) ARMENIAN COMMA → APOSTROPHE	# →ˋ→→｀→→‘→
					{0x2018,0x0027},	//	( ‘ → ' ) LEFT SINGLE QUOTATION MARK → APOSTROPHE	#
					{0x2019,0x0027},	//	( ’ → ' ) RIGHT SINGLE QUOTATION MARK → APOSTROPHE	#
					{0x201B,0x0027},	//	( ‛ → ' ) SINGLE HIGH-REVERSED-9 QUOTATION MARK → APOSTROPHE	# →′→
					{0x2032,0x0027},	//	( ′ → ' ) PRIME → APOSTROPHE	#
					{0x055A,0x0027},	//	( ՚ → ' ) ARMENIAN APOSTROPHE → APOSTROPHE	# →’→
					{0x05F3,0x0027},	//	( ‎׳‎ → ' ) HEBREW PUNCTUATION GERESH → APOSTROPHE	#
					{0x00B4,0x0027},	//	( ´ → ' ) ACUTE ACCENT → APOSTROPHE	# →΄→→ʹ→
					{0x0384,0x0027},	//	( ΄ → ' ) GREEK TONOS → APOSTROPHE	# →ʹ→
					{0x1FFD,0x0027},	//	( ´ → ' ) GREEK OXIA → APOSTROPHE	# →´→→΄→→ʹ→
					{0x1FBD,0x0027},	//	( ᾽ → ' ) GREEK KORONIS → APOSTROPHE	# →’→
					{0x1FBF,0x0027},	//	( ᾿ → ' ) GREEK PSILI → APOSTROPHE	# →’→
					{0x02B9,0x0027},	//	( ʹ → ' ) MODIFIER LETTER PRIME → APOSTROPHE	#
					{0x0374,0x0027},	//	( ʹ → ' ) GREEK NUMERAL SIGN → APOSTROPHE	# →′→
					{0x02C8,0x0027},	//	( ˈ → ' ) MODIFIER LETTER VERTICAL LINE → APOSTROPHE	#
					{0x02CA,0x0027},	//	( ˊ → ' ) MODIFIER LETTER ACUTE ACCENT → APOSTROPHE	# →ʹ→→′→
					{0x02BB,0x0027},	//	( ʻ → ' ) MODIFIER LETTER TURNED COMMA → APOSTROPHE	# →‘→
					{0x02BD,0x0027},	//	( ʽ → ' ) MODIFIER LETTER REVERSED COMMA → APOSTROPHE	# →‘→
					{0x02BC,0x0027},	//	( ʼ → ' ) MODIFIER LETTER APOSTROPHE → APOSTROPHE	# →′→
					{0x02BE,0x0027},	//	( ʾ → ' ) MODIFIER LETTER RIGHT HALF RING → APOSTROPHE	# →ʼ→→′→
					{0xA78C,0x0027},	//	( ꞌ → ' ) LATIN SMALL LETTER SALTILLO → APOSTROPHE	#
					{0x05D9,0x0027},	//	( ‎י‎ → ' ) HEBREW LETTER YOD → APOSTROPHE	#
					{0x07F4,0x0027},	//	( ‎ߴ‎ → ' ) NKO HIGH TONE APOSTROPHE → APOSTROPHE	# →’→
					{0x07F5,0x0027},	//	( ‎ߵ‎ → ' ) NKO LOW TONE APOSTROPHE → APOSTROPHE	# →‘→
					{0x144A,0x0027},	//	( ᑊ → ' ) CANADIAN SYLLABICS WEST-CREE P → APOSTROPHE	# →ˈ→
					{0x16CC,0x0027},	//	( ᛌ → ' ) RUNIC LETTER SHORT-TWIG-SOL S → APOSTROPHE	#
					{0x16F51,0x0027},	//	( 𖽑 → ' ) MIAO SIGN ASPIRATION → APOSTROPHE	# →ʼ→→′→
					{0x16F52,0x0027},	//	( 𖽒 → ' ) MIAO SIGN REFORMED VOICING → APOSTROPHE	# →ʻ→→‘→
					// The following 5 characters were originally mapped to APOSTROPHE, but have been changed to map to GRAVE ACCENT because the latter is no longer mapped to the former.
					{0x2035,0x0060},	//	( ‵ → ` ) REVERSED PRIME → GRAVE ACCENT	#
					{0x1FEF,0x0060},	//	( ` → ` ) GREEK VARIA → GRAVE ACCENT	#
					{0x1FFE,0x0060},	//	( ῾ → ` ) GREEK DASIA → GRAVE ACCENT	#
					{0x02CB,0x0060},	//	( ˋ → ` ) MODIFIER LETTER GRAVE ACCENT → GRAVE ACCENT	#
					{0x02F4,0x0060},	//	( ˴ → ` ) MODIFIER LETTER MIDDLE GRAVE ACCENT → GRAVE ACCENT	#
					{0x2768,0x0028},	//	( ❨ → ( ) MEDIUM LEFT PARENTHESIS ORNAMENT → LEFT PARENTHESIS	#
					{0x2772,0x0028},	//	( ❲ → ( ) LIGHT LEFT TORTOISE SHELL BRACKET ORNAMENT → LEFT PARENTHESIS	# →〔→
					{0x3014,0x0028},	//	( 〔 → ( ) LEFT TORTOISE SHELL BRACKET → LEFT PARENTHESIS	#
					{0xFD3E,0x0028},	//	( ﴾ → ( ) ORNATE LEFT PARENTHESIS → LEFT PARENTHESIS	#
					{0x2769,0x0029},	//	( ❩ → ) ) MEDIUM RIGHT PARENTHESIS ORNAMENT → RIGHT PARENTHESIS	#
					{0x2773,0x0029},	//	( ❳ → ) ) LIGHT RIGHT TORTOISE SHELL BRACKET ORNAMENT → RIGHT PARENTHESIS	# →〕→
					{0x3015,0x0029},	//	( 〕 → ) ) RIGHT TORTOISE SHELL BRACKET → RIGHT PARENTHESIS	#
					{0xFD3F,0x0029},	//	( ﴿ → ) ) ORNATE RIGHT PARENTHESIS → RIGHT PARENTHESIS	#
					{0x2774,0x007B},	//	( ❴ → { ) MEDIUM LEFT CURLY BRACKET ORNAMENT → LEFT CURLY BRACKET	#
					{0x1D114,0x007B},	//	( 𝄔 → { ) MUSICAL SYMBOL BRACE → LEFT CURLY BRACKET	#
					{0x2775,0x007D},	//	( ❵ → } ) MEDIUM RIGHT CURLY BRACKET ORNAMENT → RIGHT CURLY BRACKET	#
					{0x2E3F,0x00B6},	//	( ⸿ → ¶ ) CAPITULUM → PILCROW SIGN	#
					{0x204E,0x002A},	//	( ⁎ → * ) LOW ASTERISK → ASTERISK	#
					{0x066D,0x002A},	//	( ‎٭‎ → * ) ARABIC FIVE POINTED STAR → ASTERISK	#
					{0x2217,0x002A},	//	( ∗ → * ) ASTERISK OPERATOR → ASTERISK	#
					{0x1031F,0x002A},	//	( 𐌟 → * ) OLD ITALIC LETTER ESS → ASTERISK	#
					{0x1735,0x002F},	//	( ᜵ → / ) PHILIPPINE SINGLE PUNCTUATION → SOLIDUS	#
					{0x2041,0x002F},	//	( ⁁ → / ) CARET INSERTION POINT → SOLIDUS	#
					{0x2215,0x002F},	//	( ∕ → / ) DIVISION SLASH → SOLIDUS	#
					{0x2044,0x002F},	//	( ⁄ → / ) FRACTION SLASH → SOLIDUS	#
					{0x2571,0x002F},	//	( ╱ → / ) BOX DRAWINGS LIGHT DIAGONAL UPPER RIGHT TO LOWER LEFT → SOLIDUS	#
					{0x27CB,0x002F},	//	( ⟋ → / ) MATHEMATICAL RISING DIAGONAL → SOLIDUS	#
					{0x29F8,0x002F},	//	( ⧸ → / ) BIG SOLIDUS → SOLIDUS	#
					{0x1D23A,0x002F},	//	( 𝈺 → / ) GREEK INSTRUMENTAL NOTATION SYMBOL-47 → SOLIDUS	#
					{0x31D3,0x002F},	//	( ㇓ → / ) CJK STROKE SP → SOLIDUS	# →⼃→
					{0x3033,0x002F},	//	( 〳 → / ) VERTICAL KANA REPEAT MARK UPPER HALF → SOLIDUS	#
					{0x2CC6,0x002F},	//	( Ⳇ → / ) COPTIC CAPITAL LETTER OLD COPTIC ESH → SOLIDUS	#
					{0x30CE,0x002F},	//	( ノ → / ) KATAKANA LETTER NO → SOLIDUS	# →⼃→
					{0x4E3F,0x002F},	//	( 丿 → / ) CJK UNIFIED IDEOGRAPH-4E3F → SOLIDUS	# →⼃→
					{0x2F03,0x002F},	//	( ⼃ → / ) KANGXI RADICAL SLASH → SOLIDUS	#
					{0xFE68,0x005C},	//	( ﹨ → \ ) SMALL REVERSE SOLIDUS → REVERSE SOLIDUS	# →∖→
					{0x2216,0x005C},	//	( ∖ → \ ) SET MINUS → REVERSE SOLIDUS	#
					{0x27CD,0x005C},	//	( ⟍ → \ ) MATHEMATICAL FALLING DIAGONAL → REVERSE SOLIDUS	#
					{0x29F5,0x005C},	//	( ⧵ → \ ) REVERSE SOLIDUS OPERATOR → REVERSE SOLIDUS	#
					{0x29F9,0x005C},	//	( ⧹ → \ ) BIG REVERSE SOLIDUS → REVERSE SOLIDUS	#
					{0x1D20F,0x005C},	//	( 𝈏 → \ ) GREEK VOCAL NOTATION SYMBOL-16 → REVERSE SOLIDUS	#
					{0x1D23B,0x005C},	//	( 𝈻 → \ ) GREEK INSTRUMENTAL NOTATION SYMBOL-48 → REVERSE SOLIDUS	# →𝈏→
					{0x31D4,0x005C},	//	( ㇔ → \ ) CJK STROKE D → REVERSE SOLIDUS	# →⼂→
					{0x4E36,0x005C},	//	( 丶 → \ ) CJK UNIFIED IDEOGRAPH-4E36 → REVERSE SOLIDUS	# →⼂→
					{0x2F02,0x005C},	//	( ⼂ → \ ) KANGXI RADICAL DOT → REVERSE SOLIDUS	#
					{0xA778,0x0026},	//	( ꝸ → & ) LATIN SMALL LETTER UM → AMPERSAND	#
					{0x02C4,0x005E},	//	( ˄ → ^ ) MODIFIER LETTER UP ARROWHEAD → CIRCUMFLEX ACCENT	#
					{0x02C6,0x005E},	//	( ˆ → ^ ) MODIFIER LETTER CIRCUMFLEX ACCENT → CIRCUMFLEX ACCENT	#
					{0x2E30,0x00B0},	//	( ⸰ → ° ) RING POINT → DEGREE SIGN	# →∘→
					{0x02DA,0x00B0},	//	( ˚ → ° ) RING ABOVE → DEGREE SIGN	#
					{0x2218,0x00B0},	//	( ∘ → ° ) RING OPERATOR → DEGREE SIGN	#
					{0x25CB,0x00B0},	//	( ○ → ° ) WHITE CIRCLE → DEGREE SIGN	# →◦→→∘→
					{0x25E6,0x00B0},	//	( ◦ → ° ) WHITE BULLET → DEGREE SIGN	# →∘→
					{0x24B8,0x00A9},	//	( Ⓒ → © ) CIRCLED LATIN CAPITAL LETTER C → COPYRIGHT SIGN	#
					{0x24C7,0x00AE},	//	( Ⓡ → ® ) CIRCLED LATIN CAPITAL LETTER R → REGISTERED SIGN	#
					{0x16ED,0x002B},	//	( ᛭ → + ) RUNIC CROSS PUNCTUATION → PLUS SIGN	#
					{0x2795,0x002B},	//	( ➕ → + ) HEAVY PLUS SIGN → PLUS SIGN	#
					{0x1029B,0x002B},	//	( 𐊛 → + ) LYCIAN LETTER H → PLUS SIGN	#
					{0x2797,0x00F7},	//	( ➗ → ÷ ) HEAVY DIVISION SIGN → DIVISION SIGN	#
					{0x2039,0x003C},	//	( ‹ → < ) SINGLE LEFT-POINTING ANGLE QUOTATION MARK → LESS-THAN SIGN	#
					{0x276E,0x003C},	//	( ❮ → < ) HEAVY LEFT-POINTING ANGLE QUOTATION MARK ORNAMENT → LESS-THAN SIGN	# →‹→
					{0x02C2,0x003C},	//	( ˂ → < ) MODIFIER LETTER LEFT ARROWHEAD → LESS-THAN SIGN	#
					{0x1D236,0x003C},	//	( 𝈶 → < ) GREEK INSTRUMENTAL NOTATION SYMBOL-40 → LESS-THAN SIGN	#
					{0x1438,0x003C},	//	( ᐸ → < ) CANADIAN SYLLABICS PA → LESS-THAN SIGN	#
					{0x16B2,0x003C},	//	( ᚲ → < ) RUNIC LETTER KAUNA → LESS-THAN SIGN	#
					{0x1400,0x003D},	//	( ᐀ → = ) CANADIAN SYLLABICS HYPHEN → EQUALS SIGN	#
					{0x2E40,0x003D},	//	( ⹀ → = ) DOUBLE HYPHEN → EQUALS SIGN	#
					{0x30A0,0x003D},	//	( ゠ → = ) KATAKANA-HIRAGANA DOUBLE HYPHEN → EQUALS SIGN	#
					{0xA4FF,0x003D},	//	( ꓿ → = ) LISU PUNCTUATION FULL STOP → EQUALS SIGN	#
					{0x203A,0x003E},	//	( › → > ) SINGLE RIGHT-POINTING ANGLE QUOTATION MARK → GREATER-THAN SIGN	#
					{0x276F,0x003E},	//	( ❯ → > ) HEAVY RIGHT-POINTING ANGLE QUOTATION MARK ORNAMENT → GREATER-THAN SIGN	# →›→
					{0x02C3,0x003E},	//	( ˃ → > ) MODIFIER LETTER RIGHT ARROWHEAD → GREATER-THAN SIGN	#
					{0x1D237,0x003E},	//	( 𝈷 → > ) GREEK INSTRUMENTAL NOTATION SYMBOL-42 → GREATER-THAN SIGN	#
					{0x1433,0x003E},	//	( ᐳ → > ) CANADIAN SYLLABICS PO → GREATER-THAN SIGN	#
					{0x16F3F,0x003E},	//	( 𖼿 → > ) MIAO LETTER ARCHAIC ZZA → GREATER-THAN SIGN	#
					{0x2053,0x007E},	//	( ⁓ → ~ ) SWUNG DASH → TILDE	#
					{0x02DC,0x007E},	//	( ˜ → ~ ) SMALL TILDE → TILDE	#
					{0x1FC0,0x007E},	//	( ῀ → ~ ) GREEK PERISPOMENI → TILDE	# →˜→
					{0x223C,0x007E},	//	( ∼ → ~ ) TILDE OPERATOR → TILDE	#
					{0x20A4,0x00A3},	//	( ₤ → £ ) LIRA SIGN → POUND SIGN	#
					{0x1D7D0,0x0032},	//	( 𝟐 → 2 ) MATHEMATICAL BOLD DIGIT TWO → DIGIT TWO	#
					{0x1D7DA,0x0032},	//	( 𝟚 → 2 ) MATHEMATICAL DOUBLE-STRUCK DIGIT TWO → DIGIT TWO	#
					{0x1D7E4,0x0032},	//	( 𝟤 → 2 ) MATHEMATICAL SANS-SERIF DIGIT TWO → DIGIT TWO	#
					{0x1D7EE,0x0032},	//	( 𝟮 → 2 ) MATHEMATICAL SANS-SERIF BOLD DIGIT TWO → DIGIT TWO	#
					{0x1D7F8,0x0032},	//	( 𝟸 → 2 ) MATHEMATICAL MONOSPACE DIGIT TWO → DIGIT TWO	#
					{0x1FBF2,0x0032},	//	( 🯲 → 2 ) SEGMENTED DIGIT TWO → DIGIT TWO	#
					{0xA75A,0x0032},	//	( Ꝛ → 2 ) LATIN CAPITAL LETTER R ROTUNDA → DIGIT TWO	#
					{0x01A7,0x0032},	//	( Ƨ → 2 ) LATIN CAPITAL LETTER TONE TWO → DIGIT TWO	#
					{0x03E8,0x0032},	//	( Ϩ → 2 ) COPTIC CAPITAL LETTER HORI → DIGIT TWO	# →Ƨ→
					{0xA644,0x0032},	//	( Ꙅ → 2 ) CYRILLIC CAPITAL LETTER REVERSED DZE → DIGIT TWO	# →Ƨ→
					{0x14BF,0x0032},	//	( ᒿ → 2 ) CANADIAN SYLLABICS SAYISI M → DIGIT TWO	#
					{0xA6EF,0x0032},	//	( ꛯ → 2 ) BAMUM LETTER KOGHOM → DIGIT TWO	# →Ƨ→
					{0x1D206,0x0033},	//	( 𝈆 → 3 ) GREEK VOCAL NOTATION SYMBOL-7 → DIGIT THREE	#
					{0x1D7D1,0x0033},	//	( 𝟑 → 3 ) MATHEMATICAL BOLD DIGIT THREE → DIGIT THREE	#
					{0x1D7DB,0x0033},	//	( 𝟛 → 3 ) MATHEMATICAL DOUBLE-STRUCK DIGIT THREE → DIGIT THREE	#
					{0x1D7E5,0x0033},	//	( 𝟥 → 3 ) MATHEMATICAL SANS-SERIF DIGIT THREE → DIGIT THREE	#
					{0x1D7EF,0x0033},	//	( 𝟯 → 3 ) MATHEMATICAL SANS-SERIF BOLD DIGIT THREE → DIGIT THREE	#
					{0x1D7F9,0x0033},	//	( 𝟹 → 3 ) MATHEMATICAL MONOSPACE DIGIT THREE → DIGIT THREE	#
					{0x1FBF3,0x0033},	//	( 🯳 → 3 ) SEGMENTED DIGIT THREE → DIGIT THREE	#
					{0xA7AB,0x0033},	//	( Ɜ → 3 ) LATIN CAPITAL LETTER REVERSED OPEN E → DIGIT THREE	#
					{0x021C,0x0033},	//	( Ȝ → 3 ) LATIN CAPITAL LETTER YOGH → DIGIT THREE	# →Ʒ→
					{0x01B7,0x0033},	//	( Ʒ → 3 ) LATIN CAPITAL LETTER EZH → DIGIT THREE	#
					{0xA76A,0x0033},	//	( Ꝫ → 3 ) LATIN CAPITAL LETTER ET → DIGIT THREE	#
					{0x2CCC,0x0033},	//	( Ⳍ → 3 ) COPTIC CAPITAL LETTER OLD COPTIC HORI → DIGIT THREE	# →Ȝ→→Ʒ→
					{0x0417,0x0033},	//	( З → 3 ) CYRILLIC CAPITAL LETTER ZE → DIGIT THREE	#
					{0x04E0,0x0033},	//	( Ӡ → 3 ) CYRILLIC CAPITAL LETTER ABKHASIAN DZE → DIGIT THREE	# →Ʒ→
					{0x16F3B,0x0033},	//	( 𖼻 → 3 ) MIAO LETTER ZA → DIGIT THREE	# →Ʒ→
					{0x118CA,0x0033},	//	( 𑣊 → 3 ) WARANG CITI SMALL LETTER ANG → DIGIT THREE	#
					{0x1D7D2,0x0034},	//	( 𝟒 → 4 ) MATHEMATICAL BOLD DIGIT FOUR → DIGIT FOUR	#
					{0x1D7DC,0x0034},	//	( 𝟜 → 4 ) MATHEMATICAL DOUBLE-STRUCK DIGIT FOUR → DIGIT FOUR	#
					{0x1D7E6,0x0034},	//	( 𝟦 → 4 ) MATHEMATICAL SANS-SERIF DIGIT FOUR → DIGIT FOUR	#
					{0x1D7F0,0x0034},	//	( 𝟰 → 4 ) MATHEMATICAL SANS-SERIF BOLD DIGIT FOUR → DIGIT FOUR	#
					{0x1D7FA,0x0034},	//	( 𝟺 → 4 ) MATHEMATICAL MONOSPACE DIGIT FOUR → DIGIT FOUR	#
					{0x1FBF4,0x0034},	//	( 🯴 → 4 ) SEGMENTED DIGIT FOUR → DIGIT FOUR	#
					{0x13CE,0x0034},	//	( Ꮞ → 4 ) CHEROKEE LETTER SE → DIGIT FOUR	#
					{0x118AF,0x0034},	//	( 𑢯 → 4 ) WARANG CITI CAPITAL LETTER UC → DIGIT FOUR	#
					{0x1D7D3,0x0035},	//	( 𝟓 → 5 ) MATHEMATICAL BOLD DIGIT FIVE → DIGIT FIVE	#
					{0x1D7DD,0x0035},	//	( 𝟝 → 5 ) MATHEMATICAL DOUBLE-STRUCK DIGIT FIVE → DIGIT FIVE	#
					{0x1D7E7,0x0035},	//	( 𝟧 → 5 ) MATHEMATICAL SANS-SERIF DIGIT FIVE → DIGIT FIVE	#
					{0x1D7F1,0x0035},	//	( 𝟱 → 5 ) MATHEMATICAL SANS-SERIF BOLD DIGIT FIVE → DIGIT FIVE	#
					{0x1D7FB,0x0035},	//	( 𝟻 → 5 ) MATHEMATICAL MONOSPACE DIGIT FIVE → DIGIT FIVE	#
					{0x1FBF5,0x0035},	//	( 🯵 → 5 ) SEGMENTED DIGIT FIVE → DIGIT FIVE	#
					{0x01BC,0x0035},	//	( Ƽ → 5 ) LATIN CAPITAL LETTER TONE FIVE → DIGIT FIVE	#
					{0x118BB,0x0035},	//	( 𑢻 → 5 ) WARANG CITI CAPITAL LETTER HORR → DIGIT FIVE	#
					{0x1D7D4,0x0036},	//	( 𝟔 → 6 ) MATHEMATICAL BOLD DIGIT SIX → DIGIT SIX	#
					{0x1D7DE,0x0036},	//	( 𝟞 → 6 ) MATHEMATICAL DOUBLE-STRUCK DIGIT SIX → DIGIT SIX	#
					{0x1D7E8,0x0036},	//	( 𝟨 → 6 ) MATHEMATICAL SANS-SERIF DIGIT SIX → DIGIT SIX	#
					{0x1D7F2,0x0036},	//	( 𝟲 → 6 ) MATHEMATICAL SANS-SERIF BOLD DIGIT SIX → DIGIT SIX	#
					{0x1D7FC,0x0036},	//	( 𝟼 → 6 ) MATHEMATICAL MONOSPACE DIGIT SIX → DIGIT SIX	#
					{0x1FBF6,0x0036},	//	( 🯶 → 6 ) SEGMENTED DIGIT SIX → DIGIT SIX	#
					{0x2CD2,0x0036},	//	( Ⳓ → 6 ) COPTIC CAPITAL LETTER OLD COPTIC HEI → DIGIT SIX	#
					{0x0431,0x0036},	//	( б → 6 ) CYRILLIC SMALL LETTER BE → DIGIT SIX	#	// breaks case-insensitive fuzzy match to CYRILLIC CAPITAL LETTER BE, alas
					{0x13EE,0x0036},	//	( Ꮾ → 6 ) CHEROKEE LETTER WV → DIGIT SIX	#
					{0x118D5,0x0036},	//	( 𑣕 → 6 ) WARANG CITI SMALL LETTER AT → DIGIT SIX	#
					{0x1D212,0x0037},	//	( 𝈒 → 7 ) GREEK VOCAL NOTATION SYMBOL-19 → DIGIT SEVEN	#
					{0x1D7D5,0x0037},	//	( 𝟕 → 7 ) MATHEMATICAL BOLD DIGIT SEVEN → DIGIT SEVEN	#
					{0x1D7DF,0x0037},	//	( 𝟟 → 7 ) MATHEMATICAL DOUBLE-STRUCK DIGIT SEVEN → DIGIT SEVEN	#
					{0x1D7E9,0x0037},	//	( 𝟩 → 7 ) MATHEMATICAL SANS-SERIF DIGIT SEVEN → DIGIT SEVEN	#
					{0x1D7F3,0x0037},	//	( 𝟳 → 7 ) MATHEMATICAL SANS-SERIF BOLD DIGIT SEVEN → DIGIT SEVEN	#
					{0x1D7FD,0x0037},	//	( 𝟽 → 7 ) MATHEMATICAL MONOSPACE DIGIT SEVEN → DIGIT SEVEN	#
					{0x1FBF7,0x0037},	//	( 🯷 → 7 ) SEGMENTED DIGIT SEVEN → DIGIT SEVEN	#
					{0x104D2,0x0037},	//	( 𐓒 → 7 ) OSAGE CAPITAL LETTER ZA → DIGIT SEVEN	#
					{0x118C6,0x0037},	//	( 𑣆 → 7 ) WARANG CITI SMALL LETTER II → DIGIT SEVEN	#
					{0x0B03,0x0038},	//	( ଃ → 8 ) ORIYA SIGN VISARGA → DIGIT EIGHT	#
					{0x09EA,0x0038},	//	( ৪ → 8 ) BENGALI DIGIT FOUR → DIGIT EIGHT	#
					{0x0A6A,0x0038},	//	( ੪ → 8 ) GURMUKHI DIGIT FOUR → DIGIT EIGHT	#
					{0x1E8CB,0x0038},	//	( ‎𞣋‎ → 8 ) MENDE KIKAKUI DIGIT FIVE → DIGIT EIGHT	#
					{0x1D7D6,0x0038},	//	( 𝟖 → 8 ) MATHEMATICAL BOLD DIGIT EIGHT → DIGIT EIGHT	#
					{0x1D7E0,0x0038},	//	( 𝟠 → 8 ) MATHEMATICAL DOUBLE-STRUCK DIGIT EIGHT → DIGIT EIGHT	#
					{0x1D7EA,0x0038},	//	( 𝟪 → 8 ) MATHEMATICAL SANS-SERIF DIGIT EIGHT → DIGIT EIGHT	#
					{0x1D7F4,0x0038},	//	( 𝟴 → 8 ) MATHEMATICAL SANS-SERIF BOLD DIGIT EIGHT → DIGIT EIGHT	#
					{0x1D7FE,0x0038},	//	( 𝟾 → 8 ) MATHEMATICAL MONOSPACE DIGIT EIGHT → DIGIT EIGHT	#
					{0x1FBF8,0x0038},	//	( 🯸 → 8 ) SEGMENTED DIGIT EIGHT → DIGIT EIGHT	#
					{0x0223,0x0038},	//	( ȣ → 8 ) LATIN SMALL LETTER OU → DIGIT EIGHT	#
					{0x0222,0x0038},	//	( Ȣ → 8 ) LATIN CAPITAL LETTER OU → DIGIT EIGHT	#
					{0x1031A,0x0038},	//	( 𐌚 → 8 ) OLD ITALIC LETTER EF → DIGIT EIGHT	#
					{0x0A67,0x0039},	//	( ੧ → 9 ) GURMUKHI DIGIT ONE → DIGIT NINE	#
					{0x0B68,0x0039},	//	( ୨ → 9 ) ORIYA DIGIT TWO → DIGIT NINE	#
					{0x09ED,0x0039},	//	( ৭ → 9 ) BENGALI DIGIT SEVEN → DIGIT NINE	#
					{0x0D6D,0x0039},	//	( ൭ → 9 ) MALAYALAM DIGIT SEVEN → DIGIT NINE	#
					{0x1D7D7,0x0039},	//	( 𝟗 → 9 ) MATHEMATICAL BOLD DIGIT NINE → DIGIT NINE	#
					{0x1D7E1,0x0039},	//	( 𝟡 → 9 ) MATHEMATICAL DOUBLE-STRUCK DIGIT NINE → DIGIT NINE	#
					{0x1D7EB,0x0039},	//	( 𝟫 → 9 ) MATHEMATICAL SANS-SERIF DIGIT NINE → DIGIT NINE	#
					{0x1D7F5,0x0039},	//	( 𝟵 → 9 ) MATHEMATICAL SANS-SERIF BOLD DIGIT NINE → DIGIT NINE	#
					{0x1D7FF,0x0039},	//	( 𝟿 → 9 ) MATHEMATICAL MONOSPACE DIGIT NINE → DIGIT NINE	#
					{0x1FBF9,0x0039},	//	( 🯹 → 9 ) SEGMENTED DIGIT NINE → DIGIT NINE	#
					{0xA76E,0x0039},	//	( Ꝯ → 9 ) LATIN CAPITAL LETTER CON → DIGIT NINE	#
					{0x2CCA,0x0039},	//	( Ⳋ → 9 ) COPTIC CAPITAL LETTER DIALECT-P HORI → DIGIT NINE	#
					{0x118CC,0x0039},	//	( 𑣌 → 9 ) WARANG CITI SMALL LETTER KO → DIGIT NINE	#
					{0x118AC,0x0039},	//	( 𑢬 → 9 ) WARANG CITI CAPITAL LETTER KO → DIGIT NINE	#
					{0x118D6,0x0039},	//	( 𑣖 → 9 ) WARANG CITI SMALL LETTER AM → DIGIT NINE	#
					{0x237A,0x0061},	//	( ⍺ → a ) APL FUNCTIONAL SYMBOL ALPHA → LATIN SMALL LETTER A	# →α→
					{0x1D41A,0x0061},	//	( 𝐚 → a ) MATHEMATICAL BOLD SMALL A → LATIN SMALL LETTER A	#
					{0x1D44E,0x0061},	//	( 𝑎 → a ) MATHEMATICAL ITALIC SMALL A → LATIN SMALL LETTER A	#
					{0x1D482,0x0061},	//	( 𝒂 → a ) MATHEMATICAL BOLD ITALIC SMALL A → LATIN SMALL LETTER A	#
					{0x1D4B6,0x0061},	//	( 𝒶 → a ) MATHEMATICAL SCRIPT SMALL A → LATIN SMALL LETTER A	#
					{0x1D4EA,0x0061},	//	( 𝓪 → a ) MATHEMATICAL BOLD SCRIPT SMALL A → LATIN SMALL LETTER A	#
					{0x1D51E,0x0061},	//	( 𝔞 → a ) MATHEMATICAL FRAKTUR SMALL A → LATIN SMALL LETTER A	#
					{0x1D552,0x0061},	//	( 𝕒 → a ) MATHEMATICAL DOUBLE-STRUCK SMALL A → LATIN SMALL LETTER A	#
					{0x1D586,0x0061},	//	( 𝖆 → a ) MATHEMATICAL BOLD FRAKTUR SMALL A → LATIN SMALL LETTER A	#
					{0x1D5BA,0x0061},	//	( 𝖺 → a ) MATHEMATICAL SANS-SERIF SMALL A → LATIN SMALL LETTER A	#
					{0x1D5EE,0x0061},	//	( 𝗮 → a ) MATHEMATICAL SANS-SERIF BOLD SMALL A → LATIN SMALL LETTER A	#
					{0x1D622,0x0061},	//	( 𝘢 → a ) MATHEMATICAL SANS-SERIF ITALIC SMALL A → LATIN SMALL LETTER A	#
					{0x1D656,0x0061},	//	( 𝙖 → a ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL A → LATIN SMALL LETTER A	#
					{0x1D68A,0x0061},	//	( 𝚊 → a ) MATHEMATICAL MONOSPACE SMALL A → LATIN SMALL LETTER A	#
					{0x0251,0x0061},	//	( ɑ → a ) LATIN SMALL LETTER ALPHA → LATIN SMALL LETTER A	#
					{0x03B1,0x0061},	//	( α → a ) GREEK SMALL LETTER ALPHA → LATIN SMALL LETTER A	#
					{0x1D6C2,0x0061},	//	( 𝛂 → a ) MATHEMATICAL BOLD SMALL ALPHA → LATIN SMALL LETTER A	# →α→
					{0x1D6FC,0x0061},	//	( 𝛼 → a ) MATHEMATICAL ITALIC SMALL ALPHA → LATIN SMALL LETTER A	# →α→
					{0x1D736,0x0061},	//	( 𝜶 → a ) MATHEMATICAL BOLD ITALIC SMALL ALPHA → LATIN SMALL LETTER A	# →α→
					{0x1D770,0x0061},	//	( 𝝰 → a ) MATHEMATICAL SANS-SERIF BOLD SMALL ALPHA → LATIN SMALL LETTER A	# →α→
					{0x1D7AA,0x0061},	//	( 𝞪 → a ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL ALPHA → LATIN SMALL LETTER A	# →α→
					{0x0430,0x0061},	//	( а → a ) CYRILLIC SMALL LETTER A → LATIN SMALL LETTER A	#
					{0x1D400,0x0041},	//	( 𝐀 → A ) MATHEMATICAL BOLD CAPITAL A → LATIN CAPITAL LETTER A	#
					{0x1D434,0x0041},	//	( 𝐴 → A ) MATHEMATICAL ITALIC CAPITAL A → LATIN CAPITAL LETTER A	#
					{0x1D468,0x0041},	//	( 𝑨 → A ) MATHEMATICAL BOLD ITALIC CAPITAL A → LATIN CAPITAL LETTER A	#
					{0x1D49C,0x0041},	//	( 𝒜 → A ) MATHEMATICAL SCRIPT CAPITAL A → LATIN CAPITAL LETTER A	#
					{0x1D4D0,0x0041},	//	( 𝓐 → A ) MATHEMATICAL BOLD SCRIPT CAPITAL A → LATIN CAPITAL LETTER A	#
					{0x1D504,0x0041},	//	( 𝔄 → A ) MATHEMATICAL FRAKTUR CAPITAL A → LATIN CAPITAL LETTER A	#
					{0x1D538,0x0041},	//	( 𝔸 → A ) MATHEMATICAL DOUBLE-STRUCK CAPITAL A → LATIN CAPITAL LETTER A	#
					{0x1D56C,0x0041},	//	( 𝕬 → A ) MATHEMATICAL BOLD FRAKTUR CAPITAL A → LATIN CAPITAL LETTER A	#
					{0x1D5A0,0x0041},	//	( 𝖠 → A ) MATHEMATICAL SANS-SERIF CAPITAL A → LATIN CAPITAL LETTER A	#
					{0x1D5D4,0x0041},	//	( 𝗔 → A ) MATHEMATICAL SANS-SERIF BOLD CAPITAL A → LATIN CAPITAL LETTER A	#
					{0x1D608,0x0041},	//	( 𝘈 → A ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL A → LATIN CAPITAL LETTER A	#
					{0x1D63C,0x0041},	//	( 𝘼 → A ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL A → LATIN CAPITAL LETTER A	#
					{0x1D670,0x0041},	//	( 𝙰 → A ) MATHEMATICAL MONOSPACE CAPITAL A → LATIN CAPITAL LETTER A	#
					{0x2C6D,0x0041},	//	( Ɑ → A ) LATIN CAPITAL LETTER ALPHA → LATIN CAPITAL LETTER A	#	// to preserve case insensitivity
					// Unfortunately, most mappings of Greek letters and some mappings of Cyrillic letters break case-insensitive fuzzy matching :-(
					// GREEK CAPITAL LETTER ALPHA → LATIN CAPITAL LETTER A is one of these.
					{0x0391,0x0041},	//	( Α → A ) GREEK CAPITAL LETTER ALPHA → LATIN CAPITAL LETTER A	#
					// Ideally, all variants of capital alpha would be fuzzy-matched by both LATIN CAPITAL LETTER A and GREEK CAPITAL LETTER ALPHA,
					// but that is not possible right now without breaking other things, alas.
					// Many other capital Greek letters have the same problem.
					{0x1D6A8,0x0041},	//	( 𝚨 → A ) MATHEMATICAL BOLD CAPITAL ALPHA → LATIN CAPITAL LETTER A	# →𝐀→
					{0x1D6E2,0x0041},	//	( 𝛢 → A ) MATHEMATICAL ITALIC CAPITAL ALPHA → LATIN CAPITAL LETTER A	# →Α→
					{0x1D71C,0x0041},	//	( 𝜜 → A ) MATHEMATICAL BOLD ITALIC CAPITAL ALPHA → LATIN CAPITAL LETTER A	# →Α→
					{0x1D756,0x0041},	//	( 𝝖 → A ) MATHEMATICAL SANS-SERIF BOLD CAPITAL ALPHA → LATIN CAPITAL LETTER A	# →Α→
					{0x1D790,0x0041},	//	( 𝞐 → A ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL ALPHA → LATIN CAPITAL LETTER A	# →Α→
					{0x0410,0x0041},	//	( А → A ) CYRILLIC CAPITAL LETTER A → LATIN CAPITAL LETTER A	#
					{0x13AA,0x0041},	//	( Ꭺ → A ) CHEROKEE LETTER GO → LATIN CAPITAL LETTER A	#
					{0x15C5,0x0041},	//	( ᗅ → A ) CANADIAN SYLLABICS CARRIER GHO → LATIN CAPITAL LETTER A	#
					{0xA4EE,0x0041},	//	( ꓮ → A ) LISU LETTER A → LATIN CAPITAL LETTER A	#
					{0x16F40,0x0041},	//	( 𖽀 → A ) MIAO LETTER ZZYA → LATIN CAPITAL LETTER A	#
					{0x102A0,0x0041},	//	( 𐊠 → A ) CARIAN LETTER A → LATIN CAPITAL LETTER A	#
					{0x0227,0x00E5},	//	( ȧ → å ) LATIN SMALL LETTER A WITH DOT ABOVE → LATIN SMALL LETTER A WITH RING ABOVE	#
					{0x0226,0x00C5},	//	( Ȧ → Å ) LATIN CAPITAL LETTER A WITH DOT ABOVE → LATIN CAPITAL LETTER A WITH RING ABOVE	#
					{0x1D41B,0x0062},	//	( 𝐛 → b ) MATHEMATICAL BOLD SMALL B → LATIN SMALL LETTER B	#
					{0x1D44F,0x0062},	//	( 𝑏 → b ) MATHEMATICAL ITALIC SMALL B → LATIN SMALL LETTER B	#
					{0x1D483,0x0062},	//	( 𝒃 → b ) MATHEMATICAL BOLD ITALIC SMALL B → LATIN SMALL LETTER B	#
					{0x1D4B7,0x0062},	//	( 𝒷 → b ) MATHEMATICAL SCRIPT SMALL B → LATIN SMALL LETTER B	#
					{0x1D4EB,0x0062},	//	( 𝓫 → b ) MATHEMATICAL BOLD SCRIPT SMALL B → LATIN SMALL LETTER B	#
					{0x1D51F,0x0062},	//	( 𝔟 → b ) MATHEMATICAL FRAKTUR SMALL B → LATIN SMALL LETTER B	#
					{0x1D553,0x0062},	//	( 𝕓 → b ) MATHEMATICAL DOUBLE-STRUCK SMALL B → LATIN SMALL LETTER B	#
					{0x1D587,0x0062},	//	( 𝖇 → b ) MATHEMATICAL BOLD FRAKTUR SMALL B → LATIN SMALL LETTER B	#
					{0x1D5BB,0x0062},	//	( 𝖻 → b ) MATHEMATICAL SANS-SERIF SMALL B → LATIN SMALL LETTER B	#
					{0x1D5EF,0x0062},	//	( 𝗯 → b ) MATHEMATICAL SANS-SERIF BOLD SMALL B → LATIN SMALL LETTER B	#
					{0x1D623,0x0062},	//	( 𝘣 → b ) MATHEMATICAL SANS-SERIF ITALIC SMALL B → LATIN SMALL LETTER B	#
					{0x1D657,0x0062},	//	( 𝙗 → b ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL B → LATIN SMALL LETTER B	#
					{0x1D68B,0x0062},	//	( 𝚋 → b ) MATHEMATICAL MONOSPACE SMALL B → LATIN SMALL LETTER B	#
					{0x0184,0x0062},	//	( Ƅ → b ) LATIN CAPITAL LETTER TONE SIX → LATIN SMALL LETTER B	#
					{0x042C,0x0062},	//	( Ь → b ) CYRILLIC CAPITAL LETTER SOFT SIGN → LATIN SMALL LETTER B	# →Ƅ→
					{0x13CF,0x0062},	//	( Ꮟ → b ) CHEROKEE LETTER SI → LATIN SMALL LETTER B	#
					{0x1472,0x0062},	//	( ᑲ → b ) CANADIAN SYLLABICS KA → LATIN SMALL LETTER B	#
					{0x15AF,0x0062},	//	( ᖯ → b ) CANADIAN SYLLABICS AIVILIK B → LATIN SMALL LETTER B	#
					{0x212C,0x0042},	//	( ℬ → B ) SCRIPT CAPITAL B → LATIN CAPITAL LETTER B	#
					{0x1D401,0x0042},	//	( 𝐁 → B ) MATHEMATICAL BOLD CAPITAL B → LATIN CAPITAL LETTER B	#
					{0x1D435,0x0042},	//	( 𝐵 → B ) MATHEMATICAL ITALIC CAPITAL B → LATIN CAPITAL LETTER B	#
					{0x1D469,0x0042},	//	( 𝑩 → B ) MATHEMATICAL BOLD ITALIC CAPITAL B → LATIN CAPITAL LETTER B	#
					{0x1D4D1,0x0042},	//	( 𝓑 → B ) MATHEMATICAL BOLD SCRIPT CAPITAL B → LATIN CAPITAL LETTER B	#
					{0x1D505,0x0042},	//	( 𝔅 → B ) MATHEMATICAL FRAKTUR CAPITAL B → LATIN CAPITAL LETTER B	#
					{0x1D539,0x0042},	//	( 𝔹 → B ) MATHEMATICAL DOUBLE-STRUCK CAPITAL B → LATIN CAPITAL LETTER B	#
					{0x1D56D,0x0042},	//	( 𝕭 → B ) MATHEMATICAL BOLD FRAKTUR CAPITAL B → LATIN CAPITAL LETTER B	#
					{0x1D5A1,0x0042},	//	( 𝖡 → B ) MATHEMATICAL SANS-SERIF CAPITAL B → LATIN CAPITAL LETTER B	#
					{0x1D5D5,0x0042},	//	( 𝗕 → B ) MATHEMATICAL SANS-SERIF BOLD CAPITAL B → LATIN CAPITAL LETTER B	#
					{0x1D609,0x0042},	//	( 𝘉 → B ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL B → LATIN CAPITAL LETTER B	#
					{0x1D63D,0x0042},	//	( 𝘽 → B ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL B → LATIN CAPITAL LETTER B	#
					{0x1D671,0x0042},	//	( 𝙱 → B ) MATHEMATICAL MONOSPACE CAPITAL B → LATIN CAPITAL LETTER B	#
					{0xA7B4,0x0042},	//	( Ꞵ → B ) LATIN CAPITAL LETTER BETA → LATIN CAPITAL LETTER B	#
					{0x0392,0x0042},	//	( Β → B ) GREEK CAPITAL LETTER BETA → LATIN CAPITAL LETTER B	#
					{0x1D6A9,0x0042},	//	( 𝚩 → B ) MATHEMATICAL BOLD CAPITAL BETA → LATIN CAPITAL LETTER B	# →Β→
					{0x1D6E3,0x0042},	//	( 𝛣 → B ) MATHEMATICAL ITALIC CAPITAL BETA → LATIN CAPITAL LETTER B	# →Β→
					{0x1D71D,0x0042},	//	( 𝜝 → B ) MATHEMATICAL BOLD ITALIC CAPITAL BETA → LATIN CAPITAL LETTER B	# →Β→
					{0x1D757,0x0042},	//	( 𝝗 → B ) MATHEMATICAL SANS-SERIF BOLD CAPITAL BETA → LATIN CAPITAL LETTER B	# →Β→
					{0x1D791,0x0042},	//	( 𝞑 → B ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL BETA → LATIN CAPITAL LETTER B	# →Β→
					{0x0412,0x0042},	//	( В → B ) CYRILLIC CAPITAL LETTER VE → LATIN CAPITAL LETTER B	#
					{0x13F4,0x0042},	//	( Ᏼ → B ) CHEROKEE LETTER YV → LATIN CAPITAL LETTER B	#
					{0x15F7,0x0042},	//	( ᗷ → B ) CANADIAN SYLLABICS CARRIER KHE → LATIN CAPITAL LETTER B	#
					{0xA4D0,0x0042},	//	( ꓐ → B ) LISU LETTER BA → LATIN CAPITAL LETTER B	#
					{0x10282,0x0042},	//	( 𐊂 → B ) LYCIAN LETTER B → LATIN CAPITAL LETTER B	#
					{0x102A1,0x0042},	//	( 𐊡 → B ) CARIAN LETTER P2 → LATIN CAPITAL LETTER B	#
					{0x10301,0x0042},	//	( 𐌁 → B ) OLD ITALIC LETTER BE → LATIN CAPITAL LETTER B	#
					{0x217D,0x0063},	//	( ⅽ → c ) SMALL ROMAN NUMERAL ONE HUNDRED → LATIN SMALL LETTER C	#
					{0x1D41C,0x0063},	//	( 𝐜 → c ) MATHEMATICAL BOLD SMALL C → LATIN SMALL LETTER C	#
					{0x1D450,0x0063},	//	( 𝑐 → c ) MATHEMATICAL ITALIC SMALL C → LATIN SMALL LETTER C	#
					{0x1D484,0x0063},	//	( 𝒄 → c ) MATHEMATICAL BOLD ITALIC SMALL C → LATIN SMALL LETTER C	#
					{0x1D4B8,0x0063},	//	( 𝒸 → c ) MATHEMATICAL SCRIPT SMALL C → LATIN SMALL LETTER C	#
					{0x1D4EC,0x0063},	//	( 𝓬 → c ) MATHEMATICAL BOLD SCRIPT SMALL C → LATIN SMALL LETTER C	#
					{0x1D520,0x0063},	//	( 𝔠 → c ) MATHEMATICAL FRAKTUR SMALL C → LATIN SMALL LETTER C	#
					{0x1D554,0x0063},	//	( 𝕔 → c ) MATHEMATICAL DOUBLE-STRUCK SMALL C → LATIN SMALL LETTER C	#
					{0x1D588,0x0063},	//	( 𝖈 → c ) MATHEMATICAL BOLD FRAKTUR SMALL C → LATIN SMALL LETTER C	#
					{0x1D5BC,0x0063},	//	( 𝖼 → c ) MATHEMATICAL SANS-SERIF SMALL C → LATIN SMALL LETTER C	#
					{0x1D5F0,0x0063},	//	( 𝗰 → c ) MATHEMATICAL SANS-SERIF BOLD SMALL C → LATIN SMALL LETTER C	#
					{0x1D624,0x0063},	//	( 𝘤 → c ) MATHEMATICAL SANS-SERIF ITALIC SMALL C → LATIN SMALL LETTER C	#
					{0x1D658,0x0063},	//	( 𝙘 → c ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL C → LATIN SMALL LETTER C	#
					{0x1D68C,0x0063},	//	( 𝚌 → c ) MATHEMATICAL MONOSPACE SMALL C → LATIN SMALL LETTER C	#
					{0x1D04,0x0063},	//	( ᴄ → c ) LATIN LETTER SMALL CAPITAL C → LATIN SMALL LETTER C	#
					{0x03F2,0x0063},	//	( ϲ → c ) GREEK LUNATE SIGMA SYMBOL → LATIN SMALL LETTER C	#
					{0x2CA5,0x0063},	//	( ⲥ → c ) COPTIC SMALL LETTER SIMA → LATIN SMALL LETTER C	# →ϲ→
					{0x0441,0x0063},	//	( с → c ) CYRILLIC SMALL LETTER ES → LATIN SMALL LETTER C	#
					{0xABAF,0x0063},	//	( ꮯ → c ) CHEROKEE SMALL LETTER TLI → LATIN SMALL LETTER C	# →ᴄ→
					{0x1043D,0x0063},	//	( 𐐽 → c ) DESERET SMALL LETTER CHEE → LATIN SMALL LETTER C	#
					{0x1F74C,0x0043},	//	( 🝌 → C ) ALCHEMICAL SYMBOL FOR CALX → LATIN CAPITAL LETTER C	#
					{0x118F2,0x0043},	//	( 𑣲 → C ) WARANG CITI NUMBER NINETY → LATIN CAPITAL LETTER C	#
					{0x118E9,0x0043},	//	( 𑣩 → C ) WARANG CITI DIGIT NINE → LATIN CAPITAL LETTER C	#
					{0x216D,0x0043},	//	( Ⅽ → C ) ROMAN NUMERAL ONE HUNDRED → LATIN CAPITAL LETTER C	#
					{0x2102,0x0043},	//	( ℂ → C ) DOUBLE-STRUCK CAPITAL C → LATIN CAPITAL LETTER C	#
					{0x212D,0x0043},	//	( ℭ → C ) BLACK-LETTER CAPITAL C → LATIN CAPITAL LETTER C	#
					{0x1D402,0x0043},	//	( 𝐂 → C ) MATHEMATICAL BOLD CAPITAL C → LATIN CAPITAL LETTER C	#
					{0x1D436,0x0043},	//	( 𝐶 → C ) MATHEMATICAL ITALIC CAPITAL C → LATIN CAPITAL LETTER C	#
					{0x1D46A,0x0043},	//	( 𝑪 → C ) MATHEMATICAL BOLD ITALIC CAPITAL C → LATIN CAPITAL LETTER C	#
					{0x1D49E,0x0043},	//	( 𝒞 → C ) MATHEMATICAL SCRIPT CAPITAL C → LATIN CAPITAL LETTER C	#
					{0x1D4D2,0x0043},	//	( 𝓒 → C ) MATHEMATICAL BOLD SCRIPT CAPITAL C → LATIN CAPITAL LETTER C	#
					{0x1D56E,0x0043},	//	( 𝕮 → C ) MATHEMATICAL BOLD FRAKTUR CAPITAL C → LATIN CAPITAL LETTER C	#
					{0x1D5A2,0x0043},	//	( 𝖢 → C ) MATHEMATICAL SANS-SERIF CAPITAL C → LATIN CAPITAL LETTER C	#
					{0x1D5D6,0x0043},	//	( 𝗖 → C ) MATHEMATICAL SANS-SERIF BOLD CAPITAL C → LATIN CAPITAL LETTER C	#
					{0x1D60A,0x0043},	//	( 𝘊 → C ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL C → LATIN CAPITAL LETTER C	#
					{0x1D63E,0x0043},	//	( 𝘾 → C ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL C → LATIN CAPITAL LETTER C	#
					{0x1D672,0x0043},	//	( 𝙲 → C ) MATHEMATICAL MONOSPACE CAPITAL C → LATIN CAPITAL LETTER C	#
					{0x03F9,0x0043},	//	( Ϲ → C ) GREEK CAPITAL LUNATE SIGMA SYMBOL → LATIN CAPITAL LETTER C	#
					{0x2CA4,0x0043},	//	( Ⲥ → C ) COPTIC CAPITAL LETTER SIMA → LATIN CAPITAL LETTER C	# →Ϲ→
					{0x0421,0x0043},	//	( С → C ) CYRILLIC CAPITAL LETTER ES → LATIN CAPITAL LETTER C	#
					{0x13DF,0x0043},	//	( Ꮯ → C ) CHEROKEE LETTER TLI → LATIN CAPITAL LETTER C	#
					{0xA4DA,0x0043},	//	( ꓚ → C ) LISU LETTER CA → LATIN CAPITAL LETTER C	#
					{0x102A2,0x0043},	//	( 𐊢 → C ) CARIAN LETTER D → LATIN CAPITAL LETTER C	#
					{0x10302,0x0043},	//	( 𐌂 → C ) OLD ITALIC LETTER KE → LATIN CAPITAL LETTER C	#
					{0x10415,0x0043},	//	( 𐐕 → C ) DESERET CAPITAL LETTER CHEE → LATIN CAPITAL LETTER C	#
					{0x1051C,0x0043},	//	( 𐔜 → C ) ELBASAN LETTER SHE → LATIN CAPITAL LETTER C	#
					{0x217E,0x0064},	//	( ⅾ → d ) SMALL ROMAN NUMERAL FIVE HUNDRED → LATIN SMALL LETTER D	#
					{0x2146,0x0064},	//	( ⅆ → d ) DOUBLE-STRUCK ITALIC SMALL D → LATIN SMALL LETTER D	#
					{0x1D41D,0x0064},	//	( 𝐝 → d ) MATHEMATICAL BOLD SMALL D → LATIN SMALL LETTER D	#
					{0x1D451,0x0064},	//	( 𝑑 → d ) MATHEMATICAL ITALIC SMALL D → LATIN SMALL LETTER D	#
					{0x1D485,0x0064},	//	( 𝒅 → d ) MATHEMATICAL BOLD ITALIC SMALL D → LATIN SMALL LETTER D	#
					{0x1D4B9,0x0064},	//	( 𝒹 → d ) MATHEMATICAL SCRIPT SMALL D → LATIN SMALL LETTER D	#
					{0x1D4ED,0x0064},	//	( 𝓭 → d ) MATHEMATICAL BOLD SCRIPT SMALL D → LATIN SMALL LETTER D	#
					{0x1D521,0x0064},	//	( 𝔡 → d ) MATHEMATICAL FRAKTUR SMALL D → LATIN SMALL LETTER D	#
					{0x1D555,0x0064},	//	( 𝕕 → d ) MATHEMATICAL DOUBLE-STRUCK SMALL D → LATIN SMALL LETTER D	#
					{0x1D589,0x0064},	//	( 𝖉 → d ) MATHEMATICAL BOLD FRAKTUR SMALL D → LATIN SMALL LETTER D	#
					{0x1D5BD,0x0064},	//	( 𝖽 → d ) MATHEMATICAL SANS-SERIF SMALL D → LATIN SMALL LETTER D	#
					{0x1D5F1,0x0064},	//	( 𝗱 → d ) MATHEMATICAL SANS-SERIF BOLD SMALL D → LATIN SMALL LETTER D	#
					{0x1D625,0x0064},	//	( 𝘥 → d ) MATHEMATICAL SANS-SERIF ITALIC SMALL D → LATIN SMALL LETTER D	#
					{0x1D659,0x0064},	//	( 𝙙 → d ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL D → LATIN SMALL LETTER D	#
					{0x1D68D,0x0064},	//	( 𝚍 → d ) MATHEMATICAL MONOSPACE SMALL D → LATIN SMALL LETTER D	#
					{0x0501,0x0064},	//	( ԁ → d ) CYRILLIC SMALL LETTER KOMI DE → LATIN SMALL LETTER D	#
					{0x13E7,0x0064},	//	( Ꮷ → d ) CHEROKEE LETTER TSU → LATIN SMALL LETTER D	#
					{0x146F,0x0064},	//	( ᑯ → d ) CANADIAN SYLLABICS KO → LATIN SMALL LETTER D	#
					{0xA4D2,0x0064},	//	( ꓒ → d ) LISU LETTER PHA → LATIN SMALL LETTER D	#
					{0x216E,0x0044},	//	( Ⅾ → D ) ROMAN NUMERAL FIVE HUNDRED → LATIN CAPITAL LETTER D	#
					{0x2145,0x0044},	//	( ⅅ → D ) DOUBLE-STRUCK ITALIC CAPITAL D → LATIN CAPITAL LETTER D	#
					{0x1D403,0x0044},	//	( 𝐃 → D ) MATHEMATICAL BOLD CAPITAL D → LATIN CAPITAL LETTER D	#
					{0x1D437,0x0044},	//	( 𝐷 → D ) MATHEMATICAL ITALIC CAPITAL D → LATIN CAPITAL LETTER D	#
					{0x1D46B,0x0044},	//	( 𝑫 → D ) MATHEMATICAL BOLD ITALIC CAPITAL D → LATIN CAPITAL LETTER D	#
					{0x1D49F,0x0044},	//	( 𝒟 → D ) MATHEMATICAL SCRIPT CAPITAL D → LATIN CAPITAL LETTER D	#
					{0x1D4D3,0x0044},	//	( 𝓓 → D ) MATHEMATICAL BOLD SCRIPT CAPITAL D → LATIN CAPITAL LETTER D	#
					{0x1D507,0x0044},	//	( 𝔇 → D ) MATHEMATICAL FRAKTUR CAPITAL D → LATIN CAPITAL LETTER D	#
					{0x1D53B,0x0044},	//	( 𝔻 → D ) MATHEMATICAL DOUBLE-STRUCK CAPITAL D → LATIN CAPITAL LETTER D	#
					{0x1D56F,0x0044},	//	( 𝕯 → D ) MATHEMATICAL BOLD FRAKTUR CAPITAL D → LATIN CAPITAL LETTER D	#
					{0x1D5A3,0x0044},	//	( 𝖣 → D ) MATHEMATICAL SANS-SERIF CAPITAL D → LATIN CAPITAL LETTER D	#
					{0x1D5D7,0x0044},	//	( 𝗗 → D ) MATHEMATICAL SANS-SERIF BOLD CAPITAL D → LATIN CAPITAL LETTER D	#
					{0x1D60B,0x0044},	//	( 𝘋 → D ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL D → LATIN CAPITAL LETTER D	#
					{0x1D63F,0x0044},	//	( 𝘿 → D ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL D → LATIN CAPITAL LETTER D	#
					{0x1D673,0x0044},	//	( 𝙳 → D ) MATHEMATICAL MONOSPACE CAPITAL D → LATIN CAPITAL LETTER D	#
					{0x13A0,0x0044},	//	( Ꭰ → D ) CHEROKEE LETTER A → LATIN CAPITAL LETTER D	#
					{0x15DE,0x0044},	//	( ᗞ → D ) CANADIAN SYLLABICS CARRIER THE → LATIN CAPITAL LETTER D	#
					{0x15EA,0x0044},	//	( ᗪ → D ) CANADIAN SYLLABICS CARRIER PE → LATIN CAPITAL LETTER D	# →ᗞ→
					{0xA4D3,0x0044},	//	( ꓓ → D ) LISU LETTER DA → LATIN CAPITAL LETTER D	#
					{0x212E,0x0065},	//	( ℮ → e ) ESTIMATED SYMBOL → LATIN SMALL LETTER E	#
					{0x212F,0x0065},	//	( ℯ → e ) SCRIPT SMALL E → LATIN SMALL LETTER E	#
					{0x2147,0x0065},	//	( ⅇ → e ) DOUBLE-STRUCK ITALIC SMALL E → LATIN SMALL LETTER E	#
					{0x1D41E,0x0065},	//	( 𝐞 → e ) MATHEMATICAL BOLD SMALL E → LATIN SMALL LETTER E	#
					{0x1D452,0x0065},	//	( 𝑒 → e ) MATHEMATICAL ITALIC SMALL E → LATIN SMALL LETTER E	#
					{0x1D486,0x0065},	//	( 𝒆 → e ) MATHEMATICAL BOLD ITALIC SMALL E → LATIN SMALL LETTER E	#
					{0x1D4EE,0x0065},	//	( 𝓮 → e ) MATHEMATICAL BOLD SCRIPT SMALL E → LATIN SMALL LETTER E	#
					{0x1D522,0x0065},	//	( 𝔢 → e ) MATHEMATICAL FRAKTUR SMALL E → LATIN SMALL LETTER E	#
					{0x1D556,0x0065},	//	( 𝕖 → e ) MATHEMATICAL DOUBLE-STRUCK SMALL E → LATIN SMALL LETTER E	#
					{0x1D58A,0x0065},	//	( 𝖊 → e ) MATHEMATICAL BOLD FRAKTUR SMALL E → LATIN SMALL LETTER E	#
					{0x1D5BE,0x0065},	//	( 𝖾 → e ) MATHEMATICAL SANS-SERIF SMALL E → LATIN SMALL LETTER E	#
					{0x1D5F2,0x0065},	//	( 𝗲 → e ) MATHEMATICAL SANS-SERIF BOLD SMALL E → LATIN SMALL LETTER E	#
					{0x1D626,0x0065},	//	( 𝘦 → e ) MATHEMATICAL SANS-SERIF ITALIC SMALL E → LATIN SMALL LETTER E	#
					{0x1D65A,0x0065},	//	( 𝙚 → e ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL E → LATIN SMALL LETTER E	#
					{0x1D68E,0x0065},	//	( 𝚎 → e ) MATHEMATICAL MONOSPACE SMALL E → LATIN SMALL LETTER E	#
					{0xAB32,0x0065},	//	( ꬲ → e ) LATIN SMALL LETTER BLACKLETTER E → LATIN SMALL LETTER E	#
					{0x0435,0x0065},	//	( е → e ) CYRILLIC SMALL LETTER IE → LATIN SMALL LETTER E	#
					{0x04BD,0x0065},	//	( ҽ → e ) CYRILLIC SMALL LETTER ABKHASIAN CHE → LATIN SMALL LETTER E	#
					{0x22FF,0x0045},	//	( ⋿ → E ) Z NOTATION BAG MEMBERSHIP → LATIN CAPITAL LETTER E	#
					{0x2130,0x0045},	//	( ℰ → E ) SCRIPT CAPITAL E → LATIN CAPITAL LETTER E	#
					{0x1D404,0x0045},	//	( 𝐄 → E ) MATHEMATICAL BOLD CAPITAL E → LATIN CAPITAL LETTER E	#
					{0x1D438,0x0045},	//	( 𝐸 → E ) MATHEMATICAL ITALIC CAPITAL E → LATIN CAPITAL LETTER E	#
					{0x1D46C,0x0045},	//	( 𝑬 → E ) MATHEMATICAL BOLD ITALIC CAPITAL E → LATIN CAPITAL LETTER E	#
					{0x1D4D4,0x0045},	//	( 𝓔 → E ) MATHEMATICAL BOLD SCRIPT CAPITAL E → LATIN CAPITAL LETTER E	#
					{0x1D508,0x0045},	//	( 𝔈 → E ) MATHEMATICAL FRAKTUR CAPITAL E → LATIN CAPITAL LETTER E	#
					{0x1D53C,0x0045},	//	( 𝔼 → E ) MATHEMATICAL DOUBLE-STRUCK CAPITAL E → LATIN CAPITAL LETTER E	#
					{0x1D570,0x0045},	//	( 𝕰 → E ) MATHEMATICAL BOLD FRAKTUR CAPITAL E → LATIN CAPITAL LETTER E	#
					{0x1D5A4,0x0045},	//	( 𝖤 → E ) MATHEMATICAL SANS-SERIF CAPITAL E → LATIN CAPITAL LETTER E	#
					{0x1D5D8,0x0045},	//	( 𝗘 → E ) MATHEMATICAL SANS-SERIF BOLD CAPITAL E → LATIN CAPITAL LETTER E	#
					{0x1D60C,0x0045},	//	( 𝘌 → E ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL E → LATIN CAPITAL LETTER E	#
					{0x1D640,0x0045},	//	( 𝙀 → E ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL E → LATIN CAPITAL LETTER E	#
					{0x1D674,0x0045},	//	( 𝙴 → E ) MATHEMATICAL MONOSPACE CAPITAL E → LATIN CAPITAL LETTER E	#
					{0x0395,0x0045},	//	( Ε → E ) GREEK CAPITAL LETTER EPSILON → LATIN CAPITAL LETTER E	#
					{0x1D6AC,0x0045},	//	( 𝚬 → E ) MATHEMATICAL BOLD CAPITAL EPSILON → LATIN CAPITAL LETTER E	# →𝐄→
					{0x1D6E6,0x0045},	//	( 𝛦 → E ) MATHEMATICAL ITALIC CAPITAL EPSILON → LATIN CAPITAL LETTER E	# →Ε→
					{0x1D720,0x0045},	//	( 𝜠 → E ) MATHEMATICAL BOLD ITALIC CAPITAL EPSILON → LATIN CAPITAL LETTER E	# →Ε→
					{0x1D75A,0x0045},	//	( 𝝚 → E ) MATHEMATICAL SANS-SERIF BOLD CAPITAL EPSILON → LATIN CAPITAL LETTER E	# →Ε→
					{0x1D794,0x0045},	//	( 𝞔 → E ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL EPSILON → LATIN CAPITAL LETTER E	# →Ε→
					{0x0415,0x0045},	//	( Е → E ) CYRILLIC CAPITAL LETTER IE → LATIN CAPITAL LETTER E	#
					{0x2D39,0x0045},	//	( ⴹ → E ) TIFINAGH LETTER YADD → LATIN CAPITAL LETTER E	#
					{0x13AC,0x0045},	//	( Ꭼ → E ) CHEROKEE LETTER GV → LATIN CAPITAL LETTER E	#
					{0xA4F0,0x0045},	//	( ꓰ → E ) LISU LETTER E → LATIN CAPITAL LETTER E	#
					{0x118A6,0x0045},	//	( 𑢦 → E ) WARANG CITI CAPITAL LETTER II → LATIN CAPITAL LETTER E	#
					{0x118AE,0x0045},	//	( 𑢮 → E ) WARANG CITI CAPITAL LETTER YUJ → LATIN CAPITAL LETTER E	#
					{0x10286,0x0045},	//	( 𐊆 → E ) LYCIAN LETTER I → LATIN CAPITAL LETTER E	#
					{0x1D41F,0x0066},	//	( 𝐟 → f ) MATHEMATICAL BOLD SMALL F → LATIN SMALL LETTER F	#
					{0x1D453,0x0066},	//	( 𝑓 → f ) MATHEMATICAL ITALIC SMALL F → LATIN SMALL LETTER F	#
					{0x1D487,0x0066},	//	( 𝒇 → f ) MATHEMATICAL BOLD ITALIC SMALL F → LATIN SMALL LETTER F	#
					{0x1D4BB,0x0066},	//	( 𝒻 → f ) MATHEMATICAL SCRIPT SMALL F → LATIN SMALL LETTER F	#
					{0x1D4EF,0x0066},	//	( 𝓯 → f ) MATHEMATICAL BOLD SCRIPT SMALL F → LATIN SMALL LETTER F	#
					{0x1D523,0x0066},	//	( 𝔣 → f ) MATHEMATICAL FRAKTUR SMALL F → LATIN SMALL LETTER F	#
					{0x1D557,0x0066},	//	( 𝕗 → f ) MATHEMATICAL DOUBLE-STRUCK SMALL F → LATIN SMALL LETTER F	#
					{0x1D58B,0x0066},	//	( 𝖋 → f ) MATHEMATICAL BOLD FRAKTUR SMALL F → LATIN SMALL LETTER F	#
					{0x1D5BF,0x0066},	//	( 𝖿 → f ) MATHEMATICAL SANS-SERIF SMALL F → LATIN SMALL LETTER F	#
					{0x1D5F3,0x0066},	//	( 𝗳 → f ) MATHEMATICAL SANS-SERIF BOLD SMALL F → LATIN SMALL LETTER F	#
					{0x1D627,0x0066},	//	( 𝘧 → f ) MATHEMATICAL SANS-SERIF ITALIC SMALL F → LATIN SMALL LETTER F	#
					{0x1D65B,0x0066},	//	( 𝙛 → f ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL F → LATIN SMALL LETTER F	#
					{0x1D68F,0x0066},	//	( 𝚏 → f ) MATHEMATICAL MONOSPACE SMALL F → LATIN SMALL LETTER F	#
					{0x03DD,0x0066},	//	( ϝ → f ) GREEK SMALL LETTER DIGAMMA → LATIN SMALL LETTER F	#	// to preserve case insensitivity, by symmetry with GREEK LETTER DIGAMMA → LATIN CAPITAL LETTER F
					{0xAB35,0x0066},	//	( ꬵ → f ) LATIN SMALL LETTER LENIS F → LATIN SMALL LETTER F	#
					{0xA799,0x0066},	//	( ꞙ → f ) LATIN SMALL LETTER F WITH STROKE → LATIN SMALL LETTER F	#
					// Long s looks kind of like f; it would be best if it could fuzzy-match both "f" and "s", but that’s not possible with current architecture.
					{0x017F,0x0066},	//	( ſ → f ) LATIN SMALL LETTER LONG S → LATIN SMALL LETTER F	#
					{0x1E9D,0x0066},	//	( ẝ → f ) LATIN SMALL LETTER LONG S WITH HIGH STROKE → LATIN SMALL LETTER F	#
					{0x0584,0x0066},	//	( ք → f ) ARMENIAN SMALL LETTER KEH → LATIN SMALL LETTER F	#
					{0x1D213,0x0046},	//	( 𝈓 → F ) GREEK VOCAL NOTATION SYMBOL-20 → LATIN CAPITAL LETTER F	# →Ϝ→
					{0x2131,0x0046},	//	( ℱ → F ) SCRIPT CAPITAL F → LATIN CAPITAL LETTER F	#
					{0x1D405,0x0046},	//	( 𝐅 → F ) MATHEMATICAL BOLD CAPITAL F → LATIN CAPITAL LETTER F	#
					{0x1D439,0x0046},	//	( 𝐹 → F ) MATHEMATICAL ITALIC CAPITAL F → LATIN CAPITAL LETTER F	#
					{0x1D46D,0x0046},	//	( 𝑭 → F ) MATHEMATICAL BOLD ITALIC CAPITAL F → LATIN CAPITAL LETTER F	#
					{0x1D4D5,0x0046},	//	( 𝓕 → F ) MATHEMATICAL BOLD SCRIPT CAPITAL F → LATIN CAPITAL LETTER F	#
					{0x1D509,0x0046},	//	( 𝔉 → F ) MATHEMATICAL FRAKTUR CAPITAL F → LATIN CAPITAL LETTER F	#
					{0x1D53D,0x0046},	//	( 𝔽 → F ) MATHEMATICAL DOUBLE-STRUCK CAPITAL F → LATIN CAPITAL LETTER F	#
					{0x1D571,0x0046},	//	( 𝕱 → F ) MATHEMATICAL BOLD FRAKTUR CAPITAL F → LATIN CAPITAL LETTER F	#
					{0x1D5A5,0x0046},	//	( 𝖥 → F ) MATHEMATICAL SANS-SERIF CAPITAL F → LATIN CAPITAL LETTER F	#
					{0x1D5D9,0x0046},	//	( 𝗙 → F ) MATHEMATICAL SANS-SERIF BOLD CAPITAL F → LATIN CAPITAL LETTER F	#
					{0x1D60D,0x0046},	//	( 𝘍 → F ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL F → LATIN CAPITAL LETTER F	#
					{0x1D641,0x0046},	//	( 𝙁 → F ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL F → LATIN CAPITAL LETTER F	#
					{0x1D675,0x0046},	//	( 𝙵 → F ) MATHEMATICAL MONOSPACE CAPITAL F → LATIN CAPITAL LETTER F	#
					{0xA798,0x0046},	//	( Ꞙ → F ) LATIN CAPITAL LETTER F WITH STROKE → LATIN CAPITAL LETTER F	#
					{0x03DC,0x0046},	//	( Ϝ → F ) GREEK LETTER DIGAMMA → LATIN CAPITAL LETTER F	#
					{0x1D7CA,0x0046},	//	( 𝟊 → F ) MATHEMATICAL BOLD CAPITAL DIGAMMA → LATIN CAPITAL LETTER F	# →Ϝ→
					{0x15B4,0x0046},	//	( ᖴ → F ) CANADIAN SYLLABICS BLACKFOOT WE → LATIN CAPITAL LETTER F	#
					{0xA4DD,0x0046},	//	( ꓝ → F ) LISU LETTER TSA → LATIN CAPITAL LETTER F	#
					{0x118C2,0x0046},	//	( 𑣂 → F ) WARANG CITI SMALL LETTER WI → LATIN CAPITAL LETTER F	#
					{0x118A2,0x0046},	//	( 𑢢 → F ) WARANG CITI CAPITAL LETTER WI → LATIN CAPITAL LETTER F	#
					{0x10287,0x0046},	//	( 𐊇 → F ) LYCIAN LETTER W → LATIN CAPITAL LETTER F	#
					{0x102A5,0x0046},	//	( 𐊥 → F ) CARIAN LETTER R → LATIN CAPITAL LETTER F	#
					{0x10525,0x0046},	//	( 𐔥 → F ) ELBASAN LETTER GHE → LATIN CAPITAL LETTER F	#
					{0x210A,0x0067},	//	( ℊ → g ) SCRIPT SMALL G → LATIN SMALL LETTER G	#
					{0x1D420,0x0067},	//	( 𝐠 → g ) MATHEMATICAL BOLD SMALL G → LATIN SMALL LETTER G	#
					{0x1D454,0x0067},	//	( 𝑔 → g ) MATHEMATICAL ITALIC SMALL G → LATIN SMALL LETTER G	#
					{0x1D488,0x0067},	//	( 𝒈 → g ) MATHEMATICAL BOLD ITALIC SMALL G → LATIN SMALL LETTER G	#
					{0x1D4F0,0x0067},	//	( 𝓰 → g ) MATHEMATICAL BOLD SCRIPT SMALL G → LATIN SMALL LETTER G	#
					{0x1D524,0x0067},	//	( 𝔤 → g ) MATHEMATICAL FRAKTUR SMALL G → LATIN SMALL LETTER G	#
					{0x1D558,0x0067},	//	( 𝕘 → g ) MATHEMATICAL DOUBLE-STRUCK SMALL G → LATIN SMALL LETTER G	#
					{0x1D58C,0x0067},	//	( 𝖌 → g ) MATHEMATICAL BOLD FRAKTUR SMALL G → LATIN SMALL LETTER G	#
					{0x1D5C0,0x0067},	//	( 𝗀 → g ) MATHEMATICAL SANS-SERIF SMALL G → LATIN SMALL LETTER G	#
					{0x1D5F4,0x0067},	//	( 𝗴 → g ) MATHEMATICAL SANS-SERIF BOLD SMALL G → LATIN SMALL LETTER G	#
					{0x1D628,0x0067},	//	( 𝘨 → g ) MATHEMATICAL SANS-SERIF ITALIC SMALL G → LATIN SMALL LETTER G	#
					{0x1D65C,0x0067},	//	( 𝙜 → g ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL G → LATIN SMALL LETTER G	#
					{0x1D690,0x0067},	//	( 𝚐 → g ) MATHEMATICAL MONOSPACE SMALL G → LATIN SMALL LETTER G	#
					{0x0261,0x0067},	//	( ɡ → g ) LATIN SMALL LETTER SCRIPT G → LATIN SMALL LETTER G	#
					{0x1D83,0x0067},	//	( ᶃ → g ) LATIN SMALL LETTER G WITH PALATAL HOOK → LATIN SMALL LETTER G	#
					{0x018D,0x0067},	//	( ƍ → g ) LATIN SMALL LETTER TURNED DELTA → LATIN SMALL LETTER G	#
					{0x0581,0x0067},	//	( ց → g ) ARMENIAN SMALL LETTER CO → LATIN SMALL LETTER G	#
					{0x1D406,0x0047},	//	( 𝐆 → G ) MATHEMATICAL BOLD CAPITAL G → LATIN CAPITAL LETTER G	#
					{0x1D43A,0x0047},	//	( 𝐺 → G ) MATHEMATICAL ITALIC CAPITAL G → LATIN CAPITAL LETTER G	#
					{0x1D46E,0x0047},	//	( 𝑮 → G ) MATHEMATICAL BOLD ITALIC CAPITAL G → LATIN CAPITAL LETTER G	#
					{0x1D4A2,0x0047},	//	( 𝒢 → G ) MATHEMATICAL SCRIPT CAPITAL G → LATIN CAPITAL LETTER G	#
					{0x1D4D6,0x0047},	//	( 𝓖 → G ) MATHEMATICAL BOLD SCRIPT CAPITAL G → LATIN CAPITAL LETTER G	#
					{0x1D50A,0x0047},	//	( 𝔊 → G ) MATHEMATICAL FRAKTUR CAPITAL G → LATIN CAPITAL LETTER G	#
					{0x1D53E,0x0047},	//	( 𝔾 → G ) MATHEMATICAL DOUBLE-STRUCK CAPITAL G → LATIN CAPITAL LETTER G	#
					{0x1D572,0x0047},	//	( 𝕲 → G ) MATHEMATICAL BOLD FRAKTUR CAPITAL G → LATIN CAPITAL LETTER G	#
					{0x1D5A6,0x0047},	//	( 𝖦 → G ) MATHEMATICAL SANS-SERIF CAPITAL G → LATIN CAPITAL LETTER G	#
					{0x1D5DA,0x0047},	//	( 𝗚 → G ) MATHEMATICAL SANS-SERIF BOLD CAPITAL G → LATIN CAPITAL LETTER G	#
					{0x1D60E,0x0047},	//	( 𝘎 → G ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL G → LATIN CAPITAL LETTER G	#
					{0x1D642,0x0047},	//	( 𝙂 → G ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL G → LATIN CAPITAL LETTER G	#
					{0x1D676,0x0047},	//	( 𝙶 → G ) MATHEMATICAL MONOSPACE CAPITAL G → LATIN CAPITAL LETTER G	#
					{0x050C,0x0047},	//	( Ԍ → G ) CYRILLIC CAPITAL LETTER KOMI SJE → LATIN CAPITAL LETTER G	#
					{0x13C0,0x0047},	//	( Ꮐ → G ) CHEROKEE LETTER NAH → LATIN CAPITAL LETTER G	#
					{0x13F3,0x0047},	//	( Ᏻ → G ) CHEROKEE LETTER YU → LATIN CAPITAL LETTER G	#
					{0xA4D6,0x0047},	//	( ꓖ → G ) LISU LETTER GA → LATIN CAPITAL LETTER G	#
					{0x210E,0x0068},	//	( ℎ → h ) PLANCK CONSTANT → LATIN SMALL LETTER H	#
					{0x1D421,0x0068},	//	( 𝐡 → h ) MATHEMATICAL BOLD SMALL H → LATIN SMALL LETTER H	#
					{0x1D489,0x0068},	//	( 𝒉 → h ) MATHEMATICAL BOLD ITALIC SMALL H → LATIN SMALL LETTER H	#
					{0x1D4BD,0x0068},	//	( 𝒽 → h ) MATHEMATICAL SCRIPT SMALL H → LATIN SMALL LETTER H	#
					{0x1D4F1,0x0068},	//	( 𝓱 → h ) MATHEMATICAL BOLD SCRIPT SMALL H → LATIN SMALL LETTER H	#
					{0x1D525,0x0068},	//	( 𝔥 → h ) MATHEMATICAL FRAKTUR SMALL H → LATIN SMALL LETTER H	#
					{0x1D559,0x0068},	//	( 𝕙 → h ) MATHEMATICAL DOUBLE-STRUCK SMALL H → LATIN SMALL LETTER H	#
					{0x1D58D,0x0068},	//	( 𝖍 → h ) MATHEMATICAL BOLD FRAKTUR SMALL H → LATIN SMALL LETTER H	#
					{0x1D5C1,0x0068},	//	( 𝗁 → h ) MATHEMATICAL SANS-SERIF SMALL H → LATIN SMALL LETTER H	#
					{0x1D5F5,0x0068},	//	( 𝗵 → h ) MATHEMATICAL SANS-SERIF BOLD SMALL H → LATIN SMALL LETTER H	#
					{0x1D629,0x0068},	//	( 𝘩 → h ) MATHEMATICAL SANS-SERIF ITALIC SMALL H → LATIN SMALL LETTER H	#
					{0x1D65D,0x0068},	//	( 𝙝 → h ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL H → LATIN SMALL LETTER H	#
					{0x1D691,0x0068},	//	( 𝚑 → h ) MATHEMATICAL MONOSPACE SMALL H → LATIN SMALL LETTER H	#
					{0x04BB,0x0068},	//	( һ → h ) CYRILLIC SMALL LETTER SHHA → LATIN SMALL LETTER H	#
					{0x0570,0x0068},	//	( հ → h ) ARMENIAN SMALL LETTER HO → LATIN SMALL LETTER H	#
					{0x13C2,0x0068},	//	( Ꮒ → h ) CHEROKEE LETTER NI → LATIN SMALL LETTER H	#
					{0x210B,0x0048},	//	( ℋ → H ) SCRIPT CAPITAL H → LATIN CAPITAL LETTER H	#
					{0x210C,0x0048},	//	( ℌ → H ) BLACK-LETTER CAPITAL H → LATIN CAPITAL LETTER H	#
					{0x210D,0x0048},	//	( ℍ → H ) DOUBLE-STRUCK CAPITAL H → LATIN CAPITAL LETTER H	#
					{0x1D407,0x0048},	//	( 𝐇 → H ) MATHEMATICAL BOLD CAPITAL H → LATIN CAPITAL LETTER H	#
					{0x1D43B,0x0048},	//	( 𝐻 → H ) MATHEMATICAL ITALIC CAPITAL H → LATIN CAPITAL LETTER H	#
					{0x1D46F,0x0048},	//	( 𝑯 → H ) MATHEMATICAL BOLD ITALIC CAPITAL H → LATIN CAPITAL LETTER H	#
					{0x1D4D7,0x0048},	//	( 𝓗 → H ) MATHEMATICAL BOLD SCRIPT CAPITAL H → LATIN CAPITAL LETTER H	#
					{0x1D573,0x0048},	//	( 𝕳 → H ) MATHEMATICAL BOLD FRAKTUR CAPITAL H → LATIN CAPITAL LETTER H	#
					{0x1D5A7,0x0048},	//	( 𝖧 → H ) MATHEMATICAL SANS-SERIF CAPITAL H → LATIN CAPITAL LETTER H	#
					{0x1D5DB,0x0048},	//	( 𝗛 → H ) MATHEMATICAL SANS-SERIF BOLD CAPITAL H → LATIN CAPITAL LETTER H	#
					{0x1D60F,0x0048},	//	( 𝘏 → H ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL H → LATIN CAPITAL LETTER H	#
					{0x1D643,0x0048},	//	( 𝙃 → H ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL H → LATIN CAPITAL LETTER H	#
					{0x1D677,0x0048},	//	( 𝙷 → H ) MATHEMATICAL MONOSPACE CAPITAL H → LATIN CAPITAL LETTER H	#
					{0x0397,0x0048},	//	( Η → H ) GREEK CAPITAL LETTER ETA → LATIN CAPITAL LETTER H	#
					{0x1D6AE,0x0048},	//	( 𝚮 → H ) MATHEMATICAL BOLD CAPITAL ETA → LATIN CAPITAL LETTER H	# →Η→
					{0x1D6E8,0x0048},	//	( 𝛨 → H ) MATHEMATICAL ITALIC CAPITAL ETA → LATIN CAPITAL LETTER H	# →Η→
					{0x1D722,0x0048},	//	( 𝜢 → H ) MATHEMATICAL BOLD ITALIC CAPITAL ETA → LATIN CAPITAL LETTER H	# →𝑯→
					{0x1D75C,0x0048},	//	( 𝝜 → H ) MATHEMATICAL SANS-SERIF BOLD CAPITAL ETA → LATIN CAPITAL LETTER H	# →Η→
					{0x1D796,0x0048},	//	( 𝞖 → H ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL ETA → LATIN CAPITAL LETTER H	# →Η→
					{0x2C8E,0x0048},	//	( Ⲏ → H ) COPTIC CAPITAL LETTER HATE → LATIN CAPITAL LETTER H	# →Η→
					{0x041D,0x0048},	//	( Н → H ) CYRILLIC CAPITAL LETTER EN → LATIN CAPITAL LETTER H	#
					{0x13BB,0x0048},	//	( Ꮋ → H ) CHEROKEE LETTER MI → LATIN CAPITAL LETTER H	#
					{0x157C,0x0048},	//	( ᕼ → H ) CANADIAN SYLLABICS NUNAVUT H → LATIN CAPITAL LETTER H	#
					{0xA4E7,0x0048},	//	( ꓧ → H ) LISU LETTER XA → LATIN CAPITAL LETTER H	#
					{0x102CF,0x0048},	//	( 𐋏 → H ) CARIAN LETTER E2 → LATIN CAPITAL LETTER H	#
					{0x02DB,0x0069},	//	( ˛ → i ) OGONEK → LATIN SMALL LETTER I	# →ͺ→→ι→→ι→
					{0x2373,0x0069},	//	( ⍳ → i ) APL FUNCTIONAL SYMBOL IOTA → LATIN SMALL LETTER I	# →ι→
					{0x2170,0x0069},	//	( ⅰ → i ) SMALL ROMAN NUMERAL ONE → LATIN SMALL LETTER I	#
					{0x2139,0x0069},	//	( ℹ → i ) INFORMATION SOURCE → LATIN SMALL LETTER I	#
					{0x2148,0x0069},	//	( ⅈ → i ) DOUBLE-STRUCK ITALIC SMALL I → LATIN SMALL LETTER I	#
					{0x1D422,0x0069},	//	( 𝐢 → i ) MATHEMATICAL BOLD SMALL I → LATIN SMALL LETTER I	#
					{0x1D456,0x0069},	//	( 𝑖 → i ) MATHEMATICAL ITALIC SMALL I → LATIN SMALL LETTER I	#
					{0x1D48A,0x0069},	//	( 𝒊 → i ) MATHEMATICAL BOLD ITALIC SMALL I → LATIN SMALL LETTER I	#
					{0x1D4BE,0x0069},	//	( 𝒾 → i ) MATHEMATICAL SCRIPT SMALL I → LATIN SMALL LETTER I	#
					{0x1D4F2,0x0069},	//	( 𝓲 → i ) MATHEMATICAL BOLD SCRIPT SMALL I → LATIN SMALL LETTER I	#
					{0x1D526,0x0069},	//	( 𝔦 → i ) MATHEMATICAL FRAKTUR SMALL I → LATIN SMALL LETTER I	#
					{0x1D55A,0x0069},	//	( 𝕚 → i ) MATHEMATICAL DOUBLE-STRUCK SMALL I → LATIN SMALL LETTER I	#
					{0x1D58E,0x0069},	//	( 𝖎 → i ) MATHEMATICAL BOLD FRAKTUR SMALL I → LATIN SMALL LETTER I	#
					{0x1D5C2,0x0069},	//	( 𝗂 → i ) MATHEMATICAL SANS-SERIF SMALL I → LATIN SMALL LETTER I	#
					{0x1D5F6,0x0069},	//	( 𝗶 → i ) MATHEMATICAL SANS-SERIF BOLD SMALL I → LATIN SMALL LETTER I	#
					{0x1D62A,0x0069},	//	( 𝘪 → i ) MATHEMATICAL SANS-SERIF ITALIC SMALL I → LATIN SMALL LETTER I	#
					{0x1D65E,0x0069},	//	( 𝙞 → i ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL I → LATIN SMALL LETTER I	#
					{0x1D692,0x0069},	//	( 𝚒 → i ) MATHEMATICAL MONOSPACE SMALL I → LATIN SMALL LETTER I	#
					{0x0131,0x0069},	//	( ı → i ) LATIN SMALL LETTER DOTLESS I → LATIN SMALL LETTER I	#
					{0x1D6A4,0x0069},	//	( 𝚤 → i ) MATHEMATICAL ITALIC SMALL DOTLESS I → LATIN SMALL LETTER I	# →ı→
					{0x026A,0x0069},	//	( ɪ → i ) LATIN LETTER SMALL CAPITAL I → LATIN SMALL LETTER I	# →ı→
					{0x0269,0x0069},	//	( ɩ → i ) LATIN SMALL LETTER IOTA → LATIN SMALL LETTER I	#
					{0x03B9,0x0069},	//	( ι → i ) GREEK SMALL LETTER IOTA → LATIN SMALL LETTER I	#
					{0x1FBE,0x0069},	//	( ι → i ) GREEK PROSGEGRAMMENI → LATIN SMALL LETTER I	# →ι→
					{0x037A,0x0069},	//	( ͺ → i ) GREEK YPOGEGRAMMENI → LATIN SMALL LETTER I	# →ι→→ι→
					{0x1D6CA,0x0069},	//	( 𝛊 → i ) MATHEMATICAL BOLD SMALL IOTA → LATIN SMALL LETTER I	# →ι→
					{0x1D704,0x0069},	//	( 𝜄 → i ) MATHEMATICAL ITALIC SMALL IOTA → LATIN SMALL LETTER I	# →ι→
					{0x1D73E,0x0069},	//	( 𝜾 → i ) MATHEMATICAL BOLD ITALIC SMALL IOTA → LATIN SMALL LETTER I	# →ι→
					{0x1D778,0x0069},	//	( 𝝸 → i ) MATHEMATICAL SANS-SERIF BOLD SMALL IOTA → LATIN SMALL LETTER I	# →ι→
					{0x1D7B2,0x0069},	//	( 𝞲 → i ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL IOTA → LATIN SMALL LETTER I	# →ι→
					{0x0456,0x0069},	//	( і → i ) CYRILLIC SMALL LETTER BYELORUSSIAN-UKRAINIAN I → LATIN SMALL LETTER I	#
					{0xA647,0x0069},	//	( ꙇ → i ) CYRILLIC SMALL LETTER IOTA → LATIN SMALL LETTER I	# →ι→
					{0x04CF,0x0069},	//	( ӏ → i ) CYRILLIC SMALL LETTER PALOCHKA → LATIN SMALL LETTER I	# →ı→
					{0xAB75,0x0069},	//	( ꭵ → i ) CHEROKEE SMALL LETTER V → LATIN SMALL LETTER I	#
					{0x13A5,0x0069},	//	( Ꭵ → i ) CHEROKEE LETTER V → LATIN SMALL LETTER I	#
					{0x118C3,0x0069},	//	( 𑣃 → i ) WARANG CITI SMALL LETTER YU → LATIN SMALL LETTER I	# →ι→
					{0x2149,0x006A},	//	( ⅉ → j ) DOUBLE-STRUCK ITALIC SMALL J → LATIN SMALL LETTER J	#
					{0x1D423,0x006A},	//	( 𝐣 → j ) MATHEMATICAL BOLD SMALL J → LATIN SMALL LETTER J	#
					{0x1D457,0x006A},	//	( 𝑗 → j ) MATHEMATICAL ITALIC SMALL J → LATIN SMALL LETTER J	#
					{0x1D48B,0x006A},	//	( 𝒋 → j ) MATHEMATICAL BOLD ITALIC SMALL J → LATIN SMALL LETTER J	#
					{0x1D4BF,0x006A},	//	( 𝒿 → j ) MATHEMATICAL SCRIPT SMALL J → LATIN SMALL LETTER J	#
					{0x1D4F3,0x006A},	//	( 𝓳 → j ) MATHEMATICAL BOLD SCRIPT SMALL J → LATIN SMALL LETTER J	#
					{0x1D527,0x006A},	//	( 𝔧 → j ) MATHEMATICAL FRAKTUR SMALL J → LATIN SMALL LETTER J	#
					{0x1D55B,0x006A},	//	( 𝕛 → j ) MATHEMATICAL DOUBLE-STRUCK SMALL J → LATIN SMALL LETTER J	#
					{0x1D58F,0x006A},	//	( 𝖏 → j ) MATHEMATICAL BOLD FRAKTUR SMALL J → LATIN SMALL LETTER J	#
					{0x1D5C3,0x006A},	//	( 𝗃 → j ) MATHEMATICAL SANS-SERIF SMALL J → LATIN SMALL LETTER J	#
					{0x1D5F7,0x006A},	//	( 𝗷 → j ) MATHEMATICAL SANS-SERIF BOLD SMALL J → LATIN SMALL LETTER J	#
					{0x1D62B,0x006A},	//	( 𝘫 → j ) MATHEMATICAL SANS-SERIF ITALIC SMALL J → LATIN SMALL LETTER J	#
					{0x1D65F,0x006A},	//	( 𝙟 → j ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL J → LATIN SMALL LETTER J	#
					{0x1D693,0x006A},	//	( 𝚓 → j ) MATHEMATICAL MONOSPACE SMALL J → LATIN SMALL LETTER J	#
					{0x03F3,0x006A},	//	( ϳ → j ) GREEK LETTER YOT → LATIN SMALL LETTER J	#
					{0x0458,0x006A},	//	( ј → j ) CYRILLIC SMALL LETTER JE → LATIN SMALL LETTER J	#
					{0x1D409,0x004A},	//	( 𝐉 → J ) MATHEMATICAL BOLD CAPITAL J → LATIN CAPITAL LETTER J	#
					{0x1D43D,0x004A},	//	( 𝐽 → J ) MATHEMATICAL ITALIC CAPITAL J → LATIN CAPITAL LETTER J	#
					{0x1D471,0x004A},	//	( 𝑱 → J ) MATHEMATICAL BOLD ITALIC CAPITAL J → LATIN CAPITAL LETTER J	#
					{0x1D4A5,0x004A},	//	( 𝒥 → J ) MATHEMATICAL SCRIPT CAPITAL J → LATIN CAPITAL LETTER J	#
					{0x1D4D9,0x004A},	//	( 𝓙 → J ) MATHEMATICAL BOLD SCRIPT CAPITAL J → LATIN CAPITAL LETTER J	#
					{0x1D50D,0x004A},	//	( 𝔍 → J ) MATHEMATICAL FRAKTUR CAPITAL J → LATIN CAPITAL LETTER J	#
					{0x1D541,0x004A},	//	( 𝕁 → J ) MATHEMATICAL DOUBLE-STRUCK CAPITAL J → LATIN CAPITAL LETTER J	#
					{0x1D575,0x004A},	//	( 𝕵 → J ) MATHEMATICAL BOLD FRAKTUR CAPITAL J → LATIN CAPITAL LETTER J	#
					{0x1D5A9,0x004A},	//	( 𝖩 → J ) MATHEMATICAL SANS-SERIF CAPITAL J → LATIN CAPITAL LETTER J	#
					{0x1D5DD,0x004A},	//	( 𝗝 → J ) MATHEMATICAL SANS-SERIF BOLD CAPITAL J → LATIN CAPITAL LETTER J	#
					{0x1D611,0x004A},	//	( 𝘑 → J ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL J → LATIN CAPITAL LETTER J	#
					{0x1D645,0x004A},	//	( 𝙅 → J ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL J → LATIN CAPITAL LETTER J	#
					{0x1D679,0x004A},	//	( 𝙹 → J ) MATHEMATICAL MONOSPACE CAPITAL J → LATIN CAPITAL LETTER J	#
					{0xA7B2,0x004A},	//	( Ʝ → J ) LATIN CAPITAL LETTER J WITH CROSSED-TAIL → LATIN CAPITAL LETTER J	#
					{0x037F,0x004A},	//	( Ϳ → J ) GREEK CAPITAL LETTER YOT → LATIN CAPITAL LETTER J	#
					{0x0408,0x004A},	//	( Ј → J ) CYRILLIC CAPITAL LETTER JE → LATIN CAPITAL LETTER J	#
					{0x13AB,0x004A},	//	( Ꭻ → J ) CHEROKEE LETTER GU → LATIN CAPITAL LETTER J	#
					{0x148D,0x004A},	//	( ᒍ → J ) CANADIAN SYLLABICS CO → LATIN CAPITAL LETTER J	#
					{0xA4D9,0x004A},	//	( ꓙ → J ) LISU LETTER JA → LATIN CAPITAL LETTER J	#
					{0x1D424,0x006B},	//	( 𝐤 → k ) MATHEMATICAL BOLD SMALL K → LATIN SMALL LETTER K	#
					{0x1D458,0x006B},	//	( 𝑘 → k ) MATHEMATICAL ITALIC SMALL K → LATIN SMALL LETTER K	#
					{0x1D48C,0x006B},	//	( 𝒌 → k ) MATHEMATICAL BOLD ITALIC SMALL K → LATIN SMALL LETTER K	#
					{0x1D4C0,0x006B},	//	( 𝓀 → k ) MATHEMATICAL SCRIPT SMALL K → LATIN SMALL LETTER K	#
					{0x1D4F4,0x006B},	//	( 𝓴 → k ) MATHEMATICAL BOLD SCRIPT SMALL K → LATIN SMALL LETTER K	#
					{0x1D528,0x006B},	//	( 𝔨 → k ) MATHEMATICAL FRAKTUR SMALL K → LATIN SMALL LETTER K	#
					{0x1D55C,0x006B},	//	( 𝕜 → k ) MATHEMATICAL DOUBLE-STRUCK SMALL K → LATIN SMALL LETTER K	#
					{0x1D590,0x006B},	//	( 𝖐 → k ) MATHEMATICAL BOLD FRAKTUR SMALL K → LATIN SMALL LETTER K	#
					{0x1D5C4,0x006B},	//	( 𝗄 → k ) MATHEMATICAL SANS-SERIF SMALL K → LATIN SMALL LETTER K	#
					{0x1D5F8,0x006B},	//	( 𝗸 → k ) MATHEMATICAL SANS-SERIF BOLD SMALL K → LATIN SMALL LETTER K	#
					{0x1D62C,0x006B},	//	( 𝘬 → k ) MATHEMATICAL SANS-SERIF ITALIC SMALL K → LATIN SMALL LETTER K	#
					{0x1D660,0x006B},	//	( 𝙠 → k ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL K → LATIN SMALL LETTER K	#
					{0x1D694,0x006B},	//	( 𝚔 → k ) MATHEMATICAL MONOSPACE SMALL K → LATIN SMALL LETTER K	#
					{0x212A,0x004B},	//	( K → K ) KELVIN SIGN → LATIN CAPITAL LETTER K	#
					{0x1D40A,0x004B},	//	( 𝐊 → K ) MATHEMATICAL BOLD CAPITAL K → LATIN CAPITAL LETTER K	#
					{0x1D43E,0x004B},	//	( 𝐾 → K ) MATHEMATICAL ITALIC CAPITAL K → LATIN CAPITAL LETTER K	#
					{0x1D472,0x004B},	//	( 𝑲 → K ) MATHEMATICAL BOLD ITALIC CAPITAL K → LATIN CAPITAL LETTER K	#
					{0x1D4A6,0x004B},	//	( 𝒦 → K ) MATHEMATICAL SCRIPT CAPITAL K → LATIN CAPITAL LETTER K	#
					{0x1D4DA,0x004B},	//	( 𝓚 → K ) MATHEMATICAL BOLD SCRIPT CAPITAL K → LATIN CAPITAL LETTER K	#
					{0x1D50E,0x004B},	//	( 𝔎 → K ) MATHEMATICAL FRAKTUR CAPITAL K → LATIN CAPITAL LETTER K	#
					{0x1D542,0x004B},	//	( 𝕂 → K ) MATHEMATICAL DOUBLE-STRUCK CAPITAL K → LATIN CAPITAL LETTER K	#
					{0x1D576,0x004B},	//	( 𝕶 → K ) MATHEMATICAL BOLD FRAKTUR CAPITAL K → LATIN CAPITAL LETTER K	#
					{0x1D5AA,0x004B},	//	( 𝖪 → K ) MATHEMATICAL SANS-SERIF CAPITAL K → LATIN CAPITAL LETTER K	#
					{0x1D5DE,0x004B},	//	( 𝗞 → K ) MATHEMATICAL SANS-SERIF BOLD CAPITAL K → LATIN CAPITAL LETTER K	#
					{0x1D612,0x004B},	//	( 𝘒 → K ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL K → LATIN CAPITAL LETTER K	#
					{0x1D646,0x004B},	//	( 𝙆 → K ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL K → LATIN CAPITAL LETTER K	#
					{0x1D67A,0x004B},	//	( 𝙺 → K ) MATHEMATICAL MONOSPACE CAPITAL K → LATIN CAPITAL LETTER K	#
					{0x039A,0x004B},	//	( Κ → K ) GREEK CAPITAL LETTER KAPPA → LATIN CAPITAL LETTER K	#
					{0x1D6B1,0x004B},	//	( 𝚱 → K ) MATHEMATICAL BOLD CAPITAL KAPPA → LATIN CAPITAL LETTER K	# →Κ→
					{0x1D6EB,0x004B},	//	( 𝛫 → K ) MATHEMATICAL ITALIC CAPITAL KAPPA → LATIN CAPITAL LETTER K	# →𝐾→
					{0x1D725,0x004B},	//	( 𝜥 → K ) MATHEMATICAL BOLD ITALIC CAPITAL KAPPA → LATIN CAPITAL LETTER K	# →𝑲→
					{0x1D75F,0x004B},	//	( 𝝟 → K ) MATHEMATICAL SANS-SERIF BOLD CAPITAL KAPPA → LATIN CAPITAL LETTER K	# →Κ→
					{0x1D799,0x004B},	//	( 𝞙 → K ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL KAPPA → LATIN CAPITAL LETTER K	# →Κ→
					{0x2C94,0x004B},	//	( Ⲕ → K ) COPTIC CAPITAL LETTER KAPA → LATIN CAPITAL LETTER K	# →Κ→
					{0x041A,0x004B},	//	( К → K ) CYRILLIC CAPITAL LETTER KA → LATIN CAPITAL LETTER K	#
					{0x13E6,0x004B},	//	( Ꮶ → K ) CHEROKEE LETTER TSO → LATIN CAPITAL LETTER K	#
					{0x16D5,0x004B},	//	( ᛕ → K ) RUNIC LETTER OPEN-P → LATIN CAPITAL LETTER K	#
					{0xA4D7,0x004B},	//	( ꓗ → K ) LISU LETTER KA → LATIN CAPITAL LETTER K	#
					{0x10518,0x004B},	//	( 𐔘 → K ) ELBASAN LETTER QE → LATIN CAPITAL LETTER K	#
					{0x0130,0x0049},	//	( İ → I ) LATIN CAPITAL LETTER I WITH DOT ABOVE → LATIN CAPITAL LETTER I // added for symmetry with LATIN SMALL LETTER DOTLESS I → LATIN SMALL LETTER I
					// A lot of mappings that originally were to lowercase-l had to be changed to avoid surprising users.
					// It would be best if we could fuzzy-match all of these agains either "I", "l", "|" or "1", but currently that’s not possible
					// without introducing undesirable matches.
					{0x05C0,0x006C},	//	( ‎׀‎ → l ) HEBREW PUNCTUATION PASEQ → LATIN SMALL LETTER L	# →|→
					{0x2223,0x007C},	//	( ∣ → | ) DIVIDES → VERTICAL LINE	#
					{0x23FD,0x007C},	//	( ⏽ → | ) POWER ON SYMBOL → VERTICAL LINE	#
					{0xFFE8,0x007C},	//	( ￨ → | ) HALFWIDTH FORMS LIGHT VERTICAL → VERTICAL LINE	#
					//{0x0661,0x006C},	//	( ‎١‎ → l ) ARABIC-INDIC DIGIT ONE → LATIN SMALL LETTER L	# →1→	// users of Arabic script are unlikely to appreciate conflating these two
					//{0x06F1,0x006C},	//	( ۱ → l ) EXTENDED ARABIC-INDIC DIGIT ONE → LATIN SMALL LETTER L	# →1→	// users of Arabic script are unlikely to appreciate conflating these two
					{0x10320,0x006C},	//	( 𐌠 → l ) OLD ITALIC NUMERAL ONE → LATIN SMALL LETTER L	# →𐌉→→I→
					{0x1E8C7,0x006C},	//	( ‎𞣇‎ → l ) MENDE KIKAKUI DIGIT ONE → LATIN SMALL LETTER L	#
					{0x1D7CF,0x0031},	//	( 𝟏 → 1 ) MATHEMATICAL BOLD DIGIT ONE → DIGIT ONE	#
					{0x1D7D9,0x0031},	//	( 𝟙 → 1 ) MATHEMATICAL DOUBLE-STRUCK DIGIT ONE → DIGIT ONE	#
					{0x1D7E3,0x0031},	//	( 𝟣 → 1 ) MATHEMATICAL SANS-SERIF DIGIT ONE → DIGIT ONE	#
					{0x1D7ED,0x0031},	//	( 𝟭 → 1 ) MATHEMATICAL SANS-SERIF BOLD DIGIT ONE → DIGIT ONE	#
					{0x1D7F7,0x0031},	//	( 𝟷 → 1 ) MATHEMATICAL MONOSPACE DIGIT ONE → DIGIT ONE	#
					{0x1FBF1,0x0031},	//	( 🯱 → 1 ) SEGMENTED DIGIT ONE → DIGIT ONE	#
					{0x2160,0x0049},	//	( Ⅰ → I ) ROMAN NUMERAL ONE → LATIN CAPITAL LETTER I	#
					{0x2110,0x0049},	//	( ℐ → I ) SCRIPT CAPITAL I → LATIN CAPITAL LETTER I	#
					{0x2111,0x0049},	//	( ℑ → I ) BLACK-LETTER CAPITAL I → LATIN CAPITAL LETTER I	#
					{0x1D408,0x0049},	//	( 𝐈 → I ) MATHEMATICAL BOLD CAPITAL I → LATIN CAPITAL LETTER I	#
					{0x1D43C,0x0049},	//	( 𝐼 → I ) MATHEMATICAL ITALIC CAPITAL I → LATIN CAPITAL LETTER I	#
					{0x1D470,0x0049},	//	( 𝑰 → I ) MATHEMATICAL BOLD ITALIC CAPITAL I → LATIN CAPITAL LETTER I	#
					{0x1D4D8,0x0049},	//	( 𝓘 → I ) MATHEMATICAL BOLD SCRIPT CAPITAL I → LATIN CAPITAL LETTER I	#
					{0x1D540,0x0049},	//	( 𝕀 → I ) MATHEMATICAL DOUBLE-STRUCK CAPITAL I → LATIN CAPITAL LETTER I	#
					{0x1D574,0x0049},	//	( 𝕴 → I ) MATHEMATICAL BOLD FRAKTUR CAPITAL I → LATIN CAPITAL LETTER I	#
					{0x1D5A8,0x0049},	//	( 𝖨 → I ) MATHEMATICAL SANS-SERIF CAPITAL I → LATIN CAPITAL LETTER I	#
					{0x1D5DC,0x0049},	//	( 𝗜 → I ) MATHEMATICAL SANS-SERIF BOLD CAPITAL I → LATIN CAPITAL LETTER I	#
					{0x1D610,0x0049},	//	( 𝘐 → I ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL I → LATIN CAPITAL LETTER I	#
					{0x1D644,0x0049},	//	( 𝙄 → I ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL I → LATIN CAPITAL LETTER I	#
					{0x1D678,0x0049},	//	( 𝙸 → I ) MATHEMATICAL MONOSPACE CAPITAL I → LATIN CAPITAL LETTER I	#
					{0x0196,0x0049},	//	( Ɩ → I ) LATIN CAPITAL LETTER IOTA → LATIN CAPITAL LETTER I	#
					{0x217C,0x006C},	//	( ⅼ → l ) SMALL ROMAN NUMERAL FIFTY → LATIN SMALL LETTER L	#
					{0x2113,0x006C},	//	( ℓ → l ) SCRIPT SMALL L → LATIN SMALL LETTER L	#
					{0x1D425,0x006C},	//	( 𝐥 → l ) MATHEMATICAL BOLD SMALL L → LATIN SMALL LETTER L	#
					{0x1D459,0x006C},	//	( 𝑙 → l ) MATHEMATICAL ITALIC SMALL L → LATIN SMALL LETTER L	#
					{0x1D48D,0x006C},	//	( 𝒍 → l ) MATHEMATICAL BOLD ITALIC SMALL L → LATIN SMALL LETTER L	#
					{0x1D4C1,0x006C},	//	( 𝓁 → l ) MATHEMATICAL SCRIPT SMALL L → LATIN SMALL LETTER L	#
					{0x1D4F5,0x006C},	//	( 𝓵 → l ) MATHEMATICAL BOLD SCRIPT SMALL L → LATIN SMALL LETTER L	#
					{0x1D529,0x006C},	//	( 𝔩 → l ) MATHEMATICAL FRAKTUR SMALL L → LATIN SMALL LETTER L	#
					{0x1D55D,0x006C},	//	( 𝕝 → l ) MATHEMATICAL DOUBLE-STRUCK SMALL L → LATIN SMALL LETTER L	#
					{0x1D591,0x006C},	//	( 𝖑 → l ) MATHEMATICAL BOLD FRAKTUR SMALL L → LATIN SMALL LETTER L	#
					{0x1D5C5,0x006C},	//	( 𝗅 → l ) MATHEMATICAL SANS-SERIF SMALL L → LATIN SMALL LETTER L	#
					{0x1D5F9,0x006C},	//	( 𝗹 → l ) MATHEMATICAL SANS-SERIF BOLD SMALL L → LATIN SMALL LETTER L	#
					{0x1D62D,0x006C},	//	( 𝘭 → l ) MATHEMATICAL SANS-SERIF ITALIC SMALL L → LATIN SMALL LETTER L	#
					{0x1D661,0x006C},	//	( 𝙡 → l ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL L → LATIN SMALL LETTER L	#
					{0x1D695,0x006C},	//	( 𝚕 → l ) MATHEMATICAL MONOSPACE SMALL L → LATIN SMALL LETTER L	#
					{0x01C0,0x007C},	//	( ǀ → | ) LATIN LETTER DENTAL CLICK → VERTICAL LINE	#
					{0x0399,0x0049},	//	( Ι → I ) GREEK CAPITAL LETTER IOTA → LATIN CAPITAL LETTER I	#
					{0x1D6B0,0x0049},	//	( 𝚰 → I ) MATHEMATICAL BOLD CAPITAL IOTA → LATIN CAPITAL LETTER I	#
					{0x1D6EA,0x0049},	//	( 𝛪 → I ) MATHEMATICAL ITALIC CAPITAL IOTA → LATIN CAPITAL LETTER I	#
					{0x1D724,0x0049},	//	( 𝜤 → I ) MATHEMATICAL BOLD ITALIC CAPITAL IOTA → LATIN CAPITAL LETTER I	#
					{0x1D75E,0x0049},	//	( 𝝞 → I ) MATHEMATICAL SANS-SERIF BOLD CAPITAL IOTA → LATIN CAPITAL LETTER I	#
					{0x1D798,0x0049},	//	( 𝞘 → I ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL IOTA → LATIN CAPITAL LETTER I	#
					{0x2C92,0x0049},	//	( Ⲓ → I ) COPTIC CAPITAL LETTER IAUDA → LATIN CAPITAL LETTER I	#
					{0x0406,0x0049},	//	( І → I ) CYRILLIC CAPITAL LETTER BYELORUSSIAN-UKRAINIAN I → LATIN CAPITAL LETTER I	#
					{0x04C0,0x0049},	//	( Ӏ → I ) CYRILLIC LETTER PALOCHKA → LATIN CAPITAL LETTER I	#
					{0x05D5,0x006C},	//	( ‎ו‎ → l ) HEBREW LETTER VAV → LATIN SMALL LETTER L	#
					{0x05DF,0x006C},	//	( ‎ן‎ → l ) HEBREW LETTER FINAL NUN → LATIN SMALL LETTER L	#
					{0x0627,0x0031},	//	( ‎ا‎ → 1 ) ARABIC LETTER ALEF → DIGIT ONE	#
					{0x1EE00,0x0031},	//	( ‎𞸀‎ → 1 ) ARABIC MATHEMATICAL ALEF → DIGIT ONE	# →‎ا‎→
					{0x1EE80,0x0031},	//	( ‎𞺀‎ → 1 ) ARABIC MATHEMATICAL LOOPED ALEF → DIGIT ONE	# →‎ا‎→
					{0xFE8E,0x0031},	//	( ‎ﺎ‎ → 1 ) ARABIC LETTER ALEF FINAL FORM → DIGIT ONE	# →‎ا‎→
					{0xFE8D,0x0031},	//	( ‎ﺍ‎ → 1 ) ARABIC LETTER ALEF ISOLATED FORM → DIGIT ONE	# →‎ا‎→
					{0x07CA,0x007C},	//	( ‎ߊ‎ → | ) NKO LETTER A → VERTICAL LINE	# →∣→→ǀ→
					{0x2D4F,0x0049},	//	( ⵏ → I ) TIFINAGH LETTER YAN → LATIN SMALL LETTER L	#
					{0x16C1,0x0049},	//	( ᛁ → I ) RUNIC LETTER ISAZ IS ISS I → LATIN CAPITAL LETTER I	#
					{0xA4F2,0x0049},	//	( ꓲ → I ) LISU LETTER I → LATIN CAPITAL LETTER I	#
					{0x16F28,0x0049},	//	( 𖼨 → I ) MIAO LETTER GHA → LATIN CAPITAL LETTER I	#
					{0x1028A,0x0049},	//	( 𐊊 → I ) LYCIAN LETTER J → LATIN CAPITAL LETTER I	#
					{0x10309,0x0049},	//	( 𐌉 → I ) OLD ITALIC LETTER I → LATIN CAPITAL LETTER I	#
					{0x1D22A,0x004C},	//	( 𝈪 → L ) GREEK INSTRUMENTAL NOTATION SYMBOL-23 → LATIN CAPITAL LETTER L	#
					{0x216C,0x004C},	//	( Ⅼ → L ) ROMAN NUMERAL FIFTY → LATIN CAPITAL LETTER L	#
					{0x2112,0x004C},	//	( ℒ → L ) SCRIPT CAPITAL L → LATIN CAPITAL LETTER L	#
					{0x1D40B,0x004C},	//	( 𝐋 → L ) MATHEMATICAL BOLD CAPITAL L → LATIN CAPITAL LETTER L	#
					{0x1D43F,0x004C},	//	( 𝐿 → L ) MATHEMATICAL ITALIC CAPITAL L → LATIN CAPITAL LETTER L	#
					{0x1D473,0x004C},	//	( 𝑳 → L ) MATHEMATICAL BOLD ITALIC CAPITAL L → LATIN CAPITAL LETTER L	#
					{0x1D4DB,0x004C},	//	( 𝓛 → L ) MATHEMATICAL BOLD SCRIPT CAPITAL L → LATIN CAPITAL LETTER L	#
					{0x1D50F,0x004C},	//	( 𝔏 → L ) MATHEMATICAL FRAKTUR CAPITAL L → LATIN CAPITAL LETTER L	#
					{0x1D543,0x004C},	//	( 𝕃 → L ) MATHEMATICAL DOUBLE-STRUCK CAPITAL L → LATIN CAPITAL LETTER L	#
					{0x1D577,0x004C},	//	( 𝕷 → L ) MATHEMATICAL BOLD FRAKTUR CAPITAL L → LATIN CAPITAL LETTER L	#
					{0x1D5AB,0x004C},	//	( 𝖫 → L ) MATHEMATICAL SANS-SERIF CAPITAL L → LATIN CAPITAL LETTER L	#
					{0x1D5DF,0x004C},	//	( 𝗟 → L ) MATHEMATICAL SANS-SERIF BOLD CAPITAL L → LATIN CAPITAL LETTER L	#
					{0x1D613,0x004C},	//	( 𝘓 → L ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL L → LATIN CAPITAL LETTER L	#
					{0x1D647,0x004C},	//	( 𝙇 → L ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL L → LATIN CAPITAL LETTER L	#
					{0x1D67B,0x004C},	//	( 𝙻 → L ) MATHEMATICAL MONOSPACE CAPITAL L → LATIN CAPITAL LETTER L	#
					{0x2CD0,0x004C},	//	( Ⳑ → L ) COPTIC CAPITAL LETTER L-SHAPED HA → LATIN CAPITAL LETTER L	#
					{0x13DE,0x004C},	//	( Ꮮ → L ) CHEROKEE LETTER TLE → LATIN CAPITAL LETTER L	#
					{0x14AA,0x004C},	//	( ᒪ → L ) CANADIAN SYLLABICS MA → LATIN CAPITAL LETTER L	#
					{0xA4E1,0x004C},	//	( ꓡ → L ) LISU LETTER LA → LATIN CAPITAL LETTER L	#
					{0x16F16,0x004C},	//	( 𖼖 → L ) MIAO LETTER LA → LATIN CAPITAL LETTER L	#
					{0x118A3,0x004C},	//	( 𑢣 → L ) WARANG CITI CAPITAL LETTER YU → LATIN CAPITAL LETTER L	#
					{0x118B2,0x004C},	//	( 𑢲 → L ) WARANG CITI CAPITAL LETTER TTE → LATIN CAPITAL LETTER L	#
					{0x1041B,0x004C},	//	( 𐐛 → L ) DESERET CAPITAL LETTER ETH → LATIN CAPITAL LETTER L	#
					{0x10526,0x004C},	//	( 𐔦 → L ) ELBASAN LETTER GHAMMA → LATIN CAPITAL LETTER L	#
					{0x216F,0x004D},	//	( Ⅿ → M ) ROMAN NUMERAL ONE THOUSAND → LATIN CAPITAL LETTER M	#
					{0x2133,0x004D},	//	( ℳ → M ) SCRIPT CAPITAL M → LATIN CAPITAL LETTER M	#
					{0x1D40C,0x004D},	//	( 𝐌 → M ) MATHEMATICAL BOLD CAPITAL M → LATIN CAPITAL LETTER M	#
					{0x1D440,0x004D},	//	( 𝑀 → M ) MATHEMATICAL ITALIC CAPITAL M → LATIN CAPITAL LETTER M	#
					{0x1D474,0x004D},	//	( 𝑴 → M ) MATHEMATICAL BOLD ITALIC CAPITAL M → LATIN CAPITAL LETTER M	#
					{0x1D4DC,0x004D},	//	( 𝓜 → M ) MATHEMATICAL BOLD SCRIPT CAPITAL M → LATIN CAPITAL LETTER M	#
					{0x1D510,0x004D},	//	( 𝔐 → M ) MATHEMATICAL FRAKTUR CAPITAL M → LATIN CAPITAL LETTER M	#
					{0x1D544,0x004D},	//	( 𝕄 → M ) MATHEMATICAL DOUBLE-STRUCK CAPITAL M → LATIN CAPITAL LETTER M	#
					{0x1D578,0x004D},	//	( 𝕸 → M ) MATHEMATICAL BOLD FRAKTUR CAPITAL M → LATIN CAPITAL LETTER M	#
					{0x1D5AC,0x004D},	//	( 𝖬 → M ) MATHEMATICAL SANS-SERIF CAPITAL M → LATIN CAPITAL LETTER M	#
					{0x1D5E0,0x004D},	//	( 𝗠 → M ) MATHEMATICAL SANS-SERIF BOLD CAPITAL M → LATIN CAPITAL LETTER M	#
					{0x1D614,0x004D},	//	( 𝘔 → M ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL M → LATIN CAPITAL LETTER M	#
					{0x1D648,0x004D},	//	( 𝙈 → M ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL M → LATIN CAPITAL LETTER M	#
					{0x1D67C,0x004D},	//	( 𝙼 → M ) MATHEMATICAL MONOSPACE CAPITAL M → LATIN CAPITAL LETTER M	#
					{0x039C,0x004D},	//	( Μ → M ) GREEK CAPITAL LETTER MU → LATIN CAPITAL LETTER M	#
					{0x1D6B3,0x004D},	//	( 𝚳 → M ) MATHEMATICAL BOLD CAPITAL MU → LATIN CAPITAL LETTER M	# →𝐌→
					{0x1D6ED,0x004D},	//	( 𝛭 → M ) MATHEMATICAL ITALIC CAPITAL MU → LATIN CAPITAL LETTER M	# →𝑀→
					{0x1D727,0x004D},	//	( 𝜧 → M ) MATHEMATICAL BOLD ITALIC CAPITAL MU → LATIN CAPITAL LETTER M	# →𝑴→
					{0x1D761,0x004D},	//	( 𝝡 → M ) MATHEMATICAL SANS-SERIF BOLD CAPITAL MU → LATIN CAPITAL LETTER M	# →Μ→
					{0x1D79B,0x004D},	//	( 𝞛 → M ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL MU → LATIN CAPITAL LETTER M	# →Μ→
					{0x03FA,0x004D},	//	( Ϻ → M ) GREEK CAPITAL LETTER SAN → LATIN CAPITAL LETTER M	#
					{0x2C98,0x004D},	//	( Ⲙ → M ) COPTIC CAPITAL LETTER MI → LATIN CAPITAL LETTER M	#
					{0x041C,0x004D},	//	( М → M ) CYRILLIC CAPITAL LETTER EM → LATIN CAPITAL LETTER M	#
					{0x13B7,0x004D},	//	( Ꮇ → M ) CHEROKEE LETTER LU → LATIN CAPITAL LETTER M	#
					{0x15F0,0x004D},	//	( ᗰ → M ) CANADIAN SYLLABICS CARRIER GO → LATIN CAPITAL LETTER M	#
					{0x16D6,0x004D},	//	( ᛖ → M ) RUNIC LETTER EHWAZ EH E → LATIN CAPITAL LETTER M	#
					{0xA4DF,0x004D},	//	( ꓟ → M ) LISU LETTER MA → LATIN CAPITAL LETTER M	#
					{0x102B0,0x004D},	//	( 𐊰 → M ) CARIAN LETTER S → LATIN CAPITAL LETTER M	#
					{0x10311,0x004D},	//	( 𐌑 → M ) OLD ITALIC LETTER SHE → LATIN CAPITAL LETTER M	#
					{0x1D427,0x006E},	//	( 𝐧 → n ) MATHEMATICAL BOLD SMALL N → LATIN SMALL LETTER N	#
					{0x1D45B,0x006E},	//	( 𝑛 → n ) MATHEMATICAL ITALIC SMALL N → LATIN SMALL LETTER N	#
					{0x1D48F,0x006E},	//	( 𝒏 → n ) MATHEMATICAL BOLD ITALIC SMALL N → LATIN SMALL LETTER N	#
					{0x1D4C3,0x006E},	//	( 𝓃 → n ) MATHEMATICAL SCRIPT SMALL N → LATIN SMALL LETTER N	#
					{0x1D4F7,0x006E},	//	( 𝓷 → n ) MATHEMATICAL BOLD SCRIPT SMALL N → LATIN SMALL LETTER N	#
					{0x1D52B,0x006E},	//	( 𝔫 → n ) MATHEMATICAL FRAKTUR SMALL N → LATIN SMALL LETTER N	#
					{0x1D55F,0x006E},	//	( 𝕟 → n ) MATHEMATICAL DOUBLE-STRUCK SMALL N → LATIN SMALL LETTER N	#
					{0x1D593,0x006E},	//	( 𝖓 → n ) MATHEMATICAL BOLD FRAKTUR SMALL N → LATIN SMALL LETTER N	#
					{0x1D5C7,0x006E},	//	( 𝗇 → n ) MATHEMATICAL SANS-SERIF SMALL N → LATIN SMALL LETTER N	#
					{0x1D5FB,0x006E},	//	( 𝗻 → n ) MATHEMATICAL SANS-SERIF BOLD SMALL N → LATIN SMALL LETTER N	#
					{0x1D62F,0x006E},	//	( 𝘯 → n ) MATHEMATICAL SANS-SERIF ITALIC SMALL N → LATIN SMALL LETTER N	#
					{0x1D663,0x006E},	//	( 𝙣 → n ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL N → LATIN SMALL LETTER N	#
					{0x1D697,0x006E},	//	( 𝚗 → n ) MATHEMATICAL MONOSPACE SMALL N → LATIN SMALL LETTER N	#
					{0x0578,0x006E},	//	( ո → n ) ARMENIAN SMALL LETTER VO → LATIN SMALL LETTER N	#
					{0x057C,0x006E},	//	( ռ → n ) ARMENIAN SMALL LETTER RA → LATIN SMALL LETTER N	#
					{0x2115,0x004E},	//	( ℕ → N ) DOUBLE-STRUCK CAPITAL N → LATIN CAPITAL LETTER N	#
					{0x1D40D,0x004E},	//	( 𝐍 → N ) MATHEMATICAL BOLD CAPITAL N → LATIN CAPITAL LETTER N	#
					{0x1D441,0x004E},	//	( 𝑁 → N ) MATHEMATICAL ITALIC CAPITAL N → LATIN CAPITAL LETTER N	#
					{0x1D475,0x004E},	//	( 𝑵 → N ) MATHEMATICAL BOLD ITALIC CAPITAL N → LATIN CAPITAL LETTER N	#
					{0x1D4A9,0x004E},	//	( 𝒩 → N ) MATHEMATICAL SCRIPT CAPITAL N → LATIN CAPITAL LETTER N	#
					{0x1D4DD,0x004E},	//	( 𝓝 → N ) MATHEMATICAL BOLD SCRIPT CAPITAL N → LATIN CAPITAL LETTER N	#
					{0x1D511,0x004E},	//	( 𝔑 → N ) MATHEMATICAL FRAKTUR CAPITAL N → LATIN CAPITAL LETTER N	#
					{0x1D579,0x004E},	//	( 𝕹 → N ) MATHEMATICAL BOLD FRAKTUR CAPITAL N → LATIN CAPITAL LETTER N	#
					{0x1D5AD,0x004E},	//	( 𝖭 → N ) MATHEMATICAL SANS-SERIF CAPITAL N → LATIN CAPITAL LETTER N	#
					{0x1D5E1,0x004E},	//	( 𝗡 → N ) MATHEMATICAL SANS-SERIF BOLD CAPITAL N → LATIN CAPITAL LETTER N	#
					{0x1D615,0x004E},	//	( 𝘕 → N ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL N → LATIN CAPITAL LETTER N	#
					{0x1D649,0x004E},	//	( 𝙉 → N ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL N → LATIN CAPITAL LETTER N	#
					{0x1D67D,0x004E},	//	( 𝙽 → N ) MATHEMATICAL MONOSPACE CAPITAL N → LATIN CAPITAL LETTER N	#
					{0x039D,0x004E},	//	( Ν → N ) GREEK CAPITAL LETTER NU → LATIN CAPITAL LETTER N	#
					{0x1D6B4,0x004E},	//	( 𝚴 → N ) MATHEMATICAL BOLD CAPITAL NU → LATIN CAPITAL LETTER N	# →𝐍→
					{0x1D6EE,0x004E},	//	( 𝛮 → N ) MATHEMATICAL ITALIC CAPITAL NU → LATIN CAPITAL LETTER N	# →𝑁→
					{0x1D728,0x004E},	//	( 𝜨 → N ) MATHEMATICAL BOLD ITALIC CAPITAL NU → LATIN CAPITAL LETTER N	# →𝑵→
					{0x1D762,0x004E},	//	( 𝝢 → N ) MATHEMATICAL SANS-SERIF BOLD CAPITAL NU → LATIN CAPITAL LETTER N	# →Ν→
					{0x1D79C,0x004E},	//	( 𝞜 → N ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL NU → LATIN CAPITAL LETTER N	# →Ν→
					{0x2C9A,0x004E},	//	( Ⲛ → N ) COPTIC CAPITAL LETTER NI → LATIN CAPITAL LETTER N	#
					{0xA4E0,0x004E},	//	( ꓠ → N ) LISU LETTER NA → LATIN CAPITAL LETTER N	#
					{0x10513,0x004E},	//	( 𐔓 → N ) ELBASAN LETTER NE → LATIN CAPITAL LETTER N	#
					{0x0C02,0x006F},	//	( ం → o ) TELUGU SIGN ANUSVARA → LATIN SMALL LETTER O	#
					{0x0C82,0x006F},	//	( ಂ → o ) KANNADA SIGN ANUSVARA → LATIN SMALL LETTER O	#
					{0x0D02,0x006F},	//	( ം → o ) MALAYALAM SIGN ANUSVARA → LATIN SMALL LETTER O	#
					{0x0D82,0x006F},	//	( ං → o ) SINHALA SIGN ANUSVARAYA → LATIN SMALL LETTER O	#
					{0x0966,0x006F},	//	( ० → o ) DEVANAGARI DIGIT ZERO → LATIN SMALL LETTER O	#
					{0x0A66,0x006F},	//	( ੦ → o ) GURMUKHI DIGIT ZERO → LATIN SMALL LETTER O	#
					{0x0AE6,0x006F},	//	( ૦ → o ) GUJARATI DIGIT ZERO → LATIN SMALL LETTER O	#
					{0x0BE6,0x006F},	//	( ௦ → o ) TAMIL DIGIT ZERO → LATIN SMALL LETTER O	#
					{0x0C66,0x006F},	//	( ౦ → o ) TELUGU DIGIT ZERO → LATIN SMALL LETTER O	#
					{0x0CE6,0x006F},	//	( ೦ → o ) KANNADA DIGIT ZERO → LATIN SMALL LETTER O	# →౦→
					{0x0D66,0x006F},	//	( ൦ → o ) MALAYALAM DIGIT ZERO → LATIN SMALL LETTER O	#
					{0x0E50,0x006F},	//	( ๐ → o ) THAI DIGIT ZERO → LATIN SMALL LETTER O	#
					{0x0ED0,0x006F},	//	( ໐ → o ) LAO DIGIT ZERO → LATIN SMALL LETTER O	#
					{0x1040,0x006F},	//	( ၀ → o ) MYANMAR DIGIT ZERO → LATIN SMALL LETTER O	#
					//{0x0665,0x006F},	//	( ‎٥‎ → o ) ARABIC-INDIC DIGIT FIVE → LATIN SMALL LETTER O	#	// users of Arabic script are unlikely to appreciate conflating these two
					//{0x06F5,0x006F},	//	( ۵ → o ) EXTENDED ARABIC-INDIC DIGIT FIVE → LATIN SMALL LETTER O	# →‎٥‎→	// users of Arabic script are unlikely to appreciate conflating these two
					{0x2134,0x006F},	//	( ℴ → o ) SCRIPT SMALL O → LATIN SMALL LETTER O	#
					{0x1D428,0x006F},	//	( 𝐨 → o ) MATHEMATICAL BOLD SMALL O → LATIN SMALL LETTER O	#
					{0x1D45C,0x006F},	//	( 𝑜 → o ) MATHEMATICAL ITALIC SMALL O → LATIN SMALL LETTER O	#
					{0x1D490,0x006F},	//	( 𝒐 → o ) MATHEMATICAL BOLD ITALIC SMALL O → LATIN SMALL LETTER O	#
					{0x1D4F8,0x006F},	//	( 𝓸 → o ) MATHEMATICAL BOLD SCRIPT SMALL O → LATIN SMALL LETTER O	#
					{0x1D52C,0x006F},	//	( 𝔬 → o ) MATHEMATICAL FRAKTUR SMALL O → LATIN SMALL LETTER O	#
					{0x1D560,0x006F},	//	( 𝕠 → o ) MATHEMATICAL DOUBLE-STRUCK SMALL O → LATIN SMALL LETTER O	#
					{0x1D594,0x006F},	//	( 𝖔 → o ) MATHEMATICAL BOLD FRAKTUR SMALL O → LATIN SMALL LETTER O	#
					{0x1D5C8,0x006F},	//	( 𝗈 → o ) MATHEMATICAL SANS-SERIF SMALL O → LATIN SMALL LETTER O	#
					{0x1D5FC,0x006F},	//	( 𝗼 → o ) MATHEMATICAL SANS-SERIF BOLD SMALL O → LATIN SMALL LETTER O	#
					{0x1D630,0x006F},	//	( 𝘰 → o ) MATHEMATICAL SANS-SERIF ITALIC SMALL O → LATIN SMALL LETTER O	#
					{0x1D664,0x006F},	//	( 𝙤 → o ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL O → LATIN SMALL LETTER O	#
					{0x1D698,0x006F},	//	( 𝚘 → o ) MATHEMATICAL MONOSPACE SMALL O → LATIN SMALL LETTER O	#
					{0x1D0F,0x006F},	//	( ᴏ → o ) LATIN LETTER SMALL CAPITAL O → LATIN SMALL LETTER O	#
					{0x1D11,0x006F},	//	( ᴑ → o ) LATIN SMALL LETTER SIDEWAYS O → LATIN SMALL LETTER O	#
					{0xAB3D,0x006F},	//	( ꬽ → o ) LATIN SMALL LETTER BLACKLETTER O → LATIN SMALL LETTER O	#
					{0x03BF,0x006F},	//	( ο → o ) GREEK SMALL LETTER OMICRON → LATIN SMALL LETTER O	#
					{0x1D6D0,0x006F},	//	( 𝛐 → o ) MATHEMATICAL BOLD SMALL OMICRON → LATIN SMALL LETTER O	# →𝐨→
					{0x1D70A,0x006F},	//	( 𝜊 → o ) MATHEMATICAL ITALIC SMALL OMICRON → LATIN SMALL LETTER O	# →𝑜→
					{0x1D744,0x006F},	//	( 𝝄 → o ) MATHEMATICAL BOLD ITALIC SMALL OMICRON → LATIN SMALL LETTER O	# →𝒐→
					{0x1D77E,0x006F},	//	( 𝝾 → o ) MATHEMATICAL SANS-SERIF BOLD SMALL OMICRON → LATIN SMALL LETTER O	# →ο→
					{0x1D7B8,0x006F},	//	( 𝞸 → o ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL OMICRON → LATIN SMALL LETTER O	# →ο→
					{0x03C3,0x006F},	//	( σ → o ) GREEK SMALL LETTER SIGMA → LATIN SMALL LETTER O	#	// breaks case-insensitive fuzzy matching to capital sigma, alas
					{0x1D6D4,0x006F},	//	( 𝛔 → o ) MATHEMATICAL BOLD SMALL SIGMA → LATIN SMALL LETTER O	# →σ→
					{0x1D70E,0x006F},	//	( 𝜎 → o ) MATHEMATICAL ITALIC SMALL SIGMA → LATIN SMALL LETTER O	# →σ→
					{0x1D748,0x006F},	//	( 𝝈 → o ) MATHEMATICAL BOLD ITALIC SMALL SIGMA → LATIN SMALL LETTER O	# →σ→
					{0x1D782,0x006F},	//	( 𝞂 → o ) MATHEMATICAL SANS-SERIF BOLD SMALL SIGMA → LATIN SMALL LETTER O	# →σ→
					{0x1D7BC,0x006F},	//	( 𝞼 → o ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL SIGMA → LATIN SMALL LETTER O	# →σ→
					{0x2C9F,0x006F},	//	( ⲟ → o ) COPTIC SMALL LETTER O → LATIN SMALL LETTER O	#
					{0x043E,0x006F},	//	( о → o ) CYRILLIC SMALL LETTER O → LATIN SMALL LETTER O	#
					{0x10FF,0x006F},	//	( ჿ → o ) GEORGIAN LETTER LABIAL SIGN → LATIN SMALL LETTER O	#
					{0x0585,0x006F},	//	( օ → o ) ARMENIAN SMALL LETTER OH → LATIN SMALL LETTER O	#
					{0x05E1,0x006F},	//	( ‎ס‎ → o ) HEBREW LETTER SAMEKH → LATIN SMALL LETTER O	#
					{0x0647,0x006F},	//	( ‎ه‎ → o ) ARABIC LETTER HEH → LATIN SMALL LETTER O	#
					{0x1EE24,0x006F},	//	( ‎𞸤‎ → o ) ARABIC MATHEMATICAL INITIAL HEH → LATIN SMALL LETTER O	# →‎ه‎→
					{0x1EE64,0x006F},	//	( ‎𞹤‎ → o ) ARABIC MATHEMATICAL STRETCHED HEH → LATIN SMALL LETTER O	# →‎ه‎→
					{0x1EE84,0x006F},	//	( ‎𞺄‎ → o ) ARABIC MATHEMATICAL LOOPED HEH → LATIN SMALL LETTER O	# →‎ه‎→
					{0xFEEB,0x006F},	//	( ‎ﻫ‎ → o ) ARABIC LETTER HEH INITIAL FORM → LATIN SMALL LETTER O	# →‎ه‎→
					{0xFEEC,0x006F},	//	( ‎ﻬ‎ → o ) ARABIC LETTER HEH MEDIAL FORM → LATIN SMALL LETTER O	# →‎ه‎→
					{0xFEEA,0x006F},	//	( ‎ﻪ‎ → o ) ARABIC LETTER HEH FINAL FORM → LATIN SMALL LETTER O	# →‎ه‎→
					{0xFEE9,0x006F},	//	( ‎ﻩ‎ → o ) ARABIC LETTER HEH ISOLATED FORM → LATIN SMALL LETTER O	# →‎ه‎→
					{0x06BE,0x006F},	//	( ‎ھ‎ → o ) ARABIC LETTER HEH DOACHASHMEE → LATIN SMALL LETTER O	# →‎ه‎→
					{0xFBAC,0x006F},	//	( ‎ﮬ‎ → o ) ARABIC LETTER HEH DOACHASHMEE INITIAL FORM → LATIN SMALL LETTER O	# →‎ﻫ‎→→‎ه‎→
					{0xFBAD,0x006F},	//	( ‎ﮭ‎ → o ) ARABIC LETTER HEH DOACHASHMEE MEDIAL FORM → LATIN SMALL LETTER O	# →‎ﻬ‎→→‎ه‎→
					{0xFBAB,0x006F},	//	( ‎ﮫ‎ → o ) ARABIC LETTER HEH DOACHASHMEE FINAL FORM → LATIN SMALL LETTER O	# →‎ﻪ‎→→‎ه‎→
					{0xFBAA,0x006F},	//	( ‎ﮪ‎ → o ) ARABIC LETTER HEH DOACHASHMEE ISOLATED FORM → LATIN SMALL LETTER O	# →‎ه‎→
					{0x06C1,0x006F},	//	( ‎ہ‎ → o ) ARABIC LETTER HEH GOAL → LATIN SMALL LETTER O	# →‎ه‎→
					{0xFBA8,0x006F},	//	( ‎ﮨ‎ → o ) ARABIC LETTER HEH GOAL INITIAL FORM → LATIN SMALL LETTER O	# →‎ہ‎→→‎ه‎→
					{0xFBA9,0x006F},	//	( ‎ﮩ‎ → o ) ARABIC LETTER HEH GOAL MEDIAL FORM → LATIN SMALL LETTER O	# →‎ہ‎→→‎ه‎→
					{0xFBA7,0x006F},	//	( ‎ﮧ‎ → o ) ARABIC LETTER HEH GOAL FINAL FORM → LATIN SMALL LETTER O	# →‎ہ‎→→‎ه‎→
					{0xFBA6,0x006F},	//	( ‎ﮦ‎ → o ) ARABIC LETTER HEH GOAL ISOLATED FORM → LATIN SMALL LETTER O	# →‎ه‎→
					{0x06D5,0x006F},	//	( ‎ە‎ → o ) ARABIC LETTER AE → LATIN SMALL LETTER O	# →‎ه‎→
					{0x0D20,0x006F},	//	( ഠ → o ) MALAYALAM LETTER TTHA → LATIN SMALL LETTER O	#
					{0x101D,0x006F},	//	( ဝ → o ) MYANMAR LETTER WA → LATIN SMALL LETTER O	#
					{0x104EA,0x006F},	//	( 𐓪 → o ) OSAGE SMALL LETTER O → LATIN SMALL LETTER O	#
					{0x118C8,0x006F},	//	( 𑣈 → o ) WARANG CITI SMALL LETTER E → LATIN SMALL LETTER O	#
					{0x118D7,0x006F},	//	( 𑣗 → o ) WARANG CITI SMALL LETTER BU → LATIN SMALL LETTER O	#
					{0x1042C,0x006F},	//	( 𐐬 → o ) DESERET SMALL LETTER LONG O → LATIN SMALL LETTER O	#
					{0x07C0,0x004F},	//	( ‎߀‎ → O ) NKO DIGIT ZERO → LATIN CAPITAL LETTER O	# →0→
					{0x09E6,0x004F},	//	( ০ → O ) BENGALI DIGIT ZERO → LATIN CAPITAL LETTER O	# →0→
					{0x0B66,0x004F},	//	( ୦ → O ) ORIYA DIGIT ZERO → LATIN CAPITAL LETTER O	# →0→
					{0x3007,0x004F},	//	( 〇 → O ) IDEOGRAPHIC NUMBER ZERO → LATIN CAPITAL LETTER O	#
					{0x114D0,0x004F},	//	( 𑓐 → O ) TIRHUTA DIGIT ZERO → LATIN CAPITAL LETTER O	# →০→→0→
					// Some mappings were changed from LATIN CAPITAL LETTER O to DIGIT ZERO for a better match.
					// It would be best if all of these could fuzzy-match either "O" or "0", but that would mean "0" would fuzzy-match "o" in all contexts,
					// and that would be probably too surprising to users.
					{0x118E0,0x0030},	//	( 𑣠 → 0 ) WARANG CITI DIGIT ZERO → DIGIT ZERO	#
					{0x1D7CE,0x0030},	//	( 𝟎 → 0 ) MATHEMATICAL BOLD DIGIT ZERO → DIGIT ZERO	#
					{0x1D7D8,0x0030},	//	( 𝟘 → 0 ) MATHEMATICAL DOUBLE-STRUCK DIGIT ZERO → DIGIT ZERO	#
					{0x1D7E2,0x0030},	//	( 𝟢 → 0 ) MATHEMATICAL SANS-SERIF DIGIT ZERO → DIGIT ZERO	#
					{0x1D7EC,0x0030},	//	( 𝟬 → 0 ) MATHEMATICAL SANS-SERIF BOLD DIGIT ZERO → DIGIT ZERO	#
					{0x1D7F6,0x0030},	//	( 𝟶 → 0 ) MATHEMATICAL MONOSPACE DIGIT ZERO → DIGIT ZERO	#
					{0x1FBF0,0x0030},	//	( 🯰 → 0 ) SEGMENTED DIGIT ZERO → DIGIT ZERO	#
					{0x1D40E,0x004F},	//	( 𝐎 → O ) MATHEMATICAL BOLD CAPITAL O → LATIN CAPITAL LETTER O	#
					{0x1D442,0x004F},	//	( 𝑂 → O ) MATHEMATICAL ITALIC CAPITAL O → LATIN CAPITAL LETTER O	#
					{0x1D476,0x004F},	//	( 𝑶 → O ) MATHEMATICAL BOLD ITALIC CAPITAL O → LATIN CAPITAL LETTER O	#
					{0x1D4AA,0x004F},	//	( 𝒪 → O ) MATHEMATICAL SCRIPT CAPITAL O → LATIN CAPITAL LETTER O	#
					{0x1D4DE,0x004F},	//	( 𝓞 → O ) MATHEMATICAL BOLD SCRIPT CAPITAL O → LATIN CAPITAL LETTER O	#
					{0x1D512,0x004F},	//	( 𝔒 → O ) MATHEMATICAL FRAKTUR CAPITAL O → LATIN CAPITAL LETTER O	#
					{0x1D546,0x004F},	//	( 𝕆 → O ) MATHEMATICAL DOUBLE-STRUCK CAPITAL O → LATIN CAPITAL LETTER O	#
					{0x1D57A,0x004F},	//	( 𝕺 → O ) MATHEMATICAL BOLD FRAKTUR CAPITAL O → LATIN CAPITAL LETTER O	#
					{0x1D5AE,0x004F},	//	( 𝖮 → O ) MATHEMATICAL SANS-SERIF CAPITAL O → LATIN CAPITAL LETTER O	#
					{0x1D5E2,0x004F},	//	( 𝗢 → O ) MATHEMATICAL SANS-SERIF BOLD CAPITAL O → LATIN CAPITAL LETTER O	#
					{0x1D616,0x004F},	//	( 𝘖 → O ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL O → LATIN CAPITAL LETTER O	#
					{0x1D64A,0x004F},	//	( 𝙊 → O ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL O → LATIN CAPITAL LETTER O	#
					{0x1D67E,0x004F},	//	( 𝙾 → O ) MATHEMATICAL MONOSPACE CAPITAL O → LATIN CAPITAL LETTER O	#
					{0x039F,0x004F},	//	( Ο → O ) GREEK CAPITAL LETTER OMICRON → LATIN CAPITAL LETTER O	#
					{0x1D6B6,0x004F},	//	( 𝚶 → O ) MATHEMATICAL BOLD CAPITAL OMICRON → LATIN CAPITAL LETTER O	# →𝐎→
					{0x1D6F0,0x004F},	//	( 𝛰 → O ) MATHEMATICAL ITALIC CAPITAL OMICRON → LATIN CAPITAL LETTER O	# →𝑂→
					{0x1D72A,0x004F},	//	( 𝜪 → O ) MATHEMATICAL BOLD ITALIC CAPITAL OMICRON → LATIN CAPITAL LETTER O	# →𝑶→
					{0x1D764,0x004F},	//	( 𝝤 → O ) MATHEMATICAL SANS-SERIF BOLD CAPITAL OMICRON → LATIN CAPITAL LETTER O	# →Ο→
					{0x1D79E,0x004F},	//	( 𝞞 → O ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL OMICRON → LATIN CAPITAL LETTER O	# →Ο→
					{0x2C9E,0x004F},	//	( Ⲟ → O ) COPTIC CAPITAL LETTER O → LATIN CAPITAL LETTER O	#
					{0x041E,0x004F},	//	( О → O ) CYRILLIC CAPITAL LETTER O → LATIN CAPITAL LETTER O	#
					{0x0555,0x004F},	//	( Օ → O ) ARMENIAN CAPITAL LETTER OH → LATIN CAPITAL LETTER O	#
					{0x2D54,0x004F},	//	( ⵔ → O ) TIFINAGH LETTER YAR → LATIN CAPITAL LETTER O	#
					{0x12D0,0x004F},	//	( ዐ → O ) ETHIOPIC SYLLABLE PHARYNGEAL A → LATIN CAPITAL LETTER O	# →Օ→
					{0x0B20,0x004F},	//	( ଠ → O ) ORIYA LETTER TTHA → LATIN CAPITAL LETTER O	# →୦→→0→
					{0x104C2,0x004F},	//	( 𐓂 → O ) OSAGE CAPITAL LETTER O → LATIN CAPITAL LETTER O	#
					{0xA4F3,0x004F},	//	( ꓳ → O ) LISU LETTER O → LATIN CAPITAL LETTER O	#
					{0x118B5,0x004F},	//	( 𑢵 → O ) WARANG CITI CAPITAL LETTER AT → LATIN CAPITAL LETTER O	#
					{0x10292,0x004F},	//	( 𐊒 → O ) LYCIAN LETTER U → LATIN CAPITAL LETTER O	#
					{0x102AB,0x004F},	//	( 𐊫 → O ) CARIAN LETTER O → LATIN CAPITAL LETTER O	#
					{0x10404,0x004F},	//	( 𐐄 → O ) DESERET CAPITAL LETTER LONG O → LATIN CAPITAL LETTER O	#
					{0x10516,0x004F},	//	( 𐔖 → O ) ELBASAN LETTER O → LATIN CAPITAL LETTER O	#
					// MASCULINE ORDINAL INDICATOR is now mapped to DEGREE SIGN (see map1).
					// Former mappings to MASCULINE ORDINAL INDICATOR have been changed accordingly.
					{0x2070,0x00B0},	//	( ⁰ → ° ) SUPERSCRIPT ZERO → DEGREE SIGN	#
					{0x1D52,0x00B0},	//	( ᵒ → ° ) MODIFIER LETTER SMALL O → DEGREE SIGN	# →⁰→
					{0x0150,0x00D6},	//	( Ő → Ö ) LATIN CAPITAL LETTER O WITH DOUBLE ACUTE → LATIN CAPITAL LETTER O WITH DIAERESIS	#
					{0x2374,0x0070},	//	( ⍴ → p ) APL FUNCTIONAL SYMBOL RHO → LATIN SMALL LETTER P	# →ρ→
					{0x1D429,0x0070},	//	( 𝐩 → p ) MATHEMATICAL BOLD SMALL P → LATIN SMALL LETTER P	#
					{0x1D45D,0x0070},	//	( 𝑝 → p ) MATHEMATICAL ITALIC SMALL P → LATIN SMALL LETTER P	#
					{0x1D491,0x0070},	//	( 𝒑 → p ) MATHEMATICAL BOLD ITALIC SMALL P → LATIN SMALL LETTER P	#
					{0x1D4C5,0x0070},	//	( 𝓅 → p ) MATHEMATICAL SCRIPT SMALL P → LATIN SMALL LETTER P	#
					{0x1D4F9,0x0070},	//	( 𝓹 → p ) MATHEMATICAL BOLD SCRIPT SMALL P → LATIN SMALL LETTER P	#
					{0x1D52D,0x0070},	//	( 𝔭 → p ) MATHEMATICAL FRAKTUR SMALL P → LATIN SMALL LETTER P	#
					{0x1D561,0x0070},	//	( 𝕡 → p ) MATHEMATICAL DOUBLE-STRUCK SMALL P → LATIN SMALL LETTER P	#
					{0x1D595,0x0070},	//	( 𝖕 → p ) MATHEMATICAL BOLD FRAKTUR SMALL P → LATIN SMALL LETTER P	#
					{0x1D5C9,0x0070},	//	( 𝗉 → p ) MATHEMATICAL SANS-SERIF SMALL P → LATIN SMALL LETTER P	#
					{0x1D5FD,0x0070},	//	( 𝗽 → p ) MATHEMATICAL SANS-SERIF BOLD SMALL P → LATIN SMALL LETTER P	#
					{0x1D631,0x0070},	//	( 𝘱 → p ) MATHEMATICAL SANS-SERIF ITALIC SMALL P → LATIN SMALL LETTER P	#
					{0x1D665,0x0070},	//	( 𝙥 → p ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL P → LATIN SMALL LETTER P	#
					{0x1D699,0x0070},	//	( 𝚙 → p ) MATHEMATICAL MONOSPACE SMALL P → LATIN SMALL LETTER P	#
					{0x03C1,0x0070},	//	( ρ → p ) GREEK SMALL LETTER RHO → LATIN SMALL LETTER P	#
					{0x03F1,0x0070},	//	( ϱ → p ) GREEK RHO SYMBOL → LATIN SMALL LETTER P	# →ρ→
					{0x1D6D2,0x0070},	//	( 𝛒 → p ) MATHEMATICAL BOLD SMALL RHO → LATIN SMALL LETTER P	# →ρ→
					{0x1D6E0,0x0070},	//	( 𝛠 → p ) MATHEMATICAL BOLD RHO SYMBOL → LATIN SMALL LETTER P	# →ρ→
					{0x1D70C,0x0070},	//	( 𝜌 → p ) MATHEMATICAL ITALIC SMALL RHO → LATIN SMALL LETTER P	# →ρ→
					{0x1D71A,0x0070},	//	( 𝜚 → p ) MATHEMATICAL ITALIC RHO SYMBOL → LATIN SMALL LETTER P	# →ρ→
					{0x1D746,0x0070},	//	( 𝝆 → p ) MATHEMATICAL BOLD ITALIC SMALL RHO → LATIN SMALL LETTER P	# →ρ→
					{0x1D754,0x0070},	//	( 𝝔 → p ) MATHEMATICAL BOLD ITALIC RHO SYMBOL → LATIN SMALL LETTER P	# →ρ→
					{0x1D780,0x0070},	//	( 𝞀 → p ) MATHEMATICAL SANS-SERIF BOLD SMALL RHO → LATIN SMALL LETTER P	# →ρ→
					{0x1D78E,0x0070},	//	( 𝞎 → p ) MATHEMATICAL SANS-SERIF BOLD RHO SYMBOL → LATIN SMALL LETTER P	# →ρ→
					{0x1D7BA,0x0070},	//	( 𝞺 → p ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL RHO → LATIN SMALL LETTER P	# →ρ→
					{0x1D7C8,0x0070},	//	( 𝟈 → p ) MATHEMATICAL SANS-SERIF BOLD ITALIC RHO SYMBOL → LATIN SMALL LETTER P	# →ρ→
					{0x2CA3,0x0070},	//	( ⲣ → p ) COPTIC SMALL LETTER RO → LATIN SMALL LETTER P	# →ρ→
					{0x0440,0x0070},	//	( р → p ) CYRILLIC SMALL LETTER ER → LATIN SMALL LETTER P	#
					{0x2119,0x0050},	//	( ℙ → P ) DOUBLE-STRUCK CAPITAL P → LATIN CAPITAL LETTER P	#
					{0x1D40F,0x0050},	//	( 𝐏 → P ) MATHEMATICAL BOLD CAPITAL P → LATIN CAPITAL LETTER P	#
					{0x1D443,0x0050},	//	( 𝑃 → P ) MATHEMATICAL ITALIC CAPITAL P → LATIN CAPITAL LETTER P	#
					{0x1D477,0x0050},	//	( 𝑷 → P ) MATHEMATICAL BOLD ITALIC CAPITAL P → LATIN CAPITAL LETTER P	#
					{0x1D4AB,0x0050},	//	( 𝒫 → P ) MATHEMATICAL SCRIPT CAPITAL P → LATIN CAPITAL LETTER P	#
					{0x1D4DF,0x0050},	//	( 𝓟 → P ) MATHEMATICAL BOLD SCRIPT CAPITAL P → LATIN CAPITAL LETTER P	#
					{0x1D513,0x0050},	//	( 𝔓 → P ) MATHEMATICAL FRAKTUR CAPITAL P → LATIN CAPITAL LETTER P	#
					{0x1D57B,0x0050},	//	( 𝕻 → P ) MATHEMATICAL BOLD FRAKTUR CAPITAL P → LATIN CAPITAL LETTER P	#
					{0x1D5AF,0x0050},	//	( 𝖯 → P ) MATHEMATICAL SANS-SERIF CAPITAL P → LATIN CAPITAL LETTER P	#
					{0x1D5E3,0x0050},	//	( 𝗣 → P ) MATHEMATICAL SANS-SERIF BOLD CAPITAL P → LATIN CAPITAL LETTER P	#
					{0x1D617,0x0050},	//	( 𝘗 → P ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL P → LATIN CAPITAL LETTER P	#
					{0x1D64B,0x0050},	//	( 𝙋 → P ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL P → LATIN CAPITAL LETTER P	#
					{0x1D67F,0x0050},	//	( 𝙿 → P ) MATHEMATICAL MONOSPACE CAPITAL P → LATIN CAPITAL LETTER P	#
					{0x03A1,0x0050},	//	( Ρ → P ) GREEK CAPITAL LETTER RHO → LATIN CAPITAL LETTER P	#
					{0x1D6B8,0x0050},	//	( 𝚸 → P ) MATHEMATICAL BOLD CAPITAL RHO → LATIN CAPITAL LETTER P	# →𝐏→
					{0x1D6F2,0x0050},	//	( 𝛲 → P ) MATHEMATICAL ITALIC CAPITAL RHO → LATIN CAPITAL LETTER P	# →Ρ→
					{0x1D72C,0x0050},	//	( 𝜬 → P ) MATHEMATICAL BOLD ITALIC CAPITAL RHO → LATIN CAPITAL LETTER P	# →Ρ→
					{0x1D766,0x0050},	//	( 𝝦 → P ) MATHEMATICAL SANS-SERIF BOLD CAPITAL RHO → LATIN CAPITAL LETTER P	# →Ρ→
					{0x1D7A0,0x0050},	//	( 𝞠 → P ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL RHO → LATIN CAPITAL LETTER P	# →Ρ→
					{0x2CA2,0x0050},	//	( Ⲣ → P ) COPTIC CAPITAL LETTER RO → LATIN CAPITAL LETTER P	#
					{0x0420,0x0050},	//	( Р → P ) CYRILLIC CAPITAL LETTER ER → LATIN CAPITAL LETTER P	#
					{0x13E2,0x0050},	//	( Ꮲ → P ) CHEROKEE LETTER TLV → LATIN CAPITAL LETTER P	#
					{0x146D,0x0050},	//	( ᑭ → P ) CANADIAN SYLLABICS KI → LATIN CAPITAL LETTER P	#
					{0xA4D1,0x0050},	//	( ꓑ → P ) LISU LETTER PA → LATIN CAPITAL LETTER P	#
					{0x10295,0x0050},	//	( 𐊕 → P ) LYCIAN LETTER R → LATIN CAPITAL LETTER P	#
					{0x1D42A,0x0071},	//	( 𝐪 → q ) MATHEMATICAL BOLD SMALL Q → LATIN SMALL LETTER Q	#
					{0x1D45E,0x0071},	//	( 𝑞 → q ) MATHEMATICAL ITALIC SMALL Q → LATIN SMALL LETTER Q	#
					{0x1D492,0x0071},	//	( 𝒒 → q ) MATHEMATICAL BOLD ITALIC SMALL Q → LATIN SMALL LETTER Q	#
					{0x1D4C6,0x0071},	//	( 𝓆 → q ) MATHEMATICAL SCRIPT SMALL Q → LATIN SMALL LETTER Q	#
					{0x1D4FA,0x0071},	//	( 𝓺 → q ) MATHEMATICAL BOLD SCRIPT SMALL Q → LATIN SMALL LETTER Q	#
					{0x1D52E,0x0071},	//	( 𝔮 → q ) MATHEMATICAL FRAKTUR SMALL Q → LATIN SMALL LETTER Q	#
					{0x1D562,0x0071},	//	( 𝕢 → q ) MATHEMATICAL DOUBLE-STRUCK SMALL Q → LATIN SMALL LETTER Q	#
					{0x1D596,0x0071},	//	( 𝖖 → q ) MATHEMATICAL BOLD FRAKTUR SMALL Q → LATIN SMALL LETTER Q	#
					{0x1D5CA,0x0071},	//	( 𝗊 → q ) MATHEMATICAL SANS-SERIF SMALL Q → LATIN SMALL LETTER Q	#
					{0x1D5FE,0x0071},	//	( 𝗾 → q ) MATHEMATICAL SANS-SERIF BOLD SMALL Q → LATIN SMALL LETTER Q	#
					{0x1D632,0x0071},	//	( 𝘲 → q ) MATHEMATICAL SANS-SERIF ITALIC SMALL Q → LATIN SMALL LETTER Q	#
					{0x1D666,0x0071},	//	( 𝙦 → q ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL Q → LATIN SMALL LETTER Q	#
					{0x1D69A,0x0071},	//	( 𝚚 → q ) MATHEMATICAL MONOSPACE SMALL Q → LATIN SMALL LETTER Q	#
					{0x051B,0x0071},	//	( ԛ → q ) CYRILLIC SMALL LETTER QA → LATIN SMALL LETTER Q	#
					{0x0563,0x0071},	//	( գ → q ) ARMENIAN SMALL LETTER GIM → LATIN SMALL LETTER Q	#
					{0x0566,0x0071},	//	( զ → q ) ARMENIAN SMALL LETTER ZA → LATIN SMALL LETTER Q	#
					{0x211A,0x0051},	//	( ℚ → Q ) DOUBLE-STRUCK CAPITAL Q → LATIN CAPITAL LETTER Q	#
					{0x1D410,0x0051},	//	( 𝐐 → Q ) MATHEMATICAL BOLD CAPITAL Q → LATIN CAPITAL LETTER Q	#
					{0x1D444,0x0051},	//	( 𝑄 → Q ) MATHEMATICAL ITALIC CAPITAL Q → LATIN CAPITAL LETTER Q	#
					{0x1D478,0x0051},	//	( 𝑸 → Q ) MATHEMATICAL BOLD ITALIC CAPITAL Q → LATIN CAPITAL LETTER Q	#
					{0x1D4AC,0x0051},	//	( 𝒬 → Q ) MATHEMATICAL SCRIPT CAPITAL Q → LATIN CAPITAL LETTER Q	#
					{0x1D4E0,0x0051},	//	( 𝓠 → Q ) MATHEMATICAL BOLD SCRIPT CAPITAL Q → LATIN CAPITAL LETTER Q	#
					{0x1D514,0x0051},	//	( 𝔔 → Q ) MATHEMATICAL FRAKTUR CAPITAL Q → LATIN CAPITAL LETTER Q	#
					{0x1D57C,0x0051},	//	( 𝕼 → Q ) MATHEMATICAL BOLD FRAKTUR CAPITAL Q → LATIN CAPITAL LETTER Q	#
					{0x1D5B0,0x0051},	//	( 𝖰 → Q ) MATHEMATICAL SANS-SERIF CAPITAL Q → LATIN CAPITAL LETTER Q	#
					{0x1D5E4,0x0051},	//	( 𝗤 → Q ) MATHEMATICAL SANS-SERIF BOLD CAPITAL Q → LATIN CAPITAL LETTER Q	#
					{0x1D618,0x0051},	//	( 𝘘 → Q ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL Q → LATIN CAPITAL LETTER Q	#
					{0x1D64C,0x0051},	//	( 𝙌 → Q ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL Q → LATIN CAPITAL LETTER Q	#
					{0x1D680,0x0051},	//	( 𝚀 → Q ) MATHEMATICAL MONOSPACE CAPITAL Q → LATIN CAPITAL LETTER Q	#
					{0x2D55,0x0051},	//	( ⵕ → Q ) TIFINAGH LETTER YARR → LATIN CAPITAL LETTER Q	#
					// It’s better to map kappa variants and kappa-like symbols to GREEK SMALL LETTER KAPPA than to LATIN SMALL LETTER KRA
					{0x1D0B,0x03BA},	//	( ᴋ → κ ) LATIN LETTER SMALL CAPITAL K → GREEK SMALL LETTER KAPPA	#
					{0x0138,0x03BA},	//	( ĸ → κ ) LATIN SMALL LETTER KRA → GREEK SMALL LETTER KAPPA	#
					{0x03F0,0x03BA},	//	( ϰ → κ ) GREEK KAPPA SYMBOL → GREEK SMALL LETTER KAPPA	#
					{0x1D6CB,0x03BA},	//	( 𝛋 → κ ) MATHEMATICAL BOLD SMALL KAPPA → GREEK SMALL LETTER KAPPA	#
					{0x1D6DE,0x03BA},	//	( 𝛞 → κ ) MATHEMATICAL BOLD KAPPA SYMBOL → GREEK SMALL LETTER KAPPA	#
					{0x1D705,0x03BA},	//	( 𝜅 → κ ) MATHEMATICAL ITALIC SMALL KAPPA → GREEK SMALL LETTER KAPPA	#
					{0x1D718,0x03BA},	//	( 𝜘 → κ ) MATHEMATICAL ITALIC KAPPA SYMBOL → GREEK SMALL LETTER KAPPA	#
					{0x1D73F,0x03BA},	//	( 𝜿 → κ ) MATHEMATICAL BOLD ITALIC SMALL KAPPA → GREEK SMALL LETTER KAPPA	#
					{0x1D752,0x03BA},	//	( 𝝒 → κ ) MATHEMATICAL BOLD ITALIC KAPPA SYMBOL → GREEK SMALL LETTER KAPPA	#
					{0x1D779,0x03BA},	//	( 𝝹 → κ ) MATHEMATICAL SANS-SERIF BOLD SMALL KAPPA → GREEK SMALL LETTER KAPPA	#
					{0x1D78C,0x03BA},	//	( 𝞌 → κ ) MATHEMATICAL SANS-SERIF BOLD KAPPA SYMBOL → GREEK SMALL LETTER KAPPA	#
					{0x1D7B3,0x03BA},	//	( 𝞳 → κ ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL KAPPA → GREEK SMALL LETTER KAPPA	#
					{0x1D7C6,0x03BA},	//	( 𝟆 → κ ) MATHEMATICAL SANS-SERIF BOLD ITALIC KAPPA SYMBOL → GREEK SMALL LETTER KAPPA	#
					{0x2C95,0x03BA},	//	( ⲕ → κ ) COPTIC SMALL LETTER KAPA → GREEK SMALL LETTER KAPPA	#
					{0x043A,0x03BA},	//	( к → κ ) CYRILLIC SMALL LETTER KA → GREEK SMALL LETTER KAPPA	#
					{0xABB6,0x03BA},	//	( ꮶ → κ ) CHEROKEE SMALL LETTER TSO → GREEK SMALL LETTER KAPPA	#
					{0x1D42B,0x0072},	//	( 𝐫 → r ) MATHEMATICAL BOLD SMALL R → LATIN SMALL LETTER R	#
					{0x1D45F,0x0072},	//	( 𝑟 → r ) MATHEMATICAL ITALIC SMALL R → LATIN SMALL LETTER R	#
					{0x1D493,0x0072},	//	( 𝒓 → r ) MATHEMATICAL BOLD ITALIC SMALL R → LATIN SMALL LETTER R	#
					{0x1D4C7,0x0072},	//	( 𝓇 → r ) MATHEMATICAL SCRIPT SMALL R → LATIN SMALL LETTER R	#
					{0x1D4FB,0x0072},	//	( 𝓻 → r ) MATHEMATICAL BOLD SCRIPT SMALL R → LATIN SMALL LETTER R	#
					{0x1D52F,0x0072},	//	( 𝔯 → r ) MATHEMATICAL FRAKTUR SMALL R → LATIN SMALL LETTER R	#
					{0x1D563,0x0072},	//	( 𝕣 → r ) MATHEMATICAL DOUBLE-STRUCK SMALL R → LATIN SMALL LETTER R	#
					{0x1D597,0x0072},	//	( 𝖗 → r ) MATHEMATICAL BOLD FRAKTUR SMALL R → LATIN SMALL LETTER R	#
					{0x1D5CB,0x0072},	//	( 𝗋 → r ) MATHEMATICAL SANS-SERIF SMALL R → LATIN SMALL LETTER R	#
					{0x1D5FF,0x0072},	//	( 𝗿 → r ) MATHEMATICAL SANS-SERIF BOLD SMALL R → LATIN SMALL LETTER R	#
					{0x1D633,0x0072},	//	( 𝘳 → r ) MATHEMATICAL SANS-SERIF ITALIC SMALL R → LATIN SMALL LETTER R	#
					{0x1D667,0x0072},	//	( 𝙧 → r ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL R → LATIN SMALL LETTER R	#
					{0x1D69B,0x0072},	//	( 𝚛 → r ) MATHEMATICAL MONOSPACE SMALL R → LATIN SMALL LETTER R	#
					{0xAB47,0x0072},	//	( ꭇ → r ) LATIN SMALL LETTER R WITHOUT HANDLE → LATIN SMALL LETTER R	#
					{0xAB48,0x0072},	//	( ꭈ → r ) LATIN SMALL LETTER DOUBLE R → LATIN SMALL LETTER R	#
					{0x1D26,0x0072},	//	( ᴦ → r ) GREEK LETTER SMALL CAPITAL GAMMA → LATIN SMALL LETTER R	# →г→
					{0x2C85,0x0072},	//	( ⲅ → r ) COPTIC SMALL LETTER GAMMA → LATIN SMALL LETTER R	# →г→
					{0x0433,0x0072},	//	( г → r ) CYRILLIC SMALL LETTER GHE → LATIN SMALL LETTER R	#
					{0xAB81,0x0072},	//	( ꮁ → r ) CHEROKEE SMALL LETTER HU → LATIN SMALL LETTER R	# →ᴦ→→г→
					{0x1D216,0x0052},	//	( 𝈖 → R ) GREEK VOCAL NOTATION SYMBOL-23 → LATIN CAPITAL LETTER R	#
					{0x211B,0x0052},	//	( ℛ → R ) SCRIPT CAPITAL R → LATIN CAPITAL LETTER R	#
					{0x211C,0x0052},	//	( ℜ → R ) BLACK-LETTER CAPITAL R → LATIN CAPITAL LETTER R	#
					{0x211D,0x0052},	//	( ℝ → R ) DOUBLE-STRUCK CAPITAL R → LATIN CAPITAL LETTER R	#
					{0x1D411,0x0052},	//	( 𝐑 → R ) MATHEMATICAL BOLD CAPITAL R → LATIN CAPITAL LETTER R	#
					{0x1D445,0x0052},	//	( 𝑅 → R ) MATHEMATICAL ITALIC CAPITAL R → LATIN CAPITAL LETTER R	#
					{0x1D479,0x0052},	//	( 𝑹 → R ) MATHEMATICAL BOLD ITALIC CAPITAL R → LATIN CAPITAL LETTER R	#
					{0x1D4E1,0x0052},	//	( 𝓡 → R ) MATHEMATICAL BOLD SCRIPT CAPITAL R → LATIN CAPITAL LETTER R	#
					{0x1D57D,0x0052},	//	( 𝕽 → R ) MATHEMATICAL BOLD FRAKTUR CAPITAL R → LATIN CAPITAL LETTER R	#
					{0x1D5B1,0x0052},	//	( 𝖱 → R ) MATHEMATICAL SANS-SERIF CAPITAL R → LATIN CAPITAL LETTER R	#
					{0x1D5E5,0x0052},	//	( 𝗥 → R ) MATHEMATICAL SANS-SERIF BOLD CAPITAL R → LATIN CAPITAL LETTER R	#
					{0x1D619,0x0052},	//	( 𝘙 → R ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL R → LATIN CAPITAL LETTER R	#
					{0x1D64D,0x0052},	//	( 𝙍 → R ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL R → LATIN CAPITAL LETTER R	#
					{0x1D681,0x0052},	//	( 𝚁 → R ) MATHEMATICAL MONOSPACE CAPITAL R → LATIN CAPITAL LETTER R	#
					{0x01A6,0x0052},	//	( Ʀ → R ) LATIN LETTER YR → LATIN CAPITAL LETTER R	#
					{0x13A1,0x0052},	//	( Ꭱ → R ) CHEROKEE LETTER E → LATIN CAPITAL LETTER R	#
					{0x13D2,0x0052},	//	( Ꮢ → R ) CHEROKEE LETTER SV → LATIN CAPITAL LETTER R	#
					{0x104B4,0x0052},	//	( 𐒴 → R ) OSAGE CAPITAL LETTER BRA → LATIN CAPITAL LETTER R	# →Ʀ→
					{0x1587,0x0052},	//	( ᖇ → R ) CANADIAN SYLLABICS TLHI → LATIN CAPITAL LETTER R	#
					{0xA4E3,0x0052},	//	( ꓣ → R ) LISU LETTER ZHA → LATIN CAPITAL LETTER R	#
					{0x16F35,0x0052},	//	( 𖼵 → R ) MIAO LETTER ZHA → LATIN CAPITAL LETTER R	#
					{0x1D42C,0x0073},	//	( 𝐬 → s ) MATHEMATICAL BOLD SMALL S → LATIN SMALL LETTER S	#
					{0x1D460,0x0073},	//	( 𝑠 → s ) MATHEMATICAL ITALIC SMALL S → LATIN SMALL LETTER S	#
					{0x1D494,0x0073},	//	( 𝒔 → s ) MATHEMATICAL BOLD ITALIC SMALL S → LATIN SMALL LETTER S	#
					{0x1D4C8,0x0073},	//	( 𝓈 → s ) MATHEMATICAL SCRIPT SMALL S → LATIN SMALL LETTER S	#
					{0x1D4FC,0x0073},	//	( 𝓼 → s ) MATHEMATICAL BOLD SCRIPT SMALL S → LATIN SMALL LETTER S	#
					{0x1D530,0x0073},	//	( 𝔰 → s ) MATHEMATICAL FRAKTUR SMALL S → LATIN SMALL LETTER S	#
					{0x1D564,0x0073},	//	( 𝕤 → s ) MATHEMATICAL DOUBLE-STRUCK SMALL S → LATIN SMALL LETTER S	#
					{0x1D598,0x0073},	//	( 𝖘 → s ) MATHEMATICAL BOLD FRAKTUR SMALL S → LATIN SMALL LETTER S	#
					{0x1D5CC,0x0073},	//	( 𝗌 → s ) MATHEMATICAL SANS-SERIF SMALL S → LATIN SMALL LETTER S	#
					{0x1D600,0x0073},	//	( 𝘀 → s ) MATHEMATICAL SANS-SERIF BOLD SMALL S → LATIN SMALL LETTER S	#
					{0x1D634,0x0073},	//	( 𝘴 → s ) MATHEMATICAL SANS-SERIF ITALIC SMALL S → LATIN SMALL LETTER S	#
					{0x1D668,0x0073},	//	( 𝙨 → s ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL S → LATIN SMALL LETTER S	#
					{0x1D69C,0x0073},	//	( 𝚜 → s ) MATHEMATICAL MONOSPACE SMALL S → LATIN SMALL LETTER S	#
					{0xA731,0x0073},	//	( ꜱ → s ) LATIN LETTER SMALL CAPITAL S → LATIN SMALL LETTER S	#
					{0x01BD,0x0073},	//	( ƽ → s ) LATIN SMALL LETTER TONE FIVE → LATIN SMALL LETTER S	#
					{0x0455,0x0073},	//	( ѕ → s ) CYRILLIC SMALL LETTER DZE → LATIN SMALL LETTER S	#
					{0xABAA,0x0073},	//	( ꮪ → s ) CHEROKEE SMALL LETTER DU → LATIN SMALL LETTER S	# →ꜱ→
					{0x118C1,0x0073},	//	( 𑣁 → s ) WARANG CITI SMALL LETTER A → LATIN SMALL LETTER S	#
					{0x10448,0x0073},	//	( 𐑈 → s ) DESERET SMALL LETTER ZHEE → LATIN SMALL LETTER S	#
					{0x1D412,0x0053},	//	( 𝐒 → S ) MATHEMATICAL BOLD CAPITAL S → LATIN CAPITAL LETTER S	#
					{0x1D446,0x0053},	//	( 𝑆 → S ) MATHEMATICAL ITALIC CAPITAL S → LATIN CAPITAL LETTER S	#
					{0x1D47A,0x0053},	//	( 𝑺 → S ) MATHEMATICAL BOLD ITALIC CAPITAL S → LATIN CAPITAL LETTER S	#
					{0x1D4AE,0x0053},	//	( 𝒮 → S ) MATHEMATICAL SCRIPT CAPITAL S → LATIN CAPITAL LETTER S	#
					{0x1D4E2,0x0053},	//	( 𝓢 → S ) MATHEMATICAL BOLD SCRIPT CAPITAL S → LATIN CAPITAL LETTER S	#
					{0x1D516,0x0053},	//	( 𝔖 → S ) MATHEMATICAL FRAKTUR CAPITAL S → LATIN CAPITAL LETTER S	#
					{0x1D54A,0x0053},	//	( 𝕊 → S ) MATHEMATICAL DOUBLE-STRUCK CAPITAL S → LATIN CAPITAL LETTER S	#
					{0x1D57E,0x0053},	//	( 𝕾 → S ) MATHEMATICAL BOLD FRAKTUR CAPITAL S → LATIN CAPITAL LETTER S	#
					{0x1D5B2,0x0053},	//	( 𝖲 → S ) MATHEMATICAL SANS-SERIF CAPITAL S → LATIN CAPITAL LETTER S	#
					{0x1D5E6,0x0053},	//	( 𝗦 → S ) MATHEMATICAL SANS-SERIF BOLD CAPITAL S → LATIN CAPITAL LETTER S	#
					{0x1D61A,0x0053},	//	( 𝘚 → S ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL S → LATIN CAPITAL LETTER S	#
					{0x1D64E,0x0053},	//	( 𝙎 → S ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL S → LATIN CAPITAL LETTER S	#
					{0x1D682,0x0053},	//	( 𝚂 → S ) MATHEMATICAL MONOSPACE CAPITAL S → LATIN CAPITAL LETTER S	#
					{0x0405,0x0053},	//	( Ѕ → S ) CYRILLIC CAPITAL LETTER DZE → LATIN CAPITAL LETTER S	#
					{0x054F,0x0053},	//	( Տ → S ) ARMENIAN CAPITAL LETTER TIWN → LATIN CAPITAL LETTER S	#
					{0x13D5,0x0053},	//	( Ꮥ → S ) CHEROKEE LETTER DE → LATIN CAPITAL LETTER S	#
					{0x13DA,0x0053},	//	( Ꮪ → S ) CHEROKEE LETTER DU → LATIN CAPITAL LETTER S	#
					{0xA4E2,0x0053},	//	( ꓢ → S ) LISU LETTER SA → LATIN CAPITAL LETTER S	#
					{0x16F3A,0x0053},	//	( 𖼺 → S ) MIAO LETTER SA → LATIN CAPITAL LETTER S	#
					{0x10296,0x0053},	//	( 𐊖 → S ) LYCIAN LETTER S → LATIN CAPITAL LETTER S	#
					{0x10420,0x0053},	//	( 𐐠 → S ) DESERET CAPITAL LETTER ZHEE → LATIN CAPITAL LETTER S	#
					// We map LATIN SMALL LETTER SHARP S to GREEK SMALL LETTER BETA (see map1), not the other way around
					{0xA7B5,0x03B2},	//	( ꞵ → β ) LATIN SMALL LETTER BETA → GREEK SMALL LETTER BETA	#
					{0x03D0,0x03B2},	//	( ϐ → β ) GREEK BETA SYMBOL → GREEK SMALL LETTER BETA	#
					{0x1D6C3,0x03B2},	//	( 𝛃 → β ) MATHEMATICAL BOLD SMALL BETA → GREEK SMALL LETTER BETA	#
					{0x1D6FD,0x03B2},	//	( 𝛽 → β ) MATHEMATICAL ITALIC SMALL BETA → GREEK SMALL LETTER BETA	#
					{0x1D737,0x03B2},	//	( 𝜷 → β ) MATHEMATICAL BOLD ITALIC SMALL BETA → GREEK SMALL LETTER BETA	#
					{0x1D771,0x03B2},	//	( 𝝱 → β ) MATHEMATICAL SANS-SERIF BOLD SMALL BETA → GREEK SMALL LETTER BETA	#
					{0x1D7AB,0x03B2},	//	( 𝞫 → β ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL BETA → GREEK SMALL LETTER BETA	#
					{0x13F0,0x03B2},	//	( Ᏸ → β ) CHEROKEE LETTER YE → GREEK SMALL LETTER BETA	#
					{0x1D42D,0x0074},	//	( 𝐭 → t ) MATHEMATICAL BOLD SMALL T → LATIN SMALL LETTER T	#
					{0x1D461,0x0074},	//	( 𝑡 → t ) MATHEMATICAL ITALIC SMALL T → LATIN SMALL LETTER T	#
					{0x1D495,0x0074},	//	( 𝒕 → t ) MATHEMATICAL BOLD ITALIC SMALL T → LATIN SMALL LETTER T	#
					{0x1D4C9,0x0074},	//	( 𝓉 → t ) MATHEMATICAL SCRIPT SMALL T → LATIN SMALL LETTER T	#
					{0x1D4FD,0x0074},	//	( 𝓽 → t ) MATHEMATICAL BOLD SCRIPT SMALL T → LATIN SMALL LETTER T	#
					{0x1D531,0x0074},	//	( 𝔱 → t ) MATHEMATICAL FRAKTUR SMALL T → LATIN SMALL LETTER T	#
					{0x1D565,0x0074},	//	( 𝕥 → t ) MATHEMATICAL DOUBLE-STRUCK SMALL T → LATIN SMALL LETTER T	#
					{0x1D599,0x0074},	//	( 𝖙 → t ) MATHEMATICAL BOLD FRAKTUR SMALL T → LATIN SMALL LETTER T	#
					{0x1D5CD,0x0074},	//	( 𝗍 → t ) MATHEMATICAL SANS-SERIF SMALL T → LATIN SMALL LETTER T	#
					{0x1D601,0x0074},	//	( 𝘁 → t ) MATHEMATICAL SANS-SERIF BOLD SMALL T → LATIN SMALL LETTER T	#
					{0x1D635,0x0074},	//	( 𝘵 → t ) MATHEMATICAL SANS-SERIF ITALIC SMALL T → LATIN SMALL LETTER T	#
					{0x1D669,0x0074},	//	( 𝙩 → t ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL T → LATIN SMALL LETTER T	#
					{0x1D69D,0x0074},	//	( 𝚝 → t ) MATHEMATICAL MONOSPACE SMALL T → LATIN SMALL LETTER T	#
					{0x22A4,0x0054},	//	( ⊤ → T ) DOWN TACK → LATIN CAPITAL LETTER T	#
					{0x27D9,0x0054},	//	( ⟙ → T ) LARGE DOWN TACK → LATIN CAPITAL LETTER T	#
					{0x1F768,0x0054},	//	( 🝨 → T ) ALCHEMICAL SYMBOL FOR CRUCIBLE-4 → LATIN CAPITAL LETTER T	#
					{0x1D413,0x0054},	//	( 𝐓 → T ) MATHEMATICAL BOLD CAPITAL T → LATIN CAPITAL LETTER T	#
					{0x1D447,0x0054},	//	( 𝑇 → T ) MATHEMATICAL ITALIC CAPITAL T → LATIN CAPITAL LETTER T	#
					{0x1D47B,0x0054},	//	( 𝑻 → T ) MATHEMATICAL BOLD ITALIC CAPITAL T → LATIN CAPITAL LETTER T	#
					{0x1D4AF,0x0054},	//	( 𝒯 → T ) MATHEMATICAL SCRIPT CAPITAL T → LATIN CAPITAL LETTER T	#
					{0x1D4E3,0x0054},	//	( 𝓣 → T ) MATHEMATICAL BOLD SCRIPT CAPITAL T → LATIN CAPITAL LETTER T	#
					{0x1D517,0x0054},	//	( 𝔗 → T ) MATHEMATICAL FRAKTUR CAPITAL T → LATIN CAPITAL LETTER T	#
					{0x1D54B,0x0054},	//	( 𝕋 → T ) MATHEMATICAL DOUBLE-STRUCK CAPITAL T → LATIN CAPITAL LETTER T	#
					{0x1D57F,0x0054},	//	( 𝕿 → T ) MATHEMATICAL BOLD FRAKTUR CAPITAL T → LATIN CAPITAL LETTER T	#
					{0x1D5B3,0x0054},	//	( 𝖳 → T ) MATHEMATICAL SANS-SERIF CAPITAL T → LATIN CAPITAL LETTER T	#
					{0x1D5E7,0x0054},	//	( 𝗧 → T ) MATHEMATICAL SANS-SERIF BOLD CAPITAL T → LATIN CAPITAL LETTER T	#
					{0x1D61B,0x0054},	//	( 𝘛 → T ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL T → LATIN CAPITAL LETTER T	#
					{0x1D64F,0x0054},	//	( 𝙏 → T ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL T → LATIN CAPITAL LETTER T	#
					{0x1D683,0x0054},	//	( 𝚃 → T ) MATHEMATICAL MONOSPACE CAPITAL T → LATIN CAPITAL LETTER T	#
					{0x03A4,0x0054},	//	( Τ → T ) GREEK CAPITAL LETTER TAU → LATIN CAPITAL LETTER T	#
					{0x1D6BB,0x0054},	//	( 𝚻 → T ) MATHEMATICAL BOLD CAPITAL TAU → LATIN CAPITAL LETTER T	# →Τ→
					{0x1D6F5,0x0054},	//	( 𝛵 → T ) MATHEMATICAL ITALIC CAPITAL TAU → LATIN CAPITAL LETTER T	# →Τ→
					{0x1D72F,0x0054},	//	( 𝜯 → T ) MATHEMATICAL BOLD ITALIC CAPITAL TAU → LATIN CAPITAL LETTER T	# →Τ→
					{0x1D769,0x0054},	//	( 𝝩 → T ) MATHEMATICAL SANS-SERIF BOLD CAPITAL TAU → LATIN CAPITAL LETTER T	# →Τ→
					{0x1D7A3,0x0054},	//	( 𝞣 → T ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL TAU → LATIN CAPITAL LETTER T	# →Τ→
					{0x2CA6,0x0054},	//	( Ⲧ → T ) COPTIC CAPITAL LETTER TAU → LATIN CAPITAL LETTER T	#
					{0x0422,0x0054},	//	( Т → T ) CYRILLIC CAPITAL LETTER TE → LATIN CAPITAL LETTER T	#
					{0x13A2,0x0054},	//	( Ꭲ → T ) CHEROKEE LETTER I → LATIN CAPITAL LETTER T	#
					{0xA4D4,0x0054},	//	( ꓔ → T ) LISU LETTER TA → LATIN CAPITAL LETTER T	#
					{0x16F0A,0x0054},	//	( 𖼊 → T ) MIAO LETTER TA → LATIN CAPITAL LETTER T	#
					{0x118BC,0x0054},	//	( 𑢼 → T ) WARANG CITI CAPITAL LETTER HAR → LATIN CAPITAL LETTER T	#
					{0x10297,0x0054},	//	( 𐊗 → T ) LYCIAN LETTER T → LATIN CAPITAL LETTER T	#
					{0x102B1,0x0054},	//	( 𐊱 → T ) CARIAN LETTER C-18 → LATIN CAPITAL LETTER T	#
					{0x10315,0x0054},	//	( 𐌕 → T ) OLD ITALIC LETTER TE → LATIN CAPITAL LETTER T	#
					{0x1D42E,0x0075},	//	( 𝐮 → u ) MATHEMATICAL BOLD SMALL U → LATIN SMALL LETTER U	#
					{0x1D462,0x0075},	//	( 𝑢 → u ) MATHEMATICAL ITALIC SMALL U → LATIN SMALL LETTER U	#
					{0x1D496,0x0075},	//	( 𝒖 → u ) MATHEMATICAL BOLD ITALIC SMALL U → LATIN SMALL LETTER U	#
					{0x1D4CA,0x0075},	//	( 𝓊 → u ) MATHEMATICAL SCRIPT SMALL U → LATIN SMALL LETTER U	#
					{0x1D4FE,0x0075},	//	( 𝓾 → u ) MATHEMATICAL BOLD SCRIPT SMALL U → LATIN SMALL LETTER U	#
					{0x1D532,0x0075},	//	( 𝔲 → u ) MATHEMATICAL FRAKTUR SMALL U → LATIN SMALL LETTER U	#
					{0x1D566,0x0075},	//	( 𝕦 → u ) MATHEMATICAL DOUBLE-STRUCK SMALL U → LATIN SMALL LETTER U	#
					{0x1D59A,0x0075},	//	( 𝖚 → u ) MATHEMATICAL BOLD FRAKTUR SMALL U → LATIN SMALL LETTER U	#
					{0x1D5CE,0x0075},	//	( 𝗎 → u ) MATHEMATICAL SANS-SERIF SMALL U → LATIN SMALL LETTER U	#
					{0x1D602,0x0075},	//	( 𝘂 → u ) MATHEMATICAL SANS-SERIF BOLD SMALL U → LATIN SMALL LETTER U	#
					{0x1D636,0x0075},	//	( 𝘶 → u ) MATHEMATICAL SANS-SERIF ITALIC SMALL U → LATIN SMALL LETTER U	#
					{0x1D66A,0x0075},	//	( 𝙪 → u ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL U → LATIN SMALL LETTER U	#
					{0x1D69E,0x0075},	//	( 𝚞 → u ) MATHEMATICAL MONOSPACE SMALL U → LATIN SMALL LETTER U	#
					{0xA79F,0x0075},	//	( ꞟ → u ) LATIN SMALL LETTER VOLAPUK UE → LATIN SMALL LETTER U	#
					{0x1D1C,0x0075},	//	( ᴜ → u ) LATIN LETTER SMALL CAPITAL U → LATIN SMALL LETTER U	#
					{0xAB4E,0x0075},	//	( ꭎ → u ) LATIN SMALL LETTER U WITH SHORT RIGHT LEG → LATIN SMALL LETTER U	#
					{0xAB52,0x0075},	//	( ꭒ → u ) LATIN SMALL LETTER U WITH LEFT HOOK → LATIN SMALL LETTER U	#
					{0x028B,0x0075},	//	( ʋ → u ) LATIN SMALL LETTER V WITH HOOK → LATIN SMALL LETTER U	#
					// Ideally, all variants of small upsilon would be fuzzy-matched by both LATIN SMALL LETTER U and GREEK SMALL LETTER UPSILON,
					// but that is not possible right now without breaking other things, alas.
					// Greek small nu and gamma have the same problem.
					{0x03C5,0x0075},	//	( υ → u ) GREEK SMALL LETTER UPSILON → LATIN SMALL LETTER U	# →ʋ→
					{0x1D6D6,0x0075},	//	( 𝛖 → u ) MATHEMATICAL BOLD SMALL UPSILON → LATIN SMALL LETTER U	# →υ→→ʋ→
					{0x1D710,0x0075},	//	( 𝜐 → u ) MATHEMATICAL ITALIC SMALL UPSILON → LATIN SMALL LETTER U	# →υ→→ʋ→
					{0x1D74A,0x0075},	//	( 𝝊 → u ) MATHEMATICAL BOLD ITALIC SMALL UPSILON → LATIN SMALL LETTER U	# →υ→→ʋ→
					{0x1D784,0x0075},	//	( 𝞄 → u ) MATHEMATICAL SANS-SERIF BOLD SMALL UPSILON → LATIN SMALL LETTER U	# →υ→→ʋ→
					{0x1D7BE,0x0075},	//	( 𝞾 → u ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL UPSILON → LATIN SMALL LETTER U	# →υ→→ʋ→
					{0x057D,0x0075},	//	( ս → u ) ARMENIAN SMALL LETTER SEH → LATIN SMALL LETTER U	#
					{0x104F6,0x0075},	//	( 𐓶 → u ) OSAGE SMALL LETTER U → LATIN SMALL LETTER U	# →ᴜ→
					{0x118D8,0x0075},	//	( 𑣘 → u ) WARANG CITI SMALL LETTER PU → LATIN SMALL LETTER U	# →υ→→ʋ→
					{0x222A,0x0055},	//	( ∪ → U ) UNION → LATIN CAPITAL LETTER U	# →ᑌ→
					{0x22C3,0x0055},	//	( ⋃ → U ) N-ARY UNION → LATIN CAPITAL LETTER U	# →∪→→ᑌ→
					{0x1D414,0x0055},	//	( 𝐔 → U ) MATHEMATICAL BOLD CAPITAL U → LATIN CAPITAL LETTER U	#
					{0x1D448,0x0055},	//	( 𝑈 → U ) MATHEMATICAL ITALIC CAPITAL U → LATIN CAPITAL LETTER U	#
					{0x1D47C,0x0055},	//	( 𝑼 → U ) MATHEMATICAL BOLD ITALIC CAPITAL U → LATIN CAPITAL LETTER U	#
					{0x1D4B0,0x0055},	//	( 𝒰 → U ) MATHEMATICAL SCRIPT CAPITAL U → LATIN CAPITAL LETTER U	#
					{0x1D4E4,0x0055},	//	( 𝓤 → U ) MATHEMATICAL BOLD SCRIPT CAPITAL U → LATIN CAPITAL LETTER U	#
					{0x1D518,0x0055},	//	( 𝔘 → U ) MATHEMATICAL FRAKTUR CAPITAL U → LATIN CAPITAL LETTER U	#
					{0x1D54C,0x0055},	//	( 𝕌 → U ) MATHEMATICAL DOUBLE-STRUCK CAPITAL U → LATIN CAPITAL LETTER U	#
					{0x1D580,0x0055},	//	( 𝖀 → U ) MATHEMATICAL BOLD FRAKTUR CAPITAL U → LATIN CAPITAL LETTER U	#
					{0x1D5B4,0x0055},	//	( 𝖴 → U ) MATHEMATICAL SANS-SERIF CAPITAL U → LATIN CAPITAL LETTER U	#
					{0x1D5E8,0x0055},	//	( 𝗨 → U ) MATHEMATICAL SANS-SERIF BOLD CAPITAL U → LATIN CAPITAL LETTER U	#
					{0x1D61C,0x0055},	//	( 𝘜 → U ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL U → LATIN CAPITAL LETTER U	#
					{0x1D650,0x0055},	//	( 𝙐 → U ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL U → LATIN CAPITAL LETTER U	#
					{0x1D684,0x0055},	//	( 𝚄 → U ) MATHEMATICAL MONOSPACE CAPITAL U → LATIN CAPITAL LETTER U	#
					{0x054D,0x0055},	//	( Ս → U ) ARMENIAN CAPITAL LETTER SEH → LATIN CAPITAL LETTER U	#
					{0x1200,0x0055},	//	( ሀ → U ) ETHIOPIC SYLLABLE HA → LATIN CAPITAL LETTER U	# →Ս→
					{0x104CE,0x0055},	//	( 𐓎 → U ) OSAGE CAPITAL LETTER U → LATIN CAPITAL LETTER U	#
					{0x144C,0x0055},	//	( ᑌ → U ) CANADIAN SYLLABICS TE → LATIN CAPITAL LETTER U	#
					{0xA4F4,0x0055},	//	( ꓴ → U ) LISU LETTER U → LATIN CAPITAL LETTER U	#
					{0x16F42,0x0055},	//	( 𖽂 → U ) MIAO LETTER WA → LATIN CAPITAL LETTER U	#
					{0x118B8,0x0055},	//	( 𑢸 → U ) WARANG CITI CAPITAL LETTER PU → LATIN CAPITAL LETTER U	#
					{0x2228,0x0076},	//	( ∨ → v ) LOGICAL OR → LATIN SMALL LETTER V	#
					{0x22C1,0x0076},	//	( ⋁ → v ) N-ARY LOGICAL OR → LATIN SMALL LETTER V	# →∨→
					{0x2174,0x0076},	//	( ⅴ → v ) SMALL ROMAN NUMERAL FIVE → LATIN SMALL LETTER V	#
					{0x1D42F,0x0076},	//	( 𝐯 → v ) MATHEMATICAL BOLD SMALL V → LATIN SMALL LETTER V	#
					{0x1D463,0x0076},	//	( 𝑣 → v ) MATHEMATICAL ITALIC SMALL V → LATIN SMALL LETTER V	#
					{0x1D497,0x0076},	//	( 𝒗 → v ) MATHEMATICAL BOLD ITALIC SMALL V → LATIN SMALL LETTER V	#
					{0x1D4CB,0x0076},	//	( 𝓋 → v ) MATHEMATICAL SCRIPT SMALL V → LATIN SMALL LETTER V	#
					{0x1D4FF,0x0076},	//	( 𝓿 → v ) MATHEMATICAL BOLD SCRIPT SMALL V → LATIN SMALL LETTER V	#
					{0x1D533,0x0076},	//	( 𝔳 → v ) MATHEMATICAL FRAKTUR SMALL V → LATIN SMALL LETTER V	#
					{0x1D567,0x0076},	//	( 𝕧 → v ) MATHEMATICAL DOUBLE-STRUCK SMALL V → LATIN SMALL LETTER V	#
					{0x1D59B,0x0076},	//	( 𝖛 → v ) MATHEMATICAL BOLD FRAKTUR SMALL V → LATIN SMALL LETTER V	#
					{0x1D5CF,0x0076},	//	( 𝗏 → v ) MATHEMATICAL SANS-SERIF SMALL V → LATIN SMALL LETTER V	#
					{0x1D603,0x0076},	//	( 𝘃 → v ) MATHEMATICAL SANS-SERIF BOLD SMALL V → LATIN SMALL LETTER V	#
					{0x1D637,0x0076},	//	( 𝘷 → v ) MATHEMATICAL SANS-SERIF ITALIC SMALL V → LATIN SMALL LETTER V	#
					{0x1D66B,0x0076},	//	( 𝙫 → v ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL V → LATIN SMALL LETTER V	#
					{0x1D69F,0x0076},	//	( 𝚟 → v ) MATHEMATICAL MONOSPACE SMALL V → LATIN SMALL LETTER V	#
					{0x1D20,0x0076},	//	( ᴠ → v ) LATIN LETTER SMALL CAPITAL V → LATIN SMALL LETTER V	#
					{0x03BD,0x0076},	//	( ν → v ) GREEK SMALL LETTER NU → LATIN SMALL LETTER V	#
					{0x1D6CE,0x0076},	//	( 𝛎 → v ) MATHEMATICAL BOLD SMALL NU → LATIN SMALL LETTER V	# →ν→
					{0x1D708,0x0076},	//	( 𝜈 → v ) MATHEMATICAL ITALIC SMALL NU → LATIN SMALL LETTER V	# →ν→
					{0x1D742,0x0076},	//	( 𝝂 → v ) MATHEMATICAL BOLD ITALIC SMALL NU → LATIN SMALL LETTER V	# →ν→
					{0x1D77C,0x0076},	//	( 𝝼 → v ) MATHEMATICAL SANS-SERIF BOLD SMALL NU → LATIN SMALL LETTER V	# →ν→
					{0x1D7B6,0x0076},	//	( 𝞶 → v ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL NU → LATIN SMALL LETTER V	# →ν→
					{0x0475,0x0076},	//	( ѵ → v ) CYRILLIC SMALL LETTER IZHITSA → LATIN SMALL LETTER V	#
					{0x05D8,0x0076},	//	( ‎ט‎ → v ) HEBREW LETTER TET → LATIN SMALL LETTER V	#
					{0x11706,0x0076},	//	( 𑜆 → v ) AHOM LETTER PA → LATIN SMALL LETTER V	#
					{0xABA9,0x0076},	//	( ꮩ → v ) CHEROKEE SMALL LETTER DO → LATIN SMALL LETTER V	# →ᴠ→
					{0x118C0,0x0076},	//	( 𑣀 → v ) WARANG CITI SMALL LETTER NGAA → LATIN SMALL LETTER V	#
					{0x1D20D,0x0056},	//	( 𝈍 → V ) GREEK VOCAL NOTATION SYMBOL-14 → LATIN CAPITAL LETTER V	#
					//{0x0667,0x0056},	//	( ‎٧‎ → V ) ARABIC-INDIC DIGIT SEVEN → LATIN CAPITAL LETTER V	#	// users of Arabic script are unlikely to appreciate conflating these two
					//{0x06F7,0x0056},	//	( ۷ → V ) EXTENDED ARABIC-INDIC DIGIT SEVEN → LATIN CAPITAL LETTER V	# →‎٧‎→	// users of Arabic script are unlikely to appreciate conflating these two
					{0x2164,0x0056},	//	( Ⅴ → V ) ROMAN NUMERAL FIVE → LATIN CAPITAL LETTER V	#
					{0x1D415,0x0056},	//	( 𝐕 → V ) MATHEMATICAL BOLD CAPITAL V → LATIN CAPITAL LETTER V	#
					{0x1D449,0x0056},	//	( 𝑉 → V ) MATHEMATICAL ITALIC CAPITAL V → LATIN CAPITAL LETTER V	#
					{0x1D47D,0x0056},	//	( 𝑽 → V ) MATHEMATICAL BOLD ITALIC CAPITAL V → LATIN CAPITAL LETTER V	#
					{0x1D4B1,0x0056},	//	( 𝒱 → V ) MATHEMATICAL SCRIPT CAPITAL V → LATIN CAPITAL LETTER V	#
					{0x1D4E5,0x0056},	//	( 𝓥 → V ) MATHEMATICAL BOLD SCRIPT CAPITAL V → LATIN CAPITAL LETTER V	#
					{0x1D519,0x0056},	//	( 𝔙 → V ) MATHEMATICAL FRAKTUR CAPITAL V → LATIN CAPITAL LETTER V	#
					{0x1D54D,0x0056},	//	( 𝕍 → V ) MATHEMATICAL DOUBLE-STRUCK CAPITAL V → LATIN CAPITAL LETTER V	#
					{0x1D581,0x0056},	//	( 𝖁 → V ) MATHEMATICAL BOLD FRAKTUR CAPITAL V → LATIN CAPITAL LETTER V	#
					{0x1D5B5,0x0056},	//	( 𝖵 → V ) MATHEMATICAL SANS-SERIF CAPITAL V → LATIN CAPITAL LETTER V	#
					{0x1D5E9,0x0056},	//	( 𝗩 → V ) MATHEMATICAL SANS-SERIF BOLD CAPITAL V → LATIN CAPITAL LETTER V	#
					{0x1D61D,0x0056},	//	( 𝘝 → V ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL V → LATIN CAPITAL LETTER V	#
					{0x1D651,0x0056},	//	( 𝙑 → V ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL V → LATIN CAPITAL LETTER V	#
					{0x1D685,0x0056},	//	( 𝚅 → V ) MATHEMATICAL MONOSPACE CAPITAL V → LATIN CAPITAL LETTER V	#
					{0x0474,0x0056},	//	( Ѵ → V ) CYRILLIC CAPITAL LETTER IZHITSA → LATIN CAPITAL LETTER V	#
					{0x2D38,0x0056},	//	( ⴸ → V ) TIFINAGH LETTER YADH → LATIN CAPITAL LETTER V	#
					{0x13D9,0x0056},	//	( Ꮩ → V ) CHEROKEE LETTER DO → LATIN CAPITAL LETTER V	#
					{0x142F,0x0056},	//	( ᐯ → V ) CANADIAN SYLLABICS PE → LATIN CAPITAL LETTER V	#
					{0xA6DF,0x0056},	//	( ꛟ → V ) BAMUM LETTER KO → LATIN CAPITAL LETTER V	#
					{0xA4E6,0x0056},	//	( ꓦ → V ) LISU LETTER HA → LATIN CAPITAL LETTER V	#
					{0x16F08,0x0056},	//	( 𖼈 → V ) MIAO LETTER VA → LATIN CAPITAL LETTER V	#
					{0x118A0,0x0056},	//	( 𑢠 → V ) WARANG CITI CAPITAL LETTER NGAA → LATIN CAPITAL LETTER V	#
					{0x1051D,0x0056},	//	( 𐔝 → V ) ELBASAN LETTER TE → LATIN CAPITAL LETTER V	#
					{0x026F,0x0077},	//	( ɯ → w ) LATIN SMALL LETTER TURNED M → LATIN SMALL LETTER W	#
					{0x1D430,0x0077},	//	( 𝐰 → w ) MATHEMATICAL BOLD SMALL W → LATIN SMALL LETTER W	#
					{0x1D464,0x0077},	//	( 𝑤 → w ) MATHEMATICAL ITALIC SMALL W → LATIN SMALL LETTER W	#
					{0x1D498,0x0077},	//	( 𝒘 → w ) MATHEMATICAL BOLD ITALIC SMALL W → LATIN SMALL LETTER W	#
					{0x1D4CC,0x0077},	//	( 𝓌 → w ) MATHEMATICAL SCRIPT SMALL W → LATIN SMALL LETTER W	#
					{0x1D500,0x0077},	//	( 𝔀 → w ) MATHEMATICAL BOLD SCRIPT SMALL W → LATIN SMALL LETTER W	#
					{0x1D534,0x0077},	//	( 𝔴 → w ) MATHEMATICAL FRAKTUR SMALL W → LATIN SMALL LETTER W	#
					{0x1D568,0x0077},	//	( 𝕨 → w ) MATHEMATICAL DOUBLE-STRUCK SMALL W → LATIN SMALL LETTER W	#
					{0x1D59C,0x0077},	//	( 𝖜 → w ) MATHEMATICAL BOLD FRAKTUR SMALL W → LATIN SMALL LETTER W	#
					{0x1D5D0,0x0077},	//	( 𝗐 → w ) MATHEMATICAL SANS-SERIF SMALL W → LATIN SMALL LETTER W	#
					{0x1D604,0x0077},	//	( 𝘄 → w ) MATHEMATICAL SANS-SERIF BOLD SMALL W → LATIN SMALL LETTER W	#
					{0x1D638,0x0077},	//	( 𝘸 → w ) MATHEMATICAL SANS-SERIF ITALIC SMALL W → LATIN SMALL LETTER W	#
					{0x1D66C,0x0077},	//	( 𝙬 → w ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL W → LATIN SMALL LETTER W	#
					{0x1D6A0,0x0077},	//	( 𝚠 → w ) MATHEMATICAL MONOSPACE SMALL W → LATIN SMALL LETTER W	#
					{0x1D21,0x0077},	//	( ᴡ → w ) LATIN LETTER SMALL CAPITAL W → LATIN SMALL LETTER W	#
					{0x0461,0x0077},	//	( ѡ → w ) CYRILLIC SMALL LETTER OMEGA → LATIN SMALL LETTER W	#
					{0x051D,0x0077},	//	( ԝ → w ) CYRILLIC SMALL LETTER WE → LATIN SMALL LETTER W	#
					{0x0561,0x0077},	//	( ա → w ) ARMENIAN SMALL LETTER AYB → LATIN SMALL LETTER W	# →ɯ→
					{0x1170A,0x0077},	//	( 𑜊 → w ) AHOM LETTER JA → LATIN SMALL LETTER W	#
					{0x1170E,0x0077},	//	( 𑜎 → w ) AHOM LETTER LA → LATIN SMALL LETTER W	#
					{0x1170F,0x0077},	//	( 𑜏 → w ) AHOM LETTER SA → LATIN SMALL LETTER W	#
					{0xAB83,0x0077},	//	( ꮃ → w ) CHEROKEE SMALL LETTER LA → LATIN SMALL LETTER W	# →ᴡ→
					{0x118EF,0x0057},	//	( 𑣯 → W ) WARANG CITI NUMBER SIXTY → LATIN CAPITAL LETTER W	#
					{0x118E6,0x0057},	//	( 𑣦 → W ) WARANG CITI DIGIT SIX → LATIN CAPITAL LETTER W	#
					{0x1D416,0x0057},	//	( 𝐖 → W ) MATHEMATICAL BOLD CAPITAL W → LATIN CAPITAL LETTER W	#
					{0x1D44A,0x0057},	//	( 𝑊 → W ) MATHEMATICAL ITALIC CAPITAL W → LATIN CAPITAL LETTER W	#
					{0x1D47E,0x0057},	//	( 𝑾 → W ) MATHEMATICAL BOLD ITALIC CAPITAL W → LATIN CAPITAL LETTER W	#
					{0x1D4B2,0x0057},	//	( 𝒲 → W ) MATHEMATICAL SCRIPT CAPITAL W → LATIN CAPITAL LETTER W	#
					{0x1D4E6,0x0057},	//	( 𝓦 → W ) MATHEMATICAL BOLD SCRIPT CAPITAL W → LATIN CAPITAL LETTER W	#
					{0x1D51A,0x0057},	//	( 𝔚 → W ) MATHEMATICAL FRAKTUR CAPITAL W → LATIN CAPITAL LETTER W	#
					{0x1D54E,0x0057},	//	( 𝕎 → W ) MATHEMATICAL DOUBLE-STRUCK CAPITAL W → LATIN CAPITAL LETTER W	#
					{0x1D582,0x0057},	//	( 𝖂 → W ) MATHEMATICAL BOLD FRAKTUR CAPITAL W → LATIN CAPITAL LETTER W	#
					{0x1D5B6,0x0057},	//	( 𝖶 → W ) MATHEMATICAL SANS-SERIF CAPITAL W → LATIN CAPITAL LETTER W	#
					{0x1D5EA,0x0057},	//	( 𝗪 → W ) MATHEMATICAL SANS-SERIF BOLD CAPITAL W → LATIN CAPITAL LETTER W	#
					{0x1D61E,0x0057},	//	( 𝘞 → W ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL W → LATIN CAPITAL LETTER W	#
					{0x1D652,0x0057},	//	( 𝙒 → W ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL W → LATIN CAPITAL LETTER W	#
					{0x1D686,0x0057},	//	( 𝚆 → W ) MATHEMATICAL MONOSPACE CAPITAL W → LATIN CAPITAL LETTER W	#
					{0x051C,0x0057},	//	( Ԝ → W ) CYRILLIC CAPITAL LETTER WE → LATIN CAPITAL LETTER W	#
					{0x13B3,0x0057},	//	( Ꮃ → W ) CHEROKEE LETTER LA → LATIN CAPITAL LETTER W	#
					{0x13D4,0x0057},	//	( Ꮤ → W ) CHEROKEE LETTER TA → LATIN CAPITAL LETTER W	#
					{0xA4EA,0x0057},	//	( ꓪ → W ) LISU LETTER WA → LATIN CAPITAL LETTER W	#
					{0x166E,0x0078},	//	( ᙮ → x ) CANADIAN SYLLABICS FULL STOP → LATIN SMALL LETTER X	#
					{0x00D7,0x0078},	//	( × → x ) MULTIPLICATION SIGN → LATIN SMALL LETTER X	#
					{0x292B,0x0078},	//	( ⤫ → x ) RISING DIAGONAL CROSSING FALLING DIAGONAL → LATIN SMALL LETTER X	#
					{0x292C,0x0078},	//	( ⤬ → x ) FALLING DIAGONAL CROSSING RISING DIAGONAL → LATIN SMALL LETTER X	#
					{0x2A2F,0x0078},	//	( ⨯ → x ) VECTOR OR CROSS PRODUCT → LATIN SMALL LETTER X	# →×→
					{0x2179,0x0078},	//	( ⅹ → x ) SMALL ROMAN NUMERAL TEN → LATIN SMALL LETTER X	#
					{0x1D431,0x0078},	//	( 𝐱 → x ) MATHEMATICAL BOLD SMALL X → LATIN SMALL LETTER X	#
					{0x1D465,0x0078},	//	( 𝑥 → x ) MATHEMATICAL ITALIC SMALL X → LATIN SMALL LETTER X	#
					{0x1D499,0x0078},	//	( 𝒙 → x ) MATHEMATICAL BOLD ITALIC SMALL X → LATIN SMALL LETTER X	#
					{0x1D4CD,0x0078},	//	( 𝓍 → x ) MATHEMATICAL SCRIPT SMALL X → LATIN SMALL LETTER X	#
					{0x1D501,0x0078},	//	( 𝔁 → x ) MATHEMATICAL BOLD SCRIPT SMALL X → LATIN SMALL LETTER X	#
					{0x1D535,0x0078},	//	( 𝔵 → x ) MATHEMATICAL FRAKTUR SMALL X → LATIN SMALL LETTER X	#
					{0x1D569,0x0078},	//	( 𝕩 → x ) MATHEMATICAL DOUBLE-STRUCK SMALL X → LATIN SMALL LETTER X	#
					{0x1D59D,0x0078},	//	( 𝖝 → x ) MATHEMATICAL BOLD FRAKTUR SMALL X → LATIN SMALL LETTER X	#
					{0x1D5D1,0x0078},	//	( 𝗑 → x ) MATHEMATICAL SANS-SERIF SMALL X → LATIN SMALL LETTER X	#
					{0x1D605,0x0078},	//	( 𝘅 → x ) MATHEMATICAL SANS-SERIF BOLD SMALL X → LATIN SMALL LETTER X	#
					{0x1D639,0x0078},	//	( 𝘹 → x ) MATHEMATICAL SANS-SERIF ITALIC SMALL X → LATIN SMALL LETTER X	#
					{0x1D66D,0x0078},	//	( 𝙭 → x ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL X → LATIN SMALL LETTER X	#
					{0x1D6A1,0x0078},	//	( 𝚡 → x ) MATHEMATICAL MONOSPACE SMALL X → LATIN SMALL LETTER X	#
					{0x0445,0x0078},	//	( х → x ) CYRILLIC SMALL LETTER HA → LATIN SMALL LETTER X	#
					{0x1541,0x0078},	//	( ᕁ → x ) CANADIAN SYLLABICS SAYISI YI → LATIN SMALL LETTER X	# →᙮→
					{0x157D,0x0078},	//	( ᕽ → x ) CANADIAN SYLLABICS HK → LATIN SMALL LETTER X	# →ᕁ→→᙮→
					{0x166D,0x0058},	//	( ᙭ → X ) CANADIAN SYLLABICS CHI SIGN → LATIN CAPITAL LETTER X	#
					{0x2573,0x0058},	//	( ╳ → X ) BOX DRAWINGS LIGHT DIAGONAL CROSS → LATIN CAPITAL LETTER X	#
					{0x10322,0x0058},	//	( 𐌢 → X ) OLD ITALIC NUMERAL TEN → LATIN CAPITAL LETTER X	# →𐌗→
					{0x118EC,0x0058},	//	( 𑣬 → X ) WARANG CITI NUMBER THIRTY → LATIN CAPITAL LETTER X	#
					{0x2169,0x0058},	//	( Ⅹ → X ) ROMAN NUMERAL TEN → LATIN CAPITAL LETTER X	#
					{0x1D417,0x0058},	//	( 𝐗 → X ) MATHEMATICAL BOLD CAPITAL X → LATIN CAPITAL LETTER X	#
					{0x1D44B,0x0058},	//	( 𝑋 → X ) MATHEMATICAL ITALIC CAPITAL X → LATIN CAPITAL LETTER X	#
					{0x1D47F,0x0058},	//	( 𝑿 → X ) MATHEMATICAL BOLD ITALIC CAPITAL X → LATIN CAPITAL LETTER X	#
					{0x1D4B3,0x0058},	//	( 𝒳 → X ) MATHEMATICAL SCRIPT CAPITAL X → LATIN CAPITAL LETTER X	#
					{0x1D4E7,0x0058},	//	( 𝓧 → X ) MATHEMATICAL BOLD SCRIPT CAPITAL X → LATIN CAPITAL LETTER X	#
					{0x1D51B,0x0058},	//	( 𝔛 → X ) MATHEMATICAL FRAKTUR CAPITAL X → LATIN CAPITAL LETTER X	#
					{0x1D54F,0x0058},	//	( 𝕏 → X ) MATHEMATICAL DOUBLE-STRUCK CAPITAL X → LATIN CAPITAL LETTER X	#
					{0x1D583,0x0058},	//	( 𝖃 → X ) MATHEMATICAL BOLD FRAKTUR CAPITAL X → LATIN CAPITAL LETTER X	#
					{0x1D5B7,0x0058},	//	( 𝖷 → X ) MATHEMATICAL SANS-SERIF CAPITAL X → LATIN CAPITAL LETTER X	#
					{0x1D5EB,0x0058},	//	( 𝗫 → X ) MATHEMATICAL SANS-SERIF BOLD CAPITAL X → LATIN CAPITAL LETTER X	#
					{0x1D61F,0x0058},	//	( 𝘟 → X ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL X → LATIN CAPITAL LETTER X	#
					{0x1D653,0x0058},	//	( 𝙓 → X ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL X → LATIN CAPITAL LETTER X	#
					{0x1D687,0x0058},	//	( 𝚇 → X ) MATHEMATICAL MONOSPACE CAPITAL X → LATIN CAPITAL LETTER X	#
					{0xA7B3,0x0058},	//	( Ꭓ → X ) LATIN CAPITAL LETTER CHI → LATIN CAPITAL LETTER X	#
					{0x03A7,0x0058},	//	( Χ → X ) GREEK CAPITAL LETTER CHI → LATIN CAPITAL LETTER X	#
					{0x1D6BE,0x0058},	//	( 𝚾 → X ) MATHEMATICAL BOLD CAPITAL CHI → LATIN CAPITAL LETTER X	# →Χ→
					{0x1D6F8,0x0058},	//	( 𝛸 → X ) MATHEMATICAL ITALIC CAPITAL CHI → LATIN CAPITAL LETTER X	# →Χ→
					{0x1D732,0x0058},	//	( 𝜲 → X ) MATHEMATICAL BOLD ITALIC CAPITAL CHI → LATIN CAPITAL LETTER X	# →𝑿→
					{0x1D76C,0x0058},	//	( 𝝬 → X ) MATHEMATICAL SANS-SERIF BOLD CAPITAL CHI → LATIN CAPITAL LETTER X	# →Χ→
					{0x1D7A6,0x0058},	//	( 𝞦 → X ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL CHI → LATIN CAPITAL LETTER X	# →Χ→
					{0x2CAC,0x0058},	//	( Ⲭ → X ) COPTIC CAPITAL LETTER KHI → LATIN CAPITAL LETTER X	# →Х→
					{0x0425,0x0058},	//	( Х → X ) CYRILLIC CAPITAL LETTER HA → LATIN CAPITAL LETTER X	#
					{0x2D5D,0x0058},	//	( ⵝ → X ) TIFINAGH LETTER YATH → LATIN CAPITAL LETTER X	#
					{0x16B7,0x0058},	//	( ᚷ → X ) RUNIC LETTER GEBO GYFU G → LATIN CAPITAL LETTER X	#
					{0xA4EB,0x0058},	//	( ꓫ → X ) LISU LETTER SHA → LATIN CAPITAL LETTER X	#
					{0x10290,0x0058},	//	( 𐊐 → X ) LYCIAN LETTER MM → LATIN CAPITAL LETTER X	#
					{0x102B4,0x0058},	//	( 𐊴 → X ) CARIAN LETTER X → LATIN CAPITAL LETTER X	#
					{0x10317,0x0058},	//	( 𐌗 → X ) OLD ITALIC LETTER EKS → LATIN CAPITAL LETTER X	#
					{0x10527,0x0058},	//	( 𐔧 → X ) ELBASAN LETTER KHE → LATIN CAPITAL LETTER X	#
					{0x0263,0x0079},	//	( ɣ → y ) LATIN SMALL LETTER GAMMA → LATIN SMALL LETTER Y	# →γ→
					{0x1D8C,0x0079},	//	( ᶌ → y ) LATIN SMALL LETTER V WITH PALATAL HOOK → LATIN SMALL LETTER Y	#
					{0x1D432,0x0079},	//	( 𝐲 → y ) MATHEMATICAL BOLD SMALL Y → LATIN SMALL LETTER Y	#
					{0x1D466,0x0079},	//	( 𝑦 → y ) MATHEMATICAL ITALIC SMALL Y → LATIN SMALL LETTER Y	#
					{0x1D49A,0x0079},	//	( 𝒚 → y ) MATHEMATICAL BOLD ITALIC SMALL Y → LATIN SMALL LETTER Y	#
					{0x1D4CE,0x0079},	//	( 𝓎 → y ) MATHEMATICAL SCRIPT SMALL Y → LATIN SMALL LETTER Y	#
					{0x1D502,0x0079},	//	( 𝔂 → y ) MATHEMATICAL BOLD SCRIPT SMALL Y → LATIN SMALL LETTER Y	#
					{0x1D536,0x0079},	//	( 𝔶 → y ) MATHEMATICAL FRAKTUR SMALL Y → LATIN SMALL LETTER Y	#
					{0x1D56A,0x0079},	//	( 𝕪 → y ) MATHEMATICAL DOUBLE-STRUCK SMALL Y → LATIN SMALL LETTER Y	#
					{0x1D59E,0x0079},	//	( 𝖞 → y ) MATHEMATICAL BOLD FRAKTUR SMALL Y → LATIN SMALL LETTER Y	#
					{0x1D5D2,0x0079},	//	( 𝗒 → y ) MATHEMATICAL SANS-SERIF SMALL Y → LATIN SMALL LETTER Y	#
					{0x1D606,0x0079},	//	( 𝘆 → y ) MATHEMATICAL SANS-SERIF BOLD SMALL Y → LATIN SMALL LETTER Y	#
					{0x1D63A,0x0079},	//	( 𝘺 → y ) MATHEMATICAL SANS-SERIF ITALIC SMALL Y → LATIN SMALL LETTER Y	#
					{0x1D66E,0x0079},	//	( 𝙮 → y ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL Y → LATIN SMALL LETTER Y	#
					{0x1D6A2,0x0079},	//	( 𝚢 → y ) MATHEMATICAL MONOSPACE SMALL Y → LATIN SMALL LETTER Y	#
					{0x028F,0x0079},	//	( ʏ → y ) LATIN LETTER SMALL CAPITAL Y → LATIN SMALL LETTER Y	# →ү→→γ→
					{0x1EFF,0x0079},	//	( ỿ → y ) LATIN SMALL LETTER Y WITH LOOP → LATIN SMALL LETTER Y	#
					{0xAB5A,0x0079},	//	( ꭚ → y ) LATIN SMALL LETTER Y WITH SHORT RIGHT LEG → LATIN SMALL LETTER Y	#
					{0x03B3,0x0079},	//	( γ → y ) GREEK SMALL LETTER GAMMA → LATIN SMALL LETTER Y	#
					{0x213D,0x0079},	//	( ℽ → y ) DOUBLE-STRUCK SMALL GAMMA → LATIN SMALL LETTER Y	# →γ→	
					{0x1D6C4,0x0079},	//	( 𝛄 → y ) MATHEMATICAL BOLD SMALL GAMMA → LATIN SMALL LETTER Y	# →γ→
					{0x1D6FE,0x0079},	//	( 𝛾 → y ) MATHEMATICAL ITALIC SMALL GAMMA → LATIN SMALL LETTER Y	# →γ→
					{0x1D738,0x0079},	//	( 𝜸 → y ) MATHEMATICAL BOLD ITALIC SMALL GAMMA → LATIN SMALL LETTER Y	# →γ→
					{0x1D772,0x0079},	//	( 𝝲 → y ) MATHEMATICAL SANS-SERIF BOLD SMALL GAMMA → LATIN SMALL LETTER Y	# →γ→
					{0x1D7AC,0x0079},	//	( 𝞬 → y ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL GAMMA → LATIN SMALL LETTER Y	# →γ→
					{0x0443,0x0079},	//	( у → y ) CYRILLIC SMALL LETTER U → LATIN SMALL LETTER Y	#
					{0x04AF,0x0079},	//	( ү → y ) CYRILLIC SMALL LETTER STRAIGHT U → LATIN SMALL LETTER Y	# →γ→
					{0x10E7,0x0079},	//	( ყ → y ) GEORGIAN LETTER QAR → LATIN SMALL LETTER Y	#
					{0x118DC,0x0079},	//	( 𑣜 → y ) WARANG CITI SMALL LETTER HAR → LATIN SMALL LETTER Y	# →ɣ→→γ→
					{0x1D418,0x0059},	//	( 𝐘 → Y ) MATHEMATICAL BOLD CAPITAL Y → LATIN CAPITAL LETTER Y	#
					{0x1D44C,0x0059},	//	( 𝑌 → Y ) MATHEMATICAL ITALIC CAPITAL Y → LATIN CAPITAL LETTER Y	#
					{0x1D480,0x0059},	//	( 𝒀 → Y ) MATHEMATICAL BOLD ITALIC CAPITAL Y → LATIN CAPITAL LETTER Y	#
					{0x1D4B4,0x0059},	//	( 𝒴 → Y ) MATHEMATICAL SCRIPT CAPITAL Y → LATIN CAPITAL LETTER Y	#
					{0x1D4E8,0x0059},	//	( 𝓨 → Y ) MATHEMATICAL BOLD SCRIPT CAPITAL Y → LATIN CAPITAL LETTER Y	#
					{0x1D51C,0x0059},	//	( 𝔜 → Y ) MATHEMATICAL FRAKTUR CAPITAL Y → LATIN CAPITAL LETTER Y	#
					{0x1D550,0x0059},	//	( 𝕐 → Y ) MATHEMATICAL DOUBLE-STRUCK CAPITAL Y → LATIN CAPITAL LETTER Y	#
					{0x1D584,0x0059},	//	( 𝖄 → Y ) MATHEMATICAL BOLD FRAKTUR CAPITAL Y → LATIN CAPITAL LETTER Y	#
					{0x1D5B8,0x0059},	//	( 𝖸 → Y ) MATHEMATICAL SANS-SERIF CAPITAL Y → LATIN CAPITAL LETTER Y	#
					{0x1D5EC,0x0059},	//	( 𝗬 → Y ) MATHEMATICAL SANS-SERIF BOLD CAPITAL Y → LATIN CAPITAL LETTER Y	#
					{0x1D620,0x0059},	//	( 𝘠 → Y ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL Y → LATIN CAPITAL LETTER Y	#
					{0x1D654,0x0059},	//	( 𝙔 → Y ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL Y → LATIN CAPITAL LETTER Y	#
					{0x1D688,0x0059},	//	( 𝚈 → Y ) MATHEMATICAL MONOSPACE CAPITAL Y → LATIN CAPITAL LETTER Y	#
					{0x03A5,0x0059},	//	( Υ → Y ) GREEK CAPITAL LETTER UPSILON → LATIN CAPITAL LETTER Y	#
					{0x03D2,0x0059},	//	( ϒ → Y ) GREEK UPSILON WITH HOOK SYMBOL → LATIN CAPITAL LETTER Y	#
					{0x1D6BC,0x0059},	//	( 𝚼 → Y ) MATHEMATICAL BOLD CAPITAL UPSILON → LATIN CAPITAL LETTER Y	# →Υ→
					{0x1D6F6,0x0059},	//	( 𝛶 → Y ) MATHEMATICAL ITALIC CAPITAL UPSILON → LATIN CAPITAL LETTER Y	# →Υ→
					{0x1D730,0x0059},	//	( 𝜰 → Y ) MATHEMATICAL BOLD ITALIC CAPITAL UPSILON → LATIN CAPITAL LETTER Y	# →Υ→
					{0x1D76A,0x0059},	//	( 𝝪 → Y ) MATHEMATICAL SANS-SERIF BOLD CAPITAL UPSILON → LATIN CAPITAL LETTER Y	# →Υ→
					{0x1D7A4,0x0059},	//	( 𝞤 → Y ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL UPSILON → LATIN CAPITAL LETTER Y	# →Υ→
					{0x2CA8,0x0059},	//	( Ⲩ → Y ) COPTIC CAPITAL LETTER UA → LATIN CAPITAL LETTER Y	#
					{0x0423,0x0059},	//	( У → Y ) CYRILLIC CAPITAL LETTER U → LATIN CAPITAL LETTER Y	#
					{0x04AE,0x0059},	//	( Ү → Y ) CYRILLIC CAPITAL LETTER STRAIGHT U → LATIN CAPITAL LETTER Y	#
					{0x13A9,0x0059},	//	( Ꭹ → Y ) CHEROKEE LETTER GI → LATIN CAPITAL LETTER Y	#
					{0x13BD,0x0059},	//	( Ꮍ → Y ) CHEROKEE LETTER MU → LATIN CAPITAL LETTER Y	# →Ꭹ→
					{0xA4EC,0x0059},	//	( ꓬ → Y ) LISU LETTER YA → LATIN CAPITAL LETTER Y	#
					{0x16F43,0x0059},	//	( 𖽃 → Y ) MIAO LETTER AH → LATIN CAPITAL LETTER Y	#
					{0x118A4,0x0059},	//	( 𑢤 → Y ) WARANG CITI CAPITAL LETTER YA → LATIN CAPITAL LETTER Y	#
					{0x102B2,0x0059},	//	( 𐊲 → Y ) CARIAN LETTER U → LATIN CAPITAL LETTER Y	#
					{0x1D433,0x007A},	//	( 𝐳 → z ) MATHEMATICAL BOLD SMALL Z → LATIN SMALL LETTER Z	#
					{0x1D467,0x007A},	//	( 𝑧 → z ) MATHEMATICAL ITALIC SMALL Z → LATIN SMALL LETTER Z	#
					{0x1D49B,0x007A},	//	( 𝒛 → z ) MATHEMATICAL BOLD ITALIC SMALL Z → LATIN SMALL LETTER Z	#
					{0x1D4CF,0x007A},	//	( 𝓏 → z ) MATHEMATICAL SCRIPT SMALL Z → LATIN SMALL LETTER Z	#
					{0x1D503,0x007A},	//	( 𝔃 → z ) MATHEMATICAL BOLD SCRIPT SMALL Z → LATIN SMALL LETTER Z	#
					{0x1D537,0x007A},	//	( 𝔷 → z ) MATHEMATICAL FRAKTUR SMALL Z → LATIN SMALL LETTER Z	#
					{0x1D56B,0x007A},	//	( 𝕫 → z ) MATHEMATICAL DOUBLE-STRUCK SMALL Z → LATIN SMALL LETTER Z	#
					{0x1D59F,0x007A},	//	( 𝖟 → z ) MATHEMATICAL BOLD FRAKTUR SMALL Z → LATIN SMALL LETTER Z	#
					{0x1D5D3,0x007A},	//	( 𝗓 → z ) MATHEMATICAL SANS-SERIF SMALL Z → LATIN SMALL LETTER Z	#
					{0x1D607,0x007A},	//	( 𝘇 → z ) MATHEMATICAL SANS-SERIF BOLD SMALL Z → LATIN SMALL LETTER Z	#
					{0x1D63B,0x007A},	//	( 𝘻 → z ) MATHEMATICAL SANS-SERIF ITALIC SMALL Z → LATIN SMALL LETTER Z	#
					{0x1D66F,0x007A},	//	( 𝙯 → z ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL Z → LATIN SMALL LETTER Z	#
					{0x1D6A3,0x007A},	//	( 𝚣 → z ) MATHEMATICAL MONOSPACE SMALL Z → LATIN SMALL LETTER Z	#
					{0x1D22,0x007A},	//	( ᴢ → z ) LATIN LETTER SMALL CAPITAL Z → LATIN SMALL LETTER Z	#
					{0xAB93,0x007A},	//	( ꮓ → z ) CHEROKEE SMALL LETTER NO → LATIN SMALL LETTER Z	# →ᴢ→
					{0x118C4,0x007A},	//	( 𑣄 → z ) WARANG CITI SMALL LETTER YA → LATIN SMALL LETTER Z	#
					{0x102F5,0x005A},	//	( 𐋵 → Z ) COPTIC EPACT NUMBER THREE HUNDRED → LATIN CAPITAL LETTER Z	#
					{0x118E5,0x005A},	//	( 𑣥 → Z ) WARANG CITI DIGIT FIVE → LATIN CAPITAL LETTER Z	#
					{0x2124,0x005A},	//	( ℤ → Z ) DOUBLE-STRUCK CAPITAL Z → LATIN CAPITAL LETTER Z	#
					{0x2128,0x005A},	//	( ℨ → Z ) BLACK-LETTER CAPITAL Z → LATIN CAPITAL LETTER Z	#
					{0x1D419,0x005A},	//	( 𝐙 → Z ) MATHEMATICAL BOLD CAPITAL Z → LATIN CAPITAL LETTER Z	#
					{0x1D44D,0x005A},	//	( 𝑍 → Z ) MATHEMATICAL ITALIC CAPITAL Z → LATIN CAPITAL LETTER Z	#
					{0x1D481,0x005A},	//	( 𝒁 → Z ) MATHEMATICAL BOLD ITALIC CAPITAL Z → LATIN CAPITAL LETTER Z	#
					{0x1D4B5,0x005A},	//	( 𝒵 → Z ) MATHEMATICAL SCRIPT CAPITAL Z → LATIN CAPITAL LETTER Z	#
					{0x1D4E9,0x005A},	//	( 𝓩 → Z ) MATHEMATICAL BOLD SCRIPT CAPITAL Z → LATIN CAPITAL LETTER Z	#
					{0x1D585,0x005A},	//	( 𝖅 → Z ) MATHEMATICAL BOLD FRAKTUR CAPITAL Z → LATIN CAPITAL LETTER Z	#
					{0x1D5B9,0x005A},	//	( 𝖹 → Z ) MATHEMATICAL SANS-SERIF CAPITAL Z → LATIN CAPITAL LETTER Z	#
					{0x1D5ED,0x005A},	//	( 𝗭 → Z ) MATHEMATICAL SANS-SERIF BOLD CAPITAL Z → LATIN CAPITAL LETTER Z	#
					{0x1D621,0x005A},	//	( 𝘡 → Z ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL Z → LATIN CAPITAL LETTER Z	#
					{0x1D655,0x005A},	//	( 𝙕 → Z ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL Z → LATIN CAPITAL LETTER Z	#
					{0x1D689,0x005A},	//	( 𝚉 → Z ) MATHEMATICAL MONOSPACE CAPITAL Z → LATIN CAPITAL LETTER Z	#
					{0x0396,0x005A},	//	( Ζ → Z ) GREEK CAPITAL LETTER ZETA → LATIN CAPITAL LETTER Z	#
					{0x1D6AD,0x005A},	//	( 𝚭 → Z ) MATHEMATICAL BOLD CAPITAL ZETA → LATIN CAPITAL LETTER Z	# →Ζ→
					{0x1D6E7,0x005A},	//	( 𝛧 → Z ) MATHEMATICAL ITALIC CAPITAL ZETA → LATIN CAPITAL LETTER Z	# →𝑍→
					{0x1D721,0x005A},	//	( 𝜡 → Z ) MATHEMATICAL BOLD ITALIC CAPITAL ZETA → LATIN CAPITAL LETTER Z	# →Ζ→
					{0x1D75B,0x005A},	//	( 𝝛 → Z ) MATHEMATICAL SANS-SERIF BOLD CAPITAL ZETA → LATIN CAPITAL LETTER Z	# →Ζ→
					{0x1D795,0x005A},	//	( 𝞕 → Z ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL ZETA → LATIN CAPITAL LETTER Z	# →Ζ→
					{0x13C3,0x005A},	//	( Ꮓ → Z ) CHEROKEE LETTER NO → LATIN CAPITAL LETTER Z	#
					{0xA4DC,0x005A},	//	( ꓜ → Z ) LISU LETTER DZA → LATIN CAPITAL LETTER Z	#
					{0x118A9,0x005A},	//	( 𑢩 → Z ) WARANG CITI CAPITAL LETTER O → LATIN CAPITAL LETTER Z	#
					{0x01BF,0x00FE},	//	( ƿ → þ ) LATIN LETTER WYNN → LATIN SMALL LETTER THORN	#
					{0x03F8,0x00FE},	//	( ϸ → þ ) GREEK SMALL LETTER SHO → LATIN SMALL LETTER THORN	#
					{0x03F7,0x00DE},	//	( Ϸ → Þ ) GREEK CAPITAL LETTER SHO → LATIN CAPITAL LETTER THORN	#
					{0x104C4,0x00DE},	//	( 𐓄 → Þ ) OSAGE CAPITAL LETTER PA → LATIN CAPITAL LETTER THORN	#
					{0x213E,0x0393},	//	( ℾ → Γ ) DOUBLE-STRUCK CAPITAL GAMMA → GREEK CAPITAL LETTER GAMMA	#
					{0x1D6AA,0x0393},	//	( 𝚪 → Γ ) MATHEMATICAL BOLD CAPITAL GAMMA → GREEK CAPITAL LETTER GAMMA	#
					{0x1D6E4,0x0393},	//	( 𝛤 → Γ ) MATHEMATICAL ITALIC CAPITAL GAMMA → GREEK CAPITAL LETTER GAMMA	#
					{0x1D71E,0x0393},	//	( 𝜞 → Γ ) MATHEMATICAL BOLD ITALIC CAPITAL GAMMA → GREEK CAPITAL LETTER GAMMA	#
					{0x1D758,0x0393},	//	( 𝝘 → Γ ) MATHEMATICAL SANS-SERIF BOLD CAPITAL GAMMA → GREEK CAPITAL LETTER GAMMA	#
					{0x1D792,0x0393},	//	( 𝞒 → Γ ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL GAMMA → GREEK CAPITAL LETTER GAMMA	#
					{0x2C84,0x0393},	//	( Ⲅ → Γ ) COPTIC CAPITAL LETTER GAMMA → GREEK CAPITAL LETTER GAMMA	#
					{0x0413,0x0393},	//	( Г → Γ ) CYRILLIC CAPITAL LETTER GHE → GREEK CAPITAL LETTER GAMMA
					{0x13B1,0x0393},	//	( Ꮁ → Γ ) CHEROKEE LETTER HU → GREEK CAPITAL LETTER GAMMA	#
					{0x14A5,0x0393},	//	( ᒥ → Γ ) CANADIAN SYLLABICS MI → GREEK CAPITAL LETTER GAMMA	#
					{0x16F07,0x0393},	//	( 𖼇 → Γ ) MIAO LETTER FA → GREEK CAPITAL LETTER GAMMA	#
					{0x2206,0x0394},	//	( ∆ → Δ ) INCREMENT → GREEK CAPITAL LETTER DELTA	#
					{0x25B3,0x0394},	//	( △ → Δ ) WHITE UP-POINTING TRIANGLE → GREEK CAPITAL LETTER DELTA	#
					{0x1F702,0x0394},	//	( 🜂 → Δ ) ALCHEMICAL SYMBOL FOR FIRE → GREEK CAPITAL LETTER DELTA	# →△→
					{0x1D6AB,0x0394},	//	( 𝚫 → Δ ) MATHEMATICAL BOLD CAPITAL DELTA → GREEK CAPITAL LETTER DELTA	#
					{0x1D6E5,0x0394},	//	( 𝛥 → Δ ) MATHEMATICAL ITALIC CAPITAL DELTA → GREEK CAPITAL LETTER DELTA	#
					{0x1D71F,0x0394},	//	( 𝜟 → Δ ) MATHEMATICAL BOLD ITALIC CAPITAL DELTA → GREEK CAPITAL LETTER DELTA	#
					{0x1D759,0x0394},	//	( 𝝙 → Δ ) MATHEMATICAL SANS-SERIF BOLD CAPITAL DELTA → GREEK CAPITAL LETTER DELTA	#
					{0x1D793,0x0394},	//	( 𝞓 → Δ ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL DELTA → GREEK CAPITAL LETTER DELTA	#
					{0x2C86,0x0394},	//	( Ⲇ → Δ ) COPTIC CAPITAL LETTER DALDA → GREEK CAPITAL LETTER DELTA	#
					{0x2D60,0x0394},	//	( ⵠ → Δ ) TIFINAGH LETTER YAV → GREEK CAPITAL LETTER DELTA	#
					{0x1403,0x0394},	//	( ᐃ → Δ ) CANADIAN SYLLABICS I → GREEK CAPITAL LETTER DELTA	#
					{0x16F1A,0x0394},	//	( 𖼚 → Δ ) MIAO LETTER TLHA → GREEK CAPITAL LETTER DELTA	#
					{0x10285,0x0394},	//	( 𐊅 → Δ ) LYCIAN LETTER D → GREEK CAPITAL LETTER DELTA	#
					{0x102A3,0x0394},	//	( 𐊣 → Δ ) CARIAN LETTER L → GREEK CAPITAL LETTER DELTA	#
					{0x1D7CB,0x03DD},	//	( 𝟋 → ϝ ) MATHEMATICAL BOLD SMALL DIGAMMA → GREEK SMALL LETTER DIGAMMA	#
					{0x1D6C7,0x03B6},	//	( 𝛇 → ζ ) MATHEMATICAL BOLD SMALL ZETA → GREEK SMALL LETTER ZETA	#
					{0x1D701,0x03B6},	//	( 𝜁 → ζ ) MATHEMATICAL ITALIC SMALL ZETA → GREEK SMALL LETTER ZETA	#
					{0x1D73B,0x03B6},	//	( 𝜻 → ζ ) MATHEMATICAL BOLD ITALIC SMALL ZETA → GREEK SMALL LETTER ZETA	#
					{0x1D775,0x03B6},	//	( 𝝵 → ζ ) MATHEMATICAL SANS-SERIF BOLD SMALL ZETA → GREEK SMALL LETTER ZETA	#
					{0x1D7AF,0x03B6},	//	( 𝞯 → ζ ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL ZETA → GREEK SMALL LETTER ZETA	#
					{0x2CE4,0x03D7},	//	( ⳤ → ϗ ) COPTIC SYMBOL KAI → GREEK KAI SYMBOL	#
					{0x1D6CC,0x03BB},	//	( 𝛌 → λ ) MATHEMATICAL BOLD SMALL LAMDA → GREEK SMALL LETTER LAMDA	#
					{0x1D706,0x03BB},	//	( 𝜆 → λ ) MATHEMATICAL ITALIC SMALL LAMDA → GREEK SMALL LETTER LAMDA	#
					{0x1D740,0x03BB},	//	( 𝝀 → λ ) MATHEMATICAL BOLD ITALIC SMALL LAMDA → GREEK SMALL LETTER LAMDA	#
					{0x1D77A,0x03BB},	//	( 𝝺 → λ ) MATHEMATICAL SANS-SERIF BOLD SMALL LAMDA → GREEK SMALL LETTER LAMDA	#
					{0x1D7B4,0x03BB},	//	( 𝞴 → λ ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL LAMDA → GREEK SMALL LETTER LAMDA	#
					{0x2C96,0x03BB},	//	( Ⲗ → λ ) COPTIC CAPITAL LETTER LAULA → GREEK SMALL LETTER LAMDA	#
					{0x104DB,0x03BB},	//	( 𐓛 → λ ) OSAGE SMALL LETTER AH → GREEK SMALL LETTER LAMDA	#
					{0x1D6CD,0x03BC},	//	( 𝛍 → μ ) MATHEMATICAL BOLD SMALL MU → GREEK SMALL LETTER MU	#
					{0x1D707,0x03BC},	//	( 𝜇 → μ ) MATHEMATICAL ITALIC SMALL MU → GREEK SMALL LETTER MU	#
					{0x1D741,0x03BC},	//	( 𝝁 → μ ) MATHEMATICAL BOLD ITALIC SMALL MU → GREEK SMALL LETTER MU	#
					{0x1D77B,0x03BC},	//	( 𝝻 → μ ) MATHEMATICAL SANS-SERIF BOLD SMALL MU → GREEK SMALL LETTER MU	#
					{0x1D7B5,0x03BC},	//	( 𝞵 → μ ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL MU → GREEK SMALL LETTER MU	#
					{0x1D6CF,0x03BE},	//	( 𝛏 → ξ ) MATHEMATICAL BOLD SMALL XI → GREEK SMALL LETTER XI	#
					{0x1D709,0x03BE},	//	( 𝜉 → ξ ) MATHEMATICAL ITALIC SMALL XI → GREEK SMALL LETTER XI	#
					{0x1D743,0x03BE},	//	( 𝝃 → ξ ) MATHEMATICAL BOLD ITALIC SMALL XI → GREEK SMALL LETTER XI	#
					{0x1D77D,0x03BE},	//	( 𝝽 → ξ ) MATHEMATICAL SANS-SERIF BOLD SMALL XI → GREEK SMALL LETTER XI	#
					{0x1D7B7,0x03BE},	//	( 𝞷 → ξ ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL XI → GREEK SMALL LETTER XI	#
					{0x1D6B5,0x039E},	//	( 𝚵 → Ξ ) MATHEMATICAL BOLD CAPITAL XI → GREEK CAPITAL LETTER XI	#
					{0x1D6EF,0x039E},	//	( 𝛯 → Ξ ) MATHEMATICAL ITALIC CAPITAL XI → GREEK CAPITAL LETTER XI	#
					{0x1D729,0x039E},	//	( 𝜩 → Ξ ) MATHEMATICAL BOLD ITALIC CAPITAL XI → GREEK CAPITAL LETTER XI	#
					{0x1D763,0x039E},	//	( 𝝣 → Ξ ) MATHEMATICAL SANS-SERIF BOLD CAPITAL XI → GREEK CAPITAL LETTER XI	#
					{0x1D79D,0x039E},	//	( 𝞝 → Ξ ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL XI → GREEK CAPITAL LETTER XI	#
					{0x03D6,0x03C0},	//	( ϖ → π ) GREEK PI SYMBOL → GREEK SMALL LETTER PI	#
					{0x213C,0x03C0},	//	( ℼ → π ) DOUBLE-STRUCK SMALL PI → GREEK SMALL LETTER PI	#
					{0x1D6D1,0x03C0},	//	( 𝛑 → π ) MATHEMATICAL BOLD SMALL PI → GREEK SMALL LETTER PI	#
					{0x1D6E1,0x03C0},	//	( 𝛡 → π ) MATHEMATICAL BOLD PI SYMBOL → GREEK SMALL LETTER PI	#
					{0x1D70B,0x03C0},	//	( 𝜋 → π ) MATHEMATICAL ITALIC SMALL PI → GREEK SMALL LETTER PI	#
					{0x1D71B,0x03C0},	//	( 𝜛 → π ) MATHEMATICAL ITALIC PI SYMBOL → GREEK SMALL LETTER PI	#
					{0x1D745,0x03C0},	//	( 𝝅 → π ) MATHEMATICAL BOLD ITALIC SMALL PI → GREEK SMALL LETTER PI	#
					{0x1D755,0x03C0},	//	( 𝝕 → π ) MATHEMATICAL BOLD ITALIC PI SYMBOL → GREEK SMALL LETTER PI	#
					{0x1D77F,0x03C0},	//	( 𝝿 → π ) MATHEMATICAL SANS-SERIF BOLD SMALL PI → GREEK SMALL LETTER PI	#
					{0x1D78F,0x03C0},	//	( 𝞏 → π ) MATHEMATICAL SANS-SERIF BOLD PI SYMBOL → GREEK SMALL LETTER PI	#
					{0x1D7B9,0x03C0},	//	( 𝞹 → π ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL PI → GREEK SMALL LETTER PI	#
					{0x1D7C9,0x03C0},	//	( 𝟉 → π ) MATHEMATICAL SANS-SERIF BOLD ITALIC PI SYMBOL → GREEK SMALL LETTER PI	#
					{0x1D28,0x03C0},	//	( ᴨ → π ) GREEK LETTER SMALL CAPITAL PI → GREEK SMALL LETTER PI	# →п→
					{0x043F,0x03C0},	//	( п → π ) CYRILLIC SMALL LETTER PE → GREEK SMALL LETTER PI	#
					{0x220F,0x03A0},	//	( ∏ → Π ) N-ARY PRODUCT → GREEK CAPITAL LETTER PI	#
					{0x213F,0x03A0},	//	( ℿ → Π ) DOUBLE-STRUCK CAPITAL PI → GREEK CAPITAL LETTER PI	#
					{0x1D6B7,0x03A0},	//	( 𝚷 → Π ) MATHEMATICAL BOLD CAPITAL PI → GREEK CAPITAL LETTER PI	#
					{0x1D6F1,0x03A0},	//	( 𝛱 → Π ) MATHEMATICAL ITALIC CAPITAL PI → GREEK CAPITAL LETTER PI	#
					{0x1D72B,0x03A0},	//	( 𝜫 → Π ) MATHEMATICAL BOLD ITALIC CAPITAL PI → GREEK CAPITAL LETTER PI	#
					{0x1D765,0x03A0},	//	( 𝝥 → Π ) MATHEMATICAL SANS-SERIF BOLD CAPITAL PI → GREEK CAPITAL LETTER PI	#
					{0x1D79F,0x03A0},	//	( 𝞟 → Π ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL PI → GREEK CAPITAL LETTER PI	#
					{0x2CA0,0x03A0},	//	( Ⲡ → Π ) COPTIC CAPITAL LETTER PI → GREEK CAPITAL LETTER PI	#
					{0x041F,0x03A0},	//	( П → Π ) CYRILLIC CAPITAL LETTER PE → GREEK CAPITAL LETTER PI	#
					{0xA6DB,0x03A0},	//	( ꛛ → Π ) BAMUM LETTER NA → GREEK CAPITAL LETTER PI	#
					{0x102AD,0x03D8},	//	( 𐊭 → Ϙ ) CARIAN LETTER T → GREEK LETTER ARCHAIC KOPPA	#
					{0x10312,0x03D8},	//	( 𐌒 → Ϙ ) OLD ITALIC LETTER KU → GREEK LETTER ARCHAIC KOPPA	#
					{0x1D6D3,0x03C2},	//	( 𝛓 → ς ) MATHEMATICAL BOLD SMALL FINAL SIGMA → GREEK SMALL LETTER FINAL SIGMA	#
					{0x1D70D,0x03C2},	//	( 𝜍 → ς ) MATHEMATICAL ITALIC SMALL FINAL SIGMA → GREEK SMALL LETTER FINAL SIGMA	#
					{0x1D747,0x03C2},	//	( 𝝇 → ς ) MATHEMATICAL BOLD ITALIC SMALL FINAL SIGMA → GREEK SMALL LETTER FINAL SIGMA	#
					{0x1D781,0x03C2},	//	( 𝞁 → ς ) MATHEMATICAL SANS-SERIF BOLD SMALL FINAL SIGMA → GREEK SMALL LETTER FINAL SIGMA	#
					{0x1D7BB,0x03C2},	//	( 𝞻 → ς ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL FINAL SIGMA → GREEK SMALL LETTER FINAL SIGMA	#
					{0x1D6BD,0x03A6},	//	( 𝚽 → Φ ) MATHEMATICAL BOLD CAPITAL PHI → GREEK CAPITAL LETTER PHI	#
					{0x1D6F7,0x03A6},	//	( 𝛷 → Φ ) MATHEMATICAL ITALIC CAPITAL PHI → GREEK CAPITAL LETTER PHI	#
					{0x1D731,0x03A6},	//	( 𝜱 → Φ ) MATHEMATICAL BOLD ITALIC CAPITAL PHI → GREEK CAPITAL LETTER PHI	#
					{0x1D76B,0x03A6},	//	( 𝝫 → Φ ) MATHEMATICAL SANS-SERIF BOLD CAPITAL PHI → GREEK CAPITAL LETTER PHI	#
					{0x1D7A5,0x03A6},	//	( 𝞥 → Φ ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL PHI → GREEK CAPITAL LETTER PHI	#
					{0x2CAA,0x03A6},	//	( Ⲫ → Φ ) COPTIC CAPITAL LETTER FI → GREEK CAPITAL LETTER PHI	#
					{0x0424,0x03A6},	//	( Ф → Φ ) CYRILLIC CAPITAL LETTER EF → GREEK CAPITAL LETTER PHI	#
					{0x0553,0x03A6},	//	( Փ → Φ ) ARMENIAN CAPITAL LETTER PIWR → GREEK CAPITAL LETTER PHI	#
					{0x1240,0x03A6},	//	( ቀ → Φ ) ETHIOPIC SYLLABLE QA → GREEK CAPITAL LETTER PHI	# →Փ→
					{0x16F0,0x03A6},	//	( ᛰ → Φ ) RUNIC BELGTHOR SYMBOL → GREEK CAPITAL LETTER PHI	#
					{0x102B3,0x03A6},	//	( 𐊳 → Φ ) CARIAN LETTER NN → GREEK CAPITAL LETTER PHI	#
					{0xAB53,0x03C7},	//	( ꭓ → χ ) LATIN SMALL LETTER CHI → GREEK SMALL LETTER CHI	#
					{0xAB55,0x03C7},	//	( ꭕ → χ ) LATIN SMALL LETTER CHI WITH LOW LEFT SERIF → GREEK SMALL LETTER CHI	#
					{0x1D6D8,0x03C7},	//	( 𝛘 → χ ) MATHEMATICAL BOLD SMALL CHI → GREEK SMALL LETTER CHI	#
					{0x1D712,0x03C7},	//	( 𝜒 → χ ) MATHEMATICAL ITALIC SMALL CHI → GREEK SMALL LETTER CHI	#
					{0x1D74C,0x03C7},	//	( 𝝌 → χ ) MATHEMATICAL BOLD ITALIC SMALL CHI → GREEK SMALL LETTER CHI	#
					{0x1D786,0x03C7},	//	( 𝞆 → χ ) MATHEMATICAL SANS-SERIF BOLD SMALL CHI → GREEK SMALL LETTER CHI	#
					{0x1D7C0,0x03C7},	//	( 𝟀 → χ ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL CHI → GREEK SMALL LETTER CHI	#
					{0x2CAD,0x03C7},	//	( ⲭ → χ ) COPTIC SMALL LETTER KHI → GREEK SMALL LETTER CHI	#
					{0x1D6D9,0x03C8},	//	( 𝛙 → ψ ) MATHEMATICAL BOLD SMALL PSI → GREEK SMALL LETTER PSI	#
					{0x1D713,0x03C8},	//	( 𝜓 → ψ ) MATHEMATICAL ITALIC SMALL PSI → GREEK SMALL LETTER PSI	#
					{0x1D74D,0x03C8},	//	( 𝝍 → ψ ) MATHEMATICAL BOLD ITALIC SMALL PSI → GREEK SMALL LETTER PSI	#
					{0x1D787,0x03C8},	//	( 𝞇 → ψ ) MATHEMATICAL SANS-SERIF BOLD SMALL PSI → GREEK SMALL LETTER PSI	#
					{0x1D7C1,0x03C8},	//	( 𝟁 → ψ ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL PSI → GREEK SMALL LETTER PSI	#
					{0x0471,0x03C8},	//	( ѱ → ψ ) CYRILLIC SMALL LETTER PSI → GREEK SMALL LETTER PSI	#
					{0x104F9,0x03C8},	//	( 𐓹 → ψ ) OSAGE SMALL LETTER GHA → GREEK SMALL LETTER PSI	#
					{0x1D6BF,0x03A8},	//	( 𝚿 → Ψ ) MATHEMATICAL BOLD CAPITAL PSI → GREEK CAPITAL LETTER PSI	#
					{0x1D6F9,0x03A8},	//	( 𝛹 → Ψ ) MATHEMATICAL ITALIC CAPITAL PSI → GREEK CAPITAL LETTER PSI	#
					{0x1D733,0x03A8},	//	( 𝜳 → Ψ ) MATHEMATICAL BOLD ITALIC CAPITAL PSI → GREEK CAPITAL LETTER PSI	#
					{0x1D76D,0x03A8},	//	( 𝝭 → Ψ ) MATHEMATICAL SANS-SERIF BOLD CAPITAL PSI → GREEK CAPITAL LETTER PSI	#
					{0x1D7A7,0x03A8},	//	( 𝞧 → Ψ ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL PSI → GREEK CAPITAL LETTER PSI	#
					{0x2CAE,0x03A8},	//	( Ⲯ → Ψ ) COPTIC CAPITAL LETTER PSI → GREEK CAPITAL LETTER PSI	#
					{0x0470,0x03A8},	//	( Ѱ → Ψ ) CYRILLIC CAPITAL LETTER PSI → GREEK CAPITAL LETTER PSI	#
					{0x104D1,0x03A8},	//	( 𐓑 → Ψ ) OSAGE CAPITAL LETTER GHA → GREEK CAPITAL LETTER PSI	#
					{0x16D8,0x03A8},	//	( ᛘ → Ψ ) RUNIC LETTER LONG-BRANCH-MADR M → GREEK CAPITAL LETTER PSI	#
					{0x102B5,0x03A8},	//	( 𐊵 → Ψ ) CARIAN LETTER N → GREEK CAPITAL LETTER PSI	#
					{0x2375,0x03C9},	//	( ⍵ → ω ) APL FUNCTIONAL SYMBOL OMEGA → GREEK SMALL LETTER OMEGA	#
					{0xA7B7,0x03C9},	//	( ꞷ → ω ) LATIN SMALL LETTER OMEGA → GREEK SMALL LETTER OMEGA	#
					{0x1D6DA,0x03C9},	//	( 𝛚 → ω ) MATHEMATICAL BOLD SMALL OMEGA → GREEK SMALL LETTER OMEGA	#
					{0x1D714,0x03C9},	//	( 𝜔 → ω ) MATHEMATICAL ITALIC SMALL OMEGA → GREEK SMALL LETTER OMEGA	#
					{0x1D74E,0x03C9},	//	( 𝝎 → ω ) MATHEMATICAL BOLD ITALIC SMALL OMEGA → GREEK SMALL LETTER OMEGA	#
					{0x1D788,0x03C9},	//	( 𝞈 → ω ) MATHEMATICAL SANS-SERIF BOLD SMALL OMEGA → GREEK SMALL LETTER OMEGA	#
					{0x1D7C2,0x03C9},	//	( 𝟂 → ω ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL OMEGA → GREEK SMALL LETTER OMEGA	#
					{0x2CB1,0x03C9},	//	( ⲱ → ω ) COPTIC SMALL LETTER OOU → GREEK SMALL LETTER OMEGA	#
					{0xA64D,0x03C9},	//	( ꙍ → ω ) CYRILLIC SMALL LETTER BROAD OMEGA → GREEK SMALL LETTER OMEGA	# →ꞷ→
					{0x2126,0x03A9},	//	( Ω → Ω ) OHM SIGN → GREEK CAPITAL LETTER OMEGA	#
					{0x1D6C0,0x03A9},	//	( 𝛀 → Ω ) MATHEMATICAL BOLD CAPITAL OMEGA → GREEK CAPITAL LETTER OMEGA	#
					{0x1D6FA,0x03A9},	//	( 𝛺 → Ω ) MATHEMATICAL ITALIC CAPITAL OMEGA → GREEK CAPITAL LETTER OMEGA	#
					{0x1D734,0x03A9},	//	( 𝜴 → Ω ) MATHEMATICAL BOLD ITALIC CAPITAL OMEGA → GREEK CAPITAL LETTER OMEGA	#
					{0x1D76E,0x03A9},	//	( 𝝮 → Ω ) MATHEMATICAL SANS-SERIF BOLD CAPITAL OMEGA → GREEK CAPITAL LETTER OMEGA	#
					{0x1D7A8,0x03A9},	//	( 𝞨 → Ω ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL OMEGA → GREEK CAPITAL LETTER OMEGA	#
					{0x162F,0x03A9},	//	( ᘯ → Ω ) CANADIAN SYLLABICS CARRIER LHO → GREEK CAPITAL LETTER OMEGA	#
					{0x1635,0x03A9},	//	( ᘵ → Ω ) CANADIAN SYLLABICS CARRIER TLHO → GREEK CAPITAL LETTER OMEGA	# →ᘯ→
					{0x102B6,0x03A9},	//	( 𐊶 → Ω ) CARIAN LETTER TT2 → GREEK CAPITAL LETTER OMEGA	#
					{0x2CDC,0x03EC},	//	( Ⳝ → Ϭ ) COPTIC CAPITAL LETTER OLD NUBIAN SHIMA → COPTIC CAPITAL LETTER SHIMA	#
					{0x1D20B,0x0418},	//	( 𝈋 → И ) GREEK VOCAL NOTATION SYMBOL-12 → CYRILLIC CAPITAL LETTER I	# →Ͷ→
					{0x0376,0x0418},	//	( Ͷ → И ) GREEK CAPITAL LETTER PAMPHYLIAN DIGAMMA → CYRILLIC CAPITAL LETTER I	#
					{0xA6A1,0x0418},	//	( ꚡ → И ) BAMUM LETTER KA → CYRILLIC CAPITAL LETTER I	# →Ͷ→
					{0x10425,0x0418},	//	( 𐐥 → И ) DESERET CAPITAL LETTER ENG → CYRILLIC CAPITAL LETTER I	#
					{0x104BC,0x04C3},	//	( 𐒼 → Ӄ ) OSAGE CAPITAL LETTER KA → CYRILLIC CAPITAL LETTER KA WITH HOOK	#
					{0x1D2B,0x043B},	//	( ᴫ → л ) CYRILLIC LETTER SMALL CAPITAL EL → CYRILLIC SMALL LETTER EL	#
					{0xAB60,0x0459},	//	( ꭠ → љ ) LATIN SMALL LETTER SAKHA YAT → CYRILLIC SMALL LETTER LJE	#
					{0x104CD,0x040B},	//	( 𐓍 → Ћ ) OSAGE CAPITAL LETTER DHA → CYRILLIC CAPITAL LETTER TSHE	#
					{0x1D202,0x04FE},	//	( 𝈂 → Ӿ ) GREEK VOCAL NOTATION SYMBOL-3 → CYRILLIC CAPITAL LETTER HA WITH STROKE	#
					{0x1D222,0x0460},	//	( 𝈢 → Ѡ ) GREEK INSTRUMENTAL NOTATION SYMBOL-8 → CYRILLIC CAPITAL LETTER OMEGA	#
					{0x13C7,0x0460},	//	( Ꮗ → Ѡ ) CHEROKEE LETTER QUE → CYRILLIC CAPITAL LETTER OMEGA	#
					{0x15EF,0x0460},	//	( ᗯ → Ѡ ) CANADIAN SYLLABICS CARRIER GU → CYRILLIC CAPITAL LETTER OMEGA	#
					{0x04CC,0x04B7},	//	( ӌ → ҷ ) CYRILLIC SMALL LETTER KHAKASSIAN CHE → CYRILLIC SMALL LETTER CHE WITH DESCENDER	#
					{0x04CB,0x04B6},	//	( Ӌ → Ҷ ) CYRILLIC CAPITAL LETTER KHAKASSIAN CHE → CYRILLIC CAPITAL LETTER CHE WITH DESCENDER	#
					{0x2CBD,0x0448},	//	( ⲽ → ш ) COPTIC SMALL LETTER CRYPTOGRAMMIC NI → CYRILLIC SMALL LETTER SHA	#
					{0x2CBC,0x0428},	//	( Ⲽ → Ш ) COPTIC CAPITAL LETTER CRYPTOGRAMMIC NI → CYRILLIC CAPITAL LETTER SHA	#
					{0x2108,0x042D},	//	( ℈ → Э ) SCRUPLE → CYRILLIC CAPITAL LETTER E	#
					//
					// --- Mappings not generated from confusables.txt ---
					//
					{0x212B,0x00C5},	//	( Å → Å ) ANGSTROM SIGN → LATIN CAPITAL LETTER A WITH RING ABOVE
					// Fullwidth forms (those present in confusables.txt have been removed above)
					{0xFF01,0x0021},	//	( ！ → ! ) FULLWIDTH EXCLAMATION MARK → EXCLAMATION MARK
					{0xFF02,0x0022},	//	( ＂ → " ) FULLWIDTH QUOTATION MARK → QUOTATION MARK
					{0xFF03,0x0023},	//	( ＃ → # ) FULLWIDTH NUMBER SIGN → NUMBER SIGN
					{0xFF04,0x0024},	//	( ＄ → $ ) FULLWIDTH DOLLAR SIGN → DOLLAR SIGN
					{0xFF05,0x0025},	//	( ％ → % ) FULLWIDTH PERCENT SIGN → PERCENT SIGN
					{0xFF06,0x0026},	//	( ＆ → & ) FULLWIDTH AMPERSAND → AMPERSAND
					{0xFF07,0x0027},	//	( ＇ → ' ) FULLWIDTH APOSTROPHE → APOSTROPHE
					{0xFF08,0x0028},	//	( （ → ( ) FULLWIDTH LEFT PARENTHESIS → LEFT PARENTHESIS
					{0xFF09,0x0029},	//	( ） → ) ) FULLWIDTH RIGHT PARENTHESIS → RIGHT PARENTHESIS
					{0xFF0A,0x002A},	//	( ＊ → * ) FULLWIDTH ASTERISK → ASTERISK
					{0xFF0B,0x002B},	//	( ＋ → + ) FULLWIDTH PLUS SIGN → PLUS SIGN
					{0xFF0C,0x002C},	//	( ， → , ) FULLWIDTH COMMA → COMMA
					{0xFF0D,0x002D},	//	( － → - ) FULLWIDTH HYPHEN-MINUS → HYPHEN-MINUS
					{0xFF0E,0x002E},	//	( ． → . ) FULLWIDTH FULL STOP → FULL STOP
					{0xFF0F,0x002F},	//	( ／ → / ) FULLWIDTH SOLIDUS → SOLIDUS
					{0xFF10,0x0030},	//	( ０ → 0 ) FULLWIDTH DIGIT ZERO → DIGIT ZERO
					{0xFF11,0x0031},	//	( １ → 1 ) FULLWIDTH DIGIT ONE → DIGIT ONE
					{0xFF12,0x0032},	//	( ２ → 2 ) FULLWIDTH DIGIT TWO → DIGIT TWO
					{0xFF13,0x0033},	//	( ３ → 3 ) FULLWIDTH DIGIT THREE → DIGIT THREE
					{0xFF14,0x0034},	//	( ４ → 4 ) FULLWIDTH DIGIT FOUR → DIGIT FOUR
					{0xFF15,0x0035},	//	( ５ → 5 ) FULLWIDTH DIGIT FIVE → DIGIT FIVE
					{0xFF16,0x0036},	//	( ６ → 6 ) FULLWIDTH DIGIT SIX → DIGIT SIX
					{0xFF17,0x0037},	//	( ７ → 7 ) FULLWIDTH DIGIT SEVEN → DIGIT SEVEN
					{0xFF18,0x0038},	//	( ８ → 8 ) FULLWIDTH DIGIT EIGHT → DIGIT EIGHT
					{0xFF19,0x0039},	//	( ９ → 9 ) FULLWIDTH DIGIT NINE → DIGIT NINE
					{0xFF1A,0x003A},	//	( ： → : ) FULLWIDTH COLON → COLON
					{0xFF1B,0x003B},	//	( ； → ; ) FULLWIDTH SEMICOLON → SEMICOLON
					{0xFF1C,0x003C},	//	( ＜ → < ) FULLWIDTH LESS-THAN SIGN → LESS-THAN SIGN
					{0xFF1D,0x003D},	//	( ＝ → = ) FULLWIDTH EQUALS SIGN → EQUALS SIGN
					{0xFF1E,0x003E},	//	( ＞ → > ) FULLWIDTH GREATER-THAN SIGN → GREATER-THAN SIGN
					{0xFF1F,0x003F},	//	( ？ → ? ) FULLWIDTH QUESTION MARK → QUESTION MARK
					{0xFF20,0x0040},	//	( ＠ → @ ) FULLWIDTH COMMERCIAL AT → COMMERCIAL AT
					{0xFF21,0x0041},	//	( Ａ → A ) FULLWIDTH LATIN CAPITAL LETTER A → LATIN CAPITAL LETTER A
					{0xFF22,0x0042},	//	( Ｂ → B ) FULLWIDTH LATIN CAPITAL LETTER B → LATIN CAPITAL LETTER B
					{0xFF23,0x0043},	//	( Ｃ → C ) FULLWIDTH LATIN CAPITAL LETTER C → LATIN CAPITAL LETTER C
					{0xFF24,0x0044},	//	( Ｄ → D ) FULLWIDTH LATIN CAPITAL LETTER D → LATIN CAPITAL LETTER D
					{0xFF25,0x0045},	//	( Ｅ → E ) FULLWIDTH LATIN CAPITAL LETTER E → LATIN CAPITAL LETTER E
					{0xFF26,0x0046},	//	( Ｆ → F ) FULLWIDTH LATIN CAPITAL LETTER F → LATIN CAPITAL LETTER F
					{0xFF27,0x0047},	//	( Ｇ → G ) FULLWIDTH LATIN CAPITAL LETTER G → LATIN CAPITAL LETTER G
					{0xFF28,0x0048},	//	( Ｈ → H ) FULLWIDTH LATIN CAPITAL LETTER H → LATIN CAPITAL LETTER H
					{0xFF29,0x0049},	//	( Ｉ → I ) FULLWIDTH LATIN CAPITAL LETTER I → LATIN CAPITAL LETTER I
					{0xFF2A,0x004A},	//	( Ｊ → J ) FULLWIDTH LATIN CAPITAL LETTER J → LATIN CAPITAL LETTER J
					{0xFF2B,0x004B},	//	( Ｋ → K ) FULLWIDTH LATIN CAPITAL LETTER K → LATIN CAPITAL LETTER K
					{0xFF2C,0x004C},	//	( Ｌ → L ) FULLWIDTH LATIN CAPITAL LETTER L → LATIN CAPITAL LETTER L
					{0xFF2D,0x004D},	//	( Ｍ → M ) FULLWIDTH LATIN CAPITAL LETTER M → LATIN CAPITAL LETTER M
					{0xFF2E,0x004E},	//	( Ｎ → N ) FULLWIDTH LATIN CAPITAL LETTER N → LATIN CAPITAL LETTER N
					{0xFF2F,0x004F},	//	( Ｏ → O ) FULLWIDTH LATIN CAPITAL LETTER O → LATIN CAPITAL LETTER O
					{0xFF30,0x0050},	//	( Ｐ → P ) FULLWIDTH LATIN CAPITAL LETTER P → LATIN CAPITAL LETTER P
					{0xFF31,0x0051},	//	( Ｑ → Q ) FULLWIDTH LATIN CAPITAL LETTER Q → LATIN CAPITAL LETTER Q
					{0xFF32,0x0052},	//	( Ｒ → R ) FULLWIDTH LATIN CAPITAL LETTER R → LATIN CAPITAL LETTER R
					{0xFF33,0x0053},	//	( Ｓ → S ) FULLWIDTH LATIN CAPITAL LETTER S → LATIN CAPITAL LETTER S
					{0xFF34,0x0054},	//	( Ｔ → T ) FULLWIDTH LATIN CAPITAL LETTER T → LATIN CAPITAL LETTER T
					{0xFF35,0x0055},	//	( Ｕ → U ) FULLWIDTH LATIN CAPITAL LETTER U → LATIN CAPITAL LETTER U
					{0xFF36,0x0056},	//	( Ｖ → V ) FULLWIDTH LATIN CAPITAL LETTER V → LATIN CAPITAL LETTER V
					{0xFF37,0x0057},	//	( Ｗ → W ) FULLWIDTH LATIN CAPITAL LETTER W → LATIN CAPITAL LETTER W
					{0xFF38,0x0058},	//	( Ｘ → X ) FULLWIDTH LATIN CAPITAL LETTER X → LATIN CAPITAL LETTER X
					{0xFF39,0x0059},	//	( Ｙ → Y ) FULLWIDTH LATIN CAPITAL LETTER Y → LATIN CAPITAL LETTER Y
					{0xFF3A,0x005A},	//	( Ｚ → Z ) FULLWIDTH LATIN CAPITAL LETTER Z → LATIN CAPITAL LETTER Z
					{0xFF3B,0x005B},	//	( ［ → [ ) FULLWIDTH LEFT SQUARE BRACKET → LEFT SQUARE BRACKET
					{0xFF3C,0x005C},	//	( ＼ → \ ) FULLWIDTH REVERSE SOLIDUS → REVERSE SOLIDUS
					{0xFF3D,0x005D},	//	( ］ → ] ) FULLWIDTH RIGHT SQUARE BRACKET → RIGHT SQUARE BRACKET
					{0xFF3E,0x005E},	//	( ＾ → ^ ) FULLWIDTH CIRCUMFLEX ACCENT → CIRCUMFLEX ACCENT
					{0xFF3F,0x005F},	//	( ＿ → _ ) FULLWIDTH LOW LINE → LOW LINE
					{0xFF40,0x0060},	//	( ｀ → ` ) FULLWIDTH GRAVE ACCENT → GRAVE ACCENT
					{0xFF41,0x0061},	//	( ａ → a ) FULLWIDTH LATIN SMALL LETTER A → LATIN SMALL LETTER A
					{0xFF42,0x0062},	//	( ｂ → b ) FULLWIDTH LATIN SMALL LETTER B → LATIN SMALL LETTER B
					{0xFF43,0x0063},	//	( ｃ → c ) FULLWIDTH LATIN SMALL LETTER C → LATIN SMALL LETTER C
					{0xFF44,0x0064},	//	( ｄ → d ) FULLWIDTH LATIN SMALL LETTER D → LATIN SMALL LETTER D
					{0xFF45,0x0065},	//	( ｅ → e ) FULLWIDTH LATIN SMALL LETTER E → LATIN SMALL LETTER E
					{0xFF46,0x0066},	//	( ｆ → f ) FULLWIDTH LATIN SMALL LETTER F → LATIN SMALL LETTER F
					{0xFF47,0x0067},	//	( ｇ → g ) FULLWIDTH LATIN SMALL LETTER G → LATIN SMALL LETTER G
					{0xFF48,0x0068},	//	( ｈ → h ) FULLWIDTH LATIN SMALL LETTER H → LATIN SMALL LETTER H
					{0xFF49,0x0069},	//	( ｉ → i ) FULLWIDTH LATIN SMALL LETTER I → LATIN SMALL LETTER I
					{0xFF4A,0x006A},	//	( ｊ → j ) FULLWIDTH LATIN SMALL LETTER J → LATIN SMALL LETTER J
					{0xFF4B,0x006B},	//	( ｋ → k ) FULLWIDTH LATIN SMALL LETTER K → LATIN SMALL LETTER K
					{0xFF4C,0x006C},	//	( ｌ → l ) FULLWIDTH LATIN SMALL LETTER L → LATIN SMALL LETTER L
					{0xFF4D,0x006D},	//	( ｍ → m ) FULLWIDTH LATIN SMALL LETTER M → LATIN SMALL LETTER M
					{0xFF4E,0x006E},	//	( ｎ → n ) FULLWIDTH LATIN SMALL LETTER N → LATIN SMALL LETTER N
					{0xFF4F,0x006F},	//	( ｏ → o ) FULLWIDTH LATIN SMALL LETTER O → LATIN SMALL LETTER O
					{0xFF50,0x0070},	//	( ｐ → p ) FULLWIDTH LATIN SMALL LETTER P → LATIN SMALL LETTER P
					{0xFF51,0x0071},	//	( ｑ → q ) FULLWIDTH LATIN SMALL LETTER Q → LATIN SMALL LETTER Q
					{0xFF52,0x0072},	//	( ｒ → r ) FULLWIDTH LATIN SMALL LETTER R → LATIN SMALL LETTER R
					{0xFF53,0x0073},	//	( ｓ → s ) FULLWIDTH LATIN SMALL LETTER S → LATIN SMALL LETTER S
					{0xFF54,0x0074},	//	( ｔ → t ) FULLWIDTH LATIN SMALL LETTER T → LATIN SMALL LETTER T
					{0xFF55,0x0075},	//	( ｕ → u ) FULLWIDTH LATIN SMALL LETTER U → LATIN SMALL LETTER U
					{0xFF56,0x0076},	//	( ｖ → v ) FULLWIDTH LATIN SMALL LETTER V → LATIN SMALL LETTER V
					{0xFF57,0x0077},	//	( ｗ → w ) FULLWIDTH LATIN SMALL LETTER W → LATIN SMALL LETTER W
					{0xFF58,0x0078},	//	( ｘ → x ) FULLWIDTH LATIN SMALL LETTER X → LATIN SMALL LETTER X
					{0xFF59,0x0079},	//	( ｙ → y ) FULLWIDTH LATIN SMALL LETTER Y → LATIN SMALL LETTER Y
					{0xFF5A,0x007A},	//	( ｚ → z ) FULLWIDTH LATIN SMALL LETTER Z → LATIN SMALL LETTER Z
					{0xFF5B,0x007B},	//	( ｛ → { ) FULLWIDTH LEFT CURLY BRACKET → LEFT CURLY BRACKET
					{0xFF5C,0x007C},	//	( ｜ → | ) FULLWIDTH VERTICAL LINE → VERTICAL LINE
					{0xFF5D,0x007D},	//	( ｝ → } ) FULLWIDTH RIGHT CURLY BRACKET → RIGHT CURLY BRACKET
					{0xFF5E,0x007E},	//	( ～ → ~ ) FULLWIDTH TILDE → TILDE
					// Small Form Variants (other than those present in confusables.txt)
					{0xFE50,0x002C},	//	( ﹐ → , ) SMALL COMMA → COMMA
					{0xFE52,0x002E},	//	( ﹒ → . ) SMALL FULL STOP → FULL STOP
					{0xFE54,0x003B},	//	( ﹔ → ; ) SMALL SEMICOLON → SEMICOLON
					{0xFE55,0x003A},	//	( ﹕ → : ) SMALL COLON → COLON
					{0xFE56,0x003F},	//	( ﹖ → ? ) SMALL QUESTION MARK → QUESTION MARK
					{0xFE57,0x0021},	//	( ﹗ → ! ) SMALL EXCLAMATION MARK → EXCLAMATION MARK
					{0xFE59,0x0028},	//	( ﹙ → ( ) SMALL LEFT PARENTHESIS → LEFT PARENTHESIS
					{0xFE5A,0x0029},	//	( ﹚ → ) ) SMALL RIGHT PARENTHESIS → RIGHT PARENTHESIS
					{0xFE5B,0x007B},	//	( ﹛ → { ) SMALL LEFT CURLY BRACKET → LEFT CURLY BRACKET
					{0xFE5C,0x007D},	//	( ﹜ → } ) SMALL RIGHT CURLY BRACKET → RIGHT CURLY BRACKET
					{0xFE5F,0x0023},	//	( ﹟ → # ) SMALL NUMBER SIGN → NUMBER SIGN
					{0xFE60,0x0026},	//	( ﹠ → & ) SMALL AMPERSAND → AMPERSAND
					{0xFE61,0x002A},	//	( ﹡ → * ) SMALL ASTERISK → ASTERISK
					{0xFE62,0x002B},	//	( ﹢ → + ) SMALL PLUS SIGN → PLUS SIGN
					{0xFE63,0x002D},	//	( ﹣ → - ) SMALL HYPHEN-MINUS → HYPHEN-MINUS
					{0xFE64,0x003C},	//	( ﹤ → < ) SMALL LESS-THAN SIGN → LESS-THAN SIGN
					{0xFE65,0x003E},	//	( ﹥ → > ) SMALL GREATER-THAN SIGN → GREATER-THAN SIGN
					{0xFE66,0x003D},	//	( ﹦ → = ) SMALL EQUALS SIGN → EQUALS SIGN
					{0xFE69,0x0024},	//	( ﹩ → $ ) SMALL DOLLAR SIGN → DOLLAR SIGN
					{0xFE6A,0x0025},	//	( ﹪ → % ) SMALL PERCENT SIGN → PERCENT SIGN
					{0xFE6B,0x0040},	//	( ﹫ → @ ) SMALL COMMERCIAL AT → COMMERCIAL AT
				};
				
				confusable_map_1 = new HashMap<>( map1.length );
				
				confusable_map_2 = new HashMap<>( map1.length + map2.length );

				for ( int[] entry: map1 ){
					
					confusable_map_1.put( entry[0], entry[1] );
					
					confusable_map_2.put( entry[0], entry[1] );
				}
							
				for ( int[] entry: map2 ){
					
					confusable_map_2.put( entry[0], entry[1] );
				}
				
				confusable_recent_1 = 
						new LinkedHashMap<String,String>( 32, 0.75f, true )
						{
							@Override
							protected boolean
							removeEldestEntry(
								Map.Entry<String,String>	entry )
							{
								return( size() > 32 );
							}
						};

				confusable_recent_2 = 
					new LinkedHashMap<String,String>( 32, 0.75f, true )
					{
						@Override
						protected boolean
						removeEldestEntry(
							Map.Entry<String,String>	entry )
						{
							return( size() > 32 );
						}
					};
			}
			
			Map<Integer,Integer> confusable_map = is_query ? confusable_map_1 : confusable_map_2;
			Map<String,String> confusable_recent = is_query ? confusable_recent_1 : confusable_recent_2;
			
			String existing = confusable_recent.get( str );
			
			if ( existing != null ){
				
				return( existing );
			}
		
			StringBuilder result = new StringBuilder( str.length());
			
			char[] chars = str.toCharArray();
			
			char high_surrogate = 0;
			
			for ( char c: chars ){
				
				if ( Character.isLowSurrogate(c)){
					
					if ( high_surrogate > 0 ){
						
							// we have a pair
						
						int cp = Character.toCodePoint(high_surrogate, c );
						
						Integer k = confusable_map.get( cp );
						
						if ( k == null ){
							
							result.append( c );
							
						}else{
							
							result.append((char)k.intValue());
						}
						
						high_surrogate = 0;
						
					}else{
						
						// unmatched low surrogate, ignore
					}
				}else if ( Character.isHighSurrogate(c)){
					
					high_surrogate = c;

				}else{
					
					Integer k = confusable_map.get((int)c);
					
					if ( k == null ){
						
						result.append( c );
						
					}else{
						
						result.append((char)k.intValue());
					}
				}
			}
		
			String r = result.toString();
			
			confusable_recent.put( str,  r );
						
			return( r );
		}
	}
	
	/*
	public static void
	main(
		String[]	args )
	{
			// extract ascii subset of confusable chars
			// http://www.unicode.org/Public/security/latest/confusables.txt
		
		try{
			LineNumberReader lnr = new LineNumberReader( new InputStreamReader( new FileInputStream( "confusables.txt"), "UTF-8" ));
			
			PrintWriter out = new PrintWriter( "confusables.out", "UTF-8" );
			
			while( true ){
				
				String line = lnr.readLine();
				
				if ( line == null ){
					break;
				}
				
				line = line.trim();
				
				if ( line.isEmpty() || line.startsWith( "#" )){
					
					continue;
				}
				
				String[] bits = line.split( ";" );
				
				if ( bits.length >= 2 ){
					
					String from_cp 	= "0" + bits[0].trim();
					String to_cp 	= "0" + bits[1].trim();
					
						// not interested in decomposition to multiple chars
					
					if ( !to_cp.contains( " " )){
						
						try{
							int cp1 = Integer.parseInt( from_cp, 16 );
							int cp2 = Integer.parseInt( to_cp, 16 );
							
							if ( ( cp2 <= 0x17f || ( cp2 >= 0x390 && cp2 <= 0x4ff ) ) && cp1 >= 0xa0 ){
								
								int cpos = line.indexOf( '#' );
								
								out.println( "{" + String.format("0x%04X,0x%04X", cp1, cp2) + "}," + (cpos>0?("\t//\t" + line.substring( cpos+1 ).trim()):""));
							}
						}catch( Throwable e ){
							System.out.println( "Fail: " + line );
						}
					}
				}
			}
			
			out.close();
			
		}catch( Throwable e ){
			
			e.printStackTrace();
		}
	}
	*/
}
