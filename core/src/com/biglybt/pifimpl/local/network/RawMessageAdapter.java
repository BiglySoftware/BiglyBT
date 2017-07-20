/*
 * Created on Feb 11, 2005
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

package com.biglybt.pifimpl.local.network;

import java.nio.ByteBuffer;

import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.util.DirectByteBuffer;
import com.biglybt.pif.network.RawMessage;
import com.biglybt.pifimpl.local.messaging.MessageAdapter;


/**
 *
 */
public class RawMessageAdapter extends MessageAdapter implements RawMessage, com.biglybt.core.networkmanager.RawMessage {
  private RawMessage plug_msg = null;
  private com.biglybt.core.networkmanager.RawMessage core_msg = null;


  public RawMessageAdapter( RawMessage plug_msg ) {
    super( plug_msg );
    this.plug_msg = plug_msg;
  }


  public RawMessageAdapter( com.biglybt.core.networkmanager.RawMessage core_msg ) {
    super( core_msg );
    this.core_msg = core_msg;
  }


  //plugin raw message implementation
  @Override
  public ByteBuffer[] getRawPayload() {
    if( core_msg == null ) {
      return plug_msg.getRawPayload();
    }

    DirectByteBuffer[] dbbs = core_msg.getRawData();
    ByteBuffer[] bbs = new ByteBuffer[ dbbs.length ];  //TODO cache it???
    for( int i=0; i < dbbs.length; i++ ) {
      bbs[i] = dbbs[i].getBuffer( DirectByteBuffer.SS_MSG );
    }
    return bbs;
  }


  //core raw message implementation
  @Override
  public DirectByteBuffer[] getRawData() {
    if( plug_msg == null ) {
      return core_msg.getRawData();
    }

    ByteBuffer[] bbs = plug_msg.getRawPayload();
    DirectByteBuffer[] dbbs = new DirectByteBuffer[ bbs.length ];  //TODO cache it???
    for( int i=0; i < bbs.length; i++ ) {
      dbbs[i] = new DirectByteBuffer( bbs[i] );
    }
    return dbbs;
  }


  @Override
  public int getPriority() {  return com.biglybt.core.networkmanager.RawMessage.PRIORITY_NORMAL;  }


  @Override
  public boolean isNoDelay() {  return true;  }

  @Override
  public void setNoDelay() {}

  @Override
  public Message[] messagesToRemove() {  return null;  }


  @Override
  public com.biglybt.pif.messaging.Message getOriginalMessage() {
    if( plug_msg == null ) {
      return new MessageAdapter( core_msg.getBaseMessage() );
    }

    return plug_msg.getOriginalMessage();
  }


  @Override
  public Message getBaseMessage() {
    if( core_msg == null ) {
      return new MessageAdapter( plug_msg.getOriginalMessage() );
    }

    return core_msg.getBaseMessage();
  }

}
