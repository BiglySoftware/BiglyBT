/*
 * Created on 02-Dec-2005
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

package com.biglybt.core.disk.impl.access.impl;

import com.biglybt.core.disk.DiskManagerWriteRequest;
import com.biglybt.core.util.DirectByteBuffer;


public class
DiskManagerWriteRequestImpl
	extends DiskManagerRequestImpl
	implements DiskManagerWriteRequest
{
	private final int 				pieceNumber;
	private final int 				offset;
	private final DirectByteBuffer	buffer;
	private final Object				user_data;

	public
	DiskManagerWriteRequestImpl(
		int 					_pieceNumber,
		int 					_offset,
		DirectByteBuffer 		_buffer,
		Object 					_user_data )
	{
		pieceNumber = _pieceNumber;
	    offset 		= _offset;
	    buffer		= _buffer;
	    user_data	=_user_data;
	}

	@Override
	protected String
	getName()
	{
		return( "Write: " + pieceNumber + ",off=" + offset +",len=" + buffer.remaining( DirectByteBuffer.SS_DW ));
	}

	@Override
	public int
	getPieceNumber()
	{
		return( pieceNumber );
	}

	@Override
	public int
	getOffset()
	{
		return( offset );
	}

	@Override
	public DirectByteBuffer
	getBuffer()
	{
		return( buffer );
	}

	@Override
	public Object
	getUserData()
	{
		return( user_data );
	}
}