/*
 * Created on Sep 1, 2003
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
 */

package com.biglybt.core.disk.impl.piecemapper.impl;

import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.disk.impl.piecemapper.DMPieceMapEntry;


public class
PieceMapEntryImpl
	implements DMPieceMapEntry
{
		/**
		 * This class denotes the mapping of a piece onto a file. Typically a piece can
		 * span multiple files. Each overlapping segment has on of these entries created
		 * for it.
		 * It identifies the file, the offset within the file, and the length of the chunk
		 */

	private final DiskManagerFileInfo	 _file;
	private final long 					_offset;
	private final int					_length;

	public
	PieceMapEntryImpl(
			DiskManagerFileInfo 	file,
		long 						offset,
		int 						length )
	{
		_file = file;
		_offset = offset;
		_length = length;
	}

	@Override
	public DiskManagerFileInfo getFile() {
		return _file;
	}
	@Override
	public long getOffset() {
		return _offset;
	}
	@Override
	public int getLength() {
		return _length;
	}

}