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

package com.biglybt.ui.swt.columns.archivedls;

import com.biglybt.core.util.Constants;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.pif.download.DownloadStub.DownloadStubEx;
import com.biglybt.pif.ui.tables.*;


public class ColumnArchiveShareRatio
	implements TableCellRefreshListener, TableColumnExtraInfoListener
{
	public static String COLUMN_ID = "shareRatio";

	@Override
	public void
	fillTableColumnInfo(
		TableColumnInfo info)
	{
		info.addCategories(new String[] {
			TableColumn.CAT_PROGRESS,
		});

		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	public
	ColumnArchiveShareRatio(
		TableColumn column)
	{
		column.setWidth(70);
		column.setAlignment(TableColumn.ALIGN_TRAIL );
		column.setMinWidthAuto(true);

		column.addListeners(this);
	}

	@Override
	public void
	refresh(
		TableCell cell )
	{
		DownloadStubEx dl = (DownloadStubEx) cell.getDataSource();

		String 	sr_str;

		int		sr = -1;

		if ( dl != null ){

			sr = dl.getShareRatio();

			if ( sr < 0 ){

				sr_str = "";	// migration

			}else if ( sr == Integer.MAX_VALUE ){

		        sr_str = Constants.INFINITY_STRING;

		    }else{

		        sr_str = DisplayFormatters.formatDecimal((double) sr / 1000, 3);
		    }
		}else{

			sr_str = "";
		}

		if ( !cell.setSortValue( sr ) && cell.isValid()){

		      return;
		}

		cell.setText( sr_str );
	}
}
