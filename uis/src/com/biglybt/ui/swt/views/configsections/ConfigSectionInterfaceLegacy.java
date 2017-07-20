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

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.ui.swt.config.BooleanParameter;
import com.biglybt.ui.swt.config.ChangeSelectionActionPerformer;
import com.biglybt.ui.swt.pif.UISWTConfigSection;

import com.biglybt.pif.ui.config.ConfigSection;

/**
 * @author TuxPaper
 * @created Jan 6, 2009
 *
 */
public class ConfigSectionInterfaceLegacy
	implements UISWTConfigSection
{
	private final static int REQUIRED_MODE = 2;

	// @see com.biglybt.ui.swt.pif.UISWTConfigSection#configSectionCreate(org.eclipse.swt.widgets.Composite)
	@Override
	public Composite configSectionCreate(Composite parent) {
		GridData gridData;
		GridLayout layout;
		Label label;

		Composite cSection = new Composite(parent, SWT.NULL);

		gridData = new GridData(GridData.VERTICAL_ALIGN_FILL
				| GridData.HORIZONTAL_ALIGN_FILL);
		cSection.setLayoutData(gridData);
		layout = new GridLayout();
		layout.numColumns = 1;
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		cSection.setLayout(layout);

		int userMode = COConfigurationManager.getIntParameter("User Mode");
		if (userMode < REQUIRED_MODE) {
			label = new Label(cSection, SWT.WRAP);
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			label.setLayoutData(gridData);

			final String[] modeKeys = { "ConfigView.section.mode.beginner",
					"ConfigView.section.mode.intermediate",
					"ConfigView.section.mode.advanced" };

			String param1, param2;
			if (REQUIRED_MODE < modeKeys.length)
				param1 = MessageText.getString(modeKeys[REQUIRED_MODE]);
			else
				param1 = String.valueOf(REQUIRED_MODE);

			if (userMode < modeKeys.length)
				param2 = MessageText.getString(modeKeys[userMode]);
			else
				param2 = String.valueOf(userMode);

			label.setText(MessageText.getString("ConfigView.notAvailableForMode",
					new String[] { param1, param2 } ));

			return cSection;
		}

		/**
		 * Old-style speed menus.
		 */
		new BooleanParameter(cSection, "GUI_SWT_bOldSpeedMenu", "ConfigView.label.use_old_speed_menus");

		BooleanParameter bpCustomTab = new BooleanParameter(cSection,
				"useCustomTab", "ConfigView.section.style.useCustomTabs");
		Control cFancyTab = new BooleanParameter(cSection, "GUI_SWT_bFancyTab",
				"ConfigView.section.style.useFancyTabs").getControl();

		Control[] controls = {
			cFancyTab
		};
		bpCustomTab.setAdditionalActionPerformer(new ChangeSelectionActionPerformer(
				controls));

		return cSection;
	}

	// @see com.biglybt.ui.swt.pif.UISWTConfigSection#maxUserMode()
	@Override
	public int maxUserMode() {
		return REQUIRED_MODE;
	}

	// @see com.biglybt.pif.ui.config.ConfigSection#configSectionDelete()
	@Override
	public void configSectionDelete() {
		// TODO Auto-generated method stub

	}

	// @see com.biglybt.pif.ui.config.ConfigSection#configSectionGetName()
	@Override
	public String configSectionGetName() {
		return "interface.legacy";
	}

	// @see com.biglybt.pif.ui.config.ConfigSection#configSectionGetParentSection()
	@Override
	public String configSectionGetParentSection() {
		return ConfigSection.SECTION_INTERFACE;
	}

	// @see com.biglybt.pif.ui.config.ConfigSection#configSectionSave()
	@Override
	public void configSectionSave() {
		// TODO Auto-generated method stub

	}

}
