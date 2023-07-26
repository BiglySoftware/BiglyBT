/*
 * Created on Feb 8, 2005
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import com.biglybt.core.networkmanager.Transport;
import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.messaging.MessageManager;
import com.biglybt.core.peermanager.messaging.MessageStreamDecoder;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DirectByteBuffer;
import com.biglybt.core.util.DirectByteBufferPool;



/**
 * Length-prefixed message decoding.
 */
public class AZMessageDecoder implements MessageStreamDecoder {
  private static final int MIN_MESSAGE_LENGTH = 6;  //4 byte id length + at least 1 byte for id + 1 byte version
  private static final int MAX_MESSAGE_LENGTH = 131072;  //128K arbitrary limit

  private static final byte SS = DirectByteBuffer.SS_MSG;


  private DirectByteBuffer payload_buffer = null;
  private final DirectByteBuffer length_buffer = DirectByteBufferPool.getBuffer( DirectByteBuffer.AL_MSG, 4 );
  private final ByteBuffer[] decode_array = new ByteBuffer[] { null, length_buffer.getBuffer( SS ) };

  private boolean reading_length_mode = true;

  private int message_length;
  private int pre_read_start_buffer;
  private int pre_read_start_position;

  private volatile boolean destroyed = false;
  private volatile boolean is_paused = false;

  private final ArrayList messages_last_read = new ArrayList();
  private int protocol_bytes_last_read = 0;
  private int data_bytes_last_read = 0;
  
  private int	progress_id;
  private int[] progress;


  private byte[] msg_id_bytes = null;
  private boolean msg_id_read_complete = false;

  private boolean last_read_made_progress;

  private int	maximum_message_size = MAX_MESSAGE_LENGTH;

  public AZMessageDecoder() {
    /*nothing*/
  }

  public void
  setMaximumMessageSize(
	int	max_bytes )
  {
	  maximum_message_size	= max_bytes;
  }

  @Override
  public int performStreamDecode(Transport transport, int max_bytes ) throws IOException {
    protocol_bytes_last_read = 0;
    data_bytes_last_read = 0;

    int bytes_remaining = max_bytes;

    while( bytes_remaining > 0 ) {
      if( destroyed ) {
         //destruction currently isn't thread safe so one thread can destroy the decoder (e.g. when closing a connection)
         //while the read-controller is still actively processing the us
        //Debug.out( "AZ decoder already destroyed: " +transport.getDescription() );
        break;
      }

      if( is_paused ) {
        Debug.out( "AZ decoder paused" );
        break;
      }

      int bytes_possible = preReadProcess( bytes_remaining );

      if( bytes_possible < 1 ) {
        Debug.out( "ERROR AZ: bytes_possible < 1" );
        break;
      }

      long	actual_read;

      if( reading_length_mode ) {
    	  actual_read = transport.read( decode_array, 1, 1 );  //only read into length buffer
      }
      else {
    	  actual_read = transport.read( decode_array, 0, 2 );  //read payload buffer, and possibly next message length buffer
      }

      last_read_made_progress = actual_read > 0;

      int bytes_read = postReadProcess();

      bytes_remaining -= bytes_read;

      if( bytes_read < bytes_possible ) {
        break;
      }
    }

    return max_bytes - bytes_remaining;
  }



  @Override
  public int[] getCurrentMessageProgress() {
    return( progress );
  }



  @Override
  public Message[] removeDecodedMessages() {
    if( messages_last_read.isEmpty() )  return null;

    Message[] msgs = (Message[])messages_last_read.toArray( new Message[messages_last_read.size()] );
    messages_last_read.clear();

    return msgs;
  }



  @Override
  public int getProtocolBytesDecoded() {  return protocol_bytes_last_read;  }



  @Override
  public int getDataBytesDecoded() {  return data_bytes_last_read;  }

  public boolean getLastReadMadeProgress(){ return last_read_made_progress; }

	@Override
	public ByteBuffer destroy() {
    is_paused = true;
    destroyed = true;

    /*
    int lbuff_read = 0;
    int pbuff_read = 0;
    length_buffer.limit( SS, 4 );

    if( reading_length_mode ) {
      lbuff_read = length_buffer.position( SS );
    }
    else { //reading payload
      length_buffer.position( SS, 4 );
      lbuff_read = 4;
      pbuff_read = payload_buffer == null ? 0 : payload_buffer.position( SS );
    }

    ByteBuffer unused = ByteBuffer.allocate( lbuff_read + pbuff_read );

    length_buffer.flip( SS );
    unused.put( length_buffer.getBuffer( SS ) );

    if ( payload_buffer != null ) {
      payload_buffer.flip( SS );
      unused.put( payload_buffer.getBuffer( SS ) );
    }

    unused.flip();
    */

    length_buffer.returnToPool();

    if( payload_buffer != null ) {
      payload_buffer.returnToPool();
      payload_buffer = null;
    }

    try{
	    for( int i=0; i < messages_last_read.size(); i++ ) {
	      Message msg = (Message)messages_last_read.get( i );
	      msg.destroy();
	    }
    }catch( IndexOutOfBoundsException e ){
    	// as access to messages_last_read isn't synchronized we can get this error if we destroy the
    	// decoder in parallel with messages being removed. We don't really want to synchronize access
    	// to this so we'll take the hit here
    }

    messages_last_read.clear();

    //return unused;
    return null;  //NOTE: we don't bother returning any already-read data
  }





  private int preReadProcess( int allowed ) {
    if( allowed < 1 ) {
      Debug.out( "allowed < 1" );
    }

    decode_array[ 0 ] = payload_buffer == null ? null : payload_buffer.getBuffer( SS );  //ensure the decode array has the latest payload pointer

    int bytes_available = 0;
    boolean shrink_remaining_buffers = false;
    int start_buff = reading_length_mode ? 1 : 0;
    boolean marked = false;

    for( int i = start_buff; i < 2; i++ ) {  //set buffer limits according to bytes allowed
      ByteBuffer bb = decode_array[ i ];

      if( bb == null ) {
        Debug.out( "preReadProcess:: bb["+i+"] == null, decoder destroyed=" +destroyed );

        throw( new RuntimeException( "decoder destroyed" ));
      }


      if( shrink_remaining_buffers ) {
        bb.limit( 0 );  //ensure no read into this next buffer is possible
      }
      else {
        int remaining = bb.remaining();

        if( remaining < 1 )  continue;  //skip full buffer

        if( !marked ) {
          pre_read_start_buffer = i;
          pre_read_start_position = bb.position();
          marked = true;
        }

        if( remaining > allowed ) {  //read only part of this buffer
          bb.limit( bb.position() + allowed );  //limit current buffer
          bytes_available += bb.remaining();
          shrink_remaining_buffers = true;  //shrink any tail buffers
        }
        else {  //full buffer is allowed to be read
          bytes_available += remaining;
          allowed -= remaining;  //count this buffer toward allowed and move on to the next
        }
      }
    }

    return bytes_available;
  }




  private int postReadProcess() throws IOException {
  	int prot_bytes_read = 0;
    int data_bytes_read = 0;

    if( !reading_length_mode && !destroyed ) {  //reading payload data mode
      //ensure-restore proper buffer limits
      payload_buffer.limit( SS, message_length );
      length_buffer.limit( SS, 4 );

      int curr_position = payload_buffer.position( SS );
      int read = curr_position - pre_read_start_position;

      if( msg_id_bytes == null && curr_position >= 4 ) {  //need to have read the message id length first 4 bytes
      	payload_buffer.position( SS, 0 );
      	int id_size = payload_buffer.getInt( SS );
      	payload_buffer.position( SS, curr_position );  //restore
      	if( id_size < 1 || id_size > 1024 )  throw new IOException( "invalid id_size [" +id_size+ "]" );
      	msg_id_bytes = new byte[ id_size ];
      }

      if( msg_id_bytes != null && curr_position >= msg_id_bytes.length + 4 ) {  //need to have also read the message id bytes
      	if( !msg_id_read_complete ) {
      		payload_buffer.position( SS, 4 );
      		payload_buffer.get( SS, msg_id_bytes );
      		payload_buffer.position( SS, curr_position );  //restore
      		msg_id_read_complete = true;
      	}

      	Message message = MessageManager.getSingleton().lookupMessage( msg_id_bytes );

      	if ( message == null ){

      		Debug.out( "Unknown message type '" + new String( msg_id_bytes ) + "'" );

      		throw( new IOException( "Unknown message type" ));
      	}

      	if( message.getType() == Message.TYPE_DATA_PAYLOAD ) {
      		data_bytes_read += read;
      	}else{
      		prot_bytes_read += read;
      	}
      }
      else {
      	prot_bytes_read += read;
      }

      if( !payload_buffer.hasRemaining( SS ) && !is_paused ) {  //full message received!
        payload_buffer.position( SS, 0 );  //prepare for use

        DirectByteBuffer ref_buff = payload_buffer;
        payload_buffer = null;

        try {
          Message msg = AZMessageFactory.createAZMessage( ref_buff );
          messages_last_read.add( msg );
        }
        catch( Throwable e ) {
          ref_buff.returnToPoolIfNotFree();

          	// maintain unexpected errors as such so they get logged later

          if ( e instanceof RuntimeException ){

        	  throw((RuntimeException)e );
          }

          throw new IOException( "AZ message decode failed: " + e.getMessage() );
        }

        reading_length_mode = true;  //see if we've already read the next message's length
        progress = null;  //reset receive percentage
        progress_id++;
        msg_id_bytes = null;
        msg_id_read_complete = false;
      }
      else {  //only partial received so far
        progress = new int[]{ message_length, payload_buffer.position( SS ), progress_id };
      }
    }


    if( reading_length_mode && !destroyed ) {
      length_buffer.limit( SS, 4 );  //ensure proper buffer limit

      prot_bytes_read += (pre_read_start_buffer == 1) ? length_buffer.position( SS ) - pre_read_start_position : length_buffer.position( SS );

      if( !length_buffer.hasRemaining( SS ) ) {  //done reading the length
        reading_length_mode = false;
        length_buffer.position( SS, 0 );

        message_length = length_buffer.getInt( SS );

        length_buffer.position( SS, 0 );  //reset it for next length read

        if( message_length < MIN_MESSAGE_LENGTH || message_length > maximum_message_size ) {
          throw new IOException( "Invalid message length given for AZ message decode: " + message_length + " (max=" + maximum_message_size + ")" );
        }

        payload_buffer = DirectByteBufferPool.getBuffer( DirectByteBuffer.AL_MSG_AZ_PAYLOAD, message_length );
      }
    }

    protocol_bytes_last_read += prot_bytes_read;
    data_bytes_last_read += data_bytes_read;

    return prot_bytes_read + data_bytes_read;
  }



  @Override
  public void pauseDecoding() {
    is_paused = true;
  }


  @Override
  public void resumeDecoding() {
    is_paused = false;
  }


}
