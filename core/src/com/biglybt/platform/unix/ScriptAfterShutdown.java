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

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

import com.biglybt.core.config.COConfigurationManager;

public class ScriptAfterShutdown
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

		String extraCmds = COConfigurationManager.getStringParameter(
				"scriptaftershutdown", null);
		if (extraCmds != null) {
			boolean exit = COConfigurationManager.getBooleanParameter(
					"scriptaftershutdown.exit", false);
			if (exit) {
				COConfigurationManager.removeParameter("scriptaftershutdown.exit");
			}
			COConfigurationManager.removeParameter("scriptaftershutdown");
			COConfigurationManager.save();
			sysout.println(extraCmds);
			if (exit) {
				sysout.println("exit");
			}
		} else {
			log("No shutdown tasks to do");
		}
	}

	public static void addExtraCommand(String s) {
		String extraCmds = COConfigurationManager.getStringParameter(
				"scriptaftershutdown", null);
		if (extraCmds == null) {
			extraCmds = s + "\n";
		} else {
			extraCmds += s + "\n";
		}
		COConfigurationManager.setParameter("scriptaftershutdown", extraCmds);
	}

	public static void setRequiresExit(boolean requiresExit) {
		if (requiresExit) {
			COConfigurationManager.setParameter("scriptaftershutdown.exit", true);
		}
	}

	private static void log(String string) {
		sysout.println("echo \"" + string.replaceAll("\"", "\\\"") + "\"");
	}
}
