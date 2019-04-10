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

package com.biglybt.plugin.startstoprules.defaultplugin.ui.swt;

import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.internat.MessageText;
import com.biglybt.pifimpl.local.ui.config.BooleanParameterImpl;
import com.biglybt.pifimpl.local.ui.config.IntListParameterImpl;
import com.biglybt.pifimpl.local.ui.config.LabelParameterImpl;
import com.biglybt.pifimpl.local.ui.config.ParameterGroupImpl;
import com.biglybt.plugin.startstoprules.defaultplugin.DefaultRankCalculator;
import com.biglybt.ui.config.ConfigSectionImpl;

import com.biglybt.pif.ui.config.Parameter;

/** First Priority Specific options.
 * @author TuxPaper
 * @created Jan 12, 2004
 */
public class ConfigSectionSeedingFirstPriority
	extends ConfigSectionImpl
{

	public static final String SECTION_ID = "queue.seeding.firstPriority";

	public ConfigSectionSeedingFirstPriority() {
		super(SECTION_ID, ConfigSectionSeeding.SECTION_ID);
	}

	@Override
	public void build() {
		// Seeding Automation Setup

		add(new LabelParameterImpl("ConfigView.label.seeding.firstPriority.info"));

		// Group FP

		List<Parameter> listFP = new ArrayList<>();

		// row
		String[] fpLabels = {
			MessageText.getString("ConfigView.text.all"),
			MessageText.getString("ConfigView.text.any")
		};
		int[] fpValues = {
			DefaultRankCalculator.FIRSTPRIORITY_ALL,
			DefaultRankCalculator.FIRSTPRIORITY_ANY
		};
		IntListParameterImpl firstPriorityType = new IntListParameterImpl(
				"StartStopManager_iFirstPriority_Type",
				"ConfigView.label.seeding.firstPriority", fpValues, fpLabels);
		add(firstPriorityType);
		firstPriorityType.setSuffixLabelKey(
				"ConfigView.label.seeding.firstPriority.following");

		// visual tweak so drop down doesn't align with future parameter controls
		add("pgFirstPriorityType", new ParameterGroupImpl(null, firstPriorityType),
				listFP);

		// row
		String[] minQueueLabels = new String[55];
		int[] minQueueValues = new int[55];
		minQueueLabels[0] = "1:2 (" + 0.5 + ")";
		minQueueValues[0] = 500;
		minQueueLabels[1] = "3:4 (" + 0.75 + ")";
		minQueueValues[1] = 750;
		minQueueLabels[2] = "1:1";
		minQueueValues[2] = 1000;
		minQueueLabels[3] = "5:4 (" + 1.25 + ")";
		minQueueValues[3] = 1250;
		minQueueLabels[4] = "3:2 (" + 1.50 + ")";
		minQueueValues[4] = 1500;
		minQueueLabels[5] = "7:4 (" + 1.75 + ")";
		minQueueValues[5] = 1750;
		for (int i = 6; i < minQueueLabels.length; i++) {
			minQueueLabels[i] = i - 4 + ":1";
			minQueueValues[i] = (i - 4) * 1000;
		}
		add(new IntListParameterImpl("StartStopManager_iFirstPriority_ShareRatio",
				"ConfigView.label.seeding.firstPriority.shareRatio", minQueueValues,
				minQueueLabels), listFP);

		String sMinutes = MessageText.getString("ConfigView.text.minutes");
		String sHours = MessageText.getString("ConfigView.text.hours");

		// row
		String[] dlTimeLabels = new String[15];
		int[] dlTimeValues = new int[15];
		dlTimeLabels[0] = MessageText.getString("ConfigView.text.ignore");
		dlTimeValues[0] = 0;
		for (int i = 1; i < dlTimeValues.length; i++) {
			dlTimeLabels[i] = "<= " + (i + 2) + " " + sHours;
			dlTimeValues[i] = (i + 2) * 60;
		}
		add(new IntListParameterImpl("StartStopManager_iFirstPriority_DLMinutes",
				"ConfigView.label.seeding.firstPriority.DLMinutes", dlTimeValues,
				dlTimeLabels), listFP);

		// row
		String[] seedTimeLabels = new String[15];
		int[] seedTimeValues = new int[15];
		seedTimeLabels[0] = MessageText.getString("ConfigView.text.ignore");
		seedTimeValues[0] = 0;
		seedTimeLabels[1] = "<= 90 " + sMinutes;
		seedTimeValues[1] = 90;
		for (int i = 2; i < seedTimeValues.length; i++) {
			seedTimeLabels[i] = "<= " + i + " " + sHours;
			seedTimeValues[i] = i * 60;
		}
		add(new IntListParameterImpl(
				"StartStopManager_iFirstPriority_SeedingMinutes",
				"ConfigView.label.seeding.firstPriority.seedingMinutes", seedTimeValues,
				seedTimeLabels), listFP);

		add(new ParameterGroupImpl("ConfigView.label.seeding.firstPriority.FP",
				listFP));

		//	 Group Ignore FP

		List<Parameter> listIgnoreFP = new ArrayList<>();

		// Ignore S:P Ratio
		String[] ignoreSPRatioLabels = new String[15];
		int[] ignoreSPRatioValues = new int[15];
		ignoreSPRatioLabels[0] = MessageText.getString("ConfigView.text.ignore");
		ignoreSPRatioValues[0] = 0;
		for (int i = 1; i < ignoreSPRatioLabels.length; i++) {
			ignoreSPRatioLabels[i] = i * 10 + " " + ":1";
			ignoreSPRatioValues[i] = i * 10;
		}
		add(new IntListParameterImpl(
				"StartStopManager_iFirstPriority_ignoreSPRatio",
				"ConfigView.label.seeding.firstPriority.ignoreSPRatio",
				ignoreSPRatioValues, ignoreSPRatioLabels), listIgnoreFP);

		//	 Ignore 0 Peers
		add(new BooleanParameterImpl("StartStopManager_bFirstPriority_ignore0Peer",
				"ConfigView.label.seeding.firstPriority.ignore0Peer"), listIgnoreFP);

		// Ignore idle hours
		int[] availIdleHours = {
			2,
			3,
			4,
			5,
			6,
			7,
			8,
			12,
			18,
			24,
			48,
			72,
			168
		};
		String[] ignoreIdleHoursLabels = new String[availIdleHours.length + 1];
		int[] ignoreIdleHoursValues = new int[availIdleHours.length + 1];
		ignoreIdleHoursLabels[0] = MessageText.getString("ConfigView.text.ignore");
		ignoreIdleHoursValues[0] = 0;
		for (int i = 0; i < availIdleHours.length; i++) {
			ignoreIdleHoursLabels[i + 1] = availIdleHours[i] + " " + sHours;
			ignoreIdleHoursValues[i + 1] = availIdleHours[i];
		}
		add(new IntListParameterImpl(
				"StartStopManager_iFirstPriority_ignoreIdleHours",
				"ConfigView.label.seeding.firstPriority.ignoreIdleHours",
				ignoreIdleHoursValues, ignoreIdleHoursLabels), listIgnoreFP);

		//	 row

		LabelParameterImpl fpIgnoreInfo = new LabelParameterImpl(
				"ConfigView.label.seeding.firstPriority.ignore.info");
		add(fpIgnoreInfo, listIgnoreFP);
		// hack the /n out
		fpIgnoreInfo.setLabelText(
				fpIgnoreInfo.getLabelText().replaceAll("\n", " "));

		add(new ParameterGroupImpl("ConfigView.label.seeding.firstPriority.ignore",
				listIgnoreFP));

		add(new BooleanParameterImpl(

				"StartStopManager_bTagFirstPriority",
				"ConfigView.label.queue.tagfirstpriority"));
	}
}
