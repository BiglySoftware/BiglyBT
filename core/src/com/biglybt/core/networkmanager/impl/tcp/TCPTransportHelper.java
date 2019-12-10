/*
 * Created on Nov 1, 2005
 * Created by Alon Rohter
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
package com.biglybt.core.networkmanager.impl.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.biglybt.core.networkmanager.VirtualChannelSelector;
import com.biglybt.core.networkmanager.VirtualChannelSelector.VirtualSelectorListener;
import com.biglybt.core.networkmanager.impl.TransportHelper;
import com.biglybt.core.proxy.AEProxyAddressMapper;
import com.biglybt.core.proxy.AEProxyFactory;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.TimeFormatter;



/**
 *
 */
public class
TCPTransportHelper
	implements TransportHelper
{
	public static final int READ_TIMEOUT		= 10*1000;
	public static final int CONNECT_TIMEOUT		= 20*1000;

	private static final AEProxyAddressMapper proxy_address_mapper = AEProxyFactory.getAddressMapper();

	public static final int MAX_PARTIAL_WRITE_RETAIN	= 64;	// aim here is to catch headers

	private long remainingBytesToScatter = 0;

	private	final SocketChannel	channel;

	private ByteBuffer	delayed_write;

	private Map	user_data;

	boolean	trace;

	private volatile InetSocketAddress	tcp_address;
	private volatile boolean closed;

	public TCPTransportHelper( SocketChannel _channel ) {
		channel = _channel;
	}

	@Override
	public InetSocketAddress
	getAddress()
	{
		if ( tcp_address != null ){

			return( tcp_address );
		}

		Socket socket = channel.socket();

	    AEProxyAddressMapper.AppliedPortMapping applied_mapping = proxy_address_mapper.applyPortMapping( socket.getInetAddress(), socket.getPort());

	    tcp_address = applied_mapping.getRemoteAddress();

	    return( tcp_address );
	}

	@Override
	public String
	getName(boolean verbose)
	{
			// default is TCP so don't clutter up views with this info

		if ( verbose ){

			return( "TCP" );

		}else{

			return( "" );
		}
	}

	@Override
	public boolean
	minimiseOverheads()
	{
		return( false );
	}

	@Override
	public int
	getConnectTimeout()
	{
		return( CONNECT_TIMEOUT );
	}

	@Override
	public int
	getReadTimeout()
	{
		return( READ_TIMEOUT );
	}

	@Override
	public boolean
	delayWrite(
		ByteBuffer buffer)
	{
		if ( delayed_write != null ){

			Debug.out( "secondary delayed write" );

			return( false );
		}

		delayed_write = buffer;

		return( true );
	}

	@Override
	public boolean
	hasDelayedWrite()
	{
		return( delayed_write != null );
	}

	@Override
	public int
	write(
		ByteBuffer 	buffer,
		boolean 	partial_write )

		throws IOException
	{
		if ( channel == null ){

			Debug.out( "channel == null" );

			return 0;
		}

			// partial-write means we are guaranteed to get hit with another write straight away

		if ( partial_write && delayed_write == null ){

			if ( buffer.remaining() < MAX_PARTIAL_WRITE_RETAIN ){

				ByteBuffer	copy = ByteBuffer.allocate( buffer.remaining());

				copy.put( buffer );

				copy.position( 0 );

				delayed_write = copy;

				return( copy.remaining());
			}
		}

		long	written = 0;

		if ( delayed_write != null ){

			// System.out.println( "delayed write: single" );

			ByteBuffer[]	buffers = new ByteBuffer[]{ delayed_write, buffer };

			int	delay_remaining = delayed_write.remaining();

			delayed_write = null;

			written = write( buffers, 0, 2 );

				// note that we can't report delayed bytes actually written as these have already been accounted for and confuse
				// the layers above if we report them now

			if ( buffers[0].hasRemaining()){

				delayed_write = buffers[0];

				written = 0;

				// System.out.println( "delayed write: single incomp" );
			}else{

				written -= delay_remaining;
			}
		}else{

			// log( buffer );

			written = channelWrite(buffer);
		}

		if ( trace ){

			TimeFormatter.milliTrace( "tcp: write " + written );
		}

		return((int)written );
	}

	@Override
	public long
	write(
		ByteBuffer[] 	buffers,
		int 			array_offset,
		int 			length )

		throws IOException
	{
		if( channel == null ){

			Debug.out( "channel == null" );

			return 0;
		}

		long written_sofar = 0;

		if ( delayed_write != null ){

			ByteBuffer[]	buffers2 = new ByteBuffer[length+1];

			buffers2[0] = delayed_write;

			int	pos = 1;

			for (int i=array_offset;i<array_offset+length;i++){

				buffers2[pos++] = buffers[i];
			}

			// System.out.println( "delayed write: mult (" + buffers2.length + ")" );

			int	delay_remaining = delayed_write.remaining();

			delayed_write = null;

			written_sofar = write( buffers2, 0, buffers2.length );

			if ( buffers2[0].hasRemaining()){

				delayed_write = buffers2[0];

				written_sofar = 0;

				// System.out.println( "delayed write: mult incomp" );

			}else{

				written_sofar -= delay_remaining;
			}
		}else{

			// log( buffers, array_offset, length );

			if ( remainingBytesToScatter < 1 ){

				written_sofar = channel.write(buffers, array_offset, length);

			}else{
				  //single-buffer mode

				for( int i=array_offset; i < (array_offset + length); i++ ) {

					int data_length = buffers[ i ].remaining();

					int written = channelWrite(buffers[i]);

					written_sofar += written;

					if( written < data_length ) {

						break;
					}
				}
			}
		}


		if ( trace ){
			TimeFormatter.milliTrace( "tcp: write " + written_sofar );
		}

		return written_sofar;
	}

	private static final Random rnd = new Random();

	private int channelWrite(ByteBuffer buf) throws IOException
	{
		int written = 0;
		while(remainingBytesToScatter > 0 && buf.remaining() > 0)
		{
			int currentWritten = channel.write((ByteBuffer)(buf.slice().limit(Math.min(50+rnd.nextInt(100),buf.remaining()))));
			if(currentWritten == 0)
				break;
			buf.position(buf.position()+currentWritten);
			remainingBytesToScatter -= currentWritten;
			if(remainingBytesToScatter <= 0)
			{
				remainingBytesToScatter = 0;
				try
				{
					channel.socket().setTcpNoDelay(false);
				} catch (SocketException e)
				{
					Debug.printStackTrace(e);
				}
			}
			written += currentWritten;
		}

		if(buf.remaining() > 0)
			written += channel.write(buf);

		return written;
	}

	@Override
	public int
	read(
		ByteBuffer buffer )

		throws IOException
	{
		if( channel == null ) {

			Debug.out( "channel == null" );

			return 0;
		}

		int	res = channel.read( buffer );

		if ( trace ){
			TimeFormatter.milliTrace( "tcp: read " + res );
		}

		return( res );
	}

	@Override
	public long
	read(
		ByteBuffer[] 	buffers,
		int 			array_offset,
		int 			length )

		throws IOException
	{
		if( channel == null ) {
			Debug.out( "channel == null" );
			return 0;
		}

		if( buffers == null ) {
			Debug.out( "read: buffers == null" );
			return 0;
		}

		long bytes_read = 0;

		bytes_read = channel.read(buffers, array_offset, length);

		if ( bytes_read < 0 ){

			throw new IOException( "end of stream on socket read" );
		}

		if ( trace ){
			TimeFormatter.milliTrace( "tcp: read " + bytes_read );
		}

		return bytes_read;
	}

	@Override
	public void
	registerForReadSelects(
		final selectListener		listener,
		Object						attachment )
	{
		TCPNetworkManager.getSingleton().getReadSelector().register(
				channel,
				new VirtualSelectorListener()
				{
					@Override
					public boolean
					selectSuccess(
							VirtualChannelSelector	selector,
							SocketChannel			sc,
							Object 					attachment )
					{
						return( listener.selectSuccess( TCPTransportHelper.this, attachment ));
					}

					@Override
					public void
					selectFailure(
							VirtualChannelSelector	selector,
							SocketChannel 			sc,
							Object 					attachment,
							Throwable 				msg)
					{
						listener.selectFailure( TCPTransportHelper.this, attachment, msg );
					}
				},
				attachment );
	}

	@Override
	public void
	registerForWriteSelects(
		final selectListener		listener,
		Object						attachment )
	{
		TCPNetworkManager.getSingleton().getWriteSelector().register(
				channel,
				new VirtualSelectorListener()
				{
					@Override
					public boolean
					selectSuccess(
							VirtualChannelSelector	selector,
							SocketChannel			sc,
							Object 					attachment )
					{
						if ( trace ){
							TimeFormatter.milliTrace( "tcp: write select" );
						}

						return( listener.selectSuccess( TCPTransportHelper.this, attachment ));
					}

					@Override
					public void
					selectFailure(
							VirtualChannelSelector	selector,
							SocketChannel 			sc,
							Object 					attachment,
							Throwable 				msg)
					{
						listener.selectFailure( TCPTransportHelper.this, attachment, msg );
					}
				},
				attachment );
	}

	@Override
	public void
	cancelReadSelects()
	{
		TCPNetworkManager.getSingleton().getReadSelector().cancel( channel );
	}

	@Override
	public void
	cancelWriteSelects()
	{
		if ( trace ){
			TimeFormatter.milliTrace( "tcp: cancel write selects" );
		}

		TCPNetworkManager.getSingleton().getWriteSelector().cancel( channel );
	}

	@Override
	public void
	resumeReadSelects()
	{
		TCPNetworkManager.getSingleton().getReadSelector().resumeSelects( channel );
	}

	@Override
	public void
	resumeWriteSelects()
	{
		if ( trace ){
			TimeFormatter.milliTrace( "tcp: resume write selects" );
		}

		TCPNetworkManager.getSingleton().getWriteSelector().resumeSelects( channel );
	}

	@Override
	public void
	pauseReadSelects()
	{
		TCPNetworkManager.getSingleton().getReadSelector().pauseSelects( channel );
	}

	@Override
	public void
	pauseWriteSelects()
	{
		if ( trace ){
			TimeFormatter.milliTrace( "tcp: pause write selects" );
		}

		TCPNetworkManager.getSingleton().getWriteSelector().pauseSelects( channel );
	}

	@Override
	public boolean
	isClosed()
	{
		return( closed );
	}

	@Override
	public void
	close( String reason )
	{
		closed = true;

		TCPNetworkManager.getSingleton().getReadSelector().cancel( channel );
		TCPNetworkManager.getSingleton().getWriteSelector().cancel( channel );
		TCPNetworkManager.getSingleton().getConnectDisconnectManager().closeConnection( channel );
	}

	@Override
	public void
	failed(
		Throwable	reason )
	{
		close( Debug.getNestedExceptionMessage( reason ));
	}

	public SocketChannel getSocketChannel(){  return channel; }

	@Override
	public synchronized void
	setUserData(
		Object	key,
		Object	data )
	{
		if ( user_data == null ){

			user_data = new HashMap();
		}

		user_data.put( key, data );
	}

	@Override
	public synchronized Object
	getUserData(
		Object	key )
	{
		if ( user_data == null ){

			return(null);

		}

		return( user_data.get( key ));
	}

	/*
	protected void
	log(
		ByteBuffer[] 	buffers,
		int 			array_offset,
		int 			length )
	{
		System.out.println( "Writing group of " + length );

		for( int i=array_offset; i < (array_offset + length); i++ ) {

			log( buffers[i]);
		}
	}

	protected void
	log(
		ByteBuffer	buffer )
	{
		int position = buffer.position();

		int	rem = buffer.remaining();

		byte[]	temp = new byte[rem>64?64:rem];

		buffer.get( temp );

		buffer.position( position );

		System.out.println( "    writing " + rem + ": " + new String(temp));
	}
	*/

	@Override
	public void
	setTrace(
		boolean	on )
	{
		trace	= on;
	}

	@Override
	public void setScatteringMode(long forBytes) {
		if(forBytes > 0)
		{
			if(remainingBytesToScatter == 0)
				try
			{
				channel.socket().setTcpNoDelay(true);
			} catch (SocketException e)
			{
				//Debug.printStackTrace(e);
			}
			remainingBytesToScatter = forBytes;
		} else
		{
			if(remainingBytesToScatter > 0)
				try
			{
				channel.socket().setTcpNoDelay(false);
			} catch (SocketException e)
			{
				//Debug.printStackTrace(e);
			}
			remainingBytesToScatter = 0;
		}


	}
}
