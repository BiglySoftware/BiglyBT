/*
 * File    : PluginManagerImpl.java
 * Created : 14-Dec-2003
 * By      : parg
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

/**
 * @author parg
 *
 */

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemProperties;
import com.biglybt.pif.Plugin;
import com.biglybt.pif.PluginException;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.PluginManager;
import com.biglybt.pif.installer.PluginInstaller;
import com.biglybt.pifimpl.local.installer.PluginInstallerImpl;

public class
PluginManagerImpl
	extends PluginManager
{
	protected static boolean	running		= false;

	private static final boolean GET_PI_METHODS_OPERATIONAL_FLAG_DEFAULT = true;

	protected static PluginManagerImpl	singleton;
	protected static AEMonitor			class_mon	= new AEMonitor( "PluginManager");

	protected static Core core;

	protected static PluginManagerImpl
	getSingleton(
		PluginInitializer	pi )
	{
		try{
			class_mon.enter();

			if ( singleton == null ){

				singleton = new PluginManagerImpl( pi );
			}

			return( singleton );

		}finally{

			class_mon.exit();
		}
	}

	/**
	 * Starts client.
	 * <p>
	 * With the exception of null ui, method does not return until client is closed.
	 *
	 * @param ui "swt", "console", "telnet", etc
	 * @param properties
	 */
	public static void
	startClient(
			String			ui,
			Properties	properties,
			final StartClientListener startClientListener )
			throws PluginException
	{
		try{
			class_mon.enter();

			if ( running ){

				throw( new RuntimeException( Constants.APP_NAME + " is already running"));
			}

			running	= true;

		}finally{

			class_mon.exit();
		}

		String	config_dir = (String)properties.get( PR_USER_DIRECTORY );

		if ( config_dir != null ){

			System.setProperty(SystemProperties.SYSPROP_CONFIG_PATH, config_dir );
		}

		String	user_dir = (String)properties.get( PR_APP_DIRECTORY );

		if ( user_dir != null ){

			System.setProperty(SystemProperties.SYSPROP_INSTALL_PATH, user_dir );
			System.setProperty( "user.dir", user_dir );
		}

		String	doc_dir = (String)properties.get( PR_DOC_DIRECTORY );

		if ( doc_dir != null ){

			System.setProperty(SystemProperties.SYSPROP_DOC_PATH, doc_dir );
		}


		String	disable_native = (String)properties.get( PR_DISABLE_NATIVE_SUPPORT );

		if ( disable_native != null && disable_native.equalsIgnoreCase( "true" )){

			System.setProperty(SystemProperties.SYSPROP_PLATFORM_MANAGER_DISABLE, "true" );
		}

		if (startClientListener != null) {
			CoreFactory.addCoreRunningListener(new CoreRunningListener() {
				@Override
				public void coreRunning(Core core) {
					startClientListener.clientStarted(core.getPluginManager());
				}
			});
		}

			// there's a small window here when an immediate "stop" wouldn't work coz
			// this code would carry on after the stop and start. However, can't easily
			// fix this here...


		if ( ui == null ){

				// can't invoke directly as the ui.common stuff isn't part of the core distribution
				// com.biglybt.ui.common.Main.main( new String[]{"--ui=console"});

			try{

				core = CoreFactory.create();

				core.start();

			}catch( Throwable e ){

				Debug.printStackTrace( e );

					// some idiot (me) forgot to add the exception to the i/f and now we
					// can't add it as is stuffs existing plugins...

				throw( new PluginException( Constants.APP_NAME + " failed to start", e ));
			}
		}else {

			// Most likely, a plugin is calling this from their main(), which
			// will not be using our primary class loader.  Which means we already
			// have some core classes initialized on it, making it too late to
			// switch. (For example, aereg.dll will already be loaded, and the class
			// loading switch will cause an exception when trying to laod it again)
			System.setProperty("USE_OUR_PRIMARYCLASSLOADER", "0");

			String	mi = (String)properties.get( PluginManager.PR_MULTI_INSTANCE );

			if ( mi != null && mi.equalsIgnoreCase("true")){

				System.setProperty( PluginManager.PR_MULTI_INSTANCE, "true" );
			}

			try{
				Class.forName( "com.biglybt.ui.Main" ).getMethod(
					"main", new Class[]{ String[].class } ).invoke(
						null, (Object) new String[]{ "--ui=" + ui  });

			}catch( Throwable e ){

				throw( new PluginException( "Main method invocation failed", e ));
			}
		}
	}

	public static void
	stopClient()

		throws PluginException
	{
		try{
			class_mon.enter();

			if ( !running ){

				throw( new RuntimeException( Constants.APP_NAME + " is not running"));
			}

			try{
				core.requestStop();

			}catch( Throwable e ){

				throw( new PluginException( "PluginManager: " + Constants.APP_NAME +  " close action failed", e));
			}

			running	= false;

		}finally{

			class_mon.exit();
		}
	}

	public static void
	restartClient()

		throws PluginException
	{
		if ( !running ){

			throw( new RuntimeException( Constants.APP_NAME + " is not running"));
		}

		try{
			core.requestRestart();

		}catch( Throwable e ){

			throw( new PluginException( "PluginManager: " + Constants.APP_NAME + " restart action failed", e));
		}

		running	= false;
	}

		/**
		 * When AZ is started directly (i.e. not via a plugin) this method is called
		 * so that the running state is correctly understood
		 * @param type
		 */

	public static void
	setStartDetails(
		Core _core )
	{
		core = _core;

		running			= true;
	}

	public static void
	registerPlugin(
			Class<? extends Plugin>		plugin_class )
	{
		PluginInitializer.queueRegistration( plugin_class );
	}

	public static void
	registerPlugin(
		Plugin plugin,
		String		id,
		String		config_key )
	{
		PluginInitializer.queueRegistration( plugin, id, config_key );
	}

	@Override
	public PluginInterface getPluginInterfaceByID(String id) {
		return getPluginInterfaceByID(id, GET_PI_METHODS_OPERATIONAL_FLAG_DEFAULT);
	}

	@Override
	public PluginInterface
	getPluginInterfaceByID(
		String		id,
		boolean     operational)
	{
		PluginInterface[]	p = getPluginInterfaces();

		for (int i=0;i<p.length;i++){

			if ( p[i].getPluginID().equalsIgnoreCase( id )){

				if (operational && !p[i].getPluginState().isOperational()) {return null;}

				return( p[i]);
			}
		}

		return( null );
	}

	@Override
	public PluginInterface getPluginInterfaceByClass(Class c) {
		return getPluginInterfaceByClass(c, GET_PI_METHODS_OPERATIONAL_FLAG_DEFAULT);
	}

	@Override
	public PluginInterface
	getPluginInterfaceByClass(
		Class		c,
		boolean     operational)
	{
		PluginInterface[]	p = getPluginInterfaces();

		for (int i=0;i<p.length;i++){

			if ( p[i].getPlugin().getClass().equals( c )){

				if (operational && !p[i].getPluginState().isOperational()) {return null;}

				return( p[i]);
			}
		}

		return( null );
	}

	@Override
	public PluginInterface getPluginInterfaceByClass(String class_name) {
		return getPluginInterfaceByClass(class_name, GET_PI_METHODS_OPERATIONAL_FLAG_DEFAULT);
	}

	@Override
	public PluginInterface
	getPluginInterfaceByClass(
		String		class_name,
		boolean     operational)
	{
		PluginInterface[]	p = getPluginInterfaces();

		for (int i=0;i<p.length;i++){

			if ( p[i].getPlugin().getClass().getName().equals( class_name )){

				if (operational && !p[i].getPluginState().isOperational()) {return null;}

				return( p[i]);
			}
		}

		return( null );
	}

	@Override
	public PluginInterface[]
	getPluginInterfaces()
	{
		List	l = PluginInitializer.getPluginInterfaces();

		PluginInterface[]	res = new PluginInterface[l.size()];

		l.toArray(res);

		return( res );
	}

	@Override
	public PluginInterface
	getDefaultPluginInterface()
	{
		return( PluginInitializer.getDefaultInterface());
	}

	protected PluginInitializer		pi;

	protected
	PluginManagerImpl(
		PluginInitializer		_pi )
	{
		pi		= _pi;

			// pull in the installer here so it can register its handlers early

		getPluginInstaller();
	}

	@Override
	public PluginInterface[]
	getPlugins()
	{
		return( pi.getPlugins());
	}

	@Override
	public PluginInterface[]
	getPlugins(
		boolean expect_partial_result )
	{
		return( pi.getPlugins( expect_partial_result ));
	}

	@Override
	public void
	firePluginEvent(
		int	ev )
	{
		PluginInitializer.fireEvent( ev );
	}

	@Override
	public PluginInstaller
	getPluginInstaller()
	{
		return( PluginInstallerImpl.getSingleton(this));
	}

	@Override
	public void refreshPluginList(boolean initialise) {
		List loadedPlugins = pi.loadPlugins(pi.getCore(), true, true, false, initialise);
		for (Iterator iter = loadedPlugins.iterator(); iter.hasNext();) {
			PluginInterfaceImpl plugin = (PluginInterfaceImpl) iter.next();

			// If the plugin is disabled, it will just get added to the list
			// of plugins, but won't initialise.
			if (!plugin.getPluginState().isOperational()) {
				try {
					pi.reloadPlugin(plugin, false, initialise);
				} catch (PluginException e) {
					// TODO Auto-generated catch block
					Debug.printStackTrace(e);
				}
			}
		}
	}

	@Override
	public boolean
	isSilentRestartEnabled()
	{
		PluginInterface[] pis = pi.getPlugins();

		for ( int i=0;i<pis.length;i++ ){

			if ( pis[i].getPluginProperties().getProperty("plugin.silentrestart.disabled", "" ).equalsIgnoreCase( "true" )){

				return( false );
			}
		}

		return( true );
	}

	@Override
	public boolean
	isInitialized()
	{
		return( pi.isInitialized());
	}

	@Override
	public void
	executeCloseAction(
		String action)

		throws PluginException
	{
		if ( core == null ){

			throw( new PluginException( Constants.APP_NAME + " is not running"));
		}

		try{
			core.executeCloseAction( action, "plugin requested" );

		}catch( Throwable e ){

			throw( new PluginException( "PluginManager: " + Constants.APP_NAME + " restart action failed", e));
		}
	}

	@Override
	public List<PluginInterface>
	getPluginsWithMethod(
		String		name,
		Class<?>[]	parameters )
	{
		List<PluginInterface>	result = new ArrayList<>();

		List<PluginInterfaceImpl>	pis = PluginInitializer.getPluginInterfaces();

		for ( PluginInterfaceImpl pi: pis ){

			if ( pi.getIPC().canInvoke( name, parameters )){

				result.add( pi );
			}
		}

		return( result );
	}
}
