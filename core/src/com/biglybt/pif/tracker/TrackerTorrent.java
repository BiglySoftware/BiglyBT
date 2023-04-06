/*
 * File    : TrackerTorrent.java
 * Created : 08-Dec-2003
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

package com.biglybt.pif.tracker;

/**
 * @author parg
 *
 */

import com.biglybt.pif.torrent.Torrent;

public interface
TrackerTorrent
{
	public static final int	TS_STARTED		= 0;
	public static final int	TS_STOPPED		= 1;
	public static final int	TS_PUBLISHED	= 2;

	public void
	start()

		throws TrackerException;

	public void
	stop()

		throws TrackerException;

	public void
	remove()

		throws TrackerTorrentRemovalVetoException;

	public boolean
	canBeRemoved()

		throws TrackerTorrentRemovalVetoException;

	public Torrent
	getTorrent();

	public TrackerPeer[]
	getPeers();

	public int
	getStatus();

	public int
	getSeedCount();

	public int
	getLeecherCount();

		/**
		 * Gives access to the number of peers that have failed NAT checks, 0 if NAT checking
		 * is disabled.
		 * @return
		 */

	public int
	getBadNATCount();

	public long
	getTotalUploaded();

	public long
	getTotalDownloaded();

	public long
	getAverageUploaded();

	public long
	getAverageDownloaded();

	public long
	getTotalLeft();

	public long
	getCompletedCount();

		/**
		 * total bytes received by the tracker for this torrent - announce and scrape requests
		 * @return
		 */

	public long
	getTotalBytesIn();

		/**
		 * average rate of bytes in
		 * @return
		 */

	public long
	getAverageBytesIn();

		/**
		 * total bytes sent as response by the tracker - announce and scrape responses
		 * @return
		 */

	public long
	getTotalBytesOut();

		/**
		 * average bytes sent
		 * @return
		 */

	public long
	getAverageBytesOut();

	public long
	getScrapeCount();

	public long
	getAverageScrapeCount();

	public long
	getAnnounceCount();

	public long
	getAverageAnnounceCount();

		/**
		 * stops the tracker from caching replies to announces/scrapes for this torrent
		 */

	public void
	disableReplyCaching();

	public boolean
	isPassive();

	public boolean
	isExternal();
	
		/**
		 * @since 2.3.0.5
		 * @return
		 */

	public long
	getDateAdded();

	public void
	addListener(
		TrackerTorrentListener	listener );

	public void
	removeListener(
		TrackerTorrentListener	listener );

	public void
	addRemovalListener(
		TrackerTorrentWillBeRemovedListener	listener );

	public void
	removeRemovalListener(
		TrackerTorrentWillBeRemovedListener	listener );
}
