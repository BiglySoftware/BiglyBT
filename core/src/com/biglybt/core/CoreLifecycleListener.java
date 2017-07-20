/*
 * Created on 14-Jul-2004
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

/**
 * @author parg
 *
 */

public interface
CoreLifecycleListener
{
	public void
	componentCreated(
		Core core,
		CoreComponent component );

	public void
	started(
		Core core );

	public void
	stopping(
		Core core );

	public void
	stopped(
		Core core );

		/**
		 * return true if the request has been accepted (and hence the listener will arrange for a stop to occur
		 * @param core
		 * @return
		 */

	public boolean
	stopRequested(
		Core core )

		throws CoreException;

	public boolean
	restartRequested(
		Core core )

		throws CoreException;

		/**
		 * Some listeners must be invoked on the same thread that initiates a core closedown. In particular
		 * the show-alerts-raised-during-closedown logic requires that it is invoked on the com.biglybt.ui.swt thread that
		 * initiated the closedown.
		 * @return
		 */

	public boolean
	syncInvokeRequired();

		/**
		 * hack - original semantics of the "started" event was that all plugins init complete
		 * However, some can take a long time (and even block if attempting to acquire, say, public
		 * IP address and version server is down...) so added this flag to allow listeners to
		 * indicate that they're happy to be "started" before plugin init complete
		 * @return
		 */

	public boolean
	requiresPluginInitCompleteBeforeStartedEvent();
}
