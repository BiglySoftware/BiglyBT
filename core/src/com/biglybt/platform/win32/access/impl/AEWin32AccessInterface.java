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

package com.biglybt.platform.win32.access.impl;

/**
 * @author parg
 *
 */

import java.util.List;
import java.util.Map;

import com.biglybt.platform.win32.PlatformManagerImpl;
import com.biglybt.platform.win32.access.AEWin32Access;
import com.biglybt.platform.win32.access.AEWin32AccessException;
import com.biglybt.update.UpdaterUtils;

public class
AEWin32AccessInterface
{
	public static final int	HKEY_CLASSES_ROOT		= AEWin32Access.HKEY_CLASSES_ROOT;
	public static final int	HKEY_CURRENT_CONFIG		= AEWin32Access.HKEY_CURRENT_CONFIG;
	public static final int	HKEY_LOCAL_MACHINE		= AEWin32Access.HKEY_LOCAL_MACHINE;
	public static final int	HKEY_CURRENT_USER		= AEWin32Access.HKEY_CURRENT_USER;

	public static final int	WM_QUERYENDSESSION		= 0x0011;
	public static final int	WM_ENDSESSION           = 0x0016;
	public static final int	WM_POWERBROADCAST       = 0x0218;
	public static final int	PBT_APMQUERYSUSPEND     = 0x0000;
	public static final int	PBT_APMSUSPEND          = 0x0004;
	public static final int	PBT_APMRESUMESUSPEND    = 0x0007;

	public static final long BROADCAST_QUERY_DENY		= 0x424D5144L;


	public static final int  ES_SYSTEM_REQUIRED   	= 0x00000001;
	public static final int  ES_DISPLAY_REQUIRED  	= 0x00000002;
	public static final int  ES_USER_PRESENT      	= 0x00000004;
	public static final int  ES_AWAYMODE_REQUIRED 	= 0x00000040;
	public static final int  ES_CONTINUOUS        	= 0x80000000;

	private static boolean						enabled;
	private static boolean						enabled_set;

	private static AEWin32AccessCallback		cb;

	static{
		try {
			System.loadLibrary( PlatformManagerImpl.DLL_NAME );
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	protected static boolean
	isEnabled(
		boolean		check_if_disabled )
	{
		if ( !check_if_disabled ){

			return( true );
		}

		if ( enabled_set ){

			return( enabled );
		}

		try{
				// protection against something really bad in the dll

			enabled = !UpdaterUtils.disableNativeCode( getVersion());

			if ( !enabled ){

				System.err.println( "Native code has been disabled" );
			}
		}finally{

			enabled_set	= true;
		}

		return( enabled );
	}

	protected static void
	load(
		AEWin32AccessCallback	_callback,
		boolean					_fully_initialise )
	{
		cb = _callback;

		if ( _fully_initialise ){

			try{
				initialise();

			}catch( Throwable e ){

				// get here when running 2400 java against old non-updated aereg.dll (for example)
				// System.out.println( "Old aereg version, please update!" );
			}
		}
	}

	public static long
	callback(
		int		msg,
		int		param1,
		long	param2 )
	{
		if ( cb == null ){

			return( -1 );

		}else{

			return( cb.windowsMessage( msg, param1, param2 ));
		}
	}

	protected static native void
	initialise()

		throws AEWin32AccessExceptionImpl;

	/*
	protected static native void
	destroy()

		throws AEWin32AccessExceptionImpl;
	*/

	protected static native String
	getVersion();

	protected static native String
	readStringValue(
		int		type,		// HKEY type from above
		String	subkey,
		String	value_name )

		throws AEWin32AccessExceptionImpl;

	protected static native void
	writeStringValue(
		int		type,		// HKEY type from above
		String	subkey,
		String	value_name,
		String	value_value )

		throws AEWin32AccessExceptionImpl;

	protected static native int
	readWordValue(
		int		type,		// HKEY type from above
		String	subkey,
		String	value_name )

		throws AEWin32AccessExceptionImpl;

	protected static native void
	writeWordValue(
		int		type,		// HKEY type from above
		String	subkey,
		String	value_name,
		int		value_value )

		throws AEWin32AccessExceptionImpl;


	protected static native void
	deleteKey(
		int		type,
		String	subkey,
		boolean	recursive )

		throws AEWin32AccessExceptionImpl;

	protected static native void
	deleteValue(
		int		type,
		String	subkey,
		String 	value_namae )

		throws AEWin32AccessExceptionImpl;

	public static native void
	createProcess(
		String		command_line,
		boolean		inherit_handles )

		throws AEWin32AccessException;

	public static native void
	moveToRecycleBin(
		String		file_name )

		throws AEWin32AccessException;

	public static native void
	copyPermission(
		String		from_file_name,
		String		to_file_name )

		throws AEWin32AccessException;

	public static native boolean
	testNativeAvailability(
		String	name )

		throws AEWin32AccessException;

	/*
	public static native void
	ping(
		String		address )

		throws AEWin32AccessException;
	*/

	public static native void
	traceRoute(
		int						trace_id,
		int						source_address,
		int						target_address,
		int						pice_mode,
		AEWin32AccessCallback	callback )

		throws AEWin32AccessException;

	public static native int
	shellExecute(
		String 		operation,
		String 		file,
		String 		parameters,
		String 		directory,
		int 		sw_const )

		throws AEWin32AccessExceptionImpl;

	public static native int
	shellExecuteAndWait(
		String 		file,
		String 		parameters )

		throws AEWin32AccessExceptionImpl;


	public static native List
	getAvailableDrives()
		throws AEWin32AccessExceptionImpl;

	public static native Map
	getDriveInfo(char driveLetter)
		throws AEWin32AccessExceptionImpl;

	public static native int
	setThreadExecutionState(int esFLAGS);
}
