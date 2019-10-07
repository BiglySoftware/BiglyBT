/*
 * Created on Jul 15, 2007
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


package com.biglybt.core.disk.impl.piecemapper.impl;

import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.disk.impl.piecemapper.DMPieceList;
import com.biglybt.core.disk.impl.piecemapper.DMPieceMap;
import com.biglybt.core.disk.impl.piecemapper.DMPieceMapEntry;
import com.biglybt.core.torrent.TOTorrent;

public class
DMPieceMapSimple
	implements DMPieceMap
{
	final int		piece_length;
	final int		piece_count;
	final int		last_piece_length;

	final DiskManagerFileInfo	file;

	protected
	DMPieceMapSimple(
		TOTorrent				torrent,
		DiskManagerFileInfo		_file )
	{
		piece_length	= (int)torrent.getPieceLength();

		piece_count		= torrent.getNumberOfPieces();

		int lpl = (int)( torrent.getSize() % piece_length );

		if ( lpl == 0 ){

			lpl = piece_length;
		}

		last_piece_length = lpl;

		file	= _file;
	}

	@Override
	public DMPieceList
	getPieceList(
		int	piece_number )
	{
		return( new pieceList( piece_number ));
	}

	protected class
	pieceList
		implements DMPieceList, DMPieceMapEntry
	{
		private final int	piece_number;

		protected
		pieceList(
			int	_piece_number )
		{
			piece_number	= _piece_number;
		}

		@Override
		public int
		size()
		{
			return( 1 );
		}

		@Override
		public DMPieceMapEntry
		get(
			int index )
		{
			return( this );
		}

		@Override
		public int
		getCumulativeLengthToPiece(
			int file_index )
		{
			return( getLength());
		}

			// map entry

		@Override
		public DiskManagerFileInfo
		getFile()
		{
			return( file );
		}

		@Override
		public long
		getOffset()
		{
			return(((long)piece_number) * piece_length );
		}

		@Override
		public int
		getLength()
		{
			if ( piece_number == piece_count - 1 ){

				return( last_piece_length );
			}else{

				return( piece_length );
			}
		}
	}
}
