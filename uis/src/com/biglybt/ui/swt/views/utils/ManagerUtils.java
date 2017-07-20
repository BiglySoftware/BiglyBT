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
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.*;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.torrent.PlatformTorrentUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import com.biglybt.core.config.COConfigurationManager;
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
import com.biglybt.ui.swt.SimpleTextEntryWindow;
import com.biglybt.ui.swt.TextViewerWindow;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.TorrentOpener;
import com.biglybt.ui.swt.shells.CoreWaiterSWT;
import com.biglybt.ui.swt.shells.CoreWaiterSWT.TriggerInThread;
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
							String								root,
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
								"  <address>Vuze Web Server at " + host + " Port " + getServerPort() +"</address>" + NL +
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

								TorrentOpenOptions torrent_options = new TorrentOpenOptions( torrent_file.getAbsolutePath(), torrent, false );

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
      return false;
    }
    return true;
  }

  public static boolean isPauseable(DownloadManager dm) {
	    if(dm == null)
	      return false;
	    int state = dm.getState();
	    if (	state == DownloadManager.STATE_STOPPED ||
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

		if (state == DownloadManager.STATE_STOPPED
				|| state == DownloadManager.STATE_STOPPING ){
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
			return;
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

	  TorrentUtils.startTorrentDelete();

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

							  TorrentUtils.endTorrentDelete();

							  endDone[0] = true;
						  }
					  }
				  }
			  }
		  });

	  }catch( Throwable e ){

		  synchronized( endDone ){

			  if ( !endDone[0] ){

				  TorrentUtils.endTorrentDelete();

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
    			dm.stopIt( stateAfterStopped, false, false );
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
    			dm.pause();
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

						long target_time = SystemTime.getOffsetTime( seconds*1000);

						for ( DownloadManager dm: dms ){

							if ( !isPauseable( dm )){

								continue;
							}

							if ( dm.pause( target_time )){

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
												dm.resume();

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
		DirectoryDialog dd = new DirectoryDialog( shell );

		dd.setFilterPath( TorrentOpener.getFilterPathData());

		dd.setText(MessageText.getString("MyTorrentsView.menu.locatefiles.dialog"));

		String path = dd.open();

		if ( path != null ){

			TorrentOpener.setFilterPathData( path );

			final File	dir = new File( path );

			final TextViewerWindow viewer =
					new TextViewerWindow(
							MessageText.getString( "locatefiles.view.title" ),
							null, "", true, true );

			viewer.setEditable( false );

			viewer.setOKEnabled( true );

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
								}
							});

						logLine( viewer, new SimpleDateFormat().format( new Date()) +  ": Enumerating files in " + dir );

						long bfm_start = SystemTime.getMonotonousTime();

						long[] last_log = { bfm_start };

						int file_count = buildFileMap( viewer, dir, file_map, last_log, quit );

						logLine( viewer, (bfm_start==last_log[0]?"":"\r\n") + "Found " + file_count + " files with " + file_map.size() + " distinct sizes" );

						Set<String>	all_dm_incomplete_files = null;

						ConcurrentHasher hasher = ConcurrentHasher.getSingleton();

						int	downloads_modified = 0;

						for ( int i=0;i<dms.length;i++){

							DownloadManager			dm 				= dms[i];

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

							long	piece_size = torrent.getPieceLength();

							byte[][] pieces = torrent.getPieces();

							logLine( viewer, "Processing '" + dm.getDisplayName() + "', piece size=" + DisplayFormatters.formatByteCountToKiBEtc( piece_size ));

							int dm_state = dm.getState();

							if ( ! ( dm_state == DownloadManager.STATE_STOPPED || dm_state == DownloadManager.STATE_ERROR )){

								logLine( viewer, "    Download must be stopped" );

								continue;
							}

							DiskManagerFileInfo[] files = dm.getDiskManagerFileInfoSet().getFiles();

							Set<String>	dm_files = null;

							Map<DiskManagerFileInfo,File>		links_established = new HashMap<>();

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
												return( 0 );
											}
										}
									});

							int	no_candidates 		= 0;
							int	already_complete	= 0;

							int	link_count = 0;

							try{

download_loop:
								for ( final DiskManagerFileInfo file: files ){

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

										already_complete++;

										continue;
									}

									Set<File> candidates = file_map.get( file_length );

									if ( candidates != null ){

										if ( candidates.size() > 0 ){

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

											Iterator<File> it = candidates.iterator();

											while( it.hasNext()){

												File f = it.next();

												if ( all_dm_incomplete_files.contains( f.getAbsolutePath())){

													it.remove();
												}
											}
										}

										if ( candidates.size() > 0 ){

												// duplicate now as this is download-specific

											candidates = new HashSet<>( candidates );

												// remove all files from this download

											if ( dm_files == null ){

												dm_files = new HashSet<>();

												for ( DiskManagerFileInfo f: files ){

													dm_files.add( f.getFile( true ).getAbsolutePath());
												}
											}

											Iterator<File> it = candidates.iterator();

											while( it.hasNext()){

												File f = it.next();

												if ( dm_files.contains( f.getAbsolutePath())){

													it.remove();
												}
											}
										}

										if ( candidates.size() > 0 ){

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

											long	to_stop_at = file_length - piece_size;

											if ( to_file_offset < to_stop_at ){

												logLine( viewer, "    " + candidates.size() + " candidate(s) for " + to_file.getRelativePath() + " (size=" + DisplayFormatters.formatByteCountToKiBEtc(to_file.getLength()) + ")");

												byte[]	buffer = new byte[(int)piece_size];

												for ( File candidate: candidates ){

													synchronized( quit ){
														if ( quit[0] ){
															break;
														}
													}

													log( viewer, "        Testing " + candidate );

													RandomAccessFile raf = null;

													boolean	error 			= false;
													boolean	hash_failed		= false;

													long	last_ok_log = SystemTime.getMonotonousTime();

													try{
														raf = new RandomAccessFile( candidate, "r" );

														long 	file_offset 	= to_file_offset;
														int		piece_number 	= to_piece_number;

														while( file_offset < to_stop_at ){

															synchronized( quit ){
																if ( quit[0] ){
																	break;
																}
															}

															raf.seek( file_offset );

															raf.read( buffer );

															ConcurrentHasherRequest req = hasher.addRequest( ByteBuffer.wrap( buffer ));

															byte[] hash = req.getResult();

															boolean	match = Arrays.equals( pieces[piece_number], hash );

															if ( match ){

																long now = SystemTime.getMonotonousTime();

																if ( now - last_ok_log >= 250 ){

																	last_ok_log = now;

																	log( viewer, "." );
																}

																file_offset += piece_size;
																piece_number++;

															}else{

																hash_failed = true;

																failed_candidates.add( candidate.getAbsolutePath());

																logLine( viewer, "X" );

																break;
															}

														}
													}catch( Throwable e ){

														logLine( viewer, "X" );

														error = true;

													}finally{

														if ( raf != null ){

															try{
																raf.close();

															}catch( Throwable e ){

															}
														}
													}

													if ( !( error || hash_failed )){

														logLine( viewer, " Matched" );

														try{
															dm.setUserData( "set_link_dont_delete_existing", true );

															if ( file.setLink( candidate )){

																logLine( viewer, "        Link successful" );

																links_established.put( file, candidate );

																link_count++;

																matched = true;

																if ( link_count > MAX_LINKS ){

																	logLine( viewer, "    " + LINK_LIMIT_MSG );

																	break download_loop;
																}
															}else{

																logLine( viewer, "        Link failed" );
															}
														}finally{

															dm.setUserData( "set_link_dont_delete_existing", null );

														}
														break;
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

								logLine( viewer, "    Matched=" + links_established.size() + ", complete=" + already_complete + ", no candidates=" + no_candidates + ", remaining=" + unmatched_files.size() + " (total=" + files.length + ")");

								if ( links_established.size() > 0 && unmatched_files.size() > 0 ){

									logLine( viewer, "    Looking for other potential name-based matches" );

									File overall_root = null;

									for ( Map.Entry<DiskManagerFileInfo,File> entry: links_established.entrySet()){

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

											logLine( viewer, "        No usable root folder found" );

											break;
										}

										if ( overall_root == null ){

											overall_root = root;

										}else{

											if ( !overall_root.equals( root )){

												overall_root = null;

												logLine( viewer, "        Inconsistent root folder found" );

												break;
											}
										}
									}

									if ( overall_root != null ){

										logLine( viewer, "        Root folder is " + overall_root.getAbsolutePath());

										int links_ok = 0;

										for ( Map.Entry<DiskManagerFileInfo,Set<String>> entry: unmatched_files.entrySet()){

											synchronized( quit ){
												if ( quit[0] ){
													break;
												}
											}

											DiskManagerFileInfo file = entry.getKey();

											if ( selected_file_indexes != null ){

												if ( !selected_file_indexes.contains( file.getIndex())){

													continue;
												}
											}

											File expected_file = new File( overall_root, file.getTorrentFile().getRelativePath());

											if ( expected_file.exists() && expected_file.length() == file.getLength()){

												if ( !entry.getValue().contains( expected_file.getAbsolutePath())){

													try{
														dm.setUserData( "set_link_dont_delete_existing", true );

														if ( file.setLink( expected_file )){

															links_ok++;

															link_count++;

															if ( link_count > MAX_LINKS ){

																logLine( viewer, "        " + LINK_LIMIT_MSG );

																break;
															}
														}
													}finally{

														dm.setUserData( "set_link_dont_delete_existing", null );
													}
												}
											}
										}

										logLine( viewer, "        Linked " + links_ok + " of " + unmatched_files.size());
									}
								}
							}finally{

								if ( link_count > 0 ){

									dm.forceRecheck();

									downloads_modified++;
								}
							}
						}

						logLine( viewer, new SimpleDateFormat().format( new Date()) +  ": Complete, downloads updated=" + downloads_modified );

					}catch( Throwable e ){

						log( viewer, "\r\n" + new SimpleDateFormat().format( new Date()) + ": Failed: " + Debug.getNestedExceptionMessage( e ) + "\r\n" );
					}
				}
			}.start();

			viewer.goModal();
		}
	}

	private static void
	logLine(
		TextViewerWindow	viewer,
		String				str )
	{
		log( viewer, str + "\r\n" );
	}

	private static void
	log(
		final TextViewerWindow		viewer,
		final String				str )
	{
		Utils.execSWTThread(
			new Runnable()
			{
				@Override
				public void
				run()
				{
					if ( !viewer.isDisposed()){

						viewer.append( str );
					}
				}
			});
	}

	private static int
	buildFileMap(
		TextViewerWindow		viewer,
		File					dir,
		Map<Long,Set<File>>		map,
		long[]					last_log,
		boolean[]				quit )
	{
		File[] files = dir.listFiles();

		int	total_files = 0;

		if ( files != null ){

			for ( File f: files ){

				if ( quit[0] ){

					return( total_files );
				}

				long	now = SystemTime.getMonotonousTime();

				if ( now - last_log[0] > 250 ){

					log( viewer, "." );

					last_log[0] = now;
				}

				if ( f.isDirectory()){

					total_files += buildFileMap( viewer, f, map, last_log, quit );

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
							options.put( "Name", MessageText.getString( "label.more" ) + ": " + expression);

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
}
