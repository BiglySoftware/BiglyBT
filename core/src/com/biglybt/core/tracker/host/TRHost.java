/*
 * File    : TRHost.java
 * Created : 24-Oct-2003
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

package com.biglybt.core.tracker.host;

/**
 * @author parg
 */

import java.net.InetAddress;

import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.tracker.server.TRTrackerServer;

public interface
TRHost
{
	public static final int DEFAULT_MIN_RETRY_DELAY 		= TRTrackerServer.DEFAULT_MIN_RETRY_DELAY;
	public static final int DEFAULT_MAX_RETRY_DELAY 		= TRTrackerServer.DEFAULT_MAX_RETRY_DELAY;
	public static final int DEFAULT_INC_BY					= TRTrackerServer.DEFAULT_INC_BY;
	public static final int DEFAULT_INC_PER			 		= TRTrackerServer.DEFAULT_INC_PER;
	public static final int DEFAULT_SCRAPE_RETRY_PERCENTAGE	= TRTrackerServer.DEFAULT_SCRAPE_RETRY_PERCENTAGE;

	public static final int	DEFAULT_SCRAPE_CACHE_PERIOD				= TRTrackerServer.DEFAULT_SCRAPE_CACHE_PERIOD;
	public static final int	DEFAULT_ANNOUNCE_CACHE_PERIOD			= TRTrackerServer.DEFAULT_ANNOUNCE_CACHE_PERIOD;
	public static final int	DEFAULT_ANNOUNCE_CACHE_PEER_THRESHOLD	= TRTrackerServer.DEFAULT_ANNOUNCE_CACHE_PEER_THRESHOLD;

	public static final int DEFAULT_PORT 					= TRTrackerServer.DEFAULT_TRACKER_PORT;
	public static final int DEFAULT_PORT_SSL				= TRTrackerServer.DEFAULT_TRACKER_PORT_SSL;

	public void
	initialise(
		TRHostTorrentFinder	finder );

	public String
	getName();

	public InetAddress
	getBindIP();

	public TRHostTorrent
	hostTorrent(
		TOTorrent		torrent,
		boolean			persistent,
		boolean			passive )

		throws TRHostException;

	public TRHostTorrent
	publishTorrent(
		TOTorrent		torrent )

		throws TRHostException;

	public TRHostTorrent[]
	getTorrents();

	public int
	getTorrentCount();
	
		/**
		 * returns the host torrent for the torrent if it exists, null otherwise
		 * @param torrent
		 * @return
		 */

	public TRHostTorrent
	getHostTorrent(
		TOTorrent		torrent );

	public void
	addListener(
		TRHostListener	l );

	public void
	removeListener(
		TRHostListener	l );

	public void
	addListener2(
		TRHostListener2	l );

	public void
	removeListener2(
		TRHostListener2	l );

	public void
	addAuthenticationListener(
		TRHostAuthenticationListener	l );

	public void
	removeAuthenticationListener(
		TRHostAuthenticationListener	l );

	public void
	close();
}
