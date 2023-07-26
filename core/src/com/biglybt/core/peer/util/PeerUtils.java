/*
 * Created on Mar 20, 2004
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
package com.biglybt.core.peer.util;

import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.networkmanager.NetworkConnectionBase;
import com.biglybt.core.networkmanager.Transport;
import com.biglybt.core.networkmanager.TransportBase;
import com.biglybt.core.networkmanager.TransportEndpoint;
import com.biglybt.core.networkmanager.TransportStartpoint;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.networkmanager.admin.NetworkAdminASN;
import com.biglybt.core.networkmanager.admin.NetworkAdminASNListener;
import com.biglybt.core.networkmanager.admin.NetworkAdminException;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.util.*;
import com.biglybt.pif.peers.Peer;
import com.biglybt.pif.utils.LocationProvider;
import com.biglybt.pifimpl.local.PluginCoreUtils;


/**
 * Varies peer connection utility methods.
 */
public class PeerUtils {

	public static final String	CC_UNKNOWN = "??";
	
   private static final String	CONFIG_MAX_CONN_PER_TORRENT	= "Max.Peer.Connections.Per.Torrent";
   private static final String	CONFIG_MAX_CONN_TOTAL		= "Max.Peer.Connections.Total";

   public static int MAX_CONNECTIONS_PER_TORRENT;
   public static int MAX_CONNECTIONS_TOTAL;

   static{

   	COConfigurationManager.addParameterListener(
   		CONFIG_MAX_CONN_PER_TORRENT,
   		new ParameterListener()
		{
   			@Override
		    public void
			parameterChanged(
				String parameterName )
   			{
         MAX_CONNECTIONS_PER_TORRENT = COConfigurationManager.getIntParameter(CONFIG_MAX_CONN_PER_TORRENT);
   			}
		});

   	MAX_CONNECTIONS_PER_TORRENT = COConfigurationManager.getIntParameter(CONFIG_MAX_CONN_PER_TORRENT);

  	COConfigurationManager.addParameterListener(
  			CONFIG_MAX_CONN_TOTAL,
  	   		new ParameterListener()
  			{
  	   			@Override
			      public void
  				parameterChanged(
  					String parameterName )
  	   			{
             MAX_CONNECTIONS_TOTAL = COConfigurationManager.getIntParameter(CONFIG_MAX_CONN_TOTAL);
  	   			}
  			});

  	MAX_CONNECTIONS_TOTAL = COConfigurationManager.getIntParameter(CONFIG_MAX_CONN_TOTAL);
   }

   private static final NetworkAdmin	network_admin;

   private static volatile long		na_last_ip4_time;
   private static volatile long		na_last_ip6_time;
   private static volatile byte[]	na_last_ip4;
   private static volatile byte[]	na_last_ip6;

   private static int	na_tcp_port;

   static{
	   NetworkAdmin temp = null;

	   try{
		   temp = NetworkAdmin.getSingleton();

	   }catch( Throwable e ){
	   }

	   network_admin = temp;

	   COConfigurationManager.addAndFireParameterListener(
			 "TCP.Listen.Port",
			 new ParameterListener()
			 {
				@Override
				public void
				parameterChanged(
					String parameterName )
				{
					na_tcp_port = COConfigurationManager.getIntParameter( parameterName );
				}
			});
   }

   public static int
   getPeerPriority(
	   String 	address,
	   int		port )
   {
	   if ( network_admin == null ){

		   return( 0 );
	   }

	   try{

		   InetAddress ia = HostNameToIPResolver.syncResolve( address );

		   if ( ia != null ){

			   return( getPeerPriority( ia, port ));
		   }

	   }catch( Throwable e ){
	   }

	   return( 0 );
   }



   public static int
   getPeerPriority(
		InetAddress		address,
		int				peer_port )
   {
	  return( getPeerPriority( address.getAddress(), peer_port ));
   }

   public static int
   getPeerPriority(
		byte[]		peer_address,
		short		peer_port )
   {
	   return( getPeerPriority( peer_address, ((int)peer_port)&0xffff ));
   }

   public static int
   getPeerPriority(
		byte[]		peer_address,
		int			peer_port )
   {
	   if ( network_admin == null ){

		   return( 0 );
	   }

	   if ( peer_address == null ){

		   return( 0 );
	   }

	   byte[] my_address = null;

	   long now = SystemTime.getMonotonousTime();

	   if ( peer_address.length == 4 ){

		   if ( na_last_ip4 != null && now - na_last_ip4_time < 120*1000 ){

			   my_address = na_last_ip4;

		   }else{

			   if ( na_last_ip4_time == 0 || now - na_last_ip4_time > 10*1000 ){

				   na_last_ip4_time = now;

				   InetAddress ia = network_admin.getDefaultPublicAddress( true );

				   if ( ia != null ){

					   byte[] iab = ia.getAddress();

					   if ( iab != null ){

						   na_last_ip4 = my_address = ia.getAddress();
					   }
				   }
			   }
		   }

		   if ( my_address == null ){

			   my_address = na_last_ip4;
		   }
	   }else if ( peer_address.length == 16 ){

		   if ( na_last_ip6 != null && now - na_last_ip6_time < 120*1000 ){

			   my_address = na_last_ip6;

		   }else{

			   if ( na_last_ip6_time == 0 || now - na_last_ip6_time > 10*1000 ){

				   na_last_ip6_time = now;

				   InetAddress ia = network_admin.getDefaultPublicAddressV6();

				   if ( ia != null ){

					   byte[] iab = ia.getAddress();

					   if ( iab != null ){

						   na_last_ip6 = my_address = ia.getAddress();
					   }
				   }
			   }
		   }

		   if ( my_address == null ){

			   my_address = na_last_ip6;
		   }
	   }else{

		   return( 0 );
	   }

	   if ( my_address != null && my_address.length == peer_address.length ){

		   return( getPeerPriority( my_address, na_tcp_port, peer_address, peer_port ));

	   }else{

		   return( 0 );
	   }
   }

   private static int
   getPeerPriority(
		 byte[]		a1,
		 int		port1,
		 byte[]		a2,
		 int		port2 )
   {
	   		// http://www.bittorrent.org/beps/bep_0040.html

	   byte[] a1_masked = new byte[a1.length];
	   byte[] a2_masked = new byte[a2.length];

	   /*
	   The formula to be used in prioritizing peers is this:

	   priority = crc32-c(sort(masked_client_ip, masked_peer_ip))
	   If the IP addresses are the same, the port numbers (16-bit integers) should be used instead:

	   priority = crc32-c(sort(client_port, peer_port))
	   For an IPv4 address, the mask to be used should be FF.FF.55.55 unless the IP addresses are in the same /16.
	   In that case, the mask to be used should be FF.FF.FF.55. If the IP addresses are in the same /24, the entire address should be used (mask FF.FF.FF.FF).

	   For an IPv6 address, the mask should be derived in the same way, beginning with FFFF:FFFF:FFFF:5555:5555:5555:5555:5555.
	   If the IP addresses are in the same /48, the mask to be used should be FFFF:FFFF:FFFF:FF55:5555:5555:5555:5555.
	   If the IP addresses are in the same /56, the mask to be used should be FFFF:FFFF:FFFF:FFFF:5555:5555:5555:5555, etc...
	   */

	   int x = a1_masked.length==4?1:5;

	   boolean	same = true;

	   int order = 0;

	   for ( int i=0;i<a1_masked.length;i++){
		   byte	a1_byte = a1[i];
		   byte	a2_byte = a2[i];

		   if ( i < x || same ){
			   a1_masked[i] = a1_byte;
			   a2_masked[i] = a2_byte;
		   }else{
			   a1_masked[i] = (byte)(a1_byte&0x55);
			   a2_masked[i] = (byte)(a2_byte&0x55);
		   }

		   if ( i >= x && same ){
			   same = a1_byte == a2_byte;
		   }

		   if ( order == 0 ){

			   order = (a1_masked[i]&0xff) - (a2_masked[i]&0xff);
		   }
	   }

	   if ( same ){

		   a1_masked = new byte[]{ (byte)(port1>>8), (byte)port1 };
		   a2_masked = new byte[]{ (byte)(port2>>8), (byte)port2 };

		   order = port1 - port2;
	   }

	   CRC32C crc32 = new CRC32C();

	   if ( order < 0 ){

		   crc32.updateWord( a1_masked, true );
		   crc32.updateWord( a2_masked, true );

	   }else{

		   crc32.updateWord( a2_masked, true );
		   crc32.updateWord( a1_masked, true );
	   }

	   long res = crc32.getValue();

	   return( (int)res );
   }

  /**
   * Get the number of new peer connections allowed for the given data item,
   * within the configured per-torrent and global connection limits.
   * @return max number of new connections allowed, or -1 if there is no limit
   */
  public static int
  numNewConnectionsAllowed(
	 PeerIdentityDataID 	data_id,
	 int 					specific_max )
  {
	 // max will have been adjusted based on network selection so we don't actually need to use
	 // network info any further during this calculation

    int curConnPerTorrent = PeerIdentityManager.getIdentityCount( data_id );

    int curConnTotal = PeerIdentityManager.getTotalIdentityCount();

    	// specific max here will default to the global per-torrent default if not explicitly set
    	// so we don't need to consider CONFIG_MAX_CONN_PER_TORRENT seperately

    int	PER_TORRENT_LIMIT = specific_max;

    int perTorrentAllowed = -1;  //default unlimited
    if ( PER_TORRENT_LIMIT != 0 ) {  //if limited
      int allowed = PER_TORRENT_LIMIT - curConnPerTorrent;
      if ( allowed < 0 )  allowed = 0;
      perTorrentAllowed = allowed;
    }

    int totalAllowed = -1;  //default unlimited
    if ( MAX_CONNECTIONS_TOTAL != 0 ) {  //if limited
      int allowed = MAX_CONNECTIONS_TOTAL - curConnTotal;
      if ( allowed < 0 )  allowed = 0;
      totalAllowed = allowed;
    }

    int allowed = -1;  //default unlimited
    if ( perTorrentAllowed > -1 && totalAllowed > -1 ) {  //if both limited
      allowed = Math.min( perTorrentAllowed, totalAllowed );
    }
    else if ( perTorrentAllowed == -1 || totalAllowed == -1 ) {  //if either unlimited
      allowed = Math.max( perTorrentAllowed, totalAllowed );
    }

    return allowed;
  }


	private static final Set<Integer>	ignore_peer_ports	= new HashSet<>();

	static{
		COConfigurationManager.addParameterListener(
				"Ignore.peer.ports",
				new ParameterListener()
				{
					@Override
					public void
					parameterChanged(
						String parameterName )
					{
						readIgnorePeerPorts();
					}
				});

		readIgnorePeerPorts();
	}

	private static void
	readIgnorePeerPorts()
	{
		// XXX Optimize me for ranges!!
		String	str = COConfigurationManager.getStringParameter( "Ignore.peer.ports" ).trim();

		ignore_peer_ports.clear();

		if ( str.length() > 0 ){

			String[] ports = str.split("\\;");
			if (ports != null && ports.length > 0) {
				for (int i = 0; i < ports.length; i++) {
					String port = ports[i];
					int spreadPos = port.indexOf('-');
					if (spreadPos > 0 && spreadPos < port.length() - 1) {
						try {
							int iMin = Integer.parseInt(port.substring(0, spreadPos).trim());
							int iMax = Integer.parseInt(port.substring(spreadPos + 1).trim());

							iMin = Math.max( 0, iMin );
							iMax = Math.min(65535, iMax );

							for (int j = iMin; j <= iMax; j++) {
								ignore_peer_ports.add(Integer.valueOf(j));
							}
						} catch (Throwable e) {
							Debug.out( "Invalid ignore-port entry: " + port );
						}
					} else {
						try{
							ignore_peer_ports.add(Integer.parseInt(port.trim()));
						} catch (Throwable e) {
							Debug.out( "Invalid ignore-port entry: " + port );
						}
					}
				}
			}
		}
	}

	public static boolean
	ignorePeerPort(
		int		port )
	{
		return( ignore_peer_ports.contains( port ));
	}

	static final String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

	public static byte[]
	createPeerID()
	{
		byte[] peerId = new byte[20];

		byte[] version = Constants.VERSION_ID;

		System.arraycopy(version, 0, peerId, 0, 8);

	 	for (int i = 8; i < 20; i++) {
		  int pos = (int) ( Math.random() * chars.length());
		  peerId[i] = (byte)chars.charAt(pos);
		}

		if (Constants.isAndroid) {
			peerId[8] = 'A';
		}

		// System.out.println( "generated new peer id:" + ByteFormatter.nicePrint(peerId));

	 	return( peerId );
	}

	public static byte[]
	createWebSeedPeerID()
	{
		byte[] peerId = new byte[20];

		peerId[0] = '-';
		peerId[1] = 'W';
		peerId[2] = 'S';

		for (int i = 3; i < 20; i++) {
			int pos = (int) ( Math.random() * chars.length());
			peerId[i] = (byte)chars.charAt(pos);
		}

		// System.out.println( "generated new peer id:" + ByteFormatter.nicePrint(peerId));

		return( peerId );
	}


	private static volatile LocationProvider	country_provider;
	private static long							country_provider_last_check;

	private static final Object	country_key 	= new Object();
	private static final Object	net_key 		= new Object();

	private static LocationProvider
	getCountryProvider()
	{
		if ( country_provider != null ){

			if ( country_provider.isDestroyed()){

				country_provider 			= null;
				country_provider_last_check	= 0;
			}
		}

		if ( country_provider == null ){

			long	now = SystemTime.getMonotonousTime();

			if ( country_provider_last_check == 0 || now - country_provider_last_check > 20*1000 ){

				country_provider_last_check = now;

				java.util.List<LocationProvider> providers = CoreFactory.getSingleton().getPluginManager().getDefaultPluginInterface().getUtilities().getLocationProviders();

				for ( LocationProvider provider: providers ){

					if ( 	provider.hasCapabilities(
								LocationProvider.CAP_ISO3166_BY_IP |
								LocationProvider.CAP_COUNTY_BY_IP )){

						country_provider = provider;
					}
				}
			}
		}

		return( country_provider );
	}

	public static String[]
 	getCountryDetails(
 		Peer	peer )
	{
		return( getCountryDetails( PluginCoreUtils.unwrap( peer )));
	}

	public static String[]
	getCountryDetails(
		PEPeer	peer )
	{
		if ( peer == null ){

			return( null );
		}

		String[] details = (String[])peer.getUserData( country_key );

		if ( details == null ){

			LocationProvider lp = getCountryProvider();

			if ( lp != null ){

				try{
					String ip = peer.getIp();

					if ( HostNameToIPResolver.isDNSName( ip )){

						InetAddress peer_address = HostNameToIPResolver.syncResolve( ip );

						String code = lp.getISO3166CodeForIP( peer_address );
						String name = lp.getCountryNameForIP( peer_address, Locale.getDefault());

						if ( code != null && name != null ){

							details = new String[]{ code, name };

						}else{

							details = new String[0];
						}
					}else{

						String cat =  AENetworkClassifier.categoriseAddress( ip );

						if ( cat != AENetworkClassifier.AT_PUBLIC ){

							details = new String[]{ cat, cat };

						}else{

							details = new String[0];
						}
					}

					peer.setUserData( country_key, details );

				}catch( Throwable e ){
				}
			}
		}

		return( details );
	}

	public static String[]
	getCountryDetails(
		InetAddress		address )
	{
		if ( address == null ){

			return( null );
		}

		String[] details = null;

		LocationProvider lp = getCountryProvider();

		if ( lp != null ){

			try{

				String code = lp.getISO3166CodeForIP( address );
				String name = lp.getCountryNameForIP( address, Locale.getDefault());

				if ( code != null && name != null ){

					details = new String[]{ code, name };
				}
			}catch( Throwable e ){
			}
		}

		return( details );
	}

	public static String
	getNetwork(
		PEPeer	peer )
	{
		if ( peer == null ){

			return( null );
		}

		String net = (String)peer.getUserData( net_key );

		if ( net == null ){

			net = AENetworkClassifier.categoriseAddress( peer.getIp());

			peer.setUserData( net_key, net );
		}

		return( net );
	}

	private static final Object	ni_key 		= new Object();
	private static final Object	ni_null 	= new Object();

	private static final Object	ni_address_key 		= new Object();

	public static NetworkInterface
	getLocalNetworkInterface(
		PEPeer	peer )
	{
		if ( network_admin == null ){
			
			return( null );
		}

		NetworkInterface result = null;

		try{	
			Object data = peer.getUserData( ni_key );
				
			if ( data instanceof NetworkInterface ){
				
				result = (NetworkInterface)data;
				
			}else if ( data == ni_null ){
							
			}else{
				
				NetworkConnectionBase con = peer.getNetworkConnection();
				
				if ( con != null ){
					
					TransportBase tb = con.getTransportBase();
					
					if ( tb instanceof Transport ){
		
						Transport transport = (Transport)tb;
		
						TransportStartpoint start = transport.getTransportStartpoint();
		
						if ( start != null ){
		
							InetSocketAddress socket_address = start.getProtocolStartpoint().getAddress();
		
							if ( socket_address != null ){
		
								InetAddress address = socket_address.getAddress();
				
								Object[] details = network_admin.getInterfaceForAddress( address );
								
								Object o = details[0];
								
								if ( o instanceof NetworkInterface ){
									
									result = (NetworkInterface)o;
									
									peer.setUserData( ni_key, result );
									
									peer.setUserData( ni_address_key, address );
									
								}else{
									
									peer.setUserData( ni_key, ni_null );
								}
							}else{
								//System.out.println( "addr null" );
							}
						}else{
							
							if ( !transport.isTCP()){
								
								TransportEndpoint end = transport.getTransportEndpoint();
	
									// if we have a bind address then assume this is being honoured
								
								InetAddress udp_bind = null;
								
								if ( end != null ){
									
									InetSocketAddress socket_address = end.getProtocolEndpoint().getAddress();
									
									if ( socket_address != null ){
	
										int type;
										
										if ( socket_address.getAddress() instanceof Inet4Address ){
											
											type = NetworkAdmin.IP_PROTOCOL_VERSION_REQUIRE_V4;
											
										}else{
											
											type = NetworkAdmin.IP_PROTOCOL_VERSION_REQUIRE_V6;
										}
										
										try{
											udp_bind = network_admin.getSingleHomedServiceBindAddress( type );
											
										}catch( Throwable e ){
											
										}
									}	
								}
								
								if ( udp_bind == null ){
								
									udp_bind = network_admin.getSingleHomedServiceBindAddress();
								}
								
								if ( udp_bind != null && !udp_bind.isAnyLocalAddress()){
									
									Object[] details = network_admin.getInterfaceForAddress( udp_bind );
									
									Object o = details[0];
									
									if ( o instanceof NetworkInterface ){
										
										result = (NetworkInterface)o;
										
										peer.setUserData( ni_key, result );
										
										peer.setUserData( ni_address_key, udp_bind );
										
									}else{
										
										peer.setUserData( ni_key, ni_null );
									}	
								}else{
								
										// no bind address, with UDP the best I think we can do is ask the OS which
										// route it would use to get the the destination - not the same as how the destination
										// reached us if incoming but meh
																	
									if ( end != null ){
										
										InetSocketAddress socket_address = end.getProtocolEndpoint().getAddress();
										
										if ( socket_address != null ){
											
											try{
												DatagramSocket s = new DatagramSocket();
											
												try{
													s.connect( socket_address.getAddress(), 0);
												
													InetAddress local = s.getLocalAddress();
													
													if ( !local.isAnyLocalAddress()){
														
														Object[] details = network_admin.getInterfaceForAddress( local );
														
														Object o = details[0];
														
														if ( o instanceof NetworkInterface ){
															
															result = (NetworkInterface)o;
															
															peer.setUserData( ni_key, result );
															
															peer.setUserData( ni_address_key, local );
															
														}else{
															
															peer.setUserData( ni_key, ni_null );
														}	
													}
												}finally{
													
													s.close();
												}
												
											}catch( Throwable e ){
											
												peer.setUserData( ni_key, ni_null );
											}
										}
									}
								}
							}else{
								//System.out.println( "tb " + tb );
							}
						}
					}else{
						//System.out.println( "tb " + tb );
					}
				}else{
					
					//System.out.println( "con null" );
				}
			}
		}catch( Throwable e ){
			
			Debug.out( e );
		}
		
		return( result );
	}
	
	public static InetAddress
	getLocalAddress(
		PEPeer	peer )
	{
		if ( peer.getUserData( ni_key ) == null ){
			
			getLocalNetworkInterface( peer );
		}
		
		return(InetAddress)peer.getUserData( ni_address_key );
	}
	
	private static final Object	asn_key 		= new Object();
	private static final Object	asn_pending 	= new Object();

	private static final String[]	asn_failed = { "", "" };
	
		/**
		 * 
		 * @param peer
		 * @return null if lookup pending, "" or ASN otherwise
		 */
	
	public static String
	getASN(
		PEPeer		peer )
	{
		String[] result = getASandASN( peer );
		
		if ( result != null ){
			
			return( result[1] );
		}
		
		return( null );
	}
	
		/**
		 * 
		 * @param peer
		 * @return null if pending, String[] of { as, asn } or { "", "" } if unavailable
		 */
	
	public static String[]
	getASandASN(
		PEPeer		peer )
	{
		if ( network_admin == null ){
			
			return( asn_failed );
		}

    	Object o = peer.getUserData( asn_key );

    	if ( o instanceof String[] ){
    		
    		return((String[])o);
    		
    	}else if ( o == asn_pending ){   	
    		
    		return( null );
    		
    	}else{
    	
       		String peer_ip = peer.getIp();

    		if ( AENetworkClassifier.categoriseAddress( peer_ip ) == AENetworkClassifier.AT_PUBLIC ){

        		peer.setUserData( asn_key, asn_pending );

	    		try{
		    		NetworkAdmin.getSingleton().lookupASN(
		    			InetAddress.getByName( peer_ip ),
		    			new NetworkAdminASNListener()
		    			{
		    				@Override
						    public void
		    				success(
		    					NetworkAdminASN		asn )
		    				{
		    					peer.setUserData( asn_key, new String[]{ asn.getAS(), asn.getASName() });
		    				}

		    				@Override
						    public void
		    				failed(
		    					NetworkAdminException	error )
		    				{
		    					peer.setUserData( asn_key, asn_failed );
		    				}
		    			});
		    		
		    		return( null );

		    	}catch( Throwable e ){
		    		
		    		peer.setUserData( asn_key, asn_failed );
		    		
		    		return( asn_failed );
		    	}
    		}else{
    			
    			peer.setUserData( asn_key, asn_failed );
    			
    			return( asn_failed );
    		}
    	}
	}
	
	/*
	public static void
	main(
		String[]	args )
	{

		// If the client is 123.213.32.10 and the peer is 98.76.54.32, the hash that they should both arrive at is crc32-c(624C14007BD50000) or BB97323E.
		// If the client is 123.213.32.10 and the peer is 123.213.32.234, the hash that they should both arrive at is crc32-c[(7BD5200A7BD520EA) or C816B840.


		CRC32C crc = new CRC32C();

		crc.update("The quick brown fox jumps over the lazy dog".getBytes());

		System.out.println( Long.toString( crc.getValue(), 16 ));

		try{
			if ( getPeerPriority( InetAddress.getByName( "123.213.32.10" ).getAddress(), 10,   InetAddress.getByName( "98.76.54.32" ).getAddress(), 10 ) != 0xBB97323E ){

				System.out.println( "derp1" );
			}
			if ( getPeerPriority( InetAddress.getByName( "123.213.32.10" ).getAddress(), 10,   InetAddress.getByName( "123.213.32.234" ).getAddress(), 10 ) != 0xC816B840 ){
				System.out.println( "derp2" );
			}
			if ( getPeerPriority( InetAddress.getByName( "123.213.32.10" ).getAddress(), 10,   InetAddress.getByName( "123.213.32.10" ).getAddress(), 20 ) != 1879809021 ){
				System.out.println( "derp3" );
			}
		}catch( Throwable e ){
			e.printStackTrace();

		}
	}
	*/
}
