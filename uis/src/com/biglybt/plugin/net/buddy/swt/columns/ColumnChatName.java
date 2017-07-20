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

public class ColumnChatName
	implements TableCellRefreshListener, TableColumnExtraInfoListener
{
	public static String COLUMN_ID = "chat.name";

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			TableColumn.CAT_ESSENTIAL,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	/** Default Constructor */
	public ColumnChatName(TableColumn column) {
		column.setWidth(220);
		column.addListeners(this);
	}

	@Override
	public void refresh(TableCell cell) {
		ChatInstance chat = (ChatInstance) cell.getDataSource();
		String chat_name = null;
		if (chat != null) {
			chat_name = chat.getName();
		}

		if ( chat_name == null ){
			chat_name = "";
		}

		if (!cell.setSortValue(chat_name) && cell.isValid()) {
			return;
		}

		if (!cell.isShown()) {
			return;
		}

		cell.setText(chat_name);
	}
}
