/*
 * Created on 02-Feb-2006
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

package com.biglybt.pif.peers;

public interface
PeerEvent
{
	/**
	 * peer state has changed
	 * data - Integer holding one of the state values defined in Peer
	 */

	public static final int	ET_STATE_CHANGED		= 1;

	/**
	 * The peer has sent us a bad piece data chunk.
	 * data - Integer[2]
	 *     piece_num piece that failed hash check
	 *     total_bad_chunks total number of bad chunks sent by this peer so far
	 */

	public static final int	ET_BAD_CHUNK			= 2;

	/** The peer asserts that their availability should be added to the torrent-global availability pool.
	 * The peer must send this when, and only when, their availability is known (such as after
	 * receiving a bitfield message) but not after going to CLOSING state.  After having sent this
	 * message, the peer must remember they've done so and later send a corresponding removeAvailability
	 * message at an appropriate time.
	 * data - peerHavePieces boolean[] of pieces availabile
	 */

	public static final int	ET_ADD_AVAILABILITY		= 3;

	/** The peer asserts that their availability must now be taken from the torrent-global availability pool
	 * The peer must send this only after having sent a corresponding addAvailability message,
	 * and must not send it in a state prior to CLOSING state.  The BitFlags must be complete, with all
	 * pieces from any Bitfield message as well as those from any Have messages.
	 * data -  peerHavePieces boolean[] of pieces no longer available
	 */

	public static final int	ET_REMOVE_AVAILABILITY	= 4;

	public int
	getType();

	public Object
	getData();
}
