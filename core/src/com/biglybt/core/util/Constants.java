/*
 * Created on 16 juin 2003
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
package com.biglybt.core.util;

import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.TimeZone;

/**
 *
 * @author Olivier
 *
 */

public class
Constants
{
	public static final String PLUGINS_WEB_SITE   				= "https://plugins.biglybt.com/";
	public static final String URL_PLUGINS_TORRENT_BASE         = "http://version.biglybt.com/biglybt-files/plugins/";
	
	public static final String URL_CLIENT_HOME     = "https://www.biglybt.com/";
	public static final String URL_BUG_REPORTS     = "https://bugs.biglybt.com/";
	public static final String URL_VOTE            = "https://vote.biglybt.com/";
	public static final String URL_FORUMS          = "https://forums.biglybt.com/";
	public static final String URL_WIKI            = "https://wiki.biglybt.com/";
	public static final String URL_GETTING_STARTED = "https://biglybt.github.io/GettingStarted.html";
	public static final String URL_RPC             = "https://rpc.biglybt.com/rpc.php";
	public static final String URL_RPC2            = "https://vrpc.vuze.com/vzrpc/rpc.php";
	@Deprecated // Use Wiki.DEVICES_FAQ
	public static final String URL_DEVICES_FAQ     = URL_WIKI + "w/FAQ_Devices";
	public static final String URL_DONATION        = "https://www.biglybt.com/donation/donate.php";
	public static final String URL_WEBSEARCH       = "https://www.google.com/search?q=%s%20torrent&newwindow=1";
	public static final String URL_SEARCH_ADDEDIT  = "http://client.vuze.com/xsearch/addedit.php?azid=anonymous&azv=5.7.5.0&locale=en_US&os.name=Windows%207&vzemb=1";

	public static final String  VERSION_SERVER_V4 	= "version.biglybt.com";
	public static final String  VERSION_SERVER_V6 	= "version6.biglybt.com";

	public static final String DHT_SEED_ADDRESS_V4			= "dht.biglybt.com";
	public static final String DHT_SEED_ADDRESS_V6			= "dht6.biglybt.com";
	public static final String DHT_SEED_ADDRESS_V6_TUNNEL	= "dht6tunnel.biglybt.com";

	public static final String NAT_TEST_TCP_SERVER		= "nettest.biglybt.com";
	public static final String NAT_TEST_TCP_SERVER_V6	= "nettest6.biglybt.com";
	public static final String NAT_TEST_UDP_SERVER		= "nettestudp.biglybt.com";
	public static final String NAT_TEST_UDP_SERVER_V6	= "nettestudp6.biglybt.com";
	public static final String NAT_TEST_SERVER_HTTP		= "http://nettest.biglybt.com/";
	public static final String NAT_TEST_SERVER_V6_HTTP	= "http://nettest.biglybt.com/";	// nettest.biglybt.com loadbalancer both v4+v6

	public static final String PAIRING_SERVER			= "pair.biglybt.com";
	public static final String WEB_REMOTE_SERVER		= "remote.biglybt.com";

	public static final String SPEED_TEST_SERVER	= "speedtest.vuze.com";

	public static final String[] APP_DOMAINS = { "azureusplatform.com", "azureus.com", "aelitis.com", "vuze.com", "biglybt.com" };

	public static final Charset UTF_8 = Charset.forName("UTF-8");
	public static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");

	@Deprecated
	public static final String DEFAULT_ENCODING 	= "UTF8";
	
	@Deprecated
	public static final String BYTE_ENCODING 		= "ISO-8859-1";

	public static final Charset DEFAULT_ENCODING_CHARSET 	= UTF_8;
	public static final Charset BYTE_ENCODING_CHARSET 		= ISO_8859_1;

	public static final int	DEFAULT_INSTANCE_PORT	= 6880;
	public static final int	INSTANCE_PORT;

	static{
		String ip_str = System.getProperty(SystemProperties.SYSPROP_INSTANCE_PORT, String.valueOf( DEFAULT_INSTANCE_PORT ));

		int	ip;
		try{
			ip = Integer.parseInt( ip_str );
		}catch( Throwable e ){
			ip = DEFAULT_INSTANCE_PORT;
		}

		INSTANCE_PORT = ip;
	}

	public static final Locale LOCALE_ENGLISH = Locale.ENGLISH;

	static{
		try{
			String	timezone = System.getProperty(SystemProperties.SYSPROP_APP_TIMEZONE, null );

			if ( timezone != null ){

				TimeZone.setDefault( TimeZone.getTimeZone( timezone ));
			}
		}catch( Throwable e ){

			e.printStackTrace();
		}
	}

	public static final String INFINITY_STRING	= "\u221E"; // "oo";pa
	public static final int    CRAPPY_INFINITY_AS_INT  = 365*24*3600; // seconds (365days)
	public static final long   CRAPPY_INFINITE_AS_LONG = 10000*365*24*3600; // seconds (10k years)

	/**
	 *  Can't be final as accesed from client speed measurer plugin
	 *  27/04/2022 dead plugin, remove this sometime
	 */

	// @SuppressWarnings("CanBeFinal")
	public static final boolean DOWNLOAD_SOURCES_PRETEND_COMPLETE	= false;

	// keep the CVS style constant coz version checkers depend on it!
	// e.g. 2.0.8.3
	//      2.0.8.3_CVS
	//      2.0.8.3_Bnn       // incremental build


	public static final String BIGLYBT_NAME	  						= "BiglyBT";

	public static final String APP_NAME 		= System.getProperty(SystemProperties.SYSPROP_PRODUCT_NAME, BIGLYBT_NAME );
		
	public static final String DEFAULT_JAR_NAME	  					= BIGLYBT_NAME;

	public static final String BIGLY_PROTOCOL_NAME					= BIGLYBT_NAME;
	public static final String BIGLY_PEER_ID						= "BI";

	
	public static final String BIGLYBT_VERSION  = "3.1.0.0";
	
	//public static final String BUILD_VERSION  = "@build.version@";   //Ant replace - believed dead
	public static final String SUBVERSION		= "";
	public static final byte[] VERSION_ID       = ("-" + BIGLY_PEER_ID + "3100" + "-").getBytes();  //MUST be 8 chars long!

	private static final boolean FORCE_NON_CVS = System.getProperty( "az.force.noncvs", "0" ).equals( "1" );

	public static final boolean IS_CVS_VERSION = isCVSVersion( BIGLYBT_VERSION ) && !FORCE_NON_CVS;

	public static final String  OSName = System.getProperty("os.name");

	public static final boolean isOSX				= OSName.toLowerCase().startsWith("mac os");
	public static final boolean isLinux			= OSName.equalsIgnoreCase("Linux");
	public static final boolean isSolaris			= OSName.equalsIgnoreCase("SunOS");
	public static final boolean isFreeBSD			= OSName.equalsIgnoreCase("FreeBSD");
	public static final boolean isWindowsXP		= OSName.equalsIgnoreCase("Windows XP");
	public static final boolean isWindows95		= OSName.equalsIgnoreCase("Windows 95");
	public static final boolean isWindows98		= OSName.equalsIgnoreCase("Windows 98");
	public static final boolean isWindows2000		= OSName.equalsIgnoreCase("Windows 2000");
	public static final boolean isWindowsME		= OSName.equalsIgnoreCase("Windows ME");
	public static final boolean isWindows9598ME	= isWindows95 || isWindows98 || isWindowsME;

	public static boolean isSafeMode = false;

	public static final boolean isWindows	= OSName.toLowerCase().startsWith("windows");
	// If it isn't windows or osx, it's most likely an unix flavor
	public static final boolean isUnix = !isWindows && !isOSX;

	public static final boolean isWindowsVista;
	public static final boolean isWindowsVistaSP2OrHigher;
	public static final boolean isWindowsVistaOrHigher;
	public static final boolean isWindows7OrHigher;
	public static final boolean isWindows8OrHigher;
	public static final boolean isWindows10OrHigher;


	public static final boolean is64Bit;
	public static final boolean isOS64Bit;

	static{
		boolean _is64Bit;

		try{
			_is64Bit = System.getProperty( "os.arch" ).contains( "64" );

			if ( !_is64Bit ){

				_is64Bit = System.getProperty("sun.arch.data.model").equals( "64" );
			}
		}catch( Throwable e ){

			_is64Bit = false;
		}

		is64Bit = _is64Bit;

		boolean _isOS64Bit = is64Bit;

		if ( isWindows && !_isOS64Bit ){

			try{
				String pa 	= System.getenv("PROCESSOR_ARCHITECTURE");
				String wow_pa = System.getenv("PROCESSOR_ARCHITEW6432");

				_isOS64Bit = ( pa != null && pa.endsWith( "64" )) || ( wow_pa != null && wow_pa.endsWith( "64" ));

			}catch( Throwable e ){
			}
		}

		isOS64Bit = _isOS64Bit;

		if ( isWindows ){

			Float ver = null;

			try{
				ver = new Float( System.getProperty( "os.version" ));

			}catch (Throwable e){
			}

			boolean vista_sp2_or_higher	= false;

			if ( ver == null ){

				isWindowsVista			= false;
				isWindowsVistaOrHigher 	= false;
				isWindows7OrHigher		= false;
				isWindows8OrHigher		= false;
				isWindows10OrHigher		= false;

			}else{
				float f_ver = ver.floatValue();

				isWindowsVista			= f_ver == 6;
				isWindowsVistaOrHigher 	= f_ver >= 6;
				isWindows7OrHigher	 	= f_ver >= 6.1f;
				isWindows8OrHigher	 	= f_ver >= 6.2f;
				isWindows10OrHigher		= f_ver >= 10.0f;

				if ( isWindowsVista ){

					LineNumberReader lnr = null;

					try{
						Process p =
								Runtime.getRuntime().exec(
										new String[]{
												"reg",
												"query",
												"HKLM\\Software\\Microsoft\\Windows NT\\CurrentVersion",
												"/v",
										"CSDVersion" });

						lnr = new LineNumberReader( new InputStreamReader( p.getInputStream()));

						while( true ){

							String	line = lnr.readLine();

							if ( line == null ){

								break;
							}

							if ( line.matches( ".*CSDVersion.*" )){

								vista_sp2_or_higher = line.matches( ".*Service Pack [2-9]" );

								break;
							}
						}
					}catch( Throwable e ){

					}finally{

						if ( lnr != null ){

							try{
								lnr.close();

							}catch( Throwable e ){
							}
						}
					}
				}
			}

			isWindowsVistaSP2OrHigher = vista_sp2_or_higher;
		}else{

			isWindowsVista			= false;
			isWindowsVistaSP2OrHigher	= false;
			isWindowsVistaOrHigher 	= false;
			isWindows7OrHigher 		= false;
			isWindows8OrHigher 		= false;
			isWindows10OrHigher		= false;

		}
	}

	public static final boolean isOSX_10_8_OrHigher;

	static{
		if ( isOSX ){

			int	first_digit 	= 0;
			int	second_digit	= 0;

			try{
				String os_version = System.getProperty( "os.version" );

				String[] bits = os_version.split( "\\." );

				first_digit = Integer.parseInt( bits[0] );

				if ( bits.length > 1 ){

					second_digit = Integer.parseInt( bits[1] );
				}
			}catch( Throwable ignored ){
			}

			isOSX_10_8_OrHigher = first_digit > 10 || ( first_digit == 10 && second_digit >= 8 );

		}else{

			isOSX_10_8_OrHigher = false;
		}
	}

	public static final boolean	isAndroid;

	static{
		isAndroid = System.getProperty("java.vm.name", "").equalsIgnoreCase("Dalvik")
				|| System.getProperty("java.vendor", "").contains("Android");
	}

	// Android is roughly 1.8 (reports as 0 for java.version)

	public static final String	JAVA_VERSION;
	public static final int		API_LEVEL;

	static{
		String java_version 	= isAndroid?"1.8":System.getProperty("java.version");
		int	 api_level 		= 0;

		if ( isAndroid ){

			String sdk_int = System.getProperty( "android.os.build.version.sdk_int", "0" );

			try{
				api_level = Integer.parseInt( sdk_int );

			}catch( Throwable e ){
			}
		}

		JAVA_VERSION 	= java_version;
		API_LEVEL		= api_level;
	}

	public static final boolean isJava7OrHigher = !isAndroid;
	public static final boolean isJava8OrHigher;
	public static final boolean isJava9OrHigher;
	public static final boolean isJava10OrHigher;
	public static final boolean isJava12OrHigher;

	static{
		// http://www.oracle.com/technetwork/java/javase/versioning-naming-139433.html
		// should always start with n.n.

		// unless it is Android where it is always 0

		// then again, from 1.9 (9) we have http://openjdk.java.net/jeps/223

		boolean	_8plus;
		boolean	_9plus;
		boolean	_10plus;
		boolean	_12plus;

		try{
			// from Java 9 we need to drop stuff after a + or - as we can have 9-ea (well, the + case shouldn't occur in java.version but whatever)

			int pos = JAVA_VERSION.indexOf( '-' );

			if ( pos == -1 ){

				pos = JAVA_VERSION.indexOf( '+' );
			}

			String version = pos==-1?JAVA_VERSION:JAVA_VERSION.substring( 0, pos );

			String[]	bits = version.split( "\\." );

			int	first	= Integer.parseInt( bits[0] );
			int	second 	= bits.length==1?0:Integer.parseInt( bits[1] );

			_8plus = first > 1 || second >= 8;
			_9plus = first > 1 || second >= 9;

			_10plus = first >= 10;
			_12plus = first >= 12;

		}catch( Throwable e ){

			System.err.println( "Unparsable Java version: " + JAVA_VERSION );

			e.printStackTrace();

			_8plus = false;		
			_9plus = false;	
			_10plus = false;
			_12plus = false;
		}

		isJava8OrHigher		= _8plus;
		isJava9OrHigher		= _9plus;
		isJava10OrHigher	= _10plus;
		isJava12OrHigher	= _12plus;
	}

	public static final String	FILE_WILDCARD = isWindows?"*.*":"*";

	// use this if you want to prevent the constant getting cached in separately built objects (e.g. plugins)

	public static String
	getCurrentVersion()
	{
		return( BIGLYBT_VERSION );
	}
	/**
	 * Gets the current version, or if a CVS version, the one on which it is based
	 * @return
	 */

	public static String
	getBaseVersion()
	{
		return( getBaseVersion( BIGLYBT_VERSION ));
	}

	public static String
	getBaseVersion(
			String	version )
	{
		int	p1 = version.indexOf("_");	// _CVS or _Bnn

		if ( p1 == -1 ){

			return( version );
		}

		return( version.substring(0,p1));
	}

	/**
	 * is this a formal build or CVS/incremental
	 * @return
	 */

	public static boolean
	isCVSVersion()
	{
		return IS_CVS_VERSION;
	}

	public static boolean
	isCVSVersion(
			String	version )
	{
		return(version.contains("_"));
	}

	/**
	 * For CVS builds this returns the incremental build number. For people running their own
	 * builds this returns -1
	 * @return
	 */

	public static int
	getIncrementalBuild()
	{
		return( getIncrementalBuild( BIGLYBT_VERSION ));
	}

	public static int
	getIncrementalBuild(
			String	version )
	{
		if ( !isCVSVersion(version)){

			return( 0 );
		}

		int	p1 = version.indexOf( "_B" );

		if ( p1 == -1 ){

			return( -1 );
		}

		try{
			return( Integer.parseInt( version.substring(p1+2)));

		}catch( Throwable e ){

			System.out.println("can't parse version");

			return( -1 );
		}
	}

	public static boolean
	isCurrentVersionLT(
			String	version )
	{
		return( compareVersions( BIGLYBT_VERSION, version ) < 0 );
	}

	public static boolean
	isCurrentVersionGE(
			String	version )
	{
		return( compareVersions( BIGLYBT_VERSION, version ) >= 0 );
	}

	/**
	 * compare two version strings of form n.n.n.n (e.g. 1.2.3.4)
	 * @param version_1
	 * @param version_2
	 * @return -ve -> version_1 lower, 0 = same, +ve -> version_1 higher
	 */

	public static int
	compareVersions(
			String		version_1,
			String		version_2 )
	{
		try{
			version_1 = version_1.replaceAll( "_CVS", "_B100" );
			version_2 = version_2.replaceAll( "_CVS", "_B100" );

			if ( version_1.startsWith("." )){
				version_1 = "0" + version_1;
			}
			if ( version_2.startsWith("." )){
				version_2 = "0" + version_2;
			}

			version_1 = version_1.replaceAll("[^0-9.]", ".");
			version_2 = version_2.replaceAll("[^0-9.]", ".");

			StringTokenizer	tok1 = new StringTokenizer(version_1,".");
			StringTokenizer	tok2 = new StringTokenizer(version_2,".");

			while( true ){
				if ( tok1.hasMoreTokens() && tok2.hasMoreTokens()){

					int	i1 = Integer.parseInt(tok1.nextToken());
					int	i2 = Integer.parseInt(tok2.nextToken());

					if ( i1 != i2 ){

						return( i1 - i2 );
					}
				}else if ( tok1.hasMoreTokens()){

					int	i1 = Integer.parseInt(tok1.nextToken());

					if ( i1 != 0 ){

						return( 1 );
					}
				}else if ( tok2.hasMoreTokens()){

					int	i2 = Integer.parseInt(tok2.nextToken());

					if ( i2 != 0 ){

						return( -1 );
					}
				}else{
					return( 0 );
				}
			}
		}catch( Throwable e ){

			e.printStackTrace();

			return( 0 );
		}
	}

	public static boolean
	isValidVersionFormat(
			String		version )
	{
		if ( version == null || version.length() == 0 ){

			return( false );
		}

		for (int i=0;i<version.length();i++){

			char	c = version.charAt(i);

			if ( !( Character.isDigit( c ) || c == '.' )){

				return( false) ;
			}
		}

		if ( version.startsWith( "." ) || version.endsWith( "." ) ||
				version.contains("..")){

			return( false );
		}

		return( true );
	}

	public static String
	getAppName()
	{
		return( APP_NAME );
	}
	
	/**
	 * @deprecated - use isAppDomain()
	 * 06/06/19 Still used by old torhelper
	 */
	
	public static boolean
	isAzureusDomain(
		String	host )
	{
		return( isAppDomain( host ));
	}
	
	public static boolean
	isAppDomain(
			String	host )
	{
		host = host.toLowerCase();

		for (int i=0; i<APP_DOMAINS.length; i++) {

			String domain = (String) APP_DOMAINS[i];

			if ( domain.equals( host )){

				return( true );
			}

			if ( host.endsWith("." + domain)){

				return( true );
			}
		}

		return( false );
	}

	public static final String AZUREUS_PROTOCOL_NAME_PRE_4813	  	= "Azureus";
	public static final String AZUREUS_PROTOCOL_NAME	  			= "Vuze";

	/**
	 * @deprecated - use getCurrentVersion()
	 * 06/06/19 Still used by some of the older plugins out there
	 */
	
	public static final String AZUREUS_VERSION  = BIGLYBT_VERSION;

	/**
	 * @deprecated - use getAppName()
	 * 06/06/19 Still used by some of the older plugins out there
	 */
	
	public static final String AZUREUS_NAME	  	= BIGLYBT_NAME;
}
