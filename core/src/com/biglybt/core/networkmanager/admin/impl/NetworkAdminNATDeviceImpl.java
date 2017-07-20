/*
 * Created on 1 Nov 2006
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


package com.biglybt.core.networkmanager.admin.impl;

import java.net.InetAddress;

import com.biglybt.core.networkmanager.admin.NetworkAdminNATDevice;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemTime;
import com.biglybt.plugin.upnp.UPnPPluginService;

public class
NetworkAdminNATDeviceImpl
	implements NetworkAdminNATDevice
{
	private final UPnPPluginService		service;
	private InetAddress				external_address;
	private long					address_time;

	protected
	NetworkAdminNATDeviceImpl(
		UPnPPluginService		_service )
	{
		service	= _service;
	}

	@Override
	public String
	getName()
	{
		return( service.getName());
	}

	@Override
	public InetAddress
	getAddress()
	{
		try{

			return( InetAddress.getByName(service.getAddress()));

		}catch( Throwable e ){

			Debug.printStackTrace(e);

			return( null );
		}
	}

	@Override
	public int
	getPort()
	{
		return( service.getPort());
	}

	@Override
	public InetAddress
	getExternalAddress()
	{
		long	now = SystemTime.getCurrentTime();

		if ( 	external_address != null &&
				now > address_time &&
				now - address_time < 60*1000 ){

			return( external_address );
		}

		try{
			external_address = InetAddress.getByName(service.getExternalAddress());

			address_time = now;

		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}

		return( external_address );
	}

	protected boolean
	sameAs(
		NetworkAdminNATDeviceImpl	other )
	{
		if ( 	!getAddress().equals( other.getAddress()) ||
				getPort() != other.getPort()){

			return( false );
		}

		InetAddress e1 = getExternalAddress();
		InetAddress e2 = other.getExternalAddress();

		if ( e1 == null && e2 == null ){

			return( true );
		}
		if ( e1 == null || e2 == null ){

			return( false );
		}

		return( e1.equals( e2 ));
	}

	@Override
	public String
	getString()
	{
		String res = getName();

		res += ": address=" + service.getAddress() + ":" + service.getPort();

		InetAddress ext = getExternalAddress();

		if ( ext == null ){

			res += ", no public address available";
		}else{

			res += ", public address=" + ext.getHostAddress();
		}

		return( res );
	}
}
