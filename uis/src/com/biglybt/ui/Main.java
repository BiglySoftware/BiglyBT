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

package com.biglybt.ui;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.StringTokenizer;

import org.apache.commons.cli.*;
import org.apache.commons.cli.Option.Builder;

import com.biglybt.core.Core;
import com.biglybt.core.CoreException;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreLifecycleAdapter;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.impl.CoreSingleInstanceClient;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.Wiki;
import com.biglybt.launcher.Launcher;
import com.biglybt.ui.common.IUserInterface;
import com.biglybt.ui.common.StartServer;
import com.biglybt.ui.common.UIConst;
import com.biglybt.ui.common.UserInterfaceFactory;

/**
 * This is the main of all mains!
 *
 * @author TuxPaper
 * @created May 17, 2007
 *
 */
public class Main
{

	public static final boolean DEBUG_STARTUPTIME = System.getProperty(
			"DEBUG_STARTUPTIME", "0").equals("1");

	public static String DEFAULT_UI = "swt";

	public static StartServer startServer = null;

	protected static Core core;

	private static CommandLine commands;

	private static volatile boolean stopping;

	private static volatile boolean stopped;

	private static long lastDebugTime;

	private static boolean neverStarted;

	private static IUserInterface newUI;

	public static void main(String[] args) {
		if (DEBUG_STARTUPTIME) {
			lastDebugTime = System.currentTimeMillis();
		}
		if (Launcher.checkAndLaunch(Main.class, args))
			return;

		// This *has* to be done first as it sets system properties that are read and cached by Java

		COConfigurationManager.preInitialise();

		if (DEBUG_STARTUPTIME) {
			logTime("args: " + Arrays.toString(args));
		}

		Thread.currentThread().setName(Constants.APP_NAME);

		String mi_str = System.getProperty("MULTI_INSTANCE");

		boolean mi = mi_str != null && mi_str.equalsIgnoreCase("true");

		if (DEBUG_STARTUPTIME) {
			logTime("preInit");
		}

		try {

			// Build a list of UIS
			Options uiOptions = new Options();
			Builder builder = Option.builder("u").longOpt("ui").argName(
					"uis").hasArg();
			uiOptions.addOption(builder.build());
			if (Constants.isWindows) {
				builder = Option.builder("console");
				uiOptions.addOption(builder.build());
			}
			try {
				CommandLine commandLine = new DefaultParser().parse(uiOptions, args,
						true);
				buildUIList(commandLine);
			} catch (ParseException e) {
			}

			// Add UIS Command Line Options
			Options options = UIConst.buildOptions();

			commands = UIConst.buildCommandLine(options, args);

			if (commands == null) {
				System.exit(0);
			}

			if (DEBUG_STARTUPTIME) {
				logTime("buildCommandLine");
			}

			// don't create core until we know we really need it

			if (!mi) {
				startServer = new StartServer();

				if (startServer.getServerState() == StartServer.STATE_FAULTY) {
					System.setProperty("transitory.startup", "1");

					//looks like there's already a process listening on 127.0.0.1:<port>
					//attempt to pass args to existing instance

					// First, do some OSX magic because parameters are passed via OpenDocument API and other callbacks
					args = CocoaMagic(args);

					if (!new CoreSingleInstanceClient().sendArgs(args, 5000)) {

						//arg passing attempt failed, so start core anyway

						String msg = "There appears to be another process already listening on socket [127.0.0.1:"
								+ Constants.INSTANCE_PORT
								+ "].\n\nLocate and terminate the other program or change the control port - <a href=\""
								             + Wiki.COMMANDLINE_OPTIONS__CHANGING_CONTROL_PORT
								             + "\">see the wiki for details</a>.";

						System.err.println(msg);
						return;

					} else {
						return; // we sent params to other core, don't init the core
					}
				}

				if (commands.hasOption("closedown") || commands.hasOption("shutdown")
						|| commands.hasOption("restart")) {
					return;
				}
				if (DEBUG_STARTUPTIME) {
					logTime("StartServer");
				}
			} else {
				System.out.println("MULTI_INSTANCE enabled");

			}

			// Special Exit if user ask for help
			if (commands != null && commands.hasOption('h')) {
				HelpFormatter hf = new HelpFormatter();
				hf.setOptionComparator(null);
				hf.printHelp("[options] [torrent [torrent ...]]", options);
				if (startServer != null) {
					startServer.stopIt();
				}
				System.exit(0);
			}

			boolean isFirst = true;
			for (IUserInterface ui : UIConst.UIS.values()) {
				ui.init(isFirst, (UIConst.UIS.size() > 1));
				isFirst = false;
			}

			neverStarted = true;
			core = CoreFactory.create();

			if (DEBUG_STARTUPTIME) {
				logTime("Core Create");
			}

			for (IUserInterface ui : UIConst.UIS.values()) {
				ui.coreCreated(core);
			}

			if (DEBUG_STARTUPTIME) {
				logTime("UIConst.set" + Constants.AZUREUS_NAME + "Core");
			}

			UIConst.processArgs(commands, options, args);

			if (DEBUG_STARTUPTIME) {
				logTime("UIConst.processArgs");
			}
			
			if ( startServer != null ) {
				startServer.setDaemon(true);
				startServer.start();
			}
			
			neverStarted = !core.isStarted();
			core.addLifecycleListener(new CoreLifecycleAdapter() {
				@Override
				public void started(Core core) {
					Main.neverStarted = false;
				}

				@Override
				public void stopping(Core core) {
					Main.stopping = true;
				}

				@Override
				public void stopped(Core core) {
					if ( startServer != null ) {
						startServer.stopIt();
					}
					Main.stopped = true;
				}
			});

			for (IUserInterface ui : UIConst.UIS.values()) {
				ui.takeMainThread();
				if (stopping) {
					break;
				}
			}

			if (neverStarted) {
				if (DEBUG_STARTUPTIME) {
					logTime("takeMainThread");
				}
				core.start();
				if (DEBUG_STARTUPTIME) {
					logTime("coreStart");
				}
			}
			if (!stopping) {
				// no one took the main thread!
				while (!stopped) {
					try {
						Thread.sleep(200);

						// Case: start console ui, then start swt ui from console
						//       (not case "-u console,Swt")
						if (newUI != null) {
							IUserInterface threadTaker = newUI;
							newUI = null;
							threadTaker.takeMainThread();
						}
					} catch (InterruptedException e) {
					}
				}
			}

		} catch (CoreException e) {

			System.out.println("Start fails:");

			e.printStackTrace();
		}
		if (DEBUG_STARTUPTIME) {
			logTime("DONE");
		}
	}

	private static void logTime(String s) {
		System.out.println(System.currentTimeMillis() - lastDebugTime + "ms] " + s);
		lastDebugTime = System.currentTimeMillis();
	}

	private static void buildUIList(CommandLine commands) {
		if (UIConst.UIS == null) {
			UIConst.UIS = new LinkedHashMap<>();
		}
		if (commands == null) {
			UIConst.UIS.put(DEFAULT_UI, UserInterfaceFactory.getUI(DEFAULT_UI));
			return;
		}

		if (commands.hasOption('u')) {
			String uinames = commands.getOptionValue('u');
			if (uinames.indexOf(',') == -1) {
				if (!UIConst.UIS.containsKey(uinames)) {
					UIConst.UIS.put(uinames, UserInterfaceFactory.getUI(uinames));
				}
			} else {
				StringTokenizer stok = new StringTokenizer(uinames, ",");
				while (stok.hasMoreTokens()) {
					String uin = stok.nextToken();
					if (!UIConst.UIS.containsKey(uin)) {
						UIConst.UIS.put(uin, UserInterfaceFactory.getUI(uin));
					}
				}
			}
		} else {
			if (UIConst.UIS.isEmpty() && !commands.hasOption('c')
					&& !commands.hasOption('e')) {
				UIConst.UIS.put(DEFAULT_UI, UserInterfaceFactory.getUI(DEFAULT_UI));
			}
		}
	}

	private static String[] CocoaMagic(String[] args) {
		if (!Constants.isOSX) {
			return args;
		}
		try {

			// hack to tell OSXAccess static initializer to only initialize a light version
			System.setProperty("osxaccess.light", "1");

			Class<?> claOSXAccess = Class.forName(
					"com.biglybt.platform.macosx.access.jnilib.OSXAccess");
			if (claOSXAccess != null) {
				Method method = claOSXAccess.getMethod("runLight", new Class[] {
					String[].class
				});
				Object invoke = method.invoke(null, new Object[] {
					args
				});
				return (String[]) invoke;
			}

		} catch (Throwable e) {

			Debug.printStackTrace(e);
		}
		return args;
	}

	public static void setNewUI(IUserInterface newUI) {
		Main.newUI = newUI;
	}
}
