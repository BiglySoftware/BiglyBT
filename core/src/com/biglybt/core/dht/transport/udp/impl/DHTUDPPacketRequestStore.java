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

import com.biglybt.core.dht.transport.DHTTransportException;
import com.biglybt.core.dht.transport.DHTTransportValue;
import com.biglybt.core.dht.transport.udp.DHTTransportUDP;
import com.biglybt.core.dht.transport.udp.impl.packethandler.DHTUDPPacketNetworkHandler;


/**
 * @author parg
 *
 */

public class
DHTUDPPacketRequestStore
	extends DHTUDPPacketRequest
{
	public static final int	MAX_KEYS_PER_PACKET		= 255; // 1 byte DHTUDPPacket.PACKET_MAX_BYTES / 20;
	public static final int	MAX_VALUES_PER_KEY		= 255; // 1 byte DHTUDPPacket.PACKET_MAX_BYTES / DHTUDPUtils.DHTTRANSPORTVALUE_SIZE_WITHOUT_VALUE;

	private int						random_id;
	private byte[][]				keys;
	private	DHTTransportValue[][]	value_sets;

	public
	DHTUDPPacketRequestStore(
		DHTTransportUDPImpl				_transport,
		long							_connection_id,
		DHTTransportUDPContactImpl		_local_contact,
		DHTTransportUDPContactImpl		_remote_contact )
	{
		super( _transport, DHTUDPPacketHelper.ACT_REQUEST_STORE, _connection_id, _local_contact, _remote_contact );
	}

	protected
	DHTUDPPacketRequestStore(
		DHTUDPPacketNetworkHandler		network_handler,
		DataInputStream					is,
		long							con_id,
		int								trans_id )

		throws IOException
	{
		super( network_handler, is,  DHTUDPPacketHelper.ACT_REQUEST_STORE, con_id, trans_id );

		if ( getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_ANTI_SPOOF ){

			random_id	= is.readInt();
		}

		keys		= DHTUDPUtils.deserialiseByteArrayArray( is, MAX_KEYS_PER_PACKET );

			// times receieved are adjusted by + skew

		value_sets 	= DHTUDPUtils.deserialiseTransportValuesArray( this, is, getClockSkew(), MAX_KEYS_PER_PACKET );
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

		DHTUDPUtils.serialiseByteArrayArray( os, keys, MAX_KEYS_PER_PACKET );

		try{
			DHTUDPUtils.serialiseTransportValuesArray( this, os, value_sets, 0, MAX_KEYS_PER_PACKET );

		}catch( DHTTransportException e ){

			throw( new IOException( e.getMessage()));
		}
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
	setValueSets(
		DHTTransportValue[][]	_values )
	{
		value_sets	= _values;
	}

	protected DHTTransportValue[][]
	getValueSets()
	{
		return( value_sets );
	}

	protected void
	setKeys(
		byte[][]		_key )
	{
		keys	= _key;
	}

	protected byte[][]
	getKeys()
	{
		return( keys );
	}

	@Override
	public String
	getString()
	{
		return( super.getString());
	}
}