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
import com.biglybt.pifimpl.local.ui.config.ParameterImpl;
import com.biglybt.ui.config.BaseConfigSection;
import com.biglybt.ui.config.ConfigSectionImpl;
import com.biglybt.ui.console.ConsoleConfigSections;
import com.biglybt.ui.console.ConsoleInput;

import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.config.*;

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

	private void search(PrintStream out, String s, boolean isRegex) {
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

				showBox(out, getFriendlyConfigSectionID(section),
						MessageText.getString(section.getSectionNameKey()));

				for (Parameter result : results) {
					printParam(out, section, result, true);
				}
				out.println();
			}
			if (needsBuild) {
				section.deleteConfigSection();
			}
		}
		out.println(count + " found for " + s);
	}

	private void printSection(PrintStream out, String sectionID) {
		List<BaseConfigSection> configSections = ConsoleConfigSections.getInstance().getAllConfigSections(
				false);

		BaseConfigSection section = null;
		for (BaseConfigSection configSection : configSections) {
			if (configSection != null
					&& getFriendlyConfigSectionID(configSection).equalsIgnoreCase(sectionID)) {
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

			showBox(out, getFriendlyConfigSectionID(section),
					MessageText.getString(section.getSectionNameKey()));

			StringBuffer groupIndent = new StringBuffer();
			boolean isFirst = true;

			Stack<ParamGroupInfo> pgInfoStack = new Stack<>();
			ParamGroupInfo pgInfo = new ParamGroupInfo(1, true, null);
			for (Parameter param : params) {
				isFirst = printParam(out, section, param, groupIndent, isFirst, pgInfo,
						pgInfoStack);
			}
			while (groupIndent.length() > 0) {
				groupIndent.replace(0, 2, "");
				endGroup(out, groupIndent, null);
			}
		} finally {
			if (needsBuild) {
				section.deleteConfigSection();
			}
		}
	}

	private void showBox(PrintStream out, String... items) {
		int len = 1;
		for (String item : items) {
			len += item.length() + 2;
		}

		out.print('\u2552');
		outDupChar(out, '\u2550', len);
		out.print('\u2555');
		out.println();

		out.print('\u2502');
		for (int i = 0; i < items.length; i++) {
			if (i > 0) {
				out.print('\u2502');
			}
			out.print(' ');
			out.print(items[i]);
			out.print(' ');
		}
		out.print('\u2502');
		out.println();

		out.print('\u2558');
		outDupChar(out, '\u2550', len);
		out.print('\u255B');

		out.println();
	}

	private static class ParamGroupInfo
	{
		int numParamsLeft;

		boolean visible;

		String title;

		public ParamGroupInfo(int numParamsLeft, boolean visible, String title) {
			this.numParamsLeft = numParamsLeft;
			this.visible = visible;
			this.title = title;
		}
	}

	private boolean printParam(PrintStream out, BaseConfigSection section,
			Parameter param, boolean appendLine) {
		Stack<ParamGroupInfo> pgInfoStack = new Stack<>();
		ParamGroupInfo pgInfo = new ParamGroupInfo(1, true, null);
		return printParam(out, section, param, new StringBuffer(), !appendLine,
				pgInfo, pgInfoStack);
	}

	private boolean printParam(PrintStream out, BaseConfigSection section,
			Parameter param, StringBuffer groupIndent, boolean isFirst, ParamGroupInfo pgInfo,
			Stack<ParamGroupInfo> pgInfoStack) {

		String indent = String.format(FORMAT_PARAMTYPE, ' ');

		if (param instanceof ParameterGroupImpl) {
			String rid = ((ParameterGroupImpl) param).getResourceName();

			pgInfoStack.push(
				new ParamGroupInfo(pgInfo.numParamsLeft, pgInfo.visible, pgInfo.title));

			pgInfo.numParamsLeft = ((ParameterGroupImpl) param).size(false);
			pgInfo.visible = param.isVisible();

			if (param.isVisible() && rid != null) {
				pgInfo.title = MessageText.getString(rid);

				out.println(groupIndent);

				out.print(groupIndent);
				out.print('\u250C');
				outDupChar(out, '\u2500', COL_LEN_PARAMTYPE + COL_LEN_PARAM_KEY + 8);
				out.print('\u0020');
				out.println();

				out.print(groupIndent);
				out.print("\u2502 ");
				out.print(pgInfo.title);
				out.println();

				out.print(groupIndent);
				out.print('\u251C');
				outDupChar(out, '\u2500', COL_LEN_PARAMTYPE + 2);
				out.print('\u252C');
				outDupChar(out, '\u2500', COL_LEN_PARAM_KEY + 2);
				out.print('\u252C');
				outDupChar(out, '\u2500', 2);
				out.print('\u0020');
				out.println();

				groupIndent.append("\u2502 ");
			} else {
				pgInfo.title = null;
			}

			isFirst = true;
			return isFirst;
		}

		boolean endGroup = false;
		if (pgInfo.numParamsLeft > 0) {
			pgInfo.numParamsLeft--;
			endGroup = pgInfo.numParamsLeft == 0;
		}

		try {
			boolean paramEnabled = param.isEnabled();

			if (!param.isVisible() || !pgInfo.visible) {
				return isFirst;
			}
			if ((param instanceof ParameterImpl)
					&& !((ParameterImpl) param).isForUIType(UIInstance.UIT_CONSOLE)) {
				return isFirst;
			}

			if (param instanceof LabelParameter) {
				String labelKey = param.getLabelKey();
				if (labelKey == null || labelKey.isEmpty()) {
					out.print(groupIndent);
					outDupChar(out, ' ', COL_LEN_PARAMTYPE + 1);
					out.print('\u2502');
					out.println();
					return isFirst;
				}
			}

			if (isFirst) {
				isFirst = false;
			} else {
				out.print(groupIndent);
				//outDupChar(out, '\u0020', COL_LEN_PARAMTYPE + 1);
				//out.print('\u253C');
				//outDupChar(out, '\u0020', COL_LEN_KEY + 2);
				//out.print('\u252C');
				//outDupChar(out, '\u0020', 2);
				out.println();
			}

			out.print(groupIndent);

			String paramType = param.getClass().getSimpleName();
			if (paramType.endsWith("Impl")) {
				if (paramType.endsWith("ParameterImpl")) {
					paramType = paramType.substring(0, paramType.length() - 13);
				} else {
					paramType = paramType.substring(0, paramType.length() - 4);
				}
			}
			if (!paramEnabled) {
				paramType += "*";
			}
			print(out, String.format(FORMAT_PARAMTYPE, paramType), paramEnabled);

			out.print(" \u2502 ");

			String key = param.getConfigKeyName();
			if (key != null) {
				print(out, String.format(FORMAT_KEY,
						key.contains(" ") ? "\"" + key + "\"" : key), paramEnabled);

			}

			if (param instanceof PasswordParameter) {
				out.print(" \u2502 ");
				byte[] value = ((PasswordParameter) param).getValue();
				if (value != null && value.length > 0) {
					out.print('<');
					out.print(MessageText.getString("ConfigView.password.isset"));
					out.print('>');
				}
			} else if (param instanceof HyperlinkParameter) {
				String labelText = param.getLabelText();
				if (labelText != null && !labelText.isEmpty()) {
					print(out, labelText, paramEnabled);
					out.print(" \u2502 ");
				}
				String hyperlink = ((HyperlinkParameter) param).getHyperlink();

				String linkTextKey = ((HyperlinkParameter) param).getLinkTextKey();
				if (linkTextKey != null && !linkTextKey.isEmpty()) {
					String linkText = MessageText.getString(linkTextKey);
					if (!linkText.equals(hyperlink)) {
						print(out, linkText, paramEnabled);
						out.print(" \u2502 ");
					}
				}
				print(out, hyperlink, paramEnabled);
				out.println();

				return isFirst;

			} else if (param instanceof ActionParameter) {
				String actionResource = ((ActionParameter) param).getActionResource();
				print(out, MessageText.getString(actionResource), paramEnabled);
				if (paramEnabled) {
					out.print(" \u2502 ");

					// not implemented yet
					out.print("To invoke (not implemented yet): config invoke ");
					out.print(getFriendlyConfigSectionID(section));
					out.print(':');
					out.print(((ActionParameter) param).getActionID());
				}
			} else if (param instanceof LabelParameter) {
				print(out, param.getLabelText().replaceAll("\n",
						"\n" + groupIndent + indent + " \u2502 "), paramEnabled);
				out.println();
				return isFirst;
			} else {
				Object val = param.getValueObject();
				if (val != null && !val.toString().isEmpty()) {
					out.print(" \u2502 ");
					if (param instanceof IntListParameter) {
						print(out, val.toString(), paramEnabled);
						String[] labels = ((IntListParameter) param).getLabels();
						int[] vals = ((IntListParameter) param).getValues();
						int curVal = ((IntListParameter) param).getValue();
						for (int i = 0; i < vals.length; i++) {
							int entry = vals[i];
							if (entry == curVal) {
								print(out, " (", paramEnabled);
								print(out, labels[i] + ")", paramEnabled);
								break;
							}
						}
					} else if (param instanceof StringListParameter) {
						print(out, val.toString(), paramEnabled);
						String[] labels = ((StringListParameter) param).getLabels();
						String[] vals = ((StringListParameter) param).getValues();
						for (int i = 0; i < vals.length; i++) {
							String entry = vals[i];
							if (entry.equals(val)) {
								print(out, " (", paramEnabled);
								print(out, labels[i] + ")", paramEnabled);
								break;
							}
						}
					} else if (val instanceof String) {
						print(out, "\"" + val + "\"", paramEnabled);
					} else {
						print(out, val.toString(), paramEnabled);
					}

				}
			}

			if (param instanceof ParameterWithSuffix) {
				String suffixLabelKey = ((ParameterWithSuffix) param).getSuffixLabelKey();
				if (suffixLabelKey != null) {
					out.print("\t\u2502");
					out.print(MessageText.getString(suffixLabelKey));
				}
			}

			String label = param.getLabelText();
			if (label != null) {
				out.println();

				out.print(groupIndent);
				if (!paramEnabled) {
					print(out, String.format(FORMAT_PARAMTYPE, "*disabled"),
							paramEnabled);
				} else {
					out.print(indent);
				}
				out.print(" \u2502 ");

				print(out,
						label.replaceAll("\n", "\n" + groupIndent + indent + " \u2502 "),
						paramEnabled);
			}

			out.println();
		} finally {

			while (endGroup) {

				if (pgInfo.title != null) {
					isFirst = true;
					groupIndent.replace(0, 2, "");
					endGroup(out, groupIndent, pgInfo.title);
				}

				if (!pgInfoStack.isEmpty()) {
					pgInfo = pgInfoStack.pop();
					endGroup = pgInfo.numParamsLeft < 0;
				} else {
					endGroup = false;
				}
			}
		}
		return isFirst;
	}

	private void endGroup(PrintStream out, StringBuffer groupIndent,
			String pgTitle) {
		out.print(groupIndent);
		out.print('\u2514');
		//outDupChar(out, '\u2500', COL_LEN_PARAMTYPE + 2);
		//out.print('\u2534');
		//outDupChar(out, '\u2500', COL_LEN_KEY + 2);
		//out.print('\u2534');
		//outDupChar(out, '\u2500', 2);
		outDupChar(out, '\u2500', COL_LEN_PARAM_KEY + COL_LEN_PARAMTYPE + 8);
		out.println();

		out.print(groupIndent);
		out.println();
	}

	private void outDupChar(PrintStream os, char c, int count) {
		for (int i = 0; i < count; i++) {
			os.print(c);
		}
	}

	private void print(PrintStream out, String s, boolean enabled) {
		if (enabled) {
			out.print(s);
		} else {
			//out.print("\u001B[37m"); // Light Gray
			out.print("\u001B[2m"); // Faint
			//out.print("\u001B[90m"); // Bright Foreground Black
			out.print(s);
			out.print("\u001B[0m");
		}
	}

	private void printSections(PrintStream out) {
		List<BaseConfigSection> configSections = ConsoleConfigSections.getInstance().getAllConfigSections(true);

		out.print(String.format("%1$-" + COL_LEN_SECTION_KEY + "s", "ID"));
		out.print(" \u2502 ");
		out.print(String.format("%1$-" + COL_LEN_SECTION_NAME + "s", "Name"));
		out.println(" \u2502Min\u2502Max\u2502 Parent");

		outDupChar(out, '\u2500', COL_LEN_SECTION_KEY + 1);
		out.print('\u253C');
		outDupChar(out, '\u2500', COL_LEN_SECTION_NAME + 2);
		out.print('\u253C');
		outDupChar(out, '\u2500', 3);
		out.print('\u253C');
		outDupChar(out, '\u2500', 3);
		out.print('\u253C');
		outDupChar(out, '\u2500', 12);
		out.println();

		for (BaseConfigSection configSection : configSections) {
			if (configSection == null) {
				continue;
			}
			out.print(String.format("%1$-" + COL_LEN_SECTION_KEY + "s",
					getFriendlyConfigSectionID(configSection)));
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

		outDupChar(out, '\u2500', COL_LEN_SECTION_KEY + 1);
		out.print('\u2534');
		outDupChar(out, '\u2500', COL_LEN_SECTION_NAME + 2);
		out.print('\u2534');
		outDupChar(out, '\u2500', 3);
		out.print('\u2534');
		outDupChar(out, '\u2500', 3);
		out.print('\u2534');
		outDupChar(out, '\u2500', 12);
		out.println();
	}

	private String getFriendlyConfigSectionID(BaseConfigSection configSection) {
		String id = configSection.getConfigSectionID();
		final String[] squashSUffixes = new String[] {
			".name",
			".title",
			".title.full"
		};
		for (String squashSUffix : squashSUffixes) {
			if (id.endsWith(squashSUffix)) {
				return id.substring(0, id.length() - squashSUffix.length());
			}
		}
		return id;
	}

	@Override
	public String getCommandDescriptions() {
		return "config";
	}

	@Override
	public void printHelpExtra(PrintStream out, List<String> args) {
		printHelpExtra(out, "config", args);
	}

	public void printHelpExtra(PrintStream out, String command,
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
