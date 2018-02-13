/*
 * File    : IpItem.java
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

import com.biglybt.core.peer.PEPeer;
import com.biglybt.ui.swt.debug.ObfuscateCellText;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;

/**
 *
 * @author Olivier
 * @author TuxPaper (2004/Apr/19: modified to TableCellAdapter)
 */
public class IpItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener, ObfuscateCellText
{
	public static final String COLUMN_ID = "ip";

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_PEER_IDENTIFICATION,
			CAT_CONNECTION
		});
	}

  /** Default Constructor */
  public IpItem(String table_id) {
    super(COLUMN_ID, POSITION_LAST, 100, table_id);
    setObfuscation(true);
   }

  @Override
  public void refresh(TableCell cell) {
    PEPeer peer = (PEPeer)cell.getDataSource();
    String sText = (peer == null) ? "" : peer.getIp();

    if (cell.setText(sText) || !cell.isValid()) {
      String[] sBlocks = sText.split("\\.");
      if (sBlocks.length == 4) {
        try {
          long l = (Long.parseLong(sBlocks[0]) << 24) +
                   (Long.parseLong(sBlocks[1]) << 16) +
                   (Long.parseLong(sBlocks[2]) << 8) +
                   Long.parseLong(sBlocks[3]);
          cell.setSortValue(l);
        } catch (Exception e) { e.printStackTrace(); /* ignore */ }
      }
    }
  }

  @Override
  public String getObfuscatedText(TableCell cell) {
	String text = cell.getText();
  	return text.length() > 3?text.substring(0, 3):text;
  }
}
