/*
 * Created on Feb 27, 2004
 * Created by Alon Rohter
 * Copyright (C) 2004, 2005, 2006 Alon Rohter, All Rights Reserved.
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
package com.biglybt.core.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Properties;

import com.biglybt.core.internat.LocaleUtil;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.platform.PlatformManager;
import com.biglybt.platform.PlatformManagerFactory;

/**
 * Utility class to manage system-dependant information.
 */
public class SystemProperties {
	private static final LogIDs LOGID = LogIDs.CORE;

		/**
		 * Path separator character.
		 */
	
	public static final String SEP = System.getProperty("file.separator");

	public static final String	AZ_APP_ID	= "az";

		
	public static final String SYSPROP_PORTABLE_ENABLE 						= migrate( "azureus.portable.enable" );
	public static final String SYSPROP_CONFIG_PATH 							= migrate( "azureus.config.path" );	// note this is also used in the restart code....
	public static final String SYSPROP_INSTALL_PATH							= migrate( "azureus.install.path" );
	public static final String SYSPROP_PORTABLE_ROOT						= migrate( "azureus.portable.root" );
	public static final String SYSPROP_JAVA_PROTOCOL_HANDLER_PKGS			= migrate( "java.protocol.handler.pkgs" );
	public static final String SYSPROP_INTERNAL_BROWSER_DISABLE				= migrate( "azureus.internal.browser.disable" );
	public static final String SYSPROP_RCM_PUBLISH_DISABLE					= migrate( "azureus.rcm.publish.disable" );
	public static final String SYSPROP_RCM_MAX_CONCURRENT_PUBLISH			= migrate( "azureus.rcm.max.concurrent.publish" );
	public static final String SYSPROP_RCM_SEARCH_CVS_ONLY					= migrate( "azureus.rcm.search.cvs.only" );
	public static final String SYSPROP_FILE_PIECE_REORDER_FORCE				= migrate( "azureus.file.piece.reorder.force" );
	public static final String SYSPROP_IO_USEMMAP							= migrate( "azureus.io.usemmap" );
	public static final String SYSPROP_DISABLEDOWNLOADS						= migrate( "azureus.disabledownloads" );
	public static final String SYSPROP_INSTANCE_LOCK_DISABLE				= migrate( "azureus.instance.lock.disable" );
	public static final String SYSPROP_LOADPLUGINS							= migrate( "azureus.loadplugins" );
	public static final String SYSPROP_SKIP_SWTCHECK						= migrate( "azureus.skipSWTcheck" );
	public static final String SYSPROP_OVERRIDELOG							= migrate( "azureus.overridelog" );
	public static final String SYSPROP_LOG_STDOUT							= migrate( "azureus.log.stdout" );
	public static final String SYSPROP_SPEED_TEST_CHALLENGE_JAR_PATH		= migrate( "azureus.speed.test.challenge.jar.path" );
	public static final String SYSPROP_LAZY_BITFIELD						= migrate( "azureus.lazy.bitfield" );
	public static final String SYSPROP_SECURITY_MANAGER_INSTALL				= migrate( "azureus.security.manager.install" );
	public static final String SYSPROP_SUBS_MAX_ASSOCIATIONS				= migrate( "azureus.subs.max.associations" );
	public static final String SYSPROP_SUBS_MAX_CONCURRENT_ASSOC_PUBLISH	= migrate( "azureus.subs.max.concurrent.assoc.publish" );
	public static final String SYSPROP_LOG_DOS								= migrate( "azureus.log.dos" );
	public static final String SYSPROP_NATIVELAUNCHER						= migrate( "azureus.nativelauncher" );
	public static final String SYSPROP_INSTANCE_PORT						= migrate( "azureus.instance.port" );
	public static final String SYSPROP_PRODUCT_NAME							= migrate( "azureus.product.name" );
	public static final String SYSPROP_INFER_APP_NAME						= migrate( "azureus.infer.app.name" );
	public static final String SYSPROP_JAVAWS								= migrate( "azureus.javaws" );
	public static final String SYSPROP_TIME_USE_RAW_PROVIDER				= migrate( "azureus.time.use.raw.provider" );
	public static final String SYSPROP_DYNAMIC_PLUGINS						= migrate( "azureus.dynamic.plugins" );
	public static final String SYSPROP_DOC_PATH								= migrate( "azureus.doc.path" );
	public static final String SYSPROP_PLATFORM_MANAGER_DISABLE				= migrate( "azureus.platform.manager.disable" );
	public static final String SYSPROP_CONSOLE_NOISY						= migrate( "azureus.console.noisy" );
	public static final String SYSPROP_LOW_RESOURCE_MODE					= migrate( "azureus.low.resource.mode" );
	public static final String SYSPROP_SAFEMODE								= migrate( "azureus.safemode" );
	public static final String SYSPROP_OVERRIDELOGDIR						= migrate( "azureus.overridelogdir" );
	public static final String SYSPROP_SECURITY_MANAGER_PERMITEXIT			= migrate( "azureus.security.manager.permitexit" );
	public static final String SYSPROP_SCRIPT_VERSION						= migrate( "azureus.script.version" );	// inlined in Updater.java as this runs stand-alone
	public static final String SYSPROP_APP_TIMEZONE							= migrate( "azureus.timezone" );
	public static final String SYSPROP_APP_NAME								= migrate( "azureus.app.name" );
	public static final String SYSPROP_APP_SCRIPT							= migrate( "azureus.script" );
	public static final String SYSPROP_WINDOW_TITLE							= migrate( "azureus.window.title" );
	public static final String SYSPROP_CONSOLE_MULTIUSER					= migrate( "azureus.console.multiuser" );
	public static final String SYSPROP_FOLDER_DOWNLOAD						= migrate( "azureus.folder.download" );
	public static final String SYSPROP_FOLDER_TORRENT						= migrate( "azureus.folder.torrent" );
	public static final String SYSPROP_THREAD_MON_ENABLE					= migrate( "biglybt.thread.monitor.enable" );
	public static final String SYSPROP_MEMORY_MON_ENABLE					= migrate( "biglybt.memory.monitor.enable" );
	public static final String SYSPROP_THREAD_MON_USERONLY					= migrate( "biglybt.thread.monitor.user.time" );
	public static final String SYSPROP_LOGGING_DISABLE_STOP_ON_SLOW_CLOSE	= migrate( "biglybt.logging.disable.stop.on.slow.close" );

		
	private static String
	migrate(
		String		prop )
	{
		if ( prop.startsWith( "azureus." )){
			if ( System.getProperty( prop ) == null ){
				String bbt_prop = "biglybt." + prop.substring( 8 );
				String val = System.getProperty( bbt_prop, null );
				if ( val != null ){
					System.setProperty( prop, val );
				}
			}
		}
		
		return( prop );
	}
	
	
	private static String APPLICATION_NAME 		= "BiglyBT";
	private static String APPLICATION_ID 			= AZ_APP_ID;
	private static String APPLICATION_VERSION		= Constants.BIGLYBT_VERSION;

	private static 		String APPLICATION_ENTRY_POINT 	= "com.biglybt.ui.Main";

	private static final boolean PORTABLE = System.getProperty(SYSPROP_PORTABLE_ROOT, "" ).length() > 0;

  	private static String user_path;
  	private static String app_path;

	public static void
	determineApplicationName()
	{
		String explicit_name = System.getProperty(SYSPROP_APP_NAME, null );

		if ( explicit_name != null ){

			explicit_name = explicit_name.trim();

			if ( explicit_name.length() > 0 ){

				setApplicationName( explicit_name );
			}
		}
	}

	public static void
	setApplicationName(
		String		name )
	{
		if ( name != null && name.trim().length() > 0 ){

			name	= name.trim();

			if ( user_path != null ){

				if ( !name.equals( APPLICATION_NAME )){

					System.out.println( "**** SystemProperties::setApplicationName called too late! ****" );
				}
			}

			APPLICATION_NAME			= name;
		}
	}

	public static void
	setApplicationIdentifier(
		String		application_id )
	{
		if ( application_id != null && application_id.trim().length() > 0 ){

			APPLICATION_ID			= application_id.trim();
		}
	}

	public static void
	setApplicationEntryPoint(
		String		entry_point )
	{
		if ( entry_point != null && entry_point.trim().length() > 0 ){

			APPLICATION_ENTRY_POINT	= entry_point.trim();
		}
	}

	public static String
	getApplicationName()
	{
		return( APPLICATION_NAME );
	}

	public static void
	setApplicationVersion(
		String	v )
	{
		APPLICATION_VERSION = v;
	}

	public static String
	getApplicationVersion()
	{
		return( APPLICATION_VERSION );
	}

	public static String
	getApplicationIdentifier()
	{
		return( APPLICATION_ID );
	}

	public static String
	getApplicationEntryPoint()
	{
		return( APPLICATION_ENTRY_POINT );
	}

		/**
		 * This is used by third-party apps that want explicit control over the user-path
		 * @param _path
		 */

	public static void
	setUserPath(
		String		_path )
	{
		user_path	= _path;
	}

  /**
   * Returns the full path to the user's home directory for this app.
   * Under unix, this is usually ~/.[lowercase AppName]/
   * Under Windows, this is usually .../Documents and Settings/username/Application Data/[AppName]/
   * Under OSX, this is usually /Users/username/Library/Application Support/[AppName]/
   */
  public static String
  getUserPath()
  {
		if (user_path != null) {
			return user_path;
		}

		// WATCH OUT!!!! possible recursion here if logging is changed so that it messes with
		// config initialisation - that's why we don't assign the user_path variable until it
		// is complete - an earlier bug resulted in us half-assigning it and using it due to
		// recursion. At least with this approach we'll get (worst case) stack overflow if
		// a similar change is made, and we'll spot it!!!!

		// Super Override -- no AZ_DIR or xxx_DEFAULT added at all.

		String temp_user_path = System.getProperty(SYSPROP_CONFIG_PATH);

		try {
			if (temp_user_path != null) {

				if (!temp_user_path.endsWith(SEP)) {

					temp_user_path += SEP;
				}

				File dir = new File(temp_user_path);

				if (!dir.exists()) {
					FileUtil.mkdirs(dir);
				}

				// Called within initialization.. no logger!
				//if (Logger.isEnabled())
				//	Logger.log(new LogEvent(LOGID,
				//			"SystemProperties::getUserPath(Custom): user_path = "
				//					+ temp_user_path));

				return temp_user_path;
			}

			// No override, get it from platform manager

			try {
				PlatformManager platformManager = PlatformManagerFactory.getPlatformManager();

				File loc = platformManager.getLocation(	PlatformManager.LOC_USER_DATA );

				if ( loc != null ){
					temp_user_path = loc.getPath() + SEP;

					// Called within initialization.. no logger!
//					if (Logger.isEnabled()) {
//						Logger.log(new LogEvent(LOGID,
//								"SystemProperties::getUserPath: user_path = " + temp_user_path));
//					}
				}
			} catch ( Throwable e ){
				System.err.println("Unable to retrieve user config path from "
									+ "the platform manager. "
									+ "Make sure aereg.dll is present.");
				// Called within initialization.. no logger!
//				if (Logger.isEnabled()) {
//					Logger.log(new LogEvent(LOGID,
//							"Unable to retrieve user config path from "
//									+ "the platform manager. "
//									+ "Make sure aereg.dll is present."));
//				}
			}

			// If platform failed, try some hackery
			if (temp_user_path == null) {
				String userhome = System.getProperty("user.home");

				temp_user_path = userhome + SEP + "."	+ APPLICATION_NAME.toLowerCase()
						+ SEP;
			}

			//if the directory doesn't already exist, create it
			File dir = new File(temp_user_path);
			if (!dir.exists()) {
				FileUtil.mkdirs(dir);
			}

			return temp_user_path;
		} finally {

			user_path = temp_user_path;
		}
	}


  /**
   * Returns the full path to the directory where the app is installed
   * and running from (where the main jar is)
   * <p/>
   * On Windows, this is usually %Program Files%\[AppName]
   * <br>
   * On *nix, this is usually the [Launch Dir]
   * <br>
   * On Mac, this is usually "/Applications/.[AppName]"
   */
  public static String
  getApplicationPath()
  {
	  if ( app_path != null ){

		  return( app_path );
	  }

	  String temp_app_path = System.getProperty(SYSPROP_INSTALL_PATH, System.getProperty("user.dir"));

	  File jarFile = new File(app_path, Constants.DEFAULT_JAR_NAME + ".jar");

	  if (!jarFile.exists()) {
		  String i4jAppDir = System.getProperty("install4j.appDir", null);
		  if (i4jAppDir != null) {
		  	jarFile = new File(i4jAppDir, Constants.DEFAULT_JAR_NAME + ".jar");
		  	if (jarFile.exists()) {
		  		temp_app_path = i4jAppDir;
			  }
		  }
	  }

	  if ( !temp_app_path.endsWith(SEP)){

		  temp_app_path += SEP;
	  }

	  app_path = temp_app_path;

	  return( app_path );
  }


  /**
   * Returns whether or not this running instance was started via
   * Java's Web Start system.
   */
  public static boolean isJavaWebStartInstance() {
    try {
      String java_ws_prop = System.getProperty(SYSPROP_JAVAWS);
      return ( java_ws_prop != null && java_ws_prop.equals( "true" ) );
    }
    catch (Throwable e) {
      //we can get here if running in an applet, as we have no access to system props
      return false;
    }
  }



  /**
   * Will attempt to retrieve an OS-specific environmental var.
   */

  public static String
  getEnvironmentalVariable(
  		final String _var )
  {

    	// this approach doesn't work at all on Windows 95/98/ME - it just hangs
    	// so get the hell outta here!

    if ( Constants.isWindows9598ME ){

    	return( "" );
    }

		// getenv reinstated in 1.5 - try using it

	String	res = System.getenv( _var );

	if ( res != null ){

		return( res );
	}

  	Properties envVars = new Properties();
    BufferedReader br = null;

    try {

     	Process p = null;
      	Runtime r = Runtime.getRuntime();

    	if ( Constants.isWindows ) {
    		p = r.exec( new String[]{ "cmd.exe", "/c", "set" });
    	}
    	else { //we assume unix
    		p = r.exec( "env" );
    	}

			Charset system_encoding = LocaleUtil.getSingleton().getSystemEncoding();

    	if (Logger.isEnabled())
				Logger.log(new LogEvent(LOGID,
						"SystemProperties::getEnvironmentalVariable - " + _var
								+ ", system encoding = " + system_encoding));

    	br = new BufferedReader( new InputStreamReader( p.getInputStream(), system_encoding), 8192);
    	String line;
    	while( (line = br.readLine()) != null ) {
    		int idx = line.indexOf( '=' );
    		if (idx >= 0) {
      		String key = line.substring( 0, idx );
      		String value = line.substring( idx+1 );
      		envVars.setProperty( key, value );
      	}
    	}
      br.close();
    }
    catch (Throwable t) {
      if (br != null) try {  br.close();  } catch (Exception ingore) {}
    }

    return envVars.getProperty( _var, "" );
  }

  public static String getDocPath() {
	  String explicit_dir = System.getProperty(SYSPROP_DOC_PATH, null );

	  if ( explicit_dir != null ){
		  File temp = new File( explicit_dir );
		  if ( !temp.exists()){
			  if ( !temp.mkdirs()){
				  System.err.println( "Failed to create document dir: " + temp );
			  }
		  }else if ( !(temp.isDirectory() && temp.canWrite())){
			  System.err.println( "Document dir is not a directory or not writable: " + temp );
		  }
		  return( temp.getAbsolutePath());
	  }
	  if ( PORTABLE ){

		  return( getUserPath());
	  }

		File fDocPath = null;
		try {
			PlatformManager platformManager = PlatformManagerFactory.getPlatformManager();

			fDocPath = platformManager.getLocation(PlatformManager.LOC_DOCUMENTS);
		} catch (Throwable e) {
		}
		if (fDocPath == null) {
			System.err.println( "This is BAD - fix me!" );
			new Throwable().printStackTrace();
			// should never happen.. but if we are missing a dll..
			fDocPath = new File(getUserPath(), "Documents");
		}

		return fDocPath.getAbsolutePath();
  }

  public static String
  getAzureusJarPath()
  {
	  return getApplicationPath() + Constants.DEFAULT_JAR_NAME + ".jar";
  }
}
