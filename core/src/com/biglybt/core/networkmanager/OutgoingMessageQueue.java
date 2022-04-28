/*
 * Created on May 8, 2004
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

import java.io.IOException;

import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.messaging.MessageStreamEncoder;



/**
 * Priority-based outbound peer message queue.
 */
public interface
OutgoingMessageQueue
{

  public void
  setTransport(
	 Transport		_transport );


  public int
  getMssSize();


  /**
   * Set the message stream encoder that will be used to encode outgoing messages.
   * @param stream_encoder to use
   */
  public void setEncoder( MessageStreamEncoder stream_encoder ) ;


  public MessageStreamEncoder
  getEncoder();


  /**
   * @return [ total, done] or null
   */
  public int[] getCurrentMessageProgress();


  /**
   * Destroy this queue; i.e. perform cleanup actions.
   */
  public void destroy();

  public boolean
  isDestroyed();

  /**
   * Get the total number of bytes ready to be transported.
   * @return total bytes remaining
   */
  public int getTotalSize();

  public int getDataQueuedBytes();

  public int getProtocolQueuedBytes();

  public boolean isBlocked();

  public boolean getPriorityBoost();

  public void setPriorityBoost( boolean	boost );

  /**
   * Whether or not an urgent message (one that needs an immediate send, i.e. a no-delay message) is queued.
   * @return true if there's a message tagged for immediate write
   */
  public boolean hasUrgentMessage();


  public Message
  peekFirstMessage();

  /**
   * Add a message to the message queue.
   * NOTE: Allows for manual listener notification at some later time,
   * using doListenerNotifications(), instead of notifying immediately
   * from within this method.  This is useful if you want to invoke
   * listeners outside of some greater synchronised block to avoid
   * deadlock.
   * @param message message to add
   * @param manual_listener_notify true for manual notification, false for automatic
   */
  public void addMessage( Message message, boolean manual_listener_notify ) ;


  /**
   * Remove all messages of the given types from the queue.
   * NOTE: Allows for manual listener notification at some later time,
   * using doListenerNotifications(), instead of notifying immediately
   * from within this method.  This is useful if you want to invoke
   * listeners outside of some greater synchronised block to avoid
   * deadlock.
   * @param message_types type to remove
   * @param manual_listener_notify true for manual notification, false for automatic
   */
  public void removeMessagesOfType( Message[] message_types, boolean manual_listener_notify );


  /**
   * Remove a particular message from the queue.
   * NOTE: Only the original message found in the queue will be destroyed upon removal,
   * which may not necessarily be the one passed as the method parameter,
   * as some messages override equals() (i.e. BTRequest messages) instead of using reference
   * equality, and could be a completely different object, and would need to be destroyed
   * manually.  If the message does not override equals, then any such method will likely
   * *not* be found and removed, as internal queued object was a new allocation on insertion.
   * NOTE: Allows for manual listener notification at some later time,
   * using doListenerNotifications(), instead of notifying immediately
   * from within this method.  This is useful if you want to invoke
   * listeners outside of some greater synchronised block to avoid
   * deadlock.
   * @param message to remove
   * @param manual_listener_notify true for manual notification, false for automatic
   * @return true if the message was removed, false otherwise
   */

  public boolean removeMessage( Message message, boolean manual_listener_notify );

  /**
   * Deliver (write) message(s) data to the underlying transport.
   *
   * NOTE: Allows for manual listener notification at some later time,
   * using doListenerNotifications(), instead of notifying immediately
   * from within this method.  This is useful if you want to invoke
   * listeners outside of some greater synchronised block to avoid
   * deadlock.
   * @param max_bytes maximum number of bytes to deliver
   * @param manual_listener_notify true for manual notification, false for automatic
   * @return number of bytes delivered as [data, protocol]
   * @throws IOException on delivery error
   */
  public int[] deliverToTransport( int max_bytes, boolean protocol_is_free, boolean manual_listener_notify ) throws IOException;


  /**
   * Manually send any unsent listener notifications.
   */
  public void doListenerNotifications();

  /**
   * Force all pending messages to be delivered
   */

  public void
  flush();

  public void
  setTrace(
		boolean	on );

  public String getQueueTrace();

  /**
   * Add a listener to be notified of queue events.
   * @param listener
   */
  public void registerQueueListener( MessageQueueListener listener );

  /**
   * Cancel queue event notification listener.
   * @param listener
   */
  public void cancelQueueListener( MessageQueueListener listener );


  /**
   * Notifty the queue (and its listeners) of a message sent externally on the queue's behalf.
   * @param message sent externally
   */
  public void notifyOfExternallySentMessage( Message message );


  /////////////////////////////////////////////////////////////////

  /**
   * Receive notification of queue events.
   */
  public interface MessageQueueListener {
    /**
     * The given message has just been added to the queue.
     * @param message added for queuing
     * @return true if this message addition is allowed, false if it should be immediately removed without being queued or sent
     */
    public boolean messageAdded( Message message );


    /**
     * The given message has just been queued for sending out the transport.
     * @param message queued
     */
    public void messageQueued( Message message );


    /**
     * The given message has just been forcibly removed from the queue,
     * i.e. it was *not* sent out the transport.
     * @param message removed
     */
    public void messageRemoved( Message message );

    /**
     * The given message has been completely sent out through the transport.
     * @param message sent
     */
    public void messageSent( Message message );

    /**
     * The given number of protocol (overhead) bytes has been written to the transport.
     * @param byte_count number of protocol bytes
     */
    public void protocolBytesSent( int byte_count );


    /**
     * The given number of (piece) data bytes has been written to the transport.
     * @param byte_count number of data bytes
     */
    public void dataBytesSent( int byte_count );

    public void flush();
  }
}
