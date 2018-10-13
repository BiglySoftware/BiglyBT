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

import com.biglybt.core.dht.DHT;
import com.biglybt.core.dht.transport.udp.DHTTransportUDP;
import com.biglybt.core.dht.transport.udp.impl.packethandler.DHTUDPPacketNetworkHandler;


/**
 * @author parg
 *
 */

public class
DHTUDPPacketRequestFindNode
	extends DHTUDPPacketRequest
{
	private byte[]		id;

	private int			node_status;
	private int			estimated_dht_size;

	private Object	upload_stats;

	public
	DHTUDPPacketRequestFindNode(
		DHTTransportUDPImpl				_transport,
		long							_connection_id,
		DHTTransportUDPContactImpl		_local_contact,
		DHTTransportUDPContactImpl		_remote_contact )
	{
		super( _transport, DHTUDPPacketHelper.ACT_REQUEST_FIND_NODE, _connection_id, _local_contact, _remote_contact );
	}

	protected
	DHTUDPPacketRequestFindNode(
		DHTUDPPacketNetworkHandler		network_handler,
		DataInputStream					is,
		long							con_id,
		int								trans_id )

		throws IOException
	{
		super( network_handler, is, DHTUDPPacketHelper.ACT_REQUEST_FIND_NODE, con_id, trans_id );

		id = DHTUDPUtils.deserialiseByteArray( is, 64 );

		int	protocol_version = getProtocolVersion();
		
		if ( protocol_version >= DHTTransportUDP.PROTOCOL_VERSION_MORE_NODE_STATUS ){

			node_status 		= is.readInt();
			estimated_dht_size 	= is.readInt();
		}
		
		if ( getNetwork() == DHT.NW_BIGLYBT_MAIN ){
			
			if ( protocol_version >= DHTTransportUDP.PROTOCOL_VERSION_BBT_UPLOAD_STATS ){

				if ( is.available() > 0 ){	// interim during 1601 betas
				
					upload_stats = DHTUDPUtils.deserialiseUploadStats( is );
				}
			}
		}
	}

	@Override
	public void
	serialise(
		DataOutputStream	os )

		throws IOException
	{
		super.serialise(os);

		DHTUDPUtils.serialiseByteArray( os, id, 64 );

		int	protocol_version = getProtocolVersion();

		if ( protocol_version >= DHTTransportUDP.PROTOCOL_VERSION_MORE_NODE_STATUS ){

			 os.writeInt( node_status );

			 os.writeInt( estimated_dht_size );
		}
		
		if ( getNetwork() == DHT.NW_BIGLYBT_MAIN ){
			
			//if ( protocol_version >= DHTTransportUDP.PROTOCOL_VERSION_BBT_UPLOAD_STATS ){ checked in serialiseUploadStats

				DHTUDPUtils.serialiseUploadStats( protocol_version, getAction(), os );
			//}
		}
	}

	protected void
	setID(
		byte[]		_id )
	{
		id	= _id;
	}

	protected byte[]
	getID()
	{
		return( id );
	}

	protected void
	setNodeStatus(
		int		ns )
	{
		node_status	= ns;
	}

	protected int
	getNodeStatus()
	{
		return( node_status );
	}

	protected void
	setEstimatedDHTSize(
		int	s )
	{
		estimated_dht_size	= s;
	}

	protected int
	getEstimatedDHTSize()
	{
		return( estimated_dht_size );
	}

	protected Object
	getUploadStats()
	{
		return( upload_stats );
	}
	
	@Override
	public String
	getString()
	{
		return( super.getString());
	}
}