/*
 * Created on 12-Jul-2004
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

package com.biglybt.core.global.impl;

import java.io.File;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.tracker.host.TRHost;
import com.biglybt.core.tracker.host.TRHostFactory;
import com.biglybt.core.tracker.host.TRHostTorrent;
import com.biglybt.core.tracker.host.TRHostTorrentFinder;
import com.biglybt.core.util.*;
import com.biglybt.pifimpl.local.download.DownloadManagerImpl;


class
GlobalManagerHostSupport
	implements 	TRHostTorrentFinder
{
	protected final GlobalManager	gm;
	protected final TRHost		host;

	protected
	GlobalManagerHostSupport(
		GlobalManager	_gm )
	{
		gm		= _gm;

	    host = TRHostFactory.getSingleton();

		host.initialise( this );
	}

	@Override
	public TOTorrent
	lookupTorrent(
		byte[]		hash )
	{
		DownloadManager dm = gm.getDownloadManager( new HashWrapper( hash ));

		if ( dm != null ){

			TOTorrent torrent = dm.getTorrent();

			if ( torrent != null ){

				return( torrent );
			}
		}

		TOTorrent torrent = DownloadManagerImpl.getStubTorrent( hash );

		return( torrent );
	}

	protected void
	torrentRemoved(
		DownloadManager		download_manager,
		TOTorrent			torrent )
	{
		boolean is_magnet = download_manager.getDownloadState().getFlag( DownloadManagerState.FLAG_METADATA_DOWNLOAD );

		if ( is_magnet ){
			
			return;
		}
		
		String torrent_file_str = download_manager.getTorrentFileName();
		
		TRHostTorrent	host_torrent = host.getHostTorrent( torrent );

		if ( host_torrent != null ){

				// it we remove a torrent while it is hosted then we flip it into passive mode to
				// keep it around in a sensible state

				// we've got to ensure that the torrent's file location is available in the torrent itself
				// as we're moving from download-managed persistence to host managed :(

				// check file already exists - might have already been deleted as in the
				// case of shared resources

			File	torrent_file = FileUtil.newFile( torrent_file_str );

			if ( torrent_file.exists()){

				try{
					TorrentUtils.writeToFile( host_torrent.getTorrent(), torrent_file, false );

					host_torrent.setPassive( true );

				}catch( Throwable e ){

					Debug.out( "Failed to make torrent '" + torrent_file_str + "' passive: " + Debug.getNestedExceptionMessage(e));
				}
			}
		}
	}

	protected void
	torrentAdded(
		DownloadManager		download_manager )
	{
		boolean is_magnet = download_manager.getDownloadState().getFlag( DownloadManagerState.FLAG_METADATA_DOWNLOAD );

		if ( is_magnet ){
			
			return;
		}
		
		TOTorrent torrent = download_manager.getTorrent();
		
		TRHostTorrent	host_torrent = host.getHostTorrent( torrent );

		if ( host_torrent != null ){

			if ( host_torrent.getTorrent() != torrent ){

				host_torrent.setTorrent( torrent );
			}
		}
	}

	protected void
	destroy()
	{
		host.close();
	}
}
