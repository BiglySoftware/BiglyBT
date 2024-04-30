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
import com.biglybt.core.util.Debug;
import com.biglybt.pif.download.DownloadStub;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.debug.ObfuscateCellText;

public class ColumnArchiveDLSaveLocation
	implements TableCellRefreshListener, TableColumnExtraInfoListener, ObfuscateCellText
{
	public static String COLUMN_ID = "path";

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
	ColumnArchiveDLSaveLocation(
		TableColumn column)
	{
		column.setWidth(400);
		column.addListeners(this);
	}

	@Override
	public void
	refresh(
		TableCell cell )
	{
		DownloadStub dl = (DownloadStub) cell.getDataSource();

		String path = null;

		if ( dl != null ){

			path = dl.getSavePath();
		}

		if ( path == null ){

			path = "";
		}

		if ( !cell.setSortValue(path) && cell.isValid()){

			return;
		}

		if (!cell.isShown()){

			return;
		}

		cell.setText(path);
	}

	@Override
	public String
	getObfuscatedText(
		TableCell 	cell)
	{
		DownloadStub dl = (DownloadStub) cell.getDataSource();

		if ( dl == null ){

			return( "" );
		}

		return( Debug.secretFileName( dl.getSavePath()));
	}
}
