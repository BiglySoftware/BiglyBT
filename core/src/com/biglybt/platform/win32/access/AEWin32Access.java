/*
 * Created on Apr 16, 2004
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

package com.biglybt.platform.win32.access;

import java.io.File;
import java.net.InetAddress;
import java.util.Map;

import com.biglybt.platform.PlatformManagerPingCallback;

/**
 * @author parg
 *
 */


public interface
AEWin32Access
{
	public static final int	HKEY_CLASSES_ROOT		= 1;
	public static final int	HKEY_CURRENT_CONFIG		= 2;
	public static final int	HKEY_LOCAL_MACHINE		= 3;
	public static final int	HKEY_CURRENT_USER		= 4;

	public static final int SW_HIDE = 0;
	public static final int SW_NORMAL = 1;
	public static final int SW_SHOWNORMAL = 1;
	public static final int SW_SHOWMINIMIZED = 2;
	public static final int SW_SHOWMAXIMIZED = 3;
	public static final int SW_MAXIMIZE = 3;
	public static final int SW_SHOWNOACTIVATE = 4;
	public static final int SW_SHOW = 5;
	public static final int SW_MINIMIZE = 6;
	public static final int SW_SHOWMINNOACTIVE = 7;
	public static final int SW_SHOWNA = 8;
	public static final int SW_RESTORE = 9;
	public static final int SW_SHOWDEFAULT = 10;
	public static final int SW_FORCEMINIMIZE = 11;
	public static final int SW_MAX = 11;

	public boolean
	isEnabled();

	public String
	getVersion();

	public String
	readStringValue(
		int		type,		// HKEY type from above
		String	subkey,
		String	value_name )

		throws AEWin32AccessException;

	public void
	writeStringValue(
		int		type,		// HKEY type from above
		String	subkey,
		String	value_name,
		String	value_value )

		throws AEWin32AccessException;

	public int
	readWordValue(
		int		type,		// HKEY type from above
		String	subkey,
		String	value_name )

		throws AEWin32AccessException;

	public void
	writeWordValue(
		int		type,		// HKEY type from above
		String	subkey,
		String	value_name,
		int		value_value )

		throws AEWin32AccessException;


	public void
	deleteKey(
		int		type,
		String	subkey )

		throws AEWin32AccessException;

	public void
	deleteKey(
		int			type,
		String		subkey,
		boolean		recursuve )

		throws AEWin32AccessException;

	public void
	deleteValue(
		int			type,
		String		subkey,
		String		value_name )

		throws AEWin32AccessException;

	public String
	getUserAppData()

		throws AEWin32AccessException;

	public String
	getProgramFilesDir()

		throws AEWin32AccessException;

	public String
	getApplicationInstallDir(
		String	app_name )

		throws AEWin32AccessException;

	public void
	createProcess(
		String		command_line,
		boolean		inherit_handles )

		throws AEWin32AccessException;

	public void
	moveToRecycleBin(
		String	file_name )

		throws AEWin32AccessException;

	public void
    copyFilePermissions(
		String	from_file_name,
		String	to_file_name )

		throws AEWin32AccessException;

	public boolean
	testNativeAvailability(
		String	name )

		throws AEWin32AccessException;

	public void
	traceRoute(
		InetAddress							source_address,
		InetAddress							target_address,
		PlatformManagerPingCallback	callback )

		throws AEWin32AccessException;

	public void
	ping(
		InetAddress							source_address,
		InetAddress							target_address,
		PlatformManagerPingCallback	callback )

		throws AEWin32AccessException;

	public void
	addListener(
		AEWin32AccessListener	listener );

	public void
	removeListener(
		AEWin32AccessListener	listener );

	/**
	 * @return
	 * @throws AEWin32AccessException
	 */
	String getUserDocumentsDir() throws AEWin32AccessException;

	/**
	 * @return
	 * @throws AEWin32AccessException
	 */
	String getUserMusicDir() throws AEWin32AccessException;

	/**
	 * @return
	 * @throws AEWin32AccessException
	 */
	String getUserVideoDir() throws AEWin32AccessException;

	/**
	 * @return
	 * @throws AEWin32AccessException
	 * @deprecated - Shell Folders became User Shell Folders and this doesn't exist...
	 */
	String getCommonAppData() throws AEWin32AccessException;

	public int
	shellExecute(
		String operation,
		String file,
		String parameters,
		String directory,
		int SW_const)

		throws AEWin32AccessException;

	public int
	shellExecuteAndWait(
		String		file,
		String		params )

		throws AEWin32AccessException;

	/**
	 * @return
	 *
	 * @since 4.1.0.5
	 */
	public Map<File, Map>
		getAllDrives();

 	public boolean isUSBDrive(Map driveInfo);

	/**
	 * @return
	 * @throws AEWin32AccessException
	 *
	 * @since 4.5.0.3
	 */
	String getLocalAppData() throws AEWin32AccessException;

	/**
	 * @since 4713
	 * @param state
	 */

	public void
	setThreadExecutionState(
		int		state );
}
