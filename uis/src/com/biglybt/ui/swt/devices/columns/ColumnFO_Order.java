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

package com.biglybt.ui.swt.devices.columns;


import com.biglybt.core.CoreOperation;
import com.biglybt.core.CoreOperationTask.ProgressCallback;
import com.biglybt.pif.ui.tables.*;

public class ColumnFO_Order
	implements TableCellRefreshListener, TableColumnExtraInfoListener
{
	public static final String COLUMN_ID = "fileops_order";

	/**
	 *
	 * @param sTableID
	 */
	public ColumnFO_Order(TableColumn column) {
		column.initialize(TableColumn.ALIGN_TRAIL, TableColumn.POSITION_INVISIBLE, 30 );
		column.addListeners(this);
		column.setRefreshInterval(TableColumn.INTERVAL_GRAPHIC);
		column.setType(TableColumn.TYPE_TEXT_ONLY);
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			TableColumn.CAT_PROGRESS,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	@Override
	public void 
	refresh(
		TableCell cell )
	{
		CoreOperation op =  (CoreOperation)cell.getDataSource();

		int order = -1;
		
		if ( op != null ){
			
			ProgressCallback cb = op.getTask().getProgressCallback();
		
			if ( cb != null ){
				
				if ( cb.isAutoPause()){
				
					order = cb.getIndicativeOrder();
				}
			}
		}

		if (!cell.setSortValue(order) && cell.isValid()){
			
		      return;
		}	
		
		cell.setText( order <=0?"":String.valueOf( order ));
	}
}
