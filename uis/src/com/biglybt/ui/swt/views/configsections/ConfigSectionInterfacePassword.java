/*
 * File    : ConfigSectionInterfaceAlerts.java
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

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.config.PasswordParameter;
import com.biglybt.ui.swt.pif.UISWTConfigSection;

import com.biglybt.pif.ui.config.ConfigSection;

public class ConfigSectionInterfacePassword
	implements UISWTConfigSection
{
	private final static int REQUIRED_MODE = 0;

	Label passwordMatch;

	@Override
	public String configSectionGetParentSection() {
		return ConfigSection.SECTION_INTERFACE;
	}

	/* Name of section will be pulled from
	 * ConfigView.section.<i>configSectionGetName()</i>
	 */
	@Override
	public String configSectionGetName() {
		return "interface.password";
	}

	@Override
	public void configSectionSave() {
	}

	@Override
	public void configSectionDelete() {
	}

	@Override
	public int maxUserMode() {
		return REQUIRED_MODE;
	}

	@Override
	public Composite configSectionCreate(final Composite parent) {
		GridData gridData;
		GridLayout layout;
		Label label;

		Composite cSection = new Composite(parent, SWT.NULL);
		gridData = new GridData(
				GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
		Utils.setLayoutData(cSection, gridData);
		layout = new GridLayout();
		layout.marginWidth = 0;
		layout.numColumns = 2;
		cSection.setLayout(layout);

		// password

		label = new Label(cSection, SWT.NULL);
		Messages.setLanguageText(label, "ConfigView.label.password");

		gridData = new GridData();
		gridData.widthHint = 150;
		PasswordParameter pw1 = new PasswordParameter(cSection, "Password");
		pw1.setLayoutData(gridData);
		Text t1 = (Text) pw1.getControl();

		//password confirm

		label = new Label(cSection, SWT.NULL);
		Messages.setLanguageText(label, "ConfigView.label.passwordconfirm");
		gridData = new GridData();
		gridData.widthHint = 150;
		PasswordParameter pw2 = new PasswordParameter(cSection, "Password Confirm");
		pw2.setLayoutData(gridData);
		Text t2 = (Text) pw2.getControl();

		// password activated

		label = new Label(cSection, SWT.NULL);
		Messages.setLanguageText(label, "ConfigView.label.passwordmatch");
		passwordMatch = new Label(cSection, SWT.NULL);
		gridData = new GridData();
		gridData.widthHint = 150;
		Utils.setLayoutData(passwordMatch, gridData);
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

		return cSection;
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
