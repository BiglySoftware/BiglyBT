/*
 * File    : ConfigurationChecker.java
 * Created : 8 oct. 2003 23:04:14
 * By      : Olivier
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

package com.biglybt.core.config.impl;


import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.custom.CustomizationManagerFactory;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.security.SESecurityManager;
import com.biglybt.core.speedmanager.impl.SpeedManagerImpl;
import com.biglybt.core.util.*;
import com.biglybt.platform.PlatformManager;
import com.biglybt.platform.PlatformManagerCapabilities;
import com.biglybt.platform.PlatformManagerFactory;


/**
 *
 * The purpose of this class is to provide a way of checking that the config file
 * contains valid values when the client is started.
 *
 *
 * @author Olivier
 *
 */
public class
ConfigurationChecker
{
	private static final LogIDs LOGID = LogIDs.CORE;

  private static final boolean system_properties_set	= false;

  private static boolean checked 				= false;
  private static boolean new_install			= false;

  private static final AEMonitor	class_mon	= new AEMonitor( "ConfigChecker");

	private static boolean new_version = false;




  protected static void
  setSystemProperties()
  {
  	try{
  		class_mon.enter();

	  	if ( system_properties_set ){

	  		return;
	  	}

	  	COConfigurationManager.preInitialise();

	  		//

	  	String	app_path 	=  SystemProperties.getApplicationPath();
	  	String	user_path 	=  SystemProperties.getUserPath();

	  	loadProperties( app_path );

	  	if ( !app_path.equals( user_path )){

	  		loadProperties( user_path );
	  	}

	  		// kinda hard to do this system property setting early enough as we musn't load the
	  		// config until after checking the "pass to existing process" code and this loads the
	  		// class InetAddress that caches the current system prop

	  	COConfigurationManager.addAndFireParameterListener(
	  		"IPV6 Prefer Addresses",
	  		new ParameterListener()
	  		{
	  			private boolean done_something = false;

	  			@Override
				  public void
	  			parameterChanged(
	  				String name )
	  			{
	  			  	boolean	prefer_ipv6 	= COConfigurationManager.getBooleanParameter( name );

	  			  	boolean existing = !System.getProperty( "java.net.preferIPv6Addresses", "false" ).equalsIgnoreCase( "false" );

	  			  		// if user has overridden with a -D at az start then we don't want to let our config
	  			  		// setting (which currently defaults to FALSE) to set this back

	  			  	if ( existing && !done_something ){

	  			  		return;
	  			  	}

	  			  	if ( existing != prefer_ipv6 ){

	  			  		done_something = true;

		  		  		System.setProperty( "java.net.preferIPv6Addresses", prefer_ipv6?"true":"false" );

		  		  		try{
		  		  			Field field = InetAddress.class.getDeclaredField( "preferIPv6Address" );

		  		  			field.setAccessible( true );

		  		  			try{
		  		  				field.setBoolean( null, prefer_ipv6 );
		  		  				
		  		  			}catch( Throwable e ){
		  		  				
		  		  					// changed to an int 
		  		  				
		  		  				try{
		  		  					field.setInt( null, prefer_ipv6?1:0 );
		  		  					
		  		  				}catch( Throwable e2 ){
		  		  					
		  		  						// Java 10+ prevented setting Field fields
		  		  					
		  		  					Java10PlusHacks.setFieldNonFinal( field );
		  		  					
		  		  					field.setInt( null, prefer_ipv6?1:0 );
		  		  				}
		  		  			}

		  		  		}catch( Throwable e ){

		  		  			Debug.out( "Failed to update 'preferIPv6Address'", e );
		  		  		}
	  			  	}
	  			}
	  		});

	  	if ( Constants.isWindowsVistaOrHigher ){

	  		COConfigurationManager.addAndFireParameterListener(
		  		"IPV4 Prefer Stack",
		  		new ParameterListener()
		  		{
		  			private boolean done_something = false;

		  			@Override
					  public void
		  			parameterChanged(
		  				String name )
		  			{
		  			  	boolean	prefer_ipv4 	= COConfigurationManager.getBooleanParameter( name );

		  			  	boolean existing = !System.getProperty( "java.net.preferIPv4Stack", "false" ).equalsIgnoreCase( "false" );

		  			  		// if user has overridden with a -D at az start then we don't want to let our config
		  			  		// setting (which currently defaults to FALSE) to set this back

		  			  	if ( existing && !done_something ){

		  			  		return;
		  			  	}

		  			  	if ( existing != prefer_ipv4 ){

		  			  		done_something = true;

			  		  		System.setProperty( "java.net.preferIPv4Stack", prefer_ipv4?"true":"false" );

			  		  		try{
			  		  			Class<?> plainSocketImpl = getClass().forName( "java.net.PlainSocketImpl");

			  		  			Field pref_field = plainSocketImpl.getDeclaredField( "preferIPv4Stack" );

			  		  			pref_field.setAccessible( true );

			  		  			try{
			  		  				pref_field.setBoolean( null, prefer_ipv4 );
			  		  				
		  		  				}catch( Throwable e2 ){
		  		  					
		  		  						// Java 10+ prevented setting Field fields
		  		  					
		  		  					Java10PlusHacks.setFieldNonFinal( pref_field );
		  		  					
		  		  					pref_field.setBoolean( null, prefer_ipv4 );
		  		  				}

			  		  				// doesn't existing J10+, not sure if things are even
			  		  				// working but whatever
			  		  			
			  		  			if ( !Constants.isJava10OrHigher ){
			  		  				
					  		  		Field dual_field = plainSocketImpl.getDeclaredField( "useDualStackImpl" );
	
					  		  		dual_field.setAccessible( true );
	
					  		  		dual_field.setBoolean( null, !prefer_ipv4 );
			  		  			}
			  		  		}catch( Throwable e ){

			  		  			Debug.out( "Failed to update 'preferIPv4Stack'", e );
			  		  		}
		  			  	}
		  			}
		  		});
	  	}


      // socket connect/read timeouts

	  	int	connect_timeout = COConfigurationManager.getIntParameter( "Tracker Client Connect Timeout");
	  	int	read_timeout 	= COConfigurationManager.getIntParameter( "Tracker Client Read Timeout");

	  	if (Logger.isEnabled())
				Logger.log(new LogEvent(LOGID, "TrackerClient: connect timeout = "
						+ connect_timeout + ", read timeout = " + read_timeout));

	  	System.setProperty(
	  			"sun.net.client.defaultConnectTimeout",
				String.valueOf( connect_timeout*1000 ));

	  	System.setProperty(
	  			"sun.net.client.defaultReadTimeout",
	  			String.valueOf( read_timeout*1000 ));

	  	if ( COConfigurationManager.getBooleanParameter("Enable.Proxy") ) {
	  		String host = COConfigurationManager.getStringParameter("Proxy.Host");
	  		String port = COConfigurationManager.getStringParameter("Proxy.Port");
	  		String user = COConfigurationManager.getStringParameter("Proxy.Username");
	  		String pass = COConfigurationManager.getStringParameter("Proxy.Password");

	  		if ( user.trim().equalsIgnoreCase("<none>")){
	  			user = "";
	  		}

	  		if ( COConfigurationManager.getBooleanParameter("Enable.SOCKS") ) {
	  			System.setProperty("socksProxyHost", host);
	  			System.setProperty("socksProxyPort", port);

	  			if (user.length() > 0) {
	  				System.setProperty("java.net.socks.username", user);
	  				System.setProperty("java.net.socks.password", pass);
	  			}
	  		}
	  		else {
	  			System.setProperty("http.proxyHost", host);
	  			System.setProperty("http.proxyPort", port);
	  			System.setProperty("https.proxyHost", host);
	  			System.setProperty("https.proxyPort", port);

	  			if (user.length() > 0) {
	  				System.setProperty("http.proxyUser", user);
	  				System.setProperty("http.proxyPassword", pass);
	  				System.setProperty("https.proxyUser", user);
	  				System.setProperty("https.proxyPassword", pass);
	  			}
	  		}
	  	}

	  	SESecurityManager.initialise();
  	}finally{

  		class_mon.exit();
  	}
  }

  protected static void
  loadProperties(
	  String	dir )
  {
	  try{
		  String[] prefixes = { "azureus", SystemProperties.getApplicationName().toLowerCase() };
		  
		  for ( String prefix: prefixes ){
			  
			  File	prop_file = FileUtil.newFile( dir, prefix + ".properties" );
	
			  if ( prop_file.exists()){
	
				  Logger.log(new LogEvent(LOGID, "Loading properties file from " + prop_file.getAbsolutePath()));
	
				  Properties props = new Properties();
	
				  InputStream is = FileUtil.newFileInputStream( prop_file );
	
				  try{
					  props.load( is );
	
					  Iterator it = props.entrySet().iterator();
	
					  while( it.hasNext()){
	
						  Map.Entry entry = (Map.Entry)it.next();
	
						  String	key 	= (String)entry.getKey();
						  String	value 	= (String)entry.getValue();
	
						  Logger.log(new LogEvent(LOGID, "    " + key + "=" + value ));
	
						  System.setProperty( key, value );
					  }
				  }finally{
	
					  is.close();
				  }
			  }
		  }
	  }catch( Throwable e ){

	  }
  }

  public static void
  checkConfiguration()
  {
  	try{
  		class_mon.enter();

	    if(checked)
	      return;
	    checked = true;

	    boolean	changed	= CustomizationManagerFactory.getSingleton().preInitialize();

	    String	last_version = COConfigurationManager.getStringParameter( "azureus.version", "" );

	    String	this_version	= Constants.BIGLYBT_VERSION;

	    if ( !last_version.equals( this_version )){
	    	if (!Constants.getBaseVersion(last_version).equals(
						Constants.getBaseVersion())) {
	    		COConfigurationManager.setParameter("Last Version", last_version);
					new_version = true;
				}

	    	if (!COConfigurationManager.hasParameter("First Recorded Version", true)) {
					COConfigurationManager.setParameter("First Recorded Version",
							last_version.length() == 0 ? this_version : last_version);
				} else {
					String sFirstVersion = COConfigurationManager.getStringParameter("First Recorded Version");
					String sMinVersion = Constants.compareVersions(sFirstVersion,
							this_version) > 0 ? this_version : sFirstVersion;
					if (last_version.length() > 0) {
						sMinVersion = Constants.compareVersions(sMinVersion, last_version) > 0
								? last_version : sMinVersion;
					}
					COConfigurationManager.setParameter("First Recorded Version",
							sMinVersion);
				}

	    	COConfigurationManager.setParameter( "azureus.version", this_version );

	    	changed	= true;
	    }

	    	// migration from default-save-dir enable = true to false
	    	// if the user hadn't explicitly set a value then we want to stick with true

	    if ( last_version.length() == 0 ){  //this is a virgin installation, i.e. first time running, called only once ever

	    	new_install	= true;

	    		// "last version" introduced at same time as the default save dir problem
	    		// which was the release after 2.2.0.0

	    		// only do this on an already existing configuration. Easiest way to test
	    		// for this is the "diagnostics.tidy_close" flag

	    	if ( 	COConfigurationManager.doesParameterNonDefaultExist( "diagnostics.tidy_close" )){

	    		if ( !COConfigurationManager.doesParameterNonDefaultExist( "Tracker Port Enable" )){

		    		COConfigurationManager.setParameter( "Tracker Port Enable", true );

		    		changed	= true;
	    		}
	    	}

	    	//enable Beginner user mode for first time
	    	if( !COConfigurationManager.doesParameterNonDefaultExist( "User Mode" ) ) {
	    		COConfigurationManager.setParameter( "User Mode", 0 );
	    		changed	= true;
	    	}

	    	//make sure we set and save a random listen port
	    	if( !COConfigurationManager.doesParameterNonDefaultExist( "TCP.Listen.Port" ) ) {
	    		int	rand_port = RandomUtils.generateRandomNetworkListenPort();
	    		COConfigurationManager.setParameter( "TCP.Listen.Port", rand_port );
	    		COConfigurationManager.setParameter( "UDP.Listen.Port", rand_port );
	    		COConfigurationManager.setParameter( "UDP.NonData.Listen.Port", rand_port );
	    		changed = true;

	    	}
	    }else {  //this is a pre-existing installation, called every time after first


	    	// transition from tracker-only port override to global port override
	    	if(COConfigurationManager.doesParameterNonDefaultExist("TCP.Announce.Port"))
	    	{
		    	COConfigurationManager.setParameter("TCP.Listen.Port.Override", COConfigurationManager.getStringParameter("TCP.Announce.Port", ""));
		    	COConfigurationManager.removeParameter("TCP.Announce.Port");
		    	changed = true;
	    	}




	   	 //enable Advanced user mode for existing users by default, to ease 2304-->2306 migrations
	   	 if( !COConfigurationManager.doesParameterNonDefaultExist( "User Mode" ) ) {
	   		 COConfigurationManager.setParameter( "User Mode", 2 );
	   		 changed	= true;
	   	 }
	    }

	    	// initial UDP port is same as TCP

	    if( !COConfigurationManager.doesParameterNonDefaultExist( "UDP.Listen.Port" ) ){
	    	COConfigurationManager.setParameter( "UDP.Listen.Port", COConfigurationManager.getIntParameter( "TCP.Listen.Port" ));

	    	changed = true;
	    }

	    	// remove separate DHT udp port config and migrate to main UDP port above

	    if ( !COConfigurationManager.getBooleanParameter( "Plugin.DHT.dht.portdefault", true )){

	    	COConfigurationManager.removeParameter( "Plugin.DHT.dht.portdefault" );

	    	int	tcp_port	= COConfigurationManager.getIntParameter( "TCP.Listen.Port" );
	    	int	udp_port	= COConfigurationManager.getIntParameter( "UDP.Listen.Port" );

	    	int	dht_port = COConfigurationManager.getIntParameter( "Plugin.DHT.dht.port", udp_port );

	    	if ( dht_port != udp_port ){

	    			// if tcp + udp are currently different then we leave them as is and migrate
	    			// dht to the udp one. Otherwise we change the core udp to be that of the dht

	    		if ( tcp_port == udp_port ){

	    			COConfigurationManager.setParameter( "UDP.Listen.Port", dht_port );
	    		}
	    	}

	    	changed	= true;
	    }

	    	// reintroduce separate non-data UDP port yto separtate data from dht/UDP tracker

	    if( !COConfigurationManager.doesParameterNonDefaultExist( "UDP.NonData.Listen.Port" ) ){
	    	COConfigurationManager.setParameter( "UDP.NonData.Listen.Port", COConfigurationManager.getIntParameter( "UDP.Listen.Port" ));

	    	changed = true;
	    }

	    	// fix up broken config when multi-udp not enabled but values differ

	    if ( !COConfigurationManager.ENABLE_MULTIPLE_UDP_PORTS ){

	    	int	udp1 = COConfigurationManager.getIntParameter( "UDP.Listen.Port" );
	    	int	udp2 = COConfigurationManager.getIntParameter( "UDP.NonData.Listen.Port" );

	    	if ( udp1 != udp2 ){

		    	COConfigurationManager.setParameter( "UDP.NonData.Listen.Port", udp1 );

		    	changed = true;
	    	}
	    }

	    boolean	randomize_ports = COConfigurationManager.getBooleanParameter( "Listen.Port.Randomize.Enable" );

	    if ( randomize_ports ){

	    	String	random_range = COConfigurationManager.getStringParameter( "Listen.Port.Randomize.Range" );

	    	if ( random_range == null || random_range.trim().length() == 0 ){

	    		random_range = RandomUtils.LISTEN_PORT_MIN + "-" + RandomUtils.LISTEN_PORT_MAX;

	    	}else{

	    		random_range = random_range.trim();
	    	}

	    	int	min_port = RandomUtils.LISTEN_PORT_MIN;
	    	int	max_port = RandomUtils.LISTEN_PORT_MAX;

	    	String[] bits = random_range.split( "-" );

	    	boolean valid = bits.length == 2;

	    	if ( !valid ){

	    			// try alternative split based on any non-numeric char (there are lots of unicode chars that
	    			// look like a - sign...)

	    		char[] chars = random_range.toCharArray();

	    		for (int i=0;i<chars.length-1;i++){

	    			if ( !Character.isDigit( chars[i] )){

	    				bits = new String[]{ random_range.substring( 0, i ), random_range.substring( i+1 )};

	    				valid = true;

	    				break;
	    			}
	    		}
	    	}

	    	if ( valid ){

	    		String	lhs = bits[0].trim();

	    		if ( lhs.length() > 0 ){

	    			try{
	    				min_port = Integer.parseInt( lhs );

	    				valid = min_port > 0 && min_port < 65536;

	    			}catch( Throwable e ){

	    				valid = false;
	    			}
	    		}

	    		String	rhs = bits[1].trim();

	    		if ( rhs.length() > 0 ){

	    			try{
	    				max_port = Integer.parseInt( rhs );

	    				valid = max_port > 0 && max_port < 65536;

	    			}catch( Throwable e ){

	    				valid = false;
	    			}
	    		}
	    	}

	    	if ( valid ){

			    boolean	randomize_together = COConfigurationManager.getBooleanParameter( "Listen.Port.Randomize.Together" );

		    	if ( randomize_together ){

		    		int port = RandomUtils.generateRandomNetworkListenPort( min_port, max_port );

			    	COConfigurationManager.setParameter( "TCP.Listen.Port", port );
			    	COConfigurationManager.setParameter( "UDP.Listen.Port", port );
		    		COConfigurationManager.setParameter( "UDP.NonData.Listen.Port", port );

		    	}else{

			    	int old_udp1 = COConfigurationManager.getIntParameter( "UDP.Listen.Port" );
			    	int old_udp2 = COConfigurationManager.getIntParameter( "UDP.NonData.Listen.Port" );

		    		int port1 = RandomUtils.generateRandomNetworkListenPort( min_port, max_port );

		    		COConfigurationManager.setParameter( "TCP.Listen.Port", port1 );

		    		int port2 = RandomUtils.generateRandomNetworkListenPort( min_port, max_port );

			    	COConfigurationManager.setParameter( "UDP.Listen.Port", port2 );

			    	if ( old_udp1 == old_udp2 ){

			    		COConfigurationManager.setParameter( "UDP.NonData.Listen.Port", port2 );

			    	}else{

			    		int port3 = RandomUtils.generateRandomNetworkListenPort( min_port, max_port );

			    		COConfigurationManager.setParameter( "UDP.NonData.Listen.Port", port3 );
			    	}
		    	}
	    	}
	    }

	    int	tcp_port = COConfigurationManager.getIntParameter( "TCP.Listen.Port" );

	    	// reset invalid ports - single-instance socket port and (small) magnet uri listener port range

	    if ( tcp_port == Constants.INSTANCE_PORT || ( tcp_port >= 45100 && tcp_port <= 45103 )){

	    	int	new_tcp_port	=  RandomUtils.generateRandomNetworkListenPort();

	    	COConfigurationManager.setParameter( "TCP.Listen.Port", new_tcp_port );

	    	if ( COConfigurationManager.getIntParameter( "UDP.Listen.Port" ) == tcp_port ){

	    		COConfigurationManager.setParameter( "UDP.Listen.Port", new_tcp_port );
	    	}

	    	if ( COConfigurationManager.getIntParameter( "UDP.NonData.Listen.Port" ) == tcp_port ){

	    		COConfigurationManager.setParameter( "UDP.NonData.Listen.Port", new_tcp_port );
	    	}

	    	changed = true;
	    }

	    // migrate to split tracker client/server key config

	    if ( !COConfigurationManager.doesParameterDefaultExist( "Tracker Key Enable Client")){

	    	boolean	old_value = COConfigurationManager.getBooleanParameter("Tracker Key Enable");

	    	COConfigurationManager.setParameter("Tracker Key Enable Client", old_value);

	    	COConfigurationManager.setParameter("Tracker Key Enable Server", old_value);

	    	changed = true;
	    }

	    int maxUpSpeed 		= COConfigurationManager.getIntParameter("Max Upload Speed KBs",0);
	    int maxDownSpeed 	= COConfigurationManager.getIntParameter("Max Download Speed KBs",0);

	    if(	maxUpSpeed > 0 &&
	    	maxUpSpeed < COConfigurationManager.CONFIG_DEFAULT_MIN_MAX_UPLOAD_SPEED &&
			(	maxDownSpeed == 0 || maxDownSpeed > (2*maxUpSpeed ))){

	      changed = true;
	      COConfigurationManager.setParameter("Max Upload Speed KBs", COConfigurationManager.CONFIG_DEFAULT_MIN_MAX_UPLOAD_SPEED);
	    }


	    int peersRatio = COConfigurationManager.getIntParameter("Stop Peers Ratio",0);
	    if(peersRatio > 14) {
	      COConfigurationManager.setParameter("Stop Peers Ratio", 14);
	      changed = true;
	    }

	    /* Relaxed restriction on this: https://github.com/BiglySoftware/BiglyBT/issues/2110
	    int minQueueingShareRatio = COConfigurationManager.getIntParameter("StartStopManager_iFirstPriority_ShareRatio");
	    if (minQueueingShareRatio < 500) {
	      COConfigurationManager.setParameter("StartStopManager_iFirstPriority_ShareRatio", 500);
	      changed = true;
	    }
	    */

	    int iSeedingMin = COConfigurationManager.getIntParameter("StartStopManager_iFirstPriority_SeedingMinutes");
	    if (iSeedingMin < 90 && iSeedingMin != 0) {
	      COConfigurationManager.setParameter("StartStopManager_iFirstPriority_SeedingMinutes", 90);
	      changed = true;
	    }

	    int iDLMin = COConfigurationManager.getIntParameter("StartStopManager_iFirstPriority_DLMinutes");
	    if (iDLMin < 60*3 && iDLMin != 0) {
	      COConfigurationManager.setParameter("StartStopManager_iFirstPriority_DLMinutes", 60*3);
	      changed = true;
	    }

		int iIgnoreSPRatio = COConfigurationManager.getIntParameter("StartStopManager_iFirstPriority_ignoreSPRatio");
		if (iIgnoreSPRatio < 10 && iIgnoreSPRatio != 0) {
			COConfigurationManager.setParameter("StartStopManager_iFirstPriority_ignoreSPRatio", 10);
			changed = true;
		}

	    String uniqueId = COConfigurationManager.getStringParameter("ID",null);
	    if(uniqueId == null || uniqueId.length() != 20) {
	      uniqueId = RandomUtils.generateRandomAlphanumerics( 20 );
	      COConfigurationManager.setParameter("ID", uniqueId);
	      changed = true;
	    }

	    int cache_max = COConfigurationManager.getIntParameter("diskmanager.perf.cache.size");
	    if (cache_max > COConfigurationManager.CONFIG_CACHE_SIZE_MAX_MB ) {
	      COConfigurationManager.setParameter("diskmanager.perf.cache.size", COConfigurationManager.CONFIG_CACHE_SIZE_MAX_MB );
	      changed = true;
	    }
	    if( cache_max < 1 ) {  //oops
	    	COConfigurationManager.setParameter("diskmanager.perf.cache.size", 4 );
	      changed = true;
	    }

	    /**
	     * Special Patch for OSX users
	     */
	    if (Constants.isOSX) {

	      // Command + Q destroys the window, then notifies SWT, making it
	      // hard to do a confirmation exit.
	      boolean confirmExit = COConfigurationManager.getBooleanParameter("confirmationOnExit");

	      if ( confirmExit ) {
	        COConfigurationManager.setParameter("confirmationOnExit",false);
	        changed = true;
	      }
	    }


      if( Constants.isOSX ) {
        if( COConfigurationManager.getBooleanParameter( "enable_small_osx_fonts" ) ) {
          System.setProperty( "org.eclipse.swt.internal.carbon.smallFonts", "1" );
        }
        else {
          System.getProperties().remove( "org.eclipse.swt.internal.carbon.smallFonts" );
        }
        System.setProperty( "org.eclipse.swt.internal.carbon.noFocusRing", "1" );
      }


	    //remove a trailing slash, due to user manually entering the path in config
	    String[] path_params = { "Default save path",
	                             "General_sDefaultTorrent_Directory",
	                             "Watch Torrent Folder Path",
	                             "Completed Files Directory" };
	    for( int i=0; i < path_params.length; i++ ) {
	      if( path_params[i].endsWith( SystemProperties.SEP ) ) {
	        String new_path = path_params[i].substring( 0, path_params[i].length() - 1 );
	        COConfigurationManager.setParameter( path_params[i], new_path );
	        changed = true;
	      }
	    }


      //2105 removed the language file web-update functionality,
      //but old left-over MessagesBundle.properties files in the user dir
      //cause display text problems, so let's delete them.
	    if( ConfigurationManager.getInstance().doesParameterNonDefaultExist( "General_bEnableLanguageUpdate" ) ) {
        File user_dir = FileUtil.newFile( SystemProperties.getUserPath() );
        File[] files = user_dir.listFiles( new FilenameFilter() {
          @Override
          public boolean accept(File dir, String name) {
            if( name.startsWith( "MessagesBundle" ) && name.endsWith( ".properties" ) ) {
              return true;
            }
            return false;
          }
        });

        if ( files != null ){
	        for( int i=0; i < files.length; i++ ) {
	          File file = files[ i ];
	          if( file.exists() ) {
	          	if (Logger.isEnabled())
								Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
										"ConfigurationChecker:: removing old language file: "
												+ file.getAbsolutePath()));
	            file.renameTo( FileUtil.newFile( file.getParentFile(), "delme" + file.getName() ) );
	          }
	        }
        }
        ConfigurationManager.getInstance().removeParameter( "General_bEnableLanguageUpdate" );
        changed = true;
      }

	    // 4511: confirm.delete.content (boolean) changed to tb..confirm.delete.content (long)
	    final String CFG_CONFIRM_DELETE_CONTENT = "confirm.delete.content";
	    if (ConfigurationManager.getInstance().doesParameterNonDefaultExist(CFG_CONFIRM_DELETE_CONTENT) ) {
	    	boolean confirm = COConfigurationManager.getBooleanParameter(CFG_CONFIRM_DELETE_CONTENT);
				if (!confirm
						&& !ConfigurationManager.getInstance().doesParameterNonDefaultExist(
								"tb.confirm.delete.content")) {
	    		COConfigurationManager.setParameter("tb.confirm.delete.content", 1);
	    	}
	    	COConfigurationManager.removeParameter(CFG_CONFIRM_DELETE_CONTENT);
	    	changed = true;
	    }

	    	// 5601 propagate swt crash settings
	    {
	    	if ( System.getProperty(SystemProperties.SYSPROP_INTERNAL_BROWSER_DISABLE, "0" ).equals( "1" )){

	    		if ( !COConfigurationManager.getBooleanParameter( "browser.internal.disable", false )){

	    			COConfigurationManager.setParameter( "browser.internal.disable", true );

	    			changed = true;
	    		}
	    	}

			if ( COConfigurationManager.getBooleanParameter( "azpromo.dump.disable.plugin", false )){

					// plugin has detected a crash

				if ( !COConfigurationManager.doesParameterNonDefaultExist( "browser.internal.disable" )){

					COConfigurationManager.setParameter( "browser.internal.disable", true );

					changed = true;
				}
			}

			COConfigurationManager.addAndFireParameterListener(
				"browser.internal.disable",
				new ParameterListener() {

					@Override
					public void
					parameterChanged(
						String parameterName)
					{
						if ( COConfigurationManager.getBooleanParameter( "browser.internal.disable", false )){

							COConfigurationManager.setParameter( "azpromo.dump.disable.plugin", true );

							COConfigurationManager.setDirty();
						}
					}
				});
	    }

	    if ( FeatureAvailability.isAutoSpeedDefaultClassic()){

	    	ConfigurationDefaults.getInstance().addParameter( SpeedManagerImpl.CONFIG_VERSION, 1 );	// 1 == classic, 2 == beta
	    }

	    int check_level = COConfigurationManager.getIntParameter( "config.checker.level", 0 );

	    if ( check_level < 1 ){

	    	COConfigurationManager.setParameter( "config.checker.level", 1 );

	    	changed = true;

		    	// initial setting of auto-config for upload slots etc

			String[]	params = {
					"Max Uploads",
					"enable.seedingonly.maxuploads",
					"Max Uploads Seeding",
					"Max.Peer.Connections.Per.Torrent",
					"Max.Peer.Connections.Per.Torrent.When.Seeding.Enable",
					"Max.Peer.Connections.Per.Torrent.When.Seeding",
					"Max.Peer.Connections.Total",
					"Max Seeds Per Torrent"
			};

			boolean	has_been_set = false;

			for ( String param: params ){

				if ( COConfigurationManager.doesParameterNonDefaultExist( param )){

					has_been_set = true;

					break;
				}
			}

			if ( has_been_set ){

				COConfigurationManager.setParameter( "Auto Adjust Transfer Defaults", false );
			}
	    }

	    if ( Constants.isOSX && check_level < 2 ){

	    		// turn on piece-reorder mode for osx

	    	COConfigurationManager.setParameter( "config.checker.level", 2 );

	    	changed = true;

	    	if ( !COConfigurationManager.getBooleanParameter( "Zero New" )){

	    		COConfigurationManager.setParameter( "Enable reorder storage mode", true );
	    	}
	    }

	    // check_level 3 was used temporarily

	    if ( COConfigurationManager.doesParameterNonDefaultExist( "Watch Torrent Folder Interval" )){

	    	long mins = COConfigurationManager.getIntParameter( "Watch Torrent Folder Interval" );

	    	COConfigurationManager.removeParameter( "Watch Torrent Folder Interval" );

	    	COConfigurationManager.setParameter( "Watch Torrent Folder Interval Secs", 60*mins );

	    	changed = true;
	    }

	    if ( COConfigurationManager.doesParameterNonDefaultExist( "Start Watched Torrents Stopped" )){

	    	boolean stopped = COConfigurationManager.getBooleanParameter( "Start Watched Torrents Stopped" );

	    	COConfigurationManager.removeParameter( "Start Watched Torrents Stopped" );

	    	if ( stopped ){
	    	
	    		COConfigurationManager.setParameter("Watch Torrents Add Mode", 1 );	// mode = stopped
	    	}

	    	changed = true;
	    }
	    
	    if ( COConfigurationManager.doesParameterNonDefaultExist( "diskmanager.friendly.hashchecking" )){
	    	
	    	boolean friendly = COConfigurationManager.getBooleanParameter( "diskmanager.friendly.hashchecking" );
	    	
	    	COConfigurationManager.removeParameter( "diskmanager.friendly.hashchecking" );

	    	if ( friendly ){
	    	
	    		COConfigurationManager.setParameter( "diskmanager.hashchecking.strategy", 0 );
	    	}
	    	
	    	changed = true;
	    }
	    
	    String param_name = "StartStopManager_iFirstPriority_ignoreIdleHours";
	    
	    if ( COConfigurationManager.doesParameterNonDefaultExist( param_name )){
	    	
	    	int hours = COConfigurationManager.getIntParameter( param_name, 0 );
	    	
	    	COConfigurationManager.removeParameter( param_name );
	    	
	    	if ( hours > 0 ){
	    		
	    		COConfigurationManager.setParameter("StartStopManager_iFirstPriority_ignoreIdleMinutes", hours*60 );
	    	}
	    	
	    	changed = true;
	    }    
	    
	    if(changed) {
	      COConfigurationManager.save();
	    }

	    setupVerifier();

  	}finally{

  		class_mon.exit();
  	}

  	ConfigurationDefaults.getInstance().runVerifiers();
  }

  private static void
  setupVerifier()
  {
	  SimpleTimer.addEvent(
			"ConfigCheck:ver",
			SystemTime.getOffsetTime( 10*1000 ),
			new TimerEventPerformer() {

				private TimerEventPeriodic event;

				@Override
				public void
				perform(
					TimerEvent ev )
				{
					runVerifier();

					if ( event == null ){

						long freq = COConfigurationManager.getLongParameter( "Config Verify Frequency" );

						if ( freq > 0 ){

							freq = Math.max( freq,  5*60*1000 );

							event =
								SimpleTimer.addPeriodicEvent(
									"ConfigCheck:ver",
									freq,
									this );
						}
					}
				}
			});
  }

  static void
  runVerifier()
  {
	  try{
		  PlatformManager pm = PlatformManagerFactory.getPlatformManager();

		  if ( pm.hasCapability( PlatformManagerCapabilities.RunAtLogin )){

			  boolean	start_on_login = COConfigurationManager.getBooleanParameter( "Start On Login" );

			  if ( pm.getRunAtLogin() != start_on_login ){

				  pm.setRunAtLogin( start_on_login );
			  }
		  }

		  if ( pm.hasCapability(PlatformManagerCapabilities.RegisterFileAssociations )){

			  boolean	auto_reg = COConfigurationManager.getBooleanParameter( "Auto Register App" );

			  if ( auto_reg ){

				  pm.registerApplication();
			  }
		  }
	  }catch( Throwable e ){

		  Debug.out( e );
	  }
  }

	public static boolean
	isNewInstall()
	{
		return( new_install );
	}

	public static boolean
	isNewVersion()
	{
		return( new_version );
	}
}
