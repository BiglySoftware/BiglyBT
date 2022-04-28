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

import java.util.HashMap;

import com.biglybt.pif.messaging.Message;
import com.biglybt.pif.messaging.MessageStreamEncoder;
import com.biglybt.pif.network.OutgoingMessageQueue;
import com.biglybt.pif.network.OutgoingMessageQueueListener;
import com.biglybt.pifimpl.local.messaging.MessageAdapter;
import com.biglybt.pifimpl.local.messaging.MessageStreamEncoderAdapter;




/**
 *
 */
public class OutgoingMessageQueueImpl implements OutgoingMessageQueue {
  private final com.biglybt.core.networkmanager.OutgoingMessageQueue core_queue;
  private final HashMap registrations = new HashMap();


  protected OutgoingMessageQueueImpl( com.biglybt.core.networkmanager.OutgoingMessageQueue core_queue ) {
    this.core_queue = core_queue;
  }


  @Override
  public void setEncoder(final MessageStreamEncoder encoder ) {
    core_queue.setEncoder( new MessageStreamEncoderAdapter( encoder ) );
  }


  @Override
  public void sendMessage(Message message ) {
    if( message instanceof MessageAdapter ) {
      //the message must have been originally created by core and wrapped
      //so just use original core message...i.e. unwrap out of MessageAdapter
      core_queue.addMessage( ((MessageAdapter)message).getCoreMessage(), false );
      return;
    }

    //message originally created by plugin
    core_queue.addMessage( new MessageAdapter( message ), false );
  }


  @Override
  public void registerListener(final OutgoingMessageQueueListener listener ) {
    com.biglybt.core.networkmanager.OutgoingMessageQueue.MessageQueueListener core_listener =
      new com.biglybt.core.networkmanager.OutgoingMessageQueue.MessageQueueListener() {

        @Override
        public boolean messageAdded(com.biglybt.core.peermanager.messaging.Message message ) {
          if( message instanceof MessageAdapter ) {
            //the message must have been originally created by plugin encoder and wrapped
            //so just use original plugin message...i.e. unwrap out of MessageAdapter
            return listener.messageAdded( ((MessageAdapter)message).getPluginMessage() );
          }

          //message originally created by core
          return listener.messageAdded( new MessageAdapter( message ) );
        }

        @Override
        public void messageQueued(com.biglybt.core.peermanager.messaging.Message message ) {  /*nothing*/  }
        @Override
        public void messageRemoved(com.biglybt.core.peermanager.messaging.Message message ) {  /*nothing*/  }

        @Override
        public void messageSent(com.biglybt.core.peermanager.messaging.Message message ) {
          if( message instanceof MessageAdapter ) {
            //the message must have been originally created by plugin encoder and wrapped
            //so just use original plugin message...i.e. unwrap out of MessageAdapter
            listener.messageSent( ((MessageAdapter)message).getPluginMessage() );
            return;
          }

          //message originally created by core
          listener.messageSent( new MessageAdapter( message ) );
        }

        @Override
        public void protocolBytesSent(int byte_count ) {  listener.bytesSent( byte_count );  }

        @Override
        public void dataBytesSent(int byte_count ) {  listener.bytesSent( byte_count );  }
        @Override
        public void flush(){}
    };

    registrations.put( listener, core_listener );  //save this mapping for later

    core_queue.registerQueueListener( core_listener );
  }


  @Override
  public void deregisterListener(OutgoingMessageQueueListener listener ) {
    //retrieve saved mapping
    com.biglybt.core.networkmanager.OutgoingMessageQueue.MessageQueueListener core_listener =
      (com.biglybt.core.networkmanager.OutgoingMessageQueue.MessageQueueListener)registrations.remove( listener );

    if( core_listener != null ) {
      core_queue.cancelQueueListener( core_listener );
    }
  }


  @Override
  public void notifyOfExternalSend(Message message ) {
    if( message instanceof MessageAdapter ) {
      //the message must have been originally created by core and wrapped
      //so just use original core message...i.e. unwrap out of MessageAdapter
      core_queue.notifyOfExternallySentMessage( ((MessageAdapter)message).getCoreMessage() );
      return;
    }

    //message originally created by plugin
    core_queue.notifyOfExternallySentMessage( new MessageAdapter( message ) );
  }

  @Override
  public int[] getCurrentMessageProgress() {
    return core_queue.getCurrentMessageProgress();
  }

  @Override
  public int
  getDataQueuedBytes()
  {
	  return(core_queue.getDataQueuedBytes());
  }

  @Override
  public int
  getProtocolQueuedBytes()
  {
	  return(core_queue.getProtocolQueuedBytes());
  }

  @Override
  public boolean
  isBlocked()
  {
	  return(core_queue.isBlocked());
  }
}
