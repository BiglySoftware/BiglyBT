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

import com.biglybt.pifimpl.local.ui.config.*;
import com.biglybt.ui.config.ConfigSectionImpl;

import com.biglybt.pif.ui.config.Parameter;

/** Config Section for items that make us ignore torrents when seeding
 * @author TuxPaper
 * @created Jan 12, 2004
 */
public class ConfigSectionSeedingIgnore
	extends ConfigSectionImpl
{

	public static final String SECTION_ID = "queue.seeding.ignore";

	public ConfigSectionSeedingIgnore() {
		super(SECTION_ID, ConfigSectionSeeding.SECTION_ID);
	}

	@Override
	public void build() {
		// Seeding Automation Setup

		add(new LabelParameterImpl("ConfigView.label.autoSeedingIgnoreInfo"));

		List<Parameter> listIgnore = new ArrayList<>();

		IntParameterImpl ignoreSeedCount = new IntParameterImpl(
				"StartStopManager_iIgnoreSeedCount", "ConfigView.label.ignoreSeeds", 0,
				9999);
		add(ignoreSeedCount, listIgnore);
		ignoreSeedCount.setSuffixLabelKey("ConfigView.label.seeds");

		IntParameterImpl stopPeersRatio = new IntParameterImpl("Stop Peers Ratio",
				"ConfigView.label.seeding.ignoreRatioPeers", 0, 9999);
		add(stopPeersRatio, listIgnore);
		stopPeersRatio.setSuffixLabelKey("ConfigView.label.peers");

		IntParameterImpl ignoreRatioPeersSeedStart = new IntParameterImpl(
				"StartStopManager_iIgnoreRatioPeersSeedStart",
				"ConfigView.label.seeding.fakeFullCopySeedStart", 0, 9999);
		add(ignoreRatioPeersSeedStart, listIgnore);
		ignoreRatioPeersSeedStart.setSuffixLabelKey("ConfigView.label.seeds");
		ignoreRatioPeersSeedStart.setIndent(1, true);

		// Share Ratio
		FloatParameterImpl stopRatio = new FloatParameterImpl("Stop Ratio",
				"ConfigView.label.seeding.ignoreShareRatio", 1, -1, 1);
		add(stopRatio, listIgnore);
		stopRatio.setSuffixLabelText(":1");

		IntParameterImpl ignoreShareRatioSeedStart = new IntParameterImpl(
				"StartStopManager_iIgnoreShareRatioSeedStart",
				"ConfigView.label.seeding.fakeFullCopySeedStart", 0, 9999);
		add(ignoreShareRatioSeedStart, listIgnore);
		ignoreShareRatioSeedStart.setSuffixLabelKey("ConfigView.label.seeds");
		ignoreShareRatioSeedStart.setIndent(1, true);

		// Ignore 0 Peers
		add(new BooleanParameterImpl("StartStopManager_bIgnore0Peers",
				"ConfigView.label.seeding.ignore0Peers"), listIgnore);

		add(new ParameterGroupImpl("ConfigView.label.seeding.ignore", listIgnore));
	}
}
