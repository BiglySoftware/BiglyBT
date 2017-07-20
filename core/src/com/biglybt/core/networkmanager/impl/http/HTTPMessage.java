/*
 * Created on 5 Oct 2006
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

import java.nio.ByteBuffer;

import com.biglybt.core.networkmanager.RawMessage;
import com.biglybt.core.networkmanager.impl.RawMessageImpl;
import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.messaging.MessageException;
import com.biglybt.core.util.DirectByteBuffer;

public class
HTTPMessage
	implements Message
{
	public static final String	MSG_ID 			= "HTTP_DATA";
	private static final byte[]	MSG_ID_BYTES	= MSG_ID.getBytes();
	private static final String	MSG_DESC		= "HTTP data";


	private final DirectByteBuffer[]	data;

	protected
	HTTPMessage(
		String	stuff )
	{
		data = new DirectByteBuffer[]{ new DirectByteBuffer( ByteBuffer.wrap( stuff.getBytes())) };
	}

	protected
	HTTPMessage(
		byte[]	stuff )
	{
		data = new DirectByteBuffer[]{ new DirectByteBuffer( ByteBuffer.wrap( stuff )) };
	}

	@Override
	public String
	getID()
	{
		return( MSG_ID );
	}

	@Override
	public byte[]
	getIDBytes()
	{
		return( MSG_ID_BYTES );
	}

	@Override
	public String
	getFeatureID()
	{
		return( null );
	}

	@Override
	public int
	getFeatureSubID()
	{
		return( 0 );
	}

	@Override
	public int
	getType()
	{
		return( TYPE_DATA_PAYLOAD );
	}

	@Override
	public byte
	getVersion()
	{
		return( 1 );
	}

	@Override
	public String
	getDescription()
	{
		return( MSG_DESC );
	}

	@Override
	public DirectByteBuffer[]
	getData()
	{
		return( data );
	}

	@Override
	public Message
	deserialize(
		DirectByteBuffer 	data,
		byte				version )

		throws MessageException
	{
		throw( new MessageException( "not supported" ));
	}

	protected RawMessage
	encode(
		Message message )
	{
		return(
				new RawMessageImpl(
						message,
						data,
						RawMessage.PRIORITY_HIGH,
						true,
						new Message[0] ));
	}

	@Override
	public void
	destroy()
	{
		data[0].returnToPool();
	}
}
