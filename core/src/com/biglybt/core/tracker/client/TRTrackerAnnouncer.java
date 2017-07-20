/*
 * File    : TRTrackerClient.java
 * Created : 5 Oct. 2003
 * By      : Parg
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

package com.biglybt.core.tracker.client;

import java.net.URL;
import java.util.Map;

import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentAnnounceURLSet;
import com.biglybt.core.tracker.TrackerPeerSource;
import com.biglybt.core.util.IndentWriter;
import com.biglybt.pif.download.DownloadAnnounceResult;

public interface
TRTrackerAnnouncer
{



	public final static byte AZ_TRACKER_VERSION_1	= 1;	// anything before 2 ;)
	public final static byte AZ_TRACKER_VERSION_2	= 2;	// supports azcompact
	public final static byte AZ_TRACKER_VERSION_3	= 3;	// supports experimental alternative secret for crypto

	public final static byte AZ_TRACKER_VERSION_CURRENT	= AZ_TRACKER_VERSION_3;

	public static final int REFRESH_MINIMUM_SECS		= 60;
	public static final int DEFAULT_PEERS_TO_CACHE		= 512;

	public static final int TS_INITIALISED		= 1;
	public static final int TS_DOWNLOADING		= 2;
	public static final int TS_COMPLETED		= 3;
	public static final int TS_STOPPED			= 4;


	public void
	setAnnounceDataProvider(
		TRTrackerAnnouncerDataProvider		provider );

	public TOTorrent
	getTorrent();

	public URL
	getTrackerURL();

	public void
	setTrackerURL(
		URL		url );

	public void
	resetTrackerUrl(
		boolean	shuffle );

	public void
	setIPOverride(
		String		override );

	public void
	clearIPOverride();

	public byte[]
	getPeerId();

	public void
	setRefreshDelayOverrides(
		int		percentage );

	public int
	getTimeUntilNextUpdate();

	/**
	 * Last Update Time in seconds
	 */
	public int
	getLastUpdateTime();

	public void
	update(
		boolean	force );

	public void
	complete(
		boolean	already_reported );

	public void
	stop(
		boolean	for_queue );

	public void
	destroy();

	public int
	getStatus();

	public boolean
	isManual();

	public String
	getStatusString();

	public TRTrackerAnnouncer
	getBestAnnouncer();

	public TRTrackerAnnouncerResponse
	getLastResponse();

		/**
		 * returns a Map containing "bencoded" entries representing a cache of tracker
		 * responses.
		 * @return
		 */

	public Map
	getTrackerResponseCache();

		/**
		 * sets the response cache. This may be used by the tracker client to return peer
		 * details when the tracker is offline
		 * @param map
		 */

	public void
	setTrackerResponseCache(
		Map		map );

		/**
		 * remove a specific entry from the cache if present
		 * @param ip
		 * @param tcp_port
		 */

	public void
	removeFromTrackerResponseCache(
		String		ip,
		int			tcp_port );

		/**
		 * Gets a delegate tracker peer source for reporting against
		 * @param set
		 * @return
		 */

	public TrackerPeerSource
	getTrackerPeerSource(
		TOTorrentAnnounceURLSet		set );

	public TrackerPeerSource
	getCacheTrackerPeerSource();

	/**
	 * This method forces all listeners to get an explicit "urlChanged" event to get them
	 * to re-examine the tracker
	 */

	public void
	refreshListeners();

	public void
	setAnnounceResult(
		DownloadAnnounceResult	result );

	public void
	addListener(
		TRTrackerAnnouncerListener	l );

	public void
	removeListener(
		TRTrackerAnnouncerListener	l );

	public void
	generateEvidence(
		IndentWriter writer );
}
