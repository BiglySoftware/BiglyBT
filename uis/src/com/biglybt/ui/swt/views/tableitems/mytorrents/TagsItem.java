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

package com.biglybt.ui.swt.views.tableitems.mytorrents;

import java.util.List;
import java.util.Objects;

import com.biglybt.core.tag.*;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.tables.*;

/** Display Category torrent belongs to.
 *
 * @author TuxPaper
 */
public class TagsItem
	extends CoreTableColumnSWT
	implements TableCellRefreshListener, TableCellToolTipListener
{
	private static final TagManager tag_manager = TagManagerFactory.getTagManager();

	public static final Class<Download> DATASOURCE_TYPE = Download.class;

	public static final String COLUMN_ID = "tags";

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] { CAT_CONTENT });
	}

	/** Default Constructor */
	public TagsItem(String sTableID) {
		super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_LEAD, 70, sTableID);
		setRefreshInterval(INTERVAL_LIVE);
	}

	@Override
	public void refresh(TableCell cell) {
		StringBuilder sb = new StringBuilder();

		try {
			Taggable taggable = (Taggable) cell.getDataSource();
			if (taggable == null) {
				return;
			}

			List<Tag> tags = tag_manager.getTagsForTaggable(
					TagType.TT_DOWNLOAD_MANUAL, taggable);

			if (tags.isEmpty()) {
				return;
			}

			tags = TagUtils.sortTags(tags);

			for (Tag tag : tags) {
				if (sb.length() != 0) {
					sb.append(", ");
				}
				sb.append(tag.getTagName(true));
			}

		} finally {
			cell.setText(sb.toString());
		}
	}

	@Override
	public void cellHover(TableCell cell) {
		Taggable taggable = (Taggable) cell.getDataSource();
		if (taggable == null) {
			return;
		}

		List<Tag> tags = tag_manager.getTagsForTaggable(TagType.TT_DOWNLOAD_MANUAL,
				taggable);
		tags = TagUtils.sortTags(tags);
		String group = null;
		boolean firstInGroup = true;
		StringBuilder sb = new StringBuilder();
		for (Tag tag : tags) {
			String newGroup = tag.getGroup();
			if (!Objects.equals(group, newGroup)) {
				group = newGroup;
				if (sb.length() > 0) {
					sb.append('\n');
					if (group == null) {
						sb.append('\n');
					}
				}
				if (group != null) {
					sb.append(group).append(": ");
				}
				firstInGroup = true;
			}
			if (firstInGroup) {
				firstInGroup = false;
			} else {
				sb.append(", ");
			}
			sb.append(tag.getTagName(true));
		}

		cell.setToolTip(sb.toString());
	}

	@Override
	public void cellHoverComplete(TableCell cell) {
		cell.setToolTip(null);
	}
}
