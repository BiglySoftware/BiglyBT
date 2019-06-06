/*
 * Created on 31-Jan-2005
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

package com.biglybt.core.dht.control.impl;

import com.biglybt.core.dht.control.DHTControlStats;
import com.biglybt.core.dht.db.DHTDBStats;
import com.biglybt.core.dht.router.DHTRouterStats;
import com.biglybt.core.dht.transport.DHTTransport;
import com.biglybt.core.dht.transport.DHTTransportFullStats;
import com.biglybt.core.dht.transport.DHTTransportStats;
import com.biglybt.core.util.*;

/**
 * @author parg
 *
 */

public class
DHTControlStatsImpl
	implements DHTTransportFullStats, DHTControlStats
{
	private static final int	UPDATE_INTERVAL	= 10*1000;
	private static final int	UPDATE_PERIOD	= 120;

	final DHTControlImpl		control;


	private final Average	packets_in_average 		= Average.getInstance(UPDATE_INTERVAL, UPDATE_PERIOD );
	private final Average	packets_out_average 	= Average.getInstance(UPDATE_INTERVAL, UPDATE_PERIOD );
	private final Average	bytes_in_average 		= Average.getInstance(UPDATE_INTERVAL, UPDATE_PERIOD );
	private final Average	bytes_out_average 		= Average.getInstance(UPDATE_INTERVAL, UPDATE_PERIOD );

	private DHTTransportStats	transport_snapshot;
	private long[]				router_snapshot;
	private int[]				value_details_snapshot;

	protected
	DHTControlStatsImpl(
		DHTControlImpl		_control )
	{
		control	= _control;

		transport_snapshot	= control.getTransport().getStats().snapshot();

		router_snapshot		= control.getRouter().getStats().getStats();

		SimpleTimer.addPeriodicEvent(
			"DHTCS:update",
			UPDATE_INTERVAL,
			new TimerEventPerformer()
			{
				@Override
				public void
				perform(
					TimerEvent	event )
				{
					update();

					control.poke();
				}
			});
	}

	protected void
	update()
	{
		DHTTransport	transport 	= control.getTransport();

		DHTTransportStats	t_stats = transport.getStats().snapshot();

		packets_in_average.addValue(
				t_stats.getPacketsReceived() - transport_snapshot.getPacketsReceived());

		packets_out_average.addValue(
				t_stats.getPacketsSent() - transport_snapshot.getPacketsSent());

		bytes_in_average.addValue(
				t_stats.getBytesReceived() - transport_snapshot.getBytesReceived());

		bytes_out_average.addValue(
				t_stats.getBytesSent() - transport_snapshot.getBytesSent());

		transport_snapshot	= t_stats;

		router_snapshot	= control.getRouter().getStats().getStats();

		value_details_snapshot = null;
	}

	@Override
	public long
	getTotalBytesReceived()
	{
		return( transport_snapshot.getBytesReceived());
	}

	@Override
	public long
	getTotalBytesSent()
	{
		return( transport_snapshot.getBytesSent());
	}

	@Override
	public long
	getTotalPacketsReceived()
	{
		return( transport_snapshot.getPacketsReceived());
	}

	@Override
	public long
	getTotalPacketsSent()
	{
		return( transport_snapshot.getPacketsSent());

	}

	@Override
	public long
	getTotalPingsReceived()
	{
		return( transport_snapshot.getPings()[DHTTransportStats.STAT_RECEIVED]);
	}
	@Override
	public long
	getTotalFindNodesReceived()
	{
		return( transport_snapshot.getFindNodes()[DHTTransportStats.STAT_RECEIVED]);
	}
	@Override
	public long
	getTotalFindValuesReceived()
	{
		return( transport_snapshot.getFindValues()[DHTTransportStats.STAT_RECEIVED]);
	}
	@Override
	public long
	getTotalStoresReceived()
	{
		return( transport_snapshot.getStores()[DHTTransportStats.STAT_RECEIVED]);
	}
	@Override
	public long
	getTotalKeyBlocksReceived()
	{
		return( transport_snapshot.getKeyBlocks()[DHTTransportStats.STAT_RECEIVED]);
	}

		// averages

	@Override
	public long
	getAverageBytesReceived()
	{
		return( bytes_in_average.getAverage());
	}

	@Override
	public long
	getAverageBytesSent()
	{
		return( bytes_out_average.getAverage());
	}

	@Override
	public long
	getAveragePacketsReceived()
	{
		return( packets_in_average.getAverage());
	}

	@Override
	public long
	getAveragePacketsSent()
	{
		return( packets_out_average.getAverage());
	}

	@Override
	public long
	getIncomingRequests()
	{
		return( transport_snapshot.getIncomingRequests());
	}
		// DB

	protected int[]
	getValueDetails()
	{
		int[] vd = value_details_snapshot;

		if ( vd == null ){

			vd = control.getDataBase().getStats().getValueDetails();

			value_details_snapshot = vd;
		}

		return( vd );
	}

	@Override
	public long
	getDBValuesStored()
	{
		int[]	vd = getValueDetails();

		return( vd[ DHTDBStats.VD_VALUE_COUNT ]);
	}

	@Override
	public long
	getDBKeyCount()
	{
		return( control.getDataBase().getStats().getKeyCount());
	}

	@Override
	public long
	getDBValueCount()
	{
		return( control.getDataBase().getStats().getValueCount());
	}

	@Override
	public long
	getDBKeysBlocked()
	{
		return( control.getDataBase().getStats().getKeyBlockCount());
	}

	@Override
	public long
	getDBKeyDivSizeCount()
	{
		int[]	vd = getValueDetails();

		return( vd[ DHTDBStats.VD_DIV_SIZE ]);
	}

	@Override
	public long
	getDBKeyDivFreqCount()
	{
		int[]	vd = getValueDetails();

		return( vd[ DHTDBStats.VD_DIV_FREQ ]);
	}

	@Override
	public long
	getDBStoreSize()
	{
		return( control.getDataBase().getStats().getSize());
	}

		// Router

	@Override
	public long
	getRouterNodes()
	{
		return( router_snapshot[DHTRouterStats.ST_NODES]);
	}

	@Override
	public long
	getRouterLeaves()
	{
		return( router_snapshot[DHTRouterStats.ST_LEAVES]);
	}

	@Override
	public long
	getRouterContacts()
	{
		return( router_snapshot[DHTRouterStats.ST_CONTACTS]);
	}

	@Override
	public long
	getRouterUptime()
	{
		return( control.getRouterUptime());
	}

	@Override
	public int
	getRouterCount()
	{
		return( control.getRouterCount());
	}

	@Override
	public String
	getVersion()
	{
		return( Constants.BIGLYBT_VERSION );
	}

	@Override
	public long
	getEstimatedDHTSize()
	{
		return( control.getEstimatedDHTSize());
	}

	@Override
	public String
	getString()
	{
		return(	"transport:" +
				getTotalBytesReceived() + "," +
				getTotalBytesSent() + "," +
				getTotalPacketsReceived() + "," +
				getTotalPacketsSent() + "," +
				getTotalPingsReceived() + "," +
				getTotalFindNodesReceived() + "," +
				getTotalFindValuesReceived() + "," +
				getTotalStoresReceived() + "," +
				getTotalKeyBlocksReceived() + "," +
				getAverageBytesReceived() + "," +
				getAverageBytesSent() + "," +
				getAveragePacketsReceived() + "," +
				getAveragePacketsSent() + "," +
				getIncomingRequests() +
				",router:" +
				getRouterNodes() + "," +
				getRouterLeaves() + "," +
				getRouterContacts() +
				",database:" +
				getDBKeyCount() + ","+
				getDBValueCount() + ","+
				getDBValuesStored() + ","+
				getDBStoreSize() + ","+
				getDBKeyDivFreqCount() + ","+
				getDBKeyDivSizeCount() + ","+
				getDBKeysBlocked()+
				",version:" + getVersion()+","+
				getRouterUptime() + ","+
				getRouterCount());
	}
}
