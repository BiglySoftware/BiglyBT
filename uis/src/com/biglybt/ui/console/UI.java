/* Written and copyright 2001-2003 Tobias Minich.
 * Distributed under the GNU General Public License; see the README file.
 * This code comes with NO WARRANTY.
 *
 *
 * Main.java
 *
 * Created on 22. August 2003, 00:04
 */

package com.biglybt.ui.console;

import java.io.*;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.log4j.Appender;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.varia.DenyAllFilter;

import com.biglybt.core.Core;
import com.biglybt.core.CoreLifecycleAdapter;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.torrentdownloader.TorrentDownloaderFactory;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.SystemProperties;
import com.biglybt.core.util.TorrentUtils;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.*;
import com.biglybt.pif.ui.model.BasicPluginViewModel;
import com.biglybt.pif.ui.toolbar.UIToolBarManager;
import com.biglybt.ui.common.IUserInterface;
import com.biglybt.ui.common.UIConst;
import com.biglybt.ui.common.UIInstanceBase;
import com.biglybt.ui.console.multiuser.UserManager;
import com.biglybt.ui.console.multiuser.commands.UserCommand;

/**
 *
 * @author  Tobias Minich
 */
public class UI
	extends com.biglybt.ui.common.UITemplateHeadless
	implements IUserInterface, UIInstanceFactory, UIInstanceBase,
	UIManagerEventListener
{

	private ConsoleInput console = null;

	/** Creates a new instance of Main */
	/*public UI() {
	}*/

	public static void initRootLogger() {
		if (Logger.getRootLogger().getAppender("ConsoleAppender") == null) {
			Appender app;
			app = new ConsoleAppender(
					new PatternLayout(PatternLayout.TTCC_CONVERSION_PATTERN));
			app.setName("ConsoleAppender");
			app.addFilter(new DenyAllFilter()); //'log off' by default
			Logger.getRootLogger().addAppender(app);
		}
	}

	@Override
	public void init(boolean first, boolean others) {
		super.init(first, others);
		System.setProperty("java.awt.headless", "true");

		initRootLogger();
	}

	public void buildCommandLine(Options options) {
		Option.Builder builder;

		builder = Option.builder("e").longOpt("exec").hasArg().argName("file").desc(
				"Execute script file. The file should end with 'logout', otherwise the parser thread doesn't stop.");
		options.addOption(builder.build());

		builder = Option.builder("c").longOpt("command").hasArg().argName(
				"command").desc(
						"Execute single script command. Try '-c help' for help on commands.");
		options.addOption(builder.build());
	}

	@Override
	public String[] processArgs(CommandLine commands, String[] args) {
		if (commands != null && (args.length > 0 || core != null)) {
			Class clConsoleInput;
			Constructor conConsoleInput = null;
			try {
				clConsoleInput = Class.forName("com.biglybt.ui.console.ConsoleInput");

				// change this and you'll need to change the parameters below....

				Class params[] = {
					String.class,
					Core.class,
					Reader.class,
					PrintStream.class,
					Boolean.class
				};

				conConsoleInput = clConsoleInput.getConstructor(params);
			} catch (Exception e) {
				e.printStackTrace();
			}
			if (commands.hasOption('e')) {
				if (conConsoleInput != null) {
					try {
						Object params[] = {
							commands.getOptionValue('e'),
							core,
							new FileReader(commands.getOptionValue('e')),
							System.out,
							Boolean.FALSE
						};
						ConsoleInput o = (ConsoleInput) conConsoleInput.newInstance(params);
						Thread.sleep(5);
						int wait = 3;
						while (o.running && wait-- > 0) {
							Thread.sleep(10);
						}
					} catch (java.io.FileNotFoundException e) {
						Logger.getLogger("biglybt").error(
								"Script file not found: " + e.toString());
					} catch (Exception e) {
						Logger.getLogger("biglybt").error(
								"Error invocating the script processor: " + e.toString());
					}
				} else
					Logger.getLogger("biglybt").error(
							"ConsoleInput class not found. You need the console ui package to use '-e'");
			}

			if (commands.hasOption('c')) {
				if (conConsoleInput != null) {
					String comm = commands.getOptionValue('c');
					comm += "\nlogout\n";
					Object params[] = {
						commands.getOptionValue('c'),
						core,
						new StringReader(comm),
						System.out,
						Boolean.FALSE
					};
					try {
						ConsoleInput o = (ConsoleInput) conConsoleInput.newInstance(params);
						Thread.sleep(5);
						int wait = 3;
						while (o.running && wait-- > 0) {
							Thread.sleep(10);
						}
					} catch (Exception e) {
						Logger.getLogger("biglybt").error(
								"Error invocating the script processor: " + e.toString());
					}
				} else
					Logger.getLogger("biglybt").error(
							"ConsoleInput class not found. You need the console ui package to use '-e'");
			}

			if (commands.hasOption("closedown") || commands.hasOption("shutdown")) {
				if (core != null) {
					core.stop();
					return null;
				}
			}

			String save_path = null;
			
			if ( commands.hasOption("savepath" )){
				
				save_path = commands.getOptionValue( "savepath" );
				
				File f = new File( save_path );
				
				if ( f.exists()){
					
					if ( f.isFile()){
						
						Logger.getLogger("biglybt").error( "--savepath value '" + f.getAbsolutePath() + "' is a file, ignoring ");
						
						save_path = null;
					}
				}else{
					
					f.mkdirs();
					
					if ( !f.exists()){
						
						Logger.getLogger("biglybt").error( "--savepath value '" + f.getAbsolutePath() + "' can't be created, ignoring ");

						save_path = null;
					}
				}
				
				if ( save_path != null ){
					
					save_path = f.getAbsolutePath();
				}
			}
			
			String[] rest = commands.getArgs();
			for (String arg : rest) {
				openTorrent(arg, save_path );
			}
		} else {
			Logger.getLogger("biglybt").error("No commands to process");
		}

		return args;
	}

	@Override
	public String getUIType() {
		return (UIT_CONSOLE);
	}

	@Override
	public void coreCreated(Core core) {
		super.coreCreated(core);

		SimpleDateFormat temp = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss");

		UIConst.startTime = new Date();

		Logger.getLogger("biglybt").fatal(
				Constants.APP_NAME + " started at " + temp.format(UIConst.startTime));

		core.addLifecycleListener(new CoreLifecycleAdapter() {

			@Override
			public void started(Core core) {
				startUI();
			}

			@Override
			public void stopped(Core core) {
				super.stopped(core);
				SimpleDateFormat temp = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss");
				Logger.getLogger("biglybt").fatal(
						Constants.APP_NAME + " stopped at " + temp.format(new Date()));

			}
		});
		if (core.isStarted()) {
			startUI();
		}
	}

	private void startUI() {

		boolean created_console = false;

		if (console == null || !console.isAlive()) {
//      ConsoleInput.printconsolehelp(System.out);
			System.out.println();

			PrintStream this_out = System.out;

			// Unless a system property tells us not to, we'll take stdout and stderr offline.
			if (!"on".equals(
					System.getProperty(SystemProperties.SYSPROP_CONSOLE_NOISY))
					&& isFirst()) {
				// We'll hide any output to stdout or stderr - we don't want to litter our
				// view.
				PrintStream ps = new PrintStream(new java.io.OutputStream() {
					@Override
					public void write(int c) {
					}

					@Override
					public void write(byte[] b, int i1, int i2) {
					}
				});
				System.setOut(ps);
				System.setErr(ps);
				com.biglybt.core.logging.Logger.allowLoggingToStdErr(false);
			}
			console = new ConsoleInput("Main", core, System.in, this_out,
					Boolean.TRUE);
			console.printwelcome();
			console.printconsolehelp();
			created_console = true;
		}

		PluginInterface pi = core.getPluginManager().getDefaultPluginInterface();
		UIManager ui_manager = pi.getUIManager();

		ui_manager.addUIEventListener(this);

		try {
			ui_manager.attachUI(this);
		} catch (UIException e) {
			e.printStackTrace();
		}
		TorrentDownloaderFactory.initManager(core.getGlobalManager(), true, true );

		if (created_console && System.getProperty(
				SystemProperties.SYSPROP_CONSOLE_MULTIUSER) != null) {
			UserManager manager = UserManager.getInstance(pi);
			console.registerCommand(new UserCommand(manager));
		}
	}

	public void openRemoteTorrent(String url) {
		if (console != null) {
			console.downloadRemoteTorrent(url);
			return;
		}
		if (console != null) {
			console.out.println("Downloading torrent from url: " + url);
		}
		TorrentDownloaderFactory.downloadManaged(url);
		return;
	}

	public void openTorrent(String fileName, String save_path) {
		String uc_filename = fileName.toUpperCase(Locale.US);

		boolean is_remote = uc_filename.startsWith("HTTP://")
				|| uc_filename.startsWith("HTTPS://")
				|| uc_filename.startsWith("MAGNET:");

		if (console != null) {

//  		System.out.println("NOT NULL CONSOLE. CAN PASS STRAIGHT TO IT!");

			if (is_remote) {

				console.out.println("Downloading torrent from url: " + fileName);

				if ( save_path == null ){
					console.downloadRemoteTorrent(fileName);
				}else{
					console.downloadRemoteTorrent(fileName, save_path );
				}
			} else {

				console.out.println("Open Torrent " + fileName);
				
				if ( save_path == null ){
					console.downloadTorrent(fileName);
				}else{
					console.downloadTorrent(fileName,save_path);
				}
			}
			return;
		} else {
//  		System.out.println("NULL CONSOLE");
		}

		if (is_remote) {
			if (console != null) {
				console.out.println("Downloading torrent from url: " + fileName);
			}
			
			if ( save_path == null ){
			
				TorrentDownloaderFactory.downloadManaged(fileName);
				
			}else{
				
				TorrentDownloaderFactory.downloadToLocationManaged(fileName, save_path );
			}
			
			return;
		}

		try {
			if (!TorrentUtils.isTorrentFile(fileName)) {//$NON-NLS-1$
				Logger.getLogger("biglybt.ui.console").error(
						fileName + " doesn't seem to be a torrent file. Not added.");
				return;
			}
		} catch (Exception e) {
			Logger.getLogger("biglybt.ui.console").error("Something is wrong with "
					+ fileName + ". Not added. (Reason: " + e.getMessage() + ")");
			return;
		}
		if (core.getGlobalManager() != null) {
			try {
				String downloadDir = save_path!=null?save_path:COConfigurationManager.getDirectoryParameter("Default save path");
				if (console != null) {
					console.out.println(
							"Adding torrent: " + fileName + " and saving to " + downloadDir);
				}
				core.getGlobalManager().addDownloadManager(fileName, downloadDir);
			} catch (Exception e) {
				Logger.getLogger("biglybt.ui.console").error(
						"The torrent " + fileName + " could not be added.", e);
			}
		}
	}

	@Override
	public UIInstance getInstance(PluginInterface plugin_interface) {
		return (this);
	}

	@Override
	public void detach()

			throws UIException {
	}

	@Override
	public void dispose() {

	}

	@Override
	public boolean eventOccurred(UIManagerEvent event) {
		Object data = event.getData();

		switch (event.getType()) {

			case UIManagerEvent.ET_SHOW_TEXT_MESSAGE: // data is String[] - title, message, text
			{
				String[] bits = (String[]) data;

				for (int i = 0; i < bits.length; i++) {

					console.out.println(bits[i]);
				}

				break;
			}
			case UIManagerEvent.ET_OPEN_TORRENT_VIA_FILE: // data is File
			{
				openTorrent(((File) data).toString(), null );

				break;
			}
			case UIManagerEvent.ET_OPEN_TORRENT_VIA_URL: // data is Object[]{URL,URL,Boolean} - { torrent_url, referrer url, auto_download}
			{
				openRemoteTorrent(((URL) ((Object[]) data)[0]).toExternalForm());

				break;
			}
			case UIManagerEvent.ET_PLUGIN_VIEW_MODEL_CREATED: // data is PluginViewModel (or subtype)
			{
				break;
			}
			case UIManagerEvent.ET_COPY_TO_CLIPBOARD: // data is String
			{
				break;
			}
			case UIManagerEvent.ET_PLUGIN_VIEW_MODEL_DESTROYED: // data is PluginViewModel (or subtype)
			{
				break;
			}
			case UIManagerEvent.ET_OPEN_URL: // data is URL
			{
				break;
			}
			case UIManagerEvent.ET_SHOW_CONFIG_SECTION: // data is String - section id
			{
				event.setResult(Boolean.FALSE);

				break;
			}
			default: {
				//if (console != null && console.out != null)

				//console.out.println( "Unrecognised UI event '" + event.getType() + "'" );
			}
		}

		return (true);
	}

	@Override
	public int promptUser(String title, String text, String[] options,
			int defaultOption) {
		console.out.println("Prompt: " + title);
		console.out.println(text);

		String sOptions = "Options: ";
		for (int i = 0; i < options.length; i++) {
			if (i != 0) {
				sOptions += ", ";
			}
			sOptions += "[" + i + "]" + options[i];
		}

		console.out.println(sOptions);

		console.out.println(
				"WARNING: Option [" + defaultOption + "] automatically selected. "
						+ "Console UI devs need to implement this function!");

		return defaultOption;
	}

	@Override
	public void promptUser(String title, String text, String[] options, int defaultOption, UIMessageListener listener) {
		console.out.println("Prompt: " + title);
		console.out.println(text);

		String sOptions = "Options: ";
		for (int i = 0; i < options.length; i++) {
			if (i != 0) {
				sOptions += ", ";
			}
			sOptions += "[" + i + "]" + options[i];
		}

		console.out.println(sOptions);

		console.out.println(
				"WARNING: Option [" + defaultOption + "] automatically selected. "
						+ "Console UI devs need to implement this function!");
		listener.UIMessageClosed(defaultOption);
	}

	@Override
	public boolean openView(BasicPluginViewModel model) {
		// TODO Auto-generated method stub
		return false;
	}

	/** Not yet supported. **/
	@Override
	public UIInputReceiver getInputReceiver() {
		return null;
	}

	@Override
	public UIMessage createMessage() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public UIToolBarManager getToolBarManager() {
		return null;
	}

	@Override
	public void unload(PluginInterface pi) {
	}
}
