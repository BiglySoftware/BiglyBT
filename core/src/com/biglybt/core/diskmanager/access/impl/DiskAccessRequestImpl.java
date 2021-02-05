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

package com.biglybt.core.diskmanager.access.impl;

import com.biglybt.core.diskmanager.access.DiskAccessRequest;
import com.biglybt.core.diskmanager.access.DiskAccessRequestListener;
import com.biglybt.core.diskmanager.cache.CacheFile;
import com.biglybt.core.diskmanager.cache.CacheFileManagerException;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DirectByteBuffer;
import com.biglybt.core.util.SystemTime;

public class
DiskAccessRequestImpl

	implements DiskAccessRequest
{
	protected static final short	OP_READ				= 1;
	protected static final short	OP_WRITE			= 2;
	protected static final short	OP_WRITE_AND_FREE	= 3;

	private final long						create_time = SystemTime.getMonotonousTime();

	private final CacheFile					file;
	private final long						offset;
	private final DirectByteBuffer			buffer;
	private final DiskAccessRequestListener	listener;
	private final short						op;
	private final short						cache_policy;

	private final int							size;

	private volatile boolean	cancelled;

	protected
	DiskAccessRequestImpl(
		CacheFile					_file,
		long						_offset,
		DirectByteBuffer			_buffer,
		DiskAccessRequestListener	_listener,
		short						_op,
		short						_cache_policy )
	{
		file			= _file;
		offset			= _offset;
		buffer			= _buffer;
		listener		= _listener;
		op				= _op;
		cache_policy	= _cache_policy;

		size = buffer.remaining( DirectByteBuffer.SS_FILE );
	}

	@Override
	public long 
	getCreateMonoTime()
	{
		return( create_time );
	}
	
	@Override
	public int
	getSize()
	{
		return( size );
	}

	protected void
	runRequest()
	{
		if ( cancelled ){

			listener.requestCancelled( this );

			return;
		}

		//System.out.println( "DiskReq:" + Thread.currentThread().getName() + ": " + op + " - " + offset );

		try{
			if ( op == OP_READ ){

				file.read( buffer, offset, cache_policy );

			}else if ( op == OP_WRITE ){

				file.write( buffer, offset );

			}else{

				file.writeAndHandoverBuffer( buffer, offset );
			}

			listener.requestExecuted( size );

			listener.requestComplete( this );

		}catch( Throwable e ){

			listener.requestFailed( this, e );
		}
	}

	protected boolean
	canBeAggregatedWith(
		DiskAccessRequestImpl	other )
	{
		return( op == other.getOperation() && cache_policy == other.getCachePolicy());
	}

	protected static void
	runAggregated(
		DiskAccessRequestImpl		base_request,
		DiskAccessRequestImpl[]		requests )
	{
			// assumption - they are all for the same file, sequential offsets and aggregatable, not cancelled

		int			op 				= base_request.getOperation();

		CacheFile	file 			= base_request.getFile();
		long		offset			= base_request.getOffset();
		short		cache_policy	= base_request.getCachePolicy();

		DirectByteBuffer[]	buffers = new DirectByteBuffer[requests.length];

		long	current_offset 	= offset;
		long	total_size		= 0;

		for (int i=0;i<buffers.length;i++){

			DiskAccessRequestImpl	request = requests[i];

			if ( current_offset != request.getOffset()){

				Debug.out( "assert failed: requests not contiguous" );
			}

			int	size = request.getSize();

			current_offset += size;

			total_size += size;

			buffers[i] = request.getBuffer();
		}

		try{
			if ( op == OP_READ ){

				file.read( buffers, offset, cache_policy );

			}else if ( op == OP_WRITE ){

				file.write( buffers, offset );

			}else{

				file.writeAndHandoverBuffers( buffers, offset );
			}

			base_request.getListener().requestExecuted( total_size );

			for (int i=0;i<requests.length;i++){

				DiskAccessRequestImpl	request = requests[i];

				request.getListener().requestComplete( request );

				if ( request != base_request ){

					request.getListener().requestExecuted( 0 );
				}
			}

		}catch( CacheFileManagerException e ){

			int	fail_index = e.getFailIndex();

			for (int i=0;i<fail_index;i++){

				DiskAccessRequestImpl	request = requests[i];

				request.getListener().requestComplete( request );
			}

			for (int i=fail_index;i<requests.length;i++){

				DiskAccessRequestImpl	request = requests[i];

				request.getListener().requestFailed( request, e );
			}
		}catch( Throwable e ){

			for (int i=0;i<requests.length;i++){

				DiskAccessRequestImpl	request = requests[i];

				request.getListener().requestFailed( request, e );
			}
		}
	}

	@Override
	public CacheFile
	getFile()
	{
		return( file );
	}

	@Override
	public long
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
	public void
	cancel()
	{
		cancelled	= true;
	}

	public boolean
	isCancelled()
	{
		return( cancelled );
	}

	public short
	getCachePolicy()
	{
		return( cache_policy );
	}

	protected int
	getOperation()
	{
		return( op );
	}

	@Override
	public int
	getPriority()
	{
		return( listener.getPriority());
	}

	@Override
	public Object 
	getUserData()
	{
		return( listener.getUserData());
	}
	
	protected DiskAccessRequestListener
	getListener()
	{
		return( listener );
	}
}
