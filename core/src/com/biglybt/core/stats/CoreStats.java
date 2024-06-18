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


package com.biglybt.core.stats;

import java.util.*;
import java.util.regex.Pattern;

import com.biglybt.core.util.*;
import com.biglybt.core.util.Timer;
import com.biglybt.core.util.average.Average;
import com.biglybt.core.util.average.AverageFactory;

public class
CoreStats
{
	public static final int AVERAGE_PERIOD	= 1000;

	public static final String ST_ALL							= ".*";

		// DISK

	public static final String ST_DISK							= "disk.*";
	public static final String ST_DISK_READ_QUEUE_LENGTH		= "disk.read.queue.length";
	public static final String ST_DISK_READ_QUEUE_BYTES			= "disk.read.queue.bytes";
	public static final String ST_DISK_READ_REQUEST_COUNT		= "disk.read.request.count";
	public static final String ST_DISK_READ_REQUEST_SINGLE		= "disk.read.request.single";
	public static final String ST_DISK_READ_REQUEST_MULTIPLE	= "disk.read.request.multiple";
	public static final String ST_DISK_READ_REQUEST_BLOCKS		= "disk.read.request.blocks";
	public static final String ST_DISK_READ_BYTES_TOTAL			= "disk.read.bytes.total";
	public static final String ST_DISK_READ_BYTES_SINGLE		= "disk.read.bytes.single";
	public static final String ST_DISK_READ_BYTES_MULTIPLE		= "disk.read.bytes.multiple";
	public static final String ST_DISK_READ_IO_TIME				= "disk.read.io.time";
	public static final String ST_DISK_READ_IO_COUNT			= "disk.read.io.count";

	public static final String ST_DISK_WRITE_QUEUE_LENGTH		= "disk.write.queue.length";
	public static final String ST_DISK_WRITE_QUEUE_BYTES		= "disk.write.queue.bytes";
	public static final String ST_DISK_WRITE_REQUEST_COUNT		= "disk.write.request.count";
	public static final String ST_DISK_WRITE_REQUEST_BLOCKS		= "disk.write.request.blocks";
	public static final String ST_DISK_WRITE_BYTES_TOTAL		= "disk.write.bytes.total";
	public static final String ST_DISK_WRITE_BYTES_SINGLE		= "disk.write.bytes.single";
	public static final String ST_DISK_WRITE_BYTES_MULTIPLE		= "disk.write.bytes.multiple";
	public static final String ST_DISK_WRITE_IO_TIME			= "disk.write.io.time";
	public static final String ST_DISK_WRITE_IO_COUNT			= "disk.write.io.count";

		// NETWORK

	public static final String ST_NET_WRITE_CONTROL_WAIT_COUNT			= "net.write.control.wait.count";
	public static final String ST_NET_WRITE_CONTROL_NP_COUNT			= "net.write.control.np.count";
	public static final String ST_NET_WRITE_CONTROL_P_COUNT				= "net.write.control.p.count";
	public static final String ST_NET_WRITE_CONTROL_ENTITY_COUNT		= "net.write.control.entity.count";
	public static final String ST_NET_WRITE_CONTROL_CON_COUNT			= "net.write.control.con.count";
	public static final String ST_NET_WRITE_CONTROL_READY_CON_COUNT		= "net.write.control.ready.con.count";
	public static final String ST_NET_WRITE_CONTROL_READY_BYTE_COUNT	= "net.write.control.ready.byte.count";

	public static final String ST_NET_READ_CONTROL_LOOP_COUNT			= "net.read.control.loop.count";
	public static final String ST_NET_READ_CONTROL_NP_COUNT				= "net.read.control.np.count";
	public static final String ST_NET_READ_CONTROL_P_COUNT				= "net.read.control.p.count";
	public static final String ST_NET_READ_CONTROL_WAIT_COUNT			= "net.read.control.wait.count";
	public static final String ST_NET_READ_CONTROL_ENTITY_COUNT			= "net.read.control.entity.count";
	public static final String ST_NET_READ_CONTROL_CON_COUNT			= "net.read.control.con.count";
	public static final String ST_NET_READ_CONTROL_READY_CON_COUNT		= "net.read.control.ready.con.count";

		// TCP

	public static final String ST_NET_TCP_OUT_CONNECT_QUEUE_LENGTH		= "net.tcp.outbound.connect.queue.length";
	public static final String ST_NET_TCP_OUT_PENDING_QUEUE_LENGTH		= "net.tcp.outbound.pending.queue.length";
	public static final String ST_NET_TCP_OUT_PENDING_PP_QUEUE_LENGTH	= "net.tcp.outbound.pending.pp.queue.length";
	public static final String ST_NET_TCP_OUT_CANCEL_QUEUE_LENGTH		= "net.tcp.outbound.cancel.queue.length";
	public static final String ST_NET_TCP_OUT_CLOSE_QUEUE_LENGTH		= "net.tcp.outbound.close.queue.length";

	public static final String ST_NET_TCP_SELECT_WRITE_COUNT			= "net.tcp.select.write.count";
	public static final String ST_NET_TCP_SELECT_READ_COUNT				= "net.tcp.select.read.count";

		// UDP
	
	public static final String ST_NET_UDP_SEND_PACKET_COUNT				= "net.udp.send.packet.count";
	public static final String ST_NET_UDP_SEND_BYTE_COUNT				= "net.udp.send.byte.count";
	public static final String ST_NET_UDP_RECEIVE_PACKET_COUNT			= "net.udp.receive.packet.count";
	public static final String ST_NET_UDP_RECEIVE_BYTE_COUNT			= "net.udp.receive.byte.count";
	
		// HTTP

	public static final String ST_NET_HTTP_IN_REQUEST_COUNT				= "net.http.inbound.request.count";
	public static final String ST_NET_HTTP_IN_REQUEST_OK_COUNT			= "net.http.inbound.request.ok.count";
	public static final String ST_NET_HTTP_IN_REQUEST_INVALID_COUNT		= "net.http.inbound.request.invalid.count";
	public static final String ST_NET_HTTP_IN_REQUEST_WEBSEED_COUNT		= "net.http.inbound.request.webseed.count";
	public static final String ST_NET_HTTP_IN_REQUEST_GETRIGHT_COUNT	= "net.http.inbound.request.getright.count";

		// Peer Control

	public static final String ST_PEER_CONTROL_SCHEDULE_COUNT	= "peer.control.schedule.count";
	public static final String ST_PEER_CONTROL_LOOP_COUNT		= "peer.control.loop.count";
	public static final String ST_PEER_CONTROL_YIELD_COUNT		= "peer.control.yield.count";
	public static final String ST_PEER_CONTROL_WAIT_COUNT		= "peer.control.wait.count";
	public static final String ST_PEER_CONTROL_WAIT_TIME		= "peer.control.wait.time";

		// Peer Manager

	public static final String ST_PEER_MANAGER_COUNT					= "peer.manager.count";
	public static final String ST_PEER_MANAGER_PEER_COUNT				= "peer.manager.peer.count";
	public static final String ST_PEER_MANAGER_PEER_SNUBBED_COUNT		= "peer.manager.peer.snubbed.count";
	public static final String ST_PEER_MANAGER_PEER_STALLED_DISK_COUNT	= "peer.manager.peer.stalled.disk.count";

		// Peer DB
	
	public static final String ST_PEER_DB_CONNECTION_COUNT					= "peer.db.peer.count";
	public static final String ST_PEER_DB_CONNECTION_DATA_ID_COUNT			= "peer.db.data.id.count";
	public static final String ST_PEER_DB_CONNECTION_DATA_ID_ENTRY_TOTAL	= "peer.db.data.id.peer.total";
	
		// Tracker

	public static final String ST_TRACKER_READ_BYTES		= "tracker.read.bytes.total";
	public static final String ST_TRACKER_WRITE_BYTES		= "tracker.write.bytes.total";
	public static final String ST_TRACKER_ANNOUNCE_COUNT	= "tracker.announce.count";
	public static final String ST_TRACKER_ANNOUNCE_TIME		= "tracker.announce.time";
	public static final String ST_TRACKER_SCRAPE_COUNT		= "tracker.scrape.count";
	public static final String ST_TRACKER_SCRAPE_TIME		= "tracker.scrape.time";

		// xfer (persistent)

	public static final String ST_XFER_UPLOADED_PROTOCOL_BYTES		= "xfer.upload.protocol.bytes.total";
	public static final String ST_XFER_UPLOADED_DATA_BYTES			= "xfer.upload.data.bytes.total";
	public static final String ST_XFER_DOWNLOADED_PROTOCOL_BYTES	= "xfer.download.protocol.bytes.total";
	public static final String ST_XFER_DOWNLOADED_DATA_BYTES		= "xfer.download.data.bytes.total";


	public static final String	POINT 		= "Point";
	public static final String	CUMULATIVE 	= "Cumulative";

	private static final List		stats_names	= new ArrayList();
	private static final Map		stats_types	= new HashMap();

	private static final String[][] _ST_ALL = {

		{ ST_DISK_READ_QUEUE_LENGTH,				POINT },
		{ ST_DISK_READ_QUEUE_BYTES,					POINT },
		{ ST_DISK_READ_REQUEST_COUNT,				CUMULATIVE },
		{ ST_DISK_READ_REQUEST_SINGLE,				CUMULATIVE },
		{ ST_DISK_READ_REQUEST_MULTIPLE,			CUMULATIVE },
		{ ST_DISK_READ_REQUEST_BLOCKS,				CUMULATIVE },
		{ ST_DISK_READ_BYTES_TOTAL,					CUMULATIVE },
		{ ST_DISK_READ_BYTES_SINGLE,				CUMULATIVE },
		{ ST_DISK_READ_BYTES_MULTIPLE,				CUMULATIVE },
		{ ST_DISK_READ_IO_TIME,						CUMULATIVE },
		{ ST_DISK_READ_IO_COUNT,					CUMULATIVE },

		{ ST_DISK_WRITE_QUEUE_LENGTH,				POINT },
		{ ST_DISK_WRITE_QUEUE_BYTES,				POINT },
		{ ST_DISK_WRITE_REQUEST_COUNT,				CUMULATIVE },
		{ ST_DISK_WRITE_REQUEST_BLOCKS,				CUMULATIVE },
		{ ST_DISK_WRITE_BYTES_TOTAL,				CUMULATIVE },
		{ ST_DISK_WRITE_BYTES_SINGLE,				CUMULATIVE },
		{ ST_DISK_WRITE_BYTES_MULTIPLE,				CUMULATIVE },
		{ ST_DISK_WRITE_IO_TIME,					CUMULATIVE },
		{ ST_DISK_WRITE_IO_COUNT,					CUMULATIVE },

		{ ST_NET_WRITE_CONTROL_WAIT_COUNT,			CUMULATIVE },
		{ ST_NET_WRITE_CONTROL_P_COUNT,				CUMULATIVE },
		{ ST_NET_WRITE_CONTROL_NP_COUNT,			CUMULATIVE },
		{ ST_NET_WRITE_CONTROL_ENTITY_COUNT,		POINT },
		{ ST_NET_WRITE_CONTROL_CON_COUNT,			POINT },
		{ ST_NET_WRITE_CONTROL_READY_CON_COUNT,		POINT },
		{ ST_NET_WRITE_CONTROL_READY_BYTE_COUNT,	POINT },

		{ ST_NET_READ_CONTROL_LOOP_COUNT,			CUMULATIVE },
		{ ST_NET_READ_CONTROL_P_COUNT,				CUMULATIVE },
		{ ST_NET_READ_CONTROL_NP_COUNT,				CUMULATIVE },
		{ ST_NET_READ_CONTROL_WAIT_COUNT,			CUMULATIVE },
		{ ST_NET_READ_CONTROL_ENTITY_COUNT,			POINT },
		{ ST_NET_READ_CONTROL_CON_COUNT,			POINT },
		{ ST_NET_READ_CONTROL_READY_CON_COUNT,		POINT },

		{ ST_NET_TCP_OUT_CONNECT_QUEUE_LENGTH,		POINT },
		{ ST_NET_TCP_OUT_PENDING_QUEUE_LENGTH,		POINT },
		{ ST_NET_TCP_OUT_PENDING_PP_QUEUE_LENGTH,	POINT },
		{ ST_NET_TCP_OUT_CANCEL_QUEUE_LENGTH,		POINT },
		{ ST_NET_TCP_OUT_CLOSE_QUEUE_LENGTH,		POINT },

		{ ST_NET_TCP_SELECT_WRITE_COUNT,			CUMULATIVE },
		{ ST_NET_TCP_SELECT_READ_COUNT,				CUMULATIVE },

		{ ST_NET_UDP_SEND_PACKET_COUNT,				CUMULATIVE },
		{ ST_NET_UDP_SEND_BYTE_COUNT,				CUMULATIVE },
		{ ST_NET_UDP_RECEIVE_PACKET_COUNT,			CUMULATIVE },
		{ ST_NET_UDP_RECEIVE_BYTE_COUNT,			CUMULATIVE },
	
		
		{ ST_NET_HTTP_IN_REQUEST_COUNT,				CUMULATIVE },
		{ ST_NET_HTTP_IN_REQUEST_OK_COUNT,			CUMULATIVE },
		{ ST_NET_HTTP_IN_REQUEST_INVALID_COUNT,		CUMULATIVE },
		{ ST_NET_HTTP_IN_REQUEST_WEBSEED_COUNT,		CUMULATIVE },
		{ ST_NET_HTTP_IN_REQUEST_GETRIGHT_COUNT,	CUMULATIVE },

		{ ST_PEER_CONTROL_SCHEDULE_COUNT,			CUMULATIVE },
		{ ST_PEER_CONTROL_LOOP_COUNT,				CUMULATIVE },
		{ ST_PEER_CONTROL_YIELD_COUNT,				CUMULATIVE },
		{ ST_PEER_CONTROL_WAIT_COUNT,				CUMULATIVE },
		{ ST_PEER_CONTROL_WAIT_TIME,				CUMULATIVE },

		{ ST_PEER_MANAGER_COUNT,					POINT },
		{ ST_PEER_MANAGER_PEER_COUNT,				POINT },
		{ ST_PEER_MANAGER_PEER_SNUBBED_COUNT,		POINT },
		{ ST_PEER_MANAGER_PEER_STALLED_DISK_COUNT,	POINT },

		{ ST_PEER_DB_CONNECTION_COUNT,					POINT },
		{ ST_PEER_DB_CONNECTION_DATA_ID_COUNT,			POINT },
		{ ST_PEER_DB_CONNECTION_DATA_ID_ENTRY_TOTAL,	POINT },
		
		{ ST_TRACKER_READ_BYTES,					CUMULATIVE },
		{ ST_TRACKER_WRITE_BYTES,					CUMULATIVE },
		{ ST_TRACKER_ANNOUNCE_COUNT,				CUMULATIVE },
		{ ST_TRACKER_ANNOUNCE_TIME,					CUMULATIVE },
		{ ST_TRACKER_SCRAPE_COUNT,					CUMULATIVE },
		{ ST_TRACKER_SCRAPE_TIME,					CUMULATIVE },

		{ ST_XFER_UPLOADED_PROTOCOL_BYTES,			CUMULATIVE },
		{ ST_XFER_UPLOADED_DATA_BYTES,				CUMULATIVE },
		{ ST_XFER_DOWNLOADED_PROTOCOL_BYTES,		CUMULATIVE },
		{ ST_XFER_DOWNLOADED_DATA_BYTES,			CUMULATIVE },
	};

	static{

		addStatsDefinitions( _ST_ALL );

		AEDiagnostics.addEvidenceGenerator(
			new AEDiagnosticsEvidenceGenerator()
			{
		  		@Override
				  public void
	    		generate(
	    			IndentWriter writer )
	    		{
					writer.println( "Stats" );

					boolean	turn_on_averages = !getEnableAverages();

					try{
						writer.indent();

						if ( turn_on_averages ){

							setEnableAverages( true );

							try{
								Thread.sleep( AVERAGE_PERIOD * 5 );

							}catch( Throwable e ){
							}
						}

						Set	types = new HashSet();

						types.add( ST_ALL );

						Map	reply = getStats( types );

						Iterator	it = reply.entrySet().iterator();

						List	lines = new ArrayList();

						while( it.hasNext()){

							Map.Entry	entry = (Map.Entry)it.next();

							lines.add( entry.getKey() + " -> " + entry.getValue());
						}

						Collections.sort( lines );

						for ( int i=0;i<lines.size();i++){

							writer.println((String)lines.get(i));
						}

					}finally{

						if ( turn_on_averages ){

							setEnableAverages( false );
						}

						writer.exdent();
					}
	    		}
			});
	}

	private static final CopyOnWriteList	providers 	= new CopyOnWriteList();

	private static  Map	averages	= new HashMap();

	private static boolean 	enable_averages;
	private static Timer	average_timer;

	private static final CopyOnWriteList provider_listeners = new CopyOnWriteList();
	private static final CopyOnWriteList derived_generators = new CopyOnWriteList();

	public static void
	addStatsDefinitions(
		String[][]		stats )
	{
		for (int i=0;i<stats.length;i++){

			String	name = stats[i][0];

			stats_names.add( name );

			stats_types.put( name, stats[i][1] );
		}
	}

	public static Map<String, Object>
	getStats(
		Set		types )
	{
		Set	expanded = new HashSet();

		Iterator	it = types.iterator();

		while( it.hasNext()){

			String	type = (String)it.next();

			if ( type.endsWith( ".average" )){

				type = type.substring(0,type.length()-8);
			}

			if ( !type.endsWith("*")){

				type = type + ".*";
			}

			Pattern pattern = Pattern.compile( type );

			for (int i=0;i<stats_names.size();i++){

				String	s = (String)stats_names.get(i);

				if ( pattern.matcher( s ).matches()){

					expanded.add( s );
				}
			}

			Iterator provider_it = providers.iterator();

			while( provider_it.hasNext()){

				Object[]	provider_entry = (Object[])provider_it.next();

				Set provider_types = (Set)provider_entry[0];

				Iterator pt_it = provider_types.iterator();

				while( pt_it.hasNext()){

					String	s = (String)pt_it.next();

					if ( pattern.matcher( s ).matches()){

						expanded.add( s );
					}
				}
			}

			Iterator derived_it = derived_generators.iterator();

			while( derived_it.hasNext()){

				try{

					((derivedStatsGenerator)derived_it.next()).match( pattern, expanded );

				}catch( Throwable e ){

					Debug.printStackTrace( e );
				}
			}
		}

		Map<String, Object> result = getStatsSupport( expanded );

		Map	ave = averages;

		if ( ave != null ){

			it = result.keySet().iterator();

			Map	ave_results = new HashMap();

			while( it.hasNext()){

				String	key = (String)it.next();

				Object[]	a_entry = (Object[])ave.get( key );

				if ( a_entry != null ){

					Average	average = (Average)a_entry[0];

					ave_results.put( key + ".average", new Long((long)average.getAverage()));
				}
			}

			result.putAll( ave_results );
		}

		Iterator derived_it = derived_generators.iterator();

		while( derived_it.hasNext()){

			try{

				((derivedStatsGenerator)derived_it.next()).generate( result );

			}catch( Throwable e ){

				Debug.printStackTrace( e );
			}
		}

		return( result );
	}

	protected static Map<String, Object>
	getStatsSupport(
		Set		types )
	{
		Map<String, Object>	result = new HashMap<>();

		Iterator it = providers.iterator();

		while( it.hasNext()){

			Object[]	provider_entry = (Object[])it.next();

			Map	provider_result = new HashMap();

			Set	target_types;

			if ( types == null ){

				target_types = (Set)provider_entry[0];
			}else{

				target_types = types;
			}

			try{
				((CoreStatsProvider)provider_entry[1]).updateStats( target_types, provider_result );

				Iterator pit = provider_result.entrySet().iterator();

				while( pit.hasNext()){

					Map.Entry	pe = (Map.Entry)pit.next();

					String	key = (String)pe.getKey();
					Object	obj	= pe.getValue();

					if ( obj instanceof Long ){

						Long	old = (Long)result.get(key);

						if ( old == null ){

							result.put( key, obj );

						}else{

							long	v = ((Long)obj).longValue();

							result.put( key, new Long( v + old.longValue()));
						}
					}else{

						result.put( key, obj );
					}
				}
			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}

		return( result );
	}

	public static void
	registerProvider(
		Set							types,
		CoreStatsProvider provider )
	{
		synchronized( providers ){

			providers.add( new Object[]{ types, provider });
		}

		fireProvidersChangeListeners();
	}

	public static void
	addProvidersChangeListener(
		providersChangeListener		l )
	{
		provider_listeners.add( l );
	}

	protected static void
	fireProvidersChangeListeners()
	{
		Iterator it = provider_listeners.iterator();

		while( it.hasNext()){

			try{
				((providersChangeListener)it.next()).providersChanged();

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	public static void
	registerDerivedStatsGenerator(
		derivedStatsGenerator	gen )
	{
		derived_generators.add( gen );
	}

	public static synchronized void
	setEnableAverages(
		boolean		enabled )
	{
		if ( enabled == enable_averages ){

			return;
		}

		enable_averages = enabled;

		if ( enabled ){

			if ( average_timer == null ){

				average_timer = new Timer( "CoreStats:average" );

				averages = new HashMap();

				average_timer.addPeriodicEvent(
					AVERAGE_PERIOD,
					new TimerEventPerformer()
					{
						private final Map	ave = averages;

						@Override
						public void
						perform(
							TimerEvent	event )
						{
							Map	stats = getStatsSupport( null );

							Iterator	it = stats.entrySet().iterator();

							boolean	new_averages = false;

							while( it.hasNext()){

								Map.Entry	entry = (Map.Entry)it.next();

								String	key 	= (String)entry.getKey();
								Object	value 	= entry.getValue();

								if ( value instanceof Long ){

									long	last_value;
									Average	a;
									boolean	new_average;

									Object[] a_entry = (Object[])ave.get( key );

									if ( a_entry == null ){

										a 			= AverageFactory.MovingImmediateAverage( 10 );
										last_value	= 0;

										a_entry = new Object[]{ a, value };

										ave.put( key, a_entry );

										new_averages = new_average = true;

									}else{
										a			= (Average)a_entry[0];
										last_value	= ((Long)a_entry[1]).longValue();

										new_average = false;
									}

									if ( stats_types.get( key ) == CUMULATIVE ){

											// skip initial value as 'last_value' is invalid

										if ( !new_average ){

											a.update(((Long)value).longValue() - last_value);
										}
									}else{

										a.update(((Long)value).longValue());

									}

									a_entry[1] = value;
								}
							}

							if ( new_averages ){

								fireProvidersChangeListeners();
							}
						}
					});
			}
		}else{

			if ( average_timer != null ){

				average_timer.destroy();

				average_timer = null;

				averages	= null;
			}
		}
	}

	public static synchronized boolean
	getEnableAverages()
	{
		return( enable_averages );
	}

	public interface
	providersChangeListener
	{
		public void
		providersChanged();
	}

	public interface
	derivedStatsGenerator
	{
		public void
		match(
			Pattern		p,
			Set			required );

		public void
		generate(
			Map	map );
	}
}
