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

import java.net.Socket;
import java.nio.channels.SocketChannel;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.impl.ConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.platform.PlatformManager;
import com.biglybt.platform.PlatformManagerCapabilities;
import com.biglybt.platform.PlatformManagerFactory;
import com.biglybt.pif.platform.PlatformManagerException;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.LinkLabel;
import com.biglybt.ui.swt.config.*;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.pif.UISWTConfigSection;

import com.biglybt.core.networkmanager.admin.NetworkAdmin;

public class ConfigSectionConnectionAdvanced
	implements UISWTConfigSection
{

	private final static int REQUIRED_MODE = 2;

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
		return "connection.advanced";
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
		Utils.setLayoutData(cSection, gridData);
		GridLayout advanced_layout = new GridLayout();
		cSection.setLayout(advanced_layout);

		int userMode = COConfigurationManager.getIntParameter("User Mode");
		if (userMode < REQUIRED_MODE) {
			Label label = new Label(cSection, SWT.WRAP);
			gridData = new GridData();
			Utils.setLayoutData(label, gridData);

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

		new LinkLabel(cSection, gridData,
				"ConfigView.section.connection.advanced.info.link",
				MessageText.getString("ConfigView.section.connection.advanced.url"));

		///////////////////////   ADVANCED SOCKET SETTINGS GROUP //////////

		Group gSocket = new Group(cSection, SWT.NULL);
		Messages.setLanguageText(gSocket,
				"ConfigView.section.connection.advanced.socket.group");
		gridData = new GridData(
				GridData.VERTICAL_ALIGN_FILL | GridData.FILL_HORIZONTAL);
		Utils.setLayoutData(gSocket, gridData);
		GridLayout glayout = new GridLayout();
		glayout.numColumns = 3;
		gSocket.setLayout(glayout);

		// max simultaneous

		Label lmaxout = new Label(gSocket, SWT.NULL);
		Messages.setLanguageText(lmaxout,
				"ConfigView.section.connection.network.max.simultaneous.connect.attempts");
		gridData = new GridData();
		Utils.setLayoutData(lmaxout, gridData);

		IntParameter max_connects = new IntParameter(gSocket,
				"network.max.simultaneous.connect.attempts", 1, 100);
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		max_connects.setLayoutData(gridData);

		// // max pending

		Label lmaxpout = new Label(gSocket, SWT.NULL);
		Messages.setLanguageText(lmaxpout,
				"ConfigView.section.connection.network.max.outstanding.connect.attempts");
		gridData = new GridData();
		Utils.setLayoutData(lmaxpout, gridData);

		IntParameter max_pending_connects = new IntParameter(gSocket,
				"network.tcp.max.connections.outstanding", 1, 65536);
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		max_pending_connects.setLayoutData(gridData);

		// bind ip

		Label lbind = new Label(gSocket, SWT.NULL);
		Messages.setLanguageText(lbind, "ConfigView.label.bindip");
		gridData = new GridData();
		Utils.setLayoutData(lbind, gridData);

		StringParameter bindip = new StringParameter(gSocket, "Bind IP", "", false);
		gridData = new GridData();
		gridData.widthHint = 100;
		gridData.horizontalSpan = 2;
		bindip.setLayoutData(gridData);

		Text lbind2 = new Text(gSocket, SWT.READ_ONLY | SWT.MULTI);
		lbind2.setTabs(8);
		Messages.setLanguageText(lbind2, "ConfigView.label.bindip.details",
				new String[] {
					"\t" + NetworkAdmin.getSingleton().getNetworkInterfacesAsString().replaceAll(
							"\\\n", "\n\t")
				});
		gridData = new GridData();
		gridData.horizontalSpan = 3;
		Utils.setLayoutData(lbind2, gridData);

		BooleanParameter check_bind = new BooleanParameter(gSocket,
				"Check Bind IP On Start", "network.check.ipbinding");
		gridData = new GridData();
		gridData.horizontalSpan = 3;
		check_bind.setLayoutData(gridData);

		BooleanParameter force_bind = new BooleanParameter(gSocket,
				"Enforce Bind IP", "network.enforce.ipbinding");
		gridData = new GridData();
		gridData.horizontalSpan = 3;
		force_bind.setLayoutData(gridData);

		BooleanParameter bind_icon = new BooleanParameter(gSocket,
				"Show IP Bindings Icon", "network.ipbinding.icon.show");
		gridData = new GridData();
		gridData.horizontalSpan = 3;
		bind_icon.setLayoutData(gridData);

		BooleanParameter vpn_guess_enable = new BooleanParameter(gSocket,
				"network.admin.maybe.vpn.enable", "network.admin.maybe.vpn.enable");
		gridData = new GridData();
		gridData.horizontalSpan = 3;
		vpn_guess_enable.setLayoutData(gridData);

		Label lpbind = new Label(gSocket, SWT.NULL);
		Messages.setLanguageText(lpbind,
				"ConfigView.section.connection.advanced.bind_port");
		final IntParameter port_bind = new IntParameter(gSocket,
				"network.bind.local.port", 0, 65535);
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		port_bind.setLayoutData(gridData);

		Label lmtu = new Label(gSocket, SWT.NULL);
		Messages.setLanguageText(lmtu,
				"ConfigView.section.connection.advanced.mtu");
		final IntParameter mtu_size = new IntParameter(gSocket,
				"network.tcp.mtu.size");
		mtu_size.setMaximumValue(512 * 1024);
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		mtu_size.setLayoutData(gridData);

		// sndbuf

		Label lsend = new Label(gSocket, SWT.NULL);
		Messages.setLanguageText(lsend,
				"ConfigView.section.connection.advanced.SO_SNDBUF");
		final IntParameter SO_SNDBUF = new IntParameter(gSocket,
				"network.tcp.socket.SO_SNDBUF");
		gridData = new GridData();
		SO_SNDBUF.setLayoutData(gridData);

		final Label lsendcurr = new Label(gSocket, SWT.NULL);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalIndent = 10;
		Utils.setLayoutData(lsendcurr, gridData);

		// rcvbuf

		Label lreceiv = new Label(gSocket, SWT.NULL);
		Messages.setLanguageText(lreceiv,
				"ConfigView.section.connection.advanced.SO_RCVBUF");
		final IntParameter SO_RCVBUF = new IntParameter(gSocket,
				"network.tcp.socket.SO_RCVBUF");
		gridData = new GridData();
		SO_RCVBUF.setLayoutData(gridData);

		final Label lreccurr = new Label(gSocket, SWT.NULL);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalIndent = 10;
		Utils.setLayoutData(lreccurr, gridData);

		final Runnable buff_updater = new Runnable() {
			@Override
			public void run() {
				SocketChannel sc = null;

				int snd_val = 0;
				int rec_val = 0;

				try {
					sc = SocketChannel.open();

					Socket socket = sc.socket();

					if (SO_SNDBUF.getValue() == 0) {

						snd_val = socket.getSendBufferSize();
					}

					if (SO_RCVBUF.getValue() == 0) {

						rec_val = socket.getReceiveBufferSize();
					}
				} catch (Throwable e) {

				} finally {

					try {
						sc.close();

					} catch (Throwable e) {
					}
				}

				if (snd_val == 0) {
					lsendcurr.setText("");
				} else {
					Messages.setLanguageText(lsendcurr, "label.current.equals",
							new String[] {
								String.valueOf(snd_val)
					});
				}

				if (rec_val == 0) {
					lreccurr.setText("");
				} else {
					Messages.setLanguageText(lreccurr, "label.current.equals",
							new String[] {
								String.valueOf(rec_val)
					});
				}
			}
		};

		buff_updater.run();

		ParameterChangeAdapter buff_listener = new ParameterChangeAdapter() {
			@Override
			public void parameterChanged(Parameter p, boolean caused_internally) {
				buff_updater.run();
			}
		};

		SO_RCVBUF.addChangeListener(buff_listener);
		SO_SNDBUF.addChangeListener(buff_listener);

		Label ltos = new Label(gSocket, SWT.NULL);
		Messages.setLanguageText(ltos,
				"ConfigView.section.connection.advanced.IPDiffServ");
		final StringParameter IPDiffServ = new StringParameter(gSocket,
				"network.tcp.socket.IPDiffServ");
		gridData = new GridData();
		gridData.widthHint = 100;
		gridData.horizontalSpan = 2;
		IPDiffServ.setLayoutData(gridData);

		//do simple input verification, and registry key setting for TOS field
		IPDiffServ.addChangeListener(new ParameterChangeAdapter() {

			final Color obg = IPDiffServ.getControl().getBackground();

			final Color ofg = IPDiffServ.getControl().getForeground();

			@Override
			public void parameterChanged(Parameter p, boolean caused_internally) {
				String raw = IPDiffServ.getValue();
				int value = -1;

				try {
					value = Integer.decode(raw).intValue();
				} catch (Throwable t) {
				}

				if (value < 0 || value > 255) { //invalid or no value entered
					ConfigurationManager.getInstance().removeParameter(
							"network.tcp.socket.IPDiffServ");

					if (raw != null && raw.length() > 0) { //error state
						IPDiffServ.getControl().setBackground(Colors.red);
						IPDiffServ.getControl().setForeground(Colors.white);
					} else { //no value state
						IPDiffServ.getControl().setBackground(obg);
						IPDiffServ.getControl().setForeground(ofg);
					}

					enableTOSRegistrySetting(false); //disable registry setting if necessary
				} else { //passes test
					IPDiffServ.getControl().setBackground(obg);
					IPDiffServ.getControl().setForeground(ofg);

					enableTOSRegistrySetting(true); //enable registry setting if necessary
				}
			}
		});

		// read select

		Label lreadsel = new Label(gSocket, SWT.NULL);
		Messages.setLanguageText(lreadsel,
				"ConfigView.section.connection.advanced.read_select", new String[] {
					String.valueOf(
							COConfigurationManager.getDefault("network.tcp.read.select.time"))
				});
		final IntParameter read_select = new IntParameter(gSocket,
				"network.tcp.read.select.time", 10, 250);
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		read_select.setLayoutData(gridData);

		Label lreadselmin = new Label(gSocket, SWT.NULL);
		Messages.setLanguageText(lreadselmin,
				"ConfigView.section.connection.advanced.read_select_min", new String[] {
					String.valueOf(COConfigurationManager.getDefault(
							"network.tcp.read.select.min.time"))
				});
		final IntParameter read_select_min = new IntParameter(gSocket,
				"network.tcp.read.select.min.time", 0, 100);
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		read_select_min.setLayoutData(gridData);

		// write select

		Label lwritesel = new Label(gSocket, SWT.NULL);
		Messages.setLanguageText(lwritesel,
				"ConfigView.section.connection.advanced.write_select", new String[] {
					String.valueOf(COConfigurationManager.getDefault(
							"network.tcp.write.select.time"))
				});
		final IntParameter write_select = new IntParameter(gSocket,
				"network.tcp.write.select.time", 10, 250);
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		write_select.setLayoutData(gridData);

		Label lwriteselmin = new Label(gSocket, SWT.NULL);
		Messages.setLanguageText(lwriteselmin,
				"ConfigView.section.connection.advanced.write_select_min",
				new String[] {
					String.valueOf(COConfigurationManager.getDefault(
							"network.tcp.write.select.min.time"))
				});
		final IntParameter write_select_min = new IntParameter(gSocket,
				"network.tcp.write.select.min.time", 0, 100);
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		write_select_min.setLayoutData(gridData);

		new BooleanParameter(cSection, "IPV6 Enable Support",
				"network.ipv6.enable.support");

		new BooleanParameter(cSection, "IPV6 Prefer Addresses",
				"network.ipv6.prefer.addresses");

		if (Constants.isWindowsVistaOrHigher && Constants.isJava7OrHigher) {

			new BooleanParameter(cSection, "IPV4 Prefer Stack",
					"network.ipv4.prefer.stack");
		}

		//////////////////////////////////////////////////////////////////////////

		return cSection;

	}

	private void enableTOSRegistrySetting(boolean enable) {
		PlatformManager mgr = PlatformManagerFactory.getPlatformManager();

		if (mgr.hasCapability(PlatformManagerCapabilities.SetTCPTOSEnabled)) {
			//see http://wiki.biblybt.com/w/AdvancedNetworkSettings
			try {
				mgr.setTCPTOSEnabled(enable);
			} catch (PlatformManagerException pe) {
				Debug.printStackTrace(pe);
			}
		}
	}

}
