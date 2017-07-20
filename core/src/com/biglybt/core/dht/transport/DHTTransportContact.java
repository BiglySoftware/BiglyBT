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

package com.biglybt.core.dht.transport;

/**
 * @author parg
 *
 */

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import com.biglybt.core.dht.netcoords.DHTNetworkPosition;

public interface
DHTTransportContact
{
	public static final int RANDOM_ID_TYPE1	= 1;
	public static final int RANDOM_ID_TYPE2	= 2;

	public int
	getMaxFailForLiveCount();

	public int
	getMaxFailForUnknownCount();

	public int
	getInstanceID();

	public byte[]
	getID();

	public byte
	getProtocolVersion();

	public long
	getClockSkew();

	public int
	getRandomIDType();

	public void
	setRandomID(
		int	id );

	public int
	getRandomID();

	public void
	setRandomID2(
		byte[]		id );

	public byte[]
	getRandomID2();

	public String
	getName();

	public byte[]
	getBloomKey();

	public InetSocketAddress
	getAddress();

	public InetSocketAddress
	getTransportAddress();

	public InetSocketAddress
	getExternalAddress();

	public boolean
	isAlive(
		long		timeout );

	public void
	isAlive(
		DHTTransportReplyHandler	handler,
		long						timeout );

	public boolean
	isValid();

	public boolean
	isSleeping();

	public void
	sendPing(
		DHTTransportReplyHandler	handler );

	public void
	sendImmediatePing(
		DHTTransportReplyHandler	handler,
		long						timeout );

	public void
	sendStats(
		DHTTransportReplyHandler	handler );

	public void
	sendStore(
		DHTTransportReplyHandler	handler,
		byte[][]					keys,
		DHTTransportValue[][]		value_sets,
		boolean						immediate );

	public void
	sendQueryStore(
		DHTTransportReplyHandler	handler,
		int							header_length,
		List<Object[]>				key_details );

	public void
	sendFindNode(
		DHTTransportReplyHandler	handler,
		byte[]						id,
		short						flags );

	public void
	sendFindValue(
		DHTTransportReplyHandler	handler,
		byte[]						key,
		int							max_values,
		short						flags );

	public void
	sendKeyBlock(
		DHTTransportReplyHandler	handler,
		byte[]						key_block_request,
		byte[]						key_block_signature );

	public DHTTransportFullStats
	getStats();

	public void
	exportContact(
		DataOutputStream	os )

		throws IOException, DHTTransportException;

	public Map<String, Object>
	exportContactToMap();

	public void
	remove();

	public void
	createNetworkPositions(
		boolean		is_local );

	public DHTNetworkPosition[]
	getNetworkPositions();

	public DHTNetworkPosition
	getNetworkPosition(
		byte	position_type );

	public DHTTransport
	getTransport();

	public String
	getString();
}
