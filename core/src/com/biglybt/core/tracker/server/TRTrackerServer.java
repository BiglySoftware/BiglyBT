/*
 * File    : TRTrackerServer.java
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

package com.biglybt.core.tracker.server;

import java.net.InetAddress;
import java.util.Set;

import com.biglybt.core.util.Constants;

public interface
TRTrackerServer
{
	public static final String	DEFAULT_NAME	= Constants.APP_NAME;

	public static final int DEFAULT_MIN_RETRY_DELAY 		= 120;
	public static final int DEFAULT_MAX_RETRY_DELAY 		= 3600;
	public static final int DEFAULT_INC_BY					= 60;
	public static final int DEFAULT_INC_PER			 		= 10;
	public static final int DEFAULT_SCRAPE_RETRY_PERCENTAGE	= 200;

	public static final int	DEFAULT_SCRAPE_CACHE_PERIOD				= 5000;
	public static final int	DEFAULT_ANNOUNCE_CACHE_PERIOD			= 500;
	public static final int	DEFAULT_ANNOUNCE_CACHE_PEER_THRESHOLD	= 500;

	public static final int DEFAULT_TRACKER_PORT 		= 6969;
	public static final int DEFAULT_TRACKER_PORT_SSL	= 7000;

	public static final int DEFAULT_NAT_CHECK_SECS		= 15;

	public String
	getName();

	public int
	getPort();

	public String
	getHost();

	public InetAddress
	getBindIP();

	public void
	setReady();

	public void
	setEnabled(
		boolean	enabled );

	public boolean
	isSSL();

	public void
	setEnableKeepAlive(
		boolean	enable );

	public TRTrackerServerTorrent
	permit(
		String		originator,
		byte[]		hash,
		boolean		explicit  )

		throws TRTrackerServerException;

	public TRTrackerServerTorrent
	permit(
		String		originator,
		byte[]		hash,
		boolean		explicit,
		boolean		enabled )

		throws TRTrackerServerException;

	public void
	deny(
		byte[]		hash,
		boolean		explicit )

		throws TRTrackerServerException;

	public TRTrackerServerTorrentStats
	getStats(
		byte[]		hash );

	public TRTrackerServerPeer[]
	getPeers(
		byte[]		hash );

	public TRTrackerServerStats
	getStats();

	public void
	setBiasedPeers(
		Set		ips );

	public void
	addListener(
		TRTrackerServerListener	l );

	public void
	removeListener(
		TRTrackerServerListener	l );

	public void
	addListener2(
		TRTrackerServerListener2	l );

	public void
	removeListener2(
		TRTrackerServerListener2	l );


	public void
	addRequestListener(
		TRTrackerServerRequestListener	l );

	public void
	removeRequestListener(
		TRTrackerServerRequestListener	l );

	public void
	addAuthenticationListener(
		TRTrackerServerAuthenticationListener	l );

	public void
	removeAuthenticationListener(
		TRTrackerServerAuthenticationListener	l );

	public void
	close();
}
