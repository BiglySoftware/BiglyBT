/*
 * Created on 13-Oct-2004
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

import com.biglybt.core.diskmanager.file.FMFileManagerException;
import com.biglybt.core.diskmanager.file.FMFileOwner;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentFile;
import com.biglybt.core.util.AEDiagnostics;
import com.biglybt.core.util.DirectByteBuffer;

/**
 * @author parg
 *
 */

public class
FMFileTestImpl
	extends FMFileUnlimited
{
	protected long	file_offset_in_torrent;

	protected
	FMFileTestImpl(
		FMFileOwner			_owner,
		FMFileManagerImpl	_manager,
		File				_file,
		int					_type,
		boolean				_force )

		throws FMFileManagerException
	{
		super( _owner, _manager, _file, _type, _force );

		TOTorrentFile	torrent_file = getOwner().getTorrentFile();

		TOTorrent	torrent = torrent_file.getTorrent();

		for (int i=0;i<torrent.getFiles().length;i++){

			TOTorrentFile	f = torrent.getFiles()[i];

			if ( f == torrent_file ){

				break;
			}

			file_offset_in_torrent	+= f.getLength();
		}
	}

	@Override
	protected void
	readSupport(
		DirectByteBuffer	buffer,
		long				offset )

		throws FMFileManagerException
	{
		if ( AEDiagnostics.CHECK_DUMMY_FILE_DATA ){

			offset	+= file_offset_in_torrent;

			while( buffer.hasRemaining( DirectByteBuffer.SS_FILE )){

				buffer.put( DirectByteBuffer.SS_FILE, (byte)offset++ );
			}
		}else{

			buffer.position( DirectByteBuffer.SS_FILE, buffer.limit( DirectByteBuffer.SS_FILE ));
		}
	}

	@Override
	protected void
	writeSupport(
		DirectByteBuffer[]		buffers,
		long					offset )

		throws FMFileManagerException
	{
		offset	+= file_offset_in_torrent;

		for (int i=0;i<buffers.length;i++){

			DirectByteBuffer	buffer = buffers[i];

			if ( AEDiagnostics.CHECK_DUMMY_FILE_DATA ){

				while( buffer.hasRemaining( DirectByteBuffer.SS_FILE )){

					byte	v = buffer.get( DirectByteBuffer.SS_FILE );

					if ((byte)offset != v ){

						System.out.println( "FMFileTest: write is bad at " + offset +
											": expected = " + (byte)offset + ", actual = " + v );

						offset += buffer.remaining( DirectByteBuffer.SS_FILE ) + 1;

						break;
					}

					offset++;
				}
			}

			buffer.position( DirectByteBuffer.SS_FILE, buffer.limit( DirectByteBuffer.SS_FILE ));
		}
	}
}
