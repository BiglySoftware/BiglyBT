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
import java.net.InetAddress;
import java.util.*;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.category.Category;
import com.biglybt.core.category.CategoryManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagManager;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.tag.TagType;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginConfig;
import com.biglybt.pif.PluginException;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.config.ConfigParameter;
import com.biglybt.pif.config.ConfigParameterListener;
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
		
		Map<String, String>	args = new HashMap<>();
		
		if ( pos != -1 ){
			
			String[] arg_strs = url.substring( pos+1 ).split( "&" );
			
			for ( String arg_str: arg_strs ){
				
				String[]	bits = arg_str.split( "=" );
				
				if ( bits.length == 2 ){
					
					args.put( bits[0].toLowerCase( Locale.US ),  UrlUtils.decode( bits[1] ));
					
				}else{
					
					args.put( bits[0].toLowerCase( Locale.US ), "" );
				}
			}
		}
		
		String key = args.get( "apikey" );
		
		if ( key == null || !key.equals( api_key.getValue())){
			
			response.setReplyStatus( 403 );
			
			log_channel.log( "    access denied" );
			
			return( true );
		}
		
		try{
			String method = args.get( "method" );
					
			if ( method != null ){
			
				method = method.toLowerCase();
				
				GlobalManager gm = CoreFactory.getSingleton().getGlobalManager();
				
				TagManager tm = TagManagerFactory.getTagManager();
				
				if ( method.equals( "test" )){
					
					response.getOutputStream().write( "OK".getBytes( Constants.UTF_8 ));

				}else if ( method.equals( "addtag" ) || method.equals( "addcategory" ) || method.equals( "setcategory" )){
					
					String hash 	= args.get( "hash" );
					String tag_name	= args.get( "tag" );
					
					if ( tag_name == null ){
						
						tag_name	= args.get( "category" );
					}
					
					if ( hash == null || tag_name == null ){
				
						throw( new Exception( "missing parameter" ));
					}
					
					byte[] hash_bytes = UrlUtils.decodeTruncatedHash( hash );
					
					if ( hash_bytes == null ){
						
						throw( new Exception( "Invalid hash (" + hash + ")" ));
					}
					
					DownloadManager dm = gm.getDownloadManager( new HashWrapper( hash_bytes ));
					
					if ( dm == null ){
						
						throw( new Exception( "Download not found for hash " + ByteFormatter.encodeString(hash_bytes)));
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
					
					String hash 	= args.get( "hash" );
					String tag_name	= args.get( "tag" );
										
					if ( hash == null || tag_name == null ){
				
						throw( new Exception( "missing parameter" ));
					}
					
					byte[] hash_bytes = UrlUtils.decodeTruncatedHash( hash );
					
					if ( hash_bytes == null ){
						
						throw( new Exception( "Invalid hash (" + hash + ")" ));
					}
					
					DownloadManager dm = gm.getDownloadManager( new HashWrapper( hash_bytes ));
					
					if ( dm == null ){
						
						throw( new Exception( "Download not found for hash " + ByteFormatter.encodeString(hash_bytes)));
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
					
					String hash 	= args.get( "hash" );
					String networks	= args.get( "networks" );
										
					if ( hash == null || networks == null ){
				
						throw( new Exception( "missing parameter" ));
					}
					
					byte[] hash_bytes = UrlUtils.decodeTruncatedHash( hash );
					
					if ( hash_bytes == null ){
						
						throw( new Exception( "Invalid hash (" + hash + ")" ));
					}
					
					DownloadManager dm = gm.getDownloadManager( new HashWrapper( hash_bytes ));
					
					if ( dm == null ){
						
						throw( new Exception( "Download not found for hash " + ByteFormatter.encodeString(hash_bytes)));
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
				}
			}else{
				
				throw( new Exception( "method missing" ));
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
}
