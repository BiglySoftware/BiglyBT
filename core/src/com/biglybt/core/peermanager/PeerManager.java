/*
 * Created on Jan 20, 2005
 * Created by Alon Rohter
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

package com.biglybt.core.peermanager;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.disk.DiskManagerReadRequest;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.networkmanager.ConnectionEndpoint;
import com.biglybt.core.networkmanager.LimitedRateGroup;
import com.biglybt.core.networkmanager.NetworkConnection;
import com.biglybt.core.networkmanager.NetworkManager;
import com.biglybt.core.networkmanager.ProtocolEndpoint;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.networkmanager.impl.IncomingConnectionManager;
import com.biglybt.core.networkmanager.impl.TransportHelper;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerListener;
import com.biglybt.core.peer.PEPeerManagerAdapter;
import com.biglybt.core.peer.PEPeerManagerListener;
import com.biglybt.core.peer.PEPeerManagerStats;
import com.biglybt.core.peer.PEPeerSource;
import com.biglybt.core.peer.PEPeerStats;
import com.biglybt.core.peer.PEPiece;
import com.biglybt.core.peer.impl.PEPeerControl;
import com.biglybt.core.peer.impl.PEPeerControlHashHandler;
import com.biglybt.core.peer.impl.PEPeerStatsImpl;
import com.biglybt.core.peer.impl.PEPeerTransport;
import com.biglybt.core.peer.impl.PEPeerTransportFactory;
import com.biglybt.core.peer.util.PeerIdentityDataID;
import com.biglybt.core.peer.util.PeerIdentityManager;
import com.biglybt.core.peermanager.messaging.MessageManager;
import com.biglybt.core.peermanager.messaging.MessageStreamDecoder;
import com.biglybt.core.peermanager.messaging.MessageStreamEncoder;
import com.biglybt.core.peermanager.messaging.MessageStreamFactory;
import com.biglybt.core.peermanager.messaging.bittorrent.BTHandshake;
import com.biglybt.core.peermanager.messaging.bittorrent.BTMessageDecoder;
import com.biglybt.core.peermanager.messaging.bittorrent.BTMessageEncoder;
import com.biglybt.core.peermanager.peerdb.PeerExchangerItem;
import com.biglybt.core.peermanager.peerdb.PeerItem;
import com.biglybt.core.peermanager.piecepicker.PiecePicker;
import com.biglybt.core.peermanager.piecepicker.util.BitFlags;
import com.biglybt.core.stats.CoreStats;
import com.biglybt.core.stats.CoreStatsProvider;
import com.biglybt.core.torrent.TOTorrentFile;
import com.biglybt.core.tracker.TrackerPeerSource;
import com.biglybt.core.tracker.client.TRTrackerAnnouncerResponse;
import com.biglybt.core.util.*;
import com.biglybt.core.util.bloom.BloomFilter;
import com.biglybt.core.util.bloom.BloomFilterFactory;
import com.biglybt.pif.peers.PeerDescriptor;

/**
 *
 */
public class PeerManager implements CoreStatsProvider {
	private static final LogIDs LOGID = LogIDs.PEER;

	private static final Object	KEY_TRANSPORT_PROBE	= new Object();
	
	private static final PeerManager instance = new PeerManager();

	private static final int	PENDING_TIMEOUT	= 10*1000;

	private static final AEMonitor	timer_mon = new AEMonitor( "PeerManager:timeouts" );
	private static AEThread2	timer_thread;
	static final Set<PeerManagerRegistrationImpl>	timer_targets = new HashSet<>();

	
	private static boolean enable_public_tcp_peers	= true;
	private static boolean enable_public_udp_peers	= true;
	private static boolean socks_data				= false;

	static{
		COConfigurationManager.addAndFireParameterListeners(
			new String[]{
				ConfigKeys.Connection.BCFG_PEERCONTROL_TCP_PUBLIC_ENABLE,
				ConfigKeys.Connection.BCFG_PEERCONTROL_UDP_PUBLIC_ENABLE,
				ConfigKeys.Connection.BCFG_PROXY_DATA_ENABLE},
			(ignore)->{
				{
					enable_public_tcp_peers		= COConfigurationManager.getBooleanParameter( ConfigKeys.Connection.BCFG_PEERCONTROL_TCP_PUBLIC_ENABLE );
					enable_public_udp_peers		= COConfigurationManager.getBooleanParameter( ConfigKeys.Connection.BCFG_PEERCONTROL_UDP_PUBLIC_ENABLE );
					socks_data					= COConfigurationManager.getBooleanParameter( ConfigKeys.Connection.BCFG_PROXY_DATA_ENABLE );
				}
			});
	}
	
	protected static void
	registerForTimeouts(
			PeerManagerRegistrationImpl		reg )
	{
		try{
			timer_mon.enter();

			timer_targets.add( reg );

			if ( timer_thread == null ){

				timer_thread =
					new AEThread2( "PeerManager:timeouts", true )
				{
					@Override
					public void
					run()
					{
						int	idle_time	= 0;

						while( true ){

							try{
								Thread.sleep( 1000 );

							}catch( Throwable e ){
							}

							try{
								timer_mon.enter();

								if ( timer_targets.size() == 0 ){

									idle_time += 1000;

									if ( idle_time >= 30*1000 ){

										timer_thread = null;

										break;
									}
								}else{

									idle_time = 0;

									Iterator<PeerManagerRegistrationImpl>	it = timer_targets.iterator();

									while( it.hasNext()){

										PeerManagerRegistrationImpl	registration = it.next();

										if ( !registration.timeoutCheck()){

											it.remove();
										}
									}
								}
							}finally{

								timer_mon.exit();
							}
						}
					}
				};

				timer_thread.start();
			}
		}finally{

			timer_mon.exit();
		}
	}

	/**
	 * Get the singleton instance of the peer manager.
	 * @return the peer manager
	 */
	public static PeerManager getSingleton() {  return instance;  }



	private final Map<HashWrapper,CopyOnWriteList<PeerManagerRegistrationImpl>> registered_legacy_managers 	= new HashMap<>();
	private final Map<String,PeerManagerRegistrationImpl>			 			registered_links				= new HashMap<>();

	private final ByteBuffer legacy_handshake_header;


	private PeerManager() {
		legacy_handshake_header = ByteBuffer.allocate( 20 );
		legacy_handshake_header.put( (byte)BTHandshake.PROTOCOL.length() );
		legacy_handshake_header.put( BTHandshake.PROTOCOL.getBytes() );
		legacy_handshake_header.flip();

		Set<String>	types = new HashSet<>();

		types.add( CoreStats.ST_PEER_MANAGER_COUNT );
		types.add( CoreStats.ST_PEER_MANAGER_PEER_COUNT );
		types.add( CoreStats.ST_PEER_MANAGER_PEER_SNUBBED_COUNT );
		types.add( CoreStats.ST_PEER_MANAGER_PEER_STALLED_DISK_COUNT );

		CoreStats.registerProvider( types, this );

		init();
	}

	@Override
	public void
	updateStats(
		Set		types,
		Map		values )
	{
		if ( types.contains( CoreStats.ST_PEER_MANAGER_COUNT )){

			values.put( CoreStats.ST_PEER_MANAGER_COUNT, new Long( registered_legacy_managers.size()));
		}

		if ( 	types.contains( CoreStats.ST_PEER_MANAGER_PEER_COUNT ) ||
				types.contains( CoreStats.ST_PEER_MANAGER_PEER_SNUBBED_COUNT ) ||
				types.contains( CoreStats.ST_PEER_MANAGER_PEER_STALLED_DISK_COUNT )){

			long	total_peers 				= 0;
			long	total_snubbed_peers			= 0;
			long	total_stalled_pending_load	= 0;

			synchronized( registered_legacy_managers ){

				Iterator<CopyOnWriteList<PeerManagerRegistrationImpl>>	it = registered_legacy_managers.values().iterator();

				while( it.hasNext()){

					CopyOnWriteList<PeerManagerRegistrationImpl>	registrations = it.next();

					Iterator<PeerManagerRegistrationImpl>	it2 = registrations.iterator();

					while( it2.hasNext()){

						PeerManagerRegistrationImpl reg = it2.next();

						PEPeerControl control = reg.getActiveControl();

						if ( control != null ){

							total_peers 				+= control.getNbPeers();
							total_snubbed_peers			+= control.getNbPeersSnubbed();
							total_stalled_pending_load	+= control.getNbPeersStalledPendingLoad();
						}
					}
				}
			}
			
			if ( types.contains( CoreStats.ST_PEER_MANAGER_PEER_COUNT )){

				values.put( CoreStats.ST_PEER_MANAGER_PEER_COUNT, new Long( total_peers ));
			}
			if ( types.contains( CoreStats.ST_PEER_MANAGER_PEER_SNUBBED_COUNT )){

				values.put( CoreStats.ST_PEER_MANAGER_PEER_SNUBBED_COUNT, new Long( total_snubbed_peers ));
			}
			if ( types.contains( CoreStats.ST_PEER_MANAGER_PEER_STALLED_DISK_COUNT )){

				values.put( CoreStats.ST_PEER_MANAGER_PEER_STALLED_DISK_COUNT, new Long( total_stalled_pending_load ));
			}
		}
	}

	protected void
	init()
	{
		MessageManager.getSingleton().initialize();  //ensure it gets initialized

		NetworkManager.ByteMatcher matcher =
			new NetworkManager.ByteMatcher()
		{
			@Override
			public int matchThisSizeOrBigger(){	return( 48 );}
			@Override
			public int maxSize() {  return 48;  }
			@Override
			public int minSize() { return 20; }

			@Override
			public Object
			matches(
				TransportHelper		transport,
				ByteBuffer 			to_compare,
				int 				local_port )
			{
				InetSocketAddress	address = transport.getAddress();

				int old_limit = to_compare.limit();
				int old_position = to_compare.position();

				to_compare.limit( old_position + 20 );

				PeerManagerRegistrationImpl	routing_data = null;

				PeerManagerRegistrationRoutingFailed routing_failed = null;
				
				if ( to_compare.equals( legacy_handshake_header )){  //compare header
					
					to_compare.limit( old_position + 48 );
					to_compare.position( old_position + 28 );

					byte[]	hash = new byte[to_compare.remaining()];

					to_compare.get( hash );

					HashWrapper hash_wrapper = new HashWrapper( hash );
					
					CopyOnWriteList<PeerManagerRegistrationImpl>	registrations;
					
					synchronized( registered_legacy_managers ){
						
						registrations = registered_legacy_managers.get( hash_wrapper );
					}
					
					if ( registrations != null ){

						routing_data = registrations.get(0);
						
						if ( registrations.size() > 1 ){
							
							for ( PeerManagerRegistrationImpl r: registrations ){
								
								PeerManagerRegistrationAdapter adapter = r.getAdapter();
								
								if ( adapter.getHashOverride() != null ){
									
									if ( local_port == adapter.getHashOverrideLocalPort( true )){	// only if allocated as if it is't it is an out-of-date connection
										
										routing_data = r;
										
										break;
									}
								}else{
									
									routing_data = r;	// default non-override
								}
							}
						}
					}
					
					if ( routing_data == null ){
						
							// Header matched but no matching hash, use dummy routing data to
							// ensure failure. If we don't then it appears as if we don't have
							// sufficient information to decide if it is routable or not
						
						routing_failed = new PeerManagerRegistrationRoutingFailed( hash_wrapper );
					}
				}

					//restore buffer structure
				
				to_compare.limit( old_limit );
				to_compare.position( old_position );

				if ( routing_failed != null ){
					
					return( routing_failed );
				}
				
				if ( routing_data != null ){

					if ( !routing_data.isActive()){

						if ( routing_data.isKnownSeed( address )){

							String reason = "Activation request from " + address + " denied as known seed";

							if (Logger.isEnabled()){
								Logger.log(new LogEvent(LOGID, reason  ));
							}

							transport.close( reason );

							routing_data = null;

						}else{

							int at = routing_data.getAdapter().activateRequest( address );
							
							if ( at == PeerManagerRegistrationAdapter.AT_DECLINED ){

								String reason = "Activation request from " + address + " denied by rules";

								if (Logger.isEnabled()){
									Logger.log(new LogEvent(LOGID, reason ));
								}

								transport.close( reason );

								routing_data = null;
								
							}else if ( at == PeerManagerRegistrationAdapter.AT_ACCEPTED_PROBE ){
								
								transport.setUserData( KEY_TRANSPORT_PROBE, "" );
							}
						}
					}
				}

				return routing_data;
			}

			@Override
			public Object
			minMatches(
					TransportHelper		transport,
					ByteBuffer 			to_compare,
					int 				port )
			{
				boolean matches = false;

				int old_limit = to_compare.limit();
				int old_position = to_compare.position();

				to_compare.limit( old_position + 20 );

				if( to_compare.equals( legacy_handshake_header ) ) {
					matches = true;
				}

				//restore buffer structure

				to_compare.limit( old_limit );
				to_compare.position( old_position );

				return matches?"":null;
			}

			@Override
			public byte[][]
			getSharedSecrets()
			{
				return( null );	// registered manually above
			}

			@Override
			public int
			getSpecificPort()
			{
				return( -1 );
			}
			
			@Override
			public String 
			getDescription()
			{
					// shared secrets registered earlier with suitable description
				
				return( "PeerManager" );
			}
		};

		// register for incoming connection routing
		NetworkManager.getSingleton().requestIncomingConnectionRouting(
				matcher,
				new NetworkManager.RoutingListener()
				{
					@Override
					public void
					connectionRouted(
						NetworkConnection 	connection,
						Object 				routing_data )
					{
						PeerManagerRegistration	registration = (PeerManagerRegistration)routing_data;

						registration.route( connection, null );
					}

					@Override
					public boolean
					autoCryptoFallback()
					{
						return( false );
					}
				},
				new MessageStreamFactory() {
					@Override
					public MessageStreamEncoder createEncoder() {  return new BTMessageEncoder();  }
					@Override
					public MessageStreamDecoder createDecoder() {  return new BTMessageDecoder();  }
				});
	}

	public PeerManagerRegistration
	manualMatchHash(
		InetSocketAddress	address,
		byte[]				hash )
	{
		PeerManagerRegistrationImpl	routing_data = null;

		synchronized( registered_legacy_managers ){

			CopyOnWriteList<PeerManagerRegistrationImpl>	registrations = registered_legacy_managers.get( new HashWrapper( hash ));

			if ( registrations != null ){

				routing_data = registrations.get(0);
			}
		}

		if ( routing_data != null ){

			if ( !routing_data.isActive()){

				if ( routing_data.isKnownSeed( address )){

					if (Logger.isEnabled()){
						Logger.log(new LogEvent(LOGID, "Activation request from " + address + " denied as known seed" ));
					}

					routing_data = null;

				}else{

					if ( routing_data.getAdapter().activateRequest( address ) == PeerManagerRegistrationAdapter.AT_DECLINED ){

						if (Logger.isEnabled()){
							Logger.log(new LogEvent(LOGID, "Activation request from " + address + " denied by rules" ));
						}

						routing_data = null;
					}
				}
			}
		}

		return routing_data;
	}

	public PeerManagerRegistration
	manualMatchLink(
			InetSocketAddress	address,
			String				link )
	{
		byte[]	hash;

		synchronized( registered_legacy_managers ){

			PeerManagerRegistrationImpl	registration = registered_links.get( link );

			if ( registration == null ){

				return( null );
			}

			hash = registration.getHash();
		}

		return( manualMatchHash( address, hash ));
	}

	public void
	manualRoute(
		PeerManagerRegistration		_registration,
		NetworkConnection			_connection,
		PeerManagerRoutingListener	_listener )
	{
		_registration.route( _connection, _listener );
	}

	public PeerManagerRegistration
	registerLegacyManager(
		HashWrapper						hash,
		PeerManagerRegistrationAdapter  adapter )
	{
		synchronized( registered_legacy_managers ){

			CopyOnWriteList<PeerManagerRegistrationImpl>	registrations = registered_legacy_managers.get( hash );

			byte[][]	secrets = adapter.getSecrets();

			if ( registrations == null ){

				registrations = new CopyOnWriteList<>(1);

				registered_legacy_managers.put( hash, registrations );

				IncomingConnectionManager.getSingleton().addSharedSecrets( adapter.getDescription(), secrets );
			}

			PeerManagerRegistrationImpl	registration = new PeerManagerRegistrationImpl( hash, adapter );

			registrations.add( registration );

			byte[] override = adapter.getHashOverride();
			
			if ( override != null ){
				
				HashWrapper ov_hw = new HashWrapper( override );
				
				CopyOnWriteList<PeerManagerRegistrationImpl>	ov_registrations = registered_legacy_managers.get( ov_hw );

				if ( ov_registrations == null ){

					ov_registrations = new CopyOnWriteList<>(1);

					registered_legacy_managers.put( ov_hw, ov_registrations );
				}

				ov_registrations.add( registration );
			}
			
			return( registration );
		}
	}


	private class
	PeerManagerRegistrationRoutingFailed
		implements PeerManagerRegistration
	{
		private final HashWrapper	hash;
		
		PeerManagerRegistrationRoutingFailed(
			HashWrapper		_hash )
		{
			hash	= _hash;
		}
		
		public TOTorrentFile
		getLink(
			String			link )
		{
			return( null );
		}

	    public void
		removeLink(
			String			link )
		{
		}

	    public void
		addLink(
			String			link,
			TOTorrentFile	target )

			throws Exception
		{
		}

		public void
		route(
			NetworkConnection 			connection,
			PeerManagerRoutingListener	listener )
		{
			String hash_str = ByteFormatter.nicePrint( hash.getHash(), true );
			
			if (Logger.isEnabled()){
				
				Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
						"Incoming connection from [" + connection
						+ "] refused, hash " + hash_str + " unknown/inactive" ));
			}
			
			connection.close( "hash " + hash_str + " unknown/inactive" );
		}
		
		public void
		activate(
			PEPeerControl	peer_control )
		{
			Debug.out( "eh" );
		}

		public void
		deactivate()
		{
			Debug.out( "eh" );
		}

		public void
		unregister()
		{	
			Debug.out( "eh" );
		}
	    
	    public List<PeerManagerRegistration>
	    getOtherRegistrationsForHash()
	    {
	    	Debug.out( "eh" );
	    	
	    	return( Collections.emptyList());
	    }
	    
	    public int 
	    getHashOverrideLocalPort(
	    	boolean	only_if_allocated )
	    {
	    	Debug.out( "eh" );
	    	
	    	return( 0 );
	    }
	}
	
	private class
	PeerManagerRegistrationImpl
		implements PeerManagerRegistration
	{
		private final HashWrapper 						hash;
		private final PeerManagerRegistrationAdapter	adapter;

		private final ProbeControl						probe_control;
		
		private PEPeerControl					download;

		private volatile PEPeerControl			active_control;

		private List<Object[]>	pending_connections;

		private BloomFilter		known_seeds;

		private Map<String,TOTorrentFile>			links;

		protected
		PeerManagerRegistrationImpl(
			HashWrapper						_hash,
			PeerManagerRegistrationAdapter	_adapter )
		{
			hash	= _hash;
			adapter	= _adapter;
			
			probe_control		= new ProbeControl();
		}

		protected PeerManagerRegistrationAdapter
		getAdapter()
		{
			return( adapter );
		}

		protected byte[]
		getHash()
		{
			return( hash.getBytes() );
		}
		
		public int
		getHashOverrideLocalPort(
			boolean	only_if_allocated )
		{
			return( adapter.getHashOverrideLocalPort( only_if_allocated ));
		}

	    public List<PeerManagerRegistration>
	    getOtherRegistrationsForHash()
	    {
	    	List<PeerManagerRegistration> result = new ArrayList<>();
	    	
	    	HashWrapper hw;
	    	
	    	byte[] ho_b = adapter.getHashOverride();
	    	
	    	if ( ho_b == null ){
	    		
	    		hw = hash;
	    		
	    	}else{
	    		
	    		hw = new HashWrapper( ho_b );
	    	}
	    		
	    	synchronized( registered_legacy_managers ){
    							
    			CopyOnWriteList<PeerManagerRegistrationImpl>	ov_registrations = registered_legacy_managers.get( hw );

				if ( ov_registrations != null ){

					for ( PeerManagerRegistrationImpl r: ov_registrations ){
						
						if ( r != this ){
							
							result.add( r );
						}
					}
				}
	    	}
	    	
	    	return( result );
	    }
	    
		@Override
		public TOTorrentFile
		getLink(
			String		target )
		{
			synchronized( this ){

				if ( links == null ){

					return( null );
				}

				return(links.get(target));
			}
		}

		@Override
		public void
		addLink(
			String			link,
			TOTorrentFile	target )

			throws Exception
		{
			synchronized( registered_legacy_managers ){
				
				if ( registered_links.get( link ) != null ){

					throw( new Exception( "Duplicate link '" + link + "'" ));
				}

				registered_links.put( link, this );

				//System.out.println( "Added link '" + link + "'" );
			}

			synchronized( this ){

				if ( links == null ){

					links = new HashMap<>();
				}

				links.put( link, target );
			}
		}

		@Override
		public void
		removeLink(
			String			link )
		{
			synchronized( registered_legacy_managers ){

				registered_links.remove( link );
			}

			synchronized( this ){

				if ( links != null ){

					links.remove( link );
				}
			}
		}

		public boolean
		isActive()
		{
			return( active_control != null );
		}

		@Override
		public void
		activate(
			PEPeerControl	_active_control )
		{
			List<Object[]>	connections = null;

			synchronized( registered_legacy_managers ){

				active_control = _active_control;

				if ( download != null ){

					Debug.out( "Already activated" );
				}

				download = _active_control;

				connections = pending_connections;

				pending_connections = null;
			}

			if ( connections != null ){

				for (int i=0;i<connections.size();i++){

					Object[]	entry = connections.get(i);

					NetworkConnection	nc = (NetworkConnection)entry[0];

					PeerManagerRoutingListener	listener = (PeerManagerRoutingListener)entry[2];

					route( _active_control, nc, true, listener );
				}
			}
		}

		@Override
		public void
		deactivate()
		{
			synchronized( registered_legacy_managers ){

				if ( download == null ){

					Debug.out( "Already deactivated" );

				}else{

					download	= null;
				}

				active_control = null;

				if ( pending_connections != null ){

					for (int i=0;i<pending_connections.size();i++){

						Object[]	entry = pending_connections.get(i);

						NetworkConnection	connection = (NetworkConnection)entry[0];

						if (Logger.isEnabled())
							Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
									"Incoming connection from [" + connection
									                           + "] closed due to deactivation" ));

						connection.close( "deactivated" );
					}

					pending_connections = null;
				}
			}
		}

		@Override
		public void
		unregister()
		{
			synchronized( registered_legacy_managers ){

				if ( active_control != null ){

					Debug.out( "Not deactivated" );

					deactivate();
				}

				CopyOnWriteList<PeerManagerRegistrationImpl>	registrations = registered_legacy_managers.get( hash );

				if ( registrations == null ){

					Debug.out( "manager already deregistered" );

				}else{

					if ( registrations.remove( this )){

						if ( registrations.size() == 0 ){

							IncomingConnectionManager.getSingleton().removeSharedSecrets( adapter.getSecrets());

							registered_legacy_managers.remove( hash );
						}
					}else{

						Debug.out( "manager already deregistered" );
					}
				}

				byte[] override = adapter.getHashOverride();
				
				if ( override != null ){
					
					HashWrapper ov_hw = new HashWrapper( override );
					
					CopyOnWriteList<PeerManagerRegistrationImpl>	ov_registrations = registered_legacy_managers.get( ov_hw );

					if ( ov_registrations == null ){

						Debug.out( "manager already deregistered" );

					}else{

						if ( ov_registrations.remove( this )){

						}else{

							Debug.out( "manager already deregistered" );
						}
					}
				}
				
				synchronized( this ){

					if ( links != null ){

						Iterator<String>	it = links.keySet().iterator();

						while( it.hasNext()){

							registered_links.remove( it.next());
						}
					}
				}
			}
		}

		protected boolean
		isKnownSeed(
			InetSocketAddress		address )
		{
			synchronized( registered_legacy_managers ){

				if ( known_seeds == null ){

					return( false );
				}

				return( known_seeds.contains( AddressUtils.getAddressBytes( address )));
			}
		}

		protected void
		setKnownSeed(
				InetSocketAddress		address )
		{
			synchronized( registered_legacy_managers ){

				if ( known_seeds == null ){

					known_seeds = BloomFilterFactory.createAddOnly( 1024 );
				}

				// can't include port as it will be a randomly allocated one in general. two people behind the
				// same NAT will have to connect to each other using LAN peer finder

				known_seeds.add( AddressUtils.getAddressBytes( address ));
			}
		}

		protected PEPeerControl
		getActiveControl()
		{
			return( active_control );
		}

		@Override
		public void
		route(
			NetworkConnection 			connection,
			PeerManagerRoutingListener	listener )
		{
			if ( adapter.manualRoute( connection )){

				return;
			}

			if ( !adapter.isPeerSourceEnabled( PEPeerSource.PS_INCOMING )){

				if (Logger.isEnabled())
					Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
							"Incoming connection from [" + connection
							+ "] to " + adapter.getDescription() + " dropped as peer source disabled" ));

				connection.close( "peer source disabled" );

				return;
			}

			PEPeerControl	control;

			boolean	register_for_timeouts = false;

			synchronized( registered_legacy_managers ){

				control = active_control;

				if ( control == null ){

					// not yet activated, queue connection for use on activation

					if ( pending_connections != null && pending_connections.size() > 10 ){

						if (Logger.isEnabled())
							Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
									"Incoming connection from [" + connection
									+ "] to " + adapter.getDescription() + " dropped too many pending activations" ));

						connection.close( "too many pending activations" );

						return;

					}else{

						if ( pending_connections == null ){

							pending_connections = new ArrayList<>();
						}

						pending_connections.add( new Object[]{ connection, new Long( SystemTime.getMonotonousTime()), listener });

						if ( pending_connections.size() == 1 ){

							register_for_timeouts	= true;
						}
					}
				}
			}

			// do this outside the monitor as the timeout code calls us back holding the timeout monitor
			// and we need to grab managers_mon inside this to run timeouts

			if ( register_for_timeouts ){

				registerForTimeouts( this );
			}

			if ( control != null ){

				route( control, connection, false, listener );
			}
		}

		protected boolean
		timeoutCheck()
		{
			synchronized( registered_legacy_managers ){

				if ( pending_connections == null ){

					return( false );
				}

				Iterator<Object[]> it = pending_connections.iterator();

				long	now = SystemTime.getMonotonousTime();

				while( it.hasNext()){

					Object[]	entry = it.next();

					long	start_time = ((Long)entry[1]).longValue();

					if ( now - start_time > PENDING_TIMEOUT ){

						it.remove();

						NetworkConnection	connection = (NetworkConnection)entry[0];

						if (Logger.isEnabled())
							Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
									"Incoming connection from [" + connection
									+ "] to " + adapter.getDescription() + " closed due to activation timeout" ));

						connection.close( "activation timeout" );
						
					}else{
						
						NetworkConnection	connection = (NetworkConnection)entry[0];

						Object obj = connection.getTransport().getUserData( KEY_TRANSPORT_PROBE );
						
						if ( obj != null ){
						
							connection.getTransport().setUserData( KEY_TRANSPORT_PROBE, null );
							
							PEPeerTransport	pt = PEPeerTransportFactory.createTransport( probe_control, PEPeerSource.PS_INCOMING, connection, null );

							pt.start();
						}
					}
				}

				if ( pending_connections.size() == 0 ){

					pending_connections = null;
				}

				return( pending_connections != null );
			}
		}

		protected void
		route(
				PEPeerControl				control,
				final NetworkConnection 	connection,
				boolean						is_activation,
				PeerManagerRoutingListener	listener )
		{
			// make sure not already connected to the same IP address; allow
			// loopback connects for co-located proxy-based connections and
			// testing

			Object callback = connection.getUserData( "RoutedCallback" );

			if ( callback instanceof AEGenericCallback ){

				try{
					((AEGenericCallback)callback).invoke( control );

				}catch( Throwable e ){

					Debug.out( e );
				}
			}

			ConnectionEndpoint ep = connection.getEndpoint();


			InetSocketAddress is_address = ep.getNotionalAddress();

			String host_address = AddressUtils.getHostAddress( is_address );

			String net_cat = AENetworkClassifier.categoriseAddress( host_address );

			if ( !control.isNetworkEnabled( net_cat )){

				connection.close( "Network '" + net_cat + "' is not enabled" );

				return;
			}
			
			if ( net_cat == AENetworkClassifier.AT_PUBLIC ){
				
				if ( socks_data ){
					
					boolean socks_active = NetworkAdmin.getSingleton().isSocksActive();
					
					boolean lan_local = connection.isLANLocal();

					if ( socks_active && !lan_local ){
					
						connection.close( "Incoming connections not supported when SOCKS data proxy enabled" );

						return;
					}
				}
			}

			InetAddress address_mbn = is_address.getAddress();

			boolean same_allowed = COConfigurationManager.getBooleanParameter( "Allow Same IP Peers" ) || ( address_mbn != null && address_mbn.isLoopbackAddress());

			if ( !same_allowed && PeerIdentityManager.containsIPAddress( control.getPeerIdentityDataID(), host_address )){

				if (Logger.isEnabled()){

					Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
							"Incoming connection from [" + connection
							+ "] dropped as IP address already "
							+ "connected for ["
							+ control.getDisplayName() + "]"));
				}

				connection.close( "already connected to peer");

				return;
			}

			if ( AERunStateHandler.isUDPNetworkOnly()){

				if ( connection.getTransport().getTransportEndpoint().getProtocolEndpoint().getType() == ProtocolEndpoint.PROTOCOL_TCP ){

					if ( !connection.isLANLocal()){

						connection.close( "limited network mode: tcp disabled");

						return;
					}
				}
			}

			if ( !enable_public_tcp_peers ){
				
				if ( connection.getTransport().getTransportEndpoint().getProtocolEndpoint().getType() == ProtocolEndpoint.PROTOCOL_TCP ){
					
					boolean socks_active = NetworkAdmin.getSingleton().isSocksActive();
					
					boolean lan_local = connection.isLANLocal();
					
					if ( net_cat == AENetworkClassifier.AT_PUBLIC && !socks_active && !lan_local ){
						
						connection.close( "TCP public peer protocol disabled");

						return;
					}
				}
			}
			
			if ( !enable_public_udp_peers ){
				
				if ( connection.getTransport().getTransportEndpoint().getProtocolEndpoint().getType() == ProtocolEndpoint.PROTOCOL_UDP ){

					connection.close( "UDP public peer protocol disabled");

					return;
				}
			}
			
			if (Logger.isEnabled()){

				Logger.log(new LogEvent(LOGID, "Incoming connection from ["
						+ connection + "] routed to legacy download ["
						+ control.getDisplayName() + "]"));
			}

			PEPeerTransport	pt = PEPeerTransportFactory.createTransport( control, PEPeerSource.PS_INCOMING, connection, null );

			if ( listener != null ){

				boolean	ok = false;

				try{
					if ( listener.routed( pt )){

						ok	= true;
					}

				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}

				if ( !ok ){

					connection.close( "routing denied" );

					return;
				}
			}

			pt.start();

			if ( is_activation ){

				pt.addListener(
						new PEPeerListener()
						{
							@Override
							public void
							stateChanged(
									PEPeer 		peer,
									int 		new_state )
							{
								if ( new_state == PEPeer.CLOSING ){

									if ( peer.isSeed()){

										InetSocketAddress	address = connection.getEndpoint().getNotionalAddress();

										setKnownSeed( address );

										// this is mainly to deal with seeds that incorrectly connect to us

										adapter.deactivateRequest( address );
									}
								}
							}

							@Override
							public void sentBadChunk(PEPeer peer, int piece_num, int total_bad_chunks ){}

							@Override
							public void addAvailability(final PEPeer peer, final BitFlags peerHavePieces){}

							@Override
							public void removeAvailability(final PEPeer peer, final BitFlags peerHavePieces){}
						});
			}

			control.addPeerTransport( pt );
		}
	
		private class
		ProbeControl
			implements PEPeerControl
		{
			// the purpose of this controller is to provide just enough functionality to allow
			// an incoming NAT check probe connection from a tracker to work as expected
			
			private final LimitedRateGroup upload_limited_rate_group = new LimitedRateGroup(){
				@Override
				public String getName(){
					return("per_dl_up: " + getDisplayName());
				}

				@Override
				public int getRateLimitBytesPerSecond(){
					return(-1);
				}

				@Override
				public boolean isDisabled(){
					return( false );
				}

				@Override
				public void updateBytesUsed(int used){
				}
			};

			private final LimitedRateGroup download_limited_rate_group = new LimitedRateGroup(){
				@Override
				public String getName(){
					return("per_dl_down: " + getDisplayName());
				}

				@Override
				public int getRateLimitBytesPerSecond(){
					return(0);
				}

				@Override
				public boolean isDisabled(){
					return( false );
				}

				@Override
				public void updateBytesUsed(int used){
				}
			};
			
			public int
			getUID()
			{
				log(); return(0);
			}
			
			public DiskManager 
			getDiskManager()
			{ 
				return( null ); 
			}
			
			public PiecePicker 
			getPiecePicker()
			{
				return( null ); 
			}

			public PEPeerManagerAdapter	getAdapter(){ log(); return( null ); }

			@Override
			public void 
			informFullyConnected(PEPeer peer)
			{
			}
			
			@Override
			public String 
			getConnectHealth( boolean verbose )
			{
				log();return( "" );
			}
			
			public void
			start()
			{log(); 
			}

			public void
			stopAll()
			{log(); 
			}

			public byte[]
			getHash()
			{
				return( hash.getBytes());
			}

			public String
			getDisplayName()
			{ 
				log(); return( "probe" ); 
			}

			public PeerIdentityDataID
			getPeerIdentityDataID()
			{ 
				log(); return( null ); 
			}

			public byte[]
			getPeerId()
			{
				return( adapter.getPeerID());
			}

			public int[] 
			getAvailability()
			{ 
				log(); return( null );
			}

			public int 
			getAvailability(
				int pieceNumber)
			{ 
				log(); return( 0 );
			}

			public float 
			getAvgAvail()
			{
				log(); return( 0 );
			}

			public float 
			getMinAvailability()
			{
				log(); return( 0 );
			}

			public float 
			getMinAvailability( 
				int file_index )
			{
				log(); return( 0 );
			}

			public long 
			getAvailWentBadTime()
			{
				log(); return( 0 );
			}

			public long 
			getBytesUnavailable()
			{
				log(); return( 0 );
			}

			public boolean hasDownloadablePiece(){ log(); return( false ); }

			public int	getBytesQueuedForUpload(){ log(); return( 0 ); }
			public int	getNbPeersWithUploadQueued(){ log(); return( 0 ); }
			public int	getNbPeersWithUploadBlocked(){ log(); return( 0 ); }
			public int	getNbPeersUnchoked(){ log(); return( 0 ); }
			public int	getNbPeersUnchoking(){ log(); return( 0 ); }
			
			public int getNbPieces(){ return( adapter.getNbPieces());}
			
			public PEPiece[]	getPieces(){ log(); return( null ); }

			public PEPiece		getPiece(int pieceNumber){ log(); return( null ); }

			public PEPeerManagerStats getStats(){ log(); return( null ); }

			public void
			processTrackerResponse(
					TRTrackerAnnouncerResponse	response ){log(); }

			public int getNbPeers(){ log(); return( 0 ); }

			public int getNbSeeds(){ log(); return( 0 ); }

			public int getPieceLength(int pieceNumber){ log(); return( 0 ); }

			public long getRemaining(){ log(); return( 0 ); }

			public long getHiddenBytes(){ log(); return( 0 ); }

			public long getETA( boolean smoothed ){ log(); return( 0 ); }

			public String getElapsedTime(){ log(); return( "" ); }

			public long getTimeStarted( boolean mono_time ){ log(); return( 0 ); }

			public long getTimeStartedSeeding( boolean mono_time ){ log(); return( 0 ); }

			public void
			addListener(
					PEPeerManagerListener	l ){log(); }

			public void
			removeListener(
					PEPeerManagerListener	l ){log(); }

			public void addPiece(PEPiece piece, int pieceNumber, PEPeer for_peer ){log(); }

			public boolean needsMD5CheckOnCompletion(int pieceNumber){ log(); return( false ); }

			public boolean
			isSeeding(){ log(); return( false ); }

			public boolean
			isMetadataDownload(){ return( false ); }

			public int
			getTorrentInfoDictSize(){ log(); return( 0 ); }

			public void
			setTorrentInfoDictSize(
					int	size ){log(); }

			public boolean
			isSuperSeedMode(){ log(); return( false ); }

			public boolean
			canToggleSuperSeedMode(){ log(); return( false ); }

			public void
			setSuperSeedMode( boolean on ){log(); }

			public boolean
			seedPieceRecheck(){ log(); return( false ); }

			public int getNbRemoteTCPConnections(){ log(); return( 0 ); }
			public int getNbRemoteUDPConnections(){ log(); return( 0 ); }
			public int getNbRemoteUTPConnections(){ log(); return( 0 ); }

			public long getLastRemoteConnectionTime(){ log(); return( 0 ); }
			
			public int
			getMaxNewConnectionsAllowed( String network ){ log(); return( 0 ); }

			public boolean
			hasPotentialConnections(){ log(); return( false ); }

			public void	dataBytesReceived( PEPeer peer, int	l ){log(); }

			public void	dataBytesSent( PEPeer peer, int	l ){log(); }

			public void 
			protocolBytesSent( 
				PEPeer peer, int length )
			{
				
			}

			public void protocolBytesReceived( PEPeer peer, int length ){}

			public void
			discarded(
					PEPeer peer,
					int		l ){log(); }

			public PEPeerStats 
			createPeerStats(
				PEPeer owner)
			{
				return(new PEPeerStatsImpl(owner));
			}

			public PEPeer
			getMyPeer(){ log(); return( null ); }

			public List<PEPeer>
			getPeers(){ log(); return( null ); }

			public List<PEPeer>
			getPeers(
					String	address ){ log(); return( null ); }

			public int
			getPendingPeerCount(){ log(); return( 0 ); }

			public PeerDescriptor[]
			getPendingPeers(){ log(); return( null ); }

			public PeerDescriptor[]
			getPendingPeers(
					String	address ){log();  return( null ); }

			public void
			addPeer(
					PEPeer	peer ){log(); }

			public void
			addPeer(
					String 		ip_address,
					int 		tcp_port,
					int			udp_port,
					boolean 	use_crypto,
					Map			user_data ){log(); }

			public void
			peerDiscovered(
					String		peer_source,
					String 		ip_address,
					int			tcp_port,
					int			udp_port,
					boolean 	use_crypto ){log(); }

			public void
			removePeer(
					PEPeer	peer ){log(); }

			public void
			removePeer(
					PEPeer	peer,
					String	reason ){log(); }

			public void
			peerAdded(PEPeer pc){log(); }

			public void
			peerRemoved(PEPeer pc){log(); }

			public DiskManagerReadRequest
			createDiskManagerRequest(
					int pieceNumber,
					int offset,
					int length ){ log(); return( null ); }

			public void
			requestCanceled(
					DiskManagerReadRequest	item ){log(); }
			
			public void
			requestAdded(
					PEPiece piece, PEPeerTransport peer, DiskManagerReadRequest request ){log(); }

			public boolean
			requestExists(
					String			peer_ip,
					int				piece_number,
					int				offset,
					int				length ){ log(); return( false ); }

			public boolean
			validatePieceReply(
					PEPeerTransport		originator,
					int 				pieceNumber,
					int 				offset,
					DirectByteBuffer 	data ){ log(); return( true ); }

			public void
			writeBlock(
					int 				pieceNumber,
					int 				offset,
					DirectByteBuffer 	data,
					Object 				sender,
					boolean     		cancel){log(); }

			public boolean isWritten( int piece_number, int offset ){ log(); return( false ); }

			public boolean isInEndGameMode(){ log(); return( false ); }

			public void 
			peerConnectionClosed( 
					PEPeerTransport peer, 
					boolean connect_failed, 
					boolean network_failed )
			{
			}

			public PeerExchangerItem createPeerExchangeConnection( PEPeerTransport base_peer ){ log(); return( null ); }

			public void peerVerifiedAsSelf( PEPeerTransport self ){log(); }

			public LimitedRateGroup getUploadLimitedRateGroup(){ return( upload_limited_rate_group ); }

			public LimitedRateGroup getDownloadLimitedRateGroup(){ return( download_limited_rate_group ); }

			public int getEffectiveUploadRateLimitBytesPerSecond(){ log(); return( 0 ); }

			public int getUploadRateLimitBytesPerSecond(){ log(); return( 0 ); }

			public int getDownloadRateLimitBytesPerSecond(){ log(); return( 0 ); }

			public Object getData (String key){ log(); return( null ); }
			public void setData (String key, Object value){log(); }

			public int getAverageCompletionInThousandNotation(){ log(); return( 0 ); }

			public int getMaxCompletionInThousandNotation( boolean never_include_seeds ){ log(); return( 0 ); }

			public PEPeerTransport getTransportFromIdentity( byte[] peer_id ){ log(); return( null ); }

			public PEPeerTransport getTransportFromAddress(String peer){ log(); return( null ); }

			public boolean
			getPreferUDP(){ log(); return( false ); }

			public void
			setPreferUDP(
					boolean	prefer ){log(); }

			public void
			addRateLimiter(
					LimitedRateGroup	group,
					boolean				upload ){log(); }

			public void
			removeRateLimiter(
					LimitedRateGroup	group,
					boolean				upload ){log(); }

			public TrackerPeerSource
			getTrackerPeerSource(){ log(); return( null ); }

			public boolean
			isPeerSourceEnabled(
					String	peer_source ){ log(); return( true ); }

			public boolean
			isNetworkEnabled(
					String	network ){ log(); return( true ); }

			public int
			getPartitionID(){ log(); return( 0 ); }

			public void
			setMaskDownloadCompletion(
				Boolean	mask ){ log(); }
			
			public void
			removeAllPeers(
					String		reason ){log(); }

			public boolean
			isDestroyed(){ log(); return( false ); }

			public void
			generateEvidence(
					IndentWriter		writer ){log(); }

			public void
			setStatsReceiver(
					StatsReceiver	receiver ){log(); }


			public boolean
			validateReadRequest(
					PEPeerTransport	originator,
					int 			pieceNumber,
					int 			offset,
					int 			length ){ log(); return( false ); }

			public boolean
			validateHintRequest(
					PEPeerTransport	originator,
					int 			pieceNumber,
					int 			offset,
					int 			length ){ log(); return( false ); }

			public void
			havePiece(
					int pieceNumber,
					int pieceLength,
					PEPeer pcOrigin ){log(); }

			public void
			updateSuperSeedPiece(
					PEPeer peer,
					int pieceNumber){log(); }

			public int
			getTCPListeningPortNumber(){ log(); return( 0 ); }

			public byte[]
			getTargetHash(){ return( getHash()); }

			public boolean
			isPrivateTorrent(){ log(); return( false ); }

			public int
			getExtendedMessagingMode(){return( adapter.getExtendedMessagingMode()); }

			public boolean
			isPeerExchangeEnabled(){ log(); return( false ); }

			public byte[][]
			getSecrets(
				int	crypto_level ){ log(); return( null ); }

			public int
			getUploadPriority(){ log(); return( 0 ); }

			public int
			getHiddenPiece(){ log(); return( 0 ); }

			public void addPeerTransport( PEPeerTransport transport ){log(); }

			public int
			getConnectTimeout(
				int		ct_def ){ log(); return( 0 ); }

			public int[]
			getMaxConnections(){ log(); return( null ); }

			public boolean
			doOptimisticDisconnect(
					boolean pending_lan_local_peer,
					boolean	force,
					String	network ){ log(); return( false ); }

			public int getNbActivePieces(){ log(); return( 0 ); }

			public int getNbPeersStalledPendingLoad(){ log(); return( 0 ); }

			public void incNbPeersSnubbed(){log(); }
			public void decNbPeersSnubbed(){log(); }
			public void setNbPeersSnubbed(int n){log(); }
			public int getNbPeersSnubbed(){ log(); return( 0 ); }

			public void 
			checkSnubbing(
					PEPeerTransport	peer ){log(); }

			public void
			badPieceReported(
					PEPeerTransport		originator,
					int					piece_number ){log(); }

			public boolean
			isFastExtensionPermitted(
					PEPeerTransport		originator ){ log(); return( false ); }

			public void
			reportBadFastExtensionUse(
					PEPeerTransport		originator ){log(); }

			public void
			statsRequest(
					PEPeerTransport		originator,
					Map					request ){log(); }

			public void
			statsReply(
					PEPeerTransport		originator,
					Map					reply ){log(); }

			public boolean isRTA(){ log(); return( false ); }

			public void
			peerDiscovered(
					PEPeerTransport		finder,
					PeerItem			pi ){log(); }

			public PEPeerControlHashHandler
			getHashHandler(){ log(); return( null ); }
			
			@Override
			public boolean isHolePunchOperationOK(PEPeerTransport peer, boolean is_connect){
				log(); return false;
			}
			private void 
			log()
			{
				Debug.out( "eh" );
			}
		}
	}
}
