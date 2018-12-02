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

public class ColumnAllTrackersStatus
	implements TableCellRefreshListener, TableColumnExtraInfoListener
{
	public static String COLUMN_ID = "status";

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
	ColumnAllTrackersStatus(
		TableColumn column)
	{
		column.setPosition( TableColumn.POSITION_LAST );
		column.setWidth(300);
		column.addListeners(this);
	}

	@Override
	public void
	refresh(
		TableCell cell )
	{
		AllTrackersViewEntry tr = (AllTrackersViewEntry)cell.getDataSource();
		
		String status = null;

		if ( tr != null ){

			status = tr.getStatusString();
		}

		if ( status == null ){

			status = "";
		}else{
			
			status = status.replaceAll( "[\r\n]+", " ");
		}

		if ( !cell.setSortValue(status) && cell.isValid()){

			return;
		}

		if (!cell.isShown()){

			return;
		}

		cell.setText(status);
	}
}
