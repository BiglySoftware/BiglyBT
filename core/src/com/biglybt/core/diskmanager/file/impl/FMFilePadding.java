/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.core.diskmanager.file.impl;

import java.io.File;

import com.biglybt.core.diskmanager.file.FMFile;
import com.biglybt.core.diskmanager.file.FMFileManagerException;
import com.biglybt.core.diskmanager.file.FMFileOwner;
import com.biglybt.core.util.DirectByteBuffer;
import com.biglybt.core.util.FileUtil;
import com.biglybt.core.util.StringInterner;

public class 
FMFilePadding
	implements FMFile
{
	private static byte[]	ZERO_BYTES = new byte[1024];
			
	private final FMFileOwner		owner;
	private final long				length;
	private final boolean			is_clone;
	
	private StringInterner.FileKey	file;
	private int		mode	= FMFile.FM_READ;
	private boolean	is_open;
	
	protected
	FMFilePadding(
		FMFileOwner				_owner,
		StringInterner.FileKey	_file,
		boolean					_is_clone )
	{
		owner		= _owner;
		length		= owner.getTorrentFile().getLength();
		is_clone	= _is_clone;
		
		file	= _file;
	}
	
	public String
	getName()
	{
		return( owner.getName());
	}

	public boolean
	exists()
	{
		return( true );
	}

	public FMFileOwner
	getOwner()
	{
		return( owner );
	}

	public void
	moveFile(
		File						new_file,
		FileUtil.ProgressListener	pl )

		throws FMFileManagerException
	{	
		file = new StringInterner.FileKey( new_file );
		
		pl.bytesDone( length );
		
		pl.complete();
	}

	public void
	renameFile(
		String		new_name )

		throws FMFileManagerException
	{
		file = new StringInterner.FileKey( FileUtil.newFile( file.getFile().getParentFile(), new_name ));
	}

	public void
	setAccessMode(
		int		_mode )

		throws FMFileManagerException
	{
		mode	= _mode;
	}

	public int
	getAccessMode()
	{
		return( mode );
	}

	public void
	setStorageType(
		int		type,
		boolean	force )

		throws FMFileManagerException
	{		
	}

	public int
	getStorageType()
	{
		return( FMFile.FT_LINEAR );
	}

	public void
	ensureOpen(
		String	reason )

		throws FMFileManagerException
	{
		is_open = true;
	}

	public long
	getLength()

		throws FMFileManagerException
	{
		return( length );
	}

	public void
	setLength(
		long		length )

		throws FMFileManagerException
	{	
	}

	public void
	setPieceComplete(
		int					piece_number,
		DirectByteBuffer	piece_data )

		throws FMFileManagerException
	{
	}

	public void
	read(
		DirectByteBuffer	buffer,
		long				offset )

		throws FMFileManagerException
	{
		while( true ){
			
			int rem = buffer.remaining( DirectByteBuffer.SS_FILE );
			
			if ( rem == 0 ){
				
				return;
			}
			
			buffer.put( DirectByteBuffer.SS_FILE, ZERO_BYTES, 0, Math.min( rem, ZERO_BYTES.length ));
		}
	}

	public void
	read(
		DirectByteBuffer[]	buffers,
		long				offset )

		throws FMFileManagerException
	{	
		for ( DirectByteBuffer buffer: buffers ){
			
			read( buffer, 0 );
		}
	}

	public void
	write(
		DirectByteBuffer	buffer,
		long				position )

		throws FMFileManagerException
	{
	}

	public void
	write(
		DirectByteBuffer[]	buffers,
		long				position )

		throws FMFileManagerException
	{	
	}

	public void
	flush()

		throws FMFileManagerException
	{	
	}

	public void
	close()

		throws FMFileManagerException
	{
		is_open = false;
	}

	public boolean
	isOpen()
	{
		return( is_open );
	}

	public void
	delete()

		throws FMFileManagerException
	{
		is_open = false;
	}
	
	public FMFile
	createClone()

		throws FMFileManagerException
	{
		return( new FMFilePadding( owner, file, true ));
	}

	public boolean
	isClone()
	{
		return( is_clone );
	}
	
	public long
	getLastModified()
	{
		return( 0 );
	}
}
