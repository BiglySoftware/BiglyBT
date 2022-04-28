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

import java.io.IOException;

import com.biglybt.pif.messaging.Message;


/**
 * Inbound message queue.
 */
public interface IncomingMessageQueue {

  /**
   * Register queue listener.
   * @param listener to register
   */
  public void registerListener( IncomingMessageQueueListener listener );

  /**
   * Register queue listener that will get to process messages *ahead* of the core.
   * @param listener
   */
  public void registerPriorityListener( IncomingMessageQueueListener listener );


  /**
   * Remove registration of queue listener.
   * @param listener to remove
   */
  public void deregisterListener( IncomingMessageQueueListener listener );


  /**
   * Notifty the queue (and its listeners) of a message received externally on the queue's behalf.
   * @param message received externally
   */
  public void notifyOfExternalReceive( Message message ) throws IOException;

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

}
