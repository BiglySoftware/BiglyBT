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

import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.messaging.MessageException;
import com.biglybt.core.util.DirectByteBuffer;

public class
GenericMessage
	implements Message
{
	private DirectByteBuffer buffer = null;

	private final String 	id;
	private final String	desc;
	private final boolean	already_encoded;

	protected
	GenericMessage(
		String				_id,
		String				_desc,
		DirectByteBuffer	_buffer,
		boolean				_already_encoded )
	{
		id				= _id;
		desc			= _desc;
		buffer			= _buffer;
		already_encoded	= _already_encoded;
	}

	protected boolean
	isAlreadyEncoded()
	{
		return( already_encoded );
	}

	@Override
	public String getID()
	{
		return( id );
	}

	@Override
	public byte[]
	getIDBytes()
	{
		return( id.getBytes());
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
		return(0);
	}

	@Override
	public int
	getType()
	{
		return( TYPE_DATA_PAYLOAD );
	}

	@Override
	public String
	getDescription()
	{
		return( desc );
	}

	@Override
	public byte
	getVersion()
	{
		return( 1 );
	}

	public DirectByteBuffer
	getPayload()
	{
		return( buffer );
	}

	@Override
	public DirectByteBuffer[]
	getData()
	{
		return new DirectByteBuffer[]{ buffer };
	}

	@Override
	public Message
	deserialize(
		DirectByteBuffer 	data,
		byte				version )

	 	throws MessageException
	{
		throw( new MessageException( "not imp" ));
	}

	@Override
	public void
	destroy()
	{
		buffer.returnToPool();
	}
}
