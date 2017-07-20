/*
 * Created on 14-Oct-2004
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

package com.biglybt.core.peer.impl.transport;

/**
 * CURRENTLY UNUSED
 *
 */

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;


public class
PEPeerTransportDebugger
{
	protected final int	piece_length;

	protected
	PEPeerTransportDebugger(
		PEPeerTransportProtocol	transport )
	{
		piece_length = transport.getControl().getPieceLength(0);
	}

	public int
	write(
		SocketChannel		chan,
		ByteBuffer			buffer )

		throws IOException
	{
		int	pos	= buffer.position();

		int	len = chan.write( buffer );

		if ( len > 0 ){

			buffer.position( pos );

			analyse( buffer, len );
		}

		return( len );
	}

	public long
	write(
		SocketChannel		chan,
		ByteBuffer[]		buffers,
		int					array_offset,
		int					array_length )

		throws IOException
	{
		int[]	pos = new int[buffers.length];

		for (int i=array_offset;i<array_offset+array_length;i++){

			pos[i]	= buffers[i].position();
		}

		long	len = chan.write( buffers, array_offset, array_length );

		for (int i=array_offset;i<array_offset+array_length;i++){

			ByteBuffer	buffer = buffers[i];

			int	written = buffer.position() - pos[i];

			if ( written > 0 ){

				buffer.position( pos[i] );

				analyse( buffer, written );
			}
		}

		return( len );
	}

	protected static final int	BT_READING_LENGTH_AND_TYPE	= 1234567;

	protected int		state		= -1;  //bt handshake
	protected byte[]	data_read	= new byte[68];
	protected int		data_read_pos;

	protected void
	analyse(
		ByteBuffer	buffer,
		int			length )
	{
		byte[]	data = new byte[length];

		buffer.get(data);

		for (int i=0;i<data.length;i++){

			if ( data_read_pos == data_read.length ){

				if ( state == BT_READING_LENGTH_AND_TYPE ){

					ByteBuffer bb = ByteBuffer.wrap( data_read );

					int	len = bb.getInt();

					state = bb.get();

					//System.out.println( "Header: len = " + len + ", state = " + state );

					if ( len == 1 ){

							// messages with no body

						//System.out.println( "msg:" + state );

						state = BT_READING_LENGTH_AND_TYPE;

						data_read		= new byte[5];

					}else{

						data_read	= new byte[len-1];
					}

				}else{

						// messages with body

					//System.out.println( "msg:" + state );

					if ( state == 7 ){  //bt piece

						ByteBuffer bb = ByteBuffer.wrap( data_read );

						int	piece_number 	= bb.getInt();
						int piece_offset	= bb.getInt();

				       	long	overall_offset = ((long)piece_number)*piece_length + piece_offset;

			        	while(bb.hasRemaining()){

							byte	v = bb.get();

							if ((byte)overall_offset != v ){

								System.out.println( "piece: write is bad at " + overall_offset +
													": expected = " + (byte)overall_offset + ", actual = " + v );

								break;
							}

							overall_offset++;
			        	}
					}

					state = BT_READING_LENGTH_AND_TYPE;

					data_read		= new byte[5];
				}

				data_read_pos	= 0;
			}

			data_read[data_read_pos++] = data[i];
		}
	}
}
