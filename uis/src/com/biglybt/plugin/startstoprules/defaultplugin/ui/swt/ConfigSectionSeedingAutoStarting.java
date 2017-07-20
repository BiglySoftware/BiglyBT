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

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.config.*;
import com.biglybt.ui.swt.pif.UISWTConfigSection;

import com.biglybt.plugin.startstoprules.defaultplugin.StartStopRulesDefaultPlugin;

/** Auto Starting specific options
 * @author TuxPaper
 * @created Jan 12, 2004
 */
public class ConfigSectionSeedingAutoStarting implements UISWTConfigSection {
  @Override
  public String configSectionGetParentSection() {
		return "queue.seeding";
  }

	@Override
	public String configSectionGetName() {
		return "queue.seeding.autoStarting";
	}

  @Override
  public void configSectionSave() {
  }

  @Override
  public void configSectionDelete() {
  }

	@Override
	public int maxUserMode() {
		return 0;
	}

  @Override
  public Composite configSectionCreate(Composite parent) {
    // Seeding Automation Setup
    GridData gridData;
    GridLayout layout;
    Label label;

    Composite gQR = new Composite(parent, SWT.NULL);

    layout = new GridLayout();
    layout.numColumns = 1;
    layout.marginHeight = 0;
    gQR.setLayout(layout);
    gridData = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
    gQR.setLayoutData(gridData);


    // ** Begin Rank Type area
    // Rank Type area.  Encompases the 4 (or more) options groups

    Composite cRankType = new Group(gQR, SWT.NULL);
    layout = new GridLayout();
    layout.numColumns = 2;
    layout.verticalSpacing = 2;
    cRankType.setLayout(layout);
    gridData = new GridData(GridData.HORIZONTAL_ALIGN_FILL);
    cRankType.setLayoutData(gridData);
    Messages.setLanguageText(cRankType, "ConfigView.label.seeding.rankType");

    // Seeds:Peer options
    RadioParameter rparamPeerSeed =
        new RadioParameter(cRankType, "StartStopManager_iRankType",
                           StartStopRulesDefaultPlugin.RANK_SPRATIO);
    Messages.setLanguageText(rparamPeerSeed.getControl(), "ConfigView.label.seeding.rankType.peerSeed");
    gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
    rparamPeerSeed.setLayoutData(gridData);

    new Label(cRankType, SWT.NULL);


    // Seed Count options
    RadioParameter rparamSeedCount =
        new RadioParameter(cRankType, "StartStopManager_iRankType",
                           StartStopRulesDefaultPlugin.RANK_SEEDCOUNT);
    Messages.setLanguageText(rparamSeedCount.getControl(), "ConfigView.label.seeding.rankType.seed");
    gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
    rparamSeedCount.setLayoutData(gridData);

    Group gSeedCount = new Group(cRankType, SWT.NULL);
    layout = new GridLayout();
    layout.marginHeight = 2;
    layout.marginWidth = 2;
    layout.numColumns = 3;
    gSeedCount.setLayout(layout);
    gridData = new GridData(GridData.HORIZONTAL_ALIGN_FILL);
    gridData.verticalSpan = 1;
    gSeedCount.setLayoutData(gridData);
    Messages.setLanguageText(gSeedCount, "ConfigView.label.seeding.rankType.seed.options");

    label = new Label(gSeedCount, SWT.NULL);
    Messages.setLanguageText(label, "ConfigView.label.seeding.rankType.seed.fallback");

    gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
    IntParameter intParamFallBack = new IntParameter(gSeedCount, "StartStopManager_iRankTypeSeedFallback", 0, Integer.MAX_VALUE);
    intParamFallBack.setLayoutData(gridData);

    Label labelFallBackSeeds = new Label(gSeedCount, SWT.NULL);
    label.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_BEGINNING));
    Messages.setLanguageText(labelFallBackSeeds, "ConfigView.label.seeds");

    Control[] controlsSeedCount = { gSeedCount };
    rparamSeedCount.setAdditionalActionPerformer(new ChangeSelectionActionPerformer(controlsSeedCount));


    // weigted peer count ranking type
    RadioParameter rparamPeer =
        new RadioParameter(cRankType, "StartStopManager_iRankType",
                           StartStopRulesDefaultPlugin.RANK_PEERCOUNT);
    Messages.setLanguageText(rparamPeer.getControl(), "ConfigView.label.seeding.rankType.peer");
    gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
    rparamPeer.setLayoutData(gridData);

    new Label(cRankType, SWT.NULL);

    // timed rotation ranking type
    RadioParameter rparamTimed =
        new RadioParameter(cRankType, "StartStopManager_iRankType",
                           StartStopRulesDefaultPlugin.RANK_TIMED);
    Messages.setLanguageText(rparamTimed.getControl(), "ConfigView.label.seeding.rankType.timedRotation");
    gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
    rparamTimed.setLayoutData(gridData);

    Group gTimed = new Group(cRankType, SWT.NULL);
    layout = new GridLayout();
    layout.marginHeight = 2;
    layout.marginWidth = 2;
    layout.numColumns = 2;
    gTimed.setLayout(layout);
    gridData = new GridData(GridData.HORIZONTAL_ALIGN_FILL);
    gridData.verticalSpan = 1;
    gTimed.setLayoutData(gridData);
    Messages.setLanguageText(gTimed, "ConfigView.label.seeding.rankType.timed.options");

    label = new Label(gTimed, SWT.NULL);
    Messages.setLanguageText(label, "ConfigView.label.seeding.rankType.timed.minTimeWithPeers");

    gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
    IntParameter intParamTimedPeersMinTime = new IntParameter(gTimed, "StartStopManager_iTimed_MinSeedingTimeWithPeers", 0, Integer.MAX_VALUE);
    intParamTimedPeersMinTime.setLayoutData(gridData);

    Control[] controlsTimed = { gTimed };
    rparamTimed.setAdditionalActionPerformer(new ChangeSelectionActionPerformer(controlsTimed));


    // No Ranking
    RadioParameter rparamNone =
        new RadioParameter(cRankType, "StartStopManager_iRankType",
                           StartStopRulesDefaultPlugin.RANK_NONE);
    Messages.setLanguageText(rparamNone.getControl(), "ConfigView.label.seeding.rankType.none");
    gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
    rparamNone.setLayoutData(gridData);

    new Label(cRankType, SWT.NULL);

    // ** End Rank Type area


    Composite cNoTimeNone = new Composite(gQR, SWT.NULL);
    layout = new GridLayout();
    layout.numColumns = 2;
    cNoTimeNone.setLayout(layout);
    gridData = new GridData();
    layout.marginHeight = 0;
    layout.marginWidth = 0;
    cNoTimeNone.setLayoutData(gridData);

    gridData = new GridData();
    gridData.horizontalSpan = 2;
    new BooleanParameter(cNoTimeNone,
                         "StartStopManager_bPreferLargerSwarms",
                         "ConfigView.label.seeding.preferLargerSwarms").setLayoutData(gridData);



    label = new Label(cNoTimeNone, SWT.NULL);
    Messages.setLanguageText(label, "ConfigView.label.minPeersToBoostNoSeeds"); //$NON-NLS-1$
    final String boostQRPeersLabels[] = new String[9];
    final int boostQRPeersValues[] = new int[9];
    String peers = MessageText.getString("ConfigView.text.peers");
    for (int i = 0; i < boostQRPeersValues.length; i++) {
      boostQRPeersLabels[i] = (i+1) + " " + peers; //$NON-NLS-1$
      boostQRPeersValues[i] = (i+1);
    }
    gridData = new GridData();
    new IntListParameter(cNoTimeNone, "StartStopManager_iMinPeersToBoostNoSeeds", boostQRPeersLabels, boostQRPeersValues);

    Control[] controlsNoTimeNone = { cNoTimeNone };
    rparamPeerSeed.setAdditionalActionPerformer(new ChangeSelectionActionPerformer(controlsNoTimeNone));
    rparamSeedCount.setAdditionalActionPerformer(new ChangeSelectionActionPerformer(controlsNoTimeNone));

    int iRankType = COConfigurationManager.getIntParameter("StartStopManager_iRankType");
    boolean enable = (iRankType == StartStopRulesDefaultPlugin.RANK_SPRATIO ||
                      iRankType == StartStopRulesDefaultPlugin.RANK_SEEDCOUNT);
    controlsSetEnabled(controlsNoTimeNone, enable);


    new BooleanParameter(gQR, "StartStopManager_bAutoStart0Peers",
                         "ConfigView.label.seeding.autoStart0Peers");

    return gQR;
  }
  private void controlsSetEnabled(Control[] controls, boolean bEnabled) {
    for(int i = 0 ; i < controls.length ; i++) {
      if (controls[i] instanceof Composite)
        controlsSetEnabled(((Composite)controls[i]).getChildren(), bEnabled);
      controls[i].setEnabled(bEnabled);
    }
  }
}

