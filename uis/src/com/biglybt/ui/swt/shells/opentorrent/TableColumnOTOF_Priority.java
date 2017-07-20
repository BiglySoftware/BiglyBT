/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */

package com.biglybt.ui.swt.shells.opentorrent;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.torrent.impl.TorrentOpenFileOptions;
import com.biglybt.pif.ui.tables.*;

public class TableColumnOTOF_Priority
implements TableCellRefreshListener, TableColumnExtraInfoListener
{
	public static final String COLUMN_ID = "priority";

  /** Default Constructor */
  public TableColumnOTOF_Priority(TableColumn column) {
  	column.initialize(TableColumn.ALIGN_LEAD, TableColumn.POSITION_LAST, 50);
  	column.addListeners(this);
  }

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			TableColumn.CAT_SETTINGS,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

  @Override
  public void refresh(TableCell cell) {
  	Object ds = cell.getDataSource();
  	if (!(ds instanceof TorrentOpenFileOptions)) {
  		return;
  	}
  	TorrentOpenFileOptions tfi = (TorrentOpenFileOptions) ds;

  	int 		priority;
  	String		text;

  	if ( tfi.isToDownload()){

  		priority = tfi.getPriority();

		if ( priority > 0 ) {

			text = MessageText.getString("FileItem.high");

			if ( priority > 1 ){

				text += " (" + priority + ")";
			}
		}else if (priority < 0 ){

			text = MessageText.getString("FileItem.low");

			if ( priority < -1 ){

				text += " (" + priority + ")";
			}
		}else{

			text = MessageText.getString("FileItem.normal");
		}
  	}else{
  		priority = Integer.MIN_VALUE;

  		text = "";
  	}

  	if ( cell.setSortValue(priority)){

  		cell.setText( text );
  	}
  }
}
