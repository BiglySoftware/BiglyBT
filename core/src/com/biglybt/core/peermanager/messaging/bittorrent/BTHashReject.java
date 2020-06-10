/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.core.peermanager.messaging.bittorrent;

import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.messaging.MessageException;
import com.biglybt.core.util.DirectByteBuffer;
import com.biglybt.core.util.DirectByteBufferPool;

public class 
BTHashReject
	implements BTMessage
{
	private final byte version;
	
	private DirectByteBuffer buffer = null;
	
	private String description = null;

	public 
	BTHashReject( 
		byte 	_version ) 
	{
		version = _version;
	}
	  
	@Override
	public String getID() {  return BTMessage.ID_BT_HASH_REJECT;  }
	
	@Override
	public byte[] getIDBytes() {  return BTMessage.ID_BT_HASH_REJECT_BYTES;  }

	@Override
	public String getFeatureID() {  return BTMessage.BT_FEATURE_ID;  }

	@Override
	public int getFeatureSubID() {  return BTMessage.SUBID_BT_HASH_REJECT;  }

	@Override
	public int getType() {  return Message.TYPE_PROTOCOL_PAYLOAD;  }

	@Override
	public byte getVersion() { return version; }

	@Override
	public String 
	getDescription() {
		if ( description == null ){
			
			description = BTMessage.ID_BT_HASH_REJECT + " blah blah";
		}

		return description;
	}

	@Override
	public DirectByteBuffer[] 
	getData() 
	{
		if ( buffer == null ){
			
			buffer = DirectByteBufferPool.getBuffer( DirectByteBuffer.AL_MSG_BT_HAVE, 4 );
			
				// encode
			
			buffer.flip( DirectByteBuffer.SS_MSG );
		}

		return new DirectByteBuffer[]{ buffer };
	}

	@Override
	public Message 
	deserialize(
		DirectByteBuffer 	data, 
		byte 				version ) 
	
		throws MessageException 
	{
		if ( data == null ){
			
			throw new MessageException( "[" +getID() + "] decode error: data == null" );
		}

		if ( data.remaining( DirectByteBuffer.SS_MSG ) != 4 ){
			
			throw new MessageException( "[" +getID() + "] decode error: payload.remaining[" +data.remaining( DirectByteBuffer.SS_MSG )+ "] != 4" );
		}

			// decode

		data.returnToPool();

		return new BTHashReject( version );
	}

	@Override
	public void 
	destroy() 
	{
		if ( buffer != null ){
			
			buffer.returnToPool();
		}
	}
}
