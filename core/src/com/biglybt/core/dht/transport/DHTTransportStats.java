/*
 * Created on 18-Jan-2005
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

package com.biglybt.core.dht.transport;


/**
 * @author parg
 *
 */

public interface
DHTTransportStats
{
	public static final int	STAT_SENT		= 0;
	public static final int	STAT_OK			= 1;
	public static final int	STAT_FAILED		= 2;
	public static final int	STAT_RECEIVED	= 3;

		/**
		 * returns pings sent, pings succeeded, pings failed, pings received
		 * @return
		 */

	public long[]
	getPings();

	public long[]
	getFindNodes();

	public long[]
	getFindValues();

	public long[]
	getStores();

	public long[]
	getQueryStores();

	public long[]
	getData();

	public long[]
	getKeyBlocks();

		// aliens are indexed by these constants

	public static final int AT_FIND_NODE		= 0;
	public static final int AT_FIND_VALUE		= 1;
	public static final int AT_PING				= 2;
	public static final int AT_STATS			= 3;
	public static final int AT_STORE			= 4;
	public static final int AT_KEY_BLOCK		= 5;
	public static final int AT_QUERY_STORE		= 6;

	public long[]
	getAliens();

	public long
	getIncomingRequests();

	public long
	getPacketsSent();

	public long
	getPacketsReceived();

	public long
	getRequestsTimedOut();

	public long
	getBytesSent();

	public long
	getBytesReceived();

	public DHTTransportStats
	snapshot();

	public long
	getSkewAverage();

		/**
		 * -1 if stats not yet available
		 * @return
		 */

	public int
	getRouteablePercentage();

	public int[]
	getRTTHistory();

	public String
	getString();
}
