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

import static com.biglybt.core.config.ConfigKeys.Connection.BCFG_IPV_6_CHECK_MULTIPLE_ADDRESS_CHECKS;

import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import com.biglybt.core.Core;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.core.dht.DHT;
import com.biglybt.core.dht.transport.DHTTransport;
import com.biglybt.core.dht.transport.DHTTransportContact;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.networkmanager.admin.NetworkAdminNetworkInterface;
import com.biglybt.core.networkmanager.admin.NetworkAdminNetworkInterfaceAddress;
import com.biglybt.core.util.AEThread2;
import com.biglybt.core.util.AddressUtils;
import com.biglybt.core.util.SimpleTimer;
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.TimerEvent;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ddb.DistributedDatabase;
import com.biglybt.pif.ddb.DistributedDatabaseContact;
import com.biglybt.plugin.dht.DHTPlugin;
import com.biglybt.plugin.net.netstatus.*;


public class 
NetworkAdminDistributedNATTester
{
	private static final boolean 	DEBUG 	= false;
	
	private static final int		CONTACTS_TO_TEST	= 8;
	
	private static final long		CHECK_PERIOD		= 5*60*1000;
	private static final long		NOBIND_CHECK_PERIOD	= 60*60*1000;
	private static final long		BIND_CHECK_PERIOD	= 3*60*60*1000;
	
	
	private NetworkAdminImpl		admin;
	
	private DistributedDatabase		ddb;
	private DHTPlugin				dht_plugin;
	private NetStatusPlugin			net_status_plugin;
	
	private boolean	enabled;
	
	private boolean	check_running = false;
	
	private volatile long	last_nobind_check;
	private volatile long	last_bind_check;
	private volatile String	last_alert = "";
	
	protected
	NetworkAdminDistributedNATTester(
		NetworkAdminImpl		_admin,
		Core					_core )
	{
		admin		= _admin;
		
		PluginInterface dht_pi = _core.getPluginManager().getPluginInterfaceByClass( DHTPlugin.class );

		PluginInterface net_status_pi = _core.getPluginManager().getPluginInterfaceByClass( NetStatusPlugin.class  );
		
		if ( dht_pi != null && net_status_pi != null ){

			AEThread2.createAndStartDaemon(
				"NetworkAdminDistributedNATTester",
				()->{
					dht_plugin = (DHTPlugin)dht_pi.getPlugin();
				
					net_status_plugin = (NetStatusPlugin)net_status_pi.getPlugin();
		
					ddb = dht_pi.getDistributedDatabase();
					
					COConfigurationManager.addAndFireParameterListener(
						ConfigKeys.Connection.BCFG_IPV_6_CHECK_MULTIPLE_ADDRESS_CHECKS,
						(name)->{
							enabled = COConfigurationManager.getBooleanParameter( name );
						});
					
					SimpleTimer.addPeriodicEvent( "NetworkAdminDistributedNATTester", CHECK_PERIOD, this::runChecks );
					
					admin.addPropertyChangeListener(
						(property)->{
							if (  property == NetworkAdmin.PR_DEFAULT_BIND_ADDRESS ){
								
								last_nobind_check		= 0;
								last_bind_check			= 0;
							}
						});
				});
		}
	}
	
	private void
	runChecks(
		TimerEvent	ev )
	{
		if ( !enabled ){
			
			return;
		}
		
		synchronized( this ){
			
			if ( check_running ){
				
				return;
			}
			
			check_running = true;
		}
		
		boolean	async = false;
		
		logMessage( "Running checks" );
		
		try{
	 		NetworkAdminNetworkInterface[] interfaces = admin.getInterfaces();
	
	 		List<InetAddress>	global_ipv6 = new ArrayList<>();
	 		
	 		for ( NetworkAdminNetworkInterface intf: interfaces ){
	
	  			NetworkAdminNetworkInterfaceAddress[] addresses = intf.getAddresses();
	
	  			for ( NetworkAdminNetworkInterfaceAddress address: addresses ){
	  				
	  				InetAddress a = address.getAddress();
	  				
	  				if ( AddressUtils.isGlobalAddressV6( a )){
	  					
	  					global_ipv6.add( a );
	  				}
	  			}
	 		}
	 		
	 		if ( global_ipv6.size() > 1 ){
	 			
	 			DHT[] dhts = dht_plugin.getDHTs();
	 			
	 			DHT	target_dht = null;
	 			
	 			for (int i=0;i<dhts.length;i++){
	
	 				if ( dhts[i].getTransport().getNetwork() == DHT.NW_AZ_MAIN_V6 ){
	
	 					target_dht = dhts[i];
	
	 					break;
	 				}
	 			}
	 			
	 			if ( target_dht != null ){
	 				
	 				DHTTransport transport = target_dht.getTransport();
	 				
	 				InetAddress bind_address = transport.getCurrentBindAddress();
	
	 				if ( bind_address == null ){
	 					
	 					logMessage( "DHT bind address invalid, not testing" );
	 					
	 				}else if ( !bind_address.isAnyLocalAddress()){
	 			
	 					logMessage( "DHT already bound, not testing" );
	 					
	 				}else{
	 				
		 				DHT f_target_dht = target_dht;
		 				
		 				AEThread2.createAndStartDaemon(
	 						"DistributedNATCheck",
	 						()->{
	 							try{
	 								
	 								test( f_target_dht, global_ipv6 );
	 								
	 							}finally{
	 								
	 								synchronized( NetworkAdminDistributedNATTester.this ){
	 									
	 									check_running = false;
	 								}
	 							}
	 						});
		 				
		 				async = true;
	 				}
	 			}else{
	 				
	 				logMessage( "No DHT" );
	 			}
	 		}else{
	 			
	 			logMessage( "Insufficient V6 addresses" );
	 		}
		}finally{
			
			if ( !async ){
				
				synchronized( this ){
	
					check_running = false;
				}
			}
		}
	}
	
	private void
	test(
		DHT					dht,
		List<InetAddress>	addresses )
	{
		long	now = SystemTime.getMonotonousTime();
		
		if ( last_nobind_check > 0 && now - last_nobind_check < NOBIND_CHECK_PERIOD ){
			
			return;
		}
		
		int	result = testSupport( dht, null );
		
		logMessage( "No bind result: " + result );
		
		if ( result == 0 ){
			
				// can't test at the moment
			
		}else{
			
			last_nobind_check = now;

			if ( result == 1 ){
						
				// success with current settings, nothing worth doing
			
			}else{
		
					// no incoming, check each explicit bind to see if anything works
				
				if ( last_bind_check > 0 && now - last_bind_check < BIND_CHECK_PERIOD ){
					
					return;
				}
				
				last_bind_check = now;
				
				List<InetAddress>	working 	= new ArrayList<>();
				List<InetAddress>	not_working = new ArrayList<>();
				
				for ( InetAddress a: addresses ){
					
					result = testSupport( dht, a );
					
					logMessage( "Bind to " + a + " result " + result );
		
					if ( result == 1 ){
						
						working.add( a );
						
					}else if ( result == 2 ){
					
						not_working.add( a );
					}
				}
				
				InetAddress dht_address = dht.getTransport().getLocalContact().getAddress().getAddress();
				
				if ( not_working.contains( dht_address ) && !working.isEmpty()){
					
					String dht_str = dht_address.getHostAddress();
					String ok_str = "";
					
					for ( InetAddress a: working ){
						ok_str += (ok_str.isEmpty()?"":", ") + a.getHostAddress();
					}
					
					String alert = dht_str + ok_str;
					
					if ( !last_alert.equals( alert )){
						
						last_alert = alert;
						
						LogAlert la = 
							new LogAlert(
									true,
									LogAlert.AT_WARNING,
									MessageText.getString( "network.admin.multiple.global.ipv6.issue", 
										new String[]{ dht_str, ok_str }));
						
						la.forceNotify = true;
						
						Logger.log( la );
					}
				}
			}
		}
	}
	
	private int
	testSupport(
		DHT				dht,
		InetAddress		bind_address  )
	{			
		DHTTransport transport = dht.getTransport();	
				
		DHTTransportContact[] contacts = dht.getTransport().getReachableContacts();
		
		if ( contacts.length < CONTACTS_TO_TEST ){
			
			return( 0 );	// couldn't test
		}
		
		logMessage( "Testing " + dht + " against " + bind_address );
		
		NetStatusProtocolTester pt = net_status_plugin.getProtocolTester();
		
		AtomicBoolean	got_outgoing = new AtomicBoolean();
		AtomicBoolean	got_incoming = new AtomicBoolean();
		
		NetStatusProtocolTesterBT tester = 
			pt.createTester(
				new NetStatusProtocolTesterListener()
				{
					public void
					sessionAdded(
						NetStatusProtocolTesterBT.Session	session )
					{
						boolean outgoing = session.isInitiator();
						
						logMessage( "Added, initiator=" + outgoing );
						
						if ( outgoing ){
							
							got_outgoing.set( true );
							
						}else{
							
							got_incoming.set( true );
						}
					}

					public void
					complete(
						NetStatusProtocolTesterBT			tester )
					{
						logMessage( "Complete" );
					}

					public void
					log(
						String		str,
						boolean		is_detailed )
					{
						logMessage( str );
					}

					public void
					logError(
						String		str )
					{
						logMessage( str );
					}

					public void
					logError(
						String		str,
						Throwable	e )
					{
						logMessage( str );
					}
				});
		
		if ( bind_address != null ){
			
			tester.setBindIP(  bind_address );
			
			transport.setExplicitBindAddress( bind_address, true );
		}
		
		try{
			int	tried = 0;
			
			for ( DHTTransportContact contact: contacts ){
				
				if ( got_incoming.get()){
					
					break;
				}
				
				tried++;
				
				if ( tried > CONTACTS_TO_TEST ){
					
					break;
				}

				DistributedDatabaseContact ddb_contact = ddb.importContact( contact.getAddress());
				
				if ( pt.tryTest( tester, ddb_contact )){
	
					Thread.sleep( 5000 );
				}
			}				
			
		}catch( Throwable e ){
			
		}finally{
	
			tester.destroy();
			
			if ( bind_address != null ){
			
				transport.setExplicitBindAddress( null, true );
			}
		}
		
		if ( got_incoming.get()){
			
			return( 1 );	// success!
			
		}else if ( got_outgoing.get()){
			
			return( 2 );	// NAT/Firewall issue
			
		}else{
			
			return( 3 );	// bind address goes nowhere
		}
	}
	
	private void
	logMessage(
		String		str )
	{
		if ( DEBUG ){
			
			System.out.println( str );
		}
	}
}
