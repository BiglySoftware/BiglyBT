/*
 * Created on Nov 1, 2011
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

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Pattern;

public class
RegExUtil
{
		// Common Patterns
	
	public static final Pattern PAT_SPLIT_COMMAWORDS = Pattern.compile("\\s*,\\s*");
	public static final Pattern PAT_SPLIT_COMMA = Pattern.compile(",");
	public static final Pattern PAT_SPLIT_DOT = Pattern.compile("\\.");
	public static final Pattern PAT_SPLIT_SPACE = Pattern.compile(" ");
	public static final Pattern PAT_SPLIT_SLASH_N = Pattern.compile("\n");
	
	public static final String PAT_WHITE_SPACE = "(?:\\s|\\p{Z})+";	// includes non-breaking space char
			
	private static final ThreadLocal<Map<String,Object[]>>		tls	=
		new ThreadLocal<Map<String,Object[]>>()
		{
			@Override
			public Map<String,Object[]>
			initialValue()
			{
				return(new HashMap<>());
			}
		};

	public static Pattern
	getCachedPattern(
		String		namespace,
		String		pattern )
	{
		return( getCachedPattern( namespace, pattern, 0 ));
	}

	public static Pattern
	getCachedPattern(
		String		namespace,
		String		pattern,
		int			flags )
	{
		Map<String,Object[]> map = tls.get();

		Object[] entry = map.get( namespace );

		if ( entry == null || !pattern.equals((String)entry[0])){

			Pattern result = Pattern.compile( pattern, flags );

			map.put( namespace, new Object[]{ pattern, result });

			return( result );

		}else{

			return((Pattern)entry[1]);
		}
	}

	public static String
	convertAndOrToExpr(
		String		str )
	{
		str = str.replaceAll("\\s*[|;]\\s*", "|" );
		
		if ( str.contains( " " )){
			
			String[] bits = str.split( " " );
			
			String s = "";
			
				// implement 'and' for spaces - (?=.*xxx)(?=.*yyy)
			
			for ( String bit: bits ){
				
				bit = bit.trim();
				
				if ( !bit.isEmpty()){
				
					bit = splitAndQuote(bit, "[|]");
					
					s += "(?=.*" + bit + ")";
				}
			}
			
			return( s );
			
		}else{
		
			return( splitAndQuote(str, "[|]") );
		}
	}
	
	public static String
	splitAndQuote(String s, String splitterRegex) {
		String[] bits = s.split(splitterRegex);
		for (int i = 0; i < bits.length; i++) {
			bits[i] = Pattern.quote(bits[i]);
		}
		return String.join("|", bits);
	}
	
	public static boolean
	mightBeEvil(
		String	str )
	{
			// http://en.wikipedia.org/wiki/ReDoS

		if ( !str.contains( ")" )){

			return( false );
		}

		char[]	chars = str.toCharArray();

		Stack<Integer>	stack = new Stack<>();

		for (int i=0;i<chars.length;i++){

			char c = chars[i];

			if ( c == '(' ){

				stack.push( i+1 );

			}else if ( c == ')' ){

				if ( stack.isEmpty()){

					Debug.out( "bracket un-matched in " + str + " - treating as evil" );

					return( true );

				}else{

					int	start = stack.pop();

					if ( i < chars.length-1){

						char next = chars[i+1];

						if ( next == '*' || next == '+' || next == '{' ){

							for ( int j=start;j<i;j++){

								c = chars[j];

								if ( "+*{|".indexOf( c ) != -1 ){

									Debug.out( "regular expression " + str + " might be evil due to '" + str.substring( start-1, i+2) + "'" );

									return( true );
								}
							}
						}
					}
				}
			}
		}

		return( false );
	}
}
