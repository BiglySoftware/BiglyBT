/*
 * File    : DiskManagerFileInfoImpl.java
 * Created : 18-Oct-2003
 * By      : Olivier
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
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
 */

package com.biglybt.core.disk.impl;
/*
 * Created on 3 juil. 2003
 *
 */

import java.io.File;
import java.io.IOException;

import com.biglybt.core.disk.*;
import com.biglybt.core.diskmanager.cache.CacheFile;
import com.biglybt.core.diskmanager.cache.CacheFileManagerException;
import com.biglybt.core.diskmanager.cache.CacheFileManagerFactory;
import com.biglybt.core.diskmanager.cache.CacheFileOwner;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.torrent.TOTorrentFile;
import com.biglybt.core.util.*;
import com.biglybt.core.util.average.AverageFactory;
import com.biglybt.core.util.average.AverageFactory.LazyMovingImmediateAverageAdapter;
import com.biglybt.core.util.average.AverageFactory.LazyMovingImmediateAverageState;


/**
 * @author Olivier
 *
 */
public class
DiskManagerFileInfoImpl
	implements DiskManagerFileInfo, CacheFileOwner
{
  private File					root_dir;
  private final String	relative_file;

  final int			file_index;
  CacheFile		cache_file;

  private String 		extension;
  private long 			downloaded;

  final DiskManagerHelper 	diskManager;
  final TOTorrentFile			torrent_file;

  private int 		priority 	= 0;

  protected boolean 	skipped_internal 	= false;

  private volatile Boolean	skipping;
  
  private String last_error = null;
  
  private volatile CopyOnWriteList<DiskManagerFileInfoListener>	listeners;	// save mem and allocate if needed later

  public
  DiskManagerFileInfoImpl(
	DiskManagerHelper	_disk_manager,
  	File				_root_dir,
  	String				_relative_file,
  	int					_file_index,
	TOTorrentFile		_torrent_file,
	int					_storage_type )

  	throws CacheFileManagerException
  {
    diskManager 	= _disk_manager;
    torrent_file	= _torrent_file;

    root_dir		= _root_dir;
    relative_file	= _relative_file;

    file_index		= _file_index;

    int	cache_st = DiskManagerUtil.convertDMStorageTypeToCache( _storage_type );

  	cache_file = CacheFileManagerFactory.getSingleton().createFile( this, FileUtil.newFile( root_dir, relative_file), cache_st, false );

  	if ( cache_st == CacheFile.CT_COMPACT || cache_st == CacheFile.CT_PIECE_REORDER_COMPACT ){

  		skipped_internal = true;
  	}
  }

	protected void
	load(
		int		_priority,
		boolean	_skipped )
	{
		priority				= _priority;
		skipped_internal		= _skipped;
	}
	
  	@Override
	  public String
  	getCacheFileOwnerName()
  	{
  		return( diskManager.getInternalName());
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
		return( diskManager.getDownloadState().getStateFile( ));
	}

	@Override
	public int
	getCacheMode()
	{
		return( diskManager.getCacheMode());
	}

  @Override
  public void
  flushCache()

	throws	Exception
  {
  	cache_file.flushCache();
  }

  @Override
  public boolean exists(){
	return( cache_file.exists());
  }
  
  public void
  moveFile(
  	File						new_root_dir,
  	File						new_absolute_file,
  	boolean						link_only,
  	FileUtil.ProgressListener	pl )

  	throws CacheFileManagerException
  {
	  if ( !link_only ){

		  cache_file.moveFile( new_absolute_file, pl );
	  }

	 root_dir	= new_root_dir;
  }

  public void
  renameFile(
  	String	new_name )

  	throws CacheFileManagerException
  {
	  cache_file.renameFile( new_name );
  }

  public CacheFile
  getCacheFile()
  {
  	return( cache_file );
  }

  public void
  setAccessMode(
  	int		mode )

  	throws CacheFileManagerException
  {
  	cache_file.setAccessMode( mode==DiskManagerFileInfo.READ?CacheFile.CF_READ:CacheFile.CF_WRITE );
  }

  @Override
  public int
  getAccessMode()
  {
  	int	mode = cache_file.getAccessMode();

	return( mode == CacheFile.CF_READ?DiskManagerFileInfo.READ:DiskManagerFileInfo.WRITE);
  }

  /**
   * @return
   */
  @Override
  public long getDownloaded() {
	return downloaded;
  }

  @Override
  public long 
  getLastModified()
  {
	  return( cache_file.getLastModified());
  }
	
  /**
   * @return
   */
  @Override
  public String getExtension() {
	return extension;
  }

  /**
   * @return
   */
  @Override
  public File
  getFile(
	boolean	follow_link )
	{
		File file = FileUtil.newFile(root_dir, relative_file);

		if (!follow_link) {

			return file;
		}

		// Same as getLink(), except saves redundant getFile(false) call
		File	res = diskManager.getDownloadState().getFileLink(file_index, file);
		
		return res == null ? file : res;
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
		File	link_destination,
		boolean	no_delete )
	{
		last_error = "download must be stopped";
		
		Debug.out( "setLink: download must be stopped" );

		return( false );
	}

	@Override
	public String getLastError(){
		return( last_error );
	}
	
	@Override
	public boolean
	setLinkAtomic(
		File		link_destination,
		boolean		no_delete )
	{
		last_error = "download must be stopped";
		
		Debug.out( "setLink: download must be stopped" );

		return( false );
	}
	
	@Override
	public boolean
	setLinkAtomic(
		File						link_destination,
		boolean						no_delete,
		FileUtil.ProgressListener	pl )
	{
		last_error = "download must be stopped";
		
		Debug.out( "setLink: download must be stopped" );

		return( false );
	}

	@Override
	public File
	getLink()
	{
		return( diskManager.getDownloadState().getFileLink( file_index, getFile( false )));
	}

	@Override
	public boolean setStorageType(int type, boolean force ) {
		DiskManagerFileInfoSet set = diskManager.getFileSet();
		boolean[] toSet = new boolean[set.nbFiles()];
		toSet[file_index] = true;
		return set.setStorageTypes(toSet, type, force )[file_index];
	}

	@Override
	public int
	getStorageType()
	{
		return( DiskManagerUtil.convertDMStorageTypeFromString( diskManager.getStorageType(file_index)));
	}

  /**
   * @return
   */
  @Override
  public int getFirstPieceNumber() {
    return torrent_file.getFirstPieceNumber();
  }


  @Override
  public int getLastPieceNumber() {
    return torrent_file.getLastPieceNumber();
  }

  /**
   * @return
   */
  @Override
  public long getLength() {
	return torrent_file.getLength();
  }

	@Override
	public int
	getIndex()
	{
		return( file_index );
	}
  /**
   * @return
   */
  @Override
  public int getNbPieces() {
	return torrent_file.getNumberOfPieces();
  }


  /**
   * @param l
   */
  public void setDownloaded(long l) {
	downloaded = l;
  }

  /**
   * @param string
   */
  public void setExtension(String string) {
	extension = string.startsWith( "." )?StringInterner.intern(string):string;
  }

  /**
   * @return
   */
  @Override
  public int getPriority() {
	return priority;
  }

  /**
   * @param b
   */
  @Override
  public void setPriority(int b) {
	priority = b;
	diskManager.priorityChanged( this );
  }

  /**
   * @return
   */
  @Override
  public boolean isSkipped() {
	return skipped_internal;
  }

  /**
   * @param skipped
   */
  @Override
  public void setSkipped(boolean skipped) {

	  try{
		skipping = skipped;
		  
		int	existing_st = getStorageType();
	
		  // currently a non-skipped file must be linear
	
		if ( !skipped && existing_st == ST_COMPACT ){
			if ( !setStorageType( ST_LINEAR )){
				return;
			}
		}
	
		if ( !skipped && existing_st == ST_REORDER_COMPACT ){
			if ( !setStorageType( ST_REORDER )){
				return;
			}
		}
	
		setSkippedInternal( skipped );
		
		diskManager.skippedFileSetChanged( this );
			
		boolean[] toCheck = new boolean[diskManager.getFileSet().nbFiles()];
			
		toCheck[file_index] = true;
			
		DiskManagerUtil.doFileExistenceChecksAfterSkipChange(diskManager.getFileSet(), toCheck, skipped, diskManager.getDownloadState().getDownloadManager());
	  }finally{
		  skipping = null;
	  }
  }

  @Override
  public Boolean isSkipping(){
	  return( skipping );
  }
	protected void
	setSkippedInternal(
		boolean	_skipped )
	{
		skipped_internal = _skipped;

		DownloadManager dm = getDownloadManager();

		if ( dm != null && !dm.isDestroyed()){

			DownloadManagerState dm_state =  diskManager.getDownloadState();

    		String dnd_sf = dm_state.getAttribute( DownloadManagerState.AT_DND_SUBFOLDER );

     		if ( dnd_sf != null ){

    			File	link = getLink();

				File 	file = getFile( false );

        		if ( _skipped ){

        			if ( link == null || link.equals( file )){

    					File parent = file.getParentFile();

    					if ( parent != null ){

    						File new_parent = FileUtil.newFile( parent, dnd_sf );

    							// add prefix if not already present

							String prefix = dm_state.getAttribute( DownloadManagerState.AT_DND_PREFIX );

							String file_name = file.getName();

							if ( prefix != null && !file_name.startsWith( prefix )){

								file_name = prefix + file_name;
							}

    						File new_file = FileUtil.newFile( new_parent, file_name );

    						if ( !new_file.exists()){

        						if ( !new_parent.exists()){

        							new_parent.mkdirs();
        						}

        						if ( new_parent.canWrite()){

	        						boolean ok;

	    							try{
	    								dm_state.setFileLink( file_index, file, new_file );

										cache_file.moveFile( new_file, null );

										ok = true;

									}catch( Throwable e ){

										ok = false;

										Debug.out( e );
									}

	        						if ( !ok ){

	        							dm_state.setFileLink( file_index, file, link );
	        						}
        						}
    						}
    					}
        			}
        		}else{

        			if ( link != null && !file.exists()){

    					File parent = file.getParentFile();

    					if ( parent != null && parent.canWrite()){

    						File new_parent = parent.getName().equals( dnd_sf )?parent:FileUtil.newFile( parent, dnd_sf );

    							// use link name to handle incomplete file suffix if set

    						File new_file = FileUtil.newFile( new_parent, link.getName());

    						if ( new_file.equals( link )){

    							boolean	ok;

								try{
									String file_name = file.getName();

									String prefix = dm_state.getAttribute( DownloadManagerState.AT_DND_PREFIX );

									boolean prefix_removed = false;

									if ( prefix != null && file_name.startsWith(prefix)){

										file_name = file_name.substring( prefix.length());

										prefix_removed = true;
									}

									String incomp_ext = dm_state.getAttribute( DownloadManagerState.AT_INCOMP_FILE_SUFFIX );

									if  ( 	incomp_ext != null && incomp_ext.length() > 0 &&
											getDownloaded() != getLength()){

											// retain the prefix if enabled and we have a suffix

										if ( prefix == null ){

											prefix = "";
										}

										File new_link = FileUtil.newFile( file.getParentFile(), prefix + file_name + incomp_ext );

										dm_state.setFileLink( file_index, file, new_link );

										cache_file.moveFile( new_link, null );

									}else if ( prefix_removed ){

										File new_link = FileUtil.newFile( file.getParentFile(), file_name );

										dm_state.setFileLink( file_index, file, new_link );

										cache_file.moveFile( new_link, null );

									}else{

										dm_state.setFileLink( file_index, file, null );

										cache_file.moveFile( file, null );
									}

									File[] files = new_parent.listFiles();

    								if ( files != null && files.length == 0 ){

    									new_parent.delete();
    								}

									ok = true;

								}catch( Throwable e ){

									ok = false;

									Debug.out( e );
								}

    							if ( !ok ){

    								dm_state.setFileLink( file_index, file, link );
        						}
    						}
    					}
        			}
        		}
    		}
		}
	}



  @Override
  public DiskManager getDiskManager() {
    return diskManager;
  }

  @Override
  public DownloadManager	getDownloadManager()
  {
	  DownloadManagerState	state = diskManager.getDownloadState();

	  if ( state == null ){
		  return( null );
	  }

	  return( state.getDownloadManager());
  }

  	public void
  	dataWritten(
  		long		offset,
  		long		size,
  		Object		originator )
  	{
  		CopyOnWriteList<DiskManagerFileInfoListener>	l_ref = listeners;

  		if ( l_ref != null ){

  			for ( DiskManagerFileInfoListener listener: l_ref ){

  				try{
  					listener.dataWritten( offset, size, originator );

  				}catch( Throwable e ){

  					Debug.printStackTrace(e);
  				}
  			}
  		}
  	}

  	public void
  	dataChecked(
  		long		offset,
  		long		size )
  	{
 		CopyOnWriteList<DiskManagerFileInfoListener>	l_ref = listeners;

 		if ( l_ref != null ){

  			for ( DiskManagerFileInfoListener listener: l_ref ){

  				try{
  					listener.dataChecked( offset, size );

  				}catch( Throwable e ){

  					Debug.printStackTrace(e);
  				}
  			}
  		}
  	}

	@Override
	public DirectByteBuffer
	read(
		long	offset,
		int		length )

		throws IOException
	{
		DirectByteBuffer	buffer =
			DirectByteBufferPool.getBuffer( DirectByteBuffer.AL_DM_READ, length );

		try{
			cache_file.read( buffer, offset, CacheFile.CP_READ_CACHE );

		}catch( Throwable e ){

			buffer.returnToPool();

			Debug.printStackTrace(e);

			throw( new IOException( e.getMessage()));
		}

		return( buffer );
	}

	volatile LazyMovingImmediateAverageState	read_average_state;
	volatile LazyMovingImmediateAverageState	write_average_state;
	volatile LazyMovingImmediateAverageState	eta_average_state;

	private static final LazyMovingImmediateAverageAdapter<DiskManagerFileInfoImpl> read_adapter =
			new LazyMovingImmediateAverageAdapter<DiskManagerFileInfoImpl>()
			{
				@Override
				public LazyMovingImmediateAverageState
				getCurrent(
					DiskManagerFileInfoImpl		instance )
				{
					return( instance.read_average_state );
				}

				@Override
				public void
				setCurrent(
					DiskManagerFileInfoImpl 				instance,
					LazyMovingImmediateAverageState 		average )
				{
					instance.read_average_state = average;
				}

				@Override
				public long
				getValue(
					DiskManagerFileInfoImpl instance)
				{
					return( instance.cache_file.getSessionBytesRead());
				}
			};

	private static final LazyMovingImmediateAverageAdapter<DiskManagerFileInfoImpl> write_adapter =
			new LazyMovingImmediateAverageAdapter<DiskManagerFileInfoImpl>()
			{
				@Override
				public LazyMovingImmediateAverageState
				getCurrent(
					DiskManagerFileInfoImpl		instance )
				{
					return( instance.write_average_state );
				}

				@Override
				public void
				setCurrent(
					DiskManagerFileInfoImpl 				instance,
					LazyMovingImmediateAverageState 		average )
				{
					instance.write_average_state = average;
				}

				@Override
				public long
				getValue(
					DiskManagerFileInfoImpl instance)
				{
					return( instance.cache_file.getSessionBytesWritten());
				}
			};

	private static final LazyMovingImmediateAverageAdapter<DiskManagerFileInfoImpl> eta_adapter =
			new LazyMovingImmediateAverageAdapter<DiskManagerFileInfoImpl>()
			{
				@Override
				public LazyMovingImmediateAverageState
				getCurrent(
					DiskManagerFileInfoImpl		instance )
				{
					return( instance.eta_average_state );
				}

				@Override
				public void
				setCurrent(
					DiskManagerFileInfoImpl 				instance,
					LazyMovingImmediateAverageState 		average )
				{
					instance.eta_average_state = average;
				}

				@Override
				public long
				getValue(
					DiskManagerFileInfoImpl instance)
				{
					return( instance.cache_file.getSessionBytesWritten());
				}
			};

   	@Override
    public int
	getReadBytesPerSecond()
	{
		return( (int)AverageFactory.LazyMovingImmediateAverage( 10, 1, read_adapter, this ));
	}

	@Override
	public int
	getWriteBytesPerSecond()
	{
		return( (int)AverageFactory.LazyMovingImmediateAverage( 10, 1, write_adapter, this ));
	}

	@Override
	public long
	getETA()
	{
		if ( isSkipped()){

			return( -1 );
		}

		long	rem = getLength() - getDownloaded();

		if ( rem == 0 ){

			return( -1 );
		}

		long speed = AverageFactory.LazySmoothMovingImmediateAverage( eta_adapter, this );

		if ( speed == 0 ){

			return( Constants.CRAPPY_INFINITE_AS_LONG );

		}else{

			long eta = rem / speed;

			if ( eta == 0 ){

				return( 1 );

			}else{

				return( eta );
			}
		}
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
		// this doesn't need to do anything as overall closure is handled by the disk manager closing
	}

	@Override
	public void
	addListener(
		final DiskManagerFileInfoListener	listener )
	{
		synchronized( this ){

			if ( listeners == null ){

				listeners = new CopyOnWriteList<>();
			}
		}

		if ( !listeners.addIfNotPresent( listener )){

			return;
		}

		new Runnable()
		{
			private long	file_start;
			private long	file_end;

			private long	current_write_start  	= -1;
			private long	current_write_end		= -1;
			private long	current_check_start  	= -1;
			private long	current_check_end		= -1;

			@Override
			public void
			run()
			{
				TOTorrentFile[]	tfs = torrent_file.getTorrent().getFiles();

				long	torrent_offset = 0;

				for (int i=0;i<file_index;i++){

					torrent_offset += tfs[i].getLength();
				}

				file_start 	= torrent_offset;
				file_end	= file_start + torrent_file.getLength();

				DiskManagerPiece[]	pieces = diskManager.getPieces();

				int	first_piece = getFirstPieceNumber();
				int last_piece	= getLastPieceNumber();
				long	piece_size	= torrent_file.getTorrent().getPieceLength();

				for (int i=first_piece;i<=last_piece;i++){

					long	piece_offset = piece_size * i;

					DiskManagerPiece	piece = pieces[i];

					if ( piece.isDone()){

						long	bit_start 	= piece_offset;
						long	bit_end		= bit_start + piece.getLength();

						bitWritten( bit_start, bit_end, true );

					}else{

						int	block_offset = 0;

						for (int j=0;j<piece.getNbBlocks();j++){

							int	block_size = piece.getBlockSize(j);

							if ( piece.isWritten(j)){

								long	bit_start 	= piece_offset + block_offset;
								long	bit_end		= bit_start + block_size;

								bitWritten( bit_start, bit_end, false );
							}

							block_offset += block_size;
						}
					}
				}

				bitWritten( -1, -1, false );
			}

			protected void
			bitWritten(
				long	bit_start,
				long	bit_end,
				boolean	checked )
			{
				if ( current_write_start == -1 ){

					current_write_start	= bit_start;
					current_write_end	= bit_end;

				}else if ( current_write_end == bit_start ){

					current_write_end = bit_end;

				}else{

					if ( current_write_start < file_start ){

						current_write_start  = file_start;
					}

					if ( current_write_end > file_end ){

						current_write_end	= file_end;
					}

					if ( current_write_start < current_write_end ){

						try{
							listener.dataWritten( current_write_start-file_start, current_write_end-current_write_start, null );

						}catch( Throwable e ){

							Debug.printStackTrace(e);
						}
					}

					current_write_start	= bit_start;
					current_write_end	= bit_end;
				}

					// checked case

				if ( checked && current_check_start == -1 ){

					current_check_start	= bit_start;
					current_check_end	= bit_end;

				}else if ( checked && current_check_end == bit_start ){

					current_check_end = bit_end;

				}else{

					if ( current_check_start < file_start ){

						current_check_start  = file_start;
					}

					if ( current_check_end > file_end ){

						current_check_end	= file_end;
					}

					if ( current_check_start < current_check_end ){

						try{
							listener.dataChecked( current_check_start-file_start, current_check_end-current_check_start );

						}catch( Throwable e ){

							Debug.printStackTrace(e);
						}
					}

					if ( checked ){
						current_check_start	= bit_start;
						current_check_end	= bit_end;
					}else{
						current_check_start	= -1;
						current_check_end	= -1;
					}
				}
			}
		}.run();
	}


	@Override
	public void
	removeListener(
		DiskManagerFileInfoListener	listener )
	{
		synchronized( this ){

			if ( listeners != null ){

				listeners.remove( listener );
			}
		}
	}
}
