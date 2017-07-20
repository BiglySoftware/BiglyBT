/*
 * Created on 15-Nov-2004
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

package com.biglybt.core.download;

import java.io.File;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.download.impl.DownloadManagerStateImpl;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentException;

/**
 * @author parg
 *
 */

public class
DownloadManagerStateFactory
{
		// current implementation of file links is too memory inefficient for a large number of files, disable these features
		// for huge torrents until this can be fixed :(

	public static int MAX_FILES_FOR_INCOMPLETE_AND_DND_LINKAGE;

	static{

		COConfigurationManager.addAndFireParameterListener(
			"Max File Links Supported",
			new ParameterListener() {

				@Override
				public void parameterChanged(String name ){

					MAX_FILES_FOR_INCOMPLETE_AND_DND_LINKAGE = COConfigurationManager.getIntParameter(name );
				}
			});
	}

	public static DownloadManagerState
	getDownloadState(
		TOTorrent		torrent )

		throws TOTorrentException
	{
		return( DownloadManagerStateImpl.getDownloadState( torrent ));
	}


	public static void
	loadGlobalStateCache()
	{
		DownloadManagerStateImpl.loadGlobalStateCache();
	}

	public static void
	saveGlobalStateCache()
	{
		DownloadManagerStateImpl.saveGlobalStateCache();
	}

	public static void
	discardGlobalStateCache()
	{
		DownloadManagerStateImpl.discardGlobalStateCache();
	}

	public static void
	importDownloadState(
		File		source_dir,
		byte[]		download_hash )

		throws DownloadManagerException
	{
		DownloadManagerStateImpl.importDownloadState( source_dir, download_hash );
	}

	public static void
	deleteDownloadState(
		byte[]		download_hash )

		throws DownloadManagerException
	{
		DownloadManagerStateImpl.deleteDownloadState( download_hash );
	}

	public static void
	deleteDownloadState(
		File		source_dir,
		byte[]		download_hash )

		throws DownloadManagerException
	{
		DownloadManagerStateImpl.deleteDownloadState( source_dir, download_hash );
	}

	public static void
	addGlobalListener(
		DownloadManagerStateAttributeListener l, String attribute, int event_type)
	{
		DownloadManagerStateImpl.addGlobalListener( l, attribute, event_type );
	}

	public void
	removeGlobalListener(
		DownloadManagerStateAttributeListener l, String attribute, int event_type)
	{
		DownloadManagerStateImpl.removeGlobalListener( l, attribute, event_type );
	}
}
