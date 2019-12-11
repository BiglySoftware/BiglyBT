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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.StringTokenizer;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.networkmanager.ProtocolEndpoint;
import com.biglybt.core.networkmanager.ProtocolEndpointFactory;
import com.biglybt.core.networkmanager.VirtualChannelSelector;
import com.biglybt.core.networkmanager.admin.NetworkAdminException;
import com.biglybt.core.networkmanager.admin.NetworkAdminHTTPProxy;
import com.biglybt.core.networkmanager.impl.tcp.*;
import com.biglybt.core.util.AESemaphore;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.versioncheck.VersionCheckClient;

public class
NetworkAdminHTTPProxyImpl
	implements NetworkAdminHTTPProxy
{
	private static final String	NL = "\015\012";

	private final String	TARGET_HOST	= VersionCheckClient.HTTP_SERVER_ADDRESS_V4;
	private final int		TARGET_PORT	= VersionCheckClient.HTTP_SERVER_PORT;

	private final String	http_host;
	private final String	http_port;
	private final String	https_host;
	private final String	https_port;

	private final String	user;
	private final String	password;

	private final String[]	non_proxy_hosts;

	protected
	NetworkAdminHTTPProxyImpl()
	{
        http_host	= System.getProperty("http.proxyHost", "" ).trim();
        http_port	= System.getProperty("http.proxyPort", "" ).trim();
        https_host	= System.getProperty("https.proxyHost", "" ).trim();
        https_port	= System.getProperty("https.proxyPort", "" ).trim();

        user		= System.getProperty("http.proxyUser", "" ).trim();
        password	= System.getProperty("http.proxyPassword", "" ).trim();

        String	nph = System.getProperty("http.nonProxyHosts", "" ).trim();

        StringTokenizer	tok = new StringTokenizer( nph, "|" );

        non_proxy_hosts = new String[tok.countTokens()];

        int	pos = 0;

        while( tok.hasMoreTokens()){

        	non_proxy_hosts[pos++] = tok.nextToken();
        }
	}

	@Override
	public String
	getName()
	{
		String	res = "";

		if ( http_host.length() > 0 ){

			res = "http=" + http_host + ":" + http_port;
		}

		if ( https_host.length() > 0 ){

			res += (res.length()==0?"":", " ) + "https=" + https_host + ":" + https_port;
		}

		return( res );
	}

	protected boolean
	isConfigured()
	{
		return( http_host.length() > 0 || https_host.length() > 0 );
	}

	@Override
	public String
	getHTTPHost()
	{
		return( http_host );
	}

	@Override
	public String
	getHTTPPort()
	{
		return( http_port );
	}

	@Override
	public String
	getHTTPSHost()
	{
		return( https_host );
	}

	@Override
	public String
	getHTTPSPort()
	{
		return( https_port );
	}

	@Override
	public String
	getUser()
	{
		return( user );
	}

	@Override
	public String[]
	getNonProxyHosts()
	{
		return( non_proxy_hosts );
	}

	@Override
	public String
	getString()
	{
		String res = getName();

		if ( user.length() > 0 ){

			res += " [auth=" + user + "]";
		}

		try{

			NetworkAdminHTTPProxy.Details details = getDetails();

			res += " server=" +  details.getServerName();
			res += ", response=" + details.getResponse();
			res += ", auth=" + details.getAuthenticationType();

		}catch( NetworkAdminException e ){

			res += " failed to query proxy - " + e.getLocalizedMessage();
		}

		return( res );
	}

	@Override
	public Details
	getDetails()

		throws NetworkAdminException
	{
		final int RES_CONNECT_FAILED	= 0;
		final int RES_PROXY_FAILED		= 1;
		final int RES_OK				= 3;

		final AESemaphore	sem = new AESemaphore( "NetworkAdminSocksProxy:test" );

		final int[]	result = { RES_CONNECT_FAILED };

		final NetworkAdminException[]		error = { null };
		final ProxyDetails[]				details = {null};

		try{
			InetSocketAddress		socks_address =
				new InetSocketAddress( InetAddress.getByName( http_host ), Integer.parseInt(http_port));

			final InetSocketAddress	target_address = new InetSocketAddress( TARGET_HOST, TARGET_PORT );

			TCPConnectionManager.ConnectListener connect_listener =
				new TCPConnectionManager.ConnectListener()
				{
					@Override
					public int
					connectAttemptStarted(
						int	default_connect_timeout )
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

						final long start_time = SystemTime.getCurrentTime();

						try{
							String	get_str = VersionCheckClient.getSingleton().getHTTPGetString( true, false );

							ByteBuffer	request = ByteBuffer.wrap( get_str.getBytes());

							while( request.hasRemaining()){

								if ( transport.write( new ByteBuffer[]{ request }, 0, 1 ) < 1 ) {

									if( SystemTime.getCurrentTime() - start_time > 30*1000 ) {

										String error = "proxy handshake message send timed out after 30sec";

										Debug.out( error );

										throw new IOException( error );
									}

									try{
										Thread.sleep( 50 );

									}catch( Throwable t ){

										t.printStackTrace();
									}
								}
							}

							TCPNetworkManager.getSingleton().getReadSelector().register(
								transport.getSocketChannel(),
								new VirtualChannelSelector.VirtualSelectorListener()
								{
									private final byte[]		reply_buffer = new byte[8192];

									private final ByteBuffer	reply = ByteBuffer.wrap( reply_buffer );

									@Override
									public boolean
									selectSuccess(
										VirtualChannelSelector 	selector,
										SocketChannel 			sc,
										Object 					attachment )
									{
										try{
											 if( SystemTime.getCurrentTime() - start_time > 30*1000 ){

												 throw( new Exception( "Timeout" ));
											 }

											 long len = transport.read( new ByteBuffer[]{ reply }, 0, 1 );

											 if ( len <= 0 ){

												 return( false );
											 }

											 String	str = new String( reply_buffer, 0, reply.position());

											 if (str.contains(NL + NL)){

												 System.out.println( str );

												 String	server_name = "unknown";
												 String	auth		= "none";
												 String	response	= "unknown";

												 StringTokenizer tok = new StringTokenizer( str, "\n" );

												 int	line_num = 0;

												 while( tok.hasMoreTokens()){

													 String	token = tok.nextToken().trim();

													 if ( token.length() == 0 ){

														 continue;
													 }

													 line_num++;

													 if ( line_num == 1 ){

														 int pos = token.indexOf(' ');

														 if ( pos != -1 ){

															 response = token.substring( pos + 1 ).trim();
														 }
													 }else{

														 int	pos = token.indexOf(':');

														 if ( pos != -1 ){

															 String	lhs = token.substring( 0, pos ).trim().toLowerCase( MessageText.LOCALE_ENGLISH );
															 String	rhs = token.substring( pos+1 ).trim();

															 if ( lhs.equals( "server" )){

																 if ( !response.startsWith( "200" )){

																	 server_name = rhs;
																 }
															 }else if ( lhs.equals( "via" )){

																 server_name = rhs;

																 int	p = server_name.indexOf(' ');

																 if ( p != -1 ){

																	 server_name = server_name.substring( p+1 ).trim();
																 }

															 }else if ( lhs.equals( "proxy-authenticate" )){

																 auth = rhs;
															 }
														 }
													 }
												 }

												 details[0] =
												 	new ProxyDetails(
													 		server_name,
													 		response,
													 		auth );

												 transport.close( "Done" );

												 result[0] 	= RES_OK;

												 sem.release();

											 }else{

												TCPNetworkManager.getSingleton().getReadSelector().resumeSelects( transport.getSocketChannel() );
											}

											 return( true );

										}
										catch( Throwable t ) {


											return false;
										}
									}

									@Override
									public void
									selectFailure(
										VirtualChannelSelector 	selector,
										SocketChannel 			sc,
										Object 					attachment,
										Throwable				msg )
									{
										result[0] 	= RES_PROXY_FAILED;
										error[0]	= new NetworkAdminException( "Proxy error", msg );

										transport.close( "Proxy error" );

										sem.release();
									}
								},
								null );

						}catch( Throwable t ) {

							result[0] 	= RES_PROXY_FAILED;
							error[0]	= new NetworkAdminException( "Proxy connect failed", t );

							sem.release();						}
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

		if ( result[0] == RES_OK ){

			return( details[0] );
		}

		throw( error[0] );
	}

	protected static class
	ProxyDetails
		implements Details
	{
		private final String	name;
		private final String	response;
		private final String	auth_type;

		protected
		ProxyDetails(
			String	_name,
			String	_response,
			String	_auth_type )
		{
			name		= _name;
			response	= _response;
			auth_type	= _auth_type;
		}

		@Override
		public String
		getServerName()
		{
			return( name );
		}

		@Override
		public String
		getResponse()
		{
			return( response );
		}

		@Override
		public String
		getAuthenticationType()
		{
			return( auth_type );
		}
	}
}
