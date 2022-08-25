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

package com.biglybt.plugin.startstoprules.defaultplugin;

import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.impl.ConfigurationDefaults;
import com.biglybt.core.config.impl.ConfigurationParameterNotFoundException;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DisplayFormatters;

import com.biglybt.core.util.Wiki;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.config.*;
import com.biglybt.pif.ui.model.BasicPluginConfigModel;

public class StartStopConfigModel
{

	public static final String SECTION_ID_Q = "queue";

	public static final String SECTION_ID_Q_SEEDING = "queue.seeding";

	public static final String SECTION_ID_Q_DL = "queue.downloading";

	public static final String SECTION_ID_Q_SEEDING_AUTO_STARTING = "queue.seeding.autoStarting";

	public static final String SECTION_ID_Q_SEEDING_FP = "queue.seeding.firstPriority";

	public static final String SECTION_ID_Q_SEEDING_IGNORE = "queue.seeding.ignore";

	private final PluginInterface pi;

	private final ConfigurationDefaults def;

	public StartStopConfigModel(PluginInterface pi) {
		this.pi = pi;

		// Some params already have defaults set in core.  No PI to access defaults,
		// so access them directly :O

		def = ConfigurationDefaults.getInstance();

		initQueueSection();
		initDownloadingSection();
		initSeedingSection();
		initSeedingAutoStarting();
		initSeedingFirstPriority();
		initSeedingIgnore();
	}

	private void initSeedingIgnore() {
		UIManager manager = pi.getUIManager();

		BasicPluginConfigModel model = manager.createBasicPluginConfigModel(
				SECTION_ID_Q_SEEDING, SECTION_ID_Q_SEEDING_IGNORE);

		// Seeding Automation Setup

		model.addLabelParameter2("ConfigView.label.autoSeedingIgnoreInfo");

		List<Parameter> listIgnore = new ArrayList<>();

		IntParameter ignoreSeedCount = model.addIntParameter2(
				"StartStopManager_iIgnoreSeedCount", "ConfigView.label.ignoreSeeds", 0,
				0, 9999);
		listIgnore.add(ignoreSeedCount);
		ignoreSeedCount.setSuffixLabelKey("ConfigView.label.seeds");

		IntParameter stopPeersRatio = addDefaultedIntParam(model,
				"Stop Peers Ratio", "ConfigView.label.seeding.ignoreRatioPeers", 0,
				9999);
		listIgnore.add(stopPeersRatio);
		stopPeersRatio.setSuffixLabelKey("ConfigView.label.peers");

		IntParameter ignoreRatioPeersSeedStart = model.addIntParameter2(
				"StartStopManager_iIgnoreRatioPeersSeedStart",
				"ConfigView.label.seeding.fakeFullCopySeedStart", 0, 0, 9999);
		listIgnore.add(ignoreRatioPeersSeedStart);
		ignoreRatioPeersSeedStart.setSuffixLabelKey("ConfigView.label.seeds");
		ignoreRatioPeersSeedStart.setIndent(1, true);

		// Share Ratio
		FloatParameter stopRatio = addDefaultedFloatParam(model, "Stop Ratio",
				"ConfigView.label.seeding.ignoreShareRatio", 1, -1, true, 1);
		listIgnore.add(stopRatio);
		stopRatio.setSuffixLabelText(":1");

		IntParameter ignoreShareRatioSeedStart = model.addIntParameter2(
				"StartStopManager_iIgnoreShareRatioSeedStart",
				"ConfigView.label.seeding.fakeFullCopySeedStart", 0, 0, 9999);
		listIgnore.add(ignoreShareRatioSeedStart);
		ignoreShareRatioSeedStart.setSuffixLabelKey("ConfigView.label.seeds");
		ignoreShareRatioSeedStart.setIndent(1, true);

		// Ignore 0 Peers
		listIgnore.add(model.addBooleanParameter2("StartStopManager_bIgnore0Peers",
				"ConfigView.label.seeding.ignore0Peers", true));

		model.createGroup("ConfigView.label.seeding.ignore",
				listIgnore.toArray(new Parameter[0]));
	}

	private void initSeedingFirstPriority() {
		UIManager manager = pi.getUIManager();

		BasicPluginConfigModel model = manager.createBasicPluginConfigModel(
				SECTION_ID_Q_SEEDING, SECTION_ID_Q_SEEDING_FP);

		// Seeding Automation Setup

		model.addLabelParameter2("ConfigView.label.seeding.firstPriority.info");

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
		IntListParameter firstPriorityType = model.addIntListParameter2(
				"StartStopManager_iFirstPriority_Type",
				"ConfigView.label.seeding.firstPriority", fpValues, fpLabels,
				DefaultRankCalculator.FIRSTPRIORITY_ANY);
		firstPriorityType.setSuffixLabelKey(
				"ConfigView.label.seeding.firstPriority.following");

		// visual tweak so drop down doesn't align with future parameter controls
		listFP.add(model.createGroup(null, firstPriorityType));

		// row
		String[] minQueueLabels = new String[57];
		int[] minQueueValues = new int[57];
		int mqpos = 0;
		minQueueLabels[mqpos] = MessageText.getString("ConfigView.text.ignore");
		minQueueValues[mqpos++] = 0;
		minQueueLabels[mqpos] = "1:2 (" + 0.5 + ")";
		minQueueValues[mqpos++] = 500;
		minQueueLabels[mqpos] = "3:4 (" + 0.75 + ")";
		minQueueValues[mqpos++] = 750;
		minQueueLabels[mqpos] = "1:1";
		minQueueValues[mqpos++] = 1000;
		minQueueLabels[mqpos] = "5:4 (" + 1.25 + ")";
		minQueueValues[mqpos++] = 1250;
		minQueueLabels[mqpos] = "3:2 (" + 1.50 + ")";
		minQueueValues[mqpos++] = 1500;
		minQueueLabels[mqpos] = "7:4 (" + 1.75 + ")";
		minQueueValues[mqpos++] = 1750;
		minQueueLabels[mqpos] = "2:1";
		minQueueValues[mqpos++] = 2000;
		for (int i = mqpos; i < minQueueLabels.length; i++) {
			minQueueLabels[i] = i - 5 + ":1";
			minQueueValues[i] = (i - 5) * 1000;
		}
		listFP.add(
				model.addIntListParameter2("StartStopManager_iFirstPriority_ShareRatio",
						"ConfigView.label.seeding.firstPriority.shareRatio", minQueueValues,
						minQueueLabels, 500));

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
		listFP.add(
				model.addIntListParameter2("StartStopManager_iFirstPriority_DLMinutes",
						"ConfigView.label.seeding.firstPriority.DLMinutes", dlTimeValues,
						dlTimeLabels, 0));

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
		listFP.add(model.addIntListParameter2(
				"StartStopManager_iFirstPriority_SeedingMinutes",
				"ConfigView.label.seeding.firstPriority.seedingMinutes", seedTimeValues,
				seedTimeLabels, 0));

		model.createGroup("ConfigView.label.seeding.firstPriority.FP",
				listFP.toArray(new Parameter[0]));

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
		listIgnoreFP.add(model.addIntListParameter2(
				"StartStopManager_iFirstPriority_ignoreSPRatio",
				"ConfigView.label.seeding.firstPriority.ignoreSPRatio",
				ignoreSPRatioValues, ignoreSPRatioLabels, 0));

		//	 Ignore 0 Peers
		listIgnoreFP.add(model.addBooleanParameter2(
				"StartStopManager_bFirstPriority_ignore0Peer",
				"ConfigView.label.seeding.firstPriority.ignore0Peer",
				!COConfigurationManager.getStringParameter("ui", "").equals("az2")));

		// Ignore idle mins
		int[] availIdleMinutes = {
			5,
			10,
			15,
			20,
			30,
			45,
			60,
			2*60,
			3*60,
			4*60,
			5*60,
			6*60,
			7*60,
			8*60,
			12*60,
			18*60,
			24*60,
			48*60,
			72*60,
			168*60,
		};
		
		String[] ignoreIdleMinutesLabels = new String[availIdleMinutes.length + 1];
		
		int[] ignoreIdleMinutesValues = new int[availIdleMinutes.length + 1];
		
		ignoreIdleMinutesLabels[0] = MessageText.getString("ConfigView.text.ignore");
		
		ignoreIdleMinutesValues[0] = 0;
		
		for (int i = 0; i < availIdleMinutes.length; i++) {
			
			int mins = availIdleMinutes[i];
			
			ignoreIdleMinutesLabels[i + 1] = mins<60?( mins + " " + sMinutes ):((mins/60) + " " + sHours );
			
			ignoreIdleMinutesValues[i + 1] = availIdleMinutes[i];
		}
		
		listIgnoreFP.add(model.addIntListParameter2(
				"StartStopManager_iFirstPriority_ignoreIdleMinutes",
				"ConfigView.label.seeding.firstPriority.ignoreIdle",
				ignoreIdleMinutesValues, ignoreIdleMinutesLabels, 0 ));

		//	 row

		LabelParameter fpIgnoreInfo = model.addLabelParameter2(
				"ConfigView.label.seeding.firstPriority.ignore.info");
		listIgnoreFP.add(fpIgnoreInfo);
		// hack the /n out
		fpIgnoreInfo.setLabelText(
				fpIgnoreInfo.getLabelText().replaceAll("\n", " "));

		model.createGroup("ConfigView.label.seeding.firstPriority.ignore",
				listIgnoreFP.toArray(new Parameter[0]));

		model.addBooleanParameter2("StartStopManager_bTagFirstPriority",
				"ConfigView.label.queue.tagfirstpriority", false);
	}

	private void initSeedingAutoStarting() {
		UIManager manager = pi.getUIManager();

		BasicPluginConfigModel model = manager.createBasicPluginConfigModel(
				SECTION_ID_Q_SEEDING, SECTION_ID_Q_SEEDING_AUTO_STARTING);
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

		IntListParameter paramRankType = model.addIntListParameter2(
				"StartStopManager_iRankType", null, rankValues, rankLabels,
				StartStopRulesDefaultPlugin.RANK_SPRATIO);
		paramRankType.setListType(IntListParameter.TYPE_RADIO_LIST);

		// Seed Count options

		IntParameter paramSeedFallback = model.addIntParameter2(
				"StartStopManager_iRankTypeSeedFallback",
				"ConfigView.label.seeding.rankType.seed.fallback", 0, 0,
				Integer.MAX_VALUE);
		paramSeedFallback.setSuffixLabelKey("ConfigView.label.seeds");

		ParameterGroup pgSeedOptions = model.createGroup(
				"ConfigView.label.seeding.rankType.seed.options", paramSeedFallback);

		// timed rotation ranking type

		IntParameter paramMinSeedingTimeWithPeers = model.addIntParameter2(
				"StartStopManager_iTimed_MinSeedingTimeWithPeers",
				"ConfigView.label.seeding.rankType.timed.minTimeWithPeers", 0, 0,
				Integer.MAX_VALUE);

		ParameterGroup pgTimedOptions = model.createGroup(
				"ConfigView.label.seeding.rankType.timed.options",
				paramMinSeedingTimeWithPeers);

		ParameterGroup pgRankTypeOptions = model.createGroup(null, pgSeedOptions,
				pgTimedOptions);

		model.createGroup("ConfigView.label.seeding.rankType", paramRankType,
				pgRankTypeOptions).setNumberOfColumns(2);

		// ** End Rank Type area

		List<Parameter> listPSorSC = new ArrayList<>();

		listPSorSC.add(
				model.addBooleanParameter2("StartStopManager_bPreferLargerSwarms",
						"ConfigView.label.seeding.preferLargerSwarms", true));

		final String[] boostQRPeersLabels = new String[9];
		final int[] boostQRPeersValues = new int[9];
		String peers = MessageText.getString("ConfigView.text.peers");
		for (int i = 0; i < boostQRPeersValues.length; i++) {
			boostQRPeersLabels[i] = (i + 1) + " " + peers; //$NON-NLS-1$
			boostQRPeersValues[i] = (i + 1);
		}
		listPSorSC.add(
				model.addIntListParameter2("StartStopManager_iMinPeersToBoostNoSeeds",
						"ConfigView.label.minPeersToBoostNoSeeds", boostQRPeersValues,
						boostQRPeersLabels, 1));

		ParameterGroup pgPSorSC = model.createGroup(null,
				listPSorSC.toArray(new Parameter[0]));

		ParameterListener rankTypeListener = param -> {
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
		};
		paramRankType.addListener(rankTypeListener);
		rankTypeListener.parameterChanged(null);

		model.addBooleanParameter2("StartStopManager_bAutoStart0Peers",
				"ConfigView.label.seeding.autoStart0Peers", false);
	}

	private void initSeedingSection() {
		UIManager manager = pi.getUIManager();

		BasicPluginConfigModel model = manager.createBasicPluginConfigModel(
				SECTION_ID_Q, SECTION_ID_Q_SEEDING);

		// Seeding Automation Setup

		// General Seeding Options

		model.addIntParameter2("StartStopManager_iMinSeedingTime",
				"ConfigView.label.minSeedingTime", 60 * 10, 0, Integer.MAX_VALUE);

		// don't start more seeds

		BooleanParameter dontStartMore = model.addBooleanParameter2(
				"StartStopManager_bStartNoMoreSeedsWhenUpLimitMet",
				"ConfigView.label.bStartNoMoreSeedsWhenUpLimitMet", false);

		IntParameter slack = model.addIntParameter2(
				"StartStopManager_bStartNoMoreSeedsWhenUpLimitMetSlack",
				"ConfigView.label.bStartNoMoreSeedsWhenUpLimitMetSlack", 95, 0,
				Integer.MAX_VALUE);
		slack.setIndent(1, true);

		BooleanParameter slackIsPercent = model.addBooleanParameter2(
				"StartStopManager_bStartNoMoreSeedsWhenUpLimitMetPercent",
				"ConfigView.label.bStartNoMoreSeedsWhenUpLimitMetPercent", true);
		slackIsPercent.setIndent(1, true);

		dontStartMore.addEnabledOnSelection(slack, slackIsPercent);

		// disconnect seeds when seeding

		addDefaultedBooleanParam(model, "Disconnect Seed",
				"ConfigView.label.disconnetseed");

		addDefaultedBooleanParam(model, "Use Super Seeding",
				"ConfigView.label.userSuperSeeding");

		addDefaultedBooleanParam(model, "Enable Light Seeding",
				"ConfigView.label.enableLightSeeding");

		model.addIntParameter2("Light Seed Slots Reserved",
				"ConfigView.label.light.seeding.reserved.slots", 4, 0,
				1024);
		
		model.addIntParameter2("Flexible Seed Slots",
				"ConfigView.label.seeding.flexible.slots", 5, 0,
				1024);

		model.addBooleanParameter2("StartStopManager_bAutoReposition",
				"ConfigView.label.seeding.autoReposition", false);

		model.addIntParameter2("StartStopManager_iAddForSeedingDLCopyCount",
				"ConfigView.label.seeding.addForSeedingDLCopyCount", 1, 0,
				Integer.MAX_VALUE);

		IntParameter paramFakeFullCopy = model.addIntParameter2(
				"StartStopManager_iNumPeersAsFullCopy",
				"ConfigView.label.seeding.numPeersAsFullCopy", 0, 0, Integer.MAX_VALUE);
		paramFakeFullCopy.setSuffixLabelKey("ConfigView.label.peers");

		IntParameter fakeFullCopySeedStart = model.addIntParameter2(
				"StartStopManager_iFakeFullCopySeedStart",
				"ConfigView.label.seeding.fakeFullCopySeedStart", 1, 0,
				Integer.MAX_VALUE);
		fakeFullCopySeedStart.setSuffixLabelKey("ConfigView.label.seeds");
		fakeFullCopySeedStart.setIndent(1, true);

		ParameterListener fakeFullCopyListener = p -> {
			try {
				int iNumPeersAsFullCopy = paramFakeFullCopy.getValue();
				boolean enabled = (iNumPeersAsFullCopy != 0);
				fakeFullCopySeedStart.setEnabled(enabled);
			} catch (Exception ignored) {
			}
		};
		paramFakeFullCopy.addListener(fakeFullCopyListener);
		fakeFullCopyListener.parameterChanged(null);
	}

	private void initQueueSection() {
		UIManager manager = pi.getUIManager();

		BasicPluginConfigModel model = manager.createBasicPluginConfigModel(
				ConfigSection.SECTION_ROOT, SECTION_ID_Q);

		// main tab set up

		// row

		IntParameter maxDLs = addDefaultedIntParam(model, "max downloads",
				"ConfigView.label.maxdownloads", 0, Integer.MAX_VALUE);

		// subrow - ignore checking downloads
		BooleanParameter ignoreChecking = model.addBooleanParameter2(
				"StartStopManager_bMaxDownloadIgnoreChecking",
				"ConfigView.label.ignoreChecking", false);
		ignoreChecking.setIndent(1, true);

		// row

		IntParameter maxActiv = addDefaultedIntParam(model, "max active torrents",
				"ConfigView.label.maxactivetorrents", 0, Integer.MAX_VALUE);

		BooleanParameter maxActiveWhenSeedingEnabled = model.addBooleanParameter2(
				"StartStopManager_bMaxActiveTorrentsWhenSeedingEnabled",
				"ConfigView.label.queue.maxactivetorrentswhenseeding", false);
		maxActiveWhenSeedingEnabled.setIndent(1, true);

		IntParameter maxActivWhenSeeding = model.addIntParameter2(
				"StartStopManager_iMaxActiveTorrentsWhenSeeding", null, 0, 0,
				Integer.MAX_VALUE);

		// reset maxActivWhenSeeding when resetting maxActiveWhenSeedingEnabled
		maxActiveWhenSeedingEnabled.addListener(param -> {
			if (!maxActiveWhenSeedingEnabled.hasBeenSet()) {
				maxActivWhenSeeding.resetToDefault();
			}
		});
		maxActiveWhenSeedingEnabled.addEnabledOnSelection(maxActivWhenSeeding);

		ParameterGroup pgMaxActivWhenSeeding = model.createGroup(null,
				maxActiveWhenSeedingEnabled, maxActivWhenSeeding);
		pgMaxActivWhenSeeding.setNumberOfColumns(2);

		// min downloads

		IntParameter minDLs = model.addIntParameter2("min downloads",
				"ConfigView.label.mindownloads", 1, 0, maxDLs.getValue());

		BooleanParameter minmaxlink = model.addBooleanParameter2(
				"StartStopManager_bMaxMinDLLinked",
				"ConfigView.label.maxmindownloadlinked", false);
		minmaxlink.setIndent(1, true);

		minmaxlink.addDisabledOnSelection(minDLs);

		/////

		maxDLs.addListener(p -> {
			int iMaxDLs = maxDLs.getValue();
			minDLs.setMaxValue(iMaxDLs);//  / 2);

			//int iMinDLs = minDLs.getValue();
			int iMaxActive = maxActiv.getValue();

			if ((iMaxDLs == 0 || iMaxDLs > iMaxActive) && iMaxActive != 0) {
				maxActiv.setValue(iMaxDLs);
			}
		});

		maxActiv.addListener(p -> {
			int iMaxDLs = maxDLs.getValue();
			int iMaxActive = maxActiv.getValue();

			if ((iMaxDLs == 0 || iMaxDLs > iMaxActive) && iMaxActive != 0) {
				maxDLs.setValue(iMaxActive);
			}
		});

		// row

		List<Integer> values = new ArrayList<>();
		int exp = 29;
		for (int val = 0; val <= 8 * 1024 * 1024;) {
			values.add(val);
			if (val < 256)
				val += 64;
			else if (val < 1024)
				val += 256;
			else if (val < 16 * 1024)
				val += 1024;
			else
				val = (int) (Math.pow(2, exp++ / 2)
						+ (exp % 2 == 0 ? Math.pow(2, (exp - 3) / 2) : 0));
		}
		String[] activeDLLabels = new String[values.size()];
		int[] activeDLValues = new int[activeDLLabels.length];

		for (int i = 0; i < activeDLLabels.length; i++) {
			//noinspection ConstantConditions
			activeDLValues[i] = values.get(i);
			activeDLLabels[i] = DisplayFormatters.formatByteCountToKiBEtcPerSec(
					activeDLValues[i], true);

		}
		model.addIntListParameter2("StartStopManager_iMinSpeedForActiveDL",
				"ConfigView.label.minSpeedForActiveDL", activeDLValues, activeDLLabels,
				512);

		// row

		String[] activeSeedingLabels = new String[values.size() - 4];
		int[] activeSeedingValues = new int[activeSeedingLabels.length];
		System.arraycopy(activeDLLabels, 0, activeSeedingLabels, 0,
				activeSeedingLabels.length);
		System.arraycopy(activeDLValues, 0, activeSeedingValues, 0,
				activeSeedingValues.length);

		IntListParameter minSpeedForActiveSeeding = model.addIntListParameter2(
				"StartStopManager_iMinSpeedForActiveSeeding",
				"ConfigView.label.minSpeedForActiveSeeding", activeSeedingValues,
				activeSeedingLabels, 512);

		// subrow

		IntParameter maxStalledSeeding = model.addIntParameter2(
				"StartStopManager_iMaxStalledSeeding",
				"ConfigView.label.maxStalledSeeding", 5, 0, Integer.MAX_VALUE);
		maxStalledSeeding.setIndent(1, true);

		BooleanParameter maxStalledSeedingIgnoreZP = model.addBooleanParameter2(
				"StartStopManager_bMaxStalledSeedingIgnoreZP",
				"ConfigView.label.maxStalledSeedingIgnoreZP", true);
		maxStalledSeedingIgnoreZP.setIndent(1, true);

		ParameterListener minSpeedForActiveSeedingListener = p -> {
			boolean enabled = minSpeedForActiveSeeding.getValue() != 0;
			maxStalledSeeding.setEnabled(enabled);
			maxStalledSeedingIgnoreZP.setEnabled(enabled);
		};
		minSpeedForActiveSeeding.addListener(minSpeedForActiveSeedingListener);
		minSpeedForActiveSeedingListener.parameterChanged(null);


		model.addBooleanParameter2("StartStopManager_bStopOnceBandwidthMet",
				"ConfigView.label.queue.stoponcebandwidthmet", true);

		model.addBooleanParameter2("StartStopManager_bNewSeedsMoveTop",
				"ConfigView.label.queue.newseedsmovetop", true);

		model.addBooleanParameter2("StartStopManager_bRetainForceStartWhenComplete",
				"ConfigView.label.queue.retainforce", false);

		BooleanParameter paramAlertOnClose = addDefaultedBooleanParam(model, "Alert on close",
			"ConfigView.label.showpopuponclose");
		paramAlertOnClose.setAllowedUiTypes(UIInstance.UIT_SWT);

		model.addBooleanParameter2("StartStopManager_bDebugLog",
				"ConfigView.label.queue.debuglog", false);
	}

	public void initDownloadingSection() {
		UIManager manager = pi.getUIManager();

		BasicPluginConfigModel model = manager.createBasicPluginConfigModel(
				SECTION_ID_Q, SECTION_ID_Q_DL);

		// Seeding Automation Setup

		// wiki link

		model.addHyperlinkParameter2("ConfigView.label.please.visit.here",
				Wiki.DOWNLOADING_RULES);

		// sort type

		String[] orderLabels = {
			MessageText.getString("label.order"),
			MessageText.getString("label.seed.count"),
			MessageText.getString("label.reverse.seed.count"),
			MessageText.getString("TableColumn.header.size"),
			MessageText.getString("label.reverse.size"),
			MessageText.getString("label.speed"),
			MessageText.getString("TableColumn.header.eta"),
			MessageText.getString("TableColumn.header.file.priorities"),
		};

		int[] orderValues = {
			DefaultRankCalculator.DOWNLOAD_ORDER_INDEX,
			DefaultRankCalculator.DOWNLOAD_ORDER_SEED_COUNT,
			DefaultRankCalculator.DOWNLOAD_ORDER_REVERSE_SEED_COUNT,
			DefaultRankCalculator.DOWNLOAD_ORDER_SIZE,
			DefaultRankCalculator.DOWNLOAD_ORDER_REVERSE_SIZE,
			DefaultRankCalculator.DOWNLOAD_ORDER_SPEED,
			DefaultRankCalculator.DOWNLOAD_ORDER_ETA,
			DefaultRankCalculator.DOWNLOAD_ORDER_FILE_PRIORITIES,
		};

		IntListParameter sort_type = model.addIntListParameter2(
				"StartStopManager_Downloading_iSortType",
				"label.prioritize.downloads.based.on", orderValues, orderLabels,
				DefaultRankCalculator.DOWNLOAD_ORDER_INDEX);

		List<Parameter> listSpeed = new ArrayList<>();

		// info

		listSpeed.add(
				model.addLabelParameter2("ConfigView.label.downloading.info"));

		// test time

		IntParameter testTime = model.addIntParameter2(
				"StartStopManager_Downloading_iTestTimeSecs",
				"ConfigView.label.downloading.testTime", 120);
		listSpeed.add(testTime);
		testTime.setMinValue(60);

		// re-test

		IntParameter reTest = model.addIntParameter2(
				"StartStopManager_Downloading_iRetestTimeMins",
				"ConfigView.label.downloading.reTest", 30);
		listSpeed.add(reTest);
		reTest.setMinValue(0);

		BooleanParameter testActive = model.addBooleanParameter2(
				"StartStopManager_Downloading_bTestActive",
				"ConfigView.label.downloading.testActive", false);
		listSpeed.add(testActive);

		ParameterListener sortTypeListener = p -> {
			int type = sort_type.getValue();

			boolean is_speed = type == DefaultRankCalculator.DOWNLOAD_ORDER_SPEED
					|| type == DefaultRankCalculator.DOWNLOAD_ORDER_ETA;

			testTime.setEnabled(is_speed);
			reTest.setEnabled(is_speed);
			testActive.setEnabled(is_speed);
		};
		sort_type.addListener(sortTypeListener);
		sortTypeListener.parameterChanged(null);

		model.createGroup("label.speed.options",
				listSpeed.toArray(new Parameter[0]));

		model.addBooleanParameter2("StartStopManager_bAddForDownloadingSR1",
				"ConfigView.label.downloading.addsr1", true);

	}

	/**
	 * Some Queue rules use params originally stored in core with defaults
	 */
	private IntParameter addDefaultedIntParam(BasicPluginConfigModel model,
			String key, String labelKey, int min, int max) {
		try {
			return model.addIntParameter2(key, labelKey, def.getIntParameter(key),
					min, max);
		} catch (ConfigurationParameterNotFoundException e) {
			Debug.out(e);
		}
		return null;
	}

	/**
	 * Some Queue rules use params originally stored in core with defaults
	 */
	private FloatParameter addDefaultedFloatParam(BasicPluginConfigModel model,
			String key, String labelKey, float min, float max, boolean allowZero,
			int digitsAfterDecimal) {
		try {
			return model.addFloatParameter2(key, labelKey, def.getFloatParameter(key),
					min, max, allowZero, digitsAfterDecimal);
		} catch (ConfigurationParameterNotFoundException e) {
			Debug.out(e);
		}
		return null;
	}

	/**
	 * Some Queue rules use params originally stored in core with defaults
	 */
	private BooleanParameter addDefaultedBooleanParam(
			BasicPluginConfigModel model, String key, String labelKey) {
		try {
			return model.addBooleanParameter2(key, labelKey,
					def.getBooleanParameter(key));
		} catch (ConfigurationParameterNotFoundException e) {
			Debug.out(e);
		}
		return null;
	}
}
