/*
 * Created : Dec 4, 2006
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

import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.pifimpl.local.ui.config.ParameterImpl;
import com.biglybt.ui.config.ConfigSectionImpl;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.config.BaseSwtParameter;
import com.biglybt.ui.swt.config.PasswordSwtParameter;

import com.biglybt.pif.ui.config.ConfigSection;

public class ConfigSectionInterfacePasswordSWT
	extends ConfigSectionImpl implements BaseConfigSectionSWT {
	public static final String SECTION_ID = "interface.password";

	Label passwordMatch;

	public ConfigSectionInterfacePasswordSWT() {
		super(SECTION_ID, ConfigSection.SECTION_INTERFACE);
	}

	@Override
	public void build() {

	}

	@Override
	public void configSectionCreate(final Composite cSection, Map<ParameterImpl, BaseSwtParameter> mapParamToSwtParam) {
		GridData gridData;
		Label label;

		// password

		PasswordSwtParameter pw1 = new PasswordSwtParameter(cSection, "Password", "ConfigView.label.password");
		pw1.setWidthInCharacters(12);
		Text t1 = (Text) pw1.getMainControl();

		//password confirm

		PasswordSwtParameter pw2 = new PasswordSwtParameter(cSection, "Password Confirm", "ConfigView.label.passwordconfirm");
		pw2.setWidthInCharacters(12);
		Text t2 = (Text) pw2.getMainControl();

		// password activated

		label = new Label(cSection, SWT.NULL);
		Messages.setLanguageText(label, "ConfigView.label.passwordmatch");
		passwordMatch = new Label(cSection, SWT.NULL);
		gridData = new GridData();
		gridData.widthHint = 150;
		passwordMatch.setLayoutData(gridData);
		refreshPWLabel();

		t1.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				refreshPWLabel();
			}
		});
		t2.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				refreshPWLabel();
			}
		});
	}

	private void refreshPWLabel() {

		if (passwordMatch == null || passwordMatch.isDisposed())
			return;
		byte[] password = COConfigurationManager.getByteParameter("Password",
				"".getBytes());
		COConfigurationManager.setParameter("Password enabled", false);
		if (password.length == 0) {
			passwordMatch.setText(
					MessageText.getString("ConfigView.label.passwordmatchnone"));
		} else {
			byte[] confirm = COConfigurationManager.getByteParameter(
					"Password Confirm", "".getBytes());
			if (confirm.length == 0) {
				passwordMatch.setText(
						MessageText.getString("ConfigView.label.passwordmatchno"));
			} else {
				boolean same = true;
				for (int i = 0; i < password.length; i++) {
					if (password[i] != confirm[i])
						same = false;
				}
				if (same) {
					passwordMatch.setText(
							MessageText.getString("ConfigView.label.passwordmatchyes"));
					COConfigurationManager.setParameter("Password enabled", true);
				} else {
					passwordMatch.setText(
							MessageText.getString("ConfigView.label.passwordmatchno"));
				}
			}
		}
	}
}
