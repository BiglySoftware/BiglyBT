/*
 * Created on Feb 9, 2005
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

package com.biglybt.pifimpl.local.network;

import java.nio.ByteBuffer;

import com.biglybt.core.networkmanager.ProtocolEndpoint;
import com.biglybt.pif.network.*;



/**
 *
 */
public class ConnectionImpl implements Connection {

  private final com.biglybt.core.networkmanager.NetworkConnection core_connection;
  private final OutgoingMessageQueueImpl out_queue;
  private final IncomingMessageQueueImpl in_queue;
  private final TransportImpl transport;
  private final boolean incoming;


  public ConnectionImpl(com.biglybt.core.networkmanager.NetworkConnection core_connection, boolean incoming ) {
    this.core_connection = core_connection;
    this.out_queue = new OutgoingMessageQueueImpl( core_connection.getOutgoingMessageQueue() );
    this.in_queue = new IncomingMessageQueueImpl( core_connection.getIncomingMessageQueue() );
    this.transport = new TransportImpl( core_connection );
    this.incoming = incoming;
  }


  @Override
  public void connect(final ConnectionListener listener ) {
    core_connection.connect( ProtocolEndpoint.CONNECT_PRIORITY_MEDIUM, new com.biglybt.core.networkmanager.NetworkConnection.ConnectionListener() {
      @Override
      public int connectStarted(int ct ) { listener.connectStarted(); return( ct ); }

      @Override
      public void connectSuccess(ByteBuffer remaining_initial_data) { listener.connectSuccess();  }

      @Override
      public void connectFailure(Throwable failure_msg ) {  listener.connectFailure( failure_msg );  }
      @Override
      public void exceptionThrown(Throwable error ) {  listener.exceptionThrown( error );  }

      @Override
      public Object getConnectionProperty(String property_name){ return( null );}

      @Override
      public String
      getDescription()
      {
    	  return( "plugin connection: " + core_connection.getString());
      }
    });
  }


  @Override
  public void close() {
    core_connection.close( null );
  }


  @Override
  public OutgoingMessageQueue getOutgoingMessageQueue() {  return out_queue;  }

  @Override
  public IncomingMessageQueue getIncomingMessageQueue() {  return in_queue;  }


  @Override
  public void startMessageProcessing() {

    core_connection.startMessageProcessing();

    core_connection.enableEnhancedMessageProcessing( true, -1 );  //auto-upgrade connection
  }


  @Override
  public Transport getTransport() {  return transport;  }


  public com.biglybt.core.networkmanager.NetworkConnection getCoreConnection() {
    return core_connection;
  }

  @Override
  public boolean isIncoming() {
	  return this.incoming;
  }

  @Override
  public String
  getString()
  {
	  com.biglybt.core.networkmanager.Transport t = core_connection.getTransport();

	  if ( t == null ){

		  return( "" );

	  }else{

		  return( t.getEncryption( false ));
	  }
  }
}
