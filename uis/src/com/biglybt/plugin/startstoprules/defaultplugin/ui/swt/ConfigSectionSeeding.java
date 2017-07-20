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
import org.eclipse.swt.widgets.*;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.AERunnable;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.config.*;
import com.biglybt.ui.swt.pif.UISWTConfigSection;

import com.biglybt.ui.swt.imageloader.ImageLoader;

/** Seeding Automation Specific options
 * @author TuxPaper
 * @created Jan 12, 2004
 *
 * TODO: StartStopManager_fAddForSeedingULCopyCount
 */
public class ConfigSectionSeeding implements UISWTConfigSection {
  @Override
  public String configSectionGetParentSection() {
    return "queue";
  }

  @Override
  public String configSectionGetName() {
    return "queue.seeding";
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

    Composite cSeeding = new Composite(parent, SWT.NULL);

    layout = new GridLayout();
    layout.numColumns = 2;
    layout.marginHeight = 0;
    cSeeding.setLayout(layout);
    gridData = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
    Utils.setLayoutData(cSeeding, gridData);

    	// General Seeding Options

    label = new Label(cSeeding, SWT.NULL);
    Messages.setLanguageText(label, "ConfigView.label.minSeedingTime");
    gridData = new GridData();
    new IntParameter(cSeeding, "StartStopManager_iMinSeedingTime", 0, Integer.MAX_VALUE).setLayoutData(gridData);

    	// don't start more seeds

    gridData = new GridData();
    gridData.horizontalSpan = 2;
    final BooleanParameter dontStartMore =
    	new BooleanParameter(cSeeding, "StartStopManager_bStartNoMoreSeedsWhenUpLimitMet",
                         "ConfigView.label.bStartNoMoreSeedsWhenUpLimitMet");
    dontStartMore.setLayoutData(gridData);


    final Composite cDontStartOptions = new Composite(cSeeding, SWT.NULL);
    layout = new GridLayout();
    layout.numColumns = 3;
    layout.marginWidth = 0;
    layout.marginHeight = 0;
    cDontStartOptions.setLayout(layout);
    gridData = new GridData();
    gridData.horizontalIndent = 15;
    gridData.horizontalSpan = 2;
    Utils.setLayoutData(cDontStartOptions, gridData);

	label = new Label(cDontStartOptions, SWT.NULL);
	ImageLoader.getInstance().setLabelImage(label, "subitem");
    gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
    Utils.setLayoutData(label, gridData);

    Label xlabel = new Label(cDontStartOptions, SWT.NULL);
    Messages.setLanguageText(xlabel, "ConfigView.label.bStartNoMoreSeedsWhenUpLimitMetSlack");
    gridData = new GridData();
    IntParameter slack = new IntParameter(cDontStartOptions, "StartStopManager_bStartNoMoreSeedsWhenUpLimitMetSlack", 0, Integer.MAX_VALUE);
    slack.setLayoutData(gridData);

	label = new Label(cDontStartOptions, SWT.NULL);
	ImageLoader.getInstance().setLabelImage(label, "subitem");
	gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
    Utils.setLayoutData(label, gridData);

    gridData = new GridData();
    gridData.horizontalSpan = 2;
    BooleanParameter slackIsPercent = new BooleanParameter(cDontStartOptions, "StartStopManager_bStartNoMoreSeedsWhenUpLimitMetPercent",
                         "ConfigView.label.bStartNoMoreSeedsWhenUpLimitMetPercent");
    slackIsPercent.setLayoutData(gridData);

    dontStartMore.setAdditionalActionPerformer(new ChangeSelectionActionPerformer( slack, slackIsPercent ));
    dontStartMore.setAdditionalActionPerformer(new ChangeSelectionActionPerformer( xlabel ));

    	// disconnect seeds when seeding

    gridData = new GridData();
    gridData.horizontalSpan = 2;
    new BooleanParameter(cSeeding, "Disconnect Seed",
                         "ConfigView.label.disconnetseed").setLayoutData(gridData);

    gridData = new GridData();
    gridData.horizontalSpan = 2;
    new BooleanParameter(cSeeding, "Use Super Seeding",
                         "ConfigView.label.userSuperSeeding").setLayoutData(gridData);

    gridData = new GridData();
    gridData.horizontalSpan = 2;
    new BooleanParameter(cSeeding, "StartStopManager_bAutoReposition",
                         "ConfigView.label.seeding.autoReposition").setLayoutData(gridData);

    label = new Label(cSeeding, SWT.NULL);
    Messages.setLanguageText(label, "ConfigView.label.seeding.addForSeedingDLCopyCount");
    gridData = new GridData();
    new IntParameter(cSeeding, "StartStopManager_iAddForSeedingDLCopyCount", 0, Integer.MAX_VALUE).setLayoutData(gridData);

    label = new Label(cSeeding, SWT.NULL);
    Messages.setLanguageText(label, "ConfigView.label.seeding.numPeersAsFullCopy");

    Composite cArea = new Composite(cSeeding, SWT.NULL);
    layout = new GridLayout();
    layout.marginHeight = 0;
    layout.marginWidth = 0;
    layout.numColumns = 2;
    cArea.setLayout(layout);
    gridData = new GridData();
    Utils.setLayoutData(cArea, gridData);

    gridData = new GridData();
    final IntParameter paramFakeFullCopy = new IntParameter(cArea, "StartStopManager_iNumPeersAsFullCopy", 0, Integer.MAX_VALUE);
    paramFakeFullCopy.setLayoutData(gridData);

    label = new Label(cArea, SWT.NULL);
    Messages.setLanguageText(label, "ConfigView.label.peers");


    final Composite cFullCopyOptionsArea = new Composite(cSeeding, SWT.NULL);
    layout = new GridLayout();
    layout.numColumns = 4;
    layout.marginWidth = 0;
    layout.marginHeight = 0;
    cFullCopyOptionsArea.setLayout(layout);
    gridData = new GridData();
    gridData.horizontalIndent = 15;
    gridData.horizontalSpan = 2;
    Utils.setLayoutData(cFullCopyOptionsArea, gridData);

		label = new Label(cFullCopyOptionsArea, SWT.NULL);
		ImageLoader.getInstance().setLabelImage(label, "subitem");
    gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
    Utils.setLayoutData(label, gridData);

    label = new Label(cFullCopyOptionsArea, SWT.NULL);
    Messages.setLanguageText(label, "ConfigView.label.seeding.fakeFullCopySeedStart");

    gridData = new GridData();
    new IntParameter(cFullCopyOptionsArea, "StartStopManager_iFakeFullCopySeedStart", 0, Integer.MAX_VALUE).setLayoutData(gridData);
    label = new Label(cFullCopyOptionsArea, SWT.NULL);
    Messages.setLanguageText(label, "ConfigView.label.seeds");


    final int iNumPeersAsFullCopy = COConfigurationManager.getIntParameter("StartStopManager_iNumPeersAsFullCopy");
    controlsSetEnabled(cFullCopyOptionsArea.getChildren(), iNumPeersAsFullCopy != 0);

    paramFakeFullCopy.addChangeListener(new ParameterChangeAdapter() {
			@Override
			public void parameterChanged(Parameter p, boolean caused_internally) {
				Utils.execSWTThread(new AERunnable() {
					@Override
					public void runSupport() {
						try {
							int value = paramFakeFullCopy.getValue();
							boolean enabled = (value != 0);
							if (cFullCopyOptionsArea.getEnabled() != enabled) {
								cFullCopyOptionsArea.setEnabled(enabled);
								controlsSetEnabled(cFullCopyOptionsArea.getChildren(), enabled);
							}
						} catch (Exception e) {
						}
					}
				});
			}
		});
    paramFakeFullCopy.getControl().addListener(SWT.Modify, new Listener() {
        @Override
        public void handleEvent(Event event) {
        }
    });

    return cSeeding;
  }

  private void controlsSetEnabled(Control[] controls, boolean bEnabled) {
    for(int i = 0 ; i < controls.length ; i++) {
      if (controls[i] instanceof Composite)
        controlsSetEnabled(((Composite)controls[i]).getChildren(), bEnabled);
      controls[i].setEnabled(bEnabled);
    }
  }
}

