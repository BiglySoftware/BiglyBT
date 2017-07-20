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

package com.biglybt.ui.swt.views.tableitems.peers;

import com.biglybt.core.peer.PEPeer;

import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

/**
 *
 */
public class MessagingItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{
	public static final String COLUMN_ID = "Messaging";

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_PROTOCOL,
		});
	}

	/** Default Constructor */
  public MessagingItem(String table_id) {
    super(COLUMN_ID, ALIGN_CENTER, POSITION_INVISIBLE, 40, table_id);
    setRefreshInterval(INTERVAL_LIVE);
  }

  @Override
  public void refresh(TableCell cell) {
    PEPeer peer = (PEPeer)cell.getDataSource();
    int value = (peer == null) ? -1 : peer.getMessagingMode();

    if (!cell.setSortValue(value) && cell.isValid())
      return;

    String text;

    switch (value) {
    case PEPeer.MESSAGING_BT_ONLY:
		text = "";
		break;
	case PEPeer.MESSAGING_LTEP:
		text = "LT";
		break;
	case PEPeer.MESSAGING_AZMP:
		text = "AZ";
		break;
	case PEPeer.MESSAGING_EXTERN:
		text = "Plugin";
		break;
	default:
		text = "";
		break;
	}

    cell.setText(text);
  }
}
