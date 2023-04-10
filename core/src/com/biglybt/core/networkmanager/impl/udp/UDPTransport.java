/*
 * Created on 22 Jun 2006
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

package com.biglybt.core.networkmanager.impl.udp;

import java.nio.ByteBuffer;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.networkmanager.TransportEndpoint;
import com.biglybt.core.networkmanager.TransportStartpoint;
import com.biglybt.core.networkmanager.impl.TransportHelperFilter;
import com.biglybt.core.networkmanager.impl.TransportImpl;

public class
UDPTransport
	extends TransportImpl
{
	private static final LogIDs LOGID = LogIDs.NET;

	private final ProtocolEndpointUDP		endpoint;
	private byte[][]				shared_secrets;

	private int transport_mode = TRANSPORT_MODE_NORMAL;

	private volatile boolean	closed;

	protected
	UDPTransport(
		ProtocolEndpointUDP		_endpoint,
		byte[][]				_shared_secrets )
	{
		endpoint		= _endpoint;
		shared_secrets	= _shared_secrets;
	}

	protected
	UDPTransport(
		ProtocolEndpointUDP		_endpoint,
		TransportHelperFilter	_filter )
	{
		endpoint		= _endpoint;

		setFilter( _filter );
	}

	@Override
	public boolean
	isTCP()
	{
		return( false );
	}

	@Override
	public boolean
	isSOCKS()
	{
		return( false );
	}

	@Override
	public String
	getProtocol()
	{
		return "UDP";
	}

	@Override
	public TransportEndpoint
	getTransportEndpoint()
	{
		return( new TransportEndpointUDP( endpoint ));
	}

	@Override
	public TransportStartpoint
	getTransportStartpoint()
	{
		return( null );
	}

	@Override
	public int
	getMssSize()
	{
	  return( UDPNetworkManager.getUdpMssSize());
	}

	@Override
	public String
	getDescription()
	{
		return( endpoint.getAddress().toString());
	}

	@Override
	public void
	setTransportMode(
		int mode )
	{
		transport_mode	= mode;
	}

	@Override
	public int
	getTransportMode()
	{
		return( transport_mode );
	}

	@Override
	public void
	connectOutbound(
		ByteBuffer				initial_data,
		final ConnectListener 	listener,
		int						priority )
	{
		if ( !UDPNetworkManager.UDP_OUTGOING_ENABLED ){

			listener.connectFailure( this, new Throwable( "Outbound UDP connections disabled" ));

			return;
		}

		if ( closed ){

			listener.connectFailure( this, new Throwable( "Connection already closed" ));

			return;
		}

		if( getFilter() != null ){

			listener.connectFailure( this, new Throwable( "Already connected" ));

			return;
		}

		if ( COConfigurationManager.getBooleanParameter( "Proxy.Data.Enable" )){

			listener.connectFailure( this, new Throwable( "UDP proxy connection not supported" ));

			return;
		}

		UDPConnectionManager	con_man = UDPNetworkManager.getSingleton().getConnectionManager();

		con_man.connectOutbound( this, endpoint.getAddress(), shared_secrets, initial_data, listener);
	}

	@Override
	public void
	close(
		String	reason )
	{
		closed	= true;

		readyForRead( false );
		readyForWrite( false );

		TransportHelperFilter	filter = getFilter();

		if ( filter != null ){

			filter.getHelper().close( reason );

			setFilter( null );
		}
	}

	public boolean
	isClosed()
	{
		return( closed );
	}
}
