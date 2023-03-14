/*
 * Created on 15-Dec-2004
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

package com.biglybt.core.proxy.impl;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.proxy.AEProxyAddressMapper;
import com.biglybt.core.proxy.AEProxyAddressMapper.PortMapping;
import com.biglybt.core.util.*;

/**
 * @author parg
 *
 */

public class
AEProxyAddressMapperImpl
	implements AEProxyAddressMapper
{
	protected static final AEProxyAddressMapper	singleton = new AEProxyAddressMapperImpl();

	public static AEProxyAddressMapper
	getSingleton()
	{
		return( singleton );
	}

	protected boolean	enabled;

	protected String	prefix;
	protected long		next_value;

	protected final Map<String,String>		map			= new HashMap<>();
	protected final Map<String,String>		reverse_map	= new HashMap<>();

	protected final AEMonitor	this_mon	= new AEMonitor( "AEProxyAddressMapper" );

	final Map<Integer,PortMappingImpl>	port_mappings = new HashMap<>();

	protected
	AEProxyAddressMapperImpl()
	{
	    if ( 	COConfigurationManager.getBooleanParameter("Enable.Proxy") &&
	    		COConfigurationManager.getBooleanParameter("Enable.SOCKS")){

	    	String	host = COConfigurationManager.getStringParameter("Proxy.Host");

	    	try{
		    	if ( 	host.length() > 0 &&
		    			InetAddress.getByName(host).isLoopbackAddress()){

		    		enabled	= true;

		    		byte[]	b = new byte[120];

		    		for (int i=0;i<b.length;i++){

		    			b[i] = (byte)(RandomUtils.nextInt(256));
		    		}

		    		prefix = ByteFormatter.encodeString( b );
		    	}
	    	}catch( Throwable e ){

	    		Debug.printStackTrace(e);
	    	}
	    }
	}

	@Override
	public String
	internalise(
		String	address )
	{
		if ( !enabled ){

			return( address );
		}

		if ( address.length() < 256 ){

			return( address );
		}

		String	target;

		try{
			this_mon.enter();

			target = reverse_map.get( address );

			if ( target == null ){

				StringBuilder target_b = new StringBuilder( 256 );

				target_b.append( prefix );
				target_b.append( next_value++ );

				while( target_b.length() < 255 ){

					target_b.append( "0" );
				}

				target = target_b.toString();

				map.put( target, address );

				reverse_map.put( address, target );
			}
		}finally{

			this_mon.exit();
		}

		// System.out.println( "AEProxyAddressMapper: internalise " + address + " -> " + target );

		return( target );
	}

	@Override
	public String
	externalise(
		String	address )
	{
		if ( !enabled || address.length() < 255 ){

			return( address );
		}

		String	target = map.get( address );

		if ( target == null ){

			target = address;
		}

		// System.out.println( "AEProxyAddressMapper: externalise " + address + " -> " + target );

		return( target );
	}

	@Override
	public URL
	internalise(
		URL		url )
	{
		if ( !enabled ){

			return( url );
		}

		String	host = url.getHost();

		if ( host.length() < 256 ){

			return( url );
		}

		String	new_host = internalise( host );

		String	url_str = url.toString();

		int	pos = url_str.indexOf( host );

		if ( pos == -1 ){

			Debug.out( "inconsistent url '" + url_str + "' / '" + host + "'" );

			return( url );
		}

		String 	new_url_str = url_str.substring(0,pos) +
				new_host + url_str.substring(pos+host.length());

		try{
			return( new URL( new_url_str ));

		}catch( MalformedURLException e ){

			Debug.printStackTrace(e);

			return( url );
		}
	}

	@Override
	public URL
	externalise(
		URL		url )
	{
		if ( !enabled ){

			return( url );
		}

		String	host	= url.getHost();

		if ( host.length() < 255 ){

			return( url );
		}

		String	new_host = externalise( host );

		String	url_str = url.toString();

		int	pos = url_str.indexOf( host );

		if ( pos == -1 ){

			Debug.out( "inconsistent url '" + url_str + "' / '" + host + "'" );

			return( url );
		}

		String 	new_url_str = url_str.substring(0,pos) +
				new_host + url_str.substring(pos+host.length());

		try{
			return( new URL( new_url_str ));

		}catch( MalformedURLException e ){

			Debug.printStackTrace(e);

			return( url );
		}
	}


	@Override
	public PortMapping
	registerPortMapping(
		int		proxy_port,
		String	remote_ip )
	{
		PortMappingImpl mapping = new PortMappingImpl( proxy_port, null, 6881, remote_ip, 6881, null );

		synchronized( port_mappings ){

			port_mappings.put( proxy_port, mapping );
		}

		return( mapping );
	}

	@Override
	public PortMapping
	registerPortMapping(
		int						proxy_port,
		String					remote_ip,
		Map<String,Object>		properties )
	{
		PortMappingImpl mapping = new PortMappingImpl( proxy_port, null, 6881, remote_ip, 6881, properties );

		synchronized( port_mappings ){

			port_mappings.put( proxy_port, mapping );
		}

		return( mapping );
	}

	@Override
	public PortMapping
	registerPortMapping(
		int						proxy_port,
		int						local_port,
		String					local_ip,
		int						remote_port,
		String					remote_ip,
		Map<String,Object>		properties )
	{ 
		PortMappingImpl mapping = new PortMappingImpl( proxy_port, local_ip, local_port, remote_ip, remote_port, properties );

		synchronized( port_mappings ){

			port_mappings.put( proxy_port, mapping );
		}

		return( mapping );
	}
	
	@Override
	public AppliedPortMapping
	applyPortMapping(
		InetAddress		address,
		int				port )
	{
		InetSocketAddress local_address;
		InetSocketAddress remote_address;

		PortMappingImpl mapping;

		synchronized( port_mappings ){

			mapping = port_mappings.get( port );
		}

		if ( mapping == null ){

			local_address	= null;
			remote_address 	= new InetSocketAddress( address, port );

		}else{

			InetAddress bind_ip = NetworkAdmin.getSingleton().getSingleHomedServiceBindAddress();

			if ( bind_ip == null || bind_ip.isAnyLocalAddress()){

				bind_ip = null;
			}

			if (	bind_ip == null && address.isLoopbackAddress() ||
					bind_ip != null && bind_ip.equals( address )){

				String remote_ip = mapping.getRemoteIP();

				if ( AENetworkClassifier.categoriseAddress( remote_ip ) == AENetworkClassifier.AT_PUBLIC ){

					local_address	= null;
					remote_address 	= new InetSocketAddress( remote_ip, port );

				}else{
					
					String local_ip = mapping.getLocalIP();
					
					if ( local_ip == null ){
						
						local_address	= null;
						
					}else{
						
						local_address = InetSocketAddress.createUnresolved( local_ip, mapping.getLocalPort());
					}
					
					remote_address = InetSocketAddress.createUnresolved( remote_ip, mapping.getRemotePort());
				}
				
			}else{

				local_address	= null;
				remote_address 	= new InetSocketAddress( address, port );
			}
		}

		//System.out.println( "Applying mapping: " + address + "/" + port + " -> " + result );

		return( new AppliedPortMappingImpl( local_address, remote_address, mapping==null?null:mapping.getProperties()));
	}

	@Override
	public InetSocketAddress
	getLocalAddress(
		InetSocketAddress		remote )
	{
		return( AEPluginProxyHandler.getLocalAddress( remote.getHostName(), remote.getPort()));
	}
	
	private class
	PortMappingImpl
		implements PortMapping
	{
		private final int					proxy_port;
		
		private final String				local_ip;
		private final int					local_port;
		private final String				remote_ip;
		private final int					remote_port;
		private final Map<String,Object>	properties;

		private
		PortMappingImpl(
			int					_proxy_port,
			String				_local_ip,
			int					_local_port,
			String				_remote_ip,
			int					_remote_port,
			Map<String,Object>	_properties )
		{
			proxy_port			= _proxy_port;
			local_ip			= _local_ip;
			local_port			= _local_port;
			remote_ip			= _remote_ip;
			remote_port			= _remote_port;
			properties			= _properties;
		}

		private String
		getLocalIP()
		{
			return( local_ip );
		}

		private int
		getLocalPort()
		{
			return( local_port );
		}

		private String
		getRemoteIP()
		{
			return( remote_ip );
		}

		private int
		getRemotePort()
		{
			return( remote_port );
		}
		
		public Map<String,Object>
		getProperties()
		{
			return( properties );
		}

		@Override
		public void
		unregister()
		{
			synchronized( port_mappings ){

				port_mappings.remove( proxy_port );
			}
		}
	}

	private static class
	AppliedPortMappingImpl
		implements AppliedPortMapping
	{
		private final InetSocketAddress		local_address;
		private final InetSocketAddress		remote_address;
		private final Map<String,Object>	properties;

		private
		AppliedPortMappingImpl(
			InetSocketAddress	_local_address,
			InetSocketAddress	_remote_address,
			Map<String,Object>	_properties )
		{
			local_address		= _local_address;
			remote_address		= _remote_address;
			properties			= _properties;
		}

		@Override
		public InetSocketAddress
		getRemoteAddress()
		{
			return( remote_address );
		}

		@Override
		public InetSocketAddress
		getLocalAddress()
		{
			return( local_address );
		}
		
		@Override
		public Map<String,Object>
		getProperties()
		{
			return( properties );
		}
	}
}
