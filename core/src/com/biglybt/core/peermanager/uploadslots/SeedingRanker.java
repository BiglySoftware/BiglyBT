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

import com.biglybt.core.peer.impl.PEPeerTransport;
import com.biglybt.core.peermanager.unchoker.UnchokerUtil;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.RandomUtils;



/**
 * Unchoker implementation to be used while in seeding mode.
 */
public class SeedingRanker {


  public SeedingRanker() {
    /* nothing */
  }


  private PEPeerTransport getNextOptimisticPeerExec( ArrayList all_peers ) {

  	if( all_peers.isEmpty() )  {  //no connected peers
  		Debug.out( "all_peers.isEmpty()" );
  		return null;
  	}

  	int pos = RandomUtils.nextInt( all_peers.size() );  //pick a random peer to start

  	for( int i=0; i < all_peers.size(); i++ ) {  //ensure we only loop once

  		PEPeerTransport peer = (PEPeerTransport)all_peers.get( pos );  //get next potential peer

  		if( peer.isChokedByMe() && UnchokerUtil.isUnchokable( peer, true ) ) {   //filter out peers already unchoked, and unchokable

  			return peer;  //found the next optimistic!
  		}

  		pos++;  //try next

  		if( pos >= all_peers.size() ) {  //loop 'round if necessary
  			pos = 0;
  		}
  	}

  	Debug.out( "no optimistic-able seeding peers found" );
  	return null;
  }


  public PEPeerTransport getNextOptimisticPeer( ArrayList all_peers ) {
  	PEPeerTransport picked = getNextOptimisticPeerExec( all_peers );

  	//TODO test to see if peers really are picked evenly

  	return picked;
  }



}
