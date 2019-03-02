/*
 * File    : SnubbedItem.java
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
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.ui.swt.views.table.TableRowSWT;
import com.biglybt.pif.ui.tables.*;

/**
 *
 * @author Olivier
 * @author TuxPaper (2004/Apr/19: modified to TableCellAdapter)
 */
public class LANItem
	extends CoreTableColumnSWT
	implements TableCellRefreshListener
{
	public static final String COLUMN_ID = "lan";

	private static TableRowSWT.ColorRequester	color_requester = ()-> 2;

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_CONNECTION,
		});
	}

	public LANItem(String table_id) {
		super(COLUMN_ID, ALIGN_CENTER, POSITION_INVISIBLE, 20, table_id);
		setRefreshInterval(INTERVAL_LIVE);
	}

	@Override
	public void refresh(TableCell cell) {
		PEPeer peer = (PEPeer) cell.getDataSource();
		boolean lan = (peer == null) ? false : peer.isLANLocal();

		if (!cell.setSortValue(lan ? 1 : 0) && cell.isValid())
			return;

		cell.setText(lan ? "*" : "");

		TableRow row = cell.getTableRow();
		
		if (row instanceof TableRowSWT) {
			
			((TableRowSWT)row).requestForegroundColor( color_requester, lan ? Colors.blue : null );
		}
	}
}
