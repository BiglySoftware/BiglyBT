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

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.config.*;
import com.biglybt.ui.swt.pif.UISWTConfigSection;

import com.biglybt.ui.swt.imageloader.ImageLoader;

import com.biglybt.pif.ui.config.ConfigSection;

/** General Queueing options
 * @author TuxPaper
 * @created Jan 12, 2004
 */
public class ConfigSectionQueue implements UISWTConfigSection
{
	@Override
	public String configSectionGetParentSection() {
		return ConfigSection.SECTION_ROOT;
	}

	@Override
	public int maxUserMode() {
		return 0;
	}

	/**
	 * Create the "Queue" Tab in the Configuration view
	 */
	@Override
	public Composite configSectionCreate(Composite parent) {
		GridData gridData;
		GridLayout layout;
		Label label;

		// main tab set up

		Composite cSection = new Composite(parent, SWT.NULL);

		gridData = new GridData(GridData.VERTICAL_ALIGN_FILL
				| GridData.HORIZONTAL_ALIGN_FILL);
		Utils.setLayoutData(cSection, gridData);
		layout = new GridLayout();
		layout.numColumns = 2;
		layout.marginHeight = 0;
		cSection.setLayout(layout);

		// row

		label = new Label(cSection, SWT.NULL);
		Messages.setLanguageText(label, "ConfigView.label.maxdownloads");
		gridData = new GridData();
		final IntParameter maxDLs = new IntParameter(cSection, "max downloads", 0, Integer.MAX_VALUE );
		maxDLs.setLayoutData(gridData);

			// subrow - ignore checking downloads
		final Composite cMaxDownloads = new Composite(cSection, SWT.NULL);
		layout = new GridLayout();
		layout.numColumns = 3;
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		cMaxDownloads.setLayout(layout);
		gridData = new GridData();
		gridData.horizontalIndent = 15;
		gridData.horizontalSpan = 2;
		Utils.setLayoutData(cMaxDownloads, gridData);

		ImageLoader imageLoader = ImageLoader.getInstance();
		label = new Label(cMaxDownloads, SWT.NULL);
		imageLoader.setLabelImage(label, "subitem");
		gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
		Utils.setLayoutData(label, gridData);

		label = new Label(cMaxDownloads, SWT.NULL);
		Messages.setLanguageText(label, "ConfigView.label.ignoreChecking");

		gridData = new GridData();
		BooleanParameter ignoreChecking = new BooleanParameter(
				cMaxDownloads, "StartStopManager_bMaxDownloadIgnoreChecking");

		ignoreChecking.setLayoutData(gridData);

		// row

		label = new Label(cSection, SWT.NULL);
		Messages.setLanguageText(label, "ConfigView.label.maxactivetorrents");
		gridData = new GridData();
		final IntParameter maxActiv = new IntParameter(cSection,
				"max active torrents", 0, Integer.MAX_VALUE);
		maxActiv.setLayoutData(gridData);

		final Composite cMaxActiveOptionsArea = new Composite(cSection, SWT.NULL);
		layout = new GridLayout();
		layout.numColumns = 3;
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		cMaxActiveOptionsArea.setLayout(layout);
		gridData = new GridData();
		gridData.horizontalIndent = 15;
		gridData.horizontalSpan = 2;
		Utils.setLayoutData(cMaxActiveOptionsArea, gridData);

		label = new Label(cMaxActiveOptionsArea, SWT.NULL);
		imageLoader.setLabelImage(label, "subitem");
		gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
		Utils.setLayoutData(label, gridData);

		gridData = new GridData();
		BooleanParameter maxActiveWhenSeedingEnabled = new BooleanParameter(
				cMaxActiveOptionsArea,
				"StartStopManager_bMaxActiveTorrentsWhenSeedingEnabled",
				"ConfigView.label.queue.maxactivetorrentswhenseeding");
		maxActiveWhenSeedingEnabled.setLayoutData(gridData);

		gridData = new GridData();

		final IntParameter maxActivWhenSeeding = new IntParameter(
				cMaxActiveOptionsArea, "StartStopManager_iMaxActiveTorrentsWhenSeeding", 0, Integer.MAX_VALUE);
		maxActivWhenSeeding.setLayoutData(gridData);

		// row

		label = new Label(cSection, SWT.NULL);
		Messages.setLanguageText(label, "ConfigView.label.mindownloads");
		gridData = new GridData();
		final IntParameter minDLs = new IntParameter(cSection, "min downloads", 0, Integer.MAX_VALUE);
		minDLs.setLayoutData(gridData);
		minDLs.setMaximumValue(maxDLs.getValue() / 2);

		// change controllers for above items

		maxActiveWhenSeedingEnabled.setAdditionalActionPerformer(new ChangeSelectionActionPerformer(
				maxActivWhenSeeding));

		maxDLs.addChangeListener(new ParameterChangeAdapter() {
			@Override
			public void parameterChanged(Parameter p, boolean caused_internally) {
				int iMaxDLs = maxDLs.getValue();
				minDLs.setMaximumValue(iMaxDLs / 2);

				int iMinDLs = minDLs.getValue();
				int iMaxActive = maxActiv.getValue();

				if ((iMaxDLs == 0 || iMaxDLs > iMaxActive) && iMaxActive != 0) {
					maxActiv.setValue(iMaxDLs);
				}
			}
		});

		maxActiv.addChangeListener(new ParameterChangeAdapter() {
			@Override
			public void parameterChanged(Parameter p, boolean caused_internally) {
				int iMaxDLs = maxDLs.getValue();
				int iMaxActive = maxActiv.getValue();

				if ((iMaxDLs == 0 || iMaxDLs > iMaxActive) && iMaxActive != 0) {
					maxDLs.setValue(iMaxActive);
				}
			}
		});


		// row

		final ArrayList values = new ArrayList();
		int exp = 29;
		for(int val = 0; val <= 8*1024*1024;)
		{
			values.add(new Integer(val));
			if(val < 256)
				val+=64;
			else if(val < 1024)
				val+=256;
			else if(val < 16*1024)
				val+=1024;
			else
				val = (int)(Math.pow(2, exp++/2) + (exp % 2 == 0 ? Math.pow(2,  (exp-3)/2) : 0));
		}
		String[] activeDLLabels = new String[values.size()];
		int[] activeDLValues = new int[activeDLLabels.length];


		label = new Label(cSection, SWT.NULL);
		Messages.setLanguageText(label, "ConfigView.label.minSpeedForActiveDL");
		for(int i=0;i<activeDLLabels.length;i++)
		{
			activeDLValues[i] = ((Integer)values.get(i)).intValue();
			activeDLLabels[i] = DisplayFormatters.formatByteCountToKiBEtcPerSec(
				activeDLValues[i], true);

		}
		new IntListParameter(cSection, "StartStopManager_iMinSpeedForActiveDL",
				activeDLLabels, activeDLValues);

		// row

		label = new Label(cSection, SWT.NULL);
		Messages.setLanguageText(label, "ConfigView.label.minSpeedForActiveSeeding");
		String[] activeSeedingLabels = new String[values.size()-4];
		int[] activeSeedingValues = new int[activeSeedingLabels.length];
		System.arraycopy(activeDLLabels, 0, activeSeedingLabels, 0, activeSeedingLabels.length);
		System.arraycopy(activeDLValues, 0, activeSeedingValues, 0, activeSeedingValues.length);

		new IntListParameter(cSection,
				"StartStopManager_iMinSpeedForActiveSeeding", activeSeedingLabels,
				activeSeedingValues);

		// subrow

		final Composite cMinSpeedActiveCDing = new Composite(cSection, SWT.NULL);
		layout = new GridLayout();
		layout.numColumns = 3;
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		cMinSpeedActiveCDing.setLayout(layout);
		gridData = new GridData();
		gridData.horizontalIndent = 15;
		gridData.horizontalSpan = 2;
		Utils.setLayoutData(cMinSpeedActiveCDing, gridData);

		label = new Label(cMinSpeedActiveCDing, SWT.NULL);
		imageLoader.setLabelImage(label, "subitem");
		gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
		Utils.setLayoutData(label, gridData);

		label = new Label(cMinSpeedActiveCDing, SWT.NULL);
		Messages.setLanguageText(label, "ConfigView.label.maxStalledSeeding");

		gridData = new GridData();
		final IntParameter maxStalledSeeding = new IntParameter(
				cMinSpeedActiveCDing, "StartStopManager_iMaxStalledSeeding", 0, Integer.MAX_VALUE);
		maxStalledSeeding.setMinimumValue(0);
		maxStalledSeeding.setLayoutData(gridData);

		label = new Label(cMinSpeedActiveCDing, SWT.NULL);
		imageLoader.setLabelImage(label, "subitem");
		gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
		Utils.setLayoutData(label, gridData);

		gridData = new GridData();
		gridData.horizontalSpan = 2;
		new BooleanParameter(cMinSpeedActiveCDing, "StartStopManager_bMaxStalledSeedingIgnoreZP",
				"ConfigView.label.maxStalledSeedingIgnoreZP").setLayoutData(gridData);


		// row

		gridData = new GridData();
		gridData.horizontalSpan = 2;
		new BooleanParameter(cSection, "StartStopManager_bStopOnceBandwidthMet",
				"ConfigView.label.queue.stoponcebandwidthmet").setLayoutData(gridData);

		// row

		gridData = new GridData();
		gridData.horizontalSpan = 2;
		new BooleanParameter(cSection, "StartStopManager_bNewSeedsMoveTop",
				"ConfigView.label.queue.newseedsmovetop").setLayoutData(gridData);

		// row

		gridData = new GridData();
		gridData.horizontalSpan = 2;
		new BooleanParameter(cSection, "StartStopManager_bRetainForceStartWhenComplete",
				"ConfigView.label.queue.retainforce").setLayoutData(gridData);

		// row

		gridData = new GridData();
		gridData.horizontalSpan = 2;
		new BooleanParameter(cSection, "Alert on close",
				"ConfigView.label.showpopuponclose").setLayoutData(gridData);

		//row

		gridData = new GridData();
		gridData.horizontalSpan = 2;
		new BooleanParameter(cSection, "StartStopManager_bDebugLog",
				"ConfigView.label.queue.debuglog").setLayoutData(gridData);

		return cSection;
	}

	@Override
	public String configSectionGetName() {
		return "queue";
	}

	@Override
	public void configSectionSave() {
	}

	@Override
	public void configSectionDelete() {
	}
}
