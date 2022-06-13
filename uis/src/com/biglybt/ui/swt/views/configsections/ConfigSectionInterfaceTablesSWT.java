/*
 * File    : ConfigPanel*.java
 * Created : 11 mar. 2004
 * By      : TuxPaper
 *
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

package com.biglybt.ui.swt.views.configsections;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.TimeFormatter;
import com.biglybt.pifimpl.local.ui.config.*;
import com.biglybt.ui.config.ConfigSectionImpl;
import com.biglybt.ui.swt.Utils;

import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.config.Parameter;
import com.biglybt.pif.ui.config.ParameterValidator.ValidationInfo;

import static com.biglybt.ui.swt.ConfigKeysSWT.ICFG_TABLE_HEADER_HEIGHT;

public class ConfigSectionInterfaceTablesSWT
	extends ConfigSectionImpl
{
	public static final String REFID_SECTION_LIBRARY = "section-library";

	public static final String SECTION_ID = "tables";

	private ParameterListener configOSX;

	public ConfigSectionInterfaceTablesSWT() {
		super(SECTION_ID, ConfigSection.SECTION_INTERFACE);
	}

	@Override
	public void deleteConfigSection() {
		super.deleteConfigSection();
		if (configOSX != null) {
			for (int i = 0; i < 4; i++) {
				COConfigurationManager.removeParameterListener("Table.lh" + i + ".prog",
						configOSX);
			}
		}
	}

	@Override
	public void build() {
		setDefaultUITypesForAdd(UIInstance.UIT_SWT);
		boolean isAZ3 = Utils.isAZ3UI();

		// General
		//////////

		List<Parameter> listGeneral = new ArrayList<>();

		int[] sortOrderValues = {
			0,
			1,
			2,
			3
		};
		String[] sortOrderLabels = {
			MessageText.getString("ConfigView.section.style.defaultSortOrder.asc"),
			MessageText.getString("ConfigView.section.style.defaultSortOrder.desc"),
			MessageText.getString("ConfigView.section.style.defaultSortOrder.flip"),
			MessageText.getString("ConfigView.section.style.defaultSortOrder.same")
		};
		add(new IntListParameterImpl("config.style.table.defaultSortOrder",
				"ConfigView.section.style.defaultSortOrder", sortOrderValues,
				sortOrderLabels), listGeneral);

		add(new BooleanParameterImpl("Table.sort.intuitive", "ConfigView.section.table.sort.intuitive"), listGeneral);

		add(new BooleanParameterImpl("Table.filter.confusable", "ConfigView.section.table.filter.confusable"), listGeneral);
		
		int[] values = {
			10,
			25,
			50,
			100,
			250,
			500,
			1000,
			2000,
			5000,
			10000,
			15000
		};
		String ms	= TimeFormatter.MS_SUFFIX;
		
		String secs = " " + TimeFormatter.getShortSuffix( TimeFormatter.TS_SECOND );
		
		String[] labels = {
			"10" + ms,
			"25" + ms,
			"50" + ms,
			"100" + ms,
			"250" + ms,
			"500" + ms,
			"1" + secs,
			"2" + secs,
			"5" + secs,
			"10" + secs,
			"15" + secs
		};
		
		add(new IntListParameterImpl("GUI Refresh",
				"ConfigView.section.style.guiUpdate", values, labels),
				Parameter.MODE_INTERMEDIATE, listGeneral);

		add(new IntParameterImpl("Graphics Update",
				"ConfigView.section.style.graphicsUpdate", 1, Integer.MAX_VALUE),
				Parameter.MODE_INTERMEDIATE, listGeneral);

		add(new IntParameterImpl("ReOrder Delay",
				"ConfigView.section.style.reOrderDelay"), Parameter.MODE_INTERMEDIATE,
				listGeneral);

		add(new BooleanParameterImpl("NameColumn.showProgramIcon",
				"ConfigView.section.style.showProgramIcon"),
				Parameter.MODE_INTERMEDIATE, listGeneral);

		////

		add(new BooleanParameterImpl("Table.extendedErase",
				"ConfigView.section.style.extendedErase"), Parameter.MODE_INTERMEDIATE,
				listGeneral);

		////

		boolean hhEnabled = COConfigurationManager.getIntParameter(
				ICFG_TABLE_HEADER_HEIGHT) > 0;

		BooleanParameterImpl chkHeaderHeight = new BooleanParameterImpl(
				"Table.useHeaderHeight", "ConfigView.section.style.enableHeaderHeight");
		add(chkHeaderHeight, Parameter.MODE_INTERMEDIATE);
		chkHeaderHeight.setValue(hhEnabled);

		IntParameterImpl paramHH = new IntParameterImpl(ICFG_TABLE_HEADER_HEIGHT,
				null, 0, 100);
		add(paramHH, Parameter.MODE_INTERMEDIATE);

		chkHeaderHeight.addEnabledOnSelection(paramHH);

		// Note: If we check "Table.useHeaderHeight" before using "Table.headerHeight" we wouldn't need this listener
		chkHeaderHeight.addListener(param -> {
			if (chkHeaderHeight.getValue()) {
				COConfigurationManager.setParameter(ICFG_TABLE_HEADER_HEIGHT, 16);
				paramHH.setEnabled(true);
			} else {
				COConfigurationManager.setParameter(ICFG_TABLE_HEADER_HEIGHT, 0);
				paramHH.setEnabled(false);
			}
		});

		/////

		boolean cdEnabled = COConfigurationManager.getStringParameter(
				"Table.column.dateformat", "").length() > 0;

		BooleanParameterImpl chkCustomDate = new BooleanParameterImpl(
				"Table.useCustomDateFormat",
				"ConfigView.section.style.customDateFormat");
		add(chkCustomDate, Parameter.MODE_INTERMEDIATE);
		chkCustomDate.setValue(cdEnabled);

		StringParameterImpl paramCustomDate = new StringParameterImpl(
				"Table.column.dateformat", null);
		add(paramCustomDate);

		chkCustomDate.addEnabledOnSelection(paramCustomDate);

		paramCustomDate.addStringValidator((p, toValue) -> {
			try {
				SimpleDateFormat temp = new SimpleDateFormat(toValue);
				temp.format(new Date());
				return new ValidationInfo(true);
			} catch (Exception e) {
				// probably illegalargumentexception
				return new ValidationInfo(false, e.getMessage());
			}
		});

		// Note: If we check "Table.useCustomDateFormat" before using "Table.column.dateformat" we wouldn't need this listener
		chkCustomDate.addListener(param -> {
			if (chkCustomDate.getValue()) {
				COConfigurationManager.setParameter("Table.column.dateformat",
						"yyyy/MM/dd");
				paramCustomDate.setEnabled(true);
			} else {
				COConfigurationManager.setParameter("Table.column.dateformat", "");
				paramCustomDate.setEnabled(false);
			}
		});

		add("ifT.pgGeneral2Col",
				new ParameterGroupImpl(null, chkHeaderHeight, paramHH, chkCustomDate,
						paramCustomDate).setNumberOfColumns2(2),
				Parameter.MODE_INTERMEDIATE, listGeneral);

		add(new BooleanParameterImpl("Table.tooltip.disable",
				"ConfigView.section.table.disable.tooltips"),
				Parameter.MODE_INTERMEDIATE, listGeneral);

		add(new ParameterGroupImpl("ConfigView.section.global", listGeneral));

			// Library section
		
		List<Parameter> listLibrary = new ArrayList<>();

		add(new BooleanParameterImpl("Library.ShowTitle",
				"menu.show.title"), listLibrary);

		add(new BooleanParameterImpl("Table.useTree",
				"ConfigView.section.style.useTree"), listLibrary);

		add(new BooleanParameterImpl("DND Always In Incomplete",
				"ConfigView.section.style.DNDalwaysInIncomplete"),
				Parameter.MODE_ADVANCED, listLibrary);

		if (isAZ3) {

			add(new BooleanParameterImpl("Library.EnableSimpleView",
					"ConfigView.section.style.EnableSimpleView"), listLibrary);
		}

		add(new BooleanParameterImpl("Library.ShowTabsInTorrentView",
				"ConfigView.section.style.ShowTabsInTorrentView"), listLibrary);

		// split mode

		String spltLabels[] = new String[5];
		int splitValues[] = new int[spltLabels.length];

		for (int i = 0; i < spltLabels.length; i++) {

			spltLabels[i] = MessageText.getString("ConfigView.library.split." + i);
			splitValues[i] = i;
		}

		add(new IntListParameterImpl("Library.TorrentViewSplitMode",
				"ConfigView.library.split.mode", splitValues, spltLabels), listLibrary);

		// fancy menu

		add(new BooleanParameterImpl("Library.showFancyMenu",
				"ConfigView.section.style.ShowFancyMenu"), listLibrary);

		// double-click

		String[][] dblclickOptions = {
			{ "ConfigView.option.dm.dblclick.play", 			"0" },
			{ "ConfigView.option.dm.dblclick.show", 			"2" },
			{ "ConfigView.option.dm.dblclick.launch", 			"3" },
			{ "ConfigView.option.dm.dblclick.launch.content",	"7" },
			{ "ConfigView.option.dm.dblclick.launch.qv", 		"4" },
			{ "ConfigView.option.dm.dblclick.open.browser", 	"5" },
			{ "ConfigView.option.dm.dblclick.details", 			"1" },
			{ "ConfigView.option.dm.dblclick.details.pop", 		"6" }
		};

		String dblclickLabels[] = new String[dblclickOptions.length];
		String dblclickValues[] = new String[dblclickOptions.length];

		for (int i = 0; i < dblclickOptions.length; i++) {

			dblclickLabels[i] = MessageText.getString(dblclickOptions[i][0]);
			dblclickValues[i] = dblclickOptions[i][1];
		}
		add(new StringListParameterImpl("list.dm.dblclick",
				"ConfigView.label.dm.dblclick", dblclickValues, dblclickLabels),
				listLibrary);

		// always open websites in browser

		BooleanParameterImpl web_in_browser = new BooleanParameterImpl(
				"Library.LaunchWebsiteInBrowser", "library.launch.web.in.browser");
		add(web_in_browser, listLibrary);
		web_in_browser.setIndent(1, true);

		BooleanParameterImpl web_in_browser_anon = new BooleanParameterImpl(
				"Library.LaunchWebsiteInBrowserAnon",
				"library.launch.web.in.browser.anon");
		add(web_in_browser_anon, listLibrary);
		web_in_browser_anon.setIndent(2, false);

		web_in_browser.addEnabledOnSelection(web_in_browser_anon);

		// enter action

		String[][] enterOptions = {
			{ "ConfigView.option.dm.enter.sameasdblclick", 		"-1" },
			{ "ConfigView.option.dm.dblclick.play", 			"0" },
			{ "ConfigView.option.dm.dblclick.show", 			"2" },
			{ "ConfigView.option.dm.dblclick.launch", 			"3" },
			{ "ConfigView.option.dm.dblclick.launch.content",	"7" },
			{ "ConfigView.option.dm.dblclick.launch.qv", 		"4" },
			{ "ConfigView.option.dm.dblclick.open.browser", 	"5" },
			{ "ConfigView.option.dm.dblclick.details", 			"1" },
			{ "ConfigView.option.dm.dblclick.details.pop", 		"6" },
		};

		String enterLabels[] = new String[enterOptions.length];
		String enterValues[] = new String[enterOptions.length];

		for (int i = 0; i < enterOptions.length; i++) {

			enterLabels[i] = MessageText.getString(enterOptions[i][0]);
			enterValues[i] = enterOptions[i][1];
		}

		add(new StringListParameterImpl("list.dm.enteraction",
				"ConfigView.label.dm.enteraction", enterValues, enterLabels),
				listLibrary);

		// Library: Launch helpers
		////////////////

		List<Parameter> listLaunchGroup = new ArrayList<>();

		add(new LabelParameterImpl("ConfigView.label.lh.info"), listLaunchGroup);

		List<Parameter> listLaunchHelpers = new ArrayList<>();

		for (int i = 0; i < 4; i++) {

			StringParameterImpl exts = new StringParameterImpl(
					"Table.lh" + i + ".exts", "ConfigView.label.lh.ext");
			add(exts, listLaunchHelpers);
			exts.setWidthInCharacters(15);

			FileParameterImpl prog = new FileParameterImpl("Table.lh" + i + ".prog",
					"ConfigView.label.lh.prog");
			add(prog, listLaunchHelpers);

			if (Constants.isOSX) {
				if (configOSX != null) {
					// Probably could be changed to prog.addListener(..), but I'm not touching it [tux]
					configOSX = new ParameterListener() {
						private boolean changing = false;

						private String last_changed = "";

						@Override
						public void parameterChanged(String parameter_name) {
							if (changing) {

								return;

							}

							final String value = COConfigurationManager.getStringParameter(
									parameter_name);

							if (value.equals(last_changed)) {

								return;
							}

							if (value.endsWith(".app")) {

								Utils.execSWTThreadLater(1, new Runnable() {
									@Override
									public void run() {
										last_changed = value;

										try {
											changing = true;

											File file = new File(value);

											String app_name = file.getName();

											int pos = app_name.lastIndexOf(".");

											app_name = app_name.substring(0, pos);

											String new_value = value + "/Contents/MacOS/" + app_name;

											if (new File(new_value).exists()) {

												prog.setFileName(new_value);
											}
										} finally {

											changing = false;
										}
									}
								});
							}
						}
					};
				}
				COConfigurationManager.addParameterListener("Table.lh" + i + ".prog",
						configOSX);
			}
		}

		add("pgLaunchHelpersInt",
				new ParameterGroupImpl(null, listLaunchHelpers).setNumberOfColumns2(2),
				listLaunchGroup);

		add(new ParameterGroupImpl("ConfigView.section.style.launch",
				listLaunchGroup));

		ParameterGroupImpl pgLibrary = add(new ParameterGroupImpl("ConfigView.section.style.library",
				listLibrary));

		pgLibrary.setReferenceID( REFID_SECTION_LIBRARY );
		
		List<Parameter> listSearchSubs = new ArrayList<>();

		add(new BooleanParameterImpl("Search View Is Web View",
				"ConfigView.section.style.search.is.web.view"), listSearchSubs);

		add(new BooleanParameterImpl("Search View Switch Hidden",
				"ConfigView.section.style.search.hide.view.switch"), listSearchSubs);

		add(new IntParameterImpl("Search Subs Row Height",
				"ConfigView.section.style.searchsubs.row.height", 16, 64),
				listSearchSubs);

		add(new ParameterGroupImpl("ConfigView.section.style.searchsubs",
				listSearchSubs));

	}
}
