/*
 * Created on 17-Jan-2006
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

package com.biglybt.core.networkmanager.impl.test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.networkmanager.VirtualChannelSelector;
import com.biglybt.core.networkmanager.VirtualChannelSelector.VirtualSelectorListener;
import com.biglybt.core.networkmanager.VirtualServerChannelSelector;
import com.biglybt.core.networkmanager.VirtualServerChannelSelectorFactory;
import com.biglybt.core.networkmanager.impl.*;
import com.biglybt.core.networkmanager.impl.tcp.TCPNetworkManager;
import com.biglybt.core.networkmanager.impl.tcp.TCPTransportHelper;
import com.biglybt.core.util.AEDiagnostics;
import com.biglybt.core.util.Debug;

public class
PHETester
{
	final VirtualChannelSelector connect_selector = new VirtualChannelSelector( "PHETester", VirtualChannelSelector.OP_CONNECT, true );

	final byte[]	TEST_HEADER	= "TestHeader".getBytes();

	private static final boolean	OUTGOING_PLAIN = false;

	private static final byte[]	shared_secret = "sdsjdksjdkj".getBytes();

	public
	PHETester()
	{
		ProtocolDecoder.addSecrets( "PHETester", new byte[][]{ shared_secret });

		VirtualServerChannelSelector
			accept_server = VirtualServerChannelSelectorFactory.createNonBlocking(
					new InetSocketAddress( 8765 ),
					0,
					new VirtualServerChannelSelector.SelectListener()
					{
						@Override
						public void
						newConnectionAccepted(
							ServerSocketChannel	server,
							SocketChannel 		channel )
						{
							incoming( channel );
						}
					});

		accept_server.startProcessing();

		new Thread()
		{
			@Override
			public void
			run()
			{
				while( true ){
					try{
						connect_selector.select( 100 );
					}
					catch( Throwable t ) {
					  Debug.out( "connnectSelectLoop() EXCEPTION: ", t );
					}
				}
			}
		}.start();

		outgoings();
	}

	protected void
	incoming(
		SocketChannel	channel )
	{
		try{
			TransportHelper	helper = new TCPTransportHelper( channel );

			final ProtocolDecoderInitial	decoder =
				new ProtocolDecoderInitial(
						helper,
						null,
						false,
						null,
						new ProtocolDecoderAdapter()
						{
							@Override
							public void
							decodeComplete(
								ProtocolDecoder	decoder,
								ByteBuffer		remaining_initial_data )
							{
								System.out.println( "incoming decode complete: " +  decoder.getFilter().getName(false));

								readStream( "incoming", decoder.getFilter() );

								writeStream( "ten fat monkies", decoder.getFilter() );
							}

							@Override
							public void
							decodeFailed(
								ProtocolDecoder	decoder,
								Throwable			cause )
							{
								System.out.println( "incoming decode failed: " + Debug.getNestedExceptionMessage(cause));
							}

							@Override
							public void
							gotSecret(
								byte[]				session_secret )
							{
							}

							@Override
							public int
							getMaximumPlainHeaderLength()
							{
								return( TEST_HEADER.length );
							}

							@Override
							public int
							matchPlainHeader(
								ByteBuffer			buffer )
							{
								int	pos = buffer.position();
								int lim = buffer.limit();

								buffer.flip();

								boolean	match = buffer.compareTo( ByteBuffer.wrap( TEST_HEADER )) == 0;

								buffer.position( pos );
								buffer.limit( lim );

								System.out.println( "Match - " + match );

								return( match?ProtocolDecoderAdapter.MATCH_CRYPTO_NO_AUTO_FALLBACK:ProtocolDecoderAdapter.MATCH_NONE );
							}
						});
		}catch( Throwable e ){

			e.printStackTrace();
		}
	}

	protected void
	outgoings()
	{
		while( true ){

			outgoing();

			try{
				Thread.sleep(1000000);

			}catch( Throwable e ){

			}
		}
	}

	protected void
	outgoing()
	{
		try{
			final SocketChannel	channel = SocketChannel.open();

			try{
				channel.configureBlocking( false );

			}catch( IOException e ){

				channel.close();

				throw( e );
			}

			if ( channel.connect( new InetSocketAddress("localhost", 8765 ))){

				outgoing( channel );

			}else{

				connect_selector.register(
					channel,
					new VirtualSelectorListener()
					{
						@Override
						public boolean
						selectSuccess(
							VirtualChannelSelector selector, SocketChannel sc, Object attachment)
						{
							try{
								if ( channel.finishConnect()){

									outgoing( channel );

									return( true );
								}else{

									throw( new IOException( "finishConnect failed" ));
								}
							}catch( Throwable e ){

								e.printStackTrace();

								return( false );
							}
						}

						@Override
						public void
						selectFailure(
								VirtualChannelSelector selector, SocketChannel sc, Object attachment, Throwable msg)
						{
							msg.printStackTrace();
						}

					},
					null );
			}
		}catch( Throwable e ){

			e.printStackTrace();
		}
	}

	protected void
	outgoing(
		SocketChannel	channel )
	{
		try{

			if ( OUTGOING_PLAIN ){

				writeStream( TEST_HEADER,  channel);

				writeStream( "two jolly porkers".getBytes(), channel );

			}else{
				TransportHelper	helper = new TCPTransportHelper( channel );

				final ProtocolDecoderInitial decoder =
					new ProtocolDecoderInitial(
						helper,
						new byte[][]{ shared_secret },
						true,
						null,
						new ProtocolDecoderAdapter()
						{
							@Override
							public void
							decodeComplete(
								ProtocolDecoder	decoder,
								ByteBuffer		remaining_initial_data )
							{
								System.out.println( "outgoing decode complete: " +  decoder.getFilter().getName(false));

								readStream( "incoming", decoder.getFilter() );

								writeStream( TEST_HEADER,  decoder.getFilter());

								writeStream( "two jolly porkers", decoder.getFilter() );
							}

							@Override
							public void
							decodeFailed(
								ProtocolDecoder	decoder,
								Throwable			cause )
							{
								System.out.println( "outgoing decode failed: " + Debug.getNestedExceptionMessage(cause));

							}

							@Override
							public void
							gotSecret(
								byte[]				session_secret )
							{
							}

							@Override
							public int
							getMaximumPlainHeaderLength()
							{
								throw( new RuntimeException());
							}

							@Override
							public int
							matchPlainHeader(
								ByteBuffer			buffer )
							{
								throw( new RuntimeException());
							}
						});
			}
		}catch( Throwable e ){

			e.printStackTrace();
		}
	}

	protected void
	readStream(
		final String					str,
		final TransportHelperFilter	filter )
	{
		try{
			TCPNetworkManager.getSingleton().getReadSelector().register(
				((TCPTransportHelper)filter.getHelper()).getSocketChannel(),
				new VirtualSelectorListener()
				{
					@Override
					public boolean
					selectSuccess(
						VirtualChannelSelector selector, SocketChannel sc, Object attachment)
					{
						ByteBuffer	buffer = ByteBuffer.allocate(1024);

						try{
							long	len = filter.read( new ByteBuffer[]{ buffer }, 0, 1 );

							byte[]	data = new byte[buffer.position()];

							buffer.flip();

							buffer.get( data );

							System.out.println( str + ": " + new String(data));

							return( len > 0 );

						}catch( Throwable e ){

							e.printStackTrace();

							return( false );
						}
					}

					@Override
					public void
					selectFailure(
							VirtualChannelSelector selector, SocketChannel sc, Object attachment, Throwable msg)
					{
						msg.printStackTrace();
					}
				},
				null );

		}catch( Throwable e ){

			e.printStackTrace();
		}
	}

	protected void
	writeStream(
		String						str,
		TransportHelperFilter	filter )
	{
		writeStream( str.getBytes(), filter );
	}

	protected void
	writeStream(
		byte[]						data,
		TransportHelperFilter	filter )
	{
		try{
			filter.write( new ByteBuffer[]{ ByteBuffer.wrap(data)}, 0, 1 );

		}catch( Throwable e ){

			e.printStackTrace();
		}
	}

	protected void
	writeStream(
		byte[]						data,
		SocketChannel				channel )
	{
		try{
			channel.write( new ByteBuffer[]{ ByteBuffer.wrap(data)}, 0, 1 );

		}catch( Throwable e ){

			e.printStackTrace();
		}
	}

	public static void
	main(
		String[]	args )
	{
		AEDiagnostics.startup( false );

		// OUTGOING_PLAIN	= true;

		COConfigurationManager.setParameter( "network.transport.encrypted.require", true );
		COConfigurationManager.setParameter( "network.transport.encrypted.min_level", "Plain" );

		new PHETester();

		try{
			Thread.sleep(10000000);

		}catch( Throwable e ){

		}
	}
}
