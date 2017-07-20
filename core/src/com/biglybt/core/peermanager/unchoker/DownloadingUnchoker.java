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

package com.biglybt.core.peermanager.unchoker;

import java.util.ArrayList;
import java.util.Iterator;

import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.peer.PEPeer;


/**
 * Unchoker implementation to be used while in downloading mode.
 */
public class DownloadingUnchoker implements Unchoker {

  private ArrayList<PEPeer> chokes 		= new ArrayList<>();
  private ArrayList<PEPeer> unchokes 	= new ArrayList<>();


  protected DownloadingUnchoker() {
    /* nothing */
  }

  @Override
  public boolean
  isSeedingUnchoker()
  {
	  return( false );
  }

  @Override
  public ArrayList<PEPeer> getImmediateUnchokes(int max_to_unchoke, ArrayList<PEPeer> all_peers ) {
    ArrayList<PEPeer> to_unchoke = new ArrayList<>();

    //count all the currently unchoked peers
    int num_unchoked = 0;
    for( int i=0; i < all_peers.size(); i++ ) {
    	PEPeer peer = all_peers.get( i );
      if( !peer.isChokedByMe() )  num_unchoked++;
    }

    //if not enough unchokes
    int needed = max_to_unchoke - num_unchoked;
    if( needed > 0 ) {
      for( int i=0; i < needed; i++ ) {
    	PEPeer peer = UnchokerUtil.getNextOptimisticPeer( all_peers, true, true );
        if( peer == null )  break;  //no more new unchokes avail
        to_unchoke.add( peer );
        peer.setOptimisticUnchoke( true );
      }
    }

    return to_unchoke;
  }



  @Override
  public void calculateUnchokes(int max_to_unchoke, ArrayList<PEPeer> all_peers, boolean force_refresh, boolean check_priority_connections, boolean do_high_latency_peers ) {
    int max_optimistic = ((max_to_unchoke - 1) / 10) + 1;  //one optimistic unchoke for every 10 upload slots

    ArrayList<PEPeer> optimistic_unchokes = new ArrayList<>();
    ArrayList<PEPeer> best_peers = new ArrayList<>();
    long[] bests = new long[ max_to_unchoke ];  //ensure we never pick more slots than allowed to unchoke


    //get all the currently unchoked peers
    for( int i=0; i < all_peers.size(); i++ ) {
      PEPeer peer = all_peers.get( i );

      if( !peer.isChokedByMe() ) {
        if( UnchokerUtil.isUnchokable( peer, true ) ) {
          unchokes.add( peer );
          if( peer.isOptimisticUnchoke() ) {
            optimistic_unchokes.add( peer );
          }
        }
        else {  //should be immediately choked
          chokes.add( peer );
        }
      }
    }


    if( !force_refresh ) {  //ensure current optimistic unchokes remain unchoked
      for( int i=0; i < optimistic_unchokes.size(); i++ ) {
    	PEPeer peer = optimistic_unchokes.get( i );

        if( i < max_optimistic ) {
          best_peers.add( peer );  //add them to the front of the "best" list
        }
        else { //too many optimistics
          peer.setOptimisticUnchoke( false );
        }
      }
    }


    //fill slots with peers who we are currently downloading the fastest from
    int start_pos = best_peers.size();
    for( int i=0; i < all_peers.size(); i++ ) {
      PEPeer peer = all_peers.get( i );

      if( peer.isInteresting() && UnchokerUtil.isUnchokable( peer, false ) && !best_peers.contains( peer ) ) {  //viable peer found
        long rate = peer.getStats().getSmoothDataReceiveRate();
        if( rate > 256 ) {  //filter out really slow peers
          UnchokerUtil.updateLargestValueFirstSort( rate, bests, peer, best_peers, start_pos );
        }
      }
    }


    //if we havent yet picked enough slots
    if( best_peers.size() < max_to_unchoke ) {
      start_pos = best_peers.size();

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


    if( force_refresh ) {
      //make space for new optimistic unchokes
      while( best_peers.size() > max_to_unchoke - max_optimistic ) {
        best_peers.remove( best_peers.size() - 1 );
      }
    }


    //if we still have remaining slots
    while( best_peers.size() < max_to_unchoke ) {
      PEPeer peer = UnchokerUtil.getNextOptimisticPeer( all_peers, true, true );  //just pick one optimistically
      if( peer == null )  break;  //no more new unchokes avail

      if( !best_peers.contains( peer ) ) {
        best_peers.add( peer );
        peer.setOptimisticUnchoke( true );
      }
      else {
        //we're here because the given optimistic peer is already "best", but is choked still,
        //which means it will continually get picked by the getNextOptimisticPeer() method,
        //and we'll loop forever if there are no other peers to choose from
        peer.sendUnChoke();  //send unchoke immediately, so it won't get picked optimistically anymore
      	//all_peers.remove( peer );  //remove from all_peers list, so it won't get picked optimistically anymore    //TODO
      }
    }


    //update chokes
    for( Iterator<PEPeer> it = unchokes.iterator(); it.hasNext(); ) {
      PEPeer peer = it.next();

      if( !best_peers.contains( peer ) ) {  //should be choked
        if( best_peers.size() < max_to_unchoke ) {  //but there are still slots needed (no optimistics avail), so don't bother choking them
          best_peers.add( peer );
        }
        else {
          chokes.add( peer );
          it.remove();
        }
      }
    }

    //update unchokes
    for( int i=0; i < best_peers.size(); i++ ) {
      PEPeer peer = best_peers.get( i );

      if( !unchokes.contains( peer ) ) {
        unchokes.add( peer );
      }
    }

    if ( do_high_latency_peers ){

    	UnchokerUtil.doHighLatencyPeers( chokes, unchokes, true );
    }
  }


  @Override
  public ArrayList<PEPeer> getChokes() {
    ArrayList<PEPeer> to_choke = chokes;
    chokes = new ArrayList<>();
    return to_choke;
  }


  @Override
  public ArrayList<PEPeer> getUnchokes() {
    ArrayList<PEPeer> to_unchoke = unchokes;
    unchokes  = new ArrayList<>();
    return to_unchoke;
  }

}
