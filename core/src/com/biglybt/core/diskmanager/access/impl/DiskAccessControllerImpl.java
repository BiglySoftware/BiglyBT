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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.diskmanager.access.DiskAccessController;
import com.biglybt.core.diskmanager.access.DiskAccessControllerStats;
import com.biglybt.core.diskmanager.access.DiskAccessRequest;
import com.biglybt.core.diskmanager.access.DiskAccessRequestListener;
import com.biglybt.core.diskmanager.cache.CacheFile;
import com.biglybt.core.stats.CoreStats;
import com.biglybt.core.stats.CoreStatsProvider;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DirectByteBuffer;

public class
DiskAccessControllerImpl
	implements DiskAccessController, CoreStatsProvider
{
	final DiskAccessControllerInstance	read_dispatcher;
	final DiskAccessControllerInstance	write_dispatcher;

	public
	DiskAccessControllerImpl(
		String	_name,
		int		_max_read_threads,
		int		_max_read_mb,
		int 	_max_write_threads,
		int		_max_write_mb )
	{
		boolean	enable_read_aggregation 		= COConfigurationManager.getBooleanParameter( "diskmanager.perf.read.aggregate.enable");
		int		read_aggregation_request_limit 	= COConfigurationManager.getIntParameter( "diskmanager.perf.read.aggregate.request.limit", 4 );
		int		read_aggregation_byte_limit 	= COConfigurationManager.getIntParameter( "diskmanager.perf.read.aggregate.byte.limit", 64*1024 );


		boolean	enable_write_aggregation 		= COConfigurationManager.getBooleanParameter( "diskmanager.perf.write.aggregate.enable");
		int		write_aggregation_request_limit = COConfigurationManager.getIntParameter( "diskmanager.perf.write.aggregate.request.limit", 8 );
		int		write_aggregation_byte_limit 	= COConfigurationManager.getIntParameter( "diskmanager.perf.write.aggregate.byte.limit", 128*1024 );

		read_dispatcher 	=
			new DiskAccessControllerInstance(
					_name + "/" + "read",
					enable_read_aggregation,
					read_aggregation_request_limit,
					read_aggregation_byte_limit,
					_max_read_threads,
					_max_read_mb );

		write_dispatcher 	=
			new DiskAccessControllerInstance(
					_name + "/" + "write",
					enable_write_aggregation,
					write_aggregation_request_limit,
					write_aggregation_byte_limit,
					_max_write_threads,
					_max_write_mb );

		Set	types = new HashSet();

		types.add( CoreStats.ST_DISK_READ_QUEUE_LENGTH );
		types.add( CoreStats.ST_DISK_READ_QUEUE_BYTES );
		types.add( CoreStats.ST_DISK_READ_REQUEST_COUNT );
		types.add( CoreStats.ST_DISK_READ_REQUEST_SINGLE );
		types.add( CoreStats.ST_DISK_READ_REQUEST_MULTIPLE );
		types.add( CoreStats.ST_DISK_READ_REQUEST_BLOCKS );
		types.add( CoreStats.ST_DISK_READ_BYTES_TOTAL );
		types.add( CoreStats.ST_DISK_READ_BYTES_SINGLE );
		types.add( CoreStats.ST_DISK_READ_BYTES_MULTIPLE );
		types.add( CoreStats.ST_DISK_READ_IO_TIME );
		types.add( CoreStats.ST_DISK_READ_IO_COUNT );

		types.add( CoreStats.ST_DISK_WRITE_QUEUE_LENGTH );
		types.add( CoreStats.ST_DISK_WRITE_QUEUE_BYTES );
		types.add( CoreStats.ST_DISK_WRITE_REQUEST_COUNT );
		types.add( CoreStats.ST_DISK_WRITE_REQUEST_BLOCKS );
		types.add( CoreStats.ST_DISK_WRITE_BYTES_TOTAL );
		types.add( CoreStats.ST_DISK_WRITE_BYTES_SINGLE );
		types.add( CoreStats.ST_DISK_WRITE_BYTES_MULTIPLE );
		types.add( CoreStats.ST_DISK_WRITE_IO_TIME );

		CoreStats.registerProvider( types, this );
	}

	@Override
	public void
	updateStats(
		Set		types,
		Map		values )
	{
			//read

		if ( types.contains( CoreStats.ST_DISK_READ_QUEUE_LENGTH )){

			values.put( CoreStats.ST_DISK_READ_QUEUE_LENGTH, new Long( read_dispatcher.getQueueSize()));
		}

		if ( types.contains( CoreStats.ST_DISK_READ_QUEUE_BYTES )){

			values.put( CoreStats.ST_DISK_READ_QUEUE_BYTES, new Long( read_dispatcher.getQueuedBytes()));
		}

		if ( types.contains( CoreStats.ST_DISK_READ_REQUEST_COUNT )){

			values.put( CoreStats.ST_DISK_READ_REQUEST_COUNT, new Long( read_dispatcher.getTotalRequests()));
		}

		if ( types.contains( CoreStats.ST_DISK_READ_REQUEST_SINGLE )){

			values.put( CoreStats.ST_DISK_READ_REQUEST_SINGLE, new Long( read_dispatcher.getTotalSingleRequests()));
		}

		if ( types.contains( CoreStats.ST_DISK_READ_REQUEST_MULTIPLE )){

			values.put( CoreStats.ST_DISK_READ_REQUEST_MULTIPLE, new Long( read_dispatcher.getTotalAggregatedRequests()));
		}

		if ( types.contains( CoreStats.ST_DISK_READ_REQUEST_BLOCKS )){

			values.put( CoreStats.ST_DISK_READ_REQUEST_BLOCKS, new Long( read_dispatcher.getBlockCount()));
		}

		if ( types.contains( CoreStats.ST_DISK_READ_BYTES_TOTAL )){

			values.put( CoreStats.ST_DISK_READ_BYTES_TOTAL, new Long( read_dispatcher.getTotalBytes()));
		}

		if ( types.contains( CoreStats.ST_DISK_READ_BYTES_SINGLE )){

			values.put( CoreStats.ST_DISK_READ_BYTES_SINGLE, new Long( read_dispatcher.getTotalSingleBytes()));
		}

		if ( types.contains( CoreStats.ST_DISK_READ_BYTES_MULTIPLE )){

			values.put( CoreStats.ST_DISK_READ_BYTES_MULTIPLE, new Long( read_dispatcher.getTotalAggregatedBytes()));
		}

		if ( types.contains( CoreStats.ST_DISK_READ_IO_TIME )){

			values.put( CoreStats.ST_DISK_READ_IO_TIME, new Long( read_dispatcher.getIOTime()));
		}

		if ( types.contains( CoreStats.ST_DISK_READ_IO_COUNT )){

			values.put( CoreStats.ST_DISK_READ_IO_COUNT, new Long( read_dispatcher.getIOCount()));
		}

			// write

		if ( types.contains( CoreStats.ST_DISK_WRITE_QUEUE_LENGTH )){

			values.put( CoreStats.ST_DISK_WRITE_QUEUE_LENGTH, new Long( write_dispatcher.getQueueSize()));
		}

		if ( types.contains( CoreStats.ST_DISK_WRITE_QUEUE_BYTES )){

			values.put( CoreStats.ST_DISK_WRITE_QUEUE_BYTES, new Long( write_dispatcher.getQueuedBytes()));
		}

		if ( types.contains( CoreStats.ST_DISK_WRITE_REQUEST_COUNT )){

			values.put( CoreStats.ST_DISK_WRITE_REQUEST_COUNT, new Long( write_dispatcher.getTotalRequests()));
		}

		if ( types.contains( CoreStats.ST_DISK_WRITE_REQUEST_BLOCKS )){

			values.put( CoreStats.ST_DISK_WRITE_REQUEST_BLOCKS, new Long( write_dispatcher.getBlockCount()));
		}

		if ( types.contains( CoreStats.ST_DISK_WRITE_BYTES_TOTAL )){

			values.put( CoreStats.ST_DISK_WRITE_BYTES_TOTAL, new Long( write_dispatcher.getTotalBytes()));
		}

		if ( types.contains( CoreStats.ST_DISK_WRITE_BYTES_SINGLE )){

			values.put( CoreStats.ST_DISK_WRITE_BYTES_SINGLE, new Long( write_dispatcher.getTotalSingleBytes()));
		}

		if ( types.contains( CoreStats.ST_DISK_WRITE_BYTES_MULTIPLE )){

			values.put( CoreStats.ST_DISK_WRITE_BYTES_MULTIPLE, new Long( write_dispatcher.getTotalAggregatedBytes()));
		}

		if ( types.contains( CoreStats.ST_DISK_WRITE_IO_TIME )){

			values.put( CoreStats.ST_DISK_WRITE_IO_TIME, new Long( write_dispatcher.getIOTime()));
		}

		if ( types.contains( CoreStats.ST_DISK_WRITE_IO_COUNT )){

			values.put( CoreStats.ST_DISK_WRITE_IO_COUNT, new Long( write_dispatcher.getIOCount()));
		}

	}

	@Override
	public DiskAccessRequest
	queueReadRequest(
		CacheFile					file,
		long						offset,
		DirectByteBuffer			buffer,
		short						cache_policy,
		DiskAccessRequestListener	listener )
	{
		DiskAccessRequestImpl	request =
			new DiskAccessRequestImpl(
					file,
					offset,
					buffer,
					listener,
					DiskAccessRequestImpl.OP_READ,
					cache_policy );

		try{
				// do this before actual queue to guarantee that the listener gets the 'queued' callback
				// before 'complete'
			
			listener.requestQueued( request );
			
		}catch( Throwable e ){
			
			Debug.out( e );
		}
		
		read_dispatcher.queueRequest( request );

		return( request );
	}

	@Override
	public DiskAccessRequest
	queueWriteRequest(
		CacheFile					file,
		long						offset,
		DirectByteBuffer			buffer,
		boolean						free_buffer,
		DiskAccessRequestListener	listener )
	{
		// System.out.println( "write request: " + offset );

		DiskAccessRequestImpl	request =
			new DiskAccessRequestImpl(
					file,
					offset,
					buffer,
					listener,
					free_buffer?DiskAccessRequestImpl.OP_WRITE_AND_FREE:DiskAccessRequestImpl.OP_WRITE,
					CacheFile.CP_NONE );

		try{
				// do this before actual queue to guarantee that the listener gets the 'queued' callback
				// before 'complete'
		
			listener.requestQueued( request );
			
		}catch( Throwable e ){
			
			Debug.out( e );
		}

		write_dispatcher.queueRequest( request );

		return( request );
	}

	@Override
	public DiskAccessControllerStats
	getStats()
	{
		return(
			new DiskAccessControllerStats()
			{
				@Override
				public long
				getTotalReadRequests()
				{
					return( read_dispatcher.getTotalRequests());
				}

				@Override
				public long
				getTotalReadBytes()
				{
					return( read_dispatcher.getTotalBytes() );
				}
				
				public long
				getReadRequestsQueued()
				{
					return( read_dispatcher.getQueueSize());
				}
				
				public long
				getReadBytesQueued()
				{
					return( read_dispatcher.getQueuedBytes());				
				}
				
				public long
				getTotalWriteRequests()
				{
					return( write_dispatcher.getTotalRequests());		
				}				

				public long
				getTotalWriteBytes()
				{
					return( write_dispatcher.getTotalBytes() );
				}
				
				public long
				getWriteRequestsQueued()
				{
					return( write_dispatcher.getQueueSize());
				}
				
				public long
				getWriteBytesQueued()
				{
					return( write_dispatcher.getQueuedBytes());
				}
			});
	}

	@Override
	public String
	getString()
	{
		return( "read: " + read_dispatcher.getString() + ", write: " + write_dispatcher.getString());
	}
}
