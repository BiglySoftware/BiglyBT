/*
 * File    : DownloadManagerListener.java
 * Created : 29-Nov-2003
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

package com.biglybt.core.download;

import com.biglybt.core.disk.DiskManagerFileInfo;

/**
 * @author parg
 *
 * [Paper 2004/01/12]
 * Added DownloadManager parameter to calls.  This allows for one listener object
 * to listen to many DownloadManagers
 */
public interface
DownloadManagerListener
{
	public void
	stateChanged(
		DownloadManager manager,
		int		state );

  /** Notification that we were downloading and the download has completed
   */
	public void
	downloadComplete(DownloadManager manager);

  /** Notification that the completion state has changed.
   *  Tells you when we switched from Completed to Incompleted (or visa versa)
   *  Does not get called when diskManager goes into CHECKING, but does
   *  when it goes out of CHECKING, to avoid torrents jumping momentarily from
   *  Completed to Incompleted to Completed again.
   */
  public void
  completionChanged(DownloadManager manager, boolean bCompleted);

  public void
  positionChanged(DownloadManager download, int oldPosition, int newPosition);

  public void
  filePriorityChanged( DownloadManager download, DiskManagerFileInfo file );
  
  /**
   * 
   * @param download
   * @param file	null - overall location change (e.g. move download)
   */
  
  default public void
  fileLocationChanged( DownloadManager download, DiskManagerFileInfo file )
  {
  }
}
