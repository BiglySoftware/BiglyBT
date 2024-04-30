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
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.views.table.utils.TableColumnSWTUtils;

public class ColumnFO_Size
	implements TableCellRefreshListener, TableColumnExtraInfoListener
{
	public static final String COLUMN_ID = "fileops_size";

	/**
	 *
	 * @param sTableID
	 */
	public ColumnFO_Size(TableColumn column) {
		column.initialize(TableColumn.ALIGN_TRAIL,TableColumn.POSITION_LAST, 60);
		column.addListeners(this);
		column.setRefreshInterval(TableColumn.INTERVAL_GRAPHIC);
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

		ProgressCallback prog = op.getTask().getProgressCallback();
				
		long size = prog==null?-1:prog.getSize();
		
		if ( cell.setSortValue( size )){
		
			cell.setText( size<=0?"":DisplayFormatters.formatByteCountToKiBEtc( size ));
			
			TableColumnSWTUtils.setSizeAlpha( cell, size );
		}
	}
}
