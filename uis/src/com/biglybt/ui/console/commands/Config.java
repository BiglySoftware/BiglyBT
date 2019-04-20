/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
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

package com.biglybt.ui.console.commands;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.regex.Pattern;

import com.biglybt.core.internat.MessageText;
import com.biglybt.pifimpl.local.ui.config.ParameterGroupImpl;
import com.biglybt.ui.config.BaseConfigSection;
import com.biglybt.ui.config.ConfigSectionImpl;
import com.biglybt.ui.console.ConsoleConfigSections;
import com.biglybt.ui.console.ConsoleInput;
import com.biglybt.ui.console.util.PrintUtils;
import com.biglybt.ui.console.util.PrintUtils.ParamGroupInfo;

import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.config.Parameter;

public class Config
	extends IConsoleCommand
{

	public static final int COL_LEN_SECTION_KEY = 30;

	public static final int COL_LEN_SECTION_NAME = 30;

	public static final int COL_LEN_PARAM_KEY = 50;

	public static final String FORMAT_KEY = "%1$-" + COL_LEN_PARAM_KEY + "s";

	public static final int COL_LEN_PARAMTYPE = 11;

	public static final String FORMAT_PARAMTYPE = "%1$-" + COL_LEN_PARAMTYPE
			+ "s";

	public Config() {
		super("config", "cfg");
	}

	@Override
	public void execute(String commandName, ConsoleInput console,
			List<String> args) {
		if (args.isEmpty()) {
			printHelpExtra(console.out, commandName, null);
			return;
		}

		String action = args.get(0).toLowerCase();
		switch (action) {
			case "sections": {
				printSections(console.out);
				break;
			}
			case "s":
			case "section": {
				if (args.size() < 2) {
					console.out.println("Missing ID");
					break;
				}
				printSection(console.out, args.get(1));
				break;
			}
			case "find": {
				if (args.size() < 2) {
					console.out.println("Missing Text");
					break;
				}
				StringBuilder sb = new StringBuilder();
				int start = 1;
				String first = args.get(start);
				boolean isRegex = first.equals("regex") && args.size() > 2;
				if (isRegex) {
					start++;
					first = args.get(start);
				}
				sb.append(first);
				start++;
				for (int i = start; i < args.size(); i++) {
					sb.append(' ');
					sb.append(args.get(i));
				}
				search(console.out, sb.toString(), isRegex);
				break;
			}
			default: {
				printSection(console.out, action);
				break;
			}
		}
	}

	private static void search(PrintStream out, String s, boolean isRegex) {
		List<BaseConfigSection> sections = ConsoleConfigSections.getInstance().getAllConfigSections(
				true);
		int count = 0;
		for (BaseConfigSection section : sections) {
			boolean needsBuild = !section.isBuilt();
			if (needsBuild) {
				section.build();
				section.postBuild();
			}

			Pattern regex = isRegex ? Pattern.compile(s) : s.endsWith("*")
					? Pattern.compile("^\\Q" + s.substring(0, s.length() - 1) + "\\E.*",
							Pattern.CASE_INSENSITIVE)
					: Pattern.compile("\\Q" + s + "\\E", Pattern.CASE_INSENSITIVE);
			List<Parameter> results = section.search(regex);
			if (!results.isEmpty()) {
				count += results.size();

				PrintUtils.showBox(out, PrintUtils.getFriendlyConfigSectionID(section),
						MessageText.getString(section.getSectionNameKey()));

				for (Parameter result : results) {
					PrintUtils.printParam(out, section, false, result, true);
				}
				out.println();
			}
			if (needsBuild) {
				section.deleteConfigSection();
			}
		}
		out.println(count + " found for " + s);
	}

	private static void printSection(PrintStream out, String sectionID) {
		List<BaseConfigSection> configSections = ConsoleConfigSections.getInstance().getAllConfigSections(
				false);

		BaseConfigSection section = null;
		for (BaseConfigSection configSection : configSections) {
			if (configSection != null && PrintUtils.getFriendlyConfigSectionID(
					configSection).equalsIgnoreCase(sectionID)) {
				section = configSection;
				break;
			}
		}
		if (section == null) {
			out.println("No section " + sectionID);
			return;
		}
		boolean needsBuild = !section.isBuilt();
		if (needsBuild) {
			section.build();
			section.postBuild();
		}
		try {
			Parameter[] paramArray = section.getParamArray();
			List<Parameter> params = new ArrayList<>();
			for (Parameter param : paramArray) {
				if (param instanceof ParameterGroupImpl) {
					params.add(params.size() - ((ParameterGroupImpl) param).size(true),
							param);
				} else {
					params.add(param);
				}
			}

			PrintUtils.showBox(out, PrintUtils.getFriendlyConfigSectionID(section),
					MessageText.getString(section.getSectionNameKey()));

			StringBuffer groupIndent = new StringBuffer();
			boolean isFirst = true;

			Stack<ParamGroupInfo> pgInfoStack = new Stack<>();
			ParamGroupInfo pgInfo = new ParamGroupInfo(1, true, null);
			for (Parameter param : params) {
				isFirst = PrintUtils.printParam(out, section, param, groupIndent,
						isFirst, pgInfo, pgInfoStack);
			}
			while (groupIndent.length() > 0) {
				groupIndent.replace(0, 2, "");
				PrintUtils.endGroup(out, groupIndent, null);
			}
		} finally {
			if (needsBuild) {
				section.deleteConfigSection();
			}
		}
	}

	private static void printSections(PrintStream out) {
		List<BaseConfigSection> configSections = ConsoleConfigSections.getInstance().getAllConfigSections(
				true);

		out.print(String.format("%1$-" + COL_LEN_SECTION_KEY + "s", "ID"));
		out.print(" \u2502 ");
		out.print(String.format("%1$-" + COL_LEN_SECTION_NAME + "s", "Name"));
		out.println(" \u2502Min\u2502Max\u2502 Parent");

		PrintUtils.outDupChar(out, '\u2500', COL_LEN_SECTION_KEY + 1);
		out.print('\u253C');
		PrintUtils.outDupChar(out, '\u2500', COL_LEN_SECTION_NAME + 2);
		out.print('\u253C');
		PrintUtils.outDupChar(out, '\u2500', 3);
		out.print('\u253C');
		PrintUtils.outDupChar(out, '\u2500', 3);
		out.print('\u253C');
		PrintUtils.outDupChar(out, '\u2500', 12);
		out.println();

		for (BaseConfigSection configSection : configSections) {
			if (configSection == null) {
				continue;
			}
			out.print(String.format("%1$-" + COL_LEN_SECTION_KEY + "s",
					PrintUtils.getFriendlyConfigSectionID(configSection)));
			out.print(" \u2502 ");
			out.print(String.format("%1$-" + COL_LEN_SECTION_NAME + "s",
					MessageText.getString(configSection.getSectionNameKey())));
			out.print(" \u2502 ");
			out.print(configSection.getMinUserMode());
			out.print(" \u2502 ");
			out.print(configSection.getMaxUserMode());
			out.print(" \u2502 ");
			String parentSectionID = configSection.getParentSectionID();
			if (!ConfigSection.SECTION_ROOT.equals(parentSectionID)) {
				out.print(MessageText.getString(
						ConfigSectionImpl.getSectionNameKey(parentSectionID)));
			}
			out.println();
		}

		PrintUtils.outDupChar(out, '\u2500', COL_LEN_SECTION_KEY + 1);
		out.print('\u2534');
		PrintUtils.outDupChar(out, '\u2500', COL_LEN_SECTION_NAME + 2);
		out.print('\u2534');
		PrintUtils.outDupChar(out, '\u2500', 3);
		out.print('\u2534');
		PrintUtils.outDupChar(out, '\u2500', 3);
		out.print('\u2534');
		PrintUtils.outDupChar(out, '\u2500', 12);
		out.println();
	}

	@Override
	public String getCommandDescriptions() {
		return "config";
	}

	@Override
	public void printHelpExtra(PrintStream out, List<String> args) {
		printHelpExtra(out, "config", args);
	}

	public static void printHelpExtra(PrintStream out, String command,
			List<String> args) {
		String[] commands = {
			"sections",
			"section <section id>",
			"find <text>",
			"find <text>*",
			"find regex <regex>"
		};

		String[] extra = {
			"Lists config sections",
			"Lists parameters in config section",
			"Find parameters containing text (case insensitive)",
			"Find parameters starting with text (case insensitive)",
			"Find parameters using regex. Case sensitive, use (?i) for insensitive"
		};

		for (int i = 0; i < commands.length; i++) {
			out.print(command);
			out.print(' ');
			out.print(String.format("%1$-30s", commands[i]));
			out.print(' ');
			out.println(extra[i]);
		}

	}
}
