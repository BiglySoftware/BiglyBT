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

package com.biglybt.core.disk.impl.resume;

import java.util.*;
import java.util.function.Consumer;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.disk.*;
import com.biglybt.core.disk.impl.DiskManagerFileInfoImpl;
import com.biglybt.core.disk.impl.DiskManagerImpl;
import com.biglybt.core.disk.impl.DiskManagerRecheckScheduler.DiskManagerRecheckInstance;
import com.biglybt.core.disk.impl.access.DMChecker;
import com.biglybt.core.disk.impl.piecemapper.DMPieceList;
import com.biglybt.core.disk.impl.piecemapper.DMPieceMapEntry;
import com.biglybt.core.diskmanager.cache.CacheFileManagerException;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.AESemaphore;
import com.biglybt.core.util.BEncoder;
import com.biglybt.core.util.ByteArrayHashMap;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DirectByteBuffer;

/**
 * @author parg
 *
 */
public class
RDResumeHandler
{
	private static final LogIDs LOGID = LogIDs.DISK;

	private static final boolean TEST_RECHECK_FAILURE_HANDLING	= false;

	static{
		if ( TEST_RECHECK_FAILURE_HANDLING ){
			Debug.out( "**** test recheck failure enabled ****" );
		}
	}

	public static final byte		PIECE_NOT_DONE			= 0;
	public static final byte		PIECE_DONE				= 1;
	public static final byte		PIECE_RECHECK_REQUIRED	= 2;
	public static final byte		PIECE_STARTED			= 3;

	// static boolean	use_fast_resume = true;	// made this permanent, setting it false really borks things
	static boolean	use_fast_resume_recheck_all;
	static boolean	skip_comp_dl_file_checks;
	static boolean	skip_incomp_dl_file_checks;

	static{

		COConfigurationManager.addAndFireParameterListeners(
			new String[]{
				"On Resume Recheck All",
				ConfigKeys.File.BCFG_SKIP_COMP_DL_FILE_CHECKS,
				ConfigKeys.File.BCFG_SKIP_INCOMP_DL_FILE_CHECKS
			},
			new ParameterListener() {
	    	    @Override
		        public void
				parameterChanged(
					String  str )
	    	    {
	    	    	use_fast_resume_recheck_all	= COConfigurationManager.getBooleanParameter("On Resume Recheck All");
	    	    	skip_comp_dl_file_checks	= COConfigurationManager.getBooleanParameter(ConfigKeys.File.BCFG_SKIP_COMP_DL_FILE_CHECKS);
	    	    	skip_incomp_dl_file_checks	= COConfigurationManager.getBooleanParameter(ConfigKeys.File.BCFG_SKIP_INCOMP_DL_FILE_CHECKS);
	    	    }
	    	 });
	}

	final DiskManagerImpl		disk_manager;
	final DMChecker			checker;

	private volatile boolean	started;
	private volatile boolean	stopped;
	private volatile boolean	stopped_for_close;

	private volatile boolean	check_in_progress;
	private volatile boolean	check_resume_was_valid;
	private volatile boolean	check_is_full_check;
	private volatile boolean	check_interrupted;
	private volatile boolean	check_cancelled;
	private volatile int		check_position;


	public
	RDResumeHandler(
		DiskManagerImpl		_disk_manager,
		DMChecker			_writer_and_checker )
	{
		disk_manager		= _disk_manager;
		checker				= _writer_and_checker;
	}

	public void
	start()
	{
		if ( started ){

			Debug.out( "RDResumeHandler: reuse not supported" );
		}

		started	= true;
	}

	public void
	stop(
		boolean	closing )
	{
		stopped_for_close	= stopped_for_close | closing;	// can get in here > once during close

		stopped				= true;
	}

	public void
	checkAllPieces(
		boolean 			newfiles,
		boolean				forceRecheck,
		ProgressListener	listener )
	{
		//long	start = System.currentTimeMillis();

		DiskManagerRecheckInstance	recheck_inst_maybe_null = null;
			 
        final List<DiskManagerCheckRequest>	failed_pieces = new ArrayList<>();

        Consumer<Throwable> forceRecheckErrorReporter =
        	new Consumer<Throwable>(){
        		private boolean reported;
        		
				@Override
				public void 
				accept(
					Throwable cause)
				{
					synchronized( this ){
						
						if ( reported ){
							
							return;
						}
						
						reported = true;
					}
					
					String msg = disk_manager.getDisplayName() + ": One or more piece checks failed: " + Debug.getNestedExceptionMessage( cause );
					
            		Logger.log(new LogAlert( disk_manager, LogAlert.REPEATABLE, LogAlert.AT_ERROR, msg));

				}
        	};
        
        listener.percentDone( 0 );
        
		try{
			boolean	resume_data_complete = false;

			try{
				check_in_progress	= true;

				final AESemaphore	pending_checks_sem 	= new AESemaphore( "RD:PendingChecks" );
				int					pending_check_num	= 0;

				DiskManagerPiece[]	pieces	= disk_manager.getPieces();


					// calculate the current file sizes up front for performance reasons
				
				final Map<DiskManagerFileInfo,Long>	file_sizes;
								
				boolean is_complete = disk_manager.getDownloadManager().isDownloadComplete(false);
				
				if ( 	( skip_comp_dl_file_checks && is_complete ) ||
						( skip_incomp_dl_file_checks && !is_complete )){
					
					file_sizes = null;
					
				}else{
										
					file_sizes = new HashMap<>();
				}

				boolean resumeValid = false;

				byte[] resume_pieces = null;

				Map partialPieces = null;

				Map	resume_data;
				
					// if we have new files then we must ignore resume data is it may be for a file that
					// has been created and is therefore innacurate
				
				if ( newfiles ){
					
					resume_data = null;
					
				}else{
					resume_data = getResumeData();
				}

				if ( resume_data != null ){

					try {

						resume_pieces = (byte[])resume_data.get("resume data");

						if ( resume_pieces != null ){

							if ( resume_pieces.length != pieces.length ){

								Debug.out( "Resume data array length mismatch: " + resume_pieces.length + "/" + pieces.length );

								resume_pieces	= null;
							}
						}

						partialPieces = (Map)resume_data.get("blocks");

						resumeValid = ((Long)resume_data.get("valid")).intValue() == 1;

							// if the torrent download is complete we don't need to invalidate the
							// resume data

						if ( isTorrentResumeDataComplete( pieces.length, resume_data )){

							resume_data_complete	= true;

						}else{

								// set it so that if we crash the NOT_DONE pieces will be
								// rechecked

							resume_data = BEncoder.cloneMap( resume_data );	// copy it as we are updating it

							resume_data.put("valid", new Long(0));

							saveResumeData( resume_data );
						}

					}catch(Exception ignore){

						// ignore.printStackTrace();
					}
				}

				if ( resume_pieces == null ){

					check_is_full_check	= true;

					resumeValid	= false;

					resume_pieces	= new byte[pieces.length];

					Arrays.fill( resume_pieces, PIECE_RECHECK_REQUIRED );
				}

				check_resume_was_valid = resumeValid;

				boolean	recheck_all	= use_fast_resume_recheck_all;

				if ( !recheck_all ){

						// override if not much left undone

					long	total_not_done = 0;

					int	piece_size = disk_manager.getPieceLength();

					for (int i = 0; i < pieces.length; i++){

						if ( resume_pieces[i] != PIECE_DONE ){

							total_not_done	+= piece_size;
						}
					}

					if ( total_not_done < 64*1024*1024 ){

						recheck_all	= true;
					}
				}

				if (Logger.isEnabled()){

					int	total_not_done	= 0;
					int	total_done		= 0;
					int total_started	= 0;
					int	total_recheck	= 0;

					for (int i = 0; i < pieces.length; i++){

						byte	piece_state = resume_pieces[i];

						if ( piece_state == PIECE_NOT_DONE ){
							total_not_done++;
						}else if ( piece_state == PIECE_DONE ){
							total_done++;
						}else if ( piece_state == PIECE_STARTED ){
							total_started++;
						}else{
							total_recheck++;
						}
					}

					String	str = "valid=" + resumeValid + ",not done=" + total_not_done + ",done=" + total_done +
									",started=" + total_started + ",recheck=" + total_recheck + ",rc all=" + recheck_all +
									",full=" + check_is_full_check;

					Logger.log(new LogEvent(disk_manager, LOGID, str ));
				}

				for (int i = 0; i < pieces.length; i++){

					if ( stopped ){
						
						check_interrupted = true;
						
						break;
					}
					
					check_position	= i;

					DiskManagerPiece	dm_piece	= pieces[i];

					listener.percentDone(((i + 1) * 1000) / disk_manager.getNbPieces() );

					boolean pieceCannotExist = false;

					byte	piece_state = resume_pieces[i];

						// valid resume data means that the resume array correctly represents
						// the state of pieces on disk, be they done or not

					if ( piece_state == PIECE_DONE || !resumeValid || recheck_all ){

							// at least check that file sizes are OK for this piece to be valid

						if ( file_sizes != null ){

							DMPieceList list = disk_manager.getPieceList(i);

							for (int j=0;j<list.size();j++){

								DMPieceMapEntry	entry = list.get(j);
								
								DiskManagerFileInfo file = entry.getFile();
								
								Long file_size = file_sizes.get( file );
								
								if ( file_size == null ){
								
									try{
										file_size = new Long(((DiskManagerFileInfoImpl)file).getCacheFile().getLength());
																					
									}catch( CacheFileManagerException e ){
										
										Debug.printStackTrace(e);
										
										file_size = -1L;
									}
									
									file_sizes.put( file, file_size );
								}

								if ( file_size == -1 ){

									piece_state	= PIECE_NOT_DONE;
									pieceCannotExist = true;

									if (Logger.isEnabled())
										Logger.log(new LogEvent(disk_manager, LOGID,
												LogEvent.LT_WARNING, "Piece #" + i
														+ ": file is missing, " + "fails re-check."));

									break;
								}
							
								long	expected_size 	= entry.getOffset() + entry.getLength();

								if ( file_size.longValue() < expected_size ){

									piece_state	= PIECE_NOT_DONE;
									pieceCannotExist = true;

									if ( file_size > 0 ){
											// we get 0 for DND files, don't bother logging
										
										if (Logger.isEnabled())
											Logger.log(new LogEvent(disk_manager, LOGID,
													LogEvent.LT_WARNING, "Piece #" + i
															+ ": file is too small, fails re-check. File size = "
															+ file_size + ", piece needs " + expected_size));
									}
									
									break;
								}
							}
						}
					}

					if ( piece_state == PIECE_DONE ){

						dm_piece.setDone( true );

					}else if ( piece_state == PIECE_NOT_DONE && !recheck_all ){

							// if the piece isn't done and we haven't been asked to recheck all pieces
							// on restart (only started pieces) then just set as not done

					}else{

							// We only need to recheck pieces that are marked as not-ok
							// if the resume data is invalid or explicit recheck needed
						
						if (pieceCannotExist){
							
							dm_piece.setDone( false );
							
						} else if ( piece_state == PIECE_RECHECK_REQUIRED || !resumeValid ){
							

							if ( recheck_inst_maybe_null == null ){
								
								recheck_inst_maybe_null = disk_manager.getRecheckScheduler().register( disk_manager, false );
							}
							
							final DiskManagerRecheckInstance recheck_inst = recheck_inst_maybe_null;
							
							recheck_inst.reserveSlot();

							while( !stopped ){

								if ( recheck_inst.getPermission()){

									break;
								}
							}

							if ( stopped ){

								check_interrupted = true;

								break;

							}else{

								try{
									DiskManagerCheckRequest	request = disk_manager.createCheckRequest( i, null );

									request.setLowPriority( true );

									if ( forceRecheck ){
									
										request.setErrorIsFatal( false );
									}
									
									checker.enqueueCheckRequest(
										request,
										new DiskManagerCheckRequestListener()
										{
											@Override
											public void
											checkCompleted(
												DiskManagerCheckRequest 	request,
												boolean						passed )
											{
												if ( TEST_RECHECK_FAILURE_HANDLING && (int)(Math.random()*10) == 0 ){

													disk_manager.getPiece(request.getPieceNumber()).setDone(false);

													passed  = false;
												}

												if ( !passed ){

													synchronized( failed_pieces ){

														failed_pieces.add( request );
													}
												}

												complete();
											}

											@Override
											public void
											checkCancelled(
												DiskManagerCheckRequest		request )
											{
												complete();
											}

											@Override
											public void
											checkFailed(
												DiskManagerCheckRequest 	request,
												Throwable		 			cause )
											{
												if ( forceRecheck ){
													
													forceRecheckErrorReporter.accept( cause );
												}
												
												complete();
											}

											protected void
											complete()
											{
												recheck_inst.releaseSlot();

												pending_checks_sem.release();
											}
											
											@Override
											public boolean 
											isFailureInteresting()
											{
												return( false );
											}
										});

									pending_check_num++;

								}catch( Throwable e ){

									Debug.printStackTrace(e);
								}
							}
						}
					}
				}

				while( pending_check_num > 0 ){

					pending_checks_sem.reserve();

					pending_check_num--;
				}

				if ( partialPieces != null ){

					Iterator iter = partialPieces.entrySet().iterator();

					while (iter.hasNext()) {

						Map.Entry key = (Map.Entry)iter.next();

						int pieceNumber = Integer.parseInt((String)key.getKey());

						DiskManagerPiece	dm_piece = pieces[ pieceNumber ];

						if ( !dm_piece.isDone()){

							List blocks = (List)partialPieces.get(key.getKey());

							Iterator iterBlock = blocks.iterator();

							while (iterBlock.hasNext()) {

								dm_piece.setWritten(((Long)iterBlock.next()).intValue());
							}
						}
					}
				}

				if ( failed_pieces.size() > 0 && !TEST_RECHECK_FAILURE_HANDLING ){

					byte[][] piece_hashes = disk_manager.getTorrent().getPieces();

					ByteArrayHashMap<Integer>	hash_map = new ByteArrayHashMap<>();

					for ( int i=0;i<piece_hashes.length;i++){

						byte[] hash = piece_hashes[i];
						
						if ( hash != null ){
						
							hash_map.put( hash, i );
						}
					}
					
					if ( recheck_inst_maybe_null == null ){
						
						recheck_inst_maybe_null = disk_manager.getRecheckScheduler().register( disk_manager, false );
					}

					for ( DiskManagerCheckRequest request: failed_pieces ){

						while( ! stopped ){

							if ( recheck_inst_maybe_null.getPermission()){

								break;
							}
						}

						if ( stopped ){

							check_interrupted = true;

							break;
						}

						byte[] hash = request.getHash();

						if ( hash != null ){

							final Integer target_index = hash_map.get( hash );

							int		current_index 	= request.getPieceNumber();

							int		piece_size		= disk_manager.getPieceLength( current_index );

							if ( 	target_index != null &&
									target_index != current_index &&
									disk_manager.getPieceLength( target_index ) == piece_size &&
									!disk_manager.isDone( target_index )){

								final AESemaphore sem = new AESemaphore( "PieceReorder" );

								disk_manager.enqueueReadRequest(
									disk_manager.createReadRequest( current_index, 0, piece_size ),
									new DiskManagerReadRequestListener()
									{
										@Override
										public void
										readCompleted(
											DiskManagerReadRequest 	request,
											DirectByteBuffer 		data )
										{
											try{
												disk_manager.enqueueWriteRequest(
													disk_manager.createWriteRequest( target_index, 0, data, null ),
													new DiskManagerWriteRequestListener()
													{
														@Override
														public void
														writeCompleted(
															DiskManagerWriteRequest 	request )
														{
															try{
																DiskManagerCheckRequest	check_request = disk_manager.createCheckRequest( target_index, null );

																check_request.setLowPriority( true );

																checker.enqueueCheckRequest(
																		check_request,
																		new DiskManagerCheckRequestListener()
																		{
																			@Override
																			public void
																			checkCompleted(
																				DiskManagerCheckRequest 	request,
																				boolean						passed )
																			{
																				sem.release();
																			}

																			@Override
																			public void
																			checkCancelled(
																				DiskManagerCheckRequest		request )
																			{
																				sem.release();
																			}

																			@Override
																			public void
																			checkFailed(
																				DiskManagerCheckRequest 	request,
																				Throwable		 			cause )
																			{
																				sem.release();
																			}
																			
																			@Override
																			public boolean 
																			isFailureInteresting()
																			{
																				return( false );
																			}
																		});
															}catch( Throwable e ){

																sem.release();
															}
														}

														@Override
														public void
														writeFailed(
															DiskManagerWriteRequest 	request,
															Throwable		 			cause )
														{
															sem.release();
														}
													});
											}catch( Throwable e ){

												sem.release();
											}
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
										requestExecuted(
											long 	bytes )
										{
										}
									});

								sem.reserve();
							}
						}
					}
				}
			}finally{

				check_in_progress	= false;
			}

				//dump the newly built resume data to the disk/torrent

			if ( !( stopped || resume_data_complete )){

				try{
					saveResumeData( true );

				}catch( Exception e ){

					Debug.out( "Failed to dump initial resume data to disk" );

					Debug.printStackTrace( e );
				}
			}
		}catch( Throwable e ){

				// if something went wrong then log and continue.

			Debug.printStackTrace(e);

		}finally{

			if ( recheck_inst_maybe_null != null ){
			
				check_cancelled = recheck_inst_maybe_null.isCancelled();
				
				recheck_inst_maybe_null.unregister();
			}

			// System.out.println( "Check of '" + disk_manager.getDownloadManager().getDisplayName() + "' completed in " + (System.currentTimeMillis() - start));
		}
	}

	public void
	saveResumeData(
		boolean interim_save ) 	// data is marked as "invalid" if this is true to enable checking on pieces on crash restart

		throws Exception
	{
		if ( check_in_progress && interim_save ){

				// while we are rechecking it is important that an interim save doesn't come
				// along and overwite the persisted resume data. This is because should we crash
				// while rechecking we need the persisted state to be unchanged so that on
				// restart the rechecking occurs again

				// a non-interim save means that the user has decided to stop the  download (or some
				// other such significant event) so we just persist the current state

			return;
		}

			// if file caching is enabled then this is an important time to ensure that the cache is
			// flushed as we are going to record details about the accuracy of written data.
			// First build the resume map from the data (as updates can still be goin on)
			// Then, flush the cache. This means that on a successful flush the built resume
			// data matches at least the valid state of the data
			// Then update the torrent

		DiskManagerFileInfo[]	files = disk_manager.getFiles();

		/*
		if ( !use_fast_resume ){

				// flush cache even if resume disable as this is a good point to ensure that data
				// is persisted anyway

			for (int i=0;i<files.length;i++){

				files[i].flushCache();
			}

			return;
		}
		*/
		
		boolean	was_complete = isTorrentResumeDataComplete( disk_manager.getDownloadManager().getDownloadState());

		if ( was_complete && check_interrupted ){
		
				// piece completion state not usable, stick with the previous state of affairs
			
			return;
		}
		
		DiskManagerPiece[] pieces	= disk_manager.getPieces();

			//build the piece byte[]

		byte[] resume_pieces = new byte[pieces.length];

		for (int i = 0; i < resume_pieces.length; i++) {

			DiskManagerPiece piece = pieces[i];

				// if we are terminating due to az closure and this has interrupted a recheck then
				// make sure that the recheck continues appropriately on restart

			if ( stopped_for_close && check_interrupted && check_is_full_check && i >= check_position ){

				resume_pieces[i] = PIECE_RECHECK_REQUIRED;

			}else if ( piece.isDone()){

				resume_pieces[i] = PIECE_DONE;

		  	}else if ( piece.getNbWritten() > 0 ){

		  		resume_pieces[i] = PIECE_STARTED;

		  	}else{

				resume_pieces[i] = PIECE_NOT_DONE;
		  	}
		}

		Map	resume_data = new HashMap();

		resume_data.put( "resume data", resume_pieces );

		Map partialPieces = new HashMap();

		for (int i = 0; i < pieces.length; i++) {

			DiskManagerPiece piece = pieces[i];

				// save the partial pieces for any pieces that have not yet been completed
				// and are in-progress (i.e. have at least one block downloaded)

			boolean[] written = piece.getWritten();

			if (( !piece.isDone()) && piece.getNbWritten() > 0 && written != null ){

				boolean	all_written = true;

				for (int j = 0; j < written.length; j++) {

					if ( !written[j] ){

						all_written = false;

						break;
					}
				}

				if ( all_written ){

						// just mark the entire piece for recheck as we've stopped the torrent at the
						// point where a check-piece was, or was about to be, scheduled

					resume_pieces[ i ] = PIECE_RECHECK_REQUIRED;

				}else{

					List blocks = new ArrayList();

					for (int j = 0; j < written.length; j++) {

						if (written[j]){

							blocks.add(new Long(j));
						}
					}

					partialPieces.put("" + i, blocks);
				}
			}
		}

		resume_data.put("blocks", partialPieces);

		long lValid;

		if ( check_interrupted ){

				// set validity to what it was before the check started

			lValid = check_resume_was_valid?1:0;

		}else if ( interim_save ){

				// set invalid so that not-done pieces get rechecked on startup

			lValid = 0;

		}else{

			lValid = 1;
		}

		resume_data.put("valid", new Long(lValid));

		for (int i=0;i<files.length;i++){

			files[i].flushCache();
		}

	  		// OK, we've got valid resume data and flushed the cache

		boolean	is_complete = isTorrentResumeDataComplete( pieces.length, resume_data );

		if ( was_complete && is_complete ){

	  		// no change, no point in writing

		}else{

			saveResumeData( resume_data );
		}
	}

	private Map
	getResumeData()
	{
		return( getResumeData( disk_manager.getDownloadManager()));
	}

	private void
	saveResumeData(
		Map		resume_data )
	{
		saveResumeData( disk_manager.getDownloadManager().getDownloadState(), resume_data );
	}
	
	public boolean
	isCancelled()
	{
		return( check_cancelled );
	}
	
		// STATIC METHODS
	
	protected static Map
	getResumeData(
		DownloadManager		download_manager)
	{
		return( getResumeData( download_manager.getDownloadState()));
	}

	protected static Map
	getResumeData(
		DownloadManagerState	download_manager_state )
	{
		Map resume_map = download_manager_state.getResumeData();

		if ( resume_map != null ){

			Map	resume_data = (Map)resume_map.get( "data" );

			return( resume_data );

		}else{

			return( null );
		}
	}

	private static void
	saveResumeData(
		DownloadManagerState		download_manager_state,
		Map							resume_data )
	{
		Map	resume_map = new HashMap();

		resume_map.put( "data", resume_data );

		download_manager_state.setResumeData( resume_map );
	}


	public static void
	setTorrentResumeDataComplete(
		DownloadManagerState	download_manager_state )
	{
		TOTorrent	torrent = download_manager_state.getTorrent();

		int	piece_count = torrent.getNumberOfPieces();

		byte[] resume_pieces = new byte[piece_count];

		Arrays.fill( resume_pieces, PIECE_DONE );

		Map resume_data = new HashMap();

		resume_data.put( "resume data", resume_pieces );

		Map partialPieces = new HashMap();

		resume_data.put("blocks", partialPieces );

		resume_data.put("valid", new Long(1));

		saveResumeData( download_manager_state, resume_data );
	}

	private static int
	clearResumeDataSupport(
		DownloadManager			download_manager,
		DiskManagerFileInfo		file,
		boolean					recheck,
		boolean					onlyClearUnsharedFirstLast )
	{
		DownloadManagerState	download_manager_state = download_manager.getDownloadState();

		Map resume_data = getResumeData( download_manager );

		if ( resume_data == null ){

			return(0);
		}

		resume_data = BEncoder.cloneMap( resume_data );	// copy it as we are updating it
		
		int	pieces_cleared	= 0;

		// clear any affected pieces

		byte[]	resume_pieces = (byte[])resume_data.get("resume data");

		int firstPiece = file.getFirstPieceNumber();
		int lastPiece = file.getLastPieceNumber();

		if ( onlyClearUnsharedFirstLast ){
			DiskManagerFileInfo[] files = download_manager.getDiskManagerFileInfo();
			boolean firstPieceShared = false;
			boolean lastPieceShared = false;

			int firstFile = findFirstFileWithPieceN(firstPiece, files);

			for(int i = firstFile;i<files.length;i++)
			{
				DiskManagerFileInfo currentFile = files[i];
				if(currentFile.getLastPieceNumber() < firstPiece)
					continue;
				if(currentFile.getIndex() == file.getIndex())
					continue;
				if(currentFile.getFirstPieceNumber() > lastPiece)
					break;
				if(currentFile.getFirstPieceNumber() <= firstPiece && firstPiece <= currentFile.getLastPieceNumber())
					firstPieceShared |= !currentFile.isSkipped();
				if(currentFile.getFirstPieceNumber() <= lastPiece && lastPiece <= currentFile.getLastPieceNumber())
					lastPieceShared |= !currentFile.isSkipped();
			}

			if(firstPieceShared)
				firstPiece++;

			if(lastPieceShared)
				lastPiece--;
		}

		if ( resume_pieces != null ){

			for (int i=firstPiece;i<=lastPiece;i++){

				if ( i >= resume_pieces.length ){

					break;
				}

				if ( resume_pieces[i] == PIECE_DONE ){

					pieces_cleared++;
				}

				resume_pieces[i] = recheck?PIECE_RECHECK_REQUIRED:PIECE_NOT_DONE;
			}
		}
			// clear any affected partial pieces

		Map	partial_pieces = (Map)resume_data.get("blocks");

		if ( partial_pieces != null ){

			Iterator iter = partial_pieces.keySet().iterator();

			while (iter.hasNext()) {

				int piece_number = Integer.parseInt((String)iter.next());

				if ( piece_number >= firstPiece && piece_number <= lastPiece ){

					iter.remove();
				}
			}
		}

			// either way we're valid as
			//    1) clear -> pieces are set as not done
			//	  2) recheck -> pieces are set as "recheck" and will be checked on restart

		resume_data.put( "valid", new Long(1));

		saveResumeData( download_manager_state, resume_data );

		return( pieces_cleared );
	}

	/**
	 * finds the first affected file via binary search, this is necessary as some methods might be
	 * invoked for all files, which would result in O(n²) if we'd scan the whole file array every
	 * time
	 */
	private static int findFirstFileWithPieceN(int firstPiece, DiskManagerFileInfo[] files)
	{
		int start = 0;
		int end = files.length-1;
		int pivot = 0;

		while (start <= end) {
		    pivot = (start + end) >>> 1;
		    int midVal = files[pivot].getLastPieceNumber();

		    if (midVal < firstPiece)
		    	start = pivot + 1;
		    else if (midVal > firstPiece)
		    	end = pivot - 1;
		    else {
		    	// some matching file, now slide leftwards to find the first one, shouldn't be that many
		    	while(pivot > 0 && files[pivot-1].getLastPieceNumber() == firstPiece)
		    		pivot--;
		    	break;
		    }
		}

		return pivot;
	}

	/**
	 * @deprecated Kept for xmwebui 
	 */
	public static boolean
	fileMustExist(
		DownloadManager 			download_manager,
		DiskManagerFileInfo 		file)
	{
		return fileMustExist(download_manager, download_manager.getDiskManagerFileInfoSet(), file);
	}

	public static boolean 
	fileMustExist(
		DownloadManager 			download_manager,
		DiskManagerFileInfoSet		fileSet,
		DiskManagerFileInfo 		file) 
	{

		Map resumeData = getResumeData( download_manager );

		byte[]	resumePieces = resumeData != null ? (byte[])resumeData.get("resume data") : null;

		boolean sharesAnyNeededPieces = false;

		DiskManagerFileInfo[] files = fileSet.getFiles();
		int firstPiece = file.getFirstPieceNumber();
		int lastPiece = file.getLastPieceNumber();

		int firstFile = findFirstFileWithPieceN(firstPiece, files);

		// we must sweep over the files, as any number of files could share the first/last piece of the file we're probing
		for (int i = firstFile; i < files.length && !sharesAnyNeededPieces; i++)
		{
			DiskManagerFileInfo currentFile = files[i];
			if(currentFile.getLastPieceNumber() < firstPiece)
				continue;
			if (currentFile.getIndex() == file.getIndex() && resumePieces != null && file.getStorageType() != DiskManagerFileInfo.ST_COMPACT && file.getStorageType() != DiskManagerFileInfo.ST_REORDER_COMPACT)
				for (int j = firstPiece; j <= lastPiece && !sharesAnyNeededPieces; j++)
					sharesAnyNeededPieces |= resumePieces[j] != PIECE_NOT_DONE;
			if (currentFile.getFirstPieceNumber() > lastPiece)
				break;
			if (currentFile.getFirstPieceNumber() <= firstPiece && firstPiece <= currentFile.getLastPieceNumber())
				sharesAnyNeededPieces |= !currentFile.isSkipped();
			if (currentFile.getFirstPieceNumber() <= lastPiece && lastPiece <= currentFile.getLastPieceNumber())
				sharesAnyNeededPieces |= !currentFile.isSkipped();
		}

		return sharesAnyNeededPieces;
	}

	public static int
	storageTypeChanged(
		DownloadManager			download_manager,
		DiskManagerFileInfo		file )
	{
		return( clearResumeDataSupport(  download_manager, file, false, true ));
	}

	public static void
	clearResumeData(
		DownloadManager			download_manager,
		DiskManagerFileInfo		file )
	{
		clearResumeDataSupport( download_manager, file, false, false );
	}

	public static void
	recheckFile(
		DownloadManager			download_manager,
		DiskManagerFileInfo		file )
	{
		clearResumeDataSupport( download_manager, file, true, false );
	}

	public static void
	setTorrentResumeTotallyIncomplete(
		DownloadManagerState	download_manager_state )
	{
		TOTorrent	torrent = download_manager_state.getTorrent();
		
		long	piece_count = torrent.getNumberOfPieces();

		byte[] resume_pieces = new byte[(int)piece_count];

		Arrays.fill( resume_pieces, PIECE_NOT_DONE );
		
		Map resumeMap = new HashMap();

		resumeMap.put( "resume data", resume_pieces);
		
		resumeMap.put("valid", new Long(1));

		saveResumeData(download_manager_state,resumeMap);
	}

	
	public static void
	setTorrentResumeDataNearlyComplete(
		DownloadManagerState	download_manager_state )
	{
			// backwards compatability, resume data key is the dir

		TOTorrent	torrent = download_manager_state.getTorrent();

		long	piece_count = torrent.getNumberOfPieces();

		byte[] resume_pieces = new byte[(int)piece_count];

		Arrays.fill( resume_pieces, PIECE_DONE );

			// randomly clear some pieces

		for (int i=0;i<3;i++){

			int	piece_num = (int)(Math.random()*piece_count);

			resume_pieces[piece_num]= PIECE_RECHECK_REQUIRED;
		}

		Map resumeMap = new HashMap();

		resumeMap.put( "resume data", resume_pieces);

		Map partialPieces = new HashMap();

		resumeMap.put("blocks", partialPieces);

		resumeMap.put("valid", new Long(0));	// recheck the not-done pieces

		saveResumeData(download_manager_state,resumeMap);
	}

	public static boolean
	isTorrentResumeDataComplete(
		DownloadManagerState			dms )
	{
			// backwards compatability, resume data key is the dir

		Map	resume_data = getResumeData( dms );

		return( isTorrentResumeDataComplete( dms.getTorrent().getNumberOfPieces(), resume_data ));
	}
	
	public static boolean
	isTorrentResumeDataValid(
		DownloadManagerState			dms )
	{
		Map	resume_data = getResumeData( dms );
		
		if ( resume_data != null ){
			
			boolean	valid	= ((Long)resume_data.get("valid")).intValue() == 1;
			
			return( valid );
			
		}else{
			
			return( false );
		}
	}
	
	private static boolean
	isTorrentResumeDataComplete(
		int			piece_count,
		Map			resume_data )
	{
		try{
			if ( resume_data != null ){

				byte[] 	pieces 	= (byte[])resume_data.get("resume data");
				Map		blocks	= (Map)resume_data.get("blocks");
				boolean	valid	= ((Long)resume_data.get("valid")).intValue() == 1;

					// any partial pieced -> not complete

				if ( blocks == null || blocks.size() > 0 ){

					return( false );
				}

				if ( valid && pieces != null && pieces.length == piece_count ){

					for (int i=0;i<pieces.length;i++){

						if ( pieces[i] != PIECE_DONE ){

								// missing piece or recheck outstanding

							return( false );
						}
					}

					return( true );
				}
			}
		}catch( Throwable e ){

			Debug.printStackTrace( e );
		}

		return( false );
	}
	
	public static void
	setupPieces(
		DownloadManagerState		dms,
		DiskManagerPiece[]			pieces )
	{
		Map	resume_data = getResumeData( dms );
		
		if ( resume_data != null ){
			
			boolean	valid	= ((Long)resume_data.get("valid")).intValue() == 1;
	
			if ( valid ){
				
				byte[] 	rd_pieces 	= (byte[])resume_data.get( "resume data" );
				
				if ( rd_pieces != null && rd_pieces.length == pieces.length ){
					
					for ( int i=0;i<pieces.length;i++){
						
						if ( rd_pieces[i] == PIECE_DONE ){
							
							pieces[i].setDone( true );
						}
					}
				}
				
				Map		partialPieces	= (Map)resume_data.get("blocks");
					
				if ( partialPieces != null ){
					
					Iterator iter = partialPieces.entrySet().iterator();
		
					while (iter.hasNext()) {
		
						Map.Entry key = (Map.Entry)iter.next();
		
						int pieceNumber = Integer.parseInt((String)key.getKey());
		
						DiskManagerPiece	dm_piece = pieces[ pieceNumber ];
		
						if ( !dm_piece.isDone()){
		
							List blocks = (List)partialPieces.get(key.getKey());
		
							Iterator iterBlock = blocks.iterator();
		
							while (iterBlock.hasNext()) {
		
								dm_piece.setWritten(((Long)iterBlock.next()).intValue());
							}
						}
					}
				}
			}
		}
	}
	
	public interface
	ProgressListener
	{
		public void
		percentDone(
			int		percent );
	}
}
