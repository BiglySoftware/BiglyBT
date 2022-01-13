/*
 * Created : Dec 4, 2006
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
import java.util.List;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.GeneralUtils;
import com.biglybt.pifimpl.local.ui.config.*;
import com.biglybt.ui.config.ConfigSectionImpl;

import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.config.Parameter;

/**
 * Interface->Alerts
 * <p/>
 * Afaik, Alerts are only available in SWT UI, so all Parameter definitions are here
 */
public class ConfigSectionInterfaceAlertsSWT
	extends ConfigSectionImpl
{
	public static final String SECTION_ID = "interface.alerts";

	public ConfigSectionInterfaceAlertsSWT() {
		super(SECTION_ID, ConfigSection.SECTION_INTERFACE);
	}

	@Override
	public void build() {
		setDefaultUITypesForAdd(UIInstance.UIT_SWT);

		List<Parameter> listSound = new ArrayList<>();

		// DOWNLOAD FINISHED

		LabelParameterImpl paramSoundGroupInfo = new LabelParameterImpl(
				"ConfigView.section.interface.wavlocation.info");
		add(paramSoundGroupInfo, listSound);

		playSoundWhen(listSound, "Play Download Finished Announcement",
				"Play Download Finished Announcement Text",
				"ConfigView.label.playdownloadspeech", "Play Download Finished",
				"Play Download Finished File", "ConfigView.label.playdownloadfinished");

		// DOWNLOAD ERROR

		playSoundWhen(listSound, "Play Download Error Announcement",
				"Play Download Error Announcement Text",
				"ConfigView.label.playdownloaderrorspeech", "Play Download Error",
				"Play Download Error File", "ConfigView.label.playdownloaderror");

		// FILE FINISHED

		playSoundWhen(listSound, "Play File Finished Announcement",
				"Play File Finished Announcement Text",
				"ConfigView.label.playfilespeech", "Play File Finished",
				"Play File Finished File", "ConfigView.label.playfilefinished");

		// NOTIFICATION ADDED

		playSoundWhen(listSound, "Play Notification Added Announcement",
				"Play Notification Added Announcement Text",
				"ConfigView.label.playnotificationaddedspeech",
				"Play Notification Added", "Play Notification Added File",
				"ConfigView.label.playnotificationadded");

		ParameterGroupImpl pgSoundParams = new ParameterGroupImpl(null,
				listSound).setNumberOfColumns2(Constants.isOSX ? 4 : 2);
		add("pgSoundParams", pgSoundParams);

		add(new ParameterGroupImpl("ConfigView.group.sounds", pgSoundParams,
				paramSoundGroupInfo));

		// xxxxxxxxxxxxxxxx

		boolean isAZ3 = COConfigurationManager.getStringParameter("ui").equals(
				"az3");

		if (isAZ3) {

			BooleanParameterImpl p = new BooleanParameterImpl(
					"Request Attention On New Download",
					"ConfigView.label.dl.add.req.attention");
			add(p);
		}

		BooleanParameterImpl activate_win = new BooleanParameterImpl(
				"Activate Window On External Download",
				"ConfigView.label.show.win.on.add");
		add(activate_win);

		BooleanParameterImpl no_auto_activate = new BooleanParameterImpl(
				"Reduce Auto Activate Window", "ConfigView.label.reduce.auto.activate");
		add(no_auto_activate);

		//
		// popups group

		List<Parameter> listPopup = new ArrayList<>();

		BooleanParameterImpl popup_dl_added = new BooleanParameterImpl(
				"Popup Download Added", "ConfigView.label.popupdownloadadded");
		add(popup_dl_added, listPopup);

		BooleanParameterImpl popup_dl_completed = new BooleanParameterImpl(
				"Popup Download Finished", "ConfigView.label.popupdownloadfinished");
		add(popup_dl_completed, listPopup);

		BooleanParameterImpl popup_dl_error = new BooleanParameterImpl(
				"Popup Download Error", "ConfigView.label.popupdownloaderror");
		add(popup_dl_error, listPopup);
		
		BooleanParameterImpl popup_dl_checked = new BooleanParameterImpl(
				"Popup Check Complete", "ConfigView.label.popupdownloadchecked");
		add(popup_dl_checked, listPopup);

		BooleanParameterImpl popup_file_completed = new BooleanParameterImpl(
				"Popup File Finished", "ConfigView.label.popupfilefinished");
		add(popup_file_completed, listPopup);

		// disable sliding

		BooleanParameterImpl disable_sliding = new BooleanParameterImpl(
				"GUI_SWT_DisableAlertSliding",
				"ConfigView.section.style.disableAlertSliding");
		add(disable_sliding, listPopup);

		// Timestamps for popup alerts.
		BooleanParameterImpl show_alert_timestamps = new BooleanParameterImpl(
				"Show Timestamp For Alerts", "ConfigView.label.popup.timestamp");
		add(show_alert_timestamps, listPopup);

		// Auto-hide popup setting.
		IntParameterImpl auto_hide_alert = new IntParameterImpl(
				"Message Popup Autoclose in Seconds", "ConfigView.label.popup.autohide",
				0, 86400);
		add(auto_hide_alert, listPopup);

		add(new ParameterGroupImpl("label.popups", listPopup));

		//
		// notify group

		BooleanParameterImpl notify_download_finished = new BooleanParameterImpl(
				"Notify Download Finished", "ConfigView.label.nativedownloadfinished");
		add(notify_download_finished);

		BooleanParameterImpl notify_download_error = new BooleanParameterImpl(
				"Notify Download Error", "ConfigView.label.nativedownloaderror");
		add(notify_download_error);

		add(new ParameterGroupImpl("label.native.notify", notify_download_error,
				notify_download_finished));

	}

	private void playSoundWhen(List<Parameter> listSound,
			String announceEnableConfig, String announceKeyConfig,
			String announceResource, String playEnableConfig, String playKeyConfig,
			String playResource) {
		if (Constants.isOSX) {
			// download info

			add(new BooleanParameterImpl(announceEnableConfig, announceResource),
					listSound);

			final StringParameterImpl d_speechParameter = new StringParameterImpl(
					announceKeyConfig, null);
			add(d_speechParameter, listSound);
			d_speechParameter.setTextLimit(40);
			d_speechParameter.setWidthInCharacters(15);
		}

		add(new BooleanParameterImpl(playEnableConfig, playResource), listSound);

		// download info

		FileParameterImpl e_pathParameter = new FileParameterImpl(playKeyConfig,
				null, "*.wav");
		add(e_pathParameter, listSound);
		e_pathParameter.setHintKey("ConfigView.textlabel.default");
		e_pathParameter.setDialogTitleKey(
				"ConfigView.section.interface.wavlocation");

		e_pathParameter.addListener(param -> {
			GeneralUtils.playSound( e_pathParameter.getValue());
		});
	}
}
