/*
 * Created on 17 Sep 2007
 * Created by Allan Crooks
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
 */
package com.biglybt.core.peermanager.messaging.bittorrent;

import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.messaging.MessageException;
import com.biglybt.core.util.DirectByteBuffer;
import com.biglybt.core.util.DirectByteBufferPool;

/**
 * @author Allan Crooks
 *
 */
public class BTLTMessage implements BTMessage {

	public final byte extension_id;
	public final Message base_message;
	public DirectByteBuffer buffer_header;

	public BTLTMessage(Message base_message, byte extension_id) {
		this.base_message = base_message;
		this.extension_id = extension_id;
	}

	// This class should not be used for deserialisation!
	@Override
	public Message deserialize(DirectByteBuffer data, byte version) throws MessageException {
		throw new MessageException("BTLTMessage cannot be used for message deserialization!");
	}

	@Override
	public void destroy() {
		if (base_message != null) {base_message.destroy();}
		if (buffer_header != null) {
			buffer_header.returnToPool();
			buffer_header = null;
		}
	}

	@Override
	public DirectByteBuffer[] getData() {
		DirectByteBuffer[] orig_data = this.base_message.getData();
		DirectByteBuffer[] new_data = new DirectByteBuffer[orig_data.length + 1];

		if (buffer_header == null ) {
			buffer_header = DirectByteBufferPool.getBuffer(DirectByteBuffer.AL_MSG_LT_EXT_MESSAGE, 1);
			buffer_header.put(DirectByteBuffer.SS_MSG, this.extension_id);
			buffer_header.flip(DirectByteBuffer.SS_MSG);
		}

		new_data[0] = buffer_header;
		System.arraycopy(orig_data, 0, new_data, 1, orig_data.length);
		return new_data;
	}

	@Override
	public String getDescription() {
		return base_message.getDescription();
	}

	@Override
	public String getFeatureID() {
		if ( base_message == null ){
			return( BTMessage.BT_FEATURE_ID );
		}
		return base_message.getFeatureID();
	}

	@Override
	public int getFeatureSubID() {
		if ( base_message == null ){
			return( BTMessage.SUBID_BT_LT_EXT_MESSAGE );
		}
		return base_message.getFeatureSubID();
	}

	@Override
	public String getID() {
		return BTMessage.ID_BT_LT_EXT_MESSAGE;
	}

	@Override
	public byte[] getIDBytes() {
		return BTMessage.ID_BT_LT_EXT_MESSAGE_BYTES;
	}

	@Override
	public int getType() {
		return Message.TYPE_PROTOCOL_PAYLOAD;
	}

	@Override
	public byte getVersion() {
		return 0;
	}

}
