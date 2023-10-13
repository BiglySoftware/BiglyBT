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

import java.io.IOException;
import java.io.PrintWriter;
import java.net.*;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.*;
import java.util.regex.Pattern;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.instancemanager.ClientInstance;
import com.biglybt.core.instancemanager.ClientInstanceManager;
import com.biglybt.core.instancemanager.ClientInstanceManagerListener;
import com.biglybt.core.instancemanager.ClientInstanceTracked;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.networkmanager.*;
import com.biglybt.core.networkmanager.admin.*;
import com.biglybt.core.networkmanager.impl.http.HTTPNetworkManager;
import com.biglybt.core.networkmanager.impl.tcp.TCPNetworkManager;
import com.biglybt.core.networkmanager.impl.udp.UDPNetworkManager;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.PEPeerManagerListener;
import com.biglybt.core.peer.PEPeerManagerListenerAdapter;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.proxy.AEProxySelectorFactory;
import com.biglybt.core.proxy.socks.AESocksProxy;
import com.biglybt.core.proxy.socks.AESocksProxyFactory;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.platform.PlatformManagerException;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.UIManagerEvent;
import com.biglybt.pif.utils.StaticUtilities;
import com.biglybt.pif.utils.Utilities;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.platform.PlatformManager;
import com.biglybt.platform.PlatformManagerCapabilities;
import com.biglybt.platform.PlatformManagerFactory;
import com.biglybt.platform.PlatformManagerPingCallback;
import com.biglybt.plugin.upnp.UPnPPlugin;
import com.biglybt.plugin.upnp.UPnPPluginService;

public class
NetworkAdminImpl
	extends NetworkAdmin
	implements AEDiagnosticsEvidenceGenerator
{
	private static final LogIDs LOGID = LogIDs.NWMAN;

	private static final boolean	FULL_INTF_PROBE	= false;

	private static InetAddress anyLocalAddress;
	private static InetAddress anyLocalAddressIPv4;
	private static InetAddress anyLocalAddressIPv6;
	private static InetAddress localhostV4;
	private static InetAddress localhostV6;

	static
	{
		try
		{
			anyLocalAddressIPv4 	= InetAddress.getByAddress(new byte[] { 0,0,0,0 });
			anyLocalAddressIPv6  	= InetAddress.getByAddress(new byte[] {0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0});
			anyLocalAddress			= new InetSocketAddress(0).getAddress();
			localhostV4 = InetAddress.getByAddress(new byte[] {127,0,0,1});
			localhostV6 = InetAddress.getByAddress(new byte[] {0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,1});
		} catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
	}


	private static final int INTERFACE_CHECK_MILLIS = 15*1000;
	private static final int ROUTE_CHECK_MILLIS		= 60*1000;
	private static final int ROUTE_CHECK_TICKS		= ROUTE_CHECK_MILLIS / INTERFACE_CHECK_MILLIS;


	private Set<NetworkInterface>					old_network_interfaces;
	private final Map<String,AddressHistoryRecord>	address_history			= new HashMap<>();
	private long									address_history_update_time;

	private InetAddress[]				currentBindIPs			= new InetAddress[] { null };
	private boolean						forceBind				= false;
	private boolean						supportsIPv6withNIO		= true;
	private boolean						supportsIPv6 = true;
	private boolean						supportsIPv4 = true;
	private boolean						ignoreIPv4;
	private boolean						ignoreIPv6;
	
	private boolean						IPv6_enabled;
	private boolean						preferIPv6;
	
	private InetAddress[]				additionalServiceBindIPs;
	
	private volatile boolean			testedIPv6Routing;
	
	{
		COConfigurationManager.addAndFireParameterListeners(
				new String[]{ "IPV6 Enable Support", "IPV6 Prefer Addresses" },
				new ParameterListener()
				{
					@Override
					public void
					parameterChanged(
						String parameterName )
					{
						setIPv6Enabled( COConfigurationManager.getBooleanParameter("IPV6 Enable Support"));
						
						preferIPv6 = COConfigurationManager.getBooleanParameter( "IPV6 Prefer Addresses" );
					}
				});

		COConfigurationManager.addResetToDefaultsListener(
				new COConfigurationManager.ResetToDefaultsListener()
				{
					@Override
					public void
					reset()
					{
						clearMaybeVPNs();
					}
				});
	}

	private int roundRobinCounterV4 = 0;
	private int roundRobinCounterV6 = 0;

	private boolean logged_bind_force_issue;

	private final CopyOnWriteList<NetworkAdminPropertyChangeListener>	listeners = new CopyOnWriteList<>();


	final NetworkAdminRouteListener
		trace_route_listener = new NetworkAdminRouteListener()
		{
			private int	node_count = 0;

			@Override
			public boolean
			foundNode(
				NetworkAdminNode	node,
				int					distance,
				int					rtt )
			{
				node_count++;

				return( true );
			}

			@Override
			public boolean
			timeout(
				int					distance )
			{
				if ( distance == 3 && node_count == 0 ){

					return( false );
				}

				return( true );
			}
		};

	private static final int ASN_MIN_CHECK = 30*60*1000;

	private long last_asn_lookup_time;

	private final List asn_ips_checked = new ArrayList(0);

	private final List as_history = new ArrayList();

	private final AsyncDispatcher		async_asn_dispacher 	= new AsyncDispatcher();
	private static final int	MAX_ASYNC_ASN_LOOKUPS	= 1024;

	final Map<InetAddress, NetworkAdminASN>	async_asn_history =
		new LinkedHashMap<InetAddress, NetworkAdminASN>(256,0.75f,true)
		{
			@Override
			protected boolean
			removeEldestEntry(
		   		Map.Entry<InetAddress, NetworkAdminASN> eldest)
			{
				return size() > 256;
			}
		};

	private final boolean 	initialised;
	
	public
	NetworkAdminImpl()
	{
		COConfigurationManager.addParameterListener(
			new String[] {"Bind IP","Enforce Bind IP"},
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					String parameterName )
				{
					checkDefaultBindAddress( false );
				}
			});

		COConfigurationManager.addAndFireParameterListeners(
				new String[]{
					ConfigKeys.Connection.BCFG_IPV_4_IGNORE_NI_ADDRESSES,
					ConfigKeys.Connection.BCFG_IPV_6_IGNORE_NI_ADDRESSES },
				(n)->{
					ignoreIPv4 = COConfigurationManager.getBooleanParameter( ConfigKeys.Connection.BCFG_IPV_4_IGNORE_NI_ADDRESSES );
					ignoreIPv6 = COConfigurationManager.getBooleanParameter( ConfigKeys.Connection.BCFG_IPV_6_IGNORE_NI_ADDRESSES );
					
					if ( n != null ){
						
						checkNetworkInterfaces( false, true );
	
						checkDefaultBindAddress( false );
					}
				});
		
		SimpleTimer.addPeriodicEvent(
			"NetworkAdmin:checker",
			INTERFACE_CHECK_MILLIS,
			new TimerEventPerformer()
			{
				private int	tick_count;

				@Override
				public void
				perform(
					TimerEvent event )
				{
					tick_count++;

					boolean changed = checkNetworkInterfaces( false, false );

					if ( 	changed ||
							tick_count % ROUTE_CHECK_TICKS == 0 ){

						checkConnectionRoutes();
					}
				}
			});

			// populate initial values

		checkNetworkInterfaces( true, true );

		checkDefaultBindAddress( true );

		COConfigurationManager.addAndFireParameterListener(
			ConfigKeys.Connection.SCFG_NETWORK_ADDITIONAL_SERVICE_BINDS,	
			(n)->{
					setupAdditionalServiceBindIPs( COConfigurationManager.getStringParameter( n ));
				});
		
		AEDiagnostics.addWeakEvidenceGenerator( this );

		if (System.getProperty("skip.dns.spi.test", "0").equals("0")) {
			checkDNSSPI();
		}

		CoreFactory.addCoreRunningListener(
			new CoreRunningListener()
			{
				@Override
				public void
				coreRunning(
					Core core )
				{
					setup( core );
				}
			});

		initialised = true;
	}

	private void
	setup(
		Core	core )
	{
		try{
			Class.forName("com.biglybt.ui.swt.core.nwmadmin.NetworkAdminSWTImpl").getConstructor(
				new Class[]{ Core.class, NetworkAdminImpl.class }).newInstance(
					new Object[]{ core, NetworkAdminImpl.this });

		}catch( Throwable e ){
		}
		
		COConfigurationManager.addAndFireParameterListeners(
				new String[] { "Enforce Bind IP", "Enforce Bind IP Pause" },
				new ParameterListener()
				{
					private Object	lock = new Object();
					
					private TimerEventPeriodic	timer;
							
					private boolean	paused;
					
					TimerEventPerformer listener = (event_unused) -> 
						{	
							boolean	enforce 		= COConfigurationManager.getBooleanParameter( "Enforce Bind IP");
							boolean	enforce_pause 	= COConfigurationManager.getBooleanParameter( "Enforce Bind IP Pause");

							boolean bad = enforce && enforce_pause && hasMissingForcedBind();
							
							synchronized( lock ){
								
								if ( bad || paused ){
								
									paused = bad;
																							
									List<DownloadManager> dms = core.getGlobalManager().getDownloadManagers();
									
									for ( DownloadManager dm: dms ){
										
										if ( paused ){
											
											if ( !dm.isPaused()){
											
												int state = dm.getState();
																								
												if (	state != DownloadManager.STATE_ERROR && 
														state != DownloadManager.STATE_STOPPED && 
														state != DownloadManager.STATE_STOPPING ){
												
													boolean can_pause = true;
													
													if ( dm.getMoveProgress() != null || FileUtil.hasTask( dm )){
														
														can_pause = false;
														
													}else if ( state == DownloadManager.STATE_CHECKING ){

														can_pause = false;

													}else if ( state == DownloadManager.STATE_SEEDING ){

														DiskManager disk_manager = dm.getDiskManager();

														if ( disk_manager != null ){

															if ( disk_manager.getCompleteRecheckStatus() != -1 ){
																
																can_pause = false;
															}
														}
													}

													if ( can_pause ){
														
														if ( dm.pause( true )){
															
															dm.setStopReason( "{label.binding.missing}" );
														}
													}else{
														
														PEPeerManager pm = dm.getPeerManager();
														
														if ( pm != null ){
															
															addKickAllPeerListener( pm );
														}
													}
												}
											}
										}else{
											
											if ( dm.isPaused()){
												
												String reason = dm.getStopReason();
												
												if ( reason != null && reason.equals( "{label.binding.missing}" )){
													
													dm.resume();
												}
											}else{
												
												PEPeerManager pm = dm.getPeerManager();
												
												if ( pm != null ){
													
													removeKickAllPeerListener( pm );
												}
											}
										}
									}
								}
							}
						};
						
					@Override
					public void
					parameterChanged(
						String parameterName )
					{
						boolean	enforce 		= COConfigurationManager.getBooleanParameter( "Enforce Bind IP");
						boolean	enforce_pause 	= COConfigurationManager.getBooleanParameter( "Enforce Bind IP Pause");
						
						synchronized( lock ){
							
							if ( enforce && enforce_pause ){
								
								if ( timer == null ){
							
									timer = 
										SimpleTimer.addPeriodicEvent(
											"bind checker",
											10*1000,
											listener );
								}
							}else{
								
								if ( timer != null ){
					
									timer.cancel();
									
									timer = null;
									
									listener.perform( null );
								}
							}		
						}
					}
				});
		
		new NetworkAdminDistributedNATTester( this, core );
	}
	
	private static final String DM_PAUSE_PENDING_KEY = "NetworkAdminImpl::pausePending";
	
	private void
	addKickAllPeerListener(
		PEPeerManager		pm )
	{
		if ( pm.getData( DM_PAUSE_PENDING_KEY ) != null ){
			
			return;
		}
		
		PEPeerManagerListenerAdapter listener = 
			new PEPeerManagerListenerAdapter()
			{
				  public void 
				  peerAdded(
					 PEPeerManager manager, PEPeer peer )
				  {
					  manager.removePeer(peer, "Pause pending", Transport.CR_STOPPED_OR_REMOVED );
				  }
			};
		
		pm.setData( DM_PAUSE_PENDING_KEY, new Object[]{ pm, listener });
		
		pm.addListener( listener );

		pm.removeAllPeers( "Pause pending", Transport.CR_STOPPED_OR_REMOVED );
	}
	
	private void
	removeKickAllPeerListener(
		PEPeerManager		pm )
	{
		Object[] data = (Object[])pm.getData( DM_PAUSE_PENDING_KEY );
		
		if ( data != null ){
			
			if ( data[0] == pm ){
				
				pm.removeListener((PEPeerManagerListener)data[1] );
			}
			
			pm.setData( DM_PAUSE_PENDING_KEY, null );
		}
	}
	
	private void
	checkDNSSPI()
	{
		String	error_str	= null;

		try{
				// not supposed to exist - SPI will return loopback if things are working OK
			
			InetAddress ia = InetAddress.getByName( DNS_SPI_TEST_HOST );

			if ( ia.isLoopbackAddress()){

				// looks good!

			}else{

				error_str = "Loopback address expected, got " + ia;
			}
		}catch( UnknownHostException e ){

			error_str = "DNS SPI not loaded";

		}catch( Throwable e ){

			error_str = "Test lookup failed: " + Debug.getNestedExceptionMessage( e );
		}

		if ( error_str != null ){

			Logger.log(
				new LogAlert(
					true,
					LogAlert.AT_WARNING,
					MessageText.getString( "network.admin.dns.spi.fail", new String[]{ error_str })));
		}
	}

	protected void
	setIPv6Enabled(
		boolean enabled )
	{
		IPv6_enabled	= enabled;

		supportsIPv6withNIO		= enabled;
		supportsIPv6 			= enabled;

		if ( initialised ){

			checkNetworkInterfaces( false, true );

			checkDefaultBindAddress( false );
		}
	}

	@Override
	public boolean
	isIPV6Enabled()
	{
		return( IPv6_enabled );
	}

	private List<NetworkInterface> 	last_getni_result;
	private final Object					getni_lock = new Object();

	protected boolean
	checkNetworkInterfaces(
		boolean		first_time,
		boolean		force )
	{
		boolean	changed	= false;

		try{
			List<NetworkInterface>	x = NetUtils.getNetworkInterfaces();

			boolean	fire_stuff = false;

			synchronized( getni_lock ){

				if ( last_getni_result != x ){

					last_getni_result = x;

					if ( x.size() == 0 && old_network_interfaces == null ){

					}else if ( x.size() == 0 ){

						old_network_interfaces	= null;

						changed = true;

					}else if ( old_network_interfaces == null ){

						Set<NetworkInterface>	new_network_interfaces = new HashSet<>();

						new_network_interfaces.addAll( x );

						old_network_interfaces = new_network_interfaces;

						changed = true;

					}else{

						Set<NetworkInterface>	new_network_interfaces = new HashSet<>();

						for ( NetworkInterface ni: x ){

								// NetworkInterface's "equals" method is based on ni name + addresses

							if ( !old_network_interfaces.contains( ni )){

								changed	= true;
							}

							new_network_interfaces.add( ni );
						}

						if ( old_network_interfaces.size() != new_network_interfaces.size()){

							changed = true;
						}

						old_network_interfaces = new_network_interfaces;
					}

					if ( changed || force ){

						if ( changed ){
							
							testedIPv6Routing = false;
						}
						
						fire_stuff = true;

						boolean newV6 = false;
						boolean newV4 = false;

						Set<NetworkInterface> interfaces = old_network_interfaces;

						long now = SystemTime.getMonotonousTime();

						List<AddressHistoryRecord>	a_history = new ArrayList<>();

						if (interfaces != null)
						{
							Iterator<NetworkInterface> it = interfaces.iterator();
							while (it.hasNext())
							{
								NetworkInterface ni = it.next();
								Enumeration addresses = ni.getInetAddresses();
								while (addresses.hasMoreElements())
								{
									InetAddress ia = (InetAddress) addresses.nextElement();

									a_history.add( new AddressHistoryRecord( ni, ia, now ));

									if (ia.isLoopbackAddress()){
										continue;
									}
									if (ia instanceof Inet6Address && !ia.isLinkLocalAddress()){
										if ( IPv6_enabled ){
											newV6 = true;
										}
									}else if (ia instanceof Inet4Address){
										newV4 = true;
									}
								}
							}
						}


						synchronized( address_history ){

							address_history_update_time = now;

							for ( AddressHistoryRecord entry: a_history ){

								String name = entry.getAddress().getHostAddress();

								AddressHistoryRecord existing = address_history.get( name );

								if ( existing == null ){

									address_history.put( name, entry );

								}else{

									existing.setLastSeen( now );
								}
							}

							Iterator<AddressHistoryRecord> it = address_history.values().iterator();

							while( it.hasNext()){

								AddressHistoryRecord entry = it.next();

								long	age = now - entry.getLastSeen();

								if ( age > 10*60*1000 ){

									it.remove();
								}
							}
						}

						supportsIPv4 = newV4;
						supportsIPv6 = newV6;

						Logger.log(new LogEvent(LOGID, "NetworkAdmin: ipv4 supported: "+supportsIPv4+"; ipv6: "+supportsIPv6+"; probing v6+nio functionality"));

						if ( newV6 ){

							try{
								ServerSocketChannel channel = ServerSocketChannel.open();
	
								try{
									channel.configureBlocking(false);
									channel.socket().bind(new InetSocketAddress(anyLocalAddressIPv6, 0));
									Logger.log(new LogEvent(LOGID, "NetworkAdmin: testing nio + ipv6 bind successful"));
	
									supportsIPv6withNIO = true;
									
								}catch (Throwable e){
									
									Logger.log(new LogEvent(LOGID,LogEvent.LT_WARNING, "nio + ipv6 test failed",e));
									
									supportsIPv6withNIO = false;
								}
	
								channel.close();
								
							}catch( Throwable e ){
								
							}
						}else{
							
							supportsIPv6withNIO = false;
						}
						
						if ( !first_time ){

							Logger.log(
								new LogEvent(LOGID,
										"NetworkAdmin: network interfaces have changed" ));
						}
					}
				}
			}
			
			/*
			{
				String str = "";
				
				for ( NetworkInterface ni: old_network_interfaces ){
					
					str += (str.isEmpty()?"":" / ") +ni.getName() + "=";
					
					Enumeration<InetAddress> addresses = ni.getInetAddresses();
					
					String a_str = "";
					
					while( addresses.hasMoreElements()){
						
						a_str += (a_str.isEmpty()?"":", ") + addresses.nextElement();
					}
					
					str += a_str;
				}
				
				Debug.outNoStack( "checkNetworkInterfaces: " + str + " -> " + fire_stuff );
			}
			*/
			
			if ( fire_stuff ){

				interfacesChanged( first_time );
			}
		}catch( Throwable e ){
			
			if ( Constants.IS_CVS_VERSION ){
			
				Debug.out( e );
			}
		}

		return( changed );
	}

	@Override
	public InetAddress getMultiHomedOutgoingRoundRobinBindAddress(InetAddress target)
	{
		InetAddress[]	addresses = currentBindIPs;
		boolean v6 = target instanceof Inet6Address;
		int previous = (v6 ? roundRobinCounterV6 : roundRobinCounterV4) % addresses.length;
		InetAddress toReturn = null;

		int i = previous;

		do
		{
			i++;i%= addresses.length;
			if (target == null || (v6 && addresses[i] instanceof Inet6Address) || (!v6 && addresses[i] instanceof Inet4Address))
			{
				toReturn = addresses[i];
				break;
			} else if(!v6 && addresses[i].isAnyLocalAddress())
			{
				toReturn = anyLocalAddressIPv4;
				break;
			}
		} while(i!=previous);

		if(v6)
			roundRobinCounterV6 = i;
		else
			roundRobinCounterV4 = i;
		return toReturn != null ? toReturn : (v6 ? localhostV6 : localhostV4);
	}

	private void
	setupAdditionalServiceBindIPs(
		String		str )
	{
		List<InetAddress> addrs = parseAddresses( str );
		
		InetAddress[] latest = addrs.isEmpty()?null:addrs.toArray( new InetAddress[addrs.size()]);
		
		if ( !Arrays.equals( latest, additionalServiceBindIPs )){
		
			additionalServiceBindIPs = latest;
			
			firePropertyChange( NetworkAdmin.PR_ADDITIONAL_SERVICE_ADDRESS );
		}
	}
	
	@Override
	public InetAddress[] getMultiHomedServiceBindAddresses(boolean nio)
	{
		InetAddress[] bindIPs = currentBindIPs;
		for(int i=0;i<bindIPs.length;i++)
		{
			if(bindIPs[i].isAnyLocalAddress())
				return new InetAddress[] {nio && !supportsIPv6withNIO && bindIPs[i] instanceof Inet6Address ? anyLocalAddressIPv4 : bindIPs[i]};
		}
		
		InetAddress[] extra = additionalServiceBindIPs;
		
		if ( extra != null ){
			
			InetAddress[] result = new InetAddress[bindIPs.length+extra.length];
			
			System.arraycopy( bindIPs, 0, result, 0, bindIPs.length );
			System.arraycopy( extra, 0, result, bindIPs.length, extra.length );
			
			return( result );
			
		}else{
		
			return bindIPs;
		}
	}

	@Override
	public InetAddress getSingleHomedServiceBindAddress(int proto)
	{
		InetAddress[] addrs = currentBindIPs;
		
		if ( proto == IP_PROTOCOL_VERSION_AUTO ){
			
				// don't try and do anything smart here regarding converting an 'any local address' into either
				// anyLocalAddressIPv4 or anyLocalAddressIPv6 depending on preferv6 or supportsV6 because this stuffs
				// up other code in PRUDPPacketHandlerImpl creating 'alt-protocol-delegates' Don't ask me...
			
			return( addrs[0] );
			
		}else{
			
			for( InetAddress addr: addrs ){

				if( (proto == IP_PROTOCOL_VERSION_REQUIRE_V4 && addr instanceof Inet4Address || addr.isAnyLocalAddress()) ||
					(proto == IP_PROTOCOL_VERSION_REQUIRE_V6 && addr instanceof Inet6Address) ){

					if ( addr.isAnyLocalAddress()){

						if ( proto == IP_PROTOCOL_VERSION_REQUIRE_V4 ){

							return( anyLocalAddressIPv4 );

						}else{

							return( anyLocalAddressIPv6 );
						}
					}else{

						return( addr );
					}
				}
			}
		}

		throw(
				new UnsupportedAddressTypeException(){
					public String
					getMessage()
					{
						return(	"No bind address for " + (proto == IP_PROTOCOL_VERSION_REQUIRE_V4?"IPv4":"IPv6" ));
					}
				});
	}

	@Override
	public InetAddress[] 
	getSingleHomedServiceBinding( String host) 
		throws UnknownHostException, UnsupportedAddressTypeException
	{
		List<InetAddress> addresses;
		
		try{
			addresses = DNSUtils.getSingleton().getAllByName( host );
			
		}catch( Throwable e ){
			
			addresses = Arrays.asList( InetAddress.getAllByName( host ));
		}
		
		List<Inet4Address> ip4 = new ArrayList<>();
		List<Inet6Address> ip6 = new ArrayList<>();
		
		for ( InetAddress ia: addresses ){
			if ( ia instanceof Inet4Address ){
				ip4.add((Inet4Address)ia);
			}else{
				ip6.add((Inet6Address)ia);
			}
		}
		
		InetAddress	target_ia;
		InetAddress target_bind;
		
		if ( ip6.isEmpty()){
		
			target_ia = ip4.get(0);
			
			target_bind = getSingleHomedServiceBindAddress( IP_PROTOCOL_VERSION_REQUIRE_V4 );
			
		}else if ( ip4.isEmpty()){
			
			target_ia = ip6.get(0);
			
			target_bind = getSingleHomedServiceBindAddress( IP_PROTOCOL_VERSION_REQUIRE_V6 );
			
		}else{
			
			InetAddress bind_v4 = null;
			InetAddress bind_v6 = null;
			
			try{
				bind_v4 = getSingleHomedServiceBindAddress( IP_PROTOCOL_VERSION_REQUIRE_V4 );
				
			}catch( Throwable e ){		
			}
			
			try{
				bind_v6 = getSingleHomedServiceBindAddress( IP_PROTOCOL_VERSION_REQUIRE_V6 );
				
			}catch( Throwable e ){
			}
			
			if ( bind_v4 == null && bind_v6 == null ){
				
				throw(
					new UnsupportedAddressTypeException(){
						public String
						getMessage()
						{
							return(	"No compatible bind address for '" + host + "'" );
						}
					});
				
			}else{
				
				if ( bind_v4 != null && bind_v6 != null ){
				
					if ( preferIPv6 ){
						
						bind_v4 = null;
						
					}else{
						
							// need to respect the order, ipv6.torrent.ubuntu.com doesn't work correctly if you don't (get a response but it has invalid crypto_flags and no peers...)
						
						if ( addresses.get(0) instanceof Inet4Address ){
							
							bind_v6 = null;
							
						}else{
							
							bind_v4 = null;
						}
					}
				}
				
				if ( bind_v6 == null ){
					
					target_ia 	= ip4.get(0);
					target_bind = bind_v4;
					
				}else{
					target_ia 	= ip6.get(0);
					target_bind = bind_v6;
				}
			}
		}
		
		return( new InetAddress[]{ target_ia, target_bind });
	}
	
	@Override
	public List<InetAddress[]>
	getSingleHomedServiceBindings( 
		String host )
	
		throws UnknownHostException, UnsupportedAddressTypeException
	{
		List<InetAddress> addresses;
		
		try{
			addresses = DNSUtils.getSingleton().getAllByName( host );
			
		}catch( Throwable e ){
			
			addresses = Arrays.asList( InetAddress.getAllByName( host ));
		}
		
		InetAddress bind_v4 = null;
		InetAddress bind_v6 = null;
		
		boolean has_b4 = false;
		boolean has_b6 = false;
		
		try{
			bind_v4 = getSingleHomedServiceBindAddress( IP_PROTOCOL_VERSION_REQUIRE_V4 );
			
			has_b4 = true;
			
		}catch( Throwable e ){		
		}
		
		try{
			bind_v6 = getSingleHomedServiceBindAddress( IP_PROTOCOL_VERSION_REQUIRE_V6 );
			
			has_b6 = true;
			
		}catch( Throwable e ){
		}
		
		List<InetAddress[]> bindings = new ArrayList<>( addresses.size());
		
		for ( InetAddress ia: addresses ){
			if ( ia instanceof Inet4Address ){
				if ( has_b4 ){
					bindings.add( new InetAddress[]{ ia, bind_v4 });
				}
			}else{
				if ( has_b6 ){
					bindings.add( new InetAddress[]{ ia, bind_v6 });
				}
			}
		}
		
		if ( bindings.isEmpty()){
			
			throw(
				new UnsupportedAddressTypeException(){
					public String
					getMessage()
					{
						return(	"No compatible bind address for '" + host + "'" );
					}
				});
		}
	
		if ( preferIPv6 ){
			
			Collections.sort( bindings, (b1,b2)->{
				
				return(Boolean.compare( b1[0] instanceof Inet4Address, b2[0] instanceof Inet4Address));
			});
		}	
		
		return( bindings );
	}
	
	@Override
	public InetAddress[]
	getAllBindAddresses(
		boolean	include_wildcard )
	{
		if ( include_wildcard ){

			return( currentBindIPs );

		}else{

			List<InetAddress> res = new ArrayList<>();

			InetAddress[] bind_ips = currentBindIPs;

			for ( InetAddress ip: bind_ips ){

				if( !ip.isAnyLocalAddress()){

					res.add( ip );
				}
			}

			return( res.toArray( new InetAddress[ res.size()]));
		}
	}

	@Override
	public InetAddress[]
	resolveBindAddresses(
		String	bind_to )
	{
		return( calcBindAddresses( bind_to, false ));
	}

	private static List<InetAddress>
	parseAddresses(
		String		str )
	{
		if ( str == null ){
			str = "";
		}
	
		ArrayList<InetAddress> addrs = new ArrayList<>();

		Pattern addressSplitter = Pattern.compile(";");
		Pattern interfaceSplitter = Pattern.compile("[\\]\\[]");

		String[] tokens = addressSplitter.split(str);

addressLoop:
		for(int i=0;i<tokens.length;i++)
		{
			String currentAddress = tokens[i];

			currentAddress = currentAddress.trim();

			if ( currentAddress.length() == 0 ){
				continue;
			}

			InetAddress parsedAddress = null;

			try
			{ // literal ipv4 or ipv6 address
				if( AddressUtils.isPotentialLiteralOrHostAddress( currentAddress )){
					parsedAddress = InetAddress.getByName(currentAddress);
				}
			} catch (Exception e)
			{ // ignore, could be an interface name containing a ':'
			}

			if(parsedAddress != null)
			{
				try
				{
					// allow wildcard address as 1st address, otherwise only interface addresses
					if((!parsedAddress.isAnyLocalAddress() || addrs.size() > 0) && NetUtils.getByInetAddress(parsedAddress) == null)
						continue;
				} catch ( Throwable e)
				{
					Debug.printStackTrace(e);
					continue;
				}
				addrs.add(parsedAddress);
				continue;
			}

			// interface name
			String[] ifaces = interfaceSplitter.split(currentAddress);

			NetworkInterface netInterface = null;
			try
			{
				netInterface = NetUtils.getByName(ifaces[0]);
			} catch (Throwable e)
			{
				e.printStackTrace(); // should not happen
			}
			
			if (netInterface == null ){
				continue;
			}
			
			Enumeration interfaceAddresses = netInterface.getInetAddresses();
			if(ifaces.length != 2)
				while(interfaceAddresses.hasMoreElements())
					addrs.add((InetAddress)interfaceAddresses.nextElement());
			else
			{
				int selectedAddress = 0;
				try { selectedAddress = Integer.parseInt(ifaces[1]); }
				catch (NumberFormatException e) {} // ignore, user could by typing atm
				for(int j=0;interfaceAddresses.hasMoreElements();j++,interfaceAddresses.nextElement())
					if(j==selectedAddress)
					{
						addrs.add((InetAddress)interfaceAddresses.nextElement());
						continue addressLoop;
					}
			}
		}

		return( addrs );
	}
	
	private InetAddress[] 
	calcBindAddresses(final String addressString, boolean enforceBind)
	{
		List<InetAddress> addrs = parseAddresses( addressString );

		if ( !IPv6_enabled ){

			Iterator<InetAddress> it = addrs.iterator();

			while( it.hasNext()){

				if ( it.next() instanceof Inet6Address ){

					it.remove();
				}
			}
		}

		if(addrs.size() < 1){
			return new InetAddress[] {enforceBind ? localhostV4 : (hasIPV6Potential() ? anyLocalAddressIPv6 : anyLocalAddressIPv4)};
		}

		return( addrs.toArray(new InetAddress[addrs.size()]));

	}

	private String
	checkBindAddresses(
		boolean	log_alerts )
	{
		Pattern addressSplitter 	= Pattern.compile(";");
		Pattern interfaceSplitter 	= Pattern.compile("[\\]\\[]");

		String 	bind_ips 	= COConfigurationManager.getStringParameter("Bind IP", "").trim();
		boolean enforceBind = COConfigurationManager.getBooleanParameter("Enforce Bind IP");

		if ( enforceBind && bind_ips.length() == 0 ){

			if ( log_alerts ){

				Logger.log(
					new LogAlert(
						true,
						LogAlert.AT_WARNING,
						MessageText.getString( "network.admin.bind.enforce.fail" )));
			}
		}

		String[] tokens = addressSplitter.split( bind_ips );

		String	failed_entries = "";

		for ( int i=0;i<tokens.length;i++ ){

			String currentAddress = tokens[i];

			currentAddress = currentAddress.trim();

			if ( currentAddress.length() == 0 ){

				continue;
			}

			boolean ok = false;

			InetAddress parsedAddress = null;

			try{
				if ( AddressUtils.isPotentialLiteralOrHostAddress( currentAddress )){

					parsedAddress = InetAddress.getByName(currentAddress);
				}
			}catch ( Throwable e){
			}

			if ( parsedAddress != null ){

				try{
					if (  	parsedAddress.isAnyLocalAddress() ||
							NetUtils.getByInetAddress( parsedAddress ) != null ){

						ok = true;
					}

				}catch( Throwable e ){
				}
			}else{

					// interface name

				String[] ifaces = interfaceSplitter.split( currentAddress );

				NetworkInterface netInterface = null;

				try{
					netInterface = NetUtils.getByName( ifaces[0] );

				}catch( Throwable e ){
				}

				if ( netInterface != null ){

					Enumeration interfaceAddresses = netInterface.getInetAddresses();

					if ( ifaces.length != 2 ){

						ok = interfaceAddresses.hasMoreElements();

					}else{

						try{
							int selectedAddress = Integer.parseInt(ifaces[1]);

							for( int j=0; interfaceAddresses.hasMoreElements(); j++, interfaceAddresses.nextElement()){

								if (j == selectedAddress ){

									ok = true;

									break;
								}
							}
						}catch( Throwable e ){
						}
					}
				}
			}

			if ( !ok ){

				failed_entries += (failed_entries.length()==0?"":", " ) + currentAddress;
			}
		}

		if ( failed_entries.length() > 0 ){

			if ( log_alerts ){

				Logger.log(
					new LogAlert(
						true,
						LogAlert.AT_WARNING,
						"Bind IPs not resolved: " + failed_entries + "\n\nSee Tools->Options->Connection->Advanced Network Settings" ));
			}

			return( failed_entries );
		}

		return( null );
	}

	protected void checkDefaultBindAddress(boolean first_time)
	{
		boolean changed = false;
		String 	bind_ip 	= COConfigurationManager.getStringParameter("Bind IP", "").trim();

		boolean enforceBind = COConfigurationManager.getBooleanParameter("Enforce Bind IP");

		if ( enforceBind ){

			if ( bind_ip.length() == 0 ){

				if ( !logged_bind_force_issue ){

					logged_bind_force_issue = true;

					Debug.out( "'Enforce IP Bindings' is selected but no bindings have been specified - ignoring force request!" );
				}

				enforceBind = false;

			}else{

				logged_bind_force_issue = false;
			}
		}

		forceBind = enforceBind;

		InetAddress[] addrs = calcBindAddresses(bind_ip, enforceBind);
		
		changed = !Arrays.equals(currentBindIPs, addrs);
		
		/*
		{
			String str = "";
			
			for ( InetAddress ia: addrs ){
				
				str += (str.isEmpty()?"":", ") + ia;
			}
			
			Debug.outNoStack( "checkDefaultBindAddress: " + str + " -> " + changed );
		}
		*/
		
		if ( changed ){
			
			currentBindIPs = addrs;
			
			if (!first_time){

				String logmsg = "NetworkAdmin: default bind ip has changed to '";
				
				for(int i=0;i<addrs.length;i++){
					
					logmsg+=(addrs[i] == null ? "none" : addrs[i].getHostAddress()) + (i<addrs.length? ";" : "");
				}
				
				logmsg+="'";
				
				Logger.log(new LogEvent(LOGID, logmsg));

					// if the user has removed a previous bind enforcement then re-activate the maybe-vpn
					// logic as they may be switching VPN

				if ( bind_ip.length() == 0 ){

					clearMaybeVPNs();
				}
			}
			
			firePropertyChange(NetworkAdmin.PR_DEFAULT_BIND_ADDRESS);
		}
	}

	@Override
	public String getNetworkInterfacesAsString(boolean only_with_addresses)
	{
		Set interfaces = old_network_interfaces;

		if (interfaces == null){

			return ("");
		}

		Iterator it = interfaces.iterator();
		StringBuilder sb = new StringBuilder( 1024 );
		while (it.hasNext())
		{
			NetworkInterface ni = (NetworkInterface) it.next();
			Enumeration addresses = ni.getInetAddresses();
			if ( only_with_addresses && !addresses.hasMoreElements()){
				continue;
			}
			sb.append( ni.getName());
			sb.append( "\t\t(" );
			sb.append( ni.getDisplayName());
			sb.append( ")\n" );
			int i = 0;
			while(addresses.hasMoreElements()){
				InetAddress address = (InetAddress)addresses.nextElement();
				sb.append( "\t" );
				sb.append( ni.getName());
				sb.append("[" );
				sb.append( i++ );
				sb.append( "]\t" );
				sb.append((address).getHostAddress());
				sb.append("\n");
			}
		}
		return (sb.toString());
	}

	@Override
	public boolean
	hasIPV4Potential()
	{
		return supportsIPv4;
	}

	@Override
	public boolean
	hasIPV6Potential(boolean nio)
	{
		return nio ? supportsIPv6withNIO : supportsIPv6;
	}
	
	@Override
	public InetAddress
	getLoopbackAddress()
	{
		if ( supportsIPv4 && supportsIPv6 ){
			
			if ( preferIPv6 ){
				
				return( Inet6Address.getLoopbackAddress());
				
			}else{
				
				InetAddress a = getSingleHomedServiceBindAddress();
				
				if ( a.isAnyLocalAddress()){
					
						// for reasons outlined in getSingleHomedServiceBindAddress this will always be an Inet6Address
						// most people probably prefer an ipv4 loopback in this case (we support both)
					
					return( Inet4Address.getLoopbackAddress());
					
				}else if ( a instanceof Inet4Address ){
					
					return( Inet4Address.getLoopbackAddress());
					
				}else{
					
					return( Inet6Address.getLoopbackAddress());
				}
			}
		}else if ( supportsIPv6 ){
			
			return( Inet6Address.getLoopbackAddress());
			
		}else{
			
			return( Inet4Address.getLoopbackAddress());
		}
	}

	@Override
	public InetAddress[]
  	getBindableAddresses()
  	{
		return( getBindableAddresses( false, false ));
  	}

	private InetAddress[]
	getBindableAddresses(
		boolean	ignore_loopback,
		boolean	ignore_link_local )
	{
  		List<InetAddress>	bindable = new ArrayList<>();

  		NetworkAdminNetworkInterface[] interfaces = NetworkAdmin.getSingleton().getInterfaces();

  		for ( NetworkAdminNetworkInterface intf: interfaces ){

  			NetworkAdminNetworkInterfaceAddress[] addresses = intf.getAddresses();

  			for ( NetworkAdminNetworkInterfaceAddress address: addresses ){

  				InetAddress a = address.getAddress();

  				if ( 	( ignore_loopback && a.isLoopbackAddress()) ||
  						( ignore_link_local && a.isLinkLocalAddress())){

  				}else{

	  				if ( canBind( a )){

	  					bindable.add( a );
	  				}
  				}
  			}
  		}

  		return( bindable.toArray( new InetAddress[ bindable.size()]));
  	}

  	protected boolean
  	canBind(
  		InetAddress	bind_ip )
  	{
  		ServerSocketChannel ssc = null;

  		try{
  			ssc = ServerSocketChannel.open();

  			ssc.socket().bind( new InetSocketAddress( bind_ip, 0 ), 16 );

  			return( true );

  		}catch( Throwable e ){

  			return( false );

  		}finally{

  			if ( ssc != null ){

  				try{
  					ssc.close();

  				}catch( Throwable e ){

  					Debug.out( e );
  				}
  			}
  		}
  	}

	@Override
	public int
	getBindablePort(
		int	prefer_port )

		throws IOException
	{
		final int tries = 1024;

		Random random = new Random();

		for ( int i=1;i<=tries;i++ ){

			int port;

			if ( i == 1 && prefer_port != 0 ){

				port = prefer_port;

			}else{

				port = i==tries?0:random.nextInt(20000) + 40000;
			}

			ServerSocketChannel ssc = null;

			try{
				ssc = ServerSocketChannel.open();

				ssc.socket().setReuseAddress( true );

				bind( ssc, null, port );

				port = ssc.socket().getLocalPort();

				ssc.close();

				return( port );

			}catch( Throwable e ){

				if ( ssc != null ){

					try{
						ssc.close();

					}catch( Throwable f ){

						Debug.printStackTrace(e);
					}

					ssc = null;
				}
			}
		}

		throw( new IOException( "No bindable ports found" ));
	}

	protected void
	bind(
		ServerSocketChannel	ssc,
		InetAddress			address,
		int					port )

		throws IOException
	{
		if ( address == null ){

			ssc.socket().bind( new InetSocketAddress( port ), 1024 );

		}else{

			ssc.socket().bind( new InetSocketAddress( address, port ), 1024 );
		}
	}

	@Override
	public InetAddress
	guessRoutableBindAddress()
	{
		try{
				// see if we have a choice

			List	local_addresses 	= new ArrayList();
			List	non_local_addresses = new ArrayList();

			try{
				NetworkAdminNetworkInterface[] interfaces = getInterfaces();

				List possible = new ArrayList();

				for (int i=0;i<interfaces.length;i++){

					NetworkAdminNetworkInterface intf = interfaces[i];

					NetworkAdminNetworkInterfaceAddress[] addresses = intf.getAddresses();

					for (int j=0;j<addresses.length;j++){

						NetworkAdminNetworkInterfaceAddress address = addresses[j];

						InetAddress ia = address.getAddress();

						if ( ia.isLoopbackAddress()){

							continue;
						}

						if ( ia.isLinkLocalAddress() ||	ia.isSiteLocalAddress()){

							local_addresses.add( ia );

						}else{

							non_local_addresses.add( ia );
						}

						if ( 	( hasIPV4Potential() && ia instanceof Inet4Address ) ||
								( hasIPV6Potential() && ia instanceof Inet6Address )){

							possible.add( ia );
						}
					}
				}

				if ( possible.size() == 1 ){

					return((InetAddress)possible.get(0));
				}
			}catch( Throwable e ){
			}

				// if we have a socks server then let's use a compatible address for it

			try{
				NetworkAdminSocksProxy[] socks = getSocksProxies();

				if ( socks.length > 0 ){

					return( mapAddressToBindIP( InetAddress.getByName( socks[0].getHost())));
				}
			}catch( Throwable e ){
			}

				// next, same for nat devices

			try{
				NetworkAdminNATDevice[] nat = getNATDevices(CoreFactory.getSingleton());

				if ( nat.length > 0 ){

					return( mapAddressToBindIP( nat[0].getAddress()));
				}
			}catch( Throwable e ){
			}

			try{
				final AESemaphore 	sem 		= new AESemaphore( "NA:conTest" );
				final InetAddress[]	can_connect = { null };

				final int	timeout = 10*1000;

				for (int i=0;i<local_addresses.size();i++){

					final InetAddress address = (InetAddress)local_addresses.get(i);

					new AEThread2( "NA:conTest", true )
					{
						@Override
						public void
						run()
						{
							if ( canConnectWithBind( address, timeout )){

								can_connect[0] = address;

								sem.release();
							}
						}
					}.start();
				}

				if ( sem.reserve( timeout )){

					return( can_connect[0] );
				}
			}catch( Throwable e ){

			}

				// take a chance on any non local addresses we have

			if ( non_local_addresses.size() > 0 ){

				return( guessAddress( non_local_addresses ));
			}

				// lastly, select local one at random

			if ( local_addresses.size() > 0 ){

				return( guessAddress( local_addresses ));
			}

				// ho hum

			return( null );

		}catch( Throwable e ){

			Debug.printStackTrace(e);

			return( null );
		}
	}

	@Override
	public InetAddress
	getAlternativeProtocolBindAddress(
		InetAddress	address )
	{
		Set<NetworkInterface> nis = old_network_interfaces;
		
		if ( nis == null ){
			
			return( null );
		}
		
		for( NetworkInterface iface : nis ){
			
			try{
				for ( InterfaceAddress ia: iface.getInterfaceAddresses()){
					
					if ( ia.getAddress().equals( address )){
						
						boolean want_v4 = address instanceof Inet6Address;
						
						for ( InterfaceAddress x: iface.getInterfaceAddresses()){
					
							InetAddress xa = x.getAddress();
							
							if ( want_v4 == ( xa instanceof Inet4Address )){
								
								return( xa );
							}
						}
						
						return( null );
					}
				}
			}catch( Throwable e ){
				// ignore potential NPE in getInterfaceAddresses 
			}
		}
		
		return( null );
	}
	
	protected boolean
	canConnectWithBind(
		InetAddress	bind_address,
		int			timeout )
	{
		Socket	socket = null;

		try{
			socket = new Socket();

			socket.bind( new InetSocketAddress( bind_address, 0 ));

			socket.setSoTimeout( timeout );

			String domain = COConfigurationManager.getStringParameter(
				ConfigKeys.Connection.SCFG_CONNECTION_TEST_DOMAIN);
			InetAddress[] addresses = AddressUtils.getAllByName( domain );
			
			InetAddress target = null;
			
			boolean is6 = bind_address instanceof Inet6Address;
			
			for ( InetAddress ia: addresses ){
				
				if ( ia instanceof Inet4Address ){
					
					if ( !is6 ){
						
						target = ia;
						
						break;
					}
				}else{
					
					if ( is6 ){
						
						target = ia;
						
						break;
					}
				}
			}
			
			InetSocketAddress isa;
			
			if ( target == null ){
				
				isa = new InetSocketAddress( domain, 80 );
				
			}else{
				
				isa = new InetSocketAddress( target, 80 );
			}
			
			socket.connect( isa, timeout );

			return( true );

		}catch( Throwable e ){

			return( false );

		}finally{

			if ( socket != null ){

				try{
					socket.close();

				}catch( Throwable f ){
				}
			}
		}
	}

	protected InetAddress
	mapAddressToBindIP(
		InetAddress	address )
	{
		boolean[]	address_bits = bytesToBits( address.getAddress());

		NetworkAdminNetworkInterface[] interfaces = getInterfaces();

		InetAddress	best_bind_address	= null;
		int			best_prefix			= 0;

		for (int i=0;i<interfaces.length;i++){

			NetworkAdminNetworkInterface intf = interfaces[i];

			NetworkAdminNetworkInterfaceAddress[] addresses = intf.getAddresses();

			for (int j=0;j<addresses.length;j++){

				NetworkAdminNetworkInterfaceAddress bind_address = addresses[j];

				InetAddress ba = bind_address.getAddress();

				byte[]	bind_bytes = ba.getAddress();

				if ( address_bits.length == bind_bytes.length ){

					boolean[]	bind_bits = bytesToBits( bind_bytes );

					for (int k=0;k<bind_bits.length;k++){

						if ( address_bits[k] != bind_bits[k] ){

							break;
						}

						if ( k > best_prefix ){

							best_prefix	= k;

							best_bind_address	= ba;
						}
					}
				}
			}
		}

		return( best_bind_address );
	}

	protected boolean[]
  	bytesToBits(
  		byte[]	bytes )
  	{
  		boolean[]	res = new boolean[bytes.length*8];

  		for (int i=0;i<bytes.length;i++){

  			byte	b = bytes[i];

  			for (int j=0;j<8;j++){

  				res[i*8+j] = (b&(byte)(0x01<<(7-j))) != 0;
  			}
  		}

  		return( res );
  	}

	protected InetAddress
	guessAddress(
		List	addresses )
	{
			// prioritise 192.168.0.* and 192.168.1.* as common
			// then ipv4 over ipv6

		for (int i=0;i<addresses.size();i++){

			InetAddress address = (InetAddress)addresses.get(i);

			String str = address.getHostAddress();

			if ( str.startsWith( "192.168.0." ) || str.startsWith( "192.168.1." )){

				return( address );
			}
		}

		for (int i=0;i<addresses.size();i++){

			InetAddress address = (InetAddress)addresses.get(i);

			if ( address instanceof Inet4Address ){

				return( address );
			}
		}

		for (int i=0;i<addresses.size();i++){

			InetAddress address = (InetAddress)addresses.get(i);

			if ( address instanceof Inet6Address ){

				return( address );
			}
		}

		if ( addresses.size() > 0 ){

			return((InetAddress)addresses.get(0));
		}

		return( null );
	}

	private static final InetAddress[]			gdpa_lock = { null };
	private static AESemaphore					gdpa_sem;
	private static long							gdpa_last_fail;
	private static long							gdpa_last_lookup;
	private static final AESemaphore			gdpa_initial_sem = new AESemaphore( "gdpa:init" );

	private static InetAddress			gdpa6			= null;
	private static int					gdpa6_count;
	private static InetAddress			gdpa6_last_good	= null;
	private static long					gdpa6_last_check;
	private static boolean				gdpa6_checking;
	
	private void
	interfacesChanged(
		boolean		first_time )
	{
		synchronized( gdpa_lock ){
			
			gdpa6 = null;
		}
		
		firePropertyChange( NetworkAdmin.PR_NETWORK_INTERFACES );

		checkDefaultBindAddress( first_time );
	}
	
	@Override
	public InetAddress
	getDefaultPublicAddress()
	{
		return( getDefaultPublicAddress( false ));
	}

	@Override
	public InetAddress
	getDefaultPublicAddress(
		boolean		peek )
	{
		final AESemaphore	sem;

		synchronized( gdpa_lock ){

			long	now = SystemTime.getMonotonousTime();

			if ( gdpa_sem == null ){

				boolean	do_lookup = true;

				if ( peek ){

					if ( gdpa_last_lookup != 0 && now - gdpa_last_lookup <= 60*1000 ){

						do_lookup = false;
					}
				}

				if ( do_lookup ){

					gdpa_last_lookup = now;

					gdpa_sem = sem = new AESemaphore( "getDefaultPublicAddress");

					new AEThread2( "getDefaultPublicAddress" )
					{
						@Override
						public void
						run()
						{
							InetAddress address = null;

							try{
								Utilities utils = PluginInitializer.getDefaultInterface().getUtilities();

								address = utils.getPublicAddress();

								if ( address == null ){

									address = utils.getPublicAddress( true );
								}
							}catch( Throwable e ){

							}finally{

								synchronized( gdpa_lock ){

									gdpa_lock[0]	= address;

									sem.releaseForever();

									gdpa_sem = null;

									gdpa_initial_sem.releaseForever();
								}
							}
						}
					}.start();
				}else{

					sem = null;		// no lookup this time around - we're peeking
				}
			}else{

				sem = gdpa_sem;
			}

			if ( gdpa_last_fail != 0 && SystemTime.getMonotonousTime() - gdpa_last_fail < 5*60*1000 ){

				return( gdpa_lock[0] );
			}
		}

		if ( peek ){

				// we're doing a peek and don't want to wait

			gdpa_initial_sem.reserve( 10*1000 );

			synchronized( gdpa_lock ){

				return( gdpa_lock[0] );
			}
		}else{
				// in case things block - yes, they can do :(

			boolean	worked = sem.reserve( 10*1000 );

			synchronized( gdpa_lock ){

				if ( worked ){

					gdpa_last_fail = 0;

				}else{

					gdpa_initial_sem.releaseForever();

					gdpa_last_fail = SystemTime.getMonotonousTime();
				}

				return( gdpa_lock[0] );
			}
		}
	}

	@Override
	public InetAddress
	getDefaultPublicAddressV6()
	{
		if ( !supportsIPv6 ){
			
			return null;
		}
		
			// check bindings first
		
		for ( InetAddress addr : currentBindIPs ){
			
				// found a specific bind address, use that one
			
			if ( AddressUtils.isGlobalAddressV6(addr)){
				
				return( addr );
			}
		}

		boolean	run_check = false;
		
		try{
			synchronized( gdpa_lock ){
	
				if ( gdpa6 != null ){
					
					if ( SystemTime.getMonotonousTime() - gdpa6_last_check > 60*1000 ){
						
						run_check = gdpa6_count > 1 && !gdpa6_checking;
					}
					
					return( gdpa6 );
				}
			}
			
			for ( InetAddress addr : currentBindIPs ){
				
					// found v6 any-local address, check interfaces for a best match
				
				if ( addr instanceof Inet6Address && addr.isAnyLocalAddress()){
					
					ArrayList<InetAddress> addrs = new ArrayList<>();
					
					for( NetworkInterface iface : old_network_interfaces ){
						
						addrs.addAll(Collections.list(iface.getInetAddresses()));
					}
					
					synchronized( gdpa_lock ){
					
						List<InetAddress> best = AddressUtils.pickBestGlobalV6Addresses( addrs );
						
						gdpa6_count = best.size();
						
						if ( gdpa6_count == 0 ){
							
							gdpa6 = null;
							
							run_check = false;
							
						}else if ( gdpa6_count == 1 ){
							
							gdpa6 = best.get(0);
							
							run_check = false;
							
						}else{
							
							run_check = !gdpa6_checking;
							
							if ( gdpa6_last_good != null && best.contains( gdpa6_last_good )){
								
								gdpa6 = gdpa6_last_good;
								
							}else{
								
								gdpa6 = best.get(0);
							}
						}
						
						return( gdpa6 );
					}
				}
			}
	
			return( null );
			
		}finally{
			
			if ( run_check ){
				
				synchronized( gdpa_lock ){
					
					if ( !gdpa6_checking ){
						
						gdpa6_checking = true;
												
						new AEThread2( "getDefaultPublicAddressV6" )
						{
							@Override
							public void
							run()
							{
								try{
									for ( InetAddress addr : currentBindIPs ){
																			
										if ( addr instanceof Inet6Address && addr.isAnyLocalAddress()){
										
											ArrayList<InetAddress> addrs = new ArrayList<>();
											
											for( NetworkInterface iface : old_network_interfaces ){
												
												addrs.addAll(Collections.list(iface.getInetAddresses()));
											}
											
											List<InetAddress> best = AddressUtils.pickBestGlobalV6Addresses( addrs );
											
											InetAddress last_good = gdpa6_last_good;
											
											if ( last_good != null && best.contains( last_good )){
												
												best.remove( last_good );
												
												best.add( 0, last_good );
											}
											
											for ( InetAddress ia: best ){
											
												if ( canConnectWithBind( ia, 10*1000 )){
													
													synchronized( gdpa_lock ){
														
														gdpa6_last_good = gdpa6 = ia;
														
														break;
													}
												}
											}
										}
									}
								
						
								
								}finally{
									
									synchronized( gdpa_lock ){
										
										gdpa6_last_check = SystemTime.getMonotonousTime();
										
										gdpa6_checking = false;
									}
								}
							}
						}.start();
					}
				}
			}
		}
	}

	@Override
	public boolean
	hasDHTIPV4()
	{
		return( !ignoreIPv4 );	// should be brave and change this to hasIPv4Potential() but as this is really just for
								// testing gonna require user to explicitly ignore V4 ATM
	}
	
	@Override
	public boolean
	hasDHTIPV6()
	{
		if ( hasIPV6Potential(false)){

			InetAddress v6 = getDefaultPublicAddressV6();

			if ( v6 == null ){

				return( false );
			}

			if ( Constants.IS_CVS_VERSION ){

				return( true );

			}else{

				return( !AddressUtils.isTeredo( v6 ));
			}
		}

		return( false );
	}

	protected void
	firePropertyChange(
		String	property )
	{
		for ( NetworkAdminPropertyChangeListener l: listeners ){

			try{
				l.propertyChanged( property );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	@Override
	public NetworkAdminNetworkInterface[]
	getInterfaces()
	{
		Set	interfaces = old_network_interfaces;

		if ( interfaces == null ){

			return( new NetworkAdminNetworkInterface[0] );
		}

		NetworkAdminNetworkInterface[]	res = new NetworkAdminNetworkInterface[interfaces.size()];

		Iterator	it = interfaces.iterator();

		int	pos = 0;

		while( it.hasNext()){

			NetworkInterface ni = (NetworkInterface)it.next();

			res[pos++] = new networkInterface( ni );
		}

		return( res );
	}

	@Override
	public NetworkAdminProtocol[]
 	getOutboundProtocols(
 			Core core)
	{
		NetworkAdminProtocol[]	res =
			{
				new NetworkAdminProtocolImpl( core, NetworkAdminProtocol.PT_HTTP ),
				new NetworkAdminProtocolImpl( core, NetworkAdminProtocol.PT_TCP ),
				new NetworkAdminProtocolImpl( core, NetworkAdminProtocol.PT_UDP ),
			};

		return( res );
	}

	@Override
	public NetworkAdminProtocol
	createInboundProtocol(
		Core core,
		int				type,
		int				port )
	{
		return(
			new NetworkAdminProtocolImpl(
				core,
				type,
				port ));
	}

 	@Override
  public NetworkAdminProtocol[]
 	getInboundProtocols(
 			Core core)
 	{
		List	protocols = new ArrayList();

		TCPNetworkManager	tcp_manager = TCPNetworkManager.getSingleton();

		if ( tcp_manager.isDefaultTCPListenerEnabled()){

			protocols.add(
					new NetworkAdminProtocolImpl(
							core,
							NetworkAdminProtocol.PT_TCP,
							tcp_manager.getDefaultTCPListeningPortNumber()));
		}

		UDPNetworkManager	udp_manager = UDPNetworkManager.getSingleton();

		int	done_udp = -1;

		if ( udp_manager.isUDPListenerEnabled()){

			protocols.add(
					new NetworkAdminProtocolImpl(
							core,
							NetworkAdminProtocol.PT_UDP,
							done_udp = udp_manager.getUDPListeningPortNumber()));
		}

		if ( udp_manager.isUDPNonDataListenerEnabled()){

			int	port = udp_manager.getUDPNonDataListeningPortNumber();

			if ( port != done_udp ){

				protocols.add(
						new NetworkAdminProtocolImpl(
								core,
								NetworkAdminProtocol.PT_UDP,
								done_udp = udp_manager.getUDPNonDataListeningPortNumber()));

			}
		}

		HTTPNetworkManager	http_manager = HTTPNetworkManager.getSingleton();

		if ( http_manager.isHTTPListenerEnabled()){

			protocols.add(
					new NetworkAdminProtocolImpl(
							core,
							NetworkAdminProtocol.PT_HTTP,
							http_manager.getHTTPListeningPortNumber()));
		}

		return((NetworkAdminProtocol[])protocols.toArray( new NetworkAdminProtocol[protocols.size()]));
 	}

	@Override
	public InetAddress
	testProtocol(
		NetworkAdminProtocol	protocol )

		throws NetworkAdminException
	{
		return( protocol.test( null ));
	}

	@Override
	public boolean
	isSocksActive()
	{
		Proxy proxy = AEProxySelectorFactory.getSelector().getActiveProxy();

		return( proxy != null && proxy.type() == Proxy.Type.SOCKS );
	}

	@Override
	public NetworkAdminSocksProxy
	createSocksProxy(
		String		host,
		int			port,
		String		username,
		String		password )
	{
		return( new NetworkAdminSocksProxyImpl( host, ""+port, username, password ));
	}

	@Override
	public NetworkAdminSocksProxy[]
	getSocksProxies()
	{
		boolean	enable_proxy 	= COConfigurationManager.getBooleanParameter("Enable.Proxy");
	    boolean enable_socks	= COConfigurationManager.getBooleanParameter("Enable.SOCKS");

		List<NetworkAdminSocksProxy>	res = new ArrayList<>();

	    if ( enable_proxy && enable_socks ){
	    	
			String host = System.getProperty( "socksProxyHost", "" ).trim();
			String port = System.getProperty( "socksProxyPort", "" ).trim();
	
			String user 		= System.getProperty("java.net.socks.username", "" ).trim();
			String password 	= System.getProperty("java.net.socks.password", "").trim();
		
			NetworkAdminSocksProxyImpl	p1 = new NetworkAdminSocksProxyImpl( host, port, user, password );
	
			if ( p1.isConfigured()){
	
				res.add( p1 );
			}
	
				// add in the configured one in case not restarted (restart transfers to the above props)
			
	  		host = COConfigurationManager.getStringParameter("Proxy.Host");
	  		port = COConfigurationManager.getStringParameter("Proxy.Port");
	  		
	  		user = COConfigurationManager.getStringParameter("Proxy.Username");
	  		
	  		if ( user.trim().equalsIgnoreCase("<none>")){
				user = "";
			}
	  		
	  		password = COConfigurationManager.getStringParameter("Proxy.Password");
	
			NetworkAdminSocksProxyImpl	p2 = new NetworkAdminSocksProxyImpl( host, port, user, password );
	
			if ( p2.isConfigured()){
	
				if ( !p1.isConfigured() || !p1.sameAs( p2 )){
				
					res.add( p2 );
				}
			}
	    }
	    
		if ( 	COConfigurationManager.getBooleanParameter( "Proxy.Data.Enable" ) &&
				!COConfigurationManager.getBooleanParameter( "Proxy.Data.Same" )){

			for ( int i=1;i<=COConfigurationManager.MAX_DATA_SOCKS_PROXIES;i++){
				
				String suffix = i==1?"":("."+i);
				
				String host 	= COConfigurationManager.getStringParameter( "Proxy.Data.Host" + suffix );
				String port		= COConfigurationManager.getStringParameter( "Proxy.Data.Port" + suffix );
				String user	= COConfigurationManager.getStringParameter( "Proxy.Data.Username" + suffix );
	
				if ( user.trim().equalsIgnoreCase("<none>")){
					user = "";
				}
				String password = COConfigurationManager.getStringParameter( "Proxy.Data.Password" + suffix );
	
				NetworkAdminSocksProxyImpl	pn = new NetworkAdminSocksProxyImpl( host, port, user, password );
	
				if ( pn.isConfigured()){
	
					res.add( pn );
				}
			}
		}

		return(res.toArray(new NetworkAdminSocksProxy[res.size()]));
	}

	@Override
	public NetworkAdminHTTPProxy
	getHTTPProxy()
	{
		NetworkAdminHTTPProxyImpl	res = new NetworkAdminHTTPProxyImpl();

		if ( !res.isConfigured()){

			res	= null;
		}

		return( res );
	}

	@Override
	public NetworkAdminNATDevice[]
	getNATDevices(
			Core core )
	{
		List<NetworkAdminNATDeviceImpl>	devices = new ArrayList<>();

		try{

		    PluginInterface upnp_pi = core.getPluginManager().getPluginInterfaceByClass( UPnPPlugin.class );

		    if ( upnp_pi != null ){

		    	UPnPPlugin upnp = (UPnPPlugin)upnp_pi.getPlugin();

		    	UPnPPluginService[]	services = upnp.getServices();

		    	for ( UPnPPluginService service: services ){

		    		NetworkAdminNATDeviceImpl dev = new NetworkAdminNATDeviceImpl( service );

		    		boolean same = false;

		    		for ( NetworkAdminNATDeviceImpl d: devices ){

		    			if ( d.sameAs( dev )){

		    				same = true;

		    				break;
		    			}
		    		}

		    		if ( !same ){

		    			devices.add( dev );
		    		}
		    	}
		    }
		}catch( Throwable e ){

			Debug.printStackTrace( e );
		}

		return((NetworkAdminNATDevice[])devices.toArray(new NetworkAdminNATDevice[devices.size()]));
	}

	@Override
	public NetworkAdminASN
	getCurrentASN()
	{
		List	asns = COConfigurationManager.getListParameter( "ASN Details", new ArrayList());

		if ( asns.size() > 0 ){

			Map	m = (Map)asns.get(0);

			return( ASNFromMap( m ));
		}

		return( new NetworkAdminASNImpl( true, "", "", "" ));
	}

	protected Map
	ASNToMap(
		NetworkAdminASNImpl	x )
	{
		Map	m = new HashMap();

		byte[]	as	= new byte[0];
		byte[]	asn	= new byte[0];
		byte[]	bgp	= new byte[0];

		try{
			as	= x.getAS().getBytes("UTF-8");
			asn	= x.getASName().getBytes("UTF-8");
			bgp	= x.getBGPPrefix().getBytes("UTF-8");

		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}

		m.put( "as", as );
		m.put( "name", asn );
		m.put( "bgp", bgp );
		m.put( "v", x.isIPv4()?4:6 );
		
		return( m );
	}

	protected NetworkAdminASNImpl
	ASNFromMap(
		Map	m )
	{
		boolean	ipv4	= true;
		String	as		= "";
		String	asn		= "";
		String	bgp		= "";

		try{
			as	= new String((byte[])m.get("as"),"UTF-8");
			asn	= new String((byte[])m.get("name"),"UTF-8");
			bgp	= new String((byte[])m.get("bgp"),"UTF-8");
			
			if ( m.containsKey( "v" )){
				
				ipv4 = ((Number)m.get( "v" )).intValue() == 4;
			}

		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}

		return( new NetworkAdminASNImpl( ipv4, as, asn, bgp ));
	}

	@Override
	public NetworkAdminASN
	lookupCurrentASN(
		InetAddress		address )

		throws NetworkAdminException
	{
		NetworkAdminASN current = getCurrentASN();

		if ( current.matchesCIDR( address )){

			return( current );
		}

		List	asns = COConfigurationManager.getListParameter( "ASN Details", new ArrayList());

		for (int i=0;i<asns.size();i++){

			Map	m = (Map)asns.get(i);

			NetworkAdminASN x = ASNFromMap( m );

			if ( x.matchesCIDR( address )){

				asns.remove(i);

				asns.add( 0, m );

				firePropertyChange( PR_AS );

				return( x );
			}
		}

		if ( asn_ips_checked.contains( address )){

			return( current );
		}

		long now = SystemTime.getCurrentTime();

		if ( now < last_asn_lookup_time || now - last_asn_lookup_time > ASN_MIN_CHECK ){

			last_asn_lookup_time	= now;

			NetworkAdminASNLookupImpl lookup = new NetworkAdminASNLookupImpl( address );

			NetworkAdminASNImpl x = lookup.lookup();

			asn_ips_checked.add( address );

			asns.add( 0, ASNToMap( x ));

			firePropertyChange( PR_AS );

			return( x );
		}

		return( current );
	}

	@Override
	public NetworkAdminASN
	lookupASN(
		InetAddress		address )

		throws NetworkAdminException
	{
		NetworkAdminASN existing = getFromASHistory( address );

		if ( existing != null ){

			return( existing );
		}

		NetworkAdminASNLookupImpl lookup = new NetworkAdminASNLookupImpl( address );

		NetworkAdminASNImpl result = lookup.lookup();

		addToASHistory( result );

		return( result );
	}

	@Override
	public void
	lookupASN(
		final InetAddress					address,
		final NetworkAdminASNListener		listener )
	{
		synchronized( async_asn_history ){

			NetworkAdminASN existing = async_asn_history.get( address );

			if ( existing != null ){

				listener.success( existing );
			}
		}

		int	queue_size = async_asn_dispacher.getQueueSize();

		if ( queue_size >= MAX_ASYNC_ASN_LOOKUPS ){

			listener.failed( new NetworkAdminException( "Too many outstanding lookups" ));

		}else{

			async_asn_dispacher.dispatch(
				new AERunnable()
				{
					@Override
					public void
					runSupport()
					{
						synchronized( async_asn_history ){

							NetworkAdminASN existing = async_asn_history.get( address );

							if ( existing != null ){

								listener.success( existing );

								return;
							}
						}

						try{
							NetworkAdminASNLookupImpl lookup = new NetworkAdminASNLookupImpl( address );

							NetworkAdminASNImpl result = lookup.lookup();

							synchronized( async_asn_history ){

								async_asn_history.put( address, result );
							}

							listener.success( result );

						}catch( NetworkAdminException e ){

							listener.failed( e );

						}catch( Throwable e ){

							listener.failed( new NetworkAdminException( "lookup failed", e ));
						}
					}
				});
		}
	}

	protected void
	addToASHistory(
		NetworkAdminASN	asn )
	{
		synchronized( as_history ){

			boolean	found = false;

			for (int i=0;i<as_history.size();i++){

				 NetworkAdminASN x = (NetworkAdminASN)as_history.get(i);

				 if ( asn.getAS().equals( x.getAS())){

					 found = true;

					 break;
				 }
			}

			if ( !found ){

				as_history.add( asn );

				if ( as_history.size() > 256 ){

					as_history.remove(0);
				}
			}
		}
	}

	protected NetworkAdminASN
	getFromASHistory(
		InetAddress	address )
	{
		synchronized( as_history ){

			for (int i=0;i<as_history.size();i++){

				 NetworkAdminASN x = (NetworkAdminASN)as_history.get(i);

				 if ( x.matchesCIDR( address )){

					 return( x );
				 }
			}
		}

		return( null );
	}

	@Override
	public void
	runInitialChecks(
		Core core )
	{
		ClientInstanceManager i_man = core.getInstanceManager();

		final ClientInstance my_instance = i_man.getMyInstance();

		i_man.addListener(
			new ClientInstanceManagerListener()
			{
				private InetAddress external_address;

				@Override
				public void
				instanceFound(
					ClientInstance instance )
				{
				}

				@Override
				public void
				instanceChanged(
					ClientInstance instance )
				{
					if ( instance == my_instance ){

						InetAddress address = instance.getExternalAddress();

						if ( external_address == null || !external_address.equals( address )){

							external_address = address;

							if ( !address.isLoopbackAddress()){
								
								try{
									lookupCurrentASN( address );
	
								}catch( Throwable e ){
	
									Debug.printStackTrace(e);
								}
							}
						}
					}
				}

				@Override
				public void
				instanceLost(
					ClientInstance instance )
				{
				}

				@Override
				public void
				instanceTracked(
					ClientInstanceTracked instance )
				{
				}
			});

		if ( COConfigurationManager.getBooleanParameter( "Proxy.Check.On.Start" )){

			NetworkAdminSocksProxy[]	socks = getSocksProxies();

			for (int i=0;i<socks.length;i++){

				NetworkAdminSocksProxy	sock = socks[i];

				try{
					sock.getVersionsSupported();

				}catch( Throwable e ){

					Debug.printStackTrace( e );

					Logger.log(
						new LogAlert(
							true,
							LogAlert.AT_WARNING,
							"Socks proxy " + sock.getName() + " check failed: " + Debug.getNestedExceptionMessage( e )));
				}
			}

			NetworkAdminHTTPProxy http_proxy = getHTTPProxy();

			if ( http_proxy != null ){

				try{

					http_proxy.getDetails();

				}catch( Throwable e ){

					Debug.printStackTrace( e );

					Logger.log(
						new LogAlert(
							true,
							LogAlert.AT_WARNING,
							"HTTP proxy " + http_proxy.getName() + " check failed: " + Debug.getNestedExceptionMessage( e )));
				}
			}
		}

		if ( COConfigurationManager.getBooleanParameter( "Check Bind IP On Start" )){

			checkBindAddresses( true );
		}

        NetworkAdminSpeedTestScheduler nast = NetworkAdminSpeedTestSchedulerImpl.getInstance();

        nast.initialise();
    }

	@Override
	public boolean
	canTraceRoute()
	{
		PlatformManager	pm = PlatformManagerFactory.getPlatformManager();

		return( pm.hasCapability( PlatformManagerCapabilities.TraceRouteAvailability ));
	}

	public NetworkAdminNode[]
	getRoute(
		InetAddress						interface_address,
		InetAddress						target,
		final int						max_millis,
		final NetworkAdminRouteListener	listener )

		throws NetworkAdminException
	{
		PlatformManager	pm = PlatformManagerFactory.getPlatformManager();

		if ( !canTraceRoute()){

			throw( new NetworkAdminException( "No trace-route capability on platform" ));
		}

		final List	nodes = new ArrayList();

		try{
			pm.traceRoute(
				interface_address,
				target,
				new PlatformManagerPingCallback()
				{
					private long	start_time = SystemTime.getCurrentTime();

					@Override
					public boolean
					reportNode(
						int				distance,
						InetAddress		address,
						int				millis )
					{
						boolean	timeout	= false;

						if ( max_millis >= 0 ){

							long	now = SystemTime.getCurrentTime();

							if ( now < start_time ){

								start_time = now;
							}

							if ( now - start_time >= max_millis ){

								timeout = true;
							}
						}

						NetworkAdminNode	node = null;

						if ( address != null ){

							node = new networkNode( address, distance, millis );

							nodes.add( node );
						}

						boolean	result;

						if ( listener == null ){

							result = true;

						}else{

							if ( node == null ){

								result = listener.timeout( distance );

							}else{

								result =  listener.foundNode( node, distance, millis );
							}
						}

						return( result && !timeout );
					}
				});
		}catch( PlatformManagerException e ){

			throw( new NetworkAdminException( "trace-route failed", e ));
		}

		return((NetworkAdminNode[])nodes.toArray( new NetworkAdminNode[nodes.size()]));
	}

	@Override
	public boolean
	canPing()
	{
		PlatformManager	pm = PlatformManagerFactory.getPlatformManager();

		return( pm.hasCapability( PlatformManagerCapabilities.PingAvailability ));
	}

	public NetworkAdminNode
	pingTarget(
		InetAddress						interface_address,
		InetAddress						target,
		final int						max_millis,
		final NetworkAdminRouteListener	listener )

		throws NetworkAdminException
	{
		PlatformManager	pm = PlatformManagerFactory.getPlatformManager();

		if ( !canPing()){

			throw( new NetworkAdminException( "No ping capability on platform" ));
		}

		final NetworkAdminNode[] nodes = { null };

		try{
			pm.ping(
				interface_address,
				target,
				new PlatformManagerPingCallback()
				{
					private long	start_time = SystemTime.getCurrentTime();

					@Override
					public boolean
					reportNode(
						int				distance,
						InetAddress		address,
						int				millis )
					{
						boolean	timeout	= false;

						if ( max_millis >= 0 ){

							long	now = SystemTime.getCurrentTime();

							if ( now < start_time ){

								start_time = now;
							}

							if ( now - start_time >= max_millis ){

								timeout = true;
							}
						}

						NetworkAdminNode	node = null;

						if ( address != null ){

							node = new networkNode( address, distance, millis );

							nodes[0] = node;
						}

						boolean	result;

						if ( listener == null ){

							result = false;

						}else{

							if ( node == null ){

								result = listener.timeout( distance );

							}else{

								result =  listener.foundNode( node, distance, millis );
							}
						}

						return( result && !timeout );
					}
				});
		}catch( PlatformManagerException e ){

			throw( new NetworkAdminException( "ping failed", e ));
		}

		return( nodes[0] );
	}


	@Override
	public void
	getRoutes(
		final InetAddress					target,
		final int							max_millis,
		final NetworkAdminRoutesListener	listener )

		throws NetworkAdminException
	{
		final List sems 	= new ArrayList();
		final List traces 	= new ArrayList();

		NetworkAdminNetworkInterface[] interfaces = getInterfaces();

		for (int i=0;i<interfaces.length;i++){

			NetworkAdminNetworkInterface	interf = (NetworkAdminNetworkInterface)interfaces[i];

			NetworkAdminNetworkInterfaceAddress[] addresses = interf.getAddresses();

			for (int j=0;j<addresses.length;j++){

				final NetworkAdminNetworkInterfaceAddress	address = addresses[j];

				InetAddress ia = address.getAddress();

				if ( ia.isLoopbackAddress() || ia instanceof Inet6Address ){

						// ignore

				}else{

					final AESemaphore sem = new AESemaphore( "parallelRouter" );

					final List		trace = new ArrayList();

					sems.add( sem );

					traces.add( trace );

					new AEThread2( "parallelRouter", true )
					{
						@Override
						public void
						run()
						{
							try{
								address.getRoute(
									target,
									30000,
									new NetworkAdminRouteListener()
									{
										@Override
										public boolean
										foundNode(
											NetworkAdminNode 	node,
											int 				distance,
											int 				rtt )
										{
											trace.add( node );

											NetworkAdminNode[]	route = new NetworkAdminNode[trace.size()];

											trace.toArray( route );

											return( listener.foundNode( address, route, distance, rtt) );
										}

										@Override
										public boolean
										timeout(
											int distance )
										{
											NetworkAdminNode[]	route = new NetworkAdminNode[trace.size()];

											trace.toArray( route );

											return( listener.timeout( address, route, distance ));
										}
									});

							}catch( Throwable e ){

								e.printStackTrace();

							}finally{

								sem.release();
							}
						}
					}.start();
				}
			}
		}

		for (int i=0;i<sems.size();i++){

			((AESemaphore)sems.get(i)).reserve();
		}
	}

	@Override
	public void
	pingTargets(
		final InetAddress					target,
		final int							max_millis,
		final NetworkAdminRoutesListener	listener )

		throws NetworkAdminException
	{
		final List sems 	= new ArrayList();
		final List traces 	= new ArrayList();

		NetworkAdminNetworkInterface[] interfaces = getInterfaces();

		for (int i=0;i<interfaces.length;i++){

			NetworkAdminNetworkInterface	interf = (NetworkAdminNetworkInterface)interfaces[i];

			NetworkAdminNetworkInterfaceAddress[] addresses = interf.getAddresses();

			for (int j=0;j<addresses.length;j++){

				final NetworkAdminNetworkInterfaceAddress	address = addresses[j];

				InetAddress ia = address.getAddress();

				if ( ia.isLoopbackAddress() || ia instanceof Inet6Address ){

						// ignore

				}else{

					final AESemaphore sem = new AESemaphore( "parallelPinger" );

					final List		trace = new ArrayList();

					sems.add( sem );

					traces.add( trace );

					new AEThread2( "parallelPinger", true )
					{
						@Override
						public void
						run()
						{
							try{
								address.pingTarget(
									target,
									30000,
									new NetworkAdminRouteListener()
									{
										@Override
										public boolean
										foundNode(
											NetworkAdminNode 	node,
											int 				distance,
											int 				rtt )
										{
											trace.add( node );

											NetworkAdminNode[]	route = new NetworkAdminNode[trace.size()];

											trace.toArray( route );

											return( listener.foundNode( address, route, distance, rtt) );
										}

										@Override
										public boolean
										timeout(
											int distance )
										{
											NetworkAdminNode[]	route = new NetworkAdminNode[trace.size()];

											trace.toArray( route );

											return( listener.timeout( address, route, distance ));
										}
									});

							}catch( Throwable e ){

								e.printStackTrace();

							}finally{

								sem.release();
							}
						}
					}.start();
				}
			}
		}

		for (int i=0;i<sems.size();i++){

			((AESemaphore)sems.get(i)).reserve();
		}
	}

	@Override
	public boolean
	mustBind()
	{
		return( forceBind );
	}

	@Override
	public boolean
	hasMissingForcedBind()
	{
		Object[] status = getBindingStatus();

		return( (Integer)status[0] == BS_ERROR );
	}

	@Override
	public String
	getBindStatus()
	{
		Object[] status = getBindingStatus();

		int	state = (Integer)status[0];

		if ( state == BS_INACTIVE ){

			return( "No binding configured" );

		}else{

			String str = "";

			if ( state == BS_OK ){

				str = "Binding OK";

			}else if ( state == BS_WARNING ){

				str = "Binding warning";

			}else{

				str = "Binding error";
			}

			String status_str = (String)status[1];

			if ( status_str.length() > 0 ){

				str += ": " + status_str;
			}

			return( str );
		}
	}

		// for the icon

	public static final int BS_INACTIVE	= 0;
	public static final int BS_OK		= 1;
	public static final int BS_WARNING	= 2;
	public static final int BS_ERROR	= 3;

	long		bs_last_calc 	= 0;
	private Object[]	bs_last_value 	= null;

	public Object[]
	getBindingStatus()
	{
		long now = SystemTime.getMonotonousTime();

		if ( bs_last_value != null && now - bs_last_calc < 30*1000 ){

			return( bs_last_value );
		}

		String 	bind_ips 	= COConfigurationManager.getStringParameter("Bind IP", "").trim();

		if ( bind_ips.length() == 0 ){

			return( new Object[]{ BS_INACTIVE, "" });
		}

		boolean enforceBind = COConfigurationManager.getBooleanParameter( "Enforce Bind IP" );

		String missing = checkBindAddresses( false );

		InetAddress[] binds = getAllBindAddresses( false );

		List<InetAddress> bindable 		= new ArrayList<>();
		List<InetAddress> unbindable 	= new ArrayList<>();

		for ( InetAddress b: binds ){

			if ( canBind( b )){

				bindable.add( b );

			}else{

				if ( !( b.isLoopbackAddress() || b.isLinkLocalAddress())){

					unbindable.add( b );
				}
			}
		}

		Set<NetworkConnectionBase> connections = NetworkManager.getSingleton().getConnections();

		Map<InetAddress,int[]>	public_lookup_map 		= new HashMap<>();
		Map<String,int[]>		non_public_lookup_map 	= new HashMap<>();
		
		boolean ignore_bind_for_lan_addresses = COConfigurationManager.getBooleanParameter(ConfigKeys.Connection.BCFG_NETWORK_IGNORE_BIND_FOR_LAN );
		
		for ( NetworkConnectionBase connection: connections ){

			TransportBase tb = connection.getTransportBase();

			if ( tb instanceof Transport ){

				Transport transport = (Transport)tb;

				InetSocketAddress notional_address = connection.getEndpoint().getNotionalAddress();

				String ip    = AddressUtils.getHostAddress( notional_address );

				String network	= AENetworkClassifier.categoriseAddress( ip );
				
				if ( network == AENetworkClassifier.AT_PUBLIC ){
					
					TransportStartpoint start = transport.getTransportStartpoint();
	
					if ( start != null ){
	
						InetSocketAddress socket_address = start.getProtocolStartpoint().getAddress();
	
						if ( socket_address != null ){
	
							InetAddress address = socket_address.getAddress();
								
							int[]	counts = public_lookup_map.get( address );
	
							if ( counts == null ){
	
								counts = new int[3];
	
								public_lookup_map.put( address, counts );
							}
	
							if ( connection.isIncoming()){
	
								counts[0]++;
	
							}else{
	
								counts[1]++;
							
								if (	ignore_bind_for_lan_addresses &&
										AddressUtils.isLANLocalAddress( notional_address ) == AddressUtils.LAN_LOCAL_YES && 
										!AddressUtils.isExplicitLANRateLimitAddress(notional_address)){
								
									counts[2]++;
								}
							}
						}
					}
				}else{
					
					int[]	counts = non_public_lookup_map.get( network );
					
					if ( counts == null ){

						counts = new int[2];

						non_public_lookup_map.put( network, counts );
					}

					if ( connection.isIncoming()){

						counts[0]++;

					}else{

						counts[1]++;
					}
				}
			}
		}

		int	status = BS_OK;

		if ( unbindable.size() > 0 || missing != null ){

			status = enforceBind?BS_ERROR:BS_WARNING;
		}

		String	str =
			MessageText.getString(
				"network.admin.binding.state",
				new String[]{ getString( bindable ), MessageText.getString( enforceBind?"GeneralView.yes":"GeneralView.no" ).toLowerCase()});

		if ( unbindable.size() > 0 ){
			str += "\nUnbindable: " + getString( unbindable );
		}

		if ( missing != null ){
			str += "\n" + MessageText.getString( "label.missing" ) + ": " + missing;
		}

		boolean	bad_connections = false;

		if ( public_lookup_map.size() + non_public_lookup_map.size() == 0 ){

			str += "\n" + MessageText.getString( "label.no.connections" );

		}else{
			
			Set<InetAddress>	extra = new HashSet<>();
			
			InetAddress[] asb = additionalServiceBindIPs;
			
			if ( asb != null ){
				
				extra.addAll( Arrays.asList( asb ));
			}

			String con_str = "";

			for ( Map.Entry<InetAddress,int[]> entry: public_lookup_map.entrySet()){

				InetAddress address = entry.getKey();
				int[]		counts	= entry.getValue();

				String s;

				if ( address.isLoopbackAddress()){
					
						// ignore loopback addresses as these are used to black-hole things when
						// the binding is down
					
					continue;
					
				}else{
					
					if ( address.isAnyLocalAddress()){
					
						s = "*";
		
					}else{
	
						s = address.getHostAddress();
					}
	
					if ( !bindable.contains( address )){
						
						// things are OK if
						// 		any incoming connections are via an extra service bind AND
						//		any outgoing connections are to a LAN address
						
						if ( 	counts[1] != counts[2] ||
								( counts[0] != 0 && !extra.contains( address ))){
	
							// Debug.out( "BAD: " + address + "/" + counts[0] + "/" + counts[1] + "/" + counts[2] + " - extra=" + extra );
							
							bad_connections = true;
						}
					}
				}
				
				s += " - " + MessageText.getString( "label.in" ) + "=" + counts[0] + ", " + MessageText.getString( "label.out" ) + "=" + counts[1];

				con_str += (con_str.length()==0?"":"; ") + s.toLowerCase();
			}

			for ( Map.Entry<String,int[]> entry: non_public_lookup_map.entrySet()){

				String		net 	= entry.getKey();
				int[]		counts	= entry.getValue();

				String s = net;

				s += " - " + MessageText.getString( "label.in" ) + "=" + counts[0] + ", " + MessageText.getString( "label.out" ) + "=" + counts[1];

				con_str += (con_str.length()==0?"":"; ") + s.toLowerCase();
			}
			
			str += "\n" + MessageText.getString( "label.connections" ) + ": " + con_str;
		}

		if ( bad_connections ){

			status = enforceBind?BS_ERROR:BS_WARNING;
		}

		bs_last_value 	= new Object[]{ status, str };
		bs_last_calc	= now;

		return( bs_last_value );
	}

	private String
	getString(
		List<InetAddress>		addresses )
	{
		if ( addresses.size() == 0 ){

			return( "<none>" );
		}

		String str = "";

		for ( InetAddress address: addresses ){

			str += (str.length()==0?"":", ") + address.getHostAddress();
		}

		return( str );
	}

	void
	checkConnectionRoutes()
	{
		if ( getAllBindAddresses( false ).length > 0 ){

				// User knows what they're doing surely

			return;
		}

		checkActiveConnections();
		
		checkIPV6Routing();
	}
	
	void
	checkActiveConnections()
	{
		Set<NetworkConnectionBase> connections = NetworkManager.getSingleton().getConnections();

			// check if all outgoing TCP connections are being routed through the same interface

		boolean	found_wildcard 	= false;
		int		tcp_found		= 0;

		Map<InetAddress,Object[]>	lookup_map 	= new HashMap<>();
		Map<String,Object[]>		bind_map 	= new HashMap<>();

		for ( NetworkConnectionBase connection: connections ){

			if ( !connection.isIncoming()){

				TransportBase tb = connection.getTransportBase();

				if ( tb instanceof Transport ){

					Transport transport = (Transport)tb;

					if ( transport.isTCP()){

						TransportStartpoint start = transport.getTransportStartpoint();

						if ( start != null ){

							InetSocketAddress socket_address = start.getProtocolStartpoint().getAddress();

							if ( socket_address != null ){

								tcp_found++;

								InetAddress address = socket_address.getAddress();

								if ( address.isAnyLocalAddress()){

									found_wildcard = true;

								}else{

									Object[] details = lookup_map.get( address );

									if ( details == null ){

										if ( !lookup_map.containsKey( address )){

											details = getInterfaceForAddress( address );

											lookup_map.put( address, details );
										}
									}

									if ( details != null && details[0] instanceof NetworkInterface ){

										NetworkInterface intf = (NetworkInterface)details[0];

										InetAddress intf_address = details.length==1?null:(InetAddress)details[1];

										String key = intf.getName() + "/" + intf_address;

										Object[] entry = bind_map.get( key );

										if ( entry == null ){

											entry = new Object[]{ new int[1], details };

											bind_map.put( key, entry );
										}

										((int[])entry[0])[0]++;
									}
								}
							}
						}
					}
				}
			}
		}

		if ( tcp_found > 8 ){

			if ( found_wildcard && bind_map.size() == 0 ){

					// unfortunately we don't always get access to the locally bound
					// interfaces, so for the case where we have nothing useful look
					// to see if there are more than one interface and if one looks like
					// a vpn. I did try an explicit 'connect with bind' to see if I could
					// work out if just one was routing but unfortunately this didn't
					// work (with open-vpn at least) as it still permits explicit connections
					// through non-vpn interface - grrr!

				// System.out.println( "Everything wildcard" );

				InetAddress[] bindable_addresses = getBindableAddresses( true, true );

				// System.out.println( "Bindable=" + bindable_addresses.length );

				if ( bindable_addresses.length > 1 ){

					Map<String, NetworkInterface> intf_map = new HashMap<>();

					for ( InetAddress address: bindable_addresses ){

						Object[] details = getInterfaceForAddress( address );

						if ( details != null && details[0] instanceof NetworkInterface ){

							NetworkInterface intf = (NetworkInterface)details[0];

							intf_map.put( intf.getName(), intf );
						}
					}

					if ( intf_map.size() > 1 ){

						int	eth_like = 0;

						Map<String,NetworkInterface>	vpn_like = new HashMap<>();

						for ( Map.Entry<String,NetworkInterface> entry: intf_map.entrySet()){

							int type = categoriseIntf( entry.getValue());

							if ( type == 1 ){

								eth_like++;

							}else if ( type == 2 ){

								vpn_like.put( entry.getKey(), entry.getValue());
							}
						}

						if ( vpn_like.size() == 1 && eth_like > 0 ){

							maybeVPN( vpn_like.values().iterator().next());
						}
					}
				}
			}else if ( !found_wildcard && bind_map.size() == 1 ){

				Object[]	bound_details = (Object[])bind_map.values().iterator().next()[1];

				NetworkInterface bound_intf = (NetworkInterface)bound_details[0];

				// System.out.println( "All outgoing TCP bound to same: " + bound_intf );

				//maybeVPN( bound_intf );

				int bound_type = categoriseIntf( bound_intf );

				if ( bound_type == 2 ){

					if ( !maybeVPNDone( bound_intf )){

						InetAddress[] bindable_addresses = getBindableAddresses( true, true );

						if ( bindable_addresses.length > 1 ){

							int	eth_like	= 0;
							int vpn_like	= 0;

							for ( InetAddress address: bindable_addresses ){

								Object[] details = getInterfaceForAddress( address );

								if ( details != null && details[0] instanceof NetworkInterface ){

									NetworkInterface intf = (NetworkInterface)details[0];

									if ( intf != bound_intf ){

										int type = categoriseIntf( intf);

										if ( type == 1 ){

											eth_like++;

										}else if ( type == 2 ){

											vpn_like++;
										}
									}
								}
							}

							if ( vpn_like == 0 && eth_like > 0 ){

								maybeVPN( bound_intf );
							}
						}
					}
				}
			}
		}
	}

	void
	checkIPV6Routing()
	{
		if (isIPV6Enabled()){
			
			return;
		}
		
		if ( testedIPv6Routing ){
			
			return;
		}
		
		if ( COConfigurationManager.getBooleanParameter( "IPV6 Enable Support Auto Done" )){
			
			testedIPv6Routing = true;
			
			return;
		}
		
		try{
			List<InetAddress> addresses = new ArrayList<>();
			
			for ( NetworkInterface ni: old_network_interfaces ){
				
				try{
					for ( InterfaceAddress ia: ni.getInterfaceAddresses()){
						
						InetAddress a = ia.getAddress();
											
						if ( AddressUtils.isGlobalAddressV6( a ) && !AddressUtils.isTeredo( a ) && !AddressUtils.is6to4( a )){
							
							addresses.add( a );
						}	
					}
				}catch( Throwable e ){
					// ignore potential NPE in getInterfaceAddresses 
				}
			}
			
			if ( !addresses.isEmpty()){
				
				testedIPv6Routing = true;
				
				AEThread2.createAndStartDaemon( 
					"IPv6RouteTest", ()->{
						for ( InetAddress address: addresses ){
							
							if ( canConnectWithBind( address, 30*1000 )){
								
								COConfigurationManager.setParameter( "IPV6 Enable Support Auto Done", true );
								
								COConfigurationManager.setParameter( "IPV6 Enable Support", true );
								
								Logger.log(
										new LogAlert(
											true,
											LogAlert.AT_INFORMATION,
											MessageText.getString( "network.admin.ipv6.auto.enabled", new String[]{ address.toString() })));
								break;
							}
						}
					});
			}
		}catch( Throwable e ){
			
			Debug.out( e );
		}
	}
	
	void
	clearMaybeVPNs()
	{
		Set<String> keys = COConfigurationManager.getDefinedParameters();

		for ( String key: keys ){

			if ( key.startsWith( "network.admin.maybe.vpn.done." )){

				COConfigurationManager.removeParameter( key );
			}
		}
	}

	private boolean
	maybeVPNDone(
		NetworkInterface		intf )
	{
		if ( COConfigurationManager.getBooleanParameter( "network.admin.maybe.vpn.enable" )){

			return(	COConfigurationManager.getBooleanParameter( "network.admin.maybe.vpn.done." + getConfigKey( intf ), false ));

		}else{

			return( true );
		}
	}

	private void
	maybeVPN(
		final NetworkInterface		intf )
	{
		final UIManager ui_manager = StaticUtilities.getUIManager( 5*1000 );

		if ( ui_manager == null ){

			return;
		}

			// might have done in the meantime

		if ( maybeVPNDone( intf )){

			return;
		}

		COConfigurationManager.setParameter( "network.admin.maybe.vpn.done." + getConfigKey( intf ), true );

		new AEThread2( "NetworkAdmin:vpn?" )
		{
			@Override
			public void
			run()
			{
				String msg_details = MessageText.getString(
						"network.admin.maybe.vpn.msg",
						new String[]{ intf.getName() + " - " + intf.getDisplayName() });

				long res = ui_manager.showMessageBox(
							"network.admin.maybe.vpn.title",
							"!" + msg_details + "!",
							UIManagerEvent.MT_YES | UIManagerEvent.MT_NO_DEFAULT );

				if ( res == UIManagerEvent.MT_YES ){

					COConfigurationManager.setParameter( "User Mode", 2 );
					COConfigurationManager.setParameter( "Bind IP", intf.getName());
					COConfigurationManager.setParameter( "Enforce Bind IP", true );
					COConfigurationManager.setParameter( "Check Bind IP On Start", true );
					COConfigurationManager.save();

					try{
						Set<NetworkConnectionBase> connections = NetworkManager.getSingleton().getConnections();

						Map<InetAddress,Object[]>	lookup_map 	= new HashMap<>();

						for ( NetworkConnectionBase connection: connections ){

							TransportBase tb = connection.getTransportBase();

							if ( tb instanceof Transport ){

								boolean ok = false;

								Transport transport = (Transport)tb;

								if ( transport.isTCP()){

									TransportStartpoint start = transport.getTransportStartpoint();

									if ( start != null ){

										InetSocketAddress socket_address = start.getProtocolStartpoint().getAddress();

										if ( socket_address != null ){

											InetAddress address = socket_address.getAddress();

											Object[] details = lookup_map.get( address );

											if ( details == null ){

												if ( !lookup_map.containsKey( address )){

													details = getInterfaceForAddress( address );

													lookup_map.put( address, details );
												}

												if ( details[0] == intf ){

													ok = true;
												}
											}
										}
									}
								}

								if ( !ok ){

									transport.close( "Explicit bind IP set, disconnecting incompatible connections" );
								}
							}
						}
					}catch( Throwable e ){

						Debug.out( e);
					}

					bs_last_calc = 0;

					ui_manager.showMessageBox(
								"settings.updated.title",
								"settings.updated.msg",
								UIManagerEvent.MT_OK );
				}
			}
		}.start();
	}

	private String
	getConfigKey(
		NetworkInterface	intf )
	{
		try{
			return( Base32.encode( intf.getName().getBytes( "UTF-8" )));

		}catch( Throwable e ){

			Debug.out( e );

			return( "derp" );
		}
	}
	private int
	categoriseIntf(
		NetworkInterface	intf )
	{
		String name = intf.getName().toLowerCase();
		String desc	= intf.getDisplayName().toLowerCase();

		if ( desc.startsWith( "tap-" ) || desc.contains( "vpn" )){

			return( 2 );

		}else if ( name.startsWith( "ppp" )){

			return( 2 );

		}else if ( name.startsWith( "tun" )){

			return( 2 );

		}else if ( name.startsWith( "eth" ) || name.startsWith( "en" )){

			return( 1 );

		}else{

			return( 0 );
		}
	}

	@Override
	public String
	classifyRoute(
		InetAddress					address )
	{
		synchronized( address_history ){

			if ( address_history_update_time == 0 ){

				return( "Initializing" );
			}

			byte[]	address_bytes = address.getAddress();

			AddressHistoryRecord	best_entry	= null;
			int						best_prefix	= 0;

			for ( AddressHistoryRecord entry: address_history.values()){

				InetAddress 	other_address 	= entry.getAddress();
				byte[]			other_bytes 	= other_address.getAddress();

				if ( other_bytes.length == address_bytes.length ){

					int	prefix_len = 0;

					for ( int i=0;i<other_bytes.length;i++){

						byte	b1 = address_bytes[i];
						byte	b2 = other_bytes[i];

						if ( b1 == b2 ){

							prefix_len += 8;

						}else{

							for ( int j=7;j>=1;j--){

								if ( (( b1>>j ) & 0x01 ) == (( b2>>j ) & 0x01 )){

									prefix_len++;

								}else{

									break;
								}
							}

							break;
						}
					}

					if ( prefix_len > best_prefix ){

						best_prefix = prefix_len;

						best_entry	= entry;
					}
				}
			}

			if ( best_entry == null ){

				return( "Unknown" );
			}

			return( best_entry.getName( address_history_update_time ));
		}
	}

	public Object[]
	getInterfaceForAddress(
		InetAddress					address )
	{
		byte[]	address_bytes = address.getAddress();

		Set<NetworkInterface> interfaces = old_network_interfaces;

		if ( interfaces == null ){

			return( null );
		}

		NetworkInterface	best_intf 	= null;
		InetAddress			best_addr	= null;
		int					best_prefix	= 0;

		for ( NetworkInterface intf: interfaces ){

			Enumeration<InetAddress> addresses = intf.getInetAddresses();

			int	num_addresses = 0;

			InetAddress	derp = null;

			while( addresses.hasMoreElements()){

				InetAddress 	other_address 	= addresses.nextElement();
				byte[]			other_bytes 	= other_address.getAddress();

				if ( other_bytes.length == address_bytes.length ){

					num_addresses++;

					int	prefix_len = 0;

					for ( int i=0;i<other_bytes.length;i++){

						byte	b1 = address_bytes[i];
						byte	b2 = other_bytes[i];

						if ( b1 == b2 ){

							prefix_len += 8;

						}else{

							for ( int j=7;j>=1;j--){

								if ( (( b1>>j ) & 0x01 ) == (( b2>>j ) & 0x01 )){

									prefix_len++;

								}else{

									break;
								}
							}

							break;
						}
					}

					if ( prefix_len > best_prefix ){

						best_prefix = prefix_len;

						best_intf 	= intf;

						best_addr	= null;
						derp		= other_address;
					}
				}
			}

			if ( derp != null && num_addresses > 1 ){

				best_addr = derp;
			}
		}

		if ( best_addr != null ){

			return( new Object[]{ best_intf, best_addr });

		}else if ( best_intf != null ){

			return( new Object[]{ best_intf });

		}else{

			return( new Object[]{ address });
		}
	}

	@Override
	public void
	addPropertyChangeListener(
		NetworkAdminPropertyChangeListener	listener )
	{
		listeners.add( listener );
	}

	@Override
	public void
	addAndFirePropertyChangeListener(
		NetworkAdminPropertyChangeListener	listener )
	{
		listeners.add( listener );

		for (int i=0;i<NetworkAdmin.PR_NAMES.length;i++){

			try{
				listener.propertyChanged( PR_NAMES[i] );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	@Override
	public void
	removePropertyChangeListener(
		NetworkAdminPropertyChangeListener	listener )
	{
		listeners.remove( listener );
	}

	@Override
	public void
	generate(
		IndentWriter		writer )
	{
		writer.println( "Network Admin" );

		try{
			writer.indent();

			try{
				writer.println( "Binding Details" );

				writer.indent();

				boolean enforceBind = COConfigurationManager.getBooleanParameter("Enforce Bind IP");

				writer.println( "bind to: " + getString( getAllBindAddresses( false )) + ", enforce=" + enforceBind );

				writer.println( "bindable: " + getString( getBindableAddresses()));

				writer.println( "ipv6_enabled=" + IPv6_enabled );

				writer.println( "ipv4_potential=" + hasIPV4Potential());
				writer.println( "ipv6_potential=" + hasIPV6Potential(false) + "/" + hasIPV6Potential(true));

				try{
					writer.println( "single homed: " + getSingleHomedServiceBindAddress());
				}catch( Throwable e ){
					writer.println( "single homed: none" );
				}

				try{
					writer.println( "single homed (4): " + getSingleHomedServiceBindAddress( IP_PROTOCOL_VERSION_REQUIRE_V4 ));
				}catch( Throwable e ){
					writer.println( "single homed (4): none" );
				}

				try{
					writer.println( "single homed (6): " + getSingleHomedServiceBindAddress( IP_PROTOCOL_VERSION_REQUIRE_V6 ));
				}catch( Throwable e ){
					writer.println( "single homed (6): none" );
				}

				writer.println( "multi homed, nio=false: " + getString( getMultiHomedServiceBindAddresses( false )));
				writer.println( "multi homed, nio=true:  " + getString( getMultiHomedServiceBindAddresses( true )));

			}finally{

				writer.exdent();
			}

			NetworkAdminHTTPProxy	proxy = getHTTPProxy();

			if ( proxy == null ){

				writer.println( "HTTP proxy: none" );

			}else{

				writer.println( "HTTP proxy: " + proxy.getName());

				try{

					NetworkAdminHTTPProxy.Details details = proxy.getDetails();

					writer.println( "    name: " + details.getServerName());
					writer.println( "    resp: " + details.getResponse());
					writer.println( "    auth: " + details.getAuthenticationType());

				}catch( NetworkAdminException e ){

					writer.println( "    failed: " + e.getLocalizedMessage());
				}
			}

			NetworkAdminSocksProxy[]	socks = getSocksProxies();

			if ( socks.length == 0 ){

				writer.println( "Socks proxy: none" );

			}else{

				for (int i=0;i<socks.length;i++){

					NetworkAdminSocksProxy	sock = socks[i];

					writer.println( "Socks proxy: " + sock.getName());

					try{
						String[] versions = sock.getVersionsSupported();

						String	str = "";

						for (int j=0;j<versions.length;j++){

							str += (j==0?"":",") + versions[j];
						}

						writer.println( "   version: " + str );

					}catch( NetworkAdminException e ){

						writer.println( "    failed: " + e.getLocalizedMessage());
					}
				}
			}

			try {
				NetworkAdminNATDevice[]	nat_devices = getNATDevices(CoreFactory.getSingleton());

				writer.println( "NAT Devices: " + nat_devices.length );

				for (int i=0;i<nat_devices.length;i++){

					NetworkAdminNATDevice	device = nat_devices[i];

					writer.println( "    " + device.getName() + ",address=" + device.getAddress().getHostAddress() + ":" + device.getPort() + ",ext=" + device.getExternalAddress());
				}
			}catch (Exception e){

				writer.println( "Nat Devices: Can't get -> " + e.toString());
			}

			writer.println( "Interfaces" );

			writer.println( "   " + getNetworkInterfacesAsString());

		}finally{

			writer.exdent();
		}
	}

	private String
	getString(
		InetAddress[]	addresses )
	{
		String	str = "";

		for ( InetAddress address: addresses ){

			str += (str.length()==0?"":", ") + address.getHostAddress();
		}

		return( str );
	}

	@Override
	public void
	generateDiagnostics(
		final IndentWriter iw )
	{
		String domain = COConfigurationManager.getStringParameter(
			ConfigKeys.Connection.SCFG_CONNECTION_TEST_DOMAIN);

		Set	public_addresses = new HashSet();

		NetworkAdminHTTPProxy	proxy = getHTTPProxy();

		if ( proxy == null ){

			iw.println( "HTTP proxy: none" );

		}else{

			iw.println( "HTTP proxy: " + proxy.getName());

			try{

				NetworkAdminHTTPProxy.Details details = proxy.getDetails();

				iw.println( "    name: " + details.getServerName());
				iw.println( "    resp: " + details.getResponse());
				iw.println( "    auth: " + details.getAuthenticationType());

			}catch( NetworkAdminException e ){

				iw.println( "    failed: " + e.getLocalizedMessage());
			}
		}

		NetworkAdminSocksProxy[]	socks = getSocksProxies();

		if ( socks.length == 0 ){

			iw.println( "Socks proxy: none" );

		}else{

			for (int i=0;i<socks.length;i++){

				NetworkAdminSocksProxy	sock = socks[i];

				iw.println( "Socks proxy: " + sock.getName());

				try{
					String[] versions = sock.getVersionsSupported();

					String	str = "";

					for (int j=0;j<versions.length;j++){

						str += (j==0?"":",") + versions[j];
					}

					iw.println( "   version: " + str );

				}catch( NetworkAdminException e ){

					iw.println( "    failed: " + e.getLocalizedMessage());
				}
			}
		}

		try {
			NetworkAdminNATDevice[]	nat_devices = getNATDevices(CoreFactory.getSingleton());

			iw.println( "NAT Devices: " + nat_devices.length );

			for (int i=0;i<nat_devices.length;i++){

				NetworkAdminNATDevice	device = nat_devices[i];

				iw.println( "    " + device.getName() + ",address=" + device.getAddress().getHostAddress() + ":" + device.getPort() + ",ext=" + device.getExternalAddress());

				public_addresses.add( device.getExternalAddress());
			}
		} catch (Exception e) {
			iw.println( "Nat Devices: Can't get -> " + e.toString());
		}

		iw.println( "Interfaces" );

		NetworkAdminNetworkInterface[] interfaces = getInterfaces();

		if ( FULL_INTF_PROBE ){

			if ( interfaces.length > 0 ){

				if ( interfaces.length > 1 || interfaces[0].getAddresses().length > 1 ){

					for (int i=0;i<interfaces.length;i++){

						networkInterface	interf = (networkInterface)interfaces[i];

						iw.indent();

						try{

							interf.generateDiagnostics( iw, public_addresses );

						}finally{

							iw.exdent();
						}
					}
				}else{

					if ( interfaces[0].getAddresses().length > 0 ){

						networkInterface.networkAddress address = (networkInterface.networkAddress)interfaces[0].getAddresses()[0];

						try{
							NetworkAdminNode[] nodes = address.getRoute( InetAddress.getByName( domain ), 30000, trace_route_listener  );

							for (int i=0;i<nodes.length;i++){

								networkNode	node = (networkNode)nodes[i];

								iw.println( node.getString());
							}
						}catch( Throwable e ){

							iw.println( "Can't resolve host for route trace - " + e.getMessage());
						}
					}
				}
			}
		}else{

			try{
				pingTargets(
					InetAddress.getByName( domain ),
					30000,
					new NetworkAdminRoutesListener()
					{
						private int	timeouts = 0;

						@Override
						public boolean
						foundNode(
							NetworkAdminNetworkInterfaceAddress		intf,
							NetworkAdminNode[]						route,
							int										distance,
							int										rtt )
						{
							iw.println( intf.getAddress().getHostAddress() + ": " + route[route.length-1].getAddress().getHostAddress() + " (" + distance + ")" );

							return( false );
						}

						@Override
						public boolean
						timeout(
							NetworkAdminNetworkInterfaceAddress		intf,
							NetworkAdminNode[]						route,
							int										distance )
						{
							iw.println( intf.getAddress().getHostAddress() + ": timeout (dist=" + distance + ")" );

							timeouts++;

							return( timeouts < 3 );
						}
					});

			}catch( Throwable e ){

				iw.println( "getRoutes failed: " + Debug.getNestedExceptionMessage( e ));
			}
		}

		iw.println( "Inbound protocols: default routing" );


		if (CoreFactory.isCoreRunning()) {
			Core core = CoreFactory.getSingleton();

			NetworkAdminProtocol[]	protocols = getInboundProtocols(core);

  		for (int i=0;i<protocols.length;i++){

  			NetworkAdminProtocol	protocol = protocols[i];

  			try{
  				InetAddress	ext_addr = testProtocol( protocol );

  				if ( ext_addr != null ){

  					public_addresses.add( ext_addr );
  				}

  				iw.println( "    " + protocol.getName() + " - " + ext_addr );

  			}catch( NetworkAdminException e ){

  				iw.println( "    " + protocol.getName() + " - " + Debug.getNestedExceptionMessage(e));
  			}
  		}

  		iw.println( "Outbound protocols: default routing" );

  		protocols = getOutboundProtocols(core);

  		for (int i=0;i<protocols.length;i++){

  			NetworkAdminProtocol	protocol = protocols[i];

  			try{

  				InetAddress	ext_addr = testProtocol( protocol );

  				if ( ext_addr != null ){

  					public_addresses.add( ext_addr );
  				}

  				iw.println( "    " + protocol.getName() + " - " + ext_addr );

  			}catch( NetworkAdminException e ){

  				iw.println( "    " + protocol.getName() + " - " + Debug.getNestedExceptionMessage(e));
  			}
  		}
		}

		Iterator	it = public_addresses.iterator();

		iw.println( "Public Addresses" );

		while( it.hasNext()){

			InetAddress	pub_address = (InetAddress)it.next();

			try{
				NetworkAdminASN	res = lookupCurrentASN( pub_address );

				iw.println( "    " + pub_address.getHostAddress() + " -> " + res.getAS() + "/" + res.getASName());

			}catch( Throwable e ){

				iw.println( "    " + pub_address.getHostAddress() + " -> " + Debug.getNestedExceptionMessage(e));
			}
		}
	}

	protected class
	networkInterface
		implements NetworkAdminNetworkInterface
	{
		private final NetworkInterface		ni;

		protected
		networkInterface(
			NetworkInterface	_ni )
		{
			ni	= _ni;
		}

		@Override
		public String
		getDisplayName()
		{
			return( ni.getDisplayName());
		}

		@Override
		public String
		getName()
		{
			return( ni.getName());
		}

		@Override
		public NetworkAdminNetworkInterfaceAddress[]
		getAddresses()
		{
				// BAH NetworkInterface has lots of goodies but is 1.6

			Enumeration	e = ni.getInetAddresses();

			List	addresses = new ArrayList();

			while( e.hasMoreElements()){

				InetAddress address = (InetAddress)e.nextElement();

				if ((address instanceof Inet6Address) && !IPv6_enabled ){

					continue;
				}

				addresses.add( new networkAddress(address));
			}

			return((NetworkAdminNetworkInterfaceAddress[])addresses.toArray( new NetworkAdminNetworkInterfaceAddress[addresses.size()]));
		}

		@Override
		public String
		getString()
		{
			String	str = getDisplayName() + "/" + getName() + " [";

			NetworkAdminNetworkInterfaceAddress[] addresses = getAddresses();

			for (int i=0;i<addresses.length;i++){

				networkAddress	addr = (networkAddress)addresses[i];

				str += (i==0?"":",") + addr.getAddress().getHostAddress();
			}

			return( str + "]" );
		}

		public void
		generateDiagnostics(
			IndentWriter 	iw,
			Set				public_addresses )
		{
			iw.println( getDisplayName() + "/" + getName());

			NetworkAdminNetworkInterfaceAddress[] addresses = getAddresses();

			for (int i=0;i<addresses.length;i++){

				networkAddress	addr = (networkAddress)addresses[i];

				iw.indent();

				try{

					addr.generateDiagnostics( iw, public_addresses );

				}finally{

					iw.exdent();
				}
			}
		}


		protected class
		networkAddress
			implements NetworkAdminNetworkInterfaceAddress
		{
			private final InetAddress		address;

			protected
			networkAddress(

				InetAddress	_address )
			{
				address = _address;
			}

			@Override
			public NetworkAdminNetworkInterface
			getInterface()
			{
				return( networkInterface.this );
			}

			@Override
			public InetAddress
			getAddress()
			{
				return( address );
			}

			@Override
			public boolean
			isLoopback()
			{
				return( address.isLoopbackAddress());
			}

			@Override
			public NetworkAdminNode[]
			getRoute(
				InetAddress						target,
				final int						max_millis,
				final NetworkAdminRouteListener	listener )

				throws NetworkAdminException
			{
				return( NetworkAdminImpl.this.getRoute( address, target, max_millis, listener));
			}

			@Override
			public NetworkAdminNode
			pingTarget(
				InetAddress						target,
				final int						max_millis,
				final NetworkAdminRouteListener	listener )

				throws NetworkAdminException
			{
				return( NetworkAdminImpl.this.pingTarget( address, target, max_millis, listener));
			}

			@Override
			public InetAddress
			testProtocol(
				NetworkAdminProtocol	protocol )

				throws NetworkAdminException
			{
				return( protocol.test( this ));
			}

			public void
			generateDiagnostics(
				IndentWriter 	iw,
				Set				public_addresses )
			{
				iw.println( "" + getAddress());

				try{
					iw.println( "  Trace route" );

					iw.indent();

					if ( isLoopback()){

						iw.println( "Loopback - ignoring" );

					}else{

						try{
							String domain = COConfigurationManager.getStringParameter(
								ConfigKeys.Connection.SCFG_CONNECTION_TEST_DOMAIN);
							NetworkAdminNode[] nodes = getRoute( InetAddress.getByName( domain ), 30000, trace_route_listener );

							for (int i=0;i<nodes.length;i++){

								networkNode	node = (networkNode)nodes[i];

								iw.println( node.getString());
							}
						}catch( Throwable e ){

							iw.println( "Can't resolve host for route trace - " + e.getMessage());
						}

						iw.println( "Outbound protocols: bound" );

						Core core = CoreFactory.getSingleton();

						NetworkAdminProtocol[]	protocols = getOutboundProtocols(core);

						for (int i=0;i<protocols.length;i++){

							NetworkAdminProtocol	protocol = protocols[i];

							try{
								InetAddress	res = testProtocol( protocol );

								if ( res != null ){

									public_addresses.add( res );
								}

								iw.println( "    " + protocol.getName() + " - " + res );

							}catch( NetworkAdminException e ){

								iw.println( "    " + protocol.getName() + " - " + Debug.getNestedExceptionMessage(e));
							}
						}

						iw.println( "Inbound protocols: bound" );

						protocols = getInboundProtocols(core);

						for (int i=0;i<protocols.length;i++){

							NetworkAdminProtocol	protocol = protocols[i];

							try{
								InetAddress	res = testProtocol( protocol );

								if ( res != null ){

									public_addresses.add( res );
								}

								iw.println( "    " + protocol.getName() + " - " + res );

							}catch( NetworkAdminException e ){

								iw.println( "    " + protocol.getName() + " - " + Debug.getNestedExceptionMessage(e));
							}
						}
					}
				}finally{

					iw.exdent();
				}
			}
		}
	}

	protected static class
	networkNode
		implements NetworkAdminNode
	{
		private final InetAddress	address;
		private final int			distance;
		private final int			rtt;

		protected
		networkNode(
			InetAddress		_address,
			int				_distance,
			int				_millis )
		{
			address		= _address;
			distance	= _distance;
			rtt			= _millis;
		}

		@Override
		public InetAddress
		getAddress()
		{
			return( address );
		}

		@Override
		public boolean
		isLocalAddress()
		{
			return( address.isLinkLocalAddress() ||	address.isSiteLocalAddress());
		}

		@Override
		public int
		getDistance()
		{
			return( distance );
		}

		@Override
		public int
		getRTT()
		{
			return( rtt );
		}

		protected String
		getString()
		{
			if ( address == null ){

				return( "" + distance );

			}else{

				return( distance + "," + address + "[local=" + isLocalAddress() + "]," + rtt );
			}
		}
	}

	protected void
	generateDiagnostics(
		IndentWriter			iw,
		NetworkAdminProtocol[]	protocols )
	{
		for (int i=0;i<protocols.length;i++){

			NetworkAdminProtocol	protocol = protocols[i];

			iw.println( "Testing " + protocol.getName());

			try{
				InetAddress	ext_addr = testProtocol( protocol );

				iw.println( "    -> OK, public address=" + ext_addr );

			}catch( NetworkAdminException e ){

				iw.println( "    -> Failed: " + Debug.getNestedExceptionMessage(e));
			}
		}
	}

	@Override
	public void
	logNATStatus(
		IndentWriter		iw )
	{
		if (CoreFactory.isCoreRunning()) {
			generateDiagnostics( iw, getInboundProtocols(CoreFactory.getSingleton()));
		}
	}

	private static class
	AddressHistoryRecord
	{
		private final String					ni_name;
		private final boolean					ni_has_multiple_addresses;
		private final InetAddress				address;
		private long					last_seen;

		AddressHistoryRecord(
			NetworkInterface		_ni,
			InetAddress				_a,
			long					_now )
		{
			ni_name		= _ni.getName();
			address		= _a;
			last_seen	= _now;

			Enumeration<InetAddress> addresses = _ni.getInetAddresses();

			int hits = 0;

			int len = address.getAddress().length;

			while( addresses.hasMoreElements()){

				if ( addresses.nextElement().getAddress().length == len ){

					hits++;
				}
			}

			ni_has_multiple_addresses = hits > 1;
		}

		void
		setLastSeen(
			long		t )
		{
			last_seen	= t;
		}

		long
		getLastSeen()
		{
			return( last_seen );
		}

		InetAddress
		getAddress()
		{
			return( address );
		}

		String
		getName(
			long	last_update )
		{
			String result = ni_name;

			if ( ni_has_multiple_addresses ){

				result += "/" + address.getHostAddress();
			}

			if ( last_update > last_seen ){

				result += " (disconnected)";
			}

			return( result );
		}
	}

	public static void
	main(
		String[]	args )
	{
		boolean	TEST_SOCKS_PROXY 	= false;
		boolean	TEST_HTTP_PROXY		= false;

		try{
			if ( TEST_SOCKS_PROXY ){

				AESocksProxy proxy = AESocksProxyFactory.create( 4567, 10000, 10000 );

				proxy.setAllowExternalConnections( true );

				System.setProperty( "socksProxyHost", "localhost" );
				System.setProperty( "socksProxyPort", "4567" );
			}

			if ( TEST_HTTP_PROXY ){

				System.setProperty("http.proxyHost", "localhost" );
			    System.setProperty("http.proxyPort", "3128" );
			    System.setProperty("https.proxyHost", "localhost" );
			    System.setProperty("https.proxyPort", "3128" );

				Authenticator.setDefault(
						new Authenticator()
						{
							@Override
							protected PasswordAuthentication
							getPasswordAuthentication()
							{
								return( new PasswordAuthentication( "fred", "bill".toCharArray()));
							}
						});

			}

			IndentWriter iw = new IndentWriter( new PrintWriter( System.out ));

			iw.setForce( true );

			COConfigurationManager.initialise();

			CoreFactory.create();

			NetworkAdmin admin = getSingleton();

			//admin.logNATStatus( iw );
			admin.generateDiagnostics( iw );

		}catch( Throwable e){

			e.printStackTrace();
		}
	}
}
