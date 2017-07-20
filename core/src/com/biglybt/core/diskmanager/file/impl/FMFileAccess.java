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

import java.io.RandomAccessFile;

import com.biglybt.core.diskmanager.file.FMFileManagerException;
import com.biglybt.core.util.DirectByteBuffer;

public interface
FMFileAccess
{
	public void
	aboutToOpen()

		throws FMFileManagerException;

	public long
	getLength(
		RandomAccessFile		raf )

		throws FMFileManagerException;

	public void
	setLength(
		RandomAccessFile		raf,
		long					length )

		throws FMFileManagerException;

	public void
	read(
		RandomAccessFile	raf,
		DirectByteBuffer[]	buffers,
		long				offset )

		throws FMFileManagerException;

	public void
	write(
		RandomAccessFile		raf,
		DirectByteBuffer[]		buffers,
		long					position )

		throws FMFileManagerException;

	public void
	flush()

		throws FMFileManagerException;

	public boolean
	isPieceCompleteProcessingNeeded(
		int					piece_number );

	public void
	setPieceComplete(
		RandomAccessFile	raf,
		int					piece_number,
		DirectByteBuffer	piece_data )

		throws FMFileManagerException;

	public FMFileImpl
	getFile();

	public String
	getString();
}
