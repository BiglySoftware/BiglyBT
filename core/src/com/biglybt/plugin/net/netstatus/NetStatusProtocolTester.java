/*
 * Created on Feb 15, 2008
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


package com.biglybt.plugin.net.netstatus;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;

import com.biglybt.core.dht.DHT;
import com.biglybt.core.dht.transport.DHTTransportContact;
import com.biglybt.core.networkmanager.NetworkManager;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.networkmanager.impl.tcp.TCPNetworkManager;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ddb.*;
import com.biglybt.plugin.dht.DHTPlugin;
import com.biglybt.plugin.net.netstatus.NetStatusProtocolTesterBT.Session;


public class
NetStatusProtocolTester
	implements DistributedDatabaseTransferHandler
{
	private static final int REQUEST_HISTORY_MAX	= 64;

	private static final int MAX_ACTIVE_TESTS	= 3;
	private static final int MAX_TEST_TIME		= 120*1000;

	private static final int TEST_TYPE_BT		= 1;

	private static final int	VERSION_INITIAL	= 1;

	private static final int	CURRENT_VERSION	= VERSION_INITIAL;

	private static final int	BT_MAX_SLAVES	= 8;

	private NetStatusPlugin		plugin;
	private PluginInterface		plugin_interface;

	private DistributedDatabase	ddb;

	private DHTPlugin dht_plugin;



	private testXferType transfer_type = new testXferType();

	private Map	request_history =
		new LinkedHashMap(REQUEST_HISTORY_MAX,0.75f,true)
		{
			@Override
			protected boolean
			removeEldestEntry(
		   		Map.Entry eldest)
			{
				return size() > REQUEST_HISTORY_MAX;
			}
		};

	private List active_tests 		= new ArrayList();

	private TimerEventPeriodic	timer_event	= null;

	protected
	NetStatusProtocolTester(
		NetStatusPlugin		_plugin,
		PluginInterface		_plugin_interface )
	{
		plugin				= _plugin;
		plugin_interface	= _plugin_interface;

		try{
			PluginInterface dht_pi = plugin_interface.getPluginManager().getPluginInterfaceByClass( DHTPlugin.class );

			if ( dht_pi != null ){

				dht_plugin = (DHTPlugin)dht_pi.getPlugin();
			}

			ddb = plugin_interface.getDistributedDatabase();

			if ( ddb.isAvailable()){

				ddb.addTransferHandler( transfer_type, this );

				log( "DDB transfer type registered" );

			}else{

				log( "DDB transfer type not registered, DDB unavailable" );
			}
		}catch( Throwable e ){

			log( "DDB transfer type registration failed", e );
		}
	}

	public NetStatusProtocolTesterBT
	runTest(
		final NetStatusProtocolTesterListener		listener )
	{
		return( runTest( "", listener ));
	}

	public NetStatusProtocolTesterBT
	runTest(
		String										test_address,
		final NetStatusProtocolTesterListener		listener )
	{
		NetStatusProtocolTesterBT bt_tester = createTester( listener );

		try{
			if ( test_address.length() == 0 ){

				DHT[]	dhts = dht_plugin.getDHTs();

				DHT	target_dht = null;

				InetAddress bind_ip = NetworkAdmin.getSingleton().getSingleHomedServiceBindAddress();
				
				int	target_network;
				
				if ( bind_ip instanceof Inet6Address && !bind_ip.isAnyLocalAddress()){
					
					target_network = DHT.NW_AZ_MAIN_V6;
					
				}else{
					
					target_network = DHT.NW_AZ_MAIN;
				}

				for (int i=0;i<dhts.length;i++){

					if ( dhts[i].getTransport().getNetwork() == target_network ){

						target_dht = dhts[i];

						break;
					}
				}

				if ( target_dht == null && dhts.length > 0 ){
					
					target_dht = dhts[0];
					
					target_network = target_dht.getTransport().getNetwork();
				}
				
				if ( target_dht == null ){

					listener.logError( "Distributed database unavailable" );

				}else{

					DHTTransportContact[] contacts = target_dht.getTransport().getReachableContacts();

					final List f_contacts = new ArrayList(Arrays.asList(contacts));

					final int[]	ok = new int[]{ 0 };

					final int	num_threads = Math.min( BT_MAX_SLAVES, contacts.length );

					listener.log( "Searching " + contacts.length + " contacts for " + num_threads + " test targets", false );

					final AESemaphore	sem = new AESemaphore( "NetStatusProbe" );
					
					for (int i=0;i<num_threads;i++){

						new AEThread2( "NetStatusProbe", true )
						{
							@Override
							public void
							run()
							{
								try{
									while( !bt_tester.isDestroyed()){

										DHTTransportContact	contact = null;

										synchronized( ok ){

											if ( ok[0] < num_threads && f_contacts.size() > 0 ){

												contact = (DHTTransportContact)f_contacts.remove(0);
											}
										}

										if ( contact == null ){

											break;
										}

										try{
											DistributedDatabaseContact ddb_contact = ddb.importContact( contact.getAddress());
											
											if ( tryTest( bt_tester, ddb_contact )){

												synchronized( ok ){

													ok[0]++;
												}
											}
										}catch( Throwable e ){

											listener.logError( "Contact import for " + contact.getName() + " failed", e );
										}
									}
								}finally{

									sem.release();
								}
							}
						}.start();
					}

					for (int i=0;i<num_threads;i++){

						sem.reserve();
					}

					listener.log( "Searching complete, " + ok[0] + " targets found", false );
				}
			}else{

				String[]	bits = test_address.split( ":" );

				if ( bits.length != 2 ){

					log( "Invalid address - use <host>:<port> " );

					return( bt_tester );
				}

				InetSocketAddress address = new InetSocketAddress( bits[0].trim(), Integer.parseInt( bits[1].trim()));

				DistributedDatabaseContact contact = ddb.importContact( address );

				tryTest( bt_tester, contact );
			}
		}catch( Throwable e ){

			listener.logError( "Test failed", e );

		}finally{

			bt_tester.addListener(
				new NetStatusProtocolTesterListener()
				{
					@Override
					public void
					sessionAdded(
						Session session)
					{
					}

					@Override
					public void
					complete(
						NetStatusProtocolTesterBT tester )
					{
						removeFromActive( tester );
					}

					@Override
					public void
					log(
						String		str,
						boolean		detailed )
					{
					}

					@Override
					public void
					logError(
						String		str )
					{
					}

					@Override
					public void
					logError(
						String		str,
						Throwable	e )
					{
					}
				});

			bt_tester.setOutboundConnectionsComplete();

			new DelayedEvent(
				"NetStatus:killer",
				10*1000,
				new AERunnable()
				{
					@Override
					public void
					runSupport()
					{
						listener.log( "Destroying tester", false );

						bt_tester.destroy();
					}
				});
		}

		return( bt_tester );
	}

	
	public NetStatusProtocolTesterBT
	createTester(
		NetStatusProtocolTesterListener		listener )
	{
		NetStatusProtocolTesterBT bt_tester = new NetStatusProtocolTesterBT( this, true );

		bt_tester.addListener( listener );

		bt_tester.start();

		addToActive( bt_tester );
		
		return( bt_tester );
	}
	
		
	public boolean
	tryTest(
		NetStatusProtocolTesterBT		bt_tester,
		DistributedDatabaseContact		contact )
	{
		boolean	use_crypto = NetworkManager.getCryptoRequired( NetworkManager.CRYPTO_OVERRIDE_NONE );

		log( "Trying test to " + contact.getName());

		Map	request = new HashMap();

		request.put( "v", new Long( CURRENT_VERSION ));

		request.put( "t", new Long( TEST_TYPE_BT ));

		request.put( "h", bt_tester.getServerHash());

		request.put( "c", new Long( use_crypto?1:0 ));

		Map	reply = sendRequest( contact, request );

		byte[]	server_hash = reply==null?null:(byte[])reply.get( "h" );

		if ( server_hash != null ){

			log( "    " + contact.getName() + " accepted test" );

			bt_tester.testOutbound( adjustLoopback( contact.getAddress()), server_hash, use_crypto );

			return( true );

		}else{

			log( "    " + contact.getName() + " declined test" );

			return( false );
		}
	}

	protected InetSocketAddress
	adjustLoopback(
		InetSocketAddress	address )
	{
		InetSocketAddress local = dht_plugin.getLocalAddress().getAddress();

		if ( local.getAddress().getHostAddress().equals( address.getAddress().getHostAddress())){

			return( new InetSocketAddress( "127.0.0.1", address.getPort()));

		}else{

			return( address );
		}
	}

	protected Map
	sendRequest(
		DistributedDatabaseContact	contact,
		Map							request )
	{
		try{
			log( "Sending DDB request to " + contact.getName() + " - " + request );

			DistributedDatabaseKey key = ddb.createKey( BEncoder.encode( request ));

			DistributedDatabaseValue value =
				contact.read(
					null,
					transfer_type,
					key,
					10000 );

			if ( value == null ){

				return( null );
			}

			Map reply = BDecoder.decode((byte[])value.getValue( byte[].class ));

			log( "    received reply - " + reply );

			return( reply );

		}catch( Throwable e ){

			log( "sendRequest failed", e );

			return( null );
		}
	}

	protected Map
	receiveRequest(
		InetSocketAddress	originator,
		Map					request )
	{
		Map	reply = new HashMap();

		Long	test_type	= (Long)request.get( "t" );

		reply.put( "v", new Long( CURRENT_VERSION ));

		if ( test_type != null ){

			if ( test_type.intValue() == TEST_TYPE_BT ){

				TCPNetworkManager tcp_man = TCPNetworkManager.getSingleton();

				InetSocketAddress adjusted_originator = adjustLoopback( originator );

				boolean	test = adjusted_originator.getAddress().isLoopbackAddress();

				if ( 	test ||
						(	tcp_man.isDefaultTCPListenerEnabled() &&
							tcp_man.getDefaultTCPListeningPortNumber() == ddb.getLocalContact().getAddress().getPort() &&
							SystemTime.getCurrentTime() - tcp_man.getLastIncomingNonLocalConnectionTime() <= 24*60*60*1000 )){

					byte[]	their_hash	= (byte[])request.get( "h" );

					if ( their_hash != null ){

						NetStatusProtocolTesterBT bt_tester;

						synchronized( active_tests ){

							if ( active_tests.size() > MAX_ACTIVE_TESTS ){

								log( "Too many active tests" );

								return( reply );

							}else{

								bt_tester = new NetStatusProtocolTesterBT( this, false );

								bt_tester.start();

								addToActive( bt_tester );
							}
						}

						Long	l_crypto = (Long)request.get( "c" );

						boolean	use_crypto = l_crypto!=null&&l_crypto.longValue()==1;

						bt_tester.testOutbound( adjusted_originator, their_hash, use_crypto );

						reply.put( "h", bt_tester.getServerHash());
					}
				}
			}
		}

		return( reply );
	}

	protected void
	addToActive(
		NetStatusProtocolTesterBT		tester )
	{
		synchronized( active_tests ){

			active_tests.add( tester );

			if ( timer_event == null ){

				timer_event =
					SimpleTimer.addPeriodicEvent(
						"NetStatusProtocolTester:timer",
						30*1000,
						new TimerEventPerformer()
						{
							@Override
							public void
							perform(
								TimerEvent event )
							{
								long	now = SystemTime.getCurrentTime();

								List	to_remove = new ArrayList();

								synchronized( active_tests ){

									for (int i=0;i<active_tests.size();i++){

										NetStatusProtocolTesterBT tester = (NetStatusProtocolTesterBT)active_tests.get(i);

										long start = tester.getStartTime( now );

										if ( now - start > MAX_TEST_TIME ){

											to_remove.add( tester );
										}
									}
								}

								for ( int i=0;i<to_remove.size();i++ ){

									removeFromActive( (NetStatusProtocolTesterBT)to_remove.get(i));
								}
							}
						});
			}
		}
	}

	protected void
	removeFromActive(
		NetStatusProtocolTesterBT		tester )
	{
		tester.destroy();

		synchronized( active_tests ){

			active_tests.remove( tester );

			if ( active_tests.size() == 0 ){

				if ( timer_event != null ){

					timer_event.cancel();

					timer_event = null;
				}
			}
		}
	}

	@Override
	public DistributedDatabaseValue
	read(
		DistributedDatabaseContact			contact,
		DistributedDatabaseTransferType		type,
		DistributedDatabaseKey				ddb_key )

		throws DistributedDatabaseException
	{
		Object	o_key = ddb_key.getKey();

		try{
			byte[]	key = (byte[])o_key;

			HashWrapper	hw = new HashWrapper( key );

			synchronized( request_history ){

				if ( request_history.containsKey( hw )){

					return( null );
				}

				request_history.put( hw, "" );
			}

			Map	request = BDecoder.decode( (byte[])o_key);

			log( "Received DDB request from " + contact.getName() + " - " + request );

			Map	result = receiveRequest( contact.getAddress(), request );

			return( ddb.createValue( BEncoder.encode( result )));

		}catch( Throwable e ){

			log( "DDB read failed", e );

			return( null );
		}
	}

	@Override
	public DistributedDatabaseValue
	write(
		DistributedDatabaseContact			contact,
		DistributedDatabaseTransferType		type,
		DistributedDatabaseKey				key,
		DistributedDatabaseValue			value )

		throws DistributedDatabaseException
	{
		throw( new DistributedDatabaseException( "not supported" ));
	}


	public void
	log(
		String		str )
	{
		plugin.log( str );
	}

	public void
	log(
		String		str,
		Throwable	e )
	{
		plugin.log( str, e );
	}


	protected static class
	testXferType
		implements DistributedDatabaseTransferType
	{
	}
}
