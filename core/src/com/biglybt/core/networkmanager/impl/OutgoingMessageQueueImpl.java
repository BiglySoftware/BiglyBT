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


package com.biglybt.core.networkmanager.impl;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;

import com.biglybt.core.networkmanager.NetworkManager;
import com.biglybt.core.networkmanager.OutgoingMessageQueue;
import com.biglybt.core.networkmanager.RawMessage;
import com.biglybt.core.networkmanager.Transport;
import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.messaging.MessageStreamEncoder;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DirectByteBuffer;
import com.biglybt.core.util.TimeFormatter;



/**
 * Priority-based outbound peer message queue.
 */
public class
OutgoingMessageQueueImpl
	implements OutgoingMessageQueue
{
  private final LinkedList<RawMessage> 		queue		= new LinkedList<>();
  private final AEMonitor	queue_mon	= new AEMonitor( "OutgoingMessageQueue:queue" );

  private final ArrayList delayed_notifications = new ArrayList();
  private final AEMonitor delayed_notifications_mon = new AEMonitor( "OutgoingMessageQueue:DN" );

  private volatile ArrayList listeners 		= new ArrayList();  //copied-on-write
  private final AEMonitor listeners_mon		= new AEMonitor( "OutgoingMessageQueue:L");

  private int total_size = 0;
  private int total_data_size = 0;
  private boolean	priority_boost = false;
  private RawMessage urgent_message = null;
  private boolean destroyed = false;

  private MessageStreamEncoder stream_encoder;
  private Transport transport;

  private int	progress_id;
  private int[] progress;

  private static final boolean TRACE_HISTORY = false;  //TODO
  private static final int MAX_HISTORY_TRACES = 30;
  private final LinkedList<RawMessage> prev_sent = new LinkedList<>();

  private boolean	trace;

  /**
   * Create a new outgoing message queue.
   * @param stream_encoder default message encoder
   */
  public OutgoingMessageQueueImpl( MessageStreamEncoder stream_encoder ) {
    this.stream_encoder = stream_encoder;
  }

  @Override
  public void
  setTransport(
	 Transport		_transport )
  {
	transport 	= _transport;
  }

  @Override
  public int
  getMssSize()
  {
	  return( transport==null?NetworkManager.getMinMssSize():transport.getMssSize());
  }

  /**
   * Set the message stream encoder that will be used to encode outgoing messages.
   * @param stream_encoder to use
   */
  @Override
  public void setEncoder(MessageStreamEncoder stream_encoder ) {
    this.stream_encoder = stream_encoder;
  }

  @Override
  public MessageStreamEncoder
  getEncoder()
  {
	  return( stream_encoder );
  }

  @Override
  public int[] getCurrentMessageProgress() {
    return progress;
  }


  /**
   * Destroy this queue; i.e. perform cleanup actions.
   */
  @Override
  public void destroy() {
    destroyed = true;
    try{
      queue_mon.enter();

      while( !queue.isEmpty() ) {
      	queue.remove( 0 ).destroy();
      }
    }finally{
      queue_mon.exit();
    }
    total_size = 0;
    total_data_size = 0;
    prev_sent.clear();
    listeners = new ArrayList();
    progress = null;
    progress_id++;
    urgent_message = null;
  }


  /**
   * Get the total number of bytes ready to be transported.
   * @return total bytes remaining
   */
  @Override
  public int getTotalSize() {  return total_size;  }

  @Override
  public int
  getDataQueuedBytes()
  {
	 return( total_data_size );
  }

  @Override
  public int
  getProtocolQueuedBytes()
  {
	  return( total_size - total_data_size );
  }

  @Override
  public boolean
  getPriorityBoost()
  {
	  return( priority_boost );
  }

  @Override
  public void
  setPriorityBoost(
	 boolean	boost )
  {
	  priority_boost = boost;
  }
  @Override
  public boolean
  isBlocked()
  {
	 if ( transport == null ){

		 return( false );
	 }

	 return( !transport.isReadyForWrite( null ));
  }
  /**
   * Whether or not an urgent message (one that needs an immediate send, i.e. a no-delay message) is queued.
   * @return true if there's a message tagged for immediate write
   */
  @Override
  public boolean hasUrgentMessage() {  return urgent_message == null ? false : true;  }


  @Override
  public Message
  peekFirstMessage()
  {
	  try{
	      queue_mon.enter();

	      return( queue.peek());

	  }finally{

		  queue_mon.exit();
	  }
  }

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
  @Override
  public void addMessage(Message message, boolean manual_listener_notify ) {
    //do message add notifications
    boolean allowed = true;
    ArrayList list_ref = listeners;

    for( int i=0; i < list_ref.size(); i++ ) {
      MessageQueueListener listener = (MessageQueueListener)list_ref.get( i );
      allowed = allowed && listener.messageAdded( message );
    }

    if( !allowed ) {  //message addition not allowed
      //LGLogger.log( "Message [" +message.getDescription()+ "] not allowed for queueing, message addition skipped." );
      //message.destroy();  //TODO destroy????
      return;
    }


    RawMessage[] rmesgs = stream_encoder.encodeMessage( message );

    if( destroyed ) {  //queue is shutdown, drop any added messages
      for (int i=0;i<rmesgs.length;i++){
    	  rmesgs[i].destroy();
      }
      return;
    }

    for (int i=0;i<rmesgs.length;i++){

    	RawMessage rmesg = rmesgs[i];

	    removeMessagesOfType( rmesg.messagesToRemove(), manual_listener_notify );

	    try{
	      queue_mon.enter();

	      int pos = 0;
	      for( Iterator<RawMessage> it = queue.iterator(); it.hasNext(); ) {
	        RawMessage msg = it.next();
	        if( rmesg.getPriority() > msg.getPriority()
	          && msg.getRawData()[0].position(DirectByteBuffer.SS_NET) == 0 ) {  //but don't insert in front of a half-sent message
	          break;
	        }
	        pos++;
	      }
	      if( rmesg.isNoDelay() ) {
	        urgent_message = rmesg;
	      }
	      queue.add( pos, rmesg );

	      DirectByteBuffer[] payload = rmesg.getRawData();
	      int	remaining = 0;
	      for( int j=0; j < payload.length; j++ ) {
	    	  remaining += payload[j].remaining(DirectByteBuffer.SS_NET);
	      }
	      total_size += remaining;
	      if ( rmesg.getType() == Message.TYPE_DATA_PAYLOAD ){
	    	  total_data_size += remaining;
	      }
	    }finally{
	      queue_mon.exit();
	    }

	    if( manual_listener_notify ) {  //register listener event for later, manual notification
	      NotificationItem item = new NotificationItem( NotificationItem.MESSAGE_ADDED );
	      item.message = rmesg;
	      try {
	        delayed_notifications_mon.enter();

	        delayed_notifications.add( item );
	      }
	      finally {
	        delayed_notifications_mon.exit();
	      }
	    }
	    else { //do listener notification now
	      ArrayList listeners_ref = listeners;

	      for( int j=0; j < listeners_ref.size(); j++ ) {
	        MessageQueueListener listener = (MessageQueueListener)listeners_ref.get( j );
	        listener.messageQueued( rmesg.getBaseMessage() );
	      }
	    }
    }
  }



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
  @Override
  public void removeMessagesOfType(Message[] message_types, boolean manual_listener_notify ) {
    if( message_types == null ) return;

    ArrayList<RawMessage> messages_removed = null;

    try{
      queue_mon.enter();

      for( Iterator<RawMessage> i = queue.iterator(); i.hasNext(); ) {
        RawMessage msg = i.next();

        for( int t=0; t < message_types.length; t++ ) {
          boolean same_type = message_types[t].getID().equals( msg.getID() );

          if( same_type && msg.getRawData()[0].position(DirectByteBuffer.SS_NET) == 0 ) {   //dont remove a half-sent message
            if( msg == urgent_message ) urgent_message = null;

            DirectByteBuffer[] payload = msg.getRawData();
            int remaining = 0;
            for( int x=0; x < payload.length; x++ ) {
            	remaining += payload[x].remaining(DirectByteBuffer.SS_NET);
            }
            total_size -= remaining;
            if ( msg.getType() == Message.TYPE_DATA_PAYLOAD ){
            	total_data_size -= remaining;
            }
            if( manual_listener_notify ) {
              NotificationItem item = new NotificationItem( NotificationItem.MESSAGE_REMOVED );
              item.message = msg;
              try {
                delayed_notifications_mon.enter();

                delayed_notifications.add( item );
              }
              finally {
                delayed_notifications_mon.exit();
              }
            }
            else {
              if ( messages_removed == null ){
              	messages_removed = new ArrayList<>();
              }
              messages_removed.add( msg );
            }
        		i.remove();
            break;
        	}
        }
      }

      if ( queue.isEmpty()){
    	  progress = null;
    	  progress_id++;
      }
    }finally{
      queue_mon.exit();
    }

    if( !manual_listener_notify && messages_removed != null ) {
      //do listener notifications now
      ArrayList listeners_ref = listeners;

      for( int x=0; x < messages_removed.size(); x++ ) {
        RawMessage msg = messages_removed.get( x );

        for( int i=0; i < listeners_ref.size(); i++ ) {
          MessageQueueListener listener = (MessageQueueListener)listeners_ref.get( i );
          listener.messageRemoved( msg.getBaseMessage() );
        }
        msg.destroy();
      }
    }
  }


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
  @Override
  public boolean removeMessage(Message message, boolean manual_listener_notify ) {
    RawMessage msg_removed = null;

    try{
      queue_mon.enter();

      for( Iterator<RawMessage> it = queue.iterator(); it.hasNext(); ) {
        RawMessage raw = it.next();

        if( message.equals( raw.getBaseMessage() ) ) {
          if( raw.getRawData()[0].position(DirectByteBuffer.SS_NET) == 0 ) {  //dont remove a half-sent message
            if( raw == urgent_message ) urgent_message = null;

            DirectByteBuffer[] payload = raw.getRawData();
            int remaining = 0;
            for( int x=0; x < payload.length; x++ ) {
            	remaining += payload[x].remaining(DirectByteBuffer.SS_NET);
            }
            total_size -= remaining;
            if ( raw.getType() == Message.TYPE_DATA_PAYLOAD ){
            	total_data_size -= remaining;
            }
            queue.remove( raw );
            msg_removed = raw;
          }

          break;
        }
      }

      if ( queue.isEmpty()){
    	  progress = null;
    	  progress_id++;
      }
    }finally{
      queue_mon.exit();
    }


    if( msg_removed != null ) {
      if( manual_listener_notify ) { //delayed manual notification
        NotificationItem item = new NotificationItem( NotificationItem.MESSAGE_REMOVED );
        item.message = msg_removed;
        try {
          delayed_notifications_mon.enter();

          delayed_notifications.add( item );
        }
        finally {
          delayed_notifications_mon.exit();
        }
      }
      else {   //do listener notification now
        ArrayList listeners_ref = listeners;

        for( int i=0; i < listeners_ref.size(); i++ ) {
          MessageQueueListener listener = (MessageQueueListener)listeners_ref.get( i );
          listener.messageRemoved( msg_removed.getBaseMessage() );
        }
        msg_removed.destroy();
      }
      return true;
    }

    return false;
  }


  private WeakReference rawBufferCache = new WeakReference(null);
  private WeakReference origPositionsCache = new WeakReference(null);

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
   * @return number of bytes delivered
   * @throws IOException on delivery error
   */
   @Override
   public int[] deliverToTransport(int max_bytes, boolean protocol_is_free, boolean manual_listener_notify ) throws IOException {
	  if( max_bytes < 1 ) {
		  if ( !protocol_is_free ){
			  Debug.out( "max_bytes < 1: " +max_bytes );

			  return( new int[2] );
		  }

		  max_bytes = 0;	// in case it was negative
	  }

	  if ( transport == null ){
		  throw( new IOException( "not ready to deliver data" ));
	  }
	  int data_written = 0;
	  int protocol_written = 0;

	  ArrayList<RawMessage> messages_sent = null;

	  //System.out.println( "deliver: %=" + percent_complete + ", queue=" + queue.size());
	  try{
		  queue_mon.enter();

		  if( !queue.isEmpty() ){

			  int buffer_limit 		= 64;

			  ByteBuffer[] raw_buffers 	= (ByteBuffer[])rawBufferCache.get();
			  if(raw_buffers == null)
			  {
				  raw_buffers = new ByteBuffer[buffer_limit];
				  rawBufferCache = new WeakReference(raw_buffers);
			  } else
			  {
				  Arrays.fill(raw_buffers, null);
			  }


			  int[] orig_positions	= (int[])origPositionsCache.get();
			  if(orig_positions == null)
			  {
				  orig_positions = new int[buffer_limit];
				  origPositionsCache = new WeakReference(orig_positions);
			  } else
			  {
				  Arrays.fill(orig_positions, 0);
			  }



			  int buffer_count	= 0;

			  int total_sofar_excluding_free 	= 0;
			  int total_to_write				= 0;

outer:
			  for( Iterator<RawMessage> i = queue.iterator(); i.hasNext(); ){

				  RawMessage	message = i.next();

				  boolean msg_is_free = message.getType() == Message.TYPE_PROTOCOL_PAYLOAD && protocol_is_free;

				  DirectByteBuffer[] payloads = message.getRawData();

				  for( int x=0; x < payloads.length; x++ ){

					  ByteBuffer buff = payloads[x].getBuffer( DirectByteBuffer.SS_NET );

					  raw_buffers[buffer_count] = buff;

					  orig_positions[buffer_count] = buff.position();

					  buffer_count++;

					  int rem = buff.remaining();

					  total_to_write += rem;

					  if ( !msg_is_free ){

						  total_sofar_excluding_free += rem;

						  if ( total_sofar_excluding_free >= max_bytes ){

							  break outer;
						  }
					  }

					  if ( buffer_count == buffer_limit ) {

						  int	new_buffer_limit	= buffer_limit * 2;

						  ByteBuffer[] 	new_raw_buffers 	= new ByteBuffer[new_buffer_limit];
						  int[]		 	new_orig_positions	= new int[new_buffer_limit];

						  System.arraycopy( raw_buffers, 0, new_raw_buffers, 0, buffer_limit );
						  System.arraycopy( orig_positions, 0, new_orig_positions, 0, buffer_limit );

						  raw_buffers 		= new_raw_buffers;
						  orig_positions	= new_orig_positions;

						  buffer_limit 		= new_buffer_limit;
					  }
				  }
			  }

			  ByteBuffer last_buff = (ByteBuffer)raw_buffers[buffer_count - 1 ];

			  int orig_last_limit = last_buff.limit();

			  if ( total_sofar_excluding_free > max_bytes ){

				  int reduce_by = total_sofar_excluding_free - max_bytes;

				  last_buff.limit( orig_last_limit - reduce_by );

				  total_to_write -= reduce_by;
			  }

			  if ( total_to_write <= 0 ){

				  last_buff.limit( orig_last_limit );

				  return( new int[2] );
			  }

			  transport.write( raw_buffers, 0, buffer_count );

			  last_buff.limit( orig_last_limit );

			  int pos = 0;
			  boolean stop = false;

			  while( !queue.isEmpty() && !stop ) {
				  RawMessage msg = queue.get( 0 );
				  DirectByteBuffer[] payloads = msg.getRawData();

				  for( int x=0; x < payloads.length; x++ ) {
					  ByteBuffer bb = payloads[x].getBuffer( DirectByteBuffer.SS_NET );

					  int bytes_written = (bb.limit() - bb.remaining()) - orig_positions[ pos ];
					  total_size -= bytes_written;

					  if ( msg.getType() == Message.TYPE_DATA_PAYLOAD ){
						  total_data_size -= bytes_written;
					  }

					  if( x > 0 && msg.getType() == Message.TYPE_DATA_PAYLOAD ) {  //assumes the first buffer is message header
						  data_written += bytes_written;
					  }else {
						  protocol_written += bytes_written;
					  }

					  if( bb.hasRemaining() ) {  //still data left to send in this message
						  stop = true;  //so don't bother checking later messages for completion

						  //compute send percentage
						  int message_size = 0;
						  int written = 0;

						  for( int i=0; i < payloads.length; i++ ) {
							  ByteBuffer buff = payloads[i].getBuffer( DirectByteBuffer.SS_NET );

							  message_size += buff.limit();

							  if( i < x ) {  //if in front of non-empty buffer
								  written += buff.limit();
							  }
							  else if( i == x ) {  //is non-empty buffer
								  written += buff.position();
							  }
						  }

						  progress = new int[]{ message_size, written, progress_id };

						  break;
					  }
					  else if( x == payloads.length - 1 ) {  //last payload buffer of message is empty
						  if( msg == urgent_message ) urgent_message = null;

						  queue.remove( 0 );


						  if( TRACE_HISTORY ) {
							  prev_sent.addLast( msg );
							  if( prev_sent.size() > MAX_HISTORY_TRACES )  prev_sent.removeFirst();
						  }


						  progress = null;
						  progress_id++;

						  if( manual_listener_notify ) {
							  NotificationItem item = new NotificationItem( NotificationItem.MESSAGE_SENT );
							  item.message = msg;
							  try {  delayed_notifications_mon.enter();
							  delayed_notifications.add( item );
							  } finally {  delayed_notifications_mon.exit();  }
						  }
						  else {
							  if( messages_sent == null ) {
								  messages_sent = new ArrayList<>();
							  }
							  messages_sent.add( msg );
						  }
					  }

					  pos++;
					  if( pos >= buffer_count ) {
						  stop = true;
						  break;
					  }
				  }
			  }
		  }
	  }finally{
		  queue_mon.exit();
	  }

	  // we can have messages that end up getting serialised as 0 bytes (for http
	  // connections for example) - we still need to notify them of being sent...

	  if( data_written + protocol_written > 0 || messages_sent != null ) {

		  if ( trace ){
			  TimeFormatter.milliTrace( "omq:deliver: " + (data_written + protocol_written) + ", q=" + queue.size() + "/" + total_size );
		  }

		  if( manual_listener_notify ) {

			  if( data_written > 0 ) {  //data bytes notify
				  NotificationItem item = new NotificationItem( NotificationItem.DATA_BYTES_SENT );
				  item.byte_count = data_written;
				  try {
					  delayed_notifications_mon.enter();

					  delayed_notifications.add( item );
				  }
				  finally {
					  delayed_notifications_mon.exit();
				  }
			  }

			  if( protocol_written > 0 ) {  //protocol bytes notify
				  NotificationItem item = new NotificationItem( NotificationItem.PROTOCOL_BYTES_SENT );
				  item.byte_count = protocol_written;
				  try {
					  delayed_notifications_mon.enter();

					  delayed_notifications.add( item );
				  }
				  finally {
					  delayed_notifications_mon.exit();
				  }
			  }
		  }
		  else {  //do listener notification now
			  ArrayList listeners_ref = listeners;

			  int num_listeners = listeners_ref.size();
			  for( int i=0; i < num_listeners; i++ ) {
				  MessageQueueListener listener = (MessageQueueListener)listeners_ref.get( i );

				  if( data_written > 0 )  listener.dataBytesSent( data_written );
				  if( protocol_written > 0 )  listener.protocolBytesSent( protocol_written );

				  if ( messages_sent != null ){

					  for( int x=0; x < messages_sent.size(); x++ ) {
						  RawMessage msg = messages_sent.get( x );

						  listener.messageSent( msg.getBaseMessage() );

						  if( i == num_listeners - 1 ) {  //the last listener notification, so destroy
							  msg.destroy();
						  }
					  }
				  }
			  }
		  }
	  }else{
		  if ( trace ){
			  TimeFormatter.milliTrace( "omq:deliver: 0, q=" + queue.size() + "/" + total_size );
		  }
	  }

	  return( new int[]{ data_written, protocol_written });
  }

  @Override
  public void
  flush()
  {
	  try{
		  queue_mon.enter();

		  if ( queue.isEmpty()){

			  return;
		  }

		  for (int i=0;i<queue.size();i++){

			  RawMessage	msg = queue.get(i);

			  msg.setNoDelay();

			  if ( i == 0 ){

				  urgent_message = msg;
			  }
		  }
	  }finally{

		  queue_mon.exit();
	  }

	  ArrayList list_ref = listeners;

	  for( int i=0; i < list_ref.size(); i++ ) {
		 MessageQueueListener listener = (MessageQueueListener)list_ref.get( i );
		 listener.flush();
	  }
  }
  @Override
  public boolean
  isDestroyed()
  {
	  return( destroyed );
  }

  /**
   * Manually send any unsent listener notifications.
   */
  @Override
  public void doListenerNotifications() {
    ArrayList notifications_copy;
    try {
      delayed_notifications_mon.enter();

      if( delayed_notifications.size() == 0 )  return;
      notifications_copy = new ArrayList( delayed_notifications );
      delayed_notifications.clear();
    }
    finally {
      delayed_notifications_mon.exit();
    }

    ArrayList listeners_ref = listeners;

    for( int j=0; j < notifications_copy.size(); j++ ) {  //for each notification
      NotificationItem item = (NotificationItem)notifications_copy.get( j );

      switch( item.type ) {
        case NotificationItem.MESSAGE_ADDED:
          for( int i=0; i < listeners_ref.size(); i++ ) {  //for each listener
            MessageQueueListener listener = (MessageQueueListener)listeners_ref.get( i );
            listener.messageQueued( item.message.getBaseMessage() );
          }
          break;

        case NotificationItem.MESSAGE_REMOVED:
          for( int i=0; i < listeners_ref.size(); i++ ) {  //for each listener
            MessageQueueListener listener = (MessageQueueListener)listeners_ref.get( i );
            listener.messageRemoved( item.message.getBaseMessage() );
          }
          item.message.destroy();
          break;

        case NotificationItem.MESSAGE_SENT:
          for( int i=0; i < listeners_ref.size(); i++ ) {  //for each listener
            MessageQueueListener listener = (MessageQueueListener)listeners_ref.get( i );
            listener.messageSent( item.message.getBaseMessage() );
          }
          item.message.destroy();
          break;

        case NotificationItem.PROTOCOL_BYTES_SENT:
          for( int i=0; i < listeners_ref.size(); i++ ) {  //for each listener
            MessageQueueListener listener = (MessageQueueListener)listeners_ref.get( i );
            listener.protocolBytesSent( item.byte_count );
          }
          break;

        case NotificationItem.DATA_BYTES_SENT:
          for( int i=0; i < listeners_ref.size(); i++ ) {  //for each listener
            MessageQueueListener listener = (MessageQueueListener)listeners_ref.get( i );
            listener.dataBytesSent( item.byte_count );
          }
          break;

        default:
          Debug.out( "NotificationItem.type unknown :" + item.type );
      }
    }
  }


  @Override
  public void
  setTrace(
		boolean	on )
  {
	  trace	= on;

	  transport.setTrace( on );
  }

  @Override
  public String getQueueTrace() {
  	StringBuilder trace = new StringBuilder();

  	trace.append( "**** OUTGOING QUEUE TRACE ****\n" );

  	try{
      queue_mon.enter();


      int i=0;

    	for( Iterator<RawMessage> it = prev_sent.iterator(); it.hasNext(); ) {
    		RawMessage raw = it.next();
        trace.append("[#h").append(i).append("]: ")
             .append(raw.getID())
             .append(" [")
             .append(raw.getDescription())
             .append("]")
             .append("\n" );
        i++;
    	}



      int position = queue.size() - 1;

      for( Iterator<RawMessage> it = queue.iterator(); it.hasNext(); ) {
        RawMessage raw = it.next();

        int pos = raw.getRawData()[0].position(DirectByteBuffer.SS_NET);
        int length = raw.getRawData()[0].limit( DirectByteBuffer.SS_NET );

        trace.append( "[#")
             .append(position)
             .append(" ")
             .append(pos)
             .append(":")
             .append(length)
             .append("]: ")
             .append(raw.getID())
             .append(" [")
             .append(raw.getDescription())
             .append("]")
             .append("\n" );

        position--;
      }
    }
  	finally{
      queue_mon.exit();
    }

  	return trace.toString();
  }


  /**
   * Add a listener to be notified of queue events.
   * @param listener
   */
  @Override
  public void registerQueueListener(MessageQueueListener listener ) {
    try{  listeners_mon.enter();
      //copy-on-write
      ArrayList new_list = new ArrayList( listeners.size() + 1 );
      new_list.addAll( listeners );
      new_list.add( listener );
      listeners = new_list;
    }
    finally{  listeners_mon.exit();  }
  }


  /**
   * Cancel queue event notification listener.
   * @param listener
   */
  @Override
  public void cancelQueueListener(MessageQueueListener listener ) {
    try{  listeners_mon.enter();
      //copy-on-write
      ArrayList new_list = new ArrayList( listeners );
      new_list.remove( listener );
      listeners = new_list;
    }
    finally{  listeners_mon.exit();  }
  }



  /**
   * Notifty the queue (and its listeners) of a message sent externally on the queue's behalf.
   * @param message sent externally
   */
  @Override
  public void notifyOfExternallySentMessage(Message message ) {
    ArrayList listeners_ref = listeners;

    DirectByteBuffer[] buffs = message.getData();
    int size = 0;
    for( int i=0; i < buffs.length; i++ ) {
      size += buffs[i].remaining( DirectByteBuffer.SS_NET );
    }

    for( int i=0; i < listeners_ref.size(); i++ ) {
      MessageQueueListener listener = (MessageQueueListener)listeners_ref.get( i );

      listener.messageSent( message );

      if( message.getType() == Message.TYPE_DATA_PAYLOAD ) {
        listener.dataBytesSent( size );
      }
      else {
        listener.protocolBytesSent( size );
      }
    }

    //System.out.println( "notifiedOfExternallySentMessage:: [" +message.getID()+ "] size=" +size );

  }





  private static class NotificationItem {
    private static final int MESSAGE_ADDED        = 0;
    private static final int MESSAGE_REMOVED      = 1;
    private static final int MESSAGE_SENT         = 2;
    private static final int DATA_BYTES_SENT      = 3;
    private static final int PROTOCOL_BYTES_SENT  = 4;
    final int type;
    RawMessage message;
    int byte_count = 0;
    NotificationItem( int notification_type ) {
      type = notification_type;
    }
  }

}
