/*
 * File    : FMFile.java
 * Created : 12-Feb-2004
 * By      : parg
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.core.diskmanager.file;

/**
 * @author parg
 *
 */

import java.io.File;

import com.biglybt.core.util.DirectByteBuffer;
import com.biglybt.core.util.FileUtil;


public interface
FMFile
{
	public static final int	FT_LINEAR					= 1;
	public static final int	FT_COMPACT					= 2;
	public static final int	FT_PIECE_REORDER			= 3;
	public static final int	FT_PIECE_REORDER_COMPACT	= 4;

	public static final int	FM_READ		= 1;
	public static final int FM_WRITE	= 2;

	public String
	getName();

	public boolean
	exists();

	public FMFileOwner
	getOwner();

	public void
	moveFile(
		File						new_file,
		FileUtil.ProgressListener	pl )

		throws FMFileManagerException;

	public void
	renameFile(
		String		new_name )

		throws FMFileManagerException;

	public void
	setAccessMode(
		int		mode )

		throws FMFileManagerException;

	public int
	getAccessMode();

	public void
	setStorageType(
		int		type,
		boolean	force )

		throws FMFileManagerException;

	public int
	getStorageType();

	public void
	ensureOpen(
		String	reason )

		throws FMFileManagerException;

	public long
	getLength()

		throws FMFileManagerException;

	public void
	setLength(
		long		length )

		throws FMFileManagerException;

	public void
	setPieceComplete(
		int					piece_number,
		DirectByteBuffer	piece_data )

		throws FMFileManagerException;

	public void
	read(
		DirectByteBuffer	buffer,
		long				offset )

		throws FMFileManagerException;

	public void
	read(
		DirectByteBuffer[]	buffers,
		long				offset )

		throws FMFileManagerException;

	public void
	write(
		DirectByteBuffer	buffer,
		long				position )

		throws FMFileManagerException;

	public void
	write(
		DirectByteBuffer[]	buffers,
		long				position )

		throws FMFileManagerException;

	public void
	flush()

		throws FMFileManagerException;

	public void
	close()

		throws FMFileManagerException;

	public boolean
	isOpen();

	public void
	delete()

		throws FMFileManagerException;

	public FMFile
	createClone()

		throws FMFileManagerException;

	public boolean
	isClone();
	
	public long
	getLastModified();
}
