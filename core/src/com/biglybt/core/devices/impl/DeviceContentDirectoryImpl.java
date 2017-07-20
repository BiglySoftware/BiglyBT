/*
 * Created on Jan 28, 2009
 * Created by Paul Gardner
 *
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
 */


package com.biglybt.core.devices.impl;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import com.biglybt.core.devices.Device;
import com.biglybt.core.devices.DeviceContentDirectory;
import com.biglybt.core.util.Debug;
import com.biglybt.net.upnp.UPnPDevice;
import com.biglybt.net.upnp.UPnPService;

public class
DeviceContentDirectoryImpl
	extends DeviceUPnPImpl
	implements DeviceContentDirectory
{
	private UPnPService		upnp_service;

	protected
	DeviceContentDirectoryImpl(
		DeviceManagerImpl	_manager,
		UPnPDevice			_device,
		UPnPService			_service )
	{
		super( _manager, _device, Device.DT_CONTENT_DIRECTORY );

		upnp_service = _service;
	}

	protected
	DeviceContentDirectoryImpl(
		DeviceManagerImpl	_manager,
		Map					_map )

		throws IOException
	{
		super(_manager, _map );
	}

	@Override
	protected boolean
	updateFrom(
		DeviceImpl		_other,
		boolean			_is_alive )
	{
		if ( !super.updateFrom( _other, _is_alive )){

			return( false );
		}

		if ( !( _other instanceof DeviceContentDirectoryImpl )){

			Debug.out( "Inconsistent" );

			return( false );
		}

		DeviceContentDirectoryImpl other = (DeviceContentDirectoryImpl)_other;

		if ( other.upnp_service != null ){

			upnp_service = other.upnp_service;
		}

		return( true );
	}

	@Override
	public List<URL>
	getControlURLs()
	{
		if ( upnp_service != null ){

			try{
				return( upnp_service.getControlURLs());

			}catch( Throwable e ){

				Debug.out( e );
			}
		}

		return( null );
	}

	@Override
	public void
	setPreferredControlURL(
		URL url)
	{
		if ( upnp_service != null ){

			upnp_service.setPreferredControlURL( url );
		}
	}
}
