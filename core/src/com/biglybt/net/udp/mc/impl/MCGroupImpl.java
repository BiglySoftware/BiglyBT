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
import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
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


	private static boolean							overall_suspended;
	private static Map<String,MCGroupImpl>			singletons	= new HashMap<>();

	private static AEMonitor	class_mon 	= new AEMonitor( "MCGroup:class" );

	private static AsyncDispatcher		async_dispatcher = new AsyncDispatcher();


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

				singleton = new MCGroupImpl( adapter, group_address, group_port, control_port, interfaces, overall_suspended );

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

	public static void
	setSuspended(
		boolean		suspended )
	{
		try{
			class_mon.enter();

			if ( overall_suspended == suspended ){

				return;
			}

			overall_suspended = suspended;

			for ( MCGroupImpl group: singletons.values()){

				group.setInstanceSuspended( overall_suspended );
			}
		}finally{

			class_mon.exit();
		}
	}

	private MCGroupAdapter			adapter;

	private String					group_address_str;
	private int						group_port;
	private int						control_port;
	protected InetSocketAddress 	group_address;
	private String[]				selected_interfaces;


	private boolean		ttl_problem_reported	= true;	// remove these diagnostic reports on win98
	private boolean		sso_problem_reported	= true; // remove these diagnostic reports on win98

	protected AEMonitor		this_mon	= new AEMonitor( "MCGroup" );

	private Map<String,Set<InetAddress>>		current_registrations = new HashMap<>();

	private volatile boolean		instance_suspended;
	private List<Object[]>			suspended_threads = new ArrayList<>();

	private Map<String,MulticastSocket>		socket_cache = new HashMap<>();

	private
	MCGroupImpl(
		MCGroupAdapter		_adapter,
		String				_group_address,
		int					_group_port,
		int					_control_port,
		String[]			_interfaces,
		boolean				_is_suspended )

		throws MCGroupException
	{
		adapter	= _adapter;

		group_address_str	= _group_address;
		group_port			= _group_port;
		control_port		= _control_port;
		selected_interfaces	= _interfaces;

		instance_suspended	= _is_suspended;

		try{
			InetAddress ia = HostNameToIPResolver.syncResolve( group_address_str );

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

	private void
	setInstanceSuspended(
		boolean	_suspended )
	{
		try{
			this_mon.enter();

			if ( instance_suspended == _suspended ){

				return;
			}

			instance_suspended = _suspended;

			if ( !instance_suspended ){

				List<Object[]> states = new ArrayList<>(suspended_threads);

				suspended_threads.clear();

				for ( final Object[] state: states ){

					new AEThread2( (String)state[0], true )
					{
						@Override
						public void
						run()
						{
							handleSocket( (NetworkInterface)state[1], (InetAddress)state[2], (DatagramSocket)state[3], (Boolean)state[4] );
						}
					}.start();
				}
			}
		}finally{

			this_mon.exit();
		}

		if ( !_suspended ){

			async_dispatcher.dispatch(
				new AERunnable()
				{
					@Override
					public void
					runSupport()
					{
						try{
							processNetworkInterfaces( false );

						}catch( Throwable e ){

							adapter.log( e );
						}
					}
				});
		}
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

			if ( instance_suspended ){

				return;
			}

			List<NetworkInterface>	x = NetUtils.getNetworkInterfaces();

			for ( final NetworkInterface network_interface: x ){

				if ( !interfaceSelected( network_interface )){

					if ( start_of_day ){

						adapter.trace( "ignoring interface " + network_interface.getName() + ":" + network_interface.getDisplayName() + ", not selected" );
					}

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

					if ( ni_address instanceof Inet6Address ){

						if ( start_of_day ){

							adapter.trace( "ignoring IPv6 address " + ni_address + ", interface " + network_interface.getName());
						}

						continue;
					}

					if ( !start_of_day ){

						if ( !changed_interfaces.containsKey( ni_name )){

							changed_interfaces.put( ni_name, network_interface );
						}
					}

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

						String	addresses_string = "";

						Enumeration<InetAddress> it = network_interface.getInetAddresses();

						while (it.hasMoreElements()){

							InetAddress addr = it.nextElement();

							addresses_string += (addresses_string.length()==0?"":",") + addr;
						}

						adapter.trace( "group = " + group_address +"/" +
										network_interface.getName()+":"+
										network_interface.getDisplayName() + "-" + addresses_string +": started" );

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

						new AEThread2("MCGroup:MCListener", true )
							{
								@Override
								public void
								run()
								{
									handleSocket( network_interface, ni_address, mc_sock, true );
								}
							}.start();

					}catch( Throwable e ){

						adapter.log( e );
					}

						// now do the incoming control listener

					try{
						final DatagramSocket control_socket = new DatagramSocket( null );

						control_socket.setReuseAddress( true );

						control_socket.bind( new InetSocketAddress(ni_address, control_port ));

						if ( control_port == 0 ){

							control_port	= control_socket.getLocalPort();

							// System.out.println( "local port = " + control_port );
						}

						new AEThread2( "MCGroup:CtrlListener", true )
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
		if ( instance_suspended ){

			return;
		}

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

				Enumeration<InetAddress> ni_addresses = network_interface.getInetAddresses();

				String	socket_key = null;

				while( ni_addresses.hasMoreElements()){

					InetAddress ni_address = ni_addresses.nextElement();

					if ( !( ni_address instanceof Inet6Address || ni_address.isLoopbackAddress())){

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

						// System.out.println( "sendToGroup: ni = " + network_interface.getName() + ", data = " + new String(data));

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
		if ( instance_suspended ){

			return;
		}

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

				Enumeration<InetAddress> ni_addresses = network_interface.getInetAddresses();

				InetAddress	an_address = null;

				while( ni_addresses.hasMoreElements()){

					InetAddress ni_address = ni_addresses.nextElement();

					if ( !( ni_address instanceof Inet6Address || ni_address.isLoopbackAddress())){

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

						byte[]	data = param_data.replaceAll("%AZINTERFACE%", an_address.getHostAddress()).getBytes();

						// System.out.println( "sendToGroup: ni = " + network_interface.getName() + ", data = " + new String(data));

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

			if ( instance_suspended ){

				try{
					this_mon.enter();

					if ( instance_suspended ){

						suspended_threads.add( new Object[]{ Thread.currentThread().getName(), network_interface, local_address, socket, log_on_stop } );

						return;
					}
				}finally{

					this_mon.exit();
				}
			}
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

	private void
	receivePacket(
		NetworkInterface	network_interface,
		InetAddress			local_address,
	    DatagramPacket		packet )
	{
		if ( instance_suspended ){

			return;
		}

		byte[]	data 	= packet.getData();
		int		len		= packet.getLength();

		// System.out.println( "receive: add = " + local_address + ", data = " + new String( data, 0, len ));

		adapter.received(
				network_interface,
				local_address,
				(InetSocketAddress)packet.getSocketAddress(),
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
		if ( instance_suspended ){

			return;
		}

		DatagramSocket	reply_socket	= null;

		// System.out.println( "sendToMember: add = " + address + ", data = " +new String( data ));

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
