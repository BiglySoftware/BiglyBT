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

import com.biglybt.core.disk.*;
import com.biglybt.core.disk.impl.DiskManagerAllocationScheduler;
import com.biglybt.core.disk.impl.DiskManagerFileInfoImpl;
import com.biglybt.core.disk.impl.DiskManagerHelper;
import com.biglybt.core.disk.impl.access.DMWriter;
import com.biglybt.core.disk.impl.piecemapper.DMPieceList;
import com.biglybt.core.disk.impl.piecemapper.DMPieceMapEntry;
import com.biglybt.core.diskmanager.access.DiskAccessController;
import com.biglybt.core.diskmanager.access.DiskAccessRequest;
import com.biglybt.core.diskmanager.access.DiskAccessRequestListener;
import com.biglybt.core.diskmanager.cache.CacheFile;
import com.biglybt.core.diskmanager.cache.CacheFileManagerException;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.*;

/**
 * @author parg
 *
 */

public class
DMWriterImpl
	implements DMWriter
{
	static final LogIDs LOGID = LogIDs.DISK;

	private static final int		MIN_ZERO_BLOCK	= 1*1024*1024;	// must be mult of 1024 (see init below)

	private final DiskManagerHelper		disk_manager;
	private final DiskAccessController	disk_access;

	private final ConcurrentHashMap<DiskAccessRequest,String>		active_requests = new ConcurrentHashMap<>();

	private int						async_writes;
	private long					async_writes_bytes;
	
	private final Set<DiskManagerWriteRequest>	write_requests		= new HashSet<>();
	private final AESemaphore					async_write_sem 	= new AESemaphore("DMWriter::asyncWrite");

	private boolean	started;

	volatile boolean	stopped;

	private final int			pieceLength;

	private boolean	complete_recheck_in_progress;

	final AEMonitor		this_mon	= new AEMonitor( "DMWriter" );

	private long					total_write_ops;
	private long					total_write_bytes;

	private volatile long			latency;
	
	public
	DMWriterImpl(
		DiskManagerHelper	_disk_manager )
	{
		disk_manager	= _disk_manager;
		disk_access		= disk_manager.getDiskAccessController();

		pieceLength		= disk_manager.getPieceLength();
	}

	@Override
	public void
	start()
	{
		try{
			this_mon.enter();

			if ( started ){

				throw( new RuntimeException( "DMWWriter: start while started"));
			}

			if ( stopped ){

				throw( new RuntimeException( "DMWWriter: start after stopped"));
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
		int write_wait;

		try{
			this_mon.enter();

			if ( stopped || !started ){

				return;
			}

			stopped	= true;

			write_wait	= async_writes;

		}finally{

			this_mon.exit();
		}

			// wait for writes

		long	log_time 		= SystemTime.getCurrentTime();

		for (int i=0;i<write_wait;i++){

			long	now = SystemTime.getCurrentTime();

			if ( now < log_time ){

				log_time = now;

			}else{

				if ( now - log_time > 1000 ){

					log_time	= now;

					if ( Logger.isEnabled()){

						Logger.log(new LogEvent(disk_manager, LOGID, "Waiting for writes to complete - " + (write_wait-i) + " remaining" ));
					}
				}
			}

			async_write_sem.reserve();
		}
	}

	public boolean
	isChecking()
	{
	   return( complete_recheck_in_progress );
	}

	@Override
	public boolean
	zeroFile(
		DiskManagerAllocationScheduler.AllocationInstance		allocation_instance,
		DiskManagerFileInfoImpl 								file,
		long													start_from,
		long 													overall_length,
		ProgressListener										listener )
				
		throws DiskManagerException
	{
		CacheFile	cache_file = file.getCacheFile();

		try{
			if ( overall_length == 0 ){ //create a zero-length file if it is listed in the torrent

				cache_file.setLength( 0 );

			}else{
				int	buffer_size = pieceLength < MIN_ZERO_BLOCK?MIN_ZERO_BLOCK:pieceLength;

				buffer_size	= ((buffer_size+1023)/1024)*1024;

		        DirectByteBuffer	buffer = DirectByteBufferPool.getBuffer(DirectByteBuffer.AL_DM_ZERO,buffer_size);

		        long remainder	= overall_length;
				long written 	= 0;

				if ( start_from > 0 ){
					
					remainder 	-= start_from;
					written		+= start_from;
					
					listener.allocated( start_from );
				}
				
		        try{
		        	final byte[]	blanks = new byte[1024];

		        	// RandomUtils.nextBytes( blanks );
		        	
					for (int i = 0; i < buffer_size/1024; i++ ){

						buffer.put(DirectByteBuffer.SS_DW, blanks );
					}

					buffer.position(DirectByteBuffer.SS_DW, 0);

					long	time = SystemTime.getMonotonousTime();
					
					while ( remainder > 0 && !stopped ){

						long	now = SystemTime.getMonotonousTime();

						if ( now - time >= 250 ){
							
							time = now;
							
							while( !stopped ){
	
								if ( allocation_instance.getPermission()){
	
									break;
								}
							}
						
							if ( stopped ){
							
								break;
							}
						}
					       
						int	write_size = buffer_size;

						if ( remainder < write_size ){

							write_size = (int)remainder;

							buffer.limit(DirectByteBuffer.SS_DW, write_size);
						}

						final AESemaphore	sem = new AESemaphore( "DMW&C:zeroFile" );
						final Throwable[]	op_failed = {null};

						disk_access.queueWriteRequest(
								cache_file,
								written,
								buffer,
								false,
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
										
										op_failed[0] = new Throwable( "Request cancelled" );

										sem.release();
									}

									@Override
									public void
									requestFailed(
										DiskAccessRequest	request,
										Throwable			cause )
									{
										active_requests.remove( request );
										
										op_failed[0]	= cause;

										sem.release();
									}

									@Override
									public int
									getPriority()
									{
										return( -1 );
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
									}
								});

						sem.reserve();

						if ( op_failed[0] != null ){

							throw( op_failed[0] );
						}

						buffer.position(DirectByteBuffer.SS_DW, 0);

						written 	+= write_size;
						remainder 	-= write_size;

						listener.allocated( write_size );
					}
		        }finally{

		        	buffer.returnToPool();
		        }

		        cache_file.flushCache();
			}

			if ( stopped ){

				return false;
			}
		}catch ( Throwable e){

			Debug.printStackTrace( e );

			throw new DiskManagerException(e);
		}

		return true;
	}



	@Override
	public DiskManagerWriteRequest
	createWriteRequest(
		int 									pieceNumber,
		int 									offset,
		DirectByteBuffer 						buffer,
		Object 									user_data )
	{
		return( new DiskManagerWriteRequestImpl( pieceNumber, offset, buffer, user_data ));
	}

	@Override
	public boolean
	hasOutstandingWriteRequestForPiece(
		int		piece_number )
	{
		try{
			this_mon.enter();

			for ( DiskManagerWriteRequest request: write_requests ){

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
	public void
	writeBlock(
		final DiskManagerWriteRequest			request,
		final DiskManagerWriteRequestListener	_listener )

	{
		request.requestStarts();

		final DiskManagerWriteRequestListener	listener =
			new DiskManagerWriteRequestListener()
			{
				@Override
				public void
				writeCompleted(
					DiskManagerWriteRequest 	request )
				{
					request.requestEnds( true );

					_listener.writeCompleted( request );
				}

				@Override
				public void
				writeFailed(
					DiskManagerWriteRequest 	request,
					Throwable		 			cause )
				{
					request.requestEnds( false );

					_listener.writeFailed( request, cause );
				}
			};

		try{
			int					pieceNumber	= request.getPieceNumber();
			DirectByteBuffer	buffer		= request.getBuffer();
			int					offset		= request.getOffset();

				//Do not allow to write in a piece marked as done. we can get here if

			final DiskManagerPiece	dmPiece = disk_manager.getPieces()[pieceNumber];

			if ( dmPiece.isDone()){

				// Debug.out( "write: piece already done (" + request.getPieceNumber() + "/" + request.getOffset());

				buffer.returnToPool();

				listener.writeCompleted( request ); //XXX: no writing was done; is this necessary for complete()?

			}else{

				int	buffer_position = buffer.position(DirectByteBuffer.SS_DW);
				int buffer_limit	= buffer.limit(DirectByteBuffer.SS_DW);

				final long	write_length = buffer_limit - buffer_position;

				int previousFilesLength = 0;

				int currentFile = 0;

				DMPieceList pieceList = disk_manager.getPieceList(pieceNumber);

				DMPieceMapEntry current_piece = pieceList.get(currentFile);

				long fileOffset = current_piece.getOffset();

				while ((previousFilesLength + current_piece.getLength()) < offset) {

					previousFilesLength += current_piece.getLength();

					currentFile++;

					fileOffset = 0;

					current_piece = pieceList.get(currentFile);
				}

				List	chunks = new ArrayList();

					// Now current_piece points to the first file that contains data for this block

				while ( buffer_position < buffer_limit ){

					current_piece = pieceList.get(currentFile);

					long file_limit = buffer_position +
										((current_piece.getFile().getLength() - current_piece.getOffset()) -
											(offset - previousFilesLength));

					if ( file_limit > buffer_limit ){

						file_limit	= buffer_limit;
					}

						// could be a zero-length file

					if ( file_limit > buffer_position ){

						long	file_pos = fileOffset + (offset - previousFilesLength);

						chunks.add(
								new Object[]{ current_piece.getFile(),
								new Long( file_pos ),
								new Integer((int)file_limit )});

						buffer_position = (int)file_limit;
					}

					currentFile++;

					fileOffset = 0;

					previousFilesLength = offset;
				}


				DispatcherListener	l =
					new DispatcherListener()
					{
						@Override
						public void
						writeCompleted(
							DiskManagerWriteRequest 	request )
						{
							complete();

							listener.writeCompleted( request );
						}

						@Override
						public void
						writeFailed(
							DiskManagerWriteRequest 	request,
							DiskAccessRequest			da_request,
							Throwable		 			cause )
						{
							complete();

							if ( dmPiece.isDone()){

									// There's a small chance of us ending up writing the same block twice around
									// the time that a file completes and gets toggled to read-only which then
									// fails with a non-writeable-channel exception

								// Debug.out( "writeFailed: piece already done (" + request.getPieceNumber() + "/" + request.getOffset() + "/" + write_length );

								if ( Logger.isEnabled()){

									Logger.log(new LogEvent(disk_manager, LOGID, "Piece " + dmPiece.getPieceNumber() + " write failed but already marked as done" ));
								}

								listener.writeCompleted( request );

							}else{

								int	error = DiskManager.ET_WRITE_ERROR;
								
								if ( da_request != null ){
									
									if ( !da_request.getFile().exists()){
										
										error = DiskManager.ET_FILE_MISSING;
									}
								}
								
								disk_manager.setFailed( error, "Disk write error", cause );

								Debug.printStackTrace( cause );

								listener.writeFailed( request, cause );
							}
						}

						protected void
						complete()
						{
							try{
								this_mon.enter();

								async_writes--;

								async_writes_bytes -= write_length;
								
								if ( !write_requests.remove( request )){

									Debug.out( "request not found" );
								}
								
								total_write_ops++;
								total_write_bytes	+= write_length;

								if ( stopped ){

									async_write_sem.release();
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

						listener.writeFailed( request, new Exception( "Disk writer has been stopped" ));

						return;

					}else{

						async_writes++;

						async_writes_bytes += write_length;
						
						write_requests.add( request );
					}

				}finally{

					this_mon.exit();
				}

				new requestDispatcher( request, l, buffer, chunks );
			}
		}catch( Throwable e ){

			request.getBuffer().returnToPool();

			disk_manager.setFailed( DiskManager.ET_WRITE_ERROR, "Disk write error", e );

			Debug.printStackTrace( e );

			listener.writeFailed( request, e );
		}
	}

	@Override
	public long[]
	getStats()
	{
		return( new long[]{ total_write_ops, total_write_bytes, async_writes, async_writes_bytes });
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
	
	protected class
	requestDispatcher
		implements DiskAccessRequestListener
	{
		private final DiskManagerWriteRequest		request;
		private final DispatcherListener			listener;
		private final DirectByteBuffer				buffer;
		private final List							chunks;

		private int	chunk_index;

		protected
		requestDispatcher(
			DiskManagerWriteRequest			_request,
			DispatcherListener				_listener,
			DirectByteBuffer				_buffer,
			List							_chunks )
		{
			request		= _request;
			listener	= _listener;
			buffer		= _buffer;
			chunks		= _chunks;

			/*
			String	str = "Write: " + request.getPieceNumber() + "/" + request.getOffset() + ":";

			for (int i=0;i<chunks.size();i++){

				Object[]	entry = (Object[])chunks.get(i);

				String	str2 = entry[0] + "/" + entry[1] +"/" + entry[2];

				str += (i==0?"":",") + str2;
			}

			System.out.println( str );
			*/

			dispatch();
		}

		protected void
		dispatch()
		{
			DiskAccessRequest[]	error_request = { null };
			
			try{
				if ( chunk_index == chunks.size()){

					listener.writeCompleted( request );

				}else{

					if ( chunk_index == 1 && chunks.size() > 32 ){

							// for large numbers of chunks drop the recursion approach and
							// do it linearly (but on the async thread)

						for (int i=1;i<chunks.size();i++){

							final AESemaphore	sem 	= new AESemaphore( "DMW&C:dispatch:asyncReq" );
							final Throwable[]	error	= {null};

							doRequest(
								new DiskAccessRequestListener()
								{
									@Override
									public void 
									requestQueued(
										DiskAccessRequest request)
									{
										// don't need to track this, done in doRequest
									}	

									@Override
									public void
									requestComplete(
										DiskAccessRequest	request )
									{
										sem.release();
									}

									@Override
									public void
									requestCancelled(
										DiskAccessRequest	request )
									{
										Debug.out( "shouldn't get here" );
									}

									@Override
									public void
									requestFailed(
										DiskAccessRequest	request,
										Throwable			cause )
									{
										error_request[0]	= request;
										
										error[0]	= cause;

										sem.release();
									}

									@Override
									public int
									getPriority()
									{
										return( -1 );
									}

									@Override
									public Object 
									getUserData()
									{
										return( request.getUserData());
									}
									
									@Override
									public void
									requestExecuted(long bytes)
									{
									}
								});

							sem.reserve();

							if ( error[0] != null ){

								throw( error[0] );
							}
						}

						listener.writeCompleted( request );

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
			final DiskAccessRequestListener	l )

			throws CacheFileManagerException
		{
			Object[]	stuff = (Object[])chunks.get( chunk_index++ );

			final DiskManagerFileInfoImpl	file = (DiskManagerFileInfoImpl)stuff[0];

			buffer.limit( DirectByteBuffer.SS_DR, ((Integer)stuff[2]).intValue());

			if ( file.getAccessMode() == DiskManagerFileInfo.READ ){

				if (Logger.isEnabled())
					Logger.log(new LogEvent(disk_manager, LOGID, "Changing "
							+ file.getFile(true).getName()
							+ " to read/write"));

				file.setAccessMode( DiskManagerFileInfo.WRITE );
			}

			boolean	handover_buffer	= chunk_index == chunks.size();

			DiskAccessRequestListener	delegate_listener =
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
						
						l.requestComplete( request );

						file.dataWritten( request.getOffset(), request.getSize(), request.getUserData());
					}

					@Override
					public void
					requestCancelled(
						DiskAccessRequest	request )
					{
						active_requests.remove( request );
						
						l.requestCancelled( request );
					}

					@Override
					public void
					requestFailed(
						DiskAccessRequest	request,
						Throwable			cause )
					{
						active_requests.remove( request );
						
						l.requestFailed( request, cause );
					}

					@Override
					public int
					getPriority()
					{
						return( -1 );
					}

					@Override
					public Object 
					getUserData()
					{
						return( request.getUserData());
					}
					
					@Override
					public void
					requestExecuted(long bytes)
					{
					}
				};

			disk_access.queueWriteRequest(
				file.getCacheFile(),
				((Long)stuff[1]).longValue(),
				buffer,
				handover_buffer,
				delegate_listener );
		}

		@Override
		public void 
		requestQueued(
			DiskAccessRequest request)
		{
			// don't need to track latency here as done in doRequest
		}	

		@Override
		public void
		requestComplete(
			DiskAccessRequest	request )
		{
			dispatch();
		}

		@Override
		public void
		requestCancelled(
			DiskAccessRequest	request )
		{
				// we never cancel so nothing to do here

			Debug.out( "shouldn't get here" );
		}

		@Override
		public void
		requestFailed(
			DiskAccessRequest	request,
			Throwable			cause )
		{
			failed( request, cause );
		}


		@Override
		public int
		getPriority()
		{
			return( -1 );
		}

		@Override
		public Object 
		getUserData()
		{
			return( request.getUserData());
		}
		
		@Override
		public void
		requestExecuted(long bytes)
		{
		}

		protected void
		failed(
			DiskAccessRequest	da_request,
			Throwable			cause )
		{
			buffer.returnToPool();

			listener.writeFailed( request, da_request, cause );
		}
	}
	
	interface
	DispatcherListener
	{
		public void
		writeCompleted(
			DiskManagerWriteRequest 	request );

		public void
		writeFailed(
			DiskManagerWriteRequest 	request,
			DiskAccessRequest			da_request,
			Throwable		 			cause );
	}
}
