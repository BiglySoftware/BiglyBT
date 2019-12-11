/*
 * Created on 1 Nov 2006
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


package com.biglybt.core.networkmanager.admin.impl;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.networkmanager.ProtocolEndpoint;
import com.biglybt.core.networkmanager.ProtocolEndpointFactory;
import com.biglybt.core.networkmanager.admin.NetworkAdminException;
import com.biglybt.core.networkmanager.admin.NetworkAdminSocksProxy;
import com.biglybt.core.networkmanager.impl.tcp.*;
import com.biglybt.core.util.AESemaphore;
import com.biglybt.core.versioncheck.VersionCheckClient;

public class
NetworkAdminSocksProxyImpl
	implements NetworkAdminSocksProxy
{
	private final String	TARGET_HOST	= VersionCheckClient.HTTP_SERVER_ADDRESS_V4;
	private final int		TARGET_PORT	= VersionCheckClient.HTTP_SERVER_PORT;

	private final String		host;
	private final String		port;

	final String		user;
	final String		password;

	protected
	NetworkAdminSocksProxyImpl(
		String		_host,
		String		_port,
		String		_user,
		String		_password )
	{
		host		= _host;
		port		= _port;
		user		= _user;
		password	= _password;
 	}

	protected boolean
	isConfigured()
	{
		return( host.length() > 0 );
	}

	@Override
	public String
	getName()
	{
		return( host + ":" + port );
	}

	@Override
	public String
	getHost()
	{
		return( host );
	}

	@Override
	public String
	getPort()
	{
		return( port );
	}

	@Override
	public String
	getUser()
	{
		return( user );
	}

	protected boolean
	sameAs(
		NetworkAdminSocksProxyImpl	other )
	{
		return(	host.equals( other.host ) && 
				port.equals( other.port ) && 
				user.equals( other.user ) &&
				password.equals( other.password ));
	}
	
	@Override
	public String[]
	getVersionsSupported()

		throws NetworkAdminException
	{
		NetworkAdminException	failure = null;

		List	versions = new ArrayList();

		try{
			testVersion( "V4" );

			versions.add( "4" );

		}catch( NetworkAdminException e ){

			failure = e;
		}

		try{
			testVersion( "V4a" );

			versions.add( "4a" );

		}catch( NetworkAdminException e ){

			failure = e;
		}

		try{
			testVersion( "V5" );

			versions.add( "5" );

		}catch( NetworkAdminException e ){

			failure = e;
		}

		if ( versions.size() > 0 ){

			return((String[])versions.toArray( new String[versions.size()]));

		}

		throw( failure );
	}

	@Override
	public String
	getString()
	{
		String res = getName();

		if ( user.length() > 0 ){

			res += " [auth=" + user + "]";
		}

		res += ", versions=";

		try{
			String[] versions = getVersionsSupported();

			for (int j=0;j<versions.length;j++){

				res += (j==0?"":",") + versions[j];
			}

		}catch( NetworkAdminException e ){

			res += "unknown (" + e.getLocalizedMessage() + ")";
		}

		return( res );
	}

	protected void
	testVersion(
		final String	version )

		throws NetworkAdminException
	{
		final int RES_CONNECT_FAILED	= 0;
		final int RES_SOCKS_FAILED		= 1;
		final int RES_OK				= 3;

		final AESemaphore	sem = new AESemaphore( "NetworkAdminSocksProxy:test" );

		final int[]	result = { RES_CONNECT_FAILED };

		final NetworkAdminException[]	error = { null };

		try{
			InetSocketAddress		socks_address = new InetSocketAddress( InetAddress.getByName( host ), Integer.parseInt(port));

			final InetSocketAddress	target_address = new InetSocketAddress( TARGET_HOST, TARGET_PORT );

			TCPConnectionManager.ConnectListener connect_listener =
				new TCPConnectionManager.ConnectListener()
			{
				@Override
				public int
				connectAttemptStarted(
					int default_connect_timeout )
				{
					return( default_connect_timeout );
				}

				@Override
				public void
				connectSuccess(
					SocketChannel channel )
				{
					final TCPTransportImpl	transport =
						new TCPTransportImpl(
								(ProtocolEndpointTCP)ProtocolEndpointFactory.createEndpoint( ProtocolEndpoint.PROTOCOL_TCP, target_address ), false, false, null );

					transport.setFilter( TCPTransportHelperFilterFactory.createTransparentFilter( channel ));

					new ProxyLoginHandler(
							transport,
							target_address,
							new ProxyLoginHandler.ProxyListener()
							{
								@Override
								public void
								connectSuccess()
								{
									transport.close( "Done" );

									result[0] 	= RES_OK;

									sem.release();
								}

								@Override
								public void
								connectFailure(
									Throwable failure_msg )
								{
									transport.close( "Proxy login failed" );

									result[0] 	= RES_SOCKS_FAILED;
									error[0]	= new NetworkAdminException( "Proxy connect failed", failure_msg );

									sem.release();
								}
							},
							version,
							user,
							password );
				}

				@Override
				public void
				connectFailure(
					Throwable failure_msg )
				{
					result[0] 	= RES_CONNECT_FAILED;
					error[0]	= new NetworkAdminException( "Connect failed", failure_msg );

					sem.release();
				}
				
				@Override
				public Object 
				getConnectionProperty(
					String property_name)
				{
					return( null );
				}
			};

			TCPNetworkManager.getSingleton().getConnectDisconnectManager().requestNewConnection(
					socks_address, null, connect_listener, ProtocolEndpoint.CONNECT_PRIORITY_MEDIUM );

		}catch( Throwable e ){

			result[0] 	= RES_CONNECT_FAILED;
			error[0]	= new NetworkAdminException( "Connect failed", e );

			sem.release();
		}

		if ( !sem.reserve(10000)){

			result[0] 	= RES_CONNECT_FAILED;
			error[0] 	= new NetworkAdminException( "Connect timeout" );
		}

		if ( result[0] != RES_OK ){

			throw( error[0] );
		}
	}
}
