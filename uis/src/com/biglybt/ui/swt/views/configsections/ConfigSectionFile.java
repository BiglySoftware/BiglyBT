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
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.config.impl.ConfigurationDefaults;
import com.biglybt.core.internat.MessageText;
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
import com.biglybt.ui.swt.views.ConfigView;

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
		Composite cFile = new Composite(parent, SWT.NULL);
		 
		configSectionCreateSupport( cFile );
		
		return( cFile );
	}
	
	private void
	configSectionCreateSupport(
		final Composite cFile )
	{
		ImageLoader imageLoader = ImageLoader.getInstance();
		Image imgOpenFolder = imageLoader.getImage("openFolderButton");

		Label label;

		GridData gridData = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
		cFile.setLayoutData(gridData);
	
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		layout.marginHeight = 0;
		cFile.setLayout(layout);

		int userMode = COConfigurationManager.getIntParameter("User Mode");

		// Default Dir Section
		final Group gDefaultDir = new Group(cFile, SWT.NONE);
		Messages.setLanguageText(gDefaultDir,
				"ConfigView.section.file.defaultdir.section");
		layout = new GridLayout();
		layout.numColumns = 3;
		gDefaultDir.setLayout(layout);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 2;
		gDefaultDir.setLayoutData(gridData);

		if (userMode > 0) {
			// def dir: best guess
			BooleanParameter bestGuess = new BooleanParameter(gDefaultDir,
					"DefaultDir.BestGuess", "ConfigView.section.file.defaultdir.bestguess");
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			gridData.horizontalSpan = 3;
			bestGuess.setLayoutData(gridData);
		}

		// Save Path
		Label lblDefaultDir = new Label(gDefaultDir, SWT.NONE);
		Messages.setLanguageText(lblDefaultDir,
				"ConfigView.section.file.defaultdir.ask");
		lblDefaultDir.setLayoutData(new GridData());

		gridData = new GridData(GridData.FILL_HORIZONTAL);
		final StringParameter pathParameter = new StringParameter(gDefaultDir,
				"Default save path");
		pathParameter.setLayoutData(gridData);

		Button browse = new Button(gDefaultDir, SWT.PUSH);
		browse.setImage(imgOpenFolder);
		imgOpenFolder.setBackground(browse.getBackground());
		Utils.setTT(browse,MessageText.getString("ConfigView.button.browse"));

		browse.addListener(SWT.Selection, new Listener() {
			/* (non-Javadoc)
			 * @see org.eclipse.swt.widgets.Listener#handleEvent(org.eclipse.swt.widgets.Event)
			 */
			@Override
			public void handleEvent(Event event) {
				DirectoryDialog dialog = new DirectoryDialog(cFile.getShell(),
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
		Composite cOpenOptions = new Composite(gDefaultDir, SWT.NONE);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 3;
		cOpenOptions.setLayoutData(gridData);
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
		new StringListParameter(cOpenOptions, ConfigurationDefaults.CFG_TORRENTADD_OPENOPTIONS, openLabels, openValues);

		label = new Label(cOpenOptions, SWT.NULL);
		label.setText( "    " );
		new BooleanParameter(cOpenOptions,
				"ui.addtorrent.openoptions.sep", "ConfigView.section.file.showopentorrentoptions.sep");



		if (userMode > 0) {
			// def dir: autoSave -> auto-rename
			BooleanParameter autoSaveAutoRename = new BooleanParameter(gDefaultDir,
					"DefaultDir.AutoSave.AutoRename", "ConfigView.section.file.defaultdir.autorename");
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
			cHistory.setLayoutData(gridData);

			// def dir: auto update
			BooleanParameter autoUpdateSaveDir = new BooleanParameter(cHistory,
					"DefaultDir.AutoUpdate", "ConfigView.section.file.defaultdir.lastused");

			Label padLabel = new Label( cHistory, SWT.NULL );
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			padLabel.setLayoutData(gridData);

			Label historyMax = new Label(cHistory, SWT.NULL);
			Messages.setLanguageText(historyMax,"ConfigView.label.save_list.max_entries");

			IntParameter paramhistoryMax = new IntParameter(cHistory,	"saveTo_list.max_entries");

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

		label = new Label(cFile, SWT.NONE);
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		label.setLayoutData(gridData);

		////////////////////

		if (userMode > 0 && !Constants.isWindows) {
			BooleanParameter xfsAllocation = new BooleanParameter(cFile,
					"XFS Allocation", "ConfigView.label.xfs.allocation");
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			xfsAllocation.setLayoutData(gridData);
		}

		if ( userMode > 0 ){
			
				// zero new files
			
			BooleanParameter zeroNew = new BooleanParameter(cFile, "Zero New",
					"ConfigView.label.zeronewfiles");
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			zeroNew.setLayoutData(gridData);

				BooleanParameter zeroNewStop = new BooleanParameter(cFile, "Zero New Stop",
						"ConfigView.label.zeronewfiles.stop");
				gridData = new GridData();
				gridData.horizontalSpan = 2;
				gridData.horizontalIndent = 25;
				zeroNewStop.setLayoutData(gridData);
			
				// sparse
			
			BooleanParameter sparseFiles = new BooleanParameter(cFile, "Enable Sparse Files",
					"ConfigView.label.spare.file.enable");
			
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			sparseFiles.setLayoutData(gridData);
				
				// reorder 
			
			BooleanParameter pieceReorder = new BooleanParameter(cFile, "Enable reorder storage mode",
					"ConfigView.label.piecereorder");
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			pieceReorder.setLayoutData(gridData);

		
				Label lblMinMB = new Label(cFile, SWT.NULL);
				Messages.setLanguageText(lblMinMB, "ConfigView.label.piecereorderminmb");
				gridData = new GridData();
				gridData.horizontalIndent = 25;
				lblMinMB.setLayoutData(gridData);
	
				IntParameter minMB = new IntParameter(cFile, "Reorder storage mode min MB");
				gridData = new GridData();
				minMB.setLayoutData(gridData);

		
				// incremental file creation
				
			BooleanParameter incremental = new BooleanParameter(cFile, "Enable incremental file creation",
					"ConfigView.label.incrementalfile");
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			incremental.setLayoutData(gridData);
			
			ParameterChangeListener listener = (n,internal)->{
				
					// these are mutually exclusive
				
				boolean zero 	= zeroNew.isSelected();
				boolean sparse 	= sparseFiles.isSelected();
				boolean reorder	= pieceReorder.isSelected();
				boolean inc 	= incremental.isSelected();
				
				if ( zero ){
					sparse 	= false;
					reorder	= false;
					inc		= false;
				}else if ( sparse ){
					reorder	= false;
					inc		= false;
				}else if ( reorder ){
					inc = false;
				}else if ( inc ){
				}else{
					zero = sparse = reorder = inc = true;
				}
				
				zeroNew.setEnabled( zero );
				zeroNewStop.setEnabled( zero && zeroNew.isSelected());
				
				sparseFiles.setEnabled( sparse );
				
				pieceReorder.setEnabled( reorder );
				lblMinMB.setEnabled( reorder && pieceReorder.isSelected());
				minMB.setEnabled( reorder && pieceReorder.isSelected());
				
				incremental.setEnabled( inc );
			};

			for ( BooleanParameter p: new BooleanParameter[]{ zeroNew, zeroNewStop, sparseFiles, pieceReorder, incremental }){
				
				p.addChangeListener(listener);
			}
			
			listener.parameterChanged( null, false );
		}

			// truncate too large

		if (userMode > 0) {
			// truncate too large
			BooleanParameter truncateLarge = new BooleanParameter(cFile,
					"File.truncate.if.too.large", "ConfigView.section.file.truncate.too.large");
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			truncateLarge.setLayoutData(gridData);
		}

			// merge files of same size

		BooleanParameter mergeSameSize = null;

		if (userMode > 0) {
			mergeSameSize = new BooleanParameter(cFile,
					"Merge Same Size Files", "ConfigView.section.file.merge.same.size");
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			mergeSameSize.setLayoutData(gridData);
		}

			// merge extended

		if (mergeSameSize != null) {
			BooleanParameter mergeSameSizeExt = new BooleanParameter(cFile,
					"Merge Same Size Files Extended", "ConfigView.section.file.merge.same.size.extended");
			gridData = new GridData();
			gridData.horizontalIndent = 25;
			gridData.horizontalSpan = 2;
			mergeSameSizeExt.setLayoutData(gridData);

			IAdditionalActionPerformer mergeAP = new ChangeSelectionActionPerformer(
					mergeSameSizeExt.getControls(), false);
			mergeSameSize.setAdditionalActionPerformer(mergeAP);
		}

		// merge tolerance

		if (mergeSameSize != null) {
			label = new Label(cFile, SWT.NULL );
			Messages.setLanguageText( label,"ConfigView.section.file.merge.same.size.tolerance");
			gridData = new GridData();
			gridData.horizontalIndent = 25;
			label.setLayoutData(gridData);

			IntParameter tol = new IntParameter(cFile, "Merge Same Size Files Tolerance");
			gridData = new GridData();
			tol.setLayoutData(gridData);
			
			IAdditionalActionPerformer mergeAP = new ChangeSelectionActionPerformer( new Control[]{ tol.getControl(), label });
			mergeSameSize.setAdditionalActionPerformer(mergeAP);
		}
		
			// recheck on complete

		if (userMode > 0) {
			// check on complete
			BooleanParameter checkOnComp = new BooleanParameter(cFile, "Check Pieces on Completion",
					"ConfigView.label.checkOncompletion");
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			checkOnComp.setLayoutData(gridData);
		}

		if (userMode > 0) {
			// check on complete
			BooleanParameter checkOnSeeding = new BooleanParameter(cFile,
					"Seeding Piece Check Recheck Enable", "ConfigView.label.checkOnSeeding");
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			checkOnSeeding.setLayoutData(gridData);
		}

		if (userMode > 1) {

			BooleanParameter strictLocking = new BooleanParameter(cFile,
					"File.strict.locking", "ConfigView.label.strictfilelocking");
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			strictLocking.setLayoutData(gridData);
		}

		if (userMode == 0) {
		} else {

				// max links


			Composite maxLinksGroup = new Composite(cFile, SWT.NULL);
			layout = new GridLayout();
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			layout.numColumns = 3;
			maxLinksGroup.setLayout(layout);
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			gridData.horizontalSpan = 2;
			maxLinksGroup.setLayoutData(gridData);

			Label maxLinks = new Label(maxLinksGroup, SWT.NULL);
			Messages.setLanguageText(maxLinks, "ConfigView.label.max.file.links");

			new IntParameter(maxLinksGroup,	"Max File Links Supported",8,Integer.MAX_VALUE);


			Label maxLinksWarning = new Label(maxLinksGroup, SWT.NULL);
			Messages.setLanguageText(maxLinksWarning, "ConfigView.label.max.file.links.warning");
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			maxLinksWarning.setLayoutData(gridData);

				// restart out of space downloads

			// resume data
			final BooleanParameter OOSDRE = new BooleanParameter(cFile,
					"Insufficient Space Download Restart Enable", "ConfigView.label.restart.no.space.dls");
			gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
			gridData.horizontalSpan = 2;
			OOSDRE.setLayoutData(gridData);

			Composite cOOSDGroup = new Composite(cFile, SWT.NULL);
			layout = new GridLayout();
			layout.marginHeight = 0;
			layout.marginWidth = 4;
			layout.numColumns = 3;
			cOOSDGroup.setLayout(layout);
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			gridData.horizontalIndent = 25;
			gridData.horizontalSpan = 2;
			cOOSDGroup.setLayoutData(gridData);

			Label lblOOSDRInterval = new Label(cOOSDGroup, SWT.NULL);
			Messages.setLanguageText(lblOOSDRInterval,
					"ConfigView.label.restart.no.space.dls.interval");

			IntParameter paramOOSDRInterval = new IntParameter(cOOSDGroup,
					"Insufficient Space Download Restart Period",1,Integer.MAX_VALUE);
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

			// resume data
			final BooleanParameter bpUseResume = new BooleanParameter(cFile,
					"Use Resume", "ConfigView.label.usefastresume");
			bpUseResume.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_BEGINNING));

			Composite cResumeGroup = new Composite(cFile, SWT.NULL);
			layout = new GridLayout();
			layout.marginHeight = 0;
			layout.marginWidth = 4;
			layout.numColumns = 3;
			cResumeGroup.setLayout(layout);
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			gridData.horizontalIndent = 25;
			gridData.horizontalSpan = 2;
			cResumeGroup.setLayoutData(gridData);

			Label lblSaveResumeInterval = new Label(cResumeGroup, SWT.NULL);
			Messages.setLanguageText(lblSaveResumeInterval,
					"ConfigView.label.saveresumeinterval");

			IntParameter paramSaveInterval = new IntParameter(cResumeGroup,
					"Save Resume Interval");
			gridData = new GridData();
			paramSaveInterval.setLayoutData(gridData);

			Label lblMinutes = new Label(cResumeGroup, SWT.NULL);
			Messages.setLanguageText(lblMinutes, "ConfigView.text.minutes");

			// save peers

			final BooleanParameter recheck_all = new BooleanParameter(cResumeGroup,
					"On Resume Recheck All", "ConfigView.section.file.resume.recheck.all");
			gridData = new GridData();
			gridData.horizontalSpan = 3;
			recheck_all.setLayoutData(gridData);
			// save peers

			final BooleanParameter save_peers = new BooleanParameter(cResumeGroup,
					"File.save.peers.enable", "ConfigView.section.file.save.peers.enable");
			gridData = new GridData();
			gridData.horizontalSpan = 3;
			save_peers.setLayoutData(gridData);

			// save peers max

			final Label lblSavePeersMax = new Label(cResumeGroup, SWT.NULL);
			Messages.setLanguageText(lblSavePeersMax,
					"ConfigView.section.file.save.peers.max");
			final IntParameter savePeersMax = new IntParameter(cResumeGroup,
					"File.save.peers.max");
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

			// Auto-Prioritize
			label = new Label(cFile, SWT.WRAP);
			gridData = new GridData();
			gridData.widthHint = 180;
			label.setLayoutData(gridData);
			Messages.setLanguageText(label, "ConfigView.label.priorityExtensions");

			Composite cExtensions = new Composite(cFile, SWT.NULL);
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			cExtensions.setLayoutData(gridData);
			layout = new GridLayout();
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			layout.numColumns = 3;
			cExtensions.setLayout(layout);

			gridData = new GridData(GridData.FILL_HORIZONTAL);
			new StringParameter(cExtensions, "priorityExtensions").setLayoutData(gridData);

			new BooleanParameter(cExtensions, "priorityExtensionsIgnoreCase",
					"ConfigView.label.ignoreCase");
			
		}
		
		if (userMode > 0) {
			
			label = new Label(cFile, SWT.NONE);
			Messages.setLanguageText(label, "ConfigView.label.sequential.exts");

			gridData = new GridData(GridData.FILL_HORIZONTAL);
			
			StringParameter auto_sequential_ext = new StringParameter(cFile, "file.auto.sequential.exts");
			
			auto_sequential_ext.setLayoutData(gridData);
		}

		// quick view

		if (userMode > 0) {
	
			label = new Label(cFile, SWT.WRAP);
			gridData = new GridData();
			gridData.widthHint = 180;
			label.setLayoutData(gridData);
			Messages.setLanguageText(label, "ConfigView.label.quickviewexts");
	
			Composite cQuickView = new Composite(cFile, SWT.NULL);
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			cQuickView.setLayoutData(gridData);
			layout = new GridLayout();
			layout.marginHeight = 0;
			layout.marginWidth = 0;
			layout.numColumns = 3;
			cQuickView.setLayout(layout);
	
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			new StringParameter(cQuickView, "quick.view.exts" ).setLayoutData(gridData);
	
			label = new Label(cQuickView, SWT.NONE);
			Messages.setLanguageText(label, "ConfigView.label.quickviewmaxkb");
	
			IntParameter qvmax = new IntParameter(cQuickView, "quick.view.maxkb", 1, 9999 );
		}
		

		// rename incomplete

		if (userMode > 0) {

				// rename incomplete files

			gridData = new GridData();
			gridData.horizontalSpan = 1;
			final BooleanParameter rename_incomplete = new BooleanParameter(cFile,
					"Rename Incomplete Files", "ConfigView.section.file.rename.incomplete");
			rename_incomplete.setLayoutData(gridData);

			gridData = new GridData(GridData.FILL_HORIZONTAL);
			StringParameter rename_incomplete_ext = new StringParameter(cFile,
					"Rename Incomplete Files Extension");
			rename_incomplete_ext.setLayoutData(gridData);

			IAdditionalActionPerformer incompFileAP = new ChangeSelectionActionPerformer(
					rename_incomplete_ext.getControls(), false);
			rename_incomplete.setAdditionalActionPerformer(incompFileAP);

				// put 'dnd' files in subdir

			gridData = new GridData();
			gridData.horizontalSpan = 1;
			final BooleanParameter enable_subfolder = new BooleanParameter(cFile,
					"Enable Subfolder for DND Files", "ConfigView.section.file.subfolder.dnd");
			rename_incomplete.setLayoutData(gridData);

			gridData = new GridData(GridData.FILL_HORIZONTAL);
			StringParameter subfolder_name = new StringParameter(cFile,
					"Subfolder for DND Files");
			subfolder_name.setLayoutData(gridData);

			IAdditionalActionPerformer subfolderAP = new ChangeSelectionActionPerformer(
					subfolder_name.getControls(), false);
			enable_subfolder.setAdditionalActionPerformer(subfolderAP);

				// dnd prefix

			gridData = new GridData();
			gridData.horizontalSpan = 2;
			gridData.horizontalIndent=25;
			final BooleanParameter enable_dndprefix = new BooleanParameter(cFile,
					"Use Incomplete File Prefix", "ConfigView.section.file.dnd.prefix.enable");
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

		BooleanParameter recordDLHistory = new BooleanParameter(cFile, "Download History Enabled",
				"ConfigView.label.record.dl.history");
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		recordDLHistory.setLayoutData(gridData);

			// auto tag group
		
		{
		    BooleanParameter auto_tag_enable = new BooleanParameter(cFile, "Files Auto Tag Enable", "label.enable.auto.tagging");
		    gridData = new GridData();
			gridData.horizontalSpan = 2;
			auto_tag_enable.setLayoutData(gridData);
			
			auto_tag_enable.getControl().setData( ConfigView.SELECT_KEY, "torrent-add-auto-tag" );
			
			Group gAutoTag = new Group(cFile, SWT.NONE);
			Messages.setLanguageText(gAutoTag, "label.auto.tagging");
			layout = new GridLayout();
			layout.numColumns = 4;
			layout.marginHeight = 5;
			gAutoTag.setLayout(layout);
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			gridData.horizontalSpan = 2;
			gAutoTag.setLayoutData(gridData);

			
		    int	num_tags = COConfigurationManager.getIntParameter( "Files Auto Tag Count", 1);

		    for ( int i=0;i<num_tags;i++){
			    Label lExtensions = new Label(gAutoTag, SWT.NULL);
			    Messages.setLanguageText(lExtensions, "ConfigView.label.file.exts");

			    gridData = new GridData(GridData.FILL_HORIZONTAL);
			    final StringParameter extsParameter =
			    	new StringParameter(gAutoTag, "File Auto Tag Exts " + (i==0?"":(" " + i )));
			    extsParameter.setLayoutData(gridData);

			    Label lTag = new Label(gAutoTag, SWT.NULL);
			    Messages.setLanguageText(lTag, "label.assign.to.tag");

			    StringParameter tagParam =
			    	new StringParameter(gAutoTag,  "File Auto Tag Name " + (i==0?"":(" " + i )));
			    gridData = new GridData();
			    gridData.widthHint = 60;
			    tagParam.setLayoutData(gridData);
		    }

		    	// select best
		    
		    Label lPad = new Label(gAutoTag, SWT.NULL);
	    
		    BooleanParameter auto_tag_best = new BooleanParameter(gAutoTag, "Files Auto Tag Best Size", "ConfigView.label.auto.tag.best.size");
		    gridData = new GridData();
			gridData.horizontalSpan = 3;
			auto_tag_best.setLayoutData(gridData);
			
		    	// default
		    
		    lPad = new Label(gAutoTag, SWT.NULL);

		    gridData = new GridData(GridData.FILL_HORIZONTAL);
		    Label lDefault = new Label(gAutoTag, SWT.NULL);
		    Messages.setLanguageText(lDefault, "label.assign.to.tag.default");
		    lDefault.setLayoutData(gridData);

		    Label lTag = new Label(gAutoTag, SWT.NULL);
		    Messages.setLanguageText(lTag, "label.assign.to.tag");

		    StringParameter tagParam =
		    	new StringParameter(gAutoTag,  "File Auto Tag Name Default" );
		    gridData = new GridData();
		    gridData.widthHint = 60;
		    tagParam.setLayoutData(gridData);
		    
		    	// add another tag
		    
		    Label addAnother = new Label(gAutoTag, SWT.NULL);
		    Messages.setLanguageText(addAnother, "ConfigView.label.addanothertag");

		    Composite gAddButton = new Composite(gAutoTag, SWT.NULL);
		    gridData = new GridData(GridData.FILL_HORIZONTAL);
		    gridData.horizontalSpan = 3;
				gAddButton.setLayoutData(gridData);
		    layout = new GridLayout();
		    layout.marginHeight = 0;
		    layout.marginWidth = 0;
		    layout.numColumns = 2;
		    gAddButton.setLayout(layout);
		    Button addButton = new Button(gAddButton, SWT.PUSH);
		    Messages.setLanguageText(addButton, "Button.add");

		    addButton.addListener(SWT.Selection, new Listener() {
			      @Override
			      public void handleEvent(Event event) {

			    	  int num = COConfigurationManager.getIntParameter( "Files Auto Tag Count", 1);

			    	  COConfigurationManager.setParameter( "Files Auto Tag Count", num+1);

			    	  Utils.disposeComposite( cFile, false );

			    	  configSectionCreateSupport( cFile );

			    	  cFile.layout( true,  true );
			      }});
		    
		    Label pad = new Label(gAddButton, SWT.NULL);
		    gridData = new GridData(GridData.FILL_HORIZONTAL);
		    pad.setLayoutData( gridData);
			
			Control[] controls = new Control[]{ gAutoTag };
			
			auto_tag_enable.setAdditionalActionPerformer(new ChangeSelectionActionPerformer(controls));
		}
		
		
		
		
		if ( userMode > 0 ){

				// ignore group 
			
			Group gIgnoredFiles = new Group(cFile, SWT.NONE);
			Messages.setLanguageText(gIgnoredFiles,
					"ConfigView.section.file.ignore.section");
			layout = new GridLayout();
			layout.numColumns = 3;
			layout.marginHeight = 5;
			gIgnoredFiles.setLayout(layout);
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			gridData.horizontalSpan = 2;
			gIgnoredFiles.setLayoutData(gridData);

				// torrent add auto-skip file types

			Label lSkipFiles = new Label(gIgnoredFiles, SWT.NULL);
			Messages.setLanguageText(lSkipFiles,
					"ConfigView.section.file.torrent.autoskipfiles");

			lSkipFiles.setData( ConfigView.SELECT_KEY, "torrent-add-auto-skip" );
			
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			gridData.horizontalSpan = 2;
			new StringParameter(gIgnoredFiles, "File.Torrent.AutoSkipExtensions", false ).setLayoutData(gridData);

			// torrent add auto-skip file names

			Label lSkipFileNames = new Label(gIgnoredFiles, SWT.NULL);
			Messages.setLanguageText(lSkipFileNames,
					"ConfigView.section.file.torrent.autoskipfilenames");

			lSkipFileNames.setData( ConfigView.SELECT_KEY, "torrent-add-auto-skip" );

			gridData = new GridData(GridData.FILL_HORIZONTAL);
			gridData.horizontalSpan = 1;
			new StringParameter(gIgnoredFiles, "File.Torrent.AutoSkipFiles", false ).setLayoutData(gridData);
			new BooleanParameter(gIgnoredFiles, "File.Torrent.AutoSkipFiles.RegExp", "label.regexps");
			
				// torrent add auto-skip min size

			Label lSkipFilesMinSize = new Label(gIgnoredFiles, SWT.NULL);
			Messages.setLanguageText(lSkipFilesMinSize,
					"ConfigView.section.file.torrent.autoskipfilesminsize");

			lSkipFilesMinSize.setData( ConfigView.SELECT_KEY, "torrent-add-auto-skip" );
			
			new IntParameter(gIgnoredFiles, "File.Torrent.AutoSkipMinSizeKB", 0, Integer.MAX_VALUE );
			new Label( gIgnoredFiles, SWT.NULL );
			
				// torrent create/delete ignore files

			Label lIgnoreFiles = new Label(gIgnoredFiles, SWT.NULL);
			Messages.setLanguageText(lIgnoreFiles,
					"ConfigView.section.file.torrent.ignorefiles");

			gridData = new GridData(GridData.FILL_HORIZONTAL);
			gridData.horizontalSpan = 2;
			new StringParameter(gIgnoredFiles, "File.Torrent.IgnoreFiles").setLayoutData(gridData);


				// file name character mappings

			if (userMode > 1){
				Label lFileCharConv = new Label(cFile, SWT.NULL);
				Messages.setLanguageText(lFileCharConv,
						"ConfigView.section.file.char.conversions");

				gridData = new GridData(GridData.FILL_HORIZONTAL);
				gridData.horizontalSpan = 2;
				new StringParameter(cFile, "File.Character.Conversions").setLayoutData(gridData);
			}
		}

			// File Deletetion Group

		Group gDeletion = new Group(cFile, SWT.NONE);
		Messages.setLanguageText(gDeletion,
				"ConfigView.section.file.deletion.section");
		layout = new GridLayout();
		layout.numColumns = 2;
		layout.marginHeight = 5;
		gDeletion.setLayout(layout);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 2;
		gDeletion.setLayoutData(gridData);

		if (userMode > 0) {
	  		Composite c = new Composite(gDeletion, SWT.NONE);
	  		layout = new GridLayout();
	  		layout.numColumns = 2;
	  		layout.marginHeight = 0;
	  		layout.marginWidth = 0;
	  		c.setLayout(layout);
	  		gridData = new GridData(GridData.FILL_HORIZONTAL);
	  		gridData.horizontalSpan = 2;
			c.setLayoutData(gridData);

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
	  		new IntListParameter(c, "tb.confirm.delete.content", labels, values);

	  		gridData = new GridData();
	  		gridData.horizontalSpan = 2;
	  		new BooleanParameter(gDeletion, "def.deletetorrent", "ConfigView.section.file.delete.torrent").setLayoutData(gridData);
		}


		try {
			final PlatformManager platform = PlatformManagerFactory.getPlatformManager();

			if (platform.hasCapability(PlatformManagerCapabilities.RecoverableFileDelete)) {

				gridData = new GridData();
				gridData.horizontalSpan = 2;
				new BooleanParameter(gDeletion, "Move Deleted Data To Recycle Bin",
						"ConfigView.section.file.nativedelete").setLayoutData(gridData);

			}
		} catch (Throwable e) {

		}

		if (userMode > 0) {

			gridData = new GridData();
			gridData.horizontalSpan = 2;
			new BooleanParameter(gDeletion, "File.delete.include_files_outside_save_dir",
					"ConfigView.section.file.delete.include_files_outside_save_dir").setLayoutData(gridData);

			gridData = new GridData();
			gridData.horizontalSpan = 2;
			new BooleanParameter(gDeletion, "Delete Partial Files On Library Removal",
					"delete.partial.files").setLayoutData(gridData);

		}

		if (userMode > 0) {
			Group gConfigSettings = new Group(cFile, SWT.NONE);
			Messages.setLanguageText(gConfigSettings,
					"ConfigView.section.file.config.section");
			layout = new GridLayout();
			layout.numColumns = 2;
			layout.marginHeight = 5;
			gConfigSettings.setLayout(layout);
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			gridData.horizontalSpan = 2;
			gConfigSettings.setLayoutData(gridData);

			// Configuration directory information.
			Label config_label = new Label(gConfigSettings, SWT.NULL);
			Messages.setLanguageText(config_label,
					"ConfigView.section.file.config.currentdir");
			config_label.setLayoutData(new GridData());
			Label config_link = new Label(gConfigSettings, SWT.NULL);
			config_link.setText(SystemProperties.getUserPath());
			config_link.setLayoutData(new GridData());
			LinkLabel.makeLinkedLabel(config_link, SystemProperties.getUserPath());

			// check on complete
			BooleanParameter backupConfig = new BooleanParameter(gConfigSettings,
					"Use Config File Backups", "ConfigView.label.backupconfigfiles");
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			backupConfig.setLayoutData(gridData);
		}
	}
}
