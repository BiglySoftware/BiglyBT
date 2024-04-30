/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.ui.swt.shells.opentorrent;

import com.biglybt.pif.ui.tables.*;


public class TableColumnOTOT_SaveLocation
implements TableCellRefreshListener, TableColumnExtraInfoListener
{
	public static final String COLUMN_ID = "path";

  /** Default Constructor */
  public TableColumnOTOT_SaveLocation(TableColumn column) {
  	column.initialize(TableColumn.ALIGN_LEAD, TableColumn.POSITION_LAST, 260, TableColumn.INTERVAL_LIVE);
  	column.addListeners(this);

  }

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			TableColumn.CAT_ESSENTIAL,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

  @Override
  public void refresh(TableCell cell) {
  	Object ds = cell.getDataSource();
  	if (!(ds instanceof OpenTorrentOptionsWindow.OpenTorrentInstance)) {
  		return;
  	}
  	OpenTorrentOptionsWindow.OpenTorrentInstance instance = (OpenTorrentOptionsWindow.OpenTorrentInstance) ds;

  	cell.setText( instance.getOptions().getDataDir());
  }

}
