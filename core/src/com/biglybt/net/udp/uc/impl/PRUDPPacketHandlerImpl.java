/*
 * File    : PRUDPPacketReceiverImpl.java
 * Created : 20-Jan-2004
 * By      : parg
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.net.udp.uc.impl;

/**
 * @author parg
 *
 */

import java.io.*;
import java.net.*;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.*;

import org.gudy.bouncycastle.util.encoders.Base64;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.networkmanager.admin.NetworkAdminPropertyChangeListener;
import com.biglybt.core.util.*;
import com.biglybt.net.udp.uc.*;

public class
PRUDPPacketHandlerImpl
	implements PRUDPPacketHandler
{
	private static final LogIDs LOGID = LogIDs.NET;

	private boolean			TRACE_REQUESTS	= false;

	private static int 	MAX_PACKET_SIZE;

	static{
		COConfigurationManager.addAndFireParameterListener(
			"network.udp.mtu.size",
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					String parameter_name )
				{
					MAX_PACKET_SIZE = COConfigurationManager.getIntParameter( parameter_name );
				}
			});
	}

	private static final long	MAX_SEND_QUEUE_DATA_SIZE	= 2*1024*1024;
	private static final long	MAX_RECV_QUEUE_DATA_SIZE	= 1*1024*1024;

	private static boolean	use_socks;

	static{
		COConfigurationManager.addAndFireParameterListeners(
			new String[]{
				"Enable.Proxy",
				"Enable.SOCKS",
			},
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					String parameter_name )
				{
					boolean	enable_proxy 	= COConfigurationManager.getBooleanParameter("Enable.Proxy");
				    boolean enable_socks	= COConfigurationManager.getBooleanParameter("Enable.SOCKS");

				    use_socks = enable_proxy && enable_socks;
				}
			});
	}


	private int				port;
	private DatagramSocket	socket;

	private CopyOnWriteList<PRUDPPrimordialHandler>	primordial_handlers = new CopyOnWriteList<>();
	private PRUDPRequestHandler request_handler;

	private PRUDPPacketHandlerStatsImpl	stats = new PRUDPPacketHandlerStatsImpl( this );


	private Map<Integer,PRUDPPacketHandlerRequestImpl>			requests = new LightHashMap<>();
	
	private AEMonitor	requests_mon	= new AEMonitor( "PRUDPPH:req" );


	private AEMonitor		send_queue_mon	= new AEMonitor( "PRUDPPH:sd", true );
	private long			send_queue_data_size;
	private final List[]	send_queues		= new List[]{ new LinkedList(),new LinkedList(),new LinkedList()};
	private AESemaphore		send_queue_sem	= new AESemaphore( "PRUDPPH:sq" );
	private AEThread2		send_thread;

	private AEMonitor		recv_queue_mon	= new AEMonitor( "PRUDPPH:rq", true );
	private long			recv_queue_data_size;
	private List<Object[]>	recv_queue		= new ArrayList<>();
	private AESemaphore		recv_queue_sem	= new AESemaphore( "PRUDPPH:rq" );
	private AEThread2		recv_thread;

	private int			send_delay				= 0;
	private int			receive_delay			= 0;
	private int			queued_request_timeout	= 0;

	private long		total_requests_received;
	private long		total_requests_processed;
	private long		total_replies;
	private long		last_error_report;

	private AEMonitor	bind_address_mon	= new AEMonitor( "PRUDPPH:bind" );

	private InetAddress				default_bind_ip;
	private InetAddress				explicit_bind_ip;
	private boolean					explicit_bind_ip_ad;

	private volatile InetAddress				current_bind_ip;
	private volatile InetAddress				target_bind_ip;

	private volatile boolean		failed;
	private volatile boolean		destroyed;
	private AESemaphore destroy_sem = new AESemaphore("PRUDPPacketHandler:destroy");

	private Throwable 	init_error;

	private PRUDPPacketHandlerImpl altProtocolDelegate;

	private final PacketTransformer	packet_transformer;

	protected
	PRUDPPacketHandlerImpl(
		int					_port,
		InetAddress			_bind_ip,
		PacketTransformer	_packet_transformer )
	{
		port				= _port;
		explicit_bind_ip	= _bind_ip;
		packet_transformer	= _packet_transformer;

		default_bind_ip = NetworkAdmin.getSingleton().getSingleHomedServiceBindAddress();

		calcBind();

		final AESemaphore init_sem = new AESemaphore("PRUDPPacketHandler:init");

		new AEThread2( "PRUDPPacketReciever:" + port, true )
			{
				@Override
				public void
				run()
				{
					receiveLoop(init_sem);
				}
			}.start();


		final TimerEventPeriodic[]	f_ev = {null};

		TimerEventPeriodic ev =
			SimpleTimer.addPeriodicEvent(
				"PRUDP:timeouts",
				5000,
				new TimerEventPerformer()
				{
					@Override
					public void
					perform(
						TimerEvent	event )
					{
						boolean jobDone = checkTimeouts();
						
						if ( jobDone && f_ev[0] != null ){

							f_ev[0].cancel();
						}
					}
				});

		f_ev[0] = ev;

		init_sem.reserve();
	}

	@Override
	public boolean
	hasPrimordialHandler()
	{
		synchronized( primordial_handlers ){

			return( primordial_handlers.size() > 0 );
		}
	}

	@Override
	public void
	addPrimordialHandler(
		PRUDPPrimordialHandler	handler )
	{
		synchronized( primordial_handlers ){

			if ( primordial_handlers.contains( handler )){

				Debug.out( "Primordial handler already added!" );

				return;
			}

			int	priority;

			if ( handler instanceof AEPriorityMixin ){

				priority = ((AEPriorityMixin)handler).getPriority();

			}else{

				priority = AEPriorityMixin.PRIORITY_NORMAL;
			}

			List<PRUDPPrimordialHandler> existing = primordial_handlers.getList();

			int	insert_at = -1;

			for (int i=0;i<existing.size();i++){

				PRUDPPrimordialHandler e = existing.get( i );

				int	existing_priority;

				if ( e instanceof AEPriorityMixin ){

					existing_priority = ((AEPriorityMixin)e).getPriority();

				}else{

					existing_priority = AEPriorityMixin.PRIORITY_NORMAL;
				}

				if ( existing_priority < priority ){

					insert_at = i;

					break;
				}
			}

			if ( insert_at >= 0 ){

				primordial_handlers.add( insert_at, handler );

			}else{

				primordial_handlers.add( handler );
			}
		}

			// if we have an altProtocolDelegate then this shares the list of handlers so no need to add
	}

	@Override
	public void
	removePrimordialHandler(
		PRUDPPrimordialHandler	handler )
	{
		synchronized( primordial_handlers ){

			if ( !primordial_handlers.contains( handler )){

				Debug.out( "Primordial handler not found!" );

				return;
			}

			primordial_handlers.remove( handler );
		}

			// if we have an altProtocolDelegate then this shares the list of handlers so no need to remove
	}

	@Override
	public void
	setRequestHandler(
		PRUDPRequestHandler		_request_handler )
	{
		if ( request_handler != null ){

			if ( _request_handler != null ){

					// if we need to support this then the handler will have to be associated
					// with a message type map, or we chain together and give each handler
					// a bite at processing the message

				throw( new RuntimeException( "Multiple handlers per endpoint not supported" ));
			}
		}

		request_handler	= _request_handler;

		PRUDPPacketHandlerImpl delegate = altProtocolDelegate;

		if ( delegate != null ){

			delegate.setRequestHandler(_request_handler);
		}
	}

	@Override
	public PRUDPRequestHandler
	getRequestHandler()
	{
		return( request_handler );
	}

	@Override
	public int
	getPort()
	{
		if ( port == 0 && socket != null ){

			return( socket.getLocalPort());
		}

		return( port );
	}

	@Override
	public InetAddress
	getCurrentBindAddress()
	{
		return( current_bind_ip );
	}

	protected void
	setDefaultBindAddress(
		InetAddress	address )
	{
		try{
			bind_address_mon.enter();

			default_bind_ip	= address;

			calcBind();

		}finally{

			bind_address_mon.exit();
		}
	}

	@Override
	public InetAddress
	getExplicitBindAddress()
	{
		return( explicit_bind_ip );
	}

	
	@Override
	public void
	setExplicitBindAddress(
		InetAddress	address,
		boolean		autoDelegate )
	{
		try{
			bind_address_mon.enter();

			explicit_bind_ip	= address;
			explicit_bind_ip_ad	= autoDelegate;
			
			calcBind();

		}finally{

			bind_address_mon.exit();
		}

		int	loops = 0;

		while( current_bind_ip != target_bind_ip && !(failed || destroyed)){

			if ( loops >= 100 ){

				Debug.out( "Giving up on wait for bind ip change to take effect" );

				break;
			}

			try{
				Thread.sleep(50);

				loops++;

			}catch( Throwable e ){

				break;
			}
		}
	}

	protected void
	calcBind()
	{
		if ( explicit_bind_ip != null ){

			if ( explicit_bind_ip_ad ){
				
				InetAddress altAddress = null;
				NetworkAdmin adm = NetworkAdmin.getSingleton();
				
				try{
					if (explicit_bind_ip instanceof Inet6Address && !explicit_bind_ip.isAnyLocalAddress() && adm.hasIPV4Potential())
						altAddress = adm.getSingleHomedServiceBindAddress(NetworkAdmin.IP_PROTOCOL_VERSION_REQUIRE_V4);
					else if (explicit_bind_ip instanceof Inet4Address && adm.hasIPV6Potential())
						altAddress = adm.getSingleHomedServiceBindAddress(NetworkAdmin.IP_PROTOCOL_VERSION_REQUIRE_V6);
				}catch (UnsupportedAddressTypeException e){
				}

				if(altProtocolDelegate != null && !altProtocolDelegate.explicit_bind_ip.equals(altAddress)){
					altProtocolDelegate.destroy();
					altProtocolDelegate = null;
				}

				if ( altAddress != null ){
					// issue here is that we can't bind a explicit IPv6 and an 'any' IPv4 (or vice-versa) on a dual-stack impl
					
					if ( altAddress.isAnyLocalAddress()){
						
						InetAddress alt = adm.getAlternativeProtocolBindAddress( explicit_bind_ip );
						
						if ( alt != null ){
							
							altAddress = alt;
						}
					}
				}
				target_bind_ip = explicit_bind_ip;				

				if(altAddress != null && altProtocolDelegate == null){
					altProtocolDelegate = new PRUDPPacketHandlerImpl(port,altAddress,packet_transformer);
					altProtocolDelegate.stats = stats;
					altProtocolDelegate.primordial_handlers = primordial_handlers;
					altProtocolDelegate.request_handler = request_handler;
				}
				
			}else{
				
				if(altProtocolDelegate != null){
					altProtocolDelegate.destroy();
					altProtocolDelegate = null;
				}
	
				target_bind_ip = explicit_bind_ip;
			}
		}else{

			InetAddress altAddress = null;
			NetworkAdmin adm = NetworkAdmin.getSingleton();
			try{
				if (default_bind_ip instanceof Inet6Address && !default_bind_ip.isAnyLocalAddress() && adm.hasIPV4Potential())
					altAddress = adm.getSingleHomedServiceBindAddress(NetworkAdmin.IP_PROTOCOL_VERSION_REQUIRE_V4);
				else if (default_bind_ip instanceof Inet4Address && adm.hasIPV6Potential())
					altAddress = adm.getSingleHomedServiceBindAddress(NetworkAdmin.IP_PROTOCOL_VERSION_REQUIRE_V6);
			} catch (UnsupportedAddressTypeException e){
			}

			if(altProtocolDelegate != null && !altProtocolDelegate.explicit_bind_ip.equals(altAddress)){
				altProtocolDelegate.destroy();
				altProtocolDelegate = null;
			}

			if(altAddress != null && altProtocolDelegate == null){
				altProtocolDelegate = new PRUDPPacketHandlerImpl(port,altAddress,packet_transformer);
				altProtocolDelegate.stats = stats;
				altProtocolDelegate.primordial_handlers = primordial_handlers;
				altProtocolDelegate.request_handler = request_handler;
			}

			target_bind_ip = default_bind_ip;
		}
	}

	protected void
	receiveLoop(
		AESemaphore	init_sem )
	{
		long	last_socket_close_time = 0;

		NetworkAdminPropertyChangeListener prop_listener =
			new NetworkAdminPropertyChangeListener()
	    	{
	    		@Override
			    public void
	    		propertyChanged(
	    			String		property )
	    		{
	    			if ( property == NetworkAdmin.PR_DEFAULT_BIND_ADDRESS ){

	    				setDefaultBindAddress( NetworkAdmin.getSingleton().getSingleHomedServiceBindAddress());
	    			}
	    		}
	    	};

	    NetworkAdmin.getSingleton().addPropertyChangeListener( prop_listener );

		try{
				// outter loop picks up bind-ip changes

			while( !( failed || destroyed )){

				if ( socket != null ){

					try{
						socket.close();

					}catch( Throwable e ){

						Debug.printStackTrace(e);
					}
				}

				InetSocketAddress	address		= null;
				DatagramSocket		new_socket	= null;

				try{
					if ( target_bind_ip == null ){

						address = new InetSocketAddress("127.0.0.1",port);

						new_socket = new DatagramSocket( port );

					}else{

						address = new InetSocketAddress( target_bind_ip, port );

						new_socket = new DatagramSocket( address );
					}
				}catch( BindException e ){

						// some firewalls (e.g. Comodo) seem to close sockets on us and then not release them quickly so we come through here and get
						// an 'address already in use' failure

					boolean	rebind_worked = false;

					int	delay = 25;

					for (int i=0;i<16 && !( failed || destroyed );i++){

						try{
							Thread.sleep( delay );

							delay = delay * 2;

							if ( delay > 1000 ){

								delay = 1000;
							}

							if ( target_bind_ip == null ){

								address = new InetSocketAddress("127.0.0.1",port);

								new_socket = new DatagramSocket( port );

							}else{

								address = new InetSocketAddress( target_bind_ip, port );

								new_socket = new DatagramSocket( address );
							}

							if (Logger.isEnabled())
								Logger.log(new LogEvent(LOGID,"PRUDPPacketReceiver: rebind to " + target_bind_ip + " worked (tries=" + (i+1) + ") after getting " + Debug.getNestedExceptionMessage( e )));

							rebind_worked = true;

							break;

						}catch( Throwable f ){

						}
					}

					if ( !rebind_worked ){

						if (Logger.isEnabled())
							Logger.log(new LogEvent(LOGID,"PRUDPPacketReceiver: bind failed with " + Debug.getNestedExceptionMessage( e )));


							// one off attempt to recover by selecting an explicit one.
							// on  Vista (at least) we sometimes fail with wildcard but succeeed
							// with explicit

						if ( target_bind_ip.isAnyLocalAddress()){

							InetAddress guess = NetworkAdmin.getSingleton().guessRoutableBindAddress();

							if ( guess != null ){

								if (Logger.isEnabled())
									Logger.log(new LogEvent(LOGID,"PRUDPPacketReceiver: retrying with bind IP guess of " + guess ));

								try{

									InetSocketAddress guess_address = new InetSocketAddress( guess, port );

									new_socket = new DatagramSocket( guess_address );

									target_bind_ip 	= guess;
									address			= guess_address;

									if (Logger.isEnabled())
										Logger.log(new LogEvent(LOGID,"PRUDPPacketReceiver: Switched to explicit bind ip " + target_bind_ip + " after initial bind failure with wildcard (" + e.getMessage() + ")" ));

								}catch( Throwable f ){

									throw( e );
								}
							}else{

								throw( e );
							}
						}else{

							throw( e );
						}
					}
				}

				new_socket.setReuseAddress(true);

					// short timeout on receive so that we can interrupt a receive fairly quickly

				new_socket.setSoTimeout( 1000 );

					// only make the socket public once fully configured

				socket = new_socket;

				current_bind_ip	= target_bind_ip;

				init_sem.release();

				if (Logger.isEnabled())
					Logger.log(new LogEvent(LOGID,
							"PRUDPPacketReceiver: receiver established on port " + port + (current_bind_ip==null?"":(", bound to " + current_bind_ip ))));

				byte[] buffer = null;

				long	successful_accepts 	= 0;
				long	failed_accepts		= 0;

				while( !( failed || destroyed )){

					if ( current_bind_ip != target_bind_ip ){

						break;
					}

					try{

						if ( buffer == null ){

							buffer = new byte[MAX_PACKET_SIZE];
						}

						DatagramPacket packet = new DatagramPacket( buffer, buffer.length, address );

						receiveFromSocket( packet );

						if ( packet.getLength() > MAX_PACKET_SIZE ){

							if ( MAX_PACKET_SIZE < PRUDPPacket.MAX_PACKET_SIZE ){

								Debug.out( "UDP Packet truncated: received length=" + packet.getLength() + ", current max=" + MAX_PACKET_SIZE );

								MAX_PACKET_SIZE = Math.min( packet.getLength() + 256, PRUDPPacket.MAX_PACKET_SIZE );

								buffer = null;

								continue;
							}
						}

						long	receive_time = SystemTime.getCurrentTime();

						successful_accepts++;

						failed_accepts = 0;

						for ( PRUDPPrimordialHandler prim_hand: primordial_handlers ){

							if ( prim_hand.packetReceived( packet )){

									// primordial handlers get their own buffer as we can't guarantee
									// that they don't need to hang onto the data

								buffer	= null;

								stats.primordialPacketReceived( packet.getLength());

								break;
							}
						}

						if ( buffer != null ){

							process( packet, receive_time );
						}

					}catch( SocketTimeoutException e ){

					}catch( Throwable e ){

							// on vista we get periodic socket closures

						String	message = e.getMessage();

						if ( 	socket.isClosed() ||
								( message != null &&
									message.toLowerCase().contains("socket closed"))){

							long	now = SystemTime.getCurrentTime();

								// can't guarantee there aren't situations where we get into a screaming
								// closed loop so guard against this somewhat

							if ( now - last_socket_close_time < 500 ){

								Thread.sleep( 250 );
							}

							last_socket_close_time = now;

							if (Logger.isEnabled())
								Logger.log(new LogEvent(LOGID,
										"PRUDPPacketReceiver: recycled UDP port " + port + " after close: ok=" + successful_accepts ));

							break;
						}

						failed_accepts++;

						if (Logger.isEnabled())
							Logger.log(new LogEvent(LOGID,
									"PRUDPPacketReceiver: receive failed on port " + port + ": ok=" + successful_accepts + ", fails=" + failed_accepts, e));


						if (( failed_accepts > 100 && successful_accepts == 0 ) || failed_accepts > 1000 ){

							Logger.logTextResource(new LogAlert(LogAlert.UNREPEATABLE,
									LogAlert.AT_ERROR, "Network.alert.acceptfail"), new String[] {
									"" + port, "UDP" });

								// break, sometimes get a screaming loop. e.g.
							/*
							[2:01:55]  DEBUG::Tue Dec 07 02:01:55 EST 2004
							[2:01:55]    java.net.SocketException: Socket operation on nonsocket: timeout in datagram socket peek
							[2:01:55]  	at java.net.PlainDatagramSocketImpl.peekData(Native Method)
							[2:01:55]  	at java.net.DatagramSocket.receive(Unknown Source)
							[2:01:55]  	at com.biglybt.core.tracker.server.impl.udp.TRTrackerServerUDP.recvLoop(TRTrackerServerUDP.java:118)
							[2:01:55]  	at com.biglybt.core.tracker.server.impl.udp.TRTrackerServerUDP$1.runSupport(TRTrackerServerUDP.java:90)
							[2:01:55]  	at com.biglybt.core.util.AEThread.run(AEThread.java:45)
							*/

							init_error	= e;

							failed	= true;
						}
					}
				}
			}
		}catch( Throwable e ){

			init_error	= e;

			if (!( e instanceof BindException && Constants.isWindowsVistaOrHigher )){

				Logger.logTextResource(new LogAlert(LogAlert.UNREPEATABLE,
						LogAlert.AT_ERROR, "Tracker.alert.listenfail"), new String[] { "UDP:"
						+ port });
			}

			Logger.log(new LogEvent(LOGID, "PRUDPPacketReceiver: "
					+ "DatagramSocket bind failed on port " + port + ", bind=" + target_bind_ip, e));

		}finally{

			init_sem.release();

			destroy_sem.releaseForever();

			if ( socket != null ){

				try{
					socket.close();

				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}
			}

			// make sure we destroy the delegate too if something happend
			PRUDPPacketHandlerImpl delegate = altProtocolDelegate;

			if ( delegate != null ){

				delegate.destroy();
			}

			NetworkAdmin.getSingleton().removePropertyChangeListener( prop_listener );
		}
	}

	protected boolean
	checkTimeouts()
	{
		long	now = SystemTime.getCurrentTime();

		List	timed_out = new ArrayList();

		boolean result;
		
		try{
			requests_mon.enter();

			result = destroyed;
			
			Iterator it = requests.values().iterator();

			while( it.hasNext()){

				PRUDPPacketHandlerRequestImpl	request = (PRUDPPacketHandlerRequestImpl)it.next();

				long	sent_time = request.getSendTime();

				if ( 	destroyed ||	// never going to get processed, treat as timeout
						(	sent_time != 0 &&
							now - sent_time >= request.getTimeout())){

					it.remove();

					stats.requestTimedOut();

					timed_out.add( request );
				}
			}
		}finally{

			requests_mon.exit();
		}

		for (int i=0;i<timed_out.size();i++){

			PRUDPPacketHandlerRequestImpl	request = (PRUDPPacketHandlerRequestImpl)timed_out.get(i);

			if ( TRACE_REQUESTS ){
				if (Logger.isEnabled())
					Logger.log(new LogEvent(LOGID, LogEvent.LT_ERROR,
							"PRUDPPacketHandler: request timeout"));
			}
				// don't change the text of this message, it's used elsewhere

			try{
				request.setException(new PRUDPPacketHandlerException("timed out"));

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
		
		return( result );
	}

	protected void
	process(
		DatagramPacket	dg_packet,
		long			receive_time )
	{
		try{

				// HACK alert. Due to the form of the tracker UDP protocol (no common
				// header for requests and replies) we enforce a rule. All connection ids
				// must have their MSB set. As requests always start with the action, which
				// always has the MSB clear, we can use this to differentiate.

			byte[]	packet_data = dg_packet.getData();
			int		packet_len	= dg_packet.getLength();

			// System.out.println( "received:" + packet_len );

			PRUDPPacket packet;

			boolean	request_packet;

			stats.packetReceived(packet_len);

			InetSocketAddress originator = (InetSocketAddress)dg_packet.getSocketAddress();

			if ( ( packet_data[0]&0x80 ) == 0 ){

				request_packet	= false;

				packet = PRUDPPacketReply.deserialiseReply(
					this, originator,
					new DataInputStream(new ByteArrayInputStream( packet_data, 0, packet_len)));

			}else{

				request_packet	= true;

				PRUDPPacketRequest request = PRUDPPacketRequest.deserialiseRequest(
						this,
						new DataInputStream(new ByteArrayInputStream( packet_data, 0, packet_len)));

				request.setReceiveTime( receive_time );

				packet = request;
			}

			packet.setSerialisedSize( packet_len );

			packet.setAddress( originator );

			if ( request_packet ){

				total_requests_received++;

				// System.out.println( "Incoming from " + dg_packet.getAddress());

				if ( TRACE_REQUESTS ){
					Logger.log(new LogEvent(LOGID,
							"PRUDPPacketHandler: request packet received: "
									+ packet.getString()));
				}

				if ( receive_delay > 0 ){

						// we take the processing offline so that these incoming requests don't
						// interfere with replies to outgoing requests

					try{
						recv_queue_mon.enter();

						if ( recv_queue_data_size > MAX_RECV_QUEUE_DATA_SIZE ){

							long	now = SystemTime.getCurrentTime();

							if ( now - last_error_report > 30000 ){

								last_error_report	= now;

								Debug.out( "Receive queue size limit exceeded (" +
											MAX_RECV_QUEUE_DATA_SIZE + "), dropping request packet [" +
											total_requests_received + "/" + total_requests_processed + ":" + total_replies + "]");
							}

						}else if ( receive_delay * recv_queue.size() > queued_request_timeout ){

								// by the time this request gets processed it'll have timed out
								// in the caller anyway, so discard it

							long	now = SystemTime.getCurrentTime();

							if ( now - last_error_report > 30000 ){

								last_error_report	= now;

								Debug.out( "Receive queue entry limit exceeded (" +
											recv_queue.size() + "), dropping request packet [" +
											total_requests_received + "/" + total_requests_processed + ":" + total_replies + "]");
							}

						}else{

							recv_queue.add( new Object[]{ packet, new Integer( dg_packet.getLength()) });

							recv_queue_data_size	+= dg_packet.getLength();

							recv_queue_sem.release();

							if ( recv_thread == null ){

								recv_thread =
									new AEThread2( "PRUDPPacketHandler:receiver" )
									{
										@Override
										public void
										run()
										{
											Average		request_receive_average = Average.getInstance( 1000, 10 );

											while( true ){

												try{
													recv_queue_sem.reserve();

													Object[]	data;

													try{
														recv_queue_mon.enter();

														data = (Object[])recv_queue.remove(0);
														
														total_requests_processed++;

														recv_queue_data_size -= ((Integer)data[1]).intValue();

														request_receive_average.addValue( 1 );

													}finally{

														recv_queue_mon.exit();
													}

													PRUDPPacketRequest	p = (PRUDPPacketRequest)data[0];

													PRUDPRequestHandler	handler = request_handler;

													if ( handler != null ){
														
														handler.process( p );

														if ( receive_delay > 0 ){

															int 	max_req_per_sec = 1000/receive_delay;

															long	request_per_sec = request_receive_average.getAverage();

															//System.out.println( request_per_sec + "/" + max_req_per_sec + " - " + recv_queue_data_size );

															if ( request_per_sec > max_req_per_sec ){

																Thread.sleep( receive_delay );

															}else{

																/* no point in this, just backs up the request queue. if we're really
																 * overloaded then we'll hit the above condition
																 * 
																long	delay = ( receive_delay * request_per_sec ) / max_req_per_sec;

																if ( delay >= 5 ){

																	Thread.sleep( delay );
																}
																*/

															}
														}
													}

												}catch( Throwable e ){

													Debug.printStackTrace(e);
												}
											}
										}
									};

								recv_thread.start();
							}
						}
					}finally{

						recv_queue_mon.exit();
					}
				}else{

					PRUDPRequestHandler	handler = request_handler;

					if ( handler != null ){

						handler.process( (PRUDPPacketRequest)packet );
					}
				}

			}else{

				total_replies++;

				if ( TRACE_REQUESTS ){
					Logger.log(new LogEvent(LOGID,
							"PRUDPPacketHandler: reply packet received: "
									+ packet.getString()));
				}

				PRUDPPacketHandlerRequestImpl	request;

				try{
					requests_mon.enter();

					if ( packet.hasContinuation()){

							// don't remove the request if there are more replies to come

						request = (PRUDPPacketHandlerRequestImpl)requests.get(new Integer(packet.getTransactionId()));

					}else{

						request = (PRUDPPacketHandlerRequestImpl)requests.remove(new Integer(packet.getTransactionId()));
					}

				}finally{

					requests_mon.exit();
				}

				if ( request == null ){

					if ( TRACE_REQUESTS ){
						Logger.log(new LogEvent(LOGID, LogEvent.LT_ERROR,
								"PRUDPPacketReceiver: unmatched reply received, discarding:"
										+ packet.getString()));
					}

				}else{

					request.setReply( packet, (InetSocketAddress)dg_packet.getSocketAddress(), receive_time );
				}
			}
		}catch( Throwable e ){

				// if someone's sending us junk we just log and continue

			if ( e instanceof IOException ){

					// generally uninteresting
				//e.printStackTrace();
			}else{

				Logger.log(new LogEvent(LOGID, "", e));
			}
		}
	}

	public PRUDPPacket
	sendAndReceive(
		PRUDPPacket				request_packet,
		InetSocketAddress		destination_address )

		throws PRUDPPacketHandlerException
	{
		return( sendAndReceive( null,request_packet, destination_address ));
	}

	@Override
	public PRUDPPacket
	sendAndReceive(
		PasswordAuthentication	auth,
		PRUDPPacket				request_packet,
		InetSocketAddress		destination_address )

		throws PRUDPPacketHandlerException
	{
		return( sendAndReceive( auth, request_packet, destination_address, PRUDPPacket.DEFAULT_UDP_TIMEOUT ));
	}

	@Override
	public PRUDPPacket
	sendAndReceive(
		PasswordAuthentication	auth,
		PRUDPPacket				request_packet,
		InetSocketAddress		destination_address,
		long					timeout )

		throws PRUDPPacketHandlerException
	{
		PRUDPPacketHandlerRequestImpl	request =
			sendAndReceive( auth, request_packet, destination_address, null, timeout, PRUDPPacketHandler.PRIORITY_MEDIUM );

		return( request.getReply());
	}

	@Override
	public PRUDPPacket
	sendAndReceive(
		PasswordAuthentication	auth,
		PRUDPPacket				request_packet,
		InetSocketAddress		destination_address,
		long					timeout,
		int						priority )

		throws PRUDPPacketHandlerException
	{
		PRUDPPacketHandlerRequestImpl	request =
			sendAndReceive( auth, request_packet, destination_address, null, timeout, priority );

		return( request.getReply());
	}

	@Override
	public void
	sendAndReceive(
		PRUDPPacket					request_packet,
		InetSocketAddress			destination_address,
		PRUDPPacketReceiver			receiver,
		long						timeout,
		int							priority )

		throws PRUDPPacketHandlerException
	{
		sendAndReceive( null, request_packet, destination_address, receiver, timeout, priority );
	}

	public PRUDPPacketHandlerRequestImpl
	sendAndReceive(
		PasswordAuthentication		auth,
		PRUDPPacket					request_packet,
		InetSocketAddress			destination_address,
		PRUDPPacketReceiver			receiver,
		long						timeout,
		int							priority )

		throws PRUDPPacketHandlerException
	{
		if ( socket == null ){

			if ( init_error != null ){

				throw( new PRUDPPacketHandlerException( "Transport unavailable", init_error ));
			}

			throw( new PRUDPPacketHandlerException( "Transport unavailable" ));
		}

		checkTargetAddress( destination_address );

		PRUDPPacketHandlerImpl delegate = altProtocolDelegate;

		if ( delegate != null && destination_address.getAddress().getClass().isInstance(delegate.explicit_bind_ip)){

			return delegate.sendAndReceive(auth, request_packet, destination_address, receiver, timeout, priority);
		}

		try{
			MyByteArrayOutputStream	baos = new MyByteArrayOutputStream( MAX_PACKET_SIZE );

			DataOutputStream os = new DataOutputStream( baos );

			request_packet.serialise(os);

			byte[]	_buffer = baos.getBuffer();
			int		_length	= baos.size();

			request_packet.setSerialisedSize( _length );

			if ( auth != null ){

				//<parg_home> so <new_packet> = <old_packet> + <user_padded_to_8_bytes> + <hash>
				//<parg_home> where <hash> = first 8 bytes of sha1(<old_packet> + <user_padded_to_8> + sha1(pass))
				//<XTF> Yes

				SHA1Hasher hasher = new SHA1Hasher();

				String	user_name 	= auth.getUserName();
				String	password	= new String(auth.getPassword());

				byte[]	sha1_password;

				if ( user_name.equals( "<internal>")){

					sha1_password = Base64.decode(password);

				}else{

					sha1_password = hasher.calculateHash(password.getBytes());
				}

				byte[]	user_bytes = new byte[8];

				Arrays.fill( user_bytes, (byte)0);

				for (int i=0;i<user_bytes.length&&i<user_name.length();i++){

					user_bytes[i] = (byte)user_name.charAt(i);
				}

				hasher = new SHA1Hasher();

				hasher.update( _buffer, 0, _length );
				hasher.update( user_bytes );
				hasher.update( sha1_password );

				byte[]	overall_hash = hasher.getDigest();

				//System.out.println("PRUDPHandler - auth = " + auth.getUserName() + "/" + new String(auth.getPassword()));

				baos.write( user_bytes );
				baos.write( overall_hash, 0, 8 );

				_buffer = baos.getBuffer();
				_length	= baos.size();
			}

			DatagramPacket dg_packet = new DatagramPacket(_buffer, _length, destination_address );

			PRUDPPacketHandlerRequestImpl	request = new PRUDPPacketHandlerRequestImpl( receiver, timeout );

			try{
				requests_mon.enter();

				if ( destroyed ){
					
					throw( new PRUDPPacketHandlerException( "Handler destroyed" ));
				}
				
				if ( requests.put( new Integer( request_packet.getTransactionId()), request ) != null ){
					
					Debug.out( "Duplicate request transaction id!!!!" );
				}

			}finally{

				requests_mon.exit();
			}

			try{
				// System.out.println( "Outgoing to " + dg_packet.getAddress());

				if ( send_delay > 0 && priority != PRUDPPacketHandler.PRIORITY_IMMEDIATE ){

					try{
						send_queue_mon.enter();

						if ( send_queue_data_size > MAX_SEND_QUEUE_DATA_SIZE ){

							request.sent();

								// synchronous write holding lock to block senders

							sendToSocket( dg_packet );

							stats.packetSent( _length );

							if ( TRACE_REQUESTS ){
								Logger.log(new LogEvent(LOGID,
										"PRUDPPacketHandler: request packet sent to "
												+ destination_address + ": "
												+ request_packet.getString()));
							}

							Thread.sleep( send_delay * 4 );

						}else{

							send_queue_data_size	+= dg_packet.getLength();

							send_queues[priority].add( new Object[]{ dg_packet, request });

							if ( TRACE_REQUESTS ){

								String	str = "";

								for (int i=0;i<send_queues.length;i++){
									str += (i==0?"":",") + send_queues[i].size();
								}
								System.out.println( "send queue sizes: " + str );
							}

							send_queue_sem.release();

							if ( send_thread == null ){

								send_thread =
									new AEThread2( "PRUDPPacketHandler:sender" )
									{
										@Override
										public void
										run()
										{
											int[]		consecutive_sends = new int[send_queues.length];

											while( true ){

												try{
													send_queue_sem.reserve();

													Object[]	data;
													int			selected_priority	= 0;

													try{
														send_queue_mon.enter();

															// invariant: at least one queue must have an entry

														for (int i=0;i<send_queues.length;i++){

															List	queue = send_queues[i];

															int	queue_size = queue.size();

															if ( queue_size > 0 ){

																selected_priority	= i;

																if ( 	consecutive_sends[i] >= 4 ||
																		(	i < send_queues.length - 1 &&
																			send_queues[i+1].size() - queue_size > 500 )){

																		// too many consecutive or too imbalanced, see if there are
																		// lower priority queues with entries

																	consecutive_sends[i]	= 0;

																}else{

																	consecutive_sends[i]++;

																	break;
																}
															}else{

																consecutive_sends[i]	= 0;
															}
														}

														data = (Object[])send_queues[selected_priority].remove(0);

														DatagramPacket				p	= (DatagramPacket)data[0];

															// mark as sent before sending in case send fails
															// and we then rely on timeout to pick this up

														send_queue_data_size	-= p.getLength();

													}finally{

														send_queue_mon.exit();
													}

													DatagramPacket					p	= (DatagramPacket)data[0];
													PRUDPPacketHandlerRequestImpl	r	= (PRUDPPacketHandlerRequestImpl)data[1];

													r.sent();

													sendToSocket( p );

													stats.packetSent( p.getLength() );

													if ( TRACE_REQUESTS ){
														Logger.log(new LogEvent(LOGID,
															"PRUDPPacketHandler: request packet sent to "
																	+ p.getAddress()));
													}

													long	delay = send_delay;

													if ( selected_priority == PRIORITY_HIGH ){

														delay	= delay/2;
													}

													Thread.sleep( delay );

												}catch( Throwable e ){
													// get occasional send fails, not very interesting
													Logger.log(
														new LogEvent(
															LOGID,
															LogEvent.LT_WARNING,
															"PRUDPPacketHandler: send failed to " + destination_address + ": " + Debug.getNestedExceptionMessage(e)));
												}
											}
										}
									};

									send_thread.start();
							}
						}
					}finally{

						send_queue_mon.exit();
					}
				}else{

					request.sent();

					sendToSocket( dg_packet );

					// System.out.println( "sent:" + buffer.length );

					stats.packetSent( _length );

					if ( TRACE_REQUESTS ){
						Logger.log(new LogEvent(LOGID, "PRUDPPacketHandler: "
								+ "request packet sent to " + destination_address + ": "
								+ request_packet.getString()));
					}
				}
					// if the send is ok then the request will be removed from the queue
					// either when a reply comes back or when it gets timed-out

				return( request );

			}catch( Throwable e ){

					// never got sent, remove it immediately

				try{
					requests_mon.enter();

					requests.remove( new Integer( request_packet.getTransactionId()));

				}finally{

					requests_mon.exit();
				}

				throw( e );
			}
		}catch( Throwable e ){

			// AMC: I've seen this in debug logs - just wonder where it's
			// coming from.

			if ( e instanceof NullPointerException ){

				Debug.out(e);
			}

			String msg = Debug.getNestedExceptionMessage(e);

			Logger.log(new LogEvent(LOGID,LogEvent.LT_ERROR,
					"PRUDPPacketHandler: sendAndReceive to " + destination_address + " failed: " + msg ));

			if (msg.contains("Invalid data length")){

				Debug.out( "packet=" + request_packet.getString() + ",auth=" + auth );

				Debug.out( e );
			}

			throw( new PRUDPPacketHandlerException( "PRUDPPacketHandler:sendAndReceive failed", e ));
		}
	}

	@Override
	public void
	send(
		PRUDPPacket				request_packet,
		InetSocketAddress		destination_address )

		throws PRUDPPacketHandlerException
	{
		if ( socket == null || socket.isClosed()){

			if ( init_error != null ){

				throw( new PRUDPPacketHandlerException( "Transport unavailable", init_error ));
			}

			throw( new PRUDPPacketHandlerException( "Transport unavailable" ));
		}

		checkTargetAddress( destination_address );

		PRUDPPacketHandlerImpl delegate = altProtocolDelegate;

		if ( delegate != null && destination_address.getAddress().getClass().isInstance(delegate.explicit_bind_ip)){

			delegate.send(request_packet, destination_address);

			return;
		}

		try{

			MyByteArrayOutputStream	baos = new MyByteArrayOutputStream( MAX_PACKET_SIZE );

			DataOutputStream os = new DataOutputStream( baos );

			request_packet.serialise(os);

			byte[]	_buffer = baos.getBuffer();
			int		_length	= baos.size();

			request_packet.setSerialisedSize( _length );

			DatagramPacket dg_packet = new DatagramPacket( _buffer, _length, destination_address );

			// System.out.println( "Outgoing to " + dg_packet.getAddress());

			if ( TRACE_REQUESTS ){
				Logger.log(new LogEvent(LOGID,
						"PRUDPPacketHandler: reply packet sent: "
								+ request_packet.getString()));
			}

			sendToSocket( dg_packet );

			stats.packetSent( _length );

				// this is a reply to a request, no time delays considered here

		}catch( Throwable e ){

			/*
			if ( e instanceof NoRouteToHostException || e instanceof BindException ){

			}else{

				String str = Debug.getNestedExceptionMessage( e ).toLowerCase( Locale.US );
				
				if ( str.contains( "unreachable" ) || str.contains( "network is down" )){
					
				}else{
				
					e.printStackTrace();
				}
			}
			*/
			
			Logger.log(new LogEvent(LOGID, LogEvent.LT_ERROR, "PRUDPPacketHandler: send to " + destination_address + " failed: " + Debug.getNestedExceptionMessage(e)));

			throw( new PRUDPPacketHandlerException( "PRUDPPacketHandler:send failed", e ));
		}
	}

	protected void
	checkTargetAddress(
		InetSocketAddress	address )

		throws PRUDPPacketHandlerException
	{
		if ( address.getPort() == 0 ){

			throw( new PRUDPPacketHandlerException( "Invalid port - 0" ));
		}

		if ( address.getAddress() == null ){

			throw( new PRUDPPacketHandlerException( "Unknown host " + address.getHostName()));
		}
	}

	@Override
	public void
	setDelays(
		int		_send_delay,
		int		_receive_delay,
		int		_queued_request_timeout )
	{
		send_delay				= _send_delay;
		receive_delay			= _receive_delay;

			// trim a bit off this limit to include processing time

		queued_request_timeout	= _queued_request_timeout-5000;

		if ( queued_request_timeout < 5000 ){

			queued_request_timeout = 5000;
		}

		PRUDPPacketHandlerImpl delegate = altProtocolDelegate;

		if ( delegate != null ){

			delegate.setDelays(_send_delay, _receive_delay, _queued_request_timeout);
		}
	}

	public long
	getSendQueueLength()
	{
		int	res = 0;
		for (int i=0;i<send_queues.length;i++){
			res += send_queues[i].size();
		}

		PRUDPPacketHandlerImpl delegate = altProtocolDelegate;

		if ( delegate != null ){

			res += delegate.getSendQueueLength();
		}

		return(res);
	}

	public long
	getReceiveQueueLength()
	{
		long size = recv_queue.size();

		PRUDPPacketHandlerImpl delegate = altProtocolDelegate;

		if ( delegate != null ){

			size += delegate.getReceiveQueueLength();
		}

		return size;
	}

	@Override
	public void
	primordialSend(
		byte[]				buffer,
		InetSocketAddress	target )

		throws PRUDPPacketHandlerException
	{
		if ( socket == null || socket.isClosed()){

			if ( init_error != null ){

				throw( new PRUDPPacketHandlerException( "Transport unavailable", init_error ));
			}

			throw( new PRUDPPacketHandlerException( "Transport unavailable" ));
		}

		checkTargetAddress( target );

		PRUDPPacketHandlerImpl delegate = altProtocolDelegate;

		if ( 	delegate != null &&
				target.getAddress().getClass().isInstance(delegate.explicit_bind_ip)){

			delegate.primordialSend(buffer, target);

			return;
		}

		try{
			DatagramPacket dg_packet = new DatagramPacket(buffer, buffer.length, target );

			// System.out.println( "Outgoing to " + dg_packet.getAddress());

			if ( TRACE_REQUESTS ){
				Logger.log(new LogEvent(LOGID,
						"PRUDPPacketHandler: reply packet sent: " + buffer.length + " to " + target ));
			}

			sendToSocket( dg_packet );

			stats.primordialPacketSent( buffer.length );

		}catch( Throwable e ){

			throw( new PRUDPPacketHandlerException( e.getMessage()));
		}
	}

	private void
	sendToSocket(
		DatagramPacket	p )

		throws IOException
	{
		if ( packet_transformer != null ){

			packet_transformer.transformSend( p );
		}
		
		socket.send( p );
	}

	private void
	receiveFromSocket(
		DatagramPacket	p )

		throws IOException
	{
		socket.receive( p );

		if ( packet_transformer != null ){

			packet_transformer.transformReceive( p );
		}
	}

	@Override
	public PRUDPPacketHandlerStats
	getStats()
	{
		return( stats );
	}

	@Override
	public void
	destroy()
	{
		destroyed	= true;

		PRUDPPacketHandlerImpl delegate = altProtocolDelegate;

		if ( delegate != null ){

			delegate.destroy();
		}

		destroy_sem.reserve();
	}


	@Override
	public PRUDPPacketHandler
	openSession(
		InetSocketAddress		target )

		throws PRUDPPacketHandlerException
	{
		if ( use_socks ){

			return( new PRUDPPacketHandlerSocks( target ));

		}else{

			return( this );
		}
	}

	@Override
	public void
	closeSession()

		throws PRUDPPacketHandlerException
	{
	}

	private static class
	MyByteArrayOutputStream
		extends ByteArrayOutputStream
	{
		private
		MyByteArrayOutputStream(
			int	size )
		{
			super( size );
		}

		private byte[]
		getBuffer()
		{
			return( buf );
		}
	}

	protected interface
	PacketTransformer
	{
		public void
		transformSend(
			DatagramPacket	packet );

		public void
		transformReceive(
			DatagramPacket	packet );

	}
}
