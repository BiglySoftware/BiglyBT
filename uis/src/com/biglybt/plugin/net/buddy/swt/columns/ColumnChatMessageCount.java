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

import java.util.Map;

import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.tables.*;

import com.biglybt.plugin.net.buddy.BuddyPluginBeta;
import com.biglybt.plugin.net.buddy.BuddyPluginUtils;
import com.biglybt.plugin.net.buddy.BuddyPluginBeta.*;

public class ColumnChatMessageCount
	implements TableCellRefreshListener, TableColumnExtraInfoListener
{
	public static String COLUMN_ID = "chat.msg.count";

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			TableColumn.CAT_ESSENTIAL,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	/** Default Constructor */
	public ColumnChatMessageCount(TableColumn column) {
		column.setWidth(60);
		column.setAlignment( TableColumn.ALIGN_TRAIL );
		column.setRefreshInterval( TableColumn.INTERVAL_LIVE );
		column.addListeners(this);
	}

	@Override
	public void refresh(TableCell cell) {

		Object dataSource = cell.getDataSource();

		int num = -1;

		if ( dataSource instanceof Download ){

			Download dl = (Download) dataSource;

			BuddyPluginBeta beta = BuddyPluginUtils.getBetaPlugin();

			if ( beta != null && beta.getEnableAutoDownloadChats()){
				
				ChatInstance chat = beta.peekChatInstance(dl);

				if (chat != null){

					num = chat.getMessageCount( true );

				}else{

					Map<String,Object> peek_data = beta.peekChat( dl, true );

					if ( peek_data != null ){

						Number	message_count 	= (Number)peek_data.get( "m" );

						if ( message_count != null ){

							num = message_count.intValue();
						}
					}
				}
			}
		} else {

			ChatInstance chat = (ChatInstance) cell.getDataSource();

			if (chat != null){

				num = chat.getMessageCount( true );
			}
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
