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
	
	private static Map<Integer,Integer> confusable_map;
	private static Map<String,String>	confusable_recent;
	
	public static String
	getConfusableEquivalent(
		String		str )
	{
		synchronized( GeneralUtils.class ){
			
			if ( confusable_map == null ){
	
				int[][] map = new  int[][]{
					{160,32},	//	(  →   ) NO-BREAK SPACE → SPACE	#	//+ was removed in commit a6b552df, but should have been retained because NBSP is visually completely indistinct from regular SP and can easily end up in file names via copypasta from web pages
					{8232,32},	//	* (  →   ) LINE SEPARATOR → SPACE	#
					{8233,32},	//	* (  →   ) PARAGRAPH SEPARATOR → SPACE	#
					{5760,32},	//	* (   →   ) OGHAM SPACE MARK → SPACE	#
					{8192,32},	//	* (   →   ) EN QUAD → SPACE	#
					{8193,32},	//	* (   →   ) EM QUAD → SPACE	#
					{8194,32},	//	* (   →   ) EN SPACE → SPACE	#
					{8195,32},	//	* (   →   ) EM SPACE → SPACE	#
					{8196,32},	//	* (   →   ) THREE-PER-EM SPACE → SPACE	#
					{8197,32},	//	* (   →   ) FOUR-PER-EM SPACE → SPACE	#
					{8198,32},	//	* (   →   ) SIX-PER-EM SPACE → SPACE	#
					{8200,32},	//	* (   →   ) PUNCTUATION SPACE → SPACE	#
					{8201,32},	//	* (   →   ) THIN SPACE → SPACE	#
					{8202,32},	//	* (   →   ) HAIR SPACE → SPACE	#
					{8287,32},	//	* (   →   ) MEDIUM MATHEMATICAL SPACE → SPACE	#
					{8199,32},	//	* (   →   ) FIGURE SPACE → SPACE	#
					{8239,32},	//	* (   →   ) NARROW NO-BREAK SPACE → SPACE	#
					{2042,95},	//	( ‎ߺ‎ → _ ) NKO LAJANYALAN → LOW LINE	#
					//+ the following 3 are commented out because if anyone is perverted enough to use them they likely know what they are doing
					//{65101,95},	//	( ﹍ → _ ) DASHED LOW LINE → LOW LINE	#
					//{65102,95},	//	( ﹎ → _ ) CENTRELINE LOW LINE → LOW LINE	#
					//{65103,95},	//	( ﹏ → _ ) WAVY LOW LINE → LOW LINE	#
					{8208,45},	//	* ( ‐ → - ) HYPHEN → HYPHEN-MINUS	#
					{8209,45},	//	* ( ‑ → - ) NON-BREAKING HYPHEN → HYPHEN-MINUS	#
					{8210,45},	//	* ( ‒ → - ) FIGURE DASH → HYPHEN-MINUS	#
					{8211,45},	//	* ( – → - ) EN DASH → HYPHEN-MINUS	#
					{65112,45},	//	* ( ﹘ → - ) SMALL EM DASH → HYPHEN-MINUS	#
					//{1748,45},	//	* ( ‎۔‎ → - ) ARABIC FULL STOP → HYPHEN-MINUS	# →‐→	//+ apparently specific to Urdu and probably not confused with hyphen by native users
					{8259,45},	//	* ( ⁃ → - ) HYPHEN BULLET → HYPHEN-MINUS	# →‐→
					{727,45},	//	* ( ˗ → - ) MODIFIER LETTER MINUS SIGN → HYPHEN-MINUS	#
					{8722,45},	//	* ( − → - ) MINUS SIGN → HYPHEN-MINUS	#
					{10134,45},	//	* ( ➖ → - ) HEAVY MINUS SIGN → HYPHEN-MINUS	# →−→
					//{11450,45},	//	( Ⲻ → - ) COPTIC CAPITAL LETTER DIALECT-P NI → HYPHEN-MINUS	# →‒→	//+ could do more harm than good
					//{1549,44},	//	* ( ‎؍‎ → , ) ARABIC DATE SEPARATOR → COMMA	# →‎٫‎→	//+ Arabic users probably will not appreciate conflating this character with ARABIC DECIMAL SEPARATOR, others are unlikely to encounter this character anyway
					{1643,44},	//	* ( ‎٫‎ → , ) ARABIC DECIMAL SEPARATOR → COMMA	#
					{8218,44},	//	* ( ‚ → , ) SINGLE LOW-9 QUOTATION MARK → COMMA	#
					//{42233,44},	//	( ꓹ → , ) LISU LETTER TONE NA PO → COMMA	#	//+ could do more harm than good
					{894,59},	//	* ( ; → ; ) GREEK QUESTION MARK → SEMICOLON	#
					{2307,58},	//	( ः → : ) DEVANAGARI SIGN VISARGA → COLON	#
					{2691,58},	//	( ઃ → : ) GUJARATI SIGN VISARGA → COLON	#
					{65306,58},	//	* ( ： → : ) FULLWIDTH COLON → COLON	# →︰→
					{1417,58},	//	* ( ։ → : ) ARMENIAN FULL STOP → COLON	#
					{1795,58},	//	* ( ‎܃‎ → : ) SYRIAC SUPRALINEAR COLON → COLON	#
					{1796,58},	//	* ( ‎܄‎ → : ) SYRIAC SUBLINEAR COLON → COLON	#
					{5868,58},	//	* ( ᛬ → : ) RUNIC MULTIPLE PUNCTUATION → COLON	#
					{65072,58},	//	* ( ︰ → : ) PRESENTATION FORM FOR VERTICAL TWO DOT LEADER → COLON	#
					{6147,58},	//	* ( ᠃ → : ) MONGOLIAN FULL STOP → COLON	#
					{6153,58},	//	* ( ᠉ → : ) MONGOLIAN MANCHU FULL STOP → COLON	#
					{8282,58},	//	* ( ⁚ → : ) TWO DOT PUNCTUATION → COLON	#
					{1475,58},	//	* ( ‎׃‎ → : ) HEBREW PUNCTUATION SOF PASUQ → COLON	#
					{760,58},	//	* ( ˸ → : ) MODIFIER LETTER RAISED COLON → COLON	#
					{42889,58},	//	* ( ꞉ → : ) MODIFIER LETTER COLON → COLON	#
					{8758,58},	//	* ( ∶ → : ) RATIO → COLON	#
					{720,58},	//	( ː → : ) MODIFIER LETTER TRIANGULAR COLON → COLON	#
					{42237,58},	//	( ꓽ → : ) LISU LETTER TONE MYA JEU → COLON	#
					{65281,33},	//	* ( ！ → ! ) FULLWIDTH EXCLAMATION MARK → EXCLAMATION MARK	# →ǃ→
					{451,33},	//	( ǃ → ! ) LATIN LETTER RETROFLEX CLICK → EXCLAMATION MARK	#
					//{11601,33},	//	( ⵑ → ! ) TIFINAGH LETTER TUAREG YANG → EXCLAMATION MARK	#	//+ could do more harm than good
					{660,63},	//	( ʔ → ? ) LATIN LETTER GLOTTAL STOP → QUESTION MARK	#
					{577,63},	//	( Ɂ → ? ) LATIN CAPITAL LETTER GLOTTAL STOP → QUESTION MARK	# →ʔ→
					//{2429,63},	//	( ॽ → ? ) DEVANAGARI LETTER GLOTTAL STOP → QUESTION MARK	#	//+ could do more harm than good
					//{5038,63},	//	( Ꭾ → ? ) CHEROKEE LETTER HE → QUESTION MARK	# →Ɂ→→ʔ→	//+ could do more harm than good
					//{42731,63},	//	( ꛫ → ? ) BAMUM LETTER NTUU → QUESTION MARK	# →ʔ→	//+ could do more harm than good
					{119149,46},	//	( 𝅭 → . ) MUSICAL SYMBOL COMBINING AUGMENTATION DOT → FULL STOP	#
					{8228,46},	//	* ( ․ → . ) ONE DOT LEADER → FULL STOP	#
					{1793,46},	//	* ( ‎܁‎ → . ) SYRIAC SUPRALINEAR FULL STOP → FULL STOP	#
					{1794,46},	//	* ( ‎܂‎ → . ) SYRIAC SUBLINEAR FULL STOP → FULL STOP	#
					{42510,46},	//	* ( ꘎ → . ) VAI FULL STOP → FULL STOP	#
					{68176,46},	//	* ( ‎𐩐‎ → . ) KHAROSHTHI PUNCTUATION DOT → FULL STOP	#
					//{1632,46},	//	( ‎٠‎ → . ) ARABIC-INDIC DIGIT ZERO → FULL STOP	#	//+ users of Arabic script are unlikely to appreciate conflating these two
					//{1776,46},	//	( ۰ → . ) EXTENDED ARABIC-INDIC DIGIT ZERO → FULL STOP	# →‎٠‎→	//+ users of Arabic script are unlikely to appreciate conflating these two
					//{42232,46},	//	( ꓸ → . ) LISU LETTER TONE MYA TI → FULL STOP	#	//+ could do more harm than good
					{12539,183},	//	* ( ・ → · ) KATAKANA MIDDLE DOT → MIDDLE DOT	# →•→
					{65381,183},	//	* ( ･ → · ) HALFWIDTH KATAKANA MIDDLE DOT → MIDDLE DOT	# →•→
					{5867,183},	//	* ( ᛫ → · ) RUNIC SINGLE PUNCTUATION → MIDDLE DOT	#
					{903,183},	//	( · → · ) GREEK ANO TELEIA → MIDDLE DOT	#
					{11825,183},	//	* ( ⸱ → · ) WORD SEPARATOR MIDDLE DOT → MIDDLE DOT	#
					{65793,183},	//	* ( 𐄁 → · ) AEGEAN WORD SEPARATOR DOT → MIDDLE DOT	#
					{8226,183},	//	* ( • → · ) BULLET → MIDDLE DOT	#
					{8231,183},	//	* ( ‧ → · ) HYPHENATION POINT → MIDDLE DOT	#
					{8729,183},	//	* ( ∙ → · ) BULLET OPERATOR → MIDDLE DOT	#
					{8901,183},	//	* ( ⋅ → · ) DOT OPERATOR → MIDDLE DOT	#
					{42895,183},	//	( ꞏ → · ) LATIN LETTER SINOLOGICAL DOT → MIDDLE DOT	#
					{5159,183},	//	( ᐧ → · ) CANADIAN SYLLABICS FINAL MIDDLE DOT → MIDDLE DOT	#
					{1373,39},	//	* ( ՝ → ' ) ARMENIAN COMMA → APOSTROPHE	# →ˋ→→｀→→‘→
					{65287,39},	//	* ( ＇ → ' ) FULLWIDTH APOSTROPHE → APOSTROPHE	# →’→
					{8216,39},	//	* ( ‘ → ' ) LEFT SINGLE QUOTATION MARK → APOSTROPHE	#
					{8217,39},	//	* ( ’ → ' ) RIGHT SINGLE QUOTATION MARK → APOSTROPHE	#
					{8219,39},	//	* ( ‛ → ' ) SINGLE HIGH-REVERSED-9 QUOTATION MARK → APOSTROPHE	# →′→
					{8242,39},	//	* ( ′ → ' ) PRIME → APOSTROPHE	#
					{1370,39},	//	* ( ՚ → ' ) ARMENIAN APOSTROPHE → APOSTROPHE	# →’→
					{1523,39},	//	* ( ‎׳‎ → ' ) HEBREW PUNCTUATION GERESH → APOSTROPHE	#
					{180,39},	//	( ´ → ' ) ACUTE ACCENT → APOSTROPHE	#	//+ was removed in commit a6b552df, but should have been retained (unlike {96, 39} which is kept out of this table) because ACUTE ACCENT is occasionally used as a substitute for APOSTROPHE on websites that do not allow the latter in file names
					{900,39},	//	* ( ΄ → ' ) GREEK TONOS → APOSTROPHE	# →ʹ→
					{8189,39},	//	* ( ´ → ' ) GREEK OXIA → APOSTROPHE	# →´→→΄→→ʹ→
					{8125,39},	//	* ( ᾽ → ' ) GREEK KORONIS → APOSTROPHE	# →’→
					{8127,39},	//	* ( ᾿ → ' ) GREEK PSILI → APOSTROPHE	# →’→
					{697,39},	//	( ʹ → ' ) MODIFIER LETTER PRIME → APOSTROPHE	#
					{884,39},	//	( ʹ → ' ) GREEK NUMERAL SIGN → APOSTROPHE	# →′→
					{712,39},	//	( ˈ → ' ) MODIFIER LETTER VERTICAL LINE → APOSTROPHE	#
					{714,39},	//	( ˊ → ' ) MODIFIER LETTER ACUTE ACCENT → APOSTROPHE	# →ʹ→→′→
					{699,39},	//	( ʻ → ' ) MODIFIER LETTER TURNED COMMA → APOSTROPHE	# →‘→
					{701,39},	//	( ʽ → ' ) MODIFIER LETTER REVERSED COMMA → APOSTROPHE	# →‘→
					{700,39},	//	( ʼ → ' ) MODIFIER LETTER APOSTROPHE → APOSTROPHE	# →′→
					{702,39},	//	( ʾ → ' ) MODIFIER LETTER RIGHT HALF RING → APOSTROPHE	# →ʼ→→′→
					{42892,39},	//	( ꞌ → ' ) LATIN SMALL LETTER SALTILLO → APOSTROPHE	#
					{1497,39},	//	( ‎י‎ → ' ) HEBREW LETTER YOD → APOSTROPHE	#
					{2036,39},	//	( ‎ߴ‎ → ' ) NKO HIGH TONE APOSTROPHE → APOSTROPHE	# →’→
					{2037,39},	//	( ‎ߵ‎ → ' ) NKO LOW TONE APOSTROPHE → APOSTROPHE	# →‘→
					//{5194,39},	//	( ᑊ → ' ) CANADIAN SYLLABICS WEST-CREE P → APOSTROPHE	# →ˈ→	//+ could do more harm than good
					//{5836,39},	//	( ᛌ → ' ) RUNIC LETTER SHORT-TWIG-SOL S → APOSTROPHE	#	//+ could do more harm than good
					//{94033,39},	//	( 𖽑 → ' ) MIAO SIGN ASPIRATION → APOSTROPHE	# →ʼ→→′→	//+ could do more harm than good
					//{94034,39},	//	( 𖽒 → ' ) MIAO SIGN REFORMED VOICING → APOSTROPHE	# →ʻ→→‘→	//+ could do more harm than good
					//+ The following 6 characters were originally mapped to APOSTROPHE, but have been changed to map to GRAVE ACCENT because the latter is no longer mapped to the former.
					{8245,96},	//	* ( ‵ → ` ) REVERSED PRIME → GRAVE ACCENT	#
					{8175,96},	//	* ( ` → ` ) GREEK VARIA → GRAVE ACCENT	#
					{65344,96},	//	* ( ｀ → ` ) FULLWIDTH GRAVE ACCENT → GRAVE ACCENT	#
					{8190,96},	//	* ( ῾ → ` ) GREEK DASIA → GRAVE ACCENT	#
					{715,96},	//	( ˋ → ` ) MODIFIER LETTER GRAVE ACCENT → GRAVE ACCENT	#
					{756,96},	//	* ( ˴ → ` ) MODIFIER LETTER MIDDLE GRAVE ACCENT → GRAVE ACCENT	#
					//+
					{65339,91},	//	* ( ［ → [ ) FULLWIDTH LEFT SQUARE BRACKET → LEFT SQUARE BRACKET #	//+ originally mapped to LEFT PARENTHESIS but that makes no sense
					{65288,40},	//	* ( （ → ( ) FULLWIDTH LEFT PARENTHESIS → LEFT PARENTHESIS	#	//+ this, on the other hand, makes perfect sense
					{10088,40},	//	* ( ❨ → ( ) MEDIUM LEFT PARENTHESIS ORNAMENT → LEFT PARENTHESIS	#
					{10098,40},	//	* ( ❲ → ( ) LIGHT LEFT TORTOISE SHELL BRACKET ORNAMENT → LEFT PARENTHESIS	# →〔→
					{12308,40},	//	* ( 〔 → ( ) LEFT TORTOISE SHELL BRACKET → LEFT PARENTHESIS	#
					{64830,40},	//	* ( ﴾ → ( ) ORNATE LEFT PARENTHESIS → LEFT PARENTHESIS	#
					{65341,93},	//	* ( ］ → ] ) FULLWIDTH RIGHT SQUARE BRACKET → RIGHT SQUARE BRACKET #	//+ originally mapped to RIGHT PARENTHESIS but that makes no sense
					{65289,41},	//	* ( ） → ) ) FULLWIDTH RIGHT PARENTHESIS → RIGHT PARENTHESIS	#	//+ this, on the other hand, makes perfect sense
					{10089,41},	//	* ( ❩ → ) ) MEDIUM RIGHT PARENTHESIS ORNAMENT → RIGHT PARENTHESIS	#
					{10099,41},	//	* ( ❳ → ) ) LIGHT RIGHT TORTOISE SHELL BRACKET ORNAMENT → RIGHT PARENTHESIS	# →〕→
					{12309,41},	//	* ( 〕 → ) ) RIGHT TORTOISE SHELL BRACKET → RIGHT PARENTHESIS	#
					{64831,41},	//	* ( ﴿ → ) ) ORNATE RIGHT PARENTHESIS → RIGHT PARENTHESIS	#
					{10100,123},	//	* ( ❴ → { ) MEDIUM LEFT CURLY BRACKET ORNAMENT → LEFT CURLY BRACKET	#
					{119060,123},	//	* ( 𝄔 → { ) MUSICAL SYMBOL BRACE → LEFT CURLY BRACKET	#
					{10101,125},	//	* ( ❵ → } ) MEDIUM RIGHT CURLY BRACKET ORNAMENT → RIGHT CURLY BRACKET	#
					{11839,182},	//	* ( ⸿ → ¶ ) CAPITULUM → PILCROW SIGN	#
					{8270,42},	//	* ( ⁎ → * ) LOW ASTERISK → ASTERISK	#
					{1645,42},	//	* ( ‎٭‎ → * ) ARABIC FIVE POINTED STAR → ASTERISK	#
					{8727,42},	//	* ( ∗ → * ) ASTERISK OPERATOR → ASTERISK	#
					{66335,42},	//	( 𐌟 → * ) OLD ITALIC LETTER ESS → ASTERISK	#	//+ could do more harm than good
					{5941,47},	//	* ( ᜵ → / ) PHILIPPINE SINGLE PUNCTUATION → SOLIDUS	#
					//{8257,47},	//	* ( ⁁ → / ) CARET INSERTION POINT → SOLIDUS	#	//+ one would have to be totally nuts to use the former as a substitute for the latter
					{8725,47},	//	* ( ∕ → / ) DIVISION SLASH → SOLIDUS	#
					{8260,47},	//	* ( ⁄ → / ) FRACTION SLASH → SOLIDUS	#
					{9585,47},	//	* ( ╱ → / ) BOX DRAWINGS LIGHT DIAGONAL UPPER RIGHT TO LOWER LEFT → SOLIDUS	#
					{10187,47},	//	* ( ⟋ → / ) MATHEMATICAL RISING DIAGONAL → SOLIDUS	#
					{10744,47},	//	* ( ⧸ → / ) BIG SOLIDUS → SOLIDUS	#
					{119354,47},	//	* ( 𝈺 → / ) GREEK INSTRUMENTAL NOTATION SYMBOL-47 → SOLIDUS	#
					//{12755,47},	//	* ( ㇓ → / ) CJK STROKE SP → SOLIDUS	# →⼃→	//+ unlikely to be used as a substitute for SOLIDUS, or confused with it
					{12339,47},	//	( 〳 → / ) VERTICAL KANA REPEAT MARK UPPER HALF → SOLIDUS	#
					{11462,47},	//	( Ⳇ → / ) COPTIC CAPITAL LETTER OLD COPTIC ESH → SOLIDUS	#
					//{12494,47},	//	( ノ → / ) KATAKANA LETTER NO → SOLIDUS	# →⼃→	//+ unlikely to be used as a substitute for SOLIDUS, or confused with it
					//{20031,47},	//	( 丿 → / ) CJK UNIFIED IDEOGRAPH-4E3F → SOLIDUS	# →⼃→	//+ unlikely to be used as a substitute for SOLIDUS, or confused with it
					{12035,47},	//	* ( ⼃ → / ) KANGXI RADICAL SLASH → SOLIDUS	#
					{65340,92},	//	* ( ＼ → \ ) FULLWIDTH REVERSE SOLIDUS → REVERSE SOLIDUS	# →∖→
					{65128,92},	//	* ( ﹨ → \ ) SMALL REVERSE SOLIDUS → REVERSE SOLIDUS	# →∖→
					{8726,92},	//	* ( ∖ → \ ) SET MINUS → REVERSE SOLIDUS	#
					{10189,92},	//	* ( ⟍ → \ ) MATHEMATICAL FALLING DIAGONAL → REVERSE SOLIDUS	#
					{10741,92},	//	* ( ⧵ → \ ) REVERSE SOLIDUS OPERATOR → REVERSE SOLIDUS	#
					{10745,92},	//	* ( ⧹ → \ ) BIG REVERSE SOLIDUS → REVERSE SOLIDUS	#
					{119311,92},	//	* ( 𝈏 → \ ) GREEK VOCAL NOTATION SYMBOL-16 → REVERSE SOLIDUS	#
					{119355,92},	//	* ( 𝈻 → \ ) GREEK INSTRUMENTAL NOTATION SYMBOL-48 → REVERSE SOLIDUS	# →𝈏→
					{12756,92},	//	* ( ㇔ → \ ) CJK STROKE D → REVERSE SOLIDUS	# →⼂→
					{20022,92},	//	( 丶 → \ ) CJK UNIFIED IDEOGRAPH-4E36 → REVERSE SOLIDUS	# →⼂→	//+ unlikely to be used as a substitute for REVERSE SOLIDUS, or confused with it
					{12034,92},	//	* ( ⼂ → \ ) KANGXI RADICAL DOT → REVERSE SOLIDUS	#
					{42872,38},	//	( ꝸ → & ) LATIN SMALL LETTER UM → AMPERSAND	#
					{708,94},	//	* ( ˄ → ^ ) MODIFIER LETTER UP ARROWHEAD → CIRCUMFLEX ACCENT	#
					{710,94},	//	( ˆ → ^ ) MODIFIER LETTER CIRCUMFLEX ACCENT → CIRCUMFLEX ACCENT	#
					{9400,169},	//	* ( Ⓒ → © ) CIRCLED LATIN CAPITAL LETTER C → COPYRIGHT SIGN	#
					{9415,174},	//	* ( Ⓡ → ® ) CIRCLED LATIN CAPITAL LETTER R → REGISTERED SIGN	#
					{5869,43},	//	* ( ᛭ → + ) RUNIC CROSS PUNCTUATION → PLUS SIGN	#
					{10133,43},	//	* ( ➕ → + ) HEAVY PLUS SIGN → PLUS SIGN	#
					{66203,43},	//	( 𐊛 → + ) LYCIAN LETTER H → PLUS SIGN	#	//+ could do more harm than good
					{10135,247},	//	* ( ➗ → ÷ ) HEAVY DIVISION SIGN → DIVISION SIGN	#
					{8249,60},	//	* ( ‹ → < ) SINGLE LEFT-POINTING ANGLE QUOTATION MARK → LESS-THAN SIGN	#
					{10094,60},	//	* ( ❮ → < ) HEAVY LEFT-POINTING ANGLE QUOTATION MARK ORNAMENT → LESS-THAN SIGN	# →‹→
					{706,60},	//	* ( ˂ → < ) MODIFIER LETTER LEFT ARROWHEAD → LESS-THAN SIGN	#
					{119350,60},	//	* ( 𝈶 → < ) GREEK INSTRUMENTAL NOTATION SYMBOL-40 → LESS-THAN SIGN	#
					//{5176,60},	//	( ᐸ → < ) CANADIAN SYLLABICS PA → LESS-THAN SIGN	#	//+ could do more harm than good
					//{5810,60},	//	( ᚲ → < ) RUNIC LETTER KAUNA → LESS-THAN SIGN	#	//+ could do more harm than good
					{5120,61},	//	* ( ᐀ → = ) CANADIAN SYLLABICS HYPHEN → EQUALS SIGN	#
					{11840,61},	//	* ( ⹀ → = ) DOUBLE HYPHEN → EQUALS SIGN	#
					{12448,61},	//	* ( ゠ → = ) KATAKANA-HIRAGANA DOUBLE HYPHEN → EQUALS SIGN	#
					{42239,61},	//	* ( ꓿ → = ) LISU PUNCTUATION FULL STOP → EQUALS SIGN	#
					{8250,62},	//	* ( › → > ) SINGLE RIGHT-POINTING ANGLE QUOTATION MARK → GREATER-THAN SIGN	#
					{10095,62},	//	* ( ❯ → > ) HEAVY RIGHT-POINTING ANGLE QUOTATION MARK ORNAMENT → GREATER-THAN SIGN	# →›→
					{707,62},	//	* ( ˃ → > ) MODIFIER LETTER RIGHT ARROWHEAD → GREATER-THAN SIGN	#
					{119351,62},	//	* ( 𝈷 → > ) GREEK INSTRUMENTAL NOTATION SYMBOL-42 → GREATER-THAN SIGN	#
					//{5171,62},	//	( ᐳ → > ) CANADIAN SYLLABICS PO → GREATER-THAN SIGN	#	//+ could do more harm than good
					//{94015,62},	//	( 𖼿 → > ) MIAO LETTER ARCHAIC ZZA → GREATER-THAN SIGN	#	//+ could do more harm than good
					{8275,126},	//	* ( ⁓ → ~ ) SWUNG DASH → TILDE	#
					{732,126},	//	* ( ˜ → ~ ) SMALL TILDE → TILDE	#
					{8128,126},	//	* ( ῀ → ~ ) GREEK PERISPOMENI → TILDE	# →˜→
					{8764,126},	//	* ( ∼ → ~ ) TILDE OPERATOR → TILDE	#
					{8356,163},	//	* ( ₤ → £ ) LIRA SIGN → POUND SIGN	#
					{120784,50},	//	( 𝟐 → 2 ) MATHEMATICAL BOLD DIGIT TWO → DIGIT TWO	#
					{120794,50},	//	( 𝟚 → 2 ) MATHEMATICAL DOUBLE-STRUCK DIGIT TWO → DIGIT TWO	#
					{120804,50},	//	( 𝟤 → 2 ) MATHEMATICAL SANS-SERIF DIGIT TWO → DIGIT TWO	#
					{120814,50},	//	( 𝟮 → 2 ) MATHEMATICAL SANS-SERIF BOLD DIGIT TWO → DIGIT TWO	#
					{120824,50},	//	( 𝟸 → 2 ) MATHEMATICAL MONOSPACE DIGIT TWO → DIGIT TWO	#
					{130034,50},	//	( 🯲 → 2 ) SEGMENTED DIGIT TWO → DIGIT TWO	#
					//{42842,50},	//	( Ꝛ → 2 ) LATIN CAPITAL LETTER R ROTUNDA → DIGIT TWO	#	//+ would break case-insensitive matching
					//{423,50},	//	( Ƨ → 2 ) LATIN CAPITAL LETTER TONE TWO → DIGIT TWO	#	//+ would break case-insensitive matching
					//{1000,50},	//	( Ϩ → 2 ) COPTIC CAPITAL LETTER HORI → DIGIT TWO	# →Ƨ→	//+ would break case-insensitive matching
					//{42564,50},	//	( Ꙅ → 2 ) CYRILLIC CAPITAL LETTER REVERSED DZE → DIGIT TWO	# →Ƨ→	//+ would break case-insensitive matching
					//{5311,50},	//	( ᒿ → 2 ) CANADIAN SYLLABICS SAYISI M → DIGIT TWO	#	//+ could do more harm than good
					//{42735,50},	//	( ꛯ → 2 ) BAMUM LETTER KOGHOM → DIGIT TWO	# →Ƨ→	//+ could do more harm than good
					{119302,51},	//	* ( 𝈆 → 3 ) GREEK VOCAL NOTATION SYMBOL-7 → DIGIT THREE	#
					{120785,51},	//	( 𝟑 → 3 ) MATHEMATICAL BOLD DIGIT THREE → DIGIT THREE	#
					{120795,51},	//	( 𝟛 → 3 ) MATHEMATICAL DOUBLE-STRUCK DIGIT THREE → DIGIT THREE	#
					{120805,51},	//	( 𝟥 → 3 ) MATHEMATICAL SANS-SERIF DIGIT THREE → DIGIT THREE	#
					{120815,51},	//	( 𝟯 → 3 ) MATHEMATICAL SANS-SERIF BOLD DIGIT THREE → DIGIT THREE	#
					{120825,51},	//	( 𝟹 → 3 ) MATHEMATICAL MONOSPACE DIGIT THREE → DIGIT THREE	#
					{130035,51},	//	( 🯳 → 3 ) SEGMENTED DIGIT THREE → DIGIT THREE	#
					//{42923,51},	//	( Ɜ → 3 ) LATIN CAPITAL LETTER REVERSED OPEN E → DIGIT THREE	#	//+ would break case-insensitive matching
					//{540,51},	//	( Ȝ → 3 ) LATIN CAPITAL LETTER YOGH → DIGIT THREE	# →Ʒ→	//+ would break case-insensitive matching
					//{439,51},	//	( Ʒ → 3 ) LATIN CAPITAL LETTER EZH → DIGIT THREE	#	//+ would break case-insensitive matching
					//{42858,51},	//	( Ꝫ → 3 ) LATIN CAPITAL LETTER ET → DIGIT THREE	#	//+ would break case-insensitive matching
					//{11468,51},	//	( Ⳍ → 3 ) COPTIC CAPITAL LETTER OLD COPTIC HORI → DIGIT THREE	# →Ȝ→→Ʒ→	//+ would break case-insensitive matching
					//{1047,51},	//	( З → 3 ) CYRILLIC CAPITAL LETTER ZE → DIGIT THREE	#	//+ would break case-insensitive matching
					//{1248,51},	//	( Ӡ → 3 ) CYRILLIC CAPITAL LETTER ABKHASIAN DZE → DIGIT THREE	# →Ʒ→	//+ would break case-insensitive matching
					//{94011,51},	//	( 𖼻 → 3 ) MIAO LETTER ZA → DIGIT THREE	# →Ʒ→	//+ could do more harm than good
					//{71882,51},	//	( 𑣊 → 3 ) WARANG CITI SMALL LETTER ANG → DIGIT THREE	#	//+ would break case-insensitive matching
					{120786,52},	//	( 𝟒 → 4 ) MATHEMATICAL BOLD DIGIT FOUR → DIGIT FOUR	#
					{120796,52},	//	( 𝟜 → 4 ) MATHEMATICAL DOUBLE-STRUCK DIGIT FOUR → DIGIT FOUR	#
					{120806,52},	//	( 𝟦 → 4 ) MATHEMATICAL SANS-SERIF DIGIT FOUR → DIGIT FOUR	#
					{120816,52},	//	( 𝟰 → 4 ) MATHEMATICAL SANS-SERIF BOLD DIGIT FOUR → DIGIT FOUR	#
					{120826,52},	//	( 𝟺 → 4 ) MATHEMATICAL MONOSPACE DIGIT FOUR → DIGIT FOUR	#
					{130036,52},	//	( 🯴 → 4 ) SEGMENTED DIGIT FOUR → DIGIT FOUR	#
					//{5070,52},	//	( Ꮞ → 4 ) CHEROKEE LETTER SE → DIGIT FOUR	#	//+ could do more harm than good
					//{71855,52},	//	( 𑢯 → 4 ) WARANG CITI CAPITAL LETTER UC → DIGIT FOUR	#	//+ would break case-insensitive matching
					{120787,53},	//	( 𝟓 → 5 ) MATHEMATICAL BOLD DIGIT FIVE → DIGIT FIVE	#
					{120797,53},	//	( 𝟝 → 5 ) MATHEMATICAL DOUBLE-STRUCK DIGIT FIVE → DIGIT FIVE	#
					{120807,53},	//	( 𝟧 → 5 ) MATHEMATICAL SANS-SERIF DIGIT FIVE → DIGIT FIVE	#
					{120817,53},	//	( 𝟱 → 5 ) MATHEMATICAL SANS-SERIF BOLD DIGIT FIVE → DIGIT FIVE	#
					{120827,53},	//	( 𝟻 → 5 ) MATHEMATICAL MONOSPACE DIGIT FIVE → DIGIT FIVE	#
					{130037,53},	//	( 🯵 → 5 ) SEGMENTED DIGIT FIVE → DIGIT FIVE	#
					{444,53},	//	( Ƽ → 5 ) LATIN CAPITAL LETTER TONE FIVE → DIGIT FIVE	#
					//{71867,53},	//	( 𑢻 → 5 ) WARANG CITI CAPITAL LETTER HORR → DIGIT FIVE	#	//+ would break case-insensitive matching
					{120788,54},	//	( 𝟔 → 6 ) MATHEMATICAL BOLD DIGIT SIX → DIGIT SIX	#
					{120798,54},	//	( 𝟞 → 6 ) MATHEMATICAL DOUBLE-STRUCK DIGIT SIX → DIGIT SIX	#
					{120808,54},	//	( 𝟨 → 6 ) MATHEMATICAL SANS-SERIF DIGIT SIX → DIGIT SIX	#
					{120818,54},	//	( 𝟲 → 6 ) MATHEMATICAL SANS-SERIF BOLD DIGIT SIX → DIGIT SIX	#
					{120828,54},	//	( 𝟼 → 6 ) MATHEMATICAL MONOSPACE DIGIT SIX → DIGIT SIX	#
					{130038,54},	//	( 🯶 → 6 ) SEGMENTED DIGIT SIX → DIGIT SIX	#
					//{11474,54},	//	( Ⳓ → 6 ) COPTIC CAPITAL LETTER OLD COPTIC HEI → DIGIT SIX	#	//+ would break case-insensitive matching
					//{1073,54},	//	( б → 6 ) CYRILLIC SMALL LETTER BE → DIGIT SIX	#	//+ would break case-insensitive matching
					//{5102,54},	//	( Ꮾ → 6 ) CHEROKEE LETTER WV → DIGIT SIX	#	//+ could do more harm than good
					//{71893,54},	//	( 𑣕 → 6 ) WARANG CITI SMALL LETTER AT → DIGIT SIX	#	//+ would break case-insensitive matching
					{119314,55},	//	* ( 𝈒 → 7 ) GREEK VOCAL NOTATION SYMBOL-19 → DIGIT SEVEN	#
					{120789,55},	//	( 𝟕 → 7 ) MATHEMATICAL BOLD DIGIT SEVEN → DIGIT SEVEN	#
					{120799,55},	//	( 𝟟 → 7 ) MATHEMATICAL DOUBLE-STRUCK DIGIT SEVEN → DIGIT SEVEN	#
					{120809,55},	//	( 𝟩 → 7 ) MATHEMATICAL SANS-SERIF DIGIT SEVEN → DIGIT SEVEN	#
					{120819,55},	//	( 𝟳 → 7 ) MATHEMATICAL SANS-SERIF BOLD DIGIT SEVEN → DIGIT SEVEN	#
					{120829,55},	//	( 𝟽 → 7 ) MATHEMATICAL MONOSPACE DIGIT SEVEN → DIGIT SEVEN	#
					{130039,55},	//	( 🯷 → 7 ) SEGMENTED DIGIT SEVEN → DIGIT SEVEN	#
					//{66770,55},	//	( 𐓒 → 7 ) OSAGE CAPITAL LETTER ZA → DIGIT SEVEN	#	//+ would break case-insensitive matching
					//{71878,55},	//	( 𑣆 → 7 ) WARANG CITI SMALL LETTER II → DIGIT SEVEN	#	//+ would break case-insensitive matching
					//{2819,56},	//	( ଃ → 8 ) ORIYA SIGN VISARGA → DIGIT EIGHT	#	//+ could do more harm than good
					//{2538,56},	//	( ৪ → 8 ) BENGALI DIGIT FOUR → DIGIT EIGHT	#	//+ could do more harm than good
					//{2666,56},	//	( ੪ → 8 ) GURMUKHI DIGIT FOUR → DIGIT EIGHT	#	//+ could do more harm than good
					//{125131,56},	//	* ( ‎𞣋‎ → 8 ) MENDE KIKAKUI DIGIT FIVE → DIGIT EIGHT	#	//+ could do more harm than good
					{120790,56},	//	( 𝟖 → 8 ) MATHEMATICAL BOLD DIGIT EIGHT → DIGIT EIGHT	#
					{120800,56},	//	( 𝟠 → 8 ) MATHEMATICAL DOUBLE-STRUCK DIGIT EIGHT → DIGIT EIGHT	#
					{120810,56},	//	( 𝟪 → 8 ) MATHEMATICAL SANS-SERIF DIGIT EIGHT → DIGIT EIGHT	#
					{120820,56},	//	( 𝟴 → 8 ) MATHEMATICAL SANS-SERIF BOLD DIGIT EIGHT → DIGIT EIGHT	#
					{120830,56},	//	( 𝟾 → 8 ) MATHEMATICAL MONOSPACE DIGIT EIGHT → DIGIT EIGHT	#
					{130040,56},	//	( 🯸 → 8 ) SEGMENTED DIGIT EIGHT → DIGIT EIGHT	#
					//{547,56},	//	( ȣ → 8 ) LATIN SMALL LETTER OU → DIGIT EIGHT	#	//+ could do more harm than good
					//{546,56},	//	( Ȣ → 8 ) LATIN CAPITAL LETTER OU → DIGIT EIGHT	#	//+ could do more harm than good
					//{66330,56},	//	( 𐌚 → 8 ) OLD ITALIC LETTER EF → DIGIT EIGHT	#	//+ could do more harm than good
					//{2663,57},	//	( ੧ → 9 ) GURMUKHI DIGIT ONE → DIGIT NINE	#	//+ could do more harm than good
					//{2920,57},	//	( ୨ → 9 ) ORIYA DIGIT TWO → DIGIT NINE	#	//+ could do more harm than good
					//{2541,57},	//	( ৭ → 9 ) BENGALI DIGIT SEVEN → DIGIT NINE	#	//+ could do more harm than good
					//{3437,57},	//	( ൭ → 9 ) MALAYALAM DIGIT SEVEN → DIGIT NINE	#	//+ could do more harm than good; also quite unlikely to occur in real world outside of the specific script
					{120791,57},	//	( 𝟗 → 9 ) MATHEMATICAL BOLD DIGIT NINE → DIGIT NINE	#
					{120801,57},	//	( 𝟡 → 9 ) MATHEMATICAL DOUBLE-STRUCK DIGIT NINE → DIGIT NINE	#
					{120811,57},	//	( 𝟫 → 9 ) MATHEMATICAL SANS-SERIF DIGIT NINE → DIGIT NINE	#
					{120821,57},	//	( 𝟵 → 9 ) MATHEMATICAL SANS-SERIF BOLD DIGIT NINE → DIGIT NINE	#
					{120831,57},	//	( 𝟿 → 9 ) MATHEMATICAL MONOSPACE DIGIT NINE → DIGIT NINE	#
					{130041,57},	//	( 🯹 → 9 ) SEGMENTED DIGIT NINE → DIGIT NINE	#
					//{42862,57},	//	( Ꝯ → 9 ) LATIN CAPITAL LETTER CON → DIGIT NINE	#	//+ would break case-insensitive matching
					//{11466,57},	//	( Ⳋ → 9 ) COPTIC CAPITAL LETTER DIALECT-P HORI → DIGIT NINE	#	//+ would break case-insensitive matching
					//{71884,57},	//	( 𑣌 → 9 ) WARANG CITI SMALL LETTER KO → DIGIT NINE	#	//+ could do more harm than good
					//{71852,57},	//	( 𑢬 → 9 ) WARANG CITI CAPITAL LETTER KO → DIGIT NINE	#	//+ could do more harm than good
					//{71894,57},	//	( 𑣖 → 9 ) WARANG CITI SMALL LETTER AM → DIGIT NINE	#	//+ would break case-insensitive matching
					{9082,97},	//	* ( ⍺ → a ) APL FUNCTIONAL SYMBOL ALPHA → LATIN SMALL LETTER A	# →α→
					{65345,97},	//	( ａ → a ) FULLWIDTH LATIN SMALL LETTER A → LATIN SMALL LETTER A	# →а→
					{119834,97},	//	( 𝐚 → a ) MATHEMATICAL BOLD SMALL A → LATIN SMALL LETTER A	#
					{119886,97},	//	( 𝑎 → a ) MATHEMATICAL ITALIC SMALL A → LATIN SMALL LETTER A	#
					{119938,97},	//	( 𝒂 → a ) MATHEMATICAL BOLD ITALIC SMALL A → LATIN SMALL LETTER A	#
					{119990,97},	//	( 𝒶 → a ) MATHEMATICAL SCRIPT SMALL A → LATIN SMALL LETTER A	#
					{120042,97},	//	( 𝓪 → a ) MATHEMATICAL BOLD SCRIPT SMALL A → LATIN SMALL LETTER A	#
					{120094,97},	//	( 𝔞 → a ) MATHEMATICAL FRAKTUR SMALL A → LATIN SMALL LETTER A	#
					{120146,97},	//	( 𝕒 → a ) MATHEMATICAL DOUBLE-STRUCK SMALL A → LATIN SMALL LETTER A	#
					{120198,97},	//	( 𝖆 → a ) MATHEMATICAL BOLD FRAKTUR SMALL A → LATIN SMALL LETTER A	#
					{120250,97},	//	( 𝖺 → a ) MATHEMATICAL SANS-SERIF SMALL A → LATIN SMALL LETTER A	#
					{120302,97},	//	( 𝗮 → a ) MATHEMATICAL SANS-SERIF BOLD SMALL A → LATIN SMALL LETTER A	#
					{120354,97},	//	( 𝘢 → a ) MATHEMATICAL SANS-SERIF ITALIC SMALL A → LATIN SMALL LETTER A	#
					{120406,97},	//	( 𝙖 → a ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL A → LATIN SMALL LETTER A	#
					{120458,97},	//	( 𝚊 → a ) MATHEMATICAL MONOSPACE SMALL A → LATIN SMALL LETTER A	#
					{593,97},	//	( ɑ → a ) LATIN SMALL LETTER ALPHA → LATIN SMALL LETTER A	#
					{945,97},	//	( α → a ) GREEK SMALL LETTER ALPHA → LATIN SMALL LETTER A	#
					{120514,97},	//	( 𝛂 → a ) MATHEMATICAL BOLD SMALL ALPHA → LATIN SMALL LETTER A	# →α→
					{120572,97},	//	( 𝛼 → a ) MATHEMATICAL ITALIC SMALL ALPHA → LATIN SMALL LETTER A	# →α→
					{120630,97},	//	( 𝜶 → a ) MATHEMATICAL BOLD ITALIC SMALL ALPHA → LATIN SMALL LETTER A	# →α→
					{120688,97},	//	( 𝝰 → a ) MATHEMATICAL SANS-SERIF BOLD SMALL ALPHA → LATIN SMALL LETTER A	# →α→
					{120746,97},	//	( 𝞪 → a ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL ALPHA → LATIN SMALL LETTER A	# →α→
					{1072,97},	//	( а → a ) CYRILLIC SMALL LETTER A → LATIN SMALL LETTER A	#
					{65313,65},	//	( Ａ → A ) FULLWIDTH LATIN CAPITAL LETTER A → LATIN CAPITAL LETTER A	# →А→
					{119808,65},	//	( 𝐀 → A ) MATHEMATICAL BOLD CAPITAL A → LATIN CAPITAL LETTER A	#
					{119860,65},	//	( 𝐴 → A ) MATHEMATICAL ITALIC CAPITAL A → LATIN CAPITAL LETTER A	#
					{119912,65},	//	( 𝑨 → A ) MATHEMATICAL BOLD ITALIC CAPITAL A → LATIN CAPITAL LETTER A	#
					{119964,65},	//	( 𝒜 → A ) MATHEMATICAL SCRIPT CAPITAL A → LATIN CAPITAL LETTER A	#
					{120016,65},	//	( 𝓐 → A ) MATHEMATICAL BOLD SCRIPT CAPITAL A → LATIN CAPITAL LETTER A	#
					{120068,65},	//	( 𝔄 → A ) MATHEMATICAL FRAKTUR CAPITAL A → LATIN CAPITAL LETTER A	#
					{120120,65},	//	( 𝔸 → A ) MATHEMATICAL DOUBLE-STRUCK CAPITAL A → LATIN CAPITAL LETTER A	#
					{120172,65},	//	( 𝕬 → A ) MATHEMATICAL BOLD FRAKTUR CAPITAL A → LATIN CAPITAL LETTER A	#
					{120224,65},	//	( 𝖠 → A ) MATHEMATICAL SANS-SERIF CAPITAL A → LATIN CAPITAL LETTER A	#
					{120276,65},	//	( 𝗔 → A ) MATHEMATICAL SANS-SERIF BOLD CAPITAL A → LATIN CAPITAL LETTER A	#
					{120328,65},	//	( 𝘈 → A ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL A → LATIN CAPITAL LETTER A	#
					{120380,65},	//	( 𝘼 → A ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL A → LATIN CAPITAL LETTER A	#
					{120432,65},	//	( 𝙰 → A ) MATHEMATICAL MONOSPACE CAPITAL A → LATIN CAPITAL LETTER A	#
					{11373,65},	//	( Ɑ → A ) LATIN CAPITAL LETTER ALPHA → LATIN CAPITAL LETTER A	#	//+ needed to preserve case insensitivity
					{913,65},	//	( Α → A ) GREEK CAPITAL LETTER ALPHA → LATIN CAPITAL LETTER A	#
					{120488,65},	//	( 𝚨 → A ) MATHEMATICAL BOLD CAPITAL ALPHA → LATIN CAPITAL LETTER A	# →𝐀→
					{120546,65},	//	( 𝛢 → A ) MATHEMATICAL ITALIC CAPITAL ALPHA → LATIN CAPITAL LETTER A	# →Α→
					{120604,65},	//	( 𝜜 → A ) MATHEMATICAL BOLD ITALIC CAPITAL ALPHA → LATIN CAPITAL LETTER A	# →Α→
					{120662,65},	//	( 𝝖 → A ) MATHEMATICAL SANS-SERIF BOLD CAPITAL ALPHA → LATIN CAPITAL LETTER A	# →Α→
					{120720,65},	//	( 𝞐 → A ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL ALPHA → LATIN CAPITAL LETTER A	# →Α→
					{1040,65},	//	( А → A ) CYRILLIC CAPITAL LETTER A → LATIN CAPITAL LETTER A	#
					//{5034,65},	//	( Ꭺ → A ) CHEROKEE LETTER GO → LATIN CAPITAL LETTER A	#	//+ would break case-insensitive matching
					//+ The 4 characters below are from caseless scripts; having them match Latin lower-case a would be rather annoying to any user of those scripts.
					//{5573,65},	//	( ᗅ → A ) CANADIAN SYLLABICS CARRIER GHO → LATIN CAPITAL LETTER A	#
					//{42222,65},	//	( ꓮ → A ) LISU LETTER A → LATIN CAPITAL LETTER A	#
					//{94016,65},	//	( 𖽀 → A ) MIAO LETTER ZZYA → LATIN CAPITAL LETTER A	#
					//{66208,65},	//	( 𐊠 → A ) CARIAN LETTER A → LATIN CAPITAL LETTER A	#
					{551,229},	//	( ȧ → å ) LATIN SMALL LETTER A WITH DOT ABOVE → LATIN SMALL LETTER A WITH RING ABOVE	#
					{550,197},	//	( Ȧ → Å ) LATIN CAPITAL LETTER A WITH DOT ABOVE → LATIN CAPITAL LETTER A WITH RING ABOVE	#
					{119835,98},	//	( 𝐛 → b ) MATHEMATICAL BOLD SMALL B → LATIN SMALL LETTER B	#
					{119887,98},	//	( 𝑏 → b ) MATHEMATICAL ITALIC SMALL B → LATIN SMALL LETTER B	#
					{119939,98},	//	( 𝒃 → b ) MATHEMATICAL BOLD ITALIC SMALL B → LATIN SMALL LETTER B	#
					{119991,98},	//	( 𝒷 → b ) MATHEMATICAL SCRIPT SMALL B → LATIN SMALL LETTER B	#
					{120043,98},	//	( 𝓫 → b ) MATHEMATICAL BOLD SCRIPT SMALL B → LATIN SMALL LETTER B	#
					{120095,98},	//	( 𝔟 → b ) MATHEMATICAL FRAKTUR SMALL B → LATIN SMALL LETTER B	#
					{120147,98},	//	( 𝕓 → b ) MATHEMATICAL DOUBLE-STRUCK SMALL B → LATIN SMALL LETTER B	#
					{120199,98},	//	( 𝖇 → b ) MATHEMATICAL BOLD FRAKTUR SMALL B → LATIN SMALL LETTER B	#
					{120251,98},	//	( 𝖻 → b ) MATHEMATICAL SANS-SERIF SMALL B → LATIN SMALL LETTER B	#
					{120303,98},	//	( 𝗯 → b ) MATHEMATICAL SANS-SERIF BOLD SMALL B → LATIN SMALL LETTER B	#
					{120355,98},	//	( 𝘣 → b ) MATHEMATICAL SANS-SERIF ITALIC SMALL B → LATIN SMALL LETTER B	#
					{120407,98},	//	( 𝙗 → b ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL B → LATIN SMALL LETTER B	#
					{120459,98},	//	( 𝚋 → b ) MATHEMATICAL MONOSPACE SMALL B → LATIN SMALL LETTER B	#
					{388,98},	//	( Ƅ → b ) LATIN CAPITAL LETTER TONE SIX → LATIN SMALL LETTER B	#
					//{1068,98},	//	( Ь → b ) CYRILLIC CAPITAL LETTER SOFT SIGN → LATIN SMALL LETTER B	# →Ƅ→	//+ would break case-insensitive matching
					//{5071,98},	//	( Ꮟ → b ) CHEROKEE LETTER SI → LATIN SMALL LETTER B	#	//+ would break case-insensitive matching
					//+ The 2 characters below are from a caseless script; having them match Latin upper-case B would be rather annoying to any user of those scripts.
					//+ Also, any users of the Canadian syllabics script would not appreciate us conflating two of their letters, even if they both do look like variants of b to us.
					//{5234,98},	//	( ᑲ → b ) CANADIAN SYLLABICS KA → LATIN SMALL LETTER B	#
					//{5551,98},	//	( ᖯ → b ) CANADIAN SYLLABICS AIVILIK B → LATIN SMALL LETTER B	#
					{65314,66},	//	( Ｂ → B ) FULLWIDTH LATIN CAPITAL LETTER B → LATIN CAPITAL LETTER B	# →Β→
					{8492,66},	//	( ℬ → B ) SCRIPT CAPITAL B → LATIN CAPITAL LETTER B	#
					{119809,66},	//	( 𝐁 → B ) MATHEMATICAL BOLD CAPITAL B → LATIN CAPITAL LETTER B	#
					{119861,66},	//	( 𝐵 → B ) MATHEMATICAL ITALIC CAPITAL B → LATIN CAPITAL LETTER B	#
					{119913,66},	//	( 𝑩 → B ) MATHEMATICAL BOLD ITALIC CAPITAL B → LATIN CAPITAL LETTER B	#
					{120017,66},	//	( 𝓑 → B ) MATHEMATICAL BOLD SCRIPT CAPITAL B → LATIN CAPITAL LETTER B	#
					{120069,66},	//	( 𝔅 → B ) MATHEMATICAL FRAKTUR CAPITAL B → LATIN CAPITAL LETTER B	#
					{120121,66},	//	( 𝔹 → B ) MATHEMATICAL DOUBLE-STRUCK CAPITAL B → LATIN CAPITAL LETTER B	#
					{120173,66},	//	( 𝕭 → B ) MATHEMATICAL BOLD FRAKTUR CAPITAL B → LATIN CAPITAL LETTER B	#
					{120225,66},	//	( 𝖡 → B ) MATHEMATICAL SANS-SERIF CAPITAL B → LATIN CAPITAL LETTER B	#
					{120277,66},	//	( 𝗕 → B ) MATHEMATICAL SANS-SERIF BOLD CAPITAL B → LATIN CAPITAL LETTER B	#
					{120329,66},	//	( 𝘉 → B ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL B → LATIN CAPITAL LETTER B	#
					{120381,66},	//	( 𝘽 → B ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL B → LATIN CAPITAL LETTER B	#
					{120433,66},	//	( 𝙱 → B ) MATHEMATICAL MONOSPACE CAPITAL B → LATIN CAPITAL LETTER B	#
					//+ Unfortunately, while upper-case Greek Beta (Β) and Cyrillic Ve (В) are often indistinguishable from Latin upper-case B,
					//+ mapping them would break case-insensitive matching unless we also added mappings from lower-case Greek beta (β) and Cyrillic ve (в) to lower-case Latin b,
					//+ which would be quite confusing because those lower-case letters look quite distinct.
					//{42932,66},	//	( Ꞵ → B ) LATIN CAPITAL LETTER BETA → LATIN CAPITAL LETTER B	#
					//{914,66},	//	( Β → B ) GREEK CAPITAL LETTER BETA → LATIN CAPITAL LETTER B	#
					//+ Mathematical symbols do not have case mappings, so the issue does not occur for those.
					//+ Still, it would be nice to be able to match MATHEMATICAL BOLD CAPITAL BETA and GREEK CAPITAL LETTER BETA, for example...
					{120489,66},	//	( 𝚩 → B ) MATHEMATICAL BOLD CAPITAL BETA → LATIN CAPITAL LETTER B	# →Β→
					{120547,66},	//	( 𝛣 → B ) MATHEMATICAL ITALIC CAPITAL BETA → LATIN CAPITAL LETTER B	# →Β→
					{120605,66},	//	( 𝜝 → B ) MATHEMATICAL BOLD ITALIC CAPITAL BETA → LATIN CAPITAL LETTER B	# →Β→
					{120663,66},	//	( 𝝗 → B ) MATHEMATICAL SANS-SERIF BOLD CAPITAL BETA → LATIN CAPITAL LETTER B	# →Β→
					{120721,66},	//	( 𝞑 → B ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL BETA → LATIN CAPITAL LETTER B	# →Β→
					{1042,66},	//	( В → B ) CYRILLIC CAPITAL LETTER VE → LATIN CAPITAL LETTER B	#
					//+ The 6 characters below are from caseless scripts; having them match Latin lower-case b would be rather annoying to any user of those scripts.
					//{5108,66},	//	( Ᏼ → B ) CHEROKEE LETTER YV → LATIN CAPITAL LETTER B	#
					//{5623,66},	//	( ᗷ → B ) CANADIAN SYLLABICS CARRIER KHE → LATIN CAPITAL LETTER B	#
					//{42192,66},	//	( ꓐ → B ) LISU LETTER BA → LATIN CAPITAL LETTER B	#
					//{66178,66},	//	( 𐊂 → B ) LYCIAN LETTER B → LATIN CAPITAL LETTER B	#
					//{66209,66},	//	( 𐊡 → B ) CARIAN LETTER P2 → LATIN CAPITAL LETTER B	#
					//{66305,66},	//	( 𐌁 → B ) OLD ITALIC LETTER BE → LATIN CAPITAL LETTER B	#
					//+ There are many more examples of this problem below; they all fall into the "could do more harm than good" category.
					{65347,99},	//	( ｃ → c ) FULLWIDTH LATIN SMALL LETTER C → LATIN SMALL LETTER C	# →с→
					{8573,99},	//	( ⅽ → c ) SMALL ROMAN NUMERAL ONE HUNDRED → LATIN SMALL LETTER C	#
					{119836,99},	//	( 𝐜 → c ) MATHEMATICAL BOLD SMALL C → LATIN SMALL LETTER C	#
					{119888,99},	//	( 𝑐 → c ) MATHEMATICAL ITALIC SMALL C → LATIN SMALL LETTER C	#
					{119940,99},	//	( 𝒄 → c ) MATHEMATICAL BOLD ITALIC SMALL C → LATIN SMALL LETTER C	#
					{119992,99},	//	( 𝒸 → c ) MATHEMATICAL SCRIPT SMALL C → LATIN SMALL LETTER C	#
					{120044,99},	//	( 𝓬 → c ) MATHEMATICAL BOLD SCRIPT SMALL C → LATIN SMALL LETTER C	#
					{120096,99},	//	( 𝔠 → c ) MATHEMATICAL FRAKTUR SMALL C → LATIN SMALL LETTER C	#
					{120148,99},	//	( 𝕔 → c ) MATHEMATICAL DOUBLE-STRUCK SMALL C → LATIN SMALL LETTER C	#
					{120200,99},	//	( 𝖈 → c ) MATHEMATICAL BOLD FRAKTUR SMALL C → LATIN SMALL LETTER C	#
					{120252,99},	//	( 𝖼 → c ) MATHEMATICAL SANS-SERIF SMALL C → LATIN SMALL LETTER C	#
					{120304,99},	//	( 𝗰 → c ) MATHEMATICAL SANS-SERIF BOLD SMALL C → LATIN SMALL LETTER C	#
					{120356,99},	//	( 𝘤 → c ) MATHEMATICAL SANS-SERIF ITALIC SMALL C → LATIN SMALL LETTER C	#
					{120408,99},	//	( 𝙘 → c ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL C → LATIN SMALL LETTER C	#
					{120460,99},	//	( 𝚌 → c ) MATHEMATICAL MONOSPACE SMALL C → LATIN SMALL LETTER C	#
					{7428,99},	//	( ᴄ → c ) LATIN LETTER SMALL CAPITAL C → LATIN SMALL LETTER C	#
					{1010,99},	//	( ϲ → c ) GREEK LUNATE SIGMA SYMBOL → LATIN SMALL LETTER C	#
					{11429,99},	//	( ⲥ → c ) COPTIC SMALL LETTER SIMA → LATIN SMALL LETTER C	# →ϲ→
					{1089,99},	//	( с → c ) CYRILLIC SMALL LETTER ES → LATIN SMALL LETTER C	#
					{43951,99},	//	( ꮯ → c ) CHEROKEE SMALL LETTER TLI → LATIN SMALL LETTER C	# →ᴄ→
					{66621,99},	//	( 𐐽 → c ) DESERET SMALL LETTER CHEE → LATIN SMALL LETTER C	#
					{128844,67},	//	* ( 🝌 → C ) ALCHEMICAL SYMBOL FOR CALX → LATIN CAPITAL LETTER C	#
					//{71922,67},	//	* ( 𑣲 → C ) WARANG CITI NUMBER NINETY → LATIN CAPITAL LETTER C	#	//+ could do more harm than good
					//{71913,67},	//	( 𑣩 → C ) WARANG CITI DIGIT NINE → LATIN CAPITAL LETTER C	#	//+ could do more harm than good
					{65315,67},	//	( Ｃ → C ) FULLWIDTH LATIN CAPITAL LETTER C → LATIN CAPITAL LETTER C	# →С→
					{8557,67},	//	( Ⅽ → C ) ROMAN NUMERAL ONE HUNDRED → LATIN CAPITAL LETTER C	#
					{8450,67},	//	( ℂ → C ) DOUBLE-STRUCK CAPITAL C → LATIN CAPITAL LETTER C	#
					{8493,67},	//	( ℭ → C ) BLACK-LETTER CAPITAL C → LATIN CAPITAL LETTER C	#
					{119810,67},	//	( 𝐂 → C ) MATHEMATICAL BOLD CAPITAL C → LATIN CAPITAL LETTER C	#
					{119862,67},	//	( 𝐶 → C ) MATHEMATICAL ITALIC CAPITAL C → LATIN CAPITAL LETTER C	#
					{119914,67},	//	( 𝑪 → C ) MATHEMATICAL BOLD ITALIC CAPITAL C → LATIN CAPITAL LETTER C	#
					{119966,67},	//	( 𝒞 → C ) MATHEMATICAL SCRIPT CAPITAL C → LATIN CAPITAL LETTER C	#
					{120018,67},	//	( 𝓒 → C ) MATHEMATICAL BOLD SCRIPT CAPITAL C → LATIN CAPITAL LETTER C	#
					{120174,67},	//	( 𝕮 → C ) MATHEMATICAL BOLD FRAKTUR CAPITAL C → LATIN CAPITAL LETTER C	#
					{120226,67},	//	( 𝖢 → C ) MATHEMATICAL SANS-SERIF CAPITAL C → LATIN CAPITAL LETTER C	#
					{120278,67},	//	( 𝗖 → C ) MATHEMATICAL SANS-SERIF BOLD CAPITAL C → LATIN CAPITAL LETTER C	#
					{120330,67},	//	( 𝘊 → C ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL C → LATIN CAPITAL LETTER C	#
					{120382,67},	//	( 𝘾 → C ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL C → LATIN CAPITAL LETTER C	#
					{120434,67},	//	( 𝙲 → C ) MATHEMATICAL MONOSPACE CAPITAL C → LATIN CAPITAL LETTER C	#
					{1017,67},	//	( Ϲ → C ) GREEK CAPITAL LUNATE SIGMA SYMBOL → LATIN CAPITAL LETTER C	#
					{11428,67},	//	( Ⲥ → C ) COPTIC CAPITAL LETTER SIMA → LATIN CAPITAL LETTER C	# →Ϲ→
					{1057,67},	//	( С → C ) CYRILLIC CAPITAL LETTER ES → LATIN CAPITAL LETTER C	#
					{5087,67},	//	( Ꮯ → C ) CHEROKEE LETTER TLI → LATIN CAPITAL LETTER C	#
					//{42202,67},	//	( ꓚ → C ) LISU LETTER CA → LATIN CAPITAL LETTER C	#	//+ could do more harm than good
					//{66210,67},	//	( 𐊢 → C ) CARIAN LETTER D → LATIN CAPITAL LETTER C	#	//+ could do more harm than good
					//{66306,67},	//	( 𐌂 → C ) OLD ITALIC LETTER KE → LATIN CAPITAL LETTER C	#	//+ could do more harm than good
					{66581,67},	//	( 𐐕 → C ) DESERET CAPITAL LETTER CHEE → LATIN CAPITAL LETTER C	#
					//{66844,67},	//	( 𐔜 → C ) ELBASAN LETTER SHE → LATIN CAPITAL LETTER C	#	//+ could do more harm than good
					{8574,100},	//	( ⅾ → d ) SMALL ROMAN NUMERAL FIVE HUNDRED → LATIN SMALL LETTER D	#
					{8518,100},	//	( ⅆ → d ) DOUBLE-STRUCK ITALIC SMALL D → LATIN SMALL LETTER D	#
					{119837,100},	//	( 𝐝 → d ) MATHEMATICAL BOLD SMALL D → LATIN SMALL LETTER D	#
					{119889,100},	//	( 𝑑 → d ) MATHEMATICAL ITALIC SMALL D → LATIN SMALL LETTER D	#
					{119941,100},	//	( 𝒅 → d ) MATHEMATICAL BOLD ITALIC SMALL D → LATIN SMALL LETTER D	#
					{119993,100},	//	( 𝒹 → d ) MATHEMATICAL SCRIPT SMALL D → LATIN SMALL LETTER D	#
					{120045,100},	//	( 𝓭 → d ) MATHEMATICAL BOLD SCRIPT SMALL D → LATIN SMALL LETTER D	#
					{120097,100},	//	( 𝔡 → d ) MATHEMATICAL FRAKTUR SMALL D → LATIN SMALL LETTER D	#
					{120149,100},	//	( 𝕕 → d ) MATHEMATICAL DOUBLE-STRUCK SMALL D → LATIN SMALL LETTER D	#
					{120201,100},	//	( 𝖉 → d ) MATHEMATICAL BOLD FRAKTUR SMALL D → LATIN SMALL LETTER D	#
					{120253,100},	//	( 𝖽 → d ) MATHEMATICAL SANS-SERIF SMALL D → LATIN SMALL LETTER D	#
					{120305,100},	//	( 𝗱 → d ) MATHEMATICAL SANS-SERIF BOLD SMALL D → LATIN SMALL LETTER D	#
					{120357,100},	//	( 𝘥 → d ) MATHEMATICAL SANS-SERIF ITALIC SMALL D → LATIN SMALL LETTER D	#
					{120409,100},	//	( 𝙙 → d ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL D → LATIN SMALL LETTER D	#
					{120461,100},	//	( 𝚍 → d ) MATHEMATICAL MONOSPACE SMALL D → LATIN SMALL LETTER D	#
					//{1281,100},	//	( ԁ → d ) CYRILLIC SMALL LETTER KOMI DE → LATIN SMALL LETTER D	#	//+ would break case-insensitive matching
					//{5095,100},	//	( Ꮷ → d ) CHEROKEE LETTER TSU → LATIN SMALL LETTER D	#	//+ would break case-insensitive matching
					//{5231,100},	//	( ᑯ → d ) CANADIAN SYLLABICS KO → LATIN SMALL LETTER D	#	//+ could do more harm than good
					//{42194,100},	//	( ꓒ → d ) LISU LETTER PHA → LATIN SMALL LETTER D	#	//+ could do more harm than good
					{8558,68},	//	( Ⅾ → D ) ROMAN NUMERAL FIVE HUNDRED → LATIN CAPITAL LETTER D	#
					{8517,68},	//	( ⅅ → D ) DOUBLE-STRUCK ITALIC CAPITAL D → LATIN CAPITAL LETTER D	#
					{119811,68},	//	( 𝐃 → D ) MATHEMATICAL BOLD CAPITAL D → LATIN CAPITAL LETTER D	#
					{119863,68},	//	( 𝐷 → D ) MATHEMATICAL ITALIC CAPITAL D → LATIN CAPITAL LETTER D	#
					{119915,68},	//	( 𝑫 → D ) MATHEMATICAL BOLD ITALIC CAPITAL D → LATIN CAPITAL LETTER D	#
					{119967,68},	//	( 𝒟 → D ) MATHEMATICAL SCRIPT CAPITAL D → LATIN CAPITAL LETTER D	#
					{120019,68},	//	( 𝓓 → D ) MATHEMATICAL BOLD SCRIPT CAPITAL D → LATIN CAPITAL LETTER D	#
					{120071,68},	//	( 𝔇 → D ) MATHEMATICAL FRAKTUR CAPITAL D → LATIN CAPITAL LETTER D	#
					{120123,68},	//	( 𝔻 → D ) MATHEMATICAL DOUBLE-STRUCK CAPITAL D → LATIN CAPITAL LETTER D	#
					{120175,68},	//	( 𝕯 → D ) MATHEMATICAL BOLD FRAKTUR CAPITAL D → LATIN CAPITAL LETTER D	#
					{120227,68},	//	( 𝖣 → D ) MATHEMATICAL SANS-SERIF CAPITAL D → LATIN CAPITAL LETTER D	#
					{120279,68},	//	( 𝗗 → D ) MATHEMATICAL SANS-SERIF BOLD CAPITAL D → LATIN CAPITAL LETTER D	#
					{120331,68},	//	( 𝘋 → D ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL D → LATIN CAPITAL LETTER D	#
					{120383,68},	//	( 𝘿 → D ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL D → LATIN CAPITAL LETTER D	#
					{120435,68},	//	( 𝙳 → D ) MATHEMATICAL MONOSPACE CAPITAL D → LATIN CAPITAL LETTER D	#
					//{5024,68},	//	( Ꭰ → D ) CHEROKEE LETTER A → LATIN CAPITAL LETTER D	#	//+ would break case-insensitive matching
					//{5598,68},	//	( ᗞ → D ) CANADIAN SYLLABICS CARRIER THE → LATIN CAPITAL LETTER D	#	//+ could do more harm than good
					//{5610,68},	//	( ᗪ → D ) CANADIAN SYLLABICS CARRIER PE → LATIN CAPITAL LETTER D	# →ᗞ→	//+ could do more harm than good
					//{42195,68},	//	( ꓓ → D ) LISU LETTER DA → LATIN CAPITAL LETTER D	#	//+ could do more harm than good
					{8494,101},	//	( ℮ → e ) ESTIMATED SYMBOL → LATIN SMALL LETTER E	#
					{65349,101},	//	( ｅ → e ) FULLWIDTH LATIN SMALL LETTER E → LATIN SMALL LETTER E	# →е→
					{8495,101},	//	( ℯ → e ) SCRIPT SMALL E → LATIN SMALL LETTER E	#
					{8519,101},	//	( ⅇ → e ) DOUBLE-STRUCK ITALIC SMALL E → LATIN SMALL LETTER E	#
					{119838,101},	//	( 𝐞 → e ) MATHEMATICAL BOLD SMALL E → LATIN SMALL LETTER E	#
					{119890,101},	//	( 𝑒 → e ) MATHEMATICAL ITALIC SMALL E → LATIN SMALL LETTER E	#
					{119942,101},	//	( 𝒆 → e ) MATHEMATICAL BOLD ITALIC SMALL E → LATIN SMALL LETTER E	#
					{120046,101},	//	( 𝓮 → e ) MATHEMATICAL BOLD SCRIPT SMALL E → LATIN SMALL LETTER E	#
					{120098,101},	//	( 𝔢 → e ) MATHEMATICAL FRAKTUR SMALL E → LATIN SMALL LETTER E	#
					{120150,101},	//	( 𝕖 → e ) MATHEMATICAL DOUBLE-STRUCK SMALL E → LATIN SMALL LETTER E	#
					{120202,101},	//	( 𝖊 → e ) MATHEMATICAL BOLD FRAKTUR SMALL E → LATIN SMALL LETTER E	#
					{120254,101},	//	( 𝖾 → e ) MATHEMATICAL SANS-SERIF SMALL E → LATIN SMALL LETTER E	#
					{120306,101},	//	( 𝗲 → e ) MATHEMATICAL SANS-SERIF BOLD SMALL E → LATIN SMALL LETTER E	#
					{120358,101},	//	( 𝘦 → e ) MATHEMATICAL SANS-SERIF ITALIC SMALL E → LATIN SMALL LETTER E	#
					{120410,101},	//	( 𝙚 → e ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL E → LATIN SMALL LETTER E	#
					{120462,101},	//	( 𝚎 → e ) MATHEMATICAL MONOSPACE SMALL E → LATIN SMALL LETTER E	#
					{43826,101},	//	( ꬲ → e ) LATIN SMALL LETTER BLACKLETTER E → LATIN SMALL LETTER E	#
					{1077,101},	//	( е → e ) CYRILLIC SMALL LETTER IE → LATIN SMALL LETTER E	#
					//{1213,101},	//	( ҽ → e ) CYRILLIC SMALL LETTER ABKHASIAN CHE → LATIN SMALL LETTER E	#	//+ would break case-insensitive matching
					{8959,69},	//	* ( ⋿ → E ) Z NOTATION BAG MEMBERSHIP → LATIN CAPITAL LETTER E	#
					{65317,69},	//	( Ｅ → E ) FULLWIDTH LATIN CAPITAL LETTER E → LATIN CAPITAL LETTER E	# →Ε→
					{8496,69},	//	( ℰ → E ) SCRIPT CAPITAL E → LATIN CAPITAL LETTER E	#
					{119812,69},	//	( 𝐄 → E ) MATHEMATICAL BOLD CAPITAL E → LATIN CAPITAL LETTER E	#
					{119864,69},	//	( 𝐸 → E ) MATHEMATICAL ITALIC CAPITAL E → LATIN CAPITAL LETTER E	#
					{119916,69},	//	( 𝑬 → E ) MATHEMATICAL BOLD ITALIC CAPITAL E → LATIN CAPITAL LETTER E	#
					{120020,69},	//	( 𝓔 → E ) MATHEMATICAL BOLD SCRIPT CAPITAL E → LATIN CAPITAL LETTER E	#
					{120072,69},	//	( 𝔈 → E ) MATHEMATICAL FRAKTUR CAPITAL E → LATIN CAPITAL LETTER E	#
					{120124,69},	//	( 𝔼 → E ) MATHEMATICAL DOUBLE-STRUCK CAPITAL E → LATIN CAPITAL LETTER E	#
					{120176,69},	//	( 𝕰 → E ) MATHEMATICAL BOLD FRAKTUR CAPITAL E → LATIN CAPITAL LETTER E	#
					{120228,69},	//	( 𝖤 → E ) MATHEMATICAL SANS-SERIF CAPITAL E → LATIN CAPITAL LETTER E	#
					{120280,69},	//	( 𝗘 → E ) MATHEMATICAL SANS-SERIF BOLD CAPITAL E → LATIN CAPITAL LETTER E	#
					{120332,69},	//	( 𝘌 → E ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL E → LATIN CAPITAL LETTER E	#
					{120384,69},	//	( 𝙀 → E ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL E → LATIN CAPITAL LETTER E	#
					{120436,69},	//	( 𝙴 → E ) MATHEMATICAL MONOSPACE CAPITAL E → LATIN CAPITAL LETTER E	#
					//{917,69},	//	( Ε → E ) GREEK CAPITAL LETTER EPSILON → LATIN CAPITAL LETTER E	#	//+ would break case-insensitive matching, unfortunately
					{120492,69},	//	( 𝚬 → E ) MATHEMATICAL BOLD CAPITAL EPSILON → LATIN CAPITAL LETTER E	# →𝐄→
					{120550,69},	//	( 𝛦 → E ) MATHEMATICAL ITALIC CAPITAL EPSILON → LATIN CAPITAL LETTER E	# →Ε→
					{120608,69},	//	( 𝜠 → E ) MATHEMATICAL BOLD ITALIC CAPITAL EPSILON → LATIN CAPITAL LETTER E	# →Ε→
					{120666,69},	//	( 𝝚 → E ) MATHEMATICAL SANS-SERIF BOLD CAPITAL EPSILON → LATIN CAPITAL LETTER E	# →Ε→
					{120724,69},	//	( 𝞔 → E ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL EPSILON → LATIN CAPITAL LETTER E	# →Ε→
					{1045,69},	//	( Е → E ) CYRILLIC CAPITAL LETTER IE → LATIN CAPITAL LETTER E	#
					//{11577,69},	//	( ⴹ → E ) TIFINAGH LETTER YADD → LATIN CAPITAL LETTER E	#	//+ could do more harm than good
					//{5036,69},	//	( Ꭼ → E ) CHEROKEE LETTER GV → LATIN CAPITAL LETTER E	#	//+ could do more harm than good
					//{42224,69},	//	( ꓰ → E ) LISU LETTER E → LATIN CAPITAL LETTER E	#	//+ could do more harm than good
					//{71846,69},	//	( 𑢦 → E ) WARANG CITI CAPITAL LETTER II → LATIN CAPITAL LETTER E	#	//+ could do more harm than good
					//{71854,69},	//	( 𑢮 → E ) WARANG CITI CAPITAL LETTER YUJ → LATIN CAPITAL LETTER E	#	//+ could do more harm than good
					//{66182,69},	//	( 𐊆 → E ) LYCIAN LETTER I → LATIN CAPITAL LETTER E	#	//+ could do more harm than good
					{119839,102},	//	( 𝐟 → f ) MATHEMATICAL BOLD SMALL F → LATIN SMALL LETTER F	#
					{119891,102},	//	( 𝑓 → f ) MATHEMATICAL ITALIC SMALL F → LATIN SMALL LETTER F	#
					{119943,102},	//	( 𝒇 → f ) MATHEMATICAL BOLD ITALIC SMALL F → LATIN SMALL LETTER F	#
					{119995,102},	//	( 𝒻 → f ) MATHEMATICAL SCRIPT SMALL F → LATIN SMALL LETTER F	#
					{120047,102},	//	( 𝓯 → f ) MATHEMATICAL BOLD SCRIPT SMALL F → LATIN SMALL LETTER F	#
					{120099,102},	//	( 𝔣 → f ) MATHEMATICAL FRAKTUR SMALL F → LATIN SMALL LETTER F	#
					{120151,102},	//	( 𝕗 → f ) MATHEMATICAL DOUBLE-STRUCK SMALL F → LATIN SMALL LETTER F	#
					{120203,102},	//	( 𝖋 → f ) MATHEMATICAL BOLD FRAKTUR SMALL F → LATIN SMALL LETTER F	#
					{120255,102},	//	( 𝖿 → f ) MATHEMATICAL SANS-SERIF SMALL F → LATIN SMALL LETTER F	#
					{120307,102},	//	( 𝗳 → f ) MATHEMATICAL SANS-SERIF BOLD SMALL F → LATIN SMALL LETTER F	#
					{120359,102},	//	( 𝘧 → f ) MATHEMATICAL SANS-SERIF ITALIC SMALL F → LATIN SMALL LETTER F	#
					{120411,102},	//	( 𝙛 → f ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL F → LATIN SMALL LETTER F	#
					{120463,102},	//	( 𝚏 → f ) MATHEMATICAL MONOSPACE SMALL F → LATIN SMALL LETTER F	#
					{989,102},	//	( ϝ → f ) GREEK SMALL LETTER DIGAMMA → LATIN SMALL LETTER F	#	//+ needed to preserve case insensitivity, by symmetry with GREEK LETTER DIGAMMA → LATIN CAPITAL LETTER F
					{43829,102},	//	( ꬵ → f ) LATIN SMALL LETTER LENIS F → LATIN SMALL LETTER F	#
					{42905,102},	//	( ꞙ → f ) LATIN SMALL LETTER F WITH STROKE → LATIN SMALL LETTER F	#
					//{383,102},	//	( ſ → f ) LATIN SMALL LETTER LONG S → LATIN SMALL LETTER F	#	//+ would break case-insensitive matching
					{7837,102},	//	( ẝ → f ) LATIN SMALL LETTER LONG S WITH HIGH STROKE → LATIN SMALL LETTER F	#
					{1412,102},	//	( ք → f ) ARMENIAN SMALL LETTER KEH → LATIN SMALL LETTER F	#
					{119315,70},	//	* ( 𝈓 → F ) GREEK VOCAL NOTATION SYMBOL-20 → LATIN CAPITAL LETTER F	# →Ϝ→
					{8497,70},	//	( ℱ → F ) SCRIPT CAPITAL F → LATIN CAPITAL LETTER F	#
					{119813,70},	//	( 𝐅 → F ) MATHEMATICAL BOLD CAPITAL F → LATIN CAPITAL LETTER F	#
					{119865,70},	//	( 𝐹 → F ) MATHEMATICAL ITALIC CAPITAL F → LATIN CAPITAL LETTER F	#
					{119917,70},	//	( 𝑭 → F ) MATHEMATICAL BOLD ITALIC CAPITAL F → LATIN CAPITAL LETTER F	#
					{120021,70},	//	( 𝓕 → F ) MATHEMATICAL BOLD SCRIPT CAPITAL F → LATIN CAPITAL LETTER F	#
					{120073,70},	//	( 𝔉 → F ) MATHEMATICAL FRAKTUR CAPITAL F → LATIN CAPITAL LETTER F	#
					{120125,70},	//	( 𝔽 → F ) MATHEMATICAL DOUBLE-STRUCK CAPITAL F → LATIN CAPITAL LETTER F	#
					{120177,70},	//	( 𝕱 → F ) MATHEMATICAL BOLD FRAKTUR CAPITAL F → LATIN CAPITAL LETTER F	#
					{120229,70},	//	( 𝖥 → F ) MATHEMATICAL SANS-SERIF CAPITAL F → LATIN CAPITAL LETTER F	#
					{120281,70},	//	( 𝗙 → F ) MATHEMATICAL SANS-SERIF BOLD CAPITAL F → LATIN CAPITAL LETTER F	#
					{120333,70},	//	( 𝘍 → F ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL F → LATIN CAPITAL LETTER F	#
					{120385,70},	//	( 𝙁 → F ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL F → LATIN CAPITAL LETTER F	#
					{120437,70},	//	( 𝙵 → F ) MATHEMATICAL MONOSPACE CAPITAL F → LATIN CAPITAL LETTER F	#
					{42904,70},	//	( Ꞙ → F ) LATIN CAPITAL LETTER F WITH STROKE → LATIN CAPITAL LETTER F	#
					{988,70},	//	( Ϝ → F ) GREEK LETTER DIGAMMA → LATIN CAPITAL LETTER F	#
					{120778,70},	//	( 𝟊 → F ) MATHEMATICAL BOLD CAPITAL DIGAMMA → LATIN CAPITAL LETTER F	# →Ϝ→
					//{5556,70},	//	( ᖴ → F ) CANADIAN SYLLABICS BLACKFOOT WE → LATIN CAPITAL LETTER F	#	//+ could do more harm than good
					//{42205,70},	//	( ꓝ → F ) LISU LETTER TSA → LATIN CAPITAL LETTER F	#	//+ could do more harm than good
					//{71874,70},	//	( 𑣂 → F ) WARANG CITI SMALL LETTER WI → LATIN CAPITAL LETTER F	#	//+ could do more harm than good
					//{71842,70},	//	( 𑢢 → F ) WARANG CITI CAPITAL LETTER WI → LATIN CAPITAL LETTER F	#	//+ could do more harm than good
					//{66183,70},	//	( 𐊇 → F ) LYCIAN LETTER W → LATIN CAPITAL LETTER F	#	//+ could do more harm than good
					//{66213,70},	//	( 𐊥 → F ) CARIAN LETTER R → LATIN CAPITAL LETTER F	#	//+ could do more harm than good
					//{66853,70},	//	( 𐔥 → F ) ELBASAN LETTER GHE → LATIN CAPITAL LETTER F	#	//+ could do more harm than good
					{65351,103},	//	( ｇ → g ) FULLWIDTH LATIN SMALL LETTER G → LATIN SMALL LETTER G	# →ɡ→
					{8458,103},	//	( ℊ → g ) SCRIPT SMALL G → LATIN SMALL LETTER G	#
					{119840,103},	//	( 𝐠 → g ) MATHEMATICAL BOLD SMALL G → LATIN SMALL LETTER G	#
					{119892,103},	//	( 𝑔 → g ) MATHEMATICAL ITALIC SMALL G → LATIN SMALL LETTER G	#
					{119944,103},	//	( 𝒈 → g ) MATHEMATICAL BOLD ITALIC SMALL G → LATIN SMALL LETTER G	#
					{120048,103},	//	( 𝓰 → g ) MATHEMATICAL BOLD SCRIPT SMALL G → LATIN SMALL LETTER G	#
					{120100,103},	//	( 𝔤 → g ) MATHEMATICAL FRAKTUR SMALL G → LATIN SMALL LETTER G	#
					{120152,103},	//	( 𝕘 → g ) MATHEMATICAL DOUBLE-STRUCK SMALL G → LATIN SMALL LETTER G	#
					{120204,103},	//	( 𝖌 → g ) MATHEMATICAL BOLD FRAKTUR SMALL G → LATIN SMALL LETTER G	#
					{120256,103},	//	( 𝗀 → g ) MATHEMATICAL SANS-SERIF SMALL G → LATIN SMALL LETTER G	#
					{120308,103},	//	( 𝗴 → g ) MATHEMATICAL SANS-SERIF BOLD SMALL G → LATIN SMALL LETTER G	#
					{120360,103},	//	( 𝘨 → g ) MATHEMATICAL SANS-SERIF ITALIC SMALL G → LATIN SMALL LETTER G	#
					{120412,103},	//	( 𝙜 → g ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL G → LATIN SMALL LETTER G	#
					{120464,103},	//	( 𝚐 → g ) MATHEMATICAL MONOSPACE SMALL G → LATIN SMALL LETTER G	#
					{609,103},	//	( ɡ → g ) LATIN SMALL LETTER SCRIPT G → LATIN SMALL LETTER G	#
					{7555,103},	//	( ᶃ → g ) LATIN SMALL LETTER G WITH PALATAL HOOK → LATIN SMALL LETTER G	#
					{397,103},	//	( ƍ → g ) LATIN SMALL LETTER TURNED DELTA → LATIN SMALL LETTER G	#
					//{1409,103},	//	( ց → g ) ARMENIAN SMALL LETTER CO → LATIN SMALL LETTER G	#	//+ would break case-insensitive matching
					{119814,71},	//	( 𝐆 → G ) MATHEMATICAL BOLD CAPITAL G → LATIN CAPITAL LETTER G	#
					{119866,71},	//	( 𝐺 → G ) MATHEMATICAL ITALIC CAPITAL G → LATIN CAPITAL LETTER G	#
					{119918,71},	//	( 𝑮 → G ) MATHEMATICAL BOLD ITALIC CAPITAL G → LATIN CAPITAL LETTER G	#
					{119970,71},	//	( 𝒢 → G ) MATHEMATICAL SCRIPT CAPITAL G → LATIN CAPITAL LETTER G	#
					{120022,71},	//	( 𝓖 → G ) MATHEMATICAL BOLD SCRIPT CAPITAL G → LATIN CAPITAL LETTER G	#
					{120074,71},	//	( 𝔊 → G ) MATHEMATICAL FRAKTUR CAPITAL G → LATIN CAPITAL LETTER G	#
					{120126,71},	//	( 𝔾 → G ) MATHEMATICAL DOUBLE-STRUCK CAPITAL G → LATIN CAPITAL LETTER G	#
					{120178,71},	//	( 𝕲 → G ) MATHEMATICAL BOLD FRAKTUR CAPITAL G → LATIN CAPITAL LETTER G	#
					{120230,71},	//	( 𝖦 → G ) MATHEMATICAL SANS-SERIF CAPITAL G → LATIN CAPITAL LETTER G	#
					{120282,71},	//	( 𝗚 → G ) MATHEMATICAL SANS-SERIF BOLD CAPITAL G → LATIN CAPITAL LETTER G	#
					{120334,71},	//	( 𝘎 → G ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL G → LATIN CAPITAL LETTER G	#
					{120386,71},	//	( 𝙂 → G ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL G → LATIN CAPITAL LETTER G	#
					{120438,71},	//	( 𝙶 → G ) MATHEMATICAL MONOSPACE CAPITAL G → LATIN CAPITAL LETTER G	#
					//{1292,71},	//	( Ԍ → G ) CYRILLIC CAPITAL LETTER KOMI SJE → LATIN CAPITAL LETTER G	#	//+ would break case-insensitive matching
					//{5056,71},	//	( Ꮐ → G ) CHEROKEE LETTER NAH → LATIN CAPITAL LETTER G	#	//+ could do more harm than good
					//{5107,71},	//	( Ᏻ → G ) CHEROKEE LETTER YU → LATIN CAPITAL LETTER G	#	//+ could do more harm than good
					//{42198,71},	//	( ꓖ → G ) LISU LETTER GA → LATIN CAPITAL LETTER G	#	//+ could do more harm than good
					{65352,104},	//	( ｈ → h ) FULLWIDTH LATIN SMALL LETTER H → LATIN SMALL LETTER H	# →һ→
					{8462,104},	//	( ℎ → h ) PLANCK CONSTANT → LATIN SMALL LETTER H	#
					{119841,104},	//	( 𝐡 → h ) MATHEMATICAL BOLD SMALL H → LATIN SMALL LETTER H	#
					{119945,104},	//	( 𝒉 → h ) MATHEMATICAL BOLD ITALIC SMALL H → LATIN SMALL LETTER H	#
					{119997,104},	//	( 𝒽 → h ) MATHEMATICAL SCRIPT SMALL H → LATIN SMALL LETTER H	#
					{120049,104},	//	( 𝓱 → h ) MATHEMATICAL BOLD SCRIPT SMALL H → LATIN SMALL LETTER H	#
					{120101,104},	//	( 𝔥 → h ) MATHEMATICAL FRAKTUR SMALL H → LATIN SMALL LETTER H	#
					{120153,104},	//	( 𝕙 → h ) MATHEMATICAL DOUBLE-STRUCK SMALL H → LATIN SMALL LETTER H	#
					{120205,104},	//	( 𝖍 → h ) MATHEMATICAL BOLD FRAKTUR SMALL H → LATIN SMALL LETTER H	#
					{120257,104},	//	( 𝗁 → h ) MATHEMATICAL SANS-SERIF SMALL H → LATIN SMALL LETTER H	#
					{120309,104},	//	( 𝗵 → h ) MATHEMATICAL SANS-SERIF BOLD SMALL H → LATIN SMALL LETTER H	#
					{120361,104},	//	( 𝘩 → h ) MATHEMATICAL SANS-SERIF ITALIC SMALL H → LATIN SMALL LETTER H	#
					{120413,104},	//	( 𝙝 → h ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL H → LATIN SMALL LETTER H	#
					{120465,104},	//	( 𝚑 → h ) MATHEMATICAL MONOSPACE SMALL H → LATIN SMALL LETTER H	#
					//{1211,104},	//	( һ → h ) CYRILLIC SMALL LETTER SHHA → LATIN SMALL LETTER H	#	//+ would break case-insensitive matching
					//{1392,104},	//	( հ → h ) ARMENIAN SMALL LETTER HO → LATIN SMALL LETTER H	#	//+ would break case-insensitive matching
					//{5058,104},	//	( Ꮒ → h ) CHEROKEE LETTER NI → LATIN SMALL LETTER H	#	//+ would break case-insensitive matching
					{65320,72},	//	( Ｈ → H ) FULLWIDTH LATIN CAPITAL LETTER H → LATIN CAPITAL LETTER H	# →Η→
					{8459,72},	//	( ℋ → H ) SCRIPT CAPITAL H → LATIN CAPITAL LETTER H	#
					{8460,72},	//	( ℌ → H ) BLACK-LETTER CAPITAL H → LATIN CAPITAL LETTER H	#
					{8461,72},	//	( ℍ → H ) DOUBLE-STRUCK CAPITAL H → LATIN CAPITAL LETTER H	#
					{119815,72},	//	( 𝐇 → H ) MATHEMATICAL BOLD CAPITAL H → LATIN CAPITAL LETTER H	#
					{119867,72},	//	( 𝐻 → H ) MATHEMATICAL ITALIC CAPITAL H → LATIN CAPITAL LETTER H	#
					{119919,72},	//	( 𝑯 → H ) MATHEMATICAL BOLD ITALIC CAPITAL H → LATIN CAPITAL LETTER H	#
					{120023,72},	//	( 𝓗 → H ) MATHEMATICAL BOLD SCRIPT CAPITAL H → LATIN CAPITAL LETTER H	#
					{120179,72},	//	( 𝕳 → H ) MATHEMATICAL BOLD FRAKTUR CAPITAL H → LATIN CAPITAL LETTER H	#
					{120231,72},	//	( 𝖧 → H ) MATHEMATICAL SANS-SERIF CAPITAL H → LATIN CAPITAL LETTER H	#
					{120283,72},	//	( 𝗛 → H ) MATHEMATICAL SANS-SERIF BOLD CAPITAL H → LATIN CAPITAL LETTER H	#
					{120335,72},	//	( 𝘏 → H ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL H → LATIN CAPITAL LETTER H	#
					{120387,72},	//	( 𝙃 → H ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL H → LATIN CAPITAL LETTER H	#
					{120439,72},	//	( 𝙷 → H ) MATHEMATICAL MONOSPACE CAPITAL H → LATIN CAPITAL LETTER H	#
					//{919,72},	//	( Η → H ) GREEK CAPITAL LETTER ETA → LATIN CAPITAL LETTER H	#	//+ would break case-insensitive matching, unfortunately
					{120494,72},	//	( 𝚮 → H ) MATHEMATICAL BOLD CAPITAL ETA → LATIN CAPITAL LETTER H	# →Η→
					{120552,72},	//	( 𝛨 → H ) MATHEMATICAL ITALIC CAPITAL ETA → LATIN CAPITAL LETTER H	# →Η→
					{120610,72},	//	( 𝜢 → H ) MATHEMATICAL BOLD ITALIC CAPITAL ETA → LATIN CAPITAL LETTER H	# →𝑯→
					{120668,72},	//	( 𝝜 → H ) MATHEMATICAL SANS-SERIF BOLD CAPITAL ETA → LATIN CAPITAL LETTER H	# →Η→
					{120726,72},	//	( 𝞖 → H ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL ETA → LATIN CAPITAL LETTER H	# →Η→
					//{11406,72},	//	( Ⲏ → H ) COPTIC CAPITAL LETTER HATE → LATIN CAPITAL LETTER H	# →Η→	//+ would break case-insensitive matching, unfortunately
					//{1053,72},	//	( Н → H ) CYRILLIC CAPITAL LETTER EN → LATIN CAPITAL LETTER H	#	//+ would break case-insensitive matching, unfortunately
					//{5051,72},	//	( Ꮋ → H ) CHEROKEE LETTER MI → LATIN CAPITAL LETTER H	#	//+ would break case-insensitive matching
					//{5500,72},	//	( ᕼ → H ) CANADIAN SYLLABICS NUNAVUT H → LATIN CAPITAL LETTER H	#	//+ could do more harm than good
					//{42215,72},	//	( ꓧ → H ) LISU LETTER XA → LATIN CAPITAL LETTER H	#	//+ could do more harm than good
					//{66255,72},	//	( 𐋏 → H ) CARIAN LETTER E2 → LATIN CAPITAL LETTER H	#	//+ could do more harm than good
					//{731,105},	//	* ( ˛ → i ) OGONEK → LATIN SMALL LETTER I	# →ͺ→→ι→→ι→	//+ could do more harm than good
					{9075,105},	//	* ( ⍳ → i ) APL FUNCTIONAL SYMBOL IOTA → LATIN SMALL LETTER I	# →ι→
					{65353,105},	//	( ｉ → i ) FULLWIDTH LATIN SMALL LETTER I → LATIN SMALL LETTER I	# →і→
					{8560,105},	//	( ⅰ → i ) SMALL ROMAN NUMERAL ONE → LATIN SMALL LETTER I	#
					{8505,105},	//	( ℹ → i ) INFORMATION SOURCE → LATIN SMALL LETTER I	#
					{8520,105},	//	( ⅈ → i ) DOUBLE-STRUCK ITALIC SMALL I → LATIN SMALL LETTER I	#
					{119842,105},	//	( 𝐢 → i ) MATHEMATICAL BOLD SMALL I → LATIN SMALL LETTER I	#
					{119894,105},	//	( 𝑖 → i ) MATHEMATICAL ITALIC SMALL I → LATIN SMALL LETTER I	#
					{119946,105},	//	( 𝒊 → i ) MATHEMATICAL BOLD ITALIC SMALL I → LATIN SMALL LETTER I	#
					{119998,105},	//	( 𝒾 → i ) MATHEMATICAL SCRIPT SMALL I → LATIN SMALL LETTER I	#
					{120050,105},	//	( 𝓲 → i ) MATHEMATICAL BOLD SCRIPT SMALL I → LATIN SMALL LETTER I	#
					{120102,105},	//	( 𝔦 → i ) MATHEMATICAL FRAKTUR SMALL I → LATIN SMALL LETTER I	#
					{120154,105},	//	( 𝕚 → i ) MATHEMATICAL DOUBLE-STRUCK SMALL I → LATIN SMALL LETTER I	#
					{120206,105},	//	( 𝖎 → i ) MATHEMATICAL BOLD FRAKTUR SMALL I → LATIN SMALL LETTER I	#
					{120258,105},	//	( 𝗂 → i ) MATHEMATICAL SANS-SERIF SMALL I → LATIN SMALL LETTER I	#
					{120310,105},	//	( 𝗶 → i ) MATHEMATICAL SANS-SERIF BOLD SMALL I → LATIN SMALL LETTER I	#
					{120362,105},	//	( 𝘪 → i ) MATHEMATICAL SANS-SERIF ITALIC SMALL I → LATIN SMALL LETTER I	#
					{120414,105},	//	( 𝙞 → i ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL I → LATIN SMALL LETTER I	#
					{120466,105},	//	( 𝚒 → i ) MATHEMATICAL MONOSPACE SMALL I → LATIN SMALL LETTER I	#
					{305,105},	//	( ı → i ) LATIN SMALL LETTER DOTLESS I → LATIN SMALL LETTER I	#
					{120484,105},	//	( 𝚤 → i ) MATHEMATICAL ITALIC SMALL DOTLESS I → LATIN SMALL LETTER I	# →ı→
					{618,105},	//	( ɪ → i ) LATIN LETTER SMALL CAPITAL I → LATIN SMALL LETTER I	# →ı→
					{617,105},	//	( ɩ → i ) LATIN SMALL LETTER IOTA → LATIN SMALL LETTER I	#
					{953,105},	//	( ι → i ) GREEK SMALL LETTER IOTA → LATIN SMALL LETTER I	#
					{8126,105},	//	( ι → i ) GREEK PROSGEGRAMMENI → LATIN SMALL LETTER I	# →ι→
					{890,105},	//	* ( ͺ → i ) GREEK YPOGEGRAMMENI → LATIN SMALL LETTER I	# →ι→→ι→
					{120522,105},	//	( 𝛊 → i ) MATHEMATICAL BOLD SMALL IOTA → LATIN SMALL LETTER I	# →ι→
					{120580,105},	//	( 𝜄 → i ) MATHEMATICAL ITALIC SMALL IOTA → LATIN SMALL LETTER I	# →ι→
					{120638,105},	//	( 𝜾 → i ) MATHEMATICAL BOLD ITALIC SMALL IOTA → LATIN SMALL LETTER I	# →ι→
					{120696,105},	//	( 𝝸 → i ) MATHEMATICAL SANS-SERIF BOLD SMALL IOTA → LATIN SMALL LETTER I	# →ι→
					{120754,105},	//	( 𝞲 → i ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL IOTA → LATIN SMALL LETTER I	# →ι→
					{1110,105},	//	( і → i ) CYRILLIC SMALL LETTER BYELORUSSIAN-UKRAINIAN I → LATIN SMALL LETTER I	#
					{42567,105},	//	( ꙇ → i ) CYRILLIC SMALL LETTER IOTA → LATIN SMALL LETTER I	# →ι→
					//{1231,105},	//	( ӏ → i ) CYRILLIC SMALL LETTER PALOCHKA → LATIN SMALL LETTER I	# →ı→	//+ could do more harm than good
					//{43893,105},	//	( ꭵ → i ) CHEROKEE SMALL LETTER V → LATIN SMALL LETTER I	#	//+ could do more harm than good
					//{5029,105},	//	( Ꭵ → i ) CHEROKEE LETTER V → LATIN SMALL LETTER I	#	//+ could do more harm than good
					//{71875,105},	//	( 𑣃 → i ) WARANG CITI SMALL LETTER YU → LATIN SMALL LETTER I	# →ι→	//+ could do more harm than good
					{65354,106},	//	( ｊ → j ) FULLWIDTH LATIN SMALL LETTER J → LATIN SMALL LETTER J	# →ϳ→
					{8521,106},	//	( ⅉ → j ) DOUBLE-STRUCK ITALIC SMALL J → LATIN SMALL LETTER J	#
					{119843,106},	//	( 𝐣 → j ) MATHEMATICAL BOLD SMALL J → LATIN SMALL LETTER J	#
					{119895,106},	//	( 𝑗 → j ) MATHEMATICAL ITALIC SMALL J → LATIN SMALL LETTER J	#
					{119947,106},	//	( 𝒋 → j ) MATHEMATICAL BOLD ITALIC SMALL J → LATIN SMALL LETTER J	#
					{119999,106},	//	( 𝒿 → j ) MATHEMATICAL SCRIPT SMALL J → LATIN SMALL LETTER J	#
					{120051,106},	//	( 𝓳 → j ) MATHEMATICAL BOLD SCRIPT SMALL J → LATIN SMALL LETTER J	#
					{120103,106},	//	( 𝔧 → j ) MATHEMATICAL FRAKTUR SMALL J → LATIN SMALL LETTER J	#
					{120155,106},	//	( 𝕛 → j ) MATHEMATICAL DOUBLE-STRUCK SMALL J → LATIN SMALL LETTER J	#
					{120207,106},	//	( 𝖏 → j ) MATHEMATICAL BOLD FRAKTUR SMALL J → LATIN SMALL LETTER J	#
					{120259,106},	//	( 𝗃 → j ) MATHEMATICAL SANS-SERIF SMALL J → LATIN SMALL LETTER J	#
					{120311,106},	//	( 𝗷 → j ) MATHEMATICAL SANS-SERIF BOLD SMALL J → LATIN SMALL LETTER J	#
					{120363,106},	//	( 𝘫 → j ) MATHEMATICAL SANS-SERIF ITALIC SMALL J → LATIN SMALL LETTER J	#
					{120415,106},	//	( 𝙟 → j ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL J → LATIN SMALL LETTER J	#
					{120467,106},	//	( 𝚓 → j ) MATHEMATICAL MONOSPACE SMALL J → LATIN SMALL LETTER J	#
					{1011,106},	//	( ϳ → j ) GREEK LETTER YOT → LATIN SMALL LETTER J	#
					{1112,106},	//	( ј → j ) CYRILLIC SMALL LETTER JE → LATIN SMALL LETTER J	#
					{65322,74},	//	( Ｊ → J ) FULLWIDTH LATIN CAPITAL LETTER J → LATIN CAPITAL LETTER J	# →Ј→
					{119817,74},	//	( 𝐉 → J ) MATHEMATICAL BOLD CAPITAL J → LATIN CAPITAL LETTER J	#
					{119869,74},	//	( 𝐽 → J ) MATHEMATICAL ITALIC CAPITAL J → LATIN CAPITAL LETTER J	#
					{119921,74},	//	( 𝑱 → J ) MATHEMATICAL BOLD ITALIC CAPITAL J → LATIN CAPITAL LETTER J	#
					{119973,74},	//	( 𝒥 → J ) MATHEMATICAL SCRIPT CAPITAL J → LATIN CAPITAL LETTER J	#
					{120025,74},	//	( 𝓙 → J ) MATHEMATICAL BOLD SCRIPT CAPITAL J → LATIN CAPITAL LETTER J	#
					{120077,74},	//	( 𝔍 → J ) MATHEMATICAL FRAKTUR CAPITAL J → LATIN CAPITAL LETTER J	#
					{120129,74},	//	( 𝕁 → J ) MATHEMATICAL DOUBLE-STRUCK CAPITAL J → LATIN CAPITAL LETTER J	#
					{120181,74},	//	( 𝕵 → J ) MATHEMATICAL BOLD FRAKTUR CAPITAL J → LATIN CAPITAL LETTER J	#
					{120233,74},	//	( 𝖩 → J ) MATHEMATICAL SANS-SERIF CAPITAL J → LATIN CAPITAL LETTER J	#
					{120285,74},	//	( 𝗝 → J ) MATHEMATICAL SANS-SERIF BOLD CAPITAL J → LATIN CAPITAL LETTER J	#
					{120337,74},	//	( 𝘑 → J ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL J → LATIN CAPITAL LETTER J	#
					{120389,74},	//	( 𝙅 → J ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL J → LATIN CAPITAL LETTER J	#
					{120441,74},	//	( 𝙹 → J ) MATHEMATICAL MONOSPACE CAPITAL J → LATIN CAPITAL LETTER J	#
					{42930,74},	//	( Ʝ → J ) LATIN CAPITAL LETTER J WITH CROSSED-TAIL → LATIN CAPITAL LETTER J	#
					{895,74},	//	( Ϳ → J ) GREEK CAPITAL LETTER YOT → LATIN CAPITAL LETTER J	#
					{1032,74},	//	( Ј → J ) CYRILLIC CAPITAL LETTER JE → LATIN CAPITAL LETTER J	#
					//{5035,74},	//	( Ꭻ → J ) CHEROKEE LETTER GU → LATIN CAPITAL LETTER J	#	//+ could do more harm than good
					//{5261,74},	//	( ᒍ → J ) CANADIAN SYLLABICS CO → LATIN CAPITAL LETTER J	#	//+ could do more harm than good
					//{42201,74},	//	( ꓙ → J ) LISU LETTER JA → LATIN CAPITAL LETTER J	#	//+ could do more harm than good
					{119844,107},	//	( 𝐤 → k ) MATHEMATICAL BOLD SMALL K → LATIN SMALL LETTER K	#
					{119896,107},	//	( 𝑘 → k ) MATHEMATICAL ITALIC SMALL K → LATIN SMALL LETTER K	#
					{119948,107},	//	( 𝒌 → k ) MATHEMATICAL BOLD ITALIC SMALL K → LATIN SMALL LETTER K	#
					{120000,107},	//	( 𝓀 → k ) MATHEMATICAL SCRIPT SMALL K → LATIN SMALL LETTER K	#
					{120052,107},	//	( 𝓴 → k ) MATHEMATICAL BOLD SCRIPT SMALL K → LATIN SMALL LETTER K	#
					{120104,107},	//	( 𝔨 → k ) MATHEMATICAL FRAKTUR SMALL K → LATIN SMALL LETTER K	#
					{120156,107},	//	( 𝕜 → k ) MATHEMATICAL DOUBLE-STRUCK SMALL K → LATIN SMALL LETTER K	#
					{120208,107},	//	( 𝖐 → k ) MATHEMATICAL BOLD FRAKTUR SMALL K → LATIN SMALL LETTER K	#
					{120260,107},	//	( 𝗄 → k ) MATHEMATICAL SANS-SERIF SMALL K → LATIN SMALL LETTER K	#
					{120312,107},	//	( 𝗸 → k ) MATHEMATICAL SANS-SERIF BOLD SMALL K → LATIN SMALL LETTER K	#
					{120364,107},	//	( 𝘬 → k ) MATHEMATICAL SANS-SERIF ITALIC SMALL K → LATIN SMALL LETTER K	#
					{120416,107},	//	( 𝙠 → k ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL K → LATIN SMALL LETTER K	#
					{120468,107},	//	( 𝚔 → k ) MATHEMATICAL MONOSPACE SMALL K → LATIN SMALL LETTER K	#
					{8490,75},	//	( K → K ) KELVIN SIGN → LATIN CAPITAL LETTER K	#
					{65323,75},	//	( Ｋ → K ) FULLWIDTH LATIN CAPITAL LETTER K → LATIN CAPITAL LETTER K	# →Κ→
					{119818,75},	//	( 𝐊 → K ) MATHEMATICAL BOLD CAPITAL K → LATIN CAPITAL LETTER K	#
					{119870,75},	//	( 𝐾 → K ) MATHEMATICAL ITALIC CAPITAL K → LATIN CAPITAL LETTER K	#
					{119922,75},	//	( 𝑲 → K ) MATHEMATICAL BOLD ITALIC CAPITAL K → LATIN CAPITAL LETTER K	#
					{119974,75},	//	( 𝒦 → K ) MATHEMATICAL SCRIPT CAPITAL K → LATIN CAPITAL LETTER K	#
					{120026,75},	//	( 𝓚 → K ) MATHEMATICAL BOLD SCRIPT CAPITAL K → LATIN CAPITAL LETTER K	#
					{120078,75},	//	( 𝔎 → K ) MATHEMATICAL FRAKTUR CAPITAL K → LATIN CAPITAL LETTER K	#
					{120130,75},	//	( 𝕂 → K ) MATHEMATICAL DOUBLE-STRUCK CAPITAL K → LATIN CAPITAL LETTER K	#
					{120182,75},	//	( 𝕶 → K ) MATHEMATICAL BOLD FRAKTUR CAPITAL K → LATIN CAPITAL LETTER K	#
					{120234,75},	//	( 𝖪 → K ) MATHEMATICAL SANS-SERIF CAPITAL K → LATIN CAPITAL LETTER K	#
					{120286,75},	//	( 𝗞 → K ) MATHEMATICAL SANS-SERIF BOLD CAPITAL K → LATIN CAPITAL LETTER K	#
					{120338,75},	//	( 𝘒 → K ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL K → LATIN CAPITAL LETTER K	#
					{120390,75},	//	( 𝙆 → K ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL K → LATIN CAPITAL LETTER K	#
					{120442,75},	//	( 𝙺 → K ) MATHEMATICAL MONOSPACE CAPITAL K → LATIN CAPITAL LETTER K	#
					//{922,75},	//	( Κ → K ) GREEK CAPITAL LETTER KAPPA → LATIN CAPITAL LETTER K	#	//+ would break case-insensitive matching, unfortunately
					{120497,75},	//	( 𝚱 → K ) MATHEMATICAL BOLD CAPITAL KAPPA → LATIN CAPITAL LETTER K	# →Κ→
					{120555,75},	//	( 𝛫 → K ) MATHEMATICAL ITALIC CAPITAL KAPPA → LATIN CAPITAL LETTER K	# →𝐾→
					{120613,75},	//	( 𝜥 → K ) MATHEMATICAL BOLD ITALIC CAPITAL KAPPA → LATIN CAPITAL LETTER K	# →𝑲→
					{120671,75},	//	( 𝝟 → K ) MATHEMATICAL SANS-SERIF BOLD CAPITAL KAPPA → LATIN CAPITAL LETTER K	# →Κ→
					{120729,75},	//	( 𝞙 → K ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL KAPPA → LATIN CAPITAL LETTER K	# →Κ→
					//{11412,75},	//	( Ⲕ → K ) COPTIC CAPITAL LETTER KAPA → LATIN CAPITAL LETTER K	# →Κ→	//+ would break case-insensitive matching, unfortunately
					//{1050,75},	//	( К → K ) CYRILLIC CAPITAL LETTER KA → LATIN CAPITAL LETTER K	#	//+ would break case-insensitive matching, unfortunately
					//{5094,75},	//	( Ꮶ → K ) CHEROKEE LETTER TSO → LATIN CAPITAL LETTER K	#	//+ could do more harm than good
					//{5845,75},	//	( ᛕ → K ) RUNIC LETTER OPEN-P → LATIN CAPITAL LETTER K	#	//+ could do more harm than good
					//{42199,75},	//	( ꓗ → K ) LISU LETTER KA → LATIN CAPITAL LETTER K	#	//+ could do more harm than good
					//{66840,75},	//	( 𐔘 → K ) ELBASAN LETTER QE → LATIN CAPITAL LETTER K	#	//+ could do more harm than good
					//+ A lot of mappings that originally were to lowercase-l had to be changed to avoid breaking things or surprising users.
					//{1472,108},	//	* ( ‎׀‎ → l ) HEBREW PUNCTUATION PASEQ → LATIN SMALL LETTER L	# →|→	//+ could do more harm than good
					{8739,124},	//	* ( ∣ → | ) DIVIDES → VERTICAL LINE	#
					{9213,124},	//	* ( ⏽ → | ) POWER ON SYMBOL → VERTICAL LINE	#
					{65512,124},	//	* ( ￨ → | ) HALFWIDTH FORMS LIGHT VERTICAL → VERTICAL LINE	#
					//{1633,108},	//	( ‎١‎ → l ) ARABIC-INDIC DIGIT ONE → LATIN SMALL LETTER L	# →1→	//+ could do more harm than good
					//{1777,108},	//	( ۱ → l ) EXTENDED ARABIC-INDIC DIGIT ONE → LATIN SMALL LETTER L	# →1→	//+ could do more harm than good
					//{66336,108},	//	* ( 𐌠 → l ) OLD ITALIC NUMERAL ONE → LATIN SMALL LETTER L	# →𐌉→→I→	//+ could do more harm than good
					//{125127,108},	//	* ( ‎𞣇‎ → l ) MENDE KIKAKUI DIGIT ONE → LATIN SMALL LETTER L	#	//+ could do more harm than good
					{120783,49},	//	( 𝟏 → 1 ) MATHEMATICAL BOLD DIGIT ONE → DIGIT ONE	#
					{120793,49},	//	( 𝟙 → 1 ) MATHEMATICAL DOUBLE-STRUCK DIGIT ONE → DIGIT ONE	#
					{120803,49},	//	( 𝟣 → 1 ) MATHEMATICAL SANS-SERIF DIGIT ONE → DIGIT ONE	#
					{120813,49},	//	( 𝟭 → 1 ) MATHEMATICAL SANS-SERIF BOLD DIGIT ONE → DIGIT ONE	#
					{120823,49},	//	( 𝟷 → 1 ) MATHEMATICAL MONOSPACE DIGIT ONE → DIGIT ONE	#
					{130033,49},	//	( 🯱 → 1 ) SEGMENTED DIGIT ONE → DIGIT ONE	#
					{65321,73},	//	( Ｉ → I ) FULLWIDTH LATIN CAPITAL LETTER I → LATIN CAPITAL LETTER I	#
					{8544,73},	//	( Ⅰ → I ) ROMAN NUMERAL ONE → LATIN CAPITAL LETTER I	#
					{8464,73},	//	( ℐ → I ) SCRIPT CAPITAL I → LATIN CAPITAL LETTER I	#
					{8465,73},	//	( ℑ → I ) BLACK-LETTER CAPITAL I → LATIN CAPITAL LETTER I	#
					{119816,73},	//	( 𝐈 → I ) MATHEMATICAL BOLD CAPITAL I → LATIN CAPITAL LETTER I	#
					{119868,73},	//	( 𝐼 → I ) MATHEMATICAL ITALIC CAPITAL I → LATIN CAPITAL LETTER I	#
					{119920,73},	//	( 𝑰 → I ) MATHEMATICAL BOLD ITALIC CAPITAL I → LATIN CAPITAL LETTER I	#
					{120024,73},	//	( 𝓘 → I ) MATHEMATICAL BOLD SCRIPT CAPITAL I → LATIN CAPITAL LETTER I	#
					{120128,73},	//	( 𝕀 → I ) MATHEMATICAL DOUBLE-STRUCK CAPITAL I → LATIN CAPITAL LETTER I	#
					{120180,73},	//	( 𝕴 → I ) MATHEMATICAL BOLD FRAKTUR CAPITAL I → LATIN CAPITAL LETTER I	#
					{120232,73},	//	( 𝖨 → I ) MATHEMATICAL SANS-SERIF CAPITAL I → LATIN CAPITAL LETTER I	#
					{120284,73},	//	( 𝗜 → I ) MATHEMATICAL SANS-SERIF BOLD CAPITAL I → LATIN CAPITAL LETTER I	#
					{120336,73},	//	( 𝘐 → I ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL I → LATIN CAPITAL LETTER I	#
					{120388,73},	//	( 𝙄 → I ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL I → LATIN CAPITAL LETTER I	#
					{120440,73},	//	( 𝙸 → I ) MATHEMATICAL MONOSPACE CAPITAL I → LATIN CAPITAL LETTER I	#
					{406,73},	//	( Ɩ → I ) LATIN CAPITAL LETTER IOTA → LATIN CAPITAL LETTER I	#
					{65356,108},	//	( ｌ → l ) FULLWIDTH LATIN SMALL LETTER L → LATIN SMALL LETTER L	#
					{8572,108},	//	( ⅼ → l ) SMALL ROMAN NUMERAL FIFTY → LATIN SMALL LETTER L	#
					{8467,108},	//	( ℓ → l ) SCRIPT SMALL L → LATIN SMALL LETTER L	#
					{119845,108},	//	( 𝐥 → l ) MATHEMATICAL BOLD SMALL L → LATIN SMALL LETTER L	#
					{119897,108},	//	( 𝑙 → l ) MATHEMATICAL ITALIC SMALL L → LATIN SMALL LETTER L	#
					{119949,108},	//	( 𝒍 → l ) MATHEMATICAL BOLD ITALIC SMALL L → LATIN SMALL LETTER L	#
					{120001,108},	//	( 𝓁 → l ) MATHEMATICAL SCRIPT SMALL L → LATIN SMALL LETTER L	#
					{120053,108},	//	( 𝓵 → l ) MATHEMATICAL BOLD SCRIPT SMALL L → LATIN SMALL LETTER L	#
					{120105,108},	//	( 𝔩 → l ) MATHEMATICAL FRAKTUR SMALL L → LATIN SMALL LETTER L	#
					{120157,108},	//	( 𝕝 → l ) MATHEMATICAL DOUBLE-STRUCK SMALL L → LATIN SMALL LETTER L	#
					{120209,108},	//	( 𝖑 → l ) MATHEMATICAL BOLD FRAKTUR SMALL L → LATIN SMALL LETTER L	#
					{120261,108},	//	( 𝗅 → l ) MATHEMATICAL SANS-SERIF SMALL L → LATIN SMALL LETTER L	#
					{120313,108},	//	( 𝗹 → l ) MATHEMATICAL SANS-SERIF BOLD SMALL L → LATIN SMALL LETTER L	#
					{120365,108},	//	( 𝘭 → l ) MATHEMATICAL SANS-SERIF ITALIC SMALL L → LATIN SMALL LETTER L	#
					{120417,108},	//	( 𝙡 → l ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL L → LATIN SMALL LETTER L	#
					{120469,108},	//	( 𝚕 → l ) MATHEMATICAL MONOSPACE SMALL L → LATIN SMALL LETTER L	#
					{448,124},	//	( ǀ → | ) LATIN LETTER DENTAL CLICK → VERTICAL LINE	#
					{921,73},	//	( Ι → I ) GREEK CAPITAL LETTER IOTA → LATIN CAPITAL LETTER I	#
					{120496,73},	//	( 𝚰 → I ) MATHEMATICAL BOLD CAPITAL IOTA → LATIN CAPITAL LETTER I	#
					{120554,73},	//	( 𝛪 → I ) MATHEMATICAL ITALIC CAPITAL IOTA → LATIN CAPITAL LETTER I	#
					{120612,73},	//	( 𝜤 → I ) MATHEMATICAL BOLD ITALIC CAPITAL IOTA → LATIN CAPITAL LETTER I	#
					{120670,73},	//	( 𝝞 → I ) MATHEMATICAL SANS-SERIF BOLD CAPITAL IOTA → LATIN CAPITAL LETTER I	#
					{120728,73},	//	( 𝞘 → I ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL IOTA → LATIN CAPITAL LETTER I	#
					{11410,73},	//	( Ⲓ → I ) COPTIC CAPITAL LETTER IAUDA → LATIN CAPITAL LETTER I	#
					{1030,73},	//	( І → I ) CYRILLIC CAPITAL LETTER BYELORUSSIAN-UKRAINIAN I → LATIN CAPITAL LETTER I	#
					{1216,73},	//	( Ӏ → I ) CYRILLIC LETTER PALOCHKA → LATIN CAPITAL LETTER I	#
					//+ these could do more harm than good
					//{1493,108},	//	( ‎ו‎ → l ) HEBREW LETTER VAV → LATIN SMALL LETTER L	#
					//{1503,108},	//	( ‎ן‎ → l ) HEBREW LETTER FINAL NUN → LATIN SMALL LETTER L	#
					//{1575,108},	//	( ‎ا‎ → l ) ARABIC LETTER ALEF → LATIN SMALL LETTER L	# →1→
					//{126464,108},	//	( ‎𞸀‎ → l ) ARABIC MATHEMATICAL ALEF → LATIN SMALL LETTER L	# →‎ا‎→→1→
					//{126592,108},	//	( ‎𞺀‎ → l ) ARABIC MATHEMATICAL LOOPED ALEF → LATIN SMALL LETTER L	# →‎ا‎→→1→
					//{65166,108},	//	( ‎ﺎ‎ → l ) ARABIC LETTER ALEF FINAL FORM → LATIN SMALL LETTER L	# →‎ا‎→→1→
					//{65165,108},	//	( ‎ﺍ‎ → l ) ARABIC LETTER ALEF ISOLATED FORM → LATIN SMALL LETTER L	# →‎ا‎→→1→
					//{1994,108},	//	( ‎ߊ‎ → l ) NKO LETTER A → LATIN SMALL LETTER L	# →∣→→ǀ→
					//{11599,108},	//	( ⵏ → l ) TIFINAGH LETTER YAN → LATIN SMALL LETTER L	#
					//{5825,108},	//	( ᛁ → l ) RUNIC LETTER ISAZ IS ISS I → LATIN SMALL LETTER L	# →I→
					//{42226,108},	//	( ꓲ → l ) LISU LETTER I → LATIN SMALL LETTER L	# →I→
					//{93992,108},	//	( 𖼨 → l ) MIAO LETTER GHA → LATIN SMALL LETTER L	# →I→
					//{66186,108},	//	( 𐊊 → l ) LYCIAN LETTER J → LATIN SMALL LETTER L	# →I→
					//{66313,108},	//	( 𐌉 → l ) OLD ITALIC LETTER I → LATIN SMALL LETTER L	# →I→
					//+
					{119338,76},	//	* ( 𝈪 → L ) GREEK INSTRUMENTAL NOTATION SYMBOL-23 → LATIN CAPITAL LETTER L	#
					{8556,76},	//	( Ⅼ → L ) ROMAN NUMERAL FIFTY → LATIN CAPITAL LETTER L	#
					{8466,76},	//	( ℒ → L ) SCRIPT CAPITAL L → LATIN CAPITAL LETTER L	#
					{119819,76},	//	( 𝐋 → L ) MATHEMATICAL BOLD CAPITAL L → LATIN CAPITAL LETTER L	#
					{119871,76},	//	( 𝐿 → L ) MATHEMATICAL ITALIC CAPITAL L → LATIN CAPITAL LETTER L	#
					{119923,76},	//	( 𝑳 → L ) MATHEMATICAL BOLD ITALIC CAPITAL L → LATIN CAPITAL LETTER L	#
					{120027,76},	//	( 𝓛 → L ) MATHEMATICAL BOLD SCRIPT CAPITAL L → LATIN CAPITAL LETTER L	#
					{120079,76},	//	( 𝔏 → L ) MATHEMATICAL FRAKTUR CAPITAL L → LATIN CAPITAL LETTER L	#
					{120131,76},	//	( 𝕃 → L ) MATHEMATICAL DOUBLE-STRUCK CAPITAL L → LATIN CAPITAL LETTER L	#
					{120183,76},	//	( 𝕷 → L ) MATHEMATICAL BOLD FRAKTUR CAPITAL L → LATIN CAPITAL LETTER L	#
					{120235,76},	//	( 𝖫 → L ) MATHEMATICAL SANS-SERIF CAPITAL L → LATIN CAPITAL LETTER L	#
					{120287,76},	//	( 𝗟 → L ) MATHEMATICAL SANS-SERIF BOLD CAPITAL L → LATIN CAPITAL LETTER L	#
					{120339,76},	//	( 𝘓 → L ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL L → LATIN CAPITAL LETTER L	#
					{120391,76},	//	( 𝙇 → L ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL L → LATIN CAPITAL LETTER L	#
					{120443,76},	//	( 𝙻 → L ) MATHEMATICAL MONOSPACE CAPITAL L → LATIN CAPITAL LETTER L	#
					//{11472,76},	//	( Ⳑ → L ) COPTIC CAPITAL LETTER L-SHAPED HA → LATIN CAPITAL LETTER L	#	//+ could do more harm than good
					//{5086,76},	//	( Ꮮ → L ) CHEROKEE LETTER TLE → LATIN CAPITAL LETTER L	#	//+ could do more harm than good
					//{5290,76},	//	( ᒪ → L ) CANADIAN SYLLABICS MA → LATIN CAPITAL LETTER L	#	//+ could do more harm than good
					//{42209,76},	//	( ꓡ → L ) LISU LETTER LA → LATIN CAPITAL LETTER L	#	//+ could do more harm than good
					//{93974,76},	//	( 𖼖 → L ) MIAO LETTER LA → LATIN CAPITAL LETTER L	#	//+ could do more harm than good
					//{71843,76},	//	( 𑢣 → L ) WARANG CITI CAPITAL LETTER YU → LATIN CAPITAL LETTER L	#	//+ could do more harm than good
					//{71858,76},	//	( 𑢲 → L ) WARANG CITI CAPITAL LETTER TTE → LATIN CAPITAL LETTER L	#	//+ could do more harm than good
					//{66587,76},	//	( 𐐛 → L ) DESERET CAPITAL LETTER ETH → LATIN CAPITAL LETTER L	#	//+ would break case-insensitive matching
					//{66854,76},	//	( 𐔦 → L ) ELBASAN LETTER GHAMMA → LATIN CAPITAL LETTER L	#	//+ could do more harm than good
					{65325,77},	//	( Ｍ → M ) FULLWIDTH LATIN CAPITAL LETTER M → LATIN CAPITAL LETTER M	# →Μ→
					{8559,77},	//	( Ⅿ → M ) ROMAN NUMERAL ONE THOUSAND → LATIN CAPITAL LETTER M	#
					{8499,77},	//	( ℳ → M ) SCRIPT CAPITAL M → LATIN CAPITAL LETTER M	#
					{119820,77},	//	( 𝐌 → M ) MATHEMATICAL BOLD CAPITAL M → LATIN CAPITAL LETTER M	#
					{119872,77},	//	( 𝑀 → M ) MATHEMATICAL ITALIC CAPITAL M → LATIN CAPITAL LETTER M	#
					{119924,77},	//	( 𝑴 → M ) MATHEMATICAL BOLD ITALIC CAPITAL M → LATIN CAPITAL LETTER M	#
					{120028,77},	//	( 𝓜 → M ) MATHEMATICAL BOLD SCRIPT CAPITAL M → LATIN CAPITAL LETTER M	#
					{120080,77},	//	( 𝔐 → M ) MATHEMATICAL FRAKTUR CAPITAL M → LATIN CAPITAL LETTER M	#
					{120132,77},	//	( 𝕄 → M ) MATHEMATICAL DOUBLE-STRUCK CAPITAL M → LATIN CAPITAL LETTER M	#
					{120184,77},	//	( 𝕸 → M ) MATHEMATICAL BOLD FRAKTUR CAPITAL M → LATIN CAPITAL LETTER M	#
					{120236,77},	//	( 𝖬 → M ) MATHEMATICAL SANS-SERIF CAPITAL M → LATIN CAPITAL LETTER M	#
					{120288,77},	//	( 𝗠 → M ) MATHEMATICAL SANS-SERIF BOLD CAPITAL M → LATIN CAPITAL LETTER M	#
					{120340,77},	//	( 𝘔 → M ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL M → LATIN CAPITAL LETTER M	#
					{120392,77},	//	( 𝙈 → M ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL M → LATIN CAPITAL LETTER M	#
					{120444,77},	//	( 𝙼 → M ) MATHEMATICAL MONOSPACE CAPITAL M → LATIN CAPITAL LETTER M	#
					//{924,77},	//	( Μ → M ) GREEK CAPITAL LETTER MU → LATIN CAPITAL LETTER M	#	//+ would break case-insensitive matching, unfortunately
					{120499,77},	//	( 𝚳 → M ) MATHEMATICAL BOLD CAPITAL MU → LATIN CAPITAL LETTER M	# →𝐌→
					{120557,77},	//	( 𝛭 → M ) MATHEMATICAL ITALIC CAPITAL MU → LATIN CAPITAL LETTER M	# →𝑀→
					{120615,77},	//	( 𝜧 → M ) MATHEMATICAL BOLD ITALIC CAPITAL MU → LATIN CAPITAL LETTER M	# →𝑴→
					{120673,77},	//	( 𝝡 → M ) MATHEMATICAL SANS-SERIF BOLD CAPITAL MU → LATIN CAPITAL LETTER M	# →Μ→
					{120731,77},	//	( 𝞛 → M ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL MU → LATIN CAPITAL LETTER M	# →Μ→
					//{1018,77},	//	( Ϻ → M ) GREEK CAPITAL LETTER SAN → LATIN CAPITAL LETTER M	#	//+ would break case-insensitive matching
					//{11416,77},	//	( Ⲙ → M ) COPTIC CAPITAL LETTER MI → LATIN CAPITAL LETTER M	#	//+ would break case-insensitive matching
					//{1052,77},	//	( М → M ) CYRILLIC CAPITAL LETTER EM → LATIN CAPITAL LETTER M	#	//+ would break case-insensitive matching, unfortunately
					//+ these could do more harm than good
					//{5047,77},	//	( Ꮇ → M ) CHEROKEE LETTER LU → LATIN CAPITAL LETTER M	#
					//{5616,77},	//	( ᗰ → M ) CANADIAN SYLLABICS CARRIER GO → LATIN CAPITAL LETTER M	#
					//{5846,77},	//	( ᛖ → M ) RUNIC LETTER EHWAZ EH E → LATIN CAPITAL LETTER M	#
					//{42207,77},	//	( ꓟ → M ) LISU LETTER MA → LATIN CAPITAL LETTER M	#
					//{66224,77},	//	( 𐊰 → M ) CARIAN LETTER S → LATIN CAPITAL LETTER M	#
					//{66321,77},	//	( 𐌑 → M ) OLD ITALIC LETTER SHE → LATIN CAPITAL LETTER M	#
					//+
					{119847,110},	//	( 𝐧 → n ) MATHEMATICAL BOLD SMALL N → LATIN SMALL LETTER N	#
					{119899,110},	//	( 𝑛 → n ) MATHEMATICAL ITALIC SMALL N → LATIN SMALL LETTER N	#
					{119951,110},	//	( 𝒏 → n ) MATHEMATICAL BOLD ITALIC SMALL N → LATIN SMALL LETTER N	#
					{120003,110},	//	( 𝓃 → n ) MATHEMATICAL SCRIPT SMALL N → LATIN SMALL LETTER N	#
					{120055,110},	//	( 𝓷 → n ) MATHEMATICAL BOLD SCRIPT SMALL N → LATIN SMALL LETTER N	#
					{120107,110},	//	( 𝔫 → n ) MATHEMATICAL FRAKTUR SMALL N → LATIN SMALL LETTER N	#
					{120159,110},	//	( 𝕟 → n ) MATHEMATICAL DOUBLE-STRUCK SMALL N → LATIN SMALL LETTER N	#
					{120211,110},	//	( 𝖓 → n ) MATHEMATICAL BOLD FRAKTUR SMALL N → LATIN SMALL LETTER N	#
					{120263,110},	//	( 𝗇 → n ) MATHEMATICAL SANS-SERIF SMALL N → LATIN SMALL LETTER N	#
					{120315,110},	//	( 𝗻 → n ) MATHEMATICAL SANS-SERIF BOLD SMALL N → LATIN SMALL LETTER N	#
					{120367,110},	//	( 𝘯 → n ) MATHEMATICAL SANS-SERIF ITALIC SMALL N → LATIN SMALL LETTER N	#
					{120419,110},	//	( 𝙣 → n ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL N → LATIN SMALL LETTER N	#
					{120471,110},	//	( 𝚗 → n ) MATHEMATICAL MONOSPACE SMALL N → LATIN SMALL LETTER N	#
					//{1400,110},	//	( ո → n ) ARMENIAN SMALL LETTER VO → LATIN SMALL LETTER N	#	//+ could do more harm than good
					//{1404,110},	//	( ռ → n ) ARMENIAN SMALL LETTER RA → LATIN SMALL LETTER N	#	//+ could do more harm than good
					{65326,78},	//	( Ｎ → N ) FULLWIDTH LATIN CAPITAL LETTER N → LATIN CAPITAL LETTER N	# →Ν→
					{8469,78},	//	( ℕ → N ) DOUBLE-STRUCK CAPITAL N → LATIN CAPITAL LETTER N	#
					{119821,78},	//	( 𝐍 → N ) MATHEMATICAL BOLD CAPITAL N → LATIN CAPITAL LETTER N	#
					{119873,78},	//	( 𝑁 → N ) MATHEMATICAL ITALIC CAPITAL N → LATIN CAPITAL LETTER N	#
					{119925,78},	//	( 𝑵 → N ) MATHEMATICAL BOLD ITALIC CAPITAL N → LATIN CAPITAL LETTER N	#
					{119977,78},	//	( 𝒩 → N ) MATHEMATICAL SCRIPT CAPITAL N → LATIN CAPITAL LETTER N	#
					{120029,78},	//	( 𝓝 → N ) MATHEMATICAL BOLD SCRIPT CAPITAL N → LATIN CAPITAL LETTER N	#
					{120081,78},	//	( 𝔑 → N ) MATHEMATICAL FRAKTUR CAPITAL N → LATIN CAPITAL LETTER N	#
					{120185,78},	//	( 𝕹 → N ) MATHEMATICAL BOLD FRAKTUR CAPITAL N → LATIN CAPITAL LETTER N	#
					{120237,78},	//	( 𝖭 → N ) MATHEMATICAL SANS-SERIF CAPITAL N → LATIN CAPITAL LETTER N	#
					{120289,78},	//	( 𝗡 → N ) MATHEMATICAL SANS-SERIF BOLD CAPITAL N → LATIN CAPITAL LETTER N	#
					{120341,78},	//	( 𝘕 → N ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL N → LATIN CAPITAL LETTER N	#
					{120393,78},	//	( 𝙉 → N ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL N → LATIN CAPITAL LETTER N	#
					{120445,78},	//	( 𝙽 → N ) MATHEMATICAL MONOSPACE CAPITAL N → LATIN CAPITAL LETTER N	#
					//{925,78},	//	( Ν → N ) GREEK CAPITAL LETTER NU → LATIN CAPITAL LETTER N	#	//+ would break case-insensitive matching, unfortunately
					{120500,78},	//	( 𝚴 → N ) MATHEMATICAL BOLD CAPITAL NU → LATIN CAPITAL LETTER N	# →𝐍→
					{120558,78},	//	( 𝛮 → N ) MATHEMATICAL ITALIC CAPITAL NU → LATIN CAPITAL LETTER N	# →𝑁→
					{120616,78},	//	( 𝜨 → N ) MATHEMATICAL BOLD ITALIC CAPITAL NU → LATIN CAPITAL LETTER N	# →𝑵→
					{120674,78},	//	( 𝝢 → N ) MATHEMATICAL SANS-SERIF BOLD CAPITAL NU → LATIN CAPITAL LETTER N	# →Ν→
					{120732,78},	//	( 𝞜 → N ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL NU → LATIN CAPITAL LETTER N	# →Ν→
					//{11418,78},	//	( Ⲛ → N ) COPTIC CAPITAL LETTER NI → LATIN CAPITAL LETTER N	#	//+ would break case-insensitive matching, unfortunately
					//+ these could do more harm than good
					//{42208,78},	//	( ꓠ → N ) LISU LETTER NA → LATIN CAPITAL LETTER N	#
					//{66835,78},	//	( 𐔓 → N ) ELBASAN LETTER NE → LATIN CAPITAL LETTER N	#
					//{3074,111},	//	( ం → o ) TELUGU SIGN ANUSVARA → LATIN SMALL LETTER O	#
					//{3202,111},	//	( ಂ → o ) KANNADA SIGN ANUSVARA → LATIN SMALL LETTER O	#
					//{3330,111},	//	( ം → o ) MALAYALAM SIGN ANUSVARA → LATIN SMALL LETTER O	#
					//{3458,111},	//	( ං → o ) SINHALA SIGN ANUSVARAYA → LATIN SMALL LETTER O	#
					//{2406,111},	//	( ० → o ) DEVANAGARI DIGIT ZERO → LATIN SMALL LETTER O	#
					//{2662,111},	//	( ੦ → o ) GURMUKHI DIGIT ZERO → LATIN SMALL LETTER O	#
					//{2790,111},	//	( ૦ → o ) GUJARATI DIGIT ZERO → LATIN SMALL LETTER O	#
					//{3046,111},	//	( ௦ → o ) TAMIL DIGIT ZERO → LATIN SMALL LETTER O	#
					//{3174,111},	//	( ౦ → o ) TELUGU DIGIT ZERO → LATIN SMALL LETTER O	#
					//{3302,111},	//	( ೦ → o ) KANNADA DIGIT ZERO → LATIN SMALL LETTER O	# →౦→
					//{3430,111},	//	( ൦ → o ) MALAYALAM DIGIT ZERO → LATIN SMALL LETTER O	#
					//{3664,111},	//	( ๐ → o ) THAI DIGIT ZERO → LATIN SMALL LETTER O	#
					//{3792,111},	//	( ໐ → o ) LAO DIGIT ZERO → LATIN SMALL LETTER O	#
					//{4160,111},	//	( ၀ → o ) MYANMAR DIGIT ZERO → LATIN SMALL LETTER O	#
					//{1637,111},	//	( ‎٥‎ → o ) ARABIC-INDIC DIGIT FIVE → LATIN SMALL LETTER O	#
					//{1781,111},	//	( ۵ → o ) EXTENDED ARABIC-INDIC DIGIT FIVE → LATIN SMALL LETTER O	# →‎٥‎→
					//+
					{65359,111},	//	( ｏ → o ) FULLWIDTH LATIN SMALL LETTER O → LATIN SMALL LETTER O	# →о→
					{8500,111},	//	( ℴ → o ) SCRIPT SMALL O → LATIN SMALL LETTER O	#
					{119848,111},	//	( 𝐨 → o ) MATHEMATICAL BOLD SMALL O → LATIN SMALL LETTER O	#
					{119900,111},	//	( 𝑜 → o ) MATHEMATICAL ITALIC SMALL O → LATIN SMALL LETTER O	#
					{119952,111},	//	( 𝒐 → o ) MATHEMATICAL BOLD ITALIC SMALL O → LATIN SMALL LETTER O	#
					{120056,111},	//	( 𝓸 → o ) MATHEMATICAL BOLD SCRIPT SMALL O → LATIN SMALL LETTER O	#
					{120108,111},	//	( 𝔬 → o ) MATHEMATICAL FRAKTUR SMALL O → LATIN SMALL LETTER O	#
					{120160,111},	//	( 𝕠 → o ) MATHEMATICAL DOUBLE-STRUCK SMALL O → LATIN SMALL LETTER O	#
					{120212,111},	//	( 𝖔 → o ) MATHEMATICAL BOLD FRAKTUR SMALL O → LATIN SMALL LETTER O	#
					{120264,111},	//	( 𝗈 → o ) MATHEMATICAL SANS-SERIF SMALL O → LATIN SMALL LETTER O	#
					{120316,111},	//	( 𝗼 → o ) MATHEMATICAL SANS-SERIF BOLD SMALL O → LATIN SMALL LETTER O	#
					{120368,111},	//	( 𝘰 → o ) MATHEMATICAL SANS-SERIF ITALIC SMALL O → LATIN SMALL LETTER O	#
					{120420,111},	//	( 𝙤 → o ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL O → LATIN SMALL LETTER O	#
					{120472,111},	//	( 𝚘 → o ) MATHEMATICAL MONOSPACE SMALL O → LATIN SMALL LETTER O	#
					{7439,111},	//	( ᴏ → o ) LATIN LETTER SMALL CAPITAL O → LATIN SMALL LETTER O	#
					{7441,111},	//	( ᴑ → o ) LATIN SMALL LETTER SIDEWAYS O → LATIN SMALL LETTER O	#
					{43837,111},	//	( ꬽ → o ) LATIN SMALL LETTER BLACKLETTER O → LATIN SMALL LETTER O	#
					{959,111},	//	( ο → o ) GREEK SMALL LETTER OMICRON → LATIN SMALL LETTER O	#
					{120528,111},	//	( 𝛐 → o ) MATHEMATICAL BOLD SMALL OMICRON → LATIN SMALL LETTER O	# →𝐨→
					{120586,111},	//	( 𝜊 → o ) MATHEMATICAL ITALIC SMALL OMICRON → LATIN SMALL LETTER O	# →𝑜→
					{120644,111},	//	( 𝝄 → o ) MATHEMATICAL BOLD ITALIC SMALL OMICRON → LATIN SMALL LETTER O	# →𝒐→
					{120702,111},	//	( 𝝾 → o ) MATHEMATICAL SANS-SERIF BOLD SMALL OMICRON → LATIN SMALL LETTER O	# →ο→
					{120760,111},	//	( 𝞸 → o ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL OMICRON → LATIN SMALL LETTER O	# →ο→
					//{963,111},	//	( σ → o ) GREEK SMALL LETTER SIGMA → LATIN SMALL LETTER O	#	//+ definitely would do more harm than good
					{120532,111},	//	( 𝛔 → o ) MATHEMATICAL BOLD SMALL SIGMA → LATIN SMALL LETTER O	# →σ→
					{120590,111},	//	( 𝜎 → o ) MATHEMATICAL ITALIC SMALL SIGMA → LATIN SMALL LETTER O	# →σ→
					{120648,111},	//	( 𝝈 → o ) MATHEMATICAL BOLD ITALIC SMALL SIGMA → LATIN SMALL LETTER O	# →σ→
					{120706,111},	//	( 𝞂 → o ) MATHEMATICAL SANS-SERIF BOLD SMALL SIGMA → LATIN SMALL LETTER O	# →σ→
					{120764,111},	//	( 𝞼 → o ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL SIGMA → LATIN SMALL LETTER O	# →σ→
					{11423,111},	//	( ⲟ → o ) COPTIC SMALL LETTER O → LATIN SMALL LETTER O	#
					{1086,111},	//	( о → o ) CYRILLIC SMALL LETTER O → LATIN SMALL LETTER O	#
					//{4351,111},	//	( ჿ → o ) GEORGIAN LETTER LABIAL SIGN → LATIN SMALL LETTER O	#	//+ could do more harm than good
					{1413,111},	//	( օ → o ) ARMENIAN SMALL LETTER OH → LATIN SMALL LETTER O	#
					//+ these could do more harm than good
					//{1505,111},	//	( ‎ס‎ → o ) HEBREW LETTER SAMEKH → LATIN SMALL LETTER O	#
					//{1607,111},	//	( ‎ه‎ → o ) ARABIC LETTER HEH → LATIN SMALL LETTER O	#
					//{126500,111},	//	( ‎𞸤‎ → o ) ARABIC MATHEMATICAL INITIAL HEH → LATIN SMALL LETTER O	# →‎ه‎→
					//{126564,111},	//	( ‎𞹤‎ → o ) ARABIC MATHEMATICAL STRETCHED HEH → LATIN SMALL LETTER O	# →‎ه‎→
					//{126596,111},	//	( ‎𞺄‎ → o ) ARABIC MATHEMATICAL LOOPED HEH → LATIN SMALL LETTER O	# →‎ه‎→
					//{65259,111},	//	( ‎ﻫ‎ → o ) ARABIC LETTER HEH INITIAL FORM → LATIN SMALL LETTER O	# →‎ه‎→
					//{65260,111},	//	( ‎ﻬ‎ → o ) ARABIC LETTER HEH MEDIAL FORM → LATIN SMALL LETTER O	# →‎ه‎→
					//{65258,111},	//	( ‎ﻪ‎ → o ) ARABIC LETTER HEH FINAL FORM → LATIN SMALL LETTER O	# →‎ه‎→
					//{65257,111},	//	( ‎ﻩ‎ → o ) ARABIC LETTER HEH ISOLATED FORM → LATIN SMALL LETTER O	# →‎ه‎→
					//{1726,111},	//	( ‎ھ‎ → o ) ARABIC LETTER HEH DOACHASHMEE → LATIN SMALL LETTER O	# →‎ه‎→
					//{64428,111},	//	( ‎ﮬ‎ → o ) ARABIC LETTER HEH DOACHASHMEE INITIAL FORM → LATIN SMALL LETTER O	# →‎ﻫ‎→→‎ه‎→
					//{64429,111},	//	( ‎ﮭ‎ → o ) ARABIC LETTER HEH DOACHASHMEE MEDIAL FORM → LATIN SMALL LETTER O	# →‎ﻬ‎→→‎ه‎→
					//{64427,111},	//	( ‎ﮫ‎ → o ) ARABIC LETTER HEH DOACHASHMEE FINAL FORM → LATIN SMALL LETTER O	# →‎ﻪ‎→→‎ه‎→
					//{64426,111},	//	( ‎ﮪ‎ → o ) ARABIC LETTER HEH DOACHASHMEE ISOLATED FORM → LATIN SMALL LETTER O	# →‎ه‎→
					//{1729,111},	//	( ‎ہ‎ → o ) ARABIC LETTER HEH GOAL → LATIN SMALL LETTER O	# →‎ه‎→
					//{64424,111},	//	( ‎ﮨ‎ → o ) ARABIC LETTER HEH GOAL INITIAL FORM → LATIN SMALL LETTER O	# →‎ہ‎→→‎ه‎→
					//{64425,111},	//	( ‎ﮩ‎ → o ) ARABIC LETTER HEH GOAL MEDIAL FORM → LATIN SMALL LETTER O	# →‎ہ‎→→‎ه‎→
					//{64423,111},	//	( ‎ﮧ‎ → o ) ARABIC LETTER HEH GOAL FINAL FORM → LATIN SMALL LETTER O	# →‎ہ‎→→‎ه‎→
					//{64422,111},	//	( ‎ﮦ‎ → o ) ARABIC LETTER HEH GOAL ISOLATED FORM → LATIN SMALL LETTER O	# →‎ه‎→
					//{1749,111},	//	( ‎ە‎ → o ) ARABIC LETTER AE → LATIN SMALL LETTER O	# →‎ه‎→
					//{3360,111},	//	( ഠ → o ) MALAYALAM LETTER TTHA → LATIN SMALL LETTER O	#
					//{4125,111},	//	( ဝ → o ) MYANMAR LETTER WA → LATIN SMALL LETTER O	#
					//+
					{66794,111},	//	( 𐓪 → o ) OSAGE SMALL LETTER O → LATIN SMALL LETTER O	#
					//{71880,111},	//	( 𑣈 → o ) WARANG CITI SMALL LETTER E → LATIN SMALL LETTER O	#	//+ could do more harm than good
					//{71895,111},	//	( 𑣗 → o ) WARANG CITI SMALL LETTER BU → LATIN SMALL LETTER O	#	//+ could do more harm than good
					{66604,111},	//	( 𐐬 → o ) DESERET SMALL LETTER LONG O → LATIN SMALL LETTER O	#
					//+ these were changed from LATIN CAPITAL LETTER O to DIGIT ZERO to avoid surprising users
					{1984,48},	//	( ‎߀‎ → 0 ) NKO DIGIT ZERO → DIGIT ZERO	#
					{2534,48},	//	( ০ → 0 ) BENGALI DIGIT ZERO → DIGIT ZERO	#
					{2918,48},	//	( ୦ → 0 ) ORIYA DIGIT ZERO → DIGIT ZERO	#
					//{12295,79},	//	( 〇 → O ) IDEOGRAPHIC NUMBER ZERO → LATIN CAPITAL LETTER O	#	//+ could do more harm than good
					{70864,48},	//	( 𑓐 → 0 ) TIRHUTA DIGIT ZERO → DIGIT ZERO	#
					{71904,48},	//	( 𑣠 → 0 ) WARANG CITI DIGIT ZERO → DIGIT ZERO	#
					{120782,48},	//	( 𝟎 → 0 ) MATHEMATICAL BOLD DIGIT ZERO → DIGIT ZERO	#
					{120792,48},	//	( 𝟘 → 0 ) MATHEMATICAL DOUBLE-STRUCK DIGIT ZERO → DIGIT ZERO	#
					{120802,48},	//	( 𝟢 → 0 ) MATHEMATICAL SANS-SERIF DIGIT ZERO → DIGIT ZERO	#
					{120812,48},	//	( 𝟬 → 0 ) MATHEMATICAL SANS-SERIF BOLD DIGIT ZERO → DIGIT ZERO	#
					{120822,48},	//	( 𝟶 → 0 ) MATHEMATICAL MONOSPACE DIGIT ZERO → DIGIT ZERO	#
					{130032,48},	//	( 🯰 → 0 ) SEGMENTED DIGIT ZERO → DIGIT ZERO	#
					//+
					{65327,79},	//	( Ｏ → O ) FULLWIDTH LATIN CAPITAL LETTER O → LATIN CAPITAL LETTER O	# →О→
					{119822,79},	//	( 𝐎 → O ) MATHEMATICAL BOLD CAPITAL O → LATIN CAPITAL LETTER O	#
					{119874,79},	//	( 𝑂 → O ) MATHEMATICAL ITALIC CAPITAL O → LATIN CAPITAL LETTER O	#
					{119926,79},	//	( 𝑶 → O ) MATHEMATICAL BOLD ITALIC CAPITAL O → LATIN CAPITAL LETTER O	#
					{119978,79},	//	( 𝒪 → O ) MATHEMATICAL SCRIPT CAPITAL O → LATIN CAPITAL LETTER O	#
					{120030,79},	//	( 𝓞 → O ) MATHEMATICAL BOLD SCRIPT CAPITAL O → LATIN CAPITAL LETTER O	#
					{120082,79},	//	( 𝔒 → O ) MATHEMATICAL FRAKTUR CAPITAL O → LATIN CAPITAL LETTER O	#
					{120134,79},	//	( 𝕆 → O ) MATHEMATICAL DOUBLE-STRUCK CAPITAL O → LATIN CAPITAL LETTER O	#
					{120186,79},	//	( 𝕺 → O ) MATHEMATICAL BOLD FRAKTUR CAPITAL O → LATIN CAPITAL LETTER O	#
					{120238,79},	//	( 𝖮 → O ) MATHEMATICAL SANS-SERIF CAPITAL O → LATIN CAPITAL LETTER O	#
					{120290,79},	//	( 𝗢 → O ) MATHEMATICAL SANS-SERIF BOLD CAPITAL O → LATIN CAPITAL LETTER O	#
					{120342,79},	//	( 𝘖 → O ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL O → LATIN CAPITAL LETTER O	#
					{120394,79},	//	( 𝙊 → O ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL O → LATIN CAPITAL LETTER O	#
					{120446,79},	//	( 𝙾 → O ) MATHEMATICAL MONOSPACE CAPITAL O → LATIN CAPITAL LETTER O	#
					{927,79},	//	( Ο → O ) GREEK CAPITAL LETTER OMICRON → LATIN CAPITAL LETTER O	#
					{120502,79},	//	( 𝚶 → O ) MATHEMATICAL BOLD CAPITAL OMICRON → LATIN CAPITAL LETTER O	# →𝐎→
					{120560,79},	//	( 𝛰 → O ) MATHEMATICAL ITALIC CAPITAL OMICRON → LATIN CAPITAL LETTER O	# →𝑂→
					{120618,79},	//	( 𝜪 → O ) MATHEMATICAL BOLD ITALIC CAPITAL OMICRON → LATIN CAPITAL LETTER O	# →𝑶→
					{120676,79},	//	( 𝝤 → O ) MATHEMATICAL SANS-SERIF BOLD CAPITAL OMICRON → LATIN CAPITAL LETTER O	# →Ο→
					{120734,79},	//	( 𝞞 → O ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL OMICRON → LATIN CAPITAL LETTER O	# →Ο→
					{11422,79},	//	( Ⲟ → O ) COPTIC CAPITAL LETTER O → LATIN CAPITAL LETTER O	#
					{1054,79},	//	( О → O ) CYRILLIC CAPITAL LETTER O → LATIN CAPITAL LETTER O	#
					{1365,79},	//	( Օ → O ) ARMENIAN CAPITAL LETTER OH → LATIN CAPITAL LETTER O	#
					//+ these could do more harm than good
					//{11604,79},	//	( ⵔ → O ) TIFINAGH LETTER YAR → LATIN CAPITAL LETTER O	#
					//{4816,79},	//	( ዐ → O ) ETHIOPIC SYLLABLE PHARYNGEAL A → LATIN CAPITAL LETTER O	# →Օ→
					//{2848,79},	//	( ଠ → O ) ORIYA LETTER TTHA → LATIN CAPITAL LETTER O	# →୦→→0→
					//{66754,79},	//	( 𐓂 → O ) OSAGE CAPITAL LETTER O → LATIN CAPITAL LETTER O	#
					//{42227,79},	//	( ꓳ → O ) LISU LETTER O → LATIN CAPITAL LETTER O	#
					//{71861,79},	//	( 𑢵 → O ) WARANG CITI CAPITAL LETTER AT → LATIN CAPITAL LETTER O	#
					//{66194,79},	//	( 𐊒 → O ) LYCIAN LETTER U → LATIN CAPITAL LETTER O	#
					//{66219,79},	//	( 𐊫 → O ) CARIAN LETTER O → LATIN CAPITAL LETTER O	#
					//+
					{66564,79},	//	( 𐐄 → O ) DESERET CAPITAL LETTER LONG O → LATIN CAPITAL LETTER O	#
					//{66838,79},	//	( 𐔖 → O ) ELBASAN LETTER O → LATIN CAPITAL LETTER O	#	//+ could do more harm than good
					//+ DEGREE SIGN is now mapped to MASCULINE ORDINAL INDICATOR, as these two are regularly confused.
					//+ Former mappings to DEGREE SIGN have been changed accordingly.
					{176,186},	//	( ° → º ) DEGREE SIGN → MASCULINE ORDINAL INDICATOR	#
					{11824,186},	//	* ( ⸰ → º ) RING POINT → MASCULINE ORDINAL INDICATOR	#
					{730,186},	//	* ( ˚ → º ) RING ABOVE → MASCULINE ORDINAL INDICATOR	#
					{8728,186},	//	* ( ∘ → º ) RING OPERATOR → MASCULINE ORDINAL INDICATOR	#
					//{9675,186},	//	* ( ○ → º ) WHITE CIRCLE → MASCULINE ORDINAL INDICATOR	#	//+ actually this one is unlikely
					{9702,186},	//	* ( ◦ → º ) WHITE BULLET → MASCULINE ORDINAL INDICATOR	#
					{8304,186},	//	* ( ⁰ → º ) SUPERSCRIPT ZERO → MASCULINE ORDINAL INDICATOR	#
					{7506,186},	//	( ᵒ → º ) MODIFIER LETTER SMALL O → MASCULINE ORDINAL INDICATOR	# →⁰→
					{336,214},	//	( Ő → Ö ) LATIN CAPITAL LETTER O WITH DOUBLE ACUTE → LATIN CAPITAL LETTER O WITH DIAERESIS	#
					{9076,112},	//	* ( ⍴ → p ) APL FUNCTIONAL SYMBOL RHO → LATIN SMALL LETTER P	# →ρ→
					{65360,112},	//	( ｐ → p ) FULLWIDTH LATIN SMALL LETTER P → LATIN SMALL LETTER P	# →р→
					{119849,112},	//	( 𝐩 → p ) MATHEMATICAL BOLD SMALL P → LATIN SMALL LETTER P	#
					{119901,112},	//	( 𝑝 → p ) MATHEMATICAL ITALIC SMALL P → LATIN SMALL LETTER P	#
					{119953,112},	//	( 𝒑 → p ) MATHEMATICAL BOLD ITALIC SMALL P → LATIN SMALL LETTER P	#
					{120005,112},	//	( 𝓅 → p ) MATHEMATICAL SCRIPT SMALL P → LATIN SMALL LETTER P	#
					{120057,112},	//	( 𝓹 → p ) MATHEMATICAL BOLD SCRIPT SMALL P → LATIN SMALL LETTER P	#
					{120109,112},	//	( 𝔭 → p ) MATHEMATICAL FRAKTUR SMALL P → LATIN SMALL LETTER P	#
					{120161,112},	//	( 𝕡 → p ) MATHEMATICAL DOUBLE-STRUCK SMALL P → LATIN SMALL LETTER P	#
					{120213,112},	//	( 𝖕 → p ) MATHEMATICAL BOLD FRAKTUR SMALL P → LATIN SMALL LETTER P	#
					{120265,112},	//	( 𝗉 → p ) MATHEMATICAL SANS-SERIF SMALL P → LATIN SMALL LETTER P	#
					{120317,112},	//	( 𝗽 → p ) MATHEMATICAL SANS-SERIF BOLD SMALL P → LATIN SMALL LETTER P	#
					{120369,112},	//	( 𝘱 → p ) MATHEMATICAL SANS-SERIF ITALIC SMALL P → LATIN SMALL LETTER P	#
					{120421,112},	//	( 𝙥 → p ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL P → LATIN SMALL LETTER P	#
					{120473,112},	//	( 𝚙 → p ) MATHEMATICAL MONOSPACE SMALL P → LATIN SMALL LETTER P	#
					{961,112},	//	( ρ → p ) GREEK SMALL LETTER RHO → LATIN SMALL LETTER P	#
					{1009,112},	//	( ϱ → p ) GREEK RHO SYMBOL → LATIN SMALL LETTER P	# →ρ→
					{120530,112},	//	( 𝛒 → p ) MATHEMATICAL BOLD SMALL RHO → LATIN SMALL LETTER P	# →ρ→
					{120544,112},	//	( 𝛠 → p ) MATHEMATICAL BOLD RHO SYMBOL → LATIN SMALL LETTER P	# →ρ→
					{120588,112},	//	( 𝜌 → p ) MATHEMATICAL ITALIC SMALL RHO → LATIN SMALL LETTER P	# →ρ→
					{120602,112},	//	( 𝜚 → p ) MATHEMATICAL ITALIC RHO SYMBOL → LATIN SMALL LETTER P	# →ρ→
					{120646,112},	//	( 𝝆 → p ) MATHEMATICAL BOLD ITALIC SMALL RHO → LATIN SMALL LETTER P	# →ρ→
					{120660,112},	//	( 𝝔 → p ) MATHEMATICAL BOLD ITALIC RHO SYMBOL → LATIN SMALL LETTER P	# →ρ→
					{120704,112},	//	( 𝞀 → p ) MATHEMATICAL SANS-SERIF BOLD SMALL RHO → LATIN SMALL LETTER P	# →ρ→
					{120718,112},	//	( 𝞎 → p ) MATHEMATICAL SANS-SERIF BOLD RHO SYMBOL → LATIN SMALL LETTER P	# →ρ→
					{120762,112},	//	( 𝞺 → p ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL RHO → LATIN SMALL LETTER P	# →ρ→
					{120776,112},	//	( 𝟈 → p ) MATHEMATICAL SANS-SERIF BOLD ITALIC RHO SYMBOL → LATIN SMALL LETTER P	# →ρ→
					{11427,112},	//	( ⲣ → p ) COPTIC SMALL LETTER RO → LATIN SMALL LETTER P	# →ρ→
					{1088,112},	//	( р → p ) CYRILLIC SMALL LETTER ER → LATIN SMALL LETTER P	#
					{65328,80},	//	( Ｐ → P ) FULLWIDTH LATIN CAPITAL LETTER P → LATIN CAPITAL LETTER P	# →Р→
					{8473,80},	//	( ℙ → P ) DOUBLE-STRUCK CAPITAL P → LATIN CAPITAL LETTER P	#
					{119823,80},	//	( 𝐏 → P ) MATHEMATICAL BOLD CAPITAL P → LATIN CAPITAL LETTER P	#
					{119875,80},	//	( 𝑃 → P ) MATHEMATICAL ITALIC CAPITAL P → LATIN CAPITAL LETTER P	#
					{119927,80},	//	( 𝑷 → P ) MATHEMATICAL BOLD ITALIC CAPITAL P → LATIN CAPITAL LETTER P	#
					{119979,80},	//	( 𝒫 → P ) MATHEMATICAL SCRIPT CAPITAL P → LATIN CAPITAL LETTER P	#
					{120031,80},	//	( 𝓟 → P ) MATHEMATICAL BOLD SCRIPT CAPITAL P → LATIN CAPITAL LETTER P	#
					{120083,80},	//	( 𝔓 → P ) MATHEMATICAL FRAKTUR CAPITAL P → LATIN CAPITAL LETTER P	#
					{120187,80},	//	( 𝕻 → P ) MATHEMATICAL BOLD FRAKTUR CAPITAL P → LATIN CAPITAL LETTER P	#
					{120239,80},	//	( 𝖯 → P ) MATHEMATICAL SANS-SERIF CAPITAL P → LATIN CAPITAL LETTER P	#
					{120291,80},	//	( 𝗣 → P ) MATHEMATICAL SANS-SERIF BOLD CAPITAL P → LATIN CAPITAL LETTER P	#
					{120343,80},	//	( 𝘗 → P ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL P → LATIN CAPITAL LETTER P	#
					{120395,80},	//	( 𝙋 → P ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL P → LATIN CAPITAL LETTER P	#
					{120447,80},	//	( 𝙿 → P ) MATHEMATICAL MONOSPACE CAPITAL P → LATIN CAPITAL LETTER P	#
					{929,80},	//	( Ρ → P ) GREEK CAPITAL LETTER RHO → LATIN CAPITAL LETTER P	#
					{120504,80},	//	( 𝚸 → P ) MATHEMATICAL BOLD CAPITAL RHO → LATIN CAPITAL LETTER P	# →𝐏→
					{120562,80},	//	( 𝛲 → P ) MATHEMATICAL ITALIC CAPITAL RHO → LATIN CAPITAL LETTER P	# →Ρ→
					{120620,80},	//	( 𝜬 → P ) MATHEMATICAL BOLD ITALIC CAPITAL RHO → LATIN CAPITAL LETTER P	# →Ρ→
					{120678,80},	//	( 𝝦 → P ) MATHEMATICAL SANS-SERIF BOLD CAPITAL RHO → LATIN CAPITAL LETTER P	# →Ρ→
					{120736,80},	//	( 𝞠 → P ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL RHO → LATIN CAPITAL LETTER P	# →Ρ→
					{11426,80},	//	( Ⲣ → P ) COPTIC CAPITAL LETTER RO → LATIN CAPITAL LETTER P	#
					{1056,80},	//	( Р → P ) CYRILLIC CAPITAL LETTER ER → LATIN CAPITAL LETTER P	#
					//+ these could do more harm than good
					//{5090,80},	//	( Ꮲ → P ) CHEROKEE LETTER TLV → LATIN CAPITAL LETTER P	#
					//{5229,80},	//	( ᑭ → P ) CANADIAN SYLLABICS KI → LATIN CAPITAL LETTER P	#
					//{42193,80},	//	( ꓑ → P ) LISU LETTER PA → LATIN CAPITAL LETTER P	#
					//{66197,80},	//	( 𐊕 → P ) LYCIAN LETTER R → LATIN CAPITAL LETTER P	#
					//+
					{119850,113},	//	( 𝐪 → q ) MATHEMATICAL BOLD SMALL Q → LATIN SMALL LETTER Q	#
					{119902,113},	//	( 𝑞 → q ) MATHEMATICAL ITALIC SMALL Q → LATIN SMALL LETTER Q	#
					{119954,113},	//	( 𝒒 → q ) MATHEMATICAL BOLD ITALIC SMALL Q → LATIN SMALL LETTER Q	#
					{120006,113},	//	( 𝓆 → q ) MATHEMATICAL SCRIPT SMALL Q → LATIN SMALL LETTER Q	#
					{120058,113},	//	( 𝓺 → q ) MATHEMATICAL BOLD SCRIPT SMALL Q → LATIN SMALL LETTER Q	#
					{120110,113},	//	( 𝔮 → q ) MATHEMATICAL FRAKTUR SMALL Q → LATIN SMALL LETTER Q	#
					{120162,113},	//	( 𝕢 → q ) MATHEMATICAL DOUBLE-STRUCK SMALL Q → LATIN SMALL LETTER Q	#
					{120214,113},	//	( 𝖖 → q ) MATHEMATICAL BOLD FRAKTUR SMALL Q → LATIN SMALL LETTER Q	#
					{120266,113},	//	( 𝗊 → q ) MATHEMATICAL SANS-SERIF SMALL Q → LATIN SMALL LETTER Q	#
					{120318,113},	//	( 𝗾 → q ) MATHEMATICAL SANS-SERIF BOLD SMALL Q → LATIN SMALL LETTER Q	#
					{120370,113},	//	( 𝘲 → q ) MATHEMATICAL SANS-SERIF ITALIC SMALL Q → LATIN SMALL LETTER Q	#
					{120422,113},	//	( 𝙦 → q ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL Q → LATIN SMALL LETTER Q	#
					{120474,113},	//	( 𝚚 → q ) MATHEMATICAL MONOSPACE SMALL Q → LATIN SMALL LETTER Q	#
					{1307,113},	//	( ԛ → q ) CYRILLIC SMALL LETTER QA → LATIN SMALL LETTER Q	#
					//{1379,113},	//	( գ → q ) ARMENIAN SMALL LETTER GIM → LATIN SMALL LETTER Q	#	//+ could do more harm than good
					//{1382,113},	//	( զ → q ) ARMENIAN SMALL LETTER ZA → LATIN SMALL LETTER Q	#	//+ could do more harm than good
					{8474,81},	//	( ℚ → Q ) DOUBLE-STRUCK CAPITAL Q → LATIN CAPITAL LETTER Q	#
					{119824,81},	//	( 𝐐 → Q ) MATHEMATICAL BOLD CAPITAL Q → LATIN CAPITAL LETTER Q	#
					{119876,81},	//	( 𝑄 → Q ) MATHEMATICAL ITALIC CAPITAL Q → LATIN CAPITAL LETTER Q	#
					{119928,81},	//	( 𝑸 → Q ) MATHEMATICAL BOLD ITALIC CAPITAL Q → LATIN CAPITAL LETTER Q	#
					{119980,81},	//	( 𝒬 → Q ) MATHEMATICAL SCRIPT CAPITAL Q → LATIN CAPITAL LETTER Q	#
					{120032,81},	//	( 𝓠 → Q ) MATHEMATICAL BOLD SCRIPT CAPITAL Q → LATIN CAPITAL LETTER Q	#
					{120084,81},	//	( 𝔔 → Q ) MATHEMATICAL FRAKTUR CAPITAL Q → LATIN CAPITAL LETTER Q	#
					{120188,81},	//	( 𝕼 → Q ) MATHEMATICAL BOLD FRAKTUR CAPITAL Q → LATIN CAPITAL LETTER Q	#
					{120240,81},	//	( 𝖰 → Q ) MATHEMATICAL SANS-SERIF CAPITAL Q → LATIN CAPITAL LETTER Q	#
					{120292,81},	//	( 𝗤 → Q ) MATHEMATICAL SANS-SERIF BOLD CAPITAL Q → LATIN CAPITAL LETTER Q	#
					{120344,81},	//	( 𝘘 → Q ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL Q → LATIN CAPITAL LETTER Q	#
					{120396,81},	//	( 𝙌 → Q ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL Q → LATIN CAPITAL LETTER Q	#
					{120448,81},	//	( 𝚀 → Q ) MATHEMATICAL MONOSPACE CAPITAL Q → LATIN CAPITAL LETTER Q	#
					//{11605,81},	//	( ⵕ → Q ) TIFINAGH LETTER YARR → LATIN CAPITAL LETTER Q	#	//+ could do more harm than good
					{119851,114},	//	( 𝐫 → r ) MATHEMATICAL BOLD SMALL R → LATIN SMALL LETTER R	#
					{119903,114},	//	( 𝑟 → r ) MATHEMATICAL ITALIC SMALL R → LATIN SMALL LETTER R	#
					{119955,114},	//	( 𝒓 → r ) MATHEMATICAL BOLD ITALIC SMALL R → LATIN SMALL LETTER R	#
					{120007,114},	//	( 𝓇 → r ) MATHEMATICAL SCRIPT SMALL R → LATIN SMALL LETTER R	#
					{120059,114},	//	( 𝓻 → r ) MATHEMATICAL BOLD SCRIPT SMALL R → LATIN SMALL LETTER R	#
					{120111,114},	//	( 𝔯 → r ) MATHEMATICAL FRAKTUR SMALL R → LATIN SMALL LETTER R	#
					{120163,114},	//	( 𝕣 → r ) MATHEMATICAL DOUBLE-STRUCK SMALL R → LATIN SMALL LETTER R	#
					{120215,114},	//	( 𝖗 → r ) MATHEMATICAL BOLD FRAKTUR SMALL R → LATIN SMALL LETTER R	#
					{120267,114},	//	( 𝗋 → r ) MATHEMATICAL SANS-SERIF SMALL R → LATIN SMALL LETTER R	#
					{120319,114},	//	( 𝗿 → r ) MATHEMATICAL SANS-SERIF BOLD SMALL R → LATIN SMALL LETTER R	#
					{120371,114},	//	( 𝘳 → r ) MATHEMATICAL SANS-SERIF ITALIC SMALL R → LATIN SMALL LETTER R	#
					{120423,114},	//	( 𝙧 → r ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL R → LATIN SMALL LETTER R	#
					{120475,114},	//	( 𝚛 → r ) MATHEMATICAL MONOSPACE SMALL R → LATIN SMALL LETTER R	#
					{43847,114},	//	( ꭇ → r ) LATIN SMALL LETTER R WITHOUT HANDLE → LATIN SMALL LETTER R	#
					{43848,114},	//	( ꭈ → r ) LATIN SMALL LETTER DOUBLE R → LATIN SMALL LETTER R	#
					//+ these would definitely do more harm than good
					//{7462,114},	//	( ᴦ → r ) GREEK LETTER SMALL CAPITAL GAMMA → LATIN SMALL LETTER R	# →г→
					//{11397,114},	//	( ⲅ → r ) COPTIC SMALL LETTER GAMMA → LATIN SMALL LETTER R	# →г→
					//{1075,114},	//	( г → r ) CYRILLIC SMALL LETTER GHE → LATIN SMALL LETTER R	#
					//{43905,114},	//	( ꮁ → r ) CHEROKEE SMALL LETTER HU → LATIN SMALL LETTER R	# →ᴦ→→г→
					//+
					{119318,82},	//	* ( 𝈖 → R ) GREEK VOCAL NOTATION SYMBOL-23 → LATIN CAPITAL LETTER R	#
					{8475,82},	//	( ℛ → R ) SCRIPT CAPITAL R → LATIN CAPITAL LETTER R	#
					{8476,82},	//	( ℜ → R ) BLACK-LETTER CAPITAL R → LATIN CAPITAL LETTER R	#
					{8477,82},	//	( ℝ → R ) DOUBLE-STRUCK CAPITAL R → LATIN CAPITAL LETTER R	#
					{119825,82},	//	( 𝐑 → R ) MATHEMATICAL BOLD CAPITAL R → LATIN CAPITAL LETTER R	#
					{119877,82},	//	( 𝑅 → R ) MATHEMATICAL ITALIC CAPITAL R → LATIN CAPITAL LETTER R	#
					{119929,82},	//	( 𝑹 → R ) MATHEMATICAL BOLD ITALIC CAPITAL R → LATIN CAPITAL LETTER R	#
					{120033,82},	//	( 𝓡 → R ) MATHEMATICAL BOLD SCRIPT CAPITAL R → LATIN CAPITAL LETTER R	#
					{120189,82},	//	( 𝕽 → R ) MATHEMATICAL BOLD FRAKTUR CAPITAL R → LATIN CAPITAL LETTER R	#
					{120241,82},	//	( 𝖱 → R ) MATHEMATICAL SANS-SERIF CAPITAL R → LATIN CAPITAL LETTER R	#
					{120293,82},	//	( 𝗥 → R ) MATHEMATICAL SANS-SERIF BOLD CAPITAL R → LATIN CAPITAL LETTER R	#
					{120345,82},	//	( 𝘙 → R ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL R → LATIN CAPITAL LETTER R	#
					{120397,82},	//	( 𝙍 → R ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL R → LATIN CAPITAL LETTER R	#
					{120449,82},	//	( 𝚁 → R ) MATHEMATICAL MONOSPACE CAPITAL R → LATIN CAPITAL LETTER R	#
					{422,82},	//	( Ʀ → R ) LATIN LETTER YR → LATIN CAPITAL LETTER R	#
					//+ these could do more harm than good
					//{5025,82},	//	( Ꭱ → R ) CHEROKEE LETTER E → LATIN CAPITAL LETTER R	#
					//{5074,82},	//	( Ꮢ → R ) CHEROKEE LETTER SV → LATIN CAPITAL LETTER R	#
					//{66740,82},	//	( 𐒴 → R ) OSAGE CAPITAL LETTER BRA → LATIN CAPITAL LETTER R	# →Ʀ→
					//{5511,82},	//	( ᖇ → R ) CANADIAN SYLLABICS TLHI → LATIN CAPITAL LETTER R	#
					//{42211,82},	//	( ꓣ → R ) LISU LETTER ZHA → LATIN CAPITAL LETTER R	#
					//{94005,82},	//	( 𖼵 → R ) MIAO LETTER ZHA → LATIN CAPITAL LETTER R	#
					//+
					{65363,115},	//	( ｓ → s ) FULLWIDTH LATIN SMALL LETTER S → LATIN SMALL LETTER S	# →ѕ→
					{119852,115},	//	( 𝐬 → s ) MATHEMATICAL BOLD SMALL S → LATIN SMALL LETTER S	#
					{119904,115},	//	( 𝑠 → s ) MATHEMATICAL ITALIC SMALL S → LATIN SMALL LETTER S	#
					{119956,115},	//	( 𝒔 → s ) MATHEMATICAL BOLD ITALIC SMALL S → LATIN SMALL LETTER S	#
					{120008,115},	//	( 𝓈 → s ) MATHEMATICAL SCRIPT SMALL S → LATIN SMALL LETTER S	#
					{120060,115},	//	( 𝓼 → s ) MATHEMATICAL BOLD SCRIPT SMALL S → LATIN SMALL LETTER S	#
					{120112,115},	//	( 𝔰 → s ) MATHEMATICAL FRAKTUR SMALL S → LATIN SMALL LETTER S	#
					{120164,115},	//	( 𝕤 → s ) MATHEMATICAL DOUBLE-STRUCK SMALL S → LATIN SMALL LETTER S	#
					{120216,115},	//	( 𝖘 → s ) MATHEMATICAL BOLD FRAKTUR SMALL S → LATIN SMALL LETTER S	#
					{120268,115},	//	( 𝗌 → s ) MATHEMATICAL SANS-SERIF SMALL S → LATIN SMALL LETTER S	#
					{120320,115},	//	( 𝘀 → s ) MATHEMATICAL SANS-SERIF BOLD SMALL S → LATIN SMALL LETTER S	#
					{120372,115},	//	( 𝘴 → s ) MATHEMATICAL SANS-SERIF ITALIC SMALL S → LATIN SMALL LETTER S	#
					{120424,115},	//	( 𝙨 → s ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL S → LATIN SMALL LETTER S	#
					{120476,115},	//	( 𝚜 → s ) MATHEMATICAL MONOSPACE SMALL S → LATIN SMALL LETTER S	#
					{42801,115},	//	( ꜱ → s ) LATIN LETTER SMALL CAPITAL S → LATIN SMALL LETTER S	#
					{445,53},	//	( ƽ → 5 ) LATIN SMALL LETTER TONE FIVE → DIGIT FIVE	#	//+ changed to match LATIN CAPITAL LETTER TONE FIVE
					{1109,115},	//	( ѕ → s ) CYRILLIC SMALL LETTER DZE → LATIN SMALL LETTER S	#
					//{43946,115},	//	( ꮪ → s ) CHEROKEE SMALL LETTER DU → LATIN SMALL LETTER S	# →ꜱ→	//+ could do more harm than good
					//{71873,115},	//	( 𑣁 → s ) WARANG CITI SMALL LETTER A → LATIN SMALL LETTER S	#	//+ could do more harm than good
					{66632,115},	//	( 𐑈 → s ) DESERET SMALL LETTER ZHEE → LATIN SMALL LETTER S	#
					{65331,83},	//	( Ｓ → S ) FULLWIDTH LATIN CAPITAL LETTER S → LATIN CAPITAL LETTER S	# →Ѕ→
					{119826,83},	//	( 𝐒 → S ) MATHEMATICAL BOLD CAPITAL S → LATIN CAPITAL LETTER S	#
					{119878,83},	//	( 𝑆 → S ) MATHEMATICAL ITALIC CAPITAL S → LATIN CAPITAL LETTER S	#
					{119930,83},	//	( 𝑺 → S ) MATHEMATICAL BOLD ITALIC CAPITAL S → LATIN CAPITAL LETTER S	#
					{119982,83},	//	( 𝒮 → S ) MATHEMATICAL SCRIPT CAPITAL S → LATIN CAPITAL LETTER S	#
					{120034,83},	//	( 𝓢 → S ) MATHEMATICAL BOLD SCRIPT CAPITAL S → LATIN CAPITAL LETTER S	#
					{120086,83},	//	( 𝔖 → S ) MATHEMATICAL FRAKTUR CAPITAL S → LATIN CAPITAL LETTER S	#
					{120138,83},	//	( 𝕊 → S ) MATHEMATICAL DOUBLE-STRUCK CAPITAL S → LATIN CAPITAL LETTER S	#
					{120190,83},	//	( 𝕾 → S ) MATHEMATICAL BOLD FRAKTUR CAPITAL S → LATIN CAPITAL LETTER S	#
					{120242,83},	//	( 𝖲 → S ) MATHEMATICAL SANS-SERIF CAPITAL S → LATIN CAPITAL LETTER S	#
					{120294,83},	//	( 𝗦 → S ) MATHEMATICAL SANS-SERIF BOLD CAPITAL S → LATIN CAPITAL LETTER S	#
					{120346,83},	//	( 𝘚 → S ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL S → LATIN CAPITAL LETTER S	#
					{120398,83},	//	( 𝙎 → S ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL S → LATIN CAPITAL LETTER S	#
					{120450,83},	//	( 𝚂 → S ) MATHEMATICAL MONOSPACE CAPITAL S → LATIN CAPITAL LETTER S	#
					{1029,83},	//	( Ѕ → S ) CYRILLIC CAPITAL LETTER DZE → LATIN CAPITAL LETTER S	#
					//+ these could do more harm than good
					//{1359,83},	//	( Տ → S ) ARMENIAN CAPITAL LETTER TIWN → LATIN CAPITAL LETTER S	#
					//{5077,83},	//	( Ꮥ → S ) CHEROKEE LETTER DE → LATIN CAPITAL LETTER S	#
					//{5082,83},	//	( Ꮪ → S ) CHEROKEE LETTER DU → LATIN CAPITAL LETTER S	#
					//{42210,83},	//	( ꓢ → S ) LISU LETTER SA → LATIN CAPITAL LETTER S	#
					//{94010,83},	//	( 𖼺 → S ) MIAO LETTER SA → LATIN CAPITAL LETTER S	#
					//{66198,83},	//	( 𐊖 → S ) LYCIAN LETTER S → LATIN CAPITAL LETTER S	#
					//+
					{66592,83},	//	( 𐐠 → S ) DESERET CAPITAL LETTER ZHEE → LATIN CAPITAL LETTER S	#
					//+ these would break case-insensitive matching, unfortunately
					//{42933,223},	//	( ꞵ → ß ) LATIN SMALL LETTER BETA → LATIN SMALL LETTER SHARP S	# →β→
					//{946,223},	//	( β → ß ) GREEK SMALL LETTER BETA → LATIN SMALL LETTER SHARP S	#
					//{976,223},	//	( ϐ → ß ) GREEK BETA SYMBOL → LATIN SMALL LETTER SHARP S	# →β→
					//+ these would be perhaps better mapped to LATIN SMALL LETTER BETA, but for now I’m going to leave them as they are
					{120515,223},	//	( 𝛃 → ß ) MATHEMATICAL BOLD SMALL BETA → LATIN SMALL LETTER SHARP S	# →β→
					{120573,223},	//	( 𝛽 → ß ) MATHEMATICAL ITALIC SMALL BETA → LATIN SMALL LETTER SHARP S	# →β→
					{120631,223},	//	( 𝜷 → ß ) MATHEMATICAL BOLD ITALIC SMALL BETA → LATIN SMALL LETTER SHARP S	# →β→
					{120689,223},	//	( 𝝱 → ß ) MATHEMATICAL SANS-SERIF BOLD SMALL BETA → LATIN SMALL LETTER SHARP S	# →β→
					{120747,223},	//	( 𝞫 → ß ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL BETA → LATIN SMALL LETTER SHARP S	# →β→
					//+
					//{5104,223},	//	( Ᏸ → ß ) CHEROKEE LETTER YE → LATIN SMALL LETTER SHARP S	# →β→	//+ would break case-insensitive matching
					{119853,116},	//	( 𝐭 → t ) MATHEMATICAL BOLD SMALL T → LATIN SMALL LETTER T	#
					{119905,116},	//	( 𝑡 → t ) MATHEMATICAL ITALIC SMALL T → LATIN SMALL LETTER T	#
					{119957,116},	//	( 𝒕 → t ) MATHEMATICAL BOLD ITALIC SMALL T → LATIN SMALL LETTER T	#
					{120009,116},	//	( 𝓉 → t ) MATHEMATICAL SCRIPT SMALL T → LATIN SMALL LETTER T	#
					{120061,116},	//	( 𝓽 → t ) MATHEMATICAL BOLD SCRIPT SMALL T → LATIN SMALL LETTER T	#
					{120113,116},	//	( 𝔱 → t ) MATHEMATICAL FRAKTUR SMALL T → LATIN SMALL LETTER T	#
					{120165,116},	//	( 𝕥 → t ) MATHEMATICAL DOUBLE-STRUCK SMALL T → LATIN SMALL LETTER T	#
					{120217,116},	//	( 𝖙 → t ) MATHEMATICAL BOLD FRAKTUR SMALL T → LATIN SMALL LETTER T	#
					{120269,116},	//	( 𝗍 → t ) MATHEMATICAL SANS-SERIF SMALL T → LATIN SMALL LETTER T	#
					{120321,116},	//	( 𝘁 → t ) MATHEMATICAL SANS-SERIF BOLD SMALL T → LATIN SMALL LETTER T	#
					{120373,116},	//	( 𝘵 → t ) MATHEMATICAL SANS-SERIF ITALIC SMALL T → LATIN SMALL LETTER T	#
					{120425,116},	//	( 𝙩 → t ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL T → LATIN SMALL LETTER T	#
					{120477,116},	//	( 𝚝 → t ) MATHEMATICAL MONOSPACE SMALL T → LATIN SMALL LETTER T	#
					{8868,84},	//	* ( ⊤ → T ) DOWN TACK → LATIN CAPITAL LETTER T	#
					{10201,84},	//	* ( ⟙ → T ) LARGE DOWN TACK → LATIN CAPITAL LETTER T	#
					{128872,84},	//	* ( 🝨 → T ) ALCHEMICAL SYMBOL FOR CRUCIBLE-4 → LATIN CAPITAL LETTER T	#
					{65332,84},	//	( Ｔ → T ) FULLWIDTH LATIN CAPITAL LETTER T → LATIN CAPITAL LETTER T	# →Т→
					{119827,84},	//	( 𝐓 → T ) MATHEMATICAL BOLD CAPITAL T → LATIN CAPITAL LETTER T	#
					{119879,84},	//	( 𝑇 → T ) MATHEMATICAL ITALIC CAPITAL T → LATIN CAPITAL LETTER T	#
					{119931,84},	//	( 𝑻 → T ) MATHEMATICAL BOLD ITALIC CAPITAL T → LATIN CAPITAL LETTER T	#
					{119983,84},	//	( 𝒯 → T ) MATHEMATICAL SCRIPT CAPITAL T → LATIN CAPITAL LETTER T	#
					{120035,84},	//	( 𝓣 → T ) MATHEMATICAL BOLD SCRIPT CAPITAL T → LATIN CAPITAL LETTER T	#
					{120087,84},	//	( 𝔗 → T ) MATHEMATICAL FRAKTUR CAPITAL T → LATIN CAPITAL LETTER T	#
					{120139,84},	//	( 𝕋 → T ) MATHEMATICAL DOUBLE-STRUCK CAPITAL T → LATIN CAPITAL LETTER T	#
					{120191,84},	//	( 𝕿 → T ) MATHEMATICAL BOLD FRAKTUR CAPITAL T → LATIN CAPITAL LETTER T	#
					{120243,84},	//	( 𝖳 → T ) MATHEMATICAL SANS-SERIF CAPITAL T → LATIN CAPITAL LETTER T	#
					{120295,84},	//	( 𝗧 → T ) MATHEMATICAL SANS-SERIF BOLD CAPITAL T → LATIN CAPITAL LETTER T	#
					{120347,84},	//	( 𝘛 → T ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL T → LATIN CAPITAL LETTER T	#
					{120399,84},	//	( 𝙏 → T ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL T → LATIN CAPITAL LETTER T	#
					{120451,84},	//	( 𝚃 → T ) MATHEMATICAL MONOSPACE CAPITAL T → LATIN CAPITAL LETTER T	#
					//{932,84},	//	( Τ → T ) GREEK CAPITAL LETTER TAU → LATIN CAPITAL LETTER T	#	//+ would break case-insensitive matching, unfortunately
					{120507,84},	//	( 𝚻 → T ) MATHEMATICAL BOLD CAPITAL TAU → LATIN CAPITAL LETTER T	# →Τ→
					{120565,84},	//	( 𝛵 → T ) MATHEMATICAL ITALIC CAPITAL TAU → LATIN CAPITAL LETTER T	# →Τ→
					{120623,84},	//	( 𝜯 → T ) MATHEMATICAL BOLD ITALIC CAPITAL TAU → LATIN CAPITAL LETTER T	# →Τ→
					{120681,84},	//	( 𝝩 → T ) MATHEMATICAL SANS-SERIF BOLD CAPITAL TAU → LATIN CAPITAL LETTER T	# →Τ→
					{120739,84},	//	( 𝞣 → T ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL TAU → LATIN CAPITAL LETTER T	# →Τ→
					//{11430,84},	//	( Ⲧ → T ) COPTIC CAPITAL LETTER TAU → LATIN CAPITAL LETTER T	#	//+ would break case-insensitive matching, unfortunately
					//{1058,84},	//	( Т → T ) CYRILLIC CAPITAL LETTER TE → LATIN CAPITAL LETTER T	#	//+ would break case-insensitive matching, unfortunately
					//{5026,84},	//	( Ꭲ → T ) CHEROKEE LETTER I → LATIN CAPITAL LETTER T	#	//+ would break case-insensitive matching
					//+ these could do more harm than good
					//{42196,84},	//	( ꓔ → T ) LISU LETTER TA → LATIN CAPITAL LETTER T	#
					//{93962,84},	//	( 𖼊 → T ) MIAO LETTER TA → LATIN CAPITAL LETTER T	#
					//{71868,84},	//	( 𑢼 → T ) WARANG CITI CAPITAL LETTER HAR → LATIN CAPITAL LETTER T	#
					//{66199,84},	//	( 𐊗 → T ) LYCIAN LETTER T → LATIN CAPITAL LETTER T	#
					//{66225,84},	//	( 𐊱 → T ) CARIAN LETTER C-18 → LATIN CAPITAL LETTER T	#
					//{66325,84},	//	( 𐌕 → T ) OLD ITALIC LETTER TE → LATIN CAPITAL LETTER T	#
					//+
					{119854,117},	//	( 𝐮 → u ) MATHEMATICAL BOLD SMALL U → LATIN SMALL LETTER U	#
					{119906,117},	//	( 𝑢 → u ) MATHEMATICAL ITALIC SMALL U → LATIN SMALL LETTER U	#
					{119958,117},	//	( 𝒖 → u ) MATHEMATICAL BOLD ITALIC SMALL U → LATIN SMALL LETTER U	#
					{120010,117},	//	( 𝓊 → u ) MATHEMATICAL SCRIPT SMALL U → LATIN SMALL LETTER U	#
					{120062,117},	//	( 𝓾 → u ) MATHEMATICAL BOLD SCRIPT SMALL U → LATIN SMALL LETTER U	#
					{120114,117},	//	( 𝔲 → u ) MATHEMATICAL FRAKTUR SMALL U → LATIN SMALL LETTER U	#
					{120166,117},	//	( 𝕦 → u ) MATHEMATICAL DOUBLE-STRUCK SMALL U → LATIN SMALL LETTER U	#
					{120218,117},	//	( 𝖚 → u ) MATHEMATICAL BOLD FRAKTUR SMALL U → LATIN SMALL LETTER U	#
					{120270,117},	//	( 𝗎 → u ) MATHEMATICAL SANS-SERIF SMALL U → LATIN SMALL LETTER U	#
					{120322,117},	//	( 𝘂 → u ) MATHEMATICAL SANS-SERIF BOLD SMALL U → LATIN SMALL LETTER U	#
					{120374,117},	//	( 𝘶 → u ) MATHEMATICAL SANS-SERIF ITALIC SMALL U → LATIN SMALL LETTER U	#
					{120426,117},	//	( 𝙪 → u ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL U → LATIN SMALL LETTER U	#
					{120478,117},	//	( 𝚞 → u ) MATHEMATICAL MONOSPACE SMALL U → LATIN SMALL LETTER U	#
					{42911,117},	//	( ꞟ → u ) LATIN SMALL LETTER VOLAPUK UE → LATIN SMALL LETTER U	#
					{7452,117},	//	( ᴜ → u ) LATIN LETTER SMALL CAPITAL U → LATIN SMALL LETTER U	#
					{43854,117},	//	( ꭎ → u ) LATIN SMALL LETTER U WITH SHORT RIGHT LEG → LATIN SMALL LETTER U	#
					{43858,117},	//	( ꭒ → u ) LATIN SMALL LETTER U WITH LEFT HOOK → LATIN SMALL LETTER U	#
					//{651,117},	//	( ʋ → u ) LATIN SMALL LETTER V WITH HOOK → LATIN SMALL LETTER U	#	//+ would break case-insensitive matching
					//{965,117},	//	( υ → u ) GREEK SMALL LETTER UPSILON → LATIN SMALL LETTER U	# →ʋ→	//+ would break case-insensitive matching, unfortunately
					{120534,117},	//	( 𝛖 → u ) MATHEMATICAL BOLD SMALL UPSILON → LATIN SMALL LETTER U	# →υ→→ʋ→
					{120592,117},	//	( 𝜐 → u ) MATHEMATICAL ITALIC SMALL UPSILON → LATIN SMALL LETTER U	# →υ→→ʋ→
					{120650,117},	//	( 𝝊 → u ) MATHEMATICAL BOLD ITALIC SMALL UPSILON → LATIN SMALL LETTER U	# →υ→→ʋ→
					{120708,117},	//	( 𝞄 → u ) MATHEMATICAL SANS-SERIF BOLD SMALL UPSILON → LATIN SMALL LETTER U	# →υ→→ʋ→
					{120766,117},	//	( 𝞾 → u ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL UPSILON → LATIN SMALL LETTER U	# →υ→→ʋ→
					//+ these could do more harm than good
					//{1405,117},	//	( ս → u ) ARMENIAN SMALL LETTER SEH → LATIN SMALL LETTER U	#
					//{66806,117},	//	( 𐓶 → u ) OSAGE SMALL LETTER U → LATIN SMALL LETTER U	# →ᴜ→
					//{71896,117},	//	( 𑣘 → u ) WARANG CITI SMALL LETTER PU → LATIN SMALL LETTER U	# →υ→→ʋ→
					//+
					{8746,85},	//	* ( ∪ → U ) UNION → LATIN CAPITAL LETTER U	# →ᑌ→
					{8899,85},	//	* ( ⋃ → U ) N-ARY UNION → LATIN CAPITAL LETTER U	# →∪→→ᑌ→
					{119828,85},	//	( 𝐔 → U ) MATHEMATICAL BOLD CAPITAL U → LATIN CAPITAL LETTER U	#
					{119880,85},	//	( 𝑈 → U ) MATHEMATICAL ITALIC CAPITAL U → LATIN CAPITAL LETTER U	#
					{119932,85},	//	( 𝑼 → U ) MATHEMATICAL BOLD ITALIC CAPITAL U → LATIN CAPITAL LETTER U	#
					{119984,85},	//	( 𝒰 → U ) MATHEMATICAL SCRIPT CAPITAL U → LATIN CAPITAL LETTER U	#
					{120036,85},	//	( 𝓤 → U ) MATHEMATICAL BOLD SCRIPT CAPITAL U → LATIN CAPITAL LETTER U	#
					{120088,85},	//	( 𝔘 → U ) MATHEMATICAL FRAKTUR CAPITAL U → LATIN CAPITAL LETTER U	#
					{120140,85},	//	( 𝕌 → U ) MATHEMATICAL DOUBLE-STRUCK CAPITAL U → LATIN CAPITAL LETTER U	#
					{120192,85},	//	( 𝖀 → U ) MATHEMATICAL BOLD FRAKTUR CAPITAL U → LATIN CAPITAL LETTER U	#
					{120244,85},	//	( 𝖴 → U ) MATHEMATICAL SANS-SERIF CAPITAL U → LATIN CAPITAL LETTER U	#
					{120296,85},	//	( 𝗨 → U ) MATHEMATICAL SANS-SERIF BOLD CAPITAL U → LATIN CAPITAL LETTER U	#
					{120348,85},	//	( 𝘜 → U ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL U → LATIN CAPITAL LETTER U	#
					{120400,85},	//	( 𝙐 → U ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL U → LATIN CAPITAL LETTER U	#
					{120452,85},	//	( 𝚄 → U ) MATHEMATICAL MONOSPACE CAPITAL U → LATIN CAPITAL LETTER U	#
					//+ these could do more harm than good
					//{1357,85},	//	( Ս → U ) ARMENIAN CAPITAL LETTER SEH → LATIN CAPITAL LETTER U	#
					//{4608,85},	//	( ሀ → U ) ETHIOPIC SYLLABLE HA → LATIN CAPITAL LETTER U	# →Ս→
					//{66766,85},	//	( 𐓎 → U ) OSAGE CAPITAL LETTER U → LATIN CAPITAL LETTER U	#
					//{5196,85},	//	( ᑌ → U ) CANADIAN SYLLABICS TE → LATIN CAPITAL LETTER U	#
					//{42228,85},	//	( ꓴ → U ) LISU LETTER U → LATIN CAPITAL LETTER U	#
					//{94018,85},	//	( 𖽂 → U ) MIAO LETTER WA → LATIN CAPITAL LETTER U	#
					//{71864,85},	//	( 𑢸 → U ) WARANG CITI CAPITAL LETTER PU → LATIN CAPITAL LETTER U	#
					{8744,118},	//	* ( ∨ → v ) LOGICAL OR → LATIN SMALL LETTER V	#
					{8897,118},	//	* ( ⋁ → v ) N-ARY LOGICAL OR → LATIN SMALL LETTER V	# →∨→
					{65366,118},	//	( ｖ → v ) FULLWIDTH LATIN SMALL LETTER V → LATIN SMALL LETTER V	# →ν→
					{8564,118},	//	( ⅴ → v ) SMALL ROMAN NUMERAL FIVE → LATIN SMALL LETTER V	#
					{119855,118},	//	( 𝐯 → v ) MATHEMATICAL BOLD SMALL V → LATIN SMALL LETTER V	#
					{119907,118},	//	( 𝑣 → v ) MATHEMATICAL ITALIC SMALL V → LATIN SMALL LETTER V	#
					{119959,118},	//	( 𝒗 → v ) MATHEMATICAL BOLD ITALIC SMALL V → LATIN SMALL LETTER V	#
					{120011,118},	//	( 𝓋 → v ) MATHEMATICAL SCRIPT SMALL V → LATIN SMALL LETTER V	#
					{120063,118},	//	( 𝓿 → v ) MATHEMATICAL BOLD SCRIPT SMALL V → LATIN SMALL LETTER V	#
					{120115,118},	//	( 𝔳 → v ) MATHEMATICAL FRAKTUR SMALL V → LATIN SMALL LETTER V	#
					{120167,118},	//	( 𝕧 → v ) MATHEMATICAL DOUBLE-STRUCK SMALL V → LATIN SMALL LETTER V	#
					{120219,118},	//	( 𝖛 → v ) MATHEMATICAL BOLD FRAKTUR SMALL V → LATIN SMALL LETTER V	#
					{120271,118},	//	( 𝗏 → v ) MATHEMATICAL SANS-SERIF SMALL V → LATIN SMALL LETTER V	#
					{120323,118},	//	( 𝘃 → v ) MATHEMATICAL SANS-SERIF BOLD SMALL V → LATIN SMALL LETTER V	#
					{120375,118},	//	( 𝘷 → v ) MATHEMATICAL SANS-SERIF ITALIC SMALL V → LATIN SMALL LETTER V	#
					{120427,118},	//	( 𝙫 → v ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL V → LATIN SMALL LETTER V	#
					{120479,118},	//	( 𝚟 → v ) MATHEMATICAL MONOSPACE SMALL V → LATIN SMALL LETTER V	#
					{7456,118},	//	( ᴠ → v ) LATIN LETTER SMALL CAPITAL V → LATIN SMALL LETTER V	#
					//{957,118},	//	( ν → v ) GREEK SMALL LETTER NU → LATIN SMALL LETTER V	#	//+ would break case-insensitive matching, unfortunately
					{120526,118},	//	( 𝛎 → v ) MATHEMATICAL BOLD SMALL NU → LATIN SMALL LETTER V	# →ν→
					{120584,118},	//	( 𝜈 → v ) MATHEMATICAL ITALIC SMALL NU → LATIN SMALL LETTER V	# →ν→
					{120642,118},	//	( 𝝂 → v ) MATHEMATICAL BOLD ITALIC SMALL NU → LATIN SMALL LETTER V	# →ν→
					{120700,118},	//	( 𝝼 → v ) MATHEMATICAL SANS-SERIF BOLD SMALL NU → LATIN SMALL LETTER V	# →ν→
					{120758,118},	//	( 𝞶 → v ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL NU → LATIN SMALL LETTER V	# →ν→
					{1141,118},	//	( ѵ → v ) CYRILLIC SMALL LETTER IZHITSA → LATIN SMALL LETTER V	#
					//+ these could do more harm than good
					//{1496,118},	//	( ‎ט‎ → v ) HEBREW LETTER TET → LATIN SMALL LETTER V	#
					//{71430,118},	//	( 𑜆 → v ) AHOM LETTER PA → LATIN SMALL LETTER V	#
					//{43945,118},	//	( ꮩ → v ) CHEROKEE SMALL LETTER DO → LATIN SMALL LETTER V	# →ᴠ→
					//{71872,118},	//	( 𑣀 → v ) WARANG CITI SMALL LETTER NGAA → LATIN SMALL LETTER V	#
					//+
					{119309,86},	//	* ( 𝈍 → V ) GREEK VOCAL NOTATION SYMBOL-14 → LATIN CAPITAL LETTER V	#
					//{1639,86},	//	( ‎٧‎ → V ) ARABIC-INDIC DIGIT SEVEN → LATIN CAPITAL LETTER V	#	//+ would likely do more harm than good
					//{1783,86},	//	( ۷ → V ) EXTENDED ARABIC-INDIC DIGIT SEVEN → LATIN CAPITAL LETTER V	# →‎٧‎→	//+ would likely do more harm than good
					{8548,86},	//	( Ⅴ → V ) ROMAN NUMERAL FIVE → LATIN CAPITAL LETTER V	#
					{119829,86},	//	( 𝐕 → V ) MATHEMATICAL BOLD CAPITAL V → LATIN CAPITAL LETTER V	#
					{119881,86},	//	( 𝑉 → V ) MATHEMATICAL ITALIC CAPITAL V → LATIN CAPITAL LETTER V	#
					{119933,86},	//	( 𝑽 → V ) MATHEMATICAL BOLD ITALIC CAPITAL V → LATIN CAPITAL LETTER V	#
					{119985,86},	//	( 𝒱 → V ) MATHEMATICAL SCRIPT CAPITAL V → LATIN CAPITAL LETTER V	#
					{120037,86},	//	( 𝓥 → V ) MATHEMATICAL BOLD SCRIPT CAPITAL V → LATIN CAPITAL LETTER V	#
					{120089,86},	//	( 𝔙 → V ) MATHEMATICAL FRAKTUR CAPITAL V → LATIN CAPITAL LETTER V	#
					{120141,86},	//	( 𝕍 → V ) MATHEMATICAL DOUBLE-STRUCK CAPITAL V → LATIN CAPITAL LETTER V	#
					{120193,86},	//	( 𝖁 → V ) MATHEMATICAL BOLD FRAKTUR CAPITAL V → LATIN CAPITAL LETTER V	#
					{120245,86},	//	( 𝖵 → V ) MATHEMATICAL SANS-SERIF CAPITAL V → LATIN CAPITAL LETTER V	#
					{120297,86},	//	( 𝗩 → V ) MATHEMATICAL SANS-SERIF BOLD CAPITAL V → LATIN CAPITAL LETTER V	#
					{120349,86},	//	( 𝘝 → V ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL V → LATIN CAPITAL LETTER V	#
					{120401,86},	//	( 𝙑 → V ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL V → LATIN CAPITAL LETTER V	#
					{120453,86},	//	( 𝚅 → V ) MATHEMATICAL MONOSPACE CAPITAL V → LATIN CAPITAL LETTER V	#
					{1140,86},	//	( Ѵ → V ) CYRILLIC CAPITAL LETTER IZHITSA → LATIN CAPITAL LETTER V	#
					//+ these could do more harm than good
					//{11576,86},	//	( ⴸ → V ) TIFINAGH LETTER YADH → LATIN CAPITAL LETTER V	#
					//{5081,86},	//	( Ꮩ → V ) CHEROKEE LETTER DO → LATIN CAPITAL LETTER V	#
					//{5167,86},	//	( ᐯ → V ) CANADIAN SYLLABICS PE → LATIN CAPITAL LETTER V	#
					//{42719,86},	//	( ꛟ → V ) BAMUM LETTER KO → LATIN CAPITAL LETTER V	#
					//{42214,86},	//	( ꓦ → V ) LISU LETTER HA → LATIN CAPITAL LETTER V	#
					//{93960,86},	//	( 𖼈 → V ) MIAO LETTER VA → LATIN CAPITAL LETTER V	#
					//{71840,86},	//	( 𑢠 → V ) WARANG CITI CAPITAL LETTER NGAA → LATIN CAPITAL LETTER V	#
					//{66845,86},	//	( 𐔝 → V ) ELBASAN LETTER TE → LATIN CAPITAL LETTER V	#
					//+
					//{623,119},	//	( ɯ → w ) LATIN SMALL LETTER TURNED M → LATIN SMALL LETTER W	#	//+ would break case-insensitive matching
					{119856,119},	//	( 𝐰 → w ) MATHEMATICAL BOLD SMALL W → LATIN SMALL LETTER W	#
					{119908,119},	//	( 𝑤 → w ) MATHEMATICAL ITALIC SMALL W → LATIN SMALL LETTER W	#
					{119960,119},	//	( 𝒘 → w ) MATHEMATICAL BOLD ITALIC SMALL W → LATIN SMALL LETTER W	#
					{120012,119},	//	( 𝓌 → w ) MATHEMATICAL SCRIPT SMALL W → LATIN SMALL LETTER W	#
					{120064,119},	//	( 𝔀 → w ) MATHEMATICAL BOLD SCRIPT SMALL W → LATIN SMALL LETTER W	#
					{120116,119},	//	( 𝔴 → w ) MATHEMATICAL FRAKTUR SMALL W → LATIN SMALL LETTER W	#
					{120168,119},	//	( 𝕨 → w ) MATHEMATICAL DOUBLE-STRUCK SMALL W → LATIN SMALL LETTER W	#
					{120220,119},	//	( 𝖜 → w ) MATHEMATICAL BOLD FRAKTUR SMALL W → LATIN SMALL LETTER W	#
					{120272,119},	//	( 𝗐 → w ) MATHEMATICAL SANS-SERIF SMALL W → LATIN SMALL LETTER W	#
					{120324,119},	//	( 𝘄 → w ) MATHEMATICAL SANS-SERIF BOLD SMALL W → LATIN SMALL LETTER W	#
					{120376,119},	//	( 𝘸 → w ) MATHEMATICAL SANS-SERIF ITALIC SMALL W → LATIN SMALL LETTER W	#
					{120428,119},	//	( 𝙬 → w ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL W → LATIN SMALL LETTER W	#
					{120480,119},	//	( 𝚠 → w ) MATHEMATICAL MONOSPACE SMALL W → LATIN SMALL LETTER W	#
					{7457,119},	//	( ᴡ → w ) LATIN LETTER SMALL CAPITAL W → LATIN SMALL LETTER W	#
					//{1121,119},	//	( ѡ → w ) CYRILLIC SMALL LETTER OMEGA → LATIN SMALL LETTER W	#	//+ would break case-insensitive matching
					{1309,119},	//	( ԝ → w ) CYRILLIC SMALL LETTER WE → LATIN SMALL LETTER W	#	//+ would break case-insensitive matching
					{1377,119},	//	( ա → w ) ARMENIAN SMALL LETTER AYB → LATIN SMALL LETTER W	# →ɯ→	//+ would break case-insensitive matching
					//+ these could do more harm than good
					//{71434,119},	//	( 𑜊 → w ) AHOM LETTER JA → LATIN SMALL LETTER W	#
					//{71438,119},	//	( 𑜎 → w ) AHOM LETTER LA → LATIN SMALL LETTER W	#
					//{71439,119},	//	( 𑜏 → w ) AHOM LETTER SA → LATIN SMALL LETTER W	#
					//{43907,119},	//	( ꮃ → w ) CHEROKEE SMALL LETTER LA → LATIN SMALL LETTER W	# →ᴡ→
					//{71919,87},	//	* ( 𑣯 → W ) WARANG CITI NUMBER SIXTY → LATIN CAPITAL LETTER W	#
					//{71910,87},	//	( 𑣦 → W ) WARANG CITI DIGIT SIX → LATIN CAPITAL LETTER W	#
					//+
					{119830,87},	//	( 𝐖 → W ) MATHEMATICAL BOLD CAPITAL W → LATIN CAPITAL LETTER W	#
					{119882,87},	//	( 𝑊 → W ) MATHEMATICAL ITALIC CAPITAL W → LATIN CAPITAL LETTER W	#
					{119934,87},	//	( 𝑾 → W ) MATHEMATICAL BOLD ITALIC CAPITAL W → LATIN CAPITAL LETTER W	#
					{119986,87},	//	( 𝒲 → W ) MATHEMATICAL SCRIPT CAPITAL W → LATIN CAPITAL LETTER W	#
					{120038,87},	//	( 𝓦 → W ) MATHEMATICAL BOLD SCRIPT CAPITAL W → LATIN CAPITAL LETTER W	#
					{120090,87},	//	( 𝔚 → W ) MATHEMATICAL FRAKTUR CAPITAL W → LATIN CAPITAL LETTER W	#
					{120142,87},	//	( 𝕎 → W ) MATHEMATICAL DOUBLE-STRUCK CAPITAL W → LATIN CAPITAL LETTER W	#
					{120194,87},	//	( 𝖂 → W ) MATHEMATICAL BOLD FRAKTUR CAPITAL W → LATIN CAPITAL LETTER W	#
					{120246,87},	//	( 𝖶 → W ) MATHEMATICAL SANS-SERIF CAPITAL W → LATIN CAPITAL LETTER W	#
					{120298,87},	//	( 𝗪 → W ) MATHEMATICAL SANS-SERIF BOLD CAPITAL W → LATIN CAPITAL LETTER W	#
					{120350,87},	//	( 𝘞 → W ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL W → LATIN CAPITAL LETTER W	#
					{120402,87},	//	( 𝙒 → W ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL W → LATIN CAPITAL LETTER W	#
					{120454,87},	//	( 𝚆 → W ) MATHEMATICAL MONOSPACE CAPITAL W → LATIN CAPITAL LETTER W	#
					{1308,87},	//	( Ԝ → W ) CYRILLIC CAPITAL LETTER WE → LATIN CAPITAL LETTER W	#
					//+ these could do more harm than good
					//{5043,87},	//	( Ꮃ → W ) CHEROKEE LETTER LA → LATIN CAPITAL LETTER W	#
					//{5076,87},	//	( Ꮤ → W ) CHEROKEE LETTER TA → LATIN CAPITAL LETTER W	#
					//{42218,87},	//	( ꓪ → W ) LISU LETTER WA → LATIN CAPITAL LETTER W	#
					//{5742,120},	//	* ( ᙮ → x ) CANADIAN SYLLABICS FULL STOP → LATIN SMALL LETTER X	#
					//+
					{10539,120},	//	* ( ⤫ → x ) RISING DIAGONAL CROSSING FALLING DIAGONAL → LATIN SMALL LETTER X	#
					{10540,120},	//	* ( ⤬ → x ) FALLING DIAGONAL CROSSING RISING DIAGONAL → LATIN SMALL LETTER X	#
					{10799,120},	//	* ( ⨯ → x ) VECTOR OR CROSS PRODUCT → LATIN SMALL LETTER X	# →×→
					{65368,120},	//	( ｘ → x ) FULLWIDTH LATIN SMALL LETTER X → LATIN SMALL LETTER X	# →х→
					{8569,120},	//	( ⅹ → x ) SMALL ROMAN NUMERAL TEN → LATIN SMALL LETTER X	#
					{119857,120},	//	( 𝐱 → x ) MATHEMATICAL BOLD SMALL X → LATIN SMALL LETTER X	#
					{119909,120},	//	( 𝑥 → x ) MATHEMATICAL ITALIC SMALL X → LATIN SMALL LETTER X	#
					{119961,120},	//	( 𝒙 → x ) MATHEMATICAL BOLD ITALIC SMALL X → LATIN SMALL LETTER X	#
					{120013,120},	//	( 𝓍 → x ) MATHEMATICAL SCRIPT SMALL X → LATIN SMALL LETTER X	#
					{120065,120},	//	( 𝔁 → x ) MATHEMATICAL BOLD SCRIPT SMALL X → LATIN SMALL LETTER X	#
					{120117,120},	//	( 𝔵 → x ) MATHEMATICAL FRAKTUR SMALL X → LATIN SMALL LETTER X	#
					{120169,120},	//	( 𝕩 → x ) MATHEMATICAL DOUBLE-STRUCK SMALL X → LATIN SMALL LETTER X	#
					{120221,120},	//	( 𝖝 → x ) MATHEMATICAL BOLD FRAKTUR SMALL X → LATIN SMALL LETTER X	#
					{120273,120},	//	( 𝗑 → x ) MATHEMATICAL SANS-SERIF SMALL X → LATIN SMALL LETTER X	#
					{120325,120},	//	( 𝘅 → x ) MATHEMATICAL SANS-SERIF BOLD SMALL X → LATIN SMALL LETTER X	#
					{120377,120},	//	( 𝘹 → x ) MATHEMATICAL SANS-SERIF ITALIC SMALL X → LATIN SMALL LETTER X	#
					{120429,120},	//	( 𝙭 → x ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL X → LATIN SMALL LETTER X	#
					{120481,120},	//	( 𝚡 → x ) MATHEMATICAL MONOSPACE SMALL X → LATIN SMALL LETTER X	#
					{1093,120},	//	( х → x ) CYRILLIC SMALL LETTER HA → LATIN SMALL LETTER X	#
					//+ these could do more harm than good
					//{5441,120},	//	( ᕁ → x ) CANADIAN SYLLABICS SAYISI YI → LATIN SMALL LETTER X	# →᙮→
					//{5501,120},	//	( ᕽ → x ) CANADIAN SYLLABICS HK → LATIN SMALL LETTER X	# →ᕁ→→᙮→
					//{5741,88},	//	* ( ᙭ → X ) CANADIAN SYLLABICS CHI SIGN → LATIN CAPITAL LETTER X	#
					//+
					{9587,88},	//	* ( ╳ → X ) BOX DRAWINGS LIGHT DIAGONAL CROSS → LATIN CAPITAL LETTER X	#
					{66338,88},	//	* ( 𐌢 → X ) OLD ITALIC NUMERAL TEN → LATIN CAPITAL LETTER X	# →𐌗→
					//{71916,88},	//	* ( 𑣬 → X ) WARANG CITI NUMBER THIRTY → LATIN CAPITAL LETTER X	#	//+ could do more harm than good
					{65336,88},	//	( Ｘ → X ) FULLWIDTH LATIN CAPITAL LETTER X → LATIN CAPITAL LETTER X	# →Х→
					{8553,88},	//	( Ⅹ → X ) ROMAN NUMERAL TEN → LATIN CAPITAL LETTER X	#
					{119831,88},	//	( 𝐗 → X ) MATHEMATICAL BOLD CAPITAL X → LATIN CAPITAL LETTER X	#
					{119883,88},	//	( 𝑋 → X ) MATHEMATICAL ITALIC CAPITAL X → LATIN CAPITAL LETTER X	#
					{119935,88},	//	( 𝑿 → X ) MATHEMATICAL BOLD ITALIC CAPITAL X → LATIN CAPITAL LETTER X	#
					{119987,88},	//	( 𝒳 → X ) MATHEMATICAL SCRIPT CAPITAL X → LATIN CAPITAL LETTER X	#
					{120039,88},	//	( 𝓧 → X ) MATHEMATICAL BOLD SCRIPT CAPITAL X → LATIN CAPITAL LETTER X	#
					{120091,88},	//	( 𝔛 → X ) MATHEMATICAL FRAKTUR CAPITAL X → LATIN CAPITAL LETTER X	#
					{120143,88},	//	( 𝕏 → X ) MATHEMATICAL DOUBLE-STRUCK CAPITAL X → LATIN CAPITAL LETTER X	#
					{120195,88},	//	( 𝖃 → X ) MATHEMATICAL BOLD FRAKTUR CAPITAL X → LATIN CAPITAL LETTER X	#
					{120247,88},	//	( 𝖷 → X ) MATHEMATICAL SANS-SERIF CAPITAL X → LATIN CAPITAL LETTER X	#
					{120299,88},	//	( 𝗫 → X ) MATHEMATICAL SANS-SERIF BOLD CAPITAL X → LATIN CAPITAL LETTER X	#
					{120351,88},	//	( 𝘟 → X ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL X → LATIN CAPITAL LETTER X	#
					{120403,88},	//	( 𝙓 → X ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL X → LATIN CAPITAL LETTER X	#
					{120455,88},	//	( 𝚇 → X ) MATHEMATICAL MONOSPACE CAPITAL X → LATIN CAPITAL LETTER X	#
					//{42931,88},	//	( Ꭓ → X ) LATIN CAPITAL LETTER CHI → LATIN CAPITAL LETTER X	#	//+ would break case-insensitive matching
					//{935,88},	//	( Χ → X ) GREEK CAPITAL LETTER CHI → LATIN CAPITAL LETTER X	#	//+ would break case-insensitive matching, unfortunately
					{120510,88},	//	( 𝚾 → X ) MATHEMATICAL BOLD CAPITAL CHI → LATIN CAPITAL LETTER X	# →Χ→
					{120568,88},	//	( 𝛸 → X ) MATHEMATICAL ITALIC CAPITAL CHI → LATIN CAPITAL LETTER X	# →Χ→
					{120626,88},	//	( 𝜲 → X ) MATHEMATICAL BOLD ITALIC CAPITAL CHI → LATIN CAPITAL LETTER X	# →𝑿→
					{120684,88},	//	( 𝝬 → X ) MATHEMATICAL SANS-SERIF BOLD CAPITAL CHI → LATIN CAPITAL LETTER X	# →Χ→
					{120742,88},	//	( 𝞦 → X ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL CHI → LATIN CAPITAL LETTER X	# →Χ→
					//{11436,88},	//	( Ⲭ → X ) COPTIC CAPITAL LETTER KHI → LATIN CAPITAL LETTER X	# →Х→	//+ would break case-insensitive matching
					{1061,88},	//	( Х → X ) CYRILLIC CAPITAL LETTER HA → LATIN CAPITAL LETTER X	#
					//{11613,88},	//	( ⵝ → X ) TIFINAGH LETTER YATH → LATIN CAPITAL LETTER X	#	//+ could do more harm than good
					{5815,88},	//	( ᚷ → X ) RUNIC LETTER GEBO GYFU G → LATIN CAPITAL LETTER X	#
					//+ these could do more harm than good
					//{42219,88},	//	( ꓫ → X ) LISU LETTER SHA → LATIN CAPITAL LETTER X	#
					//{66192,88},	//	( 𐊐 → X ) LYCIAN LETTER MM → LATIN CAPITAL LETTER X	#
					//{66228,88},	//	( 𐊴 → X ) CARIAN LETTER X → LATIN CAPITAL LETTER X	#
					//{66327,88},	//	( 𐌗 → X ) OLD ITALIC LETTER EKS → LATIN CAPITAL LETTER X	#
					//{66855,88},	//	( 𐔧 → X ) ELBASAN LETTER KHE → LATIN CAPITAL LETTER X	#
					//+
					//{611,121},	//	( ɣ → y ) LATIN SMALL LETTER GAMMA → LATIN SMALL LETTER Y	# →γ→	//+ would break case-insensitive matching
					{7564,121},	//	( ᶌ → y ) LATIN SMALL LETTER V WITH PALATAL HOOK → LATIN SMALL LETTER Y	#
					{65369,121},	//	( ｙ → y ) FULLWIDTH LATIN SMALL LETTER Y → LATIN SMALL LETTER Y	# →у→
					{119858,121},	//	( 𝐲 → y ) MATHEMATICAL BOLD SMALL Y → LATIN SMALL LETTER Y	#
					{119910,121},	//	( 𝑦 → y ) MATHEMATICAL ITALIC SMALL Y → LATIN SMALL LETTER Y	#
					{119962,121},	//	( 𝒚 → y ) MATHEMATICAL BOLD ITALIC SMALL Y → LATIN SMALL LETTER Y	#
					{120014,121},	//	( 𝓎 → y ) MATHEMATICAL SCRIPT SMALL Y → LATIN SMALL LETTER Y	#
					{120066,121},	//	( 𝔂 → y ) MATHEMATICAL BOLD SCRIPT SMALL Y → LATIN SMALL LETTER Y	#
					{120118,121},	//	( 𝔶 → y ) MATHEMATICAL FRAKTUR SMALL Y → LATIN SMALL LETTER Y	#
					{120170,121},	//	( 𝕪 → y ) MATHEMATICAL DOUBLE-STRUCK SMALL Y → LATIN SMALL LETTER Y	#
					{120222,121},	//	( 𝖞 → y ) MATHEMATICAL BOLD FRAKTUR SMALL Y → LATIN SMALL LETTER Y	#
					{120274,121},	//	( 𝗒 → y ) MATHEMATICAL SANS-SERIF SMALL Y → LATIN SMALL LETTER Y	#
					{120326,121},	//	( 𝘆 → y ) MATHEMATICAL SANS-SERIF BOLD SMALL Y → LATIN SMALL LETTER Y	#
					{120378,121},	//	( 𝘺 → y ) MATHEMATICAL SANS-SERIF ITALIC SMALL Y → LATIN SMALL LETTER Y	#
					{120430,121},	//	( 𝙮 → y ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL Y → LATIN SMALL LETTER Y	#
					{120482,121},	//	( 𝚢 → y ) MATHEMATICAL MONOSPACE SMALL Y → LATIN SMALL LETTER Y	#
					{655,121},	//	( ʏ → y ) LATIN LETTER SMALL CAPITAL Y → LATIN SMALL LETTER Y	# →ү→→γ→
					{7935,121},	//	( ỿ → y ) LATIN SMALL LETTER Y WITH LOOP → LATIN SMALL LETTER Y	#
					{43866,121},	//	( ꭚ → y ) LATIN SMALL LETTER Y WITH SHORT RIGHT LEG → LATIN SMALL LETTER Y	#
					//{947,121},	//	( γ → y ) GREEK SMALL LETTER GAMMA → LATIN SMALL LETTER Y	#	//+ would break case-insensitive matching and is hardly a good idea anyway
					//+ the following would be better mapped to GREEK SMALL LETTER GAMMA, but I’m leaving them as they are for now
					{8509,121},	//	( ℽ → y ) DOUBLE-STRUCK SMALL GAMMA → LATIN SMALL LETTER Y	# →γ→	
					{120516,121},	//	( 𝛄 → y ) MATHEMATICAL BOLD SMALL GAMMA → LATIN SMALL LETTER Y	# →γ→
					{120574,121},	//	( 𝛾 → y ) MATHEMATICAL ITALIC SMALL GAMMA → LATIN SMALL LETTER Y	# →γ→
					{120632,121},	//	( 𝜸 → y ) MATHEMATICAL BOLD ITALIC SMALL GAMMA → LATIN SMALL LETTER Y	# →γ→
					{120690,121},	//	( 𝝲 → y ) MATHEMATICAL SANS-SERIF BOLD SMALL GAMMA → LATIN SMALL LETTER Y	# →γ→
					{120748,121},	//	( 𝞬 → y ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL GAMMA → LATIN SMALL LETTER Y	# →γ→
					//+
					{1091,121},	//	( у → y ) CYRILLIC SMALL LETTER U → LATIN SMALL LETTER Y	#
					{1199,121},	//	( ү → y ) CYRILLIC SMALL LETTER STRAIGHT U → LATIN SMALL LETTER Y	# →γ→
					//{4327,121},	//	( ყ → y ) GEORGIAN LETTER QAR → LATIN SMALL LETTER Y	#	//+ could do more harm than good
					//{71900,121},	//	( 𑣜 → y ) WARANG CITI SMALL LETTER HAR → LATIN SMALL LETTER Y	# →ɣ→→γ→	//+ could do more harm than good
					{65337,89},	//	( Ｙ → Y ) FULLWIDTH LATIN CAPITAL LETTER Y → LATIN CAPITAL LETTER Y	# →Υ→
					{119832,89},	//	( 𝐘 → Y ) MATHEMATICAL BOLD CAPITAL Y → LATIN CAPITAL LETTER Y	#
					{119884,89},	//	( 𝑌 → Y ) MATHEMATICAL ITALIC CAPITAL Y → LATIN CAPITAL LETTER Y	#
					{119936,89},	//	( 𝒀 → Y ) MATHEMATICAL BOLD ITALIC CAPITAL Y → LATIN CAPITAL LETTER Y	#
					{119988,89},	//	( 𝒴 → Y ) MATHEMATICAL SCRIPT CAPITAL Y → LATIN CAPITAL LETTER Y	#
					{120040,89},	//	( 𝓨 → Y ) MATHEMATICAL BOLD SCRIPT CAPITAL Y → LATIN CAPITAL LETTER Y	#
					{120092,89},	//	( 𝔜 → Y ) MATHEMATICAL FRAKTUR CAPITAL Y → LATIN CAPITAL LETTER Y	#
					{120144,89},	//	( 𝕐 → Y ) MATHEMATICAL DOUBLE-STRUCK CAPITAL Y → LATIN CAPITAL LETTER Y	#
					{120196,89},	//	( 𝖄 → Y ) MATHEMATICAL BOLD FRAKTUR CAPITAL Y → LATIN CAPITAL LETTER Y	#
					{120248,89},	//	( 𝖸 → Y ) MATHEMATICAL SANS-SERIF CAPITAL Y → LATIN CAPITAL LETTER Y	#
					{120300,89},	//	( 𝗬 → Y ) MATHEMATICAL SANS-SERIF BOLD CAPITAL Y → LATIN CAPITAL LETTER Y	#
					{120352,89},	//	( 𝘠 → Y ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL Y → LATIN CAPITAL LETTER Y	#
					{120404,89},	//	( 𝙔 → Y ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL Y → LATIN CAPITAL LETTER Y	#
					{120456,89},	//	( 𝚈 → Y ) MATHEMATICAL MONOSPACE CAPITAL Y → LATIN CAPITAL LETTER Y	#
					//{933,89},	//	( Υ → Y ) GREEK CAPITAL LETTER UPSILON → LATIN CAPITAL LETTER Y	#	//+ would break case-insensitive matching, unfortunately
					{978,933},	//	( ϒ → Υ ) GREEK UPSILON WITH HOOK SYMBOL → GREEK CAPITAL LETTER UPSILON	# //+ changed from LATIN CAPITAL LETTER Y
					//+ the following would be better mapped to GREEK CAPITAL LETTER UPSILON, but I’m leaving them as they are for now
					{120508,89},	//	( 𝚼 → Y ) MATHEMATICAL BOLD CAPITAL UPSILON → LATIN CAPITAL LETTER Y	# →Υ→
					{120566,89},	//	( 𝛶 → Y ) MATHEMATICAL ITALIC CAPITAL UPSILON → LATIN CAPITAL LETTER Y	# →Υ→
					{120624,89},	//	( 𝜰 → Y ) MATHEMATICAL BOLD ITALIC CAPITAL UPSILON → LATIN CAPITAL LETTER Y	# →Υ→
					{120682,89},	//	( 𝝪 → Y ) MATHEMATICAL SANS-SERIF BOLD CAPITAL UPSILON → LATIN CAPITAL LETTER Y	# →Υ→
					{120740,89},	//	( 𝞤 → Y ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL UPSILON → LATIN CAPITAL LETTER Y	# →Υ→
					//{11432,89},	//	( Ⲩ → Y ) COPTIC CAPITAL LETTER UA → LATIN CAPITAL LETTER Y	#	//+ would break case-insensitive matching
					{1059,89},	//	( У → Y ) CYRILLIC CAPITAL LETTER U → LATIN CAPITAL LETTER Y	#
					{1198,89},	//	( Ү → Y ) CYRILLIC CAPITAL LETTER STRAIGHT U → LATIN CAPITAL LETTER Y	#
					//+ these could do more harm than good
					//{5033,89},	//	( Ꭹ → Y ) CHEROKEE LETTER GI → LATIN CAPITAL LETTER Y	#
					//{5053,89},	//	( Ꮍ → Y ) CHEROKEE LETTER MU → LATIN CAPITAL LETTER Y	# →Ꭹ→
					//{42220,89},	//	( ꓬ → Y ) LISU LETTER YA → LATIN CAPITAL LETTER Y	#
					//{94019,89},	//	( 𖽃 → Y ) MIAO LETTER AH → LATIN CAPITAL LETTER Y	#
					//{71844,89},	//	( 𑢤 → Y ) WARANG CITI CAPITAL LETTER YA → LATIN CAPITAL LETTER Y	#
					//{66226,89},	//	( 𐊲 → Y ) CARIAN LETTER U → LATIN CAPITAL LETTER Y	#
					//+
					{119859,122},	//	( 𝐳 → z ) MATHEMATICAL BOLD SMALL Z → LATIN SMALL LETTER Z	#
					{119911,122},	//	( 𝑧 → z ) MATHEMATICAL ITALIC SMALL Z → LATIN SMALL LETTER Z	#
					{119963,122},	//	( 𝒛 → z ) MATHEMATICAL BOLD ITALIC SMALL Z → LATIN SMALL LETTER Z	#
					{120015,122},	//	( 𝓏 → z ) MATHEMATICAL SCRIPT SMALL Z → LATIN SMALL LETTER Z	#
					{120067,122},	//	( 𝔃 → z ) MATHEMATICAL BOLD SCRIPT SMALL Z → LATIN SMALL LETTER Z	#
					{120119,122},	//	( 𝔷 → z ) MATHEMATICAL FRAKTUR SMALL Z → LATIN SMALL LETTER Z	#
					{120171,122},	//	( 𝕫 → z ) MATHEMATICAL DOUBLE-STRUCK SMALL Z → LATIN SMALL LETTER Z	#
					{120223,122},	//	( 𝖟 → z ) MATHEMATICAL BOLD FRAKTUR SMALL Z → LATIN SMALL LETTER Z	#
					{120275,122},	//	( 𝗓 → z ) MATHEMATICAL SANS-SERIF SMALL Z → LATIN SMALL LETTER Z	#
					{120327,122},	//	( 𝘇 → z ) MATHEMATICAL SANS-SERIF BOLD SMALL Z → LATIN SMALL LETTER Z	#
					{120379,122},	//	( 𝘻 → z ) MATHEMATICAL SANS-SERIF ITALIC SMALL Z → LATIN SMALL LETTER Z	#
					{120431,122},	//	( 𝙯 → z ) MATHEMATICAL SANS-SERIF BOLD ITALIC SMALL Z → LATIN SMALL LETTER Z	#
					{120483,122},	//	( 𝚣 → z ) MATHEMATICAL MONOSPACE SMALL Z → LATIN SMALL LETTER Z	#
					{7458,122},	//	( ᴢ → z ) LATIN LETTER SMALL CAPITAL Z → LATIN SMALL LETTER Z	#
					//+ these could do more harm than good
					//{43923,122},	//	( ꮓ → z ) CHEROKEE SMALL LETTER NO → LATIN SMALL LETTER Z	# →ᴢ→
					//{71876,122},	//	( 𑣄 → z ) WARANG CITI SMALL LETTER YA → LATIN SMALL LETTER Z	#
					//{66293,90},	//	* ( 𐋵 → Z ) COPTIC EPACT NUMBER THREE HUNDRED → LATIN CAPITAL LETTER Z	#
					//{71909,90},	//	( 𑣥 → Z ) WARANG CITI DIGIT FIVE → LATIN CAPITAL LETTER Z	#
					//+
					{65338,90},	//	( Ｚ → Z ) FULLWIDTH LATIN CAPITAL LETTER Z → LATIN CAPITAL LETTER Z	# →Ζ→
					{8484,90},	//	( ℤ → Z ) DOUBLE-STRUCK CAPITAL Z → LATIN CAPITAL LETTER Z	#
					{8488,90},	//	( ℨ → Z ) BLACK-LETTER CAPITAL Z → LATIN CAPITAL LETTER Z	#
					{119833,90},	//	( 𝐙 → Z ) MATHEMATICAL BOLD CAPITAL Z → LATIN CAPITAL LETTER Z	#
					{119885,90},	//	( 𝑍 → Z ) MATHEMATICAL ITALIC CAPITAL Z → LATIN CAPITAL LETTER Z	#
					{119937,90},	//	( 𝒁 → Z ) MATHEMATICAL BOLD ITALIC CAPITAL Z → LATIN CAPITAL LETTER Z	#
					{119989,90},	//	( 𝒵 → Z ) MATHEMATICAL SCRIPT CAPITAL Z → LATIN CAPITAL LETTER Z	#
					{120041,90},	//	( 𝓩 → Z ) MATHEMATICAL BOLD SCRIPT CAPITAL Z → LATIN CAPITAL LETTER Z	#
					{120197,90},	//	( 𝖅 → Z ) MATHEMATICAL BOLD FRAKTUR CAPITAL Z → LATIN CAPITAL LETTER Z	#
					{120249,90},	//	( 𝖹 → Z ) MATHEMATICAL SANS-SERIF CAPITAL Z → LATIN CAPITAL LETTER Z	#
					{120301,90},	//	( 𝗭 → Z ) MATHEMATICAL SANS-SERIF BOLD CAPITAL Z → LATIN CAPITAL LETTER Z	#
					{120353,90},	//	( 𝘡 → Z ) MATHEMATICAL SANS-SERIF ITALIC CAPITAL Z → LATIN CAPITAL LETTER Z	#
					{120405,90},	//	( 𝙕 → Z ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL Z → LATIN CAPITAL LETTER Z	#
					{120457,90},	//	( 𝚉 → Z ) MATHEMATICAL MONOSPACE CAPITAL Z → LATIN CAPITAL LETTER Z	#
					//{918,90},	//	( Ζ → Z ) GREEK CAPITAL LETTER ZETA → LATIN CAPITAL LETTER Z	#	//+ would break case-insensitive matching, unfortunately
					{120493,90},	//	( 𝚭 → Z ) MATHEMATICAL BOLD CAPITAL ZETA → LATIN CAPITAL LETTER Z	# →Ζ→
					{120551,90},	//	( 𝛧 → Z ) MATHEMATICAL ITALIC CAPITAL ZETA → LATIN CAPITAL LETTER Z	# →𝑍→
					{120609,90},	//	( 𝜡 → Z ) MATHEMATICAL BOLD ITALIC CAPITAL ZETA → LATIN CAPITAL LETTER Z	# →Ζ→
					{120667,90},	//	( 𝝛 → Z ) MATHEMATICAL SANS-SERIF BOLD CAPITAL ZETA → LATIN CAPITAL LETTER Z	# →Ζ→
					{120725,90},	//	( 𝞕 → Z ) MATHEMATICAL SANS-SERIF BOLD ITALIC CAPITAL ZETA → LATIN CAPITAL LETTER Z	# →Ζ→
					//+ these could do more harm than good
					//{5059,90},	//	( Ꮓ → Z ) CHEROKEE LETTER NO → LATIN CAPITAL LETTER Z	#
					//{42204,90},	//	( ꓜ → Z ) LISU LETTER DZA → LATIN CAPITAL LETTER Z	#
					//{71849,90},	//	( 𑢩 → Z ) WARANG CITI CAPITAL LETTER O → LATIN CAPITAL LETTER Z	#
					//{447,254},	//	( ƿ → þ ) LATIN LETTER WYNN → LATIN SMALL LETTER THORN	#
					//{1016,254},	//	( ϸ → þ ) GREEK SMALL LETTER SHO → LATIN SMALL LETTER THORN	#
					//{1015,222},	//	( Ϸ → Þ ) GREEK CAPITAL LETTER SHO → LATIN CAPITAL LETTER THORN	#
					//{66756,222},	//	( 𐓄 → Þ ) OSAGE CAPITAL LETTER PA → LATIN CAPITAL LETTER THORN	#
					//+
					//+ Additions outside of confusables.txt
					//+
					//+ Romanian users will probably appreciate these four
					{0x218,0x15E},	//	( Ș → Ş ) LATIN CAPITAL LETTER S WITH COMMA BELOW → LATIN CAPITAL LETTER S WITH CEDILLA
					{0x219,0x15F},	//	( ș → ş ) LATIN SMALL LETTER S WITH COMMA BELOW → LATIN SMALL LETTER S WITH CEDILLA
					{0x21A,0x162},	//	( Ț → Ţ ) LATIN CAPITAL LETTER T WITH COMMA BELOW → LATIN CAPITAL LETTER T WITH CEDILLA
					{0x21B,0x163},	//	( ț → ţ ) LATIN SMALL LETTER t WITH COMMA BELOW → LATIN SMALL LETTER T WITH CEDILLA
					//+ Possible replacements for question mark in file names (in addition to those listed above)
					{0xFF1F,0x3F},	//	( ？ → ? ) FULLWIDTH QUESTION MARK → QUESTION MARK	#
					{0xFE56,0x3F},	//	( ﹖ → ? ) SMALL QUESTION MARK → QUESTION MARK	#
					//+ Arabic-Indic variant digits
					{0x6F0,0x660},	//	( ۰ → ٠ ) EXTENDED ARABIC-INDIC DIGIT ZERO → ARABIC-INDIC DIGIT ZERO
					{0x6F1,0x661},	//	( ۱ → ١ ) EXTENDED ARABIC-INDIC DIGIT ONE → ARABIC-INDIC DIGIT ONE
					{0x6F2,0x662},	//	( ۲ → ٢ ) EXTENDED ARABIC-INDIC DIGIT TWO → ARABIC-INDIC DIGIT TWO
					{0x6F3,0x663},	//	( ۳ → ٣ ) EXTENDED ARABIC-INDIC DIGIT THREE → ARABIC-INDIC DIGIT THREE
					{0x6F4,0x664},	//	( ۴ → ٤ ) EXTENDED ARABIC-INDIC DIGIT FOUR → ARABIC-INDIC DIGIT FOUR
					{0x6F5,0x665},	//	( ۵ → ٥ ) EXTENDED ARABIC-INDIC DIGIT FIVE → ARABIC-INDIC DIGIT FIVE
					{0x6F6,0x666},	//	( ۶ → ٦ ) EXTENDED ARABIC-INDIC DIGIT SIX → ARABIC-INDIC DIGIT SIX
					{0x6F7,0x667},	//	( ۷ → ٧ ) EXTENDED ARABIC-INDIC DIGIT SEVEN → ARABIC-INDIC DIGIT SEVEN
					{0x6F8,0x668},	//	( ۸ → ٨ ) EXTENDED ARABIC-INDIC DIGIT EIGHT → ARABIC-INDIC DIGIT EIGHT
					{0x6F9,0x669},	//	( ۹ → ٩ ) EXTENDED ARABIC-INDIC DIGIT NINE → ARABIC-INDIC DIGIT NINE
				};
				
				confusable_map = new HashMap<>( map.length );
				
				for ( int[] entry: map ){
					
					confusable_map.put( entry[0], entry[1] );
				}
				
				confusable_recent = 
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
							
							if ( cp2 < 256 && cp1 >= 256){
								
								int cpos = line.indexOf( '#' );
								
								out.println( "{" + cp1 + "," + cp2 + "}," + (cpos>0?("\t//\t" + line.substring( cpos+1 ).trim()):""));
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
