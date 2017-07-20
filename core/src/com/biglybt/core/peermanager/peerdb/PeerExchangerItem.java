/*
 * Created on Apr 27, 2005
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

package com.biglybt.core.peermanager.peerdb;


import java.util.*;

import com.biglybt.core.peer.util.PeerUtils;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.LightHashMap;

/**
 *
 */
public class PeerExchangerItem {
  public static final int MAX_PEERS_PER_VOLLEY = 50;
  private static final int MAX_KNOWN_PER_PEER = 500;


  private final PeerDatabase 	parent_db;
  private final PeerItem 		base_peer;
  private final String			network;

  private final LinkedHashSet<PeerItem> connections_added 		= new LinkedHashSet<>();
  private final LinkedHashSet<PeerItem> connections_dropped 	= new LinkedHashSet<>();
  private final Map<PeerItem,Object> connected_peers = new LightHashMap<>();
  private final AEMonitor peers_mon = new AEMonitor( "PeerConnectionItem" );
  private boolean maintain_peers_state = true;  //assume we do until explicitly disabled
  private final Helper helper;


  protected PeerExchangerItem( PeerDatabase parent_db, PeerItem peer, Helper helper ) {
    this.parent_db = parent_db;
    this.base_peer = peer;
    this.helper = helper;

    network = peer.getNetwork();
  }


  protected PeerItem getBasePeer(){ return base_peer; }

  protected Helper getHelper() {  return helper;  }


  /**
   * Add peer info obtained via peer exchange.
   * @param peer to add
   */
  public void addConnectedPeer( PeerItem peer ) {
    try{  peers_mon.enter();
      if( !maintain_peers_state )  return;

      int max_cache_size = PeerUtils.MAX_CONNECTIONS_PER_TORRENT;
      if( max_cache_size < 1 || max_cache_size > MAX_KNOWN_PER_PEER )  max_cache_size = MAX_KNOWN_PER_PEER;

      if( connected_peers.size() < max_cache_size ) {
        connected_peers.put( peer, null );
      }
    }
    finally{  peers_mon.exit();  }
  }


  /**
   * Remove peer info obtained via peer exchange.
   * @param peer to remove
   */
  public void dropConnectedPeer( PeerItem peer ) {
    try{  peers_mon.enter();

      connected_peers.remove( peer );
    }
    finally{  peers_mon.exit();  }
  }




  protected void notifyAdded( PeerItem peer_connection ) {
    try{  peers_mon.enter();
      if( !maintain_peers_state )  return;

      if( !connections_dropped.contains( peer_connection ) ) {
        if( !connections_added.contains( peer_connection ) ) {
          connections_added.add( peer_connection );  //register new add
        }
      }
      else {  //was dropped and then re-added
        connections_dropped.remove( peer_connection );  //pull drop and ignore add
      }
    }
    finally{  peers_mon.exit();  }
  }


  protected void notifyDropped( PeerItem peer_connection ) {
    try{  peers_mon.enter();
      if( !maintain_peers_state )  return;

      if( !connections_added.contains( peer_connection ) ) {
        if( !connections_dropped.contains( peer_connection ) ) {
          connections_dropped.add( peer_connection );  //register new drop
        }
      }
      else {  //was added and then re-dropped
        connections_added.remove( peer_connection );  //pull add and ignore drop
      }
    }
    finally{  peers_mon.exit();  }
  }

  public void
  seedStatusChanged()
  {
	  parent_db.seedStatusChanged( this );
  }

  /**
   * Get the list of peer connections added since this method was last called.
   * @return new peer connections
   */

  public PeerItem[] getNewlyAddedPeerConnections() {
    try{  peers_mon.enter();
      if( connections_added.isEmpty() )  return null;

      int num_to_send = connections_added.size() > MAX_PEERS_PER_VOLLEY ? MAX_PEERS_PER_VOLLEY : connections_added.size();

      PeerItem[] peers = new PeerItem[ num_to_send ];

      Iterator<PeerItem> it = connections_added.iterator();

      for( int i=0; i < num_to_send; i++ ) {
        peers[i] = it.next();
        it.remove();
      }

      return peers;
    }
    finally{  peers_mon.exit();  }
  }

  public PeerItem[]
  getNewlyAddedPeerConnections(
	  String network )
  {
	  try{
		  peers_mon.enter();

		  if ( connections_added.isEmpty())  return null;

		  int num_to_send = connections_added.size() > MAX_PEERS_PER_VOLLEY ? MAX_PEERS_PER_VOLLEY : connections_added.size();

		  List<PeerItem> peers = new ArrayList<>(num_to_send);

		  Iterator<PeerItem> it = connections_added.iterator();

		  while( peers.size() < num_to_send && it.hasNext()){

			  PeerItem	peer = it.next();

			  if ( peer.getNetwork() == network ){

				  peers.add( peer );
			  }

			  	// throw away items that don't match this network to prevent them from building
			  	// up and filling the cache preventing addition of other network items. This
			  	// could be improved by either maintaining separate caches per network or perhaps
			  	// only trashing non-network entries when cache gets (near to)full

			  it.remove();
		  }

		  if ( peers.size() == 0 ){

			  return( null );
		  }

		  return peers.toArray( new PeerItem[peers.size()]);

	  }finally{

		  peers_mon.exit();
	  }
  }

  /**
   * Get the list of peer connections dropped since this method was last called.
   * @return dropped peer connections
   */
  public PeerItem[] getNewlyDroppedPeerConnections() {
    try{  peers_mon.enter();
      if( connections_dropped.isEmpty() )  return null;

      int num_to_send = connections_dropped.size() > MAX_PEERS_PER_VOLLEY ? MAX_PEERS_PER_VOLLEY : connections_dropped.size();

      PeerItem[] peers = new PeerItem[ num_to_send ];

      Iterator<PeerItem> it = connections_dropped.iterator();

      for( int i=0; i < num_to_send; i++ ) {
        peers[i] = it.next();
        it.remove();
      }
      return peers;
    }
    finally{  peers_mon.exit();  }
  }

  public PeerItem[]
  getNewlyDroppedPeerConnections(
	  String network )
  {
	  try{
		  peers_mon.enter();

		  if ( connections_dropped.isEmpty())  return null;

		  int num_to_send = connections_dropped.size() > MAX_PEERS_PER_VOLLEY ? MAX_PEERS_PER_VOLLEY : connections_dropped.size();

		  List<PeerItem> peers = new ArrayList<>(num_to_send);

		  Iterator<PeerItem> it = connections_dropped.iterator();

		  while( peers.size() < num_to_send && it.hasNext()){

			  PeerItem	peer = it.next();

			  if ( peer.getNetwork() == network ){

				  peers.add( peer );
			  }

			  	// see above comment

			  it.remove();
		  }

		  if ( peers.size() == 0 ){

			  return( null );
		  }

		  return peers.toArray( new PeerItem[peers.size()]);

	  }finally{

		  peers_mon.exit();
	  }
  }

  /**
   * Clears all current peer state records and stops any future state maintenance.
   */
  public void
  disableStateMaintenance()
  {
    try{
    	peers_mon.enter();

    	maintain_peers_state = false;

    	connections_added.clear();

    	connections_dropped.clear();

    	connected_peers.clear();

    }finally{
    	peers_mon.exit();
    }
  }

  public void
  enableStateMaintenance()
  {
	    try{
	    	peers_mon.enter();

	    	if ( maintain_peers_state ){

	    		return;
	    	}

	    	maintain_peers_state = true;

	    }finally{

	    	peers_mon.exit();
	    }
 }


  protected boolean isConnectedToPeer( PeerItem peer ) {
    try{  peers_mon.enter();

      return connected_peers.containsKey( peer );
    }
    finally{  peers_mon.exit();  }
  }


  protected PeerItem[] getConnectedPeers() {
    try{  peers_mon.enter();

      PeerItem[] peers = new PeerItem[ connected_peers.size() ];
      connected_peers.keySet().toArray( peers );
      return peers;
    }
    finally{  peers_mon.exit();  }
  }




  public void destroy() {
    parent_db.deregisterPeerConnection( base_peer );

    try{  peers_mon.enter();
      connections_added.clear();
      connections_dropped.clear();
      connected_peers.clear();
    }
    finally{  peers_mon.exit();  }
  }


  public interface Helper {
    /**
     * Does this connection item represent a seed peer?
     * @return true if seeding, false if not
     */
    public boolean isSeed();
  }

}

