/*
 * Created on 15-Dec-2005
 * Created by Paul Gardner
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */

package com.biglybt.plugin.extseed;

import java.net.URL;
import java.util.List;

import com.biglybt.pif.peers.Peer;
import com.biglybt.pif.peers.PeerManager;
import com.biglybt.pif.peers.PeerReadRequest;
import com.biglybt.pif.torrent.Torrent;

public interface
ExternalSeedReader
{
	public Torrent
	getTorrent();

	public String
	getName();

	public String
	getType();

	public String
	getStatus();

		// transient peers are moved from the download on failure

	public boolean
	isTransient();

	public boolean
	isPermanentlyUnavailable();

	public URL
	getURL();

	public String
	getIP();

	public int
	getPort();

	public boolean
	isActive();

	public boolean
	sameAs(
		ExternalSeedReader	other );

	public boolean
	checkActivation(
		PeerManager		peer_manager,
		Peer			peer );

	public void
	addRequests(
		List<PeerReadRequest>			requests );

	public void
	cancelRequest(
		PeerReadRequest	request );

	public int
	getMaximumNumberOfRequests();

	public void
	calculatePriorityOffsets(
		PeerManager		peer_manager,
		int[]			base_priorities );

	public int[]
	getPriorityOffsets();

	public void
	cancelAllRequests();

	public int
	getRequestCount();

	public List<PeerReadRequest>
	getExpiredRequests();

	public List<PeerReadRequest>
	getRequests();

	public int
	readBytes(
		int	max );

	public int[]
	getCurrentIncomingRequestProgress();

	public int[]
   	getOutgoingRequestedPieceNumbers();

   	public int
   	getOutgoingRequestCount();

	public byte[]
	read(
		int			piece_number,
		int			offset,
		int			length,
		int			timeout )

		throws ExternalSeedException;

	public void
	deactivate(
		String	reason );

	public void
	addListener(
		ExternalSeedReaderListener	l );

	public void
	removeListener(
		ExternalSeedReaderListener	l );
}
