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
import com.biglybt.core.peer.PEPeerSource;
import com.biglybt.core.util.AENetworkClassifier;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.util.Wiki;
import com.biglybt.pifimpl.local.ui.config.*;

import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.config.IntParameter;
import com.biglybt.pif.ui.config.Parameter;
import com.biglybt.pif.ui.config.ParameterValidator.ValidationInfo;

import static com.biglybt.core.config.ConfigKeys.Connection.*;
import static com.biglybt.core.config.ConfigKeys.*;

public class ConfigSectionConnection
	extends ConfigSectionImpl
{

	public ConfigSectionConnection() {
		super(ConfigSection.SECTION_CONNECTION, ConfigSection.SECTION_ROOT);
	}

	@Override
	public void build() {
		List<Parameter> listTopParams = new ArrayList<>();

		int userMode = COConfigurationManager.getIntParameter(ICFG_USER_MODE);
		boolean separate_ports = userMode > 1
				|| COConfigurationManager.getIntParameter(
						ICFG_TCP_LISTEN_PORT) != COConfigurationManager.getIntParameter(
								ICFG_UDP_LISTEN_PORT);

		IntParameterImpl paramTCPListenPort = new IntParameterImpl(
				ICFG_TCP_LISTEN_PORT, separate_ports
						? "ConfigView.label.tcplistenport" : "ConfigView.label.serverport",
				1, 65535);
		add(paramTCPListenPort, listTopParams);

		// XXX Test this when linked incase recursive
		paramTCPListenPort.addIntegerValidator((p, toValue) -> {
			if (toValue == Constants.INSTANCE_PORT) {
				return new ValidationInfo(false,
						"Can't be same port as biglybt.instance.port");
			}

			return new ValidationInfo(true);
		});
		paramTCPListenPort.addListener(p -> {
			if (!separate_ports) {
				int value = ((IntParameterImpl) p).getValue();
				COConfigurationManager.setParameter(ICFG_UDP_LISTEN_PORT, value);
				COConfigurationManager.setParameter(ICFG_UDP_NON_DATA_LISTEN_PORT,
						value);
			}
		});

		if (separate_ports) {
			boolean MULTI_UDP = COConfigurationManager.ENABLE_MULTIPLE_UDP_PORTS
					&& userMode > 1;

			IntParameterImpl paramUDPListenPort = new IntParameterImpl(
					ICFG_UDP_LISTEN_PORT, "ConfigView.label.udplistenport", 1, 65535);
			add(paramUDPListenPort, listTopParams);

			paramUDPListenPort.addIntegerValidator((p, toValue) -> {
				if (toValue == Constants.INSTANCE_PORT) {
					return new ValidationInfo(false,
							"Can't be same port as biglybt.instance.port");
				}

				return new ValidationInfo(true);
			});
			paramUDPListenPort.addListener(p -> {
				if (!MULTI_UDP) {
					COConfigurationManager.setParameter(ICFG_UDP_NON_DATA_LISTEN_PORT,
							((IntParameter) p).getValue());
				}
			});

			if (MULTI_UDP) {
				BooleanParameterImpl paramNonDataSamePort = new BooleanParameterImpl(
						ICFG_UDP_NON_DATA_LISTEN_PORT_SAME,
						"ConfigView.section.connection.nondata.udp.same");
				add(paramNonDataSamePort);
				paramNonDataSamePort.addListener(param -> {
					if (!param.hasBeenSet()) {
						COConfigurationManager.removeParameter(
								ICFG_UDP_NON_DATA_LISTEN_PORT);
					}
				});

				IntParameterImpl paramNonDataUDPPort = new IntParameterImpl(
						ICFG_UDP_NON_DATA_LISTEN_PORT, null);
				add(paramNonDataUDPPort);

				paramNonDataUDPPort.addIntegerValidator((p, toValue) -> {
					if (toValue == Constants.INSTANCE_PORT) {
						return new ValidationInfo(false,
								"Can't be same port as biglybt.instance.port");
					}

					return new ValidationInfo(true);
				});

				ParameterGroupImpl paramGroupMulti = new ParameterGroupImpl(null,
						paramNonDataSamePort, paramNonDataUDPPort);
				paramGroupMulti.setNumberOfColumns(3);
				add("gMultiUDP", paramGroupMulti, listTopParams);

				paramUDPListenPort.addListener(p -> {
					if (!paramNonDataSamePort.getValue()) {
						return;
					}

					int udp_listen_port = ((IntParameterImpl) p).getValue();

					if (udp_listen_port != Constants.INSTANCE_PORT) {

						COConfigurationManager.setParameter(ICFG_UDP_NON_DATA_LISTEN_PORT,
								udp_listen_port);

						paramNonDataUDPPort.setValue(udp_listen_port);
					}
				});

				paramNonDataSamePort.addDisabledOnSelection(paramNonDataUDPPort);

				paramNonDataSamePort.addListener(p -> {
					if (!((BooleanParameterImpl) p).getValue()) {
						return;
					}

					int udp_listen_port = COConfigurationManager.getIntParameter(
							ICFG_UDP_LISTEN_PORT);

					if (COConfigurationManager.getIntParameter(
							ICFG_UDP_NON_DATA_LISTEN_PORT) != udp_listen_port) {

						COConfigurationManager.setParameter(ICFG_UDP_NON_DATA_LISTEN_PORT,
								udp_listen_port);

						paramNonDataUDPPort.setValue(udp_listen_port);
					}
				});

				BooleanParameterImpl paramEnableTCP = new BooleanParameterImpl(
						BCFG_TCP_LISTEN_PORT_ENABLE,
						"ConfigView.section.connection.tcp.enable");
				add(paramEnableTCP, listTopParams);

				BooleanParameterImpl paramEnableUDP = new BooleanParameterImpl(
						BCFG_UDP_LISTEN_PORT_ENABLE,
						"ConfigView.section.connection.udp.enable");
				add(paramEnableUDP, listTopParams);

				paramEnableTCP.addEnabledOnSelection(paramTCPListenPort);
				paramEnableUDP.addEnabledOnSelection(paramUDPListenPort);
			}
		}

		add("g0", new ParameterGroupImpl(null, listTopParams));

		BooleanParameterImpl paramRandEnable = new BooleanParameterImpl(
				BCFG_LISTEN_PORT_RANDOMIZE_ENABLE,
				"ConfigView.section.connection.port.rand.enable");
		add(paramRandEnable, Parameter.MODE_INTERMEDIATE);

		StringParameterImpl paramRandRange = new StringParameterImpl(
				SCFG_LISTEN_PORT_RANDOMIZE_RANGE,
				"ConfigView.section.connection.port.rand.range");
		add(paramRandRange,Parameter.MODE_INTERMEDIATE);

		BooleanParameterImpl paramRandTogether = new BooleanParameterImpl(
				BCFG_LISTEN_PORT_RANDOMIZE_TOGETHER,
				"ConfigView.section.connection.port.rand.together");
		add(paramRandTogether, Parameter.MODE_INTERMEDIATE);

		paramRandEnable.addEnabledOnSelection(paramRandRange, paramRandTogether);

			// public tcp peers enable
		
		BooleanParameterImpl paramTCPPublicEnable = new BooleanParameterImpl(
				BCFG_PEERCONTROL_TCP_PUBLIC_ENABLE,
				"ConfigView.section.connection.tcp.pubic.peer.enable");
		add(paramTCPPublicEnable, Parameter.MODE_ADVANCED);
		
			// public udp peers enable
		
		BooleanParameterImpl paramUDPPublicEnable = new BooleanParameterImpl(
				BCFG_PEERCONTROL_UDP_PUBLIC_ENABLE,
				"ConfigView.section.connection.udp.pubic.peer.enable");
		add(paramUDPPublicEnable, Parameter.MODE_ADVANCED);

		
		BooleanParameterImpl paramPreferUDP = new BooleanParameterImpl(
				BCFG_PEERCONTROL_PREFER_UDP,
				"ConfigView.section.connection.prefer.udp");
		add(paramPreferUDP, Parameter.MODE_ADVANCED);

		BooleanParameterImpl paramPreferIPv6 = new BooleanParameterImpl(
				BCFG_PEERCONTROL_PREFER_IPV6_CONNECTIONS,
				"ConfigView.section.connection.prefer.ipv6");
		add(paramPreferIPv6, Parameter.MODE_ADVANCED);

		
		if (userMode < 2) {
			// wiki link

			add(new HyperlinkParameterImpl(
					"ConfigView.section.connection.serverport.wiki", "Utils.link.visit",
					Wiki.WHY_PORTS_LIKE_6881_ARE_NO_GOOD_CHOICE));
		}

		if (userMode > 0) {
			/////////////////////// HTTP ///////////////////

			List<Parameter> listHTTP = new ArrayList<>();

			HyperlinkParameterImpl paramVisitHere = new HyperlinkParameterImpl(
					"ConfigView.label.please.visit.here",
					"ConfigView.section.connection.group.http.info",
					Wiki.HTTP_SEEDING);
			add(paramVisitHere, listHTTP);

			BooleanParameterImpl paramEnableHTTP = new BooleanParameterImpl(
					BCFG_HTTP_DATA_LISTEN_PORT_ENABLE,
					"ConfigView.section.connection.http.enable");
			add(paramEnableHTTP, listHTTP);

			IntParameterImpl paramHttpPort = new IntParameterImpl(
					ICFG_HTTP_DATA_LISTEN_PORT,
					"ConfigView.section.connection.http.port");
			add(paramHttpPort, listHTTP);

			IntParameterImpl paramHttpPortOverride = new IntParameterImpl(
					ICFG_HTTP_DATA_LISTEN_PORT_OVERRIDE,
					"ConfigView.section.connection.http.portoverride");
			add(paramHttpPortOverride, listHTTP);

			paramEnableHTTP.addEnabledOnSelection(paramHttpPort,
					paramHttpPortOverride);

			ParameterGroupImpl pgHTTP = new ParameterGroupImpl(
					"ConfigView.section.connection.group.http", listHTTP);
			pgHTTP.setMinimumRequiredUserMode(Parameter.MODE_INTERMEDIATE);
			add("pgHTTP", pgHTTP);

		}

		if (userMode > 0) {

			String[] units = {
					DisplayFormatters.getRateUnit(DisplayFormatters.UNIT_KB)
				};
			
			/////////////////////// WebSeeds ///////////////////

			BooleanParameterImpl paramWebSeedAct = new BooleanParameterImpl(
					BCFG_WEBSEED_ACTIVATION_USES_AVAILABILITY,
					"ConfigView.section.connection.webseed.act.on.avail");
			add(paramWebSeedAct);

			IntParameterImpl paramWebSeedMinSpeed = new IntParameterImpl(
					BCFG_WEBSEED_ACTIVATION_MIN_SPEED_KBPS,
					"");
			
			paramWebSeedMinSpeed.setLabelText(MessageText.getString(
					"ConfigView.section.connection.webseed.min.speed.kbps", units));

			paramWebSeedMinSpeed.setIndent(1,  true );
			add(paramWebSeedMinSpeed);
		
			paramWebSeedAct.addEnabledOnSelection( paramWebSeedMinSpeed );
			
			add("pgWS", new ParameterGroupImpl(
					"ConfigView.section.connection.group.webseed", paramWebSeedAct, paramWebSeedMinSpeed ));
		}

		if (userMode > 0) {
			/////////////////////// PEER SOURCES GROUP ///////////////////

			List<Parameter> listPS = new ArrayList<>();

			add(new LabelParameterImpl(
					"ConfigView.section.connection.group.peersources.info"), listPS);

			for (String p : PEPeerSource.PS_SOURCES) {

				String config_name = BCFG_PREFIX_PEER_SRC_SELECTION_DEF + p;
				String msg_text = "ConfigView.section.connection.peersource." + p;

				add(new BooleanParameterImpl(config_name, msg_text), listPS);
			}

			add("pgPS", new ParameterGroupImpl(
					"ConfigView.section.connection.group.peersources", listPS));

		}

		if (userMode > 1) {

			/////////////////////// NETWORKS GROUP ///////////////////

			List<Parameter> listNetworks = new ArrayList<>();

			add(new LabelParameterImpl(
					"ConfigView.section.connection.group.networks.info"), listNetworks);

			for (String nn : AENetworkClassifier.AT_NETWORKS) {

				String config_name = BCFG_PREFIX_NETWORK_SELECTION_DEF + nn;
				String msg_text = "ConfigView.section.connection.networks." + nn;

				add(new BooleanParameterImpl(config_name, msg_text),
						listNetworks);
			}

			// Gap
			add("con.net.gap0", new LabelParameterImpl(""), listNetworks);

			BooleanParameterImpl paramNetworksPrompt = new BooleanParameterImpl(
					BCFG_NETWORK_SELECTION_PROMPT,
					"ConfigView.section.connection.networks.prompt");
			paramNetworksPrompt.setAllowedUiTypes(UIInstance.UIT_SWT);
			add(paramNetworksPrompt, listNetworks);

			add("pgNetworks", new ParameterGroupImpl(
					"ConfigView.section.connection.group.networks", listNetworks));

		}
	}
}
