/*
 * Created on Dec 17, 2013
 * Created by Paul Gardner
 *
 * Copyright 2013 Azureus Software, Inc.  All rights reserved.
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


package com.biglybt.core.proxy.impl;

import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.net.URL;
import java.util.*;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.proxy.AEProxyFactory.PluginHTTPProxy;
import com.biglybt.core.proxy.AEProxyFactory.PluginProxy;
import com.biglybt.core.proxy.AEProxySelectorFactory;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginAdapter;
import com.biglybt.pif.PluginEvent;
import com.biglybt.pif.PluginEventListener;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ipc.IPCInterface;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.plugin.dht.DHTPluginInterface;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.UIFunctionsUserPrompter;

public class
AEPluginProxyHandler
{
	private static final CopyOnWriteList<PluginInterface>		plugins = new CopyOnWriteList<>();

	private static final int			plugin_init_max_wait	= 30*1000;
	private static final AESemaphore 	plugin_init_complete 	= new AESemaphore( "init:waiter" );

	private static boolean	enable_plugin_proxies_with_socks;

	static{
		COConfigurationManager.addAndFireParameterListener(
			"Proxy.SOCKS.disable.plugin.proxies",
			new ParameterListener() {
				@Override
				public void
				parameterChanged(
					String parameterName)
				{
					enable_plugin_proxies_with_socks = !COConfigurationManager.getBooleanParameter( parameterName );
				}
			});
	}
	
	public static void
	initialise(
		Core		core )
	{
		try{
			PluginInterface default_pi = core.getPluginManager().getDefaultPluginInterface();

			default_pi.addEventListener(
					new PluginEventListener()
					{
						@Override
						public void
						handleEvent(
							PluginEvent ev )
						{
							int	type = ev.getType();

							if ( type == PluginEvent.PEV_PLUGIN_OPERATIONAL ){

								pluginAdded((PluginInterface)ev.getValue());
							}
							if ( type == PluginEvent.PEV_PLUGIN_NOT_OPERATIONAL ){

								pluginRemoved((PluginInterface)ev.getValue());
							}
						}
					});

				PluginInterface[] plugins = default_pi.getPluginManager().getPlugins( true );

				for ( PluginInterface pi: plugins ){

					if ( pi.getPluginState().isOperational()){

						pluginAdded( pi );
					}
				}

				default_pi.addListener(
					new PluginAdapter()
					{
						@Override
						public void
						initializationComplete()
						{
							plugin_init_complete.releaseForever();
						}
					});

		}catch( Throwable e ){

			e.printStackTrace();
		}
	}

	private static void
	pluginAdded(
		PluginInterface pi )
	{
		String pid = pi.getPluginID();

		if ( pid.equals( "aznettor" ) || pid.equals( "azneti2phelper" )){

			plugins.add( pi );
		}
	}

	private static void
	pluginRemoved(
		PluginInterface pi )
	{
		String pid = pi.getPluginID();

		if ( pid.equals( "aznettor" ) || pid.equals( "azneti2phelper" )){

			plugins.remove( pi );
		}
	}

	private static boolean
	waitForPlugins(
		int		max_wait )
	{
		if ( PluginInitializer.isInitThread()){

			Debug.out( "Hmm, rework this" );
		}

		return( plugin_init_complete.reserve( max_wait ));
	}

	private static final Map<Proxy,WeakReference<PluginProxyImpl>>	proxy_map 	= new IdentityHashMap<>();
	private static final CopyOnWriteSet<SocketAddress>				proxy_list	= new CopyOnWriteSet<>(false);

	public static boolean
	hasPluginProxyForNetwork(
		String		network,
		boolean		supports_data )
	{
		// purely checking avaiability, don't trigger an installation // checkPluginInstallation( network );
		
		long start = SystemTime.getMonotonousTime();

		while( true ){

			long	rem = plugin_init_max_wait - ( SystemTime.getMonotonousTime() - start );

			if ( rem <= 0 ){

				return( false );
			}

			boolean wait_complete = waitForPlugins( Math.min( (int)rem, 1000 ));

			boolean result = getPluginProxyForNetwork( network, supports_data ) != null;

			if ( result || wait_complete ){

				return( result );
			}
		}
	}

	private static PluginInterface
	getPluginProxyForNetwork(
		String		network,
		boolean		supports_data )
	{
		for ( PluginInterface pi: plugins ){

			String pid = pi.getPluginID();

			if ( pid.equals( "aznettor" ) && network == AENetworkClassifier.AT_TOR ){

				if ( !supports_data ){

					return( pi );
				}
			}

			if ( pid.equals( "azneti2phelper" ) && network == AENetworkClassifier.AT_I2P ){

				return( pi );
			}
		}

		return( null );
	}

	public static boolean
	hasPluginProxy()
	{		
		waitForPlugins( plugin_init_max_wait );

		// purely checking avaiability, don't trigger an installation // 

		for ( PluginInterface pi: plugins ){

			try{
				IPCInterface ipc = pi.getIPC();

				if ( ipc.canInvoke( "testHTTPPseudoProxy", new Object[]{ TorrentUtils.getDecentralisedEmptyURL() })){

					return( true );
				}
			}catch( Throwable e ){
			}
		}

		return( false );
	}

	private static boolean
	isEnabled()
	{
		Proxy system_proxy = AEProxySelectorFactory.getSelector().getActiveProxy();

		if ( system_proxy == null || system_proxy.equals( Proxy.NO_PROXY )){

			return( true );

		}else{

			return( enable_plugin_proxies_with_socks );
		}
	}

	public static PluginProxyImpl
	getPluginProxy(
		String					reason,
		URL						target,
		Map<String,Object>		properties,
		boolean					can_wait )
	{
		if ( isEnabled()){

			checkPluginInstallation( null, reason );

			String url_protocol = target.getProtocol().toLowerCase();

			if ( url_protocol.startsWith( "http" ) || url_protocol.equals( "ftp" )){

				if ( can_wait ){

					waitForPlugins(0);
				}

				if ( properties == null ){

					properties = new HashMap<>();
				}

				for ( PluginInterface pi: plugins ){

					try{
						IPCInterface ipc = pi.getIPC();

						Object[] proxy_details;

						if ( ipc.canInvoke( "getProxy", new Object[]{ reason, target, properties } )){

							proxy_details = (Object[])ipc.invoke( "getProxy", new Object[]{ reason, target, properties } );

						}else{

							proxy_details = (Object[])ipc.invoke( "getProxy", new Object[]{ reason, target } );
						}

						if ( proxy_details != null ){

							if ( proxy_details.length == 2 ){

									// support old plugins

								proxy_details = new Object[]{ proxy_details[0], proxy_details[1], target.getHost()};
							}

							return( new PluginProxyImpl( target.toExternalForm(), reason, ipc, properties, proxy_details ));
						}
					}catch( Throwable e ){
					}
				}
			}
		}

		return( null );
	}

	public static PluginProxyImpl
	getPluginProxy(
		String					reason,
		String					host,
		int						port,
		Map<String,Object>		properties )
	{
		if ( isEnabled()){

			checkPluginInstallation( null, reason );

			if ( properties == null ){

				properties = new HashMap<>();
			}

			for ( PluginInterface pi: plugins ){

				try{
					IPCInterface ipc = pi.getIPC();

					Object[] proxy_details;

					if ( ipc.canInvoke( "getProxy", new Object[]{ reason, host, port, properties })){

						proxy_details = (Object[])ipc.invoke( "getProxy", new Object[]{ reason, host, port, properties });

					}else{

						proxy_details = (Object[])ipc.invoke( "getProxy", new Object[]{ reason, host, port });
					}

					if ( proxy_details != null ){

						return( new PluginProxyImpl( host + ":" + port, reason, ipc, properties, proxy_details ));
					}
				}catch( Throwable e ){
				}
			}
		}

		return( null );
	}

	public static PluginProxy
	getPluginProxy(
		Proxy		proxy )
	{
		if ( proxy != null ){

			synchronized( proxy_map ){

				WeakReference<PluginProxyImpl>	ref = proxy_map.get( proxy );

				if ( ref != null ){

					return( ref.get());
				}
			}
		}

		return( null );
	}

	public static boolean
	isPluginProxy(
		SocketAddress		address )
	{
		return( proxy_list.contains( address ));
	}

	public static Boolean
	testPluginHTTPProxy(
		URL			url,
		boolean		can_wait,
		String		reason )
	{
		if ( isEnabled()){

			checkPluginInstallation( null, reason );

			String url_protocol = url.getProtocol().toLowerCase();

			if ( url_protocol.startsWith( "http" )){

				if ( can_wait ){

					waitForPlugins(0);
				}

				for ( PluginInterface pi: plugins ){

					try{
						IPCInterface ipc = pi.getIPC();

						return((Boolean)ipc.invoke( "testHTTPPseudoProxy", new Object[]{ url }));

					}catch( Throwable e ){
					}
				}
			}else{

				Debug.out( "Unsupported protocol: " + url_protocol );
			}
		}

		return( null );
	}

	public static PluginHTTPProxyImpl
	getPluginHTTPProxy(
		String		reason,
		URL			url,
		boolean		can_wait )
	{
		if ( isEnabled()){

			checkPluginInstallation( null, reason );

			String url_protocol = url.getProtocol().toLowerCase();

			if ( url_protocol.startsWith( "http" )){

				if ( can_wait ){

					waitForPlugins(0);
				}

				for ( PluginInterface pi: plugins ){

					try{
						IPCInterface ipc = pi.getIPC();

						Proxy proxy = (Proxy)ipc.invoke( "createHTTPPseudoProxy", new Object[]{ reason, url });

						if ( proxy != null ){

							return( new PluginHTTPProxyImpl( reason, ipc, proxy ));
						}
					}catch( Throwable e ){
					}
				}
			}else{

				Debug.out( "Unsupported protocol: " + url_protocol );
			}
		}

		return( null );
	}

	public static List<PluginInterface>
	getPluginHTTPProxyProviders(
		boolean		can_wait )
	{
		if ( can_wait ){

			waitForPlugins(0);
		}

		// not required when enumerating existing providers checkPluginInstallation( null );

		List<PluginInterface> pis =
			CoreFactory.getSingleton().getPluginManager().getPluginsWithMethod(
				"createHTTPPseudoProxy",
				new Class[]{ String.class, URL.class });

		return( pis );
	}

	public static Map<String,Object>
	getPluginServerProxy(
		String					reason,
		String					network,
		String					server_uid,
		Map<String,Object>		options )
	{
		waitForPlugins( plugin_init_max_wait );

		checkPluginInstallation( network, reason );

		PluginInterface pi = getPluginProxyForNetwork( network, false );

		if ( pi == null ){

			return( null );
		}

		options = new HashMap<>(options);

		options.put( "id", server_uid );

		try{
			IPCInterface ipc = pi.getIPC();

			Map<String,Object> reply = (Map<String,Object>)ipc.invoke( "getProxyServer", new Object[]{ reason, options });

			return( reply );

		}catch( Throwable e ){

		}

		return( null );
	}

	public static DHTPluginInterface
	getPluginDHTProxy(
		String					reason,
		String					network,
		Map<String,Object>		options )
	{
		waitForPlugins( plugin_init_max_wait );

		checkPluginInstallation( network, reason );

		PluginInterface pi = getPluginProxyForNetwork( network, false );

		if ( pi == null ){

			return( null );
		}

		try{
			IPCInterface ipc = pi.getIPC();

			DHTPluginInterface reply = (DHTPluginInterface)ipc.invoke( "getProxyDHT", new Object[]{ reason, options });

			return( reply );

		}catch( Throwable e ){

		}

		return( null );
	}
	
	private static InetSocketAddress
	getLocalAddress(
		PluginProxyImpl	pp )
	{
		String					host		= pp.getHost();
		int						port		= pp.getPort();
		
		return( getLocalAddress( host, port ));
	}
	
	public static final int LA_EXPLICIT_NET_NONE	= 0;
	public static final int LA_EXPLICIT_NET_MIX		= 1;
	public static final int LA_EXPLICIT_NET_PURE	= 2;
	
	public static InetSocketAddress
	getLocalAddress(
		String		host,
		int			port )
	{
		return( getLocalAddress( host, port, LA_EXPLICIT_NET_NONE ));
	}
	
	public static InetSocketAddress
	getLocalAddress(
		String		host,
		int			port,
		int			network )
	{
		Map<String,Object>		options		= new HashMap<>();

		options.put( "net", network );
		
		Object[] args = new Object[]{ host, port, options };
		
		for ( PluginInterface pi: plugins ){

			try{
				IPCInterface ipc = pi.getIPC();

				if ( ipc.canInvoke( "getLocalProxyEndpoint", args )){

					Map<String,Object> reply = (Map<String,Object>)ipc.invoke( "getLocalProxyEndpoint", args );

					String	l_host	= (String)reply.get( "host" );
					int		l_port	= (Integer)reply.get( "port" );
					
					return( InetSocketAddress.createUnresolved( l_host, l_port ));
				}
			}catch( Throwable e ){
			}
		}
		
		return( null );
	}

	private static void
	checkPluginInstallation(
		String		network,
		String		reason )
	{
		// aznettor was moved from the installer so see if we need to dynamically install it
		
		if ( network == null || network == AENetworkClassifier.AT_TOR ) {
			
			if ( plugin_init_complete.isReleasedForever()) {
				
				if ( CoreFactory.getSingleton().getPluginManager().getPluginInterfaceByID( "aznettor", false ) == null ){
					
					if ( !Constants.isAndroid ) {
					
						installTor( reason, "aznettor.install.via.proxy", new boolean[1], null );
					}
				}
			}
		}
	}
	
	private static Object tor_install_lock = new Object();
	
	private static Map<String,Long>	declines = new HashMap<>();

	private static boolean tor_installing;
	private static boolean tor_install_fail_reported;
	
	private static boolean
	installTor(
		String				extra_text,
		String				remember_id,
		final boolean[]		install_outcome,
		final Runnable		callback )
	{
		String decline_key = remember_id;

		if ( decline_key == null ){

			decline_key = extra_text;
		}

		if ( decline_key == null ){

			decline_key = "generic";
		}

		synchronized( tor_install_lock ){

			Long decline = declines.get( decline_key );

			if ( decline != null && SystemTime.getMonotonousTime() - decline < 60*1000 ){

				return( false );
			}

			if ( tor_installing ){

				Debug.out( "Tor Helper already installing" );

				return( false );
			}

			tor_installing = true;
		}

		boolean	installing 	= false;

		boolean	declined	= false;

		try{
			UIFunctions uif = UIFunctionsManager.getUIFunctions();

			if ( uif == null ){

				Debug.out( "UIFunctions unavailable - can't install plugin" );

				return( false );
			}

			String title = MessageText.getString("aznettor.install");

			String text = "";

			if ( extra_text != null ){

				text = extra_text + "\n\n";
			}

			text += MessageText.getString("aznettor.install.text" );

			UIFunctionsUserPrompter prompter = uif.getUserPrompter(title, text, new String[] {
				MessageText.getString("Button.yes"),
				MessageText.getString("Button.no")
			}, 0);

			if ( remember_id != null ){

				prompter.setRemember(
					remember_id,
					false,
					MessageText.getString("MessageBoxWindow.nomoreprompting"));
			}

			prompter.setAutoCloseInMS(0);

			prompter.open(null);

			boolean	install = prompter.waitUntilClosed() == 0;

			if ( install ){

				installing = true;

				uif.installPlugin(
						"aznettor",
						"aznettor.install",
						new UIFunctions.actionListener()
						{
							@Override
							public void
							actionComplete(
								Object		result )
							{
								try{
									if ( result instanceof Throwable ){
										
										if ( !tor_install_fail_reported ){
											
											tor_install_fail_reported = true;
										
											UIFunctionsUserPrompter prompter = 
												uif.getUserPrompter(
													MessageText.getString( "metasearch.addtemplate.failed.title"), 
													MessageText.getString( "plugin.manual.install",
														new String[]{ Constants.PLUGINS_WEB_SITE,
														MessageText.getString( "aznettor.plugin.name" )}), 
													new String[]{
															MessageText.getString("Button.ok"),
													}, 0);
	
												prompter.setAutoCloseInMS(0);
												
												prompter.open(null);
										}
									}
									
									if ( callback != null ){

										if ( result instanceof Boolean ){

											install_outcome[0] = (Boolean)result;
										}

										callback.run();
									}
								}finally{

									synchronized( tor_install_lock ){

										tor_installing = false;
									}
								}
							}
						});

			}else{

				declined = true;

				Debug.out( "Tor Helper install declined (either user reply or auto-remembered)" );
			}

			return( install );

		}finally{


			synchronized( tor_install_lock ){

				if ( !installing ){

					tor_installing = false;
				}

				if ( declined ){

					declines.put( decline_key, SystemTime.getMonotonousTime());
				}
			}
		}
	}
	
	private static class
	PluginProxyImpl
		implements PluginProxy
	{
		private final long					create_time = SystemTime.getMonotonousTime();

		private final String				target;
		private final String				reason;

		private final IPCInterface			ipc;
		private final Map<String,Object>	proxy_options;
		private final Object[]				proxy_details;

		private final List<PluginProxyImpl>	children = new ArrayList<>();

		private volatile int	status = ST_UNKNOWN;
		
		private CopyOnWriteList<PluginProxyStatusListener>	listeners = new CopyOnWriteList<>(1);
		
		private
		PluginProxyImpl(
			String				_target,
			String				_reason,
			IPCInterface		_ipc,
			Map<String,Object>	_proxy_options,
			Object[]			_proxy_details )
		{
			target				= _target;
			reason				= _reason;
			ipc					= _ipc;
			proxy_options		= _proxy_options;
			proxy_details		= _proxy_details;

			WeakReference<PluginProxyImpl>	my_ref = new WeakReference<>(this);

			List<PluginProxyImpl>	removed = new ArrayList<>();

			synchronized( proxy_map ){

				Proxy proxy = getProxy();

				SocketAddress address = proxy.address();

				if ( !proxy_list.contains( address )){

					proxy_list.add( address );
				}

				proxy_map.put( proxy, my_ref );

				if ( proxy_map.size() > 1024 ){

					long	now = SystemTime.getMonotonousTime();

					Iterator<WeakReference<PluginProxyImpl>>	it = proxy_map.values().iterator();

					while( it.hasNext()){

						WeakReference<PluginProxyImpl> ref = it.next();

						PluginProxyImpl	pp = ref.get();

						if ( pp == null ){

							it.remove();

						}else{

							if ( now - pp.create_time > 5*60*1000 ){

								removed.add( pp );

								it.remove();
							}
						}
					}
				}
			}

			for ( PluginProxyImpl pp: removed ){

				pp.setOK( false );
			}
		}

		@Override
		public String
		getTarget()
		{
			return( target );
		}

		@Override
		public InetSocketAddress 
		getLocalAddress()
		{
			return( AEPluginProxyHandler.getLocalAddress( this ));
		}
		
		@Override
		public PluginProxy
		getChildProxy(
			String		child_reason,
			URL 		url)
		{
			PluginProxyImpl	child = getPluginProxy( reason + " - " + child_reason, url, proxy_options, false );

			if ( child != null ){

				synchronized( children ){

					children.add( child );
				}
			}

			return( child );
		}

		@Override
		public Proxy
		getProxy()
		{
			return((Proxy)proxy_details[0]);
		}

			// URL methods

		@Override
		public URL
		getURL()
		{
			return((URL)proxy_details[1]);
		}

		@Override
		public String
		getURLHostRewrite()
		{
			return((String)proxy_details[2]);
		}

			// host:port methods

		@Override
		public String
		getHost()
		{
			return((String)proxy_details[1]);
		}

		@Override
		public int
		getPort()
		{
			return((Integer)proxy_details[2]);
		}
		
		@Override
		public Throwable 
		getError()
		{
			try{
				Map<String,Object> status = (Map<String,Object>)ipc.invoke( "getProxyStatus", new Object[]{ proxy_details[0] });
	
				if ( status != null ){
					
					return((Throwable)status.get( "error" ));
				}
			}catch( Throwable e ){
			}
			
			return( null );
		}
		
		@Override
		public boolean 
		getConnected()
		{
			try{
				Map<String,Object> status = (Map<String,Object>)ipc.invoke( "getProxyStatus", new Object[]{ proxy_details[0] });
	
				if ( status != null ){
					
					Boolean connected = (Boolean)status.get( "connected" );
					
					if ( connected != null ){
						
						return( connected );
					}
				}
			}catch( Throwable e ){
			}
			
			return( false );
		}

		@Override
		public void
		setOK(
			boolean	good )
		{
			boolean	changed = false;
			
			try{
				synchronized( this ){
					
					if ( status == ST_UNKNOWN ){
				
						status = good?ST_OK:ST_BAD;
						
						changed = true;
					}
				}
				
				try{
					ipc.invoke( "setProxyStatus", new Object[]{ proxy_details[0], good });
	
				}catch( Throwable e ){
				}
	
				List<PluginProxyImpl> kids;
	
				synchronized( children ){
	
					kids = new ArrayList<>(children);
	
					children.clear();
				}
	
				for ( PluginProxyImpl child: kids ){
	
					child.setOK( good );
				}
	
				synchronized( proxy_map ){
	
					proxy_map.remove( getProxy());
				}
			}finally{
				
				if ( changed ){
					
					for ( PluginProxyStatusListener l: listeners ){
						
						try{
							l.statusChanged( this );
							
						}catch( Throwable e ){
							
							Debug.out( e );
						}
					}
				}
			}
		}
		
		@Override
		public int 
		getStatus()
		{
			return( status );
		}
		
		@Override
		public void 
		addListener(
			PluginProxyStatusListener l)
		{
			listeners.add( l );
		}
	}

	private static class
	PluginHTTPProxyImpl
		implements PluginHTTPProxy
	{
		private final String			reason;
		private final IPCInterface	ipc;
		private final Proxy			proxy;

		private
		PluginHTTPProxyImpl(
			String				_reason,
			IPCInterface		_ipc,
			Proxy				_proxy )
		{
			reason				= _reason;
			ipc					= _ipc;
			proxy				= _proxy;
		}

		@Override
		public Proxy
		getProxy()
		{
			return( proxy );
		}

		@Override
		public String
		proxifyURL(
			String url )
		{
			try{
				URL _url = new URL( url );

				InetSocketAddress pa = (InetSocketAddress)proxy.address();

				_url = UrlUtils.setHost( _url, pa.getAddress().getHostAddress());
				_url = UrlUtils.setPort( _url, pa.getPort());

				url = _url.toExternalForm();

				url += ( url.indexOf('?')==-1?"?":"&" ) + "_azpproxy=1";

				return( url );

			}catch( Throwable e ){

				Debug.out( "Failed to proxify URL: " + url, e );

				return( url );
			}
		}

		@Override
		public void
		destroy()
		{
			try{

				ipc.invoke( "destroyHTTPPseudoProxy", new Object[]{ proxy });

			}catch( Throwable e ){

				Debug.out( e );
			}
		}
	}
}
