/*
 * Created on Jul 18, 2004
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

package com.biglybt.core.peermanager.utils;

import java.util.ArrayList;

import com.biglybt.core.networkmanager.OutgoingMessageQueue;
import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.messaging.azureus.AZHave;
import com.biglybt.core.peermanager.messaging.azureus.AZMessage;
import com.biglybt.core.peermanager.messaging.bittorrent.BTHave;
import com.biglybt.core.peermanager.messaging.bittorrent.BTMessage;
import com.biglybt.core.peermanager.messaging.bittorrent.BTMessageFactory;
import com.biglybt.core.util.AEMonitor;


/**
 * Utility class to enable write aggregation of BT Have messages,
 * in order to save bandwidth by not wasting a whole network packet
 * on a single small 9-byte message, and instead pad them onto other
 * messages.
 */
public class OutgoingBTHaveMessageAggregator {

  private final ArrayList 	pending_haves 		= new ArrayList();
  private final AEMonitor	pending_haves_mon	= new AEMonitor( "OutgoingBTHaveMessageAggregator:PH");

  private byte bt_have_version;
  private byte az_have_version;

  private boolean destroyed = false;

  private final OutgoingMessageQueue outgoing_message_q;

  private final OutgoingMessageQueue.MessageQueueListener added_message_listener = new OutgoingMessageQueue.MessageQueueListener() {
    @Override
    public boolean messageAdded(Message message ) {  return true;  }

    @Override
    public void messageQueued(Message message ) {
      //if another message is going to be sent anyway, add our haves as well

      String message_id = message.getID();

      if( ! ( message_id.equals( BTMessage.ID_BT_HAVE ) || message_id.equals( AZMessage.ID_AZ_HAVE ))) {
        sendPendingHaves();
      }
    }
    @Override
    public void messageRemoved(Message message ) {/*nothing*/}
    @Override
    public void messageSent(Message message ) {/*nothing*/}
    @Override
    public void protocolBytesSent(int byte_count ) {/*ignore*/}
    @Override
    public void dataBytesSent(int byte_count ) {/*ignore*/}
    @Override
    public void flush(){}
  };




  /**
   * Create a new aggregator, which will send messages out the given queue.
   * @param outgoing_message_q
   */
  public
  OutgoingBTHaveMessageAggregator(
	OutgoingMessageQueue 	outgoing_message_q,
	byte 					_bt_have_version,
	byte					_az_have_version )
  {
    this.outgoing_message_q = outgoing_message_q;
    bt_have_version = _bt_have_version;
    az_have_version	= _az_have_version;

    outgoing_message_q.registerQueueListener( added_message_listener );
  }

  public void
  setHaveVersion(
	byte	bt_version,
	byte	az_version )
  {
	  bt_have_version 	= bt_version;
	  az_have_version	= az_version;
  }
  /**
   * Queue a new have message for aggregated sending.
   * @param piece_number of the have message
   * @param force if true, send this and any other pending haves right away
   */
  public void queueHaveMessage( int piece_number, boolean force ) {
    if( destroyed )  return;

    try{
      pending_haves_mon.enter();

      pending_haves.add( new Integer( piece_number ) );
      if( force ) {
        sendPendingHaves();
      }
      else {
        int pending_bytes = pending_haves.size() * 9;
        if( pending_bytes >= outgoing_message_q.getMssSize() ) {
          //System.out.println("enough pending haves for a full packet!");
          //there's enough pending bytes to fill a packet payload
          sendPendingHaves();
        }
      }
    }finally{

    	pending_haves_mon.exit();
    }
  }


  /**
   * Destroy the aggregator, along with any pending messages.
   */
  public void destroy() {
    try{
      pending_haves_mon.enter();

      pending_haves.clear();
      destroyed = true;
    }
    finally{
      pending_haves_mon.exit();
    }
  }


  /**
   * Force send of any aggregated/pending have messages.
   */
  public void forceSendOfPending() {
    sendPendingHaves();
  }



  /**
   * Are there Haves messages pending?
   * @return true if there are any unsent haves, false otherwise
   */
  public boolean hasPending() {  return !pending_haves.isEmpty();  }


  private void
  sendPendingHaves()
  {
    if ( destroyed ){

    	return;
    }

    try{
      pending_haves_mon.enter();

      int	num_haves = pending_haves.size();

      if ( num_haves == 0 ){

    	  return;
      }

      	// single have -> use BT

      if ( num_haves == 1 || az_have_version < BTMessageFactory.MESSAGE_VERSION_SUPPORTS_PADDING ){

	      for( int i=0; i < num_haves; i++ ){

	        Integer piece_num = (Integer)pending_haves.get( i );

	        outgoing_message_q.addMessage( new BTHave( piece_num.intValue(), bt_have_version ), true );
	      }
      }else{

    	  int[]	piece_numbers = new int[num_haves];

	      for( int i=0; i < num_haves; i++ ) {

	    	  piece_numbers[i] = ((Integer)pending_haves.get( i )).intValue();
	      }

	      outgoing_message_q.addMessage( new AZHave( piece_numbers, az_have_version ), true );

      }

      outgoing_message_q.doListenerNotifications();

      pending_haves.clear();

    }finally{

      pending_haves_mon.exit();
    }
  }

}
