/*
 * Created on 18-Apr-2004
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

package com.biglybt.platform.win32;

/**
 * @author parg
 *
 */

import java.io.*;
import java.net.InetAddress;
import java.util.*;

import com.biglybt.core.Core;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.platform.PlatformManagerException;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.UIManagerEvent;
import com.biglybt.pif.update.*;
import com.biglybt.pif.utils.StaticUtilities;
import com.biglybt.platform.PlatformManager;
import com.biglybt.platform.PlatformManagerCapabilities;
import com.biglybt.platform.PlatformManagerListener;
import com.biglybt.platform.PlatformManagerPingCallback;
import com.biglybt.platform.win32.access.AEWin32Access;
import com.biglybt.platform.win32.access.AEWin32AccessListener;
import com.biglybt.platform.win32.access.AEWin32Manager;
import com.biglybt.platform.win32.access.impl.AEWin32AccessInterface;

public class
PlatformManagerImpl
	implements PlatformManager, AEWin32AccessListener, AEDiagnosticsEvidenceGenerator
{
	public static final int			RT_NONE		= 0;
	public static final int			RT_AZ 		= 1;
	public static final int			RT_OTHER 	= 2;

	public static String					DLL_NAME = "aereg";

	public static final String				BIGLYBY_ASSOC	= "BiglyBT";
	public static final String				OLD_MAIN_ASS0C	= "BitTorrent";

	private static boolean					initialising;
	private static boolean					init_tried;

	private static PlatformManagerImpl		singleton;
	private static AEMonitor				class_mon	= new AEMonitor( "PlatformManager");

	private final Set<PlatformManagerCapabilities> capabilitySet = new HashSet<>();

	private List	listeners = new ArrayList();

	static {
		if (System.getProperty("aereg", null) != null) {
			DLL_NAME = System.getProperty("aereg");
		} else if (System.getProperty("os.arch", "").contains("64")) {
			DLL_NAME += "64";
		}
	}

	public static PlatformManagerImpl
	getSingleton()

		throws PlatformManagerException
	{
		try{
			class_mon.enter();

			if ( singleton != null ){

				return( singleton );
			}

			try{
				if ( initialising ){

					System.err.println( "PlatformManager: recursive entry during initialisation" );
				}

				initialising	= true;

				if ( !init_tried ){

					init_tried	= true;

					try{
						singleton	= new PlatformManagerImpl();

							// gotta separate this so that a recursive call due to config access during
							// patching finds the singleton

						singleton.applyPatches();

					}catch( PlatformManagerException e ){

						throw( e );

					}catch( Throwable e ){

						if ( e instanceof PlatformManagerException ){

							throw((PlatformManagerException)e);
						}

						throw( new PlatformManagerException( "Win32Platform: failed to initialise", e ));
					}
				}
			}finally{

				initialising	= false;
			}

			return( singleton );

		}finally{

			class_mon.exit();
		}
	}

	private final AEWin32Access		access;

	private final String			app_name;
	private final String			app_exe_name;
	private File					az_exe;
	private boolean					az_exe_checked;

	private boolean					prevent_computer_sleep;
	private AEThread2				prevent_sleep_thread;

	protected
	PlatformManagerImpl()

		throws PlatformManagerException
	{
		access	= AEWin32Manager.getAccessor( true );

		access.addListener( this );

		app_name		= SystemProperties.getApplicationName();

		String mod_name = System.getProperty( "exe4j.moduleName", null );

		String exe_name = null;

		if ( mod_name != null && new File( mod_name ).exists() && mod_name.toLowerCase().endsWith( ".exe" )){

			int	pos = mod_name.lastIndexOf( File.separator );

			if ( pos != -1 ){

				exe_name = mod_name.substring( pos+1 );
			}
		}

		if ( exe_name == null ){

			exe_name	= app_name + ".exe";
		}

		app_exe_name = exe_name;

        initializeCapabilities();
	}

    private void
    initializeCapabilities()
    {
    	if ( access.isEnabled()){

	        capabilitySet.add(PlatformManagerCapabilities.CreateCommandLineProcess);
	        capabilitySet.add(PlatformManagerCapabilities.GetUserDataDirectory);
	        capabilitySet.add(PlatformManagerCapabilities.RecoverableFileDelete);
	        capabilitySet.add(PlatformManagerCapabilities.RegisterFileAssociations);
	        capabilitySet.add(PlatformManagerCapabilities.ShowFileInBrowser);
	        capabilitySet.add(PlatformManagerCapabilities.GetVersion);
	        capabilitySet.add(PlatformManagerCapabilities.SetTCPTOSEnabled);
	        capabilitySet.add(PlatformManagerCapabilities.ComputerIDAvailability);

	        String plugin_version = access.getVersion();

	        if ( Constants.compareVersions( plugin_version, "1.11" ) >= 0 &&
	        		!Constants.isWindows9598ME ){

	            capabilitySet.add(PlatformManagerCapabilities.CopyFilePermissions);

	        }

	        if ( Constants.compareVersions( plugin_version, "1.12" ) >= 0 ){

	            capabilitySet.add(PlatformManagerCapabilities.TestNativeAvailability);
	        }

	        if ( Constants.compareVersions( plugin_version, "1.14" ) >= 0 ){

	            capabilitySet.add(PlatformManagerCapabilities.TraceRouteAvailability);
	        }

	        if ( Constants.compareVersions( plugin_version, "1.15" ) >= 0 ){

	            capabilitySet.add(PlatformManagerCapabilities.PingAvailability);
	        }

	        try{
	        	getUserDataDirectory();

	        		// if we can access the user dir then we're good to access vmoptions

	        	if ( Constants.compareVersions( plugin_version, "1.19" ) >= 0 ){

	        		capabilitySet.add(PlatformManagerCapabilities.AccessExplicitVMOptions );
	        	}
	        }catch( Throwable ignore ){
	        }

	        capabilitySet.add(PlatformManagerCapabilities.RunAtLogin);
	        capabilitySet.add(PlatformManagerCapabilities.PreventComputerSleep);
    	}else{

    			// disabled -> only available capability is that to get the version
    			// therefore allowing upgrade

	        capabilitySet.add(PlatformManagerCapabilities.GetVersion);
    	}
    }

    protected void
	applyPatches()
	{
		try{
			File	exe_loc = getApplicationEXELocation();

			String	az_exe_string = exe_loc.getAbsolutePath();

			//int	icon_index = getIconIndex();

			String	current =
				access.readStringValue(
					AEWin32Access.HKEY_CLASSES_ROOT,
						BIGLYBY_ASSOC + "\\DefaultIcon",
					"" );

			//System.out.println( "current = " + current );

			String	target = az_exe_string + "," + getIconIndex();

			//System.out.println( "target = " + target );

				// only patch if Azureus.exe in there

			if (current.contains(app_exe_name) && !current.equals(target)){

				writeStringToHKCRandHKCU(
						BIGLYBY_ASSOC + "\\DefaultIcon",
						"",
						target );
			}
		}catch( Throwable e ){

			//e.printStackTrace();
		}

			// one off fix of permissions in app dir

		if ( 	hasCapability( PlatformManagerCapabilities.CopyFilePermissions ) &&
				!COConfigurationManager.getBooleanParameter( "platform.win32.permfixdone2", false )){

			try{

				String	str = SystemProperties.getApplicationPath();

				if ( str.endsWith(File.separator)){

					str = str.substring(0,str.length()-1);
				}

				fixPermissions( new File( str ), new File( str ));

			}catch( Throwable e ){

			}finally{

				COConfigurationManager.setParameter( "platform.win32.permfixdone2", true );
			}
		}
	}

    protected void
    fixPermissions(
    	File		parent,
    	File		dir )

    	throws PlatformManagerException
    {
    	File[]	files = dir.listFiles();

    	if ( files == null ){

    		return;
    	}

	    for (File file : files) {

		    if (file.isFile()) {

			    copyFilePermissions(parent.getAbsolutePath(), file.getAbsolutePath());
		    }
	    }
    }

	protected int
	getIconIndex()

		throws PlatformManagerException
	{
		/*
		File	exe_loc = getAureusEXELocation();

		long	size = exe_loc.length();

		boolean	old_exe = size < 250000;

		return( old_exe?0:1);
		*/

		// weird, seems like it should be 0 for old and new

		return( 0 );
	}

	@Override
	public String
	getVersion()
	{
		return( access.getVersion());
	}

	protected File
	getApplicationEXELocation()
		throws PlatformManagerException
	{
		if ( az_exe == null ){

			try{

				String az_home;

				// Try the app dir first, because we may not be using the one in the registry
				az_home = SystemProperties.getApplicationPath();

				az_exe = new File(az_home + File.separator + app_exe_name).getAbsoluteFile();

				if (!az_exe.exists()) {
					try {
						az_home = access.getApplicationInstallDir( app_name );

						az_exe = new File(az_home + File.separator + app_exe_name).getAbsoluteFile();

						if (!az_exe.exists()) {

							throw (new PlatformManagerException(app_exe_name
									+ " not found in " + az_home + ", please re-install"));
						}
					} catch (Throwable e) {
					}
				}

				if ( !az_exe.exists()){

					String	msg = app_exe_name + " not found in " + az_home + " - can't check file associations. Please re-install " + app_name;

					az_exe = null;

					if (!az_exe_checked){

						Logger.log(new LogAlert(LogAlert.UNREPEATABLE, LogAlert.AT_WARNING,
								msg));
					}

					throw( new PlatformManagerException( msg ));
				}
			}finally{

				az_exe_checked	= true;
			}
		}

		return( az_exe );
	}

	@Override
	public int
	getPlatformType()
	{
		return( PT_WINDOWS );
	}

	@Override
	public String
	getUserDataDirectory()

		throws PlatformManagerException
	{
		try{
			return access.getUserAppData() + SystemProperties.SEP + app_name + SystemProperties.SEP;

		} catch (Throwable e) {

			String temp_user_path = SystemProperties.getEnvironmentalVariable(
					"APPDATA");

			if (temp_user_path == null || temp_user_path.length() == 0) {
				String userhome = System.getProperty("user.home");
				temp_user_path = userhome + SystemProperties.SEP + "Application Data";
			}

			temp_user_path = temp_user_path + SystemProperties.SEP + app_name
					+ SystemProperties.SEP;

			return temp_user_path;
		}
	}

	@Override
	public String
	getComputerName()
	{
		String	host = System.getenv( "COMPUTERNAME" );

		if ( host != null && host.length() > 0 ){

			return( host );
		}

		return( null );
	}

	@Override
	public File
	getLocation(
		long	location_id )

		throws PlatformManagerException
	{
	    if ( location_id == LOC_USER_DATA ){

	    	return(new File(getUserDataDirectory()));

	    }else if ( location_id == LOC_MUSIC ){

	    	try{

		    	return(new File(access.getUserMusicDir()));

	    	}catch( Throwable e ){

				throw( new PlatformManagerException( "Failed to read registry details", e ));
	    	}
	    } else if (location_id == LOC_DOCUMENTS) {
	    	try{

		    	return(new File(access.getUserDocumentsDir()));

	    	}catch( Throwable e ){

				throw( new PlatformManagerException( "Failed to read registry details", e ));
	    	}
	    } else if (location_id == LOC_DOWNLOADS) {

	    	return new File(getLocation(LOC_DOCUMENTS), "Downloads");

	    } else if (location_id == LOC_VIDEO) {
	    	try{

		    	return(new File(access.getUserVideoDir()));

	    	}catch( Throwable e ){

				throw( new PlatformManagerException( "Failed to read registry details", e ));
	    	}
	    }else{

	    	return( null );
	    }
	}

	private String
	getJVMOptionRedirect()
	{
		return( "-include-options ${APPDATA}\\" + SystemProperties.getApplicationName() + "\\java.vmoptions" );
	}

	private File[]
	getJVMOptionFiles()
	{
		try{
			File exe = getApplicationEXELocation();

			File shared_options 		= new File( exe.getParent(), exe.getName() + ".vmoptions" );
			File local_options 			= new File( SystemProperties.getUserPath(), "java.vmoptions" );

			return( new File[]{ shared_options, local_options });

		}catch( Throwable e ){

			return( new File[0] );
		}
	}

	private File
	checkAndGetLocalVMOptionFile()

		throws PlatformManagerException
	{
		String vendor = System.getProperty( "java.vendor", "<unknown>" );

		String lc_vendor = vendor.toLowerCase( Locale.US );
		
		if ( 	!lc_vendor.startsWith( "sun " ) && 
				!lc_vendor.startsWith( "oracle " ) &&
				!lc_vendor.contains( "openjdk" )){

			throw( new PlatformManagerException(
						MessageText.getString(
							"platform.jvmopt.sunonly",
							new String[]{ vendor })));
		}

		File[] option_files = getJVMOptionFiles();

		if ( option_files.length != 2 ){

			throw( new PlatformManagerException(
					MessageText.getString( "platform.jvmopt.configerror" )));
		}

		File shared_options = option_files[0];

		if ( shared_options.exists()){

			try{
				String s_options = FileUtil.readFileAsString( shared_options, -1 );

				if ( s_options.contains( getJVMOptionRedirect() )){

					File local_options = option_files[1];

					return( local_options );

				}else{

					throw( new PlatformManagerException( MessageText.getString( "platform.jvmopt.nolink" )));
				}
			}catch( Throwable e ){

				throw( new PlatformManagerException( MessageText.getString( "platform.jvmopt.accesserror", new String[]{ Debug.getNestedExceptionMessage(e) } )));
			}
		}else{

			throw( new PlatformManagerException( MessageText.getString( "platform.jvmopt.nolinkfile" )));
		}
	}

	@Override
	public File
	getVMOptionFile()

		throws PlatformManagerException
	{
		checkCapability( PlatformManagerCapabilities.AccessExplicitVMOptions );

		File local_options = checkAndGetLocalVMOptionFile();

		if ( !local_options.exists()){

			try{
				local_options.createNewFile();

			}catch( Throwable e ){
			}
		}

		return( local_options );
	}

	@Override
	public String[]
   	getExplicitVMOptions()

     	throws PlatformManagerException
  	{
		checkCapability( PlatformManagerCapabilities.AccessExplicitVMOptions );


		File local_options = checkAndGetLocalVMOptionFile();

		try{

			List<String>	list = new ArrayList<>();

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

		File local_options = checkAndGetLocalVMOptionFile();

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
		File exe = getApplicationEXELocation();

		if ( exe != null && exe.exists()){

	 		try{
				String value = access.readStringValue(
						AEWin32Access.HKEY_CURRENT_USER,
						"Software\\Microsoft\\Windows\\CurrentVersion\\Run", app_name );

				return( value.equals( exe.getAbsolutePath()));

			}catch( Throwable e ){

				return( false );
			}
		}else{

			return( false );
		}
  	}

  	@Override
	  public void
  	setRunAtLogin(
  		boolean run )

  		throws PlatformManagerException
  	{
  		File exe = getApplicationEXELocation();

  		if ( exe != null && exe.exists()){

	  		try{
	  			String key = "Software\\Microsoft\\Windows\\CurrentVersion\\Run";

	  			if ( run ){

					access.writeStringValue(
						AEWin32Access.HKEY_CURRENT_USER,
						key, app_name, exe.getAbsolutePath());
	  			}else{

	  				access.deleteValue( AEWin32Access.HKEY_CURRENT_USER, key, app_name );
	  			}
			}catch( Throwable e ){

				throw( new PlatformManagerException( "Failed to write 'run at login' key", e ));
			}
  		}
   	}

	@Override
	public int
	getShutdownTypes()
	{
		int	result = SD_SLEEP | SD_SHUTDOWN;

		if ( canHibernate()){

			result |= SD_HIBERNATE;
		}

		return( result );
	}

	@Override
	public boolean
	getPreventComputerSleep()
	{
		synchronized( this ){

			return( prevent_computer_sleep );
		}
	}

	@Override
	public void
	setPreventComputerSleep(
		boolean		prevent_it )
	{
		synchronized( this ){

			if ( prevent_computer_sleep == prevent_it ){

				return;
			}

			prevent_computer_sleep = prevent_it;

			if ( prevent_it ){

				if ( prevent_sleep_thread == null ){

					prevent_sleep_thread =
						new AEThread2( "SleepPreventer")
						{
							@Override
							public void run()
							{
									// https://stackoverflow.com/questions/72436579/setthreadexecutionstatees-system-required-does-not-prevent-system-sleep-on-win
								
									// isWindows11OrHigher not available to use 10+
								
								boolean sr_broken = Constants.isWindows10OrHigher;
								
								if ( sr_broken ){
									
									try{
										
										access.setThreadExecutionState( AEWin32AccessInterface.ES_CONTINUOUS | AEWin32AccessInterface.ES_SYSTEM_REQUIRED );

										while( true ){
											
											synchronized( PlatformManagerImpl.this ){
		
												if ( !prevent_computer_sleep ){
		
													if ( prevent_sleep_thread == this ){
		
														prevent_sleep_thread = null;
													}
		
													break;
												}
											}
											
											Thread.sleep( 5*1000 );
										}
									}catch( Throwable e ){
										
										Debug.out( e );

									}finally{
										
										access.setThreadExecutionState( AEWin32AccessInterface.ES_CONTINUOUS );
									}
								}else{
									
									while( true ){
	
										synchronized( PlatformManagerImpl.this ){
	
											if ( !prevent_computer_sleep ){
	
												if ( prevent_sleep_thread == this ){
	
													prevent_sleep_thread = null;
												}
	
												break;
											}
										}
	
										try{
											access.setThreadExecutionState( AEWin32AccessInterface.ES_SYSTEM_REQUIRED );
	
											Thread.sleep( 30*1000 );
	
										}catch( Throwable e ){
	
											Debug.out( e );
	
											break;
										}
									}
								}
							}
						};

					prevent_sleep_thread.start();
				}
			}
		}
	}

	private boolean
	canHibernate()
	{
		try{
			if ( Constants.isWindows7OrHigher ){

				int enabled = access.readWordValue( AEWin32Access.HKEY_LOCAL_MACHINE, "System\\CurrentControlSet\\Control\\Power", "HibernateEnabled" );

				return( enabled != 0 );

			}else{
				Process p = Runtime.getRuntime().exec(
					new String[]{
						"cmd.exe",
						"/C",
						"reg query \"HKLM\\System\\CurrentControlSet\\Control\\Session Manager\\Power\" /v Heuristics"
						});

				LineNumberReader lnr = new LineNumberReader( new InputStreamReader( p.getInputStream()));

				while( true ){

					String	line = lnr.readLine();

					if ( line == null ){

						break;
					}

					line = line.trim();

					if ( line.startsWith( "Heuristics" )){

						String[] bits =  line.split( "[\\s]+");

						byte[] value = ByteFormatter.decodeString( bits[2].trim());

						return(( value[6] & 0x01 ) != 0 );
					}
				}
			}

			return( false );

		}catch( Throwable e ){

			return( false );
		}
	}

	@Override
	public void
	startup(
		final Core core )

		throws PlatformManagerException
	{
		AEDiagnostics.addWeakEvidenceGenerator( this );

		if ( !hasCapability( PlatformManagerCapabilities.AccessExplicitVMOptions )){

			return;
		}

		if ( COConfigurationManager.getBooleanParameter( "platform.win32.vmo.migrated", false )){

			try{
				File local_options = checkAndGetLocalVMOptionFile();

				if ( local_options.exists()){

					File last_good = new File( local_options.getParentFile(), local_options.getName() + ".lastgood" );

					if ( 	!last_good.exists() ||
							local_options.lastModified() > last_good.lastModified()){

						FileUtil.copyFile( local_options, last_good );
					}
				}
			}catch( Throwable e ){

				Debug.out( e );
			}
		}else{

			final int	fail_count = COConfigurationManager.getIntParameter( "platform.win32.vmo.migrated.fails", 0 );

			if ( fail_count >= 3 ){

				Debug.out( "Not attempting vmoption migration due to previous failures, please perform a full install to fix this" );

				return;
			}

				// we need an up-to-date version of this to do the migration...

			PluginInterface pi = core.getPluginManager().getPluginInterfaceByID( "azupdater" );

			if ( pi != null && Constants.compareVersions( pi.getPluginVersion(), "1.8.15" ) >= 0 ){

				new AEThread2( "win32.vmo", true )
				{
					@Override
					public void
					run()
					{
						try{
							String redirect = getJVMOptionRedirect();

							File[] option_files = getJVMOptionFiles();

							if ( option_files.length != 2 ){

								return;
							}

							File shared_options 		= option_files[0];
							File old_shared_options 	= new File( shared_options.getParentFile(), shared_options.getName() + ".old" );
							File local_options 			= option_files[1];

							if ( shared_options.exists()){

								String options = FileUtil.readFileAsString( shared_options, -1 );

								if ( !options.contains( redirect )){

										// if they're already linking somewhere then abandon

									if ( !options.contains( "-include-options" )){

										if ( FileUtil.canReallyWriteToAppDirectory()){

											if ( old_shared_options.exists()){

												old_shared_options.delete();
											}

											if ( shared_options.renameTo( old_shared_options )){

												if ( !local_options.exists()){

													if ( !FileUtil.copyFile( old_shared_options, local_options )){

														Debug.out( "Failed to copy " + old_shared_options + " to " + local_options );
													}
												}

												if ( !FileUtil.writeStringAsFile( shared_options, redirect + "\r\n" )){

													Debug.out( "Failed to write to " + shared_options );
												}
											}else{

												Debug.out( "Rename of " + shared_options + " to " + old_shared_options + " failed" );
											}
										}else{

												// insufficient perms

											UpdateInstaller installer = getInstaller( core );

												// retry later

											if ( installer == null ){

												return;
											}


											if ( !informUpdateRequired()){

												return;
											}

											if ( old_shared_options.exists()){

												installer.addRemoveAction( old_shared_options.getAbsolutePath());
											}

											installer.addMoveAction( shared_options.getAbsolutePath(), old_shared_options.getAbsolutePath());

											if ( !local_options.exists()){

												installer.addResource( "local_options", new ByteArrayInputStream( options.getBytes( "UTF-8" )));

												installer.addMoveAction( "local_options", local_options.getAbsolutePath());
											}

											installer.addResource( "redirect", new ByteArrayInputStream( ( redirect + "\r\n" ).getBytes( "UTF-8" )));

											installer.addMoveAction( "redirect", shared_options.getAbsolutePath());

											final AESemaphore sem = new AESemaphore( "vmopt" );

											final UpdateException[]	error = { null };

											installer.installNow(
												new UpdateInstallerListener()
												{
													@Override
													public void
													reportProgress(
														String		str )
													{
													}

													@Override
													public void
													complete()
													{
														sem.release();
													}

													@Override
													public void
													failed(
														UpdateException	e )
													{
														error[0] = e;

														sem.release();
													}
												});

											sem.reserve();

											if ( error[0] != null ){

												throw( error[0] );
											}

										}
									}
								}else{
										// redirect in place, might be second user so migrate if needed

									if ( old_shared_options.exists() && !local_options.exists()){

										if ( !FileUtil.copyFile( old_shared_options, local_options )){

											Debug.out( "Failed to copy " + old_shared_options + " to " + local_options );
										}
									}
								}
							}else{

									// no options

								if ( FileUtil.canReallyWriteToAppDirectory()){

									if ( !FileUtil.writeStringAsFile( shared_options, redirect + "\r\n" )){

										Debug.out( "Failed to write to " + shared_options );
									}
								}else{

										// insufficient perms

									UpdateInstaller installer = getInstaller( core );

										// retry later

									if ( installer == null ){

										return;
									}


									if ( !informUpdateRequired()){

										return;
									}

									installer.addResource( "redirect", new ByteArrayInputStream( ( redirect + "\r\n" ).getBytes( "UTF-8" )));

									installer.addMoveAction( "redirect", shared_options.getAbsolutePath());

									final AESemaphore sem = new AESemaphore( "vmopt" );

									final UpdateException[]	error = { null };

									installer.installNow(
										new UpdateInstallerListener()
										{
											@Override
											public void
											reportProgress(
												String		str )
											{
											}

											@Override
											public void
											complete()
											{
												sem.release();
											}

											@Override
											public void
											failed(
												UpdateException	e )
											{
												error[0] = e;

												sem.release();
											}
										});

									sem.reserve();

									if ( error[0] != null ){

										throw( error[0] );
									}
								}
							}

							COConfigurationManager.setParameter( "platform.win32.vmo.migrated", true );

						}catch( Throwable e ){

							COConfigurationManager.setParameter( "platform.win32.vmo.migrated.fails", fail_count + 1 );

							Debug.out( "vmoption migration failed", e );
						}
					}
				}.start();
			}
		}
	}

	private UpdateInstaller
	getInstaller(
		Core core )

		throws Exception
	{
			// we don't want our update to interfere with the normal update process so
			// hang around until it completes

		PluginInterface pi = core.getPluginManager().getDefaultPluginInterface();

		UpdateManager update_manager = pi.getUpdateManager();

		final List<UpdateCheckInstance>	l_instances = new ArrayList<>();

		update_manager.addListener(
			new UpdateManagerListener()
			{
				@Override
				public void
				checkInstanceCreated(
					UpdateCheckInstance	instance )
				{
					synchronized( l_instances ){

						l_instances.add( instance );
					}
				}
			});

		UpdateCheckInstance[] instances = update_manager.getCheckInstances();

		l_instances.addAll( Arrays.asList( instances ));

		long start = SystemTime.getMonotonousTime();

		while( true ){

			if ( SystemTime.getMonotonousTime() - start >= 5*60*1000 ){

				break;
			}

			try{
				Thread.sleep(5000);

			}catch( Throwable e ){

				Debug.out( e );

				return( null );
			}

			if ( l_instances.size() > 0 ){

				boolean	all_done = true;

				for ( UpdateCheckInstance instance: l_instances ){

					if ( !instance.isCompleteOrCancelled()){

						all_done = false;

						break;
					}
				}

				if ( all_done ){

					break;
				}
			}
		}

		if ( update_manager.getInstallers().length > 0 ){

			return( null );
		}

		UpdateInstaller installer = pi.getUpdateManager().createInstaller();

		return( installer );
	}

	private boolean
	informUpdateRequired()
	{
		UIManager ui_manager = StaticUtilities.getUIManager( 120*1000 );

		long res = ui_manager.showMessageBox(
				"update.now.title",
				"update.now.desc",
				UIManagerEvent.MT_OK | UIManagerEvent.MT_CANCEL );

		return( res == UIManagerEvent.MT_OK );
	}

	@Override
	public void
	shutdown(
		int			type )

		throws PlatformManagerException
	{
		String windir = System.getenv( "windir" );

		boolean vista_or_higher = Constants.isWindowsVistaOrHigher;

		try{
			if ( type == SD_SLEEP ){

				Runtime.getRuntime().exec(
					new String[]{
						windir + "\\system32\\rundll32.exe",
						"powrprof.dll,SetSuspendState Sleep"
					});

			}else if ( type == SD_HIBERNATE ){

				if ( vista_or_higher ){

					Runtime.getRuntime().exec(
							new String[]{
								"shutdown",
								"-h"
							});

				}else{

					Runtime.getRuntime().exec(
							new String[]{
								windir + "system32\\rundll32.exe",
								"powrprof.dll,SetSuspendState Hibernate"
							});
				}
			}else if ( type == SD_SHUTDOWN ){

				Runtime.getRuntime().exec(
						new String[]{
							"shutdown",
							"-s"
						});
			}else{

				throw new PlatformManagerException("Unsupported capability called on platform manager");
			}

		}catch( PlatformManagerException e ){

			throw( e );

		}catch( Throwable e ){

			throw( new PlatformManagerException( "shutdown failed", e ));
		}
	}

	@Override
	public String
	getApplicationCommandLine()
	{
		try{
			return( getApplicationEXELocation().toString());

		}catch( Throwable e ){

			return( null );
		}
	}

	@Override
	public boolean
	isApplicationRegistered()

		throws PlatformManagerException
	{
			// all this stuff needs the exe location so bail out early if unavailable

		File exe_loc = getApplicationEXELocation();

		if ( exe_loc.exists()){

			checkExeKey( exe_loc );
		}

		String app_path = SystemProperties.getApplicationPath();

		try{
				// always trigger magnet reg here if not owned so old users get it...

			registerMagnet( false );

		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}

		try{
				// always trigger magnet reg here if not owned so old users get it...

			if ( getAdditionalFileTypeRegistrationDetails( "DHT", ".dht" ) == RT_NONE ){

				registerDHT();
			}
		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}

		try{
				// always trigger magnet reg here if not owned so old users get it...

			registerBC();

		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}

		if ( isAdditionalFileTypeRegistered( OLD_MAIN_ASS0C, ".torrent" )){

			unregisterAdditionalFileType( OLD_MAIN_ASS0C, ".torrent" );

			registerAdditionalFileType( BIGLYBY_ASSOC, Constants.APP_NAME + " Download", ".torrent", "application/x-bittorrent" );
		}

		boolean	reg = isAdditionalFileTypeRegistered( BIGLYBY_ASSOC, ".torrent" );

		// always register .biglybt association

		boolean	biglybt_reg = isAdditionalFileTypeRegistered( BIGLYBY_ASSOC, ".biglybt" );

		if ( !biglybt_reg ){

			registerAdditionalFileType(BIGLYBY_ASSOC, "BiglyBT File", ".biglybt",
					"application/x-biglybt", true);
		}

		return( reg );
	}

	protected void
	checkExeKey(
		File		exe )
	{
		checkExeKey( AEWin32Access.HKEY_CURRENT_USER, exe );
		checkExeKey( AEWin32Access.HKEY_LOCAL_MACHINE, exe );
	}

	protected void
	checkExeKey(
		int			hkey,
		File		exe )
	{
		String	exe_str = exe.getAbsolutePath();
		String path_str = exe.getParent();

		String execReg = null;
		String parentReg = null;

		try{
			execReg = access.readStringValue( hkey, "software\\" + app_name, "exec" );

		}catch( Throwable e ){
		}

		try{
			parentReg = access.readStringValue( hkey, "software\\" + app_name, "");

		}catch( Throwable e ){
		}

		try{
			if ( execReg == null || !execReg.equals( exe_str )){

				access.writeStringValue( hkey, "software\\" + app_name,	"exec",	exe_str );
			}
		}catch( Throwable e ){
		}

		try{
			if ( parentReg == null || !parentReg.equals( path_str )){

				access.writeStringValue( hkey, "software\\" + app_name,	"",	path_str );
			}
		}catch( Throwable e ){
		}
	}

	@Override
	public boolean
	isAdditionalFileTypeRegistered(
		String		name,
		String		type )

		throws PlatformManagerException
	{
		return( getAdditionalFileTypeRegistrationDetails( name, type ) == RT_AZ );
	}

	public int
	getAdditionalFileTypeRegistrationDetails(
		String		name,
		String		type )

		throws PlatformManagerException
	{

		String	az_exe_str;

		try{
			az_exe_str = getApplicationEXELocation().toString();

		}catch( Throwable e ){

			return( RT_NONE );
		}

		try{
			String	test1 =
				access.readStringValue(
					AEWin32Access.HKEY_CLASSES_ROOT,
					name + "\\shell\\open\\command",
					"" );

			if ( !test1.equals( "\"" + az_exe_str + "\" \"%1\"" )){

				return( test1.length() ==0?RT_NONE:RT_OTHER );
			}

			String test2 =
				access.readStringValue(
						AEWin32Access.HKEY_CLASSES_ROOT,
						type,
						"");
			if ( !test2.equals( BIGLYBY_ASSOC )) {
				return test2.length() == 0 ? RT_NONE : RT_OTHER;
			}

				// MRU list is just that, to remove the "always open with" we need to kill
				// the "application" entry, if it exists

			try{
				String	always_open_with =
					access.readStringValue(
						AEWin32Access.HKEY_CURRENT_USER,
						"Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\" + type,
						"Application" );

				//System.out.println( "mru_list = " + mru_list );

				if ( always_open_with.length() > 0 ){

					// AZ is default so if this entry exists it denotes another (non-AZ) app

					return( RT_OTHER );
				}
			}catch( Throwable e ){

				// e.printStackTrace();

				// failure means things are OK
			}

			/*
			try{
				String	mru_list =
					access.readStringValue(
						AEWin32Access.HKEY_CURRENT_USER,
						"Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\.torrent\\OpenWithList",
						"MRUList" );

				//System.out.println( "mru_list = " + mru_list );

				if ( mru_list.length() > 0 ){

					String	mru =
						access.readStringValue(
							AEWin32Access.HKEY_CURRENT_USER,
							"Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\.torrent\\OpenWithList",
							"" + mru_list.charAt(0) );

					//System.out.println( "mru = " + mru );

					return( mru.equalsIgnoreCase(app_exe_name));
				}
			}catch( Throwable e ){

				// e.printStackTrace();

				// failure means things are OK
			}
			*/

			return( RT_AZ );

		}catch( Throwable e ){

			if ( 	e.getMessage() == null ||
				!e.getMessage().contains("RegOpenKey failed")){

				Debug.printStackTrace( e );
			}

			return( RT_NONE );
		}
	}

	@Override
	public void
	registerApplication()

		throws PlatformManagerException
	{
		registerMagnet( true );

		registerDHT();

		registerAdditionalFileType( BIGLYBY_ASSOC, Constants.APP_NAME + " Download", ".torrent", "application/x-bittorrent" );

		registerAdditionalFileType( BIGLYBY_ASSOC, "Vuze File", ".vuze", "application/x-vuze" );

		registerAdditionalFileType( BIGLYBY_ASSOC, "BiglyBT File", ".biglybt", "application/x-biglybt", true );
	}

	protected void
	registerMagnet(
		boolean		force )
	{

		try{
			String	az_exe_string	= getApplicationEXELocation().toString();

			boolean	magnet_exe_managing = false;

			try{
				String existing = access.readStringValue( AEWin32Access.HKEY_CLASSES_ROOT, "magnet\\shell\\open\\command", "" );

				magnet_exe_managing = existing.toLowerCase().contains("\\magnet.exe");

			}catch( Throwable e ){
			}

			if ( !magnet_exe_managing ){

				if ( force || getAdditionalFileTypeRegistrationDetails( "Magnet", ".magnet" ) == RT_NONE ){

					try{
						registerAdditionalFileType(
							"Magnet",
							"URL:Magnet Protocol",
							".magnet",
							"application/x-magnet",
							true );

					}catch( Throwable e ){

						Debug.printStackTrace(e);
					}
				}
			}

				// we always write this hierarchy in case magnet.exe installed in the future

			for ( int type: new int[]{ AEWin32Access.HKEY_LOCAL_MACHINE, AEWin32Access.HKEY_CURRENT_USER } ){

				try{
					createKey( type, "Software\\Magnet" );
					createKey( type, "Software\\Magnet\\Handlers" );
					createKey( type, "Software\\Magnet\\Handlers\\Azureus" );

					access.writeStringValue( type, "Software\\Magnet\\Handlers\\Azureus", "DefaultIcon", "\"" + az_exe_string + "\"," + getIconIndex());
					access.writeStringValue( type, "Software\\Magnet\\Handlers\\Azureus", "Description", "Download with Vuze" );
					access.writeStringValue( type, "Software\\Magnet\\Handlers\\Azureus", "ShellExecute", "\"" + az_exe_string + "\" \"%URL\"" );

					access.writeWordValue( type, "Software\\Magnet\\Handlers\\Azureus\\Type", "urn:btih", 0 );
					access.writeWordValue( type, "Software\\Magnet\\Handlers\\Azureus\\Type", "urn:btmh", 0 );

				}catch( Throwable e ){
				}
			}

		}catch( Throwable e ){
		}
	}

	protected boolean
	createKey(
		int		type,
		String	key )
	{
		try{
			access.readStringValue( type, key, "");

			return( true );

		}catch( Throwable e ){

			try{
				access.writeStringValue( type, key, "", "" );

				return( true );

			}catch( Throwable f ){

				return( false );
			}
		}
	}

	protected void
	registerDHT()
	{
		try{
			registerAdditionalFileType(
				"DHT",
				"DHT URI",
				".dht",
				"application/x-dht",
				true );

		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}
	}

	protected void
	registerBC()
	{
		try{
			registerAdditionalFileType(
				"BC",
				"BC URI",
				".bcuri",
				"application/x-bc-uri",
				true );

		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}

		try{
			registerAdditionalFileType(
				"BCTP",
				"BCTP URI",
				".bctpuri",
				"application/x-bctp-uri",
				true );

		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}
	}

	@Override
	public void
	registerAdditionalFileType(
		String		name,				// e.g. "Azureus"
		String		description,		// e.g. "BitTorrent File"
		String		type,				// e.g. ".torrent"
		String		content_type )		// e.g. "application/x-bittorrent"

		throws PlatformManagerException
	{
		registerAdditionalFileType( name, description, type, content_type, false );
	}

	public void
	registerAdditionalFileType(
		String		name,
		String		description,
		String		type,
		String		content_type,
		boolean		url_protocol)

		throws PlatformManagerException
	{
		// 	WriteRegStr HKCR ".torrent" "" "Azureus"
		// 	WriteRegStr HKCR "Azureus" "" "Vuze Torrent"
		// 	WriteRegStr HKCR "Azureus\shell" "" "open"
		// 	WriteRegStr HKCR "Azureus\DefaultIcon" "" $INSTDIR\Azureus.exe,1
		// 	WriteRegStr HKCR "Azureus\shell\open\command" "" '"$INSTDIR\Azureus.exe" "%1"'
		// 	WriteRegStr HKCR "Azureus\Content Type" "" "application/x-bittorrent"


		try{
			String	az_exe_string	= getApplicationEXELocation().toString();

			unregisterAdditionalFileType( name, type );

			writeStringToHKCRandHKCU(
					type,
					"",
					name );

			writeStringToHKCRandHKCU(
					type,
					"Content Type",
					content_type );

			writeStringToHKCRandHKCU(
					"MIME\\Database\\Content Type\\" + content_type,
					"Extension",
					type );

			writeStringToHKCRandHKCU(
					name,
					"",
					description );

			writeStringToHKCRandHKCU(
					name + "\\shell",
					"",
					"open" );

			writeStringToHKCRandHKCU(
					name + "\\DefaultIcon",
					"",
					"\"" + az_exe_string + "\"," + getIconIndex());

			writeStringToHKCRandHKCU(
					name + "\\shell\\open\\command",
					"",
					"\"" + az_exe_string + "\" \"%1\"" );

			writeStringToHKCRandHKCU(
					name,
					"Content Type",
					content_type );

			if ( url_protocol ){

				writeStringToHKCRandHKCU(
						name,
						"URL Protocol",
						"" );
			}

		}catch( PlatformManagerException e ){

			throw(e );

		}catch( Throwable e ){

			throw( new PlatformManagerException( "Failed to write registry details", e ));
		}
	}

	private void writeStringToHKCRandHKCU(String subkey, String name, String value) {
		// HKCU will most likely fail on Vista due to permissions
		try {
			access.writeStringValue(AEWin32Access.HKEY_CLASSES_ROOT, subkey, name, value);
		} catch (Throwable e) {
			if (!Constants.isWindowsVistaOrHigher) {
				Debug.out(e);
			}
		}

		try {
			access.writeStringValue(AEWin32Access.HKEY_CURRENT_USER,
					"Software\\Classes\\" + subkey, name, value);
		} catch (Throwable e) {
			Debug.out(e);
		}
	}

	@Override
	public void
	unregisterAdditionalFileType(
		String		name,				// e.g. "Azureus"
		String		type )				// e.g. ".torrent"

		throws PlatformManagerException
	{
		try{
			try {
				access.deleteKey(AEWin32Access.HKEY_CURRENT_USER,
						"Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\"
								+ type + "\\UserChoice");
			} catch (Throwable e) {
				if (!Constants.isWindowsVistaOrHigher) {
					Debug.out(e);
				}
			}


			try{

				access.deleteValue(
					AEWin32Access.HKEY_CURRENT_USER,
					"Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\" + type,
					"Application" );

			}catch( Throwable e ){

				// e.printStackTrace();
			}

			try{
				access.deleteKey(
					AEWin32Access.HKEY_CLASSES_ROOT,
					type );

			}catch( Throwable e ){

				// Debug.printStackTrace( e );
			}

			try{
				access.deleteKey(
					AEWin32Access.HKEY_CLASSES_ROOT,
					name,
					true );

			}catch( Throwable e ){

				// Debug.printStackTrace( e );
			}

			try{
				access.deleteKey(
					AEWin32Access.HKEY_CURRENT_USER,
					"Software\\Classes\\" + type );

			}catch( Throwable e ){

				// Debug.printStackTrace( e );
			}

			try{
				access.deleteKey(
					AEWin32Access.HKEY_CURRENT_USER,
					"Software\\Classes\\" + name,
					true );

			}catch( Throwable e ){

				// Debug.printStackTrace( e );
			}

		}catch( Throwable e ){

			throw( new PlatformManagerException( "Failed to delete registry details", e ));
		}
	}

	@Override
	public void
	createProcess(
		String	command_line,
		boolean	inherit_handles )

		throws PlatformManagerException
	{
		try{
			access.createProcess( command_line, inherit_handles );

		}catch( Throwable e ){

			throw( new PlatformManagerException( "Failed to create process", e ));
		}
	}

	@Override
	public void
    performRecoverableFileDelete(
		String	file_name )

		throws PlatformManagerException
	{
		try{
			access.moveToRecycleBin( file_name );

		}catch( Throwable e ){

			throw( new PlatformManagerException( "Failed to move file", e ));
		}
	}

	@Override
	public void
	setTCPTOSEnabled(
		boolean		enabled )

		throws PlatformManagerException
	{
		try{
			access.writeWordValue(
					AEWin32Access.HKEY_LOCAL_MACHINE,
					"System\\CurrentControlSet\\Services\\Tcpip\\Parameters",
					"DisableUserTOSSetting",
					enabled?0:1);

		}catch( Throwable e ){

			throw( new PlatformManagerException( "Failed to write registry details", e ));
		}
	}

	@Override
	public void
    copyFilePermissions(
		String	from_file_name,
		String	to_file_name )

		throws PlatformManagerException
	{
		try{
			access.copyFilePermissions( from_file_name, to_file_name );

		}catch( Throwable e ){

			throw( new PlatformManagerException( "Failed to copy file permissions", e ));
		}
	}

    /**
     * {@inheritDoc}
     */
    @Override
    public void showFile(String file_name)

            throws PlatformManagerException
    {
        try
        {
        	File file = new File(file_name);

        	access.createProcess( "explorer.exe " + ( file.isDirectory() ? "/e," : "/e,/select," ) + "\"" + file_name + "\"", false );

        	/*
        	Runtime.getRuntime().exec(
        			new String[] { "explorer.exe",
        					file.isDirectory() ? "/e," : "/e,/select,",
        							"\"" + file_name + "\"" });
        							*/
        }
        catch (Throwable e)
        {
            throw new PlatformManagerException("Failed to show file " + file_name, e);
        }
    }

	@Override
	public boolean
	testNativeAvailability(
		String	name )

		throws PlatformManagerException
	{
		if ( !hasCapability( PlatformManagerCapabilities.TestNativeAvailability )){

			throw new PlatformManagerException("Unsupported capability called on platform manager");
		}

		try{
			return( access.testNativeAvailability( name ));

		}catch( Throwable e ){

			throw( new PlatformManagerException( "Failed to test availability", e ));
		}
	}

	@Override
	public void
	traceRoute(
		InetAddress							interface_address,
		InetAddress							target,
		PlatformManagerPingCallback			callback )

		throws PlatformManagerException
	{
		if ( !hasCapability( PlatformManagerCapabilities.TraceRouteAvailability )){

			throw new PlatformManagerException("Unsupported capability called on platform manager");
		}

		try{
			access.traceRoute( interface_address, target, callback );

		}catch( Throwable e ){

			throw( new PlatformManagerException( "Failed to trace route", e ));
		}
	}

	@Override
	public void
	ping(
		InetAddress							interface_address,
		InetAddress							target,
		PlatformManagerPingCallback			callback )

		throws PlatformManagerException
	{
		if ( !hasCapability( PlatformManagerCapabilities.PingAvailability )){

			throw new PlatformManagerException("Unsupported capability called on platform manager");
		}

		try{
			access.ping( interface_address, target, callback );

		}catch( Throwable e ){

			throw( new PlatformManagerException( "Failed to trace route", e ));
		}
	}

	public int shellExecute(String operation, String file, String parameters,
			String directory, int SW_const) throws PlatformManagerException {
		try {
			return access.shellExecute(operation, file, parameters, directory, SW_const);
		} catch (Throwable e) {
			throw( new PlatformManagerException( "Failed to shellExecute", e ));
		}
	}

	@Override
	public int
	getMaxOpenFiles()

		throws PlatformManagerException
	{
	    throw new PlatformManagerException("Unsupported capability called on platform manager");
	}

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean
    hasCapability(
            PlatformManagerCapabilities capability)
    {
        return capabilitySet.contains(capability);
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

    /**
     * Does nothing
     */
    @Override
    public void dispose()
    {
    }

	@Override
	public int
	eventOccurred(
		int		type )
	{
		int	t_type 	= -1;
		int	res 	= -1;

		if ( type == AEWin32AccessListener.ET_SHUTDOWN ){

			t_type = PlatformManagerListener.ET_SHUTDOWN;

		}else if ( type == AEWin32AccessListener.ET_SUSPEND ){

			t_type = PlatformManagerListener.ET_SUSPEND;

			synchronized( this ){

				if ( prevent_computer_sleep ){

					res = AEWin32AccessListener.RT_SUSPEND_DENY;
				}
			}
		}else if ( type == AEWin32AccessListener.ET_RESUME ){

			t_type = PlatformManagerListener.ET_RESUME;
		}

		if ( t_type != -1 ){

			for (int i=0;i<listeners.size();i++){

				try{
					int my_res = ((PlatformManagerListener)listeners.get(i)).eventOccurred( t_type );

					if ( my_res == PlatformManagerListener.RT_SUSPEND_DENY ){

						res = AEWin32AccessListener.RT_SUSPEND_DENY;

					}else if ( my_res != -1 ){

						if ( res != -1 && my_res != res ){

							Debug.out( "Incompatible result codes: " + res + "/" + my_res );

						}else{

							res = my_res;
						}
					}

				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}
			}
		}

		return( res );
	}

    @Override
    public void
    addListener(
    	PlatformManagerListener		listener )
    {
    	listeners.add( listener );
    }

    @Override
    public void
    removeListener(
    	PlatformManagerListener		listener )
    {
    	listeners.remove( listener );
    }


	@Override
	public void requestUserAttention(int type, Object data) throws PlatformManagerException {
		throw new PlatformManagerException(PlatformManager.ERR_UNSUPPORTED);
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

	@Override
	public void
	generate(
		IndentWriter		writer )
	{
		writer.println( "Platform" );

		try{
			writer.indent();

			try{
				String[] options = getExplicitVMOptions();

				writer.println( "VM Options" );

				try{
					writer.indent();

					for ( String option: options ){

						writer.println( option );
					}
				}finally{

					writer.exdent();
				}
			}catch( Throwable e ){

				writer.println( "VM options not available: " + Debug.getNestedExceptionMessage(e));
			}

		}finally{

			writer.exdent();
		}
	}
}
