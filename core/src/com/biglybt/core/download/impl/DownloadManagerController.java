/*
 * Created on 29-Jul-2005
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

package com.biglybt.core.download.impl;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.util.*;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.disk.*;
import com.biglybt.core.disk.impl.DiskManagerUtil;
import com.biglybt.core.diskmanager.file.FMFileManagerFactory;
import com.biglybt.core.download.*;
import com.biglybt.core.download.DownloadManagerState.ResumeHistory;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerEvent;
import com.biglybt.core.global.GlobalManagerStats;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.LogRelation;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.networkmanager.LimitedRateGroup;
import com.biglybt.core.networkmanager.NetworkConnection;
import com.biglybt.core.networkmanager.NetworkManager;
import com.biglybt.core.networkmanager.Transport;
import com.biglybt.core.peer.*;
import com.biglybt.core.peermanager.PeerManager;
import com.biglybt.core.peermanager.PeerManagerRegistration;
import com.biglybt.core.peermanager.PeerManagerRegistrationAdapter;
import com.biglybt.core.peermanager.peerdb.PeerItemFactory;
import com.biglybt.core.peermanager.piecepicker.PiecePicker;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentException;
import com.biglybt.core.torrent.TOTorrentFile;
import com.biglybt.core.tracker.client.TRTrackerAnnouncer;
import com.biglybt.core.tracker.client.TRTrackerAnnouncerDataProvider;
import com.biglybt.core.tracker.client.TRTrackerScraperResponse;
import com.biglybt.core.util.*;
import com.biglybt.core.util.bloom.BloomFilter;
import com.biglybt.core.util.bloom.BloomFilterFactory;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.network.ConnectionManager;
import com.biglybt.plugin.extseed.ExternalSeedPeer;
import com.biglybt.plugin.extseed.ExternalSeedPlugin;

public class
DownloadManagerController
	extends LogRelation
	implements PEPeerManagerAdapter, PeerManagerRegistrationAdapter, SimpleTimer.TimerTickReceiver
{
	private static final long STATE_FLAG_HASDND = 0x01;
	private static final long STATE_FLAG_COMPLETE_NO_DND = 0x02;

	static long skeleton_builds;

	static boolean	tracker_stats_exclude_lan;

	static{
		COConfigurationManager.addAndFireParameterListener(
			"Tracker Client Exclude LAN",
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					String name)
				{
					tracker_stats_exclude_lan = COConfigurationManager.getBooleanParameter( name );
				}
			});
	}

	private static ExternalSeedPlugin	ext_seed_plugin;
	private static boolean				ext_seed_plugin_tried;

	public static ExternalSeedPlugin
	getExternalSeedPlugin()
	{
		if ( !ext_seed_plugin_tried ){

			ext_seed_plugin_tried	= true;

			try {
				PluginInterface ext_pi = CoreFactory.getSingleton().getPluginManager().getPluginInterfaceByClass(ExternalSeedPlugin.class);
				if (ext_pi != null) {
					ext_seed_plugin = (ExternalSeedPlugin)ext_pi.getPlugin();
				}

			}catch (Throwable e){

				Debug.printStackTrace( e );
			}
		}

		return( ext_seed_plugin );
	}

		// DISK listeners

	private static final int LDT_DL_ADDED		= 1;
	private static final int LDT_DL_REMOVED		= 2;

	static final ListenerManager	disk_listeners_agregator 	= ListenerManager.createAsyncManager(
			"DMC:DiskListenAgregatorDispatcher",
			new ListenerManagerDispatcher()
			{
				@Override
				public void
				dispatch(
					Object		_listener,
					int			type,
					Object		value )
				{
					DownloadManagerDiskListener	listener = (DownloadManagerDiskListener)_listener;

					if ( type == LDT_DL_ADDED ){

						listener.diskManagerAdded((DiskManager)value);

					}else if ( type == LDT_DL_REMOVED ){

						listener.diskManagerRemoved((DiskManager)value);
					}
				}
			});

	private final ListenerManager	disk_listeners 	= ListenerManager.createManager(
			"DMC:DiskListenDispatcher",
			new ListenerManagerDispatcher()
			{
				@Override
				public void
				dispatch(
					Object		listener,
					int			type,
					Object		value )
				{
					disk_listeners_agregator.dispatch( listener, type, value );
				}
			});

	private final AEMonitor	disk_listeners_mon	= new AEMonitor( "DownloadManagerController:DL" );

	final AEMonitor	control_mon		= new AEMonitor( "DownloadManagerController" );
	private final AEMonitor	state_mon		= new AEMonitor( "DownloadManagerController:State" );
	final AEMonitor	facade_mon		= new AEMonitor( "DownloadManagerController:Facade" );

	final DownloadManagerImpl		download_manager;
	DownloadManagerState			download_manager_state;
	final DownloadManagerStatsImpl	stats;

		// these are volatile as we want to ensure that if a state is read it is always the
		// most up to date value available (as we don't synchronize state read - see below
		// for comments)

	private volatile int		state_set_by_method = DownloadManager.STATE_START_OF_DAY;
	private volatile int		substate;
	private volatile boolean 	force_start;
	private volatile boolean	is_force_rechecking;
	
		// to try and ensure people don't start using disk_manager without properly considering its
		// access implications we've given it a silly name

	private volatile DiskManager 			disk_manager_use_accessors;
	private DiskManagerListener				disk_manager_listener_use_accessors;

	private Object							disk_manager_pieces_snapshot_lock = new Object();
	private volatile DiskManagerPiece[]		disk_manager_pieces_snapshot;
	
	final FileInfoFacadeSet		fileFacadeSet = new FileInfoFacadeSet();
	boolean					files_facade_destroyed;

	private boolean					cached_complete_excluding_dnd;
	private boolean					cached_has_dnd_files;
	private boolean         		cached_values_set;

	private Set<String>				cached_networks;
	final Object					cached_networks_lock = new Object();

	private PeerManagerRegistration	peer_manager_registration;
	private PEPeerManager 			peer_manager;

	private DownloadManagerStateAttributeListener	dm_attribute_listener;
	
	private Object			external_rate_limiters_cow_lock = new Object();
	
	private List<Object[]>	external_rate_limiters_cow;

	private String 	errorDetail;
	private int		errorType	= DownloadManager.ET_NONE;
	private int		errorFlags	= 0;
	
	final GlobalManagerStats		global_stats;

	boolean bInitialized = false;

	long data_send_rate_at_close;

	private static final int			ACTIVATION_REBUILD_TIME		= 10*60*1000;
	private static final int			BLOOM_SIZE					= 64;
	private volatile BloomFilter		activation_bloom;
	private volatile long				activation_bloom_create_time	= SystemTime.getMonotonousTime();
	private volatile int				activation_count;
	private volatile long				activation_count_time;

	private boolean	 piece_checking_enabled	= true;

	private long		priority_connection_count;

	private static final int				HTTP_SEEDS_MAX	= 64;
	private final LinkedList<ExternalSeedPeer>	http_seeds = new LinkedList<>();

	private int	md_info_dict_size;
	private volatile WeakReference<byte[]>	md_info_dict_ref = new WeakReference<>(null);

	private static final int MD_INFO_PEER_HISTORY_MAX 		= 128;

	private final Map<String,int[]>	md_info_peer_history =
		new LinkedHashMap<String,int[]>(MD_INFO_PEER_HISTORY_MAX,0.75f,true)
		{
			@Override
			protected boolean
			removeEldestEntry(
		   		Map.Entry<String,int[]> eldest)
			{
				return size() > MD_INFO_PEER_HISTORY_MAX;
			}
		};



	protected
	DownloadManagerController(
		DownloadManagerImpl	_download_manager )
	{
		download_manager = _download_manager;

		GlobalManager	gm = download_manager.getGlobalManager();

		global_stats = gm.getStats();

		stats	= (DownloadManagerStatsImpl)download_manager.getStats();

		cached_values_set = false;
	}

	protected void
	setDownloadManagerState(
		DownloadManagerState	dms )
	{
		download_manager_state = dms;
	}
	
	protected void
	setInitialState(
		int	initial_state )
	{
			// only take note if there's been no errors

		bInitialized = true;

		if ( getState() == DownloadManager.STATE_START_OF_DAY ){

			setState( initial_state, true );
		}

		TOTorrent torrent = download_manager.getTorrent();

		if (torrent != null) {

			try{
				peer_manager_registration = PeerManager.getSingleton().registerLegacyManager( torrent.getHashWrapper(), this );

				md_info_dict_size = download_manager_state.getIntAttribute( DownloadManagerState.AT_MD_INFO_DICT_SIZE );

				if ( md_info_dict_size == 0 ){

					try{
						md_info_dict_size = BEncoder.encode((Map)torrent.serialiseToMap().get( "info" )).length;

					}catch( Throwable e ){

						md_info_dict_size = -1;
					}

					download_manager_state.setIntAttribute( DownloadManagerState.AT_MD_INFO_DICT_SIZE, md_info_dict_size );
				}

			}catch( TOTorrentException e ){

				Debug.printStackTrace(e);
			}
		}

		if (download_manager_state.parameterExists(DownloadManagerState.PARAM_DND_FLAGS)) {
			long flags = download_manager_state.getLongParameter(DownloadManagerState.PARAM_DND_FLAGS);
			cached_complete_excluding_dnd = (flags & STATE_FLAG_COMPLETE_NO_DND) != 0;
			cached_has_dnd_files = (flags & STATE_FLAG_HASDND) != 0;
			cached_values_set = true;
		}
	}

	public void
	startDownload(
		TRTrackerAnnouncer	tracker_client )
	{
		DiskManager	dm;

		try{
			control_mon.enter();
			
			if (download_manager.isDestroyed()) {
				if (Logger.isEnabled()) {
					Logger.log(new LogEvent(this, LogIDs.CORE, LogEvent.LT_ERROR,
							"startDownload() after manager is destroyed"));
				}
				return;
			}

			if ( getState() != DownloadManager.STATE_READY ){

				Debug.out( "DownloadManagerController::startDownload state must be ready, " + getState());

				setFailed( "Inconsistent download state: startDownload, state = " + getState());

				return;
			}

	 		if ( tracker_client == null ){

	  			Debug.out( "DownloadManagerController:startDownload: tracker_client is null" );

	  				// one day we should really do a proper state machine for this. In the meantime...
	  				// probably caused by a "stop" during initialisation, I've reproduced it once or twice
	  				// in my life... Tidy things up so we don't sit here in a READ state that can't
	  				// be started.

	  			stopIt( DownloadManager.STATE_STOPPED, false, false, false );

	  			return;
	  		}

			if ( peer_manager != null ){

				Debug.out( "DownloadManagerController::startDownload: peer manager not null" );

					// try and continue....

				peer_manager.stopAll();

				SimpleTimer.removeTickReceiver( this );

				DownloadManagerRateController.removePeerManager( peer_manager );

				peer_manager	= null;
								
				download_manager_state.removeListener(
						dm_attribute_listener,
						DownloadManagerState.AT_FLAGS,
						DownloadManagerStateAttributeListener.WRITTEN );
				
				download_manager_state.removeListener(
						dm_attribute_listener,
						DownloadManagerState.AT_MASK_DL_COMP_OPTIONAL,
						DownloadManagerStateAttributeListener.WRITTEN );

				dm_attribute_listener = null;
			}

			dm	= getDiskManager();

			if ( dm == null ){

				Debug.out( "DownloadManagerController::startDownload: disk manager is null" );

				return;
			}

			setState( DownloadManager.STATE_DOWNLOADING, false );

		}finally{

			control_mon.exit();

		}

		cacheNetworks();

			// make sure it is started before making it "visible"

		final PEPeerManager temp = PEPeerManagerFactory.create( tracker_client.getPeerId(), this, dm );

		download_manager.informWillBeStarted( temp );

		temp.start();

		   //The connection to the tracker

		tracker_client.setAnnounceDataProvider(
	    		new TRTrackerAnnouncerDataProvider()
	    		{
	    			final private PEPeerManagerStats	pm_stats = temp.getStats();

	    			private long	last_reported_total_received;
	    			private long	last_reported_total_received_data;
	    			private long	last_reported_total_received_discard;
	    			private long	last_reported_total_received_failed;

	    			@Override
				    public String
					getName()
	    			{
	    				return( getDisplayName());
	    			}

	    			@Override
				    public long
	    			getTotalSent()
	    			{
	    				return( tracker_stats_exclude_lan?pm_stats.getTotalDataBytesSentNoLan():pm_stats.getTotalDataBytesSent());
	    			}

	    			@Override
				    public long
	    			getTotalReceived()
	    			{
	    				long received 	= tracker_stats_exclude_lan?pm_stats.getTotalDataBytesReceivedNoLan():pm_stats.getTotalDataBytesReceived();
	    				long discarded 	= pm_stats.getTotalDiscarded();
	    				long failed		= pm_stats.getTotalHashFailBytes();

	    				long verified = received - ( discarded + failed );

	    				verified -= temp.getHiddenBytes();

	    					// ensure we don't go backwards. due to lack of atomicity of updates and possible
	    					// miscounting somewhere we have seen this occur

	    				if ( verified < last_reported_total_received ){

	    					verified = last_reported_total_received;

	    						// use -1 as indicator that we've reported this event

	    					if ( last_reported_total_received_data != -1 ){

	    						/*
	    						Debug.out(
	    								getDisplayName() + ": decrease in overall downloaded - " +
	    								"data=" + received + "/" + last_reported_total_received_data +
	    								",discard=" + discarded + "/" + last_reported_total_received_discard +
	    								",fail=" + failed + "/" + last_reported_total_received_failed );
	    						*/

	    						last_reported_total_received_data = -1;
	    					}
	    				}else{

	    					last_reported_total_received = verified;

	    					last_reported_total_received_data		= received;
	    					last_reported_total_received_discard	= discarded;
	    					last_reported_total_received_failed		= failed;
	    				}

	    				return( verified < 0?0:verified );
	    			}

	    			@Override
				    public long
	    			getRemaining()
	    			{
	    				return( Math.max( temp.getRemaining(), temp.getHiddenBytes()));
	    			}

	    			@Override
				    public long
	    			getFailedHashCheck()
	    			{
	    				return( pm_stats.getTotalHashFailBytes());
	    			}

					@Override
					public String
					getExtensions()
					{
						return( getTrackerClientExtensions());
					}

					@Override
					public int
					getMaxNewConnectionsAllowed( String network )
					{
						return( temp.getMaxNewConnectionsAllowed( network ));
					}

					@Override
					public int
					getPendingConnectionCount()
					{
						return( temp.getPendingPeerCount());
					}

					@Override
					public int
					getConnectedConnectionCount()
					{
						return( temp.getNbPeers() + temp.getNbSeeds());
					}

					@Override
					public int
					getUploadSpeedKBSec(
						boolean estimate )
					{
						long	current_local = stats.getDataSendRate();

						if ( estimate ){

								// see if we have an old value from previous stop/start

							current_local = data_send_rate_at_close;

							if ( current_local == 0 ){

								int current_global 	= global_stats.getDataSendRate();

								int	old_global		= global_stats.getDataSendRateAtClose();

								if ( current_global < old_global ){

									current_global = old_global;
								}

								List managers = download_manager.getGlobalManager().getDownloadManagers();

								int	num_dls = 0;

									// be optimistic and share out the bytes between non-seeds

								for (int i=0;i<managers.size();i++){

									DownloadManager	dm = (DownloadManager)managers.get(i);

									if ( dm.getStats().getDownloadCompleted( false ) == 1000 ){

										continue;
									}

									int	state = dm.getState();

									if ( 	state != DownloadManager.STATE_ERROR &&
											state != DownloadManager.STATE_STOPPING &&
											state != DownloadManager.STATE_STOPPED ){

										num_dls++;
									}
								}

								if ( num_dls == 0 ){

									current_local = current_global;
								}else{

									current_local = current_global/num_dls;
								}
							}
						}

						return((int)((current_local+1023)/1024 ));
					}
					
	    			public int
	    			getTCPListeningPortNumber()
	    			{
	    				return( DownloadManagerController.this.getTCPListeningPortNumber());
	    			}

					@Override
					public int
					getCryptoLevel()
					{
						return( download_manager.getCryptoLevel());
					}

					@Override
					public void
					setPeerSources(
						String[]	allowed_sources )
					{
						String[]	sources = PEPeerSource.PS_SOURCES;

						for (int i=0;i<sources.length;i++){

							String	s = sources[i];

							boolean	ok = false;

							for (int j=0;j<allowed_sources.length;j++){

								if ( s.equals( allowed_sources[j] )){

									ok = true;

									break;
								}
							}

							if ( !ok ){

								download_manager_state.setPeerSourcePermitted( s, false );
							}
						}

						PEPeerManager pm = getPeerManager();

						if ( pm != null ){

							Set<String>	allowed = new HashSet<>();

							allowed.addAll( Arrays.asList( allowed_sources ));

							Iterator<PEPeer> it = pm.getPeers().iterator();

							while( it.hasNext()){

								PEPeer peer = it.next();

								if ( !allowed.contains( peer.getPeerSource())){

									pm.removePeer( peer, "Peer source not permitted", Transport.CR_STOPPED_OR_REMOVED );
								}
							}
						}
					}

					@Override
					public boolean
					isPeerSourceEnabled(
						String		peer_source )
					{
						return( DownloadManagerController.this.isPeerSourceEnabled( peer_source ));
					}
	    		});


		List<Object[]>	limiters;

		try{
			control_mon.enter();

			peer_manager = temp;
			
			if ( dm_attribute_listener != null ) {
				
				download_manager_state.removeListener(
						dm_attribute_listener,
						DownloadManagerState.AT_FLAGS,
						DownloadManagerStateAttributeListener.WRITTEN );
				
				download_manager_state.removeListener(
						dm_attribute_listener,
						DownloadManagerState.AT_MASK_DL_COMP_OPTIONAL,
						DownloadManagerStateAttributeListener.WRITTEN );
			}
			
			dm_attribute_listener = new DownloadManagerStateAttributeListener(){
				
				boolean	did_set;
				
				@Override
				public void 
				attributeEventOccurred(
					DownloadManager download, 
					String attribute, 
					int event_type)
				{
					
						// this fires on any flag change so make sure we only clear the seq flag if we set it
						// otherwise this interferes with other seq settings
					
					boolean seq = download_manager_state.getFlag( DownloadManagerState.FLAG_SEQUENTIAL_DOWNLOAD );
					
					PiecePicker pp = temp.getPiecePicker();
					
					if ( seq ){
						
						if ( pp.getSequentialInfo() == 0 ){
						
							pp.setSequentialAscendingFrom( 0 );
						
							did_set = true;
						}
						
					}else{
						
						if ( did_set ){
						
							pp.clearSequential();
						}
					}
					
					Boolean mask = download_manager_state.getOptionalBooleanAttribute(DownloadManagerState.AT_MASK_DL_COMP_OPTIONAL );

					temp.setMaskDownloadCompletion( mask );
				}
			};
			
			download_manager_state.addListener(
					dm_attribute_listener,
					DownloadManagerState.AT_FLAGS,
					DownloadManagerStateAttributeListener.WRITTEN );
			
			download_manager_state.addListener(
					dm_attribute_listener,
					DownloadManagerState.AT_MASK_DL_COMP_OPTIONAL,
					DownloadManagerStateAttributeListener.WRITTEN );

			dm_attribute_listener.attributeEventOccurred( null, null, -1 );	// set initial state
			
			DownloadManagerRateController.addPeerManager( peer_manager );

			SimpleTimer.addTickReceiver( this );

			synchronized( external_rate_limiters_cow_lock ){
			
				limiters = external_rate_limiters_cow;
			}

		}finally{

			control_mon.exit();
		}

		if ( limiters != null ){

			for (int i=0;i<limiters.size();i++){

				Object[]	entry = limiters.get(i);

				temp.addRateLimiter((LimitedRateGroup)entry[0],((Boolean)entry[1]).booleanValue());
			}
		}

		// Inform only after peer_manager.start(), because it
		// may have switched it to STATE_SEEDING (in which case we don't need to
		// inform).

		if (getState() == DownloadManager.STATE_DOWNLOADING) {

			download_manager.informStateChanged();
		}

		download_manager.informStarted( temp );
	}




	public void
	initializeDiskManager(
		boolean	open_for_seeding )
	{
		initializeDiskManagerSupport(
			DownloadManager.STATE_INITIALIZED,
			new DiskManagerListener_Default(open_for_seeding));
	}

	protected void
	initializeDiskManagerSupport(
		int						initialising_state,
		DiskManagerListener		listener )
	{
		try{
			control_mon.enter();

			int	entry_state = getState();

			if ( 	entry_state != DownloadManager.STATE_WAITING &&
					entry_state != DownloadManager.STATE_STOPPED &&
					entry_state != DownloadManager.STATE_QUEUED &&
					entry_state != DownloadManager.STATE_ERROR ){

				Debug.out( "DownloadManagerController::initializeDiskManager: Illegal initialize state, " + entry_state );

				setFailed( "Inconsistent download state: initSupport, state = " + entry_state );

				return;
			}

			DiskManager	old_dm = getDiskManager();

			if ( old_dm != null ){

				Debug.out( "DownloadManagerController::initializeDiskManager: disk manager is not null" );

					// we shouldn't get here but try to recover the situation

				old_dm.stop( false );

				setDiskManager( null, null );
			}

			errorDetail	= "";
			errorType	= DownloadManager.ET_NONE;
			errorFlags	= 0;
			
			setState( initialising_state, false );

		  	DiskManager dm = DiskManagerFactory.create( download_manager.getTorrent(), download_manager);

		  	disk_manager_pieces_snapshot = null;
		  	
	  	  	setDiskManager( dm, listener );

		}finally{

			control_mon.exit();

			download_manager.informStateChanged();
		}
	}

	public boolean
	canForceRecheck()
	{
	  	int state = getState();

	  		// gotta check error + disk manager state as error can be generated by both
	  		// an overall error or a running disk manager in faulty state

	  	return(		(state == DownloadManager.STATE_STOPPED ) ||
	  	           	(state == DownloadManager.STATE_QUEUED ) ||
	  	           	(state == DownloadManager.STATE_ERROR && getDiskManager() == null));
	}

	protected boolean
	isForceRechecking()
	{
		return( is_force_rechecking );
	}
	
	public void
	forceRecheck(
		Map						resume_data )
	{
		try{
			control_mon.enter();

			if ( getDiskManager() != null || !canForceRecheck() ){

				Debug.out( "DownloadManagerController::forceRecheck: illegal entry state" );

				return;
			}

			if ( is_force_rechecking ){
				
				Debug.out( "DownloadManagerController::forceRecheck: already active" );
				
				return;
			}
			
			is_force_rechecking = true;
			
			int start_state = DownloadManagerController.this.getState();

			if ( resume_data == null ){
					// remove resume data
	
				download_manager_state.clearResumeData();
				
			}else{
				
				download_manager_state.setResumeData( resume_data );
			}
			
	  			// For extra protection from a plugin stopping a checking torrent,
	  			// fake a forced start.

	  		boolean wasForceStarted = force_start;

	  		force_start = true;

				// if a file has been deleted we want this recheck to recreate the file and mark
				// it as 0%, not fail the recheck. Otherwise the only way of recovering is to remove and
				// re-add the torrent

	  		download_manager.setDataAlreadyAllocated( false );

	  		initializeDiskManagerSupport(
	  			DownloadManager.STATE_CHECKING,
	  			new ForceRecheckDiskManagerListener(
	  				wasForceStarted, 
	  				start_state, 
	  				new ForceRecheckListener()
	  				{
	  					public void 
	  					forceRecheckComplete(
	  						DownloadManager dm,
	  						boolean			cancelled )
	  					{
	  						is_force_rechecking = false;
	  						
	  							// only fire for actual rechecks rather than restoring of resume state
	  						
	  						if ( resume_data == null ){
	  						
	  							download_manager.fireGlobalManagerEvent( GlobalManagerEvent.ET_RECHECK_COMPLETE,  new Object[]{ true, cancelled });
	  							
	  							if ( cancelled ){
	  								
	  								List<ResumeHistory> history = download_manager_state.getResumeDataHistory();
	  								
	  								if ( !history.isEmpty()){
	  									
	  									AEThread2.createAndStartDaemon( "resetResume", ()->{
	  										
	  										download_manager_state.restoreResumeData( history.get( history.size()-1));
	  									});
	  								}
	  							}
	  						}
	  					}
	  				}));

		}finally{

			control_mon.exit();
		}
	}

	interface
	ForceRecheckListener
	{
		public void 
		forceRecheckComplete(
			DownloadManager dm,
			boolean			cancelled );
	}
	
 	public void
  	setPieceCheckingEnabled(
  		boolean enabled )
 	{
 		piece_checking_enabled	= enabled;

 		DiskManager dm = getDiskManager();

 		if ( dm != null ){

 			dm.setPieceCheckingEnabled( enabled );
 		}
 	}

	public void
	stopIt(
		int 				_stateAfterStopping,
		final boolean 		remove_torrent,
		final boolean 		remove_data,
		final boolean		for_removal )
	{
		long	current_up = stats.getDataSendRate();

		if ( current_up != 0 ){

			data_send_rate_at_close = current_up;
		}

		boolean closing = _stateAfterStopping == DownloadManager.STATE_CLOSED;

		if ( closing ){

			_stateAfterStopping = DownloadManager.STATE_STOPPED;
		}

		final int stateAfterStopping	= _stateAfterStopping;

		try{
			control_mon.enter();

			int	state = getState();

			if ( 	( state == DownloadManager.STATE_STOPPED || state == DownloadManager.STATE_ERROR ) && 
					getDiskManager() == null ){

				//already in stopped state, just do removals if necessary

				if ( stateAfterStopping !=  DownloadManager.STATE_ERROR && state == DownloadManager.STATE_ERROR ){
				
						// explicitly stopping an error download clears this stuff out
					
					errorType	= DownloadManager.ET_NONE;
					errorDetail	= "";
					errorFlags	= 0;
				}
				
				if( remove_data ){

					download_manager.deleteDataFiles();

				}else{

					if ( for_removal && COConfigurationManager.getBooleanParameter( "Delete Partial Files On Library Removal") ){

						download_manager.deletePartialDataFiles();
					}
				}

				if( remove_torrent ){

					download_manager.deleteTorrentFile();
				}

				download_manager.informStopped( null, stateAfterStopping==DownloadManager.STATE_QUEUED );

				setState( _stateAfterStopping, false );

				return;
			}


			if ( state == DownloadManager.STATE_STOPPING){

				return;
			}

			setSubState( _stateAfterStopping );

			setState( DownloadManager.STATE_STOPPING, false );

			NonDaemonTaskRunner.run(
				new NonDaemonTask()
				{
					@Override
					public Object
					run()
					{
						try{
							if ( peer_manager != null ){

								peer_manager.stopAll();

								stats.saveSessionTotals();

								download_manager_state.setLongParameter( DownloadManagerState.PARAM_DOWNLOAD_LAST_ACTIVE_TIME, SystemTime.getCurrentTime());

								SimpleTimer.removeTickReceiver( DownloadManagerController.this );

								DownloadManagerRateController.removePeerManager( peer_manager );

								download_manager_state.removeListener(
										dm_attribute_listener,
										DownloadManagerState.AT_FLAGS,
										DownloadManagerStateAttributeListener.WRITTEN );
								
								download_manager_state.removeListener(
										dm_attribute_listener,
										DownloadManagerState.AT_MASK_DL_COMP_OPTIONAL,
										DownloadManagerStateAttributeListener.WRITTEN );

								dm_attribute_listener = null;
							}

							// do this even if null as it also triggers tracker actions

							download_manager.informStopped( peer_manager, stateAfterStopping==DownloadManager.STATE_QUEUED );

							peer_manager	= null;

							DiskManager	dm = getDiskManager();

							if ( dm != null ){

								boolean went_async = dm.stop( closing );

								if ( went_async ){

									try {
										int	wait_count = 0;
	
										// Delay by 10ms, hoping for really short stop
										Thread.sleep(10);
	
										while( !dm.isStopped()){
	
											wait_count++;
	
											if ( wait_count > 2*60*10 ){
	
												Debug.out( "Download stop took too long to complete" );
	
												break;
	
											}else if ( wait_count % 200 == 0 ){
	
												Debug.out( "Waiting for download to stop - elapsed=" + wait_count + " sec" );
											}
	
											Thread.sleep(100);
										}
									}catch( Throwable e ) {
										
										Debug.out( e );
									}
								}

								stats.setCompleted(stats.getCompleted());
								stats.recalcDownloadCompleteBytes();

								// we don't want to update the torrent if we're seeding

								if ( !download_manager.getAssumedComplete()){
									download_manager_state.save(false);
								}

								setDiskManager( null, null );
							}

						}finally{

							force_start = false;

							if( remove_data ){

								download_manager.deleteDataFiles();

							}else{

								if ( for_removal && COConfigurationManager.getBooleanParameter( "Delete Partial Files On Library Removal") ){

									download_manager.deletePartialDataFiles();
								}
							}

							if( remove_torrent ){

								download_manager.deleteTorrentFile();
							}

							List<ExternalSeedPeer> to_remove = new ArrayList<>();

							synchronized( http_seeds ){

								to_remove.addAll( http_seeds );

								http_seeds.clear();
							}

							for ( ExternalSeedPeer peer: to_remove ){

								peer.remove();
							}

							// only update the state if things haven't gone wrong

							if ( getState() == DownloadManager.STATE_STOPPING ){

								setState( stateAfterStopping, true );
							}
						}
						
						return( null );
					}

					@Override
					public String
					getName()
					{
						return( "Stopping '" + getDisplayName() + "'" );
					}

				});



		}catch( Throwable e ){

			Debug.printStackTrace( e );

		}finally{

			control_mon.exit();

			Logger.log(new LogEvent(this, LogIDs.CORE, "Stopped - state=" + getState() + ",error=" + getErrorType() + "/" + getErrorDetail() + "/" + getErrorFlags()));

			download_manager.informStateChanged();
		}
	}

	protected void
	setStateWaiting()
	{
		setState(DownloadManager.STATE_WAITING, true );
	}

  	@Override
	  public void
  	setStateFinishing()
  	{
  		setState(DownloadManager.STATE_FINISHING, true);
  	}

	public void
	setStateDownloading()
	{
		if (getState() == DownloadManager.STATE_SEEDING) {
			setState(DownloadManager.STATE_DOWNLOADING, true);
		} else if (getState() != DownloadManager.STATE_DOWNLOADING) {
			Logger.log(new LogEvent(this, LogIDs.CORE, LogEvent.LT_WARNING,
					"Trying to set state to downloading when state is not seeding"));
		}
	}


  	@Override
	public void
  	setStateSeeding(
  		boolean	never_downloaded )
	{
		// should already be finishing, but make sure (if it already is, there
		// won't be a trigger)
  		
		setStateFinishing();

		download_manager.downloadEnded(never_downloaded);

		setState( DownloadManager.STATE_SEEDING, true);
		
		if ( !never_downloaded ){
			
	    	if ( !COConfigurationManager.getBooleanParameter( "StartStopManager_bRetainForceStartWhenComplete" )){
	
	    		if (isForceStart()){
	
		    		setForceStart(false);
	    		}
	    	}
		}
	}

  	public boolean
  	isStateSeeding()
  	{
  		return( getState() == DownloadManager.STATE_SEEDING );
  	}

  	protected void
  	setStateQueued()
  	{
  		setState(DownloadManager.STATE_QUEUED, true);
  	}

  	public int
  	getState()
  	{
  		if ( state_set_by_method != DownloadManager.STATE_INITIALIZED ){

  			return( state_set_by_method );
  		}

  			// we don't want to synchronize here as there are potential deadlock problems
  			// regarding the DownloadManager::addListener call invoking this method while
  			// holding the listeners monitor.
  			//
  		DiskManager	dm = getDiskManager();

	  	if ( dm == null){

	  		return DownloadManager.STATE_INITIALIZED;
	  	}

  		int diskManagerState = dm.getState();

		if (diskManagerState == DiskManager.INITIALIZING){

			return DownloadManager.STATE_INITIALIZED;

		}else if (diskManagerState == DiskManager.ALLOCATING){

			return DownloadManager.STATE_ALLOCATING;

		}else if (diskManagerState == DiskManager.CHECKING){

			return DownloadManager.STATE_CHECKING;

		}else if (diskManagerState == DiskManager.READY){

			return DownloadManager.STATE_READY;

		}else if (diskManagerState == DiskManager.FAULTY){

			if ( dm.getErrorType() == DiskManager.ET_STOP_DURING_INIT ){
				
				return DownloadManager.STATE_STOPPED;
				
			}else{
			
				return DownloadManager.STATE_ERROR;
			}
		}

		return DownloadManager.STATE_ERROR;
  	}

	protected int
  	getSubState()
  	{
		if ( state_set_by_method == DownloadManager.STATE_STOPPING ){

			return( substate );
		}else{

			return( getState());
		}
  	}

	private void
	setSubState(
		int	ss )
	{
		substate	= ss;
	}

	/**
	 * @param _state
	 * @param _inform_changed trigger informStateChange (which may not trigger
	 *                        listeners if state hasn't changed since last trigger)
	 */
	void
  	setState(
  		int 		_state,
  		boolean		_inform_changed )
  	{
  			// we bring this call out of the monitor block to prevent a potential deadlock whereby we chain
  			// state_mon -> control_mon (there exist numerous dependencies control_mon -> state_mon...

  		boolean	call_filesExist	= false;

   		try{
  			state_mon.enter();

	  		int	old_state = state_set_by_method;

			// note: there is a DIFFERENCE between the state held on the DownloadManager and
		    // that reported via getState as getState incorporated DiskManager states when
		    // the DownloadManager is INITIALIZED
		  	//System.out.println( "DM:setState - " + _state );

	  		if ( old_state != _state ){

	  			state_set_by_method = _state;

	  			if ( state_set_by_method != DownloadManager.STATE_QUEUED ){

	  					// only maintain this while queued

	  				activation_bloom = null;

	  				if ( 	state_set_by_method == DownloadManager.STATE_STOPPED ||
	  						state_set_by_method == DownloadManager.STATE_DOWNLOADING ||
	  						state_set_by_method == DownloadManager.STATE_SEEDING ){

	  						// don't need this anymore once stopped or active
	  					
	  					activation_count = 0;
	  				}
	  			}

	  			if (state_set_by_method == DownloadManager.STATE_QUEUED ){


	  				// don't pick up errors regarding missing data while queued.
	  				// We'll do that when the torrent starts.  Saves time at startup
	  				// pick up any errors regarding missing data for queued SEEDING torrents
//	  				if (  download_manager.getAssumedComplete()){
//
//	  					call_filesExist	= true;
//	  				}

	  			}else if ( state_set_by_method == DownloadManager.STATE_ERROR ){

		      		// the process of attempting to start the torrent may have left some empty
		      		// directories created, some users take exception to this.
		      		// the most straight forward way of remedying this is to delete such empty
		      		// folders here

	  				TOTorrent	torrent = download_manager.getTorrent();

	  				if ( torrent != null && !torrent.isSimpleTorrent()){

	  					File	save_dir_file	= download_manager.getAbsoluteSaveLocation();

	  					if ( save_dir_file != null && save_dir_file.exists() && save_dir_file.isDirectory()){

	  						TorrentUtils.recursiveEmptyDirDelete( save_dir_file, false );
	  					}
	  				}
	  			}
	  			
	  			if ( old_state == DownloadManager.STATE_STOPPED ){
	  				
	  				download_manager.setStopReason( null );
	  			}
	  		}
  		}finally{

  			state_mon.exit();
  		}

  		if ( call_filesExist ){

  			filesExist( true );
  		}

  		if ( _inform_changed ){

  			download_manager.informStateChanged();
  		}
  	}

	 /**
	   * Stops the current download, then restarts it again.
	   */

	@Override
	public void
	restartDownload(boolean forceRecheck)
	{
		boolean	was_force_start = isForceStart();

		stopIt( DownloadManager.STATE_STOPPED, false, false, false );

		if (forceRecheck) {
			download_manager_state.clearResumeData();
		}

		download_manager.initialize();

		if ( was_force_start ){

			setForceStart(true);
		}
	}

	protected void
	destroy()
	{
		if ( peer_manager_registration != null ){

			peer_manager_registration.unregister();

			peer_manager_registration	= null;
		}

		fileFacadeSet.destroyFileInfo();
	}

	@Override
	public void 
	saveTorrentState()
	{	
		TOTorrent dms_torrent = download_manager_state.getTorrent();
		
		if ( dms_torrent.getEffectiveTorrentType() == TOTorrent.TT_V2 ){
		
			if ( !download_manager_state.getBooleanAttribute( DownloadManagerState.AT_TORRENT_EXPORT_PROPAGATED )){

					// this attribute is a hack to get the torrent to be serialised via the 'save'
				
				download_manager_state.setLongAttribute( DownloadManagerState.AT_TORRENT_SAVE_TIME, SystemTime.getCurrentTime());	
			
				download_manager_state.save( false );
			
				if ( dms_torrent.isExportable()){
				
					if ( TorrentUtils.propagateExportability( dms_torrent, FileUtil.newFile( download_manager.getTorrentFileName()))){
						
						download_manager_state.setBooleanAttribute( DownloadManagerState.AT_TORRENT_EXPORT_PROPAGATED, true );
					}
				}
			}
		}
	}
	
	@Override
	public boolean
	isPeerSourceEnabled(
		String		peer_source )
	{
		return( download_manager_state.isPeerSourceEnabled( peer_source ));
	}

	private void
	cacheNetworks()
	{
		synchronized( cached_networks_lock ){

			if ( cached_networks != null ){

				return;
			}

			cached_networks = new HashSet<>(Arrays.asList(download_manager_state.getNetworks()));

			download_manager_state.addListener(
				new DownloadManagerStateAttributeListener()
				{
					@Override
					public void
					attributeEventOccurred(
						DownloadManager 	download,
						String 				attribute,
						int 				event_type )
					{
						synchronized( cached_networks_lock ){

							cached_networks = new HashSet<>(Arrays.asList(download_manager_state.getNetworks()));
						}

						PEPeerManager	pm = peer_manager;

						if ( pm != null ){

								// disconnect all peers - this is required as the new network assignment can
								// require an alternative destination to be used for peer connections

							pm.removeAllPeers( "Networks changed, reconnection required", Transport.CR_NONE );
						}
					}
				},
				DownloadManagerState.AT_NETWORKS,
				DownloadManagerStateAttributeListener.WRITTEN );
		}
	}

	@Override
	public boolean
	isNetworkEnabled(
		String	network )
	{
		Set<String>	cache = cached_networks;

		if ( cache == null ){

			return( download_manager_state.isNetworkEnabled( network ));
		}else{

			return( cache.contains( network ));
		}
	}

	@Override
	public String[]
	getEnabledNetworks()
	{
		Set<String>	cache = cached_networks;

		if ( cache == null ){

			return( download_manager_state.getNetworks());

		}else{

			return( cache.toArray( new String[ cache.size()]));
		}
	}
		// secrets for inbound connections, support all

	@Override
	public byte[][]
	getSecrets()
	{
		TOTorrent	torrent = download_manager.getTorrent();

		try{
			byte[]	secret1 = torrent.getHash();

			try{

				byte[]	secret2	 = getSecret2( torrent );

				return( new byte[][]{ secret1, secret2 });

			}catch( Throwable e ){

				Debug.printStackTrace( e );

				return( new byte[][]{ secret1 } );
			}

		}catch( Throwable e ){

			Debug.printStackTrace( e );

			return( new byte[0][] );
		}
	}

	@Override
	public byte[] 
	getHashOverride()
	{
		HashWrapper hw = download_manager.getTorrentHashOverride();
		
		if ( hw != null ){
			
			return( hw.getBytes());
		}
		
		return( null );
	}
	
	@Override
	public byte[] 
	getPeerID()
	{
		return( download_manager.getTrackerClient().getPeerId());
	}
	
	@Override
	public int 
	getNbPieces()
	{
		return( download_manager.getTorrent().getNumberOfPieces());
	}
	
	@Override
	public int 
	getHashOverrideLocalPort(
		boolean	only_if_allocated )
	{
		return( download_manager.getTCPPortOverride( only_if_allocated ));
	}
	
		// secrets for outbound connections, based on level of target

	@Override
	public byte[][]
  	getSecrets(
  		int	crypto_level )
	{
		TOTorrent	torrent = download_manager.getTorrent();

		try{
			byte[]	secret;

			if ( crypto_level == PeerItemFactory.CRYPTO_LEVEL_1 ){

				HashWrapper override = download_manager.getTorrentHashOverride();
				
				if ( override != null ){
					
					secret = override.getBytes();
					
				}else{
				
					secret = torrent.getHash();
				}
			}else{

				secret = getSecret2( torrent );
			}

			return( new byte[][]{ secret });

		}catch( Throwable e ){

			Debug.printStackTrace( e );

			return( new byte[0][] );
		}
	}

	protected byte[]
	getSecret2(
		TOTorrent	torrent )

		throws TOTorrentException
	{
		Map	secrets_map = download_manager_state.getMapAttribute( DownloadManagerState.AT_SECRETS );

		if ( secrets_map == null ){

			secrets_map = new HashMap();

		}else{

				// clone as we can't just update the returned values

			secrets_map = new LightHashMap(secrets_map);
		}

		if ( secrets_map.size() == 0 ){

			if ( torrent.getEffectiveTorrentType() == TOTorrent.TT_V2 ){
				
				TOTorrentFile[] files = torrent.getFiles();
				
				for ( TOTorrentFile file: files ){
					
					if ( file.getLength() > 0 ){
				
						secrets_map.put( "p1", file.getRootHash());
						
						break;
					}
				}
			}else{
			
				secrets_map.put( "p1", torrent.getPieces()[0] );
			}

			download_manager_state.setMapAttribute( DownloadManagerState.AT_SECRETS, secrets_map );
		}

		return((byte[])secrets_map.get( "p1" ));
	}

	@Override
	public boolean
	manualRoute(
		NetworkConnection connection )
	{
		return false;
	}

	@Override
	public long
	getRandomSeed()
	{
		return( download_manager_state.getLongParameter( DownloadManagerState.PARAM_RANDOM_SEED ));
	}

	public void
	addRateLimiter(
		LimitedRateGroup	group,
		boolean				upload )
	{
		final boolean not_stopping = getState() != DownloadManager.STATE_STOPPING;	// locking issue here on stop :(
		
		PEPeerManager	pm;

		try{
			if ( not_stopping ){
				
				control_mon.enter();
			}

			synchronized( external_rate_limiters_cow_lock ){
				
				ArrayList<Object[]>	new_limiters = new ArrayList<>(external_rate_limiters_cow == null ? 1 : external_rate_limiters_cow.size() + 1);
	
				if ( external_rate_limiters_cow != null ){
	
					new_limiters.addAll( external_rate_limiters_cow );
				}
	
				new_limiters.add( new Object[]{ group, Boolean.valueOf(upload)});
	
				external_rate_limiters_cow = new_limiters;
			}
			
			pm	= peer_manager;

		}finally{

			if ( not_stopping ){
				
				control_mon.exit();
			}
		}

		if ( not_stopping && pm != null ){

			pm.addRateLimiter(group, upload);
		}
	}

	public LimitedRateGroup[]
	getRateLimiters(
		boolean	upload )
	{
		synchronized( external_rate_limiters_cow_lock ){

			if ( external_rate_limiters_cow == null ){

				return( new LimitedRateGroup[0] );

			}else{

				List<LimitedRateGroup> 	result = new ArrayList<>();

				for ( Object[] entry: external_rate_limiters_cow ){

					if ((Boolean)entry[1] == upload ){

						result.add((LimitedRateGroup)entry[0] );
					}
				}

				return( result.toArray( new LimitedRateGroup[ result.size() ]));
			}
		}
	}

	public void
	removeRateLimiter(
		LimitedRateGroup	group,
		boolean				upload )
	{
		final boolean not_stopping = getState() != DownloadManager.STATE_STOPPING;	// locking issue here on stop :(

		PEPeerManager	pm;

		try{
			if ( not_stopping ){
			
				control_mon.enter();
			}
			
			if ( external_rate_limiters_cow != null ){

				ArrayList<Object[]>	new_limiters = new ArrayList<>(external_rate_limiters_cow.size() - 1);

				for (int i=0;i<external_rate_limiters_cow.size();i++){

					Object[]	entry = external_rate_limiters_cow.get(i);

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

			pm	= peer_manager;

		}finally{

			if ( not_stopping ){
			
				control_mon.exit();
			}
		}

		if ( not_stopping && pm != null ){

			pm.removeRateLimiter(group, upload);
		}
	}

	@Override
	public void
	enqueueReadRequest(
		PEPeer							peer,
		DiskManagerReadRequest 			request,
		DiskManagerReadRequestListener 	listener )
	{
		getDiskManager().enqueueReadRequest( request, listener );
	}

	@Override
	public int
	activateRequest(
		InetSocketAddress	address )
	{
		if ( getState() == DownloadManager.STATE_QUEUED ){

			if ( download_manager.isLightSeedTracker( address )){
				
				return( AT_ACCEPTED_PROBE );
			}
			
			long	now = SystemTime.getMonotonousTime();

			BloomFilter	bloom = activation_bloom;

			if ( bloom == null ){

				activation_bloom = bloom = BloomFilterFactory.createAddRemove4Bit( BLOOM_SIZE );
				
				activation_bloom_create_time = now;
			}

			byte[]	address_bytes = AddressUtils.getAddressBytes(address);

			int	hit_count = bloom.add( address_bytes );

			if ( hit_count > 5 ){

				Logger.log(
						new LogEvent(
							this,
							LogIDs.CORE,
							LogEvent.LT_WARNING,
							"Activate request for " + getDisplayName() + " from " + address + " denied as too many recently received" ));

				return( AT_DECLINED );
			}

			Logger.log(new LogEvent(this, LogIDs.CORE, "Activate request for " + getDisplayName() + " from " + address ));


				// we don't really care about the bloom filter filling up and giving false positives
				// as activation events should be fairly rare

			if ( now - activation_bloom_create_time > ACTIVATION_REBUILD_TIME ){

				activation_bloom = BloomFilterFactory.createAddRemove4Bit( BLOOM_SIZE );

				activation_bloom_create_time	= now;
			}

			activation_count = bloom.getEntryCount();

			activation_count_time = now;

			if ( download_manager.activateRequest( activation_count )){
				
				return( AT_ACCEPTED );
			}
		}

		return( AT_DECLINED );
	}

	@Override
	public void
	deactivateRequest(
		InetSocketAddress	address )
	{
		BloomFilter	bloom = activation_bloom;

		if ( bloom != null ){

			byte[]	address_bytes = AddressUtils.getAddressBytes( address );

			int	count = bloom.count( address_bytes);

			for (int i=0;i<count;i++){

				bloom.remove( address_bytes );
			}

			activation_count = bloom.getEntryCount();
		}
	}
	
	@Override
	public byte[]
	getTargetHash()
	{
		HashWrapper hw = download_manager.getTorrentHashOverride();
		
		if ( hw != null ){
			
			return( hw.getBytes());
		}
		
		TOTorrent torrent = download_manager.getTorrent();
		
		if ( torrent != null ){
			
			try{
				return( torrent.getHash());
				
			}catch( Throwable e ){
				
			}
		}
		
		return( null );
	}
	
	@Override
	public int 
	getTCPListeningPortNumber()
	{
		return( download_manager.getTCPListeningPortNumber());
	}
	
	public int
	getActivationCount()
	{
			// in the absence of any new activations we persist the last count for the activation rebuild
			// period

		long	now = SystemTime.getMonotonousTime();

		if ( 	activation_count > 0 && 
				activation_count_time != 0 &&
				now - activation_count_time > ACTIVATION_REBUILD_TIME ){

			activation_count = 0;
		}

		return( activation_count );
	}

	@Override
	public PeerManagerRegistration
	getPeerManagerRegistration()
	{
		return( peer_manager_registration );
	}

  	public boolean
  	isForceStart()
  	{
	    return( force_start );
	}

	public void
	setForceStart(
		boolean _force_start)
	{
		try{
			state_mon.enter();

			if ( force_start != _force_start ){

				force_start = _force_start;

				int	state = getState();

				if (	force_start &&
						(	state == DownloadManager.STATE_ERROR ||
							state == DownloadManager.STATE_STOPPED ||
							state == DownloadManager.STATE_QUEUED )) {

						// Start it!  (Which will cause a stateChanged to trigger)

					setState(DownloadManager.STATE_WAITING, false );
				}
		    }
		}finally{

			state_mon.exit();
		}

			// "state" includes the force-start setting

		download_manager.informStateChanged();
	}

	
	private void
	setFailed(
		DiskManager		dm )
	{
		setFailed( dm.getErrorType(), dm.getErrorMessage() );
	}

	protected void
	setFailed(
		String		reason )
	{
		setFailed( DownloadManager.ET_OTHER, reason );
	}

	protected void
	setFailed(
		String			reason,
		Throwable		cause )
	{
		if ( DiskManagerUtil.isNoSpaceException( cause )){
			
			setFailed( DownloadManager.ET_INSUFFICIENT_SPACE, MessageText.getString( "DiskManager.error.nospace" ) );
			
		}else{
			
			Debug.out( cause );
			
			setFailed( DownloadManager.ET_OTHER, reason + ": " + Debug.getNestedExceptionMessage( cause ));
		}
	}
	
	protected void
	setErrorState(
		int			type,
		String		reason,
		int			flags )
	{
			// recovering download state on restart

		errorFlags = flags;
		
		setFailed( type, reason, true );
	}
	
	protected void
	setFailed(
		int			type,
		String		reason )
	{
		setFailed( type, reason, false );
	}
	
	private void
	setFailed(
		int			type,
		String		reason,
		boolean		recovering )
	{
			// all download errors come through here
		
		if ( type == DownloadManager.ET_STOP_DURING_INIT ){
			
			stopIt( DownloadManager.STATE_STOPPED, false, false, false );
			
		}else{
			
			if ( reason != null ){
	
				errorDetail = reason;
			}
	
			errorType	= type;
			
			if ( !recovering ){
				
				if ( force_start ){
					
						// stopIt will clear down force-start so grab the state before
					
					errorFlags = DownloadManager.EF_WAS_FORCE_START;
					
				}else{
					
					errorFlags = 0;
				}
			}
			
			stopIt( DownloadManager.STATE_ERROR, false, false, false );
		}
	}

	public boolean
	filesExist(
		boolean	expected_to_be_allocated )
	{
		if ( !expected_to_be_allocated ){

			if ( !download_manager.isDataAlreadyAllocated()){

				return( false );
			}
		}

		DiskManager dm = getDiskManager();

		if (dm != null) {
			return dm.filesExist();
		}

		fileFacadeSet.makeSureFilesFacadeFilled(false);

		DiskManagerFileInfo[] files = fileFacadeSet.getFiles();

		for (int i = 0; i < files.length; i++) {
			
			DiskManagerFileInfo fileInfo = files[i];
			
			if ( fileInfo.getTorrentFile().isPadFile()){
				
				continue;
			}
			
			if (!fileInfo.isSkipped()){
				
				try {
					long start = SystemTime.getMonotonousTime();

					boolean 	exists = fileInfo.exists();

					File file = fileInfo.getFile(true);

					long elapsed = SystemTime.getMonotonousTime() - start;

					if ( elapsed >= 500 ){

						Debug.out( "Accessing '" + file.getAbsolutePath() + "' in '" + getDisplayName() + "' took " + elapsed + "ms - possibly offline" );
					}

					if ( !exists ){

						// For multi-file torrents, complain if the save directory is missing.
						if (!this.download_manager.getTorrent().isSimpleTorrent()) {
							File save_path = this.download_manager.getAbsoluteSaveLocation();
							if (FileUtil.isAncestorOf(save_path, file) && !save_path.exists()) {
								file = save_path; // We're going to abort very soon, so it's OK to overwrite this.
							}
						}

						setFailed( DownloadManager.ET_FILE_MISSING, MessageText.getString("DownloadManager.error.datamissing") + ": " + file.getAbsolutePath());
						
						return false;

					} else if (fileInfo.getLength() < file.length()) {

							// file may be incremental creation - don't complain if too small

							// don't bitch if the user is happy with this

						if ( !COConfigurationManager.getBooleanParameter("File.truncate.if.too.large")){

							setFailed(MessageText.getString("DownloadManager.error.badsize")
									+ " " + file + "(" + fileInfo.getLength() + "/" + file.length() + ")");


							return false;
						}
					}
				}catch( Throwable e ){
					
					setFailed( "Existance check failed", e );
					
					return false;
				}
			}
		}

		return true;
	}

	public DiskManagerFileInfoSet getDiskManagerFileInfoSet()
	{
		fileFacadeSet.makeSureFilesFacadeFilled(false);

		return fileFacadeSet;
	}

	/**
	 * @deprecated use getDiskManagerFileInfoSet() instead
	 */
   	public DiskManagerFileInfo[]
    getDiskManagerFileInfo()
   	{
   		fileFacadeSet.makeSureFilesFacadeFilled(false);

   		return( fileFacadeSet.getFiles() );
   	}

	protected void
	fileInfoChanged()
	{
		fileFacadeSet.makeSureFilesFacadeFilled(true);
	}

	protected void
	filePrioritiesChanged(List files)
	{
		if (!cached_values_set) {
			fileFacadeSet.makeSureFilesFacadeFilled(false);
		}

		// no need to calculate completeness if there are no DND files and the
		// file being changed is not DND
		if (!cached_has_dnd_files && files.size() == 1 && !((DiskManagerFileInfo)files.get(0)).isSkipped()){
			return;
		}
		// if it's more than one file just do the scan anyway
		fileFacadeSet.makeSureFilesFacadeFilled(false);
		calculateCompleteness( fileFacadeSet.facadeFiles );
		
		disk_manager_pieces_snapshot = null;
	}

	protected void
	calculateCompleteness(
		DiskManagerFileInfo[]	active )
	{
		boolean complete_excluding_dnd = true;

		boolean has_dnd_files = false;

		for (int i = 0; i < active.length; i++) {

			DiskManagerFileInfo file = active[i];

			if ( file.isSkipped()){

				has_dnd_files = true;

			}else if (file.getDownloaded() != file.getLength()) {

				complete_excluding_dnd = false;

			}

			if(has_dnd_files && !complete_excluding_dnd)
				break; // we can bail out early
		}

		cached_complete_excluding_dnd = complete_excluding_dnd;
		cached_has_dnd_files = has_dnd_files;
		cached_values_set = true;
		
		long flags = (cached_complete_excluding_dnd ? STATE_FLAG_COMPLETE_NO_DND : 0) |
								 (cached_has_dnd_files ? STATE_FLAG_HASDND : 0);
		download_manager_state.setLongParameter(DownloadManagerState.PARAM_DND_FLAGS, flags);
	}

	/**
	 * Determine if the download is complete, excluding DND files.  This
	 * function is mostly cached when there is a DiskManager.
	 *
	 * @return completion state
	 */
	protected boolean isDownloadComplete(boolean bIncludeDND) {
		if (!cached_values_set) {
			fileFacadeSet.makeSureFilesFacadeFilled(false);
		}

		// The calculate from stats doesn't take into consideration DND
		// So, if we have no DND files, use calculation from stats, which
		// remembers things like whether the file was once complete
		if (!cached_has_dnd_files) {
			return stats.getRemaining() == 0;
		}

		// We have DND files.  If we have an existing diskmanager, then it
		// will have better information than the stats object.
		DiskManager dm = getDiskManager();

		//System.out.println(dm + ":" + (dm == null ? "null" : dm.getState() + ";" + dm.getRemainingExcludingDND()));
		if (dm != null) {

			int dm_state = dm.getState();

			if (dm_state == DiskManager.READY) {
				long remaining = bIncludeDND ? dm.getRemaining()
						: dm.getRemainingExcludingDND();
				return remaining == 0;
			}
		}

		// No DiskManager or it's in a bad state for us.
		// Assumed: We have DND files
		if (bIncludeDND) {
			// Want to include DND files in calculation, which there some, which
			// means completion MUST be false
			return false;
		}

		// Have DND files, bad DiskManager, and we don't want to include DND files
		return cached_complete_excluding_dnd;
	}

	protected PEPeerManager
	getPeerManager()
	{
		return( peer_manager );
	}

	protected DiskManager
	getDiskManager()
	{
		return( disk_manager_use_accessors );
	}
	
	public DiskManagerPiece[] 
	getDiskManagerPiecesSnapshot()
	{
		synchronized( disk_manager_pieces_snapshot_lock ){
		
			if ( disk_manager_pieces_snapshot == null ){
				
				disk_manager_pieces_snapshot = DiskManagerUtil.getDiskManagerPiecesSnapshot( download_manager );			
			}
			
			return( disk_manager_pieces_snapshot );
		}
	}

	protected String
	getErrorDetail()
	{
		return( errorDetail );
	}

	protected int
	getErrorType()
	{
		return( errorType );
	}
	
	protected int
	getErrorFlags()
	{
		return( errorFlags );
	}

 	protected void
  	setDiskManager(
  		DiskManager			new_disk_manager,
  		DiskManagerListener	new_disk_manager_listener )
  	{
 		if ( new_disk_manager != null ){

 			new_disk_manager.setPieceCheckingEnabled( piece_checking_enabled );
 		}

  	 	try{
	  		disk_listeners_mon.enter();

	  		DiskManager	old_disk_manager = disk_manager_use_accessors;

	  			// remove any old listeners in case the diskmanager is still running async

	  		if ( old_disk_manager != null && disk_manager_listener_use_accessors != null ){

	  			old_disk_manager.removeListener( disk_manager_listener_use_accessors );
	  		}

	  		disk_manager_use_accessors			= new_disk_manager;
	  		disk_manager_listener_use_accessors	= new_disk_manager_listener;

			if ( new_disk_manager != null ){

	 			new_disk_manager.addListener( new_disk_manager_listener );
	 		}

	  			// whether going from none->active or the other way, indicate that the file info
	  			// has changed

	  		fileInfoChanged();

	  		if ( new_disk_manager == null && old_disk_manager != null ){

	  			disk_listeners.dispatch( LDT_DL_REMOVED, old_disk_manager );

	  		}else if ( new_disk_manager != null && old_disk_manager == null ){

	  			disk_listeners.dispatch( LDT_DL_ADDED, new_disk_manager );

	  		}else{

	  			Debug.out( "inconsistent DiskManager state - " + new_disk_manager + "/" + old_disk_manager  );
	  		}

	  	}finally{

	  		disk_listeners_mon.exit();
	  	}
  	}

	public void
	addDiskListener(
		DownloadManagerDiskListener	listener )
	{
	 	try{
	  		disk_listeners_mon.enter();

	  		disk_listeners.addListener( listener );

	  		DiskManager	dm = getDiskManager();

			if ( dm != null ){

				disk_listeners.dispatch( listener, LDT_DL_ADDED, dm );
			}
	  	}finally{

	  		disk_listeners_mon.exit();
	  	}
	}

	public void
	removeDiskListener(
		DownloadManagerDiskListener	listener )
	{
	 	try{
	  		disk_listeners_mon.enter();

	  		disk_listeners.removeListener( listener );

	 	}finally{

	  		disk_listeners_mon.exit();
	  	}
	}

	public long getDiskListenerCount() {
		return disk_listeners.size();
	}

	@Override
	public String
	getDisplayName()
	{
		return( download_manager.getDisplayName());
	}

	@Override
	public int
	getEffectiveUploadRateLimitBytesPerSecond()
	{
		return( download_manager.getEffectiveUploadRateLimitBytesPerSecond());
	}

	@Override
	public int
	getUploadRateLimitBytesPerSecond()
	{
		return( stats.getUploadRateLimitBytesPerSecond());
	}

	@Override
	public void
	setUploadRateLimitBytesPerSecond( int b )
	{
		stats.setUploadRateLimitBytesPerSecond( b );
	}
	
	@Override
	public int
	getDownloadRateLimitBytesPerSecond()
	{
		return( stats.getDownloadRateLimitBytesPerSecond());
	}
	
	@Override
	public void
	setDownloadRateLimitBytesPerSecond( int b )
	{
		stats.setDownloadRateLimitBytesPerSecond( b );
	}

		// these per-download rates are not easy to implement as we either have per-peer limits or global limits, with the download-limits being implemented
		// by adding them to all peers as peer-limits. So for the moment we stick with global (non-lan) limits

	@Override
	public int
	getPermittedBytesToReceive()
	{
		return( NetworkManager.getSingleton().getRateHandler( false, false ).getCurrentNumBytesAllowed()[0]);
	}

	@Override
	public void
	permittedReceiveBytesUsed(
		int bytes )
	{
		NetworkManager.getSingleton().getRateHandler( false, false ).bytesProcessed( bytes, 0 );
	}

	@Override
	public int
	getPermittedBytesToSend()
	{
		return( NetworkManager.getSingleton().getRateHandler( true, false ).getCurrentNumBytesAllowed()[0]);
	}

	@Override
	public void
	permittedSendBytesUsed(
		int bytes )
	{
		NetworkManager.getSingleton().getRateHandler( true, false ).bytesProcessed( bytes, 0 );
	}

	@Override
	public int
	getMaxUploads()
	{
		return( download_manager.getEffectiveMaxUploads());
	}

	@Override
	public int[]
	getMaxConnections()
	{
		int[]	result;

		if ( download_manager.isMaxConnectionsWhenSeedingEnabled() && isStateSeeding()){

			result = download_manager.getMaxConnectionsWhenSeeding( getEnabledNetworks().length > 1 );

		}else{

			result = download_manager.getMaxConnections( getEnabledNetworks().length > 1 );
		}

		return( result );
	}

	@Override
	public int[]
	getMaxSeedConnections()
	{
		return( download_manager.getMaxSeedConnections( getEnabledNetworks().length > 1 ));
	}

	@Override
	public int
	getUploadPriority()
	{
		return( download_manager.getEffectiveUploadPriority());
	}

	@Override
	public int
	getExtendedMessagingMode()
	{
		return( download_manager.getExtendedMessagingMode());
	}


	@Override
	public boolean
	isPeerExchangeEnabled()
	{
		return( download_manager_state.isPeerSourceEnabled( PEPeerSource.PS_OTHER_PEER ));
	}

	@Override
	public int
	getCryptoLevel()
	{
		return( download_manager.getCryptoLevel());
	}

	@Override
	public boolean
	isPeriodicRescanEnabled()
	{
		return( download_manager_state.getFlag( DownloadManagerState.FLAG_SCAN_INCOMPLETE_PIECES ));
	}

	@Override
	public TRTrackerScraperResponse
	getTrackerScrapeResponse()
	{
		return( download_manager.getTrackerScrapeResponse());
	}

	@Override
	public String
	getTrackerClientExtensions()
	{
		return( download_manager_state.getTrackerClientExtensions());
	}

	@Override
	public void
	setTrackerRefreshDelayOverrides(
		int	percent )
	{
		download_manager.setTrackerRefreshDelayOverrides( percent );
	}

	@Override
	public boolean
	isNATHealthy()
	{
		return( ((Integer)download_manager.getNATStatus()[0]) == ConnectionManager.NAT_OK );
	}

	@Override
	public boolean
	isMetadataDownload()
	{
		return( download_manager_state.getFlag( DownloadManagerState.FLAG_METADATA_DOWNLOAD ));
	}

	@Override
	public int
	getTorrentInfoDictSize()
	{
		return( md_info_dict_size );
	}

	@Override
	public byte[]
	getTorrentInfoDict(
		PEPeer		peer )
	{
		try{
			String ip = peer.getIp();

			synchronized( md_info_peer_history ){

				int	now_secs = (int)( SystemTime.getMonotonousTime()/1000 );

				int[]	stats = md_info_peer_history.get( ip );

				if ( stats == null ){

					stats = new int[]{ now_secs, 0 };

					md_info_peer_history.put( ip, stats );
				}

				if ( now_secs - stats[0] > 5*60 ){

					stats[1] = 16*1024;

				}else{

					int	bytes = stats[1];

					if ( bytes >= md_info_dict_size*3 ){

						return( null );
					}

					stats[1] = bytes + 16*1024;
				}
			}

			byte[] data = md_info_dict_ref.get();

			if ( data == null ){

				TOTorrent torrent = download_manager.getTorrent();

				data = BEncoder.encode((Map)torrent.serialiseToMap().get( "info" ));

				md_info_dict_ref = new WeakReference<>(data);
			}

			return( data );

		}catch( Throwable e ){

			return( null );
		}
	}

	@Override
	public void
	addPeer(
		PEPeer	peer )
	{
		download_manager.addPeer( peer );
	}

	@Override
	public void
	removePeer(
		PEPeer	peer )
	{
		download_manager.removePeer( peer );
	}

	@Override
	public void
	addPiece(
		PEPiece	piece )
	{
		download_manager.addPiece( piece );
	}

	@Override
	public void
	removePiece(
		PEPiece	piece )
	{
		download_manager.removePiece( piece );
	}

	@Override
	public void
	discarded(
		PEPeer		peer,
		int			bytes )
	{
		if ( global_stats != null ){

			global_stats.discarded( bytes );
		}
	}

	@Override
	public void
	protocolBytesReceived(
		PEPeer		peer,
		int			bytes )
	{
		if ( global_stats != null ){

			global_stats.protocolBytesReceived( bytes, peer.isLANLocal());
		}
	}

	@Override
	public void
	dataBytesReceived(
		PEPeer		peer,
		int			bytes )
	{
		if ( global_stats != null ){

			global_stats.dataBytesReceived( bytes, peer.isLANLocal());
		}
	}

	@Override
	public void
	protocolBytesSent(
		PEPeer		peer,
		int			bytes )
	{
		if ( global_stats != null ){

			global_stats.protocolBytesSent( bytes, peer.isLANLocal());
		}
	}

	@Override
	public void
	dataBytesSent(
		PEPeer		peer,
		int			bytes )
	{
		if ( global_stats != null ){

			global_stats.dataBytesSent( bytes, peer.isLANLocal());
		}
	}

	@Override
	public int getPosition() {
		return download_manager.getPosition();
	}

	@Override
	public void
	tick(
		long	mono_now,
		int		tick_count )
	{
		stats.timerTick( tick_count );
	}

	@Override
	public void
	statsRequest(
		PEPeer 		originator,
		Map 		request,
		Map			reply )
	{
		GlobalManager	gm = download_manager.getGlobalManager();

		gm.statsRequest( request, reply );

		Map	info = new HashMap();

		reply.put( "dl", info );

		try{
			info.put( "u_lim", new Long( getUploadRateLimitBytesPerSecond()));
			info.put( "d_lim", new Long( getDownloadRateLimitBytesPerSecond()));

			info.put( "u_rate", new Long( stats.getProtocolSendRate() + stats.getDataSendRate()));
			info.put( "d_rate", new Long( stats.getProtocolReceiveRate() + stats.getDataReceiveRate()));

			info.put( "u_slot", new Long( getMaxUploads()));
			info.put( "c_max", new Long( getMaxConnections()[0]));

			info.put( "c_leech", new Long( download_manager.getNbPeers()));
			info.put( "c_seed", new Long( download_manager.getNbSeeds()));

			PEPeerManager pm = peer_manager;

			if ( pm != null ){

				info.put( "c_rem", pm.getNbRemoteTCPConnections());
				info.put( "c_rem_utp", pm.getNbRemoteUTPConnections());
				info.put( "c_rem_udp", pm.getNbRemoteUDPConnections());

				List<PEPeer> peers = pm.getPeers();

				List<Long>	slot_up = new ArrayList<>();

				info.put( "slot_up", slot_up );

				for ( PEPeer p: peers ){

					if ( !p.isChokedByMe()){

						long up = p.getStats().getDataSendRate() + p.getStats().getProtocolSendRate();

						slot_up.add( up );
					}
				}
			}
		}catch( Throwable e ){
		}
	}

	@Override
	public void
	addHTTPSeed(
		String	address,
		int		port )
	{
		ExternalSeedPlugin	plugin = getExternalSeedPlugin();

		try{
			if ( plugin != null ){

				Map config = new HashMap();

				List urls = new ArrayList();

				String	seed_url = "http://" + UrlUtils.convertIPV6Host(address) + ":" + port + "/webseed";

				urls.add( seed_url.getBytes());

				config.put("httpseeds", urls);

				Map params = new HashMap();

				params.put( "supports_503", new Long(0));
				params.put( "transient", new Long(1));

				config.put("httpseeds-params", params);

				List<ExternalSeedPeer> new_seeds = plugin.addSeed( com.biglybt.pifimpl.local.download.DownloadManagerImpl.getDownloadStatic( download_manager ), config);

				if ( new_seeds.size() > 0 ){

					List<ExternalSeedPeer> to_remove = new ArrayList<>();

					synchronized( http_seeds ){

						http_seeds.addAll( new_seeds );

						while( http_seeds.size() > HTTP_SEEDS_MAX ){

							ExternalSeedPeer x = http_seeds.removeFirst();

							to_remove.add( x );
						}
					}

					for (ExternalSeedPeer peer: to_remove ){

						peer.remove();
					}
				}
			}
		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}
	}

	@Override
	public void
	priorityConnectionChanged(
		boolean	added )
	{
		synchronized( this ){

			if ( added ){

				priority_connection_count++;

			}else{

				priority_connection_count--;
			}
		}
	}

	@Override
	public boolean
	hasPriorityConnection()
	{
		synchronized( this ){

			return( priority_connection_count > 0 );
		}
	}

	@Override
	public String
	getDescription()
	{
		return( download_manager.getDisplayName());
	}

	@Override
	public LogRelation
	getLogRelation()
	{
		return( this );
	}

	@Override
	public String
	getRelationText()
	{
		return( download_manager.getRelationText());
	}

	@Override
	public Object[]
	getQueryableInterfaces()
	{
		List	interfaces = new ArrayList();

		Object[]	intf = download_manager.getQueryableInterfaces();

		Collections.addAll(interfaces, intf);

		interfaces.add( download_manager );

		DiskManager	dm = getDiskManager();

		if ( dm != null ){

			interfaces.add( dm );
		}

		return( interfaces.toArray());
	}

	protected class FileInfoFacadeSet implements DiskManagerFileInfoSet {

		DiskManagerFileInfoSet delegate;
		fileInfoFacade[] facadeFiles = new fileInfoFacade[0];	// default before torrent avail

		@Override
		public void load(int[] priorities, boolean[] skipped){
			delegate.load(priorities, skipped);
		}
		
		@Override
		public DiskManagerFileInfo[] getFiles() {
			return facadeFiles;
		}

		@Override
		public int nbFiles() {
			if (delegate == null) {
				return 0;
			}
			return delegate.nbFiles();
		}

		@Override
		public void setPriority(int[] newPriorities) {
			delegate.setPriority(newPriorities);
		}

		@Override
		public void setSkipped(boolean[] toChange, boolean setSkipped) {
			delegate.setSkipped(toChange, setSkipped);
		}

		@Override
		public boolean[] setStorageTypes(boolean[] toChange, int newStorageType, boolean force ) {
			return delegate.setStorageTypes(toChange, newStorageType, force );
		}

		/** XXX Don't call me, call makeSureFilesFacadeFilled() */
		protected void fixupFileInfo(fileInfoFacade[] info) {

			// too early in initialisation sequence to action this - it'll get reinvoked later anyway
			if (info.length == 0) return;

			final List<DiskManagerFileInfo> delayed_prio_changes = new ArrayList<>(0);

			try {
				facade_mon.enter();
				if (files_facade_destroyed)	return;

				DiskManager dm = DownloadManagerController.this.getDiskManager();
				DiskManagerFileInfoSet active = null;

				if (dm != null) {
					int dm_state = dm.getState();
					// grab the live file info if available
					if (dm_state == DiskManager.CHECKING || dm_state == DiskManager.READY)
						active = dm.getFileSet();

				}
				if (active == null) {
					final boolean[] initialising = { true };
					// chance of recursion with this listener as the file-priority-changed is triggered
					// synchronously during construction and this can cause a listener to reenter the
					// incomplete fixup logic here + instantiate new skeletons.....
					try {
						skeleton_builds++;
						if (skeleton_builds % 1000 == 0) {
							Debug.outNoStack("Skeleton builds: " + skeleton_builds);
						}
						active = DiskManagerFactory.getFileInfoSkeleton(download_manager, new DiskManagerListener() {
							@Override
							public void stateChanged(DiskManager dm, int oldState, int newState) {}

							@Override
							public void filePriorityChanged(DiskManager dm, List<DiskManagerFileInfo> files) {
								if (initialising[0]) {
									delayed_prio_changes.addAll(files);
								} else {
									download_manager.informPriorityChange(files);
								}
							}
							@Override
							public void pieceDoneChanged(DiskManager dm, DiskManagerPiece piece) {}
							
							@Override
							public void fileCompleted(DiskManager dm, DiskManagerFileInfo file){
								// nothing to do here
							}
						});
					} finally {
						initialising[0] = false;
					}
					calculateCompleteness(active.getFiles());
				}

				DiskManagerFileInfo[] activeFiles = active.getFiles();

				for (int i = 0; i < info.length; i++)
					info[i].setDelegate(activeFiles[i]);

				delegate = active;

			} finally {
				facade_mon.exit();
			}

			fileFacadeSet.facadeFiles = info;
			
			download_manager.informPrioritiesChange(delayed_prio_changes);

			delayed_prio_changes.clear();
		}

		void makeSureFilesFacadeFilled(boolean refresh) {
			if (!bInitialized) return; // too early

			if (facadeFiles.length == 0) {
				fileInfoFacade[] newFacadeFiles = new fileInfoFacade[download_manager.getTorrent() == null
						? 0 : download_manager.getTorrent().getFiles().length];

				for (int i = 0; i < newFacadeFiles.length; i++)
					newFacadeFiles[i] = new fileInfoFacade();

				// no need to set facadeFiles, it gets set to newFacadeFiles in fixup
				fileFacadeSet.fixupFileInfo(newFacadeFiles);
			} else if (refresh) {
				fixupFileInfo(facadeFiles);
			}
		}


		protected void destroyFileInfo() {
			try {
				facade_mon.enter();
				if (fileFacadeSet == null || files_facade_destroyed)
					return;

				files_facade_destroyed = true;

				for (int i = 0; i < facadeFiles.length; i++)
					facadeFiles[i].close();
			} finally {
				facade_mon.exit();
			}
		}
	}

	protected class
	fileInfoFacade
		implements DiskManagerFileInfo
	{
		private volatile DiskManagerFileInfo		delegate;

		private List<DiskManagerFileInfoListener>	listeners;

		protected
		fileInfoFacade()
		{
		}

		protected void
		setDelegate(
			DiskManagerFileInfo		new_delegate )
		{
			DiskManagerFileInfo old_delegate;

			List<DiskManagerFileInfoListener>	existing_listeners;

			synchronized( this ){

				if ( new_delegate == delegate ){

					return;
				}

				old_delegate = delegate;

				delegate = new_delegate;

				if ( listeners == null ){

					existing_listeners = null;

				}else{

					existing_listeners = new ArrayList<>(listeners);
				}
			}

			if ( old_delegate != null ){

				old_delegate.close();
			}

	 			// transfer any existing listeners across

	   		if ( existing_listeners != null ){

	   			for (int i=0;i<existing_listeners.size();i++){

	   				new_delegate.addListener( existing_listeners.get(i));
	   			}
	   		}
		}

		@Override
		public void
		setPriority(
			int b )
		{
			delegate.setPriority(b);
		}

		@Override
		public void
		setSkipped(
			boolean b)
		{
			delegate.setSkipped(b);
		}

		@Override
		public Boolean 
		isSkipping()
		{
			return( delegate.isSkipping());
		}

		@Override
		public boolean
		setLink(
			File	link_destination,
			boolean	no_delete )
		{
			return( delegate.setLink( link_destination, no_delete ));
		}
		
		@Override
		public String getLastError(){
			return( delegate.getLastError());
		}

		@Override
		public boolean
		setLinkAtomic(
			File		link_destination,
			boolean		no_delete )
		{
			return( delegate.setLinkAtomic( link_destination, no_delete ));
		}
		
		@Override
		public boolean
		setLinkAtomic(
			File						link_destination,
			boolean						no_delete,
			FileUtil.ProgressListener 	pl )
		{
			return( delegate.setLinkAtomic( link_destination, no_delete, pl ));
		}
		

		@Override
		public File
		getLink()
		{
			return( delegate.getLink());
		}

		@Override
		public boolean
		setStorageType(
			int		type,
			boolean	force )
		{
			return( delegate.setStorageType( type, force ));
		}

		@Override
		public int
		getStorageType()
		{
			return( delegate.getStorageType());
		}


		@Override
		public int
		getAccessMode()
		{
			return( delegate.getAccessMode());
		}

		@Override
		public long
		getDownloaded()
		{
			return( delegate.getDownloaded());
		}
		
		@Override
		public long getLastModified()
		{
			return( delegate.getLastModified());
		}

		@Override
		public String
		getExtension()
		{
			return( delegate.getExtension());
		}

		@Override
		public int
		getFirstPieceNumber()
		{
			return( delegate.getFirstPieceNumber());
		}

		@Override
		public int
		getLastPieceNumber()
		{
			return( delegate.getLastPieceNumber());
		}

		@Override
		public long
		getLength()
		{
			return( delegate.getLength());
		}

		@Override
		public int
		getNbPieces()
		{
			return( delegate.getNbPieces());
		}

		@Override
		public int
		getPriority()
		{
			return( delegate.getPriority());
		}

		@Override
		public boolean
		isSkipped()
		{
			return( delegate.isSkipped());
		}

		@Override
		public boolean exists(){
			return( delegate.exists());
		}

		@Override
		public int
		getIndex()
		{
			return( delegate.getIndex());
		}

		@Override
		public DiskManager
		getDiskManager()
		{
			return( delegate.getDiskManager());
		}

		@Override
		public DownloadManager
		getDownloadManager()
		{
			return( download_manager );
		}

		@Override
		public File
		getFile( boolean follow_link )
		{
			return( delegate.getFile( follow_link ));
		}

		@Override
		public TOTorrentFile
		getTorrentFile()
		{
			return( delegate.getTorrentFile());
		}

		@Override
		public void
		flushCache()

			throws	Exception
		{
			try{
				facade_mon.enter();

				delegate.flushCache();

			}finally{

				facade_mon.exit();
			}
		}

		@Override
		public DirectByteBuffer
		read(
			long	offset,
			int		length )

			throws IOException
		{
			try{
				facade_mon.enter();

				return( delegate.read( offset, length ));

			}finally{

				facade_mon.exit();
			}
		}

		@Override
		public int
		getReadBytesPerSecond()
		{
			return( delegate.getReadBytesPerSecond());
		}

		@Override
		public int
		getWriteBytesPerSecond()
		{
			return( delegate.getWriteBytesPerSecond());
		}

		@Override
		public long
		getETA()
		{
			return( delegate.getETA());
		}

		@Override
		public void recheck()
		{
			delegate.recheck();
		}
		
		@Override
		public void
		close()
		{
			try{
				facade_mon.enter();

				delegate.close();

			}finally{

				facade_mon.exit();
			}
		}

		@Override
		public void
		addListener(
			DiskManagerFileInfoListener	listener )
		{
			DiskManagerFileInfo existing_delegate;

			synchronized( this ){

				if ( listeners == null ){

					listeners = new ArrayList();
				}

				listeners.add( listener );

				existing_delegate = delegate;
			}

			if ( existing_delegate != null ){

				existing_delegate.addListener( listener );
			}
		}

		@Override
		public void
		removeListener(
			DiskManagerFileInfoListener	listener )
		{
			DiskManagerFileInfo existing_delegate;

			synchronized( this ){

				listeners.remove( listener );

				existing_delegate = delegate;
			}

			if ( existing_delegate != null ){

				existing_delegate.removeListener( listener );
			}
		}
	}

	public void 
	generateEvidence(
		IndentWriter writer,
		boolean		full )
	{
		writer.println("DownloadManager Controller:");

		writer.indent();
		try {
			writer.println("cached info: complete w/o DND="
					+ cached_complete_excluding_dnd + "; hasDND? " + cached_has_dnd_files);

			writer.println("Complete w/DND? " + isDownloadComplete(true)
					+ "; w/o DND? " + isDownloadComplete(false));

			writer.println("filesFacade length: " + fileFacadeSet.nbFiles());

			if (force_start) {
				writer.println("Force Start");
			}

			writer.println("FilesExist? " + filesExist(download_manager.isDataAlreadyAllocated()));
			
			if ( full ){
				
				DiskManagerFileInfo[] files = fileFacadeSet.getFiles();
				
				writer.println( "Files" );
				
				try{
					writer.indent();
					
					for ( DiskManagerFileInfo file: files ){
						
						File f = file.getFile( true );
								
						writer.println( file.getIndex() + ": " + f + ", exists=" + f.exists());
					}
				}finally{
					
					writer.exdent();
				}
				
				TOTorrent torrent = download_manager.getTorrent();
				
				if ( torrent != null ){
				
					FMFileManagerFactory.getSingleton().generateEvidence( writer, torrent );
				}
			}

		} finally {
			writer.exdent();
		}
	}

	public class ForceRecheckDiskManagerListener
		implements DiskManagerListener
	{
		private final boolean wasForceStarted;

		private final int start_state;

		private final ForceRecheckListener l;

		public 
		ForceRecheckDiskManagerListener(
			boolean 				wasForceStarted,
			int 					start_state, 
			ForceRecheckListener 	l ) 
		{
			this.wasForceStarted = wasForceStarted;
			this.start_state = start_state;
			this.l = l;
		}

		@Override
		public void stateChanged(DiskManager dm, int oldDMState, int newDMState) {
			try {
				control_mon.enter();

				DiskManager latest_dm = getDiskManager();
				
				if ( latest_dm == null || latest_dm != dm ) {

					// already closed down via stop

					download_manager.setAssumedComplete(false);

					if ( l != null ){
						
						l.forceRecheckComplete(download_manager, dm.getRecheckCancelled());
					}

					return;
				}
			} finally {

				control_mon.exit();
			}


			if (newDMState == DiskManager.CHECKING) {

				fileFacadeSet.makeSureFilesFacadeFilled(true);
			}

			if (newDMState == DiskManager.READY || newDMState == DiskManager.FAULTY) {

				force_start = wasForceStarted;

				stats.recalcDownloadCompleteBytes();

				if (newDMState == DiskManager.READY) {

					try {
						boolean only_seeding = false;
						boolean update_only_seeding = false;

						try {
							control_mon.enter();

							DiskManager latest_dm = getDiskManager();

							if (latest_dm != null && latest_dm == dm ){

								dm.stop(false);

								only_seeding = dm.getRemainingExcludingDND() == 0;

								update_only_seeding = true;

								setDiskManager(null, null);

								if (start_state == DownloadManager.STATE_ERROR) {

									setState(DownloadManager.STATE_STOPPED, false);

								} else {

									setState(start_state, false);
								}
							}
						} finally {

							control_mon.exit();

							download_manager.informStateChanged();
						}

						// careful here, don't want to update seeding while holding monitor
						// as potential deadlock

						if (update_only_seeding) {

							download_manager.setAssumedComplete(only_seeding);
						}

					} catch (Exception e) {

						setFailed("Resume data save fails", e );
					}
				} else { // Faulty

					try {
						control_mon.enter();

						DiskManager latest_dm = getDiskManager();

						if (latest_dm != null && latest_dm == dm ) {

							dm.stop(false);

							setDiskManager(null, null);

							setFailed( dm );
						}
					} finally {

						control_mon.exit();
					}

					download_manager.setAssumedComplete(false);
				}
				
				if ( l != null ){
					
					l.forceRecheckComplete(download_manager, dm.getRecheckCancelled());
				}
			}
		}
		
		@Override
		public void filePriorityChanged(DiskManager	dm, List<DiskManagerFileInfo> files) {
			download_manager.informPriorityChange(files);
		}

		@Override
		public void pieceDoneChanged(DiskManager dm, DiskManagerPiece piece) {
		}
		
		@Override
		public void fileCompleted(DiskManager dm, DiskManagerFileInfo file){
			download_manager.informFileCompletionChange(file);
		}
	}

	private class DiskManagerListener_Default implements DiskManagerListener {
		private final boolean open_for_seeding;

		public DiskManagerListener_Default(boolean open_for_seeding) {
			this.open_for_seeding = open_for_seeding;
		}

		@Override
		public void
		stateChanged(
			DiskManager		dm, 
			int 			oldDMState,
			int				newDMState )
		{
			try{
				control_mon.enter();

				DiskManager latest_dm = getDiskManager();

				if ( latest_dm == null || latest_dm != dm ){

					// already been cleared down

					return;
				}

			}finally{

				control_mon.exit();
			}

			try{
				if ( newDMState == DiskManager.FAULTY ){

					setFailed( dm );
				}

				if ( oldDMState == DiskManager.CHECKING && newDMState != DiskManager.CHECKING ){

					// good time to trigger minimum file info fixup as the disk manager's
					// files are now in a good state

					fileFacadeSet.makeSureFilesFacadeFilled(true);

					stats.recalcDownloadCompleteBytes();

					download_manager.setAssumedComplete(isDownloadComplete(false));
				}

				if ( newDMState == DiskManager.READY ){

					int	completed = stats.getDownloadCompleted(false);

					if ( 	stats.getTotalDataBytesReceived() == 0 &&
							stats.getTotalDataBytesSent() == 0 &&
							stats.getSecondsDownloading() == 0 ){

						if ( completed < 1000 ){

							if ( open_for_seeding ){

								setFailed( "File check failed" );

								download_manager_state.clearResumeData();

							}else{

								// make up some sensible "downloaded" figure for torrents that have been re-added to the client
								// and resumed


								// assume downloaded = uploaded, optimistic but at least results in
								// future share ratios relevant to amount up/down from now on
								// see bug 1077060

								long	amount_downloaded = (completed*dm.getTotalLength())/1000;

								boolean	sr1 = COConfigurationManager.getBooleanParameter("StartStopManager_bAddForDownloadingSR1");

								if ( sr1 ){
								
									stats.setSavedDownloadedUploaded( amount_downloaded, amount_downloaded );
									
								}else{
									
									stats.setSavedDownloadedUploaded( amount_downloaded, 0 );

								}
							}
						}else{
							// see GlobalManager for comment on this

							int	dl_copies = COConfigurationManager.getIntParameter("StartStopManager_iAddForSeedingDLCopyCount");

							if ( dl_copies > 0 ){

								stats.setSavedDownloadedUploaded( download_manager.getSize()*dl_copies, stats.getTotalDataBytesSent());
							}

							download_manager_state.setFlag( DownloadManagerState.FLAG_ONLY_EVER_SEEDED, true );
						}
					}

					/* all initialization should be done here (Disk- and DownloadManager).
					 * assume this download is complete and won't recieve any modifications until it is stopped again
					 * or the user fiddles on the knobs
					 * discard fluff once tentatively, will save memory for many active, seeding torrent-cases
					 */
					if ( completed == 1000 ){
						download_manager_state.discardFluff();
					}
				}
			}finally{

				download_manager.informStateChanged();
			}
		}

		@Override
		public void 
		filePriorityChanged(
			DiskManager dm, 
			List<DiskManagerFileInfo> files )
		{
			download_manager.informPriorityChange( files );
		}
		
		@Override
		public void
		pieceDoneChanged(
			DiskManager			dm, 
			DiskManagerPiece	piece )
		{
			download_manager.informPieceDoneChanged( piece );
		}
		
		@Override
		public void 
		fileCompleted(
			DiskManager			dm, 
			DiskManagerFileInfo file)
		{
			download_manager.informFileCompletionChange( file );
		}
	}
}
