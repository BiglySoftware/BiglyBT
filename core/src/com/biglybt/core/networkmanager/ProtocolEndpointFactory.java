/*
 * Created on Oct 14, 2010
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


package com.biglybt.core.networkmanager;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.networkmanager.impl.tcp.ProtocolEndpointTCP;
import com.biglybt.core.networkmanager.impl.udp.ProtocolEndpointUDP;

public class
ProtocolEndpointFactory
{
	private static ProtocolEndpointHandler tcp_handler = null;
	private static ProtocolEndpointHandler udp_handler = null;

	private static final Map<Integer,ProtocolEndpointHandler>	other_handlers = new HashMap<>();

	static{
		ProtocolEndpointTCP.register();
		ProtocolEndpointUDP.register();
	}

	public static void
	registerHandler(
		ProtocolEndpointHandler		handler )
	{
		int	type = handler.getType();

		if ( type == ProtocolEndpoint.PROTOCOL_TCP ){

			tcp_handler = handler;

		}else if ( type == ProtocolEndpoint.PROTOCOL_UDP ){

			udp_handler = handler;

		}else{

			other_handlers.put( type, handler );
		}
	}

	public static boolean
	isHandlerRegistered(
		int		type )
	{
		if ( type == ProtocolEndpoint.PROTOCOL_TCP || type == ProtocolEndpoint.PROTOCOL_UDP ){

			return( true );

		}else{

			return( other_handlers.containsKey( type ));
		}
	}

	public static ProtocolEndpoint
	createEndpoint(
		int						type,
		InetSocketAddress		target )
	{
		switch( type ){
			case ProtocolEndpoint.PROTOCOL_TCP:{
				return( tcp_handler.create( target ));
			}
			case ProtocolEndpoint.PROTOCOL_UDP:{
				return( udp_handler.create( target ));
			}
			default:{
				ProtocolEndpointHandler handler = other_handlers.get( type );
				if ( handler != null ){
					return( handler.create( target ));
				}
				return( null );
			}
		}
	}

	public static ProtocolEndpoint
	createEndpoint(
		int						type,
		ConnectionEndpoint		connection_endpoint,
		InetSocketAddress		target )
	{
		switch( type ){
			case ProtocolEndpoint.PROTOCOL_TCP:{
				return( tcp_handler.create( connection_endpoint, target ));
			}
			case ProtocolEndpoint.PROTOCOL_UDP:{
				return( udp_handler.create( connection_endpoint, target ));
			}
			default:{
				ProtocolEndpointHandler handler = other_handlers.get( type );
				if ( handler != null ){
					return( handler.create( connection_endpoint, target ));
				}
				return( null );
			}
		}
	}
}
