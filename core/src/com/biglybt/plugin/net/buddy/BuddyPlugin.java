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

package com.biglybt.plugin.net.buddy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreLifecycleAdapter;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.tag.TagType;
import com.biglybt.core.util.AENetworkClassifier;
import com.biglybt.core.util.AEThread2;
import com.biglybt.core.util.Base32;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.CopyOnWriteList;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.TimeFormatter;
import com.biglybt.core.util.TorrentUtils;
import com.biglybt.core.util.UrlUtils;
import com.biglybt.core.util.Wiki;
import com.biglybt.core.util.protocol.azplug.AZPluginConnection;
import com.biglybt.core.vuzefile.VuzeFileHandler;
import com.biglybt.core.xml.util.XUXmlWriter;
import com.biglybt.pif.Plugin;
import com.biglybt.pif.PluginConfig;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.PluginListener;
import com.biglybt.pif.disk.DiskManagerFileInfo;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadException;
import com.biglybt.pif.download.DownloadScrapeResult;
import com.biglybt.pif.ipc.IPCException;
import com.biglybt.pif.logging.LoggerChannel;
import com.biglybt.pif.messaging.generic.GenericMessageConnection;
import com.biglybt.pif.network.ConnectionManager;
import com.biglybt.pif.network.RateLimiter;
import com.biglybt.pif.peers.Peer;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.torrent.TorrentAttribute;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.UIManagerListener;
import com.biglybt.pif.ui.config.BooleanParameter;
import com.biglybt.pif.ui.config.IntParameter;
import com.biglybt.pif.ui.config.Parameter;
import com.biglybt.pif.ui.config.ParameterGroup;
import com.biglybt.pif.ui.config.ParameterListener;
import com.biglybt.pif.ui.config.ParameterTabFolder;
import com.biglybt.pif.ui.config.StringListParameter;
import com.biglybt.pif.ui.config.StringParameter;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemFillListener;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pif.ui.model.BasicPluginConfigModel;
import com.biglybt.pif.ui.tables.TableContextMenuItem;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.pif.ui.tables.TableRow;
import com.biglybt.pif.utils.DelayedTask;
import com.biglybt.pif.utils.LocaleListener;
import com.biglybt.pif.utils.LocaleUtilities;
import com.biglybt.pif.utils.UTTimerEvent;
import com.biglybt.pif.utils.UTTimerEventPerformer;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.plugin.I2PHelpers;
import com.biglybt.plugin.net.buddy.tracker.BuddyPluginTracker;

public class
BuddyPlugin
	implements Plugin
{
	public static final boolean SUPPORT_ONLINE_STATUS		= true;

	public static final int	TIMER_PERIOD	= 5*1000;

	private static final int FEED_UPDATE_MIN_MILLIS	= 6*60*60*1000;

	private static final String VIEW_ID = "azbuddy";

	public static void
	load(
		PluginInterface		plugin_interface )
	{
		String name =
			plugin_interface.getUtilities().getLocaleUtilities().getLocalisedMessageText( "Views.plugins." + VIEW_ID + ".title" );

		plugin_interface.getPluginProperties().setProperty( "plugin.version", 	"1.0" );
		plugin_interface.getPluginProperties().setProperty( "plugin.name", 		name );
	}

	
	private PluginInterface	plugin_interface;

	public static final int STATUS_ONLINE			= 0;
	public static final int STATUS_AWAY				= 1;
	public static final int STATUS_NOT_AVAILABLE	= 2;
	public static final int STATUS_BUSY				= 3;
	public static final int STATUS_APPEAR_OFFLINE	= 4;

	public static final String[] STATUS_VALUES 	= { "0", "1", "2", "3", "4" };

	public static final String[] STATUS_KEYS = {
		"os_online", "os_away", "os_not_avail", "os_busy", "os_offline"
	};

	public static final String[] STATUS_STRINGS = new String[ STATUS_KEYS.length ];

	private BooleanParameter 		classic_enabled_param;
	
	private StringParameter 		nick_name_public_param;
	private StringListParameter 	online_status_public_param;
	private StringParameter 		profile_public_param;
	
	private StringParameter 		nick_name_anon_param;
	private StringListParameter 	online_status_anon_param;
	private StringParameter 		profile_anon_param;


	private List<String>			public_profile_list	= new ArrayList<>();
	private List<String>			anon_profile_list	= new ArrayList<>();

	private BooleanParameter 		enable_chat_notifications;
	private StringParameter 		cat_pub;


	private BooleanParameter 		beta_enabled_param;


	private BuddyPluginTracker	buddy_tracker;

	private TorrentAttribute	ta_category;

	private LoggerChannel	logger;

	private Set<String>	public_tags_or_categories = new HashSet<>();

	private boolean lan_local_peers;
	private boolean fp_enable;

	private BuddyPluginBeta		beta_plugin;

	private BuddyPluginViewInterface	swt_ui;
	private List<Runnable>				swt_ui_waiters = new ArrayList<>();
	
	private CopyOnWriteList<BuddyPluginListener>				listeners 			= new CopyOnWriteList<>();
	private CopyOnWriteList<PartialBuddyListener>				pb_listeners		= new CopyOnWriteList<>();

	private com.biglybt.core.config.ParameterListener configEnabledListener;

	private AtomicBoolean			initialization_complete = new AtomicBoolean( false );

	protected static final int	INIT_UNKNOWN		= 0;
	protected static final int	INIT_OK				= 1;
	protected static final int	INIT_BAD			= 2;

	private volatile int	 initialisation_state = INIT_UNKNOWN;

	private RateLimiter	inbound_limiter;

	private RateLimiter	outbound_limiter;

	private  BuddyPluginNetwork[]		plugin_networks;
	
	
	@Override
	public void
	initialize(
		final PluginInterface		_plugin_interface )
	{
		plugin_interface	= _plugin_interface;
		
		ta_category		= plugin_interface.getTorrentManager().getAttribute( TorrentAttribute.TA_CATEGORY );

		logger = plugin_interface.getLogger().getChannel( "Friends" );

		logger.setDiagnostic();

		plugin_networks = new BuddyPluginNetwork[]{ 
			 new BuddyPluginNetwork( plugin_interface, this, AENetworkClassifier.AT_PUBLIC ),
			 new BuddyPluginNetwork( plugin_interface, this, AENetworkClassifier.AT_I2P )
		};
		
		final LocaleUtilities lu = plugin_interface.getUtilities().getLocaleUtilities();
		Properties l10n_constants = new Properties();
		l10n_constants.put("azbuddy.classic.link.url", Wiki.FRIENDS);
		l10n_constants.put("azbuddy.dchat.link.url", Wiki.DECENTRALIZED_CHAT);
		l10n_constants.put("azbuddy.profile.info.url", Wiki.FRIENDS_PUBLIC_PROFILE);
		
		lu.integrateLocalisedMessageBundle(l10n_constants);

		lu.addListener(
			new LocaleListener()
			{
				@Override
				public void
				localeChanged(
					Locale		l )
				{
					updateLocale(lu);
				}
			});

		updateLocale(lu);

		BasicPluginConfigModel config = plugin_interface.getUIManager().createBasicPluginConfigModel( "Views.plugins." + VIEW_ID + ".title" );

			// enabled

		classic_enabled_param = config.addBooleanParameter2( "azbuddy.enabled", "azbuddy.enabled", false );

		ParameterTabFolder	network_tab 		= config.createTabFolder();
		ParameterGroup		network_anon_item 	= null;
		
		for ( int i=0;i<2;i++){
		
			boolean is_pub_tab = i == 0;
			
			String suffix = is_pub_tab?"":".anon";
			
				// nickname
				
			StringParameter nick_param = config.addStringParameter2( "azbuddy.nickname" + suffix, "azbuddy.nickname", "" );
	
			nick_param.setGenerateIntermediateEvents( false );
	
			nick_param.addListener(
					new ParameterListener()
					{
						@Override
						public void
						parameterChanged(
							Parameter	param )
						{
							updateNickName( is_pub_tab, nick_param.getValue());
						}
					});
	
				// online status
	
			String[]	os_values 	= STATUS_VALUES;
			String[]	os_labels	= STATUS_STRINGS;
	
			StringListParameter os_param = config.addStringListParameter2(
					"azbuddy.online_status" + suffix, "azbuddy.online_status",
					os_values,
					os_labels,
					os_values[0] );
	
			os_param.addListener(
					new ParameterListener()
					{
						@Override
						public void
						parameterChanged(
							Parameter	param )
						{
							updateOnlineStatus( is_pub_tab, Integer.parseInt( os_param.getValue()));
						}
					});
	
			os_param.setVisible( SUPPORT_ONLINE_STATUS  ); // If we add this then use proper message texts in the STATUS_STRINGS
	

			StringParameter profile_param = config.addStringParameter2( "azbuddy.profile.info" + suffix, "", "" );
			profile_param.setLabelText(
					"<a href=\"" + MessageText.getString("azbuddy.profile.info.url")
							+ "\">" + MessageText.getString("azbuddy.profile.info") + "</a>");

			profile_param.setMultiLine( 5 );
			profile_param.setGenerateIntermediateEvents( false );
			
			profile_param.addListener(
					new ParameterListener()
					{
						@Override
						public void
						parameterChanged(
							Parameter 	param )
						{
							updateProfiles();
						}
					});
				
			ParameterGroup profile_group = config.createGroup(
					is_pub_tab?"azbuddy.public.profile":"azbuddy.anon.profile",
					new Parameter[]{
							profile_param
					});


			ParameterGroup network_item = 
					config.createGroup( 
					is_pub_tab?"label.public":"label.anon",
					new Parameter[]{ 
							nick_param, os_param, profile_group
					});
				
			if ( is_pub_tab ){
				nick_name_public_param		= nick_param;
				online_status_public_param	= os_param;
				profile_public_param		= profile_param;
			}else{
				nick_name_anon_param		= nick_param;
				online_status_anon_param	= os_param;
				profile_anon_param			= profile_param;
				
				network_anon_item = network_item;
			}
			
			network_tab.addTab( network_item );
		}
		
		updateProfiles();
	
			// protocol speed

		final IntParameter	protocol_speed = config.addIntParameter2( "azbuddy.protocolspeed", "azbuddy.protocolspeed", 32 );

		protocol_speed.setMinimumRequiredUserMode( Parameter.MODE_ADVANCED );

		ConnectionManager cman = plugin_interface.getConnectionManager();

		int inbound_limit = protocol_speed.getValue()*1024;

		inbound_limiter 	= cman.createRateLimiter( "buddy_up", inbound_limit );
		outbound_limiter 	= cman.createRateLimiter( "buddy_down", 0 );

		protocol_speed.addListener(
				new ParameterListener()
				{
					@Override
					public void
					parameterChanged(
						Parameter	param )
					{
						inbound_limiter.setRateLimitBytesPerSecond( protocol_speed.getValue()*1024 );
					}
				});

			// chat notifications

		enable_chat_notifications = config.addBooleanParameter2( "azbuddy.enable_chat_notif", "azbuddy.enable_chat_notif", true );

			// default published tags or cats

		cat_pub = config.addStringParameter2( "azbuddy.enable_cat_pub", "azbuddy.enable_cat_pub", "" );

		cat_pub.setGenerateIntermediateEvents( false );

		setPublicTagsOrCategories( cat_pub.getValue(), false );

		final BooleanParameter tracker_enable 		= config.addBooleanParameter2("azbuddy.tracker.enabled", "azbuddy.tracker.enabled", true );
		final BooleanParameter tracker_so_enable 	= config.addBooleanParameter2("azbuddy.tracker.seeding.only.enabled", "azbuddy.tracker.seeding.only.enabled", false );

			// nasty hack but the existing text has a \t prefix that causes UI weirdness but I don't want to change it and
			// end up with missing translations...
		
		tracker_so_enable.setLabelText( MessageText.getString("azbuddy.tracker.seeding.only.enabled").trim());
		tracker_so_enable.setIndent( 1, true );
		
		final BooleanParameter buddies_lan_local 	= config.addBooleanParameter2("azbuddy.tracker.con.lan.local", "azbuddy.tracker.con.lan.local", true );

		buddies_lan_local.addListener(
				new ParameterListener()
				{
					@Override
					public void
					parameterChanged(
						Parameter 	param )
					{
						lan_local_peers = buddies_lan_local.getValue();
					}
				});

		lan_local_peers = buddies_lan_local.getValue();

		final BooleanParameter buddies_fp_enable 	= config.addBooleanParameter2("azbuddy.tracker.fp.enable", "azbuddy.tracker.fp.enable", true );

		buddies_fp_enable.addListener(
				new ParameterListener()
				{
					@Override
					public void
					parameterChanged(
						Parameter 	param )
					{
						fp_enable = buddies_fp_enable.getValue();
					}
				});

		fp_enable = buddies_fp_enable.getValue();
		
		cat_pub.addListener(
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					Parameter 	param )
				{
					setPublicTagsOrCategories( cat_pub.getValue(), false);
				}
			});


		
		config.createGroup(
			"label.friends",
			new Parameter[]{
					classic_enabled_param, network_tab,
					protocol_speed, enable_chat_notifications, cat_pub, tracker_enable, tracker_so_enable,
					buddies_lan_local, buddies_fp_enable
			});

			// decentralised stuff


		beta_enabled_param = config.addBooleanParameter2( "azbuddy.dchat.decentralized.enabled", "azbuddy.dchat.decentralized.enabled", true );


		config.createGroup(
				"azbuddy.dchat.decentralized",
				new Parameter[]{
						beta_enabled_param,
				});


		config.addLabelParameter2("azbuddy.dchat.more.settings");
		

			// config end

		beta_plugin = new BuddyPluginBeta( plugin_interface, this, beta_enabled_param );

		for ( String table_id: TableManager.TABLE_MYTORRENTS_ALL ){
			TableContextMenuItem menu_item =
				plugin_interface.getUIManager().getTableManager().addContextMenuItem(table_id, "azbuddy.contextmenu");

			menu_item.setStyle(TableContextMenuItem.STYLE_MENU);
			menu_item.setHeaderCategory(MenuItem.HEADER_SOCIAL);
			
			MenuItemFillListener	menu_fill_listener =
					new MenuItemFillListener()
					{
						@Override
						public void
						menuWillBeShown(
							MenuItem	menu,
							Object		_target )
						{
							menu.removeAllChildItems();

							if ( !( isClassicEnabled() && isAvailable())){

								menu.setEnabled( false );

								return;
							}

							final List<Torrent>	torrents = new ArrayList<>();

							if ( _target instanceof TableRow ){

								addDownload( torrents, (TableRow)_target );

							}else{

								TableRow[] rows = (TableRow[])_target;

								for ( TableRow row: rows ){

									addDownload( torrents, row );
								}
							}

							if ( torrents.size() == 0 ){

								menu.setEnabled( false );

							}else{

								List<BuddyPluginBuddy> buddies = getBuddies();

								for (int i=0;i<buddies.size();i++){

									final BuddyPluginBuddy	buddy = (BuddyPluginBuddy)buddies.get(i);

									boolean online = buddy.isOnline( true );

									TableContextMenuItem item =
										plugin_interface.getUIManager().getTableManager().addContextMenuItem(
											menu_item,
											"!" + ( buddy.getName() + ( buddy.isPublicNetwork()?"":(" (" + MessageText.getString( "label.anon.medium" ) + ")" ))  ) + (online?"":(" - " +  MessageText.getString( "label.disconnected" ))) + "!");

									item.addMultiListener(
										new MenuItemListener()
										{
											@Override
											public void
											selected(
												MenuItem 	menu,
												Object 		target )
											{
												for ( Torrent torrent: torrents ){

													buddy.getPluginNetwork().getAZ2Handler().sendAZ2Torrent( torrent, buddy );
												}
											}
										});
									
									item.setEnabled( online );
								}

								menu.setEnabled( true );
							}
						}

						protected void
						addDownload(
							List<Torrent>		torrents,
							TableRow			row )
						{
							Object obj = row.getDataSource();

							Download	download;

							if ( obj instanceof Download ){

								download = (Download)obj;

							}else{

								DiskManagerFileInfo file = (DiskManagerFileInfo)obj;

								try{
									download	= file.getDownload();

								}catch( DownloadException e ){

									Debug.printStackTrace(e);

									return;
								}
							}

							Torrent torrent = download.getTorrent();

							if ( torrent != null && !TorrentUtils.isReallyPrivate( PluginCoreUtils.unwrap( torrent ))){

								torrents.add( torrent );
							}
						}
					};
					
			menu_item.addFillListener( menu_fill_listener );
		}
		buddy_tracker = new BuddyPluginTracker( this, tracker_enable, tracker_so_enable );

		plugin_interface.getUIManager().addUIListener(
			new UIManagerListener()
			{
				@Override
				public void
				UIAttached(
					final UIInstance		instance )
				{
					if ( instance.getUIType().equals(UIInstance.UIT_SWT) ){

						try{
							synchronized( swt_ui_waiters ){
								
								swt_ui = (BuddyPluginViewInterface)Class.forName("com.biglybt.plugin.net.buddy.swt.BuddyPluginView").getConstructor(
									new Class[]{ BuddyPlugin.class, UIInstance.class } ).newInstance(
										new Object[]{ BuddyPlugin.this, instance } );

								for ( Runnable r: swt_ui_waiters ){
									
									try{
										
										r.run();
										
									}catch( Throwable e ){
										
										Debug.out( e );
									}
								}
								
								swt_ui_waiters.clear();
							}

						}catch( Throwable e ){
							
							Debug.out( e );
						}
					}

					setupDisablePrompt(instance);
				}

				@Override
				public void
				UIDetached(
					UIInstance		instance )
				{
					if ( instance.getUIType().equals(UIInstance.UIT_SWT) && swt_ui != null ) {
						swt_ui.destroy();
						swt_ui = null;
					}
				}
			});

		final ParameterGroup f_network_anon_item = network_anon_item;
		
		ParameterListener enabled_listener =
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					Parameter	param )
				{
					boolean classic_enabled = classic_enabled_param.getValue();

					nick_name_public_param.setEnabled( classic_enabled );
					online_status_public_param.setEnabled( classic_enabled );
					
					nick_name_anon_param.setEnabled( classic_enabled );
					online_status_anon_param.setEnabled( classic_enabled );

					protocol_speed.setEnabled( classic_enabled );
					enable_chat_notifications.setEnabled( classic_enabled );
					cat_pub.setEnabled( classic_enabled );
					tracker_enable.setEnabled( classic_enabled );

					tracker_so_enable.setEnabled( classic_enabled && tracker_enable.getValue());

					buddies_lan_local.setEnabled( classic_enabled );
					
					buddies_fp_enable.setEnabled( classic_enabled );
					
					network_tab.setEnabled( classic_enabled );
					
					f_network_anon_item.setEnabled( classic_enabled && I2PHelpers.isI2PInstalled());
					
						// only toggle overall state on a real change

					if ( param != null ){

						for ( BuddyPluginNetwork pn: plugin_networks ){
						
							pn.setClassicEnabledInternal( classic_enabled );
						}
						
						fireEnabledStateChanged();
					}
				}
			};


		classic_enabled_param.addListener( enabled_listener );
		beta_enabled_param.addListener( enabled_listener );
		tracker_enable.addListener( enabled_listener );

		for ( BuddyPluginNetwork pn: plugin_networks ){
		
			pn.loadConfig();
		
			pn.registerMessageHandler();
		}

		plugin_interface.addListener(
			new PluginListener()
			{
				@Override
				public void
				initializationComplete()
				{
					enabled_listener.parameterChanged( null );

					final DelayedTask dt = plugin_interface.getUtilities().createDelayedTask(new Runnable()
						{
							@Override
							public void
							run()
							{
								new AEThread2( "BuddyPlugin:init", true )
								{
									@Override
									public void
									run()
									{	
										startup();
										
										beta_plugin.startup();
										
										initialization_complete.set( true );
									}
								}.start();
							}
						});

					dt.queue();
				}

				@Override
				public void
				closedownInitiated()
				{
					// meh, moved this to core listener below as we need to closedown before 
					// i2p plugin so connections aren't torn down before we can tidily close
				}

				@Override
				public void
				closedownComplete()
				{
				}
			});
		
		CoreFactory.getSingleton().addLifecycleListener(
			new CoreLifecycleAdapter(){
				
				@Override
				public boolean 
				syncInvokeRequired()
				{
					return( true );
				}
				
				@Override
				public void 
				stopping(
					Core core)
				{	
					for ( BuddyPluginNetwork pn: plugin_networks ){
					
						pn.saveConfig( true );

						pn.closedown();
					}

					beta_plugin.closedown();
				}
			});
	}
	
	public PluginInterface
	getPluginInterface()
	{
		return( plugin_interface );
	}
	
	public BuddyPluginNetwork[]
	getPluginNetworks()
	{
		return( plugin_networks );
	}
	
	protected int
	getInitialisationState()
	{
		return( initialisation_state );
	}
	
	public boolean
	isInitializationComplete()
	{
		return( initialization_complete.get());
	}

	public String
	getOnlineStatus(
		int		status )
	{
		if ( status >= STATUS_STRINGS.length || status < 0 ){

			status = 0;
		}

		return( STATUS_STRINGS[status] );
	}
	
	public boolean
	isClassicEnabled()
	{
		if (classic_enabled_param == null) {return false;}

		return( classic_enabled_param.getValue());
	}

	public boolean
	setClassicEnabled(
		boolean		enabled,
		boolean		auto )
	{
		if ( classic_enabled_param == null ){

			return( false );
		}
		
		if ( enabled && auto && !classic_enabled_param.getValue()){
			
			PluginConfig config = plugin_interface.getPluginconfig();
			
			if ( config.getPluginBooleanParameter( "classic.auto.enable.done", false )){
				
				return( false );
			}
			
			config.setPluginParameter( "classic.auto.enable.done", true );
		}

		classic_enabled_param.setValue( enabled );
		
		return( enabled );
	}

	public boolean
	isBetaEnabled()
	{
		if ( beta_enabled_param == null ){

			return( false );
		}

		return( beta_enabled_param.getValue());
	}

	public BuddyPluginBeta
	getBeta()
	{
		return( beta_plugin );
	}

	public BuddyPluginTracker
	getTracker()
	{
		return( buddy_tracker );
	}


	public boolean
	getPeersAreLANLocal()
	{
		return( lan_local_peers );
	}
	
	public boolean
	getFPEnabled()
	{
		return( fp_enable );
	}

	public boolean
	isLoggerEnabled()
	{
		return( logger.isEnabled());
	}
	
	protected void
	updateLocale(
		LocaleUtilities	lu )
	{
		for ( int i=0;i<STATUS_STRINGS.length;i++){

			STATUS_STRINGS[i] = lu.getLocalisedMessageText( "azbuddy." + STATUS_KEYS[i] );
		}

		if ( online_status_public_param != null ){

			online_status_public_param.setLabels( STATUS_STRINGS );
		}
		
		if ( online_status_anon_param != null ){

			online_status_anon_param.setLabels( STATUS_STRINGS );
		}
	}

	protected void
	setupDisablePrompt(
			final UIInstance ui)
	{
		if (plugin_interface == null || configEnabledListener != null) {
			return;
		}

		String enabledConfigID = "PluginInfo." + plugin_interface.getPluginID()	+ ".enabled";
		
		configEnabledListener = new com.biglybt.core.config.ParameterListener() {
			@Override
			public void parameterChanged(
					String parameterName) {
				fireEnabledStateChanged();
			}
		};
		
		COConfigurationManager.addParameterListener(enabledConfigID,
				configEnabledListener);
	}
	
	public String
	getNickname(
		boolean public_network )
	{
		return(( public_network?nick_name_public_param:nick_name_anon_param ).getValue());
	}

	public void
	setNickname(
		boolean		public_network,
		String		str )
	{
		if ( public_network ){
		
			nick_name_public_param.setValue( str );
			
		}else{
			
			nick_name_anon_param.setValue( str );
		}
	}

	public int
	getOnlineStatus(
		boolean public_network )
	{
		return( Integer.parseInt(( public_network?online_status_public_param:online_status_anon_param ).getValue()));
	}
	
	public void
	setOnlineStatus(
		boolean	public_network,
		int		status )
	{
		if ( public_network ){
		
			online_status_public_param.setValue( "" + status );
			
		}else{
			
			online_status_anon_param.setValue( "" + status );
		}
	}
	
	public void
	showConfig()
	{
		plugin_interface.getUIManager().showConfigSection("Views.plugins." + VIEW_ID + ".title");
	}

	protected void
 	fireEnabledStateChanged()
 	{
		final boolean classic_enabled 	= !plugin_interface.getPluginState().isDisabled() && isClassicEnabled();
		final boolean beta_enabled 		= !plugin_interface.getPluginState().isDisabled() && isBetaEnabled();

 		List	 listeners_ref = listeners.getList();

 		for (int i=0;i<listeners_ref.size();i++){

 			try{
 				((BuddyPluginListener)listeners_ref.get(i)).enabledStateChanged( classic_enabled, beta_enabled );

 			}catch( Throwable e ){

 				Debug.printStackTrace( e );
 			}
 		}
 	}

	protected void
 	fireUpdated()
 	{
 		for ( BuddyPluginListener listener: listeners ){

 			try{
 				listener.updated();

 			}catch( Throwable e ){

 				Debug.printStackTrace( e );
 			}
 		}
 	}

	public BuddyPluginViewInterface
	getSWTUI()
	{
		return( swt_ui );
	}

	protected void
	addSWTUIWaiter(
		Runnable	r )
	{
		synchronized( swt_ui_waiters ){
			
			if ( swt_ui != null ){
				
				r.run();
				
			}else{
				
				swt_ui_waiters.add( r );
			}
		}
	}
	
	protected void
	addRateLimiters(
		GenericMessageConnection	connection )
	{
		connection.addInboundRateLimiter( inbound_limiter );
		connection.addOutboundRateLimiter( outbound_limiter );
	}
	
	public List<PartialBuddy>
	getPartialBuddies()
	{
		return( buddy_tracker.getPartialBuddies());
	}

	public boolean
	isAvailable()
	{
		try{
			checkAvailable();

			return( true );

		}catch( Throwable e ){

			return( false );
		}
	}

	protected void
	checkAvailable()

		throws BuddyPluginException
	{
		if ( initialisation_state == INIT_UNKNOWN ){

			throw( new BuddyPluginException( "Plugin not yet initialised" ));

		}else if ( initialisation_state == INIT_BAD ){

			throw( new BuddyPluginException( "Plugin unavailable" ));

		}
	}

	public void
	addListener(
		BuddyPluginListener	listener )
	{
		if ( listeners.contains(listener) ){
			return;
		}

		listeners.add( listener );

		if ( initialisation_state != INIT_UNKNOWN ){

			listener.initialised( initialisation_state == INIT_OK );
		}
	}

	public void
	removeListener(
		BuddyPluginListener	listener )
	{
		listeners.remove( listener );
	}

	protected void
   	fireAdded(
   		BuddyPluginBuddy		buddy )
   	{
		if ( buddy.isAuthorised()){

			buddy.setLocalAuthorisedRSSTagsOrCategories( public_tags_or_categories );

			for ( BuddyPluginListener l: listeners ){

	   			try{
	   				l.buddyAdded( buddy );

	   			}catch( Throwable e ){

	   				Debug.printStackTrace( e );
	   			}
	   		}
		}
   	}

	protected void
   	fireRemoved(
   		BuddyPluginBuddy		buddy )
   	{
		if ( buddy.isAuthorised()){
			
			for ( BuddyPluginListener l: listeners ){

	   			try{
	   				l.buddyRemoved( buddy );

	   			}catch( Throwable e ){

	   				Debug.printStackTrace( e );
	   			}
	   		}
		}
   	}

	protected void
   	fireDetailsChanged(
   		BuddyPluginBuddy		buddy )
   	{
		if ( buddy.isAuthorised()){

	   		for ( BuddyPluginListener l: listeners ){

	   			try{
	   				l.buddyChanged( buddy );

	   			}catch( Throwable e ){

	   				Debug.printStackTrace( e );
	   			}
	   		}
		}
   	}
	
	public boolean
	isFullBuddy(
		Peer		peer )
	{
		BuddyPluginTracker tracker = getTracker();
		
		return( tracker.isFullBuddy( peer ));
	}
	
	public boolean
	isPartialBuddy(
		Download	download,
		Peer		peer )
	{
		BuddyPluginTracker tracker = getTracker();
		
		return( tracker.isPartialBuddy( download, peer ));
	}
	
	public void
	setPartialBuddy(
		Download	download,
		Peer		peer,
		boolean		is_partial,
		boolean		manual )
	{
		if ( is_partial ){
			
			if ( !isClassicEnabled()){
				
				setClassicEnabled( true, true );
			}
		}
		
		BuddyPluginTracker tracker = getTracker();
		
		if ( is_partial ){
			
			tracker.addPartialBuddy( download, peer, manual );
			
		}else{
			
			tracker.removePartialBuddy( download, peer, manual );
		}
	}
	
	public BooleanParameter
	getEnableChatNotificationsParameter()
	{
		return( enable_chat_notifications );
	}

	private void
	updateProfiles()
	{
		public_profile_list = updateProfileSupport( profile_public_param );
		
		anon_profile_list 	= updateProfileSupport( profile_anon_param );
	}
	
	private List<String>
	updateProfileSupport(
		StringParameter		param )
	{
		String str = param.getValue();
		
		List<String> profile = new ArrayList<>();
		
		String[] lines = str.split( "\\n" );
		
		for ( String line: lines ){
			
			line = line.trim();
			
			if ( !line.isEmpty()){
				
				String[] bits = line.split( "=", 2 );
				
				if ( bits.length == 2 ){
					
					profile.add( bits[0].trim() + "=" + bits[1].trim());
				}
			}
		}
	
		return( profile );
	}
		
	protected static String
	normaliseCat(
		String		str )
	{
		if ( str == null ){

			return( null );

		}else if ( str.toLowerCase().equals( "all" )){

			return( "All" );

		}else{

			return( str );
		}
	}

	protected static void
	normaliseCats(
		Set<String>	cats )
	{
		if ( cats != null ){

			boolean	all_found = false;

			Iterator<String> it = cats.iterator();

			while( it.hasNext()){

				if ( it.next().toLowerCase().equals( "all" )){

					it.remove();

					all_found = true;
				}
			}

			if ( all_found ){

				cats.add( "All" );
			}
		}
	}

	public boolean
	isPublicTagOrCategory(
		String	cat )
	{
		cat = normaliseCat( cat );

		return( public_tags_or_categories.contains( cat ));
	}

	public void
	addPublicTagOrCategory(
		String	cat )
	{
		cat = normaliseCat( cat );

		Set<String> new_cats = new HashSet( public_tags_or_categories );

		if ( new_cats.add( cat )){

			setPublicTagsOrCategories( new_cats, true );
		}
	}

	public void
	removePublicTagOrCategory(
		String	cat )
	{
		cat = normaliseCat( cat );

		Set<String> new_cats = new HashSet( public_tags_or_categories );

		if ( new_cats.remove( cat )){

			setPublicTagsOrCategories( new_cats, true );
		}
	}

	protected void
	setPublicTagsOrCategories(
		String	str,
		boolean	persist )
	{
		Set<String>	new_pub_cats = new HashSet<>();

		String[]	bits = str.split(",");

		for (String s: bits ){

			s = s.trim();

			if ( bits.length > 0 ){

				new_pub_cats.add( normaliseCat( s ));
			}
		}

		setPublicTagsOrCategories( new_pub_cats, persist );
	}

	protected void
	setPublicTagsOrCategories(
		Set<String>	new_pub_tags_or_cats,
		boolean		persist )
	{
		if ( !public_tags_or_categories.equals( new_pub_tags_or_cats )){

			Set<String> removed = new HashSet<>(public_tags_or_categories);

			removed.removeAll( new_pub_tags_or_cats );

			public_tags_or_categories = new_pub_tags_or_cats;

			if ( persist ){

				String cat_str = "";

				for ( String s: public_tags_or_categories ){

					cat_str += (cat_str.length()==0?"":",") + s;
				}

				cat_pub.setValue( cat_str );
			}

			List<BuddyPluginBuddy> buds = getBuddies();

			for ( BuddyPluginBuddy b: buds ){

				Set<String> local = b.getLocalAuthorisedRSSTagsOrCategories();

				if ( local != null || new_pub_tags_or_cats.size() > 0 ){

					if ( local == null ){

						local = new HashSet<>();

					}else{

							// gotta clone else we're messing with stuff that ain't ours

						local = new HashSet<>(local);
					}

					local.addAll( new_pub_tags_or_cats );

					local.removeAll( removed );

					b.setLocalAuthorisedRSSTagsOrCategories( local );
				}
			}
		}
	}
	
	public void
	addPartialBuddyListener(
		PartialBuddyListener		l )
	{
		pb_listeners.add( l );
	}

	public void
	removePartialBuddyListener(
		PartialBuddyListener		l )
	{
		pb_listeners.remove( l );
	}
	
	public void
	partialBuddyAdded(
		PartialBuddy	pb )
	{
		for ( PartialBuddyListener l: pb_listeners ){
			
			try{
				l.partialBuddyAdded( pb );
				
			}catch( Throwable e ){
				
				Debug.out( e );
			}
		}
	}
	
	public void
	partialBuddyChanged(
		PartialBuddy	pb )
	{
		for ( PartialBuddyListener l: pb_listeners ){
			
			try{
				l.partialBuddyChanged( pb );
				
			}catch( Throwable e ){
				
				Debug.out( e );
			}
		}
	}
	
	public void
	partialBuddyRemoved(
		PartialBuddy	pb )
	{
		for ( PartialBuddyListener l: pb_listeners ){
			
			try{
				l.partialBuddyRemoved( pb );
				
			}catch( Throwable e ){
				
				Debug.out( e );
			}
		}
	}
	
	
	public InputStream
	handleURLProtocol(
		AZPluginConnection			connection,
		String						arg_str )

		throws IPCException
	{
		if ( arg_str.toLowerCase( Locale.US ).startsWith( "chat:" )){

				//azplug:?id=azbuddy&arg=chat%3Aanon%3Fmonkey%2520magic

			if ( !beta_enabled_param.getValue()){

				throw( new IPCException( "Decentralized chat not enabled" ));
			}

			try{
				InputStream result = beta_plugin.handleURI( arg_str, false );

				if ( result != null ){

					return( result );
				}

					// return an empty .vuze file to keep things happy...

				return( new ByteArrayInputStream( VuzeFileHandler.getSingleton().create().exportToBytes() ));

			}catch( Throwable e ){

				throw( new IPCException( e ));
			}

		}else{

			String[]	args = arg_str.split( "&" );

			String		pk 				= null;
			String		category_or_tag	= "All";
			byte[]		hash			= null;

			for (String arg: args ){

				String[]	bits = arg.split( "=" );

				String	lhs = bits[0];
				String	rhs	= UrlUtils.decode( bits[1] );

				if ( lhs.equals( "pk" )){

					pk		= rhs;

				}else if ( lhs.equals( "cat" )){

					category_or_tag = rhs;

				}else if ( lhs.equals( "hash" )){

					hash	= Base32.decode(rhs);
				}
			}

			if ( pk == null ){

				throw( new IPCException( "Public key missing from '" + arg_str + "'" ));
			}

			BuddyPluginBuddy	buddy	= getBuddyFromPublicKey( pk );

			if ( buddy == null ){

				throw( new IPCException( "Buddy with public key '" + pk + "' not found" ));
			}

			if ( hash == null ){

				return( buddy.getPluginNetwork().handleUPRSS( connection, buddy, category_or_tag ));

			}else{

				return( buddy.getPluginNetwork().handleUPTorrent( connection, buddy, category_or_tag, hash ));
			}
		}
	}
	
	
	
	
	protected long
	getAddedTime(
		Download	download )
	{
		DownloadManager	core_download = PluginCoreUtils.unwrap( download );

		return( core_download.getDownloadState().getLongParameter(DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME));
	}

	protected String
	escape(
		String	str )
	{
		return( XUXmlWriter.escapeXML(str));
	}
	
	public FeedDetails
	getRSS(
		BuddyPluginBuddy		buddy,
		String					tag_or_category,
		String					if_mod )

		throws BuddyPluginException
	{
		if ( !buddy.isLocalRSSTagOrCategoryAuthorised( tag_or_category )){

			throw( new BuddyPluginException( "Unauthorised tag/category '" + tag_or_category + "'" ));
		}

		buddy.localRSSTagOrCategoryRead( tag_or_category );

		Download[] downloads = plugin_interface.getDownloadManager().getDownloads();

		List<Download>	selected_dls = new ArrayList<>();

		long	fingerprint	= 0;

		for (int i=0;i<downloads.length;i++){

			Download download = downloads[i];

			Torrent torrent = download.getTorrent();

			if ( torrent == null ){

				continue;
			}

			boolean	match = tag_or_category.equalsIgnoreCase( "all" );

			if ( !match ){

				String dl_cat = download.getAttribute( ta_category );

				match = dl_cat != null && dl_cat.equals( tag_or_category );
			}

			if ( !match ){

				try{
					List<Tag> tags = TagManagerFactory.getTagManager().getTagsForTaggable( TagType.TT_DOWNLOAD_MANUAL, PluginCoreUtils.unwrap( download ));

					for ( Tag tag: tags ){

						if ( tag.getTagName( true ).equals( tag_or_category )){

							match = true;

							break;
						}
					}
				}catch( Throwable e ){
				}
			}

			if ( match ){

				if ( !TorrentUtils.isReallyPrivate( PluginCoreUtils.unwrap( torrent ))){

					selected_dls.add( download );

					byte[] hash = torrent.getHash();

					int	num = (hash[0]<<24)&0xff000000 | (hash[1] << 16)&0x00ff0000 | (hash[2] << 8)&0x0000ff00 | hash[3]&0x000000ff;

					fingerprint += num;
				}
			}
		}

		PluginConfig pc = plugin_interface.getPluginconfig();

		String	feed_finger_key = "feed_finger.category." + tag_or_category;
		String	feed_date_key 	= "feed_date.category." + tag_or_category;

		long	existing_fingerprint 	= pc.getPluginLongParameter( feed_finger_key, 0 );
		long	feed_date 				= pc.getPluginLongParameter( feed_date_key, 0 );

		long	now = SystemTime.getCurrentTime();

		if ( existing_fingerprint == fingerprint ){

				// update every now and then to pick up new peer/seed values

			if ( selected_dls.size() > 0 ){

				if ( 	now < feed_date ||
						now - feed_date > FEED_UPDATE_MIN_MILLIS ){

					feed_date = now;

					pc.setPluginParameter( feed_date_key, feed_date );
				}
			}
		}else{

			pc.setPluginParameter( feed_finger_key, fingerprint );


				// ensure feed date goes up

			if ( now <= feed_date ){

				feed_date++;

			}else{

				feed_date = now;
			}

			pc.setPluginParameter( feed_date_key, feed_date );
		}

		String last_modified = TimeFormatter.getHTTPDate( feed_date );

		if ( if_mod != null && if_mod.equals( last_modified )){

			return( new FeedDetails( new byte[0], last_modified ));
		}

		ByteArrayOutputStream	os = new ByteArrayOutputStream();

		try{
			PrintWriter pw = new PrintWriter(new OutputStreamWriter( os, "UTF-8" ));

			pw.println( "<?xml version=\"1.0\" encoding=\"utf-8\"?>" );

			pw.println( "<rss version=\"2.0\" " + Constants.XMLNS_VUZE + ">" );

			pw.println( "<channel>" );

			pw.println( "<title>" + escape( tag_or_category ) + "</title>" );

			Collections.sort(
				selected_dls,
				new Comparator<Download>()
				{
					@Override
					public int
					compare(
						Download d1,
						Download d2)
					{
						long	added1 = getAddedTime( d1 )/1000;
						long	added2 = getAddedTime( d2 )/1000;

						return((int)(added2 - added1 ));
					}
				});


			pw.println(	"<pubDate>" + last_modified + "</pubDate>" );

			for (int i=0;i<selected_dls.size();i++){

				Download download = (Download)selected_dls.get( i );

				DownloadManager	core_download = PluginCoreUtils.unwrap( download );

				Torrent torrent = download.getTorrent();

				String	hash_str = Base32.encode( torrent.getHash());

				pw.println( "<item>" );

				pw.println( "<title>" + escape( download.getName()) + "</title>" );

				pw.println( "<guid>" + hash_str + "</guid>" );

				long added = core_download.getDownloadState().getLongParameter(DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME);

				pw.println(	"<pubDate>" + TimeFormatter.getHTTPDate( added ) + "</pubDate>" );

				pw.println(	"<vuze:size>" + torrent.getSize()+ "</vuze:size>" );
				pw.println(	"<vuze:assethash>" + hash_str + "</vuze:assethash>" );

				String url = "azplug:?id=azbuddy&name=Friends&arg=";

				String arg = "pk=" + buddy.getPluginNetwork().getPublicKey() + "&cat=" + tag_or_category + "&hash=" + Base32.encode(torrent.getHash());

				url += URLEncoder.encode( arg, "UTF-8" );

				pw.println( "<vuze:downloadurl>" + escape( url ) + "</vuze:downloadurl>" );

				DownloadScrapeResult scrape = download.getLastScrapeResult();

				if ( scrape != null && scrape.getResponseType() == DownloadScrapeResult.RT_SUCCESS ){

					pw.println(	"<vuze:seeds>" + scrape.getSeedCount() + "</vuze:seeds>" );
					pw.println(	"<vuze:peers>" + scrape.getNonSeedCount() + "</vuze:peers>" );
				}

				pw.println( "</item>" );
			}

			pw.println( "</channel>" );

			pw.println( "</rss>" );

			pw.flush();

			return( new FeedDetails( os.toByteArray(), last_modified ));

		}catch( IOException e ){

			throw( new BuddyPluginException( "", e ));
		}
	}

	public byte[]
	getRSSTorrent(
		BuddyPluginBuddy		buddy,
		String					category,
		byte[]					hash )

		throws BuddyPluginException
	{
		if ( !buddy.isLocalRSSTagOrCategoryAuthorised( category )){

			throw( new BuddyPluginException( "Unauthorised category '" + category + "'" ));
		}

		try{
			Download download = plugin_interface.getDownloadManager().getDownload( hash );

			if ( download != null ){

				Torrent	torrent = download.getTorrent();

				if ( torrent != null ){

					String dl_cat = download.getAttribute( ta_category );

					if ( 	category.equalsIgnoreCase( "all" ) ||
							( dl_cat != null && dl_cat.equals( category ))){

						if ( !TorrentUtils.isReallyPrivate( PluginCoreUtils.unwrap( torrent ))){

							torrent = torrent.removeAdditionalProperties();

							return( torrent.writeToBEncodedData());
						}
					}
				}
			}
		}catch( Throwable e ){

			throw( new BuddyPluginException( "getTorrent failed", e ));
		}

		throw( new BuddyPluginException( "Not found" ));
	}

	public void
	createChat(
		BuddyPluginBuddy[]	buddies )
	{
		for ( BuddyPluginNetwork pn: plugin_networks ){
			
			List<BuddyPluginBuddy> hits = new ArrayList<>();
			
			for ( BuddyPluginBuddy buddy: buddies ){
				
				if ( buddy.getPluginNetwork() == pn ){
					
					hits.add( buddy );
				}
			}
			
			if ( !hits.isEmpty()){
				
				pn.getAZ2Handler().createChat( hits.toArray( new BuddyPluginBuddy[0] ));
			}
		}
	}
	
	private BuddyPluginNetwork
	getPluginNetwork(
		boolean	pub )
	{
		String target = pub?AENetworkClassifier.AT_PUBLIC:AENetworkClassifier.AT_I2P;
		
		for ( BuddyPluginNetwork net: plugin_networks ){
			
			if ( net.getTargetNetwork() == target ){
				
				return( net );
			}
		}
		
		return( null );
	}
		// STUFF THAT NEEDS FIXING FOR MULTI-NETWORK SUPPORT
	
	private void
	startup()
	{
		List<BuddyPluginNetwork>	pn_ok = new ArrayList<>();
		
		for ( BuddyPluginNetwork pn: plugin_networks ){
			
			boolean is_pub = pn.isPublicNetwork();
			
			String nick = getNickname( is_pub );

			int status = getOnlineStatus( is_pub );

			boolean enabled = classic_enabled_param.getValue();
			
			if ( pn.startup( nick, status, enabled )){
				
				pn_ok.add( pn );
			}
		}
		
		if ( pn_ok.size() != plugin_networks.length ){
			
			plugin_networks = pn_ok.toArray( new BuddyPluginNetwork[0]);
		}
		
		boolean ok = !pn_ok.isEmpty();
		
		if ( ok ){

			initialisation_state = INIT_OK;

			for ( BuddyPluginNetwork pn: plugin_networks ){
				
				pn.persistentDispatchInit();
				
				pn.reconnect();
			}
			
			buddy_tracker.initialise();
			
			plugin_interface.getUtilities().createTimer( "Buddy checker" ).addPeriodicEvent(
					TIMER_PERIOD,
					new UTTimerEventPerformer()
					{
						int	tick_count;
						
						@Override
						public void
						perform(
							UTTimerEvent event )
						{
							tick_count++;
							
							if ( !isClassicEnabled()){

								return;
							}

							buddy_tracker.tick( tick_count );
						}
					});
		}else{

			initialisation_state = INIT_BAD;
		}

		List	 listeners_ref = listeners.getList();

		for (int i=0;i<listeners_ref.size();i++){

			try{
				((BuddyPluginListener)listeners_ref.get(i)).initialised( ok );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}
	
	private void
	updateNickName(
		boolean		public_network,
		String		nick )
	{
		BuddyPluginNetwork	net = getPluginNetwork( public_network );
		
		if ( net != null ){
		
			net.updateNickName( nick );
		}
	}
	
	private void
	updateOnlineStatus(
		boolean		public_network,
		int			status )
	{
		BuddyPluginNetwork	net = getPluginNetwork( public_network );
		
		if ( net != null ){
		
			net.updateOnlineStatus( status );
		}
	}
	
	public String
	getPublicKey(
		boolean		public_network )
	{
		BuddyPluginNetwork	net = getPluginNetwork( public_network );
		
		if ( net != null ){
		
			return( net.getPublicKey());
		}
		
		return( null );
	}
	
	public List<String>
	getProfileInfo(
		boolean		public_network )
	{
		if ( public_network ){
		
			return( public_profile_list  );
			
		}else{
			
			return( anon_profile_list  );
		}
	}

	public byte[]
	sign(
		boolean		public_network,
		byte[]		payload )
	
		throws BuddyPluginException
	{
		BuddyPluginNetwork	net = getPluginNetwork( public_network );
		
		if ( net != null ){
		
			return( net.sign(payload));
		}
		
		throw( new BuddyPluginException( "Invalid net" ));
	}
	
	public boolean
	verify(
		boolean				public_network,
		String				pk,
		byte[]				payload,
		byte[]				signature )

		throws BuddyPluginException
	{
		BuddyPluginNetwork	net = getPluginNetwork( public_network );
		
		if ( net != null ){

			return( net.verify(pk, payload, signature));
		}
		
		throw( new BuddyPluginException( "Invalid net" ));
	}
	
	public boolean
	verifyPublicKey(
		boolean		public_network,
		String		key )
	{
		BuddyPluginNetwork	net = getPluginNetwork( public_network );
		
		if ( net != null ){
			
			return( net.verifyPublicKey(key));
		}
		
		return( false );
	}
	
	public BuddyPluginBuddy
	addBuddy(
		boolean		public_network,
		String		key,
		int			subsystem )
	{
		BuddyPluginNetwork	net = getPluginNetwork( public_network );
		
		if ( net != null ){
			
			return( net.addBuddy(key, subsystem));
		}
		
		return( null );
	}
	
	public BuddyPluginBuddy
	peekBuddy(
		boolean		public_network,
		String		key )
	{
		BuddyPluginNetwork	net = getPluginNetwork( public_network );
		
		if ( net != null ){
		
			return( net.peekBuddy(key));
		}
		
		return( null );
	}
	
	public List<BuddyPluginBuddy>
	getBuddies()
	{
		if ( plugin_networks.length == 1 ){
			
			return( plugin_networks[0].getBuddies());
			
		}else{
			
			List<BuddyPluginBuddy>	result = new ArrayList<>();
			
			for ( BuddyPluginNetwork pn: plugin_networks ){
				
				result.addAll( pn.getBuddies());
			}
			
			return( result );
		}
	}
	
	public BuddyPluginBuddy
	getBuddyFromPublicKey(
		String		key )
	{
		for ( BuddyPluginNetwork pn: plugin_networks ){
			
			BuddyPluginBuddy buddy = pn.getBuddyFromPublicKey(key);
			
			if ( buddy != null ){
				
				return( buddy );
			}
		}
		
		return( null );
	}
	
		// ******
	
	public void
	logMessage(
		BuddyPluginBuddy	buddy,
		String				str,
		Throwable			e )
	{
		logMessage( buddy, str + ": " + Debug.getNestedExceptionMessage(e), true );
	}

	public void
	logMessage(
		BuddyPluginBuddy	buddy,
		String				str )
	{
		logMessage( buddy, str, false );
	}

	public void
	logMessage(
		BuddyPluginBuddy	buddy,
		String				str,
		boolean				is_error )
	{
		if ( buddy != null && ( buddy.isTransient() || ! buddy.isAuthorised())){
			
			return;
		}
		
		log( buddy, str );

		Iterator it = listeners.iterator();

		while( it.hasNext()){

			try{
				((BuddyPluginListener)it.next()).messageLogged( str, is_error );

			}catch( Throwable e ){

				Debug.printStackTrace( e );
			}
		}
	}

	public void
	log(
		BuddyPluginBuddy	buddy,
		String				str )
	{
		if ( buddy != null && ( buddy.isTransient() || ! buddy.isAuthorised())){
			
			return;
		}

		logger.log( str );
	}

	public void
	log(
		BuddyPluginBuddy	buddy,
		String				str,
		Throwable			e )
	{
		if ( buddy != null && ( buddy.isTransient() || ! buddy.isAuthorised())){
			
			return;
		}
		
		logger.log( str + ": " + Debug.getNestedExceptionMessageAndStack( e ));
	}
	
	public interface
	CryptoResult
	{
		public byte[]
		getChallenge();

		public byte[]
		getPayload();
	}
	
	protected static class
	FeedDetails
	{
		private byte[]		contents;
		private String		last_modified;

		protected
		FeedDetails(
			byte[]		_contents,
			String		_last_modified )
		{
			contents		= _contents;
			last_modified	= _last_modified;
		}

		protected byte[]
		getContent()
		{
			return( contents );
		}

		protected String
		getLastModified()
		{
			return( last_modified );
		}
	}
}
