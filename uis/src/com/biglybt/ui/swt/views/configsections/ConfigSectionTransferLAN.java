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

package com.biglybt.ui.swt.views.configsections;

import org.eclipse.swt.SWT;

import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.config.*;
import com.biglybt.ui.swt.pif.UISWTConfigSection;



public class ConfigSectionTransferLAN implements UISWTConfigSection {

	private static final int REQUIRED_MODE = 2;

	@Override
	public int maxUserMode() {
		return REQUIRED_MODE;
	}


	@Override
	public String configSectionGetParentSection() {
		return ConfigSection.SECTION_TRANSFER;
	}

	@Override
	public String configSectionGetName() {
		return "transfer.lan";
	}

	@Override
	public void configSectionSave() {
	}

	@Override
	public void configSectionDelete() {
	}

	@Override
	public Composite configSectionCreate(final Composite parent) {
		GridData gridData;

		Composite cSection = new Composite(parent, SWT.NULL);

		gridData = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
		cSection.setLayoutData(gridData);
		GridLayout advanced_layout = new GridLayout();
		advanced_layout.numColumns = 2;
		cSection.setLayout(advanced_layout);

		int userMode = COConfigurationManager.getIntParameter("User Mode");
		if (userMode < REQUIRED_MODE) {
			Label label = new Label(cSection, SWT.WRAP);
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


		BooleanParameter enable_lan = new BooleanParameter(
				cSection, "LAN Speed Enabled",
				"ConfigView.section.transfer.lan.enable" );
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		enable_lan.setLayoutData(gridData);

		IntParameter lan_max_upload = new IntParameter( cSection, "Max LAN Upload Speed KBs" );
		gridData = new GridData();
		lan_max_upload.setLayoutData(gridData);
		Label llmux = new Label(cSection, SWT.NULL);
		Messages.setLanguageText( llmux, "ConfigView.section.transfer.lan.uploadrate" );


		IntParameter lan_max_download = new IntParameter( cSection, "Max LAN Download Speed KBs" );
		gridData = new GridData();
		lan_max_download.setLayoutData(gridData);
		Label llmdx = new Label(cSection, SWT.NULL);
		Messages.setLanguageText( llmdx, "ConfigView.section.transfer.lan.downloadrate" );

		enable_lan.setAdditionalActionPerformer(
	    		new ChangeSelectionActionPerformer( new Parameter[]{ lan_max_upload, lan_max_download } ));
		enable_lan.setAdditionalActionPerformer(
	    		new ChangeSelectionActionPerformer( new Control[]{ llmux, llmdx }));


		return cSection;

	}

}
