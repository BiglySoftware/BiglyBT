/*
 * Created on 28-Sep-2005
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

package com.biglybt.core.diskmanager.file.impl;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.diskmanager.file.FMFile;
import com.biglybt.core.diskmanager.file.FMFileManagerException;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentFile;
import com.biglybt.core.util.*;

public class
FMFileAccessPieceReorderer
	implements FMFileAccess
{
/*
 * Idea is to grow the file as needed on a piece-write basis
 *
 * Each file in general starts with a part of a piece and then is optionally
 * followed by zero or more complete pieces and ends with an option part of a piece.
 *
 * The first part-piece of the file is always stored in position.
 *
 * Whenever we receive a write request we calculate which piece number(s) it affects
 * If we have already allocated piece sized chunks for the pieces then we simply write
 * to the relevant part of the file
 * If we haven't then we allocate new piece size chunks at file end and record their position in
 * the control file. If it now turns out that we have allocated the space required for a piece previously
 * completed then we copy that piece data into the new block and reuse the space it has been
 * copied from for the new chunk
 *
 * When allocating space for the last part-piece we allocate an entire piece sized chunk and
 * trim later
 *
 * Whenever a piece is marked as complete we look up its location. If the required piece
 * of the file has already been allocated (and its not already in the right place) then
 * we swap the piece data at that location with the current piece's. If the file chunk hasn't
 * been allocated yet then we leave the piece where it is - it'll be moved later.
 *
 * If the control file is lost then there is an opportunity to recover completed pieces by
 * hashing all of the allocated chunks and checking the SHA1 results with the file's piece hashes.
 * However, this would require the addition of further interfaces etc to integrate somehow with
 * the existing force-recheck functionality...
 *
 * Obviously the setLength/getLength calls just have to be consistent, they don't actually
 * modify the length of the physical file
 *
 * Conversion between storage formats is another possibility to consider - conversion from this
 * to linear can fairly easily be done here as it just needs pieces to be written to their
 * correct locations. Conversion to this format can't be done here as we don't know which
 * pieces and blocks contain valid data. I guess such details could be added to the
 * setStorageType call as a further parameter
 */
	private static final boolean TRACE	= false;

	private static final int MIN_PIECES_REORDERABLE	= 3;	// first piece fixed at file start so need 3 to do anything worthwhile

	private static final byte SS_FILE = DirectByteBuffer.SS_FILE;

	private static final int	DIRT_CLEAN				= 0;
	private static final int	DIRT_DIRTY				= 1;
	private static final int	DIRT_NEVER_WRITTEN		= 2;

	private static final long	DIRT_FLUSH_MILLIS		= 30*1000;

	private final FMFileAccess	delegate;
	private final File			control_dir;
	private final String			control_file;
	private final int				storage_type;

	private int			piece_size;

	private int			first_piece_length;
	private int			first_piece_number;
	private int			last_piece_length;

	private int			num_pieces;

	private int		previous_storage_type	= -1;

	private long	current_length;
	private int[]	piece_map;
	private int[]	piece_reverse_map;
	private int		next_piece_index;

	private int		dirt_state;
	private long	dirt_time		= -1;

	protected
	FMFileAccessPieceReorderer(
		TOTorrentFile	_torrent_file,
		File			_control_dir,
		String			_control_file,
		int				_storage_type,
		FMFileAccess	_delegate )

		throws FMFileManagerException
	{
		delegate		= _delegate;
		control_dir		= _control_dir;
		control_file	= _control_file;
		storage_type	= _storage_type;

		try{
			first_piece_number 	= _torrent_file.getFirstPieceNumber();

			num_pieces = _torrent_file.getLastPieceNumber() - first_piece_number + 1;

			if ( num_pieces >= MIN_PIECES_REORDERABLE ){

				piece_size = (int)_torrent_file.getTorrent().getPieceLength();

				TOTorrent	torrent = _torrent_file.getTorrent();

				long	file_length	= _torrent_file.getLength();

				long	file_offset_in_torrent = 0;

				TOTorrentFile[] files = torrent.getFiles();

				for (int i=0;i<files.length;i++){

					TOTorrentFile	f = files[i];

					if ( f == _torrent_file ){

						break;
					}

					file_offset_in_torrent	+= f.getLength();
				}

				int first_piece_offset 	= (int)( file_offset_in_torrent % piece_size );

				first_piece_length	= piece_size - first_piece_offset;

				long	file_end = file_offset_in_torrent + file_length;


				last_piece_length = (int)( file_end - (( file_end / piece_size ) * piece_size ));

				if ( last_piece_length == 0 ){

					last_piece_length = piece_size;
				}

			}

			dirt_state = FileUtil.newFile( control_dir, control_file ).exists()?DIRT_CLEAN:DIRT_NEVER_WRITTEN;

		}catch( Throwable e ){

			throw( new FMFileManagerException( "Piece-reorder file init fail", e ));
		}
	}

	@Override
	public void
	aboutToOpen()

		throws FMFileManagerException
	{
			// ensure control file exists as this marks the file as piece-reordered
			// always do this, even for < MIN_PIECES_REORDERABLE piece files as
			// we still need the control file to exist

		if ( dirt_state == DIRT_NEVER_WRITTEN ){

			writeConfig();
		}
	}

	@Override
	public long
	getLength(
		FileAccessor		fa )

		throws FMFileManagerException
	{
		if ( num_pieces >= MIN_PIECES_REORDERABLE ){

			if ( piece_map == null ){

				readConfig();
			}

			try{
				boolean attempt_recovery = false;

				long max_length = first_piece_length + (num_pieces-2)*(long)piece_size + last_piece_length;

					// to cope with add-for-seeding mixed with the way the core handles move-to directory discovery we
					// also need to have recovery code in this branch to deal with the situation where the file was initially
					// opened pointing at a non-existant file and therefore missed the normal recovery path

				if ( current_length == 0 && next_piece_index == 1 ){

					attempt_recovery = true;

				}else if ( storage_type == FMFile.FT_PIECE_REORDER && previous_storage_type == FMFile.FT_PIECE_REORDER_COMPACT ){

						// if we are switching from compact to non-compact then the current_length will may well have been
						// set to the torrent size (during file allocation assuming incremental creation is disable (the default))
						// To handle the case where someone has dropped in a completed file and switched the state to 'normal' we need
						// to trigger the recovery code

					if ( current_length == max_length ){

						long physical_length = fa.getLength();

						if ( physical_length == current_length ){

							current_length = 0;

							attempt_recovery = true;
						}
					}
				}

				if ( attempt_recovery ){

					long physical_length = fa.getLength();

					if ( physical_length > current_length ){

						physical_length = Math.min( physical_length, max_length );

						if ( physical_length > current_length ){

							current_length = physical_length;

							int	piece_count = (int)(( current_length + piece_size - 1 )/piece_size) + 1;

							if ( piece_count > num_pieces ){

								piece_count = num_pieces;
							}

							for ( int i=1;i<piece_count;i++){

								piece_map[i] 			= i;
								piece_reverse_map[i]	= i;
							}

							next_piece_index = piece_count;

							setDirty();
						}
					}
				}
			}catch( IOException e ){
			}

			return( current_length );

		}else{

			return( delegate.getLength( fa ));
		}
	}

	@Override
	public void
	setLength(
		FileAccessor			fa,
		long					length )

		throws FMFileManagerException
	{
		if ( num_pieces >= MIN_PIECES_REORDERABLE ){

			if ( piece_map == null ){

				readConfig();
			}

			if ( current_length != length ){

				current_length = length;

				setDirty();
			}
		}else{

			delegate.setLength( fa, length );
		}
	}

	protected long
	getPieceOffset(
		FileAccessor		fa,
		int					piece_number,
		boolean				allocate_if_needed )

		throws FMFileManagerException
	{
		if ( piece_map == null ){

			readConfig();
		}

		int index = getPieceIndex( fa, piece_number, allocate_if_needed );

		if ( index < 0 ){

			return( index );

		}else if ( index == 0 ){

			return( 0 );

		}else if ( index == 1 ){

			return( first_piece_length );

		}else{

			return( first_piece_length + ((index-1)*(long)piece_size ));
		}
	}

	protected int
	readWritePiece(
		FileAccessor			fa,
		DirectByteBuffer[]		buffers,
		int						piece_number,
		int						piece_offset,
		boolean					is_read )

		throws FMFileManagerException
	{
		String	str = is_read?"read":"write";

		if ( piece_number >= num_pieces ){

			throw( new FMFileManagerException( "Attempt to " + str + " piece " + piece_number + ": last=" + num_pieces ));
		}

		int	this_piece_size = piece_number==0?first_piece_length:(piece_number==(num_pieces-1)?last_piece_length:piece_size);

		final int	piece_space = this_piece_size - piece_offset;

		if ( piece_space <= 0 ){

			throw( new FMFileManagerException( "Attempt to " + str + " piece " + piece_number + ", offset " + piece_offset + " - no space in piece" ));
		}

		int	rem_space = piece_space;

		int[]	limits = new int[buffers.length];

		for ( int i=0;i<buffers.length;i++ ){

			DirectByteBuffer buffer = buffers[i];

			limits[i] = buffer.limit( SS_FILE );

			int	rem = buffer.remaining( SS_FILE );

			if ( rem > rem_space ){

				buffer.limit( SS_FILE, buffer.position( SS_FILE ) + rem_space );

				rem_space = 0;

			}else{

				rem_space -= rem;
			}
		}

		try{

			long piece_start = getPieceOffset( fa, piece_number, !is_read );

			if ( TRACE ){
				System.out.println( str + " to " + piece_number + "/" + piece_offset + "/" + this_piece_size + "/" + piece_space + "/" + rem_space + "/" + piece_start );
			}

			if ( piece_start == -1 ){

				return( 0 );
			}

			long piece_io_position = piece_start + piece_offset;

			if ( is_read ){

				delegate.read( fa, buffers, piece_io_position );

			}else{

				delegate.write( fa, buffers, piece_io_position );
			}

			return( piece_space - rem_space );

		}finally{

			for ( int i=0;i<buffers.length;i++ ){

				buffers[i].limit( SS_FILE, limits[i] );
			}
		}
	}

	protected void
	readWrite(
		FileAccessor			fa,
		DirectByteBuffer[]		buffers,
		long					position,
		boolean					is_read )

		throws FMFileManagerException
	{
		long	total_length = 0;

		for ( DirectByteBuffer buffer: buffers ){

			total_length += buffer.remaining( SS_FILE );
		}

		if ( !is_read && position + total_length > current_length ){

			current_length = position + total_length;

			setDirty();
		}

		long	current_position = position;

		while( total_length > 0 ){

			int	piece_number;
			int	piece_offset;

			if ( current_position < first_piece_length ){

				piece_number 	= 0;
				piece_offset	= (int)current_position;

			}else{

				long	offset = current_position - first_piece_length;

				piece_number 	= (int)( offset / piece_size ) + 1;

				piece_offset	= (int)( offset % piece_size );
			}

			int	count = readWritePiece( fa, buffers, piece_number, piece_offset, is_read );

			if ( count == 0 ){

				if ( is_read ){

						// fill remaining space with zeros so we're consistent

					for ( DirectByteBuffer buffer: buffers ){

						ByteBuffer bb = buffer.getBuffer( SS_FILE );

						int	rem = bb.remaining();

						if ( rem > 0 ){

							bb.put( new byte[rem] );

								// not great this... to obey the normal file access semantics reads are
								// never partial (normally we'd extend the file to accommodate the read but
								// in the case of re-order access we don't want to do this when hash checking
								// a file. The issue is in the unlikely case where our padding happens to
								// match the real file contents and results in a piece hash check succeeding
								// even though the data doesn't actually exist on disk... So I've added this
								// flag to cause hash checking to always fail if the resulting data isn't 'real'
								// this of course relies on this buffer being passed back to the checking process
								// intact, which is the case currently during rechecks...

							buffer.setFlag( DirectByteBuffer.FL_CONTAINS_TRANSIENT_DATA );
						}
					}
				}else{

					throw( new FMFileManagerException( "partial write operation" ));
				}

				return;
			}

			total_length 		-= count;
			current_position 	+= count;
		}
	}

	@Override
	public void
	read(
		FileAccessor			fa,
		DirectByteBuffer[]		buffers,
		long					position )

		throws FMFileManagerException
	{
		if ( num_pieces >= MIN_PIECES_REORDERABLE ){

			readWrite( fa, buffers, position, true );

		}else{

			delegate.read( fa, buffers, position );
		}
	}

	@Override
	public void
	write(
		FileAccessor			fa,
		DirectByteBuffer[]		buffers,
		long					position )

		throws FMFileManagerException
	{
		if ( num_pieces >= MIN_PIECES_REORDERABLE ){

			readWrite( fa, buffers, position, false );

		}else{

			delegate.write( fa, buffers, position );
		}
	}

	@Override
	public void
	flush()

		throws FMFileManagerException
	{
		if ( num_pieces >= MIN_PIECES_REORDERABLE ){

			if ( dirt_state != DIRT_CLEAN ){

				writeConfig();
			}
		}else{

			delegate.flush();
		}
	}

	@Override
	public boolean
	isPieceCompleteProcessingNeeded(
		int					piece_number )
	{
		if ( num_pieces >= MIN_PIECES_REORDERABLE ){

				// note that it is possible to reduce the number of piece moves at the expense
				// of complicating the allocation process. We have the advantage here of having
				// the piece data already in memory. We also don't want to defer a mass of IO
				// until the download completes, hence interfering with other stuff such as
				// streaming. So I'm going to stick with this approach.

			piece_number = piece_number - first_piece_number;

			if ( TRACE ){
				System.out.println( "isPieceCompleteProcessingNeeded: " + piece_number );
			}

			if ( piece_number >= next_piece_index ){

					// nothing stored yet in the location where this piece belongs

				if ( TRACE ){
					System.out.println( "   nothing stored" );
				}

				return( false );
			}

			int	store_index = piece_map[ piece_number ];

			if ( store_index == -1 ){

					// things screwed up, return true to trigger subsequent fail

				if ( TRACE ){
					System.out.println( "   screwed" );
				}

				return( true );
			}

			if ( piece_number == store_index ){

					// already in the right place

				if ( TRACE ){
					System.out.println( "    already in right place" );
				}

				return( false );
			}

			if ( TRACE ){
				System.out.println( "   needs moving" );
			}

			return( true );

		}else{

			return( delegate.isPieceCompleteProcessingNeeded( piece_number ));
		}
	}

	@Override
	public void
	setPieceComplete(
		FileAccessor		fa,
		int					piece_number,
		DirectByteBuffer	piece_data )

		throws FMFileManagerException
	{
			// note, some of this logic repeated above

		if ( num_pieces >= MIN_PIECES_REORDERABLE ){

				// note that it is possible to reduce the number of piece moves at the expense
				// of complicating the allocation process. We have the advantage here of having
				// the piece data already in memory. We also don't want to defer a mass of IO
				// until the download completes, hence interfering with other stuff such as
				// streaming. So I'm going to stick with this approach.

			piece_number = piece_number - first_piece_number;

			if ( TRACE ){
				System.out.println( "pieceComplete: " + piece_number );
			}

			if ( piece_number >= next_piece_index ){

					// nothing stored yet in the location where this piece belongs

				return;
			}

			int	store_index = getPieceIndex( fa, piece_number, false );

			if ( store_index == -1 ){

				throw( new FMFileManagerException( "piece marked as complete but not yet allocated" ));
			}

			if ( piece_number == store_index ){

					// already in the right place

				if ( TRACE ){
					System.out.println( "    already in right place" );
				}

				return;
			}

				// find out what's currently stored in the place this piece should be

			int	swap_piece_number = piece_reverse_map[ piece_number ];

			if ( swap_piece_number < 1 ){

				throw( new FMFileManagerException( "Inconsistent: failed to find piece to swap" ));
			}

			if ( TRACE ){
				System.out.println( "    swapping " + piece_number + " and " + swap_piece_number + ": " + piece_number + " <-> " + store_index );
			}

			DirectByteBuffer temp_buffer = DirectByteBufferPool.getBuffer( SS_FILE, piece_size );

			DirectByteBuffer[] temp_buffers = new DirectByteBuffer[]{ temp_buffer };

			try{
				long	store_offset = first_piece_length + ((store_index-1)*(long)piece_size );
				long	swap_offset	 = first_piece_length + ((piece_number-1)*(long)piece_size );

				delegate.read( fa, temp_buffers, swap_offset );

				piece_data.position( SS_FILE, 0 );

				delegate.write( fa, new DirectByteBuffer[]{ piece_data }, swap_offset );

				temp_buffer.position( SS_FILE, 0 );

				delegate.write( fa, temp_buffers, store_offset );

				piece_map[ piece_number ] 			= piece_number;
				piece_reverse_map[ piece_number ] 	= piece_number;

				piece_map[ swap_piece_number ] 		= store_index;
				piece_reverse_map[ store_index ] 	= swap_piece_number;

				setDirty();

				if ( piece_number == num_pieces - 1 ){

					long	file_length = swap_offset + last_piece_length;

					if ( delegate.getLength( fa ) > file_length ){

						if ( TRACE ){
							System.out.println( "    truncating file to correct length of " + file_length );
						}

						delegate.setLength( fa, file_length );
					}
				}
			}finally{

				temp_buffer.returnToPool();
			}
		}else{

			delegate.setPieceComplete( fa, piece_number, piece_data );
		}
	}

	protected int
	getPieceIndex(
		FileAccessor		fa,
		int					piece_number,
		boolean				allocate_if_needed )

		throws FMFileManagerException
	{
		int	store_index = piece_map[ piece_number ];

		if ( store_index == -1 && allocate_if_needed ){

			store_index = next_piece_index++;

			if ( TRACE ){
				System.out.println( "getPiece(" + piece_number + "): allocated " + store_index );
			}

			piece_map[ piece_number ] = store_index;
			piece_reverse_map[ store_index ] = piece_number;

			if ( piece_number != store_index ){

					// not already in the right place, see if the piece we just allocated
					// corresponds to a piece previously allocated and swap if so

				int	swap_index = piece_map[ store_index ];

				if ( swap_index > 0 ){

					if ( TRACE ){
						System.out.println( "    piece number " + store_index + " already allocated at " + swap_index + ": moving piece ");
					}

					DirectByteBuffer temp_buffer = DirectByteBufferPool.getBuffer( SS_FILE, piece_size );

					DirectByteBuffer[] temp_buffers = new DirectByteBuffer[]{ temp_buffer };

					try{
						long	store_offset 	= first_piece_length + ((store_index-1)*(long)piece_size );
						long	swap_offset 	= first_piece_length + ((swap_index-1)*(long)piece_size );

						delegate.read( fa, temp_buffers, swap_offset );

						temp_buffer.position( SS_FILE, 0 );

						delegate.write( fa, temp_buffers, store_offset );

						piece_map[ store_index ] 			= store_index;
						piece_reverse_map[ store_index ] 	= store_index;

						piece_map[ piece_number ]		= swap_index;
						piece_reverse_map[ swap_index ]	= piece_number;

						if ( store_index == num_pieces - 1 ){

							long	file_length = store_offset + last_piece_length;

							if ( delegate.getLength( fa ) > file_length ){

								if ( TRACE ){
									System.out.println( "    truncating file to correct length of " + file_length );
								}

								delegate.setLength( fa, file_length );
							}
						}

						store_index = swap_index;

					}finally{

						temp_buffer.returnToPool();
					}
				}
			}

			setDirty();
		}

		// System.out.println( "getPiece: " + piece_number + "->" + store_index );

		return( store_index );
	}

	private void
	readConfig()

		throws FMFileManagerException
	{
		piece_map 			= new int[num_pieces];
		piece_reverse_map 	= new int[num_pieces];

		if ( dirt_state == DIRT_NEVER_WRITTEN ){

			Arrays.fill( piece_map, -1 );

			piece_map[0]			= 0;
			piece_reverse_map[0]	= 0;
			next_piece_index 		= 1;
			current_length			= 0;

		}else{

			Map map = FileUtil.readResilientFile( control_dir, control_file, false );

			Long	l_st		= (Long)map.get( "st" );

			if ( l_st != null ){
				previous_storage_type = l_st.intValue();
			}

			Long	l_len		= (Long)map.get( "len" );
			Long	l_next		= (Long)map.get( "next" );
			byte[]	piece_bytes = (byte[])map.get( "pieces" );

			if ( l_len == null || l_next == null || piece_bytes == null ){

				configBorked( "Failed to read control file " + FileUtil.newFile( control_dir, control_file ).getAbsolutePath() + ": map invalid - " + map );

				return;
			}

			current_length 		= l_len.longValue();
			next_piece_index	= l_next.intValue();

			if ( piece_bytes.length != num_pieces * 4 ){

				configBorked( "Failed to read control file " + FileUtil.newFile( control_dir, control_file ).getAbsolutePath() + ": piece bytes invalid" );

				return;
			}

			int	pos = 0;

			for (int i=0;i<num_pieces;i++){

				int	index =
					( piece_bytes[pos++] << 24 ) +
					(( piece_bytes[pos++] & 0xff ) << 16 ) +
					(( piece_bytes[pos++] & 0xff ) << 8 ) +
					(( piece_bytes[pos++] & 0xff ));

				piece_map[i] = index;

				if ( index != -1 ){

					piece_reverse_map[ index ] = i;
				}
			}
		}

		if ( TRACE ){
			System.out.println( "ReadConfig: length=" + current_length + ", next=" + next_piece_index );
		}
	}

	private void
	configBorked(
		String	error )

		throws FMFileManagerException
	{
		piece_map 			= new int[num_pieces];
		piece_reverse_map 	= new int[num_pieces];

		Arrays.fill( piece_map, -1 );

		piece_map[0]			= 0;
		piece_reverse_map[0]	= 0;
		current_length			= getFile().getLinkedFile().length();

		int	piece_count = (int)(( current_length + piece_size - 1 )/piece_size) + 1;

		if ( piece_count > num_pieces ){

			piece_count = num_pieces;
		}

		for ( int i=1;i<piece_count;i++){

			piece_map[i] 			= i;
			piece_reverse_map[i]	= i;
		}

		next_piece_index 		= piece_count;

		writeConfig();

		FMFileManagerException e = new FMFileManagerException( error );

		e.setRecoverable( false );

		throw( e );
	}

	protected void
	setDirty()

		throws FMFileManagerException
	{
		if ( dirt_state == DIRT_NEVER_WRITTEN ){

			Debug.out( "shouldn't get here" );

			writeConfig();

		}else{
			long	now = SystemTime.getMonotonousTime();

			if ( dirt_state == DIRT_CLEAN ){

				dirt_state 	= DIRT_DIRTY;
				dirt_time	= now;

			}else{

				if ( dirt_time >= 0 && ( now - dirt_time >= DIRT_FLUSH_MILLIS )){

					writeConfig();
				}
			}
		}
	}

	private static Map
	encodeConfig(
		int			storage_type,
		long		current_length,
		long		next_piece_index,
		int[]		piece_map )
	{
		Map	map = new HashMap();

		map.put( "st",		new Long( storage_type ));
		map.put( "len", 	new Long( current_length ));
		map.put( "next", 	new Long( next_piece_index ));

		byte[]	pieces_bytes = new byte[ piece_map.length * 4 ];

		int	pos = 0;

		for (int i=0;i<piece_map.length;i++){

			int	value = piece_map[i];

			if ( value == -1 ){

				pieces_bytes[pos++] = pieces_bytes[pos++] = pieces_bytes[pos++] = pieces_bytes[pos++] = (byte)0xff;

			}else{

				pieces_bytes[pos++] = (byte)( value >> 24 );
				pieces_bytes[pos++] = (byte)( value >> 16 );
				pieces_bytes[pos++] = (byte)( value >> 8 );
				pieces_bytes[pos++] = (byte)( value );
			}
		}

		map.put( "pieces", 	pieces_bytes );

		return( map );
	}

	protected static void
	recoverConfig(
		TOTorrentFile	torrent_file,
		File			data_file,
		File			config_file,
		int				storage_type )

		throws FMFileManagerException
	{
		// most likely add-for-seeding which means a recheck will occur. just map all existing pieces
		// to their correct positions and let the recheck sort things out

		int first_piece_number 	= torrent_file.getFirstPieceNumber();

		int num_pieces = torrent_file.getLastPieceNumber() - first_piece_number + 1;

		int piece_size = (int)torrent_file.getTorrent().getPieceLength();

		int[] piece_map 			= new int[num_pieces];

		Arrays.fill( piece_map, -1 );

		piece_map[0]	= 0;

		long	current_length = data_file.length();

		int	piece_count = (int)(( current_length + piece_size - 1 )/piece_size) + 1;

		if ( piece_count > num_pieces ){

			piece_count = num_pieces;
		}

		for ( int i=1;i<piece_count;i++){

			piece_map[i] = i;
		}

		int	next_piece_index = piece_count;

		Map	map = encodeConfig( storage_type, current_length, next_piece_index, piece_map );

		File	control_dir = config_file.getParentFile();

		if ( !control_dir.exists()){

			control_dir.mkdirs();
		}

		if ( !FileUtil.writeResilientFileWithResult( control_dir, config_file.getName(), map )){

			throw( new FMFileManagerException( "Failed to write control file " + config_file.getAbsolutePath()));
		}
	}

	private void
	writeConfig()

		throws FMFileManagerException
	{
		if ( piece_map == null ){

			readConfig();
		}

		Map	map = encodeConfig( storage_type, current_length, next_piece_index, piece_map );

		if ( !control_dir.exists()){

			control_dir.mkdirs();
		}

		if ( !FileUtil.writeResilientFileWithResult( control_dir, control_file, map )){

			throw( new FMFileManagerException( "Failed to write control file " + FileUtil.newFile( control_dir, control_file ).getAbsolutePath()));
		}

		if ( TRACE ){
			System.out.println( "WriteConfig: length=" + current_length + ", next=" + next_piece_index );
		}

		dirt_state 	= DIRT_CLEAN;
		dirt_time	= -1;
	}

	@Override
	public FMFileImpl
	getFile()
	{
		return( delegate.getFile());
	}

	@Override
	public String
	getString()
	{
		return( "reorderer" );
	}
}
