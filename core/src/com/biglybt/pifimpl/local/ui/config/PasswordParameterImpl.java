/*
 * Created on 10-Jun-2004
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

package com.biglybt.pifimpl.local.ui.config;


/**
 * @author parg
 *
 */

import java.security.MessageDigest;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SHA1Hasher;

import com.biglybt.pif.ui.config.PasswordParameter;

public class
PasswordParameterImpl
	extends 	ParameterImpl
	implements 	PasswordParameter
{
	protected 	int		encoding_type;
	private int widthInCharacters;

	public PasswordParameterImpl(String key, String labelKey,
			int _encoding_type) {
		super(key, labelKey);

		encoding_type	= _encoding_type;
	}

	@Override
	public void
	setValue(
		String	plain_password )
	{
		byte[] encoded;

		if ( plain_password == null || plain_password.length() == 0){

			encoded = new byte[0];

		}else{

			encoded = encode( plain_password );
		}

		COConfigurationManager.setParameter(getConfigKeyName(), encoded );
	}

	public int
	getEncodingType()
	{
		return( encoding_type );
	}

	@Override
	public byte[]
	getValue()
	{
		return COConfigurationManager.getByteParameter(getConfigKeyName());
	}

	protected byte[]
   	encode(
   		String		str )
   	{
		// bit of a mess here as all other than md5 use default char set whereas md5 uses utf-8

		try{

			return( encode( encoding_type == ET_MD5?str.getBytes( "UTF-8" ):str.getBytes()));

		}catch( Throwable e ){

			Debug.out( e );

			return( null );
		}
   	}

	public byte[]
	encode(
			byte[] bytes)
	{
		if ( encoding_type == ET_SHA1 ){

	        SHA1Hasher hasher = new SHA1Hasher();

	        return( hasher.calculateHash( bytes ));

		}else if ( encoding_type == ET_MD5 ){

			try{
				return( MessageDigest.getInstance( "md5" ).digest( bytes ));

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}

		return( bytes );
	}

	@Override
	public void setWidthInCharacters(int widthInCharacters) {
		this.widthInCharacters = widthInCharacters;
	}

	@Override
	public int getWidthInCharacters() {
		return widthInCharacters;
	}

	@Override
	public Object getValueObject() {
		return getValue();
	}

}
