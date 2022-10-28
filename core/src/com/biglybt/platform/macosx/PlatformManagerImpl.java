/*
 * Created on 13-Mar-2004
 * Created by James Yeh
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

package com.biglybt.platform.macosx;

import java.io.*;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.*;

import com.biglybt.core.Core;
import com.biglybt.core.CoreLifecycleAdapter;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.*;
import com.biglybt.pif.platform.PlatformManagerException;
import com.biglybt.platform.PlatformManager;
import com.biglybt.platform.PlatformManagerBase;
import com.biglybt.platform.PlatformManagerCapabilities;
import com.biglybt.platform.PlatformManagerListener;
import com.biglybt.platform.PlatformManagerPingCallback;
import com.biglybt.platform.macosx.access.jnilib.OSXAccess;


/**
 * Performs platform-specific operations with Mac OS X
 *
 * @author James Yeh
 * @version 1.0 Initial Version
 * @see PlatformManager
 */
public class 
PlatformManagerImpl
	extends PlatformManagerBase
	implements PlatformManager, AEDiagnosticsEvidenceGenerator
{
	private static final LogIDs LOGID = LogIDs.CORE;

	private static final String BUNDLE_ID = "com.biglybt"; // TODO: Pull from .plist if we want to accurate

	private static final String[] SCHEMES = new String[] {
		"magnet",
		"dht",
		"vuze",
		"biglybt",
		"bc",
		"bctp"
	};

	private static final String[] MIMETYPES = new String[] {
		"application/x-bittorrent",
		"application/x-biglybt",
		"application/x-vuze",
		"application/x-bctp-uri",
		"application/x-bc-uri"
	};

	private static final String[] EXTENSIONS = new String[] {
		"torrent",
		"tor",
		"biglybt",
		"vuze",
		"vuz",
		"bctpuri",
		"bcuri"
	};

    protected static PlatformManagerImpl singleton;
    protected static AEMonitor class_mon = new AEMonitor("PlatformManager");

    private static String fileBrowserName = "Finder";

    //T: PlatformManagerCapabilities
    private final HashSet capabilitySet = new HashSet();

    private volatile String		computer_name;
    private volatile boolean	computer_name_tried;

	private Class<?> claFileManager;

	private Core core;

	private boolean		prevent_computer_sleep_pending	= false;
	private boolean 	prevent_computer_sleep			= false;
	private Process		prevent_computer_proc;

    /**
     * Gets the platform manager singleton, which was already initialized
     */
    public static PlatformManagerImpl getSingleton()
    {
        return singleton;
    }

    /**
     * Tries to enable cocoa-java access and instantiates the singleton
     */
    static
    {
      initializeSingleton();
    }

    /**
     * Instantiates the singleton
     */
    private static void initializeSingleton()
    {
        try
        {
            class_mon.enter();
            singleton = new PlatformManagerImpl();
        }
        catch (Throwable e)
        {
        	Logger.log(new LogEvent(LOGID, "Failed to initialize platform manager"
					+ " for Mac OS X", e));
        }
        finally
        {
            class_mon.exit();
        }

        COConfigurationManager.addAndFireParameterListener("FileBrowse.usePathFinder", new ParameterListener() {
					@Override
					public void parameterChanged(String parameterName) {
						fileBrowserName = COConfigurationManager.getBooleanParameter("FileBrowse.usePathFinder")
	        		? "Path Finder" : "Finder";
					}
				});
    }

    /**
     * Creates a new PlatformManager and initializes its capabilities
     */
    public PlatformManagerImpl()
    {
        capabilitySet.add(PlatformManagerCapabilities.RecoverableFileDelete);
        capabilitySet.add(PlatformManagerCapabilities.ShowFileInBrowser);
        capabilitySet.add(PlatformManagerCapabilities.ShowPathInCommandLine);
        capabilitySet.add(PlatformManagerCapabilities.CreateCommandLineProcess);
        capabilitySet.add(PlatformManagerCapabilities.GetUserDataDirectory);
        capabilitySet.add(PlatformManagerCapabilities.UseNativeScripting);
        capabilitySet.add(PlatformManagerCapabilities.PlaySystemAlert);
        capabilitySet.add(PlatformManagerCapabilities.RequestUserAttention);

        if (OSXAccess.isLoaded()) {
	        capabilitySet.add(PlatformManagerCapabilities.GetVersion);
	        try {
  	        if (OSXAccess.canSetDefaultApp()) {
  		        capabilitySet.add(PlatformManagerCapabilities.RegisterFileAssociations);
  	        }
	        } catch (Throwable t) {
	        	// likely java.lang.UnsatisfiedLinkError -- older version
	        }
	        
	        try{
	        	double version = Double.parseDouble(getVersion());
	        	
	        	if ( version >= 1.13 ){
	        		
	        		OSXAccess.disableAppNap();
	        	}
	        }catch( Throwable e ){
	        	e.printStackTrace();
	        }
        }

        if (hasVMOptions()) {
          capabilitySet.add(PlatformManagerCapabilities.AccessExplicitVMOptions);
        }

        capabilitySet.add(PlatformManagerCapabilities.RunAtLogin);
        capabilitySet.add(PlatformManagerCapabilities.GetMaxOpenFiles);

        if ( 	new File( "/usr/bin/pmset" ).canRead() ||
        		new File( "/usr/bin/caffeinate" ).canRead()){

        	capabilitySet.add( PlatformManagerCapabilities.PreventComputerSleep );
        }

        try{
        	if ( new File( "/usr/bin/defaults" ).exists()){

				boolean	found_sleep_disabled = false;
				
				try{
					String[] read_command = { "/usr/bin/defaults", "read", BUNDLE_ID };

					Process p = Runtime.getRuntime().exec( read_command );

					if ( p.waitFor() == 0 ){

						InputStream is = p.getInputStream();

						LineNumberReader lnr = new LineNumberReader( new InputStreamReader( is, "UTF-8" ));

						while( true ){

							String line = lnr.readLine();

							if ( line == null ){

								break;
							}

							if ( line.contains( "NSAppSleepDisabled" )){

								found_sleep_disabled = true;
							}
						}
					}
				}catch( Throwable e ){

					e.printStackTrace();
				}

        		if ( !found_sleep_disabled ){

		        	String[] write_command = {
		        		"/usr/bin/defaults",
		        		"write",
		        		BUNDLE_ID,
		        		"NSAppSleepDisabled",
		        		"-bool",
		        		"YES"
		        	};

		        	Runtime.getRuntime().exec( write_command );
        		}
        		
        	}else{

        		System.err.println( "/usr/bin/defaults missing" );
        	}
        }catch( Throwable e ){

        	e.printStackTrace();
        }

        AEDiagnostics.addWeakEvidenceGenerator(this);
    }

		/**
     * {@inheritDoc}
     */
    @Override
    public int getPlatformType()
    {
        return PT_MACOSX;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getVersion() throws PlatformManagerException
    {
    	if (!OSXAccess.isLoaded()) {
        throw new PlatformManagerException("Unsupported capability called on platform manager");
    	}

    	return OSXAccess.getVersion();
    }

	@Override
	public boolean
	setUseSystemTheme(
		boolean		use_it )
	
		throws PlatformManagerException
	{
        try{
        	if ( new File( "/usr/bin/defaults" ).exists()){

				Boolean requires_aqua = null;
				
				try{
					String[] read_command = { "/usr/bin/defaults", "read", BUNDLE_ID };

					Process p = Runtime.getRuntime().exec( read_command );

					if ( p.waitFor() == 0 ){

						InputStream is = p.getInputStream();

						LineNumberReader lnr = new LineNumberReader( new InputStreamReader( is, "UTF-8" ));

						while( true ){

							String line = lnr.readLine();

							if ( line == null ){

								break;
							}

							if ( line.contains( "NSRequiresAquaSystemAppearance" )){
								
								String[] bits = line.split( "=" );
								
								if ( bits.length > 1 ){
									
									String rhs = bits[1].trim().toLowerCase( Locale.US );
								
									requires_aqua = rhs.contains( "1" );
								}
							}
						}
					}
				}catch( Throwable e ){

					e.printStackTrace();
				}
          		
        		boolean wants_aqua = !use_it;
        		
        		if ( requires_aqua == null || requires_aqua != wants_aqua ){
        			
		        	String[] write_command = {
			        		"/usr/bin/defaults",
			        		"write",
			        		BUNDLE_ID,
			        		"NSRequiresAquaSystemAppearance",
			        		"-bool",
			        		(wants_aqua?"True":"False" )
			        	};
		        	
		        	Runtime.getRuntime().exec( write_command );
		        	
		        	return( true );
		        	
        		}else{
        			
        			return( false );
        		}
        	}else{

        		throw( new PlatformManagerException( "/usr/bin/defaults missing" ));
        	}
        }catch( Throwable e ){

        	throw( new PlatformManagerException( "Failed to set application default", e ));
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

	private File
	checkAndGetLocalVMOptionFile()

		throws PlatformManagerException
	{
		checkCanUseJVMOptions();
		
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

	private String
	getJVMOptionRedirect()
	{
		return ("-include-options ${HOME}/Library/Application Support/"
				+ SystemProperties.getApplicationName() + "/java.vmoptions");
	}

  private boolean hasVMOptions() {
		File fileVMOption = FileUtil.getApplicationFile("java.vmoptions");
		return fileVMOption.exists();
	}

	private File[]
	getJVMOptionFiles()
	{
		try{

			File shared_options 		= FileUtil.getApplicationFile("java.vmoptions");
			// use LOC_USER_DATA instead of SystemProperties.getUserPath(),
			// since we assume in getJVMOptionRedirect that the shared_options'
			// include points to LOC_USER_DATA.
			File local_options 			= new File( getLocation(LOC_USER_DATA), "java.vmoptions" );

			return( new File[]{ shared_options, local_options });

		}catch( Throwable e ){

			return( new File[0] );
		}
	}


	@Override
	public void
	startup(
		Core _core )

		throws PlatformManagerException
	{
		synchronized( this ){

			core = _core;

			if ( prevent_computer_sleep_pending ){

				prevent_computer_sleep_pending = false;

				setPreventComputerSleep( true );
			}
		}

		core.addLifecycleListener(
			new CoreLifecycleAdapter()
			{
				@Override
				public void
				stopping(
					Core core )
				{
					synchronized( PlatformManagerImpl.this ){

						try{
							setPreventComputerSleep( false );

						}catch( Throwable e ){
						}

						core = null;
					}
				}
			});
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
		 throw new PlatformManagerException( "Unsupported capability called on platform manager" );
	}

	@Override
	public void
	setPreventComputerSleep(
		boolean		prevent_it )

		throws PlatformManagerException
	{
		synchronized( this ){

			if ( core == null ){

				prevent_computer_sleep_pending = prevent_it;

				return;
			}

			if ( prevent_computer_sleep == prevent_it ){

				return;
			}

			prevent_computer_sleep = prevent_it;

			if ( prevent_it ){

				String[] command;

				File binary = new File( "/usr/bin/caffeinate" );

				if ( binary.canRead()){

					command = new String[]{ binary.getAbsolutePath(), "-i" };

				}else{

					binary = new File( "/usr/bin/pmset" );

					if ( binary.canRead()){

						command = new String[]{ binary.getAbsolutePath(), "noidle" };

					}else{

						 throw new PlatformManagerException("Unsupported capability called on platform manager");
					}
				}

				if ( prevent_computer_proc != null ){

					Debug.out( "eh?" );

					prevent_computer_proc.destroy();
				}

				try{
					System.out.println( "Starting idle sleep preventer: " + command[0] );

					prevent_computer_proc = Runtime.getRuntime().exec( command );

				}catch( Throwable e ){

					Debug.out( e );
				}
			}else{

				if ( prevent_computer_proc != null ){

					System.out.println( "Stopping idle sleep preventer" );

					prevent_computer_proc.destroy();

					prevent_computer_proc = null;
				}
			}
		}
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
	  public boolean
  	getRunAtLogin()

  		throws PlatformManagerException
  	{
  		if ( Constants.isOSX_10_8_OrHigher ){

  			String item_name = SystemProperties.getApplicationName();

  			try{

                 StringBuffer sb = new StringBuffer();
                 sb.append("tell application \"");
                 sb.append("System Events");
                 sb.append("\" to get the name of every login item");

                 String[] items = performOSAScript(sb).split( "," );

                 for ( String item: items ){

                	 if ( item.trim().equalsIgnoreCase( item_name )){

                		 return( true );
                	 }
                 }

                 return( false );

             }catch (Throwable e){

                 throw new PlatformManagerException("Failed to get login items", e);
             }
  		}

  		File f = getLoginPList();

  		if ( !f.exists()){

  			return( false );
  		}

  		File	bundle_file = getAbsoluteBundleFile();

  		if ( !bundle_file.exists()){

  			return( false );
  		}

  		try{
  			convertToXML( f );

  			LineNumberReader lnr = new LineNumberReader( new InputStreamReader(new FileInputStream( f ), "UTF-8" ));

  			int	state = 0;

  			String	target = bundle_file.getAbsolutePath();

  			try{
  				while( true ){

  					String line = lnr.readLine();

  					if ( line == null ){

  						break;
  					}

  					if ( state == 0 ){

  						if ( containsTag( line, "AutoLaunchedApplicationDictionary" )){

  							state = 1;
  						}
  					}else{

  						if ( line.contains( target )){

  							return( true );
  						}
  					}
  				}

  				return( false );

  			}finally{

  				lnr.close();
  			}
  		}catch( Throwable e ){

  			throw( new PlatformManagerException( "Failed to read input file", e ));
  		}
  	}

  	@Override
	  public void
  	setRunAtLogin(
  		boolean run )

  		throws PlatformManagerException
  	{
  		if ( getRunAtLogin() == run ){

  			return;
  		}

		File	bundle_file = getAbsoluteBundleFile();

  		if ( !bundle_file.exists()){

 			throw( new PlatformManagerException( "Failed to write set run-at-login, bundle not found" ));
  		}

		String	abs_target = bundle_file.getAbsolutePath();

  		if ( Constants.isOSX_10_8_OrHigher ){

  			if ( run ){

  	 			 try{

  	                 StringBuffer sb = new StringBuffer();
  	                 sb.append("tell application \"");
  	                 sb.append("System Events");
  	                 sb.append("\" to make login item at end with properties {path:\"" );
  	                 sb.append(abs_target);
  	                 sb.append("\", hidden:false}" );

  	                 System.out.println( performOSAScript(sb));

  	                 return;

  	             }catch (Throwable e){

  	                 throw new PlatformManagerException("Failed to add login item", e);
  	             }

  			}else{


 	 			 try{

  	                 StringBuffer sb = new StringBuffer();
  	                 sb.append("tell application \"");
  	                 sb.append("System Events");
  	                 sb.append("\" to delete login item \"" );
  	                 sb.append(SystemProperties.getApplicationName());
  	                 sb.append("\"" );

  	                 System.out.println( performOSAScript(sb));

  	                 return;

  	             }catch (Throwable e){

  	                 throw new PlatformManagerException("Failed to delete login item", e);
  	             }
  			}
  		}



  		File f = getLoginPList();

  		if ( f.exists()){

  			convertToXML( f );

  		}else{

  			try{
  				PrintWriter pw = new PrintWriter( new OutputStreamWriter( new FileOutputStream( f ), "UTF-8" ));

  				try{

  					pw.println( "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" );
  					pw.println( "<!DOCTYPE plist PUBLIC \"-//Apple Computer//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">" );
  					pw.println( "<plist version=\"1.0\">" );
  					pw.println( "<dict>" );

  					pw.println( "</dict>" );
  					pw.println( "</plist>" );

  				}finally{

  					pw.close();
  				}
  			}catch( Throwable e ){

  				throw( new PlatformManagerException( "Failed to write output file", e ));
  			}
  		}


  		try{
  			List<String>	lines = new ArrayList<>();

  			LineNumberReader lnr = new LineNumberReader( new InputStreamReader(new FileInputStream( f ), "UTF-8" ));

  			int	dict_line 			= -1;
  			int	auto_launch_line 	= -1;
  			int	target_index		= -1;

  			try{
  				while( true ){

  					String line = lnr.readLine();

  					if ( line == null ){

  						break;
  					}

 					lines.add( line );

  					if ( dict_line == -1 && containsTag( line, "<dict>" )){

  						dict_line = lines.size();
  					}

  					if ( auto_launch_line == -1 && containsTag( line, "AutoLaunchedApplicationDictionary" )){

  						auto_launch_line = lines.size();
  					}

  					if ( line.contains( abs_target )){

  						target_index = lines.size();
  					}
  				}

  				if ( dict_line == -1 ){

  					throw( new PlatformManagerException( "Malformed plist - no 'dict' entry" ));
  				}

  				if ( auto_launch_line == -1 ){

  					lines.add( dict_line, "\t<key>AutoLaunchedApplicationDictionary</key>" );

  					auto_launch_line = dict_line+1;

  					lines.add( auto_launch_line, "\t<array>" );
  					lines.add( auto_launch_line+1, "\t</array>" );
  				}
  			}finally{

  				lnr.close();
  			}

  			if ( run ){

  				if ( target_index != -1 || auto_launch_line == -1 ){

  					return;
  				}

  				target_index = auto_launch_line+1;

 				lines.add( target_index++, "\t\t<dict>" );
				lines.add( target_index++, "\t\t\t<key>Path</key>" );
				lines.add( target_index++, "\t\t\t<string>" + abs_target + "</string>" );
 				lines.add( target_index++, "\t\t</dict>" );

  			}else{

  				if ( target_index == -1 ){

  					return;
  				}

  				while( !containsTag( lines.get( target_index ), "</dict>" )){

  					lines.remove( target_index );
  				}

  				lines.remove( target_index );

  				target_index--;

  				while( !containsTag( lines.get( target_index ), "<dict>" )){

  					lines.remove( target_index );

  					target_index--;
  				}

  				lines.remove( target_index );
  			}

  			File	backup = new File( f.getParentFile(), f.getName() + ".bak" );

  			if ( backup.exists()){

  				backup.delete();
  			}

  			if ( !f.renameTo( backup )){

  				throw( new PlatformManagerException( "Failed to backup " + f ));
  			}

			boolean	ok = false;

			try{
				PrintWriter pw = new PrintWriter( new OutputStreamWriter( new FileOutputStream( f ), "UTF-8" ));

				try{

					for ( String line: lines ){

						pw.println( line );
					}
				}finally{

					pw.close();

					if ( pw.checkError()){

						throw( new PlatformManagerException( "Failed to write output file" ));
					}

					ok = true;
				}
			}finally{

				if ( !ok ){

					backup.renameTo( f );
				}
			}

  		}catch( PlatformManagerException e ){

  			throw( e );

  		}catch( Throwable e ){

  			throw( new PlatformManagerException( "Failed to write output file", e ));
  		}
   	}

  	private void
  	convertToXML(
  		File		file )

  		throws PlatformManagerException
  	{
 		try{
			LineNumberReader lnr = new LineNumberReader( new InputStreamReader(new FileInputStream( file ), "UTF-8" ));

			try{
				String 	line = lnr.readLine();

				if ( line == null ){

					return;
				}

				if ( line.trim().toLowerCase().startsWith( "<?xml" )){

					return;
				}

	 			Runtime.getRuntime().exec(
					new String[]{
						findCommand( "plutil" ),
						"-convert",
						"xml1",
						file.getAbsolutePath()
					}).waitFor();

	  		}finally{

	  			lnr.close();
	  		}
  		}catch( Throwable e ){

  			throw( new PlatformManagerException( "Failed to convert plist to xml" ));
  		}
  	}

  	private String
  	findCommand(
  		String	name )
  	{
  		final String[]  locations = { "/bin", "/usr/bin" };

  		for ( String s: locations ){

  			File f = new File( s, name );

  			if ( f.exists() && f.canRead()){

  				return( f.getAbsolutePath());
  			}
  		}

  		return( name );
  	}

  	private boolean
  	containsTag(
  		String	line,
  		String	tag )
  	{
  		line 	= line.trim().toLowerCase( Locale.US );
  		tag		= tag.toLowerCase( Locale.US );

  		StringBuilder line2 = new StringBuilder( line.length());

  		for (char c:line.toCharArray()){

  			if ( !Character.isWhitespace( c )){

  				line2.append( c );
  			}
  		}

  		return( line2.toString().contains( tag ));
  	}

    private File
    getLoginPList()

    	throws PlatformManagerException
    {
    	return( new File(System.getProperty("user.home"), "/Library/Preferences/loginwindow.plist" ));
    }

    /**
     * {@inheritDoc}
     * @see com.biglybt.core.util.SystemProperties#getUserPath()
     */
    @Override
    public String getUserDataDirectory() throws PlatformManagerException
    {
    	return new File(System.getProperty("user.home")
    			+ "/Library/Application Support/"
    			+ SystemProperties.getApplicationName()).getPath()
    			+ SystemProperties.SEP;
    }

	@Override
	public String
	getComputerName()
	{
		if ( computer_name_tried ){

			return( computer_name );
		}

		try{
			String result = null;

			String	hostname = System.getenv( "HOSTNAME" );

			if ( hostname != null && hostname.length() > 0 ){

				result = hostname;
			}

			if ( result == null ){

				String	host = System.getenv( "HOST" );

				if ( host != null && host.length() > 0 ){

					result = host;
				}
			}

			if ( result == null ){

				try{
					String[] to_run = new String[3];

				  	to_run[0] = "/bin/sh";
				  	to_run[1] = "-c";
				  	to_run[2] = "echo $HOSTNAME";

					Process p = Runtime.getRuntime().exec( to_run );

					if ( p.waitFor() == 0 ){

						String	output = "";

						InputStream is = p.getInputStream();

						while( true ){

							byte[] buffer = new byte[1024];

							int len = is.read( buffer );

							if ( len <= 0 ){

								break;
							}

							output += new String( buffer, 0, len );

							if ( output.length() > 64 ){

								break;
							}
						}

						if ( output.length() > 0 ){

							result = output.trim();

							int pos = result.indexOf(' ');

							if ( pos != -1 ){

								result = result.substring( 0, pos ).trim();
							}
						}
					}
				}catch( Throwable e ){
				}
			}

			if ( result != null ){

				int	pos = result.lastIndexOf( '.' );

				if ( pos != -1 ){

					result = result.substring( 0, pos );
				}

				if ( result.length() > 0 ){

					if ( result.length() > 32 ){

						result = result.substring( 0, 32 );
					}

					computer_name = result;
				}
			}

			return( computer_name );

		}finally{

			computer_name_tried = true;
		}
	}

	@Override
	public File
	getLocation(
		long	location_id )

		throws PlatformManagerException
	{
		switch ((int)location_id) {
			case LOC_USER_DATA:
				return new File(getUserDataDirectory());

			case LOC_DOCUMENTS:
				try {
					return new File(OSXAccess.getDocDir());
				} catch (Throwable e) {
					// throws UnsatisfiedLinkError if no osxaccess
					// Sometimes throws NullPointerException

					// Usually in user.home + Documents
					return new File(System.getProperty("user.home"), "Documents");
				}

			case LOC_DOWNLOADS:
				return new File(System.getProperty("user.home"), "Downloads");

			case LOC_MUSIC:

			case LOC_VIDEO:

			default:
				return( null );
		}

	}

	/* (non-Javadoc)
	 * @see com.biglybt.platform.PlatformManager#isApplicationRegistered()
	 */
	@Override
	public boolean isApplicationRegistered()
			throws PlatformManagerException {
		try {
			if (OSXAccess.canSetDefaultApp()) {
				for (String ext : EXTENSIONS) {
					if (!isOurExt(ext)) {
						return false;
					}
				}
				for (String mimeType : MIMETYPES) {
					if (!isOurMimeType(mimeType)) {
						return false;
					}
				}
				for (String scheme : SCHEMES) {
					if (!isOurScheme(scheme)) {
						return false;
					}
				}
			}
		} catch (Throwable e) {

		}
		return true;
	}

	private boolean isOurExt(String ext) {
		try {
			String appForExt = OSXAccess.getDefaultAppForExt(ext);
			//System.out.println("app for ext:" + ext + ": " + appForExt);
			return BUNDLE_ID.equals(appForExt);
		} catch (Throwable e) {
			return true; // fake it
		}
	}

	private boolean isOurScheme(String scheme) {
		try {
			String appForScheme = OSXAccess.getDefaultAppForScheme(scheme);
			//System.out.println("app for scheme:" + scheme + ": " + appForScheme);
			return BUNDLE_ID.equals(appForScheme);
		} catch (Throwable e) {
			return true; // fake it
		}
	}

	private boolean isOurMimeType(String mimetype) {
		try {
			String appForMimeType = OSXAccess.getDefaultAppForMime(mimetype);
			//System.out.println("app for mime:" + mimetype + ": " + appForMimeType);
			return BUNDLE_ID.equals(appForMimeType);
		} catch (Throwable e) {
			return true; // fake it
		}
	}

	/**
	 * Bundle Path is the .app file that launched vuze, usually /Applications/Vuze.app
	 */
    private String
    getBundlePath()
    {
  		String mod_name = System.getProperty( "exe4j.moduleName", null );
  		if (mod_name != null && mod_name.endsWith(".app")) {
  			return mod_name;
  		}
  		
  		String app_path = SystemProperties.getApplicationPath();
  		
  		String app_name = SystemProperties.getApplicationName() + ".app";
  		
		String result = app_path + app_name;
		
		if ( !new File( result ).exists()){
			
				// since restructuring the install layout the app_path is /Applications/BiglyBT/.biglybt
			
			if ( app_path.endsWith( "/.biglybt/" )){
				
				String test = app_path.substring( 0, app_path.length()-9) + app_name;
				
				if ( new File( test ).exists()){
					
					result = test;
				}
			}
		}
		
		return( result );
    }

  	/**
  	 * Bundle Path is the .app file that launched vuze, usually /Applications/Vuze.app
  	 */
    private File
    getAbsoluteBundleFile()
    {
    	return( new File( getBundlePath()).getAbsoluteFile());
    }

  	/**
  	 * command to launch Vuze
  	 */
	@Override
	public String
	getApplicationCommandLine()
		throws PlatformManagerException
	{
		try{
			File osx_app_bundle = getAbsoluteBundleFile();

			if( !osx_app_bundle.exists() ) {
				String msg = "OSX app bundle not found: [" +osx_app_bundle.toString()+ "]";
				System.out.println( msg );
				if (Logger.isEnabled())
					Logger.log(new LogEvent(LOGID, msg));
				return "open -a \"" + SystemProperties.getApplicationName() + "\"";
			}

			return "open -a \"" +osx_app_bundle.toString()+ "\"";
		}
		catch( Throwable t ){
			t.printStackTrace();
			return null;
		}
	}


	@Override
	public boolean
	isAdditionalFileTypeRegistered(
		String		name,				// e.g. "BitTorrent"
		String		type )				// e.g. ".torrent"

		throws PlatformManagerException
	{
		String osxType = type.startsWith(".") ? type.substring(1) : type;
		return isOurExt(osxType);
	}

	@Override
	public void
	unregisterAdditionalFileType(
		String		name,				// e.g. "BitTorrent"
		String		type )				// e.g. ".torrent"

		throws PlatformManagerException
	{
		throw new PlatformManagerException("Unsupported capability called on platform manager");
	}

	@Override
	public void
	registerAdditionalFileType(
		String		name,				// e.g. "BitTorrent"
		String		description,		// e.g. "BitTorrent File"
		String		type,				// e.g. ".torrent"
		String		content_type )		// e.g. "application/x-bittorrent"

		throws PlatformManagerException
	{
		try {
			if (OSXAccess.canSetDefaultApp()) {
				if (type != null) {
  				String osxType = type.startsWith(".") ? type.substring(1) : type;
  				OSXAccess.setDefaultAppForExt(BUNDLE_ID, osxType);
				}
				if (content_type != null) {
					OSXAccess.setDefaultAppForMime(BUNDLE_ID, content_type);
				}
			}
		} catch (Throwable t) {
			throw new PlatformManagerException(
					"registerAdditionalFileType failed on platform manager", t);
		}
	}

    @Override
    public void registerApplication() throws PlatformManagerException
    {
  		try {
  			if (OSXAccess.canSetDefaultApp()) {
  				for (String ext : EXTENSIONS) {
  					OSXAccess.setDefaultAppForExt(BUNDLE_ID, ext);
  				}
  				for (String mimeType : MIMETYPES) {
  					OSXAccess.setDefaultAppForMime(BUNDLE_ID, mimeType);
  				}
  				for (String scheme : SCHEMES) {
  					OSXAccess.setDefaultAppForScheme(BUNDLE_ID, scheme);
  				}
  			}
  		} catch (Throwable t) {
  			throw new PlatformManagerException(
  					"registerApplication failed on platform manager", t);
  		}

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createProcess(String cmd, boolean inheritsHandles) throws PlatformManagerException
    {
        try
        {
            performRuntimeExec(cmd.split(" "));
        }
        catch (Throwable e)
        {
            throw new PlatformManagerException("Failed to create process", e);
        }
    }

    private Class<?> getFileManagerClass() {
    	if (claFileManager != null) {
    		return claFileManager;
    	}

			try {
				// We can only use FileManager after CocoaUIEnhancer has been initialized
				// because refering to FileManager earlier will prevent our main menu from
				// working
				Class<?> claCocoaUIEnhancer = Class.forName("com.biglybt.ui.swt.osx.CocoaUIEnhancer");
				if (((Boolean) claCocoaUIEnhancer.getMethod("isInitialized").invoke(null)).booleanValue()) {
					claFileManager = Class.forName("com.apple.eio.FileManager");
				}
			} catch (Exception e) {
			}
			return claFileManager;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void performRecoverableFileDelete(String path) throws PlatformManagerException
    {
        File file = new File(path);
        if(!file.exists())
        {
	        	if (Logger.isEnabled())
							Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING, "Cannot find "
									+ file.getName()));
            return;
        }


				try {
					Class<?> claFileManager = getFileManagerClass();

					if (claFileManager != null) {
  					Method methMoveToTrash = claFileManager.getMethod("moveToTrash",
    						new Class[] {
    							File.class
    						});
    				if (methMoveToTrash != null) {
  						Object result = methMoveToTrash.invoke(null, new Object[] {
  							file
  						});
  						if (result instanceof Boolean) {
  							if (((Boolean) result).booleanValue()) {
  								return;
  							}
  						}
    				}
					}
 				} catch (Throwable e) {
				}

        boolean useOSA = !NativeInvocationBridge.sharedInstance().isEnabled() || !NativeInvocationBridge.sharedInstance().performRecoverableFileDelete(file);

        if(useOSA)
        {
            try
            {
                StringBuffer sb = new StringBuffer();
                sb.append("tell application \"");
                sb.append("Finder");
                sb.append("\" to move (posix file \"");
                sb.append(path);
                sb.append("\" as alias) to the trash");

                performOSAScript(sb);
            }
            catch (Throwable e)
            {
                throw new PlatformManagerException("Failed to move file", e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasCapability(PlatformManagerCapabilities capability)
    {
        return capabilitySet.contains(capability);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dispose()
    {
    	try {
    		if (NativeInvocationBridge.hasSharedInstance()) {
    			NativeInvocationBridge.sharedInstance().dispose();
    		}
    	} catch (Throwable t) {
    		Debug.out("Problem disposing NativeInvocationBridge", t);
    	}
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTCPTOSEnabled(boolean enabled) throws PlatformManagerException
    {
        throw new PlatformManagerException("Unsupported capability called on platform manager");
    }

	@Override
	public void
    copyFilePermissions(
		String	from_file_name,
		String	to_file_name )

		throws PlatformManagerException
	{
	    throw new PlatformManagerException("Unsupported capability called on platform manager");
	}

    /**
     * {@inheritDoc}
     */
    @Override
    public void showFile(String path) throws PlatformManagerException
    {
        File file = new File(path);
        if(!file.exists())
        {
        	if (Logger.isEnabled())
        		Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING, "Cannot find "
        				+ file.getName()));
            throw new PlatformManagerException("File not found");
        }

        showInFinder(file);
    }

    // Public utility methods not shared across the interface

    /**
     * Plays the system alert (the jingle is specified by the user in System Preferences)
     */
    public void playSystemAlert()
    {
        try
        {
            performRuntimeExec(new String[]{"beep"});
        }
        catch (IOException e)
        {
        	if (Logger.isEnabled())
        		Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
						"Cannot play system alert"));
        	Logger.log(new LogEvent(LOGID, "", e));
        }
    }

    /**
     * <p>Shows the given file or directory in Finder</p>
     * @param path Absolute path to the file or directory
     */
    public void showInFinder(File path)
    {

			try {
				Class<?> claFileManager = getFileManagerClass();
				if (claFileManager != null && getFileBrowserName().equals("Finder")) {
  				Method methRevealInFinder = claFileManager.getMethod("revealInFinder",
  						new Class[] {
  							File.class
  						});
  				if (methRevealInFinder != null) {
						Object result = methRevealInFinder.invoke(null, new Object[] {
							path
						});
						if (result instanceof Boolean) {
							if (((Boolean) result).booleanValue()) {
								return;
							}
						}
  				}
				}
			} catch (Throwable e) {
			}

        boolean useOSA = !NativeInvocationBridge.sharedInstance().isEnabled() || !NativeInvocationBridge.sharedInstance().showInFinder(path,fileBrowserName);

        if(useOSA)
        {
            StringBuffer sb = new StringBuffer();
            sb.append("tell application \"");
            sb.append(getFileBrowserName());
            sb.append("\"\n");
            sb.append("reveal (posix file \"");
            sb.append(path);
            sb.append("\" as alias)\n");
            sb.append("activate\n");
            sb.append("end tell\n");

            try
            {
                performOSAScript(sb);
            }
            catch (IOException e)
            {
                Logger.log(new LogAlert(LogAlert.UNREPEATABLE, LogAlert.AT_ERROR, e
						.getMessage()));
            }
        }
    }

    /**
     * <p>Shows the given file or directory in Terminal by executing cd /absolute/path/to</p>
     * @param path Absolute path to the file or directory
     */
    public void showInTerminal(String path)
    {
        showInTerminal(new File(path));
    }

    /**
     * <p>Shows the given file or directory in Terminal by executing cd /absolute/path/to</p>
     * @param path Absolute path to the file or directory
     */
    public void showInTerminal(File path)
    {
        if (path.isFile())
        {
            path = path.getParentFile();
        }

        if (path != null && path.isDirectory())
        {
            StringBuffer sb = new StringBuffer();
            sb.append("tell application \"");
            sb.append("Terminal");
            sb.append("\" to do script \"cd ");
            sb.append(path.getAbsolutePath().replaceAll(" ", "\\ "));
            sb.append("\"");

            try
            {
                performOSAScript(sb);
            }
            catch (IOException e)
            {
                Logger.log(new LogAlert(LogAlert.UNREPEATABLE, LogAlert.AT_ERROR, e
						.getMessage()));
            }
        }
        else
        {
        	if (Logger.isEnabled())
        		Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING, "Cannot find "
        				+ (path==null?"null":path.getName())));
        }
    }

    // Internal utility methods

    /**
     * Compiles a new AppleScript instance and runs it
     * @param cmd AppleScript command to execute; do not surround command with extra quotation marks
     * @return Output of the script
     * @throws IOException If the script failed to execute
     */
    protected static String performOSAScript(CharSequence cmd) throws IOException
    {
        return performOSAScript(new CharSequence[]{cmd});
    }

    /**
     * Compiles a new AppleScript instance and runs it
     * @param cmds AppleScript Sequence of commands to execute; do not surround command with extra quotation marks
     * @return Output of the script
     * @throws IOException If the script failed to execute
     */
    protected static String performOSAScript(CharSequence[] cmds) throws IOException
    {
    	/*
        long start = System.currentTimeMillis();

        Debug.outNoStack("Executing OSAScript: ");
        for (int i = 0; i < cmds.length; i++)
        {
            Debug.outNoStack("\t" + cmds[i]);
        }
		*/

        String[] cmdargs = new String[2 * cmds.length + 1];
        cmdargs[0] = "osascript";
        for (int i = 0; i < cmds.length; i++)
        {
            cmdargs[i * 2 + 1] = "-e";
            cmdargs[i * 2 + 2] = String.valueOf(cmds[i]);
        }

        Process osaProcess = performRuntimeExec(cmdargs);
        BufferedReader reader = new BufferedReader(new InputStreamReader(osaProcess.getInputStream()));
        String line = reader.readLine();
        reader.close();

        //Debug.outNoStack("OSAScript Output: " + line);

        reader = new BufferedReader(new InputStreamReader(osaProcess.getErrorStream()));
        String errorMsg = reader.readLine();
        reader.close();

        //Debug.outNoStack("OSAScript Error (if any): " + errorMsg);

        //Debug.outNoStack(MessageFormat.format("OSAScript execution ended ({0}ms)", new Object[]{String.valueOf(System.currentTimeMillis() - start)}));

        try {
        	osaProcess.destroy();
        } catch (Throwable t) {
        	//ignore
        }

        if (errorMsg != null)
        {
            throw new IOException(errorMsg);
        }

        return line;
    }

    /**
     * Compiles a new AppleScript instance and runs it
     * @param script AppleScript file (.scpt) to execute
     * @return Output of the script
     * @throws IOException If the script failed to execute
     */
    protected static String performOSAScript(File script) throws IOException
    {
    	/*
        long start = System.currentTimeMillis();
        Debug.outNoStack("Executing OSAScript from file: " + script.getPath());
		*/

        Process osaProcess = performRuntimeExec(new String[]{"osascript", script.getPath()});
        BufferedReader reader = new BufferedReader(new InputStreamReader(osaProcess.getInputStream()));
        String line = reader.readLine();
        reader.close();
        //Debug.outNoStack("OSAScript Output: " + line);

        reader = new BufferedReader(new InputStreamReader(osaProcess.getErrorStream()));
        String errorMsg = reader.readLine();
        reader.close();

        //Debug.outNoStack("OSAScript Error (if any): " + errorMsg);

        //Debug.outNoStack(MessageFormat.format("OSAScript execution ended ({0}ms)", new Object[]{String.valueOf(System.currentTimeMillis() - start)}));

        try {
        	osaProcess.destroy();
        } catch (Throwable t) {
        	//ignore
        }
        if (errorMsg != null)
        {
            throw new IOException(errorMsg);
        }

        return line;
    }

    /**
     * Compiles a new AppleScript instance to the specified location
     * @param cmd         Command to compile; do not surround command with extra quotation marks
     * @param destination Destination location of the AppleScript file
     * @return True if compiled successfully
     */
    protected static boolean compileOSAScript(CharSequence cmd, File destination)
    {
        return compileOSAScript(new CharSequence[]{cmd}, destination);
    }

    /**
     * Compiles a new AppleScript instance to the specified location
     * @param cmds Sequence of commands to compile; do not surround command with extra quotation marks
     * @param destination Destination location of the AppleScript file
     * @return True if compiled successfully
     */
    protected static boolean compileOSAScript(CharSequence[] cmds, File destination)
    {
    	/*
        long start = System.currentTimeMillis();
        Debug.outNoStack("Compiling OSAScript: " + destination.getPath());
        for (int i = 0; i < cmds.length; i++)
        {
            Debug.outNoStack("\t" + cmds[i]);
        }
		*/

        String[] cmdargs = new String[2 * cmds.length + 3];
        cmdargs[0] = "osacompile";
        for (int i = 0; i < cmds.length; i++)
        {
            cmdargs[i * 2 + 1] = "-e";
            cmdargs[i * 2 + 2] = String.valueOf(cmds[i]);
        }

        cmdargs[cmdargs.length - 2] = "-o";
        cmdargs[cmdargs.length - 1] = destination.getPath();

        String errorMsg;
        try
        {
            Process osaProcess = performRuntimeExec(cmdargs);

            BufferedReader reader = new BufferedReader(new InputStreamReader(osaProcess.getErrorStream()));
            errorMsg = reader.readLine();
            reader.close();
        }
        catch (IOException e)
        {
            Debug.outNoStack("OSACompile Execution Failed: " + e.getMessage());
            Debug.printStackTrace(e);
            return false;
        }

        //Debug.outNoStack("OSACompile Error (if any): " + errorMsg);

        //Debug.outNoStack(MessageFormat.format("OSACompile execution ended ({0}ms)", new Object[]{String.valueOf(System.currentTimeMillis() - start)}));

        return (errorMsg == null);
    }

    /**
     * @see Runtime#exec(String[])
     */
    protected static Process performRuntimeExec(String[] cmdargs) throws IOException
    {
        try
        {
            return Runtime.getRuntime().exec(cmdargs);
        }
        catch (IOException e)
        {
            Logger.log(new LogAlert(LogAlert.UNREPEATABLE, e.getMessage(), e));
            throw e;
        }
    }

    /**
     * <p>Gets the preferred file browser name</p>
     * <p>Currently supported browsers are Path Finder and Finder. If Path Finder is currently running
     * (not just installed), then "Path Finder is returned; else, "Finder" is returned.</p>
     * @return "Path Finder" if it is currently running; else "Finder"
     */
    private static String getFileBrowserName()
    {
    	return fileBrowserName;
    }

	@Override
	public boolean
	testNativeAvailability(
		String	name )

		throws PlatformManagerException
	{
	    throw new PlatformManagerException("Unsupported capability called on platform manager");
	}

	@Override
	public void
	traceRoute(
		InetAddress							interface_address,
		InetAddress							target,
		PlatformManagerPingCallback			callback )

		throws PlatformManagerException
	{
	    throw new PlatformManagerException("Unsupported capability called on platform manager");
	}

	@Override
	public void
	ping(
		InetAddress							interface_address,
		InetAddress							target,
		PlatformManagerPingCallback			callback )

		throws PlatformManagerException
	{
	    throw new PlatformManagerException("Unsupported capability called on platform manager");
	}

	@Override
	public int
	getMaxOpenFiles()

		throws PlatformManagerException
	{
        LineNumberReader lnr = null;

	    try{
	        Process p =
	        	Runtime.getRuntime().exec(
	        		new String[]{
	        				"/bin/sh",
	        				"-c",
	        				"ulimit -a" });

	        lnr = new LineNumberReader( new InputStreamReader( p.getInputStream()));

	        Map<String,String>	map = new HashMap<>();

	        while( true ){

	        	String	line = lnr.readLine();

	        	if ( line == null ){

	        		break;
	        	}

	        	int	pos1 = line.indexOf( '(' );
	        	int pos2 = line.indexOf( ')', pos1+1 );

	        	String keyword 	= line.substring( 0, pos1 ).trim().toLowerCase();
	        	String value	= line.substring( pos2+1 ).trim();

	        	map.put( keyword, value );
	        }

	        String open_files = map.get( "open files" );

	        if ( open_files != null ){

	        	if ( open_files.equalsIgnoreCase( "unlimited" )){

	        		return( 0 );
	        	}else{
	        		try{
	        			return( Integer.parseInt( open_files ));

	        		}catch( Throwable e ){

	        			Debug.out( "open files invalid: " + open_files );
	        		}
	        	}
	        }
	    }catch( Throwable e ){

	    }finally{

            if ( lnr != null ){

                try{
                	lnr.close();

                }catch( Throwable e ){
                }
            }
        }

	    return( -1 );
	}

    @Override
    public void
    addListener(
    	PlatformManagerListener		listener )
    {
    }

    @Override
    public void
    removeListener(
    	PlatformManagerListener		listener )
    {
    }

    @Override
    public void generate(IndentWriter writer) {
    	writer.println("PlatformManager: MacOSX");
    	try {
    		writer.indent();

    		if (OSXAccess.isLoaded()) {
    			try {
    				writer.println("Version " + getVersion());
    				writer.println("User Data Dir: " + getLocation(LOC_USER_DATA));
    				writer.println("User Doc Dir: " + getLocation(LOC_DOCUMENTS));
    			} catch (PlatformManagerException e) {
    			}
    		} else {
    			writer.println("Not loaded");
    		}

    		writer.println("Computer Name: " + getComputerName());

    		try{
    			writer.println("Max Open Files: " + getMaxOpenFiles());
    		}catch( Throwable e ){
    			writer.println("Max Open Files: " + Debug.getNestedExceptionMessage( e ));
    		}
    	} finally {
    		writer.exdent();
    	}
    }

	// @see com.biglybt.platform.PlatformManager#getAzComputerID()
	public String getAzComputerID() throws PlatformManagerException {
		throw new PlatformManagerException(
				"Unsupported capability called on platform manager");
	}

	/**
	 * If the application is not active causes the application icon at the bottom to bounce until the application becomes active
	 * If the application is already active then this method does nothing.
	 *
	 * Note: This is an undocumented feature from Apple so it's behavior may change without warning
	 *
	 * @param type one of USER_REQUEST_INFO, USER_REQUEST_WARNING
	 */
	@Override
	public void requestUserAttention(int type, Object data)
			throws PlatformManagerException {
		if (type == USER_REQUEST_QUESTION) {
			return;
		}
		try {
			Class<?> claNSApplication = Class.forName("com.apple.eawt.Application");
			Method methGetApplication = claNSApplication.getMethod("getApplication");
			Object app = methGetApplication.invoke(null);

			Method methRequestUserAttention = claNSApplication.getMethod(
					"requestUserAttention", new Class[] {
						Boolean.class
					});
			if (type == USER_REQUEST_INFO) {
				methRequestUserAttention.invoke(app, false);
			} else if (type == USER_REQUEST_WARNING) {
				methRequestUserAttention.invoke(app, true);
			}

		} catch (Exception e) {
			throw new PlatformManagerException("Failed to request user attention", e);
		}

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

	public static void
	main(
		String[]	args )
	{
		try{
			SystemProperties.setApplicationName( "Vuze" );

			// System.out.println( new PlatformManagerImpl().getMaxOpenFiles());

			PlatformManagerImpl pm = new PlatformManagerImpl();

			pm.getRunAtLogin();

			pm.setRunAtLogin( false );
		}catch( Throwable e ){

			e.printStackTrace();
		}
	}
}
