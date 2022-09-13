/*
 * Created on 03-Aug-2004
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

package com.biglybt.core.diskmanager.cache.impl;

/**
 * @author parg
 *
 */

import java.io.File;
import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.diskmanager.cache.*;
import com.biglybt.core.diskmanager.file.*;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentFile;
import com.biglybt.core.util.*;

public class
CacheFileManagerImpl
	implements CacheFileManager, AEDiagnosticsEvidenceGenerator
{
  private static final LogIDs LOGID = LogIDs.CACHE;
	public static final boolean	DEBUG	= false;

	public static final int	CACHE_CLEANER_TICKS		= 60;	// every 60 seconds

	public static final int		STATS_UPDATE_FREQUENCY		= 1*1000;	// 1 sec
	public static final long	DIRTY_CACHE_WRITE_MAX_AGE	= 120*1000;	// 2 mins

	static{
		if ( DEBUG ){

			System.out.println( "**** Cache consistency debugging on ****" );
		}
	}

	protected static int
	convertCacheToFileType(
		int	cache_type )
	{
		if ( cache_type == CacheFile.CT_LINEAR ){

			return( FMFile.FT_LINEAR );

		}else if ( cache_type == CacheFile.CT_COMPACT ){

			return( FMFile.FT_COMPACT );

		}else if ( cache_type == CacheFile.CT_PIECE_REORDER ){

			return( FMFile.FT_PIECE_REORDER );

		}else{

			return( FMFile.FT_PIECE_REORDER_COMPACT );
		}
	}

	protected static int
	convertFileToCacheType(
		int	file_type )
	{
		if ( file_type == FMFile.FT_LINEAR ){

			return( CacheFile.CT_LINEAR );

		}else if ( file_type == FMFile.FT_COMPACT ){

			return( CacheFile.CT_COMPACT );

		}else if ( file_type == FMFile.FT_PIECE_REORDER ){

			return( CacheFile.CT_PIECE_REORDER );

		}else{

			return( CacheFile.CT_PIECE_REORDER_COMPACT );
		}
	}

	protected boolean	cache_enabled;
	protected boolean	cache_read_enabled;
	protected boolean	cache_write_enabled;

	protected long		cache_size;
	protected long		cache_files_not_smaller_than;

	protected long		cache_minimum_free_size;
	protected long		cache_space_free;

	private long	cache_file_id_next	= 0;

	protected final FMFileManager		file_manager;

		// copy on update semantics

	protected WeakHashMap		cache_files			= new WeakHashMap();
	protected WeakHashMap		updated_cache_files	= null;

		// access order

	protected final LinkedHashMap		cache_entries = new LinkedHashMap(1024, 0.75f, true );

	protected CacheFileManagerStatsImpl	stats;


	protected final Map	torrent_to_cache_file_map	= new LightHashMap();

	protected long				cache_bytes_written;
	protected long				cache_bytes_read;
	protected long				file_bytes_written;
	protected long				file_bytes_read;

	protected long				cache_read_count;
	protected long				cache_write_count;
	protected long				file_read_count;
	protected long				file_write_count;

	protected final AEMonitor			this_mon	= new AEMonitor( "CacheFileManager" );

	long	cleaner_ticks	= CACHE_CLEANER_TICKS;



	public
	CacheFileManagerImpl()
	{
		AEDiagnostics.addWeakEvidenceGenerator( this );

		file_manager	= FMFileManagerFactory.getSingleton();

		boolean	enabled	= COConfigurationManager.getBooleanParameter( "diskmanager.perf.cache.enable" );

		boolean	enable_read 	= COConfigurationManager.getBooleanParameter( "diskmanager.perf.cache.enable.read" );

		boolean	enable_write	= COConfigurationManager.getBooleanParameter( "diskmanager.perf.cache.enable.write" );

			// units are MB

		long		size			= 1024L * 1024L * COConfigurationManager.getIntParameter( "diskmanager.perf.cache.size" );

			// units are KB

		int		not_smaller_than	= 1024*COConfigurationManager.getIntParameter( "notsmallerthan" );

		if ( size <= 0 ){

			Debug.out( "Invalid cache size parameter (" + size + "), caching disabled" );

			enabled	= false;
		}

		initialise( enabled, enable_read, enable_write, size, not_smaller_than );
	}

	protected void
	initialise(
		boolean	enabled,
		boolean	enable_read,
		boolean	enable_write,
		long	size,
		long	not_smaller_than )
	{
		cache_enabled			= enabled && ( enable_read || enable_write );

		cache_read_enabled		= enabled && enable_read;

		cache_write_enabled		= enabled && enable_write;

		cache_size				= size;

		cache_files_not_smaller_than	= not_smaller_than;

		cache_minimum_free_size	= cache_size/4;

		cache_space_free		= cache_size;

		stats = new CacheFileManagerStatsImpl( this );


		cacheStatsAndCleaner();


		if (Logger.isEnabled())
			Logger.log(new LogEvent(LOGID, "DiskCache: enabled = " + cache_enabled
					+ ", read = " + cache_read_enabled + ", write = "
					+ cache_write_enabled + ", size = " + cache_size + " B"));
	}

	protected boolean
	isWriteCacheEnabled()
	{
		return( cache_write_enabled );
	}

	protected boolean
	isReadCacheEnabled()
	{
		return( cache_read_enabled );
	}

	@Override
	public CacheFile
	createFile(
		final CacheFileOwner	owner,
		File					file,
		int						type,
		boolean					force )

		throws CacheFileManagerException
	{
		final long	my_id;

			// we differentiate the
		try{
			this_mon.enter();

			my_id = cache_file_id_next++;

		}finally{

			this_mon.exit();
		}

		int	fm_type = convertCacheToFileType( type );

		try{
			FMFile	fm_file	=
				file_manager.createFile(
					new FMFileOwner()
					{
						@Override
						public String
						getName()
						{
							return( owner.getCacheFileOwnerName() + "[" + my_id + "]" );
						}
						@Override
						public TOTorrentFile
						getTorrentFile()
						{
							return( owner.getCacheFileTorrentFile());
						}
						@Override
						public File
						getControlFileDir( )
						{
							return( owner.getCacheFileControlFileDir( ));
						}
					}, file, fm_type, force );

			TOTorrentFile	tf = owner.getCacheFileTorrentFile();

			CacheFile	cf;

			int	cache_mode = owner.getCacheMode();

			if ( cache_mode == CacheFileOwner.CACHE_MODE_EXPERIMENTAL ){

				cf = new CacheFileWithoutCacheMT( this, fm_file, tf );

			}else if (( tf != null && tf.getLength() < cache_files_not_smaller_than  ) || !cache_enabled || cache_mode == CacheFileOwner.CACHE_MODE_NO_CACHE ){

				cf = new CacheFileWithoutCache( this, fm_file, tf );

			}else{

				cf = new CacheFileWithCache( this, fm_file, tf );

				try{
					this_mon.enter();

					if ( updated_cache_files == null ){

						updated_cache_files = new WeakHashMap( cache_files );
					}
						// copy on write so readers don't need to synchronize or copy

					updated_cache_files.put( cf, null );

					if ( tf != null ){

						torrent_to_cache_file_map.put( tf, cf );
					}
				}finally{

					this_mon.exit();
				}
			}

			return( cf );

		}catch( FMFileManagerException e ){

			rethrow( null, e );

			return( null );
		}
	}

	@Override
	public CacheFileManagerStats
	getStats()
	{
		return( stats );
	}

	protected boolean
	isCacheEnabled()
	{
		return( cache_enabled );
	}

		/**
		 * allocates space but does NOT add it to the cache list due to synchronization issues. Basically
		 * the caller mustn't hold their monitor when calling allocate, as a flush may result in one or more
		 * other files being flushed which results in their monitor being taken, and we've got an A->B and
		 * B->A classic deadlock situation. However, we must keep the file's cache and our cache in step.
		 * It is not acceptable to have an entry inserted into our records but not in the file's as this
		 * then screws up the flush algorithm (which assumes that if it finds an entry in our list, a flush
		 * of that file is guaranteed to release space). Therefore we add the cache entry in addCacheSpace
		 * so that the caller can safely do this while synchronised firstly on its monitor and then we can
		 * sync on our. Hence we only ever get A->B monitor grabs which won't deadlock
		 * @param file
		 * @param buffer
		 * @param file_position
		 * @param length
		 * @return
		 * @throws CacheFileManagerException
		 */

	protected CacheEntry
	allocateCacheSpace(
		int					entry_type,
		CacheFileWithCache	file,
		DirectByteBuffer	buffer,
		long				file_position,
		int					length )

		throws CacheFileManagerException
	{
		boolean	ok 	= false;
		boolean	log	= false;

		while( !ok ){

				// musn't invoke synchronised CacheFile methods while holding manager lock as this
				// can cause deadlocks (as CacheFile calls manager methods with locks)

			CacheEntry	oldest_entry	= null;

			try{
				this_mon.enter();

				if ( length < cache_space_free || cache_space_free == cache_size ){

					ok	= true;

				}else{

					oldest_entry = (CacheEntry)cache_entries.keySet().iterator().next();
				}
			}finally{

				this_mon.exit();
			}

			if ( !ok ){

				log	= true;

				long	old_free	= cache_space_free;

				CacheFileWithCache	oldest_file = oldest_entry.getFile();

				try{

					oldest_file.flushCache( oldest_entry.getFilePosition(), true, cache_minimum_free_size );

				}catch( CacheFileManagerException e ){

						// if the flush failed on a file other than this one then we don't report the error here,
						// rather we tag the existing file as failed so that when it is next accessed the error
						// will be reported

					if ( oldest_file != file ){

						oldest_file.setPendingException( e );

					}else{

						throw( e );
					}
				}

				long	flushed = cache_space_free - old_free;

				if (Logger.isEnabled()) {
					TOTorrentFile tf = file.getTorrentFile();
					TOTorrent torrent = tf == null ? null : tf.getTorrent();
					Logger.log(new LogEvent(torrent, LOGID,
							"DiskCache: cache full, flushed " + flushed + " from "
									+ oldest_file.getName()));
				}

				if ( flushed == 0 ){

					try{
						this_mon.enter();

						if (	cache_entries.size() > 0 &&
								(CacheEntry)cache_entries.keySet().iterator().next() == oldest_entry ){

								// hmm, something wrong with cache as the flush should have got rid
								// of at least the oldest entry

							throw( new CacheFileManagerException( null, "Cache inconsistent: 0 flushed"));
						}
					}finally{

						this_mon.exit();
					}
				}
			}
		}

		CacheEntry	entry = new CacheEntry( entry_type, file, buffer, file_position, length );

		if (log && Logger.isEnabled()) {
			TOTorrentFile tf = file.getTorrentFile();
			TOTorrent torrent = tf == null ? null : tf.getTorrent();

			Logger.log(new LogEvent(torrent, LOGID, "DiskCache: cr="
					+ cache_bytes_read + ",cw=" + cache_bytes_written + ",fr="
					+ file_bytes_read + ",fw=" + file_bytes_written));
		}

		return( entry );
	}

	protected void
	cacheStatsAndCleaner()
	{
		SimpleTimer.addPeriodicEvent(
			"CacheFile:stats+cleaner",
			STATS_UPDATE_FREQUENCY,
			new TimerEventPerformer()
			{
				@Override
				public void
				perform(
					TimerEvent ev )
				{
					stats.update();

					// System.out.println( "cache file count = " + cache_files.size());

					Iterator	cf_it = cache_files.keySet().iterator();

					while(cf_it.hasNext()){

						((CacheFileWithCache)cf_it.next()).updateStats();
					}

					if ( --cleaner_ticks == 0 ){

						cleaner_ticks	= CACHE_CLEANER_TICKS;

						final Set	dirty_files	= new HashSet();

						final long	oldest	=SystemTime.getCurrentTime() - DIRTY_CACHE_WRITE_MAX_AGE;

						try{
							this_mon.enter();

							if ( updated_cache_files != null ){

								cache_files	= updated_cache_files;

								updated_cache_files	= null;
							}

							if ( cache_entries.size() > 0 ){

								Iterator it = cache_entries.keySet().iterator();

								while( it.hasNext()){

									CacheEntry	entry = (CacheEntry)it.next();

									// System.out.println( "oldest entry = " + ( now - entry.getLastUsed()));

									if ( entry.isDirty()){

										dirty_files.add( entry.getFile());
									}
								}
							}

							// System.out.println( "cache file = " + cache_files.size() + ", torrent map = " + torrent_to_cache_file_map.size());

						}finally{

							this_mon.exit();
						}

						Iterator	it = dirty_files.iterator();

						while( it.hasNext()){

							CacheFileWithCache	file = (CacheFileWithCache)it.next();

							try{

								TOTorrentFile	tf = file.getTorrentFile();

								long	min_flush_size	= -1;

								if ( tf != null ){

									min_flush_size	= tf.getTorrent().getPieceLength();

								}

								file.flushOldDirtyData( oldest, min_flush_size );

							}catch( CacheFileManagerException e ){

								file.setPendingException( e );

								// if this fails then the error should reoccur on a "proper"
								// flush later and be reported

								Debug.printStackTrace( e );

							}catch( Throwable e ){

								Debug.printStackTrace( e );
							}
						}
					}

				}
			}
		);

	}

		// must be called when the cachefileimpl is synchronised to ensure that the file's
		// cache view and our cache view are consistent

	protected void
	addCacheSpace(
		CacheEntry		new_entry )

		throws CacheFileManagerException
	{
		try{
			this_mon.enter();

			cache_space_free	-= new_entry.getLength();

				// 	System.out.println( "Total cache space = " + cache_space_free );

			cache_entries.put( new_entry, new_entry );

			if ( DEBUG ){

				CacheFileWithCache	file	= new_entry.getFile();

				long	total_cache_size	= 0;

				int		my_count = 0;

				Iterator it = cache_entries.keySet().iterator();

				while( it.hasNext()){

					CacheEntry	entry = (CacheEntry)it.next();

					total_cache_size	+= entry.getLength();

					if ( entry.getFile() == file ){

						my_count++;
					}
				}

				if ( my_count != file.cache.size()){

					Debug.out( "Cache inconsistency: my count = " + my_count + ", file = " + file.cache.size());

					throw( new CacheFileManagerException( null, "Cache inconsistency: counts differ"));

				}else{

					//System.out.println( "Cache: file_count = " + my_count );
				}

				if ( total_cache_size != cache_size - cache_space_free ){

					Debug.out( "Cache inconsistency: used_size = " + total_cache_size + ", free = " + cache_space_free + ", size = " + cache_size );

					throw( new CacheFileManagerException( null, "Cache inconsistency: sizes differ"));

				}else{

					//System.out.println( "Cache: usage = " + total_cache_size );
				}
			}
		}finally{

			this_mon.exit();
		}
	}

	protected void
	cacheEntryUsed(
		CacheEntry		entry )

		throws CacheFileManagerException
	{
		try{
			this_mon.enter();

				// note that the "get" operation update the MRU in cache_entries

			if ( cache_entries.get( entry ) == null ){

				Debug.out( "Cache inconsistency: entry missing on usage" );

				throw( new CacheFileManagerException( null, "Cache inconsistency: entry missing on usage"));

			}else{

				entry.used();
			}
		}finally{

			this_mon.exit();
		}
	}

	protected void
	releaseCacheSpace(
		CacheEntry		entry )

		throws CacheFileManagerException
	{
		entry.getBuffer().returnToPool();

		try{
			this_mon.enter();

			cache_space_free	+= entry.getLength();

			if ( cache_entries.remove( entry ) == null ){

				Debug.out( "Cache inconsistency: entry missing on removal" );

				throw( new CacheFileManagerException( null, "Cache inconsistency: entry missing on removal"));
			}

			/*
			if ( 	entry.getType() == CacheEntry.CT_READ_AHEAD ){

				if ( entry.getUsageCount() < 2 ){

					System.out.println( "ra: not used" );

				}else{

					System.out.println( "ra: used" );
				}
			}
			*/

			// System.out.println( "Total cache space = " + cache_space_free );
		}finally{

			this_mon.exit();
		}
	}

	protected long
	getCacheSize()
	{
		return( cache_size );
	}

	protected long
	getCacheUsed()
	{
		long free = cache_space_free;

		if ( free < 0 ){

			free	= 0;
		}

		return( cache_size - free );
	}

	protected void
	cacheBytesWritten(
		long		num )
	{
		try{
			this_mon.enter();

			cache_bytes_written	+= num;

			cache_write_count++;

		}finally{

			this_mon.exit();
		}
	}

	protected void
	cacheBytesRead(
		int		num )
	{
		try{
			this_mon.enter();

			cache_bytes_read	+= num;

			cache_read_count++;

		}finally{

			this_mon.exit();
		}
	}

	protected void
	fileBytesWritten(
		long	num )
	{
		try{
			this_mon.enter();

			file_bytes_written	+= num;

			file_write_count++;

		}finally{

			this_mon.exit();
		}
	}

	protected void
	fileBytesRead(
		int		num )
	{
		try{
			this_mon.enter();

			file_bytes_read	+= num;

			file_read_count++;
		}finally{

			this_mon.exit();
		}
	}

	protected long
	getBytesWrittenToCache()
	{
		return( cache_bytes_written );
	}

	protected long
	getBytesWrittenToFile()
	{
		return( file_bytes_written );
	}

	protected long
	getBytesReadFromCache()
	{
		return( cache_bytes_read );
	}

	protected long
	getBytesReadFromFile()
	{
		return( file_bytes_read );
	}

	public long
	getCacheReadCount()
	{
		return( cache_read_count );
	}

	public long
	getCacheWriteCount()
	{
		return( cache_write_count );
	}

	public long
	getFileReadCount()
	{
		return( file_read_count );
	}

	public long
	getFileWriteCount()
	{
		return( file_write_count );
	}

	protected void
	closeFile(
		CacheFileWithCache	file )
	{
		TOTorrentFile	tf = file.getTorrentFile();

		if ( tf != null && torrent_to_cache_file_map.get( tf ) != null ){

			try{
				this_mon.enter();

				torrent_to_cache_file_map.remove( tf );

			}finally{
				this_mon.exit();
			}
		}
	}

    protected boolean[] getBytesInCache(TOTorrent torrent, long[] absoluteOffsets, long[] lengths)
    {
    	//sanity checks
    	if(absoluteOffsets.length != lengths.length)
    		throw new IllegalArgumentException("Offsets/Lengths mismatch");
    	long prevEnding = 0;
    	for(int i = 0;i<lengths.length;i++)
    	{
    		if(absoluteOffsets[i]<prevEnding || lengths[i] <= 0 )
    			throw new IllegalArgumentException("Offsets/Lengths are not in ascending order");
    		prevEnding = absoluteOffsets[i]+lengths[i];
    	}


		TOTorrentFile[]	files = torrent.getFiles();
		long[] fileOffsets = new long[files.length];

		boolean[] results = new boolean[absoluteOffsets.length];
		Arrays.fill(results, true); // assume everything to be cached, then check for the opposite

		final long first = absoluteOffsets[0];
		final long last = absoluteOffsets[absoluteOffsets.length-1]+lengths[lengths.length-1];
		long fileOffset = 0;
		int firstFile = -1;
		boolean lockAcquired = false;

		Map localCacheMap = new LightHashMap();

		try {
			for(int i=0;i<files.length;i++)
			{
				TOTorrentFile	tf = files[i];
				long length = tf.getLength();
				fileOffsets[i] = fileOffset;
				if(firstFile == -1 && fileOffset <= first && first < fileOffset+length)
				{
					firstFile = i;
					this_mon.enter();
					lockAcquired = true;
				}

				if(fileOffset > last)
					break;

				if(lockAcquired)
				{
					CacheFileWithCache	cache_file = (CacheFileWithCache)torrent_to_cache_file_map.get( tf );
					localCacheMap.put(tf, cache_file);
				}

				fileOffset += length;
			}
		} finally {
			if(lockAcquired)
				this_mon.exit();
		}



		for (int i=firstFile;-1 < i && i <files.length;i++){
			TOTorrentFile	tf = files[i];
			CacheFileWithCache	cache_file = (CacheFileWithCache)localCacheMap.get( tf );

			long length = tf.getLength();

			fileOffset = fileOffsets[i];
			if(fileOffset > last)
				break;

			if(cache_file != null)
				cache_file.getBytesInCache(results,absoluteOffsets,lengths);
			else // we have no cache file and thus no cache entries
				for(int j=0;j<results.length;j++) // check if any chunks fall into this non-file
					if((absoluteOffsets[j] < fileOffset+length && absoluteOffsets[j] > fileOffset) || (absoluteOffsets[j]+lengths[j] < fileOffset+length &&  absoluteOffsets[j]+lengths[j] > fileOffset))
						results[j] = false; // no file -> no cache entry
		}

		if(!lockAcquired) // never found a matching torrentfile
			Arrays.fill(results, false);


    	return results;
    }

	protected void
	rethrow(
		CacheFile				file,
		FMFileManagerException 	e )

		throws CacheFileManagerException
	{
		Throwable 	cause = e.getCause();

		CacheFileManagerException result;
		
		if ( cause != null ){

			result = new CacheFileManagerException( file, e.getMessage(), cause );
			
		}else{

			result = new CacheFileManagerException( file, e.getMessage(), e );
		}
		
		if ( e.getType() == FMFileManagerException.ET_FILE_OR_DIR_MISSING ){
			
			result.setType( CacheFileManagerException.ET_FILE_OR_DIR_MISSING );
		}
		
		throw( result );
	}

	@Override
	public void
	generate(
		IndentWriter		writer )
	{
		writer.println( "Cache Manager" );

		try{
			writer.indent();

			Iterator it;

				// grab a copy to avoid potential deadlock as we never take the manager monitor
				// and then the file's own monitor, always the other way around

			try{
				this_mon.enter();

				it = new ArrayList( cache_entries.keySet()).iterator();

			}finally{

				this_mon.exit();
			}

			writer.println( "Entries = " + cache_entries.size());

			Set	files = new HashSet();

			while( it.hasNext()){

				CacheEntry	entry = (CacheEntry)it.next();

				CacheFileWithCache file = entry.getFile();

				if( !files.contains( file )){

					files.add( file );

					TOTorrentFile torrentFile = file.getTorrentFile();
					String fileLength = "";
					try {
						fileLength = "" + file.getLength();
					} catch (Exception e) {
						if (torrentFile != null)
							fileLength = "" + torrentFile.getLength();
					}
					String	hash = "<unknown>";

					try{
						if (torrentFile != null)
							hash = ByteFormatter.encodeString( torrentFile.getTorrent().getHash());

					}catch( Throwable e ){
					}

					String name = file.getName();

					writer.println("File: " + Debug.secretFileName(name) + ", size "
							+ fileLength + ", torrent " + hash + ", access = "
							+ file.getAccessMode());
				}
			}
		}finally{

			writer.exdent();
		}
	}

	@Override
	public void
	setFileLinks(
		TOTorrent		torrent,
		LinkFileMap		links )
	{
		file_manager.setFileLinks( torrent, links );
	}
}
