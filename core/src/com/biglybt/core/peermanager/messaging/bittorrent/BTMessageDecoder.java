/*
 * Created on Jan 24, 2005
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import com.biglybt.core.networkmanager.Transport;
import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.messaging.MessageException;
import com.biglybt.core.peermanager.messaging.MessageManager;
import com.biglybt.core.peermanager.messaging.MessageStreamDecoder;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DirectByteBuffer;
import com.biglybt.core.util.DirectByteBufferPool;


/**
 *
 */
public class BTMessageDecoder implements MessageStreamDecoder {
  private static final int MIN_MESSAGE_LENGTH = 1;  //for type id
  //private static final int MAX_MESSAGE_LENGTH = 16*1024+128;  //should never be > 16KB+9B, as we never request chunks > 16KB - update, some LT extensions can be bigger
  private static final int MAX_MESSAGE_LENGTH = 128*1024;	// 17/5/2013: parg: got a huge torrent with so many pieces the bitfield exceeds the above limit...
  private static final int HANDSHAKE_FAKE_LENGTH = 323119476;  //(byte)19 + "Bit" readInt() value of header

  private static final byte SS = DirectByteBuffer.SS_MSG;

  private DirectByteBuffer payload_buffer = null;
  private final DirectByteBuffer length_buffer = DirectByteBufferPool.getBuffer( DirectByteBuffer.AL_MSG, 4 );
  private final ByteBuffer[] decode_array = new ByteBuffer[] { null, length_buffer.getBuffer( SS ) };


  private boolean reading_length_mode = true;
  private boolean reading_handshake_message = false;

  private int message_length;
  private int pre_read_start_buffer;
  private int pre_read_start_position;

  private boolean last_received_was_keepalive = false;

  private volatile boolean destroyed = false;
  private volatile boolean is_paused = false;

  private final ArrayList messages_last_read = new ArrayList();
  private int protocol_bytes_last_read = 0;
  private int data_bytes_last_read = 0;
  
  private int	progress_id;
  private int[] progress;


  public BTMessageDecoder() {
    /* nothing */
  }



  @Override
  public int performStreamDecode(Transport transport, int max_bytes ) throws IOException {
	try{
	    protocol_bytes_last_read = 0;
	    data_bytes_last_read = 0;

	    int bytes_remaining = max_bytes;

	    while( bytes_remaining > 0 ) {

	      if( destroyed ) {

	    	  	// destruction currently isn't thread safe so one thread can destroy the decoder (e.g. when closing a connection)
	    	  	// while the read-controller is still actively processing the us
	         //throw( new IOException( "BTMessageDecoder already destroyed" ));
	         break;
	      }

	      if( is_paused ) {
	        break;
	      }

	      int bytes_possible = preReadProcess( bytes_remaining );

	      if( bytes_possible < 1 ) {
	        Debug.out( "ERROR BT: bytes_possible < 1" );
	        break;
	      }

	      if( reading_length_mode ) {
	        transport.read( decode_array, 1, 1 );  //only read into length buffer
	      }
	      else {
	        transport.read( decode_array, 0, 2 );  //read into payload buffer, and possibly next message length
	      }

	      int bytes_read = postReadProcess();

	      bytes_remaining -= bytes_read;

	      if( bytes_read < bytes_possible ) {
	        break;
	      }

	      if( reading_length_mode && last_received_was_keepalive ) {
	        //hack to stop a 0-byte-read after receiving a keep-alive message
	        //otherwise we won't realize there's nothing left on the line until trying to read again
	        last_received_was_keepalive = false;
	        break;
	      }
	    }

	    return max_bytes - bytes_remaining;

	}catch( NullPointerException e ){

			// due to lack of synchronization here the buffers can be nullified by a concurrent 'destroy'
			// turn this into something less scarey

		throw( new IOException( "Decoder has most likely been destroyed" ));
	}
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



  @Override
  public ByteBuffer destroy() {
	if (destroyed) {
		//Not like we're goig to do anything about this - parg 2018/11/17
		//Debug.out("Trying to redestroy message decoder, stack trace follows: " + this);
		//Debug.outStackTrace();
	}

    is_paused = true;
    destroyed = true;

    	// there's a concurrency issue with the decoder whereby it can be destroyed while will being messed with. Don't
    	// have the energy to look into it properly atm so just try to ensure that it doesn't bork too badly (parg: 29/04/2012)
    	// only occasional but does have potential to generate direct buffer mem leak ;(

    int lbuff_read = 0;
    int pbuff_read = 0;
    
    DirectByteBuffer lb 	= length_buffer;
    
    if ( lb != null && lb.hasBeenReturnedToPool()){
    	
    	lb = null;
    }
    
    DirectByteBuffer plb 	= payload_buffer;
   
    if ( plb != null && plb.hasBeenReturnedToPool()){
    	
	   plb = null;
    }
    
    if ( lb != null ){
    	
    	lb.limit( SS, 4 );
	
	    if ( reading_length_mode ){
	    	
	    	lbuff_read = lb.position( SS );
	    	
	    }else{ 
	    	
	    		//reading payload
	    	
	    	lb.position( SS, 4 );
	    	
	    	lbuff_read = 4;
	    	
	    	pbuff_read = plb == null ? 0 : plb.position( SS );
	    }
    }
    
    ByteBuffer unused = ByteBuffer.allocate( lbuff_read + pbuff_read );   //TODO convert to direct?

    if ( lb != null ){
    	
    	lb.flip( SS );
    	
	    unused.put( lb.getBuffer( SS ) );
	
	    try{
		    if ( plb != null ){
		    	
		    	plb.flip( SS );
		    	
		    	unused.put( plb.getBuffer( SS ) ); // Got a buffer overflow exception here in the past - related to PEX?
		    }
	    }catch( RuntimeException e ){
	    	
	    	Debug.out( "hit known threading issue" );
	    }
	
	    unused.flip();
	
	    lb.returnToPool();
    }
    
    if ( plb != null ){
    	
    	plb.returnToPool();
    	
    	payload_buffer = null;
    }

    try{
	    for( int i=0; i < messages_last_read.size(); i++ ) {
	      Message msg = (Message)messages_last_read.get( i );
	      msg.destroy();
	    }
    }catch( RuntimeException e ){
    	// happens if messages modified by alt thread...
    	Debug.out( "hit known threading issue" );
    }
    messages_last_read.clear();

    return unused;
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

      int read = payload_buffer.position( SS ) - pre_read_start_position;

      if( payload_buffer.position( SS ) > 0 ) {  //need to have read the message id first byte
      	if( BTMessageFactory.getMessageType( payload_buffer) == Message.TYPE_DATA_PAYLOAD ) {
      		data_bytes_read += read;
      	}
      	else {
      		prot_bytes_read += read;
      	}
      }

      if( !payload_buffer.hasRemaining( SS ) && !is_paused ) {  //full message received!
        payload_buffer.position( SS, 0 );

        DirectByteBuffer ref_buff = payload_buffer;
        payload_buffer = null;

        if( reading_handshake_message ) {  //decode handshake
          reading_handshake_message = false;

          DirectByteBuffer handshake_data = DirectByteBufferPool.getBuffer( DirectByteBuffer.AL_MSG_BT_HAND, 68 );
          handshake_data.putInt( SS, HANDSHAKE_FAKE_LENGTH );
          handshake_data.put( SS, ref_buff );
          handshake_data.flip( SS );

          ref_buff.returnToPool();

          try {
            Message handshake = MessageManager.getSingleton().createMessage( BTMessage.ID_BT_HANDSHAKE_BYTES, handshake_data, (byte)1 );
            messages_last_read.add( handshake );
          }
          catch( MessageException me ) {
            handshake_data.returnToPool();
            throw new IOException( "BT message decode failed: " + me.getMessage() );
          }

          //we need to auto-pause decoding until we're told to start again externally,
          //as we don't want to accidentally read the next message on the stream if it's an AZ-format handshake
          pauseDecoding();
        }
        else {  //decode normal message
          try {
            messages_last_read.add(createMessage(ref_buff));
          }
          catch( Throwable e ) {
            ref_buff.returnToPoolIfNotFree();

        	// maintain unexpected errors as such so they get logged later

            if ( e instanceof RuntimeException ){

          	  throw((RuntimeException)e );
            }

            throw new IOException( "BT message decode failed: " +e.getMessage() );
          }
        }

        reading_length_mode = true;  //see if we've already read the next message's length
        progress = null;  //reset receive percentage
        progress_id++;
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

        if( message_length == HANDSHAKE_FAKE_LENGTH ) {  //handshake message
          reading_handshake_message = true;
          message_length = 64;  //restore 'real' length
          payload_buffer = DirectByteBufferPool.getBuffer( DirectByteBuffer.AL_MSG_BT_HAND, message_length );
        }
        else if( message_length == 0 ) {  //keep-alive message
          reading_length_mode = true;
          last_received_was_keepalive = true;

          try{
            Message keep_alive = MessageManager.getSingleton().createMessage( BTMessage.ID_BT_KEEP_ALIVE_BYTES, null, (byte)1 );
            messages_last_read.add( keep_alive );
          }
          catch( MessageException me ) {
            throw new IOException( "BT message decode failed: " + me.getMessage() );
          }
        }
        else if( message_length < MIN_MESSAGE_LENGTH || message_length > MAX_MESSAGE_LENGTH ) {
          throw new IOException( "Invalid message length given for BT message decode: " + message_length );
        }
        else {  //normal message
          payload_buffer = DirectByteBufferPool.getBuffer( DirectByteBuffer.AL_MSG_BT_PAYLOAD, message_length );
        }
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

  // Overridden by LTMessageDecoder.
  protected Message createMessage(DirectByteBuffer ref_buff) throws MessageException {
      try {return BTMessageFactory.createBTMessage(ref_buff);}
      catch (MessageException me) {
    	  /*if (identifier != null && me.getMessage() != null && me.getMessage().startsWith("Unknown BT message id")) {
    		  System.out.println(identifier + " " + me.getMessage());
    	  }*/
    	  throw me;
      }
  }

}
