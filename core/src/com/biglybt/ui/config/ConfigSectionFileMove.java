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
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.ui.config;

import java.util.ArrayList;
import java.util.List;

import com.biglybt.pifimpl.local.ui.config.BooleanParameterImpl;
import com.biglybt.pifimpl.local.ui.config.DirectoryParameterImpl;
import com.biglybt.pifimpl.local.ui.config.ParameterGroupImpl;

import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.config.Parameter;

import static com.biglybt.core.config.ConfigKeys.File.*;

public class ConfigSectionFileMove
	extends ConfigSectionImpl
{
	public static final String SECTION_ID = ConfigSection.SECTION_FILES + ".move";

	public ConfigSectionFileMove() {
		super(SECTION_ID, ConfigSection.SECTION_FILES);
	}

	@Override
	public void build() {

		// Move on complete / deletion.
		createMoveOnEventGrouping("ConfigView.label.movecompleted",
				BCFG_MOVE_COMPLETED_WHEN_DONE, SCFG_COMPLETED_FILES_DIRECTORY,
				BCFG_MOVE_TORRENT_WHEN_DONE, SCFG_MOVE_TORRENT_WHEN_DONE_DIRECTORY,
				BCFG_MOVE_ONLY_WHEN_IN_DEFAULT_SAVE_DIR, null);

		createMoveOnEventGrouping("ConfigView.label.moveremoved",
				BCFG_FILE_MOVE_DOWNLOAD_REMOVED_ENABLED,
				SCFG_FILE_MOVE_DOWNLOAD_REMOVED_PATH,
				BCFG_FILE_MOVE_DOWNLOAD_REMOVED_MOVE_TORRENT,
				SCFG_FILE_MOVE_DOWNLOAD_REMOVED_MOVE_TORRENT_PATH,
				BCFG_FILE_MOVE_DOWNLOAD_REMOVED_ONLY_IN_DEFAULT,
				BCFG_FILE_MOVE_DOWNLOAD_REMOVED_MOVE_PARTIAL);

		// add sub-dir
		
		BooleanParameterImpl addSubDir = new BooleanParameterImpl(
				BCFG_FILE_MOVE_ADD_SUB_FOLDER,	"ConfigView.label.move.add.sub.dir");
		add(addSubDir);

		
		// temp folder
		
		BooleanParameterImpl useTempFolder = new BooleanParameterImpl(
				BCFG_FILE_USE_TEMP_AND_MOVE_ENABLE,	"ConfigView.label.download.to.temp.and.move");
		add(useTempFolder);

		DirectoryParameterImpl tempPath = new DirectoryParameterImpl(
				SCFG_FILE_USE_TEMP_AND_MOVE_PATH, "ConfigView.label.directory");
		add(tempPath);
		tempPath.setIndent(1, true);

		useTempFolder.addEnabledOnSelection( tempPath );
		
		// copy rather than move

		BooleanParameterImpl copyDontMove = new BooleanParameterImpl(
				BCFG_COPY_AND_DELETE_DATA_RATHER_THAN_MOVE,
				"ConfigView.label.copyanddeleteratherthanmove");
		add(copyDontMove, Parameter.MODE_INTERMEDIATE);

		BooleanParameterImpl moveIfSameDrive = new BooleanParameterImpl(
				BCFG_MOVE_IF_ON_SAME_DRIVE, "ConfigView.label.moveifsamedrive");
		add(moveIfSameDrive, Parameter.MODE_INTERMEDIATE);
		moveIfSameDrive.setIndent(1, true);

		copyDontMove.addEnabledOnSelection(moveIfSameDrive);

		BooleanParameterImpl ignoreOriginDelete = new BooleanParameterImpl(
				BCFG_FILE_MOVE_ORIGIN_DELETE_FAIL_IS_WARNING,	"ConfigView.label.move.origin.del.fail.is.warn");
		add(ignoreOriginDelete);

		
		add(new BooleanParameterImpl(BCFG_FILE_MOVE_SUBDIR_IS_DEFAULT,
				"ConfigView.label.subdir_is_in_default"));
	}

	private void createMoveOnEventGrouping(String enable_section_label,
			String move_when_done_setting, String move_path_setting,
			String move_torrent_setting, String move_torrent_dir_setting,
			String move_when_in_save_dir_setting,
			String move_partial_downloads_setting) {

		BooleanParameterImpl moveCompleted = new BooleanParameterImpl(
				move_when_done_setting, enable_section_label);
		add(moveCompleted);

		List<Parameter> listGroup = new ArrayList<>();

		// move folder

		DirectoryParameterImpl movePath = new DirectoryParameterImpl(
				move_path_setting, "ConfigView.label.directory");
		add(movePath, listGroup);
		movePath.setDialogTitleKey("ConfigView.dialog.choosemovepath");

		// move torrent when done

		BooleanParameterImpl moveTorrent = new BooleanParameterImpl(
				move_torrent_setting, "ConfigView.label.movetorrent");
		add(moveTorrent, listGroup);

		// move torrent folder

		DirectoryParameterImpl moveTorrentPath = new DirectoryParameterImpl(
				move_torrent_dir_setting, "ConfigView.label.directory.if.different");
		add(moveTorrentPath, listGroup);
		moveTorrentPath.setIndent(1, true);
		moveTorrentPath.setDialogTitleKey("ConfigView.dialog.choosemovepath");

		moveTorrent.addEnabledOnSelection(moveTorrentPath);

		// only in default

		BooleanParameterImpl moveOnly = new BooleanParameterImpl(
				move_when_in_save_dir_setting,
				"ConfigView.label.moveonlyusingdefaultsave");
		add(moveOnly, listGroup);

		// move if partially finished.

		if (move_partial_downloads_setting != null) {
			BooleanParameterImpl movePartial = new BooleanParameterImpl(
					move_partial_downloads_setting,
					"ConfigView.label.movepartialdownloads");
			add(movePartial, listGroup);
		}

		ParameterGroupImpl pgMoveComplete = new ParameterGroupImpl(null, listGroup);
		add("pg" + enable_section_label, pgMoveComplete);
		pgMoveComplete.setIndent(1, false);

		moveCompleted.addEnabledOnSelection(pgMoveComplete);
	}
}
