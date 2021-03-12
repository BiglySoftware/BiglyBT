/*
 * File    : GlobalManagerListener.java
 * Created : 21-Oct-2003
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

package com.biglybt.core.global;

/**
 * @author parg
 *
 */

import com.biglybt.core.download.DownloadManager;

public interface
GlobalManagerListener
{
	public void
	downloadManagerAdded(
		DownloadManager	dm );

	public void
	downloadManagerRemoved(
		DownloadManager	dm );

	public default void
	destroyInitiated()
	{
	}
	
	public default void
	destroyInitiated(
		GlobalMangerProgressListener	l )
	{
		destroyInitiated();
	}

	public void
	destroyed();


    /**
     * Notification of global seeding status changes.
     * @param seeding_only_mode true if only seeding torrents (no downloads), false otherwise
     * @param potentially_seeding_only_mode - as above but true if queued seeds
     */

    public void
    seedingStatusChanged(
    	boolean seeding_only_mode,
    	boolean	potentially_seeding_only_mode );
}
