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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.disk.*;
import com.biglybt.core.disk.impl.DiskManagerFileInfoImpl;
import com.biglybt.core.disk.impl.DiskManagerHelper;
import com.biglybt.core.disk.impl.DiskManagerRecheckScheduler.DiskManagerRecheckInstance;
import com.biglybt.core.disk.impl.access.DMChecker;
import com.biglybt.core.disk.impl.piecemapper.DMPieceList;
import com.biglybt.core.disk.impl.piecemapper.DMPieceMapEntry;
import com.biglybt.core.diskmanager.cache.CacheFile;
import com.biglybt.core.global.GlobalManagerEvent;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.*;

/**
 * @author parg
 *
 */

public class
DMCheckerImpl
	implements DMChecker
{
	protected static final LogIDs LOGID = LogIDs.DISK;

	static boolean	flush_pieces;
	static boolean	checking_read_priority;

	static final AEMonitor		class_mon	= new AEMonitor( "DMChecker:class" );
	static final List				async_check_queue		= new ArrayList();
	static final AESemaphore		async_check_queue_sem 	= new AESemaphore("DMChecker::asyncCheck");

	private static final boolean	fully_async = COConfigurationManager.getBooleanParameter( "diskmanager.perf.checking.fully.async" );

	static{
		if ( fully_async ){

			new AEThread2( "DMCheckerImpl:asyncCheckScheduler", true )
			{
				@Override
				public void
				run()
				{
					while( true ){

						async_check_queue_sem.reserve();

						Object[]	entry;

						try{
							class_mon.enter();

							entry = (Object[])async_check_queue.remove(0);

							int	queue_size = async_check_queue.size();

							if ( queue_size % 100 == 0 && queue_size > 0 ){

								System.out.println( "async check queue size=" + async_check_queue.size());
							}

						}finally{

							class_mon.exit();
						}

						((DMCheckerImpl)entry[0]).enqueueCheckRequest(
							(DiskManagerCheckRequest)entry[1],
							(DiskManagerCheckRequestListener)entry[2],
							flush_pieces );
					}
				}
			}.start();
		}
	}

    static{

    	 ParameterListener param_listener = new ParameterListener() {
    	    @Override
	        public void
			parameterChanged(
				String  str )
    	    {
    	   	    flush_pieces				= COConfigurationManager.getBooleanParameter( "diskmanager.perf.cache.flushpieces" );
       	   	  	checking_read_priority		= COConfigurationManager.getBooleanParameter( "diskmanager.perf.checking.read.priority" );
     	    }
    	 };

 		COConfigurationManager.addAndFireParameterListeners(
 			new String[]{
 				"diskmanager.perf.cache.flushpieces",
 				"diskmanager.perf.checking.read.priority" },
 				param_listener );
    }

	protected final DiskManagerHelper		disk_manager;

	protected int			async_checks;
	protected final AESemaphore	async_check_sem 	= new AESemaphore("DMChecker::asyncCheck");

	protected int			async_reads;
	protected final AESemaphore	async_read_sem 		= new AESemaphore("DMChecker::asyncRead");

	private boolean	started;

	protected volatile boolean	stopped;

	volatile boolean	complete_recheck_in_progress;
	volatile int		complete_recheck_progress;
	volatile boolean	check_cancelled;
	
	private boolean				checking_enabled		= true;

	protected final AEMonitor	this_mon	= new AEMonitor( "DMChecker" );

	public
	DMCheckerImpl(
		DiskManagerHelper	_disk_manager )
	{
		disk_manager	= _disk_manager;
	}

	@Override
	public void
	start()
	{
		try{
			this_mon.enter();

			if ( started ){

				throw( new RuntimeException( "DMChecker: start while started"));
			}

			if ( stopped ){

				throw( new RuntimeException( "DMChecker: start after stopped"));
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
		int	check_wait;
		int	read_wait;

		try{
			this_mon.enter();

			if ( stopped || !started ){

				return;
			}

				// when we exit here we guarantee that all file usage operations have completed
				// i.e. writes and checks (checks being doubly async)

			stopped	= true;

			read_wait	= async_reads;
			check_wait	= async_checks;

		}finally{

			this_mon.exit();
		}

		long	log_time 		= SystemTime.getCurrentTime();

			// wait for reads

		for (int i=0;i<read_wait;i++){

			long	now = SystemTime.getCurrentTime();

			if ( now < log_time ){

				log_time = now;

			}else{

				if ( now - log_time > 1000 ){

					log_time	= now;

					if ( Logger.isEnabled()){

						Logger.log(new LogEvent(disk_manager, LOGID, "Waiting for check-reads to complete - " + (read_wait-i) + " remaining" ));
					}
				}
			}

			async_read_sem.reserve();
		}

		log_time 		= SystemTime.getCurrentTime();

			// wait for checks

		for (int i=0;i<check_wait;i++){

			long	now = SystemTime.getCurrentTime();

			if ( now < log_time ){

				log_time = now;

			}else{

				if ( now - log_time > 1000 ){

					log_time	= now;

					if ( Logger.isEnabled()){

						Logger.log(new LogEvent(disk_manager, LOGID, "Waiting for checks to complete - " + (read_wait-i) + " remaining" ));
					}
				}
			}

			async_check_sem.reserve();
		}
	}

	@Override
	public int
	getCompleteRecheckStatus()
	{
	   if (complete_recheck_in_progress ){

		   return( complete_recheck_progress );

	   }else{

		   return( -1 );
	   }
	}

	@Override
	public boolean
	getRecheckCancelled()
	{
		return( check_cancelled );
	}
	
	@Override
	public void
	setCheckingEnabled(
		boolean		enabled )
	{
		checking_enabled = enabled;
	}

	@Override
	public DiskManagerCheckRequest
	createCheckRequest(
		int 	pieceNumber,
		Object	user_data )
	{
		return( new DiskManagerCheckRequestImpl( pieceNumber, user_data ));
	}

	@Override
	public void
	enqueueCompleteRecheckRequest(
		final DiskManagerCheckRequest			request,
		final DiskManagerCheckRequestListener 	listener )
	{
		if ( !checking_enabled ){

			listener.checkCompleted( request, true );

			return;
		}

		complete_recheck_progress		= 0;
		complete_recheck_in_progress	= true;

			// register the instance here rather than in the thread so that the operation is guaranteed to 
			// exist when we return so the caller can rely on it
		
		DiskManagerRecheckInstance	recheck_inst = disk_manager.getRecheckScheduler().register( disk_manager, true );

	 	new AEThread2("DMChecker::completeRecheck", true )
			{
		  		@Override
				public void
				run()
		  		{
		  			try{
		  				final AESemaphore	sem = new AESemaphore( "DMChecker::completeRecheck" );

		  				int	checks_submitted	= 0;

			            final AESemaphore	 run_sem = new AESemaphore( "DMChecker::completeRecheck:runsem", 2 );

			            int nbPieces = disk_manager.getNbPieces();

		  				for ( int i=0; i < nbPieces; i++ ){

		  					complete_recheck_progress = 1000*i / nbPieces;

		  					DiskManagerPiece	dm_piece = disk_manager.getPiece(i);

	  							// only recheck the piece if it happens to be done (a complete dnd file that's
	  							// been set back to dnd for example) or the piece is part of a non-dnd file

		  					if ( dm_piece.isDone() || !dm_piece.isSkipped()){

			  					run_sem.reserve();

				  				while( !stopped ){

					  				if ( recheck_inst.getPermission()){

					  					break;
					  				}
					  			}

			  					if ( stopped ){

			  						break;
			  					}

			  					final DiskManagerCheckRequest this_request = createCheckRequest( i, request.getUserData());

			  					enqueueCheckRequest(
			  						this_request,
			  	       				new DiskManagerCheckRequestListener()
									{
					  	       			@Override
								          public void
					  	       			checkCompleted(
					  	       				DiskManagerCheckRequest 	request,
					  	       				boolean						passed )
					  	       			{
					  	       				try{
					  	       					listener.checkCompleted( request, passed );

					  	       				}catch( Throwable e ){

					  	       					Debug.printStackTrace(e);

					  	       				}finally{

					  	       					complete();
					  	       				}
					  	       			}

					  	       			@Override
								          public void
					  	       			checkCancelled(
					  	       				DiskManagerCheckRequest		request )
					  	       			{
					  	       				try{
					  	       					listener.checkCancelled( request );

					  	       				}catch( Throwable e ){

					  	       					Debug.printStackTrace(e);

					  	       				}finally{

					  	       					complete();
					  	       				}
					  	       			}

					  	       			@Override
								          public void
					  	       			checkFailed(
					  	       				DiskManagerCheckRequest 	request,
					  	       				Throwable		 			cause )
					  	       			{
					  	       				try{
					  	       					listener.checkFailed( request, cause );

					  	       				}catch( Throwable e ){

					  	       					Debug.printStackTrace(e);

					  	       				}finally{

					  	       					complete();
					  	       				}			  	       			}

					  	       			@Override
					  	       			public boolean 
					  	       			isFailureInteresting()
					  	       			{	
					  	       				return( listener.isFailureInteresting());
					  	       			}
					  	       			
					  	       			protected void
					  	       			complete()
					  	       			{
			  	       						run_sem.release();

			  	       						sem.release();
				  	       				}
									},
									false );

			  					checks_submitted++;
		  					}
		  				}

		  					// wait for all to complete

		  				for (int i=0;i<checks_submitted;i++){

		  					sem.reserve();
		  				}
		  	       }finally{

		  	       		check_cancelled = recheck_inst.isCancelled();

		  	       		complete_recheck_in_progress	= false;
		  	       		
		  	       		recheck_inst.unregister();
		  	       		
		  	       		if ( !stopped ){
		  	       			
		  	       			disk_manager.getDownload().fireGlobalManagerEvent(
		  	       				GlobalManagerEvent.ET_RECHECK_COMPLETE,
		  	       				new Object[]{ request.isExplicit(), check_cancelled });
		  	       		}
		  	       }
		        }
		 	}.start();
	}

	@Override
	public void
	enqueueCheckRequest(
		DiskManagerCheckRequest				request,
		DiskManagerCheckRequestListener 	listener )
	{
		if ( fully_async ){

				// if the disk controller read-queue is full then normal the read-request allocation
				// will block. This option forces the check request to be scheduled off the caller's
				// thread

			try{
				class_mon.enter();

				async_check_queue.add( new Object[]{ this, request, listener });

				if ( async_check_queue.size() % 100 == 0 ){

					System.out.println( "async check queue size=" + async_check_queue.size());
				}
			}finally{

				class_mon.exit();
			}

			async_check_queue_sem.release();

		}else{

			enqueueCheckRequest( request, listener, flush_pieces );
		}
	}

	@Override
	public boolean
	hasOutstandingCheckRequestForPiece(
		int		piece_number )
	{
		if ( fully_async ){

			try{
				class_mon.enter();

				for (int i=0;i<async_check_queue.size();i++){

					Object[]	entry = (Object[])async_check_queue.get(i);

					if ( entry[0] == this ){

						DiskManagerCheckRequest request = (DiskManagerCheckRequest)entry[1];

						if ( request.getPieceNumber() == piece_number ){

							return( true );
						}
					}
				}
			}finally{

				class_mon.exit();
			}
		}

		return( false );
	}

	protected void
	enqueueCheckRequest(
		final DiskManagerCheckRequest			request,
		final DiskManagerCheckRequestListener 	listener,
		boolean									read_flush )
	{
			// everything comes through here - the interceptor listener maintains the piece state and
			// does logging

		request.requestStarts();

		enqueueCheckRequestSupport(
				request,
				new DiskManagerCheckRequestListener()
				{
					@Override
					public void
					checkCompleted(
						DiskManagerCheckRequest 	request,
						boolean						passed )
					{
						request.requestEnds( true );

						try{
							int	piece_number	= request.getPieceNumber();

							DiskManagerPiece	piece = disk_manager.getPiece(request.getPieceNumber());

							piece.setDone( passed );

							if ( passed ){

								DMPieceList	piece_list = disk_manager.getPieceList( piece_number );

								for (int i = 0; i < piece_list.size(); i++) {

									DMPieceMapEntry piece_entry = piece_list.get(i);

									((DiskManagerFileInfoImpl)piece_entry.getFile()).dataChecked( piece_entry.getOffset(), piece_entry.getLength());
								}
							}
						}finally{

							listener.checkCompleted( request, passed );

							if (Logger.isEnabled()){
								if ( passed ){

									Logger.log(new LogEvent(disk_manager, LOGID, LogEvent.LT_INFORMATION,
												"Piece " + request.getPieceNumber() + " passed hash check."));
								}else{
									Logger.log(
										new LogEvent(
											disk_manager, 
											LOGID, 
											listener.isFailureInteresting()?LogEvent.LT_WARNING:LogEvent.LT_INFORMATION,
											"Piece " + request.getPieceNumber() + " failed hash check."));
								}
							}
						}
					}

					@Override
					public void
					checkCancelled(
						DiskManagerCheckRequest		request )
					{

						request.requestEnds( false );

							// don't explicitly mark a piece as failed if we get a cancellation as the
							// existing state will suffice. Either we're rechecking because it is bad
							// already (in which case it won't be done, or we're doing a recheck-on-complete
							// in which case the state is ok and musn't be flipped to bad

						listener.checkCancelled( request );

						if (Logger.isEnabled()){
							Logger.log(new LogEvent(disk_manager, LOGID, LogEvent.LT_WARNING,
											"Piece " + request.getPieceNumber() + " hash check cancelled."));
						}
					}

					@Override
					public void
					checkFailed(
						DiskManagerCheckRequest 	request,
						Throwable		 			cause )
					{
						request.requestEnds( false );

						try{
							disk_manager.getPiece(request.getPieceNumber()).setDone( false );

						}finally{

							listener.checkFailed( request, cause );

							if (Logger.isEnabled()){
								Logger.log(new LogEvent(disk_manager, LOGID, LogEvent.LT_WARNING,
												"Piece " + request.getPieceNumber() + " failed hash check - " + Debug.getNestedExceptionMessage( cause )));
							}
						}
					}
					
					@Override
					public boolean 
					isFailureInteresting()
					{
						return( listener.isFailureInteresting());
					}
					
					@Override
					public boolean 
					hashRequest(
						int 			piece_number, 
						HashListener 	hash_listener)
					{
						return( listener.hashRequest( piece_number, hash_listener));
					}
				}, read_flush, false );
	}


	protected void
	enqueueCheckRequestSupport(
		final DiskManagerCheckRequest			request,
		final DiskManagerCheckRequestListener	listener,
		boolean									read_flush,
		boolean									hash_requested )
	{
		if ( !checking_enabled ){

			listener.checkCompleted( request, true );

			return;
		}

		final int	pieceNumber	= request.getPieceNumber();

		try{

			final byte[]	required_hash = disk_manager.getPieceHash(pieceNumber);
			
			if ( required_hash == null ){
				
					// v2 torrent and hash not yet available, make an attempt to get it
					// normally it will be available as requested prior to data
				
				if ( !hash_requested ){
				
					if ( listener.hashRequest( 
							pieceNumber,
							new DiskManagerCheckRequestListener.HashListener()
							{
								@Override
								public int 
								getPieceNumber()
								{
									return( pieceNumber );
								}
								
								public void
								complete(
									boolean success )
								{
										// ignore success, just do it again and probably fail
									
									if ( !success ){
										
										Debug.out( "Failed to get hash for piece " + request.getPieceNumber());
										
									}
									enqueueCheckRequestSupport( request, listener, read_flush, true );
								}
							})){
					
						return;
					}
				}				
					
				listener.checkFailed( request, new Exception( "V2 hash for piece " + pieceNumber + " not available" ));
				
				return;
			}
				// quick check that the files that make up this piece are at least big enough
				// to warrant reading the data to check

				// also, if the piece is entirely compact then we can immediately
				// fail as we don't actually have any data for the piece (or can assume we don't)
				// we relax this a bit to catch pieces that are part of compact files with less than
				// three pieces as it is possible that these were once complete and have all their bits
				// living in retained compact areas

			final DMPieceList pieceList = disk_manager.getPieceList(pieceNumber);

			try{
					// there are other comments in the code about the existence of 0 length piece lists
					// just in case these still occur for who knows what reason ensure that a 0 length list
					// causes the code to carry on and do the check (i.e. it is no worse that before this
					// optimisation was added...)

				boolean	all_compact = pieceList.size() > 0;

				for (int i = 0; i < pieceList.size(); i++) {

					DMPieceMapEntry piece_entry = pieceList.get(i);

					DiskManagerFileInfoImpl	file_info = (DiskManagerFileInfoImpl)piece_entry.getFile();

					CacheFile	cache_file = file_info.getCacheFile();

					if ( cache_file.compareLength( piece_entry.getOffset()) < 0 ){

						listener.checkCompleted( request, false );

						return;
					}

					if ( all_compact ){

						int st = cache_file.getStorageType();

						if (( st != CacheFile.CT_COMPACT && st != CacheFile.CT_PIECE_REORDER_COMPACT ) || file_info.getNbPieces() <= 2 ){

							all_compact = false;
						}
					}
				}

				if ( all_compact ){

						// System.out.println( "Piece " + pieceNumber + " is all compact, failing hash check" );

					listener.checkCompleted( request, false );

					return;
				}

			}catch( Throwable e ){

					// we can fail here if the disk manager has been stopped as the cache file length access may be being
					// performed on a "closed" (i.e. un-owned) file

				listener.checkCancelled( request );

				return;
			}

			int this_piece_length = disk_manager.getPieceLength( pieceNumber );

			DiskManagerReadRequest read_request = disk_manager.createReadRequest( pieceNumber, 0, this_piece_length );

		   	try{
		   		this_mon.enter();

				if ( stopped ){

					listener.checkCancelled( request );

					return;
				}

				async_reads++;

		   	}finally{

		   		this_mon.exit();
		   	}

		   	read_request.setFlush( read_flush );

		   	read_request.setUseCache( !request.isAdHoc());

		   	read_request.setErrorIsFatal( request.getErrorIsFatal());
		   	
			disk_manager.enqueueReadRequest(
				read_request,
				new DiskManagerReadRequestListener()
				{
					@Override
					public void
					readCompleted(
						DiskManagerReadRequest 	read_request,
						DirectByteBuffer 		buffer )
					{
						complete();

					   	try{
					   		this_mon.enter();

							if ( stopped ){

								buffer.returnToPool();

								listener.checkCancelled( request );

								return;
							}

							async_checks++;

					   	}finally{

					   		this_mon.exit();
					   	}

					   	if ( buffer.getFlag( DirectByteBuffer.FL_CONTAINS_TRANSIENT_DATA )){

					   		try{
					   			buffer.returnToPool();

					   			listener.checkCompleted( request, false );

					   		}finally{

					   			try{
    								this_mon.enter();

    								async_checks--;

    								if ( stopped ){

    									async_check_sem.release();
    								}
    							}finally{

    								this_mon.exit();
    							}
					   		}
					   	}else{
							try{
						    	final	DirectByteBuffer	f_buffer	= buffer;

						    	int piece_length = disk_manager.getPieceLength();
						    	
						    	int hash_version = required_hash.length==20?1:2;
						    	
						    	long	v2_file_length;
						    	
						    	ByteBuffer byte_buffer = buffer.getBuffer(DirectByteBuffer.SS_DW);
						    	
						    	if ( hash_version == 2 ){
						    		
						    		DMPieceMapEntry piece_entry = pieceList.get(0);
						    		
						    		v2_file_length = piece_entry.getFile().getLength();					    		
						    		
										// with v2 a piece only contains > 1 file if the second file is a dummy padding file added
										// to 'make things work'

						    		if ( pieceList.size() == 2 ){

							    		int v2_piece_length = piece_entry.getLength();
							    		
							    		if ( v2_piece_length < piece_length ){
							    			
							    				// hasher will pad appropriately
							    			
							    			byte_buffer.limit( byte_buffer.position() + v2_piece_length );
							    		}
						    		}
						    	}else{
						    		
						    		v2_file_length = -1;
						    	}
						    	
							   	ConcurrentHasher.getSingleton().addRequest(
						    			byte_buffer,
						    			hash_version,
						    			piece_length,
						    			v2_file_length,
										new ConcurrentHasherRequestListener()
										{
						    				@Override
										    public void
											complete(
												ConcurrentHasherRequest	hash_request )
						    				{
						    					int	async_result	= 3; // cancelled

						    					try{

													byte[] actual_hash = hash_request.getResult();

													if ( actual_hash != null ){

														request.setHash( actual_hash );

					    								async_result = 1; // success

					    								for (int i = 0; i < actual_hash.length; i++){

					    									if ( actual_hash[i] != required_hash[i]){

					    										async_result = 2; // failed;

					    										break;
					    									}
					    								}
													}
						    					}finally{

						    						try{
						    							if ( async_result == 1 ){

						    								try{
						    									for (int i = 0; i < pieceList.size(); i++) {

						    										DMPieceMapEntry piece_entry = pieceList.get(i);

						    										DiskManagerFileInfoImpl	file_info = (DiskManagerFileInfoImpl)piece_entry.getFile();

						    											// edge case here for skipped zero length files that have been deleted

						    										if ( file_info.getLength() > 0 || !file_info.isSkipped()){

						    											CacheFile	cache_file = file_info.getCacheFile();

						    											if ( 	!read_flush && 
						    													file_info.getStorageType() == DiskManagerFileInfoImpl.ST_REORDER ){
						    											
						    													// got to ensure written to disk before setting complete as the re-order
						    													// logic requires this 
						    												
						    												cache_file.flushCache( piece_entry.getOffset(), piece_entry.getLength());
						    											}
						    											
						    											cache_file.setPieceComplete( pieceNumber, f_buffer );
						    										}
						    									}
						    								}catch( Throwable e ){

						    									f_buffer.returnToPool();

						    									Debug.out( e );

						    									listener.checkFailed( request, e );

						    									return;
						    								}
						    							}

							    						f_buffer.returnToPool();

							    						if ( async_result == 1 ){

							    							listener.checkCompleted( request, true );

							    						}else if ( async_result == 2 ){

							    							listener.checkCompleted( request, false );

							    						}else{

							    							listener.checkCancelled( request );
							    						}

						    						}finally{

						    							try{
						    								this_mon.enter();

						    								async_checks--;

						    								if ( stopped ){

						    									async_check_sem.release();
						    								}
						    							}finally{

						    								this_mon.exit();
						    							}
						    						}
						    					}
						    				}

										},
										request.isLowPriority());


							}catch( Throwable e ){

								Debug.printStackTrace(e);

	    						buffer.returnToPool();

	    						listener.checkFailed( request, e );
							}
					   	}
					}

					@Override
					public void
					readFailed(
						DiskManagerReadRequest 	read_request,
						Throwable		 		cause )
					{
						complete();

						listener.checkFailed( request, cause );
					}

					@Override
					public int
					getPriority()
					{
						return( checking_read_priority?0:-1 );
					}

					@Override
					public void
					requestExecuted(long bytes)
					{
					}

					protected void
					complete()
					{
						try{
							this_mon.enter();

							async_reads--;

							if ( stopped ){

								async_read_sem.release();
							}
						}finally{

							this_mon.exit();
						}
					}
				});

		}catch( Throwable e ){

			disk_manager.setFailed( DiskManager.ET_OTHER, "Piece check error", e );

			Debug.printStackTrace( e );

			listener.checkFailed( request, e );
		}
	}
}
