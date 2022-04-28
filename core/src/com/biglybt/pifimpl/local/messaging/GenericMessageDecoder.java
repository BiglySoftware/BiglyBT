/*
 * Created on 19 Jun 2006
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

package com.biglybt.pifimpl.local.messaging;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.networkmanager.Transport;
import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.messaging.MessageStreamDecoder;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DirectByteBuffer;

public class
GenericMessageDecoder
	implements MessageStreamDecoder
{
	public static final int MAX_MESSAGE_LENGTH = 256*1024;

	private final ByteBuffer length_buffer = ByteBuffer.allocate( 4 );

	private final ByteBuffer[]	buffers = { length_buffer, null };

	private final String	msg_type;
	private final String	msg_desc;

	private List 	messages = new ArrayList();

	private int protocol_bytes_last_read = 0;
	private int data_bytes_last_read = 0;

	private volatile boolean	destroyed;

	protected
	GenericMessageDecoder(
		String		_msg_type,
		String		_msg_desc )
	{
		msg_type	= _msg_type;
		msg_desc	= _msg_desc;
	}

	@Override
	public int
	performStreamDecode(
		Transport 	transport,
		int 		max_bytes )

		throws IOException
	{
		protocol_bytes_last_read 	= 0;
	    data_bytes_last_read 		= 0;

	    long	total_read = 0;

		while( total_read < max_bytes ){

			long	bytes_read;

			int		read_lim = (int)( max_bytes - total_read );

			ByteBuffer	payload_buffer = buffers[1];

			if ( payload_buffer == null ){

				int	rem = length_buffer.remaining();
				int	lim	= length_buffer.limit();

				if ( rem > read_lim ){

					length_buffer.limit( length_buffer.position() + read_lim );
				}

				bytes_read = transport.read( buffers, 0, 1 );

				length_buffer.limit( lim );

				protocol_bytes_last_read += bytes_read;

				if ( length_buffer.hasRemaining()){

					total_read += bytes_read;

					break;

				}else{

					length_buffer.flip();

					int	size = length_buffer.getInt();

					if ( size > MAX_MESSAGE_LENGTH || size < 0 ){

						Debug.out( "Message size invalid for generic payload (" + size + ", " + transport.getTransportEndpoint().getProtocolEndpoint().getAddress() + ")" );

						throw( new IOException( "message too large" ));
					}

					buffers[1] = ByteBuffer.allocate( size );

					length_buffer.flip();
				}
			}else{

				int	rem = payload_buffer.remaining();
				int	lim	= payload_buffer.limit();

				if ( rem > read_lim ){

					payload_buffer.limit( payload_buffer.position() + read_lim );
				}

				bytes_read = transport.read( buffers, 1, 1 );

				payload_buffer.limit( lim );

				data_bytes_last_read += bytes_read;

				if ( payload_buffer.hasRemaining()){

					total_read += bytes_read;

					break;
				}

				payload_buffer.flip();

				messages.add( new GenericMessage( msg_type, msg_desc, new DirectByteBuffer( payload_buffer ), false ));

				buffers[1]	= null;
			}

			total_read += bytes_read;
		}

		if ( destroyed ){

			throw( new IOException( "decoder has been destroyed" ));
		}

		return((int) total_read );
	}

	@Override
	public Message[]
	removeDecodedMessages()
	{
		if( messages.isEmpty() )  return null;

		Message[] msgs = (Message[])messages.toArray( new Message[messages.size()] );

		messages.clear();

		return( msgs );
	}

	@Override
	public int
	getProtocolBytesDecoded()
	{
		return( protocol_bytes_last_read );
	}

	@Override
	public int
	getDataBytesDecoded()
	{
		return( data_bytes_last_read );
	}

	@Override
	public int[]
	getCurrentMessageProgress()
	{
		return( null );
	}

	@Override
	public void
	pauseDecoding()
	{
	}

	@Override
	public void
	resumeDecoding()
	{
	}

	@Override
	public ByteBuffer
	destroy()
	{
		destroyed	= true;

		return( null );
	}
}
