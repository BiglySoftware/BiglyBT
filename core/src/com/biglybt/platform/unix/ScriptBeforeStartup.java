/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.platform.unix;

import java.io.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.impl.CoreSingleInstanceClient;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.PluginManager;

import javax.swing.*;

public class ScriptBeforeStartup
{
	private static PrintStream sysout;

	public static void main(String[] args) {
		// Set transitory so not everything gets loaded up. (such as the AEDiagnostic's tidy flag)
		System.setProperty("transitory.startup", "1");

		// Since stdout will be in a shell script, redirect any stdout not coming
		// from us to stderr
		sysout = System.out;
		try {
			System.setOut(new PrintStream(new FileOutputStream("/dev/stderr")));
		} catch (FileNotFoundException e) {
		}

		String mi_str = System.getProperty(PluginManager.PR_MULTI_INSTANCE);
		boolean mi = mi_str != null && mi_str.equalsIgnoreCase("true");

		if (!mi) {
			boolean argsSent = new CoreSingleInstanceClient().sendArgs(args, 500);
			if (argsSent) {
				// the client was open..
				sysout.println("exit");

				return;
			}
		}

		// If the after shutdown script didn't run or crapped out, then
		// don't run again..
		String scriptAfterShutdown = COConfigurationManager.getStringParameter(
				"scriptaftershutdown", null);

		COConfigurationManager.removeParameter("scriptaftershutdown.exit");
		COConfigurationManager.removeParameter("scriptaftershutdown");
		COConfigurationManager.save();
		if (scriptAfterShutdown != null) {
			log("Script after " + Constants.APP_NAME
					+ " shutdown did not run.. running now");

			sysout.println(scriptAfterShutdown);

			if (!scriptAfterShutdown.contains("$0")) {
				// doesn't have a restart.. add one
				sysout.println("echo \"Restarting " + Constants.APP_NAME + "..\"");
				sysout.println("$0\n");
			}
			// exit is a requirement
			sysout.println("exit");

			return;
		}

		boolean useGTK2 = COConfigurationManager.getBooleanParameter("ui.useGTK2");
		if (useGTK2) {
			sysout.println("export SWT_GTK3=0");
		}

		try {
			Class claDisplay = Class.forName("org.eclipse.swt.widgets.Display");
			claDisplay.newInstance();
		} catch (Throwable e) {
			boolean useSwing = true;
			for (String arg: args) {
				if (arg.equals("-h") || arg.startsWith("-u")) {
					useSwing = false;
					break;
				}
			}


			String error = e.toString();
			if ((e instanceof UnsatisfiedLinkError)
					&& !new File("/etc/gtk-3.0").isDirectory()) {
				error = "No GTK3 found. " + (e.toString());
			}
			System.err.println("SWT check failed with: " + error);
			if (!useSwing) {
				return;
			}

			if (System.getenv("TERM") != null) {
				try {
					int i = JOptionPane.showConfirmDialog(null,
							"GUI could not be loaded. " + error + "\nLaunch in console mode instead?",
							"Can't Launch " + Constants.APP_NAME, JOptionPane.YES_NO_OPTION);
					if (i != JOptionPane.OK_OPTION) {
						sysout.println("exit");
						return;
					}
				} catch (Throwable t) {
				}

				sysout.println("OTHER_PARAMS=\"${OTHER_PARAMS} --ui=console\"");
			} else {
				try {
					JOptionPane.showMessageDialog(null,
							"GUI could not be loaded. " + error,
							"Can't Launch " + Constants.APP_NAME, JOptionPane.ERROR_MESSAGE);
					sysout.println("exit");
				} catch (Throwable t) {
					System.err.println(t.toString());
					sysout.println("OTHER_PARAMS=\"${OTHER_PARAMS} --ui=console\"");
				}
			}
		}
	}

	private static void log(String string) {
		String s = string.replaceAll("\"", "\\\"").replaceAll("[\r\n]+", " -- ");
		sysout.println("echo \"" + s + "\"");
	}
}
