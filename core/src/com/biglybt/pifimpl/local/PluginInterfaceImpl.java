/*
 * File    : PluginInterfaceImpl.java
 * Created : 12 nov. 2003
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
import java.util.*;

import com.biglybt.core.CoreComponent;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.util.*;
import com.biglybt.pif.*;
import com.biglybt.pif.clientid.ClientIDManager;
import com.biglybt.pif.ddb.DistributedDatabase;
import com.biglybt.pif.dht.mainline.MainlineDHTManager;
import com.biglybt.pif.download.DownloadManager;
import com.biglybt.pif.ipfilter.IPFilter;
import com.biglybt.pif.logging.Logger;
import com.biglybt.pif.messaging.MessageManager;
import com.biglybt.pif.network.ConnectionManager;
import com.biglybt.pif.platform.PlatformManager;
import com.biglybt.pif.sharing.ShareException;
import com.biglybt.pif.sharing.ShareManager;
import com.biglybt.pif.torrent.TorrentManager;
import com.biglybt.pif.tracker.Tracker;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.update.UpdateManager;
import com.biglybt.pif.utils.ShortCuts;
import com.biglybt.pif.utils.Utilities;
import com.biglybt.pifimpl.local.clientid.ClientIDManagerImpl;
import com.biglybt.pifimpl.local.ddb.DDBaseImpl;
import com.biglybt.pifimpl.local.dht.mainline.MainlineDHTManagerImpl;
import com.biglybt.pifimpl.local.download.DownloadManagerImpl;
import com.biglybt.pifimpl.local.ipc.IPCInterfaceImpl;
import com.biglybt.pifimpl.local.ipfilter.IPFilterImpl;
import com.biglybt.pifimpl.local.logging.LoggerImpl;
import com.biglybt.pifimpl.local.messaging.MessageManagerImpl;
import com.biglybt.pifimpl.local.network.ConnectionManagerImpl;
import com.biglybt.pifimpl.local.sharing.ShareManagerImpl;
import com.biglybt.pifimpl.local.torrent.TorrentManagerImpl;
import com.biglybt.pifimpl.local.tracker.TrackerImpl;
import com.biglybt.pifimpl.local.ui.UIManagerImpl;
import com.biglybt.pifimpl.local.update.UpdateManagerImpl;
import com.biglybt.pifimpl.local.utils.ShortCutsImpl;
import com.biglybt.pifimpl.local.utils.UtilitiesImpl;
import com.biglybt.platform.PlatformManagerFactory;



/**
 * @author Olivier
 *
 */
public final class
PluginInterfaceImpl
	implements PluginInterface, CoreComponent
{
	private static final LogIDs LOGID = com.biglybt.core.logging.LogIDs.PLUGIN;

  private Plugin				plugin;
  private PluginInitializer		initialiser;
  private Object				initialiser_key;
  protected ClassLoader			class_loader;

  private CopyOnWriteList<PluginListener>		listeners 				= new CopyOnWriteList<>();
  private Set<PluginListener>					init_complete_fired_set	= new HashSet<>();

  private CopyOnWriteList<PluginEventListener>		event_listeners	= new CopyOnWriteList<>();
  private String				key;
  private String 				pluginConfigKey;
  private Properties 			props;
  private String 				pluginDir;
  private PluginConfigImpl		config;
  private String				plugin_version;
  private Logger				logger;
  private IPCInterfaceImpl		ipc_interface;
  protected List				children		= new ArrayList();
  private final PluginStateImpl       state;

  /**
   * This is the plugin ID value we were given when we were created.
   *
   * We might use it, but it depends what value is the plugins properties
   * (which will override this value).
   */
  private String				given_plugin_id;

  /**
   * We store this value as soon as someone calls getPluginID(), meaning
   * we will return a consistent value for the plugin's lifetime.
   */
  private String                plugin_id_to_use;

  protected
  PluginInterfaceImpl(
  		Plugin				_plugin,
  		PluginInitializer	_initialiser,
		Object				_initialiser_key,
		ClassLoader			_class_loader,
		List<File>			_verified_files,
		String 				_key,
		Properties 			_props,
		String 				_pluginDir,
		String				_plugin_id,
		String				_plugin_version )

  	throws PluginException
  {
	  	// check we're being created by the core

	  /*
	  StackTraceElement[] stack = Thread.currentThread().getStackTrace();

	  int	pos = 0;

	  while( !stack[pos].getClassName().equals( PluginInterfaceImpl.class.getName())){

		  pos++;
	  }

	  String caller_class = stack[pos+1].getClassName();

	  if ( !(	caller_class.equals( "com.biglybt.pifimpl.local.PluginInitializer" ) ||
			  	caller_class.equals( "com.biglybt.pifimpl.local.PluginInterfaceImpl" ))){

		  throw( new PluginException( "Invalid caller" ));
	  }

	  	// check we haven't been subclassed

	  String class_name = getClass().getCanonicalName();

	  if ( class_name == null || !class_name.equals( "com.biglybt.pifimpl.local.PluginInterfaceImpl" )){

		  throw( new PluginException( "Subclassing not permitted" ));
	  }
	  */
	  
	  plugin				= _plugin;
	  initialiser			= _initialiser;
	  initialiser_key		= _initialiser_key;
	  class_loader			= _class_loader;
	  key					= _key;
	  pluginConfigKey 		= "Plugin." + _key;
	  props 				= new propertyWrapper(_props );
	  pluginDir 			= _pluginDir;
	  config 				= new PluginConfigImpl(this,pluginConfigKey);
	  given_plugin_id    	= _plugin_id;
	  plugin_version		= _plugin_version;
	  ipc_interface			= new IPCInterfaceImpl( initialiser, plugin );
	  state               	= new PluginStateImpl(this, initialiser);

	  /*
	  boolean verified 	= false;
	  boolean bad		= false;

	  if ( _plugin_id.endsWith( "_v" )){

		  if ( plugin.getClass() == FailedPlugin.class ){

			  verified = true;

		  }else{
		      if ( _verified_files != null  ){

	    		  File jar = FileUtil.getJarFileFromClass( plugin.getClass());

	    		  if ( jar != null ){

	    			  for ( File file: _verified_files ){

	    				  if ( file.equals( jar )){

	    					  verified = true;
	    				  }
	    			  }
	    		  }
		      }
		  }

	      if ( !verified ){

	    	  bad = true;
	      }
	  }

	  PluginInitializer.setVerified( this, plugin, verified, bad );
	  */
  }

  	@Override
	  public Plugin
	getPlugin()
	{
  		return( plugin );
	}

	public Object
	getInitializerKey()
	{
  		return( initialiser_key );
  	}

  	@Override
	  public PluginManager
	getPluginManager()
	{
  		return( initialiser.getPluginManager());
  	}

  	@Override
	  public String getApplicationName() {
  		return Constants.APP_NAME;
  	}

	@Override
	public String
	getAzureusName()
	{
		return( Constants.BIGLYBT_NAME );
	}

	@Override
	public String
	getApplicationVersion()
	{
		return( Constants.BIGLYBT_VERSION );
	}

	public void
  setPluginName(
  	String	name )
  {
  	props.put( "plugin.name", name );
  }

  @Override
  public String getPluginName()
  {
  	String	name = null;

  	if ( props != null ){

  		name = (String)props.get( "plugin.name");
  	}

  	if ( name == null ){

  		try{

  			name = new File(pluginDir).getName();

  		}catch( Throwable e ){

  		}
  	}

  	if ( name == null || name.length() == 0 ){

  		name = plugin.getClass().getName();
  	}

  	return( name );
  }

  public void
  setPluginVersion(
  	String	version )
  {
	props.put( "plugin.version", version );
  }

  @Override
  public String
  getPluginVersion()
  {
	String	version = (String)props.get("plugin.version");

  	if ( version == null ){

  		version = plugin_version;
  	}

  	return( version );
  }

  @Override
  public String
  getPluginID()
  {
	  String id = (String)props.get("plugin.id");

	  // hack alert - azupdater needs to change its plugin id due to general hackage

	  if ( id != null && id.equals( "azupdater" )){

		  plugin_id_to_use = id;
	  }

	  if (plugin_id_to_use != null) {return plugin_id_to_use;}

	// Calculate what plugin ID value to use - look at the properties file
	// first, and if that isn't correct, base it on the given plugin ID
	// value we were given.

  	if (id == null) {id = given_plugin_id;}
  	if (id == null) {id = "<none>";}

  	plugin_id_to_use = id;
  	return plugin_id_to_use;
  }

	@Override
  public Properties getPluginProperties()
  {
    return(props);
  }

  @Override
  public String getPluginDirectoryName() {
    return pluginDir;
  }

  @Override
  public String getPerUserPluginDirectoryName(){
	String name;
	if ( pluginDir == null ){
		name = getPluginID();
	}else{
		name = new File( pluginDir).getName();
	}

	String str = new File(new File(SystemProperties.getUserPath(),"plugins"),name).getAbsolutePath();

	if ( pluginDir == null ){

		return( str );
	}

	try{
		if ( new File( pluginDir ).getCanonicalPath().equals( new File( str ).getCanonicalPath())){

			return( pluginDir );
		}
	}catch( Throwable e ){

	}

	return( str );
  }

  public void
  setPluginDirectoryName(
  	String		name )
  {
  	initialiser_key	= new File(name);

  	pluginDir	= name;
  }

  @Override
  public PluginConfig getPluginconfig() {
    return config;
  }


	public String
  getPluginConfigKey()
  {
  	return( pluginConfigKey );
  }

  @Override
  public Tracker getTracker() {
  	return( TrackerImpl.getSingleton());
  }

  @Override
  public ShareManager
  getShareManager()

  	throws ShareException
  {
  	return( ShareManagerImpl.getSingleton());
  }

  @Override
  public DownloadManager
  getDownloadManager()
  {
  	return( DownloadManagerImpl.getSingleton(initialiser.getCore()));
  }

  @Override
  public MainlineDHTManager getMainlineDHTManager() {
	  return new MainlineDHTManagerImpl(initialiser.getCore());
  }

  @Override
  public TorrentManager
  getTorrentManager()
  {
  	return( TorrentManagerImpl.getSingleton().specialise( this ));
  }

  @Override
  public Logger getLogger()
  {
  	if ( logger == null ){

  		logger = new LoggerImpl( this );
  	}

  	return( logger );
  }

  @Override
  public IPFilter
  getIPFilter()
  {
  	return( new IPFilterImpl());
  }

  @Override
  public Utilities
  getUtilities()
  {
  	return( new UtilitiesImpl( initialiser.getCore(), this ));
  }

  @Override
  public ShortCuts
  getShortCuts()
  {
  	return( new ShortCutsImpl(this));
  }

  @Override
  public UIManager
  getUIManager()
  {
  	return( new UIManagerImpl( this ));
  }

  @Override
  public UpdateManager
  getUpdateManager()
  {
  	return( UpdateManagerImpl.getSingleton( initialiser.getCore()));
  }


  protected void
  unloadSupport()
  {
	  ipc_interface.unload();

	  UIManagerImpl.unload( this );
  }

	@Override
	public boolean
	isInitialisationThread()
	{
		return( initialiser.isInitialisationThread());
	}

	 @Override
	 public ClientIDManager
	 getClientIDManager()
	 {
	 	return( ClientIDManagerImpl.getSingleton());
	 }


   @Override
   public ConnectionManager getConnectionManager() {
     return ConnectionManagerImpl.getSingleton( initialiser.getCore());
   }

   @Override
   public MessageManager getMessageManager() {
     return MessageManagerImpl.getSingleton( initialiser.getCore() );
   }


   @Override
   public DistributedDatabase
   getDistributedDatabase()
   {
   	return( DDBaseImpl.getSingleton(initialiser.getCore()));
   }

   @Override
   public PlatformManager
   getPlatformManager()
   {
	   return( PlatformManagerFactory.getPlatformManager());
   }

  protected void
  initialisationComplete()
  {
	  Iterator<PluginListener> it = listeners.iterator();

	  while( it.hasNext()){

		  try{
			  fireInitComplete( it.next());

		  }catch( Throwable e ){

			  Debug.printStackTrace( e );
		  }
	  }

	  for (int i=0;i<children.size();i++){

		  ((PluginInterfaceImpl)children.get(i)).initialisationComplete();
	  }
  }

  protected void
  fireInitComplete(
	PluginListener		listener )
  {
	  synchronized( init_complete_fired_set ){

		  if ( init_complete_fired_set.contains( listener )){

			  return;
		  }

		  init_complete_fired_set.add( listener );
	  }

	  try {
	  	listener.initializationComplete();
	  } catch (Exception e) {
	  	Debug.out(e);
	  }
  }

  protected void
  closedownInitiated()
  {
	  Iterator it = listeners.iterator();

	  while( it.hasNext()){

		  try{
			  ((PluginListener)it.next()).closedownInitiated();

		  }catch( Throwable e ){

			  Debug.printStackTrace( e );
		  }
	  }

	  for (int i=0;i<children.size();i++){

		  ((PluginInterfaceImpl)children.get(i)).closedownInitiated();
	  }
  }

  protected void
  closedownComplete()
  {
	  Iterator it = listeners.iterator();

	  while( it.hasNext()){

		  try{
			  ((PluginListener)it.next()).closedownComplete();

		  }catch( Throwable e ){

			  Debug.printStackTrace( e );
		  }
	  }

	  for (int i=0;i<children.size();i++){

		  ((PluginInterfaceImpl)children.get(i)).closedownComplete();
	  }
  }

  @Override
  public ClassLoader
  getPluginClassLoader()
  {
  	return( class_loader );
  }

	@Override
	public PluginInterface
	getLocalPluginInterface(
		Class		plugin_class,
		String		id )

		throws PluginException
	{
		try{
			Plugin	p = (Plugin)plugin_class.newInstance();

			// Discard plugin.id from the properties, we want the
			// plugin ID we create to take priority - not a value
			// from the original plugin ID properties file.
			Properties local_props = new Properties(props);
			local_props.remove("plugin.id");


	 		if( id.endsWith( "_v" )){

	  			throw( new Exception( "Verified plugins must be loaded from a jar" ));
	  		}

			PluginInterfaceImpl pi =
				new PluginInterfaceImpl(
			  		p,
			  		initialiser,
					initialiser_key,
					class_loader,
					null,
					key + "." + id,
					local_props,
					pluginDir,
					getPluginID() + "." + id,
					plugin_version );

			initialiser.fireCreated( pi );

			p.initialize( pi );

			children.add( pi );

			return( pi );

		}catch( Throwable e ){

			if ( e instanceof PluginException ){

				throw((PluginException)e);
			}

			throw( new PluginException( "Local initialisation fails", e ));
		}
	}

	 @Override
	 public IPCInterfaceImpl
	 getIPC()
	 {
		 return( ipc_interface );
	 }


	// Not exposed in the interface.
	void setAsFailed() {
		getPluginState().setDisabled(true);
		state.failed = true;
	}

	protected void
	destroy()
	{
		class_loader = null;

			// unhook the reference to the plugin but leave with a valid reference in case
			// something tries to use it

		plugin = new FailedPlugin( "Plugin '" + getPluginID() + "' has been unloaded!", null );
	}

  @Override
  public void
  addListener(
  	PluginListener	l )
  {
  	listeners.add(l);

  	if ( initialiser.isInitialisationComplete()){

  		fireInitComplete( l );
  	}
  }

  @Override
  public void
  removeListener(
  	final PluginListener	l )
  {
  	listeners.remove(l);

  		// we want to remove this ref, but there is a *small* chance that there's a parallel thread firing the complete so
  		// decrease chance of hanging onto unwanted ref

  	new DelayedEvent(
  		"PIL:clear", 10000,
  		new AERunnable()
  		{
  			@Override
			  public void
  			runSupport()
  			{
  				synchronized( init_complete_fired_set ){

  					init_complete_fired_set.remove(l);
  				}
  			}
  		});
  }

  @Override
  public void
  addEventListener(
	  final PluginEventListener l )
  {
	  initialiser.runPEVTask(
		 new AERunnable()
		 {
			 @Override
			 public void
			 runSupport()
			 {
				 List<PluginEvent> events = initialiser.getPEVHistory();

				 for ( PluginEvent event: events ){

					 try{
						 l.handleEvent( event );

					 }catch( Throwable e ){

						 Debug.out( e );
					 }
				 }
				 event_listeners.add(l);
			 }
		 });
  }

  @Override
  public void
  removeEventListener(
	  final PluginEventListener	l )
  {
	  initialiser.runPEVTask(
		 new AERunnable()
		 {
			 @Override
			 public void
			 runSupport()
			 {
				  event_listeners.remove(l);
			 }
		 });
  }

  @Override
  public void
  firePluginEvent(
	  final PluginEvent		event )
  {
	  initialiser.runPEVTask(
		 new AERunnable()
		 {
			 @Override
			 public void
			 runSupport()
			 {
				 firePluginEventSupport( event );
			 }
		 });
  }

  protected void
  firePluginEventSupport(
	  PluginEvent		event )
  {
	  Iterator<PluginEventListener> it = event_listeners.iterator();

	  while( it.hasNext()){

		  try{
			  PluginEventListener listener = it.next();

			  listener.handleEvent( event );

		  }catch( Throwable e ){

			  Debug.printStackTrace( e );
		  }
	  }

	  for (int i=0;i<children.size();i++){

		  ((PluginInterfaceImpl)children.get(i)).firePluginEvent(event);
	  }
  }

	protected void
	generateEvidence(
		IndentWriter		writer )
	{
		writer.println( getPluginName());

		try{
			writer.indent();

			writer.println( "id:" + getPluginID() + ",version:" + getPluginVersion());

			String user_dir 	= FileUtil.getUserFile( "plugins" ).toString();
			String shared_dir 	= FileUtil.getApplicationFile( "plugins" ).toString();

			String	plugin_dir = getPluginDirectoryName();

			String	type;
			boolean	built_in = false;

			if ( plugin_dir.startsWith( shared_dir )){

				type = "shared";

			}else	if ( plugin_dir.startsWith( user_dir )){

				type = "per-user";

			}else{

				built_in = true;

				type = "built-in";
			}

			PluginState ps = getPluginState();

			String	info = getPluginconfig().getPluginStringParameter( "plugin.info" );

			writer.println( "type:" + type + ",enabled=" + !ps.isDisabled() + ",load_at_start=" + ps.isLoadedAtStartup() + ",operational=" + ps.isOperational() + (info==null||info.length()==0?"":( ",info=" + info )));

			if ( ps.isOperational()){

				Plugin plugin = getPlugin();

				if ( plugin instanceof AEDiagnosticsEvidenceGenerator ){

					try{
						writer.indent();

						((AEDiagnosticsEvidenceGenerator)plugin).generate( writer );

					}catch( Throwable e ){

						writer.println( "Failed to generate plugin-specific info: " + Debug.getNestedExceptionMessage( e ));

					}finally{

						writer.exdent();
					}
				}
			}else{
				if ( !built_in ){

					File dir = new File( plugin_dir );

					if ( dir.exists()){

						String[] files = dir.list();

						if ( files != null ){

							String	files_str = "";

							for ( String f: files ){

								files_str += (files_str.length()==0?"":", ") + f;
							}

							writer.println( "    files: " + files_str );
						}
					}
				}
			}
		}finally{

			writer.exdent();
		}
	}

	@Override
	public PluginState getPluginState() {
		return this.state;
	}

	PluginStateImpl getPluginStateImpl() {
		return this.state;
	}


  	// unfortunately we need to protect ourselves against the plugin itself trying to set
  	// plugin.version and plugin.id as this screws things up if they get it "wrong".
  	// They should be setting these things in the plugin.properties file
  	// currently the RSSImport plugin does this (version 1.1 sets version as 1.0)

  protected class
  propertyWrapper
  	extends Properties
  {
  	protected boolean	initialising	= true;

  	protected
	propertyWrapper(
		Properties	_props )
	{
  		Iterator it = _props.keySet().iterator();

  		while( it.hasNext()){

  			Object	key = it.next();

  			put( key, _props.get(key));
  		}

  		initialising	= false;
  	}

  	@Override
	  public Object
	setProperty(
		String		str,
		String		val )
	{
  			// if its us then we probably know what we're doing :P

			if (!(plugin.getClass().getName().startsWith("org.gudy")
					|| plugin.getClass().getName().startsWith("com.aelitis.")
					|| plugin.getClass().getName().startsWith("com.biglybt."))) {

	  		if ( str.equalsIgnoreCase( "plugin.id" ) || str.equalsIgnoreCase("plugin.version" )){

	  			if (com.biglybt.core.logging.Logger.isEnabled())
						com.biglybt.core.logging.Logger
								.log(new LogEvent(LOGID, LogEvent.LT_WARNING, "Plugin '"
										+ getPluginName() + "' tried to set property '" + str
										+ "' - action ignored"));

	  			return( null );
	  		}
  		}

  		return( super.setProperty( str, val ));
  	}

  	@Override
	  public Object
	put(
		Object	key,
		Object	value )
	{
			// if its us then we probably know what we're doing :P

		if (!(plugin.getClass().getName().startsWith("org.gudy")
				|| plugin.getClass().getName().startsWith("com.aelitis.")
				|| plugin.getClass().getName().startsWith("com.biglybt."))) {

	 		if ((!initialising ) && key instanceof String ){

	  			String	k_str = (String)key;

	  	 		if ( k_str.equalsIgnoreCase( "plugin.id" ) || k_str.equalsIgnoreCase("plugin.version" )){

	  	 			if (com.biglybt.core.logging.Logger.isEnabled())
							com.biglybt.core.logging.Logger.log(new LogEvent(LOGID,
									LogEvent.LT_WARNING, "Plugin '" + getPluginName()
											+ "' tried to set property '" + k_str
											+ "' - action ignored"));

	  	 			return( null );
	  	 	  	}
	  		}
		}

		return( super.put( key, value ));
	}

  	@Override
	  public Object
	get(
		Object	key )
	{
  		return( super.get(key));
  	}
  }
}
