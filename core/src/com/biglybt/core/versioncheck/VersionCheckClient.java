/*
 * Created on Dec 20, 2004
 * Created by Alon Rohter
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

package com.biglybt.core.versioncheck;


import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.*;
import java.util.*;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.download.impl.DownloadManagerStateImpl;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.networkmanager.admin.NetworkAdminASN;
import com.biglybt.core.networkmanager.impl.udp.UDPNetworkManager;
import com.biglybt.core.proxy.AEProxyFactory;
import com.biglybt.core.proxy.AEProxyFactory.PluginProxy;
import com.biglybt.core.security.CryptoManagerFactory;
import com.biglybt.core.stats.transfer.OverallStats;
import com.biglybt.core.stats.transfer.StatsFactory;
import com.biglybt.core.util.*;
import com.biglybt.net.udp.uc.PRUDPPacketHandler;
import com.biglybt.net.udp.uc.PRUDPPacketHandlerFactory;
import com.biglybt.net.udp.uc.PRUDPReleasablePacketHandler;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.clientid.ClientIDGenerator;
import com.biglybt.pif.utils.DelayedTask;
import com.biglybt.pifimpl.local.clientid.ClientIDManagerImpl;
import com.biglybt.pifimpl.local.utils.UtilitiesImpl;



/**
 * Client for checking version information from a remote server.
 */
public class VersionCheckClient {
	private static final LogIDs LOGID = LogIDs.CORE;

	public static final String	REASON_UPDATE_CHECK_START		= "us";
	public static final String	REASON_UPDATE_CHECK_PERIODIC	= "up";
	public static final String	REASON_CHECK_SWT				= "sw";
	public static final String	REASON_DHT_FLAGS				= "df";
	public static final String	REASON_DHT_EXTENDED_ALLOWED		= "dx";
	public static final String	REASON_DHT_ENABLE_ALLOWED		= "de";
	public static final String	REASON_EXTERNAL_IP				= "ip";
	public static final String	REASON_RECOMMENDED_PLUGINS		= "rp";
	public static final String	REASON_SECONDARY_CHECK			= "sc";
	public static final String	REASON_PLUGIN_UPDATE			= "pu";
	public static final String	REASON_DHT_BOOTSTRAP			= "db";


	public static final String 		HTTP_SERVER_ADDRESS_V4		= Constants.VERSION_SERVER_V4;
	public static final int 		HTTP_SERVER_PORT 			= 80;

	public static final String 		TCP_SERVER_ADDRESS_V4		= Constants.VERSION_SERVER_V4;
	public static final int 		TCP_SERVER_PORT 			= 80;

	public static final String 		UDP_SERVER_ADDRESS_V4		= Constants.VERSION_SERVER_V4;
	public static final int 		UDP_SERVER_PORT 			= 2080;

	public static final String		HTTP_SERVER_ADDRESS_V6 		= Constants.VERSION_SERVER_V6;
	public static final String		TCP_SERVER_ADDRESS_V6 		= Constants.VERSION_SERVER_V6;
	public static final String		UDP_SERVER_ADDRESS_V6 		= Constants.VERSION_SERVER_V6;


	private static final long		CACHE_PERIOD	= 5*60*1000;
	private static boolean secondary_check_done;

	private final CopyOnWriteList<VersionCheckClientListener> listeners = new CopyOnWriteList<>(5);

	static{
		VersionCheckClientUDPCodecs.registerCodecs();
	}

	private static final int	AT_V4		= 1;
	private static final int	AT_V6		= 2;
	private static final int	AT_EITHER	= 3;

	private static VersionCheckClient instance;

	/**
	 * Get the singleton instance of the version check client.
	 * @return version check client
	 */
	public static synchronized VersionCheckClient
	getSingleton()
	{
		if ( instance == null ){

			instance = new VersionCheckClient();
		}

		return( instance );
	}

	private boolean	enable_v6;
	private boolean	prefer_v6;

	private Map last_check_data_v4 = null;
	private Map last_check_data_v6 = null;

	private volatile Throwable last_error_v4 = null;
	private volatile Throwable last_error_v6 = null;
	
	private final AEMonitor check_mon = new AEMonitor( "versioncheckclient" );

	private long last_check_time_v4 = 0;
	private long last_check_time_v6 = 0;

	private long last_feature_flag_cache;
	private long last_feature_flag_cache_time;

	private volatile boolean check_has_worked;

	private
	VersionCheckClient()
	{
		COConfigurationManager.addAndFireParameterListeners(
				new String[]{ "IPV6 Prefer Addresses", "IPV6 Enable Support" },
				new ParameterListener()
				{
					@Override
					public void
					parameterChanged(
							String 	name )
					{
						enable_v6	= COConfigurationManager.getBooleanParameter( "IPV6 Enable Support" );
						prefer_v6 	= COConfigurationManager.getBooleanParameter( "IPV6 Prefer Addresses" );
					}
				});
	}

	public void
	initialise()
	{
		DelayedTask delayed_task =
			UtilitiesImpl.addDelayedTask(
					"VersionCheck",
					new Runnable()
					{
						@Override
						public void
						run()
						{
							final AESemaphore sem = new AESemaphore( "VCC:init" );

							new AEThread2( "VCC:init", true )
							{
								@Override
								public void
								run()
								{
									try{
										getVersionCheckInfo( REASON_UPDATE_CHECK_START );

									}finally{

										sem.release();
									}
								}
							}.start();

							if ( !sem.reserve( 5000 )){

								Debug.out( "Timeout waiting for version check to complete" );
							}
						}
					});

		delayed_task.queue();
	}






	/**
	 * Get the version check reply info.
	 * @return reply data, possibly cached, if the server was already checked within the last minute
	 */

	public Map
	getVersionCheckInfo(
			String 	reason )
	{
		return( getVersionCheckInfo( reason, AT_EITHER ));
	}

	public Map
	getVersionCheckInfo(
			String 	reason,
			int		address_type )
	{
		if ( address_type == AT_V4 ){

			return( getVersionCheckInfoSupport( reason, false, false, false ));

		}else if ( address_type == AT_V6 ){

			return( getVersionCheckInfoSupport( reason, false, false, true ));

		}else{

			Map	reply = getVersionCheckInfoSupport( reason, false, false, prefer_v6 );

			if ( reply == null || reply.size() == 0 ){

				reply =  getVersionCheckInfoSupport( reason, false, false, !prefer_v6 );
			}

			return( reply );
		}
	}


	protected Map
	getVersionCheckInfoSupport(
		String 	reason,
		boolean only_if_cached,
		boolean force,
		boolean	v6 )
	{
		List<VersionCheckClientListener>	listeners_clone = listeners.getList();
		
		for ( VersionCheckClientListener l : listeners_clone){
			
			try{
				l.versionCheckStarted(reason);
				
			}catch( Throwable e ){
				
				Debug.out( e );
			}
		}

		boolean changed = false;
		
		try{
			if ( v6 ){
	
				if ( enable_v6 ){
	
					try {  check_mon.enter();
	
					long time_diff = SystemTime.getCurrentTime() - last_check_time_v6;
	
					force = force || time_diff > CACHE_PERIOD || time_diff < 0;
	
					if( last_check_data_v6 == null || last_check_data_v6.size() == 0 || force ) {
						// if we've never checked before then we go ahead even if the "only_if_cached"
						// flag is set as its had not chance of being cached yet!
						if ( only_if_cached && last_check_data_v6 != null ){
							return( new HashMap() );
						}
						try {
							last_error_v6		= null;
							
							last_check_data_v6 = performVersionCheck( constructVersionCheckMessage( reason ), true, true, true );
	
							if ( last_check_data_v6 != null && last_check_data_v6.size() > 0 ){
	
								COConfigurationManager.setParameter( "versioncheck.cache.v6", last_check_data_v6 );
								
								changed = true;
							}
						}
						catch(SocketException t) {
							// internet is broken
							// Debug.out(t.getClass().getName() + ": " + t.getMessage());
							
							last_error_v6 = t;
						}
						catch(UnknownHostException t) {
							// dns is broken
							// Debug.out(t.getClass().getName() + ": " + t.getMessage());
							
							last_error_v6 = t;
							
						}catch( Throwable t ) {
							Debug.out(t);
							last_check_data_v6 = new HashMap();
							
							last_error_v6 = t;
						}
					}
					else {
						Logger.log(new LogEvent(LOGID, "VersionCheckClient is using "
								+ "cached version check info. Using " + last_check_data_v6.size()
								+ " reply keys."));
					}
					}
					finally {  check_mon.exit();  }
				}
	
				if( last_check_data_v6 == null )  last_check_data_v6 = new HashMap();
	
				return last_check_data_v6;
	
			}else{
	
				try {  check_mon.enter();
	
				long time_diff = SystemTime.getCurrentTime() - last_check_time_v4;
	
				force = force || time_diff > CACHE_PERIOD || time_diff < 0;
	
				if( last_check_data_v4 == null || last_check_data_v4.size() == 0 || force ) {
					// if we've never checked before then we go ahead even if the "only_if_cached"
					// flag is set as its had not chance of being cached yet!
					if ( only_if_cached && last_check_data_v4 != null ){
						return( new HashMap() );
					}
					try {
						last_error_v4		= null;
						
						last_check_data_v4 = performVersionCheck( constructVersionCheckMessage( reason ), true, true, false );
	
						if ( last_check_data_v4 != null && last_check_data_v4.size() > 0 ){
	
							COConfigurationManager.setParameter( "versioncheck.cache.v4", last_check_data_v4 );
							
							changed = true;
						}
	
						// clear down any plugin-specific data that has successfully been sent to the version server
	
						try{
							if ( CoreFactory.isCoreAvailable() && CoreFactory.getSingleton().getPluginManager().isInitialized()){
	
								//installed plugin IDs
								PluginInterface[] plugins = CoreFactory.getSingleton().getPluginManager().getPluginInterfaces();
	
								for (int i=0;i<plugins.length;i++){
	
									PluginInterface		plugin = plugins[i];
	
									Map	data = plugin.getPluginconfig().getPluginMapParameter( "plugin.versionserver.data", null );
	
									if ( data != null ){
	
										plugin.getPluginconfig().setPluginMapParameter( "plugin.versionserver.data", new HashMap());
									}
								}
							}
						}catch( Throwable e ){
						}
					}
					catch( UnknownHostException t ) {
						// no internet
						Debug.outNoStack("VersionCheckClient - " + t.getClass().getName() + ": " + t.getMessage());
						
						last_error_v4 = t;
					}
					catch (IOException t) {
						// General connection problem.
						Debug.outNoStack("VersionCheckClient - " + t.getClass().getName() + ": " + t.getMessage());
						
						last_error_v4 = t;
						
					}catch( Throwable t ) {
						Debug.out(t);
						last_check_data_v4 = new HashMap();
						last_error_v4 = t;
					}
				}
				else {
					if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID, "VersionCheckClient is using "
								+ "cached version check info. Using " + last_check_data_v4.size()
								+ " reply keys."));
				}
				}
				finally {  check_mon.exit();  }
	
				if( last_check_data_v4 == null )  last_check_data_v4 = new HashMap();
	
				last_feature_flag_cache_time = 0;
	
				return last_check_data_v4;
			}
		}finally{
			
			if ( changed ){
				
				check_has_worked = true;
			}
			
			for (VersionCheckClientListener l : listeners_clone) {
				try{
					l.versionCheckCompleted(reason, changed);
					
				}catch( Throwable e ){
					
					Debug.out( e );
				}
			}
		}
	}

	public Map
	getMostRecentVersionCheckData()
	{
		// currently we maintain v4 much more accurately than v6

		if ( last_check_data_v4 != null ){

			return( last_check_data_v4 );
		}

		Map	res = COConfigurationManager.getMapParameter( "versioncheck.cache.v4", null );

		if ( res != null ){

			return( res );
		}

		if ( last_check_data_v6 != null ){

			return( last_check_data_v6 );
		}

		res = COConfigurationManager.getMapParameter( "versioncheck.cache.v6", null );

		return( res );
	}

	private boolean
	isVersionCheckDataValid(
		int		address_type )
	{
		boolean v6_ok = last_check_data_v6 != null && last_check_data_v6.size() > 0;
		boolean v4_ok = last_check_data_v4 != null && last_check_data_v4.size() > 0;

		if ( address_type == AT_V4 ){

			return( v4_ok );

		}else if ( address_type == AT_V6 ){

			return( v6_ok );

		}else{

			return( v4_ok | v6_ok );
		}
	}

	public long
	getCacheTime(
			boolean v6 )
	{
		return( v6?last_check_time_v6:last_check_time_v4);
	}

	public void
	clearCache()
	{
		last_check_time_v6	= 0;
		last_check_time_v4	= 0;
	}

	public Throwable
	getError()
	{
		if ( !check_has_worked ){
			
			if ( last_error_v4 != null ){
				
				return( last_error_v4 );
				
			}else{
				
				return( last_error_v6 );
			}
		}
		
		return( null );
	}
	
	public long
	getFeatureFlags()
	{
		long	now = SystemTime.getCurrentTime();

		if ( now > last_feature_flag_cache_time && now - last_feature_flag_cache_time < 60000 ){

			return( last_feature_flag_cache );
		}

		Map m = getMostRecentVersionCheckData();

		long	result;

		if ( m == null ){

			result = 0;

		}else{

			byte[]	b_feat_flags = (byte[])m.get( "feat_flags" );

			if ( b_feat_flags != null ){

				try{

					result = Long.parseLong(new String((byte[])b_feat_flags));

				}catch( Throwable e ){

					result	= 0;
				}
			}else{

				result = 0;
			}
		}

		last_feature_flag_cache 		= result;
		last_feature_flag_cache_time	= now;

		return( result );
	}

	public Set<String>
	getDisabledPluginIDs()
	{
		Set<String>	result = new HashSet<>();

		Map m = getMostRecentVersionCheckData();

		if ( m != null ){

			byte[]	x = (byte[])m.get( "disabled_pids" );

			if ( x != null ){

				String str = new String( x );

				String latest = COConfigurationManager.getStringParameter( "vc.disabled_pids.latest", "" );

				if ( !str.equals( latest )){

					byte[]	sig = (byte[])m.get( "disabled_pids_sig" );

					if ( sig == null ){

						Debug.out( "disabled plugins sig missing" );

						return( result );
					}

					try{
						AEVerifier.verifyData( str, sig );

						COConfigurationManager.setParameter( "vc.disabled_pids.latest", str );

					}catch( Throwable e ){

						return( result );
					}
				}

				String[] bits = str.split( "," );

				for ( String b: bits ){

					b = b.trim();

					if ( b.length() > 0 ){

						result.add( b );
					}
				}
			}
		}

		return( result );
	}

	public Set<String>
	getAutoInstallPluginIDs()
	{
		Set<String>	result = new HashSet<>();

		Map m = getMostRecentVersionCheckData();

		if ( m != null ){

			byte[]	x = (byte[])m.get( "autoinstall_pids" );

			if ( x != null ){

				String str = new String( x );

				String latest = COConfigurationManager.getStringParameter( "vc.autoinstall_pids.latest", "" );

				if ( !str.equals( latest )){

					byte[]	sig = (byte[])m.get( "autoinstall_pids_sig" );

					if ( sig == null ){

						Debug.out( "autoinstall plugins sig missing" );

						return( result );
					}

					try{
						AEVerifier.verifyData( str, sig );

						COConfigurationManager.setParameter( "vc.autoinstall_pids.latest", str );

					}catch( Throwable e ){

						return( result );
					}
				}

				String[] bits = str.split( "," );

				for ( String b: bits ){

					b = b.trim();

					if ( b.length() > 0 ){

						result.add( b );
					}
				}
			}
		}

		return( result );
	}

	/**
	 * Get the ip address seen by the version check server.
	 * NOTE: This information may be cached, see getVersionCheckInfo().
	 * @return external ip address, or empty string if no address information found
	 */

	public String
	getExternalIpAddress(
			boolean	only_if_cached,
			boolean	v6 )
	{
		return( getExternalIpAddress( only_if_cached, v6, false ));
	}

	public String
	getExternalIpAddress(
		boolean		only_if_cached,
		boolean		v6,
		boolean		force )
	{
		Map reply = getVersionCheckInfoSupport( REASON_EXTERNAL_IP, only_if_cached, force, v6 );

		byte[] address = (byte[])reply.get( "source_ip_address" );
		if( address != null ) {
			return new String( address );
		}

		return( null );
	}


	/**
	 * Is the DHT plugin allowed to be enabled.
	 * @return true if DHT can be enabled, false if it should not be enabled
	 */
	public boolean DHTEnableAllowed() {
		Map reply = getVersionCheckInfo( REASON_DHT_ENABLE_ALLOWED, AT_EITHER );

		boolean	res = false;

		byte[] value = (byte[])reply.get( "enable_dht" );

		if( value != null ) {

			res = new String( value ).equalsIgnoreCase( "true" );
		}

		// we take the view that if the version check failed then we go ahead
		// and enable the DHT (i.e. we're being optimistic)

		if ( !res ){
			res = !isVersionCheckDataValid( AT_EITHER );
		}

		return res;
	}


	/**
	 * Is the DHT allowed to be used by external plugins.
	 * @return true if extended DHT use is allowed, false if not allowed
	 */
	public boolean
	DHTExtendedUseAllowed()
	{
		Map reply = getVersionCheckInfo( REASON_DHT_EXTENDED_ALLOWED, AT_EITHER );

		boolean	res = false;

		byte[] value = (byte[])reply.get( "enable_dht_extended_use" );
		if( value != null ) {
			res = new String( value ).equalsIgnoreCase( "true" );
		}

		// be generous and enable extended use if check failed

		if ( !res ){
			res = !isVersionCheckDataValid( AT_EITHER );
		}

		return res;
	}

	public byte
	getDHTFlags()
	{
		Map map = getMostRecentVersionCheckData();

		if ( map != null ){

			byte[] b_flags = (byte[])map.get( "dht_flags" );

			if ( b_flags != null ){

				return( new Integer(new String( b_flags )).byteValue());
			}
		}

		return( (byte)0xff );
	}

	public String[]
	getRecommendedPlugins()
	{
		Map reply = getVersionCheckInfo( REASON_RECOMMENDED_PLUGINS, AT_EITHER );

		List	l = (List)reply.get( "recommended_plugins" );

		if ( l == null ){

			return( new String[0] );
		}

		String[]	res = new String[l.size()];

		for (int i=0;i<l.size();i++){

			res[i] = new String((byte[])l.get(i));
		}

		return( res );
	}

	public List<InetSocketAddress>
	getDHTBootstrap(
		boolean 	ipv4 )
	{
		List<InetSocketAddress>	result = new ArrayList<>();

		try{
			Map reply = getVersionCheckInfo( REASON_DHT_BOOTSTRAP, ipv4?AT_V4:AT_V6 );

			List<Map>	l = (List<Map>)reply.get( "dht_boot" );

			if ( l != null ){

				for( Map m: l ){

					byte[]	address = (byte[])m.get("a");
					int		port	= ((Long)m.get("p")).intValue();

					if (	ipv4 && address.length == 4 ||
							!ipv4 && address.length == 16 ){

						InetAddress iaddress = InetAddress.getByAddress( address );

						if ( !(	iaddress.isLoopbackAddress() ||
								iaddress.isLinkLocalAddress() ||
								iaddress.isSiteLocalAddress())){

							result.add( new InetSocketAddress( iaddress, port ));
						}
					}
				}
			}
		}catch( Throwable e ){

			Debug.out( e );
		}

		return( result );
	}

	public Map<String,Object>
	getCountryInfo()
	{
		Map reply = getVersionCheckInfo( REASON_EXTERNAL_IP, AT_EITHER );

		Map<String,Object> info = (Map<String,Object>)reply.get( "source_info" );

		if ( info == null ){

			return(new HashMap<>());

		}else{

			return( BDecoder.decodeStrings( info ));
		}
	}

	/**
	 * Perform the actual version check by connecting to the version server.
	 * @param data_to_send version message
	 * @return version reply
	 * @throws Exception if the server check connection fails
	 */
	private Map
	performVersionCheck(
		Map 	data_to_send,
		boolean	use_az_message,
		boolean	use_http,
		boolean	v6 )

		throws Exception
	{
		Exception 	error 	= null;
		Map			reply	= null;


		if ( use_http ){

			try{
				reply = executeHTTP( data_to_send, v6 );

				reply.put( "protocol_used", "HTTP" );

				error = null;
			}
			catch (IOException e) {
				error = e;
			}
			catch (Exception e){
				Debug.printStackTrace(e);
				error = e;

			}
		}

		if ( error != null ){

			throw( error );
		}

		if (Logger.isEnabled())
			Logger.log(new LogEvent(LOGID, "VersionCheckClient server "
					+ "version check successful. Received " + (reply==null?"null":reply.size())
					+ " reply keys."));

		if ( v6 ){

			last_check_time_v6 = SystemTime.getCurrentTime();

		}else{

			last_check_time_v4 = SystemTime.getCurrentTime();
		}

		return reply;
	}

	private Map
	executeHTTP(
		Map		data_to_send,
		boolean	v6 )

		throws Exception
	{
		if ( v6 && !enable_v6 ){

			throw( new Exception( "IPv6 is disabled" ));
		}

		String	host = getHost(v6, HTTP_SERVER_ADDRESS_V6, HTTP_SERVER_ADDRESS_V4 );

		if (Logger.isEnabled())
			Logger.log(new LogEvent(LOGID, "VersionCheckClient retrieving "
					+ "version information from " + host + ":" + HTTP_SERVER_PORT + " via HTTP" ));

		String	url_str = "http://" + (v6?UrlUtils.convertIPV6Host(host):host) + (HTTP_SERVER_PORT==80?"":(":" + HTTP_SERVER_PORT)) + "/version?";

		url_str += URLEncoder.encode( new String( BEncoder.encode( data_to_send ), "ISO-8859-1" ), "ISO-8859-1" );

		URL	url = new URL( url_str );

		boolean anon = COConfigurationManager.getBooleanParameter( "update.anonymous");
		
		try{
			if ( anon ){

				throw( new Exception( "Direct HTTP disabled for anonymous updates" ));
			}

			Properties	http_properties = new Properties();

	 		http_properties.put( ClientIDGenerator.PR_URL, url );

	 		try{
	 			ClientIDManagerImpl cman = ClientIDManagerImpl.getSingleton();

	 			if ( cman != null && cman.getGenerator() != null ){

	 				cman.generateHTTPProperties( null, http_properties );
	 			}

	 		}catch( Throwable e ){

	 			Debug.out( e );

	 			throw( new IOException( e.getMessage()));
	 		}

	 		url = (URL)http_properties.get( ClientIDGenerator.PR_URL );

			HttpURLConnection	url_connection = (HttpURLConnection)url.openConnection();

			url_connection.setConnectTimeout( 10*1000 );
			url_connection.setReadTimeout( 10*1000 );

			url_connection.connect();

			try{
				InputStream	is = url_connection.getInputStream();

				Map	reply = BDecoder.decode( new BufferedInputStream( is ));

				preProcessReply( reply, v6 );

				return( reply );

			}finally{

				url_connection.disconnect();
			}
		}catch( Exception e ){

			if ( !v6 ){

				PluginProxy proxy = AEProxyFactory.getPluginProxy( Constants.APP_NAME + " version check", url );

				if ( proxy != null ){

					boolean	worked = false;

					try{
						HttpURLConnection url_connection = (HttpURLConnection)proxy.getURL().openConnection( proxy.getProxy());

						url_connection.setConnectTimeout( 30*1000 );
						url_connection.setReadTimeout( 30*1000 );

						url_connection.connect();

						try{
							InputStream	is = url_connection.getInputStream();

							Map	reply = BDecoder.decode( new BufferedInputStream( is ));

							if ( reply != null ){
								
									// remove bogus address
								
								reply.remove( "source_ip_address" );
							}
								
							preProcessReply( reply, v6 );

							worked = true;

							return( reply );

						}finally{

							url_connection.disconnect();
						}
					}finally{

						proxy.setOK( worked );
					}
				}
			}

			if ( anon ){
				
				throw( new Exception( "Tor helper plugin failed or not available" ));
			}
			
			throw( e );
		}
	}

	public String
	getHTTPGetString(
		boolean	for_proxy,
		boolean	v6 )
	{
		return( getHTTPGetString( new HashMap(), for_proxy, v6 ));
	}

	private String
	getHTTPGetString(
		Map		content,
		boolean	for_proxy,
		boolean	v6 )
	{
		String	host = getHost( v6, HTTP_SERVER_ADDRESS_V6, HTTP_SERVER_ADDRESS_V4 );

		String	get_str = "GET " + (for_proxy?("http://" + (v6?UrlUtils.convertIPV6Host( host ):host ) + ":" + HTTP_SERVER_PORT ):"") +"/version?";

		try{
			get_str += URLEncoder.encode( new String( BEncoder.encode( content ), "ISO-8859-1" ), "ISO-8859-1" );

		}catch( Throwable e ){
		}

		get_str +=" HTTP/1.1" + "\015\012" + "\015\012";

		return( get_str );
	}

	private Map
	executeTCP(
		Map				data_to_send,
		InetAddress		bind_ip,
		int				bind_port,
		boolean			v6 )

		throws Exception
	{
		if ( COConfigurationManager.getBooleanParameter( "update.anonymous")){

			throw( new Exception( "TCP disabled for anonymous updates" ));
		}

		if ( v6 && !enable_v6 ){

			throw( new Exception( "IPv6 is disabled" ));
		}

		String	host = getHost(v6, TCP_SERVER_ADDRESS_V6, TCP_SERVER_ADDRESS_V4 );

		if (Logger.isEnabled())
			Logger.log(new LogEvent(LOGID, "VersionCheckClient retrieving "
					+ "version information from " + host + ":" + TCP_SERVER_PORT + " via TCP" ));

		String	get_str = getHTTPGetString( data_to_send, false, v6 );

		Socket	socket = null;

		try{
			socket = new Socket();

			if ( bind_ip != null ){

				socket.bind( new InetSocketAddress( bind_ip, bind_port ));

			}else if ( bind_port != 0 ){

				socket.bind( new InetSocketAddress( bind_port ));
			}

			socket.setSoTimeout( 10000 );

			socket.connect( new InetSocketAddress( host, TCP_SERVER_PORT ), 10000 );

			OutputStream	os = socket.getOutputStream();

			os.write( get_str.getBytes( "ISO-8859-1" ));

			os.flush();

			InputStream	is = socket.getInputStream();

			byte[]	buffer = new byte[1];

			String	header = "";

			int content_length = -1;

			while( true ){

				int	len = is.read( buffer );

				if ( len <= 0 ){

					break;
				}

				header += (char)buffer[0];

				if ( header.endsWith( "\r\n\r\n" )){

					header = header.toLowerCase( MessageText.LOCALE_ENGLISH );

					int	pos = header.indexOf( "content-length:" );

					if ( pos == -1 ){

						throw( new IOException( "content length missing" ));
					}

					header = header.substring( pos+15 );

					pos = header.indexOf( '\r' );

					header = header.substring(0,pos).trim();

					content_length = Integer.parseInt( header );

					if ( content_length > 10000 ){

						throw( new IOException( "content length too large" ));
					}

					break;
				}

				if ( header.length() > 2048 ){

					throw( new IOException( "header too large" ));
				}
			}

			ByteArrayOutputStream	baos = new ByteArrayOutputStream( content_length );

			buffer = new byte[content_length];

			while( content_length > 0 ){

				int	len = is.read( buffer );

				if ( len <= 0 ){

					break;
				}

				baos.write( buffer, 0, len );

				content_length -= len;
			}

			if ( content_length != 0 ){

				throw( new IOException( "error reading reply" ));
			}

			byte[]	reply_bytes = baos.toByteArray();

			Map reply = BDecoder.decode( new BufferedInputStream( new ByteArrayInputStream( reply_bytes )));

			preProcessReply( reply, v6 );

			return( reply );

		}finally{

			if ( socket != null ){

				try{
					socket.close();

				}catch( Throwable e ){

				}
			}
		}
	}

	private Map
	executeUDP(
		Map				data_to_send,
		InetAddress		bind_ip,
		int				bind_port,
		boolean			v6 )

		throws Exception
	{
		if ( COConfigurationManager.getBooleanParameter( "update.anonymous")){

			throw( new Exception( "UDP disabled for anonymous updates" ));
		}

		if ( v6 && !enable_v6 ){

			throw( new Exception( "IPv6 is disabled" ));
		}

		String	host = getHost( v6, UDP_SERVER_ADDRESS_V6, UDP_SERVER_ADDRESS_V4 );

		PRUDPReleasablePacketHandler handler = PRUDPPacketHandlerFactory.getReleasableHandler( bind_port );

		PRUDPPacketHandler	packet_handler = handler.getHandler();

		long timeout = 5;

		Random random = new Random();

		try{
			Exception	last_error = null;

			packet_handler.setExplicitBindAddress( bind_ip, false );

			for (int i=0;i<3;i++){

				try{
					// connection ids for requests must always have their msb set...
					// apart from the original darn udp tracker spec....

					long connection_id = 0x8000000000000000L | random.nextLong();

					VersionCheckClientUDPRequest	request_packet = new VersionCheckClientUDPRequest( connection_id );

					request_packet.setPayload( data_to_send );

					VersionCheckClientUDPReply reply_packet = (VersionCheckClientUDPReply)packet_handler.sendAndReceive( null, request_packet, new InetSocketAddress( host, UDP_SERVER_PORT ), timeout );

					Map	reply = reply_packet.getPayload();

					preProcessReply( reply, v6 );

					return( reply );

				}catch( Exception e){

					last_error	= e;

					timeout = timeout * 2;
				}
			}

			if ( last_error != null ){

				throw( last_error );
			}

			throw( new Exception( "Timeout" ));

		}finally{

			packet_handler.setExplicitBindAddress( null, false );

			handler.release();
		}
	}

	protected void
	preProcessReply(
		Map					reply,
		final boolean		v6 )
	{
		NetworkAdmin admin = NetworkAdmin.getSingleton();

		try{
			byte[] address = (byte[])reply.get( "source_ip_address" );

			if ( address != null ){

				InetAddress my_ip = InetAddress.getByName( new String( address ));

				NetworkAdminASN old_asn = admin.getCurrentASN();

				NetworkAdminASN new_asn = admin.lookupCurrentASN( my_ip );

				if ( !new_asn.sameAs( old_asn )){

					// kick off a secondary version check to communicate the new information

					if ( !secondary_check_done ){

						secondary_check_done	= true;

						new AEThread( "Secondary version check", true )
						{
							@Override
							public void
							runSupport()
							{
								getVersionCheckInfoSupport( REASON_SECONDARY_CHECK, false, true, v6 );
							}
						}.start();
					}
				}
			}
		}catch( Throwable e ){

			if ( !Debug.containsException( e, UnknownHostException.class )){

				Debug.printStackTrace(e);
			}
		}

		Long	as_advice = (Long)reply.get( "as_advice" );

		if ( as_advice != null ){

			NetworkAdminASN current_asn = admin.getCurrentASN();

			String	asn = current_asn.getASName();

			if ( asn != null ){

				long	advice = as_advice.longValue();

				if ( advice != 0 ){

					// require crypto

					String	done_asn = COConfigurationManager.getStringParameter( "ASN Advice Followed", "" );

					if ( !done_asn.equals( asn )){

						COConfigurationManager.setParameter( "ASN Advice Followed", asn );

						boolean	change 	= advice == 1 || advice == 2;
						boolean	alert 	= advice == 1 || advice == 3;

						if ( !COConfigurationManager.getBooleanParameter( "network.transport.encrypted.require" )){

							if ( change ){

								COConfigurationManager.setParameter( "network.transport.encrypted.require", true );
							}

							if ( alert ){

								String	msg =
									MessageText.getString(
											"crypto.alert.as.warning",
											new String[]{ asn });

								Logger.log( new LogAlert( false, LogAlert.AT_WARNING, msg ));
							}
						}
					}
				}
			}
		}

		// set ui.toolbar.uiswitcher based on instructions from tracker
		// Really shouldn't be in VersionCheck client, but instead have some
		// listener and have the code elsewhere.  Simply calling
		//getVersionCheckInfo from "code elsewhere" (to get the cached result)
		//caused a deadlock at startup.
		Long lEnabledUISwitcher = (Long) reply.get("ui.toolbar.uiswitcher");
		if (lEnabledUISwitcher != null) {
			COConfigurationManager.setBooleanDefault("ui.toolbar.uiswitcher",
					lEnabledUISwitcher.longValue() == 1);
		}
	}

	public InetAddress
	getExternalIpAddressHTTP(
		boolean		v6 )

		throws Exception
	{
		Map reply = executeHTTP( new HashMap(), v6 );

		byte[] address = (byte[])reply.get( "source_ip_address" );

		return( address==null?null:InetAddress.getByName( new String( address )));
	}

	public InetAddress
	getExternalIpAddressTCP(
		InetAddress	 	bind_ip,
		int				bind_port,
		boolean			v6 )

		throws Exception
	{
		Map reply = executeTCP( new HashMap(), bind_ip, bind_port, v6 );

		byte[] address = (byte[])reply.get( "source_ip_address" );

		return( address==null?null:InetAddress.getByName( new String( address )));
	}

	public InetAddress
	getExternalIpAddressUDP(
		InetAddress	 	bind_ip,
		int				bind_port,
		boolean			v6 )

		throws Exception
	{
		Map reply = executeUDP( new HashMap(), bind_ip, bind_port, v6 );

		byte[] address = (byte[])reply.get( "source_ip_address" );

		return( address==null?null:InetAddress.getByName( new String( address )));
	}

	protected String
	getHost(
		boolean		v6,
		String		v6_address,
		String		v4_address )
	{
		if ( v6 ){

			v6_address = getTestAddress( true, v6_address );

			try{
				return( InetAddress.getByName( v6_address ).getHostAddress());

			}catch( UnknownHostException e ){

				DNSUtils.DNSUtilsIntf dns_utils = DNSUtils.getSingleton();

				if ( dns_utils != null ){

					try{
						return( dns_utils.getIPV6ByName( v6_address ).getHostAddress());

					}catch( UnknownHostException f ){
					}
				}

				return( v6_address );
			}
		}else{

			v4_address = getTestAddress( false, v4_address );

			return( v4_address );
		}
	}

	private String
	getTestAddress(
		boolean	v6,
		String	address )
	{
		return( COConfigurationManager.getStringParameter( "versioncheck.test.address." + v6, address ));
	}

	/**
	 * Construct the default version check message.
	 * @return message to send
	 */
	public static Map<String,Object>
	constructVersionCheckMessage(
		String reason )
	{

		//only send if anonymous-check flag is not set

		boolean send_info = COConfigurationManager.getBooleanParameter( "Send Version Info" );

		Map<String,Object> message = new HashMap<>();

		//always send
		message.put( "appid",   SystemProperties.getApplicationIdentifier());
		message.put( "appname", SystemProperties.getApplicationName());
		message.put( "version", Constants.BIGLYBT_VERSION );
		message.put( "first_version", COConfigurationManager.getStringParameter( "First Recorded Version", "" ));

		String	sub_ver = Constants.SUBVERSION;

		if ( sub_ver.length() > 0 ){
			message.put( "subver", sub_ver );
		}

		if ( COConfigurationManager.getBooleanParameter( "Beta Programme Enabled" )){

			message.put( "beta_prog", "true" );
		}
		
		if ( System.getProperty(SystemProperties.SYSPROP_PORTABLE_ENABLE, "false" ).equalsIgnoreCase( "true" )){

			message.put( "portable", "true" );
		}
		
		message.put( "ui",      COConfigurationManager.getStringParameter( "ui", "unknown" ) );
		message.put( "os",      Constants.OSName );
		message.put( "os_version", Constants.OSVersion );
		message.put( "os_arch", Constants.OSArch );   //see http://lopica.sourceforge.net/os.html
		message.put( "os_arch_dm", System.getProperty( "sun.arch.data.model" ) );  // might be needed to openjdk on osx

		boolean using_phe = COConfigurationManager.getBooleanParameter( "network.transport.encrypted.require" );
		message.put( "using_phe", using_phe ? new Long(1) : new Long(0) );

		message.put( "imode", COConfigurationManager.getStringParameter( "installer.mode", "" ));

		//swt stuff
		try {
			Class c = Class.forName( "com.biglybt.ui.swt.Utils" );

			
			String swt_platform = (String)c.getMethod( "getSWTPlatform", new Class[]{} ).invoke( null, new Object[]{} );
			message.put( "swt_platform", swt_platform );

			Integer swt_version = (Integer)c.getMethod( "getSWTVersion", new Class[]{} ).invoke( null, new Object[]{} );
			message.put( "swt_version", new Long( swt_version.longValue() ) );
			
			Integer swt_revision = (Integer)c.getMethod( "getSWTRevision", new Class[]{} ).invoke( null, new Object[]{} );
			message.put( "swt_revision", new Long( swt_revision.longValue() ) );
		}
		
		catch( ClassNotFoundException e ) {  /* ignore */ }
		catch( NoClassDefFoundError er ) {  /* ignore */ }
		catch( InvocationTargetException err ) {  /* ignore */ }
		catch( Throwable t ) {  t.printStackTrace();  }


		int last_send_time = COConfigurationManager.getIntParameter( "Send Version Info Last Time", -1 );
		int current_send_time = (int)(SystemTime.getCurrentTime()/1000);
		COConfigurationManager.setParameter( "Send Version Info Last Time", current_send_time );


		String id = COConfigurationManager.getStringParameter( "ID", null );

		if( id != null && send_info ) {
			message.put( "id", id );

			try{
				byte[] id2 = CryptoManagerFactory.getSingleton().getSecureID();

				message.put( "id2", id2 );

			}catch( Throwable e ){
			}

			if ( last_send_time != -1 && last_send_time < current_send_time ){
				// time since last
				message.put( "tsl", new Long(current_send_time-last_send_time));
			}

			message.put( "reason", reason );

			String  java_version = Constants.JAVA_VERSION;
			if ( java_version == null ){  java_version = "unknown";  }
			message.put( "java", java_version );


			String  java_vendor = System.getProperty( "java.vm.vendor" );
			if ( java_vendor == null ){   java_vendor = "unknown";  }
			message.put( "javavendor", java_vendor );

			int  api_level = Constants.API_LEVEL;
			if ( api_level > 0 ){
				message.put( "api_level", api_level );
			}

			long  max_mem = Runtime.getRuntime().maxMemory()/(1024*1024);
			message.put( "javamx", new Long( max_mem ) );

			String java_rt_name = System.getProperty("java.runtime.name");
			if (java_rt_name != null) {
				message.put( "java_rt_name", java_rt_name);
			}

			String java_rt_version = System.getProperty("java.runtime.version");
			if (java_rt_version != null) {
				message.put( "java_rt_version", java_rt_version);
			}

			OverallStats	stats = StatsFactory.getStats();

			if ( stats != null ){

				//long total_bytes_downloaded 	= stats.getDownloadedBytes();
				//long total_bytes_uploaded		= stats.getUploadedBytes();
				long total_uptime 			= stats.getTotalUpTime();

				//removed due to complaints about anonymous stats collection
				//message.put( "total_bytes_downloaded", new Long( total_bytes_downloaded ) );
				//message.put( "total_bytes_uploaded", new Long( total_bytes_uploaded ) );
				message.put( "total_uptime", new Long( total_uptime ) );
				//message.put( "dlstats", stats.getDownloadStats());
			}

			try{
				int	port = UDPNetworkManager.getSingleton().getUDPNonDataListeningPortNumber();

				message.put( "dht", port );

			}catch( Throwable e ){

				Debug.out( e );
			}

			try{
				NetworkAdminASN current_asn = NetworkAdmin.getSingleton().getCurrentASN();

				message.put( "ip_as", current_asn.getAS());

				String	asn = current_asn.getASName();

				if ( asn.length() > 64 ){

					asn = asn.substring( 0, 64 );
				}

				message.put( "ip_asn", asn );

			}catch( Throwable e ){

				Debug.out( e );
			}

			// send locale, so we can determine which languages need attention
			message.put("locale", Locale.getDefault().toString());
			String originalLocale = System.getProperty("user.language") + "_"
			+ System.getProperty("user.country");
			String variant = System.getProperty("user.variant");
			if (variant != null && variant.length() > 0) {
				originalLocale += "_" + variant;
			}
			message.put("orig_locale", originalLocale);

			// We may want to reply differently if the user is in Beginner mode vs Advanced
			message.put("user_mode",
					COConfigurationManager.getIntParameter("User Mode", -1));

			try{
				if ( CoreFactory.isCoreAvailable() &&
						CoreFactory.getSingleton().getPluginManager().isInitialized()){

					//installed plugin IDs
					PluginInterface[] plugins = CoreFactory.getSingleton().getPluginManager().getPluginInterfaces();

					List pids = new ArrayList();

					List vs_data = new ArrayList();

					for (int i=0;i<plugins.length;i++){

						PluginInterface		plugin = plugins[i];

						String  pid = plugin.getPluginID();

						String	info = plugin.getPluginconfig().getPluginStringParameter( "plugin.info" );

						// filter out built-in and core ones
						if ( 	( info != null && info.length() > 0 ) ||
								(	!pid.startsWith( "<" ) &&
										!pid.startsWith( "azbp" ) &&
										!pid.startsWith( "azupdater" ) &&
										!pid.startsWith( "azplatform" ) &&
										!pids.contains( pid ))){

							if ( info != null && info.length() > 0 ){

								if( info.length() < 256 ){

									pid += ":" + info;

								}else{

									Debug.out( "Plugin '" + pid + "' reported excessive info string '" + info + "'" );
								}
							}

							pids.add( pid );
						}

						Map	data = plugin.getPluginconfig().getPluginMapParameter( "plugin.versionserver.data", null );

						if ( data != null ){

							Map payload = new HashMap();

							byte[]	data_bytes = BEncoder.encode( data );

							if ( data_bytes.length > 16*1024 ){

								Debug.out( "Plugin '" + pid + "' reported excessive version server data (length=" + data_bytes.length + ")" );

								payload.put( "error", "data too long: " + data_bytes.length );

							}else{

								payload.put( "data", data_bytes );
							}

							payload.put( "id", pid);
							payload.put( "version", plugin.getPluginVersion());

							vs_data.add( payload );
						}
					}
					message.put( "plugins", pids );

					if ( vs_data.size() > 0 ){

						message.put( "plugin_data", vs_data );
					}
				}
			}catch( Throwable e ){

				Debug.out( e );
			}
		}


		return message;
	}

	public void
	addVersionCheckClientListener(
		VersionCheckClientListener l)
	{
		listeners.add(l);
	}

	public void
	removeVersionCheckClientListener(
		VersionCheckClientListener l)
	{
		listeners.remove(l);
	}


	public static void
	main(
		String[]	args )
	{
		try{
			COConfigurationManager.initialise();

			COConfigurationManager.setParameter( "IPV6 Enable Support", true );

			boolean v6= true;

			// Test connectivity.
			if (true) {
				// System.out.println( "UDP:  " + getSingleton().getExternalIpAddressUDP(null,0,v6));
				// System.out.println( "TCP:  " + getSingleton().getExternalIpAddressTCP(null,0,v6));
				System.out.println( "HTTP: " + getSingleton().getExternalIpAddressHTTP(v6));
			}

			Map data = constructVersionCheckMessage(VersionCheckClient.REASON_UPDATE_CHECK_START);
			System.out.println("Sending (pre-initialisation):");
			printDataMap(data);
			System.out.println("-----------");

			System.out.println("Receiving (pre-initialisation):");
			printDataMap(getSingleton().getVersionCheckInfo(VersionCheckClient.REASON_UPDATE_CHECK_START));
			System.out.println("-----------");

			System.out.println();
			System.out.print("Initialising core... ");

			/**
			 * Suppress all of these errors being displayed in the output.
			 *
			 * These things should be temporary...
			 */
			DownloadManagerStateImpl.SUPPRESS_FIXUP_ERRORS = true;

			Core core = CoreFactory.create();
			core.start();
			System.out.println("done.");
			System.out.println();
			System.out.println("-----------");

			data = constructVersionCheckMessage(VersionCheckClient.REASON_UPDATE_CHECK_START);
			System.out.println("Sending (post-initialisation):");
			printDataMap(data);
			System.out.println("-----------");

			System.out.println("Receiving (post-initialisation):");
			printDataMap(getSingleton().getVersionCheckInfo(VersionCheckClient.REASON_UPDATE_CHECK_START));
			System.out.println("-----------");
			System.out.println();

			System.out.print("Shutting down core... ");
			core.stop();
			System.out.println("done.");

		}catch( Throwable e){
			e.printStackTrace();
		}
	}

	// Used for debugging in main.
	private static void
	printDataMap(Map map)
		throws Exception
	{
		TreeMap res = new TreeMap(map);

		Iterator key_itr = map.keySet().iterator();

		while (key_itr.hasNext()) {
			Object key = key_itr.next();
			Object val = map.get(key);
			if (val instanceof byte[]) {
				String as_bytes = ByteFormatter.nicePrint((byte[])val);
				String as_text = new String((byte[])val, Constants.BYTE_ENCODING_CHARSET);
				res.put(key, as_text + " [" + as_bytes + "]");
			}
		}

		Iterator entries = res.entrySet().iterator();
		Map.Entry entry;
		while (entries.hasNext()) {
			entry = (Map.Entry)entries.next();
			System.out.print("  ");
			System.out.print(entry.getKey());
			System.out.print(": ");
			System.out.print(entry.getValue());
			System.out.println();
		}
	}
}
