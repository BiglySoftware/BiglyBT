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

import com.biglybt.pifimpl.local.ui.config.*;

import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.config.Parameter;
import com.biglybt.pif.ui.config.ParameterValidator.ValidationInfo;

import static com.biglybt.core.config.ConfigKeys.Tracker.*;

public class ConfigSectionTrackerClient
	extends ConfigSectionImpl
{

	public static final String SECTION_ID = "tracker.client";

	public ConfigSectionTrackerClient() {
		super(SECTION_ID, ConfigSection.SECTION_TRACKER);
	}

	@Override
	public void build() {

		//////////////////////SCRAPE GROUP ///////////////////

		List<Parameter> listScrape = new ArrayList<>();

		add(new LabelParameterImpl("ConfigView.section.tracker.client.scrapeinfo"),
				listScrape);

		BooleanParameterImpl scrape = new BooleanParameterImpl(
				BCFG_TRACKER_CLIENT_SCRAPE_ENABLE,
				"ConfigView.section.tracker.client.scrapeenable");
		add(scrape, listScrape);

		BooleanParameterImpl scrape_stopped = new BooleanParameterImpl(
				BCFG_TRACKER_CLIENT_SCRAPE_STOPPED_ENABLE,
				"ConfigView.section.tracker.client.scrapestoppedenable");
		add(scrape_stopped, listScrape);

		BooleanParameterImpl dont_scrape_never_Started = new BooleanParameterImpl(
				BCFG_TRACKER_CLIENT_SCRAPE_NEVER_STARTED_DISABLE,
				"ConfigView.section.tracker.client.scrapesneverstarteddisable");
		add(dont_scrape_never_Started, listScrape);
		dont_scrape_never_Started.setIndent(1, true);

		BooleanParameterImpl aggregate = new BooleanParameterImpl(
				BCFG_TRACKER_CLIENT_SCRAPE_SINGLE_ONLY,
				"ConfigView.section.tracker.client.scrapesingleonly");
		add(aggregate, listScrape);

		scrape.addEnabledOnSelection(scrape_stopped, dont_scrape_never_Started,
				aggregate);
		scrape_stopped.addEnabledOnSelection(dont_scrape_never_Started);

		ParameterGroupImpl pgScrape = new ParameterGroupImpl(
				"ConfigView.group.scrape", listScrape);
		add("TC.pgScrape", pgScrape);

		/////////////// INFO GROUP

		List<Parameter> listInfo = new ArrayList<>();

		// send info

		add(new BooleanParameterImpl(BCFG_TRACKER_CLIENT_SEND_OS_AND_JAVA_VERSION,
				"ConfigView.section.tracker.sendjavaversionandos"), listInfo);

		// show warnings

		add(new BooleanParameterImpl(BCFG_TRACKER_CLIENT_SHOW_WARNINGS,
				"ConfigView.section.tracker.client.showwarnings"), listInfo);

		// exclude LAN

		add(new BooleanParameterImpl(BCFG_TRACKER_CLIENT_EXCLUDE_LAN,
				"ConfigView.section.tracker.client.exclude_lan"), listInfo);

		add("TC.pgInfo", new ParameterGroupImpl("label.information", listInfo));

		/////////////// PROTOCOL GROUP

		List<Parameter> listProtocol = new ArrayList<>();

		// tcp enable

		add(new BooleanParameterImpl(BCFG_TRACKER_CLIENT_ENABLE_TCP,
				"ConfigView.section.tracker.client.enabletcp"), listProtocol);

		// udp enable

		BooleanParameterImpl enableUDP = new BooleanParameterImpl(
				BCFG_SERVER_ENABLE_UDP, "ConfigView.section.server.enableudp");
		add(enableUDP, listProtocol);

		// udp probe enable

		BooleanParameterImpl enableUDPProbe = new BooleanParameterImpl(
				BCFG_TRACKER_UDP_PROBE_ENABLE,
				"ConfigView.section.server.enableudpprobe");
		add(enableUDPProbe, listProtocol);

		enableUDP.addEnabledOnSelection(enableUDPProbe);

		add(new BooleanParameterImpl(BCFG_TRACKER_DNS_RECORDS_ENABLE,
				"ConfigView.section.server.enablednsrecords"), Parameter.MODE_ADVANCED,
				listProtocol);

		add(new ParameterGroupImpl("label.protocol", listProtocol));

//////////////////////OVERRIDE GROUP ///////////////////

		List<Parameter> listOverride = new ArrayList<>();

		StringParameterImpl overrideip = new StringParameterImpl(SCFG_OVERRIDE_IP,
				"ConfigView.label.overrideip");
		add(overrideip, Parameter.MODE_INTERMEDIATE, listOverride);

		StringParameterImpl tcpOverride = new StringParameterImpl(
				SCFG_TCP_LISTEN_PORT_OVERRIDE, "ConfigView.label.announceport");
		add(tcpOverride, Parameter.MODE_INTERMEDIATE, listOverride);
		tcpOverride.setWidthInCharacters(9);

		tcpOverride.setValidChars("0123456789", false);
		tcpOverride.addStringValidator((p, toValue) -> {
			if (toValue.isEmpty()) {
				return new ValidationInfo(true);
			}

			try {
				int portVal = Integer.parseInt(toValue);
				if (portVal >= 0 && portVal <= 65535) {
					return new ValidationInfo(true);
				} else {
					return new ValidationInfo(false, "Must be between 0 and 65535");
				}
			} catch (NumberFormatException e) {
				return new ValidationInfo(false, e.getMessage());
			}
		});

		add(new BooleanParameterImpl(BCFG_TRACKER_CLIENT_NO_PORT_ANNOUNCE,
				"ConfigView.label.noportannounce"), Parameter.MODE_INTERMEDIATE,
				listOverride);

		add(new IntParameterImpl(ICFG_TRACKER_CLIENT_NUMWANT_LIMIT,
				"ConfigView.label.maxnumwant", 0, 100), Parameter.MODE_INTERMEDIATE,
				listOverride);

		add(new IntParameterImpl(ICFG_TRACKER_CLIENT_MIN_ANNOUNCE_INTERVAL,
				"ConfigView.label.minannounce"), Parameter.MODE_INTERMEDIATE,
				listOverride);

		add(new BooleanParameterImpl(BCFG_TRACKER_CLIENT_SMART_ACTIVATION,
				"ConfigView.label.trackersmartactivate"), Parameter.MODE_ADVANCED,
				listOverride);

		
		
		ParameterGroupImpl pgProtocol = new ParameterGroupImpl(
				"ConfigView.group.override", listOverride);
		add("TC.pgProtocol", pgProtocol);
		pgProtocol.setMinimumRequiredUserMode(Parameter.MODE_INTERMEDIATE);

		//////////////////////////

		add(new IntParameterImpl(ICFG_TRACKER_CLIENT_CONNECT_TIMEOUT,
				"ConfigView.section.tracker.client.connecttimeout"),
				Parameter.MODE_ADVANCED);

		add(new IntParameterImpl(ICFG_TRACKER_CLIENT_READ_TIMEOUT,
				"ConfigView.section.tracker.client.readtimeout"),
				Parameter.MODE_ADVANCED);

		add(new IntParameterImpl(ICFG_TRACKER_CLIENT_CLOSEDOWN_TIMEOUT,
				"ConfigView.section.tracker.client.closetimeout"),
				Parameter.MODE_ADVANCED);

		add(new IntParameterImpl(ICFG_TRACKER_CLIENT_CONCURRENT_ANNOUNCE,
				"ConfigView.section.tracker.client.conc.announce",8,1024),
				Parameter.MODE_ADVANCED);

		add(new BooleanParameterImpl(BCFG_TRACKER_KEY_ENABLE_CLIENT,
				"ConfigView.section.tracker.enablekey"), Parameter.MODE_ADVANCED);

		add(new BooleanParameterImpl(BCFG_TRACKER_SEPARATE_PEER_I_DS,
				"ConfigView.section.tracker.separatepeerids"), Parameter.MODE_ADVANCED);

		LabelParameterImpl paramSepPeerIDInfo = new LabelParameterImpl(
				"ConfigView.section.tracker.separatepeerids.info");
		add(paramSepPeerIDInfo, Parameter.MODE_ADVANCED);
		paramSepPeerIDInfo.setIndent(1, false);

	}
}
