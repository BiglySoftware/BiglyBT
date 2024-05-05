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


import java.net.Inet6Address;
import java.net.InetAddress;

import com.biglybt.core.Core;
import com.biglybt.core.networkmanager.admin.NetworkAdminException;
import com.biglybt.core.networkmanager.admin.NetworkAdminNetworkInterfaceAddress;
import com.biglybt.core.networkmanager.admin.NetworkAdminProgressListener;
import com.biglybt.core.networkmanager.admin.NetworkAdminProtocol;
import com.biglybt.pif.PluginInterface;
import com.biglybt.plugin.upnp.UPnPMapping;
import com.biglybt.plugin.upnp.UPnPPlugin;

public class
NetworkAdminProtocolImpl
	implements NetworkAdminProtocol
{
	private final Core core;
	private final int				type;
	private final int				port;

	protected
	NetworkAdminProtocolImpl(
		Core _core,
		int			_type )
	{
		core		= _core;
		type		= _type;
		port		= -1;
	}

	protected
	NetworkAdminProtocolImpl(
		Core _core,
		int			_type,
		int			_port )
	{
		core		= _core;
		type		= _type;
		port		= _port;
	}

	@Override
	public int
	getType()
	{
		return( type );
	}

	@Override
	public int
	getPort()
	{
		return( port );
	}

	@Override
	public InetAddress
	test(
		NetworkAdminNetworkInterfaceAddress	address )

		throws NetworkAdminException
	{
		return( test( address, null ));
	}


	@Override
	public InetAddress
	test(
		NetworkAdminNetworkInterfaceAddress	address,
		NetworkAdminProgressListener		listener )

		throws NetworkAdminException
	{
		return( test( address, false, listener ));
	}
	
	@Override
	public InetAddress
	test(
		NetworkAdminNetworkInterfaceAddress		address,
		boolean									upnp_map,
		NetworkAdminProgressListener			listener )

		throws NetworkAdminException
	{
		boolean ipv6 = address != null && address.getAddress() instanceof Inet6Address;
		
		return( test( address, ipv6, upnp_map, listener ));
	}
	
	@Override
	public InetAddress
	test(
		NetworkAdminNetworkInterfaceAddress		address,
		boolean									ipv6,
		boolean									upnp_map,
		NetworkAdminProgressListener			listener )

		throws NetworkAdminException
	{
		InetAddress bind_ip = address==null?null:address.getAddress();

		NetworkAdminProtocolTester	tester;

		if ( type == PT_HTTP ){

			tester = new NetworkAdminHTTPTester( core, listener );

		}else if ( type == PT_TCP ){

			tester = new NetworkAdminTCPTester( core, listener );

		}else{

			tester = new NetworkAdminUDPTester( core, listener );
		}

		InetAddress	res;

		if ( port <= 0 ){

			res = tester.testOutbound( bind_ip, 0, ipv6 );

		}else{

			UPnPMapping new_mapping = null;

			if ( upnp_map ){

				PluginInterface pi_upnp = core.getPluginManager().getPluginInterfaceByClass( UPnPPlugin.class );

				if( pi_upnp != null ) {

					UPnPPlugin upnp = (UPnPPlugin)pi_upnp.getPlugin();

					UPnPMapping mapping = upnp.getMapping( type != PT_UDP , port );

					if ( mapping == null ) {

						new_mapping = mapping = upnp.addMapping( "NAT Tester", type != PT_UDP, port, true );

							// give UPnP a chance to work

						try{
							Thread.sleep( 500 );

						}catch( Throwable e ){

						}
					}
				}
			}

			try{
				res = tester.testInbound( bind_ip, port, ipv6 );

			}finally{

				if ( new_mapping != null ){

					new_mapping.destroy();
				}
			}
		}

		return( res );
	}

	@Override
	public String
	getTypeString()
	{
		String	res;

		if ( type == PT_HTTP ){

			res = "HTTP";

		}else if ( type == PT_TCP ){

			res = "TCP";

		}else{

			res = "UDP";
		}

		return( res );
	}

	@Override
	public String
	getName()
	{
		String	res = getTypeString();

		if ( port == -1 ){

			return( res + " outbound" );

		}else{

			return( res + " port " + port + " inbound" );
		}
	}
}
