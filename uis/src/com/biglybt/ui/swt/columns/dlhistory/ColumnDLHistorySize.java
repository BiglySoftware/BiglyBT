/*
 * Copyright (C) 2013 Azureus Software, Inc. All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.biglybt.ui.swt.columns.dlhistory;

import com.biglybt.core.history.DownloadHistory;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.views.table.utils.TableColumnSWTUtils;

public class ColumnDLHistorySize
	implements TableCellRefreshListener, TableColumnExtraInfoListener
{
	public static String COLUMN_ID = "size";

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
	ColumnDLHistorySize(
		TableColumn column)
	{
		column.setWidth(80);
		column.setAlignment( TableColumn.ALIGN_TRAIL );
		column.setRefreshInterval( TableColumn.INTERVAL_INVALID_ONLY );
		column.addListeners(this);
	}

	@Override
	public void
	refresh(
		TableCell cell )
	{
		DownloadHistory dl = (DownloadHistory)cell.getDataSource();

		long	size = 0;

		if ( dl != null ){

			size = dl.getSize();
		}

		if ( !cell.setSortValue(size) && cell.isValid()){

			return;
		}

		if (!cell.isShown()){

			return;
		}

		cell.setText(size<=0?"":DisplayFormatters.formatByteCountToKiBEtc( size ));
		
		TableColumnSWTUtils.setSizeAlpha( cell, size );
	}
}