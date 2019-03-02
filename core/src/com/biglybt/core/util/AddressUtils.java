/*
 * Created on 04-Jan-2006
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

package com.biglybt.core.util;

import java.net.*;
import java.security.MessageDigest;
import java.util.*;

import org.gudy.bouncycastle.util.encoders.Base64;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.instancemanager.ClientInstance;
import com.biglybt.core.instancemanager.ClientInstanceManager;
import com.biglybt.core.proxy.AEProxyFactory;

public class
AddressUtils
{
	public static final byte LAN_LOCAL_MAYBE	= 0;
	public static final byte LAN_LOCAL_YES		= 1;
	public static final byte LAN_LOCAL_NO		= 2;

	private static boolean	i2p_is_lan_limit;

	static{
		COConfigurationManager.addAndFireParameterListener(
			"Plugin.azneti2phelper.azi2phelper.rates.use.lan",
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					String parameterName )
				{
					i2p_is_lan_limit = COConfigurationManager.getBooleanParameter( "Plugin.azneti2phelper.azi2phelper.rates.use.lan", false );
				}
			});
	}

	private static ClientInstanceManager instance_manager;

	private static ClientInstanceManager
	getInstanceManager()
	{
		if ( instance_manager == null ){

			if ( CoreFactory.isCoreAvailable()){

				try{
					instance_manager = CoreFactory.getSingleton().getInstanceManager();

				}catch( Throwable e ){

					// Debug.printStackTrace(e);
				}
			}
		}

		return( instance_manager );
	}

	private static Map	host_map = null;

	public static URL
	adjustURL(
		URL		url )
	{
		url = AEProxyFactory.getAddressMapper().internalise( url );

		if ( host_map != null ){

			String	rewrite = (String)host_map.get( url.getHost());

			if ( rewrite != null ){

				String str = url.toExternalForm();

				try{
					int pos = str.indexOf( "//" ) + 2;

					int pos2 = str.indexOf( "/", pos );

					String	host_bit = str.substring( pos, pos2 );

					int	pos3 = host_bit.indexOf(':');

					String	port_bit;

					if ( pos3 == -1 ){

						port_bit = "";

					}else{

						port_bit = host_bit.substring(pos3);
					}

					String new_str = str.substring(0,pos) + rewrite + port_bit + str.substring( pos2 );

					url = new URL( new_str );

				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}
			}
		}

		return( url );
	}

	public static synchronized void
	addHostRedirect(
		String	from_host,
		String	to_host )
	{
		System.out.println( "AddressUtils::addHostRedirect - " + from_host + " -> " + to_host );

		Map	new_map;

		if ( host_map == null ){

			new_map = new HashMap();
		}else{

			new_map = new HashMap( host_map );
		}

		new_map.put( from_host, to_host );

		host_map = new_map;
	}

	public static InetSocketAddress
	adjustTCPAddress(
		InetSocketAddress	address,
		boolean				ext_to_lan )
	{
		return( adjustAddress( address, ext_to_lan, ClientInstanceManager.AT_TCP ));
	}

	public static InetSocketAddress
	adjustUDPAddress(
		InetSocketAddress	address,
		boolean				ext_to_lan )
	{
		return( adjustAddress( address, ext_to_lan, ClientInstanceManager.AT_UDP ));
	}

	public static InetSocketAddress
	adjustDHTAddress(
		InetSocketAddress	address,
		boolean				ext_to_lan )
	{
		return( adjustAddress( address, ext_to_lan, ClientInstanceManager.AT_UDP_NON_DATA ));
	}

	private static InetSocketAddress
	adjustAddress(
		InetSocketAddress	address,
		boolean				ext_to_lan,
		int					port_type )
	{
		ClientInstanceManager im = getInstanceManager();

		if ( im == null || !im.isInitialized()){

			return( address );
		}

		InetSocketAddress	adjusted_address;

		if ( ext_to_lan ){

			adjusted_address	= im.getLANAddress( address, port_type );

		}else{

			adjusted_address	= im.getExternalAddress( address, port_type );
		}

		if ( adjusted_address == null ){

			adjusted_address	= address;

		}else{

			// System.out.println( "adj: " + address + "/" + ext_to_lan + " -> " + adjusted_address );
		}

		return( adjusted_address );
	}

	public static List<String>
	getLANAddresses(
		String		address )
	{
		List<String>	result = new ArrayList<>();

		result.add( address );

		try{
			InetAddress ad = InetAddress.getByName( address );

			if ( isLANLocalAddress( address ) != LAN_LOCAL_NO ){

				ClientInstanceManager im = getInstanceManager();

				if ( im == null || !im.isInitialized()){

					return( result );
				}

				ClientInstance[] instances = im.getOtherInstances();

				for (int i=0;i<instances.length;i++){

					ClientInstance instance = instances[i];

					List addresses = instance.getInternalAddresses();

					if ( addresses.contains( ad )){

						for ( int j=0; j<addresses.size();j++){

							InetAddress ia = (InetAddress)addresses.get(j);

							String str = ia.getHostAddress();

							if ( !result.contains( str )){

								result.add( str );
							}
						}
					}
				}
			}
		}catch( Throwable e ){

		}

		return( result );
	}

	public static byte
	isLANLocalAddress(
		InetSocketAddress	socket_address )
	{
		InetAddress address = socket_address.getAddress();

		return( isLANLocalAddress( address ));
	}

	public static byte
	isLANLocalAddress(
		InetAddress	address )

	{
			// if someone passes us an unresolved address then handle sensibly

		if ( address == null ){

			return( LAN_LOCAL_NO );
		}

		ClientInstanceManager im = getInstanceManager();

		if ( im == null || !im.isInitialized()){

			return( LAN_LOCAL_MAYBE );
		}

		return( im.isLANAddress( address )? LAN_LOCAL_YES:LAN_LOCAL_NO);
	}

	public static byte
	isLANLocalAddress(
		String address )
	{
		byte is_lan_local = LAN_LOCAL_MAYBE;

		try {
			is_lan_local = isLANLocalAddress( HostNameToIPResolver.syncResolve( address ));

		}catch( UnknownHostException e ){

		}catch( Throwable t ){

			t.printStackTrace();
		}

		return is_lan_local;
	}

	private static Set<InetAddress>	pending_addresses = new HashSet<>();
	private static TimerEventPeriodic	pa_timer;

	public static void
	addLANRateLimitAddress(
		InetAddress		address )
	{
		synchronized( pending_addresses ){

			ClientInstanceManager im = getInstanceManager();

			if ( im == null || !im.isInitialized()){

				pending_addresses.add( address );

				if ( pa_timer == null ){

					pa_timer =
						SimpleTimer.addPeriodicEvent(
							"au:pa",
							250,
							new TimerEventPerformer() {

								@Override
								public void
								perform(
									TimerEvent event)
								{
									synchronized( pending_addresses ){

										ClientInstanceManager im = getInstanceManager();

										if ( im != null && im.isInitialized()){

											for ( InetAddress address : pending_addresses ){

												try{
													im.addLANAddress( address );

												}catch( Throwable e ){

												}
											}

											pending_addresses.clear();

											pa_timer.cancel();

											pa_timer = null;
										}
									}
								}
							});
				}
			}else{

				im.addLANAddress( address );
			}
		}
	}

	public static void
	removeLANRateLimitAddress(
		InetAddress		address )
	{
		synchronized( pending_addresses ){

			ClientInstanceManager im = getInstanceManager();

			if ( im == null || !im.isInitialized()){

				pending_addresses.remove( address );

			}else{

				im.removeLANAddress( address );
			}
		}
	}

	public static boolean
	applyLANRateLimits(
		InetSocketAddress			address )
	{
		if ( i2p_is_lan_limit ){

			if ( address.isUnresolved()){

				return( AENetworkClassifier.categoriseAddress( address ) == AENetworkClassifier.AT_I2P );
			}
		}

		return( false );
	}

	/**
	 * checks if the provided address is a global-scope ipv6 unicast address
	 */
	public static boolean isGlobalAddressV6(InetAddress addr) {
		return addr instanceof Inet6Address && !addr.isAnyLocalAddress() && !addr.isLinkLocalAddress() && !addr.isLoopbackAddress() && !addr.isMulticastAddress() && !addr.isSiteLocalAddress() && !((Inet6Address)addr).isIPv4CompatibleAddress();
	}

	public static boolean isTeredo(InetAddress addr)
	{
		if(!(addr instanceof Inet6Address))
			return false;
		byte[] bytes = addr.getAddress();
		// check for the 2001:0000::/32 prefix, i.e. teredo
		return bytes[0] == 0x20 && bytes[1] == 0x01 && bytes[2] == 0x00 && bytes[3] == 0x00;
	}

	public static boolean is6to4(InetAddress addr)
	{
		if(!(addr instanceof Inet6Address))
			return false;
		byte[] bytes = addr.getAddress();
		// check for the 2002::/16 prefix, i.e. 6to4
		return bytes[0] == 0x20 && bytes[1] == 0x02;
	}

	/**
	 * picks 1 global-scoped address out of a list based on the heuristic
	 * "true" ipv6/tunnel broker > 6to4 > teredo
	 *
	 * @return empty list if no proper v6 address is found, best one otherwise
	 */

	public static List<InetAddress> 
	pickBestGlobalV6Addresses(
		List<InetAddress> 	addrs )
	{
		List<InetAddress> bestPicks = new ArrayList<>();;
		
		int currentRanking = 0;
		
		for ( InetAddress addr : addrs){
			
			if (!isGlobalAddressV6(addr)){
				
				continue;
			}
			
			int ranking;
			
			if (isTeredo(addr)){
				
				ranking = 1;
				
			}else if(is6to4(addr)){
				
				ranking = 2;
				
			}else{
				
				ranking = 3;
			}

			if ( ranking > currentRanking ){
			
				bestPicks.clear();
				
				bestPicks.add( addr );
				
				currentRanking = ranking;
				
			}else if ( ranking == currentRanking ){
				
				bestPicks.add( addr );
			}
		}

		return bestPicks;
	}
	
	public static InetAddress
	getByName(
		String		host )

		throws UnknownHostException
	{
		if ( AENetworkClassifier.categoriseAddress( host ) == AENetworkClassifier.AT_PUBLIC ){

			return( InetAddress.getByName( host ));
		}

		throw( new UnknownHostException( host ));
	}

	public static InetAddress[]
	getAllByName(
		String		host )

		throws UnknownHostException
	{
		if ( AENetworkClassifier.categoriseAddress( host ) == AENetworkClassifier.AT_PUBLIC ){

			return( InetAddress.getAllByName( host ));
		}

		throw( new UnknownHostException( host ));
	}

	public static byte[]
	getAddressBytes(
		InetSocketAddress	address )
	{
		if ( address.isUnresolved()){

			try{
				return( address.getHostName().getBytes( "ISO8859-1" ));

			}catch( Throwable e ){

				Debug.out( e );

				return( null );
			}
		}else{

			return( address.getAddress().getAddress());
		}
	}

	public static String
	getHostAddress(
		InetSocketAddress	address )
	{
		if ( address.isUnresolved()){

			return( address.getHostName());

		}else{

			return( address.getAddress().getHostAddress());
		}
	}

	public static String
	getHostAddressForURL(
		InetSocketAddress	address )
	{
		if ( address.isUnresolved()){

			return( address.getHostName());

		}else{
			
			if ( address.getAddress() instanceof Inet6Address ){

				return( "[" + address.getAddress().getHostAddress() + "]" );
				
			}else{
				
				return( address.getAddress().getHostAddress());
			}
		}
	}
	
	public static String
	getHostNameNoResolve(
		InetSocketAddress	address )
	{
		InetAddress i_address = address.getAddress();

		if ( i_address == null ){

			return( address.getHostName());

		}else{

				// only way I can see (short of reflection) of getting access to unresolved host name
				// toString returns (hostname or "")/getHostAddress()

			String str = i_address.toString();

			int	pos = str.indexOf( '/' );

			if ( pos == -1 ){

				// darn it, borkage

				System.out.println( "InetAddress::toString not returning expected result: " + str );

				return( i_address.getHostAddress());
			}

			if ( pos > 0  ){

				return( str.substring( 0, pos ));

			}else{

				return( str.substring( pos+1 ));
			}
		}
	}

	public static String
	convertToShortForm(
		String		address )
	{
		int	address_length = address.length();

		if ( address_length > 256  ){

			String to_decode;

			if ( address.endsWith( ".i2p" )){

				to_decode = address.substring( 0, address.length() - 4 );

			}else if ( address.indexOf( '.' ) == -1 ){

				to_decode = address;

			}else{

				return( address );
			}

			try{
					// unfortunately we have an incompatible base64 standard in i2p, they replaced / with ~ and + with -

				char[]	encoded = to_decode.toCharArray();

				for ( int i=0;i<encoded.length;i++){

					char c = encoded[i];

					if ( c == '~' ){
						encoded[i] = '/';
					}else if( c == '-' ){
						encoded[i] = '+';
					}
				}

				byte[] decoded = Base64.decode( encoded );

				byte[] hash = MessageDigest.getInstance( "SHA-256" ).digest( decoded );

				return( Base32.encode( hash ).toLowerCase( Locale.US ) + ".b32.i2p" );

			}catch( Throwable e ){

				return( null );
			}
		}

		return( address );
	}
}
