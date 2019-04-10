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
import com.biglybt.pifimpl.local.ui.config.IntParameterImpl;
import com.biglybt.pifimpl.local.ui.config.ParameterGroupImpl;
import com.biglybt.plugin.startstoprules.defaultplugin.StartStopRulesDefaultPlugin;
import com.biglybt.ui.config.ConfigSectionImpl;

import com.biglybt.pif.ui.config.IntListParameter;
import com.biglybt.pif.ui.config.Parameter;

/** Auto Starting specific options
 * @author TuxPaper
 * @created Jan 12, 2004
 */
public class ConfigSectionSeedingAutoStarting
	extends ConfigSectionImpl
{

	public static final String SECTION_ID = "queue.seeding.autoStarting";

	public ConfigSectionSeedingAutoStarting() {
		super(SECTION_ID, ConfigSectionSeeding.SECTION_ID);
	}

	@Override
	public void build() {
		// Seeding Automation Setup

		// ** Begin Rank Type area
		// Rank Type area.  Encompases the 4 (or more) options groups

		int[] rankValues = {
			StartStopRulesDefaultPlugin.RANK_SPRATIO,
			StartStopRulesDefaultPlugin.RANK_SEEDCOUNT,
			StartStopRulesDefaultPlugin.RANK_PEERCOUNT,
			StartStopRulesDefaultPlugin.RANK_TIMED,
			StartStopRulesDefaultPlugin.RANK_NONE
		};
		String[] rankLabels = {
			MessageText.getString("ConfigView.label.seeding.rankType.peerSeed"),
			MessageText.getString("ConfigView.label.seeding.rankType.seed"),
			MessageText.getString("ConfigView.label.seeding.rankType.peer"),
			MessageText.getString("ConfigView.label.seeding.rankType.timedRotation"),
			MessageText.getString("ConfigView.label.seeding.rankType.none")
		};

		IntListParameterImpl paramRankType = new IntListParameterImpl(
				"StartStopManager_iRankType", null, rankValues, rankLabels);
		add(paramRankType);
		paramRankType.setListType(IntListParameter.TYPE_RADIO_LIST);

		// Seed Count options

		IntParameterImpl paramSeedFallback = new IntParameterImpl(
				"StartStopManager_iRankTypeSeedFallback",
				"ConfigView.label.seeding.rankType.seed.fallback", 0,
				Integer.MAX_VALUE);
		add(paramSeedFallback);
		paramSeedFallback.setSuffixLabelKey("ConfigView.label.seeds");

		ParameterGroupImpl pgSeedOptions = new ParameterGroupImpl(
				"ConfigView.label.seeding.rankType.seed.options", paramSeedFallback);
		add(pgSeedOptions);

		// timed rotation ranking type

		IntParameterImpl paramMinSeedingTimeWithPeers = new IntParameterImpl(
				"StartStopManager_iTimed_MinSeedingTimeWithPeers",
				"ConfigView.label.seeding.rankType.timed.minTimeWithPeers", 0,
				Integer.MAX_VALUE);
		add(paramMinSeedingTimeWithPeers);

		ParameterGroupImpl pgTimedOptions = new ParameterGroupImpl(
				"ConfigView.label.seeding.rankType.timed.options",
				paramMinSeedingTimeWithPeers);
		add(pgTimedOptions);

		ParameterGroupImpl pgRankTypeOptions = new ParameterGroupImpl(null,
				pgSeedOptions, pgTimedOptions);
		add("pgRankTypeOptions", pgRankTypeOptions);

		add(new ParameterGroupImpl("ConfigView.label.seeding.rankType",
				paramRankType, pgRankTypeOptions).setNumberOfColumns2(2));

		// ** End Rank Type area

		List<Parameter> listPSorSC = new ArrayList<>();

		add(new BooleanParameterImpl("StartStopManager_bPreferLargerSwarms",
				"ConfigView.label.seeding.preferLargerSwarms"), listPSorSC);

		final String[] boostQRPeersLabels = new String[9];
		final int[] boostQRPeersValues = new int[9];
		String peers = MessageText.getString("ConfigView.text.peers");
		for (int i = 0; i < boostQRPeersValues.length; i++) {
			boostQRPeersLabels[i] = (i + 1) + " " + peers; //$NON-NLS-1$
			boostQRPeersValues[i] = (i + 1);
		}
		add(new IntListParameterImpl("StartStopManager_iMinPeersToBoostNoSeeds",
				"ConfigView.label.minPeersToBoostNoSeeds", boostQRPeersValues,
				boostQRPeersLabels), listPSorSC);

		ParameterGroupImpl pgPSorSC = new ParameterGroupImpl(null, listPSorSC);
		add("sas.pgPSorSC", pgPSorSC);

		paramRankType.addListener(param -> {
			int rankType = paramRankType.getValue();
			pgSeedOptions.setVisible(
					rankType == StartStopRulesDefaultPlugin.RANK_SEEDCOUNT);
			pgTimedOptions.setVisible(
					rankType == StartStopRulesDefaultPlugin.RANK_TIMED);
			pgPSorSC.setVisible(rankType == StartStopRulesDefaultPlugin.RANK_SPRATIO
					|| rankType == StartStopRulesDefaultPlugin.RANK_SEEDCOUNT);
			pgSeedOptions.setEnabled(
					rankType == StartStopRulesDefaultPlugin.RANK_SEEDCOUNT);
			pgTimedOptions.setEnabled(
					rankType == StartStopRulesDefaultPlugin.RANK_TIMED);
			pgPSorSC.setEnabled(rankType == StartStopRulesDefaultPlugin.RANK_SPRATIO
					|| rankType == StartStopRulesDefaultPlugin.RANK_SEEDCOUNT);
		});
		paramRankType.fireParameterChanged();

		add(new BooleanParameterImpl("StartStopManager_bAutoStart0Peers",
				"ConfigView.label.seeding.autoStart0Peers"));
	}
}
