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

import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.pif.ui.tables.*;


public class TableColumnOTOT_Size
implements TableCellRefreshListener, TableColumnExtraInfoListener
{
	public static final String COLUMN_ID = "size";

  /** Default Constructor */
  public TableColumnOTOT_Size(TableColumn column) {
  	column.initialize(TableColumn.ALIGN_LEAD, TableColumn.POSITION_LAST, 80, TableColumn.INTERVAL_LIVE );
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

  	long total_size 	= instance.getOptions().getTotalSize();
  	long selected_size 	= instance.getSelectedDataSize();

  	if ( cell.setSortValue( selected_size )){

  		String total_str 	= DisplayFormatters.formatByteCountToKiBEtc( total_size );
  		String sel_str 		= total_size==selected_size?total_str:DisplayFormatters.formatByteCountToKiBEtc( selected_size );

  		if ( total_str.equals( sel_str )){

  			cell.setText( total_str );

  		}else{

  			cell.setText( sel_str + " / " + total_str );
  		}
  	}
  }
}
