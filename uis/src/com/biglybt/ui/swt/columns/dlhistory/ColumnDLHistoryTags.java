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

package com.biglybt.ui.swt.columns.dlhistory;

import com.biglybt.core.history.DownloadHistory;
import com.biglybt.pif.ui.tables.*;

public class ColumnDLHistoryTags
	implements TableCellRefreshListener, TableColumnExtraInfoListener
{
	public static String COLUMN_ID = "tags";

	@Override
	public void
	fillTableColumnInfo(
		TableColumnInfo info)
	{
		info.addCategories(new String[] {
			TableColumn.CAT_CONTENT,
		});

		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	public
	ColumnDLHistoryTags(
		TableColumn column)
	{
		column.setWidth(100);
		column.addListeners(this);
	}

	@Override
	public void
	refresh(
		TableCell cell )
	{
		DownloadHistory dl = (DownloadHistory)cell.getDataSource();

		String tags_str = "";

		if ( dl != null ){

			String[] tags = dl.getTags();
			
			for ( String tag: tags ){
				
				tags_str += (tags_str.isEmpty()?"":", ") + tag;
			}
		}

		if ( !cell.setSortValue(tags_str) && cell.isValid()){

			return;
		}

		if (!cell.isShown()){

			return;
		}

		cell.setText(tags_str);
	}
}
