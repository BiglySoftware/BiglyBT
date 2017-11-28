/*
 * File    : ConfigSectionInterfaceAlerts.java
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

import java.applet.Applet;
import java.io.File;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AEThread2;
import com.biglybt.core.util.Constants;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.config.*;
import com.biglybt.ui.swt.pif.UISWTConfigSection;

import com.biglybt.ui.swt.imageloader.ImageLoader;

public class ConfigSectionInterfaceAlerts
	implements UISWTConfigSection
{
	private final static int REQUIRED_MODE = 0;

	@Override
	public String configSectionGetParentSection() {
		return ConfigSection.SECTION_INTERFACE;
	}

	/* Name of section will be pulled from
	 * ConfigView.section.<i>configSectionGetName()</i>
	 */
	@Override
	public String configSectionGetName() {
		return "interface.alerts";
	}

	@Override
	public void configSectionSave() {
	}

	@Override
	public void configSectionDelete() {
		ImageLoader imageLoader = ImageLoader.getInstance();
		imageLoader.releaseImage("openFolderButton");
	}

	@Override
	public int maxUserMode() {
		return REQUIRED_MODE;
	}

	@Override
	public Composite configSectionCreate(final Composite parent) {
		Image imgOpenFolder = null;
		ImageLoader imageLoader = ImageLoader.getInstance();
		imgOpenFolder = imageLoader.getImage("openFolderButton");

		GridData gridData;
		GridLayout layout;

		Composite cSection = new Composite(parent, SWT.NULL);
		gridData = new GridData(GridData.VERTICAL_ALIGN_FILL
				| GridData.HORIZONTAL_ALIGN_FILL);
		cSection.setLayoutData(gridData);
		layout = new GridLayout();
		layout.marginWidth = 0;
		//layout.numColumns = 2;
		cSection.setLayout(layout);

		Composite cArea = new Composite(cSection, SWT.NONE);
		layout = new GridLayout();
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		layout.numColumns = 4;
		cArea.setLayout(layout);
		cArea.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		// DOWNLOAD FINISHED

		playSoundWhen(
				cArea, imgOpenFolder,
				"Play Download Finished Announcement",
				"Play Download Finished Announcement Text",
				"ConfigView.label.playdownloadspeech",
				"Play Download Finished",
				"Play Download Finished File",
				"ConfigView.label.playdownloadfinished" );

		// DOWNLOAD ERROR

		playSoundWhen(
			cArea, imgOpenFolder,
			"Play Download Error Announcement",
			"Play Download Error Announcement Text",
			"ConfigView.label.playdownloaderrorspeech",
			"Play Download Error",
			"Play Download Error File",
			"ConfigView.label.playdownloaderror" );


		// FILE FINISHED

		playSoundWhen(
				cArea, imgOpenFolder,
				"Play File Finished Announcement",
				"Play File Finished Announcement Text",
				"ConfigView.label.playfilespeech",
				"Play File Finished",
				"Play File Finished File",
				"ConfigView.label.playfilefinished" );


		// NOTIFICATION ADDED

		playSoundWhen(
				cArea, imgOpenFolder,
				"Play Notification Added Announcement",
				"Play Notification Added Announcement Text",
				"ConfigView.label.playnotificationaddedspeech",
				"Play Notification Added",
				"Play Notification Added File",
				"ConfigView.label.playnotificationadded" );

			// xxxxxxxxxxxxxxxx

		boolean isAZ3 = COConfigurationManager.getStringParameter("ui").equals("az3");

		if ( isAZ3 ){

			BooleanParameter p = new BooleanParameter(cArea,
					"Request Attention On New Download", "ConfigView.label.dl.add.req.attention");
			gridData = new GridData();
			gridData.horizontalSpan = 3;
			p.setLayoutData(gridData);
		}

		BooleanParameter activate_win = new BooleanParameter(cArea,
				"Activate Window On External Download", "ConfigView.label.show.win.on.add");
		gridData = new GridData();
		gridData.horizontalSpan = 3;
		activate_win.setLayoutData(gridData);

		BooleanParameter no_auto_activate = new BooleanParameter(cArea,
				"Reduce Auto Activate Window", "ConfigView.label.reduce.auto.activate");
		gridData = new GridData();
		gridData.horizontalSpan = 3;
		no_auto_activate.setLayoutData(gridData);


			// popups group

		{
			Group gPopup = new Group(cSection, SWT.NULL);
			Messages.setLanguageText( gPopup, "label.popups" );
			layout = new GridLayout();
			layout.numColumns = 2;
			gPopup.setLayout(layout);
			gPopup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
	
			BooleanParameter popup_dl_added = new BooleanParameter(gPopup,
					"Popup Download Added", "ConfigView.label.popupdownloadadded");
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			popup_dl_added.setLayoutData(gridData);
	
			BooleanParameter popup_dl_completed = new BooleanParameter(gPopup,
					"Popup Download Finished", "ConfigView.label.popupdownloadfinished");
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			popup_dl_completed.setLayoutData(gridData);
	
			BooleanParameter popup_dl_error = new BooleanParameter(gPopup,
					"Popup Download Error", "ConfigView.label.popupdownloaderror");
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			popup_dl_error.setLayoutData(gridData);
	
			BooleanParameter popup_file_completed = new BooleanParameter(gPopup,
					"Popup File Finished", "ConfigView.label.popupfilefinished");
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			popup_file_completed.setLayoutData(gridData);
			
				// disable sliding
			
			BooleanParameter disable_sliding = new BooleanParameter(gPopup,
					"GUI_SWT_DisableAlertSliding", "ConfigView.section.style.disableAlertSliding");
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			disable_sliding.setLayoutData(gridData);
	
				// Timestamps for popup alerts.
			BooleanParameter show_alert_timestamps = new BooleanParameter(gPopup,
					"Show Timestamp For Alerts", "ConfigView.label.popup.timestamp");
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			show_alert_timestamps.setLayoutData(gridData);
	
				// Auto-hide popup setting.
			Label label = new Label(gPopup, SWT.WRAP);
			Messages.setLanguageText(label, "ConfigView.label.popup.autohide");
			label.setLayoutData(new GridData());
			IntParameter auto_hide_alert = new IntParameter(gPopup,
					"Message Popup Autoclose in Seconds", 0, 86400);
			gridData = new GridData();
			gridData.horizontalSpan = 1;
			auto_hide_alert.setLayoutData(gridData);
		}
		
			// notify group 

		{
			Group nPopup = new Group(cSection, SWT.NULL);
			Messages.setLanguageText( nPopup, "label.native.notify" );
			layout = new GridLayout();
			layout.numColumns = 2;
			nPopup.setLayout(layout);
			nPopup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
	
			BooleanParameter popup_dl_completed = new BooleanParameter(nPopup,
					"Notify Download Finished", "ConfigView.label.nativedownloadfinished");
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			popup_dl_completed.setLayoutData(gridData);
	
			BooleanParameter popup_dl_error = new BooleanParameter(nPopup,
					"Notify Download Error", "ConfigView.label.nativedownloaderror");
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			popup_dl_error.setLayoutData(gridData);
	
		}	
		
		
		return cSection;
	}

	private void
	playSoundWhen(
		final Composite 	cArea,
		Image				imgOpenFolder,
		String				announceEnableConfig,
		String				announceKeyConfig,
		String				announceResource,
		String				playEnableConfig,
		String				playKeyConfig,
		String				playResource )
	{
		if (Constants.isOSX) {
			// download info

			new BooleanParameter(
					cArea, announceEnableConfig, announceResource );

			final StringParameter d_speechParameter = new StringParameter(cArea,announceKeyConfig);
			GridData gridData = new GridData();
			gridData.horizontalSpan = 3;
			gridData.widthHint = 150;
			d_speechParameter.setLayoutData(gridData);
			((Text) d_speechParameter.getControl()).setTextLimit(40);
		}

		new BooleanParameter(cArea,playEnableConfig, playResource );

		// download info

		GridData gridData = new GridData(GridData.FILL_HORIZONTAL);

		final StringParameter e_pathParameter = new StringParameter(cArea,playKeyConfig, "");

		if (e_pathParameter.getValue().length() == 0) {

			e_pathParameter.setValue("<default>");
		}

		e_pathParameter.setLayoutData(gridData);

		Button d_browse = new Button(cArea, SWT.PUSH);

		d_browse.setImage(imgOpenFolder);

		imgOpenFolder.setBackground(d_browse.getBackground());

		d_browse.setToolTipText(MessageText.getString("ConfigView.button.browse"));

		d_browse.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				FileDialog dialog = new FileDialog(cArea.getShell(),
						SWT.APPLICATION_MODAL);
				dialog.setFilterExtensions(new String[] {
					"*.wav"
				});
				dialog.setFilterNames(new String[] {
					"*.wav"
				});

				dialog.setText(MessageText.getString("ConfigView.section.interface.wavlocation"));

				final String path = dialog.open();

				if (path != null) {

					e_pathParameter.setValue(path);

					new AEThread2("SoundTest") {
						@Override
						public void run() {
							try {
								Applet.newAudioClip(new File(path).toURI().toURL()).play();

								Thread.sleep(2500);

							} catch (Throwable e) {

							}
						}
					}.start();
				}
			}
		});

		Label d_sound_info = new Label(cArea, SWT.WRAP);
		Messages.setLanguageText(d_sound_info, "ConfigView.section.interface.wavlocation.info");
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.widthHint = 100;
		Utils.setLayoutData(d_sound_info, gridData);
	}
}
