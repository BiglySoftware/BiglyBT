/*
 * Created on 29-Apr-2004
 * Created by Paul Gardner
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

package com.biglybt.pif.utils;

/**
 * @author parg
 *
 */

public interface
UTTimer
{
		/**
		 * Create a single-shot event with delay
		 * @param when			when it is to occur (absolute time, not relative)
		 * @param performer
		 * @return
		 */

	public UTTimerEvent
	addEvent(
		long					when,
		UTTimerEventPerformer	performer );

		/**
		 * Create a periodic event that will fire every specified number of milliseconds until cancelled
		 * or the timer is destroyed
		 * @param periodic_millis
		 * @param performer
		 * @return
		 */

	public UTTimerEvent
	addPeriodicEvent(
		long					periodic_millis,
		UTTimerEventPerformer	performer );

		/**
		 * Releases resources associated with this timer and renders it unusable
		 */

	public int
	getMaxThreads();
	
	public int
	getActiveThreads();
	
	public void
	destroy();
}
