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

import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.config.*;
import com.biglybt.ui.swt.pif.UISWTConfigSection;

import com.biglybt.ui.swt.imageloader.ImageLoader;

/** Config Section for items that make us ignore torrents when seeding
 * @author TuxPaper
 * @created Jan 12, 2004
 */
public class ConfigSectionSeedingIgnore implements UISWTConfigSection {
  @Override
  public String configSectionGetParentSection() {
    return "queue.seeding";
  }

  @Override
  public String configSectionGetName() {
    return "queue.seeding.ignore";
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

    Composite cIgnoreRules = new Composite(parent, SWT.NULL);

    layout = new GridLayout();
    layout.numColumns = 3;
    layout.marginHeight = 0;
    cIgnoreRules.setLayout(layout);

    label = new Label(cIgnoreRules, SWT.WRAP);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 3;
    gridData.widthHint = 300;
    Utils.setLayoutData(label, gridData);
    Messages.setLanguageText(label, "ConfigView.label.autoSeedingIgnoreInfo"); //$NON-NLS-1$

	Composite cIgnore = new Group(cIgnoreRules, SWT.NULL);
    layout = new GridLayout();
    layout.numColumns = 3;
    layout.verticalSpacing = 6;
	cIgnore.setLayout(layout);
    gridData = new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
	Utils.setLayoutData(cIgnore, gridData);
	Messages.setLanguageText(cIgnore, "ConfigView.label.seeding.ignore");


    label = new Label(cIgnore, SWT.NULL);
    Messages.setLanguageText(label, "ConfigView.label.ignoreSeeds"); //$NON-NLS-1$
    gridData = new GridData();
    new IntParameter(cIgnore, "StartStopManager_iIgnoreSeedCount", 0, 9999).setLayoutData(gridData);
    label = new Label(cIgnore, SWT.NULL);
    Messages.setLanguageText(label, "ConfigView.label.seeds");

    label = new Label(cIgnore, SWT.WRAP);
    Messages.setLanguageText(label, "ConfigView.label.seeding.ignoreRatioPeers"); //$NON-NLS-1$
    gridData = new GridData();
    new IntParameter(cIgnore, "Stop Peers Ratio", 0, 9999).setLayoutData(gridData);
    label = new Label(cIgnore, SWT.NULL);
    Messages.setLanguageText(label, "ConfigView.label.peers");

    Composite cArea = new Composite(cIgnore, SWT.NULL);
    layout = new GridLayout();
    layout.numColumns = 4;
    layout.marginWidth = 0;
    layout.marginHeight = 0;
    cArea.setLayout(layout);
    gridData = new GridData();
    gridData.horizontalIndent = 15;
    gridData.horizontalSpan = 3;
    Utils.setLayoutData(cArea, gridData);

		label = new Label(cArea, SWT.NULL);
		ImageLoader.getInstance().setLabelImage(label, "subitem");
    gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
    Utils.setLayoutData(label, gridData);

    label = new Label(cArea, SWT.NULL);
    Messages.setLanguageText(label, "ConfigView.label.seeding.fakeFullCopySeedStart");

    gridData = new GridData();
    new IntParameter(cArea, "StartStopManager_iIgnoreRatioPeersSeedStart", 0, 9999).setLayoutData(gridData);
    label = new Label(cArea, SWT.NULL);
    Messages.setLanguageText(label, "ConfigView.label.seeds");

    // Share Ratio
    label = new Label(cIgnore, SWT.NULL);
    Messages.setLanguageText(label, "ConfigView.label.seeding.ignoreShareRatio");
    gridData = new GridData();
    gridData.widthHint = 50;
    new FloatParameter(cIgnore, "Stop Ratio", 1, -1, true, 1).setLayoutData(gridData);
    label = new Label(cIgnore, SWT.NULL);
    label.setText(":1");

    cArea = new Composite(cIgnore, SWT.NULL);
    layout = new GridLayout();
    layout.numColumns = 4;
    layout.marginWidth = 0;
    layout.marginHeight = 0;
    cArea.setLayout(layout);
    gridData = new GridData();
    gridData.horizontalIndent = 15;
    gridData.horizontalSpan = 3;
    Utils.setLayoutData(cArea, gridData);

    label = new Label(cArea, SWT.NULL);
		ImageLoader.getInstance().setLabelImage(label, "subitem");
    gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
    Utils.setLayoutData(label, gridData);

    label = new Label(cArea, SWT.NULL);
    Messages.setLanguageText(label, "ConfigView.label.seeding.fakeFullCopySeedStart");

    gridData = new GridData();
    new IntParameter(cArea, "StartStopManager_iIgnoreShareRatioSeedStart", 0, 9999).setLayoutData(gridData);
    label = new Label(cArea, SWT.NULL);
    Messages.setLanguageText(label, "ConfigView.label.seeds");

    // Ignore 0 Peers
    gridData = new GridData();
    gridData.horizontalSpan = 3;
    new BooleanParameter(cIgnore,
                         "StartStopManager_bIgnore0Peers",
                         "ConfigView.label.seeding.ignore0Peers").setLayoutData(gridData);

    return cIgnoreRules;
  }
}

