/*
 * Created on May 29, 2014
 * Created by Paul Gardner
 *
 * Copyright 2014 Azureus Software, Inc.  All rights reserved.
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


package com.biglybt.core.dht.transport.udp.impl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.dht.transport.DHTTransportAlternativeContact;
import com.biglybt.core.util.BDecoder;
import com.biglybt.core.util.SystemTime;

public class
DHTTransportAlternativeContactImpl
	implements DHTTransportAlternativeContact
{
	private final byte		network_type;
	private final byte		version;
	private final int		seen_secs;
	private final byte[]	encoded;

	private final int		id;

	protected
	DHTTransportAlternativeContactImpl(
		byte			_network_type,
		byte			_version,
		short			_age,
		byte[]			_encoded )
	{
		network_type	= _network_type;
		version			= _version;
		encoded			= _encoded;
		
		seen_secs = (int)(SystemTime.getMonotonousTime()/1000) - (_age<0?Short.MAX_VALUE:_age);
		
		id = Arrays.hashCode( encoded );
	}

	@Override
	public int
	getNetworkType()
	{
		return( network_type&0xff );
	}

	@Override
	public int
	getVersion()
	{
		return( version&0xff );
	}

	@Override
	public int
	getID()
	{
		return( id );
	}

	@Override
	public int
	getLastAlive()
	{
		return( seen_secs );
	}

	@Override
	public Map<String,Object>
	getProperties()
	{
		try{
			return( BDecoder.decode( encoded ));

		}catch( Throwable e ){

			return(new HashMap<>());
		}
	}
}
