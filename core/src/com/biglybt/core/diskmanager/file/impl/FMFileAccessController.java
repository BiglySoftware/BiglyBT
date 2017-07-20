/*
 * Created on 30-Nov-2005
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
import java.io.RandomAccessFile;

import com.biglybt.core.diskmanager.file.FMFile;
import com.biglybt.core.diskmanager.file.FMFileManagerException;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentFile;
import com.biglybt.core.util.*;

public class
FMFileAccessController
	implements FMFileAccess
{
	private static final String REORDER_SUFFIX = ".2";

	private static final boolean TEST_PIECE_REORDER = System.getProperty(SystemProperties.SYSPROP_FILE_PIECE_REORDER_FORCE, "0" ).equals( "1" );

	static{
		if ( TEST_PIECE_REORDER ){

			Debug.out( "*** Piece reordering storage forced ***" );
		}
	}
	private final FMFileImpl	owner;

	private int		type		= FMFile.FT_LINEAR;

	private File	control_dir;
	private String	controlFileName;

	private FMFileAccess	file_access;

	protected
	FMFileAccessController(
		FMFileImpl	_file,
		int			_target_type )

		throws FMFileManagerException
	{
		if ( TEST_PIECE_REORDER ){

			_target_type = FMFile.FT_PIECE_REORDER;
		}

		owner		= _file;

		// actual file shouldn't exist for change to occur - it is the responsibility
		// of the caller to delete the file first and take consequent actions (in
		// particular force recheck the file to ensure that the loss in save state
		// is represented in the resume view of the world )

		// in the future, if we support format conversion, this obviously changes

		setControlFile();

		if ( control_dir == null ){

			// Debug.out( "No control file" ); in optimised environments we don't support compact and return null here

			if ( _target_type == FMFile.FT_LINEAR ){

				file_access = new FMFileAccessLinear( owner );

			}else{

				throw( new FMFileManagerException( "Compact storage not supported: no control file available" ));
			}

		}else{

			if ( new File( control_dir, controlFileName ).exists()){

				type = FMFile.FT_COMPACT;

			}else if ( new File( control_dir, controlFileName + REORDER_SUFFIX ).exists()){

				type = _target_type==FMFile.FT_PIECE_REORDER?FMFile.FT_PIECE_REORDER:FMFile.FT_PIECE_REORDER_COMPACT;

			}else{

				if ((_target_type == FMFile.FT_PIECE_REORDER || _target_type == FMFile.FT_PIECE_REORDER_COMPACT )){

					File	target_file = owner.getLinkedFile();

					if ( target_file.exists()){

						FMFileAccessPieceReorderer.recoverConfig(
							owner.getOwner().getTorrentFile(),
							target_file,
							new File( control_dir, controlFileName + REORDER_SUFFIX ), _target_type );
					}

					type = _target_type;

				}else{

					type = FMFile.FT_LINEAR;
				}
			}

			if ( type == FMFile.FT_LINEAR ){

				file_access = new FMFileAccessLinear( owner );

			}else if ( type == FMFile.FT_COMPACT ){

				file_access =
					new FMFileAccessCompact(
							owner.getOwner().getTorrentFile(),
							control_dir,
							controlFileName,
							new FMFileAccessLinear( owner ));
			}else{

				file_access =
					new FMFileAccessPieceReorderer(
							owner.getOwner().getTorrentFile(),
							control_dir,
							controlFileName + REORDER_SUFFIX,
							type,
							new FMFileAccessLinear( owner ));
			}

			convert( _target_type );
		}
	}

	protected void
	convert(
		int					target_type )

		throws FMFileManagerException
	{
		if ( type == target_type ){

			return;
		}

		if ( type == FMFile.FT_PIECE_REORDER || target_type == FMFile.FT_PIECE_REORDER ){

			if ( target_type == FMFile.FT_PIECE_REORDER_COMPACT || type == FMFile.FT_PIECE_REORDER_COMPACT ){

					// these two access modes are in fact identical at the moment

				type = target_type;

				return;
			}

			throw( new FMFileManagerException( "Conversion to/from piece-reorder not supported" ));
		}

		File	file = owner.getLinkedFile();

		RandomAccessFile raf = null;

		boolean	ok	= false;

		try{
			FMFileAccess	target_access;

			if ( target_type == FMFile.FT_LINEAR ){

				target_access = new FMFileAccessLinear( owner );

			}else{

				target_access = new FMFileAccessCompact(
										owner.getOwner().getTorrentFile(),control_dir,
										controlFileName,
										new FMFileAccessLinear( owner ));
			}

			if ( file.exists()){

				raf = new RandomAccessFile( file, FMFileImpl.WRITE_ACCESS_MODE);

					// due to the simplistic implementation of compact we only actually need to deal with
					// the last piece of the file (first piece is in the right place already)

				FMFileAccessCompact	compact_access;

				if ( target_type == FMFile.FT_LINEAR ){

					compact_access = (FMFileAccessCompact)file_access;

				}else{

					 compact_access = (FMFileAccessCompact)target_access;
				}

				long	length = file_access.getLength( raf );

				long	last_piece_start 	= compact_access.getLastPieceStart();
				long	last_piece_length 	= compact_access.getLastPieceLength();

					// see if we have any potential data for the last piece

				if ( last_piece_length > 0 && length > last_piece_start ){

					long	data_length = length - last_piece_start;

					if ( data_length > last_piece_length ){

						Debug.out("data length inconsistent: len=" + data_length + ",limit=" + last_piece_length );

						data_length = last_piece_length;
					}

					DirectByteBuffer	buffer =
						DirectByteBufferPool.getBuffer( DirectByteBuffer.AL_FILE, (int)data_length );

					try{

						file_access.read( raf, new DirectByteBuffer[]{ buffer }, last_piece_start );

							// see if we need to truncate

						if ( target_type == FMFile.FT_COMPACT ){

							long	first_piece_length = compact_access.getFirstPieceLength();

							long	physical_length = raf.length();

							if ( physical_length > first_piece_length ){

								raf.setLength( first_piece_length );
							}
						}

						buffer.flip( DirectByteBuffer.AL_FILE );

						target_access.write( raf, new DirectByteBuffer[]{ buffer }, last_piece_start );

					}finally{

						buffer.returnToPool();
					}
				}else{

						// no last piece, truncate after the first piece

					if ( target_type == FMFile.FT_COMPACT ){

						long	first_piece_length = compact_access.getFirstPieceLength();

						long	physical_length = raf.length();

						if ( physical_length > first_piece_length ){

							raf.setLength( first_piece_length );
						}
					}
				}

				target_access.setLength( raf, length );

				target_access.flush();
			}

			type		= target_type;
			file_access	= target_access;

			ok	= true;

		}catch( Throwable e ){

			Debug.printStackTrace( e );

			throw( new FMFileManagerException( "convert fails", e ));

		}finally{

			try{
				if ( raf != null ){

					try{
						raf.close();

					}catch( Throwable e ){

							// override original exception if there isn't one

						if ( ok ){

							ok	= false;

							throw( new FMFileManagerException( "convert fails", e ));
						}
					}
				}
			}finally{

				if ( !ok ){

						// conversion failed - replace with linear access, caller is responsible for
						// handling this (marking file requiring recheck)

					type		= FMFile.FT_LINEAR;
					file_access = new FMFileAccessLinear( owner );
				}

				if ( type == FMFile.FT_LINEAR ){

					new File(control_dir,controlFileName).delete();
				}
			}
		}
	}

	protected void
	setControlFile()
	{
		TOTorrentFile	tf = owner.getOwner().getTorrentFile();

		if ( tf == null ){

			controlFileName = null;
			control_dir 	= null;

		}else{

			TOTorrent	torrent = tf.getTorrent();

			TOTorrentFile[]	files = torrent.getFiles();

			int	file_index = -1;

			for (int i=0;i<files.length;i++){

				if ( files[i] == tf ){

					file_index = i;

					break;
				}
			}

			if ( file_index == -1 ){

				Debug.out("File '" + owner.getName() + "' not found in torrent!" );

				controlFileName = null;
				control_dir 	= null;

			}else{

				control_dir 	= owner.getOwner().getControlFileDir( );
				controlFileName =  StringInterner.intern("fmfile" + file_index + ".dat");
			}
		}
	}


	public void
	setStorageType(
		int					new_type )

		throws FMFileManagerException
	{
		convert( new_type );
	}

	public int
	getStorageType()
	{
		return( type );
	}


		// FileAccess

	@Override
	public void
	aboutToOpen()

		throws FMFileManagerException
	{
		file_access.aboutToOpen();
	}

	@Override
	public long
	getLength(
		RandomAccessFile		raf )

		throws FMFileManagerException
	{
		return( file_access.getLength( raf ));
	}

	@Override
	public void
	setLength(
		RandomAccessFile		raf,
		long					length )

		throws FMFileManagerException
	{
		file_access.setLength( raf, length );
	}

	@Override
	public boolean
	isPieceCompleteProcessingNeeded(
		int					piece_number )
	{
		return( file_access.isPieceCompleteProcessingNeeded( piece_number ));
	}

	@Override
	public void
	setPieceComplete(
		RandomAccessFile	raf,
		int					piece_number,
		DirectByteBuffer	piece_data )

		throws FMFileManagerException
	{
		file_access.setPieceComplete( raf, piece_number, piece_data );
	}

	@Override
	public void
	read(
		RandomAccessFile	raf,
		DirectByteBuffer[]	buffers,
		long				offset )

		throws FMFileManagerException
	{
		file_access.read( raf, buffers, offset );
	}

	@Override
	public void
	write(
		RandomAccessFile		raf,
		DirectByteBuffer[]		buffers,
		long					position )

		throws FMFileManagerException
	{
		file_access.write( raf, buffers, position );
	}

	@Override
	public void
	flush()

		throws FMFileManagerException
	{
		file_access.flush();
	}

	@Override
	public FMFileImpl
	getFile()
	{
		return( owner );
	}

	@Override
	public String
	getString()
	{
		return( "type=" + type + ",acc=" + file_access.getString());
	}
}
