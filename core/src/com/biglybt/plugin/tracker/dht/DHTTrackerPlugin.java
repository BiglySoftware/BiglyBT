/*
 * Created on 31-Jan-2005
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

package com.biglybt.plugin.tracker.dht;


import java.net.InetSocketAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.networkmanager.NetworkManager;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.PEPeerSource;
import com.biglybt.core.tracker.TrackerPeerSource;
import com.biglybt.core.tracker.TrackerPeerSourceAdapter;
import com.biglybt.core.tracker.protocol.PRHelpers;
import com.biglybt.core.util.*;
import com.biglybt.pif.Plugin;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.PluginListener;
import com.biglybt.pif.download.*;
import com.biglybt.pif.logging.LoggerChannel;
import com.biglybt.pif.logging.LoggerChannelListener;
import com.biglybt.pif.peers.Peer;
import com.biglybt.pif.peers.PeerManager;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.torrent.TorrentAttribute;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.config.*;
import com.biglybt.pif.ui.model.BasicPluginConfigModel;
import com.biglybt.pif.ui.model.BasicPluginViewModel;
import com.biglybt.pif.utils.DelayedTask;
import com.biglybt.pif.utils.UTTimerEvent;
import com.biglybt.pif.utils.UTTimerEventPerformer;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.plugin.I2PHelpers;
import com.biglybt.plugin.dht.DHTPlugin;
import com.biglybt.plugin.dht.DHTPluginContact;
import com.biglybt.plugin.dht.DHTPluginOperationListener;
import com.biglybt.plugin.dht.DHTPluginValue;
import com.biglybt.util.StringCompareUtils;

/**
 * @author parg
 *
 */

public class
DHTTrackerPlugin
	implements Plugin, DownloadListener, DownloadAttributeListener, DownloadTrackerListener
{
	public static Object	DOWNLOAD_USER_DATA_I2P_SCRAPE_KEY	= new Object();

	private static final String	PLUGIN_NAME				= "Distributed Tracker";
	private static final String PLUGIN_CONFIGSECTION_ID = "plugins.dhttracker";
	private static final String PLUGIN_RESOURCE_ID		= "ConfigView.section.plugins.dhttracker";

	private static final int	ANNOUNCE_TIMEOUT			= 2*60*1000;
	private static final int	ANNOUNCE_DERIVED_TIMEOUT	= 60*1000;	// spend less time on these

	private static final int	ANNOUNCE_MIN_DEFAULT		= 2*60*1000;
	private static final int	ANNOUNCE_MAX				= 60*60*1000;
	private static final int	ANNOUNCE_MAX_DERIVED_ONLY	= 30*60*1000;

	private static final Object SCRAPE_DATA_KEY				= new Object();
	private static final int	SCRAPE_TIMEOUT				= 30*1000;
	private static final int	SCRAPE_MIN_DEFAULT			= 30*60*1000;

	private static final int	INTERESTING_CHECK_PERIOD		= 4*60*60*1000;
	private static final int	INTERESTING_INIT_RAND_OURS		=    5*60*1000;
	private static final int	INTERESTING_INIT_MIN_OURS		=    2*60*1000;
	private static final int	INTERESTING_INIT_RAND_OTHERS	=   30*60*1000;
	private static final int	INTERESTING_INIT_MIN_OTHERS		=    5*60*1000;

	private static final int	INTERESTING_DHT_CHECK_PERIOD	= 1*60*60*1000;
	private static final int	INTERESTING_DHT_INIT_RAND		=    5*60*1000;
	private static final int	INTERESTING_DHT_INIT_MIN		=    2*60*1000;


	private static final int	INTERESTING_AVAIL_MAX		= 8;	// won't pub if more
	private static final int	INTERESTING_PUB_MAX_DEFAULT	= 30;	// limit on pubs

	private static final int	MAX_ACTIVE_DHT_GETS		= 8;
	private static final int	MAX_ACTIVE_DHT_REMOVES	= 5;
	private static final int	MAX_ACTIVE_DHT_PUTS		= 5;
	private static final int	MAX_ACTIVE_DHT_SCRAPES	= 3;
	
	private static final int	REG_TYPE_NONE			= 1;
	private static final int	REG_TYPE_FULL			= 2;
	private static final int	REG_TYPE_DERIVED		= 3;

	private static final int	LIMITED_TRACK_SIZE		= 16;

	private static final boolean	TRACK_NORMAL_DEFAULT	= true;
	private static final boolean	TRACK_LIMITED_DEFAULT	= true;

	private static final boolean	TEST_ALWAYS_TRACK		= false;

	public static final int	NUM_WANT			= 30;	// Limit to ensure replies fit in 1 packet

	private static final long	start_time = SystemTime.getCurrentTime();

	private static final Object	DL_DERIVED_METRIC_KEY		= new Object();
	private static final int	DL_DERIVED_MIN_TRACK		= 5;
	private static final int	DL_DERIVED_MAX_TRACK		= 20;
	private static final int	DIRECT_INJECT_PEER_MAX		= 5;

	private static final Object LATEST_REGISTER_REASON	= new Object();
	
	//private static final boolean ADD_ASN_DERIVED_TARGET			= false;
	//private static final boolean ADD_NETPOS_DERIVED_TARGETS		= false;

	private static URL	DEFAULT_URL;

	static{
		try{
			DEFAULT_URL = new URL( "dht:" );

		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}
	}

	private PluginInterface			plugin_interface;
	private BasicPluginViewModel 	model;
	private DHTPlugin dht;

	private TorrentAttribute 	ta_networks;
	private TorrentAttribute 	ta_peer_sources;

	private Map<Download,Long>		interesting_downloads 	= new HashMap<>();
	private Set<Download>			interesting_published	= new IdentityHashSet<>();
	private int						interesting_pub_max		= INTERESTING_PUB_MAX_DEFAULT;
	private Map<Download,int[]>		running_downloads 		= new HashMap<>();
	private Map<Download,int[]>		run_data_cache	 		= new HashMap<>();
	private Map<Download,RegistrationDetails>	registered_downloads 	= new HashMap<>();

	private Map<Download,Boolean>	limited_online_tracking	= new HashMap<>();
	private Map<Download,Long>		query_map			 	= new HashMap<>();

	private Map<Download,Integer>	in_progress				= new HashMap<>();

		// external config to limit plugin op to pure decentralised only

	private boolean				track_only_decentralsed = COConfigurationManager.getBooleanParameter( "dhtplugin.track.only.decentralised", false );

	private BooleanParameter	track_normal_when_offline;
	private BooleanParameter	track_limited_when_online;

	private long				current_announce_interval = ANNOUNCE_MIN_DEFAULT;

	private LoggerChannel		log;

	private Map<Download,int[]>					scrape_injection_map = new WeakHashMap<>();

	private Random				random = new Random();
	private volatile boolean	is_running;
	private volatile boolean	closing	= false;

	private AtomicInteger		dht_gets_active			= new AtomicInteger();
	private AtomicInteger		dht_puts_active			= new AtomicInteger();
	private AtomicInteger		dht_removes_active		= new AtomicInteger();
	
	private AtomicInteger		dht_scrapes_active		= new AtomicInteger();
	private AtomicInteger		dht_scrapes_complete	= new AtomicInteger();
	
	private AEMonitor			this_mon	= new AEMonitor( "DHTTrackerPlugin" );

	//private DHTNetworkPosition[]	current_network_positions;
	//private long					last_net_pos_time;

	private AESemaphore			initialised_sem = new AESemaphore( "DHTTrackerPlugin:init" );

	private DHTTrackerPluginAlt	alt_lookup_handler;

	private boolean				disable_put;

	{
		COConfigurationManager.addAndFireParameterListeners(
			new String[]{
				"Enable.Proxy",
				"Enable.SOCKS",
			},
			new com.biglybt.core.config.ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					String parameter_name )
				{
					boolean	enable_proxy 	= COConfigurationManager.getBooleanParameter("Enable.Proxy");
				    boolean enable_socks	= COConfigurationManager.getBooleanParameter("Enable.SOCKS");

				    disable_put = enable_proxy && enable_socks;
				}
			});
	}

	public static void
	load(
		PluginInterface		plugin_interface )
	{
		plugin_interface.getPluginProperties().setProperty( "plugin.version", 	"1.0" );
		plugin_interface.getPluginProperties().setProperty( "plugin.name", 		PLUGIN_NAME );
	}

	@Override
	public void
	initialize(
		PluginInterface 	_plugin_interface )
	{
		plugin_interface	= _plugin_interface;

		log = plugin_interface.getLogger().getTimeStampedChannel(PLUGIN_NAME);

		ta_networks 	= plugin_interface.getTorrentManager().getAttribute( TorrentAttribute.TA_NETWORKS );
		ta_peer_sources = plugin_interface.getTorrentManager().getAttribute( TorrentAttribute.TA_PEER_SOURCES );

		UIManager	ui_manager = plugin_interface.getUIManager();

		model =
			ui_manager.createBasicPluginViewModel( PLUGIN_RESOURCE_ID );

		model.setConfigSectionID(PLUGIN_CONFIGSECTION_ID);

		BasicPluginConfigModel	config =
			ui_manager.createBasicPluginConfigModel( ConfigSection.SECTION_PLUGINS,
					PLUGIN_CONFIGSECTION_ID);

		track_normal_when_offline = config.addBooleanParameter2( "dhttracker.tracknormalwhenoffline", "dhttracker.tracknormalwhenoffline", TRACK_NORMAL_DEFAULT );

		track_limited_when_online = config.addBooleanParameter2( "dhttracker.tracklimitedwhenonline", "dhttracker.tracklimitedwhenonline", TRACK_LIMITED_DEFAULT );

		track_limited_when_online.addListener(
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					Parameter	param )
				{
					configChanged();
				}
			});

		track_normal_when_offline.addListener(
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					Parameter	param )
				{
					track_limited_when_online.setEnabled( track_normal_when_offline.getValue());

					configChanged();
				}
			});

		if ( !track_normal_when_offline.getValue()){

			track_limited_when_online.setEnabled( false );
		}

		interesting_pub_max = plugin_interface.getPluginconfig().getPluginIntParameter( "dhttracker.presencepubmax", INTERESTING_PUB_MAX_DEFAULT );


		if ( !TRACK_NORMAL_DEFAULT ){
			// should be TRUE by default
			System.out.println( "**** DHT Tracker default set for testing purposes ****" );
		}

		BooleanParameter	enable_alt 	= config.addBooleanParameter2( "dhttracker.enable_alt", "dhttracker.enable_alt", true );

		IntParameter 		alt_port 	= config.addIntParameter2( "dhttracker.alt_port", "dhttracker.alt_port", 0, 0, 65535 );

		enable_alt.addEnabledOnSelection( alt_port );

		config.createGroup( "dhttracker.alt_group", new Parameter[]{ enable_alt,alt_port });

		if ( enable_alt.getValue()){

			alt_lookup_handler = new DHTTrackerPluginAlt( alt_port.getValue());
		}

		model.getActivity().setVisible( false );
		model.getProgress().setVisible( false );

		model.getLogArea().setMaximumSize( 80000 );

		log.addListener(
				new LoggerChannelListener()
				{
					@Override
					public void
					messageLogged(
						int		type,
						String	message )
					{
						model.getLogArea().appendText( message+"\n");
					}

					@Override
					public void
					messageLogged(
						String		str,
						Throwable	error )
					{
						model.getLogArea().appendText( error.toString()+"\n");
					}
				});

		model.getStatus().setText( MessageText.getString( "ManagerItem.initializing" ));

		log.log( "Waiting for Distributed Database initialisation" );

		plugin_interface.addListener(
			new PluginListener()
			{
				@Override
				public void
				initializationComplete()
				{
					boolean	release_now = true;

					try{
						final PluginInterface dht_pi =
							plugin_interface.getPluginManager().getPluginInterfaceByClass(
										DHTPlugin.class );

						if ( dht_pi != null ){

							dht = (DHTPlugin)dht_pi.getPlugin();

							final DelayedTask dt =
								plugin_interface.getUtilities().createDelayedTask(
									new Runnable()
									{

										@Override
										public void
										run()
										{
											AEThread2	t =
												new AEThread2( "DHTTrackerPlugin:init", true )
												{
													@Override
													public void
													run()
													{
														try{

															if ( dht.isEnabled()){

																log.log( "DDB Available" );

																model.getStatus().setText( MessageText.getString( "DHTView.activity.status.false" ));

																initialise();

															}else{

																log.log( "DDB Disabled" );

																model.getStatus().setText( MessageText.getString( "dht.status.disabled" ));

																notRunning();
															}
														}catch( Throwable e ){

															log.log( "DDB Failed", e );

															model.getStatus().setText( MessageText.getString( "DHTView.operations.failed" ));

															notRunning();

														}finally{

															initialised_sem.releaseForever();
														}
													}
												};

												t.start();
										}
									});

							dt.queue();

							release_now = false;

						}else{

							log.log( "DDB Plugin missing" );

							model.getStatus().setText( MessageText.getString( "DHTView.operations.failed" ) );

							notRunning();
						}
					}finally{

						if ( release_now ){

							initialised_sem.releaseForever();
						}
					}
				}

				@Override
				public void
				closedownInitiated()
				{
					closing = true;
				}

				@Override
				public void
				closedownComplete()
				{

				}
			});
	}

	protected void
	notRunning()
	{
		plugin_interface.getDownloadManager().addListener(
				new DownloadManagerListener()
				{
					@Override
					public void
					downloadAdded(
						final Download	download )
					{
						addDownload( download );
					}

					@Override
					public void
					downloadRemoved(
						Download	download )
					{
						removeDownload( download );
					}
				});
	}

	protected void
	initialise()
	{
		is_running	= true;

		plugin_interface.getDownloadManager().addListener(
				new DownloadManagerListener()
				{
					@Override
					public void
					downloadAdded(
						Download download )
					{
						addDownload( download );
					}

					@Override
					public void
					downloadRemoved(
						Download download )
					{
						removeDownload( download );
					}
				});

		plugin_interface.getUtilities().createTimer("DHT Tracker", true ).addPeriodicEvent(
			15000,
			new UTTimerEventPerformer()
			{
				private int	ticks;

				private String 	prev_alt_status = "";

				@Override
				public void
				perform(
					UTTimerEvent event)
				{
					ticks++;

					processRegistrations( ticks%8==0 );

					if ( !closing ){
						
						processNonRegistrations( ticks == 2 || ticks%4==0, true, ticks%4 == 0 );
					}

					if ( alt_lookup_handler != null ){

						if ( ticks % 4 == 0 ){

							String alt_status = alt_lookup_handler.getString();

							if ( !alt_status.equals( prev_alt_status )){

								log.log( "Alternative stats: " + alt_status );

								prev_alt_status = alt_status;
							}
						}
					}
				}
			});
	}

	public void
	waitUntilInitialised()
	{
		initialised_sem.reserve();
	}

	public boolean
	isRunning()
	{
		return( is_running );
	}

	public void
	addDownload(
		final Download	download )
	{
		Torrent	torrent = download.getTorrent();

		if ( torrent == null ){
			
			return;
		}
		
		URL announce_url = torrent.getAnnounceURL();
		
		boolean	is_decentralised = TorrentUtils.isDecentralised( announce_url );

			// bail on our low noise ones, these don't require decentralised tracking unless that's what they are

		if ( download.getFlag( Download.FLAG_LOW_NOISE ) && !is_decentralised ){

				// make an excpetion for update torrents for the moment
			
			if ( !announce_url.getHost().endsWith( ".amazonaws.com" )){
			
				return;
			}
		}

		if ( track_only_decentralsed ){

			if ( !is_decentralised ){

				return;
			}
		}

		if ( is_running ){

			String[]	networks = download.getListAttribute( ta_networks );

			if ( networks != null ){

				boolean	public_net = false;

				for (int i=0;i<networks.length;i++){

					if ( networks[i].equalsIgnoreCase( "Public" )){

						public_net	= true;

						break;
					}
				}

				if ( public_net && !torrent.isPrivate()){

					boolean	our_download =  torrent.wasCreatedByUs();

					long	delay;

					if ( our_download ){

						if ( download.getCreationTime() > start_time ){

							delay = 0;

						}else{

							delay = plugin_interface.getUtilities().getCurrentSystemTime() +
									INTERESTING_INIT_MIN_OURS +
									random.nextInt( INTERESTING_INIT_RAND_OURS );

						}
					}else{

						int	min;
						int	rand;

						if ( TorrentUtils.isDecentralised( torrent.getAnnounceURL())){

							min		= INTERESTING_DHT_INIT_MIN;
							rand	= INTERESTING_DHT_INIT_RAND;

						}else{

							min		= INTERESTING_INIT_MIN_OTHERS;
							rand	= INTERESTING_INIT_RAND_OTHERS;
						}

						delay = plugin_interface.getUtilities().getCurrentSystemTime() +
									min + random.nextInt( rand );
					}

					try{
						this_mon.enter();

						interesting_downloads.put( download, new Long( delay ));

					}finally{

						this_mon.exit();
					}
				}
			}

			download.addAttributeListener(DHTTrackerPlugin.this, ta_networks, DownloadAttributeListener.WRITTEN);
			download.addAttributeListener(DHTTrackerPlugin.this, ta_peer_sources, DownloadAttributeListener.WRITTEN);

			download.addTrackerListener( DHTTrackerPlugin.this );

			download.addListener( DHTTrackerPlugin.this );

			checkDownloadForRegistration( download, true );

		}else{

			if ( torrent.isDecentralised()){

				download.addListener(
					new DownloadListener()
					{
						@Override
						public void
						stateChanged(
							final Download		download,
							int					old_state,
							int					new_state )
						{
							int	state = download.getState();

							if ( 	state == Download.ST_DOWNLOADING ||
									state == Download.ST_SEEDING ){

								download.setAnnounceResult(
									new DownloadAnnounceResult()
									{
										@Override
										public Download
										getDownload()
										{
											return( download );
										}

										@Override
										public int
										getResponseType()
										{
											return( DownloadAnnounceResult.RT_ERROR );
										}

										@Override
										public int
										getReportedPeerCount()
										{
											return( 0 );
										}


										@Override
										public int
										getSeedCount()
										{
											return( 0 );
										}

										@Override
										public int
										getNonSeedCount()
										{
											return( 0 );
										}

										@Override
										public String
										getError()
										{
											return( "Distributed Database Offline" );
										}

										@Override
										public URL
										getURL()
										{
											return( download.getTorrent().getAnnounceURL());
										}

										@Override
										public DownloadAnnounceResultPeer[]
										getPeers()
										{
											return( new DownloadAnnounceResultPeer[0] );
										}

										@Override
										public long
										getTimeToWait()
										{
											return( 0 );
										}

										@Override
										public Map
										getExtensions()
										{
											return( null );
										}
									});
							}
						}

						@Override
						public void
						positionChanged(
							Download		download,
							int 			oldPosition,
							int 			newPosition )
						{

						}
					});


				download.setScrapeResult(
					new DownloadScrapeResult()
					{
						@Override
						public Download
						getDownload()
						{
							return( download );
						}

						@Override
						public int
						getResponseType()
						{
							return( DownloadScrapeResult.RT_ERROR );
						}

						@Override
						public int
						getSeedCount()
						{
							return( -1 );
						}

						@Override
						public int
						getNonSeedCount()
						{
							return( -1 );
						}

						@Override
						public long
						getScrapeStartTime()
						{
							return( SystemTime.getCurrentTime());
						}

						@Override
						public void
						setNextScrapeStartTime(
							long nextScrapeStartTime)
						{
						}

						@Override
						public long
						getNextScrapeStartTime()
						{
							return( -1 );
						}

						@Override
						public String
						getStatus()
						{
							return( "Distributed Database Offline" );
						}

						@Override
						public URL
						getURL()
						{
							return( download.getTorrent().getAnnounceURL());
						}
					});
			}
		}
	}

	public void
	removeDownload(
		Download	download )
	{
		if ( is_running ){
			
			download.removeTrackerListener( DHTTrackerPlugin.this );

			download.removeListener( DHTTrackerPlugin.this );

			try{
				this_mon.enter();

				interesting_downloads.remove( download );

				interesting_published.remove( download );
				
				running_downloads.remove( download );

				run_data_cache.remove( download );

				limited_online_tracking.remove( download );

			}finally{

				this_mon.exit();
			}
		}
	}

	@Override
	public void attributeEventOccurred(Download download, TorrentAttribute attr, int event_type) {
		checkDownloadForRegistration(download, false);
	}

	@Override
	public void
	scrapeResult(
		DownloadScrapeResult	result )
	{
		checkDownloadForRegistration( result.getDownload(), false );
	}

	@Override
	public void
	announceResult(
		DownloadAnnounceResult	result )
	{
		checkDownloadForRegistration( result.getDownload(), false );
	}


	protected void
	checkDownloadForRegistration(
		Download		download,
		boolean			first_time )
	{
		if ( download == null ){

			return;
		}

		boolean	skip_log = false;

		int	state = download.getState();

		int	register_type	= REG_TYPE_NONE;

		String	register_reason;

		Random	random = new Random();
			/*
			 * Queued downloads are removed from the set to consider as we now have the "presence store"
			 * mechanism to ensure that there are a number of peers out there to provide torrent download
			 * if required. This has been done to avoid the large number of registrations that users with
			 * large numbers of queued torrents were getting.
			 */

		if ( 	state == Download.ST_DOWNLOADING 	||
				state == Download.ST_SEEDING 		||
				// state == Download.ST_QUEUED 		||
				download.isPaused()){	// pause is a transitory state, don't dereg

			String[]	networks = download.getListAttribute( ta_networks );

			Torrent	torrent = download.getTorrent();

			if ( torrent != null && networks != null ){

				boolean	public_net = false;

				for (int i=0;i<networks.length;i++){

					if ( networks[i].equalsIgnoreCase( "Public" )){

						public_net	= true;

						break;
					}
				}

				if ( public_net && !torrent.isPrivate()){

					if ( torrent.isDecentralised()){

							// peer source not relevant for decentralised torrents

						register_type	= REG_TYPE_FULL;

						register_reason = "Decentralised";

					}else{

						if ( torrent.isDecentralisedBackupEnabled() || TEST_ALWAYS_TRACK ){

							String[]	sources = download.getListAttribute( ta_peer_sources );

							boolean	ok = false;

							if ( sources != null ){

								for (int i=0;i<sources.length;i++){

									if ( sources[i].equalsIgnoreCase( "DHT")){

										ok	= true;

										break;
									}
								}
							}

							if ( !( ok || TEST_ALWAYS_TRACK )){

								register_reason = "Decentralised peer source disabled";

							}else{
									// this will always be true since change to exclude queued...

								boolean	is_active =
											state == Download.ST_DOWNLOADING ||
											state == Download.ST_SEEDING ||
											download.isPaused();

								if ( is_active ){

									register_type = REG_TYPE_DERIVED;
								}

								if( torrent.isDecentralisedBackupRequested() || TEST_ALWAYS_TRACK ){

									register_type	= REG_TYPE_FULL;

									register_reason = TEST_ALWAYS_TRACK?"Testing always track":"Torrent requests decentralised tracking";

								}else if ( track_normal_when_offline.getValue()){

										// only track if torrent's tracker is not available

									if ( is_active ){

										DownloadAnnounceResult result = download.getLastAnnounceResult();

										if (	result == null ||
												result.getResponseType() == DownloadAnnounceResult.RT_ERROR ||
												TorrentUtils.isDecentralised(result.getURL())){

											register_type	= REG_TYPE_FULL;

											register_reason = "Tracker unavailable (announce)";

										}else{

											register_reason = "Tracker available (announce: " + result.getURL() + ")";
										}
									}else{

										DownloadScrapeResult result = download.getLastScrapeResult();

										if (	result == null ||
												result.getResponseType() == DownloadScrapeResult.RT_ERROR ||
												TorrentUtils.isDecentralised(result.getURL())){

											register_type	= REG_TYPE_FULL;

											register_reason = "Tracker unavailable (scrape)";

										}else{

											register_reason = "Tracker available (scrape: " + result.getURL() + ")";
										}
									}

									if ( register_type != REG_TYPE_FULL && track_limited_when_online.getValue()){

										Boolean	existing = (Boolean)limited_online_tracking.get( download );

										boolean	track_it = false;

										if ( existing != null ){

											track_it = existing.booleanValue();

										}else{

											DownloadScrapeResult result = download.getLastScrapeResult();

											if (	result != null&&
													result.getResponseType() == DownloadScrapeResult.RT_SUCCESS ){

												int	seeds 		= result.getSeedCount();
												int leechers	= result.getNonSeedCount();

												int	swarm_size = seeds + leechers;

												if ( swarm_size <= LIMITED_TRACK_SIZE ){

													track_it = true;

												}else{

													track_it = random.nextInt( swarm_size ) < LIMITED_TRACK_SIZE;
												}

												if ( track_it ){

													limited_online_tracking.put( download, Boolean.valueOf(track_it));
												}
											}
										}

										if( track_it ){

											register_type	= REG_TYPE_FULL;

											register_reason = "Limited online tracking";
										}
									}
								}else{
									register_type	= REG_TYPE_FULL;

									register_reason = "Peer source enabled";
								}
							}
						}else{

							register_reason = "Decentralised backup disabled for the torrent";
						}
					}
				}else{

					if ( public_net ){
					
						register_reason = MessageText.getString( "label.private" );
						
					}else{
						
						register_reason = MessageText.getString( "Scrape.status.networkdisabled" );
					}
				}
			}else{

				register_reason = "Torrent is broken";
			}

			if ( register_type == REG_TYPE_DERIVED ){

				if ( register_reason.length() == 0 ){

					register_reason = "Derived";

				}else{

					register_reason = "Derived (overriding ' " + register_reason + "')";
				}
			}
		}else if ( 	state == Download.ST_STOPPED ||
					state == Download.ST_ERROR ){

			register_reason	= "Not running";

			skip_log	= true;

		}else if ( 	state == Download.ST_QUEUED ){

				// leave in whatever state it current is (reg or not reg) to avoid thrashing
				// registrations when seeding rules are start/queueing downloads

			register_reason	= "";

		}else{

			register_reason	= "";
		}

		download.setUserData( LATEST_REGISTER_REASON, register_reason );

		if ( register_reason.length() > 0 ){

			try{
				this_mon.enter();

				int[] run_data = running_downloads.get( download );

				if ( register_type != REG_TYPE_NONE ){

					if ( run_data == null ){

						log( download,	"Monitoring: " + register_reason);

						int[] cache = run_data_cache.remove( download );

						if ( cache == null ){

							running_downloads.put( download, new int[]{ register_type, 0, 0, 0, 0 });

						}else{

							cache[0] = register_type;

							running_downloads.put( download, cache );
						}

						query_map.put( download, new Long( SystemTime.getCurrentTime()));

					}else{

						Integer	existing_type = run_data[0];

						if ( 	existing_type.intValue() == REG_TYPE_DERIVED &&
								register_type == REG_TYPE_FULL ){

								// upgrade

							run_data[0] = register_type;
						}
					}
				}else{
					
					if ( run_data  != null ){

						if ( !skip_log ){

							log( download, "Not monitoring: "	+ register_reason);
						}

						running_downloads.remove( download );

						run_data_cache.put( download, run_data );

							// add back to interesting downloads for monitoring

						interesting_downloads.put(
								download,
								new Long( 	plugin_interface.getUtilities().getCurrentSystemTime() +
											INTERESTING_INIT_MIN_OTHERS ));

					}else{

						if ( first_time && !skip_log ){

							log( download, "Not monitoring: "	+ register_reason);
						}
					}
				}
			}finally{

				this_mon.exit();
			}
		}
	}

	protected void
	processRegistrations(
		boolean		full_processing )
	{
		int	tcp_port = plugin_interface.getPluginconfig().getUnsafeIntParameter( "TCP.Listen.Port" );

 		String port_override = COConfigurationManager.getStringParameter("TCP.Listen.Port.Override");

  		if( !port_override.equals("")){

  			try{
  				tcp_port	= Integer.parseInt( port_override );

  			}catch( Throwable e ){
  			}
  		}

  		if ( tcp_port == 0 ){

  			log.log( "TCP port=0, registration not performed" );

  			return;
  		}

	    String override_ips = COConfigurationManager.getStringParameter( "Override Ip", "" );

	    String override_ip	= null;

	  	if ( override_ips.length() > 0 ){

   				// gotta select an appropriate override based on network type

	  		StringTokenizer	tok = new StringTokenizer( override_ips, ";" );

	  		while( tok.hasMoreTokens()){

	  			String	this_address = (String)tok.nextToken().trim();

	  			if ( this_address.length() > 0 ){

	  				String	cat = AENetworkClassifier.categoriseAddress( this_address );

	  				if ( cat == AENetworkClassifier.AT_PUBLIC ){

	  					override_ip	= this_address;

	  					break;
	  				}
	  			}
			}
		}

  	    if ( override_ip != null ){

    		try{
    			override_ip = PRHelpers.DNSToIPAddress( override_ip );

    		}catch( UnknownHostException e){

    			log.log( "    Can't resolve IP override '" + override_ip + "'" );

    			override_ip	= null;
    		}
    	}

		ArrayList<Download>	rds;

		try{
			this_mon.enter();

			rds = new ArrayList<>(running_downloads.keySet());

		}finally{

			this_mon.exit();
		}

		long	 now = SystemTime.getCurrentTime();


		if ( full_processing ){

			Iterator<Download>	rds_it = rds.iterator();

			List<Object[]> interesting = new ArrayList<>();

			while( rds_it.hasNext()){

				Download	dl = rds_it.next();

				int	reg_type = REG_TYPE_NONE;

				try{
					this_mon.enter();

					int[] run_data = running_downloads.get( dl );

					if ( run_data != null ){

						reg_type = run_data[0];
					}
				}finally{

					this_mon.exit();
				}

		  		if ( reg_type == REG_TYPE_NONE ){

		  			continue;
		  		}

		  		long metric = getDerivedTrackMetric( dl );

		  		interesting.add( new Object[]{ dl, new Long( metric )} );
			}

			Collections.sort(
				interesting,
				new Comparator<Object[]>()
				{
					@Override
					public int
					compare(
						Object[] entry1,
						Object[] entry2)
					{
						long	res = ((Long)entry2[1]).longValue() - ((Long)entry1[1]).longValue();

						if( res < 0 ){

							return( -1 );

						}else if ( res > 0 ){

							return( 1 );

						}else{

							return( 0 );
						}
					}
				});

			Iterator<Object[]> it	= interesting.iterator();

			int	num = 0;

			while( it.hasNext()){

				Object[] entry = it.next();

				Download	dl 		= (Download)entry[0];
				long		metric	= ((Long)entry[1]).longValue();

				num++;

				if ( metric > 0 ){

					if ( num <= DL_DERIVED_MIN_TRACK ){

							// leave as is

					}else if ( num <= DL_DERIVED_MAX_TRACK ){

							// scale metric between limits

						metric = ( metric * ( DL_DERIVED_MAX_TRACK - num )) / ( DL_DERIVED_MAX_TRACK - DL_DERIVED_MIN_TRACK );

					}else{

						metric = 0;
					}
				}

				if ( metric > 0 ){

					dl.setUserData( DL_DERIVED_METRIC_KEY, new Long( metric ));

				}else{

					dl.setUserData( DL_DERIVED_METRIC_KEY, null );
				}
			}
		}

		Iterator<Download>	rds_it = rds.iterator();

			// first off do any puts

		while( rds_it.hasNext()){

			if ( dht_puts_active.get() > MAX_ACTIVE_DHT_PUTS || closing ){
				
				break;
			}
			
			Download	dl = rds_it.next();

			int	reg_type = REG_TYPE_NONE;

			try{
				this_mon.enter();

				int[] run_data = running_downloads.get( dl );

				if ( run_data != null ){

					reg_type = run_data[0];
				}
			}finally{

				this_mon.exit();
			}

	  		if ( reg_type == REG_TYPE_NONE ){

	  			continue;
	  		}

	  			// format is [ip_override:]tcp_port[;CI...][;udp_port]

	  	    String	value_to_put = override_ip==null?"":(override_ip+":");

	  	    value_to_put += tcp_port;

	  	    String put_flags = ";";

	  	    if ( NetworkManager.REQUIRE_CRYPTO_HANDSHAKE ){

	  	    	put_flags += "C";
	  	    }

	  	    String[]	networks = dl.getListAttribute( ta_networks );

	  	    boolean	i2p = false;

	  	    if ( networks != null ){

	  	    	for ( String net: networks ){

	  	    		if ( net == AENetworkClassifier.AT_I2P ){

	  	    			if ( I2PHelpers.isI2PInstalled()){

	  	    				put_flags += "I";
	  	    			}

	  	    			i2p = true;

	  	    			break;
	  	    		}
	  	    	}
	  	    }

	  	    if ( put_flags.length() > 1 ){

	  	    	value_to_put += put_flags;
	  	    }

			int	udp_port = plugin_interface.getPluginconfig().getUnsafeIntParameter( "UDP.Listen.Port" );

			int	dht_port = dht.getLocalAddress().getAddress().getPort();

			if ( udp_port != dht_port ){

				value_to_put += ";" + udp_port;
			}

			putDetails	put_details = new putDetails( value_to_put, override_ip, tcp_port, udp_port, i2p );

			byte	dht_flags = isComplete( dl )?DHTPlugin.FLAG_SEEDING:DHTPlugin.FLAG_DOWNLOADING;

			RegistrationDetails	registration = (RegistrationDetails)registered_downloads.get( dl );

			boolean	do_it = false;

			if ( registration == null ){

				log( dl, "Registering as " + (dht_flags == DHTPlugin.FLAG_SEEDING?"Seeding":"Downloading"));

				registration = new RegistrationDetails( dl, reg_type, put_details, dht_flags );

				registered_downloads.put( dl, registration );

				do_it = true;

			}else{

				boolean	targets_changed = false;

				if ( full_processing ){

					targets_changed = registration.updateTargets( dl, reg_type );
				}

				if (	targets_changed ||
						registration.getFlags() != dht_flags ||
						!registration.getPutDetails().sameAs( put_details )){

					log( dl,(registration==null?"Registering":"Re-registering") + " as " + (dht_flags == DHTPlugin.FLAG_SEEDING?"Seeding":"Downloading"));

					registration.update( put_details, dht_flags );

					do_it = true;
				}
			}

			if ( do_it ){

				try{
					this_mon.enter();

					query_map.put( dl, new Long( now ));

				}finally{

					this_mon.exit();
				}

				trackerPut( dl, registration );
			}
		}

			// second any removals

		Iterator<Map.Entry<Download,RegistrationDetails>> rd_it = registered_downloads.entrySet().iterator();

		while( rd_it.hasNext()){

			if ( dht_removes_active.get() > MAX_ACTIVE_DHT_REMOVES ){
				
				break;
			}
			
			Map.Entry<Download,RegistrationDetails>	entry = rd_it.next();

			final Download	dl = entry.getKey();

			boolean	unregister;

			try{
				this_mon.enter();

				unregister = !running_downloads.containsKey( dl );

			}finally{

				this_mon.exit();
			}

			if ( unregister ){

				log( dl, "Unregistering download" );

				rd_it.remove();

				try{
					this_mon.enter();

					query_map.remove( dl );

				}finally{

					this_mon.exit();
				}

				trackerRemove( dl, entry.getValue());
			}
		}

			// lastly gets
				
		rds_it = rds.iterator();

		while( rds_it.hasNext()){

			if ( dht_gets_active.get() > MAX_ACTIVE_DHT_GETS || closing ){
				
				break;
			}
			
			final Download	dl = (Download)rds_it.next();

			RegistrationDetails	registration = (RegistrationDetails)registered_downloads.get( dl );

			if ( registration == null ){

					// this can happen since we rate-limited the put operations
				
				// Debug.out( "Inconsistent, registration should be non-null: dl=" + dl.getName());

				continue;
			}
			
			Long	next_time;

			try{
				this_mon.enter();

				next_time = (Long)query_map.get( dl );

			}finally{

				this_mon.exit();
			}

			if ( next_time != null && now >= next_time.longValue()){

				int	reg_type = REG_TYPE_NONE;

				try{
					this_mon.enter();

					query_map.remove( dl );

					int[] run_data = running_downloads.get( dl );

					if ( run_data != null ){

						reg_type = run_data[0];
					}
				}finally{

					this_mon.exit();
				}

				final long	start = SystemTime.getCurrentTime();

					// if we're already connected to > NUM_WANT peers then don't bother with the main announce

				PeerManager	pm = dl.getPeerManager();

					// don't query if this download already has an active DHT operation

				boolean	skip	= isActive( dl ) || reg_type == REG_TYPE_NONE;

				if ( skip ){

					log( dl, "Deferring announce as activity outstanding" );
				}

				boolean	derived_only = false;

				if ( pm != null && !skip ){

					int	con = pm.getStats().getConnectedLeechers() + pm.getStats().getConnectedSeeds();

					derived_only = con >= NUM_WANT;
				}

				if ( !skip ){

					skip = trackerGet( dl, registration, derived_only ) == 0;

				}

					// if we didn't kick off a get then we have to reschedule here as normally
					// the get operation will do the rescheduling when it receives a result

				if ( skip ){

					try{
						this_mon.enter();

						if ( running_downloads.containsKey( dl )){

								// use "min" here as we're just deferring it

							query_map.put( dl, new Long( start + ANNOUNCE_MIN_DEFAULT ));
						}

					}finally{

						this_mon.exit();
					}
				}
			}
		}
	}

	protected long
	getDerivedTrackMetric(
		Download		download )
	{
			// metric between -100 and + 100. Note that all -ve mean 'don't do it'
			// they're just indicating different reasons

		Torrent t = download.getTorrent();

		if ( t == null ){

			return( -100 );
		}

		if ( t.getSize() < 10*1024*1024 ){

			return( -99 );
		}

		DownloadAnnounceResult announce = download.getLastAnnounceResult();

		if ( 	announce == null ||
				announce.getResponseType() != DownloadAnnounceResult.RT_SUCCESS ){

			return( -98 );
		}

		DownloadScrapeResult scrape = download.getLastScrapeResult();

		if ( 	scrape == null ||
				scrape.getResponseType() != DownloadScrapeResult.RT_SUCCESS ){

			return( -97 );
		}

		int leechers 	= scrape.getNonSeedCount();
		// int seeds		= scrape.getSeedCount();

		int	total = leechers;	// parg - changed to just use leecher count rather than seeds+leechers

		if ( total >= 2000 ){

			return( 100 );

		}else if ( total <= 200 ){

			return( 0 );

		}else{

			return( ( total - 200 ) / 4 );
		}
	}

	protected void
	trackerPut(
		final Download			download,
		RegistrationDetails		details )
	{
		final 	long	start = SystemTime.getCurrentTime();

		trackerTarget[] targets = details.getTargets( true );

		byte flags = details.getFlags();

		for (int i=0;i<targets.length;i++){

			final trackerTarget target = targets[i];

			int	target_type = target.getType();

		 	    // don't let a put block an announce as we don't want to be waiting for
		  	    // this at start of day to get a torrent running

		  	    // increaseActive( dl );

			String	encoded = details.getPutDetails().getEncoded();

			byte[]	encoded_bytes = encoded.getBytes();

			DHTPluginValue existing = dht.getLocalValue( target.getHash());

			if ( 	existing != null &&
					existing.getFlags() == flags &&
					Arrays.equals( existing.getValue(), encoded_bytes )){

					// already present, no point in updating

				continue;
			}

			if ( disable_put ){

				if ( target_type == REG_TYPE_FULL ){

					log( download, target.getDesc( "Registration" ) + " skipped as disabled due to use of SOCKS proxy");
				}
			}else if ( download.getFlag( Download.FLAG_METADATA_DOWNLOAD )){

				log( download, target.getDesc( "Registration" ) + " skipped as metadata download");

			}else if ( target_type == REG_TYPE_DERIVED && dht.isSleeping()){

				log( download, target.getDesc( "Registration" ) + " skipped as sleeping");

			}else{

				dht.put(
					target.getHash(),
					download.getName() + ": " + target.getDesc( "Put" ) + " -> " + encoded,
					encoded_bytes,
					flags,
					false,
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
							DHTPluginContact originator,
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
							byte[]	key,
							boolean	timeout_occurred )
						{
							dht_puts_active.decrementAndGet();
							
							if ( target.getType() == REG_TYPE_FULL ){

								log( 	download,
										target.getDesc( "Registration" ) + " completed (elapsed="	+ TimeFormatter.formatColonMillis((SystemTime.getCurrentTime() - start)) + ")");
							}

								// decreaseActive( dl );
						}
					});
				
				dht_puts_active.incrementAndGet();
			}
		}
	}

	protected int
	trackerGet(
		final Download					download,
		final RegistrationDetails		details,
		final boolean					derived_only )
	{
		final 	long	start = SystemTime.getCurrentTime();

		final Torrent	torrent = download.getTorrent();

		final URL	url_to_report = torrent.isDecentralised()?torrent.getAnnounceURL():DEFAULT_URL;

		trackerTarget[] targets = details.getTargets( false );

		final long[]	max_retry = { 0 };

		boolean metadata_download = download.getFlag( Download.FLAG_METADATA_DOWNLOAD );
		
		boolean do_alt = 
			alt_lookup_handler != null &&
				(	metadata_download || 
					(! ( download.getFlag( Download.FLAG_LOW_NOISE ) || download.getFlag( Download.FLAG_LIGHT_WEIGHT ))));
	
		int	num_done = 0;

		for (int i=0;i<targets.length;i++){

			final trackerTarget target = targets[i];

			int	target_type = target.getType();

			if ( target_type == REG_TYPE_FULL && derived_only ){

				continue;

			}else if ( target_type == REG_TYPE_DERIVED && dht.isSleeping()){

				continue;
			}

			increaseActive( download );

			num_done++;

			final boolean is_complete = isComplete( download );

			dht.get(target.getHash(),
					download.getName() + ": " + target.getDesc( "Announce" ),
					is_complete?DHTPlugin.FLAG_SEEDING:DHTPlugin.FLAG_DOWNLOADING,
					NUM_WANT,
					target_type==REG_TYPE_FULL?ANNOUNCE_TIMEOUT:ANNOUNCE_DERIVED_TIMEOUT,
					false, false,
					new DHTPluginOperationListener()
					{
						List<String>	addresses 	= new ArrayList<>();
						List<Integer>	ports		= new ArrayList<>();
						List<Integer>	udp_ports	= new ArrayList<>();
						List<Boolean>	is_seeds	= new ArrayList<>();
						List<String>	flags		= new ArrayList<>();

						int		seed_count;
						int		leecher_count;

						int		i2p_seed_count;
						int 	i2p_leecher_count;

						volatile boolean	complete;

						{
							if ( do_alt ){

								alt_lookup_handler.get(
										target.getHash(),
										is_complete,
										new DHTTrackerPluginAlt.LookupListener()
										{
											@Override
											public void
											foundPeer(
												InetSocketAddress	address )
											{
												alternativePeerRead( address );
											}

											@Override
											public boolean
											isComplete()
											{
												return( complete && addresses.size() > 5 );
											}

											@Override
											public void
											completed()
											{
											}
										});
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
						starts(
							byte[] 				key )
						{
						}

						private void
						alternativePeerRead(
							InetSocketAddress		peer )
						{
							boolean	try_injection = metadata_download;

							synchronized( this ){

								if ( complete ){

									try_injection |= addresses.size() < 5;

								}else{

									try{
										addresses.add( peer.getAddress().getHostAddress());
										
										ports.add( peer.getPort());
										
										udp_ports.add( peer.getPort());
										
										flags.add( null );

										is_seeds.add( false );
										
										leecher_count++;

									}catch( Throwable e ){
									}
								}
							}

							if ( try_injection ){

								PeerManager pm = download.getPeerManager();

								if ( pm != null ){

									pm.peerDiscovered(
										PEPeerSource.PS_DHT,
										peer.getAddress().getHostAddress(),
										peer.getPort(),
										peer.getPort(),
										NetworkManager.getCryptoRequired( NetworkManager.CRYPTO_OVERRIDE_NONE ));
								}
							}
						}

						@Override
						public void
						valueRead(
							DHTPluginContact	originator,
							DHTPluginValue		value )
						{
							String 	peer_ip			= null;
							int		peer_tcp_port	= 0;
							int		peer_udp_port	= 0;
							
							synchronized( this ){

								if ( complete ){

									return;
								}

								try{
									String[]	tokens = new String(value.getValue()).split(";");

									String	tcp_part = tokens[0].trim();

									int	sep = tcp_part.indexOf(':');

									String	ip_str		= null;
									String	tcp_port_str;

									if ( sep == -1 ){

										tcp_port_str = tcp_part;

									}else{

										ip_str 			= tcp_part.substring( 0, sep );
										tcp_port_str	= tcp_part.substring( sep+1 );
									}

									int	tcp_port = Integer.parseInt( tcp_port_str );

									if ( tcp_port > 0 && tcp_port < 65536 ){

										String	flag_str	= null;
										int		udp_port	= -1;

										boolean	has_i2p = false;

										try{
											for (int i=1;i<tokens.length;i++){

												String	token = tokens[i].trim();

												if ( token.length() > 0 ){

													if ( Character.isDigit( token.charAt( 0 ))){

														udp_port = Integer.parseInt( token );

														if ( udp_port <= 0 || udp_port >=65536 ){

															udp_port = -1;
														}
													}else{

														flag_str = token;

														if ( flag_str.contains("I")){

															has_i2p = true;
														}
													}
												}
											}
										}catch( Throwable e ){
										}

										
										peer_ip 		= ip_str==null?originator.getAddress().getAddress().getHostAddress():ip_str;
										peer_tcp_port	= tcp_port;
										peer_udp_port	= udp_port==-1?originator.getAddress().getPort():udp_port;
										
										addresses.add( peer_ip );
										
										ports.add( peer_tcp_port );

										udp_ports.add( peer_udp_port );

										flags.add( flag_str );

										if (( value.getFlags() & DHTPlugin.FLAG_DOWNLOADING ) == 1 ){

											leecher_count++;

											is_seeds.add(Boolean.FALSE);

											if ( has_i2p ){

												i2p_leecher_count++;
											}
										}else{

											is_seeds.add(Boolean.TRUE);

											seed_count++;

											if ( has_i2p ){

												i2p_seed_count++;
											}
										}
									}

								}catch( Throwable e ){

									// in case we get crap back (someone spamming the DHT) just
									// silently ignore
								}
							}
							
							if ( metadata_download && peer_ip != null ){
								
								PeerManager pm = download.getPeerManager();
									
								if ( pm != null ){

									pm.peerDiscovered(
										PEPeerSource.PS_DHT,
										peer_ip,
										peer_tcp_port,
										peer_udp_port,
										NetworkManager.getCryptoRequired( NetworkManager.CRYPTO_OVERRIDE_NONE ));
								}
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
							byte[]	key,
							boolean	timeout_occurred )
						{
							synchronized( this ){

								if ( complete ){

									return;
								}

								complete = true;
							}

							dht_gets_active.decrementAndGet();
							
							if ( 	target.getType() == REG_TYPE_FULL ||
									(	target.getType() == REG_TYPE_DERIVED &&
										seed_count + leecher_count > 1 )){

								log( 	download,
										target.getDesc("Announce") + " completed (elapsed=" + TimeFormatter.formatColonMillis(SystemTime.getCurrentTime() - start)
												+ "), addresses=" + addresses.size() + ", seeds="
												+ seed_count + ", leechers=" + leecher_count);
							}

							decreaseActive(download);

							int	peers_found = addresses.size();

							List<DownloadAnnounceResultPeer>	peers_for_announce = new ArrayList<>();

								// scale min and max based on number of active torrents
								// we don't want more than a few announces a minute

							int	announce_per_min = 4;

							int	num_active = query_map.size();

							int	announce_min = Math.max( ANNOUNCE_MIN_DEFAULT, ( num_active / announce_per_min )*60*1000 );

							int	announce_max = derived_only?ANNOUNCE_MAX_DERIVED_ONLY:ANNOUNCE_MAX;

							announce_min = Math.min( announce_min, announce_max );

							current_announce_interval = announce_min;

							final long	retry = announce_min + peers_found*(long)(announce_max-announce_min)/NUM_WANT;

							int download_state = download.getState();

							boolean	we_are_seeding = download_state == Download.ST_SEEDING;

							try{
								this_mon.enter();

								int[] run_data = running_downloads.get( download );

								if ( run_data != null ){

									boolean full = target.getType() == REG_TYPE_FULL;

									int peer_count = we_are_seeding?leecher_count:(seed_count+leecher_count);

									run_data[1] = full?seed_count:Math.max( run_data[1], seed_count);
									run_data[2]	= full?leecher_count:Math.max( run_data[2], leecher_count);
									run_data[3] = full?peer_count:Math.max( run_data[3], peer_count);

									run_data[4] = (int)(SystemTime.getCurrentTime()/1000);

									long	absolute_retry = SystemTime.getCurrentTime() + retry;

									if ( absolute_retry > max_retry[0] ){

											// only update next query time if none set yet
											// or we appear to have set the existing one. If we
											// don't do this then we'll overwrite any rescheduled
											// announces

										Long	existing = (Long)query_map.get( download );

										if ( 	existing == null ||
												existing.longValue() == max_retry[0] ){

											max_retry[0] = absolute_retry;

											query_map.put( download, new Long( absolute_retry ));
										}
									}
								}
							}finally{

								this_mon.exit();
							}

							putDetails put_details = details.getPutDetails();

							String	ext_address = put_details.getIPOverride();

							if ( ext_address == null ){

								ext_address = dht.getLocalAddress().getAddress().getAddress().getHostAddress();
							}

							if ( put_details.hasI2P()){

								if ( we_are_seeding ){
									if ( i2p_seed_count > 0 ){
										i2p_seed_count--;
									}
								}else{
									if ( i2p_leecher_count > 0 ){
										i2p_leecher_count--;
									}
								}
							}

							if ( i2p_seed_count + i2p_leecher_count > 0 ){

								download.setUserData( DOWNLOAD_USER_DATA_I2P_SCRAPE_KEY, new int[]{ i2p_seed_count,  i2p_leecher_count });

							}else{

								download.setUserData( DOWNLOAD_USER_DATA_I2P_SCRAPE_KEY, null );
							}

							for (int i=0;i<addresses.size();i++){

									// when we are seeding ignore seeds

								if ( we_are_seeding && ((Boolean)is_seeds.get(i)).booleanValue()){

									continue;
								}

									// remove ourselves

								String	ip = (String)addresses.get(i);

								if ( ip.equals( ext_address )){

									if ( ((Integer)ports.get(i)).intValue() == put_details.getTCPPort() &&
										 ((Integer)udp_ports.get(i)).intValue() == put_details.getUDPPort()){

										continue;
									}
								}

								final int f_i = i;

								peers_for_announce.add(
									new DownloadAnnounceResultPeer()
									{
										@Override
										public String
										getSource()
										{
											return( PEPeerSource.PS_DHT );
										}

										@Override
										public String
										getAddress()
										{
											return((String)addresses.get(f_i));
										}

										@Override
										public int
										getPort()
										{
											return(((Integer)ports.get(f_i)).intValue());
										}

										@Override
										public int
										getUDPPort()
										{
											return(((Integer)udp_ports.get(f_i)).intValue());
										}

										@Override
										public byte[]
										getPeerID()
										{
											return( null );
										}

										@Override
										public short
										getProtocol()
										{
											String	flag = (String)flags.get(f_i);

											short protocol = DownloadAnnounceResultPeer.PROTOCOL_NORMAL;

											if ( flag != null ){

												if ( flag.contains("C")){

													protocol = DownloadAnnounceResultPeer.PROTOCOL_CRYPT;
												}
											}

											return( protocol );
										}
									});

							}

							if ( target.getType() == REG_TYPE_DERIVED && peers_for_announce.size() > 0 ){

								PeerManager pm = download.getPeerManager();

								if ( pm != null ){

										// try some limited direct injection

									List<DownloadAnnounceResultPeer>	temp = new ArrayList<>(peers_for_announce);

									Random rand = new Random();

									for (int i=0;i<DIRECT_INJECT_PEER_MAX && temp.size() > 0; i++ ){

										DownloadAnnounceResultPeer peer = temp.remove( rand.nextInt( temp.size()));

										log( download, "Injecting derived peer " + peer.getAddress());

										Map<Object,Object>	user_data = new HashMap<>();

										user_data.put( Peer.PR_PRIORITY_CONNECTION, Boolean.TRUE);

										pm.addPeer(
												peer.getAddress(),
												peer.getPort(),
												peer.getUDPPort(),
												peer.getProtocol() == DownloadAnnounceResultPeer.PROTOCOL_CRYPT,
												user_data );
									}
								}
							}

							if ( 	download_state == Download.ST_DOWNLOADING ||
									download_state == Download.ST_SEEDING ){

								final DownloadAnnounceResultPeer[]	peers = new DownloadAnnounceResultPeer[peers_for_announce.size()];

								peers_for_announce.toArray( peers );

								download.setAnnounceResult(
										new DownloadAnnounceResult()
										{
											@Override
											public Download
											getDownload()
											{
												return( download );
											}

											@Override
											public int
											getResponseType()
											{
												return( DownloadAnnounceResult.RT_SUCCESS );
											}

											@Override
											public int
											getReportedPeerCount()
											{
												return( peers.length);
											}

											@Override
											public int
											getSeedCount()
											{
												return( seed_count );
											}

											@Override
											public int
											getNonSeedCount()
											{
												return( leecher_count );
											}

											@Override
											public String
											getError()
											{
												return( null );
											}

											@Override
											public URL
											getURL()
											{
												return( url_to_report );
											}

											@Override
											public DownloadAnnounceResultPeer[]
											getPeers()
											{
												return( peers );
											}

											@Override
											public long
											getTimeToWait()
											{
												return( retry/1000 );
											}

											@Override
											public Map
											getExtensions()
											{
												return( null );
											}
										});
							}

								// only inject the scrape result if the torrent is decentralised. If we do this for
								// "normal" torrents then it can have unwanted side-effects, such as stopping the torrent
								// due to ignore rules if there are no downloaders in the DHT - bthub backup, for example,
								// isn't scrapable...

								// hmm, ok, try being a bit more relaxed about this, inject the scrape if
								// we have any peers.

							boolean	inject_scrape = leecher_count > 0;

							DownloadScrapeResult result = download.getLastScrapeResult();

							if (	result == null ||
									result.getResponseType() == DownloadScrapeResult.RT_ERROR ){

							}else{

									// if the currently reported values are the same as the
									// ones we previously injected then overwrite them
									// note that we can't test the URL to see if we originated
									// the scrape values as this gets replaced when a normal
									// scrape fails :(

								synchronized( scrape_injection_map ){

									int[]	prev = (int[])scrape_injection_map.get( download );

									if ( 	prev != null &&
											prev[0] == result.getSeedCount() &&
											prev[1] == result.getNonSeedCount()){

										inject_scrape	= true;
									}
								}
							}

							if ( torrent.isDecentralised() || inject_scrape ){


									// make sure that the injected scrape values are consistent
									// with our currently connected peers

								PeerManager	pm = download.getPeerManager();

								int	local_seeds 	= 0;
								int	local_leechers 	= 0;

								if ( pm != null ){

									Peer[]	dl_peers = pm.getPeers();

									for (int i=0;i<dl_peers.length;i++){

										Peer	dl_peer = dl_peers[i];

										if ( dl_peer.getPercentDoneInThousandNotation() == 1000 ){

											local_seeds++;

										}else{
											local_leechers++;
										}
									}
								}

								final int f_adj_seeds 		= Math.max( seed_count, local_seeds );
								final int f_adj_leechers	= Math.max( leecher_count, local_leechers );

								synchronized( scrape_injection_map ){

									scrape_injection_map.put( download, new int[]{ f_adj_seeds, f_adj_leechers });
								}

								try{
									this_mon.enter();

									int[] run_data = running_downloads.get( download );

									if ( run_data == null ){

										run_data = run_data_cache.get( download );
									}

									if ( run_data != null ){

										run_data[1] = f_adj_seeds;
										run_data[2]	= f_adj_leechers;

										run_data[4] = (int)(SystemTime.getCurrentTime()/1000);
									}
								}finally{

									this_mon.exit();
								}

								download.setScrapeResult(
									new DownloadScrapeResult()
									{
										@Override
										public Download
										getDownload()
										{
											return( download );
										}

										@Override
										public int
										getResponseType()
										{
											return( DownloadScrapeResult.RT_SUCCESS );
										}

										@Override
										public int
										getSeedCount()
										{
											return( f_adj_seeds );
										}

										@Override
										public int
										getNonSeedCount()
										{
											return( f_adj_leechers );
										}

										@Override
										public long
										getScrapeStartTime()
										{
											return( start );
										}

										@Override
										public void
										setNextScrapeStartTime(
											long nextScrapeStartTime)
										{

										}
										@Override
										public long
										getNextScrapeStartTime()
										{
											return( SystemTime.getCurrentTime() + retry );
										}

										@Override
										public String
										getStatus()
										{
											return( "OK" );
										}

										@Override
										public URL
										getURL()
										{
											return( url_to_report );
										}
									});
								}
						}
					});
			
			dht_gets_active.incrementAndGet();
		}

		return( num_done );
	}

	protected boolean
	isComplete(
		Download	download )
	{
		if ( Constants.DOWNLOAD_SOURCES_PRETEND_COMPLETE ){

			return( true );
		}

		boolean	is_complete = download.isComplete();

		if ( is_complete ){

			PeerManager pm = download.getPeerManager();

			if ( pm != null ){

				PEPeerManager core_pm = PluginCoreUtils.unwrap( pm );

				if ( core_pm != null && core_pm.getHiddenBytes() > 0 ){

					is_complete = false;
				}
			}
		}

		return( is_complete );
	}

	protected void
	trackerRemove(
		final Download			download,
		RegistrationDetails		details )
	{
		if ( disable_put ){

			return;
		}

		if ( download.getFlag( Download.FLAG_METADATA_DOWNLOAD )){

			return;
		}

		final 	long	start = SystemTime.getCurrentTime();

		trackerTarget[] targets = details.getTargets( true );

		for (int i=0;i<targets.length;i++){

			final trackerTarget target = targets[i];

			if ( dht.hasLocalKey( target.getHash())){

				increaseActive( download );

				dht.remove(
						target.getHash(),
						download.getName() + ": " + target.getDesc( "Remove" ),
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
								byte[]	key,
								boolean	timeout_occurred )
							{
								dht_removes_active.decrementAndGet();
								
								if ( target.getType() == REG_TYPE_FULL ){

									log( 	download,
											target.getDesc( "Unregistration" ) + " completed (elapsed="
												+ TimeFormatter.formatColonMillis(SystemTime.getCurrentTime() - start) + ")");
								}

								decreaseActive( download );
							}
						});
				
				dht_removes_active.incrementAndGet();
			}
		}
	}

	protected void
	trackerRemove(
		final Download			download,
		final trackerTarget 	target )
	{
		if ( disable_put ){

			return;
		}

		if ( download.getFlag( Download.FLAG_METADATA_DOWNLOAD )){

			return;
		}

		final 	long	start = SystemTime.getCurrentTime();

		if ( dht.hasLocalKey( target.getHash())){

			increaseActive( download );

			dht.remove(
					target.getHash(),
					download.getName() + ": " + target.getDesc( "Remove" ),
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
							byte[]	key,
							boolean	timeout_occurred )
						{
							if ( target.getType() == REG_TYPE_FULL ){

								log( 	download,
										target.getDesc( "Unregistration" ) + " completed (elapsed="
										+ TimeFormatter.formatColonMillis(SystemTime.getCurrentTime() - start) + ")");
							}

							decreaseActive( download );
						}
					});
		}
	}

	protected void
	processNonRegistrations(
		boolean	do_presence_checks,
		boolean	do_scrape_checks,
		boolean	do_logging )
	{
		long	now			= SystemTime.getCurrentTime();
		long	mono_now	= SystemTime.getMonotonousTime();
		
			// unfortunately getting scrape results can acquire locks and there is a vague
			// possibility of deadlock here, so pre-fetch the scrape results

		List<Download>	to_scrape;

		try{
			this_mon.enter();

			to_scrape = new ArrayList<>(interesting_downloads.size());
			
			Iterator<Download>	it = interesting_downloads.keySet().iterator();

			while( it.hasNext()){

				Download	download = it.next();

				Torrent	torrent = download.getTorrent();

				if ( torrent == null ){

					continue;
				}

				int[] run_data = running_downloads.get( download );

				if ( run_data == null || run_data[0] == REG_TYPE_DERIVED ){

						// looks like we'll need the scrape below

					to_scrape.add( download );
				}
			}
		}finally{

			this_mon.exit();
		}

		Map<Download,DownloadScrapeResult> scrapes = new HashMap<>();

		List<Download> dht_only_scrapes = new ArrayList<>( to_scrape.size());
		
		boolean do_dht_scrapes = dht_scrapes_active.get() < MAX_ACTIVE_DHT_SCRAPES;
		
		for (int i=0;i<to_scrape.size();i++){

			Download	download = (Download)to_scrape.get(i);

			scrapes.put( download, download.getLastScrapeResult());
			
			if ( do_dht_scrapes ){
				
				Torrent torrent = download.getTorrent();

				if ( torrent != null && TorrentUtils.isDecentralised( torrent.getAnnounceURL())){

					int state = download.getState();
					
						// ignore stopped ones for the moment
					
					if ( state == Download.ST_QUEUED || download.isPaused()){
						
						dht_only_scrapes.add( download );
					}
				}
			}
		}

		Download	presence_download				= null;
		long		presence_download_next_check	= -1;

		if ( do_presence_checks ){
			
			try{
				this_mon.enter();
	
				Iterator<Download>	it = interesting_downloads.keySet().iterator();
	
				while( it.hasNext() && presence_download == null ){
	
					Download	download = it.next();
	
					if ( interesting_published.contains(download)){
						
						continue;
					}
					
					Torrent	torrent = download.getTorrent();
	
					if ( torrent == null ){
	
						continue;
					}
	
					int[] run_data = running_downloads.get( download );
	
					if ( run_data == null || run_data[0] == REG_TYPE_DERIVED ){
	
						boolean	force =  torrent.wasCreatedByUs();
	
						if ( !force ){
	
							if ( interesting_pub_max > 0 && interesting_published.size() > interesting_pub_max ){
	
								continue;
							}
	
							DownloadScrapeResult	scrape = (DownloadScrapeResult)scrapes.get( download );
	
							if ( scrape == null ){
	
									// catch it next time round
	
								continue;
							}
	
							if ( scrape.getSeedCount() + scrape.getNonSeedCount() > NUM_WANT ){
	
								continue;
							}
						}
	
						long	target = ((Long)interesting_downloads.get( download )).longValue();
	
						long check_period = TorrentUtils.isDecentralised( torrent.getAnnounceURL())?INTERESTING_DHT_CHECK_PERIOD:INTERESTING_CHECK_PERIOD;
	
						if ( target <= now ){
	
							presence_download				= download;
							presence_download_next_check 	= now + check_period;
	
							interesting_downloads.put( download, new Long( presence_download_next_check ));
	
						}else if ( target - now > check_period ){
	
							interesting_downloads.put( download, new Long( now + (target%check_period)));
						}
					}
				}
	
			}finally{
	
				this_mon.exit();
			}
		}
		
		Download	ready_scrape	= null;

		if ( do_scrape_checks ){
			
			if ( !dht_only_scrapes.isEmpty()){
	
				long		earliest		= -1;
				
				for ( Download download: dht_only_scrapes ){
					
					if ( download != presence_download ){
										
						long[] data = (long[])download.getUserData( SCRAPE_DATA_KEY );
						
						if ( data == null ){
							
							ready_scrape = download;
													
							break;
							
						}else{
							
							long mt = data[0];
							
							if ( mt <= mono_now ){
							
								if ( earliest == -1 || mt < earliest ){
								
									earliest		= mt;
									ready_scrape	= download;
								}
							}
						}
					}
				}
			}
		}
		
		if ( do_logging ){
			
			try{
				this_mon.enter();
				
				String p_str = presence_download==null?"null":presence_download.getName();
				String s_str = ready_scrape==null?"null":ready_scrape.getName();
				
				log.log( 	"Stats: registered=" + registered_downloads.size() + ", query=" + query_map.size() + ", running=" + running_downloads.size() + 
							", active=" + in_progress.size() + ", interesting=" + interesting_downloads.size() + 
							", dhtscrape=" + dht_only_scrapes.size() + "/" + dht_scrapes_active.get() + "/" + dht_scrapes_complete.get() + ", dl=" + p_str + "/" + s_str );
				
			}finally{

				this_mon.exit();
			}
		}
		
		if ( presence_download != null ){

			Torrent torrent = presence_download.getTorrent();

			if ( presence_download.getFlag( Download.FLAG_METADATA_DOWNLOAD )){

				try{
					this_mon.enter();

					interesting_downloads.remove( presence_download );

				}finally{

					this_mon.exit();
				}

			}else if ( dht.isDiversified( torrent.getHash())){

				// System.out.println( "presence query for " + f_ready_download.getName() + "-> diversified pre start" );

				try{
					this_mon.enter();

					interesting_downloads.remove( presence_download );

				}finally{

					this_mon.exit();
				}
			}else{

				Download	f_presence_download = presence_download;

					//System.out.println( "presence query for " + ready_download.getName());

				final long start 		= now;
				final long f_next_check = presence_download_next_check;
				
				dht.get(	torrent.getHash(),
							"Presence query for '" + presence_download.getName() + "'",
							(byte)0,
							INTERESTING_AVAIL_MAX,
							ANNOUNCE_TIMEOUT,
							false, false,
							new DHTPluginOperationListener()
							{
								private boolean diversified;
								private int 	leechers = 0;
								private int 	seeds	 = 0;

								private int 	i2p_leechers = 0;
								private int 	i2p_seeds	 = 0;

								@Override
								public boolean
								diversified()
								{
									diversified	= true;

									return( false );
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
									boolean is_leecher = ( value.getFlags() & DHTPlugin.FLAG_DOWNLOADING ) == 1;

									if ( is_leecher ){

										leechers++;

									}else{

										seeds++;
									}

									try{
										String[]	tokens = new String(value.getValue()).split(";");

										for (int i=1;i<tokens.length;i++){

											String	token = tokens[i].trim();

											if ( token.length() > 0 ){

												if ( !Character.isDigit( token.charAt( 0 ))){

													String flag_str = token;

													if ( flag_str.contains("I")){

														if ( is_leecher ){

															i2p_leechers++;

														}else{

															i2p_seeds++;
														}
													}
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
									byte[]	key,
									boolean	timeout_occurred )
								{
									// System.out.println( "    presence query for " + f_ready_download.getName() + "->" + total + "/div = " + diversified );

									int	total = leechers + seeds;

									log( f_presence_download,
											"Presence query: availability="+
											(total==INTERESTING_AVAIL_MAX?(INTERESTING_AVAIL_MAX+"+"):(total+"")) + ",div=" + diversified +
											" (elapsed=" + TimeFormatter.formatColonMillis(SystemTime.getCurrentTime() - start) + ")");

									if ( diversified ){

										try{
											this_mon.enter();

											interesting_downloads.remove( f_presence_download );

										}finally{

											this_mon.exit();
										}

									}else if ( total < INTERESTING_AVAIL_MAX ){

										interesting_published.add( f_presence_download );

										if ( !disable_put ){

											dht.put(
												torrent.getHash(),
												"Presence store for '" + f_presence_download.getName() + "'",
												"0".getBytes(),	// port 0, no connections
												(byte)0,
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
														byte[]	key,
														boolean	timeout_occurred )
													{
													}
												});
										}
									}


									try{
										this_mon.enter();

										int[] run_data = running_downloads.get( f_presence_download );

										if ( run_data == null ){

											run_data = run_data_cache.get( f_presence_download );
										}

										if ( run_data != null ){

											if ( total < INTERESTING_AVAIL_MAX ){

												run_data[1] = seeds;
												run_data[2]	= leechers;
												run_data[3] = total;

											}else{

												run_data[1] = Math.max( run_data[1], seeds );
												run_data[2] = Math.max( run_data[2], leechers );
											}

											run_data[4] = (int)(SystemTime.getCurrentTime()/1000);
										}
									}finally{

										this_mon.exit();
									}

									if ( i2p_seeds + i2p_leechers > 0 ){

										int[] details = (int[])f_presence_download.getUserData( DOWNLOAD_USER_DATA_I2P_SCRAPE_KEY );

										if ( details == null ){

											details = new int[]{ i2p_seeds, i2p_leechers };

											f_presence_download.setUserData( DOWNLOAD_USER_DATA_I2P_SCRAPE_KEY,details );

										}else{

											details[0] = Math.max( details[0], i2p_seeds );
											details[1] = Math.max( details[1], i2p_leechers );
										}
									}


									f_presence_download.setScrapeResult(
										new DownloadScrapeResult()
										{
											@Override
											public Download
											getDownload()
											{
												return( null );
											}

											@Override
											public int
											getResponseType()
											{
												return( DownloadScrapeResult.RT_SUCCESS );
											}

											@Override
											public int
											getSeedCount()
											{
												return( seeds );
											}

											@Override
											public int
											getNonSeedCount()
											{
												return( leechers );
											}

											@Override
											public long
											getScrapeStartTime()
											{
												return( SystemTime.getCurrentTime());
											}

											@Override
											public void
											setNextScrapeStartTime(
												long nextScrapeStartTime)
											{
											}

											@Override
											public long
											getNextScrapeStartTime()
											{
												return( f_next_check );
											}

											@Override
											public String
											getStatus()
											{
												return( "OK" );
											}

											@Override
											public URL
											getURL()
											{
												URL	url_to_report = torrent.isDecentralised()?torrent.getAnnounceURL():DEFAULT_URL;

												return( url_to_report );
											}
										});
								}
							});

			}
		}
		

		
		if ( ready_scrape != null ){
							
			Download f_ready_scrape = ready_scrape;
			
			Torrent torrent = ready_scrape.getTorrent();

			long start 		= now;

				// we scrape around 4 a minute so...
			
			long offset = dht_only_scrapes.size()*60*1000/4;
			
			if ( offset < SCRAPE_MIN_DEFAULT ){
				
				offset = SCRAPE_MIN_DEFAULT;
			}
			
			offset += RandomUtils.nextLong( 2*60*1000 );	// spread things out a bit
			
			long f_next_check = now +  offset;
			
			long[] scrape_data = new long[]{ mono_now + offset, f_next_check, -1, -1 };
			
			ready_scrape.setUserData( SCRAPE_DATA_KEY, scrape_data );

			dht.get(torrent.getHash(),
					"Scrape for '" + ready_scrape.getName() + "'",
					DHTPlugin.FLAG_DOWNLOADING,
					INTERESTING_AVAIL_MAX,
					SCRAPE_TIMEOUT,
					false, false,
					new DHTPluginOperationListener()
					{
						private boolean diversified;
						private int 	leechers = 0;
						private int 	seeds	 = 0;

						private int 	i2p_leechers = 0;
						private int 	i2p_seeds	 = 0;

						@Override
						public boolean
						diversified()
						{
							diversified	= true;

							return( false );
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
							boolean is_leecher = ( value.getFlags() & DHTPlugin.FLAG_DOWNLOADING ) == 1;

							if ( is_leecher ){

								leechers++;

							}else{

								seeds++;
							}

							try{
								String[]	tokens = new String(value.getValue()).split(";");

								for (int i=1;i<tokens.length;i++){

									String	token = tokens[i].trim();

									if ( token.length() > 0 ){

										if ( !Character.isDigit( token.charAt( 0 ))){

											String flag_str = token;

											if ( flag_str.contains("I")){

												if ( is_leecher ){

													i2p_leechers++;

												}else{

													i2p_seeds++;
												}
											}
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
							byte[]	key,
							boolean	timeout_occurred )
						{
							dht_scrapes_active.decrementAndGet();
							dht_scrapes_complete.incrementAndGet();
							
							log( 	f_ready_scrape,
									"Scrape completed (elapsed=" + TimeFormatter.formatColonMillis(SystemTime.getCurrentTime() - start)
											+ "), seeds=" + seeds + ", leechers=" + leechers);

							scrape_data[2] = seeds;
							scrape_data[3] = leechers;
							
							if ( diversified ){

								try{
									this_mon.enter();

									interesting_downloads.remove( f_ready_scrape );

								}finally{

									this_mon.exit();
								}
							}

							if ( i2p_seeds + i2p_leechers > 0 ){

								int[] details = (int[])f_ready_scrape.getUserData( DOWNLOAD_USER_DATA_I2P_SCRAPE_KEY );

								if ( details == null ){

									details = new int[]{ i2p_seeds, i2p_leechers };

									f_ready_scrape.setUserData( DOWNLOAD_USER_DATA_I2P_SCRAPE_KEY,details );

								}else{

									details[0] = Math.max( details[0], i2p_seeds );
									details[1] = Math.max( details[1], i2p_leechers );
								}
							}


							f_ready_scrape.setScrapeResult(
								new DownloadScrapeResult()
								{
									@Override
									public Download
									getDownload()
									{
										return( null );
									}

									@Override
									public int
									getResponseType()
									{
										return( DownloadScrapeResult.RT_SUCCESS );
									}

									@Override
									public int
									getSeedCount()
									{
										return( seeds );
									}

									@Override
									public int
									getNonSeedCount()
									{
										return( leechers );
									}

									@Override
									public long
									getScrapeStartTime()
									{
										return( SystemTime.getCurrentTime());
									}

									@Override
									public void
									setNextScrapeStartTime(
										long nextScrapeStartTime)
									{
									}

									@Override
									public long
									getNextScrapeStartTime()
									{
										return( f_next_check );
									}

									@Override
									public String
									getStatus()
									{
										return( "OK" );
									}

									@Override
									public URL
									getURL()
									{
										URL	url_to_report = torrent.isDecentralised()?torrent.getAnnounceURL():DEFAULT_URL;

										return( url_to_report );
									}
								});
						}
					});
			
			dht_scrapes_active.incrementAndGet();
		}
	}

	@Override
	public void
	stateChanged(
		Download		download,
		int				old_state,
		int				new_state )
	{
		int	state = download.getState();

		try{
			this_mon.enter();

			if ( 	state == Download.ST_DOWNLOADING ||
					state == Download.ST_SEEDING ||
					state == Download.ST_QUEUED ){	// included queued here for the mo to avoid lots
													// of thrash for torrents that flip a lot

				if ( running_downloads.containsKey( download )){

						// force requery

					query_map.put( download, new Long( SystemTime.getCurrentTime()));
				}
			}
		}finally{

			this_mon.exit();
		}

			// don't do anything if paused as we want things to just continue as they are (we would force an announce here otherwise)

		if ( !download.isPaused()){

			checkDownloadForRegistration( download, false );
		}
	}

	public void
	announceAll()
	{
		log.log( "Announce-all requested" );

		Long now = new Long( SystemTime.getCurrentTime());

		try{
			this_mon.enter();

			Iterator<Map.Entry<Download,Long>> it = query_map.entrySet().iterator();

			while( it.hasNext()){

				Map.Entry<Download,Long>	entry = it.next();

				entry.setValue( now );
			}
		}finally{

			this_mon.exit();
		}
	}

	private void
	announce(
		Download	download )
	{
		log.log( "Announce requested for " + download.getName());

		try{
			this_mon.enter();

			query_map.put(download,  SystemTime.getCurrentTime());

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	positionChanged(
		Download		download,
		int 			oldPosition,
		int 			newPosition )
	{
	}

	protected void
	configChanged()
	{
		Download[] downloads = plugin_interface.getDownloadManager().getDownloads();

		for (int i=0;i<downloads.length;i++){

			checkDownloadForRegistration(downloads[i], false );
		}
	}

		/**
		 * This is used by the dhtscraper plugin
		 */

	public DownloadScrapeResult
	scrape(
		byte[]		hash )
	{
		final int[]	seeds 		= {0};
		final int[] leechers 	= {0};

		final AESemaphore	sem = new AESemaphore( "DHTTrackerPlugin:scrape" );

		dht.get(hash,
				"Scrape for " + ByteFormatter.encodeString( hash ).substring( 0, 16 ),
				DHTPlugin.FLAG_DOWNLOADING,
				NUM_WANT,
				SCRAPE_TIMEOUT,
				false, false,
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
						if (( value.getFlags() & DHTPlugin.FLAG_DOWNLOADING ) == 1 ){

							leechers[0]++;

						}else{

							seeds[0]++;
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
						byte[]	key,
						boolean	timeout_occurred )
					{
						sem.release();
					}
				});

		sem.reserve();

		return(
				new DownloadScrapeResult()
				{
					@Override
					public Download
					getDownload()
					{
						return( null );
					}

					@Override
					public int
					getResponseType()
					{
						return( DownloadScrapeResult.RT_SUCCESS );
					}

					@Override
					public int
					getSeedCount()
					{
						return( seeds[0] );
					}

					@Override
					public int
					getNonSeedCount()
					{
						return( leechers[0] );
					}

					@Override
					public long
					getScrapeStartTime()
					{
						return( 0 );
					}

					@Override
					public void
					setNextScrapeStartTime(
						long nextScrapeStartTime)
					{
					}

					@Override
					public long
					getNextScrapeStartTime()
					{
						return( 0 );
					}

					@Override
					public String
					getStatus()
					{
						return( "OK" );
					}

					@Override
					public URL
					getURL()
					{
						return( null );
					}
				});
	}

	protected void
	increaseActive(
		Download		dl )
	{
		try{
			this_mon.enter();

			Integer	active_i = (Integer)in_progress.get( dl );

			int	active = active_i==null?0:active_i.intValue();

			in_progress.put( dl, new Integer( active+1 ));

		}finally{

			this_mon.exit();
		}
	}

	protected void
	decreaseActive(
		Download		dl )
	{
		try{
			this_mon.enter();

			Integer	active_i = (Integer)in_progress.get( dl );

			if ( active_i == null ){

				Debug.out( "active count inconsistent" );

			}else{

				int	active = active_i.intValue()-1;

				if ( active == 0 ){

					in_progress.remove( dl );

				}else{

					in_progress.put( dl, new Integer( active ));
				}
			}
		}finally{

			this_mon.exit();
		}
	}

	protected boolean
	isActive(
		Download		dl )
	{
		try{
			this_mon.enter();

			return( in_progress.get(dl) != null );

		}finally{

			this_mon.exit();
		}
	}

	protected class
	RegistrationDetails
	{
		//private static final int DERIVED_ACTIVE_MIN_MILLIS	= 2*60*60*1000;

		private putDetails			put_details;
		private byte				flags;
		private trackerTarget[]		put_targets;
		private List<trackerTarget>	not_put_targets;

		//private long			derived_active_start	= -1;
		//private long			previous_metric;

		protected
		RegistrationDetails(
			Download			_download,
			int					_reg_type,
			putDetails			_put_details,
			byte				_flags )
		{
			put_details		= _put_details;
			flags			= _flags;

			getTrackerTargets( _download, _reg_type );
		}

		protected void
		update(
			putDetails		_put_details,
			byte			_flags )
		{
			put_details	= _put_details;
			flags		= _flags;
		}

		protected boolean
		updateTargets(
			Download			_download,
			int					_reg_type )
		{
			trackerTarget[]	old_put_targets = put_targets;

			getTrackerTargets( _download, _reg_type );

				// first remove any redundant entries

			for (int i=0;i<old_put_targets.length;i++){

				boolean	found = false;

				byte[]	old_hash = old_put_targets[i].getHash();

				for (int j=0;j<put_targets.length;j++){

					if ( Arrays.equals( put_targets[j].getHash(), old_hash )){

						found	= true;

						break;
					}
				}

				if ( !found ){

					trackerRemove( _download, old_put_targets[i] );
				}
			}

				// now look to see if we have any new stuff

			boolean	changed = false;

			for (int i=0;i<put_targets.length;i++){

				byte[]	new_hash = put_targets[i].getHash();

				boolean	found = false;

				for (int j=0;j<old_put_targets.length;j++){

					if ( Arrays.equals( old_put_targets[j].getHash(), new_hash )){

						found = true;

						break;
					}
				}

				if ( !found ){

					changed = true;
				}
			}

			return( changed );
		}

		protected putDetails
		getPutDetails()
		{
			return( put_details );
		}

		protected byte
		getFlags()
		{
			return( flags );
		}

		protected trackerTarget[]
		getTargets(
			boolean		for_put )
		{
			if ( for_put || not_put_targets == null ){

				return( put_targets );

			}else{

				List<trackerTarget>	result = new ArrayList<>(Arrays.asList(put_targets));

				for (int i=0;i<not_put_targets.size()&& i < 2; i++ ){

					trackerTarget target = (trackerTarget)not_put_targets.remove(0);

					not_put_targets.add( target );

					// System.out.println( "Mixing in " + target.getDesc());

					result.add( target );
				}

				return( (trackerTarget[])result.toArray( new trackerTarget[result.size()]));
			}
		}

		protected void
    	getTrackerTargets(
    		Download		download,
    		int				type )
    	{
    		byte[]	torrent_hash = download.getTorrent().getHash();

    		List<trackerTarget>	result = new ArrayList<>();

    		if ( type == REG_TYPE_FULL ){

    			result.add( new trackerTarget( torrent_hash, REG_TYPE_FULL, "" ));
    		}
 /*
    		if ( ADD_ASN_DERIVED_TARGET ){

	    	    NetworkAdminASN net_asn = NetworkAdmin.getSingleton().getCurrentASN();

	    	    String	as 	= net_asn.getAS();
	    	    String	asn = net_asn.getASName();

	    		if ( as.length() > 0 && asn.length() > 0 ){

	    			String	key = "azderived:asn:" + as;

	    			try{
	    				byte[] asn_bytes = key.getBytes( "UTF-8" );

	    				byte[] key_bytes = new byte[torrent_hash.length + asn_bytes.length];

	    				System.arraycopy( torrent_hash, 0, key_bytes, 0, torrent_hash.length );

	    				System.arraycopy( asn_bytes, 0, key_bytes, torrent_hash.length, asn_bytes.length );

	    				result.add( new trackerTarget( key_bytes, REG_TYPE_DERIVED, asn + "/" + as ));

	    			}catch( Throwable e ){

	    				Debug.printStackTrace(e);
	    			}
	    		}
    		}

    		if ( ADD_NETPOS_DERIVED_TARGETS ){

	    		long	now = SystemTime.getMonotonousTime();

	    		boolean	do_it;

	       		Long	metric = (Long)download.getUserData( DL_DERIVED_METRIC_KEY );

	       		boolean	do_it_now = metric != null;

	    		if ( derived_active_start >= 0 && now - derived_active_start <= DERIVED_ACTIVE_MIN_MILLIS ){

	    			do_it = true;

	    			if ( metric == null ){

	    				metric = new Long( previous_metric );
	    			}
	    		}else{

	    			if ( do_it_now ){

	    				do_it = true;

	    			}else{

	    				derived_active_start = -1;

	    				do_it = false;
	    			}
	    		}

	    		boolean	newly_active = false;

	    		if ( do_it_now ){

	    			newly_active = derived_active_start == -1;

	    			derived_active_start = now;
	    		}

	    		List<trackerTarget>	skipped_targets = null;

	    		if ( do_it ){

	    			previous_metric = metric.longValue();

		    		try{
		    			DHTNetworkPosition[] positions = getNetworkPositions();

		    			for (int i=0;i<positions.length;i++){

		    				DHTNetworkPosition pos = positions[i];

		    				if ( pos.getPositionType() == DHTNetworkPosition.POSITION_TYPE_VIVALDI_V2 ){

		    					if ( pos.isValid()){

		    						List<Object[]>	derived_results = getVivaldiTargets( torrent_hash, pos.getLocation());

		    		    			int	num_to_add = metric.intValue() * derived_results.size() / 100;

		    		    			// System.out.println( download.getName() + ": metric=" + metric + ", adding=" + num_to_add );

		    						for (int j=0;j<derived_results.size();j++){

		    							Object[] entry = derived_results.get(j);

		    							// int	distance = ((Integer)entry[0]).intValue();

		    							trackerTarget	target= (trackerTarget)entry[1];

		    							if ( j < num_to_add ){

		    								result.add( target );

		    							}else{

		    								if ( skipped_targets == null ){

		    									skipped_targets = new ArrayList<trackerTarget>();
		    								}

		    								skipped_targets.add( target );
		    							}
		    						}
		    					}
		    				}
		    			}
		    		}catch( Throwable e ){

		    			Debug.printStackTrace(e);
		    		}
	    		}

	    		not_put_targets = skipped_targets;
    		}
    		*/

	    	put_targets 	= result.toArray( new trackerTarget[result.size()]);
    	}
	}

	/*
	private DHTNetworkPosition[]
	getNetworkPositions()
	{
		DHTNetworkPosition[] res = current_network_positions;

		long	now = SystemTime.getMonotonousTime();

		if ( 	res == null ||
				now - last_net_pos_time >  30*60*1000 ){

			res = current_network_positions = DHTNetworkPositionManager.getLocalPositions();

			last_net_pos_time = now;
		}

		return( res );
	}
	*/

	private void
	log(
		Download		download,
		String			str )
	{
		log.log( download, LoggerChannel.LT_INFORMATION, str );
	}

	public TrackerPeerSource
	getTrackerPeerSource(
		final Download		download )
	{
		return(
			new TrackerPeerSourceAdapter()
			{
				private long	last_fixup;
				private boolean	updating;
				private int		status		= TrackerPeerSource.ST_UNKNOWN;
				private String  status_str;
				private long	next_time	= -1;
				private int[]	run_data;
				private long[]	scrape_data;

				private void
				fixup()
				{
					long now = SystemTime.getMonotonousTime();

					if ( now - last_fixup > 5*1000 ){

						String new_status_str = null;
						
						try{
							this_mon.enter();

							updating 	= false;
							next_time	= -1;

							run_data = running_downloads.get( download );

							scrape_data = (long[])download.getUserData( SCRAPE_DATA_KEY );
							
							if ( run_data != null ){

								if ( in_progress.containsKey( download )){

									updating = true;
								}

								status = initialised_sem.isReleasedForever()? TrackerPeerSource.ST_ONLINE: TrackerPeerSource.ST_STOPPED;

								Long l_next_time = query_map.get( download );

								if ( l_next_time != null ){

									next_time = l_next_time.longValue();
								}
							}else if ( interesting_downloads.containsKey( download )){

								// int dl_state = download.getState();
								//status = dl_state == Download.ST_QUEUED?TrackerPeerSource.ST_QUEUED:TrackerPeerSource.ST_STOPPED;
								
								status = TrackerPeerSource.ST_STOPPED;							
									
								if ( scrape_data != null ){
									
									next_time = scrape_data[1];
								}
							}else{

								int dl_state = download.getState();

								if ( 	dl_state == Download.ST_DOWNLOADING ||
										dl_state == Download.ST_SEEDING ||
										dl_state == Download.ST_QUEUED ){

									status = TrackerPeerSource.ST_DISABLED;

									String reason = (String)download.getUserData( LATEST_REGISTER_REASON );
									
									if ( reason != null && !reason.isEmpty()){
										
										new_status_str = reason;
									}
								}else{

									status = TrackerPeerSource.ST_STOPPED;
								}
							}

							if ( run_data == null ){

								run_data = run_data_cache.get( download );
							}
						}finally{

							this_mon.exit();
						}

						String[]	sources = download.getListAttribute( ta_peer_sources );

						boolean	ok = false;

						if ( sources != null ){

							for (int i=0;i<sources.length;i++){

								if ( sources[i].equalsIgnoreCase( "DHT")){

									ok	= true;

									break;
								}
							}
						}

						if ( !ok ){

							status = TrackerPeerSource.ST_DISABLED;
							
							boolean done = false;
							
							try{
								if ( download.getTorrent().isPrivate()){
												
									new_status_str = MessageText.getString( "label.private" );
									
									done = true;
								}
							}catch( Throwable e ){
								
							}
					
							if ( !done ){
								
								new_status_str = MessageText.getString( "label.peer.source.disabled" );
							}
						}

						status_str = new_status_str;
						
						last_fixup = now;
					}
				}

				@Override
				public int
				getType()
				{
					return( TrackerPeerSource.TP_DHT );
				}

				@Override
				public String
				getName()
				{
					return( "DHT: " + model.getStatus().getText());
				}

				@Override
				public int
				getStatus()
				{
					fixup();

					return( status );
				}

				@Override
				public String 
				getStatusString()
				{
					fixup();

					return( status_str );
				}
				
				@Override
				public int
				getSeedCount()
				{
					fixup();

					if ( run_data != null ){
						
						return( run_data[1] );
						
					}else if ( scrape_data != null ){
						
						return((int)scrape_data[2] );
						
					}else{
						
						return( -1 );
					}
				}

				@Override
				public int
				getLeecherCount()
				{
					fixup();

					if ( run_data != null ){
						
						return( run_data[2] );
						
					}else if ( scrape_data != null ){
						
						return((int)scrape_data[3] );
						
					}else{
						
						return( -1 );
					}
				}

				@Override
				public int
				getPeers()
				{
					fixup();

					if ( run_data == null ){

						return( -1 );
					}

					return( run_data[3] );
				}

				@Override
				public int
				getLastUpdate()
				{
					fixup();

					if ( run_data == null ){

						return( 0 );
					}

					return( run_data[4] );
				}

				@Override
				public int
				getSecondsToUpdate()
				{
					fixup();

					if ( next_time < 0 ){

						return( Integer.MIN_VALUE );
					}

					return((int)(( next_time - SystemTime.getCurrentTime())/1000 ));
				}

				@Override
				public int
				getInterval()
				{
					fixup();

					if ( run_data == null ){

						return( -1 );
					}

					return((int)(current_announce_interval/1000));
				}

				@Override
				public int
				getMinInterval()
				{
					fixup();

					if ( run_data == null ){

						return( -1 );
					}

					return( ANNOUNCE_MIN_DEFAULT/1000 );
				}

				@Override
				public boolean
				isUpdating()
				{
					return( updating );
				}

				@Override
				public boolean
				canManuallyUpdate()
				{
					fixup();

					return( run_data != null || scrape_data != null );
				}

				@Override
				public void
				manualUpdate()
				{
					download.setUserData( SCRAPE_DATA_KEY, null );
					
					announce( download );
				}
				
				@Override
				public long[]
				getReportedStats()
				{
					return( null );
				}
			});
	}


	public TrackerPeerSource[]
	getTrackerPeerSources(
		final Torrent		torrent )
	{
		TrackerPeerSource vuze_dht =
			new TrackerPeerSourceAdapter()
			{
				private volatile boolean	query_done;
				private volatile int		status		= TrackerPeerSource.ST_INITIALISING;

				private volatile int		seeds 		= 0;
				private volatile int		leechers 	= 0;


				private void
				fixup()
				{
					if ( initialised_sem.isReleasedForever()){

						synchronized( this ){

							if ( query_done ){

								return;
							}

							query_done = true;

							status = TrackerPeerSource.ST_UPDATING;
						}

						dht.get(	torrent.getHash(),
									"Availability lookup for '" + torrent.getName() + "'",
									DHTPlugin.FLAG_DOWNLOADING,
									NUM_WANT,
									ANNOUNCE_DERIVED_TIMEOUT,
									false, true,
									new DHTPluginOperationListener()
									{
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
											return( true );
										}

										@Override
										public void
										valueRead(
											DHTPluginContact	originator,
											DHTPluginValue		value )
										{
											if (( value.getFlags() & DHTPlugin.FLAG_DOWNLOADING ) == 1 ){

												seeds++;

											}else{

												leechers++;
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
											status		= TrackerPeerSource.ST_ONLINE;
										}
									});
					}
				}

				@Override
				public int
				getType()
				{
					return( TrackerPeerSource.TP_DHT );
				}

				@Override
				public String
				getName()
				{
					return( Constants.APP_NAME + " DHT" );
				}

				@Override
				public int
				getStatus()
				{
					fixup();

					return( status );
				}

				@Override
				public int
				getSeedCount()
				{
					fixup();

					int	result = seeds;

					if ( result == 0 && status != TrackerPeerSource.ST_ONLINE ){

						return( -1 );
					}

					return( result );
				}

				@Override
				public int
				getLeecherCount()
				{
					fixup();

					int	result = leechers;

					if ( result == 0 && status != TrackerPeerSource.ST_ONLINE ){

						return( -1 );
					}

					return( result );
				}

				@Override
				public int
				getPeers()
				{
					return( -1 );
				}

				@Override
				public boolean
				isUpdating()
				{
					return( status == TrackerPeerSource.ST_UPDATING );
				}

			};

		if ( alt_lookup_handler != null ){

			TrackerPeerSource alt_dht =
					new TrackerPeerSourceAdapter()
					{
						private volatile int		status 	= TrackerPeerSource.ST_UPDATING;
						private volatile int		peers 	= 0;

						{
							alt_lookup_handler.get(
									torrent.getHash(),
									false,
									new DHTTrackerPluginAlt.LookupListener()
									{
										@Override
										public void
										foundPeer(
											InetSocketAddress	address )
										{
											peers++;
										}

										@Override
										public boolean
										isComplete()
										{
											return( false );
										}

										@Override
										public void
										completed()
										{
											status = TrackerPeerSource.ST_ONLINE;
										}
									});
						}


						@Override
						public int
						getType()
						{
							return( TrackerPeerSource.TP_DHT );
						}

						@Override
						public String
						getName()
						{
							return( "Mainline DHT" );
						}

						@Override
						public int
						getStatus()
						{
							return( status );
						}

						@Override
						public int
						getPeers()
						{
							int	result = peers;

							if ( result == 0 && status != TrackerPeerSource.ST_ONLINE ){

								return( -1 );
							}

							return( result );
						}

						@Override
						public boolean
						isUpdating()
						{
							return( status == TrackerPeerSource.ST_UPDATING );
						}

					};

			return( new TrackerPeerSource[]{ vuze_dht, alt_dht } );

		}else{

			return( new TrackerPeerSource[]{ vuze_dht } );
		}
	}



	/*
	public static List<Object[]>
	getVivaldiTargets(
		byte[]					torrent_hash,
		double[]				loc )
	{
		List<Object[]>	derived_results = new ArrayList<>();

		String	loc_str = "";

		for (int j=0;j<loc.length;j++){

			loc_str += (j==0?"":",") + loc[j];
		}

		TriangleSlicer slicer = new TriangleSlicer( 25 );

		double	t1_x = loc[0];
		double	t1_y = loc[1];
		double	t2_x = loc[2];
		double	t2_y = loc[3];

		int[] triangle1 = slicer.findVertices( t1_x, t1_y );

		int[] triangle2 = slicer.findVertices( t2_x, t2_y );


		for (int j=0;j<triangle1.length;j+=2 ){

			int	t1_vx = triangle1[j];
			int t1_vy = triangle1[j+1];

			double	t1_distance = getDistance( t1_x, t1_y, t1_vx, t1_vy );

			for (int k=0;k<triangle2.length;k+=2 ){

				int	t2_vx = triangle2[k];
				int t2_vy = triangle2[k+1];

				double	t2_distance = getDistance( t2_x, t2_y, t2_vx, t2_vy );

					// these distances are in different dimensions - make up a combined distance

				double distance = getDistance( t1_distance, 0, 0, t2_distance );


				String	key = "azderived:vivaldi:";

				String v_str = 	t1_vx + "." + t1_vy + "." + t2_vx + "." + t2_vy;

				key += v_str;

				try{
					byte[] v_bytes = key.getBytes( "UTF-8" );

					byte[] key_bytes = new byte[torrent_hash.length + v_bytes.length];

					System.arraycopy( torrent_hash, 0, key_bytes, 0, torrent_hash.length );

					System.arraycopy( v_bytes, 0, key_bytes, torrent_hash.length, v_bytes.length );

					derived_results.add(
						new Object[]{
							new Integer((int)distance),
							new trackerTarget( key_bytes, REG_TYPE_DERIVED, "Vivaldi: " + v_str ) });


				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}
			}
		}

		Collections.sort(
			derived_results,
			new Comparator<Object[]>()
			{
				public int
				compare(
					Object[] 	entry1,
					Object[] 	entry2 )
				{
					int	d1 = ((Integer)entry1[0]).intValue();
					int	d2 = ((Integer)entry2[0]).intValue();

					return( d1 - d2 );
				}
			});

		return( derived_results );
	}

	protected static double
	getDistance(
		double	x1,
		double	y1,
		double	x2,
		double	y2 )
	{
		return(Math.sqrt((x2-x1)*(x2-x1) + (y2-y1)*(y2-y1)));
	}
	*/

	protected static class
	putDetails
	{
		private String	encoded;
		private String	ip_override;
		private int		tcp_port;
		private int		udp_port;
		private boolean	i2p;

		private
		putDetails(
			String	_encoded,
			String	_ip,
			int		_tcp_port,
			int		_udp_port,
			boolean	_i2p )
		{
			encoded			= _encoded;
			ip_override		= _ip;
			tcp_port		= _tcp_port;
			udp_port		= _udp_port;
			i2p				= _i2p;
		}

		protected String
		getEncoded()
		{
			return( encoded );
		}

		protected String
		getIPOverride()
		{
			return( ip_override );
		}

		protected int
		getTCPPort()
		{
			return( tcp_port );
		}

		protected int
		getUDPPort()
		{
			return( udp_port );
		}

		private boolean
		hasI2P()
		{
			return( i2p );
		}

		protected boolean
		sameAs(
			putDetails		other )
		{
				// ignore i2p setting as this can flip about with network mixing
			
			if ( !StringCompareUtils.equals( ip_override, other.ip_override )){
				
				return( false );
			}
			
			if ( tcp_port == other.tcp_port && udp_port == other.udp_port ){
				
				return( true );
			}
			
			return( false );
		}
	}

	public static class
	trackerTarget
	{
		private String		desc;
		private	byte[]		hash;
		private int			type;

		protected
		trackerTarget(
			byte[]			_hash,
			int				_type,
			String			_desc )
		{
			hash		= _hash;
			type		= _type;
			desc		= _desc;
		}

		public int
		getType()
		{
			return( type );
		}

		public byte[]
		getHash()
		{
			return( hash );
		}

		public String
		getDesc(
			String	prefix )
		{
			if ( type != REG_TYPE_FULL ){

				return( prefix + " (" + desc + ")" );
			}

			return( prefix );
		}
	}

	public static class
	TriangleSlicer
	{
		int width;

		private double w;
		private double w2;
		private double h;

		private double tan60;

		public TriangleSlicer(int width) {
			this.width = width;

			this.w = (float) width;
			this.w2 = w / 2;
			this.h = Math.cos(Math.PI / 6) * w;

			this.tan60 = Math.tan(Math.PI / 3);

		}

		/**
		 *
		 * @param x
		 * @param y
		 * @return an array of int values being x,y coordinate pairs
		 */
		public int[] findVertices(double x,double y) {

			int yN = (int) Math.floor((y / h));
			int xN = (int) Math.floor((x /w2));

			double v1x,v2x,v3x,v1y,v2y,v3y;

			//weither the triangle is like /\ (true) or \/ (false)
			boolean upTriangle;

			if((xN+yN) % 2 == 0) {
				// we have a / separator in the "cell"
				if( (y-h*yN) > (x-w2*xN) * tan60 ) {
					//we're in the upper part
					upTriangle = false;
					v1x = w2 * (xN - 1);
					v1y = h * (yN + 1) ;
				} else {
					//we're in the lower part
					upTriangle = true;
					v1x = w2 * xN;
					v1y = h * yN;
				}
			} else {
				// We have a \ separator in the "cell"
				if( (y- h*yN) > (w2 - (x-w2*xN)) * tan60 ) {
					//we're in the upper part
					upTriangle = false;
					v1x = w2 * xN;
					v1y = h * (yN+1);
				} else {
					//we're in the lower part
					upTriangle = true;
					v1x = w2 * (xN - 1);
					v1y = h * yN;
				}
			}

			if(upTriangle) {
				v2x = v1x + w;
				v2y = v1y;

				v3x = v1x + w2;
				v3y = v1y + h;
			} else {
				v2x = v1x + w;
				v2y = v1y;

				v3x = v1x + w2;
				v3y = v1y - h;
			}

			int[] result = new int[6];

			result[0] = (int) v1x;
			result[1] = (int) v1y;

			result[2] = (int) v2x;
			result[3] = (int) v2y;

			result[4] = (int) v3x;
			result[5] = (int) v3y;

			return result;

		}
	}
}
