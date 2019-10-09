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


package com.biglybt.plugin.rssgen;

import java.io.*;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import org.json.jsonjava.JSONJava;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.SystemProperties;
import com.biglybt.core.util.UrlUtils;
import com.biglybt.pif.PluginException;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.tracker.web.TrackerWebPageGenerator;
import com.biglybt.pif.tracker.web.TrackerWebPageRequest;
import com.biglybt.pif.tracker.web.TrackerWebPageResponse;
import com.biglybt.pif.ui.config.BooleanParameter;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.config.HyperlinkParameter;
import com.biglybt.pif.ui.model.BasicPluginConfigModel;
import com.biglybt.ui.webplugin.WebPlugin;


public class
RSSGeneratorPlugin
	extends WebPlugin
{
	public static final String	PLUGIN_NAME		= "Local RSS etc.";
	public static final int 	DEFAULT_PORT    = 6905;
	public static final String	DEFAULT_ACCESS	= "all";

	private static volatile RSSGeneratorPlugin		singleton;

	private static boolean	loaded;

	private static final Properties defaults = new Properties();

	public static void
	load(
		PluginInterface		plugin_interface )
	{
		plugin_interface.getPluginProperties().setProperty( "plugin.version", 	"1.0" );
		plugin_interface.getPluginProperties().setProperty( "plugin.name", 		PLUGIN_NAME );

		synchronized( RSSGeneratorPlugin.class ){

			if ( loaded ){

				return;
			}

			loaded = true;
		}

		File	root_dir = new File( SystemProperties.getUserPath() + "rss" );

		if ( !root_dir.exists()){

			root_dir.mkdir();
		}

		Integer	rss_port;
		String	rss_access;

		if ( COConfigurationManager.getBooleanParameter( "rss.internal.migrated", false )){

			rss_port 	= COConfigurationManager.getIntParameter( "rss.internal.config.port", DEFAULT_PORT );
			rss_access 	= COConfigurationManager.getStringParameter( "rss.internal.config.access", DEFAULT_ACCESS );

		}else{

				// migrate from when the RSS feed was tied to devices

			int		port 	= COConfigurationManager.getIntParameter( "Plugin.default.device.rss.port", DEFAULT_PORT );

			rss_port 	= port;

			if ( port != DEFAULT_PORT ){

				COConfigurationManager.setParameter( "rss.internal.config.port", port );
			}

			boolean	local 	= COConfigurationManager.getBooleanParameter( "Plugin.default.device.rss.localonly", true );

			rss_access	= local?"local":"all";

			if ( !rss_access.equals( DEFAULT_ACCESS )){

				COConfigurationManager.setParameter( "rss.internal.config.access", rss_access );
			}

			COConfigurationManager.setParameter( "rss.internal.migrated", true );
		}

		defaults.put( WebPlugin.PR_ENABLE,
			Boolean.valueOf(COConfigurationManager.getBooleanParameter("Plugin.default.device.rss.enable", false)));
		defaults.put( WebPlugin.PR_DISABLABLE, Boolean.TRUE);
	    defaults.put( WebPlugin.PR_PORT, rss_port );
	    defaults.put( WebPlugin.PR_ACCESS, rss_access );
	    defaults.put( WebPlugin.PR_ROOT_DIR, root_dir.getAbsolutePath());
	    defaults.put( WebPlugin.PR_ENABLE_KEEP_ALIVE, Boolean.TRUE);
	    defaults.put( WebPlugin.PR_HIDE_RESOURCE_CONFIG, Boolean.TRUE);
	    defaults.put( WebPlugin.PR_PAIRING_SID, "rss" );

	    defaults.put( WebPlugin.PR_CONFIG_MODEL_PARAMS, new String[]{ ConfigSection.SECTION_ROOT, "rss" });
	}

	public static RSSGeneratorPlugin
	getSingleton()
	{
		return( singleton );
	}


	private static final Map<String,Provider>	providers = new TreeMap<>();

	private HyperlinkParameter		test_param;
	private BooleanParameter		enable_low_noise;

	public
	RSSGeneratorPlugin()
	{
		super( defaults );
	}

	public boolean
	isLowNoiseEnabled()
	{
		return( enable_low_noise.getValue());
	}

	public String
	getURL()
	{
		InetAddress bind_ip = getServerBindIP();

		InetAddress address;
		
		if ( bind_ip.isAnyLocalAddress()){

			address = NetworkAdmin.getSingleton().getLoopbackAddress();
			

		}else{

			address = bind_ip;
		}

		return( getProtocol().toLowerCase( Locale.US ) + "://" + UrlUtils.getURLForm( address, getPort()) + "/" );
	}

	@Override
	protected void
	setupServer()
	{
		super.setupServer();

		if ( test_param != null ){

			test_param.setEnabled( isPluginEnabled());

			test_param.setHyperlink( getURL());
		}
	}

	public static void
	registerProvider(
		String				name,
		Provider			provider )
	{
		synchronized( providers ){

			providers.put( name, provider );
		}
	}

	public static void
	unregisterProvider(
		String				name )
	{
		synchronized( providers ){

			providers.remove( name );
		}
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

			test_param = config.addHyperlinkParameter2( "rss.internal.test.url", "" );

			enable_low_noise = config.addBooleanParameter2( "rss.internal.enable.low.noise", "rss.internal.enable.low.noise", true );

			test_param.setEnabled( isPluginEnabled());
		}
	}

	@Override
	public boolean
	generateSupport(
		TrackerWebPageRequest		request,
		TrackerWebPageResponse		response )

		throws IOException
	{
		String url = request.getURL();

		if ( url.startsWith( "/" )){

			url = url.substring( 1 );
		}

		if ( url.equals( "favicon.ico" )){

			try{
				InputStream stream = getClass().getClassLoader().getResourceAsStream("com/biglybt/ui/icons/favicon.ico" );

				response.useStream( "image/x-icon", stream);

				return( true );

			}catch( Throwable e ){
			}
		}

		if ( url.length() == 0 || url.charAt(0) == '?' ){

			response.setContentType( "text/html; charset=UTF-8" );

			PrintWriter pw = new PrintWriter(new OutputStreamWriter( response.getOutputStream(), "UTF-8" ));

			pw.println( "<HTML><HEAD><TITLE>" + Constants.APP_NAME + " Feeds etc.</TITLE></HEAD><BODY>" );

			synchronized( providers ){

				for ( Map.Entry<String,Provider> entry: providers.entrySet()){

					Provider provider = entry.getValue();

					if ( !provider.isEnabled()){

						continue;
					}

					String	name = entry.getKey();

					pw.println( "<LI><A href=\"" + URLEncoder.encode( name, "UTF-8" ) + "\">" + name + "</A></LI>" );
				}
			}

			pw.println( "</BODY></HTML>" );

			pw.flush();

			return( true );

		}else{

			String provider_url = url;
			
			int	pos = provider_url.indexOf( '/' );

			if ( pos != -1 ){

				provider_url = provider_url.substring( 0, pos );
			}

			Provider provider;

			synchronized( providers ){

				provider = providers.get( provider_url );
			}

			if ( provider != null && provider.isEnabled()){

				pos = url.indexOf( '?' );
				
				if ( pos != -1 && url.substring( pos ).contains( "format=json" )){
				
					ByteArrayOutputStream	baos = new ByteArrayOutputStream(4096);
					
					response.setOutputStream( baos );
					
					if ( provider.generate(request, response)){
	
						if ( response.getContentType().startsWith( "application/xml" )){
							
							String xml = new String( baos.toByteArray(), "UTF-8" );
							
							JSONJava.JSONObject obj = new JSONJava.XML().toJSONObject( xml );
							
							try{
								JSONJava.JSONObject rss = obj.getJSONObject( "rss" );
							
								JSONJava.JSONObject channel = rss.getJSONObject( "channel" );
								
								Object items = channel.get( "item" );
								
								if ( items instanceof JSONJava.JSONArray ){
									
								}else{
									
									JSONJava.JSONArray item_array = new JSONJava.JSONArray();
									
									item_array.put( 0, items );
									
									channel.put( "item", item_array );
								}
							}catch( Throwable e ){
								
							}
							
							String json = obj.toString();
							
							byte[] jb = json.getBytes( "UTF-8" );
							
							ByteArrayOutputStream baos2 = new ByteArrayOutputStream( jb.length );
							
							baos2.write( jb );
							
							response.setOutputStream( baos2 );
							
							response.setContentType( "application/json; charset=UTF-8" );
						}
						
						return( true );
					}
				}else{
					
					if ( provider.generate(request, response)){
						
						return( true );
					}
				}
			}
		}

		response.setReplyStatus( 404 );

		return( true );
	}

	public interface
	Provider
		extends TrackerWebPageGenerator
	{
		public boolean
		isEnabled();
	}
}
