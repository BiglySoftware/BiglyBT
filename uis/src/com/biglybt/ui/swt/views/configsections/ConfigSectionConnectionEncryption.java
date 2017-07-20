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
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Constants;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.LinkLabel;
import com.biglybt.ui.swt.config.*;
import com.biglybt.ui.swt.pif.UISWTConfigSection;

public class ConfigSectionConnectionEncryption
	implements UISWTConfigSection
{

	private final static String CFG_PREFIX = "ConfigView.section.connection.encryption.";

	private final static int REQUIRED_MODE = 1;

	@Override
	public int maxUserMode() {
		return REQUIRED_MODE;
	}

	@Override
	public String configSectionGetParentSection() {
		return ConfigSection.SECTION_CONNECTION;
	}

	@Override
	public String configSectionGetName() {
		return "connection.encryption";
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

		gridData = new GridData(
				GridData.HORIZONTAL_ALIGN_FILL + GridData.VERTICAL_ALIGN_FILL);
		cSection.setLayoutData(gridData);
		GridLayout advanced_layout = new GridLayout();
		cSection.setLayout(advanced_layout);

		int userMode = COConfigurationManager.getIntParameter("User Mode");
		if (userMode < REQUIRED_MODE) {
			Label label = new Label(cSection, SWT.WRAP);
			gridData = new GridData();
			label.setLayoutData(gridData);

			final String[] modeKeys = {
				"ConfigView.section.mode.beginner",
				"ConfigView.section.mode.intermediate",
				"ConfigView.section.mode.advanced"
			};

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
					new String[] {
						param1,
						param2
					}));

			return cSection;
		}

		Group gCrypto = new Group(cSection, SWT.NULL);
		Messages.setLanguageText(gCrypto,
				"ConfigView.section.connection.encryption.encrypt.group");
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gCrypto.setLayoutData(gridData);
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		gCrypto.setLayout(layout);

		Label lcrypto = new Label(gCrypto, SWT.WRAP);
		Messages.setLanguageText(lcrypto,
				"ConfigView.section.connection.encryption.encrypt.info");
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 2;
		gridData.widthHint = 200; // needed for wrap
		Utils.setLayoutData(lcrypto, gridData);

		gridData = new GridData();
		gridData.horizontalSpan = 2;
		new LinkLabel(gCrypto, gridData,
				"ConfigView.section.connection.encryption.encrypt.info.link",
				Constants.URL_WIKI+ "w/Avoid_traffic_shaping");

		final BooleanParameter require = new BooleanParameter(gCrypto,
				"network.transport.encrypted.require",
				"ConfigView.section.connection.encryption.require_encrypted_transport");
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		require.setLayoutData(gridData);

		String[] encryption_types = {
			"Plain",
			"RC4"
		};
		String dropLabels[] = new String[encryption_types.length];
		String dropValues[] = new String[encryption_types.length];
		for (int i = 0; i < encryption_types.length; i++) {
			dropLabels[i] = encryption_types[i];
			dropValues[i] = encryption_types[i];
		}

		Composite cEncryptLevel = new Composite(gCrypto, SWT.NULL);
		gridData = new GridData(
				GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
		gridData.horizontalSpan = 2;
		cEncryptLevel.setLayoutData(gridData);
		layout = new GridLayout();
		layout.numColumns = 2;
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		cEncryptLevel.setLayout(layout);

		Label lmin = new Label(cEncryptLevel, SWT.NULL);
		Messages.setLanguageText(lmin,
				"ConfigView.section.connection.encryption.min_encryption_level");
		final StringListParameter min_level = new StringListParameter(cEncryptLevel,
				"network.transport.encrypted.min_level", encryption_types[1],
				dropLabels, dropValues);

		Label lcryptofb = new Label(gCrypto, SWT.WRAP);
		Messages.setLanguageText(lcryptofb,
				"ConfigView.section.connection.encryption.encrypt.fallback_info");
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 2;
		gridData.widthHint = 200; // needed for wrap
		Utils.setLayoutData(lcryptofb, gridData);

		BooleanParameter fallback_outgoing = new BooleanParameter(gCrypto,
				"network.transport.encrypted.fallback.outgoing",
				"ConfigView.section.connection.encryption.encrypt.fallback_outgoing");
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		fallback_outgoing.setLayoutData(gridData);

		final BooleanParameter fallback_incoming = new BooleanParameter(gCrypto,
				"network.transport.encrypted.fallback.incoming",
				"ConfigView.section.connection.encryption.encrypt.fallback_incoming");
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		fallback_incoming.setLayoutData(gridData);

		final BooleanParameter use_crypto_port = new BooleanParameter(gCrypto,
				"network.transport.encrypted.use.crypto.port",
				"ConfigView.section.connection.encryption.use_crypto_port");
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		use_crypto_port.setLayoutData(gridData);

		final Control[] ap_controls = {
			min_level.getControl(),
			lmin,
			lcryptofb,
			fallback_outgoing.getControl(),
			fallback_incoming.getControl()
		};

		IAdditionalActionPerformer iap = new GenericActionPerformer(
				new Control[] {}) {
			@Override
			public void performAction() {
				boolean required = require.isSelected();

				boolean ucp_enabled = !fallback_incoming.isSelected() && required;

				use_crypto_port.getControl().setEnabled(ucp_enabled);

				for (int i = 0; i < ap_controls.length; i++) {

					ap_controls[i].setEnabled(required);
				}
			}
		};

		fallback_incoming.setAdditionalActionPerformer(iap);

		require.setAdditionalActionPerformer(iap);

		///////////////////////

		return cSection;

	}

}
