/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.ui.swt.views.configsections;

import static com.biglybt.core.config.ConfigKeys.File.ICFG_FILES_AUTO_TAG_COUNT;
import static com.biglybt.core.config.ConfigKeys.File.SCFG_FILE_AUTO_TAG_NAME_DEFAULT;
import static com.biglybt.core.config.ConfigKeys.File.SCFG_PREFIX_FILE_AUTO_TAG_EXTS;
import static com.biglybt.core.config.ConfigKeys.File.SCFG_PREFIX_FILE_AUTO_TAG_NAME;
import static com.biglybt.core.config.ConfigKeys.File.SCFG_PREFIX_WATCH_TORRENT_FOLDER_TAG;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.tag.TagType;
import com.biglybt.pif.ui.config.Parameter;
import com.biglybt.pifimpl.local.ui.config.ActionParameterImpl;
import com.biglybt.pifimpl.local.ui.config.BooleanParameterImpl;
import com.biglybt.pifimpl.local.ui.config.IntListParameterImpl;
import com.biglybt.pifimpl.local.ui.config.LabelParameterImpl;
import com.biglybt.pifimpl.local.ui.config.ParameterGroupImpl;
import com.biglybt.pifimpl.local.ui.config.ParameterImpl;
import com.biglybt.ui.config.ConfigSectionInterfaceTags;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.config.BaseSwtParameter;
import com.biglybt.ui.swt.views.utils.TagUIUtils;

public class 
ConfigSectionInterfaceTagsSWT
	extends ConfigSectionInterfaceTags
	implements BaseConfigSectionSWT
{
	private Map<ParameterImpl, BaseSwtParameter>		paramMap;
	
	@Override
	public void 
	configSectionCreate(
		Composite 								parent,
		Map<ParameterImpl, BaseSwtParameter> 	mapParamToSwtParam )
	{
		paramMap = mapParamToSwtParam;
	}
	
	@Override
	protected void 
	buildUISpecific() 
	{
		boolean isAZ3 = Utils.isAZ3UI();
		
		List<Parameter> listLibrary = new ArrayList<>();
		List<Parameter> listSidebar = new ArrayList<>();

			// sidebar and library
		
		if (isAZ3) {
			
			add(new BooleanParameterImpl("Library.CatInSideBar",
					"ConfigView.section.style.CatInSidebar"), listSidebar);
		}
		
		add(new BooleanParameterImpl("Library.ShowCatButtons",
				"ConfigView.section.style.ShowCatButtons"), listLibrary);

		BooleanParameterImpl show_cat_comp_only = new BooleanParameterImpl(
				"Library.ShowCatButtons.CompOnly",
				"ConfigView.section.style.ShowTagButtons.CompOnly");
		add(show_cat_comp_only,listLibrary);
		show_cat_comp_only.setIndent(1, true);
		
		if (isAZ3) {
			
			BooleanParameterImpl show_tags = new BooleanParameterImpl(
					"Library.TagInSideBar", "ConfigView.section.style.TagInSidebar");
			add(show_tags, listSidebar);
		
			BooleanParameterImpl show_tag_groups = new BooleanParameterImpl(
					"Library.TagGroupsInSideBar",
					"ConfigView.section.style.TagGroupsInSidebar");
			add(show_tag_groups, listSidebar);
			
			show_tag_groups.setIndent(1, true);

			show_tags.addEnabledOnSelection(show_tag_groups);
		}else{
			
			BooleanParameterImpl show_tags = new BooleanParameterImpl(
					"Library.TagInTabBar", "ConfigView.section.style.TagInTabbar");
			add(show_tags,listSidebar);

		}

			// library
		
		BooleanParameterImpl show_tag = new BooleanParameterImpl(
				"Library.ShowTagButtons", "ConfigView.section.style.ShowTagButtons");
		add(show_tag,listLibrary);

		BooleanParameterImpl show_filters_only = new BooleanParameterImpl(
				"Library.ShowTagButtons.FiltersOnly",
				"ConfigView.section.style.ShowTagButtons.FiltersOnly");
		add(show_filters_only,listLibrary);
		show_filters_only.setIndent(1, true);
		
		BooleanParameterImpl image_override = new BooleanParameterImpl(
				"Library.ShowTagButtons.ImageOverride",
				"ConfigView.section.style.ShowTagButtons.ImageOverride");
		add(image_override,listLibrary);
		image_override.setIndent(1, true);
		
		BooleanParameterImpl show_tag_comp_only = new BooleanParameterImpl(
				"Library.ShowTagButtons.CompOnly",
				"ConfigView.section.style.ShowTagButtons.CompOnly");
		add(show_tag_comp_only,listLibrary);
		show_tag_comp_only.setIndent(1, true);

		BooleanParameterImpl tag_inclusive = new BooleanParameterImpl(
				"Library.ShowTagButtons.Inclusive",
				"ConfigView.section.style.ShowTagButtons.Inclusive");
		add(tag_inclusive,listLibrary);
		tag_inclusive.setIndent(1, true);

		show_tag.addEnabledOnSelection( show_filters_only, image_override, show_tag_comp_only, tag_inclusive );

		IntListParameterImpl tag_align = new IntListParameterImpl(
				"Library.ShowTagButtons.Align",
				"ConfigView.section.style.ShowTagButtons.Align",
				new int[]{ 0, 1, 2, 3 },
				new String[]{ 
					"", 
					MessageText.getString( "label.left" ),
					MessageText.getString( "label.center" ),
					MessageText.getString( "label.right" )});					
		
		add(tag_align,listLibrary);
		
		BooleanParameterImpl col_config = new BooleanParameterImpl(
				"Library.EnableSepColConfig",
				"ConfigView.section.style.enableSeparateColConfig");
		add(col_config,listLibrary);
		
		add(new ParameterGroupImpl("v3.MainWindow.menu.view.sidebar",
				listSidebar));

		
		add(new ParameterGroupImpl("ConfigView.section.style.library",
				listLibrary));
	}
	
	@Override
	protected boolean
	isSWT()
	{
		return( true );
	}
	
	@Override
	protected void
	addAutoTagLine(
		List<Parameter> listAutoTag,
		int				index )
	{
		super.addAutoTagLine(listAutoTag, index);
		
		String paramName;
		
		if ( index == -1 ){
			paramName = SCFG_FILE_AUTO_TAG_NAME_DEFAULT;
		}else{
			paramName = SCFG_PREFIX_FILE_AUTO_TAG_NAME + (index == 0 ? "" : (" " + index));
		}
		
		ActionParameterImpl selectTag = new ActionParameterImpl(
				"", "GeneralView.menu.selectTracker");
		add( selectTag, listAutoTag);
		
		selectTag.addListener(param -> {
			
			Utils.execSWTThread(()->{
				BaseSwtParameter swtParam = paramMap.get(  selectTag );
				
				Control c = swtParam.getMainControl();
				
				Menu menu = new Menu( c );
				
				c.setMenu(menu);
				
				List<Tag> all_tags = TagManagerFactory.getTagManager().getTagType( TagType.TT_DOWNLOAD_MANUAL ).getTags();
				
				TagUIUtils.createTagSelectionMenu(menu, null, all_tags, (tag)->{
					
					COConfigurationManager.setParameter( paramName, tag.getTagName( true ));
				});
				
				menu.setVisible( true );
			});
		});
		
		if ( index == -1 ){
			
			add("swt_f0.1", new LabelParameterImpl(""), listAutoTag);
			
		}else{
			ActionParameterImpl deleteRow = new ActionParameterImpl( "", "" );
			
			deleteRow.setImageID( "delete" );
			
			add( deleteRow, listAutoTag);
	
			deleteRow.addListener(param -> {
				
				Utils.execSWTThread(()->{
					skipTidy = true;

					int num_tags = COConfigurationManager.getIntParameter( ICFG_FILES_AUTO_TAG_COUNT, 1);

						// always blank the current row as we don't want the values re-appearing
						// if rows re-added
					
					setAutoTagExts( index, "" );
					setAutoTagTag( index, "" );
					
					if ( index == num_tags-1 ){
						
							// last one
						
						if ( index > 0 ){
						
								// remove last
							
							COConfigurationManager.setParameter( ICFG_FILES_AUTO_TAG_COUNT, num_tags-1);
						}
					}else{
						
							// copy down 
						
						for ( int i=index+1; i<= num_tags;i++ ){
							
							String exts = getAutoTagExts( i );
							String tag	= getAutoTagTag( i );
						
							setAutoTagExts( i-1, exts );
							setAutoTagTag( i-1, tag );
						}
						
							// remove last
						
						COConfigurationManager.setParameter( ICFG_FILES_AUTO_TAG_COUNT, num_tags-1);
					}
					requestRebuild();
				});
			});
		}
	}
}
