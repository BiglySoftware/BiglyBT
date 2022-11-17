/*
 * File    : ManagerUtils.java
 * Created : 7 d�c. 2003}
 * By      : Olivier
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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
package com.biglybt.ui.swt.views.utils;


import java.io.*;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.torrent.PlatformTorrentUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.MenuListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;

import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.disk.DiskManagerCheckRequest;
import com.biglybt.core.disk.DiskManagerCheckRequestListener;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.disk.DiskManagerFileInfoSet;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerListener;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.download.DownloadManagerStateFactory;
import com.biglybt.core.download.impl.DownloadManagerAdapter;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerDownloadRemovalVetoException;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentFactory;
import com.biglybt.core.torrent.TOTorrentFile;
import com.biglybt.core.torrent.impl.TorrentOpenFileOptions;
import com.biglybt.core.torrent.impl.TorrentOpenOptions;
import com.biglybt.core.tracker.host.TRHostException;
import com.biglybt.core.util.*;
import com.biglybt.core.xml.util.XUXmlWriter;
import com.biglybt.platform.PlatformManager;
import com.biglybt.platform.PlatformManagerCapabilities;
import com.biglybt.platform.PlatformManagerFactory;
import com.biglybt.pif.PluginException;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.PluginManager;
import com.biglybt.pif.UnloadablePlugin;
import com.biglybt.pif.disk.DiskManagerChannel;
import com.biglybt.pif.disk.DiskManagerEvent;
import com.biglybt.pif.disk.DiskManagerListener;
import com.biglybt.pif.disk.DiskManagerRequest;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadRemovalVetoException;
import com.biglybt.pif.download.DownloadStub;
import com.biglybt.pif.platform.PlatformManagerException;
import com.biglybt.pif.sharing.*;
import com.biglybt.pif.tracker.Tracker;
import com.biglybt.pif.tracker.TrackerTorrent;
import com.biglybt.pif.tracker.web.TrackerWebPageRequest;
import com.biglybt.pif.tracker.web.TrackerWebPageResponse;
import com.biglybt.pif.ui.UIInputReceiver;
import com.biglybt.pif.ui.UIInputReceiverListener;
import com.biglybt.pif.utils.PooledByteBuffer;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.pifimpl.local.utils.FormattersImpl;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.SimpleTextEntryWindow;
import com.biglybt.ui.swt.TextViewerWindow;
import com.biglybt.ui.swt.Utils;

import com.biglybt.ui.swt.mainwindow.TorrentOpener;
import com.biglybt.ui.swt.shells.AdvRenameWindow;
import com.biglybt.ui.swt.shells.CoreWaiterSWT;
import com.biglybt.ui.swt.shells.CoreWaiterSWT.TriggerInThread;
import com.biglybt.ui.swt.skin.SWTSkin;
import com.biglybt.ui.swt.skin.SWTSkinButtonUtility;
import com.biglybt.ui.swt.skin.SWTSkinCheckboxListener;
import com.biglybt.ui.swt.skin.SWTSkinObject;
import com.biglybt.ui.swt.skin.SWTSkinObjectButton;
import com.biglybt.ui.swt.skin.SWTSkinObjectCheckbox;
import com.biglybt.ui.swt.skin.SWTSkinObjectContainer;
import com.biglybt.ui.swt.skin.SWTSkinObjectList;
import com.biglybt.ui.swt.skin.SWTSkinObjectTextbox;
import com.biglybt.ui.swt.views.skin.SkinnedDialog;
import com.biglybt.ui.swt.views.skin.StandardButtonsArea;
import com.biglybt.ui.swt.shells.MessageBoxShell;
import com.biglybt.ui.webplugin.WebPlugin;

import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagManager;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.tag.TagType;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;

/**
 * @author Olivier
 *
 */
public class ManagerUtils {

	private static RunDownloadManager run;

	public static interface RunDownloadManager {
		public void run(DownloadManager dm);
	}

	public static void setRunRunnable(RunDownloadManager run) {
		ManagerUtils.run = run;
	}

  public static void run( final DownloadManager dm) {
	if(dm != null) {
		LaunchManager	launch_manager = LaunchManager.getManager();

		LaunchManager.LaunchTarget target = launch_manager.createTarget( dm );

		launch_manager.launchRequest(
			target,
			new LaunchManager.LaunchAction()
			{
				@Override
				public void
				actionAllowed()
				{
					PlatformTorrentUtils.setHasBeenOpened( dm, true);

					Utils.execSWTThread(
						new Runnable()
						{
							@Override
							public void
							run()
							{
							   	if (run != null) {
						    		run.run(dm);
						    	} else {
						    		Utils.launch(dm.getSaveLocation().toString());
						    	}
							}
						});
				}

				@Override
				public void
				actionDenied(
					Throwable			reason )
				{
					Debug.out( "Launch request denied", reason );
				}
			});
	}
  }

 /**
  * Opens the parent folder of dm's path
  * @param dm DownloadManager instance
  */
	public static void open(DownloadManager dm) {open(dm, false);}

	public static void open(final DownloadManager dm, final boolean open_containing_folder_mode) {

		if ( dm != null ){

			LaunchManager	launch_manager = LaunchManager.getManager();

			LaunchManager.LaunchTarget target = launch_manager.createTarget( dm );

			launch_manager.launchRequest(
				target,
				new LaunchManager.LaunchAction()
				{
					@Override
					public void
					actionAllowed()
					{
						Utils.execSWTThread(
							new Runnable()
							{
								@Override
								public void
								run()
								{
									open( dm.getSaveLocation(), open_containing_folder_mode );
								}
							});
					}

					@Override
					public void
					actionDenied(
						Throwable			reason )
					{
						Debug.out( "Launch request denied", reason );
					}
				});
		}
	}

	public static void
	open(
		final DiskManagerFileInfo		file,
		final boolean					open_containing_folder_mode )
	{
		if ( file != null ){

			LaunchManager	launch_manager = LaunchManager.getManager();

			LaunchManager.LaunchTarget target = launch_manager.createTarget( file );

			launch_manager.launchRequest(
				target,
				new LaunchManager.LaunchAction()
				{
					@Override
					public void
					actionAllowed()
					{
						Utils.execSWTThread(
							new Runnable()
							{
								@Override
								public void
								run()
								{
									PlatformTorrentUtils.setHasBeenOpened( file.getDownloadManager(), file.getIndex(), true);

									File this_file = file.getFile(true);

									File parent_file = (open_containing_folder_mode) ? this_file.getParentFile() : null;

									open((parent_file == null) ? this_file : parent_file);
								}
							});
					}

					@Override
					public void
					actionDenied(
						Throwable			reason )
					{
						Debug.out( "Launch request denied", reason );
					}
				});
		}
	}


	public static void open(File f, boolean open_containing_folder_mode) {
		if (open_containing_folder_mode) {
			Utils.launch(f.getParent());
		}
		else {
			open(f);
		}
	}

	public static void open(File f) {
		while (f != null && !f.exists())
			f = f.getParentFile();

		if (f == null)
			return;

		PlatformManager mgr = PlatformManagerFactory.getPlatformManager();

		if (mgr.hasCapability(PlatformManagerCapabilities.ShowFileInBrowser)) {
			try {
				PlatformManagerFactory.getPlatformManager().showFile(f.toString());
				return;
			} catch (PlatformManagerException e) {
				Debug.printStackTrace(e);
			}
		}

		if (f.isDirectory()) {
			Utils.launch(f.toString()); // default launcher
		} else {
			Utils.launch(f.getParent().toString());
		}
	}

	private static boolean
	getBrowseAnon(
		DownloadManager		dm )
	{
		boolean	anon = COConfigurationManager.getBooleanParameter( "Library.LaunchWebsiteInBrowserAnon" );

		if ( !anon ){

			boolean	found_pub = false;

			String[] nets = dm.getDownloadState().getNetworks();

			for ( String net: nets ){

				if ( net == AENetworkClassifier.AT_PUBLIC ){

					found_pub = true;

					break;
				}
			}

			if ( nets.length > 0 && !found_pub ){

				anon = true;
			}
		}

		return( anon );
	}

	private static DiskManagerFileInfo
	getBrowseHomePage(
		DownloadManager		dm )
	{
		try{
			DiskManagerFileInfo[] files = dm.getDiskManagerFileInfoSet().getFiles();

			for ( DiskManagerFileInfo file: files ){

				if ( file.getTorrentFile().getPathComponents().length == 1 ){

					String name = file.getTorrentFile().getRelativePath().toLowerCase( Locale.US );

					if ( name.equals( "index.html" ) || name.equals( "index.htm" )){

						return( file );
					}
				}
			}
		}catch( Throwable e ){

			Debug.out( e );
		}

		return( null );
	}

	public static boolean
	browseWebsite(
		DiskManagerFileInfo		file )
	{
		try{
			String name = file.getTorrentFile().getRelativePath().toLowerCase( Locale.US );

			if ( name.equals( "index.html" ) || name.equals( "index.htm" )){

				ManagerUtils.browse( file );

				return( true );
			}
		}catch( Throwable e ){

			Debug.out( e );
		}

		return( false );
	}

	public static boolean
	browseWebsite(
		DownloadManager		dm )
	{
		DiskManagerFileInfo file = getBrowseHomePage( dm );

		if ( file != null ){

			ManagerUtils.browse( file );

			return( true );
		}

		return( false );
	}

	public static String
	browse(
		DiskManagerFileInfo 	file )
	{
		boolean anon = getBrowseAnon( file.getDownloadManager());

		return( browse( file, anon, true ));
	}

	public static String
	browse(
		DiskManagerFileInfo 	file,
		boolean					anon,
		boolean					launch )
	{
		return( browse( file.getDownloadManager(), file, anon, launch ));
	}

	public static String
	browse(
		DownloadManager 	dm )
	{
		boolean anon = getBrowseAnon( dm );

		return( browse( dm, null, anon, true ));
	}

	public static String
	browse(
		DownloadManager 	dm,
		boolean				anon,
		boolean				launch )
	{
		return( browse( dm, null, anon, launch ));
	}


	private static Map<DownloadManager,WebPlugin>	browse_plugins = new IdentityHashMap<>();

	public static String
	browse(
		final DownloadManager 			dm,
		DiskManagerFileInfo				_file,
		final boolean					anon,
		final boolean					launch )
	{
		Properties	props = new Properties();

		File	save_location = dm.getSaveLocation();

		final String	root_dir;

		if ( save_location.isFile()){

			root_dir = save_location.getParentFile().getAbsolutePath();

		}else{

			root_dir = save_location.getAbsolutePath();
		}

		final String	url_suffix;

		boolean	always_browse = COConfigurationManager.getBooleanParameter( "Library.LaunchWebsiteInBrowserDirList" );

		if ( !always_browse ){

			if ( _file == null ){

				_file = getBrowseHomePage( dm );
			}
		}

		final DiskManagerFileInfo file = _file;

		if ( file == null ){

				// asked to launch a download (note that the double-click on a download that has an index.html file will by default result in
				// us getting here with the file set, not null)

			url_suffix = "";

		}else{

			String relative_path = file.getTorrentFile().getRelativePath();

			String[] bits = relative_path.replace( File.separatorChar, '/' ).split( "/" );

			String _url_suffix = "";

			int	bits_to_use = always_browse?bits.length-1:bits.length;

			for ( int i=0;i<bits_to_use;i++){

				String bit = bits[i];

				if ( bit.length() == 0 ){

					continue;
				}

				_url_suffix += (_url_suffix==""?"":"/") + UrlUtils.encode( bit );
			}

			url_suffix = _url_suffix;
		}

		synchronized( browse_plugins ){

			WebPlugin	plugin = browse_plugins.get( dm );

			if ( plugin == null ){

				props.put( WebPlugin.PR_PORT, 0 );
				props.put( WebPlugin.PR_BIND_IP, "127.0.0.1" );
				props.put( WebPlugin.PR_HOME_PAGE, "" );
				props.put( WebPlugin.PR_ROOT_DIR, root_dir );
				props.put( WebPlugin.PR_ACCESS, "local" );
				props.put( WebPlugin.PR_HIDE_RESOURCE_CONFIG, true );

				props.put( WebPlugin.PR_ENABLE_KEEP_ALIVE, true );
				props.put( WebPlugin.PR_ENABLE_PAIRING, false );
				props.put( WebPlugin.PR_ENABLE_UPNP, false );
				props.put( WebPlugin.PR_ENABLE_I2P, false );
				props.put( WebPlugin.PR_ENABLE_TOR, false );

				final String plugin_id 		= "webserver:" + dm.getInternalName();
				final String plugin_name	= "Web Server for " + dm.getDisplayName();

				Properties messages = new Properties();

				messages.put( "plugins." + plugin_id, plugin_name );

				PluginInitializer.getDefaultInterface().getUtilities().getLocaleUtilities().integrateLocalisedMessageBundle( messages );

				final AESemaphore 	waiter 		= new AESemaphore( "waiter" );
				final String[]		url_holder	= { null };

				plugin =
					new UnloadableWebPlugin( props )
					{
						private Map<String,Object>	file_map = new HashMap<>();

						private String	protocol;
						private String	host;
						private int		port;

						@Override
						public void
						initialize(
							PluginInterface plugin_interface )

							throws PluginException
						{
							DiskManagerFileInfoSet file_set = dm.getDiskManagerFileInfoSet();

							DiskManagerFileInfo[] files = file_set.getFiles();

							Set<Object>	root_dir = new HashSet<>();

							file_map.put( "", root_dir );

							for ( DiskManagerFileInfo dm_file: files ){

								TOTorrentFile file = dm_file.getTorrentFile();

								String	path = file.getRelativePath();

								file_map.put( path, dm_file );

								if ( path.startsWith( File.separator )){

									path = path.substring( 1 );
								}

								Set<Object>	dir = root_dir;

								int	pos = 0;

								while( true ){

									int next_pos = path.indexOf( File.separatorChar, pos );

									if ( next_pos == -1 ){

										dir.add( dm_file );

										break;

									}else{

										String bit = path.substring( pos, next_pos );

										dir.add( bit );

										String sub_path = path.substring( 0, next_pos );

										dir = (Set<Object>)file_map.get( sub_path );

										if ( dir == null ){

											dir = new HashSet<>();

											file_map.put( sub_path, dir );
										}

										pos = next_pos + 1;
									}
								}
							}

							Properties props = plugin_interface.getPluginProperties();

							props.put( "plugin.name", plugin_name );

							super.initialize( plugin_interface );

							InetAddress	bind_ip = getServerBindIP();

							if ( bind_ip.isAnyLocalAddress()){

								host = "127.0.0.1";

							}else{

								host = bind_ip.getHostAddress();
							}

							port = getServerPort();

							log( "Assigned port: " + port );

							protocol = getProtocol();

							String url = protocol + "://" + host + ":" + port + "/" + url_suffix;

							if ( launch ){

								Utils.launch( url, false, true, anon );

							}else{

								synchronized( url_holder ){

									url_holder[0] = url;
								}

								waiter.release();
							}
						}

						@Override
						public boolean
						generate(
							TrackerWebPageRequest		request,
							TrackerWebPageResponse		response )

							throws IOException
						{
							try{
								boolean res = super.generate(request, response);

								if ( !res ){

									response.setReplyStatus( 404 );
								}
							}catch( Throwable e ){

								response.setReplyStatus( 404 );
							}

							return( true );
						}

						@Override
						protected boolean
						useFile(
							TrackerWebPageRequest				request,
							final TrackerWebPageResponse		response,
							File								root,
							String								relative_url )

							throws IOException
						{
							URL absolute_url = request.getAbsoluteURL();

							String query = absolute_url.getQuery();

							if ( query != null ){

								String[] args = query.split( "&" );

								String 	vuze_source 	= null;
								int		vuze_file_index	= -1;
								String	vuze_file_name	= null;

								List<String>	networks = new ArrayList<>();

								for ( String arg: args ){

									String[] bits= arg.split( "=" );

									String lhs = bits[0];
									String rhs = UrlUtils.decode( bits[1] );

									if ( lhs.equals( "vuze_source" )){

										if ( rhs.endsWith( ".torrent" ) || rhs.startsWith( "magnet" )){

											vuze_source = rhs;
										}
									}else if ( lhs.equals( "vuze_file_index" )){

										vuze_file_index = Integer.parseInt( rhs );

									}else if ( lhs.equals( "vuze_file_name" )){

										vuze_file_name = rhs;

									}else if ( lhs.equals( "vuze_network" )){

										String net = AENetworkClassifier.internalise( rhs );

										if ( net != null ){

											networks.add( net );
										}
									}
								}

								if ( vuze_source != null ){

									String referrer = (String)request.getHeaders().get( "referer" );

									if ( referrer == null || !referrer.contains( "://" + host + ":" + port )){

										response.setReplyStatus( 403 );

										return( true );
									}

									if ( vuze_source.endsWith( ".torrent" )){

										Object	file_node = file_map.get( vuze_source );

										if ( file_node instanceof DiskManagerFileInfo ){

											DiskManagerFileInfo dm_file = (DiskManagerFileInfo)file_node;

											long	file_size = dm_file.getLength();

											File target_file = dm_file.getFile( true );

											boolean done = 	dm_file.getDownloaded() == file_size &&
															target_file.length() == file_size;

											if ( done ){

												return( handleRedirect( dm, target_file, vuze_file_index, vuze_file_name, networks, request, response ));

											}else{

												try{
													File torrent_file = AETemporaryFileHandler.createTempFile();

													final FileOutputStream fos = new FileOutputStream( torrent_file );

													try{
														DiskManagerChannel chan = PluginCoreUtils.wrap( dm_file ).createChannel();

														try{
															final DiskManagerRequest req = chan.createRequest();

															req.setOffset( 0 );
															req.setLength( file_size );

															req.addListener(
																new DiskManagerListener()
																{
																	@Override
																	public void
																	eventOccurred(
																		DiskManagerEvent	event )
																	{
																		int	type = event.getType();

																		if ( type ==  DiskManagerEvent.EVENT_TYPE_BLOCKED ){

																			return;

																		}else if ( type == DiskManagerEvent.EVENT_TYPE_FAILED ){

																			throw( new RuntimeException( event.getFailure()));
																		}

																		PooledByteBuffer buffer = event.getBuffer();

																		if ( buffer == null ){

																			throw( new RuntimeException( "eh?" ));
																		}

																		try{

																			byte[] data = buffer.toByteArray();

																			fos.write( data );

																		}catch( IOException e ){

																			throw( new RuntimeException( "Failed to write to " + file, e ));

																		}finally{

																			buffer.returnToPool();
																		}
																	}
																});

															req.run();

														}finally{

															chan.destroy();
														}
													}finally{

														fos.close();
													}

													return( handleRedirect( dm, torrent_file, vuze_file_index, vuze_file_name, networks, request, response ));

												}catch( Throwable e ){

													Debug.out( e );

													return( false );
												}

											}

										}else{

											return( false );
										}
									}else{

										URL magnet = new URL( vuze_source );

										File torrent_file = AETemporaryFileHandler.createTempFile();

										try{
											URLConnection connection = magnet.openConnection();

											connection.connect();

											FileUtil.copyFile( connection.getInputStream(), torrent_file.getAbsoluteFile());

											return( handleRedirect( dm, torrent_file, vuze_file_index, vuze_file_name, networks, request, response ));

										}catch( Throwable e ){

											Debug.out( e );
										}

									}
								}
							}

							String path = absolute_url.getPath();

							if ( path.equals( "/" )){

								if ( COConfigurationManager.getBooleanParameter( "Library.LaunchWebsiteInBrowserDirList" )){

									relative_url = "/";
								}
							}

							String download_name = XUXmlWriter.escapeXML( dm.getDisplayName());

							String relative_file = relative_url.replace( '/', File.separatorChar );

							String	node_key = relative_file.substring( 1 );

							Object	file_node = file_map.get( node_key );

							boolean	file_node_is_parent = false;

							if ( file_node == null ){

								int pos = node_key.lastIndexOf( File.separator );

								if ( pos == -1 ){

									node_key = "";

								}else{

									node_key = node_key.substring( 0, pos );
								}

								file_node = file_map.get( node_key );

								file_node_is_parent = true;
							}

							if ( file_node == null){

								return( false );
							}

							if ( file_node instanceof Set ){

								if ( relative_url.equals( "/favicon.ico" )){

									try{
										InputStream stream = getClass().getClassLoader().getResourceAsStream("com/biglybt/ui/icons/favicon.ico");

										response.useStream( "image/x-icon", stream);

										return( true );

									}catch( Throwable e ){
									}
								}

								Set<Object>		kids = (Set<Object>)file_node;

								String request_url = request.getURL();

								if ( file_node_is_parent ){

									int	pos = request_url.lastIndexOf( "/" );

									if ( pos == -1 ){

										request_url = "";

									}else{

										request_url = request_url.substring( 0, pos );
									}
								}

								response.setContentType( "text/html" );

								OutputStream os = response.getOutputStream();

								String title = XUXmlWriter.escapeXML( UrlUtils.decode( request_url ));

								if ( title.length() == 0 ){

									title = "/";
								}

								os.write((
									"<html>" + NL +
									" <head>" + NL +
									" <meta charset=\"UTF-8\">" + NL +
									"  <title>" + download_name + ": Index of " + title + "</title>" + NL +
									" </head>" + NL +
									" <body>" + NL +
									"  <p>" + download_name + "</p>" + NL +
									"  <h1>Index of " + title + "</h1>" + NL +
									"  <pre><hr>" + NL ).getBytes( "UTF-8" ));

								String root_url = request_url;

								if ( !root_url.endsWith( "/" )){

									root_url += "/";
								}

								if ( request_url.length() > 1 ){

									int	pos = request_url.lastIndexOf( '/' );

									if ( pos == 0 ){

										pos++;
									}

									String parent = request_url.substring( 0, pos );

									os.write(( "<a href=\"" + parent + "\">..</a>" + NL).getBytes( "UTF-8" ));
								}

								List<String[]>	filenames		= new ArrayList<>( kids.size());
								int				max_filename	= 0;

								int MAX_LEN = 120;

								for ( Object	entry: kids ){

									DiskManagerFileInfo		file;
									String					file_name;

									if ( entry instanceof String ){

										file = null;

										file_name	= (String)entry;

									}else{

										file = (DiskManagerFileInfo)entry;

										if ( file.isSkipped()){

											continue;
										}

										file_name 	= file.getTorrentFile().getRelativePath();

										int pos = file_name.lastIndexOf( File.separatorChar );

										if ( pos != -1 ){

											file_name = file_name.substring( pos+1 );
										}
									}

									String url			= root_url + UrlUtils.encode( file_name );

									if ( file == null ){

										file_name += "/";
									}

									int len = file_name.length();

									if ( len > MAX_LEN ){

										file_name = file_name.substring( 0, MAX_LEN-3 ) + "...";

										len = file_name.length();
									}

									if ( len > max_filename ){

										max_filename = len;
									}

									filenames.add( new String[]{ url, file_name, file==null?"":DisplayFormatters.formatByteCountToKiBEtc( file.getLength())});
								}

								max_filename = ((max_filename + 15 )/8)*8;

								char[]	padding = new char[max_filename];

								Arrays.fill( padding, ' ' );

								Collections.sort(
									filenames,
									new Comparator<String[]>()
									{
										Comparator comp = new FormattersImpl().getAlphanumericComparator(true);

										@Override
										public int
										compare(
											String[] o1,
											String[] o2)
										{
											return( comp.compare(o1[0], o2[0] ));
										}
									});

								for ( String[] entry: filenames ){

									String file_name = entry[1];

									int	len = file_name.length();

									StringBuilder line = new StringBuilder( max_filename + 64 );

									line.append("<a href=\"").append(entry[0]).append("\">").append(XUXmlWriter.escapeXML(file_name)).append("</a>");

									line.append( padding, 0, max_filename - len );

									line.append(  entry[2] );

									line.append( NL );

									os.write( line.toString().getBytes( "UTF-8" ));
								}

								os.write((
								"  <hr></pre>" + NL +
								"  <address>" + Constants.APP_NAME + " Web Server at " + host + " Port " + getServerPort() +"</address>" + NL +
								" </body>" + NL +
								"</html>" ).getBytes( "UTF-8" ));

								return( true );

							}else{

								DiskManagerFileInfo dm_file = (DiskManagerFileInfo)file_node;

								long	file_size = dm_file.getLength();

								File target_file = dm_file.getFile( true );

								boolean done = 	dm_file.getDownloaded() == file_size &&
												target_file.length() == file_size;


								String	file_type;

									// Use the original torrent file name when deducing file type to
									// avoid incomplete suffix issues etc

								String relative_path = dm_file.getTorrentFile().getRelativePath();

								int	pos = relative_path.lastIndexOf( "." );

								if ( pos == -1 ){

									file_type = "";

								}else{

									file_type = relative_path.substring(pos+1);
								}

									// for big files see if we can hand off all processing to the
									// media server

								if ( file_size >= 512*1024 ){

									String content_type = HTTPUtils.guessContentTypeFromFileType( file_type );

									if ( 	content_type.startsWith( "text/" ) ||
											content_type.startsWith( "image/" )){

										// don't want to be redirecting here as (for example) .html needs
										// to remain in the 'correct' place so that relative assets work

									}else{

										URL stream_url = getMediaServerContentURL( dm_file );

										if ( stream_url != null ){

											OutputStream os = response.getRawOutputStream();

											os.write((
												"HTTP/1.1 302 Found" + NL +
												"Location: " + stream_url.toExternalForm() + NL +
												NL ).getBytes( "UTF-8" ));

											return( true );
										}
									}
								}


								if ( done ){

									if ( file_size < 512*1024 ){

										FileInputStream	fis = null;

										try{
											fis = new FileInputStream(target_file);

											response.useStream( file_type, fis );

											return( true );

										}finally{

											if ( fis != null ){

												fis.close();
											}
										}
									}else{


										OutputStream 	os 	= null;
										InputStream 	is	= null;

										try{
											os = response.getRawOutputStream();

											os.write((
												"HTTP/1.1 200 OK" + NL +
												"Content-Type:" + HTTPUtils.guessContentTypeFromFileType( file_type ) + NL +
												"Content-Length: " + file_size + NL +
												"Connection: close" + NL + NL ).getBytes( "UTF-8" ));

											byte[] buffer = new byte[128*1024];

											is = new FileInputStream( target_file );

											while( true ){

												int len = is.read( buffer );

												if ( len <= 0 ){

													break;
												}

												os.write( buffer, 0, len );
											}
										}catch( Throwable e ){

											//e.printStackTrace();

										}finally{

											try{
												os.close();

											}catch( Throwable e ){
											}

											try{
												is.close();

											}catch( Throwable e ){
											}


										}

										return( true );
									}

								}else{

									dm_file.setPriority(10);

									try{
										final OutputStream os = response.getRawOutputStream();

										os.write((
												"HTTP/1.1 200 OK" + NL +
												"Content-Type:" + HTTPUtils.guessContentTypeFromFileType( file_type ) + NL +
												"Content-Length: " + file_size + NL +
												"Connection: close" + NL +
												"X-Vuze-Hack: X" ).getBytes( "UTF-8" ));

										DiskManagerChannel chan = PluginCoreUtils.wrap( dm_file ).createChannel();

										try{
											final DiskManagerRequest req = chan.createRequest();

											final boolean[] header_complete = { false };
											final long[]	last_write		= { 0 };

											req.setOffset( 0 );
											req.setLength( file_size );

											req.addListener(
												new DiskManagerListener()
												{
													@Override
													public void
													eventOccurred(
														DiskManagerEvent	event )
													{
														int	type = event.getType();

														if ( type ==  DiskManagerEvent.EVENT_TYPE_BLOCKED ){

															return;

														}else if ( type == DiskManagerEvent.EVENT_TYPE_FAILED ){

															throw( new RuntimeException( event.getFailure()));
														}

														PooledByteBuffer buffer = event.getBuffer();

														if ( buffer == null ){

															throw( new RuntimeException( "eh?" ));
														}

														try{

															boolean	do_header = false;

															synchronized( header_complete ){

																if ( !header_complete[0] ){

																	do_header = true;

																	header_complete[0] = true;
																}

																last_write[0] = SystemTime.getMonotonousTime();
															}

															if ( do_header ){

																os.write((NL+NL).getBytes( "UTF-8" ));
															}

															byte[] data = buffer.toByteArray();

															os.write( data );

														}catch( IOException e ){

															throw( new RuntimeException( "Failed to write to " + file, e ));

														}finally{

															buffer.returnToPool();
														}
													}
												});

											final TimerEventPeriodic timer_event [] = {null};

											timer_event[0] =
												SimpleTimer.addPeriodicEvent(
													"KeepAlive",
													10*1000,
													new TimerEventPerformer()
													{
														boolean	cancel_outstanding = false;

														@Override
														public void
														perform(
															TimerEvent event)
														{
															if ( cancel_outstanding ){

																req.cancel();

															}else{

																synchronized( header_complete ){

																	if ( header_complete[0] ){

																		if ( SystemTime.getMonotonousTime() - last_write[0] >= 5*60*1000 ){

																			req.cancel();
																		}
																	}else{

																		try{
																			os.write( "X".getBytes( "UTF-8" ));

																			os.flush();

																		}catch( Throwable e ){

																			req.cancel();
																		}
																	}
																}

																if ( !response.isActive()){

																	cancel_outstanding = true;
																}
															}
														}
													});

											try{
												req.run();

											}finally{

												timer_event[0].cancel();
											}

											return( true );

										}finally{

											chan.destroy();
										}
									}catch( Throwable e ){

										return( false );
									}
								}
							}
						}

						private boolean
						handleRedirect(
							DownloadManager				dm,
							File						torrent_file,
							int							file_index,
							String						file_name,
							List<String>				networks,
							TrackerWebPageRequest		request,
							TrackerWebPageResponse		response )
						{
							try{
								TOTorrent torrent = TOTorrentFactory.deserialiseFromBEncodedFile( torrent_file  );

								GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();

								UIFunctions uif = UIFunctionsManager.getUIFunctions();

								TorrentOpenOptions torrent_options = new TorrentOpenOptions( torrent_file.getAbsolutePath(), torrent, false, null  );

								torrent_options.setTorrent( torrent );

								String[] existing_nets;

								if ( networks.size() == 0 ){

										// inherit networks from parent

									existing_nets = dm.getDownloadState().getNetworks();

								}else{

									existing_nets = networks.toArray( new String[ networks.size() ]);
								}

								for ( String net: AENetworkClassifier.AT_NETWORKS ){

									boolean found = false;

									for ( String x: existing_nets ){

										if ( net == x ){

											found = true;

											break;
										}
									}

									torrent_options.setNetworkEnabled( net, found );
								}

								Map<String,Object>	add_options = new HashMap<>();

								add_options.put( UIFunctions.OTO_SILENT,  true );

								if ( uif.addTorrentWithOptions( torrent_options, add_options )){

									long start = SystemTime.getMonotonousTime();

									while( true ){

										DownloadManager o_dm = gm.getDownloadManager( torrent );

										if ( o_dm != null ){

											if ( !o_dm.getDownloadState().getFlag( DownloadManagerState.FLAG_METADATA_DOWNLOAD )){

												DiskManagerFileInfo[] files = o_dm.getDiskManagerFileInfoSet().getFiles();

												DiskManagerFileInfo o_dm_file = null;

												if ( file_name != null ){

													for ( DiskManagerFileInfo file: files ){

														String path = file.getTorrentFile().getRelativePath();

														if ( path.equals( file_name )){

															o_dm_file = file;

															break;
														}
													}

													if ( o_dm_file == null ){

														o_dm_file = files[0] ;
													}
												}else{

													if ( file_index < 0 ){

														long	largest = -1;

														for ( DiskManagerFileInfo file: files ){

															if ( file.getLength() > largest ){

																o_dm_file = file;

																largest = file.getLength();
															}
														}
													}else{

														o_dm_file = files[ file_index ];
													}
												}

												String original_path = request.getAbsoluteURL().getPath();

												if ( original_path.endsWith( ".html" )){

													String url = browse( o_dm, file_index<0?null:o_dm_file, anon, false );

													OutputStream os = response.getRawOutputStream();

													os.write((
														"HTTP/1.1 302 Found" + NL +
														"Location: " + url + NL +
														NL ).getBytes( "UTF-8" ));

													return( true );

												}else{

													URL stream_url = getMediaServerContentURL( o_dm_file );

													if ( stream_url != null ){

														OutputStream os = response.getRawOutputStream();

														os.write((
															"HTTP/1.1 302 Found" + NL +
															"Location: " + stream_url.toExternalForm() + NL +
															NL ).getBytes( "UTF-8" ));

														return( true );
													}
												}
											}
										}

										long now = SystemTime.getMonotonousTime();

										if ( now - start > 3*60*1000 ){

											Debug.out( "Timeout waiting for download to be added" );

											return( false );
										}

										Thread.sleep(1000);
									}
								}else{

									Debug.out( "Failed to add download for some reason" );

									return( false );
								}

							}catch( Throwable e ){

								Debug.out( e );

								return( false );
							}
						}

						@Override
						public void
						unload()
							throws PluginException
						{
							synchronized( browse_plugins ){

								 browse_plugins.remove( dm );
							}

							super.unload();
						}
					};

				PluginManager.registerPlugin(
					plugin,
					plugin_id,
					plugin_id );

				browse_plugins.put( dm, plugin );

				if ( launch ){

					return( null );

				}else{

					waiter.reserve( 10*1000 );

					synchronized( url_holder ){

						return( url_holder[0] );
					}
				}
			}else{

				String protocol = plugin.getProtocol();

				InetAddress	bind_ip = plugin.getServerBindIP();

				String	host;

				if ( bind_ip.isAnyLocalAddress()){

					host = "127.0.0.1";

				}else{

					host = bind_ip.getHostAddress();
				}

				String url = protocol + "://" + host+ ":" + plugin.getServerPort() + "/" + url_suffix;

				if ( launch ){

					Utils.launch( url, false, true, anon );

					return( null );

				}else{

					return( url );
				}
			}
		}
	}

	public static URL
	getMediaServerContentURL(
		DiskManagerFileInfo file )
	{
		PluginManager pm = CoreFactory.getSingleton().getPluginManager();

		PluginInterface pi = pm.getPluginInterfaceByID( "azupnpav", false );

		if ( pi == null ){

			return( null );
		}

		if ( !pi.getPluginState().isOperational()){

			return( null );
		}

		try{
			Object	url = pi.getIPC().invoke( "getContentURL", new Object[]{ PluginCoreUtils.wrap( file )});

			if ( url instanceof String ){

				String s_url = (String)url;

				if ( s_url.length() > 0 ){

					return( new URL( s_url ));
				}
			}
		}catch ( Throwable e ){

			e.printStackTrace();
		}

		return( null );
	}

	private static class
	UnloadableWebPlugin
		extends WebPlugin
		implements UnloadablePlugin
	{
		private
		UnloadableWebPlugin(
			Properties		props )
		{
			super( props );
		}

		@Override
		public void
		unload()
			throws PluginException
		{
			super.unloadPlugin();
		}
	}

  public static boolean isStartable(DownloadManager dm) {
    if(dm == null)
      return false;
    int state = dm.getState();
    if (state != DownloadManager.STATE_STOPPED) {
      return false;
    }
    return true;
  }

  public static boolean isStopable(DownloadManager dm) {
    if(dm == null)
      return false;
    int state = dm.getState();
    if (	state == DownloadManager.STATE_STOPPED ||
    		state == DownloadManager.STATE_STOPPING	) {
    	if ( dm.isPaused()) {
    		return( true );
    	}
      return false;
    }
    return true;
  }

  public static boolean isPauseable(DownloadManager dm) {
	    if(dm == null)
	      return false;
	    int state = dm.getState();
	    if (	// 	state == DownloadManager.STATE_STOPPED || decided to allow pausing of a stopped torrent
	    		dm.isPaused() ||
	    		state == DownloadManager.STATE_STOPPING	||
	    		state == DownloadManager.STATE_ERROR ) {
	      return false;
	    }
	    return true;
	  }

  public static boolean isStopped(DownloadManager dm) {
	    if(dm == null)
	      return false;
	    int state = dm.getState();
	    if (	state == DownloadManager.STATE_STOPPED ||
	    		state == DownloadManager.STATE_ERROR	) {
	      return true;
	    }
	    return false;
	  }

  public static boolean
  isForceStartable(
  	DownloadManager	dm )
  {
    if(dm == null){
        return false;
  	}

    int state = dm.getState();

    if (	state != DownloadManager.STATE_STOPPED && state != DownloadManager.STATE_QUEUED &&
            state != DownloadManager.STATE_SEEDING && state != DownloadManager.STATE_DOWNLOADING){

    	return( false );
    }

    return( true );
  }

  /**
   * Host a DownloadManager on our Tracker.
   * <P>
   * Doesn't require SWT Thread
   */
  public static void
  host(
  	Core core,
	DownloadManager dm)
 {
		if (dm == null) {
			return;
		}

		TOTorrent torrent = dm.getTorrent();
		if (torrent == null) {
			return;
		}

		try {
			core.getTrackerHost().hostTorrent(torrent, true, false);
		} catch (TRHostException e) {
			MessageBoxShell mb = new MessageBoxShell(
					SWT.ICON_ERROR | SWT.OK,
					MessageText.getString("MyTorrentsView.menu.host.error.title"),
					MessageText.getString("MyTorrentsView.menu.host.error.message").concat(
							"\n").concat(e.toString()));
			mb.open(null);
		}
	}

  /**
   * Publish a DownloadManager on our Tracker.
   * <P>
   * Doesn't require SWT Thread
   */
  public static void
  publish(
  		Core core,
		DownloadManager dm)
 {
		if (dm == null) {
			return;
		}

		TOTorrent torrent = dm.getTorrent();
		if (torrent == null) {
			return;
		}

		try {
			core.getTrackerHost().publishTorrent(torrent);
		} catch (TRHostException e) {
			MessageBoxShell mb = new MessageBoxShell(
					SWT.ICON_ERROR | SWT.OK,
					MessageText.getString("MyTorrentsView.menu.host.error.title"),
					MessageText.getString("MyTorrentsView.menu.host.error.message").concat(
							"\n").concat(e.toString()));
			mb.open(null);
		}
	}


  public static void
  start(
  		DownloadManager dm)
  {
    if (dm != null && dm.getState() == DownloadManager.STATE_STOPPED) {

      dm.setStateWaiting();
    }
  }

  public static void
  queue(
  		DownloadManager dm,
		Composite panelNotUsed)
  {
    if (dm != null) {
    	if (dm.getState() == DownloadManager.STATE_STOPPED){

    		dm.setStateQueued();

    		/* parg - removed this - why would we want to effectively stop + restart
    		 * torrents that are running? This is what happens if the code is left in.
    		 * e.g. select two torrents, one stopped and one downloading, then hit "queue"

    		 }else if (	dm.getState() == DownloadManager.STATE_DOWNLOADING ||
    				dm.getState() == DownloadManager.STATE_SEEDING) {

    			stop(dm,panel,DownloadManager.STATE_QUEUED);
    		*/
      }
    }
  }

  public static void pause(DownloadManager dm, Shell shell) {
		if (dm == null) {
			return;
		}

		int state = dm.getState();

		if (	// state == DownloadManager.STATE_STOPPED ||	decided to allow pausing of stopped downloads
				dm.isPaused() ||
				state == DownloadManager.STATE_STOPPING ){
			
			return;
		}

		asyncPause(dm);
  }

  public static void stop(DownloadManager dm, Shell shell) {
  	stop(dm, shell, DownloadManager.STATE_STOPPED);
  }

	public static void stop(final DownloadManager dm, final Shell shell,
			final int stateAfterStopped) {
		if (dm == null) {
			return;
		}

		int state = dm.getState();

		if (state == DownloadManager.STATE_STOPPED
				|| state == DownloadManager.STATE_STOPPING
				|| state == stateAfterStopped) {
			
			if ( !dm.isPaused()){
				return;
			}
		}

		boolean stopme = true;
		if (state == DownloadManager.STATE_SEEDING) {

			if (dm.getStats().getShareRatio() >= 0
					&& dm.getStats().getShareRatio() < 1000
					&& COConfigurationManager.getBooleanParameter("Alert on close", false)) {
				if (!Utils.isThisThreadSWT()) {
					Utils.execSWTThread(new AERunnable() {
						@Override
						public void runSupport() {
							stop(dm, shell, stateAfterStopped);
						}
					});
					return;
				}
				Shell aShell = shell == null ? Utils.findAnyShell() : shell;
				MessageBox mb = new MessageBox(aShell, SWT.ICON_WARNING
						| SWT.YES | SWT.NO);
				mb.setText(MessageText.getString("seedmore.title"));
				mb.setMessage(MessageText.getString("seedmore.shareratio")
						+ (dm.getStats().getShareRatio() / 10) + "%.\n"
						+ MessageText.getString("seedmore.uploadmore"));
				int action = mb.open();
				stopme = action == SWT.YES;
			}
		}

		if (stopme) {
			asyncStop(dm, stateAfterStopped);
		}
	}

	private static AsyncDispatcher async = new AsyncDispatcher(2000);

  public static void asyncStopDelete(final DownloadManager dm,
		  final int stateAfterStopped, final boolean bDeleteTorrent,
		  final boolean bDeleteData, final AERunnable deleteFailed) {

	  DownloadManager[] dms = { dm };
	  
	  TorrentUtils.startTorrentDelete( dms );

	  final boolean[] endDone = { false };

	  try{
		  async.dispatch(new AERunnable() {
			  @Override
			  public void runSupport() {

				  try {
					  // I would move the FLAG_DO_NOT_DELETE_DATA_ON_REMOVE even deeper
					  // but I fear what could possibly go wrong.
					  boolean reallyDeleteData = bDeleteData
							  && !dm.getDownloadState().getFlag(
									  Download.FLAG_DO_NOT_DELETE_DATA_ON_REMOVE);

					  dm.getGlobalManager().removeDownloadManager(dm, bDeleteTorrent,
							  reallyDeleteData);
				  } catch (GlobalManagerDownloadRemovalVetoException f) {

					  // see if we can delete a corresponding share as users frequently share
					  // stuff by mistake and then don't understand how to delete the share
					  // properly

					  try{
						  PluginInterface pi = CoreFactory.getSingleton().getPluginManager().getDefaultPluginInterface();

						  ShareManager sm = pi.getShareManager();

						  Tracker	tracker = pi.getTracker();

						  ShareResource[] shares = sm.getShares();

						  TOTorrent torrent = dm.getTorrent();

						  byte[] target_hash = torrent.getHash();

						  for ( ShareResource share: shares ){

							  int type = share.getType();

							  byte[] hash;

							  if ( type == ShareResource.ST_DIR ){

								  hash = ((ShareResourceDir)share).getItem().getTorrent().getHash();

							  }else if ( type == ShareResource.ST_FILE ){

								  hash = ((ShareResourceFile)share).getItem().getTorrent().getHash();

							  }else{

								  hash = null;
							  }

							  if ( hash != null ){

								  if ( Arrays.equals( target_hash, hash )){

									  try{
										  dm.stopIt( DownloadManager.STATE_STOPPED, false, false );

									  }catch( Throwable e ){
									  }


									  try{
										  TrackerTorrent	tracker_torrent = tracker.getTorrent( PluginCoreUtils.wrap( torrent ));

										  if ( tracker_torrent != null ){

											  tracker_torrent.stop();
										  }
									  }catch( Throwable e ){
									  }

									  share.delete();

									  return;
								  }
							  }
						  }

					  }catch( Throwable e ){

					  }

					  if (!f.isSilent()) {
						  UIFunctionsManager.getUIFunctions().forceNotify(
								  UIFunctions.STATUSICON_WARNING,
								  MessageText.getString( "globalmanager.download.remove.veto" ),
								  f.getMessage(), null, null, -1 );

						  //Logger.log(new LogAlert(dm, false,
						  //		"{globalmanager.download.remove.veto}", f));
					  }
					  if (deleteFailed != null) {
						  deleteFailed.runSupport();
					  }
				  } catch (Exception ex) {
					  Debug.printStackTrace(ex);
					  if (deleteFailed != null) {
						  deleteFailed.runSupport();
					  }
				  }finally{

					  synchronized( endDone ){

						  if ( !endDone[0] ){

							  TorrentUtils.endTorrentDelete( dms );

							  endDone[0] = true;
						  }
					  }
				  }
			  }
		  });

	  }catch( Throwable e ){

		  synchronized( endDone ){

			  if ( !endDone[0] ){

				  TorrentUtils.endTorrentDelete( dms );

				  endDone[0] = true;
			  }
		  }

		  Debug.out( e );
	  }
  }

  	public static void
	asyncStop(
		final DownloadManager	dm,
		final int 				stateAfterStopped )
  	{
    	async.dispatch(new AERunnable() {
    		@Override
		    public void
			runSupport()
    		{
    			if ( dm.isPaused()){
    				CoreWaiterSWT.waitForCore(TriggerInThread.NEW_THREAD,
    						new CoreRunningListener() {
    							@Override
    							public void coreRunning(Core core) {
    								dm.stopPausedDownload();
    							}
    						});
    			}else{
    				
    				dm.stopIt( stateAfterStopped, false, false );
    			}
    		}
		});
  	}

 	public static void
	asyncPause(
		final DownloadManager	dm )
  	{
    	async.dispatch(new AERunnable() {
    		@Override
		    public void
			runSupport()
    		{
    			dm.pause( false );
    		}
		});
  	}

	public static void asyncStartAll() {
		CoreWaiterSWT.waitForCore(TriggerInThread.NEW_THREAD,
				new CoreRunningListener() {
					@Override
					public void coreRunning(Core core) {
						core.getGlobalManager().startAllDownloads();
					}
				});
	}

	public static void asyncStopAll() {
		CoreWaiterSWT.waitForCore(TriggerInThread.NEW_THREAD,
				new CoreRunningListener() {
					@Override
					public void coreRunning(Core core) {
						core.getGlobalManager().stopAllDownloads();
					}
				});
	}

	public static void asyncPause() {
		CoreWaiterSWT.waitForCore(TriggerInThread.NEW_THREAD,
				new CoreRunningListener() {
					@Override
					public void coreRunning(Core core) {
						core.getGlobalManager().pauseDownloads();
					}
				});
	}

	public static void asyncPauseForPeriod( final int seconds ) {
		CoreWaiterSWT.waitForCore(TriggerInThread.NEW_THREAD,
				new CoreRunningListener() {
					@Override
					public void coreRunning(Core core) {
						core.getGlobalManager().pauseDownloadsForPeriod(seconds);
					}
				});
	}
	public static void asyncResume() {
		CoreWaiterSWT.waitForCore(TriggerInThread.NEW_THREAD,
				new CoreRunningListener() {
					@Override
					public void coreRunning(Core core) {
						core.getGlobalManager().resumeDownloads();
					}
				});
	}

	public static void
	asyncPauseForPeriod(
		final List<DownloadManager>		dms,
		final int 						seconds )
	{
		CoreWaiterSWT.waitForCore(TriggerInThread.NEW_THREAD,
				new CoreRunningListener() {
					@Override
					public void coreRunning(Core core)
					{

						final List<DownloadManager>		paused = new ArrayList<>();

						final DownloadManagerListener listener =
							new DownloadManagerAdapter()
							{
								@Override
								public void
								stateChanged(
									DownloadManager 	manager,
									int 				state )
								{
									synchronized( paused ){

										if ( !paused.remove( manager )){

											return;
										}
									}

									manager.removeListener( this );
								}
							};

						final long target_time = SystemTime.getOffsetTime( seconds*1000);

						String time_str = new SimpleDateFormat( "HH:mm:ss" ).format( new Date( target_time ));
						
						String reason = MessageText.getString( "label.resuming.at", new String[] { time_str });
							
						for ( DownloadManager dm: dms ){

							if ( !dm.isPaused() && !isPauseable( dm )){

								continue;
							}

							if ( dm.pause( false, target_time )){

								dm.setStopReason( reason );
								
								synchronized( paused ){

									paused.add( dm );
								}

								dm.addListener( listener, false );
							}
						}

						if ( paused.size() > 0 ){

							SimpleTimer.addEvent(
								"ManagerUtils.resumer",
								target_time,
								new TimerEventPerformer()
								{
									@Override
									public void
									perform(
										TimerEvent event )
									{
										List<DownloadManager>	to_resume = new ArrayList<>();

										synchronized( paused ){

											to_resume.addAll( paused );

											paused.clear();
										}

										for ( DownloadManager dm: to_resume ){

											dm.removeListener( listener );

											try{
												if ( dm.getAutoResumeTime() == target_time ){
												
													dm.resume();
												}
											}catch( Throwable e ){

												Debug.out( e );
											}
										}
									}
								});
						}
					}
				});
	}

	public static class
	ArchiveCallback
	{
		public void
		success(
			DownloadStub		source,
			DownloadStub		target )
		{
		}

		public void
		failed(
			DownloadStub		original,
			Throwable			error )
		{
		}

		public void
		completed()
		{
		}
	}


	public static void
	moveToArchive(
		final List<Download>	downloads,
		ArchiveCallback			_run_when_complete )
	{
		final ArchiveCallback run_when_complete=_run_when_complete==null?new ArchiveCallback():_run_when_complete;

		Utils.getOffOfSWTThread(
			new AERunnable() {

				@Override
				public void
				runSupport()
				{
					try{
						String title 	= MessageText.getString( "archive.info.title" );
						String text 	= MessageText.getString( "archive.info.text" );

						MessageBoxShell prompter =
							new MessageBoxShell(
								title, text,
								new String[] { MessageText.getString("Button.ok") }, 0 );


						String remember_id = "managerutils.archive.info";

						prompter.setRemember(
							remember_id,
							true,
							MessageText.getString("MessageBoxWindow.nomoreprompting"));

						prompter.setAutoCloseInMS(0);

						prompter.open( null );

						prompter.waitUntilClosed();

						for ( Download dm: downloads ){

							try{
								DownloadStub stub = dm.stubbify();

								run_when_complete.success( dm, stub );

							}catch( DownloadRemovalVetoException e ){

								run_when_complete.failed( dm, e );

								if (!e.isSilent()) {
									  UIFunctionsManager.getUIFunctions().forceNotify(
											  UIFunctions.STATUSICON_ERROR,
											  MessageText.getString( "globalmanager.download.remove.veto" ),
											  e.getMessage(), dm.getName(), new Object[]{ dm }, -1 );
								}
							}catch( Throwable e ){

								run_when_complete.failed( dm, e );

								Debug.out( e );
							}
						}
					}finally{

						run_when_complete.completed();
					}
				}
			});
	}

	public static void
	restoreFromArchive(
		final List<DownloadStub>		downloads,
		final boolean					start,
		ArchiveCallback					_run_when_complete )
	{
		final ArchiveCallback run_when_complete=_run_when_complete==null?new ArchiveCallback():_run_when_complete;

		Utils.getOffOfSWTThread(
			new AERunnable() {

				@Override
				public void
				runSupport()
				{
					try{
						TagManager	tag_manager		= null;
						Tag			tag_restored	= null;

						try{
							tag_manager = TagManagerFactory.getTagManager();

							TagType tt = tag_manager.getTagType( TagType.TT_DOWNLOAD_MANUAL );

							String tag_name = MessageText.getString( "label.restored" );

							tag_restored = tt.getTag( tag_name, true );

							if ( tag_restored == null ){

								tag_restored = tt.createTag( tag_name, true );

								tag_restored.setPublic( false );
							}
						}catch( Throwable e ){

							Debug.out( e );
						}

						try{
							if ( tag_manager != null ){

								tag_manager.setProcessingEnabled( false );
							}

							for ( DownloadStub dm: downloads ){

								try{
									Download dl = dm.destubbify();

									if ( dl != null ){

										run_when_complete.success( dm, dl );

										if ( tag_restored != null ){

											tag_restored.addTaggable(PluginCoreUtils.unwrap( dl ));
										}

										if ( start ){

											start( PluginCoreUtils.unwrap( dl ));
										}
									}else{

										run_when_complete.failed( dm, new Exception( "Unknown error" ));
									}

								}catch( Throwable e ){

									run_when_complete.failed( dm, e );

									Debug.out( e );
								}
							}
						}finally{

							if ( tag_manager != null ){

								tag_manager.setProcessingEnabled( true );
							}
						}

					}finally{

						run_when_complete.completed();
					}
				}
			});
	}

	public static DownloadManager[]
	cleanUp(
		DownloadManager[]	dms )
	{
		List<DownloadManager>	result = new ArrayList<>();

		if ( dms != null ){

			for ( DownloadManager dm: dms ){

				if ( dm != null && !dm.isDestroyed()){

					result.add( dm );
				}
			}
		}

		return( result.toArray( new DownloadManager[ result.size()]));
	}

	public static void
	locateFiles(
		final DownloadManager[]		dms,
		Shell						shell )
	{
		locateFiles( dms, null, shell );
	}

	public static void
	locateFiles(
		final DownloadManager[]			dms,
		final DiskManagerFileInfo[][]	dm_files,
		Shell							shell )
	{
		if ( !Utils.isSWTThread()){
			
			Utils.execSWTThread( ()->{ locateFiles( dms, dm_files, shell );});
			
			return;
		}
		
		boolean all_bad 	= true;
		boolean some_bad 	= false;
			
		for ( DownloadManager dm: dms ){
			
			int dm_state = dm.getState();

			if ( !( dm_state == DownloadManager.STATE_STOPPED || dm_state == DownloadManager.STATE_ERROR )){
				
				some_bad = true;
				
			}else{
				
				all_bad = false;
			}
		}
		
		if ( all_bad ){
			
			MessageBoxShell mb = new MessageBoxShell(
				MessageText.getString( "dlg.finddatafiles.title" ),
				MessageText.getString( "dlg.finddatafiles.dms.all.bad" ));
			
			mb.setButtons( new String[] { MessageText.getString("Button.ok") });				

			mb.setIconResource( "error" );
			
			mb.open((result)->{
				
			});
			
		}else if ( some_bad ){
			
			MessageBoxShell mb = new MessageBoxShell(
					MessageText.getString( "dlg.finddatafiles.title" ),
					MessageText.getString( "dlg.finddatafiles.dms.some.bad" ));
				
				mb.setButtons(0, new String[] {
						MessageText.getString("Button.yes"),
						MessageText.getString("Button.no"),
					}, new Integer[] {
						0,
						1
					});

				mb.setIconResource( "warning" );
				
				mb.open((result)->{
					
					if ( result == 0 ){
						
						locateFilesSupport( dms, dm_files, null, null, shell );
					}
				});
		}else{
			
			locateFilesSupport( dms, dm_files, null, null, shell );
		}
	}
	
	private static final int LOCATE_MODE_LINK		= 0;
	private static final int LOCATE_MODE_COPY		= 1;
	private static final int LOCATE_MODE_MOVE		= 2;
	private static final int LOCATE_MODE_PIECE		= 3;
	private static final int LOCATE_MODE_RELOCATE	= 4;
	
	private static final int LOCATE_MODE_LINK_BLANK		= 0;
	private static final int LOCATE_MODE_LINK_INTERNAL	= 1;
	private static final int LOCATE_MODE_LINK_HARD		= 2;

	private static void
	locateFilesSupport(
		DownloadManager[]				dms,
		DiskManagerFileInfo[][]			dm_files,
		List<TorrentOpenOptions>		torrents,
		Consumer<File[]>				listener,
		Shell							shell )
	{
		ClassLoader loader = null;
		
		if ( Constants.isCVSVersion()){
	
			try{
					// allow skin file reloading to test
				
				File file = new File( "C:\\Users\\Paul\\git\\BiglyBT\\uis\\src");
				
				if ( file.exists()){
				
					URL[] urls = { file.toURI().toURL() };
				
					loader = new URLClassLoader( urls );
				}
			}catch( Throwable e ){
		
				Debug.out( e );
			}
		}
		
		final SkinnedDialog dialog = new SkinnedDialog( loader, "skin3_dlg_findfiles", "shell", shell,  SWT.DIALOG_TRIM | SWT.RESIZE );
		
		dialog.setTitle( MessageText.getString(dms!=null?"dlg.finddatafiles.title":"dlg.findsavelocations.title" ));
		
		SWTSkin skin = dialog.getSkin();

		SWTSkinObjectList so_def_locs = (SWTSkinObjectList) skin.getSkinObject( "roots-list" );
		
		org.eclipse.swt.widgets.List def_locs = so_def_locs.getListControl();

		
		List<String> roots = COConfigurationManager.getStringListParameter( "find.files.search.roots" );
		
		if ( !roots.isEmpty()){
			
			def_locs.setItems( roots.toArray( new String[ roots.size()] ));
		}
		
		SWTSkinObjectTextbox so_exp_text	= (SWTSkinObjectTextbox)skin.getSkinObject( "exp-text" );
		
		SWTSkinObjectButton so_exp_but 	= (SWTSkinObjectButton)skin.getSkinObject( "exp-but" );
		SWTSkinObjectButton so_add_but	= (SWTSkinObjectButton)skin.getSkinObject( "add-but" );

		SWTSkinObjectCheckbox	so_use_def	= (SWTSkinObjectCheckbox)skin.getSkinObject( "use-def" );
		
		SWTSkinObject			so_mode_label	= skin.getSkinObject( "mode-label" );

		SWTSkinObjectContainer	so_mode		= (SWTSkinObjectContainer)skin.getSkinObject( "mode" );
		
		SWTSkinObjectCheckbox	so_include_skipped	= (SWTSkinObjectCheckbox)skin.getSkinObject( "skip-inc" );

		Composite c_mode = so_mode.getComposite();
		c_mode.setLayoutData( Utils.getFilledFormData());
		
		FormLayout layout = new FormLayout();
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		c_mode.setLayout( layout);
		
			// mode 
		
		Combo mode_combo = new Combo( c_mode, SWT.READ_ONLY );
		FormData	fd = new FormData();
		fd.top=new FormAttachment(c_mode,0);
		fd.bottom=new FormAttachment(100);
		mode_combo.setLayoutData( fd );
		
		mode_combo.setItems(
				MessageText.getString( "label.link" ) + "    ",	// shitty layout doesn't give sufficient width for all options
				MessageText.getString( "label.copy" ) + "    ",
				MessageText.getString( "label.move" ) + "    ",
				MessageText.getString( "Peers.column.piece" ) + "    ",
				MessageText.getString( "label.relocate" ) + "    "
			);
		
			// link mode
		
		Combo link_combo = new Combo( c_mode, SWT.READ_ONLY );
		fd = new FormData();
		fd.top=new FormAttachment(c_mode,0);
		fd.bottom=new FormAttachment(100);
		fd.left=new FormAttachment(mode_combo,2 );
		link_combo.setLayoutData( fd );
		
		link_combo.setItems(
				"    ",	
				MessageText.getString( "tag.type.internal" ) + "    ",	// shitty layout doesn't give sufficient width for all options
				MessageText.getString( "label.hard" ) + "    " );
			
		Utils.setTT( link_combo, MessageText.getString( "label.link.type" ));
		
			// tolerance
		
		CLabel tolerance = new CLabel( c_mode, SWT.CENTER );
		fd = new FormData();
		fd.top=new FormAttachment(c_mode,0);
		fd.bottom=new FormAttachment(100,-2);
		fd.left=new FormAttachment(link_combo );
		tolerance.setLayoutData( fd );
		tolerance.setText( MessageText.getString( "label.tolerance.pct" ));
		
		Spinner tolerance_spinner = new Spinner(c_mode, SWT.BORDER | SWT.RIGHT);
		fd = new FormData();
		fd.top=new FormAttachment(c_mode,0);
		fd.bottom=new FormAttachment(100);
		fd.left=new FormAttachment(tolerance,2);
		fd.right=new FormAttachment(100);
		
		tolerance_spinner.setLayoutData( fd );
		tolerance_spinner.setMinimum( 0 );
		tolerance_spinner.setMaximum( 10 );
				
		SWTSkinObjectContainer soButtonArea = (SWTSkinObjectContainer)skin.getSkinObject("bottom-area");
		
		StandardButtonsArea buttonsArea = new StandardButtonsArea() {
		
			@Override
			protected void 
			clicked(int buttonValue) 
			{
				if (buttonValue == SWT.OK) {

					String[] roots;
					
					if ( so_use_def.isChecked()){
					
						roots = def_locs.getItems();
					}else{
						
						roots = new String[]{ so_exp_text.getText().trim() };
					}
					
					int mode = mode_combo.getSelectionIndex();
					
					int link_type = link_combo.getSelectionIndex();
					
					int tolerance = tolerance_spinner.getSelection();
					
					boolean include_skipped = so_include_skipped.isChecked();
					
					dialog.close();
					
					Utils.execSWTThreadLater(
						1,
						new Runnable()
						{
							public void
							run()
							{
								if ( dms != null ){
								
									locateFiles( dms, dm_files, shell, roots, mode, link_type, tolerance, include_skipped );
									
								}else{
									
									locateSavePaths( torrents, shell, roots, listener );
								}
							}
						});
					
				}else{

					dialog.close();
				}
			}
		};
		
		buttonsArea.setButtonIDs(new String[] {
			MessageText.getString("Button.search"),
			MessageText.getString("Button.cancel")
		});
		
		buttonsArea.setButtonVals(new Integer[] {
			SWT.OK,
			SWT.CANCEL
		});
		
		buttonsArea.swt_createButtons(((SWTSkinObjectContainer) soButtonArea).getComposite());
	
		
		Runnable state_changed = 
			new Runnable()
			{
				public void
				run()
				{
					String exp_text = so_exp_text.getText().trim();
					
					boolean is_explicit = !exp_text.isEmpty();
					
					boolean has_default = def_locs.getItemCount() > 0;
					
					boolean	can_search = is_explicit || has_default;
					
					so_use_def.setEnabled( has_default );
					
					if ( is_explicit ){
						so_use_def.setChecked( false );
					}else{
						if ( has_default ){
							so_use_def.setChecked( true );
						}
					}
					
					so_add_but.getButton().setEnabled( is_explicit && !Arrays.asList(def_locs.getItems()).contains( exp_text));
					
					buttonsArea.setButtonEnabled( SWT.OK, can_search );
					
					int mode = COConfigurationManager.getIntParameter( "find.files.search.mode", LOCATE_MODE_LINK );
					
					mode_combo.select( mode );
					
					boolean not_relocate = mode != LOCATE_MODE_RELOCATE;
					
					int link_mode = COConfigurationManager.getIntParameter( "find.files.search.mode.link", LOCATE_MODE_LINK_INTERNAL );
					
					boolean link_enabled = mode == LOCATE_MODE_LINK;
					
					link_combo.select( link_enabled?link_mode:LOCATE_MODE_LINK_BLANK );
					
					link_combo.setEnabled( link_enabled );
					
					tolerance_spinner.setEnabled( not_relocate );
					
					so_include_skipped.setEnabled( not_relocate );
				}
			};
			
		state_changed.run();
			
		final Menu menu = new Menu(def_locs);
		
		def_locs.setMenu( menu );

		menu.addMenuListener(
			new MenuListener()
			{
				@Override
				public void menuShown(MenuEvent arg0){
					MenuItem[] items = menu.getItems();
					
					for (int i = 0; i < items.length; i++){

						items[i].dispose();
					}
					
					String[] selected = def_locs.getSelection();
											
					MenuItem mi = new MenuItem( menu, SWT.PUSH );
					
					Messages.setLanguageText(mi, "MySharesView.menu.remove");

					Utils.setMenuItemImage(mi, "delete");
					
					mi.addSelectionListener(
						new SelectionAdapter(){
							@Override
							public void widgetSelected(SelectionEvent e){
								List<String> temp = new ArrayList<>( Arrays.asList( def_locs.getItems()));
								
								temp.removeAll( Arrays.asList( selected ));
								
								def_locs.setItems( temp.toArray( new String[ temp.size()]));
								
								COConfigurationManager.setParameter( "find.files.search.roots", temp );
								
								state_changed.run();
							}
						});
					
					mi.setEnabled( selected.length > 0 );
				}
				public void menuHidden(MenuEvent arg0) {};
			});
		
		def_locs.addKeyListener(
			new KeyAdapter(){
				@Override
				public void
				keyPressed(
					KeyEvent event )
				{
					if ( event.stateMask == 0 && event.keyCode == SWT.DEL ){
						String[] selected = def_locs.getSelection();
						if ( selected.length > 0 ){
							List<String> temp = new ArrayList<>( Arrays.asList( def_locs.getItems()));
							
							temp.removeAll( Arrays.asList( selected ));
							
							def_locs.setItems( temp.toArray( new String[ temp.size()]));
							
							COConfigurationManager.setParameter( "find.files.search.roots", temp );
							
							state_changed.run();
						}
					}else{
						int key = event.character;
						if (key <= 26 && key > 0) {
							key += 'a' - 1;
						}

						if (event.stateMask == SWT.MOD1) {
							switch (key) {
								case 'a': { // CTRL+A select all Torrents
									def_locs.selectAll();
									event.doit = false;
									break;
								}
							}
						}
					}
				}});

		mode_combo.addSelectionListener(
			new SelectionAdapter(){
				@Override
				public void widgetSelected(SelectionEvent e){
					int index = mode_combo.getSelectionIndex();
					
					COConfigurationManager.setParameter( "find.files.search.mode", index );
					
					state_changed.run();
				}
			});
			
		link_combo.addSelectionListener(
			new SelectionAdapter(){
				@Override
				public void widgetSelected(SelectionEvent e){
					int index = link_combo.getSelectionIndex();
					
					if ( index == LOCATE_MODE_LINK_BLANK ){
						
						index = LOCATE_MODE_LINK_INTERNAL;
					}
					
					COConfigurationManager.setParameter( "find.files.search.mode.link", index );
					
					state_changed.run();
				}
			});
		
		tolerance_spinner.addListener(
				SWT.Selection, 
				(e)->{
					int value = tolerance_spinner.getSelection();
					
					if ( value != 0 ){
						
						int index = mode_combo.getSelectionIndex();
						
						if ( index == LOCATE_MODE_LINK ){
						
							index = LOCATE_MODE_COPY;
						
							mode_combo.select( index );
							
							COConfigurationManager.setParameter( "find.files.search.mode", index );
							
							state_changed.run();
						}
					}
				});

		
		so_exp_text.getTextControl().addModifyListener(
			new ModifyListener(){
				
				@Override
				public void modifyText(ModifyEvent arg0){
					state_changed.run();
				}
			});
				
		so_exp_but.addSelectionListener(
			new SWTSkinButtonUtility.ButtonListenerAdapter()
			{
				@Override
				public void pressed(SWTSkinButtonUtility buttonUtility, SWTSkinObject skinObject,
						int stateMask){
				
					DirectoryDialog dd = new DirectoryDialog( shell );

					dd.setFilterPath( TorrentOpener.getFilterPathData());

					dd.setText(MessageText.getString("MyTorrentsView.menu.locatefiles.dialog"));

					String path = dd.open();

					if ( path != null ){

						TorrentOpener.setFilterPathData( path );
						
						so_exp_text.setText( new File( path ).getAbsolutePath());
						
						state_changed.run();
					}
				}
			});
		
		so_add_but.addSelectionListener(
				new SWTSkinButtonUtility.ButtonListenerAdapter()
				{
					@Override
					public void pressed(SWTSkinButtonUtility buttonUtility, SWTSkinObject skinObject,
							int stateMask)
					{
						String loc = so_exp_text.getText().trim();
						
						String[] existing = def_locs.getItems();
						
						String[] locs = new String[ existing.length + 1 ];

						int	pos = 0;
						
						for ( String e: existing ){
							
							if ( loc.equals( e )){
								
								return;
							}
							
							locs[pos++] = e;
						}
						
						locs[pos] = loc;
						
						def_locs.setItems( locs );
						
						COConfigurationManager.setParameter( "find.files.search.roots", Arrays.asList( locs ));
						
						state_changed.run();
					}
				});
		
		so_use_def.addSelectionListener(
				new SWTSkinCheckboxListener(){
					
					@Override
					public void 
					checkboxChanged(SWTSkinObjectCheckbox so, boolean checked){
					
						if ( checked ){
							
							so_exp_text.setText( "" );
						}
						
						state_changed.run();
					}
				});
			
		so_include_skipped.setChecked( COConfigurationManager.getBooleanParameter( "find.files.include.skipped"));
		
		so_include_skipped.addSelectionListener(
				new SWTSkinCheckboxListener(){
					
					@Override
					public void 
					checkboxChanged(SWTSkinObjectCheckbox so, boolean checked){
					
						COConfigurationManager.setParameter( "find.files.include.skipped", checked );
					}
				});
		
		skin.setAutoSizeOnLayout( true, true );
		
		if ( dms == null ){
			
			so_mode_label.setVisible( false );
			
			so_mode.setVisible( false );
			
			skin.getSkinObject( "opt2-line" ).setVisible( false );
		}
		
		dialog.open( "skin3_dlg_findfiles." + (dms==null?"dms":"sps"), true );	
	}
	
		// Bug in Windows 10 causing crash in SWT/OS when window closes:
		// https://bugs.eclipse.org/bugs/show_bug.cgi?id=536376
		// https://github.com/BiglySoftware/BiglyBT/issues/502
		// in the absence of any help from SWT team currently hacked to re-use window :(
	
	private static List<TextViewerWindow>	lf_windows 	= new ArrayList<>();
	private static List<TextViewerWindow>	sp_windows 	= new ArrayList<>();
	private static final boolean			lf_reuse	= Constants.isWindows10OrHigher;
	
	private static final long	LOG_TICK_DOT_MIN = 250;
	private static final long	LOG_TICK_DOT_MAX = 2500;
	
	private static void
	locateFiles(
		final DownloadManager[]			dms,
		final DiskManagerFileInfo[][]	dm_files,
		Shell							shell,
		String[]						initial_search_roots,
		int								mode,
		int								link_type,
		int								tolerance,
		boolean							include_skipped )
	{
		TextViewerWindow _viewer = null;
		
		synchronized( lf_windows ){
			
			if ( lf_reuse ){
			
				if ( !lf_windows.isEmpty()){
					
					_viewer = lf_windows.remove( 0 );
				}				
			}
			
			if ( _viewer == null ){
		
				_viewer = new TextViewerWindow( lf_reuse?null:shell,MessageText.getString( "locatefiles.view.title" ), null, "", false, false );
				
			}else{
				
				_viewer.reset();
			}
		}

		final TextViewerWindow viewer = _viewer;
		
		if ( lf_reuse ){
			
			viewer.setReuseWindow();
		}
		
		viewer.setEditable( false );

		viewer.setOKEnabled( false );

		new AEThread2( "FileLocator" )
		{
			@Override
			public void
			run()
			{
				final int 		MAX_LINKS 		= DownloadManagerStateFactory.MAX_FILES_FOR_INCOMPLETE_AND_DND_LINKAGE;
				final String 	LINK_LIMIT_MSG 	= "Link limit of " + MAX_LINKS + " exceeded. See Tools->Options->Files to increase this";

				try{
					Map<Long,Set<File>>	file_map = new HashMap<>();

					final boolean[]	quit = { false };

					viewer.addListener(
							new TextViewerWindow.TextViewerWindowListener() {

								@Override
								public void closed() {
									synchronized( quit ){
										quit[0] = true;
									}

									synchronized( lf_windows ){

										if ( lf_reuse ){

											lf_windows.add( viewer );
										}
									};
								}
							});

					int indent = 0;

					boolean[]	handled = new boolean[dms.length ];
					
					int	downloads_modified = 0;
					
						// first level of processing - look for simple root folder changes	
						// for hard links we don't want to think about switch the root folder as the 
						// user explicitly wants to use hard links to refer to any files found
					
					if ( 	mode == LOCATE_MODE_RELOCATE || 
							( mode == LOCATE_MODE_LINK && link_type != LOCATE_MODE_LINK_HARD )){
						
							// If a user is relocating a number of downloads they may have taken the 
							// opportunity to organise things a bit (e.g. move into genre-based folders)
							// Expand the search roots a pick this up
						
						Set<String>	expanded_search_roots = new LinkedHashSet<>( initial_search_roots.length*10 );
						
						expanded_search_roots.addAll( Arrays.asList( initial_search_roots ));
						
						Collection<String>	to_expand = expanded_search_roots;
						
						for ( int level=0;level<2;level++){
							
							List<String>	all_kids = new ArrayList<>();
							
							for ( String str: to_expand ){
								
								File[] kids = new File( str ).listFiles();
								
								if ( kids != null && kids.length > 0 ){
									
									for ( File f: kids ){
										
										if ( f.isDirectory()){
										
											all_kids.add( f.getAbsolutePath());
										}
									}
								}
							}
							
							expanded_search_roots.addAll( all_kids );
							
							to_expand = all_kids;
						}						
						
						if ( expanded_search_roots.size() > initial_search_roots.length ){
							
							logLine( viewer, 0, ( expanded_search_roots.size() - initial_search_roots.length ) + " subfolders added to search roots" );
						}
						
						for ( int i=0;i<dms.length;i++){
	
							DownloadManager			dm = dms[i];
	
							int dm_indent = 0;
	
							synchronized( quit ){
								if ( quit[0] ){
									break;
								}
							}
	
							if ( !dm.isPersistent()){
	
								continue;
							}
	
							TOTorrent torrent = dm.getTorrent();
	
							if ( torrent == null ){
	
								continue;
							}
	
							DiskManagerFileInfo[]	selected_files 	= dm_files==null?null:dm_files[i];
	
							if ( selected_files != null ){
								
								continue;
							}
							
							File save_loc = dm.getAbsoluteSaveLocation();
	
							String save_loc_str = save_loc.getAbsolutePath();
							
							if ( !torrent.isSimpleTorrent()){
								
								if ( !save_loc_str.endsWith( File.separator )){
									
									save_loc_str += File.separator;
								}
							}
							
							String save_name = save_loc.getName();
							
							outer:
							for ( String root: expanded_search_roots ){
	
								if ( handled[i] ){
									break;
								}
								
								synchronized( quit ){
									if ( quit[0] ){
										break;
									}
								}
	
								File root_dir = new File( root );
								
								File test_loc = root_dir.getName().equals( save_name )?root_dir:new File( root_dir, save_name );
								
								if ( test_loc.exists()){
							
									DiskManagerFileInfo[]	dm_files = dm.getDiskManagerFileInfoSet().getFiles();					
							
									if ( torrent.isSimpleTorrent()){
										
											// if originating file exists then bail completely
										
										if ( dm_files[0].getFile( true ).exists()){
											
											break outer;
										}
										
										if ( dm_files[0].getLength() == test_loc.length()){
											
											dm_files[0].setLinkAtomic( test_loc, true );
											
											dm.setTorrentSaveDir( test_loc, true );
											
											handled[i] = true;
											
											downloads_modified++;
											
											dm.forceRecheck();
											
											logLine(viewer, dm_indent, "Download '" + dm.getDisplayName() + "' relocated from '" + save_loc.getParent() + "' to '" + root + "'" );
											
											break;
										}
									}else{
										
										boolean	all_good = true;
										
										for ( DiskManagerFileInfo file: dm_files ){
											
											if ( !all_good ){
												
												break;
											}
											
											if ( file.getTorrentFile().isPadFile()){
												
												continue;
											}
											
											File source_file = file.getFile( true );
											
											if ( source_file.exists()){
												
													// if originating file exists then bail completely
	
												break outer;
											}
											
											String source_file_str = source_file.getAbsolutePath();
											
											if ( source_file_str.startsWith( save_loc_str )){
												
													// is in the folder hierarchy so might need to exist
												
												File target_file = new File( test_loc, source_file_str.substring( save_loc_str.length()));
												
												if ( target_file.exists()){
													
													if ( file.isSkipped()){
														
														// don't know what size it should be to cover
														// partial pieces so assume ok
														
													}else{
														
														if ( file.getLength() != target_file.length()){
															
															all_good = false;
														}
													}
												}else{
													
													if ( file.isSkipped() && file.getDownloaded() == 0 ){
														
														// file doesn't need to exist
													
													}else{
													
														all_good = false;
													}
												}
											}
										}
										
										if ( all_good ){
										
											dm.setTorrentSaveDir( test_loc, true );
											
											handled[i] = true;
											
											downloads_modified++;
											
											if ( mode != LOCATE_MODE_RELOCATE || !dm.isDownloadComplete( false )){
											
												dm.forceRecheck();
											}
											
											logLine(viewer, dm_indent, "Download '" + dm.getDisplayName() + "' relocated from '" + save_loc.getParent() + "' to '" + root + "'" );
											
											break;
											
										}else{
											
										}
									}
								}
							}		
						
							if ( !handled[i] ){
							
								if ( mode == LOCATE_MODE_RELOCATE ){
								
									logLine(viewer, dm_indent, "Download '" + dm.getDisplayName() + "' not relocated from '" + save_loc.getParent() + "' as compatible files not found" );
								}
							}
						}
					}
					
					if ( downloads_modified < handled.length && mode != LOCATE_MODE_RELOCATE ){
							
						int file_count	= 0;	
	
						long bfm_start = SystemTime.getMonotonousTime();
	
						long[] log_details = { bfm_start, 0, bfm_start };
		
						for ( String root: initial_search_roots ){
	
							synchronized( quit ){
								if ( quit[0] ){
									break;
								}
							}
	
							File dir = new File( root );
	
							logLine( viewer, indent, (bfm_start==log_details[0]?"":"\r\n") + new SimpleDateFormat().format( new Date()) +  ": Enumerating files in " + dir );
	
							file_count += buildFileMap( viewer, dir, file_map, log_details, quit );
						}
	
						logLine( viewer, indent, (bfm_start==log_details[0]?"":"\r\n") + "Found " + file_count + " files with " + file_map.size() + " distinct sizes" );
	
						long[]	file_lengths = null;
	
						if ( tolerance > 0 ){
	
							Set<Long> lengths = file_map.keySet();
	
							file_lengths = new long[lengths.size()];
	
							int	pos = 0;
	
							for ( long l: lengths ){
	
								file_lengths[pos++] = l;
							}
	
							Arrays.sort( file_lengths );
						}
	
						Set<String>	all_dm_incomplete_files = null;
	
						ConcurrentHasher hasher = ConcurrentHasher.getSingleton();
	
						for ( int i=0;i<dms.length;i++){
	
							if ( handled[i] ){
								
								continue;
							}
							
							DownloadManager			dm 				= dms[i];
	
							int dm_indent = 0;
	
							synchronized( quit ){
								if ( quit[0] ){
									break;
								}
							}
	
							if ( !dm.isPersistent()){
	
								continue;
							}
	
							TOTorrent torrent = dm.getTorrent();
	
							if ( torrent == null ){
	
								continue;
							}
	
							DiskManagerFileInfo[]	selected_files 	= dm_files==null?null:dm_files[i];
	
							Set<Integer>	selected_file_indexes;
	
							if ( selected_files == null ){
	
								selected_file_indexes = null;
	
							}else{
	
								selected_file_indexes = new HashSet<>();
	
								for ( DiskManagerFileInfo f: selected_files ){
	
									selected_file_indexes.add( f.getIndex());
								}
							}
	
							TOTorrentFile[] to_files = torrent.getFiles();
	
							final long	piece_size = torrent.getPieceLength();
	
							byte[][] pieces = torrent.getPieces();
	
							logLine( viewer, dm_indent, "Processing '" + dm.getDisplayName() + "', piece size=" + DisplayFormatters.formatByteCountToKiBEtc( piece_size ));
	
							dm_indent++;
	
							int dm_state = dm.getState();
	
							if ( ! ( dm_state == DownloadManager.STATE_STOPPED || dm_state == DownloadManager.STATE_ERROR )){
	
								logLine( viewer, dm_indent, "Download must be stopped" );
	
								continue;
							}
	
							DiskManagerFileInfo[] files = dm.getDiskManagerFileInfoSet().getFiles();
	
							Set<String>	dm_files = null;
	
							Map<DiskManagerFileInfo,File>		actions_established = new HashMap<>();
	
							Map<DiskManagerFileInfo,Set<String>> unmatched_files =
									new TreeMap<>(
											new Comparator<DiskManagerFileInfo>(){
	
												@Override
												public int
												compare(
														DiskManagerFileInfo o1,
														DiskManagerFileInfo o2 )
												{
													long	diff = o2.getLength() - o1.getLength();
	
													if ( diff < 0 ){
														return( -1 );
													}else if ( diff > 0 ){
														return( 1 );
													}else{
														return( o1.getIndex() - o2.getIndex());
													}
												}
											});
	
							int	no_candidates 		= 0;
							int	already_complete	= 0;
							int skipped				= 0;
							int unskipped_count		= 0;
	
							int	action_count 		= 0;
							int internal_link_count	= 0;
							
								// pre-scan candidates to see if there is a preferred order for multiple-hits
							
							Map<String,int[]>	candidate_root_map = new HashMap<>();
							boolean				has_multiple_candidates = false;
							String				candidate_root_pref	= null;
							int					candidate_root_pref_count	= 0;
							
							for ( final DiskManagerFileInfo file: files ){

								if ( file.getTorrentFile().isPadFile()){
									
									continue;
								}

								if ( selected_file_indexes != null ){

									if ( !selected_file_indexes.contains( file.getIndex())){

										continue;
									}
								}

								long	file_length = file.getLength();

								if ( file.getDownloaded() == file_length ){

									// edge case where file has been deleted but cached download info not reset yet

									if ( file.getFile( true ).exists()){

										already_complete++;

										continue;
									}
								}

								if ( !include_skipped ){

									if ( file.isSkipped()){

										skipped++;

										continue;
									}
								}
								
								Set<File> candidates = file_map.get( file_length );

								if ( candidates == null ){
									
									continue;
								}
								
								if ( candidates.size() > 1 ){
									
									has_multiple_candidates = true;
								}
								
								for ( File candidate: candidates ){
									
									File root = candidate;

									String rel = file.getTorrentFile().getRelativePath();
	
									int pos = 0;
	
									while( root != null ){
	
										root = root.getParentFile();
	
										pos = rel.indexOf( File.separatorChar, pos );
	
										if ( pos >= 0 ){
	
											pos = pos+1;
	
										}else{
	
											break;
										}
									}
									
									if ( root != null ){
										
										String root_str = root.getAbsolutePath();
										
										int[] count = candidate_root_map.get( root_str );
										
										if ( count == null ){
											
											count = new int[]{ 0 };
											
											candidate_root_map.put( root_str, count );
										}
										
										int num = ++count[0];
										
										if ( num > candidate_root_pref_count ){
										
											candidate_root_pref_count 	= num;
											candidate_root_pref			= root_str;
										}
									}
								}
							}
							
							if ( has_multiple_candidates && candidate_root_map.size() > 1 ){
								
								logLine( viewer, dm_indent, "Multiple candidates, preferred root is " + candidate_root_pref );

							}else{
								
								candidate_root_pref = null;
							}
							
							try{
	
								download_loop:
									for ( final DiskManagerFileInfo file: files ){
	
										if ( file.getTorrentFile().isPadFile()){
											
											continue;
										}
										
										int file_indent = dm_indent;
	
										synchronized( quit ){
											if ( quit[0] ){
												break;
											}
										}
	
										if ( selected_file_indexes != null ){
	
											if ( !selected_file_indexes.contains( file.getIndex())){
	
												continue;
											}
										}
	
										long	file_length = file.getLength();
	
										if ( file.getDownloaded() == file_length ){
	
											// edge case where file has been deleted but cached download info not reset yet
	
											if ( file.getFile( true ).exists()){
	
												already_complete++;
	
												continue;
											}
										}
	
										if ( !include_skipped ){
	
											if ( file.isSkipped()){
	
												skipped++;
	
												continue;
											}
										}
	
										Set<File> general_candidates = file_map.get( file_length );
	
										String extra_info = "";
	
										if ( file_lengths != null ){
	
											int pos = Arrays.binarySearch( file_lengths, file_length );
	
											if ( pos < 0 ){
	
												pos = 1-pos;
											}
	
											long 	file_tolerance = tolerance*file_length/100;
	
											long	lower_limit = file_length - file_tolerance;
	
											int	index = pos;
	
											List<Set<File>>	extra_candidates = new ArrayList<>();
	
											while( index >= 0 ){
	
												long	l = file_lengths[index--];
	
												if ( l < lower_limit ){
	
													break;
	
												}else{
	
													if ( l != file_length ){
	
														Set<File> x = file_map.get( l );
	
														if ( x != null ){
	
															if ( extra_info.length() > 128 ){
	
																if ( !extra_info.endsWith( "..." )){
	
																	extra_info += "...";
																}		
															}else{
	
																extra_info += (extra_info.isEmpty()?"":", ") + l  + "->" + x.size();
															}
	
															extra_candidates.add( x );
														}
													}
												}
											}
	
											long	upper_limit = file_length + file_tolerance;
	
											index = pos+1;
	
											while( index < file_lengths.length ){
	
												long	l = file_lengths[index++];
	
												if ( l > upper_limit ){
	
													break;
	
												}else{
	
													if ( l != file_length ){
	
														Set<File> x = file_map.get( l );
	
														if ( x != null ){
	
															extra_info += (extra_info.isEmpty()?"":", ") + l  + "->" + x.size();
	
															extra_candidates.add( x );
														}
													}
												}
											}
	
											if ( !extra_candidates.isEmpty()){
	
												boolean duplicated = false;
	
												if ( general_candidates != null ){
	
													general_candidates = new HashSet<>( general_candidates );
	
													duplicated = true;
												}
	
												for ( Set<File> s: extra_candidates ){
	
													if ( general_candidates == null ){
	
														general_candidates = s;
	
													}else{
	
														if ( !duplicated ){
	
															general_candidates = new HashSet<>( general_candidates );
	
															duplicated = true;
														}
	
														general_candidates.addAll( s );
													}
												}
											}
										}
	
										if ( general_candidates != null ){
	
											if ( general_candidates.size() > 0 ){
	
												// remove any incomplete files from existing downloads
	
												if ( all_dm_incomplete_files == null ){
	
													all_dm_incomplete_files = new HashSet<>();
	
													List<DownloadManager> all_dms = CoreFactory.getSingleton().getGlobalManager().getDownloadManagers();
	
													for ( DownloadManager x: all_dms ){
	
														if ( !x.isDownloadComplete( false )){
	
															DiskManagerFileInfo[] fs = x.getDiskManagerFileInfoSet().getFiles();
	
															for ( DiskManagerFileInfo f: fs ){
	
																if ( 	f.isSkipped() ||
																		f.getDownloaded() != f.getLength()){
	
																	all_dm_incomplete_files.add( f.getFile(true).getAbsolutePath());
																}
															}
														}
													}
												}
	
												Iterator<File> it = general_candidates.iterator();
	
												while( it.hasNext()){
	
													File f = it.next();
	
													if ( all_dm_incomplete_files.contains( f.getAbsolutePath())){
	
														it.remove();
													}
												}
											}
	
												// must duplicate as modified below
											
											LinkedList<File> dm_candidates = new LinkedList<>( general_candidates );
											
											if ( dm_candidates.size() > 0 ){
		
												// remove all files from this download
	
												if ( dm_files == null ){
	
													dm_files = new HashSet<>();
	
													for ( DiskManagerFileInfo f: files ){
	
														dm_files.add( f.getFile( true ).getAbsolutePath());
													}
												}
	
												Iterator<File> it = dm_candidates.iterator();
	
												while( it.hasNext()){
	
													File f = it.next();
	
													if ( dm_files.contains( f.getAbsolutePath())){
	
														it.remove();
													}
												}
											}
	
											if ( dm_candidates.size() > 0 ){
	
												boolean	matched = false;
	
												Set<String>	failed_candidates = new HashSet<>();
	
												TOTorrentFile to_file = file.getTorrentFile();
	
												long	offset = 0;
	
												for ( TOTorrentFile tf: to_files ){
	
													if ( tf == to_file ){
	
														break;
													}
	
													offset += tf.getLength();
												}
	
												int	to_piece_number = to_file.getFirstPieceNumber();
	
												long to_file_offset = offset%piece_size;
	
												if ( to_file_offset != 0 ){
	
													to_file_offset = piece_size - to_file_offset;
	
													to_piece_number++;
												}
	
												long	overall_to_stop_at = file_length - piece_size;
	
												if ( to_file_offset < overall_to_stop_at ){
	
													int test_indent = file_indent+1;
	
													logLine( 
															viewer, file_indent,
															to_file.getRelativePath() + 
															" (size=" + DisplayFormatters.formatByteCountToKiBEtc(to_file.getLength()) +
															(extra_info.isEmpty()?"":(", extra: " + extra_info )) + ")" + " - " + dm_candidates.size() + " candidate(s)" );
	
													byte[]	buffer = new byte[(int)piece_size];
	
													RandomAccessFile 	to_raf 			= null;
													boolean				to_raf_tried 	= false;
													
													try{
														if ( dm_candidates.size() > 1 && candidate_root_pref != null ){
															
															for ( Iterator<File> it=dm_candidates.iterator(); it.hasNext();){
																
																File candidate = it.next();
																
																if ( candidate.getAbsolutePath().startsWith(candidate_root_pref)){
																	
																	it.remove();
																	
																	dm_candidates.addFirst( candidate );
																	
																	break;
																}
															}
														}
														
														for ( File from_file: dm_candidates ){
		
															synchronized( quit ){
																if ( quit[0] ){
																	break;
																}
															}
		
															long 	file_to_stop_at;
															long 	allowed_fails;
		
															int		matched_pieces	= 0;
															int		failed_pieces	= 0;
															int		copied_pieces	= 0;
		
															if ( tolerance == 0 ){
		
																file_to_stop_at = overall_to_stop_at;
		
																allowed_fails = 0;
		
															}else{
		
																long this_file_length = from_file.length();
		
																// might be too long, bring it back within range
		
																if ( this_file_length > file_length ){
		
																	this_file_length = file_length;
																}
		
																file_to_stop_at = this_file_length - piece_size;
		
																allowed_fails = (( tolerance*this_file_length/100 ) + (piece_size-1)) / piece_size;
															}
		
															log( viewer, test_indent, "Testing " + from_file + (allowed_fails==0?"":(" (max fails=" + allowed_fails + ")" )) + " - " );
		
															RandomAccessFile from_raf = null;
		
															boolean	error 			= false;
															boolean	hash_failed		= false;
		
															long	log_start	= SystemTime.getMonotonousTime();
															long	last_ok_log = log_start;
															long	dot_count	= 0;
		
															try{
																from_raf = new RandomAccessFile( from_file, "r" );
		
																long 	file_offset 	= to_file_offset;
																int		piece_number 	= to_piece_number;
		
																while( file_offset < file_to_stop_at ){
		
																	synchronized( quit ){
																		if ( quit[0] ){
																			break;
																		}
																	}
		
																	from_raf.seek( file_offset );
		
																	from_raf.read( buffer );
		
																	byte[] required_hash = pieces[piece_number];
																	
																	boolean match;
																	
																	if ( required_hash != null ){
																		ConcurrentHasherRequest req = 
																			hasher.addRequest( 
																				ByteBuffer.wrap( buffer ), 
																				required_hash.length==20?1:2,
																				(int)piece_size,
																				to_file.getLength());
			
																		byte[] hash = req.getResult();
			
																		match = Arrays.equals( required_hash, hash );
																		
																	}else{
																		
																		match = false;
																	}
		
																	if ( match ){
		
																		matched_pieces++;
																		
																		long now = SystemTime.getMonotonousTime();
		
																		long elapsed = now - log_start;
		
																		long delay = Math.min( LOG_TICK_DOT_MIN + ( (LOG_TICK_DOT_MAX-LOG_TICK_DOT_MIN) * elapsed )/60000, LOG_TICK_DOT_MAX );																
		
																		if ( now - last_ok_log >= delay ){
		
																			last_ok_log = now;
		
																			if ( dot_count == 80 ){
		
																				logLine( viewer, 0, "" );
																			}
		
																			dot_count++;
		
																			log( viewer, 0, "." );
																		}
																		
																		if ( mode == LOCATE_MODE_PIECE ){
																			
																			if ( to_raf == null && !to_raf_tried ){
																				
																				to_raf_tried = true;
																				
																				int action_indent = test_indent+1;
																				
																				if ( file.getStorageType() != DiskManagerFileInfo.ST_LINEAR ){
																					
																					boolean worked = file.setStorageType( DiskManagerFileInfo.ST_LINEAR, true );
																					
																					logLine( viewer, action_indent, "Setting storage type to linear - " + ( worked?"OK":"Failed" ));
																				}
						
																				File existing = file.getFile( true );

																				if ( file.isSkipped()){
												
																					boolean existed = existing.exists();
						
																					file.setSkipped( false );
						
																					boolean worked = !file.isSkipped();
						
																					if ( worked ){
						
																						unskipped_count++;
																					}
						
																					logLine( viewer, action_indent, "Setting priority to normal - " + ( worked?"OK":"Failed" ));
						
																					if ( !existed && existing.exists()){
						
																						existing.delete();
																					}
																				}
																				
																				if ( !existing.exists()){
																					
																					existing.getParentFile().mkdirs();
																				}
																				
																				to_raf = new RandomAccessFile( existing, "rw" );
																			}
																			
																			if ( to_raf != null ){
																				
																				to_raf.seek( file_offset );
																				
																				to_raf.write( buffer );

																				copied_pieces++;
																				
																				if ( !actions_established.containsKey( file )){
																					
																					actions_established.put( file, from_file );
																					
																					action_count++;
																				}
																			}
																		}
																	}else{
		
																		failed_pieces++;
		
																		if ( failed_pieces > allowed_fails && mode != LOCATE_MODE_PIECE ){
		
																			logLine( viewer, 0, "X" );
		
																			hash_failed = true;
		
																			failed_candidates.add( from_file.getAbsolutePath());	
		
																			break;
		
																		}else{
		
																			log( viewer, 0, "x" );
																		}
																	}
		
																	file_offset += piece_size;
																	piece_number++;
																}
															}catch( Throwable e ){
		
																Debug.out( e );
		
																logLine( viewer, 0, "X" );
		
																error = true;
		
															}finally{
		
																if ( from_raf != null ){
		
																	try{
																		from_raf.close();
		
																	}catch( Throwable e ){
		
																	}
																}
															}
		
															if ( !( error || hash_failed )){
		
																if ( mode == LOCATE_MODE_PIECE ){
																	
																	logLine( viewer, 0, " Copied " + copied_pieces + " pieces" );

																		// continue onto next candidate as may have other pieces
																	
																}else{
																	
																	logLine( viewer, 0, " Matched" + (failed_pieces==0?"":(" (fails=" + failed_pieces + ")")));
			
																	int action_indent = test_indent+1;
			
																	if ( file.getStorageType() != DiskManagerFileInfo.ST_LINEAR ){
																		
																		boolean worked = file.setStorageType( DiskManagerFileInfo.ST_LINEAR, true );
																		
																		logLine( viewer, action_indent, "Setting storage type to linear - " + ( worked?"OK":"Failed" ));
																	}
			
																	if ( file.isSkipped()){
			
																		File existing = file.getFile( true );
			
																		boolean existed = existing.exists();
			
																		file.setSkipped( false );
			
																		boolean worked = !file.isSkipped();
			
																		if ( worked ){
			
																			unskipped_count++;
																		}
			
																		logLine( viewer, action_indent, "Setting priority to normal - " + ( worked?"OK":"Failed" ));
			
																		if ( !existed && existing.exists()){
			
																			existing.delete();
																		}
																	}
			
																	if ( mode == LOCATE_MODE_LINK ){
			
																		logLine( viewer, action_indent, "Linking to " + from_file );
		
																		boolean do_internal_link = true;
																		
																		if ( link_type == LOCATE_MODE_LINK_HARD ){
																																			
																			File original = file.getFile( false );
																		
																			if ( original.exists()){
			
																				original.delete();
																				
																			}else{
																				
																				File o_parent = original.getParentFile();
																				
																				if ( !o_parent.exists()){
																					
																					o_parent.mkdirs();
																				}
																			}
																			
																			try{
																				Files.createLink( original.toPath(), from_file.toPath());
																				
																				if ( file.setLink( original, true )){
																					
																					do_internal_link = false;
																					
																					logLine( viewer, action_indent+1, "Hard Link successful" );
																					
																					dm_files.add( from_file.getAbsolutePath());
																					
																					actions_established.put( file, from_file );
				
																					action_count++;
				
																					matched = true;
																					
																				}else{
																					
																					original.delete();
																					
																					logLine( viewer, action_indent+1, "Hard Link failed, trying Internal Link" );
	
																				}
																			}catch( Throwable e ){
																				
																				logLine( viewer, action_indent+1, "Hard Link failed, trying Internal Link: Error=" + Debug.getNestedExceptionMessage( e ));
																			}
																		}
																		
																		if ( do_internal_link ){
																			
																			if ( file.setLink( from_file, true )){
			
																				logLine( viewer, action_indent+1, "Link successful" );
			
																				dm_files.add( from_file.getAbsolutePath());
			
																				actions_established.put( file, from_file );
			
																				action_count++;
			
																				internal_link_count++;
																				
																				matched = true;
			
																				if ( internal_link_count > MAX_LINKS ){
			
																					logLine( viewer, action_indent+2, LINK_LIMIT_MSG );
			
																					break download_loop;
																				}
																			}else{
			
																				logLine( viewer, action_indent+1, "Link failed" );
																			}
																		}
																	}else{
			
																		File target = file.getFile( true );
			
																		if ( target.exists()){
			
																			target.delete();
																		}
			
																		File parent = target.getParentFile();
			
																		if ( !parent.exists()){
			
																			parent.mkdirs();
																		}
			
																		if ( mode == LOCATE_MODE_COPY ){
			
																			logLine( viewer, action_indent, "Copying " + from_file + " to " + target );
			
																			boolean ok = FileUtil.copyFile( from_file,  target );
			
																			if ( ok ){
			
																				logLine( viewer, action_indent+1, "Copy successful" );
			
																				actions_established.put( file, from_file );
			
																				action_count++;
			
																				matched = true;
			
																			}else{
			
																				logLine( viewer, action_indent+1, "Copy failed" );
																			}
																		}else if ( mode == LOCATE_MODE_MOVE ){
			
																			logLine( viewer, action_indent, "Moving " + from_file + " to " + target );
			
																			boolean ok = FileUtil.renameFile( from_file,  target );
			
																			if ( ok ){
			
																				logLine( viewer, action_indent+1, "Move successful" );
			
																				actions_established.put( file, from_file );
			
																				action_count++;
			
																				matched = true;
			
																			}else{
			
																				logLine( viewer, action_indent+1, "Move failed" );
																			}
																		}else{
																			
																			Debug.out( "derp" );
																		}
																	}
			
																	break;
																}
															}
														}
													}finally{
													
														if ( to_raf != null ){
															
															try{
																
																to_raf.close();
																
															}catch( Throwable e ){
																
																Debug.out( e );
															}
														}
													}
												}
	
												if ( !matched ){
	
													unmatched_files.put( file, failed_candidates );
												}
											}else{
	
												no_candidates++;
											}
										}else{
	
											no_candidates++;
										}
									}
	
							logLine( viewer, dm_indent, "Matched=" + actions_established.size() + ", complete=" + already_complete + ", ignored as not selected for download=" + skipped + ", no candidates=" + no_candidates + ", remaining=" + unmatched_files.size() + " (total=" + files.length + ")");
		
							if ( mode != LOCATE_MODE_PIECE && actions_established.size() > 0 && unmatched_files.size() > 0 ){
	
								List<DiskManagerFileInfo> fixed_files = new ArrayList<>( actions_established.keySet());

								logLine( viewer, dm_indent, "Looking for other potential name-based matches" );
	
								File overall_root = null;
	
								for ( Map.Entry<DiskManagerFileInfo,File> entry: actions_established.entrySet()){
	
									int file_indent = dm_indent+1;
	
									DiskManagerFileInfo dm_file = entry.getKey();
									File				root	= entry.getValue();
	
									String rel = dm_file.getTorrentFile().getRelativePath();
	
									int pos = 0;
	
									while( root != null ){
	
										root = root.getParentFile();
	
										pos = rel.indexOf( File.separatorChar, pos );
	
										if ( pos >= 0 ){
	
											pos = pos+1;
	
										}else{
	
											break;
										}
									}
	
									if ( root == null ){
	
										logLine( viewer, file_indent, "No usable root folder found" );
	
										break;
									}
	
									if ( overall_root == null ){
	
										overall_root = root;
	
									}else{
	
										if ( !overall_root.equals( root )){
	
											overall_root = null;
	
											logLine( viewer, file_indent, "Inconsistent root folder found" );
	
											break;
										}
									}
								}
	
								if ( overall_root != null ){
	
									logLine( viewer, dm_indent, "Root folder is " + overall_root.getAbsolutePath());
	
									int actions_ok = 0;
	
									for ( Map.Entry<DiskManagerFileInfo,Set<String>> entry: unmatched_files.entrySet()){
	
										int file_indent = dm_indent+1;
	
										synchronized( quit ){
											if ( quit[0] ){
												break;
											}
										}
	
										DiskManagerFileInfo file = entry.getKey();
	
										if ( actions_established.containsKey( file )){
											
											continue;
										}
										
										if ( selected_file_indexes != null ){
	
											if ( !selected_file_indexes.contains( file.getIndex())){
	
												continue;
											}
										}
	
										File source_file = new File( overall_root, file.getTorrentFile().getRelativePath());
										
										boolean is_approximate = false;
										
										if ( source_file.exists() && source_file.length() == file.getLength()){
	
											// looks good
											
										}else{
											
												// if there's only one with the right size in same folder then grab that
											
											File[] source_files = source_file.getParentFile().listFiles();
											
											File selected = null;
											
											if ( source_files != null ){
												
												for ( File f: source_files ){
													
													if ( f.length() == file.getLength()){
														
														if ( selected == null ){
															
															selected = f;
															
														}else{
															
															selected = null;	// multiple same size
															
															break;
														}
													}
												}
											}
											
											if ( selected != null ){
											
												source_file = selected;
												
												is_approximate = true;
												
											}else{
												
												source_file = null;
											}
										}
										
										if ( source_file != null ){
											
											if ( !entry.getValue().contains( source_file.getAbsolutePath())){
	
												String str = "File '" + file.getFile( false ).getName() + "'";
												
												if ( is_approximate ){
													
													str += "- selected " + source_file ;
												}
												
												logLine( viewer, file_indent, str );
	
												int action_indent = file_indent+1;
	
												if ( file.getStorageType() != DiskManagerFileInfo.ST_LINEAR ){
													
													boolean worked = file.setStorageType( DiskManagerFileInfo.ST_LINEAR, true );
													
													logLine( viewer, action_indent, "Setting storage type to linear - " + ( worked?"OK":"Failed" ));
												}
												
												if ( file.isSkipped()){
	
													File existing = file.getFile( true );
	
													boolean existed = existing.exists();
	
													file.setSkipped( false );
	
													boolean worked = !file.isSkipped();
	
													if ( worked ){
	
														unskipped_count++;
													}
	
													logLine( viewer, action_indent, "Setting priority to normal - " + ( worked?"OK":"Failed" ));
	
													if ( !existed && existing.exists()){
	
														existing.delete();
													}
												}
	
												if ( mode == LOCATE_MODE_LINK ){
	
													logLine( viewer, action_indent, "Linking to " + source_file );

													boolean do_internal_link = true;
													
													if ( link_type == LOCATE_MODE_LINK_HARD ){
																														
														File original = file.getFile( false );
													
														if ( original.exists()){

															original.delete();
															
														}else{
															
															File o_parent = original.getParentFile();
															
															if ( !o_parent.exists()){
																
																o_parent.mkdirs();
															}
														}
														
														try{
															Files.createLink( original.toPath(), source_file.toPath());
															
															if ( file.setLink( original, true )){
																
																do_internal_link = false;
																
																logLine( viewer, action_indent+1, "Hard Link successful" );
																
																fixed_files.add( file );
																
																actions_ok++;

																action_count++;
																
															}else{
																
																original.delete();
																
																logLine( viewer, action_indent+1, "Hard Link failed, trying Internal Link" );

															}
														}catch( Throwable e ){
															
															logLine( viewer, action_indent+1, "Hard Link failed, trying Internal Link: Error=" + Debug.getNestedExceptionMessage( e ));
														}
													}
													
													if ( do_internal_link ){
														
														if ( file.setLink( source_file, true )){
	
															logLine( viewer, action_indent+1, "Link successful" );
	
															fixed_files.add( file );
	
															actions_ok++;
	
															action_count++;
	
															internal_link_count++;
															
															if ( internal_link_count > MAX_LINKS ){
	
																logLine( viewer, action_indent+2, LINK_LIMIT_MSG );
	
																break;
															}
														}else{
	
															logLine( viewer, action_indent+1, "Link failed" );
														}
													}
												}else{
	
	
													File target = file.getFile( true );
	
													if ( target.exists()){
	
														target.delete();
													}
	
													if ( mode == LOCATE_MODE_COPY || mode == LOCATE_MODE_PIECE ){
	
														logLine( viewer, action_indent, "Copying " + source_file + " to " + target );
	
														boolean ok = FileUtil.copyFile( source_file,  target );
	
														if ( ok ){
	
															logLine( viewer, action_indent+1, "Copy successful" );
	
															fixed_files.add( file );
	
															actions_ok++;
	
															action_count++;
	
														}else{
	
															logLine( viewer, action_indent+1, "Copy failed" );
														}
													}else if ( mode == LOCATE_MODE_MOVE ){
	
														logLine( viewer, action_indent, "Moving " + source_file + " to " + target );
	
														boolean ok = FileUtil.renameFile( source_file,  target );
	
														if ( ok ){
	
															logLine( viewer, action_indent+1, "Move successful" );
	
															fixed_files.add( file );
	
															actions_ok++;
	
															action_count++;
	
														}else{
	
															logLine( viewer, action_indent+1, "Move failed" );
														}
													}else{
														
														Debug.out( "derp" );
													}
												}
											}
										}
									}
	
									String action_str = mode==LOCATE_MODE_LINK?"Linked":(mode==LOCATE_MODE_COPY||mode==LOCATE_MODE_PIECE?"Copied":"Moved" );
	
									logLine( viewer, dm_indent, action_str + " " + actions_ok + " of " + unmatched_files.size());
								}
							}
	
							if ( include_skipped ){
	
								if ( unskipped_count > 0 ){
	
									logLine( viewer, dm_indent, "Changed " + unskipped_count + " file priorities from 'skipped' to 'normal'" );
								}
							}
							}finally{
	
								if ( action_count > 0 ){
	
									dm.forceRecheck();
	
									downloads_modified++;
								}
							}
						}
					}
					
					logLine( viewer, indent, new SimpleDateFormat().format( new Date()) +  ": Complete, downloads updated=" + downloads_modified );

				}catch( Throwable e ){

					log( viewer, 0, "\r\n" + new SimpleDateFormat().format( new Date()) + ": Failed: " + Debug.getNestedExceptionMessage( e ) + "\r\n" );
					
				}finally{

					Utils.execSWTThread(
							new Runnable()
							{
								public void
								run()
								{
									if ( !viewer.isDisposed()){

										viewer.setCancelEnabled( false );

										viewer.setOKEnabled( true );
									}
								}
							});
				}
			}
		}.start();

		// viewer.goModal(); - don't make it modal as it breaks the re-use window hack
	}

	
	public static void
	locateSavePaths(
		List<TorrentOpenOptions>	torrents,
		Shell						shell,
		String[]					roots,
		Consumer<File[]>			listener )
	{
		TextViewerWindow _viewer = null;
		
		synchronized( sp_windows ){
			
			if ( lf_reuse ){
			
				if ( !sp_windows.isEmpty()){
					
					_viewer = sp_windows.remove( 0 );
				}				
			}
			
			if ( _viewer == null ){
		
				_viewer = new TextViewerWindow( lf_reuse?null:shell, MessageText.getString( "locatesavepaths.view.title" ), null, "", false, false );
				
			}else{
				
				_viewer.reset();
			}
		}

		final TextViewerWindow viewer = _viewer;
		
		if ( lf_reuse ){
			
			viewer.setReuseWindow();
		}
		
		viewer.setEditable( false );

		viewer.setCancelEnabled( true );
		
		viewer.setOKEnabled( false );
		
		viewer.setOKisApply( true );
		
		viewer.getShell().moveAbove( shell );

		new AEThread2( "FileLocator" )
		{
			@Override
			public void
			run()
			{
				File[] results = new File[torrents.size()];

				int[] result_count = {0};				

				try{
					String incomplete_file_suffix = null;
					
					if ( COConfigurationManager.getBooleanParameter( "Rename Incomplete Files")){

						incomplete_file_suffix = COConfigurationManager.getStringParameter( "Rename Incomplete Files Extension" ).trim();
						
						incomplete_file_suffix = FileUtil.convertOSSpecificChars( incomplete_file_suffix, false );
						
						if ( incomplete_file_suffix.isEmpty()){
							
							incomplete_file_suffix = null;
						}
					}

					Map<Long,Set<File>>	file_map = new HashMap<>();

					final boolean[]	quit = { false };

					viewer.addListener(
							new TextViewerWindow.TextViewerWindowListener() {

								@Override
								public void closed() {
									synchronized( quit ){
										quit[0] = true;
									}

									synchronized( sp_windows ){

										if ( lf_reuse ){

											sp_windows.add( viewer );
										}
									};
								}
							});

					int indent = 0;
					
					logLine( viewer, indent, "Checking " + torrents.size() + " torrents against " + roots.length + " search locations" );
					
					Set<String>	tested_folders = new HashSet<>();
					
					LinkedList<File>	to_do = new LinkedList<>();
					
					for ( String root: roots ){
						
						to_do.add( new File( root ));
					}
					
					Map<TorrentOpenOptions,Object[]>	candidates = new HashMap<>();
					
					long last_dir_log = -1;
					
					while( !to_do.isEmpty()){
						
						synchronized( quit ){
							if ( quit[0] ){
								break;
							}
						}
						
						File dir = to_do.removeFirst();
						
						if ( !dir.isDirectory()){
							
							continue;
						}
						
						String abs_dir = dir.getAbsolutePath();
						
						if ( tested_folders.contains( abs_dir )){
							
							continue;
						}
						
						tested_folders.add( abs_dir );
						
						long now = SystemTime.getMonotonousTime();
						
						if ( last_dir_log == -1 || now - last_dir_log > 100 ){
						
							last_dir_log = now;
							
							logLine( viewer, indent, "Searching " + abs_dir + ", remaining=" + to_do.size());
						}
		
						
						for( TorrentOpenOptions too: torrents ){
							
							TOTorrent torrent = too.getTorrent();
							
							TOTorrentFile[] tfiles = torrent.getFiles();
							
							TorrentOpenFileOptions[] files = too.getFiles();
							
							int 	hits = 0;
							long	hits_size = 0;
							
							int		pads = 0;
							
							if ( torrent.isSimpleTorrent()){
								
								String file = files[0].getDestFileName();
								
								File test_file = new File( dir, file );
								
								if ( test_file.exists()){
									
									hits++;
								}
								
							}else{
								
								//String parent_dir = too.getSubDirOrDefault();
								
								for ( TorrentOpenFileOptions tofile: files ){
									
									int index = tofile.getIndex();
									
									TOTorrentFile tfile = tfiles[index];
									
									if ( tfile.isPadFile()){
										
										pads++;
										
										continue;
									}
									
									String file = tofile.getDestFileName();
									
									String path = tfile.getRelativePath() ;
									
									int pos = path.lastIndexOf( File.separator );
									
									if ( pos != -1 ){
										
										path = path.substring( 0, pos+1 ) + file;
									}
									
									/*
									if ( !too.isRemovedTopLevel()){
									
										path = parent_dir + File.separator + path;
									}
									*/
									
									File test_file = new File( dir, path );
																		
									if ( test_file.exists()){
										
										hits++;
										
										hits_size += test_file.length();
										
									}else{
										
										if ( incomplete_file_suffix != null ){
											
											test_file = new File( dir, path + incomplete_file_suffix );
											
											if ( test_file.exists()){
											
												hits++;
												
												hits_size += test_file.length();
											}
										}
									}
								}
							}
							
							if ( hits > 0 ){
							
								logLine( viewer, indent+1, "Testing " + dir  + " against " + too.getDisplayName() + ": matches=" + hits );

								Object[] entry = candidates.get( too );
								
								if ( entry == null ){
									
									entry = new Object[]{ new int[]{ 0, pads }, new ArrayList<Object[]>() };
									
									candidates.put( too,  entry );
								}
								
								int[] counts = (int[])entry[0];
								
								List<Object[]>	hit_entries = (List<Object[]>)entry[1];
								
								if ( hits > counts[0] ){
									
									counts[0] = hits;
									
									hit_entries.clear();
									
									hit_entries.add( new Object[]{ dir, hits_size } );
									
								}else if ( hits == counts[0] ){
									
									hit_entries.add( new Object[]{ dir, hits_size } );
								}
							}
						}
									
						
						File[] files = dir.listFiles();
						
						if ( files != null ){
							
							for ( File f: files){
								
								if ( f.isDirectory()){
									
									to_do.add( f );
								}
							}
						}
					}
					
					logLine( viewer, indent, "Search complete, " + tested_folders.size() + " folders checked" );
										
					int pos = 0;
										
					for( TorrentOpenOptions too: torrents ){
						
						Object[] entry = candidates.get( too );
						
						if ( entry == null ){
							
							logLine( viewer, indent, "No results for " + too.getDisplayName());
							
						}else{
							
							TOTorrent torrent = too.getTorrent();
							
							long torrent_size = torrent.getSize();
							
							long min_hit_size = torrent_size/10;
							
							int fc = torrent.getFileCount();
							
							int[] counts = (int[])entry[0];

							List<Object[]>	hit_entries = (List<Object[]>)entry[1];

							int matches =  counts[0];
							int pad		= counts[1];
							
							logLine( 
								viewer, indent, 
								too.getDisplayName() + 
									" (" + DisplayFormatters.formatByteCountToKiBEtc( torrent_size ) + 
									") has " + 
									hit_entries.size() + " locations with " + matches + " out of " + (fc-pad) + " matches:"  );
							
							List<File>	valid = new ArrayList<>();
							
							for ( Object[] e: hit_entries ){
								
								File 	f = (File)e[0];
								
								long size = (Long)e[1];
								
								int permille = (int)((size*1000)/torrent_size);
								
								logLine( viewer, indent+1, 
										f.getAbsolutePath() + 
										", match size=" + DisplayFormatters.formatByteCountToKiBEtc(size) + 
										" (" + DisplayFormatters.formatPercentFromThousands( permille ) + ")");
										
								
								if ( size >= min_hit_size ){
									
									valid.add( f );
									
								}else{
									
									logLine( viewer, indent+2, "Match is too small, ignoring" );
								}
							}
							
							if ( valid.size() == 1 ){
								
								results[pos] = valid.get(0);
								
								result_count[0]++;
								
								logLine( viewer, indent+2, "Selected for application" );
								
							}else if ( valid.size() > 1 ){
								
								logLine( viewer, indent+2, "Multiple locations require manual resolution" );
							}
						}
						
						pos++;
					}
										
				}catch( Throwable e ){

					log( viewer, 0, "\r\n" + new SimpleDateFormat().format( new Date()) + ": Failed: " + Debug.getNestedExceptionMessage( e ) + "\r\n" );
					
				}finally{

					Utils.execSWTThread(
							new Runnable()
							{
								public void
								run()
								{
									if ( !viewer.isDisposed()){

										boolean has_results = result_count[0] > 0;
										
										viewer.setOKEnabled( has_results );
										
										if ( has_results ){
											
											viewer.addListener(()->{
												
												if ( viewer.getOKPressed()){
												
													listener.accept( results );
												}
											});
										}
									}
								}
							});
				}			}
		}.start();
	}
	
	public static void
	locateSaveLocations(
		List<TorrentOpenOptions>	torrents,
		Shell						shell,
		Consumer<File[]>			listener )
	{
		locateFilesSupport( null, null, torrents, listener, shell );
	}
	
	private static void
	logLine(
		TextViewerWindow	viewer,
		int					indent,
		String				str )
	{	
		log( viewer, indent, str + "\r\n" );
	}

	private static void
	log(
		TextViewerWindow	viewer,
		int					indent,
		String				str )
	{
		String f_str;
		
		if ( indent == 0 ){
			f_str = str;
		}else{
			String prefix = "";
			for ( int i=0;i<indent;i++){
				prefix += "    ";
			}
			f_str = prefix + str;
		}
		
		Utils.execSWTThread(
			new Runnable()
			{
				@Override
				public void
				run()
				{
					if ( !viewer.isDisposed()){

						viewer.append2( f_str );
					}
				}
			});
	}

	private static int
	buildFileMap(
		TextViewerWindow		viewer,
		File					dir,
		Map<Long,Set<File>>		map,
		long[]					log_details,
		boolean[]				quit )
	{
		File[] files = dir.listFiles();

		int	total_files = 0;

		if ( files != null ){

			for ( File f: files ){

				synchronized( quit ){
					if ( quit[0] ){
	
						return( total_files );
					}
				}
				
				long	now = SystemTime.getMonotonousTime();

				long elapsed = now - log_details[2];
				
				long delay = Math.min( LOG_TICK_DOT_MIN + ( (LOG_TICK_DOT_MAX-LOG_TICK_DOT_MIN) * elapsed )/60000, LOG_TICK_DOT_MAX );																

				if ( now - log_details[0] > delay ){

					if ( log_details[1]++ > 80 ){
						
						logLine( viewer, 0, "" );
						
						log_details[1] = 1;
					}
					
					log( viewer, 0, "." );

					log_details[0] = now;
				}

				if ( f.isDirectory()){

					total_files += buildFileMap( viewer, f, map, log_details, quit );

				}else{

					long size = f.length();

					if ( size > 0 ){

						total_files++;

						Set<File> list = map.get( size );

						if ( list == null ){

							list = new HashSet<>();

							map.put( size, list );
						}

						list.add( f );
					}
				}
			}
		}

		return( total_files );
	}

	public static boolean
	canFindMoreLikeThis()
	{
		try{
			PluginInterface pi = CoreFactory.getSingleton().getPluginManager().getPluginInterfaceByID( "aercm");

			if (	pi != null &&
					pi.getPluginState().isOperational() &&
					pi.getIPC().canInvoke("lookupByExpression", new Object[]{ "", new String[0], new HashMap() })){

				return( true );
			}
		}catch( Throwable e ){

			Debug.out( e );
		}

		return( false );
	}

	public static void
	findMoreLikeThis(
		DownloadManager		dm,
		Shell				shell )
	{
		findMoreLikeThis( dm, null, shell );
	}

	public static void
	findMoreLikeThis(
		DiskManagerFileInfo		file,
		Shell					shell )
	{
		findMoreLikeThis( file.getDownloadManager(), file, shell );
	}

	private static void
	findMoreLikeThis(
		final DownloadManager			dm,
		final DiskManagerFileInfo		file,
		Shell							shell )
	{
		String expression = file==null?dm.getDisplayName():file.getFile( true ).getName();

		SimpleTextEntryWindow entryWindow =
				new SimpleTextEntryWindow(
						"find.more.like.title",
						"find.more.like.msg" );

		entryWindow.setPreenteredText( expression, false );

		entryWindow.selectPreenteredText(true);

		entryWindow.prompt(new UIInputReceiverListener() {
			@Override
			public void UIInputReceiverClosed(UIInputReceiver entryWindow) {
				if (!entryWindow.hasSubmittedInput()) {
					return;
				}

				String expression = entryWindow.getSubmittedInput();

				if ( expression != null && expression.trim().length() > 0 ){

					expression = expression.trim();

					try{
						PluginInterface pi = CoreFactory.getSingleton().getPluginManager().getPluginInterfaceByID( "aercm");

						if (	pi != null &&
								pi.getPluginState().isOperational() &&
								pi.getIPC().canInvoke("lookupByExpression", new Object[]{ "", new String[0], new HashMap() })){

							Map<String,Object>	options = new HashMap<>();

							options.put( "Subscription", true );
							//options.put( "Name", MessageText.getString( "label.more" ) + ": " + expression);
							options.put( "Name", expression);

							pi.getIPC().invoke(
								"lookupByExpression",
								new Object[]{
										expression,
									dm.getDownloadState().getNetworks(),
									options });
						}
					}catch( Throwable e ){

						Debug.out( e );
					}
				}
			}
		});



	}
	
	/**
	 * Takes account of whether the download has ever been started and if not selects between normal/delete 
	 * as opposed to normal/do-not-download
	 * @param file_info
	 * @param skipped
	 */
	
	public static void
	setFileSkipped(
		DiskManagerFileInfo		file_info,
		boolean					skipped )
	{
		if ( skipped ){
			
			DownloadManager dm = file_info.getDownloadManager();
			
			if ( dm != null && dm.getState() == DownloadManager.STATE_STOPPED ){
				
				if ( !dm.isDataAlreadyAllocated() && dm.getStats().getSecondsDownloading() <= 0 ){
			
					if ( !file_info.getFile( true ).exists()){
						
						int existing_storage_type = file_info.getStorageType();
						
						int compact_target;
						
						if ( existing_storage_type == DiskManagerFileInfo.ST_COMPACT || existing_storage_type == DiskManagerFileInfo.ST_LINEAR ){
							
							compact_target 		= DiskManagerFileInfo.ST_COMPACT;
							
						}else{
							
							compact_target 		= DiskManagerFileInfo.ST_REORDER_COMPACT;
						}
						
						int new_storage_type = compact_target;
						
						if ( existing_storage_type != new_storage_type ){
							
							file_info.setStorageType( new_storage_type );
						}
					}
				}
			}
		}
		
		file_info.setSkipped( skipped );
	}
	
	private static final Object LOW_RES_RECHECK_KEY = new Object();
	
	public static boolean
	canLowResourceRecheck(
		DownloadManager		dm )
	{
		if ( dm.getState() == DownloadManager.STATE_SEEDING ){
			
			if ( dm.getUserData( LOW_RES_RECHECK_KEY ) == null ){
				
				return( true );
			}
		}
		
		return( false );
	}
	
	public static void
	lowResourceRecheck(
		DownloadManager		dm )
	{
		if ( dm.getState() == DownloadManager.STATE_SEEDING && dm.getUserData( LOW_RES_RECHECK_KEY ) == null ){
						
			DiskManager diskManager = dm.getDiskManager();
			
			if ( diskManager != null ){
			
				dm.setUserData( LOW_RES_RECHECK_KEY, "" );

				DiskManagerCheckRequest req = diskManager.createCheckRequest( -1, null );
				
				req.setExplicit( true );
				
				diskManager.enqueueCompleteRecheckRequest(
					req,
					new DiskManagerCheckRequestListener(){
						
						boolean	failed = false;
						
						@Override
						public void checkFailed(DiskManagerCheckRequest request, Throwable cause){
							
							synchronized( this ){
								if ( failed ){
									return;
								}
								failed = true;
							}
							
							dm.stopIt( DownloadManager.STATE_STOPPED, false, false );
							
							dm.forceRecheck();
							
							dm.setUserData( LOW_RES_RECHECK_KEY, null );

						}
						
						@Override
						public void checkCompleted(DiskManagerCheckRequest request, boolean passed){
							
							dm.setUserData( LOW_RES_RECHECK_KEY, null );
						}
						
						@Override
						public void checkCancelled(DiskManagerCheckRequest request){
						}
					});
			}
		}
	}


	public static void
	advancedRename(
		DownloadManager[]		dms )
	{
		if ( dms.length < 1 ){
			return;
		}

		List<DownloadManager> list = new ArrayList<>(Arrays.asList(dms));
		advancedRename(list);
	}

	private static void advancedRename(List<DownloadManager> list) {
		if (list.isEmpty()) {
			return;
		}

		DownloadManager dm = list.remove(0);
		AdvRenameWindow window = new AdvRenameWindow(dm);
		window.open(result -> {
			if (result == -1 || list.isEmpty()) {
				return;
			}

			advancedRename(list);
		});
	}
	
	public static void
	viewLinks(
		DownloadManager[]	dms )
	{
		StringBuilder details = new StringBuilder( 32*1024 );
		
		for ( DownloadManager dm: dms ){
								
			DiskManagerFileInfoSet fis = dm.getDiskManagerFileInfoSet();

			DiskManagerFileInfo[] files = fis.getFiles();

			boolean done_name = false;
			
			for ( DiskManagerFileInfo file_info: files ){

				File file_link 		= file_info.getFile( true );
				File file_nolink 	= file_info.getFile( false );

				if ( !file_nolink.getAbsolutePath().equals( file_link.getAbsolutePath())){

					if ( dms.length > 1 && !done_name ){
						
						done_name = true;
					
						if ( details.length() > 0 ){
							details.append( "\n" );
						}
						
						details.append( dm.getDisplayName());
						details.append( "\n\n" );
					}
			
					details.append( "    " );
					details.append( file_nolink.getAbsolutePath());
					details.append(" -> " );
					details.append( file_link.getAbsolutePath());
					details.append( "\n" );
				}
			}
		}
		
		TextViewerWindow viewer =
				new TextViewerWindow(
        			  Utils.findAnyShell(),
        			  "view.links.title",
        			  "view.links.text",
        			  details.toString(), true, true );

		viewer.setEditable( false );
		
		viewer.setCancelEnabled( false );
		
		viewer.setNonProportionalFont();
		
		viewer.goModal();
	}
}
