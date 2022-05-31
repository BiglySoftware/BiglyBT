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

import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.internat.MessageText;
import com.biglybt.pif.ui.config.Parameter;
import com.biglybt.pifimpl.local.ui.config.BooleanParameterImpl;
import com.biglybt.pifimpl.local.ui.config.IntListParameterImpl;
import com.biglybt.pifimpl.local.ui.config.ParameterGroupImpl;
import com.biglybt.ui.config.ConfigSectionInterfaceTags;
import com.biglybt.ui.swt.Utils;

public class 
ConfigSectionInterfaceTagsSWT
	extends ConfigSectionInterfaceTags
{
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
}
