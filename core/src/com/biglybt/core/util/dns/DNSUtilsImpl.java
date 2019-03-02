/*
 * Created on Jan 15, 2013
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


package com.biglybt.core.util.dns;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import com.biglybt.core.util.DNSUtils;
import com.biglybt.core.util.DNSUtils.DNSUtilsIntf;
import com.biglybt.core.util.Debug;

public class
DNSUtilsImpl
	implements DNSUtilsIntf
{

	private static String
	getFactory()
	{
		return( System.getProperty("azureus.dns.context.factory", "com.sun.jndi.dns.DnsContextFactory" ));
	}

	@Override
	public DNSDirContextImpl
	getInitialDirContext()

		throws NamingException
	{
		Hashtable env = new Hashtable();

		env.put ( Context.INITIAL_CONTEXT_FACTORY, getFactory());

		return( new DNSDirContextImpl( new InitialDirContext( env )));

	}

	@Override
	public DNSDirContextImpl
	getDirContextForServer(
		String		dns_server_ip )

		throws NamingException
	{
		Hashtable env = new Hashtable();

		env.put( Context.INITIAL_CONTEXT_FACTORY, getFactory());

		env.put( Context.PROVIDER_URL, "dns://"+dns_server_ip+"/" );

		return( new DNSDirContextImpl( new InitialDirContext( env )));
	}

	@Override
	public Inet6Address
	getIPV6ByName(
		String		host )

		throws UnknownHostException
	{
		List<Inet6Address>	all = getAllIPV6ByName( host );

		return( all.get(0));
	}

	public List<Inet6Address>
	getAllIPV6ByName(
		String		host )

		throws UnknownHostException
	{
		List<Inet6Address>	result = new ArrayList<>();

		try{
			DirContext context = getInitialDirContext().ctx;

			Attributes attrs = context.getAttributes( host, new String[]{ "AAAA" });

			if ( attrs != null ){

				Attribute attr = attrs.get( "aaaa" );

				if ( attr != null ){

					NamingEnumeration values = attr.getAll();

					while( values.hasMore()){

						Object value = values.next();

						if ( value instanceof String ){

							try{
								result.add( (Inet6Address)InetAddress.getByName((String)value));

							}catch( Throwable e ){
							}
						}
					}
				}
			}
		}catch( Throwable e ){
		}

		if ( result.size() > 0 ){

			return( result );
		}

		throw( new UnknownHostException( host ));
	}

	@Override
	public List<InetAddress>
	getAllByName(
		String		host )

		throws UnknownHostException
	{
		try{
			return( getAllByName( getInitialDirContext(), host ));

		}catch( NamingException e ){

			throw( new UnknownHostException( host ));
		}
	}

	@Override
	public List<InetAddress>
	getAllByName(
		DNSUtils.DNSDirContext	context,
		String					host )

		throws UnknownHostException
	{
		// System.out.println( "Lookup for " + host );

		List<InetAddress>	result = new ArrayList<>();

		try{
			String[] attributes = new String[]{ "A", "AAAA" };

			Attributes attrs = ((DNSDirContextImpl)context).ctx.getAttributes( host, attributes );

			if ( attrs != null ){

				for( String a: attributes ){

					Attribute attr = attrs.get( a );

					if ( attr != null ){

						NamingEnumeration values = attr.getAll();

						while( values.hasMore()){

							Object value = values.next();

							if ( value instanceof String ){

								try{
									result.add( InetAddress.getByName((String)value));

								}catch( Throwable e ){
								}
							}
						}
					}
				}
			}
		}catch( Throwable e ){
		}

		if ( result.size() > 0 ){

			return( result );
		}

		throw( new UnknownHostException( host ));
	}

	private static final Map<String,String>	test_records = new HashMap<>();

	static{
		test_records.put( "test1.test.null", "BITTORRENT DENY ALL" );
		test_records.put( "test2.test.null", "BITTORRENT" );
		test_records.put( "test3.test.null", "BITTORRENT TCP:1 TCP:2 UDP:1 UDP:2" );
		test_records.put( "test4.test.null", "BITTORRENT TCP:3" );
		test_records.put( "test5.test.null", "BITTORRENT UDP:4" );
	}

	@Override
	public List<String>
	getTXTRecords(
		String		query )
	{
		// System.out.println( "DNSTXTQuery: " + query );

		List<String>	result = new ArrayList<>();

		String test_reply = test_records.get( query );

		if ( test_reply != null ){

			result.add( test_reply );

			return( result );
		}

		DirContext context = null;

		try{
			context = getInitialDirContext().ctx;

			Attributes attrs = context.getAttributes( query, new String[]{ "TXT" });

			NamingEnumeration n_enum = attrs.getAll();

			while( n_enum.hasMoreElements()){

				Attribute	attr =  (Attribute)n_enum.next();

				NamingEnumeration n_enum2 = attr.getAll();

				while( n_enum2.hasMoreElements()){

					String attribute = (String)n_enum2.nextElement();

					if ( attribute != null ){

						attribute = attribute.trim();

						if ( attribute.startsWith( "\"" )){

							attribute = attribute.substring(1);
						}

						if ( attribute.endsWith( "\"" )){

							attribute = attribute.substring(0,attribute.length()-1);
						}

						if ( attribute.length() > 0 ){

							result.add( attribute );
						}
					}
				}
			}

		}catch( Throwable e ){

			//e.printStackTrace();

		}finally{

			if ( context != null ){

				try{
					context.close();

				}catch( Throwable e ){
				}
			}
		}

		return( result );
	}

	@Override
	public String
	getTXTRecord(
		String		query )

		throws UnknownHostException
	{
		DirContext context = null;

		try{
			context = getInitialDirContext().ctx;

			Attributes attrs = context.getAttributes( query, new String[]{ "TXT" });

			NamingEnumeration n_enum = attrs.getAll();

			while( n_enum.hasMoreElements()){

				Attribute	attr =  (Attribute)n_enum.next();

				NamingEnumeration n_enum2 = attr.getAll();

				while( n_enum2.hasMoreElements()){

					String attribute = (String)n_enum2.nextElement();

					if ( attribute != null ){

						attribute = attribute.trim();

						if ( attribute.startsWith( "\"" )){

							attribute = attribute.substring(1);
						}

						if ( attribute.endsWith( "\"" )){

							attribute = attribute.substring(0,attribute.length()-1);
						}

						if ( attribute.length() > 0 ){

							return( attribute );
						}
					}
				}
			}

			throw( new UnknownHostException( "DNS query returned no results" ));

		}catch( Throwable e ){

			throw( new UnknownHostException( "DNS query failed:" + Debug.getNestedExceptionMessage(e)));

		}finally{

			if ( context != null ){

				try{
					context.close();

				}catch( Throwable e ){
				}
			}
		}
	}

	public static class
	DNSDirContextImpl
		implements DNSUtils.DNSDirContext
	{
		final DirContext ctx;

		private
		DNSDirContextImpl(
			DirContext	_ctx )
		{
			ctx = _ctx;
		}

		@Override
		public String
		getString()
		{
			try{
				return( String.valueOf( ctx.getEnvironment()));

			}catch( Throwable e ){

				return( Debug.getNestedExceptionMessage(e));
			}
		}
	}

	public static void
	main(
		String[]	args )
	{
		try{
			//List<String> records = getTXTRecords( "tracker.openbittorrent.com" );
			/*
			List<String> records = getTXTRecords( "www.ibm.com" );

			for ( String record: records ){

				System.out.println( record );
			}
			*/

			DNSUtilsImpl impl = new DNSUtilsImpl();

			DNSUtils.DNSDirContext ctx =impl.getDirContextForServer( "8.8.4.4" );

			System.out.println( impl.getAllByName( ctx, "www.google.com" ));

		}catch( Throwable e ){

			e.printStackTrace();
		}
	}
}
