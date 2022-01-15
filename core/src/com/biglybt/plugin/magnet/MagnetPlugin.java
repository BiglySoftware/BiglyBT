/*
 * Created on 03-Mar-2005
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

package com.biglybt.plugin.magnet;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.networkmanager.impl.tcp.TCPNetworkManager;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagManager;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.tag.TagType;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentAnnounceURLGroup;
import com.biglybt.core.torrent.TOTorrentAnnounceURLSet;
import com.biglybt.core.torrent.TOTorrentException;
import com.biglybt.core.torrent.TOTorrentFactory;
import com.biglybt.core.torrent.impl.TorrentOpenOptions;
import com.biglybt.core.tracker.client.TRTrackerAnnouncerFactory;
import com.biglybt.core.tracker.client.TRTrackerAnnouncerResponsePeer;
import com.biglybt.core.util.*;
import com.biglybt.net.magneturi.MagnetURIHandler;
import com.biglybt.net.magneturi.MagnetURIHandlerException;
import com.biglybt.net.magneturi.MagnetURIHandlerListener;
import com.biglybt.net.magneturi.MagnetURIHandlerProgressListener;
import com.biglybt.pif.Plugin;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.PluginListener;
import com.biglybt.pif.ddb.*;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadException;
import com.biglybt.pif.sharing.ShareException;
import com.biglybt.pif.sharing.ShareManager;
import com.biglybt.pif.sharing.ShareResource;
import com.biglybt.pif.sharing.ShareResourceDir;
import com.biglybt.pif.sharing.ShareResourceFile;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.torrent.TorrentAttribute;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.UIManagerListener;
import com.biglybt.pif.ui.config.BooleanParameter;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.config.IntParameter;
import com.biglybt.pif.ui.config.Parameter;
import com.biglybt.pif.ui.config.StringListParameter;
import com.biglybt.pif.ui.config.StringParameter;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pif.ui.model.BasicPluginConfigModel;
import com.biglybt.pif.ui.tables.TableContextMenuItem;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.pif.ui.tables.TableRow;
import com.biglybt.pif.utils.LocaleListener;
import com.biglybt.pif.utils.LocaleUtilities;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.util.MapUtils;

/**
 * @author parg
 *
 */

public class
MagnetPlugin
	implements Plugin
{
	public static final int	FL_NONE					= 0x00000000;
	public static final int	FL_DISABLE_MD_LOOKUP	= 0x00000001;
	public static final int	FL_NO_MD_LOOKUP_DELAY	= 0x00000002;

	private static final int	MD_LOOKUP_DELAY_SECS_DEFAULT		= 0;

	private static final String[] MD_EXTRA_TRACKERS = { "udp://tracker.opentrackr.org:1337/announce" }; 

	private static final String	PLUGIN_NAME				= "Magnet URI Handler";
	private static final String PLUGIN_CONFIGSECTION_ID = "plugins.magnetplugin";

	public static final String[] SOURCE_VALUES 	= { "0", "1", "2" };

	public static final String[] SOURCE_KEYS = {
		"never", "shares", "always"
	};
	
	public static final String[] SOURCE_STRINGS = new String[ SOURCE_KEYS.length ];

	protected static final Object	DM_TAG_CACHE 		= new Object();
	protected static final Object	DM_CATEGORY_CACHE 	= new Object();
	protected static final Object	DM_DN_CHANGED	 	= new Object();
		
	private static DistributedDatabase[]	db_holder	= {null};
	private static AESemaphore				db_waiter	= new AESemaphore( "Grab DDB" );

	
	private PluginInterface		plugin_interface;

	private CopyOnWriteList		listeners = new CopyOnWriteList();

	private boolean			first_download	= true;

	private static final int	PLUGIN_DOWNLOAD_TIMEOUT_SECS_DEFAULT 	= 0;	// increased to infinite; (needs to be fairly large as non-public downloads can take a while...)

	// private BooleanParameter 	secondary_lookup; removed
	private BooleanParameter 	md_lookup;
	private IntParameter	 	md_lookup_delay;
	private StringParameter 	md_extra_trackers;
	private IntParameter	 	timeout_param;
	private StringListParameter	sources_param;
	private IntParameter	 	sources_extra_param;
	private BooleanParameter	magnet_recovery;
	private IntParameter	 	magnet_recovery_concurrency;

	private Map<String,BooleanParameter> net_params = new HashMap<>();


	public static void
	load(
		PluginInterface		plugin_interface )
	{
		plugin_interface.getPluginProperties().setProperty( "plugin.version", 	"1.0" );
		plugin_interface.getPluginProperties().setProperty( "plugin.name", PLUGIN_NAME );
	}

	@Override
	public void
	initialize(
		PluginInterface _plugin_interface )
	{
		plugin_interface	= _plugin_interface;

		new AEThread2( "Grab DDB" ){
			@Override
			public void run()
			{
				try{
					DistributedDatabase db = plugin_interface.getDistributedDatabase();
					
					synchronized( db_holder ){
						
						db_holder[0] = db;
					}
				}finally{
					db_waiter.releaseForever();
				}
			}
		}.start();
		
		MagnetURIHandler uri_handler = MagnetURIHandler.getSingleton();

		final LocaleUtilities lu = plugin_interface.getUtilities().getLocaleUtilities();

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
		
		BasicPluginConfigModel	config =
			plugin_interface.getUIManager().createBasicPluginConfigModel( ConfigSection.SECTION_PLUGINS,
					PLUGIN_CONFIGSECTION_ID);

		config.addInfoParameter2("MagnetPlugin.current.port", String.valueOf( uri_handler.getPort()));

		md_lookup 			= config.addBooleanParameter2( "MagnetPlugin.use.md.download", "MagnetPlugin.use.md.download", true );
		md_lookup_delay		= config.addIntParameter2( "MagnetPlugin.use.md.download.delay", "MagnetPlugin.use.md.download.delay", MD_LOOKUP_DELAY_SECS_DEFAULT );

		String et_default = "";
		
		for ( String etd: MD_EXTRA_TRACKERS ){
			et_default += (et_default.isEmpty()?"":"\n") + etd;
		}
		
		md_extra_trackers = config.addStringParameter2( "MagnetPlugin.md.extra.trackers", "MagnetPlugin.md.extra.trackers", et_default);
		
		md_extra_trackers.setMultiLine( 3 );
		
		md_lookup.addEnabledOnSelection( md_lookup_delay );
		md_lookup.addEnabledOnSelection( md_extra_trackers );

		timeout_param		= config.addIntParameter2( "MagnetPlugin.timeout.secs", "MagnetPlugin.timeout.secs", PLUGIN_DOWNLOAD_TIMEOUT_SECS_DEFAULT );

		sources_param 		= config.addStringListParameter2( "MagnetPlugin.add.sources", "MagnetPlugin.add.sources", SOURCE_VALUES, SOURCE_STRINGS, SOURCE_VALUES[1] );
		
		sources_extra_param	= config.addIntParameter2( "MagnetPlugin.add.sources.extra", "MagnetPlugin.add.sources.extra", 0 );
				
		magnet_recovery		= config.addBooleanParameter2( "MagnetPlugin.recover.magnets", "MagnetPlugin.recover.magnets", true );
		
		magnet_recovery_concurrency	= config.addIntParameter2( "MagnetPlugin.recover.magnets.conc", "MagnetPlugin.recover.magnets.conc", 32, 8, 512 );

		magnet_recovery_concurrency.setIndent( 1, true );
		
		magnet_recovery.addEnabledOnSelection( magnet_recovery_concurrency );

		BooleanParameter rename = config.addBooleanParameter2( "MagnetPlugin.rename.using.dn", "MagnetPlugin.rename.using.dn", false );
		
		BooleanParameter rename_ext = config.addBooleanParameter2( "MagnetPlugin.rename.using.dn.only.with.ext", "MagnetPlugin.rename.using.dn.only.with.ext", false );	
		
		rename_ext.setIndent( 1, true );
		
		rename.addEnabledOnSelection( rename_ext );
		
		Parameter[] nps = new Parameter[ AENetworkClassifier.AT_NETWORKS.length ];

		for ( int i=0; i<nps.length; i++ ){

			String nn = AENetworkClassifier.AT_NETWORKS[i];

			String config_name = "Network Selection Default." + nn;

			String msg_text = "ConfigView.section.connection.networks." + nn;

			final BooleanParameter param 	=
				config.addBooleanParameter2(
						config_name,
						msg_text,
						COConfigurationManager.getBooleanParameter( config_name ));

			COConfigurationManager.addParameterListener(
					config_name,
					new com.biglybt.core.config.ParameterListener()
					{
						@Override
						public void
						parameterChanged(
							String name )
						{
							param.setDefaultValue( COConfigurationManager.getBooleanParameter( name ));
						}
					});

			nps[i] = param;

			net_params.put( nn, param );
		}

		config.createGroup( "label.default.nets", nps );

		MenuItemListener	listener =
			new MenuItemListener()
			{
				@Override
				public void
				selected(
					MenuItem		_menu,
					Object			_target )
				{
					TableRow[] rows = (TableRow[])_target;

					String cb_all_data = "";

					for ( TableRow row: rows ){
						Torrent torrent;
						String name;
						Object ds = row.getDataSource();

						Download 		download 	= null;
						ShareResource	share 		= null;
						
						if (ds instanceof ShareResourceFile) {
							ShareResourceFile sf = (ShareResourceFile)ds;
							try {
								torrent = sf.getItem().getTorrent();
							} catch (ShareException e) {
								continue;
							}
							name = sf.getName();
							
							share = sf;
							
						}else if (ds instanceof ShareResourceDir) {
							ShareResourceDir sd = (ShareResourceDir)ds;
								try {
									torrent = sd.getItem().getTorrent();
								} catch (ShareException e) {
									continue;
								}
								name = sd.getName();
								
								share = sd;
						} else if (ds instanceof Download) {
							download = (Download)ds;
							torrent = download.getTorrent();
							name = download.getName();
						} else {
							continue;
						}

						boolean	is_share = false;

						Set<String>	networks = new HashSet<>();
						
						if ( share != null ){
							
							is_share = true;
							
							Map<String,String>	properties  = share.getProperties();
							
							if ( properties != null ){
								
								String nets = properties.get( ShareManager.PR_NETWORKS );
		
								if ( nets != null ){
		
									String[] bits = nets.split( "," );
		
									for ( String bit: bits ){
		
										bit = AENetworkClassifier.internalise( bit.trim());
		
										if ( bit != null ){
		
											networks.add( bit );
										}
									}
								}
							}
						}
												
						if ( download != null ){
							
							TorrentAttribute ta = plugin_interface.getTorrentManager().getAttribute( TorrentAttribute.TA_NETWORKS );
							
							String[]	nets = download.getListAttribute( ta );
							
							networks.addAll( Arrays.asList( nets ));
							
							try{
								byte[] hash = download.getTorrentHash();
								
								if ( plugin_interface.getShareManager().lookupShare( hash ) != null ){
									
									is_share = true;
								}								
							}catch( Throwable e ){								
							}
						}
												
						String cb_data = download==null?UrlUtils.getMagnetURI( name, torrent ):UrlUtils.getMagnetURI( download);

						if ( download != null ){

							List<Tag> tags = TagManagerFactory.getTagManager().getTagsForTaggable( TagType.TT_DOWNLOAD_MANUAL, PluginCoreUtils.unwrap( download ));

							for ( Tag tag: tags ){

								if ( tag.isPublic()){

									if ( !tag.isTagAuto()[0]){
									
										cb_data += "&tag=" + UrlUtils.encode( tag.getTagName( true ));
									}
								}
							}
						}

						String sources = sources_param.getValue();

						boolean add_sources = sources.equals( "2" ) || ( sources.equals( "1" ) && is_share );

						if ( add_sources ){
							
							if ( networks.isEmpty()){
								
								for ( String net: AENetworkClassifier.AT_NETWORKS ){
									
									if ( isNetworkEnabled( net )){
										
										networks.add( net );
									}
								}
							}
							
							if ( networks.contains( AENetworkClassifier.AT_PUBLIC ) && !cb_data.contains( "xsource=" )){
								
								DownloadManager dm = download==null?null:PluginCoreUtils.unwrap( download );
								
								InetAddress ip = NetworkAdmin.getSingleton().getDefaultPublicAddress();

								InetAddress ip_v6 = NetworkAdmin.getSingleton().getDefaultPublicAddressV6();
								
								int port = dm==null?TCPNetworkManager.getSingleton().getDefaultTCPListeningPortNumber():dm.getTCPListeningPortNumber();

								if ( ip != null && port > 0 ){
									
									cb_data += "&xsource=" +  UrlUtils.encode( UrlUtils.getURLForm( ip,  port ));
								}
								
								if ( ip_v6 != null && port > 0 ){
									
									cb_data += "&xsource=" +  UrlUtils.encode( UrlUtils.getURLForm( ip_v6,  port ));
								}
																	
								int	extra = sources_extra_param.getValue();
									
								if ( extra > 0 ){
									
									if ( download == null ){
										
										if ( torrent != null ){
											
											download = plugin_interface.getDownloadManager().getDownload( torrent );
										}
									}
									
									if ( download != null ){
										
										Set<String>	added = new HashSet<>();
																				
										PEPeerManager pm = dm.getPeerManager();
										
										if ( pm != null ){
											
											List<PEPeer> peers = pm.getPeers();
											
											for ( PEPeer peer: peers ){
												
												String peer_ip = peer.getIp();
												
												if ( AENetworkClassifier.categoriseAddress( peer_ip ) == AENetworkClassifier.AT_PUBLIC ){
												
													int peer_port = peer.getTCPListenPort();
													
													if ( peer_port > 0 ){
																													
														cb_data += "&xsource=" +  UrlUtils.encode( UrlUtils.getURLForm( peer_ip, peer_port ));
														
														added.add( peer_ip );
														
														extra--;
														
														if ( extra == 0 ){
															
															break;
														}
													}
												}
											}
										}
										
										if ( extra > 0 ){
											
											Map response_cache = dm.getDownloadState().getTrackerResponseCache();
										
											if ( response_cache != null ){
											
												List<TRTrackerAnnouncerResponsePeer> peers = TRTrackerAnnouncerFactory.getCachedPeers( response_cache );
												
												for ( TRTrackerAnnouncerResponsePeer peer: peers ){
													
													String peer_ip = peer.getAddress();
													
													if ( AENetworkClassifier.categoriseAddress( peer_ip ) == AENetworkClassifier.AT_PUBLIC ){
													
														if ( !added.contains( peer_ip )){
															
															int peer_port = peer.getPort();
															
															if ( peer_port > 0 ){
																															
																cb_data += "&xsource=" + UrlUtils.encode( UrlUtils.getURLForm( peer_ip, peer_port ));
																
																added.add( peer_ip );
																
																extra--;
																
																if ( extra == 0 ){
																	
																	break;
																}
															}
														}
													}
												}
											}
										}
									}
								}
							}
						}
						
						// removed this as well - nothing wrong with allowing magnet copy
						// for private torrents - they still can't be tracked if you don't
						// have permission


						/*if ( torrent.isPrivate()){

							cb_data = getMessageText( "private_torrent" );

						}else if ( torrent.isDecentralised()){
						*/
							// ok

							/* relaxed this as we allow such torrents to be downloaded via magnet links
							 * (as opposed to tracked in the DHT)

						}else if ( torrent.isDecentralisedBackupEnabled()){

							TorrentAttribute ta_peer_sources 	= plugin_interface.getTorrentManager().getAttribute( TorrentAttribute.TA_PEER_SOURCES );

							String[]	sources = download.getListAttribute( ta_peer_sources );

							boolean	ok = false;

							for (int i=0;i<sources.length;i++){

								if ( sources[i].equalsIgnoreCase( "DHT")){

									ok	= true;

									break;
								}
							}

							if ( !ok ){

								cb_data = getMessageText( "decentral_disabled" );
							}
						}else{

							cb_data = getMessageText( "decentral_backup_disabled" );
							*/
						// }

						// System.out.println( "MagnetPlugin: export = " + url );

						cb_all_data += (cb_all_data.length()==0?"":"\n") + cb_data;
					}

					try{
						plugin_interface.getUIManager().copyToClipBoard( cb_all_data );

					}catch( Throwable  e ){

						e.printStackTrace();
					}
				}
			};

		List<TableContextMenuItem>	menus = new ArrayList<>();
		
		for ( String table: TableManager.TABLE_MYTORRENTS_ALL ){
				
			TableContextMenuItem menu = plugin_interface.getUIManager().getTableManager().addContextMenuItem(table, "MagnetPlugin.contextmenu.exporturi" );

			menu.addMultiListener( listener );
			menu.setHeaderCategory(MenuItem.HEADER_SOCIAL);
			
			menus.add( menu );
		}

		uri_handler.addListener(
			new MagnetURIHandlerListener()
			{
				@Override
				public byte[]
				badge()
				{
					InputStream is = getClass().getClassLoader().getResourceAsStream("com/biglybt/plugin/magnet/Magnet.gif");

					if ( is == null ){

						return( null );
					}

					try{
						ByteArrayOutputStream	baos = new ByteArrayOutputStream();

						try{
							byte[]	buffer = new byte[8192];

							while( true ){

								int	len = is.read( buffer );

								if ( len <= 0 ){

									break;
								}

								baos.write( buffer, 0, len );
							}
						}finally{

							is.close();
						}

						return( baos.toByteArray());

					}catch( Throwable e ){

						Debug.printStackTrace(e);

						return( null );
					}
				}

				@Override
				public byte[]
				download(
					MagnetURIHandlerProgressListener 	muh_listener,
					byte[]								hash,
					String								args,
					InetSocketAddress[]					sources,
					long								timeout )

					throws MagnetURIHandlerException
				{
						// see if we've already got it!

					try{
						Download	dl = plugin_interface.getDownloadManager().getDownload( hash );

						if ( dl != null ){

								// might just be an existing metadata download
							
							com.biglybt.core.download.DownloadManager		core_dm = PluginCoreUtils.unwrap( dl );
							
							if ( !core_dm.getDownloadState().getFlag( DownloadManagerState.FLAG_METADATA_DOWNLOAD )){
								
								Torrent	torrent = dl.getTorrent();
	
								if ( torrent != null ){
	
									byte[] torrent_data = torrent.writeToBEncodedData();
	
									torrent_data = addTrackersAndWebSeedsEtc( torrent_data, args, new HashSet<String>(), Collections.emptyList(), Collections.emptyMap());
	
									return( torrent_data);
								}
								}
						}
					}catch( Throwable e ){

						Debug.printStackTrace(e);
					}

					Object[] result = { null };
					
					AESemaphore sem = new AESemaphore( "dlwait" );
					
					DownloadAsyncListener dl_listener =
						new DownloadAsyncListener(){
							
							@Override
							public void 
							failed(
								MagnetURIHandlerException error )
							{
								synchronized( result ){
									result[0] = error;
								}
								sem.release();
							}
							
							@Override
							public void 
							complete(
								byte[] torrent_data ) 
							{
								synchronized( result ){
									result[0] = torrent_data;
								}
								sem.release();
							}
						};
						
					recoverableDownload( muh_listener, hash, args, sources, Collections.emptyList(), Collections.emptyMap(), timeout, SystemTime.getCurrentTime(), false, dl_listener );
		
					sem.reserve();
					
					synchronized( result ){
						
						Object r = result[0];
						
						if ( r instanceof MagnetURIHandlerException ){
							
							throw((MagnetURIHandlerException)r);
							
						}else{
							
							return((byte[])r);
						}
					}
				}

				@Override
				public boolean
				download(
					URL		url )

					throws MagnetURIHandlerException
				{
					try{

						plugin_interface.getDownloadManager().addDownload( url, false );

						return( true );

					}catch( DownloadException e ){

						throw( new MagnetURIHandlerException( "Operation failed", e ));
					}
				}

				@Override
				public boolean
				set(
					String		name,
					Map		values )
				{
					List	l = listeners.getList();

					for (int i=0;i<l.size();i++){

						if (((MagnetPluginListener)l.get(i)).set( name, values )){

							return( true );
						}
					}

					return( false );
				}

				@Override
				public int
				get(
					String		name,
					Map			values )
				{
					List	l = listeners.getList();

					for (int i=0;i<l.size();i++){

						int res = ((MagnetPluginListener)l.get(i)).get( name, values );

						if ( res != Integer.MIN_VALUE ){

							return( res );
						}
					}

					return( Integer.MIN_VALUE );
				}
			});


		plugin_interface.getUIManager().addUIListener(
				new UIManagerListener()
				{
					@Override
					public void
					UIAttached(
						UIInstance		instance )
					{
						if ( instance.getUIType().equals(UIInstance.UIT_SWT) ){

							try{
								Class.forName("com.biglybt.plugin.magnet.swt.MagnetPluginUISWT").getConstructor(
									new Class[]{ UIInstance.class, TableContextMenuItem[].class }).newInstance(
										new Object[]{ instance, menus.toArray( new TableContextMenuItem[menus.size()])} );

							}catch( Throwable e ){

								e.printStackTrace();
							}
						}
					}

					@Override
					public void
					UIDetached(
						UIInstance		instance )
					{

					}
				});

		final List<Download>	to_delete = new ArrayList<>();

		Download[] downloads = plugin_interface.getDownloadManager().getDownloads();

		for ( Download download: downloads ){

			if ( download.getFlag( Download.FLAG_METADATA_DOWNLOAD )){

				to_delete.add( download );
			}
		}

		final AESemaphore delete_done = new AESemaphore( "delete waiter" );
		
		if ( to_delete.size() > 0 ){

			AEThread2 t =
				new AEThread2( "MagnetPlugin:delmds", true )
				{
					@Override
					public void
					run()
					{
						try{
							for ( Download download: to_delete ){
	
								try{
									download.stopAndRemove( true, true );
	
								}catch( Throwable e ){
	
									Debug.out( e );
								}
							}
						}finally{
							
							delete_done.release();
						}
					}
				};

			t.start();

		}else {

			delete_done.release();
		}
		
		
		plugin_interface.addListener(
				new PluginListener()
				{
					@Override
					public void
					initializationComplete()
					{
							// make sure DDB is initialised as we need it to register its
							// transfer types

						AEThread2 t =
							new AEThread2( "MagnetPlugin:init", true )
							{
								@Override
								public void
								run()
								{
									delete_done.reserve();
									
									recoverDownloads();
									
									plugin_interface.getDistributedDatabase();
								}
							};

						t.start();
					}

					@Override
					public void
					closedownInitiated()
					{
						updateRecoverableDownloads();
					}

					@Override
					public void
					closedownComplete(){}
				});
	}

	public String
	addSource(
		Download			download,
		String				magnet,
		InetSocketAddress	address )
	{
		boolean	is_share = false;
		
		try{
			byte[] hash = download.getTorrentHash();
			
			if ( plugin_interface.getShareManager().lookupShare( hash ) != null ){
				
				is_share = true;
			}								
		}catch( Throwable e ){								
		}
		
		String sources = sources_param.getValue();

		boolean add_sources = sources.equals( "2" ) || ( sources.equals( "1" ) && is_share );

		if ( add_sources ){
						
			String arg = "&xsource=" + UrlUtils.encode( UrlUtils.getURLForm( address ));
	
			magnet += arg;
		}
		
		return( magnet );
	}
	
	protected void
	updateLocale(
		LocaleUtilities	lu )
	{
		for ( int i=0;i<SOURCE_STRINGS.length;i++){

			SOURCE_STRINGS[i] = lu.getLocalisedMessageText( "MagnetPlugin.add.sources." + SOURCE_KEYS[i] );
		}

		if ( sources_param != null ){

			sources_param.setLabels( SOURCE_STRINGS );
		}
	}
	
	private void
	recoverDownloads()
	{
		Map<String,Map> active;
	
		synchronized( download_activities ){
			
			active = COConfigurationManager.getMapParameter( "MagnetPlugin.active.magnets", new HashMap());
		
			if ( active.size() > 0 ){
				
				active = BEncoder.cloneMap( active );
				
				COConfigurationManager.setParameter( "MagnetPlugin.active.magnets", new HashMap());
			}
		}
		
		boolean recover = magnet_recovery.getValue();

		Set<String>	active_hashes = new HashSet<>(active.size());
		
		if ( recover && !active.isEmpty()){
		
			int conc = magnet_recovery_concurrency.getValue();
			
			if ( conc < 1 ){
				conc = 1;
			}
			
			ThreadPool tp = new ThreadPool( "Magnet Recovery", conc, true );
			
			List<Map> sorted = new ArrayList<Map>( active.values());
			
			Collections.sort(
				sorted,
				(m1, m2)->{
					Long t1 = (Long)m1.get("added" );
					Long t2 = (Long)m2.get("added" );
					
					if ( t1 == t2 ){
						return(0);
					}else if ( t1 == null ){
						return( -1 );
					}else if ( t2 == null ){
						return( 1 );
					}else{
						long diff = t1 - t2;
						
						if ( diff < 0 ){
							return( -1 );
						}else if ( diff == 0 ){
							return( 0 );
						}else{
							return( 1 );
						}
					}
				});
			
			for ( Map map: sorted ){
					
				//System.out.println( "Recovering: " + map );
					
				try{
					byte[]	hash = (byte[])map.get( "hash" );
					
					active_hashes.add( ByteFormatter.encodeString( hash ));
					
					String args = MapUtils.getMapString( map, "args", "" );
					
					InetSocketAddress[] sources = new InetSocketAddress[0];
					
					List<Map> l_sources = (List<Map>)map.get( "sources" );
					
					if ( l_sources != null && !l_sources.isEmpty()){
						
						List<InetSocketAddress> l_ias = new ArrayList<>();
						
						for ( Map m: l_sources ){
							
							try{
								int	port = ((Number)m.get( "port" )).intValue();
								
								if ( map.containsKey( "host" )){
									
									String unresolved_host = MapUtils.getMapString( map, "host", "" );
									
									l_ias.add( InetSocketAddress.createUnresolved( unresolved_host, port ));
									
								}else{
									
									byte[] address = (byte[])map.get( "address" );
									
									l_ias.add( new InetSocketAddress( InetAddress.getByAddress( address), port ));
								}
							}catch( Throwable e ){
								
								Debug.out( e );
							}
						}
						
						sources = l_ias.toArray( new InetSocketAddress[l_ias.size()]);
					}
					
					List<String> l_tags = null;
					
					try{
						l_tags = BDecoder.decodeStrings((List)map.get( "tags" ));
						
					}catch( Throwable e ){
					}
					
					List<String> f_tags = l_tags;
					
					Map<String,Object>	other_metadata = (Map<String,Object>)map.get( "other_metadata" );
										
					long timeout = ((Number)map.get( "timeout" )).longValue();
					
					Long added_time = (Long)map.get( "added" );
										
					final InetSocketAddress[] f_sources = sources;
					
					tp.run(
						new AERunnable()
						{
							public void
							runSupport()
							{
								DownloadAsyncListener dl_listener =
									new DownloadAsyncListener(){
										
										@Override
										public void 
										failed(
											MagnetURIHandlerException error )
										{
											Debug.out( error );
										}
										
										@Override
										public void 
										complete(
											byte[] result )
										{
											if ( result != null ){
												
												try{
													TOTorrent torrent = TOTorrentFactory.deserialiseFromBEncodedByteArray( result );
													
													String	torrent_name = FileUtil.convertOSSpecificChars( TorrentUtils.getLocalisedName( torrent ) + ".torrent", false );
													
													File torrent_file;
													
													String dir = null;
													
												    if ( COConfigurationManager.getBooleanParameter("Save Torrent Files")){
												    	
														dir = COConfigurationManager.getDirectoryParameter("General_sDefaultTorrent_Directory");
														
														if ( dir != null ){
															
															if ( dir.length() > 0 ){
																
																File f = FileUtil.newFile( dir );
																
																if ( !f.exists()){
																	
																	f.mkdirs();
																}
																
																if ( !( f.isDirectory() && f.canWrite())){
																
																	dir = null;
																}
															}else{
																
																dir = null;
															}
														}
												    }
												    
												    if ( dir != null ){
												    	
												    	torrent_file = FileUtil.newFile( dir, torrent_name );
												    	
												    }else {
												    	
												    	torrent_file = FileUtil.newFile( AETemporaryFileHandler.getTempDirectory(), torrent_name );
												    }
												    
												    if ( torrent_file.exists()){
												    	
												    	torrent_file = AETemporaryFileHandler.createTempFile();
												    }
												      
												    TorrentUtils.writeToFile( torrent, torrent_file, false );
															
													UIFunctions uif = UIFunctionsManager.getUIFunctions();
													
													TorrentOpenOptions torrentOptions = new TorrentOpenOptions( null );
													
													torrentOptions.setDeleteFileOnCancel( true );
													torrentOptions.setTorrentFile( torrent_file.getAbsolutePath());
													torrentOptions.setTorrent( torrent );
													
													uif.addTorrentWithOptions( false, torrentOptions );
													
												}catch( Throwable e ){
													
													Debug.out( e );
												}
											}
										}
									};
								
								recoverableDownload( null, hash, args, f_sources, f_tags, other_metadata, timeout, added_time, true, dl_listener );
							}
						});
					
						// we want the metadata downloads to be added in the correct order. as the thread pool
						// will fire things off concurrently we add this hack to give things a chance to 
						// end up correct...
					
					Thread.sleep(500);
					
				}catch( Throwable e ){
					
					Debug.out( e );
				}
			}
		}
		
		AEThread2.createAndStartDaemon( "MagnetPlugin:mdtidy", ()->{
			
			try{
				File torrents_dir = FileUtil.getUserFile( "torrents" );

				File md_dir = FileUtil.newFile( torrents_dir, "md" );
				
				File[] files = md_dir.listFiles();
				
				if ( files != null ){
					
					long now = SystemTime.getCurrentTime();
					
					for ( File f: files ){
						
						if ( now - f.lastModified() > 24*60*60*1000 ){
					
							String name = f.getName();
							
							if ( name.length() == 40 &&  !active_hashes.contains( name )){
								
								Debug.outNoStack( "Deleting dead magnet download storage for " + name );
								
								FileUtil.recursiveDeleteNoCheck( f );
							}
						}
					}
				}

			}catch( Throwable e ){
				
				Debug.out( e );
			}
		});
	}
	
	private void
	updateRecoverableDownloads()
	{
		boolean recover = magnet_recovery.getValue();
		
		if ( recover ){
			
			synchronized( download_activities ){
				
				Map<String,Map> active = COConfigurationManager.getMapParameter( "MagnetPlugin.active.magnets", new HashMap());
			
				if ( active.size() > 0 ){
					
					active = BEncoder.cloneMap( active );
					
					boolean do_update = false;
					
					for ( Map map: active.values()){
						
						//System.out.println( "Recovering: " + map );
							
						try{
							byte[]	hash = (byte[])map.get( "hash" );
							
							Download download = plugin_interface.getDownloadManager().getDownload( hash );
							
							if ( download != null ){
								
								com.biglybt.core.download.DownloadManager		core_dm = PluginCoreUtils.unwrap( download );

								if ( updateInitialMetadata( map, core_dm )){
									
									do_update = true;
								}
								
							}
						}catch( Throwable e ){
							
						}
					}
					
					if ( do_update ){
					
						COConfigurationManager.setParameter( "MagnetPlugin.active.magnets", active );
					}
				}
			}
		}
	}
	
	private void
	recoverableDownload(
		final MagnetURIHandlerProgressListener 		muh_listener,
		final byte[]								hash,
		final String								args,
		final InetSocketAddress[]					sources,
		List<String>								tags,
		Map<String,Object>							other_metadata,
		final long									timeout,
		Long										added_time,
		boolean										is_recovering,
		DownloadAsyncListener						_dl_listener )
	{
		boolean recover = magnet_recovery.getValue();
		
		String hash_str = Base32.encode( hash );
		
		//System.out.println( "Starts: " + args );
		
		Runnable run_finally = ()->
		{
			if ( recover ){
				
				synchronized( download_activities ){
					
					Map active = COConfigurationManager.getMapParameter( "MagnetPlugin.active.magnets", new HashMap());
				
					active.remove( hash_str );
				}
				
				COConfigurationManager.setDirty();
			}
		};

		try{		
			if ( recover ){

				Map map = new HashMap();
				
				map.put( "hash", hash );
				map.put( "args", args );
				
				if ( added_time != null ){
					
					map.put( "added", added_time );
				}
				
				List<Map> l_sources = new ArrayList<>();
				
				map.put( "sources", l_sources );
				
				if ( sources != null && sources.length > 0 ){
					
					for ( InetSocketAddress isa: sources ){
						
						try{
							Map m = new HashMap();
													
							m.put( "port", isa.getPort());
							
							if ( isa.isUnresolved()){
								
								map.put( "host", isa.getHostName());
								
							}else{
								
								InetAddress ia = isa.getAddress();
								
								map.put( "address", ia.getAddress());
							}
							
							l_sources.add( m );
							
						}catch( Throwable e ){
							
							Debug.out( e );
						}
					}
				}
				
				if ( tags != null ){
					
					map.put( "tags", tags );
				}
				
				if ( other_metadata != null ){
					
					map.put( "other_metadata", other_metadata );
				}
				
				map.put( "timeout", timeout );
				
				synchronized( download_activities ){
				
					Map active = COConfigurationManager.getMapParameter( "MagnetPlugin.active.magnets", new HashMap());
				
					active.put( hash_str, map );
					
					COConfigurationManager.setParameter( "MagnetPlugin.active.magnets", active );
				}
				
				COConfigurationManager.setDirty();
			}
						
			DownloadAsyncListener dl_listener = 
				new DownloadAsyncListener(){
					
					@Override
					public void 
					failed(
						MagnetURIHandlerException error )
					{
						try{
							_dl_listener.failed(error);
							
						}finally{
							
							run_finally.run();
						}
					}
					
					@Override
					public void 
					complete(
						byte[] torrent_data)
					{
						try{
							_dl_listener.complete(torrent_data);
							
						}finally{
							
							run_finally.run();
						}
					}
				};
				
			downloadAsync(
				muh_listener == null ? null : new MagnetPluginProgressListener()
				{
					@Override
					public void
					reportSize(
							long	size )
					{
						muh_listener.reportSize( size );
					}

					@Override
					public void
					reportActivity(
							String	str )
					{
						muh_listener.reportActivity( str );
					}

					@Override
					public void
					reportCompleteness(
							int		percent )
					{
						muh_listener.reportCompleteness( percent );
					}

					@Override
					public void
					reportContributor(
							InetSocketAddress	address )
					{
					}

					@Override
					public boolean
					cancelled()
					{
						return( muh_listener.cancelled());
					}

					@Override
					public boolean
					verbose()
					{
						return( muh_listener.verbose());
					}
				},
				hash,
				args,
				sources,
				tags,
				other_metadata,
				timeout,
				is_recovering?MagnetPlugin.FL_NO_MD_LOOKUP_DELAY:MagnetPlugin.FL_NONE,
				dl_listener );
						
		}catch( Throwable e ) {
			
			try{
				_dl_listener.failed( new MagnetURIHandlerException( "Magnet download failed", e ));
				
			}finally{
			
				run_finally.run();
			}
		}
	}
	
	public boolean
	isNetworkEnabled(
		String		net )
	{
		return( net_params.get( net ).getValue());
	}

	public URL
	getMagnetURL(
		byte[]		hash )
	{
		try{
			return( new URL( UrlUtils.getMagnetURI( hash )));
			
		}catch( Throwable e ){
			
			Debug.out( e );
			
			return( null );
		}
	}
	
	public URL
	getMagnetURL(
		TOTorrent		torrent  )
	
		throws TOTorrentException
	{
		try{
			return( new URL(  UrlUtils.getMagnetURI( torrent )));
			
		}catch( Throwable e ){
			
			Debug.out( e );
			
			return( null );
		}
	}

	public List<String>
	getExtraTrackers()
	{
		String extra = md_extra_trackers.getValue();
		
		List<String> result = new ArrayList<>();
		
		String[] bits = extra.split( "\n" );
		
		for ( String bit: bits ){
			
			bit = bit.trim();
			
			if ( !bit.isEmpty()){
				
				result.add( bit );
			}
		}
		
		return( result );
	}
	
	public byte[]
	badge()
	{
		return( null );
	}

	public byte[]
	download(
		MagnetPluginProgressListener		listener,
		byte[]								hash,
		String								args,
		InetSocketAddress[]					sources,
		List<String>						tags,
		Map<String,Object>					other_metadata,
		long								timeout,
		int									flags )

		throws MagnetURIHandlerException
	{
		Object[] result = { null };
		
		AESemaphore sem = new AESemaphore( "dlwait" );
		
		DownloadAsyncListener dl_listener =
			new DownloadAsyncListener(){
				
				@Override
				public void 
				failed(
					MagnetURIHandlerException error )
				{
					synchronized( result ){
						result[0] = error;
					}
					sem.release();
				}
				
				@Override
				public void 
				complete(
					byte[] torrent_data ) 
				{
					synchronized( result ){
						result[0] = torrent_data;
					}
					sem.release();
				}
			};
			
		downloadAsync( listener, hash, args, sources, tags, other_metadata, timeout, flags, dl_listener );
		
		sem.reserve();
		
		synchronized( result ){
			
			Object r = result[0];
			
			if ( r instanceof MagnetURIHandlerException ){
				
				throw((MagnetURIHandlerException)r);
				
			}else{
				
				return((byte[])r);
			}
		}
	}

	public void
	downloadAsync(
		MagnetPluginProgressListener		listener,
		byte[]								hash,
		String								args,
		InetSocketAddress[]					sources,
		List<String>						tags,
		Map<String,Object>					other_metadata,
		long								timeout,
		int									flags,
		DownloadAsyncListener				dl_listener )

		throws MagnetURIHandlerException
	{
		DownloadResultListener result_listener =
			new DownloadResultListener(){
				
				@Override
				public void 
				failed(
					MagnetURIHandlerException error )
				{
					dl_listener.failed(error);
				}
				
				@Override
				public void 
				complete(
					DownloadResult result ){

					if ( result == null ){

						dl_listener.complete( null );
						
					}else{

						dl_listener.complete( addTrackersAndWebSeedsEtc( result, args, tags, other_metadata  ));
					}
				}
			};
			
		downloadSupport( listener, hash, args, sources, tags, other_metadata, timeout, flags, result_listener );
	}
	
	private byte[]
	addTrackersAndWebSeedsEtc(
		DownloadResult		result,
		String				args,
		List<String>		tags,
		Map<String,Object>	other_metadata )
	{
		byte[]		torrent_data 	= result.getTorrentData();
		Set<String>	networks		= result.getNetworks();

		DownloadManager dm = result.getDownload();
		
		if ( dm != null ){
			
			tags = (List<String>)dm.getUserData( DM_TAG_CACHE );
			
			other_metadata = getInitialMetadata( dm );
			
			String category = (String)dm.getUserData( DM_CATEGORY_CACHE );
			
			if ( category != null ){
				
				MapUtils.setMapString( other_metadata, "category", category );
			}
		}
		
		return( addTrackersAndWebSeedsEtc( torrent_data, args, networks, tags, other_metadata ));
	}

	private byte[]
	addTrackersAndWebSeedsEtc(
		byte[]				torrent_data,
		String				args,
		Set<String>			networks,
		List<String>		initial_tags,
		Map<String,Object>	other_metadata )
	{
		if ( initial_tags == null ){
			
			initial_tags = Collections.emptyList();
		}
		
		if ( other_metadata == null ){
			
			other_metadata = new HashMap<>();
		}
		
		List<String>	new_web_seeds 	= new ArrayList<>();
		List<String>	new_trackers 	= new ArrayList<>();

		Set<String>	tags			= new HashSet<>();
		
		String dn = null;
		
		if ( args != null ){

			String[] bits = args.split( "&" );

			for ( String bit: bits ){

				String[] x = bit.split( "=" );

				if ( x.length == 2 ){

					String	lhs = x[0].toLowerCase();
					String	rhs	= x[1];
					
					if ( lhs.equals( "ws" )){

						try{
							new_web_seeds.add( new URL( UrlUtils.decode( rhs )).toExternalForm());

						}catch( Throwable e ){
						}
					}else if ( lhs.equals( "tr" )){

						try{
							new_trackers.add( new URL( UrlUtils.decode( rhs )).toExternalForm());

						}catch( Throwable e ){
						}
					}else if ( lhs.equals( "tag" )){

						tags.add(UrlUtils.decode( rhs ));
						
					}else if ( lhs.equals( "dn" )){
						
						dn = UrlUtils.decode( rhs );
					}
				}
			}
		}

		if ( dn != null || new_web_seeds.size() > 0 || new_trackers.size() > 0 || networks.size() > 0 || !initial_tags.isEmpty() || !other_metadata.isEmpty()){

			try{
				TOTorrent torrent = TOTorrentFactory.deserialiseFromBEncodedByteArray( torrent_data );

				boolean	update_torrent = false;

				if ( dn != null ){
					
					if ( TorrentUtils.getDisplayName( torrent ) == null ){
						
						String t_name = torrent.getUTF8Name();
						
						if ( t_name == null ){
							
							t_name = new String( torrent.getName(), Constants.UTF_8 );
						}
						
						if ( !dn.equals( t_name )){
							
							TorrentUtils.setDisplayName( torrent, dn );
							
							update_torrent = true;
						}
					}
				}
				
				if ( new_web_seeds.size() > 0 ){

					Object obj = torrent.getAdditionalProperty( "url-list" );

					List<String> existing = new ArrayList<>();

					if ( obj instanceof byte[] ){

						try{
							new_web_seeds.remove( new URL( new String((byte[])obj, "UTF-8" )).toExternalForm());

						}catch( Throwable e ){
						}
					}else if ( obj instanceof List ){

						List<byte[]> l = (List<byte[]>)obj;

						for ( byte[] b: l ){

							try{
								existing.add( new URL( new String((byte[])b, "UTF-8" )).toExternalForm());

							}catch( Throwable e ){
							}
						}
					}

					boolean update_ws = false;

					for ( String e: new_web_seeds ){

						if ( !existing.contains( e )){

							existing.add( e );

							update_ws = true;
						}
					}

					if ( update_ws ){

						List<byte[]>	l = new ArrayList<>();

						for ( String s: existing ){

							l.add( s.getBytes( "UTF-8" ));
						}

						torrent.setAdditionalProperty( "url-list", l );

						update_torrent = true;
					}
				}

				if ( new_trackers.size() > 0 ){

					URL announce_url = torrent.getAnnounceURL();

					new_trackers.remove( announce_url.toExternalForm());

					TOTorrentAnnounceURLGroup group = torrent.getAnnounceURLGroup();

					TOTorrentAnnounceURLSet[] sets = group.getAnnounceURLSets();

					for ( TOTorrentAnnounceURLSet set: sets ){

						URL[] set_urls = set.getAnnounceURLs();

						for( URL set_url: set_urls ){

							new_trackers.remove( set_url.toExternalForm());
						}
					}

					if ( new_trackers.size() > 0 ){

						TOTorrentAnnounceURLSet[]	new_sets = new TOTorrentAnnounceURLSet[ sets.length + new_trackers.size()];

						System.arraycopy(sets, 0, new_sets, 0, sets.length);

						for ( int i=0;i<new_trackers.size();i++){

							TOTorrentAnnounceURLSet new_set = group.createAnnounceURLSet( new URL[]{ new URL( new_trackers.get(i))});

							new_sets[i+sets.length] = new_set;
						}

						group.setAnnounceURLSets( new_sets );

						update_torrent = true;
					}
				}

				if ( networks.size() > 0 ){

					TorrentUtils.setNetworkCache( torrent, new ArrayList<>(networks));

					update_torrent = true;
				}

				if ( tags.size() > 0 ){

					TorrentUtils.setTagCache( torrent, new ArrayList<>(tags));

					update_torrent = true;
				}

				if ( setInitialMetadata( torrent, initial_tags, other_metadata )){
					
					update_torrent = true;
				}
				
				if ( update_torrent ){

					torrent_data = BEncoder.encode( torrent.serialiseToMap());
				}
			}catch( Throwable e ){
			}
		}

		return( torrent_data );
	}
	
	protected List<String>
	getInitialTags(
		DownloadManager		from_dm )
	{
		List<String> tag_names = new ArrayList<>();

		try{
			List<Tag> tags = TagManagerFactory.getTagManager().getTagsForTaggable( TagType.TT_DOWNLOAD_MANUAL, from_dm );
			
			if ( !tags.isEmpty()){
													
				for ( Tag t: tags ){
					
					if ( !t.isTagAuto()[0]){
					
						tag_names.add( t.getTagName( true ));
					}
				}
			}
		}catch( Throwable e ){	
		}
		
		return( tag_names );
	}
	
	protected boolean
	updateInitialMetadata(
		Map					map,
		DownloadManager		from_dm )
	{
			// update persistent magnet metadata
		
		List<String> tag_names = getInitialTags( from_dm );
		
		boolean	updated = false;
		
		if ( !tag_names.isEmpty()){
			
			map.put( "tags", tag_names );
			
			updated = true;
			
		}else{
			
			if ( map.remove( "tags" ) != null ){
				
				updated = true;
			}
		}
		
		Map<String,Object>	other_metadata = getInitialMetadata( from_dm );
	
		if ( !other_metadata.isEmpty()){
		
			map.put( "other_metadata", other_metadata );
			
			updated = true;
			
		}else{
			
			if ( map.remove( "other_metadata" ) != null ){
				
				updated = true;
			}
		}
		
		return( updated );
	}
	
	protected void
	setDNChanged(
		DownloadManager		dm )
	{
		dm.setUserData( DM_DN_CHANGED, "" );
	}
	
	protected Map<String,Object>
	getInitialMetadata(
		DownloadManager	dm )
	{
		return( TorrentUtils.getInitialMetadata( dm, dm.getUserData( DM_DN_CHANGED ) != null ));
	}
	
	protected void
	setInitialMetadata(
		TOTorrent			torrent,
		DownloadManager		from_dm )
	{
			// md download complete, save into torrent to be picked up when added
		
		List<String> tag_names = getInitialTags( from_dm );
		
		if ( !tag_names.isEmpty()){
		
			TorrentUtils.setInitialTags( torrent, tag_names );
		}
		
		Map<String,Object>	other_metadata = getInitialMetadata( from_dm );
		
		if ( !other_metadata.isEmpty()){
			
			TorrentUtils.setInitialMetadata( torrent, other_metadata );
		}
	}
	
	protected void
	setInitialMetadata(
		DownloadManager		to_dm,
		List<String>		tags,
		Map<String,Object>	other_metadata )
	{
			// re-populate metadata into md download
		
		if ( tags != null ){
			
			try{
				TagManager tm = TagManagerFactory.getTagManager();
				
				for ( String tn: tags ){
					
					Tag tag = tm.getTagType( TagType.TT_DOWNLOAD_MANUAL ).getTag( tn, true );
					
					if ( tag != null ){
						
						tag.addTaggable( to_dm );
					}
				}	
			}catch( Throwable e ){
			}
		}
		
		if ( other_metadata != null && !other_metadata.isEmpty()){
			
			TorrentUtils.setInitialMetadata( to_dm, other_metadata, true );
		}
	}
	
	private boolean
	setInitialMetadata(
		TOTorrent			torrent,
		List<String>		tags,
		Map<String,Object>	other_metadata )
	{
		boolean	update = false;
		
		if ( !tags.isEmpty()){
			
			TorrentUtils.setInitialTags( torrent, new ArrayList<>(tags));

			update = true;
		}
		
		if ( !other_metadata.isEmpty()){
			
			TorrentUtils.setInitialMetadata( torrent, other_metadata );

			update = true;
		}
		
		return( update );
	}
	
	private static ByteArrayHashMap<DownloadActivity>	download_activities = new ByteArrayHashMap<>();

	private static class
	DownloadActivity
	{
		private boolean						result_set;
		
		private DownloadResult				result;
		private MagnetURIHandlerException	error;

		private List<DownloadResultListener>		listeners = new ArrayList<>(2);
		
		public void
		addListener(
			DownloadResultListener			l )
		{
			boolean already_done;
			
			synchronized( this ){
				
				already_done = result_set;
					
				if ( !already_done ){
				
					listeners.add( l );
				}
			}
			
			if ( already_done ){
				
				if ( error != null ){
					
					l.failed(error);
					
				}else{
					
					l.complete(result);
				}
			}
		}
		
		public void
		setResult(
			DownloadResult	_result )
		{
			List<DownloadResultListener> to_do;
		
			synchronized( this ){
				
				result_set = true;
				
				result	= _result;

				to_do = listeners;
				
				listeners = new ArrayList<>();
			}
			
			for ( DownloadResultListener l: to_do ){
				
				try{
					l.complete(_result);
					
				}catch( Throwable e ){
					
					Debug.out(e);
				}
			}
		}
			

		public void
		setResult(
			Throwable _error  )
		{
			List<DownloadResultListener> to_do;
			
			synchronized( this ){

				result_set = true;
			
				if ( _error instanceof MagnetURIHandlerException ){

					error = (MagnetURIHandlerException)_error;

				}else{

					error = new MagnetURIHandlerException( "Download failed", _error );
				}
				
				to_do = listeners;
				
				listeners = new ArrayList<>();
			}

			for ( DownloadResultListener l: to_do ){
				
				try{
					l.failed( error );
					
				}catch( Throwable e ){
					
					Debug.out(e);
				}
			}
		}
	}

	private void
 	downloadSupport(
 		MagnetPluginProgressListener	listener,
 		byte[]							hash,
 		String							args,
 		InetSocketAddress[]				sources,
 		List<String>					tags,
 		Map<String,Object>				initial_metadata,
 		long							timeout,
 		int								flags,
 		DownloadResultListener			_result_listener )
 	{
		DownloadActivity	activity;
		boolean				new_activity = false;

 		synchronized( download_activities ){

 				// single-thread per hash to avoid madness ensuing if we get multiple concurrent hits

 			activity = download_activities.get( hash );

 			if ( activity == null ){

 				activity = new DownloadActivity();

 				download_activities.put( hash, activity );

 				new_activity = true;
 			}
 			
 			activity.addListener( _result_listener );
 		}

 		if ( new_activity ){

 			DownloadActivity f_activity = activity;
 			
	 		try{
	 			DownloadResultListener result_listener = 
	 				new DownloadResultListener()
	 				{
		 				@Override
		 				public void complete(DownloadResult result){
		 					f_activity.setResult( result );
		 				}
		 				@Override
	 					public void failed(MagnetURIHandlerException error){
		 					f_activity.setResult(error);
	 					}
	 				};

	 			_downloadSupport( listener, hash, args, sources, tags, initial_metadata, timeout, flags, result_listener );

	 		}catch( Throwable e ){

	 			activity.setResult( e );

	 		}finally{

	 			synchronized( download_activities ){

	 				download_activities.remove( hash );
	 			}
	 		}
 		}
 	}

	private void
	_downloadSupport(
		final MagnetPluginProgressListener		listener,
		final byte[]							hash,
		final String							args,
		final InetSocketAddress[]				sources,
		List<String>							tags,
		Map<String,Object>						initial_metadata,
		long									_timeout,
		int										flags,
		DownloadResultListener					_result_listener )
	{
		DownloadManager[] download = { null };
		
		DownloadResultListener result_listener =
			new DownloadResultListener(){
				
				@Override
				public void 
				failed(
					MagnetURIHandlerException error)
				{
					_result_listener.failed( error );;
				}
				
				@Override
				public void 
				complete(
					DownloadResult result)
				{
					if ( result != null ){
						
						result.setDownload( download[0] );
					}
					
					_result_listener.complete( result );
				}
			};
			
		_downloadSupport( listener, hash, args, sources, tags, initial_metadata, _timeout, flags, download, result_listener );
	}
	
	private void
	_downloadSupport(
		final MagnetPluginProgressListener		listener,
		final byte[]							hash,
		final String							args,
		final InetSocketAddress[]				sources,
		List<String>							tags,
		Map<String,Object>						initial_metadata,
		long									_timeout,
		int										flags,
		DownloadManager[]						cancelled_download,
		DownloadResultListener					result_listener )
	{
		final long	timeout;

		if ( _timeout < 0 ){

			// use plugin defined value

			int secs = timeout_param.getValue();

			if ( secs <= 0 ){

				timeout = Integer.MAX_VALUE;

			}else{

				timeout = secs*1000L;
			}

		}else{

			timeout = _timeout;
		}

		boolean	md_enabled;

		final boolean	dummy_hash = Arrays.equals( hash, new byte[20] );

		if ((flags & FL_DISABLE_MD_LOOKUP) != 0 ){

			md_enabled = false;

		}else{

			md_enabled = md_lookup.getValue() && FeatureAvailability.isMagnetMDEnabled();
		}

		final byte[][]		result_holder 		= { null };
		final Throwable[] 	result_error 		= { null };
		final boolean[]		manually_cancelled	= { false };
		
		TimerEventPeriodic[] final_timer = { null };
		
		TimerEvent[]						md_delay_event = { null };
		final MagnetPluginMDDownloader[]	md_downloader = { null };

		boolean	net_pub_default = isNetworkEnabled( AENetworkClassifier.AT_PUBLIC );

		final Set<String>	networks_enabled;

		final Set<String>	additional_networks = new HashSet<>();

		if ( args != null ){

			String[] bits = args.split( "&" );

			List<URL>	fl_args 	= new ArrayList<>();

			Set<String>	tr_networks 		= new HashSet<>();
			Set<String>	explicit_networks 	= new HashSet<>();

			for ( String bit: bits ){

				if ( bit.startsWith( "maggot_sha1" )){

					tr_networks.clear();

					explicit_networks.clear();

					fl_args.clear();

					explicit_networks.add( AENetworkClassifier.AT_I2P  );

					break;
				}

				String[] x = bit.split( "=" );

				if ( x.length == 2 ){

					String	lhs = x[0].toLowerCase();

					if ( lhs.equals( "fl" ) || lhs.equals( "xs" ) || lhs.equals( "as" )){

						try{
							URL url = new URL( UrlUtils.decode( x[1] ));

							fl_args.add(url );

							tr_networks.add( AENetworkClassifier.categoriseAddress( url.getHost()));

						}catch( Throwable e ){
						}
					}else if ( lhs.equals( "tr" )){

						try{
							tr_networks.add( AENetworkClassifier.categoriseAddress( new URL( UrlUtils.decode( x[1] )).getHost()));

						}catch( Throwable e ){
						}
					}else if ( lhs.equals( "net" )){

						String network = AENetworkClassifier.internalise( x[1] );

						if ( network != null ){

							explicit_networks.add( network );
						}
					}
				}
			}

			if ( explicit_networks.size() > 0 ){

				networks_enabled = explicit_networks;

			}else{

				networks_enabled = tr_networks;

				if ( net_pub_default ){

					if ( networks_enabled.size() == 0 ){

						networks_enabled.add( AENetworkClassifier.AT_PUBLIC );
					}
				}else{

					networks_enabled.remove( AENetworkClassifier.AT_PUBLIC );
				}
			}

			if ( fl_args.size() > 0 ){

				final AESemaphore fl_sem = new AESemaphore( "fl_sem" );

				int	fl_run = 0;

				for ( int i=0;i<fl_args.size() && i < 3; i++ ){

					final URL fl_url = fl_args.get( i );

					String url_net = AENetworkClassifier.categoriseAddress( fl_url.getHost());

					if ( networks_enabled.contains( url_net )){

						new AEThread2( "Magnet:fldl", true )
						{
							@Override
							public void
							run()
							{
								try{
									TOTorrent torrent = TorrentUtils.download( fl_url, timeout );

									if ( torrent != null ){

										if ( dummy_hash || Arrays.equals( torrent.getHash(), hash )){

											synchronized( result_holder ){

												result_holder[0] = BEncoder.encode( torrent.serialiseToMap());
											}
										}
									}
								}catch( Throwable e ){

									Debug.out( e );

								}finally{

									fl_sem.release();
								}
							}
						}.start();

						fl_run++;
					}
				}

				if ( dummy_hash ){

					long	remaining = timeout;

					for ( int i=0; i<fl_run && remaining>0; i++ ){

						long	start = SystemTime.getMonotonousTime();

						if ( !fl_sem.reserve( remaining )){

							break;
						}

						remaining -= (SystemTime.getMonotonousTime() - start );

						synchronized( result_holder ){

							if ( result_holder[0] != null ){

								result_listener.complete( new DownloadResult( result_holder[0], networks_enabled, additional_networks ));
								
								return;
							}
						}
					}
				}
			}
		}else{

			networks_enabled = new HashSet<>();

			if ( net_pub_default ){

				networks_enabled.add( AENetworkClassifier.AT_PUBLIC );
			}
		}

		if ( dummy_hash ){

			result_listener.complete( null );
			
			return;
		}

			// networks-enabled has either the networks inferrable from the magnet set up
			// or, if none, then public (but only if public is enabled by default )

		if ( md_enabled ){

			int	delay_millis;
			
			if ((flags & FL_NO_MD_LOOKUP_DELAY ) != 0 ){
				
				delay_millis = 0;
				
			}else{
				
				delay_millis = md_lookup_delay.getValue()*1000;
			}

			md_delay_event[0] =
				SimpleTimer.addEvent(
					"MagnetPlugin:md_delay",
					delay_millis<=0?0:(SystemTime.getCurrentTime() + delay_millis ),
					new TimerEventPerformer()
					{
						@Override
						public void
						perform(
							TimerEvent event )
						{
							MagnetPluginMDDownloader mdd;

							synchronized( md_downloader ){

								if ( event.isCancelled()){

									return;
								}

								md_downloader[0] = mdd = new MagnetPluginMDDownloader( MagnetPlugin.this, plugin_interface, hash, networks_enabled, sources, tags, initial_metadata, args );
							}

							if ( listener != null ){
								listener.reportActivity( getMessageText( "report.md.starts" ));
							}

							mdd.start(
								new MagnetPluginMDDownloader.DownloadListener()
								{
									@Override
									public void
									reportProgress(
										int		downloaded,
										int		total_size )
									{
										if ( listener != null ){
											listener.reportActivity( getMessageText( "report.md.progress", String.valueOf( downloaded + "/" + total_size ) ));

											listener.reportCompleteness( 100*downloaded/total_size );
										}
									}
									
									@Override
									public void
									complete(
										TOTorrent		torrent,
										Set<String>		peer_networks )
									{
										if ( listener != null ){
											listener.reportActivity( getMessageText( "report.md.done" ));
										}

										synchronized( result_holder ){

											additional_networks.addAll( peer_networks );

											try{
												result_holder[0] = BEncoder.encode( torrent.serialiseToMap());

											}catch( Throwable e ){

												Debug.out( e );
											}
										}
									}

									@Override
									public void
									failed(
										boolean			mc,
										Throwable	 	e )
									{
										if ( listener != null ){
											listener.reportActivity( getMessageText( "report.error", Debug.getNestedExceptionMessage(e)));
										}

										synchronized( result_holder ){

											manually_cancelled[0] = mc;
											
											result_error[0] = e;
										}
									}
								});
						}
					});
		}


		try{
			try{
				long	remaining	= timeout;

					// public DHT lookup

				if ( networks_enabled.contains( AENetworkClassifier.AT_PUBLIC )){

					boolean	is_first_download = first_download;

					if ( is_first_download ){

						if ( listener != null ){
							listener.reportActivity( getMessageText( "report.waiting_ddb" ));
						}

						first_download = false;
					}

					while( true ){
						
						if ( db_waiter.reserve( 100 )){
							
							break;
						}
						
						if ( listener != null && listener.cancelled()){

							result_listener.complete( null );
							
							return;
						}

						synchronized( result_holder ){

							if ( result_holder[0] != null ){

								result_listener.complete( new DownloadResult( result_holder[0], networks_enabled, additional_networks ));
								
								return;
							}
							
							if ( manually_cancelled[0] ){
							
								throw( new Exception( "Manually cancelled" ));
							}
						}
					}
					
					DistributedDatabase db;
					
					synchronized( db_holder ){
						
						db = db_holder[0];
					}
					
					if ( db.isAvailable()){

						final List			potential_contacts 		= new ArrayList();
						final AESemaphore	potential_contacts_sem 	= new AESemaphore( "MagnetPlugin:liveones" );
						final AEMonitor		potential_contacts_mon	= new AEMonitor( "MagnetPlugin:liveones" );

						final int[]			outstanding		= {0};
						final boolean[]		lookup_complete	= {false};

						if ( listener != null ){
							listener.reportActivity(  getMessageText( "report.searching" ));
						}

						DistributedDatabaseListener	ddb_listener =
							new DistributedDatabaseListener()
							{
								private Set	found_set = new HashSet();

								@Override
								public void
								event(
									DistributedDatabaseEvent 		event )
								{
									int	type = event.getType();

									if ( type == DistributedDatabaseEvent.ET_OPERATION_STARTS ){

											// give live results a chance before kicking in explicit ones

										if ( sources.length > 0 ){

											new DelayedEvent(
												"MP:sourceAdd",
												10*1000,
												new AERunnable()
												{
													@Override
													public void
													runSupport()
													{
														addExplicitSources();
													}
												});
										}

									}else if ( type == DistributedDatabaseEvent.ET_VALUE_READ ){

										contactFound( event.getValue().getContact());

									}else if (	type == DistributedDatabaseEvent.ET_OPERATION_COMPLETE ||
												type == DistributedDatabaseEvent.ET_OPERATION_TIMEOUT ){

										if ( listener != null ){
											listener.reportActivity( getMessageText( "report.found", String.valueOf( found_set.size())));
										}

											// now inject any explicit sources

										addExplicitSources();

										try{
											potential_contacts_mon.enter();

											lookup_complete[0] = true;

										}finally{

											potential_contacts_mon.exit();
										}

										potential_contacts_sem.release();
									}
								}

								protected void
								addExplicitSources()
								{
									for (int i=0;i<sources.length;i++){

										try{
											InetSocketAddress source = sources[i];
											
											if ( AENetworkClassifier.categoriseAddress( source ) == AENetworkClassifier.AT_PUBLIC ){
											
												contactFound( db.importContact(sources[i]));
											}
										}catch( Throwable e ){

											Debug.printStackTrace(e);
										}
									}
								}

								public void
								contactFound(
									final DistributedDatabaseContact	contact )
								{
									String	key = contact.getAddress().toString();

									synchronized( found_set ){

										if ( found_set.contains( key )){

											return;
										}

										found_set.add( key );
									}

									if ( listener != null && listener.verbose()){

										listener.reportActivity( getMessageText( "report.found", contact.getName()));
									}

									try{
										potential_contacts_mon.enter();

										outstanding[0]++;

									}finally{

										potential_contacts_mon.exit();
									}

									contact.isAlive(
										20*1000,
										new DistributedDatabaseListener()
										{
											@Override
											public void
											event(
												DistributedDatabaseEvent event)
											{
												try{
													boolean	alive = event.getType() == DistributedDatabaseEvent.ET_OPERATION_COMPLETE;

													if ( listener != null && listener.verbose()){

														listener.reportActivity(
															getMessageText( alive?"report.alive":"report.dead",	contact.getName()));
													}

													try{
														potential_contacts_mon.enter();

														Object[]	entry = new Object[]{Boolean.valueOf(alive), contact};

														boolean	added = false;

														if ( alive ){

																// try and place before first dead entry

															for (int i=0;i<potential_contacts.size();i++){

																if (!((Boolean)((Object[])potential_contacts.get(i))[0]).booleanValue()){

																	potential_contacts.add(i, entry );

																	added = true;

																	break;
																}
															}
														}

														if ( !added ){

															potential_contacts.add( entry );	// dead at end
														}

													}finally{

														potential_contacts_mon.exit();
													}
												}finally{

													try{
														potential_contacts_mon.enter();

														outstanding[0]--;

													}finally{

														potential_contacts_mon.exit();
													}

													potential_contacts_sem.release();
												}
											}
										});
								}
							};

						db.read(
							ddb_listener,
							db.createKey( hash, "Torrent download lookup for '" + ByteFormatter.encodeString( hash ) + "'" ),
							timeout,
							DistributedDatabase.OP_EXHAUSTIVE_READ | DistributedDatabase.OP_PRIORITY_HIGH );

						AsyncDispatcher	dispatcher = new AsyncDispatcher();

						while( remaining > 0 ){

							try{
								potential_contacts_mon.enter();

								if ( 	lookup_complete[0] &&
										potential_contacts.size() == 0 &&
										outstanding[0] == 0 ){

									break;
								}
							}finally{

								potential_contacts_mon.exit();
							}


							while( remaining > 0 ){

								if ( listener != null && listener.cancelled()){

									result_listener.complete( null );
									
									return;
								}

								synchronized( result_holder ){

									if ( result_holder[0] != null ){

										result_listener.complete( new DownloadResult( result_holder[0], networks_enabled, additional_networks ));
										
										return;
									}
									
									if ( manually_cancelled[0] ){
									
										throw( new Exception( "Manually cancelled" ));
									}
								}

								long wait_start = SystemTime.getMonotonousTime();

								boolean got_sem = potential_contacts_sem.reserve( 1000 );

								long now = SystemTime.getMonotonousTime();

								remaining -= ( now - wait_start );

								if ( got_sem ){

									break;

								}else{

									continue;
								}
							}

							final DistributedDatabaseContact	contact;
							final boolean						live_contact;

							try{
								potential_contacts_mon.enter();

								// System.out.println( "rem=" + remaining + ",pot=" + potential_contacts.size() + ",out=" + outstanding[0] );

								if ( potential_contacts.size() == 0 ){

									if ( outstanding[0] == 0 ){

										break;

									}else{

										continue;
									}
								}else{

									Object[]	entry = (Object[])potential_contacts.remove(0);

									live_contact 	= ((Boolean)entry[0]).booleanValue();
									contact 		= (DistributedDatabaseContact)entry[1];
								}

							}finally{

								potential_contacts_mon.exit();
							}

							// System.out.println( "magnetDownload: " + contact.getName() + ", live = " + live_contact );

							final AESemaphore	contact_sem 	= new AESemaphore( "MD:contact" );

							dispatcher.dispatch(
								new AERunnable()
								{
									@Override
									public void
									runSupport()
									{
										try{
											if ( !live_contact ){

												if ( listener != null ){
													listener.reportActivity( getMessageText( "report.tunnel", contact.getName()));
												}

												contact.openTunnel();
											}

											try{
												if ( listener != null ){
													listener.reportActivity( getMessageText( "report.downloading", contact.getName()));
												}

												DistributedDatabaseValue	value =
													contact.read(
															listener == null ? null : new DistributedDatabaseProgressListener()
															{
																@Override
																public void
																reportSize(
																	long	size )
																{
																	listener.reportSize( size );
																}
																@Override
																public void
																reportActivity(
																	String	str )
																{
																	listener.reportActivity( str );
																}

																@Override
																public void
																reportCompleteness(
																	int		percent )
																{
																	listener.reportCompleteness( percent );
																}
															},
															db.getStandardTransferType( DistributedDatabaseTransferType.ST_TORRENT ),
															db.createKey ( hash , "Torrent download content for '" + ByteFormatter.encodeString( hash ) + "'"),
															timeout );

												if ( value != null ){

														// let's verify the torrent

													byte[]	data = (byte[])value.getValue(byte[].class);

													try{
														TOTorrent torrent = TOTorrentFactory.deserialiseFromBEncodedByteArray( data );

														if ( Arrays.equals( hash, torrent.getHash())){

															if ( listener != null ){
																listener.reportContributor( contact.getAddress());
															}

															synchronized( result_holder ){

																result_holder[0] = data;
															}
														}else{

															if ( listener != null ){
																listener.reportActivity( getMessageText( "report.error", "torrent invalid (hash mismatch)" ));
															}
														}
													}catch( Throwable e ){

														if ( listener != null ){
															listener.reportActivity( getMessageText( "report.error", "torrent invalid (decode failed)" ));
														}
													}
												}
											}catch( Throwable e ){

												if ( listener != null ){
													listener.reportActivity( getMessageText( "report.error", Debug.getNestedExceptionMessage(e)));
												}

												Debug.printStackTrace(e);
											}
										}finally{

											contact_sem.release();
										}
									}
								});

							while( true ){

								if ( listener != null && listener.cancelled()){

									result_listener.complete( null );
									
									return;
								}

								boolean got_sem = contact_sem.reserve( 500 );

								synchronized( result_holder ){

									if ( result_holder[0] != null ){

										result_listener.complete( new DownloadResult( result_holder[0], networks_enabled, additional_networks ));
										
										return;
									}
									
									if ( manually_cancelled[0] ){
										
										throw( new Exception( "Manually cancelled" ));
									}
								}

								if ( got_sem ){

									break;
								}
							}
						}
					}else{

						if ( is_first_download ){

							if ( listener != null ){
								listener.reportActivity( getMessageText( "report.ddb_disabled" ));
							}
						}
					}
				}

					// DDB lookup process is complete or skipped

					// lastly hang around until metadata download completes

				long ticks_left = remaining/500;
				
				if ( md_enabled && ticks_left > 0 ){

						// we can move to a timer based completion now
					
					synchronized( result_holder ){
						
						final_timer[0] = 
							SimpleTimer.addPeriodicEvent(
								"magnet:md:waiter",
								500,
								new TimerEventPerformer(){
									
									int	tick = 0;
									
									@Override
									public void perform(
										TimerEvent event)
									{
										boolean	done = false;
										
										tick++;
										
										try{
											synchronized( result_holder ){
	
												if ( listener != null && listener.cancelled()){
													
													done = true;
													
													result_listener.complete( null );
													
												}else{
										
													if ( result_holder[0] != null ){
						
														done = true;
														
														result_listener.complete( new DownloadResult( result_holder[0], networks_enabled, additional_networks ));
																												
													}else if ( result_error[0] != null ){
						
														done = true;
														
														result_listener.failed( new MagnetURIHandlerException( "MagnetURIHandler failed", result_error[0] ));
													}
												}
												
											
												if ( !done && tick >= ticks_left ){
													
													done = true;
													
													result_listener.complete( null );
												}
											}
										}catch( Throwable e ){
											
											Debug.out( e );
										}
										
										if ( done ){
											
											synchronized( md_downloader ){
												
												try{
													if ( md_delay_event[0] != null ){
									
														md_delay_event[0].cancel();
									
														MagnetPluginMDDownloader downloader = md_downloader[0];
														
														if ( downloader != null ){
									
															downloader.cancel();
																
															cancelled_download[0] = downloader.getDownloadManager();
														}
													}
												}finally{
													
													final_timer[0].cancel();
												}
											}
										}
									}
								});
					}
				}else{

					result_listener.complete( null );		// nothing found
				
					return;
				}

			}catch( Throwable e ){

				Debug.printStackTrace(e);

				if ( listener != null ){
					listener.reportActivity( getMessageText( "report.error", Debug.getNestedExceptionMessage(e)));
				}

				result_listener.failed( new MagnetURIHandlerException( "MagnetURIHandler failed", e ));
				
				return;
			}
		}finally{

			if ( final_timer[0] == null ){
				
				synchronized( md_downloader ){
	
					if ( md_delay_event[0] != null ){
	
						md_delay_event[0].cancel();
	
						MagnetPluginMDDownloader downloader = md_downloader[0];
						
						if ( downloader != null ){
	
							downloader.cancel();
								
							cancelled_download[0] = downloader.getDownloadManager();
						}
					}
				}
			}
		}
	}

	protected String
	getMessageText(
		String	resource,
		String...	params )
	{
		return( plugin_interface.getUtilities().getLocaleUtilities().getLocalisedMessageText(
				"MagnetPlugin." + resource, params ));
	}

	public void
	addListener(
		MagnetPluginListener		listener )
	{
		listeners.add( listener );
	}

	public void
	removeListener(
		MagnetPluginListener		listener )
	{
		listeners.remove( listener );
	}

	private static class
	DownloadResult
	{
		private byte[]		data;
		private Set<String>	networks;

		private DownloadManager		dm;
		
		private
		DownloadResult(
			byte[]			torrent_data,
			Set<String>		networks_enabled,
			Set<String>		additional_networks )
		{
			data		= torrent_data;

			networks = new HashSet<>();

			networks.addAll( networks_enabled );
			networks.addAll( additional_networks );
		}

		private void
		setDownload(
			DownloadManager		_dm )
		{
			dm		= _dm;
		}
		
		private DownloadManager
		getDownload()
		{
			return( dm );
		}
		
		private byte[]
		getTorrentData()
		{
			return( data );
		}

		private Set<String>
		getNetworks()
		{
			return( networks );
		}
	}
	
	private interface
	DownloadResultListener
	{
		public void
		complete(
			DownloadResult		result );
		
		public void
		failed(
			MagnetURIHandlerException	error );
	}
	
	private interface
	DownloadAsyncListener
	{
		public void
		complete(
			byte[]		torrent_data );
		
		public void
		failed(
			MagnetURIHandlerException	error );
	}
}
