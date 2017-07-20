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
package com.biglybt.core.peermanager.messaging.bittorrent.ltep;

import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.util.DirectByteBuffer;

/**
 * @author Allan Crooks
 *
 */
public class LTDisabledExtensionMessage implements LTMessage {

	public static final LTDisabledExtensionMessage INSTANCE = new LTDisabledExtensionMessage();

	private LTDisabledExtensionMessage() {}

	@Override
	public Message deserialize(DirectByteBuffer data, byte version) {
		return INSTANCE;
	}

	@Override
	public void destroy() {}

	// Not meant to be used for outgoing messages, so raise an error if anyone tries to do it.
	@Override
	public DirectByteBuffer[] getData() {
		throw new RuntimeException("Disabled extension message not meant to be used for serialisation!");
	}

	/* (non-Javadoc)
	 * @see com.biglybt.core.peermanager.messaging.Message#getDescription()
	 */
	@Override
	public String getDescription() {
		return "Disabled extension message over LTEP (ignored)";
	}

	@Override
	public String getFeatureID() {return LTMessage.LT_FEATURE_ID;}
	@Override
	public int getFeatureSubID() {return LTMessage.SUBID_DISABLED_EXT;}
	@Override
	public String getID() {return LTMessage.ID_DISABLED_EXT;}
	@Override
	public byte[] getIDBytes() {return LTMessage.ID_DISABLED_EXT_BYTES;}
	@Override
	public int getType() {return Message.TYPE_PROTOCOL_PAYLOAD;}
	@Override
	public byte getVersion() {return 0;}

}
