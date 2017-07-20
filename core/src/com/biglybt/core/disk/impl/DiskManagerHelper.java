/*
 * Created on 31-Jul-2004
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

package com.biglybt.core.disk.impl;

/**
 * @author parg
 *
 */

import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.disk.impl.piecemapper.DMPieceList;
import com.biglybt.core.diskmanager.access.DiskAccessController;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentException;

public interface
DiskManagerHelper
	extends DiskManager
{
	public DiskAccessController
	getDiskAccessController();

	@Override
	public DMPieceList
	getPieceList(
		int	piece_number );

	public byte[]
	getPieceHash(
		int	piece_number )

		throws TOTorrentException;

	/**
	 * Stops the disk manager and informs the download manager that things have gone
	 * wrong.
	 * @param reason
	 */

	public void
	setFailed(
		String		reason );

	public void
	setFailed(
		DiskManagerFileInfo	file,
		String				reason );

	public long
	getAllocated();

	public void
	setAllocated(
		long		num );

	public void
	setPercentDone(
		int			num );

	public void
	setPieceDone(
		DiskManagerPieceImpl	piece,
		boolean					done );

	@Override
	public TOTorrent
	getTorrent();

	public String[]
	getStorageTypes();

	public String getStorageType(int fileIndex);

	public void
	accessModeChanged(
		DiskManagerFileInfoImpl		file,
		int							old_mode,
		int							new_mode );

	public void
    skippedFileSetChanged(
	    	DiskManagerFileInfo	file );

	public void
	priorityChanged(
		DiskManagerFileInfo	file );

	public String
	getInternalName();

	public DownloadManagerState
	getDownloadState();

	public DiskManagerRecheckScheduler
	getRecheckScheduler();

}
