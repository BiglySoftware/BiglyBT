/*
 * File    : TRTrackerServerTorrent.java
 * Created : 13-Dec-2003
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

package com.biglybt.core.tracker.server;

/**
 * @author parg
 *
 */

import java.net.URL;
import java.util.List;

import com.biglybt.core.util.HashWrapper;

public interface
TRTrackerServerTorrent
{
	public HashWrapper
	getHash();

	public TRTrackerServerPeer[]
	getPeers();

	public TRTrackerServerPeerBase[]
	getQueuedPeers();

	public TRTrackerServerTorrentStats
	getStats();

	public void
	disableCaching();

	public void
	setMinBiasedPeers(
		int		num );

	public void
	setEnabled(
		boolean	enabled );

	public boolean
	isEnabled();

	public void
	setRedirects(
		URL[]		urls );

	public URL[]
	getRedirects();

	public TRTrackerServerTorrent
	addLink(
		String	link );

	public void
	removeLink(
		String	link );

	public void
	addExplicitBiasedPeer(
		String		ip,
		int			port );

	public void
	remove(
		TRTrackerServerPeerBase		peer );

	public void
	addListener(
		TRTrackerServerTorrentListener	l );

	public void
	removeListener(
		TRTrackerServerTorrentListener	l );

	public void
	addPeerListener(
		TRTrackerServerTorrentPeerListener	l );

	public void
	removePeerListener(
		TRTrackerServerTorrentPeerListener	l );

	public void
	importPeers(
		List		peers );

	public String
	getString();
}
