/*
 * Created on 1 Nov 2006
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


package com.biglybt.core.networkmanager.admin.impl;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.networkmanager.admin.NetworkAdminException;
import com.biglybt.core.util.DNSUtils;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemTime;

public class
NetworkAdminASNLookupImpl
{
	private static final String	WHOIS_ADDRESS 	= "whois.cymru.com";
	private static final int	WHOIS_PORT		= 43;

	private static final int	TIMEOUT			= 30000;

	private final InetAddress		address;

	protected
	NetworkAdminASNLookupImpl(
		InetAddress		_address )
	{
		address= _address;
	}

	protected NetworkAdminASNImpl
	lookup()

		throws NetworkAdminException
	{
		// Debug.outDiagLoggerOnly( "ASN lookup for '" + address + "'" );

		return( lookupDNS( address ));
	}

	protected NetworkAdminASNImpl
	lookupTCP(
		InetAddress		address )

		throws NetworkAdminException
	{
		try{
			Socket	socket = new Socket();

			int	timeout = TIMEOUT;

			long	start = SystemTime.getCurrentTime();

			socket.connect( new InetSocketAddress( WHOIS_ADDRESS, WHOIS_PORT ), timeout );

			long	end = SystemTime.getCurrentTime();

			timeout -= (end - start );

			if ( timeout <= 0 ){

				throw( new NetworkAdminException( "Timeout on connect" ));

			}else if ( timeout > TIMEOUT ){

				timeout = TIMEOUT;
			}

			socket.setSoTimeout( timeout );

			try{
				OutputStream	os = socket.getOutputStream();

				String	command = "-u -p " + address.getHostAddress() + "\r\n";

				os.write( command.getBytes());

				os.flush();

				InputStream	is = socket.getInputStream();

				byte[]	buffer = new byte[1024];

				String	result = "";

				while( true ){

					int	len = is.read( buffer );

					if ( len <= 0 ){

						break;
					}

					result += new String( buffer, 0, len );
				}

				return( processResult( result ));

			}finally{

				socket.close();
			}
		}catch( Throwable e ){

			throw( new NetworkAdminException( "whois connection failed", e ));
		}
	}

	protected NetworkAdminASNImpl
	lookupDNS(
		InetAddress		address )

		throws NetworkAdminException
	{
			// first query for the as

		byte[]	bytes = address.getAddress();

		String	ip_query	= "origin.asn.cymru.com";

		for (int i=0;i<4;i++){

			ip_query =  ( bytes[i] & 0xff ) + "." + ip_query;
		}

			// "33544 | 64.71.0.0/20 | US | arin | 2006-05-04"

		String	ip_result = lookupDNS( ip_query );

		NetworkAdminASNImpl result =
			processResult(
				"AS | BGP Prefix | CC | Reg | Date | AS Name" + "\n" +
				ip_result + " | n/a" );

		String	as = result.getAS();

		if ( as.length() > 0 ){

				// now query for ASN

				// 33544 | US | arin | 2005-01-19 | WILINE - WiLine Networks Inc.

			String	asn_query = "AS" + as + ".asn.cymru.com";

			try{

				String	asn_result = lookupDNS( asn_query );

				if ( asn_result != null ){

					int	pos = asn_result.lastIndexOf( '|' );

					if ( pos != -1 ){

						String asn = asn_result.substring( pos+1 ).trim();

						result.setASName( asn );
					}
				}
			}catch( Throwable e ){

				Debug.outNoStack( "ASN lookup for '" + asn_query+ "' failed: " + e.getMessage());
			}
		}

		return( result );
	}

	protected String
	lookupDNS(
		String		query )

		throws NetworkAdminException
	{
		DNSUtils.DNSUtilsIntf dns_utils = DNSUtils.getSingleton();

		if ( dns_utils == null ){

			throw( new NetworkAdminException( "DNS lookup unavailable" ));
		}

		try{
			return( dns_utils.getTXTRecord(query));

		}catch( UnknownHostException e ){

			throw( new NetworkAdminException( "Query failed for '" + query + "'", e ));
		}
	}


	protected NetworkAdminASNImpl
	processResult(
		String		result )
	{
		StringTokenizer	lines = new StringTokenizer( result, "\n" );

		int	line_number = 0;

		List	keywords = new ArrayList();

		Map	map = new HashMap();

		while( lines.hasMoreTokens()){

			String	line = lines.nextToken().trim();

			line_number++;

			if ( line_number > 2 ){

				break;
			}

			StringTokenizer	tok = new StringTokenizer( line, "|" );

			int	token_number = 0;

			while( tok.hasMoreTokens()){

				String	token = tok.nextToken().trim();

				if ( line_number == 1 ){

					keywords.add( token.toLowerCase( MessageText.LOCALE_ENGLISH ));

				}else{

					if ( token_number >= keywords.size()){

						break;

					}else{

						String	kw = (String)keywords.get( token_number );

						map.put( kw, token );
					}
				}

				token_number++;
			}
		}

		String as 			= (String)map.get( "as" );
		String asn 		= (String)map.get( "as name" );
		String bgp_prefix	= (String)map.get( "bgp prefix" );

		if ( bgp_prefix != null ){

			int	pos = bgp_prefix.indexOf(' ');

			if ( pos != -1 ){

				bgp_prefix = bgp_prefix.substring(pos+1).trim();
			}

			if ( bgp_prefix.indexOf('/') == -1 ){

				bgp_prefix = null;
			}
		}

		return( new NetworkAdminASNImpl( as, asn, bgp_prefix ));
	}



	public static void
	main(
		String[]	args )
	{
		try{

			NetworkAdminASNLookupImpl lookup = new NetworkAdminASNLookupImpl( InetAddress.getByName( "64.71.8.82" ));

			System.out.println( lookup.lookup().getString());


			/*
			InetAddress	test = InetAddress.getByName( "255.71.15.1" );

			System.out.println( test + " -> " + matchesCIDR( "255.71.0.0/20", test ));
			*/
		}catch( Throwable e ){

			e.printStackTrace();
		}
	}
}
