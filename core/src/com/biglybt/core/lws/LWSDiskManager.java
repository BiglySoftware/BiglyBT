/*
 * Created on Jul 16, 2008
 * Created by Paul Gardner
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


package com.biglybt.core.lws;

import java.io.File;

import com.biglybt.core.disk.*;
import com.biglybt.core.disk.DiskManager.DownloadEndedProgress;
import com.biglybt.core.disk.impl.*;
import com.biglybt.core.disk.impl.access.DMAccessFactory;
import com.biglybt.core.disk.impl.access.DMChecker;
import com.biglybt.core.disk.impl.access.DMReader;
import com.biglybt.core.disk.impl.piecemapper.*;
import com.biglybt.core.diskmanager.access.DiskAccessController;
import com.biglybt.core.diskmanager.cache.CacheFile;
import com.biglybt.core.diskmanager.cache.CacheFileOwner;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.internat.LocaleTorrentUtil;
import com.biglybt.core.internat.LocaleUtilDecoder;
import com.biglybt.core.peermanager.piecepicker.util.BitFlags;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentException;
import com.biglybt.core.util.ByteFormatter;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DirectByteBuffer;
import com.biglybt.core.util.IndentWriter;


public class
LWSDiskManager
	implements DiskManagerHelper
{
	private static final sePiece	piece = new sePiece();

	
	private final LightWeightSeed			lws;
	private final DiskAccessController	disk_access_controller;
	private final File					save_file;
	private DMReader 				reader;
	private DMChecker 				checker_use_accessor;
	private DMPieceMapper			piece_mapper;
	private DMPieceMap				piece_map_use_accessor;

	private final sePiece[]					pieces;
	private final BitFlags					availability;

	private DiskManagerFileInfoImpl[]	files;
	private String						internal_name;
	private final DownloadManagerState		download_state;

	private boolean	started;
	private int 	state 			= DiskManager.INITIALIZING;
	private String	error_message	= "";
	private int		error_type		= ET_NONE;

	protected
	LWSDiskManager(
		LightWeightSeed				_lws,
		File						_save_file )
	{
		lws						= _lws;
		save_file				= _save_file;

		disk_access_controller	= DiskManagerImpl.getDefaultDiskAccessController();

		download_state	= new LWSDiskManagerState();

		TOTorrent	torrent = lws.getTOTorrent( false );

		pieces = new sePiece[ torrent.getNumberOfPieces() ];

		for (int i=0;i<pieces.length;i++){

			pieces[i] = piece;
		}
		
		availability = new BitFlags( pieces.length );
		
		availability.setAll();
	}

	public String
	getName()
	{
		return( lws.getName());
	}

	@Override
	public String 
	getDisplayName()
	{
		return( getName());
	}

	@Override
	public DownloadManager
	getDownload()
	{
		return( null );
	}
	
	@Override
	public int
	getCacheMode()
	{
		return( CacheFileOwner.CACHE_MODE_NORMAL );
	}

	@Override
	public long[]
	getReadStats()
	{
		if ( reader == null ){

			return( new long[]{ 0, 0 });
		}

		return( reader.getStats());
	}

	@Override
	public long[]
	getWriteStats()
	{
		return( new long[]{ 0, 0, 0, 0 } );
	}
	
	@Override
	public void
	start()
	{
		try{
			TOTorrent	torrent = lws.getTOTorrent( false );

			internal_name = ByteFormatter.nicePrint(torrent.getHash(),true);

			LocaleUtilDecoder	locale_decoder = LocaleTorrentUtil.getTorrentEncoding( torrent );

			piece_mapper = DMPieceMapperFactory.create( torrent );

			piece_mapper.construct( locale_decoder, save_file.getName());

			files = getFileInfo( piece_mapper.getFiles(), save_file );

			reader = DMAccessFactory.createReader( this );

			reader.start();

			if ( state != DiskManager.FAULTY ){

				started = true;

				state = DiskManager.READY;
			}
		}catch( Throwable e ){

			setFailed( DiskManager.ET_OTHER, "Start failed", e, false );
		}
	}

	protected DiskManagerFileInfoImpl[]
	getFileInfo(
		DMPieceMapperFile[]		pm_files,
		File					save_location )
	{
		boolean	ok = false;

		DiskManagerFileInfoImpl[]	local_files = new DiskManagerFileInfoImpl[pm_files.length];


		try{
			TOTorrent	torrent = lws.getTOTorrent( false );

			if ( torrent.isSimpleTorrent()){

				save_location = save_location.getParentFile();
			}

			for (int i = 0; i < pm_files.length; i++) {

				DMPieceMapperFile pm_info = pm_files[i];

				long target_length = pm_info.getLength();

				DiskManagerFileInfoImpl	file_info =
					new DiskManagerFileInfoImpl(
											this,
											save_location,
											pm_info.getRelativeDataPath(),
											i,
											pm_info.getTorrentFile(),
											DiskManagerFileInfo.ST_LINEAR );

				local_files[i] = file_info;

				CacheFile	cache_file	= file_info.getCacheFile();
				File		data_file	= file_info.getFile(true);

				if ( !cache_file.exists()){

					throw( new Exception( "File '" + data_file + "' doesn't exist" ));
				}

				if ( cache_file.getLength() != target_length ){

					throw( new Exception( "File '" + data_file + "' doesn't exist" ));

				}

				pm_info.setFileInfo( file_info );
			}

			ok	= true;

			return( local_files );

		}catch( Throwable e ){

			setFailed( DiskManager.ET_READ_ERROR, "getFiles failed", e, false );

			return( null );

		}finally{

			if ( !ok ){

				for (int i=0;i<local_files.length;i++){

					if ( local_files[i] != null ){

						local_files[i].close();
					}
				}
			}
		}
	}

	@Override
	public void
	setPieceDone(
		DiskManagerPieceImpl    dmPiece,
		boolean                 done )
	{
	}

	@Override
	public boolean
	stop(
		boolean	closing )
	{
		started = false;

		if ( reader != null ){

			reader.stop();

			reader = null;
		}

		if ( files != null ){

			for (int i=0;i<files.length;i++){

				try{
					files[i].getCacheFile().close();

				}catch( Throwable e ){

					e.printStackTrace();
				}
			}
		}

		return( false );
	}

    @Override
    public boolean
    isStopped()
    {
    	return( !started );
    }

	@Override
	public boolean
	filesExist()
	{
		throw( new RuntimeException( "filesExist not implemented" ));
	}


	@Override
	public DiskManagerWriteRequest
	createWriteRequest(
		int 				pieceNumber,
		int 				offset,
		DirectByteBuffer 	data,
		Object 				user_data )
	{
		throw( new RuntimeException( "createWriteRequest not implemented" ));
	}

	@Override
	public void
	enqueueWriteRequest(
		DiskManagerWriteRequest			request,
		DiskManagerWriteRequestListener	listener )
	{
		throw( new RuntimeException( "enqueueWriteRequest not implemented" ));
	}

	@Override
	public boolean
	hasOutstandingWriteRequestForPiece(
		int		piece_number )
	{
		throw( new RuntimeException( "hasOutstandingWriteRequestForPiece not implemented" ));
	}

	@Override
	public boolean
	hasOutstandingReadRequestForPiece(
		int		piece_number )
	{
		throw( new RuntimeException( "hasOutstandingReadRequestForPiece not implemented" ));
	}

	@Override
	public boolean
	hasOutstandingCheckRequestForPiece(
		int		piece_number )
	{
		throw( new RuntimeException( "hasOutstandingCheckRequestForPiece not implemented" ));
	}

	@Override
	public DirectByteBuffer
	readBlock(
		int pieceNumber,
		int offset,
		int length )
	{
		return( reader.readBlock( pieceNumber, offset, length ));
	}

	@Override
	public DiskManagerReadRequest
	createReadRequest(
		int pieceNumber,
		int offset,
		int length )
	{
		return( reader.createReadRequest( pieceNumber, offset, length ));
	}

	@Override
	public void
	enqueueReadRequest(
		DiskManagerReadRequest 			request,
		DiskManagerReadRequestListener 	listener )
	{
		reader.readBlock( request, listener );
	}

	@Override
	public DiskManagerCheckRequest
	createCheckRequest(
		int 		pieceNumber,
		Object		user_data )
	{
		DMChecker	checker = getChecker();

		return( checker.createCheckRequest( pieceNumber, user_data));
	}

	@Override
	public void
	enqueueCheckRequest(
		DiskManagerCheckRequest			request,
		DiskManagerCheckRequestListener	listener )
	{
		DMChecker	checker = getChecker();

		checker.enqueueCheckRequest( request, listener );
	}

	@Override
	public void
	enqueueCompleteRecheckRequest(
		DiskManagerCheckRequest			request,
		DiskManagerCheckRequestListener	listener )
	{
		throw( new RuntimeException( "enqueueCompleteRecheckRequest not implemented" ));
	}

	@Override
	public void
	setPieceCheckingEnabled(
		boolean		enabled )
	{
	}

	@Override
	public void
    saveResumeData(
    	boolean interim_save )
	{
	}

	@Override
	public DiskManagerPiece[]
	getPieces()
	{
		return( pieces );
	}

	@Override
	public BitFlags getAvailability()
	{
		return( availability );
	}
	
	@Override
	public DiskManagerPiece
	getPiece(
		int	index )
	{
		return( pieces[index] );
	}

	@Override
	public boolean
	isInteresting(
		int	piece_num )
	{
		return( false );
	}

	@Override
	public boolean
	isDone(
		int	piece_num )
	{
		return( false );
	}

	@Override
	public int
	getNbPieces()
	{
		return( pieces.length );
	}

	@Override
	public DiskManagerFileInfo[]
	getFiles()
	{
		return( files );
	}

	@Override
	public DiskManagerFileInfoSet
	getFileSet()
	{
		throw( new RuntimeException( "getFileSet not implemented" ));
	}

	@Override
	public int
	getState()
	{
		return( state );
	}

	@Override
	public long
	getTotalLength()
	{
		return( piece_mapper.getTotalLength());
	}

	@Override
	public int
	getPieceLength()
	{
		return( piece_mapper.getPieceLength());
	}

	@Override
	public int
	getPieceLength(
		int piece_number)
	{
		if ( piece_number == pieces.length-1 ){

			return( piece_mapper.getLastPieceLength());

		}else{

			return( piece_mapper.getPieceLength());
		}
	}

	public int
	getLastPieceLength()
	{
		return( piece_mapper.getLastPieceLength());
	}

	@Override
	public long
	getRemaining()
	{
		return( 0 );
	}

	@Override
	public long
	getRemainingExcludingDND()
	{
		return( 0 );
	}

	@Override
	public int
	getPercentDone()
	{
		return( 100 );
	}

	@Override
	public int
	getPercentAllocated()
	{
		return( 100 );
	}
	
	@Override
	public int getPercentDoneExcludingDND() {
		// Either this one or getPercentDone is wrong, mebbe
		return 1000;
	}

	@Override
	public long getSizeExcludingDND() {
		return getTotalLength();
	}

	@Override
	public String
	getErrorMessage()
	{
		return( error_message );
	}

	@Override
	public int
	getErrorType()
	{
		return( error_type );
	}

	@Override
	public DownloadEndedProgress
	downloadEnded( boolean start_of_day )
	{
		return( new DownloadEndedProgress(){
			@Override
			public boolean isComplete(){
				return( true );
			}
		});
	}

	@Override
	public void
	moveDataFiles(
		File 				new_parent_dir,
		String 				new_name )
	{
		throw( new RuntimeException( "moveDataFiles not implemented" ));
	}

	@Override
	public int
	getCompleteRecheckStatus()
	{
		return( -1 );
	}

	@Override
	public boolean
	getRecheckCancelled()
	{
		return( false );
	}
	
    @Override
    public long[]
    getMoveProgress()
    {
    	return( null );
    }

    @Override
    public String 
    getMoveSubTask()
    {
    	return( null );
    }
    
    @Override
    public void setMoveState(int state){
    }
    
	@Override
	public boolean
	checkBlockConsistencyForWrite(
		String				originator,
		int 				pieceNumber,
		int 				offset,
		DirectByteBuffer 	data )
	{
		long	pos = pieceNumber * (long)piece_mapper.getPieceLength() + offset + data.remaining( DirectByteBuffer.AL_EXTERNAL );

		return( pos <= piece_mapper.getTotalLength());
	}

	@Override
	public boolean
	checkBlockConsistencyForRead(
		String	originator,
	    boolean	peer_request,
		int 	pieceNumber,
		int		offset,
		int 	length )
	{
		return( DiskManagerUtil.checkBlockConsistencyForRead( this, originator, peer_request, pieceNumber, offset, length));
	}

	@Override
	public boolean
	checkBlockConsistencyForHint(
		String	originator,
		int 	pieceNumber,
		int		offset,
		int 	length )
	{
		return( DiskManagerUtil.checkBlockConsistencyForHint( this, originator, pieceNumber, offset, length));
	}

	@Override
	public void
	addListener(
		DiskManagerListener	l )
	{

	}

	@Override
	public void
	removeListener(
		DiskManagerListener	l )
	{
	}

	@Override
	public boolean
	hasListener(
		DiskManagerListener	l )
	{
		return( false );
	}

	@Override
	public void
	saveState( boolean interim )
	{
	}

	@Override
	public DiskAccessController
	getDiskAccessController()
	{
		return( disk_access_controller );
	}

	@Override
	public DMPieceMap
	getPieceMap()
	{
		DMPieceMap	map = piece_map_use_accessor;

		if ( map == null ){

			piece_map_use_accessor = map = piece_mapper.getPieceMap();
		}

		return( map );
	}

	@Override
	public DMPieceList
	getPieceList(
		int	piece_number )
	{
		DMPieceMap	map = getPieceMap();

		return( map.getPieceList( piece_number ));
	}


	protected DMChecker
	getChecker()
	{
		DMChecker	checker = checker_use_accessor;

		if ( checker == null ){

			checker = checker_use_accessor = DMAccessFactory.createChecker( this );
		}

		return( checker );
	}

	@Override
	public byte[]
	getPieceHash(
		int	piece_number )

		throws TOTorrentException
	{
		return( lws.getTorrent().getPieces()[piece_number] );
	}

	@Override
	public DiskManagerRecheckScheduler
	getRecheckScheduler()
	{
		throw( new RuntimeException( "getPieceHash not implemented" ));
	}

	@Override
	public void
	downloadRemoved()
	{
	}

	@Override
	public void
	setFailed(
		int				type,
		String			reason,
		Throwable		cause,
		boolean			can_continue )
	{
		started = false;

		state	= FAULTY;

		error_message	= reason;
		error_type		= type;
	}

	@Override
	public void
	setFailedAndRecheck(
		DiskManagerFileInfo		file,
		String					reason )
	{
		started = false;

		state	= FAULTY;

		error_message	= reason;
		error_type		= ET_OTHER;
	}

	@Override
	public TOTorrent
	getTorrent()
	{
		return( lws.getTOTorrent( false ));
	}

	@Override
	public String[]
  	getStorageTypes()
	{
		throw( new RuntimeException( "getStorageTypes not implemented" ));
	}

	@Override
	public String
	getStorageType(
		int fileIndex)
	{
		throw( new RuntimeException( "getStorageType not implemented" ));
	}

  	@Override
	public void
    skippedFileSetChanged(
  	   	DiskManagerFileInfo	file )
  	{
  	}

  	@Override
	public void
  	priorityChanged(
  		DiskManagerFileInfo	file )
  	{
  	}

  	@Override
  	public void 
  	storageTypeChanged(
  			DiskManagerFileInfo file)
  	{
  	}
  	
  	@Override
	public File
  	getSaveLocation()
  	{
  		return( save_file );
  	}

	@Override
	public String
	getInternalName()
	{
		return( internal_name );
	}

	@Override
	public DownloadManagerState
	getDownloadState()
	{
		return( download_state );
	}

	@Override
	public long getPriorityChangeMarker()
	{
		return 0;
	}

	@Override
	public void
	generateEvidence(
		IndentWriter		writer )
	{
	}

	protected static class
	sePiece
		implements DiskManagerPiece
	{
		public void			clearChecking(){throw( new RuntimeException( "clearChecking not implemented" ));}
		@Override
		public boolean		isNeedsCheck(){throw( new RuntimeException( "isNeedsCheck not implemented" ));}
		@Override
		public boolean		spansFiles(){throw( new RuntimeException( "spansfiles not implemented" ));}
		@Override
		public DMPieceList	getPieceList(){throw( new RuntimeException( "getPieceList not implemented" ));}
		@Override
		public int			getLength(){throw( new RuntimeException( "getLength not implemented" ));}
		@Override
		public int			getNbBlocks(){throw( new RuntimeException( "getNbBlocks not implemented" ));}
		@Override
		public int			getPieceNumber(){throw( new RuntimeException( "getPieceNumber not implemented" ));}
		@Override
		public int			getBlockSize(int b ){throw( new RuntimeException( "getBlockSize not implemented" ));}
		@Override
		public boolean		isWritten(){throw( new RuntimeException( "isWritten not implemented" ));}
		@Override
		public int			getNbWritten(){throw( new RuntimeException( "getNbWritten not implemented" ));}
		@Override
		public boolean[]	getWritten(){throw( new RuntimeException( "getWritten not implemented" ));}
		@Override
		public void			reDownloadBlock(int blockNumber){throw( new RuntimeException( "reDownloadBlock not implemented" ));}
		@Override
		public void			reset(){throw( new RuntimeException( "reset not implemented" ));}
		@Override
		public boolean		isDownloadable(){ return( false );}
		@Override
		public void			setDownloadable(){throw( new RuntimeException( "setRequestable not implemented" ));}
		@Override
		public DiskManager	getManager(){throw( new RuntimeException( "getManager not implemented" ));}
		@Override
		public boolean		calcNeeded(){throw( new RuntimeException( "calcNeeded not implemented" ));}
		@Override
		public void			clearNeeded(){throw( new RuntimeException( "clearNeeded not implemented" ));}
		@Override
		public boolean		isNeeded(){throw( new RuntimeException( "isNeeded not implemented" ));}
		@Override
		public void			setNeeded(){throw( new RuntimeException( "setNeeded not implemented" ));}
		@Override
		public void			setNeeded(boolean b){throw( new RuntimeException( "setNeeded not implemented" ));}
		@Override
		public void			setWritten(int b){throw( new RuntimeException( "setWritten not implemented" ));}
		@Override
		public void			clearWritten(int b){throw( new RuntimeException( "setWritten not implemented" ));}
		@Override
		public boolean		isWritten(int blockNumber){throw( new RuntimeException( "isWritten not implemented" ));}
		public boolean		calcChecking(){throw( new RuntimeException( "calcChecking not implemented" ));}
		@Override
		public boolean		isChecking(){return( false );}
		@Override
		public void			setChecking(){throw( new RuntimeException( "setChecking not implemented" ));}
		public void			setChecking(boolean b){throw( new RuntimeException( "setChecking not implemented" ));}
		@Override
		public boolean		calcDone(){throw( new RuntimeException( "calcDone not implemented" ));}
		@Override
		public boolean		isDone(){ return( true );}
		@Override
		public boolean isInteresting(){ return( false );}
		@Override
		public boolean		isSkipped(){ return false; }
		@Override
		public String		getString(){ return( "" );}
		@Override
		public short		getReadCount(){ return 0 ;}
		@Override
		public void			setReadCount(short c){}

		@Override
		public boolean isMergeRead(){throw( new RuntimeException( "setChecking not implemented" ));}
		@Override
		public boolean isMergeWrite(){throw( new RuntimeException( "setChecking not implemented" ));}
		@Override
		public void setMergeRead(){throw( new RuntimeException( "setChecking not implemented" ));}
		@Override
		public void setMergeWrite(){throw( new RuntimeException( "setChecking not implemented" ));}
		@Override
		public int getRemaining(){return(0);}
		@Override
		public void
		setDone(
			boolean 	b)
		{
			// get here when doing delayed rechecks

			if ( !b ){

				Debug.out( "Piece failed recheck" );
			}

			//throw( new RuntimeException( "setDone not implemented" ));
		}
	}
}
