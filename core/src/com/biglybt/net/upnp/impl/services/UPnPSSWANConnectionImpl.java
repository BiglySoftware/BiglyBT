/*
 * Created on 15-Jun-2004
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

package com.biglybt.net.upnp.impl.services;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.biglybt.core.util.*;
import com.biglybt.net.upnp.*;
import com.biglybt.net.upnp.impl.UPnPImpl;
import com.biglybt.net.upnp.impl.device.UPnPRootDeviceImpl;
import com.biglybt.net.upnp.services.UPnPWANConnection;
import com.biglybt.net.upnp.services.UPnPWANConnectionListener;
import com.biglybt.net.upnp.services.UPnPWANConnectionPortMapping;

/**
 * @author parg
 *
 */

public abstract class
UPnPSSWANConnectionImpl
	implements UPnPWANConnection
{
	private static AEMonitor	class_mon 	= new AEMonitor( "UPnPSSWANConnection" );
	private static List			services	= new ArrayList();

	static{

		SimpleTimer.addPeriodicEvent(
			"UPnPSSWAN:checker",
			10*60*1000,
			( ev )->{
				AEThread2.createAndStartDaemon(
					"UPnPSSWAN:checker",
					()->{
						try{
							List	to_check = new ArrayList();

							try{
								class_mon.enter();

								Iterator	it = services.iterator();

								while( it.hasNext()){

									UPnPSSWANConnectionImpl	s = (UPnPSSWANConnectionImpl)it.next();

									if ( s.getGenericService().getDevice().getRootDevice().isDestroyed()){

										it.remove();

									}else{

										to_check.add( s );
									}
								}

							}finally{

								class_mon.exit();
							}

							for (int i=0;i<to_check.size();i++){

								try{
									((UPnPSSWANConnectionImpl)to_check.get(i)).checkMappings();

								}catch( Throwable e ){

									//Debug.printStackTrace(e);
								}
							}
						}catch( Throwable e ){

							Debug.printStackTrace(e);
						}
					});
			});
	}

	private UPnPServiceImpl		service;
	private List				mappings	= new ArrayList();
	private List				listeners	= new ArrayList();

	private boolean				recheck_mappings	= true;

		// start off true to avoid logging first of repetitive failures

	private boolean				last_mapping_check_failed	= true;

	protected
	UPnPSSWANConnectionImpl(
		UPnPServiceImpl		_service )
	{
		service	= _service;

		try{
			class_mon.enter();

			services.add( this );

		}finally{

			class_mon.exit();
		}
	}

	@Override
	public int
	getCapabilities()
	{
		String	device_name = service.getDevice().getRootDevice().getDevice().getFriendlyName();

		int	capabilities = CAP_ALL;

		if ( device_name.equals( "WRT54G" )){

			capabilities = CAP_ALL & ~CAP_UDP_TCP_SAME_PORT;
		}

		return( capabilities );
	}

	@Override
	public UPnPService
	getGenericService()
	{
		return( service );
	}

	@Override
	public String[]
   	getStatusInfo()

   		throws UPnPException
   	{
		UPnPAction act = service.getAction( "GetStatusInfo" );

		if ( act == null ){

			log( "Action 'GetStatusInfo' not supported, binding not established" );

			throw( new UPnPException( "GetStatusInfo not supported" ));

		}else{

			UPnPActionInvocation inv = act.getInvocation();

			UPnPActionArgument[]	args = inv.invoke();

			String	connection_status	= null;
			String	connection_error	= null;
			String	uptime				= null;

			for (int i=0;i<args.length;i++){

				UPnPActionArgument	arg = args[i];

				String	name = arg.getName();

				if ( name.equalsIgnoreCase("NewConnectionStatus")){

					connection_status = arg.getValue();

				}else if ( name.equalsIgnoreCase("NewLastConnectionError")){

					connection_error = arg.getValue();

				}else if ( name.equalsIgnoreCase("NewUptime")){

					uptime = arg.getValue();
				}
			}

			return( new String[]{ connection_status, connection_error, uptime });
		}
   	}

	@Override
	public void
	periodicallyRecheckMappings(
		boolean	on )
	{
		recheck_mappings	= on;
	}

	protected void
	checkMappings()

		throws UPnPException
	{
		if ( !recheck_mappings ){

			return;
		}

		List	mappings_copy;

		try{
			class_mon.enter();

			mappings_copy = new ArrayList( mappings );

		}finally{

			class_mon.exit();
		}

		UPnPWANConnectionPortMapping[]	current = getPortMappings();

		Iterator	it = mappings_copy.iterator();

		while( it.hasNext()){

			portMapping	mapping = (portMapping)it.next();

			for (int j=0;j<current.length;j++){

				UPnPWANConnectionPortMapping	c = current[j];

				if ( 	c.getExternalPort() == mapping.getExternalPort() &&
						c.isTCP() 			== mapping.isTCP()){

					it.remove();

					break;
				}
			}
		}

		boolean	log	= false;

		if ( mappings_copy.size() > 0 ){

			if ( !last_mapping_check_failed ){

				last_mapping_check_failed	= true;

				log	= true;
			}
		}else{

			last_mapping_check_failed	= false;
		}

		it = mappings_copy.iterator();

		while( it.hasNext()){

			portMapping	mapping = (portMapping)it.next();

			try{
					// some routers appear to continually fail to report the mappings - avoid
					// reporting this

				if ( log ){

					log( "Re-establishing mapping " + mapping.getString());
				}

				addPortMapping(  mapping.isTCP(), mapping.getExternalPort(), mapping.getDescription());

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	@Override
	public void
	addPortMapping(
		boolean		tcp,			// false -> UDP
		int			port,
		String		description )

		throws UPnPException
	{
		UPnPAction act = service.getAction( "AddPortMapping" );

		if ( act == null ){

			log( "Action 'AddPortMapping' not supported, binding not established" );

		}else{

			UPnPActionInvocation add_inv = act.getInvocation();

			add_inv.addArgument( "NewRemoteHost", 				"" );		// "" = wildcard for hosts, 0 = wildcard for ports
			add_inv.addArgument( "NewExternalPort", 			"" + port );
			add_inv.addArgument( "NewProtocol", 				tcp?"TCP":"UDP" );
			add_inv.addArgument( "NewInternalPort", 			"" + port );
			add_inv.addArgument( "NewInternalClient",			service.getDevice().getRootDevice().getLocalAddress().getHostAddress());
			add_inv.addArgument( "NewEnabled", 					"1" );
			add_inv.addArgument( "NewPortMappingDescription", 	description );
			add_inv.addArgument( "NewLeaseDuration",			"0" );		// 0 -> infinite (?)

			boolean	ok = false;

			try{
				add_inv.invoke();

				ok	= true;

			}catch( UPnPException original_error ){

					// some routers won't add properly if the mapping's already there

				try{
					log("Problem when adding port mapping - will try to see if an existing mapping is in the way");
					deletePortMapping(tcp, port);

				}catch( Throwable e ){

					throw( original_error );
				}

				add_inv.invoke();

				ok	= true;

			}finally{

				((UPnPRootDeviceImpl)service.getDevice().getRootDevice()).portMappingResult(ok);

				for (int i=0;i<listeners.size();i++){

					UPnPWANConnectionListener	listener = (UPnPWANConnectionListener)listeners.get(i);

					try{
						listener.mappingResult( this, ok );

					}catch( Throwable e){

						Debug.printStackTrace(e);
					}
				}
			}

			try{
				class_mon.enter();

				Iterator	it = mappings.iterator();

				while( it.hasNext()){

					portMapping	m = (portMapping)it.next();

					if ( m.getExternalPort() == port && m.isTCP() == tcp ){

						it.remove();
					}
				}

				mappings.add( new portMapping( port, tcp, "", description ));

			}finally{

				class_mon.exit();
			}
		}
	}

	@Override
	public void
	deletePortMapping(
		boolean		tcp,
		int			port )

		throws UPnPException
	{
		UPnPAction act = service.getAction( "DeletePortMapping" );

		if ( act == null ){

			log( "Action 'DeletePortMapping' not supported, binding not removed" );

		}else{

			boolean	mapping_found = false;

			try{
				class_mon.enter();

				Iterator	it = mappings.iterator();

				while( it.hasNext()){

					portMapping	mapping = (portMapping)it.next();

					if ( 	mapping.getExternalPort() == port &&
							mapping.isTCP() == tcp ){

						it.remove();

						mapping_found	= true;

						break;
					}
				}
			}finally{

				class_mon.exit();
			}

			try{
				long	start = SystemTime.getCurrentTime();

				UPnPActionInvocation inv = act.getInvocation();

				inv.addArgument( "NewRemoteHost", 				"" );		// "" = wildcard for hosts, 0 = wildcard for ports
				inv.addArgument( "NewProtocol", 				tcp?"TCP":"UDP" );
				inv.addArgument( "NewExternalPort", 			"" + port );

				inv.invoke();

				long	elapsed = SystemTime.getCurrentTime() - start;

				if ( elapsed > 4000 ){

					String	info = service.getDevice().getRootDevice().getInfo();

					((UPnPImpl)service.getDevice().getRootDevice().getUPnP()).logAlert(
							"UPnP device '" + info + "' is taking a long time to release port mappings, consider disabling this via the UPnP configuration.",
							false,
							UPnPLogListener.TYPE_ONCE_EVER );
				}
			}catch( UPnPException e ){

					// only bitch about the failure if we believed we mapped it in the first place

				if ( mapping_found ){

					throw( e );

				}else{

					log( "Removal of mapping failed but not established explicitly so ignoring error" );
				}
			}
		}
	}

	@Override
	public UPnPWANConnectionPortMapping[]
	getPortMappings()

		throws UPnPException
	{
		boolean	ok = true;

		try{
			//UPnPStateVariable noe = service.getStateVariable("PortMappingNumberOfEntries");
			//System.out.println( "NOE = " + noe.getValue());

			int	entries = 0; //Integer.parseInt( noe.getValue());

				// some routers (e.g. Gudy's) return 0 here whatever!
				// In this case take mindless approach
				// hmm, even for my router the state variable isn't accurate...

			UPnPAction act	= service.getAction( "GetGenericPortMappingEntry" );

			if ( act == null ){

				log( "Action 'GetGenericPortMappingEntry' not supported, can't enumerate bindings" );

				return( new UPnPWANConnectionPortMapping[0] );

			}else{

				List	res = new ArrayList();

					// I've also seen some routers loop here rather than failing when the index gets too large (they
					// seem to keep returning the last entry) - check for a duplicate entry and exit if found

				portMapping	prev_mapping	= null;

				for (int i=0;i<(entries==0?512:entries);i++){

					UPnPActionInvocation inv = act.getInvocation();

					inv.addArgument( "NewPortMappingIndex", "" + i );

					try{
						UPnPActionArgument[] outs = inv.invoke();

						int		port			= 0;
						boolean	tcp				= false;
						String	internal_host	= null;
						String	description		= "";

						for (int j=0;j<outs.length;j++){

							UPnPActionArgument	out = outs[j];

							String	out_name = out.getName();

							if ( out_name.equalsIgnoreCase("NewExternalPort")){

								port	= Integer.parseInt( out.getValue());

							}else if ( out_name.equalsIgnoreCase( "NewProtocol" )){

								tcp = out.getValue().equalsIgnoreCase("TCP");

							}else if ( out_name.equalsIgnoreCase( "NewInternalClient" )){

								internal_host = out.getValue();

							}else if ( out_name.equalsIgnoreCase( "NewPortMappingDescription" )){

								description = out.getValue();
							}
						}

						if ( prev_mapping != null ){

							if ( 	prev_mapping.getExternalPort() == port &&
									prev_mapping.isTCP() == tcp ){

									// repeat, get out

								break;
							}
						}

						prev_mapping = new portMapping( port, tcp, internal_host, description );

						res.add( prev_mapping );

					}catch( UPnPException e ){

						if ( entries == 0 ){

							break;
						}

						ok	= false;

						throw(e);
					}
				}

				UPnPWANConnectionPortMapping[]	res2= new UPnPWANConnectionPortMapping[res.size()];

				res.toArray( res2 );

				return( res2 );
			}
		}finally{

			for (int i=0;i<listeners.size();i++){

				UPnPWANConnectionListener	listener = (UPnPWANConnectionListener)listeners.get(i);

				try{
					listener.mappingsReadResult( this, ok );

				}catch( Throwable e){

					Debug.printStackTrace(e);
				}

			}
		}
	}

	@Override
	public String
	getExternalIPAddress()

		throws UPnPException
	{
		UPnPAction act = service.getAction( "GetExternalIPAddress" );

		if ( act == null ){

			log( "Action 'GetExternalIPAddress' not supported, binding not established" );

			throw( new UPnPException( "GetExternalIPAddress not supported" ));

		}else{

			UPnPActionInvocation inv = act.getInvocation();

			UPnPActionArgument[]	args = inv.invoke();

			String	ip	= null;

			for (int i=0;i<args.length;i++){

				UPnPActionArgument	arg = args[i];

				String	name = arg.getName();

				if ( name.equalsIgnoreCase("NewExternalIPAddress")){

					ip = arg.getValue();
				}
			}

			return( ip );
		}
	}

	protected void
	log(
		String	str )
	{
		service.getDevice().getRootDevice().getUPnP().log( str );
	}

	@Override
	public void
	addListener(
		UPnPWANConnectionListener	listener )
	{
		listeners.add( listener );
	}

	@Override
	public void
	removeListener(
		UPnPWANConnectionListener	listener )
	{
		listeners.add( listener );
	}

	private static class
	portMapping
		implements UPnPWANConnectionPortMapping
	{
		protected int			external_port;
		protected boolean		tcp;
		protected String		internal_host;
		protected String		description;

		protected
		portMapping(
			int			_external_port,
			boolean		_tcp,
			String		_internal_host,
			String		_description )
		{
			external_port	= _external_port;
			tcp				= _tcp;
			internal_host	= _internal_host;
			description		= _description;
		}

		@Override
		public boolean
		isTCP()
		{
			return( tcp );
		}

		@Override
		public int
		getExternalPort()
		{
			return( external_port );
		}

		@Override
		public String
		getInternalHost()
		{
			return( internal_host );
		}

		@Override
		public String
		getDescription()
		{
			return( description );
		}

		protected String
		getString()
		{
			return( getDescription() + " [" + getExternalPort() + ":" + (isTCP()?"TCP":"UDP") + "]");
		}
	}
}
