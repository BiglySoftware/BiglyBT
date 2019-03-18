/*
 * Created on 02-Jan-2005
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

package com.biglybt.core.tracker.server.impl.tcp.nonblocking;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import com.biglybt.core.networkmanager.VirtualChannelSelector;
import com.biglybt.core.proxy.AEProxyAddressMapper;
import com.biglybt.core.proxy.AEProxyFactory;
import com.biglybt.core.tracker.server.impl.tcp.TRTrackerServerProcessorTCP;
import com.biglybt.core.tracker.server.impl.tcp.TRTrackerServerTCP;
import com.biglybt.core.util.AESemaphore;
import com.biglybt.core.util.AsyncController;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemTime;

/**
 * @author parg
 *
 */

public abstract class
TRNonBlockingServerProcessor
	extends TRTrackerServerProcessorTCP
{
	private static final int MAX_POST = 256*1024;

	private static final int			READ_BUFFER_INITIAL		= 1024;
	private static final int			READ_BUFFER_INCREMENT	= 1024;
	private static final int			READ_BUFFER_LIMIT		= 32*1024;	// needs to be reasonable size to handle scrapes with plugin generated per-hash content

	private static final AEProxyAddressMapper proxy_address_mapper = AEProxyFactory.getAddressMapper();

	  
	private final SocketChannel				socket_channel;

	private VirtualChannelSelector.VirtualSelectorListener 	read_listener;
	private VirtualChannelSelector.VirtualSelectorListener 	write_listener;

	private long						start_time;

	private ByteBuffer					read_buffer;
	private ByteBuffer					post_data_buffer;

	private String						request_header;
	private String						lc_request_header;

	private ByteBuffer					write_buffer;

	private boolean						keep_alive;

	protected
	TRNonBlockingServerProcessor(
		TRTrackerServerTCP		_server,
		SocketChannel			_socket )
	{
		super( _server );

		socket_channel		= _socket;

		start_time	= SystemTime.getCurrentTime();

		read_buffer = ByteBuffer.allocate( READ_BUFFER_INITIAL );

		// System.out.println( "create: " + System.currentTimeMillis());
	}

	protected void
	setReadListener(
		VirtualChannelSelector.VirtualSelectorListener		rl )
	{
		read_listener	= rl;
	}

	protected VirtualChannelSelector.VirtualSelectorListener
	getReadListener()
	{
		return( read_listener );
	}

	protected void
	setWriteListener(
		VirtualChannelSelector.VirtualSelectorListener 		wl )
	{
		write_listener	= wl;
	}

	protected VirtualChannelSelector.VirtualSelectorListener
	getWriteListener()
	{
		return( write_listener );
	}

		// 0 -> complete
		// 1 -> more to do
		// 2 -> no progress
		// -1 -> error

	protected int
	processRead()
	{
		if ( post_data_buffer != null ){

			try{
				int len = socket_channel.read( post_data_buffer );

				if ( len < 0 ){

					return( -1 );
				}

				if ( post_data_buffer.remaining() == 0 ){

					post_data_buffer.flip();

					getServer().runProcessor( this );

					return( 0 );

				}else{

					return( 1 );
				}
			}catch( IOException e ){

				return( -1 );
			}
		}

		if ( read_buffer.remaining() == 0 ){

			int	capacity = read_buffer.capacity();

			if ( capacity == READ_BUFFER_LIMIT ){

				return( -1 );

			}else{

				read_buffer.position(0);

				byte[]	data = new byte[capacity];

				read_buffer.get( data );

				read_buffer = ByteBuffer.allocate( capacity + READ_BUFFER_INCREMENT );

				read_buffer.put( data );
			}
		}

		try{
			int	len = socket_channel.read( read_buffer );

			// System.out.println( "read op[" + len + "]: " + System.currentTimeMillis());


			if ( len < 0 ){

				return( -1 );

			}else if ( len == 0 ){

				return( 2 );	// no progress
			}

			byte[]	data = read_buffer.array();

			int array_offset		= read_buffer.arrayOffset();
			int	array_position 		= array_offset + read_buffer.position();

			for (int i=array_offset;i<=array_position-4;i++){

				if ( 	data[i]   == CR &&
						data[i+1] == FF &&
						data[i+2] == CR &&
						data[i+3] == FF ){

					int	header_end 		= i + 4;
					int	header_length 	= header_end - array_offset;

					request_header 		= new String( data, array_offset, header_length );
					lc_request_header 	= request_header.toLowerCase();

					int	rem = array_position - header_end;

					if ( rem == 0 ){

						read_buffer = ByteBuffer.allocate( READ_BUFFER_INITIAL );

					}else{

						read_buffer = ByteBuffer.allocate( rem + READ_BUFFER_INCREMENT );

						read_buffer.put( data, header_end, rem );
					}

					post_data_buffer = null;

					int	pos1 = lc_request_header.indexOf( "content-length" );

					if ( pos1 == -1 ){

						if ( 	lc_request_header.contains( "transfer-encoding" ) &&
								lc_request_header.contains( "chunked" )){

							Debug.out( "Chunked transfer-encoding not supported!!!!" );
						}
					}else{

						int pos2 = lc_request_header.indexOf( NL, pos1 );

						String entry;

						if ( pos2 == -1 ){

							entry = lc_request_header.substring( pos1 );

						}else{

							entry = lc_request_header.substring( pos1, pos2 );
						}

						int	pos = entry.indexOf(':');

						if ( pos != -1 ){

							int content_length = 0;

							try{
								content_length = Integer.parseInt( entry.substring( pos+1 ).trim());

							}catch( Throwable e ){
							}

							if ( content_length > 0 ){

								if ( content_length > MAX_POST ){

									throw( new IOException( "content-length too large, max=" + MAX_POST ));
								}

								post_data_buffer = ByteBuffer.allocate( content_length );

								int buffer_position = read_buffer.position();

								if ( buffer_position > 0 ){

									byte[] already_read = new byte[Math.min( buffer_position, content_length )];

									read_buffer.flip();

									read_buffer.get( already_read );

									byte[] xrem = new byte[ read_buffer.remaining()];

									read_buffer.get( xrem );

									read_buffer = ByteBuffer.allocate( xrem.length + READ_BUFFER_INCREMENT );

									read_buffer.put( xrem );

									post_data_buffer.put( already_read );

									if ( post_data_buffer.remaining() == 0 ){

										getServer().runProcessor( this );

										return( 0 );
									}
								}
							}
						}
					}

					if ( post_data_buffer == null ){

						// System.out.println( "read done: " + System.currentTimeMillis());

						getServer().runProcessor( this );

						return( 0 );

					}else{

						return( 1 );
					}
				}
			}

			return( 1 );

		}catch( IOException e ){

			return( -1 );
		}
	}

		// 0 -> complete
		// 1 -> more to do
		// 2 -> no progress made
		// -1 -> error

	protected int
	processWrite()
	{
		if ( write_buffer == null ){

			return( -1 );
		}

		if ( !write_buffer.hasRemaining()){

			writeComplete();

			return( 0 );
		}

		try{
			int	written = socket_channel.write( write_buffer );

			if ( written == 0 ){

				return( 2 );
			}

			if ( write_buffer.hasRemaining()){

				return( 1 );
			}

			writeComplete();

			return( 0 );

		}catch( IOException e ){

			return( -1 );
		}
	}

	@Override
	public void
	runSupport()
	{
		boolean	async = false;

		try{
			String	url = request_header.substring(request_header.indexOf(' ')).trim();

			int	pos = url.indexOf( " " );

			url = url.substring(0,pos);

			final AESemaphore[]				went_async 		= { null };
			final ByteArrayOutputStream[]	async_stream	= { null };

			AsyncController	async_control =
				new AsyncController()
				{
					@Override
					public void
					setAsyncStart()
					{
						went_async[0] = new AESemaphore( "async" );
					}

					@Override
					public void
					setAsyncComplete()
					{
						went_async[0].reserve();

						asyncProcessComplete( async_stream[0] );
					}
				};

			try{
				InetSocketAddress remote_sa = (InetSocketAddress)socket_channel.socket().getRemoteSocketAddress();
				
				AEProxyAddressMapper.AppliedPortMapping applied_mapping = proxy_address_mapper.applyPortMapping( remote_sa.getAddress(), remote_sa.getPort());

				remote_sa = applied_mapping.getRemoteAddress();
			    
				ByteArrayOutputStream	response =
					process( 	request_header,
								lc_request_header,
								url,
								remote_sa,
								getServer().getRestrictNonBlocking(),
								new ByteArrayInputStream(new byte[0]),
								async_control );

					// two ways of going async
					//	1) return is null and something else will call asyncProcessComplete later
					//	2) return is 'not-yet-filled' os and async controller is managing things

				if ( response == null ){

					async = true;

				}else if ( went_async[0] != null ){

					async_stream[0] = response;

					async = true;

				}else{

					write_buffer = ByteBuffer.wrap( response.toByteArray());
				}
			}finally{

				if ( went_async[0] != null ){

					went_async[0].release();
				}
			}
		}catch( Throwable e ){


		}finally{

			if ( !async ){

				((TRNonBlockingServer)getServer()).readyToWrite( this );
			}
		}
	}

	protected abstract ByteArrayOutputStream
	process(
		String				input_header,
		String				lowercase_input_header,
		String				url_path,
		InetSocketAddress	client_address,
		boolean				announce_and_scrape_only,
		InputStream			is,
		AsyncController		async )

		throws IOException;

	protected void
	asyncProcessComplete(
		ByteArrayOutputStream	response )
	{
		write_buffer = ByteBuffer.wrap( response.toByteArray());

		((TRNonBlockingServer)getServer()).readyToWrite( this );
	}

	protected SocketChannel
	getSocketChannel()
	{
		return( socket_channel );
	}

	protected byte[]
	getPostData()
	{
		ByteBuffer result = post_data_buffer;

		if ( result == null ){

			return( null );
		}

		post_data_buffer = null;

		return( result.array());
	}

	protected long
	getStartTime()
	{
		return( start_time );
	}

	protected boolean
	getKeepAlive()
	{
		return( keep_alive );
	}

	protected void
	setKeepAlive(
		boolean	k )
	{
		keep_alive	= k;
	}

	@Override
	public boolean
	isActive()
	{
		return( !socket_channel.socket().isClosed());
	}

	@Override
	public void
	interruptTask()
	{
	}

		// overridden if subclass is interested in failures, so don't remove!

	protected void
	failed()
	{
	}

	protected void
	writeComplete()
	{
		if ( keep_alive ){

			// reset timer at end of current request ready for the next one

			start_time	= SystemTime.getCurrentTime();
		}
	}

	protected void
	completed()
	{
		//System.out.println( "complete: " + System.currentTimeMillis());
	}

	protected void
	closed()
	{
		//System.out.println( "close: " + System.currentTimeMillis());
	}
}
