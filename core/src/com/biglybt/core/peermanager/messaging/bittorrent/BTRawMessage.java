/*
 * Created on Mar 20, 2008
 * Created by Paul Gardner
 *
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

import com.biglybt.core.networkmanager.RawMessage;
import com.biglybt.core.peermanager.messaging.Message;
import com.biglybt.core.peermanager.messaging.MessageException;
import com.biglybt.core.util.DirectByteBuffer;

public class
BTRawMessage
	implements BTMessage, RawMessage
{
	private final DirectByteBuffer		buffer;

	public
	BTRawMessage(
		DirectByteBuffer		_buffer )
	{
		buffer	= _buffer;
	}

	@Override
	public String getID()
	{
		return( "" );
	}

	@Override
	public byte[]
	getIDBytes()
	{
		return( null );
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
		return( "<raw bt data>" );
	}

	@Override
	public byte
	getVersion()
	{
		return( 1 );
	}

	@Override
	public Message
	deserialize(
		DirectByteBuffer	data,
		byte 				version )

		throws MessageException
	{
		throw( new MessageException( "not imp" ));
	}

	@Override
	public DirectByteBuffer[]
	getData()
	{
		return new DirectByteBuffer[]{ buffer };
	}

	@Override
	public DirectByteBuffer[]
	getRawData()
	{
		return( new DirectByteBuffer[]{ buffer });
	}

	@Override
	public int
	getPriority()
	{
		return( PRIORITY_HIGH );
	}

	@Override
	public boolean
	isNoDelay()
	{
		return( true );
	}

	@Override
	public void
	setNoDelay()
	{
	}

	@Override
	public Message[]
	messagesToRemove()
	{
		return( null );
	}

	@Override
	public Message
	getBaseMessage()
	{
		return( this );
	}

	@Override
	public void
	destroy()
	{
		if( buffer != null )  buffer.returnToPool();
	}
}
