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
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;

import com.biglybt.core.internat.MessageText;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.config.BooleanParameter;
import com.biglybt.ui.swt.config.IntListParameter;
import com.biglybt.ui.swt.pif.UISWTConfigSection;

import com.biglybt.plugin.startstoprules.defaultplugin.DefaultRankCalculator;

/** First Priority Specific options.
 * @author TuxPaper
 * @created Jan 12, 2004
 */
public class ConfigSectionSeedingFirstPriority
	implements UISWTConfigSection
{
	@Override
	public String configSectionGetParentSection() {
		return "queue.seeding";
	}

	@Override
	public String configSectionGetName() {
		return "queue.seeding.firstPriority";
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
    Composite cArea, cArea1;

    Composite cFirstPriorityArea = new Composite(parent, SWT.NULL);

    layout = new GridLayout();
    layout.numColumns = 2;
    layout.marginHeight = 0;
    cFirstPriorityArea.setLayout(layout);
    gridData = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
    Utils.setLayoutData(cFirstPriorityArea, gridData);


    label = new Label(cFirstPriorityArea, SWT.WRAP);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 2;
    gridData.widthHint = 300;
    Utils.setLayoutData(label, gridData);
    Messages.setLanguageText(label, "ConfigView.label.seeding.firstPriority.info");

    // ** Begin No Touch area

// Group FP

	Composite cFP = new Group(cFirstPriorityArea, SWT.NULL);
    layout = new GridLayout();
    layout.numColumns = 2;
    layout.verticalSpacing = 6;
    cFP.setLayout(layout);
    gridData = new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
    Utils.setLayoutData(cFP, gridData);
	Messages.setLanguageText(cFP, "ConfigView.label.seeding.firstPriority.FP");

	// row
	cArea = new Composite(cFP, SWT.NULL);
    layout = new GridLayout();
    layout.marginHeight = 0;
    layout.marginWidth = 0;
    layout.numColumns = 3;
    cArea.setLayout(layout);
	gridData = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
    gridData.horizontalSpan = 3;
    Utils.setLayoutData(cArea, gridData);
	label = new Label(cArea, SWT.NULL);
	Messages.setLanguageText(label, "ConfigView.label.seeding.firstPriority");
	String fpLabels[] = { MessageText.getString("ConfigView.text.all"),
			MessageText.getString("ConfigView.text.any") };
	int fpValues[] = { DefaultRankCalculator.FIRSTPRIORITY_ALL,
			DefaultRankCalculator.FIRSTPRIORITY_ANY };
	new IntListParameter(cArea, "StartStopManager_iFirstPriority_Type",
			fpLabels, fpValues);
	label = new Label(cArea, SWT.NULL);
	Messages.setLanguageText(label, "ConfigView.label.seeding.firstPriority.following");

    // row
    label = new Label(cFP, SWT.NULL);
    Messages.setLanguageText(label, "ConfigView.label.seeding.firstPriority.shareRatio");
    String minQueueLabels[] = new String[55];
    int minQueueValues[] = new int[55];
    minQueueLabels[0] = "1:2 (" + 0.5 + ")";
    minQueueValues[0] = 500;
	minQueueLabels[1] = "3:4 (" + 0.75 +")";
	minQueueValues[1] = 750;
	minQueueLabels[2] = "1:1";
	minQueueValues[2] = 1000;
	minQueueLabels[3] = "5:4 (" + 1.25 +")";
	minQueueValues[3] = 1250;
	minQueueLabels[4] = "3:2 (" + 1.50 +")";
	minQueueValues[4] = 1500;
	minQueueLabels[5] = "7:4 (" + 1.75 +")";
	minQueueValues[5] = 1750;
    for (int i = 6; i < minQueueLabels.length; i++) {
      minQueueLabels[i] = i - 4 + ":1";
      minQueueValues[i] = (i - 4) * 1000;
    }
    new IntListParameter(cFP, "StartStopManager_iFirstPriority_ShareRatio",
                         minQueueLabels, minQueueValues);

	String sMinutes = MessageText.getString("ConfigView.text.minutes");
    String sHours = MessageText.getString("ConfigView.text.hours");

    // row
    label = new Label(cFP, SWT.NULL);
    Messages.setLanguageText(label, "ConfigView.label.seeding.firstPriority.DLMinutes");

    String dlTimeLabels[] = new String[15];
    int dlTimeValues[] = new int[15];
    dlTimeLabels[0] = MessageText.getString("ConfigView.text.ignore");
    dlTimeValues[0] = 0;
    for (int i = 1; i < dlTimeValues.length; i++) {
      dlTimeLabels[i] = "<= " + (i + 2) + " " + sHours ;
      dlTimeValues[i] = (i + 2) * 60;
    }
    new IntListParameter(cFP, "StartStopManager_iFirstPriority_DLMinutes",
                         dlTimeLabels, dlTimeValues);

	label = new Label(cFirstPriorityArea, SWT.WRAP);

    // row
    label = new Label(cFP, SWT.NULL);
    Messages.setLanguageText(label, "ConfigView.label.seeding.firstPriority.seedingMinutes");

    String seedTimeLabels[] = new String[15];
    int seedTimeValues[] = new int[15];
    seedTimeLabels[0] = MessageText.getString("ConfigView.text.ignore");
    seedTimeValues[0] = 0;
    seedTimeLabels[1] = "<= 90 " + sMinutes;
    seedTimeValues[1] = 90;
    for (int i = 2; i < seedTimeValues.length; i++) {
      seedTimeLabels[i] = "<= " + i + " " + sHours ;
      seedTimeValues[i] = i * 60;
    }
    new IntListParameter(cFP, "StartStopManager_iFirstPriority_SeedingMinutes",
                         seedTimeLabels, seedTimeValues);



//	 Group Ignore FP

    Composite cIgnoreFP = new Group(cFirstPriorityArea, SWT.NULL);
    layout = new GridLayout();
    layout.numColumns = 2;
    layout.verticalSpacing = 6;
	cIgnoreFP.setLayout(layout);
    gridData = new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.VERTICAL_ALIGN_FILL);
	Utils.setLayoutData(cIgnoreFP, gridData);
    Messages.setLanguageText(cIgnoreFP, "ConfigView.label.seeding.firstPriority.ignore");

	// Ignore S:P Ratio
	label = new Label(cIgnoreFP, SWT.NULL);
	Messages.setLanguageText(label, "ConfigView.label.seeding.firstPriority.ignoreSPRatio");
	String ignoreSPRatioLabels[] = new String[15];
    int ignoreSPRatioValues[] = new int[15];
	ignoreSPRatioLabels[0] = MessageText.getString("ConfigView.text.ignore");
	ignoreSPRatioValues[0] = 0;
    for (int i = 1; i < ignoreSPRatioLabels.length; i++) {
		ignoreSPRatioLabels[i] = i * 10 + " " + ":1" ;
		ignoreSPRatioValues[i] = i * 10;
    }
	new IntListParameter(cIgnoreFP, "StartStopManager_iFirstPriority_ignoreSPRatio", 0,
							ignoreSPRatioLabels, ignoreSPRatioValues);

	//	 Ignore 0 Peers
    new BooleanParameter(cIgnoreFP,
                         "StartStopManager_bFirstPriority_ignore0Peer",
                         "ConfigView.label.seeding.firstPriority.ignore0Peer");

		label = new Label(cIgnoreFP, SWT.NULL);

    // Ignore idle hours
		label = new Label(cIgnoreFP, SWT.NULL);
		Messages.setLanguageText(label,
				"ConfigView.label.seeding.firstPriority.ignoreIdleHours");
		int[] availIdleHours = { 2, 3, 4, 5, 6, 7, 8, 12, 18, 24, 48, 72, 168 };
		String ignoreIdleHoursLabels[] = new String[availIdleHours.length + 1];
		int ignoreIdleHoursValues[] = new int[availIdleHours.length + 1];
		ignoreIdleHoursLabels[0] = MessageText.getString("ConfigView.text.ignore");
		ignoreIdleHoursValues[0] = 0;
		for (int i = 0; i < availIdleHours.length; i++) {
			ignoreIdleHoursLabels[i + 1] = availIdleHours[i] + " " + sHours;
			ignoreIdleHoursValues[i + 1] = availIdleHours[i];
		}
		new IntListParameter(cIgnoreFP,
				"StartStopManager_iFirstPriority_ignoreIdleHours", 0,
				ignoreIdleHoursLabels, ignoreIdleHoursValues);

	//	 row
	cArea1 = new Composite(cIgnoreFP, SWT.NULL);
    layout = new GridLayout();
    layout.marginHeight = 0;
    layout.marginWidth = 0;
    layout.numColumns = 2;
    cArea1.setLayout(layout);
	gridData = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
    gridData.horizontalSpan = 2;
    Utils.setLayoutData(cArea1, gridData);
	label = new Label(cArea1, SWT.NULL);
	Messages.setLanguageText(label, "ConfigView.label.seeding.firstPriority.ignore.info");


    return cFirstPriorityArea;
  }
}
