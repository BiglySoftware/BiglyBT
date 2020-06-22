/*
 * Created on 02-Jan-2005
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

package com.biglybt.pifimpl.local.utils.xml.rss;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.biglybt.core.util.Debug;
import com.biglybt.core.util.FileUtil;
import com.biglybt.pifimpl.local.utils.UtilitiesImpl;

/**
 * @author parg
 *
 */

public class
RSSUtils
{
	public static Date
	parseRSSDate(
		String	date_str )
	{
		date_str = date_str.trim();

		if ( date_str.length() == 0 ){

			return( null );
		}

		try{
			// see rfc822 [EEE,] dd MMM yyyy HH:mm::ss z
			// assume 4 digit year

			SimpleDateFormat	format;

			if (!date_str.contains(",")){

				format = new SimpleDateFormat("dd MMM yyyy HH:mm:ss z", Locale.US );

			}else{

				format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US );
			}


			return( format.parse( date_str ));

		}catch( ParseException e ){

			String[]	fallbacks =
			{
				"dd MMM yyyy HH:mm:ss z",				// As above but laxer
				"EEE dd MMM yyyy HH:mm:ss z",			// As above but laxer
				"EEE MMM dd HH:mm:ss z yyyy",			// Fri Sep 26 00:00:00 EDT 2008
				"EEE MMM dd HH:mm z yyyy",				// Fri Sep 26 00:00 EDT 2008
				"EEE MMM dd HH z yyyy",					// Fri Sep 26 00 EDT 2008
				"yyyy-MM-dd HH:mm:ss",					// 2009-02-08 22:56:45
				"yyyy-MM-dd",							// 2009-02-08
			};

				// remove commas as these keep popping up in silly places

			date_str = date_str.replace( ',', ' ' );

				// remove duplicate white space

			date_str = date_str.replaceAll( "(\\s)+", " " );

			for (int i=0;i<fallbacks.length;i++){

				try{
					return(  new SimpleDateFormat(fallbacks[i], Locale.US ).parse( date_str ));

				}catch( ParseException f ){
				}
			}

			Debug.outNoStack( "RSSUtils: failed to parse RSS date: " + date_str );

			return( null );
		}
	}

	public static Date
	parseAtomDate(
		String	date_str )
	{
		date_str = date_str.trim();

		if ( date_str.length() == 0 ){

			return( null );
		}

			// full-time from http://tools.ietf.org/html/rfc3339 with T and Z

		final String[]	formats = {
				"yyyy-MM-dd'T'kk:mm:ss'Z'",
				"yyyy-MM-dd'T'kk:mm:ssz",
				"yyyy-MM-dd'T'kk:mm:ssZ",
				"yyyy-MM-dd'T'kk:mm:ss",

				"yyyy-MM-dd'T'kk:mm'Z'",
				"yyyy-MM-dd'T'kk:mmz",
				"yyyy-MM-dd'T'kk:mmZ",
				"yyyy-MM-dd'T'kk:mm",

				"yyyy-MM-dd-hh:mm:ss a",				// 2012-03-13-10:33:55 PM
		};


		for (int i=0;i<formats.length;i++){

			try{

				SimpleDateFormat format = new SimpleDateFormat( formats[i], Locale.US );

				return( format.parse( date_str ));

			}catch( ParseException e ){

				// Debug.printStackTrace(e);
			}
		}

		Debug.outNoStack( "RSSUtils: failed to parse Atom date: " + date_str );

		return( null );
	}

	public static boolean
	isRSSFeed(
		File		file )
	{
		try{
			String str = FileUtil.readFileAsString( file, 512 ).toLowerCase();

			str = str.trim().toLowerCase( Locale.US );

			if ( str.startsWith( "<?xml" )){

				if ( str.contains( "<feed" ) || str.contains( "<rss" )){

					InputStream is = new BufferedInputStream( FileUtil.newFileInputStream( file ));

					try{
						new RSSFeedImpl( new UtilitiesImpl( null, null ), null, is );

						return( true );

					}finally{

						is.close();
					}
				}
			}
		}catch( Throwable e ){
		}

		return( false );
	}

	public static void
	main(
		String[]	args )
	{
		System.out.println( parseRSSDate( "2013-08-11T18:30:00.000Z" ));
	}
}
