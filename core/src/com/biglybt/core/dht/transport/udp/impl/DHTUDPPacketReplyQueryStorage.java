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
import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.dht.transport.DHTTransportContact;
import com.biglybt.core.dht.transport.udp.impl.packethandler.DHTUDPPacketNetworkHandler;

public class
DHTUDPPacketReplyQueryStorage
	extends DHTUDPPacketReply
{
	private int					random_id;
	private int					header_length;
	private List<byte[]>		response;

	public
	DHTUDPPacketReplyQueryStorage(
		DHTTransportUDPImpl					transport,
		DHTUDPPacketRequestQueryStorage		request,
		DHTTransportContact					local_contact,
		DHTTransportContact					remote_contact )
	{
		super( transport, DHTUDPPacketHelper.ACT_REPLY_QUERY_STORE, request, local_contact, remote_contact );
	}

	protected
	DHTUDPPacketReplyQueryStorage(
		DHTUDPPacketNetworkHandler		network_handler,
		InetSocketAddress				originator,
		DataInputStream					is,
		int								trans_id )

		throws IOException
	{
		super( network_handler, originator, is, DHTUDPPacketHelper.ACT_REPLY_QUERY_STORE, trans_id );

		short size = is.readShort();

		response = new ArrayList<>(size);

		if ( size > 0 ){

			header_length = is.readByte()&0xff;

			byte[]	bitmap = new byte[size+7/8];

			is.read( bitmap );

			int	pos		= 0;

			int	current	= 0;

			for (int i=0;i<size;i++){

				if ( i % 8 == 0 ){

					current = bitmap[pos++]&0xff;
				}

				if (( current&0x80)!=0 ){

					byte[]	x = new byte[header_length];

					is.read( x );

					response.add( x );

				}else{

					response.add( null );
				}

				current <<= 1;
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

		int	size = response.size();

		os.writeShort( size );

		if ( size > 0 ){

			os.writeByte( header_length );

			byte[]	bitmap = new byte[size+7/8];

			int	bitmap_pos		= 0;
			int	current_byte	= 0;
			int	pos 			= 0;

			for ( byte[] x: response ){

				current_byte = current_byte << 1;

				if ( x != null){

					current_byte += 1;
				}

				if (( pos %8 ) == 7 ){

					bitmap[bitmap_pos++] = (byte)current_byte;

					current_byte = 0;
				}

				pos++;
			}

			if (( pos % 8 ) != 0 ){

				bitmap[bitmap_pos++] = (byte)(current_byte << (8 - (pos % 8)));
			}

			os.write( bitmap );

			for ( byte[] x: response ){

				if ( x != null ){

					os.write( x );
				}
			}
		}
	}

	protected void
	setRandomID(
		int		id )
	{
		random_id	= id;
	}

	protected int
	getRandomID()
	{
		return( random_id );
	}

	protected void
	setResponse(
		int				_header_length,
		List<byte[]>	_response )
	{
		header_length	= _header_length;
		response		= _response;
	}

	protected List<byte[]>
	getResponse()
	{
		return( response );
	}
}
