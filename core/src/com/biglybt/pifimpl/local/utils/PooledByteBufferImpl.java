/*
 * Created on 21-Jul-2005
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

package com.biglybt.pifimpl.local.utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

import com.biglybt.core.util.BDecoder;
import com.biglybt.core.util.DirectByteBuffer;
import com.biglybt.core.util.DirectByteBufferPool;
import com.biglybt.pif.utils.PooledByteBuffer;

public class
PooledByteBufferImpl
	implements PooledByteBuffer
{
	private DirectByteBuffer	buffer;

	public
	PooledByteBufferImpl(
		DirectByteBuffer	_buffer )
	{
		buffer	= _buffer;
	}

	public
	PooledByteBufferImpl(
		int		size )
	{
		buffer = DirectByteBufferPool.getBuffer( DirectByteBuffer.AL_EXTERNAL, size );
	}

	public
	PooledByteBufferImpl(
		byte[]		data )
	{
		buffer = DirectByteBufferPool.getBuffer( DirectByteBuffer.AL_EXTERNAL, data.length );

		buffer.put( DirectByteBuffer.AL_EXTERNAL, data );

		buffer.position( DirectByteBuffer.AL_EXTERNAL, 0 );
	}

	public
	PooledByteBufferImpl(
		byte[]		data,
		int			offset,
		int			length )
	{
		buffer = DirectByteBufferPool.getBuffer( DirectByteBuffer.AL_EXTERNAL, length );

		buffer.put( DirectByteBuffer.AL_EXTERNAL, data, offset, length );

		buffer.position( DirectByteBuffer.AL_EXTERNAL, 0 );
	}

	@Override
	public byte[]
	toByteArray()
	{
		buffer.position( DirectByteBuffer.SS_EXTERNAL, 0 );

		int	len = buffer.limit( DirectByteBuffer.SS_EXTERNAL );

		byte[]	res = new byte[len];

		buffer.get( DirectByteBuffer.SS_EXTERNAL, res );

		buffer.position( DirectByteBuffer.SS_EXTERNAL, 0 );

		return( res );
	}

	@Override
	public ByteBuffer
	toByteBuffer()
	{
		return( buffer.getBuffer( DirectByteBuffer.SS_EXTERNAL ));
	}

	@Override
	public Map
	toMap()

		throws IOException
	{
		return( BDecoder.decode( toByteArray()));
	}

	public DirectByteBuffer
	getBuffer()
	{
		return( buffer );
	}

	@Override
	public void
	returnToPool()
	{
		buffer.returnToPool();
	}
}
