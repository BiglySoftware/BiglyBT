/*
 * File    : TimeToSendPieceItem.java
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

package com.biglybt.ui.swt.views.tableitems.peers;

import com.biglybt.core.util.TimeFormatter;

import com.biglybt.core.peer.PEPeer;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

/**
 *
 * @author Olivier
 * @author TuxPaper (2004/Apr/19: modified to TableCellAdapter)
 */
public class TimeToSendPieceItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{
  /** Default Constructor */
  public TimeToSendPieceItem(String table_id) {
    super("timetosend", ALIGN_TRAIL, POSITION_INVISIBLE, 70, table_id);
    setRefreshInterval(INTERVAL_LIVE);
  }

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_TIME,
		});
	}

  @Override
  public void refresh(TableCell cell) {
    PEPeer peer = (PEPeer)cell.getDataSource();
    long value = (peer == null) ? 0 : peer.getUploadHint();

    Comparable sortValue = cell.getSortValue();
    long oldValue = 0;
    if (sortValue instanceof Number) {
    	oldValue = ((Number)sortValue).longValue();
    }

    if (!cell.setSortValue(value) && cell.isValid())
      return;

    String text = TimeFormatter.format(value / 1000);
    if (oldValue > 0) {
    	text += ", " + TimeFormatter.format(oldValue / 1000);
    }
    cell.setText(text);
  }
}
