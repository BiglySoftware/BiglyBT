/*
 * Created on Oct 9, 2009
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


package com.biglybt.plugin.simpleapi;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.util.*;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.category.Category;
import com.biglybt.core.category.CategoryManager;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.disk.DiskManagerFileInfoSet;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.subs.Subscription;
import com.biglybt.core.subs.SubscriptionManager;
import com.biglybt.core.subs.SubscriptionManagerFactory;
import com.biglybt.core.subs.SubscriptionResult;
import com.biglybt.core.subs.util.SubscriptionResultFilterable;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagManager;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.tag.TagType;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentFactory;
import com.biglybt.core.torrent.TOTorrentFile;
import com.biglybt.core.torrent.impl.TorrentOpenOptions;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginException;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.ipc.IPCException;
import com.biglybt.pif.logging.LoggerChannel;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.torrent.TorrentDownloader;
import com.biglybt.pif.torrent.TorrentManager;
import com.biglybt.pif.tracker.web.TrackerWebPageRequest;
import com.biglybt.pif.tracker.web.TrackerWebPageResponse;
import com.biglybt.pif.ui.config.ActionParameter;
import com.biglybt.pif.ui.config.HyperlinkParameter;
import com.biglybt.pif.ui.config.StringParameter;
import com.biglybt.pif.ui.model.BasicPluginConfigModel;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.webplugin.WebPlugin;
import com.biglybt.util.JSONUtils;



public class
SimpleAPIPlugin
	extends WebPlugin
{
	public static final String	PLUGIN_NAME		= "Simple API";
	public static final int 	DEFAULT_PORT    = 6906;
	public static final String	DEFAULT_ACCESS	= "local";

	private static volatile SimpleAPIPlugin		singleton;

	private static boolean	loaded;

	private static final Properties defaults = new Properties();

	private static LoggerChannel		log_channel;

	public static void
	load(
		PluginInterface		plugin_interface )
	{
		plugin_interface.getPluginProperties().setProperty( "plugin.version", 	"1.0" );
		plugin_interface.getPluginProperties().setProperty( "plugin.name", 		PLUGIN_NAME );

		synchronized( SimpleAPIPlugin.class ){

			if ( loaded ){

				return;
			}

			loaded = true;
		}

		log_channel = plugin_interface.getLogger().getChannel( PLUGIN_NAME );

		defaults.put( WebPlugin.PR_ENABLE, false );
		defaults.put( WebPlugin.PR_DISABLABLE, true );
		defaults.put( WebPlugin.PR_ROOT_DIR, AETemporaryFileHandler.getTempDirectory().getAbsolutePath());		
	    defaults.put( WebPlugin.PR_PORT, DEFAULT_PORT );
	    defaults.put( WebPlugin.PR_ACCESS, DEFAULT_ACCESS );
	    defaults.put( WebPlugin.PR_ENABLE_KEEP_ALIVE, true );
	    defaults.put( WebPlugin.PR_HIDE_RESOURCE_CONFIG, true );
	    defaults.put( WebPlugin.PR_ENABLE_PAIRING, false );
	    defaults.put( WebPlugin.PR_ENABLE_UPNP, false );
	    defaults.put( WebPlugin.PR_ENABLE_I2P, false );
	    defaults.put( WebPlugin.PR_ENABLE_TOR, false );
	    defaults.put( WebPlugin.PR_LOG, log_channel );
	}

	public static SimpleAPIPlugin
	getSingleton()
	{
		return( singleton );
	}

	private PluginInterface		plugin_interface;
	
	private StringParameter		api_key;
	
	private HyperlinkParameter	test_param;
	
	public
	SimpleAPIPlugin()
	{
		super( defaults );
	}

	@Override
	public void
	initialize(
		PluginInterface		pi )

		throws PluginException
	{
		plugin_interface = pi;
		
		singleton = this;

		pi.getPluginProperties().setProperty( "plugin.name", PLUGIN_NAME );
		
		super.initialize( pi );
	}

	@Override
	protected void
	initStage(
		int	num )
	{
		if ( num == 1 ){

			BasicPluginConfigModel  config = getConfigModel();

			api_key = config.addStringParameter2( "apikey", "plugin.simpleapi.apikey", "" );

			if ( api_key.getValue().isEmpty()){
				
				api_key.setValue( createAPIKey());
			}
			
			ActionParameter change = config.addActionParameter2( "plugin.simpleapi.apikey.new", "pairing.srp.setpw.doit" );
			
			change.addListener((n)->{
				
				api_key.setValue( createAPIKey());
				
				updateTestParam();
			});
			
			test_param = config.addHyperlinkParameter2("plugin.simpleapi.test", "");
		
		}else if ( num == 2 ){
			
			updateTestParam();
		}
	}

	protected boolean
	verifyReferrer()
	{
		return( false );	// we have an apikey, that is sufficient
	}
	
	@Override
	protected void
	setupServer()
	{
		try{
			super.setupServer();
			
		}finally{
			updateTestParam();
		}
	}
	
	private void 
	updateTestParam()
	{
		test_param.setHyperlink( getServerURL() + "?apikey=" + api_key.getValue() + "&method=test" );
	}

	private String
	createAPIKey()
	{
		byte[] bytes = new byte[32];
		
		RandomUtils.nextSecureBytes( bytes );
		
		return( Base32.encode(bytes).toLowerCase());
	}
	
	@Override
	public boolean
	generateSupport(
		TrackerWebPageRequest		request,
		TrackerWebPageResponse		response )

		throws IOException
	{
		String url = request.getURL();
		
		if ( url.contains( "favicon.ico" )){

			try{
				InputStream stream = getClass().getClassLoader().getResourceAsStream("com/biglybt/ui/icons/favicon.ico" );

				response.useStream( "image/x-icon", stream);

				return( true );

			}catch( Throwable e ){
			}
		}
		
		log_channel.log( request.getClientAddress() + ": " + url );
		
		int pos = url.indexOf( '?' );
		
		Map<String, String>			args 		= new HashMap<>();
		Map<String, List<String>>	multi_args	= new HashMap<>();
		
		if ( pos != -1 ){
			
			String[] arg_strs = url.substring( pos+1 ).split( "&" );
			
			for ( String arg_str: arg_strs ){
				
				String[]	bits = arg_str.split( "=" );
				
				String name = bits[0].toLowerCase( Locale.US );
				String value;
				
				if ( bits.length == 2 ){
					
					value = UrlUtils.decode( bits[1] );
										
				}else{
					
					value = "";
				}
				
				args.put( name, value );
				
				List<String> multi_value = multi_args.get( name );
				
				if ( multi_value == null ){
					
					multi_value = new ArrayList<>();
					
					multi_args.put( name, multi_value );
				}
				
				multi_value.add( value );
			}
		}
		
		String key = args.get( "apikey" );
		
		if ( key == null || !key.equals( api_key.getValue())){
			
			response.setReplyStatus( 403 );
			
			log_channel.log( "    access denied" );
			
			return( true );
		}
		
		try{
			String result = process( response, args, multi_args );
			
			if ( result != null ){
				
				response.getOutputStream().write( result.getBytes( Constants.UTF_8 ));
			}
						
			response.setHeader( "Access-Control-Allow-Origin", "*" );

			response.setReplyStatus( 200 );
			
			return( true );
			
		}catch( Throwable e ){
			
			log_channel.log( "    error: " + Debug.getNestedExceptionMessage( e ));
			
			Debug.out( e );
			
			response.setReplyStatus( 500 );
			
			response.getOutputStream().write( Debug.getNestedExceptionMessage( e ).getBytes( Constants.UTF_8 ));
			
			return( true );
		}
	}
	
	private DownloadManager
	getDownloadFromHash(
		Map<String,String>	args )
	
		throws Exception
	{
		GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();
		
		String hash 	= args.get( "hash" );

		if ( hash == null ){
			
			throw( new Exception( "missing 'hash' parameter" ));
		}
		
		byte[] hash_bytes = UrlUtils.decodeTruncatedHash( hash );
		
		if ( hash_bytes == null ){
			
			throw( new Exception( "Invalid hash (" + hash + ")" ));
		}
		
		DownloadManager dm = gm.getDownloadManager( new HashWrapper( hash_bytes ));
		
		if ( dm == null ){
			
			throw( new Exception( "Download not found for hash " + ByteFormatter.encodeString(hash_bytes)));
		}

		return( dm );
	}
	
	private String
	process(
		TrackerWebPageResponse		response,
		Map<String,String>			args,
		Map<String,List<String>>	multi_args )
	
		throws Exception
	{
		String method = args.get( "method" );
		
		if ( method != null ){
		
			method = method.toLowerCase( Locale.US );
						
			TagManager tm = TagManagerFactory.getTagManager();
			
			if ( method.equals( "test" )){
				
				return( "OK" );

			}else if ( method.equals( "listdownloads" )){
				
				JSONArray json = new JSONArray();
				
				List<DownloadManager> dms = CoreFactory.getSingleton().getGlobalManager().getDownloadManagers();
				
				for ( DownloadManager dm: dms ){
					
					try{
						Download download = PluginCoreUtils.wrap( dm );
						
						TOTorrent torrent = dm.getTorrent();
						
						JSONObject	obj = new JSONObject();
						
						obj.put( "DisplayName", dm.getDisplayName());
						
						obj.put( "InfoHash", torrent==null?"":ByteFormatter.encodeString( torrent.getHash()));
						
						String save_path = download.getSavePath();
						
						obj.put( "SavePath", save_path );
						
						boolean	sp_is_file;
						
						if ( torrent.isSimpleTorrent()){
							
							sp_is_file = true;
							
						}else{
						
							File sp_file = new File( save_path );
						
							if ( sp_file.exists()){
							
								sp_is_file = sp_file.isFile();
								
							}else{
									// possible to have a non-simple torrent with a single file
									// with containing-folder removed...
								
								DiskManagerFileInfo[] files = dm.getDiskManagerFileInfoSet().getFiles();
								
								if ( files.length > 1 ){
									
									sp_is_file = false;
									
								}else{
									
									File f = files[0].getFile( true );
									
									sp_is_file = FileUtil.areFilePathsIdentical( sp_file, f );
								}
							}
						}
						
						obj.put( "SavePathType", sp_is_file?"File":"Directory" );
						
						try{
							List<Tag> tags = TagManagerFactory.getTagManager().getTagsForTaggable( TagType.TT_DOWNLOAD_MANUAL, dm );
							
							JSONArray tags_a = new JSONArray();
							
							for ( Tag t: tags ){
								
								tags_a.add( t.getTagName( true ));
							}
							
							obj.put( "Tags", tags_a );
							
						}catch( Throwable e ){
							
							Debug.out( e );
						}
						json.add( obj );
						
					}catch( Throwable e ){
						
						Debug.out( e );
					}
				}
				
				if ( response != null ){
					
					response.setContentType( "application/json; charset=UTF-8" );
				}
				
				return( JSONUtils.encodeToJSON( json ));
				
			}else if ( method.equals( "listfiles" )){
				
				DownloadManager dm = getDownloadFromHash( args );
				
				TOTorrent torrent = dm.getTorrent();
				
				Map<String,byte[]> rh_cache = (Map<String,byte[]>)TorrentUtils.getV2RootHashCache( torrent );
				
				boolean rh_cache_updated = false;
				
				String crh_str	= args.get( "calc_root_hashes" );
				
				boolean calc_root_hashes = crh_str != null && getBoolean( crh_str );
				
				DiskManagerFileInfo[] files = dm.getDiskManagerFileInfoSet().getFiles();
				
				JSONArray json = new JSONArray();

				for ( DiskManagerFileInfo file: files ){
					
					TOTorrentFile t_file = file.getTorrentFile();
					
					JSONObject	obj = new JSONObject();

					int index		= t_file.getIndex();
					
					long size		= t_file.getLength();
					long downloaded	= file.getDownloaded();
					
					obj.put( "Index", index );
					
					obj.put( "Size", size );
					
					obj.put( "Downloaded", downloaded );
					
					String relative_path = t_file.getRelativePath();
					
					int pos = relative_path.lastIndexOf( File.separator );
					
					obj.put( "Name", pos<0?relative_path:relative_path.substring( pos+1 ));
					
					obj.put( "RelativePath", relative_path);
					
					File data_file = file.getFile( true );
					
					obj.put( "DataPath", data_file.getAbsolutePath());
					
					boolean is_pad = t_file.isPadFile();
					
					obj.put( "IsPad", is_pad );
					
					obj.put( "IsSkipped", file.isSkipped());
					
					obj.put( "Priority", file.getPriority());
					
					byte[] root_hash = null;
					
					if ( !is_pad ){
					
						root_hash = t_file.getRootHash();

						if ( root_hash == null ){
													
							if ( calc_root_hashes && size > 0 && size == downloaded && data_file.length() == size && !t_file.isPadFile()){
							
								String key = String.valueOf( index );
								
								if ( rh_cache != null ){
									
									root_hash = rh_cache.get( key );
								}
								
								if ( root_hash == null ){
								
									try{
										root_hash = TOTorrentFactory.getV2RootHash( data_file );
							
										if ( root_hash != null ){
										
											if ( rh_cache == null ){
												
												rh_cache = new HashMap<String,byte[]>();
											}
										
											rh_cache.put( key, root_hash );
											
											rh_cache_updated = true;
										}
									}catch( Throwable e ){
										
										Debug.out( e );
									}
								}
							}
						}
					}
					
					if ( root_hash != null ){
					
						obj.put( "RootHash", Base32.encode( root_hash ));
					}
					
					json.add( obj );
				}
				
				if ( rh_cache_updated ){
					
					try{
						TorrentUtils.setV2RootHashCache( torrent, rh_cache );
						
						TorrentUtils.writeToFile( torrent );
						
					}catch( Throwable e ){
						
						Debug.out( e );
					}
				}
				
				if ( response != null ){
					
					response.setContentType( "application/json; charset=UTF-8" );
				}
				
				return( JSONUtils.encodeToJSON( json ));
				
			}else if ( method.equals( "addtag" ) || method.equals( "addcategory" ) || method.equals( "setcategory" )){
				
				DownloadManager dm = getDownloadFromHash( args );

				String tag_name	= args.get( "tag" );
				
				if ( tag_name == null ){
					
					tag_name	= args.get( "category" );
				}
				
				if ( tag_name == null ){
			
					throw( new Exception( "missing parameter" ));
				}
																
				int tag_type = method.equals( "addtag" )?TagType.TT_DOWNLOAD_MANUAL:TagType.TT_DOWNLOAD_CATEGORY;
				
				if ( tag_type == TagType.TT_DOWNLOAD_CATEGORY && tag_name.isEmpty()){
					
					Category uncat = CategoryManager.getCategory( Category.TYPE_UNCATEGORIZED );
					
					dm.getDownloadState().setCategory( uncat );
					
				}else{
					
					TagType tt = tm.getTagType( tag_type );
					
					Tag tag = tt.getTag( tag_name, true );
					
					if ( tag == null ){
						
						tag = tt.createTag( tag_name, true );
					}
					
					if ( !tag.hasTaggable( dm )){
					
						tag.addTaggable( dm );
					}
				}
				
			}else if ( method.equals( "removetag" )){

				DownloadManager dm = getDownloadFromHash( args );

				String tag_name	= args.get( "tag" );
									
				if ( tag_name == null ){
			
					throw( new Exception( "missing parameter" ));
				}
													
				TagType tt = tm.getTagType( TagType.TT_DOWNLOAD_MANUAL );
				
				Tag tag = tt.getTag( tag_name, true );
				
				if ( tag == null ){
				
					throw( new Exception( "Tag '" + tag_name + "' not found" ));
				}
				
				if ( tag.hasTaggable( dm )){
						
					tag.removeTaggable( dm );
				}
			}else if ( method.equals( "setnetworks" )){
	
				DownloadManager dm = getDownloadFromHash( args );

				String networks	= args.get( "networks" );
									
				if ( networks == null ){
			
					throw( new Exception( "missing parameter" ));
				}
	
				networks = networks.trim();
				
				if ( networks.isEmpty()){
					
					dm.getDownloadState().setNetworks( new String[0] );
					
				}else{
					
					String[] bits = networks.split( "," );
					
					String[] nets = new String[bits.length];
							
					for ( int i=0;i<bits.length;i++){
						
						String bit = bits[i].trim();
					
						String net = AENetworkClassifier.internalise( bit );
						
						if ( net == null ){
							
							throw( new Exception( "Invalid network (" + bit + ")" ));
						}
						
						nets[i] = net;
					}
					
					dm.getDownloadState().setNetworks( nets );
				}
			}else if ( method.equals( "setdownloadattribute" )){
				
				DownloadManager dm = getDownloadFromHash( args );

				List<String> names	= multi_args.get( "name" );
									
				if ( names == null ){
			
					throw( new Exception( "missing 'name' parameter" ));
				}
				
				List<String> values	= multi_args.get( "value" );
				
				if ( values == null ){
			
					throw( new Exception( "missing 'value' parameter" ));
				}
				
				if ( names.size() != values.size()){
					
					throw( new Exception( "'name' and 'value' parameter count mismatch" ));
				}
				
				for ( int i=0; i<names.size(); i++ ){
					
					String	name 	= names.get(i);
					String	value	= values.get(i);
				
					name = name.toLowerCase( Locale.US );
					
					if ( name.equals( "completedon" )){
						
						try{
							long time = Long.parseLong( value )*1000;
							
							DownloadManagerState dms = dm.getDownloadState();
												
							dms.setLongParameter( DownloadManagerState.PARAM_DOWNLOAD_COMPLETED_TIME, time );
							
							dms.setLongAttribute( DownloadManagerState.AT_COMPLETE_LAST_TIME, time );
	
						}catch( Throwable e ){
								
							throw( new Exception( "invalid 'value' parameter (" + value + ")" ));	
						}
					}else if ( name.equals( "displayname" )){
						
						dm.getDownloadState().setDisplayName( value );
						
					}else if ( name.equals( "savepath" )){
						
						if ( value.contains( File.pathSeparator )){
							
							throw( new Exception( "invalid savepath, must not contain path separators" ));
						}
						
						boolean save_loc_is_folder = dm.getSaveLocation().isDirectory();

						String new_save_path	= FileUtil.convertOSSpecificChars( value, save_loc_is_folder );
						
						try{
							if ( dm.getTorrent().isSimpleTorrent()){

								String dnd_sf = dm.getDownloadState().getAttribute( DownloadManagerState.AT_INCOMP_FILE_SUFFIX );

								if ( dnd_sf != null ){

									dnd_sf = dnd_sf.trim();

									String existing_name = dm.getSaveLocation().getName();

									if ( existing_name.endsWith( dnd_sf )){

										if ( !new_save_path.endsWith( dnd_sf )){

											new_save_path += dnd_sf;
										}
									}
								}
							}
						}catch( Throwable e ){
						}
						
						dm.renameDownload( new_save_path );
						
					}else if ( name.equals( "torrentname" )){
						
						if ( value.contains( File.pathSeparator )){
							
							throw( new Exception( "invalid torrentname, must not contain path separators" ));
						}

						String new_torrent_name	= FileUtil.convertOSSpecificChars( value, false );
						
						dm.renameTorrentSafe( new_torrent_name );
						
					}else if ( name.equals( "usercomment" )){

						dm.getDownloadState().setUserComment( value );
						
					}else if ( name.equals( "ipfilterenable" )){
						
						boolean enable = getBoolean( value );
						
						dm.getDownloadState().setFlag( DownloadManagerState.FLAG_DISABLE_IP_FILTER, !enable );
						
					}else{
						
						throw( new Exception( "invalid 'name' parameter (" + name + ")" ));
					}
				}
			}else if ( method.equals( "setdownloadfileattribute" )){
				
				DownloadManager dm = getDownloadFromHash( args );

				boolean paused = false;
				
				try{
					List<String> names	= multi_args.get( "name" );
					
					if ( names == null ){
				
						throw( new Exception( "missing 'name' parameter" ));
					}
					
					List<String> values	= multi_args.get( "value" );
					
					if ( values == null ){
				
						throw( new Exception( "missing 'value' parameter" ));
					}
					
					List<String> indexes	= multi_args.get( "index" );
					
					if ( indexes == null ){
				
						throw( new Exception( "missing 'index' parameter" ));
					}
					
					
					if ( names.size() != values.size() || values.size() != indexes.size()){
						
						throw( new Exception( "'index', 'name' and 'value' parameter count mismatch" ));
					}
					
					DiskManagerFileInfoSet info_set = dm.getDiskManagerFileInfoSet();
					
					DiskManagerFileInfo[] files = info_set.getFiles();
	
					for ( int i=0; i<names.size(); i++ ){
						
						String	name 	= names.get(i);
						String	value	= values.get(i);
					
						name = name.toLowerCase( Locale.US );
	
						String i_str	= indexes.get( i );
													
						int	index;
						
						try{
							index = Integer.parseInt(i_str);
							
						}catch( Throwable e ){
							
							throw( new Exception( "'index' parameter invalid (" + i_str + ")" ));
						}
											
						if ( index < 0 || index >= files.length ){
							
							throw( new Exception( "'index' parameter out of range (files=" + files.length + ")" ));
						}
						
						DiskManagerFileInfo file = files[index];
						
						if ( name.equals( "datapath" )){
							
							String data_path = value;
							
							if ( dm.pause( true )){
								
								paused = true;
							}
							
							file.setLink( new File( data_path ), true );
							
						}else if ( name.equals( "skipped" )){
							
							file.setSkipped( getBoolean( value ));

						}else if ( name.equals( "priority" )){
						
							try{
								file.setPriority( Integer.parseInt( value ));
								
							}catch( Throwable e ){
								
								throw( new Exception( "'value' parameter invalid for priority(" + value + ")" ));
							}
						}
					}
				}finally{
				
					if ( paused ){
					
						dm.resume();
					}
				}
			}else if ( method.equals( "alert" )){
				
				DownloadManager dm = getDownloadFromHash( args );

				String caption	= args.get( "caption" );
				
				if ( caption == null ){
					
					caption = "";
				}
				
				int atype = LogAlert.AT_INFORMATION;
				
				String type	= args.get( "type" );
				
				if ( type != null ){
					
					if ( type.equals( "info" )){
						
					}else if ( type.equals( "error" )){
						
						atype = LogAlert.AT_ERROR;
						
					}else{
						throw( new Exception( "invalid type (" + type + ")" ));
					}
				}
				
				LogAlert alert = new LogAlert( LogAlert.REPEATABLE, atype, caption );
				
				alert.details = dm.getDisplayName();
				
				String details	= args.get( "details" );

				if ( details != null && !details.isEmpty()){
					
					alert.details += "\n\n" + details;
				}
				
				alert.isNative = true;
				
				Logger.log( alert );
				
			}else if ( method.equals( "playsound" )){
				
				String optional_file	= args.get( "file" );
				
				GeneralUtils.playSound( optional_file );
				
			}else if ( 	method.equals( "markresultsread" ) ||  
						method.equals( "markallresultsread" ) ||	// remove this post 3101_B02
						method.equals( "markresultsreadinall")){
				
				SubscriptionManager subs_man = SubscriptionManagerFactory.getSingleton();
				
				String subs_id	= args.get( "subscription_id" );
				
				if ( subs_id == null ){
					
					throw( new Exception( "missing 'subscription_id' parameter" ));
				}
				
				Subscription subs = subs_man.getSubscriptionByID( subs_id );
				
				if ( subs == null ){
					
					throw( new Exception( "subscripton '" + subs_id + "' not found" ));
				}
				
				List<String> result_ids = new ArrayList<>();

				{
					String result_id	= args.get( "subscription_result_id" );
									
					if ( result_id == null ){
						
						String result_ids_str	= args.get( "subscription_result_ids" );
						
						if ( result_ids_str == null ){
						
							throw( new Exception( "missing 'subscription_result_id(s)' parameter" ));
						}
						
						String[] bits = result_ids_str.split( "," );
						
						for ( String bit: bits ){
							
							bit = bit.trim();
							
							if ( !bit.isEmpty()){
								
								result_ids.add( bit );
							}
						}
					}else{
						
						result_ids.add( result_id );
					}
				}
				
				if ( method.equals( "markresultsread" )){
					
					boolean[] read = new boolean[result_ids.size()];
					
					Arrays.fill( read, true );
					
					subs.getHistory().markResults( result_ids.toArray( new String[ read.length]), read );
					
				}else{
					
					List<SubscriptionResultFilterable> srfs = new ArrayList<>( result_ids.size());
					
					for ( String result_id: result_ids ){
					
						SubscriptionResult result = subs.getHistory().getResult( result_id );
					
						if ( result == null ){
						
							throw( new Exception( "subscription result '" + result_id + "' not found" ));
							
						}else{
							
							srfs.add( new SubscriptionResultFilterable( subs, result ));
						}
					}
					
					SubscriptionResultFilterable[] results = srfs.toArray( new SubscriptionResultFilterable[srfs.size()]);
						
					subs_man.markReadInAllSubscriptions( results );
				}
			}else if ( 	method.equals( "addtorrent" ) || 
						method.equals( "adddownload" )){
				
				String[] target_args = { "file", "magnet", "url", "torrent" };
				
				String original_target = null;
				
				for ( String ta: target_args ){
					
					original_target = args.get( ta );
					
					if ( original_target != null ){
						
						break;
					}
				}
				
				if ( original_target == null ){
					
					throw( new Exception( "missing file/magnet/url/torrent parameter" ));
				}
				
				String f_original_target = original_target;
				
				String target = original_target;

				File 	file = null;
				URL		url = null;
				
				try{
					File f = FileUtil.newFile( target );
					
					if ( f.exists()){
						
						file = f;
					}
				}catch( Throwable e ){
				}
				
				if ( file == null ){
					
					try{
						File f = FileUtil.newFile( new URI( target ));
						
						if ( f.exists()){
							
							file = f;
						}
					}catch( Throwable e ){
					}
				}
				
				if ( file == null ){
					
					target = UrlUtils.decode( target );
					
					target = target.trim().replaceAll(" ", "%20");
	
					// hack due to core bug - have to add a bogus arg onto magnet uris else they fail to parse
	
					String lc_target = target.toLowerCase(Locale.US);
	
					if ( lc_target.startsWith("magnet:")) {
	
						target += "&dummy_param=1";
	
					}else if ( !lc_target.startsWith("http")){
	
						String temp = UrlUtils.parseTextForURL( target, true, true );
						
						if ( temp != null ){
							
							target = temp;
						}
					}
					
					try{
						url = new URL( target );
						
					}catch( Throwable e ){
					}
				}
				
				if ( file != null ){
					
					try{
						TOTorrent torrent = TorrentUtils.readFromFile( file, false );
						
						addTorrent( torrent );
						
					}catch( Throwable e ){
						
						throw( new Exception( "failed to read torrent from '" + file.getAbsolutePath() + "'" ));
					}
				}else if ( url != null ){
					
					URL f_url = url;
					
					TorrentManager torrentManager = plugin_interface.getTorrentManager();

					TorrentDownloader dl = torrentManager.getURLDownloader( url, null, null );

					UIFunctions uif = UIFunctionsManager.getUIFunctions();
					
					AEThread2.createAndStartDaemon( "SAPI:tdl", ()->{
						
						Object sk = 
							uif.pushStatusText( 
								MessageText.getString("fileDownloadWindow.state_downloading") + ": " + f_original_target );
						
						try{
	
							Torrent torrent = dl.download( Constants.DEFAULT_ENCODING );
									
							uif.popStatusText( sk, 0, null );
							
							sk = null;
							
							addTorrent( PluginCoreUtils.unwrap( torrent ));
							
						}catch( Throwable e ){
							
								// see if we can convert to a magnet
							
							boolean alt_tried = false;

							try{
								String url_str = f_url.toExternalForm();
								
									// remove the protocol so we don't just find the same url when parsing the "text"
								
								url_str = url_str.substring( url_str.indexOf( ":" ));
								
								String alt_target = UrlUtils.parseTextForURL( url_str, true, true );
															
								if ( alt_target != null ){
									
									URL url2 = new URL( alt_target );
									
									if ( !f_url.equals( url2 )){									
										
										uif.popStatusText( sk, 1, null );
										
										sk = null;

										TorrentDownloader dl2 = torrentManager.getURLDownloader( url2, null, null );
		
										AEThread2.createAndStartDaemon( "SAPI:tdl2", ()->{
											
											Object sk2 = 
												uif.pushStatusText( 
														MessageText.getString("fileDownloadWindow.state_downloading") + ": " + url2.toExternalForm());
											
											try{
												Torrent torrent = dl2.download( Constants.DEFAULT_ENCODING );
												
												uif.popStatusText( sk2, 0, null );
												
												sk2 = null;
												
												addTorrent( PluginCoreUtils.unwrap( torrent ));
												
											}catch( Throwable f ){
											
												log_channel.log( "Torrent download failed for '" + f_url + "'", e );
												
												uif.popStatusText( sk2, 2, Debug.getNestedExceptionMessage(f));
												
												sk2 = null;
												
											}finally{
												
												if ( sk2 != null ){
												
													uif.popStatusText( sk2, 2, null );
												}
											}
										});
									
										alt_tried = true;
									}
								}
							}catch( Throwable f ){									
							}
							
							if ( !alt_tried ){
															
								log_channel.log( "Torrent download failed for '" + f_url + "'", e );
								
								uif.popStatusText( sk, 2, Debug.getNestedExceptionMessage(e));
								
								sk = null;
							}
						}finally{
							
							if ( sk != null ){
							
								uif.popStatusText( sk, 2, null );
							}
						}
					});
					
				}else{
					
					throw( new Exception( "invalid file/magnet/url parameter '" + original_target + "'" ));
				}
			}else{
				throw( new Exception( "unsupported method '" + method + "'" ));
			}
		}else{
			
			throw( new Exception( "method missing" ));
		}
		
		return( null );
	}
	
	private void
	addTorrent(
		TOTorrent		torrent )
	{
		try{
			GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();
	
			DownloadManager existing_dm = gm.getDownloadManager( torrent );
	
			if ( existing_dm != null ){
				
				log_channel.log( "Download '" + existing_dm.getDisplayName() + "' already added" );
				
				return;
			}
			
			TorrentOpenOptions torrentOptions = new TorrentOpenOptions( null );
		
			torrent = TorrentUtils.cloneTorrent( torrent );
			
			File to_file = AETemporaryFileHandler.createTempFile();
				
			TorrentUtils.writeToFile( torrent, to_file, false );											
			
			torrent = TorrentUtils.readFromFile( to_file, false );
					
			torrentOptions.setDeleteFileOnCancel( true );
			torrentOptions.setTorrentFile( to_file.getAbsolutePath());
			torrentOptions.setTorrent( torrent );
			
			UIFunctions uif = UIFunctionsManager.getUIFunctions();
	
			uif.addTorrentWithOptions( false, torrentOptions );
			
			log_channel.log( "Added download '" + new String( torrent.getName()) + "'");
			
		}catch( Throwable e ){
			
			log_channel.log( "Failed to add download '" + new String( torrent.getName()) + "'", e );
		}
	}
	
	private boolean
	getBoolean(
		String		value )
	
		throws Exception
	{
		value = value.toLowerCase( Locale.US );
				
		if ( value.equals( "true" ) || value.equals( "1" ) || value.equals( "y" )){
			
			return( true );
			
		}else if ( value.equals( "false" ) || value.equals( "0" ) || value.equals( "n" )){

			return( false );
			
		}else{
			
			throw( new Exception( "invalid boolean value (" + value + ")" ));
		}	
	}
	
	public Object
	evalScript(
		Map<String,Object>		eval_args )
	
		throws IPCException
	{
		String		intent		= (String)eval_args.get( "intent" );
				
		String		scripts		= (String)eval_args.get( "script" );
		
		scripts = scripts.trim();
		
		if ( 	scripts.length() > 2 && 
				GeneralUtils.startsWithDoubleQuote( scripts ) && 
				GeneralUtils.endsWithDoubleQuote(scripts)){
			
			scripts = scripts.substring( 1, scripts.length()-1 );
			
			scripts = scripts.trim();
		}
		
		log_channel.log( intent + " - " + scripts );
				
		String[] script_strs = scripts.split( ";" );
		
		List<String>	results = new ArrayList<>();
		
		Download	download 	= (Download)eval_args.get( "download" );

		Subscription subscription = (Subscription)eval_args.get( "subscription" );
		
		SubscriptionResult			subscription_result = (SubscriptionResult)eval_args.get( "subscription_result" );
		List<SubscriptionResult>	subscription_results = (List<SubscriptionResult>)eval_args.get( "subscription_results" );
		
		for ( String script: script_strs ){
			
			script = script.trim();
			
			if ( script.isEmpty()){
				
				continue;
			}
			
			Map<String, String>			args 		= new HashMap<>();
			Map<String, List<String>>	multi_args	= new HashMap<>();
						
			String[] arg_strs = script.split( "&" );
			
			for ( String arg_str: arg_strs ){
				
				String[]	bits = arg_str.split( "=" );
				
				String name = bits[0].toLowerCase( Locale.US );
				String value;
				
				if ( bits.length == 2 ){
					
					value = UrlUtils.decode( bits[1] );
										
				}else{
					
					value = "";
				}
				
				args.put( name, value );
				
				List<String> multi_value = multi_args.get( name );
				
				if ( multi_value == null ){
					
					multi_value = new ArrayList<>();
					
					multi_args.put( name, multi_value );
				}
				
				multi_value.add( value );
			}
	
			args.put( "apikey,", api_key.getValue());
			
			if ( download != null ){
			
				args.put( "hash", ByteFormatter.encodeString( download.getTorrentHash()));
			}
			
			if ( subscription != null ){
				
				args.put( "subscription_id", subscription.getID());
			}
			
			if ( subscription_result != null ){
				
				args.put( "subscription_result_id", subscription_result.getID());
				
			}else if ( subscription_results != null ){
			
				StringBuffer sr_ids = new StringBuffer();
				
				for ( SubscriptionResult sr: subscription_results ){
					
					if ( sr_ids.length() > 0 ){
						
						sr_ids.append(",");
					}
					 
					sr_ids.append( sr.getID());
				}
				
				args.put( "subscription_result_ids", sr_ids.toString());
			}
			
			try{
				String result = process( null, args, multi_args );
				
				if ( result != null ){
					
					results.add( result );
				}
				
			}catch( Throwable e ){
				
				log_channel.log( "    error: " + Debug.getNestedExceptionMessage( e ));
				
				throw( new IPCException(e));
			}
		}
		
		if ( results.isEmpty()){
			
			return( null );
			
		}else{
			
			String str = "";
			
			for ( String result: results ){
				
				str += (str.isEmpty()?"":";") + result;
			}
			
			return( str );
		}
	}
}
