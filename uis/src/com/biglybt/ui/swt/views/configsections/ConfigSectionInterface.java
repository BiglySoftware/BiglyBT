/*
 * File    : ConfigPanel*.java
 * Created : 11 mar. 2004
 * By      : TuxPaper
 *
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

package com.biglybt.ui.swt.views.configsections;

import java.util.ArrayList;
import java.util.HashMap;

import com.biglybt.ui.swt.systray.SystemTraySWT;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.TrackersUtil;
import com.biglybt.pif.platform.PlatformManagerException;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.platform.PlatformManager;
import com.biglybt.platform.PlatformManagerCapabilities;
import com.biglybt.platform.PlatformManagerFactory;
import com.biglybt.ui.common.RememberedDecisionsManager;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.LinkLabel;
import com.biglybt.ui.swt.config.*;
import com.biglybt.ui.swt.pif.UISWTConfigSection;

public class ConfigSectionInterface
	implements UISWTConfigSection
{
	private ParameterListener decisions_parameter_listener;

	@Override
	public String configSectionGetParentSection() {
		return ConfigSection.SECTION_ROOT;
	}

	@Override
	public String configSectionGetName() {
		return ConfigSection.SECTION_INTERFACE;
	}

	@Override
	public void configSectionSave() {
	}

	@Override
	public void configSectionDelete() {

		if (decisions_parameter_listener != null) {

			COConfigurationManager.removeParameterListener(
					"MessageBoxWindow.decisions", decisions_parameter_listener);
		}
	}

	@Override
	public int maxUserMode() {
		return 0;
	}

	@Override
	public Composite configSectionCreate(final Composite parent) {
		GridData gridData;
		GridLayout layout;
		Label label;

		Composite cDisplay = new Composite(parent, SWT.NULL);

		gridData = new GridData(
				GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
		Utils.setLayoutData(cDisplay, gridData);
		layout = new GridLayout();
		layout.numColumns = 1;
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		cDisplay.setLayout(layout);

		final PlatformManager platform = PlatformManagerFactory.getPlatformManager();

		// ***** auto open group

		Group gAutoOpen = new Group(cDisplay, SWT.NULL);
		Messages.setLanguageText(gAutoOpen, "ConfigView.label.autoopen");
		layout = new GridLayout(3, false);
		gAutoOpen.setLayout(layout);
		Utils.setLayoutData(gAutoOpen, new GridData(GridData.FILL_HORIZONTAL));

		label = new Label(gAutoOpen, SWT.NULL);
		Messages.setLanguageText(label, "ConfigView.label.autoopen.detailstab");
		new BooleanParameter(gAutoOpen, "Open Details",
				"ConfigView.label.autoopen.dl");
		new BooleanParameter(gAutoOpen, "Open Seeding Details",
				"ConfigView.label.autoopen.cd");

		label = new Label(gAutoOpen, SWT.NULL);
		Messages.setLanguageText(label, "ConfigView.label.autoopen.downloadbars");
		new BooleanParameter(gAutoOpen, "Open Bar Incomplete",
				"ConfigView.label.autoopen.dl");
		new BooleanParameter(gAutoOpen, "Open Bar Complete",
				"ConfigView.label.autoopen.cd");

		// ****

		if (!Constants.isOSX) {

			new BooleanParameter(cDisplay, "Show Status In Window Title",
					"ConfigView.label.info.in.window.title");
		}

		new BooleanParameter(cDisplay, "Remember transfer bar location",
				"ConfigView.label.transferbar.remember_location");

		Composite gBarTrans = new Composite(cDisplay, SWT.NULL);
		layout = new GridLayout(4, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		gBarTrans.setLayout(layout);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalIndent = 25;
		Utils.setLayoutData(gBarTrans, gridData);

		label = new Label(gBarTrans, SWT.NULL);
		Messages.setLanguageText(label, "label.bar.trans");

		new IntParameter(gBarTrans, "Bar Transparency", 0, 100);

		label = new Label(gBarTrans, SWT.NULL);
		Messages.setLanguageText(label, "label.show.icon.area");

		new BooleanParameter(gBarTrans, "Transfer Bar Show Icon Area");

		{
				// sys tray
			
			Group gSysTray = new Group(cDisplay, SWT.NULL);
			Messages.setLanguageText(gSysTray, "ConfigView.label.systray");
			layout = new GridLayout();
			layout.numColumns = 2;
			gSysTray.setLayout(layout);
			Utils.setLayoutData(gSysTray, new GridData(GridData.FILL_HORIZONTAL));
	
			BooleanParameter est = new BooleanParameter(gSysTray, "Enable System Tray",
					"ConfigView.section.interface.enabletray");
			est.addChangeListener(new ParameterChangeAdapter() {
				@Override
				public void booleanParameterChanging(Parameter p, boolean toValue) {
					if (toValue) {
						SystemTraySWT.getTray();
					} else {
						SystemTraySWT.getTray().dispose();
					}
				}
			});
			
			BooleanParameter stdo = new BooleanParameter(gSysTray, "System Tray Disabled Override",
					"ConfigView.label.closetotrayoverride");
	
			BooleanParameter ctt = new BooleanParameter(gSysTray, "Close To Tray",
					"ConfigView.label.closetotray");
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			gridData.horizontalSpan = 2;
			ctt.setLayoutData( gridData );
			
			BooleanParameter mtt = new BooleanParameter(gSysTray, "Minimize To Tray",
					"ConfigView.label.minimizetotray");
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			gridData.horizontalSpan = 2;
			mtt.setLayoutData( gridData );
			
			BooleanParameter esttt = new BooleanParameter(gSysTray,
					"ui.systray.tooltip.enable", "ConfigView.label.enableSystrayToolTip");
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			gridData.horizontalSpan = 2;
			esttt.setLayoutData( gridData );
			
			BooleanParameter estttd = new BooleanParameter(gSysTray,
					"ui.systray.tooltip.next.eta.enable",
					"ConfigView.label.enableSystrayToolTipNextETA");
			
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			gridData.horizontalSpan = 2;
			gridData.horizontalIndent = 25;
			estttd.setLayoutData(gridData);
			
			est.setAdditionalActionPerformer(
					new ChangeSelectionActionPerformer(stdo.getControls(), true));
			
			
			IAdditionalActionPerformer st_enabler = new GenericActionPerformer(
					new Control[] {}) {
				@Override
				public void performAction() {
					boolean st_enabled 	= est.isSelected();
					boolean override 	= stdo.isSelected();
					boolean dl_stats	= esttt.isSelected();
					
					ctt.setEnabled(st_enabled || override );
					mtt.setEnabled( st_enabled || override );
					esttt.setEnabled(st_enabled);
					estttd.setEnabled(st_enabled && dl_stats );
				}
			};
			
			est.setAdditionalActionPerformer( st_enabler );
			stdo.setAdditionalActionPerformer( st_enabler );
			esttt.setAdditionalActionPerformer( st_enabler );
		}
		
		/**
		 * Default download / upload limits available in the UI.
		 */
		Group limit_group = new Group(cDisplay, SWT.NULL);
		Messages.setLanguageText(limit_group,
				"ConfigView.label.set_ui_transfer_speeds");
		layout = new GridLayout();
		layout.numColumns = 2;
		limit_group.setLayout(layout);
		Utils.setLayoutData(limit_group, new GridData(GridData.FILL_HORIZONTAL));

		Label limit_group_label = new Label(limit_group, SWT.WRAP);
		Utils.setLayoutData(limit_group_label,
				Utils.getWrappableLabelGridData(2, GridData.GRAB_HORIZONTAL));
		Messages.setLanguageText(limit_group_label,
				"ConfigView.label.set_ui_transfer_speeds.description");

		String[] limit_types = new String[] {
			"download",
			"upload"
		};
		final String limit_type_prefix = "config.ui.speed.partitions.manual.";
		for (int i = 0; i < limit_types.length; i++) {
			final BooleanParameter bp = new BooleanParameter(limit_group,
					limit_type_prefix + limit_types[i] + ".enabled", false,
					"ConfigView.label.set_ui_transfer_speeds.description."
							+ limit_types[i]);
			final StringParameter sp = new StringParameter(limit_group,
					limit_type_prefix + limit_types[i] + ".values", "");
			IAdditionalActionPerformer iaap = new GenericActionPerformer(
					new Control[] {}) {
				@Override
				public void performAction() {
					sp.getControl().setEnabled(bp.isSelected());
				}
			};

			gridData = new GridData();
			gridData.widthHint = 150;
			sp.setLayoutData(gridData);
			iaap.performAction();
			bp.setAdditionalActionPerformer(iaap);
		}

		// send version

		new BooleanParameter(cDisplay, "Send Version Info",
				"ConfigView.label.allowSendVersion");

		Composite cArea = new Composite(cDisplay, SWT.NULL);
		layout = new GridLayout();
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		layout.numColumns = 2;
		cArea.setLayout(layout);
		Utils.setLayoutData(cArea, new GridData(GridData.FILL_HORIZONTAL));

		new LinkLabel(cArea, "ConfigView.label.version.info.link",
				Constants.URL_WIKI + "w/Version.azureusplatform.com");

		if (!Constants.isOSX) {

			BooleanParameter confirm = new BooleanParameter(cArea,
					"confirmationOnExit", "ConfigView.section.style.confirmationOnExit");
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			confirm.setLayoutData(gridData);
		}

		cArea = new Composite(cDisplay, SWT.NULL);
		layout = new GridLayout();
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		layout.numColumns = 2;
		cArea.setLayout(layout);
		Utils.setLayoutData(cArea, new GridData(GridData.FILL_HORIZONTAL));

		// clear remembered decisions

		final Label clear_label = new Label(cArea, SWT.NULL);
		Messages.setLanguageText(clear_label,
				"ConfigView.section.interface.cleardecisions");

		final Button clear_decisions = new Button(cArea, SWT.PUSH);
		Messages.setLanguageText(clear_decisions,
				"ConfigView.section.interface.cleardecisionsbutton");

		clear_decisions.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {

				RememberedDecisionsManager.clearAll();
			}
		});

		final Label clear_tracker_label = new Label(cArea, SWT.NULL);
		Messages.setLanguageText(clear_tracker_label,
				"ConfigView.section.interface.cleartrackers");

		final Button clear_tracker_button = new Button(cArea, SWT.PUSH);
		Messages.setLanguageText(clear_tracker_button,
				"ConfigView.section.interface.cleartrackersbutton");

		clear_tracker_button.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				TrackersUtil.getInstance().clearAllTrackers(true);
			}
		});

		final Label clear_save_path_label = new Label(cArea, SWT.NULL);
		Messages.setLanguageText(clear_save_path_label,
				"ConfigView.section.interface.clearsavepaths");

		final Button clear_save_path_button = new Button(cArea, SWT.PUSH);
		Messages.setLanguageText(clear_save_path_button,
				"ConfigView.section.interface.clearsavepathsbutton");

		clear_save_path_button.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				COConfigurationManager.setParameter("saveTo_list",
						new ArrayList());
			}
		});

		decisions_parameter_listener = new ParameterListener() {
			@Override
			public void parameterChanged(String parameterName) {
				if (clear_decisions.isDisposed()) {

					// tidy up from previous incarnations

					COConfigurationManager.removeParameterListener(
							"MessageBoxWindow.decisions", this);

				} else {

					boolean enabled = COConfigurationManager.getMapParameter(
							"MessageBoxWindow.decisions", new HashMap()).size() > 0;

					clear_label.setEnabled(enabled);
					clear_decisions.setEnabled(enabled);
				}
			}
		};

		decisions_parameter_listener.parameterChanged(null);

		COConfigurationManager.addParameterListener("MessageBoxWindow.decisions",
				decisions_parameter_listener);

		// reset associations

		if (platform.hasCapability(
				PlatformManagerCapabilities.RegisterFileAssociations)) {

			Composite cResetAssoc = new Composite(cArea, SWT.NULL);
			layout = new GridLayout();
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			layout.numColumns = 2;
			cResetAssoc.setLayout(layout);
			Utils.setLayoutData(cResetAssoc, new GridData());

			label = new Label(cResetAssoc, SWT.NULL);
			Messages.setLanguageText(label,
					"ConfigView.section.interface.resetassoc");

			Button reset = new Button(cResetAssoc, SWT.PUSH);
			Messages.setLanguageText(reset,
					"ConfigView.section.interface.resetassocbutton"); //$NON-NLS-1$

			reset.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {

					try {
						platform.registerApplication();

					} catch (PlatformManagerException e) {

						Logger.log(new LogAlert(LogAlert.UNREPEATABLE,
								"Failed to register application", e));
					}
				}
			});

			new BooleanParameter(cArea, "config.interface.checkassoc",
					"ConfigView.section.interface.checkassoc");

		}

		return cDisplay;
	}

}
