/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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
 */

package com.biglybt.ui.swt.columns.searchsubs;

import com.biglybt.ui.common.table.TableColumnCore;
import com.biglybt.core.subs.util.SearchSubsResultBase;
import com.biglybt.pif.ui.tables.*;

public class ColumnSearchSubResultRank
	implements TableCellRefreshListener
{
	public static final String COLUMN_ID = "rank";

	/**
	 *
	 * @param sTableID
	 */
	public ColumnSearchSubResultRank(TableColumn column) {
		column.initialize(TableColumn.ALIGN_CENTER, TableColumn.POSITION_LAST, 60 );
		column.addListeners(this);
		column.setRefreshInterval(TableColumn.INTERVAL_INVALID_ONLY);
		column.setType(TableColumn.TYPE_TEXT_ONLY);

		if ( column instanceof TableColumnCore ){
			((TableColumnCore)column).setUseCoreDataSource( true );
		}
	}

	@Override
	public void refresh(TableCell cell) {
		SearchSubsResultBase rc = (SearchSubsResultBase) cell.getDataSource();
		if (rc == null) {
			return;
		}

		long rank = rc.getRank();

		if ( rank >= 0 && cell.setSortValue( rank )){

			cell.setText( String.valueOf(rank ));
		}
	}
}
