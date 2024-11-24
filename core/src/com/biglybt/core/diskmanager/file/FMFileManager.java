/*
 * File    : FMFileManager.java
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


import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.IndentWriter;
import com.biglybt.core.util.StringInterner;

public interface
FMFileManager
{
	public FMFile
	createFile(
		FMFileOwner				owner,
		StringInterner.FileKey	file,
		int						type,
		boolean					force )

		throws FMFileManagerException;
	
	public void
	generateEvidence(
		IndentWriter		writer,
		TOTorrent			torrent );
}
