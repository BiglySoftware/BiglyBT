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

import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.pifimpl.local.ui.config.*;

import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.config.Parameter;
import com.biglybt.pif.ui.config.ParameterListener;

import static com.biglybt.core.config.ConfigKeys.Connection.*;

public class ConfigSectionConnectionProxy
	extends ConfigSectionImpl
{

	public static final String ID_BTN_TEST = "btnProxyTest";

	public static final String SECTION_ID = "proxy";

	private ConfigDetailsCallback cbTestProxy;

	public ConfigSectionConnectionProxy() {
		super(SECTION_ID, ConfigSection.SECTION_CONNECTION,
				Parameter.MODE_ADVANCED);
	}

	public void init(ConfigDetailsCallback cbTestProxy) {
		this.cbTestProxy = cbTestProxy;
	}

	@Override
	public void build() {

		//////////////////////  PROXY GROUP /////////////////

		List<Parameter> listProxyTracker = new ArrayList<>();

		BooleanParameterImpl enableProxy = new BooleanParameterImpl(
				BCFG_ENABLE_PROXY, "ConfigView.section.proxy.enable_proxy");
		add(enableProxy, listProxyTracker);

		BooleanParameterImpl enableSocks = new BooleanParameterImpl(
				BCFG_ENABLE_SOCKS, "ConfigView.section.proxy.enable_socks");
		add(enableSocks, listProxyTracker);

		StringParameterImpl pHost = new StringParameterImpl(SCFG_PROXY_HOST,
				"ConfigView.section.proxy.host");
		add(pHost, listProxyTracker);
		pHost.setWidthInCharacters(20);

		IntParameterImpl pPort = new IntParameterImpl(SCFG_PROXY_PORT,
				"ConfigView.section.proxy.port", 0, 65535);
		// String :(
		pPort.setStoredAsString(true, 0);
		add(pPort, listProxyTracker);

		StringParameterImpl pUser = new StringParameterImpl(SCFG_PROXY_USERNAME,
				"ConfigView.section.proxy.username");
		add(pUser, listProxyTracker);
		pUser.setWidthInCharacters(12);

		StringParameterImpl pPass = new StringParameterImpl(SCFG_PROXY_PASSWORD,
				"ConfigView.section.proxy.password");
		add(pPass, listProxyTracker);
		pPass.setWidthInCharacters(12);

		BooleanParameterImpl trackerDNSKill = new BooleanParameterImpl(
				BCFG_PROXY_SOCKS_TRACKER_DNS_DISABLE,
				"ConfigView.section.proxy.no.local.dns");
		add(trackerDNSKill, listProxyTracker);

		if (cbTestProxy != null) {
			ActionParameterImpl btnTest = new ActionParameterImpl(null,
					"ConfigView.section.proxy.testsocks");
			add(ID_BTN_TEST, btnTest, listProxyTracker);

			btnTest.addListener(param -> cbTestProxy.run(mapPluginParams));
		}

		add("gProxyTracker", new ParameterGroupImpl(
				"ConfigView.section.proxy.group.tracker", listProxyTracker));

		////////////////////////////////////////////////

		List<Parameter> listProxyPeer = new ArrayList<>();

		BooleanParameterImpl enableSocksPeer = new BooleanParameterImpl(
				BCFG_PROXY_DATA_ENABLE, "ConfigView.section.proxy.enable_socks.peer");
		add(enableSocksPeer, listProxyPeer);

		BooleanParameterImpl socksPeerInform = new BooleanParameterImpl(
				BCFG_PROXY_DATA_SOCKS_INFORM,
				"ConfigView.section.proxy.peer.informtracker");
		add(socksPeerInform, listProxyPeer);

		String[] socks_types = {
			"V4",
			"V4a",
			"V5"
		};
		String[] dropLabels = new String[socks_types.length];
		String[] dropValues = new String[socks_types.length];
		for (int i = 0; i < socks_types.length; i++) {
			dropLabels[i] = socks_types[i];
			dropValues[i] = socks_types[i];
		}
		StringListParameterImpl socksType = new StringListParameterImpl(
				SCFG_PROXY_DATA_SOCKS_VERSION, "ConfigView.section.proxy.socks.version",
				dropLabels, dropValues);
		add(socksType, listProxyPeer);

		BooleanParameterImpl sameConfig = new BooleanParameterImpl(
				BCFG_PROXY_DATA_SAME, "ConfigView.section.proxy.peer.same");
		add(sameConfig, listProxyPeer);

		/**/

		List<Parameter> listProxyPeerServers = new ArrayList<>();

		List<ParameterImpl> pp_params = new ArrayList<>();

		for (int i = 1; i <= COConfigurationManager.MAX_DATA_SOCKS_PROXIES; i++) {

			String suffix = i == 1 ? "" : ("." + i);

			StringParameterImpl pDataHost = new StringParameterImpl(
					SCFG_PREFIX_PROXY_DATA_HOST + suffix,
					"ConfigView.section.proxy.host");
			add(pDataHost);
			pDataHost.setWidthInCharacters(15);

			IntParameterImpl pDataPort = new IntParameterImpl(
					SCFG_PREFIX_PROXY_DATA_PORT + suffix,
					"ConfigView.section.proxy.port");
			add(pDataPort);
			pDataPort.setStoredAsString(true, 0);

			StringParameterImpl pDataUser = new StringParameterImpl(
					SCFG_PREFIX_PROXY_DATA_USERNAME + suffix,
					"ConfigView.section.proxy.username");
			add(pDataUser);
			pDataUser.setWidthInCharacters(12);

			StringParameterImpl pDataPass = new StringParameterImpl(
					SCFG_PREFIX_PROXY_DATA_PASSWORD + suffix,
					"ConfigView.section.proxy.password");
			add(pDataPass);
			pDataPass.setWidthInCharacters(12);

			ParameterGroupImpl pgProxyServer = new ParameterGroupImpl("", pDataHost,
					pDataPort, pDataUser, pDataPass);
			add(pgProxyServer, listProxyPeerServers);
			pgProxyServer.setGroupTitle(MessageText.getString(
					"ConfigView.section.proxy.servergroup", new String[] {
						"" + i
					}));

			pp_params.add(pDataHost);
			pp_params.add(pDataPort);
			pp_params.add(pDataUser);
			pp_params.add(pDataPass);
		}

		ParameterGroupImpl gProxyPeerServers = new ParameterGroupImpl(null,
				listProxyPeerServers);
		gProxyPeerServers.setNumberOfColumns(
				COConfigurationManager.MAX_DATA_SOCKS_PROXIES);
		add("gProxyPeerServers", gProxyPeerServers, listProxyPeer);

		ParameterGroupImpl gProxyPeer = new ParameterGroupImpl(
				"ConfigView.section.proxy.group.peer", listProxyPeer);
		add("gProxyPeer", gProxyPeer);

		final ParameterImpl[] proxy_controls = new ParameterImpl[] {
			enableSocks,
			pHost,
			pPort,
			pUser,
			pPass
		};

		ParameterListener proxy_enabler = p -> {
			for (ParameterImpl proxy_control : proxy_controls) {

				proxy_control.setEnabled(enableProxy.getValue());
			}
		};
		enableSocks.addListener(proxy_enabler);
		enableProxy.addListener(proxy_enabler);

		ParameterImpl[] proxy_peer_controls = pp_params.toArray(
				new ParameterImpl[0]);

		ParameterImpl[] proxy_peer_details = new ParameterImpl[] {
			sameConfig,
			socksPeerInform,
			socksType
		};

		ParameterListener proxy_peer_enabler = p -> {
			for (ParameterImpl param : proxy_peer_controls) {

				param.setEnabled(enableSocksPeer.getValue() && !sameConfig.getValue());
			}

			for (ParameterImpl detail : proxy_peer_details) {

				detail.setEnabled(enableSocksPeer.getValue());
			}
		};

		enableSocksPeer.addListener(proxy_peer_enabler);
		sameConfig.addListener(proxy_peer_enabler);

		proxy_enabler.parameterChanged(null);
		proxy_peer_enabler.parameterChanged(null);

		// dns info

		add(new LabelParameterImpl("ConfigView.section.proxy.dns.info"));

		// disable plugin proxies

		BooleanParameterImpl disablepps = new BooleanParameterImpl(
				BCFG_PROXY_SOCKS_DISABLE_PLUGIN_PROXIES,
				"ConfigView.section.proxy.disable.plugin.proxies");
		add(disablepps);

		// check on start

		BooleanParameterImpl checkOnStart = new BooleanParameterImpl(
				BCFG_PROXY_CHECK_ON_START, "ConfigView.section.proxy.check.on.start");
		add(checkOnStart);

		// icon

		BooleanParameterImpl showIcon = new BooleanParameterImpl(
				BCFG_PROXY_SOCKS_SHOW_ICON, "ConfigView.section.proxy.show_icon");
		add(showIcon);
		showIcon.setAllowedUiTypes(UIInstance.UIT_SWT);

		BooleanParameterImpl flagIncoming = new BooleanParameterImpl(
				BCFG_PROXY_SOCKS_SHOW_ICON_FLAG_INCOMING,
				"ConfigView.section.proxy.show_icon.flag.incoming");
		add(flagIncoming);
		flagIncoming.setIndent(1, true);
		flagIncoming.setAllowedUiTypes(UIInstance.UIT_SWT);

		showIcon.addEnabledOnSelection(flagIncoming);

		// username info

		add(new LabelParameterImpl("ConfigView.section.proxy.username.info"));
	}
}
