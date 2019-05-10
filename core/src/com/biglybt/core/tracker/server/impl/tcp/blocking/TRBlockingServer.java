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

package com.biglybt.core.tracker.server.impl.tcp.blocking;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.security.SESecurityManager;
import com.biglybt.core.tracker.server.TRTrackerServerException;
import com.biglybt.core.tracker.server.impl.tcp.TRTrackerServerTCP;
import com.biglybt.core.util.AEThread;
import com.biglybt.core.util.Debug;


/**
 * @author parg
 *
 */

public class
TRBlockingServer
	extends TRTrackerServerTCP
{
	private static final LogIDs LOGID = LogIDs.TRACKER;

	private InetAddress		current_bind_ip;
	private ServerSocket	server_socket;

	private volatile boolean	closed;

	public
	TRBlockingServer(
		String		_name,
		int			_port,
		InetAddress	_bind_ip,
		boolean		_ssl,
		boolean		_apply_ip_filter,
		boolean		_start_up_ready )

		throws TRTrackerServerException
	{
		super( _name, _port, _ssl, _apply_ip_filter, _start_up_ready );

		boolean	ok = false;

		try{
			InetAddress bind_ip = NetworkAdmin.getSingleton().getSingleHomedServiceBindAddress();

			String tr_bind_ip = COConfigurationManager.getStringParameter("Bind IP for Tracker", "");

			if ( tr_bind_ip.length() >= 7 ){

				try{

					bind_ip = InetAddress.getByName(tr_bind_ip);

				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}
			}

			if ( _ssl ){

				if ( _port == 0 ){

					throw( new TRTrackerServerException( "port of 0 not currently supported for SSL"));
				}

				try {
					SSLServerSocketFactory factory = SESecurityManager.getSSLServerSocketFactory();

					if ( factory == null ){

						throw( new TRTrackerServerException( "TRTrackerServer: failed to get SSL factory" ));

					}else{
						SSLServerSocket ssl_server_socket;

						if ( _bind_ip != null ){

							current_bind_ip = _bind_ip;

							ssl_server_socket = (SSLServerSocket)factory.createServerSocket(  getPort(), 128, _bind_ip );

						}else if ( bind_ip == null ){

							ssl_server_socket = (SSLServerSocket)factory.createServerSocket( getPort(), 128 );

						}else{

							current_bind_ip = bind_ip;

							ssl_server_socket = (SSLServerSocket)factory.createServerSocket(  getPort(), 128, bind_ip );
						}

						String cipherSuites[] = ssl_server_socket.getSupportedCipherSuites();

						ssl_server_socket.setEnabledCipherSuites(cipherSuites);

						ssl_server_socket.setNeedClientAuth(false);

						ssl_server_socket.setReuseAddress(true);

						server_socket = ssl_server_socket;

						Thread accept_thread =
								new AEThread("TRTrackerServer:accept.loop(ssl)")
								{
									@Override
									public void
									runSupport()
									{
										acceptLoop( server_socket );
									}
								};

						accept_thread.setDaemon( true );

						accept_thread.start();

						Logger.log(new LogEvent(LOGID,
								"TRTrackerServer: SSL listener established on port "
										+ getPort()));

						ok	= true;
					}

				}catch( Throwable e){

					Logger.logTextResource(new LogAlert(LogAlert.UNREPEATABLE,
							LogAlert.AT_ERROR, "Tracker.alert.listenfail"), new String[] { ""
							+ getPort() + " (" + getName() + ")"});

					Logger.log(new LogEvent(LOGID,
							"TRTrackerServer: SSL listener failed on port " + getPort(), e));

					if ( e instanceof TRTrackerServerException ){

						throw((TRTrackerServerException)e);

					}else{

						throw( new TRTrackerServerException( "TRTrackerServer: accept fails: " + e.toString()));
					}
				}

			}else{

				try{
					ServerSocket ss;

					int	port = getPort();

					if ( _bind_ip != null ){

						current_bind_ip = _bind_ip;

						ss = new ServerSocket(  port, 1024, _bind_ip );

					}else if ( bind_ip == null ){

						ss = new ServerSocket(  port, 1024 );

					}else{

						current_bind_ip = bind_ip;

						ss = new ServerSocket(  port, 1024, bind_ip );
					}

					if ( port == 0 ){

						setPort( ss.getLocalPort());
					}

					ss.setReuseAddress(true);

					server_socket = ss;

					Thread accept_thread =
							new AEThread("TRTrackerServer:accept.loop")
							{
								@Override
								public void
								runSupport()
								{
									acceptLoop( server_socket );
								}
							};

					accept_thread.setDaemon( true );

					accept_thread.start();

					Logger.log(new LogEvent(LOGID, "TRTrackerServer: "
							+ "listener established on port " + getPort()));

					ok	= true;

				}catch( Throwable e){

					Logger.logTextResource(new LogAlert(LogAlert.UNREPEATABLE,
							LogAlert.AT_ERROR, "Tracker.alert.listenfail"), new String[] { ""
							+ getPort() + " (" + getName() +")"});

					throw( new TRTrackerServerException( "TRTrackerServer: accept fails", e ));
				}
			}
		}finally{

			if ( !ok ){

				destroySupport();
			}
		}
	}

	@Override
	public InetAddress
	getBindIP()
	{
		return( current_bind_ip );
	}

	protected void
	acceptLoop(
		ServerSocket	ss )
	{
		long	successfull_accepts = 0;
		long	failed_accepts		= 0;

		while( !closed ){

			try{
				Socket socket = ss.accept();

				successfull_accepts++;

				String	ip = socket.getInetAddress().getHostAddress();

				if ( (!isIPFilterEnabled()) || (!ip_filter.isInRange( ip, "Tracker", null ))){

					runProcessor( new TRBlockingServerProcessor( this, socket ));

				}else{

					socket.close();
				}

			}catch( Throwable e ){

				if ( !closed ){

					failed_accepts++;

					Logger.log(new LogEvent(LOGID,
							"TRTrackerServer: listener failed on port " + getPort(), e));

					if ( failed_accepts > 100 && successfull_accepts == 0 ){

							// looks like its not going to work...
							// some kind of socket problem

						Logger.logTextResource(new LogAlert(LogAlert.UNREPEATABLE,
								LogAlert.AT_ERROR, "Network.alert.acceptfail"), new String[] {
								"" + getPort(), "TCP" });

						break;
					}
				}
			}
		}
	}

	@Override
	protected void
	closeSupport()
	{
		closed = true;

		try{
			server_socket.close();

		}catch( Throwable e ){

		}

		destroySupport();
	}
}
