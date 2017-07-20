/*
 * Created on 13-Dec-2004
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

package com.biglybt.core.proxy.socks.impl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.proxy.AEProxyConnection;
import com.biglybt.core.proxy.socks.AESocksProxy;
import com.biglybt.core.proxy.socks.AESocksProxyAddress;
import com.biglybt.core.proxy.socks.AESocksProxyConnection;
import com.biglybt.core.proxy.socks.AESocksProxyPlugableConnection;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DirectByteBuffer;
import com.biglybt.core.util.DirectByteBufferPool;
import com.biglybt.core.util.HostNameToIPResolver;


/**
 * @author parg
 *
 */

public class
AESocksProxyPlugableConnectionDefault
	implements AESocksProxyPlugableConnection
{
	protected final AESocksProxyConnection	socks_connection;
	protected final AEProxyConnection			connection;

	protected final SocketChannel		source_channel;
	protected SocketChannel		target_channel;

	protected proxyStateRelayData	relay_data_state;

	public
	AESocksProxyPlugableConnectionDefault(
		AESocksProxyConnection		_socks_connection )
	{
		socks_connection	= _socks_connection;
		connection			= socks_connection.getConnection();

		source_channel	= connection.getSourceChannel();
	}

	@Override
	public String
	getName()
	{
		if ( target_channel != null ){

			return( target_channel.socket().getInetAddress() + ":" + target_channel.socket().getPort());
		}

		return( "" );
	}

	@Override
	public InetAddress
	getLocalAddress()
	{
		return( target_channel.socket().getInetAddress());
	}

	@Override
	public int
	getLocalPort()
	{
		return( target_channel.socket().getPort());
	}

	@Override
	public void
	connect(
		AESocksProxyAddress		_address )

		throws IOException
	{
		InetAddress address = _address.getAddress();

		if ( address == null ){

			if ( socks_connection.areDNSLookupsEnabled()){

				try{
					address = HostNameToIPResolver.syncResolve( _address.getUnresolvedAddress());

				}catch( Throwable e ){
				}
			}

			if ( address == null ){

				throw( new IOException( "DNS lookup of '" + _address.getUnresolvedAddress() + "' fails" ));
			}
		}

		new proxyStateRelayConnect( new InetSocketAddress( address, _address.getPort()));
	}

	@Override
	public void
	relayData()

		throws IOException
	{
		new proxyStateRelayData();
	}

	@Override
	public void
	close()
	{
		if ( target_channel != null ){

			try{
				connection.cancelReadSelect( target_channel );
				connection.cancelWriteSelect( target_channel );

				target_channel.close();

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}

		if ( relay_data_state != null ){

			relay_data_state.destroy();
		}
	}


	protected class
	proxyStateRelayConnect
		extends AESocksProxyState
	{
		protected final InetSocketAddress	address;

		protected
		proxyStateRelayConnect(
			InetSocketAddress	_address )

			throws IOException
		{
			super( socks_connection );

			address			= _address;

				// OK, we're almost ready to roll. Unregister the read select until we're
				// connected

			connection.cancelReadSelect( source_channel );

			connection.setConnectState( this );

			target_channel = SocketChannel.open();

		    InetAddress bindIP = NetworkAdmin.getSingleton().getMultiHomedOutgoingRoundRobinBindAddress(address.getAddress());

	        if ( bindIP != null ){

	        	try{
	        		target_channel.socket().bind( new InetSocketAddress( bindIP, 0 ) );

	        	}catch( IOException e ){

	        		if ( bindIP.isAnyLocalAddress()){

	        			// no point in moaning here about this

	        		}else{

		        			// if the address is unresolved then the calculated bind address can be invalid
		        			// (might pick an IPv6 address for example when this is unbindable). In this case
		        			// carry on and attempt to connect as this will fail anyway

		        		if ( ! ( e.getMessage().contains( "not supported" ) && address.isUnresolved())){

		        			throw( e );
		        		}
	        		}
	        	}
	        }

	        target_channel.configureBlocking( false );

	        target_channel.connect( address );

	        connection.requestConnectSelect( target_channel );
		}

		@Override
		protected boolean
		connectSupport(
			SocketChannel 		sc )

			throws IOException
		{
			if( !sc.finishConnect()){

				throw( new IOException( "finishConnect returned false" ));
			}

				// if we've got a proxy chain, now's the time to negotiate the connection

			AESocksProxy	proxy = socks_connection.getProxy();

			if ( proxy.getNextSOCKSProxyHost() != null ){

			}

			socks_connection.connected();

			return( true );
		}
	}

	protected class
	proxyStateRelayData
		extends AESocksProxyState
	{
		protected DirectByteBuffer		source_buffer;
		protected DirectByteBuffer		target_buffer;

		protected long				outward_bytes	= 0;
		protected long				inward_bytes	= 0;

		protected
		proxyStateRelayData()

			throws IOException
		{
			super( socks_connection );

			source_buffer	= DirectByteBufferPool.getBuffer( DirectByteBuffer.AL_PROXY_RELAY, 1024 );
			target_buffer	= DirectByteBufferPool.getBuffer( DirectByteBuffer.AL_PROXY_RELAY, 1024 );

			relay_data_state	= this;

			if ( connection.isClosed()){

				destroy();

				throw( new IOException( "connection closed" ));
			}

			connection.setReadState( this );

			connection.setWriteState( this );

			connection.requestReadSelect( source_channel );

			connection.requestReadSelect( target_channel );

			connection.setConnected();
		}

		protected void
		destroy()
		{
			if ( source_buffer != null ){

				source_buffer.returnToPool();

				source_buffer	= null;
			}

			if ( target_buffer != null ){

				target_buffer.returnToPool();

				target_buffer	= null;
			}
		}

		@Override
		protected boolean
		readSupport(
			SocketChannel 		sc )

			throws IOException
		{
			connection.setTimeStamp();

			SocketChannel	chan1 = sc;
			SocketChannel	chan2 = sc==source_channel?target_channel:source_channel;

			DirectByteBuffer	read_buffer = sc==source_channel?source_buffer:target_buffer;

			int	len = read_buffer.read( DirectByteBuffer.SS_PROXY, chan1 );

			if ( len == -1 ){

					//means that the channel has been shutdown

				connection.close();

			}else{

				if ( read_buffer.position( DirectByteBuffer.SS_PROXY ) > 0 ){

					read_buffer.flip(DirectByteBuffer.SS_PROXY);

					int	written = read_buffer.write( DirectByteBuffer.SS_PROXY, chan2 );

					if ( chan1 == source_channel ){

						outward_bytes += written;

					}else{

						inward_bytes += written;
					}

					if ( read_buffer.hasRemaining(DirectByteBuffer.SS_PROXY)){

						connection.cancelReadSelect( chan1 );

						connection.requestWriteSelect( chan2 );

					}else{

						read_buffer.position(DirectByteBuffer.SS_PROXY, 0);

						read_buffer.limit( DirectByteBuffer.SS_PROXY, read_buffer.capacity(DirectByteBuffer.SS_PROXY));
					}
				}
			}

			return( len > 0 );
		}

		@Override
		protected boolean
		writeSupport(
			SocketChannel 		sc )

			throws IOException
		{
				// socket SX -> SY via BX
				// so if SX = source_channel then BX is target buffer

			SocketChannel	chan1 = sc;
			SocketChannel	chan2 = sc==source_channel?target_channel:source_channel;

			DirectByteBuffer	read_buffer = sc==source_channel?target_buffer:source_buffer;

			int written = read_buffer.write( DirectByteBuffer.SS_PROXY, chan1 );

			if ( chan1 == target_channel ){

				outward_bytes += written;

			}else{

				inward_bytes += written;
			}

			if ( read_buffer.hasRemaining(DirectByteBuffer.SS_PROXY)){

				connection.requestWriteSelect( chan1 );

			}else{

				read_buffer.position(DirectByteBuffer.SS_PROXY,0);

				read_buffer.limit( DirectByteBuffer.SS_PROXY, read_buffer.capacity(DirectByteBuffer.SS_PROXY));

				connection.requestReadSelect( chan2 );
			}

			return( written > 0 );
		}

		@Override
		public String
		getStateName()
		{
			String	state = this.getClass().getName();

			int	pos = state.indexOf( "$");

			state = state.substring(pos+1);

			return( state  +" [out=" + outward_bytes +",in=" + inward_bytes +"] " + source_buffer + " / " + target_buffer );
		}
	}
}
