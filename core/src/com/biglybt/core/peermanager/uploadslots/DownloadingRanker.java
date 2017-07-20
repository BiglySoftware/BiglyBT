/*
 * Created on Apr 5, 2005
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

package com.biglybt.core.peermanager.uploadslots;

import java.util.ArrayList;

import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peermanager.unchoker.UnchokerUtil;


/**
 * Unchoker implementation to be used while in downloading mode.
 */
public class DownloadingRanker {


  public DownloadingRanker() {
    /* nothing */
  }



  public PEPeer getNextOptimisticPeer( ArrayList<PEPeer> all_peers ) {
  	return UnchokerUtil.getNextOptimisticPeer( all_peers, true, true );  //TODO extract and optimize?
  }




  public ArrayList<PEPeer> rankPeers( int max_to_unchoke, ArrayList<PEPeer> all_peers ) {

  	 ArrayList<PEPeer> best_peers = new ArrayList<>();
  	 long[] bests = new long[ max_to_unchoke ];  //ensure we never rank more peers than needed


    //fill slots with peers who we are currently downloading the fastest from
    for( int i=0; i < all_peers.size(); i++ ) {
    	PEPeer peer = all_peers.get( i );

      if( peer.isInteresting() && UnchokerUtil.isUnchokable( peer, false ) ) {  //viable peer found
        long rate = peer.getStats().getSmoothDataReceiveRate();
        if( rate > 256 ) {  //filter out really slow peers
          UnchokerUtil.updateLargestValueFirstSort( rate, bests, peer, best_peers, 0 );
        }
      }
    }


    //if we havent yet picked enough slots
    if( best_peers.size() < max_to_unchoke ) {
      int start_pos = best_peers.size();

      //fill the remaining slots with peers that we have downloaded from in the past
      for( int i=0; i < all_peers.size(); i++ ) {
    	PEPeer peer = all_peers.get( i );

        if( peer.isInteresting() && UnchokerUtil.isUnchokable( peer, false ) && !best_peers.contains( peer ) ) {  //viable peer found
          long uploaded_ratio = peer.getStats().getTotalDataBytesSent() / (peer.getStats().getTotalDataBytesReceived() + (DiskManager.BLOCK_SIZE-1));
          //make sure we haven't already uploaded several times as much data as they've sent us
          if( uploaded_ratio <3) {
            UnchokerUtil.updateLargestValueFirstSort( peer.getStats().getTotalDataBytesReceived(), bests, peer, best_peers, start_pos );
          }
        }
      }
    }


    return best_peers;
  }




}
