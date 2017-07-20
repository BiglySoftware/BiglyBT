/*
 * Created on 22 Jun 2006
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

package com.biglybt.core.networkmanager.impl.udp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import com.biglybt.core.util.AESemaphore;

public class
UDPConnection
{
	private final UDPConnectionSet	set;
	private int					id;
	private UDPTransportHelper	transport;

	private final List	read_buffers = new LinkedList();

	private final AESemaphore	read_buffer_sem	= new AESemaphore( "UDPConnection", 64 );


	private volatile boolean	connected = true;

	protected
	UDPConnection(
		UDPConnectionSet	_set,
		int					_id,
		UDPTransportHelper	_transport )
	{
		set			= _set;
		id			= _id;
		transport	= _transport;
	}

	protected
	UDPConnection(
		UDPConnectionSet	_set,
		int					_id )
	{
		set			= _set;
		id			= _id;
	}

	protected UDPSelector
	getSelector()
	{
		return( set.getSelector());
	}

	protected int
	getID()
	{
		return( id );
	}

	protected void
	setID(
		int		_id )
	{
		id	= _id;
	}

	public boolean
	isIncoming()
	{
		return( transport.isIncoming());
	}

	protected void
	setSecret(
		byte[]	session_secret )
	{
		set.setSecret( this, session_secret );
	}

	protected void
	setTransport(
		UDPTransportHelper	_transport )
	{
		transport	= _transport;
	}

	protected UDPTransportHelper
	getTransport()
	{
		return( transport );
	}

	protected void
	receive(
		ByteBuffer		data )

		throws IOException
	{
			// packets reach us using 8K space regardless of content - trim this back for small protocol
			// messages to save memory

		int	rem = data.remaining();

		if ( rem < 256 ){

			byte[]	temp = new byte[rem];

			data.get( temp );

			data = ByteBuffer.wrap( temp );
		}

		read_buffer_sem.reserve();

		if ( !connected ){

			throw( new IOException( "Transport closed" ));
		}

		boolean	was_empty = false;

		synchronized( read_buffers ){

			was_empty = read_buffers.size() == 0;

			read_buffers.add( data );
		}

		if ( was_empty ){

			transport.canRead();
		}
	}

	protected void
	sent()
	{
			// notification that a packet has been sent

		transport.canWrite();
	}

	protected boolean
	canRead()
	{
		synchronized( read_buffers ){

			return( read_buffers.size() > 0 );
		}
	}

	protected boolean
	canWrite()
	{
		return( set.canWrite( this ));
	}

	protected int
	write(
		ByteBuffer[] 	buffers,
		int				offset,
		int				length )

		throws IOException
	{
		int	written = set.write( this, buffers, offset, length );

		// System.out.println( "Connection(" + getID() + ") - write -> " + written );

		return( written );
	}

	protected int
	read(
		ByteBuffer	buffer )

		throws IOException
	{
		int	total = 0;

		synchronized( read_buffers ){

			while( read_buffers.size() > 0 ){

				int	rem = buffer.remaining();

				if ( rem == 0 ){

					break;
				}

				ByteBuffer	b = (ByteBuffer)read_buffers.get(0);

				int	old_limit = b.limit();

				if ( b.remaining() > rem ){

					b.limit( b.position() + rem );
				}

				buffer.put( b );

				b.limit( old_limit );

				total += rem - buffer.remaining();

				if ( b.hasRemaining()){

					break;

				}else{

					read_buffers.remove(0);

					read_buffer_sem.release();
				}
			}
		}

		// System.out.println( "Connection(" + getID() + ") - read -> " +total );

		return( total );
	}

	protected void
	close(
		String	reason )
	{
		if ( transport != null ){

			transport.close( reason );

		}else{

			closeSupport( reason );
		}
	}

	protected void
	failed(
		Throwable	reason )
	{
		if ( transport != null ){

			transport.failed( reason );

		}else{

			failedSupport( reason );
		}
	}

	protected void
	closeSupport(
		String	reason )
	{
		connected	= false;

		read_buffer_sem.releaseForever();

		set.close( this, reason );
	}

	protected void
	failedSupport(
		Throwable	reason )
	{
		connected	= false;

		read_buffer_sem.releaseForever();

		set.failed( this, reason );
	}

	protected boolean
	isConnected()
	{
		return( connected );
	}

	protected void
	poll()
	{
		if ( transport != null ){

			transport.poll();
		}
	}
}
