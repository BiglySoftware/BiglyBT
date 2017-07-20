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

package com.biglybt.core.peermanager.messaging.bittorrent;

import java.util.HashMap;

import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.networkmanager.RawMessage;
import com.biglybt.core.networkmanager.impl.RawMessageImpl;
import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.messaging.MessageException;
import com.biglybt.core.peermanager.messaging.MessageManager;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DirectByteBuffer;
import com.biglybt.core.util.DirectByteBufferPool;


/**
 *
 */
public class BTMessageFactory {
  public static final byte MESSAGE_VERSION_INITIAL				= 1;
  public static final byte MESSAGE_VERSION_SUPPORTS_PADDING		= 2;	// most of these messages are also used by AZ code

  private static final LogIDs LOGID = LogIDs.PEER;

  /**
   * Initialize the factory, i.e. register the messages with the message manager.
   */
  public static void init() {
    try {
      MessageManager.getSingleton().registerMessageType( new BTBitfield( null, MESSAGE_VERSION_SUPPORTS_PADDING ));
      MessageManager.getSingleton().registerMessageType( new BTCancel( -1, -1, -1, MESSAGE_VERSION_SUPPORTS_PADDING ));
      MessageManager.getSingleton().registerMessageType( new BTChoke( MESSAGE_VERSION_SUPPORTS_PADDING ));
      MessageManager.getSingleton().registerMessageType( new BTHandshake( new byte[0], new byte[0], BTHandshake.AZ_RESERVED_MODE, MESSAGE_VERSION_INITIAL ));
      MessageManager.getSingleton().registerMessageType( new BTHave( -1, MESSAGE_VERSION_SUPPORTS_PADDING ));
      MessageManager.getSingleton().registerMessageType( new BTInterested( MESSAGE_VERSION_SUPPORTS_PADDING ));
      MessageManager.getSingleton().registerMessageType( new BTKeepAlive(MESSAGE_VERSION_SUPPORTS_PADDING ));
      MessageManager.getSingleton().registerMessageType( new BTPiece( -1, -1, null, MESSAGE_VERSION_SUPPORTS_PADDING ));
      MessageManager.getSingleton().registerMessageType( new BTRequest( -1, -1 , -1, MESSAGE_VERSION_SUPPORTS_PADDING ));
      MessageManager.getSingleton().registerMessageType( new BTUnchoke( MESSAGE_VERSION_SUPPORTS_PADDING ));
      MessageManager.getSingleton().registerMessageType( new BTUninterested( MESSAGE_VERSION_SUPPORTS_PADDING ));
      MessageManager.getSingleton().registerMessageType( new BTSuggestPiece( -1, MESSAGE_VERSION_SUPPORTS_PADDING ));
      MessageManager.getSingleton().registerMessageType( new BTHaveAll( MESSAGE_VERSION_SUPPORTS_PADDING ));
      MessageManager.getSingleton().registerMessageType( new BTHaveNone( MESSAGE_VERSION_SUPPORTS_PADDING ));
      MessageManager.getSingleton().registerMessageType( new BTRejectRequest( -1, -1, -1, MESSAGE_VERSION_SUPPORTS_PADDING ));
      MessageManager.getSingleton().registerMessageType( new BTAllowedFast( -1, MESSAGE_VERSION_SUPPORTS_PADDING ));
      MessageManager.getSingleton().registerMessageType( new BTLTMessage( null, MESSAGE_VERSION_SUPPORTS_PADDING ));
      MessageManager.getSingleton().registerMessageType( new BTDHTPort(-1));
    }
    catch( MessageException me ) {  me.printStackTrace();  }
  }




  private static final String[] id_to_name = new String[21];
  private static final HashMap legacy_data = new HashMap();
  static {
    legacy_data.put( BTMessage.ID_BT_CHOKE, new LegacyData( RawMessage.PRIORITY_HIGH, true, new Message[]{new BTUnchoke((byte)0), new BTPiece( -1, -1, null,(byte)0 )}, (byte)0 ) );
    id_to_name[0] = BTMessage.ID_BT_CHOKE;

    legacy_data.put( BTMessage.ID_BT_UNCHOKE, new LegacyData( RawMessage.PRIORITY_NORMAL, true, new Message[]{new BTChoke((byte)0)}, (byte)1 ) );
    id_to_name[1] = BTMessage.ID_BT_UNCHOKE;

    legacy_data.put( BTMessage.ID_BT_INTERESTED, new LegacyData( RawMessage.PRIORITY_HIGH, true, new Message[]{new BTUninterested((byte)0)}, (byte)2 ) );
    id_to_name[2] = BTMessage.ID_BT_INTERESTED;

    legacy_data.put( BTMessage.ID_BT_UNINTERESTED, new LegacyData( RawMessage.PRIORITY_NORMAL, false, new Message[]{new BTInterested((byte)0)}, (byte)3 ) );
    id_to_name[3] = BTMessage.ID_BT_UNINTERESTED;

    legacy_data.put( BTMessage.ID_BT_HAVE, new LegacyData( RawMessage.PRIORITY_LOW, false, null, (byte)4 ) );
    id_to_name[4] = BTMessage.ID_BT_HAVE;

    legacy_data.put( BTMessage.ID_BT_BITFIELD, new LegacyData( RawMessage.PRIORITY_HIGH, true, null, (byte)5 ) );
    id_to_name[5] = BTMessage.ID_BT_BITFIELD;

    legacy_data.put( BTMessage.ID_BT_REQUEST, new LegacyData( RawMessage.PRIORITY_NORMAL, true, null, (byte)6 ) );
    id_to_name[6] = BTMessage.ID_BT_REQUEST;

    legacy_data.put( BTMessage.ID_BT_PIECE, new LegacyData( RawMessage.PRIORITY_LOW, false, null, (byte)7 ) );
    id_to_name[7] = BTMessage.ID_BT_PIECE;

    legacy_data.put( BTMessage.ID_BT_CANCEL, new LegacyData( RawMessage.PRIORITY_HIGH, true, null, (byte)8 ) );
    id_to_name[8] = BTMessage.ID_BT_CANCEL;

    legacy_data.put( BTMessage.ID_BT_DHT_PORT, new LegacyData( RawMessage.PRIORITY_LOW, true, null, (byte)9 ) );
    id_to_name[9] = BTMessage.ID_BT_DHT_PORT;

    legacy_data.put( BTMessage.ID_BT_SUGGEST_PIECE, new LegacyData( RawMessage.PRIORITY_NORMAL, true, null, (byte)13 ) );
    id_to_name[13] = BTMessage.ID_BT_SUGGEST_PIECE;

    legacy_data.put( BTMessage.ID_BT_HAVE_ALL, new LegacyData( RawMessage.PRIORITY_HIGH, true, null, (byte)14 ) );
    id_to_name[14] = BTMessage.ID_BT_HAVE_ALL;

    legacy_data.put( BTMessage.ID_BT_HAVE_NONE, new LegacyData( RawMessage.PRIORITY_HIGH, true, null, (byte)15 ) );
    id_to_name[15] = BTMessage.ID_BT_HAVE_NONE;

    legacy_data.put( BTMessage.ID_BT_REJECT_REQUEST, new LegacyData( RawMessage.PRIORITY_NORMAL, true, null, (byte)16 ) );
    id_to_name[16] = BTMessage.ID_BT_REJECT_REQUEST;

    legacy_data.put( BTMessage.ID_BT_ALLOWED_FAST, new LegacyData( RawMessage.PRIORITY_LOW, false, null, (byte)17 ) );
    id_to_name[17] = BTMessage.ID_BT_ALLOWED_FAST;

    legacy_data.put( BTMessage.ID_BT_LT_EXT_MESSAGE, new LegacyData( RawMessage.PRIORITY_HIGH, true, null, (byte)20 ) );
    id_to_name[20] = BTMessage.ID_BT_LT_EXT_MESSAGE;
  }






  /**
   * Construct a new BT message instance from the given message raw byte stream.
   * @param stream_payload data
   * @return decoded/deserialized BT message
   * @throws MessageException if message creation failed
   * NOTE: Does not auto-return given direct buffer on thrown exception.
   */
  public static Message createBTMessage( DirectByteBuffer stream_payload) throws MessageException {
    byte id = stream_payload.get( DirectByteBuffer.SS_MSG );

    switch( id ) {
      case 0:
        return MessageManager.getSingleton().createMessage( BTMessage.ID_BT_CHOKE_BYTES, stream_payload, (byte)1 );

      case 1:
        return MessageManager.getSingleton().createMessage( BTMessage.ID_BT_UNCHOKE_BYTES, stream_payload, (byte)1 );

      case 2:
        return MessageManager.getSingleton().createMessage( BTMessage.ID_BT_INTERESTED_BYTES, stream_payload, (byte)1 );

      case 3:
        return MessageManager.getSingleton().createMessage( BTMessage.ID_BT_UNINTERESTED_BYTES, stream_payload, (byte)1 );

      case 4:
        return MessageManager.getSingleton().createMessage( BTMessage.ID_BT_HAVE_BYTES, stream_payload, (byte)1 );

      case 5:
        return MessageManager.getSingleton().createMessage( BTMessage.ID_BT_BITFIELD_BYTES, stream_payload, (byte)1 );

      case 6:
        return MessageManager.getSingleton().createMessage( BTMessage.ID_BT_REQUEST_BYTES, stream_payload, (byte)1 );

      case 7:
        return MessageManager.getSingleton().createMessage( BTMessage.ID_BT_PIECE_BYTES, stream_payload, (byte)1 );

      case 8:
        return MessageManager.getSingleton().createMessage( BTMessage.ID_BT_CANCEL_BYTES, stream_payload, (byte)1 );

      case 9:
    	return MessageManager.getSingleton().createMessage( BTMessage.ID_BT_DHT_PORT_BYTES, stream_payload, (byte)1 );

      case 13:
      	return MessageManager.getSingleton().createMessage( BTMessage.ID_BT_SUGGEST_PIECE_BYTES, stream_payload, (byte)1 );

      case 14:
      	return MessageManager.getSingleton().createMessage( BTMessage.ID_BT_HAVE_ALL_BYTES, stream_payload, (byte)1 );

      case 15:
      	return MessageManager.getSingleton().createMessage( BTMessage.ID_BT_HAVE_NONE_BYTES, stream_payload, (byte)1 );

      case 16:
      	return MessageManager.getSingleton().createMessage( BTMessage.ID_BT_REJECT_REQUEST_BYTES, stream_payload, (byte)1 );

      case 17:
      	return MessageManager.getSingleton().createMessage( BTMessage.ID_BT_ALLOWED_FAST_BYTES, stream_payload, (byte)1 );

      case 20:
    	  //Clients seeing our handshake reserved bit will send us the old 'extended' messaging hello message accidentally.
   		  //Instead of throwing an exception and dropping the peer connection, we'll just fake it as a keep-alive :)
  	      if (Logger.isEnabled())
  					Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
  							"Old extended messaging hello received (or malformed LT extension message), "
  									+ "ignoring and faking as keep-alive."));
  	        return MessageManager.getSingleton().createMessage( BTMessage.ID_BT_KEEP_ALIVE_BYTES, null, (byte)1 );

      default: {  System.out.println( "Unknown BT message id [" +id+ "]" );
        					throw new MessageException( "Unknown BT message id [" +id+ "]" );
      				}
    }
  }



  public static int getMessageType( DirectByteBuffer stream_payload ) {
  	byte id = stream_payload.get( DirectByteBuffer.SS_MSG, 0 );
  	if( id == 84 )  return Message.TYPE_PROTOCOL_PAYLOAD;  //handshake message byte in position 4
  	if ( id >= 0 && id < id_to_name.length ){
 		String name = id_to_name[ id ];

 		if ( name != null ){

 			Message message = MessageManager.getSingleton().lookupMessage( name );

 			if ( message != null ){

 				return( message.getType());
 			}
 		}
  	}
  	// invalid, return whatever
  	return Message.TYPE_PROTOCOL_PAYLOAD;
  }



  /**
   * Create the proper BT raw message from the given base message.
   * @param base_message to create from
   * @return BT raw message
   */
  public static RawMessage createBTRawMessage( Message base_message ) {
    if( base_message instanceof RawMessage ) {  //used for handshake and keep-alive messages
      return (RawMessage)base_message;
    }

    LegacyData ld = (LegacyData)legacy_data.get( base_message.getID() );

    if( ld == null ) {
      Debug.out( "legacy message type id not found for [" +base_message.getID()+ "]" );
      return null;  //message id type not found
    }

    DirectByteBuffer[] payload = base_message.getData();

    int payload_size = 0;
    for( int i=0; i < payload.length; i++ ) {
      payload_size += payload[i].remaining( DirectByteBuffer.SS_MSG );
    }

    DirectByteBuffer header = DirectByteBufferPool.getBuffer( DirectByteBuffer.AL_MSG_BT_HEADER, 5 );
    header.putInt( DirectByteBuffer.SS_MSG, 1 + payload_size );
    header.put( DirectByteBuffer.SS_MSG, ld.bt_id );
    header.flip( DirectByteBuffer.SS_MSG );

    DirectByteBuffer[] raw_buffs = new DirectByteBuffer[ payload.length + 1 ];
    raw_buffs[0] = header;
    System.arraycopy(payload, 0, raw_buffs, 1, payload.length);

    return new RawMessageImpl( base_message, raw_buffs, ld.priority, ld.is_no_delay, ld.to_remove );
  }




  protected static class LegacyData {
  	protected final int priority;
  	protected final boolean is_no_delay;
  	protected final Message[] to_remove;
  	protected final byte bt_id;

  	protected LegacyData( int prio, boolean no_delay, Message[] remove, byte btid ) {
      this.priority = prio;
      this.is_no_delay = no_delay;
      this.to_remove = remove;
      this.bt_id = btid;
    }
  }
}
