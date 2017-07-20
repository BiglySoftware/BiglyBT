/*
 * Created on Jan 30, 2004
 * Created by Alon Rohter
 * Copyright (C) 2004, 2005, 2006 Alon Rohter, All Rights Reserved.
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
package com.biglybt.core.util;



public abstract class
DirectByteBufferPool
{
	//According to reports (from the http://mina.apache.org folks), hotspot vms actually
	//work better with non-direct (heap) buffers for network and disk io these days.

	private static final DirectByteBufferPool	impl;

	static{
		if ( System.getProperty( "use.heap.buffers" ) != null ){

			// impl = new DirectByteBufferPoolHeap();

			Debug.outNoStack( "******** USE_HEAP_BUFFERS MODE DEPRECATED ********" );
		}

		impl = new DirectByteBufferPoolReal();
	}


	public static DirectByteBuffer
	getBuffer(
		byte		allocator,
		int			length )
	{
		return( impl.getBufferSupport( allocator, length ));
	}

	protected abstract DirectByteBuffer
	getBufferSupport(
		byte		allocator,
		int			length );

	protected abstract void
	returnBufferSupport(
		DirectByteBuffer	buffer );
}
