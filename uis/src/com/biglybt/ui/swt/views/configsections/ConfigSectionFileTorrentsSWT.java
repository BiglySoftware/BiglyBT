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

import static com.biglybt.core.config.ConfigKeys.File.SCFG_PREFIX_WATCH_TORRENT_FOLDER_TAG;

import java.util.List;
import java.util.Map;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.tag.TagType;
import com.biglybt.pif.ui.config.Parameter;
import com.biglybt.pifimpl.local.ui.config.ActionParameterImpl;
import com.biglybt.pifimpl.local.ui.config.ParameterImpl;
import com.biglybt.ui.config.ConfigSectionFileTorrents;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.config.BaseSwtParameter;
import com.biglybt.ui.swt.views.utils.TagUIUtils;

public class
ConfigSectionFileTorrentsSWT
	extends ConfigSectionFileTorrents
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
	protected boolean
	isSWT()
	{
		return( true );
	}
	
	@Override
	protected void
	addImportLine(
		List<Parameter>		listWatchDirs,
		int					index )
	{
		super.addImportLine(listWatchDirs, index);
		
		String paramName = SCFG_PREFIX_WATCH_TORRENT_FOLDER_TAG + (index == 0 ? "" : (" " + index));
		
		ActionParameterImpl selectTag = new ActionParameterImpl(
				"", "GeneralView.menu.selectTracker");
		add( selectTag, listWatchDirs);
		
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
	}
}
