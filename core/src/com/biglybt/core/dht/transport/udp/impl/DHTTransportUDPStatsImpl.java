/*
 * Created on 25-Jan-2005
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

package com.biglybt.core.dht.transport.udp.impl;

import com.biglybt.core.dht.transport.DHTTransportStats;
import com.biglybt.core.dht.transport.udp.impl.packethandler.DHTUDPPacketHandlerStats;
import com.biglybt.core.dht.transport.util.DHTTransportStatsImpl;


/**
 * @author parg
 *
 */

public class
DHTTransportUDPStatsImpl
	extends 	DHTTransportStatsImpl
{
	private final DHTTransportUDPImpl				transport;
	private DHTUDPPacketHandlerStats		stats;

	protected
	DHTTransportUDPStatsImpl(
		DHTTransportUDPImpl				_transport,
		byte							_pv,
		DHTUDPPacketHandlerStats		_stats )
	{
		super( _pv );

		transport	= _transport;
		stats		= _stats;
	}

	protected void
	setStats(
		DHTUDPPacketHandlerStats	_stats )
	{
		stats = _stats;
	}

	@Override
	public long
	getPacketsSent()
	{
		return( stats.getPacketsSent());
	}

	@Override
	public long
	getPacketsReceived()
	{
		return( stats.getPacketsReceived());
	}

	@Override
	public long
	getRequestsTimedOut()
	{
		return( stats.getRequestsTimedOut());
	}

	@Override
	public long
	getBytesSent()
	{
		return( stats.getBytesSent());
	}

	@Override
	public long
	getBytesReceived()
	{
		return( stats.getBytesReceived());
	}

	@Override
	public int
	getRouteablePercentage()
	{
		return( transport.getRouteablePercentage());
	}

	@Override
	public DHTTransportStats
	snapshot()
	{
		DHTTransportStatsImpl	res = new DHTTransportUDPStatsImpl( transport, getProtocolVersion(), stats.snapshot());

		snapshotSupport( res );

		return( res );
	}

	@Override
	public String
	getString()
	{
		return( super.getString() + "," +
				"packsent:" + getPacketsSent() + "," +
				"packrecv:" + getPacketsReceived() + "," +
				"bytesent:" + getBytesSent() + "," +
				"byterecv:" + getBytesReceived() + "," +
				"timeout:" + getRequestsTimedOut() + "," +
				"sendq:" + stats.getSendQueueLength() + "," +
				"recvq:" + stats.getReceiveQueueLength());
	}
}
