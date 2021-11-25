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
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.TorrentUtils;
import com.biglybt.pif.PluginAdapter;
import com.biglybt.pif.PluginInterface;
import com.biglybt.plugin.I2PHelpers;

public class 
AENameServiceJava9 
{	
	public static boolean
	init()
	{
		try{
			final Class<?> iai_class = Class.forName( "java.net.InetAddressImpl" );
					
			final Class<?> ia_class = Class.forName( "java.net.InetAddress" );
	
			Field f = ia_class.getDeclaredField( "impl" );
			
			f.setAccessible( true );
			
			Field modifiersField = Field.class.getDeclaredField("modifiers");
		    
			modifiersField.setAccessible(true);
		    
			modifiersField.setInt(f, f.getModifiers() & ~Modifier.FINAL);
			
			final Object existing = f.get( null );
			
			Object proxy = Proxy.newProxyInstance( 
				iai_class.getClassLoader(), 
				new Class[] { iai_class },
				new NameServiceProxy( existing ));

			f.set( null, proxy );
			
			return true;
		}catch( Throwable e ){
			
			if (!Constants.isAndroid) {
				e.printStackTrace();
			}
		}
		return false;
	}
	
	private static boolean 				config_listener_added;
	private static volatile boolean 	tracker_dns_disabled;
	private static volatile boolean		tracker_plugin_proxies_permit;

	private static class
	NameServiceProxy
		implements InvocationHandler
	{
		private Object	delegate;
		
		private
		NameServiceProxy(
			Object	_delegate )
		{
			delegate = _delegate;
		}
		
		@Override
		public Object
		invoke(
			Object		proxy,
			Method 		method,
			Object[]		args )

			throws Throwable
		{
			Method delegate_method = delegate.getClass().getDeclaredMethod( method.getName(), method.getParameterTypes());
				
			delegate_method.setAccessible( true );
				
			try{					
				String method_name = method.getName();
	
				if ( method_name.equals( "lookupAllHostAddr" )){
	
					String host_name = (String)args[0];
	
					if ( host_name.equals( NetworkAdmin.DNS_SPI_TEST_HOST )){
	
						if ( delegate == null ){
	
							throw( new RuntimeException( "Delegate Name Service unavailable" ));
						}
	
						host_name = "www.google.com";
	
						try{
							Object result = delegate_method.invoke( delegate, host_name );
	
						}catch( Throwable e ){
	
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
	
				return( invokeSupport( delegate_method, method_name, args ));
				
			}catch( InvocationTargetException e ) {
				
				throw( e.getTargetException());
			}
		}

		private Object
		invokeSupport(
			Method		delegate_method,
			String		method_name,
			Object[]		args )

			throws Throwable
		{
			if ( method_name.equals( "getHostByAddr" )){

				// byte[] address_bytes = (byte[])args[0];

				//System.out.println( method_name + ": " + ByteFormatter.encodeString( address_bytes ));

				return( delegate_method.invoke( delegate, args ));
				
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
					return( delegate_method.invoke( delegate, args ));
				
				}catch( InvocationTargetException e ){
					
					Throwable target = e.getTargetException();
					
					if ( target instanceof UnknownHostException ){
					
						throw( new InvocationTargetException( new UnknownHostException( host_name )));
						
					}else{
						
						throw( e );
					}
				}
			}else{
			
				return( delegate_method.invoke( delegate, args ));
			}
		}
	}

	private static boolean i2p_checked = false;

	private static void
	checkI2PInstall(
		final String	host_name )
	{
		synchronized( AENameServiceJava9.class ){

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
