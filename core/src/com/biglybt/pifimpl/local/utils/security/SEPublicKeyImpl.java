/*
 * Created on 19 Jun 2006
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

package com.biglybt.pifimpl.local.utils.security;

import java.util.Arrays;

import com.biglybt.core.util.HashWrapper;
import com.biglybt.pif.utils.security.SEPublicKey;

public class
SEPublicKeyImpl
	implements SEPublicKey
{
	private int		type;
	private int		instance;
	private byte[]	encoded;
	private int		hashcode;

	protected
	SEPublicKeyImpl(
		int			_type,
		int			_instance,
		byte[]		_encoded )
	{
		type		= _type;
		instance	= _instance;
		encoded		= _encoded;
		hashcode	= new HashWrapper( encoded ).hashCode();
	}

	@Override
	public int
	getType()
	{
		return( type );
	}

	@Override
	public int 
	getInstance()
	{
		return( instance );
	}
	
	@Override
	public byte[]
	encodePublicKey()
	{
		byte[]	res = new byte[encoded.length+1];

		res[0] = (byte)type;

		System.arraycopy( encoded, 0, res, 1, encoded.length );

		return( res );
	}

	@Override
	public byte[]
	encodeRawPublicKey()
	{
		byte[]	res = new byte[encoded.length];

		System.arraycopy( encoded, 0, res, 0, encoded.length );

		return( res );
	}

	public boolean
	equals(
		Object	other )
	{
		if ( other instanceof SEPublicKeyImpl ){

			return( Arrays.equals( encoded, ((SEPublicKeyImpl)other).encoded ));

		}else{

			return( false );
		}
	}

	public int
	hashCode()
	{
		return( hashcode );
	}
}
