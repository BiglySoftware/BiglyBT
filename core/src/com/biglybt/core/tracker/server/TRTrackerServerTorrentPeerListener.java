/*
 * Created on 18 May 2006
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

package com.biglybt.core.tracker.server;

import java.util.Map;

public interface
TRTrackerServerTorrentPeerListener
{
	public static final int	ET_STARTED			= 1;
	public static final int	ET_UPDATED			= 2;
	public static final int	ET_COMPLETE			= 3;

		// all the following result in the peer being removed from the tracker's tables

	public static final int	ET_STOPPED			= 4;		// peer removed due to stop
	public static final int	ET_TIMEOUT			= 5;		// peer has timed out and been removed
	public static final int	ET_REPLACED			= 5;		// peer's address has changed and someone else already has it - old one is removed
	public static final int	ET_TOO_MANY_PEERS	= 6;		// peer removed due to too many peers
	public static final int	ET_FAILED			= 7;		// eventOccurred method threw exception

	public static final int	ET_ANNOUNCE			= 8;		// announce event - reply can contain List of explicit peers to return


	public Map
	eventOccurred(
		TRTrackerServerTorrent	torrent,
		TRTrackerServerPeer		peer,
		int						event,
		String					url_parameters )

		throws TRTrackerServerException;
}
