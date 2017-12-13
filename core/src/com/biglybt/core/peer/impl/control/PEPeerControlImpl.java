/*
 * Created by Olivier Chalouhi
 * Modified Apr 13, 2004 by Alon Rohter
 * Heavily modified Sep 2005 by Joseph Bridgewater
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
 */

package com.biglybt.core.peer.impl.control;


import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.*;


import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.disk.*;
import com.biglybt.core.disk.DiskManager.GettingThere;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.ipfilter.*;
import com.biglybt.core.logging.*;
import com.biglybt.core.networkmanager.LimitedRateGroup;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.networkmanager.impl.tcp.TCPConnectionManager;
import com.biglybt.core.networkmanager.impl.tcp.TCPNetworkManager;
import com.biglybt.core.networkmanager.impl.udp.UDPNetworkManager;
import com.biglybt.core.peer.*;
import com.biglybt.core.peer.impl.*;
import com.biglybt.core.peer.util.PeerIdentityDataID;
import com.biglybt.core.peer.util.PeerIdentityManager;
import com.biglybt.core.peer.util.PeerUtils;
import com.biglybt.core.peermanager.PeerManagerRegistration;
import com.biglybt.core.peermanager.control.PeerControlInstance;
import com.biglybt.core.peermanager.control.PeerControlScheduler;
import com.biglybt.core.peermanager.control.PeerControlSchedulerFactory;
import com.biglybt.core.peermanager.nat.PeerNATInitiator;
import com.biglybt.core.peermanager.nat.PeerNATTraversalAdapter;
import com.biglybt.core.peermanager.nat.PeerNATTraverser;
import com.biglybt.core.peermanager.peerdb.*;
import com.biglybt.core.peermanager.piecepicker.PiecePicker;
import com.biglybt.core.peermanager.piecepicker.PiecePickerFactory;
import com.biglybt.core.peermanager.unchoker.Unchoker;
import com.biglybt.core.peermanager.unchoker.UnchokerFactory;
import com.biglybt.core.peermanager.unchoker.UnchokerUtil;
import com.biglybt.core.peermanager.uploadslots.UploadHelper;
import com.biglybt.core.peermanager.uploadslots.UploadSlotManager;
import com.biglybt.core.torrent.TOTorrentException;
import com.biglybt.core.tracker.TrackerPeerSource;
import com.biglybt.core.tracker.TrackerPeerSourceAdapter;
import com.biglybt.core.tracker.client.TRTrackerAnnouncer;
import com.biglybt.core.tracker.client.TRTrackerAnnouncerResponse;
import com.biglybt.core.tracker.client.TRTrackerAnnouncerResponsePeer;
import com.biglybt.core.tracker.client.TRTrackerScraperResponse;
import com.biglybt.core.util.*;
import com.biglybt.core.util.bloom.BloomFilter;
import com.biglybt.core.util.bloom.BloomFilterFactory;
import com.biglybt.pif.download.DownloadAnnounceResultPeer;
import com.biglybt.pif.network.Connection;
import com.biglybt.pif.network.OutgoingMessageQueue;
import com.biglybt.pif.peers.Peer;
import com.biglybt.pif.peers.PeerDescriptor;

/**
 * manages all peer transports for a torrent
 *
 * @author MjrTom
 *			2005/Oct/08: Numerous changes for new piece-picking. Also
 *						a few optimizations and multi-thread cleanups
 *			2006/Jan/02: refactoring piece picking related code
 */

@SuppressWarnings("serial")
public class
PEPeerControlImpl
extends LogRelation
implements 	PEPeerControl, DiskManagerWriteRequestListener, PeerControlInstance, PeerNATInitiator,
DiskManagerCheckRequestListener, IPFilterListener
{
	private static final LogIDs LOGID = LogIDs.PEER;

	private static final boolean TEST_PERIODIC_SEEDING_SCAN_FAIL_HANDLING	= false;

	static{
		if ( TEST_PERIODIC_SEEDING_SCAN_FAIL_HANDLING ){
			Debug.out( "**** test periodic scan failure enabled ****" );
		}
	}

	private static final int WARNINGS_LIMIT = 2;

	private static final int	CHECK_REASON_DOWNLOADED		= 1;
	private static final int	CHECK_REASON_COMPLETE		= 2;
	private static final int	CHECK_REASON_SCAN			= 3;
	private static final int	CHECK_REASON_SEEDING_CHECK		= 4;
	private static final int	CHECK_REASON_BAD_PIECE_CHECK	= 5;

	private static final int	SEED_CHECK_WAIT_MARKER	= 65526;

		// config

	private static boolean 	disconnect_seeds_when_seeding;
	private static boolean 	enable_seeding_piece_rechecks;
	private static int		stalled_piece_timeout;
	private static boolean 	fast_unchoke_new_peers;
    private static float	ban_peer_discard_ratio;
    private static int		ban_peer_discard_min_kb;
    private static boolean	udp_fallback_for_failed_connection;
    private static boolean	udp_fallback_for_dropped_connection;
    private static boolean	udp_probe_enabled;
    private static boolean	hide_a_piece;
    private static boolean	prefer_udp_default;

	static{

		COConfigurationManager.addAndFireParameterListeners(
			new String[]{
				"Disconnect Seed",
				"Seeding Piece Check Recheck Enable",
				"peercontrol.stalled.piece.write.timeout",
				"Peer.Fast.Initial.Unchoke.Enabled",
   				"Ip Filter Ban Discard Ratio",
   				"Ip Filter Ban Discard Min KB",
   				"peercontrol.udp.fallback.connect.fail",
   				"peercontrol.udp.fallback.connect.drop",
   				"peercontrol.udp.probe.enable",
  				"peercontrol.hide.piece",
  				"peercontrol.hide.piece.ds",
   				"peercontrol.prefer.udp",
			},
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					String name )
				{
					disconnect_seeds_when_seeding 		= COConfigurationManager.getBooleanParameter("Disconnect Seed");
					enable_seeding_piece_rechecks 		= COConfigurationManager.getBooleanParameter("Seeding Piece Check Recheck Enable");
					stalled_piece_timeout				= COConfigurationManager.getIntParameter( "peercontrol.stalled.piece.write.timeout", 60*1000 );
					fast_unchoke_new_peers 				= COConfigurationManager.getBooleanParameter( "Peer.Fast.Initial.Unchoke.Enabled" );
					ban_peer_discard_ratio				= COConfigurationManager.getFloatParameter( "Ip Filter Ban Discard Ratio" );
					ban_peer_discard_min_kb				= COConfigurationManager.getIntParameter( "Ip Filter Ban Discard Min KB" );
					udp_fallback_for_failed_connection	= COConfigurationManager.getBooleanParameter( "peercontrol.udp.fallback.connect.fail" );
					udp_fallback_for_dropped_connection	= COConfigurationManager.getBooleanParameter( "peercontrol.udp.fallback.connect.drop" );
					udp_probe_enabled					= COConfigurationManager.getBooleanParameter( "peercontrol.udp.probe.enable" );
					hide_a_piece						= COConfigurationManager.getBooleanParameter( "peercontrol.hide.piece" );
					boolean hide_a_piece_ds				= COConfigurationManager.getBooleanParameter( "peercontrol.hide.piece.ds" );

					if ( hide_a_piece && !hide_a_piece_ds ){

						disconnect_seeds_when_seeding = false;
					}

					prefer_udp_default						= COConfigurationManager.getBooleanParameter( "peercontrol.prefer.udp" );
				}
			});
	}

	private static final IpFilter ip_filter = IpFilterManagerFactory.getSingleton().getIPFilter();

	private volatile boolean	is_running 		= false;
	private volatile boolean	is_destroyed 	= false;

	private volatile ArrayList<PEPeerTransport>	peer_transports_cow = new ArrayList<>();	// Copy on write!
	private final AEMonitor     peer_transports_mon	= new AEMonitor( "PEPeerControl:PT");

	protected final PEPeerManagerAdapter	adapter;
	private final DiskManager           disk_mgr;
	private final DiskManagerPiece[]    dm_pieces;

	private final boolean	is_private_torrent;

	private PEPeerManager.StatsReceiver	stats_receiver;

	private final PiecePicker	piecePicker;
	private long				lastNeededUndonePieceChange;

	/** literally seeding as in 100% torrent complete */
	private boolean 			seeding_mode;
	private boolean				restart_initiated;

	private final int       _nbPieces;     //how many pieces in the torrent
	private final PEPieceImpl[]	pePieces;      //pieces that are currently in progress
	private int				nbPiecesActive;	// how many pieces are currently in progress

	private int				nbPeersSnubbed;

	private PeerIdentityDataID			_hash;
	private final byte[]        		_myPeerId;
	private PEPeerManagerStatsImpl      _stats;

	//private final TRTrackerAnnouncer _tracker;
	//  private int _maxUploads;
	private int		stats_tick_count;
	private int		_seeds, _peers,_remotesTCPNoLan, _remotesUDPNoLan, _remotesUTPNoLan;
	private int 	_tcpPendingConnections, _tcpConnectingConnections;
	private long last_remote_time;
	private long	_timeStarted;
	private long	_timeStarted_mono;
	private long	_timeStartedSeeding 		= -1;
	private long	_timeStartedSeeding_mono 	= -1;
	private long	_timeFinished;
	private Average	_averageReceptionSpeed;

	private long mainloop_loop_count;

	private static final int MAINLOOP_ONE_SECOND_INTERVAL = 1000 / PeerControlScheduler.SCHEDULE_PERIOD_MILLIS;
	private static final int MAINLOOP_FIVE_SECOND_INTERVAL = MAINLOOP_ONE_SECOND_INTERVAL * 5;
	private static final int MAINLOOP_TEN_SECOND_INTERVAL = MAINLOOP_ONE_SECOND_INTERVAL * 10;
	private static final int MAINLOOP_TWENTY_SECOND_INTERVAL = MAINLOOP_ONE_SECOND_INTERVAL * 20;
	private static final int MAINLOOP_THIRTY_SECOND_INTERVAL = MAINLOOP_ONE_SECOND_INTERVAL * 30;
	private static final int MAINLOOP_SIXTY_SECOND_INTERVAL = MAINLOOP_ONE_SECOND_INTERVAL * 60;
	private static final int MAINLOOP_TEN_MINUTE_INTERVAL = MAINLOOP_SIXTY_SECOND_INTERVAL * 10;


	private volatile ArrayList<PEPeerManagerListener> peer_manager_listeners_cow = new ArrayList<>();  //copy on write


	private final List<Object[]>	piece_check_result_list     	= new ArrayList<>();
	private final AEMonitor	piece_check_result_list_mon  	= new AEMonitor( "PEPeerControl:PCRL");

	private boolean 			superSeedMode;
	private int 				superSeedModeCurrentPiece;
	private int 				superSeedModeNumberOfAnnounces;
	private SuperSeedPiece[]	superSeedPieces;

	private int			hidden_piece;

	private final AEMonitor     this_mon = new AEMonitor( "PEPeerControl");

	private long		ip_filter_last_update_time;

	private Map<Object,Object>			user_data;

	private Unchoker		unchoker;

	private List<Object[]>	external_rate_limiters_cow;

	private int	bytes_queued_for_upload;
	private int	connections_with_queued_data;
	private int	connections_with_queued_data_blocked;
	private int	connections_unchoked;


	private List<PEPeerTransport> sweepList = Collections.emptyList();
	private int nextPEXSweepIndex = 0;


	private final UploadHelper upload_helper = new UploadHelper() {
		@Override
		public int getPriority() {
			return UploadHelper.PRIORITY_NORMAL;  //TODO also must call UploadSlotManager.getSingleton().updateHelper( upload_helper ); on priority change
		}

		@Override
		public ArrayList<PEPeer> getAllPeers() {
			return( (ArrayList)peer_transports_cow );
		}

		@Override
		public boolean isSeeding() {
			return seeding_mode;
		}
	};



	private final PeerDatabase	peer_database = PeerDatabaseFactory.createPeerDatabase();

	private int				bad_piece_reported		= -1;

	private int				next_rescan_piece		= -1;
	private long			rescan_piece_time		= -1;

	private long			last_eta;
	private long			last_eta_smoothed;
	private long			last_eta_calculation;

	private static final int MAX_UDP_CONNECTIONS		= 16;

	private static final int PENDING_NAT_TRAVERSAL_MAX	= 32;
	private static final int MAX_UDP_TRAVERSAL_COUNT	= 3;

	private static final String	PEER_NAT_TRAVERSE_DONE_KEY	= PEPeerControlImpl.class.getName() + "::nat_trav_done";

	private final Map<String,PEPeerTransport>	pending_nat_traversals =
		new LinkedHashMap<String,PEPeerTransport>(PENDING_NAT_TRAVERSAL_MAX,0.75f,true)
	{
		@Override
		protected boolean
		removeEldestEntry(
			Map.Entry<String,PEPeerTransport> eldest)
		{
			return size() > PENDING_NAT_TRAVERSAL_MAX;
		}
	};

	private int udp_traversal_count;

	private static final int UDP_RECONNECT_MAX			= 16;

	private final Map<String,PEPeerTransport>	udp_reconnects =
		new LinkedHashMap<String,PEPeerTransport>(UDP_RECONNECT_MAX,0.75f,true)
	{
		@Override
		protected boolean
		removeEldestEntry(
			Map.Entry<String,PEPeerTransport> eldest)
		{
			return size() > UDP_RECONNECT_MAX;
		}
	};

	private static final int UDP_RECONNECT_MIN_MILLIS	= 10*1000;
	private long	last_udp_reconnect;

	private boolean	prefer_udp;

	private static final int		PREFER_UDP_BLOOM_SIZE	= 10000;
	private volatile BloomFilter	prefer_udp_bloom;

	private volatile boolean	upload_diabled;
	private volatile boolean	download_diabled;
	
	private final LimitedRateGroup upload_limited_rate_group = new LimitedRateGroup() {
		@Override
		public String
		getName()
		{
			return( "per_dl_up: " + getDisplayName());
		}
		@Override
		public int getRateLimitBytesPerSecond() {
			int rate = adapter.getUploadRateLimitBytesPerSecond();
			
			boolean disabled = rate < 0;
			
			if ( disabled != upload_diabled ) {
			
				try{					
					peer_transports_mon.enter();

					if ( disabled != upload_diabled  ){
						
						upload_diabled = disabled;
						
						for ( PEPeerTransport peer: peer_transports_cow ){
							
							peer.setUploadDisabled( upload_limited_rate_group, disabled );
						}
					}
				}finally {
					peer_transports_mon.exit();
				}	
			}
			
			if ( disabled ){
				
				return( 0 );
				
			}else{
				
				return( rate );
			}
		}
		
		@Override
		public boolean
		isDisabled()
		{
			return( adapter.getUploadRateLimitBytesPerSecond() == -1 );
		}
		@Override
		public void
		updateBytesUsed(
				int	used )
		{
		}
	};

	private final LimitedRateGroup download_limited_rate_group = new LimitedRateGroup() {
		@Override
		public String
		getName()
		{
			return( "per_dl_down: " + getDisplayName());
		}
		@Override
		public int getRateLimitBytesPerSecond() {
			int rate = adapter.getDownloadRateLimitBytesPerSecond();
			
			boolean disabled = rate < 0;
			
			if ( disabled != download_diabled ) {
			
				try{					
					peer_transports_mon.enter();

					if ( disabled != download_diabled  ){
						
						download_diabled = disabled;
						
						for ( PEPeerTransport peer: peer_transports_cow ){
							
							peer.setDownloadDisabled( download_limited_rate_group, disabled );
						}
					}
				}finally {
					peer_transports_mon.exit();
				}	
			}
			
			if ( disabled ){
				
				return( 0 );
				
			}else{
				
				return( rate );
			}		
		}
		
		@Override
		public boolean
		isDisabled()
		{
			return( adapter.getDownloadRateLimitBytesPerSecond() == -1 );
		}
		@Override
		public void
		updateBytesUsed(
				int	used )
		{
		}
	};

	private final int	partition_id;

	private final boolean	is_metadata_download;
	private int				metadata_infodict_size;

	private GettingThere	finish_in_progress;

	private long			last_seed_disconnect_time;

	private final BloomFilter		naughty_fast_extension_bloom =
			BloomFilterFactory.createRotating(
				BloomFilterFactory.createAddRemove4Bit( 2000 ), 2 );




	public
	PEPeerControlImpl(
		byte[]					_peer_id,
		PEPeerManagerAdapter 	_adapter,
		DiskManager 			_diskManager,
		int						_partition_id )
	{
		_myPeerId		= _peer_id;
		adapter 		= _adapter;
		disk_mgr 		= _diskManager;
		partition_id	= _partition_id;

		boolean is_private = false;

		try{
			is_private = disk_mgr.getTorrent().getPrivate();

		}catch( Throwable e ){

			Debug.out( e );
		}

		is_private_torrent = is_private;

		is_metadata_download	= adapter.isMetadataDownload();

		if ( !is_metadata_download ){
			metadata_infodict_size	= adapter.getTorrentInfoDictSize();
		}

		_nbPieces =disk_mgr.getNbPieces();
		dm_pieces =disk_mgr.getPieces();

		pePieces = new PEPieceImpl[_nbPieces];

		hidden_piece = hide_a_piece?((int)(Math.abs(adapter.getRandomSeed())%_nbPieces)):-1;

		/*
		if ( hidden_piece >= 0 ){

			System.out.println( "Hidden piece for " + getDisplayName() + " = " + hidden_piece );
		}
		*/

		piecePicker = PiecePickerFactory.create( this );

		ip_filter.addListener( this );
	}


	@Override
	public void
	start()
	{
		//This torrent Hash
		try
		{

			_hash = PeerIdentityManager.createDataID( disk_mgr.getTorrent().getHash());

		} catch (TOTorrentException e)
		{

			// this should never happen
			Debug.printStackTrace( e );

			_hash = PeerIdentityManager.createDataID( new byte[20] );
		}

		// the recovered active pieces
		for (int i =0; i <_nbPieces; i++ )
		{
			final DiskManagerPiece dmPiece =dm_pieces[i];
			if (!dmPiece.isDone() &&dmPiece.getNbWritten() >0)
			{
				addPiece(new PEPieceImpl(this, dmPiece, 0), i, true, null );
			}
		}

		//The peer connections
		peer_transports_cow = new ArrayList();

		//BtManager is threaded, this variable represents the
		// current loop iteration. It's used by some components only called
		// at some specific times.
		mainloop_loop_count = 0;

		//The current tracker state
		//this could be start or update

		_averageReceptionSpeed = Average.getInstance(1000, 30);

		// the stats
		_stats =new PEPeerManagerStatsImpl(this);

		superSeedMode = (COConfigurationManager.getBooleanParameter("Use Super Seeding") && this.getRemaining() == 0);

		superSeedModeCurrentPiece = 0;

		if (superSeedMode)
		{
			initialiseSuperSeedMode();
		}

		// initial check on finished state - future checks are driven by piece check results

		// Moved out of mainLoop() so that it runs immediately, possibly changing
		// the state to seeding.

		checkFinished( true );

		UploadSlotManager.getSingleton().registerHelper( upload_helper );

		lastNeededUndonePieceChange =Long.MIN_VALUE;
		_timeStarted 		= SystemTime.getCurrentTime();
		_timeStarted_mono 	= SystemTime.getMonotonousTime();

		is_running = true;

		// activate after marked as running as we may synchronously add connections here due to pending activations

		PeerManagerRegistration reg = adapter.getPeerManagerRegistration();

		if ( reg != null ){

			reg.activate( this );
		}

		PeerNATTraverser.getSingleton().register( this );

		PeerControlSchedulerFactory.getSingleton(partition_id).register(this);
	}

	@Override
	public void stopAll()
	{
		is_running =false;

		UploadSlotManager.getSingleton().deregisterHelper( upload_helper );

		PeerControlSchedulerFactory.getSingleton(partition_id).unregister(this);

		PeerNATTraverser.getSingleton().unregister( this );

		// remove legacy controller activation

		PeerManagerRegistration reg = adapter.getPeerManagerRegistration();

		if ( reg != null ){

			reg.deactivate();
		}

		closeAndRemoveAllPeers("download stopped", false);

		// clear pieces
		for (int i =0; i <_nbPieces; i++ )
		{
			if (pePieces[i] !=null)
				removePiece(pePieces[i], i);
		}

		// 5. Remove listeners

		ip_filter.removeListener(this);

		piecePicker.destroy();

		final ArrayList<PEPeerManagerListener> peer_manager_listeners = peer_manager_listeners_cow;

		for( int i=0; i < peer_manager_listeners.size(); i++ ) {

			((PEPeerManagerListener)peer_manager_listeners.get(i)).destroyed();
		}

		sweepList = Collections.emptyList();

		pending_nat_traversals.clear();

		udp_reconnects.clear();

		is_destroyed = true;
	}

	@Override
	public int
	getPartitionID()
	{
		return( partition_id );
	}

	@Override
	public boolean
	isDestroyed()
	{
		return( is_destroyed );
	}

	@Override
	public DiskManager getDiskManager() {  return disk_mgr;   }
	@Override
	public PiecePicker getPiecePicker()
	{
		return piecePicker;
	}

	@Override
	public PEPeerManagerAdapter	getAdapter(){ return( adapter ); }

	@Override
	public String getDisplayName(){ return( adapter.getDisplayName()); }

	@Override
	public String
	getName()
	{
		return( getDisplayName());
	}

	@Override
	public void
	schedule()
	{
		if ( finish_in_progress != null ){

			// System.out.println( "Finish in prog" );

			if ( finish_in_progress.hasGotThere()){

				finish_in_progress = null;

				// System.out.println( "Finished" );
			}else{

				return;
			}
		}

		try{
				// first off update the stats so they can be used by subsequent steps

			updateStats();

			updateTrackerAnnounceInterval();

			doConnectionChecks();

			processPieceChecks();

			if ( finish_in_progress != null ){

					// get off the scheduler thread while potentially long running operations complete

				return;
			}

			// note that seeding_mode -> torrent totally downloaded, not just non-dnd files
			// complete, so there is no change of a new piece appearing done by a means such as
			// background periodic file rescans

			if ( !seeding_mode ){

				checkCompletedPieces(); //check to see if we've completed anything else
			}

			checkBadPieces();

			checkInterested();      // see if need to recheck Interested on all peers

			piecePicker.updateAvailability();

			checkCompletionState();	// pick up changes in completion caused by dnd file changes

			if ( finish_in_progress != null ){

					// get off the scheduler thread while potentially long running operations complete

				return;
			}

			checkSeeds();

			if(!seeding_mode) {
				// if we're not finished

				checkRequests();

				piecePicker.allocateRequests();

				checkRescan();
				checkSpeedAndReserved();

				check99PercentBug();
			}


			updatePeersInSuperSeedMode();
			doUnchokes();

		}catch (Throwable e) {

			Debug.printStackTrace( e );
		}
		mainloop_loop_count++;
	}



	/**
	 * A private method that does analysis of the result sent by the tracker.
	 * It will mainly open new connections with peers provided
	 * and set the timeToWait variable according to the tracker response.
	 * @param tracker_response
	 */

	private void
	analyseTrackerResponse(
			TRTrackerAnnouncerResponse	tracker_response )
	{
		// tracker_response.print();
		final TRTrackerAnnouncerResponsePeer[]	peers = tracker_response.getPeers();

		if ( peers != null ){
			addPeersFromTracker( tracker_response.getPeers());
		}

		final Map extensions = tracker_response.getExtensions();

		if (extensions != null ){
			addExtendedPeersFromTracker( extensions );
		}
	}

	@Override
	public void
	processTrackerResponse(
			TRTrackerAnnouncerResponse	response )
	{
		// only process new peers if we're still running
		if ( is_running ){
			analyseTrackerResponse( response );
		}
	}

	private void
	addExtendedPeersFromTracker(
			Map		extensions )
	{
		final Map	protocols = (Map)extensions.get("protocols");

		if ( protocols != null ){

			System.out.println( "PEPeerControl: tracker response contained protocol extensions");

			final Iterator protocol_it = protocols.keySet().iterator();

			while( protocol_it.hasNext()){

				final String	protocol_name = (String)protocol_it.next();

				final Map	protocol = (Map)protocols.get(protocol_name);

				final List	transports = PEPeerTransportFactory.createExtendedTransports( this, protocol_name, protocol );

				for (int i=0;i<transports.size();i++){

					final PEPeer	transport = (PEPeer)transports.get(i);

					addPeer( transport );
				}
			}
		}
	}

	@Override
	public List<PEPeer>
	getPeers()
	{
		return( (List)peer_transports_cow );
	}

	@Override
	public List<PEPeer>
	getPeers(
		String	address )
	{
		List<PEPeer>	result = new ArrayList<>();

		Iterator<PEPeerTransport>	it = peer_transports_cow.iterator();

		if ( address.contains( ":" )){

				// straight forward string matching can fail due to use of :: compression

			try{
				byte[] address_bytes = InetAddress.getByName( address ).getAddress();

				while( it.hasNext()){

					PEPeerTransport	peer = it.next();

					String peer_address = peer.getIp();

					if ( peer_address.contains( ":" )){

						byte[] peer_bytes = (byte[])peer.getUserData( "ipv6.bytes" );

						if ( peer_bytes == null ){

							peer_bytes = InetAddress.getByName( peer_address ).getAddress();

							peer.setUserData( "ipv6.bytes", peer_bytes );
						}

						if ( Arrays.equals( address_bytes, peer_bytes )){

							result.add( peer );
						}
					}
				}

				return( result );

			}catch( Throwable e ){

				// weird, just carry on
			}
		}

		while( it.hasNext()){

			PEPeerTransport	peer = it.next();

			if ( peer.getIp().equals( address )){

				result.add( peer );
			}
		}

		return( result );
	}

	@Override
	public int
	getPendingPeerCount()
	{
		return( peer_database.getDiscoveredPeerCount());
	}

	@Override
	public PeerDescriptor[]
  	getPendingPeers()
  	{
  		return((PeerDescriptor[])peer_database.getDiscoveredPeers());
  	}

	@Override
	public PeerDescriptor[]
	getPendingPeers(
		String	address )
	{
		return((PeerDescriptor[])peer_database.getDiscoveredPeers( address ));
	}

	@Override
	public void
	addPeer(
		PEPeer		_transport )
	{
		if ( !( _transport instanceof PEPeerTransport )){

			throw( new RuntimeException("invalid class"));
		}

		final PEPeerTransport	transport = (PEPeerTransport)_transport;

		if (!ip_filter.isInRange(transport.getIp(), getDisplayName(), getTorrentHash())) {

			final ArrayList<PEPeerTransport> peer_transports = peer_transports_cow;

			if ( !peer_transports.contains(transport)){

				addToPeerTransports( transport );

				transport.start();

			}else{
				Debug.out( "addPeer():: peer_transports.contains(transport): SHOULD NEVER HAPPEN !" );
				transport.closeConnection( "already connected" );
			}
		}else{

			transport.closeConnection( "IP address blocked by filters" );
		}
	}

	protected byte[]
	getTorrentHash()
	{
		try{
			return( disk_mgr.getTorrent().getHash());

		}catch( Throwable e ){

			return( null );
		}
	}
	@Override
	public void
	removePeer(
			PEPeer	_transport )
	{
		removePeer( _transport, "remove peer" );
	}

	@Override
	public void
	removePeer(
			PEPeer	_transport,
			String	reason )
	{
		if ( !( _transport instanceof PEPeerTransport )){

			throw( new RuntimeException("invalid class"));
		}

		PEPeerTransport	transport = (PEPeerTransport)_transport;

		closeAndRemovePeer( transport, reason, true );
	}

	private void closeAndRemovePeer( PEPeerTransport peer, String reason, boolean log_if_not_found )
	{
		boolean removed =false;

		// copy-on-write semantics
		try{
			peer_transports_mon.enter();

			if ( peer_transports_cow.contains( peer )){

				final ArrayList new_peer_transports = new ArrayList( peer_transports_cow );

				new_peer_transports.remove(peer);
				peer_transports_cow =new_peer_transports;
				removed =true;
			}
		}
		finally{
			peer_transports_mon.exit();
		}

		if( removed ) {
			peer.closeConnection( reason );
			peerRemoved(peer);  	//notify listeners
		}
		else {
			if ( log_if_not_found ){
				// we know this happens due to timing issues... Debug.out( "closeAndRemovePeer(): peer not removed" );
			}
		}
	}

	private void closeAndRemoveAllPeers( String reason, boolean reconnect ) {
		List<PEPeerTransport> peer_transports;

		try{
			peer_transports_mon.enter();

			peer_transports = peer_transports_cow;

			peer_transports_cow = new ArrayList<>(0);
		}
		finally{
			peer_transports_mon.exit();
		}

		for( int i=0; i < peer_transports.size(); i++ ) {
			final PEPeerTransport peer = peer_transports.get( i );

			try{

				peer.closeConnection( reason );

			}catch( Throwable e ){

				// if something goes wrong with the close process (there's a bug in there somewhere whereby
				// we occasionally get NPEs then we want to make sure we carry on and close the rest

				Debug.printStackTrace(e);
			}

			try{
				peerRemoved( peer );  //notify listeners

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}

		if( reconnect ) {
			for( int i=0; i < peer_transports.size(); i++ ) {
				final PEPeerTransport peer = peer_transports.get( i );

				PEPeerTransport	reconnected_peer = peer.reconnect(false, false);
			}
		}
	}




	@Override
	public void
	addPeer(
		String 		ip_address,
		int			tcp_port,
		int			udp_port,
		boolean 	use_crypto,
		Map			user_data )
	{
		final byte type = use_crypto ? PeerItemFactory.HANDSHAKE_TYPE_CRYPTO : PeerItemFactory.HANDSHAKE_TYPE_PLAIN;
		final PeerItem peer_item = PeerItemFactory.createPeerItem( ip_address, tcp_port, PeerItem.convertSourceID( PEPeerSource.PS_PLUGIN ), type, udp_port, PeerItemFactory.CRYPTO_LEVEL_1, 0 );

		byte	crypto_level = PeerItemFactory.CRYPTO_LEVEL_1;

		boolean force = false;
		
		if ( user_data != null ){

			Boolean f = (Boolean)user_data.get( Peer.PR_FORCE_CONNECTION );
			
			if ( f != null ){
				
				force = f;
			}
		}
		
		if ( force || !isAlreadyConnected( peer_item ) ) {

			String fail_reason;

			boolean	tcp_ok = TCPNetworkManager.TCP_OUTGOING_ENABLED && tcp_port > 0;
			boolean udp_ok = UDPNetworkManager.UDP_OUTGOING_ENABLED && udp_port > 0;

			if ( 	tcp_ok && !( ( prefer_udp || prefer_udp_default ) && udp_ok )){

				fail_reason = makeNewOutgoingConnection( PEPeerSource.PS_PLUGIN, ip_address, tcp_port, udp_port, true, use_crypto, crypto_level, user_data );  //directly inject the the imported peer

			}else if ( udp_ok ){

				fail_reason = makeNewOutgoingConnection( PEPeerSource.PS_PLUGIN, ip_address, tcp_port, udp_port, false, use_crypto, crypto_level, user_data );  //directly inject the the imported peer

			}else{

				fail_reason = "No usable protocol";
			}

			if( fail_reason != null )  Debug.out( "Injected peer " + ip_address + ":" + tcp_port + " was not added - " + fail_reason );
		}
	}

	@Override
	public void
	peerDiscovered(
		String		peer_source,
		String 		ip_address,
		int			tcp_port,
		int			udp_port,
		boolean 	use_crypto )
	{
		if ( peer_database != null ){

			final ArrayList<PEPeerTransport> peer_transports = peer_transports_cow;

			for( int x=0; x < peer_transports.size(); x++ ){

				PEPeer transport = peer_transports.get( x );

					// allow loopback connects for co-located proxy-based connections and testing

				if ( ip_address.equals( transport.getIp())){

					boolean same_allowed = COConfigurationManager.getBooleanParameter( "Allow Same IP Peers" ) ||

					transport.getIp().equals( "127.0.0.1" );

					if ( !same_allowed || tcp_port == transport.getPort()){

						return;
					}
				}
			}

			byte type = use_crypto ? PeerItemFactory.HANDSHAKE_TYPE_CRYPTO : PeerItemFactory.HANDSHAKE_TYPE_PLAIN;

			PeerItem item = PeerItemFactory.createPeerItem(
					ip_address,
					tcp_port,
					PeerItem.convertSourceID( peer_source ),
					type,
					udp_port,
					PeerItemFactory.CRYPTO_LEVEL_1,
					0 );

			peerDiscovered( null, item );

			peer_database.addDiscoveredPeer( item );
		}
	}

	private void
	addPeersFromTracker(
		TRTrackerAnnouncerResponsePeer[]		peers )
	{

		for (int i = 0; i < peers.length; i++){
			final TRTrackerAnnouncerResponsePeer	peer = peers[i];

			final List<PEPeerTransport> peer_transports = peer_transports_cow;

			boolean already_connected = false;

			for( int x=0; x < peer_transports.size(); x++ ) {
				final PEPeerTransport transport = peer_transports.get( x );

				// allow loopback connects for co-located proxy-based connections and testing

				if( peer.getAddress().equals( transport.getIp() )){

					final boolean same_allowed = COConfigurationManager.getBooleanParameter( "Allow Same IP Peers" ) ||

					transport.getIp().equals( "127.0.0.1" );

					if( !same_allowed || peer.getPort() == transport.getPort() ) {
						already_connected = true;
						break;
					}
				}
			}

			if( already_connected )  continue;

			if( peer_database != null ){

				byte type = peer.getProtocol() == DownloadAnnounceResultPeer.PROTOCOL_CRYPT ? PeerItemFactory.HANDSHAKE_TYPE_CRYPTO : PeerItemFactory.HANDSHAKE_TYPE_PLAIN;

				byte crypto_level = peer.getAZVersion() < TRTrackerAnnouncer.AZ_TRACKER_VERSION_3?PeerItemFactory.CRYPTO_LEVEL_1:PeerItemFactory.CRYPTO_LEVEL_2;

				PeerItem item = PeerItemFactory.createPeerItem(
						peer.getAddress(),
						peer.getPort(),
						PeerItem.convertSourceID( peer.getSource() ),
						type,
						peer.getUDPPort(),
						crypto_level,
						peer.getUploadSpeed());

				peerDiscovered( null, item );

				peer_database.addDiscoveredPeer( item );
			}

			int	http_port = peer.getHTTPPort();

			if ( http_port != 0 && !seeding_mode ){

				adapter.addHTTPSeed( peer.getAddress(), http_port );
			}
		}
	}


	/**
	 * Request a new outgoing peer connection.
	 * @param address ip of remote peer
	 * @param port remote peer listen port
	 * @return null if the connection was added to the transport list, reason if rejected
	 */

	private String
	makeNewOutgoingConnection(
			String	peer_source,
			String 	address,
			int 		tcp_port,
			int			udp_port,
			boolean		use_tcp,
			boolean 	require_crypto,
			byte		crypto_level,
			Map			user_data )
	{
			//make sure this connection isn't filtered

		if ( ip_filter.isInRange( address, getDisplayName(), getTorrentHash())) {

			return( "IPFilter block" );
		}

		String net_cat = AENetworkClassifier.categoriseAddress( address );

		if ( !adapter.isNetworkEnabled( net_cat )){

			return( "Network '" + net_cat + "' is not enabled" );
		}

		if ( !adapter.isPeerSourceEnabled( peer_source )){

			return( "Peer source '" + peer_source + "' is not enabled" );
		}

		boolean	is_priority_connection 	= false;
		boolean	force					= false;
		
		if ( user_data != null ){

			Boolean pc = (Boolean)user_data.get( Peer.PR_PRIORITY_CONNECTION );

			if ( pc != null && pc.booleanValue()){

				is_priority_connection = true;
			}
			
			Boolean f = (Boolean)user_data.get( Peer.PR_FORCE_CONNECTION );
			
			if ( f != null ){
				
				force = f;
			}
		}
	
		//make sure we need a new connection

		boolean max_reached = getMaxNewConnectionsAllowed( net_cat ) == 0;	// -1 -> unlimited

		if ( max_reached ){

			if ( 	peer_source != PEPeerSource.PS_PLUGIN ||
					!doOptimisticDisconnect(
							AddressUtils.isLANLocalAddress( address ) != AddressUtils.LAN_LOCAL_NO,
							is_priority_connection,
							net_cat )){

				return( "Too many connections" );
			}
		}

		//make sure not already connected to the same IP address; allow loopback connects for co-located proxy-based connections and testing

		final boolean same_allowed = force || COConfigurationManager.getBooleanParameter( "Allow Same IP Peers" ) || address.equals( "127.0.0.1" );

		if( !same_allowed && PeerIdentityManager.containsIPAddress( _hash, address ) ){

			return( "Already connected to IP" );
		}

		if( PeerUtils.ignorePeerPort( tcp_port ) ) {
			if (Logger.isEnabled())
				Logger.log(new LogEvent(disk_mgr.getTorrent(), LOGID,
						"Skipping connect with " + address + ":" + tcp_port
						+ " as peer port is in ignore list."));

			return( "TCP port '" + tcp_port + "' is in ignore list" );
		}


			//start the connection

		PEPeerTransport real = PEPeerTransportFactory.createTransport( this, peer_source, address, tcp_port, udp_port, use_tcp, require_crypto, crypto_level, user_data );

		addToPeerTransports( real );

		return null;
	}


	/**
	 * A private method that checks if PEPieces being downloaded are finished
	 * If all blocks from a PEPiece are written to disk, this method will
	 * queue the piece for hash check.
	 * Elsewhere, if it passes sha-1 check, it will be marked as downloaded,
	 * otherwise, it will unmark it as fully downloaded, so blocks can be retreived again.
	 */
	private void checkCompletedPieces() {
		if ((mainloop_loop_count %MAINLOOP_ONE_SECOND_INTERVAL) !=0)
			return;

		//for every piece
		for (int i = 0; i <_nbPieces; i++) {
			final DiskManagerPiece dmPiece =dm_pieces[i];
			//if piece is completly written, not already checking, and not Done
			if (dmPiece.isNeedsCheck())
			{
				//check the piece from the disk
				dmPiece.setChecking();

				DiskManagerCheckRequest req =
					disk_mgr.createCheckRequest(
							i, new Integer(CHECK_REASON_DOWNLOADED));

				req.setAdHoc( false );

				disk_mgr.enqueueCheckRequest(  req, this );
			}
		}
	}

	/** Checks given piece to see if it's active but empty, and if so deactivates it.
	 * @param pieceNumber to check
	 * @return true if the piece was removed and is no longer active (pePiece ==null)
	 */
	private boolean
	checkEmptyPiece(
		final int pieceNumber )
	{
        if ( piecePicker.isInEndGameMode()){

			return false;   // be sure to not remove pieces in EGM
        }

		final PEPiece pePiece =pePieces[pieceNumber];
		final DiskManagerPiece dmPiece =dm_pieces[pieceNumber];

		if ( pePiece == null || pePiece.isRequested())
			return false;

		if (dmPiece.getNbWritten() >0 ||pePiece.getNbUnrequested() < pePiece.getNbBlocks() ||pePiece.getReservedBy() !=null)
			return false;

			// reset in case dmpiece is in some skanky state

		pePiece.reset();

		removePiece(pePiece, pieceNumber);
		return true;
	}

	/**
	 * Check if a piece's Speed is too fast for it to be getting new data
	 * and if a reserved pieced failed to get data within 120 seconds
	 */
	private void checkSpeedAndReserved()
	{
		// only check every 5 seconds
		if(mainloop_loop_count % MAINLOOP_FIVE_SECOND_INTERVAL != 0)
			return;

		final int				nbPieces	=_nbPieces;
		final PEPieceImpl[] pieces =pePieces;
		//for every piece
		for (int i =0; i <nbPieces; i++)
		{
			// placed before null-check in case it really removes a piece
			checkEmptyPiece(i);


			final PEPieceImpl pePiece =pieces[i];
			// these checks are only against pieces being downloaded
			// yet needing requests still/again
			if (pePiece !=null)
			{
				final long timeSinceActivity =pePiece.getTimeSinceLastActivity()/1000;

				int pieceSpeed =pePiece.getSpeed();
				// block write speed slower than piece speed
				if (pieceSpeed > 0 && timeSinceActivity*pieceSpeed*0.25 > DiskManager.BLOCK_SIZE/1024)
				{
					if(pePiece.getNbUnrequested() > 2)
						pePiece.setSpeed(pieceSpeed-1);
					else
						pePiece.setSpeed(0);
				}


				if(timeSinceActivity > 120)
				{
					pePiece.setSpeed(0);
					// has reserved piece gone stagnant?
					final String reservingPeer =pePiece.getReservedBy();
					if(reservingPeer !=null)
					{
						final PEPeerTransport pt = getTransportFromAddress(reservingPeer);
						// Peer is too slow; Ban them and unallocate the piece
						// but, banning is no good for peer types that get pieces reserved
						// to them for other reasons, such as special seed peers
						if (needsMD5CheckOnCompletion(i))
							badPeerDetected(reservingPeer, i);
						else if (pt != null)
							closeAndRemovePeer(pt, "Reserved piece data timeout; 120 seconds", true);

						pePiece.setReservedBy(null);
					}

					if (!piecePicker.isInEndGameMode()){
						pePiece.checkRequests();
					}

					checkEmptyPiece(i);
				}

			}
		}
	}

	private void
	check99PercentBug()
	{
		// there's a bug whereby pieces are left downloaded but never written. might have been fixed by
		// changes to the "write result" logic, however as a stop gap I'm adding code to scan for such
		// stuck pieces and reset them

		if ( mainloop_loop_count % MAINLOOP_SIXTY_SECOND_INTERVAL == 0 ) {

			long	now = SystemTime.getCurrentTime();

			for ( int i=0;i<pePieces.length;i++){

				PEPiece	pe_piece = pePieces[ i ];

				if ( pe_piece != null ){

					DiskManagerPiece	dm_piece = dm_pieces[i];

					if ( !dm_piece.isDone()){

						if ( pe_piece.isDownloaded()){

							if ( now - pe_piece.getLastDownloadTime(now) > stalled_piece_timeout ){

								// people with *very* slow disk writes can trigger this (I've been talking to a user
								// with a SAN that has .5 second write latencies when checking a file at the same time
								// this means that when dowloading > 32K/sec things start backing up). Eventually the
								// write controller will start blocking the network thread to prevent unlimited
								// queueing but until that time we need to handle this situation slightly better)

								// if there are any outstanding requests for this piece then leave it alone

								if ( !( disk_mgr.hasOutstandingWriteRequestForPiece( i ) ||
										disk_mgr.hasOutstandingReadRequestForPiece( i ) ||
										disk_mgr.hasOutstandingCheckRequestForPiece( i ) )){

									Debug.out( "Fully downloaded piece stalled pending write, resetting p_piece " + i );

									pe_piece.reset();
								}
							}
						}
					}
				}

			}

			if ( hidden_piece >= 0 ){

				int	hp_avail = piecePicker.getAvailability( hidden_piece );

				if ( hp_avail < (dm_pieces[hidden_piece].isDone()?2:1)){

					int[] avails = piecePicker.getAvailability();

					int	num = 0;

					for ( int i=0;i<avails.length;i++){

						if ( avails[i] > 0 && !dm_pieces[i].isDone() && pePieces[i] == null ){

							num++;
						}
					}

					if ( num > 0 ){

						num = RandomUtils.nextInt( num );

						int	backup = -1;

						for ( int i=0;i<avails.length;i++){

							if ( avails[i] > 0 && !dm_pieces[i].isDone() && pePieces[i] == null ){

								if ( backup == -1 ){

									backup = i;
								}

								if ( num == 0 ){

									hidden_piece = i;

									backup = -1;

									break;
								}

								num--;
							}
						}

						if ( backup != -1 ){

							hidden_piece = backup;
						}
					}
				}
			}
		}
	}

	private void checkInterested()
	{
		if ( (mainloop_loop_count %MAINLOOP_ONE_SECOND_INTERVAL) != 0 ){
			return;
		}

		if (lastNeededUndonePieceChange >=piecePicker.getNeededUndonePieceChange())
			return;

		lastNeededUndonePieceChange =piecePicker.getNeededUndonePieceChange();

		final List<PEPeerTransport> peer_transports = peer_transports_cow;
		int cntPeersSnubbed =0;	// recount # snubbed peers while we're at it
		for (int i =0; i <peer_transports.size(); i++)
		{
			final PEPeerTransport peer =peer_transports.get(i);
			peer.checkInterested();
			if (peer.isSnubbed())
				cntPeersSnubbed++;
		}
		setNbPeersSnubbed(cntPeersSnubbed);
	}


	/**
	 * Private method to process the results given by DiskManager's
	 * piece checking thread via asyncPieceChecked(..)
	 */
	private void
	processPieceChecks()
	{
		if ( piece_check_result_list.size() > 0 ){

			final List pieces;

			// process complete piece results

			try{
				piece_check_result_list_mon.enter();

				pieces = new ArrayList( piece_check_result_list );

				piece_check_result_list.clear();

			}finally{

				piece_check_result_list_mon.exit();
			}

			final Iterator it = pieces.iterator();

			while (it.hasNext()) {

				final Object[]	data = (Object[])it.next();

				//bah

				processPieceCheckResult((DiskManagerCheckRequest)data[0],((Integer)data[1]).intValue());

			}
		}
	}

	private void
	checkBadPieces()
	{
		if ( mainloop_loop_count % MAINLOOP_SIXTY_SECOND_INTERVAL == 0 ){

			if ( bad_piece_reported != -1 ){

				DiskManagerCheckRequest	req =
					disk_mgr.createCheckRequest(
						bad_piece_reported,
						new Integer( CHECK_REASON_BAD_PIECE_CHECK ));

				req.setLowPriority( true );

			   	if ( Logger.isEnabled()){

					Logger.log(
							new LogEvent(
								disk_mgr.getTorrent(), LOGID,
								"Rescanning reported-bad piece " + bad_piece_reported ));

			   	}

				bad_piece_reported	= -1;

				try{
					disk_mgr.enqueueCheckRequest( req, this );

				}catch( Throwable e ){


					Debug.printStackTrace(e);
				}
			}
		}
	}

	private void
	checkRescan()
	{
		if ( rescan_piece_time == 0 ){

			// pending a piece completion

			return;
		}

		if ( next_rescan_piece == -1 ){

			if ( mainloop_loop_count % MAINLOOP_FIVE_SECOND_INTERVAL == 0 ){

				if ( adapter.isPeriodicRescanEnabled()){

					next_rescan_piece	= 0;
				}
			}
		}else{

			if ( mainloop_loop_count % MAINLOOP_TEN_MINUTE_INTERVAL == 0 ){

				if ( !adapter.isPeriodicRescanEnabled()){

					next_rescan_piece	= -1;
				}
			}
		}

		if ( next_rescan_piece == -1 ){

			return;
		}

		// delay as required

		final long	now = SystemTime.getCurrentTime();

		if ( rescan_piece_time > now ){

			rescan_piece_time	= now;
		}

		// 250K/sec limit

		final long	piece_size = disk_mgr.getPieceLength();

		final long	millis_per_piece = piece_size / 250;

		if ( now - rescan_piece_time < millis_per_piece ){

			return;
		}

		while( next_rescan_piece != -1 ){

			int	this_piece = next_rescan_piece;

			next_rescan_piece++;

			if ( next_rescan_piece == _nbPieces ){

				next_rescan_piece	= -1;
			}

				// this functionality is to pick up pieces that have been downloaded OUTSIDE of
				// Azureus - e.g. when two torrents are sharing a single file. Hence the check on
				// the piece NOT being done

			if ( pePieces[this_piece] == null && !dm_pieces[this_piece].isDone() && dm_pieces[this_piece].isNeeded()){

				DiskManagerCheckRequest	req =
					disk_mgr.createCheckRequest(
							this_piece,
							new Integer( CHECK_REASON_SCAN ));

				req.setLowPriority( true );

				if ( Logger.isEnabled()){

					Logger.log(
							new LogEvent(
									disk_mgr.getTorrent(), LOGID,
									"Rescanning piece " + this_piece ));

				}

				rescan_piece_time	= 0;	// mark as check piece in process

				try{
					disk_mgr.enqueueCheckRequest( req, this );

				}catch( Throwable e ){

					rescan_piece_time	= now;

					Debug.printStackTrace(e);
				}

				break;
			}
		}
	}

	@Override
	public void
	badPieceReported(
		PEPeerTransport		originator,
		int					piece_number )
	{
		Debug.outNoStack( getDisplayName() + ": bad piece #" + piece_number + " reported by " + originator.getIp());

		if ( piece_number < 0 || piece_number >= _nbPieces ){

			return;
		}

		bad_piece_reported = piece_number;
	}

	private static final int FE_EVENT_LIMIT	= 5;	// don't make > 15 without changing bloom!S

		/*
		 * We keep track of both peer connection events and attempts to re-download the same fast piece
		 * for a given peer to prevent an attack whereby a peer connects and repeatedly downloads the same
		 * fast piece, or alternatively connects, downloads some fast pieces, disconnects, then does so
		 * again.
		 */

	@Override
	public boolean
	isFastExtensionPermitted(
		PEPeerTransport		originator )
	{
		try{
			byte[] key = originator.getIp().getBytes( Constants.BYTE_ENCODING );

			synchronized( naughty_fast_extension_bloom ){

				int events = naughty_fast_extension_bloom.add( key );

				if ( events < FE_EVENT_LIMIT ){

					return( true );
				}

				Logger.log(new LogEvent(disk_mgr.getTorrent(), LOGID,
						"Fast extension disabled for " + originator.getIp() + " due to repeat connections" ));
			}

		}catch( Throwable e ){
		}

		return( false );
	}

	@Override
	public void
	reportBadFastExtensionUse(
		PEPeerTransport		originator )
	{
		try{
			byte[] key = originator.getIp().getBytes( Constants.BYTE_ENCODING );

			synchronized( naughty_fast_extension_bloom ){

				if ( naughty_fast_extension_bloom.add( key ) == FE_EVENT_LIMIT ){

					Logger.log(new LogEvent(disk_mgr.getTorrent(), LOGID,
							"Fast extension disabled for " + originator.getIp() + " due to repeat requests for the same pieces" ));
				}
			}
		}catch( Throwable e ){
		}
	}

	@Override
	public void
	setStatsReceiver(
		PEPeerManager.StatsReceiver	receiver )
	{
		stats_receiver = receiver;
	}

	@Override
	public void
	statsRequest(
		PEPeerTransport 	originator,
		Map 				request )
	{
		Map		reply = new HashMap();

		adapter.statsRequest( originator, request, reply );

		if ( reply.size() > 0 ){

			originator.sendStatsReply( reply );
		}
	}

	@Override
	public void
	statsReply(
		PEPeerTransport 	originator,
		Map 				reply )
	{
		PEPeerManager.StatsReceiver receiver = stats_receiver;

		if ( receiver != null ){

			receiver.receiveStats( originator, reply );
		}
	}

	/**
	 * This method checks if the downloading process is finished.
	 *
	 */

	private void
	checkFinished(
		final boolean start_of_day )
	{
		final boolean all_pieces_done =disk_mgr.getRemainingExcludingDND() ==0;

		if ( all_pieces_done ){

			seeding_mode	= true;

			prefer_udp_bloom = null;

			piecePicker.clearEndGameChunks();

			if (!start_of_day)
				adapter.setStateFinishing();

			_timeFinished = SystemTime.getCurrentTime();
			final List<PEPeerTransport> peer_transports = peer_transports_cow;

			//remove previous snubbing
			for (int i =0; i <peer_transports.size(); i++ )
			{
				final PEPeerTransport pc = peer_transports.get(i);
				pc.setSnubbed(false);
			}
			setNbPeersSnubbed(0);

			final boolean checkPieces =COConfigurationManager.getBooleanParameter( "Check Pieces on Completion" );

			//re-check all pieces to make sure they are not corrupt, but only if we weren't already complete
			if (checkPieces &&!start_of_day)
			{
				final DiskManagerCheckRequest req =disk_mgr.createCheckRequest(-1, new Integer(CHECK_REASON_COMPLETE));
				disk_mgr.enqueueCompleteRecheckRequest(req, this);
			}

			_timeStartedSeeding 		= SystemTime.getCurrentTime();
			_timeStartedSeeding_mono 	= SystemTime.getMonotonousTime();

			try{
				disk_mgr.saveResumeData(false);

			}catch( Throwable e ){
				Debug.out( "Failed to save resume data", e );
			}

			adapter.setStateSeeding( start_of_day );

			final AESemaphore waiting_it = new AESemaphore( "PEC:DE" );

			new AEThread2( "PEC:DE" ){
				@Override
				public void
				run()
				{
					try{
						disk_mgr.downloadEnded(
							new DiskManager.OperationStatus()
							{
								@Override
								public void
								gonnaTakeAWhile(
									GettingThere gt )
								{
									boolean	async_set = false;

									synchronized( PEPeerControlImpl.this ){

										if ( finish_in_progress == null ){

											finish_in_progress = gt;

											async_set = true;
										}
									}

									if ( async_set ){

										waiting_it.release();
									}
								}
							});
					}finally{

						waiting_it.release();
					}
				}
			}.start();

			waiting_it.reserve();

		}else{

			seeding_mode = false;
		}
	}

	protected void
	checkCompletionState()
	{
		if ( mainloop_loop_count % MAINLOOP_ONE_SECOND_INTERVAL != 0 ){

			return;
		}

		boolean dm_done = disk_mgr.getRemainingExcludingDND() == 0;

		if ( seeding_mode ){

			if ( !dm_done ){

				seeding_mode = false;

				_timeStartedSeeding 		= -1;
				_timeStartedSeeding_mono 	= -1;
				_timeFinished				= 0;

				Logger.log(
						new LogEvent(	disk_mgr.getTorrent(), LOGID,
								"Turning off seeding mode for PEPeerManager"));
			}

		}else{

			if ( dm_done ){

				checkFinished( false );

				if ( seeding_mode ){

					Logger.log(
							new LogEvent(	disk_mgr.getTorrent(), LOGID,
									"Turning on seeding mode for PEPeerManager"));

				}
			}
		}
	}

	/**
	 * This method will locate expired requests on peers, will cancel them,
	 * and mark the peer as snubbed if we haven't received usefull data from
	 * them within the last 60 seconds
	 */
	private void checkRequests()
	{
		// to be honest I don't see why this can't be 5 seconds, but I'm trying 1 second
		// now as the existing 0.1 second is crazy given we're checking for events that occur
		// at 60+ second intervals

		if ( mainloop_loop_count % MAINLOOP_ONE_SECOND_INTERVAL != 0 ){

			return;
		}

		final long now =SystemTime.getCurrentTime();

		//for every connection
		final List<PEPeerTransport> peer_transports = peer_transports_cow;
		for (int i =peer_transports.size() -1; i >=0 ; i--)
		{
			final PEPeerTransport pc = peer_transports.get(i);
			if (pc.getPeerState() ==PEPeer.TRANSFERING)
			{
				final List expired = pc.getExpiredRequests();
				if (expired !=null &&expired.size() >0)
				{   // now we know there's a request that's > 60 seconds old
					final boolean isSeed =pc.isSeed();
					// snub peers that haven't sent any good data for a minute
					final long timeSinceGoodData =pc.getTimeSinceGoodDataReceived();
					if (timeSinceGoodData <0 ||timeSinceGoodData >60 *1000)
						pc.setSnubbed(true);

					//Only cancel first request if more than 2 mins have passed
					DiskManagerReadRequest request =(DiskManagerReadRequest) expired.get(0);

					final long timeSinceData =pc.getTimeSinceLastDataMessageReceived();
					final boolean noData =(timeSinceData <0) ||timeSinceData >(1000 *(isSeed ?120 :60));
					final long timeSinceOldestRequest = now - request.getTimeCreated(now);


					//for every expired request
					for (int j = (timeSinceOldestRequest >120 *1000 && noData)  ? 0 : 1; j < expired.size(); j++)
					{
						//get the request object
						request =(DiskManagerReadRequest) expired.get(j);
						//Only cancel first request if more than 2 mins have passed
						pc.sendCancel(request);				//cancel the request object
						//get the piece number
						final int pieceNumber = request.getPieceNumber();
						PEPiece	pe_piece = pePieces[pieceNumber];
						//unmark the request on the block
						if ( pe_piece != null )
							pe_piece.clearRequested(request.getOffset() /DiskManager.BLOCK_SIZE);
						// remove piece if empty so peers can choose something else, except in end game
						if (!piecePicker.isInEndGameMode())
							checkEmptyPiece(pieceNumber);
					}
				}
			}
		}
	}

	private void
	updateTrackerAnnounceInterval()
	{
		if ( mainloop_loop_count % MAINLOOP_FIVE_SECOND_INTERVAL != 0 ){
			return;
		}

		final int WANT_LIMIT = 100;

		int[] _num_wanted = getMaxNewConnectionsAllowed();

		int num_wanted;

		if ( _num_wanted[0] < 0 ){

			num_wanted = WANT_LIMIT;	// unlimited

		}else{

			num_wanted = _num_wanted[0] + _num_wanted[1];

			if ( num_wanted > WANT_LIMIT ){

				num_wanted = WANT_LIMIT;
			}
		}

		final boolean has_remote = adapter.isNATHealthy();

		if( has_remote ) {
			//is not firewalled, so can accept incoming connections,
			//which means no need to continually keep asking the tracker for peers
			num_wanted = (int)(num_wanted / 1.5);
		}


		int current_connection_count = PeerIdentityManager.getIdentityCount( _hash );

		final TRTrackerScraperResponse tsr = adapter.getTrackerScrapeResponse();

		if( tsr != null && tsr.isValid() ) {  //we've got valid scrape info
			final int num_seeds = tsr.getSeeds();
			final int num_peers = tsr.getPeers();

			final int swarm_size;

			if( seeding_mode ) {
				//Only use peer count when seeding, as other seeds are unconnectable.
				//Since trackers return peers randomly (some of which will be seeds),
				//backoff by the seed2peer ratio since we're given only that many peers
				//on average each announce.
				final float ratio = (float)num_peers / (num_seeds + num_peers);
				swarm_size = (int)(num_peers * ratio);
			}
			else {
				swarm_size = num_peers + num_seeds;
			}

			if( swarm_size < num_wanted ) {  //lower limit to swarm size if necessary
				num_wanted = swarm_size;
			}
		}

		if( num_wanted < 1 ) {  //we dont need any more connections
			adapter.setTrackerRefreshDelayOverrides( 100 );  //use normal announce interval
			return;
		}

		if( current_connection_count == 0 )  current_connection_count = 1;  //fudge it :)

		final int current_percent = (current_connection_count * 100) / (current_connection_count + num_wanted);

		adapter.setTrackerRefreshDelayOverrides( current_percent );  //set dynamic interval override
	}

	@Override
	public boolean
	hasDownloadablePiece()
	{
		return( piecePicker.hasDownloadablePiece());
	}

	@Override
	public int
	getBytesQueuedForUpload()
	{
		return( bytes_queued_for_upload );
	}

	@Override
	public int
	getNbPeersWithUploadQueued()
	{
		return( connections_with_queued_data );
	}

	@Override
	public int
	getNbPeersWithUploadBlocked()
	{
		return( connections_with_queued_data_blocked );
	}

	@Override
	public int
	getNbPeersUnchoked()
	{
		return( connections_unchoked );
	}

	@Override
	public int[] getAvailability()
	{
		return piecePicker.getAvailability();
	}

	//this only gets called when the My Torrents view is displayed
	@Override
	public float getMinAvailability()
	{
		return piecePicker.getMinAvailability();
	}

	@Override
	public float getMinAvailability(int file_index )
	{
		return piecePicker.getMinAvailability( file_index );
	}

	@Override
	public long getBytesUnavailable() {
		return piecePicker.getBytesUnavailable();
	}

	@Override
	public float getAvgAvail()
	{
		return piecePicker.getAvgAvail();
	}

	@Override
	public long getAvailWentBadTime()
	{
		long went_bad = piecePicker.getAvailWentBadTime();

				// there's a chance a seed connects and then disconnects (when we're seeding) quickly
				// enough for the piece picker not to notice...

		if ( piecePicker.getMinAvailability() < 1.0 && last_seed_disconnect_time > went_bad - 5000 ){

			went_bad = last_seed_disconnect_time;
		}

		return( went_bad );
	}

	@Override
	public void addPeerTransport(PEPeerTransport transport ) {
    if (!ip_filter.isInRange(transport.getIp(), getDisplayName(), getTorrentHash())) {
			final ArrayList peer_transports = peer_transports_cow;

			if (!peer_transports.contains( transport )) {
				addToPeerTransports(transport);
			}
			else{
				Debug.out( "addPeerTransport():: peer_transports.contains(transport): SHOULD NEVER HAPPEN !" );
				transport.closeConnection( "already connected" );
			}
		}
		else {
			transport.closeConnection( "IP address blocked by filters" );
		}
	}


	/**
	 * Do all peer choke/unchoke processing.
	 */
	private void doUnchokes() {

		// logic below is either 1 second or 10 secondly, bail out early id neither

		if( !UploadSlotManager.AUTO_SLOT_ENABLE ) {		 //manual per-torrent unchoke slot mode

			if( mainloop_loop_count % MAINLOOP_ONE_SECOND_INTERVAL != 0 ) {
				return;
			}

			final int max_to_unchoke = adapter.getMaxUploads();  //how many simultaneous uploads we should consider
			final ArrayList peer_transports = peer_transports_cow;

			//determine proper unchoker
			if( seeding_mode ) {
				if( unchoker == null || !(unchoker.isSeedingUnchoker()) ) {
					unchoker = UnchokerFactory.getSingleton().getUnchoker( true );
				}
			}
			else {
				if( unchoker == null || unchoker.isSeedingUnchoker()) {
					unchoker = UnchokerFactory.getSingleton().getUnchoker( false );
				}
			}

				//do main choke/unchoke update every 10 secs

			if ( mainloop_loop_count % MAINLOOP_TEN_SECOND_INTERVAL == 0 ){

				final boolean refresh = mainloop_loop_count % MAINLOOP_THIRTY_SECOND_INTERVAL == 0;

				boolean	do_high_latency_peers =  mainloop_loop_count % MAINLOOP_TWENTY_SECOND_INTERVAL == 0 ;

				if ( do_high_latency_peers ){

					boolean ok = false;

					for ( String net: AENetworkClassifier.AT_NON_PUBLIC ){

						if ( adapter.isNetworkEnabled( net )){

							ok = true;

							break;
						}
					}

					if ( !ok ){

						do_high_latency_peers = false;
					}
				}

				unchoker.calculateUnchokes( max_to_unchoke, peer_transports, refresh, adapter.hasPriorityConnection(), do_high_latency_peers );

				ArrayList	chokes 		= unchoker.getChokes();
				ArrayList	unchokes	= unchoker.getUnchokes();

				addFastUnchokes( unchokes );

				UnchokerUtil.performChokes( chokes, unchokes );

			}else if ( mainloop_loop_count % MAINLOOP_ONE_SECOND_INTERVAL == 0 ) {  //do quick unchoke check every 1 sec

				ArrayList unchokes = unchoker.getImmediateUnchokes( max_to_unchoke, peer_transports );

				addFastUnchokes( unchokes );

				UnchokerUtil.performChokes( null, unchokes );
			}
		}
	}

	private void
	addFastUnchokes(
		ArrayList	peers_to_unchoke )
	{
		for( Iterator<PEPeerTransport> it=peer_transports_cow.iterator();it.hasNext();) {

			PEPeerTransport peer = it.next();

			if ( 	peer.getConnectionState() != PEPeerTransport.CONNECTION_FULLY_ESTABLISHED ||
					!UnchokerUtil.isUnchokable( peer, true ) ||
					peers_to_unchoke.contains( peer )){

				continue;
			}

			if( peer.isLANLocal()){

				peers_to_unchoke.add( peer );

			}else if ( 	fast_unchoke_new_peers &&
						peer.getData( "fast_unchoke_done" ) == null ){

				peer.setData( "fast_unchoke_done", "" );

				peers_to_unchoke.add( peer );
			}
		}
	}

//	send the have requests out
	private void sendHave(int pieceNumber) {
		//fo
		final List<PEPeerTransport> peer_transports = peer_transports_cow;

		for (int i = 0; i < peer_transports.size(); i++) {
			//get a peer connection
			final PEPeerTransport pc = peer_transports.get(i);
			//send the have message
			pc.sendHave(pieceNumber);
		}

	}

	// Method that checks if we are connected to another seed, and if so, disconnect from him.
	private void checkSeeds() {
		//proceed on mainloop 1 second intervals if we're a seed and we want to force disconnects
		if ((mainloop_loop_count % MAINLOOP_ONE_SECOND_INTERVAL) != 0)
			return;

		if (!disconnect_seeds_when_seeding ){
			return;
		}

		List<PEPeerTransport> to_close = null;

		final List<PEPeerTransport> peer_transports = peer_transports_cow;
		for (int i = 0; i < peer_transports.size(); i++) {
			final PEPeerTransport pc = peer_transports.get(i);

			if (pc != null && pc.getPeerState() == PEPeer.TRANSFERING && ((isSeeding() && pc.isSeed()) || pc.isRelativeSeed())) {
				if( to_close == null )  to_close = new ArrayList();
				to_close.add( pc );
			}
		}

		if( to_close != null ) {
			for( int i=0; i < to_close.size(); i++ ) {
				closeAndRemovePeer( to_close.get(i), "disconnect other seed when seeding", false );
			}
		}
	}




	private void updateStats() {

		if ( (mainloop_loop_count %MAINLOOP_ONE_SECOND_INTERVAL) != 0 ){
			return;
		}

		stats_tick_count++;

		//calculate seeds vs peers
		final ArrayList<PEPeerTransport> peer_transports = peer_transports_cow;

		int	new_pending_tcp_connections 	= 0;
		int new_connecting_tcp_connections	= 0;

		int	new_seeds = 0;
		int new_peers = 0;
		int new_tcp_incoming 	= 0;
		int new_udp_incoming  	= 0;
		int new_utp_incoming  	= 0;

		int	bytes_queued 	= 0;
		int	con_queued		= 0;
		int con_blocked		= 0;
		int con_unchoked	= 0;

		for ( Iterator<PEPeerTransport> it=peer_transports.iterator();it.hasNext();){

			final PEPeerTransport pc = it.next();

			if ( pc.getPeerState() == PEPeer.TRANSFERING) {

				if ( !pc.isChokedByMe()){

					con_unchoked++;
				}

				Connection connection = pc.getPluginConnection();

				if ( connection != null ){

					OutgoingMessageQueue mq = connection.getOutgoingMessageQueue();

					int q = mq.getDataQueuedBytes() + mq.getProtocolQueuedBytes();

					bytes_queued += q;

					if ( q > 0 ){

						con_queued++;

						if ( mq.isBlocked()){

							con_blocked++;
						}
					}
				}

				if (pc.isSeed())
					new_seeds++;
				else
					new_peers++;

				if ( pc.isIncoming() && !pc.isLANLocal()){

					if ( pc.isTCP() ) {

						if ( pc.getNetwork() == AENetworkClassifier.AT_PUBLIC ){
						
							new_tcp_incoming++;
						}
					}else{

						String protocol = pc.getProtocol();

						if ( protocol.equals( "UDP" )){

							new_udp_incoming++;

						}else{

							new_utp_incoming++;
						}
					}
				}
			}else{
				if ( pc.isTCP()){

					int c_state = pc.getConnectionState();

					if ( c_state == PEPeerTransport.CONNECTION_PENDING ){

						new_pending_tcp_connections++;

					}else if ( c_state == PEPeerTransport.CONNECTION_CONNECTING ){

						new_connecting_tcp_connections++;
					}
				}
			}
		}

		_seeds = new_seeds;
		_peers = new_peers;
		_remotesTCPNoLan = new_tcp_incoming;
		_remotesUDPNoLan = new_udp_incoming;
		_remotesUTPNoLan = new_utp_incoming;
		_tcpPendingConnections = new_pending_tcp_connections;
		_tcpConnectingConnections = new_connecting_tcp_connections;

		bytes_queued_for_upload 				= bytes_queued;
		connections_with_queued_data			= con_queued;
		connections_with_queued_data_blocked	= con_blocked;
		connections_unchoked					= con_unchoked;

		_stats.update( stats_tick_count );
	}
	/**
	 * The way to unmark a request as being downloaded, or also
	 * called by Peer connections objects when connection is closed or choked
	 * @param request a DiskManagerReadRequest holding details of what was canceled
	 */
	@Override
	public void requestCanceled(DiskManagerReadRequest request)
	{
		final int pieceNumber =request.getPieceNumber();  //get the piece number
		PEPiece pe_piece = pePieces[pieceNumber];
		if (pe_piece != null ){
			pe_piece.clearRequested(request.getOffset() /DiskManager.BLOCK_SIZE);
		}
	}


	public PEPeerControl
	getControl()
	{
		return( this );
	}

	@Override
	public byte[][]
	getSecrets(
		int	crypto_level )
	{
		return( adapter.getSecrets( crypto_level ));
	}

//	get the hash value
	@Override
	public byte[] getHash() {
		return _hash.getDataID();
	}

	@Override
	public PeerIdentityDataID
	getPeerIdentityDataID()
	{
		return( _hash );
	}

//	get the peer id value
	@Override
	public byte[] getPeerId() {
		return _myPeerId;
	}

//	get the remaining percentage
	@Override
	public long getRemaining() {
		return disk_mgr.getRemaining();
	}


	@Override
	public void discarded(PEPeer peer, int length) {
		if (length > 0){
			_stats.discarded(peer, length);

				// discards are more likely during end-game-mode

			if ( ban_peer_discard_ratio > 0 && !( piecePicker.isInEndGameMode() || piecePicker.hasEndGameModeBeenAbandoned())){

				long	received 	= peer.getStats().getTotalDataBytesReceived();
				long	discarded	= peer.getStats().getTotalBytesDiscarded();

				long	non_discarded = received - discarded;

				if ( non_discarded < 0 ){

					non_discarded = 0;
				}

				if ( discarded >= ban_peer_discard_min_kb * 1024L ){

					if ( 	non_discarded == 0 ||
							((float)discarded) / non_discarded >= ban_peer_discard_ratio ){

						badPeerDetected( peer.getIp(), -1 );
					}
				}
			}
		}
	}

	@Override
	public void dataBytesReceived(PEPeer peer, int length) {
		if (length > 0) {
			_stats.dataBytesReceived(peer,length);

			_averageReceptionSpeed.addValue(length);
		}
	}


	@Override
	public void protocolBytesReceived(PEPeer peer, int length ) {
		if (length > 0) {
			_stats.protocolBytesReceived(peer,length);
		}
	}

	@Override
	public void dataBytesSent(PEPeer peer, int length) {
		if (length > 0) {
			_stats.dataBytesSent(peer, length );
		}
	}


	@Override
	public void protocolBytesSent(PEPeer peer, int length ) {
		if (length > 0) {
			_stats.protocolBytesSent(peer,length);
		}
	}

	/** DiskManagerWriteRequestListener message
	 * @see com.biglybt.core.disk.DiskManagerWriteRequestListener
	 */
	@Override
	public void writeCompleted(DiskManagerWriteRequest request)
	{
		final int pieceNumber =request.getPieceNumber();

		DiskManagerPiece	dm_piece = dm_pieces[pieceNumber];

		if (!dm_piece.isDone()){

			final PEPiece pePiece =pePieces[pieceNumber];

			if ( pePiece != null ){

				Object user_data = request.getUserData();

				String key;

				if ( user_data instanceof String ){

					key = (String)user_data;

				}else if ( user_data instanceof PEPeer ){

					key = ((PEPeer)user_data).getIp();

				}else{

					key = "<none>";
				}

				pePiece.setWritten( key, request.getOffset() /DiskManager.BLOCK_SIZE );

			}else{

				// this is a way of fixing a 99.9% bug where a dmpiece is left in a
				// fully downloaded state with the underlying pe_piece null. Possible explanation is
				// that a slow peer sends an entire piece at around the time a pe_piece gets reset
				// due to inactivity.

				// we also get here when recovering data that has come in late after the piece has
				// been abandoned

				dm_piece.setWritten( request.getOffset() /DiskManager.BLOCK_SIZE );
			}
		}
	}

	@Override
	public void
	writeFailed(
			DiskManagerWriteRequest 	request,
			Throwable		 			cause )
	{
		// if the write has failed then the download will have been stopped so there is no need to try
		// and reset the piece
	}


	/** This method will queue up a dism manager write request for the block if the block is not already written.
	 * It will send out cancels for the block to all peer either if in end-game mode, or per cancel param
	 * @param pieceNumber to potentialy write to
	 * @param offset within piece to queue write for
	 * @param data to be writen
	 * @param sender peer that sent this data
	 * @param cancel if cancels definatly need to be sent to all peers for this request
	 */
	@Override
	public void writeBlock(int pieceNumber, int offset, DirectByteBuffer data, Object sender, boolean cancel)
	{
		final int blockNumber =offset /DiskManager.BLOCK_SIZE;
		final DiskManagerPiece dmPiece =dm_pieces[pieceNumber];
		if (dmPiece.isWritten(blockNumber))
		{
			data.returnToPool();
			return;
		}

		PEPiece	pe_piece = pePieces[ pieceNumber ];

		if ( pe_piece != null ){

			pe_piece.setDownloaded( offset );
		}

		final DiskManagerWriteRequest request =disk_mgr.createWriteRequest(pieceNumber, offset, data, sender);
		disk_mgr.enqueueWriteRequest(request, this );
		// In case we are in endGame mode, remove the block from the chunk list
		if (piecePicker.isInEndGameMode())
			piecePicker.removeFromEndGameModeChunks(pieceNumber, offset);
		if (cancel ||piecePicker.isInEndGameMode())
		{   // cancel any matching outstanding download requests
			//For all connections cancel the request
			final List<PEPeerTransport> peer_transports = peer_transports_cow;
			for (int i=0;i<peer_transports.size();i++)
			{
				final PEPeerTransport connection = peer_transports.get(i);
				final DiskManagerReadRequest dmr =disk_mgr.createReadRequest(pieceNumber, offset, dmPiece.getBlockSize(blockNumber));
				connection.sendCancel( dmr );
			}
		}
	}


//	/**
//	* This method is only called when a block is received after the initial request expired,
//	* but the data has not yet been fulfilled by any other peer, so we use the block data anyway
//	* instead of throwing it away, and cancel any outstanding requests for that block that might have
//	* been sent after initial expiry.
//	*/
//	public void writeBlockAndCancelOutstanding(int pieceNumber, int offset, DirectByteBuffer data,PEPeer sender) {
//	final int blockNumber =offset /DiskManager.BLOCK_SIZE;
//	final DiskManagerPiece dmPiece =dm_pieces[pieceNumber];
//	if (dmPiece.isWritten(blockNumber))
//	{
//	data.returnToPool();
//	return;
//	}
//	DiskManagerWriteRequest request =disk_mgr.createWriteRequest(pieceNumber, offset, data, sender);
//	disk_mgr.enqueueWriteRequest(request, this);

//	// cancel any matching outstanding download requests
//	List peer_transports =peer_transports_cow;
//	for (int i =0; i <peer_transports.size(); i++)
//	{
//	PEPeerTransport connection =(PEPeerTransport) peer_transports.get(i);
//	DiskManagerReadRequest dmr =disk_mgr.createReadRequest(pieceNumber, offset, dmPiece.getBlockSize(blockNumber));
//	connection.sendCancel(dmr);
//	}
//	}


	@Override
	public boolean isWritten(int piece_number, int offset)
	{
		return dm_pieces[piece_number].isWritten(offset /DiskManager.BLOCK_SIZE);
	}

	@Override
	public boolean
	validateReadRequest(
			PEPeerTransport	originator,
			int				pieceNumber,
			int 			offset,
			int 			length)
	{
		if ( disk_mgr.checkBlockConsistencyForRead(originator.getClient() + ": " + originator.getIp(), true, pieceNumber, offset, length)){

			if ( enable_seeding_piece_rechecks && isSeeding()){

				DiskManagerPiece	dm_piece = dm_pieces[pieceNumber];

				int	read_count = dm_piece.getReadCount()&0xffff;

				if ( read_count < SEED_CHECK_WAIT_MARKER - 1 ){

					read_count++;

					dm_piece.setReadCount((short)read_count );
				}
			}

			return( true );
		}else{

			return( false );
		}
	}

	@Override
	public boolean
	validateHintRequest(
			PEPeerTransport	originator,
			int				pieceNumber,
			int 			offset,
			int 			length)
	{
		return( disk_mgr.checkBlockConsistencyForHint(originator.getClient() + ": " + originator.getIp(),pieceNumber, offset, length ));
	}

	@Override
	public boolean
	validatePieceReply(
		PEPeerTransport		originator,
		int 				pieceNumber,
		int 				offset,
		DirectByteBuffer 	data)
	{
		return disk_mgr.checkBlockConsistencyForWrite(originator.getClient() + ": " + originator.getIp(),pieceNumber, offset, data);
	}

	@Override
	public int getAvailability(int pieceNumber)
	{
		return piecePicker.getAvailability(pieceNumber);
	}

	@Override
	public void havePiece(int pieceNumber, int pieceLength, PEPeer pcOrigin) {
		piecePicker.addHavePiece(pcOrigin, pieceNumber);
		_stats.haveNewPiece(pieceLength);

		if(superSeedMode) {
			superSeedPieces[pieceNumber].peerHasPiece(pcOrigin);
			if(pieceNumber == pcOrigin.getUniqueAnnounce()) {
				pcOrigin.setUniqueAnnounce(-1);
				superSeedModeNumberOfAnnounces--;
			}
		}
		int availability =piecePicker.getAvailability(pieceNumber) -1;
		if (availability < 4) {
			if (dm_pieces[pieceNumber].isDone())
				availability--;
			if (availability <= 0)
				return;
			//for all peers

			final List<PEPeerTransport> peer_transports = peer_transports_cow;

			for (int i = peer_transports.size() - 1; i >= 0; i--) {
				final PEPeerTransport pc = peer_transports.get(i);
				if (pc !=pcOrigin &&pc.getPeerState() ==PEPeer.TRANSFERING &&pc.isPieceAvailable(pieceNumber))
					((PEPeerStatsImpl)pc.getStats()).statisticalSentPiece(pieceLength / availability);
			}
		}
	}

	@Override
	public int getPieceLength(int pieceNumber) {

		return disk_mgr.getPieceLength(pieceNumber);

	}

	@Override
	public int
	getNbPeers()
	{
		return _peers;
	}

	@Override
	public int getNbSeeds()
	{
		return _seeds;
	}

	@Override
	public int getNbRemoteTCPConnections()
	{
		return _remotesTCPNoLan;
	}

	@Override
	public int getNbRemoteUDPConnections()
	{
		return _remotesUDPNoLan;
	}
	@Override
	public int getNbRemoteUTPConnections()
	{
		return _remotesUTPNoLan;
	}

	@Override
	public long getLastRemoteConnectionTime()
	{
		return( last_remote_time );
	}

	@Override
	public PEPeerManagerStats getStats() {
		return _stats;
	}

	@Override
	public int
	getNbPeersStalledPendingLoad()
	{
		int	res = 0;

		Iterator<PEPeerTransport> it = peer_transports_cow.iterator();

		while( it.hasNext()){

			PEPeerTransport transport = it.next();

			if ( transport.isStalledPendingLoad()){

				res ++;
			}
		}

		return( res );
	}

	/**
	 * Returns the ETA time in seconds.
	 *   0 = download is complete.
	 * < 0 = download is complete and it took -xxx time to complete.
	 * Constants.CRAPPY_INFINITE_AS_LONG = incomplete and 0 average speed
	 *
	 * @note eta will be zero for incomplete torrent in the following case:<br>
	 * * Torrent has DND files
	 * * non-DND files are all downloaded, however, the last piece is incomplete.
	 *   The blocks for the file are downloaded, but the piece is not hash checked,
	 *   and therefore potentially incomplete.
	 */
	@Override
	public long
	getETA( boolean smoothed )
	{
		final long	now = SystemTime.getCurrentTime();

		if ( now < last_eta_calculation || now - last_eta_calculation > 900 ){

			long dataRemaining = disk_mgr.getRemainingExcludingDND();

			if ( dataRemaining > 0 ){

				int writtenNotChecked = 0;

				for (int i = 0; i < _nbPieces; i++)
				{
					if (dm_pieces[i].isInteresting()){
						writtenNotChecked +=dm_pieces[i].getNbWritten() *DiskManager.BLOCK_SIZE;
					}
				}

				dataRemaining = dataRemaining - writtenNotChecked;

				if  (dataRemaining < 0 ){
					dataRemaining	= 0;
				}
			}

			long	jagged_result;
			long	smooth_result;

			if (dataRemaining == 0) {
				final long timeElapsed = (_timeFinished - _timeStarted)/1000;
				//if time was spent downloading....return the time as negative
				if(timeElapsed > 1){
					jagged_result = timeElapsed * -1;
				}else{
					jagged_result = 0;
				}
				smooth_result = jagged_result;
			}else{

				{
					final long averageSpeed = _averageReceptionSpeed.getAverage();
					long lETA = (averageSpeed == 0) ? Constants.CRAPPY_INFINITE_AS_LONG : dataRemaining / averageSpeed;
					// stop the flickering of ETA from "Finished" to "x seconds" when we are
					// just about complete, but the data rate is jumpy.
					if (lETA == 0)
						lETA = 1;
					jagged_result = lETA;
				}
				{
					final long averageSpeed = _stats.getSmoothedDataReceiveRate();
					long lETA = (averageSpeed == 0) ? Constants.CRAPPY_INFINITE_AS_LONG : dataRemaining / averageSpeed;
					// stop the flickering of ETA from "Finished" to "x seconds" when we are
					// just about complete, but the data rate is jumpy.
					if (lETA == 0)
						lETA = 1;
					smooth_result = lETA;
				}
			}

			last_eta				= jagged_result;
			last_eta_smoothed		= smooth_result;
			last_eta_calculation	= now;
		}

		return( smoothed?last_eta_smoothed:last_eta );
	}

	@Override
	public boolean
	isRTA()
	{
		return( piecePicker.getRTAProviders().size() > 0 );
	}

	private void
	addToPeerTransports(
		PEPeerTransport		peer )
	{
		boolean added = false;

		List limiters;

		try{
			peer_transports_mon.enter();

			// if it is already disconnected (synchronous failure during connect
			// for example) don't add it

			if ( peer.getPeerState() == PEPeer.DISCONNECTED ){

				return;
			}

			if( peer_transports_cow.contains( peer ) ){
				Debug.out( "Transport added twice" );
				return;  //we do not want to close it
			}

			if( is_running ) {
				//copy-on-write semantics
				final ArrayList new_peer_transports = new ArrayList(peer_transports_cow.size() +1);

				new_peer_transports.addAll( peer_transports_cow );

				new_peer_transports.add( peer );

				peer_transports_cow = new_peer_transports;

				if ( upload_diabled ){
				
					peer.setUploadDisabled( upload_limited_rate_group, true );
				}
				
				if ( download_diabled ){
					
					peer.setDownloadDisabled( download_limited_rate_group, true );
				}
				
				added = true;
			}

			limiters = external_rate_limiters_cow;
		}
		finally{
			peer_transports_mon.exit();
		}

		if( added ) {
			boolean incoming = peer.isIncoming();

			_stats.haveNewConnection( incoming );

			if ( incoming ){
				long	connect_time = SystemTime.getCurrentTime();

				if ( connect_time > last_remote_time ){

					last_remote_time = connect_time;
				}
			}

			if ( limiters != null ){

				for (int i=0;i<limiters.size();i++){

					Object[]	entry = (Object[])limiters.get(i);

					peer.addRateLimiter((LimitedRateGroup)entry[0],((Boolean)entry[1]).booleanValue());
				}
			}

			peerAdded( peer );
		}
		else {
			peer.closeConnection( "PeerTransport added when manager not running" );
		}
	}

	@Override
	public void
	addRateLimiter(
		LimitedRateGroup	group,
		boolean				upload )
	{
		List<PEPeerTransport>	transports;

		try{
			peer_transports_mon.enter();

			ArrayList<Object[]>	new_limiters = new ArrayList<>(external_rate_limiters_cow == null ? 1 : external_rate_limiters_cow.size() + 1);

			if ( external_rate_limiters_cow != null ){

				new_limiters.addAll( external_rate_limiters_cow );
			}

			new_limiters.add( new Object[]{ group, Boolean.valueOf(upload)});

			external_rate_limiters_cow = new_limiters;

			transports = peer_transports_cow;

		}finally{

			peer_transports_mon.exit();
		}

		for (int i=0;i<transports.size();i++){

			transports.get(i).addRateLimiter( group, upload );
		}
	}

	@Override
	public void
	removeRateLimiter(
		LimitedRateGroup	group,
		boolean				upload )
	{
		List<PEPeerTransport>	transports;

		try{
			peer_transports_mon.enter();

			if ( external_rate_limiters_cow != null ){

				ArrayList	new_limiters = new ArrayList( external_rate_limiters_cow.size()-1);

				for (int i=0;i<external_rate_limiters_cow.size();i++){

					Object[]	entry = (Object[])external_rate_limiters_cow.get(i);

					if ( entry[0] != group ){

						new_limiters.add( entry );
					}
				}

				if ( new_limiters.size() == 0 ){

					external_rate_limiters_cow = null;

				}else{

					external_rate_limiters_cow = new_limiters;
				}
			}

			transports = peer_transports_cow;

		}finally{

			peer_transports_mon.exit();
		}

		for (int i=0;i<transports.size();i++){

			transports.get(i).removeRateLimiter( group, upload );
		}
	}

	@Override
	public int
	getUploadRateLimitBytesPerSecond()
	{
		return( adapter.getUploadRateLimitBytesPerSecond());
	}

	@Override
	public int
	getDownloadRateLimitBytesPerSecond()
	{
		return( adapter.getDownloadRateLimitBytesPerSecond());
	}

		//	the peer calls this method itself in closeConnection() to notify this manager

	@Override
	public void
	peerConnectionClosed(
		PEPeerTransport 	peer,
		boolean 			connect_failed,
		boolean				network_failed )
	{
		boolean	connection_found = false;

		boolean tcpReconnect = false;
		boolean ipv6reconnect = false;

		try{
			peer_transports_mon.enter();

			int udpPort = peer.getUDPListenPort();

			boolean canTryUDP = UDPNetworkManager.UDP_OUTGOING_ENABLED && peer.getUDPListenPort() > 0;
			boolean canTryIpv6 = NetworkAdmin.getSingleton().hasIPV6Potential(true) && peer.getAlternativeIPv6() != null;

			if ( is_running ){

				PeerItem peer_item = peer.getPeerItemIdentity();
				PeerItem self_item = peer_database.getSelfPeer();


				if ( self_item == null || !self_item.equals( peer_item )){

					String	ip = peer.getIp();
					boolean wasIPv6;
					if ( peer.getNetwork() == AENetworkClassifier.AT_PUBLIC ){
						try
						{

							wasIPv6 = AddressUtils.getByName(ip) instanceof Inet6Address;
						} catch (UnknownHostException e)
						{
							wasIPv6 = false;
							// something is fishy about the old address, don't try to reconnect with v6
							canTryIpv6 = false;
						}
					}else{
						wasIPv6 	= false;
						canTryIpv6 	= false;
					}

					//System.out.println("netfail="+network_failed+", connfail="+connect_failed+", can6="+canTryIpv6+", was6="+wasIPv6);

					String	key = ip + ":" + udpPort;

					if ( peer.isTCP()){

						String net = AENetworkClassifier.categoriseAddress( ip );

						if ( connect_failed ){

								// TCP connect failure, try UDP later if necessary

							if ( canTryUDP && udp_fallback_for_failed_connection ){

								pending_nat_traversals.put(key, peer);
							} else if (canTryIpv6 && !wasIPv6)
							{
								tcpReconnect = true;
								ipv6reconnect = true;
							}
						}else if ( 	canTryUDP &&
									udp_fallback_for_dropped_connection &&
									network_failed &&
									seeding_mode &&
									peer.isInterested() &&
									!peer.isSeed() && !peer.isRelativeSeed() &&
									peer.getStats().getEstimatedSecondsToCompletion() > 60 &&
									FeatureAvailability.isUDPPeerReconnectEnabled()){

							if (Logger.isEnabled()){
								Logger.log(new LogEvent(peer, LOGID, LogEvent.LT_WARNING, "Unexpected stream closure detected, attempting recovery"));
							}

								// System.out.println( "Premature close of stream: " + getDisplayName() + "/" + peer.getIp());

							udp_reconnects.put( key, peer );

						}else if (	network_failed &&
									peer.isSafeForReconnect() &&
									!(seeding_mode && (peer.isSeed() || peer.isRelativeSeed() || peer.getStats().getEstimatedSecondsToCompletion() < 60)) &&
									getMaxConnections( net ) > 0 &&
									( getMaxNewConnectionsAllowed( net ) < 0 || getMaxNewConnectionsAllowed( net ) > getMaxConnections( net ) / 3  )&&
									FeatureAvailability.isGeneralPeerReconnectEnabled()){

							tcpReconnect = true;
						}
					}else if ( connect_failed ){

							// UDP connect failure

						if ( udp_fallback_for_failed_connection ){

							if ( peer.getData(PEER_NAT_TRAVERSE_DONE_KEY) == null){

								// System.out.println( "Direct reconnect failed, attempting NAT traversal" );

								pending_nat_traversals.put(key, peer);
							}
						}
					}
				}
			}

			if( peer_transports_cow.contains( peer )) {
				final ArrayList new_peer_transports = new ArrayList( peer_transports_cow );
				new_peer_transports.remove(peer);
				peer_transports_cow = new_peer_transports;
				connection_found  = true;
			}
		}
		finally{
			peer_transports_mon.exit();
		}

		if ( connection_found ){
			if( peer.getPeerState() != PEPeer.DISCONNECTED ) {
				System.out.println( "peer.getPeerState() != PEPeer.DISCONNECTED: " +peer.getPeerState() );
			}

			peerRemoved( peer );  //notify listeners
		}

		if(tcpReconnect)
			peer.reconnect(false, ipv6reconnect);
	}




	@Override
	public void
	peerAdded(
			PEPeer pc)
	{
		adapter.addPeer(pc);  //async downloadmanager notification

		//sync peermanager notification
		final ArrayList peer_manager_listeners = peer_manager_listeners_cow;

		for( int i=0; i < peer_manager_listeners.size(); i++ ) {
			((PEPeerManagerListener)peer_manager_listeners.get(i)).peerAdded( this, pc );
		}
	}


	@Override
	public void
	peerRemoved(
			PEPeer pc)
	{
		if ( 	is_running &&
				!seeding_mode &&
				( prefer_udp || prefer_udp_default )){

			int udp = pc.getUDPListenPort();

			if ( udp != 0 && udp == pc.getTCPListenPort()){

				BloomFilter filter = prefer_udp_bloom;

				if ( filter == null ){

					filter = prefer_udp_bloom = BloomFilterFactory.createAddOnly( PREFER_UDP_BLOOM_SIZE );
				}

				if ( filter.getEntryCount() < PREFER_UDP_BLOOM_SIZE / 10 ){

					filter.add( pc.getIp().getBytes());
				}
			}
		}

		final int piece = pc.getUniqueAnnounce();
		if(piece != -1 && superSeedMode ) {
			superSeedModeNumberOfAnnounces--;
			superSeedPieces[piece].peerLeft();
		}

		int[]	reserved_pieces = pc.getReservedPieceNumbers();

		if ( reserved_pieces != null ){

			for ( int reserved_piece: reserved_pieces ){

				PEPiece	pe_piece = pePieces[reserved_piece];

				if ( pe_piece != null ){

					String	reserved_by = pe_piece.getReservedBy();

					if ( reserved_by != null && reserved_by.equals( pc.getIp())){

						pe_piece.setReservedBy( null );
					}
				}
			}
		}

		if ( pc.isSeed()){

			last_seed_disconnect_time = SystemTime.getCurrentTime();
		}

		adapter.removePeer(pc);  //async downloadmanager notification

		//sync peermanager notification
		final ArrayList peer_manager_listeners = peer_manager_listeners_cow;

		for( int i=0; i < peer_manager_listeners.size(); i++ ) {
			((PEPeerManagerListener)peer_manager_listeners.get(i)).peerRemoved( this, pc );
		}
	}

	/** Don't pass a null to this method. All activations of pieces must go through here.
	 * @param piece PEPiece invoked; notifications of it's invocation need to be done
	 * @param pieceNumber of the PEPiece
	 */
	@Override
	public void addPiece(final PEPiece piece, final int pieceNumber, PEPeer for_peer )
	{
		addPiece( piece, pieceNumber, false, for_peer );
	}

	protected void addPiece(final PEPiece piece, final int pieceNumber, final boolean force_add, PEPeer for_peer )
	{
		if ( piece == null || pePieces[pieceNumber] != null ){
			Debug.out( "piece state inconsistent" );
		}
		pePieces[pieceNumber] =(PEPieceImpl)piece;
		nbPiecesActive++;
		if ( is_running || force_add ){
			// deal with possible piece addition by scheduler loop after closdown started
			adapter.addPiece(piece);
		}

		final ArrayList peer_manager_listeners = peer_manager_listeners_cow;

		for( int i=0; i < peer_manager_listeners.size(); i++ ) {
			try{
				((PEPeerManagerListener)peer_manager_listeners.get(i)).pieceAdded(this, piece, for_peer );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	/** Sends messages to listeners that the piece is no longer active.  All closing
	 * out (deactivation) of pieces must go through here. The piece will be null upon return.
	 * @param pePiece PEPiece to remove
	 * @param pieceNumber int
	 */
	public void removePiece(PEPiece pePiece, int pieceNumber) {
		if ( pePiece != null ){
			adapter.removePiece(pePiece);
		} else {
			pePiece = pePieces[pieceNumber];
		}

			// only decrement num-active if this piece was active (see comment below as to why this might no be the case)

		if ( pePieces[pieceNumber] != null ){
			pePieces[pieceNumber] = null;
			nbPiecesActive--;
		}

		if ( pePiece == null ){
			// we can get here without the piece actually being active when we have a very slow peer that is sent a request for the last
			// block of a piece, doesn't reply, the request gets cancelled, (and piece marked as inactive) and then it sends the block
			// and our 'recover block as useful' logic kicks in, writes the block, completes the piece, triggers a piece check and here we are

			return;
		}

		final ArrayList peer_manager_listeners = peer_manager_listeners_cow;

		for( int i=0; i < peer_manager_listeners.size(); i++ ) {
			try{
				((PEPeerManagerListener)peer_manager_listeners.get(i)).pieceRemoved(this, pePiece );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	@Override
	public int getNbActivePieces()
	{
		return nbPiecesActive;
	}

	@Override
	public String getElapsedTime() {
		return TimeFormatter.format((SystemTime.getCurrentTime() - _timeStarted) / 1000);
	}

//	Returns time started in ms
	@Override
	public long getTimeStarted(boolean mono) {
		return mono?_timeStarted_mono:_timeStarted;
	}

	@Override
	public long getTimeStartedSeeding(boolean mono) {
		return mono?_timeStartedSeeding_mono:_timeStartedSeeding;
	}

	private byte[] computeMd5Hash(DirectByteBuffer buffer)
	{
		BrokenMd5Hasher md5 	= new BrokenMd5Hasher();

		md5.reset();
		final int position = buffer.position(DirectByteBuffer.SS_DW);
		md5.update(buffer.getBuffer(DirectByteBuffer.SS_DW));
		buffer.position(DirectByteBuffer.SS_DW, position);
		ByteBuffer md5Result	= ByteBuffer.allocate(16);
		md5Result.position(0);
		md5.finalDigest( md5Result );

		final byte[] result =new byte[16];
		md5Result.position(0);
		for (int i =0; i <result.length; i++ )
		{
			result[i] = md5Result.get();
		}

		return result;
	}

	private void MD5CheckPiece(PEPiece piece, boolean correct)
	{
		final String[] writers =piece.getWriters();
		int offset = 0;
		for (int i =0; i <writers.length; i++ )
		{
			final int length =piece.getBlockSize(i);
			final String peer =writers[i];
			if (peer !=null)
			{
				DirectByteBuffer buffer =disk_mgr.readBlock(piece.getPieceNumber(), offset, length);

				if (buffer !=null)
				{
					final byte[] hash =computeMd5Hash(buffer);
					buffer.returnToPool();
					buffer = null;
					piece.addWrite(i,peer,hash,correct);
				}
			}
			offset += length;
		}
	}

	@Override
	public void checkCompleted(DiskManagerCheckRequest request, boolean passed)
	{
		if ( TEST_PERIODIC_SEEDING_SCAN_FAIL_HANDLING && ((Integer) request.getUserData()).intValue() == CHECK_REASON_SEEDING_CHECK ){

			passed = false;
		}

		try
		{
			piece_check_result_list_mon.enter();

			piece_check_result_list.add(new Object[]{request, new Integer( passed?1:0 )});
		} finally
		{
			piece_check_result_list_mon.exit();
		}
	}

	@Override
	public void checkCancelled(DiskManagerCheckRequest request)
	{
		try
		{
			piece_check_result_list_mon.enter();

			piece_check_result_list.add(new Object[]{request, new Integer( 2 )});

		} finally
		{
			piece_check_result_list_mon.exit();
		}
	}

	@Override
	public void checkFailed(DiskManagerCheckRequest request, Throwable cause)
	{
		try
		{
			piece_check_result_list_mon.enter();

			piece_check_result_list.add(new Object[]{request, new Integer( 0 )});

		} finally
		{
			piece_check_result_list_mon.exit();
		}
	}

	@Override
	public boolean needsMD5CheckOnCompletion(int pieceNumber)
	{
		final PEPieceImpl piece = pePieces[pieceNumber];
		if(piece == null)
			return false;
		return piece.getPieceWrites().size() > 0;
	}

	private void processPieceCheckResult(DiskManagerCheckRequest request, int outcome)
	{
		final int check_type =((Integer) request.getUserData()).intValue();

		try{

			final int pieceNumber =request.getPieceNumber();

			// System.out.println( "processPieceCheckResult(" + _finished + "/" + recheck_on_completion + "):" + pieceNumber +
			// "/" + piece + " - " + result );

			// passed = 1, failed = 0, cancelled = 2

			if (check_type ==CHECK_REASON_COMPLETE){
				// this is a recheck, so don't send HAVE msgs

				if ( outcome == 0 ){

					// piece failed; restart the download afresh
					Debug.out(getDisplayName() + ": Piece #" +pieceNumber +" failed final re-check. Re-downloading...");

					if (!restart_initiated){

						restart_initiated	= true;
						adapter.restartDownload(true);
					}
				}

				return;

			}else if ( check_type == CHECK_REASON_SEEDING_CHECK || check_type == CHECK_REASON_BAD_PIECE_CHECK ){

				if ( outcome == 0 ){

					if ( check_type == CHECK_REASON_SEEDING_CHECK ){

					Debug.out(getDisplayName() + "Piece #" +pieceNumber +" failed recheck while seeding. Re-downloading...");

					}else{

						Debug.out(getDisplayName() + "Piece #" +pieceNumber +" failed recheck after being reported as bad. Re-downloading...");
					}

					Logger.log(new LogAlert(this, LogAlert.REPEATABLE, LogAlert.AT_ERROR,
							"Download '" + getDisplayName() + "': piece " + pieceNumber
									+ " has been corrupted, re-downloading"));

					if ( !restart_initiated ){

						restart_initiated = true;

						adapter.restartDownload(true);
					}
				}

				return;
			}

			// piece can be null when running a recheck on completion
				// actually, give the above code I don't think this is true anymore...

			final PEPieceImpl pePiece =pePieces[pieceNumber];

			if ( outcome == 1 || is_metadata_download ){

			//  the piece has been written correctly

				try{
					if (pePiece !=null){

						if (needsMD5CheckOnCompletion(pieceNumber)){
						MD5CheckPiece(pePiece, true);
						}

						final List list =pePiece.getPieceWrites();

						if (list.size() >0 ){

							//For each Block
							for (int i =0; i <pePiece.getNbBlocks(); i++ ){

								//System.out.println("Processing block " + i);
								//Find out the correct hash
								final List listPerBlock =pePiece.getPieceWrites(i);
								byte[] correctHash = null;
								//PEPeer correctSender = null;
								Iterator iterPerBlock = listPerBlock.iterator();
								while (iterPerBlock.hasNext())
								{
									final PEPieceWriteImpl write =(PEPieceWriteImpl) iterPerBlock.next();
									if (write.isCorrect())
									{
										correctHash = write.getHash();
										//correctSender = write.getSender();
									}
								}
								//System.out.println("Correct Hash " + correctHash);
								//If it's found
								if (correctHash !=null)
								{
									iterPerBlock = listPerBlock.iterator();
									while (iterPerBlock.hasNext())
									{
										final PEPieceWriteImpl write =(PEPieceWriteImpl) iterPerBlock.next();
										if (!Arrays.equals(write.getHash(), correctHash))
										{
											//Bad peer found here
											badPeerDetected(write.getSender(),pieceNumber);
										}
									}
								}
							}
						}
					}
				}finally{
						// regardless of any possible failure above, tidy up correctly

				removePiece(pePiece, pieceNumber);

				// send all clients a have message
				sendHave(pieceNumber);  //XXX: if Done isn't set yet, might refuse to send this piece
				}
			}else if ( outcome == 0 ){

				//    the piece is corrupt

				Iterator<PEPeerManagerListener> it = peer_manager_listeners_cow.iterator();

				while( it.hasNext()){

					try{
						it.next().pieceCorrupted( this, pieceNumber );

					}catch( Throwable e ){

						Debug.printStackTrace(e);
					}
				}

				if ( pePiece != null ){

					try{
					MD5CheckPiece(pePiece, false);

					final String[] writers =pePiece.getWriters();
					final List uniqueWriters =new ArrayList();
					final int[] writesPerWriter =new int[writers.length];
					for (int i =0; i <writers.length; i++ )
					{
						final String writer =writers[i];
						if (writer !=null)
						{
							int writerId = uniqueWriters.indexOf(writer);
							if (writerId ==-1)
							{
								uniqueWriters.add(writer);
								writerId = uniqueWriters.size() - 1;
							}
							writesPerWriter[writerId]++;
						}
					}
					final int nbWriters =uniqueWriters.size();
					if (nbWriters ==1)
					{
						//Very simple case, only 1 peer contributed for that piece,
						//so, let's mark it as a bad peer

							String	bad_ip = (String)uniqueWriters.get(0);

							PEPeerTransport bad_peer = getTransportFromAddress( bad_ip );

							if ( bad_peer != null ){

								bad_peer.sendBadPiece( pieceNumber );
							}

							badPeerDetected( bad_ip, pieceNumber);

						//and let's reset the whole piece
						pePiece.reset();

						}else if ( nbWriters > 1 ){

						int maxWrites = 0;
						String bestWriter =null;

							PEPeerTransport	bestWriter_transport = null;

							for (int i =0; i <uniqueWriters.size(); i++ ){

							final int writes =writesPerWriter[i];

								if (writes > maxWrites ){

								final String writer =(String) uniqueWriters.get(i);

									PEPeerTransport pt =getTransportFromAddress(writer);

									if (	pt !=null &&
											pt.getReservedPieceNumbers() == null &&
											!ip_filter.isInRange(writer, getDisplayName(),getTorrentHash())){

									bestWriter = writer;
									maxWrites = writes;

										bestWriter_transport	= pt;
								}
							}
						}

							if ( bestWriter !=null ){

								pePiece.setReservedBy(bestWriter);

								bestWriter_transport.addReservedPieceNumber(pePiece.getPieceNumber());

								pePiece.setRequestable();

								for (int i =0; i <pePiece.getNbBlocks(); i++ ){

								// If the block was contributed by someone else

									if (writers[i] == null ||!writers[i].equals( bestWriter )){

									pePiece.reDownloadBlock(i);
								}
							}
							}else{

							//In all cases, reset the piece
							pePiece.reset();
						}
						}else{

						//In all cases, reset the piece
						pePiece.reset();
					}


					//if we are in end-game mode, we need to re-add all the piece chunks
					//to the list of chunks needing to be downloaded
					piecePicker.addEndGameChunks(pePiece);
					_stats.hashFailed(pePiece.getLength());

					}catch( Throwable e ){

						Debug.printStackTrace(e);

							// anything craps out in the above code, ensure we tidy up

						pePiece.reset();
				}
				}else{

						// no active piece for some reason, clear down DM piece anyway
						// one reason for getting here is that blocks are being injected directly from another
						// download with the same file (well, turns out it isn't the same file if getting hash fails);

					// Debug.out(getDisplayName() + ": Piece #" +pieceNumber +" failed check and no active piece, resetting..." );

					dm_pieces[pieceNumber].reset();
				}
			}else{

				// cancelled, download stopped
			}
		}finally{

			if (check_type ==CHECK_REASON_SCAN ){
				rescan_piece_time	= SystemTime.getCurrentTime();
			}

			if (!seeding_mode ){
				checkFinished( false );
		}
	}
	}


	private void badPeerDetected(String ip,  int piece_number )
	{
		boolean hash_fail = piece_number >= 0;

			// note that peer can be NULL but things still work in the main

		PEPeerTransport peer = getTransportFromAddress(ip);

		if ( hash_fail && peer != null ){

			Iterator<PEPeerManagerListener> it = peer_manager_listeners_cow.iterator();

			while( it.hasNext()){

				try{
					it.next().peerSentBadData( this, peer, piece_number );

				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}
			}
		}
		// Debug.out("Bad Peer Detected: " + peerIP + " [" + peer.getClient() + "]");

		IpFilterManager filter_manager =IpFilterManagerFactory.getSingleton();

		//Ban fist to avoid a fast reco of the bad peer

		int nbWarnings = filter_manager.getBadIps().addWarningForIp(ip);

		boolean	disconnect_peer = false;

		// no need to reset the bad chunk count as the peer is going to be disconnected and
		// if it comes back it'll start afresh

		// warning limit only applies to hash-fails, discards cause immediate action

		if ( nbWarnings > WARNINGS_LIMIT ){

			if (COConfigurationManager.getBooleanParameter("Ip Filter Enable Banning")){

				// if a block-ban occurred, check other connections

				if (ip_filter.ban(ip, getDisplayName(), false )){

					checkForBannedConnections();
				}

				// Trace the ban
				if (Logger.isEnabled()){
					Logger.log(new LogEvent(peer, LOGID, LogEvent.LT_ERROR, ip +" : has been banned and won't be able "
						+"to connect until you restart"));
				}

				disconnect_peer = true;
			}
		}else if ( !hash_fail ){

				// for failures due to excessive discard we boot the peer anyway

			disconnect_peer	= true;

		}

		if ( disconnect_peer ){

			if ( peer != null ){

					final int ps =peer.getPeerState();

					// might have been through here very recently and already started closing
					// the peer (due to multiple bad blocks being found from same peer when checking piece)
					if (!(ps ==PEPeer.CLOSING ||ps ==PEPeer.DISCONNECTED))
					{
						//	Close connection
					closeAndRemovePeer(peer, "has sent too many " + (hash_fail?"bad pieces":"discarded blocks") + ", " +WARNINGS_LIMIT +" max.", true);
				}
			}
		}
	}

	@Override
	public PEPiece[] getPieces()
	{
		return pePieces;
	}

	@Override
	public PEPiece getPiece(int pieceNumber)
	{
		return pePieces[pieceNumber];
	}

	@Override
	public PEPeerStats
	createPeerStats(
			PEPeer	owner )
	{
		return( new PEPeerStatsImpl( owner ));
	}


	@Override
	public DiskManagerReadRequest
	createDiskManagerRequest(
			int pieceNumber,
			int offset,
			int length )
	{
		return( disk_mgr.createReadRequest( pieceNumber, offset, length ));
	}

	@Override
	public boolean
	requestExists(
			String			peer_ip,
			int				piece_number,
			int				offset,
			int				length )
	{
		List<PEPeerTransport> peer_transports = peer_transports_cow;

		DiskManagerReadRequest	request = null;

		for (int i=0; i < peer_transports.size(); i++) {

			PEPeerTransport conn = peer_transports.get( i );

			if ( conn.getIp().equals( peer_ip )){

				if ( request == null ){

					request = createDiskManagerRequest( piece_number, offset, length );
				}

				if ( conn.getRequestIndex( request ) != -1 ){

					return( true );
				}
			}
		}

		return( false );
	}

	@Override
	public boolean
	seedPieceRecheck()
	{
		if ( !( enable_seeding_piece_rechecks || isSeeding())){

			return( false );
		}

		int	max_reads 		= 0;
		int	max_reads_index = 0;

		if ( TEST_PERIODIC_SEEDING_SCAN_FAIL_HANDLING ){

			max_reads_index = (int)(Math.random()*dm_pieces.length);
			max_reads = dm_pieces[ max_reads_index ].getNbBlocks()*3;

		}else{

			for (int i=0;i<dm_pieces.length;i++){

					// skip dnd pieces

				DiskManagerPiece	dm_piece = dm_pieces[i];

				if ( !dm_piece.isDone()){

					continue;
				}

				int	num = dm_piece.getReadCount()&0xffff;

				if ( num > SEED_CHECK_WAIT_MARKER ){

					// recently been checked, skip for a while

						num--;

					if ( num == SEED_CHECK_WAIT_MARKER ){

						num = 0;
					}

					dm_piece.setReadCount((short)num);

				}else{

					if ( num > max_reads ){

						max_reads 		= num;
						max_reads_index = i;
					}
				}
			}
		}

		if ( max_reads > 0 ){

			DiskManagerPiece	dm_piece = dm_pieces[ max_reads_index ];

			// if the piece has been read 3 times (well, assuming each block is read once,
			// which is obviously wrong, but...)

			if ( max_reads >= dm_piece.getNbBlocks() * 3 ){

				DiskManagerCheckRequest	req = disk_mgr.createCheckRequest( max_reads_index, new Integer( CHECK_REASON_SEEDING_CHECK ) );

				req.setAdHoc( true );

				req.setLowPriority( true );

				if (Logger.isEnabled())
					Logger.log(new LogEvent(disk_mgr.getTorrent(), LOGID,
							"Rechecking piece " + max_reads_index + " while seeding as most active"));

				disk_mgr.enqueueCheckRequest( req, this );

				dm_piece.setReadCount((short)65535);

				// clear out existing, non delayed pieces so we start counting piece activity
				// again

				for (int i=0;i<dm_pieces.length;i++){

					if ( i != max_reads_index ){

						int	num = dm_pieces[i].getReadCount()&0xffff;

						if ( num < SEED_CHECK_WAIT_MARKER ){

							dm_pieces[i].setReadCount((short)0 );
						}
					}
				}

				return( true );
			}
		}

		return( false );
	}

	@Override
	public void
	addListener(
			PEPeerManagerListener	l )
	{
		try{
			this_mon.enter();

			//copy on write
			final ArrayList peer_manager_listeners = new ArrayList( peer_manager_listeners_cow.size() + 1 );
			peer_manager_listeners.addAll( peer_manager_listeners_cow );
			peer_manager_listeners.add( l );
			peer_manager_listeners_cow = peer_manager_listeners;

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	removeListener(
			PEPeerManagerListener	l )
	{
		try{
			this_mon.enter();

			//copy on write
			final ArrayList peer_manager_listeners = new ArrayList( peer_manager_listeners_cow );
			peer_manager_listeners.remove( l );
			peer_manager_listeners_cow = peer_manager_listeners;

		}finally{

			this_mon.exit();
		}
	}

	private void
	checkForBannedConnections()
	{
		if ( ip_filter.isEnabled()){  //if ipfiltering is enabled, remove any existing filtered connections
			List<PEPeerTransport> to_close = null;

			final List<PEPeerTransport>	peer_transports = peer_transports_cow;

			String name 	= getDisplayName();
			byte[]	hash	= getTorrentHash();

			for (int i=0; i < peer_transports.size(); i++) {
				final PEPeerTransport conn = peer_transports.get( i );

				if ( ip_filter.isInRange( conn.getIp(), name, hash )) {
					if( to_close == null )  to_close = new ArrayList();
					to_close.add( conn );
				}
			}

			if( to_close != null ) {
				for( int i=0; i < to_close.size(); i++ ) {
					closeAndRemovePeer( to_close.get(i), "IPFilter banned IP address", true );
				}
			}
		}
	}

	@Override
	public boolean
	isSeeding()
	{
		return( seeding_mode );
	}

	@Override
	public boolean
	isMetadataDownload()
	{
		return( is_metadata_download );
	}

	@Override
	public int
	getTorrentInfoDictSize()
	{
		return( metadata_infodict_size );
	}

	@Override
	public void
	setTorrentInfoDictSize(
		int		size )
	{
		metadata_infodict_size	= size;
	}

	@Override
	public boolean isInEndGameMode() {
		return piecePicker.isInEndGameMode();
	}

	@Override
	public boolean isSuperSeedMode() {
		return superSeedMode;
	}

	@Override
	public boolean
	canToggleSuperSeedMode()
	{
		if ( superSeedMode ){

			return( true );
		}

		return( superSeedPieces == null && this.getRemaining() ==0  );
	}

	@Override
	public void
	setSuperSeedMode(
			boolean _superSeedMode)
	{
		if ( _superSeedMode == superSeedMode ){

			return;
		}

		boolean	kick_peers = false;

		if ( _superSeedMode ){

			if ( superSeedPieces == null && this.getRemaining() ==0  ){

				superSeedMode = true;

				initialiseSuperSeedMode();

				kick_peers	= true;
			}
		}else{

			superSeedMode = false;

			kick_peers	= true;
		}

		if ( kick_peers ){

			// turning on/off super-seeding, gotta kick all connected peers so they get the
			// "right" bitfield

			List<PEPeerTransport> peer_transports = peer_transports_cow;

			for (int i=0; i < peer_transports.size(); i++) {

				PEPeerTransport conn = peer_transports.get( i );

				closeAndRemovePeer( conn, "Turning on super-seeding", false );
			}
		}
	}

	private void
	initialiseSuperSeedMode()
	{
		superSeedPieces = new SuperSeedPiece[_nbPieces];
		for(int i = 0 ; i < _nbPieces ; i++) {
			superSeedPieces[i] = new SuperSeedPiece(this,i);
		}
	}

	private void updatePeersInSuperSeedMode() {
		if(!superSeedMode) {
			return;
		}

		//Refresh the update time in case this is needed
		for(int i = 0 ; i < superSeedPieces.length ; i++) {
			superSeedPieces[i].updateTime();
		}

		//Use the same number of announces than unchoke
		int nbUnchoke = adapter.getMaxUploads();
		if(superSeedModeNumberOfAnnounces >= 2 * nbUnchoke)
			return;


		//Find an available Peer
		PEPeerTransport selectedPeer = null;
		List<SuperSeedPeer> sortedPeers = null;

		final List<PEPeerTransport>	peer_transports = peer_transports_cow;

		sortedPeers = new ArrayList<>(peer_transports.size());
		Iterator<PEPeerTransport> iter1 = peer_transports.iterator();
		while(iter1.hasNext()) {
			sortedPeers.add(new SuperSeedPeer(iter1.next()));
		}

		Collections.sort(sortedPeers);
		Iterator<SuperSeedPeer> iter2 = sortedPeers.iterator();
		while(iter2.hasNext()) {
			final PEPeerTransport peer = ((SuperSeedPeer)iter2.next()).peer;
			if((peer.getUniqueAnnounce() == -1) && (peer.getPeerState() == PEPeer.TRANSFERING)) {
				selectedPeer = peer;
				break;
			}
		}

		if(selectedPeer == null ||selectedPeer.getPeerState() >=PEPeer.CLOSING)
			return;

		if(selectedPeer.getUploadHint() == 0) {
			//Set to infinite
			selectedPeer.setUploadHint(Constants.CRAPPY_INFINITY_AS_INT);
		}

		//Find a piece
		boolean found = false;
		SuperSeedPiece piece = null;
		boolean loopdone = false;  // add loop status

		while(!found) {
			piece = superSeedPieces[superSeedModeCurrentPiece];
			if(piece.getLevel() > 0) {
				piece = null;
				superSeedModeCurrentPiece++;
				if(superSeedModeCurrentPiece >= _nbPieces) {
					superSeedModeCurrentPiece = 0;

					if (loopdone) {  // if already been here, has been full loop through pieces, quit
						//quit superseed mode
						superSeedMode = false;
						closeAndRemoveAllPeers( "quiting SuperSeed mode", true );
						return;
					}
					else {
						// loopdone==false --> first time here --> go through the pieces
						// for a second time to check if reserved pieces have got freed due to peers leaving
						loopdone = true;
					}
				}
			} else {
				found = true;
			}
		}

		if(piece == null) {
			return;
		}

		//If this peer already has this piece, return (shouldn't happen)
		if(selectedPeer.isPieceAvailable(piece.getPieceNumber())) {
			return;
		}

		selectedPeer.setUniqueAnnounce(piece.getPieceNumber());
		superSeedModeNumberOfAnnounces++;
		piece.pieceRevealedToPeer();
		selectedPeer.sendHave(piece.getPieceNumber());
	}

	@Override
	public void updateSuperSeedPiece(PEPeer peer, int pieceNumber) {
		// currently this gets only called from bitfield scan function in PEPeerTransportProtocol
		if (!superSeedMode)
			return;
		superSeedPieces[pieceNumber].peerHasPiece(null);
		if(peer.getUniqueAnnounce() == pieceNumber)
		{
			peer.setUniqueAnnounce(-1);
			superSeedModeNumberOfAnnounces--;
		}
	}

	@Override
	public boolean
	isPrivateTorrent()
	{
		return( is_private_torrent );
	}

	@Override
	public int
	getExtendedMessagingMode()
	{
		return( adapter.getExtendedMessagingMode());
	}

	@Override
	public boolean
	isPeerExchangeEnabled()
	{
		return( adapter.isPeerExchangeEnabled());
	}

	@Override
	public LimitedRateGroup getUploadLimitedRateGroup() {  return upload_limited_rate_group;  }

	@Override
	public LimitedRateGroup getDownloadLimitedRateGroup() {  return download_limited_rate_group;  }


	/** To retreive arbitrary objects against this object. */
	@Override
	public Object
	getData(
			String key)
	{
		try{
			this_mon.enter();

			if (user_data == null) return null;

			return user_data.get(key);

		}finally{

			this_mon.exit();
		}
	}

	/** To store arbitrary objects against a control. */

	@Override
	public void
	setData(
			String key,
			Object value)
	{
		try{
			this_mon.enter();

			if (user_data == null) {
				user_data = new HashMap();
			}
			if (value == null) {
				if (user_data.containsKey(key))
					user_data.remove(key);
			} else {
				user_data.put(key, value);
			}
		}finally{
			this_mon.exit();
		}
	}

	@Override
	public int
	getConnectTimeout(
		int		ct_def )
	{
		if ( ct_def <= 0 ){

			return( ct_def );
		}

		if ( seeding_mode ){

				// seeding mode connections are already de-prioritised so nothing to do

			return( ct_def );
		}

		int max_sim_con = TCPConnectionManager.MAX_SIMULTANIOUS_CONNECT_ATTEMPTS;

			// high, let's not mess with things

		if ( max_sim_con >= 50 ){

			return( ct_def );
		}

			// we have somewhat limited outbound connection limits, see if it makes sense to
			// reduce the connect timeout to prevent connection stall due to a bunch getting
			// stuck 'connecting' for a long time and stalling us

		int	connected			= _seeds + _peers;
		int	connecting			= _tcpConnectingConnections;
		int queued				= _tcpPendingConnections;

		int	not_yet_connected 	= peer_database.getDiscoveredPeerCount();

		int	max = getMaxConnections( "" );

		int	potential = connecting + queued + not_yet_connected;

		/*
		System.out.println(
				"connected=" + connected +
				", queued=" + queued +
				", connecting=" + connecting +
				", queued=" + queued +
				", not_yet=" + not_yet_connected +
				", max=" + max );
		*/

			// not many peers -> don't amend

		int	lower_limit = max/4;

		if ( potential <= lower_limit || max == lower_limit ){

			return( ct_def );
		}

			// if we got lots of potential, use minimum delay

		final int MIN_CT = 7500;

		if ( potential >= max ){

			return( MIN_CT );
		}

			// scale between MIN and ct_def

		int pos 	= potential - lower_limit;
		int	scale 	= max - lower_limit;

		int res =  MIN_CT + ( ct_def - MIN_CT )*(scale - pos )/scale;

		// System.out.println( "scaled->" + res );

		return( res );
	}

	private void doConnectionChecks()
	{
		// if mixed networks then we have potentially two connections limits
		// 1) general peer one  - e.g. 100
		// 2) general+reserved slots for non-public net  - e.g. 103
		// so we get to schedule 3 'extra' non-public connections

		//every 1 second
		if ( mainloop_loop_count % MAINLOOP_ONE_SECOND_INTERVAL == 0 ){
			
				// need to sync the rates periodically as when upload is disabled (for example) the we can end up with
				// nothing requesting the rate in order for a change to be noticed
			
			upload_limited_rate_group.getRateLimitBytesPerSecond();
			download_limited_rate_group.getRateLimitBytesPerSecond();
			
			final List<PEPeerTransport> peer_transports = peer_transports_cow;

			int num_waiting_establishments = 0;

			int udp_connections = 0;

			for( int i=0; i < peer_transports.size(); i++ ) {
				final PEPeerTransport transport = peer_transports.get( i );

				//update waiting count
				final int state = transport.getConnectionState();
				if( state == PEPeerTransport.CONNECTION_PENDING || state == PEPeerTransport.CONNECTION_CONNECTING ) {
					num_waiting_establishments++;
				}

				if ( !transport.isTCP()){

					udp_connections++;
				}
			}

			int[]	allowed_seeds_info 	= getMaxSeedConnections();

			int		base_allowed_seeds = allowed_seeds_info[0];

				// base_allowed_seeds == 0 -> no limit

			if ( base_allowed_seeds > 0 ){

				int	extra_seeds = allowed_seeds_info[1];

				int	to_disconnect = _seeds - base_allowed_seeds;

				if ( to_disconnect > 0 ){

					// seeds are limited by people trying to get a reasonable upload by connecting
					// to leechers where possible. disconnect seeds from end of list to prevent
					// cycling of seeds

					Set<PEPeerTransport> to_retain = new HashSet<>();

					if ( extra_seeds > 0 ){

							// we can have up to extra_seeds additional non-public seeds on top of base

						for ( PEPeerTransport transport: peer_transports ){

							if ( transport.isSeed() && transport.getNetwork() != AENetworkClassifier.AT_PUBLIC ){

								to_retain.add( transport );

								if ( to_retain.size() == extra_seeds ){

									break;
								}
							}
						}

						to_disconnect -= to_retain.size();
					}

					for( int i=peer_transports.size()-1; i >= 0 && to_disconnect > 0; i-- ){

						final PEPeerTransport transport = peer_transports.get( i );

						if ( transport.isSeed()){

							if ( !to_retain.contains( transport )){

								closeAndRemovePeer( transport, "Too many seeds", false );

								to_disconnect--;
							}
						}
					}
				}
			}

			int[] allowed_info = getMaxNewConnectionsAllowed();

			int	allowed_base = allowed_info[0];

				// allowed_base < 0 -> unlimited

			if ( allowed_base < 0 || allowed_base > 1000 ){

				allowed_base = 1000;  //ensure a very upper limit so it doesnt get out of control when using PEX

				allowed_info[0] = allowed_base;
			}

			if( adapter.isNATHealthy()){  //if unfirewalled, leave slots avail for remote connections

				int free = getMaxConnections()[0] / 20;  //leave 5%

				allowed_base = allowed_base - free;

				allowed_info[0] = allowed_base;
			}

			for ( int i=0;i<allowed_info.length;i++){

				int	allowed = allowed_info[i];

				if ( allowed > 0 ){

					//try and connect only as many as necessary

					final int wanted = TCPConnectionManager.MAX_SIMULTANIOUS_CONNECT_ATTEMPTS - num_waiting_establishments;

					if( wanted > allowed ) {
						num_waiting_establishments += wanted - allowed;
					}

					int	remaining = allowed;

					int	tcp_remaining = TCPNetworkManager.getSingleton().getConnectDisconnectManager().getMaxOutboundPermitted();

					int	udp_remaining = UDPNetworkManager.getSingleton().getConnectionManager().getMaxOutboundPermitted();

					while( num_waiting_establishments < TCPConnectionManager.MAX_SIMULTANIOUS_CONNECT_ATTEMPTS && ( tcp_remaining > 0 || udp_remaining > 0 )){

						if( !is_running )  break;

						final PeerItem item = peer_database.getNextOptimisticConnectPeer( i == 1 );

						if( item == null || !is_running )  break;

						final PeerItem self = peer_database.getSelfPeer();
						if( self != null && self.equals( item ) ) {
							continue;
						}

						if( !isAlreadyConnected( item ) ) {
							final String source = PeerItem.convertSourceString( item.getSource() );

							final boolean use_crypto = item.getHandshakeType() == PeerItemFactory.HANDSHAKE_TYPE_CRYPTO;

							int	tcp_port = item.getTCPPort();
							int	udp_port = item.getUDPPort();

							if ( udp_port == 0 && udp_probe_enabled ){

									// for probing we assume udp port same as tcp

								udp_port = tcp_port;
							}

							boolean	prefer_udp_overall = prefer_udp || prefer_udp_default;

							if ( prefer_udp_overall && udp_port == 0 ){

									// see if we have previous record of this address as udp connectable

								byte[]	address = item.getIP().getBytes();

								BloomFilter	bloom = prefer_udp_bloom;

								if ( bloom != null && bloom.contains( address )){

									udp_port = tcp_port;
								}
							}

							boolean	tcp_ok = TCPNetworkManager.TCP_OUTGOING_ENABLED && tcp_port > 0 && tcp_remaining > 0;
							boolean udp_ok = UDPNetworkManager.UDP_OUTGOING_ENABLED && udp_port > 0 && udp_remaining > 0;

							if ( tcp_ok && !( prefer_udp_overall && udp_ok )){

								if ( makeNewOutgoingConnection( source, item.getAddressString(), tcp_port, udp_port, true, use_crypto, item.getCryptoLevel(), null) == null) {

									tcp_remaining--;

									num_waiting_establishments++;
									remaining--;
								}
							}else if ( udp_ok ){

								if ( makeNewOutgoingConnection( source, item.getAddressString(), tcp_port, udp_port, false, use_crypto, item.getCryptoLevel(), null) == null) {

									udp_remaining--;

									num_waiting_establishments++;

									remaining--;
								}
							}
						}
					}

					if ( i == 0 ){

						if ( 	UDPNetworkManager.UDP_OUTGOING_ENABLED &&
								remaining > 0 &&
								udp_remaining > 0 &&
								udp_connections < MAX_UDP_CONNECTIONS ){

							doUDPConnectionChecks( remaining );
						}
					}
				}
			}
		}

		//every 5 seconds
		if ( mainloop_loop_count % MAINLOOP_FIVE_SECOND_INTERVAL == 0 ) {
			final List<PEPeerTransport> peer_transports = peer_transports_cow;

			for( int i=0; i < peer_transports.size(); i++ ) {
				final PEPeerTransport transport = peer_transports.get( i );

				//check for timeouts
				if( transport.doTimeoutChecks() )  continue;

				//keep-alive check
				transport.doKeepAliveCheck();

				//speed tuning check
				transport.doPerformanceTuningCheck();
			}
		}

		// every 10 seconds check for connected + banned peers
		if ( mainloop_loop_count % MAINLOOP_TEN_SECOND_INTERVAL == 0 )
		{
			final long	last_update = ip_filter.getLastUpdateTime();
			if ( last_update != ip_filter_last_update_time )
			{
				ip_filter_last_update_time	= last_update;
				checkForBannedConnections();
			}
		}

		//every 30 seconds
		if ( mainloop_loop_count % MAINLOOP_THIRTY_SECOND_INTERVAL == 0 ) {
			//if we're at our connection limit, time out the least-useful
			//one so we can establish a possibly-better new connection
			optimisticDisconnectCount = 0;
			int[] allowed = getMaxNewConnectionsAllowed();
			if ( allowed[0] + allowed[1] == 0 ){  //we've reached limit
				doOptimisticDisconnect( false, false, "" );
			}
		}


		//sweep over all peers in a 60 second timespan
		float percentage = ((mainloop_loop_count % MAINLOOP_SIXTY_SECOND_INTERVAL) + 1F) / (1F *MAINLOOP_SIXTY_SECOND_INTERVAL);
		int goal;
		if(mainloop_loop_count % MAINLOOP_SIXTY_SECOND_INTERVAL == 0)
		{
			goal = 0;
			sweepList = peer_transports_cow;
		} else
			goal = (int)Math.floor(percentage * sweepList.size());

		for( int i=nextPEXSweepIndex; i < goal && i < sweepList.size(); i++) {
			//System.out.println(mainloop_loop_count+" %:"+percentage+" start:"+nextPEXSweepIndex+" current:"+i+" <"+goal+"/"+sweepList.size());
			final PEPeerTransport peer = sweepList.get( i );
			peer.updatePeerExchange();
		}

		nextPEXSweepIndex = goal;

			// kick duplicate outbound connections - some users experience increasing numbers of connections to the same IP address sitting there in a 'connecting' state
			// not sure of the exact cause unfortunately so adding this as a stop gap measure (parg, 2015/01/11)
			// I have a suspicion this is caused by an outbound connection failing (possibly with TCP+uTP dual connects) and not resulting in overall disconnect
			// as the user has a bunch of 'bind' exceptions in their log

		if ( mainloop_loop_count % MAINLOOP_SIXTY_SECOND_INTERVAL == 0 ){

			List<PEPeerTransport> peer_transports = peer_transports_cow;

			if ( peer_transports.size() > 1 ){

				Map<String,List<PEPeerTransport>>	peer_map = new HashMap<>();

				for ( PEPeerTransport peer: peer_transports ){

					if ( peer.isIncoming()){

						continue;
					}

					if ( 	peer.getPeerState() == PEPeer.CONNECTING &&
							peer.getConnectionState() == PEPeerTransport.CONNECTION_CONNECTING &&
							peer.getLastMessageSentTime() != 0 ){

						String key = peer.getIp() + ":" + peer.getPort();

						List<PEPeerTransport> list = peer_map.get( key );

						if ( list == null ){

							list = new ArrayList<>(1);

							peer_map.put( key, list );
						}

						list.add( peer );
					}
				}

				for ( List<PEPeerTransport> list: peer_map.values()){

					if ( list.size() >= 2 ){

						long			newest_time = Long.MIN_VALUE;
						PEPeerTransport	newest_peer	= null;

						for ( PEPeerTransport peer: list ){

							long	last_sent = peer.getLastMessageSentTime();

							if ( last_sent > newest_time ){

								newest_time = last_sent;
								newest_peer	= peer;
							}
						}

						for ( PEPeerTransport peer: list ){

							if ( peer != newest_peer ){

								if ( 	peer.getPeerState() == PEPeer.CONNECTING &&
										peer.getConnectionState() == PEPeerTransport.CONNECTION_CONNECTING ){

									closeAndRemovePeer( peer, "Removing old duplicate connection", false );
								}
							}
						}
					}
				}
			}
		}
	}

	private void
	doUDPConnectionChecks(
		int		number )
	{
		List<PEPeerTransport>	new_connections = null;

		try{
			peer_transports_mon.enter();

			long	now = SystemTime.getCurrentTime();

			if ( udp_reconnects.size() > 0 && now - last_udp_reconnect >= UDP_RECONNECT_MIN_MILLIS ){

				last_udp_reconnect = now;

				Iterator<PEPeerTransport> it = udp_reconnects.values().iterator();

				PEPeerTransport	peer = it.next();

				it.remove();

				if (Logger.isEnabled()){
					Logger.log(new LogEvent(this, LOGID, LogEvent.LT_INFORMATION, "Reconnecting to previous failed peer " + peer.getPeerItemIdentity().getAddressString()));
				}

				if ( new_connections == null ){

					new_connections = new ArrayList<>();
				}

				new_connections.add( peer );

				number--;

				if ( number <= 0 ){

					return;
				}
			}

			if ( pending_nat_traversals.size() == 0 ){

				return;
			}

			int max = MAX_UDP_TRAVERSAL_COUNT;

			// bigger the swarm, less chance of doing it

			if ( seeding_mode ){

				if ( _peers > 8 ){

					max = 0;

				}else{

					max = 1;
				}
			}else if ( _seeds > 8 ){

				max = 0;

			}else if ( _seeds > 4 ){

				max = 1;
			}

			int	avail = max - udp_traversal_count;

			int	to_do = Math.min( number, avail );

			Iterator<PEPeerTransport>	it = pending_nat_traversals.values().iterator();

			while( to_do > 0 && it.hasNext()){

				final PEPeerTransport	peer = it.next();

				it.remove();

				String peer_ip = peer.getPeerItemIdentity().getAddressString();

				if ( AENetworkClassifier.categoriseAddress( peer_ip ) != AENetworkClassifier.AT_PUBLIC ){

					continue;
				}

				to_do--;

				PeerNATTraverser.getSingleton().create(
						this,
						new InetSocketAddress( peer_ip, peer.getPeerItemIdentity().getUDPPort() ),
						new PeerNATTraversalAdapter()
						{
							private boolean	done;

							@Override
							public void
							success(
									InetSocketAddress	target )
							{
								complete();

								PEPeerTransport newTransport = peer.reconnect(true, false);

								if( newTransport != null ){

									newTransport.setData(PEER_NAT_TRAVERSE_DONE_KEY, "");
								}
							}

							@Override
							public void
							failed()
							{
								complete();
							}

							protected void
							complete()
							{
								try{
									peer_transports_mon.enter();

									if ( !done ){

										done = true;

										udp_traversal_count--;
									}
								}finally{

									peer_transports_mon.exit();
								}
							}
						});

				udp_traversal_count++;
			}
		}finally{

			peer_transports_mon.exit();

			if ( new_connections != null ){

				for (int i=0;i<new_connections.size();i++){

					PEPeerTransport	peer_item = new_connections.get(i);

						// don't call when holding monitor - deadlock potential
					peer_item.reconnect(true, false);

				}
			}
		}
	}

	// counter is reset every 30s by doConnectionChecks()
	private int optimisticDisconnectCount = 0;

	@Override
	public boolean
	doOptimisticDisconnect(
		boolean		pending_lan_local_peer,
		boolean 	force,
		String 		network )	// on behalf of a particular peer OR "" for general
	{
		// if it isn't for non-pub network then try to maintain the extra connection slots, if any, allocated
		// to non-pub

		final int	non_pub_extra;

		if ( network != AENetworkClassifier.AT_I2P ){

			int[] max_con = getMaxConnections();

			non_pub_extra = max_con[1];

		}else{

			non_pub_extra = 0;
		}

		final List<PEPeerTransport> peer_transports = peer_transports_cow;

		PEPeerTransport max_transport 			= null;
		PEPeerTransport max_seed_transport		= null;
		PEPeerTransport max_non_lan_transport 	= null;

		PEPeerTransport max_pub_transport 			= null;
		PEPeerTransport max_pub_seed_transport		= null;
		PEPeerTransport max_pub_non_lan_transport 	= null;

		long max_time = 0;
		long max_seed_time 		= 0;
		long max_non_lan_time	= 0;
		long max_pub_time		= 0;
		long max_pub_seed_time 		= 0;
		long max_pub_non_lan_time	= 0;

		int	non_pub_found = 0;

		List<Long> activeConnectionTimes = new ArrayList<>(peer_transports.size());

		int	lan_peer_count	= 0;

		for ( int i=0; i < peer_transports.size(); i++ ){

			final PEPeerTransport peer = peer_transports.get( i );

			if( peer.getConnectionState() == PEPeerTransport.CONNECTION_FULLY_ESTABLISHED ) {

				final long timeSinceConnection =peer.getTimeSinceConnectionEstablished();
				final long timeSinceSentData =peer.getTimeSinceLastDataMessageSent();

				activeConnectionTimes.add(timeSinceConnection);

				long peerTestTime = 0;
				if( seeding_mode){
					if( timeSinceSentData != -1 )
						peerTestTime = timeSinceSentData;  //ensure we've sent them at least one data message to qualify for drop
				}else{
					final long timeSinceGoodData =peer.getTimeSinceGoodDataReceived();


					if( timeSinceGoodData == -1 )
						peerTestTime +=timeSinceConnection;   //never received
					else
						peerTestTime +=timeSinceGoodData;

					// try to drop unInteresting in favor of Interesting connections
					if (!peer.isInteresting())
					{
						if (!peer.isInterested())   // if mutually unInterested, really try to drop the connection
							peerTestTime +=timeSinceConnection +timeSinceSentData;   // if we never sent, it will subtract 1, which is good
						else
							peerTestTime +=(timeSinceConnection -timeSinceSentData); // try to give interested peers a chance to get data

						peerTestTime *=2;
					}

					peerTestTime +=peer.getSnubbedTime();
				}

				if( !peer.isIncoming() ){
					peerTestTime = peerTestTime * 2;   //prefer to drop a local connection, to make room for more remotes
				}

				boolean	count_pubs = non_pub_extra > 0 && peer.getNetwork() == AENetworkClassifier.AT_PUBLIC;

				if ( peer.isLANLocal()){

					lan_peer_count++;

				}else{

					if ( peerTestTime > max_non_lan_time ){

						max_non_lan_time 		= peerTestTime;
						max_non_lan_transport 	= peer;
					}

					if ( count_pubs ){

						if ( peerTestTime > max_pub_non_lan_time ){

							max_pub_non_lan_time 		= peerTestTime;
							max_pub_non_lan_transport 	= peer;
						}
					}
				}

					// anti-leech checks

				if( !seeding_mode ) {

					// remove long-term snubbed peers with higher probability
					peerTestTime += peer.getSnubbedTime();
					if(peer.getSnubbedTime() > 2*60) {peerTestTime*=1.5;}

					PEPeerStats pestats = peer.getStats();
					// everybody has deserverd a chance of half an MB transferred data
					if(pestats.getTotalDataBytesReceived()+pestats.getTotalDataBytesSent() > 1024*512 ) {
						boolean goodPeer = true;

						// we don't like snubbed peers with a negative gain
						if( peer.isSnubbed() && pestats.getTotalDataBytesReceived() < pestats.getTotalDataBytesSent() ) {
							peerTestTime *= 1.5;
							goodPeer = false;
						}
						// we don't like peers with a very bad ratio (10:1)
						if( pestats.getTotalDataBytesSent() > pestats.getTotalDataBytesReceived() * 10 ) {
							peerTestTime *= 2;
							goodPeer = false;
						}
						// modify based on discarded : overall downloaded ratio
						if( pestats.getTotalDataBytesReceived() > 0 && pestats.getTotalBytesDiscarded() > 0 ) {
							peerTestTime = (long)(peerTestTime *( 1.0+((double)pestats.getTotalBytesDiscarded()/(double)pestats.getTotalDataBytesReceived())));
						}

						// prefer peers that do some work, let the churn happen with peers that did nothing
						if(goodPeer)
							peerTestTime *= 0.7;
					}
				}


				if ( peerTestTime > max_time ){

					max_time 		= peerTestTime;
					max_transport 	= peer;
				}

				if ( count_pubs ){

					if ( peerTestTime > max_pub_time ){

						max_pub_time 		= peerTestTime;
						max_pub_transport 	= peer;
					}
				}else{

					non_pub_found++;
				}

				if ( peer.isSeed() || peer.isRelativeSeed()){

					if ( peerTestTime > max_seed_time ){

						max_seed_time 		= peerTestTime;
						max_seed_transport 	= peer;
					}

					if ( count_pubs ){

						if ( peerTestTime > max_pub_seed_time ){

							max_pub_seed_time 		= peerTestTime;
							max_pub_seed_transport 	= peer;
						}
					}
				}
			}
		}

		if ( non_pub_extra > 0 ){

			if ( non_pub_found <= non_pub_extra ){

					// don't kick a non-pub peer

				if ( max_transport != null && max_transport.getNetwork() != AENetworkClassifier.AT_PUBLIC ){

					max_time		= max_pub_time;
					max_transport 	= max_pub_transport;
				}

				if ( max_seed_transport != null && max_seed_transport.getNetwork() != AENetworkClassifier.AT_PUBLIC ){

					max_seed_time		= max_pub_seed_time;
					max_seed_transport	= max_pub_seed_transport;
				}

				if ( max_non_lan_transport != null && max_non_lan_transport.getNetwork() != AENetworkClassifier.AT_PUBLIC ){

					max_non_lan_time		= max_pub_non_lan_time;
					max_non_lan_transport 	= max_pub_non_lan_transport;
				}
			}
		}

		long medianConnectionTime;

		if ( activeConnectionTimes.size() > 0 ){
			Collections.sort(activeConnectionTimes);
			medianConnectionTime = activeConnectionTimes.get(activeConnectionTimes.size()/2);
		}else{
			medianConnectionTime = 0;
		}

		int max_con = getMaxConnections( network );

		// allow 1 disconnect every 30s per 30 peers; 2 at least every 30s
		int maxOptimistics = max_con==0?8:Math.max(max_con/30,2);

		// avoid unnecessary churn, e.g.
		if(!pending_lan_local_peer && !force && optimisticDisconnectCount >= maxOptimistics && medianConnectionTime < 5*60*1000)
			return false;


		// don't boot lan peers if we can help it (unless we have a few of them)

		if ( max_transport != null ){

			final int LAN_PEER_MAX	= 4;

			if ( max_transport.isLANLocal() && lan_peer_count < LAN_PEER_MAX && max_non_lan_transport != null ){

				// override lan local max with non-lan local max

				max_transport	= max_non_lan_transport;
				max_time		= max_non_lan_time;
			}

			// if we have a seed limit, kick seeds in preference to non-seeds

			if( getMaxSeedConnections( network ) > 0 && max_seed_transport != null && max_time > 5*60*1000 ) {
				closeAndRemovePeer( max_seed_transport, "timed out by doOptimisticDisconnect()", true );
				optimisticDisconnectCount++;
				return true;
			}

			if( max_transport != null && max_time > 5 *60*1000 ) {  //ensure a 5 min minimum test time
				closeAndRemovePeer( max_transport, "timed out by doOptimisticDisconnect()", true );
				optimisticDisconnectCount++;
				return true;
			}

			// kick worst peers to accomodate lan peer

			if ( pending_lan_local_peer && lan_peer_count < LAN_PEER_MAX ){
				closeAndRemovePeer( max_transport, "making space for LAN peer in doOptimisticDisconnect()", true );
				optimisticDisconnectCount++;
				return true;
			}

			if ( force ){

				closeAndRemovePeer( max_transport, "force removal of worst peer in doOptimisticDisconnect()", true );

				return true;
			}
		}else if ( force ){

			if ( peer_transports.size() > 0 ){

				PEPeerTransport pt = peer_transports.get( new Random().nextInt( peer_transports.size()));

				closeAndRemovePeer( pt, "force removal of random peer in doOptimisticDisconnect()", true );

				return true;
			}
		}

		return false;
	}


	@Override
	public PeerExchangerItem createPeerExchangeConnection(final PEPeerTransport base_peer ) {
		if( base_peer.getTCPListenPort() > 0 ) {  //only accept peers whose remote port is known
			final PeerItem peer =
				PeerItemFactory.createPeerItem( base_peer.getIp(),
						base_peer.getTCPListenPort(),
						PeerItemFactory.PEER_SOURCE_PEER_EXCHANGE,
						base_peer.getPeerItemIdentity().getHandshakeType(),
						base_peer.getUDPListenPort(),
						PeerItemFactory.CRYPTO_LEVEL_1,
						0 );

			return peer_database.registerPeerConnection( peer, new PeerExchangerItem.Helper(){
				@Override
				public boolean
				isSeed()
				{
					return base_peer.isSeed();
				}
			});
		}

		return null;
	}



	private boolean isAlreadyConnected( PeerItem peer_id ) {
		final List<PEPeerTransport> peer_transports = peer_transports_cow;
		for( int i=0; i < peer_transports.size(); i++ ) {
			final PEPeerTransport peer = peer_transports.get( i );
			if( peer.getPeerItemIdentity().equals( peer_id ) )  return true;
		}
		return false;
	}


	@Override
	public void peerVerifiedAsSelf(PEPeerTransport self ) {
		if( self.getTCPListenPort() > 0 ) {  //only accept self if remote port is known
			final PeerItem peer = PeerItemFactory.createPeerItem( self.getIp(), self.getTCPListenPort(),
					PeerItem.convertSourceID( self.getPeerSource() ), self.getPeerItemIdentity().getHandshakeType(), self.getUDPListenPort(),PeerItemFactory.CRYPTO_LEVEL_CURRENT, 0 );
			peer_database.setSelfPeer( peer );
		}
	}

	@Override
	public void
	IPFilterEnabledChanged(
		boolean			is_enabled )
	{
		if ( is_enabled ){

			checkForBannedConnections();
		}
	}

	@Override
	public boolean
	canIPBeBanned(
			String ip )
	{
		return true;
	}

	@Override
	public boolean
	canIPBeBlocked(
		String	ip,
		byte[]	torrent_hash )
	{
		return true;
	}

	@Override
	public void IPBlockedListChanged(IpFilter filter) {
		Iterator<PEPeerTransport>	it = peer_transports_cow.iterator();

		String	name 	= getDisplayName();
		byte[]	hash	= getTorrentHash();

		while( it.hasNext()){
			try {
  			PEPeerTransport	peer = it.next();

  			if (filter.isInRange(peer.getIp(), name, hash )) {
  				peer.closeConnection( "IP address blocked by filters" );
  			}
			} catch (Exception e) {
			}
		}
	}

	@Override
	public void IPBanned(BannedIp ip)
	{
		for (int i =0; i <_nbPieces; i++ )
		{
			if (pePieces[i] !=null)
				pePieces[i].reDownloadBlocks(ip.getIp());
		}
	}

	@Override
	public long
	getHiddenBytes()
	{
		if ( hidden_piece < 0 ){

			return( 0 );
		}

		return( dm_pieces[hidden_piece].getLength());
	}

	@Override
	public int
	getHiddenPiece()
	{
		return( hidden_piece );
	}

	@Override
	public int
	getUploadPriority()
	{
		return( adapter.getUploadPriority());
	}

	@Override
	public int getAverageCompletionInThousandNotation()
	{
		ArrayList<PEPeerTransport> peer_transports = peer_transports_cow;

		final long total =disk_mgr.getTotalLength();

		final int my_completion = total == 0
		? 1000
				: (int) ((1000 * (total - disk_mgr.getRemainingExcludingDND())) / total);

		int sum = my_completion == 1000 ? 0 : my_completion;  //add in our own percentage if not seeding
		int num = my_completion == 1000 ? 0 : 1;

		for (int i =0; i <peer_transports.size(); i++ )
		{
			final PEPeer peer =peer_transports.get(i);

			if (peer.getPeerState() ==PEPeer.TRANSFERING &&!peer.isSeed())
			{
				num++;
				sum += peer.getPercentDoneInThousandNotation();
			}
		}

		return num > 0 ? sum / num : 0;
	}
	
	@Override
	public int getMaxCompletionInThousandNotation( boolean never_include_seeds )
	{
		ArrayList<PEPeerTransport> peer_transports = peer_transports_cow;
		
		int	max = 0;
		
			// generally if we're seeeding we shouldn't connect to seeds so ignore them

		boolean ignore_seeds = isSeeding() || never_include_seeds;
		
		for ( int i =0; i<peer_transports.size(); i++ ){
		
			final PEPeer peer = peer_transports.get(i);

			if ( peer.getPeerState() == PEPeer.TRANSFERING ){
							
				int done =  peer.getPercentDoneInThousandNotation();
				
				if ( done == 1000 && ignore_seeds ){
										
				}else{
					
					if ( done > max ){
					
						max = done;
					}
				}
			}
		}

		return max;
	}

	@Override
	public int[]
	getMaxConnections()
	{
		return( adapter.getMaxConnections());
	}

	private int
	getMaxConnections(
		String		net )
	{
		int[]	data = getMaxConnections();

		int	result = data[0];

			// 0 = unlimited

		if ( result > 0 ){

			if ( net != AENetworkClassifier.AT_PUBLIC ){

				result += data[1];
			}
		}

		return( result );
	}

	public int[]
	getMaxSeedConnections()
	{
		return( adapter.getMaxSeedConnections());
	}

	private int
	getMaxSeedConnections(
		String		net )
	{
		int[]	data = getMaxSeedConnections();

		int	result = data[0];

			// 0 = unlimited

		if ( result > 0 ){

			if ( net != AENetworkClassifier.AT_PUBLIC ){

				result += data[1];
			}
		}

		return( result );
	}

	/**
	 * returns the allowed connections for the given network
	 * -1 -> unlimited
	 */

	@Override
	public int
	getMaxNewConnectionsAllowed(
		String		network )
	{
		int[]	max_con = getMaxConnections();

		int	dl_max = max_con[0];

		if ( network != AENetworkClassifier.AT_PUBLIC ){

			dl_max += max_con[1];
		}

		int	allowed_peers = PeerUtils.numNewConnectionsAllowed(getPeerIdentityDataID(), dl_max );

		return( allowed_peers );
	}

	/**
	 * returns number of whatever peers to connect and then extra ones that must be non-pub if available
	 * @return -1 -> unlimited
	 */

	private int[]
	getMaxNewConnectionsAllowed()
	{
		int[]	max_con = getMaxConnections();

		int	dl_max 	= max_con[0];
		int	extra	= max_con[1];

		int	allowed_peers = PeerUtils.numNewConnectionsAllowed(getPeerIdentityDataID(), dl_max + extra );

			// allowed_peers == -1 -> unlimited

		if ( allowed_peers >= 0 ){

			allowed_peers -= extra;

			if ( allowed_peers < 0 ){

				extra += allowed_peers;

				if ( extra < 0 ){

					extra = 0;
				}

				allowed_peers = 0;
			}
		}

		return( new int[]{ allowed_peers, extra });
	}

	@Override
	public int getSchedulePriority() {
		return isSeeding() ? Integer.MAX_VALUE : adapter.getPosition();
	}

	@Override
	public boolean
	hasPotentialConnections()
	{
		return( pending_nat_traversals.size() + peer_database.getDiscoveredPeerCount() > 0 );
	}

	@Override
	public String
	getRelationText()
	{
		return( adapter.getLogRelation().getRelationText());
	}

	@Override
	public Object[]
	              getQueryableInterfaces()
	{
		return( adapter.getLogRelation().getQueryableInterfaces());
	}



	@Override
	public PEPeerTransport getTransportFromIdentity(byte[] peer_id ) {
		final List<PEPeerTransport>	peer_transports = peer_transports_cow;
		for( int i=0; i < peer_transports.size(); i++ ) {
			final PEPeerTransport conn = peer_transports.get( i );
			if( Arrays.equals( peer_id, conn.getId() ) )   return conn;
		}
		return null;
	}


	/* peer item is not reliable for general use
	public PEPeerTransport getTransportFromPeerItem(PeerItem peerItem)
	{
		ArrayList peer_transports =peer_transports_cow;
		for (int i =0; i <peer_transports.size(); i++)
		{
			PEPeerTransport pt =(PEPeerTransport) peer_transports.get(i);
			if (pt.getPeerItemIdentity().equals(peerItem))
				return pt;
		}
		return null;
	}
	 */

	@Override
	public PEPeerTransport getTransportFromAddress(String peer)
	{
		final List<PEPeerTransport> peer_transports =peer_transports_cow;
		for (int i =0; i <peer_transports.size(); i++)
		{
			final PEPeerTransport pt = peer_transports.get(i);
			if (peer.equals(pt.getIp()))
				return pt;
		}
		return null;
	}

	// Snubbed peers accounting
	@Override
	public void incNbPeersSnubbed()
	{
		nbPeersSnubbed++;
	}

	@Override
	public void decNbPeersSnubbed()
	{
		nbPeersSnubbed--;
	}

	@Override
	public void setNbPeersSnubbed(int n)
	{
		nbPeersSnubbed =n;
	}

	@Override
	public int getNbPeersSnubbed()
	{
		return nbPeersSnubbed;
	}

	@Override
	public boolean
	getPreferUDP()
	{
		return( prefer_udp );
	}

	@Override
	public void
	setPreferUDP(
		boolean	prefer )
	{
		 prefer_udp = prefer;
	}

	@Override
	public boolean
	isPeerSourceEnabled(
		String peer_source )
	{
		return( adapter.isPeerSourceEnabled( peer_source ));
	}

	@Override
	public boolean
	isNetworkEnabled(
		String net )
	{
		return( adapter.isNetworkEnabled( net ));
	}

	@Override
	public void
	peerDiscovered(
		PEPeerTransport		finder,
		PeerItem			pi )
	{
		final ArrayList peer_manager_listeners = peer_manager_listeners_cow;

		for( int i=0; i < peer_manager_listeners.size(); i++ ) {
			try{
				((PEPeerManagerListener)peer_manager_listeners.get(i)).peerDiscovered( this, pi, finder );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	@Override
	public TrackerPeerSource
	getTrackerPeerSource()
	{
		return(
			new TrackerPeerSourceAdapter()
			{
				@Override
				public int
				getType()
				{
					return( TP_PEX );
				}

				@Override
				public int
				getStatus()
				{
					return( isPeerExchangeEnabled()?ST_ONLINE:ST_DISABLED );
				}

				@Override
				public String
				getName()
				{
					return(
						MessageText.getString( "tps.pex.details",
							new String[]{
								String.valueOf( peer_transports_cow.size()),
								String.valueOf( peer_database.getExchangedPeerCount()),
								String.valueOf( peer_database.getDiscoveredPeerCount())}));
				}

				@Override
				public int
				getPeers()
				{
					return( isPeerExchangeEnabled()?peer_database.getExchangedPeersUsed():-1);
				}
			});
	}

	@Override
	public void
	generateEvidence(
			IndentWriter		writer )
	{
		writer.println( "PeerManager: seeding=" + seeding_mode );

		writer.println(
				"    udp_fb=" + pending_nat_traversals.size() +
				",udp_tc=" + udp_traversal_count +
				",pd=[" + peer_database.getString() + "]");

		String	pending_udp = "";

		try{
			peer_transports_mon.enter();

			Iterator<PEPeerTransport>	it = pending_nat_traversals.values().iterator();

			while( it.hasNext()){

				PEPeerTransport peer = it.next();

				pending_udp += (pending_udp.length()==0?"":",") + peer.getPeerItemIdentity().getAddressString() + ":" + peer.getPeerItemIdentity().getUDPPort();
			}
		}finally{

			peer_transports_mon.exit();
		}

		if ( pending_udp.length() > 0 ){

			writer.println( "    pending_udp=" + pending_udp );
		}

		List	traversals = PeerNATTraverser.getSingleton().getTraversals( this );

		String	active_udp = "";

		Iterator it1 = traversals.iterator();

		while( it1.hasNext()){

			InetSocketAddress ad = (InetSocketAddress)it1.next();

			active_udp += (active_udp.length()==0?"":",") + AddressUtils.getHostAddress( ad ) + ":" + ad.getPort();
		}

		if ( active_udp.length() > 0 ){

			writer.println( "    active_udp=" + active_udp );
		}

		if ( !seeding_mode ){

			writer.println( "  Active Pieces" );

			int	num_active = 0;

			try{
				writer.indent();

				String	str	= "";
				int		num	= 0;

				for (int i=0;i<pePieces.length;i++){

					PEPiece	piece = pePieces[i];

					if ( piece != null ){

						num_active++;

						str += (str.length()==0?"":",") + "#" + i + " " + dm_pieces[i].getString() + ": " + piece.getString();

						num++;

						if ( num == 20 ){

							writer.println( str );
							str = "";
							num	= 0;
						}
					}
				}

				if ( num > 0 ){
					writer.println(str);
				}

			}finally{

				writer.exdent();
			}

			if ( num_active == 0 ){

				writer.println( "  Inactive Pieces (excluding done/skipped)" );

				try{
					writer.indent();

					String	str	= "";
					int		num	= 0;

					for (int i=0;i<dm_pieces.length;i++){

						DiskManagerPiece	dm_piece = dm_pieces[i];

						if ( dm_piece.isInteresting()){

							str += (str.length()==0?"":",") + "#" + i + " " + dm_pieces[i].getString();

							num++;

							if ( num == 20 ){

								writer.println( str );
								str = "";
								num	= 0;
							}
						}
					}

					if ( num > 0 ){

						writer.println(str);
					}

				}finally{

					writer.exdent();
				}
			}

			piecePicker.generateEvidence( writer );
		}

		try{
			peer_transports_mon.enter();

			writer.println( "Peers: total = " + peer_transports_cow.size());

			writer.indent();

			try{
				writer.indent();

				Iterator<PEPeerTransport> it2 = peer_transports_cow.iterator();

				while( it2.hasNext()){

					PEPeerTransport	peer = it2.next();

					peer.generateEvidence( writer );
				}
			}finally{

				writer.exdent();
			}
		}finally{

			peer_transports_mon.exit();

			writer.exdent();
		}

		disk_mgr.generateEvidence( writer );
	}
}
