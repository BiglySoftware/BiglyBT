/*
 * File    : TRTrackerServerFactoryImpl.java
 * Created : 13-Dec-2003
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

package com.biglybt.core.tracker.server.impl;

/**
 * @author parg
 *
 */

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.stats.CoreStats;
import com.biglybt.core.stats.CoreStatsProvider;
import com.biglybt.core.tracker.server.*;
import com.biglybt.core.tracker.server.impl.dht.TRTrackerServerDHT;
import com.biglybt.core.tracker.server.impl.tcp.TRTrackerServerTCP;
import com.biglybt.core.tracker.server.impl.tcp.blocking.TRBlockingServer;
import com.biglybt.core.tracker.server.impl.tcp.nonblocking.TRNonBlockingServer;
import com.biglybt.core.tracker.server.impl.tcp.nonblocking.TRNonBlockingServerProcessor;
import com.biglybt.core.tracker.server.impl.tcp.nonblocking.TRNonBlockingServerProcessorFactory;
import com.biglybt.core.tracker.server.impl.udp.TRTrackerServerUDP;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.AsyncController;
import com.biglybt.core.util.CopyOnWriteList;
import com.biglybt.pif.tracker.Tracker;

public class
TRTrackerServerFactoryImpl
{
	protected static final CopyOnWriteList		servers		= new CopyOnWriteList();

	protected static final List		listeners 	= new ArrayList();
	protected static final AEMonitor 	class_mon 	= new AEMonitor( "TRTrackerServerFactory" );

	static{
		Set	types = new HashSet();

		types.add( CoreStats.ST_TRACKER_READ_BYTES );
		types.add( CoreStats.ST_TRACKER_WRITE_BYTES );
		types.add( CoreStats.ST_TRACKER_ANNOUNCE_COUNT );
		types.add( CoreStats.ST_TRACKER_ANNOUNCE_TIME );
		types.add( CoreStats.ST_TRACKER_SCRAPE_COUNT );
		types.add( CoreStats.ST_TRACKER_SCRAPE_TIME );

		CoreStats.registerProvider(
			types,
			new CoreStatsProvider()
			{
				@Override
				public void
				updateStats(
					Set		types,
					Map		values )
				{
					long	read_bytes		= 0;
					long	write_bytes		= 0;
					long	announce_count	= 0;
					long	announce_time	= 0;
					long	scrape_count	= 0;
					long	scrape_time		= 0;

					Iterator it = servers.iterator();

					while( it.hasNext()){

						TRTrackerServerStats stats = ((TRTrackerServer)it.next()).getStats();

						read_bytes		+= stats.getBytesIn();
						write_bytes		+= stats.getBytesOut();
						announce_count 	+= stats.getAnnounceCount();
						announce_time	+= stats.getAnnounceTime();
						scrape_count 	+= stats.getScrapeCount();
						scrape_time		+= stats.getScrapeTime();
					}

					if ( types.contains( CoreStats.ST_TRACKER_READ_BYTES )){

						values.put( CoreStats.ST_TRACKER_READ_BYTES, new Long( read_bytes ));
					}
					if ( types.contains( CoreStats.ST_TRACKER_WRITE_BYTES )){

						values.put( CoreStats.ST_TRACKER_WRITE_BYTES, new Long( write_bytes ));
					}
					if ( types.contains( CoreStats.ST_TRACKER_ANNOUNCE_COUNT )){

						values.put( CoreStats.ST_TRACKER_ANNOUNCE_COUNT, new Long( announce_count ));
					}
					if ( types.contains( CoreStats.ST_TRACKER_ANNOUNCE_TIME )){

						values.put( CoreStats.ST_TRACKER_ANNOUNCE_TIME, new Long( announce_time ));
					}
					if ( types.contains( CoreStats.ST_TRACKER_SCRAPE_COUNT )){

						values.put( CoreStats.ST_TRACKER_SCRAPE_COUNT, new Long( scrape_count ));
					}
					if ( types.contains( CoreStats.ST_TRACKER_SCRAPE_TIME )){

						values.put( CoreStats.ST_TRACKER_SCRAPE_TIME, new Long( scrape_time ));
					}
				}
			});
	}

	public static TRTrackerServer
	create(
		String					name,
		int						protocol,
		int						port,
		InetAddress				bind_ip,
		boolean					ssl,
		boolean					apply_ip_filter,
		boolean					main_tracker,
		boolean					start_up_ready,
		Map<String,Object>		properties )

		throws TRTrackerServerException
	{
		if ( properties == null ){

			properties = new HashMap<>();
		}

		Boolean	pr_non_blocking = (Boolean)properties.get(Tracker.PR_NON_BLOCKING );

		try{
			class_mon.enter();

			TRTrackerServerImpl	server;

			if ( protocol == TRTrackerServerFactory.PR_TCP ){

				boolean explicit_non_blocking = pr_non_blocking != null && pr_non_blocking;

				boolean non_blocking =
						( COConfigurationManager.getBooleanParameter( "Tracker TCP NonBlocking" ) && main_tracker ) ||
						( explicit_non_blocking );

				if ( non_blocking && !ssl ){

					TRNonBlockingServer nb_server =
						new TRNonBlockingServer(
							name,
							port,
							bind_ip,
							apply_ip_filter,
							start_up_ready,
							new TRNonBlockingServerProcessorFactory()
							{
								@Override
								public TRNonBlockingServerProcessor
								create(
									TRTrackerServerTCP		_server,
									SocketChannel			_socket )
								{
									return( new NonBlockingProcessor( _server, _socket ));

								}
							});

					server = nb_server;

					if ( explicit_non_blocking ){

						nb_server.setRestrictNonBlocking( false );
					}
				}else{

					server = new TRBlockingServer( name, port, bind_ip, ssl, apply_ip_filter, start_up_ready );
				}

			}else if ( protocol == TRTrackerServerFactory.PR_UDP ){

				if ( ssl ){

					throw( new TRTrackerServerException( "TRTrackerServerFactory: UDP doesn't support SSL"));
				}

				server = new TRTrackerServerUDP( name, port, start_up_ready );

			}else{

				server = new TRTrackerServerDHT( name, start_up_ready );
			}

			servers.add( server );

			for (int i=0;i<listeners.size();i++){

				((TRTrackerServerFactoryListener)listeners.get(i)).serverCreated( server );
			}

			return( server );

		}finally{

			class_mon.exit();
		}
	}

	protected static void
	close(
		TRTrackerServerImpl	server )
	{
		try{
			class_mon.enter();

			server.closeSupport();

			if ( servers.remove( server )){

				for (int i=0;i<listeners.size();i++){

					((TRTrackerServerFactoryListener)listeners.get(i)).serverDestroyed( server );
				}
			}
		}finally{

			class_mon.exit();
		}
	}

	public static void
	addListener(
		TRTrackerServerFactoryListener	l )
	{
		try{
			class_mon.enter();

			listeners.add( l );

			Iterator it = servers.iterator();

			while( it.hasNext()){

				l.serverCreated((TRTrackerServer)it.next());
			}
		}finally{

			class_mon.exit();
		}
	}

	public static void
	removeListener(
		TRTrackerServerFactoryListener	l )
	{
		try{
			class_mon.enter();

			listeners.remove( l );

		}finally{

			class_mon.exit();
		}
	}

	protected static class
	NonBlockingProcessor
		extends TRNonBlockingServerProcessor
	{
		protected
		NonBlockingProcessor(
			TRTrackerServerTCP		_server,
			SocketChannel			_socket )
		{
			super( _server, _socket );
		}

		@Override
		protected ByteArrayOutputStream
		process(
			String 				input_header,
			String 				lowercase_input_header,
			String 				url_path,
			InetSocketAddress 	remote_address,
			boolean 			announce_and_scrape_only,
			InputStream 		is,
			AsyncController		async )

			throws IOException
		{
			ByteArrayOutputStream	os = new ByteArrayOutputStream( 1024 );

			InetSocketAddress	local_address = null;	// TODO

			processRequest(input_header, lowercase_input_header, url_path, local_address, remote_address, announce_and_scrape_only, false, is, os, async );

			return( os );
		}
	}
}
