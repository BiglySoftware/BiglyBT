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

package com.biglybt.ui.swt.views.tableitems.mytorrents;

import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.ui.swt.views.table.utils.TableColumnSWTUtils;


public class ColumnSizeWithDND
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{
	public static final Class DATASOURCE_TYPE = Download.class;

  public static final String COLUMN_ID = "sizewithdnd";

	/** Default Constructor */
  public ColumnSizeWithDND(String sTableID) {
    super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_TRAIL, 70, sTableID);
    setRefreshInterval(INTERVAL_LIVE);

    setPosition(POSITION_LAST);
  }

  @Override
  public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_SHARING,
			CAT_BYTES
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_ADVANCED);
	}

  @Override
  public void refresh(TableCell cell) {
  	long value = 0;
		Object ds = cell.getDataSource();
		if (ds instanceof DownloadManager) {
			DownloadManager dm = (DownloadManager) ds;
			value = dm.getSize();
		} else if (ds instanceof DiskManagerFileInfo) {
			DiskManagerFileInfo fileInfo = (DiskManagerFileInfo) ds;
			value = fileInfo.getLength();
		}

    if (!cell.setSortValue(value) && cell.isValid())
      return;

    cell.setText(DisplayFormatters.formatByteCountToKiBEtc(value));
    
    TableColumnSWTUtils.setSizeAlpha( cell, value );
  }
}
