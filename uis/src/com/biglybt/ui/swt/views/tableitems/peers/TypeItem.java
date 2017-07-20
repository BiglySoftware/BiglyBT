/*
 * File    : TypeItem.java
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

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

/**
 *
 * @author Olivier
 * @author TuxPaper (2004/Apr/19: modified to TableCellAdapter)
 */
public class TypeItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener, TableCellToolTipListener
{
  /** Default Constructor */
  public TypeItem(String table_id) {
    super("T", ALIGN_CENTER, POSITION_LAST, 20, table_id);
    setRefreshInterval(INTERVAL_LIVE);
  }

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_CONNECTION,
		});
	}

  @Override
  public void refresh(TableCell cell) {
    PEPeer peer = (PEPeer)cell.getDataSource();
    long value = (peer == null) ? 0 : (peer.isIncoming() ? 1 : 0);

    if (!cell.setSortValue(value) && cell.isValid())
      return;

    cell.setText((value == 1) ? "R" : "L");
  }

	@Override
	public void cellHover(TableCell cell) {
		String ID = "PeersView.T." + cell.getText() + ".tooltip";
		String sTooltip = MessageText.getString(ID, "");
		if (sTooltip.length() > 0)
			cell.setToolTip(sTooltip);
	}

	@Override
	public void cellHoverComplete(TableCell cell) {
		cell.setToolTip(null);
	}
}
