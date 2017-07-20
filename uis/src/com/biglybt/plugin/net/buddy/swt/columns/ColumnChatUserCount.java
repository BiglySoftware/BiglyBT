/*
 * Copyright (C) 2013 Azureus Software, Inc. All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.biglybt.plugin.net.buddy.swt.columns;

import com.biglybt.pif.ui.tables.*;

import com.biglybt.plugin.net.buddy.BuddyPluginBeta.*;

public class ColumnChatUserCount
	implements TableCellRefreshListener, TableColumnExtraInfoListener
{
	public static String COLUMN_ID = "chat.user.count";

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			TableColumn.CAT_ESSENTIAL,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	/** Default Constructor */
	public ColumnChatUserCount(TableColumn column) {
		column.setWidth(40);
		column.setAlignment( TableColumn.ALIGN_CENTER );
		column.setRefreshInterval( TableColumn.INTERVAL_LIVE );
		column.addListeners(this);
	}

	@Override
	public void refresh(TableCell cell) {
		ChatInstance chat = (ChatInstance) cell.getDataSource();
		int num = -1;
		if (chat != null) {
			num = chat.getEstimatedNodes();
		}

		if (!cell.setSortValue(num) && cell.isValid()) {
			return;
		}

		if (!cell.isShown()) {
			return;
		}

		cell.setText(num==-1?"":(num<100?String.valueOf( num ):"100+"));
	}
}
