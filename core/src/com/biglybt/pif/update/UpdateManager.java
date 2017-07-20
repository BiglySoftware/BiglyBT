/*
 * Created on 07-May-2004
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

package com.biglybt.pif.update;

/**
 * @author parg
 *
 */

public interface
UpdateManager
{
		/**
		 * All updateable components must register in order to receive update check events
		 * @param component
		 * @param mandatory indicates that this component must successfully complete checking
		 * for any overall update check to complete
		 */

	public void
	registerUpdatableComponent(
		UpdatableComponent		component,
		boolean					mandatory );

		/**
		 * creates an update check instance with currently registered updatable components
		 * Default check type is "UCI_UPDATE"
		 * @return
		 */

	public UpdateCheckInstance
	createUpdateCheckInstance();

		/**
		 * creates an update check instance with currently registered updatable components
		 * @param check_type	see UpdateCheckInstance.UCI_xx
		 * @param name			name of the update instance
		 */

	public UpdateCheckInstance
	createUpdateCheckInstance(
		int			check_type,
		String		name );

		/**
		 * creates an update check instance with no attached updateable components (as opposed
		 * to automatically including all registered
		 * @param check_type	see UpdateCheckInstance.UCI_xx
		 * @param name			name of the update instance
		 * @return
		 */

	public UpdateCheckInstance
	createEmptyUpdateCheckInstance(
		int			check_type,
		String		name );

	public UpdateCheckInstance[]
	getCheckInstances();

		/**
		 * create a stand alone update installer. you will need to restart Azureus for it to
		 * be installed
		 * @return
		 * @throws UpdateException
		 */

	public UpdateInstaller
	createInstaller()

		throws UpdateException;

	boolean hasPendingInstalls();

	public String
	getInstallDir();

	public String
	getUserDir();

		/**
		 * returns the currently declared installers, if any
		 * @return
		 */

	public UpdateInstaller[]
	getInstallers();

	/**
		 * applies any updates and stops or restarts Azureus
		 * @throws UpdateException
		 */

	public void
	applyUpdates(
		boolean	restart_after )

		throws UpdateException;


	public void
	addVerificationListener(
		UpdateManagerVerificationListener	l );

	public void
	removeVerificationListener(
		UpdateManagerVerificationListener	l );

	public void
	addListener(
		UpdateManagerListener	l );

	public void
	removeListener(
		UpdateManagerListener	l );

}
