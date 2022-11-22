/*
 * File    : DownloadManagerFactory.java
 * Created : 19-Oct-2003
 * By      : stuff
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

package com.biglybt.core.download;

/**
 * @author parg
 *
 */

import java.util.List;

import com.biglybt.core.download.impl.DownloadManagerAvailabilityImpl;
import com.biglybt.core.download.impl.DownloadManagerImpl;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.torrent.TOTorrent;

public class
DownloadManagerFactory
{
	public static DownloadManager
	create(
		GlobalManager 	gm,
		byte[]			torrent_hash,
		String 			torrentFileName,
		String 			torrent_save_dir,
		String			torrent_save_file,
		int      		initialState,
		boolean			persistent,
		boolean			recovered,
		boolean			for_seeding,
		boolean			has_ever_been_started,
		List			file_priorities )
	{
		return( new DownloadManagerImpl( gm, torrent_hash, torrentFileName, torrent_save_dir, torrent_save_file, initialState, persistent, recovered, for_seeding, has_ever_been_started, file_priorities ));
	}

	public static DownloadManagerAvailability
	getAvailability(
		TOTorrent				torrent,
		List<List<String>>		updated_trackers,
		String[]				enabled_peer_sources,
		String[]				enabled_networks )
	{
		return( new DownloadManagerAvailabilityImpl( torrent, updated_trackers, enabled_peer_sources, enabled_networks ));
	}

	public static void
	addGlobalDownloadListener(
		DownloadManagerListener listener )
	{
		DownloadManagerImpl.addGlobalDownloadListener( listener );
	}

    public static void
    removeGlobalDownloadListener(
        DownloadManagerListener listener )
    {
		DownloadManagerImpl.removeGlobalDownloadListener( listener );
    }
}
