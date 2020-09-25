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
import com.biglybt.core.internat.MessageText;
import com.biglybt.pif.ui.tables.*;

public class ColumnFO_Type
	implements TableCellRefreshListener, TableColumnExtraInfoListener
{
	public static final String COLUMN_ID = "fileops_type";

	/**
	 *
	 * @param sTableID
	 */
	public ColumnFO_Type(TableColumn column) {
		column.initialize(TableColumn.ALIGN_LEAD, TableColumn.POSITION_LAST, 70);
		column.addListeners(this);
		column.setRefreshInterval(TableColumn.INTERVAL_INVALID_ONLY);
		column.setType(TableColumn.TYPE_TEXT_ONLY);
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			TableColumn.CAT_ESSENTIAL,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	@Override
	public void 
	refresh(
		TableCell cell )
	{
		CoreOperation op = (CoreOperation) cell.getDataSource();
		
		if ( op == null ){
			
			return;
		}

		int type = op.getOperationType();

		String	suffix;
		
		switch( type ){
			
			case CoreOperation.OP_FILE_MOVE:{
				
				suffix = "move";
				
				break;
			}
			case CoreOperation.OP_DOWNLOAD_EXPORT:{
				
				suffix = "export";
				
				break;
			}
			case CoreOperation.OP_DOWNLOAD_ALLOCATION:{
				
				suffix = "alloc";
				
				break;
			}
			case CoreOperation.OP_DOWNLOAD_CHECKING:{
				
				suffix = "check";
				
				break;
			}
			default:{
				
				suffix = "unknown";
			}
		}

		cell.setText( MessageText.getString( "label.fop." + suffix ));
	}
}
