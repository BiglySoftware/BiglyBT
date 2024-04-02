/*
 * File    : DownloadManagerImpl.java
 * Created : 19-Oct-2003
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

package com.biglybt.core.download.impl;
/*
 * Created on 30 juin 2003
 *
 */

import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreOperation;
import com.biglybt.core.CoreOperationTask;
import com.biglybt.core.CoreOperationTask.ProgressCallback;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.config.impl.TransferSpeedValidator;
import com.biglybt.core.disk.*;
import com.biglybt.core.disk.impl.DiskManagerImpl;
import com.biglybt.core.disk.impl.DiskManagerUtil;
import com.biglybt.core.disk.impl.piecemapper.DMPieceList;
import com.biglybt.core.disk.impl.piecemapper.DMPieceMapEntry;
import com.biglybt.core.download.*;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerEvent;
import com.biglybt.core.global.GlobalManagerStats;
import com.biglybt.core.internat.LocaleTorrentUtil;
import com.biglybt.core.internat.LocaleUtilDecoder;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.*;
import com.biglybt.core.networkmanager.LimitedRateGroup;
import com.biglybt.core.networkmanager.NetworkManager;
import com.biglybt.core.networkmanager.Transport;
import com.biglybt.core.networkmanager.impl.tcp.TCPNetworkManager;
import com.biglybt.core.peer.*;
import com.biglybt.core.peermanager.PeerManagerRegistration;
import com.biglybt.core.peermanager.control.PeerControlSchedulerFactory;
import com.biglybt.core.tag.Taggable;
import com.biglybt.core.tag.TaggableResolver;
import com.biglybt.core.torrent.*;
import com.biglybt.core.tracker.AllTrackersManager;
import com.biglybt.core.tracker.AllTrackersManager.AllTrackers;
import com.biglybt.core.tracker.AllTrackersManager.AllTrackersTracker;
import com.biglybt.core.tracker.TrackerPeerSource;
import com.biglybt.core.tracker.TrackerPeerSourceAdapter;
import com.biglybt.core.tracker.client.*;
import com.biglybt.core.util.*;
import com.biglybt.core.util.DataSourceResolver.DataSourceImporter;
import com.biglybt.core.util.DataSourceResolver.ExportedDataSource;
import com.biglybt.core.util.FileUtil.ProgressListener;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.clientid.ClientIDGenerator;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.Download.SeedingRank;
import com.biglybt.pif.download.DownloadAnnounceResult;
import com.biglybt.pif.download.DownloadScrapeResult;
import com.biglybt.pif.download.savelocation.SaveLocationChange;
import com.biglybt.pif.network.ConnectionManager;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.pifimpl.local.clientid.ClientIDManagerImpl;
import com.biglybt.pifimpl.local.download.DownloadImpl;
import com.biglybt.plugin.extseed.ExternalSeedPlugin;
import com.biglybt.plugin.tracker.dht.DHTTrackerPlugin;
import com.biglybt.plugin.tracker.local.LocalTrackerPlugin;

import java.io.File;
import java.io.FileFilter;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.*;

/**
 * @author Olivier
 *
 */

public class
DownloadManagerImpl
	extends LogRelation
	implements DownloadManager, Taggable, DataSourceResolver.ExportableDataSource, DiskManagerUtil.MoveTaskAapter
{
	private final static long SCRAPE_DELAY_ERROR_TORRENTS = 1000 * 60 * 60 * 2;// 2 hrs
	private final static long SCRAPE_DELAY_STOPPED_TORRENTS = 1000 * 60 * 60;  // 1 hr

	private final static long SCRAPE_INITDELAY_ERROR_TORRENTS = 1000 * 60 * 10;
	private final static long SCRAPE_INITDELAY_STOPPED_TORRENTS = 1000 * 60 * 3;

	private static int 		upload_when_busy_min_secs;
	private static int 		max_connections_npp_extra;
	private static int		default_light_seeding_status;
	
	
	private static final ClientIDManagerImpl client_id_manager = ClientIDManagerImpl.getSingleton();

	static{
		COConfigurationManager.addAndFireParameterListeners(
			new String[]{
				"max.uploads.when.busy.inc.min.secs",
				"Non-Public Peer Extra Connections Per Torrent",
				"Enable Light Seeding",
			},
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					String name )
				{
					upload_when_busy_min_secs = COConfigurationManager.getIntParameter( "max.uploads.when.busy.inc.min.secs" );

					max_connections_npp_extra = COConfigurationManager.getIntParameter( "Non-Public Peer Extra Connections Per Torrent" );
					
					default_light_seeding_status = COConfigurationManager.getBooleanParameter( "Enable Light Seeding" )?1:2;
				}
			});
	}

	private static final String CFG_MOVE_COMPLETED_TOP = "Newly Seeding Torrents Get First Priority";
		// DownloadManager listeners

	private static final int LDT_STATECHANGED			= 1;
	private static final int LDT_DOWNLOADCOMPLETE		= 2;
	private static final int LDT_COMPLETIONCHANGED 		= 3;
	private static final int LDT_POSITIONCHANGED 		= 4;
	private static final int LDT_FILEPRIORITYCHANGED 	= 5;
	private static final int LDT_FILELOCATIONCHANGED 	= 6;

	private static Object 			port_init_lock = new Object();
	
	private final AEMonitor	listeners_mon	= new AEMonitor( "DM:DownloadManager:L" );


	static final ListenerManager<DownloadManagerListener>	listeners_aggregator 	= ListenerManager.createAsyncManager(
			"DM:ListenAggregatorDispatcher",
			new ListenerManagerDispatcher<DownloadManagerListener>()
			{
				@Override
				public void
				dispatch(
					DownloadManagerListener		listener,
					int							type,
					Object						_value )
				{
					Object[]	value = (Object[])_value;

					DownloadManagerImpl	dm = (DownloadManagerImpl)value[0];

					if ( type == LDT_STATECHANGED ){

						listener.stateChanged(dm, ((Integer)value[1]).intValue());

					}else if ( type == LDT_DOWNLOADCOMPLETE ){

						listener.downloadComplete(dm);

					}else if ( type == LDT_COMPLETIONCHANGED ){

						listener.completionChanged(dm, ((Boolean)value[1]).booleanValue());

					}else if ( type == LDT_FILEPRIORITYCHANGED ){

						listener.filePriorityChanged(dm, (DiskManagerFileInfo)value[1]);

					}else if ( type == LDT_POSITIONCHANGED ){

						listener.positionChanged( dm, ((Integer)value[1]).intValue(), ((Integer)value[2]).intValue());
						
					}else if ( type == LDT_FILELOCATIONCHANGED ){

						listener.fileLocationChanged( dm, (DiskManagerFileInfo)value[1]);
					}
				}
			});

	static final CopyOnWriteList<DownloadManagerListener>	global_dm_listeners = new CopyOnWriteList<>();

    private static final DownloadManagerListener global_dm_listener =
		new DownloadManagerListener() {

			@Override
			public void stateChanged(DownloadManager manager, int state) {
				for ( DownloadManagerListener listener: global_dm_listeners ){

					try{
						listener.stateChanged(manager, state);
					}catch( Throwable e ){
						Debug.out( e );
					}
				}
			}

			@Override
			public void positionChanged(DownloadManager download, int oldPosition,
			                            int newPosition) {
				for ( DownloadManagerListener listener: global_dm_listeners ){

					try{
						listener.positionChanged(download, oldPosition, newPosition);
					}catch( Throwable e ){
						Debug.out( e );
					}
				}
			}

			@Override
			public void filePriorityChanged(DownloadManager download,
			                                DiskManagerFileInfo file){
				for ( DownloadManagerListener listener: global_dm_listeners ){

					try{
						listener.filePriorityChanged(download, file);
					}catch( Throwable e ){
						Debug.out( e );
					}
				}
			}

			@Override
			public void fileLocationChanged(DownloadManager download,
			                                DiskManagerFileInfo file){
				for ( DownloadManagerListener listener: global_dm_listeners ){

					try{
						listener.fileLocationChanged(download, file);
					}catch( Throwable e ){
						Debug.out( e );
					}
				}
			}
			
			@Override
			public void downloadComplete(DownloadManager manager){
				for ( DownloadManagerListener listener: global_dm_listeners ){

					try{
						listener.downloadComplete(manager);
					}catch( Throwable e ){
						Debug.out( e );
					}
				}
			}

			@Override
			public void completionChanged(DownloadManager manager, boolean bCompleted){
				DownloadManagerState dms = manager.getDownloadState();

				long time = dms.getLongAttribute( DownloadManagerState.AT_COMPLETE_LAST_TIME );

				if ( time == -1 ){
					if ( bCompleted ){
						dms.setLongAttribute( DownloadManagerState.AT_COMPLETE_LAST_TIME, SystemTime.getCurrentTime());
					}
				}else if ( time > 0 ){
					if ( !bCompleted ){
						dms.setLongAttribute( DownloadManagerState.AT_COMPLETE_LAST_TIME, -1 );
					}
				}else{
					if ( bCompleted ){

						long completedOn = dms.getLongParameter(DownloadManagerState.PARAM_DOWNLOAD_COMPLETED_TIME);

						if ( completedOn > 0 ){

							dms.setLongAttribute( DownloadManagerState.AT_COMPLETE_LAST_TIME, completedOn );
						}
					}else{
						dms.setLongAttribute( DownloadManagerState.AT_COMPLETE_LAST_TIME, -1 );
					}
				}

				for ( DownloadManagerListener listener: global_dm_listeners ){

					try{
						listener.completionChanged(manager, bCompleted);
					}catch( Throwable e ){
						Debug.out( e );
					}
				}
			}
    };

    private static final SeedingRank defaultSeedingRank = new SeedingRank(){};
    
	public static void
	addGlobalDownloadListener(
		DownloadManagerListener listener )
	{
		global_dm_listeners.add( listener );
	}

    public static void
    removeGlobalDownloadListener(
        DownloadManagerListener listener )
    {
    	global_dm_listeners.remove( listener );
    }


	private static final AllTrackers	all_trackers = AllTrackersManager.getAllTrackers();

	
	private final ListenerManager<DownloadManagerListener>	listeners 	= ListenerManager.createManager(
			"DM:ListenDispatcher",
			new ListenerManagerDispatcher<DownloadManagerListener>()
			{
				@Override
				public void
				dispatch(
					DownloadManagerListener		listener,
					int							type,
					Object						value )
				{
					listeners_aggregator.dispatch( listener, type, value );
				}
			});

	{
		listeners.addListener( global_dm_listener );
	}
		// TrackerListeners

	private static final int LDT_TL_ANNOUNCERESULT		= 1;
	private static final int LDT_TL_SCRAPERESULT		= 2;

	final ListenerManager<DownloadManagerTrackerListener>	tracker_listeners 	= ListenerManager.createManager(
			"DM:TrackerListenDispatcher",
			new ListenerManagerDispatcher<DownloadManagerTrackerListener>()
			{
				@Override
				public void
				dispatch(
					DownloadManagerTrackerListener		listener,
					int			type,
					Object		value )
				{
					if ( type == LDT_TL_ANNOUNCERESULT ){

						listener.announceResult((TRTrackerAnnouncerResponse)value);

					}else if ( type == LDT_TL_SCRAPERESULT ){

						listener.scrapeResult((TRTrackerScraperResponse)value);
					}
				}
			});

	// PeerListeners

	private static final int LDT_PE_PEER_ADDED		= 1;
	private static final int LDT_PE_PEER_REMOVED	= 2;
	private static final int LDT_PE_PM_ADDED		= 5;
	private static final int LDT_PE_PM_REMOVED		= 6;

		// one static async manager for them all

	static final ListenerManager<DownloadManagerPeerListener>	peer_listeners_aggregator 	= ListenerManager.createAsyncManager(
			"DM:PeerListenAggregatorDispatcher",
			new ListenerManagerDispatcher<DownloadManagerPeerListener>()
			{
				@Override
				public void
				dispatch(
					DownloadManagerPeerListener		listener,
					int								type,
					Object							value )
				{
					if ( type == LDT_PE_PEER_ADDED ){

						listener.peerAdded((PEPeer)value);

					}else if ( type == LDT_PE_PEER_REMOVED ){

						listener.peerRemoved((PEPeer)value);

					}else if ( type == LDT_PE_PM_ADDED ){

						listener.peerManagerAdded((PEPeerManager)value);

					}else if ( type == LDT_PE_PM_REMOVED ){

						listener.peerManagerRemoved((PEPeerManager)value);
					}
				}
			});

	static final Object TPS_Key = new Object();

	public static volatile String	dnd_subfolder;

	static{
		COConfigurationManager.addAndFireParameterListeners(
			new String[]{ "Enable Subfolder for DND Files", "Subfolder for DND Files" },
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					String parameterName)
				{
					boolean enable  = COConfigurationManager.getBooleanParameter( "Enable Subfolder for DND Files" );

					if ( enable ){

						String folder = COConfigurationManager.getStringParameter( "Subfolder for DND Files" ).trim();

						if ( folder.length() > 0 ){

							folder = FileUtil.convertOSSpecificChars( folder, true ).trim();
						}

						if ( folder.length() > 0 ){

							dnd_subfolder = folder;

						}else{

							dnd_subfolder = null;
						}
					}else{

						dnd_subfolder = null;
					}
				}
			});
	}

	private static Map<String,Boolean>		save_dir_check_cache = new HashMap<>();
	private static TimerEventPeriodic		save_dir_check_timer = null;


	private final ListenerManager<DownloadManagerPeerListener>	peer_listeners 	= ListenerManager.createManager(
			"DM:PeerListenDispatcher",
			new ListenerManagerDispatcher<DownloadManagerPeerListener>()
			{
				@Override
				public void
				dispatch(
					DownloadManagerPeerListener		listener,
					int			type,
					Object		value )
				{
					peer_listeners_aggregator.dispatch( listener, type, value );
				}
			});

	final AEMonitor	peer_listeners_mon	= new AEMonitor( "DM:DownloadManager:PeerL" );

	final Map<PEPeer,String>	current_peers 						= new IdentityHashMap<>();
	private final Map<PEPeer,Long>	current_peers_unmatched_removal 	= new IdentityHashMap<>();

		// PieceListeners

	private static final int LDT_PE_PIECE_ADDED		= 3;
	private static final int LDT_PE_PIECE_REMOVED	= 4;

		// one static async manager for them all

	static final ListenerManager<DownloadManagerPieceListener>	piece_listeners_aggregator 	= ListenerManager.createAsyncManager(
			"DM:PieceListenAggregatorDispatcher",
			new ListenerManagerDispatcher<DownloadManagerPieceListener>()
			{
				@Override
				public void
				dispatch(
					DownloadManagerPieceListener	listener,
					int								type,
					Object							value )
				{
					if ( type == LDT_PE_PIECE_ADDED ){

						listener.pieceAdded((PEPiece)value);

					}else if ( type == LDT_PE_PIECE_REMOVED ){

						listener.pieceRemoved((PEPiece)value);
					}
				}
			});

	private final ListenerManager<DownloadManagerPieceListener>	piece_listeners 	= ListenerManager.createManager(
			"DM:PieceListenDispatcher",
			new ListenerManagerDispatcher<DownloadManagerPieceListener>()
			{
				@Override
				public void
				dispatch(
					DownloadManagerPieceListener		listener,
					int			type,
					Object		value )
				{
					piece_listeners_aggregator.dispatch( listener, type, value );
				}
			});

	private boolean			constructed;
	
	private Object 			init_lock = new Object();
	private boolean			initialised;
	private List<Runnable>	post_init_tasks = new ArrayList<>();
	
	
	private List<DownloadManagerTPSListener>	tps_listeners;

	private final AEMonitor	piece_listeners_mon	= new AEMonitor( "DM:DownloadManager:PeiceL" );

	private final List<PEPiece>	current_pieces	= new ArrayList<>();

	final DownloadManagerController	controller;
	private final DownloadManagerStatsImpl	stats;

	protected final AEMonitor					this_mon = new AEMonitor( "DM:DownloadManager" );

	private final boolean		persistent;

	/**
	 * Pretend this download is complete while not running,
	 * even if it has no data.  When the torrent starts up, the real complete
	 * level will be checked (probably by DiskManager), and if the torrent
	 * actually does have missing data at that point, the download will be thrown
	 * into error state.
	 * <p>
	 * Only a forced-recheck should clear this flag.
	 * <p>
	 * Current Implementation:<br>
	 * - implies that the user completed the download at one point<br>
	 * - Checks if there's Data Missing when torrent is done (or torrent load)
	 */
	private volatile boolean assumedComplete;

	/**
	 * forceStarted torrents can't/shouldn't be automatically stopped
	 */

	private int			last_informed_state	= STATE_START_OF_DAY;
	private boolean		latest_informed_force_start;

	private long		resume_time;

	final GlobalManager globalManager;
	private String 		torrentFileName;
	private boolean 	torrentFileExplicitlyDeleted;
	
	private boolean	open_for_seeding;

	private String	display_name	= "";
	private String	internal_name	= "";

		// for simple torrents this refers to the torrent file itself. For non-simple it refers to the
		// folder containing the torrent's files

	private File	torrent_save_location;

	// Position in Queue
	private int position = -1;

	private Object[]					read_torrent_state;
	DownloadManagerState		download_manager_state;

	private TOTorrent		torrent;
	private String 			torrent_comment;
	private String 			torrent_created_by;

	
	private volatile Map<String,Object[]>	url_group_map 		= new HashMap<>();
	private volatile long					url_group_map_uid	= -1;
	
	private volatile TRTrackerAnnouncer 			_tracker_client;
	private volatile TRTrackerAnnouncer 			_tracker_client_for_queued_download;
	private volatile int							light_seeding_status = 0;
	
	private final TRTrackerAnnouncerListener		tracker_client_listener =
			new TRTrackerAnnouncerListener()
			{
				@Override
				public void
				receivedTrackerResponse(
					TRTrackerAnnouncerRequest	request,
					TRTrackerAnnouncerResponse	response)
				{
					PEPeerManager pm = controller.getPeerManager();

					if ( pm != null ) {

						pm.processTrackerResponse( response );
					}

					tracker_listeners.dispatch( LDT_TL_ANNOUNCERESULT, response );
				}

				@Override
				public void
				urlChanged(
					final TRTrackerAnnouncer	announcer,
					final URL	 				old_url,
					URL							new_url,
					boolean 					explicit )
				{
					if ( explicit ){

							// flush connected peers on explicit url change

						if ( torrent.getPrivate()){

							final List<PEPeer>	peers;

							try{
								peer_listeners_mon.enter();

								peers = new ArrayList<>(current_peers.keySet());

							}finally{

								peer_listeners_mon.exit();
							}

							new AEThread2( "DM:torrentChangeFlusher", true )
							{
								@Override
								public void
								run()
								{
									for (int i=0;i<peers.size();i++){

										PEPeer	peer = (PEPeer)peers.get(i);

										peer.getManager().removePeer( peer, "Private torrent: tracker changed", Transport.CR_STOPPED_OR_REMOVED );
									}
								}
							}.start();
						}

						requestTrackerAnnounce( true );
					}
				}

				@Override
				public void
				urlRefresh()
				{
					requestTrackerAnnounce( true );
				}
			};

		// a third listener responsible for tracking the stats up/down reports
	
	private final TRTrackerAnnouncerListener		tracker_client_stats_listener =
			new TRTrackerAnnouncerListener()
			{
				@Override
				public void
				receivedTrackerResponse(
					TRTrackerAnnouncerRequest	request,
					TRTrackerAnnouncerResponse	response)
				{
					if ( response.getStatus() == TRTrackerAnnouncerResponse.ST_ONLINE ){

						stats.updateTrackerSession( request );
					}
				}

				@Override
				public void
				urlChanged(
					TRTrackerAnnouncer	announcer,
					URL 				old_url,
					URL					new_url,
					boolean 			explicit ){}

				@Override
				public void
				urlRefresh(){}
			};
		
		
	private final CopyOnWriteList<DownloadManagerActivationListener>	activation_listeners = new CopyOnWriteList<>();

	private final long						scrape_random_seed	= SystemTime.getCurrentTime();

	private volatile Map<Object,Object>		data;

	private boolean data_already_allocated = false;

	private long	creation_time	= SystemTime.getCurrentTime();

	private SeedingRank seedingRank = defaultSeedingRank;

	private boolean	dl_identity_obtained;
	private byte[]	dl_identity;
    private int 	dl_identity_hashcode;

    private int		max_uploads	= DownloadManagerState.MIN_MAX_UPLOADS;
    private int		max_connections;
    private int		max_connections_when_seeding;
    private boolean	max_connections_when_seeding_enabled;
    private int		max_seed_connections;
    private int		max_uploads_when_seeding	= DownloadManagerState.MIN_MAX_UPLOADS;
    private boolean	max_uploads_when_seeding_enabled;

    private int		max_upload_when_busy_bps;
    private int		current_upload_when_busy_bps;
    private long	last_upload_when_busy_update;
    private long	last_upload_when_busy_dec_time;
    private int		upload_priority_manual;
    private int		upload_priority_auto;

    private int		crypto_level 	= NetworkManager.CRYPTO_OVERRIDE_NONE;
    private int		message_mode	= -1;

    private volatile int		tcp_port_override;

    private volatile int		set_file_priority_high_pieces_rem	= 0;
    
	// Only call this with STATE_QUEUED, STATE_WAITING, or STATE_STOPPED unless you know what you are doing

    private volatile boolean	removing;
	private volatile boolean	destroyed;

	public
	DownloadManagerImpl(
		GlobalManager 							_gm,
		byte[]									_torrent_hash,
		String 									_torrentFileName,
		String 									_torrent_save_dir,
		String									_torrent_save_file,
		int   									_initialState,
		boolean									_persistent,
		boolean									_recovered,
		boolean									_open_for_seeding,
		boolean									_has_ever_been_started,
		List									_file_priorities )
	{
		List<Runnable>	to_run;
		
		synchronized( init_lock ){
			
			if ( 	_initialState != STATE_WAITING &&
					_initialState != STATE_STOPPED &&
					_initialState != STATE_QUEUED ){
	
				Debug.out( "DownloadManagerImpl: Illegal start state, " + _initialState );
			}
	
			persistent			= _persistent;
			globalManager 		= _gm;
			open_for_seeding	= _open_for_seeding;
	
				// TODO: move this to download state!
	
	    	if ( _file_priorities != null ){
	
	    		setUserData( "file_priorities", _file_priorities );
	    	}
	
			stats = new DownloadManagerStatsImpl( this );
	
			controller	= new DownloadManagerController( this );
	
			torrentFileName = _torrentFileName;
	
			_torrent_save_dir = FileUtil.removeTrailingSeparators( _torrent_save_dir );
	
				// readTorrent adjusts the save dir and file to be sensible values
	
			readTorrent( 	_torrent_save_dir, _torrent_save_file, _torrent_hash,
							persistent && !_recovered, _open_for_seeding, _has_ever_been_started,
							_initialState );
	
			if ( torrent != null ){
	
				buildURLGroupMap( torrent );
				
				torrent.addListener(
					new TOTorrentListener(){
						
						@Override
						public void torrentChanged(TOTorrent torrent, int change_type, Object data){
							buildURLGroupMap( torrent );
						}
					});
				
				if ( _open_for_seeding && !_recovered ){
	
					Map<Integer,File>	linkage = TorrentUtils.getInitialLinkage( torrent );
	
					if ( linkage.size() > 0 ){
	
						DownloadManagerState	dms = getDownloadState();
	
						DiskManagerFileInfo[]	files = getDiskManagerFileInfoSet().getFiles();
	
						try{
							dms.suppressStateSave( true );
	
							for ( Map.Entry<Integer,File> entry: linkage.entrySet()){
	
								int	index 	= entry.getKey();
								File target = entry.getValue();
	
								dms.setFileLink( index, files[index].getFile( false ), target );
	
							}
						}finally{
	
							dms.suppressStateSave( false );
						}
					}
				}
			}
			
			initialised = true;
			
			to_run = post_init_tasks;
			
			post_init_tasks = null;
		}
		
		// calcFilePriorityStats();
		
		for ( Runnable r: to_run ){
			
			try{
				r.run();
				
			}catch( Throwable e ){
				
				Debug.out( e );
			}
		}
	}
	
	@Override
	public void 
	setConstructed()
	{
		constructed = true;
	}
	
	@Override
	public boolean 
	isConstructed()
	{
		return( constructed );
	}
	
	private void
	buildURLGroupMap(
		TOTorrent	torrent )
	{
		int	group = 0;
		
		Map<String,Object[]>	new_map = new HashMap<>();
		
		TOTorrentAnnounceURLGroup t_group = torrent.getAnnounceURLGroup();
		
		url_group_map_uid = t_group.getUID();
		
		int	overall_ls = 0;
		
		for ( TOTorrentAnnounceURLSet set: t_group.getAnnounceURLSets()){
			
			URL[] urls = set.getAnnounceURLs();
			
			if ( urls.length > 1 ){
		
				Integer g = ++group;
				
				for ( URL u: urls ){
					
					String key = all_trackers.ingestURL( u );
					
					new_map.put( key, new Object[]{ u, g });
					
					overall_ls = Math.max( overall_ls, getLightSeedTrackerStatus( key ));
				}
			}else{
				for ( URL u: urls ){
					
					String key = all_trackers.ingestURL( u );
										
					overall_ls = Math.max( overall_ls, getLightSeedTrackerStatus( key ));
				}
			}
		}
		
		URL announce_url = torrent.getAnnounceURL();
		
		String announce_key = all_trackers.ingestURL( announce_url );
		
		overall_ls = Math.max( overall_ls, getLightSeedTrackerStatus( announce_key ));
		
		light_seeding_status = overall_ls;
		
		/*
		if ( Constants.isCVSVersion()){
			
			Map<String,Object[]>	old_map = url_group_map;
			
			boolean changed = false;
			
			if ( old_map.size() != new_map.size()){
				changed = true;
			}else{
				for ( String key:old_map.keySet()){
					
					Object[] o1 = old_map.get(key);
					Object[] o2 = new_map.get(key);
					
					if ( o2 == null ){
						changed = true;
						break;
					}else{
						int g1 = (Integer)o1[1];
						int g2 = (Integer)o2[1];
						
						if ( g1 != g2 ){
							changed = true;
							break;
						}
					}
				}
			}
			
			if ( changed ){
				
				String old_str = "";
				String new_str = "";
				
				for ( Map.Entry<String,Object[]> entry: old_map.entrySet()){
					old_str += (old_str.isEmpty()?"":", ") + entry.getKey() + "=" + entry.getValue()[1];
				}
				
				for ( Map.Entry<String,Object[]> entry: new_map.entrySet()){
					new_str += (new_str.isEmpty()?"":", ") + entry.getKey() + "=" + entry.getValue()[1];
				}

				Debug.outNoStack( "URL Group map changed for " + getDisplayName() + ": old=" + old_str + ", new=" + new_str );
			}
		}
		*/
		
		url_group_map = new_map;
	}

	private int
	getLightSeedTrackerStatus(
		String		name )
	{
		if ( name != null ){
			
			AllTrackersTracker tracker = all_trackers.getTracker( name );
			
			if ( tracker != null ){
				
				Map<String,Object>	options = tracker.getOptions();
				
				if ( options != null ){
					
					Number n = (Number)options.get( AllTrackersTracker.OPT_LIGHT_SEEDING );
					
					if ( n != null ){
						
						return( n.intValue());
					}
				}
			}
		}
		
		return( 0 );
	}
	
	protected int
	getTrackerURLGroup(
		String		key )
	{
		if ( torrent.getAnnounceURLGroup().getUID() != url_group_map_uid ){
			
			buildURLGroupMap( torrent );
		}
		
		Object[] entry = url_group_map.get( key );
		
		return( entry==null?-1:(int)entry[1] );
	}
	
	@Override
	public int
	getTaggableType()
	{
		return( TT_DOWNLOAD );
	}

	@Override
	public TaggableResolver
	getTaggableResolver()
	{
		return( globalManager );
	}

	@Override
	public String
	getTaggableID()
	{
		return( dl_identity==null?null:Base32.encode(dl_identity));
	}
	
	@Override
	public String getTaggableName(){
		return(getDisplayName());
	}
	
	@Override
	public ExportedDataSource 
	exportDataSource()
	{
		return(
			new ExportedDataSource()
			{
				public Class<? extends DataSourceImporter>
				getExporter()
				{
					return( globalManager.getClass());
				}
				
				public Map<String,Object>
				getExport()
				{
					Map	m = new HashMap<String,Object>();
					
					m.put( "id", getTaggableID());
					
					return( m );
				}
			});
	}
	

	private void
	readTorrent(
		String		torrent_save_dir,
		String		torrent_save_file,
		byte[]		torrent_hash,		// can be null for initial torrents
		boolean		new_torrent,		// probably equivalend to (torrent_hash == null)????
		boolean		for_seeding,
		boolean		has_ever_been_started,
		int			initial_state )
	{
		try{
			display_name				= torrentFileName;	// default if things go wrong decoding it
			internal_name				= "";
			torrent_comment				= "";
			torrent_created_by			= "";

			try{

					// this is the first thing we do and most likely to go wrong - hence its
					// existence is used below to indicate success or not

				 download_manager_state	=
					 	DownloadManagerStateImpl.getDownloadState(
					 			this,
					 			torrentFileName,
					 			torrent_hash,
					 			initial_state == DownloadManager.STATE_STOPPED ||
					 			initial_state == DownloadManager.STATE_QUEUED );

				 controller.setDownloadManagerState( download_manager_state );
				 
				 readParameters();
				 
				 readFilePriorityConfig( true, false );

					// establish any file links

				 DownloadManagerStateAttributeListener attr_listener =
					 new DownloadManagerStateAttributeListener()
				 	 {
					 	private final ThreadLocal<Boolean>	links_changing =
					 			new ThreadLocal<Boolean>()
					 			{
					 				@Override
								  protected Boolean initialValue(){
					 					return Boolean.FALSE;
					 				}
					 			};

					 	@Override
					 	public void
					 	attributeEventOccurred(
							DownloadManager dm, String attribute_name, int event_type)
					 	{
					 		if (attribute_name.equals(DownloadManagerState.AT_FILE_LINKS2)){

				 				if ( links_changing.get()){

					 				System.out.println( "recursive!" );

					 				return;
					 			}

					 			links_changing.set( true );

					 			try{

					 				setFileLinks();

					 			}finally{

					 				links_changing.set( false );
					 			}
					 		}else if (attribute_name.equals(DownloadManagerState.AT_PARAMETERS)){

					 			readParameters();

					 		}else if (attribute_name.equals(DownloadManagerState.AT_NETWORKS)){

					 			TRTrackerAnnouncer tc = getTrackerClient();

					 			if ( tc != null ){

					 				tc.resetTrackerUrl( false );
					 			}
							}else if ( attribute_name.equals( DownloadManagerState.AT_SET_FILE_PRIORITY_REM_PIECE )){
								
								readFilePriorityConfig( false, false );
							}
						}
					};

				 download_manager_state.addListener(attr_listener, DownloadManagerState.AT_FILE_LINKS2, DownloadManagerStateAttributeListener.WRITTEN);
				 download_manager_state.addListener(attr_listener, DownloadManagerState.AT_PARAMETERS, DownloadManagerStateAttributeListener.WRITTEN);
				 download_manager_state.addListener(attr_listener, DownloadManagerState.AT_NETWORKS, DownloadManagerStateAttributeListener.WRITTEN);
				 download_manager_state.addListener(attr_listener, DownloadManagerState.AT_SET_FILE_PRIORITY_REM_PIECE, DownloadManagerStateAttributeListener.WRITTEN);

				 torrent	= download_manager_state.getTorrent();

				 all_trackers.registerTorrent( torrent );
				 
				 setFileLinks();

				 	// We can't have the identity of this download changing as this will screw up
				 	// anyone who tries to maintain a unique set of downloads (e.g. the GlobalManager)
				 	//

				 if ( !dl_identity_obtained ){

					 	// flag set true below

					 dl_identity			= torrent_hash==null?torrent.getHash():torrent_hash;

	                 this.dl_identity_hashcode = new String( dl_identity ).hashCode();
				 }

				 if ( !Arrays.equals( dl_identity, torrent.getHash())){

					 torrent	= null;	// prevent this download from being used

					 	// set up some kinda default else things don't work wel...

					 torrent_save_location = FileUtil.newFile( torrent_save_dir, torrentFileName );

					 throw( new NoStackException( DownloadManager.ET_OTHER,"Download identity changed - please remove and re-add the download" ));
				 }

				 read_torrent_state	= null;	// no longer needed if we saved it

				 LocaleUtilDecoder	locale_decoder = LocaleTorrentUtil.getTorrentEncoding( torrent );

				 	// if its a simple torrent and an explicit save file wasn't supplied, use
				 	// the torrent name itself

				 display_name = FileUtil.convertOSSpecificChars(TorrentUtils.getLocalisedName(torrent),false);

				 byte[] hash = torrent.getHash();

				 internal_name = ByteFormatter.nicePrint( hash, true);

				 	// now we know if its a simple torrent or not we can make some choices about
				 	// the save dir and file. On initial entry the save_dir will have the user-selected
				 	// save location and the save_file will be null

				 File	save_dir_file	= FileUtil.newFile( torrent_save_dir );

				 // System.out.println( "before: " + torrent_save_dir + "/" + torrent_save_file );

				 	// if save file is non-null then things have already been sorted out

				 if ( torrent_save_file == null ){

				 		// make sure we're working off a canonical save dir if possible

				 	try{
				 		if ( save_dir_file.exists()){

				 			save_dir_file = FileUtil.getCanonicalFileSafe( save_dir_file );
				 		}
				 	}catch( Throwable e ){

				 		Debug.printStackTrace(e);
				 	}

				 	if ( torrent.isSimpleTorrent()){

				 			// if target save location is a directory then we use that as the save
				 			// dir and use the torrent display name as the target. Otherwise we
				 			// use the file name

				 		if ( save_dir_file.exists()){

				 			if ( save_dir_file.isDirectory()){

				 				torrent_save_file	= display_name;

				 			}else{

				 				torrent_save_dir	= save_dir_file.getParent().toString();

				 				torrent_save_file	= save_dir_file.getName();
				 			}
				 		}else{

				 				// doesn't exist, assume it refers directly to the file

				 			if ( save_dir_file.getParent() == null ){

				 				throw( new NoStackException( DownloadManager.ET_FILE_MISSING, "Data location '" + torrent_save_dir + "' is invalid" ));

				 			}

			 				torrent_save_dir	= save_dir_file.getParent().toString();

			 				torrent_save_file	= save_dir_file.getName();
				 		}

				 	}else{

				 			// torrent is a folder. It is possible that the natural location
				 			// for the folder is X/Y and that in fact 'Y' already exists and
				 			// has been selected. If ths is the case the select X as the dir and Y
				 			// as the file name

				 		if ( save_dir_file.exists()){

				 			if ( !save_dir_file.isDirectory()){

				 				throw( new NoStackException( DownloadManager.ET_OTHER, "'" + torrent_save_dir + "' is not a directory" ));
				 			}

				 			if ( save_dir_file.getName().equals( display_name )){
				 				torrent_save_dir	= save_dir_file.getParent().toString();
				 			}
				 		}

				 		torrent_save_file	= display_name;
				 	}
				 }

				 torrent_save_location = FileUtil.newFile( torrent_save_dir, torrent_save_file );

				 	// final validity test must be based of potentially linked target location as file
				 	// may have been re-targeted


					// if this isn't a new torrent then we treat the absence of the enclosing folder
					// as a fatal error. This is in particular to solve a problem with the use of
				 	// externally mounted torrent data on OSX, whereby a re-start with the drive unmounted
				 	// results in the creation of a local directory in /Volumes that subsequently stuffs
				 	// up recovery when the volume is mounted

				 	// changed this to only report the error on non-windows platforms

				if ( !(new_torrent || Constants.isWindows )){

						// another exception here - if the torrent has never been started then we can
						// fairly safely continue as its in a stopped state

					if ( has_ever_been_started ){

						 File	linked_target = getSaveLocation();

						 File parent = linked_target.getParentFile();
						 
						 Boolean res;
						 
						 	// seemingly seen a lot of time taken on startup with big library checking that
						 	// save location exists so experimenting with reducing FS accesses
						 
						 synchronized( save_dir_check_cache ){
							 
							 String key = parent.getAbsolutePath();
							 
							 res = save_dir_check_cache.get( key );
							 
							 if ( res == null ){
								 
								 res = parent.exists();
								 
								 save_dir_check_cache.put( key, res );
								 								 
								 if ( save_dir_check_timer == null ){
									 
									 save_dir_check_timer = 
										SimpleTimer.addPeriodicEvent(
											"sdct",
											60*1000,
											new TimerEventPerformer(){
												
												@Override
												public void perform(TimerEvent event){
													
													synchronized( save_dir_check_cache ){
														
														if ( save_dir_check_cache.isEmpty()){
															
															save_dir_check_timer.cancel();
															
															save_dir_check_timer = null;
															
														}else{
															
															save_dir_check_cache.clear();
														}
													}
												}
											});
								 }
							 }
						 }
						 
						 if ( !res ){

							throw (new NoStackException(
									DownloadManager.ET_FILE_MISSING,
									MessageText.getString("DownloadManager.error.datamissing")
											+ ": " + Debug.secretFileName(linked_target.toString())));
						}
				 	}
				 }

		 		// propagate properties from torrent to download

				boolean	low_noise = TorrentUtils.getFlag( torrent, TorrentUtils.TORRENT_FLAG_LOW_NOISE );

				if ( low_noise ){

					download_manager_state.setFlag( DownloadManagerState.FLAG_LOW_NOISE, true );
				}

				boolean	metadata_dl = TorrentUtils.getFlag( torrent, TorrentUtils.TORRENT_FLAG_METADATA_TORRENT );

				if ( metadata_dl ){

					download_manager_state.setFlag( DownloadManagerState.FLAG_METADATA_DOWNLOAD, true );
				}

				 	// if this is a newly introduced torrent trash the tracker cache. We do this to
				 	// prevent, say, someone publishing a torrent with a load of invalid cache entries
				 	// in it and a bad tracker URL. This could be used as a DOS attack

				 if ( new_torrent ){

					download_manager_state.setLongParameter( DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME, SystemTime.getCurrentTime());

					Map peer_cache = TorrentUtils.getPeerCache( torrent );

					if ( peer_cache != null ){

						try{
							download_manager_state.setTrackerResponseCache( peer_cache );

						}catch( Throwable e ){

							Debug.out( e );

							download_manager_state.setTrackerResponseCache( new HashMap());
						}
					}else{

						download_manager_state.setTrackerResponseCache( new HashMap());
					}

				 		// also remove resume data incase someone's published a torrent with resume
				 		// data in it

				 	if ( for_seeding ){

				 		DiskManagerFactory.setTorrentResumeDataNearlyComplete(download_manager_state);

				 		// Prevent download being considered for on-completion moving - it's considered complete anyway.
				 		download_manager_state.setFlag(DownloadManagerState.FLAG_MOVE_ON_COMPLETION_DONE, true);

				 	}else{

				 		download_manager_state.clearResumeData();
				 	}

				 		// set up the dnd-subfolder status on addition

				 	if ( persistent && !for_seeding && !torrent.isSimpleTorrent()){

				 		String dnd_sf = dnd_subfolder;

				 		if ( dnd_sf != null ){

				 			if ( torrent.getFiles().length <= DownloadManagerStateFactory.MAX_FILES_FOR_INCOMPLETE_AND_DND_LINKAGE ){

				 				if ( download_manager_state.getAttribute( DownloadManagerState.AT_DND_SUBFOLDER ) == null ){

				 					download_manager_state.setAttribute( DownloadManagerState.AT_DND_SUBFOLDER, dnd_sf );
				 				}

								boolean	use_prefix = COConfigurationManager.getBooleanParameter( "Use Incomplete File Prefix" );

								if ( use_prefix ){

					 				if ( download_manager_state.getAttribute( DownloadManagerState.AT_DND_PREFIX ) == null ){

										String prefix = Base32.encode( hash ).substring( 0, 12 ).toLowerCase( Locale.US ) + "_";

					 					download_manager_state.setAttribute( DownloadManagerState.AT_DND_PREFIX, prefix );
					 				}
								}
            				}
            			}
            		}
				 }else{

					 long	add_time = download_manager_state.getLongParameter( DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME );

					 if ( add_time == 0 ){

						 // grab an initial value from torrent file - migration only

						 try{
							 add_time = FileUtil.newFile( torrentFileName ).lastModified();

						 }catch( Throwable e ){
						 }

						 if ( add_time == 0 ){

							 add_time = SystemTime.getCurrentTime();
						 }

						 download_manager_state.setLongParameter( DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME, add_time );
					 }
				 }


				 //trackerUrl = torrent.getAnnounceURL().toString();

				torrent_comment = StringInterner.intern(locale_decoder.decodeString(torrent.getComment()));

				if ( torrent_comment == null ){

				   torrent_comment	= "";
				}

				torrent_created_by = locale_decoder.decodeString(torrent.getCreatedBy());

				if ( torrent_created_by == null ){

					torrent_created_by	= "";
				}

				 	// only restore the tracker response cache for non-seeds

				 if ( download_manager_state.isResumeDataComplete() || for_seeding ){

					 	// actually, can't think of a good reason not to restore the
					 	// cache for seeds, after all if the tracker's down we still want
					 	// to connect to peers to upload to

					  // download_manager_state.clearTrackerResponseCache();

					  stats.setDownloadCompletedBytes(getSize());

					  setAssumedComplete(true);

				 }else{

					 setAssumedComplete(false);
				}

				if ( download_manager_state.getDisplayName() == null ){

					String title = TorrentUtils.getDisplayName( torrent );
					
					if ( title == null ){
					
						title = com.biglybt.core.torrent.PlatformTorrentUtils.getContentTitle( torrent );
					}
					
					if ( title != null && title.length() > 0 ){

						download_manager_state.setDisplayName(title);
					}
				}
				
				if ( download_manager_state.getAndClearRecoveredStatus()){
					
					setFailed( "Recovered from original torrent" );
				}
			}catch( TOTorrentException e ){

				//Debug.printStackTrace( e );

				setFailed( TorrentUtils.exceptionToText( e ));

			}catch( UnsupportedEncodingException e ){

				Debug.printStackTrace( e );

				setFailed( MessageText.getString("DownloadManager.error.unsupportedencoding"));

			}catch( NoStackException e ){

				Debug.outNoStack( e.getMessage());

				setFailed( e.getError(), e.getMessage());
				
			}catch( Throwable e ){

				Debug.printStackTrace( e );

				setFailed( "Failed to read torrent", e );

			}finally{

				 dl_identity_obtained	= true;
			}

			if ( download_manager_state == null ){
				read_torrent_state =
					new Object[]{
						torrent_save_dir, torrent_save_file, torrent_hash,
						Boolean.valueOf(new_torrent), Boolean.valueOf(for_seeding), Boolean.valueOf(has_ever_been_started),
						new Integer( initial_state )
					};

					// torrent's stuffed - create a dummy "null object" to simplify use
					// by other code

				download_manager_state	= DownloadManagerStateImpl.getDownloadState( this );

				controller.setDownloadManagerState( download_manager_state );
				
					// make up something vaguely sensible for save location

				if ( torrent_save_file == null ){

					torrent_save_location = FileUtil.newFile( torrent_save_dir );

				}else{

					torrent_save_location = FileUtil.newFile( torrent_save_dir, torrent_save_file );
				}

			}else{


					// make up something vaguely sensible for save location if we haven't got one

				if ( torrent_save_file == null ){

					torrent_save_location = FileUtil.newFile( torrent_save_dir );
				}

					// make sure we know what networks to use for this download

				if ( torrent != null && !download_manager_state.hasAttribute( DownloadManagerState.AT_NETWORKS )){

					String[] networks = AENetworkClassifier.getNetworks( torrent, display_name );

					download_manager_state.setNetworks( networks );
				}
			}
		}finally{

			if ( torrent_save_location != null ){

				boolean	already_done = false;

				String cache = download_manager_state.getAttribute( DownloadManagerState.AT_CANONICAL_SD_DMAP );

				if ( cache != null ){

					String key = torrent_save_location.getAbsolutePath() + "\n";

					if ( cache.startsWith( key )){

						torrent_save_location = FileUtil.newFile( cache.substring( key.length()));

						already_done = true;
					}
				}

				if ( !already_done ){

					String key = torrent_save_location.getAbsolutePath() + "\n";

					torrent_save_location = FileUtil.getCanonicalFileSafe( torrent_save_location );

					download_manager_state.setAttribute(
						DownloadManagerState.AT_CANONICAL_SD_DMAP,
						key + torrent_save_location.getAbsolutePath());
				}
					// update cached stuff in case something changed

				getSaveLocation();
			}

				// must be after torrent read, so that any listeners have a TOTorrent
				// not that if things have failed above this method won't override a failed
				// state with the initial one

			controller.setInitialState( initial_state );
		}
	}

	protected void
	readTorrent()
	{
		if ( read_torrent_state == null ){

			return;
		}

		readTorrent(
				(String)read_torrent_state[0],
				(String)read_torrent_state[1],
				(byte[])read_torrent_state[2],
				((Boolean)read_torrent_state[3]).booleanValue(),
				((Boolean)read_torrent_state[4]).booleanValue(),
				((Boolean)read_torrent_state[5]).booleanValue(),
				((Integer)read_torrent_state[6]).intValue());

	}

	@Override
	public void 
	syncGlobalConfig()
	{
		readFilePriorityConfig( false, true );
	}
	
	protected void
	readParameters()
	{
		DownloadManagerState state = getDownloadState();
		
		max_connections							= state.getIntParameter( DownloadManagerState.PARAM_MAX_PEERS );
		max_connections_when_seeding_enabled	= state.getBooleanParameter( DownloadManagerState.PARAM_MAX_PEERS_WHEN_SEEDING_ENABLED );
		max_connections_when_seeding			= state.getIntParameter( DownloadManagerState.PARAM_MAX_PEERS_WHEN_SEEDING );
		max_seed_connections					= state.getIntParameter( DownloadManagerState.PARAM_MAX_SEEDS );
		max_uploads						 		= state.getIntParameter( DownloadManagerState.PARAM_MAX_UPLOADS );
		max_uploads_when_seeding_enabled 		= state.getBooleanParameter( DownloadManagerState.PARAM_MAX_UPLOADS_WHEN_SEEDING_ENABLED );
		max_uploads_when_seeding 				= state.getIntParameter( DownloadManagerState.PARAM_MAX_UPLOADS_WHEN_SEEDING );
		max_upload_when_busy_bps				= state.getIntParameter( DownloadManagerState.PARAM_MAX_UPLOAD_WHEN_BUSY ) * 1024;

		max_uploads = Math.max( max_uploads, DownloadManagerState.MIN_MAX_UPLOADS );
		max_uploads_when_seeding = Math.max( max_uploads_when_seeding, DownloadManagerState.MIN_MAX_UPLOADS );

		upload_priority_manual					= state.getIntParameter( DownloadManagerState.PARAM_UPLOAD_PRIORITY );
	}

	private void
	readFilePriorityConfig(
		boolean		init,
		boolean		global_change )
	{
		int sfp		= getDownloadState().getIntAttribute( DownloadManagerState.AT_SET_FILE_PRIORITY_REM_PIECE );
		
		if ( sfp == 0 ){
			
			sfp = COConfigurationManager.getIntParameter( ConfigKeys.Transfer.ICFG_SET_FILE_PRIORITY_REM_PIECE );
		}
		
		if ( sfp != set_file_priority_high_pieces_rem ){
		
			set_file_priority_high_pieces_rem = sfp;
			
			if ( !init ){
				
				checkFilePriorities( global_change );
			}
		}
	}
	
	protected int[]
	getMaxConnections( boolean mixed )
	{
		if ( mixed && max_connections > 0 ){

			return( new int[]{ max_connections, max_connections_npp_extra });
		}

		return( new int[]{ max_connections, 0 });
	}

	protected int[]
	getMaxConnectionsWhenSeeding( boolean mixed )
	{
		if ( mixed && max_connections_when_seeding > 0){

			return( new int[]{ max_connections_when_seeding, max_connections_npp_extra });
		}

		return( new int[]{ max_connections_when_seeding, 0 });
	}

	protected boolean
	isMaxConnectionsWhenSeedingEnabled()
	{
		return( max_connections_when_seeding_enabled );
	}

	protected int[]
	getMaxSeedConnections( boolean mixed )
	{
		if ( mixed && max_seed_connections > 0 ){

			return( new int[]{ max_seed_connections, max_connections_npp_extra });
		}

		return( new int[]{ max_seed_connections, 0 });
	}

	protected boolean
	isMaxUploadsWhenSeedingEnabled()
	{
		return( max_uploads_when_seeding_enabled );
	}

	protected int
	getMaxUploadsWhenSeeding()
	{
		return( max_uploads_when_seeding );
	}

	@Override
	public void
	updateAutoUploadPriority(
		Object		key,
		boolean		inc )
	{
		try{
	  		peer_listeners_mon.enter();

	  		boolean	key_exists = getUserData( key ) != null;

	  		if ( inc && !key_exists ){

	  			upload_priority_auto++;

	  			setUserData( key, "" );

	  		}else if ( !inc && key_exists ){

	  			upload_priority_auto--;

	  			setUserData( key, null );
	  		}
		}finally{

			peer_listeners_mon.exit();
		}
	}

	public int
	getEffectiveUploadPriority()
	{
		return( upload_priority_manual + upload_priority_auto );
	}

	@Override
	public int
	getMaxUploads()
	{
		return( max_uploads );
	}

	@Override
	public void
	setMaxUploads(
		int	max )
	{
		download_manager_state.setIntParameter( DownloadManagerState.PARAM_MAX_UPLOADS, max );
	}

	public void
	setManualUploadPriority(
		int	priority )
	{
		download_manager_state.setIntParameter( DownloadManagerState.PARAM_UPLOAD_PRIORITY, priority );
	}

	@Override
	public int
	getEffectiveMaxUploads()
	{
		if ( isMaxUploadsWhenSeedingEnabled() && getState() == DownloadManager.STATE_SEEDING ){

			return( getMaxUploadsWhenSeeding());

		}else{

			return( max_uploads );
		}
	}

	@Override
	public int
	getEffectiveUploadRateLimitBytesPerSecond()
	{
		int	local_max_bps	= stats.getUploadRateLimitBytesPerSecond();
		int	rate			= local_max_bps;

		if ( max_upload_when_busy_bps != 0 ){

			long	now = SystemTime.getCurrentTime();

			if ( now < last_upload_when_busy_update || now - last_upload_when_busy_update > 5000 ){

				last_upload_when_busy_update	= now;

					// might need to impose the limit

				String key = TransferSpeedValidator.getActiveUploadParameter( globalManager );

				int	global_limit_bps = COConfigurationManager.getIntParameter( key )*1024;

				if ( global_limit_bps > 0 && max_upload_when_busy_bps < global_limit_bps ){

						// we have a global limit and a valid busy limit

					local_max_bps = local_max_bps==0?global_limit_bps:local_max_bps;

					GlobalManagerStats gm_stats = globalManager.getStats();

					int	actual = gm_stats.getDataSendRateNoLAN() + gm_stats.getProtocolSendRateNoLAN();

					int	move_by = ( local_max_bps - max_upload_when_busy_bps ) / 10;

					if ( move_by < 1024 ){

						move_by = 1024;
					}

					if ( global_limit_bps - actual <= 2*1024 ){

							// close enough to impose the busy limit downwards


						if ( current_upload_when_busy_bps == 0 ){

							current_upload_when_busy_bps = local_max_bps;
						}

						int	prev_upload_when_busy_bps = current_upload_when_busy_bps;

						current_upload_when_busy_bps -= move_by;

						if ( current_upload_when_busy_bps < max_upload_when_busy_bps ){

							current_upload_when_busy_bps = max_upload_when_busy_bps;
						}

						if ( current_upload_when_busy_bps < prev_upload_when_busy_bps ){

							last_upload_when_busy_dec_time = now;
						}
					}else{

							// not hitting limit, increase

						if ( current_upload_when_busy_bps != 0 ){

								// only try increment if sufficient time passed

							if ( 	upload_when_busy_min_secs == 0 ||
									now < last_upload_when_busy_dec_time ||
									now - last_upload_when_busy_dec_time >=  upload_when_busy_min_secs*1000L ){

								current_upload_when_busy_bps += move_by;

								if ( current_upload_when_busy_bps >= local_max_bps ){

									current_upload_when_busy_bps	= 0;
								}
							}
						}
					}

					if ( current_upload_when_busy_bps > 0 ){

						rate = current_upload_when_busy_bps;
					}
				}else{

					current_upload_when_busy_bps = 0;
				}
			}else{

				if ( current_upload_when_busy_bps > 0 ){

					rate = current_upload_when_busy_bps;
				}
			}
		}

		return( rate );
	}

	protected void
	setFileLinks()
	{
			// invalidate the cache info in case its now wrong

		cached_save_location	= null;

		DiskManagerFactory.setFileLinks( this, download_manager_state.getFileLinks());

		controller.fileInfoChanged();
	}

	protected void
	clearFileLinks()
	{
		download_manager_state.clearFileLinks();
	}

	private void
	updateFileLinks(
		File old_save_path, File new_save_path)
	{
		old_save_path = FileUtil.getCanonicalFileSafe( old_save_path );
		
		new_save_path = FileUtil.getCanonicalFileSafe( new_save_path );

		//System.out.println( "update_file_links: " + old_save_path +  " -> " + new_save_path );

		LinkFileMap links = download_manager_state.getFileLinks();

		Iterator<LinkFileMap.Entry> it = links.entryIterator();

		List<Integer>	from_indexes 	= new ArrayList<>();
		List<File>		from_links 		= new ArrayList<>();
		List<File>		to_links		= new ArrayList<>();

		while(it.hasNext()){

			LinkFileMap.Entry entry = it.next();

			try{

				File	to			= entry.getToFile();

				if ( to == null ){

						// represents a deleted link, nothing to update

					continue;
				}

				to = FileUtil.getCanonicalFileSafe( to );

				int		file_index 	= entry.getIndex();
				File	from 		= entry.getFromFile();

				from = FileUtil.getCanonicalFileSafe( from );

				updateFileLink( file_index, old_save_path, new_save_path, from, to, 
					from_indexes, from_links, to_links );

			}catch( Exception e ){

				Debug.printStackTrace(e);
			}
		}

		if ( from_links.size() > 0 ){

			download_manager_state.setFileLinks( from_indexes, from_links, to_links );
		}
	}

	// old_path -> Full location of old torrent (inclusive of save name)
	// from_loc -> Old unmodified location of file within torrent.
	// to_loc -> Old modified location of file (where the link points to).
	//
	// We have to update from_loc and to_loc.
	// We should always be modifying from_loc. Only modify to_loc if it sits within
	// the old path.

	private void
	updateFileLink(
		int				file_index,
		File 			old_path,
		File 			new_path,
		File 			from_loc,
		File 			to_loc,
		List<Integer>	from_indexes,
		List<File> 		from_links,
		List<File> 		to_links )
	{
		//System.out.println( "ufl: " + file_index + "\n  " + old_path + " - " + new_path + "\n  " + from_loc + " - " + to_loc );

		String old_path_str = old_path.getPath();
		String new_path_str = new_path.getPath();

		if ( torrent.isSimpleTorrent()){

			if (!FileUtil.areFilePathsIdentical(old_path, from_loc)) {

				throw new RuntimeException("assert failure: old_path=" + old_path + ", from_loc=" + from_loc);
			}

			//System.out.println( "   adding " + old_path + " -> null" );

			from_indexes.add( 0 );
			from_links.add(old_path);
			to_links.add( null );

				// in general links on simple torrents aren't used, instead the download's save-path is switched to the
				// alternate location (only a single file after all this is simplest implementation). Unfortunately links can
				// actually still be set (e.g. to add an 'incomplete' suffix to a file) so we still need to support link-rewriting
				// properly

				// so the only valid linking on a simple torrent is to rename the file, not to point to a file in an
				// alternative directory. Thus fixing up the link becomes a case of taking the target file name from the old link
				// and making it the target file naem of the new link within the new path's parent folder

			File new_path_parent = new_path.getParentFile();
			if (new_path_parent == null) {
				Debug.out( "new_path " + new_path + " missing file separator, not good" );

				new_path_parent = new_path;
			}

			String to_loc_name = to_loc.getName();

			File to_loc_to_use = FileUtil.newFile(new_path_parent, to_loc_name);

			//System.out.println( "   adding " + new_path + " -> " + to_loc_to_use );
			from_indexes.add( 0 );
			from_links.add( new_path );
			to_links.add( to_loc_to_use );

		}else{

			String from_loc_to_use = FileUtil.translateMoveFilePath( old_path_str, 
				new_path_str, from_loc.getAbsolutePath() );

			if ( from_loc_to_use == null ){

				return;
			}

			String to_loc_to_use = FileUtil.translateMoveFilePath( old_path_str, 
				new_path_str, to_loc.getAbsolutePath() );

				// delete old

			from_indexes.add( file_index );
			from_links.add( from_loc );
			to_links.add( null );

				// add new

			from_indexes.add( file_index );
			from_links.add( FileUtil.newFile(from_loc_to_use));
			to_links.add( to_loc_to_use == null ? to_loc : FileUtil.newFile(to_loc_to_use));
		}
	}

	@Override
	public boolean
	filesExist(
		boolean expected_to_be_allocated )
	{
		return( controller.filesExist( expected_to_be_allocated ));
	}


	@Override
	public boolean
	isPersistent()
	{
		return( persistent );
	}

	@Override
	public String
	getDisplayName()
	{
		DownloadManagerState dms = this.getDownloadState();
		if (dms != null) {
			String result = dms.getDisplayName();
			if (result != null) {return result;}
		}
		return( display_name );
	}

 	@Override
 	public String
	getInternalName()
  	{
 		return( internal_name );
  	}

 	@Override
 	public void 
 	setErrorState(
 		int 		errorType, 
 		String		errorDetails,
 		int			errorFlags )
 	{
 		controller.setErrorState( errorType, errorDetails, errorFlags );
 	}
 	
	@Override
	public String
	getErrorDetails()
	{
		return( controller.getErrorDetail());
	}

	@Override
	public int
	getErrorType()
	{
		return( controller.getErrorType());
	}
	
	@Override
	public int 
	getErrorFlags()
	{
		return( controller.getErrorFlags());
	}

	@Override
	public long
	getSize()
	{
		if( torrent != null){

			return torrent.getSize();
		}

		return 0;
	}

	protected void
	setFailed(
		String	str )
	{
		controller.setFailed( str );
	}

	protected void
	setFailed(
		int		error,
		String	str )
	{
		controller.setFailed( error, str );
	}
	
	protected void
	setFailed(
		String		str,
		Throwable	e )
	{
		controller.setFailed( str, e );
	}
	
	protected void
	setTorrentInvalid(
		Throwable 	cause )
	{
		setFailed( "Invalid torrent", cause );

		torrent	= null;
	}

	protected int
	getTCPPortOverride(
		boolean	only_if_allocated )
	{
		if ( tcp_port_override != 0 ){
			
			return( tcp_port_override>0?tcp_port_override:0 );
		}
		
		if ( getTorrentHashOverride() != null ){
							
			if ( only_if_allocated ){
					
				return( -1 );
			}
				
			PeerManagerRegistration reg = controller.getPeerManagerRegistration();
				
			if ( reg != null ){
										
				List<PeerManagerRegistration> others = reg.getOtherRegistrationsForHash();
					
				List<Integer>	existing_ports = new ArrayList<Integer>();
							
				for ( PeerManagerRegistration r: others ){
						
					int port = r.getHashOverrideLocalPort( true );
						
					if ( port > 0 ){
						
						existing_ports.add( port );
					}
				}

				synchronized( port_init_lock ){
					
					if ( tcp_port_override == 0 ){
						
						tcp_port_override = TCPNetworkManager.getSingleton().getAdditionalTCPListeningPortNumber( existing_ports );
					}
					
					return( tcp_port_override );
				}
			}else{
					
					// hmm, what to do
					
				return( -1 );
			}
		}else{
		
			tcp_port_override = -1;
				
			return( 0 );
		}
	}
	
	protected HashWrapper 
	getTorrentHashOverride()
	{
		try{
			if ( torrent != null ){
				
				byte[] orig = TorrentUtils.getOriginalHash( torrent );
				
				if ( orig != null ){
				
					return( new HashWrapper( orig ));
				}
			}
		}catch( Throwable e ){
			
		}
		
		return( null );
	}
	
	@Override
	public int 
	getTCPListeningPortNumber()
	{
		int port = getTCPPortOverride( false );
		
		if ( port > 0 ){
			
			return( port );
		}
		
		return( TCPNetworkManager.getSingleton().getDefaultTCPListeningPortNumber());
	}
	
	@Override
	public void
	saveResumeData()
	{
		if ( getState() == STATE_DOWNLOADING) {

			try{
				getDiskManager().saveResumeData( true );

			}catch( Exception e ){

				setFailed( "Resume data save fails", e );
			}
		}

		// we don't want to update the torrent if we're seeding

		if ( !assumedComplete  ){

			download_manager_state.save( true );	// always in interim save
		}
	}

  	@Override
	public void
  	saveDownload(
  		boolean	interim )
  	{
  		DiskManager disk_manager = controller.getDiskManager();

  		if ( disk_manager != null ){

  			disk_manager.saveState( interim );
  		}

  		download_manager_state.save( interim );
  	}


	@Override
	public void
	initialize()
	{
		if (isDestroyed()) {
			if (Logger.isEnabled()) {
				Logger.log(new LogEvent(this, LogIDs.CORE, LogEvent.LT_ERROR,
						"Initialize called after destroyed"));
			}
			return;
		}

	  	// entry:  valid if waiting, stopped or queued
	  	// exit: error, ready or on the way to error

		if ( torrent == null ) {

				// have a go at re-reading the torrent in case its been recovered

			readTorrent();
		}

		if ( torrent == null ) {

			setFailed( "Failed to read torrent" );

			return;
		}

		// If a torrent that is assumed complete, verify that it actually has the
		// files, before we create the diskManager, which will try to allocate
		// disk space.
		if (assumedComplete && !filesExist( true )) {
			// filesExist() has set state to error for us

			// If the user wants to re-download the missing files, they must
			// do a re-check, which will reset the flag.
			return;
		}

		download_manager_state.setActive( true );

		try{
			try{
				this_mon.enter();

				stopQueuedTrackerClient();
				
				if ( _tracker_client != null ){

					Debug.out( "DownloadManager: initialize called with tracker client still available" );

					_tracker_client.destroy();
				}

				_tracker_client =
					TRTrackerAnnouncerFactory.create(
						torrent,
						new TRTrackerAnnouncerFactory.DataProvider()
						{
							@Override
							public String[]
							getNetworks()
							{
								return( download_manager_state.getNetworks());
							}
							
							@Override
							public HashWrapper 
							getTorrentHashOverride()
							{
								return( DownloadManagerImpl.this.getTorrentHashOverride());
							}
						});

				_tracker_client.setTrackerResponseCache( download_manager_state.getTrackerResponseCache());

				_tracker_client.addListener( tracker_client_listener );

				_tracker_client.addListener( tracker_client_stats_listener );
				
			}finally{

				this_mon.exit();
			}

      		// we need to set the state to "initialized" before kicking off the disk manager
      		// initialisation as it should only report its status while in the "initialized"
      		// state (see getState for how this works...)

			try{
				controller.initializeDiskManager( open_for_seeding );

			}finally{

					// only supply this the very first time the torrent starts so the controller can check
					// that things are ok. Subsequent restarts are under user control

				open_for_seeding	= false;
			}

		}catch( TRTrackerAnnouncerException e ){

			setFailed( "Tracker initialisation failed", e );
		}
	}

    public void
    checkLightSeeding(
    	boolean		full_sync )
    {
    	if ( full_sync ){
    		
    		if ( torrent != null ){
    		
    			buildURLGroupMap( torrent );
    		}
    	}
    
    	int status;
    	
    	String debug;
    	
       	if ( seedingRank.getLightSeedEligibility() == 0 ){
       		
	    	status = light_seeding_status;
	    	
	    	if ( status == 0 ){
	    		
	    		status = default_light_seeding_status;
	    		
	    		debug = (status==1?"active":"inactive") + " (default)";
	    		
	    	}else{
	    		
	    		debug = (status==1?"active":"inactive") + " (explicit)";
	    	}
       	}else{
       		
       		status = 2;
       		
       		debug = "inactive";
       	}
       	       	
	   	if ( status == 2 ){
	    		
	   		if ( _tracker_client_for_queued_download != null ){
	   			
	   			try{
	   	  			this_mon.enter();
	   	  				
	   	 			stopQueuedTrackerClient();
	   	  			
	   			}finally{
	   				
	   				this_mon.exit();
	   			}
	   		}
	   	
	       	seedingRank.setActivationStatus( debug );

    		return;
    	}
    	
	   	if ( _tracker_client_for_queued_download != null ){

	       	seedingRank.setActivationStatus( debug );

    		return;
    	}
    	    	
    	if ( getState() != DownloadManager.STATE_QUEUED ){
    		
           	seedingRank.setActivationStatus( debug + " but not queued" );

    		return;
    	}
    	
    	if ( !isDownloadComplete( false )){
    		
           	seedingRank.setActivationStatus( debug + " but not complete" );

    		return;
    	}
		
		try{
  			this_mon.enter();
  				
 			startQueuedTrackerClient();
  			
 			seedingRank.setActivationStatus( debug );
 			
		}finally{
			
			this_mon.exit();
		}
	}

    protected boolean
    isLightSeedTracker(
    	InetSocketAddress	isa )
    {
    	TRTrackerAnnouncer ta = _tracker_client_for_queued_download;
    	
    	if ( ta != null ){
    		
    		return( TorrentUtils.isTrackerAddress( ta.getTorrent(), isa ));
    	}
    	
    	return( false );
    }
    
	private void
	startQueuedTrackerClient()
	{
		if ( _tracker_client == null && _tracker_client_for_queued_download == null ){
							
			try{
				_tracker_client_for_queued_download = 
					TRTrackerAnnouncerFactory.create(
							torrent,
							new TRTrackerAnnouncerFactory.DataProvider()
							{
								@Override
								public String[]
								getNetworks()
								{
									return( download_manager_state.getNetworks());
								}
								
								@Override
								public HashWrapper 
								getTorrentHashOverride()
								{
									return( DownloadManagerImpl.this.getTorrentHashOverride());
								}
							});
  				
				_tracker_client_for_queued_download.addListener(
  					new TRTrackerAnnouncerListener(){
						
						@Override
						public void 
						urlRefresh()
						{
						}
						
						@Override
						public void 
						urlChanged(
							TRTrackerAnnouncer announcer, 
							URL old_url, 
							URL new_url, 
							boolean explicit)
						{
						}
						
						@Override
						public void 
						receivedTrackerResponse(
							TRTrackerAnnouncerRequest 		request, 
							TRTrackerAnnouncerResponse 		response)
						{
							if ( response.getStatus() == TRTrackerAnnouncerResponse.ST_ONLINE ){
							
								// don't need to do anything as scrape result will have been injected
							}
						}
					});
  				
				_tracker_client_for_queued_download.setAnnounceDataProvider(
  					new TRTrackerAnnouncerDataProvider(){
						
						@Override
						public void 
						setPeerSources(
							String[] allowed_sources)
						{
							DownloadManagerState	dms = getDownloadState();

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

									dms.setPeerSourcePermitted( s, false );
								}
							}	
						}
						
						@Override
						public boolean 
						isPeerSourceEnabled(String peer_source)
						{
							return( controller.isPeerSourceEnabled( peer_source ));
						}
						
						@Override
						public int 
						getUploadSpeedKBSec(boolean estimate)
						{
							return( 0 );
						}
						
						@Override
						public long 
						getTotalSent()
						{
							return( 0 );
						}
						
						@Override
						public long 
						getTotalReceived()
						{
							return( 0 );
						}
						
						@Override
						public int 
						getTCPListeningPortNumber()
						{
							return( controller.getTCPListeningPortNumber());
						}
						
						@Override
						public long 
						getRemaining()
						{
							return( stats.getRemaining());
						}
						
						@Override
						public int 
						getPendingConnectionCount()
						{
							return( 0 );
						}
						
						@Override
						public String 
						getName()
						{
							return( getDisplayName());
						}
						
						@Override
						public int 
						getMaxNewConnectionsAllowed(
							String network)
						{
							return( controller.getMaxConnections()[0]);
						}
						
						@Override
						public long 
						getFailedHashCheck()
						{
							return( 0 );
						}
						
						@Override
						public String 
						getExtensions()
						{
							return( controller.getTrackerClientExtensions());
						}
						
						@Override
						public int 
						getCryptoLevel()
						{
							return( DownloadManagerImpl.this.getCryptoLevel());
						}
						
						@Override
						public int 
						getConnectedConnectionCount()
						{
							return( 0 );
						}
					});
  				
				_tracker_client_for_queued_download.update( true );
  				
			}catch( Throwable e ){
				
				Debug.out( e );
			}
		}
	}
	
	private void
	stopQueuedTrackerClient()
	{
		if ( _tracker_client_for_queued_download != null ){

			_tracker_client_for_queued_download.stop( false );
			
			_tracker_client_for_queued_download.destroy();
			
			_tracker_client_for_queued_download = null;
		}
	}
	
	@Override
	public void
	setStateWaiting()
	{
		if ( checkResuming()){
			
			setForceStart( true );
			
		}else{

			controller.setStateWaiting();
		}
	}

  	public void
  	setStateFinishing()
  	{
  		controller.setStateFinishing();
  	}

  	@Override
	public void
  	setStateQueued()
  	{
  		if ( checkResuming()){
  			
  			setForceStart( true );
  			
  		}else{

  			controller.setStateQueued();
  		}
  	}

  	@Override
	public int
  	getState()
  	{
  		return( controller.getState());
  	}

  	@Override
	  public int
  	getSubState()
  	{
  		int	substate = controller.getSubState();
  		
  		if ( substate == DownloadManager.STATE_QUEUED ){
  			
  			if ( _tracker_client_for_queued_download != null ){
  				
  				return( DownloadManager.STATE_SEEDING );
  			}
  		}
  		
  		return( substate );
  	}

  	@Override
	public boolean
  	canForceRecheck()
  	{
		if ( getTorrent() == null ){

  				// broken torrent, can't force recheck

			return( false );
	  	}

  		return( controller.canForceRecheck());
  	}

  	protected void
  	restoreResumeData(
  		Map		data )
  	{
  		controller.forceRecheck( data );
  	}

  	@Override
	public void
  	forceRecheck()
  	{
  		controller.forceRecheck( null );
  	}

  	@Override
  	public boolean 
  	isForceRechecking()
  	{
  		return( controller.isForceRechecking());
  	}
  	@Override
	public void
  	setPieceCheckingEnabled(
  		boolean enabled )
  	{
  		controller.setPieceCheckingEnabled( enabled );
  	}

    @Override
    public void
    resetFile(
    	DiskManagerFileInfo		file )
    {
		int	state = getState();

	  	if ( 	state == DownloadManager.STATE_STOPPED ||
	  			state == DownloadManager.STATE_ERROR ){

	  		DiskManagerFactory.clearResumeData( this, file );

	  	}else{

	  		Debug.out( "Download not stopped" );
	  	}
    }

    @Override
    public void
    recheckFile(
    	DiskManagerFileInfo		file )
    {
		int	state = getState();

	  	if ( 	state == DownloadManager.STATE_STOPPED ||
	  			state == DownloadManager.STATE_ERROR ){

	  		DiskManagerFactory.recheckFile( this, file );

	  	}else{

	  		Debug.out( "Download not stopped" );
	  	}
	  }

    @Override
    public void
    requestAllocation(
    	List<DiskManagerFileInfo>		files )
    {
    	if ( files.isEmpty()){
    		
    		return;
    	}
    	
    	int state_before = getState();
    	
    	boolean paused = pause( true );
    	
    	try{  	  		
    		Map reqs = download_manager_state.getMapAttribute( DownloadManagerState.AT_FILE_ALLOC_REQUEST );
    		
    		if ( reqs == null ){
    			
    			reqs = new HashMap<>();
    			
    		}else{
    			
    			reqs = new HashMap<>( reqs );
    		}
    		
    		for ( DiskManagerFileInfo file: files ){
    			
    			reqs.put( String.valueOf( file.getIndex()), "" );
    		}
    		
    		download_manager_state.setMapAttribute( DownloadManagerState.AT_FILE_ALLOC_REQUEST, reqs );
    		
    		setDataAlreadyAllocated( false );
    		
    	}finally{
    		
    		if ( paused ){
    			
    			resume();
 
    			long start = SystemTime.getMonotonousTime();
    			
    			long max = 2*1000;
    			
    				// give it some time to get back to where it was
    			
    			while( true ){
    				
    				int current_state = getState();
    				
    				if ( current_state == state_before ){
    					
    					break;
    				}
    				
    				if ( SystemTime.getMonotonousTime() - start > max ){
    					
    					break;
    				}
    				
      				if ( 	current_state == DownloadManager.STATE_STOPPED || 
       						current_state == DownloadManager.STATE_ERROR ||
       	       				current_state == DownloadManager.STATE_QUEUED ||
       	            		current_state == DownloadManager.STATE_DOWNLOADING ||
       						current_state == DownloadManager.STATE_SEEDING ){
    					
      					break;
    				}
      				
    				if ( 	current_state == DownloadManager.STATE_ALLOCATING || 
    						current_state == DownloadManager.STATE_CHECKING ){
    				
    					max = 10*1000;
    				}
    				
    				try{
    					Thread.sleep(100);
    					
    				}catch( Throwable e ){
    					
    				}
    			}
    		}
    	}
    }
    
  	@Override
	  public void
  	startDownload()
  	{
  		message_mode = -1;	// reset it here

 		controller.startDownload( getTrackerClient() );
  	}

	@Override
	public void
	stopIt(
		int 		state_after_stopping,
		boolean 	remove_torrent,
		boolean 	remove_data)
	{
		stopIt( state_after_stopping, remove_torrent, remove_data, false );
	}

	@Override
	public void setStopReason(String reason) {
		setUserData( UD_KEY_STOP_REASON, reason );
	}

	@Override
	public String getStopReason() {
		return((String)getUserData( UD_KEY_STOP_REASON ));
	}

	@Override
	public void
	stopIt(
		int 		state_after_stopping,
		boolean 	remove_torrent,
		boolean		remove_data,
		boolean		for_removal )
	{
		if ( for_removal ){

			removing = true;
			
		}else{
			
			if ( download_manager_state.getFlag( DownloadManagerState.FLAG_METADATA_DOWNLOAD )){
				
				if ( state_after_stopping == DownloadManager.STATE_STOPPED ){
					
					try{
						FileUtil.log( "stopDownloadManager: stopping magnet" + ByteFormatter.encodeString( torrent.getHash()), new Exception());
						
					}catch( Throwable e ){
					}
				}
			}
		}

		try {
			boolean closing = state_after_stopping == DownloadManager.STATE_CLOSED;
			
			int curState = getState();
			
			boolean alreadyStopped = 	curState == STATE_STOPPED ||
										curState == STATE_STOPPING || 
										curState == STATE_ERROR;
			
			boolean skipSetTimeStopped = alreadyStopped	|| (closing && curState == STATE_QUEUED);

			if ( alreadyStopped ){
				
				resume_time = 0;
			}
			
			if (!skipSetTimeStopped) {
				download_manager_state.setLongAttribute(
						DownloadManagerState.AT_TIME_STOPPED, SystemTime.getCurrentTime());
			}

			controller.stopIt(state_after_stopping, remove_torrent, remove_data,for_removal );

			
		} finally {

			download_manager_state.setActive(false);
		}
	}

	private boolean
	checkResuming()
	{
		return( globalManager.resumingDownload( this ));
	}

	@Override
	public boolean
	pause(
		boolean		only_if_active )
	{
		return( globalManager.pauseDownload( this, only_if_active ));
	}

	@Override
	public boolean
	pause(
		boolean		only_if_active,
		long		_resume_time )
	{
			// obviously we should manage the timer+resumption but it works as it is at the moment...

		if ( isPaused()){
			
			if ( only_if_active ){
				
				return( false );
			}
			
			resume_time = _resume_time;
			
			return( true );
			
		}else{
		
			int curState = getState();
			
			if ( curState == STATE_STOPPED ){
				
				resume_time = _resume_time;
				
			}else{

				// we're not yet in a stopped state so indicate this with a negative value - it'll be corrected when the download stops

				resume_time	= -_resume_time;
			}
			
			return( globalManager.pauseDownload( this, only_if_active ));
		}
	}

	@Override
	public long
	getAutoResumeTime()
	{
		return( resume_time );
	}
	
	@Override
	public void
	setAutoResumeTime(
		long		time )
	{
		resume_time	= time;
	}
	
	@Override
	public boolean 
	stopPausedDownload()
	{
		if ( globalManager.stopPausedDownload( this )){
			
			resume_time = 0;
			  
			setStopReason( null );
			
				// this is needed so that listeners get a chance to notice the change (toolbar state for stop button needs
				// to go from enabled (when paused) to disabled (when stopped)
			
			listeners.dispatch( LDT_STATECHANGED, new Object[]{ this, new Integer( getState() )});
			
			return( true );
			
		}else{
			
			return( false );
		}
	}
	
	@Override
	public boolean
	isPaused()
	{
		return( globalManager.isPaused( this ));
	}

	@Override
	public void
	resume()
	{
		globalManager.resumeDownload( this );
	}

	@Override
	public boolean getAssumedComplete() {
		return assumedComplete;
	}

	@Override
	public boolean requestAssumedCompleteMode() {
		return( requestAssumedCompleteMode( false ));
	}
	
	protected boolean requestAssumedCompleteMode( boolean filePriorityChanged ) {

		boolean bCompleteNoDND = controller.isDownloadComplete(false);

		setAssumedComplete(bCompleteNoDND, filePriorityChanged );
		return bCompleteNoDND;
	}

	// Protected: Use requestAssumedCompleteMode outside of scope
	protected void setAssumedComplete(boolean _assumedComplete) {
		setAssumedComplete( _assumedComplete, false );
	}
	
	protected void setAssumedComplete(boolean _assumedComplete, boolean filePriorityChanged ) {
		if (_assumedComplete) {
			long completedOn = download_manager_state.getLongParameter(DownloadManagerState.PARAM_DOWNLOAD_COMPLETED_TIME);
			if (completedOn <= 0) {
				long now =SystemTime.getCurrentTime();
				
				download_manager_state.setLongParameter( DownloadManagerState.PARAM_DOWNLOAD_COMPLETED_TIME, now );
				
				long last_file = download_manager_state.getLongParameter( DownloadManagerState.PARAM_DOWNLOAD_FILE_COMPLETED_TIME );
				
				if ( last_file <= 0 ){
				
					Runnable set_it = 
						new Runnable()
						{
							public void
							run()
							{
					
									// get a sensible value from actual files - useful when adding-for-seeding
							
								long	last_mod = 0;
								
								DiskManagerFileInfo[] files = getDiskManagerFileInfoSet().getFiles();
								
								for ( DiskManagerFileInfo file: files ){
									
									if ( !file.isSkipped()){
										
										File f = file.getFile( true );
										
										if ( f.length() == file.getLength()){
											
											long mod = f.lastModified();
											
											if ( mod > last_mod ){
												
												last_mod = mod;
											}
										}
									}
								}
								
								if ( last_mod == 0 ){
									
									last_mod = now;
								}
								
								download_manager_state.setLongParameter( DownloadManagerState.PARAM_DOWNLOAD_FILE_COMPLETED_TIME, last_mod );
								
								if ( last_mod < now ){
									
										// update with more useful value
									
									download_manager_state.setLongParameter( DownloadManagerState.PARAM_DOWNLOAD_COMPLETED_TIME, last_mod );
								}
							}
						};
						
					synchronized( init_lock ){
						
						if ( !initialised ){
							
							post_init_tasks.add( set_it );
							
							set_it = null;
						}
					}
					
					if ( set_it != null ){
						
						set_it.run();
					}
				}
			}
		}else{
			if ( filePriorityChanged ){
				
					// download no longer complete due to user switching file from DND - reset
				
				download_manager_state.setLongParameter( DownloadManagerState.PARAM_DOWNLOAD_COMPLETED_TIME, 0 );
			}
		}

		if (assumedComplete == _assumedComplete) {
			return;
		}

		//Logger.log(new LogEvent(this, LogIDs.CORE, "setAssumedComplete("
		//		+ _assumedComplete + ") was " + assumedComplete));

		assumedComplete = _assumedComplete;

		if (!assumedComplete) {
			controller.setStateDownloading();
		}

		// NOTE: We don't set "stats.setDownloadCompleted(1000)" anymore because
		//       we can be in seeding mode with an unfinished torrent

		if ( position != -1 && globalManager.contains( this )){
			
			// we are in a new list, move to the top of the list so that we continue
			// seeding.
			// -1 position means it hasn't been added to the global list.  We
			// shouldn't touch it, since it'll get a position once it's adding is
			// complete. Also check explicitly that it is known to the global manager
			// as it is possible to come through here twice during init

			DownloadManager[] dms = { DownloadManagerImpl.this };

			// pretend we are at the bottom of the new list
			// so that move top will shift everything down one

			position = globalManager.getDownloadManagers().size() + 1;

			if ( _assumedComplete ){
				
				if (COConfigurationManager.getBooleanParameter( CFG_MOVE_COMPLETED_TOP )){
	
					globalManager.moveTop(dms);
	
				} else {
	
					globalManager.moveEnd(dms);
				}
			}else{
				
					// moved back to downloading - use the open-torrent-options default to decide where to put the download
					
				int qp = COConfigurationManager.getIntParameter( "Add Torrent Queue Position", 1 );
				
				if ( qp == 0 ){
					
					globalManager.moveTop(dms);
					
				}else{
					
					globalManager.moveEnd(dms);
				}
			}
			
			// we left a gap in incomplete list, fixup

			globalManager.fixUpDownloadManagerPositions();
		}

		listeners.dispatch(LDT_COMPLETIONCHANGED, new Object[] {
				this,
			Boolean.valueOf(_assumedComplete)
		});
	}


  @Override
  public int
  getNbSeeds()
  {
	  PEPeerManager peerManager = controller.getPeerManager();

	  if (peerManager != null){

		  return peerManager.getNbSeeds();
	  }

	  return 0;
  }

  @Override
  public int
  getNbPeers()
  {
	  PEPeerManager peerManager = controller.getPeerManager();

	  if (peerManager != null){

		  return peerManager.getNbPeers();
	  }

	  return 0;
  }



  	@Override
	  public String
  	getTrackerStatus()
  	{
  		TRTrackerAnnouncer tc = getTrackerClient();

  		if (tc != null){

  			return tc.getStatusString();
  		}

  			// no tracker, return scrape

  		if (torrent != null ) {

  			TRTrackerScraperResponse response = getTrackerScrapeResponse();

  			if (response != null) {
  				return response.getStatusString();

  			}
  		}

  		return "";
  	}

  	@Override
	public TRTrackerAnnouncer
  	getTrackerClient()
  	{
  		TRTrackerAnnouncer result = _tracker_client;
  		
  		if ( result == null ){
  			
  			result = _tracker_client_for_queued_download;
  		}
  		
  		return( result );
  	}

	@Override
	public void
	setAnnounceResult(
		DownloadAnnounceResult	result )
	{
		TRTrackerAnnouncer	cl = getTrackerClient();

		if ( cl == null ){

			// this can happen due to timing issues - not work debug spew for
			// Debug.out( "setAnnounceResult called when download not running" );

			return;
		}

		cl.setAnnounceResult( result );
	}

	@Override
	public void
	setScrapeResult(
		DownloadScrapeResult	result )
	{
		if ( torrent != null && result != null ){

			TRTrackerScraper	scraper = globalManager.getTrackerScraper();

			TRTrackerScraperResponse current_resp = getTrackerScrapeResponse();

			URL	target_url;

			if ( current_resp != null ){

				target_url = current_resp.getURL();

			}else{

				target_url = torrent.getAnnounceURL();
			}

			scraper.setScrape( torrent, target_url, result );
		}
	}

	@Override
	public int
	getNbPieces()
	{
		if ( torrent == null ){

			return(0);
		}

		return( torrent.getNumberOfPieces());
	}


	@Override
	public int
	getTrackerTime()
	{
		TRTrackerAnnouncer tc = getTrackerClient();

		if ( tc != null){

			return( tc.getTimeUntilNextUpdate());
		}

			// no tracker, return scrape

		if ( torrent != null ) {

			TRTrackerScraperResponse response = getTrackerScrapeResponse();

			if (response != null) {

				if (response.getStatus() == TRTrackerScraperResponse.ST_SCRAPING){

					return( -1 );
				}

				return (int)((response.getNextScrapeStartTime() - SystemTime.getCurrentTime()) / 1000);
			}
		}

		return( TRTrackerAnnouncer.REFRESH_MINIMUM_SECS );
	}


  	@Override
	public TOTorrent
  	getTorrent()
  	{
  		return( torrent );
  	}

 	private File	cached_save_location;
	private File	cached_save_location_result;

  	@Override
	public File
	getSaveLocation()
  	{
  			// this can be called quite often - cache results for perf reasons

  		File	save_location	= torrent_save_location;

  		if ( save_location == cached_save_location  ){

  			return( cached_save_location_result );
  		}

 		File	res;

 		if ( torrent == null || torrent.isSimpleTorrent()){

 			res = download_manager_state.getFileLink( 0, save_location );

 		}else{

 			res = save_location;
 		}

 		if ( res == null || res.equals(save_location) ){

 			res	= save_location;
 		}else{

 			res = FileUtil.getCanonicalFileSafe( res );
 		}

 		cached_save_location		= save_location;
 		cached_save_location_result	= res;

 		return( res );
 	}

  	@Override
	  public File
  	getAbsoluteSaveLocation()
  	{
  		return( torrent_save_location );
  	}

	public void setTorrentSaveDir(File _new_location, boolean locationIncludesName) {
		File new_location = locationIncludesName ? _new_location
				: FileUtil.newFile(_new_location, torrent_save_location.getName());
		File old_location = torrent_save_location;

		if ( FileUtil.areFilePathsIdentical( new_location, old_location)){
			
			return;
		}

  		// assumption here is that the caller really knows what they are doing. You can't
  		// just change this willy nilly, it must be synchronised with reality. For example,
  		// the disk-manager calls it after moving files on completing
  		// The UI can call it as long as the torrent is stopped.
  		// Calling it while a download is active will in general result in unpredictable behaviour!

		updateFileLinks( old_location, new_location);

		torrent_save_location = new_location;

		String key = torrent_save_location.getAbsolutePath() + "\n";

		torrent_save_location = FileUtil.getCanonicalFileSafe( torrent_save_location );

		download_manager_state.setAttribute(
			DownloadManagerState.AT_CANONICAL_SD_DMAP,
			key + torrent_save_location.getAbsolutePath());

		Logger.log(new LogEvent(this, LogIDs.CORE, "Torrent save directory changing from \"" + old_location.getPath() + "\" to \"" + new_location.getPath()));

		// Trying to fix a problem where downloads are being moved into the program
		// directory on my machine, and I don't know why...
		//Debug.out("Torrent save directory changing from \"" + old_location.getPath() + "\" to \"" + new_location.getPath());

		controller.fileInfoChanged();
		
		informLocationChange( null );
	}

	@Override
	public String
	getPieceLength()
	{
		if ( torrent != null ){
			return( DisplayFormatters.formatByteCountToKiBEtc(torrent.getPieceLength()));
		}

		return( "" );
	}

	@Override
	public String
	getTorrentFileName()
	{
		return torrentFileName;
	}

	@Override
	public void
	setTorrentFileName(
		String string)
	{
		torrentFileName = string;
	}

		// this is called asynchronously when a response is received

 	@Override
 	public void
 	setTrackerScrapeResponse(
 		TRTrackerScraperResponse	response )
 	{
  			// this is a reasonable place to pick up the change in active url caused by this scrape
  			// response and update the torrent's url accordingly

		Object[] res = getActiveScrapeResponse();

		URL	active_url = (URL)res[1];

		if ( active_url != null && torrent != null ){

			torrent.setAnnounceURL( active_url );
		}

		if (response != null) {
			int state = getState();
			if (state == STATE_ERROR || state == STATE_STOPPED) {
				long minNextScrape;
				if (response.getStatus() == TRTrackerScraperResponse.ST_INITIALIZING) {
					minNextScrape = SystemTime.getCurrentTime()
							+ (state == STATE_ERROR ? SCRAPE_INITDELAY_ERROR_TORRENTS
									: SCRAPE_INITDELAY_STOPPED_TORRENTS);
				} else {
					minNextScrape = SystemTime.getCurrentTime()
							+ (state == STATE_ERROR ? SCRAPE_DELAY_ERROR_TORRENTS
									: SCRAPE_DELAY_STOPPED_TORRENTS);
				}
				if (response.getNextScrapeStartTime() < minNextScrape) {
					response.setNextScrapeStartTime(minNextScrape);
				}
			} else if (!response.isValid() && response.getStatus() == TRTrackerScraperResponse.ST_INITIALIZING) {
				long minNextScrape;
				// Spread the scrapes out a bit.  This is extremely helpful on large
				// torrent lists, and trackers that do not support multi-scrapes.
				// For trackers that do support multi-scrapes, it will really delay
				// the scrape for all torrent in the tracker to the one that has
				// the lowest share ratio.
				int sr = getStats().getShareRatio();
				minNextScrape = SystemTime.getCurrentTime()
						+ ((sr > 10000 ? 10000 : sr + 1000) * 60);

				if (response.getNextScrapeStartTime() < minNextScrape) {
					response.setNextScrapeStartTime(minNextScrape);
				}
			}

			// Need to notify listeners, even if scrape result is not valid, in
			// case they parse invalid scrapes

			if ( response.isValid() && response.getStatus() == TRTrackerScraperResponse.ST_ONLINE ){

				long cache = ((((long)response.getSeeds())&0x00ffffffL)<<32)|(((long)response.getPeers())&0x00ffffffL);

				download_manager_state.setLongAttribute( DownloadManagerState.AT_SCRAPE_CACHE, cache );
			}

			tracker_listeners.dispatch(LDT_TL_SCRAPERESULT, response);
		}
	}

	@Override
	public TRTrackerScraperResponse
	getTrackerScrapeResponse()
	{
		Object[] res = getActiveScrapeResponse();

		return((TRTrackerScraperResponse)res[0]);
	}

		/**
		 * Returns the "first" online scrape response found, and its active URL, otherwise one of the failing
		 * scrapes
		 * @return
		 */

	protected Object[]
	getActiveScrapeResponse()
	{
		TRTrackerScraperResponse 	response	= null;
       	URL							active_url	= null;

		TRTrackerScraper	scraper = globalManager.getTrackerScraper();

		TRTrackerAnnouncer tc = getTrackerClient();

		if ( tc != null ){

			response = scraper.scrape( tc );
		}

		if ( response == null && torrent != null){

				// torrent not running. For multi-tracker torrents we need to behave sensibly
      			// here

			TRTrackerScraperResponse	non_null_response = null;

			TOTorrentAnnounceURLSet[]	sets;
			try {
				sets = torrent.getAnnounceURLGroup().getAnnounceURLSets();
			} catch (Exception e) {
				return( new Object[]{ scraper.scrape(torrent), active_url } );
			}

			if ( sets.length == 0 ){

				response = scraper.scrape(torrent);

			}else{

				URL							backup_url 		= null;
				TRTrackerScraperResponse	backup_response = null;

					// we use a fixed seed so that subsequent scrapes will randomise
    				// in the same order, as required by the spec. Note that if the
    				// torrent's announce sets are edited this all works fine (if we
    				// cached the randomised URL set this wouldn't work)

				Random	scrape_random = new Random(scrape_random_seed);

				for (int i=0;response==null && i<sets.length;i++){

					TOTorrentAnnounceURLSet	set = sets[i];

					URL[]	urls = set.getAnnounceURLs();

					List	rand_urls = new ArrayList();

					for (int j=0;j<urls.length;j++ ){

						URL url = urls[j];

						int pos = (int)(scrape_random.nextDouble() *  (rand_urls.size()+1));

						rand_urls.add(pos,url);
					}

					for (int j=0;response==null && j<rand_urls.size();j++){

						URL url = (URL)rand_urls.get(j);

						response = scraper.scrape(torrent, url);

						if ( response!= null ){

							int status = response.getStatus();

								// Exit if online

							if (status == TRTrackerScraperResponse.ST_ONLINE) {

								if ( response.isDHTBackup()){

										// we'll use this if we don't find anything better

									backup_url		= url;
									backup_response	= response;

									response = null;

									continue;
								}else{

									active_url	= url;

									break;
								}
							}

								// Scrape 1 at a time to save on outgoing connections

							if (	status == TRTrackerScraperResponse.ST_INITIALIZING ||
									status == TRTrackerScraperResponse.ST_SCRAPING) {

								break;
							}

								// treat bad scrapes as missing so we go on to
			 					// the next tracker

							if ( (!response.isValid()) || status == TRTrackerScraperResponse.ST_ERROR ){

								if ( non_null_response == null ){

									non_null_response	= response;
								}

								response	= null;
							}
						}
					}
				}

				if ( response == null ){

					if ( backup_response != null ){

						response 	= backup_response;
						active_url	= backup_url;

					}else{

						response = non_null_response;
					}
				}
			}
		}

		return( new Object[]{ response, active_url } );
	}


	@Override
	public List<TRTrackerScraperResponse>
	getGoodTrackerScrapeResponses()
	{
		List<TRTrackerScraperResponse> 	responses	= new ArrayList<>();

		if ( torrent != null){

			TRTrackerScraper	scraper = globalManager.getTrackerScraper();


			TOTorrentAnnounceURLSet[]	sets;

			try{
				sets = torrent.getAnnounceURLGroup().getAnnounceURLSets();

			}catch( Throwable e ){

				sets = new  TOTorrentAnnounceURLSet[0];
			}

			if ( sets.length == 0 ){

				TRTrackerScraperResponse response = scraper.peekScrape( torrent, null );

				if ( response!= null ){

					int status = response.getStatus();

					if ( status == TRTrackerScraperResponse.ST_ONLINE ) {

						responses.add( response );
					}
				}
			}else{

				for (int i=0; i<sets.length;i++){

					TOTorrentAnnounceURLSet	set = sets[i];

					URL[]	urls = set.getAnnounceURLs();

					for ( URL url: urls ){

						TRTrackerScraperResponse response = scraper.peekScrape( torrent, url );

						if ( response!= null ){

							int status = response.getStatus();

							if ( status == TRTrackerScraperResponse.ST_ONLINE ) {


								responses.add( response );
							}
						}
					}
				}
			}
		}

		return( responses );
	}


	@Override
	public void
	requestTrackerAnnounce(
		boolean	force )
	{
		TRTrackerAnnouncer tc = getTrackerClient();

		if ( tc != null ){

			tc.update( force );
			
		}else{
			
				// do the next best thing (and needed for incoming activation requests to a queued seed)
			
			requestTrackerScrape( force );
		}
	}

	@Override
	public void
	requestTrackerScrape(
		boolean	force )
	{
		if ( torrent != null ){

			TRTrackerScraper	scraper = globalManager.getTrackerScraper();

			scraper.scrape( torrent, force );
		}
	}

	protected void
	setTrackerRefreshDelayOverrides(
		int	percent )
	{
		TRTrackerAnnouncer tc = getTrackerClient();

		if ( tc != null ){

			tc.setRefreshDelayOverrides( percent );
		}
	}

	protected boolean
	activateRequest(
		int		count )
	{
			// activation request for a queued torrent

		for ( DownloadManagerActivationListener listener: activation_listeners ){

			try{
				if ( listener.activateRequest( count )){

					return( true );
				}
			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}

		return( false );
	}

	@Override
	public int
	getActivationCount()
	{
		return( controller.getActivationCount());
	}

	@Override
	public String
	getTorrentComment()
	{
		return torrent_comment;
	}

	@Override
	public String
	getTorrentCreatedBy()
	{
		return torrent_created_by;
	}

	@Override
	public long
	getTorrentCreationDate()
	{
		if (torrent==null){
			return(0);
		}

		return( torrent.getCreationDate());
	}


	@Override
	public GlobalManager
	getGlobalManager()
	{
		return( globalManager );
	}

	@Override
	public DiskManager
	getDiskManager()
	{
		return( controller.getDiskManager());
	}

	@Override
	public DiskManagerPiece[] 
	getDiskManagerPiecesSnapshot()
	{
		if ( destroyed || removing ){
			return( null );
		}
		
		return controller.getDiskManagerPiecesSnapshot();
	}
	
	@Override
	public DiskManagerFileInfoSet getDiskManagerFileInfoSet()
	{
		return controller.getDiskManagerFileInfoSet();
	}

	/**
	 * @deprecated use getDiskManagerFileInfoSet() instead
	 */
	@Override
	public DiskManagerFileInfo[]
   	getDiskManagerFileInfo()
	{
		return( controller.getDiskManagerFileInfo());
	}

	@Override
	public int getNumFileInfos() {

		return torrent == null ? 0 : torrent.getFileCount();
	}

	@Override
	public PEPeerManager
	getPeerManager()
	{
		return( controller.getPeerManager());
	}

	@Override
	public boolean isDownloadComplete(boolean bIncludeDND) {
		if (!bIncludeDND) {
			return assumedComplete;
		}

		return controller.isDownloadComplete(bIncludeDND);
	}

	@Override
	public void
	addListener(
		DownloadManagerListener	listener )
	{
		addListener(listener, true);
	}

	@Override
	public void
	addListener(
		DownloadManagerListener		listener,
		boolean 					triggerStateChange )
	{
		if (listener == null) {
			Debug.out("Warning: null listener");
			return;
		}

		try{
			listeners_mon.enter();

			listeners.addListener(listener);

			if ( triggerStateChange ){

				listeners.dispatch( listener, LDT_STATECHANGED, new Object[]{ this, new Integer( getState() )});
			}

				// we DON'T dispatch a downloadComplete event here as this event is used to mark the
				// transition between downloading and seeding, NOT purely to inform of seeding status

		} catch (Throwable t) {

			Debug.out("adding listener", t);

		}finally{

			listeners_mon.exit();
		}
	}

	@Override
	public void
	removeListener(
		DownloadManagerListener	listener )
	{
		try{
			listeners_mon.enter();

			listeners.removeListener(listener);

		}finally{

			listeners_mon.exit();
		}
	}

	private void
	checkFilePriorities(
		boolean	global_change )
	{
			// on global change to zero we reset file priorities as user seems to want to disable the default and manually clearing priorities across
			// all downloads is a pain
		
		int sfp_pieces = set_file_priority_high_pieces_rem;
		
		if ( global_change || sfp_pieces > 0 ){
			
			DiskManager	dm = getDiskManager();
			
			if ( dm != null ){

				long sfp_bytes = dm.getPieceLength() * sfp_pieces;
				
				DiskManagerFileInfoSet set = getDiskManagerFileInfoSet();
				
				DiskManagerFileInfo[] files = set.getFiles();
				
				int[]	priorities = null;
						
				for ( DiskManagerFileInfo file: files ){
					
					long	rem = file.getLength() - file.getDownloaded();
		    		
		    		int expected = sfp_bytes==0?0:( rem <= sfp_bytes?1:0);
		    					    				
		    		int existing = file.getPriority();
		    		
		    		if ( expected != existing && ( existing == 0 || existing == 1 )){
		    			
	    				if ( priorities == null ){
	    					
	    					priorities = new int[files.length];
	    					
	    					Arrays.fill( priorities, Integer.MIN_VALUE );
	    				}
	    				
	    				priorities[file.getIndex()] = expected;
		    		}
				}
				
				if ( priorities != null ){
				
					set.setPriority( priorities );
				}
			}
			
		}
	}
	
	/**
	 * Doesn't not inform if state didn't change from last inform call
	 */
	protected void
	informStateChanged()
	{
			// whenever the state changes we'll get called
		try{
			listeners_mon.enter();

			int		new_state 		= controller.getState();
			boolean new_force_start	= controller.isForceStart();

			if ( 	new_state != last_informed_state ||
					new_force_start != latest_informed_force_start ){

				last_informed_state	= new_state;

				latest_informed_force_start	= new_force_start;

				if ( resume_time < 0 ){

					if ( new_state == DownloadManager.STATE_STOPPED ){

						resume_time = -resume_time;
					}
				}else{

					resume_time = 0;
				}

				if ( new_state == DownloadManager.STATE_DOWNLOADING ){
					
					checkFilePriorities( false );
				}

				listeners.dispatch( LDT_STATECHANGED, new Object[]{ this, new Integer( new_state )});
			}

		}finally{

			listeners_mon.exit();
		}
	}

	protected void
	informDownloadEnded()
	{
		try{
			listeners_mon.enter();

			listeners.dispatch( LDT_DOWNLOADCOMPLETE, new Object[]{ this });

		}finally{

			listeners_mon.exit();
		}
	}

	protected void 
	informPrioritiesChange(
		List	files )
	{
		calcFilePriorityStats();
		
		controller.filePrioritiesChanged(files);

		try{
			listeners_mon.enter();

			for(int i=0;i<files.size();i++)
				listeners.dispatch( LDT_FILEPRIORITYCHANGED, new Object[]{ this, (DiskManagerFileInfo)files.get(i) });

		}finally{

			listeners_mon.exit();
		}

		requestAssumedCompleteMode( files.size() > 0 );
	}

	protected void
	informLocationChange(
		int	file_index )
	{
		try{
			listeners_mon.enter();

			listeners.dispatch( LDT_FILELOCATIONCHANGED, new Object[]{ this, getDiskManagerFileInfoSet().getFiles()[file_index] });

		}finally{

			listeners_mon.exit();
		}
	}
	
	protected void
	informLocationChange(
		DiskManagerFileInfo	file )
	{
		try{
			listeners_mon.enter();

			listeners.dispatch( LDT_FILELOCATIONCHANGED, new Object[]{ this, file });

		}finally{

			listeners_mon.exit();
		}
	}

	protected void
	informPriorityChange(
		List<DiskManagerFileInfo>	files )
	{
		informPrioritiesChange( files );
	}
	
	protected void
	informPieceDoneChanged(
		DiskManagerPiece	piece )
	{
		int sfp_pieces = set_file_priority_high_pieces_rem;
		
		if ( sfp_pieces > 0 ){
			
			DiskManager	dm = getDiskManager();
			
			if ( dm != null ){
					
				long sfp_bytes = dm.getPieceLength() * sfp_pieces;	// easier to work with bytes and close enough

				DMPieceList list = piece.getPieceList();
				
				for ( int i=0; i<list.size(); i++){
	
					DMPieceMapEntry entry = list.get( i );
	
					DiskManagerFileInfo file = entry.getFile();
					
					long	rem = file.getLength() - file.getDownloaded();

		    		if ( rem <= sfp_bytes ){
    						    			
		    			file.setPriority( 1 );
		    		}
				}
			}
		}
	}
	
	protected void
	informFileCompletionChange(
		DiskManagerFileInfo	file )
	{
		calcFilePriorityStats();
	}
	
	protected void
	informPositionChanged(
		int new_position )
	{
		try{
			listeners_mon.enter();

			int	old_position = position;

			if ( new_position != old_position ){

				position = new_position;

				listeners.dispatch(
					LDT_POSITIONCHANGED,
					new Object[]{ this, new Integer( old_position ), new Integer( new_position )});

				// an active torrent changed its position, scheduling needs to be updated
				if(getState() == DownloadManager.STATE_SEEDING || getState() == DownloadManager.STATE_DOWNLOADING)
					PeerControlSchedulerFactory.updateScheduleOrdering();
			}
		}finally{

			listeners_mon.exit();
		}
	}

	@Override
	public void
	addPeerListener(
		DownloadManagerPeerListener	listener )
	{
		addPeerListener(listener, true);
	}

	@Override
	public void
	addPeerListener(
		DownloadManagerPeerListener	listener,
		boolean bDispatchForExisting )
	{
		try{
			peer_listeners_mon.enter();

			peer_listeners.addListener( listener );

			if (!bDispatchForExisting){

				return; // finally will call
			}

			for ( PEPeer peer: current_peers.keySet()){

				peer_listeners.dispatch( listener, LDT_PE_PEER_ADDED, peer );
			}

			PEPeerManager	temp = controller.getPeerManager();

			if ( temp != null ){

				peer_listeners.dispatch( listener, LDT_PE_PM_ADDED, temp );
			}

		}finally{

			peer_listeners_mon.exit();
		}
	}

	@Override
	public void
	removePeerListener(
		DownloadManagerPeerListener	listener )
	{
		peer_listeners.removeListener( listener );
	}

	@Override
	public void
	addPieceListener(
		DownloadManagerPieceListener	listener )
	{
		addPieceListener(listener, true);
	}

	@Override
	public void
	addPieceListener(
		DownloadManagerPieceListener	listener,
		boolean 						bDispatchForExisting )
	{
		try{
			piece_listeners_mon.enter();

			piece_listeners.addListener( listener );

			if (!bDispatchForExisting)
				return; // finally will call

			for (int i=0;i<current_pieces.size();i++){

				piece_listeners.dispatch( listener, LDT_PE_PIECE_ADDED, current_pieces.get(i));
			}

		}finally{

			piece_listeners_mon.exit();
		}
	}

	@Override
	public void
	removePieceListener(
		DownloadManagerPieceListener	listener )
	{
		piece_listeners.removeListener( listener );
	}



	public void
	addPeer(
		PEPeer 		peer )
	{
		try{
			peer_listeners_mon.enter();

			if ( current_peers_unmatched_removal.remove( peer ) != null ){

				return;
			}

			current_peers.put( peer, "" );

			peer_listeners.dispatch( LDT_PE_PEER_ADDED, peer );

		}finally{

			peer_listeners_mon.exit();
		}
	}

	public void
	removePeer(
		PEPeer		peer )
	{
		try{
			peer_listeners_mon.enter();

			if ( current_peers.remove( peer ) == null ){

				long	now = SystemTime.getMonotonousTime();

				current_peers_unmatched_removal.put( peer, now );

				if ( current_peers_unmatched_removal.size() > 100 ){

					Iterator<Map.Entry<PEPeer, Long>> it = current_peers_unmatched_removal.entrySet().iterator();

					while( it.hasNext()){

						if ( now - it.next().getValue() > 10*1000 ){

							Debug.out( "Removing expired unmatched removal record" );

							it.remove();
						}
					}
				}
			}

			peer_listeners.dispatch( LDT_PE_PEER_REMOVED, peer );

		}finally{

			peer_listeners_mon.exit();
		}

			// if we're a seed and they're a seed then no point in keeping in the announce cache
			// if it happens to be there - avoid seed-seed connections in the future

		if ( (peer.isSeed() || peer.isRelativeSeed()) && isDownloadComplete( false )){

			TRTrackerAnnouncer	announcer = getTrackerClient();

			if ( announcer != null ){

				announcer.removeFromTrackerResponseCache( peer.getIp(), peer.getTCPListenPort());
			}
		}
	}

	@Override
	public PEPeer[]
	getCurrentPeers()
	{
		try{
			peer_listeners_mon.enter();

			return( current_peers.keySet().toArray(new PEPeer[current_peers.size()]));

		}finally{

			peer_listeners_mon.exit();

		}
	}

	public void
	addPiece(
		PEPiece 	piece )
	{
		try{
			piece_listeners_mon.enter();

			current_pieces.add( piece );

			piece_listeners.dispatch( LDT_PE_PIECE_ADDED, piece );

		}finally{

			piece_listeners_mon.exit();
		}
	}

	public void
	removePiece(
		PEPiece		piece )
	{
		try{
			piece_listeners_mon.enter();

			current_pieces.remove( piece );

			piece_listeners.dispatch( LDT_PE_PIECE_REMOVED, piece );

		}finally{

			piece_listeners_mon.exit();
		}
	}

	@Override
	public PEPiece[]
	getCurrentPieces()
	{
		try{
			piece_listeners_mon.enter();

			return (PEPiece[])current_pieces.toArray(new PEPiece[current_pieces.size()]);

		}finally{

			piece_listeners_mon.exit();

		}
	}

	protected void
	informWillBeStarted(
		PEPeerManager	pm )
	{
			// hack I'm afraid - sometimes we want synchronous notification of a peer manager's
			// creation before it actually starts

		List l = peer_listeners.getListenersCopy();

		for (int i=0;i<l.size();i++){

			try{
				((DownloadManagerPeerListener)l.get(i)).peerManagerWillBeAdded( pm );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

  	protected void
  	informStarted(
		PEPeerManager	pm )
  	{
		try{
			peer_listeners_mon.enter();

			peer_listeners.dispatch( LDT_PE_PM_ADDED, pm );
		}finally{

			peer_listeners_mon.exit();
		}

		TRTrackerAnnouncer tc = getTrackerClient();

		if ( tc != null ){

			tc.update( true );
		}
  	}

  	protected void
  	informStopped(
		PEPeerManager	pm,
		boolean			for_queue )	// can be null if controller was already stopped....
  	{
  		if ( pm != null ){

  			try{
  				peer_listeners_mon.enter();

  				peer_listeners.dispatch( LDT_PE_PM_REMOVED, pm );

  			}finally{

  				peer_listeners_mon.exit();
  			}
  		}

  		try{
  			this_mon.enter();

  			if ( _tracker_client != null ){

  				_tracker_client.addListener(
  					new TRTrackerAnnouncerListener()
					{
						@Override
						public void
						receivedTrackerResponse(
							TRTrackerAnnouncerRequest	request,
							TRTrackerAnnouncerResponse	response)
						{
							if ( _tracker_client == null ){

								response.setPeers(new TRTrackerAnnouncerResponsePeer[0]);
							}

							tracker_listeners.dispatch( LDT_TL_ANNOUNCERESULT, response );
							
							checkLightSeeding( false );
						}

						@Override
						public void
						urlChanged(
							TRTrackerAnnouncer	announcer,
							URL 				old_url,
							URL					new_url,
							boolean 			explicit )
						{
						}

						@Override
						public void
						urlRefresh()
						{
						}
					});

  				_tracker_client.removeListener( tracker_client_listener );

 				download_manager_state.setTrackerResponseCache(	_tracker_client.getTrackerResponseCache());

 				// we have serialized what we need -> can destroy retained stuff now
 				_tracker_client.getLastResponse().setPeers(new TRTrackerAnnouncerResponsePeer[0]);

 				// currently only report this for complete downloads...

 				_tracker_client.stop( for_queue && isDownloadComplete( false ));

 				_tracker_client.destroy();

 				_tracker_client = null;
  			}
  			  			
			stopQueuedTrackerClient();
  			
		}finally{

			this_mon.exit();
		}
  	}

	@Override
	public DownloadManagerStats
	getStats()
	{
		return( stats );
	}

	private void
	calcFilePriorityStats()
	{
		DiskManagerFileInfo[] files = getDiskManagerFileInfoSet().getFiles();

		int	max_priority 			= Integer.MIN_VALUE;
		int	max_priority_incomplete = Integer.MIN_VALUE;
		
		int priority_cumulative 			= 0;
		int priority_cumulative_incomplete	= 0;
		
		for ( DiskManagerFileInfo file: files ){

			if ( file.isSkipped()){

				continue;
			}
			
			int priority = file.getPriority();
			
			priority_cumulative += priority;
			
			if ( priority > max_priority ){
				
				max_priority = priority;
			}
							
			if ( file.getLength() != file.getDownloaded()){
				
				priority_cumulative_incomplete += priority;
				
				if ( priority > max_priority_incomplete ){

					max_priority_incomplete = priority;
				}
			}
		}
		
		stats.setFilePriorityStats( new int[]{ max_priority, max_priority_incomplete, priority_cumulative, priority_cumulative_incomplete  });
	}
	
	@Override
	public boolean
	isForceStart()
	{
		return( controller.isForceStart());
	}

	@Override
	public void
	setForceStart(
		boolean forceStart)
	{
		if ( forceStart ){

			checkResuming();
		}

		controller.setForceStart( forceStart );
	}

	  /**
	   * Is called when a download is finished.
	   * Activates alerts for the user.
	   *
	   * @param never_downloaded true indicates that we never actually downloaded
	   *                         anything in this session, but we determined that
	   *                         the download is complete (usually via
	   *                         startDownload())
	   *
	   * @author Rene Leonhardt
	   */

	protected void
	downloadEnded(
		boolean	never_downloaded )
	{
	    if ( !never_downloaded ){

	    	setAssumedComplete(true);

	    	informDownloadEnded();
	    }

	    TRTrackerAnnouncer	tc = getTrackerClient();

	    if ( tc != null ){

	    	DiskManager	dm = getDiskManager();

	    		// only report "complete" if we really are complete, not a dnd completion event

	    	boolean globalMask = COConfigurationManager.getBooleanParameter( ConfigKeys.Transfer.BCFG_PEERCONTROL_HIDE_PIECE );
	    	
	    	Boolean dmMask = download_manager_state.getOptionalBooleanAttribute( DownloadManagerState.AT_MASK_DL_COMP_OPTIONAL );
	    	
	    	boolean mask = dmMask==null?globalMask:dmMask;
	    	
	    	if ( dm != null && dm.getRemaining() == 0 && !mask ){

	    		tc.complete( never_downloaded );
	    	}
	    }
	}


	@Override
	public void
	addDiskListener(
		DownloadManagerDiskListener	listener )
	{
		controller.addDiskListener( listener );
	}

	@Override
	public void
	removeDiskListener(
		DownloadManagerDiskListener	listener )
	{
		controller.removeDiskListener( listener );
	}

	@Override
	public void
    addActivationListener(
    	DownloadManagerActivationListener listener )
	{
		activation_listeners.add( listener );
	}

    @Override
    public void
    removeActivationListener(
    	DownloadManagerActivationListener listener )
    {
    	activation_listeners.remove( listener );
    }

	@Override
	public int
	getHealthStatus()
	{
		int	state = getState();

		PEPeerManager	peerManager	 = controller.getPeerManager();

		TRTrackerAnnouncer tc = getTrackerClient();

		if( tc != null && peerManager != null && (state == STATE_DOWNLOADING || state == STATE_SEEDING)) {

			int nbSeeds = getNbSeeds();
			int nbPeers = getNbPeers();
			int nbRemotes = peerManager.getNbRemoteTCPConnections() + peerManager.getNbRemoteUTPConnections();

			TRTrackerAnnouncerResponse	announce_response = tc.getLastResponse();

			int trackerStatus = announce_response.getStatus();

			boolean isSeed = (state == STATE_SEEDING);

			if( (nbSeeds + nbPeers) == 0) {

				if( isSeed ){

					return WEALTH_NO_TRACKER;	// not connected to any peer and seeding
				}

				return WEALTH_KO;        // not connected to any peer and downloading
			}

      			// read the spec for this!!!!
      			// no_tracker =
      			//	1) if downloading -> no tracker
      			//	2) if seeding -> no connections		(dealt with above)

			if ( !isSeed ){

				if( 	trackerStatus == TRTrackerAnnouncerResponse.ST_OFFLINE ||
						trackerStatus == TRTrackerAnnouncerResponse.ST_REPORTED_ERROR){

					return WEALTH_NO_TRACKER;
				}
			}

			if( nbRemotes == 0 ){

				TRTrackerScraperResponse scrape_response = getTrackerScrapeResponse();

				if ( scrape_response != null && scrape_response.isValid()){

						// if we're connected to everyone then report OK as we can't get
						// any incoming connections!

					if ( 	nbSeeds == scrape_response.getSeeds() &&
							nbPeers == scrape_response.getPeers()){

						return WEALTH_OK;
					}
				}

				return WEALTH_NO_REMOTE;
			}

			return WEALTH_OK;

		} else if (state == STATE_ERROR) {
			return WEALTH_ERROR;
		}else{

			return WEALTH_STOPPED;
		}
	}

	@Override
	public Object[]
	getNATStatus()
	{
		int	state = getState();

		PEPeerManager	peerManager	 = controller.getPeerManager();

		TRTrackerAnnouncer tc = getTrackerClient();

		final int		nat_status;
		final String 	nat_info;
		
		if ( tc != null && peerManager != null && (state == STATE_DOWNLOADING || state == STATE_SEEDING)) {

			int	rem_tcp = peerManager.getNbRemoteTCPConnections();
			int rem_utp	= peerManager.getNbRemoteUTPConnections();
			
			if ( rem_tcp > 0 || rem_utp > 0 ){

				nat_status 	= ConnectionManager.NAT_OK;
				nat_info	= "Has remote " + (rem_tcp>0?"TCP":"uTP") + " connections";
			}else{

				long	last_good_time = peerManager.getLastRemoteConnectionTime();
	
				if ( last_good_time > 0 ){
	
						// half an hour's grace
	
					if ( SystemTime.getCurrentTime() - last_good_time < 30*60*1000 ){
	
						nat_status 	= ConnectionManager.NAT_OK;
						nat_info	= "Had a recent remote connection";
	
					}else{
	
						nat_status = ConnectionManager.NAT_PROBABLY_OK;
						nat_info	= "Had a remote connection at some point";
					}
				}else{
	
					TRTrackerAnnouncerResponse	announce_response = tc.getLastResponse();
		
					int trackerStatus = announce_response.getStatus();
		
					if( 	trackerStatus == TRTrackerAnnouncerResponse.ST_OFFLINE ||
							trackerStatus == TRTrackerAnnouncerResponse.ST_REPORTED_ERROR){
		
						nat_status 	= ConnectionManager.NAT_UNKNOWN;
						nat_info	= "Tracker offline";
						
					}else if ( SystemTime.getCurrentTime() - peerManager.getTimeStarted( false ) < 3*60*1000 ){
						
						// tracker's ok but no remotes - give it some time

						nat_status 	= ConnectionManager.NAT_UNKNOWN;
						nat_info	= "Tracker OK but not remote connections yet"; 
						
					}else{
		
						TRTrackerScraperResponse scrape_response = getTrackerScrapeResponse();
			
						if ( scrape_response != null && scrape_response.isValid()){
			
								// if we're connected to everyone then report OK as we can't get
								// any incoming connections!
			
							if ( 	peerManager.getNbSeeds() == scrape_response.getSeeds() &&
									peerManager.getNbPeers() == scrape_response.getPeers()){
			
								nat_status 	= ConnectionManager.NAT_UNKNOWN;
								nat_info	= "Connected to all known peers, hard to tell"; 
								
							}else if ( state == STATE_SEEDING  && scrape_response.getPeers() == 0 ){
			
								// can't expect incoming if we're seeding and there are no peers

								nat_status 	= ConnectionManager.NAT_UNKNOWN;
								nat_info	= "Seeding and no peers, status can't be determined"; 
							}else{
								
								nat_status 	= ConnectionManager.NAT_BAD;
								nat_info	= "There are peers, we should get some remote connections"; 
							}
						}else{
			
								// no scrape and we're seeding - don't use this as sign of badness as
								// we can't determine
			
							if ( state == STATE_SEEDING ){
			
								nat_status 	= ConnectionManager.NAT_UNKNOWN;
								nat_info	= "Tracker info unavailable and we're seeding, hard to tell"; 
							}else{
								
								nat_status 	= ConnectionManager.NAT_BAD;
								nat_info	= "Tracker info unavailable, assuming bad"; 
							}
						}
					}
				}
			}
		}else{

			nat_status 	= ConnectionManager.NAT_UNKNOWN;
			nat_info	= "Download not running, can't determine status";
		}
		
		return( new Object[]{ nat_status, nat_info });
	}

	@Override
	public int
	getPosition()
	{
		return position;
	}

	@Override
	public void
	setPosition(
		int new_position )
	{
		informPositionChanged( new_position );
	}

	@Override
	public void
	addTrackerListener(
		DownloadManagerTrackerListener	listener )
	{
		tracker_listeners.addListener( listener );
	}

	@Override
	public void
	removeTrackerListener(
		DownloadManagerTrackerListener	listener )
	{
  		tracker_listeners.removeListener( listener );
	}

	protected void
	deleteDataFiles()
	{
		DownloadManagerState state = getDownloadState();

		DiskManagerFactory.deleteDataFiles(
			torrent,
			torrent_save_location.getParent(),
			torrent_save_location.getName(),
			( 	state.getFlag( DownloadManagerState.FLAG_LOW_NOISE ) ||
				state.getFlag( DownloadManagerState.FLAG_FORCE_DIRECT_DELETE )));

		// Attempted fix for bug 1572356 - apparently sometimes when we perform removal of a download's data files,
		// it still somehow gets processed by the move-on-removal rules. I'm making the assumption that this method
		// is only called when a download is about to be removed.

		state.setFlag(DownloadManagerState.FLAG_DISABLE_AUTO_FILE_MOVE, true);
	}

	protected void
	deletePartialDataFiles()
	{
		DiskManagerFileInfo[] files = getDiskManagerFileInfoSet().getFiles();

		String abs_root = torrent_save_location.getAbsolutePath();

		for ( DiskManagerFileInfo file: files ){

			if ( !file.isSkipped()){

				continue;
			}
			
			int	storage_type = file.getStorageType() ;

			boolean compact = storage_type == DiskManagerFileInfo.ST_COMPACT || storage_type == DiskManagerFileInfo.ST_REORDER_COMPACT;

				// just to be safe...

			if ( file.getDownloaded() == file.getLength() && !compact ){

				continue;
			}

				// user may have switched a partially completed file to DND for some reason - be safe
				// and only delete compact files

			if ( compact ){

				File f = file.getFile( true );

				if ( f.exists()){

					if ( f.delete()){

						File parent = f.getParentFile();

						while ( parent != null ){

							if ( parent.isDirectory() && parent.listFiles().length == 0 ){

								if ( parent.getAbsolutePath().startsWith( abs_root )){

									if ( !parent.delete()){

										Debug.outNoStack( "Failed to remove empty directory: " + parent );

										break;

									}else{

										parent = parent.getParentFile();
									}

								}else{

									break;
								}
							}else{

								break;
							}
						}
					}else{

						Debug.outNoStack( "Failed to remove partial: " + f );
					}
				}
			}
		}
	}

	protected void
	deleteTorrentFile()
	{
		torrentFileExplicitlyDeleted = true;
		
		if ( torrentFileName != null ){
			
			TorrentUtils.delete(FileUtil.newFile(torrentFileName),getDownloadState().getFlag( DownloadManagerState.FLAG_LOW_NOISE ));
		}
	}


	@Override
	public DownloadManagerState
	getDownloadState()
	{
		return( download_manager_state );
	}


	/** To retreive arbitrary objects against a download. */

	@Override
	public Object
	getUserData(
		Object key )
	{
		Map<Object,Object> data_ref = data;

		if ( data_ref == null ){

			return null;
		}

		return( data_ref.get(key));
	}

		/** To store arbitrary objects against a download. */

	@Override
	public void
	setUserData(
		Object 		key,
		Object 		value)
	{
		try{
				// copy-on-write

			peer_listeners_mon.enter();

			Map<Object,Object> data_ref = data;

			if ( data_ref == null && value == null ){

				return;
			}

			if ( value == null ){

					// removal, data_ref not null here

				if ( data_ref.containsKey(key)){

					if ( data_ref.size() == 1 ){

						data_ref = null;

					}else{

						data_ref = new LightHashMap<>(data_ref);

						data_ref.remove( key );
					}
				}else{

					return;
				}
			}else{

				if ( data_ref == null ){

					data_ref = new LightHashMap<>();

				}else{

					data_ref = new LightHashMap<>(data_ref);
				}

				data_ref.put( key, value );
			}

			data = data_ref;

		}finally{

			peer_listeners_mon.exit();
		}
	}

	private static Object	TTP_KEY = new Object();

	@Override
	public Object
	getTaggableTransientProperty(
		String key)
	{
		synchronized( TTP_KEY ){

			LightHashMap<String,Object>	map = (LightHashMap<String,Object>)getUserData( TTP_KEY );

			if ( map == null ){

				return( null );

			}else{

				return( map.get( key ));
			}
		}
	}
	@Override
	public void
	setTaggableTransientProperty(String key, Object value)
	{
		synchronized( TTP_KEY ){

			LightHashMap<String,Object>	map = (LightHashMap<String,Object>)getUserData( TTP_KEY );

			if ( map == null ){

				if ( value == null ){

					return;
				}

				map = new LightHashMap<>();

				map.put( key, value );

				setUserData( TTP_KEY, map );

			}else{

				if ( value == null ){

					map.remove( key );

					if ( map.size() == 0 ){

						setUserData( TTP_KEY, null );
					}
				}else{

					map.put( key, value );
				}
			}
		}
	}

  @Override
  public boolean
  isDataAlreadyAllocated()
  {
  	return data_already_allocated;
  }

  @Override
  public void
  setDataAlreadyAllocated(
  	boolean already_allocated )
  {
    data_already_allocated = already_allocated;
  }

  @Override
  public void setSeedingRank(SeedingRank rank) {
    seedingRank = rank;
  }

  @Override
  public SeedingRank getSeedingRank() {
    return seedingRank;
  }

  @Override
  public long
  getCreationTime()
  {
  	return( creation_time );
  }

  @Override
  public void
  setCreationTime(
  	long		t )
  {
  	creation_time	= t;
  }

  @Override
  public boolean
  isSwarmMerging()
  {
	 return( globalManager.isSwarmMerging( this ));
  }
  
  @Override
  public String
  getSwarmMergingInfo()
  {
	 return( globalManager.getSwarmMergingInfo( this ));
  }

  @Override
  public int
  getExtendedMessagingMode()
  {
	  if ( message_mode == -1 ){

		  byte[] hash = null;

		  if ( torrent != null ){

			  try{
				  hash = torrent.getHash();

			  }catch( Throwable e ){
			  }
		  }

		  message_mode = (Integer)client_id_manager.getProperty( hash, ClientIDGenerator.PR_MESSAGING_MODE );
	  }

	  return( message_mode );
  }

  @Override
  public void
  setCryptoLevel(
	int		level )
  {
	  crypto_level = level;
  }

  @Override
  public int
  getCryptoLevel()
  {
	  return( crypto_level );
  }

  private volatile long[]	move_progress	= null;
  private volatile String	move_subtask	= "";
  private volatile int 		move_state		= ProgressListener.ST_NORMAL;
  
  public long[]
  getMoveProgress()
  {
	  DiskManager	dm = getDiskManager();

	  if ( dm != null ){
		  
		  return( dm.getMoveProgress());
		  
	  }else{
		  
		  return( move_progress );
	  }
  }
  
  public String
  getMoveSubTask()
  {
	  DiskManager	dm = getDiskManager();

	  if ( dm != null ){
		  
		 return( dm.getMoveSubTask());
		  
	  }else{
		  
		  return( move_subtask );
	  }
  }
  
  public void
  setMoveState(
	int	state )
  {
	  DiskManager	dm = getDiskManager();

	  if ( dm != null ){
		  		  
		  dm.setMoveState( state );
		  
	  }else{
		  
		 move_state = state;
	  }
  }
  
  @Override
  public void
  moveDataFiles(
	File	new_parent_dir )

  	throws DownloadManagerException
  {
	  moveDataFiles(new_parent_dir, null);
  }

  @Override
  public void
  moveDataFilesLive(
	File new_parent_dir)

  	throws DownloadManagerException
  {
	  moveDataFiles(new_parent_dir, null, true);
  }

  @Override
  public void renameDownload(String new_name) throws DownloadManagerException {
      this.moveDataFiles(null, new_name);
  }

  @Override
  public void
  moveDataFiles(
	final File 		destination,
	final String    new_name)

  	throws DownloadManagerException
  {
	  moveDataFiles( destination, new_name, false );
  }
  
  public void
  moveDataFiles(
	final File 		destination,
	final String    new_name,
	final boolean	live )

  	throws DownloadManagerException
  {
	  if (destination == null && new_name == null) {
		  throw new NullPointerException("destination and new name are both null");
	  }

	  if (!canMoveDataFiles()) {
		  throw new DownloadManagerException("canMoveDataFiles is false!");
	  }

	  if ( FileUtil.hasTask( this )){
		  
		  throw( new DownloadManagerException( "Move operation already in progress" ));
	  }	  

	  /**
	   * Test to see if the download is to be moved somewhere where it already
	   * exists. Perhaps you might think it is slightly unnecessary to check this,
	   * but I would prefer this test left in - we want to prevent pausing
	   * unnecessarily pausing a running torrent (it fires off listeners, which
	   * might try to move the download).
	   *
	   * This helps us avoid a situation with AzCatDest...
	   */
	  SaveLocationChange slc = new SaveLocationChange();
	  slc.download_location = destination;
	  slc.download_name = new_name;

	  File current_location = getSaveLocation();
	  if ( FileUtil.areFilePathsIdentical( slc.normaliseDownloadLocation(current_location), current_location)) {
		  
		  return;
	  }

	  Runnable target = ()->{
		  
		  try{
				if ( live ){

					moveDataFilesSupport0( destination, new_name );

				}else{

					moveDataFilesSupport( destination, new_name );
				}
			}catch( DownloadManagerException e ){

				throw( new RuntimeException( e ));
			}
	  };
	  	  
	  DiskManagerUtil.runMoveTask( this, destination, target, this );
  }

  void
  moveDataFilesSupport(
	File 	new_parent_dir,
	String 	new_filename)

  	throws DownloadManagerException
  	{
	  boolean is_paused = this.pause( true );
	  try {moveDataFilesSupport0(new_parent_dir, new_filename);}
	  finally {if (is_paused) {this.resume();}}
  	}

  void
  moveDataFilesSupport0(
		  File 		new_parent_dir,
		  String 		new_filename )

				  throws DownloadManagerException
  {
	  if (!canMoveDataFiles()){
		  throw new DownloadManagerException("canMoveDataFiles is false!");
	  }

	  if (new_filename != null) {new_filename = FileUtil.convertOSSpecificChars(new_filename,false);}

	  // old file will be a "file" for simple torrents, a dir for non-simple

	  File	old_file = getSaveLocation();

	  try{
		  old_file = FileUtil.getCanonicalFileSafe( old_file );
		  if (new_parent_dir != null) {new_parent_dir = FileUtil.getCanonicalFileSafe( new_parent_dir);}

	  }catch( Throwable e ){
		  Debug.printStackTrace(e);
		  throw( new DownloadManagerException( "Failed to get canonical paths", e ));
	  }

	  final File current_save_location = old_file;
	  File new_save_location = FileUtil.newFile(
			  (new_parent_dir == null) ? old_file.getParentFile() : new_parent_dir,
					  (new_filename == null) ? old_file.getName() : new_filename
			  );

	  if ( FileUtil.areFilePathsIdentical( current_save_location, new_save_location)){
		  // null operation
		  return;
	  }

	  DiskManager	dm = getDiskManager();

	  if ( dm == null || dm.getFiles() == null){

		  if ( !old_file.exists()){

			  // files not created yet

			  FileUtil.mkdirs(new_save_location.getParentFile());

			  if ( torrent.isSimpleTorrent()){
				  
				  if ( controller.getDiskManagerFileInfoSet().getFiles()[0].setLinkAtomic( new_save_location, false )){
				  
				  	setTorrentSaveDir(new_save_location, true);
					  	
				  }else{
					  
					  throw new DownloadManagerException( "rename operation failed");
				  }
			  }else{
				  
				  setTorrentSaveDir( new_save_location, true);
			  }
			  
			  return;
		  }

		  new_save_location = FileUtil.getCanonicalFileSafe( new_save_location );

		  int[]		files_accepted 		= { 0 };
		  int[]		files_skipped		= { 0 };
		  int[]		files_done			= { 0 };
		  long[]	total_size_bytes	= { 0 };
		  long[]	total_done_bytes	= { 0 };
		  
		  
		  FileUtil.ProgressListener	pl = 
			new FileUtil.ProgressListener()
		  	{
				public void
				setTotalSize(
					long	size )
				{
					total_size_bytes[0] = size;
				}
				
				@Override
				public void 
				setCurrentFile(
					File file )
				{
					files_done[0]++;
					
					move_subtask = file.getName();
				}
				
				public void
				bytesDone(
					long	num )
				{
					total_done_bytes[0] += num;
					
					long total_size = total_size_bytes[0];
					
					move_progress = new long[]{ total_size==0?0:(int)(Math.min( 1000, (1000*total_done_bytes[0])/total_size )), total_size };
				}
				
				@Override
				public int 
				getState()
				{
					return( move_state );
				}
				
				public void
				complete()
				{
					move_progress = new long[]{ 1000, total_size_bytes[0] };
				}
		  	};
			
		  String log_str = "Move inactive \"" + getDisplayName() + "\" from  " + current_save_location + " to " + new_save_location;

		  try{
			  FileUtil.log( log_str + " starts" );

			  move_progress = new long[2];
			  move_subtask	= "";
			  
			  if ( FileUtil.areFilePathsIdentical( old_file, new_save_location )){
	
				  // nothing to do
	
			  } else if (torrent.isSimpleTorrent()) {
	
				  DiskManagerFileInfo file = getDiskManagerFileInfoSet().getFiles()[0];
				  
				  pl.setTotalSize( file.getFile( true ).length());
	
				  files_accepted[0] = 1;
				  files_done[0]		= 1;
				  
				  if ( file.setLinkAtomic( new_save_location, false, pl )){
					  
					  setTorrentSaveDir( new_save_location, true);
					  
				  }else{
					  
					  throw new DownloadManagerException( "Rename operation failed: " + file.getLastError());
				  }
	
				  /*
				  // Have to keep the file name in sync if we're renaming.
				  //if (controller.getDiskManagerFileInfo()[0].setLinkAtomic(new_save_location)) {
				  if ( FileUtil.renameFile( old_file, new_save_location )){
	
					  setTorrentSaveDir( new_save_location.getParentFile().toString(), new_save_location.getName());
	
				  }else{
	
					  throw( new DownloadManagerException( "rename operation failed" ));
				  }
				  //} else {throw new DownloadManagerException( "rename operation failed");}
				   */
			  }else{
	
				  if (FileUtil.isAncestorOf(old_file, new_save_location)) {
	
					  Logger.logTextResource(new LogAlert(this, LogAlert.REPEATABLE,
							  LogAlert.AT_ERROR, "DiskManager.alert.movefilefails"),
							  new String[] {old_file.toString(), "Target is sub-directory of files" });
	
					  throw( new DownloadManagerException( "Rename operation failed: Target is sub-directory of files" ));
				  }
	
				  long	total_size = 0;
				  
				  // The files we move must be limited to those mentioned in the torrent.
				  
				  final HashSet<File> files_to_move = new HashSet<>();
	
				  // Required for the adding of parent directories logic.
				  
				  files_to_move.add(null);
				  
				  DiskManagerFileInfo[] info_files = getDiskManagerFileInfoSet().getFiles();
				  
				  for (int i=0; i<info_files.length; i++) {
					  File f = info_files[i].getFile(true);
					  total_size += f.length();
					  f = FileUtil.getCanonicalFileSafe(f);
					  
					  boolean added_entry = files_to_move.add(f);
	
					  /**
					   * Start adding all the parent directories to the
					   * files_to_move list. Doesn't matter if we include
					   * files which are outside of the file path, the
					   * renameFile call won't try to move those directories
					   * anyway.
					   */
					  while (added_entry) {
						  f = f.getParentFile();
						  added_entry = files_to_move.add(f);
					  }
				  }
				  FileFilter ff = 
					new FileFilter() 
				  	{
					  @Override
					  	public boolean 
					  	accept(
					  	    File f )
					  {  
						  boolean do_it = files_to_move.contains(f);
						  
						  if ( f != null && f.isFile()){
							  
							  if ( do_it ){
								  
								  files_accepted[0]++;
								  
							  }else{
								  
								  files_skipped[0]++;
								  
								  FileUtil.log( "File not selected for move: " + f.getAbsolutePath());
							  }
						  }
						  
						  return( do_it );
					  }
				  };
	
				  pl.setTotalSize( total_size );
				  
				  String result = FileUtil.renameFile( old_file, new_save_location, false, ff, pl );
				  
				  if ( result == null ){
	
					  setTorrentSaveDir( new_save_location, true);
	
				  }else{
	
					  if ( new_save_location.isDirectory()){
							
						  TorrentUtils.recursiveEmptyDirDelete( new_save_location, false );
					  }
					  
					  throw( new DownloadManagerException( "Rename operation failed: " + result ));
				  }
	
				  if (  current_save_location.isDirectory()){
	
					  TorrentUtils.recursiveEmptyDirDelete( current_save_location, false );
				  }
			  }
		  }finally {
			  
			  pl.complete();
			  
			  move_progress = null;
			  move_subtask	= "";
			  move_state	= ProgressListener.ST_NORMAL;
			  
			  FileUtil.log( 
					 log_str + 
					 	" ends (files accepted=" + files_accepted[0] + 
					 	", skipped=" + files_skipped[0] + ", done=" + files_done[0] +
					 	"; bytes total=" + total_size_bytes[0] + ", done=" + total_done_bytes[0] + ")");
		  }
	  }else{
		  dm.moveDataFiles( new_save_location.getParentFile(), new_save_location.getName());
	  }
	  
	  	// this is a manual move - we want to prevent any future move-on-complete from kicking in and moving the files
	  	// elsewhere.
	  
	  if ( getAssumedComplete()){
	  
		  getDownloadState().setFlag(DownloadManagerState.FLAG_MOVE_ON_COMPLETION_DONE, true);
	  }
  }

  @Override
	public void
	copyDataFiles(
		File									dest_parent_dir,
		CoreOperationTask.ProgressCallback		cb )

		throws DownloadManagerException
	{
		if ( dest_parent_dir.exists()){

			if ( !dest_parent_dir.isDirectory()){

				throw( new DownloadManagerException( "'" + dest_parent_dir + "' is not a directory" ));
			}
		}else{

			if ( !dest_parent_dir.mkdirs()){

				throw( new DownloadManagerException( "failed to create '" + dest_parent_dir + "'" ));
			}
		}

	  DiskManagerFileInfo[] files = controller.getDiskManagerFileInfoSet().getFiles();

	  FileUtil.ProgressListener pl = null;
			  
	  if ( cb != null ){
		  
		  long	_total_size = 0;
		  
		  for ( DiskManagerFileInfo file: files ){

			  if ( !file.isSkipped() && file.getDownloaded() == file.getLength()){
				  
				  _total_size += file.getLength();
			  }
		  }
		  
		  long total_size = _total_size;
		  
		  cb.setSize( total_size );
		  
		  pl = 
			new FileUtil.ProgressListener()
		  	{
			  	long	total_done 	= 0;
			  	
				public void
				setTotalSize(
					long	size )
				{
					// not used
				}
				
				@Override
				public void 
				setCurrentFile(
					File file )
				{
					cb.setSubTaskName( file.getName());
				}
				
				public void
				bytesDone(
					long	num )
				{
					if ( total_size > 0 ){
						
						total_done += num;
						
						long prog = (total_done*1000)/total_size;
						
						cb.setProgress((int)prog);
					}
				}
				
				@Override
				public int 
				getState()
				{
					switch( cb.getTaskState()){
					
						case ProgressCallback.ST_PAUSE:{
							return( ProgressListener.ST_PAUSED );
						}
						case ProgressCallback.ST_CANCEL:{
							return( ProgressListener.ST_CANCELLED );
						}
						default:{
							return( ProgressListener.ST_NORMAL );
						}
					}
				}
				
				public void
				complete()
				{
					cb.setProgress( 1000 );
				}
		  	};
	  }
	  
	  
	  if ( torrent.isSimpleTorrent()){

		  File file_from = files[0].getFile( true );

		  try{
			  File file_to = FileUtil.newFile( dest_parent_dir, file_from.getName());

			  if ( file_to.exists()){

				  if ( file_to.length() != file_from.length()){

					  throw( new Exception( "target file '" + file_to + " already exists" ));
				  }
			  }else{

				  FileUtil.copyFileWithException( file_from, file_to, pl );
			  }
		  }catch( Throwable e ){

			  throw( new DownloadManagerException( "copy of '" + file_from + "' failed", e ));
		  }
	  }else{

		  try{
			  File sl_file = getSaveLocation();

			  dest_parent_dir = FileUtil.newFile( dest_parent_dir, sl_file.getName());

			  if ( !dest_parent_dir.isDirectory()){

				  dest_parent_dir.mkdirs();
			  }

			  for ( DiskManagerFileInfo file: files ){

				  if ( !file.isSkipped() && file.getDownloaded() == file.getLength()){

					  File file_from = file.getFile( true );

					  try{
						  String relativePath = FileUtil.getRelativePath(sl_file, file_from);

						  if (relativePath != null && !relativePath.isEmpty()) {

							  File file_to = FileUtil.newFile( dest_parent_dir, relativePath);

							  if ( file_to.exists()){

								  if ( file_to.length() != file_from.length()){

									  throw( new Exception( "target file '" + file_to + " already exists" ));
								  }
							  }else{

								  File parent = file_to.getParentFile();

								  if ( !parent.exists()){

									  if ( !parent.mkdirs()){

										  throw( new Exception( "Failed to make directory '" + parent + "'" ));
									  }
								  }

								  FileUtil.copyFileWithException( file_from, file_to, pl );
							  }
						  }
					  }catch( Throwable e ){

						  throw( new DownloadManagerException( "copy of '" + file_from + "' failed", e ));
					  }
				  }
			  }
		  }catch( Throwable e ){

			  throw( new DownloadManagerException( "copy failed", e ));
		  }
	  }
  }

  private void
  copyTorrentFile(
	 File	parent_dir ) throws DownloadManagerException
  {
	  File	file = FileUtil.newFile( getTorrentFileName() );

	  File target = FileUtil.newFile( parent_dir, file.getName());
	  
	  if ( !file.exists()){
		  
		  		// try to recover using internal torrent
		  
		  TOTorrent internal_torrent = download_manager_state.getTorrent();
		  
		  try{
			  TOTorrent clone = TorrentUtils.cloneTorrent( internal_torrent );
			  
			  clone.removeAdditionalProperties();
			  
			  TorrentUtils.writeToFile( clone, target, false );
			  
			  return;
			  
		  }catch( Throwable e ){
			  
			  Debug.out( e );
		  }
		  
		  throw( new DownloadManagerException( "Torrent file '" + file + "' doesn't exist" ));
	  }
	  
	  try{
		  FileUtil.copyFileWithException( file, target, null);
		  
	  }catch( Throwable e ){
		  
		  throw( new DownloadManagerException( "Failed to copy torrent file", e ));
	  }
  }
  
  public boolean
  canExportDownload()
  {
	  return( getAssumedComplete());
  }
  
  public void
  exportDownload( File parent_dir ) throws DownloadManagerException
  {
	  if ( !canExportDownload()){
		  
		  throw( new DownloadManagerException( "Not in correct state" ));
	  }
	  
	  try{
		  FileUtil.runAsTask(
				  CoreOperation.OP_DOWNLOAD_EXPORT,
				  new CoreOperationTask()
				  {
					  private ProgressCallback cb =
						  new CoreOperationTask.ProgressCallbackAdapter()
						  {
						  	  private int state = ST_NONE;
						  	
							  @Override
							  public int 
							  getSupportedTaskStates()
							  {
								  return( ST_PAUSE | ST_RESUME | ST_CANCEL | ST_SUBTASKS );
							  }
						  };

					  @Override
					  public String 
					  getName()
					  {
						  return( getDisplayName());
					  }

					  @Override
					  public DownloadManager 
					  getDownload()
					  {
						  return( DownloadManagerImpl.this );
					  }

					  @Override
					  public String[] 
					  getAffectedFileSystems()
					  {
						  return( FileUtil.getFileStoreNames( getAbsoluteSaveLocation(), parent_dir ));
					  }

					  @Override
					  public void
					  run(
						  CoreOperation operation)
					  {
						  try{
							  copyDataFiles( parent_dir, cb );

							  copyTorrentFile( parent_dir );

						  }catch( Throwable e ){

							  throw( new RuntimeException( e ));
						  }
					  }

					  @Override
					  public ProgressCallback 
					  getProgressCallback()
					  {
						  return( cb );
					  }
				  });
		  
	  }catch( Throwable e ){
		  
		  Throwable f = e.getCause();
		  
		  if ( f instanceof DownloadManagerException ){
			  
			  throw((DownloadManagerException)f);
		  }
		  
		  throw( new DownloadManagerException( "Export failed", e ));
	  }

  }
  
  @Override
  public void moveTorrentFile(File new_parent_dir) throws DownloadManagerException {
	  this.moveTorrentFile(new_parent_dir, null);
  }

  @Override
  public void moveTorrentFile(File new_parent_dir, String new_name) throws DownloadManagerException {
	  SaveLocationChange slc = new SaveLocationChange();
	  slc.torrent_location = new_parent_dir;
	  slc.torrent_name = new_name;

	  File torrent_file_now = FileUtil.newFile(getTorrentFileName());
	  if (!slc.isDifferentTorrentLocation(torrent_file_now)) {return;}

	  boolean is_paused = this.pause( true );
	  try {moveTorrentFile0(new_parent_dir, new_name);}
	  finally {if (is_paused) {this.resume();}}
  }


  private void moveTorrentFile0(
	File	new_parent_dir,
	String  new_name)

	throws DownloadManagerException
  {

	  if ( !canMoveDataFiles()){

		  throw( new DownloadManagerException( "Cannot move torrent file" ));
	  }

	  setTorrentFile(new_parent_dir, new_name);
  }

  @Override
  public void setTorrentFile(File new_parent_dir, String new_name) throws DownloadManagerException {

	  File	old_file = FileUtil.newFile( getTorrentFileName() );

	  if ( !old_file.exists()){
		  
		  // used to fail here but we might as well use our internal copy of the torrent file
		  // don't resurrect it if the user has explicity deleted it (on removal, required  for
		  // 'move on remove' to work correctly when user requests torrent delete in removal dialog)
		  
		  if ( !torrentFileExplicitlyDeleted ){
		  
			  TOTorrent internal_torrent = download_manager_state.getTorrent();
			  
			  try{
				  TOTorrent clone = TorrentUtils.cloneTorrent( internal_torrent );
				  
				  clone.removeAdditionalProperties();
				  
				  TorrentUtils.writeToFile( clone, old_file, false );
				  
			  }catch( Throwable e ){
				  
				  Debug.out( e );
			  }
		  }
		  
		  if ( !old_file.exists()){
		  
			  Debug.out( "torrent file '" + old_file + "' doesn't exist!" );
		  
			  return;
		  }
	  }

	  if (new_parent_dir == null) {new_parent_dir = old_file.getParentFile();}
	  if (new_name == null) {new_name = old_file.getName();}
	  File new_file = FileUtil.newFile(new_parent_dir, new_name);

	  try{
		  old_file = FileUtil.getCanonicalFileSafe( old_file );
		  new_file = FileUtil.getCanonicalFileSafe( new_file );

	  }catch( Throwable e ){

		  Debug.printStackTrace(e);

		  throw( new DownloadManagerException( "Failed to get canonical paths", e ));
	  }

	  // Nothing to do.
	  if ( FileUtil.areFilePathsIdentical( new_file, old_file)) {return;}

	  if (TorrentUtils.move( old_file, new_file )){
		  setTorrentFileName( new_file.toString());

	  }else{
		  throw( new DownloadManagerException( "rename operation failed" ));
	  }
  }

  @Override
  public boolean isInDefaultSaveDir() {
	  return DownloadManagerDefaultPaths.isInDefaultDownloadDir(this);
  }

  @Override
  public boolean
  seedPieceRecheck()
  {
	  PEPeerManager pm = controller.getPeerManager();

	  if ( pm != null ){

		  return( pm.seedPieceRecheck());
	  }

	  return( false );
  }

  @Override
  public void
  addRateLimiter(
	  LimitedRateGroup	group,
	  boolean			upload )
  {
	  controller.addRateLimiter( group, upload );
  }

  @Override
  public LimitedRateGroup[]
  getRateLimiters(
	boolean	upload )
  {
	  return( controller.getRateLimiters( upload ));
  }

  @Override
  public void
  removeRateLimiter(
	  LimitedRateGroup	group,
	  boolean				upload )
  {
	  controller.removeRateLimiter( group, upload );
  }

  @Override
  public boolean
  isTrackerError()
  {
		TRTrackerAnnouncer announcer = getTrackerClient();

		if ( announcer != null ){

			TRTrackerAnnouncerResponse resp = announcer.getLastResponse();

			if ( resp != null ){

				if ( resp.getStatus() == TRTrackerAnnouncerResponse.ST_REPORTED_ERROR ){

					return( true );
				}
			}
		}else{

			TRTrackerScraperResponse resp = getTrackerScrapeResponse();

			if ( resp != null ){

				if ( resp.getStatus() == TRTrackerScraperResponse.ST_ERROR ){

					return( true );
				}
			}
		}

		return( false );
  }

  @Override
  public boolean
  isUnauthorisedOnTracker()
  {
		TRTrackerAnnouncer announcer = getTrackerClient();

		String	status_str = null;

		if ( announcer != null ){

			TRTrackerAnnouncerResponse resp = announcer.getLastResponse();

			if ( resp != null ){

				if ( resp.getStatus() == TRTrackerAnnouncerResponse.ST_REPORTED_ERROR ){

					status_str = resp.getStatusString();
				}
			}
		}else{

			TRTrackerScraperResponse resp = getTrackerScrapeResponse();

			if ( resp != null ){

				if ( resp.getStatus() == TRTrackerScraperResponse.ST_ERROR ){

					status_str = resp.getStatusString();
				}
			}
		}

		if ( status_str != null ){

			status_str = status_str.toLowerCase();

			if ( 	status_str.contains( "not authorised" ) ||
					status_str.contains( "not authorized" )){

				return( true );
			}
		}

		return( false );
  }

  	@Override
	public List<TrackerPeerSource>
  	getTrackerPeerSources()
  	{
  		try{
  			this_mon.enter();

  			Object[] tps_data = (Object[])getUserData( TPS_Key );

  			List<TrackerPeerSource>	tps;

  			if ( tps_data == null ){

  				tps = new ArrayList<>();

  				TOTorrentListener tol =
  					new TOTorrentListener()
					{
						@Override
						public void
						torrentChanged(
							TOTorrent	torrent,
							int 		type,
							Object		data )
						{
							if ( type == TOTorrentListener.CT_ANNOUNCE_URLS ){

					  			torrent.removeListener( this );

					  			informTPSChanged();
							}
						}
					};

  				setUserData( TPS_Key, new Object[]{ tps, tol });

  				Download plugin_download = PluginCoreUtils.wrap( this );

  				if ( isDestroyed() || plugin_download == null ){

  					return( tps );
  				}

  					// tracker peer sources

  				final TOTorrent t = getTorrent();

  				if ( t != null ){

  					t.addListener( tol );

  					TOTorrentAnnounceURLSet[] sets = t.getAnnounceURLGroup().getAnnounceURLSets();

  					if ( sets.length == 0 ){

  						sets = new TOTorrentAnnounceURLSet[]{ t.getAnnounceURLGroup().createAnnounceURLSet( new URL[]{ torrent.getAnnounceURL()})};
  					}

  						// source per set
 					
					for ( final TOTorrentAnnounceURLSet set: sets ){

						final URL[] urls = set.getAnnounceURLs();
				
						if ( urls.length == 0 || TorrentUtils.isDecentralised( urls[0] )){

							continue;
						}

						tps.add(
							new TrackerPeerSource()
							{
								private TrackerPeerSource _delegate;

			 					private TRTrackerAnnouncer		ta;
			  					private long					ta_fixup;

			  					private long					last_scrape_fixup_time;
			  					private Object[]				last_scrape;

								private TrackerPeerSource
								fixup()
								{
									long	now = SystemTime.getMonotonousTime();

									if ( now - ta_fixup > 1000 ){

										TRTrackerAnnouncer current_ta = getTrackerClient();

										if ( current_ta == ta ){

											if ( current_ta != null && _delegate == null ){

												_delegate = current_ta.getTrackerPeerSource( set );
											}
										}else{

											if ( current_ta == null ){

												_delegate = null;

											}else{

												_delegate = current_ta.getTrackerPeerSource( set );
											}

											ta = current_ta;
										}

										ta_fixup	= now;
									}

									return( _delegate );
								}

								protected Object[]
								getScrape()
								{
									long now = SystemTime.getMonotonousTime();

									if ( now - last_scrape_fixup_time > 30*1000 || last_scrape == null ){

										TRTrackerScraper	scraper = globalManager.getTrackerScraper();

										int	max_peers 	= -1;
										int max_seeds 	= -1;
										int max_comp	= -1;
										int max_time 	= 0;
										int	min_scrape	= Integer.MAX_VALUE;

										String status_str = null;

										boolean	found_usable = false;

										for ( URL u: urls ){

											TRTrackerScraperResponse resp = scraper.peekScrape(torrent, u );

											if ( resp != null ){

												if ( !resp.isDHTBackup()){

													found_usable = true;

													int peers 	= resp.getPeers();
													int seeds 	= resp.getSeeds();
													int comp	= resp.getCompleted();

													if ( peers > max_peers ){

														max_peers = peers;
													}

													if ( seeds > max_seeds ){

														max_seeds = seeds;
													}

													if ( comp > max_comp ){

														max_comp = comp;
													}

													status_str = resp.getStatusString();

													if ( resp.getStatus() != TRTrackerScraperResponse.ST_INITIALIZING ){
														
														int	time	= resp.getScrapeTime();

														if ( time > max_time ){

															max_time = time;
														}

														long next_scrape = resp.getNextScrapeStartTime();

														if ( next_scrape > 0 ){

															int	 ns = (int)(next_scrape/1000);

															if ( ns < min_scrape ){

																min_scrape = ns;
															}
														}
													}
												}
											}
										}

											// don't overwrite an old status if this time around we haven't found anything usable

										if ( found_usable || last_scrape == null ){

											last_scrape = new Object[]{ max_seeds, max_peers, max_time, min_scrape, max_comp, status_str };
										}

										last_scrape_fixup_time = now;
									}

									return( last_scrape );
								}

								@Override
								public int
								getType()
								{
									return( TrackerPeerSource.TP_TRACKER );
								}

								@Override
								public String
								getName()
								{
									TrackerPeerSource delegate = fixup();

									if ( delegate == null ){

										return( urls[0].toExternalForm());
									}

									return( delegate.getName());
								}

								@Override
								public URL
								getURL()
								{
									TrackerPeerSource delegate = fixup();

									if ( delegate == null ){

										return( urls[0]);
									}

									return( delegate.getURL());
								}
								
								@Override
								public int
								getStatus()
								{
									TrackerPeerSource delegate = fixup();

									if ( delegate == null ){

										return( ST_STOPPED );
									}

									return( delegate.getStatus());
								}

								@Override
								public String
								getStatusString()
								{
									TrackerPeerSource delegate = fixup();

									if ( delegate == null ){

										return( (String)getScrape()[5] );
									}

									return( delegate.getStatusString());
								}

								@Override
								public int
								getSeedCount()
								{
									TrackerPeerSource delegate = fixup();

									if ( delegate == null ){

										return((Integer)getScrape()[0] );
									}

									int seeds = delegate.getSeedCount();

									if ( seeds < 0 ){

										seeds = (Integer)getScrape()[0];
									}

									return( seeds );
								}

								@Override
								public int
								getLeecherCount()
								{
									TrackerPeerSource delegate = fixup();

									if ( delegate == null ){

										return( (Integer)getScrape()[1] );
									}

									int leechers = delegate.getLeecherCount();

									if ( leechers < 0 ){

										leechers = (Integer)getScrape()[1];
									}

									return( leechers );
								}

								@Override
								public int
								getCompletedCount()
								{
									TrackerPeerSource delegate = fixup();

									if ( delegate == null ){

										return( (Integer)getScrape()[4] );
									}

									int comp = delegate.getCompletedCount();

									if ( comp < 0 ){

										comp = (Integer)getScrape()[4];
									}

									return( comp );
								}

								@Override
								public int
								getPeers()
								{
									TrackerPeerSource delegate = fixup();

									if ( delegate == null ){

										return( -1 );
									}

									return( delegate.getPeers());
								}

								@Override
								public int
								getInterval()
								{
									TrackerPeerSource delegate = fixup();

									if ( delegate == null ){

										Object[] si = getScrape();

										int	last 	= (Integer)si[2];
										int next	= (Integer)si[3];

										if ( last > 0 && next < Integer.MAX_VALUE && last < next ){

											return( next - last );
										}

										return( -1 );
									}

									return( delegate.getInterval());
								}

								@Override
								public int
								getMinInterval()
								{
									TrackerPeerSource delegate = fixup();

									if ( delegate == null ){

										return( -1 );
									}

									return( delegate.getMinInterval());
								}

								@Override
								public boolean
								isUpdating()
								{
									TrackerPeerSource delegate = fixup();

									if ( delegate == null ){

										return( false );
									}

									return( delegate.isUpdating());
								}

								@Override
								public int
								getLastUpdate()
								{
									TrackerPeerSource delegate = fixup();

									if ( delegate == null ){

										return( (Integer)getScrape()[2] );
									}

									return( delegate.getLastUpdate());
								}

								@Override
								public int
								getSecondsToUpdate()
								{
									TrackerPeerSource delegate = fixup();

									if ( delegate == null ){

										int next = (Integer)getScrape()[3];
										
										if ( next < Integer.MAX_VALUE ){
											
											return((int)( next - (SystemTime.getCurrentTime()/1000)));
										}
										
										return( Integer.MIN_VALUE );
									}

									return( delegate.getSecondsToUpdate());
								}

								@Override
								public boolean
								canManuallyUpdate()
								{
									TrackerPeerSource delegate = fixup();

									if ( delegate == null ){

										return( false );
									}

									return( delegate.canManuallyUpdate());
								}

								@Override
								public void
								manualUpdate()
								{
									TrackerPeerSource delegate = fixup();

									if ( delegate != null ){

										delegate.manualUpdate();
									}
								}
								
								@Override
								public long[]
								getReportedStats()
								{
									TrackerPeerSource delegate = fixup();

									URL url = getURL();
									
									long[] url_stats = stats.getTrackerReportedStats( url );

									long session_up 	= 0;
									long session_down	= 0;
									
									if ( delegate != null ){

										long[] session = delegate.getReportedStats();
										
										if ( session == null ){
											
											return( null );
										}
										
										session_up 		= session[0];
										session_down	= session[1];
									}
									
									return( new long[]{ url_stats[0], url_stats[1], url_stats[2], url_stats[3], session_up, session_down });
								}
								
								@Override
								public String 
								getDetails()
								{
									URL url = getURL();
									
									String key = all_trackers.ingestURL(url);
									
									if ( torrent.getAnnounceURLGroup().getUID() != url_group_map_uid ){
										
										buildURLGroupMap( torrent );
									}
									
									Map<String,Object[]> map = url_group_map;
									
									Object[] entry = map.get( key );
									
									if ( entry != null ){
										
										String str = MessageText.getString( "wizard.multitracker.group" );
										
										long total_sent 	= 0;
										long total_received	= 0;
										
										String sent_str = MessageText.getString( "DHTView.transport.sent" );
										String recv_str = MessageText.getString( "DHTView.transport.received" );
										
										for (Map.Entry<String,Object[]> e: map.entrySet()){
											
											Object[] temp = e.getValue();
											
											if ( temp[1] == entry[1] ){
												
												URL u = (URL)temp[0];
												
												long[] url_stats = stats.getTrackerReportedStats( u );
												
												long sent 		= url_stats[2];
												long received	= url_stats[3];
												
												total_sent += sent;
												
												total_received += received;
												
												str += 	"\n\t" + 
														u + ": " +
														sent_str + "=" + DisplayFormatters.formatByteCountToKiBEtc( sent ) + ", " +
														recv_str + "=" + DisplayFormatters.formatByteCountToKiBEtc( received );
											}
										}
										
										str += 	"\n\t" + MessageText.getString( "SpeedView.stats.total" )+ ": "+
												sent_str + "=" + DisplayFormatters.formatByteCountToKiBEtc( total_sent ) + ", " +
												recv_str + "=" + DisplayFormatters.formatByteCountToKiBEtc( total_received );
										
										return( str );
									}
									
									return( null );
								}
								
								@Override
								public boolean
								canDelete()
								{
									return( true );
								}

								@Override
								public void
								delete()
								{
									List<List<String>> lists = TorrentUtils.announceGroupsToList( t );

									List<String>	rem = new ArrayList<>();

									for ( URL u: urls ){
										rem.add( u.toExternalForm());
									}

									lists = TorrentUtils.removeAnnounceURLs2( lists, rem, false );

									TorrentUtils.listToAnnounceGroups( lists, t );
								}
							});
					}

						// cache peer source

					tps.add(
							new TrackerPeerSourceAdapter()
							{
								private TrackerPeerSource _delegate;

			 					private TRTrackerAnnouncer		ta;
			 					private boolean					enabled;
			  					private long					ta_fixup;

								private TrackerPeerSource
								fixup()
								{
									long	now = SystemTime.getMonotonousTime();

									if ( now - ta_fixup > 1000 ){

										TRTrackerAnnouncer current_ta = getTrackerClient();

										if ( current_ta == ta ){

											if ( current_ta != null && _delegate == null ){

												_delegate = current_ta.getCacheTrackerPeerSource();
											}
										}else{

											if ( current_ta == null ){

												_delegate = null;

											}else{

												_delegate = current_ta.getCacheTrackerPeerSource();
											}

											ta = current_ta;
										}

										enabled = controller.isPeerSourceEnabled( PEPeerSource.PS_BT_TRACKER );

										ta_fixup	= now;
									}

									return( _delegate );
								}

								@Override
								public int
								getType()
								{
									return( TrackerPeerSource.TP_TRACKER );
								}

								@Override
								public String
								getName()
								{
									TrackerPeerSource delegate = fixup();

									if ( delegate == null ){

										return( MessageText.getString( "tps.tracker.cache" ));
									}

									return( delegate.getName());
								}
								
								@Override
								public URL
								getURL()
								{
									TrackerPeerSource delegate = fixup();

									if ( delegate == null ){

										return( null );
									}

									return( delegate.getURL());
								}

								@Override
								public int
								getStatus()
								{
									TrackerPeerSource delegate = fixup();

									if ( !enabled ){

										return( ST_DISABLED );
									}

									if ( delegate == null ){

										return( ST_STOPPED );
									}

									return( ST_ONLINE );
								}

								@Override
								public int
								getPeers()
								{
									TrackerPeerSource delegate = fixup();

									if ( delegate == null || !enabled ){

										return( -1 );
									}

									return( delegate.getPeers());
								}
							});
  				}

	  				// http seeds

	  			try{
	  				ExternalSeedPlugin esp = DownloadManagerController.getExternalSeedPlugin();

	  				if ( esp != null ){

						tps.add( esp.getTrackerPeerSource( plugin_download ));
	 				}
	  			}catch( Throwable e ){
	  			}

	  				// dht

	  			try{

					PluginInterface dht_pi = CoreFactory.getSingleton().getPluginManager().getPluginInterfaceByClass(DHTTrackerPlugin.class);

	  			    if ( dht_pi != null ){

	  			    	tps.add(((DHTTrackerPlugin)dht_pi.getPlugin()).getTrackerPeerSource( plugin_download ));
	  			    }
				}catch( Throwable e ){
	  			}

					// LAN

				try{

					PluginInterface lt_pi = CoreFactory.getSingleton().getPluginManager().getPluginInterfaceByClass(LocalTrackerPlugin.class);

					if ( lt_pi != null ){

	  			    	tps.add(((LocalTrackerPlugin)lt_pi.getPlugin()).getTrackerPeerSource( plugin_download ));

					}

				}catch( Throwable e ){

				}

					// Plugin

				try{

					tps.addAll(Arrays.asList(((DownloadImpl)plugin_download).getTrackerPeerSources()));

				}catch( Throwable e ){

				}

					// PEX...

				tps.add(
					new TrackerPeerSourceAdapter()
					{
						private PEPeerManager		_pm;
						private TrackerPeerSource	_delegate;

						private TrackerPeerSource
						fixup()
						{
							PEPeerManager pm = getPeerManager();

							if ( pm == null ){

								_delegate 	= null;
								_pm			= null;

							}else if ( pm != _pm ){

								_pm	= pm;

								_delegate = pm.getTrackerPeerSource();
							}

							return( _delegate );
						}

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
							TrackerPeerSource delegate = fixup();

							if ( delegate == null ){

								return( ST_STOPPED );

							}else{

								return( delegate.getStatus());
							}
						}

						@Override
						public String
						getName()
						{
							TrackerPeerSource delegate = fixup();

							if ( delegate == null ){

								return( "" );

							}else{

								return( delegate.getName());
							}
						}

						@Override
						public String 
						getStatusString()
						{
							if ( getStatus() == TrackerPeerSource.ST_DISABLED ){
								
								try{
									if ( torrent.getPrivate()){
										
										return( MessageText.getString( "label.private" ));
									}
								}catch( Throwable e ){
									
								}
							}
							
							return( null );
						}
						
						@Override
						public int
						getPeers()
						{
							TrackerPeerSource delegate = fixup();

							if ( delegate == null ){

								return( -1 );

							}else{

								return( delegate.getPeers());
							}
						}
					});

					// incoming

				tps.add(
						new TrackerPeerSourceAdapter()
						{
							private long				fixup_time;

							private PEPeerManager		_pm;
							private int					tcp;
							private int					udp;
							private int					utp;
							private int					total;
							private boolean				enabled;

							private PEPeerManager
							fixup()
							{
								long	now = SystemTime.getMonotonousTime();

								if ( now - fixup_time > 1000 ){

									PEPeerManager pm = _pm = getPeerManager();

									if ( pm != null ){

										tcp 	= pm.getNbRemoteTCPConnections();
										udp		= pm.getNbRemoteUDPConnections();
										utp		= pm.getNbRemoteUTPConnections();
										total	= pm.getStats().getTotalIncomingConnections();
									}

									enabled = controller.isPeerSourceEnabled( PEPeerSource.PS_INCOMING );

									fixup_time = now;
								}

								return( _pm );
							}

							@Override
							public int
							getType()
							{
								return( TP_INCOMING );
							}

							@Override
							public int
							getStatus()
							{
								if ( !enabled ){

									return( ST_DISABLED );
								}
								
								PEPeerManager delegate = fixup();

								if ( delegate == null ){

									if ( _tracker_client_for_queued_download == null ){
								
										return( ST_STOPPED );
										
									}else{
									
											// ready for activation requests
										
										return( ST_ONLINE );
									}
								}else{

									return( ST_ONLINE );
								}
							}

							@Override
							public String
							getName()
							{
								PEPeerManager delegate = fixup();

								if ( delegate == null || !enabled ){

									return( "" );

								}else{

									return(
										MessageText.getString(
											"tps.incoming.details",
											new String[]{ String.valueOf( tcp ), String.valueOf( udp + utp ), String.valueOf( total )} ));
								}
							}

							@Override
							public int
							getPeers()
							{
								PEPeerManager delegate = fixup();

								if ( delegate == null || !enabled ){

									return( -1 );

								}else{

									return( tcp + udp );
								}
							}
						});

 			}else{

  				tps = (List<TrackerPeerSource>)tps_data[0];
  			}

  			return( tps );

  		}finally{

  			this_mon.exit();
  		}
  	}

    @Override
    public void
    addTPSListener(
    	DownloadManagerTPSListener		listener )
    {
    	try{
    		this_mon.enter();

    		if ( tps_listeners == null ){

    			tps_listeners = new ArrayList<>(1);
    		}

    		tps_listeners.add( listener );

    	}finally{

    		this_mon.exit();
    	}
    }

    @Override
    public void
    removeTPSListener(
    	DownloadManagerTPSListener		listener )
    {
       	try{
    		this_mon.enter();

    		if ( tps_listeners != null ){

    			tps_listeners.remove( listener );

    			if ( tps_listeners.size() == 0 ){

    				tps_listeners = null;

    	  			Object[] tps_data = (Object[])getUserData( TPS_Key );

    	  			if ( tps_data != null ){

    	 				TOTorrent t = getTorrent();

    	 				if ( t != null ){

    	 					t.removeListener( (TOTorrentListener)tps_data[1] );
    	 				}

    	  				setUserData( TPS_Key, null );
    	  			}
    			}
    		}
    	}finally{

    		this_mon.exit();
    	}
    }
    
    @Override
    public void
    informTPSChanged()
    {
		List<DownloadManagerTPSListener>	to_inform = null;

 		try{
  			this_mon.enter();

  			setUserData( TPS_Key, null );

  			if ( tps_listeners != null ){

  				to_inform = new ArrayList<>(tps_listeners);
  			}
  		}finally{

  			this_mon.exit();
  		}

  		if ( to_inform != null ){

  			for ( DownloadManagerTPSListener l: to_inform ){

  				try{

  					l.trackerPeerSourcesChanged();

  				}catch( Throwable e ){

  					Debug.out(e);
  				}
  			}
  		}
    }

  private byte[]
  getIdentity()
  {
 	  return( dl_identity );
  }

   /** @return true, if the other DownloadManager has the same hash
    * @see java.lang.Object#equals(java.lang.Object)
    */
   public boolean equals(Object obj)
   {
 		// check for object equivalence first!

 	if ( this == obj ){

 		return( true );
 	}

 	if( obj instanceof DownloadManagerImpl ) {

 	  DownloadManagerImpl other = (DownloadManagerImpl) obj;

 	  byte[] id1 = getIdentity();
 	  byte[] id2 = other.getIdentity();

 	  if ( id1 == null || id2 == null ){

 		return( false );	// broken torrents - treat as different so shown
 							// as broken
 	  }

 	  return( Arrays.equals( id1, id2 ));
 	}

 	return false;
   }


   public int
   hashCode()
   {
	   return dl_identity_hashcode;
   }


	/* (non-Javadoc)
	 * @see com.biglybt.core.logging.LogRelation#getLogRelationText()
	 */
	@Override
	public String getRelationText() {
		return "Download: '" + getDisplayName() + "'";
	}


	/* (non-Javadoc)
	 * @see com.biglybt.core.logging.LogRelation#queryForClass(java.lang.Class)
	 */
	@Override
	public Object[] getQueryableInterfaces() {
		return new Object[] { getTrackerClient() };
	}

	public String toString() {
		String hash = "<unknown>";

		if ( torrent != null ){
			try {
				hash = ByteFormatter.encodeString(torrent.getHash());

			} catch (Throwable e) {
			}
		}

		String status = DisplayFormatters.formatDownloadStatus(this);
		if (status.length() > 10) {
			status = status.substring(0, 10);
		}
		return "DownloadManagerImpl#" + getPosition()
				+ (getAssumedComplete() ? "s" : "d") + "@"
				+ Integer.toHexString(hashCode()) + "/"
				+ status + "/"
				+ hash;
	}

	protected static class
	NoStackException
		extends Exception
	{
		private final int error;
		
		protected
		NoStackException(
			int		_error,
			String	str )
		{
			super( str );
			
			error = _error;
		}
		
		protected int
		getError()
		{
			return( error );
		}
	}

	@Override
	public void
	generateEvidence(
		IndentWriter		writer,
		boolean				full )
	{
		writer.println(toString());

		PEPeerManager pm = getPeerManager();

		try {
			writer.indent();

			if ( full ){
				writer.println("Save Dir: "	+ getSaveLocation().toString());
			}else{
				writer.println("Save Dir: "	+ Debug.secretFileName(getSaveLocation().toString()));
			}

			if (current_peers.size() > 0) {
				writer.println("# Peers: " + current_peers.size());
			}

			if (current_pieces.size() > 0) {
				writer.println("# Pieces: " + current_pieces.size());
			}

			writer.println("Listeners: DownloadManager=" + listeners.size() + "; Disk="
				+ controller.getDiskListenerCount() + "; Peer=" + peer_listeners.size()
				+ "; Tracker=" + tracker_listeners.size());

			writer.println("SR: " + seedingRank.getRank());


			String sFlags = "";
			if (open_for_seeding) {
				sFlags += "Opened for Seeding; ";
			}

			if (data_already_allocated) {
				sFlags += "Data Already Allocated; ";
			}

			if (assumedComplete) {
				sFlags += "onlySeeding; ";
			}

			if (persistent) {
				sFlags += "persistent; ";
			}

			if (sFlags.length() > 0) {
				writer.println("Flags: " + sFlags);
			}

			stats.generateEvidence( writer );

			download_manager_state.generateEvidence( writer, full );

			if ( pm != null ){
				
				pm.generateEvidence( writer );
			}

				// note, PeerManager generates DiskManager evidence

			controller.generateEvidence( writer, full );

			TRTrackerAnnouncer announcer = getTrackerClient();

			if ( announcer != null ){

				announcer.generateEvidence( writer );
			}

			TRTrackerScraperResponse scrape = getTrackerScrapeResponse();

			if ( scrape == null ){

				writer.println( "Scrape: null" );
			}else{

				writer.println( "Scrape: " + scrape.getString());
			}
		} finally {

			writer.exdent();
		}
	}

	@Override
	public void
	destroy(
		boolean	is_duplicate )
	{
		destroyed	= true;

		if ( is_duplicate ){

				// minimal tear-down

			controller.destroy();

		}else{

			try{
					// Data files don't exist, so we just don't do anything.
		    	if (!getSaveLocation().exists()) {return;}

		    	DiskManager dm = this.getDiskManager();
		    	if (dm != null) {
		    		dm.downloadRemoved();
		    		return;
		    	}

		    	SaveLocationChange move_details;
		    	move_details = DownloadManagerMoveHandler.onRemoval(this);
		    	if (move_details == null) {
		    		return;
		    	}

		    	boolean can_move_torrent = move_details.hasTorrentChange();

		    	try {
		    		if (move_details.hasDownloadChange()) {
		    			this.moveDataFiles(move_details.download_location, move_details.download_name);
		    		}
		    	}
		    	catch (Exception e) {
		    		can_move_torrent = false;
		    		Logger.log(new LogAlert(this, true,
							"Problem moving files to removed download directory", e));
		    	}

		    	// This code will silently fail if the torrent file doesn't exist.
		    	if (can_move_torrent) {
		  		    try {
			    		this.moveTorrentFile(move_details.torrent_location, move_details.torrent_name);
			    	}
			    	catch (Exception e) {
			    		Logger.log(new LogAlert(this, true,
			    				"Problem moving torrent to removed download directory", e));
			    	}
		    	}
			}finally{

				clearFileLinks();

				controller.destroy();
				
				if ( torrent != null ){
				
					all_trackers.unregisterTorrent( torrent );
				}
			}
		}
	}

	@Override
	public boolean
	isDestroyed()
	{
		return( destroyed || removing );
	}

	@Override
	public int[] getStorageType(DiskManagerFileInfo[] info) {
		String[] types = DiskManagerImpl.getStorageTypes(this);
		int[] result = new int[info.length];
		for (int i=0; i<info.length; i++) {
			result[i] = DiskManagerUtil.convertDMStorageTypeFromString( types[info[i].getIndex()]);
		}
		return result;
	}

	@Override
	public boolean canMoveDataFiles() {
		if (!isPersistent()) {return false;}
		return true;
	}

	@Override
	public void rename(String name) throws DownloadManagerException {
		boolean paused = this.pause( true );
		try {
			this.renameDownload(name);
			this.getDownloadState().setAttribute(DownloadManagerState.AT_DISPLAY_NAME, name);
			this.renameTorrentSafe(name);
		}
		finally {
			if (paused) {this.resume();}
		}
	}

	@Override
	public void renameTorrent(String name) throws DownloadManagerException {
		this.moveTorrentFile(null, name);
	}

	@Override
	public void renameTorrentSafe(String name) throws DownloadManagerException {
		String torrent_parent = FileUtil.newFile(this.getTorrentFileName()).getParent();
		String torrent_name = name;

		File new_path = FileUtil.newFile(torrent_parent, torrent_name + ".torrent");
		if ( FileUtil.reallyExists( new_path )) {
			new_path = null;
		}

		for (int i=1; i<10; i++) {
			if (new_path != null) {break;}
			new_path = FileUtil.newFile(torrent_parent, torrent_name + "(" + i + ").torrent");
			if ( FileUtil.reallyExists( new_path )) {new_path = null;}
		}

		if (new_path == null) {
			throw new DownloadManagerException("cannot rename torrent file - file already exists");
		}

		this.renameTorrent(new_path.getName());
	}

	@Override
	public void
	requestAttention()
	{
		fireGlobalManagerEvent(GlobalManagerEvent.ET_REQUEST_ATTENTION);
	}

	@Override
	public void
	fireGlobalManagerEvent(
		int 	eventType,
		Object	eventData )
	{
		globalManager.fireGlobalManagerEvent( eventType, this, eventData );
	}

	/* (non-Javadoc)
	 * @see com.biglybt.core.download.DownloadManager#setFilePriorities(com.biglybt.core.disk.DiskManagerFileInfo[], int)
	 */
	@Override
	public void setFilePriorities(DiskManagerFileInfo[] fileInfos, int priority) {
		// TODO: Insted of looping, which fires off a lot of events,
		//       do it more directly, and fire needed events once, at end
		for (DiskManagerFileInfo fileInfo : fileInfos) {
			fileInfo.setPriority(priority);
		}
	}
}
