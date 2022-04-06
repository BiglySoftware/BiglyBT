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

package com.biglybt.ui.config;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.Map.Entry;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.logging.impl.FileLogging;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.stats.CoreStats;
import com.biglybt.core.util.AEDiagnostics;
import com.biglybt.core.util.AEThread2;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.IndentWriter;
import com.biglybt.pifimpl.local.ui.config.*;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;

import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.config.Parameter;
import com.biglybt.pif.update.UpdateException;
import com.biglybt.pif.update.UpdateInstaller;
import com.biglybt.pif.update.UpdateInstallerListener;

import static com.biglybt.core.config.ConfigKeys.Logging.*;

public class ConfigSectionLogging
	extends ConfigSectionImpl
{
	public static final String SECTION_ID = "logging";

	private static final LogIDs LOGID = LogIDs.GUI;

	private static final int[] logFileSizes = {
		1,
		2,
		3,
		4,
		5,
		6,
		7,
		8,
		9,
		10,
		15,
		20,
		25,
		30,
		40,
		50,
		75,
		100,
		200,
		300,
		500
	};

	public ConfigSectionLogging() {
		super(SECTION_ID, ConfigSection.SECTION_ROOT);
	}

	@Override
	public void build() {

		BooleanParameterImpl enable_logger = new BooleanParameterImpl(
				BCFG_LOGGER_ENABLED, "ConfigView.section.logging.loggerenable");
		add(enable_logger);

		// row

		BooleanParameterImpl enableFileLogging = new BooleanParameterImpl(
				BCFG_LOGGING_ENABLE, "ConfigView.section.logging.enable");
		add(enableFileLogging);
		enableFileLogging.setIndent(1, true);

		List<Parameter> listFileLogging = new ArrayList<>();

		// row

		DirectoryParameterImpl pathParameter = new DirectoryParameterImpl(
				SCFG_LOGGING_DIR, "ConfigView.section.logging.logdir");
		add(pathParameter, listFileLogging);
		//pathParameter.setWidthInCharacters(12);
		pathParameter.setDialogTitleKey(
				"ConfigView.section.logging.choosedefaultsavepath");

		final String[] lmLabels = new String[logFileSizes.length];
		final int[] lmValues = new int[logFileSizes.length];
		for (int i = 0; i < logFileSizes.length; i++) {
			int num = logFileSizes[i];
			lmLabels[i] = " " + num + " MB";
			lmValues[i] = num;
		}

		IntListParameterImpl paramMaxSize = new IntListParameterImpl(
				ICFG_LOGGING_MAX_SIZE, "ConfigView.section.logging.maxsize", lmValues,
				lmLabels);
		add(paramMaxSize, listFileLogging);

		StringParameterImpl timeStamp = new StringParameterImpl(
				SCFG_LOGGING_TIMESTAMP, "ConfigView.section.logging.timestamp");
		add(timeStamp, Parameter.MODE_ADVANCED, listFileLogging);
		timeStamp.setWidthInCharacters(20);

		// FileLogging filter, consisting of a List of types (info, warning, error)
		// and a checkbox Table of component IDs.

		final int[] logTypes = {
			LogEvent.LT_INFORMATION,
			LogEvent.LT_WARNING,
			LogEvent.LT_ERROR
		};
		final LogIDs[] logIDs = FileLogging.configurableLOGIDs;

		List<Parameter> listFileFilter = new ArrayList<>();

		add("t0", new LabelParameterImpl(""), listFileFilter);
		for (int i = 0; i < logTypes.length; i++) {
			add("t" + (i + 1),
					new LabelParameterImpl("ConfigView.section.logging.log" + i + "type"),
					listFileFilter);
		}
		final String sFilterPrefix = "ConfigView.section.logging.filter";
		for (LogIDs logID : logIDs) {
			LabelParameterImpl title = new LabelParameterImpl("");
			add(logID.toString(), title, listFileFilter);
			title.setLabelText(
					MessageText.getString(sFilterPrefix + "." + logID, logID.toString()));

			for (int logType : logTypes) {
				BooleanParameterImpl check = new BooleanParameterImpl(
						"bLog." + logType + "." + logID, "");
				add(check, listFileFilter);
			}
		}

		ParameterGroupImpl pgFileFilter = new ParameterGroupImpl(sFilterPrefix,
				listFileFilter);
		add("pgFileFilter", pgFileFilter, listFileLogging);
		pgFileFilter.setNumberOfColumns(logTypes.length + 1);

		ParameterGroupImpl pgFileLogging = new ParameterGroupImpl(null,
				listFileLogging);
		add("pgFileLogging", pgFileLogging);
		pgFileLogging.setIndent(3, false);

		enableFileLogging.addEnabledOnSelection(pgFileLogging);
		enable_logger.addEnabledOnSelection(enableFileLogging, pgFileLogging);

		// Force File Logging off when general logging is off
		enable_logger.addListener(param -> {
			if (!enable_logger.getValue()) {
				enableFileLogging.setValue(false);
			}
		});

		// Param still in use.. not sure why it's commented out
//		BooleanParameterImpl udp_transport = new BooleanParameterImpl(config, "Logging Enable UDP Transport", "ConfigView.section.logging.udptransport");
		
		if ( Constants.IS_CVS_VERSION ){
			
			BooleanParameterImpl force_debug = new BooleanParameterImpl(
					BCFG_LOGGER_DEBUG_FILES_DISABLE, "ConfigView.section.logging.disabledebugfiles");
			
			add(force_debug);
		}else{
				// beta versions already have the debug files forced
			
			BooleanParameterImpl force_debug = new BooleanParameterImpl(
					BCFG_LOGGER_DEBUG_FILES_FORCE, "ConfigView.section.logging.forcedebug");
			
			add(force_debug);
		}
		
		
		add(new IntParameterImpl(ICFG_LOGGER_DEBUG_FILES_SIZE_KB,
				"ConfigView.section.logging.debugfilesize", 10, Integer.MAX_VALUE),
				Parameter.MODE_INTERMEDIATE);

		// advanced option

		// name

		StringParameterImpl name = new StringParameterImpl("Advanced Option Name",
				"label.name");
		add(name, Parameter.MODE_ADVANCED);
		name.setWidthInCharacters(20);

		// value

		StringParameterImpl value = new StringParameterImpl("Advanced Option Value",
				"label.value");
		add(value, Parameter.MODE_ADVANCED);
		value.setWidthInCharacters(12);

		// set

		ActionParameterImpl set_option = new ActionParameterImpl(null,
				"Button.set");
		add(set_option, Parameter.MODE_ADVANCED);

		set_option.addListener(param -> {
			String key = name.getValue().trim();

			if ((key.startsWith("'") && key.endsWith("'"))
					|| (key.startsWith("\"") && key.endsWith("\""))) {

				key = key.substring(1, key.length() - 1);
			}

			if (key.length() > 0) {

				if (key.startsWith("!")) {
					key = key.substring(1);
				}

				String val = value.getValue().trim();

				boolean is_string = false;

				if ((val.startsWith("'") && val.endsWith("'"))
						|| (val.startsWith("\"") && val.endsWith("\""))) {

					val = val.substring(1, val.length() - 1);

					is_string = true;
				}

				if (val.length() == 0) {

					COConfigurationManager.removeParameter(key);

				} else {

					if (is_string) {
						COConfigurationManager.setParameter(key, val);
					} else {
						String lc_val = val.toLowerCase(Locale.US);

						if (lc_val.equals("false") || lc_val.equals("true")) {
							COConfigurationManager.setParameter(key, lc_val.startsWith("t"));
						} else {
							try {
								long l = Long.parseLong(val);
								COConfigurationManager.setParameter(key, l);
							} catch (Throwable e) {
								COConfigurationManager.setParameter(key, val);
							}
						}
					}
				}

				COConfigurationManager.save();
			}

		});

		ParameterGroupImpl pgAdvSetting = new ParameterGroupImpl(
				"dht.advanced.group", name, value, set_option);
		add("pgAdvSetting", pgAdvSetting);
		pgAdvSetting.setMinimumRequiredUserMode(Parameter.MODE_ADVANCED);
		pgAdvSetting.setNumberOfColumns(3);

		// network diagnostics

		ActionParameterImpl generate_net_button = new ActionParameterImpl(
				"ConfigView.section.logging.netinfo",
				"ConfigView.section.logging.generatediagnostics");
		add(generate_net_button);
		generate_net_button.addListener(
				param -> new AEThread2("GenerateNetDiag", true) {
					@Override
					public void run() {
						StringWriter sw = new StringWriter();

						PrintWriter pw = new PrintWriter(sw);

						IndentWriter iw = new IndentWriter(pw);

						NetworkAdmin admin = NetworkAdmin.getSingleton();

						admin.generateDiagnostics(iw);

						pw.close();

						final String info = sw.toString();

						Logger.log(new LogEvent(LOGID, "Network Info:\n" + info));

						UIFunctions uif = UIFunctionsManager.getUIFunctions();
						if (uif != null) {
							uif.copyToClipboard(info);
						}

					}
				}.start());

		// stats

		ActionParameterImpl generate_stats_button = new ActionParameterImpl(
				"ConfigView.section.logging.statsinfo",
				"ConfigView.section.logging.generatediagnostics");
		add(generate_stats_button);

		generate_stats_button.addListener(param -> {
			Set<String> types = new HashSet<>();

			types.add(CoreStats.ST_ALL);

			Map<String, Object> reply = CoreStats.getStats(types);

			Iterator<Entry<String, Object>> it = reply.entrySet().iterator();

			StringBuilder buffer = new StringBuilder(16000);

			while (it.hasNext()) {

				Entry<String, Object> entry = it.next();

				if (entry == null) {
					continue;
				}

				buffer.append(entry.getKey()).append(" -> ").append(
						entry.getValue()).append("\r\n");
			}

			String str = buffer.toString();

			Logger.log(new LogEvent(LOGID, "Stats Info:\n" + str));

			UIFunctions uif = UIFunctionsManager.getUIFunctions();
			if (uif != null) {
				uif.copyToClipboard(str);
			}
		});

		// diagnostics

		ActionParameterImpl generate_button = new ActionParameterImpl(
				"ConfigView.section.logging.generatediagnostics.info",
				"ConfigView.section.logging.generatediagnostics");
		add(generate_button);

		generate_button.addListener(param -> {
			StringWriter sw = new StringWriter();

			PrintWriter pw = new PrintWriter(sw);

			AEDiagnostics.generateEvidence(pw);

			pw.close();

			String evidence = sw.toString();

			Logger.log(new LogEvent(LOGID, "Evidence Generation:\n" + evidence));

			UIFunctions uif = UIFunctionsManager.getUIFunctions();
			if (uif != null) {
				uif.copyToClipboard(evidence);
			}
		});

		if (false) {

			ActionParameterImpl test_button = new ActionParameterImpl("", "!Test!");
			add(test_button);
			test_button.addListener(param -> {
				try {
					PluginInterface pi = CoreFactory.getSingleton().getPluginManager().getDefaultPluginInterface();

					UpdateInstaller installer = pi.getUpdateManager().createInstaller();

					installer.addMoveAction("C:\\temp\\file1", "C:\\temp\\file2");

					installer.installNow(new UpdateInstallerListener() {
						@Override
						public void reportProgress(String str) {
							System.out.println(str);
						}

						@Override
						public void complete() {
							System.out.println("complete");
						}

						@Override
						public void failed(UpdateException e) {
							System.out.println("failed");

							e.printStackTrace();

						}
					});

				} catch (Throwable e) {

					e.printStackTrace();
				}
			});
		}
	}
}
