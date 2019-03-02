/*
 * Created on 1 Nov 2006
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


package com.biglybt.core.disk.impl;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.disk.*;
import com.biglybt.core.disk.impl.resume.RDResumeHandler;
import com.biglybt.core.diskmanager.cache.CacheFile;
import com.biglybt.core.diskmanager.cache.CacheFileManagerFactory;
import com.biglybt.core.diskmanager.cache.CacheFileOwner;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerException;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.download.DownloadManagerStats;
import com.biglybt.core.download.impl.DownloadManagerStatsImpl;
import com.biglybt.core.internat.LocaleTorrentUtil;
import com.biglybt.core.internat.LocaleUtilDecoder;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentFile;
import com.biglybt.core.util.*;

public class
DiskManagerUtil
{
    private static final LogIDs LOGID = LogIDs.DISK;

	protected static int        max_read_block_size;

	static{

		ParameterListener param_listener = new ParameterListener() {
			@Override
			public void
			parameterChanged(
					String  str )
			{
				max_read_block_size = COConfigurationManager.getIntParameter( "BT Request Max Block Size" );
			}
		};

		COConfigurationManager.addAndFireParameterListener( "BT Request Max Block Size", param_listener);
	}

	public static boolean
	checkBlockConsistencyForHint(
		DiskManager	dm,
		String		originator,
		int 		pieceNumber,
		int 		offset,
		int 		length)
	{
		if (length <= 0 ) {
			if (Logger.isEnabled())
				Logger.log(new LogEvent(dm, LOGID, LogEvent.LT_ERROR,
						"Hint invalid: " + originator + " length=" + length + " <= 0"));
			return false;
		}
		if (pieceNumber < 0) {
			if (Logger.isEnabled())
				Logger.log(new LogEvent(dm, LOGID, LogEvent.LT_ERROR,
						"Hint invalid: " + originator + " pieceNumber=" + pieceNumber + " < 0"));
			return false;
		}
		if (pieceNumber >= dm.getNbPieces()) {
			if (Logger.isEnabled())
				Logger.log(new LogEvent(dm, LOGID, LogEvent.LT_ERROR,
						"Hint invalid: " + originator + " pieceNumber=" + pieceNumber + " >= this.nbPieces="
						+ dm.getNbPieces()));
			return false;
		}
		int pLength = dm.getPieceLength(pieceNumber);

		if (offset < 0) {
			if (Logger.isEnabled())
				Logger.log(new LogEvent(dm, LOGID, LogEvent.LT_ERROR,
						"Hint invalid: " + originator + " offset=" + offset + " < 0"));
			return false;
		}
		if (offset > pLength) {
			if (Logger.isEnabled())
				Logger.log(new LogEvent(dm, LOGID, LogEvent.LT_ERROR,
						"Hint invalid: " + originator + " offset=" + offset + " > pLength=" + pLength));
			return false;
		}
		if (offset + length > pLength) {
			if (Logger.isEnabled())
				Logger.log(new LogEvent(dm, LOGID, LogEvent.LT_ERROR,
						"Hint invalid: " + originator + " offset=" + offset + " + length=" + length
						+ " > pLength=" + pLength));
			return false;
		}

		return true;
	}

	public static boolean
	checkBlockConsistencyForRead(
		DiskManager	dm,
		String		originator,
		boolean		peer_request,
		int 		pieceNumber,
		int 		offset,
		int 		length)
	{
		if ( !checkBlockConsistencyForHint( dm, originator, pieceNumber, offset, length )){

			return( false );
		}

		if (length > max_read_block_size && peer_request) {
			if (Logger.isEnabled())
				Logger.log(new LogEvent(dm, LOGID, LogEvent.LT_ERROR,
						"Read invalid: " + originator + " length=" + length + " > " + max_read_block_size));
			return false;
		}

		if(!dm.getPiece(pieceNumber).isDone()) {
			Logger.log(new LogEvent(dm, LOGID, LogEvent.LT_ERROR,
					"Read invalid: " + originator + " piece #" + pieceNumber + " not done"));
			return false;
		}

		return true;
	}

	public static void
	doFileExistenceChecks(
		DiskManagerFileInfoSet 	fileSet,
		boolean[] 				toCheck,
		DownloadManager 		dm,
		boolean 				allowAlloction )
	{
		DiskManagerFileInfo[] files = fileSet.getFiles();

		int lastPieceScanned = -1;
		int windowStart = -1;
		int windowEnd = -1;

		String[] types = DiskManagerImpl.getStorageTypes(dm);

		// sweep over all files to see if adjacent files of changed files can be deleted or need allocation
		for(int i = 0; i< files.length;i++)
		{
			int firstPiece = files[i].getFirstPieceNumber();
			int lastPiece = files[i].getLastPieceNumber();

			if(toCheck[i])
			{ // found a file that changed, scan adjacent files
				if(lastPieceScanned < firstPiece)
				{ // haven't checked the preceding files, slide backwards
					windowStart = firstPiece;
					while(i > 0 && files[i-1].getLastPieceNumber() >= windowStart)
						i--;
				}

				if(windowEnd < lastPiece)
					windowEnd = lastPiece;
			}

			if((windowStart <= firstPiece && firstPiece <= windowEnd) || (windowStart <= lastPiece && lastPiece <= windowEnd))
			{ // file falls in current scanning window, check it
				File currentFile = files[i].getFile(true);
				if(!RDResumeHandler.fileMustExist(dm, files[i]))
				{
					int	st = convertDMStorageTypeFromString( types[i] );
					if( st == DiskManagerFileInfo.ST_COMPACT || st == DiskManagerFileInfo.ST_REORDER_COMPACT ){
						currentFile.delete();
					}
				} else if(allowAlloction && !currentFile.exists())	{
					/*
					 * file must exist, does not exist and we probably just changed to linear
					 * mode, assume that (re)allocation of adjacent files is necessary
					 */
					dm.setDataAlreadyAllocated(false);
				}
				lastPieceScanned = lastPiece;
			}


		}
	}

	static final AEMonitor    cache_read_mon  = new AEMonitor( "DiskManager:cacheRead" );

	static boolean
	setFileLink(
	    DownloadManager         	download_manager,
	    DiskManagerFileInfo[]   	info,
	    DiskManagerFileInfo    		file_info,
	    File                   		from_file,
	    File                   		to_link,
	    FileUtil.ProgressListener	pl )
	{
	    if ( to_link != null ){

		    File    existing_file = file_info.getFile( true );

		    if ( to_link.equals( existing_file )){

		            // already pointing to the right place

		        return( true );
		    }

		    for (int i=0;i<info.length;i++){

		        if ( to_link.equals( info[i].getFile( true ))){

		            Logger.log(new LogAlert(download_manager, LogAlert.REPEATABLE, LogAlert.AT_ERROR,
		                            "Attempt to link to existing file '" + info[i].getFile(true)
		                                    + "'"));

		            return( false );
		        }
		    }

		    if ( to_link.exists()){

		        if ( !existing_file.exists()){

		                // using a new file, make sure we recheck

		            download_manager.recheckFile( file_info );

		        }else{

			        		// On OSX (at least) a rename of a file here all that has changed is the capitalisation results in us getting here
			        		// as the 'to_link.exists()' returns true for the file even though case differs
			        	
			        	if ( 	to_link.getParent().equals( existing_file.getParent()) &&
			        			to_link.getName().equalsIgnoreCase( existing_file.getName())){
			        		
			            if ( !FileUtil.renameFile( existing_file, to_link )){

			                Logger.log(new LogAlert(download_manager, LogAlert.REPEATABLE, LogAlert.AT_ERROR,
			                    "Failed to rename '" + existing_file.toString() + "'" ));

			                return( false );
			            }
			        	}else{
			        	
				        	Object	skip_delete = download_manager.getUserData( "set_link_dont_delete_existing" );
		
				        		// user is manually re-targetting a file - this hack is to stop us from deleting the old file which
				        		// we really have no right to go and delete
		
				        	if ( ( skip_delete instanceof Boolean ) && (Boolean)skip_delete ){
		
				        		 download_manager.recheckFile( file_info );
		
				        	}else{
		
				                if ( FileUtil.deleteWithRecycle(
				                		existing_file,
				                		download_manager.getDownloadState().getFlag( DownloadManagerState.FLAG_LOW_NOISE ))){
		
					                    // new file, recheck
		
					                download_manager.recheckFile( file_info );
		
					            }else{
		
					                Logger.log(new LogAlert(download_manager, LogAlert.REPEATABLE, LogAlert.AT_ERROR,
					                        "Failed to delete '" + existing_file.toString() + "'"));
		
					                return( false );
					            }
				        	}
			        }
		        }
		    }else{

		        if ( existing_file.exists()){

		            if ( !FileUtil.renameFile( existing_file, to_link, pl )){

		                Logger.log(new LogAlert(download_manager, LogAlert.REPEATABLE, LogAlert.AT_ERROR,
		                    "Failed to rename '" + existing_file.toString() + "'" ));

		                return( false );
		            }
		        }
		    }
	    }

	    DownloadManagerState    state = download_manager.getDownloadState();

	    state.setFileLink( file_info.getIndex(), from_file, to_link );

	    state.save();

	    return( true );
	}

	static abstract class FileSkeleton implements DiskManagerFileInfoHelper {
	    protected int     priority;
	    protected boolean skipped_internal;
		protected long    downloaded;

		protected abstract File
		setSkippedInternal( boolean skipped );
	}


	public static DiskManagerFileInfoSet
	getFileInfoSkeleton(
	    final DownloadManager       download_manager,
	    final DiskManagerListener   listener )
	{
	    final TOTorrent   torrent = download_manager.getTorrent();

	    if ( torrent == null ){

	        return( new DiskManagerFileInfoSetImpl(new DiskManagerFileInfoImpl[0],null) );
	    }

	    String  tempRootDir = download_manager.getAbsoluteSaveLocation().getParent();

	    if(tempRootDir == null) // in case we alraedy are at the root
	    	tempRootDir = download_manager.getAbsoluteSaveLocation().getPath();


	    if ( !torrent.isSimpleTorrent()){
	    	tempRootDir += File.separator + download_manager.getAbsoluteSaveLocation().getName();
	    }

	    tempRootDir    += File.separator;

	    	// prevent attempted state saves and associated nastyness during population of
	    	// the file skeleton entries

	    final boolean[] loading = { true };

	    try{
		    final String root_dir = StringInterner.intern(tempRootDir) ;

		    try{
		        final LocaleUtilDecoder locale_decoder = LocaleTorrentUtil.getTorrentEncoding( torrent );

		        TOTorrentFile[] torrent_files = torrent.getFiles();

		        final FileSkeleton[]   res = new FileSkeleton[ torrent_files.length ];

				final String incomplete_suffix = download_manager.getDownloadState().getAttribute( DownloadManagerState.AT_INCOMP_FILE_SUFFIX );

		        final DiskManagerFileInfoSet fileSetSkeleton = new DiskManagerFileInfoSet() {

					@Override
					public DiskManagerFileInfo[] getFiles() {
						return res;
					}

					@Override
					public int nbFiles() {
						return res.length;
					}

					@Override
					public void setPriority(int[] toChange) {
						if(toChange.length != res.length)
							throw new IllegalArgumentException("array length mismatches the number of files");

						for(int i=0;i<res.length;i++)
							res[i].priority = toChange[i];

						if ( !loading[0] ){

							DiskManagerImpl.storeFilePriorities( download_manager, res);
						}

						for(int i=0;i<res.length;i++)
							if(toChange[i] != 0 )
								listener.filePriorityChanged(res[i]);
					}

					@Override
					public void setSkipped(boolean[] toChange, boolean setSkipped) {
						if(toChange.length != res.length)
							throw new IllegalArgumentException("array length mismatches the number of files");

		        		if (!setSkipped ){
		    				String[] types = DiskManagerImpl.getStorageTypes(download_manager);

		    				boolean[]	toLinear 	= new boolean[toChange.length];
		    				boolean[]	toReorder 	= new boolean[toChange.length];

		    				int	num_linear 	= 0;
		    				int num_reorder	= 0;

		    				for ( int i=0;i<toChange.length;i++){

		    					if ( toChange[i] ){

		    						int old_type = DiskManagerUtil.convertDMStorageTypeFromString( types[i] );

		    						if ( old_type == DiskManagerFileInfo.ST_COMPACT ){

		    							toLinear[i] = true;

		    							num_linear++;

		    						}else if ( old_type == DiskManagerFileInfo.ST_REORDER_COMPACT ){

		    							toReorder[i] = true;

		    							num_reorder++;
		    						}
		    					}
		    				}

		    				if ( num_linear > 0 ){

		    					if (!Arrays.equals(toLinear, setStorageTypes(toLinear, DiskManagerFileInfo.ST_LINEAR))){

		    						return;
		    					}
		    				}

		    				if ( num_reorder > 0 ){

		    					if (!Arrays.equals(toReorder, setStorageTypes(toReorder, DiskManagerFileInfo.ST_REORDER ))){

		    						return;
		    					}
		    				}
		        		}

		        		File[]	to_link = new File[res.length];

						for(int i=0;i<res.length;i++){
							if(toChange[i]){
								to_link[i] = res[i].setSkippedInternal( setSkipped );
							}
						}

						if ( !loading[0] ){

							DiskManagerImpl.storeFilePriorities( download_manager, res);
						}

						List<Integer>	from_indexes 	= new ArrayList<>();
						List<File>		from_links 		= new ArrayList<>();
						List<File>		to_links		= new ArrayList<>();

						for(int i=0;i<res.length;i++){
							if ( to_link[i] != null ){
								from_indexes.add( i );
								from_links.add( res[i].getFile( false ));
								to_links.add( to_link[i] );
							}
						}

						if ( from_links.size() > 0 ){
							download_manager.getDownloadState().setFileLinks( from_indexes, from_links, to_links );
						}

						if(!setSkipped){
							doFileExistenceChecks(this, toChange, download_manager, true);
						}

						for(int i=0;i<res.length;i++){
							if(toChange[i]){
								listener.filePriorityChanged(res[i]);
							}
						}
					}

					@Override
					public boolean[] setStorageTypes(boolean[] toChange, int newStorageType) {
						if(toChange.length != res.length)
							throw new IllegalArgumentException("array length mismatches the number of files");

						String[] types = DiskManagerImpl.getStorageTypes(download_manager);
						boolean[] modified = new boolean[res.length];
						boolean[] toSkip = new boolean[res.length];
						int toSkipCount = 0;
						DownloadManagerState dmState = download_manager.getDownloadState();

						try {
							dmState.suppressStateSave(true);

							for(int i=0;i<res.length;i++)
							{
								if(!toChange[i])
									continue;


								final int idx = i;

								int old_type = DiskManagerUtil.convertDMStorageTypeFromString( types[i] );

								//System.out.println(old_type + " <> " + newStroageType);

								if ( newStorageType == old_type )
								{
									modified[i] = true;
									continue;
								}

								try{
									File    target_file = res[i].getFile( true );

									// if the file doesn't exist then this is the start-of-day, most likely
									// being called from the torrent-opener, so we don't need to do any
									// file fiddling (in fact, if we do, we end up leaving zero length
									// files for dnd files which then force a recheck when the download
									// starts for the first time)

									if ( target_file.exists()){

										CacheFile cache_file =
											CacheFileManagerFactory.getSingleton().createFile(
												new CacheFileOwner()
												{
													@Override
													public String
													getCacheFileOwnerName()
													{
														return( download_manager.getInternalName());
													}

													@Override
													public TOTorrentFile
													getCacheFileTorrentFile()
													{
														return( res[idx].getTorrentFile() );
													}

													@Override
													public File
													getCacheFileControlFileDir()
													{
														return( download_manager.getDownloadState().getStateFile( ));
													}
													@Override
													public int
													getCacheMode()
													{
														return( CacheFileOwner.CACHE_MODE_NORMAL );
													}
												},
												target_file,
												DiskManagerUtil.convertDMStorageTypeToCache( newStorageType ));

										cache_file.getLength();	// need this to trigger recovery for re-order files :(

										cache_file.close();
									}

									toSkip[i] = ( newStorageType == FileSkeleton.ST_COMPACT || newStorageType == FileSkeleton.ST_REORDER_COMPACT )&& !res[i].isSkipped();

									if ( toSkip[i] ){

										toSkipCount++;
									}

									modified[i] = true;

								}catch( Throwable e ){

									Debug.printStackTrace(e);

									Logger.log(
										new LogAlert(download_manager,
											LogAlert.REPEATABLE,
											LogAlert.AT_ERROR,
											"Failed to change storage type for '" + res[i].getFile(true) +"': " + Debug.getNestedExceptionMessage(e)));

									// download's not running - tag for recheck

									RDResumeHandler.recheckFile( download_manager, res[i] );

								}

								types[i] = DiskManagerUtil.convertDMStorageTypeToString( newStorageType );
							}

							/*
							 * set storage type and skipped before we do piece clearing and file
							 * clearing checks as those checks work better when skipped/stype is set
							 * properly
							 */
							dmState.setListAttribute( DownloadManagerState.AT_FILE_STORE_TYPES, types);

							if ( toSkipCount > 0){

								setSkipped( toSkip, true );
							}

							for(int i=0;i<res.length;i++)
							{
								if(!toChange[i])
									continue;

								// download's not running, update resume data as necessary

								int cleared = RDResumeHandler.storageTypeChanged( download_manager, res[i] );

								// try and maintain reasonable figures for downloaded. Note that because
								// we don't screw with the first and last pieces of the file during
								// storage type changes we don't have the problem of dealing with
								// the last piece being smaller than torrent piece size

								if (cleared > 0)
								{
									res[i].downloaded = res[i].downloaded - cleared * res[i].getTorrentFile().getTorrent().getPieceLength();
									if (res[i].downloaded < 0) res[i].downloaded = 0;
								}
							}

							DiskManagerImpl.storeFileDownloaded( download_manager, res, true );

							doFileExistenceChecks(this, toChange, download_manager, newStorageType == FileSkeleton.ST_LINEAR || newStorageType == FileSkeleton.ST_REORDER );

						} finally {
							dmState.suppressStateSave(false);
							dmState.save();
						}

						return modified;
					}
		        };

		        for (int i=0;i<res.length;i++){

		            final TOTorrentFile torrent_file    = torrent_files[i];

		            final int file_index = i;

		            FileSkeleton info = new FileSkeleton() {

		            	private volatile CacheFile   read_cache_file;
		            	// do not access this field directly, use lazyGetFile() instead
		            	private WeakReference dataFile = new WeakReference(null);

		            	@Override
			            public void
		            	setPriority(int b)
		            	{
		            		priority    = b;

		            		DiskManagerImpl.storeFilePriorities( download_manager, res );

		            		listener.filePriorityChanged( this );
		            	}

		            	@Override
			            public void
		            	setSkipped(boolean _skipped)
		            	{
		            		if ( !_skipped && getStorageType() == ST_COMPACT ){
		            			if ( !setStorageType( ST_LINEAR )){
		            				return;
		            			}
		            		}

		            		if ( !_skipped && getStorageType() == ST_REORDER_COMPACT ){
		            			if ( !setStorageType( ST_REORDER )){
		            				return;
		            			}
		            		}

		            		File to_link = setSkippedInternal( _skipped );

		            		DiskManagerImpl.storeFilePriorities( download_manager, res );

		            		if ( to_link != null ){

		            			download_manager.getDownloadState().setFileLink( file_index, getFile( false ), to_link );
		            		}

		            		if ( !_skipped ){
		            			boolean[] toCheck = new boolean[fileSetSkeleton.nbFiles()];
		            			toCheck[file_index] = true;
		            			doFileExistenceChecks(fileSetSkeleton, toCheck, download_manager, true);
		            		}

		            		listener.filePriorityChanged( this );
		            	}

		            	@Override
			            public int
		            	getAccessMode()
		            	{
		            		return( READ );
		            	}

		            	@Override
			            public long
		            	getDownloaded()
		            	{
		            		return( downloaded );
		            	}

		            	@Override
		            	public long getLastModified(){
		            		return( getFile( true ).lastModified());
		            	}
		            	
		            	@Override
			            public void
		            	setDownloaded(
		            		long    l )
		            	{
		            		downloaded  = l;
		            	}

		            	@Override
			            public String
		            	getExtension()
		            	{
		            		String    ext   = lazyGetFile().getName();

		                    if ( incomplete_suffix != null && ext.endsWith( incomplete_suffix )){

		                    	ext = ext.substring( 0, ext.length() - incomplete_suffix.length());
		                    }

		            		int separator = ext.lastIndexOf(".");
		            		if (separator == -1)
		            			separator = 0;
		            		return ext.substring(separator);
		            	}

		            	@Override
			            public int
		            	getFirstPieceNumber()
		            	{
		            		return( torrent_file.getFirstPieceNumber());
		            	}

		            	@Override
			            public int
		            	getLastPieceNumber()
		            	{
		            		return( torrent_file.getLastPieceNumber());
		            	}

		            	@Override
			            public long
		            	getLength()
		            	{
		            		return( torrent_file.getLength());
		            	}

		            	@Override
			            public int
		            	getIndex()
		            	{
		            		return( file_index );
		            	}

		            	@Override
			            public int
		            	getNbPieces()
		            	{
		            		return( torrent_file.getNumberOfPieces());
		            	}

		            	@Override
			            public int
		            	getPriority()
		            	{
		            		return( priority );
		            	}

		            	@Override
			            protected File
		            	setSkippedInternal(
		            		boolean	_skipped )
		            	{
		            			// returns the file to link to if linkage is required

		            		skipped_internal = _skipped;

	    					if ( !download_manager.isDestroyed()){

	    						DownloadManagerState dm_state = download_manager.getDownloadState();

			            		String dnd_sf = dm_state.getAttribute( DownloadManagerState.AT_DND_SUBFOLDER );

			            		if ( dnd_sf != null ){

			            			File	link = getLink();

			        				File 	file = getFile( false );

				            		if ( _skipped ){

				            			if ( link == null || link.equals( file )){

			            					File parent = file.getParentFile();

			            					if ( parent != null ){

	    										String prefix = dm_state.getAttribute( DownloadManagerState.AT_DND_PREFIX );

	    										String file_name = file.getName();

	    										if ( prefix != null && !file_name.startsWith( prefix )){

	    											file_name = prefix + file_name;
	    										}

			            						File new_parent = new File( parent, dnd_sf );

			            						File new_file = new File( new_parent, file_name );

			            						if ( !new_file.exists()){

				            						if ( !new_parent.exists()){

				            							new_parent.mkdirs();
				            						}

				            						if ( new_parent.canWrite()){

					            						boolean ok;

					            						if ( file.exists()){

					            							ok = FileUtil.renameFile( file, new_file );

					            						}else{

					            							ok = true;
					            						}

					            						if ( ok ){

					            							return( new_file );
					            						}
				            						}
			            						}
			            					}
				            			}
				            		}else{

				            			if ( link != null && !file.exists()){

			            					File parent = file.getParentFile();

			            					if ( parent != null && parent.canWrite()){

			            						File new_parent = parent.getName().equals( dnd_sf )?parent:new File( parent, dnd_sf );

			            							// use link name to handle incomplete file suffix if set

			            						File new_file = new File( new_parent, link.getName());

			            						if ( new_file.equals( link )){

			            							boolean	ok;

			            							String incomp_ext = dm_state.getAttribute( DownloadManagerState.AT_INCOMP_FILE_SUFFIX );

			    									String file_name = file.getName();

			    									String prefix = dm_state.getAttribute( DownloadManagerState.AT_DND_PREFIX );

			    									boolean prefix_removed = false;

			    									if ( prefix != null && file_name.startsWith(prefix)){

			    										file_name = file_name.substring( prefix.length());

			    										prefix_removed = true;
			    									}

			    									if  ( 	incomp_ext != null && incomp_ext.length() > 0 &&
			    											getDownloaded() != getLength()){

															// retain the prefix if enabled and we have a suffix

			    										if ( prefix == null ){

			    											prefix = "";
			    										}

			    										file = new File( file.getParentFile(), prefix + file_name + incomp_ext );

			    									}else if ( prefix_removed ){

			    										file = new File( file.getParentFile(), file_name);
			    									}

			            							if ( new_file.exists()){

			            								ok = FileUtil.renameFile( new_file, file );

			            							}else{

			            								ok = true;
			            							}

			            							if ( ok ){

			            								File[] files = new_parent.listFiles();

			            								if ( files != null && files.length == 0 ){

			            									new_parent.delete();
			            								}

			            								return( file );
			            							}
			            						}
			            					}
				            			}
				            		}
			            		}
	    					}

	    					return( null );
		            	}

		            	@Override
			            public boolean
		            	isSkipped()
		            	{
		            		return( skipped_internal );
		            	}

		            	@Override
			            public DiskManager
		            	getDiskManager()
		            	{
		            		return( null );
		            	}

		            	@Override
			            public DownloadManager
		            	getDownloadManager()
		            	{
		            		return( download_manager );
		            	}

		            	@Override
			            public File
		            	getFile(
		            		boolean follow_link )
		            	{
		            		if ( follow_link ){

		            			File link = getLink();

		            			if ( link != null ){

		            				return( link );
		            			}
		            		}
		            		return lazyGetFile();
		            	}

		            	private File lazyGetFile()
		            	{
		            		File toReturn = (File)dataFile.get();
		            		if(toReturn != null)
		            			return toReturn;

		            		TOTorrent tor = download_manager.getTorrent();

		            		String  path_str = root_dir;
		            		File simpleFile = null;

		            		// for a simple torrent the target file can be changed

		            		if ( tor == null || tor.isSimpleTorrent()){

		            				// rumour has it tor can sometimes be null

		            			simpleFile = download_manager.getAbsoluteSaveLocation();

		            		}else{
		            			byte[][]path_comps = torrent_file.getPathComponents();

		            			for (int j=0;j<path_comps.length;j++){

		            				String comp;
		            				try
		            				{
		            					comp = locale_decoder.decodeString( path_comps[j] );
		            				} catch (UnsupportedEncodingException e)
		            				{
		            					Debug.printStackTrace(e);
		            					comp = "undecodableFileName"+file_index;
		            				}

		            				comp = FileUtil.convertOSSpecificChars( comp,  j != path_comps.length-1 );

		            				path_str += (j==0?"":File.separator) + comp;
		            			}
		            		}

		            		dataFile = new WeakReference(toReturn = simpleFile != null ? simpleFile : new File( path_str ));

		            		//System.out.println("new file:"+toReturn);
		            		return toReturn;
		            	}

		            	@Override
			            public TOTorrentFile
		            	getTorrentFile()
		            	{
		            		return( torrent_file );
		            	}

	                	@Override
		                public boolean
	                	setLink(
	                		File    link_destination )
	                	{
	                		/**
	                		 * If we a simple torrent, then we'll redirect the call to the download and move the
	                		 * data files that way - that'll keep everything in sync.
	                		 */
	                		if (download_manager.getTorrent().isSimpleTorrent()) {
	                			try {
	                				download_manager.moveDataFiles(link_destination.getParentFile(), link_destination.getName());
	                				return true;
	                			}
	                			catch (DownloadManagerException e) {
	                				// What should we do with the error?
	                				return false;
	                			}
	                		}
	                		return setLinkAtomic(link_destination);
	                	}

	                	@Override
		                public boolean
	                	setLinkAtomic(
	                		File    link_destination )
	                	{
	                		return( setFileLink( download_manager, res, this, lazyGetFile(), link_destination, null ));
	                	}

	                	@Override
		                public boolean
	                	setLinkAtomic(
	                		File    						link_destination,
	                		FileUtil.ProgressListener		pl )
	                	{
	                		return( setFileLink( download_manager, res, this, lazyGetFile(), link_destination , pl));
	                	}
	                	
	                	@Override
		                public File
	                	getLink()
	                	{
	                		return( download_manager.getDownloadState().getFileLink( file_index, lazyGetFile() ));
	                	}

	                	@Override
		                public boolean setStorageType(int type) {
	                		boolean[] change = new boolean[res.length];
	                		change[file_index] = true;
	                		return fileSetSkeleton.setStorageTypes(change, type)[file_index];
	                	}

	                	@Override
		                public int
	                	getStorageType()
	                	{
	                		return( DiskManagerUtil.convertDMStorageTypeFromString( DiskManagerImpl.getStorageType(download_manager, file_index)));
	                	}

	                	@Override
		                public void
	                	flushCache()
	                	{
	                	}

	                	@Override
		                public DirectByteBuffer
	                	read(
	                		long    offset,
	                		int     length )

	                		throws IOException
	                	{
	                		CacheFile temp;

			                try{
	                			cache_read_mon.enter();

	                			if ( read_cache_file == null ){

	                				try{
	                					int type = convertDMStorageTypeFromString( DiskManagerImpl.getStorageType(download_manager, file_index));

	                					read_cache_file =
	                						CacheFileManagerFactory.getSingleton().createFile(
	                							new CacheFileOwner()
	                							{
	                								@Override
									                public String
	                								getCacheFileOwnerName()
	                								{
	                									return( download_manager.getInternalName());
	                								}

	                								@Override
									                public TOTorrentFile
	                								getCacheFileTorrentFile()
	                								{
	                									return( torrent_file );
	                								}

	                								@Override
									                public File
	                								getCacheFileControlFileDir()
	                								{
	                									return( download_manager.getDownloadState().getStateFile( ));
	                								}
	                								@Override
									                public int
	                								getCacheMode()
	                								{
	                									return( CacheFileOwner.CACHE_MODE_NORMAL );
	                								}
	                							},
	                							getFile( true ),
	                							convertDMStorageTypeToCache( type ));

	                				}catch( Throwable e ){

	                					Debug.printStackTrace(e);

	                					throw( new IOException( e.getMessage()));
	                				}
	                			}

	                			temp = read_cache_file;

	                		}finally{

	                			cache_read_mon.exit();
	                		}

	                		DirectByteBuffer    buffer =
	                			DirectByteBufferPool.getBuffer( DirectByteBuffer.AL_DM_READ, length );

	                		try{
	                			temp.read( buffer, offset, CacheFile.CP_READ_CACHE );

	                		}catch( Throwable e ){

	                			buffer.returnToPool();

	                			Debug.printStackTrace(e);

	                			throw( new IOException( e.getMessage()));
	                		}

	                		return( buffer );
	                	}

	                	@Override
		                public int
	                	getReadBytesPerSecond()
	                	{
	                		CacheFile temp = read_cache_file;

	                		if ( temp == null ){

	                			return( 0 );
	                		}

	                			// could do something here one day I guess

	                		return( 0 );
	                	}

	                	@Override
		                public int
	                	getWriteBytesPerSecond()
	                	{
	                		return( 0 );
	                	}

	                	@Override
		                public long
	                	getETA()
	                	{
	                		return( -1 );
	                	}

	                	@Override
	                	public void recheck()
	                	{
	                		DiskManagerFactory.recheckFile( getDownloadManager(), this );
	                	}
	                	
	                	@Override
		                public void
	                	close()
	                	{
	                		CacheFile temp;

	                   		try{
	                			cache_read_mon.enter();

	                			temp = read_cache_file;

	                			read_cache_file = null;

	                   		}finally{

	                   			cache_read_mon.exit();
	                   		}

	                		if ( temp != null ){

	                			try{
	                				temp.close();

	                			}catch( Throwable e ){

	                				Debug.printStackTrace(e);
	                			}
	                		}
	                	}

		            	@Override
			            public void
		            	addListener(
		            		DiskManagerFileInfoListener listener )
		            	{
		            		if ( getDownloaded() == getLength()){

		            			try{
		            				listener.dataWritten( 0, getLength());

		            				listener.dataChecked( 0, getLength());

		            			}catch( Throwable e ){

		            				Debug.printStackTrace(e);
		            			}
		            		}
		            	}

		            	@Override
			            public void
		            	removeListener(
		            		DiskManagerFileInfoListener listener )
		            	{
		            	}
		            };

		            res[i]  = info;
		        }

		        loadFilePriorities( download_manager, fileSetSkeleton);

		        loadFileDownloaded( download_manager, res );

		        return( fileSetSkeleton );

		    }finally{

		    	loading[0] = false;
		    }

	    }catch( Throwable e ){

	        Debug.printStackTrace(e);

	        return( new DiskManagerFileInfoSetImpl(new DiskManagerFileInfoImpl[0],null) );

	    }
	}

	public static int
	convertDMStorageTypeFromString(
		String		str )
	{
		char c = str.charAt(0);

		switch( c ){
			case 'L':{
				return( DiskManagerFileInfo.ST_LINEAR );
			}
			case 'C':{
				return( DiskManagerFileInfo.ST_COMPACT );
			}
			case 'R':{
				return( DiskManagerFileInfo.ST_REORDER );
			}
			case 'X':{
				return( DiskManagerFileInfo.ST_REORDER_COMPACT );
			}
		}

		Debug.out( "eh?" );

		return( DiskManagerFileInfo.ST_LINEAR );
	}

	public static String
	convertDMStorageTypeToString(
		int		dm_type )
	{
		switch( dm_type ){
			case DiskManagerFileInfo.ST_LINEAR:{
				return( "L" );
			}
			case DiskManagerFileInfo.ST_COMPACT:{
				return( "C" );
			}
			case DiskManagerFileInfo.ST_REORDER:{
				return( "R" );
			}
			case DiskManagerFileInfo.ST_REORDER_COMPACT:{
				return( "X" );
			}
		}

		Debug.out( "eh?" );

		return( "?" );
	}

	public static String
	convertCacheStorageTypeToString(
		int		cache_type )
	{
		switch( cache_type ){
			case CacheFile.CT_LINEAR:{
				return( "L" );
			}
			case CacheFile.CT_COMPACT:{
				return( "C" );
			}
			case CacheFile.CT_PIECE_REORDER:{
				return( "R" );
			}
			case CacheFile.CT_PIECE_REORDER_COMPACT:{
				return( "X" );
			}
		}

		Debug.out( "eh?" );

		return( "?" );
	}
	public static int
	convertDMStorageTypeToCache(
		int	dm_type )
	{
		switch( dm_type ){
			case DiskManagerFileInfo.ST_LINEAR:{
				return( CacheFile.CT_LINEAR );
			}
			case DiskManagerFileInfo.ST_COMPACT:{
				return( CacheFile.CT_COMPACT );
			}
			case DiskManagerFileInfo.ST_REORDER:{
				return( CacheFile.CT_PIECE_REORDER );
			}
			case DiskManagerFileInfo.ST_REORDER_COMPACT:{
				return( CacheFile.CT_PIECE_REORDER_COMPACT );
			}
		}

		Debug.out( "eh?" );

		return( CacheFile.CT_LINEAR );
	}

	protected static void
	storeFilePriorities(
		DownloadManager         download_manager,
		DiskManagerFileInfo[]   files )
	{
		if ( files == null ) return;
		// bit confusing this: priorities are stored as
		// >= 1   : priority, 1 = high, 2 = higher etc
		// 0      : skipped
		// -1     : normal priority
		// <= -2  : negative priority , so -2 -> priority -1, -3 -> priority -2 etc

		List file_priorities = new ArrayList(files.length);
		for (int i=0; i < files.length; i++) {
			DiskManagerFileInfo file = files[i];
			if (file == null) return;
			boolean skipped = file.isSkipped();
			int priority = file.getPriority();
			int value;
			if ( skipped ){
				value = 0;
			}else if ( priority > 0 ){
				value = priority;
			}else{
				value = priority - 1;
				if ( value > 0 ){
					value = Integer.MIN_VALUE;
				}
			}
			file_priorities.add( i, Long.valueOf(value));
		}

	   download_manager.setUserData( "file_priorities", file_priorities );

	   		// for whatever reason we don't set the skipped file stats here if we have a REAL set of
	   		// file info (maybe because the code tries to maintain its own active version of these values)

	   if (files.length > 0 && !(files[0] instanceof DiskManagerFileInfoImpl)) {

	       long skipped_file_set_size   = 0;
	       long skipped_but_downloaded  = 0;

	       for (int i=0;i<files.length;i++){

	           DiskManagerFileInfo file = files[i];

	           if ( file.isSkipped()){

	               skipped_file_set_size   += file.getLength();
	               skipped_but_downloaded  += file.getDownloaded();
	           }
	       }

	       DownloadManagerStats stats = download_manager.getStats();
	       if (stats instanceof DownloadManagerStatsImpl) {
	       	((DownloadManagerStatsImpl) stats).setSkippedFileStats(skipped_file_set_size, skipped_but_downloaded);
	       }
	   }
	}

	static void
	loadFilePriorities(
		DownloadManager         download_manager,
		DiskManagerFileInfoSet   fileSet )
	{
		//  TODO: remove this try/catch.  should only be needed for those upgrading from previous snapshot
		try {
			DiskManagerFileInfo[] files = fileSet.getFiles();

			if ( files == null ) return;
			List file_priorities = (List)download_manager.getUserData( "file_priorities" );
			if ( file_priorities == null ) return;

			boolean[] toSkip = new boolean[files.length];
			int[] prio = new int[files.length];

			for (int i=0; i < files.length; i++) {
				DiskManagerFileInfo file = files[i];
				if (file == null) return;
				try {
  				int priority = ((Long)file_priorities.get( i )).intValue();
  				if ( priority == 0 ){
  					toSkip[i] = true;
  				}else{
  					if ( priority < 0 ){
  						priority++;
  					}
  					prio[i] = priority;
  				}
				} catch (Throwable t2) {
					Debug.printStackTrace(t2);
				}
			}

			fileSet.setPriority(prio);
			fileSet.setSkipped(toSkip, true);

		}
		catch (Throwable t) {Debug.printStackTrace( t );}
	}

	protected static void
	  loadFileDownloaded(
	    DownloadManager             download_manager,
	    DiskManagerFileInfoHelper[] files )
	  {
	      DownloadManagerState  state = download_manager.getDownloadState();

	      Map   details = state.getMapAttribute( DownloadManagerState.AT_FILE_DOWNLOADED );

	      if ( details == null ){

	          return;
	      }

	      List  downloaded = (List)details.get( "downloaded" );

	      if ( downloaded == null ){

	          return;
	      }

	      try{
	          for (int i=0;i<files.length;i++){

	              files[i].setDownloaded(((Long)downloaded.get(i)).longValue());
	          }
	     }catch( Throwable e ){

	         Debug.printStackTrace(e);
	     }

	  }


}
