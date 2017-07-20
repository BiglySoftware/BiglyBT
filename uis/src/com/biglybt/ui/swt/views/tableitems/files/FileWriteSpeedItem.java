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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.ui.swt.views.tableitems.files;

import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;


public class FileWriteSpeedItem
	extends CoreTableColumnSWT
	implements TableCellRefreshListener
{
	public
	FileWriteSpeedItem()
	{
		super( "writerate", ALIGN_TRAIL, POSITION_INVISIBLE, 60, TableManager.TABLE_TORRENT_FILES);

		setRefreshInterval( INTERVAL_LIVE );

		setMinWidthAuto(true);
	}

	@Override
	public void
	fillTableColumnInfo(
		TableColumnInfo info )
	{
		info.addCategories( new String[]{
			CAT_BYTES,
		});

		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE );
	}

	@Override
	public void
	refresh(
		TableCell cell )
	{
		DiskManagerFileInfo fileInfo = (DiskManagerFileInfo) cell.getDataSource();

		int speed = 0;

		if ( fileInfo != null ){

			speed = fileInfo.getWriteBytesPerSecond();
		}

		if (!cell.setSortValue(speed) && cell.isValid()) {

			return;
		}

		cell.setText( speed==0?"":DisplayFormatters.formatByteCountToKiBEtcPerSec( speed ));
	}
}
