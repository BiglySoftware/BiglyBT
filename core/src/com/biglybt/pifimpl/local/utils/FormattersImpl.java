/*
 * File    : FormattersImpl.java
 * Created : 30-Mar-2004
 * By      : parg
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.pifimpl.local.utils;

/**
 * @author parg
 *
 */

import java.io.IOException;
import java.util.Comparator;
import java.util.Map;

import com.biglybt.core.util.*;
import com.biglybt.pif.utils.Formatters;

public class
FormattersImpl
	implements Formatters
{
	public
	FormattersImpl()
	{
	}

	@Override
	public String
	formatByteCountToKiBEtc(
		long		bytes )
	{
		return( DisplayFormatters.formatByteCountToKiBEtc( bytes ));
	}

	@Override
	public String
	formatByteCountToKiBEtcPerSec(
		long		bytes )
	{
		return( DisplayFormatters.formatByteCountToKiBEtcPerSec( bytes ));
	}

	@Override
	public String
	formatPercentFromThousands(
		long		thousands )
	{
		return( DisplayFormatters.formatPercentFromThousands( (int)thousands ));
	}

	@Override
	public String
	formatByteArray(
		byte[]		data,
		boolean		no_spaces )
	{
		return( ByteFormatter.nicePrint( data, no_spaces ));
	}

	@Override
	public String
	encodeBytesToString(
		byte[]		bytes )
	{
		return( ByteFormatter.encodeString( bytes ));
	}

	@Override
	public byte[]
	decodeBytesFromString(
		String		str )
	{
		return( ByteFormatter.decodeString( str ));
	}

	@Override
	public String formatDate(long millis) {
		return DisplayFormatters.formatCustomDateTime(millis);
	}

	@Override
	public String formatTimeOnly(long millis) {
		return DisplayFormatters.formatCustomTimeOnly(millis);
	}

	@Override
	public String formatTimeOnly(long millis, boolean with_secs) {
		return DisplayFormatters.formatCustomTimeOnly(millis, with_secs);
	}

	@Override
	public String formatDateOnly(long millis) {
		return DisplayFormatters.formatCustomDateOnly(millis);
	}


	@Override
	public String
	formatTimeFromSeconds(
		long		seconds )
	{
		return( DisplayFormatters.formatTime( seconds*1000 ));
	}


	@Override
	public String formatETAFromSeconds(long seconds ) {
		return TimeFormatter.format( seconds );
	}


	@Override
	public byte[]
	bEncode(
		Map	map )

		throws IOException
	{
		return( BEncoder.encode( map ));
	}

	@Override
	public Map
	bDecode(
		byte[]	data )

		throws IOException
	{
		return( BDecoder.decode( data ));
	}

	@Override
	public String
	base32Encode(
		byte[]		data )
	{
		return( Base32.encode( data ));
	}

	@Override
	public byte[]
	base32Decode(
		String		data )
	{
		return( Base32.decode( data ));
	}

	@Override
	public Comparator<String>
	getAlphanumericComparator(
		final boolean	ignore_case )
	{
		return( getAlphanumericComparator2( ignore_case ));
	}
	
	public static Comparator<String>
	getAlphanumericComparator2(
		final boolean	ignore_case )
	{
		return(
			new Comparator<String>()
			{
				@Override
				public int
				compare(
					String	s1,
					String	s2 )
				{
					int	l1 = s1.length();
					int	l2 = s2.length();

					int	c1_pos	= 0;
					int c2_pos	= 0;

					while( c1_pos < l1 && c2_pos < l2 ){

						char	c1 = s1.charAt( c1_pos++ );
						char	c2 = s2.charAt( c2_pos++ );

						if ( Character.isDigit(c1) && Character.isDigit(c2)){

							int	n1_pos = c1_pos-1;
							int n2_pos = c2_pos-1;

							while( c1_pos < l1 ){

								if ( !Character.isDigit( s1.charAt( c1_pos ))){

									break;
								}

								c1_pos++;
							}

							while(c2_pos<l2){

								if ( !Character.isDigit( s2.charAt( c2_pos ))){

									break;
								}

								c2_pos++;
							}

							int	n1_length = c1_pos - n1_pos;
							int n2_length = c2_pos - n2_pos;

							if ( n1_length != n2_length ){

								return( n1_length - n2_length );
							}

							for (int i=0;i<n1_length;i++){

								char	nc1 = s1.charAt( n1_pos++ );
								char	nc2 = s2.charAt( n2_pos++ );

								if ( nc1 != nc2 ){

									return( nc1 - nc2 );
								}
							}
						}else{

							if ( ignore_case ){

								 c1 = Character.toLowerCase( c1 );

								 c2 = Character.toLowerCase( c2 );
							}

							if ( c1 != c2 ){

								return( c1 - c2 );
							}
						}
					}

					return( l1 - l2);

				}
			});
	}
}
