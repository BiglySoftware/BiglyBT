/*
 * Created on Apr 30, 2004
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


import com.biglybt.core.networkmanager.RawMessage;
import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.messaging.MessageException;
import com.biglybt.core.util.DirectByteBuffer;
import com.biglybt.core.util.DirectByteBufferPool;


/**
 * BitTorrent keep-alive message.
 */
public class BTKeepAlive implements BTMessage, RawMessage {
  private final byte version;
  private DirectByteBuffer[] buffer = null;

  private boolean no_delay = false;

  public BTKeepAlive(byte _version) {
    version = _version;
  }


  // message
  @Override
  public String getID() {  return BTMessage.ID_BT_KEEP_ALIVE;  }
  @Override
  public byte[] getIDBytes() {  return BTMessage.ID_BT_KEEP_ALIVE_BYTES;  }

  @Override
  public String getFeatureID() {  return BTMessage.BT_FEATURE_ID;  }

  @Override
  public int getFeatureSubID() {  return BTMessage.SUBID_BT_KEEP_ALIVE;  }

  @Override
  public int getType() {  return Message.TYPE_PROTOCOL_PAYLOAD;  }

  @Override
  public byte getVersion() { return version; }

	@Override
	public String getDescription() {  return BTMessage.ID_BT_KEEP_ALIVE;  }

  @Override
  public DirectByteBuffer[] getData() {  return new DirectByteBuffer[]{};  }

  @Override
  public Message deserialize(DirectByteBuffer data, byte version ) throws MessageException {
    if( data != null && data.hasRemaining( DirectByteBuffer.SS_MSG ) ) {
      throw new MessageException( "[" +getID() +"] decode error: payload not empty" );
    }

    if( data != null )  data.returnToPool();

    return new BTKeepAlive(version);
  }


  // raw message
  @Override
  public DirectByteBuffer[] getRawData() {
    if( buffer == null ) {
      DirectByteBuffer dbb = DirectByteBufferPool.getBuffer( DirectByteBuffer.AL_MSG_BT_KEEPALIVE, 4 );
      dbb.putInt( DirectByteBuffer.SS_BT, 0 );
      dbb.flip( DirectByteBuffer.SS_BT );
      buffer = new DirectByteBuffer[]{ dbb };
    }

    return buffer;
  }

  @Override
  public int getPriority() {  return RawMessage.PRIORITY_LOW;  }

  @Override
  public boolean isNoDelay() {  return no_delay;  }

  @Override
  public void setNoDelay() { no_delay = true; }

  @Override
  public Message[] messagesToRemove() {  return null;  }

  @Override
  public void destroy() {
    if( buffer != null ) {
      buffer[0].returnToPool();
    }
  }

  @Override
  public Message getBaseMessage() {  return this;  }

}
