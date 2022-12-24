/*
 * File    : UtilitiesImpl.java
 * Created : 24-Mar-2004
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

package com.biglybt.pifimpl.local.utils;

/**
 * @author parg
 *
 */

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.*;

import com.biglybt.core.Core;
import com.biglybt.core.ipchecker.extipchecker.ExternalIPChecker;
import com.biglybt.core.ipchecker.extipchecker.ExternalIPCheckerFactory;
import com.biglybt.core.ipchecker.extipchecker.ExternalIPCheckerService;
import com.biglybt.core.ipchecker.extipchecker.ExternalIPCheckerServiceListener;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.networkmanager.LimitedRateGroup;
import com.biglybt.core.proxy.AEProxyFactory;
import com.biglybt.core.proxy.AEProxyFactory.PluginProxy;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.tag.TagType;
import com.biglybt.core.util.*;
import com.biglybt.core.util.Timer;
import com.biglybt.core.versioncheck.VersionCheckClient;
import com.biglybt.pif.PluginException;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ddb.DistributedDatabase;
import com.biglybt.pif.network.RateLimiter;
import com.biglybt.pif.tag.Tag;
import com.biglybt.pif.tag.TagManager;
import com.biglybt.pif.utils.*;
import com.biglybt.pif.utils.ScriptProvider.ScriptProviderListener;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloader;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderException;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderFactory;
import com.biglybt.pif.utils.resourceuploader.ResourceUploaderFactory;
import com.biglybt.pif.utils.search.SearchException;
import com.biglybt.pif.utils.search.SearchInitiator;
import com.biglybt.pif.utils.search.SearchProvider;
import com.biglybt.pif.utils.security.SESecurityManager;
import com.biglybt.pif.utils.subscriptions.Subscription;
import com.biglybt.pif.utils.subscriptions.SubscriptionException;
import com.biglybt.pif.utils.subscriptions.SubscriptionManager;
import com.biglybt.pif.utils.subscriptions.SubscriptionResult;
import com.biglybt.pif.utils.xml.rss.RSSFeed;
import com.biglybt.pif.utils.xml.simpleparser.SimpleXMLParserDocumentException;
import com.biglybt.pif.utils.xml.simpleparser.SimpleXMLParserDocumentFactory;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.pifimpl.local.ddb.DDBaseImpl;
import com.biglybt.pifimpl.local.network.ConnectionManagerImpl;
import com.biglybt.pifimpl.local.update.UpdateManagerImpl;
import com.biglybt.pifimpl.local.utils.resourcedownloader.ResourceDownloaderFactoryImpl;
import com.biglybt.pifimpl.local.utils.resourceuploader.ResourceUploaderFactoryImpl;
import com.biglybt.pifimpl.local.utils.security.SESecurityManagerImpl;
import com.biglybt.pifimpl.local.utils.xml.rss.RSSFeedImpl;
import com.biglybt.pifimpl.local.utils.xml.simpleparser.SimpleXMLParserDocumentFactoryImpl;
import com.biglybt.platform.PlatformManager;
import com.biglybt.platform.PlatformManagerCapabilities;
import com.biglybt.platform.PlatformManagerFactory;

public class
UtilitiesImpl
	implements Utilities
{
	private static InetAddress		last_public_ip_address;
	private static long				last_public_ip_address_time;

	private Core core;
	private PluginInterface			pi;

	private static ThreadLocal<PluginInterface>		tls	=
		new ThreadLocal<PluginInterface>()
		{
			@Override
			public PluginInterface
			initialValue()
			{
				return( null );
			}
		};

	private static List<searchManager>		search_managers 	= new ArrayList<>();
	private static List<Object[]>			search_providers	= new ArrayList<>();

	// need to use a consistent wrapped group as its object identity drives byte allocs...

	private static WeakHashMap<RateLimiter,PluginLimitedRateGroup>	limiter_map = new WeakHashMap<>();


	private static CopyOnWriteList<LocationProviderListener>	lp_listeners 		= new CopyOnWriteList<>();
	private static CopyOnWriteList<LocationProvider>			location_providers 	= new CopyOnWriteList<>();

	private static CopyOnWriteList<ScriptProviderListener>	sp_listeners 		= new CopyOnWriteList<>();
	private static CopyOnWriteList<ScriptProvider>			script_providers 	= new CopyOnWriteList<>();


	public static PluginLimitedRateGroup
	wrapLimiter(
		RateLimiter						limiter,
		boolean							disable_disable )
	{
		synchronized( limiter_map ){

			PluginLimitedRateGroup l = limiter_map.get( limiter );

			if ( l == null ){

				l = new PluginLimitedRateGroup( limiter, disable_disable );

				limiter_map.put( limiter, l );
			}else{

				if ( l.isDisableDisable() != disable_disable ){

					Debug.out( "Inconsistent setting for disable_disable" );
				}
			}

			return( l );
		}
	}

	public static RateLimiter
	unwrapLmiter(
		PluginLimitedRateGroup l )
	{
		return( l.limiter );
	}

	public
	UtilitiesImpl(
		Core _core,
		PluginInterface		_pi )
	{
		core	= _core;
		pi		= _pi;
	}

	@Override
	public String
	getUserDir()
	{
		String	res = SystemProperties.getUserPath();

		if ( res.endsWith(File.separator )){

			res = res.substring(0,res.length()-1);
		}

		return( res );
	}

	/**
	 * @note Exactly the same as {@link UpdateManagerImpl#getInstallDir()}
	 */
	@Override
	public String
	getProgramDir()
	{
		String	res = SystemProperties.getApplicationPath();

		if ( res.endsWith(File.separator )){

			res = res.substring(0,res.length()-1);
		}

		return( res );
	}

	@Override
	public boolean
	isWindows()
	{
		return( Constants.isWindows );
	}

	@Override
	public boolean
	isLinux()
	{
		return( Constants.isLinux );
	}

	@Override
	public boolean
	isUnix()
	{
		return( Constants.isUnix );
	}

	@Override
	public boolean
	isFreeBSD()
	{
		return( Constants.isFreeBSD );
	}

	@Override
	public boolean
	isSolaris()
	{
		return( Constants.isSolaris );
	}

	@Override
	public boolean
	isOSX()
	{
		return( Constants.isOSX );
	}

	@Override
	public boolean
	isCVSVersion()
	{
		return( Constants.isCVSVersion());
	}

	@Override
	public InputStream
	getImageAsStream(
		String	image_name )
	{
		return( UtilitiesImpl.class.getClassLoader().getResourceAsStream("com/biglybt/ui/icons/" + image_name));
	}

	@Override
	public Semaphore
	getSemaphore()
	{
		return( new SemaphoreImpl( pi ));
	}

    @Override
    public Monitor getMonitor(){
      return new MonitorImpl( pi );
    }


	@Override
	public ByteBuffer
	allocateDirectByteBuffer(
		int		size )
	{
		return( DirectByteBufferPool.getBuffer( DirectByteBuffer.AL_EXTERNAL,size ).getBuffer(DirectByteBuffer.SS_EXTERNAL));
	}

	@Override
	public void
	freeDirectByteBuffer(
		ByteBuffer	buffer )
	{

		//DirectByteBufferPool.freeBuffer( buffer );
	}

	@Override
	public PooledByteBuffer
	allocatePooledByteBuffer(
		int		length )
	{
		return( new PooledByteBufferImpl( length ));
	}

	@Override
	public PooledByteBuffer
	allocatePooledByteBuffer(
		byte[]		data )
	{
		return( new PooledByteBufferImpl( data ));
	}

	@Override
	public PooledByteBuffer
	allocatePooledByteBuffer(
		Map		map )

		throws IOException
	{
		return( new PooledByteBufferImpl( BEncoder.encode( map )));
	}

	@Override
	public Formatters
	getFormatters()
	{
		return( new FormattersImpl());
	}

	@Override
	public LocaleUtilities
	getLocaleUtilities()
	{
		return( new LocaleUtilitiesImpl( pi ));
	}

	@Override
	public UTTimer
	createTimer(
		String		name )
	{
		return( new UTTimerImpl( pi, name, false ));
	}

	@Override
	public UTTimer
	createTimer(
		String		name,
		boolean		lightweight )
	{
		return( new UTTimerImpl( pi, name, lightweight ));
	}

	@Override
	public UTTimer
	createTimer(
		String		name,
		int priority )
	{
		return( new UTTimerImpl( pi, name, priority ));
	}

	@Override
	public UTTimer
	createTimer(
		String		name,
		int			max_threads,
		int			priority )
	{
		return( new UTTimerImpl( pi, name, max_threads, priority ));
	}
	
	@Override
	public void
	createThread(
		String			name,
		final Runnable	target )
	{
		AEThread2 t =
			new AEThread2( pi.getPluginName() + "::" + name, true )
			{
				@Override
				public void
				run()
				{
					callWithPluginThreadContext( pi, target );
				}
			};

		t.start();
	}

	@Override
	public void
	createProcess(
		String		command_line )

		throws PluginException
	{
	    try{
	    		// we need to spawn without inheriting handles

	    	PlatformManager pm = PlatformManagerFactory.getPlatformManager();

	    	if ( pm.hasCapability( PlatformManagerCapabilities.CreateCommandLineProcess )){

	    		pm.createProcess( command_line, false );

	    		return;
	    	}
	    }catch( Throwable e ){

	        Debug.printStackTrace(e);
	    }

	    try{
	       	Runtime.getRuntime().exec( command_line );

	    }catch( Throwable f ){

	    	throw( new PluginException("Failed to create process", f ));
	    }
	}

	public void
	createProcess(
		File		working_dirctory,
		String[]	command,
		String[]	env )
	
		throws PluginException
	{
		try{
			GeneralUtils.createProcessBuilder( working_dirctory, command, env ).start();
			
	    }catch( Throwable f ){

	    	throw( new PluginException("Failed to create process", f ));
	    }
	}
	
	@Override
	public ResourceDownloaderFactory
	getResourceDownloaderFactory()
	{
		return( ResourceDownloaderFactoryImpl.getSingleton());
	}

	@Override
	public ResourceUploaderFactory
	getResourceUploaderFactory()
	{
		return( ResourceUploaderFactoryImpl.getSingleton());
	}

	@Override
	public SESecurityManager
	getSecurityManager()
	{
		return( new SESecurityManagerImpl( core ));
	}

	@Override
	public SimpleXMLParserDocumentFactory
	getSimpleXMLParserDocumentFactory()
	{
		return( new SimpleXMLParserDocumentFactoryImpl());
	}

	@Override
	public RSSFeed
	getRSSFeed(
		URL				source_url,
		InputStream		is )

		throws SimpleXMLParserDocumentException
	{
		try{
			return( new RSSFeedImpl( this, source_url, is ));

		}finally{

			try{
				is.close();

			}catch( Throwable e ){
			}
		}
	}

	@Override
	public RSSFeed
	getRSSFeed(
		URL		feed_location )

		throws ResourceDownloaderException, SimpleXMLParserDocumentException
	{
		String	feed_str	= feed_location.toExternalForm();

		String	lc_feed_str = feed_str.toLowerCase( Locale.US );

		ResourceDownloader	rd;

		PluginProxy plugin_proxy	= null;

		try{
			if ( lc_feed_str.startsWith( "tor:" )){

				String target_resource = feed_str.substring( 4 );

				try{
					feed_location = new URL( target_resource );

				}catch( MalformedURLException e ){

					throw( new ResourceDownloaderException( e ));
				}

				Map<String,Object>	options = new HashMap<>();

				options.put( AEProxyFactory.PO_PEER_NETWORKS, new String[]{ AENetworkClassifier.AT_TOR });

				plugin_proxy =
					AEProxyFactory.getPluginProxy(
						"RSS Feed download of '" + target_resource + "'",
						feed_location,
						options,
						true );

				if ( plugin_proxy == null ){

					throw( new ResourceDownloaderException( "No Tor plugin proxy available for '" + feed_str + "'" ));
				}


				rd = getResourceDownloaderFactory().create( plugin_proxy.getURL(), plugin_proxy.getProxy());

				rd.setProperty( "URL_HOST", plugin_proxy.getURLHostRewrite() + (feed_location.getPort()==-1?"":(":" + feed_location.getPort())));

			}else if ( lc_feed_str.startsWith( "i2p:" )){

				String target_resource = feed_str.substring( 4 );

				try{
					feed_location = new URL( target_resource );

				}catch( MalformedURLException e ){

					throw( new ResourceDownloaderException( e ));
				}

				Map<String,Object>	options = new HashMap<>();
				
				options.put( AEProxyFactory.PO_PREFERRED_PROXY_TYPE, "HTTP" );
				options.put( AEProxyFactory.PO_FORCE_PROXY, true );

				plugin_proxy =
						AEProxyFactory.getPluginProxy(
							"RSS Feed download of '" + feed_location + "'",
							feed_location,
							options,
							true );

				if ( plugin_proxy == null ){

					throw( new ResourceDownloaderException( "No Plugin proxy available for '" + feed_str + "'" ));
				}

				rd = getResourceDownloaderFactory().create( plugin_proxy.getURL(), plugin_proxy.getProxy());

				rd.setProperty( "URL_HOST", plugin_proxy.getURLHostRewrite() + (feed_location.getPort()==-1?"":(":" + feed_location.getPort())));

			}else{

				if ( AENetworkClassifier.categoriseAddress( feed_location.getHost()) != AENetworkClassifier.AT_PUBLIC ){

					Map<String,Object>	options = new HashMap<>();
					
					options.put( AEProxyFactory.PO_PREFERRED_PROXY_TYPE, "HTTP" );

					plugin_proxy =
							AEProxyFactory.getPluginProxy(
								"RSS Feed download of '" + feed_location + "'",
								feed_location,
								options,
								true );

					if ( plugin_proxy == null ){

						throw( new ResourceDownloaderException( "No Plugin proxy available for '" + feed_str + "'" ));
					}


					rd = getResourceDownloaderFactory().create( plugin_proxy.getURL(), plugin_proxy.getProxy());

					rd.setProperty( "URL_HOST", plugin_proxy.getURLHostRewrite() + (feed_location.getPort()==-1?"":(":" + feed_location.getPort())));

				}else{

					rd = getResourceDownloaderFactory().create( feed_location );
				}
			}

			return( getRSSFeed( feed_location, rd));

		}finally{

			if ( plugin_proxy != null ){

				plugin_proxy.setOK( true );
			}
		}
	}


	@Override
	public RSSFeed
	getRSSFeed(
		URL					source_url,
		ResourceDownloader	feed_location )

		throws ResourceDownloaderException, SimpleXMLParserDocumentException
	{
		return( new RSSFeedImpl( this, source_url, feed_location ));
	}

	@Override
	public InetAddress
	getPublicAddress(
		boolean	v6 )
	{
		if ( v6 ){

			String	vc_ip = VersionCheckClient.getSingleton().getExternalIpAddress( false, true );

			if ( vc_ip != null && vc_ip.length() > 0 ){

				try{
					return( InetAddress.getByName( vc_ip ));

				}catch( Throwable e ){

					Debug.printStackTrace( e );
				}
			}

			return( null );
		}else{

			return( getPublicAddress());
		}
	}

	@Override
	public InetAddress
	getPublicAddress()
	{
		long	now = SystemTime.getCurrentTime();

		if ( now < last_public_ip_address_time ){

			last_public_ip_address_time	 = now;

		}else{

			if ( last_public_ip_address != null && now - last_public_ip_address_time < 15*60*1000 ){

				return( last_public_ip_address );
			}
		}

		InetAddress res	= null;

		try{

			String	vc_ip = VersionCheckClient.getSingleton().getExternalIpAddress( false, false );

			if ( vc_ip != null && vc_ip.length() > 0 ){

				res = InetAddress.getByName( vc_ip );

			}else{

				ExternalIPChecker	checker = ExternalIPCheckerFactory.create();

				ExternalIPCheckerService[]	services = checker.getServices();

				final String[]	ip = new String[]{ null };

				for (int i=0;i<services.length && ip[0] == null;i++){

					final ExternalIPCheckerService	service = services[i];

					if ( service.supportsCheck()){

						final AESemaphore	sem = new AESemaphore("Utilities:getExtIP");

						ExternalIPCheckerServiceListener	listener =
							new ExternalIPCheckerServiceListener()
							{
								@Override
								public void
								checkComplete(
									ExternalIPCheckerService	_service,
									String						_ip )
								{
									ip[0]	= _ip;

									sem.release();
								}

								@Override
								public void
								checkFailed(
									ExternalIPCheckerService	_service,
									String						_reason )
								{
									sem.release();
								}

								@Override
								public void
								reportProgress(
									ExternalIPCheckerService	_service,
									String						_message )
								{
								}
							};

						services[i].addListener( listener );

						try{

							services[i].initiateCheck( 60000 );

							sem.reserve( 60000 );

						}finally{

							services[i].removeListener( listener );
						}
					}


					if ( ip[0] != null ){

						res = InetAddress.getByName( ip[0] );

						break;
					}
				}
			}
		}catch( Throwable e ){

			Debug.printStackTrace( e );
		}

		if ( res == null ){

				// if we failed then use any prior value if we've got one

			res	= last_public_ip_address;

		}else{

			last_public_ip_address		= res;

			last_public_ip_address_time	= now;
		}

		return( res );
	}

	@Override
	public String
	reverseDNSLookup(
		InetAddress		address )
	{
		final AESemaphore	sem = new AESemaphore("Utilities:reverseDNS");

		final String[]	res = { null };

		IPToHostNameResolver.addResolverRequest(
					address.getHostAddress(),
					new IPToHostNameResolverListener()
					{
						@Override
						public void
						IPResolutionComplete(
							String 		result,
							boolean 	succeeded )
						{
							if ( succeeded ){

								res[0] = result;
							}

							sem.release();
						}
					});

		sem.reserve( 60000 );

		return( res[0] );
	}


  @Override
  public long getCurrentSystemTime() {
    return SystemTime.getCurrentTime();
  }

	@Override
	public ByteArrayWrapper
	createWrapper(
		byte[]		data )
	{
		return( new HashWrapper( data ));
	}

	@Override
	public AggregatedDispatcher
	createAggregatedDispatcher(
		final long	idle_dispatch_time,
		final long	max_queue_size )
	{
		return(
			new AggregatedDispatcher()
			{
				private AggregatedList	list =
					createAggregatedList(
						new AggregatedListAcceptor()
						{
							@Override
							public void
							accept(
								List		l )
							{
								for (int i=0;i<l.size();i++){

									try{
										((Runnable)l.get(i)).run();

									}catch( Throwable e ){

										Debug.printStackTrace(e);
									}
								}
							}
						},
						idle_dispatch_time,
						max_queue_size );

				@Override
				public void
				add(
					Runnable	runnable )
				{
					list.add( runnable );
				}

				@Override
				public Runnable
				remove(
					Runnable	runnable )
				{
					return((Runnable)list.remove( runnable ));
				}

				@Override
				public void
				destroy()
				{
					list.destroy();
				}
			});
	}

	@Override
	public AggregatedList
	createAggregatedList(
		final AggregatedListAcceptor	acceptor,
		final long						idle_dispatch_time,
		final long						max_queue_size )
	{
		return(
			new AggregatedList()
			{
				AEMonitor	timer_mon	= new AEMonitor( "aggregatedList" );

				Timer		timer = new Timer( "AggregatedList" );
				TimerEvent	event;

				List		list	= new ArrayList();

				@Override
				public void
				add(
					Object	obj )
				{

					List	dispatch_now = null;

					try{
						timer_mon.enter();

							// if the list is full kick off a dispatch and reset the list

						if (	max_queue_size > 0 &&
								max_queue_size	== list.size()){

							dispatch_now = list;

							list	= new ArrayList();

						}

						list.add( obj );

							// set up a timer to wakeup in required time period

						long	now = SystemTime.getCurrentTime();

						if ( event != null ){

							event.cancel();
						}

						event =
							timer.addEvent(
									now + idle_dispatch_time,
									new TimerEventPerformer()
									{
										@Override
										public void
										perform(
											TimerEvent	event )
										{
											dispatch();
										}
									});
					}finally{

						timer_mon.exit();
					}

					if ( dispatch_now != null ){

						dispatch( dispatch_now );
					}
				}

				@Override
				public Object
				remove(
					Object	obj )
				{
					Object	res = null;

					try{
						timer_mon.enter();

						res = list.remove( obj )?obj:null;

						if ( res != null ){

							long	now = SystemTime.getCurrentTime();

							if ( event != null ){

								event.cancel();
							}

							if ( list.size() == 0 ){

								event	= null;

							}else{

								event =
									timer.addEvent(
											now + idle_dispatch_time,
											new TimerEventPerformer()
											{
												@Override
												public void
												perform(
													TimerEvent	event )
												{
													dispatch();
												}
											});
							}
						}
					}finally{

						timer_mon.exit();
					}

					return( res );
				}

				protected void
				dispatch()
				{
					List	dispatch_list;

					try{
						timer_mon.enter();

						dispatch_list	= list;

						list	= new ArrayList();

					}finally{

						timer_mon.exit();
					}

					dispatch( dispatch_list );
				}

				protected void
				dispatch(
					List		l )
				{
					if ( l.size() > 0 ){

						try{
							acceptor.accept( l );

						}catch( Throwable e ){

							Debug.printStackTrace(e);
						}
					}
				}

				@Override
				public void
				destroy()
				{
					dispatch();

					timer.destroy();
				}
			});
	}

	public static void
	callWithPluginThreadContext(
		PluginInterface pi,
		Runnable					target )
	{
		PluginInterface existing = tls.get();

		try{
			tls.set( pi );

			target.run();

		}finally{

			tls.set( existing );
		}
	}

	public static <T extends Exception> void
	callWithPluginThreadContext(
		PluginInterface					pi,
		runnableWithException<T>		target )

		throws T
	{
		PluginInterface existing = tls.get();

		try{
			tls.set( pi );

			target.run();

		}finally{

			tls.set( existing );
		}
	}

	public static <T> T
	callWithPluginThreadContext(
		PluginInterface				pi,
		runnableWithReturn<T>			target )
	{
		PluginInterface existing = tls.get();

		try{
			tls.set( pi );

			return( target.run());

		}finally{

			tls.set( existing );
		}
	}

	public static <T,S extends Exception> T
	callWithPluginThreadContext(
		PluginInterface							pi,
		runnableWithReturnAndException<T,S>		target )

		throws S
	{
		PluginInterface existing = tls.get();

		try{
			tls.set( pi );

			return( target.run());

		}finally{

			tls.set( existing );
		}
	}

	public static PluginInterface
	getPluginThreadContext()
	{
		return((PluginInterface)tls.get());
	}

	@Override
	public Map
 	readResilientBEncodedFile(
 		File	parent_dir,
 		String	file_name,
 		boolean	use_backup )
	{
		return( FileUtil.readResilientFile( parent_dir, file_name, use_backup ));
	}

	@Override
	public void
 	writeResilientBEncodedFile(
 		File	parent_dir,
 		String	file_name,
 		Map		data,
 		boolean	use_backup )
	{
		FileUtil.writeResilientFile( parent_dir, file_name, data, use_backup );
	}

	@Override
	public void
 	deleteResilientBEncodedFile(
 		File	parent_dir,
 		String	file_name,
 		boolean	use_backup )
	{
		FileUtil.deleteResilientFile( FileUtil.newFile( parent_dir, file_name ));
	}

	@Override
	public int compareVersions(String v1, String v2) {
		return Constants.compareVersions( v1, v2 );
	}

	@Override
	public String normaliseFileName(String f_name) {
		return FileUtil.convertOSSpecificChars(f_name,false);
	}

	@Override
	public DelayedTask createDelayedTask(Runnable target) {
		return addDelayedTask(pi.getPluginName(), target);
	}

	private static List			delayed_tasks = new ArrayList();
	private static AESemaphore	delayed_tasks_sem	= new AESemaphore( "Utilities:delayedTask" );
	private static AEThread2	delayed_task_thread;

	public static DelayedTask addDelayedTask(String	name, Runnable r) {
		DelayedTaskImpl res = new DelayedTaskImpl(name);
		res.setTask(r);
		return res;
	}

	private static void
	queueTask(
		DelayedTaskImpl		task,
		int pos)
	{
		synchronized( delayed_tasks ){

			delayed_tasks.add( pos == -1 ? delayed_tasks.size() : pos, task );

			delayed_tasks_sem.release();

			if ( delayed_task_thread == null ){

				delayed_task_thread =
					new AEThread2( "Utilities:delayedTask", true )
					{
						@Override
						public void
						run()
						{
							try{
								PluginInitializer.addInitThread();

								while( true ){

									if ( !delayed_tasks_sem.reserve( 5*1000 )){

										synchronized( delayed_tasks ){

											if ( delayed_tasks.isEmpty()){

												delayed_task_thread	= null;

												break;
											}
										}
									}else{

										DelayedTaskImpl	task;

										synchronized( delayed_tasks ){

											task = (DelayedTaskImpl)delayed_tasks.remove(0);
										}

										//System.out.println( TimeFormatter.milliStamp() + ": Running delayed task: " + task.getName());

										task.run();
									}
								}
							}finally{

								PluginInitializer.removeInitThread();
							}
						}
					};

				delayed_task_thread.setPriority( Thread.MIN_PRIORITY );

				delayed_task_thread.start();
			}
		}
	}

	@Override
	public void
	registerSearchProvider(
		SearchProvider 		provider )

		throws SearchException
	{
		List<searchManager>	managers;

		synchronized( UtilitiesImpl.class ){

			search_providers.add( new Object[]{ pi, provider  });

			managers = new ArrayList<>(search_managers);
		}

		for (int i=0;i<managers.size();i++){

			((searchManager)managers.get(i)).addProvider( pi, provider );
		}
	}

	@Override
	public void
	unregisterSearchProvider(
		SearchProvider 		provider )

		throws SearchException
	{
		List<searchManager>	managers;

		synchronized( UtilitiesImpl.class ){

			Iterator<Object[]> it = search_providers.iterator();

			while( it.hasNext()){

				Object[] entry = it.next();

				if ( entry[0] == pi && entry[1] == provider ){

					it.remove();
				}
			}

			managers = new ArrayList<>(search_managers);
		}

		for (int i=0;i<managers.size();i++){

			((searchManager)managers.get(i)).removeProvider( pi, provider );
		}
	}

	@Override
	public SearchInitiator
	getSearchInitiator()

		throws SearchException
	{
		List<searchManager>	managers;

		synchronized( UtilitiesImpl.class ){

			managers = new ArrayList<>(search_managers);
		}

		if ( managers.size() == 0 ){

			throw( new SearchException( "No search managers registered - try later" ));
		}

		return( managers.get(0));
	}

	public static void
	addSearchManager(
		searchManager		manager )
	{
		List	providers;

		synchronized( UtilitiesImpl.class ){

			search_managers.add( manager );

			providers = new ArrayList( search_providers );
		}

		for (int i=0;i<providers.size();i++){

			Object[]	entry = (Object[])providers.get(i);

			manager.addProvider((PluginInterface)entry[0],(SearchProvider)entry[1]);
		}
	}

	public interface
	searchManager
		extends SearchInitiator
	{
		public void
		addProvider(
			PluginInterface		pi,
			SearchProvider		provider );

		public void
		removeProvider(
			PluginInterface		pi,
			SearchProvider		provider );
	}


	@Override
	public SubscriptionManager
	getSubscriptionManager()

		throws SubscriptionException
	{
		try{
			Method m = Class.forName("com.biglybt.core.subs.SubscriptionManagerFactory").getMethod( "getSingleton" );

			final PluginSubscriptionManager sm = (PluginSubscriptionManager)m.invoke( null );

			return(
				new SubscriptionManager()
				{
					@Override
					public void
					requestSubscription(
						URL		url )
					{
						sm.requestSubscription( url, new HashMap<String,Object>());
					}

					@Override
					public void
					requestSubscription(
						URL 					url,
						Map<String, Object> 	options )
					{
						sm.requestSubscription( url, options );
					}

					@Override
					public void
					requestSubscription(
						SearchProvider 			sp,
						Map<String, Object> 	search_parameters )

						throws SubscriptionException
					{
						sm.requestSubscription( sp, search_parameters );
					}

					@Override
					public Subscription[]
					getSubscriptions()
					{
						PluginSubscription[] p_subs = sm.getSubscriptions( true );

						Subscription[]	subs = new Subscription[ p_subs.length ];

						for ( int i=0;i<subs.length;i++ ){

							final PluginSubscription p_sub = p_subs[i];

							subs[i] =
								new Subscription()
								{
									@Override
									public String
									getID()
									{
										return( p_sub.getID());
									}

									@Override
									public String
									getName()
									{
										return( p_sub.getName());
									}

									@Override
									public boolean
									isSearchTemplate()
									{
										return( p_sub.isSearchTemplate());
									}

									@Override
									public SubscriptionResult[]
									getResults()
									{
										PluginSubscriptionResult[] p_results = p_sub.getResults( false );

										SubscriptionResult[] results = new SubscriptionResult[p_results.length];

										for (int i=0;i<results.length;i++){

											final PluginSubscriptionResult p_res = p_results[i];

											results[i] =
												new SubscriptionResult()
												{
													private Map<Integer,Object> map = p_res.toPropertyMap();

													@Override
													public Object
													getProperty(
														int		property_name )
													{
														return( map.get( property_name ));
													}

													@Override
													public boolean
													isRead()
													{
														return( p_res.getRead());
													}

													@Override
													public void
													setRead(
														boolean	read )
													{
														p_res.setRead( read );
													}
												};
										}

										return( results );
									}
								};
						}

						return( subs );
					}
				});

		}catch( Throwable e ){

			throw( new SubscriptionException( "Subscriptions unavailable", e ));
		}
	}

	@Override
	public boolean
	supportsPowerStateControl(
		int state )
	{
		if ( state == PowerManagementListener.ST_SLEEP ){

			return( PlatformManagerFactory.getPlatformManager().hasCapability( PlatformManagerCapabilities.PreventComputerSleep ));
		}

		return( false );
	}

	@Override
	public void
	addPowerManagementListener(
		PowerManagementListener	listener )
	{
		core.addPowerManagementListener( listener );
	}

	@Override
	public void
	removePowerManagementListener(
		PowerManagementListener	listener )
	{
		core.removePowerManagementListener( listener );
	}

	@Override
	public List<LocationProvider>
	getLocationProviders()
	{
		return( location_providers.getList());
	}

	@Override
	public void
	addLocationProvider(
		LocationProvider	provider )
	{
		location_providers.add( provider );

		for ( LocationProviderListener l: lp_listeners ){

			try{
				l.locationProviderAdded( provider );

			}catch( Throwable e ){

				Debug.out( e );
			}
		}
	}

	@Override
	public void
	removeLocationProvider(
		LocationProvider	provider )
	{
		location_providers.remove( provider );

		for ( LocationProviderListener l: lp_listeners ){

			try{
				l.locationProviderRemoved( provider );

			}catch( Throwable e ){

				Debug.out( e );
			}
		}
	}

	@Override
	public void
	addLocationProviderListener(
		LocationProviderListener		listener )
	{
		lp_listeners.add( listener );

		for ( LocationProvider lp: location_providers ){

			listener.locationProviderAdded( lp );
		}
	}

	@Override
	public void
	removeLocationProviderListener(
		LocationProviderListener		listener )
	{
		lp_listeners.remove( listener );
	}


		// script providers

	@Override
	public List<ScriptProvider>
	getScriptProviders()
	{
		return( script_providers.getList());
	}

	@Override
	public void
	registerScriptProvider(
		ScriptProvider	provider )
	{
		script_providers.add( provider );

		for ( ScriptProviderListener l: sp_listeners ){

			try{
				l.scriptProviderAdded( provider );

			}catch( Throwable e ){

				Debug.out( e );
			}
		}
	}

	@Override
	public void
	unregisterScriptProvider(
		ScriptProvider	provider )
	{
		script_providers.remove( provider );

		for ( ScriptProviderListener l: sp_listeners ){

			try{
				l.scriptProviderRemoved( provider );

			}catch( Throwable e ){

				Debug.out( e );
			}
		}
	}

	@Override
	public void
	addScriptProviderListener(
		ScriptProviderListener		listener )
	{
		sp_listeners.add( listener );

		for ( ScriptProvider lp: script_providers ){

			listener.scriptProviderAdded( lp );
		}
	}

	@Override
	public void
	removeScriptProviderListener(
		ScriptProviderListener		listener )
	{
		sp_listeners.remove( listener );
	}

	@Override
	public Tag
	lookupTag(
		String		name )
	{
		List<TagType> tts = TagManagerFactory.getTagManager().getTagTypes();

		for ( TagType tt: tts ){

			Tag t = tt.getTag( name, true );

			if ( t != null ){

				return( t );
			}
		}

		return( null );
	}

	@Override
	public List<DistributedDatabase>
	getDistributedDatabases(
		String[]		networks )
	{
		return( DDBaseImpl.getDDBs( networks, null ));
	}

	@Override
	public List<DistributedDatabase>
	getDistributedDatabases(
		String[]				networks,
		Map<String,Object>		options )
	{
		return( DDBaseImpl.getDDBs( networks, options ));
	}

	public interface
	PluginSubscriptionManager
	{
		public void
		requestSubscription(
			URL					url,
			Map<String,Object>	options );

		public void
		requestSubscription(
			SearchProvider 			sp,
			Map<String, Object> 	search_parameters )

			throws SubscriptionException;

		public PluginSubscription[]
		getSubscriptions(
			boolean	subscribed_only );
	}

	public interface
	PluginSubscription
	{
		public String
		getID();

		public String
		getName();

		public boolean
		isSearchTemplate();

		public PluginSubscriptionResult[]
		getResults(
			boolean		include_deleted );
	}

	public interface
	PluginSubscriptionResult
	{
		public Map<Integer,Object>
		toPropertyMap();

		public void
		setRead(
			boolean		read );

		public boolean
		getRead();
	}

	public interface
	runnableWithReturn<T>
	{
		public T
		run();
	}

	public interface
	runnableWithException<T extends Exception>
	{
		public void
		run()

			throws T;
	}

	public interface
	runnableWithReturnAndException<T,S extends Exception>
	{
		public T
		run()

			throws S;
	}

	public interface
	PluginLimitedRateGroupListener
	{
		public void
		disabledChanged(
			PluginLimitedRateGroup		group,
			boolean						is_disabled );

			/**
			 * Periodically called to allow sanity checks - shouldn't really be required
			 * @param group
			 * @param is_disabled
			 */

		public void
		sync(
			PluginLimitedRateGroup		group,
			boolean						is_disabled );
	}

	public static class
	PluginLimitedRateGroup
		implements LimitedRateGroup
	{
		private RateLimiter	limiter;

		private ConnectionManagerImpl.PluginRateLimiter plimiter;

		private CopyOnWriteList<PluginLimitedRateGroupListener>	listeners;

		/*
		 * For peer connections throttling up/down speed to zero to try and block upload/download has the
		 * unwanted effect of blocking protocol message flow and stalls the connection. the 'disable_disable'
		 * flag causes the rate limiter to inform listeners (peer connections) when flow should be disabled
		 * at the protocol (as opposed to byte) level and at the same time leaves the byte flow unlimited
		 * to ensure the connection doesn't stall
		 */

		private final boolean	disable_disable;

		private boolean	current_disabled = false;

		private long	last_sync;

		private
		PluginLimitedRateGroup(
			RateLimiter		_limiter,
			boolean			_disable_disable )
		{
			limiter	= _limiter;

			disable_disable	= _disable_disable;

			if ( limiter instanceof ConnectionManagerImpl.PluginRateLimiter ){

				plimiter = (ConnectionManagerImpl.PluginRateLimiter)limiter;
			}
		}

		public boolean
		isDisableDisable()
		{
			return( disable_disable );
		}

		public void
		addListener(
			PluginLimitedRateGroupListener		listener )
		{
			if ( disable_disable ){

					// in case things have changed but not been triggered yet...

				getRateLimitBytesPerSecond();

				synchronized( this ){

					if ( listeners == null ){

						listeners = new CopyOnWriteList<>();
					}

					listeners.add( listener );

					if ( current_disabled ){

						try{
							listener.disabledChanged( this, true );

						}catch( Throwable e ){

							Debug.out( e );
						}
					}
				}
			}
		}

		public void
		removeListener(
			PluginLimitedRateGroupListener		listener )
		{
			if ( disable_disable ){

				synchronized( this ){

					if ( listeners != null ){

						if ( listeners.remove( listener )){

							if ( current_disabled ){

								try{
									listener.disabledChanged( this, false );

								}catch( Throwable e ){

									Debug.out( e );
								}
							}
						}
					}
				}
			}
		}

		@Override
		public String
		getName()
		{
			String name = limiter.getName();

			if ( Constants.IS_CVS_VERSION ){

				if ( disable_disable ){

					String str = "";

					if ( current_disabled ){

						str += "Disabled";
					}

					synchronized( this ){

						if ( listeners != null ){

							str += (str.length()==0?"":"/") + listeners.size();
						}
					}

					if ( str.length() > 0 ){

						name += " (" + str + ")";
					}
				}
			}

			return( name );
		}

		@Override
		public int
		getRateLimitBytesPerSecond()
		{
			int	value = limiter.getRateLimitBytesPerSecond();

			if ( disable_disable ){

				boolean is_disabled = value == -1;

				if ( is_disabled != current_disabled ){

					synchronized( this ){

						current_disabled = is_disabled;

						if ( listeners != null ){

							for ( PluginLimitedRateGroupListener l: listeners ){

								try{
									l.disabledChanged( this, is_disabled );

								}catch( Throwable e ){

									Debug.out( e );
								}
							}
						}
					}
				}else{
					long now = SystemTime.getMonotonousTime();

					if ( now - last_sync > 60*1000 ){

						last_sync = now;

						synchronized( this ){

							if ( listeners != null ){

								for ( PluginLimitedRateGroupListener l: listeners ){

									try{
										l.sync( this, current_disabled );

									}catch( Throwable e ){

										Debug.out( e );
									}
								}
							}
						}
					}
				}

				return( is_disabled?0:value );

			}else{

				return( value );
			}
		}

		@Override
		public boolean
		isDisabled()
		{
			return( limiter.getRateLimitBytesPerSecond() < 0 );
		}

		@Override
		public void
		updateBytesUsed(
			int	used )
		{
			if ( plimiter != null ){

				plimiter.updateBytesUsed( used );
			}
		}
	}

	static class
	DelayedTaskImpl
		implements DelayedTask
	{
		private String 		name;
		private Runnable	target;

		private long	create_time = SystemTime.getCurrentTime();
		private long	run_time;

		private
		DelayedTaskImpl(
			String		_name )
		{
			name 	= _name;
		}

		public void
		setTask(
			Runnable		_target )
		{
			target	= _target;
		}

		@Override
		public void
		queue()
		{
			if ( target == null ){

				throw( new RuntimeException( "Target must be set before queueing" ));
			}

			queueTask( this, -1 );
		}

		@Override
		public void
		queueFirst()
		{
			if ( target == null ){

				throw( new RuntimeException( "Target must be set before queueing" ));
			}

			queueTask( this, 0 );
		}

		protected void
		run()
		{
			try{
				run_time = SystemTime.getCurrentTime();

				target.run();

				long now = SystemTime.getCurrentTime();

				if (Logger.isEnabled()) {
		     		Logger.log(
		     			new LogEvent(
		     				LogIDs.PLUGIN, LogEvent.LT_INFORMATION,
		     				"Delayed task '" + getName() +
		     					"': queue_time=" + ( run_time - create_time ) +
		     					", exec_time=" + ( now - run_time )));
				}
			}catch( Throwable e ){

				Debug.out( "Initialisation task " + getName() + " failed to complete", e );
			}
		}

		protected String
		getName()
		{
			return( name + " (" + target.getClass() + ")" );
		}

	}

	private static Map<String,JSONServer>	json_servers = new HashMap<>();
	private static  Map<String,JSONClient>	json_clients = new HashMap<>();

	@Override
	public void
	registerJSONRPCServer(
		final JSONServer		server )
	{
		String key = (pi==null?"default":pi.getPluginID()) + ":" + server.getName();

		synchronized( json_servers ){

			JSONServer existing = json_servers.get( key );

			if ( existing != null ){

				for ( JSONClient client: json_clients.values()){

					client.serverUnregistered( existing );
				}
			}

			json_servers.put( key, server );

			for ( JSONClient client: json_clients.values()){

				client.serverRegistered( server );
			}
		}
	}

	@Override
	public void
	unregisterJSONRPCServer(
		JSONServer		server )
	{
		String key = (pi==null?"default":pi.getPluginID()) + ":" + server.getName();

		synchronized( json_servers ){

			JSONServer existing = json_servers.remove( key );

			if ( existing != null ){

				for ( JSONClient client: json_clients.values()){

					client.serverUnregistered( existing );
				}
			}
		}
	}

	@Override
	public void
	registerJSONRPCClient(
		JSONClient		client )
	{
		String key = pi==null?"default":pi.getPluginID();

		synchronized( json_servers ){

			json_clients.put( key, client );

			for ( JSONServer server: json_servers.values()){

				client.serverRegistered( server );
			}
		}
	}

	@Override
	public void
	unregisterJSONRPCClient(
		JSONClient		client )
	{
		String key = pi==null?"default":pi.getPluginID();

		synchronized( json_servers ){

			json_clients.remove( key );
		}
	}

	private TagManagerImpl	tag_manager = new TagManagerImpl();

	@Override
	public TagManager
	getTagManager()
	{
		return( tag_manager );
	}


	private static class
	TagManagerImpl
		implements TagManager
	{
		@Override
		public List<Tag>
		getTags()
		{
			List<com.biglybt.core.tag.Tag> tags = TagManagerFactory.getTagManager().getTagType( TagType.TT_DOWNLOAD_MANUAL ).getTags();

			return( new ArrayList<Tag>( tags ));
		}

		@Override
		public Tag
		lookupTag(
			String name)
		{
			return( TagManagerFactory.getTagManager().getTagType( TagType.TT_DOWNLOAD_MANUAL ).getTag( name, true ));
		}

		@Override
		public Tag
		createTag(
			String		name )
		{
			try{
				return( TagManagerFactory.getTagManager().getTagType( TagType.TT_DOWNLOAD_MANUAL ).createTag(name, true ));

			}catch( Throwable e ){

				Debug.out( e );

				return( null );
			}
		}
	}
}
