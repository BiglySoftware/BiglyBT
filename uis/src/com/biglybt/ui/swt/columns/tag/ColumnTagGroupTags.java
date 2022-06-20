/*
 * File    : CategoryItem.java
 * Created : 01 feb. 2004
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

package com.biglybt.ui.swt.columns.tag;


import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.pif.ui.tables.TableColumnExtraInfoListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.ui.swt.views.tableitems.TagsColumnHelper;

import java.util.*;

import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagGroup;
import com.biglybt.core.tag.TagManager;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.tag.TagType;

/** Display Category torrent belongs to.
 *
 * @author TuxPaper
 */
public class 
ColumnTagGroupTags
	implements TagsColumnHelper, TableColumnExtraInfoListener
{
	private static TagManager tag_manager = TagManagerFactory.getTagManager();

	public static final Class DATASOURCE_TYPE = Download.class;
	
	private static int[]	interesting_tts = { TagType.TT_DOWNLOAD_MANUAL, TagType.TT_DOWNLOAD_CATEGORY };

	private TagGroup	tag_group;
	
	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			TableColumn.CAT_CONTENT,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	public 
	ColumnTagGroupTags(
		TableColumn column, TagGroup	tg ) 
	{
		column.initialize(TableColumn.ALIGN_LEAD, TableColumn.POSITION_INVISIBLE, 70);
		column.addListeners(this);
		column.setRefreshInterval(TableColumn.INTERVAL_LIVE);
		column.setIconReference("image.tag.column", false );

		tag_group	= tg;
	}
	
	@Override
	public List<Tag> 
	getTags(
		TableCell cell)
	{
		List<Tag> result = new ArrayList<Tag>(16);
		
		Download dm = (Download)cell.getDataSource();
		
		if (dm != null) {
			
			List<Tag> tags = tag_manager.getTagsForTaggable( interesting_tts, PluginCoreUtils.unwrap( dm ) );

			if ( tags.size() > 0 ){

				for ( Tag t: tags ){

					if ( t.getGroupContainer() == tag_group ){
						
						result.add( t );
					}
				}
			}
		}
		
		return( result );
	}
}
