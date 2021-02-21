/*
 * Created on May 30, 2014
 * Created by Paul Gardner
 *
 * Copyright 2014 Azureus Software, Inc.  All rights reserved.
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
 */


package com.biglybt.plugin.tracker.dht;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.*;

import com.biglybt.core.dht.transport.DHTTransportAlternativeContact;
import com.biglybt.core.dht.transport.DHTTransportAlternativeNetwork;
import com.biglybt.core.dht.transport.udp.impl.DHTUDPUtils;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.util.*;

public class
DHTTrackerPluginAlt
{
	private static final long	startup_time 	= SystemTime.getMonotonousTime();
	private static final int	startup_grace	= 60*1000;

	private static final int INITAL_DELAY	= 5*1000;
	private static final int RPC_TIMEOUT	= 15*1000;
	private static final int LOOKUP_TIMEOUT	= 90*1000;
	private static final int LOOKUP_LINGER	= 5*1000;

	private static final int CONC_LOOKUPS	= 8;
	private static final int NUM_WANT		= 32;

	private static final int NID_CLOSENESS_LIMIT	= 10;


	private final int		port;

	private final byte[]	NID = new byte[20];

	private DatagramSocket	current_server;
	private Throwable		last_server_error;

	private ByteArrayHashMap<Object[]>	tid_map = new ByteArrayHashMap<>();

	private TimerEventPeriodic	timer_event;

	private AsyncDispatcher		dispatcher = new AsyncDispatcher();

	private volatile long	lookup_count;
	private volatile long	hit_count;

	private volatile long	packets_out;
	private volatile long	packets_in;
	private volatile long	bytes_out;
	private volatile long	bytes_in;

	protected
	DHTTrackerPluginAlt(
		int		_port )
	{
		port	= _port;

			// there is no node id restriction for requests

		RandomUtils.nextBytes( NID );
	}

	private DatagramSocket
	getServer()
	{
		synchronized( this ){

			if ( current_server != null ){

				if ( current_server.isClosed()){

					current_server = null;

				}else{

					return( current_server );
				}
			}

			try{
				final DatagramSocket server = new DatagramSocket(null);

				server.setReuseAddress(true);

				InetAddress bind_ip = NetworkAdmin.getSingleton().getSingleHomedServiceBindAddress();

				if ( bind_ip == null ){

					bind_ip = InetAddress.getByName( "127.0.0.1" );
				}

				server.bind( new InetSocketAddress(bind_ip, port));

				current_server = server;

				last_server_error	= null;

				new AEThread2( "DHTPluginAlt:server" )
				{
					@Override
					public void
					run()
					{
						try{
							while( true ){

								byte[] buffer = new byte[5120];

								DatagramPacket packet = new DatagramPacket( buffer, buffer.length );

								server.receive( packet );

								packets_in++;

								bytes_in += packet.getLength();

								Map<String, Object> map = new BDecoder().decodeByteArray(packet.getData(), 0, packet.getLength() ,false);

								//System.out.println( "got " + map );

								byte[]	tid = (byte[])map.get( "t" );

								if ( tid != null ){

									Object[] task;

									synchronized( tid_map ){

										task = tid_map.remove( tid );
									}

									if ( task != null ){

										((GetPeersTask)task[0]).handleReply((InetSocketAddress)packet.getSocketAddress(), tid, map );
									}
								}
							}
						}catch( Throwable e ){

						}finally{

							try{
								server.close();

							}catch( Throwable f ){
							}

							synchronized( DHTTrackerPluginAlt.this ){

								if ( current_server == server ){

									current_server = null;
								}
							}
						}
					}
				}.start();

				return( server );

			}catch( Throwable e ){

				last_server_error = e;

				return( null );
			}
		}
	}

	protected void
	get(
		final byte[]				hash,
		final boolean				no_seeds,
		final LookupListener		listener )
	{
		SimpleTimer.addEvent(
			"altlookup.delay",
			SystemTime.getCurrentTime() + INITAL_DELAY,
			new TimerEventPerformer()
			{
				@Override
				public void
				perform(
					TimerEvent event)
				{
					if ( listener.isComplete()){

						return;
					}

					if ( dispatcher.getQueueSize() > 100 ){

						return;
					}

					dispatcher.dispatch(
						new AERunnable() {

							@Override
							public void
							runSupport()
							{
								getSupport( hash, no_seeds, listener );
							}
						});
				}
			});
	}

	private void
	getSupport(
		final byte[]				hash,
		final boolean				no_seeds,
		final LookupListener		listener )
	{
		List<DHTTransportAlternativeContact> contacts;

		while( true ){

			if ( listener.isComplete()){

				return;
			}

			contacts = DHTUDPUtils.getAlternativeContacts( DHTTransportAlternativeNetwork.AT_MLDHT_IPV4, 16 );

			if ( contacts.size() == 0 ){

				long now = SystemTime.getMonotonousTime();

				if ( now - startup_time < startup_grace ){

					try{
						Thread.sleep(5000);

					}catch( Throwable e ){

					}

					continue;
				}

				return;

			}else{

				break;
			}
		}

		DatagramSocket	server = getServer();

		if ( server == null ){

			return;
		}

		lookup_count++;

		new GetPeersTask( server, contacts, hash, no_seeds, listener );
	}

	private byte[]
	send(
		GetPeersTask		task,
		DatagramSocket		server,
		InetSocketAddress	address,
		Map<String,Object>	map )

		throws IOException
	{
		byte[]	tid;

		while( true ){

			tid = new byte[4];

			RandomUtils.nextBytes( tid );

			synchronized( tid_map ){

				if ( tid_map.containsKey( tid )){

					continue;
				}

				tid_map.put( tid, new Object[]{ task, SystemTime.getMonotonousTime() });

				if ( timer_event == null ){

					timer_event =
						SimpleTimer.addPeriodicEvent(
							"dhtalttimer",
							2500,
							new TimerEventPerformer()
							{
								@Override
								public void
								perform(
									TimerEvent event)
								{
									checkTimeouts();

									synchronized( tid_map ){

										if ( tid_map.size() == 0 ){

											timer_event.cancel();

											timer_event = null;
										}
									}
								}
							});
				}
			}

			try{
				map.put( "t", tid );

				// System.out.println( "Sending: " + map );

				byte[] 	data_out = BEncoder.encode( map );

				DatagramPacket packet = new DatagramPacket( data_out, data_out.length);

				packet.setSocketAddress( address );

				packets_out++;
				bytes_out += data_out.length;

				server.send( packet );

				return( tid );

			}catch( Throwable e ){

				try{
					server.close();

				}catch( Throwable f ){

				}

				synchronized( tid_map ){

					tid_map.remove( tid );
				}

				if ( e instanceof IOException ){

					throw((IOException)e);

				}else{

					throw(new IOException(Debug.getNestedExceptionMessage(e)));
				}
			}
		}
	}

	private void
	checkTimeouts()
	{
		long	now = SystemTime.getMonotonousTime();

		List<Object[]>	timeouts = null;

		synchronized( tid_map ){

			Iterator<byte[]> it = tid_map.keys().iterator();

			while( it.hasNext()){

				byte[] key = it.next();

				Object[]	value = tid_map.get( key );

				long time = (Long)value[1];

				if ( now - time > RPC_TIMEOUT ){

					tid_map.remove( key );

					if ( timeouts == null ){

						timeouts = new ArrayList<>();
					}

					timeouts.add(new Object[]{ key, value[0] });
				}
			}
		}

		if ( timeouts != null ){

			for ( Object[] entry: timeouts ){

				try{
					((GetPeersTask)entry[1]).handleTimeout((byte[])entry[0]);

				}catch( Throwable e ){

					Debug.out( e );
				}
			}
		}
	}

	protected String
	getString()
	{
		return( "lookups=" + lookup_count + ", hits=" + hit_count +
				", out=" + packets_out + "/" + DisplayFormatters.formatByteCountToKiBEtc( bytes_out ) +
				", in=" + packets_in + "/" + DisplayFormatters.formatByteCountToKiBEtc( bytes_in ) +
				(last_server_error==null?"":(", error=" + Debug.getNestedExceptionMessage( last_server_error ))));
	}

	private class
	GetPeersTask
	{
		private long				start_time	= SystemTime.getMonotonousTime();

		private DatagramSocket		server;
		private byte[]				torrent_hash;
		private boolean				no_seeds;
		private LookupListener		listener;

		private List<DHTTransportAlternativeContact>	initial_contacts;

		private ByteArrayHashMap<InetSocketAddress>	active_queries = new ByteArrayHashMap<>();

		private Set<InetSocketAddress>		queried_nodes = new HashSet<>();

		Comparator<byte[]>	comparator =
			new Comparator<byte[]>()
			{
				@Override
				public int
				compare(
					byte[] o1,
					byte[] o2)
				{
					for ( int i=0; i < o1.length;i++ ){

						byte b1 = o1[i];
						byte b2 = o2[i];

						if ( b1 == b2 ){

							continue;
						}

						byte t = torrent_hash[i];

						int d1 = (b1^t)&0xff;
						int d2 = (b2^t)&0xff;

						if ( d1 == d2 ){

							continue;
						}

						if ( d1 < d2 ){

							return( -1 );

						}else{

							return( 1 );
						}
					}

					return( 0 );
				}
			};

		private TreeMap<byte[],InetSocketAddress>	to_query = new TreeMap<>(comparator);

		private TreeMap<byte[],InetSocketAddress>	heard_from =
				new TreeMap<>(
						new Comparator<byte[]>() {
							@Override
							public int
							compare(
									byte[] o1,
									byte[] o2) {
								return (-comparator.compare(o1, o2));
							}
						});

		private long	found_peer_time;

		private Set<InetSocketAddress>	found_peers = new HashSet<>();

		private int		query_count;
		private int		timeout_count;
		private int		reply_count;

		private boolean	completed;
		private boolean	failed;

		private
		GetPeersTask(
			DatagramSocket								_server,
			List<DHTTransportAlternativeContact>		_contacts,
			byte[]										_torrent_hash,
			boolean										_no_seeds,
			LookupListener								_listener )
		{
			server			= _server;
			torrent_hash	= _torrent_hash;
			no_seeds		= _no_seeds;
			listener		= _listener;

			initial_contacts = _contacts;

			tryQuery();
		}

		private void
		search(
			InetSocketAddress	address )

			throws IOException
		{
			if ( queried_nodes.contains( address )){

				return;
			}

			queried_nodes.add( address );

			Map<String,Object> map = new HashMap<>();

			map.put( "q", "get_peers" );
			map.put( "y", "q" );

			Map<String,Object> args = new HashMap<>();

			map.put( "a", args );

			args.put( "id", NID );

			args.put( "info_hash", torrent_hash );

			args.put( "noseed", new Long( no_seeds?1:0 ));

			byte[]	tid = send( this, server, address, map );

			query_count++;

			active_queries.put( tid, address );
		}

		private void
		tryQuery()
		{
			if ( listener.isComplete()){

				return;
			}

			try{
				synchronized( this ){

					if ( failed || active_queries.size() >= CONC_LOOKUPS ){

						return;
					}

					long	now = SystemTime.getMonotonousTime();

					if ( now - start_time > LOOKUP_TIMEOUT ){

						return;
					}

					if ( found_peer_time > 0 ){

						if ( found_peers.size() > NUM_WANT ){

							setCompleted();

							return;
						}

						if ( now - found_peer_time > LOOKUP_LINGER ){

							setCompleted();

							return;
						}
					}

					try{
						byte[]	limit_nid;

						if ( heard_from.size() >= NID_CLOSENESS_LIMIT ){

							limit_nid = heard_from.keySet().iterator().next();

						}else{

							limit_nid = null;
						}

						Iterator<Map.Entry<byte[],InetSocketAddress>> query_it = to_query.entrySet().iterator();

						while( query_it.hasNext()){

							Map.Entry<byte[],InetSocketAddress> entry = query_it.next();

							query_it.remove();

							byte[]	nid = entry.getKey();

							if ( limit_nid != null && comparator.compare( limit_nid, nid ) <= 0 ){

								// System.out.println( "skipping " + ByteFormatter.encodeString( nid ) + ": limit=" + ByteFormatter.encodeString( limit_nid ) + "/" + ByteFormatter.encodeString( torrent_hash ));

								continue;
							}

							// System.out.println( "searching " + ByteFormatter.encodeString( nid ));

							InetSocketAddress	address = entry.getValue();

							search( address );

							if ( active_queries.size() >= CONC_LOOKUPS ){

								return;
							}
						}

						if ( heard_from.size() < 10 ){

							Iterator<DHTTransportAlternativeContact> contact_it = initial_contacts.iterator();

							while( contact_it.hasNext()){

								DHTTransportAlternativeContact	contact = contact_it.next();

								contact_it.remove();

								Map<String,Object>	properties = contact.getProperties();

								byte[]	_a 	= (byte[])properties.get( "a" );
								Long	_p	= (Long)properties.get( "p" );

								if ( _a != null && _p != null ){

									try{
										InetSocketAddress address = new InetSocketAddress( InetAddress.getByAddress( _a ), _p.intValue());

										search( address );

										if ( active_queries.size() >= CONC_LOOKUPS ){

											return;
										}
									}catch( Throwable e ){

									}
								}
							}
						}
					}finally{

						if ( active_queries.size() == 0 ){

							setCompleted();
						}
					}
				}
			}catch( Throwable e ){

				synchronized( this ){

					setFailed();
				}
			}finally{

				// log();
			}
		}

		private void
		handleTimeout(
			byte[]	tid )
		{
			synchronized( this ){

				active_queries.remove( tid );

				timeout_count++;
			}

			tryQuery();
		}

		private void
		handleReply(
			InetSocketAddress		from,
			byte[]					tid,
			Map<String,Object>		map )

			throws IOException
		{
			Map<String,Object> reply = (Map<String,Object>)map.get( "r" );

			synchronized( this ){

				active_queries.remove( tid );

				reply_count++;

				if ( reply == null ){

					// System.out.println( "Error: " + reply );
					
						// error response, ignore

					return;
				}

				heard_from.put((byte[])reply.get( "id" ), from );

				if ( heard_from.size() > NID_CLOSENESS_LIMIT ){

					Iterator<byte[]> it = heard_from.keySet().iterator();

					it.next();

					it.remove();
				}
			}

			ArrayList<byte[]>	values = ( ArrayList<byte[]>)reply.get( "values" );

			if ( values != null ){

				synchronized( this ){

					if ( found_peer_time == 0 ){

						found_peer_time	= SystemTime.getMonotonousTime();

					}
				}

				for ( byte[] value: values ){

					try{
						ByteBuffer bb = ByteBuffer.wrap( value );

						byte[]	address = new byte[value.length-2];

						bb.get( address );

						int	port = bb.getShort()&0xffff;

						InetSocketAddress addr = new InetSocketAddress( InetAddress.getByAddress(address), port );

						synchronized( this ){

							if ( found_peers.contains( addr )){

								continue;
							}

							found_peers.add( addr );
						}

						// System.out.println("Found peer: " + addr );
						
						hit_count++;

						listener.foundPeer( addr );

					}catch( Throwable e ){
					}
				}
			}

			byte[]	nodes 	= (byte[])reply.get( "nodes" );
			byte[]	nodes6 	= (byte[])reply.get( "nodes6" );

			if ( nodes != null ){

				int	entry_size = 20+4+2;

				for ( int i=0;i<nodes.length;i+=entry_size ){

					ByteBuffer bb = ByteBuffer.wrap(nodes, i, entry_size );

					byte[] nid = new byte[20];

					bb.get(nid);

					byte[] address = new byte[ 4 ];

					bb.get( address );

					int port = bb.getShort()&0xffff;

					try{
						InetSocketAddress addr = new InetSocketAddress( InetAddress.getByAddress(address), port );

						synchronized( this ){

							if ( !queried_nodes.contains( addr )){

								to_query.put( nid, addr );
							}
						}
					}catch( Throwable e ){
					}
				}
			}

			tryQuery();
		}

		private void
		setCompleted()
		{
			if ( !completed ){

				completed = true;

				listener.completed();
			}
		}

		private void
		setFailed()
		{
			failed = true;

			setCompleted();
		}

		private void
		log()
		{
			System.out.println(
				ByteFormatter.encodeString( torrent_hash ) +
				": send=" + query_count +
				", recv=" + reply_count +
				", t/o=" + timeout_count +
				", elapsed=" + (SystemTime.getMonotonousTime() - start_time ) +
				", toq=" + to_query.size() +
				", found=" + found_peers.size());

			synchronized( this ){

				for ( byte[] nid: heard_from.keySet()){

					System.out.println( "    " + ByteFormatter.encodeString( nid ));
				}
			}
		}
	}


	protected interface
	LookupListener
	{
		public void
		foundPeer(
			InetSocketAddress	address );

		public boolean
		isComplete();

		public void
		completed();
	}
}
