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

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.util.Wiki;
import com.biglybt.pifimpl.local.ui.config.*;

import com.biglybt.pif.ui.config.Parameter;

import static com.biglybt.core.config.ConfigKeys.AutoSpeed.*;

public class ConfigSectionTransferAutoSpeedClassic
	extends ConfigSectionImpl
{

	public static final String SECTION_ID = "transfer.autospeed";

	public ConfigSectionTransferAutoSpeedClassic() {
		super(SECTION_ID, ConfigSectionTransferAutoSpeedSelect.SECTION_ID);
	}

	@Override
	public void build() {

		add(new LabelParameterImpl("ConfigView.section.transfer.autospeed.info"));

		add(new HyperlinkParameterImpl("ConfigView.label.please.visit.here",
				Wiki.AUTO_SPEED));

		String[] units = {
			DisplayFormatters.getRateUnit(DisplayFormatters.UNIT_KB)
		};

		// min up

		IntParameterImpl min_upload = new IntParameterImpl(
				ICFG_AUTO_SPEED_MIN_UPLOAD_KBS, "");
		add(min_upload);
		min_upload.setLabelText(MessageText.getString(
				"ConfigView.section.transfer.autospeed.minupload", units));

		// max up

		IntParameterImpl max_upload = new IntParameterImpl(
				ICFG_AUTO_SPEED_MAX_UPLOAD_KBS, "");
		add(max_upload);
		max_upload.setLabelText(MessageText.getString(
				"ConfigView.section.transfer.autospeed.maxupload", units));

		BooleanParameterImpl enable_down_adj = new BooleanParameterImpl(
				BCFG_AUTO_SPEED_DOWNLOAD_ADJ_ENABLE,
				"ConfigView.section.transfer.autospeed.enabledownadj");
		add(enable_down_adj, Parameter.MODE_INTERMEDIATE);

		FloatParameterImpl down_adj = new FloatParameterImpl(
				FCFG_AUTO_SPEED_DOWNLOAD_ADJ_RATIO,
				"ConfigView.section.transfer.autospeed.downadjratio", 0, 999, 2);
		add(down_adj, Parameter.MODE_INTERMEDIATE);
		down_adj.setIndent(1, true);

		enable_down_adj.addEnabledOnSelection(down_adj);

		// max inc

		IntParameterImpl max_increase = new IntParameterImpl(
				ICFG_AUTO_SPEED_MAX_INCREMENT_KBS, "");
		add(max_increase, Parameter.MODE_ADVANCED);
		max_increase.setLabelText(MessageText.getString(
				"ConfigView.section.transfer.autospeed.maxinc", units));

		// max dec

		IntParameterImpl max_decrease = new IntParameterImpl(
				ICFG_AUTO_SPEED_MAX_DECREMENT_KBS, "");
		add(max_decrease, Parameter.MODE_ADVANCED);
		max_decrease.setLabelText(MessageText.getString(
				"ConfigView.section.transfer.autospeed.maxdec", units));

		// choking ping

		IntParameterImpl choke_ping = new IntParameterImpl(
				ICFG_AUTO_SPEED_CHOKING_PING_MILLIS,
				"ConfigView.section.transfer.autospeed.chokeping");
		add(choke_ping, Parameter.MODE_ADVANCED);

		// forced min

		final IntParameterImpl forced_min = new IntParameterImpl(
				ICFG_AUTO_SPEED_FORCED_MIN_KBS, "");
		add(forced_min, Parameter.MODE_ADVANCED);
		forced_min.setLabelText(MessageText.getString(
				"ConfigView.section.transfer.autospeed.forcemin", units));

		// latency

		IntParameterImpl latency_factor = new IntParameterImpl(
				ICFG_AUTO_SPEED_LATENCY_FACTOR,
				"ConfigView.section.transfer.autospeed.latencyfactor", 1,
				Integer.MAX_VALUE);
		add(latency_factor, Parameter.MODE_ADVANCED);

		ActionParameterImpl reset_button = new ActionParameterImpl(
				"ConfigView.section.transfer.autospeed.reset",
				"ConfigView.section.transfer.autospeed.reset.button");
		add(reset_button, Parameter.MODE_ADVANCED);

		reset_button.addListener(param -> {
			max_increase.resetToDefault();
			max_decrease.resetToDefault();
			choke_ping.resetToDefault();
			latency_factor.resetToDefault();
			forced_min.resetToDefault();
		});
	}
}
