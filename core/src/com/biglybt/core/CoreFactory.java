/*
 * Created on 13-Jul-2004
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

package com.biglybt.core;

import com.biglybt.core.impl.CoreImpl;

/**
 * @author parg
 *
 */

public class
CoreFactory
{
		/**
		 * Core is a singleton that must be initially created by someone, and initialised
		 * @return
		 * @throws CoreException
		 */

	public static Core
	create()

		throws CoreException
	{
		return( CoreImpl.create());
	}

	/**
	 * Returns whether the core is available.  All features
	 * of the core (such as GlobalManager) may not be available yet.
	 *
	 * @return
	 */
	public static boolean
	isCoreAvailable()
	{
		return( CoreImpl.isCoreAvailable());
	}

	/**
	 * Returns whether the core is running.  All features of the
	 * core (GlobalManager) should be available when the result
	 * is true.
	 *
	 * @return
	 */
	public static boolean
	isCoreRunning() {
		return CoreImpl.isCoreRunning();
	}
		/**
		 * Once created the singleton can be accessed via this method
		 * @return
		 * @throws CoreException
		 */

	public static Core
	getSingleton()

		throws CoreException
	{
		return( CoreImpl.getSingleton());
	}

	/**
	 * Adds a listener that is triggered once the core is running.
	 * <p>
	 * This is in CoreFactory instead of {@link CoreLifecycleListener}
	 * so that listeners can be added before the core instance is
	 * even created.
	 *
	 * @param l Listener to trigger when the core is running.  If
	 *          the core is already running, listener is fired
	 *          immediately
	 */
	public static void
	addCoreRunningListener(CoreRunningListener l) {
		CoreImpl.addCoreRunningListener(l);
	}
}
