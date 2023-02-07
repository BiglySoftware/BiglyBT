/* 
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.core.tracker.alltrackers;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreLifecycleAdapter;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentAnnounceURLSet;
import com.biglybt.core.torrent.TOTorrentListener;
import com.biglybt.core.tracker.AllTrackersManager.AllTrackers;
import com.biglybt.core.tracker.AllTrackersManager.AllTrackersEvent;
import com.biglybt.core.tracker.AllTrackersManager.AllTrackersListener;
import com.biglybt.core.tracker.AllTrackersManager.AllTrackersTracker;
import com.biglybt.core.tracker.AllTrackersManager.AnnounceStats;
import com.biglybt.core.tracker.AllTrackersManager.AnnounceStatsProvider;
import com.biglybt.core.tracker.AllTrackersManager.ScrapeStats;
import com.biglybt.core.tracker.AllTrackersManager.ScrapeStatsProvider;
import com.biglybt.core.tracker.client.TRTrackerAnnouncerRequest;
import com.biglybt.core.tracker.client.TRTrackerAnnouncerResponse;
import com.biglybt.core.tracker.client.TRTrackerScraperResponse;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Average;
import com.biglybt.core.util.BDecoder;
import com.biglybt.core.util.CopyOnWriteList;
import com.biglybt.core.util.DNSUtils;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.FileUtil;
import com.biglybt.core.util.FrequencyLimitedDispatcher;
import com.biglybt.core.util.HashWrapper;
import com.biglybt.core.util.SimpleTimer;
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.TimerEvent;
import com.biglybt.core.util.TimerEventPerformer;
import com.biglybt.core.util.average.AverageFactory;
import com.biglybt.core.util.average.MovingImmediateAverage;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.logging.LoggerChannel;
import com.biglybt.util.MapUtils;

public class 
AllTrackersManagerImpl
	implements AllTrackers, TOTorrentListener
{
	final static int	MAX_TRACKERS	= 1024;
	
	final static int 	TICK_PERIOD	= 2500;
	
	final static int	SAVE_PERIOD	= 5*60*1000;
	final static int	SAVE_TICKS	= SAVE_PERIOD/TICK_PERIOD;
	
	final static int	LAG_CHECK_PERIOD	= 60*1000;
	final static int	LAG_CHECK_TICKS		= LAG_CHECK_PERIOD/TICK_PERIOD;

	
	private static final String	CONFIG_FILE 				= "alltrackers.config";

	private static final String CONFIG_PRIVATE_ACTIVE_AT_CLOSE = "alltrackers.close.private.active";
	
	final private static AllTrackersManagerImpl singleton = new AllTrackersManagerImpl();
	
	public static AllTrackers
	getSingleton()
	{
		return( singleton );
	}

	private final long start_time = SystemTime.getMonotonousTime();
			
	private final Core	core = CoreFactory.getSingleton();
	
	private volatile boolean started;
	
	private volatile boolean stopping;
	
	private Map<String,AllTrackersTrackerImpl>		host_map = new ConcurrentHashMap<>();
	
	private ConcurrentLinkedDeque<Object[]>			update_queue = new ConcurrentLinkedDeque<>();
	
	private CopyOnWriteList<AllTrackersListener>	listeners = new CopyOnWriteList<>();
	
	private Map<TRTrackerAnnouncerRequest,String>	active_requests = new ConcurrentHashMap<>();
			
	private boolean	got_running;
	
	private final Object process_lock = new Object();
	
	private List<TOTorrent>	pending_torrents = new ArrayList<>();

	private Map<String, LoggerChannel>	logging_keys = new HashMap<>();
	
	private Map<HashWrapper,String>		dm_name_cache = new HashMap<>();
	
	private AtomicLong	options_mutation_count = new AtomicLong();
	
	private
	AllTrackersManagerImpl()
	{
		loadConfig();
		
		updateLogging();
	
		try{
			List<String> actives = BDecoder.decodeStrings( COConfigurationManager.getListParameter( CONFIG_PRIVATE_ACTIVE_AT_CLOSE, new ArrayList<String>()));
									
			if ( !actives.isEmpty()){
				
				String trackers = "";
				
				for ( String t: actives ){
					
					trackers += ( trackers.isEmpty()?"":", ") + t;
				}
				
				String text = MessageText.getString( "alltorrents.updates.outstanding", new String[]{ trackers });
				
				Logger.log(new LogAlert(LogAlert.UNREPEATABLE, LogAlert.AT_WARNING, text, 0 ));
			}
		}catch( Throwable e ){
			
			Debug.out( e );
			
		}finally{
			
			COConfigurationManager.removeParameter( CONFIG_PRIVATE_ACTIVE_AT_CLOSE );
		}
		
		core.addLifecycleListener(
				new CoreLifecycleAdapter()
				{
					@Override
					public void 
					started(Core core)
					{
						started	= true;
						
						recalcTotals();
					}
					
					@Override
					public void 
					stopping(
						Core core )
					{
						stopping	= true;
						
						synchronized( process_lock ){
							
							if ( !logging_keys.isEmpty()){
								
									// gotta cache these for logging purposes as by the time we might need them later
									// the downloads will have been unloaded...
								
								for ( DownloadManager dm: core.getGlobalManager().getDownloadManagers()){
									
									try{
										dm_name_cache.put( dm.getTorrent().getHashWrapper(), dm.getDisplayName());
										
									}catch( Throwable e ){
										
									}
								}
							}
						}
					}
					
					@Override
					public void
					stopped(
						Core core )
					{
						processUpdates( true );
						
						saveConfig( true );
					}
				});
		
		SimpleTimer.addPeriodicEvent(
			"AllTrackers",
			TICK_PERIOD,
			new TimerEventPerformer(){
					
				private int	tick_count;
										
				@Override
				public void 
				perform(
					TimerEvent event )
				{
					tick_count++;
					
					processUpdates( false );
					
					if ( tick_count % LAG_CHECK_TICKS == 0 ){
						
						checkLag();
					}
					
					if ( tick_count % SAVE_TICKS == 0 ){
						
						saveConfig( false );
					}
				}
			});
	}
	
	@Override
	public boolean
	isStopping()
	{
		return( stopping );
	}
	
	private MovingImmediateAverage	lag_average = AverageFactory.MovingImmediateAverage( 5 );
	private boolean					lag_logged;
	
	private void
	checkLag()
	{
		AnnounceStats stats = getAnnounceStats();
		
		long max_lag = Math.max( stats.getPrivateLagMillis(), stats.getPublicLagMillis());
		
		lag_average.update( max_lag );
		
		if ( !lag_logged && SystemTime.getMonotonousTime() - start_time > 10*60*1000 ){
			
			if ( lag_average.getAverage() > 120*1000 ){
				
				lag_logged = true;
				
				String text = MessageText.getString( "alltorrents.updates.lagging" );
				
				Logger.log(new LogAlert(LogAlert.UNREPEATABLE, LogAlert.AT_WARNING, text, 0 ));
			}
		}
	}
	
	private void
	processUpdates(
		boolean	for_close )
	{
		synchronized( process_lock ){
			
			if ( pending_torrents != null && CoreFactory.isCoreRunning()){
				
				for ( TOTorrent torrent: pending_torrents ){
					
					torrent.addListener( AllTrackersManagerImpl.this );
				}
				
				got_running = true;
				
				pending_torrents = null;
			}
			
			Set<AllTrackersTracker>	updates = new HashSet<>();
					
			while( !update_queue.isEmpty()){
				
				Object[] entry = update_queue.remove();
				
				try{
					Object	e0 = entry[0];
					
					if ( e0 instanceof TOTorrent ){
					
						TOTorrent torrent = (TOTorrent)e0;
						
						if ( pending_torrents == null ){
							
							torrent.addListener( AllTrackersManagerImpl.this );
							
						}else{
							
							pending_torrents.add( torrent );
						}
						
						continue;
						
					}else if ( e0 instanceof String ){
						
						String cmd = (String)e0;
						
						if ( cmd.equals( "logging_changed" )){
							
							updateLogging();
							
						}else{
							
							Debug.out( "eh?" );
						}
						
						continue;
					}
							
					AllTrackersTrackerImpl 		tracker = (AllTrackersTrackerImpl)e0;
					
					if ( host_map.containsKey( tracker.getTrackerName())){
		
						Object	obj 	= entry[1];
												
						boolean	updated = false;
						
						if ( obj instanceof String ){
							
							String cmd = (String)obj;
							
							if ( cmd.equals( "reset_stats" )){
								
								tracker.resetReportedStatsSupport();
								
								updated = true;
								
							}else{
								
								Debug.out( "eh?" );
							}
						}else if ( obj instanceof TRTrackerAnnouncerResponse ){
					
							TRTrackerAnnouncerResponse a_resp = (TRTrackerAnnouncerResponse)obj;
														
							if ( tracker.setOK( a_resp.getStatus() == TRTrackerAnnouncerResponse.ST_ONLINE )){
								
								updated = true;
							}
							
							if ( tracker.setStatusString( a_resp.getStatusString())){
								
								updated = true;
							}
		
							tracker.peersReceived( a_resp.getPeers().length);
							
							if ( updated ){
								
								tracker.log( a_resp );
							}
						}else if ( obj instanceof TRTrackerScraperResponse ){
							
								// announce status trumps scrape 
							
							if ( tracker.hasStatus()){
								
								continue;
							}
							
							TRTrackerScraperResponse s_resp = (TRTrackerScraperResponse)obj;							
																						
							if ( tracker.setOK( s_resp.getStatus() == TRTrackerScraperResponse.ST_ONLINE )){
								
								updated = true;
							}
							
							if ( tracker.setStatusString( s_resp.getStatusString() )){
								
								updated = true;		
							}
						}else if ( obj instanceof TRTrackerAnnouncerRequest ){
															
							TRTrackerAnnouncerRequest req = (TRTrackerAnnouncerRequest)obj;
																		
							tracker.updateSession( req );
							
							tracker.log( req, false );
	
							updated = true;
						}
							
						if ( updated ){
							
							updates.add( tracker );
						}
					}
				}catch( Throwable e ){
					
					Debug.out( e );
				}
			}
			
			if ( for_close ){
				
				Set<String>		active_privates = new HashSet<>();
				
				for ( TRTrackerAnnouncerRequest req: active_requests.keySet()){
					
					String key = ingestURL( req.getURL());
					
					if ( key != null ){
						
						AllTrackersTrackerImpl existing_tracker = host_map.get( key );
						
						if ( existing_tracker != null ){
							
							existing_tracker.log( req, true );
							
							if ( existing_tracker.getPrivatePercentage() > 80 ){
								
								active_privates.add( existing_tracker.getShortKey());
							}
						}
					}
				}
				
				if ( !active_privates.isEmpty()){
					
					COConfigurationManager.setParameter( CONFIG_PRIVATE_ACTIVE_AT_CLOSE, new ArrayList<String>( active_privates ));
				}
			}else{
				
				if ( !updates.isEmpty()){
					
					List<AllTrackersTracker> trackers = new ArrayList<>( updates );
					
					for ( AllTrackersListener listener: listeners ){
						
						try{
							listener.trackerEventOccurred(	new AllTrackersEventImpl( AllTrackersEvent.ET_TRACKER_UPDATED, trackers ));
							
						}catch( Throwable e ){
							
							Debug.out( e );
						}
					}
				}
			}
		}
	}
	
	private void
	loadConfig()
	{
		synchronized( process_lock ){
			
			try{
				Map map = FileUtil.readResilientConfigFile( CONFIG_FILE );
				
				List<Map>	trackers = (List<Map>)map.get( "trackers" );
				
				if ( trackers != null ){
					
					for ( Map t: trackers ){
						
						try{
							AllTrackersTrackerImpl tracker = new AllTrackersTrackerImpl( t );
							
							host_map.put( tracker.getTrackerName(), tracker );
							
							if ( host_map.size() > MAX_TRACKERS ){
								
								Debug.out( "Too many trackers - " + trackers.size());
								
								return;
							}
						}catch( Throwable e ){
							
							Debug.out( e );
						}
					}
				}
				
				List<Map>	logging = (List<Map>)map.get( "logging" );
				
				if ( logging != null ){
					
					for ( Map log: logging ){
						
						String key = MapUtils.getMapString( log, "key", null );
						
						if ( key != null ){
							
							logging_keys.put( key, getLogger( key ));
						}
					}
				}
			}catch( Throwable e ){
				
				Debug.out( e );
			}
		}
	}
	
	private void
	saveConfig(
		boolean	closing )
	{
		synchronized( process_lock ){
			
			boolean skip_unregistered = closing && got_running;
			
			try{
				Map<String,Object> map = new HashMap<>();
				
				List<Map<String,Object>>	trackers = new ArrayList<>( host_map.size() + 32 );
				
				map.put( "trackers", trackers ); 
				
				for ( AllTrackersTrackerImpl tracker: host_map.values()){
				
					if ( skip_unregistered && !tracker.isRegistered()){
						
							// retain if it has non-default options
						
						Map<String,Object> options = tracker.getOptions();
						
						boolean has_non_def = false;
						
						if ( options != null ){
							
							for ( String opt: AllTrackersTracker.OPT_ALL ){
								
								try{
									Number num = (Number)options.get( opt );
									
									if ( num != null && num.intValue() != 0 ){
										
										has_non_def = true;
										
										break;
									}
								}catch( Throwable e ){
									
									Debug.out( e );
								}
							}
						}
						
						if ( !has_non_def ){
						
							continue;
						}
					}
					
					try{
						trackers.add( tracker.exportToMap());
						
						if ( trackers.size() > MAX_TRACKERS ){
							
							Debug.out( "Too many trackers - " + trackers.size());
							
							break;
						}
						
					}catch( Throwable e ){
						
						Debug.out( e );
					}
				}
				
				if ( !logging_keys.isEmpty()){
					
					List<Map<String,String>>	logging = new ArrayList<>( logging_keys.size() + 32 );
					
					map.put( "logging", logging ); 
					
					for ( String key: logging_keys.keySet()){
						
						Map<String,String> m = new HashMap<>();
						
						logging.add( m );
						
						m.put( "key", key );
					}
				}
				
				FileUtil.writeResilientConfigFile( CONFIG_FILE, map );
				
			}catch( Throwable e ){
				
				Debug.out( e );
			}
		}
	}
	
	private void
	updateLogging()
	{
		for ( AllTrackersTrackerImpl tracker: host_map.values()){
			
			tracker.updateLogger();
		}
	}
	
	private final Average announce_rate = Average.getInstance( 1000, 20 );  //update every 3s, average over 60s
	private final Average scrape_rate 	= Average.getInstance( 1000, 20 );  //update every 3s, average over 60s

	private AnnounceStats	announce_stats = new AnnounceStats(){
		
		@Override
		public int getPublicScheduledCount(){
			return 0;
		}
		
		@Override
		public long getPublicLagMillis(){
			return 0;
		}
		
		@Override
		public int getPrivateScheduledCount(){
			return 0;
		}
		
		@Override
		public long getPrivateLagMillis(){
			return 0;
		}
		
		@Override
		public int getPrivatePendingCount(){
			return 0;
		}
		
		@Override
		public int getPublicPendingCount(){
			return 0;
		}
	};

	private volatile ScrapeStats	scrape_stats = new ScrapeStats(){
		
		@Override
		public long getLagMillis(){
			return 0;
		}
	};
	
	private AnnounceStatsProvider	announce_provider = new AnnounceStatsProvider(){
		
		@Override
		public AnnounceStats getStats(){
			return( announce_stats );
		}
	};
	
	private ScrapeStatsProvider	scrape_provider = new ScrapeStatsProvider(){
		
		@Override
		public ScrapeStats getStats(){
			return( scrape_stats );
		}
	};
	
	@Override
	public void registerAnnounceStatsProvider(AnnounceStatsProvider provider){
		announce_provider = provider;
	}
	
	@Override
	public void registerScrapeStatsProvider(ScrapeStatsProvider provider){
		scrape_provider = provider;
	}
	
	@Override
	public void
	addActiveRequest(
		TRTrackerAnnouncerRequest	request )
	{
		active_requests.put( request, "" );
	}
		
	@Override
	public void
	removeActiveRequest(
		TRTrackerAnnouncerRequest	request )
	{
		active_requests.remove( request );
		
		announce_rate.addValue( 100 );
	}
	
	@Override
	public void
	addScrapeRequest()
	{
	}
	
	@Override
	public void
	removeScrapeRequest()
	{
		scrape_rate.addValue( 100 );
	}
		
	@Override
	public int 
	getActiveRequestCount()
	{
		return( active_requests.size());
	}
	
	@Override
	public float 
	getAnnouncesPerSecond()
	{
		if ( stopping && active_requests.size() == 0 ){
		
			AnnounceStats stats = announce_provider.getStats();
			
			if ( stats.getPrivateScheduledCount() + stats.getPublicScheduledCount() == 0 ){
				
				return( 0 );
			}
		}
		
		return( (float)announce_rate.getAverage()/100 );
	}
	
	@Override
	public float 
	getScrapesPerSecond()
	{
		return( (float)scrape_rate.getAverage()/100 );
	}
    
	@Override
	public AnnounceStats 
	getAnnounceStats()
	{
		return( announce_provider.getStats());
	}
	
	@Override
	public ScrapeStats 
	getScrapeStats()
	{
		return( scrape_provider.getStats());
	}
	
	@Override
	public int 
	getTrackerCount()
	{
		return( host_map.size());
	}
	
	private final FrequencyLimitedDispatcher totalDisp = 
		new FrequencyLimitedDispatcher(AERunnable.create(()->{
			
			Map<String,int[]> counts = new HashMap<>();
			
			for ( DownloadManager dm: core.getGlobalManager().getDownloadManagers()){
				
				try{
					TOTorrent torrent = dm.getTorrent();
					
					if ( torrent != null ){
						
						boolean priv = torrent.getPrivate();
						
						URL announce_url = torrent.getAnnounceURL();
						
						String announce_name;
						
						if ( announce_url != null ){
						
							announce_name = ingestURL( announce_url );
							
							if ( announce_name == null ){
								
								announce_name = "";
								
							}else{
								
								int[] c = counts.get( announce_name );
								
								if ( c == null ){
									
									c = new int[2];
									
									counts.put( announce_name, c );
								}
								
								if ( priv ){
									c[0]++;
								}else{
									c[1]++;
								}
							}
						}else{
							
							announce_name = "";
						}
						
						TOTorrentAnnounceURLSet[] sets = torrent.getAnnounceURLGroup().getAnnounceURLSets();
									
						for ( TOTorrentAnnounceURLSet set: sets ){
								
							URL[] urls = set.getAnnounceURLs();
								
							for ( URL url: urls ){
									
								String name = ingestURL( url );
								
								if ( name == null || name.equals( announce_name )){
									
									continue;
								}
								
								int[] c = counts.get( name );
								
								if ( c == null ){
									
									c = new int[2];
									
									counts.put( name, c );
								}
								
								if ( priv ){
									c[0]++;
								}else{
									c[1]++;
								}
							}
						}
					}
				}catch( Throwable e ){
					
					Debug.out( e );
				}
			}
			
			List<AllTrackersTracker> updates = new ArrayList<>();

			for ( AllTrackersTrackerImpl tracker: host_map.values()){
				
				int[] c = counts.get( tracker.getTrackerName());
				
				boolean changed;
				
				if ( c != null ){
				
					changed = tracker.updateCounts( c[1],  c[0] );
					
				}else{
					
					changed = tracker.updateCounts( 0,  0 );
				}
				
				if ( changed ){
					
					updates.add( tracker );
				}
			}
			
			if ( !updates.isEmpty()){
				
				for ( AllTrackersListener listener: listeners ){
					
					try{
						listener.trackerEventOccurred(	new AllTrackersEventImpl( AllTrackersEvent.ET_TRACKER_UPDATED, updates ));
						
					}catch( Throwable e ){
						
						Debug.out( e );
					}
				}
			}
		}),
		5000 );
	
	{
		totalDisp.setSingleThreaded();
	}
	
	private void
	recalcTotals()
	{
		if ( started ){
			
			totalDisp.dispatch();
		}
	}
	
	@Override
	public void 
	registerTorrent(
		TOTorrent torrent)
	{
		if ( torrent == null ){
			
			return;
		}
					
		registerTorrentSupport( torrent );
		
		update_queue.add( new Object[]{ torrent } );
		
		recalcTotals();
	}
	
	@Override
	public void 
	unregisterTorrent(
		TOTorrent torrent )
	{
		recalcTotals();
	}
	
	private void 
	registerTorrentSupport(
		TOTorrent torrent)
	{
		registerTracker( torrent, torrent.getAnnounceURL());
		
		TOTorrentAnnounceURLSet[] sets = torrent.getAnnounceURLGroup().getAnnounceURLSets();
					
		for ( TOTorrentAnnounceURLSet set: sets ){
				
			URL[] urls = set.getAnnounceURLs();
				
			for ( URL url: urls ){
					
				register( torrent, url );
			}
		}
	}
	
	@Override
	public void
	torrentChanged(
		TOTorrent		torrent,
		int				change_type,
		Object			data )
	{
		registerTorrentSupport( torrent );
		
		recalcTotals();
	}
	
	@Override
	public void
	registerTracker(
		TOTorrent		torrent,
		URL				url )
	{
		if ( url == null ){
			
			return;
		}
				
		register( torrent, url );
	}
	
	@Override
	public void
	registerTrackers(
		TOTorrent				torrent,
		List<List<URL>>			trackers )
	{
		for ( List<URL> urls: trackers ){
			
			for ( URL url: urls ){
				
				register( torrent, url );
			}
		}
	}
	
	@Override
	public long 
	getOptionsMutationCount()
	{
		return( options_mutation_count.get());
	}
	
	@Override
	public String
	ingestURL(
		URL		url )
	{
		String 	name = url.getHost();
		
		if ( name != null && !name.endsWith( ".dht" )){

			name = name.toLowerCase( Locale.US );
			
			int	port = url.getPort();
			
			if ( port == -1 ){
				
				port = url.getDefaultPort();
			}
			
			name = url.getProtocol() + "://" + name + (port>0?(":" + port):"");
			
			return( name );
			
		}else{
			
			return( null );
		}
	}
	
	private AllTrackersTrackerImpl
	register(
		TOTorrent		torrent_maybe_null,
		URL				url )
	{
		String 	name = ingestURL( url );
		
		if ( name != null ){
			
			return( register( torrent_maybe_null, name ));
			
		}else{
			
			return( null );
		}
	}
	
	private AllTrackersTrackerImpl
	register(
		TOTorrent		torrent_maybe_null,
		String			name )
	{
		AllTrackersTrackerImpl existing_tracker = host_map.get( name );
		
		if ( existing_tracker == null ){ 
			
			AllTrackersTrackerImpl new_tracker = new AllTrackersTrackerImpl( name );
			
			existing_tracker = host_map.putIfAbsent( name, new_tracker );
			
			if ( existing_tracker == null ){
			
				for ( AllTrackersListener listener: listeners ){
					
					List<AllTrackersTracker>	trackers = new ArrayList<>();
					
					trackers.add( new_tracker );
					
					try{
						listener.trackerEventOccurred( new AllTrackersEventImpl( AllTrackersEvent.ET_TRACKER_ADDED, trackers ));

					}catch( Throwable e ){
						
						Debug.out( e );
					}
				}
				
				new_tracker.setRegistered();
				
				return( new_tracker );
			}
		}
		
		existing_tracker.setRegistered();
			
		return( existing_tracker );
	}
	
	private void
	unregisterTracker(
		String		name )
	{
		AllTrackersTrackerImpl existing_tracker = host_map.remove( name );
		
		if ( existing_tracker != null ){
			
			for ( AllTrackersListener listener: listeners ){
				
				List<AllTrackersTracker>	trackers = new ArrayList<>();
				
				trackers.add( existing_tracker );
				
				try{
					listener.trackerEventOccurred( new AllTrackersEventImpl( AllTrackersEvent.ET_TRACKER_REMOVED, trackers ));
	
				}catch( Throwable e ){
					
					Debug.out( e );
				}
			}
		}
	}
	
	@Override
	public void 
	updateTracker(
		URL 							url, 
		TRTrackerAnnouncerResponse	 	response )
	{
		AllTrackersTrackerImpl tracker = register( null, url );
		
		if ( tracker != null ){
			
			update_queue.add( new Object[]{ tracker, response } );
		}
	}
	
	@Override
	public void 
	updateTracker(
		String 						key, 
		TRTrackerAnnouncerRequest 	request )
	{
		AllTrackersTrackerImpl tracker = register( null, key );
		
		if ( tracker != null ){
			
			update_queue.add( new Object[]{ tracker, request });
		}	
	}
	
	@Override
	public void 
	updateTracker(
		URL 							url, 
		TRTrackerScraperResponse	 	response )
	{
		AllTrackersTrackerImpl tracker = register( null, url );
		
		if ( tracker != null ){
			
			int scrape_state = response.getStatus();
			
			if ( 	scrape_state == TRTrackerScraperResponse.ST_INITIALIZING ||
					scrape_state == TRTrackerScraperResponse.ST_SCRAPING ){
				
					// ignore
			}else{
				
				update_queue.add( new Object[]{ tracker, response } );
			}
		}
	}
	
	void
	queueCommand(
		AllTrackersTrackerImpl		tracker,
		String						cmd )
	{
		update_queue.add( new Object[]{ tracker, cmd } );
	}
	
	@Override
	public AllTrackersTracker 
	getTracker(
		String		name )
	{
		if ( name == null ){
			
			return( null );
		}
		
		return( host_map.get( name ));
	}
	
	@Override
	public AllTrackersTracker 
	getTracker(URL url)
	{
		AllTrackersTrackerImpl tracker = register( null, url );

		return( tracker );
	}
	
	@Override
	public boolean 
	getLoggingEnabled(
		String short_key)
	{
		synchronized( process_lock ){
			
			return( logging_keys.containsKey( short_key ));
		}
	}
	
	@Override
	public void 
	setLoggingEnabled(
		String 		short_key, 
		boolean 	enabled )
	{
		synchronized( process_lock ){

			if ( enabled ){
			
				if ( !logging_keys.containsKey( short_key )){
					
					logging_keys.put( short_key, getLogger( short_key ));
				}
				
			}else{
				
				logging_keys.remove( short_key );
			}
		}
		
		update_queue.add( new Object[]{ "logging_changed" } );
	}
	
	private LoggerChannel
	getLogger(
		String		short_key )
	{
		PluginInterface plugin_interface = CoreFactory.getSingleton().getPluginManager().getDefaultPluginInterface();
		
		LoggerChannel log = plugin_interface.getLogger().getChannel( "TrackerLog_" + FileUtil.convertOSSpecificChars( short_key, false ));

		log.setDiagnostic(-1,true);

		log.setForce( true );
				
		return( log );
	}
	
	@Override
	public File 
	getLogFile(
		String short_key )
	{
		LoggerChannel log = getLogger( short_key );

		File f = log.getCurrentFile( true );
		
		if ( f != null && !f.exists()){
			
			try{
				f.createNewFile();
				
			}catch( Throwable e ){
				
			}
		}
		
		return( f );
	}
	
	@Override
	public void
	addListener(
		AllTrackersListener		listener,
		boolean					fire_for_existing )
	{
		listeners.add( listener );
		
		if ( fire_for_existing ){
		
			List<AllTrackersTracker> existing = (List<AllTrackersTracker>)new ArrayList( host_map.values());
			
			if ( !existing.isEmpty()){
				
				listener.trackerEventOccurred( new AllTrackersEventImpl( AllTrackersEvent.ET_TRACKER_ADDED, existing ));
			}
		}
	}

	@Override
	public void
	removeListener(
		AllTrackersListener		listener )
	{
		listeners.remove( listener );
	}
	
	private class
	AllTrackersTrackerImpl
		implements AllTrackersTracker
	{
		final private String	name;
		final private String	short_key;
		
		private String		status = "";
		
		private	long		last_good;
		private	long		last_bad;
		private	long		bad_since;
		private	long		consec_fails;
		
		private Map<String,Object>	options;
		
		private Map<Long,long[]>	session_stats;
		
		private long				total_up;
		private long				total_down;
		
		private boolean		registered;
				
		private int			num_private;
		private int			num_public;
		
		private LoggerChannel	logger;
		
		private long			peers_received;		// not persisted
		
		private MovingImmediateAverage	request_average = AverageFactory.MovingImmediateAverage( 5 );
		
		private
		AllTrackersTrackerImpl(
			String		_name )
		{
			name	= _name;
			
			String sk;
			
			try{
				sk = DNSUtils.getInterestingHostSuffix( new URL( name ).getHost().toLowerCase( Locale.US ));
				
			}catch( Throwable e ){
				
				sk = null;
			}
			
			short_key = sk;
			
			updateLogger();
		}
		
		private
		AllTrackersTrackerImpl(
			Map			map )
		
			throws IOException
		{
			name = MapUtils.getMapString( map, "name", null );
			
			if ( name == null ){
				
				throw( new IOException( "Invalid" ));
			}
			
			String sk;
			
			try{
				sk = DNSUtils.getInterestingHostSuffix( new URL( name ).getHost().toLowerCase( Locale.US ));
				
			}catch( Throwable e ){
				
				sk = null;
			}
			
			short_key = sk;
			
			updateLogger();
			
			status = MapUtils.getMapString( map, "status", "" );
			
			last_good = MapUtils.getMapLong( map, "lg", 0 );
			last_bad = MapUtils.getMapLong( map, "lb", 0 );
			bad_since = MapUtils.getMapLong( map, "bs", 0 );
			consec_fails = MapUtils.getMapLong( map, "cf", 0 );
			
			options = (Map<String,Object>)map.get( "op" );
			
			Map<String,List<Number>> ss = (Map<String,List<Number>>)map.get("ss" );
			
			if ( ss != null ){
				
				session_stats = new HashMap<>();
				
					// should only be the one consolidated entry
				
				for ( Map.Entry<String,List<Number>> entry: ss.entrySet()){
					
					try{
						long id = Long.parseLong( entry.getKey());
						
						List<Number> nums = entry.getValue();
						
						long[] vals = new long[nums.size()];
						
						for ( int i=0; i<vals.length; i++){
							
							vals[i] = nums.get(i).longValue();
						}
						
						session_stats.put( id,  vals );
						
						total_up 	+= vals[1];
						total_down	+= vals[2];
						
					}catch( Throwable e ){
						
						Debug.out( e );
					}
				}
			}
		}
		
		private void
		updateLogger()
		{
			logger = logging_keys.get( short_key );
		}
		
		private Map<String,Object>
		exportToMap()
		{
			Map<String,Object>	map = new HashMap<>();
			
			map.put( "name", name );
			map.put( "status", status );
			map.put( "lg",  last_good );
			map.put( "lb",  last_bad );
			map.put( "bs",  bad_since );
			map.put( "cf",  consec_fails );
			
			if ( options != null ){
				
				map.put( "op", options );
			}
			
			if ( session_stats != null ){
								
					// consolidate all the sessions but don't touch the existing state as use is ongoing...

				long[]	consolidated = null;
					
				for ( long[] vals: session_stats.values()){
																		
					if ( consolidated == null ){
						
						consolidated = vals.clone();
						
					}else{
						
						for ( int i=1;i<Math.min( vals.length, consolidated.length ); i++){
							
							consolidated[i] = consolidated[i] + vals[i];
						}
					}
				}
				
				if ( consolidated != null ){
					
					consolidated[0] = SystemTime.getCurrentTime();
										
					List<Long> vals = new ArrayList<>();
					
					for ( long l: consolidated ){
						
						vals.add( l );
					}
					
					Map<String,Object> ss = new HashMap<>();

					ss.put( "0", vals );
					
					map.put( "ss", ss );
				}				
			}
			
			return( map );
		}
		
		private void
		setRegistered()
		{
			registered	= true;
		}
		
		private boolean
		isRegistered()
		{
			return( registered );
		}
		
		private boolean
		updateCounts(
			int		_pub,
			int		_priv )
		{
			if ( num_private != _priv || num_public != _pub ){
			
				num_private	= _priv;
				num_public	= _pub;
				
				return( true );
				
			}else{
				
				return( false );
			}
		}
		
		@Override
		public int
		getTorrentCount()
		{
			return( num_private + num_public );
		}
		
		@Override
		public boolean 
		isRemovable()
		{
			return( getTorrentCount() == 0 );
		}
		
		private void
		peersReceived(
			int		num )
		{
			peers_received += num;
		}
		
		@Override
		public long 
		getPeersReceived()
		{
			return( peers_received );
		}
		
		@Override
		public void 
		remove()
		{
			unregisterTracker( name );
		}
		
		@Override
		public String 
		getTrackerName()
		{
			return( name );
		}
		
		@Override
		public String 
		getShortKey()
		{
			return( short_key );
		}
		
		@Override
		public int 
		getPrivatePercentage()
		{
			int total = num_private + num_public;
					
			if ( total == 0 ){
				
				return( 0 );
				
			}else{
				
				return( (num_private*100)/total );
			}
		}
		
		@Override
		public String 
		getStatusString()
		{
			return( status );
		}
		
		protected boolean
		hasStatus()
		{
			return( !status.isEmpty());
		}
		
		protected boolean
		setStatusString(
			String	str )
		{
			if ( str == null ){
				
				str = "";
			}
			
			if ( str.equals( status )){
				
				return( false );
				
			}else{
				
				status = str;
				
				return( true );
			}
		}
		
		protected boolean
		setOK(
			boolean	is_ok )
		{
			long	now = SystemTime.getCurrentTime();
			
			boolean	was_ok = consec_fails == 0 && last_good > 0 ;
			
			if ( was_ok == is_ok ){
				
					// reduce updates when things don't change
				
				now = (now/(60*1000))*(60*1000);
				
				if ( is_ok ){
					
					if ( last_good == now ){
						
						return( false );
					}
				}else{
										
					if ( last_bad == now ){
					
						consec_fails++;	// keep track of this though

						return( false );
					}
				}
			}
			
			if ( is_ok ){
				
				last_good		= now;
				
				bad_since		= 0;
				
				consec_fails	= 0;
				
			}else{
				
				last_bad = now;
				
				if ( consec_fails == 0 ){
					
					bad_since	= now;
				}
				
				consec_fails++;
			}
			
			return( true );
		}
		
		protected void
		log(
			TRTrackerAnnouncerResponse	resp )
		{
			if ( logger != null ){
			
				HashWrapper hw = resp.getHash();
				
				if ( hw != null ){
					
					DownloadManager dm = core.getGlobalManager().getDownloadManager( hw );
				
					if ( dm != null ){
					
						if ( resp.getStatus() != TRTrackerAnnouncerResponse.ST_ONLINE ){
						
							TRTrackerAnnouncerRequest req = resp.getRequest();
							
							String req_details;
							
							if ( req != null ){
								
								long session = req.getSessionID();
								
								String sid = Long.toHexString( session );
								
								if ( sid.length() > 4 ){
									sid = sid.substring( 0, 4 );
								}else{
									while( sid.length() < 4 ){
										sid = "0" + sid;
									}
								}
								
								if ( req.isStopRequest()){
									sid += "$";
								}
								
								req_details = ", session=" + sid + " - pending_sent=" + req.getReportedUpload() + ", pending_received=" + req.getReportedDownload();
								
							}else{
								
								req_details = "";
							}
							
							logger.log( dm.getDisplayName() + ", " + name + req_details + " - " + resp.getStatusString());
						}
					}
				}
			}
		}
		
		protected void
		log(
			TRTrackerAnnouncerRequest	req,
			boolean						incomplete )
		{
			if ( logger != null ){
				
				HashWrapper hw = req.getHash();
				
				if ( hw != null ){
					
					DownloadManager dm = core.getGlobalManager().getDownloadManager( hw );
				
					String dm_name;
					
					if ( dm != null ){
						
						dm_name = dm.getDisplayName();
						
					}else{
						
						dm_name = dm_name_cache.get( hw );
					}
					
					if ( dm_name != null ){
					
						long session = req.getSessionID();
						
						String sid = Long.toHexString( session );
						
						if ( sid.length() > 4 ){
							sid = sid.substring( 0, 4 );
						}else{
							while( sid.length() < 4 ){
								sid = "0" + sid;
							}
						}
						
						if ( req.isStopRequest()){
							sid += "$";
						}
						
						if ( incomplete ){
							
							sid += "[Success unknown]";
						}
						
						logger.log( dm_name + ", " + name + ", session=" + sid + " - sent=" + req.getReportedUpload() + ", received=" + req.getReportedDownload());
					}
				}
			}
		}
		
		protected void
		updateSession(
			TRTrackerAnnouncerRequest		req )
		{
				// caller already validated this
			
			long	session_id = req.getSessionID();
												
			long	up 		= req.getReportedUpload();
			long	down	= req.getReportedDownload();
		
			long	now = SystemTime.getCurrentTime();

			if ( up > 0 || down > 0 ){
								
				if ( session_stats == null ){
					
					session_stats = new HashMap<>();
				}
				
				session_stats.put( session_id, new long[]{ now, up, down });
				
				long	new_up 		= 0;
				long	new_down	= 0;
				
				for ( long[] entry: session_stats.values()){
					
					new_up 		+= entry[1];
					new_down	+= entry[2];
				}
				
				total_up 	= new_up;
				total_down	= new_down;
			}
			
			if ( req.isStopRequest() && session_stats != null ){
				
				long[]	values = session_stats.remove( session_id );
				
				if ( values != null ){
					
					long[]	consolidated = session_stats.get( 0L );
					
					if ( consolidated == null ){
						
						consolidated = values;
						
						session_stats.put( 0L, consolidated );
						
					}else{
						
						for ( int i=1;i<Math.min( values.length, consolidated.length ); i++){
							
							consolidated[i] = consolidated[i] + values[i];
						}
					}
					
					consolidated[0] = now;
				}
			}
			
			
			long elapsed = req.getElapsed();
			
			if ( elapsed >= 0 ){
				
				request_average.update( elapsed );
			}
		}
		
		protected void
		resetReportedStatsSupport()
		{
			session_stats	= null;
			total_up		= 0;
			total_down		= 0;
		}
		
		public long
		getLastGoodTime()
		{
			return( last_good );
		}
		
		public long
		getLastFailTime()
		{
			return( last_bad );
		}
		
		public long
		getFailingSinceTime()
		{
			return( bad_since );
		}
		
		public long
		getConsecutiveFails()
		{
			return( consec_fails );
		}
		
		@Override
		public void 
		resetReportedStats()
		{
			queueCommand( this, "reset_stats" );
		}
		
		@Override
		public long 
		getTotalReportedDown()
		{
			return( total_down );
		}
		
		@Override
		public long 
		getTotalReportedUp()
		{
			return( total_up );
		}
		
		@Override
		public Map<String, Object> 
		getOptions()
		{
			return( options );
		}
		
		@Override
		public void 
		setOptions(
			Map<String, Object> _options)
		{
			options = _options;
			
			options_mutation_count.incrementAndGet();
		}
		
		@Override
		public long 
		getAverageRequestDuration()
		{
			if ( request_average.getSampleCount() == 0 ){
				
				return( -1 );
			}
			
			return((long)request_average.getAverage());
		}
	}
	
	private static class
	AllTrackersEventImpl
		implements AllTrackersEvent
	{
		final private int						type;
		final private List<AllTrackersTracker>	trackers;
		
		private
		AllTrackersEventImpl(
			int			_type,
			List<AllTrackersTracker>	_trackers )
		{
			type		= _type;
			trackers	= _trackers;
		}
		
		public int
		getEventType()
		{
			return( type );
		}
		
		public List<AllTrackersTracker>
		getTrackers()
		{
			return( trackers );
		}
	}
}
