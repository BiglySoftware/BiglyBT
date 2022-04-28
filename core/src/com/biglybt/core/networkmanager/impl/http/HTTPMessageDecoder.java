/*
 * Created on 2 Oct 2006
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

package com.biglybt.core.networkmanager.impl.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.networkmanager.Transport;
import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.messaging.MessageStreamDecoder;
import com.biglybt.core.util.Debug;

public class
HTTPMessageDecoder
	implements MessageStreamDecoder
{
	private static final int 	MAX_HEADER	= 1024;
	private static final String	NL			= "\r\n";

	private HTTPNetworkConnection	http_connection;

	private volatile boolean		paused;
	private volatile boolean		paused_internally;
	private volatile boolean		destroyed;

	private final StringBuffer	header_so_far = new StringBuffer();
	private boolean			header_ready;

	private final List			messages = new ArrayList();

	private int				protocol_bytes_read;

	public
	HTTPMessageDecoder()
	{
	}

	public
	HTTPMessageDecoder(
		String		pre_read_header )
	{
		header_so_far.append( pre_read_header );

		header_ready	= true;
	}

	public void
	setConnection(
		HTTPNetworkConnection	_http_connection )
	{
		http_connection	= _http_connection;

		if ( destroyed ){

			http_connection.destroy();
		}
	}

	@Override
	public int
	performStreamDecode(
		Transport 	transport,
		int 		max_bytes )

		throws IOException
	{
			// before we start message processing we should have had the connection bound

		if ( http_connection == null ){

			Debug.out( "connection not yet assigned" );

			throw( new IOException( "Internal error - connection not yet assigned" ));
		}

		// System.out.println( "performStreamDecode" );

		protocol_bytes_read	= 0;

		if ( paused_internally ){

			return( 0 );
		}

		if ( header_ready ){

			header_ready = false;

			int	len = header_so_far.length();

			http_connection.decodeHeader( this, header_so_far.toString());

			header_so_far.setLength(0);

			return( len );

		}else{
			int	rem = max_bytes;

			byte[]	bytes = new byte[1];

			ByteBuffer		bb	= ByteBuffer.wrap( bytes );

			ByteBuffer[]	bbs = { bb };

			while( rem > 0 && !( paused || paused_internally )){

				if ( transport.read( bbs,0, 1 ) == 0 ){

					break;
				}

				rem--;

				protocol_bytes_read++;

				bb.flip();

				char	c = (char)(bytes[0]&0xff);

				header_so_far.append( c );

				if ( header_so_far.length() > MAX_HEADER ){

					throw( new IOException( "HTTP header exceeded maximum of " + MAX_HEADER ));
				}

				if ( c == '\n' ){

					String	header_str = header_so_far.toString();

					if ( header_str.endsWith( NL + NL )){

						http_connection.decodeHeader( this, header_str );

						header_so_far.setLength(0);
					}
				}
			}

			return( max_bytes - rem );
		}
	}



	protected void
	addMessage(
		Message		message )
	{
		synchronized( messages ){

			messages.add( message );
		}

		http_connection.readWakeup();
	}

	@Override
	public Message[]
	removeDecodedMessages()
	{
		synchronized( messages ){

			if ( messages.isEmpty()){

				return null;
			}

			Message[] msgs = (Message[])messages.toArray( new Message[messages.size()] );

			messages.clear();

			return( msgs );
		}
	}

	@Override
	public int
	getProtocolBytesDecoded()
	{
		return( protocol_bytes_read );
	}

	@Override
	public int
	getDataBytesDecoded()
	{
		return( 0 );
	}

	@Override
	public int[]
	getCurrentMessageProgress()
	{
		return( null );
	}

	protected void
	pauseInternally()
	{
		paused_internally = true;
	}

	@Override
	public void
	pauseDecoding()
	{
		paused	= true;
	}

	@Override
	public void
	resumeDecoding()
	{
		if ( !destroyed ){

			paused	= false;
		}
	}

	protected int
	getQueueSize()
	{
		return( messages.size());
	}

	@Override
	public ByteBuffer
	destroy()
	{
		paused		= true;
		destroyed	= true;

		if ( http_connection != null ){

			http_connection.destroy();
		}

	    try{

	    	for( int i=0; i<messages.size(); i++ ){

	    		Message msg = (Message)messages.get( i );

			    msg.destroy();
			}
		}catch( IndexOutOfBoundsException e ){
		    	// as access to messages_last_read isn't synchronized we can get this error if we destroy the
		    	// decoder in parallel with messages being removed. We don't really want to synchornized access
		    	// to this so we'll take the hit here
		}

		messages.clear();

		return( null );
	}
}
