/*
 * Created on Nov 1, 2012
 * Created by Paul Gardner
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


package com.biglybt.core.proxy.impl;

import java.io.IOException;
import java.net.*;
import java.util.*;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.config.COConfigurationListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.proxy.AEProxyFactory;
import com.biglybt.core.proxy.AEProxySelector;
import com.biglybt.core.util.*;

public class
AEProxySelectorImpl
	extends ProxySelector
	implements AEProxySelector
{
	private static final boolean	LOG = false;

	private static final String NL = "\r\n";

	private static final AEProxySelectorImpl		singleton = new AEProxySelectorImpl();

	private static final List<Proxy>		no_proxy_list = Collections.singletonList( Proxy.NO_PROXY);

	private static final ThreadLocal<Integer>		tls	=
		new ThreadLocal<Integer>()
		{
			@Override
			public Integer
			initialValue()
			{
				return( 0 );
			}
		};

	public static AEProxySelector
	getSingleton()
	{
		return( singleton );
	}

	private final ProxySelector			existing_selector;

	private volatile ActiveProxy		active_proxy;
	private volatile List<String>		alt_dns_servers	= new ArrayList<>();

	private final CopyOnWriteMap<String,List<Proxy>>	explicit_proxy_map = new CopyOnWriteMap<>();

	private
	AEProxySelectorImpl()
	{
		COConfigurationManager.addAndFireListener(
				new COConfigurationListener()
				{
					@Override
					public void
					configurationSaved()
					{
						boolean	enable_proxy 	= COConfigurationManager.getBooleanParameter("Enable.Proxy");
					    boolean enable_socks	= COConfigurationManager.getBooleanParameter("Enable.SOCKS");

					    String	proxy_host 	= null;
					    int		proxy_port	= -1;

					    if ( enable_proxy && enable_socks ){

					    	proxy_host 		= COConfigurationManager.getStringParameter("Proxy.Host").trim();
					    	try {
					    		proxy_port 		= Integer.parseInt(COConfigurationManager.getStringParameter("Proxy.Port").trim());
					    	} catch (Exception e) {
					    		// User got NumberFormatException and Vuze Failed to Launch
					    	}

							if ( proxy_host.length() == 0 ){

								proxy_host = null;
							}

							if ( proxy_port <= 0 || proxy_port > 65535 ){

								proxy_host = null;
							}
					    }

					    List<String>	new_servers = new ArrayList<>();

					    if ( COConfigurationManager.getBooleanParameter( "DNS Alt Servers SOCKS Enable" )){

						    String	alt_servers = COConfigurationManager.getStringParameter( "DNS Alt Servers" );

						    alt_servers = alt_servers.replace( ',', ';' );

						    String[] servers = alt_servers.split(  ";" );

						    for ( String s: servers ){

						    	s = s.trim();

						    	if ( s.length() > 0 ){

						    		new_servers.add( s );
						    	}
						    }
					    }

					    synchronized( AEProxySelectorImpl.this ){

					    	boolean	servers_changed = false;

					    	if ( alt_dns_servers.size() != new_servers.size()){

					    		servers_changed = true;

					    	}else{

					    		for ( String s: new_servers ){

					    			if ( !alt_dns_servers.contains( s )){

					    				servers_changed = true;

					    				break;
					    			}
					    		}
					    	}

					    	if ( servers_changed ){

					    		alt_dns_servers = new_servers;
					    	}

					    	if ( proxy_host == null ){

					    		if ( active_proxy != null ){

					    			active_proxy = null;
					    		}
					    	}else{
						    	if ( 	active_proxy == null ||
						    			!active_proxy.sameAddress( proxy_host, proxy_port )){

						    		active_proxy = new ActiveProxy( proxy_host, proxy_port, new_servers );

						    	}else{

						    		if ( servers_changed ){

						    			active_proxy.updateServers( new_servers );
						    		}
						    	}
					    	}
					    }
					}
				});

		existing_selector = ProxySelector.getDefault();

		try{
			ProxySelector.setDefault( this );

		}catch( Throwable e ){

			Debug.out( e );
		}

		CoreFactory.addCoreRunningListener(
				new CoreRunningListener()
				{
					@Override
					public void
					coreRunning(
						Core core )
					{
						try{
								// decouple SWT stuff from core

							Class.forName("com.biglybt.ui.swt.core.proxy.AEProxySelectorSWTImpl").getConstructor(
								new Class[]{ Core.class, AEProxySelectorImpl.class }).newInstance(
										new Object[]{ core,AEProxySelectorImpl.this });

							//new AEProxySelectorSWTImpl( core, AEProxySelectorImpl.this );

						}catch( Throwable e ){

						}
					}
				});
	}

	@Override
	public void
	startNoProxy()
	{
		tls.set( tls.get() + 1  );
	}

	@Override
	public void
	endNoProxy()
	{
		tls.set( tls.get() - 1  );
	}

	@Override
	public Proxy
	setProxy(
		InetSocketAddress		address,
		Proxy					proxy )
	{
		List<Proxy> p = new ArrayList<>();

		p.add( proxy );

		String address_str = AddressUtils.getHostNameNoResolve( address ) + ":" + address.getPort();

		List<Proxy> old = explicit_proxy_map.put( address_str, Collections.unmodifiableList( p ));

		if ( old != null ){

			return( old.get(0));

		}else{

			return( null );
		}
	}

	@Override
	public Proxy
	removeProxy(
		InetSocketAddress		address )
	{
		String address_str = AddressUtils.getHostNameNoResolve( address ) + ":" + address.getPort();

		List<Proxy> old = explicit_proxy_map.remove( address_str );

		if ( old != null ){

			return( old.get(0));

		}else{

			return( null );
		}
	}

	@Override
	public List<Proxy>
	select(
		URI uri )
	{
		List<Proxy>  result;

		if ( explicit_proxy_map.size() > 0 ){

			try{
				String	host 	= uri.getHost();

				if ( host != null ){

					int port 		= uri.getPort();

					if ( port == -1 ){

						String scheme = uri.getScheme();

						if ( scheme != null ){

							scheme = scheme.toLowerCase( Locale.US );

							if ( scheme.equals( "http" )){

								port = 80;

							}else if ( scheme.equals( "https" )){

								port = 443;
							}
						}
					}

					if ( port != -1 ){

						List<Proxy> p = explicit_proxy_map.get( host + ":" + port );

						if ( p != null ){

							return( p );
						}
					}
				}
			}catch( Throwable e ){

				e.printStackTrace();
			}
		}

		if ( tls.get() > 0 ){

			result = no_proxy_list;

		}else{

			result = selectSupport( uri );

			String host = uri.getHost();

			if ( host != null ){

				if ( host.endsWith( ".i2p" ) || host.endsWith( ".onion" )){

					List<Proxy> trimmed = new ArrayList<>(result.size());

					for ( Proxy p: result ){

						if ( p.type() != Proxy.Type.DIRECT ){

							trimmed.add( p );
						}
					}

					if ( trimmed.size() == 0 ){

						throw( new AEProxyFactory.UnknownHostException( host ));
					}
				}
			}
		}

		if ( LOG ){
			System.out.println( "select: " + uri + " -> " + result );
		}

		return( result );
	}

	private List<Proxy>
	selectSupport(
		URI uri )
	{
		ActiveProxy active = active_proxy;

		if ( active == null ){

			if ( existing_selector == null ){

				return( no_proxy_list );
			}

			List<Proxy> proxies = existing_selector.select( uri );

			if ( !proxies.isEmpty()){
			
					// have to copy as returned in not modifiable...
				
				proxies = new ArrayList<>( proxies );
			}
			
			Iterator<Proxy> it = proxies.iterator();

			while( it.hasNext()){

				Proxy p = it.next();

				if ( p.type() == Proxy.Type.SOCKS ){

					it.remove();
				}
			}

			if ( proxies.size() > 0 ){

				return( proxies );
			}

			return( no_proxy_list );
		}

			// we don't want to be recursing on this!

		if ( alt_dns_servers.contains( uri.getHost())){

			return( no_proxy_list );
		}

			// bit mindless this but the easiest way to see if we should apply socks proxy to this URI is to hit the existing selector
			// and see if it would (take a look at http://www.docjar.com/html/api/sun/net/spi/DefaultProxySelector.java.html)....
			// requires the existing one to be picking up socks details which requires a restart after enabling but this is no worse than current...

		if ( existing_selector != null ){

			List<Proxy> proxies = existing_selector.select( uri );

			boolean	apply = false;

			for ( Proxy p: proxies ){

				if ( p.type() == Proxy.Type.SOCKS ){

					apply = true;

					break;
				}
			}

			if ( !apply ){

				return( no_proxy_list );
			}
		}

		return( Collections.singletonList( active.select()));
	}

	private void
	connectFailed(
		SocketAddress	sa,
		Throwable 		error )
	{
		ActiveProxy active = active_proxy;

		if ( active == null || !( sa instanceof InetSocketAddress )){

			return;
		}

		active.connectFailed((InetSocketAddress)sa, error );
	}

	@Override
	public void
	connectFailed(
		URI 			uri,
		SocketAddress 	sa,
		IOException 	ioe )
	{
		connectFailed( sa, ioe  );

		if ( existing_selector != null ){

			existing_selector.connectFailed( uri, sa, ioe );
		}
	}

	@Override
	public Proxy
	getSOCKSProxy(
		String				host,
		int					port,
		InetSocketAddress	target )
	{
		InetSocketAddress isa = new InetSocketAddress( host, port );

		return( getSOCKSProxy( isa, target ));
	}

	@Override
	public Proxy
	getSOCKSProxy(
		InetSocketAddress	isa,
		InetSocketAddress	target )
	{
		Proxy result;

		if ( tls.get() > 0 ){

			result = Proxy.NO_PROXY;

		}else{

			ActiveProxy active = active_proxy;

			if ( 	active == null ||
					!active.getAddress().equals( isa )){

				result = new Proxy( Proxy.Type.SOCKS, isa );

			}else{

				result = active.select();
			}
		}

		if ( LOG ){
			System.out.println( "select: " + target + " -> " + result );
		}

		return( result );
	}

	@Override
	public Proxy
	getActiveProxy()
	{
		String proxy = System.getProperty( "socksProxyHost", "" );

		if ( proxy.trim().length() == 0 ){

			return( null );
		}

		ActiveProxy active = active_proxy;

		if ( active == null ){

			return( null );
		}

		return( new Proxy( Proxy.Type.SOCKS, active.getAddress()));
	}

	@Override
	public void
	connectFailed(
		Proxy			proxy,
		Throwable		error )
	{
		connectFailed( proxy.address(), error );
	}

	@Override
	public long
	getLastConnectionTime()
	{
		ActiveProxy active = active_proxy;

		if ( active == null ){

			return( -1 );
		}

		return( active.getLastConnectionTime());
	}

	@Override
	public long
	getLastFailTime()
	{
		ActiveProxy active = active_proxy;

		if ( active == null ){

			return( -1 );
		}

		return( active.getLastFailTime());
	}

	@Override
	public int
	getConnectionCount()
	{
		ActiveProxy active = active_proxy;

		if ( active == null ){

			return( 0 );
		}

		return( active.getConnectionCount());
	}

	@Override
	public int
	getFailCount()
	{
		ActiveProxy active = active_proxy;

		if ( active == null ){

			return( 0 );
		}

		return( active.getFailCount());
	}

	@Override
	public String
	getInfo()
	{
		ActiveProxy active = active_proxy;

		if ( active == null || getActiveProxy() == null ){

			return( "No proxy active" );
		}

		return( active.getInfo());
	}

	private static class
	ActiveProxy
	{
		private static final int	DNS_RETRY_MILLIS = 15*60*1000;

		private final String				proxy_host;
		private final int 					proxy_port;

		private final InetSocketAddress		address;

		private volatile List<MyProxy>		proxy_list_cow 	= new ArrayList<>();

		private Boolean				alt_dns_enable;

		private List<String>		alt_dns_to_try;
		private final Map<String,Long>	alt_dns_tried		= new HashMap<>();

		private long				default_dns_tried_time	= -1;

		private volatile long	last_connection_time	= -1;
		private volatile int 	connection_count		= 0;
		private volatile long	last_fail_time			= -1;
		private volatile int	fail_count				= 0;

		private
		ActiveProxy(
			String			_proxy_host,
			int				_proxy_port,
			List<String>	_servers )
		{
			proxy_host		= _proxy_host;
			proxy_port		= _proxy_port;
	   		alt_dns_to_try 	= _servers;

			address	= new InetSocketAddress( proxy_host, proxy_port );

    		proxy_list_cow.add( new MyProxy( address ));
 		}

		public String
		getInfo()
		{
			StringBuilder sb = new StringBuilder(2048);

			long now		= SystemTime.getCurrentTime();
			long mono_now 	= SystemTime.getMonotonousTime();

			sb.append("Proxy: ").append(address).append(NL);
			sb.append("Last connection attempt: ")
				.append(last_connection_time == -1 ? "Never" : (new Date(now - (mono_now - last_connection_time)))).append(NL);
			sb.append("Last failure: ").append(last_fail_time == -1 ? "Never" : (new Date(now - (mono_now - last_fail_time)))).append(NL);
			sb.append("Total connections: ").append(connection_count).append(NL);
			sb.append("Total failures: ").append(fail_count).append(NL);

			List<MyProxy> proxies = new ArrayList<>(proxy_list_cow);

			sb.append( NL );

			for ( MyProxy p: proxies ){

				sb.append(p.getInfo(now, mono_now)).append(NL);
			}

			return( sb.toString());
		}

		private void
		updateServers(
			List<String>	servers )
		{
			synchronized( this ){

				alt_dns_to_try	= servers;

				alt_dns_tried.clear();
			}
		}

		private boolean
		sameAddress(
			String	host,
			int		port )
		{
			return( host.equals( proxy_host ) && port == proxy_port );
		}

		private InetSocketAddress
		getAddress()
		{
			return( address );
		}

		public long
		getLastConnectionTime()
		{
			return( last_connection_time );
		}

		public int
		getConnectionCount()
		{
			return( connection_count );
		}

		public long
		getLastFailTime()
		{
			return( last_fail_time );
		}

		public int
		getFailCount()
		{
			return( fail_count );
		}

		private MyProxy
		select()
		{
				// only return one proxy - this avoids the Java runtime from cycling through a bunch of
				// them that fail in the same way (e.g. if the address being connected to is unreachable) and
				// thus slugging everything

			MyProxy proxy = proxy_list_cow.get( 0 );

			proxy.handedOut();

			last_connection_time = SystemTime.getMonotonousTime();

			connection_count++;

			return( proxy );
		}

		private void
		connectFailed(
			InetSocketAddress	failed_isa,
			Throwable 			error )
		{
			String msg = Debug.getNestedExceptionMessage( error ).toLowerCase();

				// filter out errors that are not associated with the socks server itself but rather then destination

			if ( 	msg.contains( "unreachable" ) ||
					msg.contains( "operation on nonsocket" )){

				return;
			}

			long	now_mono = SystemTime.getMonotonousTime();

			if ( LOG ){
				System.out.println( "failed: " + failed_isa + " -> " + msg );
			}

			synchronized( this ){

				InetAddress	failed_ia 		= failed_isa.getAddress();
				String		failed_hostname = failed_ia==null?failed_isa.getHostName():null;	// avoid reverse DNS lookup if resolved

				MyProxy	matching_proxy = null;

				List<MyProxy>	new_list = new ArrayList<>();

				Set<InetAddress>	existing_addresses = new HashSet<>();

					// stick the failed proxy at the end of the list

				boolean	all_failed = true;

				for ( MyProxy p: proxy_list_cow ){

					InetSocketAddress p_isa = (InetSocketAddress)p.address();

					InetAddress	p_ia 		= p_isa.getAddress();
					String		p_hostname 	= p_ia==null?p_isa.getHostName():null;	// avoid reverse DNS lookup if resolved

					if ( p_ia != null ){

						existing_addresses.add( p_ia );
					}

					if ( 	( failed_ia != null && failed_ia.equals( p_ia )) ||
							( failed_hostname != null && failed_hostname.equals( p_hostname ))){

						matching_proxy = p;

						matching_proxy.setFailed();

					}else{

						new_list.add( p );
					}

					if ( p.getFailCount() == 0 ){

						all_failed = false;
					}
				}

				if ( matching_proxy == null ){

						// can happen when switching proxy config

					if ( LOG ){
						System.out.println( "No proxy match for " + failed_isa );
					}
				}else{

					last_fail_time	= now_mono;

					fail_count++;

						// stick it at the end of the list

					new_list.add( matching_proxy );
				}

				DNSUtils.DNSUtilsIntf dns_utils = DNSUtils.getSingleton();

				if ( dns_utils != null && all_failed ){

					DNSUtils.DNSDirContext	dns_to_try = null;

						// make sure the host isn't an IP address...

					if ( alt_dns_enable == null ){

						alt_dns_enable = HostNameToIPResolver.hostAddressToBytes( proxy_host ) == null;
					}

					if ( alt_dns_enable ){

						if ( 	default_dns_tried_time == -1 ||
								now_mono - default_dns_tried_time >= DNS_RETRY_MILLIS ){

							default_dns_tried_time = now_mono;

							if ( failed_ia != null ){

									// the proxy resolved so at least the name appears valid so we might as well try the system DNS before
									// moving onto possible others

								try{
									dns_to_try = dns_utils.getInitialDirContext();

								}catch( Throwable e ){

									Debug.out( e );
								}
							}
						}

						if ( dns_to_try == null ){

							if ( alt_dns_to_try.size() == 0 ){

								Iterator<Map.Entry<String,Long>> it = alt_dns_tried.entrySet().iterator();

								while( it.hasNext()){

									Map.Entry<String,Long> entry = it.next();

									if ( now_mono - entry.getValue() >= DNS_RETRY_MILLIS ){

										it.remove();

										alt_dns_to_try.add( entry.getKey());
									}
								}
							}

							if ( alt_dns_to_try.size() > 0 ){

								String try_dns = alt_dns_to_try.remove( 0 );

								alt_dns_tried.put( try_dns, now_mono );

								try{
									dns_to_try = dns_utils.getDirContextForServer( try_dns );

								}catch( Throwable e ){

									Debug.out( e );
								}
							}
						}

						if ( dns_to_try != null ){

							try{
								List<InetAddress> addresses = dns_utils.getAllByName( dns_to_try, proxy_host );

								if ( LOG ){
									System.out.println( "DNS " + dns_to_try.getString() + " resolve for " + proxy_host + " returned " + addresses );
								}

								Collections.shuffle( addresses );

								for ( InetAddress a: addresses ){

									if ( !existing_addresses.contains( a )){

										new_list.add( 0, new MyProxy( new InetSocketAddress( a, proxy_port )));
									}
								}
							}catch( Throwable e ){

								Debug.out( e );
							}
						}
					}
				}

				proxy_list_cow = new_list;
			}
		}
	}

	private static class
	MyProxy
		extends Proxy
	{
		private int		use_count	= 0;
		private int		fail_count	= 0;

		private long	last_use;
		private long	last_fail;

		private
		MyProxy(
			InetSocketAddress	address )
		{
			super( Proxy.Type.SOCKS, address );
		}

		private void
		handedOut()
		{
			use_count++;

			last_use	= SystemTime.getMonotonousTime();
		}

		private void
		setFailed()
		{
			fail_count++;

			last_fail	= SystemTime.getMonotonousTime();
		}

		private int
		getFailCount()
		{
			return( fail_count );
		}

		private String
		getInfo(
			long	now,
			long	mono_now )
		{
			InetSocketAddress address = (InetSocketAddress)address();

			InetAddress ia = address.getAddress();

			String str = ia==null?address.getHostName():ia.getHostAddress();

			if ( last_use > 0 ){

				str += NL + "\tcons=" + use_count + ", last=" + new Date( now - ( mono_now - last_use ));
			}
			if ( last_fail > 0 ){

				str += NL + "\tfails=" + fail_count + ", last=" + new Date( now - ( mono_now - last_fail ));
			}

			return( str );
		}
	}
}
