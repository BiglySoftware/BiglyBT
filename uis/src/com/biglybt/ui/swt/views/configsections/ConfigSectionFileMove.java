/*
 * File    : ConfigPanelFile.java
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

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Constants;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.config.*;
import com.biglybt.ui.swt.pif.UISWTConfigSection;

import com.biglybt.ui.swt.imageloader.ImageLoader;

import com.biglybt.pif.ui.config.ConfigSection;

public class ConfigSectionFileMove implements UISWTConfigSection
{
	private Image imgOpenFolder;

	@Override
	public String configSectionGetParentSection() {
		return ConfigSection.SECTION_FILES;
	}

	@Override
	public String configSectionGetName() {
		return ConfigSection.SECTION_FILES + ".move";
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
		return 1;
	}


	@Override
	public Composite configSectionCreate(final Composite parent) {
		GridData gridData;
		int userMode = COConfigurationManager.getIntParameter("User Mode");

		Composite gFile = new Composite(parent, SWT.NULL);

		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		layout.marginHeight = 0;
		gFile.setLayout(layout);

		ImageLoader imageLoader = ImageLoader.getInstance();
		imgOpenFolder = imageLoader.getImage("openFolderButton");

		// Move on complete / deletion.
		createMoveOnEventGrouping(gFile,
				"ConfigView.label.movecompleted",
				"Move Completed When Done",
				"Completed Files Directory",
				"Move Torrent When Done",
				"Move Torrent When Done Directory",
				"Move Only When In Default Save Dir", null);

		createMoveOnEventGrouping(gFile,
				"ConfigView.label.moveremoved",
				"File.move.download.removed.enabled",
				"File.move.download.removed.path",
				"File.move.download.removed.move_torrent",
				"File.move.download.removed.move_torrent_path",
				"File.move.download.removed.only_in_default",
				"File.move.download.removed.move_partial");

		if (userMode > 0) {
			// copy rather than move

			BooleanParameter copyDontMove = new BooleanParameter(gFile,
					"Copy And Delete Data Rather Than Move",
					"ConfigView.label.copyanddeleteratherthanmove");
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			copyDontMove.setLayoutData(gridData);

			BooleanParameter moveIfSameDrive = new BooleanParameter(gFile,
					"Move If On Same Drive",
					"ConfigView.label.moveifsamedrive");
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			gridData.horizontalIndent = 25;
			moveIfSameDrive.setLayoutData(gridData);

			IAdditionalActionPerformer derp = new ChangeSelectionActionPerformer(
					moveIfSameDrive);
			copyDontMove.setAdditionalActionPerformer(derp);
		}

		BooleanParameter subdirIsDefault = new BooleanParameter(gFile,
				"File.move.subdir_is_default", "ConfigView.label.subdir_is_in_default");
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		subdirIsDefault.setLayoutData(gridData);

		return gFile;
	}

	private void createMoveOnEventGrouping(
			final Composite gFile,
			String enable_section_label,
			String move_when_done_setting,
			String move_path_setting,
			String move_torrent_setting,
			String move_torrent_dir_setting,
			String move_when_in_save_dir_setting,
			String move_partial_downloads_setting)
	{
		BooleanParameter moveCompleted = new BooleanParameter(gFile,
				move_when_done_setting, enable_section_label);
		GridData gridData = new GridData();
		GridLayout layout = null;
		gridData.horizontalSpan = 2;
		moveCompleted.setLayoutData(gridData);

		Composite gMoveCompleted = new Composite(gFile, SWT.NULL);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalIndent = 25;
		gridData.horizontalSpan = 2;
		Utils.setLayoutData(gMoveCompleted, gridData);
		layout = new GridLayout();
		layout.marginHeight = 0;
		layout.marginWidth = 4;
		layout.numColumns = 4;
		gMoveCompleted.setLayout(layout);

			// move folder

		Label lDir = new Label(gMoveCompleted, SWT.NULL);
		Messages.setLanguageText(lDir, "ConfigView.label.directory");

		gridData = new GridData(GridData.FILL_HORIZONTAL);
		final StringParameter movePath = new StringParameter(gMoveCompleted, move_path_setting);
		gridData.horizontalSpan = 2;
		movePath.setLayoutData(gridData);

		Button browse3 = new Button(gMoveCompleted, SWT.PUSH);
		browse3.setImage(imgOpenFolder);
		imgOpenFolder.setBackground(browse3.getBackground());
		browse3.setToolTipText(MessageText.getString("ConfigView.button.browse"));

		browse3.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				DirectoryDialog dialog = new DirectoryDialog(gFile.getShell(),
						SWT.APPLICATION_MODAL);
				dialog.setFilterPath(movePath.getValue());
				dialog.setText(MessageText.getString("ConfigView.dialog.choosemovepath"));
				String path = dialog.open();
				if (path != null) {
					movePath.setValue(path);
				}
			}
		});

			// move torrent when done

		final BooleanParameter moveTorrent = new BooleanParameter(gMoveCompleted,
				move_torrent_setting, "ConfigView.label.movetorrent");
		gridData = new GridData();
		gridData.horizontalSpan = 4;
		moveTorrent.setLayoutData(gridData);

			// move torrent folder

		Composite cTorrentDir = new Composite( gMoveCompleted, SWT.NULL );
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 4;
		cTorrentDir.setLayoutData(gridData);
		layout = new GridLayout();
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		layout.numColumns = 3;
		cTorrentDir.setLayout(layout);

		Label lTorrentDir = new Label(cTorrentDir, SWT.NULL);
		Messages.setLanguageText(lTorrentDir, "ConfigView.label.directory.if.different");
		gridData = new GridData();
		gridData.horizontalIndent = 25;
		Utils.setLayoutData(lTorrentDir, gridData);

		gridData = new GridData(GridData.FILL_HORIZONTAL);
		final StringParameter moveTorrentPath = new StringParameter(cTorrentDir, move_torrent_dir_setting);
		moveTorrentPath.setLayoutData(gridData);

		Button browse4 = new Button(cTorrentDir, SWT.PUSH);
		browse4.setImage(imgOpenFolder);
		imgOpenFolder.setBackground(browse4.getBackground());
		browse4.setToolTipText(MessageText.getString("ConfigView.button.browse"));

		browse4.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				DirectoryDialog dialog = new DirectoryDialog(gFile.getShell(),
						SWT.APPLICATION_MODAL);
				dialog.setFilterPath(moveTorrentPath.getValue());
				dialog.setText(MessageText.getString("ConfigView.dialog.choosemovepath"));
				String path = dialog.open();
				if (path != null) {
					moveTorrentPath.setValue(path);
				}
			}
		});

		final IAdditionalActionPerformer grayPathAndButton3 = new ChangeSelectionActionPerformer(
				new Control[]{ moveTorrentPath.getControl(), browse4});

		moveTorrent.setAdditionalActionPerformer(grayPathAndButton3);

			// only in default

		BooleanParameter moveOnly = new BooleanParameter(gMoveCompleted,
				move_when_in_save_dir_setting, "ConfigView.label.moveonlyusingdefaultsave");
		gridData = new GridData();
		gridData.horizontalSpan = 3;
		moveOnly.setLayoutData(gridData);

			// move if partially finished.

		if (move_partial_downloads_setting != null) {
			BooleanParameter movePartial = new BooleanParameter(gMoveCompleted,
					move_partial_downloads_setting, "ConfigView.label.movepartialdownloads");
			gridData = new GridData();
			gridData.horizontalSpan = 3;
			movePartial.setLayoutData(gridData);
		}

		Control[] controls3 = new Control[] { gMoveCompleted };

		IAdditionalActionPerformer grayPathAndButton2 = new ChangeSelectionActionPerformer(
				controls3);

		moveCompleted.setAdditionalActionPerformer(grayPathAndButton2);
		moveCompleted.setAdditionalActionPerformer(
			new ChangeSelectionActionPerformer(new Control[0]) {

				@Override
				public void performAction() {
					grayPathAndButton3.performAction();
				}
			});
	}
}
