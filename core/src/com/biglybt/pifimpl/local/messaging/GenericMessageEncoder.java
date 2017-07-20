/*
 * Created on 19 Jun 2006
 * Created by Paul Gardner
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

package com.biglybt.pifimpl.local.messaging;

import com.biglybt.core.networkmanager.RawMessage;
import com.biglybt.core.networkmanager.impl.RawMessageImpl;
import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.messaging.MessageStreamEncoder;
import com.biglybt.core.util.DirectByteBuffer;
import com.biglybt.core.util.DirectByteBufferPool;

public class
GenericMessageEncoder
	implements MessageStreamEncoder
{
	@Override
	public RawMessage[]
	encodeMessage(
		Message _message )
	{
		GenericMessage	message = (GenericMessage)_message;

		DirectByteBuffer	payload = message.getPayload();

		if ( message.isAlreadyEncoded()){

			return(
				new RawMessage[]{
					new RawMessageImpl(
						message,
						new DirectByteBuffer[]{ payload },
						RawMessage.PRIORITY_NORMAL,
						true,	// send immediately
						new Message[0] )});

		}else{

			DirectByteBuffer 	header = DirectByteBufferPool.getBuffer( DirectByteBuffer.AL_EXTERNAL, 4 );

			header.putInt( DirectByteBuffer.SS_MSG, payload.remaining( DirectByteBuffer.SS_MSG ));

			header.flip( DirectByteBuffer.SS_MSG );

			return(
				new RawMessage[]{
					new RawMessageImpl(
						message,
						new DirectByteBuffer[]{ header, payload },
						RawMessage.PRIORITY_NORMAL,
						true,	// send immediately
						new Message[0] )});
		}
	}
}
