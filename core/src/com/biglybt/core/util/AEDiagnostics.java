/*
 * Created on 22-Sep-2004
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

package com.biglybt.core.util;

import java.io.*;
import java.util.*;

import com.biglybt.core.Core;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.Logger;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.logging.LoggerChannel;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.model.BasicPluginViewModel;
import com.biglybt.pif.ui.components.*;
import com.biglybt.platform.PlatformManager;
import com.biglybt.platform.PlatformManagerCapabilities;
import com.biglybt.platform.PlatformManagerFactory;

/**
 * @author parg
 */

public class
AEDiagnostics
{
	// these can not be set true and have a usable AZ!
	public static final boolean	ALWAYS_PASS_HASH_CHECKS			= false;
	public static final boolean	USE_DUMMY_FILE_DATA				= false;
	public static final boolean	CHECK_DUMMY_FILE_DATA			= false;

	// these can safely be set true, things will work just slower
	public static final boolean	DEBUG_MONITOR_SEM_USAGE			= false;
    public static final boolean DEBUG_THREADS			        = true; // Leave this on by default for the moment

	public static final boolean	TRACE_DIRECT_BYTE_BUFFERS		= false;
	public static final boolean	TRACE_DBB_POOL_USAGE			= false;
	public static final boolean	PRINT_DBB_POOL_USAGE			= false;

    public static final boolean TRACE_TCP_TRANSPORT_STATS       = false;
    public static final boolean TRACE_CONNECTION_DROPS          = false;

	private static final int	MAX_FILE_SIZE_DEFAULT;	// get two of these per logger type
	private static int[]		MAX_FILE_SIZE_ACTUAL = { 0 };

	static{
		if ( ALWAYS_PASS_HASH_CHECKS ){
			System.out.println( "**** Always passing hash checks ****" );
		}
		if ( USE_DUMMY_FILE_DATA ){
			System.out.println( "**** Using dummy file data ****" );
		}
		if ( CHECK_DUMMY_FILE_DATA ){
			System.out.println( "**** Checking dummy file data ****" );
		}
		if ( DEBUG_MONITOR_SEM_USAGE ){
			System.out.println( "**** AEMonitor/AESemaphore debug on ****" );
		}
		if ( TRACE_DIRECT_BYTE_BUFFERS ){
			System.out.println( "**** DirectByteBuffer tracing on ****" );
		}
		if ( TRACE_DBB_POOL_USAGE ){
			System.out.println( "**** DirectByteBufferPool tracing on ****" );
		}
		if ( PRINT_DBB_POOL_USAGE ){
			System.out.println( "**** DirectByteBufferPool printing on ****" );
		}
		if ( TRACE_TCP_TRANSPORT_STATS ){
		  System.out.println( "**** TCP_TRANSPORT_STATS tracing on ****" );
		}

		int maxFileSize = 256 * 1024;
		try {
			String logSize = System.getProperty("diag.logsize", null);
			if (logSize != null) {
				if (logSize.toLowerCase().endsWith("m")) {
					maxFileSize = Integer.parseInt(logSize.substring(0,
							logSize.length() - 1)) * 1024 * 1024;
				} else {
					maxFileSize = Integer.parseInt(logSize);
				}
			}
		} catch (Throwable t) {
		}
		MAX_FILE_SIZE_ACTUAL[0] = MAX_FILE_SIZE_DEFAULT = maxFileSize;
	}


	private static final String	CONFIG_KEY	= "diagnostics.tidy_close";

	private static File	debug_dir;

	private static File	debug_save_dir;

	private static boolean	started_up;
	private static volatile boolean	startup_complete;
	private static boolean	enable_pending_writes;

	private static final Map<String,AEDiagnosticsLogger>		loggers	= new HashMap<>();

	protected static boolean	logging_enabled;
	protected static boolean	loggers_enabled;
	
	protected static boolean	loggers_disabled;	// all these stupid vars.

	private static final List<AEDiagnosticsEvidenceGenerator>		evidence_generators	= new ArrayList<>();
	private static final Map<AEDiagnosticsEvidenceGenerator, Void>		weak_evidence_generators	= new WeakHashMap<>();

	private static final AESemaphore	dump_check_done_sem = new AESemaphore( "dumpcheckcomplete" );

	public static synchronized void
	startup(
		boolean	_enable_pending )
	{
		if ( started_up ){

			return;
		}

		started_up	= true;

		enable_pending_writes = _enable_pending;

		try{
				// Minimize risk of loading to much when in transitory startup mode

			boolean transitoryStartup = System.getProperty("transitory.startup", "0").equals("1");

			if ( transitoryStartup ){

					// no xxx_?.log logging for you!

				loggers_enabled = false;

					// skip tidy check and more!

				return;
			}

			debug_dir		= FileUtil.getUserFile( "logs" );

			debug_save_dir	= FileUtil.newFile( debug_dir, "save" );
			
			COConfigurationManager.addAndFireParameterListeners(
				new String[]{
					"Logger.Enabled",
					"Logger.DebugFiles.Enabled",		// don't think used anymore, always true...
					"Logger.DebugFiles.Enabled.Force",
					"Logger.DebugFiles.Disable",		// config to disable all debug files regardless
					"Logger.DebugFiles.SizeKB",
				},
				new ParameterListener()
				{
					@Override
					public void
					parameterChanged(
						String parameterName)
					{
						logging_enabled = COConfigurationManager.getBooleanParameter( "Logger.Enabled" );

						loggers_enabled = logging_enabled && COConfigurationManager.getBooleanParameter( "Logger.DebugFiles.Enabled");
						
						loggers_disabled = COConfigurationManager.getBooleanParameter( "Logger.DebugFiles.Disable");

						if ( !loggers_enabled ){

							boolean skipCVSCheck = System.getProperty("skip.loggers.enabled.cvscheck", "0").equals("1");
							loggers_enabled = (!skipCVSCheck && Constants.IS_CVS_VERSION) || COConfigurationManager.getBooleanParameter( "Logger.DebugFiles.Enabled.Force" );
						}
						
						if ( System.getProperty("diag.logsize", null) == null ){
							
							int kb = COConfigurationManager.getIntParameter("Logger.DebugFiles.SizeKB", 0 )*1024;
							
							if ( kb > 0 ){
								
								MAX_FILE_SIZE_ACTUAL[0] = kb;
							}
						}
					}
				});

			boolean	was_tidy	= COConfigurationManager.getBooleanParameter( CONFIG_KEY );

			new AEThread2( "asyncify", true )
			{
				@Override
				public void
				run()
				{
					SimpleTimer.addEvent("AEDiagnostics:logCleaner",SystemTime.getCurrentTime() + 60000
							+ RandomUtils.nextInt(15000), new TimerEventPerformer() {
						@Override
						public void perform(TimerEvent event) {
							cleanOldLogs();
						}
					});
				}
			}.start();

			if ( debug_dir.exists()){

				boolean save_logs = System.getProperty( "az.logging.save.debug", "true" ).equals( "true" );

				long	now = SystemTime.getCurrentTime();

				File[] files = debug_dir.listFiles();

				if ( files != null ){

					boolean	file_found	= false;

					for (int i=0;i<files.length;i++){

						File	file = files[i];

						if ( file.isDirectory()){

							continue;
						}

						if ( !was_tidy ){

							file_found = true;

							if ( save_logs ){

								if ( !debug_save_dir.exists()){

									debug_save_dir.mkdir();
								}

								FileUtil.copyFile( file, FileUtil.newFile( debug_save_dir, now + "_" + file.getName()));
							}
						}
					}

					if ( file_found ){

						Logger.logTextResource(new LogAlert(LogAlert.UNREPEATABLE,
								LogAlert.AT_WARNING, "diagnostics.log_found"),
								new String[] { debug_save_dir.toString() });
					}
				}
			}else{

				debug_dir.mkdir();
			}

			AEJavaManagement.initialise();

		}catch( Throwable e ){

				// with webui we don't have the file stuff so this fails with class not found

			if ( !(e instanceof NoClassDefFoundError )){

				Debug.printStackTrace( e );
			}
		}finally{

			startup_complete	= true;
		}
	}

	public static void
	dumpThreads()
	{
		AEJavaManagement.dumpThreads();
	}

	public static void
	dumpThreads(
		IndentWriter writer )
	{
		AEJavaManagement.dumpThreads( writer );
	}
	
	public static String
	getThreadInfo(
		Thread		thread )
	{
		return( AEJavaManagement.getThreadInfo( thread ));
	}

	/**
	 *
	 */
	private static synchronized void cleanOldLogs() {
		try {
			long now = SystemTime.getCurrentTime();

			// clear out any really old files in the save-dir

			File[] files = debug_save_dir.listFiles();

			if (files != null) {

				for (int i = 0; i < files.length; i++) {

					File file = files[i];

					if (!file.isDirectory()) {

						long last_modified = file.lastModified();

						if (now - last_modified > 10 * 24 * 60 * 60 * 1000L) {

							file.delete();
						}
					}
				}
			}

		} catch (Exception e) {
		}
	}

	public static boolean
	isStartupComplete()
	{
		return( startup_complete );
	}
	
	public static void
	postStartup(
		Core		core )
	{
		  PluginInterface plugin_interface = core.getPluginManager().getDefaultPluginInterface();

		  LoggerChannel log = plugin_interface.getLogger().getChannel( "JVM Info" );

		  UIManager	ui_manager = plugin_interface.getUIManager();

		  BasicPluginViewModel model = ui_manager.createBasicPluginViewModel( "log.jvm.info" );

		  model.getActivity().setVisible( false );
		  model.getProgress().setVisible( false );

		  model.attachLoggerChannel( log );
		  
		  model.getLogArea().addRefreshListener(
			  new UIComponent.RefreshListener(){
				  private String last_line = null;
				  
				  @Override
				  public void 
				  refresh()
				  {
					  List<String>	lines = AEJavaManagement.getMemoryHistory();

					  int line_num = lines.size();
							  
					  if ( line_num == 0 ){
						  
						  return;
					  }
					  
					  int pos = lines.indexOf( last_line );
					  
					  if ( pos == line_num-1 ){
						  
						  return;
					  }
					  
					  String content = "";

					  for ( String line: lines.subList(pos<0?0:pos+1,lines.size())){
	
						  content += line + "\n";
					  }
					
					  last_line = lines.get(lines.size()-1);
					  
					  model.getLogArea().appendText( content );
				  }
		  });
	}

	public static File
	getLogDir()
	{
		startup( false );

		return( debug_dir );
	}

	public static synchronized void
	flushPendingLogs()
	{
		for ( AEDiagnosticsLogger logger: loggers.values()){

			logger.writePending();
		}

		enable_pending_writes = false;
	}

	public static synchronized AEDiagnosticsLogger
	getLogger(
		String		name )
	{
		AEDiagnosticsLogger	logger = loggers.get(name);

		if ( logger == null ){

			startup( false );

			logger	= new AEDiagnosticsLogger( debug_dir, name, MAX_FILE_SIZE_ACTUAL, !enable_pending_writes );

			loggers.put( name, logger );
		}

		return( logger );
	}

	public static void
	logWithStack(
		String	logger_name,
		String	str )
	{
		log( logger_name, str + ": " + Debug.getCompressedStackTrace(1));
	}

	public static void
	log(
		String	logger_name,
		String	str )
	{
		getLogger( logger_name ).log( str );
	}

	public static void
	markDirty()
	{
		try{

			COConfigurationManager.setParameter( CONFIG_KEY, false );

			COConfigurationManager.save();

		}catch( Throwable e ){

			Debug.printStackTrace( e );
		}
	}

	public static boolean
	isDirty()
	{
		return( !COConfigurationManager.getBooleanParameter( CONFIG_KEY ));
	}

	public static void
	markClean()
	{
		try{
			COConfigurationManager.setParameter( CONFIG_KEY, true );

			COConfigurationManager.save();

		}catch( Throwable e ){

			Debug.printStackTrace( e );
		}
	}

	private static final String[][]
	   	bad_dlls = {
			{	"niphk", 			"y", },
	   		{	"nvappfilter", 		"y", },
	   		{	"netdog", 			"y", },
	   		{	"vlsp", 			"y", },
	   		{	"imon", 			"y", },
	   		{	"sarah", 			"y", },
	   		{	"MxAVLsp", 			"y", },
	   		{	"mclsp", 			"y", },
	   		{	"radhslib", 		"y", },
	   		{	"winsflt",			"y", },
	   		{	"nl_lsp",			"y", },
	   		{	"AxShlex",			"y", },
	   		{	"iFW_Xfilter",		"y", },
	   		{	"gapsp",			"y", },
	   		{	"WSOCKHK",			"n", },
	   		{	"InjHook12",		"n", },
	   		{	"FPServiceProvider","n", },
	   		{	"SBLSP.dll",		"y"  },
	   		{	"nvLsp.dll",		"y"	 },
	};

	public static void
	checkDumpsAndNatives()
	{
		try{
			PlatformManager	p_man = PlatformManagerFactory.getPlatformManager();

			if ( 	p_man.getPlatformType() == PlatformManager.PT_WINDOWS &&
					p_man.hasCapability( PlatformManagerCapabilities.TestNativeAvailability )){

				for (int i=0;i<bad_dlls.length;i++){

					String	dll 	= bad_dlls[i][0];
					String	load	= bad_dlls[i][1];

					if ( load.equalsIgnoreCase( "n" )){

						continue;
					}

					if ( !COConfigurationManager.getBooleanParameter( "platform.win32.dll_found." + dll, false )){

						try{
							if ( p_man.testNativeAvailability( dll + ".dll" )){

								COConfigurationManager.setParameter( "platform.win32.dll_found." + dll, true );

								String	detail = MessageText.getString( "platform.win32.baddll." + dll );

								Logger.logTextResource(
										new LogAlert(
												LogAlert.REPEATABLE,
												LogAlert.AT_WARNING,
												"platform.win32.baddll.info" ),
										new String[]{ dll + ".dll", detail });
							}

						}catch( Throwable e ){

							Debug.printStackTrace(e);
						}
					}
				}
			}

			List<File>	fdirs_to_check = new ArrayList<>();

			fdirs_to_check.add( FileUtil.newFile( SystemProperties.getApplicationPath()));

			try{
				File temp_file = File.createTempFile( "AZU", "tmp" );

				fdirs_to_check.add( temp_file.getParentFile());

				temp_file.delete();

			}catch( Throwable e ){

			}

			File	most_recent_dump 	= null;
			long	most_recent_time	= 0;

			for ( File dir: fdirs_to_check ){

				if ( dir.canRead()){

					File[]	files = dir.listFiles(
							new FilenameFilter() {

								@Override
								public boolean
								accept(
									File dir,
									String name)
								{
									return( name.startsWith( "hs_err_pid" ) && name.endsWith( ".log" ));
								}
							});

					if ( files != null ){

						long	now = SystemTime.getCurrentTime();

						long	one_week_ago = now - 7*24*60*60*1000;

						for (int i=0;i<files.length;i++){

							File	f = files[i];

							long	last_mod = f.lastModified();

							if ( last_mod > most_recent_time && last_mod > one_week_ago){

								most_recent_dump 	= f;
								most_recent_time	= last_mod;
							}
						}
					}
				}
			}

			if ( most_recent_dump!= null ){

				long	last_done =
					COConfigurationManager.getLongParameter( "diagnostics.dump.lasttime", 0 );

				if ( last_done < most_recent_time ){

					COConfigurationManager.setParameter( "diagnostics.dump.lasttime", most_recent_time );

					analyseDump( most_recent_dump );
				}
			}
		}catch( Throwable e ){

			Debug.printStackTrace(e);

		}finally{

			dump_check_done_sem.releaseForever();
		}
	}

	protected static void
	analyseDump(
		File	file )
	{
		System.out.println( "Analysing " + file );

		try{
			LineNumberReader lnr = new LineNumberReader( new InputStreamReader( FileUtil.newFileInputStream( file )));

			try{
				boolean	float_excep		= false;
				boolean	swt_crash		= false;
				boolean	browser_crash	= false;

				String[]	bad_dlls_uc = new String[bad_dlls.length];

				for (int i=0;i<bad_dlls.length;i++){

					String	dll 	= bad_dlls[i][0];

					bad_dlls_uc[i] = (dll + ".dll" ).toUpperCase();
				}

				String	alcohol_dll = "AxShlex";

				List<String>	matches = new ArrayList<>();

				while( true ){

					String	line = lnr.readLine();

					if ( line == null ){

						break;
					}

					line = line.toUpperCase();

					if (line.contains("EXCEPTION_FLT")){

						float_excep	= true;

					}else{

						if ( 	line.startsWith( "# C" ) && line.contains( "[SWT-WIN32" ) ||
								line.startsWith( "# C" ) && line.contains( "[LIBSWT" )){

							swt_crash = true;

						}else if ( line.contains( "CURRENT THREAD" ) && line.contains( "SWT THREAD" )){

							swt_crash = true;

						}else if ( 	line.startsWith( "# C" ) &&
									( 	line.contains( "[IEFRAME" ) ||
										line.contains( "[JSCRIPT" ) ||
										line.contains( "[FLASH" ) ||
										line.contains( "[MSHTML" ))){

							swt_crash = browser_crash = true;

						}else if ( 	( line.startsWith( "J " ) && line.contains( "SWT.BROWSER")) ||
									( line.startsWith( "C " ) && line.contains( "[IEFRAME" )) ||
									( line.startsWith( "C " ) && line.contains( "[MSHTML" )) ||
									( line.startsWith( "C " ) && line.contains( "[FLASH" )) ||
									( line.startsWith( "C " ) && line.contains( "[JSCRIPT" ))){

							browser_crash = true;
						}

						for (int i=0;i<bad_dlls_uc.length;i++){

							String b_uc = bad_dlls_uc[i];

							if (line.contains(b_uc)){

								String	dll = bad_dlls[i][0];

								if ( dll.equals( alcohol_dll )){

									if ( float_excep ){

										matches.add( dll );
									}

								}else{

									matches.add( dll );
								}
							}
						}
					}
				}

				for (int i=0;i<matches.size();i++){

					String	dll = matches.get(i);

					String	detail = MessageText.getString( "platform.win32.baddll." + dll );

					Logger.logTextResource(
							new LogAlert(
									LogAlert.REPEATABLE,
									LogAlert.AT_WARNING,
									"platform.win32.baddll.info" ),
							new String[]{ dll + ".dll", detail });
				}

				if ( swt_crash && browser_crash ){

					if ( Constants.isWindows || Constants.isLinux ){

						if ( !COConfigurationManager.getBooleanParameter( "browser.internal.disable", false )){

							COConfigurationManager.setParameter( "browser.internal.disable", true );

							COConfigurationManager.save();

							Logger.logTextResource(
									new LogAlert(
											LogAlert.REPEATABLE,
											LogAlert.AT_WARNING,
											"browser.internal.auto.disabled" ));
						}
					}
				}
			}finally{

				lnr.close();
			}
		}catch( Throwable e){

			Debug.printStackTrace( e );
		}
	}

	public static void
	waitForDumpChecks(
		long	max_wait )
	{
		dump_check_done_sem.reserve( max_wait );
	}

	public static void
	addWeakEvidenceGenerator(
			AEDiagnosticsEvidenceGenerator	gen )
	{
		synchronized( evidence_generators ){

			weak_evidence_generators.put( gen, null );
		}
	}

	public static void
	addEvidenceGenerator(
		AEDiagnosticsEvidenceGenerator	gen )
	{
		synchronized( evidence_generators ){

			evidence_generators.add( gen );
		}
	}

	public static void
	removeEvidenceGenerator(
		AEDiagnosticsEvidenceGenerator	gen )
	{
		synchronized( evidence_generators ){

			evidence_generators.remove( gen );
		}
	}

	public static void
	generateEvidence(
		PrintWriter		_writer )
	{
		IndentWriter	writer = new IndentWriter( _writer );

		synchronized( evidence_generators ){

			for (AEDiagnosticsEvidenceGenerator gen : evidence_generators) {

				try {
					gen.generate(writer);

				} catch (Throwable e) {

					e.printStackTrace(_writer);
				}
			}

			for (AEDiagnosticsEvidenceGenerator gen : weak_evidence_generators.keySet()) {

				try {
					gen.generate(writer);

				} catch (Throwable e) {

					e.printStackTrace(_writer);
				}
			}
		}

		writer.println( "Memory" );

		try{
			writer.indent();

			Runtime rt = Runtime.getRuntime();

			writer.println( "max=" + rt.maxMemory() + ",total=" + rt.totalMemory() + ",free=" + rt.freeMemory());

		}finally{

			writer.exdent();
		}
	}
	
	/*
	public static void
	main(
		String[]	args )
	{
		analyseDump( new File( "c:\\temp\\hs_err_pid1376539.log" ));
	}
	*/
}
