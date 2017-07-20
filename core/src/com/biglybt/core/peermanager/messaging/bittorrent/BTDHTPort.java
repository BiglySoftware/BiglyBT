/*
 * Created on 15 Jan 2008
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
public class BTDHTPort implements BTMessage {

	private final int port;
	private DirectByteBuffer buffer;

	public BTDHTPort(int port) {
		this.port = port;
	}

	@Override
	public Message deserialize(DirectByteBuffer data, byte version) throws MessageException {
		if (data == null)
			throw new MessageException("[" +getID() + "] decode error: data == null");
	    if (data.remaining(DirectByteBuffer.SS_MSG) != 2)
	        throw new MessageException("[" +getID() + "] decode error: payload.remaining[" +data.remaining( DirectByteBuffer.SS_MSG )+ "] != 2");
	    short s_port = data.getShort(DirectByteBuffer.SS_MSG);
	    data.returnToPool();
	    return new BTDHTPort(0xFFFF & s_port);
	}

	@Override
	public DirectByteBuffer[] getData() {
		if (buffer == null) {
			buffer = DirectByteBufferPool.getBuffer(DirectByteBuffer.AL_MSG_BT_DHT_PORT, 2);
			short s_port = (short)port;
			buffer.put(DirectByteBuffer.SS_MSG, (byte)(s_port >> 8));
			buffer.put(DirectByteBuffer.SS_MSG, (byte)(s_port & 0xff));
			buffer.flip(DirectByteBuffer.SS_MSG);
		}
		return new DirectByteBuffer[] {buffer};
	}

	@Override
	public String getDescription() {
		return getID() + " (port " + port + ")";
	}

	@Override
	public void destroy() {
		if (buffer != null) {buffer.returnToPool();}
	}

	@Override
	public String getFeatureID() {return BTMessage.BT_FEATURE_ID;}
	@Override
	public int getFeatureSubID() {return BTMessage.SUBID_BT_DHT_PORT;}
	@Override
	public String getID() {return BTMessage.ID_BT_DHT_PORT;}
	@Override
	public byte[] getIDBytes() {return BTMessage.ID_BT_DHT_PORT_BYTES;}
	@Override
	public int getType() {return Message.TYPE_PROTOCOL_PAYLOAD;}
	@Override
	public byte getVersion() {return (byte)1;}

	public int getDHTPort() {return port;}

}
