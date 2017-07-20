/*
 * File    : DownloadManagerAdapter.java
 * Created : 2004/May/21
 * By      : tuxpaper
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

package com.biglybt.core.download.impl;

import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerListener;

/** Adapter for DownloadManagerListener
 */
public class DownloadManagerAdapter
       implements DownloadManagerListener
{
	@Override
	public void stateChanged(DownloadManager manager, int state) {}
	@Override
	public void downloadComplete(DownloadManager manager) {}
    @Override
    public void completionChanged(DownloadManager manager, boolean bCompleted) {}
	@Override
	public void	positionChanged(DownloadManager download, int oldPosition, int newPosition) {}
	@Override
	public void filePriorityChanged(DownloadManager download, DiskManagerFileInfo file ){}
}
