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

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.platform.PlatformManagerPingCallback;
import com.biglybt.platform.win32.access.AEWin32Access;
import com.biglybt.platform.win32.access.AEWin32AccessException;
import com.biglybt.platform.win32.access.AEWin32AccessListener;
import com.biglybt.platform.win32.access.AEWin32Manager;
import com.biglybt.util.MapUtils;

// don't use any core stuff in here as we need this access stub to be able to run in isolation

public class
AEWin32AccessImpl
	implements AEWin32Access, AEWin32AccessCallback
{
	protected static AEWin32AccessImpl	singleton;

	public static synchronized AEWin32Access
	getSingleton(
		boolean	fully_initialise )
	{
		if ( singleton == null ){

			singleton = new AEWin32AccessImpl(fully_initialise);
		}

		return( singleton );
	}

	private boolean	fully_initialise;

	private int	trace_id_next = new Random().nextInt();

	private List	listeners = new ArrayList();

	protected
	AEWin32AccessImpl(
		boolean		_fully_initialise )
	{
		fully_initialise	= _fully_initialise;

		if ( isEnabled()){

			AEWin32AccessInterface.load( this, fully_initialise );
		}
	}

	@Override
	public boolean
	isEnabled()
	{
		return( AEWin32AccessInterface.isEnabled( fully_initialise ));
	}

	@Override
	public long
	windowsMessage(
		int		msg,
		int		param1,
		long	param2 )
	{
		int	type	= -1;

		if ( msg == AEWin32AccessInterface.WM_ENDSESSION ){

			type = AEWin32AccessListener.ET_SHUTDOWN;

		}else if ( msg == AEWin32AccessInterface.WM_POWERBROADCAST ){

			if ( param1 == AEWin32AccessInterface.PBT_APMQUERYSUSPEND ){

				type = AEWin32AccessListener.ET_SUSPEND;

			}else if ( param1 == AEWin32AccessInterface.PBT_APMRESUMESUSPEND ){

				type = AEWin32AccessListener.ET_RESUME;
			}
		}

		int	result = -1;

		if ( type != -1 ){

			for (int i=0;i<listeners.size();i++){

				try{
					int temp = ((AEWin32AccessListener)listeners.get(i)).eventOccurred( type );

					if ( temp != result ){

						if ( result != -1 ){

							Debug.out( "Incompatible results received: " + result + "/" + temp );
						}

						result 	= temp;
					}
				}catch( Throwable e ){

					e.printStackTrace();
				}
			}
		}

		if ( result == AEWin32AccessListener.RT_SUSPEND_DENY ){

			if (( param2 & 0x0001 ) != 0 ){

				return( AEWin32AccessInterface.BROADCAST_QUERY_DENY );

			}else{

				Debug.out( "Ignoring suspend deny request as not permitted" );
			}
		}

		return( result );
	}

	@Override
	public long
	generalMessage(
		String	str )
	{
		return( 0 );
	}

	@Override
	public String
	getVersion()
	{
		return( AEWin32AccessInterface.getVersion());
	}

	@Override
	public String
	readStringValue(
		int		type,
		String	subkey,
		String	value_name )

		throws AEWin32AccessException
	{
		return( AEWin32AccessInterface.readStringValue( type, subkey, value_name ));
	}

	@Override
	public void
	writeStringValue(
		int		type,
		String	subkey,
		String	value_name,
		String	value_value )

		throws AEWin32AccessException
	{
		AEWin32AccessInterface.writeStringValue( type, subkey, value_name, value_value );
	}


	@Override
	public int
	readWordValue(
		int		type,
		String	subkey,
		String	value_name )

		throws AEWin32AccessException
	{
		return( AEWin32AccessInterface.readWordValue( type, subkey, value_name ));
	}

	@Override
	public void
	writeWordValue(
		int		type,
		String	subkey,
		String	value_name,
		int		value_value )

		throws AEWin32AccessException
	{
		AEWin32AccessInterface.writeWordValue( type, subkey, value_name, value_value );
	}


	@Override
	public void
	deleteKey(
		int		type,
		String	subkey )

		throws AEWin32AccessException
	{
		deleteKey( type, subkey, false );
	}

	@Override
	public void
	deleteKey(
		int		type,
		String	subkey,
		boolean	recursive )

		throws AEWin32AccessException
	{
		AEWin32AccessInterface.deleteKey( type, subkey, recursive );
	}

	@Override
	public void
	deleteValue(
		int			type,
		String		subkey,
		String		value_name )

		throws AEWin32AccessException
	{
		AEWin32AccessInterface.deleteValue( type, subkey, value_name );
	}

	private String
	getShellFolders()
	{
		if ( Constants.isWindows7OrHigher ){
			
			return( "software\\microsoft\\windows\\currentversion\\explorer\\user shell folders" );

		}else{
		
			return( "software\\microsoft\\windows\\currentversion\\explorer\\shell folders" );
		}
	}
	@Override
	public String
	getUserAppData()

		throws AEWin32AccessException
	{
		String	app_data_key	= getShellFolders();
		String	app_data_name 	= "appdata";

		return(	readStringValue(
					HKEY_CURRENT_USER,
					app_data_key,
					app_data_name ));

	}

	@Override
	public String
	getCommonAppData()

		throws AEWin32AccessException
	{
		throw( new AEWin32AccessException( "deprecated"));
	}

	@Override
	public String
	getLocalAppData()

		throws AEWin32AccessException
	{
		String	app_data_key	= getShellFolders();
		String	app_data_name 	= "Local AppData";

		return(	readStringValue(
					HKEY_CURRENT_USER,
					app_data_key,
					app_data_name ));

	}

	@Override
	public String
	getUserDocumentsDir()

		throws AEWin32AccessException
	{
		String	app_data_key	= getShellFolders();
		String	app_data_name 	= "personal";

		return(	readStringValue(
					HKEY_CURRENT_USER,
					app_data_key,
					app_data_name ));

	}

	@Override
	public String
	getUserMusicDir()

		throws AEWin32AccessException
	{
		String	app_data_key	= getShellFolders();
		String	app_data_name 	= "my music";

		try {
  		return(	readStringValue(
  					HKEY_CURRENT_USER,
  					app_data_key,
  					app_data_name ));
		} catch (AEWin32AccessException e) {
			// Win98 doesn't have it
  		String s = getUserDocumentsDir();
  		if (s != null) {
  			s += "\\My Music";
  		}
  		return s;
		}

	}


	@Override
	public String
	getUserVideoDir()

		throws AEWin32AccessException
	{
		String	app_data_key	= getShellFolders();
		String	app_data_name 	= "my video";

		try {
  		return(	readStringValue(
  					HKEY_CURRENT_USER,
  					app_data_key,
  					app_data_name ));
		} catch (AEWin32AccessException e) {
			// Win98 doesn't have it
  		String s = getUserDocumentsDir();
  		if (s != null) {
  			s += "\\My Video";
  		}
  		return s;
		}

	}

	@Override
	public String
	getProgramFilesDir()

		throws AEWin32AccessException
	{
		String	app_data_key	= "software\\microsoft\\windows\\currentversion";
		String	app_data_name 	= "ProgramFilesDir";

		return(	readStringValue(
					HKEY_LOCAL_MACHINE,
					app_data_key,
					app_data_name ));
	}


	@Override
	public String
	getApplicationInstallDir(
		String	app_name )

		throws AEWin32AccessException
	{
		String	res = "";

		try{
			res = readStringValue(
					HKEY_CURRENT_USER,
					"software\\" + app_name,
					null );

		}catch( AEWin32AccessException e ){

			res = readStringValue(
					HKEY_LOCAL_MACHINE,
					"software\\" + app_name,
					null );

		}

		return( res );
	}

	@Override
	public void
	createProcess(
		String		command_line,
		boolean		inherit_handles )

		throws AEWin32AccessException
	{
		AEWin32AccessInterface.createProcess( command_line, inherit_handles );
	}

	@Override
	public void
	moveToRecycleBin(
		String	file_name )

		throws AEWin32AccessException
	{
		AEWin32AccessInterface.moveToRecycleBin( file_name );
	}

	@Override
	public void
    copyFilePermissions(
		String	from_file_name,
		String	to_file_name )

		throws AEWin32AccessException
	{
		AEWin32AccessInterface.copyPermission( from_file_name, to_file_name );
	}

	@Override
	public boolean
	testNativeAvailability(
		String	name )

		throws AEWin32AccessException
	{
		return( AEWin32AccessInterface.testNativeAvailability( name ));
	}

	@Override
	public int shellExecute(String operation, String file, String parameters,
	                        String directory, int SW_const) throws AEWin32AccessException {
		return AEWin32AccessInterface.shellExecute(operation, file, parameters,
				directory, SW_const);
	}

	@Override
	public int
	shellExecuteAndWait(
		String file,
		String params )

		throws AEWin32AccessException
	{
		return( AEWin32AccessInterface.shellExecuteAndWait(file, params ));
	}

	@Override
	public void
	traceRoute(
		InetAddress								source_address,
		InetAddress								target_address,
		final PlatformManagerPingCallback		callback )

		throws AEWin32AccessException
	{
		traceRoute( source_address, target_address, false, callback );
	}

	@Override
	public void
	ping(
		InetAddress								source_address,
		InetAddress								target_address,
		final PlatformManagerPingCallback		callback )

		throws AEWin32AccessException
	{
		if ( Constants.compareVersions( getVersion(), "1.15" ) < 0 ){

			throw( new AEWin32AccessException( "Sorry, ping is broken in versions < 1.15" ));
		}

		traceRoute( source_address, target_address, true, callback );
	}

	protected void
	traceRoute(
		InetAddress						source_address,
		InetAddress						target_address,
		boolean							ping_mode,
		PlatformManagerPingCallback		callback )

		throws AEWin32AccessException
	{
		int	trace_id;

		synchronized( this ){

			trace_id = trace_id_next++;
		}

		AEWin32AccessCallback	cb = new traceRouteCallback( ping_mode, callback );

		AEWin32AccessInterface.traceRoute(
				trace_id,
				addressToInt( source_address ),
				addressToInt( target_address ),
				ping_mode?1:0,
				cb );
	}

	private int
	addressToInt(
		InetAddress	address )
	{
		byte[]	bytes = address.getAddress();

		int	resp = (bytes[0]<<24)&0xff000000 | (bytes[1] << 16)&0x00ff0000 | (bytes[2] << 8)&0x0000ff00 | bytes[3]&0x000000ff;

		return( resp );
	}

	private InetAddress
	intToAddress(
		int		address )
	{
		byte[]	bytes = { (byte)(address>>24), (byte)(address>>16),(byte)(address>>8),(byte)address };

		try{
			InetAddress	res = InetAddress.getByAddress(bytes);

			return( res );

		}catch( UnknownHostException e ){

			return( null );
		}
	}

	@Override
	public void
    addListener(
    	AEWin32AccessListener		listener )
    {
    	listeners.add( listener );
    }

    @Override
    public void
    removeListener(
    	AEWin32AccessListener		listener )
    {
    	listeners.remove( listener );
    }

    protected class
    traceRouteCallback
    	implements AEWin32AccessCallback
	{
    	private boolean							ping_mode;
    	private PlatformManagerPingCallback		cb;

    	protected
    	traceRouteCallback(
    		boolean							_ping_mode,
    		PlatformManagerPingCallback		_cb )
    	{
    		ping_mode	= _ping_mode;
    		cb			= _cb;
    	}

		@Override
		public long
		windowsMessage(
			int		msg,
			int		param1,
			long	param2 )
		{
			return(0);
		}

		@Override
		public long
		generalMessage(
			String	msg )
		{
			StringTokenizer	tok = new StringTokenizer( msg, "," );

			int	ttl 	= Integer.parseInt( tok.nextToken().trim());
			int	time 	= -1;

			InetAddress	address;

			if ( tok.hasMoreTokens()){

				int	i_addr = Integer.parseInt( tok.nextToken().trim());

				address = intToAddress( i_addr );

				time = Integer.parseInt( tok.nextToken().trim());

				//boolean is_udp = Integer.parseInt( tok.nextToken().trim()) == 1;

				// System.out.println( "udp = " + is_udp );
			}else{

				address = null;
			}

			return( cb.reportNode( ping_mode?-1:ttl, address, time )?1:0 );
		}
	}

    @Override
    public Map<File, Map> getAllDrives() {

    	// gah, we sometimes get crashes here

    	String	state_key = "awein32.getalldrives.state.2";

    	int state = COConfigurationManager.getIntParameter( state_key, 0 );

    	if ( state == 1 ){

    		Debug.out( "Not enumerating system drives as it crashed last time we tried" );

    		return(new HashMap<>());
    	}

    	try{
    		COConfigurationManager.setParameter( state_key, 1 );

    		COConfigurationManager.save();

	    	Map<File, Map> mapDrives = new HashMap<>();
	    	try {
					List availableDrives = AEWin32AccessInterface.getAvailableDrives();
					if (availableDrives != null) {
						for (Object object : availableDrives) {
							File f = (File) object;
							Map driveInfo = AEWin32AccessInterface.getDriveInfo(f.getPath().charAt(0));
							boolean isWritableUSB = AEWin32Manager.getAccessor(false).isUSBDrive(driveInfo);
							driveInfo.put("isWritableUSB", isWritableUSB);
							mapDrives.put(f, driveInfo);
						}
					}

					return mapDrives;
	    	} catch (UnsatisfiedLinkError ue) {
	    		Debug.outNoStack("Old aereg.dll");
				} catch (Throwable e) {
					Debug.out(e);
				}
				return Collections.emptyMap();
    	}finally{

    		COConfigurationManager.setParameter( state_key, 2 );

    		COConfigurationManager.setDirty();
    	}
    }

  @Override
  public boolean isUSBDrive(Map driveInfo) {
  	if (driveInfo == null) {
  		return false;
  	}
  	boolean removeable = MapUtils.getMapBoolean(driveInfo, "Removable", false);
  	// values GetDriveType: {UNKNOWN, NO_ROOT_DIR, REMOVABLE, FIXED, REMOTE, CDROM, RAMDISK}
  	long driveType = MapUtils.getMapLong(driveInfo, "DriveType", 0);
  	// STORAGE_BUS_TYPE: http://msdn.microsoft.com/en-us/library/aa363465%28VS.85%29.aspx
  	long busType = MapUtils.getMapLong(driveInfo, "BusType", 0);
  	// MEDIA_TYPE: http://msdn.microsoft.com/en-us/library/aa365231%28VS.85%29.aspx
  	long mediaType = MapUtils.getMapLong(driveInfo, "MediaType", -1);

  	if (removeable && driveType == 2 && busType == 7
  			&& (mediaType == 11 || mediaType == -1)) {
  		return true;
  	}

  	return false;
  }

	@Override
	public void
	setThreadExecutionState(
		int		state )
	{
		AEWin32AccessInterface.setThreadExecutionState( state );
	}
}
