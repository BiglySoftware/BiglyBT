/*
 * File    : PRUDPPacketReplyConnect.java
 * Created : 20-Jan-2004
 * By      : parg
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.core.dht.transport.udp.impl;

/**
 * @author parg
 *
 */

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;

import com.biglybt.core.dht.transport.DHTTransportContact;
import com.biglybt.core.dht.transport.DHTTransportFullStats;
import com.biglybt.core.dht.transport.udp.DHTTransportUDP;
import com.biglybt.core.dht.transport.udp.impl.packethandler.DHTUDPPacketNetworkHandler;

public class
DHTUDPPacketReplyStats
	extends DHTUDPPacketReply
{
	private int							stats_type = DHTUDPPacketRequestStats.STATS_TYPE_ORIGINAL;

	private DHTTransportFullStats		original_stats;
	private byte[]						new_stats;

	public
	DHTUDPPacketReplyStats(
		DHTTransportUDPImpl			transport,
		DHTUDPPacketRequestStats	request,
		DHTTransportContact			local_contact,
		DHTTransportContact			remote_contact )
	{
		super( transport, DHTUDPPacketHelper.ACT_REPLY_STATS, request, local_contact, remote_contact );
	}

	protected
	DHTUDPPacketReplyStats(
		DHTUDPPacketNetworkHandler		network_handler,
		InetSocketAddress				originator,
		DataInputStream					is,
		int								trans_id )

		throws IOException
	{
		super( network_handler, originator, is, DHTUDPPacketHelper.ACT_REPLY_STATS, trans_id );

		if ( getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_GENERIC_NETPOS ){

			stats_type = is.readInt();

			if ( stats_type == DHTUDPPacketRequestStats.STATS_TYPE_ORIGINAL ){

				original_stats = DHTUDPUtils.deserialiseStats( getProtocolVersion(), is );

			}else{

				new_stats = DHTUDPUtils.deserialiseByteArray( is, 65535 );
			}
		}else{

			original_stats = DHTUDPUtils.deserialiseStats( getProtocolVersion(), is );
		}
	}

	public int
	getStatsType()
	{
		return( stats_type );
	}

	public DHTTransportFullStats
	getOriginalStats()
	{
		return( original_stats );
	}

	public void
	setOriginalStats(
		DHTTransportFullStats	_stats )
	{
		stats_type		= DHTUDPPacketRequestStats.STATS_TYPE_ORIGINAL;
		original_stats	= _stats;
	}

	public byte[]
	getNewStats()
	{
		return( new_stats );
	}

	public void
	setNewStats(
		byte[]					_stats,
		int						_stats_type )
	{
		stats_type		= _stats_type;
		new_stats		= _stats;
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

			if ( stats_type == DHTUDPPacketRequestStats.STATS_TYPE_ORIGINAL ){

				DHTUDPUtils.serialiseStats( getProtocolVersion(), os, original_stats );

			}else{

				DHTUDPUtils.serialiseByteArray( os, new_stats, 65535 );
			}

		}else{

			DHTUDPUtils.serialiseStats( getProtocolVersion(), os, original_stats );
		}
	}
}
