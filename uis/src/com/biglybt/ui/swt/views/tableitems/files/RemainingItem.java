/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.ui.swt.views.tableitems.files;


import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.ui.swt.views.table.utils.TableColumnSWTUtils;
import com.biglybt.pif.ui.tables.*;


public class 
RemainingItem
	extends CoreTableColumnSWT
	implements TableCellRefreshListener
{
	public 
	RemainingItem(
		String TableID )
	{
		super("remaining", ALIGN_TRAIL, POSITION_LAST, 70, TableID);
		setRefreshInterval(INTERVAL_LIVE);
	}

	@Override
	public void 
	fillTableColumnInfo(
		TableColumnInfo info) 
	{
		info.addCategories(new String[] {
				CAT_BYTES,
		});
		
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	@Override
	public void 
	refresh(
		TableCell cell ) 
	{
		DiskManagerFileInfo fileInfo = (DiskManagerFileInfo)cell.getDataSource();

		long value;

		if ( fileInfo == null || fileInfo.isSkipped() || fileInfo.isMetadataDownload()){

			value = -1;
			
		}else{
			
			value = fileInfo.getLength() - fileInfo.getDownloaded();
		}

		if ( !cell.setSortValue( value ) && cell.isValid()){
			
			return;
		}

		String text = value<0?"":DisplayFormatters.formatByteCountToKiBEtc(value);

		cell.setNumeric( value );

		cell.setText( text );

		TableColumnSWTUtils.setSizeAlpha( cell, value );
	}
}
