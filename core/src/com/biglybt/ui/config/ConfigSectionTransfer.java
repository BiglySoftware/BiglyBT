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

import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.impl.TransferSpeedValidator;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.util.Wiki;
import com.biglybt.pifimpl.local.ui.config.*;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.config.Parameter;
import com.biglybt.pif.ui.config.ParameterListener;

import static com.biglybt.core.config.ConfigKeys.*;
import static com.biglybt.core.config.ConfigKeys.Transfer.*;

public class ConfigSectionTransfer
	extends ConfigSectionImpl
{
	public ConfigSectionTransfer() {
		super(ConfigSection.SECTION_TRANSFER, ConfigSection.SECTION_ROOT);
	}

	@Override
	public void build() {

		//  store the initial d/l speed so we can do something sensible later
		final int[] manual_max_download_speed = {
			COConfigurationManager.getIntParameter(ICFG_MAX_DOWNLOAD_SPEED_KBS)
		};

		//  max upload speed

		IntParameterImpl paramMaxUploadSpeed = new IntParameterImpl(
				ICFG_MAX_UPLOAD_SPEED_KBS, "ConfigView.label.maxuploadspeed", 0,
				Integer.MAX_VALUE);
		add(paramMaxUploadSpeed);

		//  max upload speed when seeding

		BooleanParameterImpl enable_seeding_rate = new BooleanParameterImpl(
				BCFG_ENABLE_SEEDINGONLY_UPLOAD_RATE,
				"ConfigView.label.maxuploadspeedseeding");
		add(enable_seeding_rate);

		IntParameterImpl paramMaxUploadSpeedSeeding = new IntParameterImpl(
				ICFG_MAX_UPLOAD_SPEED_SEEDING_KBS, null, 0, Integer.MAX_VALUE);
		add(paramMaxUploadSpeedSeeding);

		enable_seeding_rate.addEnabledOnSelection(paramMaxUploadSpeedSeeding);

		add("cMaxUploadSpeedOptionsArea",
				new ParameterGroupImpl(null, enable_seeding_rate,
						paramMaxUploadSpeedSeeding).setNumberOfColumns2(2));

		int userMode = COConfigurationManager.getIntParameter(ICFG_USER_MODE);
		// todo: Create a Parameter.setMaximumRequiredUserMode
		if (userMode < Parameter.MODE_ADVANCED) {
			// wiki link

			add(new HyperlinkParameterImpl("ConfigView.section.transfer.speeds.wiki",
					"Utils.link.visit", Wiki.GOOD_SETTINGS));
		}

		add(new IntParameterImpl(ICFG_MAX_UPLOADS_WHEN_BUSY_INC_MIN_SECS,
				"ConfigView.label.maxuploadswhenbusymin", 0, Integer.MAX_VALUE),
				Parameter.MODE_ADVANCED);

		// max download speed

		IntParameterImpl paramMaxDownSpeed = new IntParameterImpl(
				ICFG_MAX_DOWNLOAD_SPEED_KBS, "ConfigView.label.maxdownloadspeed", 0,
				Integer.MAX_VALUE);
		add(paramMaxDownSpeed);

		// max upload/download limit dependencies

		ParameterListener l = param -> {
			boolean disableAuto;
			boolean disableAutoSeeding;

			if (enable_seeding_rate.getValue()) {
				disableAutoSeeding = param == paramMaxUploadSpeedSeeding;
				disableAuto = param == paramMaxDownSpeed
						|| param == paramMaxUploadSpeed;
			} else {
				disableAuto = true;
				disableAutoSeeding = true;
			}

			if (disableAuto)
				COConfigurationManager.setParameter(
						TransferSpeedValidator.AUTO_UPLOAD_ENABLED_CONFIGKEY, false);
			if (disableAutoSeeding)
				COConfigurationManager.setParameter(
						TransferSpeedValidator.AUTO_UPLOAD_SEEDING_ENABLED_CONFIGKEY,
						false);
		};

		paramMaxDownSpeed.addListener(l);
		paramMaxUploadSpeed.addListener(l);
		paramMaxUploadSpeedSeeding.addListener(l);

		paramMaxUploadSpeed.addListener(
				param -> CoreFactory.addCoreRunningListener(core -> {

					// we don't want to police these limits when auto-speed is running as
					// they screw things up bigtime

					if (TransferSpeedValidator.isAutoSpeedActive(
							core.getGlobalManager())) {

						return;
					}

					int up_val = paramMaxUploadSpeed.getValue();
					int down_val = paramMaxDownSpeed.getValue();

					if (up_val != 0
							&& up_val < COConfigurationManager.CONFIG_DEFAULT_MIN_MAX_UPLOAD_SPEED) {

						if ((down_val == 0) || down_val > (up_val * 2)) {

							paramMaxDownSpeed.setValue(up_val * 2);
						}
					} else {

						if (down_val != manual_max_download_speed[0]) {

							paramMaxDownSpeed.setValue(manual_max_download_speed[0]);
						}
					}
				}));

		paramMaxDownSpeed.addListener(
				param -> CoreFactory.addCoreRunningListener(core -> {
					// we don't want to police these limits when auto-speed is running as
					// they screw things up bigtime

					if (TransferSpeedValidator.isAutoSpeedActive(
							core.getGlobalManager())) {

						return;
					}

					int up_val = paramMaxUploadSpeed.getValue();
					int down_val = paramMaxDownSpeed.getValue();

					manual_max_download_speed[0] = down_val;

					if (up_val < COConfigurationManager.CONFIG_DEFAULT_MIN_MAX_UPLOAD_SPEED) {

						if (up_val != 0 && up_val < (down_val * 2)) {

							paramMaxUploadSpeed.setValue((down_val + 1) / 2);

						} else if (down_val == 0) {

							paramMaxUploadSpeed.setValue(0);
						}
					}
				}));

		// bias upload to incomplete

		BooleanParameterImpl bias_upload = new BooleanParameterImpl(
				BCFG_BIAS_UPLOAD_ENABLE, "ConfigView.label.xfer.bias_up");
		add(bias_upload, Parameter.MODE_INTERMEDIATE);

		IntParameterImpl bias_slack = new IntParameterImpl(
				ICFG_BIAS_UPLOAD_SLACK_KBS, "ConfigView.label.xfer.bias_slack", 1,
				Integer.MAX_VALUE);
		add(bias_slack, Parameter.MODE_INTERMEDIATE);
		bias_slack.setIndent(1, true);

		BooleanParameterImpl bias_no_limit = new BooleanParameterImpl(
				BCFG_BIAS_UPLOAD_HANDLE_NO_LIMIT,
				"ConfigView.label.xfer.bias_no_limit");
		add(bias_no_limit, Parameter.MODE_INTERMEDIATE);
		bias_no_limit.setIndent(1, true);

		bias_upload.addEnabledOnSelection(bias_slack, bias_no_limit);

		List<Parameter> listAuto = new ArrayList<>();

		// AUTO GROUP

		BooleanParameterImpl auto_adjust = new BooleanParameterImpl(
				BCFG_AUTO_ADJUST_TRANSFER_DEFAULTS, "ConfigView.label.autoadjust");
		add(auto_adjust, Parameter.MODE_INTERMEDIATE);

		// max uploads

		IntParameterImpl paramMaxUploads = new IntParameterImpl(ICFG_MAX_UPLOADS,
				"ConfigView.label.maxuploads", 2, Integer.MAX_VALUE);
		add(paramMaxUploads, Parameter.MODE_INTERMEDIATE, listAuto);

		// max uploads when seeding

		BooleanParameterImpl enable_seeding_uploads = new BooleanParameterImpl(
				BCFG_ENABLE_SEEDINGONLY_MAXUPLOADS,
				"ConfigView.label.maxuploadsseeding");
		add(enable_seeding_uploads, Parameter.MODE_INTERMEDIATE);

		IntParameterImpl paramMaxUploadsSeeding = new IntParameterImpl(
				ICFG_MAX_UPLOADS_SEEDING, null, 2, Integer.MAX_VALUE);

		add(paramMaxUploadsSeeding, Parameter.MODE_INTERMEDIATE);

		add("Transfer.pgMaxUpSeeding", new ParameterGroupImpl(null,
				enable_seeding_uploads, paramMaxUploadsSeeding).setNumberOfColumns2(2),
				listAuto);

		////

		IntParameterImpl paramMaxClients = new IntParameterImpl(
				ICFG_MAX_PEER_CONNECTIONS_PER_TORRENT,
				"ConfigView.label.max_peers_per_torrent");
		add(paramMaxClients, Parameter.MODE_INTERMEDIATE, listAuto);

		/////

		// max peers when seeding

		BooleanParameterImpl enable_max_peers_seeding = new BooleanParameterImpl(
				BCFG_MAX_PEER_CONNECTIONS_PER_TORRENT_WHEN_SEEDING_ENABLE,
				"ConfigView.label.maxuploadsseeding");
		add(enable_max_peers_seeding, Parameter.MODE_INTERMEDIATE);

		IntParameterImpl paramMaxPeersSeeding = new IntParameterImpl(
				ICFG_MAX_PEER_CONNECTIONS_PER_TORRENT_WHEN_SEEDING, null, 0,
				Integer.MAX_VALUE);
		add(paramMaxPeersSeeding, Parameter.MODE_INTERMEDIATE);

		add("Transfer.pgMaxPeersSeeding", new ParameterGroupImpl(null,
				enable_max_peers_seeding, paramMaxPeersSeeding).setNumberOfColumns2(2),
				listAuto);

		/////

		IntParameterImpl paramMaxClientsTotal = new IntParameterImpl(
				ICFG_MAX_PEER_CONNECTIONS_TOTAL, "ConfigView.label.max_peers_total");
		add(paramMaxClientsTotal, Parameter.MODE_INTERMEDIATE, listAuto);

		IntParameterImpl max_seeds_per_torrent = new IntParameterImpl(
				ICFG_MAX_SEEDS_PER_TORRENT, "ConfigView.label.maxseedspertorrent");
		add(max_seeds_per_torrent, Parameter.MODE_INTERMEDIATE, listAuto);

		auto_adjust.addDisabledOnSelection(listAuto.toArray(new Parameter[0]));
		enable_seeding_uploads.addEnabledOnSelection(paramMaxUploadsSeeding);
		enable_max_peers_seeding.addEnabledOnSelection(paramMaxPeersSeeding);

		listAuto.add(0, auto_adjust);
		add("Transfer.gAuto", new ParameterGroupImpl("group.auto", listAuto));

		// NON PUBLIC PEERS GROUP

		List<Parameter> listNPP = new ArrayList<>();

		add(new IntParameterImpl(ICFG_NON_PUBLIC_PEER_EXTRA_SLOTS_PER_TORRENT,
				"ConfigView.label.npp.slots", 0, Integer.MAX_VALUE),
				Parameter.MODE_INTERMEDIATE, listNPP);

		add(new IntParameterImpl(ICFG_NON_PUBLIC_PEER_EXTRA_CONNECTIONS_PER_TORRENT,
				"ConfigView.label.npp.connections", 0, Integer.MAX_VALUE),
				Parameter.MODE_INTERMEDIATE, listNPP);

		add("Transfer.NPP",
				new ParameterGroupImpl("label.non.public.peers", listNPP));

		// END NON PUBLIC PEERS GROUP

		BooleanParameterImpl useReqLimiting = new BooleanParameterImpl(
				BCFG_USE_REQUEST_LIMITING, "ConfigView.label.userequestlimiting");
		add(useReqLimiting, Parameter.MODE_INTERMEDIATE);

		BooleanParameterImpl useReqLimitingPrios = new BooleanParameterImpl(
				BCFG_USE_REQUEST_LIMITING_PRIORITIES,
				"ConfigView.label.userequestlimitingpriorities");
		add(useReqLimitingPrios, Parameter.MODE_INTERMEDIATE);
		useReqLimitingPrios.setIndent(1, true);

		useReqLimiting.addEnabledOnSelection(useReqLimitingPrios);

		// up limits include protocol

		BooleanParameterImpl upIncludesProt = new BooleanParameterImpl(
				BCFG_UP_RATE_LIMITS_INCLUDE_PROTOCOL,
				"ConfigView.label.up.includes.prot");
		add(upIncludesProt, Parameter.MODE_INTERMEDIATE);

		// down limits include protocol

		BooleanParameterImpl downIncludesProt = new BooleanParameterImpl(
				BCFG_DOWN_RATE_LIMITS_INCLUDE_PROTOCOL,
				"ConfigView.label.down.includes.prot");
		add(downIncludesProt, Parameter.MODE_INTERMEDIATE);

		// same IP

		BooleanParameterImpl allowSameIP = new BooleanParameterImpl(
				BCFG_ALLOW_SAME_IP_PEERS, "ConfigView.label.allowsameip");
		add(allowSameIP, Parameter.MODE_INTERMEDIATE);

		// both IPv4 and IPv6 connections
		
		int[] values = { 0, 1, 2 };
		
		String[] labels = {
				MessageText.getString("label.allow.both"),
				MessageText.getString("label.ban.ipv4"),
				MessageText.getString("label.ban.ipv6"),
		};
		
		IntListParameterImpl paramDualIPAction = 
			new IntListParameterImpl(
				ICFG_IPv4_IPv6_CONN_ACTION, "ConfigView.label.dual.con.behaviour",
				values, labels);
		
		add(paramDualIPAction, Parameter.MODE_INTERMEDIATE);	
		
		// lazy bit field
		BooleanParameterImpl lazybf = new BooleanParameterImpl(
				BCFG_USE_LAZY_BITFIELD, "ConfigView.label.lazybitfield");
		add(lazybf, Parameter.MODE_INTERMEDIATE);

		// don't declare completion

		BooleanParameterImpl hap = new BooleanParameterImpl(
				BCFG_PEERCONTROL_HIDE_PIECE, "ConfigView.label.hap");
		add(hap, Parameter.MODE_ADVANCED);

		BooleanParameterImpl hapds = new BooleanParameterImpl(
				BCFG_PEERCONTROL_HIDE_PIECE_DS, "ConfigView.label.hapds");
		add(hapds, Parameter.MODE_ADVANCED);
		hapds.setIndent(1, true);

		hap.addEnabledOnSelection(hapds);

			// prioritise first/last pieces
		
		BooleanParameterImpl pfp = new BooleanParameterImpl(BCFG_PRIORITIZE_FIRST_PIECE,
				"ConfigView.label.prioritizefirstpiece");
		
		add( pfp, Parameter.MODE_INTERMEDIATE);

		IntParameterImpl pMB = new 
				IntParameterImpl(ICFG_PRIORITIZE_FIRST_MB,
				"", 0, Integer.MAX_VALUE);

		String[] units = {
				DisplayFormatters.getUnit(DisplayFormatters.UNIT_MB)
			};
		
		pMB.setLabelText(MessageText.getString("ConfigView.label.prioritizefirstmb", units ));
		
		pMB.setIndent( 1, true );

		add( pMB, Parameter.MODE_INTERMEDIATE);

			// prioritise first/last pieces Force
		
		BooleanParameterImpl pfpf = new BooleanParameterImpl(BCFG_PRIORITIZE_FIRST_PIECE_FORCE,
				"ConfigView.label.prioritizefirstpiece.force");
		
		pfpf.setIndent( 1, true );

		add( pfpf, Parameter.MODE_INTERMEDIATE);

		pfp.addEnabledOnSelection( pfpf );
		pfp.addEnabledOnSelection( pMB );
		
		// Further prioritize High priority files according to % complete and size of file
		add(new BooleanParameterImpl(BCFG_PRIORITIZE_MOST_COMPLETED_FILES,
				"ConfigView.label.prioritizemostcompletedfiles"),
				Parameter.MODE_INTERMEDIATE);

		IntParameterImpl sfp =new IntParameterImpl( ICFG_SET_FILE_PRIORITY_REM_PIECE, "ConfigView.label.set.file.pri.pieces.rem", 0, Integer.MAX_VALUE);
		
		sfp.setGenerateIntermediateEvents( false );
		
		add( sfp, Parameter.MODE_INTERMEDIATE);
		
		// ignore ports

		StringParameterImpl paramIgnorePeerPorts = new StringParameterImpl(
				SCFG_IGNORE_PEER_PORTS, "ConfigView.label.transfer.ignorepeerports");
		add(paramIgnorePeerPorts, Parameter.MODE_INTERMEDIATE);

		add("gIgnore.peer.ports",
				new ParameterGroupImpl(null, paramIgnorePeerPorts));
	}
}
