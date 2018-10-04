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
import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.dht.transport.udp.impl.packethandler.DHTUDPPacketNetworkHandler;


/**
 * @author parg
 *
 */

public class
DHTUDPPacketRequestQueryStorage
	extends DHTUDPPacketRequest
{
	protected static final int SPACE =
		DHTUDPPacketHelper.PACKET_MAX_BYTES - DHTUDPPacketRequest.DHT_HEADER_SIZE - 3;

	private int						header_length;
	private List<Object[]>			keys;


	public
	DHTUDPPacketRequestQueryStorage(
		DHTTransportUDPImpl				_transport,
		long							_connection_id,
		DHTTransportUDPContactImpl		_local_contact,
		DHTTransportUDPContactImpl		_remote_contact )
	{
		super( _transport, DHTUDPPacketHelper.ACT_REQUEST_QUERY_STORE, _connection_id, _local_contact, _remote_contact );
	}

	protected
	DHTUDPPacketRequestQueryStorage(
		DHTUDPPacketNetworkHandler		network_handler,
		DataInputStream					is,
		long							con_id,
		int								trans_id )

		throws IOException
	{
		super( network_handler, is,  DHTUDPPacketHelper.ACT_REQUEST_QUERY_STORE, con_id, trans_id );

		header_length = is.readByte()&0xff;

		int	num_keys = is.readShort();

		keys = new ArrayList<>(num_keys);

		for (int i=0;i<num_keys;i++){

			int	prefix_length = is.readByte()&0xff;

			byte[]	prefix = new byte[prefix_length];

			is.read( prefix );

			short num_suffixes = is.readShort();

			List<byte[]> suffixes = new ArrayList<>(num_suffixes);

			keys.add( new Object[]{ prefix, suffixes });

			int	suffix_length = header_length - prefix_length;

			for (int j=0;j<num_suffixes;j++){

				byte[] suffix = new byte[ suffix_length ];

				is.read( suffix );

				suffixes.add( suffix );
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

		os.writeByte( header_length&0xff );

		os.writeShort( keys.size());

			// add anything here be sure to adjust the SPACE above

		for ( Object[] entry: keys ){

			byte[] prefix = (byte[])entry[0];

			os.writeByte( prefix.length );

			os.write( prefix );

			List<byte[]> suffixes = (List<byte[]>)entry[1];

			os.writeShort( suffixes.size());

			for ( byte[] suffix: suffixes ){

				os.write( suffix );
			}
		}
	}

	protected void
	setDetails(
		int							_header_length,
		List<Object[]>				_keys )
	{
		header_length	= _header_length;
		keys			= _keys;
	}

	protected int
	getHeaderLength()
	{
		return( header_length );

	}

	protected List<Object[]>
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