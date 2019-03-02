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
DHTUDPPacketRequestFindValue
	extends DHTUDPPacketRequest
{
	private byte[]		id;
	private byte		flags;
	private byte		maximum_values;

	public
	DHTUDPPacketRequestFindValue(
		DHTTransportUDPImpl				_transport,
		long							_connection_id,
		DHTTransportUDPContactImpl		_local_contact,
		DHTTransportUDPContactImpl		_remote_contact )
	{
		super( _transport, DHTUDPPacketHelper.ACT_REQUEST_FIND_VALUE, _connection_id, _local_contact, _remote_contact );
	}

	protected
	DHTUDPPacketRequestFindValue(
		DHTUDPPacketNetworkHandler		network_handler,
		DataInputStream					is,
		long							con_id,
		int								trans_id )

		throws IOException
	{
		super( network_handler, is,  DHTUDPPacketHelper.ACT_REQUEST_FIND_VALUE, con_id, trans_id );

		id = DHTUDPUtils.deserialiseByteArray( is, 64 );

		flags = is.readByte();

		maximum_values	= is.readByte();
	}

	@Override
	public void
	serialise(
		DataOutputStream	os )

		throws IOException
	{
		super.serialise(os);

		DHTUDPUtils.serialiseByteArray( os, id, 64 );

		os.writeByte( flags );

		os.writeByte( maximum_values );
	}

	protected void
	setID(
		byte[]	_id )
	{
		id	= _id;
	}

	protected byte[]
	getID()
	{
		return( id );
	}

	protected byte
	getFlags()
	{
		return( flags );
	}

	protected void
	setFlags(
		byte		_flags )
	{
		flags	= _flags;
	}

	protected void
	setMaximumValues(
		int		max )
	{
		if ( max > 255 ){

			max	= 255;
		}

		maximum_values	= (byte)max;
	}

	protected int
	getMaximumValues()
	{
		return( maximum_values&0xff );
	}

	@Override
	public String
	getString()
	{
		return( super.getString());
	}
}