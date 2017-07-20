/*
 * Created on 14-Jan-2005
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

import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentAnnounceURLSet;

/**
 * @author parg
 *
 */

public class
AENetworkClassifier
{
		// DON'T change these constants as they get serialised!!!!
		// (obviously you can add new networks to them).
		// If you add to them remember to update the configuration item default for
		// "Network Selection Default.<name>" and
		// "Tracker Network Selection Default.<name>

	public static final String	AT_PUBLIC		= "Public";
	public static final String	AT_I2P			= "I2P";
	public static final String	AT_TOR			= "Tor";

	public static final String[]	AT_NETWORKS =
		{ AT_PUBLIC, AT_I2P, AT_TOR };

	public static final String[] AT_NON_PUBLIC = { AT_I2P, AT_TOR };

	private static final List	listeners = new ArrayList();

	public static String
	categoriseAddress(
		String	str )
	{
		if ( str == null ){

			return( AT_PUBLIC );	// woreva
		}

		int len = str.length();

		if ( len < 7 ){

			return( AT_PUBLIC );
		}

		char[] chars = str.toCharArray();

		char last_char = chars[len-1];

		if ( last_char >= '0' && last_char <= '9' ){

			return( AT_PUBLIC );

		}else if ( last_char == 'p' || last_char == 'P' ){

			if ( 	chars[len-2] == '2' &&
					chars[len-4] == '.' ){

				char c = chars[len-3];

				if ( c == 'i' || c == 'I' ){

					return( AT_I2P );
				}
			}

			return( AT_PUBLIC );

		}else if ( last_char == 'n' || last_char == 'N') {

			if ( chars[len-6] == '.' ){

				String temp = new String( chars, len-5, 4 ).toLowerCase( Locale.US );

				if ( temp.equals( "onio" )){

					return( AT_TOR );
				}
			}
		}

		return( AT_PUBLIC );
	}

	public static String
	internalise(
		String	str )
	{
		if ( str == null ){

			return( null );

		}else{

			for ( String net: AT_NETWORKS ){

				if ( str.equalsIgnoreCase( net )){

					return( net );
				}
			}
		}

		return( null );
	}

	public static String
	categoriseAddress(
		InetSocketAddress		isa )
	{
		return( categoriseAddress( AddressUtils.getHostAddress( isa )));
	}

	public static String[]
	getNetworks(
		TOTorrent	torrent,
		String		display_name )
	{
			// go through all the announce URL and find all networks

		List<URL>	urls = new ArrayList();

		urls.add( torrent.getAnnounceURL());

		TOTorrentAnnounceURLSet[] sets = torrent.getAnnounceURLGroup().getAnnounceURLSets();

		for (int i=0;i<sets.length;i++){

			URL[]	u = sets[i].getAnnounceURLs();

			Collections.addAll(urls, u);
		}

		List<String>	available_networks = new ArrayList<>();

		for (int i=0;i<urls.size();i++){

			URL	u = (URL)urls.get(i);

			String	network = categoriseAddress( u.getHost());

			if ( !available_networks.contains( network )){

				available_networks.add( network );
			}
		}

		if ( available_networks.size() == 1 && available_networks.get(0) == AT_PUBLIC ){

			return( new String[]{ AT_PUBLIC });
		}


		boolean	prompt = COConfigurationManager.getBooleanParameter( "Network Selection Prompt" );

		List<String>	res = new ArrayList<>();

		if ( prompt && listeners.size() > 0 ){

			String[]	t_nets = new String[available_networks.size()];

			available_networks.toArray( t_nets );

			for (int i=0;i<listeners.size();i++){

				try{
					String[]	selected = ((AENetworkClassifierListener)listeners.get(i)).selectNetworks(
											display_name,
											t_nets );

					if ( selected != null ){

						Collections.addAll(res, selected);
					}
				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}
			}

		}else{
				// use enabled defaults to proceed


			for (int i=0;i<available_networks.size();i++){

				if ( COConfigurationManager.getBooleanParameter( "Network Selection Default." + available_networks.get(i))){

					res.add( available_networks.get(i));
				}
			}
		}

		String[]	x = new String[res.size()];

		res.toArray( x );

		return( x );
	}

	public static String[]
	getDefaultNetworks()
	{
		List<String>	res = new ArrayList<>();

		for ( String net: AT_NETWORKS ){

			if ( COConfigurationManager.getBooleanParameter( "Network Selection Default." + net )){

				res.add( net );
			}
		}

		String[]	x = new String[res.size()];

		res.toArray( x );

		return( x );
	}

	public static void
	addListener(
		AENetworkClassifierListener	l )
	{
		listeners.add(l);
	}

	public static void
	removeListener(
		AENetworkClassifierListener	l )
	{
		listeners.remove(l);
	}

	public static void
	main(
		String[]		args )
	{
		String[] tests = {
			null,
			"12345",
			"192.168.1.2",
			"fred.i2p",
			"fred.i2",
			"bill.onion",
			"bill.onio"
		};

		for ( String str: tests ){

			System.out.println( str + " -> " + categoriseAddress( str ));
		}
	}
}
