/*
 * Created on 14-Jun-2004
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

package com.biglybt.net.udp.mc.impl;

import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;
import java.nio.channels.MulticastChannel;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.core.networkmanager.VirtualAbstractChannelSelector;
import com.biglybt.core.networkmanager.VirtualAbstractChannelSelector.VirtualSelectorListener;
import com.biglybt.core.util.*;
import com.biglybt.net.udp.mc.MCGroup;
import com.biglybt.net.udp.mc.MCGroupAdapter;
import com.biglybt.net.udp.mc.MCGroupException;


/**
 * @author parg
 *
 */

public class
MCGroupImpl
	implements MCGroup
{
	private final static int		TTL					= 4;

	private final static int		PACKET_SIZE		= 8192;

	private final static boolean		USE_NIO	= true;
	
	private static Map<String,MCGroupImpl>			singletons	= new HashMap<>();

	private static AEMonitor	class_mon 	= new AEMonitor( "MCGroup:class" );

	private static AsyncDispatcher		async_dispatcher = new AsyncDispatcher();

	private volatile boolean	ignore_v4;
	private volatile boolean	ignore_v6;
	
	{
		COConfigurationManager.addAndFireParameterListeners(
			new String[]{
				ConfigKeys.Connection.BCFG_IPV_4_IGNORE_NI_ADDRESSES,
				ConfigKeys.Connection.BCFG_IPV_6_IGNORE_NI_ADDRESSES },
			(n)->{
				ignore_v4 = COConfigurationManager.getBooleanParameter( ConfigKeys.Connection.BCFG_IPV_4_IGNORE_NI_ADDRESSES );
				ignore_v6 = COConfigurationManager.getBooleanParameter( ConfigKeys.Connection.BCFG_IPV_6_IGNORE_NI_ADDRESSES );
			});
	}

	private static VirtualAbstractChannelSelector	v4_selector = new VirtualAbstractChannelSelector( "MCGroup:v4", VirtualAbstractChannelSelector.OP_READ, false );
	private static VirtualAbstractChannelSelector	v6_selector = new VirtualAbstractChannelSelector( "MCGroup:v6", VirtualAbstractChannelSelector.OP_READ, false );
	
	static{
		if ( USE_NIO ){
			
			new AEThread2( "MCGroup:selector", true )
			{
				@Override
				public void
				run()
				{
					while( true ){
						
						v4_selector.select( 250 );
						v6_selector.select( 250 );
					}
				}
			}.start();
		}
	}
	
	public static MCGroupImpl
	getSingleton(
		MCGroupAdapter adapter,
		String				group_address,
		int					group_port,
		int					control_port,
		String[]			interfaces )

		throws MCGroupException
	{
		try{
			class_mon.enter();

			String	key = group_address + ":" + group_port + ":" + control_port;

			MCGroupImpl	singleton = (MCGroupImpl)singletons.get( key );

			if ( singleton == null ){

				if ( control_port == 0 ){

					int	last_allocated = COConfigurationManager.getIntParameter( "mcgroup.ports." + key, 0 );

					if ( last_allocated != 0 ){

						try{
							DatagramSocket test_socket = new DatagramSocket( null );

							test_socket.setReuseAddress( false );

							test_socket.bind( new InetSocketAddress( last_allocated ));

							test_socket.close();

							control_port = last_allocated;

						}catch( Throwable e ){

							e.printStackTrace();
						}
					}
				}

				singleton = new MCGroupImpl( adapter, group_address, group_port, control_port, interfaces );

				if ( control_port == 0 ){

					control_port = singleton.getControlPort();

					COConfigurationManager.setParameter( "mcgroup.ports." + key, control_port );
				}

				singletons.put( key, singleton );
			}

			return( singleton );

		}finally{

			class_mon.exit();
		}
	}

	private final MCGroupAdapter			adapter;

	private final String					group_address_str;
	private final int						group_port;
	protected final InetSocketAddress 		group_address;
	protected final boolean					ipv6;

	private int						control_port;
	private String[]				selected_interfaces;


	private boolean		ttl_problem_reported	= true;	// remove these diagnostic reports on win98
	private boolean		sso_problem_reported	= true; // remove these diagnostic reports on win98

	protected AEMonitor		this_mon	= new AEMonitor( "MCGroup" );

	private Map<String,Set<InetAddress>>		current_registrations = new HashMap<>();

	private Map<String,MulticastSocket>		socket_cache = new HashMap<>();

	private
	MCGroupImpl(
		MCGroupAdapter		_adapter,
		String				_group_address,
		int					_group_port,
		int					_control_port,
		String[]			_interfaces )

		throws MCGroupException
	{
		adapter	= _adapter;

		group_address_str	= _group_address;
		group_port			= _group_port;
		control_port		= _control_port;
		selected_interfaces	= _interfaces;

		try{
			InetAddress ia = HostNameToIPResolver.syncResolve( group_address_str );

			ipv6 = ia instanceof Inet6Address;
			
			group_address = new InetSocketAddress( ia, 0 );

			processNetworkInterfaces( true );

			SimpleTimer.addPeriodicEvent(
				"MCGroup:refresher",
				60*1000,
				new TimerEventPerformer()
				{
					@Override
					public void
					perform(
						TimerEvent event )
					{
						try{
							processNetworkInterfaces( false );

						}catch( Throwable e ){

							adapter.log(e);
						}
					}
				});

		}catch( Throwable e ){

			throw( new MCGroupException( "Failed to initialise MCGroup", e ));
		}
	}

	@Override
	public boolean 
	isIPv6()
	{
		return( ipv6 );
	}

	private void
	processNetworkInterfaces(
		boolean		start_of_day )

		throws SocketException
	{
		Map<String,Set<InetAddress>>			new_registrations	= new HashMap<>();

		Map<String,NetworkInterface>			changed_interfaces	= new HashMap<>();

		try{
			this_mon.enter();

			List<NetworkInterface>	x = NetUtils.getNetworkInterfaces();

			for ( final NetworkInterface network_interface: x ){

				if ( !interfaceSelected( network_interface )){

					if ( start_of_day ){

						adapter.trace( "ignoring interface " + network_interface.getName() + ":" + network_interface.getDisplayName() + ", not selected" );
					}

					continue;
				}
				
				if ( ( ignore_v4 && !ipv6 ) || ( ignore_v6 && ipv6 )){
					
					continue;
				}

				String ni_name = network_interface.getName();

				Set<InetAddress> old_address_set = current_registrations.get( ni_name );

				if ( old_address_set == null ){

					old_address_set	= new HashSet<>();
				}

				Set<InetAddress>	new_address_set = new HashSet<>();

				new_registrations.put( ni_name, new_address_set );

				Enumeration<InetAddress> ni_addresses = network_interface.getInetAddresses();

				while( ni_addresses.hasMoreElements()){

					final InetAddress ni_address = ni_addresses.nextElement();

					new_address_set.add( ni_address );

					if ( old_address_set.contains( ni_address )){

							// already established

						continue;
					}
						// turn on loopback to see if it helps for local host UPnP devices
						// nah, turn it off again, it didn;t

					if ( ni_address.isLoopbackAddress()){

						if ( start_of_day ){

							adapter.trace( "ignoring loopback address " + ni_address + ", interface " + network_interface.getName());
						}

						continue;
					}

					if (	ipv6 && ni_address instanceof Inet4Address ||
							!ipv6 && ni_address instanceof Inet6Address ){

						continue;
					}

					if ( ni_address instanceof Inet6Address ){
						
						if ( ni_address.isLinkLocalAddress()){
							
							continue;
						}
					}
					
					if ( !start_of_day ){

						if ( !changed_interfaces.containsKey( ni_name )){

							changed_interfaces.put( ni_name, network_interface );
						}
					}

					if ( USE_NIO ){
						
						try{
							DatagramChannel mc_channel = DatagramChannel.open( ipv6?StandardProtocolFamily.INET6:StandardProtocolFamily.INET );
	
							mc_channel.setOption(StandardSocketOptions.SO_REUSEADDR, true );
							
							try{
								mc_channel.setOption(StandardSocketOptions.IP_MULTICAST_TTL, TTL );
								
							}catch( Throwable e ){
								
								if ( !ttl_problem_reported ){
	
									ttl_problem_reported	= true;
	
									adapter.log( e );
								}
							}
							
							mc_channel.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, true );

							mc_channel.setOption(StandardSocketOptions.IP_MULTICAST_IF, network_interface );
							
							mc_channel.bind( new InetSocketAddress( group_port ));
								
							adapter.trace( "group = " + group_address +"/" +
											network_interface.getName()+":"+
											network_interface.getDisplayName() + "-" + ni_address.getHostAddress() +": started" );

							
							MembershipKey key = mc_channel.join( group_address.getAddress(), network_interface );
							
							mc_channel.socket().setSoTimeout( 30*1000 );
							
							Runtime.getRuntime().addShutdownHook(
									new AEThread("MCGroup:VMShutdown")
									{
										@Override
										public void
										runSupport()
										{
											try{
												key.drop();
	
											}catch( Throwable e ){
	
												adapter.log( e );
											}
										}
									});
							
							mc_channel.configureBlocking( false );
							
							SelectorListener listener = new SelectorListener( network_interface, ni_address, mc_channel, true );
								
							if ( ipv6 ){
								
								v6_selector.register( mc_channel, listener, null );
								
							}else{ 
								
								v4_selector.register( mc_channel, listener, null );
							}
						}catch( Throwable e ){
							
							// adapter.log( "Failed to join group '" + group_address + ", ni=" + network_interface, e );
						}
					}else{
						try{
								// set up group
	
							final MulticastSocket mc_sock = new MulticastSocket( group_port );
	
							mc_sock.setReuseAddress(true);
	
								// windows 98 doesn't support setTimeToLive
	
							try{
								mc_sock.setTimeToLive(TTL);
	
							}catch( Throwable e ){
	
								if ( !ttl_problem_reported ){
	
									ttl_problem_reported	= true;
	
									adapter.log( e );
								}
							}
		
							adapter.trace( "group = " + group_address +"/" +
											network_interface.getName()+":"+
											network_interface.getDisplayName() + "-" + ni_address.getHostAddress() +": started" );
	
							mc_sock.joinGroup( group_address, network_interface );
	
							mc_sock.setNetworkInterface( network_interface );
	
								// note that false ENABLES loopback mode which is what we want
	
							mc_sock.setLoopbackMode(false);
	
							Runtime.getRuntime().addShutdownHook(
									new AEThread("MCGroup:VMShutdown")
									{
										@Override
										public void
										runSupport()
										{
											try{
												mc_sock.leaveGroup( group_address, network_interface );
	
											}catch( Throwable e ){
	
												adapter.log( e );
											}
										}
									});
	
							new AEThread2("MCGroup:MCListener - " + ni_address, true )
								{
									@Override
									public void
									run()
									{
										handleSocket( network_interface, ni_address, mc_sock, true );
									}
								}.start();
	
						}catch( Throwable e ){
	
							// adapter.log( "Failed to join group '" + group_address + ", ni=" + network_interface, e );
						}
					}
					
						// now do the incoming control listener

					if ( USE_NIO ){
						
						try{
							DatagramChannel control_channel = DatagramChannel.open( ipv6?StandardProtocolFamily.INET6:StandardProtocolFamily.INET );
	
							control_channel.setOption(StandardSocketOptions.SO_REUSEADDR, true );
	
							control_channel.bind( new InetSocketAddress(ni_address, control_port ));
							
							control_channel.socket().setSoTimeout( 30*1000 );
							
							if ( control_port == 0 ){
	
								control_port	= ((InetSocketAddress)control_channel.getLocalAddress()).getPort();
	
								// System.out.println( "local port = " + control_port );
							}
							
							control_channel.configureBlocking( false );
							
							SelectorListener listener = new SelectorListener( network_interface, ni_address, control_channel, false );
								
							if ( ipv6 ){
								
								v6_selector.register( control_channel, listener, null );
								
							}else{ 
								
								v4_selector.register( control_channel, listener, null );
							}
						}catch( Throwable e ){
	
							adapter.log( e );
						}
					}else{
				
						try{
							final DatagramSocket control_socket = new DatagramSocket( null );
	
							control_socket.setReuseAddress( true );
	
							control_socket.bind( new InetSocketAddress(ni_address, control_port ));
	
							if ( control_port == 0 ){
	
								control_port	= control_socket.getLocalPort();
	
								// System.out.println( "local port = " + control_port );
							}
	
							new AEThread2( "MCGroup:CtrlListener - " + ni_address, true )
								{
									@Override
									public void
									run()
									{
										handleSocket( network_interface, ni_address, control_socket, false );
									}
								}.start();
	
						}catch( Throwable e ){
	
							adapter.log( e );
						}
					}
				}
			}
		}finally{

			current_registrations	= new_registrations;

			this_mon.exit();
		}

		for ( NetworkInterface ni: changed_interfaces.values()){

			try{
				adapter.interfaceChanged( ni );

			}catch( Throwable e ){

				Debug.out( e );
			}
		}
	}

	@Override
	public int
	getControlPort()
	{
		return( control_port );
	}

	private boolean
	interfaceSelected(
		NetworkInterface	ni )
	{
		if ( selected_interfaces != null && selected_interfaces.length > 0 ){

			boolean	ok 	= false;

			for (int i=0;i<selected_interfaces.length;i++){

				if ( ni.getName().equalsIgnoreCase( selected_interfaces[i] )){

					ok	= true;

					break;
				}
			}

			return( ok );

		}else{

			return( true );
		}
	}

	private boolean
	validNetworkAddress(
		final NetworkInterface	network_interface,
		final InetAddress		ni_address )
	{
		String ni_name = network_interface.getName();

		try{
			this_mon.enter();

			Set<InetAddress>	set = (Set<InetAddress>)current_registrations.get( ni_name );

			if ( set == null ){

				return( false );
			}

			return( set.contains( ni_address ));

		}finally{

			this_mon.exit();
		}
	}


	@Override
	public void
	sendToGroup(
		final byte[]	data )
	{
			// have debugs showing the send-to-group operation hanging and blocking AZ close, make async

		async_dispatcher.dispatch(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					sendToGroupSupport( data );
				}
			});
	}

	private void
	sendToGroupSupport(
		byte[]	data )
	{
		try{
			List<NetworkInterface>	x = NetUtils.getNetworkInterfaces();

			for ( final NetworkInterface network_interface: x ){

				if ( !interfaceSelected( network_interface )){

					continue;
				}

				if ( ( ignore_v4 && !ipv6 ) || ( ignore_v6 && ipv6 )){
					
					continue;
				}
				
				Enumeration<InetAddress> ni_addresses = network_interface.getInetAddresses();

				String	socket_key = null;

				while( ni_addresses.hasMoreElements()){

					InetAddress ni_address = ni_addresses.nextElement();
					
					if (	ipv6 && ni_address instanceof Inet4Address ||
							!ipv6 && ni_address instanceof Inet6Address ){

						continue;
					}
					
					if ( !ni_address.isLoopbackAddress()){

						socket_key = ni_address.toString();

						break;
					}
				}

				if ( socket_key == null ){

					continue;
				}

				socket_key += ":" + control_port;

				try{

					synchronized( socket_cache ){

						MulticastSocket mc_sock = socket_cache.get( socket_key );

						if ( mc_sock == null ){

							mc_sock = new MulticastSocket(null);

							mc_sock.setReuseAddress(true);

							try{
								mc_sock.setTimeToLive( TTL );

							}catch( Throwable e ){

								if ( !ttl_problem_reported ){

									ttl_problem_reported	= true;

									adapter.log( e );
								}
							}

							mc_sock.bind( new InetSocketAddress( control_port ));

							mc_sock.setNetworkInterface( network_interface );

							socket_cache.put( socket_key, mc_sock );
						}

						/*
						if ( isIPv6() ){
							System.out.println( "sendToGroup: ni = " + network_interface.getName() + ", data = " + new String(data));
						}
						*/
						
						DatagramPacket packet = new DatagramPacket(data, data.length, group_address.getAddress(), group_port );

						try{
							mc_sock.send(packet);

						}catch( Throwable e ){

							try{
								mc_sock.close();

							}catch( Throwable f ){

							}

							socket_cache.remove( socket_key );

							throw( e );
						}
					}

				}catch( Throwable e ){

					if ( !sso_problem_reported ){

						sso_problem_reported	= true;

						adapter.log( e );
					}
				}
			}
		}catch( Throwable e ){
		}
	}

	@Override
	public void
	sendToGroup(
		final String	param_data )
	{
			// have debugs showing the send-to-group operation hanging and blocking AZ close, make async

		async_dispatcher.dispatch(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					sendToGroupSupport( param_data );
				}
			});
	}

	private void
	sendToGroupSupport(
		String	param_data )
	{
		try{
			List<NetworkInterface>	x = NetUtils.getNetworkInterfaces();

			for ( NetworkInterface network_interface: x ){

				if ( !interfaceSelected( network_interface )){

					continue;
				}
				
				if ( ( ignore_v4 && !ipv6 ) || ( ignore_v6 && ipv6 )){
					
					continue;
				}

				Enumeration<InetAddress> ni_addresses = network_interface.getInetAddresses();

				InetAddress	an_address = null;

				while( ni_addresses.hasMoreElements()){

					InetAddress ni_address = ni_addresses.nextElement();

					if (	ipv6 && ni_address instanceof Inet4Address ||
							!ipv6 && ni_address instanceof Inet6Address ){

						continue;
					}
					
					if ( !ni_address.isLoopbackAddress()){

						an_address	= ni_address;

						break;
					}
				}

				if ( an_address == null ){

					continue;
				}

				String socket_key = an_address.toString() + ":" + control_port;

				try{

					synchronized( socket_cache ){

						MulticastSocket mc_sock = socket_cache.get( socket_key );

						if ( mc_sock == null ){

							mc_sock = new MulticastSocket(null);

							mc_sock.setReuseAddress(true);

							try{
								mc_sock.setTimeToLive( TTL );

							}catch( Throwable e ){

								if ( !ttl_problem_reported ){

									ttl_problem_reported	= true;

									adapter.log( e );
								}
							}

							mc_sock.bind( new InetSocketAddress( control_port ));

							mc_sock.setNetworkInterface( network_interface );

							socket_cache.put( socket_key, mc_sock );
						}

						String host = AddressUtils.getHostAddressForURL(an_address);
						
						byte[]	data = param_data.replaceAll("%AZINTERFACE%", host).getBytes();

						/*
						if ( ipv6 ){
							System.out.println( "sendToGroup: ni = " + network_interface.getName() + ", data = " + new String(data));
						}
						*/
						
						DatagramPacket packet = new DatagramPacket(data, data.length, group_address.getAddress(), group_port );

						try{
							mc_sock.send(packet);

						}catch( Throwable e ){

							try{
								mc_sock.close();

							}catch( Throwable f ){

							}

							socket_cache.remove( socket_key );

							throw( e );
						}
					}
				}catch( Throwable e ){

					if ( !sso_problem_reported ){

						sso_problem_reported	= true;

						adapter.log( e );
					}
				}
			}
		}catch( Throwable e ){
		}
	}

	private void
	handleSocket(
		final NetworkInterface		network_interface,
		final InetAddress			local_address,
		final DatagramSocket		socket,
		final boolean				log_on_stop )
	{
		long	successful_accepts 	= 0;
		long	failed_accepts		= 0;

		int	port = socket.getLocalPort();

		try{
				// introduce a timeout so that when a Network interface changes we don't sit here
				// blocking forever and thus never realise that we should shutdown

			socket.setSoTimeout( 30000 );

		}catch( Throwable e ){

		}

		while(true){

			if ( !validNetworkAddress( network_interface, local_address )){

				if ( log_on_stop ){

					adapter.trace(
							"group = " + group_address +"/" +
							network_interface.getName()+":"+
							network_interface.getDisplayName() + " - " + local_address + ": stopped" );
				}

				return;
			}

			try{
				byte[] buf = new byte[PACKET_SIZE];

				DatagramPacket packet = new DatagramPacket(buf, buf.length );

				socket.receive( packet );

				successful_accepts++;

				failed_accepts	 = 0;

				receivePacket( network_interface, local_address, packet );

			}catch( SocketTimeoutException e ){

			}catch( Throwable e ){

				failed_accepts++;

				adapter.trace( "MCGroup: receive failed on port " + port + ":" + e.getMessage());

				if (( failed_accepts > 100 && successful_accepts == 0 ) || failed_accepts > 1000 ){

					adapter.trace( "    too many failures, abandoning" );

					break;
				}
			}
		}
	}

	class
	SelectorListener
		implements VirtualSelectorListener
	{
		private final NetworkInterface		network_interface;
		private final InetAddress			local_address;
		private final DatagramChannel		channel;
		private final boolean				log_on_stop;
		
		long	successful_accepts 	= 0;
		long	failed_accepts		= 0;

		SelectorListener(
			NetworkInterface	_network_interface,
			InetAddress			_local_address,
			DatagramChannel		_channel,
			boolean				_log_on_stop )
		{
			network_interface		= _network_interface;
			local_address			= _local_address;
			channel 				= _channel;
			log_on_stop				= _log_on_stop;
		}
		
		@Override
		public boolean 
		selectSuccess(
			VirtualAbstractChannelSelector	selector,
			AbstractSelectableChannel 		sc, 
			Object 							attachment )
		{
			if ( !validNetworkAddress( network_interface, local_address )){

				if ( log_on_stop ){

					adapter.trace(
							"group = " + group_address +"/" +
							network_interface.getName()+":"+
							network_interface.getDisplayName() + " - " + local_address + ": stopped" );
				}

				selector.cancel( sc );
				
				return( false );
			}
			
			try{
				ByteBuffer buffer = ByteBuffer.allocate(PACKET_SIZE);

				InetSocketAddress remote = (InetSocketAddress)channel.receive( buffer );
								
				successful_accepts++;

				failed_accepts	 = 0;
				
				receivePacket( network_interface, local_address, buffer, remote );
				
				return( true );
				
			}catch( Throwable  e){
				
				failed_accepts++;

				adapter.trace( "MCGroup: receive failed on port " + channel.socket().getLocalPort() + ":" + e.getMessage());

				if (( failed_accepts > 100 && successful_accepts == 0 ) || failed_accepts > 1000 ){

					adapter.trace( "    too many failures, abandoning" );

					selector.cancel( sc );
				}
				
				return( false );
			}
		}
		
		@Override
		public void 
		selectFailure(
			VirtualAbstractChannelSelector	selector,
			AbstractSelectableChannel		sc, 
			Object							attachment, 
			Throwable 						msg)
		{
			Debug.out( msg );
			
			selector.cancel( sc );
		}
	}
	
	private void
	receivePacket(
		NetworkInterface	network_interface,
		InetAddress			local_address,
	    DatagramPacket		packet )
	{
		byte[]	data 	= packet.getData();
		int		len		= packet.getLength();

		/*
		if ( local_address instanceof Inet6Address ){
			System.out.println( "receive: add = " + local_address + ", data = " + new String( data, 0, len ));
		}
		*/
		
		adapter.received(
				network_interface,
				local_address,
				(InetSocketAddress)packet.getSocketAddress(),
				data,
				len );
	}

	private void
	receivePacket(
		NetworkInterface	network_interface,
		InetAddress			local_address,
		ByteBuffer			buffer,
		InetSocketAddress	remote )
	{
		byte[]	data 	= buffer.array();
		int		len		= buffer.position();

		/*
		if ( local_address instanceof Inet6Address ){
			System.out.println( "receive: add = " + local_address + ", data = " + new String( data, 0, len ));
		}
		*/
		
		adapter.received(
				network_interface,
				local_address,
				remote,
				data,
				len );
	}
	
	@Override
	public void
	sendToMember(
		InetSocketAddress	address,
		byte[]				data )

		throws MCGroupException
	{
		DatagramSocket	reply_socket	= null;

		/*
		if ( address.getAddress() instanceof Inet6Address ){
			System.out.println( "sendToMember: add = " + address + ", data = " +new String( data ));
		}
		*/
		
		try{
			reply_socket = new DatagramSocket( null );

			reply_socket.setReuseAddress(true);

			reply_socket.bind( new InetSocketAddress( group_port ));

			DatagramPacket reply_packet = new DatagramPacket(data,data.length,address);

			reply_socket.send( reply_packet );

		}catch( Throwable e ){

			throw( new MCGroupException( "sendToMember failed for " + address, e ));

		}finally{

			if ( reply_socket != null ){

				try{
					reply_socket.close();

				}catch( Throwable e ){
				}
			}
		}
	}
}
