/*
 * Created on 06-Dec-2004
 * Created by Paul Gardner
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

package com.biglybt.core.proxy;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.net.URL;
import java.util.List;
import java.util.Map;

import com.biglybt.core.Core;
import com.biglybt.core.proxy.impl.AEPluginProxyHandler;
import com.biglybt.core.proxy.impl.AEProxyAddressMapperImpl;
import com.biglybt.core.proxy.impl.AEProxyImpl;
import com.biglybt.pif.PluginInterface;
import com.biglybt.plugin.dht.DHTPluginInterface;

/**
 * @author parg
 *
 */

public class
AEProxyFactory
{
	public static void
	initialise(
		Core	core )
	{
		AEPluginProxyHandler.initialise( core );
	}
	
		/**
		 * @param port				0 = free port
		 * @param connect_timeout	0 = no timeout
		 * @param read_timeout		0 = no timeout
		 * @return
		 * @throws AEProxyException
		 */

	public static AEProxy
	create(
		int					port,
		long				connect_timeout,
		long				read_timeout,
		AEProxyHandler		state_factory )

		throws AEProxyException
	{
		return( new AEProxyImpl(port,connect_timeout,read_timeout,state_factory));
	}

	public static AEProxyAddressMapper
	getAddressMapper()
	{
		return( AEProxyAddressMapperImpl.getSingleton());
	}

	public static final String PO_PEER_NETWORKS 		= "peer_networks";			// String[]
	public static final String PO_LOCAL_PORT			= "local_port";				// Integer
	public static final String PO_EXPLICIT_BIND			= "explicit_bind";			// InetAddress
	public static final String PO_PREFERRED_PROXY_TYPE	= "preferred_proxy_type";	// String SOCKS(default) or HTTP
	public static final String PO_FORCE_PROXY			= "force_proxy";			// Boolean
	
	public static PluginProxy
	getPluginProxy(
		String		reason,
		URL			target )
	{
		return( getPluginProxy( reason, target, false));
	}

	public static PluginProxy
	getPluginProxy(
		String		reason,
		URL			target,
		boolean		can_wait )
	{
		return( getPluginProxy( reason, target, null, can_wait ));
	}

	public static PluginProxy
	getPluginProxy(
		String				reason,
		URL					target,
		Map<String,Object>	proxy_options )
	{
		return( getPluginProxy( reason, target, proxy_options, false ));
	}
	
	public static PluginProxy
	getPluginProxy(
		String				reason,
		URL					target,
		Map<String,Object>	proxy_options,
		boolean				can_wait )
	{
		return( AEPluginProxyHandler.getPluginProxy( reason, target, proxy_options, can_wait ));
	}

	public static PluginProxy
	getPluginProxy(
		String		reason,
		String		host,
		int			port )
	{
		return( getPluginProxy( reason, host, port, null ));
	}

	public static PluginProxy
	getPluginProxy(
		String				reason,
		String				host,
		int					port,
		Map<String,Object>	proxy_options )
	{
		return( AEPluginProxyHandler.getPluginProxy( reason, host, port, proxy_options ));
	}

	public static PluginProxy
	getPluginProxy(
		Proxy		proxy )
	{
		return( AEPluginProxyHandler.getPluginProxy( proxy ));
	}

	public static boolean
	isPluginProxy(
		SocketAddress		address )
	{
		return( AEPluginProxyHandler.isPluginProxy( address ));
	}

	public static Boolean
	testPluginHTTPProxy(
		URL				target,
		boolean			can_wait,
		String			reason )
	{
		return( AEPluginProxyHandler.testPluginHTTPProxy( target, can_wait, reason ));

	}

	public static PluginHTTPProxy
	getPluginHTTPProxy(
		String			reason,
		URL				target,
		boolean			can_wait )
	{
		return( AEPluginProxyHandler.getPluginHTTPProxy( reason, target, can_wait ));
	}

	public static List<PluginInterface>
	getPluginHTTPProxyProviders(
		boolean	can_wait )
	{
		return( AEPluginProxyHandler.getPluginHTTPProxyProviders( can_wait ));
	}

	public static boolean
	hasPluginProxy()
	{
		return( AEPluginProxyHandler.hasPluginProxy());
	}

	public static final String	SP_HOST				= "host";
	public static final String	SP_PORT				= "port";
	public static final String	SP_REMOTE_PORT		= "remote-port";
	public static final String	SP_BIND				= "bind";

	public static Map<String,Object>
	getPluginServerProxy(
		String					reason,
		String					network,
		String					server_uid,
		Map<String,Object>		options )
	{
		return( AEPluginProxyHandler.getPluginServerProxy( reason, network, server_uid, options ));
	}

	public static final String	DP_DOWNLOAD		= "download";
	public static final String	DP_NETWORKS		= "networks";


	public static DHTPluginInterface
	getPluginDHTProxy(
		String					reason,
		String					network,
		Map<String,Object>		options )
	{
		return( AEPluginProxyHandler.getPluginDHTProxy( reason, network, options ));

	}

	public interface
	PluginProxy
	{
		public static final int ST_UNKNOWN	= 1;
		public static final int ST_OK		= 2;
		public static final int ST_BAD		= 3;
		
		public String
		getTarget();

		public PluginProxy
		getChildProxy(
			String		reason,
			URL			url );

		public Proxy
		getProxy();

		public URL
		getURL();

		public String
		getURLHostRewrite();

		public String
		getHost();

		public int
		getPort();

		public InetSocketAddress
		getLocalAddress();
		
		public void
		setOK(
			boolean	good );
		
		public int
		getStatus();
		
		public boolean
		getConnected();
		
		public Throwable
		getError();
		
		public void
		addListener(
			PluginProxyStatusListener		l );
		
		public interface
		PluginProxyStatusListener
		{
			public void
			statusChanged(
				PluginProxy pp );
		}
	}

	public interface
	PluginHTTPProxy
	{
		public Proxy
		getProxy();

		public String
		proxifyURL(
			String		url );

		public void
		destroy();
	}


	public static class
	UnknownHostException
		extends RuntimeException
	{
		public
		UnknownHostException(
			String	host )
		{
			super( host );
		}
	}
}
