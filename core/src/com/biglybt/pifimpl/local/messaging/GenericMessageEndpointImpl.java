/*
 * Created on 19 Jun 2006
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

package com.biglybt.pifimpl.local.messaging;

import java.net.InetSocketAddress;

import com.biglybt.core.networkmanager.ConnectionEndpoint;
import com.biglybt.core.networkmanager.ProtocolEndpoint;
import com.biglybt.core.networkmanager.ProtocolEndpointFactory;
import com.biglybt.core.networkmanager.impl.tcp.ProtocolEndpointTCP;
import com.biglybt.core.networkmanager.impl.udp.ProtocolEndpointUDP;
import com.biglybt.pif.messaging.generic.GenericMessageEndpoint;

public class
GenericMessageEndpointImpl
	implements GenericMessageEndpoint
{
	private ConnectionEndpoint		ce;

	public
	GenericMessageEndpointImpl(
		ConnectionEndpoint		_ce )
	{
		ce		= _ce;
	}

	public
	GenericMessageEndpointImpl(
		InetSocketAddress		_ne )
	{
		ce		= new ConnectionEndpoint( _ne );
	}

	@Override
	public InetSocketAddress
	getNotionalAddress()
	{
		return( ce.getNotionalAddress());
	}

	protected ConnectionEndpoint
	getConnectionEndpoint()
	{
		return( ce );
	}

	@Override
	public void
	addTCP(
		InetSocketAddress	target )
	{
		ce.addProtocol( ProtocolEndpointFactory.createEndpoint( ProtocolEndpoint.PROTOCOL_TCP, target ));
	}

	@Override
	public InetSocketAddress
	getTCP()
	{
		ProtocolEndpoint[]	pes = ce.getProtocols();

		for (int i=0;i<pes.length;i++){

			if ( pes[i] instanceof ProtocolEndpointTCP ){

				return( ((ProtocolEndpointTCP)pes[i]).getAddress());
			}
		}

		return( null );
	}

	@Override
	public void
	addUDP(
		InetSocketAddress	target )
	{
		ce.addProtocol( ProtocolEndpointFactory.createEndpoint( ProtocolEndpoint.PROTOCOL_UDP, target ));
	}

	@Override
	public InetSocketAddress
	getUDP()
	{
		ProtocolEndpoint[]	pes = ce.getProtocols();

		for (int i=0;i<pes.length;i++){

			if ( pes[i] instanceof ProtocolEndpointUDP ){

				return( ((ProtocolEndpointUDP)pes[i]).getAddress());
			}
		}

		return( null );
	}
}
