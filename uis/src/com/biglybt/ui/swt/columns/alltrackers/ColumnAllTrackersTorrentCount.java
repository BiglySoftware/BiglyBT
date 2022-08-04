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

package com.biglybt.ui.swt.columns.alltrackers;

import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.views.skin.SBC_AllTrackersView.AllTrackersViewEntry;

public class ColumnAllTrackersTorrentCount
	implements TableCellRefreshListener, TableColumnExtraInfoListener
{
	public static String COLUMN_ID = "alltrackers.torrentcount";

	@Override
	public void
	fillTableColumnInfo(
		TableColumnInfo info)
	{
		info.addCategories(new String[] {
			TableColumn.CAT_ESSENTIAL,
		});

		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	public
	ColumnAllTrackersTorrentCount(
		TableColumn column)
	{
		column.initialize(TableColumn.ALIGN_CENTER, TableColumn.POSITION_INVISIBLE, 80 );
		column.addListeners(this);
	}

	@Override
	public void
	refresh(
		TableCell cell )
	{
		AllTrackersViewEntry tr = (AllTrackersViewEntry)cell.getDataSource();
		
		long count;

		if ( tr != null ){

			count = tr.getTorrentCount();
	
		}else{
			
			count = -1;
		}

		if ( !cell.setSortValue(count) && cell.isValid()){

			return;
		}

		cell.setText(count<0?"":String.valueOf( count ));
	}
}
