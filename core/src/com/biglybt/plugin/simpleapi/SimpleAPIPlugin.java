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
import java.util.*;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.category.Category;
import com.biglybt.core.category.CategoryManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagManager;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.tag.TagType;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginException;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.ipc.IPCException;
import com.biglybt.pif.logging.LoggerChannel;
import com.biglybt.pif.tracker.web.TrackerWebPageRequest;
import com.biglybt.pif.tracker.web.TrackerWebPageResponse;
import com.biglybt.pif.ui.config.ActionParameter;
import com.biglybt.pif.ui.config.HyperlinkParameter;
import com.biglybt.pif.ui.config.StringParameter;
import com.biglybt.pif.ui.model.BasicPluginConfigModel;
import com.biglybt.ui.webplugin.WebPlugin;



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
			String result = process( args, multi_args );
			
			if ( result != null ){
				
				response.getOutputStream().write( result.getBytes( Constants.UTF_8 ));
			}
			
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
						
						value = value.toLowerCase( Locale.US );
						
						boolean disable;
						
						if ( value.equals( "true" ) || value.equals( "1" ) || value.equals( "y" )){
							
							disable = false;
							
						}else if ( value.equals( "false" ) || value.equals( "0" ) || value.equals( "n" )){

							disable = true;
							
						}else{
							
							throw( new Exception( "invalid boolean value (" + value + ")" ));
						}
						
						dm.getDownloadState().setFlag( DownloadManagerState.FLAG_DISABLE_IP_FILTER, disable );
						
					}else{
						
						throw( new Exception( "invalid 'name' parameter (" + name + ")" ));
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
						throw( new Exception( "Invalid type (" + type + ")" ));
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
			}
		}else{
			
			throw( new Exception( "method missing" ));
		}
		
		return( null );
	}
	
	public Object
	evalScript(
		Map<String,Object>		eval_args )
	
		throws IPCException
	{
		String		intent		= (String)eval_args.get( "intent" );
		
		Download	download 	= (Download)eval_args.get( "download" );
		
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
			
			args.put( "hash", ByteFormatter.encodeString( download.getTorrentHash()));
		
			try{
				String result = process( args, multi_args );
				
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
