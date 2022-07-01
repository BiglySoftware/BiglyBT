/*
 * File    : COConfigurationManager.java
 * Created : 5 Oct. 2003
 * By      : Parg
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

package com.biglybt.core.config;

import java.io.*;
import java.net.URL;
import java.security.Security;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.biglybt.core.config.impl.ConfigurationDefaults;
import com.biglybt.core.config.impl.ConfigurationManager;
import com.biglybt.core.util.*;
import com.biglybt.core.util.protocol.AzURLStreamHandlerFactory;

public class
COConfigurationManager
{
	public static final int CONFIG_DEFAULT_MIN_MAX_UPLOAD_SPEED			= 5;
	public static final int CONFIG_DEFAULT_MAX_DOWNLOAD_SPEED			= 0;
	public static final int	CONFIG_DEFAULT_MAX_CONNECTIONS_PER_TORRENT	= 50;
	public static final int	CONFIG_DEFAULT_MAX_CONNECTIONS_GLOBAL		= 250;

	public static final int CONFIG_CACHE_SIZE_MAX_MB;

	static{
		long max_mem_bytes 	= Runtime.getRuntime().maxMemory();
	    long mb_1			= 1*1024*1024;
	    long mb_32			= 32*mb_1;
	    int size = (int)(( max_mem_bytes - mb_32 )/mb_1);
	    if( size > 2000 )  size = 2000;  //safety check
      if( size < 1 )  size = 1;
	    CONFIG_CACHE_SIZE_MAX_MB = size;
	}

	public static final boolean	ENABLE_MULTIPLE_UDP_PORTS	= false;

	public static final int	MAX_DATA_SOCKS_PROXIES = 3;
	
	private static boolean	pre_initialised;

	public static synchronized void
	preInitialise()
	{
		if ( !pre_initialised ){

			pre_initialised	= true;

			try{
				if ( System.getProperty(SystemProperties.SYSPROP_PORTABLE_ENABLE, "false" ).equalsIgnoreCase( "true" )){

					try{
						if ( File.separatorChar != '\\' ){

							throw( new Exception( "Portable only supported on Windows" ));
						}

						File portable_root;

						try{
							portable_root = new File( "." ).getCanonicalFile();

						}catch( Throwable e ){

							portable_root = new File( "." ).getAbsoluteFile();
						}

						if ( !portable_root.canWrite()){

							throw( new Exception( "can't write to " + portable_root ));
						}

						File	root_file = new File( portable_root, "portable.dat" );

						String	str = portable_root.getAbsolutePath();

						if ( str.length() < 2 || str.charAt(1) != ':' ){

							throw( new Exception( "drive letter missing in '" + str + "'" ));
						}

						String	root_relative = str.substring( 2 );

						boolean	write_file = true;

						if ( root_file.exists()){

							LineNumberReader lnr = new LineNumberReader( new InputStreamReader( 
								FileUtil.newFileInputStream( root_file ), "UTF-8" ));

							try{
								String	 line = lnr.readLine();

								if ( line != null ){

									line = line.trim();

									if ( line.equalsIgnoreCase( root_relative )){

										write_file = false;

									}else{

										throw( new Exception( "root changed - old='" + line + "', new='" + root_relative ));
									}
								}
							}finally{

								lnr.close();
							}
						}

						if ( write_file ){

							PrintWriter pw = new PrintWriter( new OutputStreamWriter( FileUtil.newFileOutputStream( root_file ), "UTF-8" ));

							try{
								pw.println( root_relative );

							}finally{

								pw.close();
							}
						}

						System.setProperty(SystemProperties.SYSPROP_INSTALL_PATH, str );
						System.setProperty(SystemProperties.SYSPROP_CONFIG_PATH, str );

						System.setProperty(SystemProperties.SYSPROP_PORTABLE_ROOT, str );

						System.out.println( "Portable setup OK - root=" + root_relative + " (current=" + str + ")" );

					}catch( Throwable e ){

						System.err.println( "Portable setup failed: " + e.getMessage());

						System.setProperty(SystemProperties.SYSPROP_PORTABLE_ENABLE, "false" );

						System.setProperty(SystemProperties.SYSPROP_PORTABLE_ROOT, "" );
					}
				}else{

					System.setProperty(SystemProperties.SYSPROP_PORTABLE_ROOT, "" );
				}

			  	String	handlers = System.getProperty(SystemProperties.SYSPROP_JAVA_PROTOCOL_HANDLER_PKGS);

			  	if ( handlers == null ){

			  		handlers = "com.biglybt.core.util.protocol";

			  	}else{

			  		handlers += "|com.biglybt.core.util.protocol";
			  	}

			  	System.setProperty(SystemProperties.SYSPROP_JAVA_PROTOCOL_HANDLER_PKGS, handlers );


					/* for the moment disable this as it is causing some users to get an SSL exception on
					 * trackers with Java 7 due to the tracker hostname setup
					 * See http://stackoverflow.com/questions/7615645/ssl-handshake-alert-unrecognized-name-error-since-upgrade-to-java-1-7-0
					 */

					// Update: nope, disabling this is causing too much other trouble, worked around SNI issues
					// with various hacks...
				//System.setProperty( "jsse.enableSNIExtension", "false" );

				try{
						// From Java 9 onwards this removes the need for unlimited policy files
						// https://bugs.openjdk.java.net/browse/JDK-7024850

					Security.setProperty( "crypto.policy", "unlimited");

				}catch( Throwable e ){
				}

				System.setProperty( "sun.net.maxDatagramSockets", "4096" );

				URL.setURLStreamHandlerFactory(new AzURLStreamHandlerFactory());

			  		// DNS cache timeouts

			  	System.setProperty("sun.net.inetaddr.ttl", "60");
			  	System.setProperty("networkaddress.cache.ttl", "60");
			  	System.setProperty("sun.net.inetaddr.negative.ttl", "300" );
			  	System.setProperty("networkaddress.cache.negative.ttl", "300" );

			  	// flick AWT into headless mode, which is supposed to make it more lightweight
			  	// don't do this as it borks (for example) swing based plugins, java webstart installer,
			  	// swing webui plugin, ....

			  	// System.setProperty("java.awt.headless", "true");

		  		// defaults, overridden later if needed

			  	System.setProperty( "sun.net.client.defaultConnectTimeout", "120000" );
			  	System.setProperty(	"sun.net.client.defaultReadTimeout", "60000" );

			  		// allows us to set HOST headers which is needed when working with Tor+nginx...

			  	System.setProperty( "sun.net.http.allowRestrictedHeaders", "true" );

			      //see http://developer.apple.com/releasenotes/Java/Java142RN/ResolvedIssues/chapter_3_section_7.html
			      //fixes the osx kernel panic bug caused by Apple's faulty kqueue implementation (as of 10.3.6)
						// OpenJDK 7 (Jan, 2012) removed java.nio.preferSelect and doesn't use kqueue
			    //if( Constants.isOSX ) {
			    //		System.setProperty( "java.nio.preferSelect", "true" );
			    //}

			    //if ( Constants.IS_CVS_VERSION && ( Constants.isOSX || Constants.isWindows )){
			    // everyone gets this as we use it to force prevent resolution when running socks

				if ( Constants.isJava9OrHigher ){
					
					try{
						if ( Constants.isJava12OrHigher ){
							
								// AENameServiceJava12.init();
							
							Class.forName( "com.biglybt.core.util.spi.AENameServiceJava12").getMethod( "init" ).invoke( null );
							
						}else{
							
								// AENameServiceJava9.init();
							
							Class.forName( "com.biglybt.core.util.spi.AENameServiceJava9").getMethod( "init" ).invoke( null );
						}
					}catch( Throwable e ){
						
						// issues will be reported later
					}
				}else{
					
					System.setProperty("sun.net.spi.nameservice.provider.1", "dns,aednsproxy");
				}

				//}

			    SystemProperties.determineApplicationName();

			}catch( Throwable e ){

				e.printStackTrace();
			}
		}
	}

	public static ConfigurationManager
	initialise()
	{
		preInitialise();

		return ConfigurationManager.getInstance();
	}

	public static ConfigurationManager
	initialiseFromMap(
		Map		data )
	{
		preInitialise();

		return ConfigurationManager.getInstance(data);
	}

	public static boolean
	isNewInstall()
	{
		return( ConfigurationManager.getInstance().isNewInstall());
	}

	public static String
	getStringParameter(
		String		_name )
	{
		return( ConfigurationManager.getInstance().getStringParameter( _name ));
	}

	public static String
	getStringParameter(
		String		_name,
		String		_default )
	{
		return( ConfigurationManager.getInstance().getStringParameter( _name, _default ));
	}

	public static boolean
	setParameter(String parameter, String value)
	{
		return ConfigurationManager.getInstance().setParameter( parameter, value );
	}

	public static boolean
	verifyParameter(String parameter, String value)
	{
		return ConfigurationManager.getInstance().verifyParameter( parameter, value );
	}

	public static boolean
	getBooleanParameter(
		String		_name )
	{
		return( ConfigurationManager.getInstance().getBooleanParameter( _name ));
	}

	/**
	 * @deprecated You should set ConfigurationDefaults, and use {@link #getBooleanParameter(String)}
	 */
	public static boolean
	getBooleanParameter(
		String		_name,
		boolean		_default )
	{
		return( ConfigurationManager.getInstance().getBooleanParameter( _name, _default ));
	}

	public static boolean
	setParameter(String parameter, boolean value)
	{
		return ConfigurationManager.getInstance().setParameter( parameter, value );
	}

	public static int
	getIntParameter(
		String		_name )
	{
		return( ConfigurationManager.getInstance().getIntParameter( _name ));
	}


	/**
	 * Only use this for internal values, NOT for ones that the user can sensibly change. In this
	 * case add the key to the configuration defaults and use the above method
	 * @param _name
	 * @param _def
	 * @return
	 */

	public static int
	getIntParameter(
		String		_name,
		int		_default )
	{
		return( ConfigurationManager.getInstance().getIntParameter( _name, _default ));
	}

	public static boolean
	setParameter(String parameter, int value)
	{
		return ConfigurationManager.getInstance().setParameter( parameter, value );
	}

	public static boolean
	setParameter(String parameter, long value)
	{
		return ConfigurationManager.getInstance().setParameter( parameter, value );
	}

	public static long
	getLongParameter(
		String		_name )
	{
		return( ConfigurationManager.getInstance().getLongParameter( _name ));
	}

	/**
	 * Only use this for internal values, NOT for ones that the user can sensibly change. In this
	 * case add the key to the configuration defaults and use the above method
	 * @param _name
	 * @param _def
	 * @return
	 */

	public static long
	getLongParameter(
		String		_name,
		long		_def )
	{
		return( ConfigurationManager.getInstance().getLongParameter( _name, _def ));
	}

	public static byte[] getByteParameter(String _name) {
		return( ConfigurationManager.getInstance().getByteParameter(_name));
	}

	public static byte[]
	getByteParameter(
		String		_name,
		byte[]		_default )
	{
		return( ConfigurationManager.getInstance().getByteParameter( _name, _default ));
	}

	public static boolean
	setParameter(String parameter, byte[] value)
	{
		return ConfigurationManager.getInstance().setParameter( parameter, value );
	}

	public static String
	getDirectoryParameter(
		String		_name )
		throws IOException
	{
		return( ConfigurationManager.getInstance().getDirectoryParameter( _name ));
	}



	/*
	public static boolean
	setParameter(String parameter, Color value)
	{
		return ConfigurationManager.getInstance().setParameter( parameter, value );
	}

	public static boolean
	setParameter(String parameter, RGB value)
	{
		return ConfigurationManager.getInstance().setParameter( parameter, value );
	}
	*/

	public static boolean
	setRGBParameter(String parameter, int red, int green, int blue, Boolean override)
	{
		return ConfigurationManager.getInstance().setRGBParameter( parameter, red, green, blue, override);
	}

	public static void
	setRGBDefault(String parameter, int red, int green, int blue)
	{
		ConfigurationDefaults defaults = ConfigurationDefaults.getInstance();
		defaults.addParameter( parameter + ".red", red);
		defaults.addParameter( parameter + ".green", green);
		defaults.addParameter( parameter + ".blue", blue);
	}

	public static boolean setRGBParameter(String parameter, int[] rgb, Boolean override) {
		return ConfigurationManager.getInstance().setRGBParameter(parameter, rgb, override);
	}

	public static int[] getRGBParameter(String parameter ) {
		return ConfigurationManager.getInstance().getRGBParameter(parameter);
	}

	public static float
	getFloatParameter(
		String		_name)
	{
		return( ConfigurationManager.getInstance().getFloatParameter( _name ));
	}

	public static float
	getFloatParameter(
		String		_name,
		float		_def )
	{
		return( ConfigurationManager.getInstance().getFloatParameter( _name, _def ));
	}
	public static boolean
	setParameter(String parameter, float value)
	{
		return ConfigurationManager.getInstance().setParameter( parameter, value );
	}

	/**
	 * Retrieves a List of String from config.
	 * <p/>
	 * Compared to {@link #getListParameter(String, List)}, this method handles
	 * decoding the Strings from byte arrays.
	 */
	public static List<String>
	getStringListParameter(String parameter)
	{
		return( ConfigurationManager.getInstance().getStringListParameter( parameter ));
	}

	public static boolean
	setParameter(String parameter,List value) {
		return ConfigurationManager.getInstance().setParameter( parameter, value );
	}

	/**
	 * Retrieves a List from config.  Not that Strings will be in byte[] format
	 * (you can use {@link #getStringListParameter(String)} if you want String)
	 */
	public static List
	getListParameter(String parameter, List def)
	{
		return( ConfigurationManager.getInstance().getListParameter( parameter, def ));
	}

	public static boolean
	setParameter(String parameter,Map value) {
		return ConfigurationManager.getInstance().setParameter( parameter, value );
	}

	public static Map
	getMapParameter(String parameter, Map def)
	{
		return( ConfigurationManager.getInstance().getMapParameter( parameter, def ));
	}

	  /**
	   * Returns true if a parameter with the given name exists.
	   * @param key The name of the parameter to check.
	   * @param explicit If <tt>true</tt>, we only check for a value which is
	   *     definitely stored explicitly, <tt>false</tt> means that we'll also
	   *     check against configuration defaults too.
	   */
	public static boolean hasParameter(String parameter, boolean explicit) {
		return ConfigurationManager.getInstance().hasParameter(parameter, explicit);
	}

	public static void
	save()
	{
		ConfigurationManager.getInstance().save();
	}

		/**
		 * Mark as needing a save but not immediately - use when potentially needing a large number of saves that aren't
		 * absolutely required to be immediately persisted
		 */

	public static void
	setDirty()
	{
		ConfigurationManager.getInstance().setDirty();
	}

	public static void
	addListener(
		COConfigurationListener		listener )
	{
		ConfigurationManager.getInstance().addListener( listener );
	}

	public static void
	addAndFireListener(
		COConfigurationListener		listener )
	{
		ConfigurationManager.getInstance().addAndFireListener( listener );
	}

	// having ParameterListener first makes it ugly to invoke with anonymous
	// inner class which is a good reminder that this is a WEAK add, and the
	// instance would have been GC'd right away.
	public static void addWeakParameterListener(ParameterListener listener,
			boolean fireImmediately, String... parameter) {
		for (String id : parameter) {
			ConfigurationManager.getInstance().addWeakParameterListener(id, listener);
		}
		if (fireImmediately) {
			try {
				listener.parameterChanged( parameter.length == 1 ? parameter[0] : null );

			}catch (Throwable e) {

				Debug.printStackTrace(e);
			}
		}
	}

	public static void removeWeakParameterListener(ParameterListener listener, String... parameter) {
		for (String id : parameter) {
			ConfigurationManager.getInstance().removeWeakParameterListener(id, listener);
		}
	}

	public static void
	addParameterListener(String parameter, ParameterListener listener)
	{
	  ConfigurationManager.getInstance().addParameterListener(parameter, listener);
	}

	/**
	 * @param strings
	 * @param parameterListener
	 *
	 * @since 3.0.1.5
	 */
	public static void addParameterListener(String[] ids,
			ParameterListener listener) {
		ConfigurationManager instance = ConfigurationManager.getInstance();
		for (int i = 0; i < ids.length; i++) {
		  instance.addParameterListener(ids[i], listener);
		}
	}

	public static void
	addAndFireParameterListener(String parameter, ParameterListener listener)
	{
	  ConfigurationManager.getInstance().addParameterListener(parameter, listener);

	  try {
		  listener.parameterChanged( parameter );

	  }catch (Throwable e) {

		  Debug.printStackTrace(e);
	  }
	}

	public static void
	addAndFireParameterListeners(String[] parameters, ParameterListener listener)
	{
		for (int i=0;i<parameters.length;i++){
			ConfigurationManager.getInstance().addParameterListener(parameters[i], listener);
		}

		try{
			listener.parameterChanged( null );	// code out there relies on the param being null, no changey!

		}catch (Throwable e) {

			Debug.printStackTrace(e);
		}
	}
	public static void
	removeParameterListener(String parameter, ParameterListener listener)
	{
		ConfigurationManager.getInstance().removeParameterListener(parameter, listener);
	}

	public static void
	removeParameterListeners(String[] parameters, ParameterListener listener)
	{
		for ( String parameter: parameters ){
			ConfigurationManager.getInstance().removeParameterListener(parameter, listener);
		}
	}
	
	public static void
	addAndFireWeakParameterListener(String parameter, ParameterListener listener)
	{
	  ConfigurationManager.getInstance().addWeakParameterListener(parameter, listener);

	  try {
		  listener.parameterChanged( parameter );

	  }catch (Throwable e) {

		  Debug.printStackTrace(e);
	  }
	}

	public static void
	addAndFireWeakParameterListeners(String[] parameters, ParameterListener listener)
	{
		for (int i=0;i<parameters.length;i++){
			ConfigurationManager.getInstance().addWeakParameterListener(parameters[i], listener);
		}

		try{
			listener.parameterChanged( null );	// code out there relies on the param being null, no changey!

		}catch (Throwable e) {

			Debug.printStackTrace(e);
		}
	}
	public static void
	removeWeakParameterListener(String parameter, ParameterListener listener)
	{
		ConfigurationManager.getInstance().removeWeakParameterListener(parameter, listener);
	}

	public static void
	removeWeakParameterListeners(String[] parameters, ParameterListener listener)
	{
		for ( String parameter: parameters ){
			ConfigurationManager.getInstance().removeWeakParameterListener(parameter, listener);
		}
	}
	
	public static void
	removeListener(
		COConfigurationListener		listener )
	{
		ConfigurationManager.getInstance().removeListener( listener );
	}

  public static Set<String>
  getAllowedParameters()
  {
  	return ConfigurationDefaults.getInstance().getAllowedParameters();
  }

  public static Set<String>
  getDefinedParameters()
  {
  	return ConfigurationManager.getInstance().getDefinedParameters();
  }

  	/**
  	 * raw parameter access
  	 * @param name
  	 * @return
  	 */
  public static Object
  getParameter(
	String	name )
  {
  	return ConfigurationManager.getInstance().getParameter(name);
  }

  	/**
  	 * checks if a default is defined for the named parameter
  	 * @param parameter
  	 * @return
  	 */

  public static boolean
  doesParameterDefaultExist(
	 String     parameter)
  {
       return ConfigurationDefaults.getInstance().doesParameterDefaultExist(parameter);
  }

  	/**
  	 * checks if the user has explicitly set a value for the named parameter
  	 * @param parameter
  	 * @return
  	 */

  public static boolean
  doesParameterNonDefaultExist(
	 String     parameter)
  {
       return ConfigurationManager.getInstance().doesParameterNonDefaultExist(parameter);
  }
  public static void
  registerExternalDefaults(
  	Map							addmap)
  {
  	ConfigurationDefaults.getInstance().registerExternalDefaults(addmap);
  }

  public static void
  setBooleanDefault(
  	String	parameter,
	boolean	_default )
  {
  	ConfigurationDefaults.getInstance().addParameter( parameter, _default );
  }

  public static void setFloatDefault(String parameter, float _default) {
	  ConfigurationDefaults.getInstance().addParameter( parameter, _default );
  }

  public static void
  setIntDefault(
  	String	parameter,
	int	_default )
  {
  	ConfigurationDefaults.getInstance().addParameter( parameter, _default );
  }

  public static void
  setLongDefault(
  	String	parameter,
	long	_default )
  {
  	ConfigurationDefaults.getInstance().addParameter( parameter, _default );
  }

  public static void
  setStringDefault(
  	String	parameter,
	String	_default )
  {
  	ConfigurationDefaults.getInstance().addParameter( parameter, _default );
  }

  public static void
  setByteDefault(
  	String	parameter,
	byte[]	_default )
  {
  	ConfigurationDefaults.getInstance().addParameter( parameter, _default );
  }

  public static Object
  getDefault(
	 String parameter )
  {
	return( ConfigurationDefaults.getInstance().getParameter( parameter ));
  }

	/**
	 * Remove the given configuration parameter completely.
	 * <br>
	 * If parameter had a value, {@link ParameterListener}s will be fired.
	 *
	 * @param parameter to remove
	 * @return true if found and removed, false if not
	 */
  public static boolean removeParameter(String parameter) {
		return ConfigurationManager.getInstance().removeParameter(parameter);
  }

  public static boolean removeRGBParameter(String parameter) {
		return ConfigurationManager.getInstance().removeRGBParameter(parameter);
  }

  public static void
  registerExportedParameter(
	 String		name,
	 String		key )
  {
	  ConfigurationManager.getInstance().registerExportedParameter( name, key );
  }

  public static void
  resetToDefaults()
  {
	  ConfigurationManager.getInstance().resetToDefaults();
  }

  public static void
  addResetToDefaultsListener(
	  ResetToDefaultsListener		l )
  {
	  ConfigurationManager.getInstance().addResetToDefaultsListener( l );
  }

  public static void
  dumpConfigChanges(
	IndentWriter	writer )
  {
	 ConfigurationManager.getInstance().dumpConfigChanges( writer );
  }

  public interface
  ParameterVerifier
  {
	  public boolean
	  verify(
		String	parameter,
		Object	value );
  }

  public interface
  ResetToDefaultsListener
  {
	public void
	reset();
  }
}
