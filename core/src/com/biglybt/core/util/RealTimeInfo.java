/*
 * Created on Nov 8, 2007
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


package com.biglybt.core.util;

public class
RealTimeInfo
{
	private static volatile int		realtime_task_count;

	private static volatile long	progressive_bytes_per_sec;

	public static synchronized void
	addRealTimeTask()
	{
		realtime_task_count++;

		//System.out.println( "RealTimeInfo: tasks=" + realtime_task_count );
	}

	public static synchronized void
	removeRealTimeTask()
	{
		realtime_task_count--;

		//System.out.println( "RealTimeInfo: tasks=" + realtime_task_count );
	}

	public static boolean
	isRealTimeTaskActive()
	{
		return( realtime_task_count > 0 );
	}

	public static synchronized void
	setProgressiveActive(
		long	bytes_per_sec )
	{
		progressive_bytes_per_sec = bytes_per_sec;

		//System.out.println( "RealTimeInfo: progressive active, " + bytes_per_sec );
	}

	public static synchronized void
	setProgressiveInactive()
	{
		progressive_bytes_per_sec = 0;

		//System.out.println( "RealTimeInfo: progressive inactive" );

	}

		/**
		 * Gives the currently active progressive download's speed if there is one, 0 otherwise
		 * @return
		 */

	public static long
	getProgressiveActiveBytesPerSec()
	{
		return( progressive_bytes_per_sec );
	}
}
