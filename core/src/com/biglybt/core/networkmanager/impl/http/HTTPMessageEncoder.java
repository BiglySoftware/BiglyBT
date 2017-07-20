/*
 * Created on 2 Oct 2006
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

package com.biglybt.core.networkmanager.impl.http;

import com.biglybt.core.networkmanager.RawMessage;
import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.messaging.MessageStreamEncoder;
import com.biglybt.core.peermanager.messaging.bittorrent.BTMessage;

public class
HTTPMessageEncoder
	implements MessageStreamEncoder
{
	private HTTPNetworkConnection	http_connection;

	public void
	setConnection(
		HTTPNetworkConnection	_http_connection )
	{
		http_connection	= _http_connection;
	}

	@Override
	public RawMessage[]
	encodeMessage(
		Message message )
	{
		String	id = message.getID();

		// System.out.println( "encodeMessage: " + message.getID());

		RawMessage	raw_message = null;

		if ( id.equals( BTMessage.ID_BT_HANDSHAKE )){

			raw_message = http_connection.encodeHandShake( message );

		}else if ( id.equals( BTMessage.ID_BT_CHOKE )){

			raw_message = http_connection.encodeChoke();

		}else if ( id.equals( BTMessage.ID_BT_UNCHOKE )){

			raw_message = http_connection.encodeUnchoke();

		}else if ( id.equals( BTMessage.ID_BT_BITFIELD)){

			raw_message = http_connection.encodeBitField();

		}else if ( id.equals( BTMessage.ID_BT_PIECE )){

			return( http_connection.encodePiece( message ));

		}else if ( id.equals( HTTPMessage.MSG_ID )){

			raw_message = ((HTTPMessage)message).encode( message );
		}

		if ( raw_message == null ){

			raw_message = http_connection.getEmptyRawMessage( message );
		}

		return( new RawMessage[]{ raw_message });
	}
}
