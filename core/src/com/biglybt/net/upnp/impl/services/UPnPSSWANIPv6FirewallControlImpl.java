/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.net.upnp.impl.services;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.AEThread2;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SimpleTimer;
import com.biglybt.core.util.SystemTime;
import com.biglybt.net.upnp.UPnPAction;
import com.biglybt.net.upnp.UPnPActionArgument;
import com.biglybt.net.upnp.UPnPActionInvocation;
import com.biglybt.net.upnp.UPnPException;
import com.biglybt.net.upnp.UPnPLogListener;
import com.biglybt.net.upnp.UPnPService;
import com.biglybt.net.upnp.impl.UPnPImpl;
import com.biglybt.net.upnp.services.UPnPWANIPv6FirewallControl;
import com.biglybt.net.upnp.services.UPnPWANIPv6FirewallControlPinhole;

public class 
UPnPSSWANIPv6FirewallControlImpl
	implements UPnPWANIPv6FirewallControl
{
	private static final int		LEASE_SECS	= 10*60; // 2*60*60;
	private static final int		CHECK_SECS	= 10*60;
	
	private static AEMonitor	class_mon 	= new AEMonitor( "UPnPSSWANIPv6FirewallControl" );

	private static List<UPnPSSWANIPv6FirewallControlImpl>			services	= new ArrayList<>();

	static{

		SimpleTimer.addPeriodicEvent(
			"UPnPSSWANIPv6FirewallControl:checker",
			CHECK_SECS*1000,
			( ev )->{
				AEThread2.createAndStartDaemon(
					"UPnPSSWAN:checker",
					()->{
						try{
							List<UPnPSSWANIPv6FirewallControlImpl>	to_check = new ArrayList<>();

							try{
								class_mon.enter();

								Iterator<UPnPSSWANIPv6FirewallControlImpl>	it = services.iterator();

								while( it.hasNext()){

									UPnPSSWANIPv6FirewallControlImpl	s = it.next();

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
									to_check.get(i).checkMappings();

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
	
	private final UPnPServiceImpl	service;

	private List<Pinhole>						pinholes	= new ArrayList<>();

	protected
	UPnPSSWANIPv6FirewallControlImpl(
		UPnPServiceImpl		_service )
	{
		service = _service;
		
		try{
			class_mon.enter();

			services.add( this );

		}finally{

			class_mon.exit();
		}
	}
	
	@Override
	public UPnPService 
	getGenericService()
	{
		return( service );
	}
	
	@Override
	public void
	addPinhole(
		boolean		tcp,
		int			port,
		String		local_address,
		String		description )

		throws UPnPException
	{
		UPnPAction act = service.getAction( "AddPinhole" );

		if ( act == null ){

			log( "Action 'AddPinhole' not supported, pinhole not created" );

		}else{
			
			UPnPActionInvocation add_inv = act.getInvocation();

			add_inv.addArgument( "RemoteHost", 				"" );		// "" = wildcard for hosts, 0 = wildcard for ports
			add_inv.addArgument( "RemotePort", 				"" + port );
			add_inv.addArgument( "InternalClient",			local_address);
			add_inv.addArgument( "InternalPort", 			"" + port );
			add_inv.addArgument( "Protocol", 				tcp?"6":"17" );	// IANA protocol numbers
			add_inv.addArgument( "LeaseTime",				String.valueOf( LEASE_SECS));

			String	uid	= null;
			boolean	ok	= false;

			try{
				UPnPActionArgument[] result = add_inv.invoke( false );

				uid = result[0].getValue();
				
				ok	= true;

			}catch( UPnPException error ){

				// log( "Failed to add Pinhole '" + description + "': " + error.getMessage());
				
				throw( error );

			}finally{

				/*
				((UPnPRootDeviceImpl)service.getDevice().getRootDevice()).portMappingResult(ok);

				for (int i=0;i<listeners.size();i++){

					UPnPWANConnectionListener	listener = (UPnPWANConnectionListener)listeners.get(i);

					try{
						listener.mappingResult( this, ok );

					}catch( Throwable e){

						Debug.printStackTrace(e);
					}
				}
				*/
			}

			try{
				class_mon.enter();

				Iterator<Pinhole>	it = pinholes.iterator();

				while( it.hasNext()){

					Pinhole	m = it.next();

					if ( m.getExternalPort() == port && m.isTCP() == tcp ){

						it.remove();
					}
				}

				pinholes.add( new Pinhole( uid, port, tcp, "", description ));

			}finally{

				class_mon.exit();
			}
		}
	}
	
	@Override
	public void 
	updatePinhole(
		boolean 	tcp, 
		int 		port) 
				
		throws UPnPException
	{
		UPnPAction act = service.getAction( "UpdatePinhole" );

		if ( act == null ){

			log( "Action 'UpdatePinhole' not supported" );

		}else{

			Pinhole	pinhole_found = null;

			try{
				class_mon.enter();

				Iterator<Pinhole>	it = pinholes.iterator();

				while( it.hasNext()){

					Pinhole	pinhole = it.next();

					if ( 	pinhole.getExternalPort() == port &&
							pinhole.isTCP() == tcp ){

						pinhole_found	= pinhole;

						break;
					}
				}
			}finally{

				class_mon.exit();
			}

			if ( pinhole_found != null){
				
				try{
					long	start = SystemTime.getCurrentTime();
	
					UPnPActionInvocation inv = act.getInvocation();
	
					inv.addArgument( "UniqueID", 		"" + pinhole_found.getUID());	
					inv.addArgument( "NewLeaseTime",	String.valueOf( LEASE_SECS));
	
					inv.invoke( false );
	
					pinhole_found.updated();
					
					long	elapsed = SystemTime.getCurrentTime() - start;
	
					if ( elapsed > 4000 ){
	
						String	info = service.getDevice().getRootDevice().getInfo();
	
						((UPnPImpl)service.getDevice().getRootDevice().getUPnP()).logAlert(
								"UPnP device '" + info + "' is taking a long time to update pinholes, consider disabling this via the UPnP configuration.",
								false,
								UPnPLogListener.TYPE_ONCE_EVER );
					}
				}catch( UPnPException e ){
		
					throw( e );
				}
			}
		}	
	}
	
	@Override
	public void 
	removePinhole(
		boolean tcp, int port) 
				
		throws UPnPException
	{
		UPnPAction act = service.getAction( "DeletePinhole" );

		if ( act == null ){

			log( "Action 'DeletePinhole' not supported" );

		}else{

			Pinhole	pinhole_found = null;

			try{
				class_mon.enter();

				Iterator<Pinhole>	it = pinholes.iterator();

				while( it.hasNext()){

					Pinhole	pinhole = it.next();

					if ( 	pinhole.getExternalPort() == port &&
							pinhole.isTCP() == tcp ){

						it.remove();

						pinhole_found	= pinhole;

						break;
					}
				}
			}finally{

				class_mon.exit();
			}

			if ( pinhole_found != null){
				
				try{
					long	start = SystemTime.getCurrentTime();
	
					UPnPActionInvocation inv = act.getInvocation();
	
					inv.addArgument( "UniqueID", 				"" + pinhole_found.getUID());	
	
					inv.invoke( false );
	
					long	elapsed = SystemTime.getCurrentTime() - start;
	
					if ( elapsed > 4000 ){
	
						String	info = service.getDevice().getRootDevice().getInfo();
	
						((UPnPImpl)service.getDevice().getRootDevice().getUPnP()).logAlert(
								"UPnP device '" + info + "' is taking a long time to delete pinholes, consider disabling this via the UPnP configuration.",
								false,
								UPnPLogListener.TYPE_ONCE_EVER );
					}
				}catch( UPnPException e ){
		
					throw( e );
				}
			}
		}	
	}
	
	
	protected void
	checkMappings()

		throws UPnPException
	{
		List<Pinhole>	pinholes_copy;

		try{
			class_mon.enter();

			pinholes_copy = new ArrayList( pinholes );

		}finally{

			class_mon.exit();
		}

		for ( Pinhole pin: pinholes_copy ){
			
			long now = SystemTime.getMonotonousTime();
			
			long elapsed_secs = ( now - pin.getLastUpdate())/1000;
			
			if ( LEASE_SECS - elapsed_secs < 2*CHECK_SECS ){
				
				try{
					updatePinhole( pin.isTCP(), pin.getExternalPort());
					
				}catch( Throwable e ){
					
					log( "Failed to update pinhole: " + e.getMessage());
				}
			}
		}
	}
	
	protected void
	log(
		String	str )
	{
		service.getDevice().getRootDevice().getUPnP().log( str );
	}
	
	private static class
	Pinhole
		implements UPnPWANIPv6FirewallControlPinhole
	{
		private final String		uid;
		private final int			external_port;
		private final boolean		tcp;
		private final String		internal_host;
		private final String		description;

		private long		last_update = SystemTime.getMonotonousTime();
		
		protected
		Pinhole(
			String		_uid,
			int			_external_port,
			boolean		_tcp,
			String		_internal_host,
			String		_description )
		{
			uid				= _uid;
			external_port	= _external_port;
			tcp				= _tcp;
			internal_host	= _internal_host;
			description		= _description;
		}

		long
		getLastUpdate()
		{
			return( last_update );
		}
		
		void
		updated()
		{
			last_update = SystemTime.getMonotonousTime();
		}
		
		String
		getUID()
		{
			return( uid );
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
