/*
 * Created on Feb 19, 2005
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

package com.biglybt.core.peermanager.messaging.azureus;

import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.networkmanager.RawMessage;
import com.biglybt.core.networkmanager.impl.RawMessageImpl;
import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.messaging.MessageException;
import com.biglybt.core.peermanager.messaging.MessageManager;
import com.biglybt.core.peermanager.messaging.bittorrent.*;
import com.biglybt.core.util.DirectByteBuffer;
import com.biglybt.core.util.DirectByteBufferPool;
import com.biglybt.core.util.RandomUtils;




/**
 * Factory for handling AZ message creation.
 * NOTE: wire format: [total message length] + [id length] + [id bytes] + [version byte] + [payload bytes]
 */
public class AZMessageFactory {
  public static final byte MESSAGE_VERSION_INITIAL				= BTMessageFactory.MESSAGE_VERSION_INITIAL;
  public static final byte MESSAGE_VERSION_SUPPORTS_PADDING		= BTMessageFactory.MESSAGE_VERSION_SUPPORTS_PADDING;

  public static final int AZ_HANDSHAKE_PAD_MAX		= 64;
  public static final int SMALL_PAD_MAX				= 8;
  public static final int BIG_PAD_MAX				= 20;

  private static final byte bss = DirectByteBuffer.SS_MSG;



  private static final Map<String,LegacyData> legacy_data = new HashMap<>();
  static {
    legacy_data.put( BTMessage.ID_BT_CHOKE, new LegacyData( RawMessage.PRIORITY_HIGH, true, new Message[]{new BTUnchoke((byte)0)})); // with support for fast extension we don't cancel outstanding piece data, new BTPiece(-1, -1, null,(byte)0 )} ) );
    legacy_data.put( BTMessage.ID_BT_UNCHOKE, new LegacyData( RawMessage.PRIORITY_NORMAL, true, new Message[]{new BTChoke((byte)0)} ) );
    legacy_data.put( BTMessage.ID_BT_INTERESTED, new LegacyData( RawMessage.PRIORITY_HIGH, true, new Message[]{new BTUninterested((byte)0)} ) );
    legacy_data.put( BTMessage.ID_BT_UNINTERESTED, new LegacyData( RawMessage.PRIORITY_NORMAL, false, new Message[]{new BTInterested((byte)0)} ) );
    legacy_data.put( BTMessage.ID_BT_HAVE, new LegacyData( RawMessage.PRIORITY_LOW, false, null ) );
    legacy_data.put( BTMessage.ID_BT_BITFIELD, new LegacyData( RawMessage.PRIORITY_HIGH, true, null ) );
    legacy_data.put( BTMessage.ID_BT_HAVE_ALL, new LegacyData( RawMessage.PRIORITY_HIGH, true, null ) );
    legacy_data.put( BTMessage.ID_BT_HAVE_NONE, new LegacyData( RawMessage.PRIORITY_HIGH, true, null ) );
    legacy_data.put( BTMessage.ID_BT_REQUEST, new LegacyData( RawMessage.PRIORITY_NORMAL, true, null ) );
    legacy_data.put( BTMessage.ID_BT_REJECT_REQUEST, new LegacyData( RawMessage.PRIORITY_NORMAL, true, null ) );
    legacy_data.put( BTMessage.ID_BT_PIECE, new LegacyData( RawMessage.PRIORITY_LOW, false, null ) );
    legacy_data.put( BTMessage.ID_BT_CANCEL, new LegacyData( RawMessage.PRIORITY_HIGH, true, null ) );
    legacy_data.put( BTMessage.ID_BT_HANDSHAKE, new LegacyData( RawMessage.PRIORITY_HIGH, true, null ) );
    legacy_data.put( BTMessage.ID_BT_KEEP_ALIVE, new LegacyData( RawMessage.PRIORITY_LOW, false, null ) );
    legacy_data.put( BTMessage.ID_BT_DHT_PORT, new LegacyData( RawMessage.PRIORITY_LOW, false, null ) );
    legacy_data.put( BTMessage.ID_BT_SUGGEST_PIECE, new LegacyData( RawMessage.PRIORITY_NORMAL, true, null ) );
    legacy_data.put( BTMessage.ID_BT_ALLOWED_FAST, new LegacyData( RawMessage.PRIORITY_LOW, false, null ) );
  }



  /**
   * Initialize the factory, i.e. register the messages with the message manager.
   */
  public static void init() {
    try {
      MessageManager.getSingleton().registerMessageType( new AZHandshake( new byte[20], null, null, "", "", 0, 0, 0, null, null, 0, new String[0], new byte[0], 0, MESSAGE_VERSION_SUPPORTS_PADDING,false ) );
      MessageManager.getSingleton().registerMessageType( new AZPeerExchange( new byte[20], null, null, MESSAGE_VERSION_SUPPORTS_PADDING ));
      MessageManager.getSingleton().registerMessageType( new AZRequestHint( -1, -1, -1, -1, MESSAGE_VERSION_SUPPORTS_PADDING ));
      MessageManager.getSingleton().registerMessageType( new AZHave( new int[0], MESSAGE_VERSION_SUPPORTS_PADDING ));
      MessageManager.getSingleton().registerMessageType( new AZBadPiece( -1, MESSAGE_VERSION_SUPPORTS_PADDING ));
      MessageManager.getSingleton().registerMessageType( new AZStatRequest( null, MESSAGE_VERSION_SUPPORTS_PADDING ));
      MessageManager.getSingleton().registerMessageType( new AZStatReply( null, MESSAGE_VERSION_SUPPORTS_PADDING ));
      MessageManager.getSingleton().registerMessageType( new AZMetaData( null, null, MESSAGE_VERSION_SUPPORTS_PADDING ));

      /*
      MessageManager.getSingleton().registerMessageType( new AZSessionSyn( new byte[20], -1, null) );
      MessageManager.getSingleton().registerMessageType( new AZSessionAck( new byte[20], -1, null) );
      MessageManager.getSingleton().registerMessageType( new AZSessionEnd( new byte[20], "" ) );
      MessageManager.getSingleton().registerMessageType( new AZSessionBitfield( -1, null ) );
      MessageManager.getSingleton().registerMessageType( new AZSessionCancel( -1, -1, -1, -1 ) );
      MessageManager.getSingleton().registerMessageType( new AZSessionHave( -1, new int[]{-1} ) );
      MessageManager.getSingleton().registerMessageType( new AZSessionPiece( -1, -1, -1, null ) );
      MessageManager.getSingleton().registerMessageType( new AZSessionRequest( -1, (byte)-1, -1, -1, -1 ) );
      */
    }
    catch( MessageException me ) {  me.printStackTrace();  }
  }


  /**
   * Register a generic map payload type with the factory.
   * @param type_id to register
   * @throws MessageException on registration error
   */
  public static void registerGenericMapPayloadMessageType( String type_id ) throws MessageException {
  	MessageManager.getSingleton().registerMessageType( new AZGenericMapPayload( type_id, null, MESSAGE_VERSION_INITIAL ) );
  }



  /**
   * Construct a new AZ message instance from the given message raw byte stream.
   * @param stream_payload data
   * @return decoded/deserialized AZ message
   * @throws MessageException if message creation failed.
   * NOTE: Does not auto-return to buffer pool the given direct buffer on thrown exception.
   */
  public static Message createAZMessage( DirectByteBuffer stream_payload ) throws MessageException {
    int id_length = stream_payload.getInt( bss );

    if( id_length < 1 || id_length > 1024 || id_length > stream_payload.remaining( bss ) - 1 ) {
      byte bt_id = stream_payload.get( (byte)0, 0 );
      throw new MessageException( "invalid AZ id length given: " +id_length+ ", stream_payload.remaining(): " +stream_payload.remaining( bss )+ ", BT id?=" +bt_id );
    }

    byte[] id_bytes = new byte[ id_length ];

    stream_payload.get( bss, id_bytes );

    	// if only the version came first we could save a lot of space by changing the id length + id....

    	// in the meantime we overload the version byte to have a version number and flags
    	// flags = top 4 bits, version = bottom 4 bits

    byte version_and_flags = stream_payload.get( bss );

    byte version = (byte)( version_and_flags & 0x0f );

    if ( version >= MESSAGE_VERSION_SUPPORTS_PADDING ){

    	byte	flags =  (byte)(( version_and_flags >> 4 ) & 0x0f );

    	if ( ( flags & 0x01 ) != 0 ){

    		short padding_length = stream_payload.getShort( bss );

    		byte[]	padding = new byte[padding_length];

    		stream_payload.get( bss, padding );
    	}
    }

    return MessageManager.getSingleton().createMessage( id_bytes, stream_payload, version );
  }




  /**
   * Create the proper AZ raw message from the given base message.
   * @param base_message to create from
   * @return AZ raw message
   */
  public static RawMessage createAZRawMessage( Message base_message, int padding_mode ) {
    byte[] id_bytes = base_message.getIDBytes();
    byte version = base_message.getVersion();

    DirectByteBuffer[] payload = base_message.getData();

    int payload_size = 0;
    for( int i=0; i < payload.length; i++ ) {
      payload_size += payload[i].remaining( bss );
    }

    //create and fill header buffer

    DirectByteBuffer header;

    if ( version >= MESSAGE_VERSION_SUPPORTS_PADDING ){

    	boolean enable_padding = padding_mode != AZMessageEncoder.PADDING_MODE_NONE;

    	short 	padding_length;

    	if ( enable_padding ){

    		if ( padding_mode == AZMessageEncoder.PADDING_MODE_MINIMAL ){

       			padding_length = (short)( RandomUtils.nextInt( SMALL_PAD_MAX  ));

    		}else{

    			padding_length = (short)( RandomUtils.nextInt( payload_size>256?SMALL_PAD_MAX:BIG_PAD_MAX ));
    		}

    		if ( padding_length == 0 ){

    			enable_padding = false;
    		}
    	}else{

    		padding_length = 0;
    	}

    	byte	flags = enable_padding?(byte)0x01:(byte)0x00;

    	int	header_size = 4 + 4 + id_bytes.length + 1 + (enable_padding?(2+padding_length):0);

        header = DirectByteBufferPool.getBuffer( DirectByteBuffer.AL_MSG_AZ_HEADER, header_size );

	    header.putInt( bss, header_size - 4 + payload_size );
	    header.putInt( bss, id_bytes.length );
	    header.put( bss, id_bytes );

	    byte version_and_flags = (byte)( ( flags << 4 ) | version );

	    header.put( bss, version_and_flags );

	    if ( enable_padding ){

	    	byte[]	padding = new byte[padding_length];

	    	header.putShort( bss, padding_length );
	    	header.put( bss, padding );
	    }
    }else{

	   	int	header_size = 4 + 4 + id_bytes.length + 1;

	    header = DirectByteBufferPool.getBuffer( DirectByteBuffer.AL_MSG_AZ_HEADER, header_size );

	    header.putInt( bss, header_size - 4 + payload_size );
	    header.putInt( bss, id_bytes.length );
	    header.put( bss, id_bytes );
	    header.put( bss, version );
    }

    header.flip( bss );

    DirectByteBuffer[] raw_buffs = new DirectByteBuffer[ payload.length + 1 ];
    raw_buffs[0] = header;
	  System.arraycopy(payload, 0, raw_buffs, 1, payload.length);

    String message_id = base_message.getID();

    LegacyData ld = (LegacyData)legacy_data.get( message_id );  //determine if a legacy BT message

    if( ld != null ) {  //legacy message, use pre-configured values
      return new RawMessageImpl( base_message, raw_buffs, ld.priority, ld.is_no_delay, ld.to_remove );
    }

    	// these should really be properties of the message...

    int 	priority;
    boolean	no_delay	= true;

    if ( message_id == AZMessage.ID_AZ_HANDSHAKE ){

    		// handshake needs to go out first - if not high then bitfield can get in front of it...

    	priority = RawMessage.PRIORITY_HIGH;

    }else if ( message_id == AZMessage.ID_AZ_HAVE ){

    	priority 	= RawMessage.PRIORITY_LOW;
    	no_delay	= false;

    }else{

    	   //standard message, ensure that protocol messages have wire priority over data payload messages

    	priority = base_message.getType() == Message.TYPE_DATA_PAYLOAD ? RawMessage.PRIORITY_LOW : RawMessage.PRIORITY_NORMAL;
    }

    return new RawMessageImpl( base_message, raw_buffs, priority, no_delay, null );
  }






  protected static class LegacyData {
  	protected final int priority;
    protected final boolean is_no_delay;
    protected final Message[] to_remove;

    protected LegacyData( int prio, boolean no_delay, Message[] remove ) {
      this.priority = prio;
      this.is_no_delay = no_delay;
      this.to_remove = remove;
    }
  }

}
