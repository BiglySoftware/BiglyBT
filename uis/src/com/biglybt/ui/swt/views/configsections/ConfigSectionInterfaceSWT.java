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
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.ui.swt.views.configsections;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.TrackersUtil;
import com.biglybt.core.util.Wiki;
import com.biglybt.pifimpl.local.ui.config.*;
import com.biglybt.platform.PlatformManager;
import com.biglybt.platform.PlatformManagerCapabilities;
import com.biglybt.platform.PlatformManagerFactory;
import com.biglybt.ui.common.RememberedDecisionsManager;
import com.biglybt.ui.config.ConfigSectionImpl;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.systray.SystemTraySWT;

import com.biglybt.pif.platform.PlatformManagerException;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.config.Parameter;

public class ConfigSectionInterfaceSWT
	extends ConfigSectionImpl
{
	public static final String REFID_INTERFACE_SYSTRAY = "interface-systyray";
	
	private ParameterListener decisions_parameter_listener;

	public ConfigSectionInterfaceSWT() {
		super(ConfigSection.SECTION_INTERFACE, ConfigSection.SECTION_ROOT);
	}

	@Override
	public void deleteConfigSection() {
		super.deleteConfigSection();

		if (decisions_parameter_listener != null) {

			COConfigurationManager.removeParameterListener(
					"MessageBoxWindow.decisions", decisions_parameter_listener);
		}
	}

	@Override
	public void build() {
		setDefaultUITypesForAdd(UIInstance.UIT_SWT);

		final PlatformManager platform = PlatformManagerFactory.getPlatformManager();

		// ***** auto open group

		List<Parameter> listAutoOpen = new ArrayList<>();

		add(new LabelParameterImpl("ConfigView.label.autoopen.detailstab"),
				listAutoOpen);
		add(new BooleanParameterImpl("Open Details",
				"ConfigView.label.autoopen.dl"), listAutoOpen);
		add(new BooleanParameterImpl("Open Seeding Details",
				"ConfigView.label.autoopen.cd"), listAutoOpen);

		add(new LabelParameterImpl("ConfigView.label.autoopen.downloadbars"),
				listAutoOpen);
		add(new BooleanParameterImpl("Open Bar Incomplete",
				"ConfigView.label.autoopen.dl"), listAutoOpen);
		add(new BooleanParameterImpl("Open Bar Complete",
				"ConfigView.label.autoopen.cd"), listAutoOpen);

		add("if.pgAutoOpen", new ParameterGroupImpl("ConfigView.label.autoopen",
				listAutoOpen).setNumberOfColumns2(3));

		// ****

		if (!Constants.isOSX) {

			BooleanParameterImpl paramShowTitleStatus = new BooleanParameterImpl(
					"Show Status In Window Title",
					"ConfigView.label.info.in.window.title");
			add(paramShowTitleStatus);

			add("if.pgTitle", new ParameterGroupImpl("ConfigView.label.titlebar",
					paramShowTitleStatus));
		}

		// ****

		List<Parameter> listTransferBar = new ArrayList<>();

		add(new BooleanParameterImpl("Remember transfer bar location",
				"ConfigView.label.transferbar.remember_location"), listTransferBar);

		add(new IntParameterImpl("Bar Transparency", "label.bar.trans", 0, 100),
				listTransferBar);

		add(new BooleanParameterImpl("Transfer Bar Show Icon Area",
				"label.show.icon.area"), listTransferBar);

		add("if.TransferBar", new ParameterGroupImpl(
				"MainWindow.menu.view.open_global_transfer_bar", listTransferBar));

		// **** sys tray

		List<Parameter> listSysTray = new ArrayList<>();

		BooleanParameterImpl est = new BooleanParameterImpl("Enable System Tray",
				"ConfigView.section.interface.enabletray");
		add(est, listSysTray);
		est.addListener(p -> Utils.execSWTThread(() -> {
			boolean hasTray = SystemTraySWT.hasTray();
			boolean enable = est.getValue();
			if (enable && !hasTray) {
				SystemTraySWT.getTray();
			} else if (!enable && hasTray) {
				SystemTraySWT.getTray().dispose();
			}
		}));

		BooleanParameterImpl stdo = new BooleanParameterImpl(
				"System Tray Disabled Override",
				"ConfigView.label.closetotrayoverride");
		add(stdo, listSysTray);

		BooleanParameterImpl ctt = new BooleanParameterImpl("Close To Tray",
				"ConfigView.label.closetotray");
		add(ctt, listSysTray);

		BooleanParameterImpl mtt = new BooleanParameterImpl("Minimize To Tray",
				"ConfigView.label.minimizetotray");
		add(mtt, listSysTray);

		BooleanParameterImpl esttt = new BooleanParameterImpl(
				"ui.systray.tooltip.enable", "ConfigView.label.enableSystrayToolTip");
		add(esttt, listSysTray);

		BooleanParameterImpl estttd = new BooleanParameterImpl(
				"ui.systray.tooltip.next.eta.enable",
				"ConfigView.label.enableSystrayToolTipNextETA");
		add(estttd, listSysTray);
		estttd.setIndent(1, true);

		est.addDisabledOnSelection(stdo);

		com.biglybt.pif.ui.config.ParameterListener st_enabler = param -> {
			boolean st_enabled = est.getValue();
			boolean override = stdo.getValue();
			boolean dl_stats = esttt.getValue();

			ctt.setEnabled(st_enabled || override);
			mtt.setEnabled(st_enabled || override);
			esttt.setEnabled(st_enabled);
			estttd.setEnabled(st_enabled && dl_stats);
		};

		est.addListener(st_enabler);
		stdo.addListener(st_enabler);
		esttt.addListener(st_enabler);
		st_enabler.parameterChanged(null);

		ParameterGroupImpl sysTrayPG = new ParameterGroupImpl("ConfigView.label.systray", listSysTray);
		
		add("if.SysTray", sysTrayPG );
		
		sysTrayPG.setReferenceID(REFID_INTERFACE_SYSTRAY);
		
		
		// ****	Default download / upload limits available in the UI.

		LabelParameterImpl paramSpeedOptionsLabel = new LabelParameterImpl(
				"ConfigView.label.set_ui_transfer_speeds.description");
		add(paramSpeedOptionsLabel);

		List<Parameter> listSpeedOptions = new ArrayList<>();

		String[] limit_types = new String[] {
			"download",
			"upload"
		};
		String limit_type_prefix = "config.ui.speed.partitions.manual.";
		for (String type : limit_types) {
			BooleanParameterImpl bp = new BooleanParameterImpl(
					limit_type_prefix + type + ".enabled",
					"ConfigView.label.set_ui_transfer_speeds.description." + type);
			add(bp, listSpeedOptions);

			StringParameterImpl sp = new StringParameterImpl(
					limit_type_prefix + type + ".values", null);
			add(sp, listSpeedOptions);

			bp.addEnabledOnSelection(sp);
		}

		ParameterGroupImpl pgSpeedOptions2Col = new ParameterGroupImpl(null,
				listSpeedOptions).setNumberOfColumns2(2);
		add("if.pgSpeedOptions2Col", pgSpeedOptions2Col);

		ParameterGroupImpl pgSpeedOptions = new ParameterGroupImpl(
				"ConfigView.label.set_ui_transfer_speeds", paramSpeedOptionsLabel,
				pgSpeedOptions2Col);
		add("if.pgSpeedOptions", pgSpeedOptions);

		// **** send version

		// TODO: Check if this is used irregardless of SWT UI running

		add(new BooleanParameterImpl("Send Version Info",
				"ConfigView.label.allowSendVersion"));

		add(new HyperlinkParameterImpl("ConfigView.label.version.info.link",
				Wiki.VERSION_AZUREUSPLATFORM));

		if (!Constants.isOSX) {

			add(new BooleanParameterImpl("confirmationOnExit",
					"ConfigView.section.style.confirmationOnExit"));
		}

		// clear remembered decisions

		ActionParameterImpl clear_decisions = new ActionParameterImpl(
				"ConfigView.section.interface.cleardecisions",
				"ConfigView.section.interface.cleardecisionsbutton");
		add(clear_decisions);

		clear_decisions.addListener(param -> RememberedDecisionsManager.clearAll());

		//

		ActionParameterImpl clear_tracker_button = new ActionParameterImpl(
				"ConfigView.section.interface.cleartrackers",
				"ConfigView.section.interface.cleartrackersbutton");
		add(clear_tracker_button);

		clear_tracker_button.addListener(
				param -> TrackersUtil.getInstance().clearAllTrackers(true));

		//

		ActionParameterImpl clear_save_path_button = new ActionParameterImpl(
				"ConfigView.section.interface.clearsavepaths",
				"ConfigView.section.interface.clearsavepathsbutton");
		add(clear_save_path_button);

		clear_save_path_button.addListener(
				param -> {
					COConfigurationManager.setParameter("saveTo_list", new ArrayList());
					COConfigurationManager.setParameter("open.torrent.window.moc.history", new ArrayList());
				});

		//

		decisions_parameter_listener = parameterName -> {
			boolean enabled = COConfigurationManager.getMapParameter(
					"MessageBoxWindow.decisions", new HashMap()).size() > 0;

			clear_decisions.setEnabled(enabled);
		};

		COConfigurationManager.addAndFireParameterListener(
				"MessageBoxWindow.decisions", decisions_parameter_listener);

		// reset associations

		if (platform.hasCapability(
				PlatformManagerCapabilities.RegisterFileAssociations)) {

			ActionParameterImpl reset_associations = new ActionParameterImpl(
					"ConfigView.section.interface.resetassoc",
					"ConfigView.section.interface.resetassocbutton");
			add(reset_associations);

			reset_associations.addListener(param -> {

				try {
					platform.registerApplication();

				} catch (PlatformManagerException e) {

					Logger.log(new LogAlert(LogAlert.UNREPEATABLE,
							"Failed to register application", e));
				}
			});

			add(new BooleanParameterImpl("config.interface.checkassoc",
					"ConfigView.section.interface.checkassoc"));

		}
	}
}
