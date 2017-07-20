/*
 * File    : TRTrackerServerUDP.java
 * Created : 19-Jan-2004
 * By      : parg
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.core.tracker.server.impl.udp;

/**
 * @author parg
 *
 */

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.tracker.server.TRTrackerServerRequestListener;
import com.biglybt.core.tracker.server.impl.TRTrackerServerImpl;
import com.biglybt.core.util.AEThread;
import com.biglybt.core.util.ThreadPool;
import com.biglybt.net.udp.uc.PRUDPPacket;

public class
TRTrackerServerUDP
	extends 	TRTrackerServerImpl
{
	private static final LogIDs LOGID = LogIDs.TRACKER;

	private static final int THREAD_POOL_SIZE				= 10;

	private final ThreadPool	thread_pool;

	private final int				port;
	private InetAddress		current_bind_ip;

	private DatagramSocket	dg_socket;

	private volatile boolean	closed;

	public
	TRTrackerServerUDP(
		String	_name,
		int		_port,
		boolean	_start_up_ready )
	{
		super( _name, _start_up_ready );

		port		= _port;

		thread_pool = new ThreadPool( "TrackerServer:UDP:"+port, THREAD_POOL_SIZE );

		try{
			InetAddress bind_ip = NetworkAdmin.getSingleton().getSingleHomedServiceBindAddress();

			InetSocketAddress	address;

			DatagramSocket	socket;

			if ( bind_ip == null ){

				address = new InetSocketAddress(InetAddress.getByName("127.0.0.1"),port);

				socket = new DatagramSocket( port );

			}else{

				current_bind_ip = bind_ip;

				address = new InetSocketAddress( bind_ip, port);

				socket = new DatagramSocket(address);
			}

			socket.setReuseAddress(true);

			dg_socket 	= socket;

			final InetSocketAddress	f_address	= address;

			Thread recv_thread =
				new AEThread("TRTrackerServerUDP:recv.loop")
				{
					@Override
					public void
					runSupport()
					{
						recvLoop( dg_socket, f_address );
					}
				};

			recv_thread.setDaemon( true );

			recv_thread.start();

			Logger.log(new LogEvent(LOGID,
					"TRTrackerServerUDP: recv established on port " + port));

		}catch( Throwable e ){

			Logger.log(new LogEvent(LOGID, "TRTrackerServerUDP: "
					+ "DatagramSocket bind failed on port " + port, e));
		}
	}

	@Override
	public InetAddress
	getBindIP()
	{
		return( current_bind_ip );
	}

	protected void
	recvLoop(
		DatagramSocket		socket,
		InetSocketAddress	address )
	{
		long	successful_accepts 	= 0;
		long	failed_accepts		= 0;

		while( !closed ){

			try{
				byte[] buf = new byte[PRUDPPacket.MAX_PACKET_SIZE];

				DatagramPacket packet = new DatagramPacket( buf, buf.length, address );

				socket.receive( packet );

				successful_accepts++;

				failed_accepts	= 0;

				String	ip = packet.getAddress().getHostAddress();

				if ( !ip_filter.isInRange( ip, "Tracker", null )){

					thread_pool.run( new TRTrackerServerProcessorUDP( this, socket, packet ));
				}

			}catch( Throwable e ){

				if ( !closed ){

					failed_accepts++;

					Logger.log(new LogEvent(LOGID,
							"TRTrackerServer: receive failed on port " + port, e));

					if (( failed_accepts > 100 && successful_accepts == 0 ) || failed_accepts > 1000 ){

						// looks like its not going to work...
						// some kind of socket problem

						Logger.logTextResource(new LogAlert(LogAlert.UNREPEATABLE,
								LogAlert.AT_ERROR, "Network.alert.acceptfail"), new String[] {
								"" + port, "UDP" });

						break;
					}
				}
			}
		}
	}

	@Override
	public int
	getPort()
	{
		return( port );
	}

	@Override
	public String
	getHost()
	{
		return( COConfigurationManager.getStringParameter( "Tracker IP", "" ));
	}

	@Override
	public boolean
	isSSL()
	{
		return( false );
	}

	@Override
	public void
	addRequestListener(
		TRTrackerServerRequestListener	l )
	{
	}

	@Override
	public void
	removeRequestListener(
		TRTrackerServerRequestListener	l )
	{
	}

	@Override
	protected void
	closeSupport()
	{
		closed = true;

		try{
			dg_socket.close();

		}catch( Throwable e ){

		}

		destroySupport();
	}
}
