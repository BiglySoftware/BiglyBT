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

package com.biglybt.ui.common;

import java.io.*;
import java.util.Date;
import java.util.LinkedHashMap;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.util.Constants;
import com.biglybt.ui.Main;
import org.apache.commons.cli.*;
import org.apache.commons.cli.Option.Builder;


/**
 * @author tobi
 *
 */
public class UIConst
{
	public static Date startTime;

	public static LinkedHashMap<String, IUserInterface> UIS;


	public static synchronized boolean startUI(String ui) {
		if (UIS.containsKey(ui))
			return false;
		IUserInterface uif = UserInterfaceFactory.getUI(ui);
		UIS.put(ui, uif);

		uif.init(false, true);
		uif.coreCreated(CoreFactory.getSingleton());

		Main.setNewUI(uif);
		return true;
	}

	public static Options buildOptions() {
		Options options = getDefaultOptions();
		for (IUserInterface ui : UIConst.UIS.values()) {
			ui.buildCommandLine(options);
		}
		return options;
	}

	private static Options getDefaultOptions() {
		Options options = new Options();

		Builder builder;

		options.addOption("h", "help", false, "Show this help.");

		builder = Option.builder("u").longOpt("ui").argName("uis").hasArg().desc(
				"Run <uis>. ',' separated list of user interfaces to run (swt, console, telnet). The first one given will respond to requests without determinable source UI (e.g. further torrents added via command line).");
		options.addOption(builder.build());

		builder = Option.builder().longOpt("closedown").desc(
				"shutdown an existing instance of BiglyBT");
		options.addOption(builder.build());

		builder = Option.builder().longOpt("shutdown").desc(
				"shutdown an existing instance of BiglyBT");
		options.addOption(builder.build());

		builder = Option.builder().longOpt("restart").desc(
				"restart an existing instance of BiglyBT");
		options.addOption(builder.build());

		
		builder = Option.builder().longOpt("open").desc(
				"show the BiglyBT interface");
		options.addOption(builder.build());

		builder = Option.builder().longOpt("share").desc(
				"share a resource");
		options.addOption(builder.build());

		builder = Option.builder().longOpt("savepath").argName("path").hasArg().desc(
				"specify the. Absolute save location for the torrent(s)");
		options.addOption(builder.build());
		
		
		
		if (Constants.isWindows) {
			builder = Option.builder("console").desc(
					"(Windows) keeps a console window open while " + Constants.APP_NAME + " is running");
			options.addOption(builder.build());
		}

		return options;
	}

	public static CommandLine buildCommandLine(Options options, String[] args) {
		try {
			return new DefaultParser().parse(options, args, true);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static String processArgs(CommandLine commands, Options options,
			String[] args) {
		StringBuilder sb = new StringBuilder();
		final PrintStream oldOut = System.out;
		try {
			ByteArrayOutputStream newOut = new ByteArrayOutputStream();
			System.setOut(new PrintStreamTempCapture(newOut));

			for (IUserInterface ui : UIConst.UIS.values()) {
				args = ui.processArgs(commands, args);
				if (args == null) {
					break;
				}
				try {
					commands = new DefaultParser().parse(options, args, true);
				} catch (ParseException e) {
					commands = null;
				}
			}

			sb.append(new String(newOut.toByteArray(), Constants.UTF_8));
			return sb.toString();
		} finally {
			System.setOut(oldOut);
		}
	}

	public static void removeUI(String ui) {
		if (UIS != null) {
			UIS.remove(ui);
		}
	}
	
	// Used to easily identity PrintStream when looking at System.out 
	private static class PrintStreamTempCapture extends PrintStream {
		public PrintStreamTempCapture(OutputStream out) {
			super(out);
		}
	}
}
