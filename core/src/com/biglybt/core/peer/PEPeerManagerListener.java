/*
 * File    : PEPeerManagerListener.java
 * Created : 22-Nov-2003
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

package com.biglybt.core.peer;

import com.biglybt.core.peermanager.peerdb.PeerItem;
import com.biglybt.pif.peers.PeerReadRequest;

/**
 * @author parg
 *
 */
public interface
PEPeerManagerListener
{

  /**
   * The given peer has been added to the given manager.
   * @param manager to which peer belongs
   * @param peer added
   */
  public void peerAdded( PEPeerManager manager, PEPeer peer );


  /**
   * The given peer has been removed from the given manager.
   * @param manager to which peer belongs
   * @param peer removed
   */
  public void peerRemoved( PEPeerManager manager, PEPeer peer );

  /**
   * piece activated
   * @param peice
   * @param for_peer maybe null if not for a particular peer
   */

  public void pieceAdded( PEPeerManager manager, PEPiece piece, PEPeer for_peer );

  public void pieceRemoved( PEPeerManager manager, PEPiece piece );

  public void requestAdded( PEPeerManager manager, PEPiece piece, PEPeer peer, PeerReadRequest request );

  public void peerDiscovered( PEPeerManager manager, PeerItem peer, PEPeer finder );

  public void peerSentBadData( PEPeerManager manager, PEPeer peer, int piece_number );

  public void pieceCorrupted( PEPeerManager manager, int piece_number );

  public void destroyed( PEPeerManager manager );
}
