/*
 * Created on 29-Jun-2004
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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author parg
 *
 */

public class
HostNameToIPResolver
{
	static protected AEThread2		resolver_thread;

	static protected final List			request_queue		= new ArrayList();

	static protected final AEMonitor		request_queue_mon	= new AEMonitor( "HostNameToIPResolver" );

	static protected final AESemaphore	request_semaphore	= new AESemaphore("HostNameToIPResolver");

	public static boolean
	isDNSName(
		String	host )
	{
		return( AENetworkClassifier.categoriseAddress( host ) == AENetworkClassifier.AT_PUBLIC );
	}

	public static boolean
	isNonDNSName(
		String	host )
	{
		return( AENetworkClassifier.categoriseAddress( host ) != AENetworkClassifier.AT_PUBLIC );
	}

	public static InetAddress
	syncResolve(
		String	host )

		throws UnknownHostException
	{
		if ( isNonDNSName( host ) || host.equalsIgnoreCase( "tor" ) || host.equalsIgnoreCase( "i2p" )){

			throw( new HostNameToIPResolverException( "non-DNS name '" + host + "'", true ));
		}

			// handle any raw addresses up front

		byte[]	bytes = textToNumericFormat( host );

		if ( bytes != null ){

			return( InetAddress.getByAddress( bytes ));
		}

			// filter out partially complete raw addresses by applying the basic rule in ftp://ftp.is.co.za/rfc/rfc3696.txt
			// at least one dot + not all numeric

		char[]	chars = host.toCharArray();

		boolean	resolve = false;

		for (int i=0;i<chars.length;i++){

			if ( !( chars[i] == '.' || Character.isDigit(chars[i]))){

				resolve	= true;

				break;
			}
		}

		if ( resolve && host.startsWith( "websocket." )){

			resolve = false;

			for (int i=10;i<chars.length;i++){

				if ( !Character.isDigit(chars[i])){

					resolve = true;

					break;
				}
			}
		}

		if ( resolve ){

			return( InetAddress.getByName( host));

		}else{

			throw( new UnknownHostException( "Host '" + host + "' doesn't obey minimal validation rules"));
		}
	}

	public static void
	addResolverRequest(
		String							host,
		HostNameToIPResolverListener	l )
	{
		byte[]	bytes = textToNumericFormat( host );

		if ( bytes != null ){

			try{
				l.hostNameResolutionComplete( InetAddress.getByAddress( host, bytes ));

				return;

			}catch( UnknownHostException e ){
			}
		}

		try{
			request_queue_mon.enter();

			request_queue.add( new request( host, l ));

			request_semaphore.release();

			if ( resolver_thread == null ){

				resolver_thread =
					new AEThread2("HostNameToIPResolver",true)
					{
						@Override
						public void
						run()
						{
							while(true){

								try{
									request_semaphore.reserve(30000);

									request	req;

									try{
										request_queue_mon.enter();

										if ( request_queue.isEmpty()){

											resolver_thread = null;

											break;
										}

										req	= (request)request_queue.remove(0);

									}finally{

										request_queue_mon.exit();
									}

									try{
										InetAddress addr = syncResolve( req.getHost());

										req.getListener().hostNameResolutionComplete( addr );

									}catch( Throwable e ){

										req.getListener().hostNameResolutionComplete( null );

									}
								}catch( Throwable e ){

									Debug.printStackTrace( e );
								}
							}
						}
					};

				resolver_thread.start();
			}
		}finally{

			request_queue_mon.exit();
		}
	}

	public static InetAddress
	hostAddressToInetAddress(
		String		host )
	{
		byte[]	bytes = hostAddressToBytes( host );

		if ( bytes != null ){

			try{
				return( InetAddress.getByAddress( bytes ));

			}catch( Throwable e ){

				return( null );
			}
		}

		return( null );
	}

	public static byte[]
	hostAddressToBytes(
		String		host )
	{
		byte[] res = textToNumericFormat( host );

		return( res );
	}

	final static int INADDRSZ	= 4;

	static byte[]
	textToNumericFormat(
		String src )
	{
		if (src.length() == 0) {
		    return null;
		}

		if ( src.indexOf(':') != -1 ){

				// v6
			try{
				return( InetAddress.getByName(src).getAddress());

			}catch( Throwable e ){

				return( null );
			}
		}

		int octets;
		char ch;
		byte[] dst = new byte[INADDRSZ];

		char[] srcb = src.toCharArray();
		boolean saw_digit = false;

		octets = 0;
		int i = 0;
		int cur = 0;
		while (i < srcb.length) {
		    ch = srcb[i++];
		    if (Character.isDigit(ch)) {
			// note that Java byte is signed, so need to convert to int
			int sum = (dst[cur] & 0xff)*10
			    + (Character.digit(ch, 10) & 0xff);

			if (sum > 255)
			    return null;

			dst[cur] = (byte)(sum & 0xff);
			if (! saw_digit) {
			    if (++octets > INADDRSZ)
				return null;
			    saw_digit = true;
			}
		    } else if (ch == '.' && saw_digit) {
			if (octets == INADDRSZ)
			    return null;
			cur++;
			dst[cur] = 0;
			saw_digit = false;
		    } else
			return null;
		}
		if (octets < INADDRSZ)
		    return null;
		return dst;
	}



	protected static class
	request
	{
		protected final String						host;
		protected final HostNameToIPResolverListener	listener;

		protected
		request(
			String							_host,
			HostNameToIPResolverListener	_listener )
		{
			host			= _host;
			listener		= _listener;
		}

		protected String
		getHost()
		{
			return( host );
		}

		protected HostNameToIPResolverListener
		getListener()
		{
			return( listener );
		}
	}
}
