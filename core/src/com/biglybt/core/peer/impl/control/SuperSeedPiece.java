/*
 * File    : SuperSeedPiece.java
 * Created : 13 d�c. 2003}
 * By      : Olivier
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
package com.biglybt.core.peer.impl.control;

import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.impl.PEPeerControl;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.Ignore;
import com.biglybt.core.util.SystemTime;

/**
 * @author Olivier
 *
 */
public class SuperSeedPiece {


  //private PEPeerControl manager;
  private final int pieceNumber;

  private int level;
  private long timeFirstDistributed;
  private PEPeer firstReceiver;
  //private int numberOfPeersWhenFirstReceived;
  private int timeToReachAnotherPeer;

  	// use class monitor to reduce number of monitor objects (low contention here)

  private static final AEMonitor	class_mon	= new AEMonitor( "SuperSeedPiece:class" );


  public SuperSeedPiece(PEPeerControl manager,int _pieceNumber) {
    Ignore.ignore( manager );
    pieceNumber = _pieceNumber;
    level = 0;
  }

  public void peerHasPiece(PEPeer peer) {
  	try{
  		class_mon.enter();
  			//System.out.println("piece: #"+pieceNumber+" , old level:"+level+", timeFirstDistributed:"+timeFirstDistributed+ "/ timetoreachanotherpeer:"+timeToReachAnotherPeer);
  			if(level < 2) {
  				// first time that somebody tells us this piece exists elsewhere
  				// if peer=null, it is due to bitfield scan, so firstreceiver gets NULL
  				firstReceiver = peer;
  				timeFirstDistributed = SystemTime.getCurrentTime();
  				//numberOfPeersWhenFirstReceived = manager.getNbPeers();
  				level = 2;
  			}
  			else {
  				// level=2 or 3 and we arrive here when somebody has got the piece, either BT_HAVE or bitfield scan
  				// if we are here due to bitfield scan, 'peer' = null --> do nothing
  				// if level=2 --> mark piece redistributed, set speedstatus of firstreceiver, bump level to 3
  				// if level=3 --> this piece has been already seen at 3rd party earlier, do nothing
  				if(peer != null && firstReceiver != null && level==2) {
  					timeToReachAnotherPeer = (int) (SystemTime.getCurrentTime() - timeFirstDistributed);
  					firstReceiver.setUploadHint(timeToReachAnotherPeer);
  					level = 3;
  				}
  			}
  	}
  	finally{
  		class_mon.exit();
  	}
  }

  public int getLevel() {
    return level;
  }

  public void pieceRevealedToPeer() {
  	try{
  		class_mon.enter();

  		level = 1;
  	}finally{

  		class_mon.exit();
  	}
  }
  /**
   * @return Returns the pieceNumber.
   */
  public int getPieceNumber() {
    return pieceNumber;
  }

  public void peerLeft() {
    if(level == 1)
      level = 0;
  }

  public void updateTime() {
    if(level < 2)
   	// not yet distributed, no effect on speed status
      return;
    if(timeToReachAnotherPeer > 0)
   	// piece has been seen elsewhere, no effect on speed status any more
      return;
    if(firstReceiver == null)
   	// piece was found due to bitfield scan, no idea how it got distributed
      return;
    int timeToSend = (int) (SystemTime.getCurrentTime() - timeFirstDistributed);
    if(timeToSend > firstReceiver.getUploadHint())
      firstReceiver.setUploadHint(timeToSend);
  }

}
