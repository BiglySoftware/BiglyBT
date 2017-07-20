/*
 * Created on Jan 26, 2007
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


package com.biglybt.core.util;

import java.nio.ByteBuffer;

public class
ReferenceCountedDirectByteBuffer
	extends DirectByteBuffer
{
	private DirectByteBuffer	basis;

	private int	ref_count = 1;

	protected
	ReferenceCountedDirectByteBuffer(
		DirectByteBuffer	_basis )
	{
		this( _basis.getBufferInternal());

		basis	= _basis;
	}

	protected
	ReferenceCountedDirectByteBuffer(
		ByteBuffer		_buffer )
	{
		super( _buffer );
	}

	public ReferenceCountedDirectByteBuffer
	duplicate(
		int		offset,
		int		length )
	{
		ByteBuffer	duplicate = getBufferInternal().duplicate();

		duplicate.position( duplicate.position() + offset );

		duplicate.limit(  duplicate.position() + length );

		ReferenceCountedDirectByteBuffer res = new ReferenceCountedDirectByteBufferDuplicate( duplicate );

		return( res );
	}

	public void
	incrementReferenceCount()
	{
		synchronized( this ){

			ref_count++;

			// System.out.println( "" + this + ": rc=" + ref_count );
		}
	}

	public void
	decrementReferenceCount()
	{
		synchronized( this ){

			ref_count--;

			// System.out.println( "" + this + ": rc=" + ref_count );

			if ( ref_count == 0 ){

				basis.returnToPool();
			}
		}
	}

	public int
	getReferenceCount()
	{
		synchronized( this ){

			return( ref_count );
		}
	}

	@Override
	public void
	returnToPool()
	{
		decrementReferenceCount();
	}

	protected class
	ReferenceCountedDirectByteBufferDuplicate
		extends ReferenceCountedDirectByteBuffer
	{
		protected
		ReferenceCountedDirectByteBufferDuplicate(
			ByteBuffer		owner )
		{
			super( owner );

			incrementReferenceCount();
		}

		@Override
		public ReferenceCountedDirectByteBuffer
		duplicate(
			int		offset,
			int		length )
		{
			Debug.out( "dup dup" );

			return( null );
		}

		@Override
		public void
		returnToPool()
		{
			decrementReferenceCount();
		}

		@Override
		public void
		incrementReferenceCount()
		{
			ReferenceCountedDirectByteBuffer.this.incrementReferenceCount();
		}

		@Override
		public void
		decrementReferenceCount()
		{
			ReferenceCountedDirectByteBuffer.this.decrementReferenceCount();
		}
	}
}
