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
import com.biglybt.core.speedmanager.LimitToTextHelper;
import com.biglybt.core.speedmanager.SpeedManager;
import com.biglybt.core.speedmanager.SpeedManagerLimitEstimate;
import com.biglybt.core.speedmanager.SpeedManagerListener;
import com.biglybt.core.speedmanager.impl.SpeedManagerImpl;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.util.Wiki;
import com.biglybt.pifimpl.local.ui.config.*;

import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.config.Parameter;

public class ConfigSectionTransferAutoSpeedSelect
		extends ConfigSectionImpl {

	public static final String SECTION_ID = "transfer.select";

	private SpeedManager sm;

	private SpeedManagerListener smListener;

	public ConfigSectionTransferAutoSpeedSelect() {
		super(SECTION_ID, ConfigSection.SECTION_TRANSFER);
	}

	@Override
	public void deleteConfigSection() {
		super.deleteConfigSection();

		if (sm != null && smListener != null) {
			sm.removeListener(smListener);
			smListener = null;
			sm = null;
		}
	}

	@Override
	public void build() {

		sm = CoreFactory.getSingleton().getSpeedManager();
		//V1, V2 ... drop down.

		//enable auto-speed beta
		///////////////////////////////////
		// AutoSpeed Beta mode group
		///////////////////////////////////
		//Beta-mode grouping.

		//Need a drop down to select which method will be used.

		String AutoSpeedClassic = MessageText.getString(
				"ConfigTransferAutoSpeed.auto.speed.classic");
		String AutoSpeedBeta = MessageText.getString(
				"ConfigTransferAutoSpeed.auto.speed.beta");

		String[] modeNames = {
				AutoSpeedClassic,
				AutoSpeedBeta,
		};

		int[] modes = {
				1,
				2,
		};

		IntListParameterImpl versionList = new IntListParameterImpl(
				SpeedManagerImpl.CONFIG_VERSION, "ConfigTransferAutoSpeed.algorithm",
				modes, modeNames);
		add(versionList);

		BooleanParameterImpl enableAutoSpeed = new BooleanParameterImpl(
				TransferSpeedValidator.AUTO_UPLOAD_ENABLED_CONFIGKEY,
				"ConfigView.section.transfer.autospeed.enableauto");
		add(enableAutoSpeed);

		//AutoSpeed while seeding enabled.
		BooleanParameterImpl enableAutoSpeedWhileSeeding = new BooleanParameterImpl(
				"Auto Upload Speed Seeding Enabled",
				"ConfigView.section.transfer.autospeed.enableautoseeding");
		add(enableAutoSpeedWhileSeeding);

		enableAutoSpeed.addDisabledOnSelection(enableAutoSpeedWhileSeeding);

		add("TASS.pgSelector", new ParameterGroupImpl("ConfigTransferAutoSpeed.algorithm.selector",
				enableAutoSpeed, enableAutoSpeedWhileSeeding, versionList));

		// NETWORK GROUP

		List<Parameter> listNetwork = new ArrayList<>();
		LimitToTextHelper limit_to_text = new LimitToTextHelper();

		// asn

		InfoParameterImpl paramASN = new InfoParameterImpl(null,
				"SpeedView.stats.asn", sm.getASN());
		add(paramASN, listNetwork);

		// up cap

		InfoParameterImpl paramEstUp = new InfoParameterImpl(null,
				"SpeedView.stats.estupcap",
				limit_to_text.getLimitText(sm.getEstimatedUploadCapacityBytesPerSec()));
		add(paramEstUp, listNetwork);

		// down cap

		InfoParameterImpl paramEstDown = new InfoParameterImpl(null,
				"SpeedView.stats.estdowncap", limit_to_text.getLimitText(
				sm.getEstimatedDownloadCapacityBytesPerSec()));
		add(paramEstDown, listNetwork);

		// space

		add("s0", new LabelParameterImpl(""), listNetwork);

		// info

		LabelParameterImpl info_label = new LabelParameterImpl("");
		add("tass.info", info_label, listNetwork);
		info_label.setLabelText(MessageText.getString(
				"ConfigView.section.transfer.autospeed.network.info", new String[]{
						DisplayFormatters.getRateUnit(DisplayFormatters.UNIT_KB)
				}));

		// up set

		String co_up = "AutoSpeed Network Upload Speed (temp)";
		String co_up_type = "AutoSpeed Network Upload Speed Type (temp)";

		int kinb = DisplayFormatters.getKinB();

		SpeedManagerLimitEstimate up_lim = sm.getEstimatedUploadCapacityBytesPerSec();

		COConfigurationManager.setParameter(co_up, up_lim.getBytesPerSec() / kinb);
		COConfigurationManager.setParameter(co_up_type,
				limit_to_text.getSettableType(up_lim));

		final IntParameterImpl max_upload = new IntParameterImpl(co_up,
				"SpeedView.stats.estupcap");
		add(max_upload);

		StringListParameterImpl max_upload_type = new StringListParameterImpl(
				co_up_type, null, limit_to_text.getSettableTypes(),
				limit_to_text.getSettableTypes());
		add(max_upload_type);
		max_upload_type.setLabelText(
				getMBitLimit(limit_to_text, (up_lim.getBytesPerSec() / kinb) * kinb));

		max_upload_type.addListener(p -> {
			float type = limit_to_text.textToType(max_upload_type.getValue());

			SpeedManagerLimitEstimate existing = sm.getEstimatedUploadCapacityBytesPerSec();

			if (existing.getEstimateType() != type) {

				sm.setEstimatedUploadCapacityBytesPerSec(existing.getBytesPerSec(),
						type);
			}
		});

		max_upload.addListener(p -> {
			int value = max_upload.getValue() * kinb;

			SpeedManagerLimitEstimate existing = sm.getEstimatedUploadCapacityBytesPerSec();

			if (existing.getBytesPerSec() != value) {

				sm.setEstimatedUploadCapacityBytesPerSec(value,
						existing.getEstimateType());
			}
		});

		// down set

		SpeedManagerLimitEstimate down_lim = sm.getEstimatedDownloadCapacityBytesPerSec();

		String co_down = "AutoSpeed Network Download Speed (temp)";
		String co_down_type = "AutoSpeed Network Download Speed Type (temp)";

		COConfigurationManager.setParameter(co_down,
				down_lim.getBytesPerSec() / kinb);
		COConfigurationManager.setParameter(co_down_type,
				limit_to_text.getSettableType(down_lim));

		IntParameterImpl max_download = new IntParameterImpl(co_down,
				"SpeedView.stats.estdowncap");
		add(max_download);

		StringListParameterImpl max_download_type = new StringListParameterImpl(
				co_down_type, null, limit_to_text.getSettableTypes(),
				limit_to_text.getSettableTypes());
		add(max_download_type);
		max_download_type.setLabelText(
				getMBitLimit(limit_to_text, (down_lim.getBytesPerSec() / kinb) * kinb));

		max_download_type.addListener(p -> {
			float type = limit_to_text.textToType(max_download_type.getValue());

			SpeedManagerLimitEstimate existing = sm.getEstimatedDownloadCapacityBytesPerSec();

			if (existing.getEstimateType() != type) {

				sm.setEstimatedDownloadCapacityBytesPerSec(existing.getBytesPerSec(),
						type);
			}
		});

		max_download.addListener(p -> {
			int value = max_download.getValue() * kinb;

			SpeedManagerLimitEstimate existing = sm.getEstimatedDownloadCapacityBytesPerSec();

			if (existing.getBytesPerSec() != value) {

				sm.setEstimatedDownloadCapacityBytesPerSec(value,
						existing.getEstimateType());
			}
		});

		ParameterGroupImpl pgNetworkLimits = new ParameterGroupImpl(null, max_download,
				max_download_type, max_upload, max_upload_type).setNumberOfColumns2(2);
		add("TASS.pgNetworkLimits", pgNetworkLimits,
				listNetwork);
		pgNetworkLimits.setIndent(1, false);

		// reset

		ActionParameterImpl reset_button = new ActionParameterImpl(
				"ConfigView.section.transfer.autospeed.resetnetwork",
				"ConfigView.section.transfer.autospeed.reset.button");
		add(reset_button, listNetwork);

		reset_button.addListener(param -> sm.reset());

		smListener = property -> {
			if (property == SpeedManagerListener.PR_ASN) {

				paramASN.setValue(sm.getASN());

			} else if (property == SpeedManagerListener.PR_UP_CAPACITY) {

				SpeedManagerLimitEstimate limit = sm.getEstimatedUploadCapacityBytesPerSec();

				paramEstUp.setValue(limit_to_text.getLimitText(limit));

				max_upload_type.setLabelText(
						getMBitLimit(limit_to_text, limit.getBytesPerSec()));

				max_upload.setValue(limit.getBytesPerSec() / kinb);

				max_upload_type.setValue(limit_to_text.getSettableType(limit));

			} else if (property == SpeedManagerListener.PR_DOWN_CAPACITY) {

				SpeedManagerLimitEstimate limit = sm.getEstimatedDownloadCapacityBytesPerSec();

				paramEstDown.setValue(limit_to_text.getLimitText(limit));

				max_download_type.setLabelText(
						getMBitLimit(limit_to_text, limit.getBytesPerSec()));

				max_download.setValue(limit.getBytesPerSec() / kinb);

				max_download_type.setValue(limit_to_text.getSettableType(limit));

			}
		};
		sm.addListener(smListener);

		//Add listeners to disable setting when needed.

		ParameterGroupImpl pgNetworks = new ParameterGroupImpl(
				"ConfigView.section.transfer.autospeed.networks", listNetwork);
		add("TASS.pgNetworks", pgNetworks);

		BooleanParameterImpl debug_au = new BooleanParameterImpl(
				"Auto Upload Speed Debug Enabled",
				"ConfigView.section.transfer.autospeed.enabledebug");
		add(debug_au);

		/////////////////////////////////////////
		//Add group to link to Wiki page.
		/////////////////////////////////////////

		add("s1", new LabelParameterImpl(""));

		add(new HyperlinkParameterImpl("!" + Constants.APP_NAME + " Wiki AutoSpeed (beta)!", "Utils.link.visit",
				Wiki.AUTO_SPEED));
	}

	protected String getMBitLimit(LimitToTextHelper helper, long value) {
		return ("(" + (value == 0 ? helper.getUnlimited()
				: DisplayFormatters.formatByteCountToBitsPerSec2(value)) + ")");
	}
}
