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

package com.biglybt.ui.swt.columns.archivedls;


import com.biglybt.pif.download.DownloadStub.DownloadStubEx;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.pif.ui.tables.TableColumnExtraInfoListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.ui.swt.views.table.utils.TableColumnCreator;
import com.biglybt.ui.swt.views.tableitems.ColumnDateSizer;


public class
ColumnArchiveDLCompDate
	implements TableColumnExtraInfoListener, TableCellRefreshListener
{
	public static String COLUMN_ID = "archive.comp.date";

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
				TableColumn.CAT_TIME,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	public
	ColumnArchiveDLCompDate(
		TableColumn column)
	{
		column.setWidth(TableColumnCreator.DATE_COLUMN_WIDTH);
		column.addListeners(this);
	}

	@Override
	public void
	refresh(
		TableCell cell )
	{
		TableColumn tc = cell.getTableColumn();

		if (tc instanceof ColumnDateSizer) {

			DownloadStubEx dl = (DownloadStubEx) cell.getDataSource();

			((ColumnDateSizer) tc).refresh(cell, dl.getCompletionDate());
		}
	}
}
