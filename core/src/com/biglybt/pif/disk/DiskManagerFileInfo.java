/*
 * Created : 2004/May/26
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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

package com.biglybt.pif.disk;

import java.io.File;

import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadException;

/**
 * @author TuxPaper
 *
 * @since 2.1.0.0
 */

public interface
DiskManagerFileInfo
{
	public static final int READ = 1;
	public static final int WRITE = 2;

	public static final int PRIORITY_LOW = -1;
	public static final int PRIORITY_NORMAL = 0;
	public static final int PRIORITY_HIGH = 1;

		// set methods

	public void
	setPriority(
		boolean b );

  /**
   * Sets the file's download priority base on a number
   *
   * @param priority Any number or {@link #PRIORITY_LOW}, {@link #PRIORITY_NORMAL}, {@link #PRIORITY_HIGH}
   *
   * @since 4407
   */
	public void
	setNumericPriority(
		int		priority );

	public void
	setSkipped(
		boolean b );

	public Boolean
	isSkipping();
	
		/**
		 * Mark the file as deleted or not (deleted means the file will be truncated to take up minimum
		 * space). This is generally 0 <= X < 2*piece_length as pieces can span file boundaries.
		 * @since 2403
		 * @param b
		 */

	public void
	setDeleted(boolean b);

		// links the file to the named destination

	/**
	 * @deprecated remove when xmwebui plugin no longer uses it (post 3101)
	 * @param link_destination
	 */
	
	public default void
	setLink(
		File		link_destination )
	{
		setLink( link_destination, false );
	}
	
	public void
	setLink(
		File		link_destination,
		boolean		no_delete );

		// gets the current link, null if none

	public File
	getLink();

	 	// get methods

	public int
	getAccessMode();

	public long
	getDownloaded();

	public long
	getLastModified();
	
	/**
	 * Size when file is complete
	 */
	public long
	getLength();

	public File
	getFile();

		/**
		 * returns liked file if it exists, direct otherwise
		 * @param follow_link
		 * @return
		 * @since 4.3.1.5
		 */

	public File
	getFile(
		boolean	follow_link );

	public int
	getIndex();

	public int
	getFirstPieceNumber();

	public long
	getPieceSize();

	public int
	getNumPieces();

	public boolean
	isPriority();

	/**
	 *
	 * @return
	 *
	 * @since 4.8.1.3
	 */
	public int
	getNumericPriority();

	public boolean
	isSkipped();

	public boolean
	isDeleted();

	public byte[]
	getDownloadHash()

         throws DownloadException;

	public Download
	getDownload()

         throws DownloadException;

	public DiskManagerChannel
	createChannel()

		throws DownloadException;

		/**
		 * Creates a random read request - these will be executed against the download
		 * sequentially
		 * @param file_offset
		 * @param length
		 * @param reverse_order	- deliver blocks to the listener in reverse order
		 * @param listener
		 * @return
		 * @throws DownloadException
		 */

	public DiskManagerRandomReadRequest
	createRandomReadRequest(
		long						file_offset,
		long						length,
		boolean						reverse_order,
		DiskManagerListener			listener )

		throws DownloadException;
}
