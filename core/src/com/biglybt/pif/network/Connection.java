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

package com.biglybt.pif.network;


/**
 * Represents a managed peer connection, over which messages can be sent and received.
 */
public interface Connection {

  /**
   * Connect this connection's transport, i.e. establish the peer connection.
   * If this connection is already established (from an incoming connection for example),
   * then this provides a mechanism to register the connection listener, in which case
   * connectSuccess() will be called immediately.
   * @param listener notified on connect success or failure
   */
  public void connect( ConnectionListener listener );


  /**
   * Close and shutdown this connection.
   */
  public void close();


  /**
   * Get the connection's outgoing message queue.
   * @return outbound message queue
   */
  public OutgoingMessageQueue getOutgoingMessageQueue();


  /**
   * Get the connection's incoming message queue.
   * @return inbound message queue
   */
  public IncomingMessageQueue getIncomingMessageQueue();


  /**
   * Begin processing incoming and outgoing message queues.
   */
  public void startMessageProcessing();

  /**
   * Returns the transport object for this connection.
   * @since 3.0.5.3
   */
  public Transport getTransport();

  /**
   * Returns <tt>true</tt> if the connection represents an incoming
   * connection.
   *
   * @since 3.0.5.3
   */
  public boolean isIncoming();

  /**
   * descriptive text for the connection
   * @return
   */
  public String
  getString();
}
