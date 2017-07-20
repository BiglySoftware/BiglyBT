/*
 * Created on Feb 25, 2005
 * Created by Alon Rohter
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

package com.biglybt.core.peer;

import com.biglybt.core.peermanager.piecepicker.util.BitFlags;

/**
 * Listener for peer events.
 */
public interface PEPeerListener {

  /**
   * The peer has changed to the given state.
   * @param peer the peer the message is about
   * @param new_state of peer
   */
  public void stateChanged(PEPeer peer, int new_state );

  /**
   * The peer has sent us a bad piece data chunk.
   * @param peer the peer the message is about
   * @param piece_num piece that failed hash check
   * @param total_bad_chunks total number of bad chunks sent by this peer so far
   */
  public void sentBadChunk(PEPeer peer, int piece_num, int total_bad_chunks );

  /** The peer asserts that their availability should be added to the torrent-global availability pool
   * The peer must send when, and only when, their availability is known
   * but not after going to CLOSING state.  Upon sending this message, the peer must remember it was
   * sent, and then later send a corresponding removeAvailability message
   * @param peer the message is about
   * @param peerHavePieces BitFlags of pieces availabile
   */
  public void addAvailability(final PEPeer peer, final BitFlags peerHavePieces);

  /** The peer asserts that their availability must now be taken from the torrent-global availability pool
   * The peer must send this only after having sent a corresponding addAvailability message,
   * and must not send it in a state prior to CLOSING state.  The BitFlags must be complete, with all
   * pieces from any Bitfield message as well as those from any Have messages.
   * @param peer the message is about
   * @param peerHavePieces BitFlags of pieces no longer available
   */
  public void removeAvailability(final PEPeer peer, final BitFlags peerHavePieces);

}
