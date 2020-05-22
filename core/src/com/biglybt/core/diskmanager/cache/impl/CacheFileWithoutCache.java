/*
 * Created on 02-Nov-2004
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

import com.biglybt.core.diskmanager.cache.CacheFile;
import com.biglybt.core.diskmanager.cache.CacheFileManagerException;
import com.biglybt.core.diskmanager.file.FMFile;
import com.biglybt.core.diskmanager.file.FMFileManagerException;
import com.biglybt.core.torrent.TOTorrentFile;
import com.biglybt.core.util.DirectByteBuffer;
import com.biglybt.core.util.FileUtil;

public class
CacheFileWithoutCache
	implements CacheFile
{
	protected final CacheFileManagerImpl		manager;
	protected final FMFile					file;
	protected final TOTorrentFile				torrent_file;

	private long	bytes_written;
	private long	bytes_read;

	protected
	CacheFileWithoutCache(
		CacheFileManagerImpl	_manager,
		FMFile					_file,
		TOTorrentFile			_torrent_file )
	{
		manager			= _manager;
		file			= _file;
		torrent_file	= _torrent_file;
		// System.out.println( "without cache = " + file.getFile().toString());
	}

	@Override
	public TOTorrentFile
	getTorrentFile()
	{
		return( torrent_file );
	}

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
			file.moveFile( new_file, pl );

		}catch( FMFileManagerException e ){

			manager.rethrow(this,e);
		}
	}

	@Override
	public void
	renameFile(
		String		new_file )

		throws CacheFileManagerException
	{
		try{
			file.renameFile( new_file );

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

			file.setAccessMode( mode==CF_READ?FMFile.FM_READ:FMFile.FM_WRITE );

		}catch( FMFileManagerException e ){

			manager.rethrow(this,e);
		}
	}

	@Override
	public int
	getAccessMode()
	{
		return( file.getAccessMode()==FMFile.FM_READ?CF_READ:CF_WRITE );
	}

	@Override
	public void
	setStorageType(
		int		type,
		boolean	force )

		throws CacheFileManagerException
	{
		try{

			file.setStorageType( CacheFileManagerImpl.convertCacheToFileType( type ), force);

		}catch( FMFileManagerException e ){

			manager.rethrow(this,e);
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
		try{

			return( file.exists() ? file.getLength() : 0);

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
		return( getLength() - compare_to );
	}

	@Override
	public void
	setLength(
		long		length )

		throws CacheFileManagerException
	{
		try{

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
		int	read_length	= 0;

		for (int i=0;i<buffers.length;i++){

			read_length += buffers[i].remaining(DirectByteBuffer.SS_CACHE);
		}

		try{
			file.read( buffers, position );

			manager.fileBytesRead( read_length );

			bytes_read += read_length;

		}catch( FMFileManagerException e ){

			manager.rethrow(this,e);
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
		int	read_length	= buffer.remaining(DirectByteBuffer.SS_CACHE);

		try{
			file.read( buffer, position );

			manager.fileBytesRead( read_length );

			bytes_read += read_length;

		}catch( FMFileManagerException e ){

			manager.rethrow(this,e);
		}
	}

	@Override
	public void
	write(
		DirectByteBuffer	buffer,
		long				position )

		throws CacheFileManagerException
	{
		int	write_length = buffer.remaining(DirectByteBuffer.SS_CACHE);

		try{
			file.write( buffer, position );

			manager.fileBytesWritten( write_length );

			bytes_written += write_length;

		}catch( FMFileManagerException e ){

			manager.rethrow(this,e);
		}
	}

	@Override
	public void
	write(
		DirectByteBuffer[]	buffers,
		long				position )

		throws CacheFileManagerException
	{
		int	write_length	= 0;

		for (int i=0;i<buffers.length;i++){

			write_length += buffers[i].remaining(DirectByteBuffer.SS_CACHE);
		}

		try{
			file.write( buffers, position );

			manager.fileBytesWritten( write_length );

			bytes_written += write_length;

		}catch( FMFileManagerException e ){

			manager.rethrow(this,e);
		}
	}

	@Override
	public void
	writeAndHandoverBuffer(
		DirectByteBuffer	buffer,
		long				position )

		throws CacheFileManagerException
	{
		int	write_length = buffer.remaining(DirectByteBuffer.SS_CACHE);

		boolean	write_ok	= false;

		try{
			file.write( buffer, position );

			manager.fileBytesWritten( write_length );

			bytes_written += write_length;

			write_ok	= true;

		}catch( FMFileManagerException e ){

			manager.rethrow(this,e);

		}finally{

			if ( write_ok ){

				buffer.returnToPool();
			}
		}
	}

	@Override
	public void
	writeAndHandoverBuffers(
		DirectByteBuffer[]	buffers,
		long				position )

		throws CacheFileManagerException
	{
		int	write_length	= 0;

		for (int i=0;i<buffers.length;i++){

			write_length += buffers[i].remaining(DirectByteBuffer.SS_CACHE);
		}

		boolean	write_ok	= false;

		try{
			file.write( buffers, position );

			manager.fileBytesWritten( write_length );

			bytes_written += write_length;

			write_ok	= true;

		}catch( FMFileManagerException e ){

			manager.rethrow(this,e);

		}finally{

			if ( write_ok ){

				for (int i=0;i<buffers.length;i++){

					buffers[i].returnToPool();
				}
			}
		}
	}

	@Override
	public void
	flushCache()

		throws CacheFileManagerException
	{
		try{
			file.flush();

		}catch( FMFileManagerException e ){

			manager.rethrow(this,e);
		}
	}

	public void
	flushCache(
		long		offset,
		int			length )

		throws CacheFileManagerException
	{
		flushCache();
	}
	
	@Override
	public void
	clearCache()

		throws CacheFileManagerException
	{
	}

	@Override
	public void
	close()

		throws CacheFileManagerException
	{
		try{

			file.close();

		}catch( FMFileManagerException e ){

			manager.rethrow(this,e);
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
