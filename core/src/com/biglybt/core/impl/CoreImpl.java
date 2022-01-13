/*
 * Created on 13-Jul-2004
 * Created by Paul Gardner
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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

package com.biglybt.core.impl;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import com.biglybt.core.*;
import com.biglybt.core.backup.BackupManagerFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.config.impl.TransferSpeedValidator;
import com.biglybt.core.custom.CustomizationManagerFactory;
import com.biglybt.core.dht.DHT;
import com.biglybt.core.dht.DHTListener;
import com.biglybt.core.dht.speed.DHTSpeedTester;
import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.disk.DiskManagerFactory;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerAdapter;
import com.biglybt.core.global.GlobalManagerFactory;
import com.biglybt.core.global.GlobalManagerStats;
import com.biglybt.core.global.GlobalMangerProgressListener;
import com.biglybt.core.instancemanager.ClientInstanceManager;
import com.biglybt.core.instancemanager.ClientInstanceManagerAdapter;
import com.biglybt.core.instancemanager.ClientInstanceManagerFactory;
import com.biglybt.core.instancemanager.ClientInstanceTracked;
import com.biglybt.core.internat.LocaleUtil;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.ipfilter.IpFilterManager;
import com.biglybt.core.ipfilter.IpFilterManagerFactory;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.nat.NATTraverser;
import com.biglybt.core.networkmanager.NetworkManager;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.networkmanager.admin.NetworkAdminNetworkInterface;
import com.biglybt.core.networkmanager.admin.NetworkAdminNetworkInterfaceAddress;
import com.biglybt.core.networkmanager.admin.NetworkAdminPropertyChangeListener;
import com.biglybt.core.networkmanager.impl.tcp.TCPNetworkManager;
import com.biglybt.core.networkmanager.impl.udp.UDPNetworkManager;
import com.biglybt.core.pairing.PairingManagerFactory;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.PEPeerSource;
import com.biglybt.core.peermanager.PeerManager;
import com.biglybt.core.peermanager.nat.PeerNATTraverser;
import com.biglybt.core.proxy.AEProxyFactory;
import com.biglybt.core.proxy.AEProxySelectorFactory;
import com.biglybt.core.security.BGSpongy;
import com.biglybt.core.security.CryptoManager;
import com.biglybt.core.security.CryptoManagerFactory;
import com.biglybt.core.security.SESecurityManager;
import com.biglybt.core.speedmanager.SpeedLimitHandler;
import com.biglybt.core.speedmanager.SpeedManager;
import com.biglybt.core.speedmanager.SpeedManagerAdapter;
import com.biglybt.core.speedmanager.SpeedManagerFactory;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.tag.TagType;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.tracker.AllTrackersManager;
import com.biglybt.core.tracker.AllTrackersManager.AllTrackers;
import com.biglybt.core.tracker.client.TRTrackerAnnouncer;
import com.biglybt.core.tracker.client.TRTrackerAnnouncerResponse;
import com.biglybt.core.tracker.host.TRHost;
import com.biglybt.core.tracker.host.TRHostFactory;
import com.biglybt.core.update.ClientRestarterFactory;
import com.biglybt.core.util.*;
import com.biglybt.core.versioncheck.VersionCheckClient;
import com.biglybt.core.vuzefile.VuzeFile;
import com.biglybt.core.vuzefile.VuzeFileComponent;
import com.biglybt.core.vuzefile.VuzeFileHandler;
import com.biglybt.core.vuzefile.VuzeFileProcessor;
import com.biglybt.pif.*;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.torrent.TorrentDownloader;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.UIManagerEvent;
import com.biglybt.pif.utils.DelayedTask;
import com.biglybt.pif.utils.PowerManagementListener;
import com.biglybt.pif.utils.ScriptProvider;
import com.biglybt.pif.utils.StaticUtilities;
import com.biglybt.pifimpl.PluginUtils;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.pifimpl.local.clientid.ClientIDPlugin;
import com.biglybt.pifimpl.local.download.DownloadManagerImpl;
import com.biglybt.pifimpl.local.utils.UtilitiesImpl;
import com.biglybt.platform.PlatformManager;
import com.biglybt.platform.PlatformManagerCapabilities;
import com.biglybt.platform.PlatformManagerFactory;
import com.biglybt.platform.PlatformManagerListener;
import com.biglybt.plugin.dht.DHTPlugin;
import com.biglybt.plugin.startstoprules.defaultplugin.DefaultRankCalculator;
import com.biglybt.plugin.startstoprules.defaultplugin.StartStopRulesDefaultPlugin;
import com.biglybt.plugin.tracker.dht.DHTTrackerPlugin;
import com.biglybt.plugin.upnp.UPnPPlugin;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.util.MapUtils;

/**
 * @author parg
 *
 */

public class
CoreImpl
	implements Core
{
	public static final boolean DEBUG_STARTUPTIME = System.getProperty(
			"DEBUG_STARTUPTIME", "0").equals("1");

	final static LogIDs LOGID = LogIDs.CORE;
	protected static Core singleton;
	protected static final AEMonitor			class_mon	= new AEMonitor( "Core:class" );

	private static final String DM_ANNOUNCE_KEY	= "Core:announce_key";

		// one of the ideas behind the separate 'load' and 'initialize' call is that the 'load' is done
		// BEFORE any downloads start - this gives interested plugins the opportunity to get things in
		// place while knowing that peers connections haven't started etc.
		// If there's a problem with a plugin taking a long time during 'load' then fix the plugin
		// So PLEASE don't change this to true!

	private static final boolean LOAD_PLUGINS_IN_OTHER_THREAD = false;

	/**
	 * Listeners that will be fired after core has completed initialization
	 */
	static List<CoreRunningListener> coreRunningListeners = new ArrayList<>(1);

	static final AEMonitor mon_coreRunningListeners = new AEMonitor("CoreCreationListeners");
	private static long lastDebugTime = 0;

	public static Core
	create()

		throws CoreException
	{
		try{
			class_mon.enter();

			if ( singleton != null ){

				throw( new CoreException( Constants.APP_NAME + " core already instantiated" ));
			}

			singleton	= new CoreImpl();

			return( singleton );

		}finally{

			class_mon.exit();
		}
	}

	public static boolean
	isCoreAvailable()
	{
		return( singleton != null );
	}

	public static boolean
	isCoreRunning()
	{
		return( singleton != null && singleton.isStarted() );
	}

	public static Core
	getSingleton()

		throws CoreException
	{
		if ( singleton == null ){

			throw( new CoreException( "core not instantiated"));
		}

		return( singleton );
	}

	final PluginInitializer 	pi;
	GlobalManager		global_manager;
	private final ClientInstanceManager instance_manager;
	SpeedManager		speed_manager;
	private final CryptoManager		crypto_manager;
	private final NATTraverser		nat_traverser;

	private final long create_time;


	private volatile boolean				started;
	volatile boolean				stopped;
	volatile boolean				restarting;

	private final CopyOnWriteList<CoreLifecycleListener>	lifecycle_listeners		= new CopyOnWriteList<>();
	
	private boolean					ll_started;
	
	private final List<CoreOperationListener>				operation_listeners		= new ArrayList<>();
	private final CopyOnWriteList<CoreOperation>			operations				= new CopyOnWriteList<>();
	
	private final CopyOnWriteList<PowerManagementListener>	power_listeners = new CopyOnWriteList<>();

	final AESemaphore			stopping_sem	= new AESemaphore( "Core::stopping" );

	private final AEMonitor			this_mon		= new AEMonitor( "Core" );

	boolean ca_shutdown_computer_after_stop	= false;
	long	ca_last_time_downloading 		= -1;
	long	ca_last_time_seeding 			= -1;

	private boolean	ra_restarting		= false;
	private long	ra_last_total_data	= -1;
	private long	ra_last_data_time	= 0;

	private boolean prevent_sleep_remove_trigger	= false;

	protected CoreImpl()
	{
		try{
			
			create_time = SystemTime.getCurrentTime();
		
			if (DEBUG_STARTUPTIME) {
				lastDebugTime = System.currentTimeMillis();
			}
	
			COConfigurationManager.initialise();
	
			if (DEBUG_STARTUPTIME) {
				logTime("ConfigMan.init");
			}
	
			MessageText.loadBundle();
	
			if (DEBUG_STARTUPTIME) {
				logTime("MessageText");
			}
	
			AEDiagnostics.startup( COConfigurationManager.getBooleanParameter( "diags.enable.pending.writes", false ));
	
			COConfigurationManager.setParameter( "diags.enable.pending.writes", false );
	
			AEDiagnostics.markDirty();
	
			AETemporaryFileHandler.startup();
	
			AEThread2.setOurThread();
	
				// set up a backwards pointer from config -> app dir so we can derive one from the other. It'll get saved on closedown, no need to do so now
	
			COConfigurationManager.setParameter( "azureus.application.directory", FileUtil.newFile( SystemProperties.getApplicationPath()).getAbsolutePath());
			COConfigurationManager.setParameter( "azureus.user.directory", FileUtil.newFile( SystemProperties.getUserPath()).getAbsolutePath());
				
			crypto_manager = CryptoManagerFactory.getSingleton();
	
			PlatformManagerFactory.getPlatformManager().addListener(
				new PlatformManagerListener()
				{
					@Override
					public int
					eventOccurred(
						int		type )
					{
						if ( type == ET_SHUTDOWN ){
	
							if (Logger.isEnabled()){
								Logger.log(new LogEvent(LOGID, "Platform manager requested shutdown"));
							}
	
							COConfigurationManager.save();
	
							requestStop();
	
							return( 0 );
	
						}else if ( type == ET_SUSPEND ){
	
							if (Logger.isEnabled()){
								Logger.log(new LogEvent(LOGID, "Platform manager requested suspend"));
							}
	
							COConfigurationManager.save();
	
						}else if ( type == ET_RESUME ){
	
							if (Logger.isEnabled()){
								Logger.log(new LogEvent(LOGID, "Platform manager requested resume"));
							}
	
							announceAll( true );
						}
	
						return( -1 );
					}
				});
	
				//ensure early initialization
	
			CustomizationManagerFactory.getSingleton().initialize();
	
				// NetworkManager.getSingleton() (for example) results in TCP listen being setup - in case another instance
				// is still in the process of closing poke the 'canStart' method here to back things off if so
			
			canStart( 15 );
			
			AEProxySelectorFactory.getSelector();
	
			if (DEBUG_STARTUPTIME) {
				logTime("Init1");
			}
		
			DiskManagerFactory.initialise( this );
			
			NetworkManager.getSingleton();
	
			if (DEBUG_STARTUPTIME) {
				logTime("Init NetworkManager");
			}
	
			PeerManager.getSingleton();
	
			if (DEBUG_STARTUPTIME) {
				logTime("Init PeerManager");
			}
		
			new ClientIDPlugin().initialize( this );
	
			pi = PluginInitializer.getSingleton( this );
	
			if (DEBUG_STARTUPTIME) {
				logTime("Init PluginInitializer");
			}
	
			AEProxyFactory.initialise( this );
			
			BGSpongy.initialize( this );

			instance_manager =
				ClientInstanceManagerFactory.getSingleton(
					new ClientInstanceManagerAdapter()
					{
						@Override
						public String
						getID()
						{
							return( COConfigurationManager.getStringParameter( "ID", "" ));
						}
	
						@Override
						public InetAddress
						getPublicAddress()
						{
							return( PluginInitializer.getDefaultInterface().getUtilities().getPublicAddress());
						}
	
						@Override
						public int[]
						getPorts()
						{
							return( new int[]{
								TCPNetworkManager.getSingleton().getDefaultTCPListeningPortNumber(),
								UDPNetworkManager.getSingleton().getUDPListeningPortNumber(),
								UDPNetworkManager.getSingleton().getUDPNonDataListeningPortNumber()});
	
						}
						@Override
						public VCPublicAddress
						getVCPublicAddress()
						{
							return(
								new VCPublicAddress()
								{
									private final VersionCheckClient vcc = VersionCheckClient.getSingleton();
	
									@Override
									public String
									getAddress()
									{
										return( vcc.getExternalIpAddress( true, false ));
									}
	
									@Override
									public long
									getCacheTime()
									{
										return( vcc.getSingleton().getCacheTime( false ));
									}
								});
						}
	
						@Override
						public ClientInstanceTracked.TrackTarget
						track(
							byte[]		hash )
						{
							List	dms = getGlobalManager().getDownloadManagers();
	
							Iterator	it = dms.iterator();
	
							DownloadManager	matching_dm = null;
	
							try{
								while( it.hasNext()){
	
									DownloadManager	dm = (DownloadManager)it.next();
	
									TOTorrent	torrent = dm.getTorrent();
	
									if ( torrent == null ){
	
										continue;
									}
	
									byte[]	sha1_hash = (byte[])dm.getUserData( "AZInstanceManager::sha1_hash" );
	
									if ( sha1_hash == null ){
	
										sha1_hash	= new SHA1Simple().calculateHash( torrent.getHash());
	
										dm.setUserData( "AZInstanceManager::sha1_hash", sha1_hash );
									}
	
									if ( Arrays.equals( hash, sha1_hash )){
	
										matching_dm	= dm;
	
										break;
									}
								}
							}catch( Throwable e ){
	
								Debug.printStackTrace(e);
							}
	
							if ( matching_dm == null ){
	
								return( null );
							}
	
							if ( !matching_dm.getDownloadState().isPeerSourceEnabled( PEPeerSource.PS_PLUGIN )){
	
								return( null );
							}
	
							int	dm_state = matching_dm.getState();
	
							if ( dm_state == DownloadManager.STATE_ERROR || dm_state == DownloadManager.STATE_STOPPED ){
	
								return( null );
							}
	
							try{
	
								final Object target = DownloadManagerImpl.getDownloadStatic( matching_dm );
	
								final boolean	is_seed = matching_dm.isDownloadComplete(true);
	
								return(
									new ClientInstanceTracked.TrackTarget()
									{
										@Override
										public Object
										getTarget()
										{
											return( target );
										}
	
										@Override
										public boolean
										isSeed()
										{
											return( is_seed );
										}
									});
	
							}catch( Throwable e ){
	
								return( null );
							}
						}
	
						@Override
						public DHTPlugin
						getDHTPlugin()
						{
							PluginInterface pi = getPluginManager().getPluginInterfaceByClass( DHTPlugin.class );
	
							if ( pi != null ){
	
								return( (DHTPlugin)pi.getPlugin());
							}
	
							return( null );
						}
	
						@Override
						public UPnPPlugin
						getUPnPPlugin()
						{
							PluginInterface pi = getPluginManager().getPluginInterfaceByClass( UPnPPlugin.class );
	
							if ( pi != null ){
	
								return((UPnPPlugin)pi.getPlugin());
							}
	
							return( null );
						}
	
						@Override
						public void
						addListener(
							final StateListener listener)
						{
							CoreImpl.this.addLifecycleListener(
								new CoreLifecycleAdapter()
								{
									@Override
									public void
									started(
										Core core)
									{
										listener.started();
									}
	
									@Override
									public void
									stopping(
										Core core )
									{
										listener.stopped();
									}
								});
						}
					});
	
			if (DEBUG_STARTUPTIME) {
				logTime("Init instance_manager");
			}
	
			if ( COConfigurationManager.getBooleanParameter( "speedmanager.enable", true )){
	
				speed_manager	=
					SpeedManagerFactory.createSpeedManager(
							this,
							new SpeedManagerAdapter()
							{
								private static final int UPLOAD_SPEED_ADJUST_MIN_KB_SEC		= 10;
								private static final int DOWNLOAD_SPEED_ADJUST_MIN_KB_SEC	= 300;
	
								private boolean setting_limits;
	
								@Override
								public int
								getCurrentProtocolUploadSpeed(
									int	average_period )
								{
									if ( global_manager != null ){
	
										GlobalManagerStats stats = global_manager.getStats();
	
										return( stats.getProtocolSendRateNoLAN( average_period ));
	
									}else{
	
										return(0);
									}
								}
	
								@Override
								public int
								getCurrentDataUploadSpeed(
									int	average_period )
								{
									if ( global_manager != null ){
	
										GlobalManagerStats stats = global_manager.getStats();
	
										return( stats.getDataSendRateNoLAN( average_period ));
	
									}else{
	
										return(0);
									}
								}
	
		                        @Override
		                        public int
		                        getCurrentProtocolDownloadSpeed(
		                        	int	average_period )
		                        {
		                            if( global_manager != null ){
		                                GlobalManagerStats stats = global_manager.getStats();
		                                return (stats.getProtocolReceiveRateNoLAN( average_period ) );
		                            }else{
		                                return(0);
		                            }
		                        }
	
		                        @Override
		                        public int
		                        getCurrentDataDownloadSpeed(
		                        	int	average_period )
		                        {
		                            if( global_manager != null ){
		                                GlobalManagerStats stats = global_manager.getStats();
		                                return (stats.getDataReceiveRateNoLAN( average_period ) );
		                            }else{
		                                return(0);
		                            }
		                        }
	
		                        @Override
		                        public int
								getCurrentUploadLimit()
								{
									String key = TransferSpeedValidator.getActiveUploadParameter( global_manager );
	
									int	k_per_second = COConfigurationManager.getIntParameter( key );
	
									int	bytes_per_second;
	
									if ( k_per_second == 0 ){
	
										bytes_per_second = Integer.MAX_VALUE;
	
									}else{
	
										bytes_per_second = k_per_second*1024;
									}
	
									return( bytes_per_second );
								}
	
								@Override
								public void
								setCurrentUploadLimit(
									int		bytes_per_second )
								{
									if ( bytes_per_second != getCurrentUploadLimit()){
	
										String key = TransferSpeedValidator.getActiveUploadParameter( global_manager );
	
										int	k_per_second;
	
										if ( bytes_per_second == Integer.MAX_VALUE ){
	
											k_per_second	= 0;
	
										}else{
	
											k_per_second = (bytes_per_second+1023)/1024;
										}
	
										if ( k_per_second > 0 ){
	
											k_per_second = Math.max( k_per_second, UPLOAD_SPEED_ADJUST_MIN_KB_SEC );
										}
	
										COConfigurationManager.setParameter( key, k_per_second );
									}
								}
	
								@Override
								public int
								getCurrentDownloadLimit()
								{
									return( TransferSpeedValidator.getGlobalDownloadRateLimitBytesPerSecond());
								}
	
								@Override
								public void
								setCurrentDownloadLimit(
									int		bytes_per_second )
								{
									if ( bytes_per_second == Integer.MAX_VALUE ){
	
										bytes_per_second = 0;
									}
	
									if ( bytes_per_second > 0 ){
	
										bytes_per_second = Math.max( bytes_per_second, DOWNLOAD_SPEED_ADJUST_MIN_KB_SEC*1024 );
									}
	
									TransferSpeedValidator.setGlobalDownloadRateLimitBytesPerSecond( bytes_per_second );
								}
	
								@Override
								public Object
								getLimits()
								{
									String up_key 	= TransferSpeedValidator.getActiveUploadParameter( global_manager );
									String down_key	= TransferSpeedValidator.getDownloadParameter();
	
									return(
										new Object[]{
											up_key,
											new Integer( COConfigurationManager.getIntParameter( up_key )),
											down_key,
											new Integer( COConfigurationManager.getIntParameter( down_key )),
										});
								}
	
								@Override
								public void
								setLimits(
									Object		limits,
									boolean		do_up,
									boolean		do_down )
								{
									if ( limits == null ){
	
										return;
									}
									try{
										if ( setting_limits ){
	
											return;
										}
	
										setting_limits	= true;
	
										Object[]	bits = (Object[])limits;
	
										if ( do_up ){
	
											COConfigurationManager.setParameter((String)bits[0], ((Integer)bits[1]).intValue());
										}
	
										if ( do_down ){
	
											COConfigurationManager.setParameter((String)bits[2], ((Integer)bits[3]).intValue());
										}
	
									}finally{
	
										setting_limits	= false;
	
									}
								}
							});
				if (DEBUG_STARTUPTIME) {
					logTime("SpeedManager");
				}
			}
	
			nat_traverser = new NATTraverser( this );
	
			PeerNATTraverser.initialise( this );
	
			BackupManagerFactory.getManager( this );
	
	
			if (DEBUG_STARTUPTIME) {
				logTime("BackupManagerFactory,NATTraverser");
			}
				// one off explicit GC to clear up initialisation mem
	
			SimpleTimer.addEvent(
					"Core:gc",
					SystemTime.getOffsetTime(60*1000),
					new TimerEventPerformer()
					{
						@Override
						public void
						perform(
							TimerEvent event)
						{
							System.gc();
						}
					});
			
		}catch( Throwable e ) {
		
			Debug.out( "Initialisation failed", e );
			
			if ( e instanceof RuntimeException ){
				
				throw((RuntimeException)e);
			}
			
			throw( new RuntimeException( e ));
		}
	}

	private static void logTime(String s) {
		long diff = (System.currentTimeMillis() - lastDebugTime);
		if (diff > 19) {
			System.out.println("Core: " + diff + "ms] " + s);
		}
		lastDebugTime = System.currentTimeMillis();
	}

	@Override
	public long
	getCreateTime()
	{
		return( create_time );
	}

	protected void
	announceAll(
		boolean	force )
	{
		Logger.log(	new LogEvent(LOGID, "Updating trackers" ));

		GlobalManager gm = getGlobalManager();

		if ( gm != null ){

			List	downloads = gm.getDownloadManagers();

			long now	= SystemTime.getCurrentTime();

			for (int i=0;i<downloads.size();i++){

				DownloadManager	dm = (DownloadManager)downloads.get(i);

				Long	last_announce_l = (Long)dm.getUserData( DM_ANNOUNCE_KEY );

				long	last_announce	= last_announce_l==null?create_time:last_announce_l.longValue();

				TRTrackerAnnouncer an = dm.getTrackerClient();

				if ( an != null ){

					TRTrackerAnnouncerResponse last_announce_response = an.getLastResponse();

					if ( 	now - last_announce > 15*60*1000 ||
							last_announce_response == null ||
							last_announce_response.getStatus() == TRTrackerAnnouncerResponse.ST_OFFLINE ||
							force ){

						dm.setUserData( DM_ANNOUNCE_KEY, new Long( now ));

						Logger.log(	new LogEvent(LOGID, "    updating tracker for " + dm.getDisplayName()));

						dm.requestTrackerAnnounce( true );
					}
				}
			}
		}

	    PluginInterface dht_tracker_pi = getPluginManager().getPluginInterfaceByClass( DHTTrackerPlugin.class );

	    if ( dht_tracker_pi != null ){

	    	((DHTTrackerPlugin)dht_tracker_pi.getPlugin()).announceAll();
	    }
	}

	@Override
	public LocaleUtil
	getLocaleUtil()
	{
		return( LocaleUtil.getSingleton());
	}

	@Override
	public File
	getLockFile()
	{
		return( FileUtil.newFile(SystemProperties.getUserPath(), ".azlock" ));
	}

	private FileLock file_lock;

	long start = SystemTime.getMonotonousTime();
	
	@Override
	public boolean
	canStart(
		int	max_wait_secs )
	{
		if ( System.getProperty(SystemProperties.SYSPROP_INSTANCE_LOCK_DISABLE, "0" ).equals( "1" )){

			return( true );
		}

		synchronized( this ){

			if ( file_lock != null ){

				return( true );
			}

			File lock_file = getLockFile();

			try{
				RandomAccessFile raf = new RandomAccessFile( lock_file, "rw" );

				FileChannel channel = raf.getChannel();

				for ( int i=0;i<max_wait_secs;i++ ){

					file_lock = channel.tryLock();

					if ( file_lock != null ){

						return( true );
					}

					try{
						Thread.sleep( 1000 );

					}catch( Throwable e ){
					}
				}
			}catch( Throwable e ){
			}

			return( false );
		}
	}

	@Override
	public void
	start()

		throws CoreException
	{
		if ( !canStart( 15 )){

			throw( new CoreException( "Core: already started (alternative process)" ));
		}

		AEThread2.setOurThread();

		try{
			this_mon.enter();

			if ( started ){

				throw( new CoreException( "Core: already started" ));
			}

			if ( stopped ){

				throw( new CoreException( "Core: already stopped" ));
			}

			started	= true;

		}finally{

			this_mon.exit();
		}

		// If a user sets this property, it is an alias for the following settings.
		if ("1".equals(System.getProperty(SystemProperties.SYSPROP_SAFEMODE))) {
			if (Logger.isEnabled())
				Logger.log(new LogEvent(LOGID, "Safe mode enabled"));

			Constants.isSafeMode = true;
			System.setProperty(SystemProperties.SYSPROP_LOADPLUGINS, "0");
			System.setProperty(SystemProperties.SYSPROP_DISABLEDOWNLOADS, "1");
			System.setProperty(SystemProperties.SYSPROP_SKIP_SWTCHECK, "1");

			// Not using localised text - not sure it's safe to this early.
			Logger.log(new LogAlert(LogAlert.UNREPEATABLE, LogEvent.LT_WARNING,
				"You are running " + Constants.APP_NAME + " in safe mode - you " +
					"can change your configuration, but any downloads added will " +
					"not be remembered when you close " + Constants.APP_NAME + "."
			));
		}

	   /**
	    * test to see if UI plays nicely with a really slow initialization
	    */
	   String sDelayCore = System.getProperty("delay.core", null);
	   if (sDelayCore != null) {
	  	 try {
	  		 long delayCore = Long.parseLong(sDelayCore);
	  		 Thread.sleep(delayCore);
	  	 } catch (Exception e) {
	  		 e.printStackTrace();
	  	 }
	   }


		// run plugin loading in parallel to the global manager loading
		AEThread2 pluginload = new AEThread2("PluginLoader",true)
		{
			@Override
			public void run() {
				if (Logger.isEnabled())
					Logger.log(new LogEvent(LOGID, "Loading of Plugins starts"));
				pi.loadPlugins(CoreImpl.this, false, !"0".equals(System.getProperty(SystemProperties.SYSPROP_LOADPLUGINS)), true, true);
				if (Logger.isEnabled())
					Logger.log(new LogEvent(LOGID, "Loading of Plugins complete"));
			}
		};

		if (LOAD_PLUGINS_IN_OTHER_THREAD) {
			pluginload.start();
		}
		else {
			pluginload.run();
		}

		global_manager = GlobalManagerFactory.create( this, null );

		if (stopped) {
			System.err.println("Core stopped while starting");
			return;
		}

		// wait until plugin loading is done
		if (LOAD_PLUGINS_IN_OTHER_THREAD) {
			pluginload.join();
		}

		if (stopped) {
			System.err.println("Core stopped while starting");
			return;
		}

		VuzeFileHandler.getSingleton().addProcessor(
				new VuzeFileProcessor()
				{
					@Override
					public void
					process(
						VuzeFile[]		files,
						int				expected_types )
					{
						for (int i=0;i<files.length;i++){

							VuzeFile	vf = files[i];

							VuzeFileComponent[] comps = vf.getComponents();

							for (int j=0;j<comps.length;j++){

								VuzeFileComponent comp = comps[j];

								int	comp_type = comp.getType();

								if ( comp_type == VuzeFileComponent.COMP_TYPE_ADD_TORRENT ){

									PluginInterface default_pi = getPluginManager().getDefaultPluginInterface();

									Map map = comp.getContent();

									try{
										Torrent torrent;

										String url = MapUtils.getMapString(map, "torrent_url", null );

										if ( url != null ){

											TorrentDownloader dl = default_pi.getTorrentManager().getURLDownloader( new URL( url ));

											torrent = dl.download();

										}else{

											String tf = MapUtils.getMapString(map, "torrent_file", null );

											if ( tf != null ){

												File file = FileUtil.newFile( tf );

												if ( !file.canRead() || file.isDirectory()){

													throw( new Exception( "torrent_file '" + tf + "' is invalid" ));
												}

												torrent = default_pi.getTorrentManager().createFromBEncodedFile(file);

											}else{

												throw( new Exception( "torrent_url or torrent_file must be specified" ));
											}
										}

										File	dest = null;

										String save_folder = MapUtils.getMapString(map, "save_folder", null );

										if ( save_folder != null ){

											dest = FileUtil.newFile( save_folder, torrent.getName());

										}else{

											String save_file = MapUtils.getMapString(map, "save_file", null );

											if ( save_file != null ){

												dest = FileUtil.newFile( save_file );
											}
										}

										if ( dest != null ){

											dest.getParentFile().mkdirs();
										}

										default_pi.getDownloadManager().addDownload( torrent, null, dest );

									}catch( Throwable e ){

										Debug.out( e );
									}

									comp.setProcessed();
								}
							}
						}
					}
				});



		triggerLifeCycleComponentCreated(global_manager);

		pi.initialisePlugins();

		if (stopped) {
			System.err.println("Core stopped while starting");
			return;
		}

		if (Logger.isEnabled())
			Logger.log(new LogEvent(LOGID, "Initializing Plugins complete"));

		try{
			PluginInterface dht_pi 	= getPluginManager().getPluginInterfaceByClass( DHTPlugin.class );

			if ( dht_pi != null ){

				dht_pi.addEventListener(
					new PluginEventListener()
					{
						private boolean	first_dht = true;

						@Override
						public void
						handleEvent(
							PluginEvent ev )
						{
							if ( ev.getType() == DHTPlugin.EVENT_DHT_AVAILABLE ){

								if ( first_dht ){

									first_dht	= false;

									DHT 	dht = (DHT)ev.getValue();

									dht.addListener(
										new DHTListener()
										{
											@Override
											public void
											speedTesterAvailable(
												DHTSpeedTester tester )
											{
												if ( speed_manager != null ){

													speed_manager.setSpeedTester( tester );
												}
											}
										});

									global_manager.addListener(
											new GlobalManagerAdapter()
											{
												@Override
												public void
												seedingStatusChanged(
													boolean seeding_only_mode,
													boolean	b )
												{
													checkConfig();
												}
											});

									COConfigurationManager.addAndFireParameterListeners(
										new String[]{	TransferSpeedValidator.AUTO_UPLOAD_ENABLED_CONFIGKEY,
														TransferSpeedValidator.AUTO_UPLOAD_SEEDING_ENABLED_CONFIGKEY },
										new ParameterListener()
										{
											@Override
											public void
											parameterChanged(
												String parameterName )
											{
												checkConfig();
											}
										});

								}
							}
						}

						protected void
						checkConfig()
						{
							if ( speed_manager != null ){

								speed_manager.setEnabled( TransferSpeedValidator.isAutoSpeedActive(global_manager) );
							}
						}

					});
			}
		}catch( Throwable e ){
		}

	   if ( COConfigurationManager.getBooleanParameter( "Resume Downloads On Start" )){

		   global_manager.resumeDownloads();
	   }

	   VersionCheckClient.getSingleton().initialise();

	   instance_manager.initialize();

	   NetworkManager.getSingleton().initialize(this);

	   SpeedLimitHandler.getSingleton( this );

	   Runtime.getRuntime().addShutdownHook( new AEThread("Shutdown Hook") {
	     @Override
	     public void runSupport() {
			Logger.log(new LogEvent(LOGID, "Shutdown hook triggered" ));
			CoreImpl.this.stop();
	     }
	   });


	   DelayedTask delayed_task =
	   		UtilitiesImpl.addDelayedTask(
	   			"Core",
	   			new Runnable()
	   			{
	   				@Override
				    public void
	   				run()
	   				{
	   					new AEThread2( "core:delayTask", true )
	   					{
	   						@Override
						    public void
	   						run()
	   						{
			   					AEDiagnostics.checkDumpsAndNatives();

			   					COConfigurationManager.setParameter( "diags.enable.pending.writes", true );

			   					AEDiagnostics.flushPendingLogs();

			   					NetworkAdmin na = NetworkAdmin.getSingleton();

			   					na.runInitialChecks(CoreImpl.this);

			   					na.addPropertyChangeListener(
			   							new NetworkAdminPropertyChangeListener()
			   							{
			   								private String	last_as;

			   								@Override
										    public void
			   								propertyChanged(
			   										String		property )
			   								{
			   									NetworkAdmin na = NetworkAdmin.getSingleton();

			   									if ( property.equals( NetworkAdmin.PR_NETWORK_INTERFACES )){

			   										boolean	found_usable = false;

			   										NetworkAdminNetworkInterface[] intf = na.getInterfaces();

			   										for (int i=0;i<intf.length;i++){

			   											NetworkAdminNetworkInterfaceAddress[] addresses = intf[i].getAddresses();

			   											for (int j=0;j<addresses.length;j++){

			   												if ( !addresses[j].isLoopback()){

			   													found_usable = true;
			   												}
			   											}
			   										}

			   										// ignore event if nothing usable

			   										if ( !found_usable ){

			   											return;
			   										}

			   										Logger.log(	new LogEvent(LOGID, "Network interfaces have changed (new=" + na.getNetworkInterfacesAsString() + ")"));

			   										announceAll( false );

			   									}else if ( property.equals( NetworkAdmin.PR_AS )){

			   										String	as = na.getCurrentASN().getAS();

			   										if ( last_as == null ){

			   											last_as = as;

			   										}else if ( !as.equals( last_as )){

			   											Logger.log(	new LogEvent(LOGID, "AS has changed (new=" + as + ")" ));

			   											last_as = as;

			   											announceAll( false );
			   										}
			   									}
			   								}
			   							});

			   					setupSleepAndCloseActions();
	   						}
	   					}.start();
	   				}
	   			});

	   delayed_task.queue();

			if (stopped) {
				System.err.println("Core stopped while starting");
				return;
			}

	   PairingManagerFactory.getSingleton();

	   CoreRunningListener[] runningListeners;
	   mon_coreRunningListeners.enter();
	   try {
	  	 if (coreRunningListeners == null) {
	  		 runningListeners = new CoreRunningListener[0];
	  	 } else {
	  		 runningListeners = coreRunningListeners.toArray(new CoreRunningListener[0]);
	  		 coreRunningListeners = null;
	  	 }

	   } finally {
	  	 mon_coreRunningListeners.exit();
	   }

		// Trigger Listeners now that core is started
		new AEThread2("Plugin Init Complete", false )
		{
			@Override
			public void
			run()
			{
				try{
					PlatformManagerFactory.getPlatformManager().startup( CoreImpl.this );

				}catch( Throwable e ){

					Debug.out( "PlatformManager: init failed", e );
				}

				Iterator	it;
				
				synchronized( lifecycle_listeners ){
				
					it = lifecycle_listeners.iterator();
					
					ll_started = true;
				}
				
				while( it.hasNext()){

					try{
						CoreLifecycleListener listener = (CoreLifecycleListener)it.next();

						if ( !listener.requiresPluginInitCompleteBeforeStartedEvent()){

							listener.started( CoreImpl.this );
						}
					}catch( Throwable e ){

						Debug.printStackTrace(e);
					}
				}

				pi.initialisationComplete();

				it = lifecycle_listeners.iterator();

				while( it.hasNext()){

					try{
						CoreLifecycleListener listener = (CoreLifecycleListener)it.next();

						if ( listener.requiresPluginInitCompleteBeforeStartedEvent()){

							listener.started( CoreImpl.this );
						}
					}catch( Throwable e ){

						Debug.printStackTrace(e);
					}
				}
			}
		}.start();

		// Typically there are many runningListeners, most with quick execution, and
		// a few longer ones.  Let 3 run at a time, queue the rest.  Without
		// a ThreadPool, the slow ones would delay the startup processes that run
		// after this start() method
		ThreadPool tp = new ThreadPool("Trigger CoreRunning Listeners", 3);
		for (final CoreRunningListener l : runningListeners) {
			try {
				tp.run(new AERunnable() {
					@Override
					public void runSupport() {
						l.coreRunning(CoreImpl.this);
					}
				});
			} catch (Throwable t) {
				Debug.out(t);
			}
		}

		// Debug.out("Core Start Complete");
	}

	@Override
	public boolean
	isInitThread()
	{
		return( AEThread2.isOurThread( Thread.currentThread()));
	}

	@Override
	public boolean
	isStarted()
	{
	   mon_coreRunningListeners.enter();
	   try {
	  	 return( started && coreRunningListeners == null );
	   } finally {
	  	 mon_coreRunningListeners.exit();
	   }
	}

	@Override
	public void
	triggerLifeCycleComponentCreated(
		CoreComponent component )
	{
		Iterator it = lifecycle_listeners.iterator();

		while( it.hasNext()){

			try{
				((CoreLifecycleListener)it.next()).componentCreated(this, component);

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	private void
	runNonDaemon(
		final Runnable	r )

		throws CoreException
	{
		if ( !Thread.currentThread().isDaemon()){

			r.run();

		}else{

			final AESemaphore	sem = new AESemaphore( "Core:runNonDaemon" );

			final Throwable[]	error = {null};

			new AEThread2( "Core:runNonDaemon", false )
			{
				@Override
				public void
				run()
				{
					try{

						r.run();

					}catch( Throwable e ){

						error[0]	= e;

					}finally{

						sem.release();
					}
				}
			}.start();

			sem.reserve();

			if ( error[0] != null ){

				if ( error[0] instanceof CoreException){

					throw((CoreException)error[0]);

				}else{

					throw( new CoreException( "Operation failed", error[0] ));
				}
			}
		}
	}
	
	@Override
	public void
	stop()
		throws CoreException
	{
		stop( new CoreOperationTask.ProgressCallbackAdapter());
	}

	@Override
	public void
	stop(
		CoreOperationTask.ProgressCallback	callback )

		throws CoreException
	{
		runNonDaemon(new AERunnable() {
			@Override
			public void runSupport() {
				if (Logger.isEnabled())
					Logger.log(new LogEvent(LOGID, "Stop operation starts"));

				stopSupport( false, true, callback );
			}
		});
	}

	void
	stopSupport(
		final boolean							for_restart,
		final boolean							apply_updates,
		CoreOperationTask.ProgressCallback		callback )

		throws CoreException
	{
		Logger.setClosing();
		
		AEDiagnostics.flushPendingLogs();

		boolean	wait_and_return = false;

		try{
			this_mon.enter();

			if ( stopped ){

					// ensure config is saved as there may be pending changes to persist and we've got here
					// via a shutdown hook

				COConfigurationManager.save();

				wait_and_return = true;

			}else{

				stopped	= true;

				if ( !started ){

					Logger.log(new LogEvent(LOGID, "Core not started"));

						// might have been marked dirty due to core being created to allow functions to be used but never started...

					if ( AEDiagnostics.isDirty()){

						AEDiagnostics.markClean();
					}

					stopping_sem.releaseForever();

					return;
				}
			}
		}finally{

			this_mon.exit();
		}

		if ( wait_and_return ){

			Logger.log(new LogEvent(LOGID, "Waiting for stop to complete"));

			stopping_sem.reserve();

			return;
		}
		
		int stall_mins = Math.max( 2, COConfigurationManager.getIntParameter( ConfigKeys.StartupShutdown.ICFG_STOP_FORCE_TERMINATE_AFTER ));
		
		long stall_millis = stall_mins * 60 * 1000;

		AtomicLong last_progress = new AtomicLong( SystemTime.getMonotonousTime() );
		
		SimpleTimer.addEvent(
			"ShutFail",
			SystemTime.getOffsetTime( 60*1000 ),
			new TimerEventPerformer()
			{				
				boolean	die_die_die;

				@Override
				public void
				perform(
					TimerEvent event )
				{	
						// it has been a minute - turn off logging if it is enabled as this can significantly
						// slow things down
					
					if ( System.getProperty( SystemProperties.SYSPROP_LOGGING_DISABLE_STOP_ON_SLOW_CLOSE, "0" ).equals( "0" )){
					
						Logger.setClosingTakingTooLong();
					}
										
						// hang around while things are making progress
					
					while( SystemTime.getMonotonousTime() - last_progress.get() < stall_millis ){
						
						try{
							Thread.sleep(5000);
							
						}catch( Throwable e ){
						}
					}
					
					AEDiagnostics.dumpThreads();

					if ( die_die_die ){

						Debug.out( "Shutdown blocked, force exiting" );

						stopping_sem.releaseForever();

							// try and do something with these outstanding actions before force terminating

						if ( for_restart ){

							ClientRestarterFactory.create( CoreImpl.this ).restart( false );

						}else if ( apply_updates ){

							if ( getPluginManager().getDefaultPluginInterface().getUpdateManager().getInstallers().length > 0 ){

								ClientRestarterFactory.create( CoreImpl.this ).restart( true );
							}
						}

						if ( ca_shutdown_computer_after_stop ){

							if ( apply_updates ){

									// best we can do here is wait a while for updates to be applied
								try{
									Thread.sleep( 10*1000 );

								}catch( Throwable e ){

								}
							}

							try{
								PlatformManagerFactory.getPlatformManager().shutdown( PlatformManager.SD_SHUTDOWN );

							}catch( Throwable e ){

								Debug.out( "PlatformManager: shutdown failed", e );
							}
						}

						SESecurityManager.exitVM(0);
					}

					die_die_die = true;

					SimpleTimer.addEvent( "ShutFail", SystemTime.getOffsetTime( 30*1000 ), this );
				}
			});

		List	sync_listeners 	= new ArrayList();
		List	async_listeners	= new ArrayList();

		Iterator it = lifecycle_listeners.iterator();

		while( it.hasNext()){
			CoreLifecycleListener l = (CoreLifecycleListener)it.next();

			if ( l.syncInvokeRequired()){
				sync_listeners.add( l );
			}else{
				async_listeners.add( l );
			}
		}

		int	progress = 125;
				
		callback.setSubTaskName( MessageText.getString( "label.starting.closedown" ));
		callback.setProgress( progress );
		
		try{
			if (Logger.isEnabled())
				Logger.log(new LogEvent(LOGID, "Invoking synchronous 'stopping' listeners"));

			for (int i=0;i<sync_listeners.size();i++){
				try{
					((CoreLifecycleListener)sync_listeners.get(i)).stopping( this );

				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}
				
				last_progress.set( SystemTime.getMonotonousTime());
			}

			if (Logger.isEnabled())
				Logger.log(new LogEvent(LOGID, "Invoking asynchronous 'stopping' listeners"));

				// in case something hangs during listener notification (e.g. version check server is down
				// and the instance manager tries to obtain external address) we limit overall dispatch
				// time to 10 seconds

			ListenerManager.dispatchWithTimeout(
					async_listeners,
					new ListenerManagerDispatcher()
					{
						@Override
						public void
						dispatch(
							Object		listener,
							int			type,
							Object		value )
						{
							((CoreLifecycleListener)listener).stopping( CoreImpl.this );
							
							last_progress.set( SystemTime.getMonotonousTime());
						}
					},
					10*1000 );

			if (Logger.isEnabled()){
				Logger.log(new LogEvent(LOGID, "Waiting for quiescence pre gm stop"));
			}
			
			NonDaemonTaskRunner.waitUntilIdle();

			last_progress.set( SystemTime.getMonotonousTime());
			
			if (Logger.isEnabled()){
				Logger.log(new LogEvent(LOGID, "Stopping global manager"));
			}
			
			progress = 250;
						
			callback.setSubTaskName( MessageText.getString( "label.stopping.downloads" ));
			callback.setProgress( progress );
			
			if ( global_manager != null ){
				
				int p_start = 250;
				int p_end	= 699;
				
				global_manager.stopGlobalManager( 
					new GlobalMangerProgressListener(){
						
						@Override
						public void 
						reportPercent(
							int percent)
						{
							callback.setProgress( p_start + ((p_end-p_start)*percent)/100 );
							
							last_progress.set( SystemTime.getMonotonousTime());
						}
						
						@Override
						public void 
						reportCurrentTask(
							String currentTask )
						{
							callback.setSubTaskName( currentTask );
							
							last_progress.set( SystemTime.getMonotonousTime());
						}
					});
			}

			last_progress.set( SystemTime.getMonotonousTime());
			
			AllTrackers at = AllTrackersManager.getAllTrackers();
			
			progress = 750;
				
			int wait_secs = COConfigurationManager.getIntParameter( ConfigKeys.Tracker.ICFG_TRACKER_CLIENT_CLOSEDOWN_TIMEOUT );

			callback.setSubTaskName( MessageText.getString( "label.waiting.tracker.updates", new String[]{ String.valueOf( wait_secs )}));
			callback.setProgress( progress );

			int active_req = at.getActiveRequestCount();
			
			AllTrackersManager.AnnounceStats announce_stats = at.getAnnounceStats();

			int scheduled_req = announce_stats.getPrivateScheduledCount() + announce_stats.getPublicScheduledCount();
			
			int total_req = active_req + scheduled_req;
			
			if ( total_req > 0 ){				
				
				if (Logger.isEnabled()){
					Logger.log(new LogEvent(LOGID, "Waiting for tracker updates, " + active_req + "/" + scheduled_req + " outstanding"));
				}
				
				long at_start = SystemTime.getMonotonousTime();
								
				int current_req = total_req;
				
				int p_start = 750;
				int p_end	= 899;
				
				if ( wait_secs > 0 ){
					
					while( true ){
						
						if ( SystemTime.getMonotonousTime() - at_start > wait_secs*1000 ){
							
							break;
						}
						
						try{
							Thread.sleep(500);
							
						}catch( Throwable e ){
							
						}
						
						int latest_active = at.getActiveRequestCount();
						
						announce_stats = at.getAnnounceStats();

						int latest_scheduled = announce_stats.getPrivateScheduledCount() + announce_stats.getPublicScheduledCount();

						int latest_req = latest_active + latest_scheduled;
						
						if ( latest_req == 0 ){
							
							break;
						}
												
						if ( latest_req < current_req ){
							
							current_req = latest_req;
							
							int percent = ((total_req-current_req)*100)/total_req;
							
							callback.setProgress( p_start + ((p_end-p_start)*percent)/100 );
							
							last_progress.set( SystemTime.getMonotonousTime());
						}
					}
				}
			}
			
			if (Logger.isEnabled()){
				Logger.log(new LogEvent(LOGID, "Invoking synchronous 'stopped' listeners"));
			}
			
			progress = 900;
			
			callback.setSubTaskName( MessageText.getString( "label.finalising.closedown" ));
			callback.setProgress( progress );
			
			for (int i=0;i<sync_listeners.size();i++){
				try{
					((CoreLifecycleListener)sync_listeners.get(i)).stopped( this );

				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}
				
				last_progress.set( SystemTime.getMonotonousTime());
			}

			if (Logger.isEnabled()){
				Logger.log(new LogEvent(LOGID, "Invoking asynchronous 'stopped' listeners"));
			}
			
			ListenerManager.dispatchWithTimeout(
					async_listeners,
					new ListenerManagerDispatcher()
					{
						@Override
						public void
						dispatch(
							Object		listener,
							int			type,
							Object		value )
						{
							((CoreLifecycleListener)listener).stopped( CoreImpl.this );
							
							last_progress.set( SystemTime.getMonotonousTime());
						}
					},
					10*1000 );

			if (Logger.isEnabled())
				Logger.log(new LogEvent(LOGID, "Waiting for quiescence post gm stop"));

			NonDaemonTaskRunner.waitUntilIdle();

			last_progress.set( SystemTime.getMonotonousTime());
			
				// shut down diags - this marks the shutdown as tidy and saves the config

			AEDiagnostics.markClean();

			if (Logger.isEnabled())
				Logger.log(new LogEvent(LOGID, "Stop operation completes"));

				// if any installers exist then we need to closedown via the updater

			if ( 	apply_updates &&
					getPluginManager().getDefaultPluginInterface().getUpdateManager().getInstallers().length > 0 ){

				ClientRestarterFactory.create( this ).restart( true );
			}

			if (System.getProperty("skip.shutdown.nondeamon.check", "0").equals("1")) {
				return;
			}

			try {
				Class c = Class.forName( "sun.awt.AWTAutoShutdown" );

				if (c != null) {
					c.getMethod( "notifyToolkitThreadFree", new Class[]{} ).invoke( null, new Object[]{} );
				}
			} catch (Throwable t) {
			}

			if ( ca_shutdown_computer_after_stop ){

				if ( apply_updates ){

						// best we can do here is wait a while for updates to be applied
					try{
						Thread.sleep( 10*1000 );

					}catch( Throwable e ){

					}
				}

				try{
					PlatformManagerFactory.getPlatformManager().shutdown( PlatformManager.SD_SHUTDOWN );

				}catch( Throwable e ){

					Debug.out( "PlatformManager: shutdown failed", e );
				}
			}

			try{
				ThreadGroup	tg = Thread.currentThread().getThreadGroup();

				while( tg.getParent() != null ){

					tg = tg.getParent();
				}

				Thread[]	threads = new Thread[tg.activeCount()+1024];

				tg.enumerate( threads, true );

				boolean	bad_found = false;

				for (int i=0;i<threads.length;i++){

					Thread	t = threads[i];

					if ( t != null && t.isAlive() && t != Thread.currentThread() && !t.isDaemon() && !AEThread2.isOurThread( t )){

						bad_found = true;

						break;
					}
				}

				if ( bad_found ){

					new AEThread2( "VMKiller", true )
					{
						@Override
						public void
						run()
						{
							try{
								int	loops = 0;
								
								while( true ){
									
									ThreadGroup	tg = Thread.currentThread().getThreadGroup();
	
									Thread[]	threads = new Thread[tg.activeCount()+1024];
	
									tg.enumerate( threads, true );
	
									List<String>	bad = new ArrayList<>();
									
									String	bad_found = "";
	
									for (int i=0;i<threads.length;i++){
	
										Thread	t = threads[i];
	
										if ( t != null && t.isAlive() && !t.isDaemon() && !AEThread2.isOurThread( t )){
	
											String	details = t.getName();
	
											bad.add( details );
											
											StackTraceElement[] trace = t.getStackTrace();
	
											if ( trace.length > 0 ){
	
												details += "[";
	
												for ( int j=0;j<trace.length;j++ ){
	
													details += (j==0?"":",") + trace[j];
												}
	
												details += "]";
											}
	
											bad_found += (bad_found.length()==0?"":", ") + details;
										}
									}
	
									if ( bad.size() == 1 && bad.get(0).equals( "Launcher::bootstrap" )){
										
										Debug.outNoStack( "Only non-daemon bootstrap thread remaining, exiting..." );
										
										SESecurityManager.exitVM(0);
										
										break;
									}
									
									if ( loops == 10 ){
									
										Debug.out( "Non-daemon thread(s) found: '" + bad_found + "' - force closing VM" );
	
										SESecurityManager.exitVM(0);
										
										break;
									}
								
									Thread.sleep(1*1000);
								
									loops++;
								}
							}catch( Throwable e ){

								Debug.out( e );
							}
						}
					}.start();

				}
			}catch( Throwable e ){
			}


		}finally{

			stopping_sem.releaseForever();
		}
	}


	@Override
	public void
	requestStop()

		throws CoreException
	{
		if ( stopped ){
			
			return;
		}
		
		runNonDaemon(new AERunnable() {
			@Override
			public void runSupport() {

				boolean	in_progress = false;

				for ( CoreLifecycleListener l: lifecycle_listeners ){
					
					if ( l.stopRequested(CoreImpl.this)){

						in_progress = true;
						
					}else{
						if (Logger.isEnabled())
							Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
									"Request to stop the core has been denied"));

						return;
					}
				}

				if ( !in_progress ){
				
					stop();
				}
			}
		});
	}

	@Override
	public void
	restart()
	
		throws CoreException
	{
		restart( new CoreOperationTask.ProgressCallbackAdapter());
	}
	
	@Override
	public void
	restart(
		CoreOperationTask.ProgressCallback		callback )

		throws CoreException
	{
		runNonDaemon(new AERunnable() {
			@Override
			public void runSupport() {
				if (Logger.isEnabled())
					Logger.log(new LogEvent(LOGID, "Restart operation starts"));

				checkRestartSupported();

				restarting = true;

				stopSupport( true, false, callback );

				if (Logger.isEnabled())
					Logger.log(new LogEvent(LOGID, "Restart operation: stop complete,"
							+ "restart initiated"));

				ClientRestarterFactory.create(CoreImpl.this).restart(false);
			}
		});
	}

	@Override
	public void
	requestRestart()

		throws CoreException
	{
		if ( stopped ){
			
			return;
		}

		runNonDaemon(new AERunnable() {
            @Override
            public void runSupport(){
                checkRestartSupported();

                boolean	in_progress = false;
                
                for ( CoreLifecycleListener l: lifecycle_listeners ){
                	
                    if ( l.restartRequested(CoreImpl.this)){

                    	in_progress = true;
                    	
                    }else{
                    	
                        if (Logger.isEnabled())
                            Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
                                    "Request to restart the core"
                                            + " has been denied"));

                        return;
                    }
                }

                if (!in_progress ){
                
                	restart();
                }
            }
        });
	}

	@Override
	public boolean
	isRestarting()
	{
		return( restarting );
	}

	@Override
	public void
	checkRestartSupported()

		throws CoreException
	{
		if ( getPluginManager().getPluginInterfaceByClass( "com.biglybt.update.UpdaterPatcher") == null ){
			Logger.log(new LogAlert(LogAlert.REPEATABLE, LogAlert.AT_ERROR,
					"Can't restart without the 'azupdater' plugin installed"));

			throw( new CoreException("Can't restart without the 'azupdater' plugin installed"));
		}
	}

	@Override
	public void
	saveState()
	{
		GlobalManager	gm = global_manager;

		if ( gm != null ){

			gm.saveState();
		}

		COConfigurationManager.save();
	}

	@Override
	public GlobalManager
	getGlobalManager()

		throws CoreException
	{
		if ( global_manager == null ){

			throw( new CoreException( "Core not running" ));
		}

		return( global_manager );
	}

	@Override
	public TRHost
	getTrackerHost()

		throws CoreException
	{
		return( TRHostFactory.getSingleton());
	}

	@Override
	public PluginManagerDefaults
	getPluginManagerDefaults()

		throws CoreException
	{
		return( PluginManager.getDefaults());
	}

	@Override
	public PluginManager
	getPluginManager()

		throws CoreException
	{
			// don't test for running here, the restart process calls this after terminating the core...

		return( PluginInitializer.getDefaultInterface().getPluginManager());
	}

	@Override
	public IpFilterManager
	getIpFilterManager()

		throws CoreException
	{
		return( IpFilterManagerFactory.getSingleton());
	}

	@Override
	public ClientInstanceManager
	getInstanceManager()
	{
		return( instance_manager );
	}

	@Override
	public SpeedManager
	getSpeedManager()
	{
		return( speed_manager );
	}

	@Override
	public CryptoManager
	getCryptoManager()
	{
		return( crypto_manager );
	}

	@Override
	public NATTraverser
	getNATTraverser()
	{
		return( nat_traverser );
	}

	void
	setupSleepAndCloseActions()
	{
		if ( PlatformManagerFactory.getPlatformManager().hasCapability( PlatformManagerCapabilities.PreventComputerSleep )){

			COConfigurationManager.addAndFireParameterListeners(
				new String[]{
					"Prevent Sleep Downloading",
					"Prevent Sleep FP Seeding",
					"Prevent Sleep Tag",
				},
				new ParameterListener()
				{
					private TimerEventPeriodic timer_event;

					@Override
					public void
					parameterChanged(
						String parameterName )
					{
						synchronized( this ){

							boolean dl = COConfigurationManager.getBooleanParameter( "Prevent Sleep Downloading" );
							boolean se = COConfigurationManager.getBooleanParameter( "Prevent Sleep FP Seeding" );

							boolean	active = dl || se;

							if ( !active ){
								
								String tag = COConfigurationManager.getStringParameter( "Prevent Sleep Tag" );
																
								if ( !tag.trim().isEmpty()){
								
									active = true;
								}
							}
							
							try{
								setPreventComputerSleep( PlatformManagerFactory.getPlatformManager(), active, "config change" );

							}catch( Throwable e ){

								Debug.out( e );
							}


							if ( !active ){

								if ( timer_event != null ){

									timer_event.cancel();

									timer_event = null;
								}
							}else{

								if ( timer_event == null ){

									timer_event =
										SimpleTimer.addPeriodicEvent(
												"core:sleepAct",
												2*60*1000,
												new TimerEventPerformer()
												{
													@Override
													public void
													perform(
														TimerEvent event )
													{
														if ( !stopped ){

															checkSleepActions();
														}
													}
												});
								}
							}
						}
					}
				});
		}

		COConfigurationManager.addAndFireParameterListeners(
			new String[]{
					"On Downloading Complete Do",
					"On Seeding Complete Do",
					"Auto Restart When Idle"
			},
			new ParameterListener()
			{
				private TimerEventPeriodic timer_event;

				@Override
				public void
				parameterChanged(
					String parameterName )
				{
					String	dl_act = COConfigurationManager.getStringParameter( "On Downloading Complete Do" );
					String	se_act = COConfigurationManager.getStringParameter( "On Seeding Complete Do" );
										
					int		restart_after = COConfigurationManager.getIntParameter( "Auto Restart When Idle" );

					synchronized( this ){

						boolean	dl_nothing 	= dl_act.equals( "Nothing" );
						boolean se_nothing	= se_act.equals( "Nothing" );

						if ( dl_nothing ){

							ca_last_time_downloading	= -1;
						}

						if ( se_nothing ){

							ca_last_time_seeding	= -1;
						}

						if ( dl_nothing && se_nothing && restart_after == 0 ){

							if ( timer_event != null ){

								timer_event.cancel();

								timer_event = null;
							}
						}else{

							if ( timer_event == null ){

								timer_event =
									SimpleTimer.addPeriodicEvent(
											"core:closeAct",
											30*1000,
											new TimerEventPerformer()
											{
												@Override
												public void
												perform(
													TimerEvent event )
												{
													if ( !stopped ){

														if ( !checkRestartAction()){

															checkCloseActions();
														}
													}
												}
											});
							}

							checkCloseActions();
						}
					}
				}
			});
	}

	protected void
	checkSleepActions()
	{
		boolean ps_downloading 	= COConfigurationManager.getBooleanParameter( "Prevent Sleep Downloading" );
		boolean ps_fp_seed	 	= COConfigurationManager.getBooleanParameter( "Prevent Sleep FP Seeding" );

		String tag_name = COConfigurationManager.getStringParameter( "Prevent Sleep Tag" );

		Tag	ps_tag = null;
		
		tag_name = tag_name.trim();
		
		if ( !tag_name.isEmpty()){
		
			ps_tag = TagManagerFactory.getTagManager().getTagType( TagType.TT_DOWNLOAD_MANUAL ).getTag( tag_name, true );
		}
		
		String	declining_subsystems = "";

		for ( PowerManagementListener l: power_listeners ){

			try{
				if ( !l.requestPowerStateChange( PowerManagementListener.ST_SLEEP, null )){

					declining_subsystems += (declining_subsystems.length()==0?"":",") + l.getPowerName();
				}

			}catch( Throwable e ){

				Debug.out( e );
			}
		}
		
		PlatformManager platform = PlatformManagerFactory.getPlatformManager();

		if ( declining_subsystems.length() == 0 && !( ps_downloading || ps_fp_seed || ps_tag != null )){

			if ( platform.getPreventComputerSleep()){
				
				setPreventComputerSleep( platform, false, "configuration change" );
			}
			
			return;
		}

		boolean	prevent_sleep 	= false;
		String	prevent_reason	= null;

		if ( declining_subsystems.length() > 0 ){

			prevent_sleep 	= true;
			prevent_reason 	= "subsystems declined sleep: " +  declining_subsystems;

		}else if ( ps_tag != null && ps_tag.getTaggedCount() > 0 ){
			
			prevent_sleep 	= true;
			prevent_reason 	= "tag '" + tag_name + "' has entries";
			
		}else{

			List<DownloadManager> managers = getGlobalManager().getDownloadManagers();

			for ( DownloadManager manager: managers ){

				int state = manager.getState();

				if ( 	state == DownloadManager.STATE_FINISHING ||
						manager.getDownloadState().getFlag( DownloadManagerState.FLAG_METADATA_DOWNLOAD )){

					if ( ps_downloading ){

						prevent_sleep 	= true;
						prevent_reason	= "active downloads";

						break;
					}

				}else{

					if ( state == DownloadManager.STATE_DOWNLOADING ){

						PEPeerManager pm = manager.getPeerManager();

						if ( pm != null ){

							if ( pm.hasDownloadablePiece()){

								if ( ps_downloading ){

									prevent_sleep 	= true;
									prevent_reason	= "active downloads";

									break;
								}
							}else{

									// its effectively seeding, change so logic about recheck obeyed below

								state = DownloadManager.STATE_SEEDING;
							}
						}
					}

					if ( state == DownloadManager.STATE_SEEDING && ps_fp_seed ){

						DiskManager disk_manager = manager.getDiskManager();

						if ( disk_manager != null && disk_manager.getCompleteRecheckStatus() != -1 ){

								// wait until recheck is complete before we mark as downloading-complete

							if ( ps_downloading ){

								prevent_sleep 	= true;
								prevent_reason	= "active downloads";

								break;
							}

						}else{

							try{
								DefaultRankCalculator calc = StartStopRulesDefaultPlugin.getRankCalculator( PluginCoreUtils.wrap( manager ));

								if ( calc.getCachedIsFP()){

									prevent_sleep 	= true;
									prevent_reason	= "first-priority seeding";

									break;
								}
							}catch( Throwable e ){

							}
						}
					}
				}
			}
		}

		if ( prevent_sleep != platform.getPreventComputerSleep()){

			if ( prevent_sleep ){

				prevent_sleep_remove_trigger = false;

			}else{

				if ( !prevent_sleep_remove_trigger ){

					prevent_sleep_remove_trigger = true;

					return;
				}
			}

			if ( prevent_reason == null ){

				if ( ps_downloading && ps_fp_seed ){

					prevent_reason = "no active downloads or first-priority seeding";

				}else if ( ps_downloading ){

					prevent_reason = "no active downloads";

				}else{

					prevent_reason = "no active first-priority seeding";
				}
			}

			setPreventComputerSleep( platform, prevent_sleep, prevent_reason );
		}
	}

	void
	setPreventComputerSleep(
		PlatformManager		platform,
		boolean				prevent_sleep,
		String				prevent_reason )
	{
		for ( PowerManagementListener l: power_listeners ){

			try{
				l.informPowerStateChange( PowerManagementListener.ST_SLEEP, new Object[]{ prevent_sleep, prevent_reason });

			}catch( Throwable e ){

				Debug.out( e );
			}
		}

		Logger.log( new LogEvent(LOGID, "Computer sleep prevented state changed to '" + prevent_sleep + "' due to " + prevent_reason ));

		try{
			platform.setPreventComputerSleep( prevent_sleep );

		}catch( Throwable e ){

			Debug.out( e );
		}
	}

	protected boolean
	checkRestartAction()
	{
		if ( ra_restarting ){

			return( true );
		}

		int		restart_after = COConfigurationManager.getIntParameter( "Auto Restart When Idle" );

		if ( restart_after > 0 ){

			List<DownloadManager> managers = getGlobalManager().getDownloadManagers();

			boolean	active = false;

			for ( DownloadManager manager: managers ){

				int state = manager.getState();

				if ( 	state == DownloadManager.STATE_DOWNLOADING ||
						state == DownloadManager.STATE_SEEDING ){

					active = true;

					break;
				}
			}

			if ( active ){

				GlobalManagerStats stats = global_manager.getStats();

				long totals = stats.getTotalDataBytesReceived() + stats.getTotalDataBytesSent();

				long	now = SystemTime.getMonotonousTime();

				if ( totals == ra_last_total_data ){

					if ( now - ra_last_data_time >= 60*1000*restart_after ){

						ra_restarting = true;

						String message =
								MessageText.getString(
									"core.restart.alert",
									new String[]{
										String.valueOf( restart_after ),
									});

						UIFunctions ui_functions = UIFunctionsManager.getUIFunctions();

						if ( ui_functions != null ){

							ui_functions.forceNotify( UIFunctions.STATUSICON_NONE, null, message, null, new Object[0], -1 );
						}

						Logger.log(
							new LogAlert(
								LogAlert.UNREPEATABLE,
								LogEvent.LT_INFORMATION,
								message ));

						new DelayedEvent(
							"CoreRestart",
							10*1000,
							new AERunnable()
							{
								@Override
								public void
								runSupport()
								{
									requestRestart();
								}
							});

						return( true );
					}
				}else{

					ra_last_total_data 	= totals;
					ra_last_data_time	= now;
				}
			}else{

				ra_last_total_data = -1;
			}
		}else{

			ra_last_total_data = -1;
		}

		return( false );
	}

	protected void
	checkCloseActions()
	{
		List<DownloadManager> managers = getGlobalManager().getDownloadManagers();

		boolean	is_downloading 	= false;
		boolean is_seeding		= false;

		for ( DownloadManager manager: managers ){

			if ( manager.isPaused()){

					// if anything's paused we don't want to trigger any actions as something transient (e.g. speed test) is going on

				return;
			}

			if ( manager.getDownloadState().getFlag( DownloadManagerState.FLAG_METADATA_DOWNLOAD )){

					// we want this to complete before considering actions

				return;
			}

			if ( manager.getDownloadState().getFlag( DownloadManagerState.FLAG_LOW_NOISE )){

				continue;	// don't count these as interesting as the user isn't directly interested in them
			}

			int state = manager.getState();

			if ( state == DownloadManager.STATE_FINISHING ){

				is_downloading = true;

			}else{

				if ( state == DownloadManager.STATE_DOWNLOADING ){

					PEPeerManager pm = manager.getPeerManager();

					if ( pm != null ){

						if ( pm.hasDownloadablePiece()){

							is_downloading = true;

						}else{

								// its effectively seeding, change so logic about recheck obeyed below

							state = DownloadManager.STATE_SEEDING;
						}
					}
				}else{

					if ( !manager.isDownloadComplete( false )){

						if ( 	state != DownloadManager.STATE_STOPPED &&
								state != DownloadManager.STATE_ERROR ){

								// an incomplete download that is in an active state counts as downloading even
								// if it is currently queued/waiting as it is 'on its way' to being in a downloading
								// state

							is_downloading = true;
						}
					}
				}

				if ( state == DownloadManager.STATE_SEEDING ){

					DiskManager disk_manager = manager.getDiskManager();

					if ( disk_manager != null && disk_manager.getCompleteRecheckStatus() != -1 ){

							// wait until recheck is complete before we mark as downloading-complete

						is_downloading	= true;

					}else{

						is_seeding		= true;
					}
				}
			}
		}

		long	now = SystemTime.getMonotonousTime();

		if ( is_downloading ){

			ca_last_time_downloading 	= now;
			ca_last_time_seeding		= -1;

		}else if ( is_seeding ){

			ca_last_time_seeding = now;
		}

		String	dl_act = COConfigurationManager.getStringParameter( "On Downloading Complete Do" );

		if ( !dl_act.equals( "Nothing" )){

			if ( ca_last_time_downloading >= 0 && !is_downloading && now - ca_last_time_downloading >= 30*1000 ){

				executeInternalCloseAction( true, true, dl_act, null );
			}
		}

		String	se_act = COConfigurationManager.getStringParameter( "On Seeding Complete Do" );

		if ( !se_act.equals( "Nothing" )){

			if ( ca_last_time_seeding >= 0 && !is_seeding && now - ca_last_time_seeding >= 30*1000 ){

				executeInternalCloseAction( true, false, se_act, null );
			}
		}
	}

	@Override
	public void
	executeCloseAction(
		String		action,
		String		reason )
	{
		executeInternalCloseAction( false, false, action, reason );
	}

	private void
	executeInternalCloseAction(
		final boolean	obey_reset,
		final boolean	download_trigger,
		final String	action,
		final String	reason )
	{
			// prevent immediate retriggering if user aborts

		ca_last_time_downloading	= -1;
		ca_last_time_seeding		= -1;

		String type_str		= reason==null?MessageText.getString( download_trigger?"core.shutdown.dl":"core.shutdown.se"):reason;
		String action_str 	= MessageText.getString( "ConfigView.label.stop." + action );

		String message =
			MessageText.getString(
				"core.shutdown.alert",
				new String[]{
					action_str,
					type_str,
				});

		UIFunctions ui_functions = UIFunctionsManager.getUIFunctions();

		if ( ui_functions != null ){

			ui_functions.forceNotify( UIFunctions.STATUSICON_NONE, null, message, null, new Object[0], -1 );
		}

		Logger.log(
			new LogAlert(
				LogAlert.UNREPEATABLE,
				LogEvent.LT_INFORMATION,
				message ));

		if ( COConfigurationManager.getBooleanParameter( "Prompt To Abort Shutdown" )){

			UIManager ui_manager = StaticUtilities.getUIManager( 30*1000 );

			if ( ui_manager != null ){

				Map<String,Object>	options = new HashMap<>();

				options.put( UIManager.MB_PARAM_AUTO_CLOSE_MS, 30*1000 );

				if ( ui_manager.showMessageBox(
						"core.shutdown.prompt.title",
						"core.shutdown.prompt.msg",
						UIManagerEvent.MT_OK_DEFAULT | UIManagerEvent.MT_CANCEL,
						options ) == UIManagerEvent.MT_CANCEL ){

					return;
				}
			}
		}

		executeCloseActionSupport( obey_reset, download_trigger, action, reason );
	}

	private void
	executeCloseActionSupport(
		final boolean	obey_reset,
		final boolean	download_trigger,
		final String	action,
		final String	reason )
	{
			// prevent retriggering on resume from standby

		ca_last_time_downloading	= -1;
		ca_last_time_seeding		= -1;

		boolean reset = obey_reset && COConfigurationManager.getBooleanParameter( "Stop Triggers Auto Reset" );

		if ( reset ){

			if ( download_trigger ){

				COConfigurationManager.setParameter( "On Downloading Complete Do", "Nothing" );

			}else{

				COConfigurationManager.setParameter( "On Seeding Complete Do", "Nothing" );
			}
		}

		new DelayedEvent(
			"CoreShutdown",
			10*1000,
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					Logger.log( new LogEvent(LOGID, "Executing close action '" + action + "' due to " + (download_trigger?"downloading":"seeding") + " completion" ));

						// quit vuze -> quit
						// shutdown computer -> quit vuze + shutdown
						// sleep/hibernate = announceAll and then sleep/hibernate with Vuze still running

					if ( action.equals( CA_QUIT_VUZE )){

						requestStop();

					}else if ( action.equals( CA_SLEEP ) || action.equals( CA_HIBERNATE )){

						announceAll( true );

						try{
							PlatformManagerFactory.getPlatformManager().shutdown(
									action.equals( CA_SLEEP )?PlatformManager.SD_SLEEP:PlatformManager.SD_HIBERNATE );

						}catch( Throwable e ){

							Debug.out( "PlatformManager: shutdown failed", e );
						}

					}else if ( action.equals( CA_SHUTDOWN )){

						ca_shutdown_computer_after_stop = true;

						requestStop();

					}else if ( action.startsWith( "RunScript" )){

						String script;

						if ( download_trigger ){

							script = COConfigurationManager.getStringParameter( "On Downloading Complete Script", "" );

						}else{

							script = COConfigurationManager.getStringParameter( "On Seeding Complete Script", "" );
						}

						executeScript( script, action, download_trigger );

					}else{

						Debug.out( "Unknown close action '" + action + "'" );
					}
				}
			});
	}

	private boolean		js_plugin_install_tried;

	void
	executeScript(
		String		script,
		String		action,
		boolean		download_trigger )
	{
		String script_type = "";

		if ( script.length() >=10 && script.substring(0,10).toLowerCase( Locale.US ).startsWith( "javascript" )){

			int	p1 = script.indexOf( '(' );

			int	p2 = script.lastIndexOf( ')' );

			if ( p1 != -1 && p2 != -1 ){

				script = script.substring( p1+1, p2 ).trim();

				if ( script.startsWith( "\"" ) && script.endsWith( "\"" )){

					script = script.substring( 1, script.length()-1 );
				}

					// allow people to escape " if it makes them feel better

				script = script.replaceAll( "\\\\\"", "\"" );

				script_type = ScriptProvider.ST_JAVASCRIPT;
			}
		}

		File script_file	= null;

		if ( script_type == "" ){

			script_file = FileUtil.newFile( script.trim());

			if ( !script_file.isFile()){

				Logger.log( new LogEvent(LOGID, "Script failed to run - '" + script_file + "' isn't a valid script file" ));

				Debug.out( "Invalid script: " + script_file );

				return;
			}
		}

		try{
			boolean	close_vuze = action.equals( "RunScriptAndClose" );

			if ( !close_vuze ){

					// assume script might implement a sleep

				announceAll( true );
			}

			if ( script_file != null ){

				getPluginManager().getDefaultPluginInterface().getUtilities().createProcess( script_file.getAbsolutePath());

			}else{

				boolean	provider_found = false;

				List<ScriptProvider> providers = CoreFactory.getSingleton().getPluginManager().getDefaultPluginInterface().getUtilities().getScriptProviders();

				for ( ScriptProvider p: providers ){

					if ( p.getScriptType() == script_type ){

						provider_found = true;

						Map<String,Object>	bindings = new HashMap<>();

						String intent = "shutdown" + "(\"" + action + "\")";

						bindings.put( "intent", intent );

						bindings.put( "is_downloading_complete", download_trigger);

						p.eval( script, bindings );

					}
				}

				if ( !provider_found ){

					if ( !js_plugin_install_tried ){

						js_plugin_install_tried = true;

						PluginUtils.installJavaScriptPlugin();
					}
				}
			}

			if ( close_vuze ){

				requestStop();
			}

		}catch( Throwable e ){

			Logger.log( new LogAlert(true,LogAlert.AT_ERROR, "Script failed to run - '" + script + "'", e ));

			Debug.out( "Invalid script: " + script, e );
		}
	}

	@Override
	public void
	executeOperation(
		int						type,
		CoreOperationTask 		task )
	{
		CoreOperation op =
				new CoreOperation()
				{
					@Override
					public int
					getOperationType()
					{
						return( type );
					}

					@Override
					public CoreOperationTask
					getTask()
					{
						return( task );
					}
				};

		boolean	run_it = true;
				
		try{
			addOperation( op );
			
			for ( CoreOperationListener l: operation_listeners ){
	
					// don't catch exceptions here as we want errors from task execution to propagate
					// back to the invoker
	
				if ( l.operationExecuteRequest( op )){
	
					run_it = false;
					
					break;
				}
			}
	
				// nobody volunteeered to run it for us, we'd better do it
	
			if ( run_it ){
	
				task.run( op );
			}
		}finally{
			
			if ( run_it ){
				
				removeOperation( op );
			}
		}
	}

	@Override
	public void
	addOperation(
		CoreOperation		op )
	{
		operations.add( op );
		
		for ( CoreOperationListener l: operation_listeners ){
			
			try{
				l.operationAdded( op );
				
			}catch( Throwable e ){
				
				Debug.out( e );
			}
		}
	}
	
	@Override
	public void
	removeOperation(
		CoreOperation		op )
	{
		if ( operations.remove( op )){
		
			for ( CoreOperationListener l: operation_listeners ){
				
				try{
					l.operationRemoved( op );
					
				}catch( Throwable e ){
					
					Debug.out( e );
				}
			}
		}
	}
	
	@Override
	public List<CoreOperation>
	getOperations()
	{
		return( operations.getList());
	}
	
	
	@Override
	public void
	addLifecycleListener(
		CoreLifecycleListener l )
	{
		boolean	lls;
		
		synchronized( lifecycle_listeners ){
		
			lifecycle_listeners.add(l);
			
			lls = ll_started;
		}
		
		if ( global_manager != null ){

			l.componentCreated( this, global_manager );
		}
		
		if ( lls ){
			
			l.started( this );
		}
	}

	@Override
	public void
	removeLifecycleListener(
		CoreLifecycleListener l )
	{
		lifecycle_listeners.remove(l);
	}

	@Override
	public void
	addOperationListener(
		CoreOperationListener l )
	{
		operation_listeners.add(l);
	}

	@Override
	public void
	removeOperationListener(
		CoreOperationListener l )
	{
		operation_listeners.remove(l);
	}

	public static void addCoreRunningListener(CoreRunningListener l) {
	   mon_coreRunningListeners.enter();
	   try {
    		if (CoreImpl.coreRunningListeners != null) {
    			coreRunningListeners.add(l);

    			return;
    		}
	   } finally {
	  	 mon_coreRunningListeners.exit();
	   }

	   l.coreRunning(CoreImpl.getSingleton());
	}

	@Override
	public void
	addPowerManagementListener(
		PowerManagementListener	listener )
	{
		power_listeners.add( listener );
	}

	@Override
	public void
	removePowerManagementListener(
		PowerManagementListener	listener )
	{
		power_listeners.remove( listener );
	}
}
