/*
 * File    : DiskManagerFactory.java
 * Created : 18-Oct-2003
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

package com.biglybt.core.disk;

import java.util.Map;

import com.biglybt.core.Core;

/**
 * @author parg
 *
 */


import com.biglybt.core.disk.impl.DiskManagerImpl;
import com.biglybt.core.disk.impl.DiskManagerOperationScheduler;
import com.biglybt.core.disk.impl.DiskManagerUtil;
import com.biglybt.core.disk.impl.resume.RDResumeHandler;
import com.biglybt.core.diskmanager.access.DiskAccessController;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.LinkFileMap;

public class
DiskManagerFactory
{
	public static void
	initialise(
		Core		core )
	{
		DiskManagerOperationScheduler.initialise( core );
	}
	
	public static DiskAccessController
	getDiskAccessController()
	{
		return( DiskManagerImpl.getDefaultDiskAccessController());
	}
	    
	public static DiskManager
	create(
		TOTorrent		torrent,
		DownloadManager manager)
	{
		DiskManagerImpl dm = new DiskManagerImpl( torrent, manager );

		if ( dm.getState() != DiskManager.FAULTY ){

			dm.start();
		}

		return dm;
	}

	/*
	public static DiskManager
	createNoStart(
		TOTorrent		torrent,
		DownloadManager manager)
	{
		return( new DiskManagerImpl( torrent, manager ));
	}
	*/

		/**
		 * Method to preset resume data to indicate completely valid file.
		 * Doesn't save the torrent
		 * @param torrent
		 */

	public static void
	setResumeDataCompletelyValid(
		DownloadManagerState	download_manager_state )
	{
		RDResumeHandler.setTorrentResumeDataComplete( download_manager_state );
	}

	public static void
	setResumeDataTotallyIncomplete(
		DownloadManagerState	download_manager_state )
	{
		RDResumeHandler.setTorrentResumeTotallyIncomplete( download_manager_state );
	}
	
		/**
		 * Sets resume data complete apart from a few random pieces. On torrent start these will be checked
		 * This is used in the "open for seeding" mode to ensure that there's at least a chance that the
		 * data they claim represents the data for the torrent is valid
		 * @param torrent
		 * @param torrent_save_dir
		 * @param torrent_save_file
		 */

	public static void
	setTorrentResumeDataNearlyComplete(
		DownloadManagerState	dms )
	{
		RDResumeHandler.setTorrentResumeDataNearlyComplete( dms );
	}

	public static boolean
	isTorrentResumeDataComplete(
		DownloadManagerState	dms )
	{
		return RDResumeHandler.isTorrentResumeDataComplete( dms );
	}
	
	public static boolean
	isTorrentResumeDataValid(
		DownloadManagerState	dms )
	{
		return RDResumeHandler.isTorrentResumeDataValid( dms );
	}

	public static void
	deleteDataFiles(
		TOTorrent 	torrent,
		String		torrent_save_dir,
		String		torrent_save_file,
		boolean		force_no_recycle )
	{
	  DiskManagerImpl.deleteDataFiles(torrent, torrent_save_dir, torrent_save_file, force_no_recycle );
	}

	public static DiskManagerFileInfoSet
   	getFileInfoSkeleton(
		DownloadManager			download_manager,
		DiskManagerListener		listener )
	{
		return( DiskManagerUtil.getFileInfoSkeleton( download_manager, listener ));
	}

	public static void
	setFileLinks(
		DownloadManager			download_manager,
		LinkFileMap				links )
	{
		DiskManagerImpl.setFileLinks( download_manager, links );
	}

	public static void
	clearResumeData(
		DownloadManager			download_manager,
		DiskManagerFileInfo		file )
	{
		RDResumeHandler.clearResumeData( download_manager, file );
	}

	public static void
	recheckFile(
		DownloadManager			download_manager,
		DiskManagerFileInfo		file )
	{
		RDResumeHandler.recheckFile( download_manager, file );
	}
}
