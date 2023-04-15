/*
 * Created on Jul 29, 2004
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

package com.biglybt.core.networkmanager.impl;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

import com.biglybt.core.networkmanager.*;
import com.biglybt.core.peermanager.messaging.MessageStreamDecoder;
import com.biglybt.core.peermanager.messaging.MessageStreamEncoder;
import com.biglybt.core.proxy.AEProxyFactory.PluginProxy;
import com.biglybt.core.util.AddressUtils;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.LightHashMap;



/**
 *
 */

public class
NetworkConnectionImpl
	extends NetworkConnectionHelper
	implements NetworkConnection
{
  private final ConnectionEndpoint	connection_endpoint;
  private final boolean				is_incoming;

  private boolean connect_with_crypto;
  private boolean allow_fallback;
  private byte[][] shared_secrets;

  private ConnectionListener connection_listener;
  private boolean 	is_connected;
  private byte		is_lan_local	= AddressUtils.LAN_LOCAL_MAYBE;

  private int		enhanced_partition_id = -1;
  
  private final OutgoingMessageQueueImpl outgoing_message_queue;
  private final IncomingMessageQueueImpl incoming_message_queue;

  private Transport	transport;

  private volatile ConnectionAttempt	connection_attempt;
  private boolean						started;
  private volatile boolean				closed;

  private Map<Object,Object>			user_data;

  /**
   * Constructor for new OUTbound connection.
   * The connection is not yet established upon instantiation; use connect() to do so.
   * @param _remote_address to connect to
   * @param encoder default message stream encoder to use for the outgoing queue
   * @param decoder default message stream decoder to use for the incoming queue
   */
  public NetworkConnectionImpl(
		  		ConnectionEndpoint _target, MessageStreamEncoder encoder,
		  		MessageStreamDecoder decoder, boolean _connect_with_crypto, boolean _allow_fallback,
		  		byte[][] _shared_secrets )
  {
	connection_endpoint	= _target;
	is_incoming			= false;
    connect_with_crypto	= _connect_with_crypto;
    allow_fallback = _allow_fallback;
    shared_secrets = _shared_secrets;


    is_connected = false;
    outgoing_message_queue = new OutgoingMessageQueueImpl( encoder );
    incoming_message_queue = new IncomingMessageQueueImpl( decoder, this );
  }


  /**
   * Constructor for new INbound connection.
   * The connection is assumed to be already established, by the given already-connected channel.
   * @param _remote_channel connected by
   * @param data_already_read bytestream already read during routing
   * @param encoder default message stream encoder to use for the outgoing queue
   * @param decoder default message stream decoder to use for the incoming queue
   */
  public NetworkConnectionImpl( Transport _transport, MessageStreamEncoder encoder, MessageStreamDecoder decoder ) {
    transport = _transport;
    connection_endpoint = transport.getTransportEndpoint().getProtocolEndpoint().getConnectionEndpoint();
    is_incoming		= true;
    is_connected 	= true;
    outgoing_message_queue = new OutgoingMessageQueueImpl( encoder );
    outgoing_message_queue.setTransport( transport );
    incoming_message_queue = new IncomingMessageQueueImpl( decoder, this );

    transport.bindConnection( this );
  }


  @Override
  public ConnectionEndpoint
  getEndpoint()
  {
	  return( connection_endpoint );
  }

  @Override
  public boolean
  isIncoming()
  {
	  return( is_incoming );
  }

  @Override
  public void connect(int priority, ConnectionListener listener ) {
	  connect( null, priority, listener );
  }

  @Override
  public void connect(ByteBuffer initial_outbound_data, int priority, ConnectionListener listener ) {
    this.connection_listener = listener;

    if( is_connected ){

      connection_listener.connectStarted( -1 );

      connection_listener.connectSuccess( initial_outbound_data );

      return;
    }

    if ( connection_attempt != null ){

    	Debug.out( "Connection attempt already active" );

    	listener.connectFailure( new Throwable( "Connection attempt already active" ));

    	return;
    }

    connection_attempt =
    	connection_endpoint.connectOutbound(
    			connect_with_crypto,
    			allow_fallback,
    			shared_secrets,
    			initial_outbound_data,
    			priority,
    			new Transport.ConnectListener() {
			      @Override
			      public int connectAttemptStarted(Transport _transport, int default_connect_timeout ){
			    	if ( _transport != null ){
			    		transport	= _transport;
			    	}
			        return( connection_listener.connectStarted( default_connect_timeout ));
			      }

			      @Override
			      public void connectSuccess(Transport	_transport, ByteBuffer remaining_initial_data ) {
			        is_connected = true;
			        transport	= _transport;
			        outgoing_message_queue.setTransport( transport );
			        transport.bindConnection( NetworkConnectionImpl.this );
			        connection_listener.connectSuccess( remaining_initial_data );
			        connection_attempt	= null;
			      }

			      @Override
			      public void connectFailure(Transport transport, Throwable failure_msg ) {
			        is_connected = false;
			        connection_listener.connectFailure( failure_msg );
			      }

			    	@Override
				    public Object
					getConnectionProperty(
						String property_name)
					{
			    		return( connection_listener.getConnectionProperty( property_name ));
					}
			    });

    if ( closed ){

    	ConnectionAttempt	ca = connection_attempt;

    	if ( ca != null ){

    		ca.abandon();
    	}
    }
  }

  @Override
  public Transport
  detachTransport()
  {
	  Transport	t = transport;

	  if ( t != null ){

		  t.unbindConnection( this );
	  }

	  transport = new bogusTransport( transport );

	  close( "detached transport" );

	  return( t );
  }

  @Override
  public void close(String reason ) {
  	NetworkManager.getSingleton().stopTransferProcessing( this );
  	closed	= true;
    if ( connection_attempt != null ){
    	connection_attempt.abandon();
    }
    if ( transport != null ){
    	transport.close( "Tidy close" + ( reason==null||reason.length()==0?"":(": " + reason )));
    }
    incoming_message_queue.destroy();
   	outgoing_message_queue.destroy();
    is_connected = false;
  }

  @Override
  public boolean 
  isClosed()
  {
	  return( closed );
  }

  @Override
  public void notifyOfException(Throwable error ) {
    if( connection_listener != null ) {
      connection_listener.exceptionThrown( error );
    }
    else {
      Debug.out( "notifyOfException():: connection_listener == null for exception: " +error.getMessage() );
    }
  }


  @Override
  public OutgoingMessageQueue getOutgoingMessageQueue() {  return outgoing_message_queue;  }

  @Override
  public IncomingMessageQueue getIncomingMessageQueue() {  return incoming_message_queue;  }


  @Override
  public void
  startMessageProcessing()
  {
	started = true;
	
  	NetworkManager.getSingleton().startTransferProcessing( this );
  }


  @Override
  public void enableEnhancedMessageProcessing(boolean enable, int partition_id ) {
    if( enable ) {
    	enhanced_partition_id = partition_id;
    	
    	NetworkManager.getSingleton().upgradeTransferProcessing( this, partition_id );
    	
    }else{
    	enhanced_partition_id = -1;
    	
    	NetworkManager.getSingleton().downgradeTransferProcessing( this );
    }
  }


  @Override
  public Transport getTransport() {  return transport;  }

  @Override
  public TransportBase getTransportBase() {  return transport;  }

  @Override
  public int
  getMssSize()
  {
	  if ( transport == null ){

		  return( NetworkManager.getMinMssSize());

	  }else{

		  return( transport.getMssSize());
	  }
  }


  @Override
  public Object
  setUserData(
  	Object		key,
  	Object		value )
  {
	synchronized( this ){
		if ( user_data == null ){
			user_data = new LightHashMap<>();
		}

		return( user_data.put( key, value ));
	}
  }

  @Override
  public Object
  getUserData(
  	Object		key )
  {
	  synchronized( this ){
			if ( user_data == null ){
				return( null );
			}

			return( user_data.get( key ));
	  }
  }

  public String toString() {
    return( transport==null?connection_endpoint.getDescription():transport.getDescription() );
  }


	@Override
	public boolean isConnected() {
		return is_connected;
	}


	@Override
	public boolean isLANLocal() {
		if ( is_lan_local == AddressUtils.LAN_LOCAL_MAYBE ){

			is_lan_local = AddressUtils.isLANLocalAddress( connection_endpoint.getNotionalAddress());
		}
		return( is_lan_local == AddressUtils.LAN_LOCAL_YES );
	}

	@Override
	public void 
	resetLANLocalStatus(){
		if ( closed ){
			
			return;
		}
		
		if ( is_lan_local != AddressUtils.LAN_LOCAL_MAYBE ){
			
			NetworkManager nm = NetworkManager.getSingleton();
									
			if ( started ){
				
				nm.stopTransferProcessing( this );
			}
			
			is_lan_local = AddressUtils.LAN_LOCAL_MAYBE;
			
			if ( started ){
				
				nm.startTransferProcessing( this );
			}
			
			if ( enhanced_partition_id != -1 ){
			
				nm.upgradeTransferProcessing( this, enhanced_partition_id );
			}
		}
	}
	
	@Override
	public String
	getString()
	{
		return( "tran=" + (transport==null?"null":transport.getDescription()+
				",closed/con=" + closed + "/" + is_connected +
				",w_ready=" + transport.isReadyForWrite(null)+
				",r_ready=" + transport.isReadyForRead( null ))+ 
				",in=" + incoming_message_queue.getCurrentMessageProgress() +
				",out=" + (outgoing_message_queue==null?0:outgoing_message_queue.getTotalSize()) + 
				",owner=" + (connection_listener==null?"null":connection_listener.getDescription()));
	}

	protected static class
	bogusTransport
		implements Transport
	{
		private final Transport transport;

		protected
		bogusTransport(
			Transport	_transport )
		{
			transport = _transport;
		}

		@Override
		public boolean
		isReadyForWrite(
			EventWaiter waiter )
		{
			return( false );
		}

		@Override
		public long
		isReadyForRead(
			EventWaiter waiter )
		{
			return( Long.MAX_VALUE );
		}

		@Override
		public boolean
		isTCP()
		{
			return( transport.isTCP());
		}

		@Override
		public boolean
		isSOCKS()
		{
			return( transport.isSOCKS());
		}

		@Override
		public PluginProxy 
		getPluginProxy()
		{
			return( transport.getPluginProxy());
		}
		
		@Override
		public String
		getDescription()
		{
			return( transport.getDescription());
		}

		@Override
		public int
		getMssSize()
		{
			return( transport.getMssSize());
		}

		@Override
		public void
		setAlreadyRead(
			ByteBuffer bytes_already_read )
		{
			Debug.out( "Bogus Transport Operation" );
		}

		@Override
		public TransportEndpoint
		getTransportEndpoint()
		{
			return( transport.getTransportEndpoint());
		}

		@Override
		public TransportStartpoint
		getTransportStartpoint()
		{
			return( transport.getTransportStartpoint());
		}

		@Override
		public boolean
		isEncrypted()
		{
			return( transport.isEncrypted());
		}

		@Override
		public String
		getEncryption( boolean verbose)
		{
			return( transport.getEncryption( verbose ));
		}

		@Override
		public String getProtocol(){ return transport.getProtocol(); }

		@Override
		public void
		setReadyForRead()
		{
			Debug.out( "Bogus Transport Operation" );
		}

		@Override
		public long
		write(
			ByteBuffer[] buffers,
			int array_offset,
			int length )

			throws IOException
		{
			Debug.out( "Bogus Transport Operation" );

			throw( new IOException( "Bogus transport!" ));
		}

		@Override
		public long
		read(
			ByteBuffer[] buffers, int array_offset, int length )

			throws IOException
		{
			Debug.out( "Bogus Transport Operation" );

			throw( new IOException( "Bogus transport!" ));
		}

		@Override
		public void
		setTransportMode(
			int mode )
		{
			Debug.out( "Bogus Transport Operation" );
		}

		@Override
		public int
		getTransportMode()
		{
			return( transport.getTransportMode());
		}

		@Override
		public void
		connectOutbound(
			ByteBuffer			initial_data,
			ConnectListener 	listener,
			int					priority )
		{
			Debug.out( "Bogus Transport Operation" );

			listener.connectFailure( transport, new Throwable( "Bogus Transport" ));
		}

		@Override
		public void
		connectedInbound()
		{
			Debug.out( "Bogus Transport Operation" );
		}

		@Override
		public void
		close(
			String reason )
		{
			// we get here after detaching a transport and then closing the peer connection
		}

		@Override
		public void
		bindConnection(
			NetworkConnection	connection )
		{
		}

		@Override
		public void
		unbindConnection(
			NetworkConnection	connection )
		{
		}

		@Override
		public Object getUserData(Object key){
			return( transport.getUserData(key));
		}
		
		@Override
		public void setUserData(Object key, Object value){
			transport.setUserData(key, value);
		}
		
		@Override
		public void
		setTrace(
			boolean	on )
		{
		}
	}
}
