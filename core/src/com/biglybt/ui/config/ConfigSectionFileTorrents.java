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

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.torrent.impl.TorrentOpenOptions;
import com.biglybt.pifimpl.local.ui.config.*;

import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.config.Parameter;

import static com.biglybt.core.config.ConfigKeys.File.*;

public class ConfigSectionFileTorrents
		extends ConfigSectionImpl {
	public static final String SECTION_ID = "torrents";

	public ConfigSectionFileTorrents() {
		super(SECTION_ID, ConfigSection.SECTION_FILES);
	}

	@Override
	public void build() {

		List<Parameter> listSaveTorrents = new ArrayList<>();

		// Save .Torrent files to..

		BooleanParameterImpl saveTorrents = new BooleanParameterImpl(
				BCFG_SAVE_TORRENT_FILES, "ConfigView.label.savetorrents");
		add(saveTorrents);

		DirectoryParameterImpl torrentPathParameter = new DirectoryParameterImpl(
				SCFG_GENERAL_DEFAULT_TORRENT_DIRECTORY,
				"ConfigView.label.savedirectory");
		add(torrentPathParameter, listSaveTorrents);
		torrentPathParameter.setDialogTitleKey(
				"ConfigView.dialog.choosedefaulttorrentpath");

		add(new BooleanParameterImpl(BCFG_SAVE_TORRENT_BACKUP,
				"ConfigView.label.savetorrentbackup"), listSaveTorrents);

		// Delete saved torrents

		add(new BooleanParameterImpl(BCFG_DELETE_SAVED_TORRENT_FILES,
						"ConfigView.label.deletesavedtorrents"), Parameter.MODE_ADVANCED,
				listSaveTorrents);

		ParameterGroupImpl pgSaveTorrents = new ParameterGroupImpl(null,
				listSaveTorrents);
		add("pgSaveTorrents", pgSaveTorrents);
		pgSaveTorrents.setIndent(1, false);

		saveTorrents.addEnabledOnSelection(pgSaveTorrents);

		// Delete .Torrent files

		add(new BooleanParameterImpl(BCFG_DELETE_ORIGINAL_TORRENT_FILES,
				"ConfigView.label.deletetorrents"));

		// add stopped

		add(new BooleanParameterImpl(BCFG_DEFAULT_START_TORRENTS_STOPPED,
				"ConfigView.label.defaultstarttorrentsstopped"));

		add(new BooleanParameterImpl(
				BCFG_DEFAULT_START_TORRENTS_STOPPED_AUTO_PAUSE,
				"ConfigView.label.defaultstarttorrentsstoppedandpause"));

		// Watch Folder
		BooleanParameterImpl watchFolder = new BooleanParameterImpl(
				BCFG_WATCH_TORRENT_FOLDER, "ConfigView.label.watchtorrentfolder");
		add(watchFolder);

		List<Parameter> listWatchDirs = new ArrayList<>();
		List<Parameter> listWatch = new ArrayList<>();

		int num_folders = COConfigurationManager.getIntParameter(
				ICFG_WATCH_TORRENT_FOLDER_PATH_COUNT, 1);

		for (int i = 0; i < num_folders; i++) {
			DirectoryParameterImpl watchFolderPathParameter = new DirectoryParameterImpl(
					SCFG_PREFIX_WATCH_TORRENT_FOLDER_PATH + (i == 0 ? "" : (" " + i)),
					"ConfigView.label.importdirectory");
			add(watchFolderPathParameter, listWatchDirs);

			watchFolderPathParameter.setDialogTitleKey(
					"ConfigView.dialog.choosewatchtorrentfolderpath");

			StringParameterImpl tagParam = new StringParameterImpl(
					SCFG_PREFIX_WATCH_TORRENT_FOLDER_TAG + (i == 0 ? "" : (" " + i)),
					"label.assign.to.tag");
			add(tagParam, listWatchDirs);
			tagParam.setWidthInCharacters(12);
		}

		ParameterGroupImpl pgWatchDirs = new ParameterGroupImpl(null,
				listWatchDirs);
		add("pgWatchDirs", pgWatchDirs);
		pgWatchDirs.setNumberOfColumns(2);
		pgWatchDirs.setIndent(1, false);

		// add another folder
		ActionParameterImpl addButton = new ActionParameterImpl(
				"ConfigView.label.addanotherfolder", "Button.add");
		add("addFolder", addButton, listWatch);

		addButton.addListener(param -> {

			int num = COConfigurationManager.getIntParameter(
					ICFG_WATCH_TORRENT_FOLDER_PATH_COUNT, 1);

			COConfigurationManager.setParameter(ICFG_WATCH_TORRENT_FOLDER_PATH_COUNT,
					num + 1);
			requestRebuild();

		});

		// watch interval

		String sec = " "
				+ MessageText.getString("ConfigView.section.stats.seconds");
		String min = " "
				+ MessageText.getString("ConfigView.section.stats.minutes");
		String hr = " " + MessageText.getString("ConfigView.section.stats.hours");

		int[] watchTorrentFolderIntervalValues = {
				1,
				2,
				3,
				4,
				5,
				10,
				30,
				1 * 60,
				2 * 60,
				3 * 60,
				4 * 60,
				5 * 60,
				10 * 60,
				15 * 60,
				30 * 60,
				60 * 60,
				2 * 60 * 60,
				4 * 60 * 60,
				6 * 60 * 60,
				8 * 60 * 60,
				12 * 60 * 60,
				16 * 60 * 60,
				20 * 60 * 60,
				24 * 60 * 60
		};

		String[] watchTorrentFolderIntervalLabels = new String[watchTorrentFolderIntervalValues.length];

		for (int i = 0; i < watchTorrentFolderIntervalValues.length; i++) {
			int secs = watchTorrentFolderIntervalValues[i];
			int mins = secs / 60;
			int hrs = mins / 60;

			watchTorrentFolderIntervalLabels[i] = " " + (secs < 60 ? (secs + sec)
					: ((hrs == 0 ? (mins + min) : (hrs + hr))));
		}

		add(new IntListParameterImpl(
						ICFG_WATCH_TORRENT_FOLDER_INTERVAL_SECS,
						"ConfigView.label.watchtorrentfolderinterval",
						watchTorrentFolderIntervalValues, watchTorrentFolderIntervalLabels),
				listWatch);

		// add stopped
		/*
		add(new BooleanParameterImpl(BCFG_START_WATCHED_TORRENTS_STOPPED,
				"ConfigView.label.startwatchedtorrentsstopped"), listWatch);
		*/
		
		// add mode
				
		String[]	addModeLabels = new String[TorrentOpenOptions.STARTMODE_KEYS.length];
		
		for ( int i=0;i< addModeLabels.length;i++){
			addModeLabels[i] = MessageText.getString( TorrentOpenOptions.STARTMODE_KEYS[i] );
		}
		
		add(new IntListParameterImpl(
				ICFG_WATCH_TORRENT_FOLDER_ADD_MODE,
				"OpenTorrentWindow.startMode",
				TorrentOpenOptions.STARTMODE_VALUES, addModeLabels),
				listWatch );

		
		// always rename to .imported

		add(new BooleanParameterImpl(BCFG_WATCH_TORRENT_ALWAYS_RENAME,
				"ConfigView.label.watchtorrentrename"), listWatch);

		ParameterGroupImpl pgWatch = new ParameterGroupImpl(null, listWatch);
		add("pgWatch", pgWatch);
		pgWatch.setIndent(1, false);

		watchFolder.addEnabledOnSelection(pgWatch, pgWatchDirs);
		
			// monitor clipboard

		BooleanParameterImpl paramMonClip = new BooleanParameterImpl(BCFG_TORRENT_MONITOR_CLIPBOARD,
			"label.monitor.clipboard");
		paramMonClip.setAllowedUiTypes(UIInstance.UIT_SWT);
		add(paramMonClip);

		
	}
}
