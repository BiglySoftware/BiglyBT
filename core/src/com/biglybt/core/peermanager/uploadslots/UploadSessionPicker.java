/*
 * Created on Jul 17, 2006
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
 * AELITIS, SARL au capital de 30,000 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */
package com.biglybt.core.peermanager.uploadslots;

import java.util.*;

import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.RandomUtils;




/**
 *
 */
public class UploadSessionPicker {

	private final LinkedList next_optimistics = new LinkedList();
	private final AEMonitor next_optimistics_mon = new AEMonitor( "UploadSessionPicker" );

	private final LinkedList helpers = new LinkedList();

	private final DownloadingRanker down_ranker = new DownloadingRanker();
	private final SeedingRanker seed_ranker = new SeedingRanker();


	protected UploadSessionPicker() {
		/*nothing*/
	}



	protected void registerHelper( UploadHelper helper ) {
		try {  next_optimistics_mon.enter();
			helpers.add( helper );

			int priority = helper.getPriority();

			//the higher the priority, the more optimistic unchoke chances they get
			for( int i=0; i < priority; i++ ) {
				insertHelper( helper );
			}
		}
		finally {  next_optimistics_mon.exit();  }
	}



	protected void deregisterHelper( UploadHelper helper ) {
		try {  next_optimistics_mon.enter();
			helpers.remove( helper );

			boolean rem = next_optimistics.removeAll( Collections.singleton( helper ) );
			if( !rem ) Debug.out( "!rem" );
		}
		finally {  next_optimistics_mon.exit();  }
	}



	protected void updateHelper( UploadHelper helper ) {
		try {  next_optimistics_mon.enter();
			int priority = helper.getPriority();  //new priority

			int count = 0;

			for( Iterator it = next_optimistics.iterator(); it.hasNext(); ) {
				UploadHelper h = (UploadHelper)it.next();
				if( h == helper ) {
					count++;

					if( count > priority ) {  //new priority is lower
						it.remove();  //trim
					}
				}
			}

			if( count < priority ) {  //new priority is higher
				for( int i=count; i < priority; i++ ) {
					insertHelper( helper );  //add
				}
			}
		}
		finally {  next_optimistics_mon.exit();  }
	}



	private void insertHelper( UploadHelper helper ) {
		int pos = RandomUtils.nextInt( next_optimistics.size() + 1 );  //pick a random location
		next_optimistics.add( pos, helper );  //store into location
	}




	protected int getHelperCount() {
		try {  next_optimistics_mon.enter();
			return next_optimistics.size();
		}
		finally {  next_optimistics_mon.exit();  }
	}



	//this picks both downloading and seeding sessions
	protected UploadSession pickNextOptimisticSession() {

		try {  next_optimistics_mon.enter();

			HashSet failed_helpers = null;

			int loops_allowed = next_optimistics.size();

			while( loops_allowed > 0 ) {

				UploadHelper helper = (UploadHelper)next_optimistics.removeFirst();  //take from front

				next_optimistics.addLast( helper );   //add back at end

				if( failed_helpers == null || !failed_helpers.contains( helper ) ) {   //pre-emptive check to see if we've already tried this helper

					PEPeer peer;

					if( helper.isSeeding() ) {
						peer = seed_ranker.getNextOptimisticPeer( helper.getAllPeers() );
					}
					else {
						peer = down_ranker.getNextOptimisticPeer( helper.getAllPeers() );
					}

					if( peer == null )  {  //no peers from this helper to unchoke

						if( failed_helpers == null )  failed_helpers = new HashSet();   //lazy alloc

						failed_helpers.add( helper );  //remember this helper in case we get it again in another loop round
					}
					else {   //found an optimistic peer!

						return new UploadSession( peer, helper.isSeeding() ? UploadSession.TYPE_SEED : UploadSession.TYPE_DOWNLOAD );
					}
				}

				loops_allowed--;
			}

			return null;  //no optimistic peer found

		}
		finally {  next_optimistics_mon.exit();  }
	}




	private ArrayList<PEPeer> globalGetAllDownloadPeers() {
		try {  next_optimistics_mon.enter();
			ArrayList<PEPeer> all = new ArrayList<>();

			for( Iterator<PEPeer> it = helpers.iterator(); it.hasNext(); ) {
				UploadHelper helper = (UploadHelper)it.next();

				if( !helper.isSeeding() )  {  //filter out seeding
					all.addAll( helper.getAllPeers() );
				}
			}

			return all;
		}
		finally {  next_optimistics_mon.exit();  }
	}



	//this picks downloading sessions only
	protected LinkedList<UploadSession> pickBestDownloadSessions( int max_sessions ) {
		//TODO factor download priority into best calculation?

		ArrayList<PEPeer> all_peers = globalGetAllDownloadPeers();

		if( all_peers.isEmpty() )  return new LinkedList();

		ArrayList<PEPeer> best = down_ranker.rankPeers( max_sessions, all_peers );

		if( best.size() != max_sessions ) {
			Debug.outNoStack( "best.size()[" +best.size()+ "] != max_sessions[" +max_sessions+ "]" );
		}

		if( best.isEmpty() ) {
			return new LinkedList<>();
		}


		LinkedList<UploadSession> best_sessions = new LinkedList<>();

		for( Iterator<PEPeer> it = best.iterator(); it.hasNext(); ) {
			PEPeer peer = it.next();
			UploadSession session = new UploadSession( peer, UploadSession.TYPE_DOWNLOAD );
			best_sessions.add( session );
		}

		return best_sessions;
	}




}
