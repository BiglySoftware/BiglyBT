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

import java.util.Iterator;

import com.biglybt.core.util.Debug;
import com.biglybt.net.upnp.UPnPAction;
import com.biglybt.net.upnp.UPnPActionArgument;
import com.biglybt.net.upnp.UPnPActionInvocation;
import com.biglybt.net.upnp.UPnPException;
import com.biglybt.net.upnp.UPnPService;
import com.biglybt.net.upnp.impl.device.UPnPRootDeviceImpl;
import com.biglybt.net.upnp.services.UPnPWANConnectionListener;
import com.biglybt.net.upnp.services.UPnPWANIPv6FirewallControl;

public class 
UPnPSSWANIPv6FirewallControlImpl
	implements UPnPWANIPv6FirewallControl
{
	private static final int		LEASE_SECS	= 3600;
	private final UPnPServiceImpl	service;
	
	protected
	UPnPSSWANIPv6FirewallControlImpl(
		UPnPServiceImpl		_service )
	{
		service = _service;
	}
	
	@Override
	public UPnPService 
	getGenericService()
	{
		return( service );
	}
	
	/*
	@Override
	public void
	addPinhole(
		boolean		tcp,			// false -> UDP
		int			port,
		String		description )

		throws UPnPException
	{
		UPnPAction act = service.getAction( "AddPinhole" );

		if ( act == null ){

			log( "Action 'AddPinhole' not supported, binding not established" );

		}else{
			
			UPnPActionInvocation add_inv = act.getInvocation();

			add_inv.addArgument( "RemoteHost", 				"" );		// "" = wildcard for hosts, 0 = wildcard for ports
			add_inv.addArgument( "RemotePort", 				"" + port );
			add_inv.addArgument( "InternalClient",			service.getDevice().getRootDevice().getLocalAddress().getHostAddress());
			add_inv.addArgument( "InternalPort", 			"" + port );
			add_inv.addArgument( "Protocol", 				tcp?"6":"17" );	// IANA protocol numbers
			add_inv.addArgument( "LeaseTime",				String.valueOf( LEASE_SECS));		// 0 -> infinite (?)

			String	uid	= null;
			boolean	ok	= false;

			try{
				UPnPActionArgument[] result = add_inv.invoke();

				uid = result[0].getValue();
				
				ok	= true;

			}catch( UPnPException error ){

				throw( error );

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
	*/
}
