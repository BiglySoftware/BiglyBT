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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.*;

import com.biglybt.core.networkmanager.*;
import com.biglybt.core.peermanager.PeerManager;
import com.biglybt.core.peermanager.PeerManagerRegistration;
import com.biglybt.core.peermanager.PeerManagerRegistrationAdapter;
import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.messaging.bittorrent.*;
import com.biglybt.core.proxy.AEProxyFactory;
import com.biglybt.core.util.*;

public class
NetStatusProtocolTesterBT
{
	private static Random	random = RandomUtils.SECURE_RANDOM;

	private NetStatusProtocolTester			tester;
	private boolean							test_initiator;

	private CopyOnWriteList					listeners	= new CopyOnWriteList();


	private byte[]		my_hash;
	private byte[]		peer_id;

	private InetAddress	explicit_bind;
	
	private PeerManagerRegistration		pm_reg;

	private long		start_time	= SystemTime.getCurrentTime();

	private List		sessions	= new ArrayList();
	private int			session_id_next;

	private int			outbound_attempts	= 0;
	private int			outbound_connects	= 0;
	private int			inbound_connects	= 0;

	private boolean		outbound_connections_complete;
	private AESemaphore	completion_sem = new AESemaphore( "Completion" );


	private boolean		destroyed;

	protected
	NetStatusProtocolTesterBT(
		NetStatusProtocolTester			_tester,
		boolean							_test_initiator )
	{
		tester			= _tester;
		test_initiator	= _test_initiator;
	}

	public void
	setBindIP(
		InetAddress	a )
	{
		log( "Bind IP set to " + a );
		
		explicit_bind = a;
	}
	
	protected void
	start()
	{
		my_hash = new byte[20];

		random.nextBytes( my_hash );

		peer_id = new byte[20];

		random.nextBytes( peer_id );


		pm_reg = PeerManager.getSingleton().registerLegacyManager(
			new HashWrapper( my_hash ),
			new PeerManagerRegistrationAdapter()
			{
				@Override
				public byte[][]
	          	getSecrets()
				{
					return( new byte[][]{ my_hash });
				}

				@Override
				public byte[] 
				getHashOverride()
				{
					return( null );
				}
				
				@Override
				public int getNbPieces(){
					return 1;
				}
				
				@Override
				public int 
				getExtendedMessagingMode()
				{
					return( BTHandshake.AZ_RESERVED_MODE );
				}
				
				@Override
				public byte[] 
				getPeerID()
				{
					return( peer_id );
				}
				
				@Override
				public int 
				getHashOverrideLocalPort(
					boolean only_if_allocated )
				{
					return( 0 );
				}
				
	          	@Override
		        public boolean
	          	manualRoute(
	          		NetworkConnection		connection )
	          	{
	          		log( "Got incoming connection from " + connection.getEndpoint().getNotionalAddress());

	          		new Session( connection, null );

	          		return( true );
	          	}

	          	@Override
		        public boolean
	          	isPeerSourceEnabled(
	          		String					peer_source )
	          	{
	          		return( true );
	          	}

	          	@Override
		        public int
	          	activateRequest(
	          		InetSocketAddress		remote_address )
	          	{
	          		return( AT_ACCEPTED );
	          	}

	          	@Override
		        public void
	          	deactivateRequest(
	          		InetSocketAddress		remote_address )
	          	{
	          	}

	          	@Override
		        public String
	          	getDescription()
	          	{
	          		return( "NetStatusPlugin - router" );
	          	}

			});

		log( "Incoming routing established for " + ByteFormatter.encodeString( my_hash ));
	}

	protected byte[]
	getServerHash()
	{
		return( my_hash );
	}

	protected long
	getStartTime(
		long	now )
	{
		if ( now < start_time ){

			start_time = now;
		}

		return( start_time );
	}

	protected void
	testOutbound(
		InetSocketAddress		address,
		final byte[]			their_hash,
		boolean					use_crypto )
	{
			// regardless of the caller's desires, override with crypto if we're using it

		if ( NetworkManager.getCryptoRequired( NetworkManager.CRYPTO_OVERRIDE_NONE )){

			use_crypto = true;
		}

		log( "Making outbound connection to " + address );

		synchronized( this ){

			outbound_attempts++;
		}

		boolean	allow_fallback	= false;

		ProtocolEndpoint	pe = ProtocolEndpointFactory.createEndpoint( ProtocolEndpoint.PROTOCOL_TCP, address );

		ConnectionEndpoint connection_endpoint	= new ConnectionEndpoint( address );

		connection_endpoint.addProtocol( pe );

		final NetworkConnection connection =
			NetworkManager.getSingleton().createConnection(
					connection_endpoint,
					new BTMessageEncoder(),
					new BTMessageDecoder(),
					use_crypto,
					allow_fallback,
					new byte[][]{ their_hash });

		new Session( connection, their_hash );
	}

	public void
	destroy()
	{
		List	to_close	= new ArrayList();

		synchronized( sessions ){

			if ( destroyed ){

				return;
			}

			destroyed = true;

			to_close.addAll( sessions );

			sessions.clear();
		}

		for (int i=0;i<to_close.size();i++){

			Session session = (Session)to_close.get(i);

			session.close();
		}

		pm_reg.unregister();

		checkCompletion();

		log( "Incoming routing destroyed for " + ByteFormatter.encodeString( my_hash ));
	}

	protected boolean
	isDestroyed()
	{
		return( destroyed );
	}

	public void
	setOutboundConnectionsComplete()
	{
		synchronized( sessions ){

			outbound_connections_complete	= true;
		}

		checkCompletion();
	}

	protected void
	checkCompletion()
	{
		boolean	inform = false;

		synchronized( sessions ){

			if ( completion_sem.isReleasedForever()){

				return;
			}

			if ( 	destroyed ||
					( outbound_connections_complete && sessions.size() == 0 )){

				inform = true;

				completion_sem.releaseForever();
			}
		}

		if ( inform ){

			Iterator it = listeners.iterator();

			while( it.hasNext()){

				try{
					((NetStatusProtocolTesterListener)it.next()).complete( this );

				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}
			}
		}
	}

	public boolean
	waitForCompletion(
		long		max_millis )
	{
		if ( max_millis == 0 ){

			completion_sem.reserve();

			return( true );

		}else{

			return( completion_sem.reserve( max_millis ));
		}
	}

	public void
	addListener(
		NetStatusProtocolTesterListener		l )
	{
		listeners.add( l );
	}

	public void
	removeListener(
		NetStatusProtocolTesterListener		l )
	{
		listeners.remove( l );
	}

	public int
	getOutboundConnects()
	{
		return( outbound_connects );
	}

	public int
	getInboundConnects()
	{
		return( inbound_connects );
	}

	public String
	getStatus()
	{
		return( "sessions=" + sessions.size() +
					", out_attempts=" + outbound_attempts +
					", out_connect=" + outbound_connects +
					", in_connect=" + inbound_connects );
	}

	protected void
	log(
		String		str )
	{
		log( str, false );
	}

	protected void
	log(
		String		str,
		boolean 	detailed )
	{
		Iterator it = listeners.iterator();

		while( it.hasNext()){

			try{
				((NetStatusProtocolTesterListener)it.next()).log( str, detailed );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}

		tester.log( str );
	}

	protected void
	logError(
		String	str )
	{
		Iterator it = listeners.iterator();

		while( it.hasNext()){

			try{
				((NetStatusProtocolTesterListener)it.next()).logError( str );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}

		tester.log( str );
	}

	protected void
	logError(
		String		str,
		Throwable	e )
	{
		Iterator it = listeners.iterator();

		while( it.hasNext()){

			try{
				((NetStatusProtocolTesterListener)it.next()).logError( str, e );

			}catch( Throwable f ){

				Debug.printStackTrace(f);
			}
		}

		tester.log( str, e );
	}

	public class
	Session
	{
		private NetworkConnection		connection;
		private int						session_id;
		private boolean					initiator;
		private byte[]					info_hash;

		private boolean 	handshake_sent;
		private boolean		handshake_received;

		private boolean 	bitfield_sent;
		private boolean		bitfield_received;

		private int			num_pieces;
		private boolean		is_seed;
		private Set			missing_pieces = new HashSet();

		private boolean		connected;
		private boolean		closing;
		private boolean		closed;

		protected
		Session(
			NetworkConnection		_connection,
			byte[]					_info_hash )
		{
			connection	= _connection;
			info_hash	= _info_hash;

			initiator 	= info_hash != null;

			synchronized( sessions ){

				session_id_next++;

				session_id = session_id_next;

				if ( destroyed ){

					log( "Already destroyed" );

					close();

					return;

				}else{

						// if we're a responder then we limit connections as we should only
						// receive 1 (be generous, give them 3 in case we decide to reconnect)

					if ( !( test_initiator || initiator )){

						int responder_sessions = 0;

						for (int i=0;i<sessions.size();i++){

							Session	existing_session = (Session)sessions.get(i);

							if ( !existing_session.isInitiator()){

								responder_sessions++;
							}
						}

						if ( responder_sessions >= 2 ){

							log( "Too many responder sessions" );

							close();

							return;
						}
					}

					sessions.add( this );

					is_seed = initiator && sessions.size()%2 == 0;
				}
			}

			Iterator it = listeners.iterator();

			while( it.hasNext()){

				try{
					((NetStatusProtocolTesterListener)it.next()).sessionAdded( this );

				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}
			}

			connection.connect(
					ProtocolEndpoint.CONNECT_PRIORITY_MEDIUM,
					new NetworkConnection.ConnectionListener()
					{
						final String	type = initiator?"Outbound":"Inbound";

						@Override
						public int
						connectStarted(
							int default_connect_timeout )
						{
							log( type + " connect start", true );

							return( default_connect_timeout );
						}

						@Override
						public final void
						connectSuccess(
							ByteBuffer remaining_initial_data )
						{
							log( type + " connect success, protocol=" + connection.getTransport().getProtocol(), true );

							connected	= true;

							synchronized( NetStatusProtocolTesterBT.this ){

								if ( initiator ){

									outbound_connects++;

								}else{

									inbound_connects++;
								}
							}

							connected();
						}

						@Override
						public final void
						connectFailure(
							Throwable e )
						{
							if ( !closing ){

								logError( type + " connect failed: " + Debug.getNestedExceptionMessage( e ));
							}

							close();
						}

						@Override
						public final void
						exceptionThrown(
							Throwable e )
						{
							if ( !closing ){

								logError( type + " connection failure", e );
							}

							close();
						}

						@Override
						public Object
						getConnectionProperty(
							String property_name )
						{
							if ( property_name == AEProxyFactory.PO_EXPLICIT_BIND ){
								
								return( explicit_bind );
								
							}else{
								
								return( null );
							}
						}

						@Override
						public String
						getDescription()
						{
							return( "NetStatusPlugin - " + type );
						}
					});
		}

		public boolean
		isInitiator()
		{
			return( initiator );
		}

		public boolean
		isConnected()
		{
			return( connected );
		}

		public boolean
		isSeed()
		{
			return( is_seed );
		}

		public boolean
		isOK()
		{
			return( bitfield_received );
		}

		protected void
		connected()
		{
			connection.getIncomingMessageQueue().registerQueueListener(
				new IncomingMessageQueue.MessageQueueListener()
				{

					@Override
					public boolean
					messageReceived(
						Message message )
					{
						try{
							String	message_id = message.getID();

							log( "Incoming message received: " + message.getID(), true );

					        if ( message_id.equals( BTMessage.ID_BT_HANDSHAKE )){

					        	handshake_received = true;

				        		BTHandshake handshake = (BTHandshake)message;

				        		info_hash = handshake.getDataHash();

				        		num_pieces = 500 + (info_hash[0]&0xff);

				        			// we use the piece at 'n' + 1 to indicate a close request by sending a HAVE for it
				        			// this helps us tidily close things

				        		if ( num_pieces%8 == 0 ){

				        			num_pieces--;
				        		}

				        		if ( !is_seed ){

				        			int missing = random.nextInt( num_pieces/2 ) + 5;

				        			for (int i=0;i<missing;i++){

				        				missing_pieces.add( new Integer( random.nextInt( num_pieces )));
				        			}
				        		}

				        		sendHandshake();

					        	sendBitfield();

					        	connection.getIncomingMessageQueue().getDecoder().resumeDecoding();

					        }else if ( message_id.equals( BTMessage.ID_BT_BITFIELD )){

					        	bitfield_received = true;

					        	BTBitfield bitfield = (BTBitfield)message;

					        	ByteBuffer bb = bitfield.getBitfield().getBuffer((byte)0);

					        	byte[]	contents = new byte[bb.remaining()];

					        	bb.get( contents );

					        }else if ( message_id.equals( BTMessage.ID_BT_HAVE  )){

					        	BTHave have = (BTHave)message;

					        	if ( have.getPieceNumber() == num_pieces ){

					    			synchronized( sessions ){

					    				closing = true;
					    			}

					    			close();
					        	}
					        }

					        return( true );

						}finally{

							message.destroy();
						}
					}


					@Override
					public final void
					protocolBytesReceived(
						int byte_count )

					{
					}

					@Override
					public final void
					dataBytesReceived(
						int byte_count )
					{
					}

					@Override
					public boolean
					isPriority()
					{
						return true;
					}
				});

			connection.getOutgoingMessageQueue().registerQueueListener(
				new OutgoingMessageQueue.MessageQueueListener()
				{
					@Override
					public final boolean
					messageAdded(
						Message message )
					{
						return( true );
					}

					@Override
					public final void
					messageQueued(
						Message message )
					{
					}

					@Override
					public final void
					messageRemoved(
						Message message )
					{

					}

					@Override
					public final void
					messageSent(
						Message message )
					{
						log( "Outgoing message sent: " + message.getID(), true );
					}

					@Override
					public final void
					protocolBytesSent(
						int byte_count )
					{
					}

					@Override
					public final void
					dataBytesSent(
						int byte_count )
					{
					}

					@Override
					public void
					flush()
					{
					}
			});

			connection.startMessageProcessing();

			if ( initiator ){

				sendHandshake();
			}
		}

		protected void
		sendHandshake()
		{
			if ( !handshake_sent ){

				handshake_sent = true;

				connection.getOutgoingMessageQueue().addMessage(
					new BTHandshake( info_hash, peer_id, BTHandshake.BT_RESERVED_MODE, BTMessageFactory.MESSAGE_VERSION_INITIAL ),
					false );
			}
		}

		protected void
		sendHave(
			int	piece_number )
		{
			BTHave message = new BTHave( piece_number, BTMessageFactory.MESSAGE_VERSION_INITIAL );

			OutgoingMessageQueue out_q = connection.getOutgoingMessageQueue();

			out_q.addMessage( message, false );

			out_q.flush();
		}

		protected void
		sendBitfield()
		{
			if ( !bitfield_sent ){

				bitfield_sent = true;

				byte[]	bits = new byte[( num_pieces + 7 ) /8];

				int	pos = 0;

				int i		= 0;
				int bToSend	= 0;

				for (; i <num_pieces; i++ ){

					if ((i %8) ==0){

						bToSend =0;
					}

					bToSend = bToSend << 1;

					boolean	has_piece = !missing_pieces.contains( new Integer(i));

					if ( has_piece ){

						bToSend += 1;
					}

					if ((i %8) ==7){

						bits[pos++] = (byte)bToSend;
					}
				}

				if ((i %8) !=0){

					bToSend = bToSend << (8 - (i % 8));

					bits[pos++] = (byte)bToSend;
				}

				DirectByteBuffer buffer = new DirectByteBuffer( ByteBuffer.wrap( bits ));

				connection.getOutgoingMessageQueue().addMessage(
					new BTBitfield( buffer, BTMessageFactory.MESSAGE_VERSION_INITIAL ),
					false );
			}
		}

		protected void
		close()
		{
			synchronized( sessions ){

				sessions.remove( this );

				if ( !closing ){

					closing	= true;

				}else{

					closed = true;
				}
			}

			if ( closed ){

				log( "Closing connection", true  );

				connection.close( null );

			}else{

				sendHave( num_pieces );

				new DelayedEvent(
					"NetStatus:delayClose",
					5000,
					new AERunnable()
					{
						@Override
						public void
						runSupport()
						{
							if ( !closed ){

								close();
							}
						}
					});
			}

			checkCompletion();
		}

		public String
		getProtocolString()
		{
			String	str = "";

			if ( connected ){

				str = "connected";

				str += addSent( "hand", handshake_sent );
				str += addRecv( "hand", handshake_received );
				str += addSent( "bitf", bitfield_sent );
				str += addRecv( "bitf", bitfield_received );

			}else{

				str = "not connected";
			}

			return( str );
		}

		protected String
		addSent(
			String	str,
			boolean	ok )
		{
			if ( ok ){

				return( ", " + str + " sent" );
			}else{

				return( ", " + str + " !sent" );
			}
		}

		protected String
		addRecv(
			String	str,
			boolean	ok )
		{
			if ( ok ){

				return( ", " + str + " recv" );
			}else{

				return( ", " + str + " !recv" );
			}
		}

		protected String
		getLogPrefix()
		{
			return( "(" + (initiator?"L":"R") + (is_seed?"S":"L") + " " + session_id + ") " );
		}

		protected void
		log(
			String	str )
		{
			NetStatusProtocolTesterBT.this.log( getLogPrefix() + str );
		}

		protected void
		log(
			String		str,
			boolean		is_detailed )
		{
			NetStatusProtocolTesterBT.this.log( getLogPrefix() + str, is_detailed );
		}

		protected void
		logError(
			String	str )
		{
			NetStatusProtocolTesterBT.this.logError( getLogPrefix() + str );
		}

		protected void
		logError(
			String		str,
			Throwable	e )
		{
			NetStatusProtocolTesterBT.this.logError( getLogPrefix() + str, e );
		}
	}
}
