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
 * BitTorrent bitfield message.
 */
public class BTBitfield implements BTMessage {
  private final DirectByteBuffer[] 	buffer;
  private final byte				version;

  public BTBitfield( DirectByteBuffer bitfield, byte _version ) {
    buffer 	= new DirectByteBuffer[] { bitfield };
    version	= _version;
  }


  public DirectByteBuffer getBitfield() {  return buffer[0];  }



  @Override
  public String getID() {  return BTMessage.ID_BT_BITFIELD;  }
  @Override
  public byte[] getIDBytes() {  return BTMessage.ID_BT_BITFIELD_BYTES;  }

  @Override
  public String getFeatureID() {  return BTMessage.BT_FEATURE_ID;  }

  @Override
  public int getFeatureSubID() {  return BTMessage.SUBID_BT_BITFIELD;  }


  @Override
  public int getType() {  return Message.TYPE_PROTOCOL_PAYLOAD;  }

  @Override
  public byte getVersion() { return version; }

	@Override
	public String getDescription() {  return BTMessage.ID_BT_BITFIELD;  }

  @Override
  public DirectByteBuffer[] getData() {  return buffer;  }

  @Override
  public Message deserialize(DirectByteBuffer data, byte version ) throws MessageException {
    if( data == null ) {
      throw new MessageException( "[" +getID() +"] decode error: data == null" );
    }

    return new BTBitfield( data, version );
  }

  @Override
  public void destroy() {
    if( buffer[0] != null )  buffer[0].returnToPool();
  }

}
