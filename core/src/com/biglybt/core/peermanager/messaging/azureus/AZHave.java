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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.messaging.MessageException;
import com.biglybt.core.peermanager.messaging.MessagingUtil;
import com.biglybt.core.util.DirectByteBuffer;

public class
AZHave
	implements AZMessage
{
	private final byte version;
	private DirectByteBuffer buffer = null;

	private final int[]	piece_numbers;

	public
	AZHave(
		int[]	_piece_numbers,
		byte	_version )
	{
		piece_numbers	= _piece_numbers;
		version			= _version;
	}

	@Override
	public String
	getID()
	{
		return( AZMessage.ID_AZ_HAVE );
	}

	@Override
	public byte[]
	getIDBytes()
	{
		return( AZMessage.ID_AZ_HAVE_BYTES );
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
		return( AZMessage.SUBID_AZ_HAVE );
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
		StringBuilder str = new StringBuilder(piece_numbers.length*10);

		for (int i=0;i<piece_numbers.length;i++){

			if ( i > 0 ){
				str.append(",");
			}

			str.append( piece_numbers[i] );
		}

		return( getID() + " " + str );
	}

	public int[]
	getPieceNumbers()
	{
		return( piece_numbers );
	}

	@Override
	public DirectByteBuffer[]
	getData()
	{
		if ( buffer == null ){

			Map	map = new HashMap();

			List l = new ArrayList( piece_numbers.length );

			for (int i=0;i<piece_numbers.length;i++){

				l.add( new Long( piece_numbers[i] ));
			}

			map.put( "pieces", l );

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

		List	l	= (List)payload.get( "pieces");

		int[]	pieces = new int[l.size()];

		for (int i=0;i<pieces.length;i++){

			pieces[i] = ((Long)l.get(i)).intValue();
		}

		AZHave message =  new AZHave( pieces, version );

		return( message );
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
