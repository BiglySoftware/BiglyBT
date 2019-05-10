/*
 * Created on 06-Dec-2004
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

package com.biglybt.core.proxy.impl;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.networkmanager.VirtualChannelSelector;
import com.biglybt.core.proxy.AEProxy;
import com.biglybt.core.proxy.AEProxyException;
import com.biglybt.core.proxy.AEProxyHandler;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.AEThread2;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemTime;

/**
 * @author parg
 *
 */

public class
AEProxyImpl
	implements AEProxy, VirtualChannelSelector.VirtualSelectorListener
{
	private static final LogIDs LOGID = LogIDs.NET;
	private static final int	DEBUG_PERIOD	= 60000;
	private long				last_debug;

	private int					port;
	private final long				connect_timeout;
	private final long				read_timeout;
	private final AEProxyHandler		proxy_handler;

	private ServerSocketChannel		ssc;
	final VirtualChannelSelector	read_selector;
	final VirtualChannelSelector	connect_selector;
	final VirtualChannelSelector	write_selector;

	private final List<AEProxyConnectionImpl>				processors = new ArrayList<>();

	private final HashMap 		write_select_regs = new HashMap();

	private boolean				allow_external_access;

	private final AEMonitor			this_mon	= new AEMonitor( "AEProxyImpl" );

	private volatile boolean	destroyed;

	public
	AEProxyImpl(
		int				_port,
		long			_connect_timeout,
		long			_read_timeout,
		AEProxyHandler	_proxy_handler )
		throws AEProxyException
	{
		port					= _port;
		connect_timeout			= _connect_timeout;
		read_timeout			= _read_timeout;
		proxy_handler			= _proxy_handler;

		String	name = "Proxy:" + port;

		read_selector	 = new VirtualChannelSelector( name, VirtualChannelSelector.OP_READ, false );
		connect_selector = new VirtualChannelSelector( name, VirtualChannelSelector.OP_CONNECT, true );
		write_selector	 = new VirtualChannelSelector( name, VirtualChannelSelector.OP_WRITE, true );

		try{

			ssc = ServerSocketChannel.open();

			ServerSocket ss	= ssc.socket();

			ss.setReuseAddress(true);

			ss.bind(  new InetSocketAddress( InetAddress.getByName("127.0.0.1"), port), 128 );

			if ( port == 0 ){

				port	= ss.getLocalPort();
			}

			new AEThread2("AEProxy:connect.loop")
			{
				@Override
				public void
				run()
				{
					selectLoop( connect_selector );
				}
			}.start();


			new AEThread2("AEProxy:read.loop")
			{
				@Override
				public void
				run()
				{
					selectLoop( read_selector );
				}
			}.start();

			new AEThread2("AEProxy:write.loop")
			{
				@Override
				public void
				run()
				{
					selectLoop( write_selector );
				}
			}.start();


			new AEThread2("AEProxy:accept.loop")
			{
				@Override
				public void
				run()
				{
					acceptLoop( ssc );
				}
			}.start();

			if (Logger.isEnabled())
				Logger.log(new LogEvent(LOGID, "AEProxy: listener established on port "
						+ port));

		}catch( Throwable e){

			Logger.logTextResource(new LogAlert(LogAlert.UNREPEATABLE,
					LogAlert.AT_ERROR, "Tracker.alert.listenfail"), new String[] { ""
					+ port + " (proxy)" });

			if (Logger.isEnabled())
				Logger.log(new LogEvent(LOGID, "AEProxy: listener failed on port "
						+ port, e));

			throw( new AEProxyException( "AEProxy: accept fails: " + e.toString()));
		}
	}

	@Override
	public void
	setAllowExternalConnections(
		boolean	permit )
	{
		allow_external_access = permit;
	}

	protected void
	acceptLoop(
		ServerSocketChannel	ssc )
	{
		long	successfull_accepts = 0;
		long	failed_accepts		= 0;

		while( !destroyed ){

			try{
				SocketChannel socket_channel = ssc.accept();

				successfull_accepts++;

				if ( !( allow_external_access || socket_channel.socket().getInetAddress().isLoopbackAddress())){

					if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
								"AEProxy: incoming connection from '"
										+ socket_channel.socket().getInetAddress()
										+ "' - closed as not local"));

					socket_channel.close();

				}else{

					try{
						socket_channel.configureBlocking( false );

						socket_channel.socket().setTcpNoDelay( true );

					}catch( Throwable e ){

						socket_channel.close();

						throw( e );
					}

					AEProxyConnectionImpl processor = new AEProxyConnectionImpl(this, socket_channel, proxy_handler);

					if ( !processor.isClosed()){

						boolean	added = false;

						try{
							this_mon.enter();

							if ( !destroyed ){

								added = true;

								processors.add( processor );

								if (Logger.isEnabled())
									Logger.log(new LogEvent(LOGID, "AEProxy: active processors = "
											+ processors.size()));
							}
						}finally{

							this_mon.exit();
						}

						if ( !added ){

							processor.close();

						}else{

							read_selector.register( socket_channel, this, processor );
						}
					}
				}
			}catch( Throwable e ){

				if ( !destroyed ){

					failed_accepts++;

					if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID, "AEProxy: listener failed on port "
								+ port, e));

					if ( failed_accepts > 100 && successfull_accepts == 0 ){

							// looks like its not going to work...
							// some kind of socket problem
						Logger.logTextResource(new LogAlert(LogAlert.UNREPEATABLE,
								LogAlert.AT_ERROR, "Network.alert.acceptfail"), new String[] {
								"" + port, "TCP" });

						break;
					}
				}
			}
		}
	}

	protected void
	close(
		AEProxyConnectionImpl	processor )
	{
		try{
			this_mon.enter();

			processors.remove( processor );

		}finally{

			this_mon.exit();
		}
	}

	protected void
	selectLoop(
      VirtualChannelSelector	selector )
	{
		long	last_time	= 0;

		while( !destroyed ){

			try{
				selector.select( 100 );

					// only use one selector to trigger the timeouts!

				if ( selector == read_selector ){

					long	now = SystemTime.getCurrentTime();

					if ( now < last_time ){

						last_time	= now;

					}else if ( now - last_time >= 5000 ){

						last_time	= now;

						checkTimeouts();
					}
				}
			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	protected void
	checkTimeouts()
	{
		long	now = SystemTime.getCurrentTime();

		if ( now - last_debug > DEBUG_PERIOD ){

			last_debug	= now;

			try{
				this_mon.enter();

				Iterator	it = processors.iterator();

				while( it.hasNext()){

					AEProxyConnectionImpl	processor = (AEProxyConnectionImpl)it.next();

					if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID, "AEProxy: active processor: "
								+ processor.getStateString()));
				}
			}finally{

				this_mon.exit();
			}
		}

		if ( connect_timeout <= 0 && read_timeout <= 0 ){

			return;
		}

		List	closes = new ArrayList();

		try{
			this_mon.enter();

			Iterator	it = processors.iterator();

			while( it.hasNext()){

				AEProxyConnectionImpl	processor = (AEProxyConnectionImpl)it.next();

				long diff = now - processor.getTimeStamp();

				if ( 	connect_timeout > 0 &&
						diff >= connect_timeout &&
						!processor.isConnected()){

					closes.add( processor );

				}else if (	read_timeout > 0 &&
							diff >= read_timeout &&
							processor.isConnected()){

					closes.add( processor );
				}
			}
		}finally{

			this_mon.exit();
		}

		for (int i=0;i<closes.size();i++){

			((AEProxyConnectionImpl)closes.get(i)).failed( new SocketTimeoutException( "timeout" ));
		}
	}

	protected void
	requestWriteSelect(
		AEProxyConnectionImpl	processor,
		SocketChannel 			sc )
	{

    if( write_select_regs.containsKey( sc ) ) {  //already been registered, just resume
      write_selector.resumeSelects( sc );
    }
    else {  //not yet registered
      write_select_regs.put( sc, null );
      write_selector.register( sc, this, processor );
    }
	}

	protected void
	cancelWriteSelect(
		SocketChannel 			sc )
	{
		write_select_regs.remove( sc );
		write_selector.cancel( sc );
	}

	protected void
	requestReadSelect(
		AEProxyConnectionImpl	processor,
		SocketChannel 		sc )
	{
		read_selector.register( sc, this, processor );
	}

	protected void
	cancelReadSelect(
		SocketChannel 		sc )
	{
		read_selector.cancel( sc );
	}

	protected void
	requestConnectSelect(
		AEProxyConnectionImpl	processor,
		SocketChannel 			sc )
	{
		connect_selector.register( sc, this, processor );
	}

	protected void
	cancelConnectSelect(
		SocketChannel 		sc )
	{
		connect_selector.cancel( sc );
	}

    @Override
    public boolean
	selectSuccess(
      VirtualChannelSelector	selector,
		SocketChannel 			sc,
		Object 					attachment )
    {
    	AEProxyConnectionImpl	processor = (AEProxyConnectionImpl)attachment;

    	if ( selector == read_selector ){

    		return( processor.read(sc));

    	}else if ( selector == write_selector ){

    		return( processor.write(sc));

    	}else{

    		return( processor.connect(sc));
    	}
    }

    @Override
    public void
	selectFailure(
      VirtualChannelSelector	selector,
		SocketChannel 			sc,
		Object 					attachment,
		Throwable 				msg )
    {
    	AEProxyConnectionImpl	processor = (AEProxyConnectionImpl)attachment;

    	processor.failed( msg );
    }

	@Override
	public int
	getPort()
	{
		return( port );
	}

	@Override
	public void
	destroy()
	{
		List<AEProxyConnectionImpl>	to_close;

		try{
			this_mon.enter();

			destroyed = true;

			to_close = new ArrayList<>(processors);

		}finally{

			this_mon.exit();
		}

		for ( AEProxyConnectionImpl con: to_close ){

			try{
				con.close();

			}catch( Throwable e ){
			}
		}

		if ( ssc != null ){

			try{
				ssc.close();

			}catch( Throwable e ){
			}
		}

		connect_selector.destroy();
		read_selector.destroy();
		write_selector.destroy();
	}
}
