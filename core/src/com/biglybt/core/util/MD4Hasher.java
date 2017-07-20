/*
 * Created on 08-Jun-2004
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

package com.biglybt.core.util;

import java.security.MessageDigest;

import org.gudy.bouncycastle.jce.provider.BouncyCastleProvider;


public class
MD4Hasher
{
	protected MessageDigest md4;

		/**
		 * Uses bouncy castle provider
		 *
		 */

    public
	MD4Hasher()
    {
    	try{
    		md4 = MessageDigest.getInstance("MD4", BouncyCastleProvider.PROVIDER_NAME );

    	}catch( Throwable e ){

    			// should never get here

    		Debug.printStackTrace( e );
    	}
    }

    public void
	reset()
    {
    	md4.reset();
    }

    public void
    update(
    	byte[]		data,
		int			pos,
		int			len )
    {
    	md4.update( data, pos, len );
    }

    public void
    update(
    	byte[]		data )
    {
    	update( data, 0, data.length );
    }

    public byte[]
    getDigest()
    {
    	return( md4.digest());
    }
}
