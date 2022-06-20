/*
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

package com.biglybt.ui.swt.views.tableitems.mytorrents;

import java.util.*;

import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagManager;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.tag.TagType;
import com.biglybt.core.tag.Taggable;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.views.tableitems.TagsColumn;

public class TagsItem
	extends TagsColumn
{
	private static final TagManager tag_manager = TagManagerFactory.getTagManager();

	public static final String COLUMN_ID = "tags";

	public TagsItem(String tableID) {
		super( tableID, COLUMN_ID );
	}

	public List<Tag>
	getTags(
		TableCell cell )
	{
		Taggable taggable = (Taggable) cell.getDataSource();
		
		if ( taggable == null ){
			
			return( Collections.emptyList());
		}

		List<Tag> tags = tag_manager.getTagsForTaggable( TagType.TT_DOWNLOAD_MANUAL, taggable);

		return( tags );
	}
}
