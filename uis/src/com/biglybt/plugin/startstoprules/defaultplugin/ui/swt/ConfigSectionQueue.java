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

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.pifimpl.local.ui.config.BooleanParameterImpl;
import com.biglybt.pifimpl.local.ui.config.IntListParameterImpl;
import com.biglybt.pifimpl.local.ui.config.IntParameterImpl;
import com.biglybt.pifimpl.local.ui.config.ParameterGroupImpl;
import com.biglybt.ui.config.ConfigSectionImpl;

import com.biglybt.pif.ui.config.ConfigSection;

/** General Queueing options
 * @author TuxPaper
 * @created Jan 12, 2004
 */
public class ConfigSectionQueue
	extends ConfigSectionImpl
{

	public static final String SECTION_ID = "queue";

	public ConfigSectionQueue() {
		super(SECTION_ID, ConfigSection.SECTION_ROOT);
	}

	/**
	 * Create the "Queue" Tab in the Configuration view
	 */
	@Override
	public void build() {
		// main tab set up

		// row

		IntParameterImpl maxDLs = new IntParameterImpl("max downloads",
				"ConfigView.label.maxdownloads", 0, Integer.MAX_VALUE);
		add(maxDLs);

		// subrow - ignore checking downloads
		BooleanParameterImpl ignoreChecking = new BooleanParameterImpl(
				"StartStopManager_bMaxDownloadIgnoreChecking",
				"ConfigView.label.ignoreChecking");
		add(ignoreChecking);
		ignoreChecking.setIndent(1, true);

		// row

		IntParameterImpl maxActiv = new IntParameterImpl("max active torrents",
				"ConfigView.label.maxactivetorrents", 0, Integer.MAX_VALUE);
		add(maxActiv);

		BooleanParameterImpl maxActiveWhenSeedingEnabled = new BooleanParameterImpl(
				"StartStopManager_bMaxActiveTorrentsWhenSeedingEnabled",
				"ConfigView.label.queue.maxactivetorrentswhenseeding") {
			@Override
			public boolean resetToDefault() {
				COConfigurationManager.removeParameter(
						"StartStopManager_iMaxActiveTorrentsWhenSeeding");
				return super.resetToDefault();
			}
		};
		add(maxActiveWhenSeedingEnabled);
		maxActiveWhenSeedingEnabled.setIndent(1, true);

		IntParameterImpl maxActivWhenSeeding = new IntParameterImpl(
				"StartStopManager_iMaxActiveTorrentsWhenSeeding", null, 0,
				Integer.MAX_VALUE);
		add(maxActivWhenSeeding);

		maxActiveWhenSeedingEnabled.addEnabledOnSelection(maxActivWhenSeeding);

		add("q.maxActiv2col",
				new ParameterGroupImpl(null, maxActiveWhenSeedingEnabled,
						maxActivWhenSeeding).setNumberOfColumns2(2));

		// min downloads

		IntParameterImpl minDLs = new IntParameterImpl("min downloads",
				"ConfigView.label.mindownloads", 0, maxDLs.getValue());
		add(minDLs);

		BooleanParameterImpl minmaxlink = new BooleanParameterImpl(
				"StartStopManager_bMaxMinDLLinked",
				"ConfigView.label.maxmindownloadlinked");
		add(minmaxlink);
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
		add(new IntListParameterImpl("StartStopManager_iMinSpeedForActiveDL",
				"ConfigView.label.minSpeedForActiveDL", activeDLValues,
				activeDLLabels));

		// row

		String[] activeSeedingLabels = new String[values.size() - 4];
		int[] activeSeedingValues = new int[activeSeedingLabels.length];
		System.arraycopy(activeDLLabels, 0, activeSeedingLabels, 0,
				activeSeedingLabels.length);
		System.arraycopy(activeDLValues, 0, activeSeedingValues, 0,
				activeSeedingValues.length);

		add(new IntListParameterImpl("StartStopManager_iMinSpeedForActiveSeeding",
				"ConfigView.label.minSpeedForActiveSeeding", activeSeedingValues,
				activeSeedingLabels));

		// subrow

		IntParameterImpl maxStalledSeeding = new IntParameterImpl(
				"StartStopManager_iMaxStalledSeeding",
				"ConfigView.label.maxStalledSeeding", 0, Integer.MAX_VALUE);
		add(maxStalledSeeding);
		maxStalledSeeding.setIndent(1, true);

		BooleanParameterImpl maxStalledSeedingIgnoreZP = new BooleanParameterImpl(
				"StartStopManager_bMaxStalledSeedingIgnoreZP",
				"ConfigView.label.maxStalledSeedingIgnoreZP");
		add(maxStalledSeedingIgnoreZP);
		maxStalledSeedingIgnoreZP.setIndent(1, true);

		add(new BooleanParameterImpl("StartStopManager_bStopOnceBandwidthMet",
				"ConfigView.label.queue.stoponcebandwidthmet"));

		add(new BooleanParameterImpl("StartStopManager_bNewSeedsMoveTop",
				"ConfigView.label.queue.newseedsmovetop"));

		add(new BooleanParameterImpl(
				"StartStopManager_bRetainForceStartWhenComplete",
				"ConfigView.label.queue.retainforce"));

		add(new BooleanParameterImpl("Alert on close",
				"ConfigView.label.showpopuponclose"));

		add(new BooleanParameterImpl("StartStopManager_bDebugLog",
				"ConfigView.label.queue.debuglog"));
	}
}
