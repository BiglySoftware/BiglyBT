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

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.net.upnp.services.UPnPWANConnection;
import com.biglybt.net.upnp.services.UPnPWANConnectionPortMapping;
import com.biglybt.pif.logging.LoggerChannel;
import com.biglybt.pif.ui.config.BooleanParameter;
import com.biglybt.pif.ui.config.StringParameter;

public class
UPnPPluginService
{
	private UPnPWANConnection connection;
	private StringParameter			desc_prefix;
	private BooleanParameter 		alert_success;
	private BooleanParameter 		grab_ports;
	private BooleanParameter 		alert_other_port_param;
	private BooleanParameter		release_mappings;

	protected List<serviceMapping>	service_mappings = new ArrayList<>();

	protected AEMonitor	this_mon 	= new AEMonitor( "UPnPPluginService" );

	protected
	UPnPPluginService(
		UPnPWANConnection				_connection,
		UPnPWANConnectionPortMapping[]	_ports,
		StringParameter					_desc_prefix,
		BooleanParameter				_alert_success,
		BooleanParameter				_grab_ports,
		BooleanParameter				_alert_other_port_param,
		BooleanParameter				_release_mappings)
	{
		connection				= _connection;
		desc_prefix				= _desc_prefix;
		alert_success			= _alert_success;
		grab_ports				= _grab_ports;
		alert_other_port_param	= _alert_other_port_param;
		release_mappings		= _release_mappings;

		for (int i=0;i<_ports.length;i++){

			service_mappings.add( new serviceMapping( _ports[i]));
		}
	}

	public String
	getName()
	{
		return( connection.getGenericService().getDevice().getRootDevice().getDevice().getFriendlyName());
	}

	public String
	getInfo()
	{
		return( connection.getGenericService().getDevice().getRootDevice().getInfo());
	}
	public String
	getAddress()
	{
		return( connection.getGenericService().getDevice().getRootDevice().getLocation().getHost());
	}

	public int
	getPort()
	{
		URL	url = connection.getGenericService().getDevice().getRootDevice().getLocation();

		int	port = url.getPort();

		if ( port == -1 ){

			port = url.getDefaultPort();
		}

		return( port );
	}

	public String
	getExternalAddress()
	{
		try{
			return( connection.getExternalIPAddress());

		}catch( Throwable e ){

			return( null );
		}
	}

	public UPnPWANConnection
	getService()
	{
		return( connection );
	}

	protected String
	getOldDescriptionForPort(
		int		port )
	{
			// Remove one day - port name was changed as some routers use name uniqueness
			// to manage ports, hence UDP and TCP with same name failed

		return( desc_prefix.getValue() + " " + port );
	}

	protected String
	getDescriptionForPort(
		boolean	TCP,
		int		port )
	{
		return( desc_prefix.getValue() + " " + port + " " + (TCP?"TCP":"UDP"));
	}

	protected void
	checkMapping(
		LoggerChannel		log,
		UPnPMapping			mapping )
	{
		try{
			this_mon.enter();

			if ( mapping.isEnabled()){

					// check for change of port number and delete old value if so

				for (int i=0;i<service_mappings.size();i++){

					serviceMapping	sm = (serviceMapping)service_mappings.get(i);

					if ( sm.getMappings().contains( mapping )){

						if ( sm.getPort() != mapping.getPort()){

							removeMapping( log, mapping, sm, false );
						}
					}
				}

				serviceMapping	grab_in_progress	= null;

				String local_address = connection.getGenericService().getDevice().getRootDevice().getLocalAddress().getHostAddress();

				for (int i=0;i<service_mappings.size();i++){

					serviceMapping	sm = (serviceMapping)service_mappings.get(i);

					if ( 	sm.isTCP() 		== mapping.isTCP() &&
							sm.getPort() 	== mapping.getPort()){

						if ( sm.getInternalHost().equals( local_address )){

								// make sure we tie this to the mapping in case it
								// was external to begin with

							sm.addMapping( mapping  );

							if ( !sm.getLogged(mapping)){

								sm.setLogged(mapping);

								log.log( "Mapping " + mapping.getString() + " already established" );
							}

							return;

						}else{

							if ( !grab_ports.getValue() ){

								if ( !sm.getLogged(mapping)){

									sm.setLogged(mapping);

									String	text =
										MessageText.getString(
											"upnp.alert.differenthost",
											new String[]{ mapping.getString(), sm.getInternalHost()});

									if ( alert_other_port_param.getValue()){

										log.logAlertRepeatable( LoggerChannel.LT_WARNING, text );
									}else{

										log.log( text);
									}
								}

								return;

							}else{

									// we're going to grab it

								sm.addMapping( mapping  );

								grab_in_progress	= sm;
							}
						}
					}
				}

					// not found - try and establish it + add entry even if we fail so
					// that we don't retry later

				String	error_text = null;

				try{
					connection.addPortMapping(
						mapping.isTCP(), mapping.getPort(),
						getDescriptionForPort( mapping.isTCP(), mapping.getPort()));

					String	text;

					if ( grab_in_progress != null ){

						text = MessageText.getString(
								"upnp.alert.mappinggrabbed",
								new String[]{ mapping.getString(), grab_in_progress.getInternalHost()});
					}else{

						text = MessageText.getString(
								"upnp.alert.mappingok",
								new String[]{ mapping.getString()});
					}

					log.log( text );

					if ( alert_success.getValue()){

						log.logAlertRepeatable( LoggerChannel.LT_INFORMATION, text );
					}

				}catch( Throwable e ){

					error_text = Debug.getNestedExceptionMessage( e );

					String	text =
						MessageText.getString(
								"upnp.alert.mappingfailed",
								new String[]{ mapping.getString()});

					log.log( text );

					if ( alert_other_port_param.getValue()){

						log.logAlertRepeatable( LoggerChannel.LT_ERROR, text );
					}
				}

				if ( grab_in_progress == null ){

					serviceMapping	new_mapping = new serviceMapping( mapping );

					new_mapping.setError( error_text );

					service_mappings.add( new_mapping );

				}else{

					grab_in_progress.setError( error_text );
				}

			}else{
					// mapping is disabled

				removeMapping( log, mapping, false );
			}
		}finally{

			this_mon.exit();
		}
	}

	protected void
	removeMapping(
		LoggerChannel		log,
		UPnPMapping			mapping,
		boolean				end_of_day )
	{
		try{
			this_mon.enter();

			for (int i=0;i<service_mappings.size();i++){

				serviceMapping	sm = (serviceMapping)service_mappings.get(i);

				if ( 	sm.isTCP() == mapping.isTCP() &&
						sm.getPort() == mapping.getPort() &&
						sm.getMappings().contains( mapping )){

					removeMapping( log, mapping, sm, end_of_day );

					return;
				}
			}
		}finally{

			this_mon.exit();
		}
	}

	protected void
	removeMapping(
		LoggerChannel		log,
		UPnPMapping			upnp_mapping,
		serviceMapping		service_mapping,
		boolean				end_of_day )
	{
		if ( service_mapping.isExternal()){

			log.log( "Mapping " + service_mapping.getString() + " not removed as not created by " + Constants.BIGLYBT_NAME );

		}else{
			int	persistent	=  UPnPMapping.PT_DEFAULT;

			List	mappings = service_mapping.getMappings();

			for (int i=0;i<mappings.size();i++){

				UPnPMapping	map = (UPnPMapping)mappings.get(i);

				int	p = map.getPersistent();

				if ( p == UPnPMapping.PT_DEFAULT ){

						// default - leave as is

				}else if ( p == UPnPMapping.PT_TRANSIENT ){

						// transient overrides default

					if ( persistent == UPnPMapping.PT_DEFAULT ){

						persistent	= p;
					}
				}else{

						// persistent overrides all others

					persistent	= UPnPMapping.PT_PERSISTENT;
				}
			}

				// set effective persistency

			if ( persistent == UPnPMapping.PT_DEFAULT ){

				persistent = release_mappings.getValue()?UPnPMapping.PT_TRANSIENT:UPnPMapping.PT_PERSISTENT;
			}

				// only time we take note of whether or not to release the mapping is
				// at closedown

			if ( end_of_day && persistent == UPnPMapping.PT_PERSISTENT ){

				log.log( "Mapping " + service_mapping.getString() + " not removed as mapping is persistent" );

			}else{

					// get the name here for the deletion case so that the subsequent message makes
					// sense (as the name is derived from the current mappings, so getting it after
					// deleting it results in a name of <external>)

				String	service_name = service_mapping.getString();

				service_mapping.removeMapping( upnp_mapping );

				if ( service_mapping.getMappings().size() == 0 ){

					try{
						connection.deletePortMapping(
								service_mapping.isTCP(), service_mapping.getPort());

						log.log( "Mapping " + service_name + " removed" );

					}catch( Throwable e ){

						log.log( "Mapping " + service_name + " failed to delete", e );
					}

					service_mappings.remove(service_mapping);

				}else{

						// don't log during closedown as the remaining interests should be removed too
					
					if ( !end_of_day ){
						
						log.log( "Mapping " + service_mapping.getString() + " not removed as interest remains" );
					}
				}
			}
		}
	}

	public serviceMapping[]
	getMappings()
	{
		try{
			this_mon.enter();

			return( service_mappings.toArray( new serviceMapping[service_mappings.size()]));

		}finally{

			this_mon.exit();
		}
	}

	public String
	getString()
	{
		String str = "name=" + getName() + ",info=" + getInfo() + ",int=" + getAddress() + ":" + getPort() + ",ext=" + getExternalAddress();

		serviceMapping[] sms = getMappings();

		for ( serviceMapping sm: sms ){

			String error = sm.getError();

			if ( error != null ){

				str += ":" + sm.getString() + " -> " + error;
			}
		}

		return( str );
	}

	public class
	serviceMapping
	{
		private List		mappings	= new ArrayList();

		private boolean		tcp;
		private int			port;
		private String		internal_host;

		private boolean		external;		// true -> not defined by us

		private List		logged_mappings = new ArrayList();

		private String		error;

		protected
		serviceMapping(
			UPnPWANConnectionPortMapping		device_mapping )
		{
			tcp				= device_mapping.isTCP();
			port			= device_mapping.getExternalPort();
			internal_host	= device_mapping.getInternalHost();

			String	desc = device_mapping.getDescription();

			if ( 	desc == null ||
					!(	desc.equalsIgnoreCase( getOldDescriptionForPort( port )) ||
						desc.equalsIgnoreCase( getDescriptionForPort( tcp, port )))){

				external		= true;
			}
		}

		protected
		serviceMapping(
			UPnPMapping		_mapping )
		{
			mappings.add( _mapping );

			tcp				= _mapping.isTCP();
			port			= _mapping.getPort();
			internal_host	= connection.getGenericService().getDevice().getRootDevice().getLocalAddress().getHostAddress();
		}

		public boolean
		isExternal()
		{
			return( external );
		}

		protected List
		getMappings()
		{
			return( mappings );
		}

		protected void
		addMapping(
			UPnPMapping	_mapping )
		{
			if ( !mappings.contains( _mapping )){

				mappings.add( _mapping );
			}
		}

		protected void
		removeMapping(
			UPnPMapping	_mapping )
		{
			mappings.remove( _mapping );
		}

		protected boolean
		getLogged(
			UPnPMapping	mapping )
		{
			return( logged_mappings.contains( mapping ));
		}

		protected void
		setLogged(
			UPnPMapping	mapping )
		{
			if ( !logged_mappings.contains( mapping )){

				logged_mappings.add( mapping );
			}
		}

		public boolean
		isTCP()
		{
			return( tcp );
		}

		public int
		getPort()
		{
			return( port );
		}

		public String
		getInternalHost()
		{
			return( internal_host );
		}

		private String
		getError()
		{
			return( error );
		}

		private void
		setError(
			String	_error )
		{
			error = _error;
		}

		public String
		getString()
		{
			if ( mappings.size() == 0 ){

				return( "<external> (" + (isTCP()?"TCP":"UDP")+"/"+getPort()+")" );

			}else{

				String	str = "";

				for (int i=0;i<mappings.size();i++){
					str += (i==0?"":",")+ ((UPnPMapping)mappings.get(i)).getString( getPort());
				}

				return( str );
			}
		}
	}
}