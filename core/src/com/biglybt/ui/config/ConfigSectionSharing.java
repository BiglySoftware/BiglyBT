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

import com.biglybt.core.util.AENetworkClassifier;
import com.biglybt.pifimpl.local.ui.config.*;

import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.config.Parameter;
import com.biglybt.pif.ui.config.ParameterListener;

import static com.biglybt.core.config.ConfigKeys.Sharing.*;

public class ConfigSectionSharing
	extends ConfigSectionImpl
{

	public static final String SECTION_ID = "sharing";

	public ConfigSectionSharing() {
	    super(SECTION_ID, ConfigSection.SECTION_ROOT);
	}

	@Override
	public void build() {
		// row

		String[] protocols = {
			"HTTP",
			"HTTPS",
			"UDP",
			"DHT"
		};
		String[] descs = {
			"HTTP",
			"HTTPS (SSL)",
			"UDP",
			"Decentralised"
		};

		StringListParameterImpl protocol = new StringListParameterImpl(
				SCFG_SHARING_PROTOCOL, "ConfigView.section.sharing.protocol", protocols,
				descs);
		add(protocol);

		// row

		BooleanParameterImpl private_torrent = new BooleanParameterImpl(
				BCFG_SHARING_TORRENT_PRIVATE,
				"ConfigView.section.sharing.privatetorrent");
		add(private_torrent);

		// row
		BooleanParameterImpl permit_dht_backup = new BooleanParameterImpl(
				BCFG_SHARING_PERMIT_DHT, "ConfigView.section.sharing.permitdht");
		add(permit_dht_backup);

		// Force "Permit DHT Tracking" off when "Private Torrent" is on
		ParameterListener protocol_cl = p -> {
			boolean not_dht = !protocol.getValue().equals("DHT");

			private_torrent.setEnabled(not_dht);

			permit_dht_backup.setEnabled(not_dht && !private_torrent.getValue());

			if (private_torrent.getValue()) {

				permit_dht_backup.setValue(false);
			}
		};

		protocol_cl.parameterChanged(protocol);

		protocol.addListener(protocol_cl);
		private_torrent.addListener(protocol_cl);

		// row
		add(new BooleanParameterImpl(BCFG_SHARING_ADD_HASHES,
				"wizard.createtorrent.extrahashes"));

		// row

		add(new BooleanParameterImpl(BCFG_SHARING_DISABLE_RCM,
				"ConfigView.section.sharing.disable_rcm"));

		// row
		BooleanParameterImpl rescan_enable = new BooleanParameterImpl(
				BCFG_SHARING_RESCAN_ENABLE, "ConfigView.section.sharing.rescanenable");
		add(rescan_enable);

		//row

		IntParameterImpl rescan_period = new IntParameterImpl(
				ICFG_SHARING_RESCAN_PERIOD, "ConfigView.section.sharing.rescanperiod");
		add(rescan_period);
		rescan_period.setMinValue(1);
		rescan_period.setIndent(1, true);

		rescan_enable.addEnabledOnSelection(rescan_period);

		// comment

		StringParameterImpl torrent_comment = new StringParameterImpl(
				SCFG_SHARING_TORRENT_COMMENT,
				"ConfigView.section.sharing.torrentcomment");
		add(torrent_comment);
		torrent_comment.setMultiLine(2);

		// row
		add(new BooleanParameterImpl(BCFG_SHARING_IS_PERSISTENT,
				"ConfigView.section.sharing.persistentshares"));

		/////////////////////// NETWORKS GROUP ///////////////////

		List<Parameter> listNetworks = new ArrayList<>();

		BooleanParameterImpl network_global = new BooleanParameterImpl(
				BCFG_SHARING_NETWORK_SELECTION_GLOBAL, "label.use.global.defaults");
		add(network_global, listNetworks);

		List<BooleanParameterImpl> net_params = new ArrayList<>();

		for (String net : AENetworkClassifier.AT_NETWORKS) {

			String config_name = BCFG_PREFIX_SHARING_NETWORK_SELECTION_DEFAULT + net;
			String msg_text = "ConfigView.section.connection.networks." + net;

			BooleanParameterImpl network = new BooleanParameterImpl(
					config_name, msg_text);

			add(network, listNetworks);
			network.setIndent(1, false);

			net_params.add(network);
		}

		network_global.addDisabledOnSelection(net_params.toArray(new Parameter[0]));

		add(SECTION_ID + ".pgNetworks", new ParameterGroupImpl(
				"ConfigView.section.connection.group.networks", listNetworks));

	}
}
