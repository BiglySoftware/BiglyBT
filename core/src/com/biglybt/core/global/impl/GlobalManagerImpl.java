/*
 * File    : GlobalManagerImpl.java
 * Created : 21-Oct-2003
 * By      : stuff
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

package com.biglybt.core.global.impl;

/*
 * Created on 30 juin 2003
 *
 */

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreOperation;
import com.biglybt.core.CoreOperationListener;
import com.biglybt.core.CoreOperationTask;
import com.biglybt.core.category.Category;
import com.biglybt.core.category.CategoryManager;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.config.impl.TransferSpeedValidator;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.download.*;
import com.biglybt.core.download.impl.DownloadManagerAdapter;
import com.biglybt.core.global.*;
import com.biglybt.core.helpers.TorrentFolderWatcher;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.networkmanager.NetworkManager;
import com.biglybt.core.networkmanager.impl.tcp.TCPNetworkManager;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peermanager.control.PeerControlSchedulerFactory;
import com.biglybt.core.speedmanager.SpeedManager;
import com.biglybt.core.speedmanager.impl.SpeedManagerImpl;
import com.biglybt.core.tag.*;
import com.biglybt.core.tag.impl.TagDownloadWithState;
import com.biglybt.core.tag.impl.TagTypeWithState;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentException;
import com.biglybt.core.tracker.AllTrackersManager;
import com.biglybt.core.tracker.AllTrackersManager.AllTrackers;
import com.biglybt.core.tracker.client.*;
import com.biglybt.core.tracker.util.TRTrackerUtils;
import com.biglybt.core.tracker.util.TRTrackerUtilsListener;
import com.biglybt.core.util.*;
import com.biglybt.pif.dht.mainline.MainlineDHTProvider;
import com.biglybt.pif.network.ConnectionManager;
import com.biglybt.util.MapUtils;

/**
 * @author Olivier
 *
 */
public class GlobalManagerImpl
	extends DownloadManagerAdapter
	implements 	GlobalManager, AEDiagnosticsEvidenceGenerator
{
	static final LogIDs LOGID = LogIDs.CORE;

		// GlobalManagerListener support
		// Must be an async listener to support the non-synchronised invocation of
		// listeners when a new listener is added and existing downloads need to be
		// reported

	private static final int LDT_MANAGER_ADDED			= 1;
	private static final int LDT_MANAGER_REMOVED		= 2;
	private static final int LDT_DESTROY_INITIATED		= 3;
	private static final int LDT_DESTROYED				= 4;
    private static final int LDT_SEEDING_ONLY           = 5;
    private static final int LDT_EVENT		            = 6;

	private final ListenerManager<Object>	listeners_and_event_listeners 	= ListenerManager.createAsyncManager(
		"GM:ListenDispatcher",
		new ListenerManagerDispatcher<Object>()
		{
			@Override
			public void
			dispatch(
				Object		_listener,
				int			type,
				Object		value )
			{
				if ( type == LDT_EVENT ){

					if ( _listener instanceof GlobalManagerEventListener ){

						((GlobalManagerEventListener)_listener ).eventOccurred( (GlobalManagerEvent)value );
					}
				}else{

					if ( _listener instanceof GlobalManagerListener ){

						GlobalManagerListener	target = (GlobalManagerListener)_listener;

						if ( type == LDT_MANAGER_ADDED ){

							target.downloadManagerAdded((DownloadManager)value);

						}else if ( type == LDT_MANAGER_REMOVED ){

							target.downloadManagerRemoved((DownloadManager)value);

						}else if ( type == LDT_DESTROY_INITIATED ){

							GlobalMangerProgressListener progress = (GlobalMangerProgressListener)value;
							
							target.destroyInitiated( progress);

						}else if ( type == LDT_DESTROYED ){

							target.destroyed();

						}else if ( type == LDT_SEEDING_ONLY ){

							boolean	[] temp = (boolean[])value;

		                    target.seedingStatusChanged( temp[0], temp[1] );
		                }
					}
				}
			}
		});

		// GlobalManagerDownloadWillBeRemovedListener support
		// Not async (doesn't need to be and can't be anyway coz it has an exception)

	private static final int LDT_MANAGER_WBR			= 1;

	private final ListenerManager<GlobalManagerDownloadWillBeRemovedListener>	removal_listeners 	= 
		ListenerManager.createManager(
			"GM:DLWBRMListenDispatcher",
			new ListenerManagerDispatcherWithException<GlobalManagerDownloadWillBeRemovedListener>()
			{
				@Override
				public void
				dispatchWithException(
					GlobalManagerDownloadWillBeRemovedListener		target,
					int												type,
					Object											value )

					throws GlobalManagerDownloadRemovalVetoException
				{
					DownloadManager dm = (DownloadManager) ((Object[])value)[0];
					boolean remove_torrent = ((Boolean) ((Object[])value)[1]).booleanValue();
					boolean remove_data = ((Boolean) ((Object[])value)[2]).booleanValue();

					target.downloadWillBeRemoved(dm, remove_torrent, remove_data);
				}
			});

	static boolean enable_stopped_scrapes;
	static boolean disable_never_started_scrapes;

	static int		no_space_dl_restart_check_period_millis;

	static int		missing_file_dl_restart_check_period_millis;
	static Object	missing_file_dl_restart_key = new Object();
	
	static{
		 COConfigurationManager.addAndFireParameterListeners(
			new String[]{
				"Tracker Client Scrape Stopped Enable",
				"Tracker Client Scrape Never Started Disable",
				ConfigKeys.File.BCFG_INSUFFICIENT_SPACE_DOWNLOAD_RESTART,
				ConfigKeys.File.ICFG_INSUFFICIENT_SPACE_DOWNLOAD_RESTART_MINS,
				ConfigKeys.File.BCFG_MISSING_FILE_DOWNLOAD_RESTART,
				ConfigKeys.File.ICFG_MISSING_FILE_DOWNLOAD_RESTART_MINS
			},
			new ParameterListener(){
				@Override
				public void parameterChanged(String parameterName) {
					enable_stopped_scrapes = COConfigurationManager.getBooleanParameter( "Tracker Client Scrape Stopped Enable" );
					
					disable_never_started_scrapes = COConfigurationManager.getBooleanParameter( "Tracker Client Scrape Never Started Disable" );
					
					boolean enable_no_space_dl_restarts = COConfigurationManager.getBooleanParameter( ConfigKeys.File.BCFG_INSUFFICIENT_SPACE_DOWNLOAD_RESTART );

					if ( enable_no_space_dl_restarts ){

						int mins = COConfigurationManager.getIntParameter( ConfigKeys.File.ICFG_INSUFFICIENT_SPACE_DOWNLOAD_RESTART_MINS );

						no_space_dl_restart_check_period_millis = Math.max( 1, mins )*60*1000;

					}else{

						no_space_dl_restart_check_period_millis = 0;
					}
					
					boolean enable_missing_file_dl_restarts = COConfigurationManager.getBooleanParameter( ConfigKeys.File.BCFG_MISSING_FILE_DOWNLOAD_RESTART );

					if ( enable_missing_file_dl_restarts ){

						int mins = COConfigurationManager.getIntParameter( ConfigKeys.File.ICFG_MISSING_FILE_DOWNLOAD_RESTART_MINS );

						missing_file_dl_restart_check_period_millis = Math.max( 1, mins )*60*1000;

					}else{

						missing_file_dl_restart_check_period_millis = 0;
					}
				}
			});
	}

	private Object								create_dm_lock		= new Object();

	private Object								managers_lock		= new Object();
	private volatile DownloadManager[] 			managers_list_cow	= new DownloadManager[0];
	final Map<HashWrapper,DownloadManager>		manager_hash_map	= new ConcurrentHashMap<>();
	final Map<DownloadManager, DownloadManager> manager_id_set		= new HashMap<>();
	
	private final GlobalMangerProgressListener	progress_listener;
	private long								lastListenerUpdate;

	private final Checker checker;
	private final GlobalManagerStatsImpl		stats;
    private long last_swarm_stats_calc_time		= 0;
    private long last_swarm_stats				= 0;

    // Set this flag to disable interaction with downloads.config.
    // Do *NOT* change this - only the constructor should set it once.
	private final boolean cripple_downloads_config;

	private final TRTrackerScraper 			trackerScraper;
	private GlobalManagerStatsWriter 	stats_writer;
	private GlobalManagerHostSupport	host_support;

	private Object						download_history_manager;

		// for non-persistent downloads
	private final Map<HashWrapper,Map>			saved_download_manager_state	= new HashMap<>();
	private final Map<HashWrapper,Boolean> 		paused_list_initial 			= new HashMap<>();

	private Map<LifecycleControlListener,GlobalManagerDownloadWillBeRemovedListener>	lcl_map = new HashMap<>();
	
	private int							next_seed_piece_recheck_index;

	private final TorrentFolderWatcher torrent_folder_watcher;

	private final Map<HashWrapper,Boolean>	paused_list = new HashMap<>();

	private final AEMonitor paused_list_mon = new AEMonitor( "GlobalManager:PL" );

	private final GlobalManagerFileMerger	file_merger;

	/* Whether the GlobalManager is active (false) or stopped (true) */

	private volatile boolean 	isStopping;
	private volatile boolean	destroyed;
	volatile long		needsSavingCozStateChanged;

	private boolean seeding_only_mode 				= false;
	private boolean potentially_seeding_only_mode	= false;

	private final 	AllTrackers	all_trackers = AllTrackersManager.getAllTrackers();
	private long	all_trackers_options_mut = all_trackers.getOptionsMutationCount();
	
	private final FrequencyLimitedDispatcher	check_seeding_only_state_dispatcher =
		new FrequencyLimitedDispatcher(
			new AERunnable(){ @Override
			public void runSupport(){ checkSeedingOnlyStateSupport(); }}, 5000 );

	private boolean	force_start_non_seed_exists;
	private int 	nat_status				= ConnectionManager.NAT_UNKNOWN;
	private String	nat_info_prefix, nat_info;
	private long	nat_status_last_good	= -1;
	private boolean	nat_status_probably_ok;

   private final CopyOnWriteList<DownloadManagerInitialisationAdapter>	dm_adapters = new CopyOnWriteList<>();

   /** delay loading of torrents */
   DelayedEvent loadTorrentsDelay = null;
   /** Whether loading of existing torrents is done */
   boolean loadingComplete = false;
   /** Monitor to block adding torrents while loading existing torrent list */
   final AESemaphore loadingSem = new AESemaphore("Loading Torrents");

   private MainlineDHTProvider provider = null;

   private boolean		auto_resume_on_start;
   private TimerEvent	auto_resume_timer;
   private boolean		auto_resume_disabled;

   private final TaggableLifecycleHandler	taggable_life_manager;

   {
	   auto_resume_on_start = COConfigurationManager.getBooleanParameter( "Resume Downloads On Start" );
	   
	   auto_resume_disabled =
	   		COConfigurationManager.getBooleanParameter( "Pause Downloads On Exit" ) && !auto_resume_on_start;
	
	   taggable_life_manager = TagManagerFactory.getTagManager().registerTaggableResolver( this );
   }

   private DownloadStateTagger	ds_tagger;

   public class Checker extends AEThread {
    int loopFactor;
    private static final int waitTime = 10*1000;
    // 5 minutes save resume data interval (default)
    private int saveResumeLoopCount 	= 5*60*1000 / waitTime;
    private static final int initSaveResumeLoopCount = 60*1000 / waitTime;
    private static final int natCheckLoopCount		= 30*1000 / waitTime;
    private static final int seedPieceCheckCount		= 30*1000 / waitTime;
    private static final int oneMinuteThingCount		= 60*1000 / waitTime;

    private final AESemaphore	run_sem = new AESemaphore( "GM:Checker:run");

     public Checker() {
      super("Global Status Checker");
      loopFactor = 0;
      setPriority(Thread.MIN_PRIORITY);
      //determineSaveResumeDataInterval();
    }

    private void determineSaveResumeDataInterval() {
      int saveResumeInterval = COConfigurationManager.getIntParameter("Save Resume Interval", 5);
      if (saveResumeInterval >= 1 && saveResumeInterval <= 90)
        saveResumeLoopCount = saveResumeInterval * 60000 / waitTime;
    }

    @Override
    public void
	runSupport()
    {
      while ( true ){

      	try{
	        loopFactor++;

	        determineSaveResumeDataInterval();

	        if (( loopFactor % saveResumeLoopCount == 0 )){

	        	saveDownloads( true, null );

	        }else if ( loadingComplete && loopFactor > initSaveResumeLoopCount ){

	        	if ( needsSavingCozStateChanged > 0 ){

	        		int num_downloads = managers_list_cow.length;

	        		boolean	do_save = false;

	        		if ( num_downloads < 10 ){

	        			do_save = true;

	        		}else{

	        				// this save isn't that important so back off based on number of downloads as saving a lot of download's state
	        				// takes some resources...

	        			long	now = SystemTime.getMonotonousTime();

	        			long	elapsed_secs = ( now - needsSavingCozStateChanged )/1000;

	        			do_save = elapsed_secs > num_downloads;
	        		}

	        		if ( do_save ){

	        			saveDownloads( true, null );
	        		}
	        	}
	        }

	        if ((loopFactor % natCheckLoopCount == 0)) {

	        	computeNATStatus();

	        		// we need this periodic check to pick up on DND file state changes (someone changes
	        		// a file from DND to normal and consequentially changes to a non-seeding mode).
	        		// Doing this via listeners is too much effort

		        checkSeedingOnlyState();

		        	// double check consistency

		        checkForceStart( false );
	        }

	        if ((loopFactor % seedPieceCheckCount == 0)) {

	        	seedPieceRecheck();
	        }

        	if ( loopFactor % saveResumeLoopCount == 0 ) {

    			DownloadManager[] managers = managers_list_cow;

		        for ( DownloadManager manager: managers ){

		        	manager.saveResumeData();
		       	}
	        }

        	if ( no_space_dl_restart_check_period_millis > 0 ){

        		int lc = no_space_dl_restart_check_period_millis / waitTime;

        		if ( loopFactor % lc == 0 ) {

        			List<DownloadManager>	eligible = new ArrayList<>();

        			DownloadManager[] managers = managers_list_cow;
        			
        			for ( DownloadManager manager: managers ){
  
    		        	if ( 	manager.getState() == DownloadManager.STATE_ERROR &&
    		        			(!manager.isDownloadComplete( false )) &&
    		        			(!manager.isPaused()) &&
    		        			manager.getErrorType() == DownloadManager.ET_INSUFFICIENT_SPACE ){

    		        		eligible.add( manager );
    		        	}
    		        }

    		        if ( !eligible.isEmpty()){

    		        	if ( eligible.size() > 1 ){

	    		        	Collections.sort(
	    		        		eligible,
	    		        		new Comparator<DownloadManager>()
	    		        		{
	    		        			@Override
						            public int
	    		        			compare(
	    		        				DownloadManager o1,
	    		        				DownloadManager o2)
	    		        			{
	    		        				return( o1.getPosition() - o2.getPosition());
	    		        			}
	    		        		});
    		        	}

    		        	DownloadManager manager = eligible.get(0);

    		        	Logger.log(new LogEvent(LOGID, "Restarting download '" + manager.getDisplayName() + "' to check if disk space now available" ));

    		        	boolean force = (manager.getErrorFlags()&DownloadManager.EF_WAS_FORCE_START) != 0;
    		        	
    		        	if ( force ){
    		        		
    		        		manager.setForceStart( true );
    		        		
    		        	}else{
    		        	
    		        		manager.setStateQueued();
    		        	}
    		       	}
        		}
        	}

           	if ( missing_file_dl_restart_check_period_millis > 0 ){

    			long now = SystemTime.getMonotonousTime();

        		List<DownloadManager>	eligible = new ArrayList<>();

        		DownloadManager[] managers = managers_list_cow;
        			
        		for ( DownloadManager manager: managers ){
  
    		       	if ( 	manager.getState() == DownloadManager.STATE_ERROR &&
     		       			(!manager.isPaused()) &&
    		       			manager.getErrorType() == DownloadManager.ET_FILE_MISSING ){

	        			Long t = (Long)manager.getUserData( missing_file_dl_restart_key );
	        				
	        			if ( t == null || now - t >= missing_file_dl_restart_check_period_millis ){
    		       		
	        				eligible.add( manager );
	        			}
    		       	}
        		}

		        if ( !eligible.isEmpty()){

		        	if ( eligible.size() > 1 ){

    		        	Collections.sort(
    		        		eligible,
    		        		new Comparator<DownloadManager>()
    		        		{
    		        			
    		        			@Override
					            public int
    		        			compare(
    		        				DownloadManager o1,
    		        				DownloadManager o2)
    		        			{
       		        				Long t1 = (Long)o1.getUserData( missing_file_dl_restart_key );
       		        				Long t2 = (Long)o2.getUserData( missing_file_dl_restart_key );
    		        				
       		        				long e1 = t1 == null?0:(now-t1);
       		        				long e2 = t2 == null?0:(now-t2);
       		        				
       		        				long diff = e2-e1;
       		        				
       		        				if ( diff < 0 ){
       		        					return( -1 );
       		        				}else if ( diff > 0 ){
       		        					return( 1 );
       		        				}else{
       		        					return( o1.getPosition() - o2.getPosition());
       		        				}
    		        			}
    		        		});
	        		}

		        	DownloadManager manager = eligible.get(0);

		        	manager.setUserData( missing_file_dl_restart_key, now );
		        	
		        	Logger.log(new LogEvent(LOGID, "Restarting download '" + manager.getDisplayName() + "' to check if missing file(s) now available" ));

		        	boolean force = (manager.getErrorFlags()&DownloadManager.EF_WAS_FORCE_START) != 0;
		        	
		        	if ( force ){
		        		
		        		manager.setForceStart( true );
		        		
		        	}else{
		        	
		        		manager.setStateQueued();
		        	}
        		}
        	}
        	if ( loopFactor % oneMinuteThingCount == 0 ) {

        		try{
	        		if ( !HttpURLConnection.getFollowRedirects()){

	        			Debug.outNoStack( "Something has set global 'follow redirects' to false!!!!" );

	        			HttpURLConnection.setFollowRedirects( true );
	        		}
        		}catch( Throwable e ){

        			Debug.out( e );
        		}
        	}
      	}catch( Throwable e ){

      		Debug.printStackTrace( e );
      	}

        try {
        	run_sem.reserve(waitTime);

        	if ( run_sem.isReleasedForever()){

        		break;
        	}
        }
        catch (Exception e) {
        	Debug.printStackTrace( e );
        }
      }
    }

    public void stopIt() {
      run_sem.releaseForever();
    }
  }

  public
  GlobalManagerImpl(
	Core 							core,
	GlobalMangerProgressListener 	listener )
  {
    //Debug.dumpThreadsLoop("Active threads");
	progress_listener = listener;

	this.cripple_downloads_config = "1".equals(System.getProperty(SystemProperties.SYSPROP_DISABLEDOWNLOADS));

  	AEDiagnostics.addWeakEvidenceGenerator( this );

  	DataSourceResolver.registerExporter( this );
  	
    stats = new GlobalManagerStatsImpl( this );

    try{
    	stats_writer = new GlobalManagerStatsWriter( core, stats );

    }catch( Throwable e ){

    	Logger.log(new LogEvent(LOGID, "Stats unavailable", e ));
    }

    try{
    	try{
    		Class<?> impl_class = GlobalManagerImpl.class.getClassLoader().loadClass("com.biglybt.core.history.impl.DownloadHistoryManagerImpl");

    		download_history_manager = impl_class.newInstance();

    	}catch( ClassNotFoundException e ){

    	}
    }catch( Throwable e ){

    	Logger.log(new LogEvent(LOGID, "Download History unavailable", e ));
    }
    
    try{

        if ( TagManagerFactory.getTagManager().isEnabled()){

        		// must register the tag type/tags before telling tag manager that the downloads are initialised
        		// as this process kicks off tag constraint validation and this may depend on the download state
        		// tags existing (e.g. hasTag( "Seeding" )
        	
        	ds_tagger = new DownloadStateTagger( core );
        }

    	loadDownloads();
    	
	}finally{

		if ( ds_tagger != null ){
			
			ds_tagger.initialise();
		}
		
		taggable_life_manager.initialized( getResolvedTaggables());
	}
    
    if (progress_listener != null){
    	progress_listener.reportCurrentTask(MessageText.getString("splash.initializeGM"));
    }

    // Initialize scraper after loadDownloads so that we can merge scrapes
    // into one request per tracker
    trackerScraper = TRTrackerScraperFactory.getSingleton();

    trackerScraper.setClientResolver(
    	new TRTrackerScraperClientResolver()
		{
    		@Override
		    public boolean
			isScrapable(
				HashWrapper	torrent_hash )
    		{
       			DownloadManager	dm = getDownloadManager(torrent_hash);

    			if ( dm == null ){

    				return( false );
    			}


    			int	dm_state = dm.getState();

    			if ( 	dm_state == DownloadManager.STATE_QUEUED ){

    				return( true );

    			}else if ( 	dm_state == DownloadManager.STATE_DOWNLOADING ||
    						dm_state == DownloadManager.STATE_SEEDING ){

    				return( true );
    			}

    				// download is stopped

    			if ( !enable_stopped_scrapes ){

    				return( false );
    			}

    				// we don't want to scrape downloads that have never been started because the user
    				// might be working out which networks to enable and it would be bad to go ahead
    				// and start, say, a public scrape, when they're about to set the download as
    				// anonymous only

    			DownloadManagerStats stats = dm.getStats();

    				// hack atm rather than recording 'has ever been started' state just look at data
    				// might have been added for seeding etc so don't just use bytes downloaded

    			if ( stats.getTotalDataBytesReceived() == 0 && stats.getPercentDoneExcludingDND() == 0 ){

    				if ( disable_never_started_scrapes ){
    				
    					return( false );
    				}
    			}

    			return( true );
    		}

    		@Override
		    public boolean
			isNetworkEnabled(
				HashWrapper	hash,
				URL			url )
    		{
       			DownloadManager	dm = getDownloadManager(hash);

    			if ( dm == null ){

    				return( false );
    			}

    			String	nw = AENetworkClassifier.categoriseAddress( url.getHost());

    			String[]	networks = dm.getDownloadState().getNetworks();

    			for (int i=0;i<networks.length;i++){

    				if ( networks[i] ==  nw ){

    					return( true );
    				}
    			}

    			return( false );
    		}

    		@Override
		    public String[]
			getEnabledNetworks(
				HashWrapper	hash )
    		{
       			DownloadManager	dm = getDownloadManager(hash);

    			if ( dm == null ){

    				return( null );
    			}

    			return( dm.getDownloadState().getNetworks());
    		}

    		@Override
		    public int[]
    		getCachedScrape(
    			HashWrapper hash )
    		{
    			DownloadManager	dm = getDownloadManager(hash);

    			if ( dm == null ){

    				return( null );
    			}

				long cache = dm.getDownloadState().getLongAttribute( DownloadManagerState.AT_SCRAPE_CACHE );

				if ( cache == -1 ){

					return( null );

				}else{

					int cache_src = dm.getDownloadState().getIntAttribute( DownloadManagerState.AT_SCRAPE_CACHE_SOURCE );

					if ( cache_src == 0  ){

						int seeds 		= (int)((cache>>32)&0x00ffffff);
						int leechers 	= (int)(cache&0x00ffffff);

						return( new int[]{ seeds, leechers });

					}else{

						return( null );
					}
				}
    		}

    		@Override
		    public Object[]
    		getExtensions(
    			HashWrapper	hash )
    		{
     			DownloadManager	dm = getDownloadManager(hash);

     			Character	state;
     			String		ext;

    			if ( dm == null ){

    				ext		= "";
    	   			state	= TRTrackerScraperClientResolver.FL_NONE;

    			}else{

    				ext = dm.getDownloadState().getTrackerClientExtensions();

    				if ( ext == null ){

    					ext = "";
    				}

    				boolean	comp = dm.isDownloadComplete( false );

    				int	dm_state = dm.getState();

    					// treat anything not stopped or running as queued as we need to be "optimistic"
    					// for torrents at the start-of-day

    				if ( 	dm_state == DownloadManager.STATE_ERROR ||
    						dm_state == DownloadManager.STATE_STOPPED ||
    						( dm_state == DownloadManager.STATE_STOPPING && dm.getSubState() != DownloadManager.STATE_QUEUED )){

       					state	= comp?TRTrackerScraperClientResolver.FL_COMPLETE_STOPPED:TRTrackerScraperClientResolver.FL_INCOMPLETE_STOPPED;

    				}else if (  dm_state == DownloadManager.STATE_DOWNLOADING ||
    							dm_state == DownloadManager.STATE_SEEDING ){

      					state	= comp?TRTrackerScraperClientResolver.FL_COMPLETE_RUNNING:TRTrackerScraperClientResolver.FL_INCOMPLETE_RUNNING;

    				}else{

    					state	= comp?TRTrackerScraperClientResolver.FL_COMPLETE_QUEUED:TRTrackerScraperClientResolver.FL_INCOMPLETE_QUEUED;
    				}
    			}

    			return( new Object[]{ ext, state });
    		}

    		@Override
		    public boolean
    		redirectTrackerUrl(
    			HashWrapper		hash,
    			URL				old_url,
    			URL				new_url )
    		{
       			DownloadManager	dm = getDownloadManager(hash);

       			if ( dm == null || dm.getTorrent() == null ){

       				return( false );
       			}

       			return( TorrentUtils.replaceAnnounceURL( dm.getTorrent(), old_url, new_url ));
    		}
		});

    trackerScraper.addListener(
    	new TRTrackerScraperListener() {
    		@Override
		    public void scrapeReceived(TRTrackerScraperResponse response) {
    			HashWrapper	hash = response.getHash();

    			DownloadManager manager = manager_hash_map.get( hash );
    			
	   			if ( manager != null ){
	   				
	   				manager.setTrackerScrapeResponse( response );
    			}
    		}
    	});

    try{
	    host_support = new GlobalManagerHostSupport( this );

    }catch( Throwable e ){

    	Logger.log(new LogEvent(LOGID, "Hosting unavailable", e));
    }

    checker = new Checker();

    checker.start();

    torrent_folder_watcher = new TorrentFolderWatcher( this );

    torrent_folder_watcher.start();

    TRTrackerUtils.addListener(
    	new TRTrackerUtilsListener()
    	{
    		@Override
		    public void
    		announceDetailsChanged()
    		{
				Logger.log( new LogEvent(LOGID, "Announce details have changed, updating trackers" ));

				DownloadManager[] managers = managers_list_cow;
				
				for ( DownloadManager manager: managers ){

					manager.requestTrackerAnnounce( true );
				}
    		}
    	});

    TorrentUtils.addTorrentURLChangeListener(
    	new TorrentUtils.TorrentAnnounceURLChangeListener()
    	{
    		@Override
		    public void
    		changed()
    		{
				Logger.log( new LogEvent(LOGID, "Announce URL details have changed, updating trackers" ));

				DownloadManager[] managers = managers_list_cow;
				
				for ( DownloadManager manager: managers ){

					TRTrackerAnnouncer client = manager.getTrackerClient();

					if ( client != null ){

						client.resetTrackerUrl( false );
					}
				}
    		}
    	});

    file_merger = new GlobalManagerFileMerger( this );
  }

  @Override
  public DownloadManager
  addDownloadManager(
  		String fileName,
		String savePath)
  {
  	// TODO: add optionalHash?
  	return addDownloadManager(fileName, null, savePath, DownloadManager.STATE_WAITING, true);
  }

	@Override
	public DownloadManager
	addDownloadManager(
	    String 		fileName,
	    byte[]	optionalHash,
	    String 		savePath,
	    int         initialState,
		boolean		persistent )
	{
	 	return addDownloadManager(fileName, optionalHash, savePath, initialState,
				persistent, false, null);
	}

	  @Override
	  public DownloadManager
	  addDownloadManager(
	  		String torrent_file_name,
	  		byte[] optionalHash,
			String savePath,
			int initialState,
			boolean persistent,
			boolean for_seeding,
			DownloadManagerInitialisationAdapter _adapter )
	  {
		  return addDownloadManager(torrent_file_name, optionalHash, savePath, null, initialState, persistent, for_seeding, _adapter);
	  }


  /**
	 * @return true, if the download was added
	 *
	 * @author Rene Leonhardt
	 */

  @Override
  public DownloadManager
  addDownloadManager(
  		String		torrent_file_name,
  		byte[]		optionalHash,
		String		savePath,
		String		saveFile,
		int			initialState,
		boolean		persistent,
		boolean		for_seeding,
		DownloadManagerInitialisationAdapter _adapter )
 
  {
		boolean needsFixup = false;
		DownloadManager manager;

		// wait for "load existing" to complete
		loadingSem.reserve(60 * 1000);

		DownloadManagerInitialisationAdapter adapter = getDMAdapter(_adapter);

		/* to recover the initial state for non-persistent downloads the simplest way is to do it here
		 */

		List file_priorities = null;

		if (!persistent) {

			HashWrapper hw = new HashWrapper( optionalHash );
			
			Map save_download_state = (Map) saved_download_manager_state.get( hw );

			if (save_download_state != null) {

				if (save_download_state.containsKey("state")) {

					int saved_state = ((Long) save_download_state.get("state")).intValue();

					if (saved_state == DownloadManager.STATE_STOPPED) {

						initialState = saved_state;
					}
				}

				file_priorities = (List) save_download_state.get("file_priorities");

				// non persistent downloads come in at random times
				// If it has a position, it's probably invalid because the
				// list has been fixed up to remove gaps.  Set a flag to
				// do another fixup after adding
				Long lPosition = (Long) save_download_state.get("position");
				if (lPosition != null) {
					if (lPosition.longValue() != -1) {
						needsFixup = true;
					}
				}
			}
			
			try {
          		paused_list_mon.enter();
          		
          		Boolean force = paused_list_initial.remove( hw );
          		
          		if ( force != null ){
          			
          			if ( auto_resume_on_start ){
          			
          				if ( initialState == DownloadManager.STATE_STOPPED ){
          					
          					initialState = DownloadManager.STATE_QUEUED;
          				}
          				
          				if ( save_download_state != null ){
          				
          					save_download_state.put( "forceStart", new Long( force?1:0 ));
          				}
          			}else{
          			
          				paused_list.put( hw, force );
          			}
          		}
          		
			}finally{
				paused_list_mon.exit();
			}
		}

			// torrent operations
		
		File torrentDir = null;
		File fDest = null;
		HashWrapper hash = null;
		boolean deleteDest = false;
		DownloadManager deleteDestExistingDM = null;

		boolean 	thisIsMagnet	= false;

		try {
			File f = FileUtil.newFile(torrent_file_name);

			if (!f.exists()) {
				throw (new IOException("Torrent file '" + torrent_file_name
						+ "' doesn't exist"));
			}

			if (!f.isFile()) {
				throw (new IOException("Torrent '" + torrent_file_name
						+ "' is not a file"));
			}

			fDest = TorrentUtils.copyTorrentFileToSaveDir(f, persistent);

			String fName = fDest.getCanonicalPath();

			HashWrapper	torrentHash		= null;
						
			try{
					// This does not trigger locale decoding

				TOTorrent torrent = TorrentUtils.readFromFile(fDest, false);
			
				thisIsMagnet = TorrentUtils.getFlag( torrent, TorrentUtils.TORRENT_FLAG_METADATA_TORRENT );
				
				torrentHash = torrent.getHashWrapper();
				
			}catch( Throwable e ){
				// will fail later
			}
		
			if ( optionalHash != null ){
				
				hash = new HashWrapper( optionalHash );
				
			}else{
				
				hash = torrentHash;
			}

			if ( hash != null ){
								
					// early check if download already exists, saves creating a download and then
					// having to delete it when dup found
				
				DownloadManager existingDM = getDownloadManager( hash );
				
				if ( existingDM != null ){
					
						// exception here is if the existing download is a magnet download and
						// this one is a real one - in this case things will be resolved later
					
					boolean existingIsMagnet = existingDM.getDownloadState().getFlag( DownloadManagerState.FLAG_METADATA_DOWNLOAD );
				
					FileUtil.log( "addDownload: " + ByteFormatter.encodeString( hash.getBytes()) + ": isMagnet=" + thisIsMagnet + ", existingIsMagnet=" + existingIsMagnet );

					if (	( thisIsMagnet == existingIsMagnet ) ||
							( thisIsMagnet && !existingIsMagnet )){
						
						deleteDest = true;
						
						deleteDestExistingDM = existingDM;
						
						return( existingDM );
					}
				}else{
					
					FileUtil.log( "addDownload: " + ByteFormatter.encodeString( hash.getBytes()) + ": isMagnet=" + thisIsMagnet );
				}
			}

				// save path operations
			
			File moc = null;
			
			if ( persistent && !for_seeding ){
			
				if ( COConfigurationManager.getBooleanParameter( ConfigKeys.File.BCFG_FILE_USE_TEMP_AND_MOVE_ENABLE )){
			
					String path = COConfigurationManager.getStringParameter( ConfigKeys.File.SCFG_FILE_USE_TEMP_AND_MOVE_PATH, "" );
					
					if ( !path.isEmpty()){
						
						File temp = FileUtil.newFile( path );
						
						if ( !temp.isDirectory()){
							
							temp.mkdirs();
						}
						
						if ( temp.isDirectory()){
							
							moc	= FileUtil.newFile( savePath );
							
							savePath = temp.getAbsolutePath();
						}
					}
				}
			}
			
			// now do the creation!

			boolean[] is_existing = {false};
			
			manager = createAndAddNewDownloadManager(
					optionalHash, fName, savePath, saveFile, initialState, persistent, for_seeding,
					file_priorities, adapter, thisIsMagnet, is_existing );

			
			// if a different manager is returned then an existing manager for
			// this torrent exists and the new one isn't needed (yuck)

			if ( manager == null || is_existing[0] ){
				
				deleteDest = true;
				
				deleteDestExistingDM = manager;
				
			}else{
					// new torrent, see if it is add-stopped and we want to auto-pause

				if ( moc != null ){
					
					DownloadManagerState dms = manager.getDownloadState();
					
					dms.setAttribute( DownloadManagerState.AT_MOVE_ON_COMPLETE_DIR, moc.getAbsolutePath());
				}
				
				if ( initialState == DownloadManager.STATE_STOPPED ){

					if ( COConfigurationManager.getBooleanParameter( "Default Start Torrents Stopped Auto Pause" )){

			         	try {
			          		paused_list_mon.enter();

			          		paused_list.put( manager.getTorrent().getHashWrapper(), false );

				    	}finally{

				    		paused_list_mon.exit();
				    	}
					}
				}

				if ( TorrentUtils.shouldDeleteTorrentFileAfterAdd( fDest, persistent )){
					
					deleteDest = true;
				}
			}
		}catch (IOException e){
			
			System.err.println("DownloadManager::addDownloadManager: fails - td = "
					+ torrentDir + ", fd = " + fDest + ": " + Debug.getNestedExceptionMessage( e ));
			
			System.err.println(Debug.getCompressedStackTrace());
			
			//Debug.printStackTrace(e);
			
			manager = createAndAddNewDownloadManager( optionalHash,
					torrent_file_name, savePath, saveFile, initialState, persistent, for_seeding,
					file_priorities, adapter, thisIsMagnet, new boolean[1] );
						
		}catch( Exception e ){
			
			// get here on duplicate files, no need to treat as error
			manager = createAndAddNewDownloadManager( optionalHash,
					torrent_file_name, savePath, saveFile, initialState, persistent, for_seeding,
					file_priorities, adapter, thisIsMagnet, new boolean[1] );
						
		}finally{
			
			if ( deleteDest ){
				
				try{
					boolean skip = deleteDestExistingDM != null && !FileUtil.newFile( deleteDestExistingDM.getTorrentFileName()).equals( fDest );
					
					if ( !skip ){
						fDest.delete();
						File backupFile;
	
						backupFile = FileUtil.newFile(fDest.getCanonicalPath() + ".bak");
						if (backupFile.exists()){
							backupFile.delete();
						}
					}
				} catch (Throwable e) {
				}
			}
		}

		if ( needsFixup && manager != null ){
			
			if (manager.getPosition() <= downloadManagerCount(manager.isDownloadComplete(false))) {
				
				fixUpDownloadManagerPositions();
			}
		}

		return manager;
	}
  
	private DownloadManager
  	createAndAddNewDownloadManager(
		byte[]									torrent_hash,
		String 									torrentFileName,
		String 									savePath,
		String									saveFile,
		int      								initialState,
		boolean									persistent,
		boolean									for_seeding,
		List									file_priorities,
		DownloadManagerInitialisationAdapter 	adapter,
		boolean									thisIsMagnet,
		boolean[]								is_existing )
  	{
			// single thread this as simply creating a download manager causes state files to be copied
			// and we don't want multiple things going on at once if someone is trying to add the same
			// download multiple times
		
		synchronized( create_dm_lock ){
		
			DownloadManager manager = 
				createNewDownloadManager( 
					torrent_hash, torrentFileName, savePath, saveFile, initialState, 
					persistent, for_seeding, file_priorities, thisIsMagnet, is_existing );
			
			if ( !is_existing[0] ){
			
				manager = addDownloadManager( manager, true, adapter, for_seeding, is_existing, false );
			}
			
			return( manager );
		}
  	}

  	private DownloadManager
  	createNewDownloadManager(
		byte[]									torrent_hash,
		String 									torrentFileName,
		String 									savePath,
		String									saveFile,
		int      								initialState,
		boolean									persistent,
		boolean									for_seeding,
		List									file_priorities,
		boolean									thisIsMagnet,
		boolean[]								is_existing )
  	{
		FileUtil.log( "createNewDownload: starts for " + ByteFormatter.encodeString( torrent_hash) + ", " + torrentFileName + "," + savePath + ", " + saveFile );

  		int loop = 0;
  		
  		boolean debug_on = false;
  		
  		try{
	  		while( true ){
	  			
	  			loop++;
	  			
					// due to the fact that magnet and real downloads share the same hash it is possible
					// for the creation to return an existing magnet download. check for this and
					// delete it if so
	
				DownloadManager new_manager = 
					DownloadManagerFactory.create(
						this, torrent_hash, torrentFileName, savePath, saveFile, initialState, persistent, 
						false, for_seeding, false, file_priorities );
			
				if ( thisIsMagnet || torrent_hash == null ){
					
					FileUtil.log( "createNewDownload: " + ByteFormatter.encodeString( torrent_hash) + ": isMagnet=true" );
	
					return( new_manager );
				}
				
				boolean newIsMagnet = new_manager.getDownloadState().getFlag( DownloadManagerState.FLAG_METADATA_DOWNLOAD );
	
				if ( !newIsMagnet ){
					
					FileUtil.log( "createNewDownload: " + ByteFormatter.encodeString( torrent_hash) + ": isMagnet=false, newIsMagnet=false" );
	
					return( new_manager );
				}

				
				if ( loop > 10 ){
					
					return( new_manager );
				}
				
					// we've been asked to create a non-magnet but the new manager is a magnet as it has picked
					// up existing state from a magnet download
				
				DownloadManager existing = getDownloadManager( new HashWrapper( torrent_hash ));
						
				if ( existing != null ){
							
					boolean existingIsMagnet = existing.getDownloadState().getFlag( DownloadManagerState.FLAG_METADATA_DOWNLOAD );
							
					FileUtil.log( "createNewDownload: " + ByteFormatter.encodeString( torrent_hash) + ": isMagnet=false, newIsMagnet=true, existingIsMagnet=" + existingIsMagnet );
		
					if ( existingIsMagnet ){
						
						try{
							removeDownloadManager( existing, true, true );
								
						}catch( Throwable e ){
						
							Debug.out( e );
						}
					}else{
					
						is_existing[0] = true;
						
						return( existing );
					}
				}else{
				
					try{
						DownloadManagerStateFactory.deleteDownloadState( torrent_hash, true );
						
					}catch( Throwable e ){
						
						FileUtil.log( "createNewDownload: failed to delete existing state", e );
					}
					
					if ( !debug_on ){
						
						debug_on = true;
						
						DownloadManagerStateFactory.setDebugOn( true );
						
						try{
							FileUtil.copyFile( 
								new File( torrentFileName ),
								FileUtil.getUserFile( ByteFormatter.encodeString( torrent_hash) + ".log" ));
								
						}catch( Throwable e ){
							
						}
					}
					
					FileUtil.log( "createNewDownload: " + ByteFormatter.encodeString( torrent_hash) + ": isMagnet=false, newIsMagnet=true, existing=null" );
				}
		
				try{
					Thread.sleep(500);
					
				}catch( Throwable e ){
					
				}
	  		}
  		}finally{
  			
  			if ( debug_on ){
  				
  				DownloadManagerStateFactory.setDebugOn( false );
  			}
  		}
  	}
  	
   protected DownloadManager
   addDownloadManager(
   		DownloadManager 						download_manager,
		boolean									notifyListeners,
		DownloadManagerInitialisationAdapter	adapter,
		boolean									for_seeding,
		boolean[]								is_existing,
		boolean									initial_load )
   {
	   if ( !isStopping ){
			   
		   synchronized( managers_lock ){

			   DownloadManager existing = manager_id_set.get(download_manager);

			   if ( existing != null ){
				   
				   download_manager.destroy( true );

				   try{
					   FileUtil.log( "addDownloadManager: " + ByteFormatter.encodeString( download_manager.getTorrent().getHash()) + ": returning  existing" );
			   
				   }catch( Throwable e ){
					   
				   }
				   
				   is_existing[0] = true;
				   
				   return( existing );
			   }

			   try{
				   if ( !initial_load ){
				   
					   FileUtil.log( "addDownloadManager: " + ByteFormatter.encodeString( download_manager.getTorrent().getHash()) + ": actually adding" );
				   }
			   }catch( Throwable e ){
				   
			   }
		   }
		   
		   if ( adapter != null ){
			   
				if ( download_manager.getTorrent() != null ){
					
					try{
						adapter.initialised( download_manager, for_seeding );

					}catch( Throwable e ){

						Debug.printStackTrace(e);
					}
				}
		   }
		   
		   download_manager.setConstructed();
		   
		   synchronized( managers_lock ){

			   DownloadManagerStats dm_stats = download_manager.getStats();

			   HashWrapper hashwrapper = null;

			   try{
				   TOTorrent torrent = download_manager.getTorrent();

				   if ( torrent != null ){

					   hashwrapper = torrent.getHashWrapper();
				   }
			   } catch (Exception e1) { }

			   Map	save_download_state	= (Map)saved_download_manager_state.remove(hashwrapper);

			   long saved_data_bytes_downloaded	= 0;
			   long saved_data_bytes_uploaded		= 0;
			   long saved_discarded				= 0;
			   long saved_hashfails				= 0;
			   long saved_SecondsDownloading		= 0;
			   long saved_SecondsOnlySeeding 		= 0;

			   if ( save_download_state != null ){

				   int maxDL = save_download_state.get("maxdl")==null?0:((Long) save_download_state.get("maxdl")).intValue();
				   int maxUL = save_download_state.get("maxul")==null?0:((Long) save_download_state.get("maxul")).intValue();

				   Long lDownloaded = (Long) save_download_state.get("downloaded");
				   Long lUploaded = (Long) save_download_state.get("uploaded");
				   Long lCompletedBytes = (Long) save_download_state.get("completedbytes");
				   Long lDiscarded = (Long) save_download_state.get("discarded");
				   Long lHashFailsCount = (Long) save_download_state.get("hashfails");	// old method, number of fails
				   Long lHashFailsBytes = (Long) save_download_state.get("hashfailbytes");	// new method, bytes failed

				   Long nbUploads = (Long)save_download_state.get("uploads");	// migrated to downloadstate in 2403

				   if ( nbUploads != null ){
					   // migrate anything other than the default value of 4
					   int	maxUploads = nbUploads.intValue();
					   if ( maxUploads != 4 ){
						   // hmm, can't currently remove maxuploads as it stops people regressing to earlier
						   // version. So currently we store maxuploads still and only overwrite the dm state
						   // value if the stored value is non-default and the state one is
						   if ( download_manager.getMaxUploads() == 4 ){
							   download_manager.setMaxUploads( maxUploads );
						   }
					   }
				   }

				   dm_stats.setDownloadRateLimitBytesPerSecond( maxDL );
				   dm_stats.setUploadRateLimitBytesPerSecond( maxUL );

				   if (lCompletedBytes != null) {
					   dm_stats.setDownloadCompletedBytes(lCompletedBytes.longValue());
				   }

				   if (lDiscarded != null) {
					   saved_discarded = lDiscarded.longValue();
				   }

				   if ( lHashFailsBytes != null ){

					   saved_hashfails = lHashFailsBytes.longValue();

				   }else if ( lHashFailsCount != null) {

					   TOTorrent torrent = download_manager.getTorrent();

					   if ( torrent != null ){

						   saved_hashfails = lHashFailsCount.longValue() * torrent.getPieceLength();
					   }
				   }

				   Long lPosition = (Long) save_download_state.get("position");

				   // 2.2.0.1 - category moved to downloadstate - this here for
				   // migration purposes

				   String sCategory = null;
				   if (save_download_state.containsKey("category")){
					   sCategory = new String((byte[]) save_download_state.get("category"), Constants.DEFAULT_ENCODING_CHARSET);
				   }

				   if (sCategory != null) {
					   Category cat = CategoryManager.getCategory(sCategory);
					   if (cat != null) download_manager.getDownloadState().setCategory(cat);
				   }

				   download_manager.requestAssumedCompleteMode();

				   if (lDownloaded != null && lUploaded != null) {
					   boolean bCompleted = download_manager.isDownloadComplete(false);

					   long lUploadedValue = lUploaded.longValue();

					   long lDownloadedValue = lDownloaded.longValue();

					   if ( bCompleted && (lDownloadedValue == 0)){

						   //Gudy : I say if the torrent is complete, let's simply set downloaded
						   //to size in order to see a meaningfull share-ratio
						   //Gudy : Bypass this horrible hack, and I don't care of first priority seeding...
						   /*
		            if (lDownloadedValue != 0 && ((lUploadedValue * 1000) / lDownloadedValue < minQueueingShareRatio) )
		              lUploadedValue = ( download_manager.getSize()+999) * minQueueingShareRatio / 1000;
						    */
						   // Parg: quite a few users have complained that they want "open-for-seeding" torrents to
						   // have an infinite share ratio for seeding rules (i.e. so they're not first priority)

						   int	dl_copies = COConfigurationManager.getIntParameter("StartStopManager_iAddForSeedingDLCopyCount");

						   lDownloadedValue = download_manager.getSize() * dl_copies;

						   download_manager.getDownloadState().setFlag( DownloadManagerState.FLAG_ONLY_EVER_SEEDED, true );
					   }

					   saved_data_bytes_downloaded	= lDownloadedValue;
					   saved_data_bytes_uploaded		= lUploadedValue;
				   }

				   if (lPosition != null)
					   download_manager.setPosition(lPosition.intValue());
				   // no longer needed code
				   //  else if (dm_stats.getDownloadCompleted(false) < 1000)
				   //  dm.setPosition(bCompleted ? numCompleted : numDownloading);

				   Long lSecondsDLing = (Long)save_download_state.get("secondsDownloading");
				   if (lSecondsDLing != null) {
					   saved_SecondsDownloading = lSecondsDLing.longValue();
				   }

				   Long lSecondsOnlySeeding = (Long)save_download_state.get("secondsOnlySeeding");
				   if (lSecondsOnlySeeding != null) {
					   saved_SecondsOnlySeeding = lSecondsOnlySeeding.longValue();
				   }

				   Long already_allocated = (Long)save_download_state.get( "allocated" );
				   if( already_allocated != null && already_allocated.intValue() == 1 ) {
					   download_manager.setDataAlreadyAllocated( true );
				   }

				   Long creation_time = (Long)save_download_state.get( "creationTime" );

				   if ( creation_time != null ){

					   long	ct = creation_time.longValue();

					   if ( ct < SystemTime.getCurrentTime()){

						   download_manager.setCreationTime( ct );
					   }
				   }

			   }else{

				   // no stats, bodge the uploaded for seeds

				   if ( dm_stats.getDownloadCompleted(false) == 1000 ){

					   int	dl_copies = COConfigurationManager.getIntParameter("StartStopManager_iAddForSeedingDLCopyCount");

					   saved_data_bytes_downloaded = download_manager.getSize()*dl_copies;
				   }
			   }

			   dm_stats.restoreSessionTotals(
					   saved_data_bytes_downloaded,
					   saved_data_bytes_uploaded,
					   saved_discarded,
					   saved_hashfails,
					   saved_SecondsDownloading,
					   saved_SecondsOnlySeeding );

			   boolean isCompleted = download_manager.isDownloadComplete(false);

			   if (download_manager.getPosition() == -1) {

				   int endPosition = 0;

				   for ( DownloadManager dm: managers_list_cow ){

					   boolean dmIsCompleted = dm.isDownloadComplete(false);

					   if (dmIsCompleted == isCompleted){

						   endPosition++;
					   }
				   }

				   download_manager.setPosition(endPosition + 1);
			   }

			   // Even though when the DownloadManager was created, onlySeeding was
			   // most likely set to true for completed torrents (via the Initializer +
			   // readTorrent), there's a chance that the torrent file didn't have the
			   // resume data.  If it didn't, but we marked it as complete in our
			   // downloads config file, we should set to onlySeeding

			   download_manager.requestAssumedCompleteMode();

			   int len = managers_list_cow.length;

			   DownloadManager[]	new_download_managers = new DownloadManager[ len+1 ];

			   System.arraycopy( managers_list_cow, 0, new_download_managers, 0, len );

			   new_download_managers[len] = download_manager;

			   managers_list_cow	= new_download_managers;

			   manager_id_set.put( download_manager,  download_manager );
	
			   if ( hashwrapper != null ){

				   manager_hash_map.put( hashwrapper, download_manager );
			   }

			   // Old completed downloads should have their "considered for move on completion"
			   // flag set, to prevent them being moved.
			   if (COConfigurationManager.getBooleanParameter("Set Completion Flag For Completed Downloads On Start")) {

				   // We only want to know about truly complete downloads, since we aren't able to move partially complete
				   // ones yet.
				   if (download_manager.isDownloadComplete(true)) {
					   download_manager.getDownloadState().setFlag(DownloadManagerState.FLAG_MOVE_ON_COMPLETION_DONE, true);
				   }
			   }

			   if (notifyListeners) {

				   listeners_and_event_listeners.dispatch( LDT_MANAGER_ADDED, download_manager );

				   taggable_life_manager.taggableCreated( download_manager );

				   if ( host_support != null ){

					   host_support.torrentAdded( download_manager.getTorrentFileName(), download_manager.getTorrent());
				   }
			   }

			   download_manager.addListener(this);

			   if ( save_download_state != null ){

				   Long lForceStart = (Long) save_download_state.get("forceStart");
				   if (lForceStart == null) {
					   Long lStartStopLocked = (Long) save_download_state.get("startStopLocked");
					   if(lStartStopLocked != null) {
						   lForceStart = lStartStopLocked;
					   }
				   }

				   if(lForceStart != null) {
					   if(lForceStart.intValue() == 1) {
						   download_manager.setForceStart(true);
					   }
				   }
			   }
		   }

		   return( download_manager );
	   
	   }else{
		   
		   Logger.log(new LogEvent(LOGID, LogEvent.LT_ERROR,
				   "Tried to add a DownloadManager after shutdown of GlobalManager."));
		   
		   return( null );
	   }
  }

   @Override
   public void
   clearNonPersistentDownloadState(
		   byte[] hash )
   {
	   saved_download_manager_state.remove( new HashWrapper( hash ));
   }

  @Override
  public List<DownloadManager> getDownloadManagers() {
	 
	  List<DownloadManager> result = Arrays.asList( managers_list_cow );
	  
	  if ( Constants.isCVSVersion()){
		  
		  return(  Collections.unmodifiableList( result ));	// check that nobody modifies things
		  
	  }else{
		  
		  return( result );
	  }
  }

  @Override
  public DownloadManager getDownloadManager(TOTorrent torrent) {
  	if (torrent == null) {
  		return null;
  	}
    try {
      return getDownloadManager(torrent.getHashWrapper());
    } catch (TOTorrentException e) {
      return null;
    }
  }

  @Override
  public DownloadManager
  getDownloadManager(HashWrapper	hw)
  {     
	  return( manager_hash_map.get( hw ));
  }

  @Override
  public void
  canDownloadManagerBeRemoved(
  	DownloadManager manager,
  	boolean remove_torrent, boolean remove_data)

  	throws GlobalManagerDownloadRemovalVetoException
  {
	if ( manager.getDownloadState().getFlag( DownloadManagerState.FLAG_METADATA_DOWNLOAD )){
		
			// can always remove these
		
		return;
	}
	  
  	try{
  		removal_listeners.dispatchWithException(LDT_MANAGER_WBR, new Object[] {
				manager,
				Boolean.valueOf(remove_torrent),
			  Boolean.valueOf(remove_data)
			});

  	}catch( Throwable e ){
  		if (e instanceof GlobalManagerDownloadRemovalVetoException) {
    		throw((GlobalManagerDownloadRemovalVetoException)e);
  		}
			GlobalManagerDownloadRemovalVetoException gmv = new GlobalManagerDownloadRemovalVetoException("Error running veto check");
			gmv.initCause(e);
			Debug.out(e);
			throw gmv;
  	}
  }


  @Override
  public void
  removeDownloadManager(
  	DownloadManager manager)

  	throws GlobalManagerDownloadRemovalVetoException
  {
  	removeDownloadManager(manager, false, false);
  }

  @Override
  public void
  removeDownloadManager(
		DownloadManager manager,
		boolean	remove_torrent,
		boolean	remove_data )

  	throws GlobalManagerDownloadRemovalVetoException
  {
	  TOTorrent		torrent	= null;
	  HashWrapper	hash	= null;
	  
	  try{
		  torrent = manager.getTorrent();
		  
		  hash = torrent==null?null:torrent.getHashWrapper();
		  
		  boolean isMagnet = manager.getDownloadState().getFlag( DownloadManagerState.FLAG_METADATA_DOWNLOAD );
		  
		  FileUtil.log( "removeDownload: " + (hash==null?manager.getDisplayName():ByteFormatter.encodeString(hash.getBytes())) + ": isMagnet=" + isMagnet );
		  
	  }catch( Throwable e ){
	  }
	  
	  // simple protection against people calling this twice

	  synchronized( managers_lock ){

		  if ( !manager_id_set.containsKey( manager )){

			  return;
		  }
	  }

	  canDownloadManagerBeRemoved( manager, remove_torrent, remove_data );

	  if ( ds_tagger != null ){
	  
		  ds_tagger.removeInitiated( manager );
	  }
	  
	  manager.stopIt(DownloadManager.STATE_STOPPED, remove_torrent, remove_data, true );

	  synchronized( managers_lock ){

		  if ( !manager_id_set.containsKey( manager )){

			  return;
		  }

		  int	len = managers_list_cow.length;

		  for ( int i=0; i<len; i++ ){

			  if ( managers_list_cow[i].equals( manager )){

				  DownloadManager[] new_download_managers	= new DownloadManager[len-1];

				  if ( i > 0 ){ 
					  
					  System.arraycopy( managers_list_cow, 0, new_download_managers, 0, i );
				  }
				  
				  if (  new_download_managers.length - i > 0 ){
					  
					  System.arraycopy(managers_list_cow, i + 1, new_download_managers, i, new_download_managers.length - i);
				  }

				  managers_list_cow = new_download_managers;

				  break;
			  }
		  }


		  manager_id_set.remove( manager );

		  if ( hash == null || manager_hash_map.remove( hash ) == null ){
			  			  
			  Iterator<DownloadManager> it = manager_hash_map.values().iterator();
			  
			  boolean removed = false;
			  
			  while( it.hasNext()){
				  
				  if ( it.next() == manager ){
					  
					  it.remove();
					  
					  removed = true;
				  }
			  }
			  
			  if ( !removed ){
				  
				  Debug.out( "Failed to remove download " + manager.getDisplayName() + " from manager hash map" );
			  }
		  }
	  }

	  // when we remove a download manager from the client this is the time to remove it from the record of
	  // created torrents if present

	  if ( torrent != null ){

		  TorrentUtils.removeCreatedTorrent( torrent );
	  }

	  manager.destroy( false );

	  fixUpDownloadManagerPositions();

	  listeners_and_event_listeners.dispatch( LDT_MANAGER_REMOVED, manager );

	  TorrentUtils.setTorrentDeleted();

	  taggable_life_manager.taggableDestroyed( manager );

	  manager.removeListener(this);

	  DownloadManagerState dms = manager.getDownloadState();

	  if ( dms.getCategory() != null){

		  dms.setCategory(null);
	  }

	  if ( torrent != null ) {

		  trackerScraper.remove( torrent );
	  }

	  if ( host_support != null ){

		  host_support.torrentRemoved( manager.getTorrentFileName(), torrent);
	  }

	  // delete the state last as passivating a hosted torrent may require access to
	  // the existing torrent state

	  dms.delete();
  }

	public boolean isStopping() {
		return isStopping;
	}

  /* Puts GlobalManager in a stopped state.
   * Used when closing down the client.
   */
  @Override
  public void
  stopGlobalManager(
	  GlobalMangerProgressListener	listener )
  {
	 synchronized( managers_lock ){

		  if ( isStopping ){

			  return;
		  }

		  isStopping	= true;
	  }

	  stats.save();

	  informDestroyInitiated( listener );

	  if ( host_support != null ){
		  host_support.destroy();
	  }

	  torrent_folder_watcher.destroy();

	  // kick off a non-daemon task. This will ensure that we hang around
	  // for at least LINGER_PERIOD to run other non-daemon tasks such as writing
	  // torrent resume data...

	  try{
		  NonDaemonTaskRunner.run(
				  new NonDaemonTask()
				  {
					  @Override
					  public Object
					  run()
					  {
						  return( null );
					  }

					  @Override
					  public String
					  getName()
					  {
						  return( "Stopping global manager" );
					  }
				  });
	  }catch( Throwable e ){
		  Debug.printStackTrace( e );
	  }

	  checker.stopIt();

	  if ( COConfigurationManager.getBooleanParameter("Pause Downloads On Exit" )){

		  pauseDownloads( true );

		  // do this before save-downloads so paused state gets saved

		  stopAllDownloads( true, new GlobalMangerProgressListener.GlobalMangerProgressAdapter( listener, 0, 49 ));

		  saveDownloads( false, new GlobalMangerProgressListener.GlobalMangerProgressAdapter( listener, 50, 100 ) );

	  }else{

		  saveDownloads( false, new GlobalMangerProgressListener.GlobalMangerProgressAdapter( listener, 0, 49 ) );

		  stopAllDownloads( true, new GlobalMangerProgressListener.GlobalMangerProgressAdapter( listener, 50, 100 ) );
	  }

	  if ( stats_writer != null ){

		  stats_writer.destroy();
	  }

	  // Disable DNS Mods lookup while shutting down
	  TorrentUtils.temporarilyDisableDNSHandling();
	  DownloadManagerStateFactory.saveGlobalStateCache();

	  synchronized( managers_lock ){

		  managers_list_cow	= new DownloadManager[0];

		  manager_id_set.clear();
		  
		  manager_hash_map.clear();
	  }

	  informDestroyed();
  }

  @Override
  public void stopAllDownloads() {
	  try{
		  NonDaemonTaskRunner.run(
			new NonDaemonTask(){
				
				@Override
				public Object run() throws Throwable{
					  stopAllDownloads(false, new GlobalMangerProgressListener.GlobalMangerProgressAdapter());
					  return( null );
				}
				
				@Override
				public String getName(){
					return( "Manual 'stop all downloads'");
				}
			});
	  }catch( Throwable e ){
		  Debug.out( e );
	  }
  }

  protected void stopAllDownloads(boolean for_close, GlobalMangerProgressListener listener ) {

	if ( for_close ){
		if (progress_listener != null){
			  progress_listener.reportCurrentTask(MessageText.getString("splash.unloadingTorrents"));
		}
	}

	long	lastListenerUpdate = 0;

	List<DownloadManager> managers = sortForStop();

	int nbDownloads = managers.size();

	String prefix = MessageText.getString( "label.stopping.downloads" );
	
    for ( int i=0;i<nbDownloads;i++){

      DownloadManager manager = managers.get(i);

      long	now = SystemTime.getCurrentTime();

	  if(progress_listener != null &&  now - lastListenerUpdate > 100) {
		  lastListenerUpdate = now;

		  int	currentDownload = i+1;

		  progress_listener.reportPercent(100 * currentDownload / nbDownloads);
		  progress_listener.reportCurrentTask(MessageText.getString("splash.unloadingTorrent")
				  + " " + currentDownload + " "
				  + MessageText.getString("splash.of") + " " + nbDownloads
				  + " : " + manager.getTorrentFileName());
	  }

      int state = manager.getState();

      if( state != DownloadManager.STATE_STOPPED &&
          state != DownloadManager.STATE_STOPPING ) {

    	listener.reportCurrentTask( prefix + ": " + manager.getDisplayName());;
    	  
        manager.stopIt( for_close?DownloadManager.STATE_CLOSED:DownloadManager.STATE_STOPPED, false, false );
        
        listener.reportPercent((i*100)/nbDownloads );
      }
    }
    
    listener.reportPercent( 100 );
  }


  /**
   * Starts all downloads
   */
  @Override
  public void startAllDownloads() {
	  try{
		  NonDaemonTaskRunner.run(
			  new NonDaemonTask(){

				  @Override
				  public Object run() throws Throwable{
						DownloadManager[] managers = managers_list_cow;
						
						for ( DownloadManager manager: managers ){

						  if ( manager.getState() == DownloadManager.STATE_STOPPED ){

							  manager.stopIt( DownloadManager.STATE_QUEUED, false, false );
						  }
					  }
					  return( null );
				  }

				  @Override
				  public String getName(){
					  return( "Manual 'start all downloads'");
				  }
			  });
	  }catch( Throwable e ){
		  Debug.out( e );
	  }
  }

  @Override
  public boolean
  pauseDownload(
	DownloadManager	manager,
	boolean			only_if_active )
  {
	  if ( manager.getTorrent() == null ) {

		  return( false );
	  }
	  
	  if ( manager.isPaused()){
		  
		  if ( only_if_active ){
			  
			  return( false );
		  }
		  
		  return( true );
	  }

	  int state = manager.getState();

	  if ( 	// state != DownloadManager.STATE_STOPPED &&	decided to allow pausing of stopped torrents
			state != DownloadManager.STATE_ERROR &&
			state != DownloadManager.STATE_STOPPING ) {

		  if ( state == DownloadManager.STATE_STOPPED && only_if_active ){
			  
			  return( false );
		  }
		  
	      try{

	    	  HashWrapper	wrapper = manager.getTorrent().getHashWrapper();

	    	  boolean	forced = manager.isForceStart();

	    	  	// add first so anyone picking up the ->stopped transition see it is paused

	    	  try{
	    		  paused_list_mon.enter();

	    		  paused_list.put( wrapper, forced );

	    	  }finally{

	    		  paused_list_mon.exit();
	    	  }

	    	  if ( state != DownloadManager.STATE_STOPPED ){
	    	  
	    		  manager.stopIt( DownloadManager.STATE_STOPPED, false, false );
	    		  
	    	  }else{
	    		  
	    		  if ( ds_tagger != null ){
	    			  
	    			  ds_tagger.stateChanged( manager, DownloadManager.STATE_STOPPED );
	    		  }
	    	  }
	    	  
	    	  return( true );

	      }catch( TOTorrentException e ){

	    	  Debug.printStackTrace( e );
	      }
      }

	  return( false );
  }

  @Override
  public boolean 
  stopPausedDownload(
		DownloadManager dm )
  {
	  TOTorrent torrent = dm.getTorrent();
	  
	  if ( torrent == null ){
		  
		  return( false );
	  }
	  
	  boolean state_changed = false;
	  
	  try {
		  paused_list_mon.enter();

		  HashWrapper hw = torrent.getHashWrapper();
		 
		  if ( paused_list.containsKey( hw )){
			  
			  DownloadManager this_manager = getDownloadManager( hw );
	
			  if ( this_manager == dm ){
					  
				  paused_list.remove( hw );
	
				  state_changed = true;
					  
				  return( true );
			  }
		  }
	  }catch( Throwable e ){
		  
		  return( false );
		  
	  }finally{

		  paused_list_mon.exit();
		  
		  if ( state_changed ){
			  
			  if ( ds_tagger != null ){
				  
				  ds_tagger.stateChanged( dm, DownloadManager.STATE_STOPPED );
			  }
		  }
	  }
	  
	  return( false );
  }
  
  @Override
  public void
  pauseDownloadsForPeriod(
	  int seconds )
  {
	try{
      	paused_list_mon.enter();

      	if ( auto_resume_timer != null ){

      		auto_resume_timer.cancel();
      	}

      	auto_resume_timer =
      		SimpleTimer.addEvent(
      			"GM:auto-resume",
      			SystemTime.getOffsetTime( seconds*1000 ),
      			new TimerEventPerformer()
      			{
      				@Override
				      public void
      				perform(
      					TimerEvent event )
      				{
      					resumeDownloads();
      				}
      			});
	}finally{

		paused_list_mon.exit();
	}

	pauseDownloads();
  }

  @Override
  public int
  getPauseDownloadPeriodRemaining()
  {
	  try{
	      	paused_list_mon.enter();

	      	if ( auto_resume_timer != null ){

	      		long rem = auto_resume_timer.getWhen() - SystemTime.getCurrentTime();

	      		return(Math.max( 0, (int)(rem/1000)));
	      	}
	  }finally{

			paused_list_mon.exit();
	  }

	  return( 0 );
  }

  @Override
  public void
  pauseDownloads()
  {
	  try{
		  NonDaemonTaskRunner.run(
			new NonDaemonTask(){
				
				@Override
				public Object run() throws Throwable{
					  pauseDownloads( false );
					  return( null );
				}
				
				@Override
				public String getName(){
					return( "Manual 'pause all downloads'");
				}
			});
	  }catch( Throwable e ){
		  Debug.out( e );
	  }
  }

  protected void
  pauseDownloads(
	boolean	tag_only )
  {
	List<DownloadManager> managers = sortForStop();

    for( DownloadManager manager: managers ){

      if ( manager.getTorrent() == null ) {
        continue;
      }

      int state = manager.getState();

      if( state != DownloadManager.STATE_STOPPED &&
          state != DownloadManager.STATE_ERROR &&
          state != DownloadManager.STATE_STOPPING ) {

        try {
        	boolean	forced = manager.isForceStart();

        		// add before stopping so anyone picking up the ->stopped transition sees that it is
        		// paused

          	try {
          		paused_list_mon.enter();

          		paused_list.put( manager.getTorrent().getHashWrapper(), forced );

	    	}finally{

	    		paused_list_mon.exit();
	    	}

	    	if ( !tag_only ){

	    		manager.stopIt( DownloadManager.STATE_STOPPED, false, false );
	    	}

        }catch( TOTorrentException e ) {
        	Debug.printStackTrace( e );
        }
      }
    }
  }

  	public boolean
  	canPauseDownload(
  		DownloadManager	manager )
  	{

      if( manager.getTorrent() == null ) {

    	  return( false );
      }

      int state = manager.getState();

      if( state != DownloadManager.STATE_STOPPED &&
          state != DownloadManager.STATE_ERROR &&
          state != DownloadManager.STATE_STOPPING ) {

        return( true );
      }

      return false;
  	}

	@Override
	public boolean
	isPaused(
		DownloadManager	manager )
	{
		TOTorrent torrent = manager.getTorrent();
		
		if ( torrent == null || paused_list.size() == 0 ){

			return( false );
		}

		try {
			paused_list_mon.enter();

			HashWrapper hw =  torrent.getHashWrapper();
						
		    if ( paused_list.containsKey( hw )){

		        DownloadManager this_manager = getDownloadManager( hw );

		        if ( this_manager == manager ){

		        	return( true );
		        }
		    }

		    return( false );

		}catch( Throwable e ){
			
			return( false );
			
		}finally{

			paused_list_mon.exit();
		}
	}

	@Override
	public boolean
	canPauseDownloads()
	{
		DownloadManager[] managers = managers_list_cow;
		
		for ( DownloadManager manager: managers ){

			if ( canPauseDownload( manager )){

				return( true );
			}
		}
		return false;
	}


  @Override
  public void
  resumeDownload(
	DownloadManager	manager )
  {
	boolean	resume_ok 	= false;
	boolean force		= false;

    TOTorrent torrent = manager.getTorrent();

    if ( torrent == null ){
    	
    	return;
    }
    
    try {
    	paused_list_mon.enter();

    	HashWrapper hw = torrent.getHashWrapper();

    	Boolean forced = paused_list.get( hw );

    	if ( forced != null ){

    		force = forced;

    		DownloadManager this_manager = getDownloadManager( hw );

    		if ( this_manager == manager ){

    			resume_ok	= true;

    			paused_list.remove( hw );
    		}
    	}
	}catch( Throwable e ){
		
		return;
		
	}finally{

		paused_list_mon.exit();
   	}

	if ( resume_ok ){

		if ( manager.getState() == DownloadManager.STATE_STOPPED ) {

			if ( force ){

        		manager.setForceStart(true);

        	}else{

        		manager.stopIt( DownloadManager.STATE_QUEUED, false, false );
        	}
        }
	}
  }

  @Override
  public boolean
  resumingDownload(
	 DownloadManager manager)
  {
	  TOTorrent torrent = manager.getTorrent();

	  if ( torrent == null ){
		  
		  return( false );
	  }
		try {
			paused_list_mon.enter();

			HashWrapper	hw = torrent.getHashWrapper();
			
			if ( paused_list.containsKey( hw )){

		        DownloadManager this_manager = getDownloadManager( hw );

		        if ( this_manager == manager ){

		        	Boolean force = paused_list.remove( hw );

		        	return( force != null && force );
		        }
		    }
		}catch( Throwable e ){
			
			return( false );
			
		}finally{

			paused_list_mon.exit();
	   	}

		return( false );
  }


  @Override
  public void
  resumeDownloads()
  {
	  try{
		  NonDaemonTaskRunner.run(
			  new NonDaemonTask(){

				  @Override
				  public Object run() throws Throwable{
					  auto_resume_disabled = false;

					  try{
						  paused_list_mon.enter();

						  if ( auto_resume_timer != null ){

							  auto_resume_timer.cancel();

							  auto_resume_timer = null;
						  }

						  // copy the list as the act of resuming entries causes entries to be removed from the
						  // list and therefore borkerage

						  Map<HashWrapper,Boolean> copy = new HashMap<>(paused_list);

						  for( Map.Entry<HashWrapper, Boolean> entry: copy.entrySet()){

							  HashWrapper 	hash 	= entry.getKey();
							  boolean		force 	= entry.getValue();

							  DownloadManager manager = getDownloadManager( hash );

							  if( manager != null && manager.getState() == DownloadManager.STATE_STOPPED ) {

								  if ( force ){

									  manager.setForceStart(true);

								  }else{

									  manager.stopIt( DownloadManager.STATE_QUEUED, false, false );
								  }
							  }
						  }
						  paused_list.clear();

					  }finally{

						  paused_list_mon.exit();
					  }
					  return( null );
				  }

				  @Override
				  public String getName(){
					  return( "Manual 'pause all downloads'");
				  }
			  });
	  }catch( Throwable e ){
		  Debug.out( e );
	  }
  }

  @Override
  public boolean
  resumeDownloads(
	 boolean is_auto_resume)
  {
	if ( is_auto_resume && auto_resume_disabled ){

		return( false );
	}

	resumeDownloads();

	return( true );
  }

  @Override
  public boolean canResumeDownloads() {
    try {  paused_list_mon.enter();
      for( HashWrapper hash: paused_list.keySet()){
     
        DownloadManager manager = getDownloadManager( hash );

        if( manager != null && manager.getState() == DownloadManager.STATE_STOPPED ) {
          return true;
        }
      }
    }
    finally {  paused_list_mon.exit();  }

    return false;
  }

  @Override
  public boolean isSwarmMerging(DownloadManager dm) {
	  return( file_merger.isSwarmMergingZ(dm));
  }
  
  @Override
  public String getSwarmMergingInfo(DownloadManager dm) {
	  return( file_merger.getSwarmMergingInfo(dm));
  }

  	private List<DownloadManager>
  	sortForStop()
	{
  		List<DownloadManager>	managers = new ArrayList<>( getDownloadManagers());

  		Collections.sort(
  			managers,
  			new Comparator<DownloadManager>()
  			{
  				@Override
				  public int
  				compare(
  					DownloadManager o1,
  					DownloadManager o2)
  				{
  					int s1 = o1.getState();
  					int s2 = o2.getState();

  					if ( s2 == DownloadManager.STATE_QUEUED ){

  						return( 1 );

  					}else if ( s1 == DownloadManager.STATE_QUEUED ){

  						return( -1 );
  					}

  					return( 0 );
  				}
  			});

  		return( managers );
	}

  void loadDownloads()
  {
	  if (this.cripple_downloads_config) {
		  loadingComplete = true;
		  loadingSem.releaseForever();
		  return;
	  }

	  boolean pause_active = COConfigurationManager.getBooleanParameter( "Pause Downloads On Start After Resume", false );

	  if ( pause_active ){
		  
		  COConfigurationManager.removeParameter( "Pause Downloads On Start After Resume" );
		  
		  COConfigurationManager.setParameter( "br.restore.autopause", true );
	  }


	  try{
		  DownloadManagerStateFactory.loadGlobalStateCache();

		  int triggerOnCount = 2;
		  ArrayList<DownloadManager> downloadsAdded = new ArrayList<>();
		  lastListenerUpdate = 0;
		  try{
			  if (progress_listener != null){
				  progress_listener.reportCurrentTask(MessageText.getString("splash.loadingTorrents"));
			  }

			  Map map = FileUtil.readResilientConfigFile("downloads.config");

			  ArrayList pause_data = (ArrayList)map.get( "pause_data" );

			  boolean debug = Boolean.getBoolean("debug");

			  Iterator iter = null;
			  //v2.0.3.0+ vs older mode
			  List downloads = (List) map.get("downloads");
			  int nbDownloads;
			  if (downloads == null) {
				  //No downloads entry, then use the old way
				  iter = map.values().iterator();
				  nbDownloads = map.size();
			  }
			  else {
				  //New way, downloads stored in a list
				  iter = downloads.iterator();
				  nbDownloads = downloads.size();
			  }
			  int currentDownload = 0;
			  while (iter.hasNext()) {
				  currentDownload++;
				  Map mDownload = (Map) iter.next();

				  if ( pause_active ){
					  
					  try{
						  int 		state 	= ((Number)mDownload.get( "state" )).intValue();
						  boolean 	fs 		= ((Number)mDownload.get( "forceStart" )).intValue() != 0;
						  
						  if ( state != DownloadManager.STATE_STOPPED ){
							  
							  mDownload.put( "state", new Long( DownloadManager.STATE_STOPPED ));
							  mDownload.remove( "forceStart" );
							  
							  byte[] key = (byte[])mDownload.get( "torrent_hash" );
							  
							  if ( pause_data == null ){
								  
								  pause_data = new ArrayList();
							  }
							  
							  Map m = new HashMap();
							  
							  m.put( "hash", key );
							  m.put( "force", new Long( fs?1:0 ));
							  
							  pause_data.add( m );
						  }
						  
					  }catch( Throwable e ){
						  Debug.printStackTrace( e );
					  }
				  }
				  DownloadManager dm =
					  loadDownload(
							  mDownload,
							  currentDownload,
							  nbDownloads,
							  progress_listener,
							  debug );

				  if ( dm != null ){

					  downloadsAdded.add(dm);

					  if (downloadsAdded.size() >= triggerOnCount) {
						  triggerOnCount *= 2;
						  triggerAddListener(downloadsAdded);
						  downloadsAdded.clear();
					  }
				  }
			  }

			  // This is set to true by default, but once the downloads have been loaded, we have no reason to ever
			  // to do this check again - we only want to do it once to upgrade the state of existing downloads
			  // created before this code was around.
			  COConfigurationManager.setParameter("Set Completion Flag For Completed Downloads On Start", false);

			  //load pause/resume state
			  if( pause_data != null ) {
				  try {  paused_list_mon.enter();
					  for( int i=0; i < pause_data.size(); i++ ) {
						  Object	pd = pause_data.get(i);
	
						  byte[]		key;
						  boolean		force;
	
						  if ( pd instanceof byte[]){
							  // old style, migration purposes
							  key 	= (byte[])pause_data.get( i );
							  force	= false;
						  }else{
							  Map	m = (Map)pd;
	
							  key 	= (byte[])m.get("hash");
							  force 	= ((Long)m.get("force")).intValue() == 1;
						  }
						  
						  HashWrapper hw = new HashWrapper( key );
						  
						  Boolean b_force = Boolean.valueOf(force);
						  
						  paused_list.put( hw, b_force );
						  
						  paused_list_initial.put( hw, b_force );
					  }
				  }
				  
				  finally {  paused_list_mon.exit();  }
			  }


			  // Someone could have mucked with the config file and set weird positions,
			  // so fix them up.
			  fixUpDownloadManagerPositions();
			  Logger.log(new LogEvent(LOGID, "Loaded " + managers_list_cow.length + " torrents"));

		  }catch( Throwable e ){
			  // there's been problems with corrupted download files stopping AZ from starting
			  // added this to try and prevent such foolishness

			  Debug.printStackTrace( e );
		  } finally {
			  loadingComplete = true;
			  triggerAddListener(downloadsAdded);

			  loadingSem.releaseForever();
		  }

	  }finally{

		  DownloadManagerStateFactory.discardGlobalStateCache();
	  }
  }

  private void triggerAddListener(List downloadsToAdd) {
		synchronized( managers_lock ){
			List listenersCopy = listeners_and_event_listeners.getListenersCopy();

			for (int j = 0; j < listenersCopy.size(); j++) {
				Object listener = listenersCopy.get(j);

				if ( listener instanceof GlobalManagerListener ){
					GlobalManagerListener gmListener = (GlobalManagerListener)listener;
					for (int i = 0; i < downloadsToAdd.size(); i++) {
						DownloadManager dm = (DownloadManager) downloadsToAdd.get(i);
						gmListener.downloadManagerAdded(dm);
					}
				}
			}
		}
  }

  @Override
  public void
  saveState()
  {
	  saveDownloads( false,null );
  }

  protected void
  saveDownloads(
	boolean							interim,
	GlobalMangerProgressListener 	listener_maybe_null  )
  {
	  if (!loadingComplete) {

		  return;
	  }

	  needsSavingCozStateChanged 	= 0;

	  if (this.cripple_downloads_config) {
		  return;
	  }

	  synchronized( managers_lock ){

		  DownloadManager[]	managers_temp = managers_list_cow.clone();

		  Arrays.sort(
				  managers_temp,
				  new Comparator<DownloadManager>()
				  {
					  @Override
					  public final int
					  compare(DownloadManager a, DownloadManager b) {
						  return ( a.getPosition() - b.getPosition());
					  }
				  });

		  managers_list_cow = managers_temp;

		  if (Logger.isEnabled()){
			  Logger.log(new LogEvent(LOGID, "Saving Download List ("	+ managers_temp.length + " items)"));
		  }

		  Map map = new HashMap();

		  int nbDownloads = managers_temp.length;

		  List<Map> list = new ArrayList<>(nbDownloads);

		  String prefix = MessageText.getString( "label.saving.downloads" );
		  
		  for ( int i=0;i<nbDownloads;i++){
			  
			  DownloadManager dm = managers_temp[i];

			  if ( listener_maybe_null != null ){
				
				  listener_maybe_null.reportCurrentTask( prefix + ": " + dm.getDisplayName());
				  
				  listener_maybe_null.reportPercent((i*100)/nbDownloads );
			  }
			  
			  Map dmMap = exportDownloadStateToMapSupport( dm, true, interim );

			  list.add(dmMap);
		  }

		  map.put("downloads", list);

		  //save pause/resume state
		  try {  paused_list_mon.enter();
			  if( !paused_list.isEmpty() ) {
				  ArrayList pause_data = new ArrayList();
				  for ( Map.Entry<HashWrapper,Boolean> entry: paused_list.entrySet()){
	
					  HashWrapper 	hash 	= entry.getKey();
					  Boolean		force 	= entry.getValue();
	
					  Map	m = new HashMap();
	
					  m.put( "hash", hash.getHash());
					  m.put( "force", new Long(force.booleanValue()?1:0));
	
					  pause_data.add( m );
				  }
				  map.put( "pause_data", pause_data );
			  }
		  }
		  finally {  paused_list_mon.exit();  }


		  FileUtil.writeResilientConfigFile("downloads.config", map );
	  }
  }

  public DownloadManager
  loadDownload(
	Map 							mDownload,
	int								currentDownload,
	int								nbDownloads,
	GlobalMangerProgressListener	progress_listener,
	boolean							debug )
  {
	  try {
		  byte[]	torrent_hash = (byte[])mDownload.get( "torrent_hash" );

		  Long	lPersistent = (Long)mDownload.get( "persistent" );

		  boolean	persistent = lPersistent==null || lPersistent.longValue()==1;


		  String fileName = new String((byte[]) mDownload.get("torrent"), Constants.DEFAULT_ENCODING_CHARSET);

		  if ( progress_listener != null &&  SystemTime.getCurrentTime() - lastListenerUpdate > 100) {
			  lastListenerUpdate = SystemTime.getCurrentTime();

			  String shortFileName = fileName;
			  try {
				  File f = FileUtil.newFile(fileName);
				  shortFileName = f.getName();
			  } catch (Exception e) {
				// TODO: handle exception
			}

			  progress_listener.reportPercent(100 * currentDownload / nbDownloads);
			  progress_listener.reportCurrentTask(MessageText.getString("splash.loadingTorrent")
					  + " " + currentDownload + " "
					  + MessageText.getString("splash.of") + " " + nbDownloads
					  + " : " + shortFileName );
		  }

		  //migration from using a single savePath to a separate dir and file entry
		  String	torrent_save_dir;
		  String	torrent_save_file;

		  byte[] torrent_save_dir_bytes   = (byte[]) mDownload.get("save_dir");

		  if ( torrent_save_dir_bytes != null ){

			  byte[] torrent_save_file_bytes 	= (byte[]) mDownload.get("save_file");

			  torrent_save_dir	= new String(torrent_save_dir_bytes, Constants.DEFAULT_ENCODING_CHARSET);

			  if ( torrent_save_file_bytes != null ){

				  torrent_save_file	= new String(torrent_save_file_bytes, Constants.DEFAULT_ENCODING_CHARSET);
			  }else{

				  torrent_save_file	= null;
			  }
		  }else{

			  byte[] savePathBytes = (byte[]) mDownload.get("path");
			  torrent_save_dir 	= new String(savePathBytes, Constants.DEFAULT_ENCODING_CHARSET);
			  torrent_save_file	= null;
		  }



		  int state = DownloadManager.STATE_WAITING;
		  if (debug){

			  state = DownloadManager.STATE_STOPPED;

		  }else {

			  if (mDownload.containsKey("state")) {
				  state = ((Long) mDownload.get("state")).intValue();
				  if (state != DownloadManager.STATE_STOPPED &&
						  state != DownloadManager.STATE_QUEUED &&
						  state != DownloadManager.STATE_WAITING)

					  state = DownloadManager.STATE_QUEUED;

			  }else{

				  int stopped = ((Long) mDownload.get("stopped")).intValue();

				  if (stopped == 1){

					  state = DownloadManager.STATE_STOPPED;
				  }
			  }
		  }

		  Long seconds_downloading = (Long)mDownload.get("secondsDownloading");

		  boolean	has_ever_been_started = seconds_downloading != null && seconds_downloading.longValue() > 0;

		  if (torrent_hash != null) {
			  saved_download_manager_state.put(new HashWrapper(torrent_hash),
					  mDownload);
		  }

		  // for non-persistent downloads the state will be picked up if the download is re-added
		  // it won't get saved unless it is picked up, hence dead data is dropped as required

		  if ( persistent ){

		  	List file_priorities;
			  Map map_file_priorities = (Map) mDownload.get("file_priorities_c");

			  if (map_file_priorities != null) {
					// We don't know how many files, so we need to build array
					Long[] array_file_priorities = new Long[0];
			  	for (Object key : map_file_priorities.keySet()) {
						long priority = Long.parseLong(key.toString());
						String indexRanges = new String((byte[]) map_file_priorities.get(key), "utf-8");
						String[] rangesStrings = indexRanges.split(",");

						if (array_file_priorities.length == 0 && rangesStrings.length > 1) {
							// going to be at least the length of # of ranges
							array_file_priorities = new Long[rangesStrings.length];
						}

						for (String rangeString : rangesStrings) {
							String[] ranges = rangeString.split("-");
							int start = Integer.parseInt(ranges[0]);
							int end = ranges.length == 1 ? start : Integer.parseInt(ranges[1]);
							if (end >= array_file_priorities.length) {
								array_file_priorities = enlargeLongArray(array_file_priorities, end + 1);
							}
							Arrays.fill(array_file_priorities, start, end + 1, priority);
						}
					}
			  	file_priorities = Arrays.asList(array_file_priorities);
			  } else {
			  	file_priorities = (List) mDownload.get("file_priorities");
			  }

			  synchronized( create_dm_lock ){
				  
				  DownloadManager new_dm =
					  DownloadManagerFactory.create(
							  this, torrent_hash, fileName, torrent_save_dir, torrent_save_file,
							  state, true, true, false, has_ever_been_started, file_priorities );
	
				  boolean[] existing = { false };
				  
				  addDownloadManager( new_dm, false, null, false, existing, true );
				  
				  if ( !existing[0] ){
	
					  	// recover any error state
					  
					  Long errorType = (Long)mDownload.get( "errorType" );
					  
					  if ( errorType != null ){
						  
						  String errorDetails = MapUtils.getMapString( mDownload, "errorDetails", null );
						  
						  Long errorFlags = (Long)mDownload.get( "errorFlags" );
						  
						  new_dm.setErrorState(((Long)errorType).intValue(), errorDetails, errorFlags==null?0:errorFlags.intValue());
					  }
					  
					  return( new_dm );
				  }
			  }
		  }
	  }
	  catch (UnsupportedEncodingException e1) {
		  //Do nothing and process next.
	  }
	  catch (Throwable e) {
		  Logger.log(new LogEvent(LOGID,
				  "Error while loading downloads.  " +
				  "One download may not have been added to the list.", e));
	  }

	  return( null );
  }

  public static Long[] enlargeLongArray(Long[] array, int expandTo) {
		Long[] new_array = new Long[expandTo];
		if (array.length > 0) {
			System.arraycopy(array, 0,
					new_array, 0,
					array.length);
		}
		return new_array;
  }

  @Override
  public Map
  exportDownloadStateToMap(
	  DownloadManager		dm )
  {
	  return( exportDownloadStateToMapSupport( dm, false, false ));
  }

  @Override
  public DownloadManager
  importDownloadStateFromMap(
	  Map		map )
  {
	  DownloadManager dm = loadDownload( map, 1, 1, null, false );

	  if ( dm != null ){

		  List<DownloadManager> dms = new ArrayList<>(1);

		  dms.add( dm );

		  triggerAddListener( dms );

		  taggable_life_manager.taggableCreated( dm );

          if ( host_support != null ){

          	host_support.torrentAdded( dm.getTorrentFileName(), dm.getTorrent());
          }
	  }

	  return( dm );
  }

  private Map
  exportDownloadStateToMapSupport(
	DownloadManager 	dm,
	boolean				internal_export,
	boolean				interim )
  {
	  DownloadManagerStats dm_stats = dm.getStats();
	  Map<String, Object> dmMap = new HashMap<>();
	  TOTorrent	torrent = dm.getTorrent();

	  if ( torrent != null ){
		  try{
			  dmMap.put( "torrent_hash", torrent.getHash());

		  }catch( TOTorrentException e ){

			  Debug.printStackTrace(e);
		  }
	  }

	  File	save_loc = dm.getAbsoluteSaveLocation();
	  dmMap.put("persistent", new Long(dm.isPersistent()?1:0));
	  dmMap.put("torrent", dm.getTorrentFileName());
	  dmMap.put("save_dir", save_loc.getParent());
	  dmMap.put("save_file", save_loc.getName());

	  dmMap.put("maxdl", new Long( dm_stats.getDownloadRateLimitBytesPerSecond() ));
	  dmMap.put("maxul", new Long( dm_stats.getUploadRateLimitBytesPerSecond() ));

	  int state = dm.getState();

	  if (state == DownloadManager.STATE_ERROR ){

		  // torrents in error state always come back stopped
		  // well, since 2203_B11 we remember the error state across restarts so we can continue
		  // attempting restarts

		  int errorType = dm.getErrorType();

		  if ( errorType != DownloadManager.ET_NONE ){
			  dmMap.put( "errorType", errorType );
			  dmMap.put( "errorFlags", dm.getErrorFlags());
			  
			  String errorDetails = dm.getErrorDetails();
			  
			  if ( errorDetails != null && !errorDetails.isEmpty()){
				  dmMap.put( "errorDetails", errorDetails );
			  }
		  }
		  
		  state = DownloadManager.STATE_STOPPED;	// keep stopped rather than error state for the moment for backwards compatability

	  }else if (	dm.getAssumedComplete() && !dm.isForceStart() &&
			  state != DownloadManager.STATE_STOPPED) {

		  state = DownloadManager.STATE_QUEUED;

	  }else if (	state != DownloadManager.STATE_STOPPED &&
			  state != DownloadManager.STATE_QUEUED &&
			  state != DownloadManager.STATE_WAITING){

		  state = DownloadManager.STATE_WAITING;

	  }

	  dmMap.put("state", new Long(state));

	  if ( internal_export ){
		  
		  dmMap.put("position", new Long(dm.getPosition()));
	  }
	  
	  dmMap.put("downloaded", new Long(dm_stats.getTotalDataBytesReceived()));
	  dmMap.put("uploaded", new Long(dm_stats.getTotalDataBytesSent()));
	  dmMap.put("completedbytes", new Long(dm_stats.getDownloadCompletedBytes()));
	  dmMap.put("discarded", new Long(dm_stats.getDiscarded()));
	  dmMap.put("hashfailbytes", new Long(dm_stats.getHashFailBytes()));
	  dmMap.put("forceStart", new Long(dm.isForceStart() && (dm.getState() != DownloadManager.STATE_CHECKING) ? 1 : 0));
	  dmMap.put("secondsDownloading", new Long(dm_stats.getSecondsDownloading()));
	  dmMap.put("secondsOnlySeeding", new Long(dm_stats.getSecondsOnlySeeding()));

	  // although this has been migrated, keep storing it to allow regression for a while
	  dmMap.put("uploads", new Long(dm.getMaxUploads()));

	  dmMap.put("creationTime", new Long( dm.getCreationTime()));

	  //save file priorities

	  dm.saveDownload( interim );

	  List file_priorities = (List)dm.getUserData( "file_priorities" );
	  if ( file_priorities != null ) {
	  	int count = file_priorities.size();
	  	Map<String, String> map_file_priorities = new HashMap<>();
	  	Long priority = (Long) file_priorities.get(0);
	  	int posStart = 0;
	  	int posEnd = 0;
	  	while (posStart < count) {
	  		priority = (Long) file_priorities.get(posStart);
	  		while (posEnd + 1 < count && (Long) file_priorities.get(posEnd + 1) == priority) {
	  			posEnd++;
	  		}
	  		String key = priority.toString();
	  		String val = map_file_priorities.get(key);
	  		if (val == null) {
	  			val = "" + posStart;
	  		} else {
	  			val += "," + posStart;
	  		}
	  		if (posStart != posEnd) {
	  			 val += "-" + posEnd;
	  		}
	  		map_file_priorities.put(key, val);
	  		posStart = posEnd + 1;
	  	}
	  	//dmMap.put( "file_priorities" , file_priorities );
	  	dmMap.put( "file_priorities_c" , map_file_priorities );
	  }

	  dmMap.put( "allocated", new Long(dm.isDataAlreadyAllocated() ? 1 : 0 ) );

	  return( dmMap );
  }

  /**
   * @return
   */
  @Override
  public TRTrackerScraper getTrackerScraper() {
    return trackerScraper;
  }

	@Override
	public GlobalManagerStats
	getStats()
	{
		return( stats );
	}

	@Override
	public boolean contains(DownloadManager manager) {
    if ( manager != null) {
        synchronized( managers_lock ){
        	return( manager_id_set.containsKey( manager ));
        }
    }
    return false;
  }

	@Override
  public boolean isMoveableUp(DownloadManager manager) {

    if ((manager.isDownloadComplete(false)) &&
        (COConfigurationManager.getIntParameter("StartStopManager_iRankType") != 0) &&
        (COConfigurationManager.getBooleanParameter("StartStopManager_bAutoReposition")))
      return false;

    return manager.getPosition() > 1;
  }

  @Override
  public int downloadManagerCount(boolean bCompleted) {
    int numInGroup = 0;
    DownloadManager[] managers = managers_list_cow;
    for ( DownloadManager dm: managers ){
      if (dm.isDownloadComplete(false) == bCompleted)
        numInGroup++;
    }
    return numInGroup;
  }

  @Override
  public boolean isMoveableDown(DownloadManager manager) {

    boolean isCompleted = manager.isDownloadComplete(false);

    if (isCompleted &&
        (COConfigurationManager.getIntParameter("StartStopManager_iRankType") != 0) &&
        (COConfigurationManager.getBooleanParameter("StartStopManager_bAutoReposition")))
      return false;

    return manager.getPosition() < downloadManagerCount(isCompleted);
  }

  @Override
  public void moveUp(DownloadManager manager) {
  	moveTo(manager, manager.getPosition() - 1);
  }

  @Override
  public void moveDown(DownloadManager manager) {
  	moveTo(manager, manager.getPosition() + 1);
  }

  @Override
  public void moveTop(DownloadManager[] manager) {

	  synchronized( managers_lock ){

		  int newPosition = 1;
		  for (int i = 0; i < manager.length; i++)
			  moveTo(manager[i], newPosition++);

	  }
  }

  @Override
  public void moveEnd(DownloadManager[] manager) {
	 synchronized( managers_lock ){

		  int endPosComplete = 0;
		  int endPosIncomplete = 0;
		  for ( DownloadManager dm: managers_list_cow ){
			  if (dm.isDownloadComplete(false))
				  endPosComplete++;
			  else
				  endPosIncomplete++;
		  }
		  for (int i = manager.length - 1; i >= 0; i--) {
			  if (manager[i].isDownloadComplete(false) && endPosComplete > 0) {
				  moveTo(manager[i], endPosComplete--);
			  } else if (endPosIncomplete > 0) {
				  moveTo(manager[i], endPosIncomplete--);
			  }
		  }
	  }
  }

  @Override
  public void
  moveTo(
		  DownloadManager manager, 
		  int newPosition) 
  {
	  boolean curCompleted = manager.isDownloadComplete(false);

	  if (newPosition < 1 || newPosition > downloadManagerCount(curCompleted))
		  return;

	  synchronized( managers_lock ){

		  int curPosition = manager.getPosition();
		  if (newPosition > curPosition) {
			  // move [manager] down
			  // move everything between [curPosition+1] and [newPosition] up(-) 1
			  int numToMove = newPosition - curPosition;
			  for ( DownloadManager dm: managers_list_cow ){
				  boolean dmCompleted = (dm.isDownloadComplete(false));
				  if (dmCompleted == curCompleted) {
					  int dmPosition = dm.getPosition();
					  if ((dmPosition > curPosition) && (dmPosition <= newPosition)) {
						  dm.setPosition(dmPosition - 1);
						  numToMove--;
						  if (numToMove <= 0)
							  break;
					  }
				  }
			  }

			  manager.setPosition(newPosition);
		  }
		  else if (newPosition < curPosition && curPosition > 1) {
			  // move [manager] up
			  // move everything between [newPosition] and [curPosition-1] down(+) 1
			  int numToMove = curPosition - newPosition;

			  for ( DownloadManager dm: managers_list_cow ){
				  boolean dmCompleted = (dm.isDownloadComplete(false));
				  int dmPosition = dm.getPosition();
				  if ((dmCompleted == curCompleted) &&
						  (dmPosition >= newPosition) &&
						  (dmPosition < curPosition)
						  ) {
					  dm.setPosition(dmPosition + 1);
					  numToMove--;
					  if (numToMove <= 0)
						  break;
				  }
			  }
			  manager.setPosition(newPosition);
		  }
	  }
  }

	@Override
	public void 
	fixUpDownloadManagerPositions() 
	{
		synchronized( managers_lock ){

			int posComplete = 1;
			int posIncomplete = 1;

			DownloadManager[]	managers_temp = managers_list_cow.clone();

			Arrays.sort(
				managers_temp,
				new Comparator<DownloadManager>()
				{
					@Override
					public final int compare (DownloadManager a, DownloadManager b) {
						int i = a.getPosition() - b.getPosition();
						if (i != 0) {
							return i;
						}

						// non persistent before persistent
						if (a.isPersistent()) {
							return 1;
						} else if (b.isPersistent()) {
							return -1;
						}

						return 0;
					}
				} );

			managers_list_cow = managers_temp;

			for ( DownloadManager dm: managers_temp ){
				
				if (dm.isDownloadComplete(false)){
					
					dm.setPosition(posComplete++);
					
				}else{
					
					dm.setPosition(posIncomplete++);
				}
			}
		}
    }


	@Override
	public long
	getResolverTaggableType()
	{
		return( Taggable.TT_DOWNLOAD );
	}

	@Override
	public Taggable
	resolveTaggable(
		String		id )
	{
		if ( id == null ){

			return( null );
		}

		return( getDownloadManager( new HashWrapper( Base32.decode( id ))));
	}

	@Override
	public List<Taggable>
	getResolvedTaggables()
	{
		return( new ArrayList<Taggable>( getDownloadManagers()));
	}

	public Object
	importDataSource(
		Map		map )
	{
		String id = (String)map.get( "id" );
		
		return( resolveTaggable(id));
	}
	
	@Override
	public String
	getDisplayName(
		Taggable taggable)
	{
		return(((DownloadManager)taggable).getDisplayName());
	}

	@Override
	public void
	requestAttention(
		String id )
	{
		DownloadManager dm =  getDownloadManager( new HashWrapper( Base32.decode( id )));

		if ( dm != null ){

			dm.requestAttention();
		}
	}
	
	@Override
	public void 
	addLifecycleControlListener(
		LifecycleControlListener l )
	{
		synchronized( lcl_map ){
			
			if ( lcl_map.containsKey( l )){
				
				Debug.out( "Listener already added" );
			}
			
			GlobalManagerDownloadWillBeRemovedListener gl = 
				new GlobalManagerDownloadWillBeRemovedListener(){
					
					@Override
					public void 
					downloadWillBeRemoved(
						DownloadManager manager, 
						boolean remove_torrent, 
						boolean remove_data )
					
						throws GlobalManagerDownloadRemovalVetoException
					{
				
						try{
							l.canTaggableBeRemoved( manager );
							
						}catch( Throwable e ){
							
							throw( new GlobalManagerDownloadRemovalVetoException( Debug.getNestedExceptionMessage( e )));
						}
					}
				};
				
			lcl_map.put( l, gl );
			
			addDownloadWillBeRemovedListener( gl );
		}
	}
	
	@Override
	public void 
	removeLifecycleControlListener(
		LifecycleControlListener l )
	{
		synchronized( lcl_map ){
			
			GlobalManagerDownloadWillBeRemovedListener gl = lcl_map.remove( l );
			
			if ( gl == null ){
				
				Debug.out( "Listener not found" );
				
			}else{
				
				removeDownloadWillBeRemovedListener( gl );
			}
		}
	}

  protected void  informDestroyed() {
  		if ( destroyed )
  		{
  			return;
  		}

  		destroyed = true;

  		/*
		Thread t = new Thread("Client: destroy checker")
			{
				public void
				run()
				{
					long	start = SystemTime.getCurrentTime();

					while(true){

						try{
							Thread.sleep(2500);
						}catch( Throwable e ){
							e.printStackTrace();
						}

						if ( SystemTime.getCurrentTime() - start > 10000 ){

								// java web start problem here...

							// Debug.dumpThreads("Client: slow stop - thread dump");

							// Debug.killAWTThreads(); doesn't work
						}
					}
				}
			};

		t.setDaemon(true);

		t.start();
		*/

  		listeners_and_event_listeners.dispatch( LDT_DESTROYED, null, true );
  }

  private void
  informDestroyInitiated(GlobalMangerProgressListener progress )
  {
	  listeners_and_event_listeners.dispatch( LDT_DESTROY_INITIATED, progress, true );
  }

 	@Override
  public void
	addListener(
		GlobalManagerListener	listener )
	{
 		addListener(listener, true);
	}

 	@Override
  public void
	addListener(
		GlobalManagerListener	listener,
		boolean trigger )
	{
		if ( isStopping ){

			listener.destroyed();

		}else{

			listeners_and_event_listeners.addListener(listener);

			if (!trigger) {
				return;
			}

			// Don't use Dispatch.. async is bad (esp for plugin initialization)
			
			synchronized( managers_lock ){
				
				for ( DownloadManager manager: managers_list_cow ){

				  listener.downloadManagerAdded( manager );
				}
			}
		}
	}

	@Override
	public void
 	removeListener(
		GlobalManagerListener	listener )
	{
		listeners_and_event_listeners.removeListener(listener);
	}

	@Override
	public void
	addEventListener(
		GlobalManagerEventListener 		listener )
	{
		listeners_and_event_listeners.addListener( listener );
	}

	@Override
	public void
	removeEventListener(
		GlobalManagerEventListener 		listener )
	{
		listeners_and_event_listeners.removeListener( listener );
	}


	@Override
	public void
	fireGlobalManagerEvent(
		final int 				type,
		final DownloadManager 	dm,
		Object					data )
	{
		listeners_and_event_listeners.dispatch(
			LDT_EVENT,
			new GlobalManagerEvent()
			{
				@Override
				public int
				getEventType()
				{
					return( type );
				}

				@Override
				public Object 
				getEventData()
				{
					return( data );
				}
				
				@Override
				public DownloadManager
				getDownload()
				{
					return( dm );
				}
			});
 	}

	@Override
	public void
	addDownloadWillBeRemovedListener(
		GlobalManagerDownloadWillBeRemovedListener	l )
	{
		removal_listeners.addListener( l );
	}

	@Override
	public void
	removeDownloadWillBeRemovedListener(
		GlobalManagerDownloadWillBeRemovedListener	l )
	{
		removal_listeners.removeListener( l );
	}

  // DownloadManagerListener
  @Override
  public void
  stateChanged(
	DownloadManager 	manager,
	int 				new_state )
  {
	  if ( needsSavingCozStateChanged == 0  ){

		  needsSavingCozStateChanged = SystemTime.getMonotonousTime(); //make sure we update 'downloads.config' on state changes
	  }

	  //run seeding-only-mode check

	  PEPeerManager	pm_manager = manager.getPeerManager();

	  if ( 	new_state == DownloadManager.STATE_DOWNLOADING &&
			  pm_manager != null &&
			  pm_manager.hasDownloadablePiece()){

		  //the new state is downloading, so can skip the full check

		  setSeedingOnlyState( false, false );

	  }else{

		  checkSeedingOnlyState();
	  }

	  checkForceStart( manager.isForceStart() && new_state == DownloadManager.STATE_DOWNLOADING );
  }

  protected void
  checkForceStart(
	 boolean	known_to_exist )
  {
	  boolean	exists;

	  if ( known_to_exist ){

		  exists	= true;

	  }else{

		  exists	= false;

		  if ( force_start_non_seed_exists ){

			  DownloadManager[] managers = managers_list_cow;

			  for( DownloadManager manager: managers ){

				  if ( manager.isForceStart() && manager.getState() == DownloadManager.STATE_DOWNLOADING  ){

					  exists = true;

					  break;
				  }
			  }
		  }
	  }

	  if ( exists != force_start_non_seed_exists ){

		  force_start_non_seed_exists = exists;

		  Logger.log(new LogEvent(LOGID, "Force start download " + (force_start_non_seed_exists?"exists":"doesn't exist") + ", modifying download weighting" ));

		  //System.out.println( "force_start_exists->" + force_start_non_seed_exists );

		  PeerControlSchedulerFactory.overrideWeightedPriorities( force_start_non_seed_exists  );
	  }
  }

  protected void
  checkSeedingOnlyState()
  {
	check_seeding_only_state_dispatcher.dispatch();
  }

  protected void
  checkSeedingOnlyStateSupport()
  {
	  boolean seeding 			= false;
	  boolean seeding_set 			= false;
	  boolean	potentially_seeding	= false;

	  long curr_mut = all_trackers.getOptionsMutationCount();

	  boolean full_sync = curr_mut != all_trackers_options_mut;

	  if ( full_sync ){
		  all_trackers_options_mut = curr_mut;
	  }

	  DownloadManager[] managers = managers_list_cow;

	  boolean	got_result = false;
	  
	  for( DownloadManager manager: managers ){

		  int	state = manager.getState();

		  if ( got_result ){
			  
			  if ( state == DownloadManager.STATE_QUEUED ){

				  if ( manager.isDownloadComplete( false )){

					  manager.checkLightSeeding( full_sync );
				  }
			  }
			  
			  continue;
		  }

		  PEPeerManager pm = manager.getPeerManager();

		  if ( pm == null ){

			  	// download not running

			  if ( state == DownloadManager.STATE_QUEUED ){

				  if ( manager.isDownloadComplete( false )){

					  manager.checkLightSeeding( full_sync );

					  potentially_seeding = true;
					  
				  } else {

						  // Queued and Incomplete means we aren't "only seeding".  This
						  // download WILL start, it just hasn't yet for various reasons
						  // (1st Priority, etc)
					  
					  seeding 	= false;
	
						  // can't break out, because we still need to calculate
						  // potentially_seeding.  Set a flag so we don't set "seeding"
						  // back to true
					  
					  seeding_set = true;

				  }
			  }

			  continue;
		  }

		  if ( state == DownloadManager.STATE_DOWNLOADING ){

			  if (!pm.hasDownloadablePiece()){

				  	// complete DND file

				  if (!seeding_set) {

					  seeding = true;
				  }

			  }else{

				  seeding 				= false;
				  potentially_seeding	= false;

				  got_result = true;
				  
				  //  break; want to carry on any update remaining light-seed status
			  }
		  }else if ( state == DownloadManager.STATE_SEEDING ){

			  if (!seeding_set) {

				  seeding = true;
			  }
		  }
	  }

	  if ( seeding ){

		  potentially_seeding = true;
	  }

	  setSeedingOnlyState( seeding, potentially_seeding );
  }


  protected void
  setSeedingOnlyState(
		boolean		seeding,
		boolean		potentially_seeding )
  {
	  synchronized( this ){

		  if ( 	seeding 			!= seeding_only_mode ||
	    		potentially_seeding != potentially_seeding_only_mode ){

		      seeding_only_mode 			= seeding;
		      potentially_seeding_only_mode	= potentially_seeding;

		      // System.out.println( "dispatching " + seeding_only_mode + "/" + potentially_seeding_only_mode );

		      listeners_and_event_listeners.dispatch( LDT_SEEDING_ONLY, new boolean[]{ seeding_only_mode, potentially_seeding_only_mode });
		  }
	  }
  }

  @Override
  public boolean
  isSeedingOnly()
  {
	  return( seeding_only_mode );
  }

  @Override
  public boolean
  isPotentiallySeedingOnly()
  {
	  return( potentially_seeding_only_mode );
  }

	public long
	getTotalSwarmsPeerRate(
		boolean 	downloading,
		boolean 	seeding )
	{
		long	now = SystemTime.getCurrentTime();

		if ( 	now < last_swarm_stats_calc_time ||
				now - last_swarm_stats_calc_time >= 1000 ){

			long	total = 0;

			  DownloadManager[] managers = managers_list_cow;

			  for( DownloadManager manager: managers ){

				boolean	is_seeding = manager.getState() == DownloadManager.STATE_SEEDING;

				if (	( downloading && !is_seeding ) ||
						( seeding && is_seeding )){

					total += manager.getStats().getTotalAveragePerPeer();
				}
			}

			last_swarm_stats	= total;

			last_swarm_stats_calc_time	= now;
		}

		return( last_swarm_stats );
	}

	protected void
	computeNATStatus()
	{
		int	num_ok			= 0;
		int num_probably_ok	= 0;
		int	num_bad			= 0;
		int	num_unknown		= 0;

		String[]	infos 		= { "", "", "", "" };
		int[]		extra		= { 0, 0, 0, 0  };
		
		  DownloadManager[] managers = managers_list_cow;

		  for( DownloadManager manager: managers ){

        	Object[] o_status = manager.getNATStatus();

        	int status 		= (Integer)o_status[0];
        	String	info 	= (String)o_status[1];
        	
        	int	index;
        	
        	if ( status == ConnectionManager.NAT_OK ){

        		index = 0;
        		
        		num_ok++;

        	}else if ( status == ConnectionManager.NAT_PROBABLY_OK ){

        		index = 1;
        		
        		num_probably_ok++;

        	}else if ( status == ConnectionManager.NAT_BAD ){

        		index = 2;
        		
            	num_bad++;
            	

        	}else if ( status == ConnectionManager.NAT_UNKNOWN ){

        		index = 3;
        		
        		num_unknown++;
            	
        	}else{
        		
        		continue;
        	}
        	
        	String str = infos[index];
        	        	
       		if ( str.length() < 250 ){
        			
       			String name = manager.getDisplayName();
       			
       			if ( name.length() > 23 ){
       				name = name.substring( 0,  20) + "...";
       			}
       			
        		str += (str.isEmpty()?"":"\n") + name + ": " + info;
        		
        		infos[index] = str;
        		
        	}else{
        		
        		extra[index]++;
        	}
        }
        
        for ( int i=0;i<infos.length;i++){
        	int e = extra[i];
        	
        	if ( e > 0 ){
        		infos[i] += "\n(" + e + " more)";
        	}
        }

        long now = SystemTime.getMonotonousTime();

        nat_info_prefix = null;
        
        if ( num_ok > 0 ){

        	nat_status = ConnectionManager.NAT_OK;

        	nat_status_last_good = now;

        	nat_info = infos[0];
        	
        }else if ( nat_status_last_good != -1 && now - nat_status_last_good < 30*60*1000 ){

        	nat_status = ConnectionManager.NAT_OK;

        	nat_info_prefix = "Has been good within the last hour: ";
        	
        }else if ( nat_status_last_good != -1 && SystemTime.getCurrentTime() - TCPNetworkManager.getSingleton().getLastIncomingNonLocalConnectionTime() < 30*60*1000 ){

        	nat_status = ConnectionManager.NAT_OK;
        	
        	nat_info_prefix = "Last incoming connection received less than an hour ago: ";

        }else if ( num_probably_ok > 0 || nat_status_probably_ok ){

        	nat_status 				= ConnectionManager.NAT_PROBABLY_OK;

        	nat_status_probably_ok	= true;

        	if ( num_probably_ok > 0 ){
        		
        		nat_info = infos[1];
        	}
        }else if ( num_bad > 0 ){

        	nat_status = ConnectionManager.NAT_BAD;

        	nat_info = infos[2];
        	
        }else{

        	nat_status = ConnectionManager.NAT_UNKNOWN;
        	
        	nat_info = infos[3];
        }
	}

	@Override
	public Object[]
	getNATStatus()
	{
		String info 	= nat_info;
		String prefix	= nat_info_prefix;
		
		if ( prefix != null ){
			
			info = prefix + info;
		}
		
		return( new Object[]{ nat_status, info });
	}

	protected void
	seedPieceRecheck()
	{
		DownloadManager[] managers = managers_list_cow;

		if ( next_seed_piece_recheck_index >= managers.length){

			next_seed_piece_recheck_index	= 0;
		}

		for (int i=next_seed_piece_recheck_index;i<managers.length;i++){

			DownloadManager manager = managers[i];

			if ( seedPieceRecheck( manager )){

				next_seed_piece_recheck_index = i+1;

				if ( next_seed_piece_recheck_index >= managers.length){

					next_seed_piece_recheck_index	= 0;
				}

				return;
			}
		}

		for (int i=0;i<next_seed_piece_recheck_index;i++){

			DownloadManager manager = managers[i];

			if ( seedPieceRecheck( manager )){

				next_seed_piece_recheck_index = i+1;

				if ( next_seed_piece_recheck_index >= managers.length ){

					next_seed_piece_recheck_index	= 0;
				}

				return;
			}
		}
	}

	protected boolean
	seedPieceRecheck(
		DownloadManager	manager )
	{
		if ( manager.getState() != DownloadManager.STATE_SEEDING ){

			return( false );
		}

		return( manager.seedPieceRecheck());
	}

	protected DownloadManagerInitialisationAdapter
	getDMAdapter(
		DownloadManagerInitialisationAdapter	adapter )
	{
		List<DownloadManagerInitialisationAdapter>	adapters = dm_adapters.getList();

		adapters = new ArrayList( adapters );

		if ( adapter != null ){

			adapters.add( adapter );
		}

		List<DownloadManagerInitialisationAdapter>	tag_assigners 	= new ArrayList<>();
		List<DownloadManagerInitialisationAdapter>	tag_processors 	= new ArrayList<>();

		for ( DownloadManagerInitialisationAdapter a: adapters ){

			int	actions = a.getActions();

			if ((actions & DownloadManagerInitialisationAdapter.ACT_ASSIGNS_TAGS) != 0 ){

				tag_assigners.add( a );
			}
			if ((actions & DownloadManagerInitialisationAdapter.ACT_PROCESSES_TAGS) != 0 ){

				tag_processors.add( a );
			}
		}

		if ( tag_assigners.size() > 0 && tag_processors.size() > 0 ){

			for ( DownloadManagerInitialisationAdapter a: tag_processors ){

				adapters.remove( a );
			}

			int	pos = adapters.indexOf( tag_assigners.get( tag_assigners.size()-1));

			for ( DownloadManagerInitialisationAdapter a: tag_processors ){

				adapters.add( ++pos, a );
			}
		}

		final List<DownloadManagerInitialisationAdapter>	f_adapters = adapters;

			// wrap the existing 'static' adapters, plus a possible dynamic one, with a controlling one that
			// is responsible for (amongst other things) implementing the 'incomplete suffix' logic

		return( new DownloadManagerInitialisationAdapter()
				{
					@Override
					public int
					getActions()
					{
						return( ACT_NONE );		// not relevant this is
					}

					@Override
					public void
					initialised(
						DownloadManager		manager,
						boolean				for_seeding )
					{
						for (int i=0;i<f_adapters.size();i++){

							try{
								f_adapters.get(i).initialised( manager, for_seeding );

							}catch( Throwable e ){

								Debug.printStackTrace(e);
							}
						}

						if (Constants.isOSX) {
							fixLongFileName(manager);
						}

						if ( COConfigurationManager.getBooleanParameter( "Rename Incomplete Files")){

							String	ext = COConfigurationManager.getStringParameter( "Rename Incomplete Files Extension" ).trim();

							boolean	use_prefix = COConfigurationManager.getBooleanParameter( "Use Incomplete File Prefix" );

							DownloadManagerState state = manager.getDownloadState();

							String existing_ext = state.getAttribute( DownloadManagerState.AT_INCOMP_FILE_SUFFIX );

							if ( ext.length() > 0 && existing_ext == null ){

								DiskManagerFileInfo[] fileInfos = manager.getDiskManagerFileInfo();

								if ( fileInfos.length <= DownloadManagerStateFactory.MAX_FILES_FOR_INCOMPLETE_AND_DND_LINKAGE ){

									ext = FileUtil.convertOSSpecificChars( ext, false );

									String	prefix = "";

									if ( use_prefix ){

										try{
											prefix = Base32.encode( manager.getTorrent().getHash()).substring( 0, 12 ).toLowerCase( Locale.US ) + "_";

										}catch( Throwable e ){

										}
									}

									try{
										state.suppressStateSave(true);

										List<Integer>	from_indexes 	= new ArrayList<>();
										List<File>		from_links 		= new ArrayList<>();
										List<File>		to_links 		= new ArrayList<>();

										for ( int i=0; i<fileInfos.length; i++ ){

											DiskManagerFileInfo fileInfo = fileInfos[i];

											File base_file = fileInfo.getFile( false );

											File existing_link = state.getFileLink( i, base_file );

											if ( existing_link == null && base_file.exists()){

													// file already exists, do nothing as probably adding for seeding

											}else if ( existing_link == null || !existing_link.exists()){

												File	new_link;

												if ( existing_link == null ){

													new_link = FileUtil.newFile( base_file.getParentFile(), prefix + base_file.getName() + ext );

												}else{

													String link_name = existing_link.getName();

													if ( !link_name.startsWith( prefix )){

														link_name = prefix + link_name;
													}

													new_link = FileUtil.newFile( existing_link.getParentFile(), link_name + ext );
												}

												from_indexes.add( i );

												from_links.add( base_file );

												to_links.add( new_link );
											}
										}

										if ( from_links.size() > 0 ){

											state.setFileLinks( from_indexes, from_links, to_links );
										}
									}finally{

										state.setAttribute( DownloadManagerState.AT_INCOMP_FILE_SUFFIX, ext );

										if ( use_prefix ){

											state.setAttribute( DownloadManagerState.AT_DND_PREFIX, prefix );
										}

										state.suppressStateSave(false);
									}
								}
							}
						}
					}
				});
	}

	void fixLongFileName(DownloadManager manager) {
		// "File name too long" test
		// Note: This only addresses the case where the filename is too
		//       long, not the parent directory
		DiskManagerFileInfo[] fileInfos = manager.getDiskManagerFileInfo();

		DownloadManagerState state = manager.getDownloadState();

		try {
			state.suppressStateSave(true);

			for (int i = 0; i < fileInfos.length; i++) {

				DiskManagerFileInfo fileInfo = fileInfos[i];

				File base_file = fileInfo.getFile(false);

				File existing_link = state.getFileLink( i, base_file);

				if (existing_link == null && !base_file.exists()) {

					String name = base_file.getName();
					String ext = FileUtil.getExtension(name);
					int extLength = ext.length();
					name = name.substring(0, name.length() - extLength);

					// Java appears to be pretending
					// each unicode character is 3 bytes long.  If the limit
					// on a name is 256 two byte characters, then in theory,
					// the limit is 170 unicode characters.  Instead of assuming
					// that's the case, let's just walk back until the name
					// is accepted.
					// Bail at 50 just for the fun of it (plus most filenames
					// are short, so we can skip the Canonical call)
					int origLength = name.length();
					if (origLength > 50) {
						File parentFile = base_file.getParentFile();

						// We don't get "File name too long" on getCanonicalPath
						// unless the dir is there
						// I Wonder if we should remove the dirs after using them
						// FMFileImpl will create dirs again
						parentFile.mkdirs();

						File newFile = null;
						boolean first = true;
						while (name.length() > 50) {
							try {
								newFile = FileUtil.newFile(parentFile, name + ext);
								newFile.getCanonicalPath();

								if (first) {
									break;
								}

								// it worked, but the new name might already exist
								int fixNameID = 0xFF; // always 3 digits :)
								boolean redo;
								do {
									redo = false;
									for (int j = 0; j < i; j++) {
										DiskManagerFileInfo convertedFileInfo = fileInfos[j];
										if (newFile.equals(convertedFileInfo.getFile(true))) {
											do {
												fixNameID++;
												if (fixNameID >= 0xFFF) {
													// exit, will fail later :(
													break;
												}
												name = name.substring(0, name.length() - 3)
														+ Integer.toHexString(fixNameID);
												newFile = FileUtil.newFile(parentFile, name + ext);
											} while (newFile.equals(convertedFileInfo.getFile(true)));
											redo = fixNameID <= 0xFFF;
											break;
										}
									}
								} while (redo);

								if (fixNameID <= 0xFFF) {
									state.setFileLink(i,base_file, newFile);
								}
								break;
							} catch (IOException e) {
								first = false;
								name = name.substring(0, name.length() - 1);
							} catch (Throwable t) {
								Debug.out(t);
							}
						}
					}

				}
			}
		} finally {

			state.suppressStateSave(false);
		}
	}

	@Override
	public void
	addDownloadManagerInitialisationAdapter(
		DownloadManagerInitialisationAdapter	adapter )
	{
		dm_adapters.add( adapter );
	}

	@Override
	public void
	removeDownloadManagerInitialisationAdapter(
		DownloadManagerInitialisationAdapter	adapter )
	{
		dm_adapters.remove( adapter );
	}

	@Override
	public Object		// DownloadHistoryManager
	getDownloadHistoryManager()
	{
		return( download_history_manager );
	}

	@Override
	public void
	generate(
		IndentWriter		writer )
	{
		writer.println( "Global Manager" );

		try{
			writer.indent();

	    	synchronized( managers_lock ){

				writer.println( "  managers: " + managers_list_cow.length );
	
				for ( DownloadManager manager: managers_list_cow ){
	
					try{
						writer.indent();
	
						manager.generateEvidence( writer );
	
					}finally{
	
						writer.exdent();
					}
				}
	    	}
	    }finally{

			writer.exdent();
	    }
	}

	public static void
	main(
		String[]	args )
	{
		if ( args.length == 0 ){
			args = new String[]{
					"C:\\temp\\downloads.config",
					"C:\\temp\\downloads-9-3-05.config",
					"C:\\temp\\merged.config" };

		}else if ( args.length != 3 ){

			System.out.println( "Usage: newer_config_file older_config_file save_config_file" );

			return;
		}

		try{
			Map	map1 = FileUtil.readResilientFile( FileUtil.newFile(args[0]));
			Map	map2 = FileUtil.readResilientFile( FileUtil.newFile(args[1]));

			List	downloads1 = (List)map1.get( "downloads" );
			List	downloads2 = (List)map2.get( "downloads" );

			Set	torrents = new HashSet();

			Iterator	it1 = downloads1.iterator();

			while( it1.hasNext()){

				Map	m = (Map)it1.next();

				byte[]	hash = (byte[])m.get( "torrent_hash" );

				System.out.println( "1:" + ByteFormatter.nicePrint(hash));

				torrents.add( new HashWrapper( hash ));
			}

			List	to_add = new ArrayList();

			Iterator	it2 = downloads2.iterator();

			while( it2.hasNext()){

				Map	m = (Map)it2.next();

				byte[]	hash = (byte[])m.get( "torrent_hash" );

				HashWrapper	wrapper = new HashWrapper( hash );

				if ( torrents.contains( wrapper )){

					System.out.println( "-:" + ByteFormatter.nicePrint(hash));

				}else{

					System.out.println( "2:" + ByteFormatter.nicePrint(hash));

					to_add.add( m );
				}
			}

			downloads1.addAll( to_add );

			System.out.println( to_add.size() + " copied from " + args[1] + " to " + args[2]);

			FileUtil.writeResilientFile( FileUtil.newFile( args[2]), map1 );

		}catch( Throwable e ){

			e.printStackTrace();
		}
	}

	@Override
	public void setMainlineDHTProvider(MainlineDHTProvider provider) {
		this.provider = provider;
	}

	@Override
	public MainlineDHTProvider getMainlineDHTProvider() {
		return this.provider;
	}

	@Override
	public void
	statsRequest(
		Map		request,
		Map		reply )
	{
		Core core = CoreFactory.getSingleton();

		Map	glob = new HashMap();

		reply.put( "gm", glob );

		try{
			glob.put( "u_rate", new Long( stats.getDataAndProtocolSendRate()));
			glob.put( "d_rate", new Long( stats.getDataAndProtocolReceiveRate()));

			glob.put( "d_lim", new Long( TransferSpeedValidator.getGlobalDownloadRateLimitBytesPerSecond()));

			boolean auto_up = TransferSpeedValidator.isAutoSpeedActive(this) && TransferSpeedValidator.isAutoUploadAvailable( core );

			glob.put( "auto_up", new Long(auto_up?COConfigurationManager.getLongParameter( SpeedManagerImpl.CONFIG_VERSION ):0));

			long up_lim = NetworkManager.getMaxUploadRateBPSNormal();

			boolean	seeding_only = NetworkManager.isSeedingOnlyUploadRate();

			glob.put( "so", new Long(seeding_only?1:0));

			if ( seeding_only ){

				up_lim = NetworkManager.getMaxUploadRateBPSSeedingOnly();
			}

			glob.put( "u_lim", new Long( up_lim ));

			SpeedManager sm = core.getSpeedManager();

			if ( sm != null ){

				glob.put( "u_cap", new Long( sm.getEstimatedUploadCapacityBytesPerSec().getBytesPerSec()));
				glob.put( "d_cap", new Long( sm.getEstimatedDownloadCapacityBytesPerSec().getBytesPerSec()));
			}

			List<DownloadManager> dms = getDownloadManagers();

			int	comp 	= 0;
			int	incomp	= 0;

			long comp_up 		= 0;
			long incomp_up		= 0;
			long incomp_down	= 0;

			for ( DownloadManager dm: dms ){

				int state = dm.getState();

				if ( state == DownloadManager.STATE_SEEDING || state == DownloadManager.STATE_DOWNLOADING ){

					DownloadManagerStats stats = dm.getStats();

					if ( dm.isDownloadComplete( false )){

						comp++;

						comp_up += stats.getProtocolSendRate() + stats.getDataSendRate();

					}else{

						incomp++;

						incomp_up 	+= stats.getProtocolSendRate() + stats.getDataSendRate();
						incomp_down += stats.getProtocolReceiveRate() + stats.getDataReceiveRate();
					}
				}
			}

			glob.put( "dm_i", new Long( incomp ));
			glob.put( "dm_c", new Long( comp ));

			glob.put( "dm_i_u", new Long( incomp_up ));
			glob.put( "dm_i_d", new Long( incomp_down ));
			glob.put( "dm_c_u", new Long( comp_up ));

			glob.put( "nat", new Long( nat_status ));

			boolean	request_limiting = COConfigurationManager.getBooleanParameter( "Use Request Limiting" );

			glob.put( "req_lim", new Long( request_limiting?1:0 ));

			if ( request_limiting ){

				glob.put( "req_focus", new Long( COConfigurationManager.getBooleanParameter( "Use Request Limiting Priorities" )?1:0 ));
			}

			boolean bias_up = COConfigurationManager.getBooleanParameter( "Bias Upload Enable" );

			glob.put( "bias_up", new Long( bias_up?1:0 ));

			if ( bias_up ){

				glob.put( "bias_slack", new Long( COConfigurationManager.getLongParameter( "Bias Upload Slack KBs" )));

				glob.put( "bias_ulim", new Long( COConfigurationManager.getBooleanParameter( "Bias Upload Handle No Limit" )?1:0 ));
			}
		}catch( Throwable e ){
		}
	}

	private class
	DownloadStateTagger
		extends TagTypeWithState
		implements DownloadManagerListener
	{
		private final int[] color_default = { 41, 140, 165 };

		private final Object	main_tag_key 	= new Object();
		private final Object	comp_tag_key	= new Object();

			// exclusive tags

		private final TagDownloadWithState	tag_initialising;
		private final TagDownloadWithState	tag_downloading;
		private final TagDownloadWithState	tag_seeding;
		private final TagDownloadWithState	tag_queued_downloading;
		private final TagDownloadWithState	tag_queued_seeding;
		private final TagDownloadWithState	tag_stopped;
		private final TagDownloadWithState	tag_error;

			// non-exclusive/derived tags

		private final TagDownloadWithState	tag_active;
		private final TagDownloadWithState	tag_inactive;
		private final TagDownloadWithState	tag_complete;
		private final TagDownloadWithState	tag_incomplete;
		private final TagDownloadWithState	tag_moving;
		private final TagDownloadWithState	tag_checking;
		private final TagDownloadWithState	tag_deleting;
		private final TagDownloadWithState	tag_paused;
		
			// extends the above and you need to extend derived_tags below...

		private final TagDownloadWithState[]	derived_tags;
		
		int user_mode = -1;

		{
			COConfigurationManager.addAndFireParameterListener(
				"User Mode",
				new ParameterListener()
				{
					@Override
					public void
					parameterChanged(
						String parameterName )
					{
						int	old_mode = user_mode;

						user_mode = COConfigurationManager.getIntParameter("User Mode");

						if ( old_mode != -1 ){

							fireChanged();
						}
					}
				});
		}

		DownloadStateTagger(Core core)
		{
			super( TagType.TT_DOWNLOAD_STATE, TagDownload.FEATURES & ~TagFeature.TF_NOTIFICATIONS, "tag.type.ds" );

			addTagType();

				// keep these ids constant as they are externalised

			tag_initialising		= new MyTag( 0, "tag.type.ds.init", false, false, false, false, TagFeatureRunState.RSC_NONE );
			tag_downloading			= new MyTag( 1, "tag.type.ds.down", true, true, true, true, TagFeatureRunState.RSC_STOP_PAUSE );
			tag_seeding				= new MyTag( 2, "tag.type.ds.seed", true, true, false, true, TagFeatureRunState.RSC_STOP_PAUSE );
			tag_queued_downloading	= new MyTag( 3, "tag.type.ds.qford", false, false, false, false, TagFeatureRunState.RSC_STOP_PAUSE );
			tag_queued_seeding		= new MyTag( 4, "tag.type.ds.qfors", false, false, false, false, TagFeatureRunState.RSC_STOP_PAUSE );
			tag_stopped				= new MyTag( 5, "tag.type.ds.stop", false, false, false, false, TagFeatureRunState.RSC_START );
			tag_error				= new MyTag( 6, "tag.type.ds.err", false, false, false, false, TagFeatureRunState.RSC_NONE  );
			tag_active				= new MyTag( 7, "tag.type.ds.act", true, false, false, false, TagFeatureRunState.RSC_STOP_PAUSE );
			tag_paused				= new MyTag( 8, "tag.type.ds.pau", false, false, false, false, TagFeatureRunState.RSC_RESUME );
			tag_inactive			= new MyTag( 9, "tag.type.ds.inact", false, false, false, false, TagFeatureRunState.RSC_START_STOP_PAUSE );
			tag_complete			= new MyTag( 10, "tag.type.ds.comp", true, true, false, true, TagFeatureRunState.RSC_START_STOP_PAUSE );
			tag_incomplete			= new MyTag( 11, "tag.type.ds.incomp", true, true, true, true, TagFeatureRunState.RSC_START_STOP_PAUSE );
			tag_moving				= new MyTag( 12, "tag.type.ds.mov", false, false, false, false, TagFeatureRunState.RSC_STOP_PAUSE );
			tag_checking			= new MyTag( 13, "tag.type.ds.chk", false, false, false, false, TagFeatureRunState.RSC_STOP_PAUSE );
			tag_deleting			= new MyTag( 14, "tag.type.ds.del", false, false, false, false, TagFeatureRunState.RSC_NONE );

			derived_tags = new TagDownloadWithState[]{
					tag_active,
					tag_inactive,
					tag_complete,
					tag_incomplete,
					tag_moving,
					tag_checking,
					tag_deleting,
					tag_paused,
			};
			
			if ( tag_active.isColorDefault()){
				tag_active.setColor( new int[]{ 96, 160, 96 });
			}

			if ( tag_error.isColorDefault()){
				tag_error.setColor( new int[]{ 132, 16, 58 });
			}
			
			core.addOperationListener(
				new CoreOperationListener(){

					@Override
					public void 
					operationAdded(
						CoreOperation operation )
					{
						process( operation, true );
					}

					@Override
					public void 
					operationRemoved(
						CoreOperation operation )
					{
						process( operation, false );
					}

					private void
					process(
						CoreOperation		operation,
						boolean				added )
					{
						int type = operation.getOperationType();

						if ( type == CoreOperation.OP_DOWNLOAD_CHECKING || type == CoreOperation.OP_FILE_MOVE ){

							CoreOperationTask task = operation.getTask();

							DownloadManager dm = task.getDownload();

							if ( dm == null ){

								return;
							}

								// too lazy to ref count these in case > 1 active at same time...
							
							if ( type == CoreOperation.OP_DOWNLOAD_CHECKING ){

								if ( added ){

									tag_checking.addTaggable( dm );
									
								}else{
									
									tag_checking.removeTaggable( dm );
								}
							}else{
								
								if ( added ){

									tag_moving.addTaggable( dm );
									
								}else{
									
									tag_moving.removeTaggable( dm );
								}
							}
						}
					}
					
					@Override
					public boolean 
					operationExecuteRequest(
							CoreOperation operation )
					{
						return false;
					}
				});
		}
		
		private void
		initialise()
		{
			addListener(
				new GlobalManagerAdapter()
				{
					@Override
					public void
					downloadManagerAdded(
						DownloadManager	dm )
					{
						dm.addListener( DownloadStateTagger.this, true );
					}

					@Override
					public void
					downloadManagerRemoved(
						DownloadManager	dm )
					{
						dm.removeListener( DownloadStateTagger.this );

						remove( dm );
					}
				});

			SimpleTimer.addPeriodicEvent(
				"gm:ds",
				10*1000,
				new TimerEventPerformer()
				{
					@Override
					public void
					perform(
						TimerEvent event )
					{
						updateActive();
					}
				});
		}

		@Override
		public void
		stateChanged(
			DownloadManager 	manager,
			int					state )
		{
			if ( manager.isDestroyed()){

				remove( manager );

				return;
			}

			Tag	new_tag;

			Tag old_tag = (Tag)manager.getUserData( main_tag_key );

			boolean complete = manager.isDownloadComplete( false );

			switch( state ){
				case DownloadManager.STATE_WAITING:
				case DownloadManager.STATE_INITIALIZING:
				case DownloadManager.STATE_INITIALIZED:
				case DownloadManager.STATE_ALLOCATING:
				case DownloadManager.STATE_CHECKING:
				case DownloadManager.STATE_READY:
					if ( old_tag == null ){
						new_tag = tag_initialising;
					}else{
						new_tag = old_tag;
					}
					break;
				case DownloadManager.STATE_FINISHING:
					new_tag = old_tag;	// get here for incomplete + complete downloads - transient state so ignore
					break;
				case DownloadManager.STATE_DOWNLOADING:
					new_tag = tag_downloading;
					break;
				case DownloadManager.STATE_SEEDING:
					new_tag = tag_seeding;
					break;
				case DownloadManager.STATE_STOPPING:
				case DownloadManager.STATE_STOPPED:
				case DownloadManager.STATE_CLOSED:
					new_tag = tag_stopped;
					break;
				case DownloadManager.STATE_QUEUED:
					if ( complete ){
						new_tag = tag_queued_seeding;
					}else{
						new_tag = tag_queued_downloading;
					}
					break;
				case DownloadManager.STATE_ERROR:
				default:
					new_tag = tag_error;
					break;
			}

			if ( old_tag != new_tag ){

				if ( old_tag != null ){

					old_tag.removeTaggable( manager );
				}

				new_tag.addTaggable( manager );

				manager.setUserData( main_tag_key, new_tag );

				synchronized( this ){

					boolean	was_inactive = tag_inactive.hasTaggable( manager );

					if ( new_tag != tag_seeding && new_tag != tag_downloading ){

						tag_active.removeTaggable( manager );

						if ( !was_inactive ){

							tag_inactive.addTaggable( manager );
						}
					}else{

						boolean	was_active = tag_active.hasTaggable( manager );

						if ( !( was_active || was_inactive )){

							tag_inactive.addTaggable( manager );
						}
					}
				}

				if ( new_tag == tag_stopped && manager.isPaused()){

					tag_paused.addTaggable( manager );

				}else if ( old_tag == tag_stopped ){

					tag_paused.removeTaggable( manager );
				}
			}else if ( state == DownloadManager.STATE_STOPPED ){
				
				boolean paused = manager.isPaused();
				
				if ( paused && !tag_paused.hasTaggable( manager )){
					
					tag_paused.addTaggable( manager );
					
				}else if ( !paused && tag_paused.hasTaggable( manager )){
					
					tag_paused.removeTaggable( manager );
				}
			}

			Boolean was_complete = (Boolean)manager.getUserData( comp_tag_key );

			if ( was_complete == null || was_complete != complete ){

				synchronized( this ){

					if ( complete ){

						if ( !tag_complete.hasTaggable( manager )){

							tag_complete.addTaggable( manager );

							tag_incomplete.removeTaggable( manager );
						}
					}else{

						if ( !tag_incomplete.hasTaggable( manager )){

							tag_incomplete.addTaggable( manager );

							tag_complete.removeTaggable( manager );
						}
					}

					manager.setUserData(  comp_tag_key, complete );
				}
			}
		}

		void
		updateActive()
		{
			synchronized( this ){

				Set<DownloadManager> active 	= new HashSet<>(tag_active.getTaggedDownloads());

				for ( TagDownloadWithState tag: new TagDownloadWithState[]{ tag_downloading, tag_seeding }){

					for ( DownloadManager dm: tag.getTaggedDownloads()){

						DownloadManagerStats stats = dm.getStats();

						boolean is_active =
							stats.getDataReceiveRate() + stats.getDataSendRate() > 0 &&
							!dm.isDestroyed();

						if ( is_active ){

							if ( !active.remove( dm )){

								tag_active.addTaggable( dm );

								tag_inactive.removeTaggable( dm );
							}

							dm.getDownloadState().setLongAttribute( DownloadManagerState.AT_LAST_ADDED_TO_ACTIVE_TAG, SystemTime.getCurrentTime());
						}
					}
				}

				for ( DownloadManager dm: active ){

					tag_active.removeTaggable( dm );

					if ( !dm.isDestroyed()){

						tag_inactive.addTaggable( dm );
					}
				}
			}
		}

		void
		removeInitiated(
			DownloadManager		manager )
		{
			tag_deleting.addTaggable( manager );
		}
		
		void
		remove(
			DownloadManager		manager )
		{
			Tag old_tag = (Tag)manager.getUserData( main_tag_key );

			if ( old_tag != null ){

				old_tag.removeTaggable( manager );
			}

			synchronized( this ){

				for ( TagDownloadWithState tag: derived_tags ){
					
					if ( tag.hasTaggable( manager )){

						tag.removeTaggable( manager );
					}
				}
			}
		}

		@Override
		public void
		downloadComplete(DownloadManager manager)
		{
		}

		@Override
		public void
		completionChanged(DownloadManager manager, boolean bCompleted)
		{
			stateChanged( manager, manager.getState());
		}

		@Override
		public void
		positionChanged(DownloadManager download, int oldPosition, int newPosition)
		{
		}

		@Override
		public void
		filePriorityChanged( DownloadManager download, DiskManagerFileInfo file )
		{
		}

		@Override
		public int[]
	    getColorDefault()
		{
			return( color_default );
		}

		private class
		MyTag
			extends TagDownloadWithState
		{
			MyTag(
				int				tag_id,
				String			name,
				boolean			do_rates,
				boolean			do_up,
				boolean			do_down,
				boolean			do_bytes,
				int				run_states )
			{
				super( DownloadStateTagger.this, tag_id, name, do_rates, do_up, do_down, do_bytes, run_states );

				addTag();
			}

			@Override
			protected boolean
			getVisibleDefault()
			{
				int	id = getTagID();

				if ( id >= 7 && id <= 9 ){	// active, paused, inactive

					return( user_mode > 0 );

				}else{

					return( id == 7 );	// active
				}
			}

			@Override
			protected boolean
			getCanBePublicDefault()
			{
				return( false );
			}

			@Override
			public void
			removeTag()
			{
				throw( new RuntimeException( "Not Supported" ));
			}
		}
	}
}
