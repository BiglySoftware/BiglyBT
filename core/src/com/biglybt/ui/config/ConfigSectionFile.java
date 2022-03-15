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
import com.biglybt.core.config.impl.ConfigurationDefaults;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.SystemProperties;
import com.biglybt.pifimpl.local.ui.config.*;
import com.biglybt.platform.PlatformManager;
import com.biglybt.platform.PlatformManagerCapabilities;
import com.biglybt.platform.PlatformManagerFactory;

import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.config.Parameter;
import com.biglybt.pif.ui.config.ParameterListener;

import static com.biglybt.core.config.ConfigKeys.File.*;

public class ConfigSectionFile
	extends ConfigSectionImpl
{

	public static final String REFID_TORRENT_ADD_AUTO_SKIP 		= "torrent-add-auto-skip";
	public static final String REFID_TORRENT_ADD_AUTO_PRIORITY	= "torrent-add-auto-priority";

	public ConfigSectionFile() {
		super(ConfigSection.SECTION_FILES, ConfigSection.SECTION_ROOT);
	}

	@Override
	public void build() {

		// Default Dir Section

		List<Parameter> listDefaultDir = new ArrayList<>();

		// def dir: best guess
		BooleanParameterImpl bestGuess = new BooleanParameterImpl(
				BCFG_DEFAULT_DIR_BEST_GUESS,
				"ConfigView.section.file.defaultdir.bestguess");
		add(bestGuess, Parameter.MODE_INTERMEDIATE, listDefaultDir);

		// Save Path
		DirectoryParameterImpl pathParameter = new DirectoryParameterImpl(
				SCFG_DEFAULT_SAVE_PATH, "ConfigView.section.file.defaultdir.ask");
		add(pathParameter, listDefaultDir);
		pathParameter.setDialogTitleKey("ConfigView.dialog.choosedefaultsavepath");
		pathParameter.setDialogTitleKey("ConfigView.section.file.defaultdir.ask");

		// def dir: autoSave

		String[] openValues = {
			ConfigurationDefaults.CFG_TORRENTADD_OPENOPTIONS_NEVER,
			ConfigurationDefaults.CFG_TORRENTADD_OPENOPTIONS_ALWAYS,
			ConfigurationDefaults.CFG_TORRENTADD_OPENOPTIONS_MANY,
		};
		String[] openLabels = {
			MessageText.getString("OpenTorrentOptions.show.never"),
			MessageText.getString("OpenTorrentOptions.show.always"),
			MessageText.getString("OpenTorrentOptions.show.many"),
		};
		StringListParameterImpl paramShowOpenTorrentOptions = new StringListParameterImpl(
			ConfigurationDefaults.CFG_TORRENTADD_OPENOPTIONS,
			"ConfigView.section.file.showopentorrentoptions", openValues,
			openLabels);
		paramShowOpenTorrentOptions.setAllowedUiTypes(UIInstance.UIT_SWT);
		add(paramShowOpenTorrentOptions, listDefaultDir);

		BooleanParameterImpl paramShowSep = new BooleanParameterImpl(BCFG_UI_ADDTORRENT_OPENOPTIONS_SEP,
			"ConfigView.section.file.showopentorrentoptions.sep");
		paramShowSep.setAllowedUiTypes(UIInstance.UIT_SWT);
		add(paramShowSep, listDefaultDir);

		IntParameterImpl autoClose = new IntParameterImpl(
				ICFG_UI_ADDTORRENT_OPENOPTIONS_AUTO_CLOSE_SECS,
				"ConfigView.label.showopentorrentoptions.autoclose");
		add( autoClose, Parameter.MODE_INTERMEDIATE, listDefaultDir);

		
		// def dir: autoSave -> auto-rename

		BooleanParameterImpl autoSaveAutoRename = new BooleanParameterImpl(
				BCFG_DEFAULT_DIR_AUTO_SAVE_AUTO_RENAME,
				"ConfigView.section.file.defaultdir.autorename");
		add(autoSaveAutoRename, Parameter.MODE_INTERMEDIATE, listDefaultDir);

		// def dir: auto update

		BooleanParameterImpl autoUpdateSaveDir = new BooleanParameterImpl(
				BCFG_DEFAULT_DIR_AUTO_UPDATE,
				"ConfigView.section.file.defaultdir.lastused");
		add(autoUpdateSaveDir, Parameter.MODE_INTERMEDIATE, listDefaultDir);

		IntParameterImpl saveHistorySize = new IntParameterImpl(
				ICFG_SAVE_TO_LIST_MAX_ENTRIES,
				"ConfigView.label.save_list.max_entries");
		add(saveHistorySize, Parameter.MODE_INTERMEDIATE, listDefaultDir);

		ActionParameterImpl btnClearHistory = new ActionParameterImpl(
				"ConfigView.label.save_list.clear", "Button.clear");
		add("btnClearHistory", btnClearHistory, listDefaultDir);
		btnClearHistory.setMinimumRequiredUserMode(Parameter.MODE_INTERMEDIATE);
		btnClearHistory.addListener(p -> {
			COConfigurationManager.setParameter(SCFG_SAVE_TO_LIST, new ArrayList());
			btnClearHistory.setEnabled(false);
		});
		List<String> dirList = COConfigurationManager.getStringListParameter(
				SCFG_SAVE_TO_LIST);
		btnClearHistory.setEnabled(dirList.size() > 0);

		add(new ParameterGroupImpl("ConfigView.section.file.defaultdir.section",
				listDefaultDir));

		////////////////////

		if (!Constants.isWindows) {
			BooleanParameterImpl xfsAllocation = new BooleanParameterImpl(
					BCFG_XFS_ALLOCATION, "ConfigView.label.xfs.allocation");
			add(xfsAllocation, Parameter.MODE_INTERMEDIATE);
		}

		// zero new files
		BooleanParameterImpl zeroNew = new BooleanParameterImpl(BCFG_ZERO_NEW,
				"ConfigView.label.zeronewfiles");
		add(zeroNew, Parameter.MODE_INTERMEDIATE);

		BooleanParameterImpl zeroNewStop = new BooleanParameterImpl(
				BCFG_ZERO_NEW_STOP, "ConfigView.label.zeronewfiles.stop");
		add(zeroNewStop, Parameter.MODE_INTERMEDIATE);
		zeroNewStop.setIndent(1, true);

		BooleanParameterImpl sparseFiles = new BooleanParameterImpl(
				BCFG_ENABLE_SPARSE_FILES, "ConfigView.label.spare.file.enable");
		add(sparseFiles, Parameter.MODE_INTERMEDIATE);

		BooleanParameterImpl pieceReorder = new BooleanParameterImpl(
				BCFG_ENABLE_REORDER_STORAGE_MODE, "ConfigView.label.piecereorder");
		add(pieceReorder, Parameter.MODE_INTERMEDIATE);

		IntParameterImpl minMB = new IntParameterImpl(
				ICFG_REORDER_STORAGE_MODE_MIN_MB, "ConfigView.label.piecereorderminmb");
		add(minMB, Parameter.MODE_INTERMEDIATE);
		minMB.setIndent(1, true);

		// incremental file creation

		BooleanParameterImpl incremental = new BooleanParameterImpl(
				BCFG_ENABLE_INCREMENTAL_FILE_CREATION,
				"ConfigView.label.incrementalfile");
		add(incremental, Parameter.MODE_INTERMEDIATE);

		ParameterListener listener = param -> {
			// these are mutually exclusive
			boolean zero = zeroNew.getValue();
			boolean sparse = sparseFiles.getValue();
			boolean reorder = pieceReorder.getValue();
			boolean inc = incremental.getValue();

			if (zero) {
				sparse = false;
				reorder = false;
				inc = false;
			} else if (sparse) {
				reorder = false;
				inc = false;
			} else if (reorder) {
				inc = false;
			} else if (inc) {
				// NOP
			} else {
				zero = sparse = reorder = inc = true;
			}

			zeroNew.setEnabled(zero);
			zeroNewStop.setEnabled(zero && zeroNew.getValue());

			sparseFiles.setEnabled(sparse);

			pieceReorder.setEnabled(reorder);
			minMB.setEnabled(reorder && pieceReorder.getValue());

			incremental.setEnabled(inc);
		};

		for (Parameter p : new Parameter[] {
			zeroNew,
			zeroNewStop,
			sparseFiles,
			pieceReorder,
			incremental
		}) {
			p.addListener(listener);
		}
		listener.parameterChanged(null);

		// truncate too large
		BooleanParameterImpl truncateLarge = new BooleanParameterImpl(
				BCFG_FILE_TRUNCATE_IF_TOO_LARGE,
				"ConfigView.section.file.truncate.too.large");
		add(truncateLarge, Parameter.MODE_INTERMEDIATE);

		// merge files of same size

		BooleanParameterImpl mergeSameSize = new BooleanParameterImpl(
				BCFG_MERGE_SAME_SIZE_FILES, "ConfigView.section.file.merge.same.size");
		add(mergeSameSize, Parameter.MODE_INTERMEDIATE);

		// merge extended
		BooleanParameterImpl mergeSameSizeExt = new BooleanParameterImpl(
				BCFG_MERGE_SAME_SIZE_FILES_EXTENDED,
				"ConfigView.section.file.merge.same.size.extended");
		add(mergeSameSizeExt, Parameter.MODE_INTERMEDIATE);
		mergeSameSizeExt.setIndent(1, true);

		mergeSameSize.addEnabledOnSelection(mergeSameSizeExt);

		// merge tolerance
		IntParameterImpl tol = new IntParameterImpl(
				ICFG_MERGE_SAME_SIZE_FILES_TOLERANCE,
				"ConfigView.section.file.merge.same.size.tolerance", 0, Integer.MAX_VALUE );
		add(tol, Parameter.MODE_INTERMEDIATE);
		tol.setIndent(1, true);

		mergeSameSize.addEnabledOnSelection(tol);
		
		// min merge pieces
		IntParameterImpl minmerge = new IntParameterImpl(
				ICFG_MERGE_SAME_SIZE_FILES_MIN_PIECES,
				"ConfigView.section.file.merge.min.pieces", 0, Integer.MAX_VALUE );
		add(minmerge, Parameter.MODE_INTERMEDIATE);
		minmerge.setIndent(1, true);

		mergeSameSize.addEnabledOnSelection(minmerge);
		
		
		
		// check on complete
		BooleanParameterImpl checkPiecesOnCompletion = new BooleanParameterImpl(
				BCFG_CHECK_PIECES_ON_COMPLETION, "ConfigView.label.checkOncompletion");
		add(checkPiecesOnCompletion, Parameter.MODE_INTERMEDIATE);

		// check on complete
		BooleanParameterImpl seedingPieceCheckRecheck = new BooleanParameterImpl(
				BCFG_SEEDING_PIECE_CHECK_RECHECK_ENABLE,
				"ConfigView.label.checkOnSeeding");
		add(seedingPieceCheckRecheck, Parameter.MODE_INTERMEDIATE);

		BooleanParameterImpl fileStrictLocking = new BooleanParameterImpl(
				BCFG_FILE_STRICT_LOCKING, "ConfigView.label.strictfilelocking");
		add(fileStrictLocking, Parameter.MODE_INTERMEDIATE);

		// max links

		IntParameterImpl maxFileLinksSupported = new IntParameterImpl(
				ICFG_MAX_FILE_LINKS_SUPPORTED, "ConfigView.label.max.file.links", 8,
				Integer.MAX_VALUE);
		add(maxFileLinksSupported, Parameter.MODE_INTERMEDIATE);
		maxFileLinksSupported.setSuffixLabelKey(
				"ConfigView.label.max.file.links.warning");

		// restart out of space downloads

		BooleanParameterImpl OOSDRE = new BooleanParameterImpl(
				BCFG_INSUFFICIENT_SPACE_DOWNLOAD_RESTART,
				"ConfigView.label.restart.no.space.dls");
		add(OOSDRE, Parameter.MODE_INTERMEDIATE);

		IntParameterImpl OOSDRInterval = new IntParameterImpl(
				ICFG_INSUFFICIENT_SPACE_DOWNLOAD_RESTART_MINS,
				"ConfigView.label.restart.no.space.dls.interval", 1, Integer.MAX_VALUE);
		add(OOSDRInterval, Parameter.MODE_INTERMEDIATE);
		OOSDRInterval.setIndent(1, true);
		OOSDRInterval.setSuffixLabelKey("ConfigView.text.minutes");

		OOSDRE.addEnabledOnSelection(OOSDRInterval);

		// restart missing file downloads

		BooleanParameterImpl MFDRE = new BooleanParameterImpl(
				BCFG_MISSING_FILE_DOWNLOAD_RESTART,
				"ConfigView.label.restart.missing.file.dls");
		add(MFDRE, Parameter.MODE_INTERMEDIATE);

		IntParameterImpl MFDRInterval = new IntParameterImpl(
				ICFG_MISSING_FILE_DOWNLOAD_RESTART_MINS,
				"ConfigView.label.restart.no.space.dls.interval", 1, Integer.MAX_VALUE);
		add(MFDRInterval, Parameter.MODE_INTERMEDIATE);
		MFDRInterval.setIndent(1, true);
		MFDRInterval.setSuffixLabelKey("ConfigView.text.minutes");

		MFDRE.addEnabledOnSelection(MFDRInterval);
	
		// resume

		List<Parameter> listResume = new ArrayList<>();

		IntParameterImpl paramSaveInterval = new IntParameterImpl(
				ICFG_SAVE_RESUME_INTERVAL, "ConfigView.label.saveresumeinterval");
		add(paramSaveInterval, Parameter.MODE_INTERMEDIATE, listResume);
		paramSaveInterval.setSuffixLabelKey("ConfigView.text.minutes");

		BooleanParameterImpl recheck_all = new BooleanParameterImpl(
				BCFG_ON_RESUME_RECHECK_ALL,
				"ConfigView.section.file.resume.recheck.all");
		add(recheck_all, Parameter.MODE_INTERMEDIATE, listResume);

		// save peers

		BooleanParameterImpl save_peers = new BooleanParameterImpl(
				BCFG_FILE_SAVE_PEERS_ENABLE,
				"ConfigView.section.file.save.peers.enable");
		add(save_peers, Parameter.MODE_INTERMEDIATE, listResume);

		// save peers max

		IntParameterImpl savePeersMax = new IntParameterImpl(
				ICFG_FILE_SAVE_PEERS_MAX, "ConfigView.section.file.save.peers.max");
		add(savePeersMax, Parameter.MODE_INTERMEDIATE, listResume);
		savePeersMax.setSuffixLabelKey(
				"ConfigView.section.file.save.peers.pertorrent");

		ParameterGroupImpl pgResumeGroup = new ParameterGroupImpl("v3.MainWindow.button.resume",
				listResume);
		add("pgResumeGroup", pgResumeGroup);
		//pgResumeGroup.setIndent(1, false);

		save_peers.addEnabledOnSelection(savePeersMax);

		// disable interim state save 
		BooleanParameterImpl bDisableSveInterim = new BooleanParameterImpl(BCFG_DISABLE_SAVE_INTERIM_DOWNLOAD_STATE,
				"ConfigView.label.disableinterimstatesave");
		add(bDisableSveInterim, Parameter.MODE_ADVANCED );
		
			// skip complete download file existance checks 
		
		BooleanParameterImpl bSkipCompDLFileChecks = new BooleanParameterImpl(BCFG_SKIP_COMP_DL_FILE_CHECKS,
				"ConfigView.label.skipCompDLFileChecks");
		add(bSkipCompDLFileChecks, Parameter.MODE_ADVANCED );

		List<Parameter> listExt = new ArrayList<>();

		StringParameterImpl priorityExtensions = new StringParameterImpl(
				SCFG_PRIORITY_EXTENSIONS, "ConfigView.label.priorityExtensions");
		add(priorityExtensions, Parameter.MODE_INTERMEDIATE);

		BooleanParameterImpl priorityExtensionsIgnoreCase = new BooleanParameterImpl(
				BCFG_PRIORITY_EXTENSIONS_IGNORE_CASE, "ConfigView.label.ignoreCase");
		add(priorityExtensionsIgnoreCase, Parameter.MODE_INTERMEDIATE);

		ParameterGroupImpl pgPriorityExt = new ParameterGroupImpl(null,	priorityExtensions, priorityExtensionsIgnoreCase);
		pgPriorityExt.setReferenceID(REFID_TORRENT_ADD_AUTO_PRIORITY);
		add("pgPriorityExt", pgPriorityExt, listExt);
		pgPriorityExt.setNumberOfColumns(2);

		add(new StringParameterImpl(SCFG_FILE_AUTO_SEQUENTIAL_EXTS,
				"ConfigView.label.sequential.exts"), Parameter.MODE_INTERMEDIATE,
				listExt);
		
		// quick view

		StringParameterImpl quickViewExts = new StringParameterImpl(
				SCFG_QUICK_VIEW_EXTS, "ConfigView.label.quickviewexts");
		add(quickViewExts, Parameter.MODE_INTERMEDIATE);

		IntParameterImpl quickViewMaxKB = new IntParameterImpl(
				ICFG_QUICK_VIEW_MAXKB, "ConfigView.label.quickviewmaxkb", 1, 9999);
		add(quickViewMaxKB, Parameter.MODE_INTERMEDIATE);

		ParameterGroupImpl pgQV = new ParameterGroupImpl(null, quickViewExts,
				quickViewMaxKB);
		
		add("pgQV", pgQV, listExt);
		pgQV.setNumberOfColumns(2);
		
		// rename incomplete files

		BooleanParameterImpl rename_incomplete = new BooleanParameterImpl(
				BCFG_RENAME_INCOMPLETE_FILES,
				"ConfigView.section.file.rename.incomplete");
		add(rename_incomplete, Parameter.MODE_INTERMEDIATE);

		StringParameterImpl rename_incomplete_ext = new StringParameterImpl(
				SCFG_RENAME_INCOMPLETE_FILES_EXTENSION, null);
		add(rename_incomplete_ext, Parameter.MODE_INTERMEDIATE);

		rename_incomplete.addEnabledOnSelection(rename_incomplete_ext);

		ParameterGroupImpl pgExtRename = new ParameterGroupImpl(null,
				rename_incomplete, rename_incomplete_ext);
		pgExtRename.setNumberOfColumns(2);
		add("pgExtRename", pgExtRename, listExt);

		// put 'dnd' files in subdir

		BooleanParameterImpl enable_subfolder = new BooleanParameterImpl(
				BCFG_ENABLE_SUBFOLDER_FOR_DND_FILES,
				"ConfigView.section.file.subfolder.dnd");
		add(enable_subfolder, Parameter.MODE_INTERMEDIATE);

		StringParameterImpl subfolder_name = new StringParameterImpl(
				SCFG_SUBFOLDER_FOR_DND_FILES, null);
		add(subfolder_name, Parameter.MODE_INTERMEDIATE);

		enable_subfolder.addEnabledOnSelection(subfolder_name);

		ParameterGroupImpl pgExtSubFolder = new ParameterGroupImpl(null,
				enable_subfolder, subfolder_name);
		pgExtSubFolder.setNumberOfColumns(2);
		add("pgExtSubFolder", pgExtSubFolder, listExt);

		// dnd prefix

		BooleanParameterImpl enable_dndprefix = new BooleanParameterImpl(
				BCFG_USE_INCOMPLETE_FILE_PREFIX,
				"ConfigView.section.file.dnd.prefix.enable");
		add(enable_dndprefix, Parameter.MODE_INTERMEDIATE, listExt);

		ParameterListener prefixListener = param -> enable_dndprefix.setEnabled(
				enable_subfolder.getValue() || rename_incomplete.getValue());
		enable_subfolder.addListener(prefixListener);
		rename_incomplete.addListener(prefixListener);
		prefixListener.parameterChanged(null);

		ParameterGroupImpl pgFileExt = new ParameterGroupImpl(
				"ConfigView.group.FileExtensions", listExt);
		add("pgFileExt", pgFileExt, Parameter.MODE_INTERMEDIATE);

		// download history

		add(new BooleanParameterImpl(BCFG_DOWNLOAD_HISTORY_ENABLED,
				"ConfigView.label.record.dl.history"));

		// ignore group

		List<Parameter> listIgnoredFiles = new ArrayList<>();

		// torrent add auto-skip file types

		StringParameterImpl paramSkipExtensions = new StringParameterImpl(
				SCFG_FILE_TORRENT_AUTO_SKIP_EXTENSIONS,
				"ConfigView.section.file.torrent.autoskipfiles");
		add(paramSkipExtensions, Parameter.MODE_INTERMEDIATE, listIgnoredFiles);

		// torrent add auto-skip file names

		StringParameterImpl paramSkipFiles = new StringParameterImpl(
				SCFG_FILE_TORRENT_AUTO_SKIP_FILES,
				"ConfigView.section.file.torrent.autoskipfilenames");
		add(paramSkipFiles, Parameter.MODE_INTERMEDIATE);

		BooleanParameterImpl paramSkipFilesRegex = new BooleanParameterImpl(
				BCFG_FILE_TORRENT_AUTO_SKIP_FILES_REG_EXP, "label.regexps");
		add(paramSkipFilesRegex, Parameter.MODE_INTERMEDIATE);

		ParameterGroupImpl pgSkipFileNames = new ParameterGroupImpl(null,
				paramSkipFiles, paramSkipFilesRegex);
		pgSkipFileNames.setNumberOfColumns(2);
		add("pgSkipFileNames", pgSkipFileNames, Parameter.MODE_INTERMEDIATE,
				listIgnoredFiles);

		// torrent add auto-skip min size

		IntParameterImpl paramAutoSkipMinSize = new IntParameterImpl(
				ICFG_FILE_TORRENT_AUTO_SKIP_MIN_SIZE_KB,
				"ConfigView.section.file.torrent.autoskipfilesminsize", 0,
				Integer.MAX_VALUE);
		add(paramAutoSkipMinSize, Parameter.MODE_INTERMEDIATE, listIgnoredFiles);

		// torrent create/delete ignore files

		StringParameterImpl ignoreCreateDelete = new StringParameterImpl(
				SCFG_FILE_TORRENT_IGNORE_FILES,
				"ConfigView.section.file.torrent.ignorefiles");
		add(ignoreCreateDelete, Parameter.MODE_INTERMEDIATE, listIgnoredFiles);

		ParameterGroupImpl pgIgnoredFiles = new ParameterGroupImpl(
				"ConfigView.section.file.ignore.section", listIgnoredFiles);
		add("pgIgnoredFiles", pgIgnoredFiles);
		pgIgnoredFiles.setReferenceID(REFID_TORRENT_ADD_AUTO_SKIP);

		// file name character mappings

		StringParameterImpl paramCharConversions = new StringParameterImpl(
				SCFG_FILE_CHARACTER_CONVERSIONS,
				"ConfigView.section.file.char.conversions");
		add(paramCharConversions, Parameter.MODE_ADVANCED);
		// hax
		paramCharConversions.setWidthInCharacters(14);
		String[] split = MessageText.getString(
				"ConfigView.section.file.char.conversions").split("\\n\\s+", 2);
		paramCharConversions.setLabelText(split[0]);
		if (split.length > 1) {
			paramCharConversions.setSuffixLabelText(split[1]);
		}

		// File Deletetion Group

		List<Parameter> listDeletion = new ArrayList<>();

		int[] values = {
			0,
			1,
			2,
		};
		String[] labels = {
			MessageText.getString("ConfigView.tb.delete.ask"),
			MessageText.getString("ConfigView.tb.delete.content"),
			MessageText.getString("ConfigView.tb.delete.torrent"),
		};
		IntListParameterImpl paramConfirmDelete = new IntListParameterImpl(
				ICFG_TB_CONFIRM_DELETE_CONTENT, "ConfigView.section.file.tb.delete",
				values, labels);
		paramConfirmDelete.setAllowedUiTypes(UIInstance.UIT_SWT);
		add(paramConfirmDelete, Parameter.MODE_INTERMEDIATE, listDeletion);

		BooleanParameterImpl paramDeleteTorrentFile = new BooleanParameterImpl(
				BCFG_DEF_DELETETORRENT, "ConfigView.section.file.delete.torrent");
		paramDeleteTorrentFile.setAllowedUiTypes(UIInstance.UIT_SWT);
		add(paramDeleteTorrentFile, Parameter.MODE_INTERMEDIATE, listDeletion);

		BooleanParameterImpl paramDeleteAllDefault = new BooleanParameterImpl(
				BCFG_DEF_DELETEALLSELECTED, "ConfigView.section.file.delete.all.selected");
		paramDeleteAllDefault.setAllowedUiTypes(UIInstance.UIT_SWT);
		add(paramDeleteAllDefault, Parameter.MODE_INTERMEDIATE, listDeletion);

		
		try {
			final PlatformManager platform = PlatformManagerFactory.getPlatformManager();

			if (platform.hasCapability(
					PlatformManagerCapabilities.RecoverableFileDelete)) {

				BooleanParameterImpl paramRecycleBin = new BooleanParameterImpl(
						BCFG_MOVE_DELETED_DATA_TO_RECYCLE_BIN,
						"ConfigView.section.file.nativedelete");
				add(paramRecycleBin, listDeletion);

			}
		} catch (Throwable ignored) {

		}

		add(new BooleanParameterImpl(
				BCFG_FILE_DELETE_INCLUDE_FILES_OUTSIDE_SAVE_DIR,
				"ConfigView.section.file.delete.include_files_outside_save_dir"),
				listDeletion);

		add(new BooleanParameterImpl(BCFG_DELETE_PARTIAL_FILES_ON_LIBRARY_REMOVAL,
				"delete.partial.files"), listDeletion);

		ParameterGroupImpl pgDeletion = new ParameterGroupImpl(
				"ConfigView.section.file.deletion.section", listDeletion);
		add("pgDeletion", pgDeletion);

		// Configuration directory information.
		HyperlinkParameterImpl paramUserPath = new HyperlinkParameterImpl(
				"!" + SystemProperties.getUserPath() + "!",
				"ConfigView.section.file.config.currentdir",
				SystemProperties.getUserPath());
		add(paramUserPath);

		BooleanParameterImpl paramBackupConfig = new BooleanParameterImpl(
				BCFG_USE_CONFIG_FILE_BACKUPS, "ConfigView.label.backupconfigfiles");
		add(paramBackupConfig);

		add("pgConfig",
				new ParameterGroupImpl("ConfigView.section.file.config.section",
						paramBackupConfig, paramUserPath));

	}
}
