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
import com.biglybt.core.speedmanager.impl.v2.SpeedLimitMonitor;
import com.biglybt.core.speedmanager.impl.v2.SpeedManagerAlgorithmProviderV2;
import com.biglybt.core.util.AEDiagnostics;
import com.biglybt.core.util.AEDiagnosticsLogger;
import com.biglybt.pifimpl.local.ui.config.*;

public class ConfigSectionTransferAutoSpeedV2
	extends ConfigSectionImpl
{
	public static final String SECTION_ID = "transfer.select.v2";

	public ConfigSectionTransferAutoSpeedV2() {
		super(SECTION_ID, ConfigSectionTransferAutoSpeedSelect.SECTION_ID);
	}

	@Override
	public void build() {

		//add a comment to the debug log.
		///////////////////////////////////
		// Comment group
		///////////////////////////////////

		StringParameterImpl commentBox = new StringParameterImpl("AutoSpeedv2.comment",
				"ConfigTransferAutoSpeed.add.comment.to.log");
		add(commentBox);

		ActionParameterImpl commentButton = new ActionParameterImpl(null,
				"ConfigTransferAutoSpeed.log.button");
		add(commentButton);

		commentButton.addListener(param -> {
			//Add a file to the log.
			AEDiagnosticsLogger dLog = AEDiagnostics.getLogger("AutoSpeed");
			String comment = commentBox.getValue();
			if (comment != null) {
				if (comment.length() > 0) {
					dLog.log("user-comment:" + comment);
					commentBox.resetToDefault();
				}
			}
		});

		add("TASV2.pgComment", new ParameterGroupImpl(
				"ConfigTransferAutoSpeed.add.comment.to.log.group", commentBox,
				commentButton).setNumberOfColumns2(2));

		///////////////////////////
		// Upload Capacity used settings.
		///////////////////////////

		//Label column
		InfoParameterImpl ucuLabel = new InfoParameterImpl(null,
				"ConfigTransferAutoSpeed.mode",
				MessageText.getString("ConfigTransferAutoSpeed.capacity.used"));
		add(ucuLabel);

		//add a drop down.
		String[] downloadModeNames = {
			" 80%",
			" 70%",
			" 60%",
			" 50%"
		};

		int[] downloadModeValues = {
			80,
			70,
			60,
			50
		};

		IntListParameterImpl ucuDL = new IntListParameterImpl(
				SpeedLimitMonitor.USED_UPLOAD_CAPACITY_DOWNLOAD_MODE,
				"ConfigTransferAutoSpeed.while.downloading", downloadModeValues,
				downloadModeNames);
		add(ucuDL);

		add("TASV2.pgUpCap", new ParameterGroupImpl("ConfigTransferAutoSpeed.upload.capacity.usage",
				ucuDL, ucuLabel));

		//////////////////////////
		// DHT Ping Group
		//////////////////////////

		//how much data to accumulate before making an adjustment.

		IntParameterImpl adjustmentInterval = new IntParameterImpl(
				SpeedManagerAlgorithmProviderV2.SETTING_INTERVALS_BETWEEN_ADJUST,
				"ConfigTransferAutoSpeed.adjustment.interval");

		add( adjustmentInterval );
		
		//how much data to accumulate before making an adjustment.

		BooleanParameterImpl skipAfterAdjustment = new BooleanParameterImpl(
				SpeedManagerAlgorithmProviderV2.SETTING_WAIT_AFTER_ADJUST,
				"ConfigTransferAutoSpeed.skip.after.adjust");

		add(skipAfterAdjustment);

		add("TASV2.pgUpFreq", new ParameterGroupImpl("ConfigTransferAutoSpeed.data.update.frequency",
				adjustmentInterval, skipAfterAdjustment));
	}
}
