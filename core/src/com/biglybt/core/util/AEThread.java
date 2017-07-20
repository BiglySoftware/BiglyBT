/*
 * Created on 28-Jun-2004
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

import java.util.WeakHashMap;


/**
 * @author parg
 * @deprecated - use AEThread2
 */

public abstract class
AEThread
	extends Thread
{
	private static final WeakHashMap	our_thread_map = new WeakHashMap();

	public
	AEThread(
		String	name )
	{
		super(name);

		setDaemon( false );
	}

	public
	AEThread(
		String	name,
		boolean	daemon )
	{
		super(name);

		setDaemon( daemon );
	}

	@Override
	public void
	run()
	{
		if ( AEThread2.TRACE_TIMES ){

			System.out.println( TimeFormatter.milliStamp() + ": AEThread:start: " + this );
		}

		try{
			/*
			if ( !isDaemon()){

				System.out.println( "non-daemon thread:" + this );
			}
			*/

			runSupport();

		}catch( Throwable e ){

			DebugLight.printStackTrace(e);
		}

		// System.out.println( "Stop: " + this );
	}

	public abstract void
	runSupport();

	public static boolean
	isOurThread(
		Thread	thread )
	{
		if ( thread instanceof AEThread ){

			return( true );
		}

		synchronized( our_thread_map ){

			return( our_thread_map.get( thread ) != null );
		}
	}

	public static void
	setOurThread()
	{
		setOurThread( Thread.currentThread());
	}

	public static void
	setOurThread(
		Thread	thread )
	{
		if ( thread instanceof AEThread || thread instanceof AEThread2.threadWrapper ){

			return;
		}

		synchronized( our_thread_map ){

			our_thread_map.put( thread, "" );
		}
	}
}
