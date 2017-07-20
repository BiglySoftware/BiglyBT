/*
 * Created on Feb 28, 2005
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

package com.biglybt.pif.messaging.bittorrent;

import java.nio.ByteBuffer;

import com.biglybt.core.peermanager.messaging.bittorrent.BTCancel;
import com.biglybt.core.peermanager.messaging.bittorrent.BTMessage;
import com.biglybt.core.peermanager.messaging.bittorrent.BTPiece;
import com.biglybt.core.peermanager.messaging.bittorrent.BTRequest;
import com.biglybt.core.util.DirectByteBuffer;
import com.biglybt.pif.messaging.Message;
import com.biglybt.pifimpl.local.messaging.MessageAdapter;



/**
 *
 */
public class BTMessageManager {

  public static final String ID_BTMESSAGE_REQUEST = BTMessage.ID_BT_REQUEST;
  public static final String ID_BTMESSAGE_CANCEL  = BTMessage.ID_BT_CANCEL;
  public static final String ID_BTMESSAGE_PIECE   = BTMessage.ID_BT_PIECE;
  public static final String ID_BTMESSAGE_UNCHOKE = BTMessage.ID_BT_UNCHOKE;


  /**
   * Translate the given core-made BT Request message into a specific plugin message adaptation.
   * @param core_made_message to translate
   * @return adapted plugin message
   */
  public static BTMessageRequest createCoreBTRequestAdaptation( Message core_made_message ) {
    //the message was originally created by core and wrapped
    com.biglybt.core.peermanager.messaging.Message core_msg = ((MessageAdapter)core_made_message).getCoreMessage();

    if( core_msg.getID().equals( BTMessage.ID_BT_REQUEST ) ) {
      return new BTMessageRequest( core_msg );
    }

    return null;
  }


  /**
   * Translate the given core-made BT Cancel message into a specific plugin message adaptation.
   * @param core_made_message to translate
   * @return adapted plugin message
   */
  public static BTMessageCancel createCoreBTCancelAdaptation( Message core_made_message ) {
    //the message was originally created by core and wrapped
    com.biglybt.core.peermanager.messaging.Message core_msg = ((MessageAdapter)core_made_message).getCoreMessage();

    if( core_msg.getID().equals( BTMessage.ID_BT_CANCEL ) ) {
      return new BTMessageCancel( core_msg );
    }

    return null;
  }


  /**
   * Translate the given core-made BT Piece message into a specific plugin message adaptation.
   * @param core_made_message to translate
   * @return adapted plugin message
   */
  public static BTMessagePiece createCoreBTPieceAdaptation( Message core_made_message ) {
    //the message was originally created by core and wrapped
    com.biglybt.core.peermanager.messaging.Message core_msg = ((MessageAdapter)core_made_message).getCoreMessage();

    if( core_msg.getID().equals( BTMessage.ID_BT_PIECE ) ) {
      return new BTMessagePiece( core_msg );
    }

    return null;
  }

  public static MessageAdapter
  wrapCoreMessage(
	 BTMessage		core_msg )
  {
	  String	id = core_msg.getID();

	  if ( id.equals( BTMessage.ID_BT_REQUEST )){

		  return new BTMessageRequest( core_msg );

	  }else if ( id.equals( BTMessage.ID_BT_CANCEL )){

		  return new BTMessageCancel( core_msg );

	  }else if ( id.equals( BTMessage.ID_BT_PIECE )){

		  return new BTMessagePiece( core_msg );

	  }else{

		  return( new MessageAdapter( core_msg ));
	  }
  }

  /**
   * Create a core BT Request message instance.
   * @param piece_number
   * @param piece_offset
   * @param length
   * @return core message wrapped in an adapter
   */
  public static Message createCoreBTRequest( int piece_number, int piece_offset, int length ) {
    return new MessageAdapter( new BTRequest( piece_number, piece_offset, length, (byte)1 ) );
  }


  /**
   * Create a core BT Cancel message instance.
   * @param piece_number
   * @param piece_offset
   * @param length
   * @return core message wrapped in an adapter
   */
  public static Message createCoreBTCancel( int piece_number, int piece_offset, int length ) {
    return new MessageAdapter( new BTCancel( piece_number, piece_offset, length, (byte)1 ) );
  }


  /**
   * Create a core BT Piece message instance.
   * @param piece_number
   * @param piece_offset
   * @param data
   * @return core message wrapped in an adapter
   */
  public static Message createCoreBTPiece( int piece_number, int piece_offset, ByteBuffer data ) {
    return new MessageAdapter( new BTPiece( piece_number, piece_offset, new DirectByteBuffer( data ), (byte)1 ) );
  }

}
