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

import com.biglybt.core.subs.util.SearchSubsResultBase;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.TimeFormatter;
import com.biglybt.ui.common.table.TableColumnCore;

import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumn;

public class ColumnSearchSubResultAge
	implements TableCellRefreshListener
{
	public static final String COLUMN_ID = "age";

	/**
	 *
	 * @param sTableID
	 */
	public ColumnSearchSubResultAge(TableColumn column) {
		column.initialize(TableColumn.ALIGN_CENTER, TableColumn.POSITION_LAST, 50 );
		column.addListeners(this);
		column.setRefreshInterval(TableColumn.INTERVAL_GRAPHIC);
		column.setType(TableColumn.TYPE_TEXT_ONLY);

		if ( column instanceof TableColumnCore){
			((TableColumnCore)column).setUseCoreDataSource( true );
		}
	}

	@Override
	public void refresh(TableCell cell) {
		SearchSubsResultBase rc = (SearchSubsResultBase) cell.getDataSource();
		if (rc == null) {
			return;
		}

		long time = rc.getTime();

		long age_secs = (SystemTime.getCurrentTime() - time)/1000;

		if ( cell.setSortValue( age_secs )){

			if ( time <= 0 ){
				cell.setText( "--" );
			}else{
				cell.setToolTip(time <= 0 ? "--" : TimeFormatter.format2(age_secs, false)
						+ "\n" + DisplayFormatters.formatCustomDateOnly(time));
				cell.setText( age_secs < 0?"--":TimeFormatter.format3( age_secs ));
			}
		}
	}
}
