/*
 * Created on 16-Jun-2004
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

package com.biglybt.plugin.upnp;

/**
 * @author parg
 *
 */

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.util.*;
import com.biglybt.net.upnp.services.UPnPWANConnection;
import com.biglybt.pif.logging.LoggerChannel;

public class
UPnPMappingManager
{
	private static UPnPMappingManager	singleton;

	protected static synchronized UPnPMappingManager
	getSingleton(
		UPnPPlugin	plugin )
	{
		if ( singleton == null ){

			singleton = new UPnPMappingManager( plugin );
		}

		return( singleton );
	}

	private UPnPPlugin	plugin;

	private List<UPnPMapping>	mappings	= new ArrayList<>();

	private CopyOnWriteList<UPnPMappingManagerListener>			listeners	= new CopyOnWriteList<>();

	private AsyncDispatcher	async_dispatcher = new AsyncDispatcher();

	protected
	UPnPMappingManager(
		UPnPPlugin		_plugin )
	{
		plugin	= _plugin;

			// incoming data port

			// Zyxel routers currently seem to overwrite the TCP mapping for a given port
			// with the UDP one, leaving the TCP one non-operational. Hack to try setting them
			// in UDP -> TCP order to hopefully leave the more important one working :)

		addConfigPort( "upnp.mapping.dataport", false, "UDP.Listen.Port.Enable", "UDP.Listen.Port" );

			// this is actually the UDP tracker client mapping, very badly named params...

		addConfigPort( "upnp.mapping.trackerclientudp", false, "Server Enable UDP", "UDP.NonData.Listen.Port" );

			// note that the dht plugin registers its own mapping


		addConfigPort( "upnp.mapping.dataport", true, "TCP.Listen.Port.Enable", "TCP.Listen.Port" );
		
		addConfigPortList( "upnp.mapping.dataport", true, "TCP.Listen.Port.Enable", "TCP.Listen.AdditionalPorts" );

		addConfigPort( "upnp.mapping.dataport", true, "HTTP.Data.Listen.Port.Enable", "HTTP.Data.Listen.Port" );

			// tracker server TCP

		addConfigPort( "upnp.mapping.tcptrackerport", true, "Tracker Port Enable", "Tracker Port" );

		addConfigPortX( "upnp.mapping.tcptrackerport", true, "Tracker Port Enable", "Tracker Port Backups" );

		addConfigPort( "upnp.mapping.tcpssltrackerport", true, "Tracker Port SSL Enable", "Tracker Port SSL" );

		addConfigPortX( "upnp.mapping.tcpssltrackerport", true, "Tracker Port SSL Enable", "Tracker Port SSL Backups" );

			// tracker server UDP

		addConfigPort( "upnp.mapping.udptrackerport", false, "Tracker Port UDP Enable", "Tracker Port" );
	}

	protected void
	serviceFound(
		UPnPWANConnection		service )
	{
		boolean save_config = false;

		if (( service.getCapabilities() & UPnPWANConnection.CAP_UDP_TCP_SAME_PORT ) == 0 ){

				// doesn't support UDP and TCP on same port number - patch up
				// unfortunately some routers remember the stuffed ports and makes them unusable for
				// either UDP OR TCP until a HARD reset so we need to change both ports...

			UPnPMapping[]	maps = getMappings();

			for (int i=0;i<maps.length;i++){

				UPnPMapping	map = maps[i];

				if ( map.isEnabled() && map.isTCP()){

					List	others = getMappingEx( false, map.getPort());

					if ( others.size() == 0 ){

						continue;
					}

					boolean	enabled = false;

					for (int j=0;j<others.size();j++){

						UPnPMapping	other = (UPnPMapping)others.get(j);

						if ( other.isEnabled()){

							enabled	= true;
						}
					}

					if ( enabled ){

						int	new_port_1;
						int	new_port_2;

						while( true ){

							int	new_port = RandomUtils.generateRandomNetworkListenPort();

							if ( getMapping( true, new_port ) == null && getMapping( false, new_port ) == null){

								new_port_1 = new_port;

								break;
							}
						}

						while( true ){

							int	new_port = RandomUtils.generateRandomNetworkListenPort();

							if ( getMapping( true, new_port ) == null && getMapping( false, new_port ) == null){

								if ( new_port_1 != new_port ){

									new_port_2 = new_port;

									break;
								}
							}
						}

						String	others_str = "";

						for (int j=0;j<others.size();j++){

							UPnPMapping	other = (UPnPMapping)others.get(j);

							if ( other.isEnabled()){

								others_str += (others_str.length()==0?"":",") + other.getString( new_port_2 );
							}
						}

						plugin.logAlert(
								LoggerChannel.LT_WARNING,
								"upnp.portchange.alert",
								new String[]{
										map.getString( new_port_1 ),
										String.valueOf( map.getPort()),
										others_str,
										String.valueOf( map.getPort())});

						map.setPort( new_port_1 );

						for (int j=0;j<others.size();j++){

							UPnPMapping	other = (UPnPMapping)others.get(j);

							if ( other.isEnabled()){

								other.setPort( new_port_2 );
							}
						}

						save_config	= true;
					}
				}
			}
		}

		if ( save_config ){

			COConfigurationManager.save();
		}
	}

	protected UPnPMapping
	addConfigPort(
		String			name_resource,
		boolean			tcp,
		boolean			enabled,
		final String	int_param_name )
	{
		int	value = COConfigurationManager.getIntParameter(int_param_name);

		final UPnPMapping	mapping = addMapping( name_resource, tcp, value, enabled );

		mapping.addListener(
				new UPnPMappingListener()
				{
					@Override
					public void
					mappingChanged(
						UPnPMapping mapping)
					{
						COConfigurationManager.setParameter( int_param_name, mapping.getPort());
					}

					@Override
					public void
					mappingDestroyed(
						UPnPMapping	mapping )
					{
					}
				});

		addConfigListener(
				int_param_name,
				new ParameterListener()
				{
					@Override
					public void
					parameterChanged(
						String	name )
					{
						mapping.setPort( COConfigurationManager.getIntParameter(int_param_name));
					}
				});

		return( mapping );
	}

	protected void
	addConfigPort(
		String			name_resource,
		boolean			tcp,
		final String	enabler_param_name,
		final String	int_param_name )
	{
		boolean	enabled = COConfigurationManager.getBooleanParameter(enabler_param_name);

		final UPnPMapping	mapping = addConfigPort( name_resource, tcp, enabled, int_param_name );

		mapping.addListener(
				new UPnPMappingListener()
				{
					@Override
					public void
					mappingChanged(
						UPnPMapping mapping)
					{
						COConfigurationManager.setParameter( int_param_name, mapping.getPort());
					}

					@Override
					public void
					mappingDestroyed(
						UPnPMapping	mapping )
					{
					}
				});

		addConfigListener(
				enabler_param_name,
				new ParameterListener()
				{
					@Override
					public void
					parameterChanged(
						String	name )
					{
						mapping.setEnabled( COConfigurationManager.getBooleanParameter(enabler_param_name));
					}
				});
	}

	protected void
	addConfigPortList(
		String			name_resource,
		boolean			tcp,
		final String	enabler_param_name,
		final String	list_param_name )
	{
		COConfigurationManager.addAndFireParameterListener(
			list_param_name,
			new ParameterListener(){
				List<Long>	existing_ports = new ArrayList<>();
				
				@Override
				public void 
				parameterChanged(
					String parameterName)
				{
					synchronized( existing_ports ){
						
						boolean	enabled = COConfigurationManager.getBooleanParameter(enabler_param_name);
						
						List<Long>	ports = (List<Long>)COConfigurationManager.getListParameter( list_param_name, new ArrayList<>());
	
						for ( Long port: ports ){
							
							if ( !existing_ports.contains( port )){
								
								existing_ports.add( port );
								
								UPnPMapping	mapping = addMapping( name_resource, tcp, port.intValue(), enabled );
	
								mapping.addListener(
										new UPnPMappingListener()
										{
											@Override
											public void
											mappingChanged(
												UPnPMapping mapping)
											{
												Debug.out( "not supported" );
											}
			
											@Override
											public void
											mappingDestroyed(
												UPnPMapping	mapping )
											{
											}
										});
			
								addConfigListener(
										enabler_param_name,
										new ParameterListener()
										{
											@Override
											public void
											parameterChanged(
												String	name )
											{
												mapping.setEnabled( COConfigurationManager.getBooleanParameter(enabler_param_name));
											}
										});
							}
						}
					}
				}
			});	

	}
	
	protected void
	addConfigPortX(
		final String	name_resource,
		final boolean	tcp,
		final String	enabler_param_name,
		final String	string_param_name )
	{
		final List	config_mappings = new ArrayList();

		ParameterListener	l1 =
				new ParameterListener()
				{
					@Override
					public void
					parameterChanged(
						String	name )
					{
						boolean	enabled = COConfigurationManager.getBooleanParameter(enabler_param_name);

						List ports = stringToPorts( COConfigurationManager.getStringParameter( string_param_name ));

						for (int i=0;i<ports.size();i++){

							int		port = ((Integer)ports.get(i)).intValue();

							if ( config_mappings.size() <= i ){

								UPnPMapping	mapping =
									addMapping( name_resource, tcp, port, enabled );

								mapping.setEnabled( enabled );

								config_mappings.add( mapping );

							}else{

								((UPnPMapping)config_mappings.get(i)).setPort( port );
							}
						}

						for (int i=ports.size();i<config_mappings.size();i++){

							((UPnPMapping)config_mappings.get(i)).setEnabled( false );
						}
					}
				};

		addConfigListener( string_param_name, l1 );

		ParameterListener	l2 =
				new ParameterListener()
				{
					@Override
					public void
					parameterChanged(
						String	name )
					{
						List ports = stringToPorts( COConfigurationManager.getStringParameter( string_param_name ));

						boolean	enabled = COConfigurationManager.getBooleanParameter(enabler_param_name);

						for (int i=0;i<(enabled?ports.size():config_mappings.size());i++){

							((UPnPMapping)config_mappings.get(i)).setEnabled( enabled );
						}
					}
				};

		addConfigListener( enabler_param_name, l2 );


		l1.parameterChanged( null );
		l2.parameterChanged( null );
	}

	protected List
	stringToPorts(
		String	str )
	{
		str = str.replace(',', ';' );

		StringTokenizer	tok = new StringTokenizer( str, ";" );

		List	res = new ArrayList();

		while( tok.hasMoreTokens()){

			try{
				res.add( new Integer( tok.nextToken().trim()));

			}catch( Throwable e ){

				Debug.out("Invalid port entry in '" + str + "'", e);
			}
		}

		return( res );
	}

	public UPnPMapping
	addMapping(
		String		desc_resource,
		boolean		tcp,
		int			port,
		boolean		enabled )
	{
		// System.out.println( "UPnPMappingManager: added '" + desc_resource + "'" + (tcp?"TCP":"UDP") + "/" + port + ", enabled = " + enabled );

		UPnPMapping	mapping = new UPnPMapping(desc_resource, tcp, port, enabled );

		synchronized( mappings ){

			mappings.add( mapping );
		}

		added( mapping );

		return( mapping );
	}

	public UPnPMapping[]
	getMappings()
	{
		synchronized( mappings ){

			UPnPMapping[]		res = new UPnPMapping[mappings.size()];

			mappings.toArray( res );

			return( res );
		}
	}

	public UPnPMapping
	getMapping(
		boolean	tcp,
		int		port )
	{
		synchronized( mappings ){

			for (int i=0;i<mappings.size();i++){

				UPnPMapping	mapping = (UPnPMapping)mappings.get(i);

				if ( mapping.isTCP() == tcp && mapping.getPort() == port ){

					return( mapping );
				}
			}
		}

		return( null );
	}

	public List
	getMappingEx(
		boolean	tcp,
		int		port )
	{
		List	res = new ArrayList();

		synchronized( mappings ){

			for (int i=0;i<mappings.size();i++){

				UPnPMapping	mapping = (UPnPMapping)mappings.get(i);

				if ( mapping.isTCP() == tcp && mapping.getPort() == port ){

					res.add( mapping );
				}
			}
		}

		return( res );
	}

	protected void
	added(
		UPnPMapping		mapping )
	{
		mapping.addListener(
			new UPnPMappingListener()
			{
				@Override
				public void
				mappingChanged(
					UPnPMapping	mapping )
				{
				}

				@Override
				public void
				mappingDestroyed(
					UPnPMapping	mapping )
				{
					synchronized( mappings ){

						mappings.remove( mapping );
					}
				}
			});

		for (UPnPMappingManagerListener listener: listeners){

			try{
				listener.mappingAdded( mapping );

			}catch( Throwable e ){

				Debug.out( e );
			}
		}
	}

	public void
	addListener(
		UPnPMappingManagerListener	l )
	{
		listeners.add(l);
	}

	public void
	removeListener(
		UPnPMappingManagerListener	l )
	{
		listeners.remove(l);
	}

	protected void
	addConfigListener(
		final String				param,
		final ParameterListener		listener )
	{
		COConfigurationManager.addParameterListener(
			param,
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					String name )
				{
					async_dispatcher.dispatch(
						new AERunnable()
						{
							@Override
							public void
							runSupport()
							{
								listener.parameterChanged( param );
							}
						});
				}
			});
	}
}
