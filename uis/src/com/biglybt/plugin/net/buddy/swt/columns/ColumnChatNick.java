/* 
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.plugin.net.buddy.swt.columns;

import com.biglybt.pif.ui.tables.*;

import com.biglybt.plugin.net.buddy.BuddyPluginBeta.*;

public class ColumnChatNick
	implements TableCellRefreshListener, TableColumnExtraInfoListener
{
	public static String COLUMN_ID = "chat.nick";

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			TableColumn.CAT_ESSENTIAL,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	/** Default Constructor */
	public ColumnChatNick(TableColumn column) {
		column.initialize(TableColumn.ALIGN_LEAD,TableColumn.POSITION_INVISIBLE, 120);
		column.addListeners(this);
		column.setRefreshInterval(TableColumn.INTERVAL_GRAPHIC);
		column.setType(TableColumn.TYPE_TEXT_ONLY);
	}

	@Override
	public void refresh(TableCell cell) {
		ChatInstance chat = (ChatInstance) cell.getDataSource();
		String nick = null;
		if (chat != null) {
			nick = chat.getNickname( false);
			if ( chat.isSharedNickname()){
				nick = "[ " + nick + " ]";
			}
		}

		if ( nick == null ){
			nick = "";
		}

		if (!cell.setSortValue(nick) && cell.isValid()) {
			return;
		}

		if (!cell.isShown()) {
			return;
		}

		cell.setText(nick);
	}
}
