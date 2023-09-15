/*
 * Created on Jul 8, 2009
 * Created by Paul Gardner
 *
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


package com.biglybt.core.content;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.biglybt.core.Core;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.download.DownloadManagerStateFactory;
import com.biglybt.core.proxy.impl.AEPluginProxyHandler;
import com.biglybt.core.security.CryptoManagerFactory;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagManager;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.tag.TagType;
import com.biglybt.core.tag.TagUtils;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentAnnounceURLGroup;
import com.biglybt.core.torrent.TOTorrentAnnounceURLSet;
import com.biglybt.core.util.*;
import com.biglybt.core.util.bloom.BloomFilter;
import com.biglybt.core.util.bloom.BloomFilterFactory;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.PluginListener;
import com.biglybt.pif.ddb.DistributedDatabase;
import com.biglybt.pif.ddb.DistributedDatabaseTransferType;
import com.biglybt.pif.disk.DiskManagerFileInfo;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadManager;
import com.biglybt.pif.download.DownloadManagerListener;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.torrent.TorrentAttribute;
import com.biglybt.pif.utils.search.SearchException;
import com.biglybt.pif.utils.search.SearchInstance;
import com.biglybt.pif.utils.search.SearchObserver;
import com.biglybt.pif.utils.search.SearchProvider;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.pifimpl.local.ddb.DDBaseImpl;
import com.biglybt.plugin.dht.*;
import com.biglybt.util.MapUtils;

public class
RelatedContentManager
{
	public static final long FILE_ASSOC_MIN_SIZE	= 50*1024*1024;

	public static final int RCM_SEARCH_PROPERTY_TRACKER_KEYS	= 50001; // don't change these, used in plugin
	public static final int RCM_SEARCH_PROPERTY_WEB_SEED_KEYS	= 50002;
	public static final int RCM_SEARCH_PROPERTY_TAGS			= 50003;
	public static final int RCM_SEARCH_PROPERTY_NETWORKS		= 50004;

	private static final boolean 	TRACE 			= false;

	private static final boolean	USE_BIGLY_DHT_FOR_PUBLIC_LOOKUPS	= false;
	
	private static final int		MAX_HISTORY					= 16;
	private static final int		MAX_TITLE_LENGTH			= 80;
	private static final int		MAX_CONCURRENT_PUBLISH;
	private static final boolean	DISABLE_PUBLISHING;

	static{
		int max_conc_pub = 2;

		DISABLE_PUBLISHING = System.getProperty(SystemProperties.SYSPROP_RCM_PUBLISH_DISABLE, "0").equals( "1" );

		try{

			max_conc_pub = Integer.parseInt( System.getProperty(SystemProperties.SYSPROP_RCM_MAX_CONCURRENT_PUBLISH, ""+max_conc_pub));

		}catch( Throwable e ){
			Debug.out( e );
		}

		MAX_CONCURRENT_PUBLISH = max_conc_pub;
	}

	private static boolean prefer_i2p;
	
	static{	
		COConfigurationManager.addAndFireParameterListener(
			"Plugin.DHT.dht.prefer.i2p",
			new ParameterListener(){
				
				@Override
				public void parameterChanged(String name){
				
					prefer_i2p = COConfigurationManager.getBooleanParameter( name, false );
				}
			});
	}
	
	private static final int	TEMPORARY_SPACE_DELTA	= 50;

	private static final int	MAX_RANK	= 100;

	private static final String	CONFIG_FILE 				= "rcm.config";
	private static final String	PERSIST_DEL_FILE 			= "rcmx.config";

	private static final String	CONFIG_TOTAL_UNREAD	= "rcm.numunread.cache";

	private static RelatedContentManager	singleton;
	private static Core core;

	protected static final int TIMER_PERIOD					= 30*1000;

	private static final int CONFIG_SAVE_CHECK_PERIOD		= 60*1000;
	private static final int CONFIG_SAVE_PERIOD				= 5*60*1000;
	private static final int CONFIG_SAVE_CHECK_TICKS		= CONFIG_SAVE_CHECK_PERIOD/TIMER_PERIOD;
	private static final int CONFIG_SAVE_TICKS				= CONFIG_SAVE_PERIOD/TIMER_PERIOD;
	private static final int PUBLISH_CHECK_PERIOD			= 30*1000;
	private static final int PUBLISH_CHECK_TICKS			= PUBLISH_CHECK_PERIOD/TIMER_PERIOD;
	private static final int PUBLISH_SLEEPING_CHECK_PERIOD	= 5*60*1000;
	private static final int PUBLISH_SLEEPING_CHECK_TICKS	= PUBLISH_SLEEPING_CHECK_PERIOD/TIMER_PERIOD;

	private static final int SECONDARY_LOOKUP_PERIOD		= 15*60*1000;
	private static final int SECONDARY_LOOKUP_TICKS			= SECONDARY_LOOKUP_PERIOD/TIMER_PERIOD;
	private static final int REPUBLISH_PERIOD				= 8*60*60*1000;
	private static final int REPUBLISH_TICKS				= REPUBLISH_PERIOD/TIMER_PERIOD;

	private static final int I2P_SEARCHER_CHECK_PERIOD		= 10*60*1000;
	private static final int I2P_SEARCHER_CHECK_TICKS		= I2P_SEARCHER_CHECK_PERIOD/TIMER_PERIOD;



	private static final int INITIAL_PUBLISH_DELAY	= 3*60*1000;
	private static final int INITIAL_PUBLISH_TICKS	= INITIAL_PUBLISH_DELAY/TIMER_PERIOD;


	private static final int CONFIG_DISCARD_MILLIS	= 60*1000;

	protected static final byte		NET_NONE	= 0x00;
	protected static final byte		NET_PUBLIC	= 0x01;
	protected static final byte		NET_I2P		= 0x02;
	protected static final byte		NET_TOR		= 0x04;

	private static final String[] NET_PUBLIC_ARRAY 			= { AENetworkClassifier.AT_PUBLIC };
	private static final String[] NET_I2P_ARRAY 			= { AENetworkClassifier.AT_I2P };
	private static final String[] NET_TOR_ARRAY 			= { AENetworkClassifier.AT_TOR };
	private static final String[] NET_PUBLIC_AND_I2P_ARRAY 	= { AENetworkClassifier.AT_PUBLIC, AENetworkClassifier.AT_I2P };

	public static synchronized void
	preInitialise(
		Core _core )
	{
		core		= _core;
	}

	public static synchronized RelatedContentManager
	getSingleton()

		throws ContentException
	{
		if ( singleton == null ){

			singleton = new RelatedContentManager();
		}

		return( singleton );
	}

	protected final Object	rcm_lock	= new Object();

	private PluginInterface 				plugin_interface;
	private TorrentAttribute 				ta_networks;
	private TorrentAttribute 				ta_category;
	
	private DHTPluginBasicInterface			public_dht_plugin;
	private DHTPluginBasicInterface			public_dht_plugin_bigly;

	private volatile Map<Byte,DHTPluginBasicInterface>		i2p_dht_plugin_map = new HashMap<>();

	private TagManager						tag_manager;

	private long	global_random_id = -1;

	private LinkedList<DownloadInfo>			pub_download_infos1 	= new LinkedList<>();
	private LinkedList<DownloadInfo>			pub_download_infos2 	= new LinkedList<>();

	private LinkedList<DownloadInfo>			non_pub_download_infos1 	= new LinkedList<>();
	private LinkedList<DownloadInfo>			non_pub_download_infos2 	= new LinkedList<>();

	private ByteArrayHashMapEx<DownloadInfo>	download_info_map	= new ByteArrayHashMapEx<>();
	private Set<String>							download_priv_set	= new HashSet<>();


	private final boolean	enabled;

	private int		max_search_level;
	private int		max_results;

	private boolean		global_filter_active_only;
	
	private AtomicInteger	temporary_space = new AtomicInteger();

	private int publishing_count = 0;

	CopyOnWriteList<RelatedContentManagerListener>	listeners = new CopyOnWriteList<>();

	AESemaphore initialisation_complete_sem = new AESemaphore( "RCM:init" );

	private ContentCache				content_cache_ref;
	private WeakReference<ContentCache>	content_cache;

	private boolean		content_dirty;
	private long		last_config_access;
	private int			content_discard_ticks;

	private AtomicInteger	total_unread = new AtomicInteger( COConfigurationManager.getIntParameter( CONFIG_TOTAL_UNREAD, 0 ));

	private AsyncDispatcher	content_change_dispatcher 	= new AsyncDispatcher();

	private static final int SECONDARY_LOOKUP_CACHE_MAX = 10;

	LinkedList<SecondaryLookup> secondary_lookups = new LinkedList<>();

	boolean	secondary_lookup_in_progress;
	long	secondary_lookup_complete_time;

	RCMSearchXFer			main_transfer_type	= new RCMSearchXFer();
	RCMSearchXFerBiglyBT	bigly_transfer_type = new RCMSearchXFerBiglyBT();

	final 	CopyOnWriteList<RelatedContentSearcher>	searchers = new CopyOnWriteList<>();

	private boolean	added_i2p_searcher;
	private RelatedContentSearcher	mix_searcher;

	private static final int MAX_TRANSIENT_CACHE	= 256;

	protected static Map<String,DownloadInfo> transient_info_cache =
		new LinkedHashMap<String,DownloadInfo>(MAX_TRANSIENT_CACHE,0.75f,true)
		{
			@Override
			protected boolean
			removeEldestEntry(
		   		Map.Entry<String,DownloadInfo> eldest)
			{
				return size() > MAX_TRANSIENT_CACHE;
			}
		};


	boolean	persist;

	{
		COConfigurationManager.addAndFireParameterListener(
			"rcm.persist",
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					String parameterName )
				{
					persist = COConfigurationManager.getBooleanParameter( "rcm.persist" ) || true;
				}
			});

			// remove one day

		COConfigurationManager.removeParameter( "rcm.dlinfo.history" );
	}

	protected
	RelatedContentManager()

		throws ContentException
	{
		COConfigurationManager.addAndFireParameterListeners(
				new String[]{
					"rcm.ui.enabled",
					"rcm.max_search_level",
					"rcm.max_results",
					"rcm.global.filter.active_only",
				},
				new ParameterListener()
				{
					@Override
					public void
					parameterChanged(
						String name )
					{
						max_search_level 	= COConfigurationManager.getIntParameter( "rcm.max_search_level", 3 );
						max_results		 	= COConfigurationManager.getIntParameter( "rcm.max_results", 500 );
						
						global_filter_active_only	= COConfigurationManager.getBooleanParameter( "rcm.global.filter.active_only", false );
					}
				});

		if ( !FeatureAvailability.isRCMEnabled() ||
			 !COConfigurationManager.getBooleanParameter( "rcm.overall.enabled", true )){

			enabled		= false;

			deleteRelatedContent();

			initialisation_complete_sem.releaseForever();

			return;
		}

		enabled = true;

		try{
			if ( core == null ){

				throw( new ContentException( "getSingleton called before pre-initialisation" ));
			}

			while( global_random_id == -1 ){

				global_random_id = COConfigurationManager.getLongParameter( "rcm.random.id", -1 );

				if ( global_random_id == -1 ){

					global_random_id = RandomUtils.nextLong();

					COConfigurationManager.setParameter( "rcm.random.id", global_random_id );
				}
			}

			plugin_interface = core.getPluginManager().getDefaultPluginInterface();

			ta_networks 	= plugin_interface.getTorrentManager().getAttribute( TorrentAttribute.TA_NETWORKS );
			ta_category 	= plugin_interface.getTorrentManager().getAttribute( TorrentAttribute.TA_CATEGORY );

			tag_manager	= TagManagerFactory.getTagManager();

			plugin_interface.getUtilities().createDelayedTask(new AERunnable() {
				@Override
				public void runSupport() {
					SimpleTimer.addEvent(
							"rcm.delay.init",
							SystemTime.getOffsetTime( 15*1000 ),
							new TimerEventPerformer()
							{
								@Override
								public void
								perform(
									TimerEvent event )
								{
									delayedInit();
								}
							});
				}
			}).queue();

		}catch( Throwable e ){

			initialisation_complete_sem.releaseForever();

			if ( e instanceof ContentException ){

				throw((ContentException)e);
			}

			throw( new ContentException( "Initialisation failed", e ));
		}
	}

	void delayedInit() {

		plugin_interface.addListener(
			new PluginListener()
			{
				@Override
				public void
				initializationComplete()
				{
					if ( !persist ){

						deleteRelatedContent();
					}

					try{
						PluginInterface dht_pi =
							plugin_interface.getPluginManager().getPluginInterfaceByClass(
										DHTPlugin.class );

						if ( dht_pi != null ){

							DHTPlugin dp = (DHTPlugin)dht_pi.getPlugin();

							public_dht_plugin = dp;
																												
							RelatedContentSearcher public_searcher = new RelatedContentSearcher( RelatedContentManager.this, main_transfer_type, public_dht_plugin, true );
													
							searchers.add( public_searcher );

							if ( dp.isEnabled()){
								
								public_dht_plugin_bigly = dp.getDHTPlugin( DHTPlugin.NW_BIGLYBT_MAIN );
									
								RelatedContentSearcher bigly_searcher = new RelatedContentSearcher( RelatedContentManager.this, bigly_transfer_type, public_dht_plugin_bigly, true );
								
								searchers.add( bigly_searcher );
							}

							DownloadManager dm = plugin_interface.getDownloadManager();

							Download[] downloads = dm.getDownloads();

							addDownloads( downloads, true );

							dm.addListener(
								new DownloadManagerListener()
								{
									@Override
									public void
									downloadAdded(
										Download	download )
									{
										addDownloads( new Download[]{ download }, false );
									}

									@Override
									public void
									downloadRemoved(
										Download	download )
									{
									}
								},
								false );

							SimpleTimer.addPeriodicEvent(
								"RCM:publisher",
								TIMER_PERIOD,
								new TimerEventPerformer()
								{
									private int	tick_count;

									@Override
									public void
									perform(
										TimerEvent event )
									{
										tick_count++;

										if ( tick_count == 1 || tick_count % I2P_SEARCHER_CHECK_TICKS == 0 ){

											checkI2PSearcher( false );
										}

										if ( enabled ){

											if ( tick_count >= INITIAL_PUBLISH_TICKS ){

												if ( tick_count % ( public_dht_plugin.isSleeping()?PUBLISH_SLEEPING_CHECK_TICKS:PUBLISH_CHECK_TICKS) == 0 ){

													publish();
												}

												if ( tick_count % SECONDARY_LOOKUP_TICKS == 0 ){

													secondaryLookup();
												}

												if ( tick_count % REPUBLISH_TICKS == 0 ){

													republish();
												}

												if ( tick_count % CONFIG_SAVE_CHECK_TICKS == 0 ){

													saveRelatedContent( tick_count );
												}
											}
										}

										for ( RelatedContentSearcher searcher: searchers ){

											searcher.timerTick( enabled, tick_count );
										}
									}
								});
						}
					}finally{

						initialisation_complete_sem.releaseForever();
					}
				}

				@Override
				public void
				closedownInitiated()
				{
					saveRelatedContent( 0 );
				}

				@Override
				public void
				closedownComplete()
				{
				}
			});
	}

	void
	checkI2PSearcher(
		boolean force )
	{
			// wanna defer adding the I2P one so we don't activate the pure destination just for rcm i2p search

		synchronized( searchers ){

			if ( added_i2p_searcher ){

				return;
			}

			if ( !force ){

				DownloadManager dm = plugin_interface.getDownloadManager();

				Download[] downloads = dm.getDownloads();

				boolean	found = false;

				for ( Download download: downloads ){

					String[] nets = PluginCoreUtils.unwrap( download ).getDownloadState().getNetworks();

					if ( nets.length == 1 && nets[0] == AENetworkClassifier.AT_I2P ){

						found = true;

						break;
					}
				}

				if ( !found ){

					return;
				}
			}

			List<DistributedDatabase> ddbs = DDBaseImpl.getDDBs( new String[]{ AENetworkClassifier.AT_I2P });

			for ( DistributedDatabase ddb: ddbs ){

				if ( ddb.getNetwork() == AENetworkClassifier.AT_I2P ){

					DHTPluginBasicInterface i2p_dht = ddb.getDHTPlugin();

					RelatedContentSearcher i2p_searcher = new RelatedContentSearcher( RelatedContentManager.this, main_transfer_type, i2p_dht, false );

					searchers.add( i2p_searcher );

					added_i2p_searcher	= true;
				}
			}
		}
	}

	private RelatedContentSearcher
	checkMixSearcher()
	{
		synchronized( searchers ){

			if ( mix_searcher == null ){

				List<DistributedDatabase> ddbs = 
					DDBaseImpl.getDDBs( new String[]{ AENetworkClassifier.AT_PUBLIC, AENetworkClassifier.AT_I2P });
	
				for ( DistributedDatabase ddb: ddbs ){
	
					if ( ddb.getNetwork() == AENetworkClassifier.AT_I2P ){
	
						DHTPluginBasicInterface i2p_mix_dht = ddb.getDHTPlugin();
	
						mix_searcher = new RelatedContentSearcher( RelatedContentManager.this, main_transfer_type, i2p_mix_dht, false );
					}
				}
			}
			
			return( mix_searcher );
		}
	}

	
	public boolean
	isEnabled()
	{
		return( enabled );
	}

	public int
	getMaxSearchLevel()
	{
		return( max_search_level );
	}

	public void
	setMaxSearchLevel(
		int		_level )
	{
		COConfigurationManager.setParameter( "rcm.max_search_level", _level );
	}

	public int
	getMaxResults()
	{
		return( max_results );
	}

	public void
	setMaxResults(
		int		_max )
	{
		COConfigurationManager.setParameter( "rcm.max_results", _max );

		enforceMaxResults( false );
	}

	public boolean
	getFilterActiveOnly()
	{
		return( global_filter_active_only );
	}

	public void
	setFilterActiveOnly(
		boolean		b )
	{
		global_filter_active_only	= b;
		
		COConfigurationManager.setParameter( "rcm.global.filter.active_only", b );
	}
		


	private DHTPluginBasicInterface
	selectDHT(
		byte		networks,
		boolean		for_lookup )
	{
		DHTPluginBasicInterface	result = null;
		
		if (	(networks & NET_PUBLIC ) != 0 &&
				(	( !prefer_i2p ) ||
					( networks & NET_I2P ) == 0 )){

			if ( for_lookup && USE_BIGLY_DHT_FOR_PUBLIC_LOOKUPS ){
				
				result = public_dht_plugin_bigly;
				
			}else{
			
				result = public_dht_plugin;
			}
		}else if ((networks & NET_I2P ) != 0 ){

			synchronized( i2p_dht_plugin_map ){

				result = i2p_dht_plugin_map.get( networks );

				if ( result == null && !i2p_dht_plugin_map.containsKey( networks )){

					try{

						List<DistributedDatabase> ddbs = DDBaseImpl.getDDBs( convertNetworks( networks ));

						for ( DistributedDatabase ddb: ddbs ){

							if ( ddb.getNetwork() == AENetworkClassifier.AT_I2P ){

								result = ddb.getDHTPlugin();
							}
						}
					}finally{

						i2p_dht_plugin_map.put( networks, result );
					}
				}
			}
		}

		if ( result != null ){

			if ( !result.isEnabled()){

				result = null;
			}
		}

		return( result );
	}

	protected void
	addDownloads(
		Download[]		downloads,
		boolean			initialising )
	{
		synchronized( rcm_lock ){

			List<DownloadInfo>	new_info = new ArrayList<>(downloads.length);

			for ( Download download: downloads ){

				try{
					if ( !download.isPersistent()){

						continue;
					}

					Torrent	torrent = download.getTorrent();

					if ( torrent == null ){

						continue;
					}

					byte[]	hash = torrent.getHash();

					if ( download_info_map.containsKey( hash )){

						continue;
					}

					byte nets = getNetworks( download );

					if ( nets == NET_NONE ){

						continue;
					}

					TOTorrent to_torrent = PluginCoreUtils.unwrap( torrent );

					if ( !( TorrentUtils.isReallyPrivate( to_torrent ) || TorrentUtils.getFlag( to_torrent, TorrentUtils.TORRENT_FLAG_DISABLE_RCM ))){

						DownloadManagerState state = PluginCoreUtils.unwrap( download ).getDownloadState();

						if ( state.getFlag(DownloadManagerState.FLAG_LOW_NOISE ) || state.getFlag(DownloadManagerState.FLAG_METADATA_DOWNLOAD )){

							continue;
						}

						if ( to_torrent.getTorrentType() == TOTorrent.TT_V1_V2 && to_torrent.getEffectiveTorrentType() == TOTorrent.TT_V2 ){
						
								// secondary swarm torrent
							
							continue;
						}
						
						LinkedList<DownloadInfo>			download_infos1;
						LinkedList<DownloadInfo>			download_infos2;

						if (( nets & NET_PUBLIC ) != 0 ){

							download_infos1 	= pub_download_infos1;
							download_infos2 	= pub_download_infos2;

						}else{

							download_infos1		= non_pub_download_infos1;
							download_infos2 	= non_pub_download_infos2;
						}

						int version = RelatedContent.VERSION_INITIAL;

						long rand = global_random_id ^ state.getLongParameter( DownloadManagerState.PARAM_RANDOM_SEED );

						int	seeds_leechers;

						int[]	aggregate_seeds_leechers = DownloadManagerStateFactory.getCachedAggregateScrapeSeedsLeechers( state );

						if ( aggregate_seeds_leechers == null ){

							long cache = state.getLongAttribute( DownloadManagerState.AT_SCRAPE_CACHE );

							if ( cache == -1 ){

								seeds_leechers = -1;

							}else{

								int seeds 		= (int)((cache>>32)&0x00ffffff);
								int leechers 	= (int)(cache&0x00ffffff);

								seeds_leechers 	= (int)((seeds<<16)|(leechers&0xffff));
							}
						}else{

							version = RelatedContent.VERSION_BETTER_SCRAPE;

							int seeds 		= aggregate_seeds_leechers[0];
							int leechers 	= aggregate_seeds_leechers[1];

							seeds_leechers 	= (int)((seeds<<16)|(leechers&0xffff));
						}

						byte[][] keys = getKeys( download );

						int first_seen = (int)(state.getLongParameter( DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME )/1000);
						
						DownloadInfo info =
							new DownloadInfo(
								version,
								hash,
								hash,
								download.getName(),
								(int)rand,
								torrent.isPrivate()?StringInterner.intern(torrent.getAnnounceURL().getHost()):null,
								keys[0],
								keys[1],
								getTags( download ),
								nets,
								first_seen,
								0,
								false,
								torrent.getSize(),
								(int)( to_torrent.getCreationDate()/(60*60)),
								seeds_leechers);

						new_info.add( info );

						if ( initialising || download_infos1.size() == 0 ){

							download_infos1.add( info );

						}else{

							download_infos1.add( RandomUtils.nextInt( download_infos1.size()), info );
						}

						download_infos2.add( info );

						download_info_map.put( hash, info );

						if ( info.getTracker() != null ){

							download_priv_set.add( getPrivateInfoKey( info ));
						}
					}
				}catch( Throwable e ){

					Debug.out( e );
				}
			}

			List<Map<String,Object>> history = (List<Map<String,Object>>)COConfigurationManager.getListParameter( "rcm.dlinfo.history.privx", new ArrayList<Map<String,Object>>());

			if ( initialising ){

				int padd = MAX_HISTORY - download_info_map.size();

				for ( int i=0;i<history.size() && padd > 0;i++ ){

					try{
						DownloadInfo info = deserialiseDI((Map<String,Object>)history.get(i), null);

						if ( info != null && !download_info_map.containsKey( info.getHash())){

							download_info_map.put( info.getHash(), info );

							if ( info.getTracker() != null ){

								download_priv_set.add( getPrivateInfoKey( info ));
							}

							byte nets = info.getNetworksInternal();

							if ( nets != NET_NONE ){

								if (( nets & NET_PUBLIC ) != 0 ){

									pub_download_infos1.add( info );
									pub_download_infos2.add( info );

								}else{

									non_pub_download_infos1.add( info );
									non_pub_download_infos2.add( info );
								}

								padd--;
							}
						}
					}catch( Throwable e ){
					}
				}

				Collections.shuffle( pub_download_infos1 );
				Collections.shuffle( non_pub_download_infos1 );

			}else{

				if ( new_info.size() > 0 ){

					final List<String>	base32_hashes = new ArrayList<>();

					for ( DownloadInfo info: new_info ){

						byte[] hash = info.getHash();

						if ( hash != null ){

							base32_hashes.add( Base32.encode( hash ));
						}

						Map<String,Object> map = serialiseDI( info, null );

						if ( map != null ){

							history.add( map );
						}
					}

					while( history.size() > MAX_HISTORY ){

						history.remove(0);
					}

					COConfigurationManager.setParameter( "rcm.dlinfo.history.privx", history );

					if ( base32_hashes.size() > 0 ){

						content_change_dispatcher.dispatch(
							new AERunnable()
							{
								@Override
								public void
								runSupport()
								{
									List<RelatedContent>	to_remove = new ArrayList<>();

									synchronized( rcm_lock ){

										ContentCache content_cache = loadRelatedContent();

										for ( String h: base32_hashes ){

											DownloadInfo di = content_cache.related_content.get( h );

											if ( di != null ){

												to_remove.add( di );
											}
										}
									}

									if ( to_remove.size() > 0 ){

										delete( to_remove.toArray( new RelatedContent[ to_remove.size()] ));
									}
								}
							});
					}
				}
			}
		}
	}

	void
	republish()
	{
		if ( DISABLE_PUBLISHING ){

			return;
		}

		synchronized( rcm_lock ){

			if ( publishing_count > 0 ){

				return;
			}

			if ( 	pub_download_infos1.isEmpty() ||
					( 	pub_download_infos1.size() == 1 &&
						pub_download_infos1.getFirst() == pub_download_infos2.getFirst())){

				pub_download_infos1.clear();
				pub_download_infos2.clear();

				List<DownloadInfo> list = download_info_map.values();

				for ( DownloadInfo info: list ){

					if (( info.getNetworksInternal() & NET_PUBLIC ) != 0 ){

						pub_download_infos1.add( info );
						pub_download_infos2.add( info );
					}
				}

				Collections.shuffle( pub_download_infos1 );
			}

			if ( non_pub_download_infos1.isEmpty() ||
					( 	non_pub_download_infos1.size() == 1 &&
						non_pub_download_infos1.getFirst() == non_pub_download_infos2.getFirst())){

				non_pub_download_infos1.clear();
				non_pub_download_infos2.clear();

				List<DownloadInfo> list = download_info_map.values();

				for ( DownloadInfo info: list ){

					byte nets = info.getNetworksInternal();

					if ( nets != NET_NONE ){

						if (( nets & NET_PUBLIC ) == 0 ){

							non_pub_download_infos1.add( info );
							non_pub_download_infos2.add( info );
						}
					}
				}

				Collections.shuffle( non_pub_download_infos1 );
			}
		}
	}

	private boolean last_pub_was_pub;

	void
	publish()
	{
		if ( DISABLE_PUBLISHING ){

			return;
		}

		while( true ){

			DownloadInfo	info1 = null;
			DownloadInfo	info2 = null;

			synchronized( rcm_lock ){

				if ( TRACE ){
					System.out.println( "publish: count=" + publishing_count + ", dim=" + download_info_map.size() + ", pub=" + pub_download_infos1.size() + "/" + pub_download_infos2.size() + ", nonpub=" + non_pub_download_infos1.size() + "/" + non_pub_download_infos2.size());
				}

				if ( publishing_count >= MAX_CONCURRENT_PUBLISH ){

						// too busy

					return;
				}

				if ( download_info_map.size() == 1 ){

						// only one download, nothing to pair up with

					return;
				}

				boolean	pub_ok = false;

				if ( 	pub_download_infos1.isEmpty() ||
						( 	pub_download_infos1.size() == 1 &&
							pub_download_infos1.getFirst() == pub_download_infos2.getFirst())){

					// either none or only one download remaining to be published and it has no partner

				}else{

					pub_ok = true;
				}

				boolean	non_pub_ok = false;

				if ( 	non_pub_download_infos1.isEmpty() ||
						( 	non_pub_download_infos1.size() == 1 &&
							non_pub_download_infos1.getFirst() == non_pub_download_infos2.getFirst())){

					// either none or only one download remaining to be published and it has no partner

				}else{

					non_pub_ok = true;
				}

				if ( !( pub_ok || non_pub_ok )){

					return;
				}

				LinkedList<DownloadInfo> download_infos1;
				LinkedList<DownloadInfo> download_infos2;

				if ( pub_ok && non_pub_ok ){

					if ( last_pub_was_pub ){

						pub_ok = false;
					}

					last_pub_was_pub = !last_pub_was_pub;
				}

				if ( pub_ok ){

					download_infos1 = pub_download_infos1;
					download_infos2 = pub_download_infos2;

				}else{

					download_infos1 = non_pub_download_infos1;
					download_infos2 = non_pub_download_infos2;
				}

				if ( download_infos1.isEmpty() || download_info_map.size() == 1 ){

					return;
				}

				info1 = download_infos1.removeFirst();

				Iterator<DownloadInfo> it = download_infos2.iterator();

				while( it.hasNext()){

					info2 = it.next();

					if ( info1 != info2 || download_infos2.size() == 1 ){

						it.remove();

						break;
					}
				}

				if ( info1 == info2 ){

					return;
				}

				publishing_count++;
			}

			try{
				if ( !publish( info1, info2 )){

					synchronized( rcm_lock ){

						publishing_count--;
					}
				}
			}catch( Throwable e ){

				synchronized( rcm_lock ){

					publishing_count--;
				}

				Debug.out( e );
			}
		}
	}

	void
	publishNext()
	{
		synchronized( rcm_lock ){

			publishing_count--;

			if ( publishing_count < 0 ){

					// shouldn't happen but whatever

				publishing_count = 0;
			}
		}

		publish();
	}

	private boolean
	publish(
		final DownloadInfo	from_info,
		final DownloadInfo	to_info )

		throws Exception
	{
		final DHTPluginBasicInterface dht_plugin = selectDHT( from_info.getNetworksInternal(), false );

		// System.out.println( "publish: " + from_info.getString() + " -> " + to_info.getString() + ": " + dht_plugin );

		if ( dht_plugin == null ){

			return( false );
		}

		final String from_hash	= ByteFormatter.encodeString( from_info.getHash());
		final String to_hash	= ByteFormatter.encodeString( to_info.getHash());

		final byte[] key_bytes	= ( "az:rcm:assoc:" + from_hash ).getBytes( "UTF-8" );

		String title = to_info.getTitle();

		if ( title.length() > MAX_TITLE_LENGTH ){

			title = title.substring( 0, MAX_TITLE_LENGTH );
		}

		Map<String,Object> map = new HashMap<>();

		map.put( "d", title );
		map.put( "r", new Long( Math.abs( to_info.getRand()%1000 )));

		String	tracker = to_info.getTracker();

		if ( tracker == null ){

			map.put( "h", to_info.getHash());

		}else{

			map.put( "t", tracker );
		}

		if ( to_info.getLevel() == 0 ){

			try{
				Download to_download = to_info.getRelatedToDownload();

				if ( to_download != null ){

					int	version	= RelatedContent.VERSION_INITIAL;

					Torrent torrent = to_download.getTorrent();

					if ( torrent != null ){

						long secs = torrent.getCreationDate();

						long hours = secs/(60*60);

						if ( hours > 0 ){

							map.put( "p", new Long( hours ));
						}
					}

					DownloadManagerState state = PluginCoreUtils.unwrap( to_download ).getDownloadState();

					int leechers 	= -1;
					int seeds 		= -1;

					int[]	aggregate_seeds_leechers = DownloadManagerStateFactory.getCachedAggregateScrapeSeedsLeechers( state );

					if ( aggregate_seeds_leechers == null ){

						long cache = state.getLongAttribute( DownloadManagerState.AT_SCRAPE_CACHE );

						if ( cache != -1 ){

							seeds 		= (int)((cache>>32)&0x00ffffff);
							leechers 	= (int)(cache&0x00ffffff);
						}
					}else{

						seeds		= aggregate_seeds_leechers[0];
						leechers	= aggregate_seeds_leechers[1];

						version 	= RelatedContent.VERSION_BETTER_SCRAPE;
					}

					if ( version > 0 ){
						map.put( "v", new Long( version ));
					}

					if ( leechers > 0 ){
						map.put( "l", new Long( leechers ));
					}
					if ( seeds > 0 ){
						map.put( "z", new Long( seeds ));
					}

					byte[][] keys = getKeys( to_download );

					if ( keys[0] != null ){
						map.put( "k", keys[0] );
					}
					if ( keys[1] != null ){
						map.put( "w", keys[1] );
					}

					String[] _tags = getTags( to_download );

					if ( _tags != null ){
						map.put( "g", encodeTags( _tags ));
					}

					byte nets = getNetworks( to_download );

					if ( nets != NET_PUBLIC ){
						map.put( "o", new Long( nets&0xff ));
					}
				}
			}catch( Throwable e ){
				Debug.out(e);
			}
		}

		Download from_download = from_info.getRelatedToDownload();

		final Set<String>	my_tags = new HashSet<>();

		try{
			if ( from_download != null ){

				String[] _tags = getTags( from_download );

				if ( _tags != null ){

					map.put( "b", from_info.getRand() % 100 );

					map.put( "m", encodeTags( _tags ));

					Collections.addAll(my_tags, _tags);
				}
			}
		}catch( Throwable e ){
			
			Debug.out(e);
		}

		Set<String>	my_tags_original = new HashSet<>( my_tags );
		
		long	size = to_info.getSize();

		if ( size != 0 ){

			map.put( "s", new Long( size ));
		}

		final byte[] map_bytes = BEncoder.encode( map );

		//System.out.println( "rcmsize=" + map_bytes.length );

		final int max_hits = 30;

		dht_plugin.get(
				key_bytes,
				"Content rel test: " + from_hash.substring( 0, 16 ),
				DHTPlugin.FLAG_SINGLE_VALUE,
				max_hits,
				30*1000,
				false,
				false,
				new DHTPluginOperationListener()
				{
					private boolean diversified;
					private int		hits;

					private Set<String>	entries = new HashSet<>();

					private Set<String>	discovered_tags = new HashSet<>();
					
					@Override
					public void
					starts(
						byte[]				key )
					{
					}

					@Override
					public boolean
					diversified()
					{
						diversified = true;

						return( false );
					}

					@Override
					public void
					valueRead(
						DHTPluginContact	originator,
						DHTPluginValue		value )
					{
						try{
							Map<String,Object> map = (Map<String,Object>)BDecoder.decode( value.getValue());

							DownloadInfo info = decodeInfo( map, from_info.getHash(), 1, false, entries );

							try{
								String[] r_tags = decodeTags((byte[]) map.get( "m" ));

 								if ( r_tags != null ){

									Long	b = (Long)map.get( "b" );

										// don't remove tags from the set that we actually published

									if ( b == null || from_info.getRand()%100 != b%100 ){

										for ( String tag: r_tags ){

											synchronized( my_tags ){

												my_tags.remove( tag );
												
												if ( !my_tags_original.contains( tag )){
													
													discovered_tags.add( tag );
												}
											}
										}
									}
								}
							}catch( Throwable e ){
							}

							if ( info != null ){

								analyseResponse( info, null );
							}
						}catch( Throwable e ){
						}

						hits++;
					}

					@Override
					public void
					valueWritten(
						DHTPluginContact	target,
						DHTPluginValue		value )
					{

					}

					@Override
					public void
					complete(
						byte[]				key,
						boolean				timeout_occurred )
					{
						if ( from_download != null ){
							
							synchronized( my_tags ){
							
								if ( !discovered_tags.isEmpty()){
									
									Set<String> interesting = new HashSet<>();
									
									for ( String tag: discovered_tags ){
										
										if ( TagUtils.isInternalTagName( tag )){
											
											continue;
										}
										
										interesting.add( tag );
									}
									
									if ( !interesting.isEmpty()){

										try{
											DownloadManagerState dms = PluginCoreUtils.unwrap( from_download ).getDownloadState();
											
											String[] old = dms.getListAttribute( DownloadManagerState.AT_SWARM_TAGS );
											
											if ( old == null || old.length == 0 ){
												
												dms.setListAttribute( DownloadManagerState.AT_SWARM_TAGS, interesting.toArray( new String[0] ));
												
											}else{
													
												if ( old.length < 16 ){
													
													interesting.addAll( Arrays.asList( old ));
													
													if ( interesting.size() > old.length ){
														
														dms.setListAttribute( DownloadManagerState.AT_SWARM_TAGS, interesting.toArray( new String[0] ));
													}
												}
											}
										}catch( Throwable e ){
											
										}
									}
								}
							}
						}
							// if we have something to say prioritise it somewhat

						int f_cutoff = my_tags.size()>0?20:10;

						try{
							boolean	do_it;

							// System.out.println( from_hash + ": hits=" + hits + ", div=" + diversified );

							if ( diversified || hits >= f_cutoff ){

								do_it = false;

							}else if ( hits <= f_cutoff / 2 ){

								do_it = true;

							}else{

								do_it = RandomUtils.nextInt( hits - (f_cutoff/2) + 1 ) == 0;
							}

							if ( do_it ){

								try{
									dht_plugin.put(
											key_bytes,
											"Content rel: " +  from_hash.substring( 0, 16 ) + " -> " + to_hash.substring( 0, 16 ),
											map_bytes,
											DHTPlugin.FLAG_ANON,
											new DHTPluginOperationListener()
											{
												@Override
												public boolean
												diversified()
												{
													return( true );
												}

												@Override
												public void
												starts(
													byte[] 				key )
												{
												}

												@Override
												public void
												valueRead(
													DHTPluginContact	originator,
													DHTPluginValue		value )
												{
												}

												@Override
												public void
												valueWritten(
													DHTPluginContact	target,
													DHTPluginValue		value )
												{
												}

												@Override
												public void
												complete(
													byte[]				key,
													boolean				timeout_occurred )
												{
													publishNext();
												}
											});
								}catch( Throwable e ){

									Debug.printStackTrace(e);

									publishNext();
								}
							}else{

								publishNext();
							}
						}finally{

							checkAlternativePubs( to_info, map_bytes, f_cutoff );
						}
					}
				});

		return( true );
	}

	void
	checkAlternativePubs(
		DownloadInfo	to_info,
		final byte[]	map_bytes,
		final int		f_cutoff )
	{
		Download dl = to_info.getRelatedToDownload();

		if ( dl != null ){

			DiskManagerFileInfo[] files = dl.getDiskManagerFileInfo();

			List<Long>	sizes = new ArrayList<>();

			for ( DiskManagerFileInfo file: files ){

				long	size = file.getLength();

				if ( size >= FILE_ASSOC_MIN_SIZE ){

					sizes.add( size );
				}
			}

			final DHTPluginBasicInterface dht_plugin = selectDHT( to_info.getNetworksInternal(), false );

			if ( dht_plugin != null && sizes.size() > 0 ){

				try{
					final String to_hash	= ByteFormatter.encodeString( to_info.getHash());

					final long selected_size = sizes.get( new Random().nextInt( sizes.size()));

					final byte[] key_bytes	= ( "az:rcm:size:assoc:" + selected_size ).getBytes( "UTF-8" );

					int	max_hits = 30;

					dht_plugin.get(
							key_bytes,
							"Content size rel test: " + to_hash.substring( 0, 16 ),
							DHTPlugin.FLAG_SINGLE_VALUE,
							max_hits,
							30*1000,
							false,
							false,
							new DHTPluginOperationListener()
							{
								private boolean diversified;
								private int		hits;

								private Set<String>	entries = new HashSet<>();

								@Override
								public void
								starts(
									byte[]				key )
								{
								}

								@Override
								public boolean
								diversified()
								{
									diversified = true;

									return( false );
								}

								@Override
								public void
								valueRead(
									DHTPluginContact	originator,
									DHTPluginValue		value )
								{
									hits++;
								}

								@Override
								public void
								valueWritten(
									DHTPluginContact	target,
									DHTPluginValue		value )
								{

								}

								@Override
								public void
								complete(
									byte[]				key,
									boolean				timeout_occurred )
								{
									boolean	do_it;

									// System.out.println( from_hash + ": hits=" + hits + ", div=" + diversified );

									if ( diversified || hits >= f_cutoff ){

										do_it = false;

									}else if ( hits <= f_cutoff / 2 ){

										do_it = true;

									}else{

										do_it = RandomUtils.nextInt( hits - ( f_cutoff / 2 ) + 1 ) == 0;
									}

									if ( do_it ){

										try{
											dht_plugin.put(
												key_bytes,
												"Content size rel: " +  selected_size + " -> " + to_hash.substring( 0, 16 ),
												map_bytes,
												DHTPlugin.FLAG_ANON,
												new DHTPluginOperationListener()
												{
													@Override
													public boolean
													diversified()
													{
														return( true );
													}

													@Override
													public void
													starts(
														byte[] 				key )
													{
													}

													@Override
													public void
													valueRead(
														DHTPluginContact	originator,
														DHTPluginValue		value )
													{
													}

													@Override
													public void
													valueWritten(
														DHTPluginContact	target,
														DHTPluginValue		value )
													{
													}

													@Override
													public void
													complete(
														byte[]				key,
														boolean				timeout_occurred )
													{
													}
												});
										}catch( Throwable e ){

											Debug.printStackTrace(e);
										}
									}
								}
							});
				}catch( Throwable e ){

					Debug.out( e);
				}
			}
		}
	}

	protected DownloadInfo
	decodeInfo(
		Map				map,
		byte[]			from_hash,	// will be null for those trawled from the local DHT
		int				level,
		boolean			explicit,
		Set<String>		unique_keys )
	{
		try{
			String	title = new String((byte[])map.get( "d" ), "UTF-8" );

			String	tracker	= null;

			byte[]	hash 	= (byte[])map.get( "h" );

			if ( hash == null ){

				tracker = new String((byte[])map.get( "t" ), "UTF-8" );
			}

			int	rand = ((Long)map.get( "r" )).intValue();

			String	key = title + " % " + rand;

			synchronized( unique_keys ){

				if ( unique_keys.contains( key )){

					return( null );
				}

				unique_keys.add( key );
			}

			Long	l_version 	= (Long)map.get( "v" );

			int	version = l_version==null?RelatedContent.VERSION_INITIAL:l_version.intValue();

			Long	l_size = (Long)map.get( "s" );

			long	size = l_size==null?0:l_size.longValue();

			Long	published 	= (Long)map.get( "p" );
			Long	leechers 	= (Long)map.get( "l" );
			Long	seeds	 	= (Long)map.get( "z" );

			// System.out.println( "p=" + published + ", l=" + leechers + ", s=" + seeds );

			int	seeds_leechers;

			if ( leechers == null && seeds == null ){

				seeds_leechers = -1;

			}else if ( leechers == null ){

				seeds_leechers = seeds.intValue()<<16;

			}else if ( seeds == null ){

				seeds_leechers = leechers.intValue()&0xffff;

			}else{

				seeds_leechers = (seeds.intValue()<<16)|(leechers.intValue()&0xffff);
			}

			byte[] tracker_keys = (byte[])map.get( "k" );
			byte[] ws_keys 		= (byte[])map.get( "w" );

			if ( tracker_keys != null && tracker_keys.length % 4 != 0 ){

				tracker_keys = null;
			}

			if ( ws_keys != null && ws_keys.length % 4 != 0 ){

				ws_keys = null;
			}

			byte[]	_tags = (byte[])map.get( "g" );

			String[] tags = decodeTags( _tags );

			Long _nets = (Long)map.get( "o" );

			byte nets = _nets==null?NET_PUBLIC:_nets.byteValue();
			
			int first_seen = (int)(SystemTime.getCurrentTime()/1000);
			
			return(
				new DownloadInfo(
						version,
						from_hash, hash, title, rand, tracker, tracker_keys, ws_keys, tags, nets, first_seen, level, explicit, size,
						published==null?0:published.intValue(),
						seeds_leechers));

		}catch( Throwable e ){

			return( null );
		}
	}

	public void
	lookupAttributes(
		final byte[]							from_hash,
		final RelatedAttributeLookupListener	listener )

		throws ContentException
	{
		lookupAttributes( from_hash, new String[]{ AENetworkClassifier.AT_PUBLIC }, listener );
	}

	public void
	lookupAttributes(
		final byte[]							from_hash,
		final String[]							networks,
		final RelatedAttributeLookupListener	listener )

		throws ContentException
	{
		if ( from_hash == null ){

			throw( new ContentException( "hash is null" ));
		}

		if ( 	!initialisation_complete_sem.isReleasedForever() ||
				( public_dht_plugin != null && public_dht_plugin.isInitialising())){

			AsyncDispatcher dispatcher = new AsyncDispatcher();

			dispatcher.dispatch(
				new AERunnable()
				{
					@Override
					public void
					runSupport()
					{
						try{
							initialisation_complete_sem.reserve();

							lookupAttributesSupport( from_hash, convertNetworks( networks ), listener );

						}catch( ContentException e ){

							Debug.out( e );
						}
					}
				});
		}else{

			lookupAttributesSupport( from_hash, convertNetworks( networks ), listener );
		}
	}

	void
	lookupAttributesSupport(
		final byte[]							from_hash,
		final byte								networks,
		final RelatedAttributeLookupListener	listener )

		throws ContentException
	{
		try{
			if ( !enabled ){

				throw( new ContentException( "rcm is disabled" ));
			}

			Download from_download = getDownload( from_hash );
			
			Set<String> existing_tags;
			
			if ( from_download != null ){

				existing_tags = getExplicitTags( from_download );
				
			}else{
				
				existing_tags = Collections.emptySet();
			}
			
			final DHTPluginBasicInterface dht_plugin = selectDHT( networks, true );

			if ( dht_plugin == null ){

				throw( new Exception( "DHT Plugin unavailable for networks " + getString( convertNetworks( networks ))));
			}

				// really should implement a getNetwork() in DHTPluginInterface...

			final String dht_plugin_network = dht_plugin == public_dht_plugin?AENetworkClassifier.AT_PUBLIC:AENetworkClassifier.AT_I2P;

			final String from_hash_str	= ByteFormatter.encodeString( from_hash );

			final byte[] key_bytes	= ( "az:rcm:assoc:" + from_hash_str ).getBytes( "UTF-8" );

			String op_str = "Content attr read: " + from_hash_str.substring( 0, 16 );

			dht_plugin.get(
					key_bytes,
					op_str,
					DHTPlugin.FLAG_SINGLE_VALUE,
					512,
					30*1000,
					false,
					true,
					new DHTPluginOperationListener()
					{
						private Set<String>	tags 		= new HashSet<>();
						private Set<String>	swarm_tags	= new HashSet<>();
						
						
						@Override
						public void
						starts(
							byte[]				key )
						{
							if ( listener != null ){

								try{
									listener.lookupStart();

								}catch( Throwable e ){

									Debug.out( e );
								}

								ContentCache	content_cache = loadRelatedContent();

								DownloadInfo info = content_cache.related_content.get( Base32.encode( from_hash ));

								if ( info != null ){

									String[] l_tags = info.getTags();

									if ( l_tags != null ){

										for ( String tag: l_tags ){

											synchronized( tags ){

												if ( tags.contains( tag )){

													continue;
												}

												tags.add( tag );
											}

											try{
												listener.tagFound( tag, dht_plugin_network );

											}catch( Throwable e ){

												Debug.out( e );
											}
										}
									}
								}
							}
						}

						@Override
						public boolean
						diversified()
						{
							return( true );
						}

						@Override
						public void
						valueRead(
							DHTPluginContact	originator,
							DHTPluginValue		value )
						{
							try{
								Map<String,Object> map = (Map<String,Object>)BDecoder.decode( value.getValue());

								String[] r_tags = decodeTags((byte[]) map.get( "m" ));

								if ( r_tags != null ){

									for ( String tag: r_tags ){

										synchronized( tags ){

											if ( !TagUtils.isInternalTagName( tag )){
												
												if ( !existing_tags.contains( tag )){

													swarm_tags.add( tag );
												}
											}
											
											if ( tags.contains( tag )){

												continue;
											}

											tags.add( tag );
										}

										try{
											listener.tagFound( tag, dht_plugin_network );

										}catch( Throwable e ){

											Debug.out( e );
										}
									}
								}

							}catch( Throwable e ){
							}
						}

						@Override
						public void
						valueWritten(
							DHTPluginContact	target,
							DHTPluginValue		value )
						{

						}

						@Override
						public void
						complete(
							byte[]				key,
							boolean				timeout_occurred )
						{
							if ( from_download != null ){
								
								synchronized( tags ){
									
									if ( !swarm_tags.isEmpty()){
										
										DownloadManagerState dms = PluginCoreUtils.unwrap( from_download ).getDownloadState();
										
										String[] old = dms.getListAttribute( DownloadManagerState.AT_SWARM_TAGS );
										
										if ( old == null || old.length == 0 ){
											
											dms.setListAttribute( DownloadManagerState.AT_SWARM_TAGS, swarm_tags.toArray( new String[0] ));
											
										}else{
												
											if ( old.length < 16 ){
												
												swarm_tags.addAll( Arrays.asList( old ));
												
												if ( swarm_tags.size() > old.length ){
													
													dms.setListAttribute( DownloadManagerState.AT_SWARM_TAGS, swarm_tags.toArray( new String[0] ));
												}
											}
										}
									}
								}
							}
							
							if ( listener != null ){

								try{
									listener.lookupComplete();

								}catch( Throwable e ){

									Debug.out( e );
								}
							}
						}
					});
		}catch( Throwable e ){

			ContentException	ce;

			if ( ( e instanceof ContentException )){

				ce = (ContentException)e;

			}else{
				ce = new ContentException( "Lookup failed", e );
			}

			if ( listener != null ){

				try{
					listener.lookupFailed( ce );

				}catch( Throwable f ){

					Debug.out( f );
				}
			}

			throw( ce );
		}
	}

	public void
	lookupContent(
		final byte[]						hash,
		final RelatedContentLookupListener	listener )

		throws ContentException
	{
		if ( hash == null ){

			throw( new ContentException( "hash is null" ));
		}

		byte	net = NET_PUBLIC;

		try{
			Download download = getDownload( hash );

			if ( download != null ){

				net = getNetworks( download );
			}
		}catch( Throwable e ){

		}

		final byte f_net = net;

		if ( 	!initialisation_complete_sem.isReleasedForever() ||
				( public_dht_plugin != null && public_dht_plugin.isInitialising())){

			AsyncDispatcher dispatcher = new AsyncDispatcher();

			dispatcher.dispatch(
				new AERunnable()
				{
					@Override
					public void
					runSupport()
					{
						try{
							initialisation_complete_sem.reserve();

							lookupContentSupport( hash, 0, f_net, true, listener );

						}catch( ContentException e ){

							Debug.out( e );
						}
					}
				});
		}else{

			lookupContentSupport( hash, 0, f_net, true, listener );
		}
	}

	public void
	lookupContent(
		final byte[]						hash,
		final String[]						networks,
		final RelatedContentLookupListener	listener )

		throws ContentException
	{
		if ( hash == null ){

			throw( new ContentException( "hash is null" ));
		}

		final byte	net = convertNetworks( networks );

		if ( net == 0 ){

			throw( new ContentException( "No networks specified" ));
		}

		if ( 	!initialisation_complete_sem.isReleasedForever() ||
				( public_dht_plugin != null && public_dht_plugin.isInitialising())){

			AsyncDispatcher dispatcher = new AsyncDispatcher();

			dispatcher.dispatch(
				new AERunnable()
				{
					@Override
					public void
					runSupport()
					{
						try{
							initialisation_complete_sem.reserve();

							lookupContentSupport( hash, 0, net, true, listener );

						}catch( ContentException e ){

							Debug.out( e );
						}
					}
				});
		}else{

			lookupContentSupport( hash, 0, net, true, listener );
		}
	}

	public void
	lookupContent(
		final long							file_size,
		final RelatedContentLookupListener	listener )

		throws ContentException
	{
		if ( file_size < FILE_ASSOC_MIN_SIZE ){

			throw( new ContentException( "file size is invalid - min=" + FILE_ASSOC_MIN_SIZE ));
		}

		if ( 	!initialisation_complete_sem.isReleasedForever() ||
				( public_dht_plugin != null && public_dht_plugin.isInitialising())){

			AsyncDispatcher dispatcher = new AsyncDispatcher();

			dispatcher.dispatch(
				new AERunnable()
				{
					@Override
					public void
					runSupport()
					{
						try{
							initialisation_complete_sem.reserve();

							lookupContentSupport( file_size, NET_PUBLIC, listener );

						}catch( ContentException e ){

							Debug.out( e );
						}
					}
				});
		}else{

			lookupContentSupport( file_size, NET_PUBLIC, listener );
		}
	}

	public void
	lookupContent(
		final long							file_size,
		final String[]						networks,
		final RelatedContentLookupListener	listener )

		throws ContentException
	{
		if ( file_size < FILE_ASSOC_MIN_SIZE ){

			throw( new ContentException( "file size is invalid - min=" + FILE_ASSOC_MIN_SIZE ));
		}

		final byte	net = convertNetworks( networks );

		if ( net == 0 ){

			throw( new ContentException( "No networks specified" ));
		}

		if ( 	!initialisation_complete_sem.isReleasedForever() ||
				( public_dht_plugin != null && public_dht_plugin.isInitialising())){

			AsyncDispatcher dispatcher = new AsyncDispatcher();

			dispatcher.dispatch(
				new AERunnable()
				{
					@Override
					public void
					runSupport()
					{
						try{
							initialisation_complete_sem.reserve();

							lookupContentSupport( file_size, net, listener );

						}catch( ContentException e ){

							Debug.out( e );
						}
					}
				});
		}else{

			lookupContentSupport( file_size, net, listener );
		}
	}

	void
	lookupContentSupport(
		final long							file_size,
		final byte							networks,
		final RelatedContentLookupListener	listener )

		throws ContentException
	{
		if ( !enabled ){

			throw( new ContentException( "rcm is disabled" ));
		}

		try{
			final byte[] key_bytes	= ( "az:rcm:size:assoc:" + file_size ).getBytes( "UTF-8" );

				// we need something to use

			final byte[] from_hash = new SHA1Simple().calculateHash( key_bytes );

			String op_str = "Content rel read: size=" + file_size;

			lookupContentSupport0( from_hash, key_bytes, op_str, 0, networks, true, listener );

		}catch( ContentException e ){

			throw( e );

		}catch( Throwable e ){

			throw( new ContentException( "lookup failed", e ));
		}
	}

	void
	lookupContentSupport(
		final byte[]						from_hash,
		final int							level,
		final byte							networks,
		final boolean						explicit,
		final RelatedContentLookupListener	listener )

		throws ContentException
	{
		if ( !enabled ){

			throw( new ContentException( "rcm is disabled" ));
		}

		try{

			final String from_hash_str	= ByteFormatter.encodeString( from_hash );

			final byte[] key_bytes	= ( "az:rcm:assoc:" + from_hash_str ).getBytes( "UTF-8" );

			String op_str = "Content rel read: " + from_hash_str.substring( 0, 16 );

			lookupContentSupport0( from_hash, key_bytes, op_str, level, networks, explicit, listener );

		}catch( ContentException e ){

			throw( e );

		}catch( Throwable e ){

			throw( new ContentException( "lookup failed", e ));
		}
	}

	private String
	getString(
		String[]	args )
	{
		String str = "";

		for ( String s: args ){

			str += (str.length()==0?"":",") + s;
		}

		return( str );
	}

	private void
	lookupContentSupport0(
		final byte[]						from_hash,
		final byte[]						key_bytes,
		final String						op_str,
		final int							level,
		final byte							networks,
		final boolean						explicit,
		final RelatedContentLookupListener	listener )

		throws ContentException
	{
		try{
			final int max_hits = 30;

			DHTPluginBasicInterface dht_plugin = selectDHT( networks, true );

			if ( dht_plugin == null ){

				throw( new Exception( "DHT Plugin unavailable for networks '" + getString( convertNetworks( networks )) + "'" ));
			}

			dht_plugin.get(
					key_bytes,
					op_str,
					DHTPlugin.FLAG_SINGLE_VALUE,
					max_hits,
					60*1000,
					false,
					true,
					new DHTPluginOperationListener()
					{
						private Set<String>	entries = new HashSet<>();

						private RelatedContentManagerListener manager_listener =
							new RelatedContentManagerListener()
							{
							private Set<RelatedContent>	content_list = new HashSet<>();

								@Override
								public void
								contentFound(
									RelatedContent[]	content )
								{
									handle( content );
								}

								@Override
								public void
								contentChanged(
									RelatedContent[]	content )
								{
									handle( content );
								}

								@Override
								public void
								contentRemoved(
									RelatedContent[] 	content )
								{
								}

								@Override
								public void
								contentChanged()
								{
								}

								@Override
								public void
								contentReset()
								{
								}

								private void
								handle(
									RelatedContent[]	content )
								{
									List<RelatedContent>	new_content = new ArrayList<>(content.length);

									synchronized( content_list ){

										for ( RelatedContent rc: content ){

											if ( !content_list.contains( rc )){

												new_content.add( rc );
											}
										}

										if ( new_content.size() == 0 ){

											return;
										}

										content_list.addAll( new_content );
									}

									listener.contentFound( new_content.toArray( new RelatedContent[new_content.size()] ));
								}
							};

						@Override
						public void
						starts(
							byte[]				key )
						{
							if ( listener != null ){

								try{
									listener.lookupStart();

								}catch( Throwable e ){

									Debug.out( e );
								}
							}
						}

						@Override
						public boolean
						diversified()
						{
							return( true );
						}

						@Override
						public void
						valueRead(
							DHTPluginContact	originator,
							DHTPluginValue		value )
						{
							try{
								Map<String,Object> map = (Map<String,Object>)BDecoder.decode( value.getValue());

								DownloadInfo info = decodeInfo( map, from_hash, level+1, explicit, entries );

								if ( info != null ){

									analyseResponse( info, listener==null?null:manager_listener );
								}
							}catch( Throwable e ){
							}
						}

						@Override
						public void
						valueWritten(
							DHTPluginContact	target,
							DHTPluginValue		value )
						{

						}

						@Override
						public void
						complete(
							byte[]				key,
							boolean				timeout_occurred )
						{
							if ( listener != null ){

								try{
									listener.lookupComplete();

								}catch( Throwable e ){

									Debug.out( e );
								}
							}
						}
					});
		}catch( Throwable e ){

			ContentException	ce;

			if ( ( e instanceof ContentException )){

				ce = (ContentException)e;

			}else{
				ce = new ContentException( "Lookup failed", e );
			}

			if ( listener != null ){

				try{
					listener.lookupFailed( ce );

				}catch( Throwable f ){

					Debug.out( f );
				}
			}

			throw( ce );
		}
	}

	protected void
	popuplateSecondaryLookups(
		ContentCache	content_cache )
	{
		Random rand = new Random();

		secondary_lookups.clear();

			// stuff in a couple primarys

		List<DownloadInfo> primaries = download_info_map.values();

		int	primary_count = primaries.size();

		int	primaries_to_add;

		if ( primary_count < 2 ){

			primaries_to_add = 0;

		}else if ( primary_count < 5 ){

			if ( rand.nextInt(4) == 0 ){

				primaries_to_add = 1;

			}else{

				primaries_to_add = 0;
			}
		}else if ( primary_count < 10 ){

			primaries_to_add = 1;

		}else{

			primaries_to_add = 2;
		}

		if ( primaries_to_add > 0 ){

			Set<DownloadInfo> added = new HashSet<>();

			for (int i=0;i<primaries_to_add;i++){

				DownloadInfo info = primaries.get( rand.nextInt( primaries.size()));

				if ( !added.contains( info )){

					added.add( info );

					secondary_lookups.addLast(new SecondaryLookup(info.getHash(), info.getLevel(), info.getNetworksInternal()));
				}
			}
		}

		Map<String,DownloadInfo>		related_content			= content_cache.related_content;

		Iterator<DownloadInfo> it = related_content.values().iterator();

		List<DownloadInfo> secondary_cache_temp = new ArrayList<>(related_content.size());

		while( it.hasNext()){

			DownloadInfo di = it.next();

			if ( di.getHash() != null && di.getLevel() < max_search_level ){

				secondary_cache_temp.add( di );
			}
		}

		final int cache_size = Math.min( secondary_cache_temp.size(), SECONDARY_LOOKUP_CACHE_MAX - secondary_lookups.size());

		if ( cache_size > 0 ){

			for( int i=0;i<cache_size;i++){

				int index = rand.nextInt( secondary_cache_temp.size());

				DownloadInfo x = secondary_cache_temp.get( index );

				secondary_cache_temp.set( index, secondary_cache_temp.get(i));

				secondary_cache_temp.set( i, x );
			}

			for ( int i=0;i<cache_size;i++){

				DownloadInfo x = secondary_cache_temp.get(i);

				secondary_lookups.addLast(new SecondaryLookup(x.getHash(), x.getLevel(), x.getNetworksInternal()));
			}
		}
	}

	protected void
	secondaryLookup()
	{
		SecondaryLookup sl;

		long	now = SystemTime.getMonotonousTime();

		synchronized( rcm_lock ){

			if ( secondary_lookup_in_progress ){

				return;
			}

			if ( now - secondary_lookup_complete_time < SECONDARY_LOOKUP_PERIOD ){

				return;
			}

			if ( secondary_lookups.size() == 0 ){

				ContentCache cc = content_cache==null?null:content_cache.get();

				if ( cc == null ){

						// this will populate the cache

					cc = loadRelatedContent();

				}else{

					popuplateSecondaryLookups( cc );
				}
			}

			if ( secondary_lookups.size() == 0 ){

				return;
			}

			sl = secondary_lookups.removeFirst();

			secondary_lookup_in_progress = true;
		}

		try{
			lookupContentSupport(
				sl.getHash(),
				sl.getLevel(),
				sl.getNetworks(),
				false,
				new RelatedContentLookupListener()
				{
					@Override
					public void
					lookupStart()
					{
					}

					@Override
					public void
					contentFound(
						RelatedContent[]	content )
					{
					}

					@Override
					public void
					lookupComplete()
					{
						next();
					}

					@Override
					public void
					lookupFailed(
						ContentException 	error )
					{
						next();
					}

					protected void
					next()
					{
						final SecondaryLookup next_sl;

						synchronized( rcm_lock ){

							if ( secondary_lookups.size() == 0 ){

								secondary_lookup_in_progress = false;

								secondary_lookup_complete_time = SystemTime.getMonotonousTime();

								return;

							}else{

								next_sl = secondary_lookups.removeFirst();
							}
						}

						final RelatedContentLookupListener listener = this;

						SimpleTimer.addEvent(
							"RCM:SLDelay",
							SystemTime.getOffsetTime( 30*1000 ),
							new TimerEventPerformer()
							{
								@Override
								public void
								perform(
									TimerEvent event )
								{
									try{
										lookupContentSupport( next_sl.getHash(), next_sl.getLevel(), next_sl.getNetworks(), false, listener );

									}catch( Throwable e ){

										//Debug.out( e );

										synchronized( rcm_lock ){

											secondary_lookup_in_progress = false;

											secondary_lookup_complete_time = SystemTime.getMonotonousTime();
										}
									}
								}
							});
					}
				});

		}catch( Throwable e ){

			//Debug.out( e );

			synchronized( rcm_lock ){

				secondary_lookup_in_progress = false;

				secondary_lookup_complete_time = now;
			}
		}
	}

	protected void
	contentChanged(
		final DownloadInfo		info )
	{
		setConfigDirty();

		content_change_dispatcher.dispatch(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					for ( RelatedContentManagerListener l: listeners ){

						try{
							l.contentChanged( new RelatedContent[]{ info });

						}catch( Throwable e ){

							Debug.out( e );
						}
					}
				}
			});
	}

	protected void
	contentChanged(
		boolean	is_dirty )
	{
		if ( is_dirty ){

			setConfigDirty();
		}

		content_change_dispatcher.dispatch(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					for ( RelatedContentManagerListener l: listeners ){

						try{
							l.contentChanged();

						}catch( Throwable e ){

							Debug.out( e );
						}
					}
				}
			});
	}

	public void
	delete(
		RelatedContent[]	content )
	{
		synchronized( rcm_lock ){

			ContentCache content_cache = loadRelatedContent();

			delete( content, content_cache, true );
		}
	}

	protected void
	delete(
		final RelatedContent[]	content,
		ContentCache			content_cache,
		boolean					persistent )
	{
		if ( persistent ){

			addPersistentlyDeleted( content );
		}

		Map<String,DownloadInfo> related_content = content_cache.related_content;

		Iterator<DownloadInfo> it = related_content.values().iterator();

		while( it.hasNext()){

			DownloadInfo di = it.next();

			for ( RelatedContent c: content ){

				if ( c == di ){

					it.remove();

					if ( di.isUnread()){

						decrementUnread();
					}
				}
			}
		}

		ByteArrayHashMapEx<ArrayList<DownloadInfo>> related_content_map = content_cache.related_content_map;

		List<byte[]> delete = new ArrayList<>();

		for ( byte[] key: related_content_map.keys()){

			ArrayList<DownloadInfo>	infos = related_content_map.get( key );

			for ( RelatedContent c: content ){

				if ( infos.remove( c )){

					if ( infos.size() == 0 ){

						delete.add( key );

						break;
					}
				}
			}
		}

		for ( byte[] key: delete ){

			related_content_map.remove( key );
		}

		setConfigDirty();

		content_change_dispatcher.dispatch(
				new AERunnable()
				{
					@Override
					public void
					runSupport()
					{
						for ( RelatedContentManagerListener l: listeners ){

							try{
								l.contentRemoved( content );

							}catch( Throwable e ){

								Debug.out( e );
							}
						}
					}
				});
	}

	protected String
	getPrivateInfoKey(
		RelatedContent		info )
	{
		return( info.getTitle() + ":" + info.getTracker());
	}

	protected void
	analyseResponse(
		DownloadInfo						to_info,
		final RelatedContentManagerListener	listener )
	{
		try{
			synchronized( rcm_lock ){

				byte[] target = to_info.getHash();

				String	key;

				if ( target != null ){

					if ( download_info_map.containsKey( target )){

							// target refers to download we already have

						return;
					}

					key = Base32.encode( target );

				}else{

					key = getPrivateInfoKey( to_info );

					if ( download_priv_set.contains( key )){

							// target refers to download we already have

						return;
					}
				}

				if ( isPersistentlyDeleted( to_info )){

					return;
				}

				if ( globalFilter( to_info )){
					
					return;
				}
				
				ContentCache	content_cache = loadRelatedContent();

				DownloadInfo	target_info = null;

				boolean	changed_content = false;
				boolean	new_content 	= false;


				target_info = content_cache.related_content.get( key );

				if ( target_info == null ){

					if ( enoughSpaceFor( content_cache, to_info )){

						target_info = to_info;

						content_cache.related_content.put( key, target_info );

						byte[] from_hash = to_info.getRelatedToHash();

						ArrayList<DownloadInfo> links = content_cache.related_content_map.get( from_hash );

						if ( links == null ){

							links = new ArrayList<>(1);

							content_cache.related_content_map.put( from_hash, links );
						}

						links.add( target_info );

						links.trimToSize();

						target_info.setPublic( content_cache );

						if ( secondary_lookups.size() < SECONDARY_LOOKUP_CACHE_MAX ){

							byte[]	hash 	= target_info.getHash();
							int		level	= target_info.getLevel();

							if ( hash != null && level < max_search_level ){

								secondary_lookups.add( new SecondaryLookup( hash, level, target_info.getNetworksInternal()));
							}
						}

						new_content = true;

					}else{

						transient_info_cache.put( key, to_info );
					}
				}else{

						// we already know about this, see if new info - ignore lower versions

					if ( to_info.getVersion() >= target_info.getVersion()){

						changed_content = target_info.addInfo( to_info );
					}
				}

				if ( target_info != null ){

					final RelatedContent[]	f_target 	= new RelatedContent[]{ target_info };
					final boolean			f_change	= changed_content;

					final boolean something_changed = changed_content || new_content;

					if ( something_changed ){

						setConfigDirty();
					}

					content_change_dispatcher.dispatch(
						new AERunnable()
						{
							@Override
							public void
							runSupport()
							{
								if ( something_changed ){

									for ( RelatedContentManagerListener l: listeners ){

										try{
											if ( f_change ){

												l.contentChanged( f_target );

											}else{

												l.contentFound( f_target );
											}
										}catch( Throwable e ){

											Debug.out( e );
										}
									}
								}

								if ( listener != null ){

									try{
										if ( f_change ){

											listener.contentChanged( f_target );

										}else{

											listener.contentFound( f_target );
										}
									}catch( Throwable e ){

										Debug.out( e );
									}
								}
							}
						});
				}
			}

		}catch( Throwable e ){

			Debug.out( e );
		}
	}

	protected boolean
	enoughSpaceFor(
		ContentCache	content_cache,
		DownloadInfo	fi )
	{
		Map<String,DownloadInfo> related_content = content_cache.related_content;

		if ( related_content.size() < max_results + temporary_space.get()){

			return( true );
		}

		Iterator<Map.Entry<String,DownloadInfo>>	it = related_content.entrySet().iterator();

		int	max_level 		= fi.getLevel();

			// delete oldest at highest level >= level with minimum rank

		Map<Integer,DownloadInfo>	oldest_per_rank = new HashMap<>();

		int	min_rank 	= Integer.MAX_VALUE;
		int	max_rank	= -1;

		while( it.hasNext()){

			Map.Entry<String,DownloadInfo> entry = it.next();

			DownloadInfo info = entry.getValue();

			if ( info.isExplicit()){

				continue;
			}

			int	info_level = info.getLevel();

			if ( info_level >= max_level ){

				if ( info_level > max_level ){

					max_level = info_level;

					min_rank 	= Integer.MAX_VALUE;
					max_rank	= -1;

					oldest_per_rank.clear();
				}

				int	rank = info.getRank();

				if ( rank < min_rank ){

					min_rank = rank;

				}else if ( rank > max_rank ){

					max_rank = rank;
				}

				DownloadInfo oldest = oldest_per_rank.get( rank );

				if ( oldest == null ){

					oldest_per_rank.put( rank, info );

				}else{

					if ( info.getLastSeenSecs() < oldest.getLastSeenSecs()){

						oldest_per_rank.put( rank, info );
					}
				}
			}
		}

		DownloadInfo to_remove = oldest_per_rank.get( min_rank );

		if ( to_remove != null ){

			delete( new RelatedContent[]{ to_remove }, content_cache, false );

			return( true );
		}

			// we don't want high-ranked entries to get stuck there and prevent newer stuff from getting in and rising up

		if ( max_level == 1 ){

			to_remove = oldest_per_rank.get( max_rank );

			if ( to_remove != null ){

				int	now_secs = (int)( SystemTime.getCurrentTime()/1000 );

					// give it a day at the top

				if ( now_secs - to_remove.getLastSeenSecs() >= 24*60*60 ){

					delete( new RelatedContent[]{ to_remove }, content_cache, false );

					return( true );
				}
			}
		}

		return( false );
	}

	public RelatedContent[]
	getRelatedContent()
	{
		synchronized( rcm_lock ){

			ContentCache	content_cache = loadRelatedContent();

			return( content_cache.related_content.values().toArray( new DownloadInfo[ content_cache.related_content.size()]));
		}
	}

	protected List<DownloadInfo>
  	getRelatedContentAsList()
  	{
  		synchronized( rcm_lock ){

  			ContentCache	content_cache = loadRelatedContent();

  			return(new ArrayList<>(content_cache.related_content.values()));
  		}
  	}

	public void
	reset()
	{
		reset( true );
	}

	protected void
	reset(
		boolean	reset_perm_dels )
	{
		synchronized( rcm_lock ){

			ContentCache cc = content_cache==null?null:content_cache.get();

			if ( cc == null ){

				FileUtil.deleteResilientConfigFile( CONFIG_FILE );

			}else{

				cc.related_content 		= new HashMap<>();
				cc.related_content_map 	= new ByteArrayHashMapEx<>();
			}

			pub_download_infos1.clear();
			pub_download_infos2.clear();

			non_pub_download_infos1.clear();
			non_pub_download_infos2.clear();

			List<DownloadInfo>	list = download_info_map.values();

			for ( DownloadInfo info: list ){

				byte nets = info.getNetworksInternal();

				if ( nets != NET_NONE ){

					if (( nets & NET_PUBLIC ) != 0 ){

						pub_download_infos1.add( info );
						pub_download_infos2.add( info );

					}else{

						non_pub_download_infos1.add( info );
						non_pub_download_infos2.add( info );
					}
				}
			}

			Collections.shuffle( pub_download_infos1 );
			Collections.shuffle( non_pub_download_infos1 );

			total_unread.set( 0 );

			if ( reset_perm_dels ){

				resetPersistentlyDeleted();
			}

			setConfigDirty();
		}

		content_change_dispatcher.dispatch(
				new AERunnable()
				{
					@Override
					public void
					runSupport()
					{
						for ( RelatedContentManagerListener l: listeners ){

							l.contentReset();
						}
					}
				});
	}

	public SearchInstance
	searchRCM(
		final Map<String,Object>		search_parameters,
		final SearchObserver			observer )

		throws SearchException
	{
		if ( !initialisation_complete_sem.isReleasedForever()){

			AsyncDispatcher dispatcher = new AsyncDispatcher();

			final boolean[] 		cancelled 	= { false };
			final boolean[] 		went_async 	= { false };
			final SearchInstance[]	si 			= { null };
			final SearchException[]	error 		= { null };

			final AESemaphore temp_sem = new AESemaphore( "" );

			dispatcher.dispatch(
				new AERunnable()
				{
					@Override
					public void
					runSupport()
					{
						try{
							si[0] = searchRCMSupport( search_parameters, observer );

							synchronized( cancelled ){

								if ( cancelled[0] ){

									si[0].cancel();
								}
							}
						}catch( Throwable e ){

							Debug.out( e );

							SearchException se;

							if ( e instanceof SearchException ){

								se = (SearchException)e;

							}else{

								se = new SearchException( "Search failed", e );
							}

							synchronized( cancelled ){

								error[0] = se;

								if ( went_async[0] ){

										// error won't be returned to caller, signify that things
										// ain't going anywhere

									observer.complete();
								}
							}
						}finally{

							temp_sem.release();
						}
					}
				});

			temp_sem.reserve( 500 );

			synchronized( cancelled ){

				if ( si[0] != null ){

					return( si[0] );
				}

				if ( error[0] != null ){

					throw( error[0] );
				}

				went_async[0] = true;
			}

			SearchInstance	result =
				new SearchInstance()
				{
					@Override
					public void
					cancel()
					{
						synchronized( cancelled ){

							if ( si[0] != null ){

								si[0].cancel();
							}

							cancelled[0] = true;
						}
					}
				};

			return( result );

		}else{

			return( searchRCMSupport( search_parameters, observer ));
		}
	}

	SearchInstance
	searchRCMSupport(
		Map<String,Object>		search_parameters,
		SearchObserver			observer )

		throws SearchException
	{
		initialisation_complete_sem.reserve();

		if ( !enabled ){

			throw( new SearchException( "rcm is disabled" ));
		}

		String[]	networks = (String[])search_parameters.get( SearchProvider.SP_NETWORKS );

		String	target_net = AENetworkClassifier.AT_PUBLIC;

		if ( networks != null ){

			for ( String net: networks ){

				if ( net == AENetworkClassifier.AT_PUBLIC ){

					target_net = AENetworkClassifier.AT_PUBLIC;

					break;

				}else if ( net == AENetworkClassifier.AT_I2P ){

					target_net = AENetworkClassifier.AT_I2P;
				}
			}
		}

		if ( target_net == AENetworkClassifier.AT_I2P ){

			checkI2PSearcher( true );
			
		}else if ( prefer_i2p ) {
			
			RelatedContentSearcher searcher = checkMixSearcher();
			
			if ( searcher != null ) {
				
				return( searcher.searchRCM( search_parameters, observer ));
			}
		}
		
		DHTPluginBasicInterface target_dht_plugin = USE_BIGLY_DHT_FOR_PUBLIC_LOOKUPS?public_dht_plugin_bigly:null;
		
		for ( RelatedContentSearcher searcher: searchers ){

			DHTPluginBasicInterface dht_plugin = searcher.getDHTPlugin();
			
			String net = dht_plugin.getAENetwork();

			if ( net == target_net ){
				
				if ( 	target_net !=  AENetworkClassifier.AT_PUBLIC ||
						target_dht_plugin == null || 
						target_dht_plugin == dht_plugin ){
					
					return( searcher.searchRCM( search_parameters, observer ));		
				}
			}
		}

		throw( new SearchException( "no searchers available" ));
	}


	protected void
	setConfigDirty()
	{
		synchronized( rcm_lock ){

			content_dirty	= true;
		}
	}

	protected ContentCache
	loadRelatedContent()
	{
		boolean	fire_event = false;

		try{
			synchronized( rcm_lock ){

				last_config_access = SystemTime.getMonotonousTime();

				ContentCache cc = content_cache==null?null:content_cache.get();

				if ( cc == null ){

					if ( TRACE ){
						System.out.println( "rcm: load new" );
					}

					fire_event = true;

					cc = new ContentCache();

					content_cache = new WeakReference<>(cc);

					try{
						int	new_total_unread = 0;

						if ( FileUtil.resilientConfigFileExists( CONFIG_FILE )){

							Map map = FileUtil.readResilientConfigFile( CONFIG_FILE );

							Map<String,DownloadInfo>						related_content			= cc.related_content;
							ByteArrayHashMapEx<ArrayList<DownloadInfo>>		related_content_map		= cc.related_content_map;

							Map<String,String>	rcm_map;

							byte[]	data = (byte[])map.get( "d" );

							if ( data != null ){
								map = null;

								BufferedInputStream is = null;
								try{
									is = new BufferedInputStream(
											new GZIPInputStream(new ByteArrayInputStream(
													CryptoManagerFactory.getSingleton().deobfuscate(
															data))));
									map = BDecoder.decode(is);

								} catch (Throwable ignore) {
								} finally {
									if (is != null) {
										try {
											is.close();
										} catch (Throwable ignore) {
										}
									}
								}

								if (map == null) {

										// can get here is config's been deleted

									map = new HashMap();
								}
							}

							rcm_map = (Map<String,String>)map.get( "rcm" );

							Object	rc_map_stuff 	= map.get( "rc" );

							if ( rc_map_stuff != null && rcm_map != null ){

								Map<Integer,DownloadInfo> id_map = new HashMap<>();

								if ( rc_map_stuff instanceof Map ){

										// migration from when it was a Map with non-ascii key issues

									Map<String,Map<String,Object>>	rc_map 	= (Map<String,Map<String,Object>>)rc_map_stuff;

									for ( Map.Entry<String,Map<String,Object>> entry: rc_map.entrySet()){

										try{

											String	key = entry.getKey();

											Map<String,Object>	info_map = entry.getValue();

											DownloadInfo info = deserialiseDI( info_map, cc );

											if ( info.isUnread()){

												new_total_unread++;
											}

											related_content.put( key, info );

											int	id = ((Long)info_map.get( "_i" )).intValue();

											id_map.put( id, info );

										}catch( Throwable e ){

											Debug.out( e );
										}
									}
								}else{

									List<Map<String,Object>>	rc_map_list 	= (List<Map<String,Object>>)rc_map_stuff;

									for ( Map<String,Object> info_map: rc_map_list ){

										try{

											String	key = new String((byte[])info_map.get( "_k" ), "UTF-8" );

											DownloadInfo info = deserialiseDI( info_map, cc );

											if ( info.isUnread()){

												new_total_unread++;
											}

											related_content.put( key, info );

											int	id = ((Long)info_map.get( "_i" )).intValue();

											id_map.put( id, info );

										}catch( Throwable e ){

											Debug.out( e );
										}
									}
								}

								if ( rcm_map.size() != 0 && id_map.size() != 0 ){

									for ( String key: rcm_map.keySet()){

										try{
											byte[]	hash = Base32.decode( key );

											int[]	ids = MapUtils.importIntArray( rcm_map, key );

											if ( ids == null || ids.length == 0 ){

												// Debug.out( "Inconsistent - no ids" );

											}else{

												ArrayList<DownloadInfo>	di_list = new ArrayList<>(ids.length);

												for ( int id: ids ){

													DownloadInfo di = id_map.get( id );

													if ( di == null ){

														// Debug.out( "Inconsistent: id " + id + " missing" );

													}else{

															// we don't currently remember all originators, just one that works

														di.setRelatedToHash( hash );

														di_list.add( di );
													}
												}

												if ( di_list.size() > 0 ){

													related_content_map.put( hash, di_list );
												}
											}
										}catch( Throwable e ){

											Debug.out( e );
										}
									}
								}

								Iterator<DownloadInfo> it = related_content.values().iterator();

								while( it.hasNext()){

									DownloadInfo di = it.next();

									if ( di.getRelatedToHash() == null ){

										// Debug.out( "Inconsistent: info not referenced" );

										if ( di.isUnread()){

											new_total_unread--;
										}

										it.remove();
									}
								}

								popuplateSecondaryLookups( cc );
							}
						}

						if ( total_unread.get() != new_total_unread ){

							// Debug.out( "total_unread - inconsistent (" + total_unread + "/" + new_total_unread + ")" );

							total_unread.set( new_total_unread );

							COConfigurationManager.setParameter( CONFIG_TOTAL_UNREAD, new_total_unread );
						}
					}catch( Throwable e ){

						Debug.out( e );
					}

					enforceMaxResults( cc, false );
				}

				content_cache_ref = cc;

				return( cc );
			}
		}finally{

			if ( fire_event ){

				contentChanged( false );
			}
		}
	}

	protected void
	saveRelatedContent(
		int	tick_count )
	{
		synchronized( rcm_lock ){

			COConfigurationManager.setParameter( CONFIG_TOTAL_UNREAD, total_unread.get());

			long	now = SystemTime.getMonotonousTime();

			ContentCache cc = content_cache==null?null:content_cache.get();

			if ( !content_dirty ){

				if ( cc != null  ){

					if ( now - last_config_access > CONFIG_DISCARD_MILLIS ){

						if ( content_cache_ref != null ){

							content_discard_ticks = 0;
						}

						if ( TRACE ){
							System.out.println( "rcm: discard: tick count=" + content_discard_ticks++ );
						}

						content_cache_ref	= null;
					}
				}else{

					if ( TRACE ){
						System.out.println( "rcm: discarded" );
					}
				}

				return;
			}

			if ( tick_count % CONFIG_SAVE_TICKS != 0 ){

				return;
			}

			last_config_access = now;

			content_dirty	= false;

			if ( cc == null ){

				// Debug.out( "RCM: cache inconsistent" );

			}else{

				if ( persist ){

					if ( TRACE ){
						System.out.println( "rcm: save" );
					}

					Map<String,DownloadInfo>						related_content			= cc.related_content;
					ByteArrayHashMapEx<ArrayList<DownloadInfo>>		related_content_map		= cc.related_content_map;

					if ( related_content.size() == 0 ){

						FileUtil.deleteResilientConfigFile( CONFIG_FILE );

					}else{

						Map<String,Object>	map = new HashMap<>();

						Set<Map.Entry<String,DownloadInfo>> rcs = related_content.entrySet();

						List<Map<String,Object>> rc_map_list = new ArrayList<>(rcs.size());

						map.put( "rc", rc_map_list );

						int		id = 0;

						Map<DownloadInfo,Integer>	info_map = new HashMap<>();

						for ( Map.Entry<String,DownloadInfo> entry: rcs ){

							DownloadInfo	info = entry.getValue();

							Map<String,Object> di_map = serialiseDI( info, cc );

							if ( di_map != null ){

								info_map.put( info, id );

								di_map.put( "_i", new Long( id ));
								di_map.put( "_k", entry.getKey());

								rc_map_list.add( di_map );

								id++;
							}
						}

						Map<String,Object> rcm_map = new HashMap<>();

						map.put( "rcm", rcm_map );

						for ( byte[] hash: related_content_map.keys()){

							List<DownloadInfo> dis = related_content_map.get( hash );

							int[] ids = new int[dis.size()];

							int	pos = 0;

							for ( DownloadInfo di: dis ){

								Integer	index = info_map.get( di );

								if ( index == null ){

									// Debug.out( "inconsistent: info missing for " + di );

									break;

								}else{

									ids[pos++] = index;
								}
							}

							if ( pos == ids.length ){

								MapUtils.exportIntArrayAsByteArray( rcm_map, Base32.encode( hash), ids );
							}
						}

						if ( true ){

							ByteArrayOutputStream baos = new ByteArrayOutputStream( 100*1024 );

							try{
								GZIPOutputStream gos = new GZIPOutputStream( baos );

								gos.write( BEncoder.encode( map ));

								gos.close();

							}catch( Throwable e ){

								Debug.out( e );
							}

							map.clear();

							map.put( "d", CryptoManagerFactory.getSingleton().obfuscate( baos.toByteArray()));
						}

						FileUtil.writeResilientConfigFile( CONFIG_FILE, map );
					}
				}else{

					deleteRelatedContent();
				}

				for ( RelatedContentSearcher searcher: searchers ){

					searcher.updateKeyBloom( cc );
				}
			}
		}
	}

	void
	deleteRelatedContent()
	{
		FileUtil.deleteResilientConfigFile( CONFIG_FILE );
		FileUtil.deleteResilientConfigFile( PERSIST_DEL_FILE );
	}

	public int
	getNumUnread()
	{
		return( total_unread.get());
	}

	public void
	setAllRead()
	{
		boolean	changed = false;

		synchronized( rcm_lock ){

			DownloadInfo[] content = (DownloadInfo[])getRelatedContent();

			for ( DownloadInfo c: content ){

				if ( c.isUnread()){

					changed = true;

					c.setUnreadInternal( false );
				}
			}

			total_unread.set( 0 );
		}

		if ( changed ){

			contentChanged( true );
		}
	}

	public void
	deleteAll()
	{
		synchronized( rcm_lock ){

			ContentCache	content_cache = loadRelatedContent();

			addPersistentlyDeleted( content_cache.related_content.values().toArray( new DownloadInfo[ content_cache.related_content.size()]));

			reset( false );
		}
	}

	protected void
	incrementUnread()
	{
		total_unread.incrementAndGet();
	}

	protected void
	decrementUnread()
	{
		synchronized( rcm_lock ){

			int val = total_unread.decrementAndGet();

			if ( val < 0 ){

				// Debug.out( "inconsistent" );

				total_unread.set( 0 );
			}
		}
	}

	protected Download
	getDownload(
		byte[]	hash )
	{
		try{
			return( plugin_interface.getDownloadManager().getDownload( hash ));

		}catch( Throwable e ){

			return( null );
		}
	}

	private byte[][]
	getKeys(
		Download		download )
	{
		byte[]	tracker_keys	= null;
		byte[]	ws_keys			= null;

		try{
			Torrent torrent = download.getTorrent();

			if ( torrent != null ){

				TOTorrent to_torrent = PluginCoreUtils.unwrap( torrent );

				Set<String>	tracker_domains = new HashSet<>();

				addURLToDomainKeySet( tracker_domains, to_torrent.getAnnounceURL());

				TOTorrentAnnounceURLGroup group = to_torrent.getAnnounceURLGroup();

				TOTorrentAnnounceURLSet[] sets = group.getAnnounceURLSets();

				for ( TOTorrentAnnounceURLSet set: sets ){

					URL[] urls = set.getAnnounceURLs();

					for ( URL u: urls ){

						addURLToDomainKeySet( tracker_domains, u );
					}
				}

				tracker_keys = domainsToArray( tracker_domains, 8 );

				Set<String>	ws_domains = new HashSet<>();

				List getright = BDecoder.decodeStrings( getURLList( to_torrent, "url-list" ));
				List webseeds = BDecoder.decodeStrings( getURLList( to_torrent, "httpseeds" ));

				for ( List l: new List[]{ getright, webseeds }){

					for ( Object o: l ){

						if ( o instanceof String ){

							try{
								addURLToDomainKeySet( ws_domains, new URL((String)o));

							}catch( Throwable e ){

							}
						}
					}
				}

				ws_keys = domainsToArray( ws_domains, 3 );
			}
		}catch( Throwable e ){
		}

		return( new byte[][]{ tracker_keys, ws_keys });
	}

	protected byte[]
	domainsToArray(
		Set<String>	domains,
		int			max )
	{
		int	entries = Math.min( domains.size(), max );

		if ( entries > 0 ){

			byte[] keys = new byte[ entries*4 ];

			int	pos = 0;

			for ( String dom: domains ){

				int hash = dom.hashCode();

				byte[]	bytes = { (byte)(hash>>24), (byte)(hash>>16),(byte)(hash>>8),(byte)hash };

				System.arraycopy( bytes, 0, keys, pos, 4 );

				pos += 4;
			}

			return( keys );
		}

		return( null );
	}

	protected List
	getURLList(
		TOTorrent	torrent,
		String		key )
	{
		Object obj = torrent.getAdditionalProperty( key );

		if ( obj instanceof byte[] ){

            List l = new ArrayList();

	        l.add(obj);

	        return( l );

		}else if ( obj instanceof List ){

			return (List)BEncoder.clone(obj);

		}else{

			return( new ArrayList());
		}
	}

	private void
	addURLToDomainKeySet(
		Set<String>	set,
		URL			u )
	{
		String prot = u.getProtocol();

		if ( prot != null ){

			if ( prot.equalsIgnoreCase( "http" ) || prot.equalsIgnoreCase( "udp" )){

				String host = u.getHost().toLowerCase( Locale.US );

				if ( host.contains( ":" )){

						// ipv6 raw

					return;
				}

				String[] bits = host.split( "\\." );

				int	len = bits.length;

				if ( len >= 2 ){

					String	end = bits[len-1];

					char[] chars = end.toCharArray();

						// simple check for ipv4 raw

					boolean	all_digits = true;

					for ( char c: chars ){

						if ( !Character.isDigit( c )){

							all_digits = false;

							break;
						}
					}

					if ( !all_digits ){

						set.add( bits[len-2] + "." + end );
					}
				}
			}
		}
	}

	private byte
	getNetworks(
		Download		download )
	{
		String[]	networks = download.getListAttribute( ta_networks );

		if ( networks == null ){

			return( NET_NONE );

		}else{

			return( convertNetworks( networks ));
		}
	}

	public static String[]
	convertNetworks(
		byte		net )
	{
		if ( net == NET_NONE ){
			return( new String[0] );
		}else if ( net == NET_PUBLIC ){
			return( NET_PUBLIC_ARRAY );
		}else if ( net == NET_I2P ){
			return( NET_I2P_ARRAY );
		}else if ( net == NET_TOR ){
			return( NET_TOR_ARRAY );
		}else if ( net == (NET_PUBLIC | NET_I2P )){
			return( NET_PUBLIC_AND_I2P_ARRAY );
		}else{
			List<String>	nets = new ArrayList<>();

			if (( net & NET_PUBLIC ) != 0 ){
				nets.add( AENetworkClassifier.AT_PUBLIC );
			}
			if (( net & NET_I2P ) != 0 ){
				nets.add( AENetworkClassifier.AT_I2P );
			}
			if (( net & NET_TOR ) != 0 ){
				nets.add( AENetworkClassifier.AT_TOR );
			}

			return( nets.toArray( new String[ nets.size()]));
		}
	}

	public static byte
	convertNetworks(
		String[]		networks )
	{
		byte	nets = NET_NONE;

		for (String n : networks) {

			if (n.equalsIgnoreCase( AENetworkClassifier.AT_PUBLIC )){

				nets |= NET_PUBLIC;

			}else if ( n.equalsIgnoreCase( AENetworkClassifier.AT_I2P )){

				nets |= NET_I2P;

			}else if ( n.equalsIgnoreCase( AENetworkClassifier.AT_TOR )){

				nets |= NET_TOR;
			}
		}

		return( nets );
	}

	private Set<String>
	getExplicitTags(
		Download	download )
	{
		Set<String>	all_tags = new HashSet<>();

		if ( tag_manager.isEnabled()){

			String	cat_name = ta_category==null?null:download.getAttribute( ta_category );

			if ( cat_name != null ){

				Tag cat_tag = tag_manager.getTagType( TagType.TT_DOWNLOAD_CATEGORY ).getTag( cat_name, true );

				if ( cat_tag != null && cat_tag.isPublic()){

					all_tags.add( cat_name.toLowerCase( Locale.US ));
				}
			}

			List<Tag> tags = tag_manager.getTagType( TagType.TT_DOWNLOAD_MANUAL ).getTagsForTaggable( PluginCoreUtils.unwrap( download ));

			for ( Tag t: tags ){

				if ( t.isPublic()){

					all_tags.add( t.getTagName( true ).toLowerCase( Locale.US ));
				}
			}
		}
		
		return( all_tags );
	}
	
	private String[]
	getTags(
		Download	download )
	{
		Set<String>	all_tags = new HashSet<>();

		if ( tag_manager.isEnabled()){

			String	cat_name = ta_category==null?null:download.getAttribute( ta_category );

			if ( cat_name != null ){

				Tag cat_tag = tag_manager.getTagType( TagType.TT_DOWNLOAD_CATEGORY ).getTag( cat_name, true );

				if ( cat_tag != null && cat_tag.isPublic()){

					all_tags.add( cat_name.toLowerCase( Locale.US ));
				}
			}

			List<Tag> tags = tag_manager.getTagType( TagType.TT_DOWNLOAD_MANUAL ).getTagsForTaggable( PluginCoreUtils.unwrap( download ));

			for ( Tag t: tags ){

				if ( t.isPublic()){

					all_tags.add( t.getTagName( true ).toLowerCase( Locale.US ));
				}
			}
		}

		String[]	networks = download.getListAttribute( ta_networks );

		for ( String network: networks ){

			if ( !network.equals( "Public" )){

				if ( AEPluginProxyHandler.hasPluginProxyForNetwork( network, true )){

					all_tags.add( "_" + network.toLowerCase( Locale.US ) + "_" );
				}
			}
		}
		
		try{
			TOTorrent torrent = PluginCoreUtils.unwrap( download.getTorrent());
			
			int tt = torrent.getTorrentType();
			
			if ( tt == TOTorrent.TT_V1_V2 ){
				
				all_tags.add( "_hybrid_" );
				
			}else  if ( tt == TOTorrent.TT_V2 ){
				
				all_tags.add( "_v2_" );
			}
		}catch( Throwable e ){	
		}
		
		if ( all_tags.size() == 0 ){

			return( null );

		}else if ( all_tags.size() == 1 ){

			return( new String[]{ all_tags.iterator().next()});

		}else{

			List<String> temp = new ArrayList<>(all_tags);

			Collections.shuffle( temp );

			return( temp.toArray( new String[ temp.size()] ));
		}
	}

	private static final int MAX_TAG_LENGTH			= 20;
	private static final int MAX_TAGS_TOTAL_LENGTH 	= 64;

	protected byte[]
	encodeTags(
		String[]		tags )
	{
		if ( tags == null || tags.length == 0 ){

			return( null );
		}

		byte[]	temp 	= new byte[MAX_TAGS_TOTAL_LENGTH];
		int		pos		= 0;
		int		rem		= temp.length;

		for ( int i=0;i<tags.length;i++){

			String tag = tags[i];

			tag = truncateTag( tag );

			try{
				byte[] tag_bytes = tag.getBytes( "UTF-8" );

				int	tb_len = tag_bytes.length;

				if ( rem < tb_len + 1 ){

					break;
				}

				temp[pos++] = (byte)tb_len;

				System.arraycopy( tag_bytes, 0, temp, pos, tb_len );

				pos += tb_len;
				rem	-= (tb_len+1);

			}catch( Throwable e ){

			}
		}

		if ( pos == 0 ){

			return( null );

		}else{

			byte[] result = new byte[pos];

			System.arraycopy( temp, 0, result, 0, pos );

			return( result );
		}
	}

	protected String
	truncateTag(
		String	tag )
	{
		if ( tag.length() > MAX_TAG_LENGTH ){

			tag = tag.substring( 0, MAX_TAG_LENGTH );
		}

		while( tag.length() > 0 ){

			try{
				byte[] tag_bytes = tag.getBytes( "UTF-8" );

				if ( tag_bytes.length <= MAX_TAG_LENGTH ){

					break;

				}else{

					tag = tag.substring( 0, tag.length() - 1 );
				}

			}catch( Throwable e ){

				break;
			}
		}

		return( tag );
	}

	protected String[]
	decodeTags(
		byte[]		bytes )
	{
		if ( bytes == null || bytes.length == 0 ){

			return( null );
		}

		List<String>	tags = new ArrayList<>(10);

		int	pos = 0;

		while( pos < bytes.length ){

			int	tag_len = bytes[pos++]&0x000000ff;

			if ( tag_len > MAX_TAG_LENGTH ){

				break;
			}

			try{
				tags.add( new String( bytes, pos, tag_len, "UTF-8" ));

				pos += tag_len;

			}catch( Throwable e ){

				break;
			}
		}

		if ( tags.size() == 0 ){

			return( null );

		}else{

			return( tags.toArray( new String[ tags.size()] ));
		}
	}

	private static final int PD_BLOOM_INITIAL_SIZE		= 1000;
	private static final int PD_BLOOM_INCREMENT_SIZE	= 1000;


	private BloomFilter	persist_del_bloom;

	protected byte[]
	getPermDelKey(
		RelatedContent	info )
	{
		byte[]	bytes = info.getHash();

		if ( bytes == null ){

			try{
				bytes = new SHA1Simple().calculateHash( getPrivateInfoKey(info).getBytes( "ISO-8859-1" ));

			}catch( Throwable e ){

				Debug.out( e );

				return( null );
			}
		}

		byte[] key = new byte[8];

		System.arraycopy( bytes, 0, key, 0, 8 );

		return( key );
	}

	protected List<byte[]>
	loadPersistentlyDeleted()
	{
		List<byte[]> entries = null;

		if ( FileUtil.resilientConfigFileExists( PERSIST_DEL_FILE )){

			Map<String,Object> map = (Map<String,Object>)FileUtil.readResilientConfigFile( PERSIST_DEL_FILE );

			entries = (List<byte[]>)map.get( "entries" );
		}

		if ( entries == null ){

			entries = new ArrayList<>(0);
		}

		return( entries );
	}

	protected void
	addPersistentlyDeleted(
		RelatedContent[]	content )
	{
		if ( content.length == 0 ){

			return;
		}

		List<byte[]> entries = loadPersistentlyDeleted();

		List<byte[]> new_keys = new ArrayList<>(content.length);

		for ( RelatedContent rc: content ){

			byte[] key = getPermDelKey( rc );

			new_keys.add( key );

			entries.add( key );
		}

		Map<String,Object>	map = new HashMap<>();

		map.put( "entries", entries );

		FileUtil.writeResilientConfigFile( PERSIST_DEL_FILE, map );

		if ( persist_del_bloom != null ){

			if ( persist_del_bloom.getSize() / ( persist_del_bloom.getEntryCount() + content.length ) < 10 ){

				persist_del_bloom = BloomFilterFactory.createAddOnly( Math.max( PD_BLOOM_INITIAL_SIZE, persist_del_bloom.getSize() *10 + PD_BLOOM_INCREMENT_SIZE + content.length  ));

				for ( byte[] k: entries ){

					persist_del_bloom.add( k );
				}
			}else{

				for ( byte[] k: new_keys ){

					persist_del_bloom.add( k );
				}
			}
		}
	}
	
	protected boolean
	globalFilter(
		RelatedContent		content )
	{
		if ( global_filter_active_only ){
			
			if ( content.getSeeds() <= 0 && content.getLeechers() <= 0 ){
				
				return( true );
			}
		}
		
		return( false );
	}

	protected boolean
	isPersistentlyDeleted(
		RelatedContent		content )
	{
		if ( persist_del_bloom == null ){

			List<byte[]> entries = loadPersistentlyDeleted();

			persist_del_bloom = BloomFilterFactory.createAddOnly( Math.max( PD_BLOOM_INITIAL_SIZE, entries.size()*10 + PD_BLOOM_INCREMENT_SIZE ));

			for ( byte[] k: entries ){

				persist_del_bloom.add( k );
			}
		}

		byte[]	key = getPermDelKey( content );

		return( persist_del_bloom.contains( key ));
	}

	protected void
	resetPersistentlyDeleted()
	{
		FileUtil.deleteResilientConfigFile( PERSIST_DEL_FILE );

		persist_del_bloom = BloomFilterFactory.createAddOnly( PD_BLOOM_INITIAL_SIZE );
	}

	public void
	reserveTemporarySpace()
	{
		temporary_space.addAndGet( TEMPORARY_SPACE_DELTA );
	}

	public void
	releaseTemporarySpace()
	{
		boolean	reset_explicit = temporary_space.addAndGet( -TEMPORARY_SPACE_DELTA ) == 0;

		enforceMaxResults( reset_explicit );
	}

	protected void
	enforceMaxResults(
		boolean reset_explicit )
	{
		synchronized( rcm_lock ){

			ContentCache	content_cache = loadRelatedContent();

			enforceMaxResults( content_cache, reset_explicit );
		}
	}

	protected void
	enforceMaxResults(
		ContentCache		content_cache,
		boolean				reset_explicit )
	{
		Map<String,DownloadInfo>		related_content			= content_cache.related_content;

		int num_to_remove = related_content.size() - ( max_results + temporary_space.get());

		if ( num_to_remove > 0 ){

			List<DownloadInfo>	infos = new ArrayList<>(related_content.values());

			if ( reset_explicit ){

				for ( DownloadInfo info: infos ){

					if ( info.isExplicit()){

						info.setExplicit( false );
					}
				}
			}

			Collections.sort(
				infos,
				new Comparator<DownloadInfo>()
				{
					@Override
					public int
					compare(
						DownloadInfo o1,
						DownloadInfo o2)
					{
						int res = o2.getLevel() - o1.getLevel();

						if ( res != 0 ){

							return( res );
						}

						res = o1.getRank() - o2.getRank();

						if ( res != 0 ){

							return( res );
						}

						return( o1.getLastSeenSecs() - o2.getLastSeenSecs());
					}
				});

			List<RelatedContent> to_remove = new ArrayList<>();

			for (int i=0;i<Math.min( num_to_remove, infos.size());i++ ){

				to_remove.add( infos.get(i));
			}

			if ( to_remove.size() > 0 ){

				delete( to_remove.toArray( new RelatedContent[to_remove.size()]), content_cache, false );
			}
		}
	}

	public void
	addListener(
		RelatedContentManagerListener		listener )
	{
		listeners.add( listener );
	}

	public void
	removeListener(
		RelatedContentManagerListener		listener )
	{
		listeners.remove( listener );
	}

	protected static class
	ByteArrayHashMapEx<T>
		extends ByteArrayHashMap<T>
	{
	    public T
	    getRandomValueExcluding(
	    	T	excluded )
	    {
	    	int	num = RandomUtils.nextInt( size );

	    	T result = null;

	        for (int j = 0; j < table.length; j++) {

		         Entry<T> e = table[j];

		         while( e != null ){

	              	T value = e.value;

	              	if ( value != excluded ){

	              		result = value;
	              	}

	              	if ( num <= 0 && result != null ){

	              		return( result );
	              	}

	              	num--;

	              	e = e.next;
		        }
		    }

	        return( result );
	    }
	}

	private Map<String,Object>
	serialiseDI(
		DownloadInfo			info,
		ContentCache			cc )
	{
		try{
			Map<String,Object> info_map = new HashMap<>();

			MapUtils.exportLong( info_map, "v", info.getVersion());

			info_map.put( "h", info.getHash());

			MapUtils.setMapString( info_map, "d", info.getTitle());
			MapUtils.exportInt( info_map, "r", info.getRand());
			MapUtils.setMapString( info_map, "t", info.getTracker());
			MapUtils.exportLong( info_map, "z", info.getSize());

			MapUtils.exportInt( info_map, "p", (int)( info.getPublishDate()/(60*60*1000)));
			MapUtils.exportInt( info_map, "q", (info.getSeeds()<<16)|(info.getLeechers()&0xffff));

			byte[] tracker_keys = info.getTrackerKeys();
			if ( tracker_keys != null ){
				info_map.put( "k", tracker_keys );
			}

			byte[] ws_keys = info.getWebSeedKeys();
			if ( ws_keys != null ){
				info_map.put( "w", ws_keys );
			}

			String[] tags = info.getTags();
			if ( tags != null ){
				info_map.put( "g", encodeTags(tags));
			}

			byte nets = info.getNetworksInternal();
			if (nets != NET_PUBLIC ){
				info_map.put( "o", new Long(nets&0x00ff ));
			}

			if ( cc != null ){

				MapUtils.exportBooleanAsLong( info_map, "u", info.isUnread());
				MapUtils.exportIntArrayAsByteArray( info_map, "l", info.getRandList());
				MapUtils.exportInt( info_map, "f", info.getFirstSeenSecs());
				MapUtils.exportInt( info_map, "s", info.getLastSeenSecs());
				MapUtils.exportInt( info_map, "e", info.getLevel());
			}

			MapUtils.exportLong(info_map, "cl", info.getChangedLocallyOn());

			return( info_map );

		}catch( Throwable e ){

			Debug.out( e );

			return( null );
		}
	}

	private DownloadInfo
	deserialiseDI(
		Map<String,Object>		info_map,
		ContentCache			cc )
	{
		try{
			int		version	= (int) MapUtils.importLong( info_map, "v", RelatedContent.VERSION_INITIAL );
			byte[]	hash 	= (byte[])info_map.get("h");
			String	title	= MapUtils.getMapString( info_map, "d", null );
			int		rand	= MapUtils.importInt( info_map, "r" , 0);
			String	tracker	= MapUtils.getMapString( info_map, "t", null );
			long	size	= MapUtils.importLong( info_map, "z", 0 );

			int		date 			=  MapUtils.importInt( info_map, "p", 0 );
			int		seeds_leechers 	=  MapUtils.importInt( info_map, "q", -1 );
			byte[]	tracker_keys	= (byte[])info_map.get( "k");
			byte[]	ws_keys			= (byte[])info_map.get( "w" );
			long lastChangedLocally = MapUtils.importLong( info_map, "cl", 0 );

			if ( tracker_keys != null && tracker_keys.length % 4 != 0 ){

				tracker_keys = null;
			}

			if ( ws_keys != null && ws_keys.length % 4 != 0 ){

				ws_keys = null;
			}

			byte[]	_tags	= (byte[])info_map.get( "g" );

			String[] tags = decodeTags( _tags );

			Long _nets = (Long)info_map.get( "o" );

			byte nets = _nets==null?NET_PUBLIC:_nets.byteValue();

			int first_seen = MapUtils.importInt( info_map, "f", 0 );

			if ( cc == null ){

				 DownloadInfo info = new DownloadInfo( version, hash, hash, title, rand, tracker, tracker_keys, ws_keys, tags, nets, first_seen, 0, false, size, date, seeds_leechers );

				 info.setChangedLocallyOn( lastChangedLocally );

				 return( info );

			}else{

				boolean unread = MapUtils.getMapBoolean( info_map, "u", false );

				int[] rand_list = MapUtils.importIntArray( info_map, "l" );

				int	last_seen = MapUtils.importInt( info_map, "s", 0 );

				int	level = MapUtils.importInt( info_map, "e", 0 );

				DownloadInfo info = new DownloadInfo( version, hash, title, rand, tracker, tracker_keys, ws_keys, tags, nets, unread, rand_list, first_seen, last_seen, level, size, date, seeds_leechers, cc );

				info.setChangedLocallyOn( lastChangedLocally );

				return( info );
			}
		}catch( Throwable e ){

			Debug.out( e );

			return( null );
		}
	}

	private void
	dump()
	{
		RelatedContent[] related_content = getRelatedContent();

		ByteArrayHashMap<List<String>>	tk_map = new ByteArrayHashMap<>();
		ByteArrayHashMap<List<String>>	ws_map = new ByteArrayHashMap<>();

		for ( RelatedContent rc: related_content ){

			byte[] tracker_keys = rc.getTrackerKeys();

			if ( tracker_keys != null ){

				for (int i=0;i<tracker_keys.length;i+=4 ){

					byte[] tk = new byte[4];

					System.arraycopy( tracker_keys, i, tk, 0, 4 );

					List<String> titles = tk_map.get( tk );

					if ( titles == null ){

						titles = new ArrayList<>();

						tk_map.put( tk, titles );
					}

					titles.add( rc.getTitle());
				}
			}
			byte[] ws_keys = rc.getWebSeedKeys();

			if ( ws_keys != null ){

				for (int i=0;i<ws_keys.length;i+=4 ){

					byte[] wk = new byte[4];

					System.arraycopy( ws_keys, i, wk, 0, 4 );

					List<String> titles = ws_map.get( wk );

					if ( titles == null ){

						titles = new ArrayList<>();

						ws_map.put( wk, titles );
					}

					titles.add( rc.getTitle());
				}
			}
		}

		System.out.println( "-- Trackers --" );

		for ( byte[] key: tk_map.keys()){

			List<String>	titles = tk_map.get( key );

			System.out.println( ByteFormatter.encodeString( key ));

			for ( String title: titles ){

				System.out.println( "    " + title );
			}
		}

		System.out.println( "-- Web Seeds --" );

		for ( byte[] key: ws_map.keys()){

			List<String>	titles = ws_map.get( key );

			System.out.println( ByteFormatter.encodeString( key ));

			for ( String title: titles ){

				System.out.println( "    " + title );
			}
		}
	}

	protected class
	DownloadInfo
		extends RelatedContent
	{
		final private int		rand;

		final private int 		first_seen;
		
		private boolean			unread	= true;
		private int[]			rand_list;
		private int				last_seen;
		private int				level;
		private boolean			explicit;

			// we *need* this reference here to manage garbage collection correctly

		private ContentCache	cc;

		protected
		DownloadInfo(
			int			_version,
			byte[]		_related_to,
			byte[]		_hash,
			String		_title,
			int			_rand,
			String		_tracker,
			byte[]		_tracker_keys,
			byte[]		_ws_keys,
			String[]	_tags,
			byte		_nets,
			int			_first_seen,
			int			_level,
			boolean		_explicit,
			long		_size,
			int			_date,
			int			_seeds_leechers )
		{
			super( _version, _related_to, _title, _hash, _tracker, _tracker_keys, _ws_keys, _tags, _nets, _size, _date, _seeds_leechers);

			first_seen	= _first_seen;
			rand		= _rand;
			level		= _level;
			explicit	= _explicit;

			updateLastSeen();
		}

		protected
		DownloadInfo(
			int				_version,
			byte[]			_hash,
			String			_title,
			int				_rand,
			String			_tracker,
			byte[]			_tracker_keys,
			byte[]			_ws_keys,
			String[]		_tags,
			byte			_nets,
			boolean			_unread,
			int[]			_rand_list,
			int				_first_seen,
			int				_last_seen,
			int				_level,
			long			_size,
			int				_date,
			int				_seeds_leechers,
			ContentCache	_cc )
		{
			super( _version, _title, _hash, _tracker, _tracker_keys, _ws_keys, _tags, _nets, _size, _date, _seeds_leechers);

			first_seen	= _first_seen;
			rand		= _rand;
			unread		= _unread;
			rand_list	= _rand_list;
			last_seen	= _last_seen;
			level		= _level;
			cc			= _cc;

			if ( rand_list != null ){

				if ( rand_list.length > MAX_RANK ){

					int[] temp = new int[ MAX_RANK ];

					System.arraycopy( rand_list, 0, temp, 0, MAX_RANK );

					rand_list = temp;
				}
			}
		}

		protected boolean
		addInfo(
			DownloadInfo		info )
		{
			boolean	result = false;

			synchronized( this ){

				updateLastSeen();

				int r = info.getRand();

				if ( rand_list == null ){

					rand_list = new int[]{ r };

					result	= true;

				}else{

					boolean	match = false;

					for (int i=0;i<rand_list.length;i++){

						if ( rand_list[i] == r ){

							match = true;

							break;
						}
					}

					if ( !match && rand_list.length < MAX_RANK ){

						int	len = rand_list.length;

						int[]	new_rand_list = new int[len+1];

						System.arraycopy( rand_list, 0, new_rand_list, 0, len );

						new_rand_list[len] = r;

						rand_list = new_rand_list;

						result = true;
					}
				}
			}

			if ( info.getVersion() > getVersion()){

				setVersion( info.getVersion());

				result = true;
			}

			if ( info.getLevel() < level ){

				level = info.getLevel();

				result = true;
			}

			if ( info.getVersion() >= getVersion()){

					// don't update seeds/leechers with older version (less accurate) values

				int sl = info.getSeedsLeechers();

				if ( sl != -1 && sl != getSeedsLeechers()){

					setSeedsLeechers( sl );

					result = true;
				}
			}

			int	d = info.getDateHours();

			if ( d > 0 && getDateHours() == 0 ){

				setDateHours( d );

				result = true;
			}

			String[] other_tags = info.getTags();

			if ( other_tags != null && other_tags.length > 0 ){

				String[] existing_tags = getTags();

				if ( existing_tags == NO_TAGS ){

					setTags( other_tags );

					result = true;

				}else{

					boolean	same;

					if ( other_tags.length == existing_tags.length ){

						if ( existing_tags.length == 1 ){

							same = other_tags[0].equals( existing_tags[0] );

						}else{

							same = true;

							for ( int i=0;i<existing_tags.length;i++ ){

								String e_tag = existing_tags[i];

								boolean	found = false;

								for ( int j=0;j<other_tags.length;j++){

									if ( e_tag.equals( other_tags[j])){

										found = true;

										break;
									}
								}

								if ( !found ){

									same = false;

									break;
								}
							}
						}
					}else{

						same = false;
					}

					if ( !same ){

						Set<String>	tags = new HashSet<>();

						Collections.addAll(tags, existing_tags);
						Collections.addAll(tags, other_tags);

						setTags( tags.toArray( new String[tags.size()]));

						result = true;
					}
				}
			}

			byte	other_nets 		= info.getNetworksInternal();
			byte	existing_nets	= getNetworksInternal();

			if ( other_nets != existing_nets ){

				setNetworksInternal((byte)( other_nets | existing_nets ));

				result = true;
			}

			if (result) {
				setChangedLocallyOn(0);
			}
			return( result );
		}

		@Override
		public int
		getLevel()
		{
			return( level );
		}

		protected boolean
		isExplicit()
		{
			return( explicit );
		}

		protected void
		setExplicit(
			boolean		b )
		{
			explicit	= b;
		}

		protected void
		updateLastSeen()
		{
				// persistence of this is piggy-backed on other saves to limit resource usage
				// only therefore a vague measure

			last_seen	= (int)( SystemTime.getCurrentTime()/1000 );
		}

		@Override
		public int
		getRank()
		{
			return( rand_list==null?0:rand_list.length );
		}

		@Override
		public boolean
		isUnread()
		{
			return( unread );
		}

		protected void
		setPublic(
			ContentCache	_cc )
		{
			cc	= _cc;

			if ( unread ){

				incrementUnread();
			}

			rand_list = new int[]{ rand };
			setChangedLocallyOn(0);
		}

		@Override
		public int
		getLastSeenSecs()
		{
			return( last_seen );
		}

		@Override
		public int
		getFirstSeenSecs()
		{
			return( first_seen );
		}
		
		protected void
		setUnreadInternal(
			boolean	_unread )
		{
			synchronized( this ){

				unread = _unread;
			}
		}

		@Override
		public void
		setUnread(
			boolean	_unread )
		{
			boolean	changed = false;

			synchronized( this ){

				if ( unread != _unread ){

					unread = _unread;

					changed = true;
				}
			}

			if ( changed ){

				if ( _unread ){

					incrementUnread();

				}else{

					decrementUnread();
				}

				setChangedLocallyOn(0);
				contentChanged( this );
			}
		}

		protected int
		getRand()
		{
			return( rand );
		}

		protected int[]
		getRandList()
		{
			return( rand_list );
		}

		@Override
		public Download
		getRelatedToDownload()
		{
			try{
				return( getDownload( getRelatedToHash()));

			}catch( Throwable e ){

				Debug.out( e );

				return( null );
			}
		}

		@Override
		public void
		delete()
		{
			setChangedLocallyOn(0);
			RelatedContentManager.this.delete( new RelatedContent[]{ this });
		}

		@Override
		public String
		getString()
		{
			return( super.getString() + ", " + rand + ", rl=" + Arrays.toString(rand_list) + ", last_seen=" + last_seen + ", level=" + level );
		}
	}

	// can't move this class out of here as the key for the transfer type is based on its
	// class name...
	// note there is migration for this in DDBaseHelpers::xfer_migration
	
	protected static class
	RCMSearchXFer
		implements DistributedDatabaseTransferType
	{
	}

	protected static class
	RCMSearchXFerBiglyBT
		implements DistributedDatabaseTransferType
	{
	}
	
	protected static class
	ContentCache
	{
		protected Map<String,DownloadInfo>						related_content			= new HashMap<>();
		protected ByteArrayHashMapEx<ArrayList<DownloadInfo>>	related_content_map		= new ByteArrayHashMapEx<>();
	}

	private static class
	SecondaryLookup
	{
		final private byte[]	hash;
		final private int		level;
		final private byte		nets;

		SecondaryLookup(
			byte[]		_hash,
			int			_level,
			byte		_nets )
		{
			hash	= _hash;
			level	= _level;
			nets	= _nets;
		}

		byte[]
		getHash()
		{
			return( hash );
		}

		int
		getLevel()
		{
			return( level );
		}

		byte
		getNetworks()
		{
			return( nets );
		}
	}
}
