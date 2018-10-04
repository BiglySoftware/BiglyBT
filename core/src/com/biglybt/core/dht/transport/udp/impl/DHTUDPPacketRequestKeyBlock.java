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

import com.biglybt.core.dht.transport.udp.impl.packethandler.DHTUDPPacketNetworkHandler;


/**
 * @author parg
 *
 */

public class
DHTUDPPacketRequestKeyBlock
	extends DHTUDPPacketRequest
{
	private int		random_id;

	private byte[]	key_block_request;
	private byte[]	key_block_signature;

	public
	DHTUDPPacketRequestKeyBlock(
		DHTTransportUDPImpl				_transport,
		long							_connection_id,
		DHTTransportUDPContactImpl		_local_contact,
		DHTTransportUDPContactImpl		_remote_contact )
	{
		super( _transport, DHTUDPPacketHelper.ACT_REQUEST_KEY_BLOCK, _connection_id, _local_contact, _remote_contact );
	}

	protected
	DHTUDPPacketRequestKeyBlock(
		DHTUDPPacketNetworkHandler		network_handler,
		DataInputStream					is,
		long							con_id,
		int								trans_id )

		throws IOException
	{
		super( network_handler, is,  DHTUDPPacketHelper.ACT_REQUEST_KEY_BLOCK, con_id, trans_id );

		random_id = is.readInt();

		key_block_request 	= DHTUDPUtils.deserialiseByteArray( is, 255 );
		key_block_signature = DHTUDPUtils.deserialiseByteArray( is, 65535 );
	}

	@Override
	public void
	serialise(
		DataOutputStream	os )

		throws IOException
	{
		super.serialise(os);

		os.writeInt( random_id );

		DHTUDPUtils.serialiseByteArray( os, key_block_request, 255 );
		DHTUDPUtils.serialiseByteArray( os, key_block_signature , 65535);
	}

	public void
	setKeyBlockDetails(
		byte[]		req,
		byte[]		sig )
	{
		key_block_request	= req;
		key_block_signature	= sig;
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

	public byte[]
	getKeyBlockRequest()
	{
		return( key_block_request );
	}

	public byte[]
	getKeyBlockSignature()
	{
		return( key_block_signature );
	}

	@Override
	public String
	getString()
	{
		return( super.getString());
	}
}