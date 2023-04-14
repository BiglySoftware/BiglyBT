/*
 * Created on Sep 27, 2004
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

package com.biglybt.core.networkmanager.impl;

import com.biglybt.core.networkmanager.EventWaiter;
import com.biglybt.core.networkmanager.RateHandler;

/**
 * Interface designation for rate-limited entities controlled by a handler.
 */
public interface 
RateControlledEntity {
	/**
	 * Uses fair round-robin scheduling of processing ops.
	 */
	public static final int PRIORITY_NORMAL = 0;

	/**
	 * Guaranteed scheduling of processing ops, with preference over normal-priority entities.
	 */
	public static final int PRIORITY_HIGH   = 1;

	/**
	 * Is ready for a processing op.
	 * @return true if it can process >0 bytes, false if not ready
	 */
	
	public boolean 
	canProcess(  
		EventWaiter waiter );
	/**
	 * Attempt to do a processing operation.
	 * @return true if >0 bytes were processed (success), false if 0 bytes were processed (failure)
	 */
	
	public int 
	doProcessing( 
		EventWaiter waiter, 
		int max_bytes_permitted );

	/**
	 * Get this entity's priority level.
	 * @return priority
	 */
	public int getPriority();

	/**
	 * stats functions
	 * @return
	 */

	public boolean
	getPriorityBoost();

	public long
	getBytesReadyToWrite();

	/**
	 * If there are no connections then the waiter will be kicked when a connection arrives
	 * @param waiter
	 * @return
	 */

	public int
	getConnectionCount( EventWaiter waiter );

	/**
	 * The waiter is kicked if the ready condition changes
	 * @param waiter
	 * @return
	 */

	public int
	getReadyConnectionCount( EventWaiter waiter );

	public RateHandler
	getRateHandler();

	public String
	getString();
}
