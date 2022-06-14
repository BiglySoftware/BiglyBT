/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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

package com.biglybt.ui.swt.views.tableitems.archivedfiles;


import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.ui.swt.views.table.utils.TableColumnSWTUtils;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.pif.download.DownloadStub.DownloadStubFile;
import com.biglybt.pif.ui.tables.*;



public class
SizeItem
	extends CoreTableColumnSWT
	implements TableCellLightRefreshListener
{
	public
	SizeItem(
		String tableID )
	{
		super( "size", ALIGN_TRAIL, POSITION_LAST, 70, tableID );
	}

	@Override
	public void
	fillTableColumnInfo(
		TableColumnInfo info )
	{
		info.addCategories(new String[] {
			CAT_BYTES,
		});

		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	@Override
	public void
	refresh(
		TableCell 	cell,
		boolean 	sortOnlyRefresh)
	{
		DownloadStubFile fileInfo = (DownloadStubFile) cell.getDataSource();

		long size;

		if ( fileInfo == null ){

			size = 0;

		}else{

			size = fileInfo.getLength();
		}

		if( !cell.setSortValue( size ) && cell.isValid()){

			return;
		}

		if ( size < 0 ){

				// skipped

			cell.setText( "(" + DisplayFormatters.formatByteCountToKiBEtc(-size) + ")");

		}else{

			cell.setText(DisplayFormatters.formatByteCountToKiBEtc(size));
		}
		
		TableColumnSWTUtils.setSizeAlpha( cell, size );
	}

	@Override
	public void
	refresh(
		TableCell cell )
	{
		refresh(cell, false);
	}
}
