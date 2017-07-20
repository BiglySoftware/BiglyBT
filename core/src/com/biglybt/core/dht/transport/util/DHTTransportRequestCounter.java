/*
 * Created on 21-Jan-2005
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

package com.biglybt.core.dht.transport.util;

import java.util.List;

import com.biglybt.core.dht.transport.*;

/**
 * @author parg
 *
 */

public class
DHTTransportRequestCounter
	implements DHTTransportRequestHandler
{
	private final DHTTransportRequestHandler		delegate;
	private final DHTTransportStatsImpl			stats;

	public
	DHTTransportRequestCounter(
		DHTTransportRequestHandler	_delegate,
		DHTTransportStatsImpl		_stats )
	{
		delegate	= _delegate;
		stats		= _stats;
	}

	@Override
	public void
	pingRequest(
		DHTTransportContact contact )
	{
		stats.pingReceived();

		delegate.pingRequest( contact );
	}

	@Override
	public void
	keyBlockRequest(
		DHTTransportContact contact,
		byte[]				key_block_request,
		byte[]				key_block_signature )
	{
		stats.keyBlockReceived();

		delegate.keyBlockRequest( contact, key_block_request, key_block_signature );
	}

	@Override
	public DHTTransportFullStats
	statsRequest(
		DHTTransportContact contact )
	{
		stats.statsReceived();

		return( delegate.statsRequest( contact ));
	}

	@Override
	public DHTTransportStoreReply
	storeRequest(
		DHTTransportContact 	contact,
		byte[][]				keys,
		DHTTransportValue[][]	value_sets )
	{
		stats.storeReceived();

		return( delegate.storeRequest( contact, keys, value_sets ));
	}

	@Override
	public DHTTransportQueryStoreReply
	queryStoreRequest(
		DHTTransportContact 	contact,
		int						header_len,
		List<Object[]>			keys )
	{
		stats.queryStoreReceived();

		return( delegate.queryStoreRequest( contact, header_len, keys ));
	}

	@Override
	public DHTTransportContact[]
	findNodeRequest(
		DHTTransportContact contact,
		byte[]				id )
	{
		stats.findNodeReceived();

		return( delegate.findNodeRequest( contact, id ));
	}

	@Override
	public DHTTransportFindValueReply
	findValueRequest(
		DHTTransportContact contact,
		byte[]				key,
		int					max,
		short				flags )
	{
		stats.findValueReceived();

		return( delegate.findValueRequest( contact, key, max, flags ));
	}

	@Override
	public void
	contactImported(
		DHTTransportContact		contact,
		boolean					is_bootstrap )
	{
		delegate.contactImported( contact, is_bootstrap );
	}

	@Override
	public void
	contactRemoved(
		DHTTransportContact	contact )
	{
		delegate.contactRemoved( contact );
	}

	@Override
	public int
	getTransportEstimatedDHTSize()
	{
		return( delegate.getTransportEstimatedDHTSize());
	}

	@Override
	public void
	setTransportEstimatedDHTSize(
		int	size )
	{
		delegate.setTransportEstimatedDHTSize(size);
	}
}
