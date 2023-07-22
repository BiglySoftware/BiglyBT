/*
 * Created on 21-Jun-2004
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

package com.biglybt.core.internat;

import java.io.File;
import java.io.UnsupportedEncodingException;

import com.biglybt.core.util.ByteFormatter;
import com.biglybt.core.util.SHA1Hasher;

/**
 * @author parg
 *
 */

public class
LocaleUtilDecoderFallback
	implements LocaleUtilDecoder
{
	public static final String	NAME	= "Fallback";

	private static volatile int		max_ok_name_length	= 64;
	private static volatile boolean max_ok_name_length_determined;

		// don't change these, it'll stuff up people with torrents that are using the
		// fallback encoding

	private static final String VALID_CHARS = "abcdefghijklmnopqrstuvwxyz1234567890_-.";

	private final int		index;


	protected
	LocaleUtilDecoderFallback(
		int		_index )
	{
		index	= _index;
	}

	@Override
	public String
	getName()
	{
		return( NAME );
	}

	@Override
	public int
	getIndex()
	{
		return( index );
	}

	@Override
	public String
	tryDecode(
		byte[]		bytes,
		boolean		lax )
	{
		return( decode( bytes ));
	}

	@Override
	public String
	decodeString(
		byte[]		bytes )

		throws UnsupportedEncodingException
	{
		return( decode( bytes ));
	}

	protected String
	decode(
		byte[]	data )
	{
		if ( data == null ){

			return( null );
		}

		StringBuffer	res = new StringBuffer( data.length*2 );

		for (int i=0;i<data.length;i++){

			byte	c = data[i];

			if ( VALID_CHARS.indexOf( Character.toLowerCase((char)c)) != -1 ){

				res.append((char)c);

			}else{

				res.append( "_" );
				res.append( ByteFormatter.nicePrint(c));
			}
		}

			// more often that not these decoded values are used for filenames. Windows has a limit
			// of 250 (ish) chars, so we do something sensible with longer values

		int	len = res.length();

		if ( len > max_ok_name_length ){

				// could be a file system out there that supports arbitrarily long names, so
				// we can't pre-calculate the max

			if ( 	( !max_ok_name_length_determined )&&
					fileLengthOK( len )){

					// this length is ok, bump up the known limit

				max_ok_name_length = len;

			}else{

					// won't fit

				if ( !max_ok_name_length_determined ){

					for (int i=max_ok_name_length+16;i<len;i+=16 ){

						if ( fileLengthOK( i )){

							max_ok_name_length	= i;

						}else{

							break;
						}
					}

					max_ok_name_length_determined	= true;
				}

					// try and preserve extension

				String	extension = null;

				int	pos = res.lastIndexOf(".");

				if ( pos != -1 ){

						// include the "."

					extension = res.substring( pos );

					if ( extension.length() == 1 || extension.length() > 4 ){

						extension = null;
					}
				}
					// replace the end of the string with a hash value to ensure uniqueness

				byte[] hash = new SHA1Hasher().calculateHash( data );

				String	hash_str = ByteFormatter.nicePrint( hash, true );

				res = new StringBuffer(res.substring(
							0,
							max_ok_name_length - hash_str.length() - (extension == null?0:extension.length())));

				res.append( hash_str );

				if ( extension != null ){

					res.append( extension );
				}
			}
		}

		return( res.toString());
	}

	protected boolean
	fileLengthOK(
		int		len )
	{
		StringBuilder n = new StringBuilder( len );

		for (int i=0;i<len;i++){

			n.append( "A" );
		}

		try{
			File f = File.createTempFile( n.toString(), "" );

			f.delete();

			return( true );

		}catch( Throwable e ){

			return( false );
		}
	}
}
