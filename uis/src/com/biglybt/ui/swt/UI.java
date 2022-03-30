/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.ui.swt;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.biglybt.ui.common.UIConst;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.eclipse.swt.SWT;

import com.biglybt.core.Core;
import com.biglybt.core.CoreComponent;
import com.biglybt.core.CoreLifecycleAdapter;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.UrlUtils;
import com.biglybt.pif.update.UpdateInstaller;
import com.biglybt.pif.update.UpdateManager;
import com.biglybt.platform.PlatformManagerFactory;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.UITemplate;
import com.biglybt.ui.swt.mainwindow.TorrentOpener;
import com.biglybt.ui.swt.sharing.ShareUtils;

/**
 * Created by TuxPaper on 7/4/2017.
 */
public class UI
	extends UITemplate
{
	private static final LogIDs LOGID = LogIDs.GUI;

	private static volatile boolean useSystemTheme;
	
	private static boolean isFirstUI;

	
	public static boolean
	canUseSystemTheme()
	{
		return(	Constants.isOSX && SWT.getVersion() >= 4924 || // 4.12RC2
				Constants.isWindows10OrHigher && SWT.getVersion() >= 4948 );
	}
	
	static{
					
		COConfigurationManager.addAndFireParameterListener(
			"Use System Theme",
			(n)->{
				
				useSystemTheme = COConfigurationManager.getBooleanParameter( "Use System Theme" );
			
				if ( useSystemTheme && !canUseSystemTheme()){
					
					useSystemTheme = false;		
					
					COConfigurationManager.setParameter( "Use System Theme", false );
					
					return;
				}
				
				try{
					PlatformManagerFactory.getPlatformManager().setUseSystemTheme( useSystemTheme );
					
				}catch( Throwable e ){
					
					useSystemTheme = false;
					
					Debug.out( e );
				}
				
				if ( Constants.isOSX ){

					System.setProperty( "org.eclipse.swt.display.useSystemTheme", useSystemTheme?"true":"false" );
				}
			});
	}
	
	protected final AEMonitor this_mon = new AEMonitor("swt.UI");

	protected List queued_torrents = new ArrayList();

	protected boolean queueTorrents = true;

	public UI() {
		super();
	}

	public static boolean
	useSystemTheme()
	{
		return( useSystemTheme );
	}
	
	protected static boolean isURI(String file_name) {
		String file_name_lower = file_name.toLowerCase();

		return (file_name_lower.startsWith("http:")
				|| file_name_lower.startsWith("https:")
				|| file_name_lower.startsWith("magnet:")
				|| file_name_lower.startsWith("maggot:")
				|| file_name_lower.startsWith("bc:")
				|| file_name_lower.startsWith("biglybt:")
				|| file_name_lower.startsWith("bctp:")
				|| file_name_lower.startsWith("dht:"));
	}

	@Override
	public void init(boolean first, boolean others) {
		super.init(first, others);
		isFirstUI = first;
	}

	@Override
	public void coreCreated(Core core) {
		super.coreCreated(core);
		UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
		if (uiFunctions != null) {
			openQueuedTorrents();
		} else {
			core.addLifecycleListener(new CoreLifecycleAdapter() {
				@Override
				public void componentCreated(Core core, CoreComponent component) {
					if (component instanceof UIFunctionsSWT) {

						openQueuedTorrents();
						core.removeLifecycleListener(this);
					}
				}
			});
		}
	}

	@Override
	public void takeMainThread() {
		new Initializer(core, null);
		UIConst.removeUI("swt");
	}

	@Override
	public void buildCommandLine(Options options) {

	}

	@Override
	public String[] 
	processArgs(
		CommandLine commands, String[] args )
	{
		boolean showMainWindow = args.length == 0;	// no args, always show

		if ( commands.hasOption("closedown") || commands.hasOption("shutdown")){

			// discard any pending updates as we need to shutdown immediately (this
			// is called from installer to close running instance)

			try {
				UpdateManager um = core.getPluginManager().getDefaultPluginInterface().getUpdateManager();

				UpdateInstaller[] installers = um.getInstallers();

				for (UpdateInstaller installer : installers) {

					installer.destroy();
				}
			} catch (Throwable e) {
			}

			UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();

			if (uiFunctions != null) {

				uiFunctions.dispose(false);
			}

			return null;

		}

		if ( commands.hasOption("restart")){

			UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();

			if (uiFunctions != null) {

				uiFunctions.dispose(true);
			}

			return null;
		}

		boolean isAddingStuff 	= false;
		boolean isOpen			= true;

		if (commands.hasOption("share")) {
			isAddingStuff = true;

			isOpen = false;
		}

		if (commands.hasOption("open")) {
			isAddingStuff = true;
		}
		
		String save_path = null;
		
		if ( commands.hasOption("savepath" )){
			
			save_path = commands.getOptionValue( "savepath" );
			
			File f = new File( save_path );
			
			if ( f.exists()){
				
				if ( f.isFile()){
										
					Logger.log(new LogAlert(LogAlert.REPEATABLE, LogAlert.AT_ERROR,
								"StartServer: --savepath value ' + f.getAbsolutePath() + ' is a file, ignoring"));
					
					save_path = null;
				}
			}else{
				
				f.mkdirs();
				
				if ( !f.exists()){
					
					Logger.log(new LogAlert(LogAlert.REPEATABLE, LogAlert.AT_ERROR,
							"StartServer: --savepath value ' + f.getAbsolutePath() + ' can't be created, ignoring"));

					save_path = null;
				}
			}
			
			if ( save_path != null ){
				
				save_path = f.getAbsolutePath();
			}
		}

		String[] rest = commands.getArgs();

		for (int i = 0; i < rest.length; i++) {

			String filename = rest[i];

			File file = new File(filename);

			boolean isURI;
			if (!file.exists() && !isURI(filename)) {

				String magnet_uri = UrlUtils.normaliseMagnetURI(filename);

				isURI = magnet_uri != null;
				if (isURI) {

					filename = magnet_uri;
				}
			} else {
				isURI = isURI(filename);
			}

			if (isURI) {

				if (Logger.isEnabled())
					Logger.log(new LogEvent(LOGID,
							"StartServer: args[" + i + "] handling as a URI: " + filename));

			} else {

				try {

					if (!file.exists()) {

						throw (new Exception("File '" + file + "' not found"));
					}

					filename = file.getCanonicalPath();

					Logger.log(new LogEvent(LOGID, "StartServer: file = " + filename));

				} catch (Throwable e) {

					Logger.log(new LogAlert(LogAlert.REPEATABLE, LogAlert.AT_ERROR,
							"Failed to access torrent file '" + filename
									+ "'. Ensure sufficient temporary "
									+ "file space available (check browser cache usage)."));
				}
			}

			boolean queued = false;

			try {
				this_mon.enter();

				if (queueTorrents) {

					queued_torrents.add(new Object[] {
						filename,
						Boolean.valueOf(isOpen),
						save_path,
					});

					queued = true;
				}
			} finally {

				this_mon.exit();
			}

			if (!queued) {

				handleFile(filename, isOpen, save_path);
			}
		}

		if (	showMainWindow || 
				( isAddingStuff && COConfigurationManager.getBooleanParameter("Activate Window On External Download"))){
			
			UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
			
			if (uiFunctions != null) {
				uiFunctions.bringToFront();
			}
		}
		
		return args;
	}

	protected void handleFile(String file_name, boolean open, String save_path ) {
		try {

			if (open) {

				Map<String,Object> options = new HashMap<String, Object>();
				
				if ( save_path != null ){
					
					options.put( UIFunctions.OTO_DEFAULT_SAVE_PATH, save_path );
				}
				
				TorrentOpener.openTorrent(file_name, options );

			} else {

				File f = new File(file_name);

				if (f.isDirectory()) {

					ShareUtils.shareDir(file_name);

				} else {

					ShareUtils.shareFile(file_name);
				}
			}
		} catch (Throwable e) {

			Debug.printStackTrace(e);
		}
	}

	protected void openQueuedTorrents() {
		try {
			this_mon.enter();

			if (!queueTorrents) {
				return;
			}

			queueTorrents = false;

		} finally {

			this_mon.exit();
		}

		for (Object queued_torrent : queued_torrents) {

			Object[] entry = (Object[]) queued_torrent;

			String file_name = (String) entry[0];
			boolean open = ((Boolean) entry[1]).booleanValue();
			String save_path = (String)entry[2];
			
			handleFile(file_name, open, save_path );
		}
		queued_torrents = null;
	}

	public static boolean isFirstUI() {
		return isFirstUI;
	}
}
