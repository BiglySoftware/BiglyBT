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


import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.messaging.MessageException;
import com.biglybt.core.util.DirectByteBuffer;


/**
 * BitTorrent interested message.
 */
public class BTInterested implements BTMessage {

  private final byte version;

  public BTInterested(byte _version) {
    version = _version;
  }

  @Override
  public String getID() {  return BTMessage.ID_BT_INTERESTED;  }
  @Override
  public byte[] getIDBytes() {  return BTMessage.ID_BT_INTERESTED_BYTES;  }

  @Override
  public String getFeatureID() {  return BTMessage.BT_FEATURE_ID;  }

  @Override
  public int getFeatureSubID() {  return BTMessage.SUBID_BT_INTERESTED;  }

  @Override
  public int getType() {  return Message.TYPE_PROTOCOL_PAYLOAD;  }

  @Override
  public byte getVersion() { return version; }

	@Override
	public String getDescription() {  return BTMessage.ID_BT_INTERESTED;  }

  @Override
  public DirectByteBuffer[] getData() {  return new DirectByteBuffer[] {};  }

  @Override
  public Message deserialize(DirectByteBuffer data, byte version ) throws MessageException {
    if( data != null && data.hasRemaining( DirectByteBuffer.SS_MSG ) ) {
      throw new MessageException( "[" +getID() +"] decode error: payload not empty" );
    }

    if( data != null )  data.returnToPool();

    return new BTInterested(version);
  }

  @Override
  public void destroy() {  /*nothing*/  }
}
