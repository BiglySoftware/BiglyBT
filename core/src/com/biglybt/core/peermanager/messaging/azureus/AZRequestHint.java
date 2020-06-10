/*
 * Created on Jan 19, 2007
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


package com.biglybt.core.peermanager.messaging.azureus;

import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.messaging.MessageException;
import com.biglybt.core.peermanager.messaging.MessagingUtil;
import com.biglybt.core.util.DirectByteBuffer;

public class
AZRequestHint
	implements AZMessage
{
	private final byte version;
	private DirectByteBuffer buffer = null;

	private final int		piece_number;
	private final int		offset;
	private final int		length;
	private final int		life;

	public
	AZRequestHint(
		int		_piece_number,
		int		_offset,
		int		_length,
		int		_life,
		byte	_version )
	{
		piece_number	= _piece_number;
		offset			= _offset;
		length			= _length;
		life			= _life;
		version			= _version;
	}

	@Override
	public String
	getID()
	{
		return( AZMessage.ID_AZ_REQUEST_HINT );
	}

	@Override
	public byte[]
	getIDBytes()
	{
		return( AZMessage.ID_AZ_REQUEST_HINT_BYTES );
	}

	@Override
	public String
	getFeatureID()
	{
		return( AZMessage.AZ_FEATURE_ID );
	}

	@Override
	public int
	getFeatureSubID()
	{
		return( AZMessage.SUBID_AZ_REQUEST_HINT );
	}

	@Override
	public int
	getType()
	{
		return( Message.TYPE_PROTOCOL_PAYLOAD );
	}

	@Override
	public byte getVersion() { return version; }

	@Override
	public String
	getDescription()
	{
		return( getID() + " piece #" + piece_number + ":" + offset + "->" + (offset + length -1) + "/" + life );
	}

	public int
	getPieceNumber()
	{
		return( piece_number );
	}

	public int
	getOffset()
	{
		return( offset );
	}
	public int
	getLength()
	{
		return( length );
	}
	public int
	getLife()
	{
		return( life );
	}

	@Override
	public DirectByteBuffer[]
	getData()
	{
		if ( buffer == null ){

			Map	map = new HashMap();

			map.put( "piece", new Long( piece_number ));
			map.put( "offset", new Long( offset ));
			map.put( "length", new Long( length ));
			map.put( "life", new Long( life ));

			buffer = MessagingUtil.convertPayloadToBencodedByteStream( map, DirectByteBuffer.AL_MSG );
		}

		return new DirectByteBuffer[]{ buffer };
	}

	@Override
	public Message
	deserialize(
		DirectByteBuffer 	data,
		byte				version )

		throws MessageException
	{
		Map payload = MessagingUtil.convertBencodedByteStreamToPayload( data, 1, getID() );

		int	piece_number	= ((Long)payload.get( "piece")).intValue();
		int	offset			= ((Long)payload.get( "offset")).intValue();
		int	length			= ((Long)payload.get( "length")).intValue();
		int	life			= ((Long)payload.get( "life" )).intValue();

		return( new AZRequestHint( piece_number, offset, length, life, version ));
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
