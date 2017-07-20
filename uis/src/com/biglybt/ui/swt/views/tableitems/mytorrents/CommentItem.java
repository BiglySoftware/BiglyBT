/*
 * File    : CommentItem.java
 * Created : 26 Oct 2006
 * By      : Allan Crooks
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

import com.biglybt.core.download.DownloadManager;
import com.biglybt.ui.swt.TorrentUtil;
import com.biglybt.ui.swt.debug.ObfuscateCellText;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.tables.*;


/**
 * User-editable comment for a download.
 *
 * @author amc1
 */
public class CommentItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener, TableCellMouseListener, ObfuscateCellText
{
	public static final Class DATASOURCE_TYPE = Download.class;

  public static final String COLUMN_ID = "comment";

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] { CAT_CONTENT });
	}

	/** Default Constructor */
  public CommentItem(String sTableID) {
    super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_LEAD, 300, sTableID);
    setRefreshInterval(INTERVAL_LIVE);
    setType(TableColumn.TYPE_TEXT);
    setObfuscation(true);
    setMinWidth(50);
  }

  @Override
  public void refresh(TableCell cell) {
    String comment = null;
    DownloadManager dm = (DownloadManager)cell.getDataSource();
    comment = dm.getDownloadState().getUserComment();
    if (comment != null) {
    	comment = comment.replace('\r', ' ').replace('\n', ' ');
    }
    cell.setText((comment == null) ? "" : comment);
  }

	@Override
	public void cellMouseTrigger(TableCellMouseEvent event) {
		DownloadManager dm = (DownloadManager) event.cell.getDataSource();
		if (dm == null) {return;}

		event.skipCoreFunctionality = true;
		if (event.eventType != TableCellMouseEvent.EVENT_MOUSEDOUBLECLICK) {return;}
		TorrentUtil.promptUserForComment(new DownloadManager[] {dm});
	}

	@Override
	public String getObfuscatedText(TableCell cell) {
		DownloadManager dm = (DownloadManager)cell.getDataSource();
		return Integer.toHexString(dm.hashCode());
	}


}
