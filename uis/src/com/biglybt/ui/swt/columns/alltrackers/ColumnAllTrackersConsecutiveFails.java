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

import com.biglybt.core.tracker.AllTrackersManager.AllTrackersTracker;
import com.biglybt.pif.ui.tables.*;

public class ColumnAllTrackersConsecutiveFails
	implements TableCellRefreshListener, TableColumnExtraInfoListener
{
	public static String COLUMN_ID = "alltrackers.consecfail";

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
	ColumnAllTrackersConsecutiveFails(
		TableColumn column)
	{
		column.initialize(TableColumn.ALIGN_CENTER, TableColumn.POSITION_LAST, 80 );
		column.addListeners(this);
	}

	@Override
	public void
	refresh(
		TableCell cell )
	{
		AllTrackersTracker tr = (AllTrackersTracker) cell.getDataSource();

		long fails;

		if ( tr != null ){

			fails = tr.getConsecutiveFails();
			
			if ( fails == 0 ){
				
				if ( tr.getLastGoodTime() == 0 ){
					
					fails = -1;
				}
			}
		}else{
			
			fails = 0;
		}

		if ( !cell.setSortValue(fails) && cell.isValid()){

			return;
		}

		cell.setText(fails<=0?"":String.valueOf( fails ));
	}
}
