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

import java.math.BigInteger;

import com.biglybt.core.history.DownloadHistory;
import com.biglybt.core.util.ByteFormatter;
import com.biglybt.pif.ui.tables.*;

public class ColumnDLHistoryHash
	implements TableCellRefreshListener, TableColumnExtraInfoListener
{
	public static String COLUMN_ID = "hash";

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
	ColumnDLHistoryHash(
		TableColumn column)
	{
		column.setWidth(200);
		column.addListeners(this);
	}

	@Override
	public void
	refresh(
		TableCell cell )
	{
		DownloadHistory dl = (DownloadHistory)cell.getDataSource();

		byte[]	hash = null;

		if ( dl != null ){

			hash = dl.getTorrentHash();
		}

		BigInteger sort = hash==null?new BigInteger(-1,new byte[0]):new BigInteger(1,hash);

		if ( !cell.setSortValue(sort) && cell.isValid()){

			return;
		}

		if (!cell.isShown()){

			return;
		}

		String str = hash==null?"":ByteFormatter.encodeString( hash );

		cell.setText(str);
	}
}
