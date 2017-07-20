/*
 * Created on 12 Jun 2006
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

package com.biglybt.net.natpmp.upnp.impl;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.ThreadPool;
import com.biglybt.net.natpmp.NatPMPDevice;
import com.biglybt.net.upnp.*;
import com.biglybt.net.upnp.services.UPnPSpecificService;
import com.biglybt.net.upnp.services.UPnPWANConnection;
import com.biglybt.net.upnp.services.UPnPWANConnectionListener;
import com.biglybt.net.upnp.services.UPnPWANConnectionPortMapping;

public class
NatPMPUPnPRootDeviceImpl
	implements UPnPRootDevice
{
	private UPnP			upnp;
	private NatPMPDevice	nat_device;

	private String			USN		= "natpmp";
	private URL				location;

	private UPnPDevice device;
	private UPnPService[]	services;

	private ThreadPool		thread_pool;

	public
	NatPMPUPnPRootDeviceImpl(
		UPnP			_upnp,
		NatPMPDevice	_nat_device )

		throws Exception
	{
		upnp		= _upnp;
		nat_device	= _nat_device;

		location = new URL( "http://undefined/" );

		device = new NatPMPUPnPDevice();

		services = new UPnPService[]{ new NatPMPUPnPWANConnection() };

		thread_pool = new ThreadPool( "NatPMPUPnP", 1, true );
	}

	@Override
	public UPnP
	getUPnP()
	{
		return( upnp );
	}

	@Override
	public String
	getUSN()
	{
		return( USN );
	}

	@Override
	public URL
	getLocation()
	{
		return( location );
	}

	@Override
	public InetAddress
	getLocalAddress()
	{
		return( nat_device.getLocalAddress());
	}

	@Override
	public NetworkInterface
	getNetworkInterface()
	{
		return( nat_device.getNetworkInterface());
	}

	@Override
	public String
	getInfo()
	{
		return( "Nat-PMP" );
	}

	@Override
	public UPnPDevice
	getDevice()
	{
		return( device );
	}

	@Override
	public boolean
	isDestroyed()
	{
		return( false );
	}

	@Override
	public Map
	getDiscoveryCache()
	{
		return( null );
	}

	@Override
	public void
	addListener(
		UPnPRootDeviceListener l )
	{
	}

	@Override
	public void
	removeListener(
		UPnPRootDeviceListener	l )
	{
	}

	protected class
	NatPMPUPnPDevice
		implements UPnPDevice
	{
		@Override
		public String
		getDeviceType()
		{
			return( "NatPMP" );
		}

		@Override
		public String
		getFriendlyName()
		{
			return( "NatPMP" );
		}

		@Override
		public String
		getManufacturer()
		{
			return( "" );
		}

		@Override
		public String
		getManufacturerURL()
		{
			return( "" );
		}

		@Override
		public String
		getModelDescription()
		{
			return( "" );
		}

		@Override
		public String
		getModelName()
		{
			return( "" );
		}

		@Override
		public String
		getModelNumber()
		{
			return( "" );
		}

		@Override
		public String
		getModelURL()
		{
			return( "" );
		}

		@Override
		public String
		getPresentation()
		{
			return( "" );
		}

		@Override
		public UPnPDevice[]
		getSubDevices()
		{
			return( new UPnPDevice[0]);
		}

		@Override
		public UPnPService[]
		getServices()
		{
			return( services );
		}

		@Override
		public UPnPRootDevice
		getRootDevice()
		{
			return( NatPMPUPnPRootDeviceImpl.this );
		}

		@Override
		public UPnPDeviceImage[] getImages() {
			return new UPnPDeviceImage[0];
		}
	}

	protected class
	NatPMPUPnPWANConnection
		implements UPnPWANConnection, UPnPService
	{
		private NatPMPImpl	nat_impl;

		protected
		NatPMPUPnPWANConnection()

			throws UPnPException
		{
			nat_impl	= new NatPMPImpl( nat_device );
		}

		@Override
		public UPnPDevice
		getDevice()
		{
			return( device );
		}

		@Override
		public String
		getServiceType()
		{
				// pretend to be an ip connection

			return( "urn:schemas-upnp-org:service:WANIPConnection:1" );
		}

		@Override
		public String
		getConnectionType()
		{
			return( "IP" );	// ??
		}

		@Override
		public List<URL>
		getControlURLs()

			throws UPnPException
		{
			return(new ArrayList<>(0));
		}

		@Override
		public void
		setPreferredControlURL(
			URL url)
		{
		}

		@Override
		public boolean
		isConnectable()
		{
			return( true );
		}

		@Override
		public UPnPAction[]
		getActions()

			throws UPnPException
		{
			return( new UPnPAction[0] );
		}

		@Override
		public UPnPAction
		getAction(
			String		name )

			throws UPnPException
		{
			return( null );
		}

		@Override
		public UPnPStateVariable[]
		getStateVariables()

			throws UPnPException
		{
			return( new UPnPStateVariable[0] );
		}

		@Override
		public UPnPStateVariable
		getStateVariable(
			String		name )

			throws UPnPException
		{
			return( null );
		}

			/**
			 * gets a specific service if such is supported
			 * @return
			 */
		@Override
		public UPnPSpecificService
		getSpecificService()
		{
			return( this );
		}

		@Override
		public UPnPService
		getGenericService()
		{
			return( this );
		}

		@Override
		public boolean
		getDirectInvocations()
		{
			return( true );
		}

		@Override
		public void
		setDirectInvocations(
			boolean	force )
		{
		}


		@Override
		public void
		addPortMapping(
			final boolean		tcp,
			final int			port,
			final String		description )

			throws UPnPException
		{
			thread_pool.run(
				new AERunnable()
				{
					@Override
					public void
					runSupport()
					{
						try{

							nat_impl.addPortMapping( tcp, port, description );

						}catch( UPnPException e ){

							e.printStackTrace();
						}
					}
				});
		}

		@Override
		public UPnPWANConnectionPortMapping[]
		getPortMappings()

			throws UPnPException
		{
			return( nat_impl.getPortMappings());
		}

		@Override
		public void
		deletePortMapping(
			final boolean		tcp,
			final int			port )

			throws UPnPException
		{
			thread_pool.run(
					new AERunnable()
					{
						@Override
						public void
						runSupport()
						{
							try{
								nat_impl.deletePortMapping( tcp, port );

							}catch( UPnPException e ){

								e.printStackTrace();
							}
						}
					});
		}

		@Override
		public String[]
		getStatusInfo()

			throws UPnPException
		{
			return( nat_impl.getStatusInfo());
		}

		@Override
		public String
		getExternalIPAddress()

			throws UPnPException
		{
			return( nat_impl.getExternalIPAddress());
		}

		@Override
		public void
		periodicallyRecheckMappings(
			boolean	on )
		{
		}

		@Override
		public int
		getCapabilities()
		{
			return( CAP_ALL );
		}

		@Override
		public void
		addListener(
			UPnPWANConnectionListener	listener )
		{
		}

		@Override
		public void
		removeListener(
			UPnPWANConnectionListener	listener )
		{
		}
	}
}
