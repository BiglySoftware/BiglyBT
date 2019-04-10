/*
 * Created on Jan 6, 2009
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.ui.swt.views.configsections;

import com.biglybt.pifimpl.local.ui.config.BooleanParameterImpl;
import com.biglybt.ui.config.ConfigSectionImpl;

import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.config.ConfigSection;

import static com.biglybt.pif.ui.config.Parameter.MODE_ADVANCED;

/**
 * @author TuxPaper
 * @created Jan 6, 2009
 *
 */
public class ConfigSectionInterfaceLegacySWT
	extends ConfigSectionImpl
{
	public static final String SECTION_ID = "interface.legacy";

	private final static int REQUIRED_MODE = MODE_ADVANCED;

	public ConfigSectionInterfaceLegacySWT() {
		super(SECTION_ID, ConfigSection.SECTION_INTERFACE, REQUIRED_MODE);
	}

	@Override
	public void build() {
		setDefaultUserModeForAdd(REQUIRED_MODE);
		setDefaultUITypesForAdd(UIInstance.UIT_SWT);

		/**
		 * Old-style speed menus.
		 */
		add(new BooleanParameterImpl("GUI_SWT_bOldSpeedMenu",
				"ConfigView.label.use_old_speed_menus"));

		BooleanParameterImpl bpCustomTab = new BooleanParameterImpl(
				"useCustomTab", "ConfigView.section.style.useCustomTabs");
		add(bpCustomTab);

		BooleanParameterImpl bpFancyTab = new BooleanParameterImpl(
				"GUI_SWT_bFancyTab", "ConfigView.section.style.useFancyTabs");
		add(bpFancyTab);

		bpCustomTab.addEnabledOnSelection(bpFancyTab);
	}

}
