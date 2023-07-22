/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.biglybt.platform.unix;

import java.io.*;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import com.biglybt.core.Core;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemProperties;
import com.biglybt.pif.platform.PlatformManagerException;
import com.biglybt.platform.PlatformManager;
import com.biglybt.platform.PlatformManagerCapabilities;
import com.biglybt.platform.PlatformManagerListener;
import com.biglybt.platform.PlatformManagerPingCallback;

/**
 * @author TuxPaper
 * @created Dec 18, 2006
 *
 */
public class PlatformManagerImpl implements PlatformManager
{
	private static final LogIDs LOGID = LogIDs.CORE;

	protected static PlatformManagerImpl singleton;

	protected static AEMonitor class_mon = new AEMonitor("PlatformManager");

	private final HashSet capabilitySet = new HashSet();

	private static final Object migrate_lock = new Object();

	/**
	 * Gets the platform manager singleton, which was already initialized
	 */
	public static PlatformManagerImpl getSingleton() {
		return singleton;
	}

	static {
		initializeSingleton();
	}

	/**
	 * Instantiates the singleton
	 */
	private static void initializeSingleton() {
		try {
			class_mon.enter();
			singleton = new PlatformManagerImpl();
		} catch (Throwable e) {
			Logger.log(new LogEvent(LOGID, "Failed to initialize platform manager"
					+ " for Unix Compatible OS", e));
		} finally {
			class_mon.exit();
		}
	}

	/**
	 * Creates a new PlatformManager and initializes its capabilities
	 */
	public PlatformManagerImpl() {
		capabilitySet.add(PlatformManagerCapabilities.GetUserDataDirectory);

		if (hasVMOptions()) {
			capabilitySet.add(PlatformManagerCapabilities.AccessExplicitVMOptions);
		}
	}

	private boolean hasVMOptions() {
		return System.getProperty(SystemProperties.SYSPROP_SCRIPT_VERSION, null) != null;
	}

	// @see com.biglybt.platform.PlatformManager#copyFilePermissions(java.lang.String, java.lang.String)
	@Override
	public void copyFilePermissions(String from_file_name, String to_file_name)
			throws PlatformManagerException {
		throw new PlatformManagerException(ERR_UNSUPPORTED);
	}

	// @see com.biglybt.platform.PlatformManager#createProcess(java.lang.String, boolean)
	@Override
	public void createProcess(String command_line, boolean inherit_handles)
			throws PlatformManagerException {
		throw new PlatformManagerException(ERR_UNSUPPORTED);
	}

	// @see com.biglybt.platform.PlatformManager#dispose()
	@Override
	public void dispose() {
	}

	// @see com.biglybt.platform.PlatformManager#getApplicationCommandLine()
	@Override
	public String getApplicationCommandLine() throws PlatformManagerException {
		throw new PlatformManagerException(ERR_UNSUPPORTED);
	}

	// @see com.biglybt.platform.PlatformManager#getPlatformType()
	@Override
	public int getPlatformType() {
		return PT_UNIX;
	}

	// @see com.biglybt.platform.PlatformManager#getUserDataDirectory()
	@Override
	public String getUserDataDirectory()
		throws PlatformManagerException
	{
		String userhome = System.getProperty("user.home");
		String temp_user_path = userhome + SystemProperties.SEP + "."
				+ SystemProperties.getApplicationName().toLowerCase()
				+ SystemProperties.SEP;

		synchronized (migrate_lock) {
			File home = new File(temp_user_path);
			if (!home.exists()) { //might be a fresh install or might be an old non-migrated install
				String old_home_path = userhome + SystemProperties.SEP + "."
						+ SystemProperties.getApplicationName() + SystemProperties.SEP;
				File old_home = new File(old_home_path);
				if (old_home.exists()) { //migrate
					String msg = "Migrating unix user config dir [" + old_home_path
							+ "] ===> [" + temp_user_path + "]";
					System.out.println(msg);
					Logger.log(new LogEvent(LOGID,
							"SystemProperties::getUserPath(Unix): " + msg));
					try {
						old_home.renameTo(home);
					} catch (Throwable t) {
						t.printStackTrace();
						Logger.log(new LogEvent(LOGID, "migration rename failed:", t));
					}
				}
			}
		}

		return temp_user_path;
	}

	@Override
	public String
	getComputerName()
	{
		String	host = System.getenv( "HOST" );

		if ( host != null && host.length() > 0 ){

			return( host );
		}

		return( null );
	}

	// @see com.biglybt.platform.PlatformManager#getVersion()
	@Override
	public String getVersion() throws PlatformManagerException {
		throw new PlatformManagerException(ERR_UNSUPPORTED);
	}

	// @see com.biglybt.platform.PlatformManager#hasCapability(com.biglybt.platform.PlatformManagerCapabilities)
	@Override
	public boolean hasCapability(PlatformManagerCapabilities capability) {
		return capabilitySet.contains(capability);
	}

	// @see com.biglybt.platform.PlatformManager#isApplicationRegistered()
	@Override
	public boolean isApplicationRegistered() throws PlatformManagerException {
		throw new PlatformManagerException(ERR_UNSUPPORTED);
	}

	// @see com.biglybt.platform.PlatformManager#performRecoverableFileDelete(java.lang.String)
	@Override
	public void performRecoverableFileDelete(String file_name)
			throws PlatformManagerException {
		throw new PlatformManagerException(ERR_UNSUPPORTED);
	}

	// @see com.biglybt.platform.PlatformManager#ping(java.net.InetAddress, java.net.InetAddress, com.biglybt.platform.PlatformManagerPingCallback)
	@Override
	public void ping(InetAddress interface_address, InetAddress target,
	                 PlatformManagerPingCallback callback) throws PlatformManagerException {
		throw new PlatformManagerException(ERR_UNSUPPORTED);
	}

	@Override
	public int
	getMaxOpenFiles()

		throws PlatformManagerException
	{
	    throw new PlatformManagerException(ERR_UNSUPPORTED);
	}

	// @see com.biglybt.platform.PlatformManager#registerApplication()
	@Override
	public void registerApplication() throws PlatformManagerException {
		throw new PlatformManagerException(ERR_UNSUPPORTED);
	}

	// @see com.biglybt.platform.PlatformManager#addListener(com.biglybt.platform.PlatformManagerListener)
	@Override
	public void addListener(PlatformManagerListener listener) {
		// No Listener Functionality
	}

	// @see com.biglybt.platform.PlatformManager#removeListener(com.biglybt.platform.PlatformManagerListener)
	@Override
	public void removeListener(PlatformManagerListener listener) {
		// No Listener Functionality
	}

	@Override
	public File
	getVMOptionFile()

		throws PlatformManagerException
	{
		checkCapability( PlatformManagerCapabilities.AccessExplicitVMOptions );

		File local_options = new File( getLocation(LOC_USER_DATA), "java.vmoptions" );

		if ( !local_options.exists()){

			try{
				local_options.createNewFile();

			}catch( Throwable e ){
			}
		}

		return( local_options );
	}

	private void
	checkCapability(
			PlatformManagerCapabilities capability )

			throws PlatformManagerException
	{
		if ( !hasCapability(capability)){

			throw( new PlatformManagerException( "Capability " + capability + " not supported" ));
		}
	}


	@Override
	public String[]
	getExplicitVMOptions()

	 	throws PlatformManagerException
	{
		checkCapability( PlatformManagerCapabilities.AccessExplicitVMOptions );


		File local_options = getVMOptionFile();

		try{

			List<String> list = new ArrayList<>();

			if ( local_options.exists()){

				LineNumberReader lnr = new LineNumberReader( new InputStreamReader( new FileInputStream( local_options ), "UTF-8" ));

				try{
					while( true ){

						String	line = lnr.readLine();

						if ( line == null ){

							break;
						}

						line = line.trim();

						if ( line.length() > 0 ){

							list.add( line );
						}
					}

				}finally{

					lnr.close();
				}
			}

			return( list.toArray( new String[list.size()]));

		}catch( Throwable e ){

			throw( new PlatformManagerException( MessageText.getString( "platform.jvmopt.accesserror", new String[]{ Debug.getNestedExceptionMessage(e) } )));
		}
	}

	@Override
	public void
	setExplicitVMOptions(
		String[]		options )

		throws PlatformManagerException
	{
		checkCapability( PlatformManagerCapabilities.AccessExplicitVMOptions );

		File local_options = getVMOptionFile();

		try{
			if ( local_options.exists()){

				File backup = new File( local_options.getParentFile(), local_options.getName() + ".bak" );

				if ( backup.exists()){

					backup.delete();
				}

				if ( !local_options.renameTo( backup )){

					throw( new Exception( "Failed to move " + local_options + " to " + backup ));
				}

				boolean	ok = false;

				try{

					PrintWriter pw = new PrintWriter( new OutputStreamWriter( new FileOutputStream( local_options ), "UTF-8" ));

					try{
						for ( String option: options ){

							pw.println( option );
						}

						ok = true;

					}finally{

						pw.close();
					}
				}finally{

					if ( !ok ){

						local_options.delete();

						backup.renameTo( local_options );
					}
				}
			}
		}catch( Throwable e ){

			throw( new PlatformManagerException( MessageText.getString( "platform.jvmopt.accesserror", new String[]{ Debug.getNestedExceptionMessage(e) } )));
		}
	}

  	@Override
	  public boolean
  	getRunAtLogin()

  		throws PlatformManagerException
  	{
  		throw new PlatformManagerException(ERR_UNSUPPORTED);
  	}

  	@Override
	  public void
  	setRunAtLogin(
  		boolean run )

  		throws PlatformManagerException
  	{
  		throw new PlatformManagerException(ERR_UNSUPPORTED);
   	}

	@Override
	public void
	startup(
		Core core )

		throws PlatformManagerException
	{
	}

	@Override
	public int
	getShutdownTypes()
	{
		return( 0 );
	}

	@Override
	public void
	shutdown(
		int			type )

		throws PlatformManagerException
	{
		 throw new PlatformManagerException("Unsupported capability called on platform manager");
	}

	@Override
	public void
	setPreventComputerSleep(
		boolean		b )

		throws PlatformManagerException
	{
		 throw new PlatformManagerException("Unsupported capability called on platform manager");
	}

	@Override
	public boolean
	getPreventComputerSleep()
	{
		return( false );
	}

	// @see com.biglybt.platform.PlatformManager#setTCPTOSEnabled(boolean)
	@Override
	public void setTCPTOSEnabled(boolean enabled) throws PlatformManagerException {
		throw new PlatformManagerException(ERR_UNSUPPORTED);
	}

	// @see com.biglybt.platform.PlatformManager#testNativeAvailability(java.lang.String)
	@Override
	public boolean testNativeAvailability(String name)
			throws PlatformManagerException {
		throw new PlatformManagerException(ERR_UNSUPPORTED);
	}

	// @see com.biglybt.platform.PlatformManager#traceRoute(java.net.InetAddress, java.net.InetAddress, com.biglybt.platform.PlatformManagerPingCallback)
	@Override
	public void traceRoute(InetAddress interface_address, InetAddress target,
	                       PlatformManagerPingCallback callback) throws PlatformManagerException {
		throw new PlatformManagerException(ERR_UNSUPPORTED);
	}

	// @see com.biglybt.pif.platform.PlatformManager#getLocation(long)
	@Override
	public File getLocation(long location_id) throws PlatformManagerException {
		switch ((int)location_id) {
			case LOC_USER_DATA:
				return( new File( getUserDataDirectory() ));

			case LOC_DOCUMENTS:
				return new File(System.getProperty("user.home"));

			case LOC_DOWNLOADS:
				return new File(System.getProperty("user.home"), "Downloads");

			case LOC_MUSIC:

			case LOC_VIDEO:

			default:
				return( null );
		}
	}

	// @see com.biglybt.pif.platform.PlatformManager#isAdditionalFileTypeRegistered(java.lang.String, java.lang.String)
	@Override
	public boolean isAdditionalFileTypeRegistered(String name, String type)
			throws PlatformManagerException {
		throw new PlatformManagerException(ERR_UNSUPPORTED);
	}

	// @see com.biglybt.pif.platform.PlatformManager#registerAdditionalFileType(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
	@Override
	public void registerAdditionalFileType(String name, String description,
	                                       String type, String content_type) throws PlatformManagerException {
		throw new PlatformManagerException(ERR_UNSUPPORTED);
	}

	// @see com.biglybt.pif.platform.PlatformManager#showFile(java.lang.String)
	@Override
	public void showFile(String file_name) throws PlatformManagerException {
		throw new PlatformManagerException(ERR_UNSUPPORTED);
	}

	// @see com.biglybt.pif.platform.PlatformManager#unregisterAdditionalFileType(java.lang.String, java.lang.String)
	@Override
	public void unregisterAdditionalFileType(String name, String type)
			throws PlatformManagerException {
		throw new PlatformManagerException(ERR_UNSUPPORTED);
	}

	// @see com.biglybt.platform.PlatformManager#getAzComputerID()
	public String getAzComputerID() throws PlatformManagerException {
    throw new PlatformManagerException(ERR_UNSUPPORTED);
	}

	@Override
	public void requestUserAttention(int type, Object data) throws PlatformManagerException {
		throw new PlatformManagerException("Unsupported capability called on platform manager");
	}

	@Override
	public Class<?>
	loadClass(
		ClassLoader	loader,
		String		class_name )

		throws PlatformManagerException
	{
		try{
			return( loader.loadClass( class_name ));

		}catch( Throwable e ){

			throw( new PlatformManagerException( "load of '" + class_name + "' failed", e ));
		}
	}
}
