/*
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
 *
 * Created on 3 juil. 2003
 *
 */
package com.biglybt.core.disk;

import java.io.File;
import java.io.IOException;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.torrent.TOTorrentFile;
import com.biglybt.core.util.DirectByteBuffer;
import com.biglybt.core.util.FileUtil;

/**
 * @author Olivier
 *
 */
public interface
DiskManagerFileInfo
{
	public static final int READ 	= 1;
	public static final int WRITE 	= 2;

	public static final int	ST_LINEAR			= 1;
	public static final int	ST_COMPACT			= 2;
	public static final int	ST_REORDER			= 3;
	public static final int	ST_REORDER_COMPACT	= 4;

		// set methods

	public void setPriority(int p);

	public void setSkipped(boolean b);

	/**
	 * Relink the file to the destination given - this method deals with if the file
	 * is part of a simple torrent or not (so it may set the download name to keep it
	 * in sync). If you just want a simple relink, use setLinkAtomic.
	 *
	 * @param link_destination
	 * @return true - worked, false - failed, use getLastError to possibly get some extra info
	 */
	
	public boolean
	setLink(
		File	link_destination, boolean no_delete );

	public boolean setLinkAtomic(File link_destination, boolean no_delete);

	public boolean setLinkAtomic(File link_destination, boolean no_delete, FileUtil.ProgressListener pl );

		// gets the current link, null if none

	public File
	getLink();

		/**
		 * Download must be stopped before calling this!
		 * @param type	one of
		 * {@link DiskManagerFileInfo#ST_LINEAR},
		 * {@link DiskManagerFileInfo#ST_COMPACT},
		 * {@link DiskManagerFileInfo#ST_REORDER},
		 * {@link DiskManagerFileInfo#ST_REORDER_COMPACT}
		 */
	
	public default boolean setStorageType(int type ){ return( setStorageType( type, false )); }
	
		/**
		 * @param type
		 * @param force	discards any existing file content if it exists - use with care...
		 * @return
		 */
	
	public boolean setStorageType(int type, boolean force );

	/**
	 * Returns the storage type for this file
	 * <p/>
	 * @return
	 * {@link DiskManagerFileInfo#ST_LINEAR},
	 * {@link DiskManagerFileInfo#ST_COMPACT},
	 * {@link DiskManagerFileInfo#ST_REORDER},
	 * {@link DiskManagerFileInfo#ST_REORDER_COMPACT}
	 */
	public int
	getStorageType();

	 	// get methods

	public int getAccessMode();

	public long getDownloaded();

	public long getLastModified();
	
	public String getExtension();

	public int getFirstPieceNumber();

	public int getLastPieceNumber();

	public long getLength();

	public int getNbPieces();

	/**
	 * File Download Priority
	 * @return
	 * Common:<br>
	 * <code>-1</code>: Low<br>
	 * <code>&nbsp;0</code>: Normal<br>
	 * <code>&nbsp;1</code>: High<br>
	 *   <br>
	 * Not Common:<br>
	 * < <code>-1</code>: Lower and Lower<br>
	 * > <code>&nbsp;0</code>: Higher and Higher
	 */
	public int getPriority();

	/**
	 * Skipped files don't get downloaded.
	 * <p/>
	 * The state of the existing data is dependent upon {@link #getStorageType()}.
	 * States {@link #ST_COMPACT} and {@link #ST_REORDER_COMPACT} will delete (or trim) the file.
	 * Other states will retain existing data (ie. Do Not Download).
	 */
	public boolean isSkipped();

	public Boolean isSkipping();
	
		/**
		 * Checks that the linked file exists (always returns true for pad files)
		 * @return
		 */
	
	public boolean exists();

	public int	getIndex();

	public DownloadManager	getDownloadManager();

	public DiskManager getDiskManager();

	public File getFile( boolean follow_link );

	public TOTorrentFile
	getTorrentFile();

	public DirectByteBuffer
	read(
		long	offset,
		int		length )

		throws IOException;

	public void
	flushCache()

		throws	Exception;

	public int
	getReadBytesPerSecond();

	public int
	getWriteBytesPerSecond();

	public long
	getETA();

	public void
	recheck();
	
	public void
	close();

	public String
	getLastError();
	
	public void
	addListener(
		DiskManagerFileInfoListener	listener );

	public void
	removeListener(
		DiskManagerFileInfoListener	listener );

}
