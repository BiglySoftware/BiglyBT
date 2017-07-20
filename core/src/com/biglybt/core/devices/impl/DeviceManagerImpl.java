/*
 * Created on Jan 27, 2009
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


package com.biglybt.core.devices.impl;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.util.*;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreLifecycleAdapter;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.config.impl.ConfigurationDefaults;
import com.biglybt.core.devices.*;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.*;
import com.biglybt.core.vuzefile.VuzeFile;
import com.biglybt.core.vuzefile.VuzeFileComponent;
import com.biglybt.core.vuzefile.VuzeFileHandler;
import com.biglybt.core.vuzefile.VuzeFileProcessor;
import com.biglybt.net.upnp.UPnPDevice;
import com.biglybt.pif.disk.DiskManagerFileInfo;
import com.biglybt.pif.ipc.IPCInterface;
import com.biglybt.pif.torrent.TorrentAttribute;
import com.biglybt.pif.tracker.web.TrackerWebPageRequest;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.UIManagerEvent;
import com.biglybt.pif.utils.PowerManagementListener;
import com.biglybt.pif.utils.StaticUtilities;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.platform.PlatformManagerFactory;

public class
DeviceManagerImpl
	implements DeviceManager, PowerManagementListener, AEDiagnosticsEvidenceGenerator
{
	private static final String	LOGGER_NAME 				= "Devices";
	private static final String	CONFIG_FILE 				= "devices.config";
	private static final String	AUTO_SEARCH_CONFIG_KEY		= "devices.config.auto_search";

	private static final int	AUTO_HIDE_OLD_DAYS_DEFAULT	= 31;
	private static final String	AUTO_HIDE_OLD_CONFIG_KEY	= "devices.config.auto_hide_old";

	private static final String	RSS_ENABLE_CONFIG_KEY		= "devices.config.rss_enable";

	private static final String OD_ENABLED_CONFIG_KEY			= "devices.config.od.enabled";
	private static final String OD_IS_AUTO_CONFIG_KEY			= "devices.config.od.auto";
	private static final String OD_INCLUDE_PRIVATE_CONFIG_KEY	= "devices.config.od.inc_priv";

	private static final String TRANSCODE_DIR_DEFAULT	= "transcodes";

	private static final String CONFIG_DEFAULT_WORK_DIR		= "devices.config.def_work_dir";
	private static final String CONFIG_DISABLE_SLEEP		= "devices.config.disable_sleep";


	protected static final int	DEVICE_UPDATE_PERIOD			= 5*1000;
	protected static final int	DEVICE_AUTO_HIDE_CHECK_PERIOD	= 2*60*1000;
	protected static final int	DEVICE_AUTO_HIDE_CHECK_TICKS	= DEVICE_AUTO_HIDE_CHECK_PERIOD/DEVICE_UPDATE_PERIOD;

	private static boolean pre_initialised;

	private static DeviceManagerImpl		singleton;

	public static void
	preInitialise()
	{
		synchronized( DeviceManagerImpl.class ){

			if ( pre_initialised ){

				return;
			}

			pre_initialised = true;
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

							int	type = comp.getType();

							if ( type == VuzeFileComponent.COMP_TYPE_DEVICE ){

								try{
									((DeviceManagerImpl)getSingleton()).importVuzeFile(
											comp.getContent(),
											( expected_types &
												( VuzeFileComponent.COMP_TYPE_DEVICE )) == 0 );

									comp.setProcessed();

								}catch( Throwable e ){

									Debug.printStackTrace(e);
								}
							}
						}
					}
				}
			});
	}

	public static DeviceManager
	getSingleton()
	{
		synchronized( DeviceManagerImpl.class ){

			if ( singleton == null ){

				singleton = new DeviceManagerImpl();
			}
		}

		return( singleton );
	}


	private Core core;

	private TorrentAttribute			od_manual_ta;

	List<DeviceImpl>			device_list = new ArrayList<>();
	private Map<String,DeviceImpl>		device_map	= new HashMap<>();

	DeviceTivoManager		tivo_manager;
	DeviceManagerUPnPImpl	upnp_manager;
	DeviceDriveManager		drive_manager;

	private Set<Device>				disable_events	= Collections.synchronizedSet( new HashSet<Device>());

		// have to go async on this as there are situations where we end up firing listeners
		// while holding monitors and this can result in deadlock if sync

	private static final int LT_DEVICE_ADDED		= 1;
	private static final int LT_DEVICE_CHANGED		= 2;
	private static final int LT_DEVICE_ATTENTION	= 3;
	private static final int LT_DEVICE_REMOVED		= 4;
	private static final int LT_INITIALIZED			= 5;

	private ListenerManager<DeviceManagerListener>	listeners =
		ListenerManager.createAsyncManager(
				"DM:ld",
				new ListenerManagerDispatcher<DeviceManagerListener>()
				{
					@Override
					public void
					dispatch(
						DeviceManagerListener 		listener,
						int 						type,
						Object 						value )
					{
						DeviceImpl	device = (DeviceImpl)value;

						switch( type ){

							case LT_DEVICE_ADDED:{

								listener.deviceAdded( device );

								break;
							}
							case LT_DEVICE_CHANGED:{

								if ( deviceAdded( device )){

									device.fireChanged();

									listener.deviceChanged( device );
								}

								break;
							}
							case LT_DEVICE_ATTENTION:{

								if ( deviceAdded( device )){

									listener.deviceAttentionRequest( device );
								}

								break;
							}
							case LT_DEVICE_REMOVED:{

								listener.deviceRemoved( device );

								break;
							}
							case LT_INITIALIZED:{

								listener.deviceManagerLoaded();

								break;
							}
						}
					}

					protected boolean
					deviceAdded(
						Device		device )
					{
						synchronized( DeviceManagerImpl.this ){

							return( device_list.contains( device ));
						}
					}
				});


	boolean	auto_search;
	int		auto_hide_old_days	= AUTO_HIDE_OLD_DAYS_DEFAULT;

	private DeviceManagerRSSFeed	rss_publisher;

	boolean	closing;

	boolean	config_unclean;
	boolean	config_dirty;

	int		explicit_search;

	volatile TranscodeManagerImpl	transcode_manager;

	private CopyOnWriteList<DeviceManagerDiscoveryListener>	discovery_listeners = new CopyOnWriteList<>();

	private int						getMimeType_fails;

	private Object					logger_lock = new Object();
	private AEDiagnosticsLogger		logger;

	private AESemaphore			init_sem = new AESemaphore( "dm:init" );

	private volatile boolean initialized = false;

	private Object		lsn_lock			= new Object();
	private String		local_service_name;

	protected
	DeviceManagerImpl()
	{
		AEDiagnostics.addWeakEvidenceGenerator( this );

		String	def_dir = COConfigurationManager.getStringParameter( "Default save path" );
		ConfigurationDefaults.getInstance().addParameter(CONFIG_DEFAULT_WORK_DIR,
				new File(def_dir, TRANSCODE_DIR_DEFAULT).getAbsolutePath());

		CoreFactory.addCoreRunningListener(new CoreRunningListener() {
			@Override
			public void coreRunning(Core core) {
				initWithCore(core);
			}
		});
	}

	private TranscodeManager
	ensureInitialised(
		boolean 	partial )
	{
		Core core =  CoreFactory.getSingleton();

		if ( core.isStarted()){

			initWithCore( core );

		}else if ( core.isInitThread()){

			Debug.out( "This is bad" );

			initWithCore( core );
		}

		if ( partial ){

			long start = SystemTime.getMonotonousTime();

			while( !init_sem.reserve(250)){

				if ( transcode_manager != null ){

					break;
				}

				if ( SystemTime.getMonotonousTime() - start >= 30*1000 ){

					Debug.out( "Timeout waiting for init" );

					AEDiagnostics.dumpThreads();

					break;
				}
			}
		}else{
			if ( !init_sem.reserve( 30*1000 )){

				Debug.out( "Timeout waiting for init" );

				AEDiagnostics.dumpThreads();
			}
		}

		return( transcode_manager );
	}

	void
	initWithCore(
		final Core _core )
	{
		synchronized( this ){

			if ( core != null ){

				return;
			}

			core = _core;
		}

		try{
			od_manual_ta = PluginInitializer.getDefaultInterface().getTorrentManager().getPluginAttribute( "device.manager.od.ta.manual" );

			rss_publisher = new DeviceManagerRSSFeed( this );

				// need to pick up auto-search early on

			COConfigurationManager.addAndFireParameterListeners(
					new String[]{
						AUTO_SEARCH_CONFIG_KEY,
						AUTO_HIDE_OLD_CONFIG_KEY,
					},
					new ParameterListener()
					{
						@Override
						public void
						parameterChanged(
							String name )
						{
							auto_search 		= COConfigurationManager.getBooleanParameter( AUTO_SEARCH_CONFIG_KEY, true );
							auto_hide_old_days	= COConfigurationManager.getIntParameter( AUTO_HIDE_OLD_CONFIG_KEY, AUTO_HIDE_OLD_DAYS_DEFAULT );
						}
					});

				// init tivo before upnp as upnp init completion starts up tivo

			tivo_manager = new DeviceTivoManager( this );

			upnp_manager = new DeviceManagerUPnPImpl( this );

			loadConfig();

			new DeviceiTunesManager( this );

			drive_manager = new DeviceDriveManager( this );

			transcode_manager = new TranscodeManagerImpl( this );

			transcode_manager.initialise();

			core.addLifecycleListener(
				new CoreLifecycleAdapter()
				{
					@Override
					public void
					stopping(
						Core core )
					{
						synchronized( DeviceManagerImpl.this ){

							if ( config_dirty || config_unclean ){

								saveConfig();
							}

							closing	= true;

							transcode_manager.close();

							DeviceImpl[] devices = getDevices();

							for ( DeviceImpl device: devices ){

								device.close();
							}
						}
					}
				});

			upnp_manager.initialise();

			SimpleTimer.addPeriodicEvent(
					"DeviceManager:update",
					DEVICE_UPDATE_PERIOD,
					new TimerEventPerformer()
					{
						private int tick_count = 0;

						@Override
						public void
						perform(
							TimerEvent event )
						{
							List<DeviceImpl> copy;

							tick_count++;

							transcode_manager.updateStatus( tick_count );

							synchronized( DeviceManagerImpl.this ){

								if( device_list.size() == 0 ){

									return;
								}

								copy = new ArrayList<>(device_list);
							}

							for ( DeviceImpl device: copy ){

								device.updateStatus( tick_count );
							}

							if ( 	auto_hide_old_days > 0 &&
									tick_count % DEVICE_AUTO_HIDE_CHECK_TICKS == 0 ){

								long	now = SystemTime.getCurrentTime();

								int	num_hidden = 0;

								for ( DeviceImpl device: copy ){

									if ( 	device.isLivenessDetectable() &&
											!device.isTagged()){

										int type = device.getType();

										if ( type == Device.DT_CONTENT_DIRECTORY ){

										}else if ( type == Device.DT_MEDIA_RENDERER ){

											DeviceMediaRenderer rend = (DeviceMediaRenderer)device;

											if ( rend.getRendererSpecies() != DeviceMediaRenderer.RS_OTHER ){

												continue;
											}
										}else{

											continue;
										}

										long age = now - device.getLastSeen();

										if ( age > auto_hide_old_days*24*60*60*1000L ){

											if ( !device.isHidden()){

												log( "Auto-hiding '" +  device.getName() + "'" );

												device.setHidden( true );

												device.setAutoHidden( true );

												num_hidden++;
											}
										}else{

											if ( device.isHidden() && device.isAutoHidden()){

												log( "Auto-showing '" +  device.getName() + "'" );

												device.setAutoHidden( false );

												device.setHidden( false );
											}
										}
									}
								}

								if ( num_hidden > 0 ){

									Logger.log(
										new LogAlert(
											true,
											LogAlert.AT_INFORMATION,
											MessageText.getString(
												"device.autohide.alert",
												new String[]{
													String.valueOf( num_hidden ),
													String.valueOf( auto_hide_old_days )
												})));
								}
							}
						}
					});

			initialized = true;

			listeners.dispatch( LT_INITIALIZED, null );

			core.addPowerManagementListener( this );

		}finally{

			init_sem.releaseForever();
		}
	}

	protected void
	UPnPManagerStarted()
	{
		tivo_manager.startUp();

		DeviceImpl[] devices = getDevices();

		for ( DeviceImpl device: devices ){

			if ( device instanceof DeviceUPnPImpl ){

				((DeviceUPnPImpl)device).UPnPInitialised();
			}
		}
	}

	protected Core
	getCore()
	{
		return( core );
	}

	protected DeviceManagerUPnPImpl
	getUPnPManager()
	{
		return( upnp_manager );
	}

	@Override
	public String
	getLocalServiceName()
	{
		synchronized( lsn_lock ){

			if ( local_service_name == null ){

					// try to use the media server's name

				try{
					IPCInterface ipc = getUPnPManager().getUPnPAVIPC();

					if ( ipc != null ){

						local_service_name = (String)ipc.invoke( "getServiceName", new Object[]{});
					}

				}catch( Throwable e ){
				}

				if ( local_service_name == null ){

					local_service_name = Constants.APP_NAME;

					try{
						String cn = PlatformManagerFactory.getPlatformManager().getComputerName();

						if ( cn != null && cn.length() > 0 ){

							local_service_name += " on " + cn;
						}
					}catch( Throwable e ){
					}
				}
			}

			return( local_service_name );
		}
	}

	@Override
	public boolean
	isTiVoEnabled()
	{
		return( tivo_manager.isEnabled());
	}

	@Override
	public void
	setTiVoEnabled(
		boolean	enabled )
	{
		tivo_manager.setEnabled( enabled );
	}

	public TranscodeProvider[]
	getProviders()
	{
		TranscodeManager tm = ensureInitialised( true );

		if ( tm == null ){

			return( new TranscodeProvider[0] );
		}

		return( tm.getProviders());
	}

	@Override
	public DeviceTemplate[]
	getDeviceTemplates(
		int		device_type )
	{
		if ( transcode_manager == null || device_type != Device.DT_MEDIA_RENDERER ){

			return( new DeviceTemplate[0] );
		}

		TranscodeProvider[] providers = transcode_manager.getProviders();

		List<DeviceTemplate> result = new ArrayList<>();

		for ( TranscodeProvider provider: providers ){

			TranscodeProfile[] profiles = provider.getProfiles();

			Map<String,DeviceMediaRendererTemplateImpl>	class_map = new HashMap<>();

			for ( TranscodeProfile profile: profiles ){

				String	classification = profile.getDeviceClassification();

				if ( classification.startsWith( "apple." )){

					classification = "apple.";
				}

				boolean	auto =
					classification.equals( "sony.PS3" ) ||
					classification.equals( "microsoft.XBox" ) ||
					classification.equals( "apple." ) ||
					classification.equals( "nintendo.Wii" ) ||
					classification.equals( "browser.generic" );

				DeviceMediaRendererTemplateImpl temp = class_map.get( classification );

				if ( temp == null ){

					temp = new DeviceMediaRendererTemplateImpl( this, classification, auto );

					class_map.put( classification, temp );

					result.add( temp );
				}

				temp.addProfile( profile );
			}
		}

		return( result.toArray( new DeviceTemplate[ result.size() ]));
	}

	@Override
	public DeviceManufacturer[]
  	getDeviceManufacturers(
  		int		device_type )
	{
		DeviceTemplate[] templates = getDeviceTemplates( device_type );

		Map<String,DeviceManufacturerImpl>	map = new HashMap<>();

		for ( DeviceTemplate template: templates ){

			if ( template.getType() != device_type ){

				continue;
			}

			String	man_str = template.getManufacturer();

			DeviceManufacturerImpl man = map.get( man_str );

			if ( man == null ){

				man = new DeviceManufacturerImpl( man_str );

				map.put( man_str, man );
			}

			man.addTemplate( template );
		}

		return( map.values().toArray( new DeviceManufacturer[ map.size() ] ));
	}

	@Override
	public Device
	addVirtualDevice(
		int 		type,
		String		uid,
		String		classification,
		String		name )

		throws DeviceManagerException
	{
		return( createDevice( type, uid, classification, name, true ));
	}

	@Override
	public Device
	addInetDevice(
		int 		type,
		String		uid,
		String		classification,
		String		name,
		InetAddress address)

		throws DeviceManagerException
	{
		Device device = createDevice( type, uid, classification, name, false );
		device.setAddress(address);
		return device;
	}

	protected Device
	createDevice(
		int						device_type,
		String					uid,
		String					classification,
		String					name,
		boolean					manual )

		throws DeviceManagerException
	{
		if ( device_type == Device.DT_MEDIA_RENDERER ){

			DeviceImpl res;

			if ( manual ){

				res = new DeviceMediaRendererManual( this, uid, classification, true, name );

			}else{

				res = new DeviceMediaRendererImpl( this, uid, classification, true, name );
			}

			res = addDevice( res );

			return( res );

		}else{

			throw( new DeviceManagerException( "Can't manually create this device type" ));
		}
	}

	@Override
	public void
	search(
		final int					millis,
		final DeviceSearchListener	listener )
	{
		new AEThread2( "DM:search", true )
		{
			@Override
			public void
			run()
			{
				synchronized( DeviceManagerImpl.this ){

					explicit_search++;
				}

				tivo_manager.search();

				drive_manager.search();

				AESemaphore	sem = new AESemaphore( "DM:search" );

				DeviceManagerListener	dm_listener =
					new DeviceManagerListener()
					{
						@Override
						public void
						deviceAdded(
							Device		device )
						{
							listener.deviceFound( device );
						}

						@Override
						public void
						deviceChanged(
							Device		device )
						{
						}

						@Override
						public void
						deviceAttentionRequest(
							Device		device )
						{
						}

						@Override
						public void
						deviceRemoved(
							Device		device )
						{
						}

						@Override
						public void
						deviceManagerLoaded() {
						}
					};

				try{
					addListener( dm_listener );

					upnp_manager.search();

					sem.reserve( millis );

				}finally{

					synchronized( DeviceManagerImpl.this ){

						explicit_search--;
					}

					removeListener( dm_listener );

					listener.complete();
				}
			}
		}.start();
	}

	protected DeviceImpl
	getDevice(
		String		id )
	{
		synchronized( this ){

			return( device_map.get( id ));
		}
	}

	protected DeviceImpl
	addDevice(
		DeviceImpl		device )
	{
		return( addDevice( device, true ));
	}

	protected DeviceImpl
	addDevice(
		DeviceImpl		device,
		boolean			is_alive )
	{
			// for xbox (currently) we automagically replace a manual entry with an auto one as we may have
			// added the manual one when receiving a previous browse before getting the UPnP renderer details

		DeviceImpl	existing = null;

		synchronized( this ){

			existing = device_map.get( device.getID());

			if ( existing != null ){

				existing.updateFrom( device, is_alive );

			}else{

				if ( device.getType() == Device.DT_MEDIA_RENDERER ){

					DeviceMediaRenderer renderer = (DeviceMediaRenderer)device;

					if ( renderer.getRendererSpecies() == DeviceMediaRenderer.RS_XBOX && !renderer.isManual()){

						for ( DeviceImpl d: device_list ){

							if ( d.getType() == Device.DT_MEDIA_RENDERER ){

								DeviceMediaRenderer r = (DeviceMediaRenderer)d;

								if ( r.getRendererSpecies() == DeviceMediaRenderer.RS_XBOX && r.isManual()){

									existing = d;

									log( "Merging " + device.getString() + " -> " + existing.getString());

									String	secondary_id = device.getID();

									existing.setSecondaryID( secondary_id );

									existing.updateFrom( device, is_alive );
								}
							}
						}
					}
				}
			}

			if ( existing == null ){

				device_list.add( device );

				device_map.put( device.getID(), device );
			}
		}

		if ( existing != null ){

				// don't trigger config save here, if anything has changed it will have been handled
				// by the updateFrom call above

			// if anything has changed then the updateFrom methods should have indicated this
			// so there's no need to blindly fire a change event here
			// deviceChanged( existing, false );

			applyUpdates( existing );

			return( existing );
		}

		try{
			disable_events.add( device );

			device.initialise();

			if ( is_alive ){

				device.alive();
			}

			applyUpdates( device );

		}finally{

			disable_events.remove( device );
		}
		deviceAdded( device );

		configDirty();

		return( device );
	}

	protected void
	applyUpdates(
		DeviceImpl		device )
	{
		if ( device.getType() == Device.DT_MEDIA_RENDERER ){

			DeviceMediaRenderer renderer = (DeviceMediaRenderer)device;

			if ( renderer instanceof DeviceUPnPImpl ){

				UPnPDevice upnp_device = ((DeviceUPnPImpl)renderer).getUPnPDevice();

				if ( upnp_device != null ){

					String lc_manufacturer 	= getOptionalLC( upnp_device.getManufacturer());
					String lc_model			= getOptionalLC( upnp_device.getModelName());
					String lc_fname			= getOptionalLC( upnp_device.getFriendlyName());

					if ( lc_manufacturer.startsWith( "samsung" )){

						device.setPersistentStringProperty( DeviceImpl.PP_REND_CLASSIFICATION, "samsung.generic" );

						TranscodeProfile[] profiles = device.getTranscodeProfiles();

						if ( profiles.length == 0 ){

							device.setTranscodeRequirement( TranscodeTarget.TRANSCODE_NEVER );

						}else{

							device.setTranscodeRequirement( TranscodeTarget.TRANSCODE_WHEN_REQUIRED );
						}
					}else if ( lc_manufacturer.startsWith( "western digital" )){

							device.setPersistentStringProperty( DeviceImpl.PP_REND_CLASSIFICATION, "western.digital.generic" );

							TranscodeProfile[] profiles = device.getTranscodeProfiles();

							if ( profiles.length == 0 ){

								device.setTranscodeRequirement( TranscodeTarget.TRANSCODE_NEVER );

							}else{

								device.setTranscodeRequirement( TranscodeTarget.TRANSCODE_WHEN_REQUIRED );
							}
					}else if ( lc_manufacturer.startsWith( "sony" ) && lc_fname.startsWith( "bravia" )){

						device.setPersistentStringProperty( DeviceImpl.PP_REND_CLASSIFICATION, "sony.bravia" );

					}else if ( lc_model.equals( "windows media player" )){

						String model_number = upnp_device.getModelNumber();

						if ( model_number != null ){

							try{
								int num = Integer.parseInt( model_number );

								if ( num >= 12 ){

									device.setPersistentStringProperty( DeviceImpl.PP_REND_CLASSIFICATION, "ms_wmp.generic" );

									TranscodeProfile[] profiles = device.getTranscodeProfiles();

									if ( profiles.length == 0 ){

										device.setTranscodeRequirement( TranscodeTarget.TRANSCODE_NEVER );

									}else{

										device.setTranscodeRequirement( TranscodeTarget.TRANSCODE_WHEN_REQUIRED );
									}
								}
							}catch( Throwable e ){

							}
						}
					}
				}
			}
		}
	}

	private String
	getOptionalLC(
		String	str )
	{
		if ( str == null ){

			return( "" );
		}

		return( str.toLowerCase().trim());
	}

	protected void
	removeDevice(
		DeviceImpl		device )
	{
		synchronized( this ){

			DeviceImpl existing = device_map.remove( device.getID());

			if ( existing == null ){

				return;
			}

			device_list.remove( device );

			String secondary_id = device.getSecondaryID();

			if ( secondary_id != null ){

				device_map.remove( secondary_id );
			}
		}

		device.destroy();

		deviceRemoved( device );

		configDirty();
	}

	@Override
	public boolean
	isBusy(
		int	device_type )
	{
			// transcoding is rolled into renderers

		if ( device_type == Device.DT_UNKNOWN || device_type == Device.DT_MEDIA_RENDERER ){

			if ( getTranscodeManager().getQueue().isTranscoding()){

				return( true );
			}
		}

		DeviceImpl[] devices = getDevices();

		for ( DeviceImpl device: devices ){

			if ( device.isBusy()){

				if ( device_type == Device.DT_UNKNOWN || device_type == device.getType()){

					return( true );
				}
			}
		}

		return( false );
	}

	@Override
	public DeviceImpl[]
  	getDevices()
	{
		synchronized( this ){

			return( device_list.toArray( new DeviceImpl[ device_list.size()] ));
		}
	}

	@Override
	public boolean
	getAutoSearch()
	{
		return( auto_search );
	}

	@Override
	public void
	setAutoSearch(
		boolean	auto )
	{
		COConfigurationManager.setParameter( AUTO_SEARCH_CONFIG_KEY, auto );
	}

	@Override
	public int
	getAutoHideOldDevicesDays()
	{
		return( COConfigurationManager.getIntParameter( AUTO_HIDE_OLD_CONFIG_KEY, AUTO_HIDE_OLD_DAYS_DEFAULT ));
	}

	@Override
	public void
	setAutoHideOldDevicesDays(
		int		days )
	{
		COConfigurationManager.setParameter( AUTO_HIDE_OLD_CONFIG_KEY, days );
	}

	@Override
	public boolean
	isRSSPublishEnabled()
	{
		return( COConfigurationManager.getBooleanParameter( RSS_ENABLE_CONFIG_KEY, false ) );
	}

	@Override
	public void
	setRSSPublishEnabled(
		boolean		enabled )
	{
		COConfigurationManager.setParameter( RSS_ENABLE_CONFIG_KEY, enabled );
	}

	@Override
	public String
	getRSSLink()
	{
		return( rss_publisher.getFeedURL());
	}

	protected boolean
	isExplicitSearch()
	{
		synchronized( this ){

			return( explicit_search > 0 );
		}
	}

	protected boolean
	isClosing()
	{
		synchronized( this ){

			return( closing );
		}
	}

	protected void
	loadConfig()
	{
		if ( !FileUtil.resilientConfigFileExists( CONFIG_FILE )){

			return;
		}

		log( "Loading configuration" );

		synchronized( this ){

			Map map = FileUtil.readResilientConfigFile( CONFIG_FILE );

			List	l_devices = (List)map.get( "devices" );

			if ( l_devices != null ){

				for (int i=0;i<l_devices.size();i++){

					Map	m = (Map)l_devices.get(i);

					try{
						DeviceImpl device = DeviceImpl.importFromBEncodedMapStatic(this,  m );

						device_list.add( device );

						device_map.put( device.getID(), device );

						String secondary_id = device.getSecondaryID();

						if ( secondary_id != null ){

							device_map.put( secondary_id, device );
						}

						device.initialise();

						log( "    loaded " + device.getString());

					}catch( Throwable e ){

						log( "Failed to import subscription from " + m, e );
					}
				}
			}
		}
	}

	protected void
	configDirty(
		DeviceImpl		device,
		boolean			save_changes )
	{
		deviceChanged( device, save_changes );
	}

	protected void
	configDirty()
	{
		synchronized( this ){

			if ( config_dirty ){

				return;
			}

			config_dirty = true;

			new DelayedEvent(
				"Subscriptions:save", 5000,
				new AERunnable()
				{
					@Override
					public void
					runSupport()
					{
						synchronized( DeviceManagerImpl.this ){

							if ( !config_dirty ){

								return;
							}

							saveConfig();
						}
					}
				});
		}
	}

	protected void
	saveConfig()
	{
		log( "Saving configuration" );

		synchronized( this ){

			if ( closing ){

					// to late to try writing

				return;
			}

			config_dirty 	= false;
			config_unclean	= false;

			if ( device_list.size() == 0 ){

				FileUtil.deleteResilientConfigFile( CONFIG_FILE );

			}else{

				Map map = new HashMap();

				List	l_devices = new ArrayList();

				map.put( "devices", l_devices );

				Iterator<DeviceImpl>	it = device_list.iterator();

				while( it.hasNext()){

					DeviceImpl device = it.next();

					try{
						Map d = new HashMap();

						device.exportToBEncodedMap( d, false );

						l_devices.add( d );

					}catch( Throwable e ){

						log( "Failed to save device " + device.getString(), e );
					}
				}

				FileUtil.writeResilientConfigFile( CONFIG_FILE, map );
			}
		}
	}

	protected void
	deviceAdded(
		DeviceImpl		device )
	{
		configDirty();

		listeners.dispatch( LT_DEVICE_ADDED, device );
	}


	protected void
	deviceChanged(
		DeviceImpl		device,
		boolean			save_changes )
	{
		if ( save_changes ){

			configDirty();

		}else{

			synchronized( this ){

				config_unclean = true;
			}
		}

		if ( !disable_events.contains( device )){

			//System.out.println(System.currentTimeMillis() + "] CHANGE -> " + device.getID() + "/" + device.getName() + " via " + Debug.getCompressedStackTrace());

			listeners.dispatch( LT_DEVICE_CHANGED, device );
		}
	}

	protected void
	deviceRemoved(
		DeviceImpl		device )
	{
		configDirty();

		listeners.dispatch( LT_DEVICE_REMOVED, device );
	}

	protected void
	requestAttention(
		DeviceImpl		device )
	{
		listeners.dispatch( LT_DEVICE_ATTENTION, device );
	}

	protected URL
	getStreamURL(
		TranscodeFileImpl		file,
		String					host )
	{
		IPCInterface ipc = upnp_manager.getUPnPAVIPC();

		if ( ipc != null ){

			try{
				DiskManagerFileInfo f = file.getTargetFile();

				String str = (String)ipc.invoke( "getContentURL", new Object[]{ f });

				if ( str != null && str.length() > 0 ){

					if ( host != null ){

						str = str.replace( "127.0.0.1", host );
					}

					return( new URL( str ));
				}
			}catch( Throwable e ){

			}
		}

		return( null );
	}

	protected String
	getMimeType(
		TranscodeFileImpl		file )
	{
		if ( getMimeType_fails > 5 ){

			return( null );
		}

		IPCInterface ipc = upnp_manager.getUPnPAVIPC();

		if ( ipc != null ){

			try{
				DiskManagerFileInfo f = file.getTargetFile();

				String[] strs = (String[])ipc.invoke( "getMimeTypes", new Object[]{ f });

				if ( strs != null && strs.length > 0 ){

					return( strs[0] );
				}
			}catch( Throwable e ){

				getMimeType_fails++;

				e.printStackTrace();
			}
		}

		return( null );
	}

	@Override
	public File
	getDefaultWorkingDirectory()
	{
		return( getDefaultWorkingDirectory( false ));
	}

	public File
	getDefaultWorkingDirectory(
		boolean		persist )
	{
		String def = COConfigurationManager.getStringParameter( CONFIG_DEFAULT_WORK_DIR ).trim();

		File f = new File( def );

		if ( !f.exists() && persist ){

				f.mkdirs();
		}

		return( f );
	}

	@Override
	public void
	setDefaultWorkingDirectory(
		File		dir )
	{
		File existing = getDefaultWorkingDirectory( false );

		if ( !existing.getAbsolutePath().equals( dir.getAbsolutePath())){

				// default has changed, reset all device save locations so that they pick up the change

			DeviceImpl[] devices = getDevices();

			for ( DeviceImpl device: devices ){

				device.resetWorkingDirectory();
			}
		}

		COConfigurationManager.setParameter( CONFIG_DEFAULT_WORK_DIR, dir.getAbsolutePath());
	}

	@Override
	public boolean
	getDisableSleep()
	{
		return( COConfigurationManager.getBooleanParameter( CONFIG_DISABLE_SLEEP, true ));
	}

	@Override
	public void
	setDisableSleep(
		boolean		b )
	{
		COConfigurationManager.setParameter( CONFIG_DISABLE_SLEEP, b );
	}

	@Override
	public TranscodeManagerImpl
	getTranscodeManager()
	{
		if ( transcode_manager == null ){

			ensureInitialised( false );
		}

		return( transcode_manager );
	}

	@Override
	public boolean isTranscodeManagerInitialized() {
		return transcode_manager != null;
	}

	@Override
	public UnassociatedDevice[]
	getUnassociatedDevices()
	{
		return( upnp_manager.getUnassociatedDevices());
	}

	@Override
	public String
	getPowerName()
	{
		return( "Transcode" );
	}

	@Override
	public boolean
	requestPowerStateChange(
		int		new_state,
		Object	data )
	{
		if ( getDisableSleep()){

			if ( getTranscodeManager().getQueue().isTranscoding()){

				return( false );
			}
		}

		return( true );
	}

	@Override
	public void
	informPowerStateChange(
		int		new_state,
		Object	data )
	{

	}

  	@Override
	  public void
  	addListener(
  		DeviceManagerListener		listener )
  	{
  		listeners.addListener( listener );

  		if (initialized) {

  			listeners.dispatch( listener, LT_INITIALIZED, null );
  		}
  	}

	protected boolean
	browseReceived(
		TrackerWebPageRequest		request,
		Map<String,Object>			browser_args )
	{
		for ( DeviceManagerDiscoveryListener l: discovery_listeners ){

			try{
				if ( l.browseReceived( request, browser_args )){

					return( true );
				}
			}catch( Throwable e ){

				Debug.out( e );
			}
		}

		return( false );
	}

	protected VuzeFile
	exportVuzeFile(
		DeviceImpl		device )

		throws IOException
	{
		VuzeFile	vf = VuzeFileHandler.getSingleton().create();

		Map	map = new HashMap();

		Map device_map = new HashMap();

		map.put( "device", device_map );

		device.exportToBEncodedMap( device_map, true );

		vf.addComponent( VuzeFileComponent.COMP_TYPE_DEVICE, map );

		return( vf );
	}

	void
	importVuzeFile(
		Map			map,
		boolean		warn_user )
	{
		Map	m = (Map)map.get( "device" );

		try{
			DeviceImpl device = DeviceImpl.importFromBEncodedMapStatic( this, m );

			DeviceImpl existing;

			synchronized( this ){

				existing = device_map.get( device.getID());
			}

			if ( existing == null ){

				if ( warn_user ){

					UIManager ui_manager = StaticUtilities.getUIManager( 120*1000 );

					String details = MessageText.getString(
							"device.import.desc",
							new String[]{ device.getName()});

					long res = ui_manager.showMessageBox(
							"device.import.title",
							"!" + details + "!",
							UIManagerEvent.MT_YES | UIManagerEvent.MT_NO );

					if ( res != UIManagerEvent.MT_YES ){

						return;
					}
				}

				addDevice( device, false );

			}else{

				if ( warn_user ){

					UIManager ui_manager = StaticUtilities.getUIManager( 120*1000 );

					String details = MessageText.getString(
							"device.import.dup.desc",
							new String[]{ existing.getName()});

					ui_manager.showMessageBox(
							"device.import.dup.title",
							"!" + details + "!",
							UIManagerEvent.MT_OK );
				}

			}
		}catch( Throwable e ){

			Debug.out( e );
		}
	}

	@Override
	public void
	addDiscoveryListener(
		DeviceManagerDiscoveryListener	listener )
	{
		discovery_listeners.add( listener );
	}

	@Override
	public void
	removeDiscoveryListener(
		DeviceManagerDiscoveryListener	listener )
	{
		discovery_listeners.remove( listener );
	}

  	@Override
	  public void
  	removeListener(
  		DeviceManagerListener		listener )
  	{
  		listeners.removeListener( listener );
  	}

  @Override
  public Device findDevice(UPnPDevice upnpDevice) {
  	DeviceImpl[] devices = getDevices();
  	for (DeviceImpl device : devices) {
			if (device instanceof DeviceUPnPImpl) {
				DeviceUPnPImpl deviceUPnP = (DeviceUPnPImpl) device;
				UPnPDevice uPnPDevice2 = deviceUPnP.getUPnPDevice();
				if (upnpDevice.equals(uPnPDevice2)) {
					return device;
				}
			}
		}
  	return null;
  }

	protected AEDiagnosticsLogger
	getLogger()
	{
		synchronized( logger_lock ){

			if ( logger == null ){

				logger = AEDiagnostics.getLogger( LOGGER_NAME );
			}

			return( logger );
		}
	}

	public void
	log(
		String 		s,
		Throwable 	e )
	{
		AEDiagnosticsLogger diag_logger = getLogger();

		diag_logger.log( s );
		diag_logger.log( e );
	}

	public void
	log(
		String 	s )
	{
		AEDiagnosticsLogger diag_logger = getLogger();

		diag_logger.log( s );
	}

	@Override
	public void
	generate(
		IndentWriter		writer )
	{
		writer.println( "Devices" );

		try{
			writer.indent();

			DeviceImpl[] devices = getDevices();

			for ( DeviceImpl device: devices ){

				device.generate( writer );
			}

			if ( transcode_manager != null ){

				transcode_manager.generate( writer );
			}
		}finally{

			writer.exdent();
		}
	}

	protected static class
	DeviceManufacturerImpl
		implements DeviceManufacturer
	{
		private String 	name;

		private List<DeviceTemplate>	templates = new ArrayList<>();

		protected
		DeviceManufacturerImpl(
			String		_name )
		{
			name	= _name;
		}

		protected void
		addTemplate(
			DeviceTemplate	t )
		{
			templates.add( t );
		}

		@Override
		public String
		getName()
		{
			return( name );
		}

		@Override
		public DeviceTemplate[]
		getDeviceTemplates()
		{
			return( templates.toArray( new DeviceTemplate[ templates.size()] ));
		}
	}
}
