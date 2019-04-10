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
import com.biglybt.core.util.Constants;
import com.biglybt.pifimpl.local.ui.config.*;
import com.biglybt.plugin.startstoprules.defaultplugin.DefaultRankCalculator;
import com.biglybt.ui.config.ConfigSectionImpl;

import com.biglybt.pif.ui.config.Parameter;

/** Seeding Automation Specific options
 * @author TuxPaper
 * @created Jan 12, 2004
 *
 * TODO: StartStopManager_fAddForSeedingULCopyCount
 */
public class ConfigSectionDownloading
	extends ConfigSectionImpl
{

	public static final String SECTION_ID = "queue.downloading";

	public ConfigSectionDownloading() {
		super(SECTION_ID,  ConfigSectionQueue.SECTION_ID);
	}

	@Override
	public void build() {
		// Seeding Automation Setup

		// wiki link

		add(new HyperlinkParameterImpl("ConfigView.label.please.visit.here",
				Constants.URL_WIKI + "w/Downloading_Rules"));

		// sort type

		String[] orderLabels = {
			MessageText.getString("label.order"),
			MessageText.getString("label.seed.count"),
			MessageText.getString("label.reverse.seed.count"),
			MessageText.getString("TableColumn.header.size"),
			MessageText.getString("label.reverse.size"),
			MessageText.getString("label.speed"),
			MessageText.getString("TableColumn.header.eta"),
		};

		int[] orderValues = {
			DefaultRankCalculator.DOWNLOAD_ORDER_INDEX,
			DefaultRankCalculator.DOWNLOAD_ORDER_SEED_COUNT,
			DefaultRankCalculator.DOWNLOAD_ORDER_REVERSE_SEED_COUNT,
			DefaultRankCalculator.DOWNLOAD_ORDER_SIZE,
			DefaultRankCalculator.DOWNLOAD_ORDER_REVERSE_SIZE,
			DefaultRankCalculator.DOWNLOAD_ORDER_SPEED,
			DefaultRankCalculator.DOWNLOAD_ORDER_ETA,
		};

		IntListParameterImpl sort_type = new IntListParameterImpl(
				"StartStopManager_Downloading_iSortType",
				"label.prioritize.downloads.based.on", orderValues, orderLabels);
		add(sort_type);

		List<Parameter> listSpeed = new ArrayList<>();

		// info

		add(new LabelParameterImpl("ConfigView.label.downloading.info"), listSpeed);

		// test time

		IntParameterImpl testTime = new IntParameterImpl(
				"StartStopManager_Downloading_iTestTimeSecs",
				"ConfigView.label.downloading.testTime");
		add(testTime, listSpeed);
		testTime.setMinValue(60);

		// re-test

		IntParameterImpl reTest = new IntParameterImpl(
				"StartStopManager_Downloading_iRetestTimeMins",
				"ConfigView.label.downloading.reTest");
		add(reTest, listSpeed);
		reTest.setMinValue(0);

		BooleanParameterImpl testActive = new BooleanParameterImpl(
				"StartStopManager_Downloading_bTestActive",
				"ConfigView.label.downloading.testActive");
		add(testActive, listSpeed);

		sort_type.addListener(p -> {
			int type = sort_type.getValue();

			boolean is_speed = type == DefaultRankCalculator.DOWNLOAD_ORDER_SPEED || type == DefaultRankCalculator.DOWNLOAD_ORDER_ETA;

			testTime.setEnabled(is_speed);
			reTest.setEnabled(is_speed);
			testActive.setEnabled(is_speed);
		});
		sort_type.fireParameterChanged();

		add(new ParameterGroupImpl("label.speed.options", listSpeed));

		add(new BooleanParameterImpl("StartStopManager_bAddForDownloadingSR1",
				"ConfigView.label.downloading.addsr1"));

	}
}
