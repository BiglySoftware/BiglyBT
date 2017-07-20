/*
 * Created on 01-Feb-2005
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

import com.biglybt.core.dht.transport.DHTTransportStats;
import com.biglybt.core.dht.transport.util.DHTTransportStatsImpl;

/**
 * @author parg
 *
 */

public class
DHTTransportLoopbackStatsImpl
	extends DHTTransportStatsImpl
{
	protected
	DHTTransportLoopbackStatsImpl(
		byte		pv )
	{
		super( pv );
	}

	@Override
	public long
	getPacketsSent()
	{
		return( 0 );
	}

	@Override
	public long
	getPacketsReceived()
	{
		return( 0 );
	}

	@Override
	public long
	getRequestsTimedOut()
	{
		return( 0 );
	}

	@Override
	public long
	getBytesSent()
	{
		return( 0 );
	}

	@Override
	public long
	getBytesReceived()
	{
		return( 0 );
	}

	@Override
	public int
	getRouteablePercentage()
	{
		return -1;
	}

	@Override
	public DHTTransportStats
	snapshot()
	{
		DHTTransportStatsImpl	res = new DHTTransportLoopbackStatsImpl(getProtocolVersion());

		snapshotSupport( res );

		return( res );
	}
}
