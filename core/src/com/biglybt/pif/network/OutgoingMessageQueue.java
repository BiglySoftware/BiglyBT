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

import com.biglybt.pif.messaging.Message;
import com.biglybt.pif.messaging.MessageStreamEncoder;



/**
 * Queue for sending outgoing messages.
 */
public interface OutgoingMessageQueue {

  /**
   * Set the message stream encoder that will be used to encode outgoing messages.
   * @param encoder to use
   */
  public void setEncoder( MessageStreamEncoder encoder );

  /**
   * Queue the given message for sending.
   * @param message to send
   */
  public void sendMessage( Message message );

  /**
   * Register queue listener.
   * @param listener to register
   */
  public void registerListener( OutgoingMessageQueueListener listener );

  /**
   * Remove registration of queue listener.
   * @param listener to remove
   */
  public void deregisterListener( OutgoingMessageQueueListener listener );

  /**
   * Notifty the queue (and its listeners) of a message sent externally on the queue's behalf.
   * @param message sent externally
   */
  public void notifyOfExternalSend( Message message );

  public default int
  getPercentDoneOfCurrentMessage()
  {
	  int[] progress = getCurrentMessageProgress();
	  
	  if ( progress == null ){
	  
		  return( -1 );
		  
	  }else{
		  
		  int length 	= progress[0];
		  int done		= progress[1];
		  
		  if ( length <= 0 ){
			  
			  return( -1 );
			  
		  }else if ( done >= length ){
			  
			  return( 100 );
			  
		  }else{
			  
			  return((done*100)/length);
		  }
	  }
  }
  
  public int[] getCurrentMessageProgress();

  public int getDataQueuedBytes();

  public int getProtocolQueuedBytes();

  public boolean isBlocked();
}
