/*
 * Created on Feb 21, 2005
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

package com.biglybt.core.networkmanager;

import java.nio.ByteBuffer;


/**
 * Represents a managed network connection, over which messages can be sent and received.
 */
public interface
NetworkConnection
	extends NetworkConnectionBase
{
  /**
   * Connect this connection's transport, i.e. establish the network connection.
   * If this connection is already established (from an incoming connection for example),
   * then this provides a mechanism to register the connection listener, in which case
   * connectSuccess() will be called immediately.
   * @param listener notified on connect success or failure
   */
  public void connect( int priority, ConnectionListener listener );

  public void connect( ByteBuffer initial_outbound_data, int priority, ConnectionListener listener );

  /**
   * Close and shutdown this connection.
   */
  public void close( String reason );


  /**
   * Begin processing incoming and outgoing message queues.
   * @param upload_group upload rate limit group to use
   * @param download_group download rate limit group to use
   */
  public void startMessageProcessing();


  /**
   * Upgrade the connection to high-speed transfer processing.
   * @param enable true for high-speed processing, false for normal processing
   */
  public void enableEnhancedMessageProcessing( boolean enable, int partition_id );

  /**
   * Decouples the transport from this network connection so it can be reused
   * @return null if detach failed
   */

  public Transport detachTransport();

  /**
   * Get the connection's data transport interface.
   * @return the transport - MAY BE NULL if not yet fully connected
   */
  public Transport getTransport();


  public boolean isConnected();

  public Object
  setUserData(
  	Object		key,
  	Object		value );

  public Object
  getUserData(
  	Object		key );

  /**
   * Listener for notification of connection events.
   */
  public interface ConnectionListener {
    /**
     * The connection establishment process has started,
     * i.e. the connection is actively being attempted.
     * @return modified connect timeout
     */
    public int connectStarted( int default_connect_timeout );

    /**
     * The connection attempt succeeded.
     * The connection is now established.
     * NOTE: Called only during initial connect attempt.
     */
    public void connectSuccess( ByteBuffer remaining_initial_data );

    /**
     * The connection attempt failed.
     * NOTE: Called only during initial connect attempt.
     * @param failure_msg failure reason
     */
    public void connectFailure( Throwable failure_msg );

    /**
     * Handle exception thrown by this connection.
     * NOTE: Can be called at any time during connection lifetime.
     * @param error exception
     */
    public void exceptionThrown( Throwable error );

    public Object getConnectionProperty( String property_name);

    public String
    getDescription();
  }
}
