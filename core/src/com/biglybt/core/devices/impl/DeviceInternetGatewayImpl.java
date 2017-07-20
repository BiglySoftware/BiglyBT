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
import java.util.Set;
import java.util.TreeSet;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.devices.Device;
import com.biglybt.core.devices.DeviceInternetGateway;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Debug;
import com.biglybt.net.upnp.UPnPDevice;
import com.biglybt.net.upnp.services.UPnPWANConnection;
import com.biglybt.pif.PluginInterface;
import com.biglybt.plugin.upnp.UPnPMapping;
import com.biglybt.plugin.upnp.UPnPPlugin;
import com.biglybt.plugin.upnp.UPnPPluginService;

public class
DeviceInternetGatewayImpl
	extends DeviceUPnPImpl
	implements DeviceInternetGateway
{
	private static final int CHECK_MAPPINGS_PERIOD 		= 30*1000;
	private static final int CHECK_MAPPINGS_TICK_COUNT 	= CHECK_MAPPINGS_PERIOD / DeviceManagerImpl.DEVICE_UPDATE_PERIOD;


	static UPnPPlugin						upnp_plugin;

	static{
		CoreFactory.addCoreRunningListener(new CoreRunningListener() {

			@Override
			public void coreRunning(Core core) {

				try {
					PluginInterface pi_upnp = core.getPluginManager().getPluginInterfaceByClass(
							UPnPPlugin.class);

					if (pi_upnp != null) {

						upnp_plugin = (UPnPPlugin) pi_upnp.getPlugin();
					}
				} catch (Throwable e) {
				}
			}
		});
	}

	private boolean		mapper_enabled;

	private UPnPPluginService[]	current_services;
	private UPnPMapping[]		current_mappings;

	protected
	DeviceInternetGatewayImpl(
		DeviceManagerImpl			_manager,
		UPnPDevice					_device,
		List<UPnPWANConnection>		_connections )
	{
		super( _manager, _device, Device.DT_INTERNET_GATEWAY );

		updateStatus( 0 );
	}

	protected
	DeviceInternetGatewayImpl(
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

		if ( !( _other instanceof DeviceInternetGatewayImpl )){

			Debug.out( "Inconsistent" );

			return( false );
		}

		DeviceInternetGatewayImpl other = (DeviceInternetGatewayImpl)_other;

		return( true );
	}

	@Override
	protected void
	updateStatus(
		int		tick_count )
	{
		super.updateStatus( tick_count );

		if ( tick_count % CHECK_MAPPINGS_TICK_COUNT != 0 ){

			return;
		}

		mapper_enabled = upnp_plugin != null && upnp_plugin.isEnabled();

		UPnPDevice	device = getUPnPDevice();

		if ( mapper_enabled && device != null ){

			current_services = upnp_plugin.getServices( device );

			current_mappings = upnp_plugin.getMappings();
		}
	}

	@Override
	protected URL
	getPresentationURL(
		UPnPDevice		device )
	{
		URL	url = super.getPresentationURL( device );

		if ( url == null ){

			try{
					// no explicit one, try hitting location

				URL loc = device.getRootDevice().getLocation();

				URL	test_loc = new URL( loc.getProtocol() +  "://" + loc.getHost() + "/" );

				test_loc.openConnection().connect();

				return( test_loc );

			}catch( Throwable e ){
			}
		}

		return( url );
	}

	protected Set<mapping>
	getRequiredMappings()
	{
		Set<mapping>	res = new TreeSet<>();

		UPnPMapping[]		required_mappings 	= current_mappings;

		if ( required_mappings != null ){

			for ( UPnPMapping mapping: required_mappings ){

				if ( mapping.isEnabled()){

					res.add( new mapping( mapping ));
				}
			}
		}

		return( res );
	}

	protected Set<mapping>
	getActualMappings(
		UPnPPluginService	service )
	{
		UPnPPluginService.serviceMapping[] actual_mappings = service.getMappings();

		Set<mapping> actual = new TreeSet<>();

		for ( UPnPPluginService.serviceMapping act_mapping: actual_mappings ){

			mapping m = new mapping( act_mapping );

			actual.add( m );
		}

		return( actual );
	}

	@Override
	protected void
	getDisplayProperties(
		List<String[]>	dp )
	{
		super.getDisplayProperties( dp );

		addDP(dp, "device.router.is_mapping", mapper_enabled );

		UPnPPluginService[]	services = current_services;

		String	req_map_str = "";

		Set<mapping> required = getRequiredMappings();

		for ( mapping m: required ){

			req_map_str += (req_map_str.length()==0?"":",") + m.getString();
		}

		addDP( dp, "device.router.req_map", req_map_str );

		if ( services != null ){

			for ( UPnPPluginService service: services ){

				Set<mapping> actual = getActualMappings( service );

				String	act_map_str = "";

				for ( mapping m: actual ){

					if ( required.contains(m)){

						act_map_str += (act_map_str.length()==0?"":",") + m.getString();
					}
				}

				String service_name = MessageText.getString( "device.router.con_type", new String[]{ service.getService().getConnectionType() });

				addDP( dp, "!    " + service_name + "!", act_map_str );
			}
		}
	}

	protected static class
	mapping
		implements Comparable<mapping>
	{
		private boolean	is_tcp;
		private int		port;

		protected
		mapping(
			UPnPMapping m )
		{
			is_tcp		= m.isTCP();
			port		= m.getPort();
		}

		protected
		mapping(
			UPnPPluginService.serviceMapping		m )
		{
			is_tcp		= m.isTCP();
			port		= m.getPort();
		}

		@Override
		public int
		compareTo(
			mapping o )
		{
			int res = port - o.port;

			if ( res == 0 ){

				res = (is_tcp?1:0) - (o.is_tcp?1:0);
			}

			return( res );
		}

		public boolean
		equals(
			Object	_other )
		{
			if ( _other instanceof mapping ){

				mapping other = (mapping)_other;

				return( is_tcp == other.is_tcp && port == other.port );

			}else{

				return( false );
			}
		}

		public int
		hashCode()
		{
			return((port<<16) + (is_tcp?1:0));
		}

		public String
		getString()
		{
			return( (is_tcp?"TCP":"UDP") + " " + port );
		}
	}
}
