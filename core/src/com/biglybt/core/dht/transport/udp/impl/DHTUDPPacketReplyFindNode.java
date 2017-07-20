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
import com.biglybt.core.dht.transport.udp.DHTTransportUDP;
import com.biglybt.core.dht.transport.udp.impl.packethandler.DHTUDPPacketNetworkHandler;

public class
DHTUDPPacketReplyFindNode
	extends DHTUDPPacketReply
{
	private DHTTransportContact[]	contacts;
	private int						random_id;
	private int						node_status	= DHTTransportUDPContactImpl.NODE_STATUS_UNKNOWN;
	private int						estimated_dht_size;

	public
	DHTUDPPacketReplyFindNode(
		DHTTransportUDPImpl				transport,
		DHTUDPPacketRequestFindNode		request,
		DHTTransportContact				local_contact,
		DHTTransportContact				remote_contact )
	{
		super( transport, DHTUDPPacketHelper.ACT_REPLY_FIND_NODE, request, local_contact, remote_contact );
	}

	protected
	DHTUDPPacketReplyFindNode(
		DHTUDPPacketNetworkHandler		network_handler,
		InetSocketAddress				originator,
		DataInputStream					is,
		int								trans_id )

		throws IOException
	{
		super( network_handler, originator, is, DHTUDPPacketHelper.ACT_REPLY_FIND_NODE, trans_id );

		if ( getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_ANTI_SPOOF ){

			random_id	= is.readInt();
		}

		if ( getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_XFER_STATUS ){

			node_status = is.readInt();
		}

		if ( getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_SIZE_ESTIMATE ){

			estimated_dht_size	= is.readInt();
		}

		if ( getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_VIVALDI ){

			DHTUDPUtils.deserialiseVivaldi( this, is );
		}

		contacts = DHTUDPUtils.deserialiseContacts( getTransport(), is );
	}

	@Override
	public void
	serialise(
		DataOutputStream	os )

		throws IOException
	{
		super.serialise(os);

		if ( getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_ANTI_SPOOF ){

			os.writeInt( random_id );
		}

		if ( getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_XFER_STATUS ){

			 os.writeInt( node_status );
		}

		if ( getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_SIZE_ESTIMATE ){

			 os.writeInt( estimated_dht_size );
		}

		if ( getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_VIVALDI ){

			DHTUDPUtils.serialiseVivaldi( this, os );
		}

		DHTUDPUtils.serialiseContacts( os, contacts );
	}

	protected void
	setContacts(
		DHTTransportContact[]	_contacts )
	{
		contacts	= _contacts;
	}

	protected void
	setRandomID(
		int	_random_id )
	{
		random_id	= _random_id;
	}

	protected int
	getRandomID()
	{
		return( random_id );
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

	protected DHTTransportContact[]
	getContacts()
	{
		return( contacts );
	}

	@Override
	public String
	getString()
	{
		return( super.getString() + ",contacts=" + (contacts==null?"null":(""+contacts.length )));
	}
}
