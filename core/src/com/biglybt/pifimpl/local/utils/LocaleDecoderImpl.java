/*
 * File    : LocaleDecoderImpl.java
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

import java.io.UnsupportedEncodingException;

import com.biglybt.core.internat.LocaleUtilDecoder;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.utils.LocaleDecoder;

public class
LocaleDecoderImpl
	implements LocaleDecoder
{
	LocaleUtilDecoder		decoder;

	protected
	LocaleDecoderImpl(
		LocaleUtilDecoder		_decoder )
	{
		decoder	= _decoder;
	}

	@Override
	public String
	getName()
	{
		return( decoder.getName());
	}

	@Override
	public String
	decode(
		byte[]		encoded_bytes )
	{
		try{
			return( decoder.decodeString( encoded_bytes ));

		}catch( UnsupportedEncodingException	e ){

			Debug.printStackTrace( e );

			return( null );
		}
	}
}
