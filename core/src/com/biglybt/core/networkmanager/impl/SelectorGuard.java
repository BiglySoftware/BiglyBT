/*
 * Created on Jul 28, 2004
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
package com.biglybt.core.networkmanager.impl.tcp;

import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemTime;


/**
 * Temp class designed to help detect Selector anomalies and cleanly re-open if necessary.
 *
 * NOTE:
 * As of JVM 1.4.2_03, after network connection disconnect/reconnect, usually-blocking
 * select() and select(long) calls no longer block, and will instead return immediately.
 * This can cause selector spinning and 100% cpu usage.
 * See:
 *   http://forum.java.sun.com/thread.jsp?forum=4&thread=293213
 *   http://developer.java.sun.com/developer/bugParade/bugs/4850373.html
 *   http://developer.java.sun.com/developer/bugParade/bugs/4881228.html
 * Fixed in JVM 1.4.2_05+ and 1.5b2+
 *
 * The workaround applied for the above is Thread.sleep() to avoid CPU hogging.
 *
 */
public class SelectorGuard {
  private static final int SELECTOR_SPIN_THRESHOLD    = 200;
  private static final int MAX_IGNORES = 5;

  private boolean marked = false;
  private int consecutiveZeroSelects = 0;
  private long beforeSelectTime;
  private long select_op_time;

  private final String type;
  private int ignores = 0;


  /**
   * Create a new SelectorGuard with the given failed count threshold.
   */
  public SelectorGuard( String _type) {
    this.type = _type;
  }

  public String
  getType()
  {
	  return( type );
  }

  /**
   * Run this method right before the select() operation to
   * mark the start time.
   */
  public void markPreSelectTime() {
    beforeSelectTime = SystemTime.getMonotonousTime();
    marked = true;
  }


  /**
   * Checks whether selector is still OK, and not spinning.
   */
  public void verifySelectorIntegrity( int num_keys_ready, long time_threshold ) {
    if( num_keys_ready > 0 ) {  //non-zero select, so OK
      ignores++;
      if( ignores > MAX_IGNORES ) {  //allow MAX_IGNORES / SELECTOR_SPIN_THRESHOLD to be successful select ops and still trigger a spin alert
        ignores = 0;
        consecutiveZeroSelects = 0;
      }
      return;
    }

    if (marked) marked = false;
    else Debug.out("Error: You must run markPreSelectTime() before calling isSelectorOK");

    select_op_time = SystemTime.getMonotonousTime() - beforeSelectTime;

    if( select_op_time > time_threshold ) {
      //zero-select, but over the time threshold, so OK
      consecutiveZeroSelects = 0;
      return;
    }

    //if we've gotten here, then we have a potential selector anomalie
    consecutiveZeroSelects++;

    if( consecutiveZeroSelects % 20 == 0 && Constants.isWindows ) {
    	// getting triggered with 20 +_ sometimes 40 due to general high CPU usage
      if ( consecutiveZeroSelects > 40 ){
    	  Debug.out( "consecutiveZeroSelects=" +consecutiveZeroSelects );
      }
    }


    if( consecutiveZeroSelects > SELECTOR_SPIN_THRESHOLD ) {
      //it seems that selector spin is somewhat common, but normal??? behavior, so just sleep a bit
      consecutiveZeroSelects = 0;
      try {
        Thread.sleep(50);
      } catch (Throwable t) {
        t.printStackTrace();
      }
      return;
    }

    //not yet over the count threshold
  }

}
