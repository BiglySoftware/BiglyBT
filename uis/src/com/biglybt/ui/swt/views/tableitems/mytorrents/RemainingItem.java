/*
 * File    : NameItem.java
 * Created : 24 nov. 2003
 * By      : Olivier
 *
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

import com.biglybt.pif.download.DownloadTypeIncomplete;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;


/**
 *
 * @author Olivier
 * @author TuxPaper (2004/Apr/17: modified to TableCellAdapter)
 */
public class RemainingItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{
	public static final Class DATASOURCE_TYPE = DownloadTypeIncomplete.class;

  public static final String COLUMN_ID = "remaining";

	/** Default Constructor */
  public RemainingItem(String sTableID) {
    super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_TRAIL, 70, sTableID);
		addDataSourceType(DiskManagerFileInfo.class);
    setRefreshInterval(INTERVAL_LIVE);
  }

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_CONTENT,
			CAT_PROGRESS
		});
	}

  @Override
  public void refresh(TableCell cell) {
    long lRemaining = getRemaining(cell);

    if( !cell.setSortValue( lRemaining ) && cell.isValid() ) {
      return;
    }

    cell.setText(DisplayFormatters.formatByteCountToKiBEtc(lRemaining));
  }

  private long getRemaining(TableCell cell) {
  	Object ds = cell.getDataSource();
  	if (ds instanceof DownloadManager) {
      DownloadManager manager = (DownloadManager)cell.getDataSource();
      if (manager != null) {
      	return( manager.getStats().getRemainingExcludingDND());
      }
  	} else if (ds instanceof DiskManagerFileInfo) {
  		DiskManagerFileInfo fileInfo = (DiskManagerFileInfo) ds;
  		return fileInfo.getLength() - fileInfo.getDownloaded();
  	}
  	return 0;
  }
}
