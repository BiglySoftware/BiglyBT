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

import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pifimpl.local.ui.config.*;


import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.config.Parameter;


import static com.biglybt.core.config.ConfigKeys.File.*;
import static com.biglybt.core.config.ConfigKeys.Tag.*;

public class ConfigSectionInterfaceTags
	extends ConfigSectionImpl
{
	public static final String REFID_TORRENT_ADD_AUTO_TAG = "torrent-add-auto-tag";

	public static final String SECTION_ID = "style.tags";

	protected boolean skipTidy = false;

	public 
	ConfigSectionInterfaceTags() 
	{
		super(	SECTION_ID, ConfigSection.SECTION_INTERFACE, Parameter.MODE_BEGINNER );
	}


	@Override
	public void 
	build() 
	{
		buildGeneral();
		
		buildUISpecific();
		
		buildFiles();
		
		buildTracker();
	}
	
	protected void
	buildUISpecific()
	{
	}
	
	protected void
	buildGeneral()
	{
		List<Parameter> listGeneral = new ArrayList<>();
		
		BooleanParameterImpl persistSwarmTags = new BooleanParameterImpl(
				BCFG_TAG_SHOW_SWARM_TAGS_IN_OVERVIEW, "label.show.swarm.tags.in.overview");
		persistSwarmTags.setAllowedUiTypes(UIInstance.UIT_SWT);
		add( persistSwarmTags,  Parameter.MODE_INTERMEDIATE, listGeneral);
		
		IntParameterImpl autoReapply = new IntParameterImpl(
				ICFG_TAG_AUTO_FULL_REAPPLY_PERIOD_SECS,
				"tag.auto.reapply.period", 0, Integer.MAX_VALUE );
		
		add( autoReapply, Parameter.MODE_INTERMEDIATE, listGeneral );
		
		ParameterGroupImpl pgGeneral = new ParameterGroupImpl(
				"ConfigView.section.global", listGeneral);
		
		add("pgGeneral", pgGeneral);
	}
	
	protected String
	getAutoTagExts(
		int		i )
	{
		return( COConfigurationManager.getStringParameter(SCFG_PREFIX_FILE_AUTO_TAG_EXTS + (i==0?"":(" " + i ))));
	}
	
	protected void
	setAutoTagExts(
		int			i,
		String		str )
	{
		COConfigurationManager.setParameter(SCFG_PREFIX_FILE_AUTO_TAG_EXTS + (i==0?"":(" " + i )), str );
	}
	
	protected String
	getAutoTagTag(
		int		i )
	{
		return( COConfigurationManager.getStringParameter(SCFG_PREFIX_FILE_AUTO_TAG_NAME + (i==0?"":(" " + i )), null ));
	}

	protected void
	setAutoTagTag(
		int			i,
		String		str )
	{
		COConfigurationManager.setParameter(SCFG_PREFIX_FILE_AUTO_TAG_NAME + (i==0?"":(" " + i )), str );
	}
	
	private void
	buildFiles()
	{
			// auto tag group

		List<Parameter> listAutoTag = new ArrayList<>();

		BooleanParameterImpl auto_tag_enable = new BooleanParameterImpl(
				BCFG_FILES_AUTO_TAG_ENABLE, "label.enable.auto.tagging");
		add(auto_tag_enable, listAutoTag);

			// filler
		add("f0", new LabelParameterImpl(""), listAutoTag);
		if ( isSWT()){
			add("f0.1", new LabelParameterImpl(""), listAutoTag);
			add("f0.2", new LabelParameterImpl(""), listAutoTag);
		}
		int num_tags = COConfigurationManager.getIntParameter( ICFG_FILES_AUTO_TAG_COUNT, 1);
		
		boolean	tidied = false;

		if ( skipTidy ){
			
			skipTidy = false;
			
		}else{
			
			for ( int i=num_tags-1; i>=1; i-- ){
				String exts	= getAutoTagExts( i );
				String tag	= getAutoTagTag( i );
				
				if ( 	( exts == null || exts.isEmpty()) &&
						( tag == null || tag.isEmpty())){
					
					num_tags--;
					
					tidied = true;
					
				}else{
					
					break;
				}
			}
			
			if ( tidied ){
				
				num_tags++;	// keep one empty one
				
				COConfigurationManager.setParameter(ICFG_FILES_AUTO_TAG_COUNT, num_tags);
			}
			
		}
		
		for (int i = 0; i < num_tags; i++) {

			addAutoTagLine( listAutoTag, i );
		}

			// select best

		BooleanParameterImpl auto_tag_best = new BooleanParameterImpl(
				BCFG_FILES_AUTO_TAG_BEST_SIZE, "ConfigView.label.auto.tag.best.size");
		add(auto_tag_best, listAutoTag);

			// filler
		add("f1", new LabelParameterImpl(""), listAutoTag);
		if ( isSWT()){
			add("f1.1", new LabelParameterImpl(""), listAutoTag);
			add("f1.2", new LabelParameterImpl(""), listAutoTag);
		}
			// default

		addAutoTagLine(listAutoTag, -1 );

		// add another tag

		ActionParameterImpl addButton = new ActionParameterImpl(null,
				"ConfigView.label.addanothertag");
		add("addButton", addButton, listAutoTag);

		addButton.addListener(param -> {

			int num = COConfigurationManager.getIntParameter( ICFG_FILES_AUTO_TAG_COUNT, 1);

			COConfigurationManager.setParameter(ICFG_FILES_AUTO_TAG_COUNT, num + 1);

			skipTidy = true;

			requestRebuild();
		});

			// filler 
		add("f2", new LabelParameterImpl(""), listAutoTag);
		if ( isSWT()){
			add("f2.1", new LabelParameterImpl(""), listAutoTag);
			add("f2.2", new LabelParameterImpl(""), listAutoTag);
		}
		
		BooleanParameterImpl auto_tag_mod = new BooleanParameterImpl(
				BCFG_FILES_AUTO_TAG_ALLOW_MOD, "ConfigView.label.auto.tag.allow.mod");
		add(auto_tag_mod, listAutoTag);

		ParameterGroupImpl pgExtensionTagging = new ParameterGroupImpl(
				"ConfigView.label.lh.ext", listAutoTag);
		add("pgAutoTagging", pgExtensionTagging);
		pgExtensionTagging.setNumberOfColumns(isSWT()?4:2);
		pgExtensionTagging.setReferenceID(REFID_TORRENT_ADD_AUTO_TAG);

		auto_tag_enable.addEnabledOnSelection(
				listAutoTag.subList(1, listAutoTag.size()).toArray(new Parameter[0]));
		
		
		List<Parameter> listFiles = new ArrayList<>();
		listFiles.add( pgExtensionTagging );
		
		ParameterGroupImpl pgFiles = new ParameterGroupImpl(
				"ConfigView.section.files", listFiles);
		add("pgFiles", pgFiles);
		pgFiles.setNumberOfColumns(1);
	}
	
	protected boolean
	isSWT()
	{
		return( false );
	}
	
	protected void
	addAutoTagLine(
		List<Parameter> listAutoTag,
		int				index )
	{
		if ( index == -1 ){
			LabelParameterImpl autoTagNoMatchInfo = new LabelParameterImpl(
					"label.assign.to.tag.default");
			add(autoTagNoMatchInfo, listAutoTag);

			StringParameterImpl tagParam = new StringParameterImpl(
					SCFG_FILE_AUTO_TAG_NAME_DEFAULT, "label.assign.to.tag");
			add(tagParam, listAutoTag);
			tagParam.setWidthInCharacters(15);
		}else{
			StringParameterImpl tagExts = new StringParameterImpl(
					SCFG_PREFIX_FILE_AUTO_TAG_EXTS + (index == 0 ? "" : (" " + index)),
					"ConfigView.label.file.exts");
			add(tagExts, listAutoTag);
	
			StringParameterImpl tagParam = new StringParameterImpl(
					SCFG_PREFIX_FILE_AUTO_TAG_NAME + (index == 0 ? "" : (" " + index)),
					"label.assign.to.tag");
			add(tagParam, listAutoTag);
			tagParam.setWidthInCharacters(15);
		}
	}
	
	private void
	buildTracker()
	{

		if (!COConfigurationManager.getBooleanParameter( "PluginInfo.azbuddy.enabled")) {
			return;
		}
			// Tracker tagging
		
		List<Parameter> listTracker = new ArrayList<>();

		BooleanParameterImpl auto_tag_enable = new BooleanParameterImpl(
				BCFG_TRACKER_AUTO_TAG_INTERESTING_TRACKERS, "label.auto.tag.interesting.trackers");
		add(auto_tag_enable, listTracker);
		
		ParameterGroupImpl pgTracker = new ParameterGroupImpl(
				"label.tracker", listTracker);
		add("pgTracker", pgTracker);
		pgTracker.setNumberOfColumns(2);

	}
}
