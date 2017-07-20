/*
 * Created on 19-Nov-2004
 * Created by Paul Gardner
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */

package com.biglybt.ui.swt.views.tableitems.mytorrents;

/**
 * @author parg
 *
 */
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.debug.ObfuscateCellText;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;


public class TorrentPathItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener, ObfuscateCellText
{
	public static final Class DATASOURCE_TYPE = Download.class;

  public static final String COLUMN_ID = "torrentpath";

	/** Default Constructor */
  public TorrentPathItem(String sTableID) {
    super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_LEAD, 150, sTableID);
    setObfuscation(true);
    setRefreshInterval(INTERVAL_LIVE);
  }

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_CONTENT,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_ADVANCED);
	}

  @Override
  public void refresh(TableCell cell) {
    DownloadManager dm = (DownloadManager)cell.getDataSource();

    cell.setText((dm == null) ? "" : dm.getTorrentFileName());
  }

	@Override
	public String getObfuscatedText(TableCell cell) {
		return Debug.secretFileName(cell.getText());
	}
}