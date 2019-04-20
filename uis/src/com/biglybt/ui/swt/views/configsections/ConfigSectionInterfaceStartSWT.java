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

import com.biglybt.core.util.Constants;
import com.biglybt.pifimpl.local.ui.config.ActionParameterImpl;
import com.biglybt.pifimpl.local.ui.config.BooleanParameterImpl;
import com.biglybt.pifimpl.local.ui.config.LabelParameterImpl;
import com.biglybt.ui.config.ConfigSectionImpl;
import com.biglybt.ui.swt.UISwitcherUtil;
import com.biglybt.ui.swt.Utils;

import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.config.Parameter;

public class ConfigSectionInterfaceStartSWT
	extends ConfigSectionImpl
{
	public static final String SECTION_ID = "start";

	public ConfigSectionInterfaceStartSWT() {
		super(SECTION_ID, ConfigSection.SECTION_INTERFACE);
	}

	@Override
	public void build() {
		setDefaultUITypesForAdd(UIInstance.UIT_SWT);

		// "Start" Sub-Section
		// -------------------
		add(new BooleanParameterImpl("ui.startfirst",
				"ConfigView.label.StartUIBeforeCore"), Parameter.MODE_ADVANCED);

		add(new BooleanParameterImpl("Show Splash", "ConfigView.label.showsplash"));

		// XXX is this really SWT only?
		add(new BooleanParameterImpl("update.start",
				"ConfigView.label.checkonstart"));

		// XXX is this really SWT only?
		add(new BooleanParameterImpl("update.periodic",
				"ConfigView.label.periodiccheck"));

		BooleanParameterImpl autoDownload = new BooleanParameterImpl(
				"update.autodownload", "ConfigView.section.update.autodownload");
		add(autoDownload);
		BooleanParameterImpl openDialog = new BooleanParameterImpl(
				"update.opendialog", "ConfigView.label.opendialog");
		add(openDialog);

		autoDownload.addDisabledOnSelection(openDialog);

		// XXX is this really SWT only?
		add(new BooleanParameterImpl("update.anonymous",
				"ConfigView.label.update.anonymous"));

		add("ifs.gap1", new LabelParameterImpl(""));

		add(new BooleanParameterImpl("Open Transfer Bar On Start",
				"ConfigView.label.open_transfer_bar_on_start"));
		add(new BooleanParameterImpl("Start Minimized",
				"ConfigView.label.startminimized"));

		if (Constants.isUnix) {
			add(new BooleanParameterImpl("ui.useGTK2", "ConfigView.label.useGTK2"));
		}

		// UI switcher window.

		ActionParameterImpl ui_switcher_button = new ActionParameterImpl(
				"ConfigView.label.ui_switcher", "ConfigView.label.ui_switcher_button");
		add( ui_switcher_button );
		
		ui_switcher_button.addListener(param -> Utils.execSWTThread(
				() -> UISwitcherUtil.openSwitcherWindow()));
	}
}
