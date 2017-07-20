/*
 * File    : DoneItem.java
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
import com.biglybt.core.download.DownloadManagerStats;
import com.biglybt.core.util.DisplayFormatters;

import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;



/** % Done column in My Torrents
 *
 * @author Olivier
 * @author TuxPaper (2004/Apr/17: modified to TableCellAdapter)
 */
public class DoneItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{
	public static final Class DATASOURCE_TYPE = Download.class;

  public static final String COLUMN_ID = "done";

	/** Default Constructor */
  public DoneItem(String sTableID) {
    super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_TRAIL, 55, sTableID);
    addDataSourceType(DiskManagerFileInfo.class);
    setRefreshInterval(INTERVAL_LIVE);
    if (sTableID.equals(TableManager.TABLE_MYTORRENTS_INCOMPLETE))
      setPosition(POSITION_LAST);
    else
      setPosition(POSITION_INVISIBLE);
    setMinWidthAuto(true);
  }

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] { CAT_PROGRESS });
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

  @Override
  public void refresh(TableCell cell) {
  	int value;
  	Object ds = cell.getDataSource();
  	if (ds instanceof DownloadManager) {
  			// show amount completed of non-dnd files as makes more sense
  		DownloadManager dm = (DownloadManager) ds;
  		DownloadManagerStats stats = dm.getStats();
  		value = stats.getPercentDoneExcludingDND();
  	} else if (ds instanceof DiskManagerFileInfo) {
  		DiskManagerFileInfo fileInfo = (DiskManagerFileInfo) ds;
			long length = fileInfo.getLength();
			if (length == 0) {
				value = 1000;
			} else {
				value = (int) (fileInfo.getDownloaded() * 1000 / length);
			}
  	} else {
  		return;
  	}
    if (!cell.setSortValue(value) && cell.isValid())
      return;
    cell.setText(DisplayFormatters.formatPercentFromThousands(value));
  }
}
