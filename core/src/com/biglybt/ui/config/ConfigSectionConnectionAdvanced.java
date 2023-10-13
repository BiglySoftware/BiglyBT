/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.ui.config;

import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.Wiki;
import com.biglybt.pifimpl.local.ui.config.*;
import com.biglybt.platform.PlatformManager;
import com.biglybt.platform.PlatformManagerCapabilities;
import com.biglybt.platform.PlatformManagerFactory;

import com.biglybt.pif.platform.PlatformManagerException;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.config.Parameter;
import com.biglybt.pif.ui.config.ParameterValidator.ValidationInfo;

import static com.biglybt.core.config.ConfigKeys.Connection.*;

public class ConfigSectionConnectionAdvanced
	extends ConfigSectionImpl
{
	public static final String SECTION_ID = "connection.advanced";

	public ConfigSectionConnectionAdvanced() {
		super(SECTION_ID, ConfigSection.SECTION_CONNECTION,
				Parameter.MODE_ADVANCED);
	}

	@Override
	public void build() {

		add(new HyperlinkParameterImpl(
				"ConfigView.section.connection.advanced.info.link",
				Wiki.ADVANCED_NETWORK_SETTINGS));

		///////////////////////   ADVANCED SOCKET SETTINGS GROUP //////////

		List<Parameter> listSocket = new ArrayList<>();

		// max simultaneous

		add(new IntParameterImpl(ICFG_NETWORK_MAX_SIMULTANEOUS_CONNECT_ATTEMPTS,
				"ConfigView.section.connection.network.max.simultaneous.connect.attempts",
				1, 100), listSocket);

		// // max pending

		add(new IntParameterImpl(ICFG_NETWORK_TCP_MAX_CONNECTIONS_OUTSTANDING,
				"ConfigView.section.connection.network.max.outstanding.connect.attempts",
				1, 65536), listSocket);

		// bind ip

		StringParameterImpl paramBindIP = new StringParameterImpl(SCFG_BIND_IP,
				"ConfigView.label.bindip");
		add(paramBindIP, listSocket);

		InfoParameterImpl paramInterfaceList = new InfoParameterImpl(null, null, ""	);
		add("ifList", paramInterfaceList, listSocket);
		paramInterfaceList.setTextSelectable(true);

		BooleanParameterImpl paramInterfaceWithAddresses = new BooleanParameterImpl(
				"ConfigView.section.connection.show.intf.with.addresses", "ConfigView.label.show.intf.with.addresses");
		paramInterfaceWithAddresses.setDefaultValue( true );
		
		Runnable set_intf = ()->{
			paramInterfaceList.setValue(
					MessageText.getString("ConfigView.label.bindip.details", new String[] {
					"\n\t" + NetworkAdmin.getSingleton().getNetworkInterfacesAsString(
							paramInterfaceWithAddresses.getValue()).replaceAll(
							"\n", "\n\t")
				}));
		};
				
		paramInterfaceWithAddresses.addAndFireListener((n)->{
		
			set_intf.run();
		});
		
		add(paramInterfaceWithAddresses, listSocket);
	
		StringParameterImpl paramAdditionServiceBind = new StringParameterImpl(SCFG_NETWORK_ADDITIONAL_SERVICE_BINDS,
				"ConfigView.label.additional.service.bind");
		add(paramAdditionServiceBind, listSocket);
		
		BooleanParameterImpl paramIgnoreBindLAN = new BooleanParameterImpl(
				BCFG_NETWORK_IGNORE_BIND_FOR_LAN, "ConfigView.label.ignore.bind.for.lan");
		add(paramIgnoreBindLAN, listSocket);

		BooleanParameterImpl paramCheckBind = new BooleanParameterImpl(
				BCFG_CHECK_BIND_IP_ON_START, "network.check.ipbinding");
		add(paramCheckBind, listSocket);

		BooleanParameterImpl paramForceBind = new BooleanParameterImpl(
				BCFG_ENFORCE_BIND_IP, "network.enforce.ipbinding");
		add(paramForceBind, listSocket);

		BooleanParameterImpl paramForceBindPause = new BooleanParameterImpl(
				BCFG_ENFORCE_BIND_IP_PAUSE, "network.enforce.ipbinding.pause");
		add(paramForceBindPause, listSocket);
		paramForceBindPause.setIndent(1, true);

		paramForceBind.addEnabledOnSelection(paramForceBindPause);

		BooleanParameterImpl paramBindIcon = new BooleanParameterImpl(
				BCFG_SHOW_IP_BINDINGS_ICON, "network.ipbinding.icon.show");
		add(paramBindIcon, listSocket);
		paramBindIcon.setAllowedUiTypes(UIInstance.UIT_SWT);

		BooleanParameterImpl paramVPNGuess = new BooleanParameterImpl(
				BCFG_NETWORK_ADMIN_MAYBE_VPN_ENABLE, "network.admin.maybe.vpn.enable");
		paramVPNGuess.setAllowedUiTypes(UIInstance.UIT_SWT);
		add(paramVPNGuess, listSocket);

		IntParameterImpl paramPortBind = new IntParameterImpl(
				ICFG_NETWORK_BIND_LOCAL_PORT,
				"ConfigView.section.connection.advanced.bind_port", 0, 65535);
		add(paramPortBind, listSocket);

		IntParameterImpl paramMtuSize = new IntParameterImpl(
				ICFG_NETWORK_TCP_MTU_SIZE, "ConfigView.section.connection.advanced.mtu",
				0, 512 * 1024);
		add(paramMtuSize, listSocket);

		// sndbuf

		IntParameterImpl paramSoSndBuf = new IntParameterImpl(
				ICFG_NETWORK_TCP_SOCKET_SO_SNDBUF,
				"ConfigView.section.connection.advanced.SO_SNDBUF");
		add(paramSoSndBuf);

		LabelParameterImpl paramSendCurr = new LabelParameterImpl("");
		add("sendcur", paramSendCurr);

		ParameterGroupImpl pgSoSnd = new ParameterGroupImpl(null, paramSoSndBuf,
				paramSendCurr);
		add("pgSoSnd", pgSoSnd, listSocket);
		pgSoSnd.setNumberOfColumns(2);

		// rcvbuf

		IntParameterImpl paramSoRcvBuf = new IntParameterImpl(
				ICFG_NETWORK_TCP_SOCKET_SO_RCVBUF,
				"ConfigView.section.connection.advanced.SO_RCVBUF");
		add(paramSoRcvBuf);

		LabelParameterImpl paramRcvCurr = new LabelParameterImpl("");
		add("rcvcur", paramRcvCurr);

		ParameterGroupImpl pgSoRcv = new ParameterGroupImpl(null, paramSoRcvBuf,
				paramRcvCurr);
		add("pgSoRcv", pgSoRcv, listSocket);
		pgSoRcv.setNumberOfColumns(2);

		final Runnable buff_updater = () -> {
			SocketChannel sc = null;

			int snd_val = 0;
			int rec_val = 0;

			try {
				sc = SocketChannel.open();

				Socket socket = sc.socket();

				if (paramSoSndBuf.getValue() == 0) {

					snd_val = socket.getSendBufferSize();
				}

				if (paramSoRcvBuf.getValue() == 0) {

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
				paramSendCurr.setLabelText("");
			} else {
				paramSendCurr.setLabelText(
						MessageText.getString("label.current.equals", new String[] {
							String.valueOf(snd_val)
				}));
			}

			if (rec_val == 0) {
				paramRcvCurr.setLabelText("");
			} else {
				paramRcvCurr.setLabelText(
						MessageText.getString("label.current.equals", new String[] {
							String.valueOf(rec_val)
				}));
			}
		};

		buff_updater.run();

		paramSoRcvBuf.addListener(param -> buff_updater.run());
		paramSoSndBuf.addListener(param -> buff_updater.run());

		StringParameterImpl paramIPDiffServ = new StringParameterImpl(
				SCFG_NETWORK_TCP_SOCKET_IPDIFF_SERV,
				"ConfigView.section.connection.advanced.IPDiffServ");
		add(paramIPDiffServ, listSocket);
		paramIPDiffServ.setWidthInCharacters(15);

		paramIPDiffServ.setValidChars("0123456789xABCDEF", false);
		paramIPDiffServ.addStringValidator((p, toValue) -> {
			boolean valid;
			String reason = null;
			if (toValue.length() == 0) {
				valid = true;
			} else {
				try {
					int value = Integer.decode(toValue);
					valid = value >= 0 && value <= 255;
					if (!valid) {
						reason = "Not within range of 0 - 255";
					}
				} catch (Throwable t) {
					valid = false;
					reason = t.getMessage();
				}
			}
			return new ValidationInfo(valid, reason);
		});

		//do simple input verification, and registry key setting for TOS field
		paramIPDiffServ.addListener(p -> {
			String raw = ((StringParameterImpl) p).getValue();
			int value = -1;

			try {
				value = Integer.decode(raw);
			} catch (Throwable ignore) {
			}

			if (value < 0 || value > 255) { //invalid or no value entered
				COConfigurationManager.removeParameter(
						SCFG_NETWORK_TCP_SOCKET_IPDIFF_SERV);

				enableTOSRegistrySetting(false); //disable registry setting if necessary
			} else { //passes test
				enableTOSRegistrySetting(true); //enable registry setting if necessary
			}
		});

		// read select

		IntParameterImpl paramReadSelect = new IntParameterImpl(
				ICFG_NETWORK_TCP_READ_SELECT_TIME, "", 10, 250);
		add(paramReadSelect, listSocket);
		paramReadSelect.setLabelText(MessageText.getString(
				"ConfigView.section.connection.advanced.read_select", new String[] {
					String.valueOf(COConfigurationManager.getDefault(
							ICFG_NETWORK_TCP_READ_SELECT_TIME))
				}));

		IntParameterImpl paramReadSelectMin = new IntParameterImpl(
				ICFG_NETWORK_TCP_READ_SELECT_MIN_TIME, "", 0, 100);
		add(paramReadSelectMin, listSocket);
		paramReadSelectMin.setLabelText(
				MessageText.getString(
						"ConfigView.section.connection.advanced.read_select_min",
						new String[] {
							String.valueOf(COConfigurationManager.getDefault(
									ICFG_NETWORK_TCP_READ_SELECT_MIN_TIME))
						}));

		// write select

		IntParameterImpl paramWriteSelect = new IntParameterImpl(
				ICFG_NETWORK_TCP_WRITE_SELECT_TIME, "", 10, 250);
		add(paramWriteSelect, listSocket);
		paramWriteSelect.setLabelText(
				MessageText.getString(
						"ConfigView.section.connection.advanced.write_select",
						new String[] {
							String.valueOf(COConfigurationManager.getDefault(
									ICFG_NETWORK_TCP_WRITE_SELECT_TIME))
						}));

		IntParameterImpl paramWriteSelectMin = new IntParameterImpl(
				ICFG_NETWORK_TCP_WRITE_SELECT_MIN_TIME, "", 0, 100);
		add(paramWriteSelectMin, listSocket);
		paramWriteSelectMin.setLabelText(MessageText.getString(
				"ConfigView.section.connection.advanced.write_select_min",
				new String[] {
					String.valueOf(COConfigurationManager.getDefault(
							ICFG_NETWORK_TCP_WRITE_SELECT_MIN_TIME))
				}));

		add(new ParameterGroupImpl(
				"ConfigView.section.connection.advanced.socket.group", listSocket));
		
		BooleanParameterImpl ipv6_enable = 
			add(new BooleanParameterImpl(BCFG_IPV_6_ENABLE_SUPPORT,
				"network.ipv6.enable.support"));
		
		BooleanParameterImpl ipv6_checks = 
			add(new BooleanParameterImpl(BCFG_IPV_6_CHECK_MULTIPLE_ADDRESS_CHECKS,
				"network.ipv6.enable.multiple.address.checks"));
		
		ipv6_checks.setIndent( 1, true );
		ipv6_enable.addEnabledOnSelection(ipv6_checks);
		
		StringParameterImpl ipv6_extra_globals = 
				add(new StringParameterImpl(SCFG_IPV_6_EXTRA_GLOBALS, "network.ipv6.extra.global"));
			
		ipv6_extra_globals.setGenerateIntermediateEvents( false );
		ipv6_extra_globals.setIndent( 1, true );
		ipv6_enable.addEnabledOnSelection(ipv6_extra_globals);
		
		add(new BooleanParameterImpl(BCFG_IPV_6_PREFER_ADDRESSES, "network.ipv6.prefer.addresses"));

		if (Constants.isWindowsVistaOrHigher) {

			add(new BooleanParameterImpl(BCFG_IPV_4_PREFER_STACK, "network.ipv4.prefer.stack"));
		}
		
		List<Parameter> listNIIgnore = new ArrayList<>();
		
		add(new LabelParameterImpl( "connection.advanced.ni.ignore.info"), listNIIgnore);
		add(new BooleanParameterImpl(BCFG_IPV_4_IGNORE_NI_ADDRESSES, "label.ignore.ipv4"), listNIIgnore);
		add(new BooleanParameterImpl(BCFG_IPV_6_IGNORE_NI_ADDRESSES, "label.ignore.ipv6"), listNIIgnore);

		add(new ParameterGroupImpl(
				"connection.advanced.ni.ignore.group", listNIIgnore ));

		add(new StringParameterImpl(SCFG_CONNECTION_TEST_DOMAIN,
			"connection.test.domain"));
	}

	private static void enableTOSRegistrySetting(boolean enable) {
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
