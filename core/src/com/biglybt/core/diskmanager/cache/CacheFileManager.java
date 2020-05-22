/*
 * Created on 03-Aug-2004
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

package com.biglybt.core.diskmanager.cache;

import java.io.File;

import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.LinkFileMap;


/**
 * @author parg
 *
 */

public interface
CacheFileManager
{
	public CacheFile
	createFile(
		CacheFileOwner	owner,
		File			file,
		int				type,
		boolean			force )

		throws CacheFileManagerException;

	public CacheFileManagerStats
	getStats();

	public void
	setFileLinks(
		TOTorrent				torrent,
		LinkFileMap				links );
}
