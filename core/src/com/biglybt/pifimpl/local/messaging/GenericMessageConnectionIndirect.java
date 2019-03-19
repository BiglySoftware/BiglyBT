/*
 * Created on 10 Aug 2006
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

package com.biglybt.pifimpl.local.messaging;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.*;

import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.nat.NATTraverser;
import com.biglybt.core.util.*;
import com.biglybt.pif.messaging.MessageException;
import com.biglybt.pif.messaging.generic.GenericMessageConnection;
import com.biglybt.pif.messaging.generic.GenericMessageEndpoint;
import com.biglybt.pif.messaging.generic.GenericMessageHandler;
import com.biglybt.pif.messaging.generic.GenericMessageStartpoint;
import com.biglybt.pif.network.Connection;
import com.biglybt.pif.network.RateLimiter;
import com.biglybt.pif.utils.PooledByteBuffer;


public class
GenericMessageConnectionIndirect
	implements GenericMessageConnectionAdapter
{
	private static final LogIDs LOGID = LogIDs.NET;

	private static final boolean	TRACE	= false;

	static{

		if ( TRACE ){
			System.out.println( "**** GenericMessageConnectionIndirect: TRACE on **** " );
		}
	}

	public static final int MAX_MESSAGE_SIZE	= 32*1024;

	private static final int MESSAGE_TYPE_CONNECT		= 1;
	private static final int MESSAGE_TYPE_ERROR			= 2;
	private static final int MESSAGE_TYPE_DATA			= 3;
	private static final int MESSAGE_TYPE_DISCONNECT	= 4;

	private static final int TICK_PERIOD 				= 5000;

	private static final int KEEP_ALIVE_CHECK_PERIOD	= 5000;
	private static final int KEEP_ALIVE_MIN				= 10000;
	private static final int STATS_PERIOD				= 60000;

	private static final int KEEP_ALIVE_CHECK_TICKS	= KEEP_ALIVE_CHECK_PERIOD / TICK_PERIOD;
	private static final int STATS_TICKS			= STATS_PERIOD / TICK_PERIOD;

	private static final int MAX_REMOTE_CONNECTIONS			= 1024;
	private static final int MAX_REMOTE_CONNECTIONS_PER_IP	= 32;

	private static long	connection_id_next	= RandomUtils.nextLong();


	private static Map	local_connections 	= new HashMap();
	private static Map	remote_connections 	= new HashMap();

	private static ThreadPool	keep_alive_pool = new ThreadPool( "GenericMessageConnectionIndirect:keepAlive", 8, true );

	static{

			// there are two reasons for timers
			//     1) to check for dead connections (send keepalive/check timeouts)
			//     2) the connection is one-sided so if the responder sends an unsolicited message it
			//        is queued and only picked up on a periodic ping by the initiator

		SimpleTimer.addPeriodicEvent(
			"DDBTorrent:timeout2",
			TICK_PERIOD,
			new TimerEventPerformer()
			{
				private int	tick_count = 0;

				@Override
				public void
				perform(
					TimerEvent	event )
				{
					tick_count++;

					if ( tick_count % STATS_TICKS == 0 ){

						int	local_total;
						int remote_total;

						if ( Logger.isEnabled()){

							synchronized( local_connections ){

								local_total = local_connections.size();
							}

							synchronized( remote_connections ){

								remote_total = remote_connections.size();
							}

							if  ( local_total + remote_total > 0 ){

								log( "local=" + local_total + " [" + getLocalConnectionStatus() + "], remote=" + remote_total + " [" + getRemoteConnectionStatus() + "]" );
							}
						}
					}

					if ( tick_count % KEEP_ALIVE_CHECK_TICKS == 0 ){

						synchronized( local_connections ){

							Iterator	it = local_connections.values().iterator();

							while( it.hasNext()){

								final GenericMessageConnectionIndirect con = (GenericMessageConnectionIndirect)it.next();

								if ( con.prepareForKeepAlive( false )){

									keep_alive_pool.run(
										new AERunnable()
										{
											@Override
											public void
											runSupport()
											{
												con.keepAlive();
											}
										});
								}
							}
						}

						long	now = SystemTime.getCurrentTime();

						synchronized( remote_connections ){

							if ( remote_connections.size() > 0 ){

									// copy the connections here as we can recursively modify  the set when closing

								Iterator	it = new ArrayList( remote_connections.values()).iterator();

								while( it.hasNext()){

									GenericMessageConnectionIndirect con = (GenericMessageConnectionIndirect)it.next();

									long	last_receive = con.getLastMessageReceivedTime();

									if ( now - last_receive > KEEP_ALIVE_MIN * 3 ){

										try{
											con.close( new Throwable( "Timeout" ));

										}catch( Throwable e ){

											Debug.printStackTrace(e);
										}
									}
								}
							}
						}
					}
				}
			});
	}

	protected static Map
	receive(
		MessageManagerImpl		message_manager,
		InetSocketAddress		originator,
		Map						message )
	{
		if (TRACE ){
			System.out.println( "receive:" + originator + "/" + message );
		}
			// if this purely a NAT traversal request then bail out

		if ( !message.containsKey( "type" )){

			return( null );
		}

		int	type = ((Long)message.get("type")).intValue();

		if ( type == MESSAGE_TYPE_CONNECT ){

			String	msg_id 		= new String((byte[])message.get( "msg_id" ));
			String	msg_desc 	= new String((byte[])message.get( "msg_desc" ));

			GenericMessageEndpointImpl	endpoint = new GenericMessageEndpointImpl( originator );

			endpoint.addUDP( originator );

			GenericMessageHandler	handler = message_manager.getHandler( msg_id );

			if ( handler == null ){

				Debug.out( "No message handler registered for '" + msg_id + "'" );

				return( null );
			}

			try{
				Long	con_id;

				synchronized( remote_connections ){

					if ( remote_connections.size() >= MAX_REMOTE_CONNECTIONS ){

						Debug.out( "Maximum remote connections exceeded - request from " + originator + " denied [" + getRemoteConnectionStatus() + "]" );

						return( null );
					}

					int	num_from_this_ip = 0;

					Iterator	it = remote_connections.values().iterator();

					while( it.hasNext()){

						GenericMessageConnectionIndirect con = (GenericMessageConnectionIndirect)it.next();

						if ( con.getEndpoint().getNotionalAddress().getAddress().equals( originator.getAddress())){

							num_from_this_ip++;
						}
					}

					if ( num_from_this_ip >= MAX_REMOTE_CONNECTIONS_PER_IP ){

						Debug.out( "Maximum remote connections per-ip exceeded - request from " + originator + " denied [" + getRemoteConnectionStatus() + "]" );

						return( null );

					}
					con_id = new Long( connection_id_next++ );
				}

				GenericMessageConnectionIndirect indirect_connection =
					new GenericMessageConnectionIndirect(
							message_manager, msg_id, msg_desc, endpoint, con_id.longValue());

				GenericMessageConnectionImpl new_connection = new GenericMessageConnectionImpl( message_manager, indirect_connection );

				if ( handler.accept( new_connection )){

					new_connection.accepted();

					synchronized( remote_connections ){

						remote_connections.put( con_id, indirect_connection );
					}

					List	replies = indirect_connection.receive((List)message.get( "data" ));

					Map	reply = new HashMap();

					reply.put( "type", new Long( MESSAGE_TYPE_CONNECT ));
					reply.put( "con_id", con_id );
					reply.put( "data", replies );

					return( reply );

				}else{

					return( null );
				}

			}catch( MessageException e ){

				Debug.out( "Error accepting message", e);

				return( null );
			}

		}else if ( type == MESSAGE_TYPE_DATA ){

			Long	con_id = (Long)message.get( "con_id" );

			GenericMessageConnectionIndirect indirect_connection;

			synchronized( remote_connections ){

				indirect_connection = (GenericMessageConnectionIndirect)remote_connections.get( con_id );
			}

			if ( indirect_connection == null ){

				return( null );
			}

			Map	reply = new HashMap();

			if ( indirect_connection.isClosed()){

				reply.put( "type", new Long( MESSAGE_TYPE_DISCONNECT ));

			}else{

				List replies = indirect_connection.receive((List)message.get( "data" ));

				reply.put( "type", new Long( MESSAGE_TYPE_DATA ));
				reply.put( "data", replies );

				if ( indirect_connection.receiveIncomplete()){

					reply.put( "more_data", new Long(1));
				}
			}

			return( reply );

		}else{

				// error or disconnect

			Long	con_id = (Long)message.get( "con_id" );

			GenericMessageConnectionIndirect indirect_connection;

			synchronized( remote_connections ){

				indirect_connection = (GenericMessageConnectionIndirect)remote_connections.get( con_id );
			}

			if ( indirect_connection != null ){

				try{
					indirect_connection.close( new Throwable( "Remote closed connection" ) );

				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}
			}

			return( null );
		}
	}

	protected static String
	getRemoteConnectionStatus()
	{
		return( getConnectionStatus( remote_connections ));
	}

	protected static String
	getLocalConnectionStatus()
	{
		return( getConnectionStatus( local_connections ));
	}

	protected static String
	getConnectionStatus(
		Map		connections )
	{
		Map totals = new HashMap();

		synchronized( connections ){

			Iterator	it = connections.values().iterator();

			while( it.hasNext()){

				GenericMessageConnectionIndirect con = (GenericMessageConnectionIndirect)it.next();

				InetAddress	originator = con.getEndpoint().getNotionalAddress().getAddress();

				Integer	i = (Integer)totals.get( originator );

				if ( i == null ){

					i = new Integer(1);

				}else{

					i = new Integer(i.intValue() + 1 );
				}

				totals.put( originator, i );
			}
		}

		String	str = "";

		Iterator it = totals.entrySet().iterator();

		while( it.hasNext()){

			Map.Entry entry = (Map.Entry)it.next();

			str += (str.length()==0?"":",") + entry.getKey() + ":" + entry.getValue();
		}

		return( str );
	}




	private MessageManagerImpl			message_manager;
	private String						msg_id;
	private String						msg_desc;
	private GenericMessageEndpoint		endpoint;

	private NATTraverser					nat_traverser;
	private GenericMessageConnectionImpl	owner;

	private	InetSocketAddress		rendezvous;
	private InetSocketAddress		target;

	private long					connection_id;
	private boolean					incoming;
	private boolean					closed;

	private LinkedList<byte[]>	send_queue		= new LinkedList<>();

	private AESemaphore	send_queue_sem	= new AESemaphore( "GenericMessageConnectionIndirect:sendq" );

	private volatile long		last_message_sent;
	private volatile long		last_message_received;
	private volatile boolean	keep_alive_in_progress;

	protected
	GenericMessageConnectionIndirect(
		MessageManagerImpl			_message_manager,
		String						_msg_id,
		String						_msg_desc,
		GenericMessageEndpoint		_endpoint,
		InetSocketAddress			_rendezvous,
		InetSocketAddress			_target )
	{
			// outgoing

		message_manager	= _message_manager;
		msg_id			= _msg_id;
		msg_desc		= _msg_desc;
		endpoint		= _endpoint;
		rendezvous		= _rendezvous;
		target			= _target;

		nat_traverser = message_manager.getNATTraverser();

		log( "outgoing connection to " + endpoint.getNotionalAddress());
	}

	protected
	GenericMessageConnectionIndirect(
		MessageManagerImpl			_message_manager,
		String						_msg_id,
		String						_msg_desc,
		GenericMessageEndpoint		_endpoint,
		long						_connection_id )
	{
			// incoming

		message_manager	= _message_manager;
		msg_id			= _msg_id;
		msg_desc		= _msg_desc;
		endpoint		= _endpoint;
		connection_id	= _connection_id;

		incoming		= true;

		last_message_received	= SystemTime.getCurrentTime();

		if ( TRACE ){
			trace( "inbound connect from " + endpoint.getNotionalAddress());
		}

		log( "incoming connection from " + endpoint.getNotionalAddress());
	}

	@Override
	public void
	setOwner(
		GenericMessageConnectionImpl	_owner )
	{
		owner	= _owner;
	}

	@Override
	public int
	getMaximumMessageSize()
	{
		return( MAX_MESSAGE_SIZE );
	}

	@Override
	public String
	getType()
	{
		return( "Tunnel" );
	}

	@Override
	public int
	getTransportType()
	{
		return( GenericMessageConnection.TT_INDIRECT );
	}

	public long
	getLastMessageReceivedTime()
	{
		long	now = SystemTime.getCurrentTime();

		if ( now < last_message_received ){

			last_message_received = now;
		}

		return( last_message_received );
	}

	@Override
	public GenericMessageEndpoint
	getEndpoint()
	{
		return( endpoint );
	}

	@Override
	public GenericMessageStartpoint 
	getStartpoint()
	{
		return( null );
	}

	@Override
	public Connection 
	getConnection()
	{
		return( null );
	}
	
	@Override
	public void
	addInboundRateLimiter(
		RateLimiter		limiter )
	{
		// no support for this here
	}

	@Override
	public void
	removeInboundRateLimiter(
		RateLimiter		limiter )
	{
		// no support for this here
	}

	@Override
	public void
	addOutboundRateLimiter(
		RateLimiter		limiter )
	{
		// no support for this here
	}

	@Override
	public void
	removeOutboundRateLimiter(
		RateLimiter		limiter )
	{
		// no support for this here
	}


	@Override
	public void
	connect(
		ByteBuffer			initial_data,
		ConnectionListener	listener )
	{
		if ( TRACE ){
			trace( "outbound connect to " + endpoint.getNotionalAddress());
		}

		try{
			Map	message = new HashMap();

			byte[]	initial_data_bytes = new byte[ initial_data.remaining()];

			initial_data.get( initial_data_bytes );

			List	initial_messages = new ArrayList();

			initial_messages.add( initial_data_bytes );

			message.put( "type", new Long( MESSAGE_TYPE_CONNECT ));
			message.put( "msg_id", msg_id );
			message.put( "msg_desc", msg_desc );
			message.put( "data", initial_messages );

			Map reply = nat_traverser.sendMessage( message_manager, rendezvous, target, message );

			last_message_sent = SystemTime.getCurrentTime();

			if ( reply == null || !reply.containsKey( "type") ){

				listener.connectFailure( new Throwable( "Indirect connect failed (response=" + reply + ")" ));

			}else{

				int	reply_type = ((Long)reply.get( "type" )).intValue();

				if ( reply_type == MESSAGE_TYPE_ERROR ){

					listener.connectFailure( new Throwable( new String((byte[])reply.get( "error" ))));

				}else if ( reply_type == MESSAGE_TYPE_DISCONNECT ){

					listener.connectFailure( new Throwable( "Disconnected" ));

				}else if ( reply_type == MESSAGE_TYPE_CONNECT ){

					connection_id = ((Long)reply.get( "con_id" )).longValue();

					synchronized( local_connections ){

						local_connections.put( new Long( connection_id ), this );
					}

					listener.connectSuccess();

					List<byte[]>	replies = (List<byte[]>)reply.get( "data" );

					for (int i=0;i<replies.size();i++){

						owner.receive( new GenericMessage(msg_id, msg_desc, new DirectByteBuffer(ByteBuffer.wrap(replies.get(i))), false ));
					}

				}else{

					Debug.out( "Unexpected reply type - " + reply_type );

					listener.connectFailure( new Throwable( "Unexpected reply type - " + reply_type ));
				}
			}
		}catch( Throwable e ){

			listener.connectFailure( e );
		}
	}

	@Override
	public void
	accepted()
	{
	}

	@Override
	public void
	send(
		PooledByteBuffer			pbb )

		throws MessageException
	{
		byte[]	bytes = pbb.toByteArray();

		if ( TRACE ){
			trace( "send " +  bytes.length );
		}

		if ( incoming ){

			synchronized( send_queue ){

				if ( send_queue.size() > 64 ){

					throw( new MessageException( "Send queue limit exceeded" ));
				}

				send_queue.add( bytes );
			}

			send_queue_sem.release();

		}else{

			List	messages = new ArrayList();

			messages.add( bytes );

			send( messages );
		}
	}

	protected void
	send(
		List	messages )
	{
		if ( TRACE ){
			trace( "    send " + messages );
		}

		try{
			Map	message = new HashMap();

			message.put( "con_id", new Long( connection_id ));
			message.put( "type", new Long( MESSAGE_TYPE_DATA ));
			message.put( "data", messages );

			Map reply = nat_traverser.sendMessage( message_manager, rendezvous, target, message );

			last_message_sent = SystemTime.getCurrentTime();

			if ( reply == null || !reply.containsKey( "type")){

				owner.reportFailed( new Throwable( "Indirect message send failed (response=" + reply + ")" ));

			}else{

				int	reply_type = ((Long)reply.get( "type" )).intValue();

				if ( reply_type == MESSAGE_TYPE_ERROR ){

					owner.reportFailed( new Throwable( new String((byte[])reply.get( "error" ))));

				}else if ( reply_type == MESSAGE_TYPE_DATA ){

					List<byte[]>	replies = (List<byte[]>)reply.get( "data" );

					for (int i=0;i<replies.size();i++){

						owner.receive( new GenericMessage(msg_id, msg_desc, new DirectByteBuffer(ByteBuffer.wrap(replies.get(i))), false ));
					}

						// if there's more data queued force a keep alive to pick it up but delay
						// a little to give the rendezvous a breather

					if ( reply.get( "more_data" ) != null ){

						if ( TRACE ){
							trace( "    received 'more to come'" );
						}

						new DelayedEvent(
							"GenMsg:kap",
							500,
							new AERunnable()
							{
								@Override
								public void
								runSupport()
								{
									if ( prepareForKeepAlive( true )){

										keep_alive_pool.run(
												new AERunnable()
												{
													@Override
													public void
													runSupport()
													{
														GenericMessageConnectionIndirect.this.keepAlive();
													}
												});
									}
								}
							});
					}
				}else if ( reply_type == MESSAGE_TYPE_DISCONNECT ){

					owner.reportFailed( new Throwable( "Disconnected" ));
				}
			}
		}catch( Throwable e ){

			owner.reportFailed( e );
		}
	}

	protected List<byte[]>
	receive(
		List<byte[]>		messages )
	{
		if ( TRACE ){
			trace( "receive: " + messages );
		}

		last_message_received	= SystemTime.getCurrentTime();

		for (int i=0;i<messages.size();i++){

			owner.receive( new GenericMessage(msg_id, msg_desc, new DirectByteBuffer(ByteBuffer.wrap(messages.get(i))), false ));
		}

		List<byte[]>	reply = new ArrayList<>();

			// hang around a bit to see if we can piggyback a reply

		if ( send_queue_sem.reserve( 2500 )){

				// give a little more time in case async > 1 message is being queued

			try{
				Thread.sleep(250);

			}catch( Throwable e ){
			}

			int	max 	= getMaximumMessageSize();
			int	total 	= 0;

			synchronized( send_queue ){

				while( send_queue.size() > 0 ){

					byte[]	data = send_queue.getFirst();

					if ( total > 0 && total + data.length > max ){

						break;
					}

					reply.add( send_queue.removeFirst());

					total += data.length;
				}


				if ( TRACE ){
					trace( "    messages returned = " + reply.size() + " (" + total + "), more=" + (send_queue.size() > 0 ));
				}
			}

			if ( reply.size() == 0 ){

					// another thread stole our reply, release semaphore we grabbed above that
					// doesn't have a matching queue removal

				send_queue_sem.release();

			}else{
					// grab sems for any entries other than the initial one grabbed above

				for (int i=1;i<reply.size();i++){

					send_queue_sem.reserve();
				}
			}
		}

		return( reply );
	}

	protected boolean
	receiveIncomplete()
	{
		synchronized( send_queue ){

			return( send_queue.size() > 0 );
		}
	}

	@Override
	public void
	close()

		throws MessageException
	{
		close( null );
	}

	protected void
	close(
		Throwable	close_cause )

		throws MessageException
	{
		if ( closed ){

			return;
		}

		if ( TRACE ){
			if ( close_cause == null ){
				trace( "close[local]" );
			}else{
				trace( "close[" + close_cause.getMessage() + "]" );
			}
		}

		log( "connection to " + endpoint.getNotionalAddress() + " closed" + (close_cause==null?"":(" (" + close_cause + ")")));

		try{
			closed	= true;

			if ( incoming ){

				synchronized( remote_connections ){

					remote_connections.remove( new Long( connection_id ));
				}
			}else{


				synchronized( local_connections ){

					local_connections.remove( new Long( connection_id ));
				}

				Map	message = new HashMap();

				message.put( "con_id", new Long( connection_id ));
				message.put( "type", new Long( MESSAGE_TYPE_DISCONNECT ));

				try{
					nat_traverser.sendMessage( message_manager, rendezvous, target, message );

					last_message_sent = SystemTime.getCurrentTime();

				}catch( Throwable e ){

					throw( new MessageException( "Close operation failed", e ));
				}
			}
		}finally{

			if ( close_cause != null ){

				owner.reportFailed( close_cause );
			}
		}
	}

	protected boolean
	isClosed()
	{
		return( closed );
	}

	protected boolean
	prepareForKeepAlive(
		boolean	force )
	{
		if ( keep_alive_in_progress ){

			return( false );
		}

		long	now = SystemTime.getCurrentTime();

		if ( force || now < last_message_sent || now - last_message_sent > KEEP_ALIVE_MIN ){

			keep_alive_in_progress = true;

			return( true );
		}

		return( false );
	}

	protected void
	keepAlive()
	{
		if (TRACE ){
			trace( "keepAlive" );
		}

		try{

			send( new ArrayList());

		}finally{

			keep_alive_in_progress	= false;
		}
	}

	protected static void
	log(
		String	str )
	{
		if ( Logger.isEnabled()){

			Logger.log(new LogEvent(LOGID, "GenericMessaging (indirect):" + str ));
		}
	}

	protected void
	trace(
		String	str )
	{
		if ( TRACE ){
			System.out.println( "GMCI[" +(incoming?"R":"L") + "/" + connection_id + "] " + str );
		}
	}
}
