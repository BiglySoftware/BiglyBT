/*
 * Created on 12-Jun-2004
 * Created by Paul Gardner
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */

package com.biglybt.ui.swt.views.configsections;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.security.CryptoManager;
import com.biglybt.core.security.CryptoManagerFactory;
import com.biglybt.pifimpl.local.ui.config.ParameterImpl;
import com.biglybt.ui.config.ConfigSectionSecurity;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.UIFunctionsSWT;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.auth.CertificateCreatorWindow;
import com.biglybt.ui.swt.config.BaseSwtParameter;
import com.biglybt.ui.swt.shells.MessageBoxShell;

/**
 * @author parg
 *
 */
public class ConfigSectionSecuritySWT
	extends ConfigSectionSecurity
	implements BaseConfigSectionSWT
{

	Shell shell;

	public ConfigSectionSecuritySWT() {
		init(mapPluginParams -> new CertificateCreatorWindow(),
				// Backup Keys
				mapPluginParams -> Utils.execSWTThread(() -> {

					FileDialog dialog = new FileDialog(shell, SWT.APPLICATION_MODAL);

					String target = dialog.open();

					if (target != null) {

						try {
							CryptoManager crypt_man = CryptoManagerFactory.getSingleton();
							String keys = crypt_man.getECCHandler().exportKeys();

							PrintWriter pw = new PrintWriter(new FileWriter(target));

							pw.println(keys);

							pw.close();

						} catch (Throwable e) {

							MessageBoxShell mb = new MessageBoxShell(SWT.ICON_ERROR | SWT.OK,
									MessageText.getString(
											"ConfigView.section.security.op.error.title"),
									MessageText.getString("ConfigView.section.security.op.error",
											new String[] {
												ConfigSectionSecurity.getError(e)
							}));
							mb.setParent(shell);
							mb.open(null);
						}
					}
				}),
				// Restore Keys
				mapPluginParams -> Utils.execSWTThread(() -> {
					FileDialog dialog = new FileDialog(shell, SWT.APPLICATION_MODAL);

					String target = dialog.open();

					if (target != null) {

						try {
							LineNumberReader reader = new LineNumberReader(
									new FileReader(target));

							String str = "";

							try {
								while (true) {

									String line = reader.readLine();

									if (line == null) {

										break;
									}

									str += line + "\r\n";
								}
							} finally {

								reader.close();
							}

							CryptoManager crypt_man = CryptoManagerFactory.getSingleton();
							boolean restart = crypt_man.getECCHandler().importKeys(str);

							if (restart) {

								MessageBoxShell mb = new MessageBoxShell(
										SWT.ICON_INFORMATION | SWT.OK,
										MessageText.getString(
												"ConfigView.section.security.restart.title"),
										MessageText.getString(
												"ConfigView.section.security.restart.msg"));
								mb.setParent(shell);
								mb.open(null);

								UIFunctionsSWT uiFunctions = UIFunctionsManagerSWT.getUIFunctionsSWT();

								if (uiFunctions != null) {

									uiFunctions.dispose(true);
								}
							}
						} catch (Throwable e) {

							MessageBoxShell mb = new MessageBoxShell(SWT.ICON_ERROR | SWT.OK,
									MessageText.getString(
											"ConfigView.section.security.op.error.title"),
									MessageText.getString("ConfigView.section.security.op.error",
											new String[] {
												ConfigSectionSecurity.getError(e)
							}));
							mb.setParent(shell);
							mb.open(null);
						}
					}
				}));
	}

	@Override
	public void configSectionCreate(Composite parent, Map<ParameterImpl, BaseSwtParameter> mapParamToSwtParam) {
		shell = parent.getShell();
	}
}
