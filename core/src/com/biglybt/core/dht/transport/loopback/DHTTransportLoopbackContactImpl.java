/*
 * Created on 12-Jan-2005
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

package com.biglybt.core.dht.transport.loopback;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import com.biglybt.core.dht.impl.DHTLog;
import com.biglybt.core.dht.netcoords.DHTNetworkPosition;
import com.biglybt.core.dht.transport.*;

/**
 * @author parg
 *
 */

public class
DHTTransportLoopbackContactImpl
	implements DHTTransportContact
{
	private final DHTTransportLoopbackImpl	transport;

	private final byte[]		id;
	private int			random_id;

	protected
	DHTTransportLoopbackContactImpl(
		DHTTransportLoopbackImpl	_transport,
		byte[]						_id )
	{
		transport	= _transport;
		id			= _id;
	}

	@Override
	public DHTTransport
	getTransport()
	{
		return( transport );
	}

	@Override
	public int
	getInstanceID()
	{
		return( 0 );
	}

	@Override
	public byte
	getProtocolVersion()
	{
		return( 0 );
	}
	@Override
	public long
	getClockSkew()
	{
		return( 0 );
	}

	@Override
	public int
	getRandomIDType()
	{
		return( RANDOM_ID_TYPE1 );
	}

	@Override
	public int
	getRandomID()
	{
		return( random_id );
	}

	@Override
	public void
	setRandomID(
		int	_random_id )
	{
		random_id	= _random_id;
	}

	@Override
	public void
	setRandomID2(
		byte[]		id )
	{
	}

	@Override
	public byte[]
	getRandomID2()
	{
		return( null );
	}

	@Override
	public boolean
	isValid()
	{
		return( true );
	}

	@Override
	public boolean
	isSleeping()
	{
		return( false );
	}

	@Override
	public int
	getMaxFailForLiveCount()
	{
		return( 5 );
	}

	@Override
	public int
	getMaxFailForUnknownCount()
	{
		return( 3 );
	}

	@Override
	public String
	getName()
	{
		return( "" );
	}

	@Override
	public byte[]
	getBloomKey()
	{
		return( null );
	}


	@Override
	public InetSocketAddress
	getAddress()
	{
		return( null );
	}

	@Override
	public InetSocketAddress
	getTransportAddress()
	{
		return( null );
	}

	@Override
	public InetSocketAddress
	getExternalAddress()
	{
		return null;
	}

	@Override
	public boolean
	isAlive(
		long		timeout )
	{
		return( true );
	}

	@Override
	public void
	isAlive(
		DHTTransportReplyHandler 	handler,
		long 						timeout )
	{
		transport.sendPing( this, handler );
	}

	@Override
	public void
	sendPing(
		DHTTransportReplyHandler	handler )
	{
		transport.sendPing( this, handler );
	}

	@Override
	public void
	sendImmediatePing(
		DHTTransportReplyHandler	handler,
		long						timeout )
	{
		transport.sendPing( this, handler );
	}

	@Override
	public void
	sendKeyBlock(
		DHTTransportReplyHandler	handler,
		byte[]						request,
		byte[]						signature )
	{
		transport.sendKeyBlock( this, handler, request, signature );
	}

	@Override
	public void
	sendStats(
		DHTTransportReplyHandler	handler )
	{
		transport.sendStats( this, handler );
	}

	@Override
	public void
	sendStore(
		DHTTransportReplyHandler	handler,
		byte[][]					keys,
		DHTTransportValue[][]		value_sets,
		boolean						immediate )
	{
		transport.sendStore( this, handler, keys, value_sets, false );
	}

	@Override
	public void
	sendQueryStore(
		DHTTransportReplyHandler 	handler,
		int							header_length,
		List<Object[]>			 	key_details )
	{
		transport.sendQueryStore( this, handler, header_length, key_details);
	}

	@Override
	public void
	sendFindNode(
		DHTTransportReplyHandler	handler,
		byte[]						nid,
		short						flags )
	{
		transport.sendFindNode( this, handler, nid );
	}

	@Override
	public void
	sendFindValue(
		DHTTransportReplyHandler	handler,
		byte[]						key,
		int							max,
		short						flags )
	{
		transport.sendFindValue( this, handler, key, max, flags );
	}

	@Override
	public DHTTransportFullStats
	getStats()
	{
		return( null );
	}

	@Override
	public byte[]
	getID()
	{
		return( id );
	}

	@Override
	public void
	exportContact(
		DataOutputStream	os )

		throws IOException
	{
		transport.exportContact( this, os );
	}

	@Override
	public Map<String, Object>
	exportContactToMap()
	{
		return( transport.exportContactToMap( this ));
	}

	@Override
	public void
	remove()
	{
		transport.removeContact( this );
	}

	@Override
	public void
	createNetworkPositions(
		boolean is_local)
	{
	}

	@Override
	public DHTNetworkPosition[]
	getNetworkPositions()
	{
		return( new DHTNetworkPosition[0] );
	}

	@Override
	public DHTNetworkPosition
  	getNetworkPosition(
  		byte		type )
  	{
  		return( null );
  	}

	@Override
	public String
	getString()
	{
		return( DHTLog.getString( this ));
	}
}
