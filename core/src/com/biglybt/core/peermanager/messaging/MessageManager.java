/*
 * Created on Jan 8, 2005
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

package com.biglybt.core.peermanager.messaging;

import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.peermanager.messaging.azureus.AZMessageFactory;
import com.biglybt.core.peermanager.messaging.bittorrent.BTMessageFactory;
import com.biglybt.core.peermanager.messaging.bittorrent.ltep.LTMessageFactory;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.ByteArrayHashMap;
import com.biglybt.core.util.DirectByteBuffer;




/**
 *
 */
public class MessageManager {
  private static final MessageManager instance = new MessageManager();

  private final ByteArrayHashMap 	message_map 	= new ByteArrayHashMap();
  private final List				messages		= new ArrayList();

  protected final AEMonitor	this_mon = new AEMonitor( "MessageManager" );

  private MessageManager() {
    /*nothing*/
  }


  public static MessageManager getSingleton() {  return instance;  }


  /**
   * Perform manager initialization.
   */
  public void initialize() {
    AZMessageFactory.init();  //register AZ message types
    BTMessageFactory.init();  //register BT message types
    LTMessageFactory.init();  //register LT message types
  }




  /**
   * Register the given message type with the manager for processing.
   * @param message instance to use for decoding
   * @throws MessageException if this message type has already been registered
   */
  public void registerMessageType( Message message ) throws MessageException {
  	try{
  		this_mon.enter();

  		byte[]	id_bytes = message.getIDBytes();

	    if( message_map.containsKey( id_bytes ) ) {
	      throw new MessageException( "message type [" +message.getID()+ "] already registered!" );
	    }

	    message_map.put( id_bytes, message );

	    messages.add( message );
  	}finally{

  		this_mon.exit();
  	}
  }



  /**
   * Remove registration of given message type from manager.
   * @param message type to remove
   */
  public void deregisterMessageType( Message message ) {
    try{  this_mon.enter();

      message_map.remove( message.getIDBytes() );

      messages.remove( message );
    }
    finally{  this_mon.exit();  }
  }


  /**
   * Construct a new message instance from the given message information.
   * @param id of message
   * @param message_data payload
   * @return decoded/deserialized message
   * @throws MessageException if message creation failed
   */
  public Message createMessage( byte[] id_bytes, DirectByteBuffer message_data, byte version ) throws MessageException {
    Message message = (Message)message_map.get( id_bytes );

    if( message == null ) {
      throw new MessageException( "message id[" + new String( id_bytes) + "] not registered" );
    }

    return message.deserialize( message_data, version );
  }



  /**
   * Lookup a registered message type via id and version.
   * @param id to look for
   * @return the default registered message instance if found, otherwise returns null if this message type is not registered
   */
  public Message lookupMessage( String id ) {
    return (Message)message_map.get( id.getBytes());
  }


  public Message lookupMessage( byte[] id_bytes ) {
    return (Message)message_map.get( id_bytes );
  }


  /**
   * Get a list of the registered messages.
   * @return messages
   */
  public Message[] getRegisteredMessages() {
    return (Message[])messages.toArray( new Message[messages.size()] );
  }


}
