/*
 * Created on 31-Jul-2004
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.disk.DiskManagerReadRequest;
import com.biglybt.core.disk.DiskManagerReadRequestListener;
import com.biglybt.core.disk.impl.DiskManagerFileInfoImpl;
import com.biglybt.core.disk.impl.DiskManagerHelper;
import com.biglybt.core.disk.impl.access.DMReader;
import com.biglybt.core.disk.impl.piecemapper.DMPieceList;
import com.biglybt.core.disk.impl.piecemapper.DMPieceMapEntry;
import com.biglybt.core.diskmanager.access.DiskAccessController;
import com.biglybt.core.diskmanager.access.DiskAccessRequest;
import com.biglybt.core.diskmanager.access.DiskAccessRequestListener;
import com.biglybt.core.diskmanager.cache.CacheFile;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.*;

/**
 * @author parg
 *
 */
public class
DMReaderImpl
	implements DMReader
{
	private static final LogIDs LOGID = LogIDs.DISK;

	private final DiskManagerHelper		disk_manager;
	private final DiskAccessController	disk_access;

	private final ConcurrentHashMap<DiskAccessRequest,String>		active_requests = new ConcurrentHashMap<>();
	
	private int								async_reads;
	private final Set<Object[]>				read_requests		= new HashSet<>();
	private final AESemaphore				async_read_sem = new AESemaphore("DMReader:asyncReads");

	private final List<Object[]>			suspended_requests	= new ArrayList<>();
	
	private boolean					started;
	private boolean					stopped;
	private int						suspended;
	
	private long					total_read_ops;
	private long					total_read_bytes;

	private volatile long			latency;
	
	private final AEMonitor	this_mon	= new AEMonitor( "DMReader" );

	public
	DMReaderImpl(
		DiskManagerHelper		_disk_manager )
	{
		disk_manager	= _disk_manager;

		disk_access		= disk_manager.getDiskAccessController();
	}

	@Override
	public void
	start()
	{
		try{
			this_mon.enter();

			if ( started ){

				throw( new RuntimeException("can't start twice" ));
			}

			if ( stopped ){

				throw( new RuntimeException("already been stopped" ));
			}

			started	= true;

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	stop()
	{
		int	read_wait;

		List<Object[]>		suspended = null;

		try{
			this_mon.enter();

			if ( stopped || !started ){

				return;
			}

			stopped	= true;

			read_wait	= async_reads;

			if ( !suspended_requests.isEmpty()){
				
				suspended = new ArrayList<>( suspended_requests );
				
				suspended_requests.clear();
			}
		}finally{

			this_mon.exit();
		}

		if ( suspended != null ){
			
			for ( Object[] susp: suspended ){
				
				DiskManagerReadRequest			request 	= (DiskManagerReadRequest)susp[0];
				DiskManagerReadRequestListener	listener 	= (DiskManagerReadRequestListener)susp[1];

				listener.readFailed( request, new Exception( "Disk Reader stopped" ));
			}
		}
		
		long	log_time 		= SystemTime.getCurrentTime();

		for (int i=0;i<read_wait;i++){

			long	now = SystemTime.getCurrentTime();

			if ( now < log_time ){

				log_time = now;

			}else{

				if ( now - log_time > 1000 ){

					log_time	= now;

					if ( Logger.isEnabled()){

						Logger.log(new LogEvent(disk_manager, LOGID, "Waiting for reads to complete - " + (read_wait-i) + " remaining" ));
					}
				}
			}

			async_read_sem.reserve();
		}
	}

	@Override
	public void 
	setSuspended(
		boolean 	b )
	{
		boolean				wait_for_requests = false;
		
		List<Object[]>		to_run = null;
		
		try{
			this_mon.enter();
		
			if ( b ){
				
				suspended++;
				
				if ( suspended == 1 ){
					
					wait_for_requests = async_reads > 0;
				}
			}else{
				
				suspended--;
				
				if ( suspended == 0 ){
					
					to_run = new ArrayList<>( suspended_requests );
					
					suspended_requests.clear();
				}
			}
		}finally{

			this_mon.exit();
		}

		while( wait_for_requests ){
			
			try{
				Thread.sleep(100);
				
			}catch( Throwable e ){
				
			}
			
			try{
				this_mon.enter();
			
				if ( stopped ){
					
					break;
				}
				
				wait_for_requests = async_reads > 0;
				
			}finally{

				this_mon.exit();
			}
		}
		
		if ( to_run != null ){
			
			for ( Object[] susp: to_run ){
								
				DiskManagerReadRequest			request 	= (DiskManagerReadRequest)susp[0];
				DiskManagerReadRequestListener	listener 	= (DiskManagerReadRequestListener)susp[1];
				
				Object[]						request_wrapper = (Object[])susp[2];
				DiskManagerReadRequestListener	l				= (DiskManagerReadRequestListener)susp[3];
				List<Object[]>					chunks			= (List<Object[]>)susp[4];
						
				DirectByteBuffer buffer = DirectByteBufferPool.getBuffer( DirectByteBuffer.AL_DM_READ, request.getLength());

				if ( buffer == null ) { // Fix for bug #804874

					Debug.out("DiskManager::readBlock:: ByteBufferPool returned null buffer");

					listener.readFailed( request, new Exception( "Out of memory" ));
					
					continue;
				}
				
				try{
					this_mon.enter();

					if ( stopped ){

						buffer.returnToPool();

						listener.readFailed( request, new Exception( "Disk reader has been stopped" ));

						continue;
					}
					
					if ( suspended > 0 ){
						
						suspended_requests.add( new Object[]{ request_wrapper, request, l, chunks });
						
						buffer.returnToPool();
						
						continue;
					}

					async_reads++;

					read_requests.add( request_wrapper );

				}finally{

					this_mon.exit();
				}

				new requestDispatcher( request, l, buffer, chunks );
			}
		}
	}
	
	@Override
	public DiskManagerReadRequest
	createReadRequest(
		int pieceNumber,
		int offset,
		int length )
	{
		return( new DiskManagerReadRequestImpl( pieceNumber, offset, length ));
	}

	@Override
	public boolean
	hasOutstandingReadRequestForPiece(
		int		piece_number )
	{
		try{
			this_mon.enter();

			Iterator<Object[]>	it = read_requests.iterator();

			while( it.hasNext()){

				DiskManagerReadRequest	request = (DiskManagerReadRequest)(it.next())[0];

				if ( request.getPieceNumber() == piece_number ){

					return( true );
				}
			}

			return( false );

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public long[]
	getStats()
	{
		return( new long[]{ total_read_ops, total_read_bytes });
	}

	@Override
	public long
	getLatency()
	{
		long	result = latency;
		
		long now = SystemTime.getMonotonousTime();
		
		for ( DiskAccessRequest req: active_requests.keySet()){
			
			result = Math.max( result, now - req.getCreateMonoTime());
		}
		
		return( result );
	}
	
		// returns null if the read can't be performed

	@Override
	public DirectByteBuffer
	readBlock(
		int pieceNumber,
		int offset,
		int length )
	{
		DiskManagerReadRequest	request = createReadRequest( pieceNumber, offset, length );

		final AESemaphore	sem = new AESemaphore( "DMReader:readBlock" );

		final DirectByteBuffer[]	result = {null};

		readBlock(
				request,
				new DiskManagerReadRequestListener()
				{
					  @Override
					  public void
					  readCompleted(
					  		DiskManagerReadRequest 	request,
							DirectByteBuffer 		data )
					  {
						  result[0]	= data;

						  sem.release();
					  }

					  @Override
					  public void
					  readFailed(
					  		DiskManagerReadRequest 	request,
							Throwable		 		cause )
					  {
						  sem.release();
					  }

					  @Override
					  public int
					  getPriority()
					  {
						  return( -1 );
					  }

					  @Override
					  public void
					  requestExecuted(long bytes)
					  {
					  }
				});

		sem.reserve();

		return( result[0] );
	}

	@Override
	public void
	readBlock(
		final DiskManagerReadRequest			request,
		final DiskManagerReadRequestListener	_listener )
	{
		request.requestStarts();

		final DiskManagerReadRequestListener	listener =
			new DiskManagerReadRequestListener()
			{
				@Override
				public void
				readCompleted(
						DiskManagerReadRequest 	request,
						DirectByteBuffer 		data )
				{
					request.requestEnds( true );

					_listener.readCompleted( request, data );
				}

				@Override
				public void
				readFailed(
						DiskManagerReadRequest 	request,
						Throwable		 		cause )
				{
					request.requestEnds( false );

					_listener.readFailed( request, cause );
				}

				@Override
				public int
				getPriority()
				{
					return( _listener.getPriority());
				}
				@Override
				public void
				requestExecuted(long bytes)
				{
					_listener.requestExecuted( bytes );
				}
			};

		DirectByteBuffer buffer	= null;

		try{
			int	length		= request.getLength();

			buffer = DirectByteBufferPool.getBuffer( DirectByteBuffer.AL_DM_READ,length );

			if ( buffer == null ) { // Fix for bug #804874

				Debug.out("DiskManager::readBlock:: ByteBufferPool returned null buffer");

				listener.readFailed( request, new Exception( "Out of memory" ));

				return;
			}

			int	pieceNumber	= request.getPieceNumber();
			int	offset		= request.getOffset();

			DMPieceList pieceList = disk_manager.getPieceList(pieceNumber);

				// temporary fix for bug 784306

			if ( pieceList.size() == 0 ){

				Debug.out("no pieceList entries for " + pieceNumber);

				listener.readCompleted( request, buffer );

				return;
			}

			long previousFilesLength = 0;

			int currentFile = 0;

			long fileOffset = pieceList.get(0).getOffset();

			while (currentFile < pieceList.size() && pieceList.getCumulativeLengthToPiece(currentFile) < offset) {

				previousFilesLength = pieceList.getCumulativeLengthToPiece(currentFile);

				currentFile++;

				fileOffset = 0;
			}

				// update the offset (we're in the middle of a file)

			fileOffset += offset - previousFilesLength;

			List<Object[]>	chunks = new ArrayList<>();

			int	buffer_position = 0;

			while ( buffer_position < length && currentFile < pieceList.size()) {

				DMPieceMapEntry map_entry = pieceList.get( currentFile );

				int	length_available = map_entry.getLength() - (int)( fileOffset - map_entry.getOffset());

					//explicitly limit the read size to the proper length, rather than relying on the underlying file being correctly-sized
					//see long DMWriterAndCheckerImpl::checkPiece note

				int entry_read_limit = buffer_position + length_available;

					// now bring down to the required read length if this is shorter than this
					// chunk of data

				entry_read_limit = Math.min( length, entry_read_limit );

					// this chunk denotes a read up to buffer offset "entry_read_limit"

				chunks.add( new Object[]{ ((DiskManagerFileInfoImpl)map_entry.getFile()).getCacheFile(), new Long(fileOffset), new Integer( entry_read_limit )});

				buffer_position = entry_read_limit;

				currentFile++;

				fileOffset = 0;
			}

			if ( chunks.size() == 0 ){

				Debug.out("no chunk reads for " + pieceNumber);

				listener.readCompleted( request, buffer );

				return;
			}

				// this is where we go async and need to start counting requests for the sake
				// of shutting down tidily

				// have to wrap the request as we can validly have >1 for same piece/offset/length and
				// the request type itself overrides object equiv based on this...

			final Object[] request_wrapper = { request };

			DiskManagerReadRequestListener	l =
				new DiskManagerReadRequestListener()
				{
					@Override
					public void
					readCompleted(
							DiskManagerReadRequest 	request,
							DirectByteBuffer 		data )
					{
						complete();

						listener.readCompleted( request, data );
					}

					@Override
					public void
					readFailed(
							DiskManagerReadRequest 	request,
							Throwable		 		cause )
					{
						complete();

						listener.readFailed( request, cause );
					}

					@Override
					public int
					getPriority()
					{
						return( _listener.getPriority());
					}

					@Override
					public void
					requestExecuted(long bytes)
					{
						_listener.requestExecuted( bytes );
					}

					protected void
					complete()
					{
						try{
							this_mon.enter();

							async_reads--;

							if ( !read_requests.remove( request_wrapper )){

								Debug.out( "request not found" );
							}

							if ( stopped ){

								async_read_sem.release();
							}
						}finally{

							this_mon.exit();
						}
					}
				};

			try{
				this_mon.enter();

				if ( stopped ){

					buffer.returnToPool();

					listener.readFailed( request, new Exception( "Disk reader has been stopped" ));

					return;
				}
				
				if ( suspended > 0 ){
					
					suspended_requests.add( new Object[]{ request, listener, request_wrapper, l, chunks });
					
					buffer.returnToPool();
					
					return;
				}

				async_reads++;

				read_requests.add( request_wrapper );

			}finally{

				this_mon.exit();
			}

			new requestDispatcher( request, l, buffer, chunks );

		}catch( Throwable e ){

			if ( buffer != null ){

				buffer.returnToPool();
			}

			if ( request.getErrorIsFatal()){
				
				disk_manager.setFailed( DiskManager.ET_READ_ERROR, "Disk read error", e, false );
			}
			
			Debug.printStackTrace( e );

			listener.readFailed( request, e );
		}
	}

	protected class
	requestDispatcher
		implements DiskAccessRequestListener
	{
		private final DiskManagerReadRequest		dm_request;
		final DiskManagerReadRequestListener		listener;
		private final DirectByteBuffer				buffer;
		private final List<Object[]>				chunks;

		private final int	buffer_length;

		private int	chunk_index;
		private int	chunk_limit;

		protected
		requestDispatcher(
			DiskManagerReadRequest			_request,
			DiskManagerReadRequestListener	_listener,
			DirectByteBuffer				_buffer,
			List<Object[]>					_chunks )
		{
			dm_request	= _request;
			listener	= _listener;
			buffer		= _buffer;
			chunks		= _chunks;

			/*
			String	str = "Read: " + dm_request.getPieceNumber()+"/"+dm_request.getOffset()+"/"+dm_request.getLength()+":";

			for (int i=0;i<chunks.size();i++){

				Object[]	entry = (Object[])chunks.get(i);

				String	str2 = entry[0] + "/" + entry[1] +"/" + entry[2];

				str += (i==0?"":",") + str2;
			}

			System.out.println( str );
			*/

			buffer_length = buffer.limit( DirectByteBuffer.SS_DR );

			dispatch();
		}

		protected void
		dispatch()
		{
			final DiskAccessRequest[] error_request = { null };
			
			try{
				if ( chunk_index == chunks.size()){

					buffer.limit( DirectByteBuffer.SS_DR, buffer_length );

					buffer.position(  DirectByteBuffer.SS_DR, 0 );

					listener.readCompleted( dm_request, buffer );

				}else{

					if ( chunk_index == 1 && chunks.size() > 32 ){

							// for large numbers of chunks drop the recursion approach and
							// do it linearly (but on the async thread)

						for (int i=1;i<chunks.size();i++){

							final AESemaphore	sem 	= new AESemaphore( "DMR:dispatch:asyncReq" );
							final Throwable[]	error	= {null};

							doRequest(
								new DiskAccessRequestListener()
								{
									@Override
									public void 
									requestQueued(
										DiskAccessRequest request)
									{
										active_requests.put( request, "" );
									}
									
									@Override
									public void
									requestComplete(
										DiskAccessRequest	request )
									{
										latency = SystemTime.getMonotonousTime() - request.getCreateMonoTime();
										
										active_requests.remove( request );
										
										sem.release();
									}

									@Override
									public void
									requestCancelled(
										DiskAccessRequest	request )
									{
										active_requests.remove( request );
										
										Debug.out( "shouldn't get here" );
									}

									@Override
									public void
									requestFailed(
										DiskAccessRequest	request,
										Throwable			cause )
									{
										active_requests.remove( request );
										
										error_request[0]	= request;
										
										error[0]	= cause;

										sem.release();
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
										return( null );
									}
									
									@Override
									public void
									requestExecuted(long bytes)
									{
										if ( bytes > 0 ){

											total_read_bytes 	+= bytes;
											total_read_ops		++;
										}

										listener.requestExecuted( bytes );
									}
								});

							sem.reserve();

							if ( error[0] != null ){

								throw( error[0] );
							}
						}

						buffer.limit( DirectByteBuffer.SS_DR, buffer_length );

						buffer.position(  DirectByteBuffer.SS_DR, 0 );

						listener.readCompleted( dm_request, buffer );
					}else{

						doRequest( this );
					}
				}
			}catch( Throwable e ){

				failed( error_request[0], e );
			}
		}

		protected void
		doRequest(
			DiskAccessRequestListener	l )
		{
			Object[]	stuff = (Object[])chunks.get( chunk_index++ );

			if ( chunk_index > 0 ){

				buffer.position( DirectByteBuffer.SS_DR, chunk_limit );
			}

			chunk_limit = ((Integer)stuff[2]).intValue();

			buffer.limit( DirectByteBuffer.SS_DR, chunk_limit );

			short	cache_policy = dm_request.getUseCache()?CacheFile.CP_READ_CACHE:CacheFile.CP_NONE;

			if ( dm_request.getFlush()){

				cache_policy |= CacheFile.CP_FLUSH;
			}

			disk_access.queueReadRequest(
					(CacheFile)stuff[0],
					((Long)stuff[1]).longValue(),
					buffer,
					cache_policy,
					l );
		}

		@Override
		public void 
		requestQueued(
			DiskAccessRequest request)
		{
			active_requests.put( request, "" );
		}

		@Override
		public void
		requestComplete(
			DiskAccessRequest	request )
		{
			latency = SystemTime.getMonotonousTime() - request.getCreateMonoTime();

			active_requests.remove( request );
			
			dispatch();
		}

		@Override
		public void
		requestCancelled(
			DiskAccessRequest	request )
		{
			active_requests.remove( request );
			
				// we never cancel so nothing to do here

			Debug.out( "shouldn't get here" );
		}

		@Override
		public void
		requestFailed(
			DiskAccessRequest	request,
			Throwable			cause )
		{
			active_requests.remove( request );
			
			failed( request, cause );
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
			return( null );
		}
		
		@Override
		public void
		requestExecuted(long bytes)
		{
			if ( bytes > 0 ){

				total_read_bytes 	+= bytes;
				total_read_ops		++;
			}

			listener.requestExecuted( bytes );
		}

		protected void
		failed(
			DiskAccessRequest	request,
			Throwable			cause )
		{
			buffer.returnToPool();

			int	error = DiskManager.ET_READ_ERROR;
			
			if ( request != null ){
				
				if ( !request.getFile().exists()){
					
					error = DiskManager.ET_FILE_MISSING;
				}
			}
			
			if ( dm_request.getErrorIsFatal()){
			
				disk_manager.setFailed( error, "Disk read error", cause, true );
			}
			
			Debug.printStackTrace( cause );

			listener.readFailed( dm_request, cause );
		}
	}
}
