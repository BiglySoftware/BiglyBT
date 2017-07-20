/*
 * Created on Feb 10, 2005
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

package com.biglybt.pifimpl.local.messaging;

import java.nio.ByteBuffer;

import com.biglybt.core.util.DirectByteBuffer;
import com.biglybt.pif.messaging.Message;
import com.biglybt.pif.messaging.MessageException;

/**
 *
 */
public class MessageAdapter implements Message, com.biglybt.core.peermanager.messaging.Message {
  private Message plug_msg = null;
  private com.biglybt.core.peermanager.messaging.Message core_msg = null;


  public MessageAdapter( Message plug_msg ) {
    this.plug_msg = plug_msg;
  }


  public MessageAdapter( com.biglybt.core.peermanager.messaging.Message core_msg ) {
    this.core_msg = core_msg;
  }


  public Message getPluginMessage() {  return plug_msg;  }

  public com.biglybt.core.peermanager.messaging.Message getCoreMessage() {  return core_msg;  }



  //plugin Message implementation
  @Override
  public ByteBuffer[] getPayload() {
    if( core_msg == null ) {
      return plug_msg.getPayload();
    }

    DirectByteBuffer[] dbbs = core_msg.getData();
    ByteBuffer[] bbs = new ByteBuffer[ dbbs.length ];  //TODO cache it???
    for( int i=0; i < dbbs.length; i++ ) {
      bbs[i] = dbbs[i].getBuffer( DirectByteBuffer.SS_MSG );
    }
    return bbs;
  }

  @Override
  public Message create(ByteBuffer data ) throws MessageException  {
    if( core_msg == null ) {
      return plug_msg.create( data );
    }

    try{
      return new MessageAdapter( core_msg.deserialize( new DirectByteBuffer( data ), (byte)1 ) );
    }
    catch( com.biglybt.core.peermanager.messaging.MessageException e ) {
      throw new MessageException( e.getMessage() );
    }
  }



  //shared Message implementation
  @Override
  public String getID() {
    return core_msg == null ? plug_msg.getID() : core_msg.getID();
  }

  @Override
  public byte[] getIDBytes() {
	    return core_msg == null ? plug_msg.getID().getBytes() : core_msg.getIDBytes();
	  }

  @Override
  public int getType() {
    return core_msg == null ? plug_msg.getType() : core_msg.getType();
  }

  @Override
  public byte getVersion() {
	    return core_msg == null ? (byte)1 : core_msg.getVersion();
  }


  @Override
  public String getDescription() {
    return core_msg == null ? plug_msg.getDescription() : core_msg.getDescription();
  }

  @Override
  public void destroy() {
    if( core_msg == null ) plug_msg.destroy();
    else core_msg.destroy();
  }



  //core Message implementation

  @Override
  public String getFeatureID() {  return "AZPLUGMSG";  }

  @Override
  public int getFeatureSubID() {  return -1;  }


  @Override
  public DirectByteBuffer[] getData() {
    if( plug_msg == null ) {
      return core_msg.getData();
    }

    ByteBuffer[] bbs = plug_msg.getPayload();
    DirectByteBuffer[] dbbs = new DirectByteBuffer[ bbs.length ];  //TODO cache it???
    for( int i=0; i < bbs.length; i++ ) {
      dbbs[i] = new DirectByteBuffer( bbs[i] );
    }
    return dbbs;
  }

  @Override
  public com.biglybt.core.peermanager.messaging.Message deserialize(DirectByteBuffer data, byte version ) throws com.biglybt.core.peermanager.messaging.MessageException {
    if( plug_msg == null ) {
      return core_msg.deserialize( data, version );
    }

    try{
    	Message message = plug_msg.create( data.getBuffer( DirectByteBuffer.SS_MSG ) );

    	if ( message == null ){

    		throw( new com.biglybt.core.peermanager.messaging.MessageException( "Plugin message deserialisation failed" ));
    	}

    	return new MessageAdapter( message );
    }
    catch( MessageException e ) {
      throw new com.biglybt.core.peermanager.messaging.MessageException( e.getMessage() );
    }
    finally {
      data.returnToPool();
    }
  }

}
