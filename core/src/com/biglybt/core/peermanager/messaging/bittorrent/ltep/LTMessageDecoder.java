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
import com.biglybt.core.peermanager.messaging.MessageException;
import com.biglybt.core.peermanager.messaging.MessageManager;
import com.biglybt.core.peermanager.messaging.bittorrent.BTMessageDecoder;
import com.biglybt.core.peermanager.messaging.bittorrent.BTMessageFactory;
import com.biglybt.core.util.CopyOnWriteMap;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DirectByteBuffer;

/**
 * @author Allan Crooks
 *
 */
public class LTMessageDecoder extends BTMessageDecoder {

	private static final CopyOnWriteMap<Byte,byte[]>	default_entension_handlers = new CopyOnWriteMap<>();

	public static void
	addDefaultExtensionHandler(
		long		id,
		byte[]		message_id )
	{
		default_entension_handlers.put( (byte)id, message_id );
	}

	public static void
	removeDefaultExtensionHandler(
		long		id )
	{
		default_entension_handlers.remove( (byte)id );
	}

	private final CopyOnWriteMap<Byte,byte[]>	extension_handlers = new CopyOnWriteMap<>();

	public
	LTMessageDecoder()
	{
		if ( default_entension_handlers.size() > 0 ){

			extension_handlers.putAll( default_entension_handlers );
		}
	}

	@Override
	protected Message createMessage(DirectByteBuffer ref_buff) throws MessageException {
		// Check to see if it is a LT-extension message. If not, delegate to BTMessageDecoder.
		int old_position = ref_buff.position(DirectByteBuffer.SS_MSG);
		byte id = ref_buff.get(DirectByteBuffer.SS_MSG);
		if (id != 20) {
			ref_buff.position(DirectByteBuffer.SS_MSG, old_position);
			return super.createMessage(ref_buff);
		}

		// Here is where we decode the message.
		id = ref_buff.get(DirectByteBuffer.SS_MSG);
		switch(id) {
			case LTMessage.SUBID_LT_HANDSHAKE:
				return MessageManager.getSingleton().createMessage(LTMessage.ID_LT_HANDSHAKE_BYTES, ref_buff, BTMessageFactory.MESSAGE_VERSION_INITIAL);
			case LTMessage.SUBID_UT_PEX:
				return MessageManager.getSingleton().createMessage(LTMessage.ID_UT_PEX_BYTES, ref_buff, BTMessageFactory.MESSAGE_VERSION_INITIAL);
			case LTMessage.SUBID_UT_METADATA:
				return MessageManager.getSingleton().createMessage(LTMessage.ID_UT_METADATA_BYTES, ref_buff, BTMessageFactory.MESSAGE_VERSION_INITIAL);
			case LTMessage.SUBID_UT_UPLOAD_ONLY:
				return MessageManager.getSingleton().createMessage(LTMessage.ID_UT_UPLOAD_ONLY_BYTES, ref_buff, BTMessageFactory.MESSAGE_VERSION_INITIAL);
			case LTMessage.SUBID_UT_HOLEPUNCH:
				return MessageManager.getSingleton().createMessage(LTMessage.ID_UT_HOLEPUNCH_BYTES, ref_buff, BTMessageFactory.MESSAGE_VERSION_INITIAL);
			default: {
			  byte[]	message_id;

			  message_id = extension_handlers.get( id );

			  if ( message_id != null ){
				return MessageManager.getSingleton().createMessage( message_id, ref_buff, (byte)1);
			  }
		      System.out.println( "Unknown LTEP message id [" +id+ "]" );
		      throw new MessageException( "Unknown LTEP message id [" +id+ "]" );
			}
		}
	}

	public void
	addExtensionHandler(
		byte		id,
		byte[]		message_id )
	{
		extension_handlers.put( id, message_id );
	}

	public void
	removeExtensionHandler(
		byte		id )
	{
		extension_handlers.remove( id );
	}
}
