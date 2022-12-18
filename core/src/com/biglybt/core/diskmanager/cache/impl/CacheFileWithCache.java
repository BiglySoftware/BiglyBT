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
import com.biglybt.core.diskmanager.cache.CacheFile;
import com.biglybt.core.diskmanager.cache.CacheFileManagerException;
import com.biglybt.core.diskmanager.file.FMFile;
import com.biglybt.core.diskmanager.file.FMFileManagerException;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentFile;
import com.biglybt.core.util.*;

public class
CacheFileWithCache
	implements CacheFile
{
  // Make code prettier by bringing over SS_CACHE from DirectByteBuffer
  private static final byte SS_CACHE = DirectByteBuffer.SS_CACHE;
  private static final LogIDs LOGID = LogIDs.CACHE;

  protected static final Comparator<CacheEntry> comparator = new
		Comparator<CacheEntry>()
		{
			@Override
			public int
		   	compare(
		   		CacheEntry o1,
		   		CacheEntry o2)
			{
				if ( o1 == o2 ){

					return( 0 );
				}
					// entries in the cache should never overlap

				long	offset1 = o1.getFilePosition();
				int		length1	= o1.getLength();

				long	offset2 = o2.getFilePosition();
				int		length2	= o2.getLength();

				if (offset1 + length1 <= offset2 || offset2 + length2 <= offset1 ||  length1 == 0 || length2 == 0)
				{
				}else{
					Debug.out( "Overlapping cache entries - " + o1.getString() + "/" + o2.getString());
				}

				return( offset1 - offset2 < 0?-1:1 );
			}
		};

	protected static boolean		TRACE					= false;
	protected final static boolean	TRACE_CACHE_CONTENTS	= false;

	static{

		TRACE = COConfigurationManager.getBooleanParameter( "diskmanager.perf.cache.trace" );

		if ( TRACE ){

			System.out.println( "**** Disk Cache tracing enabled ****" );
		}
	}

	protected final static int		READAHEAD_LOW_LIMIT		= 64*1024;
	protected final static int		READAHEAD_HIGH_LIMIT	= 256*1024;

	protected final static int		READAHEAD_HISTORY	= 32;

	protected final CacheFileManagerImpl		manager;
	protected final FMFile					file;
	protected int						access_mode	= CF_READ;
	protected TOTorrentFile				torrent_file;
	protected TOTorrent      			torrent;
	protected long						file_offset_in_torrent;

	protected long[]					read_history; // lazy allocation
	protected int						read_history_next	= 0;

	protected final TreeSet<CacheEntry>					cache			= new TreeSet<CacheEntry>(comparator);

	protected int 	current_read_ahead_size				= 0;

	protected static final int READ_AHEAD_STATS_WAIT_TICKS	= 10*1000 / CacheFileManagerImpl.STATS_UPDATE_FREQUENCY;

	protected int		read_ahead_stats_wait	= READ_AHEAD_STATS_WAIT_TICKS;

	protected final Average	read_ahead_made_average 	= Average.getInstance(CacheFileManagerImpl.STATS_UPDATE_FREQUENCY, 5);
	protected final Average	read_ahead_used_average 	= Average.getInstance(CacheFileManagerImpl.STATS_UPDATE_FREQUENCY, 5);

	protected long		read_ahead_bytes_made;
	protected long		last_read_ahead_bytes_made;
	protected long		read_ahead_bytes_used;
	protected long		last_read_ahead_bytes_used;

	protected int piece_size						= 0;
	protected int piece_offset						= 0;

	protected final AEMonitor				this_mon		= new AEMonitor( "CacheFile" );

	protected volatile CacheFileManagerException	pending_exception;

	private long	bytes_written;
	private long	bytes_read;

	protected
	CacheFileWithCache(
		CacheFileManagerImpl	_manager,
		FMFile					_file,
		TOTorrentFile			_torrent_file )
	{
		manager		= _manager;
		file		= _file;

		if ( _torrent_file != null ){

			torrent_file	= _torrent_file;

			torrent = torrent_file.getTorrent();

			piece_size	= (int)torrent.getPieceLength();

			for (int i=0;i<torrent.getFiles().length;i++){

				TOTorrentFile	f = torrent.getFiles()[i];

				if ( f == torrent_file ){

					break;
				}

				file_offset_in_torrent	+= f.getLength();
			}

			piece_offset	= piece_size - (int)( file_offset_in_torrent % piece_size );

			if ( piece_offset == piece_size ){

				piece_offset	= 0;
			}

			current_read_ahead_size	= Math.min( READAHEAD_LOW_LIMIT, piece_size );
		}
	}

	@Override
	public TOTorrentFile
	getTorrentFile()
	{
		return( torrent_file );
	}

	protected void
	updateStats()
	{
		long	made	= read_ahead_bytes_made;
		long	used	= read_ahead_bytes_used;

		long	made_diff	= made - last_read_ahead_bytes_made;
		long	used_diff	= used - last_read_ahead_bytes_used;

		read_ahead_made_average.addValue( made_diff );
		read_ahead_used_average.addValue( used_diff );

		last_read_ahead_bytes_made	= made;
		last_read_ahead_bytes_used	= used;

			// give changes made to read ahead size a chance to work through the stats
			// before recalculating

		if ( --read_ahead_stats_wait == 0 ){

			read_ahead_stats_wait	= READ_AHEAD_STATS_WAIT_TICKS;

				// see if we need to adjust the read-ahead size

			double	made_average	= read_ahead_made_average.getAverage();
			double	used_average	= read_ahead_used_average.getAverage();

				// if used average > 75% of made average then increase

			double 	ratio = used_average*100/made_average;

			if ( ratio > 0.75 ){

				current_read_ahead_size	+= 16*1024;

					// no bigger than a piece

				current_read_ahead_size	= Math.min( current_read_ahead_size, piece_size );

					// no bigger than the fixed max size

				current_read_ahead_size	= Math.min( current_read_ahead_size, READAHEAD_HIGH_LIMIT );

					// no bigger than a 16th of the cache, in case its really small (e.g. 1M)

				current_read_ahead_size = Math.min( current_read_ahead_size, (int)(manager.getCacheSize()/16 ));

			}else if ( ratio < 0.5 ){

				current_read_ahead_size	-= 16*1024;

					// no smaller than the min

				current_read_ahead_size = Math.max( current_read_ahead_size, READAHEAD_LOW_LIMIT );
			}
		}

		// System.out.println( "read-ahead: done = " + read_ahead_bytes_made + ", used = " + read_ahead_bytes_used + ", done_av = " + read_ahead_made_average.getAverage() + ", used_av = " +  read_ahead_used_average.getAverage()+ ", size = " + current_read_ahead_size );
	}

	protected void
	readCache(
		final DirectByteBuffer	file_buffer,
		final long				file_position,
		final boolean			recursive,
		final boolean			disable_read_cache )

		throws CacheFileManagerException
	{
		checkPendingException();

		final int	file_buffer_position	= file_buffer.position(SS_CACHE);
		final int	file_buffer_limit		= file_buffer.limit(SS_CACHE);

		final int	read_length	= file_buffer_limit - file_buffer_position;

		try{
			if ( manager.isCacheEnabled()){

				if (TRACE)
					Logger.log(new LogEvent(torrent, LOGID, "readCache: " + getName()
							+ ", " + file_position + " - "
							+ (file_position + read_length - 1) + ":" + file_buffer_position
							+ "/" + file_buffer_limit));

				if ( read_length == 0 ){

					return;	// nothing to do
				}

				long	writing_file_position	= file_position;
				int		writing_left			= read_length;

				boolean	ok 				= true;
				int		used_entries	= 0;
				long	used_read_ahead	= 0;



						// if we can totally satisfy the read from the cache, then use it
						// otherwise flush the cache (not so smart here to only read missing)

				try{

					this_mon.enter();


					if(read_history == null)
					{
						read_history = new long[ READAHEAD_HISTORY ];
						Arrays.fill( read_history, -1 );
					}

						// record the position of the byte *following* the end of this read

					read_history[read_history_next++]	= file_position + read_length;

					if ( read_history_next == READAHEAD_HISTORY ){

						read_history_next	= 0;
					}

					Iterator	it = cache.iterator();

					while( ok && writing_left > 0 && it.hasNext()){

						CacheEntry	entry = (CacheEntry)it.next();

						long	entry_file_position 	= entry.getFilePosition();
						int		entry_length			= entry.getLength();

						if ( entry_file_position > writing_file_position ){

								// data missing at the start of the read section

							ok = false;

							break;

						}else if ( entry_file_position + entry_length <= writing_file_position ){

								// not got there yet
						}else{

								// copy required amount into read buffer

							int		skip	= (int)(writing_file_position - entry_file_position);

							int		available = entry_length - skip;

							if ( available > writing_left ){

								available	= writing_left;
							}

							DirectByteBuffer	entry_buffer = entry.getBuffer();

							int					entry_buffer_position 	= entry_buffer.position(SS_CACHE);
							int					entry_buffer_limit		= entry_buffer.limit(SS_CACHE);

							try{

								entry_buffer.limit( SS_CACHE, entry_buffer_position + skip + available );

								entry_buffer.position( SS_CACHE, entry_buffer_position + skip );

								if (TRACE)
									Logger.log(new LogEvent(torrent, LOGID, "cacheRead: using "
											+ entry.getString() + "["
											+ entry_buffer.position(SS_CACHE) + "/"
											+ entry_buffer.limit(SS_CACHE) + "]" + "to write to ["
											+ file_buffer.position(SS_CACHE) + "/"
											+ file_buffer.limit(SS_CACHE) + "]"));

								used_entries++;

								file_buffer.put( SS_CACHE, entry_buffer );

								manager.cacheEntryUsed( entry );

							}finally{

								entry_buffer.limit( SS_CACHE, entry_buffer_limit );

								entry_buffer.position( SS_CACHE, entry_buffer_position );
							}

							writing_file_position	+= available;
							writing_left			-= available;

							if ( entry.getType() == CacheEntry.CT_READ_AHEAD ){

								used_read_ahead	+= available;
							}

						}
					}
				}finally{

					if ( ok ){

						read_ahead_bytes_used += used_read_ahead;
					}

					this_mon.exit();
				}

				if ( ok && writing_left == 0 ){

						// only record this as a cache read hit if we haven't just read the
						// data from the file system

					if ( !recursive ){

						manager.cacheBytesRead( read_length );

						bytes_read += read_length;
					}

					if (TRACE)
						Logger.log(new LogEvent(torrent, LOGID,
								"cacheRead: cache use ok [entries = " + used_entries + "]"));

				}else{

					if (TRACE)
						Logger.log(new LogEvent(torrent, LOGID,
								"cacheRead: cache use fails, reverting to plain read"));

						// reset in case we've done some partial reads

					file_buffer.position( SS_CACHE, file_buffer_position );

						// If read-ahead fails then we resort to a straight read
						// Read-ahead can fail if a cache-flush fails (e.g. out of disk space
						// on a file belonging to a different torrent than this.
						// We don't want such a failure to break this read operation

					for (int i=0;i<2;i++){

						try{
							boolean	do_read_ahead	=
										i == 0 &&		// first time round
										!recursive &&
										!disable_read_cache &&
										read_history != null &&
										manager.isReadCacheEnabled() &&
										read_length <  current_read_ahead_size &&
										file_position + current_read_ahead_size <= file.getLength();

							if ( do_read_ahead ){

									// only read ahead if this is a continuation of a prior read within history

								do_read_ahead	= false;

								for (int j=0;j<READAHEAD_HISTORY;j++){

									if ( read_history[j] == file_position ){

										do_read_ahead	= true;

										break;
									}
								}
							}

							int	actual_read_ahead = current_read_ahead_size;

							if ( do_read_ahead ){

									// don't read ahead over the end of a piece

								int	request_piece_offset = (int)((file_position - piece_offset ) % piece_size);

								if ( request_piece_offset < 0 ){

									request_piece_offset += piece_size;
								}

								//System.out.println( "request offset = " + request_piece_offset );

								int	data_left = piece_size - request_piece_offset;

								if ( data_left < actual_read_ahead ){

									actual_read_ahead	= data_left;

										// no point in using read-ahead logic if actual read ahead
										// smaller or same as request size!

									if ( actual_read_ahead <= read_length ){

										do_read_ahead	= false;
									}
									//System.out.println( "    trimmed to " + data_left );
								}
							}

							if ( do_read_ahead ){

								if (TRACE)
									Logger.log(new LogEvent(torrent, LOGID,
											"\tperforming read-ahead"));

								DirectByteBuffer	cache_buffer =
										DirectByteBufferPool.getBuffer( DirectByteBuffer.AL_CACHE_READ, actual_read_ahead );

								boolean	buffer_cached	= false;

								try{

										// must allocate space OUTSIDE sync block (see manager for details)

									CacheEntry	entry =
										manager.allocateCacheSpace(
												CacheEntry.CT_READ_AHEAD,
												this,
												cache_buffer, file_position, actual_read_ahead );

									entry.setClean();

									try{

										this_mon.enter();

											// flush before read so that any bits in cache get re-read correctly on read

										flushCache( file_position, actual_read_ahead, true, -1, 0, -1 );

										getFMFile().read( cache_buffer, file_position );

										read_ahead_bytes_made	+= actual_read_ahead;

										manager.fileBytesRead( actual_read_ahead );

										bytes_read += actual_read_ahead;

										cache_buffer.position( SS_CACHE, 0 );

										cache.add( entry );

										manager.addCacheSpace( entry );

									}finally{

										this_mon.exit();
									}

									buffer_cached	= true;

								}finally{

									if ( !buffer_cached ){

											// if the read operation failed, and hence the buffer
											// wasn't added to the cache, then release it here

										cache_buffer.returnToPool();
									}
								}

									// recursively read from the cache, should hit the data we just read although
									// there is the possibility that it could be flushed before then - hence the
									// recursion flag that will avoid this happening next time around

								readCache( file_buffer, file_position, true, disable_read_cache );

							}else{

								if (TRACE)
									Logger.log(new LogEvent(torrent, LOGID,
											"\tnot performing read-ahead"));

								try{

									this_mon.enter();

									flushCache( file_position, read_length, true, -1, 0, -1 );

									getFMFile().read( file_buffer, file_position );

								}finally{

									this_mon.exit();
								}

								manager.fileBytesRead( read_length );

								bytes_read += read_length;
							}

							break;

						}catch( CacheFileManagerException e ){

							if ( i == 1 ){

								throw( e );
							}

						}catch( FMFileManagerException e ){

							if ( i == 1 ){

								manager.rethrow(this,e);
							}
						}
					}
				}
			}else{

				try{
					getFMFile().read( file_buffer, file_position );

					manager.fileBytesRead( read_length );

					bytes_read += read_length;

				}catch( FMFileManagerException e ){

					manager.rethrow(this,e);
				}
			}
		}finally{

			if ( AEDiagnostics.CHECK_DUMMY_FILE_DATA ){

				long	temp_position = file_position + file_offset_in_torrent;

				file_buffer.position( SS_CACHE, file_buffer_position );

				while( file_buffer.hasRemaining( SS_CACHE )){

					byte	v = file_buffer.get( SS_CACHE );

					if ((byte)temp_position != v ){

						System.out.println( "readCache: read is bad at " + temp_position +
											": expected = " + (byte)temp_position + ", actual = " + v );

						file_buffer.position( SS_CACHE, file_buffer_limit );

						break;
					}

					temp_position++;
				}
			}
		}
	}

	protected void
	writeCache(
		DirectByteBuffer	file_buffer,
		long				file_position,
		boolean				buffer_handed_over )

		throws CacheFileManagerException
	{
		checkPendingException();

		boolean	buffer_cached	= false;
		boolean	failed			= false;

		try{
			int	file_buffer_position	= file_buffer.position(SS_CACHE);
			int file_buffer_limit		= file_buffer.limit(SS_CACHE);

			int	write_length = file_buffer_limit - file_buffer_position;

			if ( write_length == 0 ){

				return;	// nothing to do
			}

			if ( AEDiagnostics.CHECK_DUMMY_FILE_DATA ){

				long	temp_position = file_position + file_offset_in_torrent;

				while( file_buffer.hasRemaining( SS_CACHE )){

					byte	v = file_buffer.get( SS_CACHE );

					if ((byte)temp_position != v ){

						System.out.println( "writeCache: write is bad at " + temp_position +
											": expected = " + (byte)temp_position + ", actual = " + v );

						break;
					}

					temp_position++;
				}

				file_buffer.position( SS_CACHE, file_buffer_position );
			}

			if ( manager.isWriteCacheEnabled() ){

				if (TRACE)
					Logger.log(new LogEvent(torrent, LOGID, "writeCache: " + getName()
							+ ", " + file_position + " - "
							+ (file_position + write_length - 1) + ":" + file_buffer_position
							+ "/" + file_buffer_limit));

					// if the data is smaller than a piece and not handed over
                    // then it is most
					// likely apart of a piece at the start or end of a file. If
                    // so, copy it
					// and insert the copy into cache

				if ( 	( !buffer_handed_over ) &&
						write_length < piece_size ){

					if (TRACE)
						Logger.log(new LogEvent(torrent, LOGID,
								"    making copy of non-handedover buffer"));

					DirectByteBuffer	cache_buffer = DirectByteBufferPool.getBuffer( DirectByteBuffer.AL_CACHE_WRITE, write_length );

					cache_buffer.put( SS_CACHE, file_buffer );

					cache_buffer.position( SS_CACHE, 0 );

						// make it look like this buffer has been handed over

					file_buffer				= cache_buffer;

					file_buffer_position	= 0;
					file_buffer_limit		= write_length;

					buffer_handed_over	= true;
				}

				if ( buffer_handed_over ){

						// cache this write, allocate outside sync block (see manager for details)

					CacheEntry	entry =
						manager.allocateCacheSpace(
								CacheEntry.CT_DATA_WRITE,
								this,
								file_buffer,
								file_position,
								write_length );

					try{
						this_mon.enter();

						if ( access_mode != CF_WRITE ){

							throw( new CacheFileManagerException( this,"Write failed - cache file is read only" ));
						}

							// if we are overwriting stuff already in the cache then force-write overlapped
							// data (easiest solution as this should only occur on hash-fails)

							// do the flush and add sychronized to avoid possibility of another
							// thread getting in-between and adding same block thus causing mutiple entries
							// for same space

						flushCache( file_position, write_length, true, -1, 0, -1 );

						cache.add( entry );

						manager.addCacheSpace( entry );

					}finally{

						this_mon.exit();
					}

					manager.cacheBytesWritten( write_length );

					bytes_written += write_length;

					buffer_cached	= true;

				}else{

						// not handed over, invalidate any cache that exists for the area
						// as it is now out of date

					try{

						this_mon.enter();

						flushCache( file_position, write_length, true, -1, 0, -1 );

						getFMFile().write( file_buffer, file_position );

					}finally{

						this_mon.exit();
					}

					manager.fileBytesWritten( write_length );

					bytes_written += write_length;
				}
			}else{

				getFMFile().write( file_buffer, file_position );

				manager.fileBytesWritten( write_length );

				bytes_written += write_length;
			}

		}catch( CacheFileManagerException e ){

			failed	= true;

			throw( e );

		}catch( FMFileManagerException e ){

			failed	= true;

			manager.rethrow(this,e);

		}finally{

			if ( buffer_handed_over ){

				if ( !(failed || buffer_cached )){

					file_buffer.returnToPool();
				}
			}
		}
	}

	protected void
	flushCache(
		long				file_position,
		long				length,					// -1 -> do all from position onwards
		boolean				release_entries,
		long				minimum_to_release,		// -1 -> all
		long				oldest_dirty_time, 		// dirty entries newer than this won't be flushed
													// 0 -> now
		long				min_chunk_size )		// minimum contiguous size for flushing, -1 -> no limit

		throws CacheFileManagerException
	{
		try{
			flushCacheSupport( file_position, length, release_entries, minimum_to_release, oldest_dirty_time, min_chunk_size );

		}catch( CacheFileManagerException	e ){

			if ( !release_entries ){

					// make sure we release the offending buffer entries otherwise they'll hang around
					// in memory causing grief when the next attempt it made to flush them...

				flushCacheSupport( 0, -1, true, -1, 0, -1 );
			}

			throw( e );
		}
	}

	protected void
	flushCacheSupport(
		long				file_position,
		long				length,					// -1 -> do all from position onwards
		boolean				release_entries,
		long				minimum_to_release,		// -1 -> all
		long				oldest_dirty_time, 		// dirty entries newer than this won't be flushed
													// 0 -> now
		long				min_chunk_size )		// minimum contiguous size for flushing, -1 -> no limit

		throws CacheFileManagerException
	{
		try{
			this_mon.enter();

			if ( cache.size() == 0 ){

				return;
			}

			Iterator<CacheEntry>	it = cache.iterator();

			Throwable	last_failure = null;

			long	entry_total_released = 0;

			List<CacheEntry>	multi_block_entries		= new ArrayList<>();
			long				multi_block_start		= -1;
			long				multi_block_next		= -1;

			while( it.hasNext()){

				CacheEntry	entry = it.next();

				long	entry_file_position 	= entry.getFilePosition();
				int		entry_length			= entry.getLength();

				if ( entry_file_position + entry_length <= file_position ){

						// to the left

					continue;

				}else if ( length != -1 && file_position + length <= entry_file_position ){

						// to the right, give up

					break;
				}

					// overlap!!!!
					// we're going to deal with this entry one way or another. In particular if
					// we are releasing entries then this is guaranteed to be released, either directly
					// or via a flush if dirty

				boolean	dirty = entry.isDirty();

				try{

					if ( 	dirty &&
							(	oldest_dirty_time == 0 ||
								entry.getLastUsed() < oldest_dirty_time )){

						if ( multi_block_start == -1 ){

								// start of day

							multi_block_start	= entry_file_position;

							multi_block_next	= entry_file_position + entry_length;

							multi_block_entries.add( entry );

						}else if ( multi_block_next == entry_file_position ){

								// continuation, add in

							multi_block_next = entry_file_position + entry_length;

							multi_block_entries.add( entry );

						}else{

								// we've got a gap - flush current and start another series

								// set up ready for next block in case the flush fails - we try
								// and flush as much as possible in the face of failure

							boolean	skip_chunk	= false;

							if ( min_chunk_size != -1 ){

								if ( release_entries ){

									Debug.out( "CacheFile: can't use min chunk with release option" );
								}else{

									skip_chunk	= multi_block_next - multi_block_start < min_chunk_size;
								}
							}

							List<CacheEntry>	f_multi_block_entries	= multi_block_entries;
							long				f_multi_block_start		= multi_block_start;
							long				f_multi_block_next		= multi_block_next;

							multi_block_start	= entry_file_position;

							multi_block_next	= entry_file_position + entry_length;

							multi_block_entries	= new ArrayList<>();

							multi_block_entries.add( entry );

							if ( skip_chunk ){
								
								if (TRACE)
									Logger.log(new LogEvent(torrent, LOGID,
											"flushCache: skipping " + multi_block_entries.size()
													+ " entries, [" + multi_block_start + ","
													+ multi_block_next + "] as too small"));
							}else{

								multiBlockFlush(
										f_multi_block_entries,
										f_multi_block_start,
										f_multi_block_next,
										release_entries );
							}
						}
					}
				}catch( Throwable e ){

					last_failure	= e;

				}finally{

					if ( release_entries ){

						it.remove();

							// if it is dirty it will be released when the flush is done

						if ( !dirty ){

							manager.releaseCacheSpace( entry );
						}

						entry_total_released += entry.getLength();

						if ( minimum_to_release != -1 && entry_total_released > minimum_to_release ){

								// if this entry needs flushing this is done outside the loop

							break;
						}
					}
				}
			}

			if ( multi_block_start != -1 ){

				boolean	skip_chunk	= false;

				if ( min_chunk_size != -1 ){

					if ( release_entries ){

						Debug.out( "CacheFile: can't use min chunk with release option" );
					}else{

						skip_chunk	= multi_block_next - multi_block_start < min_chunk_size;
					}
				}

				if ( skip_chunk ){

					if (TRACE)
						Logger
								.log(new LogEvent(torrent, LOGID, "flushCache: skipping "
										+ multi_block_entries.size() + " entries, ["
										+ multi_block_start + "," + multi_block_next
										+ "] as too small"));

				}else{

					multiBlockFlush(
							multi_block_entries,
							multi_block_start,
							multi_block_next,
							release_entries );
				}
			}

			if ( last_failure != null ){

				if ( last_failure instanceof CacheFileManagerException ){

					throw((CacheFileManagerException)last_failure );
				}

				throw( new CacheFileManagerException( this,"cache flush failed", last_failure ));
			}
		}finally{

			this_mon.exit();
		}
	}

	protected void
	multiBlockFlush(
		List<CacheEntry>	multi_block_entries,
		long				multi_block_start,
		long				multi_block_next,
		boolean				release_entries )

		throws CacheFileManagerException
	{
		boolean	write_ok	= false;

		try{
			if (TRACE)
				Logger.log(new LogEvent(torrent, LOGID, "multiBlockFlush: writing "
						+ multi_block_entries.size() + " entries, [" + multi_block_start
						+ "," + multi_block_next + "," + release_entries + "]"));

			DirectByteBuffer[]	buffers = new DirectByteBuffer[ multi_block_entries.size()];

			long	expected_per_entry_write = 0;

			for (int i=0;i<buffers.length;i++){

				CacheEntry	entry = multi_block_entries.get(i);

					// sanitity check - we should always be flushing entire entries

				DirectByteBuffer	buffer = entry.getBuffer();

				if ( buffer.limit(SS_CACHE) - buffer.position(SS_CACHE) != entry.getLength()){

					throw( new CacheFileManagerException( this,"flush: inconsistent entry length, position wrong" ));
				}

				expected_per_entry_write	+= entry.getLength();

				buffers[i] = buffer;
			}

			long	expected_overall_write	= multi_block_next - multi_block_start;

			if ( expected_per_entry_write != expected_overall_write ){

				throw( new CacheFileManagerException( this,"flush: inconsistent write length, entrys = " + expected_per_entry_write + " overall = " + expected_overall_write ));

			}

			getFMFile().write( buffers, multi_block_start );

			manager.fileBytesWritten( expected_overall_write );

			// bytes_written += expected_overall_write;

			write_ok	= true;

		}catch( FMFileManagerException e ){

			throw( new CacheFileManagerException( this,"flush fails", e ));

		}finally{

			for (int i=0;i<multi_block_entries.size();i++){

				CacheEntry	entry = multi_block_entries.get(i);

				if ( release_entries ){

					manager.releaseCacheSpace( entry );

				}else{

					entry.resetBufferPosition();

					if ( write_ok ){

						entry.setClean();
					}
				}
			}
		}
	}

	protected void
	flushCache(
		long				file_start_position,
		boolean				release_entries,
		long				minumum_to_release )

		throws CacheFileManagerException
	{
		if ( manager.isCacheEnabled()){

			if (TRACE)
				Logger.log(new LogEvent(torrent, LOGID, "flushCache: " + getName()
						+ ", rel = " + release_entries + ", min = " + minumum_to_release));

			flushCache( file_start_position, -1, release_entries, minumum_to_release, 0, -1 );
		}
	}

		// this is the flush method used by the public methods directly (as opposed to those use when reading, writing etc)
		// and it is the place that pending exceptions are checked for. We don't want to check for this in the internal
		// logic for flushing as we need to be able to flush from files that have a pending error to clear the cache
		// state

	protected void
	flushCachePublic(
		boolean				release_entries,
		long				minumum_to_release )

		throws CacheFileManagerException
	{
		checkPendingException();

		flushCache(0, release_entries, minumum_to_release );
	}

	protected void
	flushOldDirtyData(
		long	oldest_dirty_time,
		long	min_chunk_size )

		throws CacheFileManagerException
	{
		if ( manager.isCacheEnabled()){

			if (TRACE)
				Logger.log(new LogEvent(torrent, LOGID, "flushOldDirtyData: "
						+ getName()));

			flushCache( 0, -1, false, -1, oldest_dirty_time, min_chunk_size );
		}
	}

	protected void
	flushOldDirtyData(
		long	oldest_dirty_time )

		throws CacheFileManagerException
	{
		flushOldDirtyData( oldest_dirty_time, -1 );
	}

	protected void
	getBytesInCache(
		boolean[] 	result,
		long[]		absolute_offsets,
		long[]		lengths )
	{
			// absolute offsets are in ascending order and might span the start/end of this file
			
			// cache entries are in ascending order
		
			// purpose of this function is to set toModify[i] to false if any part of [i] that falls in this file's span
			// is not in the cache
		
		
		int	num_entries = absolute_offsets.length;
		
		/*
		System.out.println( "to test" );
		
		for ( int i=0;i<num_entries;i++){
			
			System.out.println( "    " + absolute_offsets[i] + " / " + lengths[i] );
		}
		*/
		
		long file_start = file_offset_in_torrent;
		long file_end 	= file_start + torrent_file.getLength();
		
		while( num_entries > 0 ){
			
			if ( absolute_offsets[ num_entries-1 ] >= file_end ){
				
					// entry beyond this file extent, ignore
				
				num_entries--;
				
			}else{
				
				break;
			}
		}
		
		if ( num_entries == 0 ){
			
			return;
		}

		int current_index = 0;
		
			// skip any entries that come completely before this file
		
		while( absolute_offsets[ current_index ] + lengths[ current_index ] < file_start ){
			
			current_index++;
			
			if ( current_index == num_entries ){
				
				return;	// nothing of relevance here
			}
		}
		
		long overall_start 			= absolute_offsets[ current_index ];
		long overall_end_exclusive	= absolute_offsets[ num_entries -1 ] + lengths[ num_entries - 1 ];
				
		if ( !this_mon.enter(250)){

			Debug.outNoStack( "Failed to lock stats, abandoning" );

			return;
		}

		// System.out.println( "cache" );
		
		try{
				// subSet doesn't really work as a cache entry could overlap the first chunk and be relevant even though
				// its offset is < chunk start - I added in "-piece_size" in an attempt to ensure we grab such entries
				// which should generally be OK as we tend (?) not to cache lumps bigger than that...
			
			Set<CacheEntry> sub_map = cache.subSet(new CacheEntry( overall_start-file_start-piece_size), new CacheEntry( overall_end_exclusive-file_start));
			
			Iterator<CacheEntry> it = sub_map.iterator();
						
			long current_start 			= absolute_offsets[ current_index ];
			long current_end_exclusive	= current_start + lengths[ current_index ];
			
			while( current_index < num_entries && it.hasNext()){
				
				CacheEntry	entry = it.next();
				
				long cache_start = entry.getFilePosition() + file_start;
				
				// System.out.println( "    " + cache_start + " / " + entry.getLength());
				
				long cache_end_exclusive = cache_start + entry.getLength();
		
					// cache entry might overlap multiple chunks
				
				while( true ){
					
					boolean	next_cache	= false;

					if ( cache_end_exclusive <= current_start ){
						
						// cache entry entirely before, retry with next cache entry
						
						next_cache = true;
						
					}else{
							
						boolean next_chunk 	= false;

						if ( current_start < cache_start ){
							
								// missing a chunk before 
							
							result[current_index] = false;
							
							next_chunk = true;

						}else{ 
							
								// cache entry overlaps
							
							if ( cache_end_exclusive > current_end_exclusive ){
								
									// cache entry goes beyond current chunk so this chunk ok and move onto next chunk
								
								next_chunk = true;
								
							}else if ( cache_end_exclusive == current_end_exclusive ){

									// exact match at end of cache/chunk, move both on
								
								next_chunk 	= true;
								
								next_cache	= true;
								
							}else{
								
									// current chunk looking good but has remainder that needs to be checked against
									// next cache entry
								
								current_start = cache_end_exclusive;
								
								next_cache	= true;
							}
						}
						
						if ( next_chunk ){
							
							current_index++;
							
							if ( current_index == num_entries ){
								
								break;
							}
							
							current_start 			= absolute_offsets[ current_index ];
							current_end_exclusive	= current_start + lengths[ current_index ];
						}
					}
					
					if ( next_cache ){
						
						break;
					}
				}
			}

		}finally{
			
			this_mon.exit();
		}
		
			// stuff beyond end of last cache entry not available
		
		while( current_index < num_entries ){
			
			result[current_index++] = false;
		}
		
		/*
		
		final long baseOffset = file_offset_in_torrent;

		int i = 0;

		long first = absoluteOffsets[0];
		long last = absoluteOffsets[absoluteOffsets.length-1]+lengths[lengths.length-1];

		// chunk might span file boundaries
		long lastEnd = Math.max(absoluteOffsets[0],baseOffset);
		while(absoluteOffsets[i]+lengths[i] < baseOffset)
			i++;

		boolean doSkipping = true;

		if ( !this_mon.enter(250)){

			Debug.outNoStack( "Failed to lock stats, abandoning" );

			return;
		}

		System.out.println( "get for" );
		
		for (int j=0;j<absoluteOffsets.length;j++){
			System.out.println( "    " + absoluteOffsets[j] + " / " + lengths[j] );
		}
		
		try{
			Iterator<CacheEntry> it = cache.subSet(new CacheEntry(first-1-baseOffset), new CacheEntry(last-baseOffset)).iterator();
			//Iterator<CacheEntry> it = cache.iterator();

			System.out.println( "cache" );
			while(it.hasNext())
			{
				CacheEntry	entry = it.next();
				long startPos = entry.getFilePosition()+baseOffset;
				
				System.out.println( "    " + startPos + " / " + entry.getLength());
				
				long endPos = startPos+entry.getLength();
				// the following check ensures that we are within the interesting region
				if(startPos < first)
					continue; // skip forward until we reach a chunk

				// perform skipping from the previous round
				if(doSkipping)
					while(i < absoluteOffsets.length && absoluteOffsets[i] < startPos)
					{
						toModify[i] = false;
						i++;
					}

				if(i >= absoluteOffsets.length)
					break;

				doSkipping = false;

				if(startPos >= absoluteOffsets[i] && endPos >= absoluteOffsets[i]+lengths[i])
				{ // chunk completely falls into cache entry -> don't invalidate
					i++;
					doSkipping = true;
				} else if(startPos >= lastEnd)
				{ // chunk spans multiple cache entries AND we skipped -> invalidate
					doSkipping = true;
				} else if(startPos >= absoluteOffsets[i]+lengths[i])
				{  // end of a spanning chunk -> don't invalidate
					i++;
					doSkipping = true;
				}

				if(endPos > last)
					break;

				lastEnd = endPos;
			}

		} finally {
			this_mon.exit();
		}

		if(doSkipping) // we fell through the loop but there's still cleanup to do
			while(i<absoluteOffsets.length)
			{
				if(absoluteOffsets[i]+lengths[i] < baseOffset || absoluteOffsets[i] > baseOffset+torrent_file.getLength())
				{
					i++;
					continue;
				}
				toModify[i] = false;
				i++;
			}
			*/
	}


		// support methods

	protected void
	checkPendingException()

		throws CacheFileManagerException
	{
		if ( pending_exception != null ){

			throw( pending_exception );
		}
	}

	protected void
	setPendingException(
		CacheFileManagerException	e )
	{
		pending_exception	= e;
	}

	protected String
	getName()
	{
		return( file.getName());
	}

	protected FMFile
	getFMFile()
	{
		return( file );
	}

		// public methods

	@Override
	public boolean
	exists()
	{
		return( file.exists());
	}

	@Override
	public void
	moveFile(
		File						new_file,
		FileUtil.ProgressListener	pl )

		throws CacheFileManagerException
	{
		try{
			flushCachePublic( true, -1 );

			file.moveFile( new_file, pl );

		}catch( FMFileManagerException e ){

			manager.rethrow(this,e);
		}
	}

	@Override
	public void
	renameFile(
		String		new_name )

		throws CacheFileManagerException
	{
		try{
			flushCachePublic( true, -1 );

			file.renameFile( new_name );

		}catch( FMFileManagerException e ){

			manager.rethrow(this,e);
		}
	}

	@Override
	public void
	setAccessMode(
		int		mode )

		throws CacheFileManagerException
	{
		try{
			this_mon.enter();

			if ( access_mode != mode ){

				flushCachePublic( false, -1 );
			}

			file.setAccessMode( mode==CF_READ?FMFile.FM_READ:FMFile.FM_WRITE );

			access_mode	= mode;

		}catch( FMFileManagerException e ){

			manager.rethrow(this,e);

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public int
	getAccessMode()
	{
		return( access_mode );
	}

	@Override
	public void
	setStorageType(
		int		type,
		boolean	force )

		throws CacheFileManagerException
	{
		try{
			this_mon.enter();

			if ( getStorageType() != type ){

				flushCachePublic( false, -1 );
			}

			file.setStorageType( CacheFileManagerImpl.convertCacheToFileType( type ), force );

		}catch( FMFileManagerException e ){

			manager.rethrow(this,e);

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public int
	getStorageType()
	{
		return( CacheFileManagerImpl.convertFileToCacheType( file.getStorageType()));
	}

	@Override
	public long
	getLength()

		throws CacheFileManagerException
	{

			// not sure of the difference between "size" and "length" here. Old code
			// used to use "size" so I'm going to carry on for the moment in case
			// there is some weirdness here

		try{
				// bug found here with "incremental creation" failing with lots of hash
				// fails. Caused by the reported length not taking into account the cache
				// entries that have yet to be flushed.

			if ( manager.isCacheEnabled()){

				try{
					this_mon.enter();

					long	physical_size = file.exists() ?  file.getLength() : 0;

					Iterator	it = cache.iterator();

						// last entry is furthest down the file

					while( it.hasNext()){

						CacheEntry	entry = (CacheEntry)it.next();

						if ( !it.hasNext()){

							long	entry_file_position 	= entry.getFilePosition();
							int		entry_length			= entry.getLength();

							long	logical_size = entry_file_position + entry_length;

							if ( logical_size > physical_size ){

								physical_size	= logical_size;
							}
						}
					}

					return( physical_size );

				}finally{

					this_mon.exit();
				}
			}else{

				return( file.exists() ? file.getLength() : 0);
			}

		}catch( FMFileManagerException e ){

			manager.rethrow(this,e);

			return( 0 );
		}
	}

	@Override
	public long
	compareLength(
		long	compare_to )

		throws CacheFileManagerException
	{
		try{
				// we can optimise this if the file's already big enough as cache entries can
				// only make it bigger

			long	physical_length = file.exists() ? file.getLength() : 0;

			long	res = physical_length - compare_to;

			if ( res >= 0 ){

				return( res );
			}

			return( getLength() - compare_to );

		}catch( FMFileManagerException e ){

			manager.rethrow(this,e);

			return( 0 );
		}
	}

	@Override
	public void
	setLength(
		long		length )

		throws CacheFileManagerException
	{
		try{

				// flush in case length change will invalidate cache data (unlikely but possible)

			flushCachePublic( true, -1 );

			file.setLength( length );

		}catch( FMFileManagerException e ){

			manager.rethrow(this,e);
		}
	}

	@Override
	public void
	setPieceComplete(
		int					piece_number,
		DirectByteBuffer	piece_data )

		throws CacheFileManagerException
	{
		try{
			file.setPieceComplete( piece_number, piece_data );

		}catch( FMFileManagerException e ){

			manager.rethrow(this,e);
		}
	}

	@Override
	public void
	read(
		DirectByteBuffer[]	buffers,
		long				position,
		short				policy )

		throws CacheFileManagerException
	{
		for (int i=0;i<buffers.length;i++){

			DirectByteBuffer	buffer = buffers[i];

			int	len = buffer.remaining( DirectByteBuffer.SS_CACHE );

			try{
				read( buffer, position, policy );

				position += len;

			}catch( CacheFileManagerException e ){

				throw( new CacheFileManagerException( this, e.getMessage(), e, i ));
			}
		}
	}

	@Override
	public void
	read(
		DirectByteBuffer	buffer,
		long				position,
		short				policy )

		throws CacheFileManagerException
	{
		boolean	read_cache 	= ( policy & CP_READ_CACHE ) != 0;
		boolean	flush		= ( policy & CP_FLUSH ) != 0;

		if ( flush ){

			int	file_buffer_position	= buffer.position(DirectByteBuffer.SS_CACHE);
			int	file_buffer_limit		= buffer.limit(DirectByteBuffer.SS_CACHE);

			int	read_length	= file_buffer_limit - file_buffer_position;

			flushCache( position, read_length, false, -1, 0, -1 );
		}

		readCache( buffer, position, false, !read_cache );
	}

	@Override
	public void
	write(
		DirectByteBuffer	buffer,
		long				position )

		throws CacheFileManagerException
	{
		writeCache( buffer, position, false );
	}

	@Override
	public void
	write(
		DirectByteBuffer[]	buffers,
		long				position )

		throws CacheFileManagerException
	{
		for (int i=0;i<buffers.length;i++){

			DirectByteBuffer	buffer = buffers[i];

			int	len = buffer.remaining( DirectByteBuffer.SS_CACHE );

			try{
				write( buffer, position );

				position += len;

			}catch( CacheFileManagerException e ){

				throw( new CacheFileManagerException( this, e.getMessage(), e, i ));
			}
		}
	}

	@Override
	public void
	writeAndHandoverBuffer(
		DirectByteBuffer	buffer,
		long				position )

		throws CacheFileManagerException
	{
		writeCache( buffer, position, true );
	}

	@Override
	public void
	writeAndHandoverBuffers(
		DirectByteBuffer[]	buffers,
		long				position )

		throws CacheFileManagerException
	{
		for (int i=0;i<buffers.length;i++){

			DirectByteBuffer	buffer = buffers[i];

			int	len = buffer.remaining( DirectByteBuffer.SS_CACHE );

			try{
				writeAndHandoverBuffer( buffer, position );

				position += len;

			}catch( CacheFileManagerException e ){

				throw( new CacheFileManagerException( this, e.getMessage(), e, i ));
			}
		}
	}

	@Override
	public void
	flushCache()

		throws CacheFileManagerException
	{
		try{
			flushCachePublic( false, -1 );

			file.flush();

		}catch( FMFileManagerException e ){

			manager.rethrow(this,e);
		}
	}

	public void
	flushCache(
		long		file_position,
		int			length )

		throws CacheFileManagerException
	{
		try{
			flushCache( file_position, length, false, -1, 0, -1 );
			
			file.flush();

		}catch( FMFileManagerException e ){

			manager.rethrow(this,e);
		}
	}
	
	@Override
	public void
	clearCache()

		throws CacheFileManagerException
	{
		flushCachePublic(true, -1);
	}

	@Override
	public void
	close()

		throws CacheFileManagerException
	{
			// we've got to always close the file here, even if the flush fails

		boolean	fm_file_closed = false;

		try{
			flushCachePublic( true, -1 );

			file.close();

			fm_file_closed	= true;

		}catch( FMFileManagerException e ){

			manager.rethrow(this,e);

		}finally{

			if ( !fm_file_closed ){

				try{
					file.close();

				}catch( Throwable e ){

						// we're already on our way out via exception, no need to
						// throw a new one

				}
			}

			manager.closeFile( this );
		}
	}

	@Override
	public boolean
	isOpen()
	{
		return( file.isOpen());
	}

	@Override
	public long
	getSessionBytesRead()
	{
		return( bytes_read );
	}

	@Override
	public long
	getSessionBytesWritten()
	{
		return( bytes_written );
	}

	@Override
	public long
	getLastModified()
	{
		return( file.getLastModified());
	}
	
	@Override
	public void
	delete()

		throws CacheFileManagerException
	{
		try{

			file.delete();

		}catch( FMFileManagerException e ){

			manager.rethrow(this,e);
		}
	}
}
