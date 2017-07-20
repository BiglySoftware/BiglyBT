/*
 * Created on 12-Jun-2005
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

package com.biglybt.core.dht.transport.udp.impl.packethandler;

import com.biglybt.net.udp.uc.PRUDPPacketHandler;
import com.biglybt.net.udp.uc.PRUDPPacketHandlerStats;


public class
DHTUDPPacketHandlerStats
{
	private long	packets_sent;
	private long	packets_received;
	private long	bytes_sent;
	private long	bytes_received;
	private long	timeouts;

	private final PRUDPPacketHandlerStats		stats;

	protected
	DHTUDPPacketHandlerStats(
		PRUDPPacketHandler		_handler )
	{
		stats	= _handler.getStats();
	}

	protected
	DHTUDPPacketHandlerStats(
		DHTUDPPacketHandlerStats	_originator,
		PRUDPPacketHandlerStats		_stats )
	{
		packets_sent		= _originator.packets_sent;
		packets_received	= _originator.packets_received;
		bytes_sent			= _originator.bytes_sent;
		bytes_received		= _originator.bytes_received;
		timeouts			= _originator.timeouts;

		stats	= _stats;
	}

		// update

	protected void
	timeout()
	{
		timeouts++;
	}

	protected void
	packetSent(
		long		bytes )
	{
		packets_sent++;
		bytes_sent	+= bytes;
	}

	protected void
	packetReceived(
		long		bytes )
	{
		packets_received++;
		bytes_received	+= bytes;
	}
		// access

	public long
	getPacketsSent()
	{
		return( packets_sent );
	}

	public long
	getPacketsReceived()
	{
		return( packets_received );
	}

	public long
	getRequestsTimedOut()
	{
		return( timeouts );
	}

	public long
	getBytesSent()
	{
		return( bytes_sent );
	}

	public long
	getBytesReceived()
	{
		return( bytes_received );
	}

	public long
	getSendQueueLength()
	{
		return( stats.getSendQueueLength());
	}

	public long
	getReceiveQueueLength()
	{
		return( stats.getReceiveQueueLength());
	}

	public DHTUDPPacketHandlerStats
	snapshot()
	{
		return( new DHTUDPPacketHandlerStats( this, stats.snapshot()));
	}
}
