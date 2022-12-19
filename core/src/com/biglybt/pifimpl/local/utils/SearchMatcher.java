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

package com.biglybt.pifimpl.local.utils;

import java.util.regex.Pattern;

import com.biglybt.core.util.RegExUtil;

public class
SearchMatcher
{
	private String[]	bits;
	private int[]		bit_types;
	private Pattern[]	bit_patterns;

	public
	SearchMatcher(
		String		term )
	{
		bits = RegExUtil.PAT_SPLIT_SPACE.split(term.toLowerCase() );

		bit_types 		= new int[bits.length];
		bit_patterns 	= new Pattern[bits.length];

		for (int i=0;i<bits.length;i++){

			String bit = bits[i] = bits[i].trim();

			if ( bit.length() > 0 ){

				char	c = bit.charAt(0);

				if ( c == '+' ){

					bit_types[i] = 1;

					bit = bits[i] = bit.substring(1);

				}else if ( c == '-' ){

					bit_types[i] = 2;

					bit = bits[i] = bit.substring(1);
				}

				if ( bit.startsWith( "(" ) && bit.endsWith((")"))){

					bit = bit.substring( 1, bit.length()-1 );

					try{
						bit_patterns[i] = Pattern.compile( bit, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE );

					}catch( Throwable e ){
					}
				}else if ( bit.contains( "|" )){

					try{
						bit_patterns[i] = Pattern.compile( bit, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE );

					}catch( Throwable e ){
					}
				}
			}
		}
	}

	public boolean
	matches(
		String		str )
	{
		// term is made up of space separated bits - all bits must match
		// each bit can be prefixed by + or -, a leading - means 'bit doesn't match'. + doesn't mean anything
		// each bit (with prefix removed) can be "(" regexp ")"
		// if bit isn't regexp but has "|" in it it is turned into a regexp so a|b means 'a or b'

		str = str.toLowerCase();

		boolean	match 			= true;
		boolean	at_least_one 	= false;

		for (int i=0;i<bits.length;i++){

			String bit = bits[i];

			if ( bit.length() > 0 ){

				boolean	hit;

				if ( bit_patterns[i] == null ){

					hit = str.contains( bit );

				}else{

					hit = bit_patterns[i].matcher( str ).find();
				}

				int	type = bit_types[i];

				if ( hit ){

					if ( type == 2 ){

						match = false;

						break;

					}else{

						at_least_one = true;

					}
				}else{

					if ( type == 2 ){

						at_least_one = true;

					}else{

						match = false;

						break;
					}
				}
			}
		}

		boolean res = match && at_least_one;

		return( res );
	}
}