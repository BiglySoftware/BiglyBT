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

package com.biglybt.core.peermanager.messaging.azureus;

import java.util.Map;

import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.messaging.MessageException;
import com.biglybt.core.peermanager.messaging.MessagingUtil;
import com.biglybt.core.util.DirectByteBuffer;



/**
 * This is a helper class for creating messages with a Map'd beencode-able payload.
 */
public class AZGenericMapPayload implements AZMessage {
  private final byte version;
  private DirectByteBuffer buffer = null;

  private final String type_id;
  private final Map msg_map;


  /**
   * Create a new AZ message with the given message type id, with the given bencode-able map payload.
   * @param message_type of message
   * @param message payload (to be bencoded)
   */
  public AZGenericMapPayload( String message_type, Map message, byte version ) {
    this.type_id = message_type;
    this.msg_map = message;
    this.version = version;
  }


  @Override
  public String getID() {  return type_id;  }
  @Override
  public byte[] getIDBytes() {  return type_id.getBytes();  }

  @Override
  public String getFeatureID() {  return AZMessage.AZ_FEATURE_ID;  }

  @Override
  public int getFeatureSubID() { return AZMessage.SUBID_AZ_GENERIC_MAP;  }


  @Override
  public int getType() {  return Message.TYPE_PROTOCOL_PAYLOAD;  }

  @Override
  public byte getVersion() { return version; }

	public Map getMapPayload() {  return msg_map;  }


  @Override
  public String getDescription() {   return getID();  }


  @Override
  public DirectByteBuffer[] getData() {
    if( buffer == null ) {
      buffer = MessagingUtil.convertPayloadToBencodedByteStream( msg_map, DirectByteBuffer.AL_MSG );
    }
    return new DirectByteBuffer[]{ buffer };
  }


  @Override
  public Message deserialize(DirectByteBuffer data, byte version ) throws MessageException {
    Map payload = MessagingUtil.convertBencodedByteStreamToPayload( data, 1, getID() );
    return new AZGenericMapPayload( getID(), payload, version );
  }


  @Override
  public void destroy() {
    if( buffer != null )  buffer.returnToPool();
  }

}
