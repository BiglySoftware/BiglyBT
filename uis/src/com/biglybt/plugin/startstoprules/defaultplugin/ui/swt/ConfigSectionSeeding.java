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

import com.biglybt.pifimpl.local.ui.config.BooleanParameterImpl;
import com.biglybt.pifimpl.local.ui.config.IntParameterImpl;
import com.biglybt.ui.config.ConfigSectionImpl;

/** Seeding Automation Specific options
 * @author TuxPaper
 * @created Jan 12, 2004
 *
 * TODO: StartStopManager_fAddForSeedingULCopyCount
 */
public class ConfigSectionSeeding
	extends ConfigSectionImpl
{

	public static final String SECTION_ID = "queue.seeding";

	public ConfigSectionSeeding() {
		super(SECTION_ID, ConfigSectionQueue.SECTION_ID);
	}

	@Override
	public void build() {
		// Seeding Automation Setup

		// General Seeding Options

		add(new IntParameterImpl("StartStopManager_iMinSeedingTime",
				"ConfigView.label.minSeedingTime", 0, Integer.MAX_VALUE));

		// don't start more seeds

		BooleanParameterImpl dontStartMore = new BooleanParameterImpl(
				"StartStopManager_bStartNoMoreSeedsWhenUpLimitMet",
				"ConfigView.label.bStartNoMoreSeedsWhenUpLimitMet");
		add(dontStartMore);

		IntParameterImpl slack = new IntParameterImpl(
				"StartStopManager_bStartNoMoreSeedsWhenUpLimitMetSlack",
				"ConfigView.label.bStartNoMoreSeedsWhenUpLimitMetSlack", 0,
				Integer.MAX_VALUE);
		add(slack);
		slack.setIndent(1, true);

		BooleanParameterImpl slackIsPercent = new BooleanParameterImpl(
				"StartStopManager_bStartNoMoreSeedsWhenUpLimitMetPercent",
				"ConfigView.label.bStartNoMoreSeedsWhenUpLimitMetPercent");
		add(slackIsPercent);
		slackIsPercent.setIndent(1, true);

		dontStartMore.addEnabledOnSelection(slack, slackIsPercent);

		// disconnect seeds when seeding

		add(new BooleanParameterImpl("Disconnect Seed",
				"ConfigView.label.disconnetseed"));

		add(new BooleanParameterImpl("Use Super Seeding",
				"ConfigView.label.userSuperSeeding"));

		add(new BooleanParameterImpl("StartStopManager_bAutoReposition",
				"ConfigView.label.seeding.autoReposition"));

		add(new IntParameterImpl("StartStopManager_iAddForSeedingDLCopyCount",
				"ConfigView.label.seeding.addForSeedingDLCopyCount", 0,
				Integer.MAX_VALUE));

		IntParameterImpl paramFakeFullCopy = new IntParameterImpl(
				"StartStopManager_iNumPeersAsFullCopy",
				"ConfigView.label.seeding.numPeersAsFullCopy", 0, Integer.MAX_VALUE);
		add(paramFakeFullCopy);
		paramFakeFullCopy.setSuffixLabelKey("ConfigView.label.peers");

		IntParameterImpl fakeFullCopySeedStart = new IntParameterImpl(
				"StartStopManager_iFakeFullCopySeedStart",
				"ConfigView.label.seeding.fakeFullCopySeedStart", 0, Integer.MAX_VALUE);
		add(fakeFullCopySeedStart);
		fakeFullCopySeedStart.setSuffixLabelKey("ConfigView.label.seeds");
		fakeFullCopySeedStart.setIndent(1, true);

		paramFakeFullCopy.addListener(p -> {
			try {
				int iNumPeersAsFullCopy = paramFakeFullCopy.getValue();
				boolean enabled = (iNumPeersAsFullCopy != 0);
				fakeFullCopySeedStart.setEnabled(enabled);
			} catch (Exception ignored) {
			}
		});
		paramFakeFullCopy.fireParameterChanged();
	}
}
