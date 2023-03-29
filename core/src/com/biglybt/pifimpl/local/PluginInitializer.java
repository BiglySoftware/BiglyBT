/*
 * File    : PluginInitializer.java
 * Created : 2 nov. 2003 18:59:17
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

package com.biglybt.pifimpl.local;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.util.*;

import com.biglybt.core.Core;
import com.biglybt.core.CoreComponent;
import com.biglybt.core.CoreException;
import com.biglybt.core.CoreLifecycleAdapter;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerListener;
import com.biglybt.core.global.GlobalMangerProgressListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.*;
import com.biglybt.core.versioncheck.VersionCheckClient;
import com.biglybt.pif.*;
import com.biglybt.pif.ipc.IPCInterface;
import com.biglybt.pif.platform.PlatformManagerException;
import com.biglybt.pif.utils.ScriptProvider;
import com.biglybt.pifimpl.local.launch.PluginLauncherImpl;
import com.biglybt.pifimpl.local.ui.UIManagerImpl;
import com.biglybt.pifimpl.local.update.UpdateManagerImpl;
import com.biglybt.pifimpl.local.utils.UtilitiesImpl;
import com.biglybt.pifimpl.local.utils.UtilitiesImpl.runnableWithException;
import com.biglybt.platform.PlatformManagerFactory;
import com.biglybt.update.UpdaterUpdateChecker;
import com.biglybt.update.UpdaterUtils;



/**
 * @author Olivier
 *
 */
public class
PluginInitializer
	implements GlobalManagerListener, AEDiagnosticsEvidenceGenerator
{
	public static final boolean DISABLE_PLUGIN_VERIFICATION = false;

	private static final LogIDs LOGID = LogIDs.CORE;
	public static final String	INTERNAL_PLUGIN_ID = "<internal>";

	// class name, plugin id, plugin key (key used for config props so if you change
	// it you'll need to migrate the config)
	// "id" is used when checking for updates

	// IF YOU ADD TO THE BUILTIN PLUGINS, AMEND PluginManagerDefault appropriately!!!!

		// Plugin ID constant
		// class
		// plugin id
		// plugin key for prefixing config data
		// report if not present
	// force re-enable if disabled by config

  private String[][]	builtin_plugins = {
   			{	 PluginManagerDefaults.PID_START_STOP_RULES,
				    /** {@link com.biglybt.plugin.startstoprules.defaultplugin.StartStopRulesDefaultPlugin} */
   					"com.biglybt.plugin.startstoprules.defaultplugin.StartStopRulesDefaultPlugin",
   					"azbpstartstoprules",
   					"",
   					"true",
   					"true"},
   			{	 PluginManagerDefaults.PID_REMOVE_RULES,
				    /** {@link com.biglybt.plugin.removerules.DownloadRemoveRulesPlugin} */
   					"com.biglybt.plugin.removerules.DownloadRemoveRulesPlugin",
   					"azbpremovalrules",
   					"",
					"true",
					"false"},
    		{	 PluginManagerDefaults.PID_SHARE_HOSTER,
				    /** {@link com.biglybt.plugin.sharing.hoster.ShareHosterPlugin} */
   					"com.biglybt.plugin.sharing.hoster.ShareHosterPlugin",
   					"azbpsharehoster",
   					"ShareHoster",
					"true",
					"false"},
   			{	 PluginManagerDefaults.PID_PLUGIN_UPDATE_CHECKER,
				    /** {@link com.biglybt.pifimpl.update.PluginUpdatePlugin} */
   					"com.biglybt.pifimpl.update.PluginUpdatePlugin",
   					"azbppluginupdate",
   					"PluginUpdate",
					"true",
					"true"},
			{	 PluginManagerDefaults.PID_UPNP,
						/** {@link com.biglybt.plugin.upnp.UPnPPlugin} */
				    "com.biglybt.plugin.upnp.UPnPPlugin",
				    "azbpupnp",
				    "UPnP",
					"true",
					"false"},
			{	 PluginManagerDefaults.PID_DHT,
					/** {@link com.biglybt.plugin.dht.DHTPlugin} */
					"com.biglybt.plugin.dht.DHTPlugin",
					"azbpdht",
					"DHT",
					"true",
					"false"},
			{	 PluginManagerDefaults.PID_DHT_TRACKER,
					/** {@link com.biglybt.plugin.tracker.dht.DHTTrackerPlugin} */
					"com.biglybt.plugin.tracker.dht.DHTTrackerPlugin",
					"azbpdhdtracker",
					"DHT Tracker",
					"true",
					"false"},
			{	 PluginManagerDefaults.PID_MAGNET,
					/** {@link com.biglybt.plugin.magnet.MagnetPlugin} */
					"com.biglybt.plugin.magnet.MagnetPlugin",
					"azbpmagnet",
					"Magnet URI Handler",
					"true",
					"false"},
			{	 PluginManagerDefaults.PID_CORE_UPDATE_CHECKER,
					/** {@link com.biglybt.update.CoreUpdateChecker} */
   					"com.biglybt.update.CoreUpdateChecker",
   					"azbpcoreupdater",
   					"CoreUpdater",
					"true",
					"true"},
			{	 PluginManagerDefaults.PID_CORE_PATCH_CHECKER,
					/** {@link com.biglybt.update.CorePatchChecker} */
   					"com.biglybt.update.CorePatchChecker",
   					"azbpcorepatcher",
   					"CorePatcher",
					"true",
					"true"},
	   		{	 PluginManagerDefaults.PID_PLATFORM_CHECKER,
				    /** {@link com.biglybt.platform.PlatformManagerPluginDelegate} */
   					"com.biglybt.platform.PlatformManagerPluginDelegate",
   					"azplatform2",
   					"azplatform2",
					"true",
					"false"},
	   		//{	 PluginManagerDefaults.PID_JPC,
				//	"com.biglybt.plugins.jpc.JPCPlugin",
				//	"azjpc",
				//	"azjpc",
				//	"false" },
	   		{	 PluginManagerDefaults.PID_EXTERNAL_SEED,
				    /** {@link com.biglybt.plugin.extseed.ExternalSeedPlugin} */
					"com.biglybt.plugin.extseed.ExternalSeedPlugin",
					"azextseed",
					"azextseed",
	   				"true",
	   				"false"},
	   		{	 PluginManagerDefaults.PID_LOCAL_TRACKER,
				    /** {@link com.biglybt.plugin.tracker.local.LocalTrackerPlugin} */
	   				"com.biglybt.plugin.tracker.local.LocalTrackerPlugin",
	   				"azlocaltracker",
	   				"azlocaltracker",
					"true",
					"false"},
			{	 PluginManagerDefaults.PID_NET_STATUS,
					/** {@link com.biglybt.plugin.net.netstatus.NetStatusPlugin} */
		   			"com.biglybt.plugin.net.netstatus.NetStatusPlugin",
		   			"aznetstat",
		   			"aznetstat",
					"true",
					"false"},
			{	 PluginManagerDefaults.PID_BUDDY,
					/** {@link com.biglybt.plugin.net.buddy.BuddyPlugin} */
					"com.biglybt.plugin.net.buddy.BuddyPlugin",
					"azbuddy",
					"azbuddy",
					"true",
					"false"},
			{	 PluginManagerDefaults.PID_RSS,
					/** {@link com.biglybt.plugin.rssgen.RSSGeneratorPlugin} */
					"com.biglybt.plugin.rssgen.RSSGeneratorPlugin",
					"azintrss",
					"azintrss",
					"true",
					"false"},
			{	 PluginManagerDefaults.PID_SIMPLE_API,
					/** {@link com.biglybt.plugin.simpleapi.SimpleAPIPlugin} */
					"com.biglybt.plugin.simpleapi.SimpleAPIPlugin",
					"azintsimpleapi",
					"azintsimpleapi",
					"true",
					"false"},
			{	 PluginManagerDefaults.PID_NET_TEST,
						/** {@link com.biglybt.plugin.net.test.NetTestPlugin} */
						"com.biglybt.plugin.net.test.NetTestPlugin",
						"azintnettest",
						"azintnettest",
						"true",
						"false"},
        };

  /*
  static VerifiedPluginHolder verified_plugin_holder;

  static{
	  synchronized( PluginInitializer.class ){

		  verified_plugin_holder = new VerifiedPluginHolder();
	  }
  }
  */
  
  private static PluginInitializer	singleton;
  private static AEMonitor			class_mon	= new AEMonitor( "PluginInitializer");

  private static List		registration_queue 	= new ArrayList();

  private static List		initThreads = new ArrayList(1);

  private static AsyncDispatcher	async_dispatcher = new AsyncDispatcher();
  private static List<PluginEvent>	plugin_event_history = new ArrayList<>();



  private Core core;

  private PluginInterfaceImpl	default_plugin;
  private PluginManager			plugin_manager;

  private ClassLoader			root_class_loader	= getClass().getClassLoader();

  private List<List<PluginInterface>>		loaded_pi_list		= new ArrayList<>();

  private static boolean	loading_builtin;

  private List<Plugin>					s_plugins				= new ArrayList<>();
  private List<PluginInterfaceImpl>		s_plugin_interfaces		= new ArrayList<>();

  private boolean	initialisation_complete;

  private volatile boolean	plugins_initialised;

  private Set<String>	vc_disabled_plugins = VersionCheckClient.getSingleton().getDisabledPluginIDs();

  public static PluginInitializer
  getSingleton(
  	Core core )
  {
  	try{
  		class_mon.enter();

	  	if ( singleton == null ){

	  		singleton = new PluginInitializer( core );
	  	}

	  	return( singleton );

	}finally{

		class_mon.exit();
	}
  }

  private static PluginInitializer
  peekSingleton()
  {
  	try{
  		class_mon.enter();

	  	return( singleton );

	}finally{

		class_mon.exit();
	}
  }

  protected static void
  queueRegistration(
  	Class	_class )
  {
  	try{
  		class_mon.enter();

	   	if ( singleton == null ){

	  		registration_queue.add( _class );

	  	}else{

	  		try{
	  			singleton.initializePluginFromClass( _class, INTERNAL_PLUGIN_ID, _class.getName(), false, false, true);

			}catch(PluginException e ){

	  		}
	  	}
	}finally{

		class_mon.exit();
	}
  }

  protected static void
  queueRegistration(
  	Plugin plugin,
	String		id,
	String		config_key )
  {
  	try{
  		class_mon.enter();

	   	if ( singleton == null ){

	  		registration_queue.add( new Object[]{ plugin, id, config_key });

	  	}else{

	  		try{
	  			singleton.initializePluginFromInstance( plugin, id, config_key );

			}catch( Throwable e ){

				Debug.out( e );
	  		}
	  	}
	}finally{

		class_mon.exit();
	}
  }

  protected static boolean
  isLoadingBuiltin()
  {
	  return( loading_builtin );
  }

  public static void
  checkCoreAppVersion(
	  String name,
	  Properties props,
	  boolean alert_on_fail
  ) throws PluginException {

	  String required_version = (String)props.get("plugin.coreapp.min_version");
	  if (required_version == null) {return;}
	  if (Constants.compareVersions(Constants.BIGLYBT_VERSION, required_version) < 0) {
		  String plugin_name_bit = name.length() > 0 ? (name+" "):"";
		  String msg = "Plugin " + plugin_name_bit + "requires " + Constants.APP_NAME + " version " + required_version + " or higher";
		  if (alert_on_fail) {
			  Logger.log(new LogAlert(LogAlert.REPEATABLE, LogAlert.AT_ERROR, msg));
		  }
		  throw new PluginException(msg);
	  }
  }

  public static void
  checkJDKVersion(
	String		name,
	Properties	props,
	boolean		alert_on_fail )

  	throws PluginException
  {
      String	required_jdk = (String)props.get( "plugin.jdk.min_version" );

      if ( required_jdk != null ){

    	  String	actual_jdk = Constants.JAVA_VERSION;

    	  required_jdk 	= normaliseJDK( required_jdk );
    	  actual_jdk	= normaliseJDK( actual_jdk );

    	  if ( required_jdk.length() == 0 || actual_jdk.length() == 0 ){

    		  return;
    	  }

    	  if ( Constants.compareVersions( actual_jdk, required_jdk ) < 0 ){

    	    	String	msg =  "Plugin " + (name.length()>0?(name+" "):"" ) + "requires Java version " + required_jdk + " or higher";

    	    	if ( alert_on_fail ){

    	    		Logger.log(new LogAlert(LogAlert.REPEATABLE, LogAlert.AT_ERROR, msg));
    	    	}

    	        throw( new PluginException( msg ));
    	  }
      }
  }

  protected static String
  normaliseJDK(
	String	jdk )
  {
	  try{
		  String	str = "";

		  // take leading digit+. portion only

		  for (int i=0;i<jdk.length();i++){

			  char c = jdk.charAt( i );

			  if ( c == '.' || Character.isDigit( c )){

				  str += c;

			  }else{

				  break;
			  }
		  }

		  	// convert 5|6|... to 1.5|1.6 etc

		  if ( Integer.parseInt( "" + str.charAt(0)) > 1 ){

			  str = "1." + str;
		  }

		  return( str );

	  }catch( Throwable e ){

		  return( "" );
	  }
  }

  protected
  PluginInitializer(
  	Core core )
  {
  	this.core = core;

  	AEDiagnostics.addWeakEvidenceGenerator( this );

  	ScriptProvider provider = 
			new ScriptProvider()
			{
				@Override
				public String
				getProviderName()
				{
					return( "plugin" );
				}
			
				@Override
				public String
				getScriptType()
				{
					return( ST_PLUGIN );
				}
			
				@Override
				public boolean
				canEvalBatch(
					String					script )
				{
					String[] bits = script.split( ",", 2 );
					
					if ( bits.length != 2 ){
						
						return( false );
					}
					
					String pid = bits[0].trim();
					
					if ( pid.endsWith( "+" )){
						
						pid = pid.substring( 0, pid.length()-1);
						
						pid = getFullPID( pid );

						PluginInterface plugin_interface = getPluginManager().getPluginInterfaceByID( pid );
					
						if ( plugin_interface != null ){
						
							return( plugin_interface.getIPC().canInvoke("evalBatchScript" , new Object[]{ new HashMap()}));
						}
					}
					
					return( false );
				}
				
				@Override
				public Object
				eval(
					String					script,
					Map<String,Object>		bindings )
					
					throws Exception
				{
					String[] bits = script.split( ",", 2 );
					
					if ( bits.length != 2 ){
						
						throw( new Exception( "Malformed script" ));
					}
					
					String pid = bits[0].trim();
										
					boolean is_batch = pid.endsWith( "+" );
					
					if ( is_batch ){
						
						pid = pid.substring( 0, pid.length()-1);
					}
					
					pid = getFullPID( pid );

					PluginInterface plugin_interface = getPluginManager().getPluginInterfaceByID( pid );
					
					if ( plugin_interface == null ){
						
						throw( new Exception( "Plugin script: plugin id " + pid + " not installed/loaded" ));
					}
					
					Map<String,Object> args = new HashMap<>( bindings );
					
					args.put( "script", bits[1].trim());
					
					Object[] a_args = new Object[]{ args };
					
					IPCInterface ipc = plugin_interface.getIPC();
					
					if ( is_batch && ipc.canInvoke("evalBatchScript", a_args )){
						
						return( ipc.invoke( "evalBatchScript", a_args ));
						
					}else{
					
						return( ipc.invoke( "evalScript", a_args ));
					}
				}
				
				private String
				getFullPID(
					String	pid )
				{
						// shorthand
					
					if ( pid.equals( "simpleapi" )){
						
						return( "azintsimpleapi" );
					}
					
					return( pid );
				}
			};
				
	getDefaultInterfaceSupport().getUtilities().registerScriptProvider( provider );
		
  	this.core.addLifecycleListener(
	    	new CoreLifecycleAdapter()
			{
	    		@Override
			    public void
				componentCreated(
					Core core,
					CoreComponent comp )
	    		{
	    			if ( comp instanceof GlobalManager ){

	    				GlobalManager	gm	= (GlobalManager)comp;

	    				gm.addListener( PluginInitializer.this );
	    			}
	    		}
			});

    UpdateManagerImpl.getSingleton(this.core);	// initialise the update manager

    plugin_manager = PluginManagerImpl.getSingleton( this );

    String	dynamic_plugins = System.getProperty(SystemProperties.SYSPROP_DYNAMIC_PLUGINS, null );

    if ( dynamic_plugins != null ){

    	String[]	classes = dynamic_plugins.split( ";" );

    	for ( String c: classes ){

    		try{
    			queueRegistration( Class.forName( c ));

    		}catch( Throwable e ){

    			Debug.out( "Registration of dynamic plugin '" + c + "' failed", e );
    		}
    	}
    }

    UpdaterUtils.checkBootstrapPlugins();
  }

  protected void
  fireCreated(
	PluginInterfaceImpl pi )
  {
	  core.triggerLifeCycleComponentCreated( pi );
  }

  protected void
  fireOperational(
	PluginInterfaceImpl	pi,
	boolean				op )
  {
	  fireEventSupport( op?PluginEvent.PEV_PLUGIN_OPERATIONAL:PluginEvent.PEV_PLUGIN_NOT_OPERATIONAL, pi );
  }

  public static void
  addInitThread()
  {
	  synchronized( initThreads ){

		  if ( initThreads.contains( Thread.currentThread())){

			  Debug.out( "Already added" );
		  }

		  initThreads.add( Thread.currentThread());
	  }
  }

  public static void
  removeInitThread()
  {
	  synchronized( initThreads ){

		  initThreads.remove( Thread.currentThread());
	  }
  }

  public static boolean
  isInitThread()
  {
	  synchronized( initThreads ){

		  return initThreads.contains(Thread.currentThread());
	  }
  }

  protected boolean
  isInitialisationThread()
  {
	  return( isInitThread());
  }

  	public List
	loadPlugins(
		Core core,
		boolean bSkipAlreadyLoaded,
		boolean load_external_plugins,
		boolean loading_for_startup,
		boolean initialise_plugins)
  	{
  		if ( bSkipAlreadyLoaded ){

  				// discard any failed ones

  			List pis;

  			synchronized( s_plugin_interfaces ){

  				pis = new ArrayList( s_plugin_interfaces );
  			}

  			for (int i=0;i<pis.size();i++){

  				PluginInterfaceImpl pi = (PluginInterfaceImpl)pis.get(i);

  		  		Plugin p = pi.getPlugin();

  		  		if ( p instanceof FailedPlugin ){

  		  			unloadPlugin( pi );
  		  		}
  		  	}

  		}

  		List pluginLoaded = new ArrayList();

  		PluginManagerImpl.setStartDetails( core );

  		getRootClassLoader();

  			// first do explicit plugins

	    File	user_dir = FileUtil.getUserFile("plugins");

	    File	app_dir	 = FileUtil.getApplicationFile("plugins");

	    int	user_plugins	= 0;
	    int app_plugins		= 0;

	    if ( user_dir.exists() && user_dir.isDirectory()){

	    	user_plugins = user_dir.listFiles().length;

	    }

	    if ( app_dir.exists() && app_dir.isDirectory()){

	    	app_plugins = app_dir.listFiles().length;

	    }

	    	// user ones first so they override app ones if present

	    if (load_external_plugins) {
		    pluginLoaded.addAll(loadPluginsFromDir(user_dir, 0, user_plugins
					+ app_plugins, bSkipAlreadyLoaded, loading_for_startup, initialise_plugins));

		    if ( !user_dir.equals( app_dir )){

		    	pluginLoaded.addAll(loadPluginsFromDir(app_dir, user_plugins,
						user_plugins + app_plugins, bSkipAlreadyLoaded, loading_for_startup, initialise_plugins));
		    }
	    }
	    else {
	    	if (Logger.isEnabled()) {
	    		Logger.log(new LogEvent(LOGID, "Loading of external plugins skipped"));
	    	}
	    }

	    if (Logger.isEnabled())
	    	Logger.log(new LogEvent(LOGID, "Loading built-in plugins"));

  		PluginManagerDefaults	def = PluginManager.getDefaults();

  		for (int i=0;i<builtin_plugins.length;i++){

  			if ( def.isDefaultPluginEnabled( builtin_plugins[i][0])){

  				try{
  					loading_builtin	= true;

  						// lazyness here, for builtin we use static load method with default plugin interface
  						// if we need to improve on this then we'll have to move to a system more akin to
  						// the dir-loaded plugins

  					Class	cla = root_class_loader.loadClass( builtin_plugins[i][1]);

  			      	Method	load_method = cla.getMethod( "load", new Class[]{ PluginInterface.class });

  			      	load_method.invoke( null, new Object[]{ getDefaultInterfaceSupport() });

					Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
							"Built-in plugin '" + builtin_plugins[i][0] + "' ok"));
  			      }catch( NoSuchMethodException e ){

  			      }catch( Throwable e ){

					if ( builtin_plugins[i][4].equalsIgnoreCase("true" )){

						Debug.printStackTrace( e );

						Logger.log(new LogAlert(LogAlert.UNREPEATABLE,
								"Load of built in plugin '" + builtin_plugins[i][2] + "' fails", e));
					}
  				}finally{

  					loading_builtin = false;
  				}
  			}else{
  				if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
								"Built-in plugin '" + builtin_plugins[i][2] + "' is disabled"));
  			}
  		}

 		if (Logger.isEnabled())
			Logger.log(new LogEvent(LOGID, "Loading dynamically registered plugins"));

		for (int i=0;i<registration_queue.size();i++){

			Object	entry = registration_queue.get(i);

			Class	cla;
			String	id;

			if ( entry instanceof Class ){

  				cla = (Class)entry;

  				id	= cla.getName();
			}else{

				Object[]	x = (Object[])entry;

				Plugin	plugin = (Plugin)x[0];

				cla	= plugin.getClass();

				id	= (String)x[1];
			}

			try{
					// lazyness here, for dynamic we use static load method with default plugin interface
					// if we need to improve on this then we'll have to move to a system more akin to
					// the dir-loaded plugins

				Method	load_method = cla.getMethod( "load", new Class[]{ PluginInterface.class });

		      	load_method.invoke( null, new Object[]{ getDefaultInterfaceSupport() });

		    }catch( NoSuchMethodException e ){

		    }catch( Throwable e ){

				Debug.printStackTrace( e );

				Logger.log(new LogAlert(LogAlert.UNREPEATABLE,
						"Load of dynamic plugin '" + id + "' fails", e));
			}
		}

		for ( List<PluginInterface> pis: loaded_pi_list ){

			if ( pis.size() == 1 ){
				
				PluginInterface pi = pis.get(0);
				
				String init_now = pi.getPluginProperties().getProperty( "plugin.initialize.after.load" );
				
				if ( init_now != null && init_now.equals( "true" )){
					
					try{
						if (Logger.isEnabled())
							Logger.log(new LogEvent(LOGID, "Initializing plugin '"
									+ pi.getPluginName() + "' (immediate)"));

						initialisePlugin(pis);

						if (Logger.isEnabled())
							Logger.log(new LogEvent(LOGID, "Initialization of plugin '"
									+ pi.getPluginName() + "' complete"));
						
					}catch( Throwable e ){
					
						// already handled
					}
				}
			}
		}
		
		return pluginLoaded;
  	}

  	private void
	getRootClassLoader()
  	{
  			// first do explicit plugins

  		File	user_dir = FileUtil.getUserFile("shared");

  		getRootClassLoader( user_dir );

  		File	app_dir	 = FileUtil.getApplicationFile("shared");

  		if ( !user_dir.equals( app_dir )){

  			getRootClassLoader( app_dir );
  		}
  	}

 	private void
	getRootClassLoader(
		File		dir )
  	{
 		dir = FileUtil.newFile( dir, "lib" );

 		if ( dir.exists() && dir.isDirectory()){

 			File[]	files = dir.listFiles();

 			if ( files != null ){

 				files = PluginLauncherImpl.getHighestJarVersions( files, new String[]{ null }, new String[]{ null }, false );

 				for (int i=0;i<files.length;i++){

 				 	if (Logger.isEnabled())
 						Logger.log(new LogEvent(LOGID, "Share class loader extended by " + files[i].toString()));

 				 	root_class_loader =
 				 		PluginLauncherImpl.addFileToClassPath(
 				 				PluginInitializer.class.getClassLoader(),
 				 				root_class_loader, files[i] );
 				}
 			}
 		}
  	}

  private List
  loadPluginsFromDir(
  	File	pluginDirectory,
	int		plugin_offset,
	int		plugin_total,
	boolean bSkipAlreadyLoaded,
	boolean loading_for_startup,
	boolean initialise)
  {
  	List dirLoadedPIs = new ArrayList();

  	if (Logger.isEnabled())
			Logger.log(new LogEvent(LOGID, "Plugin Directory is " + pluginDirectory));

    if ( !pluginDirectory.exists() ){

    	FileUtil.mkdirs(pluginDirectory);
    }

    if( pluginDirectory.isDirectory()){

	    File[] pluginsDirectory = pluginDirectory.listFiles();

	    for(int i = 0 ; i < pluginsDirectory.length ; i++) {

        if( pluginsDirectory[i].getName().equals( "CVS" ) ) {

        	if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID, "Skipping plugin "
								+ pluginsDirectory[i].getName()));

          continue;
        }


      if (Logger.isEnabled())
				Logger.log(new LogEvent(LOGID, "Loading plugin "
						+ pluginsDirectory[i].getName()));

	    try{

	    	List	loaded_pis = loadPluginFromDir(pluginsDirectory[i], bSkipAlreadyLoaded, loading_for_startup, initialise);

	    		// save details for later initialisation

	    	loaded_pi_list.add( loaded_pis );
	    	dirLoadedPIs.addAll( loaded_pis );

	      }catch( PluginException e ){

	      		// already handled
	      }

	    }
    }
    return dirLoadedPIs;
  }

  private List
  loadPluginFromDir(
		  File directory,
		  boolean bSkipAlreadyLoaded,
		  boolean loading_for_startup,
		  boolean initialise) // initialise setting is used if loading_for_startup isnt

  throws PluginException
  {
	  List	loaded_pis = new ArrayList();

	  ClassLoader plugin_class_loader = root_class_loader;

	  if( !directory.isDirectory()){

		  return( loaded_pis );
	  }

	  String pluginName = directory.getName();

	  File[] pluginContents = directory.listFiles();

	  if ( pluginContents == null || pluginContents.length == 0){

		  return( loaded_pis );
	  }

	  // first sanity check - dir must include either a plugin.properties or
	  // at least one .jar file

	  boolean	looks_like_plugin	= false;

	  for (int i=0;i<pluginContents.length;i++){

		  String	name = pluginContents[i].getName().toLowerCase();

		  if ( name.endsWith( ".jar") || name.equals( "plugin.properties" )){

			  looks_like_plugin = true;

			  break;
		  }
	  }

	  if ( !looks_like_plugin ){

		  if (Logger.isEnabled())
			  Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
					  "Plugin directory '" + directory + "' has no plugin.properties "
					  + "or .jar files, skipping"));

		  return( loaded_pis );
	  }

	  // take only the highest version numbers of jars that look versioned

	  String[]	plugin_version = {null};
	  String[]	plugin_id = {null};

	  pluginContents	= PluginLauncherImpl.getHighestJarVersions( pluginContents, plugin_version, plugin_id, true );

	  for( int i = 0 ; i < pluginContents.length ; i++){

		  File	jar_file = pluginContents[i];

		  // migration hack for i18nAZ_1.0.jar

		  if ( pluginContents.length > 1 ){

			  String	name = jar_file.getName();

			  if ( name.startsWith( "i18nPlugin_" )){

				  // non-versioned version still there, rename it

				  if (Logger.isEnabled())
					  Logger.log(new LogEvent(LOGID, "renaming '" + name
							  + "' to conform with versioning system"));

				  jar_file.renameTo( new File( jar_file.getParent(), "i18nAZ_0.1.jar  " ));

				  continue;
			  }
		  }

		  plugin_class_loader = PluginLauncherImpl.addFileToClassPath( root_class_loader, plugin_class_loader, jar_file);
	  }

	  String plugin_class_string = null;

	  try {
		  Properties props = new Properties();

		  File	properties_file = FileUtil.newFile(directory.toString(), "plugin.properties");

		  try {

			  // if properties file exists on its own then override any properties file
			  // potentially held within a jar

			  if ( properties_file.exists()){

				  FileInputStream	fis = null;

				  try{
					  fis = FileUtil.newFileInputStream( properties_file );

					  props.load( fis );

				  }finally{

					  if ( fis != null ){

						  fis.close();
					  }
				  }

			  }else{

				  if ( plugin_class_loader instanceof URLClassLoader ){

					  URLClassLoader	current = (URLClassLoader)plugin_class_loader;

					  URL url = current.findResource("plugin.properties");

					  if ( url != null ){
						  URLConnection connection = url.openConnection();

						  InputStream is = connection.getInputStream();

						  props.load(is);

					  }else{

						  throw( new Exception( "failed to load plugin.properties from jars"));
					  }
				  }else{

					  throw( new Exception( "failed to load plugin.properties from dir or jars"));

				  }
			  }
		  }catch( Throwable e ){

			  Debug.printStackTrace( e );

			  String	msg =  "Can't read 'plugin.properties' for plugin '" + pluginName + "': file may be missing";

			  Logger.log(new LogAlert(LogAlert.UNREPEATABLE, LogAlert.AT_ERROR, msg));

			  System.out.println( msg );

			  throw( new PluginException( msg, e ));
		  }

		  checkJDKVersion( pluginName, props, true );
		  checkCoreAppVersion(pluginName, props, true);

		  plugin_class_string = (String)props.get( "plugin.class");

		  if ( plugin_class_string == null ){

			  plugin_class_string = (String)props.get( "plugin.classes");

			  if ( plugin_class_string == null ){

				  // set so we don't bork later will npe

				  plugin_class_string = "";
			  }
		  }

		  String	plugin_name_string = (String)props.get( "plugin.name");

		  if ( plugin_name_string == null ){

			  plugin_name_string = (String)props.get( "plugin.names");
		  }

		  int	pos1 = 0;
		  int	pos2 = 0;

		  while(true){
			  int	p1 = plugin_class_string.indexOf( ";", pos1 );

			  String	plugin_class;

			  if ( p1 == -1 ){
				  plugin_class = plugin_class_string.substring(pos1).trim();
			  }else{
				  plugin_class	= plugin_class_string.substring(pos1,p1).trim();
				  pos1 = p1+1;
			  }

			  PluginInterfaceImpl existing_pi = getPluginFromClass( plugin_class );

			  if ( existing_pi != null ){

				  if (bSkipAlreadyLoaded) {
					  break;
				  }

				  // allow user dir entries to override app dir entries without warning

				  File	this_parent 	= directory.getParentFile();
				  File	existing_parent = null;

				  if ( existing_pi.getInitializerKey() instanceof File ){

					  existing_parent	= ((File)existing_pi.getInitializerKey()).getParentFile();
				  }

				  if ( 	this_parent.equals( FileUtil.getApplicationFile("plugins")) &&
						  existing_parent	!= null &&
						  existing_parent.equals( FileUtil.getUserFile( "plugins" ))){

					  // skip this overridden plugin

					  if (Logger.isEnabled())
						  Logger.log(new LogEvent(LOGID, "Plugin '" + plugin_name_string
								  + "/" + plugin_class
								  + ": shared version overridden by user-specific one"));

					  return( new ArrayList());

				  }else{
					  Logger.log(new LogAlert(LogAlert.UNREPEATABLE, LogAlert.AT_WARNING,
							  "Error loading '" + plugin_name_string + "', plugin class '"
							  + plugin_class + "' is already loaded"));
				  }

			  }else{

				  String	plugin_name = null;

				  if ( plugin_name_string != null ){

					  int	p2 = plugin_name_string.indexOf( ";", pos2 );


					  if ( p2 == -1 ){
						  plugin_name = plugin_name_string.substring(pos2).trim();
					  }else{
						  plugin_name	= plugin_name_string.substring(pos2,p2).trim();
						  pos2 = p2+1;
					  }
				  }

				  Properties new_props = (Properties)props.clone();

				  new_props.put( "plugin.class", plugin_class );

				  if ( plugin_name != null ){

					  new_props.put( "plugin.name", plugin_name );
				  }

				  // System.out.println( "loading plugin '" + plugin_class + "' using cl " + classLoader);

				  // if the plugin load fails we still need to generate a plugin entry
				  // as this drives the upgrade process


				  Throwable	load_failure	= null;

				  String pid = plugin_id[0]==null?directory.getName():plugin_id[0];

				  List<File>	verified_files = null;

				  Plugin plugin = null;

				  if ( vc_disabled_plugins.contains ( pid )){

					  log( "Plugin '" + pid + "' has been administratively disabled" );

				  }else{
					  try{
						  String cl_key = "plugin.cl.ext." + pid;

						  String str = COConfigurationManager.getStringParameter( cl_key, null );

						  if ( str != null && str.length() > 0 ){

							  COConfigurationManager.removeParameter( cl_key );

							  plugin_class_loader = PluginLauncherImpl.extendClassLoader( root_class_loader, plugin_class_loader, new URL( str ));
						  }
					  }catch( Throwable e ){
					  }

					  if ( pid.endsWith( "_v" )){

						  verified_files = new ArrayList<>();

						  // re-verify jar files

						  log( "Re-verifying " + pid );

						  for( int i = 0 ; i < pluginContents.length ; i++){

							  File	jar_file = pluginContents[i];

							  if ( jar_file.getName().endsWith( ".jar" )){

								  try{
									  log( "    verifying " + jar_file );

									  AEVerifier.verifyData( jar_file );

									  verified_files.add( jar_file );

									  log( "    OK" );

								  }catch( Throwable e ){

									  String	msg = "Error loading plugin '" + pluginName + "' / '" + plugin_class_string + "'";

									  Logger.log(new LogAlert(LogAlert.UNREPEATABLE, msg, e));

									  plugin = new FailedPlugin(plugin_name,directory.getAbsolutePath());
								  }
							  }
						  }
					  }

					  if ( plugin == null ){

						  plugin = PluginLauncherImpl.getPreloadedPlugin( plugin_class );

						  if ( plugin == null ){

							  try{
								  try{
									  Class<Plugin> c = (Class<Plugin>)PlatformManagerFactory.getPlatformManager().loadClass( plugin_class_loader, plugin_class );


									  //Class c = plugin_class_loader.loadClass(plugin_class);

									  plugin	= c.newInstance();

									  try{
										  // kick off any pre-inits

										  if ( plugin_class_loader instanceof URLClassLoader ){

											  URL[] urls = ((URLClassLoader)plugin_class_loader).getURLs();

											  for ( URL u: urls ){

												  String path = u.getPath();

												  if ( path.endsWith( ".jar" )){

													  int	s1 = path.lastIndexOf( '/' );
													  int	s2 = path.lastIndexOf( '\\' );

													  path = path.substring( Math.max( s1, s2 )+1);

													  s2 = path.indexOf( '_' );

													  if ( s2 > 0 ){

														  path = path.substring( 0, s2 );

														  path = path.replaceAll( "-", "" );

														  String cl = "plugin.preinit." + pid + ".PI" + path;

														  try{
															  Class pic = plugin_class_loader.loadClass( cl );

															  if ( pic != null ){

																  pic.newInstance();
															  }
														  }catch( Throwable e ){
														  }
													  }
												  }
											  }
										  }
									  }catch( Throwable e ){
									  }
								  }catch( PlatformManagerException e ){

									  throw( e.getCause());
								  }

							  }catch (java.lang.UnsupportedClassVersionError e) {
								  plugin = new FailedPlugin(plugin_name,directory.getAbsolutePath());

								  // shorten stack trace
								  load_failure	= new UnsupportedClassVersionError(e.getMessage());

							  }catch( Throwable e ){

								  if ( 	e instanceof ClassNotFoundException &&
										  props.getProperty( "plugin.install_if_missing", "no" ).equalsIgnoreCase( "yes" )){

									  // don't report the failure

								  }else{

									  load_failure	= e;
								  }

								  plugin = new FailedPlugin(plugin_name,directory.getAbsolutePath());
							  }
						  }else{

							  plugin_class_loader = plugin.getClass().getClassLoader();
						  }
					  }

					  MessageText.integratePluginMessages((String)props.get("plugin.langfile"),plugin_class_loader);

					  PluginInterfaceImpl plugin_interface =
						  new PluginInterfaceImpl(
								  plugin,
								  this,
								  directory,
								  plugin_class_loader,
								  verified_files,
								  directory.getName(),	// key for config values
								  new_props,
								  directory.getAbsolutePath(),
								  pid,
								  plugin_version[0] );

					  boolean bEnabled = (loading_for_startup) ? plugin_interface.getPluginState().isLoadedAtStartup() : initialise;

					  plugin_interface.getPluginState().setDisabled(!bEnabled);

					  try{

						  Method	load_method = plugin.getClass().getMethod( "load", new Class[]{ PluginInterface.class });

						  load_method.invoke( plugin, new Object[]{ plugin_interface });

					  }catch( NoSuchMethodException e ){

					  }catch( Throwable e ){

						  load_failure	= e;
					  }

					  loaded_pis.add( plugin_interface );

					  if ( load_failure != null ){

						  plugin_interface.setAsFailed();

						  	// don't complain about our internal one

						  if ( !pid.equals(UpdaterUpdateChecker.getPluginID())){

							  String msg =
								  MessageText.getString("plugin.init.load.failed",
									new String[]{
										  plugin_name==null?pluginName:plugin_name,
										  directory.getAbsolutePath()
							  		});

							  LogAlert la;

							  if ( load_failure instanceof UnsupportedClassVersionError ){

								  la = new LogAlert(LogAlert.UNREPEATABLE, LogAlert.AT_ERROR, msg + ".\n\n" + MessageText.getString("plugin.install.class_version_error"));

							  }else if ( load_failure instanceof ClassNotFoundException ){

								  la = new LogAlert(LogAlert.UNREPEATABLE, LogAlert.AT_ERROR, msg + ".\n\n" + MessageText.getString("plugin.init.load.failed.classmissing") + "\n\n", load_failure );

							  }else{

								  la = new LogAlert(LogAlert.UNREPEATABLE, msg, load_failure);
							  }

							  Logger.log( la );

							  System.out.println( msg + ": " + load_failure);
						  }
					  }
				  }
			  }
			  if ( p1 == -1 ){
				  break;

			  }
		  }

		  return( loaded_pis );

	  }catch(Throwable e) {

		  if ( e instanceof PluginException ){

			  throw((PluginException)e);
		  }

		  Debug.printStackTrace( e );

		  String	msg = "Error loading plugin '" + pluginName + "' / '" + plugin_class_string + "'";

		  Logger.log(new LogAlert(LogAlert.UNREPEATABLE, msg, e));

		  System.out.println( msg + ": " + e);

		  throw( new PluginException( msg, e ));
	  }
  }

  private void
  log(
	String	str )
  {
	if (Logger.isEnabled()){
		Logger.log(new LogEvent(LOGID, str ));
	}
  }

  public void
  initialisePlugins()
  {
	  try{
		  addInitThread();

		  final LinkedList<Runnable> initQueue = new LinkedList<>();

			for (int i = 0; i < loaded_pi_list.size(); i++) {
				final int idx = i;
				initQueue.add(new Runnable() {
					@Override
					public void run() {
						try {
							List l = (List) loaded_pi_list.get(idx);

							if (l.size() > 0) {
								PluginInterfaceImpl plugin_interface = (PluginInterfaceImpl) l.get(0);

								if (Logger.isEnabled())
									Logger.log(new LogEvent(LOGID, "Initializing plugin '"
											+ plugin_interface.getPluginName() + "'"));

								initialisePlugin(l);

								if (Logger.isEnabled())
									Logger.log(new LogEvent(LOGID, "Initialization of plugin '"
											+ plugin_interface.getPluginName() + "' complete"));

							}

						} catch ( Throwable e ){
							// already handled
						}

						// some plugins try and steal the logger stdout redirects.
						// re-establish them if needed
						Logger.doRedirects();
					}
				});
			}



			// now do built in ones

			initQueue.add(new Runnable() {
				@Override
				public void run() {
					if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID, "Initializing built-in plugins"));
				}
			});



			final PluginManagerDefaults def = PluginManager.getDefaults();

			for (int i = 0; i < builtin_plugins.length; i++) {

				final int idx = i;

				initQueue.add(new Runnable() {
					@Override
					public void run() {
						if (def.isDefaultPluginEnabled(builtin_plugins[idx][0])) {
							String id = builtin_plugins[idx][2];
							String key = builtin_plugins[idx][3];

							try {
								Class cla = root_class_loader.loadClass(
										builtin_plugins[idx][1]);

								if (Logger.isEnabled())
									Logger.log(new LogEvent(LOGID, "Initializing built-in plugin '"
											+ builtin_plugins[idx][2] + "'" ));

								initializePluginFromClass(cla, id, key, "true".equals(builtin_plugins[idx][5]), true, true);

								if (Logger.isEnabled())
								Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
										"Initialization of built in plugin '" + builtin_plugins[idx][2] + "' complete"));
							} catch (Throwable e) {
								try {
									// replace it with a "broken" plugin instance
									initializePluginFromClass(FailedPlugin.class, id, key, false, false, true);

								} catch (Throwable f) {
								}

								if (builtin_plugins[idx][4].equalsIgnoreCase("true")) {
									Debug.printStackTrace(e);
									Logger.log(new LogAlert(LogAlert.UNREPEATABLE,
											"Initialization of built in plugin '" + builtin_plugins[idx][2]
													+ "' fails", e));
								}
							}
						} else {
							if (Logger.isEnabled())
								Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
										"Built-in plugin '" + builtin_plugins[idx][2] + "' is disabled"));
						}

					}
				});

			}

			initQueue.add(new Runnable() {
				@Override
				public void run() {
					if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID,
								"Initializing dynamically registered plugins"));
				}
			});

			for (int i = 0; i < registration_queue.size(); i++) {

				final int idx = i;

				initQueue.add(new Runnable() {
					@Override
					public void run() {
						try {
							Object entry = registration_queue.get(idx);

							if (entry instanceof Class) {

								Class cla = (Class) entry;

								singleton.initializePluginFromClass(cla, INTERNAL_PLUGIN_ID, cla
										.getName(), false, true, true);

							} else {
								Object[] x = (Object[]) entry;

								Plugin plugin = (Plugin) x[0];

								singleton.initializePluginFromInstance(plugin, (String) x[1], (String)x[2]);
							}
						} catch (PluginException e) {
						}
					}
				});
			}

			AEThread2 secondaryInitializer =
				new AEThread2("2nd PluginInitializer Thread",true)
				{
					@Override
					public void run()
					{

						try{
							addInitThread();

							while( true ){

								Runnable toRun;

								synchronized (initQueue){

									if (initQueue.isEmpty()){

										break;
									}

									toRun = (Runnable)initQueue.remove(0);
								}

								try{
									toRun.run();

								}catch( Throwable e ){

									Debug.out(e);
								}
							}
						}finally{

							removeInitThread();
						}
					}
				};
			secondaryInitializer.start();

			while(true){

				Runnable toRun;

				synchronized( initQueue ){

					if( initQueue.isEmpty()){

						break;
					}

					toRun = (Runnable)initQueue.remove(0);
				}

				try{
					toRun.run();

				}catch( Throwable e ){

					Debug.out(e);
				}
			}

			secondaryInitializer.join();

			registration_queue.clear();

			plugins_initialised = true;

			fireEvent( PluginEvent.PEV_ALL_PLUGINS_INITIALISED );

	  }finally{

		  removeInitThread();
	  }
	}

  	protected void
  	checkPluginsInitialised()
  	{
  		if ( !plugins_initialised ){

  			Debug.out( "Wait until plugin initialisation is complete until doing this!" );
  		}
  	}

  	protected boolean
	isInitialized()
  	{
  		return( plugins_initialised );
  	}

  	private void
	initialisePlugin(
		List	l )

  		throws PluginException
  	{
  		PluginException	last_load_failure = null;

  		for (int i=0;i<l.size();i++){

  			final PluginInterfaceImpl	plugin_interface = (PluginInterfaceImpl)l.get(i);

  			synchronized( s_plugin_interfaces ){
  				
  				if ( s_plugin_interfaces.contains( plugin_interface )){
  					
  					String init_now = plugin_interface.getPluginProperties().getProperty( "plugin.initialize.after.load" );
  					
  					if ( init_now != null && init_now.equals( "true" )){
  						
  					}else{
  						
  						Debug.out( "Plugin already initialized!" );
  					}
  					
  					continue;
  				}
  			}
  			
  			if (plugin_interface.getPluginState().isDisabled()) {

  				synchronized( s_plugin_interfaces ){

  					s_plugin_interfaces.add( plugin_interface );
  				}

  				continue;
  			}

  			if ( plugin_interface.getPluginState().isOperational()){

  				continue;
  			}

  			Throwable	load_failure = null;

 			final Plugin	plugin = plugin_interface.getPlugin();

  			try{

  				UtilitiesImpl.callWithPluginThreadContext(
  					plugin_interface,
  					new runnableWithException<PluginException>()
  					{
  						@Override
						  public void
  						run()
  							throws PluginException
  						{
  							fireCreated( plugin_interface );

  							plugin.initialize(plugin_interface);

  							if (!(plugin instanceof FailedPlugin)){

  								plugin_interface.getPluginStateImpl().setOperational( true, false );
  							}
  						}
  					});

  			}catch( Throwable e ){

  				load_failure	= e;
  			}

  			synchronized( s_plugin_interfaces ){

  				s_plugins.add( plugin );

  				s_plugin_interfaces.add( plugin_interface );
  			}

  			if ( load_failure != null ){

  				Debug.printStackTrace( load_failure );

  				String	msg = "Error initializing plugin '" + plugin_interface.getPluginName() + "'";

  				Logger.log(new LogAlert(LogAlert.UNREPEATABLE, msg, load_failure));

  				System.out.println( msg + " : " + load_failure);

  				last_load_failure = new PluginException( msg, load_failure );
  			}
  		}

  		if ( last_load_failure != null ){

  			throw( last_load_failure );
  		}
  	}

  protected void
  initializePluginFromClass(
  	final Class 	plugin_class,
	final String	plugin_id,
	String			plugin_config_key,
	boolean 		force_enabled,
	boolean 		loading_for_startup,
	boolean 		initialise)

  	throws PluginException
  {

  	if ( plugin_class != FailedPlugin.class && getPluginFromClass( plugin_class ) != null ){


  		Logger.log(new LogAlert(LogAlert.UNREPEATABLE, LogAlert.AT_WARNING,
					"Error loading '" + plugin_id + "', plugin class '"
							+ plugin_class.getName() + "' is already loaded"));

  		return;
  	}

  	try{
  		final Plugin plugin = (Plugin) plugin_class.newInstance();

  		String	plugin_name;

  		if ( plugin_config_key.length() == 0 ){

  			plugin_name = plugin_class.getName();

  			int	pos = plugin_name.lastIndexOf(".");

  			if ( pos != -1 ){

  				plugin_name = plugin_name.substring( pos+1 );

  			}
  		}else{

  			plugin_name = plugin_config_key;
  		}

  		Properties properties = new Properties();

  			// default plugin name

  		properties.put( "plugin.name", plugin_name );

  		final PluginInterfaceImpl plugin_interface =
  			new PluginInterfaceImpl(
  						plugin,
						this,
						plugin_class,
						plugin_class.getClassLoader(),
						null,
						plugin_config_key,
						properties,
						"",
						plugin_id,
						null );

  		boolean bEnabled = (loading_for_startup) ? plugin_interface.getPluginState().isLoadedAtStartup() : initialise;

	      /**
	       * For some plugins, override any config setting which disables the plugin.
	       */
	      if (force_enabled && !bEnabled) {
	    	  plugin_interface.getPluginState().setLoadedAtStartup(true);
	    	  bEnabled = true;
	    	  Logger.log(new LogAlert(false, LogAlert.AT_WARNING, MessageText.getString(
	    	      "plugins.init.force_enabled", new String[] {plugin_id}
	    	  )));
	      }

	      plugin_interface.getPluginState().setDisabled(!bEnabled);

	      final boolean f_enabled = bEnabled;

	      UtilitiesImpl.callWithPluginThreadContext(
	    		 plugin_interface,
	    		 new runnableWithException<PluginException>()
	    		 {
	    			 @Override
				     public void
	    			 run()
	    			 	throws PluginException
	    			 {
	    				 try{

	    					 Method	load_method = plugin_class.getMethod( "load", new Class[]{ PluginInterface.class });

	    					 load_method.invoke( plugin, new Object[]{ plugin_interface });

	    				 }catch( NoSuchMethodException e ){

	    				 }catch( Throwable e ){

	    					 Debug.printStackTrace( e );

	    					 Logger.log(new LogAlert(LogAlert.UNREPEATABLE,
	    							 "Load of built in plugin '" + plugin_id + "' fails", e));
	    				 }

	    				 if (f_enabled) {

	    					 fireCreated( plugin_interface );

	    					 plugin.initialize(plugin_interface);

	    					 if (!(plugin instanceof FailedPlugin)){

	    						 plugin_interface.getPluginStateImpl().setOperational( true, false );
	    					 }
	    				 }
	    			 }
	    		 });

		 synchronized( s_plugin_interfaces ){

			s_plugins.add( plugin );

			s_plugin_interfaces.add( plugin_interface );
		 }
  	}catch(Throwable e){

  		Debug.printStackTrace( e );

  		String	msg = "Error loading internal plugin '" + plugin_class.getName() + "'";

  		Logger.log(new LogAlert(LogAlert.UNREPEATABLE, msg, e));

  		System.out.println(msg + " : " + e);

  		throw( new PluginException( msg, e ));
  	}
  }

  protected void
  initializePluginFromInstance(
  	final Plugin		plugin,
	String				plugin_id,
	String				plugin_config_key )

  	throws PluginException
  {
  	try{
  		final PluginInterfaceImpl plugin_interface =
  			new PluginInterfaceImpl(
  						plugin,
						this,
						plugin.getClass(),
						plugin.getClass().getClassLoader(),
						null,
						plugin_config_key,
						plugin.getInitialProperties(),
						"",
						plugin_id,
						null );

		UtilitiesImpl.callWithPluginThreadContext(
			plugin_interface,
			new UtilitiesImpl.runnableWithException<PluginException>()
			{
				@Override
				public void
				run()

					throws PluginException
				{
					fireCreated( plugin_interface );

			  		plugin.initialize(plugin_interface);

			  		if (!(plugin instanceof FailedPlugin)){

			  			plugin_interface.getPluginStateImpl().setOperational( true, false );
			  		}
				}
			});

  		synchronized( s_plugin_interfaces ){

  			s_plugins.add( plugin );

  			s_plugin_interfaces.add( plugin_interface );
  		}
  	}catch(Throwable e){

  		Debug.printStackTrace( e );

  		String	msg = "Error loading internal plugin '" + plugin.getClass().getName() + "'";

  		Logger.log(new LogAlert(LogAlert.UNREPEATABLE, msg, e));

  		System.out.println(msg + " : " + e);

  		throw( new PluginException( msg, e ));
  	}
  }

  protected void
  unloadPlugin(
  	PluginInterfaceImpl		pi )
  {
	synchronized( s_plugin_interfaces ){

		s_plugins.remove( pi.getPlugin());

		s_plugin_interfaces.remove( pi );
	}

  	pi.unloadSupport();

  	for (int i=0;i<loaded_pi_list.size();i++){

  		List	l = (List)loaded_pi_list.get(i);

  		if ( l.remove(pi)){

  			if ( l.size() == 0 ){

  				loaded_pi_list.remove(i);
  			}

  			break;
  		}
  	}

  	// verified_plugin_holder.removeValue( pi );
  }

  protected void reloadPlugin(PluginInterfaceImpl pi) throws PluginException {
	  reloadPlugin(pi, false, true);
  }

	protected void reloadPlugin(
			PluginInterfaceImpl pi,
			boolean loading_for_startup,
			boolean initialise) throws PluginException {

	  unloadPlugin( pi );

	  Object key 			= pi.getInitializerKey();
	  String config_key	= pi.getPluginConfigKey();

	  if ( key instanceof File ){

  		List	pis = loadPluginFromDir( (File)key, false, loading_for_startup, initialise);

  		initialisePlugin( pis );

	  }else{
  		initializePluginFromClass( (Class) key, pi.getPluginID(), config_key, false, loading_for_startup, initialise );
	  }
	}

  protected Core
  getCore()
  {
  	return( core );
  }

  protected GlobalManager
  getGlobalManager()
  {
  	return( core.getGlobalManager() );
  }

	public static PluginInterface
	getDefaultInterface()
	{
  	if (singleton == null) {
  		throw new CoreException(
					"PluginInitializer not instantiated by Core.create yet");
  	}
  	return( singleton.getDefaultInterfaceSupport());
	}

  protected PluginInterface
  getDefaultInterfaceSupport()
  {
	  synchronized( s_plugin_interfaces ){

	  	if ( default_plugin == null ){

	  		try{
		  		default_plugin =
		  			new PluginInterfaceImpl(
		  					new Plugin()
							{
		  						@Override
								  public void
								initialize(
									PluginInterface pi)
		  						{
		  						}
							},
							this,
							getClass(),
							getClass().getClassLoader(),
							null,
							"default",
							new Properties(),
							null,
							INTERNAL_PLUGIN_ID,
							null );

	  		}catch( Throwable e ){

	  			Debug.out( e );
	  		}
	  	}
  	}

  	return( default_plugin );
  }

  @Override
  public void
  downloadManagerAdded(
  	DownloadManager	dm )
  {
  }

  @Override
  public void
  downloadManagerRemoved(
  	DownloadManager	dm )
  {
  }

  @Override
  public void
  destroyInitiated(
	GlobalMangerProgressListener progress ) 
  {
	  List<PluginInterfaceImpl> plugin_interfaces;

	  synchronized( s_plugin_interfaces ){

		  plugin_interfaces = new ArrayList<>( s_plugin_interfaces );
	  }

	  	// reverse order - really only to ensure that built in ones get stopped before slower ones
	  	// in particular the I2P Helper plugin can take a long time to stop and we would prefer that
	  	// the start-stop rules plugin (built-in so loaded later) stops early
	  	// Also it does make sense to stop them in the opposite order, kinda

	  Collections.reverse( plugin_interfaces );
	  
	  for ( PluginInterfaceImpl pi: plugin_interfaces ){

		  if ( progress != null ){
			  
			  progress.reportCurrentTask( MessageText.getString( "ManagerItem.stopping" ) + " " + pi.getPluginName());
		  }
		  
		  try{
			  pi.closedownInitiated();
			  
		  }catch( Throwable e ){
			  
			  Debug.out( e );
		  }
	  }

	  if ( default_plugin != null ){

		  try{
			  default_plugin.closedownInitiated();
			  
		  }catch( Throwable e ){
			  
			  Debug.out( e );
		  }
	  }
  }

  @Override
  public void
  destroyed()
  {
	  List<PluginInterfaceImpl> plugin_interfaces;

	  synchronized( s_plugin_interfaces ){

		  plugin_interfaces = new ArrayList<>( s_plugin_interfaces );
	  }

	  Collections.reverse( plugin_interfaces );
	  
	  for ( PluginInterfaceImpl pi: plugin_interfaces ){

		  try{
			  pi.closedownComplete();
			  
		  }catch( Throwable e ){
			  
			  Debug.out( e );
		  }
	  }

	  if ( default_plugin != null ){

		  try{
			  default_plugin.closedownComplete();
			  
		  }catch( Throwable e ){
			  
			  Debug.out( e );
		  }
	  }
  }


  @Override
  public void seedingStatusChanged(boolean seeding_only_mode, boolean b ){
    /*nothing*/
  }

  protected void
  runPEVTask(
		AERunnable	run )
  {
	  async_dispatcher.dispatch( run );
  }


  protected List<PluginEvent>
  getPEVHistory()
  {
	  return( plugin_event_history );
  }

  protected void
  fireEventSupport(
  	final int		type,
  	final Object	value )
  {
	  async_dispatcher.dispatch(
		 new AERunnable()
		 {
			 @Override
			 public void
			 runSupport()
			 {
			  	PluginEvent	ev =
			  		new PluginEvent()
			  		{
			  			@Override
						  public int
			  			getType()
			  			{
			  				return( type );
			  			}

			  			@Override
						  public Object
			  			getValue()
			  			{
			  				return( value );
			  			}
			  		};

			  	if ( 	type == PluginEvent.PEV_CONFIGURATION_WIZARD_STARTS ||
			  			type == PluginEvent.PEV_CONFIGURATION_WIZARD_COMPLETES ||
			  			type == PluginEvent.PEV_INITIAL_SHARING_COMPLETE ||
			  			type == PluginEvent.PEV_INITIALISATION_UI_COMPLETES ||
			  			type == PluginEvent.PEV_ALL_PLUGINS_INITIALISED ){

			  		plugin_event_history.add( ev );

			  		if ( plugin_event_history.size() > 1024 ){

			  			Debug.out( "Plugin event history too large!!!!" );

			  			plugin_event_history.remove( 0 );
			  		}
			  	}

			  	List plugin_interfaces;

			  	synchronized( s_plugin_interfaces ){

			  		plugin_interfaces = new ArrayList( s_plugin_interfaces );
			  	}

			  	for (int i=0;i<plugin_interfaces.size();i++){

			  		try{
			  			((PluginInterfaceImpl)plugin_interfaces.get(i)).firePluginEventSupport(ev);

			  		}catch(Throwable e ){

			  			Debug.printStackTrace(e);
			  		}
			  	}

			 	if ( default_plugin != null ){

			  		default_plugin.firePluginEventSupport(ev);
			  	}
			  }
		 });
  }

  private void
  waitForEvents()
  {
	  if ( async_dispatcher.isDispatchThread()){

		  Debug.out( "Deadlock - recode this monkey boy" );

	  }else{

		  final AESemaphore sem = new AESemaphore( "waiter" );

		  async_dispatcher.dispatch(
				new AERunnable()
				{
					@Override
					public void
					runSupport()
					{
						sem.release();
					}
				});

		  if ( !sem.reserve( 10*1000 )){

			  Debug.out( "Timeout waiting for event dispatch" );
		  }

	  }
  }

  public static void
  fireEvent(
  	int		type )
  {
  	singleton.fireEventSupport(type, null);
  }

  public static void
  fireEvent(
	int		type,
	Object	value )
  {
  	singleton.fireEventSupport(type, value);
  }

  public static void
  waitForPluginEvents()
  {
	  singleton.waitForEvents();
  }

  public void
  initialisationComplete()
  {
  	initialisation_complete	= true;

  	UIManagerImpl.initialisationComplete();

	List plugin_interfaces;

	synchronized( s_plugin_interfaces ){

		plugin_interfaces = new ArrayList( s_plugin_interfaces );
	}

  	for (int i=0;i<plugin_interfaces.size();i++){

  		((PluginInterfaceImpl)plugin_interfaces.get(i)).initialisationComplete();
  	}

  		// keep this last as there are things out there that rely on the init complete of the
  		// default interface meaning that everything else is complete and informed complete

  	if ( default_plugin != null ){

  		default_plugin.initialisationComplete();
  	}
  }

  protected boolean
  isInitialisationComplete()
  {
  	return( initialisation_complete );
  }


  public static List<PluginInterfaceImpl> getPluginInterfaces() {
  	return singleton.getPluginInterfacesSupport( false );
  }

  private List<PluginInterfaceImpl> getPluginInterfacesSupport( boolean expect_partial_result ) {

	if ( !expect_partial_result ){

		checkPluginsInitialised();
	}

	synchronized( s_plugin_interfaces ){

		return(new ArrayList<>(s_plugin_interfaces));
	}
  }

  public PluginInterface[]
  getPlugins()
  {
	  return( getPlugins( false ));
  }

  public PluginInterface[]
  getPlugins(
		boolean	expect_partial_result )
  {
  	List	pis = getPluginInterfacesSupport( expect_partial_result );

  	PluginInterface[]	res = new 	PluginInterface[pis.size()];

  	pis.toArray(res);

  	return( res );
  }

  protected PluginManager
  getPluginManager()
  {
  	return( plugin_manager );
  }

  protected PluginInterfaceImpl
  getPluginFromClass(
  	Class	cla )
  {
  	return( getPluginFromClass( cla.getName()));
  }

  protected PluginInterfaceImpl
  getPluginFromClass(
  	String	class_name )
  {
	List plugin_interfaces;

	synchronized( s_plugin_interfaces ){

		plugin_interfaces = new ArrayList( s_plugin_interfaces );
	}

  	for (int i=0;i<plugin_interfaces.size();i++){

  		PluginInterfaceImpl	pi = (PluginInterfaceImpl)plugin_interfaces.get(i);

  		if ( pi.getPlugin().getClass().getName().equals( class_name )){

  			return( pi );
  		}
  	}

  		// fall back to the loaded but not-yet-initialised list

  	for (int i=0;i<loaded_pi_list.size();i++){

  		List	l = (List)loaded_pi_list.get(i);

  		for (int j=0;j<l.size();j++){

  			PluginInterfaceImpl	pi = (PluginInterfaceImpl)l.get(j);

  			if ( pi.getPlugin().getClass().getName().equals( class_name )){

  	  			return( pi );
  	  		}
  		}
  	}

  	return( null );
  }


	@Override
	public void
	generate(
		IndentWriter		writer )
	{
		writer.println( "Plugins" );

		try{
			writer.indent();

			List plugin_interfaces;

			synchronized( s_plugin_interfaces ){

				plugin_interfaces = new ArrayList( s_plugin_interfaces );
			}

		 	for (int i=0;i<plugin_interfaces.size();i++){

		  		PluginInterfaceImpl	pi = (PluginInterfaceImpl)plugin_interfaces.get(i);

		  		pi.generateEvidence( writer );
		 	}

		}finally{

			writer.exdent();
		}
	}

	/*
	protected static void
	setVerified(
		PluginInterfaceImpl		pi,
		Plugin					plugin,
		boolean					v,
		boolean					bad )

		throws PluginException
	{
		Object[] existing = (Object[])verified_plugin_holder.setValue( pi, new Object[]{ plugin, v });

		if ( existing != null && ( existing[0] != plugin || (Boolean)existing[1] != v )){

			throw( new PluginException( "Verified status change not permitted" ));
		}

  	  	if ( bad && !DISABLE_PLUGIN_VERIFICATION ){

		  throw( new RuntimeException( "Plugin verification failed" ));
	  }
	}

	public static boolean
	isVerified(
		PluginInterface		pi,
		Plugin				plugin )
	{
		if ( !( pi instanceof PluginInterfaceImpl )){

			return( false );
		}

		VerifiedPluginHolder holder = verified_plugin_holder;

		if ( holder.getClass() != VerifiedPluginHolder.class ){

			Debug.out( "class mismatch" );

			return( false );
		}

		if ( DISABLE_PLUGIN_VERIFICATION ){

			Debug.out( " **************************** VERIFICATION DISABLED ******************" );

			return( true );
		}

		Object[] ver = (Object[])verified_plugin_holder.getValue( pi );

		return( ver != null && ver[0] == plugin && (Boolean)ver[1] );
	}

	public static boolean
	isCoreOrVerifiedPlugin()
	{
		Class<?>[] stack = SESecurityManager.getClassContext();

		ClassLoader core = PluginInitializer.class.getClassLoader();

		PluginInitializer singleton = peekSingleton();

		PluginInterface[] pis = singleton==null?new PluginInterface[0]:singleton.getPlugins();

		Set<ClassLoader>	ok_loaders = new HashSet<>();

		ok_loaders.add( core );

		for ( Class<?> c: stack ){

			ClassLoader cl = c.getClassLoader();

			if ( cl != null && !ok_loaders.contains( cl )){

				boolean ok = false;

				for ( PluginInterface pi: pis ){

					Plugin plugin = pi.getPlugin();

					if ( plugin.getClass().getClassLoader() == cl ){

						if ( isVerified( pi, plugin )){

							ok_loaders.add( cl );

							ok = true;

							break;
						}
					}
				}

				if ( !ok ){

					Debug.out( "Class " + c.getCanonicalName() + " with loader " + cl + " isn't trusted" );

					return( false );
				}
			}
		}

		return( true );
	}

	
	private static final class
	VerifiedPluginHolder
	{
		private static final Object	NULL_VALUE = new Object();

		private volatile boolean initialised;

		private AESemaphore	request_sem = new AESemaphore( "ValueHolder" );

		private List<Object[]>	request_queue = new ArrayList<>();

		private
		VerifiedPluginHolder()
		{
			Class[] context = SESecurityManager.getClassContext();

			if ( context.length == 0 ){

				return;
			}

			if ( context[2] != PluginInitializer.class ){

				Debug.out( "Illegal operation" );

				return;
			}

			AEThread2 t =
				new AEThread2( "PluginVerifier" )
				{
					@Override
					public void
					run()
					{
						Map<Object,Object> values = new IdentityHashMap<>();

						while( true ){

							request_sem.reserve();

							Object[] req;

							synchronized( request_queue ){

								req = request_queue.remove(0);
							}

							if ( req[1] == null ){

								req[1] = values.get( req[0] );

							}else{

								Object existing = values.get( req[0] );

								if ( req[1] == NULL_VALUE ){

									req[1] = existing;

									values.remove( req[0] );

								}else{
									if ( existing != null){

										req[1] = existing;

									}else{

										values.put( req[0], req[1] );
									}
								}
							}

							((AESemaphore)req[2]).release();
						}
					}
				};

			t.start();

			initialised = true;
		}

		public Object
		removeValue(
			Object	key )
		{
			if ( !initialised ){

				return( null );
			}

			AESemaphore sem = new AESemaphore( "ValueHolder:remove" );

			Object[] request = new Object[]{ key, NULL_VALUE, sem };

			synchronized( request_queue ){

				request_queue.add( request );
			}

			request_sem.release();

			sem.reserve();

			return(request[1]);
		}

		public Object
		setValue(
			Object	key,
			Object	value )
		{
			if ( !initialised ){

				return( null );
			}

			AESemaphore sem = new AESemaphore( "ValueHolder:set" );

			Object[] request = new Object[]{ key, value, sem };

			synchronized( request_queue ){

				request_queue.add( request );
			}

			request_sem.release();

			sem.reserve();

			return(request[1]);
		}

		public Object
		getValue(
			Object	key )
		{
			if ( !initialised ){

				return( null );
			}

			AESemaphore sem = new AESemaphore( "ValueHolder:get" );

			Object[] request = new Object[]{ key, null, sem };

			synchronized( request_queue ){

				request_queue.add( request );
			}

			request_sem.release();

			sem.reserve();

			return( request[1] );
		}
	}
	*/
}
