/*
 * This program is free software; you can redistribute it and/or modify
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * Created on Oct 18, 2003
 * Created by Paul Gardner
 * Modified Apr 13, 2004 by Alon Rohter
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 */

package com.biglybt.core.disk.impl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.disk.*;
import com.biglybt.core.disk.impl.access.DMAccessFactory;
import com.biglybt.core.disk.impl.access.DMChecker;
import com.biglybt.core.disk.impl.access.DMReader;
import com.biglybt.core.disk.impl.access.DMWriter;
import com.biglybt.core.disk.impl.piecemapper.*;
import com.biglybt.core.disk.impl.resume.RDResumeHandler;
import com.biglybt.core.diskmanager.access.DiskAccessController;
import com.biglybt.core.diskmanager.access.DiskAccessControllerFactory;
import com.biglybt.core.diskmanager.cache.CacheFile;
import com.biglybt.core.diskmanager.cache.CacheFileManagerException;
import com.biglybt.core.diskmanager.cache.CacheFileManagerFactory;
import com.biglybt.core.diskmanager.cache.CacheFileOwner;
import com.biglybt.core.diskmanager.file.FMFileManager;
import com.biglybt.core.diskmanager.file.FMFileManagerFactory;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerException;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.download.DownloadManagerStats;
import com.biglybt.core.download.impl.DownloadManagerMoveHandler;
import com.biglybt.core.download.impl.DownloadManagerStatsImpl;
import com.biglybt.core.internat.LocaleTorrentUtil;
import com.biglybt.core.internat.LocaleUtilDecoder;
import com.biglybt.core.internat.LocaleUtilEncodingException;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.*;
import com.biglybt.core.peermanager.piecepicker.util.BitFlags;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentException;
import com.biglybt.core.torrent.TOTorrentFile;
import com.biglybt.core.util.*;
import com.biglybt.core.util.FileUtil.ProgressListener;
import com.biglybt.pif.download.savelocation.SaveLocationChange;
import com.biglybt.platform.PlatformManager;
import com.biglybt.platform.PlatformManagerCapabilities;
import com.biglybt.platform.PlatformManagerFactory;


/**
 *
 * The disk Wrapper.
 *
 * @author Tdv_VgA
 * @author MjrTom
 *          2005/Oct/08: new piece-picking support changes
 *          2006/Jan/02: refactoring piece picking related code
 *
 */

public class
DiskManagerImpl
    extends LogRelation
    implements DiskManagerHelper, DiskManagerUtil.MoveTaskAapter
{
	private static final int DM_FREE_PIECELIST_TIMEOUT	= 120*1000;

    private static final LogIDs LOGID = LogIDs.DISK;

    private static final DiskAccessController disk_access_controller;

    static {
        int max_read_threads        = COConfigurationManager.getIntParameter( "diskmanager.perf.read.maxthreads" );
        int max_read_mb             = COConfigurationManager.getIntParameter( "diskmanager.perf.read.maxmb" );
        int max_write_threads       = COConfigurationManager.getIntParameter( "diskmanager.perf.write.maxthreads" );
        int max_write_mb            = COConfigurationManager.getIntParameter( "diskmanager.perf.write.maxmb" );

        disk_access_controller =
            DiskAccessControllerFactory.create(
            		"core",
                    max_read_threads, max_read_mb,
                    max_write_threads, max_write_mb );

        if (Logger.isEnabled()){
            Logger.log(
                    new LogEvent(
                            LOGID,
                            "Disk access controller params: " +
                                max_read_threads + "/" + max_read_mb + "/" + max_write_threads + "/" + max_write_mb ));

        }
    }

    public static DiskAccessController
    getDefaultDiskAccessController()
    {
        return( disk_access_controller );
    }

    static boolean 	reorder_storage_mode;
    static int		reorder_storage_mode_min_mb;

    static{
    	COConfigurationManager.addAndFireParameterListeners(
    		new String[]{
    			"Enable reorder storage mode",
    			"Reorder storage mode min MB" },
    		new ParameterListener()
    		{
    			@Override
			    public void
    			parameterChanged(
    				String parameterName )
    			{
       				reorder_storage_mode 		= COConfigurationManager.getBooleanParameter( "Enable reorder storage mode" );
       				reorder_storage_mode_min_mb = COConfigurationManager.getIntParameter( "Reorder storage mode min MB" );
    			}
    		});
    }
    


	static volatile boolean	missing_file_dl_restart_enabled;
	
	static boolean	skip_incomp_dl_file_checks;
	static boolean	skip_comp_dl_file_checks;

	static{
		 COConfigurationManager.addAndFireParameterListeners(
			new String[]{
				ConfigKeys.File.BCFG_MISSING_FILE_DOWNLOAD_RESTART,
				ConfigKeys.File.BCFG_SKIP_COMP_DL_FILE_CHECKS,
				ConfigKeys.File.BCFG_SKIP_INCOMP_DL_FILE_CHECKS,
			},
			new ParameterListener(){
				@Override
				public void parameterChanged(String parameterName) {
										
					missing_file_dl_restart_enabled = COConfigurationManager.getBooleanParameter( ConfigKeys.File.BCFG_MISSING_FILE_DOWNLOAD_RESTART );
	    	    	skip_comp_dl_file_checks		= COConfigurationManager.getBooleanParameter( ConfigKeys.File.BCFG_SKIP_COMP_DL_FILE_CHECKS );
	    	    	skip_incomp_dl_file_checks		= COConfigurationManager.getBooleanParameter( ConfigKeys.File.BCFG_SKIP_INCOMP_DL_FILE_CHECKS );
				}
			});
	}
	
    private static final DiskManagerRecheckScheduler      recheck_scheduler       = new DiskManagerRecheckScheduler();
    private static final DiskManagerAllocationScheduler   allocation_scheduler    = new DiskManagerAllocationScheduler();

    private static final ThreadPool	start_pool = new ThreadPool( "DiskManager:start", 64, true );

    static{
    	start_pool.setThreadPriority( Thread.MIN_PRIORITY );
    }

    private boolean used    = false;

    private boolean started = false;
    final AESemaphore started_sem = new AESemaphore( "DiskManager::started" );
    
    private boolean starting;
    
    private volatile boolean stopping;


    private volatile int 		state_set_via_method;
    private volatile String 	errorMessage_set_via_method = "";
    private volatile int		errorType	= 0;

    private int pieceLength;
    private int lastPieceLength;

    private int         nbPieces;       // total # pieces in this torrent
    private long        totalLength;    // total # bytes in this torrent
    private int         percentDone;
    private long        allocated;
    private long		allocate_not_required;
    private long        remaining;

    private volatile String	allocation_task;
    
    	// this used to drive end-of-download detection, careful that it is accurate at all times (there was a bug where it wasn't and caused downloads to prematurely recheck...)
    
    private volatile long	remaining_excluding_dnd;
    

    private final TOTorrent       torrent;


    private DMReader                reader;
    private DMChecker               checker;
    private DMWriter                writer;

    private RDResumeHandler         resume_handler;
    private DMPieceMapper           piece_mapper;

    private DiskManagerPieceImpl[]  pieces;

	private DMPieceMap				piece_map_use_accessor;
	private long					piece_map_use_accessor_time;

    private DiskManagerFileInfoImpl[]				files;
	private DiskManagerFileInfoSet					fileset;

    protected final DownloadManager       download_manager;

    private boolean alreadyMoved = false;

    private boolean             skipped_file_set_changed =true; // go over them once when starting
    private long                skipped_file_set_size;
    private long                skipped_but_downloaded;

    private final AtomicLong			priority_change_marker = new AtomicLong( RandomUtils.nextLong());
    {
	    if ( priority_change_marker.get() == 0 ){
	    	priority_change_marker.incrementAndGet();
	    }
    }

    private boolean				checking_enabled = true;

    private volatile boolean	move_in_progress;
    private volatile long[]		move_progress;
    private volatile File		move_subtask;
    private volatile int		move_state = ProgressListener.ST_NORMAL;
    
        // DiskManager listeners

    private static final int LDT_STATECHANGED           = 1;
    private static final int LDT_PRIOCHANGED            = 2;
    private static final int LDT_PIECE_DONE_CHANGED     = 3;
    private static final int LDT_FILE_COMPLETED		    = 4;

    protected static final ListenerManager<DiskManagerListener>    listeners_aggregator    = ListenerManager.createAsyncManager(
            "DiskM:ListenAggregatorDispatcher",
            new ListenerManagerDispatcher<DiskManagerListener>()
            {
                @Override
                public void
                dispatch(
                	DiskManagerListener      listener,
                    int         			type,
                    Object      			value )
                {
                    if (type == LDT_STATECHANGED){

                        int params[] = (int[])value;

                        listener.stateChanged(params[0], params[1]);

                    }else if (type == LDT_PRIOCHANGED) {

                        listener.filePriorityChanged((DiskManagerFileInfo)value);

                    }else if (type == LDT_PIECE_DONE_CHANGED) {

                        listener.pieceDoneChanged((DiskManagerPiece)value);

                    }else if (type == LDT_FILE_COMPLETED) {

                        listener.fileCompleted((DiskManagerFileInfo)value );
                    }
                }
            });

    private final ListenerManager<DiskManagerListener> listeners   = ListenerManager.createManager(
            "DiskM:ListenDispatcher",
            new ListenerManagerDispatcher<DiskManagerListener>()
            {
                @Override
                public void
                dispatch(
                	DiskManagerListener      listener,
                    int         			type,
                    Object      			value )
                {
                    listeners_aggregator.dispatch( listener, type, value );
                }
            });

    final AEMonitor   start_stop_mon  = new AEMonitor( "DiskManager:startStop" );
    
    private final Object   file_piece_lock  = new Object();

    private final BitFlags	availability;
    
    public
    DiskManagerImpl(
        TOTorrent           _torrent,
        DownloadManager     _dmanager)
    {
        torrent             = _torrent;
        download_manager    = _dmanager;
        
        pieces      = new DiskManagerPieceImpl[0];  // in case things go wrong later

        setState( INITIALIZING );

        percentDone = 0;
        errorType	= ET_NONE;

        if ( torrent == null ){

            setErrorState( "Torrent not available" );

            availability = null;
            
            return;
        }

        nbPieces    = torrent.getNumberOfPieces();

        availability = new BitFlags( nbPieces );

        LocaleUtilDecoder   locale_decoder = null;

        try{
            locale_decoder = LocaleTorrentUtil.getTorrentEncoding( torrent );

        }catch( TOTorrentException e ){

            Debug.printStackTrace( e );

            setErrorState( TorrentUtils.exceptionToText(e));

            return;

        }catch( Throwable e ){

            Debug.printStackTrace( e );

            setErrorState( "Initialisation failed - " + Debug.getNestedExceptionMessage(e));

            return;
        }

        piece_mapper    = DMPieceMapperFactory.create( torrent );

        try{
            piece_mapper.construct( locale_decoder, download_manager.getAbsoluteSaveLocation().getName());

        }catch( Throwable e ){

            Debug.printStackTrace( e );

            setErrorState( "Failed to build piece map - " + Debug.getNestedExceptionMessage(e));

            return;
        }

        totalLength = piece_mapper.getTotalLength();
        
        remaining   			= totalLength;
        remaining_excluding_dnd = remaining;
        
        pieceLength     = (int)torrent.getPieceLength();
        lastPieceLength = piece_mapper.getLastPieceLength();

        pieces      = new DiskManagerPieceImpl[nbPieces];

        for (int i =0; i <nbPieces; i++)
        {
            pieces[i] =new DiskManagerPieceImpl(this, i, i==nbPieces-1?lastPieceLength:pieceLength);
        }

        reader          = DMAccessFactory.createReader(this);

        checker         = DMAccessFactory.createChecker(this);

        writer          = DMAccessFactory.createWriter(this);

        resume_handler  = new RDResumeHandler( this, checker );

    }
    
    @Override
    public String 
    getDisplayName()
    {
    	return( download_manager.getDisplayName());
    }

    @Override
    public DownloadManager 
    getDownload()
    {
    	return( download_manager );
    }
    
    @Override
    public void
    start()
    {
        try{
        	if ( move_in_progress ){

        		Debug.out( "start called while move in progress!" );
        	}

            start_stop_mon.enter();

            if ( used ){

                Debug.out( "DiskManager reuse not supported!!!!" );
            }

            used    = true;

            if ( getState() == FAULTY ){

                Debug.out( "starting a faulty disk manager");

                return;
            }

            started     = true;
            starting    = true;

            start_pool.run(
            	new AERunnable()
            	{
                    @Override
                    public void
                    runSupport()
                    {
                        try{
                        		// now we use a limited pool to manage disk manager starts there
                        		// is an increased possibility of us being stopped before starting
                        		// handle this situation better by avoiding an un-necessary "startSupport"

                            try{
                                start_stop_mon.enter();

	                        	if ( stopping ){

	                        		throw( new Exception( "Stopped during startup" ));
	                        	}
                            }finally{

                                start_stop_mon.exit();
                            }

                            startSupport();

                        }catch( Throwable e ){

                            Debug.printStackTrace(e);

                            setErrorState( Debug.getNestedExceptionMessage(e) + " (start)" );

                        }finally{

                            started_sem.release();
                        }

                        boolean stop_required;

                        try{
                            start_stop_mon.enter();

                            stop_required = DiskManagerImpl.this.getState() == DiskManager.FAULTY || stopping;

                            starting    = false;

                        }finally{

                            start_stop_mon.exit();
                        }

                        if ( stop_required ){

                            DiskManagerImpl.this.stop( false );
                        }
                    }
                });

        }finally{

            start_stop_mon.exit();
        }
    }

    void
    startSupport()
    {
            //if the data file is already in the completed files dir, we want to use it
        boolean files_exist = false;

        if (download_manager.isPersistent()){

        	/**
        	 * Try one of these candidate directories, see if the data already exists there.
        	 */
        	File[] move_to_dirs = DownloadManagerMoveHandler.getRelatedDirs(download_manager);

        	for (int i=0; i<move_to_dirs.length; i++) {
        		File move_to_dir = move_to_dirs[i].getAbsoluteFile();
        		if (filesExist (move_to_dir,true)) {
                    alreadyMoved = files_exist = true;
                    download_manager.setTorrentSaveDir(move_to_dir, false);
                    break;
                }
        	}
        }

        reader.start();

        checker.start();

        writer.start();

        // If we haven't yet allocated the files, take this chance to determine
        // whether any relative paths should be taken into account for default
        // save path calculations.
        if (!alreadyMoved && !download_manager.isDataAlreadyAllocated()) {

        	// Check the files don't already exist in their current location.
        	if (!files_exist) {files_exist = this.filesExist();}
        	if (!files_exist) {
        		SaveLocationChange transfer =
        			DownloadManagerMoveHandler.onInitialisation(download_manager);
        		if (transfer != null) {
        			if (transfer.download_location != null || transfer.download_name != null) {
        				File dl_location = transfer.download_location;
        				if (dl_location == null) {dl_location = download_manager.getAbsoluteSaveLocation().getParentFile();}
        				if (transfer.download_name == null) {
        					download_manager.setTorrentSaveDir(dl_location, false);
        				}
        				else {
        					download_manager.setTorrentSaveDir(FileUtil.newFile(dl_location, transfer.download_name), true);
        				}
        			}
        			if (transfer.torrent_location != null || transfer.torrent_name != null) {
        				try {download_manager.setTorrentFile(transfer.torrent_location, transfer.torrent_name);}
        				catch (DownloadManagerException e) {Debug.printStackTrace(e);}
        			}
        		}
        	}
        }

            //allocate / check every file

        boolean[] stop_after_start = { false };
        
        int[] alloc_result = allocateFiles( stop_after_start );

        int	newFiles 		= alloc_result[0];
        int	notNeededFiles	= alloc_result[1];
        int numPadFiles		= alloc_result[2];
        
        if ( getState() == FAULTY ){

                // bail out if broken in the meantime
                // state will be "faulty" if the allocation process is interrupted by a stop
            return;
        }

        setState( DiskManager.CHECKING );

        resume_handler.start();

        if ( checking_enabled ){

	        if ( newFiles == 0 ){

	            resume_handler.checkAllPieces(false, download_manager.isForceRechecking(), (p)->{ percentDone = p; });

	            	// unlikely to need piece list, force discard

	            if ( getRemainingExcludingDND() == 0 ){

	            	checkFreePieceList( true );
	            }
	        }else if ( newFiles + notNeededFiles + numPadFiles != files.length ){

	                //  if not a fresh torrent, check pieces ignoring fast resume data

	            resume_handler.checkAllPieces(true, download_manager.isForceRechecking(), (p)->{ percentDone = p; });
	        }
        }

        if ( getState() == FAULTY  ){

            return;
        }

            // in all the above cases we want to continue to here if we have been "stopped" as
            // other components require that we end up either FAULTY or READY

            //3.Change State

        	// flag for an update of the 'downloaded' values for skipped files

        skipped_file_set_changed = true;
        
        if ( stop_after_start[0] ){
        	        	        	
        	setErrorState( ET_STOP_DURING_INIT );
        	 
        }else{
        	
            setState( READY );

        }
    }

    @Override
    public boolean
    stop(
    	boolean	closing )
    {
        try{
        	if ( move_in_progress ){

        		Debug.out( "stop called while move in progress!" );
        	}

            start_stop_mon.enter();

            if ( !started ){

                return( false );
            }

                // we need to be careful if we're still starting up as this may be
                // a re-entrant "stop" caused by a faulty state being reported during
                // startup. Defer the actual stop until starting is complete

            if ( starting ){

                stopping    = true;

                    // we can however safely stop things at this point - this is important
                    // to interrupt an alloc/recheck process that might be holding up the start
                    // operation

                checker.stop();

                writer.stop();

                reader.stop();

                resume_handler.stop( closing );

                	// at least save the current stats to download state  - they'll be persisted later
                	// when the "real" stop gets through

                saveState( false );

                return( true );
            }

            started     = false;

            stopping    = false;

        }finally{

            start_stop_mon.exit();
        }

        started_sem.reserve();

        checker.stop();

        writer.stop();

        reader.stop();

        resume_handler.stop( closing );

        if ( files != null ){

            for (int i = 0; i < files.length; i++){

                try{
                    if (files[i] != null) {

                        files[i].getCacheFile().close();
                    }
                }catch ( Throwable e ){

                    setFailed( DiskManager.ET_OTHER, "File close fails", e );
                }
            }
        }

        if ( getState() == DiskManager.READY || ( getState() == DiskManager.FAULTY && errorType == ET_STOP_DURING_INIT )){

            try{

                saveResumeData( false );

            }catch( Exception e ){

                setFailed( DiskManager.ET_OTHER, "Resume data save fails", e );
            }
        }

        saveState( false );

        // can't be used after a stop so we might as well clear down the listeners
        listeners.clear();

        return( false );
    }

    @Override
    public boolean
    isStopped()
    {
       	if ( move_in_progress ){

    		Debug.out( "isStopped called while move in progress!" );
    	}

        try{
            start_stop_mon.enter();

            return( !( started || starting || stopping ));

        }finally{

            start_stop_mon.exit();
        }
    }

    @Override
    public boolean
    filesExist()
    {
        return( filesExist( download_manager.getAbsoluteSaveLocation().getParentFile(), false));
    }

    protected boolean
    filesExist(
        File  		root_dir,
        boolean		exact )
    {
        if ( !torrent.isSimpleTorrent()){

            root_dir = FileUtil.newFile(root_dir, download_manager.getAbsoluteSaveLocation().getName());
        }

        // System.out.println( "root dir = " + root_dir_file );

        DMPieceMapperFile[] pm_files = piece_mapper.getFiles();

        String[]    storage_types = getStorageTypes();

  		DownloadManagerState state = download_manager.getDownloadState();

        for (int i = 0; i < pm_files.length; i++) {

            DMPieceMapperFile pm_info = pm_files[i];

            String relative_file = pm_info.getRelativeDataPath();

            long target_length = pm_info.getLength();

                // use the cache file to ascertain length in case the caching/writing algorithm
                // fiddles with the real length
                // Unfortunately we may be called here BEFORE the disk manager has been
                // started and hence BEFORE the file info has been setup...
                // Maybe one day we could allocate the file info earlier. However, if we do
                // this then we'll need to handle the "already moved" stuff too...

            DiskManagerFileInfoImpl file_info = (DiskManagerFileInfoImpl)pm_info.getFileInfo();

            boolean close_it    = false;

            try{
                if ( file_info == null ){

                    int storage_type = DiskManagerUtil.convertDMStorageTypeFromString( storage_types[i]);

                	file_info = createFileInfo( state, pm_info, i, root_dir, relative_file, storage_type );

                	close_it = true;
                }

                try{
                    CacheFile   cache_file  = file_info.getCacheFile();
                    File        data_file   = file_info.getFile(true);

                    if ( !cache_file.exists()){

                            // look for something sensible to report

                          File current = data_file;

                          while( !current.exists()){

                            File    parent = current.getParentFile();

                            if ( parent == null ){

                                break;

                            }else if ( !parent.exists()){

                                current = parent;

                            }else{

                                if ( parent.isDirectory()){

                                    setErrorMessage( current.toString() + " not found." );

                                }else{

                                	setErrorMessage( parent.toString() + " is not a directory." );
                                }

                                return( false );
                            }
                          }

                          setErrorMessage( data_file.toString() + " not found." );

                          return false;
                    }

                    long    existing_length = file_info.getCacheFile().getLength();

                    if ( exact && !file_info.isSkipped() && existing_length != target_length ){
                    	
                    	setErrorMessage( data_file.toString() + " incorrect size." );
                    	
                    	return( false );
                    }
                    
                    	// only test for too big as if incremental creation selected
                    	// then too small is OK

                    if ( existing_length > target_length ){

                        if ( COConfigurationManager.getBooleanParameter("File.truncate.if.too.large")){

                            file_info.setAccessMode( DiskManagerFileInfo.WRITE );

                            file_info.getCacheFile().setLength( target_length );

                            Debug.out( "Existing data file length too large [" +existing_length+ ">" +target_length+ "]: " + data_file.getAbsolutePath() + ", truncating" );

                        }else{

                        	setErrorMessage( "Existing data file length too large [" +existing_length+ ">" +target_length+ "]: " + data_file.getAbsolutePath());

                            return false;
                        }
                    }
                }finally{

                    if ( close_it ){

                        file_info.getCacheFile().close();
                    }
                }
            }catch( Throwable e ){

            	Debug.out(e);

            	setErrorMessage( e, "filesExist:" + relative_file);

                return( false );
            }
        }

        return true;
    }

    private DiskManagerFileInfoImpl
    createFileInfo(
    	DownloadManagerState		state,
    	DMPieceMapperFile			pm_info,
    	int							file_index,
    	File						root_dir,
    	String					relative_file,
    	int							storage_type )

    	throws Exception
    {
        try{

            return( new DiskManagerFileInfoImpl(
                                this,
                                root_dir,
                                relative_file,
                                file_index,
                                pm_info.getTorrentFile(),
                                storage_type ));

        }catch( CacheFileManagerException e ){

        		// unfortunately there are files out there with ascii < 32 chars in that are invalid on windows
        		// but ok on other file systems
        		// it would be possible to fix this in FileUtil.convertOSSPecificChars but my worry with this is that it
        		// would potentially break existing downloads, so I whimped out and decided to take the approach of
        		// detecting the issue and using file-links to work around it

        	if ( Debug.getNestedExceptionMessage(e).contains( "volume label syntax is incorrect" )){

						File target_file = FileUtil.newFile( root_dir, relative_file);

        		File actual_file = state.getFileLink( file_index, target_file );

        		if ( actual_file == null ){

        			actual_file = target_file;
        		}

        		File temp = actual_file;

        		Stack<String>	comps = new Stack<>();

        		boolean	fixed = false;

        		while( temp != null ){

        			if ( temp.exists()){

        				break;
        			}

        			String old_name 	= temp.getName();
        			String new_name		= "";

        			char[] chars = old_name.toCharArray();

        			for ( char c: chars ){

        				int	i_c = (int)c;

        				if ( i_c >= 0 && i_c < 32 ){

        					new_name += "_";

        				}else{

        					new_name += c;
        				}
        			}

        			comps.push( new_name );

        			if ( !old_name.equals( new_name )){

        				fixed = true;
        			}

        			temp = temp.getParentFile();
        		}

        		if ( fixed ){

        			while( !comps.isEmpty()){

        				String comp = comps.pop();

        				if ( comps.isEmpty()){

        					String prefix = Base32.encode( new SHA1Simple().calculateHash( relative_file.getBytes( "UTF-8" ))).substring( 0, 4 );

        					comp = prefix + "_" + comp;
        				}

        				temp = FileUtil.newFile( temp, comp );
        			}

           			Debug.outNoStack( "Fixing unsupported file path: " + actual_file.getAbsolutePath() + " -> " + temp.getAbsolutePath());

           			state.setFileLink( file_index, target_file, temp );

                    return(
                    	new DiskManagerFileInfoImpl(
                            this,
                            root_dir,
                            relative_file,
                            file_index,
                            pm_info.getTorrentFile(),
                            storage_type ));
        		}
        	}

        	throw( e );
        }
    }

    private int[]
    allocateFiles(
    	boolean[] stop_after_start )
    {
    	int[] fail_result = { -1, -1, -1 };

        //Set<String> file_set    = new HashSet<>();

        DMPieceMapperFile[] pm_files = piece_mapper.getFiles();

        
        DownloadManagerState	state = download_manager.getDownloadState();

        boolean alreadyAllocated = download_manager.isDataAlreadyAllocated();
        
        boolean isComplete = download_manager.isDownloadComplete(false);
        
        long alloc_strategy = state.getLongAttribute( DownloadManagerState.AT_FILE_ALLOC_STRATEGY );

		boolean skip_complete_file_checks 	= skip_comp_dl_file_checks && isComplete;
		boolean skip_incomplete_file_checks	= skip_incomp_dl_file_checks && alreadyAllocated && !isComplete;
		
        boolean	alloc_ok = false;
        
        DiskManagerFileInfoImpl[] allocated_files = new DiskManagerFileInfoImpl[pm_files.length];

        DiskManagerAllocationScheduler.AllocationInstance allocation_instance = null;
             
        	// alloc_requests are expected to need allocating so don't treat them as unexpected
        
		Map alloc_requests = state.getMapAttribute( DownloadManagerState.AT_FILE_ALLOC_REQUEST );
		
        try{
            setState( ALLOCATING );

            allocated 				= 0;
            allocate_not_required	= 0;
            
            int numNewFiles 		= 0;
            int notRequiredFiles	= 0;
            int numPadFiles			= 0;

            File root_dir = download_manager.getAbsoluteSaveLocation();

            if ( torrent.isSimpleTorrent()){

                root_dir = root_dir.getParentFile();
            }

            String[]    storage_types = getStorageTypes();

			String incomplete_suffix = state.getAttribute( DownloadManagerState.AT_INCOMP_FILE_SUFFIX );

			for ( int i=0;i<pm_files.length;i++ ){

				if ( stopping ){

					setErrorState( "File allocation interrupted - download is stopping" );

					return( fail_result );
				}

				DMPieceMapperFile pm_info = pm_files[i];

				String relative_data_file = pm_info.getRelativeDataPath();

				allocation_task = relative_data_file;

				DiskManagerFileInfoImpl fileInfo;

				try{
					int storage_type = DiskManagerUtil.convertDMStorageTypeFromString( storage_types[i]);

					fileInfo = createFileInfo( state, pm_info, i, root_dir, relative_data_file, storage_type );

					allocated_files[i] = fileInfo;

					pm_info.setFileInfo( fileInfo );

				}catch ( Exception e ){

					setErrorState( Debug.getNestedExceptionMessage(e) + " (allocateFiles:" + relative_data_file.toString() + ")" );

					return( fail_result );
				}
			}

			DiskManagerFileInfoSetImpl allocated_fileset = new DiskManagerFileInfoSetImpl( allocated_files ,this );

			DiskManagerUtil.loadFilePriorities( download_manager, allocated_fileset );		
			
            Set<String>	priority_file_exts = TorrentUtils.getFilePriorityExtensions();

            boolean priority_file_exts_ignore_case = TorrentUtils.getFilePriorityExtensionsIgnoreCase();
            
            for ( int i=0;i<pm_files.length;i++ ){

            	if ( stopping ){

                    setErrorState( "File allocation interrupted - download is stopping" );

                    return( fail_result );
            	}

                final DMPieceMapperFile pm_info = pm_files[i];

                final long target_length = pm_info.getLength();

                String relative_data_file = pm_info.getRelativeDataPath();

                allocation_task = relative_data_file;

                DiskManagerFileInfoImpl fileInfo = allocated_files[i];

                if ( fileInfo.getTorrentFile().isPadFile()){
                	
                	allocate_not_required += target_length;
                	
                	numPadFiles++;
                	
                	continue;
                }
                
                File        data_file       = fileInfo.getFile(true);
                
                String  file_key = data_file.getAbsolutePath();

                if ( Constants.isWindows ){

                    file_key = file_key.toLowerCase();
                }

                /*
                	Windows 10 supports case-sensitive file systems so ditch this
                	
                if ( file_set.contains( file_key )){

                    String msg = "File occurs more than once in download: " + data_file.toString() + ".\nRename one of the files in Files view via the right-click menu.";

                    setErrorState( msg );

                    return( fail_result );
                }

                file_set.add( file_key );
				*/
                
                String      ext  = data_file.getName();

                if ( incomplete_suffix != null && ext.endsWith( incomplete_suffix )){

                	ext = ext.substring( 0, ext.length() - incomplete_suffix.length());
                }

                int separator = ext.lastIndexOf(".");

                if ( separator == -1 ){

                    separator = 0;
                }

                fileInfo.setExtension(ext.substring(separator));
                
                if (!priority_file_exts.isEmpty()){
                	
                    String e = fileInfo.getExtension();
                    
                    if ( priority_file_exts_ignore_case ){
                    	
                    	e = e.toLowerCase( Locale.US );
                    }
                    
                    if ( priority_file_exts.contains( e )){
                            
                    	fileInfo.setPriority(1);
                    }
                }

                fileInfo.setDownloaded(0);

                CacheFile   cache_file      = fileInfo.getCacheFile();

                int st = cache_file.getStorageType();

                boolean compact = st == CacheFile.CT_COMPACT || st == CacheFile.CT_PIECE_REORDER_COMPACT;

                boolean mustExistOrAllocate = ( !compact ) || RDResumeHandler.fileMustExist(download_manager, allocated_fileset, fileInfo);

                if ( skip_complete_file_checks ){
                	
                	if ( mustExistOrAllocate ){
                		
                		allocated += target_length;
                		
                	}else{
                		
	                	allocate_not_required += target_length;
	                	
	                	notRequiredFiles++;
                	}
                }else{
                	
                		// delete compact files that do not contain pieces we need
                	
	                if (!mustExistOrAllocate && !skip_incomplete_file_checks && cache_file.exists()){
	
						data_file.delete();
	                }
	
	                if ( skip_incomplete_file_checks || cache_file.exists() ){
	
	                	boolean did_allocate = false;
	                	
	                    try {
	
	                        //make sure the existing file length isn't too large
	
	                        long    existing_length = skip_incomplete_file_checks?target_length:cache_file.getLength();
	
	                        if(  existing_length > target_length ){
	
	                            if ( COConfigurationManager.getBooleanParameter("File.truncate.if.too.large")){
	
	                                fileInfo.setAccessMode( DiskManagerFileInfo.WRITE );
	
	                                cache_file.setLength( target_length );
	
	                                fileInfo.setAccessMode( DiskManagerFileInfo.READ );
	
	                                Debug.out( "Existing data file length too large [" +existing_length+ ">" +target_length+ "]: " +data_file.getAbsolutePath() + ", truncating" );
	
	                            }else{
	
	                                setErrorState( "Existing data file length too large [" +existing_length+ ">" +target_length+ "]: " + data_file.getAbsolutePath() );
	
	                                return( fail_result );
	                            }
	                        }else if ( existing_length < target_length ){
	
	                        	if ( !compact ){
	                        		
		                        		// file is too small
	
	                        		if ( allocation_instance == null ){
	                        			
	                        			allocation_instance = allocation_scheduler.register( this );
	                        		}
	                        		
		                         	if ( !allocateFile( allocation_instance, fileInfo, data_file, existing_length, target_length, stop_after_start, alloc_strategy )){
	
		                      			// aborted
	
		                         		return( fail_result );
		                         	}
		                         	
		                         	did_allocate = true;
	                        	}
	                        }
	                    }catch (Throwable e) {
	                    	Debug.out(e);
	
	                    	fileAllocFailed( data_file, target_length, false, e );
	
	                        setErrorState();
	
	                        return( fail_result );
	                    }
	
	                    if ( !did_allocate ){
	                    
	                    	allocated += target_length;
	                    }
	
	                } else if ( mustExistOrAllocate ){
	
	                		//we need to allocate it
	                        //make sure it hasn't previously been allocated
	
	                    if ( alreadyAllocated ){
	
	                        setErrorState( 
	                        	DiskManager.ET_FILE_MISSING, 
	                        	MessageText.getString( "DownloadManager.error.datamissing" ) + ": " + data_file.getAbsolutePath());
	
	                        return( fail_result );
	                    }
	
	
	                    try{
                    		if ( allocation_instance == null ){
                    			
                    			allocation_instance = allocation_scheduler.register( this );
                    		}
	
	                    	if ( !allocateFile( allocation_instance, fileInfo, data_file, -1, target_length, stop_after_start, alloc_strategy )){
	
	                      			// aborted
	
	                    		return( fail_result );
	                    	}
	
	                    }catch( Throwable e ){
	
	                    	fileAllocFailed( data_file, target_length, true, e );
	
	                        setErrorState();
	
	                        return( fail_result );
	                    }
	
	                    if ( alloc_requests == null || !alloc_requests.containsKey( String.valueOf( i ))){
	                    
	                    	numNewFiles++;
	                    }
	
	                }else{
	
	                	allocate_not_required += target_length;
	                	
	                	notRequiredFiles++;
	                }
                }
            }

                // make sure that "files" doens't become visible to the rest of the world until all
                // entries have been populated

            files   = allocated_files;
            fileset = allocated_fileset;

            download_manager.setDataAlreadyAllocated( true );

            alloc_ok = true;
            
            return( new int[]{ numNewFiles, notRequiredFiles, numPadFiles });

        }finally{

        	if ( allocation_instance != null ){
            
        		allocation_instance.unregister();
        	}

            allocation_task = null;
            
            if ( alloc_requests != null ){
            	
        		state.setMapAttribute( DownloadManagerState.AT_FILE_ALLOC_REQUEST, null );
            }
            
            if ( alloc_ok ){
            
            	if ( allocated + allocate_not_required != totalLength ){
            		
            		Debug.out( "Allocation ok but totals inconsistent: " + allocated + "/" + allocate_not_required + "/" + totalLength );
            	}
            }
                // if we failed to do the allocation make sure we close all the files that
                // we might have opened

            if ( files == null ){

                for (int i=0;i<allocated_files.length;i++){

                    if ( allocated_files[i] != null ){

                        try{
                            allocated_files[i].getCacheFile().close();

                        }catch( Throwable e ){
                        }
                    }
                }
            }
        }
    }

    private boolean
    allocateFile(
    	DiskManagerAllocationScheduler.AllocationInstance	allocation_instance,
    	DiskManagerFileInfoImpl								fileInfo,
    	File												data_file,
    	long												existing_length,	// -1 if not exists
    	long												target_length,
    	boolean[]											stop_after_start,
    	long												alloc_strategy )

    	throws Throwable
    {
    		// on success will have incremented 'allocated' by 'target_length
    	
        while( started && !stopping ){

            if ( allocation_instance.getPermission()){

                break;
            }
        }

        if ( stopping || !started ){

                // allocation interrupted

        	setErrorState( "File allocation interrupted - download is stopping" );
        	
            return( false );
        }

        fileInfo.setAccessMode( DiskManagerFileInfo.WRITE );
        
        boolean def_strategy = alloc_strategy == DownloadManagerState.FAS_DEFAULT;
        
        if ( def_strategy && COConfigurationManager.getBooleanParameter("Enable incremental file creation" )){

                //  do incremental stuff

        	if ( existing_length < 0 ){

        			// only do this if it doesn't exist

        		fileInfo.getCacheFile().setLength( 0 );
        	}
        	
        	 allocated += target_length;
        	 
        }else{

	            //fully allocate. XFS borks with zero length files though

	        if ( 	def_strategy &&
	        		target_length > 0 &&
	        		!Constants.isWindows &&
	        		COConfigurationManager.getBooleanParameter("XFS Allocation") ){

	            fileInfo.getCacheFile().setLength( target_length );

	            long	resvp_start;
	            long	resvp_len;

	            if ( existing_length > 0 ){

	            	resvp_start = existing_length;
	            	resvp_len	= target_length - existing_length;
	            }else{
	            	resvp_start = 0;
	            	resvp_len	= target_length;
	            }

	            String[] cmd = {"/usr/sbin/xfs_io","-c", "resvsp " + resvp_start + " " + resvp_len, data_file.getAbsolutePath()};

	            ByteArrayOutputStream os = new ByteArrayOutputStream();
	            byte[] buffer = new byte[1024];
	            try {
	                Process p = Runtime.getRuntime().exec(cmd);
	                for (int count = p.getErrorStream().read(buffer); count > 0; count = p.getErrorStream().read(buffer)) {
	                   os.write(buffer, 0, count);
	                }
	                os.close();
	                p.waitFor();
	            } catch (IOException e) {
	            	String message = MessageText.getString("xfs.allocation.xfs_io.not.found", new String[] {e.getMessage()});
	            	Logger.log(new LogAlert(this, LogAlert.UNREPEATABLE, LogAlert.AT_ERROR, message));
	            }
	            if (os.size() > 0) {
	            	String message = os.toString().trim();
	            	if (message.endsWith("is not on an XFS filesystem")) {
	            		Logger.log(new LogEvent(this, LogIDs.DISK, "XFS file allocation impossible because \"" + data_file.getAbsolutePath()
	            				+ "\" is not on an XFS filesystem. Original error reported by xfs_io : \"" + message + "\""));
	            	} else {
	            		throw new Exception(message);
	            	}
	            }

	            allocated += target_length;

	        }else if ( COConfigurationManager.getBooleanParameter( "Zero New" ) || 
	        			( 	alloc_strategy == DownloadManagerState.FAS_ZERO_NEW || 
	        				alloc_strategy == DownloadManagerState.FAS_ZERO_NEW_STOP )) {  //zero fill

	        	boolean successfulAlloc = false;

	        	try {
	        		
	        		long start_from = 0;
	        		
	        		if ( existing_length > 0 && existing_length < target_length ){
	        			
	        				// if someone has stopped a download and dropped in a file that is too short 
	        				// then we don't want to zero the entire thing in case they have a file they believe is
	        				// partially correct and they're rechecking it
	        			
	        			if ( download_manager.getState() == DownloadManager.STATE_CHECKING ){
	        				
	        				start_from = existing_length;
	        			}
	        		}
	        		        			
	        		successfulAlloc = 
	        			writer.zeroFile( 
	        				allocation_instance,
	        				fileInfo, 
	        				start_from, 
	        				target_length,
	        				(b)->{ allocated += b; });

	        		if ( successfulAlloc && !stop_after_start[0] ){
	        				        			
	        			if ( COConfigurationManager.getBooleanParameter("Zero New Stop") || alloc_strategy == DownloadManagerState.FAS_ZERO_NEW_STOP ){
	        			
	        				DownloadManagerState dms = download_manager.getDownloadState();
	        				
		        			if ( !( dms.getFlag( DownloadManagerState.FLAG_METADATA_DOWNLOAD ) || dms.getFlag( DownloadManagerState.FLAG_DISABLE_STOP_AFTER_ALLOC ))){

		        				stop_after_start[0] = true;
		        			}
	        			}
	        		}
	        	}catch( Throwable e ){
	        			// in case an error occured set the error message before we set it to FAULTY in the finally clause, the exception handler further down is too late

	        		fileAllocFailed( data_file, target_length, existing_length==-1, e );

	                throw( e );

	        	}finally{

	        		if (!successfulAlloc){

						try{
								// failed to zero it, delete it so it gets done next start

							fileInfo.getCacheFile().close();

							fileInfo.getCacheFile().delete();

						}catch (Throwable e){

						}

						setErrorState();
					}
	        	}

	        		// the zeroFile method updates allocation as it occurs

	        }else{

	                //reserve the full file size with the OS file system

	            fileInfo.getCacheFile().setLength( target_length );

	            allocated += target_length;
	        }
        }

        fileInfo.setAccessMode( DiskManagerFileInfo.READ );

        return( true );
    }

    private void
    fileAllocFailed(
    	File		file,
    	long		length,
    	boolean		is_new,
    	Throwable 	e )
    {
    	setErrorMessage( length, e, "allocateFiles " + (is_new?"new":"existing") + ":" + file.toString());
    }

    @Override
    public DiskAccessController
    getDiskAccessController()
    {
        return( disk_access_controller );
    }

    @Override
    public void
    enqueueReadRequest(
        DiskManagerReadRequest request,
        DiskManagerReadRequestListener listener )
    {
        reader.readBlock( request, listener );
    }

	@Override
	public boolean
	hasOutstandingReadRequestForPiece(
		int		piece_number )
	{
		return( reader.hasOutstandingReadRequestForPiece( piece_number ));
	}

    @Override
    public int
    getNbPieces()
    {
        return nbPieces;
    }

    @Override
    public int
    getPercentDone()
    {
        return percentDone;
    }

    @Override
    public int
    getPercentAllocated()
    {
        return((int) (((allocated+allocate_not_required) * 1000) / totalLength ));
    }

    @Override
    public long[]
    getLatency()
    {
    	return( new long[]{ reader.getLatency(), writer.getLatency() });
    }
    
    @Override
    public String 
    getAllocationTask()
    {
    	return( allocation_task );
    }
    
    @Override
    public long
    getRemaining() {
        return remaining;
    }

    private void
    fixupSkippedCalculation()
    {
        if ( skipped_file_set_changed ){

            DiskManagerFileInfoImpl[]   current_files = files;

            if ( current_files != null ){

                skipped_file_set_changed    = false;

                synchronized( file_piece_lock ){
                	
	                try{	
	                    long skipped   		= 0;
	                    long downloaded  	= 0;
	
	                    for (int i=0;i<current_files.length;i++){
	
	                        DiskManagerFileInfoImpl file = current_files[i];
	
	                        if ( file.isSkipped()){
	
	                        	skipped   += file.getLength();
	                        	downloaded  += file.getDownloaded();
	                        }
	                    }
	
	                    skipped_file_set_size 	= skipped;
	                    skipped_but_downloaded	= downloaded;
	                    
	                }finally{
	
	                	remaining_excluding_dnd = ( remaining - ( skipped_file_set_size - skipped_but_downloaded ));
	                	
	                	if ( remaining_excluding_dnd < 0 ){
	                		
	                		Debug.out( "remaining_excluding_dnd went negative" );
	                		
	                		remaining_excluding_dnd = 0;
	                	}
	                }
                }
                
                DownloadManagerStats stats = download_manager.getStats();

                if (stats instanceof DownloadManagerStatsImpl) {
                	((DownloadManagerStatsImpl) stats).setSkippedFileStats(skipped_file_set_size, skipped_but_downloaded);
                }
            }
        }
    }

    @Override
    public long
    getRemainingExcludingDND()
    {
    	fixupSkippedCalculation();
    	
    	return( remaining_excluding_dnd );
    }

	@Override
	public long getSizeExcludingDND() {
		fixupSkippedCalculation();

		return totalLength - skipped_file_set_size;
	}

	@Override
	public int getPercentDoneExcludingDND() {
		long sizeExcludingDND = getSizeExcludingDND();
		if (sizeExcludingDND <= 0) {
			return 0;
		}
		float pct = (sizeExcludingDND - getRemainingExcludingDND()) / (float) sizeExcludingDND;
		return (int) (1000 * pct);
	}

    /**
     *  Called when status has CHANGED and should only be called by DiskManagerPieceImpl
     */

    @Override
    public void
    setPieceDone(
        DiskManagerPieceImpl    dmPiece,
        boolean                 done )
    {
        int piece_number =dmPiece.getPieceNumber();
        int piece_length =dmPiece.getLength();
        
        synchronized( file_piece_lock ){
	        try{
	
	            if ( dmPiece.isDone() != done ){
	            	
	                dmPiece.setDoneSupport(done);
	
	                if (done){
	                
	                	availability.set( piece_number );
	                	
	                    remaining -=piece_length;
	                    
	                }else{
	                	
	                	availability.unset( piece_number );
	                	
	                    remaining +=piece_length;
	                }
	                
	                DMPieceList piece_list = getPieceList( piece_number );
	
	                for (int i =0; i <piece_list.size(); i++){
	
	                    DMPieceMapEntry piece_map_entry =piece_list.get(i);
	
	                    DiskManagerFileInfoImpl this_file = (DiskManagerFileInfoImpl)piece_map_entry.getFile();
	
	                    long file_length =this_file.getLength();
	
	                    long file_done =this_file.getDownloaded();
	
	                    long file_done_before =file_done;
	
	                    if (done){
	                    	
	                        file_done +=piece_map_entry.getLength();
	                        
	                    }else{
	                    	
	                        file_done -=piece_map_entry.getLength();
	                    }
	                    
	                    if (file_done <0){
	                    	
	                        Debug.out("piece map entry length negative");
	
	                        file_done = 0;
	
	                    }else if (file_done >file_length){
	                    	
	                        Debug.out("piece map entry length too large");
	
	                        file_done =file_length;
	                    }
	
	                    if ( this_file.isSkipped()){
	                    	
	                        skipped_but_downloaded +=(file_done -file_done_before);
	                    }
	
	                    this_file.setDownloaded(file_done);
	
	                    	// change file modes based on whether or not the file is complete or not
	
	                    if ( file_done == file_length ){
	
	                    	try{
	                      		DownloadManagerState state = download_manager.getDownloadState();
	
	                    		try{
	
		                    		String suffix = state.getAttribute( DownloadManagerState.AT_INCOMP_FILE_SUFFIX );
	
		                    		if ( suffix != null && suffix.length() > 0 ){
	
										String prefix = state.getAttribute( DownloadManagerState.AT_DND_PREFIX );
	
										if ( prefix == null ){
	
											prefix = "";
										}
	
		                    			File base_file = this_file.getFile( false );
	
		                    			int	file_index = this_file.getIndex();
	
		                    			File link = state.getFileLink( file_index, base_file );
	
		                    			if ( link != null ){
	
		                    				String	name = link.getName();
	
		                    				if ( name.endsWith( suffix ) && name.length() > suffix.length()){
	
		                    					String	new_name = name.substring( 0, name.length() - suffix.length());
	
		                    					if ( !this_file.isSkipped()){
	
		                    							// retain prefix for dnd files as it is there to prevent clashes
	
			                    					if ( prefix.length() > 0 && new_name.startsWith( prefix )){
	
			                    						new_name = new_name.substring( prefix.length());
			                    					}
		                    					}
	
		                    					File new_file = FileUtil.newFile( link.getParentFile(), new_name );
	
		                    					if ( !new_file.exists()){
	
		                    						this_file.renameFile( new_name );
	
		                    						if ( base_file.equals( new_file )){
	
		                    							state.setFileLink( file_index, base_file, null );
	
		                    						}else{
	
		                    							state.setFileLink( file_index, base_file, new_file );
		                    						}
		                    					}
		                    				}
		                    			}else{
	
		                    					/* bit nasty this but I (parg) spent a while trying to find an alternative solution to this and gave up
		                    					 * With simple torrents, if a 'file-move' operation is performed while incomplete with a suffix defined then
		                    					 * the actual save location gets updated and the link information lost as a result (it is as if the user went and
		                    					 * moved the file to another one that happened to end in the suffix). Detect this situation and do the best we
		                    					 * can to remove the auto-added suffix
		                    					 */
	
		                    				if ( this_file.getTorrentFile().getTorrent().isSimpleTorrent()){
	
		                    					File save_location = download_manager.getSaveLocation();
	
		                    					String	name = save_location.getName();
	
			                    				if ( name.endsWith( suffix ) && name.length() > suffix.length()){
	
			                    					String	new_name = name.substring( 0, name.length() - suffix.length());
	
			                    					if ( !this_file.isSkipped()){
	
			                    							// retain prefix for dnd files as it is there to prevent clashes
	
				                    					if ( prefix.length() > 0 && new_name.startsWith( prefix )){
	
				                    						new_name = new_name.substring( prefix.length());
				                    					}
			                    					}
	
			                    					File new_file = FileUtil.newFile( save_location.getParentFile(), new_name );
	
			                    					if ( !new_file.exists()){
	
			                    						this_file.renameFile( new_name );
	
			                    						if ( save_location.equals( new_file )){
	
			                    							state.setFileLink( 0, save_location, null );
	
			                    						}else{
	
			                    							state.setFileLink( 0, save_location, new_file );
			                    						}
			                    					}
			                    				}
		                    				}
		                    			}
		                    		}
	                    		}finally{
	
	                             	if ( this_file.getAccessMode() == DiskManagerFileInfo.WRITE ){
	
	                               		this_file.setAccessMode( DiskManagerFileInfo.READ );
	                               	}
	
	                             		// only record completion during normal downloading, not rechecking etc
	
	                             	if ( getState() == READY ){
	
	                             		state.setLongParameter( DownloadManagerState.PARAM_DOWNLOAD_FILE_COMPLETED_TIME, SystemTime.getCurrentTime());
	                             		
	    	                    		listeners.dispatch( LDT_FILE_COMPLETED, this_file );
	                             	}
	                    		}
	                        }catch ( Throwable e ){
	
	                            setFailed( this_file.getCacheFile().exists()?DiskManager.ET_WRITE_ERROR:DiskManager.ET_FILE_MISSING, "Disk access error", e );
	
	                            Debug.printStackTrace(e);
	                        }
	
	                        // note - we don't set the access mode to write if incomplete as we may
	                        // be rechecking a file and during this process the "file_done" amount
	                        // will not be file_length until the end. If the file is read-only then
	                        // changing to write will cause trouble!
	                      }
	                }
	
	                if ( getState() == READY ){
	
	                		// don't start firing these until we're ready otherwise we send notifications
	                		// for complete pieces during initialisation
	
	                	listeners.dispatch(LDT_PIECE_DONE_CHANGED, dmPiece);
	                }
	            }
	        }finally{
	        	
	        	remaining_excluding_dnd = ( remaining - ( skipped_file_set_size - skipped_but_downloaded ));
	        	
            	if ( remaining_excluding_dnd < 0 ){
            		
            		Debug.out( "remaining_excluding_dnd went negative" );
            		
            		remaining_excluding_dnd = 0;
            	}
	        }
        }
    }

    @Override
    public BitFlags 
    getAvailability()
    {
    	return( availability );
    }
    
    @Override
    public DiskManagerPiece[] getPieces()
    {
        return pieces;
    }

    @Override
    public DiskManagerPiece getPiece(int PieceNumber)
    {
        return pieces[PieceNumber];
    }

    @Override
    public int getPieceLength() {
        return pieceLength;
    }

    @Override
    public int
    getPieceLength(
    	int		piece_number )
    {
		if (piece_number == nbPieces -1 ){

			return( lastPieceLength );

		}else{

			return( pieceLength );
		}
    }

    @Override
    public long getTotalLength() {
        return totalLength;
    }

    public int getLastPieceLength() {
        return lastPieceLength;
    }

    @Override
    public int getState() {
        return state_set_via_method;
    }

    private void
    setErrorMessage(
    	String		str )
    {
    	errorMessage_set_via_method = str;
    }
    
    private void
    setErrorMessage(
    	Throwable	e,
    	String		str )
    {
    	errorMessage_set_via_method = Debug.getNestedExceptionMessage( e ) + " (" + str + ")";
    }
    
    private void
    setErrorMessage(
    	long		file_length,
    	Throwable	e,
    	String		str )
    {			
		if ( DiskManagerUtil.isNoSpaceException( e )){
	
			errorType	= ET_INSUFFICIENT_SPACE;
	
			if ( file_length >= 4*1024*1024*1024L ){
	
					// might be FAT32 limit, see if we really have run out of space
	
				errorMessage_set_via_method = MessageText.getString( "DiskManager.error.nospace_fat32" );
	
			}else{
	
				errorMessage_set_via_method = MessageText.getString( "DiskManager.error.nospace" );
			}
		}else{
	
		   	String exception_str  = Debug.getNestedExceptionMessage(e);
			
		   	errorMessage_set_via_method = exception_str + " (" + str + ")";
		}
    }
    
    private void
    setErrorState()
    {
     	setState( FAULTY );
    }
    
    private void
    setErrorState(
    	String		msg )
    {
    	setErrorMessage( msg );
    	
    	setState( FAULTY );
    }
    
    private void
    setErrorState(
    	int			type,
    	String		msg )
    {
    	setErrorMessage( msg );
    	
    	errorType		= type;
    	
    	setState( FAULTY );
    }
    
    private void
    setErrorState(
    	String		msg,
    	Throwable	cause )
    {	
		if ( DiskManagerUtil.isNoSpaceException( cause )){
	
			errorType	= ET_INSUFFICIENT_SPACE;
		
			errorMessage_set_via_method = MessageText.getString( "DiskManager.error.nospace" );
			
		}else{
			
	       	String exception_str  = Debug.getNestedExceptionMessage(cause);
	    	
		   	errorMessage_set_via_method = msg + ": " + exception_str;
		}
    	
    	setState( FAULTY );
    }
    
    private void
    setErrorState(
    	int			type,
    	String		msg,
    	Throwable	cause )
    {		
	    String exception_str  = Debug.getNestedExceptionMessage(cause);
	    
		errorMessage_set_via_method = msg + ": " + exception_str;
		
		errorType		= type;
    	
    	setState( FAULTY );
    }
    
    private void
    setErrorState(
    	int			type )
    {
    	errorType		= type;
    	
    	setState( FAULTY );
    }
    
    
    private void
    setState(
        int     _state )
    {
            // we never move from a faulty state
    	
        if ( state_set_via_method == FAULTY ){

            if ( _state != FAULTY ){

                Debug.out( "DiskManager: attempt to move from faulty state to " + _state );
            }

            return;
        }

        if ( state_set_via_method != _state ){

            int params[] = {state_set_via_method, _state};

            state_set_via_method = _state;

            if ( _state == FAULTY ){

            	if ( errorType == ET_NONE ){

            		errorType	= ET_OTHER;
            	}
            }

            listeners.dispatch( LDT_STATECHANGED, params);
        }
    }


    @Override
    public DiskManagerFileInfo[]
    getFiles()
    {
        return files;
    }

    @Override
    public DiskManagerFileInfoSet getFileSet() {
    	return fileset;
    }

    @Override
    public String 
    getErrorMessage() 
    {
        return errorMessage_set_via_method;
    }

	@Override
	public int
	getErrorType()
	{
		return( errorType );
	}

    @Override
    public void
    setFailed(
    	int					type,
        String        		reason,
        Throwable			cause )
    {
            /**
             * need to run this on a separate thread to avoid deadlock with the stopping
             * process - setFailed tends to be called from within the read/write activities
             * and stopping these requires this.
             */

        new AEThread2("DiskManager:setFailed")
        {
            @Override
            public void
            run()
            {
            	String msg = reason + ": " + Debug.getNestedExceptionMessage( cause );

            	if ( missing_file_dl_restart_enabled && type == ET_FILE_MISSING ){
            	
            		// prevent spamming alerts for things we are going to try and auto-recover
            		
            		Logger.log( new LogEvent( DiskManagerImpl.this, LOGID, LogEvent.LT_ERROR, msg ));
            		
            	}else{
                
            			// include download name so that alerts raised give some context
            		
            		msg = getDisplayName() + ": " + msg;
            		
            		Logger.log(new LogAlert(DiskManagerImpl.this, LogAlert.UNREPEATABLE, LogAlert.AT_ERROR, msg));
            	}
            	
                setErrorState( type, reason, cause );

                DiskManagerImpl.this.stop( false );
            }
        }.start();
    }

    @Override
    public void
    setFailedAndRecheck(
        DiskManagerFileInfo       file,
        String                    reason )
    {
            /**
             * need to run this on a separate thread to avoid deadlock with the stopping
             * process - setFailed tends to be called from within the read/write activities
             * and stopping these requires this.
             */

        new AEThread2("DiskManager:setFailed")
        {
            @Override
            public void
            run()
            {
                Logger.log(new LogAlert(DiskManagerImpl.this, LogAlert.UNREPEATABLE, LogAlert.AT_ERROR, reason));

                setErrorState( reason );

                DiskManagerImpl.this.stop( false );

                RDResumeHandler.recheckFile( download_manager, file );
            }
        }.start();
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
		if ( writer == null ){

			return( new long[]{ 0, 0, 0, 0 });
		}

		return( writer.getStats());
	}

	@Override
	public DMPieceMap
	getPieceMap()
	{
		DMPieceMap	map = piece_map_use_accessor;

		if ( map == null ){

			// System.out.println( "Creating piece list for " + new String( torrent.getName()));

			piece_map_use_accessor = map = piece_mapper.getPieceMap();
		}

		piece_map_use_accessor_time = SystemTime.getCurrentTime();

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

	public void
	checkFreePieceList(
		boolean	force_discard )
	{
		if ( piece_map_use_accessor == null ){

			return;
		}

		long now = SystemTime.getCurrentTime();

		if ( !force_discard ){

			if ( now < piece_map_use_accessor_time ){

				piece_map_use_accessor_time	= now;

				return;

			}else if ( now - piece_map_use_accessor_time < DM_FREE_PIECELIST_TIMEOUT ){

				return;
			}
		}

		// System.out.println( "Discarding piece list for " + new String( torrent.getName()));

		piece_map_use_accessor = null;
	}

    @Override
    public byte[]
    getPieceHash(
        int piece_number )

        throws TOTorrentException
    {
        return( torrent.getPieces()[ piece_number ]);
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
    public DiskManagerCheckRequest
    createCheckRequest(
        int     pieceNumber,
        Object  user_data )
    {
        return( checker.createCheckRequest( pieceNumber, user_data ));
    }

	@Override
	public boolean
	hasOutstandingCheckRequestForPiece(
		int		piece_number )
	{
		return( checker.hasOutstandingCheckRequestForPiece( piece_number ));
	}

    @Override
    public void
    enqueueCompleteRecheckRequest(
        DiskManagerCheckRequest             request,
        DiskManagerCheckRequestListener     listener )

    {
        checker.enqueueCompleteRecheckRequest( request, listener );
    }

    @Override
    public void
    enqueueCheckRequest(
        DiskManagerCheckRequest         request,
        DiskManagerCheckRequestListener listener )
    {
        checker.enqueueCheckRequest( request, listener );
    }

    @Override
    public int getCompleteRecheckStatus()
    {
      return ( checker.getCompleteRecheckStatus());
    }

    @Override
    public long[]
    getMoveProgress()
    {
    	if ( move_in_progress ){

    		return( move_progress );
    	}

    	return( null );
    }

    @Override
    public String 
    getMoveSubTask()
    {
    	if ( move_in_progress ){

    		File f = move_subtask;
    		
    		if ( f != null ){
    			
    			return( f.getName());
    		}
    	}

    	return( "" );
    }
    
    @Override
    public void 
    setMoveState(
    	int state)
    {
    	if ( move_in_progress ){
    		
    		move_state	= state;
    		
    	}else{
    		
    		move_state	= ProgressListener.ST_NORMAL;
    	}
    }
    
	@Override
	public void
	setPieceCheckingEnabled(
		boolean		enabled )
	{
		checking_enabled = enabled;

		checker.setCheckingEnabled( enabled );
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
    public DiskManagerWriteRequest
    createWriteRequest(
        int                 pieceNumber,
        int                 offset,
        DirectByteBuffer    data,
        Object              user_data )
    {
        return( writer.createWriteRequest( pieceNumber, offset, data, user_data ));
    }

    @Override
    public void
    enqueueWriteRequest(
        DiskManagerWriteRequest         request,
        DiskManagerWriteRequestListener listener )
    {
        writer.writeBlock( request, listener );
    }

	@Override
	public boolean
	hasOutstandingWriteRequestForPiece(
		int		piece_number )
	{
		return( writer.hasOutstandingWriteRequestForPiece( piece_number ));
	}

    @Override
    public boolean
    checkBlockConsistencyForWrite(
    	String				originator,
        int 				pieceNumber,
        int 				offset,
        DirectByteBuffer 	data )
    {
        if (pieceNumber < 0) {
            if (Logger.isEnabled())
                Logger.log(new LogEvent(this, LOGID, LogEvent.LT_ERROR,
                        "Write invalid: " + originator + " pieceNumber=" + pieceNumber + " < 0"));
            return false;
        }
        if (pieceNumber >= this.nbPieces) {
            if (Logger.isEnabled())
                Logger.log(new LogEvent(this, LOGID, LogEvent.LT_ERROR,
                        "Write invalid: " + originator + " pieceNumber=" + pieceNumber + " >= this.nbPieces="
                                + this.nbPieces));
            return false;
        }
        int length = this.pieceLength;
        if (pieceNumber == nbPieces - 1) {
            length = this.lastPieceLength;
        }
        if (offset < 0) {
            if (Logger.isEnabled())
                Logger.log(new LogEvent(this, LOGID, LogEvent.LT_ERROR,
                        "Write invalid: " + originator + " offset=" + offset + " < 0"));
            return false;
        }
        if (offset > length) {
            if (Logger.isEnabled())
                Logger.log(new LogEvent(this, LOGID, LogEvent.LT_ERROR,
                        "Write invalid: " + originator + " offset=" + offset + " > length=" + length));
            return false;
        }
        int size = data.remaining(DirectByteBuffer.SS_DW);
        if (size <= 0) {
            if (Logger.isEnabled())
                Logger.log(new LogEvent(this, LOGID, LogEvent.LT_ERROR,
                        "Write invalid: " + originator + " size=" + size + " <= 0"));
            return false;
        }
        if (offset + size > length) {
            if (Logger.isEnabled())
                Logger.log(new LogEvent(this, LOGID, LogEvent.LT_ERROR,
                        "Write invalid: " + originator + " offset=" + offset + " + size=" + size + " > length="
                                + length));
            return false;
        }
        return true;
    }

	@Override
	public boolean
	checkBlockConsistencyForRead(
		String	originator,
	    boolean	peer_request,
	    int 	pieceNumber,
	    int 	offset,
	    int 	length )
	{
		return( DiskManagerUtil.checkBlockConsistencyForRead(this, originator, peer_request, pieceNumber, offset, length));
	}

	@Override
	public boolean
	checkBlockConsistencyForHint(
		String	originator,
	    int 	pieceNumber,
	    int 	offset,
	    int 	length )
	{
		return( DiskManagerUtil.checkBlockConsistencyForHint(this, originator, pieceNumber, offset, length));
	}

    @Override
    public void
    saveResumeData(
        boolean interim_save )

        throws Exception
    {
        resume_handler.saveResumeData( interim_save );
    }

    @Override
    public DownloadEndedProgress 
    downloadEnded() 
    {
        DownloadEndedProgressImpl progress = new DownloadEndedProgressImpl();

        AEThread2.createAndStartDaemon( "DownloadEnded", ()->{
        	
        		moveDownloadFilesWhenEndedOrRemoved( false, true, progress );
        	});
        
        return( progress );
    }

    @Override
    public void 
    downloadRemoved() 
    {
        moveDownloadFilesWhenEndedOrRemoved( true, true, new DownloadEndedProgressImpl());
    }

    private void
    moveDownloadFilesWhenEndedOrRemoved(
    	boolean 					removing,
    	boolean 					torrent_file_exists,
    	DownloadEndedProgressImpl	progress )
    {
      try {
        start_stop_mon.enter();

        final boolean ending = !removing; // Just a friendly alias.

        /**
         * It doesn't matter if we set alreadyMoved, but don't end up moving the files.
         * This is because we only get called once (when it matters), which is when the
         * download has finished. We only want this to apply when the download has finished,
         * not if the user restarts the (already completed) download.
         */
                
        if ( ending ){
        	
            if ( this.alreadyMoved ){
            	
            	progress.setComplete();
            	
            	return;
            }
            
            this.alreadyMoved = true;
        }

        
        
        if ( removing ){
        	
        	SaveLocationChange remove_details = DownloadManagerMoveHandler.onRemoval(this.download_manager);

        	if ( remove_details != null ){
        		
        		moveDownloadFilesWhenEndedOrRemoved0( remove_details, progress );
        		
        	}else{
        		
        		progress.setComplete();
        	}
        }else{
        	
        	boolean[] delegated = { false };
        	
       		DownloadManagerMoveHandler.onCompletion(
        		this.download_manager,
        		new DownloadManagerMoveHandler.MoveCallback()
        		{
        			@Override
			        public void
        			perform(
        				SaveLocationChange move_details )
        			{
        				delegated[0] = true;
        				
        				moveDownloadFilesWhenEndedOrRemoved0( move_details, progress );
        			}
        		});

        	if ( !delegated[0] ){
        	
        		progress.setComplete();
        	}
        }

        return;

      }finally{

          start_stop_mon.exit();

          if (!removing) {
        	  
              try{
            	  if ( !download_manager.isDestroyed()){
                  
					saveResumeData(false);
            	  }
              }catch( Throwable e ){
            	  
                  setFailed( DiskManager.ET_OTHER, "Resume data save fails", e );
              }
          }
      }
    }
    
    private void
    moveDownloadFilesWhenEndedOrRemoved0(
   		SaveLocationChange 			loc_change,
   		DownloadEndedProgressImpl	progress )	
    {
    	Runnable target = ()->{
    		try{
    			moveFiles( loc_change, true );
    			
    		}finally{
 
    			progress.setComplete();
    		}
    	};
    	
    	File destination = loc_change.download_location;
    	
    	if ( destination == null ){
    	
    		destination = download_manager.getAbsoluteSaveLocation();
    	}
    	
    	try{
    		DiskManagerUtil.runMoveTask( download_manager, destination, target, this );
    		
    	}catch( Throwable e ){
    		
    		progress.setComplete();
    		
    		Debug.out( e );
    	}
    }

    @Override
    public void
    moveDataFiles(
    	File 				new_parent_dir,
    	String 				new_name )
    {
    	SaveLocationChange loc_change = new SaveLocationChange();

    	loc_change.download_location 	= new_parent_dir;
    	loc_change.download_name 		= new_name;

    	moveFiles( loc_change, false );
    }

    protected void
    moveFiles(
    	SaveLocationChange 				loc_change,
    	boolean 						change_to_read_only )
    {
    	boolean move_files = false;
    	
    	if ( loc_change.hasDownloadChange()){
    		
    		move_files = !this.isFileDestinationIsItself( loc_change );
    	}

        try{
            start_stop_mon.enter();

            /**
             * The 0 suffix is indicate that these are quite internal, and are
             * only intended for use within this method.
             */
            boolean files_moved = true;
            
            if ( move_files ){
            	
            	try{
            		move_progress		= new long[2];
            		move_subtask		= null;
            		move_state			= ProgressListener.ST_NORMAL;
            		move_in_progress 	= true;
            		
            		files_moved = moveDataFiles0(loc_change, change_to_read_only );

            	}finally{

            		move_in_progress 	= false;
            		move_subtask		= null;
            		move_state			= ProgressListener.ST_NORMAL;
            	}
            }

            if (loc_change.hasTorrentChange() && ( files_moved || !move_files )){
            	
                moveTorrentFile(loc_change);
            }
        }catch( Throwable e ){
        	
            Debug.printStackTrace(e);
            
        }finally{

        	start_stop_mon.exit();
        }
  }

  // Helper function
  private void logMoveFileError(File destination_path, String message) {
	  FileUtil.log( download_manager.getDisplayName() + ": failed to move to " + destination_path + ", " + message );
      Logger.log(new LogEvent(this, LOGID, LogEvent.LT_ERROR, message));
      Logger.logTextResource(new LogAlert(this, LogAlert.REPEATABLE,
                      LogAlert.AT_ERROR, "DiskManager.alert.movefilefails"),
                      new String[] {destination_path.toString(), message});
  }

  private boolean isFileDestinationIsItself(SaveLocationChange loc_change) {
	  File old_location = download_manager.getAbsoluteSaveLocation();
	  File new_location = loc_change.normaliseDownloadLocation(old_location);
	  try {
		  old_location = old_location.getCanonicalFile();
		  new_location = new_location.getCanonicalFile();
          if (old_location.equals(new_location)) {return true;}
          if (!download_manager.getTorrent().isSimpleTorrent() && FileUtil.isAncestorOf(new_location, old_location)) {
       		String msg = "Target is sub-directory of files";
       		logMoveFileError(new_location, msg);
       		return true;
          }
	  }
	  catch (Throwable e) {
		  Debug.out(e);
	  }
	  return false;
  }

  	  public long garbage;
  	  
	  private boolean 
	  moveDataFiles0(
		  SaveLocationChange 			loc_change, 
		  final boolean 				change_to_read_only )
	  
		  throws Exception  
	  {
	
		  // there is a time race condition here between a piece being marked as complete and the
		  // associated file actions being taken (switch to read only, do the 'incomplete file suffix' nonsense)
		  // and the peer controller noting that the download is complete and kicking off these actions.
		  // in order to ensure that the completion actions are done prior to us running here we do:
			  
		  synchronized( file_piece_lock ){
			
			  	// I don't think the VM can remove 'unnecessary' synchronized blocks but in case try
			  	// and do something that isn't obviously pointless
			  
			  garbage += remaining_excluding_dnd;
		  }

		  final File current_save_location = download_manager.getAbsoluteSaveLocation();
		  
		  final File move_to_dir = 
				loc_change.download_location == null?
						current_save_location.getParentFile(): 
						loc_change.download_location;

		  final String new_name = loc_change.download_name;
	
		  // consider the two cases:
		  //		simple torrent:  /temp/simple.avi
		  // 		complex torrent: /temp/complex[/other.avi]
	
		  // we are moving the files to the "move_to_arg" /M and possibly renaming to "wibble.x"
		  //		/temp/simple.avi, null	->  /M/simple.avi
		  //		/temp, "wibble.x"		->	/M/wibble.x
	
		  //		/temp/complex[/other.avi], null		->	/M/complex[/other.avi]
		  //		/temp, "wibble.x"					->	/M/wibble.x[/other.avi]
	
	
		  if ( files == null ){return false;}
	
		  if (isFileDestinationIsItself(loc_change)) {return false;}
	
		  String log_str = "Move active \"" + download_manager.getDisplayName() + "\" from  " + current_save_location + " to " + move_to_dir;
		  
		  int	files_accepted 		= 0;
		  int	files_skipped		= 0;
		  int	files_done			= 0;
		  long	total_size_bytes	= 0;
		  long	total_done_bytes	= 0;
		  
		  try{
			  FileUtil.log( log_str + " starts" );
			  
			  reader.setSuspended( true );
			  
			  boolean simple_torrent = download_manager.getTorrent().isSimpleTorrent();
	
			  // absolute save location does not follow links
			  // 		for simple: /temp/simple.avi
			  //		for complex: /temp/complex
	
			  final File save_location = current_save_location;
	
			  // It is important that we are able to get the canonical form of the directory to
			  // move to, because later code determining new file paths will break otherwise.
	
			  final String move_from_name	= save_location.getName();
			  final File move_from_dir	= save_location.getParentFile().getCanonicalFile();
	
			  final File[]    new_files   = new File[files.length];
	
			  File[]    old_files   = new File[files.length];
			  boolean[] link_only   = new boolean[files.length];
		
			  final long[]	file_lengths_to_move	 	= new long[files.length];
	
			  for (int i=0; i < files.length; i++) {
	
				  File old_file = files[i].getFile(false);
	
				  File linked_file = FMFileManagerFactory.getSingleton().getFileLink( torrent, i, old_file );
	
				  if ( !linked_file.equals(old_file)){
	
					  if ( simple_torrent ){
	
						  // simple torrent, only handle a link if its a simple rename
	
						  if ( linked_file.getParentFile().getCanonicalPath().equals( save_location.getParentFile().getCanonicalPath())){
	
							  old_file  = linked_file;
	
						  }else{
							  
							  FileUtil.log( "File linkage prohibits move: " + linked_file.getCanonicalPath() + " / " + save_location.getCanonicalPath());
							  
							  link_only[i] = true;
						  }
	
					  }else{
						  // if we are linked to a file outside of the torrent's save directory then we don't
						  // move the file
	
						  if ( linked_file.getCanonicalPath().startsWith( save_location.getCanonicalPath())){
	
							  old_file  = linked_file;
	
						  }else{
	
							  FileUtil.log( "File linkage prohibits move: " + linked_file.getCanonicalPath() + " / " + save_location.getCanonicalPath());

							  link_only[i] = true;
						  }
					  }
				  }
	
				  /**
				   * We are trying to calculate the relative path of the file within the original save
				   * directory, and then use that to calculate the new save path of the file in the new
				   * save directory.
				   *
				   * We have three cases which we may deal with:
				   *   1) Where the file in the torrent has never been moved (therefore, old_file will
				   *      equals linked_file),
				   *   2) Where the file in the torrent has been moved somewhere elsewhere inside the save
				   *      path (old_file will not equal linked_file, but we will overwrite the value of
				   *      old_file with linked_file),
				   *   3) Where the file in the torrent has been moved outside of the download path - meaning
				   *      we set link_only[i] to true. This is just to update the internal reference of where
				   *      the file should be - it doesn't move the file at all.
				   *
				   * Below, we will determine a new path for the file, but only in terms of where it should be
				   * inside the new download save location - if the file currently exists outside of the save
				   * location, we will not move it.
				   */
	
				  old_files[i] = old_file;
	
				  /**
				   * move_from_dir should be canonical (see earlier code).
				   *
				   * Need to get canonical form of the old file, because that's what we are using for determining
				   * the relative path.
				   */
	
					File old_parent = old_file.getParentFile();
	
	
				  /**
				   * Calculate the sub path of where the file lives compared to the new save location.
				   */

					String sub_path = FileUtil.getRelativePath(move_from_dir, old_parent);

					if ( sub_path == null ){

						logMoveFileError(move_to_dir,
								"Could not determine relative path for file - " + old_parent);

						throw new IOException(
								"relative path assertion failed: move_from_dir=\""
										+ move_from_dir + "\", old_parent_path=\"" + old_parent
										+ "\"");
				  }
	
				  //create the destination dir
	
				  // We may be doing a rename, and if this is a simple torrent, we have to keep the names in sync.
	
				  File new_file;
	
				  if ( new_name == null ){
	
					  new_file = FileUtil.newFile( FileUtil.newFile( move_to_dir, sub_path ), old_file.getName());
	
				  }else{
	
					  // renaming
	
					  if ( simple_torrent ){
	
						  new_file = FileUtil.newFile( FileUtil.newFile( move_to_dir, sub_path ), new_name );
	
					  }else{
	
						  // subpath includes the old dir name, replace this with new
	
						  int	pos = sub_path.indexOf( File.separator );
						  String	new_path;
						  if (pos == -1) {
							  new_path = new_name;
						  }
						  else {
							  // Assertion check.
							  String sub_sub_path = sub_path.substring(pos);
							  String expected_old_name = sub_path.substring(0, pos);
							  new_path = new_name + sub_sub_path;
							  boolean assert_expected_old_name = expected_old_name.equals(save_location.getName());
							  if (!assert_expected_old_name) {
								  Debug.out("Assertion check for renaming file in multi-name torrent " + (assert_expected_old_name ? "passed" : "failed") + "\n" +
										  "  Old parent path: " + old_parent + "\n" +
										  "  Subpath: " + sub_path + "\n" +
										  "  Sub-subpath: " + sub_sub_path + "\n" +
										  "  Expected old name: " + expected_old_name + "\n" +
										  "  Torrent pre-move name: " + save_location.getName() + "\n" +
										  "  New torrent name: " + new_name + "\n" +
										  "  Old file: " + old_file + "\n" +
										  "  Linked file: " + linked_file + "\n" +
										  "\n" +
										  "  Move-to-dir: " + move_to_dir + "\n" +
										  "  New path: " + new_path + "\n" +
										  "  Old file [name]: " + old_file.getName() + "\n"
										  );
							  }
						  }
	
	
						  new_file = FileUtil.newFile( FileUtil.newFile( move_to_dir, new_path ), old_file.getName());
					  }
				  }
	
				  new_files[i]  = new_file;
	
				  if ( link_only[i] ){
					  
					  files_skipped++;
					  
				  }else{
	
					  files_accepted++;
					  
					  total_size_bytes += file_lengths_to_move[i] = old_file.length();
	
					  if ( new_file.exists()){
	
						  String msg = "" + linked_file.getName() + " already exists in MoveTo destination dir";
	
						  Logger.log(new LogEvent(this, LOGID, LogEvent.LT_ERROR, msg));
	
						  Logger.logTextResource(new LogAlert(this, LogAlert.REPEATABLE,
								  LogAlert.AT_ERROR, "DiskManager.alert.movefileexists"),
								  new String[] { old_file.getName() });
	
	
						  Debug.out(msg);
	
						  return false;
					  }
	
					  FileUtil.mkdirs(new_file.getParentFile());
				  }
			  }
	
			  String	abs_path = move_to_dir.getAbsolutePath();
	
			  String	_average_config_key = null;
	
			  try{
				  _average_config_key = "dm.move.target.abps." + Base32.encode( abs_path.getBytes( "UTF-8" ));
	
			  }catch( Throwable e ){
	
				  Debug.out(e );
			  }
	
			  final String average_config_key	= _average_config_key;
				  
			  	// lazy here for rare case where all non-zero length files are links

			  long stats_total_bytes = total_size_bytes==0?1:total_size_bytes;
		
			  long	done_bytes = 0;
	
			  final Object	progress_lock = new Object();
			  final int[] 	current_file_index 	= { 0 };
			  final long[]	current_file_bs		= { 0 };
	
			  final long[]	last_progress_bytes		= { 0 };
			  final long[]	last_progress_update 	= { SystemTime.getMonotonousTime() };
	
			  final boolean[]	move_failed = { false };
	
			  TimerEventPeriodic timer_event1 =
				  SimpleTimer.addPeriodicEvent(
					  "MoveFile:speedster",
					  1000,
					  new TimerEventPerformer()
					  {
						  private final long	start_time = SystemTime.getMonotonousTime();

						  private long	last_update_processed;

						  private long	estimated_speed = 1*1024*1024;	// 1MB/sec default

						  {
							  if ( average_config_key != null ){

								  long val = COConfigurationManager.getLongParameter( average_config_key, 0 );

								  if ( val > 0 ){

									  estimated_speed = val;
								  }
							  }
						  }

						  @Override
						  public void
						  perform(
								  TimerEvent event )
						  {
							  synchronized( progress_lock ){

								  if ( move_failed[0] ){

									  return;
								  }

								  int file_index = current_file_index[0];

								  if ( file_index >= new_files.length ){

									  return;
								  }

								  long 	now			= SystemTime.getMonotonousTime();

								  long	last_update = last_progress_update[0];
								  long	bytes_moved = last_progress_bytes[0];

								  if ( last_update != last_update_processed ){

									  last_update_processed = last_update;

									  if ( bytes_moved > 10*1024*1024 ){

										  // a usable amount of progress

										  long	elapsed = now - start_time;

										  estimated_speed = ( bytes_moved * 1000 ) / elapsed;

										  // System.out.println( "estimated speed: " + estimated_speed );
									  }
								  }

								  long	secs_since_last_update  = ( now - last_update ) / 1000;

								  if ( secs_since_last_update > 2 ){

									  // looks like we're not getting useful updates, add some in based on
									  // elapsed time and average rate

									  long	file_start_overall		= current_file_bs[0];
									  long	file_end_overall 		= file_start_overall + file_lengths_to_move[ file_index ];
									  long	bytes_of_file_remaining	= file_end_overall - bytes_moved;

									  long	pretend_bytes = 0;

									  long	current_speed	 	= estimated_speed;
									  long	current_remaining	= bytes_of_file_remaining;
									  long	current_added		= 0;

									  int		percentage_to_slow_at	= 80;

									  // System.out.println( "injection pretend progress" );

									  for (int i=0;i<secs_since_last_update;i++){

										  current_added += current_speed;
										  pretend_bytes += current_speed;

										  // System.out.println( "    pretend=" + pretend_bytes + ", rate=" + percentage_to_slow_at + ", speed=" + current_speed );

										  if ( current_added > percentage_to_slow_at*current_remaining/100 ){

											  percentage_to_slow_at = 50;

											  current_speed = current_speed / 2;

											  current_remaining = bytes_of_file_remaining - pretend_bytes;

											  current_added = 0;

											  if ( current_speed < 1024 ){

												  current_speed = 1024;
											  }
										  }

										  if ( pretend_bytes >= bytes_of_file_remaining ){

											  pretend_bytes = bytes_of_file_remaining;

											  break;
										  }
									  }

									  long	pretend_bytes_moved = bytes_moved + pretend_bytes;

									  move_progress = new long[]{ (int)( 1000*pretend_bytes_moved/stats_total_bytes), stats_total_bytes };

									  // System.out.println( "pretend prog: " + move_progress );
								  }
							  }
						  }
					  });
	
			  TimerEventPeriodic timer_event2 =
				  SimpleTimer.addPeriodicEvent(
					  "MoveFile:observer",
					  500,
					  new TimerEventPerformer()
					  {
						  @Override
						  public void
						  perform(
								  TimerEvent event )
						  {
							  int			index;
							  File		file;

							  synchronized( progress_lock ){

								  if ( move_failed[0] ){

									  return;
								  }

								  index = current_file_index[0];

								  if ( index >= new_files.length ){

									  return;
								  }

								  // unfortunately file.length() blocks on my NAS until the operation is complete :(

								  file = new_files[index];
							  }

							  long	file_length = file.length();

							  synchronized( progress_lock ){

								  if ( move_failed[0] ){

									  return;
								  }

								  if ( index == current_file_index[0]){

									  long	done_bytes = current_file_bs[0] + file_length;

									  move_progress = new long[]{ (int)( 1000*done_bytes/stats_total_bytes), stats_total_bytes };

									  last_progress_bytes[0]	= done_bytes;
									  last_progress_update[0]	= SystemTime.getMonotonousTime();
								  }
							  }
						  }
					  });
	
			  long	start = SystemTime.getMonotonousTime();
	
			  File	old_root_dir;
			  File	new_root_dir;
	
			  if ( simple_torrent ){
	
				  old_root_dir = move_from_dir;
				  new_root_dir = FileUtil.newFile(move_to_dir);
	
			  }else{
	
				  old_root_dir = FileUtil.newFile(move_from_dir, move_from_name);
				  new_root_dir = FileUtil.newFile(move_to_dir, (new_name==null?move_from_name:new_name ));
			  }
	
			  FileUtil.ProgressListener pl = 
					  new ProgressListener(){
	
				  @Override
				  public void setTotalSize(long size){
				  }
	
				  @Override
				  public void setCurrentFile(File file){
				  }
	
				  @Override
				  public int getState(){
					  return( move_state );
				  }
	
				  @Override
				  public void complete(){
				  }
	
				  @Override
				  public void bytesDone(long num){
				  }
			  };
			  
			  try{
				  for (int i=0; i < files.length; i++){
	
					  File new_file = new_files[i];
	
					  long initial_done_bytes = done_bytes;

					  try{
	
						  // one day we should move all this progress estimation crap into FileUtil but hey
	
						  move_subtask		=  old_files[i];
	
						  files[i].moveFile( new_root_dir, new_file, link_only[i], pl );
	
						  files_done++;
						  
						  total_done_bytes += file_lengths_to_move[i];
						  
						  synchronized( progress_lock ){
	
							  current_file_index[0] = i+1;
	
							  done_bytes = initial_done_bytes + file_lengths_to_move[i];
	
							  current_file_bs[0] = done_bytes;
	
							  move_progress = new long[]{ (int)( 1000*done_bytes/stats_total_bytes), stats_total_bytes };
	
							  last_progress_bytes[0]	= done_bytes;
							  last_progress_update[0]	= SystemTime.getMonotonousTime();
						  }
	
						  if ( change_to_read_only ){
	
							  files[i].setAccessMode(DiskManagerFileInfo.READ);
						  }
	
					  }catch( CacheFileManagerException e ){
	
						  synchronized( progress_lock ){
	
							  move_failed[0] = true;
						  }
	
	
						  String msg = "Failed to move " + old_files[i].toString() + " to destination " + new_root_dir + ": " + new_file + "/" + link_only[i];
	
						  Logger.log(new LogEvent(this, LOGID, LogEvent.LT_ERROR, msg));
	
						  Logger.logTextResource(new LogAlert(this, LogAlert.REPEATABLE,
								  LogAlert.AT_ERROR, "DiskManager.alert.movefilefails"),
								  new String[] { old_files[i].toString(),
										  Debug.getNestedExceptionMessage(e) });
	
						  // try some recovery by moving any moved files back...
							  
						  long	bytes_moved = 0;
						  
						  for (int j=0;j<i;j++){
						  
							  bytes_moved += file_lengths_to_move[j];
						  }
						  					  
						  for (int j=0;j<i;j++){
	
							  move_subtask		=  old_files[j];
							  move_progress 	= new long[]{ (int)( 1000*bytes_moved/stats_total_bytes), stats_total_bytes };
								
  						  	  long bytes_this_file =  file_lengths_to_move[j];

  						  	  final long bytes_moved_at_start = bytes_moved;
  						  	  
  						  	  FileUtil.ProgressListener pl_undo = 
  						  		  new ProgressListener(){

	  						  		  private long total_done = 0;
	
	  						  		  @Override
	  						  		  public void setTotalSize(long size){
	  						  		  }
	
	  						  		  @Override
	  						  		  public void setCurrentFile(File file){
	  						  		  }
	
	  						  		  @Override
	  						  		  public int getState(){
	  						  			  return( ST_NORMAL );
	  						  		  }
	
	  						  		  @Override
	  						  		  public void complete(){
	  						  		  }
	
	  						  		  @Override
	  						  		  public void bytesDone(long num){
	  						  			total_done += num;
	
	  						  			  if ( total_done > bytes_this_file){
	
	  						  				  total_done = bytes_this_file;
	  						  			  }
	
	  						  			  move_progress = new long[]{ (int)( 1000*(bytes_moved_at_start-total_done)/stats_total_bytes), stats_total_bytes }; 
	  						  		  }
	  						  	  };
							  
							  try{
								  files[j].moveFile( old_root_dir, old_files[j], link_only[j], pl_undo );
	
							  }catch( Throwable f ){
	
								  Logger.logTextResource(new LogAlert(this, LogAlert.REPEATABLE,
										  LogAlert.AT_ERROR,
										  "DiskManager.alert.movefilerecoveryfails"),
										  new String[] { old_files[j].toString(),
												  Debug.getNestedExceptionMessage(f) });
	
							  }
							  
							  bytes_moved -= bytes_this_file;
						  }
	
						  if ( new_root_dir.isDirectory()){
	
							  TorrentUtils.recursiveEmptyDirDelete( new_root_dir, false );
						  }
	
						  return false;
					  }
				  }
			  }finally{
	
				  timer_event1.cancel();
				  timer_event2.cancel();
			  }
	
			  long	elapsed_secs = ( SystemTime.getMonotonousTime() - start )/1000;
	
			  if ( total_size_bytes > 10*1024*1024 && elapsed_secs > 10 ){
	
				  long	bps = total_size_bytes / elapsed_secs;
	
				  if ( average_config_key != null ){
	
					  COConfigurationManager.setParameter( average_config_key, bps );
				  }
			  }
	
			  //remove the old dir
	
			  if (  save_location.isDirectory()){
	
				  TorrentUtils.recursiveEmptyDirDelete( save_location, false );
			  }
	
			  // NOTE: this operation FIXES up any file links
	
			  if ( new_name == null ){

					download_manager.setTorrentSaveDir(FileUtil.newFile(move_to_dir), false);

				}else{

					download_manager.setTorrentSaveDir(FileUtil.newFile(move_to_dir,
						new_name), true);
				}
	
			  return true;
	
		  }finally{
	
			  reader.setSuspended( false );
			  			  
			  FileUtil.log( 
						 log_str + 
						 	" ends (files accepted=" + files_accepted + 
						 	", skipped=" + files_skipped + ", done=" + files_done +
						 	"; bytes total=" + total_size_bytes + ", done=" + total_done_bytes + ")");
		  }
	  }

    private void 
    moveTorrentFile(
    	SaveLocationChange loc_change) 
    {
    	if (!loc_change.hasTorrentChange()) {return;}

		File old_torrent_file = FileUtil.newFile(download_manager.getTorrentFileName());
		File new_torrent_file = loc_change.normaliseTorrentLocation(old_torrent_file);

		if (!old_torrent_file.exists()) {
            // torrent file's been removed in the meantime, just log a warning
            if (Logger.isEnabled())
                  Logger.log(new LogEvent(this, LOGID, LogEvent.LT_WARNING, "Torrent file '" + old_torrent_file.getPath() + "' has been deleted, move operation ignored" ));
            return;
		}

    	try{
    		
    		download_manager.setTorrentFile(loc_change.torrent_location, loc_change.torrent_name);
    		
    	}catch (DownloadManagerException e) {
            String msg = "Failed to move " + old_torrent_file.toString() + " to " + new_torrent_file.toString();

            if (Logger.isEnabled())
                Logger.log(new LogEvent(this, LOGID, LogEvent.LT_ERROR, msg));

            Logger.logTextResource(new LogAlert(this, LogAlert.REPEATABLE,
                            LogAlert.AT_ERROR, "DiskManager.alert.movefilefails"),
                            new String[] { old_torrent_file.toString(),
                                    new_torrent_file.toString() });

            Debug.out(msg);
       	}
    }

    @Override
    public TOTorrent
    getTorrent()
    {
        return( torrent );
    }


    @Override
    public void
    addListener(
        DiskManagerListener l )
    {
        listeners.addListener( l );

        int params[] = {getState(), getState()};

        listeners.dispatch( l, LDT_STATECHANGED, params);
    }

    @Override
    public void
    removeListener(
        DiskManagerListener l )
    {
        listeners.removeListener(l);
    }

    @Override
    public boolean
    hasListener(
    	DiskManagerListener	l )
    {
    	return( listeners.hasListener( l ));
    }

          /** Deletes all data files associated with torrent.
           * Currently, deletes all files, then tries to delete the path recursively
           * if the paths are empty.  An unexpected result may be that a empty
           * directory that the user created will be removed.
           *
           * TODO: only remove empty directories that are created for the torrent
           */

    public static void
    deleteDataFiles(
        TOTorrent   torrent,
        String      torrent_save_dir,       // enclosing dir, not for deletion
        String      torrent_save_file, 		// file or dir for torrent
        boolean		force_no_recycle )
    {
        if (torrent == null || torrent_save_file == null ){

            return;
        }

        try{
            if (torrent.isSimpleTorrent()){

                File    target = FileUtil.newFile( torrent_save_dir, torrent_save_file );

                target = FMFileManagerFactory.getSingleton().getFileLink( torrent, 0, target.getCanonicalFile());

                FileUtil.deleteWithRecycle( target, force_no_recycle );

            }else{

            	PlatformManager mgr = PlatformManagerFactory.getPlatformManager();
            	
            		// parg - not sure what this special case code is doing here for OSX but I guess we'll
            		// keep it for the moment...
            	
            	boolean deleted = false;
            	
            	if (	 Constants.isOSX &&
            			torrent_save_file.length() > 0 &&
            			COConfigurationManager.getBooleanParameter("Move Deleted Data To Recycle Bin" ) &&
            			(! force_no_recycle ) &&
            			mgr.hasCapability(PlatformManagerCapabilities.RecoverableFileDelete )){

            			
        			String  dir = torrent_save_dir + File.separatorChar + torrent_save_file + File.separatorChar;

        				// only delete the dir if there's only this torrent's files in it!

        			int numDataFiles = countDataFiles( torrent, torrent_save_dir, torrent_save_file );
        			
        			if ( countFiles( FileUtil.newFile(dir), numDataFiles) == numDataFiles){

        				if ( FileUtil.deleteWithRecycle( FileUtil.newFile( dir ), false )){
        					
        					deleted = true;
        				}
        			}
            	}
            	
    			if ( !deleted ){
    					
    				deleteDataFileContents( torrent, torrent_save_dir, torrent_save_file, force_no_recycle );
    			}
            }
        }catch( Throwable e ){

            Debug.printStackTrace( e );
        }
    }

    private static int
    countFiles(
        File    f,
        int stopAfterCount )
    {
        if ( f.isFile()){

            return( 1 );
        }else{

            int res = 0;

            File[]  files = f.listFiles();

            if ( files != null ){

                for (int i=0;i<files.length;i++){

                    res += countFiles( files[i], stopAfterCount );

                    if (res > stopAfterCount) {
                    	break;
                    }
                }
            }

            return( res );
        }
    }

	/**
	 * @note: Only used on OSX
	 */
    private static int
    countDataFiles(
        TOTorrent torrent,
        String torrent_save_dir,
        String torrent_save_file )
    {
        try{
            int res = 0;

            LocaleUtilDecoder locale_decoder = LocaleTorrentUtil.getTorrentEncoding( torrent );

            TOTorrentFile[] files = torrent.getFiles();

            for (int i=0;i<files.length;i++){

                byte[][]path_comps = files[i].getPathComponents();

                File file = FileUtil.newFile(torrent_save_dir, torrent_save_file);

                for (int j=0;j<path_comps.length;j++){

                    String comp = locale_decoder.decodeString( path_comps[j] );

                    comp = FileUtil.convertOSSpecificChars( comp, j != path_comps.length-1 );

                    file = FileUtil.newFile( file, comp );
                }

                file = file.getCanonicalFile();

                File linked_file = FMFileManagerFactory.getSingleton().getFileLink( torrent, i, file );

                boolean skip = false;

                if ( linked_file != file ){

                    if ( !linked_file.getCanonicalPath().startsWith(FileUtil.newFile( torrent_save_dir ).getCanonicalPath())){

                        skip = true;
                    }
                }

                if ( !skip && file.exists() && !file.isDirectory()){

                    res++;
                }
            }

            return( res );

        }catch( Throwable e ){

            Debug.printStackTrace(e);

            return( -1 );
        }
    }

    private static void
    deleteDataFileContents(
        TOTorrent torrent,
        String torrent_save_dir,
        String 		torrent_save_file,
        boolean		force_no_recycle )

            throws TOTorrentException, UnsupportedEncodingException, LocaleUtilEncodingException
    {
			LocaleUtilDecoder locale_decoder = LocaleTorrentUtil.getTorrentEncoding( torrent );

			TOTorrentFile[] files = torrent.getFiles();

			File root_path_file = FileUtil.newFile( torrent_save_dir, torrent_save_file );
			String root_full_path;
			try {
				root_full_path = root_path_file.getCanonicalPath();
			}catch( Throwable e ){

				Debug.printStackTrace(e);
				return;
			}

			FMFileManager fm_factory = FMFileManagerFactory.getSingleton();
			boolean has_links = fm_factory.hasLinks(torrent);
	
			if (!has_links) {
				if	(!root_path_file.isDirectory()) {
					return;
				}
				if (root_path_file.list().length == 0) {
					TorrentUtils.recursiveEmptyDirDelete(root_path_file);
					return;
				}
			}


        boolean delete_if_not_in_dir = COConfigurationManager.getBooleanParameter("File.delete.include_files_outside_save_dir");

        // delete all files, then empty directories

        for (int i=0;i<files.length;i++){

            byte[][]path_comps = files[i].getPathComponents();

            File  file    = root_path_file;

            for (int j=0;j<path_comps.length;j++){

                try{

                    String comp = locale_decoder.decodeString( path_comps[j] );

                    comp = FileUtil.convertOSSpecificChars( comp, j != path_comps.length-1 );

                    file = FileUtil.newFile( file, comp);

                }catch( UnsupportedEncodingException e ){

                    Debug.out( "file - unsupported encoding!!!!");
                }
            }

            boolean delete;

            if (has_links) {
            	File linked_file = fm_factory.getFileLink( torrent, i, file );

            	if ( linked_file == file ){

            		delete  = true;

            	}else{

            		// only consider linked files for deletion if they are in the torrent save dir
            		// i.e. a rename probably instead of a retarget to an existing file elsewhere
            		// delete_if_not_in_dir does allow this behaviour to be overridden though.

            		try{
            			if ( delete_if_not_in_dir || linked_file.getCanonicalPath().startsWith(root_full_path)){

            				file    = linked_file;

            				delete  = true;

            			}else{

            				delete = false;
            			}
            		}catch( Throwable e ){

            			Debug.printStackTrace(e);

            			delete = false;
            		}
            	}
            } else {
            	delete = true;
            }

            if ( delete && file.exists() && !file.isDirectory()){

                try{
                    FileUtil.deleteWithRecycle( file, force_no_recycle );

                }catch (Exception e){

                    Debug.out(e.toString());
                }
            }
        }

        TorrentUtils.recursiveEmptyDirDelete(root_path_file);
    }

    @Override
    public void
    skippedFileSetChanged(
        DiskManagerFileInfo file )
    {
        skipped_file_set_changed    = true;
        if ( priority_change_marker.incrementAndGet() == 0 ){
        	priority_change_marker.incrementAndGet();
        }
        listeners.dispatch(LDT_PRIOCHANGED, file);
    }

    @Override
    public void
    priorityChanged(
        DiskManagerFileInfo file )
    {
        if ( priority_change_marker.incrementAndGet() == 0 ){
        	priority_change_marker.incrementAndGet();
        }
        listeners.dispatch(LDT_PRIOCHANGED, file);
    }

    @Override
    public void
    storageTypeChanged(
        DiskManagerFileInfo file )
    {
    		// hijacking priority change so we pick up switch between DND+Delete 'priorities'
        listeners.dispatch(LDT_PRIOCHANGED, file);
    }

  protected void
  storeFilePriorities()
  {
      storeFilePriorities( download_manager, files );
  }

  protected static void
  storeFilePriorities(
    DownloadManager         download_manager,
    DiskManagerFileInfo[]   files )
  {
	  DiskManagerUtil.storeFilePriorities ( download_manager, files );
  }

  protected static void
  storeFileDownloaded(
    DownloadManager         download_manager,
    DiskManagerFileInfo[]   files,
    boolean					persist,
    boolean					interim )
  {
      DownloadManagerState  state = download_manager.getDownloadState();

      Map   details = new HashMap();

      List  downloaded = new ArrayList();

      details.put( "downloaded", downloaded );

      for (int i=0;i<files.length;i++){

          downloaded.add( new Long( files[i].getDownloaded()));
      }

      state.setMapAttribute( DownloadManagerState.AT_FILE_DOWNLOADED, details );

      if ( persist ){

    	  state.save( interim );
      }
  }

  @Override
  public void
  saveState( boolean interim )
  {
	  saveStateSupport( true, interim );
  }

  protected void
  saveStateSupport(
	boolean	persist,
	boolean	interim )
  {
      if ( files != null ){

    	  if ( getState() == READY ){
        
    		  storeFileDownloaded( download_manager, files, persist, interim );
    	  }
    	  
    	  storeFilePriorities();
      }

      checkFreePieceList( false );
  }

  public DownloadManager getDownloadManager() {
    return download_manager;
  }

    @Override
    public String
    getInternalName()
    {
        return( download_manager.getInternalName());
    }

    @Override
    public DownloadManagerState
    getDownloadState()
    {
        return( download_manager.getDownloadState());
    }

    @Override
    public File
    getSaveLocation()
    {
        return( download_manager.getSaveLocation());
    }

    @Override
    public String[]
    getStorageTypes()
    {
        return( getStorageTypes( download_manager ));
    }

    @Override
    public String getStorageType(int fileIndex) {
    	return( getStorageType( download_manager , fileIndex));
    }

    	// Used by DownloadManagerImpl too.

	/**
	 * Returns the storage type for each file in {@link DownloadManager}.
	 * <p/>
	 * According to {@link DiskManagerUtil#convertDMStorageTypeFromString(String)}, String values are:<BR>
	 * "R" {@link DiskManagerFileInfo#ST_REORDER}<br>
	 * "L" {@link DiskManagerFileInfo#ST_LINEAR}<br>
	 * "C" {@link DiskManagerFileInfo#ST_COMPACT}<br>
	 * "X" {@link DiskManagerFileInfo#ST_REORDER_COMPACT}<br>
	 */
    public static String[] getStorageTypes(DownloadManager download_manager) {
        DownloadManagerState state = download_manager.getDownloadState();
        String[] types = state.getListAttribute(DownloadManagerState.AT_FILE_STORE_TYPES);
        if (types == null || types.length == 0) {
        	TOTorrentFile[] files = download_manager.getTorrent().getFiles();
            types = new String[download_manager.getTorrent().getFiles().length];

         	if ( reorder_storage_mode ){

        		int	existing = state.getIntAttribute( DownloadManagerState.AT_REORDER_MIN_MB );

        		if ( existing < 0 ){

        			existing = reorder_storage_mode_min_mb;

        			state.setIntAttribute( DownloadManagerState.AT_REORDER_MIN_MB, existing );
        		}

        		for (int i=0; i<types.length; i++){

            		if ( files[i].getLength()/(1024*1024) >= existing ){

            			types[i] = "R";

            		}else{

            			types[i] = "L";
            		}
            	}
          	}else{

         		for (int i=0; i<types.length; i++){

         			types[i] = "L";
         		}
            }

			state.setListAttribute(DownloadManagerState.AT_FILE_STORE_TYPES, types );
        }

        return( types );
    }

    	// Used by DownloadManagerImpl too.

	/**
	 * Returns the storage type for a {@link DownloadManager}'s file at <code>fileIndex</code>.
	 * <p/>
	 * According to {@link DiskManagerUtil#convertDMStorageTypeFromString(String)}, values are:<BR>
	 * "R" {@link DiskManagerFileInfo#ST_REORDER}<br>
	 * "L" {@link DiskManagerFileInfo#ST_LINEAR}<br>
	 * "C" {@link DiskManagerFileInfo#ST_COMPACT}<br>
	 * "X" {@link DiskManagerFileInfo#ST_REORDER_COMPACT}<br>
	 */
    public static String getStorageType(DownloadManager download_manager, int fileIndex) {
        DownloadManagerState state = download_manager.getDownloadState();
        String type = state.getListAttribute(DownloadManagerState.AT_FILE_STORE_TYPES,fileIndex);

        if ( type != null ){

        	return( type );
        }

        return( getStorageTypes( download_manager )[fileIndex]);
    }

    public static void
    setFileLinks(
        DownloadManager         download_manager,
        LinkFileMap    			links )
    {
        try{
            CacheFileManagerFactory.getSingleton().setFileLinks( download_manager.getTorrent(), links );

        }catch( Throwable e ){

            Debug.printStackTrace(e);
        }
    }

    /* (non-Javadoc)
     * @see com.biglybt.core.logging.LogRelation#getLogRelationText()
     */
    @Override
    public String getRelationText() {
        return "TorrentDM: '" + download_manager.getDisplayName() + "'";
    }


    /* (non-Javadoc)
     * @see com.biglybt.core.logging.LogRelation#queryForClass(java.lang.Class)
     */
    @Override
    public Object[] getQueryableInterfaces() {
        return new Object[] { download_manager, torrent };
    }

    @Override
    public DiskManagerRecheckScheduler
    getRecheckScheduler()
    {
        return( recheck_scheduler );
    }

    @Override
    public boolean isInteresting(int pieceNumber)
    {
        return pieces[pieceNumber].isInteresting();
    }

    @Override
    public boolean isDone(int pieceNumber)
    {
        return pieces[pieceNumber].isDone();
    }

	@Override
	public long
	getPriorityChangeMarker()
	{
		return( priority_change_marker.get());
	}

	@Override
	public void
	generateEvidence(
		IndentWriter		writer )
	{
		writer.println( "Disk Manager" );

		try{
			writer.indent();

			writer.println( "percent_done=" + percentDone +",allocated=" + allocated+",remaining="+ remaining);
			writer.println( "skipped_file_set_size=" + skipped_file_set_size + ",skipped_but_downloaded=" + skipped_but_downloaded );
			writer.println( "already_moved=" + alreadyMoved );
		}finally{

			writer.exdent();
		}
	}

	private static class
	DownloadEndedProgressImpl
		implements DownloadEndedProgress
	{
		private volatile boolean complete;
		
		private void
		setComplete()
		{
			complete = true;
		}
		
		@Override
		public boolean 
		isComplete()
		{
			return( complete );
		}
	}
}
