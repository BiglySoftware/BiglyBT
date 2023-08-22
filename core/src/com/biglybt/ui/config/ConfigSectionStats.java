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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.stats.StatsWriterPeriodic;
import com.biglybt.core.stats.transfer.OverallStats;
import com.biglybt.core.stats.transfer.StatsFactory;
import com.biglybt.core.util.GeneralUtils;
import com.biglybt.pifimpl.local.ui.config.*;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.UIFunctionsUserPrompter;

import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.config.Parameter;

import static com.biglybt.core.config.ConfigKeys.Stats.*;

public class ConfigSectionStats
	extends ConfigSectionImpl
{
	public static final String SECTION_ID = "stats";

	private static final int[] statsPeriods = {
		1,
		2,
		3,
		4,
		5,
		10,
		15,
		20,
		25,
		30,
		40,
		50,
		60,
		120,
		180,
		240,
		300,
		360,
		420,
		480,
		540,
		600,
		900,
		1200,
		1800,
		2400,
		3000,
		3600,
		7200,
		10800,
		14400,
		21600,
		43200,
		86400,
	};

	public ConfigSectionStats() {
		super(SECTION_ID, ConfigSection.SECTION_ROOT);
	}

	@Override
	public void build() {

		// general

		IntParameterImpl paramStatsSmoothingSecs = new IntParameterImpl(
				ICFG_STATS_SMOOTHING_SECS, 
				"stats.general.smooth_secs", 
				GeneralUtils.SMOOTHING_UPDATE_WINDOW_MIN, 
				GeneralUtils.SMOOTHING_UPDATE_WINDOW_MAX );
		
		add(paramStatsSmoothingSecs);

		add("pgGeneralStats", new ParameterGroupImpl("ConfigView.section.general",
				paramStatsSmoothingSecs));

		// display

		BooleanParameterImpl paramGraphDividers = new BooleanParameterImpl(
				BCFG_STATS_GRAPH_DIVIDERS,
				"ConfigView.section.stats.graph_update_dividers");
		add(paramGraphDividers);
		paramGraphDividers.setAllowedUiTypes(UIInstance.UIT_SWT);

		add("pgStatsDisplay",
				new ParameterGroupImpl("stats.display.group", paramGraphDividers));

		// snapshots

		BooleanParameterImpl enableStats = new BooleanParameterImpl(
				BCFG_STATS_ENABLE, "ConfigView.section.stats.enable");
		add(enableStats);

		List<Parameter> listSnapShot = new ArrayList<>();

		// row

		DirectoryParameterImpl pathParameter = new DirectoryParameterImpl(
				SCFG_STATS_DIR, "ConfigView.section.stats.defaultsavepath");
		add(pathParameter, listSnapShot);
		pathParameter.setDialogTitleKey(
				"ConfigView.section.stats.choosedefaultsavepath");

		// row

		StringParameterImpl fileParameter = new StringParameterImpl(
				StatsWriterPeriodic.DEFAULT_STATS_FILE_NAME,
				"ConfigView.section.stats.savefile");
		add(fileParameter, listSnapShot);
		fileParameter.setWidthInCharacters(15);

		StringParameterImpl xslParameter = new StringParameterImpl(
				SCFG_STATS_XSL_FILE, "ConfigView.section.stats.xslfile");
		add(xslParameter, listSnapShot);
		xslParameter.setWidthInCharacters(15);
		xslParameter.setSuffixLabelKey("ConfigView.section.stats.xslfiledetails");

		final String[] spLabels = new String[statsPeriods.length];
		final int[] spValues = new int[statsPeriods.length];
		for (int i = 0; i < statsPeriods.length; i++) {
			int num = statsPeriods[i];

			if (num % 3600 == 0)
				spLabels[i] = " " + (statsPeriods[i] / 3600) + " "
						+ MessageText.getString("ConfigView.section.stats.hours");

			else if (num % 60 == 0)
				spLabels[i] = " " + (statsPeriods[i] / 60) + " "
						+ MessageText.getString("ConfigView.section.stats.minutes");

			else
				spLabels[i] = " " + statsPeriods[i] + " "
						+ MessageText.getString("ConfigView.section.stats.seconds");

			spValues[i] = statsPeriods[i];
		}

		IntListParameterImpl statsPeriod = new IntListParameterImpl(
				ICFG_STATS_PERIOD, "ConfigView.section.stats.savefreq", spValues,
				spLabels);
		add(statsPeriod, listSnapShot);

		BooleanParameterImpl exportPeers = new BooleanParameterImpl(
				BCFG_STATS_EXPORT_PEER_DETAILS, "ConfigView.section.stats.exportpeers");
		add(exportPeers, listSnapShot);

		BooleanParameterImpl exportFiles = new BooleanParameterImpl(
				BCFG_STATS_EXPORT_FILE_DETAILS, "ConfigView.section.stats.exportfiles");
		add(exportFiles, listSnapShot);

		enableStats.addEnabledOnSelection(listSnapShot.toArray(new Parameter[0]));

		listSnapShot.add(0, enableStats);
		add("pgStatsSnapshot",
				new ParameterGroupImpl("stats.snapshot.group", listSnapShot));

		// xfer

		// set mark

		LabelParameterImpl paramSetMarkLabel = new LabelParameterImpl(
				"ConfigView.section.transfer.setmark");
		add(paramSetMarkLabel);

		ActionParameterImpl set_mark_button = new ActionParameterImpl(null,
				"Button.set");

		add(set_mark_button);
		set_mark_button.addListener(param -> {
			OverallStats stats = StatsFactory.getStats();

			if (stats != null) {
				stats.setMark();
			}
		});

		ActionParameterImpl clear_mark_button = new ActionParameterImpl(null,
				"Button.clear");
		add(clear_mark_button);
		clear_mark_button.addListener(param -> {
			OverallStats stats = StatsFactory.getStats();

			if (stats != null) {
				stats.clearMark();
			}
		});

		ParameterGroupImpl pgXferButtons = new ParameterGroupImpl(null,
				set_mark_button, clear_mark_button);
		add("Stats.pgXferButtons", pgXferButtons);

		ParameterGroupImpl pgXfer = new ParameterGroupImpl(
				"ConfigView.section.transfer", paramSetMarkLabel, pgXferButtons);
		add("Stats.pgXfer", pgXfer);

		// long term

		List<Parameter> listLong = new ArrayList<>();

		BooleanParameterImpl enableLongStats = new BooleanParameterImpl(
				BCFG_LONG_TERM_STATS_ENABLE, "ConfigView.section.stats.enable");
		add(enableLongStats);

		// week start

		final String[] wsLabels = new String[7];
		final int[] wsValues = new int[7];

		Calendar cal = new GregorianCalendar();
		SimpleDateFormat format = new SimpleDateFormat("E");

		for (int i = 0; i < 7; i++) {
			int dow = i + 1; // sun = 1 etc
			cal.set(Calendar.DAY_OF_WEEK, dow);
			wsLabels[i] = format.format(cal.getTime());
			wsValues[i] = i + 1;
		}

		IntListParameterImpl week_start = new IntListParameterImpl(
				ICFG_LONG_TERM_STATS_WEEKSTART, "stats.long.weekstart", wsValues,
				wsLabels);
		add(week_start, listLong);

		// month start

		IntParameterImpl month_start = new IntParameterImpl(
				ICFG_LONG_TERM_STATS_MONTHSTART, "stats.long.monthstart", 1, 28);
		add(month_start, listLong);

		enableLongStats.addEnabledOnSelection(listLong.toArray(new Parameter[0]));

		// reset

		ActionParameterImpl lt_reset_button = new ActionParameterImpl(
				"ConfigView.section.transfer.lts.reset", "Button.clear");
		add(lt_reset_button, listLong);

		lt_reset_button.addListener(param -> {
			UIFunctions uif = UIFunctionsManager.getUIFunctions();
			if (uif == null) {
				StatsFactory.clearLongTermStats();
				return;
			}
			UIFunctionsUserPrompter userPrompter = uif.getUserPrompter(
					MessageText.getString(
							"ConfigView.section.security.resetcerts.warning.title"),
					MessageText.getString(
							"ConfigView.section.transfer.ltsreset.warning.msg"),
					new String[] {
						MessageText.getString("Button.ok"),
						MessageText.getString("Button.cancel")
			}, 1);
			if (userPrompter == null) {
				StatsFactory.clearLongTermStats();
				return;
			}
			userPrompter.setIconResource(UIFunctionsUserPrompter.ICON_WARNING);

			userPrompter.open(returnVal -> {
				if (returnVal != 0) {
					return;
				}

				StatsFactory.clearLongTermStats();
			});
		});

		listLong.add(0, enableLongStats);
		add("Stats.pgLong",
				new ParameterGroupImpl("stats.longterm.group", listLong));
	}
}
