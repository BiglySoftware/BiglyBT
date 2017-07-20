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

import com.biglybt.core.torrent.impl.TorrentOpenFileOptions;
import com.biglybt.pif.ui.tables.*;

import com.biglybt.ui.common.table.TableColumnCore;

public class TableColumnOTOF_Position
implements TableCellRefreshListener, TableColumnExtraInfoListener
{
	public static final String COLUMN_ID = "#";

  /** Default Constructor */
  public TableColumnOTOF_Position(TableColumn column) {
  	column.initialize(TableColumn.ALIGN_TRAIL, TableColumn.POSITION_LAST, 40);
  	column.addListeners(this);
 	if ( column instanceof TableColumnCore ){
  		((TableColumnCore)column).setDefaultSortAscending( true );
  	}
  }

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			TableColumn.CAT_PROTOCOL,
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
  	int index = tfi.getIndex();
  	cell.setSortValue(index);
  	cell.setText("" + index);
  }

}
