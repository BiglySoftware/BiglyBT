/*
 * Created on Jan 31, 2008
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


package com.biglybt.plugin.net.netstatus.swt;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.*;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

import java.security.cert.Certificate;

import com.biglybt.core.Core;
import com.biglybt.core.util.BDecoder;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;

import com.biglybt.core.networkmanager.admin.*;
import com.biglybt.core.proxy.AEProxyFactory;
import com.biglybt.core.proxy.AEProxyFactory.PluginProxy;
import com.biglybt.core.versioncheck.VersionCheckClient;
import com.biglybt.plugin.net.netstatus.NetStatusPlugin;
import com.biglybt.plugin.net.netstatus.NetStatusProtocolTesterBT;
import com.biglybt.plugin.net.netstatus.NetStatusProtocolTesterListener;

public class
NetStatusPluginTester
{
	//public static final int		TEST_PING_ROUTE		= 0x00000001;
	public static final int		TEST_NAT_PROXIES		= 0x00000002;
	public static final int		TEST_OUTBOUND			= 0x00000004;
	public static final int		TEST_INBOUND			= 0x00000008;
	public static final int		TEST_BT_CONNECT			= 0x00000010;
	public static final int		TEST_IPV6				= 0x00000020;
	public static final int		TEST_BIGLYBT_SERVICES	= 0x00000040;
	public static final int		TEST_PROXY_CONNECT		= 0x00000080;


	private static final int	ROUTE_TIMEOUT	= 120*1000;

	private NetStatusPlugin		plugin;
	private int					test_types;
	private loggerProvider		logger;

	private volatile boolean	test_cancelled;

	public
	NetStatusPluginTester(
		NetStatusPlugin		_plugin,
		int					_test_types,
		loggerProvider		_logger )
	{
		plugin		= _plugin;
		test_types	= _test_types;
		logger		= _logger;
	}

	protected boolean
	doTest(
		int		type )
	{
		if ( test_cancelled ){

			return( false );
		}

		return((test_types & type ) != 0 );
	}

	public void
	run(Core core)
	{
		final NetworkAdmin	admin = NetworkAdmin.getSingleton();

		boolean	checked_public	= false;

		Set<InetAddress>	public_addresses = new HashSet<>();

		InetAddress def_pa = admin.getDefaultPublicAddress();

		if ( def_pa != null ){

			log( "Default public address is " + def_pa.getHostAddress());

			addPublicAddress( public_addresses, def_pa );

			checked_public = true;
		}

		InetAddress[] bindable = admin.getBindableAddresses();

		String bindable_str = "";

		for ( InetAddress b: bindable ){

			bindable_str += ( bindable_str.length()==0?"":", " ) + b.getHostAddress();
		}

		log( "Bindable addresses: " + bindable_str );


		/* this ain't working well and some users reporting crashes so boo
		 *
		if ( doTest( TEST_PING_ROUTE )){

			log( "Testing routing for the following interfaces:" );

			NetworkAdminNetworkInterface[] interfaces = admin.getInterfaces();

			for (int i=0;i<interfaces.length;i++){

				NetworkAdminNetworkInterface	intf = interfaces[i];

				NetworkAdminNetworkInterfaceAddress[] addresses = intf.getAddresses();

				String	a_str = "";

				for (int j=0;j<addresses.length;j++){

					NetworkAdminNetworkInterfaceAddress address = addresses[j];

					InetAddress ia = address.getAddress();

					if ( ia.isLoopbackAddress() || ia instanceof Inet6Address ){

					}else{

						a_str += (a_str.length()==0?"":",") + ia.getHostAddress();
					}
				}

				if ( a_str.length() > 0 ){

					log( "    " + intf.getName() + "/" + intf.getDisplayName() + ": " + a_str );
				}
			}

			if ( admin.canPing()){

				log( "Running ping tests" );

				try{
					InetAddress	target_address = InetAddress.getByName( plugin.getPingTarget());

					final Map	active_pings = new HashMap();

					admin.pingTargets(
						target_address,
						ROUTE_TIMEOUT,
						new NetworkAdminRoutesListener()
						{
							private int	timeouts;

							public boolean
							foundNode(
								NetworkAdminNetworkInterfaceAddress		intf,
								NetworkAdminNode[]						route,
								int										distance,
								int										rtt )
							{
								if ( test_cancelled ){

									return( false );
								}

								synchronized( active_pings ){

									active_pings.put( intf, route );
								}

								log( "  " + intf.getAddress().getHostAddress() + " -> " + route[route.length-1].getAddress().getHostAddress());

								return( false );
							}

							public boolean
							timeout(
								NetworkAdminNetworkInterfaceAddress		intf,
								NetworkAdminNode[]						route,
								int										distance )
							{
								if ( test_cancelled ){

									return( false );
								}

								log( "  " + intf.getAddress().getHostAddress() + " - timeout" );

								timeouts++;

								if ( timeouts >= 3 ){

									return( false );
								}

								return( true );
							}
						});

					if ( test_cancelled ){

						return;
					}

					int	num_routes = active_pings.size();

					if ( num_routes == 0 ){

						logError( "No active pings found!" );

					}else{

						log( "Found " + num_routes + " pings(s)" );

						Iterator it = active_pings.entrySet().iterator();

						while( it.hasNext()){

							Map.Entry entry = (Map.Entry)it.next();

							NetworkAdminNetworkInterfaceAddress address = (NetworkAdminNetworkInterfaceAddress)entry.getKey();

							NetworkAdminNode[]	route = (NetworkAdminNode[])entry.getValue();

							String	node_str = "";

							for (int i=0;i<route.length;i++){

								node_str += (i==0?"":",") + route[i].getAddress().getHostAddress();
							}

							log( "    " + address.getInterface().getName() + "/" + address.getAddress().getHostAddress() + " - " + node_str );
						}
					}
				}catch( Throwable e ){

					logError( "Pinging failed: " + Debug.getNestedExceptionMessage(e));
				}
			}else{

				logError( "Can't run ping test as not supported" );
			}

			if ( test_cancelled ){

				return;
			}

			if ( admin.canTraceRoute()){

				log( "Running trace route tests" );

				try{
					InetAddress	target_address = InetAddress.getByName( plugin.getPingTarget());

					final Map	active_routes = new HashMap();

					admin.getRoutes(
						target_address,
						ROUTE_TIMEOUT,
						new NetworkAdminRoutesListener()
						{
							private String	last_as = "";

							public boolean
							foundNode(
								NetworkAdminNetworkInterfaceAddress		intf,
								NetworkAdminNode[]						route,
								int										distance,
								int										rtt )
							{
								if ( test_cancelled ){

									return( false );
								}

								synchronized( active_routes ){

									active_routes.put( intf, route );
								}

								InetAddress ia = route[route.length-1].getAddress();

								String	as = "";

								if ( !ia.isLinkLocalAddress() && !ia.isSiteLocalAddress()){

									try{
										NetworkAdminASN asn = admin.lookupASN( ia );

										as = asn.getString();

										if ( as.equals( last_as )){

											as = "";

										}else{

											last_as = as;
										}
									}catch( Throwable e ){

									}
								}

								log( "  " + intf.getAddress().getHostAddress() + " -> " + ia.getHostAddress() + " (hop=" + distance + ")" + (as.length()==0?"":( " - " + as )));

								return( true );
							}

							public boolean
							timeout(
								NetworkAdminNetworkInterfaceAddress		intf,
								NetworkAdminNode[]						route,
								int										distance )
							{
								if ( test_cancelled ){

									return( false );
								}

								log( "  " + intf.getAddress().getHostAddress() + " - timeout (hop=" + distance + ")" );

									// see if we're getting nowhere

								if ( route.length == 0 && distance >= 5 ){

									logError( "    giving up, no responses" );

									return( false );
								}

									// see if we've got far enough

								if ( route.length >= 5 && distance > 6 ){

									log( "    truncating, sufficient responses" );

									return( false );
								}

								return( true );
							}
						});

					if ( test_cancelled ){

						return;
					}

					int	num_routes = active_routes.size();

					if ( num_routes == 0 ){

						logError( "No active routes found!" );

					}else{

						log( "Found " + num_routes + " route(s)" );

						Iterator it = active_routes.entrySet().iterator();

						while( it.hasNext()){

							Map.Entry entry = (Map.Entry)it.next();

							NetworkAdminNetworkInterfaceAddress address = (NetworkAdminNetworkInterfaceAddress)entry.getKey();

							NetworkAdminNode[]	route = (NetworkAdminNode[])entry.getValue();

							String	node_str = "";

							for (int i=0;i<route.length;i++){

								node_str += (i==0?"":",") + route[i].getAddress().getHostAddress();
							}

							log( "    " + address.getInterface().getName() + "/" + address.getAddress().getHostAddress() + " - " + node_str );
						}
					}
				}catch( Throwable e ){

					logError( "Route tracing failed: " + Debug.getNestedExceptionMessage(e));
				}
			}else{

				logError( "Can't run trace route test as not supported" );
			}

			if ( test_cancelled ){

				return;
			}
		}
		*/

		if ( doTest( TEST_NAT_PROXIES )){

			checked_public = true;

			NetworkAdminNATDevice[] nat_devices = admin.getNATDevices(core);

			log( nat_devices.length + " NAT device" + (nat_devices.length==1?"":"s") + " found" );

			for (int i=0;i<nat_devices.length;i++){

				NetworkAdminNATDevice device = nat_devices[i];

				InetAddress ext_address = device.getExternalAddress();

				addPublicAddress( public_addresses, ext_address );

				log( "    " + device.getString());
			}

			NetworkAdminSocksProxy[] socks_proxies = admin.getSocksProxies();

			if ( socks_proxies.length == 0 ){

				log( "No SOCKS proxy found" );

			}else if (  socks_proxies.length == 1 ){

				log( "One SOCKS proxy found" );

			}else{

				log( socks_proxies.length + " SOCKS proxies found" );
			}

			for (int i=0;i<socks_proxies.length;i++){

				NetworkAdminSocksProxy proxy = socks_proxies[i];

				log( "    " + proxy.getString());
			}

			NetworkAdminHTTPProxy http_proxy = admin.getHTTPProxy();

			if ( http_proxy == null ){

				log( "No HTTP proxy found" );

			}else{

				log( "HTTP proxy found" );

				log( "    " + http_proxy.getString());
			}
		}

		InetAddress[] bind_addresses = admin.getAllBindAddresses( false );

		int	num_binds = 0;

		for ( int i=0;i<bind_addresses.length;i++ ){

			if ( bind_addresses[i] != null ){

				num_binds++;
			}
		}

		if ( num_binds == 0 ){

			log( "No explicit bind address set" );

		}else{

			log( num_binds + " bind addresses" );

			for ( int i=0;i<bind_addresses.length;i++ ){

				if ( bind_addresses[i] != null ){

					log( "    " + bind_addresses[i].getHostAddress());
				}
			}
		}

		if ( doTest( TEST_OUTBOUND )){

			checked_public = true;

			NetworkAdminProtocol[] outbound_protocols = admin.getOutboundProtocols(core);

			if ( outbound_protocols.length == 0 ){

				log( "No outbound protocols" );

			}else{

				for (int i=0;i<outbound_protocols.length;i++){

					if ( test_cancelled ){

						return;
					}

					NetworkAdminProtocol protocol = outbound_protocols[i];

					log( "Testing " + protocol.getName());

					try{
						InetAddress public_address =
							protocol.test(
								null,
								new NetworkAdminProgressListener()
								{
									@Override
									public void
									reportProgress(
										String task )
									{
										log( "    " + task );
									}
								});

						logSuccess( "    Test successful" );

						addPublicAddress( public_addresses, public_address );

					}catch( Throwable e ){

						logError( "    Test failed", e );
					}
				}
			}
		}

		if ( doTest( TEST_INBOUND )){

			checked_public = true;

			NetworkAdminProtocol[] inbound_protocols = admin.getInboundProtocols(core);

			if ( inbound_protocols.length == 0 ){

				log( "No inbound protocols" );

			}else{

				for (int i=0;i<inbound_protocols.length;i++){

					if ( test_cancelled ){

						return;
					}

					NetworkAdminProtocol protocol = inbound_protocols[i];

					log( "Testing " + protocol.getName());

					try{
						InetAddress public_address =
							protocol.test(
								null,
								new NetworkAdminProgressListener()
								{
									@Override
									public void
									reportProgress(
										String task )
									{
										log( "    " + task );
									}
								});

						logSuccess( "    Test successful" );

						addPublicAddress( public_addresses, public_address );

					}catch( Throwable e ){

						logError( "    Test failed", e );
						logInfo(  "    Check your port forwarding for " + protocol.getTypeString() + " " + protocol.getPort());
					}
				}
			}
		}

		if ( checked_public ){

			if ( public_addresses.size() == 0 ){

				log( "No public addresses found" );

			}else{

				Iterator<InetAddress>	it = public_addresses.iterator();

				log( public_addresses.size() + " public/external addresses found" );

				while( it.hasNext()){

					InetAddress	pub_address = it.next();

					log( "    " + pub_address.getHostAddress());

					try{
						NetworkAdminASN asn = admin.lookupASN(pub_address);

						log( "    AS details: " + asn.getString());

					}catch( Throwable e ){

						logError( "    failed to lookup AS", e );
					}
				}
			}
		}

		String[][] services = {
				{ "BiglyBT Website", 	Constants.URL_CLIENT_HOME },		// directly referenced at index 0 below
				{ "Version Server", 	"http://" + Constants.VERSION_SERVER_V4 + "/?dee" },
				{ "Plugins Website", 	Constants.PLUGINS_WEB_SITE },
		};

		if ( doTest( TEST_BIGLYBT_SERVICES )){

			log( "BiglyBT Services test" );

			for ( String[] service: services ){

				if ( test_cancelled ){

					return;
				}

				try{
					URL	url = new URL( service[1] );

					log( "    " + service[0] + " - " + url.getHost());

					boolean	is_https = url.getProtocol().equals( "https" );

					if ( is_https ){

						String[]	host_bits = url.getHost().split( "\\." );

						String host_match = "." + host_bits[host_bits.length-2] + "." + host_bits[host_bits.length-1];

						HttpsURLConnection con = (HttpsURLConnection)url.openConnection();

						con.setHostnameVerifier(
							new HostnameVerifier()
							{
								@Override
								public boolean
								verify(
									String		host,
									SSLSession	session )
								{
									return( true );
								}
							});


						con.setInstanceFollowRedirects( false );

						con.setConnectTimeout( 30*1000 );
						con.setReadTimeout( 30*1000 );

						con.getResponseCode();

						con.getInputStream();

						Certificate[] certs = con.getServerCertificates();

						if ( certs == null || certs.length == 0 ){

							logError( "        No certificates returned" );

						}else{

							Certificate cert = certs[0];

							java.security.cert.X509Certificate x509_cert;

							if ( cert instanceof java.security.cert.X509Certificate ){

								x509_cert = (java.security.cert.X509Certificate)cert;

							}else{

								java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");

								x509_cert = (java.security.cert.X509Certificate)cf.generateCertificate(new ByteArrayInputStream(cert.getEncoded()));
							}

							log( "        Certificate: " + x509_cert.getSubjectX500Principal().getName());

							Collection<List<?>> alt_names = x509_cert.getSubjectAlternativeNames();

							boolean match = false;

							for ( List<?> alt_name: alt_names ){

								int	type = ((Number)alt_name.get(0)).intValue();

								if ( type == 2 ){		// DNS name

									String	dns_name = (String)alt_name.get(1);

									if ( dns_name.endsWith( host_match )){

										match = true;

										break;
									}
								}
							}

							if ( !match ){

								logError( "        Failed: Host '" + host_match + "' not found in certificate" );

							}else{

								logSuccess( "        Connection result: " + con.getResponseCode() + "/" + con.getResponseMessage());
							}
						}
					}else{

						HttpURLConnection con = (HttpURLConnection)url.openConnection();

						con.setInstanceFollowRedirects( false );

						con.setConnectTimeout( 30*1000 );
						con.setReadTimeout( 30*1000 );

						if ( con.getResponseCode() != 200 ){

							throw( new Exception( "Connection failed: " + con.getResponseCode() + "/" + con.getResponseMessage()));
						}

						Map resp = BDecoder.decode( new BufferedInputStream( con.getInputStream(), 16*1024 ));

						if ( resp != null && resp.containsKey( "version" )){

							logSuccess( "        Connection result: " + con.getResponseCode() + "/" + con.getResponseMessage());

						}else{

							logError( "        Unexpected reply from server: " + resp );
						}
					}
				}catch( Throwable e ){

					logError( "        Failed: " + Debug.getNestedExceptionMessage( e ));
				}
			}
		}

		if ( doTest( TEST_PROXY_CONNECT )){

			log( "Indirect Connect test" );

			try{
				URL target = new URL( services[0][1] );

				PluginProxy proxy = AEProxyFactory.getPluginProxy( "Network Status test", target );

				if ( proxy == null ){

					logError( "    No plugin proxy available" );
					logInfo( "    For the plugin installer refer to the 'Tor Helper' plugin" );

				}else{

					log( "    Connecting to " + target.toExternalForm());

					HttpURLConnection con = (HttpURLConnection)proxy.getURL().openConnection( proxy.getProxy());

					if ( con instanceof HttpsURLConnection ){

						((HttpsURLConnection)con).setHostnameVerifier(
								new HostnameVerifier()
								{
									@Override
									public boolean
									verify(
										String		host,
										SSLSession	session )
									{
										return( true );
									}
								});
					}

					con.setRequestProperty( "HOST", proxy.getURLHostRewrite());

					con.setInstanceFollowRedirects( false );

					con.setConnectTimeout( 60*1000 );
					con.setReadTimeout( 30*1000 );

					try{
						int resp = con.getResponseCode();

						if ( con instanceof HttpsURLConnection ){

							Certificate[] certs = ((HttpsURLConnection)con).getServerCertificates();

							if ( certs == null || certs.length == 0 ){

								logError( "    No certificates returned" );

							}else{
								Certificate cert = certs[0];

								java.security.cert.X509Certificate x509_cert;

								if ( cert instanceof java.security.cert.X509Certificate ){

									x509_cert = (java.security.cert.X509Certificate)cert;

								}else{

									java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");

									x509_cert = (java.security.cert.X509Certificate)cf.generateCertificate(new ByteArrayInputStream(cert.getEncoded()));
								}

								log( "    Certificate: " + x509_cert.getSubjectDN());
							}
						}

						if ( resp == 200 ){

							logSuccess( "    Connection result: " + con.getResponseCode() + "/" + con.getResponseMessage());

						}else{

							log( "    Connection result: " + con.getResponseCode() + "/" + con.getResponseMessage());
						}
					}finally{

						proxy.setOK( true );
					}
				}
			}catch( Throwable e ){

				logError( "    Failed: " + Debug.getNestedExceptionMessage( e ));
				logError( "    Check the logs for the 'Tor Helper Plugin' (Tools->Plugins->Log Views)" );
			}
		}

		if ( doTest( TEST_BT_CONNECT )){

			log( "Distributed protocol test" );

			NetStatusProtocolTesterBT bt_test =
				plugin.getProtocolTester().runTest(

					new NetStatusProtocolTesterListener()
					{
						private List	sessions = new ArrayList();

						@Override
						public void
						complete(
							NetStatusProtocolTesterBT	tester )
						{
							log( "Results", false );

							if ( tester.getOutboundConnects() < 4 ){

								log( "    insufficient outbound connects for analysis", false );

								return;
							}

							int outgoing_seed_ok		= 0;
							int outgoing_leecher_ok		= 0;
							int outgoing_seed_bad		= 0;
							int outgoing_leecher_bad	= 0;

							int incoming_connect_ok	= 0;

							for (int i=0;i<sessions.size();i++){

								NetStatusProtocolTesterBT.Session session = (NetStatusProtocolTesterBT.Session)sessions.get(i);

								if ( session.isOK()){

									if ( session.isInitiator()){

										if ( session.isSeed()){

											outgoing_seed_ok++;

										}else{

											outgoing_leecher_ok++;
										}
									}else{

										incoming_connect_ok++;
									}
								}else{

									if ( session.isConnected()){

										if ( session.isInitiator()){

											if ( session.isSeed()){

												outgoing_seed_bad++;

											}else{

												outgoing_leecher_bad++;
											}
										}else{

											incoming_connect_ok++;
										}
									}
								}

								log( "  " +
										( session.isInitiator()?"Outbound":"Inbound" ) + "," +
										( session.isSeed()?"Seed":"Leecher") + "," +
										session.getProtocolString(), false );
							}

							boolean	good = true;

							if ( incoming_connect_ok == 0 ){

								logError( "  No incoming connections received, likely NAT problems" );

								good = false;
							}

							if ( 	outgoing_leecher_ok > 0 &&
									outgoing_seed_ok == 0 &&
									outgoing_seed_bad > 0 ){

								logError( "  Outgoing seed connects appear to be failing while non-seeds succeed" );

								good = false;
							}

							if ( good ){

								logSuccess( "    Test successful" );
							}
						}

						@Override
						public void
						sessionAdded(
							NetStatusProtocolTesterBT.Session	session )
						{
							synchronized( sessions ){

								sessions.add( session );
							}
						}

						@Override
						public void
						log(
							String		str,
							boolean		detailed )
						{
							NetStatusPluginTester.this.log( "  " + str, detailed );
						}

						@Override
						public void
						logError(
							String		str )
						{
							NetStatusPluginTester.this.logError( "  " + str );
						}

						@Override
						public void
						logError(
							String		str,
							Throwable	e )
						{
							NetStatusPluginTester.this.logError( "  " + str, e );
						}
					});

			while( !bt_test.waitForCompletion( 5000 )){

				if ( isCancelled()){

					bt_test.destroy();

					break;
				}

				log( "    Status: " + bt_test.getStatus());
			}
		}

		if ( doTest( TEST_IPV6 )){

			log( "IPv6 test" );

			InetAddress ipv6_address = admin.getDefaultPublicAddressV6();

			if ( ipv6_address == null ){

				log( "    No default public IPv6 address found" );

			}else{

				log( "    Default public IPv6 address: " + ipv6_address.getHostAddress());

				log( "    Testing connectivity..." );

				String res = VersionCheckClient.getSingleton().getExternalIpAddress( false, true, true );

				if ( res != null && res.length() > 0 ){

					logSuccess( "        Connect succeeded, reported IPv6 address: " + res );

				}else{

					logError( "        Connect failed" );
				}
			}
		}
	}

	protected void
	addPublicAddress(
		Set<InetAddress>	addresses,
		InetAddress			address )
	{
		if ( address == null ){

			return;
		}

		if ( 	address.isAnyLocalAddress() ||
				address.isLoopbackAddress() ||
				address.isLinkLocalAddress()||
				address.isSiteLocalAddress()){

				return;
		}

		addresses.add( address );
	}

	public void
	cancel()
	{
		test_cancelled	= true;
	}

	public boolean
	isCancelled()
	{
		return( test_cancelled );
	}

	protected void
	log(
		String		str )
	{
		log( str, false );
	}

	protected void
	log(
		String		str,
		boolean		detailed )
	{
		logger.log( str, detailed );
	}

	protected void
	logSuccess(
		String	str )
	{
		logger.logSuccess( str );
	}

	protected void
	logInfo(
		String	str )
	{
		logger.logInfo( str );
	}

	protected void
	log(
		String		str,
		Throwable	e )
	{
		logger.log( str + ": " + e.getLocalizedMessage(), false );
	}

	protected void
	logError(
		String	str )
	{
		logger.logFailure( str );
	}

	protected void
	logError(
		String		str,
		Throwable	e )
	{
		logger.logFailure( str + ": " + e.getLocalizedMessage());
	}

	public interface
	loggerProvider
	{
		public void
		log(
			String	str,
			boolean	is_detailed );

		public void
		logSuccess(
			String	str );

		public void
		logInfo(
			String	str );

		public void
		logFailure(
			String	str );
	}

}
