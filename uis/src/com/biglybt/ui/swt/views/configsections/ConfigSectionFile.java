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

import java.util.ArrayList;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.impl.ConfigurationDefaults;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.SystemProperties;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.platform.PlatformManager;
import com.biglybt.platform.PlatformManagerCapabilities;
import com.biglybt.platform.PlatformManagerFactory;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.LinkLabel;
import com.biglybt.ui.swt.config.*;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.pif.UISWTConfigSection;

public class ConfigSectionFile
	implements UISWTConfigSection
{
	@Override
	public String configSectionGetParentSection() {
		return ConfigSection.SECTION_ROOT;
	}

	@Override
	public String configSectionGetName() {
		return ConfigSection.SECTION_FILES;
	}

	@Override
	public void configSectionSave() {
	}

	@Override
	public int maxUserMode() {
		return 2;
	}

	@Override
	public void configSectionDelete() {
		ImageLoader imageLoader = ImageLoader.getInstance();
		imageLoader.releaseImage("openFolderButton");
	}

	@Override
	public Composite configSectionCreate(final Composite parent) {
		ImageLoader imageLoader = ImageLoader.getInstance();
		Image imgOpenFolder = imageLoader.getImage("openFolderButton");

		GridData gridData;
		Label label;
		String sCurConfigID;
		final ArrayList<String> allConfigIDs = new ArrayList<>();

		Composite gFile = new Composite(parent, SWT.NULL);

		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		layout.marginHeight = 0;
		gFile.setLayout(layout);

		int userMode = COConfigurationManager.getIntParameter("User Mode");

		// Default Dir Section
		final Group gDefaultDir = new Group(gFile, SWT.NONE);
		Messages.setLanguageText(gDefaultDir,
				"ConfigView.section.file.defaultdir.section");
		layout = new GridLayout();
		layout.numColumns = 3;
		gDefaultDir.setLayout(layout);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 2;
		Utils.setLayoutData(gDefaultDir, gridData);

		if (userMode > 0) {
			// def dir: best guess
			sCurConfigID = "DefaultDir.BestGuess";
			allConfigIDs.add(sCurConfigID);
			BooleanParameter bestGuess = new BooleanParameter(gDefaultDir,
					sCurConfigID, "ConfigView.section.file.defaultdir.bestguess");
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			gridData.horizontalSpan = 3;
			bestGuess.setLayoutData(gridData);
		}

		// Save Path
		sCurConfigID = "Default save path";
		allConfigIDs.add(sCurConfigID);
		Label lblDefaultDir = new Label(gDefaultDir, SWT.NONE);
		Messages.setLanguageText(lblDefaultDir,
				"ConfigView.section.file.defaultdir.ask");
		Utils.setLayoutData(lblDefaultDir, new GridData());

		gridData = new GridData(GridData.FILL_HORIZONTAL);
		final StringParameter pathParameter = new StringParameter(gDefaultDir,
				sCurConfigID);
		pathParameter.setLayoutData(gridData);

		Button browse = new Button(gDefaultDir, SWT.PUSH);
		browse.setImage(imgOpenFolder);
		imgOpenFolder.setBackground(browse.getBackground());
		browse.setToolTipText(MessageText.getString("ConfigView.button.browse"));

		browse.addListener(SWT.Selection, new Listener() {
			/* (non-Javadoc)
			 * @see org.eclipse.swt.widgets.Listener#handleEvent(org.eclipse.swt.widgets.Event)
			 */
			@Override
			public void handleEvent(Event event) {
				DirectoryDialog dialog = new DirectoryDialog(parent.getShell(),
						SWT.APPLICATION_MODAL);
				dialog.setFilterPath(pathParameter.getValue());
				dialog.setMessage(MessageText.getString("ConfigView.dialog.choosedefaultsavepath"));
				dialog.setText(MessageText.getString("ConfigView.section.file.defaultdir.ask"));
				String path = dialog.open();
				if (path != null) {
					pathParameter.setValue(path);
				}
			}
		});

		// def dir: autoSave
		sCurConfigID = ConfigurationDefaults.CFG_TORRENTADD_OPENOPTIONS;
		allConfigIDs.add(sCurConfigID);
		Composite cOpenOptions = new Composite(gDefaultDir, SWT.NONE);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 3;
		Utils.setLayoutData(cOpenOptions, gridData);
		RowLayout rowLayout = new RowLayout();
		rowLayout.marginBottom = rowLayout.marginLeft = rowLayout.marginRight = rowLayout.marginTop = 0;
		rowLayout.center = true;
		cOpenOptions.setLayout(rowLayout);

		label = new Label(cOpenOptions, SWT.NULL);
		Messages.setLanguageText(label, "ConfigView.section.file.showopentorrentoptions");
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
		new StringListParameter(cOpenOptions, sCurConfigID, openLabels, openValues);

		label = new Label(cOpenOptions, SWT.NULL);
		label.setText( "    " );
		sCurConfigID = "ui.addtorrent.openoptions.sep";
		new BooleanParameter(cOpenOptions,
				sCurConfigID, "ConfigView.section.file.showopentorrentoptions.sep");



		if (userMode > 0) {
			// def dir: autoSave -> auto-rename
			sCurConfigID = "DefaultDir.AutoSave.AutoRename";
			allConfigIDs.add(sCurConfigID);
			BooleanParameter autoSaveAutoRename = new BooleanParameter(gDefaultDir,
					sCurConfigID, "ConfigView.section.file.defaultdir.autorename");
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			gridData.horizontalSpan = 3;
			autoSaveAutoRename.setLayoutData(gridData);
			//IAdditionalActionPerformer aapDefaultDirStuff3 = new ChangeSelectionActionPerformer(
			//		autoSaveAutoRename.getControls(), false);

			final Composite cHistory = new Composite(gDefaultDir, SWT.NONE);

			layout = new GridLayout();
			layout.numColumns = 6;
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			cHistory.setLayout(layout);
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			gridData.horizontalSpan = 3;
			Utils.setLayoutData(cHistory, gridData);

			// def dir: auto update
			sCurConfigID = "DefaultDir.AutoUpdate";
			allConfigIDs.add(sCurConfigID);
			BooleanParameter autoUpdateSaveDir = new BooleanParameter(cHistory,
					sCurConfigID, "ConfigView.section.file.defaultdir.lastused");

			Label padLabel = new Label( cHistory, SWT.NULL );
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			Utils.setLayoutData(padLabel, gridData);

			sCurConfigID = "saveTo_list.max_entries";
			allConfigIDs.add(sCurConfigID);
			Label historyMax = new Label(cHistory, SWT.NULL);
			Messages.setLanguageText(historyMax,"ConfigView.label.save_list.max_entries");

			IntParameter paramhistoryMax = new IntParameter(cHistory,	sCurConfigID);

			Label historyReset = new Label(cHistory, SWT.NULL);
			Messages.setLanguageText(historyReset,"ConfigView.label.save_list.clear");

			final Button clear_history_button = new Button(cHistory, SWT.PUSH);
			Messages.setLanguageText(clear_history_button, "Button.clear" );

			clear_history_button.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					COConfigurationManager.setParameter("saveTo_list", new ArrayList());
					clear_history_button.setEnabled( false );
				}
			});

			java.util.List<String> dirList = COConfigurationManager.getStringListParameter("saveTo_list");

			clear_history_button.setEnabled( dirList.size() > 0 );
		}

		label = new Label(gFile, SWT.NONE);
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		label.setLayoutData(gridData);

		////////////////////

		sCurConfigID = "XFS Allocation";
		allConfigIDs.add(sCurConfigID);
		if (userMode > 0 && !Constants.isWindows) {
			BooleanParameter xfsAllocation = new BooleanParameter(gFile,
					sCurConfigID, "ConfigView.label.xfs.allocation");
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			xfsAllocation.setLayoutData(gridData);
		}

		BooleanParameter zeroNew = null;

		sCurConfigID = "Zero New";
		allConfigIDs.add(sCurConfigID);
		if (userMode > 0) {
			// zero new files
			zeroNew = new BooleanParameter(gFile, sCurConfigID,
					"ConfigView.label.zeronewfiles");
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			zeroNew.setLayoutData(gridData);
		}

		BooleanParameter pieceReorder = null;

		sCurConfigID = "Enable reorder storage mode";
		allConfigIDs.add(sCurConfigID);
		if (userMode > 0) {

			pieceReorder = new BooleanParameter(gFile, sCurConfigID,
					"ConfigView.label.piecereorder");
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			pieceReorder.setLayoutData(gridData);

			//Make the reorder checkbox (button) deselect when zero new is used
			Button[] btnReorder = {
				(Button) pieceReorder.getControl()
			};
			zeroNew.setAdditionalActionPerformer(new ExclusiveSelectionActionPerformer(
					btnReorder));

			//Make the zero new checkbox(button) deselct when reorder is used
			Button[] btnZeroNew = {
				(Button) zeroNew.getControl()
			};
			pieceReorder.setAdditionalActionPerformer(new ExclusiveSelectionActionPerformer(
					btnZeroNew));
		}

		sCurConfigID = "Reorder storage mode min MB";
		allConfigIDs.add(sCurConfigID);

		if (userMode > 0) {
			Label lblMinMB = new Label(gFile, SWT.NULL);
			Messages.setLanguageText(lblMinMB, "ConfigView.label.piecereorderminmb");
			gridData = new GridData();
			gridData.horizontalIndent = 25;
			Utils.setLayoutData(lblMinMB, gridData);

			IntParameter minMB = new IntParameter(gFile, sCurConfigID);
			gridData = new GridData();
			minMB.setLayoutData(gridData);

			pieceReorder.setAdditionalActionPerformer(new ChangeSelectionActionPerformer(
					lblMinMB));
			pieceReorder.setAdditionalActionPerformer(new ChangeSelectionActionPerformer(
					minMB));
		}

		sCurConfigID = "Enable incremental file creation";
		allConfigIDs.add(sCurConfigID);
		if (userMode > 0) {
			// incremental file creation
			BooleanParameter incremental = new BooleanParameter(gFile, sCurConfigID,
					"ConfigView.label.incrementalfile");
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			incremental.setLayoutData(gridData);

			//Make the incremental checkbox (button) deselect when zero new is used
			Button[] btnIncremental = {
				(Button) incremental.getControl()
			};
			zeroNew.setAdditionalActionPerformer(new ExclusiveSelectionActionPerformer(
					btnIncremental));

			//Make the zero new checkbox(button) deselct when incremental is used
			Button[] btnZeroNew = {
				(Button) zeroNew.getControl()
			};
			incremental.setAdditionalActionPerformer(new ExclusiveSelectionActionPerformer(
					btnZeroNew));
		}

			// truncate too large

		sCurConfigID = "File.truncate.if.too.large";
		allConfigIDs.add(sCurConfigID);
		if (userMode > 0) {
			// truncate too large
			BooleanParameter truncateLarge = new BooleanParameter(gFile,
					sCurConfigID, "ConfigView.section.file.truncate.too.large");
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			truncateLarge.setLayoutData(gridData);
		}

			// merge files of same size

		sCurConfigID = "Merge Same Size Files";
		allConfigIDs.add(sCurConfigID);
		BooleanParameter mergeSameSize = null;

		if (userMode > 0) {
			mergeSameSize = new BooleanParameter(gFile,
					sCurConfigID, "ConfigView.section.file.merge.same.size");
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			mergeSameSize.setLayoutData(gridData);
		}

			// merge extended

		sCurConfigID = "Merge Same Size Files Extended";
		allConfigIDs.add(sCurConfigID);
		if (mergeSameSize != null) {
			BooleanParameter mergeSameSizeExt = new BooleanParameter(gFile,
					sCurConfigID, "ConfigView.section.file.merge.same.size.extended");
			gridData = new GridData();
			gridData.horizontalIndent = 25;
			gridData.horizontalSpan = 2;
			mergeSameSizeExt.setLayoutData(gridData);

			IAdditionalActionPerformer mergeAP = new ChangeSelectionActionPerformer(
					mergeSameSizeExt.getControls(), false);
			mergeSameSize.setAdditionalActionPerformer(mergeAP);
		}

			// recheck on complete

		sCurConfigID = "Check Pieces on Completion";
		allConfigIDs.add(sCurConfigID);
		if (userMode > 0) {
			// check on complete
			BooleanParameter checkOnComp = new BooleanParameter(gFile, sCurConfigID,
					"ConfigView.label.checkOncompletion");
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			checkOnComp.setLayoutData(gridData);
		}

		sCurConfigID = "Seeding Piece Check Recheck Enable";
		allConfigIDs.add(sCurConfigID);
		if (userMode > 0) {
			// check on complete
			BooleanParameter checkOnSeeding = new BooleanParameter(gFile,
					sCurConfigID, "ConfigView.label.checkOnSeeding");
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			checkOnSeeding.setLayoutData(gridData);
		}

		sCurConfigID = "File.strict.locking";
		allConfigIDs.add(sCurConfigID);
		if (userMode > 1) {

			BooleanParameter strictLocking = new BooleanParameter(gFile,
					sCurConfigID, "ConfigView.label.strictfilelocking");
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			strictLocking.setLayoutData(gridData);
		}

		if (userMode == 0) {
			allConfigIDs.add("Use Resume");
			sCurConfigID = "Save Resume Interval";
			allConfigIDs.add(sCurConfigID);
			sCurConfigID = "On Resume Recheck All";
			allConfigIDs.add(sCurConfigID);
			sCurConfigID = "File.save.peers.enable";
			allConfigIDs.add(sCurConfigID);
			sCurConfigID = "File.save.peers.max";
			allConfigIDs.add(sCurConfigID);
		} else {

				// max links


			Composite maxLinksGroup = new Composite(gFile, SWT.NULL);
			layout = new GridLayout();
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			layout.numColumns = 3;
			maxLinksGroup.setLayout(layout);
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			gridData.horizontalSpan = 2;
			Utils.setLayoutData(maxLinksGroup, gridData);

			Label maxLinks = new Label(maxLinksGroup, SWT.NULL);
			Messages.setLanguageText(maxLinks, "ConfigView.label.max.file.links");

			sCurConfigID = "Max File Links Supported";
			allConfigIDs.add(sCurConfigID);

			new IntParameter(maxLinksGroup,	sCurConfigID,8,Integer.MAX_VALUE);


			Label maxLinksWarning = new Label(maxLinksGroup, SWT.NULL);
			Messages.setLanguageText(maxLinksWarning, "ConfigView.label.max.file.links.warning");
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			maxLinksWarning.setLayoutData(gridData);

				// restart out of space downloads

			sCurConfigID = "Insufficient Space Download Restart Enable";
			allConfigIDs.add(sCurConfigID);
			// resume data
			final BooleanParameter OOSDRE = new BooleanParameter(gFile,
					sCurConfigID, "ConfigView.label.restart.no.space.dls");
			gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
			gridData.horizontalSpan = 2;
			OOSDRE.setLayoutData(gridData);

			Composite cOOSDGroup = new Composite(gFile, SWT.NULL);
			layout = new GridLayout();
			layout.marginHeight = 0;
			layout.marginWidth = 4;
			layout.numColumns = 3;
			cOOSDGroup.setLayout(layout);
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			gridData.horizontalIndent = 25;
			gridData.horizontalSpan = 2;
			Utils.setLayoutData(cOOSDGroup, gridData);

			sCurConfigID = "Insufficient Space Download Restart Period";
			allConfigIDs.add(sCurConfigID);
			Label lblOOSDRInterval = new Label(cOOSDGroup, SWT.NULL);
			Messages.setLanguageText(lblOOSDRInterval,
					"ConfigView.label.restart.no.space.dls.interval");

			IntParameter paramOOSDRInterval = new IntParameter(cOOSDGroup,
					sCurConfigID,1,Integer.MAX_VALUE);
			gridData = new GridData();
			paramOOSDRInterval.setLayoutData(gridData);

			Label lblOOSDRMinutes = new Label(cOOSDGroup, SWT.NULL);
			Messages.setLanguageText(lblOOSDRMinutes, "ConfigView.text.minutes");

			final Control[] OOSDRContrls = { cOOSDGroup };

			IAdditionalActionPerformer OOSDREnabler =
				new GenericActionPerformer( OOSDRContrls )
				{
					@Override
					public void performAction() {
						controlsSetEnabled(controls, OOSDRE.isSelected());
					}
				};

			OOSDRE.setAdditionalActionPerformer(OOSDREnabler);

				// use resume

			sCurConfigID = "Use Resume";
			allConfigIDs.add(sCurConfigID);
			// resume data
			final BooleanParameter bpUseResume = new BooleanParameter(gFile,
					sCurConfigID, "ConfigView.label.usefastresume");
			bpUseResume.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_BEGINNING));

			Composite cResumeGroup = new Composite(gFile, SWT.NULL);
			layout = new GridLayout();
			layout.marginHeight = 0;
			layout.marginWidth = 4;
			layout.numColumns = 3;
			cResumeGroup.setLayout(layout);
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			gridData.horizontalIndent = 25;
			gridData.horizontalSpan = 2;
			Utils.setLayoutData(cResumeGroup, gridData);

			sCurConfigID = "Save Resume Interval";
			allConfigIDs.add(sCurConfigID);
			Label lblSaveResumeInterval = new Label(cResumeGroup, SWT.NULL);
			Messages.setLanguageText(lblSaveResumeInterval,
					"ConfigView.label.saveresumeinterval");

			IntParameter paramSaveInterval = new IntParameter(cResumeGroup,
					sCurConfigID);
			gridData = new GridData();
			paramSaveInterval.setLayoutData(gridData);

			Label lblMinutes = new Label(cResumeGroup, SWT.NULL);
			Messages.setLanguageText(lblMinutes, "ConfigView.text.minutes");

			// save peers

			sCurConfigID = "On Resume Recheck All";
			allConfigIDs.add(sCurConfigID);
			final BooleanParameter recheck_all = new BooleanParameter(cResumeGroup,
					sCurConfigID, "ConfigView.section.file.resume.recheck.all");
			gridData = new GridData();
			gridData.horizontalSpan = 3;
			recheck_all.setLayoutData(gridData);
			// save peers

			sCurConfigID = "File.save.peers.enable";
			allConfigIDs.add(sCurConfigID);
			final BooleanParameter save_peers = new BooleanParameter(cResumeGroup,
					sCurConfigID, "ConfigView.section.file.save.peers.enable");
			gridData = new GridData();
			gridData.horizontalSpan = 3;
			save_peers.setLayoutData(gridData);

			// save peers max

			sCurConfigID = "File.save.peers.max";
			allConfigIDs.add(sCurConfigID);
			final Label lblSavePeersMax = new Label(cResumeGroup, SWT.NULL);
			Messages.setLanguageText(lblSavePeersMax,
					"ConfigView.section.file.save.peers.max");
			final IntParameter savePeersMax = new IntParameter(cResumeGroup,
					sCurConfigID);
			gridData = new GridData();
			savePeersMax.setLayoutData(gridData);
			final Label lblPerTorrent = new Label(cResumeGroup, SWT.NULL);
			Messages.setLanguageText(lblPerTorrent,
					"ConfigView.section.file.save.peers.pertorrent");

			final Control[] controls = {
				cResumeGroup
			};

			/*
			IAdditionalActionPerformer performer = new ChangeSelectionActionPerformer(controls);
			bpUseResume.setAdditionalActionPerformer(performer);
			*/

			IAdditionalActionPerformer f_enabler = new GenericActionPerformer(
					controls) {
				@Override
				public void performAction() {
					controlsSetEnabled(controls, bpUseResume.isSelected());

					if (bpUseResume.isSelected()) {
						lblSavePeersMax.setEnabled(save_peers.isSelected());
						savePeersMax.getControl().setEnabled(save_peers.isSelected());
						lblPerTorrent.setEnabled(save_peers.isSelected());
					}
				}
			};

			bpUseResume.setAdditionalActionPerformer(f_enabler);
			save_peers.setAdditionalActionPerformer(f_enabler);

		} //end usermode>0

		if (userMode > 0) {
			sCurConfigID = "priorityExtensions";
			allConfigIDs.add(sCurConfigID);

			// Auto-Prioritize
			label = new Label(gFile, SWT.WRAP);
			gridData = new GridData();
			gridData.widthHint = 180;
			Utils.setLayoutData(label, gridData);
			Messages.setLanguageText(label, "ConfigView.label.priorityExtensions");

			Composite cExtensions = new Composite(gFile, SWT.NULL);
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			Utils.setLayoutData(cExtensions, gridData);
			layout = new GridLayout();
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			layout.numColumns = 3;
			cExtensions.setLayout(layout);

			gridData = new GridData(GridData.FILL_HORIZONTAL);
			new StringParameter(cExtensions, sCurConfigID).setLayoutData(gridData);

			sCurConfigID = "priorityExtensionsIgnoreCase";
			allConfigIDs.add(sCurConfigID);
			new BooleanParameter(cExtensions, sCurConfigID,
					"ConfigView.label.ignoreCase");
		} else {
			sCurConfigID = "priorityExtensions";
			allConfigIDs.add(sCurConfigID);
			sCurConfigID = "priorityExtensionsIgnoreCase";
			allConfigIDs.add(sCurConfigID);
		}

		// quick view

		sCurConfigID = "quick.view.exts";
		allConfigIDs.add(sCurConfigID);

		label = new Label(gFile, SWT.WRAP);
		gridData = new GridData();
		gridData.widthHint = 180;
		Utils.setLayoutData(label, gridData);
		Messages.setLanguageText(label, "ConfigView.label.quickviewexts");

		Composite cQuickView = new Composite(gFile, SWT.NULL);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		Utils.setLayoutData(cQuickView, gridData);
		layout = new GridLayout();
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		layout.numColumns = 3;
		cQuickView.setLayout(layout);

		gridData = new GridData(GridData.FILL_HORIZONTAL);
		new StringParameter(cQuickView, sCurConfigID ).setLayoutData(gridData);

		label = new Label(cQuickView, SWT.NONE);
		Messages.setLanguageText(label, "ConfigView.label.quickviewmaxkb");

		sCurConfigID = "quick.view.maxkb";
		allConfigIDs.add(sCurConfigID);
		IntParameter qvmax = new IntParameter(cQuickView, sCurConfigID, 1, 9999 );


		// rename incomplete

		if (userMode > 0) {

				// rename incomplete files

			sCurConfigID = "Rename Incomplete Files";
			allConfigIDs.add(sCurConfigID);

			gridData = new GridData();
			gridData.horizontalSpan = 1;
			final BooleanParameter rename_incomplete = new BooleanParameter(gFile,
					sCurConfigID, "ConfigView.section.file.rename.incomplete");
			rename_incomplete.setLayoutData(gridData);

			sCurConfigID = "Rename Incomplete Files Extension";
			allConfigIDs.add(sCurConfigID);
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			StringParameter rename_incomplete_ext = new StringParameter(gFile,
					sCurConfigID);
			rename_incomplete_ext.setLayoutData(gridData);

			IAdditionalActionPerformer incompFileAP = new ChangeSelectionActionPerformer(
					rename_incomplete_ext.getControls(), false);
			rename_incomplete.setAdditionalActionPerformer(incompFileAP);

				// put 'dnd' files in subdir

			sCurConfigID = "Enable Subfolder for DND Files";
			allConfigIDs.add(sCurConfigID);

			gridData = new GridData();
			gridData.horizontalSpan = 1;
			final BooleanParameter enable_subfolder = new BooleanParameter(gFile,
					sCurConfigID, "ConfigView.section.file.subfolder.dnd");
			rename_incomplete.setLayoutData(gridData);

			sCurConfigID = "Subfolder for DND Files";
			allConfigIDs.add(sCurConfigID);
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			StringParameter subfolder_name = new StringParameter(gFile,
					sCurConfigID);
			subfolder_name.setLayoutData(gridData);

			IAdditionalActionPerformer subfolderAP = new ChangeSelectionActionPerformer(
					subfolder_name.getControls(), false);
			enable_subfolder.setAdditionalActionPerformer(subfolderAP);

				// dnd prefix

			sCurConfigID = "Use Incomplete File Prefix";
			allConfigIDs.add(sCurConfigID);

			gridData = new GridData();
			gridData.horizontalSpan = 2;
			gridData.horizontalIndent=25;
			final BooleanParameter enable_dndprefix = new BooleanParameter(gFile,
					sCurConfigID, "ConfigView.section.file.dnd.prefix.enable");
			enable_dndprefix.setLayoutData(gridData);

			ParameterChangeListener listener =
				new ParameterChangeAdapter()
				{
					@Override
					public void
					parameterChanged(
						Parameter	p,
						boolean		caused_internally )
					{
						enable_dndprefix.setEnabled( enable_subfolder.isSelected() || rename_incomplete.isSelected());
					}
				};

			enable_subfolder.addChangeListener( listener );
			rename_incomplete.addChangeListener( listener );

			listener.parameterChanged( null, true );
		}
				// download history

		sCurConfigID = "Download History Enabled";
		allConfigIDs.add(sCurConfigID);
		BooleanParameter recordDLHistory = new BooleanParameter(gFile, sCurConfigID,
				"ConfigView.label.record.dl.history");
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		recordDLHistory.setLayoutData(gridData);

		if ( userMode > 0 ){

			Group gIgnoredFiles = new Group(gFile, SWT.NONE);
			Messages.setLanguageText(gIgnoredFiles,
					"ConfigView.section.file.ignore.section");
			layout = new GridLayout();
			layout.numColumns = 2;
			layout.marginHeight = 5;
			gIgnoredFiles.setLayout(layout);
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			gridData.horizontalSpan = 2;
			Utils.setLayoutData(gIgnoredFiles, gridData);

				// torrent add auto-skip file types

			Label lSkipFiles = new Label(gIgnoredFiles, SWT.NULL);
			Messages.setLanguageText(lSkipFiles,
					"ConfigView.section.file.torrent.autoskipfiles");

			gridData = new GridData(GridData.FILL_HORIZONTAL);
			new StringParameter(gIgnoredFiles, "File.Torrent.AutoSkipExtensions").setLayoutData(gridData);

				// torrent add auto-skip min size

			Label lSkipFilesMinSize = new Label(gIgnoredFiles, SWT.NULL);
			Messages.setLanguageText(lSkipFilesMinSize,
					"ConfigView.section.file.torrent.autoskipfilesminsize");

			new IntParameter(gIgnoredFiles, "File.Torrent.AutoSkipMinSizeKB", 0, Integer.MAX_VALUE );

				// torrent create/delete ignore files

			Label lIgnoreFiles = new Label(gIgnoredFiles, SWT.NULL);
			Messages.setLanguageText(lIgnoreFiles,
					"ConfigView.section.file.torrent.ignorefiles");

			gridData = new GridData(GridData.FILL_HORIZONTAL);
			new StringParameter(gIgnoredFiles, "File.Torrent.IgnoreFiles",
					TOTorrent.DEFAULT_IGNORE_FILES).setLayoutData(gridData);


				// file name character mappings

			if (userMode > 1){
				Label lFileCharConv = new Label(gFile, SWT.NULL);
				Messages.setLanguageText(lFileCharConv,
						"ConfigView.section.file.char.conversions");

				gridData = new GridData(GridData.FILL_HORIZONTAL);
				new StringParameter(gFile, "File.Character.Conversions",
						ConfigurationDefaults.DEFAULT_FILE_CONVERSION_CHARS).setLayoutData(gridData);
			}
		}

			// File Deletetion Group

		Group gDeletion = new Group(gFile, SWT.NONE);
		Messages.setLanguageText(gDeletion,
				"ConfigView.section.file.deletion.section");
		layout = new GridLayout();
		layout.numColumns = 2;
		layout.marginHeight = 5;
		gDeletion.setLayout(layout);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 2;
		Utils.setLayoutData(gDeletion, gridData);

		if (userMode > 0) {
	  		Composite c = new Composite(gDeletion, SWT.NONE);
	  		layout = new GridLayout();
	  		layout.numColumns = 2;
	  		layout.marginHeight = 0;
	  		layout.marginWidth = 0;
	  		c.setLayout(layout);
	  		gridData = new GridData(GridData.FILL_HORIZONTAL);
	  		gridData.horizontalSpan = 2;
	  		Utils.setLayoutData(c, gridData);

	  		sCurConfigID = "tb.confirm.delete.content";
	  		label = new Label(c, SWT.NULL);
	  		Messages.setLanguageText(label, "ConfigView.section.file.tb.delete");
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
	  		new IntListParameter(c, sCurConfigID, labels, values);


	  		sCurConfigID = "def.deletetorrent";
	  		allConfigIDs.add(sCurConfigID);
	  		gridData = new GridData();
	  		gridData.horizontalSpan = 2;
	  		new BooleanParameter(gDeletion, sCurConfigID, "ConfigView.section.file.delete.torrent").setLayoutData(gridData);
		}


		try {
			final PlatformManager platform = PlatformManagerFactory.getPlatformManager();

			if (platform.hasCapability(PlatformManagerCapabilities.RecoverableFileDelete)) {
				sCurConfigID = "Move Deleted Data To Recycle Bin";
				allConfigIDs.add(sCurConfigID);

				gridData = new GridData();
				gridData.horizontalSpan = 2;
				new BooleanParameter(gDeletion, sCurConfigID,
						"ConfigView.section.file.nativedelete").setLayoutData(gridData);

			}
		} catch (Throwable e) {

		}

		if (userMode > 0) {
			sCurConfigID = "File.delete.include_files_outside_save_dir";
			allConfigIDs.add(sCurConfigID);

			gridData = new GridData();
			gridData.horizontalSpan = 2;
			new BooleanParameter(gDeletion, sCurConfigID,
					"ConfigView.section.file.delete.include_files_outside_save_dir").setLayoutData(gridData);

			sCurConfigID = "Delete Partial Files On Library Removal";
			allConfigIDs.add(sCurConfigID);

			gridData = new GridData();
			gridData.horizontalSpan = 2;
			new BooleanParameter(gDeletion, sCurConfigID,
					"delete.partial.files").setLayoutData(gridData);

		}

		if (userMode > 0) {
			Group gConfigSettings = new Group(gFile, SWT.NONE);
			Messages.setLanguageText(gConfigSettings,
					"ConfigView.section.file.config.section");
			layout = new GridLayout();
			layout.numColumns = 2;
			layout.marginHeight = 5;
			gConfigSettings.setLayout(layout);
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			gridData.horizontalSpan = 2;
			Utils.setLayoutData(gConfigSettings, gridData);

			// Configuration directory information.
			Label config_label = new Label(gConfigSettings, SWT.NULL);
			Messages.setLanguageText(config_label,
					"ConfigView.section.file.config.currentdir");
			Utils.setLayoutData(config_label, new GridData());
			Label config_link = new Label(gConfigSettings, SWT.NULL);
			config_link.setText(SystemProperties.getUserPath());
			Utils.setLayoutData(config_link, new GridData());
			LinkLabel.makeLinkedLabel(config_link, SystemProperties.getUserPath());

			sCurConfigID = "Use Config File Backups";
			allConfigIDs.add(sCurConfigID);

			// check on complete
			BooleanParameter backupConfig = new BooleanParameter(gConfigSettings,
					sCurConfigID, "ConfigView.label.backupconfigfiles");
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			backupConfig.setLayoutData(gridData);
		}
		/*
		    Button buttonReset = new Button(gFile, SWT.PUSH);
		    Messages.setLanguageText(buttonReset, "Button.reset");
		    gridData = new GridData(GridData.FILL_VERTICAL | GridData.VERTICAL_ALIGN_END);
		  	gridData.horizontalSpan = 2;
		  	Utils.setLayoutData(buttonReset, gridData);
		  	buttonReset.addSelectionListener(new SelectionAdapter() {
		  		public void widgetSelected(SelectionEvent e) {
		  			for (Iterator iter = allConfigIDs.iterator(); iter.hasNext();) {
							String sConfigID = (String) iter.next();
							COConfigurationManager.removeParameter(sConfigID);
						}
		  		}
		  	});
		*/
		return gFile;
	}
}
