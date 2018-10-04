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

package com.biglybt.core.dht.transport.udp.impl;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.biglybt.core.dht.transport.udp.DHTTransportUDP;
import com.biglybt.core.dht.transport.udp.impl.packethandler.DHTUDPPacketNetworkHandler;


/**
 * @author parg
 *
 */

public class
DHTUDPPacketRequestStats
	extends DHTUDPPacketRequest
{
	public static final int	STATS_TYPE_ORIGINAL			= 1;
	public static final int	STATS_TYPE_NP_VER2			= 2;

	private int	stats_type	= STATS_TYPE_ORIGINAL;

	public
	DHTUDPPacketRequestStats(
		DHTTransportUDPImpl				_transport,
		long							_connection_id,
		DHTTransportUDPContactImpl		_local_contact,
		DHTTransportUDPContactImpl		_remote_contact )
	{
		super( _transport, DHTUDPPacketHelper.ACT_REQUEST_STATS, _connection_id, _local_contact, _remote_contact );
	}

	protected
	DHTUDPPacketRequestStats(
		DHTUDPPacketNetworkHandler		network_handler,
		DataInputStream					is,
		long							con_id,
		int								trans_id )

		throws IOException
	{
		super( network_handler, is,  DHTUDPPacketHelper.ACT_REQUEST_STATS, con_id, trans_id );

		if ( getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_GENERIC_NETPOS ){

			stats_type	= is.readInt();
		}
	}

	@Override
	public void
	serialise(
		DataOutputStream	os )

		throws IOException
	{
		super.serialise(os);

		if ( getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_GENERIC_NETPOS ){

			os.writeInt( stats_type );
		}
	}

	public void
	setStatsType(
		int		_type )
	{
		stats_type	= _type;
	}

	public int
	getStatsType()
	{
		return( stats_type );
	}

	@Override
	public String
	getString()
	{
		return( super.getString());
	}
}