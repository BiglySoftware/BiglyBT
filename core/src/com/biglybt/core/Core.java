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

/**
 * @author parg
 *
 */

import java.io.File;
import java.util.List;

import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.instancemanager.ClientInstanceManager;
import com.biglybt.core.internat.LocaleUtil;
import com.biglybt.core.ipfilter.IpFilterManager;
import com.biglybt.core.nat.NATTraverser;
import com.biglybt.core.security.CryptoManager;
import com.biglybt.core.speedmanager.SpeedManager;
import com.biglybt.core.tracker.host.TRHost;
import com.biglybt.pif.PluginManager;
import com.biglybt.pif.PluginManagerDefaults;
import com.biglybt.pif.utils.PowerManagementListener;

public interface
Core
{
	public static final String	CA_QUIT_VUZE	= PluginManager.CA_QUIT_VUZE;
	public static final String	CA_SLEEP		= PluginManager.CA_SLEEP;
	public static final String	CA_HIBERNATE	= PluginManager.CA_HIBERNATE;
	public static final String	CA_SHUTDOWN		= PluginManager.CA_SHUTDOWN;

	public long
	getCreateTime();

	public boolean
	canStart(
		int	max_wait_secs );

	public void
	start()

		throws CoreException;

	public boolean
	isStarted();

	public boolean
	isInitThread();

		/**
		 * stop the core and inform lifecycle listeners of stopping
		 * @throws CoreException
		 */

	public void
	stop()

		throws CoreException;

	public void
	stop(
		CoreOperationTask.ProgressCallback		progress );
	
		/**
		 * ask lifecycle listeners to perform a stop. they may veto this by throwing an exception, or do nothing
		 * if nothing is done then it will be stopped as per "stop" above
		 * @throws CoreException
		 */

	public void
	requestStop()

		throws CoreException;

		/**
		 * checks if restart operation is supported - if not an alert will be raised and an exception thrown
		 * @throws CoreException
		 */

	public void
	checkRestartSupported()

		throws CoreException;

		/**
		 * restart the system
		 */

	public void
	restart();

	public void
	restart(
		CoreOperationTask.ProgressCallback		progress );

		/**
		 * request a restart of the system - currently only available for com.biglybt.ui.swt based systems
		 * @throws CoreException
		 */

	public void
	requestRestart()

		throws CoreException;

	public boolean
	isRestarting();

	public boolean
	isStopping();
	
	public void
	executeCloseAction(
		String		action,			// see CA_ constants above
		String		reason );

	public void
	saveState();

	public LocaleUtil
	getLocaleUtil();

	public GlobalManager
	getGlobalManager()

		throws CoreException;

	public PluginManagerDefaults
	getPluginManagerDefaults()

		throws CoreException;

	public PluginManager
	getPluginManager()

		throws CoreException;

	public TRHost
	getTrackerHost()

		throws CoreException;

	public IpFilterManager
	getIpFilterManager()

		throws CoreException;

	public ClientInstanceManager
	getInstanceManager();

	public SpeedManager
	getSpeedManager();

	public CryptoManager
	getCryptoManager();

	public NATTraverser
	getNATTraverser();

	public File
	getLockFile();

	public void
	executeOperation(
		int							type,
		CoreOperationTask 			task );

	public void
	addOperation(
		CoreOperation		op );
	
	public void
	removeOperation(
		CoreOperation		op );

	public List<CoreOperation>
	getOperations();
	
	public void
	addLifecycleListener(
		CoreLifecycleListener l );

	public void
	removeLifecycleListener(
		CoreLifecycleListener l );

	public void
	addOperationListener(
		CoreOperationListener l );

	public void
	removeOperationListener(
		CoreOperationListener l );

	/**
	 * @param component
	 */
	void triggerLifeCycleComponentCreated(CoreComponent component);

	public void
	addPowerManagementListener(
		PowerManagementListener	listener );

	public void
	removePowerManagementListener(
		PowerManagementListener	listener );
}
