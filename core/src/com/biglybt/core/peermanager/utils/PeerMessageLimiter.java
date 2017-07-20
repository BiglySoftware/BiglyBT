/*
 * Created on Jul 18, 2005
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

package com.biglybt.core.peermanager.utils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

import com.biglybt.core.util.SystemTime;



/**
 * Handles incoming peer message counting/timing/stats in order to catch and block abusive peers.
 */
public class PeerMessageLimiter {

  private final HashMap message_counts = new HashMap();


  public PeerMessageLimiter() {
    /*nothing*/
  }



  /**
   * Add the reception of the given message to time-limited count.
   * @param message_id message to count
   * @param max_counts max counts allowed within the given time limit
   * @param time_limit_ms time in ms that the count limiting applies
   * @return true if the added count is within acceptable time limits, false if there have been too many counts
   */
  public boolean countIncomingMessage( String message_id, int max_counts, int time_limit_ms ) {

    CountData data = (CountData)message_counts.get( message_id );

    if( data == null ) {  //new message
      data = new CountData( max_counts, time_limit_ms );
      message_counts.put( message_id, data );
    }

    long now = SystemTime.getCurrentTime();

    data.counts.addLast( new Long( now ) );

    if( data.counts.size() > data.max_counts ) {  //we've potentially reached our count limit

      long cutoff = now - data.time_limit;

      //prune out any expired counts
      for( Iterator it = data.counts.iterator(); it.hasNext(); ) {
        long time = ((Long)it.next()).longValue();

        if( time < cutoff ) {  //this count is older than the limit allows
          it.remove();  //drop it
        }
        else {  //still within limit
          break;
        }
      }

      if( data.counts.size() > data.max_counts ) {   //too many counts within the time limit
        return false;   //return error
      }
    }

    return true;
  }




  private static class CountData {
    private final int max_counts;
    private final int time_limit;
    private final LinkedList counts = new LinkedList();

    private CountData( int max_counts, int time_limit ) {
      this.max_counts = max_counts;
      this.time_limit = time_limit;
    }
  }

}
