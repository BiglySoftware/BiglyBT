/*
 * Created on Aug 18, 2014
 * Created by Paul Gardner
 *
 * Copyright 2014 Azureus Software, Inc.  All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */

package com.biglybt.core.util.spi;

import java.lang.reflect.*;
import java.net.InetAddress;
import java.net.UnknownHostException;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.util.AENetworkClassifier;
import com.biglybt.core.util.TorrentUtils;
import com.biglybt.pif.PluginAdapter;
import com.biglybt.pif.PluginInterface;
import com.biglybt.plugin.I2PHelpers;

import sun.net.spi.nameservice.NameService;
import sun.net.spi.nameservice.NameServiceDescriptor;
import sun.net.spi.nameservice.dns.DNSNameService;

/*
 * This proxy is controlled by the setting in ConfigurationManager
 *
 * 		System.setProperty("sun.net.spi.nameservice.provider.1","dns,aednsproxy");
 *
 * and also requires META-INF/services/sun.net.spi.nameservice.NameServiceDescriptor to contain the text
 *
 *  	com.biglybt.core.util.spi.AENameServiceDescriptor
 *
 * On OSX you will need to do the following to get things to compile:
 *
 * Windows -> Preferences -> Java -> Compiler -> Errors/Warnings -> Deprecated and restricted API -> Forbidden reference (access rules): -> change to warning
 *
 */

public class
AENameServiceDescriptor
	implements NameServiceDescriptor
{
		// used in NetworkAdminImpl - not direct fixup due to loading issues observed...

	private final static NameService 	delegate_ns;
	private final static Method 		delegate_ns_method_lookupAllHostAddr;

	private final static Object		 	delegate_iai;
	private final static Method 		delegate_iai_method_lookupAllHostAddr;


	private final static NameService proxy_name_service;

	static{
		NameService default_ns 					= null;
		Method		default_lookupAllHostAddr	= null;

		NameService new_ns = null;

		try{
			default_ns = new DNSNameService();

			if ( default_ns != null ){

				default_lookupAllHostAddr = default_ns.getClass().getMethod( "lookupAllHostAddr", String.class );

				new_ns =
						(NameService)Proxy.newProxyInstance(
							NameService.class.getClassLoader(),
							new Class[]{ NameService.class },
							new NameServiceProxy());
			}
		}catch( Throwable e ){

			e.printStackTrace();
		}

		/*
		 * It almost works by delegating the the DNSNameService directly apart from InetAddress.getLocalHost() - rather than returning something sensible
		 * it fails with unknown host. However, if we directly grab the InetAddressImpl and use this (which has already been set up to use the default name server)
		 * things work better :( Hacked to support both at the moment...
		 */

		Object	iai						= null;
		Method	iai_lookupAllHostAddr	= null;

		try{
			Field field = InetAddress.class.getDeclaredField( "impl" );

			field.setAccessible( true );

			iai = field.get( null );

			iai_lookupAllHostAddr = iai.getClass().getMethod( "lookupAllHostAddr", String.class );

			iai_lookupAllHostAddr.setAccessible( true );

		}catch( Throwable e ){

			System.err.println( "Issue resolving the default name service..." );
		}

		proxy_name_service						= new_ns;
		delegate_ns 							= default_ns;
		delegate_ns_method_lookupAllHostAddr 	= default_lookupAllHostAddr;
		delegate_iai 							= iai;
		delegate_iai_method_lookupAllHostAddr 	= iai_lookupAllHostAddr;
	}

	private static boolean 				config_listener_added;
	private static volatile boolean 	tracker_dns_disabled;
	private static volatile boolean		tracker_plugin_proxies_permit;

	@Override
	public NameService
	createNameService()

		throws Exception
	{
		if ( proxy_name_service == null ){

			throw( new Exception( "Failed to create proxy name service" ));
		}

		return( proxy_name_service );
	}

	@Override
	public String
	getType()
	{
		return( "dns" );
	}

	@Override
	public String
	getProviderName()
	{
		return( "aednsproxy" );
	}

	private static class
	NameServiceProxy
		implements InvocationHandler
	{
		@Override
		public Object
		invoke(
			Object		proxy,
			Method 		method,
			Object[]	args )

			throws Throwable
		{
			String method_name = method.getName();

			if ( method_name.equals( "lookupAllHostAddr" )){

				String host_name = (String)args[0];

				if ( host_name.equals( NetworkAdmin.DNS_SPI_TEST_HOST )){

					if ( delegate_ns == null ){

						throw( new RuntimeException( "Delegate Name Service unavailable" ));
					}

					host_name = "www.google.com";

					try{
						Object result = null;

						if ( delegate_iai_method_lookupAllHostAddr != null ){

							try{
								result = delegate_iai_method_lookupAllHostAddr.invoke(  delegate_iai, host_name );

							}catch( Throwable e ){
							}
						}

						if ( result == null ){

							result = delegate_ns_method_lookupAllHostAddr.invoke( delegate_ns, host_name );
						}



					}catch( Throwable e ){

						if ( e instanceof InvocationTargetException ){
							
							e = ((InvocationTargetException)e).getTargetException();
						}
						
						if ( e instanceof UnknownHostException ){

								// guess their DNS might be down - don't treat as complete fail

							System.err.println( "DNS resolution of " + host_name + " failed, DNS unavailable?" );

						}else{

							throw( new RuntimeException( "Delegate lookup failed", e ));
						}
					}

					// byte[][] or InetAddress[]

					Class ret_type = method.getReturnType();

					if ( ret_type.equals( byte[][].class )){

						return( new byte[][]{ {127,0,0,1}});

					}else{

						return( new InetAddress[]{ InetAddress.getByAddress( new byte[]{ 127, 0, 0, 1 })});
					}
				}

				boolean tracker_request = TorrentUtils.getTLSTorrentHash() != null;

				if ( tracker_request ){

					synchronized( this ){

						if ( !config_listener_added ){

							config_listener_added = true;

							COConfigurationManager.addAndFireListener(
									new COConfigurationListener()
									{
										@Override
										public void
										configurationSaved()
										{
											boolean	enable_proxy 	= COConfigurationManager.getBooleanParameter("Enable.Proxy");
										    boolean enable_socks	= COConfigurationManager.getBooleanParameter("Enable.SOCKS");
										    boolean prevent_dns		= COConfigurationManager.getBooleanParameter("Proxy.SOCKS.Tracker.DNS.Disable");

											tracker_plugin_proxies_permit =
													enable_proxy&&
													enable_socks&&
													!COConfigurationManager.getBooleanParameter( "Proxy.SOCKS.disable.plugin.proxies" );

										    tracker_dns_disabled = enable_proxy&&enable_socks&&prevent_dns;
										}
									});
						}
					}

					if ( tracker_plugin_proxies_permit ){

							// in this case we want non-public addresses to be sent to the plugin proxy, not handed
							// unresolved to a SOCKS proxy, so use a runtime exception to
							// prevent this (UnknownHostException causes SOCKS usage)

						if ( AENetworkClassifier.categoriseAddress( host_name ) != AENetworkClassifier.AT_PUBLIC ){

							throw( new RuntimeException( "Plugin proxies enabled for SOCKS"));
						}
					}

					if ( tracker_dns_disabled ){

							// this will normally result in the address being marked as 'unresolved' and
							// therefore be passed through socks 4a/5 for remote resolution

						throw( new UnknownHostException( host_name ));
					}
				}
			}

			return( invokeSupport( method_name, args ));
		}

		private Object
		invokeSupport(
			String		method_name,
			Object[]	args )

			throws Throwable
		{
			if ( method_name.equals( "getHostByAddr" )){

				byte[] address_bytes = (byte[])args[0];

				//System.out.println( method_name + ": " + ByteFormatter.encodeString( address_bytes ));

				return delegate_ns.getHostByAddr( address_bytes );

			}else if ( method_name.equals( "lookupAllHostAddr" )){

				String host_name = (String)args[0];

				//System.out.println( method_name + ": " + host_name );

				if ( host_name == null || host_name.equals( "null" )){

					// get quite a few of these from 3rd party libs :(
					// new Exception("Bad DNS lookup: " + host_name).printStackTrace();

				}else if ( host_name.endsWith( ".i2p" )){

					//new Exception( "Prevented DNS leak for " + host_name ).printStackTrace();

					checkI2PInstall( host_name );

					throw( new UnknownHostException( host_name ));

				}else if ( host_name.endsWith( ".onion" )){

					//new Exception( "Prevented DNS leak for " + host_name ).printStackTrace();

					throw( new UnknownHostException( host_name ));
				}

				// System.out.println( "DNS: " + host_name );

				try{
					if ( delegate_iai_method_lookupAllHostAddr != null ){

						try{
							return( delegate_iai_method_lookupAllHostAddr.invoke(  delegate_iai, host_name ));

						}catch( Throwable e ){
						}
					}

					return( delegate_ns_method_lookupAllHostAddr.invoke( delegate_ns, host_name ));

				}catch( InvocationTargetException e ){

					throw(((InvocationTargetException)e).getTargetException());
				}

			}else{

				throw( new IllegalArgumentException( "Unknown method '" + method_name + "'" ));
			}
		}
	}

	private static boolean i2p_checked = false;

	private static void
	checkI2PInstall(
		final String	host_name )
	{
		synchronized( AENameServiceDescriptor.class ){

			if ( i2p_checked ){

				return;
			}


			try{
				Core core = CoreFactory.getSingleton();

				if ( core != null ){

					i2p_checked = true;

					PluginInterface pi = core.getPluginManager().getDefaultPluginInterface();

					pi.addListener(
						new PluginAdapter()
						{
							@Override
							public void
							initializationComplete()
							{
								if ( I2PHelpers.isI2PInstalled()){

									return;
								}

								final boolean[]	install_outcome = { false };

								String enable_i2p_reason =
									MessageText.getString( "azneti2phelper.install.reason.dns", new String[]{ host_name });

								I2PHelpers.installI2PHelper(
										enable_i2p_reason,
										"azneti2phelper.install.dns.resolve",
										install_outcome,
										new Runnable()
										{
											@Override
											public void
											run()
											{
												if ( !install_outcome[0] ){

												}
											}
										});
							}
						});
				}
			}catch( Throwable e ){

			}
		}
	}
}
