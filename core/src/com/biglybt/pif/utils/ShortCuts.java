/*
 * Created on 10-May-2004
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

package com.biglybt.pif.utils;

/**
 * @author parg
 *
 */

import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadException;
import com.biglybt.pif.download.DownloadRemovalVetoException;
import com.biglybt.pif.download.DownloadStats;

public interface
ShortCuts
{
		/**
		 * A quick way of looking up a download given its hash
		 * @param hash
		 * @return
		 * @throws DownloadException
		 */

	public Download
	getDownload(
		byte[]		hash )

		throws DownloadException;

		/**
		 * A quick way of getting a download's statistics given its hash
		 * @param hash
		 * @return
		 * @throws DownloadException
		 */

	public DownloadStats
	getDownloadStats(
		byte[]		hash )

		throws DownloadException;

		/**
		 * A quick way of restarting a download given its hash
		 * @param hash
		 * @throws DownloadException
		 */

	public void
	restartDownload(
		byte[]		hash )

		throws DownloadException;

		/**
		 * A quick way of stopping a download given its hash
		 * @param hash
		 * @throws DownloadException
		 */

	public void
	stopDownload(
		byte[]		hash )

		throws DownloadException;

		/**
		 * A quick way of deleting a download given its hash
		 * @param hash
		 * @throws DownloadException
		 * @throws DownloadRemovalVetoException
		 */

	public void
	removeDownload(
		byte[]		hash )

		throws DownloadException, DownloadRemovalVetoException;
}
