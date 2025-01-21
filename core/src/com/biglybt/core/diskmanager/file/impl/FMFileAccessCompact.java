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
import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.diskmanager.file.FMFile;
import com.biglybt.core.diskmanager.file.FMFileManagerException;
import com.biglybt.core.torrent.TOTorrentFile;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DirectByteBuffer;
import com.biglybt.core.util.FileUtil;

public class
FMFileAccessCompact
	implements FMFileAccess
{
	private final static byte SS = DirectByteBuffer.SS_FILE;

	protected static boolean
	isCompact(
		FMFileImpl		file,
		TOTorrentFile	torrent_file,
		File			control_dir,
		String			control_file_name,
		int				target_type )
	{
		if ( target_type == FMFile.FT_COMPACT ){
			
			int piece_size = (int) torrent_file.getTorrent().getPieceLength();
	
			long	torrent_file_length	= torrent_file.getLength();
	
			long	file_offset_in_torrent = torrent_file.getOffsetInTorrent();
	
			int piece_offset	= piece_size - (int)( file_offset_in_torrent % piece_size);
	
			if ( piece_offset == piece_size){
	
				piece_offset	= 0;
			}
	
			long first_piece_length	= piece_offset;
	
			if ( first_piece_length >= torrent_file_length && target_type == FMFile.FT_COMPACT ){
				
					// first piece takes up all the file, optimise to avoid state file
				
				return( true );
			}
						
			if ( FileUtil.existsWithCache( control_dir, control_file_name )){
				
				return( true );
			}
			
			long last_piece_length	= ( torrent_file_length - piece_offset ) % piece_size;

			long actual_file_length = file.getFile().length();
			
			if ( actual_file_length > 0 ){
				
					// there are 3 cases
					// 		file contains just the first piece		- file length = first piece
					//		file contains just the second piece		- file length = (blank) first piece + second piece 
					//		file contains both pieces				- file length = first_piece + second piece
					// so this boils down to two possibilities
				
				if ( 	actual_file_length == first_piece_length || 
						actual_file_length == first_piece_length + last_piece_length ){
					
						// looks like a completed compact file, lash up config
					
					try{
						Map<String, Long>	data = new HashMap<>();
		
						data.put( "version", version );
		
						data.put( "length", actual_file_length==first_piece_length + last_piece_length?torrent_file_length:actual_file_length );
		
						FileUtil.mkdirs( control_dir );
						
						FileUtil.writeResilientFile( control_dir, control_file_name, data, false );
		
						FileUtil.existsWithCacheClear( control_dir );
						
						return( true );
						
					}catch( Throwable e ){
		
						Debug.out( "Failed to write recovered state file" );
					}
				}
			}
			
			return( false );
		}
	
		return( FileUtil.existsWithCache( control_dir, control_file_name ));
	}
	
	private final File				controlFileDir;
	private final String			controlFileName;
	private final FMFileAccess		delegate;

	private volatile long		current_length_in_state;
	private static final long	version				= 0;

	private final boolean		state_required;
	
	private volatile boolean	write_required;

	private long	first_piece_start;
	private long	first_piece_length;
	private long	last_piece_start;
	private long	last_piece_length;

	protected
	FMFileAccessCompact(
		TOTorrentFile	torrent_file,
		File			_controlFileDir,
		String			_controlFileName,
		FMFileAccess	_delegate )

		throws FMFileManagerException
	{
		controlFileDir	= _controlFileDir;
		controlFileName = _controlFileName;
		delegate		= _delegate;

		try{
			int piece_size = (int) torrent_file.getTorrent().getPieceLength();

			long	file_length	= torrent_file.getLength();

			long	file_offset_in_torrent = torrent_file.getOffsetInTorrent();

			int piece_offset	= piece_size - (int)( file_offset_in_torrent % piece_size);

			if ( piece_offset == piece_size){

				piece_offset	= 0;
			}

			first_piece_length	= piece_offset;
			first_piece_start	= 0;

			if ( first_piece_length >= file_length ){

					// first piece takes up all the file,  optimise to avoid state file

				first_piece_length 	= file_length;
				last_piece_start	= file_length;
				last_piece_length	= 0;

				state_required = false;
				
			}else{

				last_piece_length	= ( file_length - piece_offset ) % piece_size;
				last_piece_start	= file_length - last_piece_length;
				
				state_required = true;
			}

			/*
			System.out.println(
					"file " + new String(torrent_file.getPathComponents()[0]) + ": " +
					"off = " + file_offset_in_torrent + ", len = " + file_length + ", fp = " + first_piece_start + "/" + first_piece_length +
					", lp = " + last_piece_start + "/" + last_piece_length );
			*/

			if ( state_required ){
				
				if ( !FileUtil.exists( FileUtil.newFile(controlFileDir,controlFileName))){
	
					if (!controlFileDir.isDirectory() && !FileUtil.mkdirs(controlFileDir)){
						
						throw new FMFileManagerException( FMFileManagerException.OP_OPEN, "Directory creation failed: "	+ controlFileDir);
					}
	
				}else{
	
					readState();
				}
			}
		}catch( Throwable e ){

			throw( new FMFileManagerException( FMFileManagerException.OP_OPEN, "Compact file init fail", e ));
		}
	}

	protected long
	getFirstPieceStart()
	{
		return( first_piece_start );
	}

	protected long
	getFirstPieceLength()
	{
		return( first_piece_length );
	}

	protected long
	getLastPieceStart()
	{
		return( last_piece_start );
	}

	protected long
	getLastPieceLength()
	{
		return( last_piece_length );
	}

	@Override
	public void
	aboutToOpen()

		throws FMFileManagerException
	{
		delegate.aboutToOpen();
	}

	@Override
	public long
	getLength(
		FileAccessor		fa )

		throws FMFileManagerException
	{
		if ( state_required ){
		
			return( current_length_in_state );
			
		}else{
			
			return( delegate.getLength( fa ));
		}
	}

	@Override
	public void
	setLength(
		FileAccessor		fa,
		long				length )

		throws FMFileManagerException
	{
		if ( state_required ){
			
			if ( length != current_length_in_state ){
	
				current_length_in_state	= length;
	
				write_required = true;
			}
		}else{
			
			delegate.setLength( fa, length );
		}
	}

	protected void
	read(
		FileAccessor		fa,
		DirectByteBuffer	buffer,
		long				position )

		throws FMFileManagerException
	{
		int	original_limit	= buffer.limit(SS);

		try{
			int	len = original_limit - buffer.position(SS);

			// System.out.println( "compact: read - " + position + "/" + len );

				// deal with any read access to the first piece

			if ( position < first_piece_start + first_piece_length ){

				int	available = (int)( first_piece_start + first_piece_length - position );

				if ( available >= len ){

						// all they require is in the first piece

					// System.out.println( "    all in first piece" );

					delegate.read( fa, new DirectByteBuffer[]{ buffer }, position );

					position	+= len;
					len			= 0;
				}else{

						// read goes past end of first piece

					// System.out.println( "    part in first piece" );

					buffer.limit( SS, buffer.position(SS) + available );

					delegate.read( fa, new DirectByteBuffer[]{ buffer }, position );

					buffer.limit( SS, original_limit );

					position	+= available;
					len			-= available;
				}
			}

			if ( len == 0 ){

				return;
			}

				// position is at start of gap between start and end - work out how much,
				// if any, space has been requested

			long	space = last_piece_start - position;

			if ( space > 0 ){

				if ( space >= len ){

						// all they require is space

					// System.out.println( "    all in space" );

					buffer.position( SS, original_limit );

					position	+= len;
					len			= 0;
				}else{

						// read goes past end of space

					// System.out.println( "    part in space" );

					buffer.position( SS, buffer.position(SS) + (int)space );

					position	+= space;
					len			-= space;
				}
			}

			if ( len == 0 ){

				return;
			}

				// lastly read from last piece

			// System.out.println( "    some in last piece" );

			delegate.read( fa, new DirectByteBuffer[]{ buffer }, ( position - last_piece_start ) + first_piece_length );

		}finally{

			buffer.limit(SS,original_limit);
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
		for (int i=0;i<buffers.length;i++){

			DirectByteBuffer	buffer = buffers[i];

			int	len = buffers[i].limit(SS) - buffers[i].position(SS);

			read( fa, buffer, position );

			int	rem = buffers[i].remaining( SS );

			position += len - rem;

			if ( rem > 0 ){

				break;
			}
		}

		if ( state_required ){
		
			if ( position > current_length_in_state ){

				setLength( fa, position );
			}
		}
	}

	protected void
	write(
		FileAccessor		fa,
		DirectByteBuffer	buffer,
		long				position )

		throws FMFileManagerException
	{
		int	original_limit	= buffer.limit(SS);

		try{
			int	len = original_limit - buffer.position(SS);

			// System.out.println( "compact: write - " + position + "/" + len );

				// deal with any write access to the first piece

			if ( position < first_piece_start + first_piece_length ){

				int	available = (int)( first_piece_start + first_piece_length - position );

				if ( available >= len ){

						// all they require is in the first piece

					// System.out.println( "    all in first piece" );

					delegate.write( fa, new DirectByteBuffer[]{buffer}, position );

					position	+= len;
					len			= 0;
				}else{

						// write goes past end of first piece

					// System.out.println( "    part of first piece" );

					buffer.limit( SS, buffer.position(SS) + available );

					delegate.write( fa, new DirectByteBuffer[]{buffer}, position );

					buffer.limit( SS, original_limit );

					position	+= available;
					len			-= available;
				}
			}

			if ( len == 0 ){

				return;
			}

				// position is at start of gap between start and end - work out how much,
				// if any, space has been requested

			long	space = last_piece_start - position;

			if ( space > 0 ){

				if ( space >= len ){

					// System.out.println( "    all in space" );

						// all they require is space

					buffer.position( SS, original_limit );

					position	+= len;
					len			= 0;
				}else{

						// write goes past end of space

					// System.out.println( "    part in space" );

					buffer.position( SS, buffer.position(SS) + (int)space );

					position	+= space;
					len			-= space;
				}
			}

			if ( len == 0 ){

				return;
			}

				// lastly write to last piece

			// System.out.println( "    some in last piece" );

			delegate.write( fa, new DirectByteBuffer[]{buffer}, ( position - last_piece_start ) + first_piece_length );

		}finally{

			buffer.limit(SS,original_limit);
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
		for (int i=0;i<buffers.length;i++){

			DirectByteBuffer	buffer = buffers[i];

			int	len = buffers[i].limit(SS) - buffers[i].position(SS);

			write( fa, buffer, position );

			position += len;
		}

		if ( state_required ){
			
			if ( position > current_length_in_state ){
	
				setLength( fa, position );
			}
		}
	}

	@Override
	public void
	flush()

		throws FMFileManagerException
	{
		writeState();
	}

	@Override
	public boolean
	isPieceCompleteProcessingNeeded(
		int					piece_number )
	{
		return( false );
	}

	@Override
	public void
	setPieceComplete(
		FileAccessor		fa,
		int					piece_number,
		DirectByteBuffer	piece_data )

		throws FMFileManagerException
	{
	}

	private void
	readState()

		throws FMFileManagerException
	{
		if ( state_required ){
			
			try{
				Map	data = FileUtil.readResilientFile( controlFileDir, controlFileName, false );
	
				if ( data != null && data.size() > 0 ){
	
					//Long	version = (Long)data.get( "version" );
	
					Long	length = (Long)data.get( "length" );
	
					current_length_in_state	= length.longValue();
				}
			}catch( Throwable e ){
	
				throw( new FMFileManagerException( FMFileManagerException.OP_READ, "Failed to read control file state", e ));
			}
		}
	}

	protected void
	writeState()

		throws FMFileManagerException
	{
		if ( state_required ){
			
			boolean	write = write_required;
	
			if ( write ){
	
				write_required	= false;
	
				try{
					Map<String, Long>	data = new HashMap<>();
	
					data.put( "version", version);
	
					data.put( "length", current_length_in_state );
						
					FileUtil.writeResilientFile(
							controlFileDir, controlFileName, data, false );
	
					FileUtil.existsWithCacheClear( controlFileDir );

				}catch( Throwable e ){
	
					throw( new FMFileManagerException( FMFileManagerException.OP_WRITE, "Failed to write control file state", e ));
				}
			}
		}
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
		if ( state_required ){
	
			return( "compact" );
		}else{
			
			return( "compact-opt" );
		}
	}
}
