/*
 * Created on Mar 20, 2013
 * Created by Paul Gardner
 *
 * Copyright 2013 Azureus Software, Inc.  All rights reserved.
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


package com.biglybt.core.tag.impl;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.*;

import com.biglybt.core.Core;
import com.biglybt.core.CoreComponent;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreLifecycleAdapter;
import com.biglybt.core.CoreOperation;
import com.biglybt.core.CoreOperationTask;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerException;
import com.biglybt.core.download.DownloadManagerInitialisationAdapter;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.tag.*;
import com.biglybt.core.tag.TagFeatureProperties.TagProperty;
import com.biglybt.core.tag.impl.TagTypeBase.TagGroupImpl;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.*;
import com.biglybt.core.util.DataSourceResolver.DataSourceImporter;
import com.biglybt.core.vuzefile.VuzeFile;
import com.biglybt.core.vuzefile.VuzeFileComponent;
import com.biglybt.core.vuzefile.VuzeFileHandler;
import com.biglybt.core.vuzefile.VuzeFileProcessor;
import com.biglybt.core.xml.util.XMLEscapeWriter;
import com.biglybt.core.xml.util.XUXmlWriter;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.PluginManager;
import com.biglybt.pif.disk.DiskManagerFileInfo;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadCompletionListener;
import com.biglybt.pif.download.DownloadScrapeResult;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.tracker.web.TrackerWebPageRequest;
import com.biglybt.pif.tracker.web.TrackerWebPageResponse;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.UIManagerEvent;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.pif.ui.tables.TableColumnCreationListener;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.pif.utils.ScriptProvider;
import com.biglybt.pif.utils.StaticUtilities;
import com.biglybt.pifimpl.PluginUtils;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.plugin.rssgen.RSSGeneratorPlugin;
import com.biglybt.util.MapUtils;
import com.biglybt.core.torrent.PlatformTorrentUtils;

public class
TagManagerImpl
	implements TagManager, DownloadCompletionListener, AEDiagnosticsEvidenceGenerator, DataSourceImporter
{
	private static final String	CONFIG_FILE 				= "tag.config";

		// order is important as 'increases' in effects (see applyConfigUpdates)

	private static final int CU_TAG_CREATE		= 1;
	private static final int CU_TAG_CHANGE		= 2;
	private static final int CU_TAG_CONTENTS	= 3;
	private static final int CU_TAG_REMOVE		= 4;

	private static final boolean	enabled = COConfigurationManager.getBooleanParameter( "tagmanager.enable", true );

	private static TagManagerImpl	singleton;

	public static synchronized TagManagerImpl
	getSingleton()
	{
		if ( singleton == null ){

			singleton = new TagManagerImpl();

			singleton.init();
		}

		return( singleton );
	}

	final CopyOnWriteList<TagTypeBase>	tag_types = new CopyOnWriteList<>();

	private final Map<Integer,TagType>	tag_type_map = new HashMap<>();

	private static final String RSS_PROVIDER	= "tags";

	final Set<TagBase>	rss_tags = new HashSet<>();

	final Set<DownloadManager>	active_copy_on_complete = new IdentityHashSet<>();

	private final RSSGeneratorPlugin.Provider rss_generator =
		new RSSGeneratorPlugin.Provider()
		{
			@Override
			public boolean
			isEnabled()
			{
				return( true );
			}

			@Override
			public boolean
			generate(
				TrackerWebPageRequest		request,
				TrackerWebPageResponse		response )

				throws IOException
			{
				URL	url	= request.getAbsoluteURL();

				String path = url.getPath();

				String	query = url.getQuery();

				if ( query != null ){

					path += "?" + query;
				}

				int	pos = path.indexOf( '?' );

				if ( pos != -1 ){

					String args = path.substring( pos+1 );

					path = path.substring(0,pos);

					if ( path.endsWith( "GetTorrent" )){

						String[] bits = args.split( "&" );

						for ( String bit: bits ){

							String[] temp = bit.split( "=" );

							if ( temp.length == 2 ){

								if ( temp[0].equals( "hash" )){

									try{
										Download download = CoreFactory.getSingleton().getPluginManager().getDefaultPluginInterface().getDownloadManager().getDownload( Base32.decode( temp[1] ));

										Torrent torrent = download.getTorrent();

										torrent = torrent.getClone();

										torrent = torrent.removeAdditionalProperties();

										response.getOutputStream().write( torrent.writeToBEncodedData());

										response.setContentType( "application/x-bittorrent" );

										return( true );

									}catch( Throwable e ){

									}
								}
							}
						}

						response.setReplyStatus( 404 );

						return( true );

					}else if ( path.endsWith( "GetThumbnail" )){

						String[] bits = args.split( "&" );

						for ( String bit: bits ){

							String[] temp = bit.split( "=" );

							if ( temp.length == 2 ){

								if ( temp[0].equals( "hash" )){

									try{
										Download download = CoreFactory.getSingleton().getPluginManager().getDefaultPluginInterface().getDownloadManager().getDownload( Base32.decode( temp[1] ));

										DownloadManager	core_download = PluginCoreUtils.unwrap( download );

										TOTorrent torrent  = core_download.getTorrent();

										byte[] thumb = PlatformTorrentUtils.getContentThumbnail( torrent );

										if ( thumb != null ){

											response.getOutputStream().write( thumb );

											String thumb_type = PlatformTorrentUtils.getContentThumbnailType( torrent );

											if ( thumb_type == null || thumb_type.length() == 0 ){

												thumb_type = "image/jpeg";
											}

											response.setContentType( thumb_type );

											return( true );
										}
									}catch( Throwable e ){

									}
								}
							}
						}

						response.setReplyStatus( 404 );

						return( true );
					}
				}

				path = path.substring( RSS_PROVIDER.length()+1);

				XMLEscapeWriter pw = new XMLEscapeWriter( new PrintWriter(new OutputStreamWriter( response.getOutputStream(), "UTF-8" )));

				pw.setEnabled( false );

				if ( path.length() <= 1 ){

					response.setContentType( "text/html; charset=UTF-8" );

					pw.println( "<HTML><HEAD><TITLE>" + Constants.APP_NAME + " Tag Feeds</TITLE></HEAD><BODY>" );

					Map<String,String>	lines = new TreeMap<>();

					List<TagBase>	tags;

					synchronized( rss_tags ){

						tags = new ArrayList<>(rss_tags);
					}

					for ( TagBase t: tags ){

						if ( t instanceof TagDownload ){

							if ( ((TagFeatureRSSFeed)t).isTagRSSFeedEnabled()){

								String	name = t.getTagName( true );

								String	tag_url = RSS_PROVIDER + "/" + t.getTagType().getTagType()+"-" + t.getTagID();

								lines.put( name, "<LI><A href=\"" + tag_url + "\">" + name + "</A>&nbsp;&nbsp;-&nbsp;&nbsp;<font size=\"-1\"><a href=\"" + tag_url + "?format=html\">html</a></font></LI>" );
							}
						}
					}

					for ( String line: lines.values() ){

						pw.println( line );
					}

					pw.println( "</BODY></HTML>" );

				}else{

					String	tag_id = path.substring( 1 );

					String[] bits = tag_id.split( "-" );

					int	tt_id 	= Integer.parseInt( bits[0] );
					int	t_id 	= Integer.parseInt( bits[1] );

					TagDownload	tag = null;

					synchronized( rss_tags ){

						for ( TagBase t: rss_tags ){

							if ( t.getTagType().getTagType() == tt_id && t.getTagID() == t_id ){

								if ( t instanceof TagDownload ){

									tag = (TagDownload)t;
								}
							}
						}
					}

					if ( tag == null ){

						response.setReplyStatus( 404 );

						return( true );
					}

					boolean	enable_low_noise = RSSGeneratorPlugin.getSingleton().isLowNoiseEnabled();


					Set<DownloadManager> dms = tag.getTaggedDownloads();

					List<Download> downloads = new ArrayList<>(dms.size());

					long	dl_marker = 0;

					for ( DownloadManager dm: dms ){

						TOTorrent torrent = dm.getTorrent();

						if ( torrent == null ){

							continue;
						}

						DownloadManagerState state = dm.getDownloadState();

						if ( state.getFlag( DownloadManagerState.FLAG_METADATA_DOWNLOAD )){

							continue;
						}

						if ( !enable_low_noise ){

							if (  state.getFlag( DownloadManagerState.FLAG_LOW_NOISE )){

								continue;
							}
						}

						if ( !TorrentUtils.isReallyPrivate( torrent )){

							dl_marker += dm.getDownloadState().getLongParameter( DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME );

							downloads.add( PluginCoreUtils.wrap(dm));
						}
					}

					if ( url.toExternalForm().contains( "format=html" )){

						String	host = (String)request.getHeaders().get( "host" );

						if ( host != null ){

							int	c_pos = host.indexOf( ':' );

							if ( c_pos != -1 ){

								host = host.substring( 0, c_pos );
							}
						}else{

							host = "127.0.0.1";
						}

						response.setContentType( "text/html; charset=UTF-8" );

						pw.println( "<HTML><HEAD><TITLE>Tag: " + escape( tag.getTagName( true )) + "</TITLE></HEAD><BODY>" );

						PluginManager pm = CoreFactory.getSingleton().getPluginManager();

						PluginInterface pi = pm.getPluginInterfaceByID( "azupnpav", true );

						if ( pi == null ){

							pw.println( "UPnP Media Server plugin not found" );

						}else{

							for (int i=0;i<downloads.size();i++){

								Download download = downloads.get( i );

								DiskManagerFileInfo[] files = download.getDiskManagerFileInfo();

								for ( DiskManagerFileInfo file: files ){

									File target_file = file.getFile( true );

									if ( !target_file.exists()){

										continue;
									}

									try{
										URL stream_url = new URL((String)pi.getIPC().invoke("getContentURL", new Object[] { file }));

						  				if ( stream_url != null ){

						  					stream_url = UrlUtils.setHost( stream_url, host );

						  					String url_ext = stream_url.toExternalForm();

						  					pw.println( "<p>" );

						  					pw.println( "<a href=\"" + url_ext + "\">" + escape( target_file.getName()) + "</a>" );

						  					url_ext += url_ext.indexOf('?') == -1?"?":"&";

						  					url_ext += "action=download";

						  					pw.println( "&nbsp;&nbsp;-&nbsp;&nbsp;<font size=\"-1\"><a href=\"" + url_ext + "\">save</a></font>" );
						  				}
									}catch( Throwable e ){

										e.printStackTrace();
									}
								}
							}
						}

						pw.println( "</BODY></HTML>" );

					}else{

						String	config_key = "tag.rss.config." + tt_id + "." + t_id;

						long	old_marker = COConfigurationManager.getLongParameter( config_key + ".marker", 0 );

						long	last_modified = COConfigurationManager.getLongParameter( config_key + ".last_mod", 0 );

						long now = SystemTime.getCurrentTime();

						if ( old_marker == dl_marker ){

							if ( last_modified == 0 ){

								last_modified = now;
							}
						}else{

							COConfigurationManager.setParameter( config_key + ".marker", dl_marker );

							last_modified = now;
						}

						if ( last_modified == now ){

							COConfigurationManager.setParameter( config_key + ".last_mod", last_modified );
						}

						response.setContentType( "application/xml; charset=UTF-8" );

						pw.println( "<?xml version=\"1.0\" encoding=\"utf-8\"?>" );

						pw.println( "<rss version=\"2.0\" xmlns:media=\"http://search.yahoo.com/mrss/\" xmlns:vuze=\"http://www.vuze.com\">" );

						pw.println( "<channel>" );

						pw.println( "<title>" + escape( tag.getTagName( true )) + "</title>" );

						Collections.sort(
							downloads,
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


						pw.println(	"<pubDate>" + TimeFormatter.getHTTPDate( last_modified ) + "</pubDate>" );

						for (int i=0;i<downloads.size();i++){

							Download download = downloads.get( i );

							DownloadManager	core_download = PluginCoreUtils.unwrap( download );

							Torrent 	torrent 	= download.getTorrent();
							TOTorrent 	to_torrent 	= core_download.getTorrent();

							byte[] hash = torrent.getHash();

							String	hash_str = Base32.encode( hash );

							pw.println( "<item>" );

							pw.println( "<title>" + escape( download.getName()) + "</title>" );

							String desc  = PlatformTorrentUtils.getContentDescription( to_torrent );

							if ( desc != null && desc.length() > 0 ){

								desc = desc.replaceAll( "\r\n", "<br>" );
								desc = desc.replaceAll( "\n", "<br>" );
								desc = desc.replaceAll( "\t", "    " );

								pw.println( "<description>" + escape( desc) + "</description>" );
							}

							pw.println( "<guid>" + hash_str + "</guid>" );

							String magnet_uri = UrlUtils.getMagnetURI( download );

							String obtained_from = TorrentUtils.getObtainedFrom( core_download.getTorrent());

							String[] dl_nets = core_download.getDownloadState().getNetworks();

							boolean	added_fl = false;

							if ( obtained_from != null ){

								try{
									URL ou = new URL( obtained_from );

									if ( ou.getProtocol().toLowerCase( Locale.US ).startsWith( "http" )){

										String host = ou.getHost();

											// make sure the originator network is compatible with the ones enabled
											// for the download

										String net = AENetworkClassifier.categoriseAddress( host );

										boolean	net_ok = false;

										if ( dl_nets == null || dl_nets.length == 0 ){

											net_ok = true;

										}else{

											for ( String dl_net: dl_nets ){

												if ( dl_net == net ){

													net_ok = true;

													break;
												}
											}
										}

										if ( net_ok ){

											magnet_uri += "&fl=" + UrlUtils.encode( ou.toExternalForm());

											added_fl = true;
										}
									}
								}catch( Throwable e ){

								}
							}

								// in theory we could add multiple &fls but it keeps things less confusing
								// and more efficient to just use one - if an external link is available and
								// the torrent file is a reasonable size and the rss feed is popular then this
								// can avoid quite a bit of load - plus it reduces the size of magnet URI

							if ( !added_fl ){

								String host = (String)request.getHeaders().get( "host" );

								if ( host != null ){

										// don't need to check network here as we are replying with the network
										// used to contact us

									String local_fl = url.getProtocol() + "://" + host + "/" + RSS_PROVIDER + "/GetTorrent?hash=" + Base32.encode( torrent.getHash());

									magnet_uri += "&fl=" + UrlUtils.encode( local_fl );
								}
							}

							magnet_uri = escape( magnet_uri );

							pw.println( "<link>" + magnet_uri + "</link>" );

							long added = core_download.getDownloadState().getLongParameter(DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME);

							pw.println(	"<pubDate>" + TimeFormatter.getHTTPDate( added ) + "</pubDate>" );

							pw.println(	"<vuze:size>" + torrent.getSize()+ "</vuze:size>" );
							pw.println(	"<vuze:assethash>" + hash_str + "</vuze:assethash>" );

							pw.println( "<vuze:downloadurl>" + magnet_uri + "</vuze:downloadurl>" );

							DownloadScrapeResult scrape = download.getLastScrapeResult();

							if ( scrape != null && scrape.getResponseType() == DownloadScrapeResult.RT_SUCCESS ){

								pw.println(	"<vuze:seeds>" + scrape.getSeedCount() + "</vuze:seeds>" );
								pw.println(	"<vuze:peers>" + scrape.getNonSeedCount() + "</vuze:peers>" );
							}

							byte[] thumb = PlatformTorrentUtils.getContentThumbnail( to_torrent );

							if ( thumb != null ){

								String host = (String)request.getHeaders().get( "host" );

								if ( host != null ){

										// don't need to check network here as we are replying with the network
										// used to contact us

									String thumb_url = url.getProtocol() + "://" + host + "/" + RSS_PROVIDER + "/GetThumbnail?hash=" + Base32.encode( torrent.getHash());

									pw.println( "<media:thumbnail url=\"" + thumb_url + "\"/>" );
								}
							}

							pw.println( "</item>" );
						}

						pw.println( "</channel>" );

						pw.println( "</rss>" );
					}
				}

				pw.flush();

				return( true );
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
		};

	final AsyncDispatcher async_dispatcher = new AsyncDispatcher(5000);
	
	final AsyncDispatcher move_on_assign_dispatcher = new AsyncDispatcher(5000);

	private final FrequencyLimitedDispatcher dirty_dispatcher =
		new FrequencyLimitedDispatcher(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
						// always go async to avoid blocking caller

					new AEThread2( "tag:fld" )
					{
						@Override
						public void
						run()
						{
							try{
									// just in case there's a bunch of changes coming in together

								Thread.sleep( 1000 );

							}catch( Throwable e ){

							}

							writeConfig();
						}
					}.start();
				}
			},
			30*1000 );


	private Map<String,Object>					config;
	private WeakReference<Map<String,Object>>	config_ref;

	private boolean				config_dirty;

	private final List<Object[]>		config_change_queue = new ArrayList<>();


	private final CopyOnWriteList<TagManagerListener>		listeners = new CopyOnWriteList<>();

	private final CopyOnWriteList<Object[]>				feature_listeners = new CopyOnWriteList<>();

	private final Map<Long,LifecycleHandlerImpl>			lifecycle_handlers = new HashMap<>();

	private TagPropertyTrackerHandler 		auto_tracker;
	private TagPropertyUntaggedHandler		untagged_handler;

	private TagPropertyConstraintHandler	constraint_handler;

	private boolean		js_plugin_install_tried;

	private
	TagManagerImpl()
	{
		DataSourceResolver.registerExporter( this );
		
		AEDiagnostics.addWeakEvidenceGenerator( this );
		
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

								// do tracker templates first as maybe needed by Tag... If other uses of templates 
								// are added then we need to address the dependency issue in a more generic way
							
							for (int j=0;j<comps.length;j++){

								VuzeFileComponent comp = comps[j];

								int	type = comp.getType();

								if ( type == VuzeFileComponent.COMP_TYPE_TRACKER_TEMPLATE ){

									Map map = comp.getContent();
											
									map = BDecoder.decodeStrings( map );
									
									String tt_name = (String)map.get( "name");
									
									List<List<String>>	tt_template = (List<List<String>>)map.get( "template" );
									
									Map<String,List<List<String>>> m_t = TrackersUtil.getInstance().getMultiTrackers();
									
									if ( m_t.containsKey( tt_name )){
										
										Debug.out( "Tracker template '" + tt_name + "' already exists, ignoring import" );
										
									}else{
										
										TrackersUtil.getInstance().addMultiTracker( tt_name, tt_template );
										
										comp.setProcessed();
									}
								}
							}
							
							for (int j=0;j<comps.length;j++){

								VuzeFileComponent comp = comps[j];

								int	type = comp.getType();

								if ( type == VuzeFileComponent.COMP_TYPE_TAG ){
									
									Tag tag = importVuzeFile( comp.getContent());
									
									if ( tag != null ){
									
										comp.setProcessed();
										
										UIManager ui_manager = StaticUtilities.getUIManager( 120*1000 );

										String details = MessageText.getString(
												"tag.import.ok.desc",
												new String[]{ tag.getTagName( true )});

										ui_manager.showMessageBox(
												"tag.import.ok.title",
												"!" + details + "!",
												UIManagerEvent.MT_OK );
									}
								}
							}
						}
					}
				});
	}

	@Override
	public boolean
	isEnabled()
	{
		return( enabled );
	}

	private void
	init()
	{
		if ( !enabled ){

			return;
		}

		Core core = CoreFactory.getSingleton();

		auto_tracker = new TagPropertyTrackerHandler( core, this );

		untagged_handler = new TagPropertyUntaggedHandler( core, this );

		new TagPropertyTrackerTemplateHandler( core, this );

		constraint_handler = new TagPropertyConstraintHandler( core, this );

		core.addLifecycleListener(
			new CoreLifecycleAdapter()
			{
				@Override
				public void
				started(
					Core core )
				{
					core.getPluginManager().getDefaultPluginInterface().getDownloadManager().getGlobalDownloadEventNotifier().addCompletionListener( TagManagerImpl.this);
				}

				@Override
				public void
				componentCreated(
					Core core,
					CoreComponent component )
				{
					if ( component instanceof GlobalManager ){

						initializeSwarmTags();
						
						GlobalManager global_manager = (GlobalManager)component;

						global_manager.addDownloadManagerInitialisationAdapter(
								new DownloadManagerInitialisationAdapter()
								{
									@Override
									public int
									getActions()
									{
										return( ACT_ASSIGNS_TAGS );
									}

									@Override
									public void
									initialised(
										DownloadManager 	manager,
										boolean				for_seeding )
									{
										com.biglybt.core.disk.DiskManagerFileInfo[] files = manager.getDiskManagerFileInfoSet().getFiles();

										for ( com.biglybt.core.disk.DiskManagerFileInfo file: files ){

											if ( file.getTorrentFile().getPathComponents().length == 1 ){

												String name = file.getTorrentFile().getRelativePath().toLowerCase( Locale.US );

												if ( name.equals( "index.html" ) || name.equals( "index.htm" )){

													TagType tt = TagManagerFactory.getTagManager().getTagType( TagType.TT_DOWNLOAD_MANUAL );

													String tag_name = "Websites";

													Tag tag = tt.getTag( tag_name, true );

													try{
														if ( tag == null ){

															tag = tt.createTag( tag_name, true );
														}

														if ( !tag.hasTaggable( manager )){

															tag.addTaggable( manager );

															tag.setDescription( MessageText.getString( "tag.website.desc" ));
														}
													}catch( Throwable e ){

														Debug.out( e );
													}

													break;
												}
											}
										}
									}
								});

						global_manager.addDownloadManagerInitialisationAdapter(
							new DownloadManagerInitialisationAdapter()
							{
								@Override
								public int
								getActions()
								{
									return( ACT_PROCESSES_TAGS );
								}

								@Override
								public void
								initialised(
									DownloadManager 	manager,
									boolean				for_seeding )
								{
									if ( for_seeding ){

										return;
									}

										// perform any auto-tagging - note that auto-tags aren't applied to the download
										// yet

									List<Tag> auto_tags = auto_tracker.getTagsForDownload( manager );

									Set<Tag> tags = new HashSet<>(getTagsForTaggable(TagType.TT_DOWNLOAD_MANUAL, manager));

									tags.addAll( auto_tags );

									if ( tags.size() == 0 ){

											// pick up untagged tags here as they haven't been added yet :(

										tags.addAll( untagged_handler.getUntaggedTags());
									}

									TagFeatureFileLocation tag = TagUtils.selectInitialDownloadLocation(tags);

									if ( tag != null ){
										
										long	options = tag.getTagInitialSaveOptions();

										boolean set_data 	= (options&TagFeatureFileLocation.FL_DATA) != 0;
										boolean set_torrent = (options&TagFeatureFileLocation.FL_TORRENT) != 0;

										File new_loc = tag.getTagInitialSaveFolder();

										if ( set_data ){

											File old_loc = manager.getSaveLocation();

											if ( !new_loc.equals( old_loc )){

													// it is possible some folder structure has already been created, we need to remove this
													// as simply setting the save dir doesn't do this.
													// example is if we have a dnd-dir configured and one or more skipped files. this
													// results in the dnd-dir(s) being pre-created and they will remain in the old
													// save location if we don't remove them+it
												
												if ( old_loc.isDirectory()){
													
													TorrentUtils.recursiveEmptyDirDelete( old_loc, false );
												}
												
												manager.setTorrentSaveDir(FileUtil.newFile(
													new_loc.getAbsolutePath()), false);
											}
										}

										if ( set_torrent ){

											File old_torrent_file = FileUtil.newFile( manager.getTorrentFileName());

											if ( old_torrent_file.exists()){

												try{
													manager.setTorrentFile( new_loc,  old_torrent_file.getName());

												}catch( Throwable e ){

													Debug.out( e );
												}
											}
										}
									}
								}
							});
					}
				}

				@Override
				public void
				stopped(
					Core core )
				{
					destroy();
				}
			});

		SimpleTimer.addPeriodicEvent(
			"TM:Sync",
			30*1000,
			new TimerEventPerformer()
			{
				@Override
				public void
				perform(
					TimerEvent event)
				{
					for ( TagType tt: tag_types ){

						((TagTypeBase)tt).sync();
					}
					
					auto_tracker.sync();
				}
			});
	}

	@Override
	public void
	setProcessingEnabled(
		boolean	enabled )
	{
		if ( constraint_handler != null ){

			constraint_handler.setProcessingEnabled( enabled );
		}
	}

	@Override
	public void
	onCompletion(
		Download d )
	{
		final DownloadManager manager = PluginCoreUtils.unwrap( d );

		List<Tag> tags = getTagsForTaggable( manager );

		List<Tag> cc_tags = new ArrayList<>();

		for ( Tag tag: tags ){

			if ( tag.getTagType().hasTagTypeFeature( TagFeature.TF_FILE_LOCATION )){

				TagFeatureFileLocation fl = (TagFeatureFileLocation)tag;

				if ( fl.supportsTagCopyOnComplete()){

					File save_loc = fl.getTagCopyOnCompleteFolder();

					if ( save_loc != null ){

						cc_tags.add( tag );
					}
				}
			}
		}

		if ( cc_tags.size() > 0 ){

			if ( cc_tags.size() > 1 ){

				Collections.sort(
						cc_tags,
					new Comparator<Tag>()
					{
						@Override
						public int
						compare(
							Tag o1, Tag o2)
						{
							return( o1.getTagID() - o2.getTagID());
						}
					});
			}

			TagFeatureFileLocation fl = (TagFeatureFileLocation)cc_tags.get(0);

			final File new_loc = fl.getTagCopyOnCompleteFolder();

			long	options = fl.getTagCopyOnCompleteOptions();

			boolean copy_data 		= (options&TagFeatureFileLocation.FL_DATA) != 0;
			boolean copy_torrent 	= (options&TagFeatureFileLocation.FL_TORRENT) != 0;

			if ( copy_data ){

				File old_loc = manager.getSaveLocation();

				if ( !new_loc.equals( old_loc )){

					boolean do_it;

					synchronized( active_copy_on_complete ){

						if ( active_copy_on_complete.contains( manager )){

							do_it = false;

						}else{

							active_copy_on_complete.add( manager );

							do_it = true;
						}
					}

					if ( do_it ){

						new AEThread2( "tm:copy")
						{
							@Override
							public void
							run()
							{
								try{
									long stopped_and_incomplete_start 	= 0;
									long looks_good_start 				= 0;

									while( true ){

										if ( manager.isDestroyed()){

											throw( new Exception( "Download has been removed" ));
										}

										DiskManager dm = manager.getDiskManager();

										if ( dm == null ){

											looks_good_start = 0;

											if ( !manager.getAssumedComplete()){

												long	now = SystemTime.getMonotonousTime();

												if ( stopped_and_incomplete_start == 0 ){

													stopped_and_incomplete_start = now;

												}else if ( now - stopped_and_incomplete_start > 30*1000 ){

													throw( new Exception( "Download is stopped and incomplete" ));
												}
											}else{

												break;
											}
										}else{

											stopped_and_incomplete_start = 0;

											if ( manager.getAssumedComplete()){

												if ( dm.getMoveProgress() == null && dm.getCompleteRecheckStatus() == -1 ){

													long	now = SystemTime.getMonotonousTime();

													if ( looks_good_start == 0 ){

														looks_good_start = now;

													}else if ( now - looks_good_start > 5*1000 ){

														break;
													}
												}
											}else{

												looks_good_start = 0;
											}
										}

										//System.out.println( "Waiting" );

										Thread.sleep( 1000 );
									}

									// manager.copyDataFiles( new_loc );

									
									try{
										FileUtil.runAsTask(
												CoreOperation.OP_DOWNLOAD_COPY,
												new CoreOperationTask()
												{
													private ProgressCallback cb = new CoreOperationTask.ProgressCallbackAdapter();
													
													@Override
													public String 
													getName()
													{
														return( manager.getDisplayName());
													}

													@Override
													public DownloadManager 
													getDownload()
													{
														return( manager );
													}

													@Override
													public String[] 
													getAffectedFileSystems()
													{
														return( FileUtil.getFileStoreNames( manager.getAbsoluteSaveLocation(), new_loc ));
													}

													@Override
													public void
													run(
															CoreOperation operation)
													{
														try{
															manager.copyDataFiles( new_loc, cb );

														}catch( Throwable e ){

															throw( new RuntimeException( e ));
														}
													}

													@Override
													public ProgressCallback 
													getProgressCallback()
													{
														return( cb );
													}
												});

									}catch( Throwable e ){

										Throwable f = e.getCause();

										if ( f instanceof DownloadManagerException ){

											throw((DownloadManagerException)f);
										}

										throw( new DownloadManagerException( "Copy failed", e ));
									}
									
									
									
									
									
									
									
									
									
									
									
									
									
									
									
									
									
									Logger.logTextResource(
										new LogAlert(
											manager,
											LogAlert.REPEATABLE,
											LogAlert.AT_INFORMATION,
											"alert.copy.on.comp.done"),
										new String[]{ manager.getDisplayName(), new_loc.toString()});

								}catch( Throwable e ){

									 Logger.logTextResource(
										new LogAlert(
											manager,
											LogAlert.REPEATABLE,
											LogAlert.AT_ERROR,
											"alert.copy.on.comp.fail"),
										new String[]{ manager.getDisplayName(), new_loc.toString(), Debug.getNestedExceptionMessage(e)});

								}finally{

									synchronized( active_copy_on_complete ){

										active_copy_on_complete.remove( manager );
									}

								}
							}
						}.start();
					}
				}
			}

			if ( copy_torrent ){

				File old_file = FileUtil.newFile( manager.getTorrentFileName());

				if ( old_file.exists()){

					File new_file = FileUtil.newFile( new_loc, old_file.getName());

					FileUtil.copyFile( old_file, new_file );
				}
			}
		}
	}

	protected Object
	evalScript(
		Tag						tag,
		String					script,
		List<DownloadManager>	dms,
		String					intent_key )
	{
		String script_type = "";

		script = script.trim();
		
		if ( script.length() >=10 ){
			
			String start = script.substring(0,10).toLowerCase( Locale.US );
		
			if ( start.startsWith( "javascript" ) || start.startsWith( "plugin" )){
				
				int	p1 = script.indexOf( '(' );
	
				int	p2 = script.lastIndexOf( ')' );
	
				if ( p1 != -1 && p2 != -1 ){
	
					script = script.substring( p1+1, p2 ).trim();
	
					if ( script.startsWith( "\"" ) && script.endsWith( "\"" )){
	
						script = script.substring( 1, script.length()-1 );
					}
	
						// allow people to escape " if it makes them feel better
	
					script = script.replaceAll( "\\\\\"", "\"" );
	
					script_type = start.startsWith( "javascript" )?ScriptProvider.ST_JAVASCRIPT:ScriptProvider.ST_PLUGIN;
				}
			}
		}

		if ( script_type == "" ){

			String error = "Unrecognised script type: " + script;
					
			Debug.out( error  );

			return( new Exception( error ));
		}

		boolean	provider_found = false;

		List<ScriptProvider> providers = CoreFactory.getSingleton().getPluginManager().getDefaultPluginInterface().getUtilities().getScriptProviders();

		for ( ScriptProvider p: providers ){

			if ( p.getScriptType() == script_type ){

				provider_found = true;

				if ( dms.size() > 1 && p.canEvalBatch( script )){
													
					Map<String,Object>	bindings = new HashMap<>();
	
					List<String>	intents 	= new ArrayList<>();
					List<Download>	plugin_dms	= new ArrayList<>();
						
					for ( DownloadManager dm: dms ){

						Download plugin_dm = PluginCoreUtils.wrap( dm );
						
						if ( plugin_dm == null ){
		
							continue;
						}

						String dm_name = dm.getDisplayName();
		
						if ( dm_name.length() > 32 ){
		
							dm_name = dm_name.substring( 0, 29 ) + "...";
						}
		
						String intent = intent_key + "(\"" + tag.getTagName() + "\",\"" + dm_name + "\")";
						
						intents.add( intent );
						
						plugin_dms.add( plugin_dm );
					}
					
					if ( intents.isEmpty()){
						
						return( null );
					}
					
					bindings.put( "intents", intents );
	
					bindings.put( "downloads", plugin_dms );
	
					bindings.put( "tag", tag );
	
					try{
						Object result = p.eval( script, bindings );
	
						return( result );
	
					}catch( Throwable e ){
	
						Debug.out( e );
						
						return( e );
					}
					
				}else{
					
					List<Object>	results = new ArrayList<>();
					
					for ( DownloadManager dm: dms ){
						
						Download plugin_dm = PluginCoreUtils.wrap( dm );
		
						if ( plugin_dm == null ){
		
							continue; // deleted in the meantime
						}
		
						Map<String,Object>	bindings = new HashMap<>();
		
		
						String dm_name = dm.getDisplayName();
		
						if ( dm_name.length() > 32 ){
		
							dm_name = dm_name.substring( 0, 29 ) + "...";
						}
		
						String intent = intent_key + "(\"" + tag.getTagName() + "\",\"" + dm_name + "\")";
		
						bindings.put( "intent", intent );
		
						bindings.put( "download", plugin_dm );
		
						bindings.put( "tag", tag );
		
						try{
							Object result = p.eval( script, bindings );
		
							results.add( result );
		
						}catch( Throwable e ){
		
							Debug.out( e );
							
							results.add( e );
						}
					}
					
					if ( results.size() == 1 && dms.size() == 1 ){
						
						return( results.get( 0 ));
						
					}else{
						
						return( results );
					}
				}
			}
		}

		if ( script_type == ScriptProvider.ST_JAVASCRIPT && !provider_found ){

			if ( !js_plugin_install_tried ){

				js_plugin_install_tried = true;

				PluginUtils.installJavaScriptPlugin();
			}
		}

		return( null );
	}

	private void
	loadTags(
		TagTypeWithState		tt_with_state )
	{
		List<Tag> tags = new ArrayList<>();

		synchronized( this ){

			Map config = getConfig();

			Map<String,Object> tt = (Map<String,Object>)config.get( String.valueOf( tt_with_state.getTagType()));

			if ( tt != null ){

				for ( Map.Entry<String,Object> entry: tt.entrySet()){

					String key = entry.getKey();

					try{
						if ( Character.isDigit( key.charAt(0))){

							int	tag_id 	= Integer.parseInt( key );
							Map m		= (Map)entry.getValue();

							tags.add( tt_with_state.createTag( tag_id, m ));
						}
					}catch( Throwable e ){

						Debug.out( e );
					}
				}
			}
		}

		for ( Tag tag: tags ){

			tt_with_state.addTag( tag );
		}
	}
	
	private void
	initializeSwarmTags()
	{
		TagTypeSwarmTag stt = new TagTypeSwarmTag();

		loadTags( stt );
	}
	
	private void
	resolverInitialized(
		TaggableResolver		resolver )
	{
		TagTypeDownloadManual ttdm = new TagTypeDownloadManual( resolver );

		loadTags( ttdm );
		
		TagTypeDownloadInternal ttdi = new TagTypeDownloadInternal( resolver );
	}

	private void
	removeTaggable(
		TaggableResolver	resolver,
		Taggable			taggable )
	{
		for ( TagType	tt: tag_types ){

			TagTypeBase	ttb = (TagTypeBase)tt;

			ttb.removeTaggable( resolver, taggable );
		}
	}

	public void
	addTagType(
		TagTypeBase		tag_type )
	{
		if ( !enabled ){

			Debug.out( "Not enabled" );

			return;
		}

		synchronized( tag_type_map ){

			if ( tag_type_map.put( tag_type.getTagType(), tag_type) != null ){

				Debug.out( "Duplicate tag type!" );
			}
		}

		tag_types.add( tag_type );

		for ( TagManagerListener l : listeners ){

			try{
				l.tagTypeAdded(this, tag_type);

			}catch ( Throwable t ){

				Debug.out(t);
			}
		}
	}

	@Override
	public TagType
	getTagType(
		int 	tag_type)
	{
		synchronized( tag_type_map ){

			return( tag_type_map.get( tag_type ));
		}
	}

	protected void
	removeTagType(
		TagTypeBase		tag_type )
	{
		synchronized( tag_type_map ){

			tag_type_map.remove( tag_type.getTagType());
		}

		tag_types.remove( tag_type );

		for ( TagManagerListener l : listeners ){

			try{
				l.tagTypeRemoved(this, tag_type);

			}catch( Throwable t ){

				Debug.out(t);
			}
		}

		removeConfig( tag_type );
	}

	@Override
	public List<TagType>
	getTagTypes()
	{
		return((List<TagType>)(Object)tag_types.getList());
	}

	public void
	taggableAdded(
		TagType		tag_type,
		Tag			tag,
		Taggable	tagged )
	{
		int tt = tag_type.getTagType();

		if ( tt == TagType.TT_DOWNLOAD_MANUAL && tagged instanceof DownloadManager ){

			TagFeatureFileLocation fl = (TagFeatureFileLocation)tag;
			
				// hack to support initial-save-location logic when a user manually assigns a tag and the download
				// hasn't had files allocated yet (most common scenario is user has 'add-torrent-stopped' set up)

			if ( fl.supportsTagInitialSaveFolder()){

				try{
					File save_loc = fl.getTagInitialSaveFolder();

					if ( save_loc != null ){

						DownloadManager dm = (DownloadManager)tagged;

						if ( dm.getState() == DownloadManager.STATE_STOPPED ){

							TOTorrent torrent = dm.getTorrent();

							if ( torrent != null ){

									// This test detects whether or not we are in the process of adding the download
									// If we are then initial save-location stuff will be applied by the init-adapter
									// code above - we're only dealing later assignments here

								if ( dm.getGlobalManager().getDownloadManager( torrent.getHashWrapper()) != null ){

									long	options = fl.getTagInitialSaveOptions();

									boolean set_data 	= (options&TagFeatureFileLocation.FL_DATA) != 0;
									boolean set_torrent = (options&TagFeatureFileLocation.FL_TORRENT) != 0;

									if ( set_data ){

										File existing_save_loc = dm.getSaveLocation();

										if ( ! ( existing_save_loc.equals( save_loc ) || existing_save_loc.exists())){

											dm.setTorrentSaveDir(FileUtil.newFile(
												save_loc.getAbsolutePath()), false);
										}
									}

									if ( set_torrent ){

										File old_torrent_file = FileUtil.newFile( dm.getTorrentFileName());

										if ( old_torrent_file.exists()){

											try{
												dm.setTorrentFile( save_loc, old_torrent_file.getName());

											}catch( Throwable e ){

												Debug.out( e );
											}
										}
									}
								}
							}
						}
					}

				}catch( Throwable e ){

					Debug.out(e );
				}
			}
				
			if ( fl.supportsTagMoveOnAssign()){
	
				try{
					File ass_loc = fl.getTagMoveOnAssignFolder();
		
					if ( ass_loc != null ){
		
						DownloadManager dm = (DownloadManager)tagged;
		
						TOTorrent torrent = dm.getTorrent();
	
						if ( torrent != null ){
	
								// Only consider applying move-on-assign after a download has been added. If the user
								// also wants it to be applied initially then they need to specify the initial-save-location
								// to be the same. Much simpler to do this than deal with the possible interaction between the 
								// two options
							
							if ( dm.getGlobalManager().getDownloadManager( torrent.getHashWrapper()) != null ){
																
								move_on_assign_dispatcher.dispatch(
									AERunnable.create(()->{
										
										moveOnAssign( dm, ass_loc, fl.getTagMoveOnAssignOptions());
									}));
									
							}
						}
					}	
				}catch( Throwable e ){
		
					Debug.out(e );
				}
			}
		}
		

			// hack to limit tagged/untagged callbacks as the auto-dl-state ones generate a lot
			// of traffic and thusfar nobody's interested in it

		if ( tt == TagType.TT_DOWNLOAD_MANUAL ){

			synchronized( lifecycle_handlers ){

				long type = tagged.getTaggableType();

				LifecycleHandlerImpl handler = lifecycle_handlers.get( type );

				if ( handler == null ){

					handler = new LifecycleHandlerImpl();

					lifecycle_handlers.put( type, handler );
				}

				handler.taggableTagged( tag_type, tag, tagged );
			}
		}
	}

	private void
	moveOnAssign(
		DownloadManager		dm,
		File				location,
		long				options )
	{
		try{
			boolean set_data 	= (options&TagFeatureFileLocation.FL_DATA) != 0;
			boolean set_torrent = (options&TagFeatureFileLocation.FL_TORRENT) != 0;
	
			if ( set_data ){
	
				File existing_save_loc = dm.getSaveLocation();
	
				if ( existing_save_loc.isFile()){
					
					existing_save_loc = existing_save_loc.getParentFile();
				}
				
				if ( ! existing_save_loc.equals( location )){
	
					dm.moveDataFilesLive( location );
				}
			}
	
			if ( set_torrent ){
	
				File old_torrent_file = FileUtil.newFile( dm.getTorrentFileName());
	
				if ( old_torrent_file.exists()){
	
					try{
						dm.setTorrentFile( location, old_torrent_file.getName());
	
					}catch( Throwable e ){
	
						Debug.out( e );
					}
				}
			}
		}catch( Throwable e ){
			
			Debug.out( e );
		}
	}
		
	public void
	taggableRemoved(
		TagType		tag_type,
		Tag			tag,
		Taggable	tagged )
	{
		int tt = tag_type.getTagType();

			// as above

		if ( tt == TagType.TT_DOWNLOAD_MANUAL ){

			synchronized( lifecycle_handlers ){

				long type = tagged.getTaggableType();

				LifecycleHandlerImpl handler = lifecycle_handlers.get( type );

				if ( handler == null ){

					handler = new LifecycleHandlerImpl();

					lifecycle_handlers.put( type, handler );
				}

				handler.taggableUntagged( tag_type, tag, tagged );
			}
		}
	}

	@Override
	public List<Tag>
	getTagsForTaggable(
		Taggable	taggable )
	{
		Set<Tag>	result = new HashSet<>();

		for ( TagType tt: tag_types ){

			result.addAll( tt.getTagsForTaggable( taggable ));
		}

		return(new ArrayList<>(result));
	}

	@Override
	public List<Tag>
	getTagsForTaggable(
		int			tag_type,
		Taggable	taggable )
	{
		Set<Tag>	result = new HashSet<>();

		for ( TagType tt: tag_types ){

			if ( tt.getTagType() == tag_type ){

				result.addAll( tt.getTagsForTaggable( taggable ));
			}
		}

		return(new ArrayList<>(result));
	}

	@Override
	public List<Tag>
	getTagsForTaggable(
		int[]		tts,
		Taggable	taggable )
	{
		Set<Tag>	result = new HashSet<>();

		Set<Integer>	tt_set = new HashSet<>();
		
		for ( int tt: tts ){
			
			tt_set.add( tt );
		}
		
		for ( TagType tt: tag_types ){

			if ( tt_set.contains( tt.getTagType())){

				result.addAll( tt.getTagsForTaggable( taggable ));
			}
		}

		return(new ArrayList<>(result));
	}
	
	@Override
	public List<Tag> 
	getTagsByName(
		String name, 
		boolean is_localized)
	{
		List<Tag>	result = new ArrayList<Tag>();

		for ( TagType tt: tag_types ){

			Tag t = tt.getTag( name, is_localized );
			
			if ( t != null ){
				
				result.add( t );
			}
		}

		return( result );
	}
	
	@Override
	public Tag
	lookupTagByUID(
		long	tag_uid )
	{
		int	tag_type_id = (int)((tag_uid>>32)&0xffffffffL);

		TagType tt;

		synchronized( tag_type_map ){

			tt = tag_type_map.get( tag_type_id );
		}

		if ( tt != null ){

			int	tag_id = (int)(tag_uid&0xffffffffL);

			return( tt.getTag( tag_id ));
		}

		return( null );
	}
	
	@Override
	public List<Tag> 
	lookupTagsByName(
		String tag_name)
	{
		List<Tag> result = new ArrayList<>();
		
		for ( TagType tt: tag_types ){
			
			Tag tag = tt.getTag( tag_name, true );
			
			if ( tag != null ){
				
				result.add( tag );
			}
		}
		
		return( result );
	}

	@Override
	public Object 
	importDataSource(
		Map map )
	{
		long uid = (Long)map.get( "uid");
		
		return( lookupTagByUID( uid ));
	}
	
	@Override
	public TaggableLifecycleHandler
	registerTaggableResolver(
		TaggableResolver	resolver )
	{
		if ( !enabled ){

			return(
				new TaggableLifecycleHandler()
				{
					@Override
					public void
					initialized(
						List<Taggable>	initial_taggables )
					{
					}

					@Override
					public void
					taggableCreated(
						Taggable	taggable )
					{
					}

					@Override
					public void
					taggableDestroyed(
						Taggable	taggable )
					{
					}
				});
		}

		LifecycleHandlerImpl handler;

		long type = resolver.getResolverTaggableType();

		synchronized( lifecycle_handlers ){

			handler = lifecycle_handlers.get( type );

			if ( handler == null ){

				handler = new LifecycleHandlerImpl();

				lifecycle_handlers.put( type, handler );
			}

			handler.setResolver( resolver );
		}

		return( handler );
	}

	@Override
	public void
	setTagPublicDefault(
		boolean	pub )
	{
		COConfigurationManager.setParameter( "tag.manager.pub.default", pub );
	}

	@Override
	public boolean
	getTagPublicDefault()
	{
		return( COConfigurationManager.getBooleanParameter( "tag.manager.pub.default", true ));
	}

	protected void
	tagGroupCreated(
		TagTypeBase		tag_type,
		TagGroupImpl	group )
	{
		Map<String,Object> conf = getConf( tag_type, false );
		
		if ( conf != null ){
			
			Map<String,Object>	tg_conf = (Map<String,Object>)conf.get( group.getGroupID());
			
			if ( tg_conf != null ){
				
				group.importState( tg_conf );
			}
		}
		
		PluginInterface pi = CoreFactory.getSingleton().getPluginManager().getDefaultPluginInterface();
		
		UIManager ui_manager = pi.getUIManager();
		
		TableManager tm = ui_manager.getTableManager();
				
		Properties props = new Properties();
		
		String col_id_text 	= "tag.group.col." + group.getGroupID();
		String col_id_icons = "tag.group.col.icons." + group.getGroupID();

		props.put( "TableColumn.header." + col_id_text, group.getName());
		props.put( "TableColumn.header." + col_id_text + ".info", MessageText.getString( "label.tag.names" ));
		props.put( "TableColumn.header." + col_id_icons, group.getName());
		props.put( "TableColumn.header." + col_id_icons + ".info", MessageText.getString( "TableColumn.header.tag_icons" ));
		
		pi.getUtilities().getLocaleUtilities().integrateLocalisedMessageBundle( props );
		
		int[]	interesting_tts = { TagType.TT_DOWNLOAD_MANUAL, TagType.TT_DOWNLOAD_CATEGORY };
		
		tm.registerColumn(
			Download.class,
			col_id_text,
			new TableColumnCreationListener(){
				
				@Override
				public void tableColumnCreated(TableColumn column){
					column.setAlignment(TableColumn.ALIGN_CENTER);
					column.setPosition(TableColumn.POSITION_INVISIBLE);
					column.setWidth(70);
					column.setRefreshInterval(TableColumn.INTERVAL_LIVE);

					column.setIconReference("image.tag.column", false );
					
					column.addCellRefreshListener(
						(cell)->{
							Download	dl = (Download)cell.getDataSource();

							if ( dl == null ){

								return;
							}
							
							List<Tag> tags = TagManagerImpl.this.getTagsForTaggable( interesting_tts, PluginCoreUtils.unwrap( dl ));

							String sTags = null;
							
							if ( tags.size() > 0 ){

								tags = TagUtils.sortTags( tags );

								for ( Tag t: tags ){

									if ( t.getGroupContainer() == group ){
										
										String str = t.getTagName( true );
	
										if ( sTags == null ){
											
											sTags = str;
											
										}else{
											
											sTags += ", " + str;
										}
									}
								}
							}

							cell.setText((sTags == null) ? "" : sTags );
						});
				}
			});
		
		tm.registerColumn(
				Download.class,
				col_id_icons,
				new TableColumnCreationListener(){
					
					@Override
					public void tableColumnCreated(TableColumn column){
						try{
							Class cla = Class.forName( "com.biglybt.ui.swt.columns.tag.ColumnTagGroupIcons");
							
							cla.getConstructor( TableColumn.class, TagGroup.class ).newInstance( column, group );
							
						}catch( Throwable e ){
							
							Debug.out( e );;
						}
					}
				});
	}
	
	protected void
	tagGroupUpdated(
		TagTypeBase		tag_type,
		TagGroupImpl	group )
	{
		Map<String,Object> conf = getConf( tag_type, true );
		
		String id = group.getGroupID();
		
		Map<String,Object> state = group.exportState();
		
		if ( state.isEmpty()){
			
			conf.remove( id );
			
		}else{
			
			conf.put( id,  state );
		}
		
		setDirty();
	}
	
	
	protected void
	checkRSSFeeds(
		TagBase		tag,
		boolean		enable )
	{
		synchronized( rss_tags ){

			if ( enable ){

				if ( rss_tags.contains( tag )){

					return;
				}

				rss_tags.add( tag );

				if ( rss_tags.size() > 1 ){

					return;

				}else{

					RSSGeneratorPlugin.registerProvider( RSS_PROVIDER, rss_generator  );
				}
			}else{

				rss_tags.remove( tag );

				if ( rss_tags.size() == 0 ){

					RSSGeneratorPlugin.unregisterProvider( RSS_PROVIDER  );
				}
			}
		}
	}

	protected String
	getTagStatus(
		Tag	tag )
	{
		if ( constraint_handler != null ){
		
			return( constraint_handler.getTagStatus( tag ));
		}
		
		return( null );
	}
	
	protected Set<Tag>
	getDependsOnTags(
		Tag	tag )
	{
		if ( constraint_handler != null ){
			
			return( constraint_handler.getDependsOnTags( tag ));
		}
		
		return( Collections.emptySet());
	}
	
	@Override
	public void
	addTagManagerListener(
		TagManagerListener		listener,
		boolean					fire_for_existing )
	{
		listeners.add( listener );

		if ( fire_for_existing ){

			for (TagType tt: tag_types ){

				listener.tagTypeAdded( this, tt );
			}
		}
	}

	@Override
	public void
	removeTagManagerListener(
		TagManagerListener		listener )
	{
		listeners.remove( listener );
	}

	@Override
	public void
	addTagFeatureListener(
		int						features,
		TagFeatureListener		listener )
	{
		feature_listeners.add( new Object[]{ features, listener });
	}

	@Override
	public void
	removeTagFeatureListener(
		TagFeatureListener		listener )
	{
		for ( Object[] entry: feature_listeners ){

			if ( entry[1] == listener ){

				feature_listeners.remove( entry );
			}
		}
	}

	protected void
	featureChanged(
		Tag			tag,
		int			feature )
	{
		for ( Object[] entry: feature_listeners ){

			if ((((Integer)entry[0]) & feature ) != 0 ){

				try{
					((TagFeatureListener)entry[1]).tagFeatureChanged( tag, feature );

				}catch( Throwable e ){

					Debug.out( e );
				}
			}
		}
	}

	public TaggableResolver
	getResolver(
		long		taggable_type )
	{
		synchronized( lifecycle_handlers ){

			LifecycleHandlerImpl handler = lifecycle_handlers.get( taggable_type );
			
			if ( handler != null ){
				
				return( handler.resolver );
			}
		}
		
		return( null );
	}
	
	@Override
	public void
	addTaggableLifecycleListener(
		long						taggable_type,
		TaggableLifecycleListener	listener )
	{
		synchronized( lifecycle_handlers ){

			LifecycleHandlerImpl handler = lifecycle_handlers.get( taggable_type );

			if ( handler == null ){

				handler = new LifecycleHandlerImpl();

				lifecycle_handlers.put( taggable_type, handler );
			}

			handler.addListener( listener );
		}
	}

	@Override
	public void
	removeTaggableLifecycleListener(
		long						taggable_type,
		TaggableLifecycleListener	listener )
	{
		synchronized( lifecycle_handlers ){

			LifecycleHandlerImpl handler = lifecycle_handlers.get( taggable_type );

			if ( handler != null ){

				handler.removeListener( listener );
			}
		}
	}

	protected void
	tagCreated(
		TagWithState	tag )
	{
		addConfigUpdate( CU_TAG_CREATE, tag );
	}

	protected void
	tagChanged(
		TagWithState	tag )
	{
		addConfigUpdate( CU_TAG_CHANGE, tag );

	}

	protected void
	tagRemoved(
		TagWithState	tag )
	{
		addConfigUpdate( CU_TAG_REMOVE, tag );
	}

	protected void
	tagContentsChanged(
		TagWithState	tag )
	{
		addConfigUpdate( CU_TAG_CONTENTS, tag );
	}

	private void
	addConfigUpdate(
		int				type,
		TagWithState	tag )
	{
		if ( !tag.getTagType().isTagTypePersistent()){

			return;
		}

		if ( tag.isRemoved() && type != CU_TAG_REMOVE ){

			return;
		}

		synchronized( this ){

			config_change_queue.add( new Object[]{ type, tag });
		}

		setDirty();
	}

	private void
	applyConfigUpdates(
		Map			config )
	{
		Map<TagWithState,Integer>	updates = new HashMap<>();

		for ( Object[] update: config_change_queue ){

			int				type	= (Integer)update[0];
			TagWithState	tag 	= (TagWithState)update[1];

			if ( tag.isRemoved()){

				type = CU_TAG_REMOVE;
			}

			Integer existing = updates.get( tag );

			if ( existing == null ){

				updates.put( tag, type );

			}else{

				if ( existing == CU_TAG_REMOVE ){

				}else if ( type > existing ){

					updates.put( tag, type );
				}
			}
		}

		for ( Map.Entry<TagWithState,Integer> entry: updates.entrySet()){

			TagWithState 	tag = entry.getKey();
			int				type	= entry.getValue();

			TagType	tag_type = tag.getTagType();

			String tt_key = String.valueOf( tag_type.getTagType());

			Map tt = (Map)config.get( tt_key );

			if ( tt == null ){

				if ( type == CU_TAG_REMOVE ){

					continue;
				}

				tt = new HashMap();

				config.put( tt_key, tt );
			}

			String t_key = String.valueOf( tag.getTagID());

			if ( type == CU_TAG_REMOVE ){

				tt.remove( t_key );

				continue;
			}

			Map t = (Map)tt.get( t_key );

			if ( t == null ){

				t = new HashMap();

				tt.put( t_key, t );
			}

			tag.exportDetails( t, type == CU_TAG_CONTENTS );
		}

		config_change_queue.clear();
	}

	private void
	destroy()
	{
		for ( TagType tt: tag_types ){

			((TagTypeBase)tt).closing();
		}

		writeConfig();
	}

	private void
	setDirty()
	{
		synchronized( this ){

			if ( !config_dirty ){

				config_dirty = true;

				dirty_dispatcher.dispatch();
			}
		}
	}

	private Map
	readConfig()
	{
		if ( !enabled ){

			Debug.out( "TagManager is disabled" );

			return( new HashMap());
		}

		Map map;

		if ( FileUtil.resilientConfigFileExists( CONFIG_FILE )){

			map = FileUtil.readResilientConfigFile( CONFIG_FILE );

		}else{

			map = new HashMap();
		}

		return( map );
	}

	private Map<String,Object>
	getConfig()
	{
		synchronized( this ){

			if ( config != null ){

				return( config );
			}

			if ( config_ref != null ){

				config = config_ref.get();

				if ( config != null ){

					return( config );
				}
			}

			config = readConfig();

			return( config );
		}
	}

	private void
	writeConfig()
	{
		if ( !enabled ){

			Debug.out( "TagManager is disabled" );
		}

		synchronized( this ){

			if ( !config_dirty ){

				return;
			}

			config_dirty = false;

			if ( config_change_queue.size() > 0 ){

				applyConfigUpdates( getConfig());
			}

			if ( config != null ){

				FileUtil.writeResilientConfigFile( CONFIG_FILE, config );

				config_ref = new WeakReference<>(config);

				config = null;
			}
		}
	}

	private Map<String,Object>
	getConf(
		TagTypeBase	tag_type,
		boolean		create )
	{
		Map<String,Object> m = getConfig();

		String tt_key = String.valueOf( tag_type.getTagType());

		Map<String,Object> tt = (Map<String,Object>)m.get( tt_key );

		if ( tt == null ){

			if ( create ){

				tt = new HashMap<>();

				m.put( tt_key, tt );

			}else{

				return( null );
			}
		}

		Map<String,Object> conf = (Map)tt.get( "c" );

		if ( conf == null && create ){

			conf = new HashMap<>();

			tt.put( "c", conf );
		}

		return( conf );
	}
	
	private Map<String,Object>
	getConf(
		TagTypeBase	tag_type,
		TagBase		tag,
		boolean		create )
	{
		Map m = getConfig();

		String tt_key = String.valueOf( tag_type.getTagType());

		Map tt = (Map)m.get( tt_key );

		if ( tt == null ){

			if ( create ){

				tt = new HashMap();

				m.put( tt_key, tt );

			}else{

				return( null );
			}
		}

		String t_key = String.valueOf( tag.getTagID());

		Map t = (Map)tt.get( t_key );

		if ( t == null ){

			if ( create ){

				t = new HashMap();

				tt.put( t_key, t );

			}else{

				return( null );
			}
		}

		Map conf = (Map)t.get( "c" );

		if ( conf == null && create ){

			conf = new HashMap();

			t.put( "c", conf );
		}

		return( conf );
	}

	protected void
	setConf(
		int			tag_type,
		int			tag_id,
		Map			conf )
	{
		Map m = getConfig();

		String tt_key = String.valueOf( tag_type );

		Map tt = (Map)m.get( tt_key );

		if ( tt == null ){

			tt = new HashMap();

			m.put( tt_key, tt );
		}

		String t_key = String.valueOf( tag_id );

		Map t = new HashMap();

		tt.put( t_key, t );

		t.put( "c", conf );
		
		setDirty();
	}
	
	protected Boolean
	readBooleanAttribute(
		TagTypeBase	tag_type,
		TagBase		tag,
		String		attr,
		Boolean		def )
	{
		Long result = readLongAttribute(tag_type, tag, attr, def==null?null:(def?1L:0L));

		if ( result == null ){

			return( null );
		}

		return( result == 1 );
	}

	protected boolean
	writeBooleanAttribute(
		TagTypeBase		tag_type,
		TagBase			tag,
		String			attr,
		Boolean			value )
	{
		return( writeLongAttribute( tag_type, tag, attr, value==null?null:(value?1L:0L )));
	}

	protected Long
	readLongAttribute(
		TagTypeBase	tag_type,
		TagBase		tag,
		String		attr,
		Long		def )
	{
		try{
			synchronized( this ){

				Map conf = getConf( tag_type, tag, false );

				if ( conf == null ){

					return( def );
				}

				Long value = (Long)conf.get( attr );

				if ( value == null ){

					return( def );
				}

				return( value );
			}
		}catch( Throwable e ){

			Debug.out( e );

			return( def );
		}
	}

	protected boolean
	writeLongAttribute(
		TagTypeBase		tag_type,
		TagBase			tag,
		String			attr,
		Long			value )
	{
		try{
			synchronized( this ){

				Map conf = getConf( tag_type, tag, true );

				if ( value == null ){

					if ( conf.containsKey( attr )){

						conf.remove( attr );

						setDirty();

						return( true );

					}else{

						return( false );
					}
				}else{

					long old = MapUtils.getMapLong( conf, attr, 0 );

					if ( old == value && conf.containsKey( attr )){

						return( false );
					}

					conf.put( attr, value );

					setDirty();

					return( true );
				}
			}
		}catch( Throwable e ){

			Debug.out( e );

			return( false );
		}
	}

	protected String
	readStringAttribute(
		TagTypeBase	tag_type,
		TagBase		tag,
		String		attr,
		String		def )
	{
		try{
			synchronized( this ){

				Map conf = getConf( tag_type, tag, false );

				if ( conf == null ){

					return( def );
				}

				return( MapUtils.getMapString( conf, attr, def ));
			}
		}catch( Throwable e ){

			Debug.out( e );

			return( def );
		}
	}

	/**
	 * @return Whether attribute was changed from existing value
	 */
	protected boolean
	writeStringAttribute(
		TagTypeBase		tag_type,
		TagBase			tag,
		String			attr,
		String			value )
	{
		try{
			synchronized( this ){

				Map conf = getConf( tag_type, tag, true );

				String old = MapUtils.getMapString( conf, attr, null );

				if ( old == value ){

					return false;

				}else if ( old != null && value != null && old.equals( value )){

					return false;
				}

				MapUtils.setMapString( conf, attr, value );

				setDirty();
			}
		}catch( Throwable e ){

			Debug.out( e );
		}
		return true;
	}

	protected Map<String,Object>
	readMapAttribute(
		TagTypeBase			tag_type,
		TagBase				tag,
		String				attr,
		Map<String,Object>	def )
	{
		try{
			synchronized( this ){

				Map conf = getConf( tag_type, tag, false );

				if ( conf == null ){

					return( def );
				}

				Map m = (Map)conf.get( attr );
				
				if ( m == null ){
					
					return( def );
				}
				
				return( BEncoder.cloneMap( m ));
			}
		}catch( Throwable e ){

			Debug.out( e );

			return( def );
		}
	}

	protected void
	writeMapAttribute(
		TagTypeBase			tag_type,
		TagBase				tag,
		String				attr,
		Map<String,Object>	value )
	{
		try{
			synchronized( this ){

				Map conf = getConf( tag_type, tag, true );

				Map old = (Map)conf.get( attr );

				if ( old == value ){

					return;

				}else if ( old != null && value != null && BEncoder.mapsAreIdentical( old, value )){

					return;
				}

				conf.put( attr, value );

				setDirty();
			}
		}catch( Throwable e ){

			Debug.out( e );
		}
	}
	
	protected String[]
	readStringListAttribute(
		TagTypeBase		tag_type,
		TagBase			tag,
		String			attr,
		String[]		def )
	{
		try{
			synchronized( this ){

				Map conf = getConf( tag_type, tag, false );

				if ( conf == null ){

					return( def );
				}

				List<String> vals = BDecoder.decodeStrings((List)conf.get( attr ));

				if ( vals == null ){

					return( def );
				}

				return( vals.toArray( new String[ vals.size()]));
			}
		}catch( Throwable e ){

			Debug.out( e );

			return( def );
		}
	}

	protected boolean
	writeStringListAttribute(
		TagTypeBase		tag_type,
		TagBase			tag,
		String			attr,
		String[]		value )
	{
		try{
			synchronized( this ){

				Map conf = getConf( tag_type, tag, true );

				List<String> old = BDecoder.decodeStrings((List)conf.get( attr ));

				if ( old == null && value == null ){

					return( false );

				}else if ( old != null && value != null ){

					if ( value.length == old.size()){

						boolean diff = false;

						for ( int i=0;i<value.length;i++){

							String old_value = old.get(i);

							if ( old_value == null || !old.get(i).equals(value[i])){

								diff = true;

								break;
							}
						}

						if ( !diff ){

							return( false );
						}
					}
				}

				if ( value == null ){

					conf.remove( attr );
				}else{

					conf.put( attr, Arrays.asList( value ));
				}

				setDirty();

				return( true );
			}
		}catch( Throwable e ){

			Debug.out( e );

			return( false );
		}
	}

	protected long[]
	readLongListAttribute(
		TagTypeBase		tag_type,
		TagBase			tag,
		String			attr,
		long[]			def )
	{
		try{
			synchronized( this ){

				Map conf = getConf( tag_type, tag, false );

				if ( conf == null ){

					return( def );
				}

				List<Long> vals =(List)conf.get( attr );

				if ( vals == null ){

					return( def );
				}

				long[] result = new long[vals.size()];
				
				for ( int i=0;i<result.length;i++){
					result[i] = vals.get(i);
				}
				
				return( result );
			}
		}catch( Throwable e ){

			Debug.out( e );

			return( def );
		}
	}

	protected boolean
	writeLongListAttribute(
		TagTypeBase		tag_type,
		TagBase			tag,
		String			attr,
		long[]			value )
	{
		try{
			synchronized( this ){

				Map conf = getConf( tag_type, tag, true );

				List<Long> old = (List)conf.get( attr );

				if ( old == null && value == null ){

					return( false );

				}else if ( old != null && value != null ){

					if ( value.length == old.size()){

						boolean diff = false;

						for ( int i=0;i<value.length;i++){

							long old_value = old.get(i);

							if ( old_value  != value[i]){

								diff = true;

								break;
							}
						}

						if ( !diff ){

							return( false );
						}
					}
				}

				if ( value == null ){

					conf.remove( attr );
					
				}else{

					List<Long> l = new ArrayList<>( value.length );
					
					for ( long v: value ){
						
						l.add( v );
					}
					
					conf.put( attr, l);
				}

				setDirty();

				return( true );
			}
		}catch( Throwable e ){

			Debug.out( e );

			return( false );
		}
	}
	
	protected void
	removeConfig(
		TagType	tag_type )
	{
		synchronized( this ){

			Map m = getConfig();

			String tt_key = String.valueOf( tag_type.getTagType());

			Map tt = (Map)m.remove( tt_key );

			if ( tt != null ){

				setDirty();
			}
		}
	}

	protected void
	removeConfig(
		Tag	tag )
	{
		TagType	tag_type = tag.getTagType();

		synchronized( this ){

			Map m = getConfig();

			String tt_key = String.valueOf( tag_type.getTagType());

			Map tt = (Map)m.get( tt_key );

			if ( tt == null ){

				return;
			}

			String t_key = String.valueOf( tag.getTagID());

			Map t = (Map)tt.remove( t_key );

			if ( t != null ){

				setDirty();
			}
		}
	}

	private class
	LifecycleHandlerImpl
		implements TaggableLifecycleHandler
	{
		private TaggableResolver		resolver;
		private boolean					initialised;

		private final CopyOnWriteList<TaggableLifecycleListener>	listeners = new CopyOnWriteList<>();

		private
		LifecycleHandlerImpl()
		{
		}

		private void
		setResolver(
			TaggableResolver	_resolver )
		{
			resolver = _resolver;
		}

		private void
		addListener(
			final TaggableLifecycleListener	listener )
		{
			synchronized( this ){

				listeners.add( listener );

				if ( initialised ){

					final List<Taggable> taggables = resolver.getResolvedTaggables();

					if ( taggables.size() > 0 ){

						async_dispatcher.dispatch(
							new AERunnable()
							{
								@Override
								public void
								runSupport()
								{
									listener.initialised( taggables );
								}
							});
					}
				}
			}
		}

		private void
		removeListener(
			TaggableLifecycleListener	listener )
		{
			synchronized( this ){

				listeners.remove( listener );
			}
		}

		@Override
		public void
		initialized(
			final	List<Taggable>	initial_taggables )
		{
			resolverInitialized( resolver );

			synchronized( this ){

				initialised = true;

				if ( listeners.size() > 0 ){

					final List<TaggableLifecycleListener> listeners_ref = listeners.getList();

					async_dispatcher.dispatch(
						new AERunnable()
						{
							@Override
							public void
							runSupport()
							{
								for ( TaggableLifecycleListener listener: listeners_ref ){

									listener.initialised( initial_taggables );
								}
							}
						});
				}
			}
		}

		@Override
		public void
		taggableCreated(
			final Taggable	t )
		{
			synchronized( this ){

				if ( initialised ){

					final List<TaggableLifecycleListener> listeners_ref = listeners.getList();

					async_dispatcher.dispatch(
						new AERunnable()
						{
							@Override
							public void
							runSupport()
							{
								for ( TaggableLifecycleListener listener: listeners_ref ){

									try{
										listener.taggableCreated( t );

									}catch( Throwable e ){

										Debug.out( e );
									}
								}
							}
						});
				}
			}
		}

		@Override
		public void
		taggableDestroyed(
			final Taggable	t )
		{
			removeTaggable( resolver, t );

			synchronized( this ){

				if ( initialised ){

					final List<TaggableLifecycleListener> listeners_ref = listeners.getList();

					async_dispatcher.dispatch(
						new AERunnable()
						{
							@Override
							public void
							runSupport()
							{
								for ( TaggableLifecycleListener listener: listeners_ref ){

									try{
										listener.taggableDestroyed( t );

									}catch( Throwable e ){

										Debug.out( e );
									}
								}
							}
						});
				}
			}
		}

		public void
		taggableTagged(
			final TagType	tag_type,
			final Tag		tag,
			final Taggable	taggable )
		{
			synchronized( this ){

				if ( initialised ){

					final List<TaggableLifecycleListener> listeners_ref = listeners.getList();

					async_dispatcher.dispatch(
						new AERunnable()
						{
							@Override
							public void
							runSupport()
							{
								for ( TaggableLifecycleListener listener: listeners_ref ){

									try{
										listener.taggableTagged( tag_type, tag, taggable);

									}catch( Throwable e ){

										Debug.out( e );
									}
								}
							}
						});
				}
			}
		}

		public void
		taggableUntagged(
			final TagType	tag_type,
			final Tag		tag,
			final Taggable	taggable )
		{
			synchronized( this ){

				if ( initialised ){

					final List<TaggableLifecycleListener> listeners_ref = listeners.getList();

					async_dispatcher.dispatch(
						new AERunnable()
						{
							@Override
							public void
							runSupport()
							{
								for ( TaggableLifecycleListener listener: listeners_ref ){

									try{
										listener.taggableUntagged( tag_type, tag, taggable );

									}catch( Throwable e ){

										Debug.out( e );
									}
								}
							}
						});
				}
			}
		}
	}

	private Tag
	importVuzeFile(
		Map		content )
	{
		TagTypeDownloadManual tt = (TagTypeDownloadManual)getTagType( TagType.TT_DOWNLOAD_MANUAL );
		
		TagDownloadWithState tag = tt.importTag((Map)content.get( "tag" ), (Map)content.get( "config" ));
				
		tt.addTag( tag );
		
		return( tag );
	}
	
	public VuzeFile
	getVuzeFile(
		TagBase	tag )
	{
		if ( tag.getTagType().getTagType() == TagType.TT_DOWNLOAD_MANUAL ){
			
			TagWithState tws = (TagWithState)tag;
			
			VuzeFile	vf = VuzeFileHandler.getSingleton().create();
	
			Map	map = new HashMap();
		
			Map tag_map = new HashMap();

			tws.exportDetails( vf, tag_map,  false );
			
			Map conf = getConf( tag.getTagType(), tag, false );
			
			map.put( "tag", tag_map );
			
			map.put( "config", conf );
		
			vf.addComponent( VuzeFileComponent.COMP_TYPE_TAG, map );
	
			return( vf );
			
		}else{
			
			return( null );
		}
	}
	
	
	@Override
	public TagConstraint 
	compileConstraint(
		String expression ) 
	{
		if ( constraint_handler == null ){
			
			return( null );
		}
		
		return( constraint_handler.compileConstraint( expression ));
	}
	
	@Override
	public VuzeFile 
	exportTags(
		List<Tag> tags)
	{
		VuzeFile	vf = VuzeFileHandler.getSingleton().create();
		
		for ( Tag tag: tags ){
			
			if ( tag.getTagType().getTagType() == TagType.TT_DOWNLOAD_MANUAL ){
				
				TagWithState tws = (TagWithState)tag;
	
				Map	map = new HashMap();
			
				Map tag_map = new HashMap();
		
				tws.exportDetails( vf, tag_map,  false );
				
				Map conf = getConf( tws.getTagType(), tws, false );
				
				map.put( "tag", tag_map );
				
				map.put( "config", conf );
			
				vf.addComponent( VuzeFileComponent.COMP_TYPE_TAG, map );
			}
		}

		return( vf );
	}
	
	@Override
	public Tag 
	duplicate(
		Tag tag)
	{
		if ( tag.getTagType().getTagType() == TagType.TT_DOWNLOAD_MANUAL ){
			
			TagTypeDownloadManual tt = (TagTypeDownloadManual)tag.getTagType();

			TagWithState tws = (TagWithState)tag;

			Map	map = new HashMap();
		
			Map tag_map = new HashMap();
	
			VuzeFile	vf = VuzeFileHandler.getSingleton().create();
			
			tws.exportDetails( vf, tag_map, false );
			
			Map conf = getConf( tt, tws, false );
				
			tag_map	= BEncoder.cloneMap(tag_map);
			conf 	= BEncoder.cloneMap( conf );
			
			TagDownloadWithState dup_tag = tt.importTag( tag_map, conf );
			
			tt.addTag( dup_tag );
			
			return( dup_tag );
		}else{
			
			return( null );
		}
	}
	
	protected String
	explain(
		Tag				tag,
		TagProperty		property,
		Taggable		taggable )
	{
		if ( property.getName( false ) == TagFeatureProperties.PR_CONSTRAINT ){
			
			return( constraint_handler.explain( tag, taggable ));
			
		}else{
			
			return( "" );
		}
	}
	@Override
	public void
	generate(
		IndentWriter		writer )
	{
		writer.println( "Tag Manager" );

		try{
			writer.indent();

			for ( TagTypeBase tag_type: tag_types ){

				tag_type.generate( writer );
			}
		}finally{

			writer.exdent();
		}
	}

	public void
	generate(
		IndentWriter		writer,
		TagTypeBase			tag_type )
	{
	}

	public void
	generate(
		IndentWriter		writer,
		TagTypeBase			tag_type,
		TagBase				tag )
	{
		synchronized( this ){

			Map conf = getConf( tag_type, tag, false );

			if ( conf != null ){

				conf = BDecoder.decodeStrings( BEncoder.cloneMap( conf ));

				writer.println( BEncoder.encodeToJSON( conf ));
			}
		}
	}

}
