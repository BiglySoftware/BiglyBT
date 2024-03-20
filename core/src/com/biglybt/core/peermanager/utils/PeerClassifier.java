/*
 * Created on Sep 9, 2004
 * Created by Alon Rohter
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

package com.biglybt.core.peermanager.utils;

import java.net.InetAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.biglybt.core.util.Constants;
import com.biglybt.core.util.FeatureAvailability;
import com.biglybt.core.util.HostNameToIPResolver;
import com.biglybt.core.util.IPToHostNameResolver;

/**
 * Handles peer client identification and banning.
 */
public class PeerClassifier {

	public static final String CACHE_LOGIC 		= "CacheLogic";
	public static final String HTTP_SEED_PREFIX = "HTTP Seed: ";
	public static final String WEB_SEED_PREFIX	= "WebSeed: ";

  /**
   * Get a client description (name and version) from the given peerID byte array.
   * @param peer_id peerID sent in handshake
   * @return description
   */
  public static String getClientDescription( byte[] peer_id, String net ) {
    return BTPeerIDByteDecoder.decode( peer_id, net );
  }


  /**
   * Get a printable representation of the given raw peerID byte array,
   * i.e. filter out the first 32 non-printing ascii chars.
   * @param peer_id peerID sent in handshake
   * @return printable peerID
   */
  public static String getPrintablePeerID( byte[] peer_id ) {
    return BTPeerIDByteDecoder.getPrintablePeerID(peer_id);
  }


  /**
   * Check if the client type is allowed to connect.
   * @param client_description given by getClientDescription
   * @return true if allowed, false if banned
   */
  public static boolean isClientTypeAllowed( String client_description ) {
    //if( client_description.startsWith( "BitComet" ) ) return false;
    return true;
  }

  public static boolean fullySupportsFE( String client_description ){

	  if ( FeatureAvailability.allowAllFEClients()){

		  return( true );
	  }

	  	// some clients don't ever offer any fast-allow pieces so we reciprocate

	  boolean res = !(client_description.startsWith( "\u00B5" ) || client_description.startsWith( "Trans" ));

	  return( res );
  }

	private static final Set	platform_ips = Collections.synchronizedSet(new HashSet());


		/**
		 * This only works for ones that have been explicitly set as AZ ips
		 * @param ip
		 * @return
		 */

	public static boolean
	isAzureusIP(
		final String	ip )
	{
		return( platform_ips.contains( ip ));
	}

	public static void
	setAzureusIP(
		final String	ip )
	{
		platform_ips.add( ip );
	}

		/**
		 * SYNC call!
		 * @param ip
		 * @return
		 */

	public static boolean
	testIfAzureusIP(
		final String	ip )
	{
		try{
			InetAddress address = HostNameToIPResolver.syncResolve( ip );

			final String host_address = address.getHostAddress();

			if ( platform_ips.contains( host_address )){

				return( true );
			}

			String name = IPToHostNameResolver.syncResolve( ip, 10000 );

			if ( Constants.isAppDomain( name )){

				platform_ips.add( host_address );

				return( true );
			}
		}catch( Throwable e ){
		}

		return( false );
	}
	
	public static boolean
	isHTTPSeed(
		String		client )
	{
		if ( client == null || client.isEmpty()){
			
			return( false );
		}
		
		return( client.startsWith(HTTP_SEED_PREFIX) || client.startsWith( WEB_SEED_PREFIX ));
	}
}
