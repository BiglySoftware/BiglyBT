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
import com.biglybt.core.util.DirectByteBufferPool;


/**
 * BitTorrent piece message.
 */
public class BTPiece implements BTMessage {
  private final byte version;
  private final DirectByteBuffer[] buffer = new DirectByteBuffer[ 2 ];
  private String description;

  private final int piece_number;
  private final int piece_offset;
  private final int piece_length;


  public BTPiece( int piece_number, int piece_offset, DirectByteBuffer data, byte version ) {
    this.piece_number = piece_number;
    this.piece_offset = piece_offset;
    this.piece_length = data == null ? 0 : data.remaining( DirectByteBuffer.SS_MSG );
    buffer[1] = data;
    this.version = version;
  }



  public int getPieceNumber() {  return piece_number;  }

  public int getPieceOffset() {  return piece_offset;  }

  public DirectByteBuffer getPieceData() {  return buffer[1];  }



  @Override
  public String getID() {  return BTMessage.ID_BT_PIECE;  }
  @Override
  public byte[] getIDBytes() {  return BTMessage.ID_BT_PIECE_BYTES;  }

  @Override
  public String getFeatureID() {  return BTMessage.BT_FEATURE_ID;  }

  @Override
  public int getFeatureSubID() {  return BTMessage.SUBID_BT_PIECE;  }

  @Override
  public int getType() {  return Message.TYPE_DATA_PAYLOAD;  }

  @Override
  public byte getVersion() { return version; }

	@Override
	public String getDescription() {
    if( description == null ) {
      description = BTMessage.ID_BT_PIECE + " data for piece #" + piece_number + ":" + piece_offset + "->" + (piece_offset + piece_length -1);
    }

    return description;
  }


  @Override
  public DirectByteBuffer[] getData() {
    if( buffer[0] == null ) {
      buffer[0] = DirectByteBufferPool.getBuffer( DirectByteBuffer.AL_MSG_BT_PIECE, 8 );
      buffer[0].putInt( DirectByteBuffer.SS_MSG, piece_number );
      buffer[0].putInt( DirectByteBuffer.SS_MSG, piece_offset );
      buffer[0].flip( DirectByteBuffer.SS_MSG );
    }

    return buffer;
  }



  @Override
  public Message deserialize(DirectByteBuffer data, byte version ) throws MessageException {
    if( data == null ) {
      throw new MessageException( "[" +getID() + "] decode error: data == null" );
    }

    if( data.remaining( DirectByteBuffer.SS_MSG ) < 8 ) {
      throw new MessageException( "[" +getID()+ "] decode error: payload.remaining[" +data.remaining( DirectByteBuffer.SS_MSG )+ "] < 8" );
    }

    int number = data.getInt( DirectByteBuffer.SS_MSG );
    if( number < 0 ) {
      throw new MessageException( "[" +getID() +"] decode error: number < 0" );
    }

    int offset = data.getInt( DirectByteBuffer.SS_MSG );
    if( offset < 0 ) {
      throw new MessageException( "[" +getID() + "] decode error: offset < 0" );
    }

    return new BTPiece( number, offset, data, version );
  }


  @Override
  public void destroy() {
    if( buffer[0] != null ) buffer[0].returnToPool();
    if( buffer[1] != null ) buffer[1].returnToPool();
  }
}
