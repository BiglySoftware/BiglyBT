/*
 * File    : TRHostTorrent.java
 * Created : 26-Oct-2003
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

package com.biglybt.core.tracker.host;

/**
 * @author parg
 *
 */

import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.tracker.server.TRTrackerServerTorrent;

public interface
TRHostTorrent
{
	public static final int	TS_FAILED		= 0;
	public static final int	TS_STOPPED		= 1;
	public static final int	TS_STARTED		= 2;
	public static final int	TS_PUBLISHED	= 3;

	public void
	start();

	public void
	stop();

	public void
	remove()

		throws TRHostTorrentRemovalVetoException;

	/**
	 * doesn't guarantee that removal will be successful as conditions may change
	 * @return true if OK, exception thrown otherwise
	 * @throws TRHostTorrentRemovalVetoException
	 */

	public boolean
	canBeRemoved()

		throws TRHostTorrentRemovalVetoException;

	public int
	getStatus();

	public boolean
	isPersistent();

	public boolean
	isPassive();

	public void
	setPassive(
		boolean		passive );

	public boolean
	isExternal();

	public long
	getDateAdded();

	public TOTorrent
	getTorrent();

	public void
	setTorrent(
		TOTorrent	torrent );

	public TRTrackerServerTorrent
	getTrackerTorrent();

	public int
	getPort();

	public TRHostPeer[]
	getPeers();

	public int
	getSeedCount();

	public int
	getLeecherCount();

	public int
	getBadNATCount();

	public long
	getAnnounceCount();

	public long
	getAverageAnnounceCount();

	public long
	getScrapeCount();

	public long
	getAverageScrapeCount();

	public long
	getCompletedCount();

	public long
	getTotalUploaded();

	public long
	getTotalDownloaded();

	public long
	getTotalLeft();

	public long
	getAverageUploaded();

	public long
	getAverageDownloaded();

	public long
	getTotalBytesIn();

	public long
	getAverageBytesIn();

	public long
	getTotalBytesOut();

	public long
	getAverageBytesOut();

	public void
	disableReplyCaching();

	public void
	addListener(
		TRHostTorrentListener	l );

	public void
	removeListener(
		TRHostTorrentListener	l );

	public void
	addRemovalListener(
		TRHostTorrentWillBeRemovedListener	l );

	public void
	removeRemovalListener(
		TRHostTorrentWillBeRemovedListener	l );

  /** To retreive arbitrary objects against this object. */
  public Object getData (String key);
  /** To store arbitrary objects against this object. */
  public void setData (String key, Object value);
}
