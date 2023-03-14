/*
 * Created on 16 Jun 2006
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

package com.biglybt.core.networkmanager.impl.tcp;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SocketChannel;

import com.biglybt.core.networkmanager.ProtocolEndpoint;
import com.biglybt.core.networkmanager.ProtocolStartpoint;
import com.biglybt.core.networkmanager.TransportStartpoint;
import com.biglybt.core.proxy.AEProxyAddressMapper;
import com.biglybt.core.proxy.AEProxyFactory;

public class
TransportStartpointTCP
	implements TransportStartpoint, ProtocolStartpoint
{
	private static final AEProxyAddressMapper proxy_address_mapper = AEProxyFactory.getAddressMapper();

	private final TCPTransportImpl			transport;
	private final TransportEndpointTCP		ep;

	public
	TransportStartpointTCP(
		TCPTransportImpl		_transport,
		TransportEndpointTCP	_ep )
	{
		transport	= _transport;
		ep			= _ep;
	}

	@Override
	public ProtocolStartpoint
	getProtocolStartpoint()
	{
		return( this );
	}

	@Override
	public int
	getType()
	{
		return( ProtocolEndpoint.PROTOCOL_TCP );
	}

	@Override
	public InetSocketAddress
	getAddress()
	{
		SocketChannel channel = ep.getSocketChannel();

		if ( channel != null ){

			Socket socket = channel.socket();

			if ( socket != null ){

				return((InetSocketAddress)socket.getLocalSocketAddress());
			}
		}

		return( null );
	}

	@Override
	public InetSocketAddress
	getNotionalAddress()
	{
		SocketChannel channel = ep.getSocketChannel();

		if ( channel != null ){

			Socket socket = channel.socket();

			if ( socket != null ){
				
			    AEProxyAddressMapper.AppliedPortMapping applied_mapping = proxy_address_mapper.applyPortMapping( socket.getInetAddress(), socket.getPort());

			    InetSocketAddress	local = applied_mapping.getLocalAddress();
			    
			    if ( local != null ){
			    
			    	return( local );
			    }

				if ( !transport.isIncoming()){
					
					InetSocketAddress target = ep.getProtocolEndpoint().getAddress();
					
					local = proxy_address_mapper.getLocalAddress( target );
					
					if ( local != null ){
						
						return( local );
					}
				}
				
			    return((InetSocketAddress)socket.getLocalSocketAddress());
			}
		}

		return( null );
	}
	
	@Override
	public String
	getDescription()
	{
		InetSocketAddress address = getAddress();

		if ( address == null ){

			return( "not connected" );
		}else{

			return( address.toString());
		}
	}
}
