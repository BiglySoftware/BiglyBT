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
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.TimeFormatter;
import com.biglybt.pif.ui.tables.*;

public class ColumnAllTrackersFailingFor
	implements TableCellRefreshListener, TableColumnExtraInfoListener
{
	public static String COLUMN_ID = "alltrackers.failingfor";

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
	ColumnAllTrackersFailingFor(
		TableColumn column)
	{
		column.initialize(TableColumn.ALIGN_CENTER, TableColumn.POSITION_LAST, 80 );
		column.setRefreshInterval( TableColumn.INTERVAL_LIVE );
		column.addListeners(this);
	}

	@Override
	public void
	refresh(
		TableCell cell )
	{
		AllTrackersTracker tr = (AllTrackersTracker) cell.getDataSource();

		long 	sort = 0;
		String	str	= "";
		
		if ( tr != null ){

			long fails = tr.getConsecutiveFails();
			
			if ( fails > 0 ){
				
				long since = tr.getFailingSinceTime();
				
				if ( since > 0 ){
					
					sort = SystemTime.getCurrentTime() - since;
					
					long[] new_sort = { 0 };
					
					str = TimeFormatter.format3(( sort )/1000, new_sort );
					
					sort = new_sort[0]*1000;
				}
			}
		}

		if ( !cell.setSortValue(sort) && cell.isValid()){

			return;
		}

		cell.setText( str );
	}
}
