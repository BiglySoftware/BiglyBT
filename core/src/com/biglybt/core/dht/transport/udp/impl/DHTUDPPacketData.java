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

import com.biglybt.core.dht.impl.DHTLog;
import com.biglybt.core.dht.transport.udp.DHTTransportUDP;
import com.biglybt.core.dht.transport.udp.impl.packethandler.DHTUDPPacketNetworkHandler;


/**
 * @author parg
 *
 */

public class
DHTUDPPacketData
	extends DHTUDPPacketRequest
{
	public static final byte		PT_READ_REQUEST		= 0x00;
	public static final byte		PT_READ_REPLY		= 0x01;
	public static final byte		PT_WRITE_REQUEST	= 0x02;
	public static final byte		PT_WRITE_REPLY		= 0x03;

	private byte	packet_type;
	private byte[]	transfer_key;
	private byte[]	key;
	private byte[]	data;
	private int		start_position;
	private int		length;
	private int		total_length;

		// assume keys are 20 bytes + 1 len, data len is 2 bytes

	public static final int	MAX_DATA_SIZE = DHTUDPPacketHelper.PACKET_MAX_BYTES - DHTUDPPacketReply.DHT_HEADER_SIZE -
											1- 21 - 21 - 14;

	public
	DHTUDPPacketData(
		DHTTransportUDPImpl				_transport,
		long							_connection_id,
		DHTTransportUDPContactImpl		_local_contact,
		DHTTransportUDPContactImpl		_remote_contact )
	{
		super( _transport, DHTUDPPacketHelper.ACT_DATA, _connection_id, _local_contact, _remote_contact );
	}

	protected
	DHTUDPPacketData(
		DHTUDPPacketNetworkHandler		network_handler,
		DataInputStream					is,
		long							con_id,
		int								trans_id )

		throws IOException
	{
		super( network_handler, is,  DHTUDPPacketHelper.ACT_DATA, con_id, trans_id );

		packet_type		= is.readByte();
		transfer_key	= DHTUDPUtils.deserialiseByteArray( is, 64 );

		int	max_key_size;

		if ( getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_REPLICATION_CONTROL ){

			max_key_size = 255;

		}else{

			max_key_size = 64;
		}

		key				= DHTUDPUtils.deserialiseByteArray( is, max_key_size );
		start_position	= is.readInt();
		length			= is.readInt();
		total_length	= is.readInt();
		data			= DHTUDPUtils.deserialiseByteArray( is, 65535 );
	}

	@Override
	public void
	serialise(
		DataOutputStream	os )

		throws IOException
	{
		super.serialise(os);

		os.writeByte( packet_type );
		DHTUDPUtils.serialiseByteArray( os, transfer_key, 64 );

		int	max_key_size;

		if ( getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_REPLICATION_CONTROL ){

			max_key_size = 255;

		}else{

			max_key_size = 64;
		}

		DHTUDPUtils.serialiseByteArray( os, key, max_key_size );
		os.writeInt( start_position );
		os.writeInt( length );
		os.writeInt( total_length );

		if ( data.length > 0 ){

			DHTUDPUtils.serialiseByteArray( os, data, start_position, length, 65535 );

		}else{

			DHTUDPUtils.serialiseByteArray( os, data,  65535 );
		}
	}

	public void
	setDetails(
		byte		_packet_type,
		byte[]		_transfer_key,
		byte[]		_key,
		byte[]		_data,
		int			_start_pos,
		int			_length,
		int			_total_length )
	{
		packet_type			= _packet_type;
		transfer_key		= _transfer_key;
		key					= _key;
		data				= _data;
		start_position		= _start_pos;
		length				= _length;
		total_length		= _total_length;
	}

	public byte
	getPacketType()
	{
		return( packet_type );
	}

	public byte[]
	getTransferKey()
	{
		return( transfer_key );
	}

	public byte[]
	getRequestKey()
	{
		return( key );
	}

	public byte[]
	getData()
	{
		return( data );
	}

	public int
	getStartPosition()
	{
		return( start_position );
	}

	public int
	getLength()
	{
		return( length );
	}

	public int
	getTotalLength()
	{
		return( total_length );
	}

	@Override
	public String
	getString()
	{
		return( super.getString() + "tk=" + DHTLog.getString2( transfer_key ) + ",rk=" +
				DHTLog.getString2( key ) + ",data=" + data.length +
				",st=" + start_position + ",len=" + length + ",tot=" + total_length );
	}
}