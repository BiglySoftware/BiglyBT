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
import com.biglybt.pif.ui.tables.*;

public class ColumnArchiveDLCategory
	implements TableCellRefreshListener, TableColumnExtraInfoListener
{
	public static String COLUMN_ID = "category";

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
	ColumnArchiveDLCategory(
		TableColumn column)
	{
		column.setPosition( TableColumn.POSITION_INVISIBLE );
		column.setWidth(100);
		column.addListeners(this);
	}

	@Override
	public void
	refresh(
		TableCell cell )
	{
		DownloadStubEx dl = (DownloadStubEx) cell.getDataSource();

		String text = "";

		if ( dl != null ){

			String cat = dl.getCategory();

			if ( cat != null ){
				
				text = cat;
			}
		}

		if ( !cell.setSortValue( text ) && cell.isValid()){

			return;
		}

		if (!cell.isShown()){

			return;
		}

		cell.setText( text );
	}
}
