/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.core.networkmanager.admin.impl;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import com.biglybt.core.Core;
import com.biglybt.core.dht.DHT;
import com.biglybt.core.dht.transport.DHTTransport;
import com.biglybt.core.dht.transport.DHTTransportContact;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.networkmanager.admin.NetworkAdminNetworkInterface;
import com.biglybt.core.networkmanager.admin.NetworkAdminNetworkInterfaceAddress;
import com.biglybt.core.util.AddressUtils;
import com.biglybt.core.util.ByteFormatter;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.HashWrapper;
import com.biglybt.core.util.RandomUtils;
import com.biglybt.core.util.SimpleTimer;
import com.biglybt.core.util.TimerEvent;
import com.biglybt.net.udp.uc.PRUDPPacketHandlerFactory;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ddb.DistributedDatabase;
import com.biglybt.pif.ddb.DistributedDatabaseContact;
import com.biglybt.plugin.dht.DHTPlugin;
import com.biglybt.plugin.net.netstatus.*;
import com.biglybt.plugin.net.netstatus.NetStatusProtocolTesterBT.Session;

public class 
NetworkAdminDistributedNATTester
{
	private NetworkAdminImpl		admin;
	
	private DistributedDatabase		ddb;
	private DHTPlugin				dht_plugin;
	private NetStatusPlugin			net_status_plugin;
	
	protected
	NetworkAdminDistributedNATTester(
		NetworkAdminImpl		_admin,
		Core					_core )
	{
		admin		= _admin;
		
		PluginInterface dht_pi = _core.getPluginManager().getPluginInterfaceByClass( DHTPlugin.class );

		PluginInterface net_status_pi = _core.getPluginManager().getPluginInterfaceByClass( NetStatusPlugin.class  );
		
		if ( dht_pi != null && net_status_pi != null ){

			dht_plugin = (DHTPlugin)dht_pi.getPlugin();
		
			net_status_plugin = (NetStatusPlugin)net_status_pi.getPlugin();

			ddb = dht_pi.getDistributedDatabase();
			
			// SimpleTimer.addPeriodicEvent( "NetworkAdminDistributedNATTester", 30*1000, this::runChecks );
		}
	}
	
	private void
	runChecks(
		TimerEvent	ev )
	{
 		NetworkAdminNetworkInterface[] interfaces = admin.getInterfaces();

 		for ( NetworkAdminNetworkInterface intf: interfaces ){

  			NetworkAdminNetworkInterfaceAddress[] addresses = intf.getAddresses();

  			for ( NetworkAdminNetworkInterfaceAddress address: addresses ){
  				
  				InetAddress a = address.getAddress();
  				
  				if ( AddressUtils.isGlobalAddressV6( a )){
  					
  					test( a );
  				}
  			}
 		}
	}
	
	private void
	test(
		InetAddress		address )
	{
		DHT[] dhts = dht_plugin.getDHTs();
		
		DHT	target_dht = null;
		
		int target_network;
		
		if ( address instanceof Inet6Address ){
			
			target_network = DHT.NW_AZ_MAIN_V6;
			
		}else{
			
			target_network = Constants.isCVSVersion()?DHT.NW_AZ_CVS:DHT.NW_AZ_MAIN;
		}

		for (int i=0;i<dhts.length;i++){

			if ( dhts[i].getTransport().getNetwork() == target_network ){

				target_dht = dhts[i];

				break;
			}
		}
		
		if ( target_dht != null ){
			
			DHTTransport transport = target_dht.getTransport();
			
			InetAddress bind_address = transport.getCurrentBindAddress();

			if ( !bind_address.isAnyLocalAddress()){
				
				System.out.println( "Already bound, ignoring" );
			
			}else{
				
				DHTTransportContact[] contacts = target_dht.getTransport().getReachableContacts();
				
				if ( contacts.length > 0 ){
				
					System.out.println( "Testing " + address );
					
					NetStatusProtocolTester pt = net_status_plugin.getProtocolTester();
					
					NetStatusProtocolTesterBT tester = 
						pt.createTester(
							new NetStatusProtocolTesterListener()
							{
								public void
								sessionAdded(
									NetStatusProtocolTesterBT.Session	session )
								{
									System.out.println( "Added, initiator=" + session.isInitiator());
								}
		
								public void
								complete(
									NetStatusProtocolTesterBT			tester )
								{
									System.out.println( "Complete" );
								}
		
								public void
								log(
									String		str,
									boolean		is_detailed )
								{
									System.out.println( str );
								}
		
								public void
								logError(
									String		str )
								{
									System.out.println( str );
								}
		
								public void
								logError(
									String		str,
									Throwable	e )
								{
									System.out.println( str );
								}
							});
					
					tester.setBindIP(  address );
					
					transport.setExplicitBindAddress( address );
					
					try{
						int	tried = 0;
						
						for ( DHTTransportContact contact: contacts ){
							
							tried++;
							
							if ( tried > 10 ){
								
								break;
							}
	
							DistributedDatabaseContact ddb_contact = ddb.importContact( contact.getAddress());
							
							if ( pt.tryTest( tester, ddb_contact )){
				
								Thread.sleep( 10000 );
								
								break;
							}
						}				
						
					}catch( Throwable e ){
						
					}finally{
				
						tester.destroy();
						
						transport.setExplicitBindAddress( null );

					}
				}
			}
		}
	}
}
