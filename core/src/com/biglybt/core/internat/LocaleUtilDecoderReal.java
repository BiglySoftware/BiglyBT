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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.util.Arrays;

import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.Constants;


public class
LocaleUtilDecoderReal
	implements LocaleUtilDecoder
{
	protected final CharsetDecoder	decoder;
	protected final int				index;

	private AEMonitor this_mon = new AEMonitor("LUDR");

	protected
	LocaleUtilDecoderReal(
		int				_index,
		CharsetDecoder	_decoder )
	{
		index		= _index;
		decoder		= _decoder;
	}

	@Override
	public String
	getName()
	{
		return( decoder.charset().name());
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
		byte[]		array,
		boolean		lax )
	{
		try{
			ByteBuffer bb = ByteBuffer.wrap(array);

			CharBuffer cb = CharBuffer.allocate(array.length);

			CoderResult cr;
			this_mon.enter();
			try {
				cr = decoder.decode(bb, cb, true);
			} finally {
				this_mon.exit();
			}

			if ( !cr.isError() ){

				cb.flip();

				String	str = cb.toString();

					// lax means that as long as the conversion works we consider it usable
					// as opposed to strict which requires reverse-conversion equivalence

				if ( lax ){

					return( str );
				}

				byte[]	b2 = str.getBytes( getName() );

					// make sure the conversion is symmetric (there are cases where it appears
					// to work but in fact converting back to bytes leads to a different
					// result

				/*
				for (int k=0;k<str.length();k++){
					System.out.print( Integer.toHexString(str.charAt(k)));
				}
				System.out.println("");
				*/

				if ( Arrays.equals( array, b2 )){

					return( str );
				}
			}

			return( null );

		}catch( Throwable e ){

				// Throwable here as we can get "classdefnotfound" + others if the decoder
				// isn't available

			return( null );
		}
	}

	@Override
	public String decodeString(byte[] bytes) {
		if ( bytes == null ){

			return( null );
		}

		try{
			ByteBuffer bb = ByteBuffer.wrap(bytes);

			CharBuffer cb = CharBuffer.allocate(bytes.length);

			CoderResult cr;
			this_mon.enter();
			try {
				cr = decoder.decode(bb, cb, true);
			} finally {
				this_mon.exit();
			}

			if ( !cr.isError() ){

				cb.flip();

				String	str = cb.toString();

				byte[]	b2 = str.getBytes(decoder.charset().name());

					// make sure the conversion is symmetric (there are cases where it appears
					// to work but in fact converting back to bytes leads to a different
					// result

				/*
				for (int k=0;k<str.length();k++){
					System.out.print( Integer.toHexString(str.charAt(k)));
				}
				System.out.println("");
				*/

				if ( Arrays.equals( bytes, b2 )){

					return( str );
				}
			}
		}catch( Throwable e ){

			// Throwable here as we can get "classdefnotfound" + others if the decoder
			// isn't available

			// ignore
		}

		// no joy, default
		return new String(bytes, Constants.DEFAULT_ENCODING_CHARSET);
	}
}
