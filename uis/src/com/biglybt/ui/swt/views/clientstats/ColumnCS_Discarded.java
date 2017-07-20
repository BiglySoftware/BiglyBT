/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.ui.swt.views.clientstats;

import java.util.Map;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumn;

import com.biglybt.util.MapUtils;

public class ColumnCS_Discarded
	implements TableCellRefreshListener
{

	public static final String COLUMN_ID = "discarded";

	public ColumnCS_Discarded(TableColumn column) {
		column.initialize(TableColumn.ALIGN_TRAIL, TableColumn.POSITION_LAST, 80);
		column.addListeners(this);
		column.setType(TableColumn.TYPE_TEXT_ONLY);
		String network = column.getUserDataString("network");
		if (network != null) {
			column.setVisible(false);
			column.setNameOverride(network + " "
					+ MessageText.getString("ClientStats.column." + COLUMN_ID));
		}
	}

	@Override
	public void refresh(TableCell cell) {
		ClientStatsDataSource ds = (ClientStatsDataSource) cell.getDataSource();
		if (ds == null) {
			return;
		}
		long val = ds.bytesDiscarded;
		TableColumn column = cell.getTableColumn();
		if (column != null) {
			String network = column.getUserDataString("network");
			if (network != null) {
				Map<String, Object> map = ds.perNetworkStats.get(network);
				if (map != null) {
					val = MapUtils.getMapLong(map, "bytesDiscarded", 0);
				} else {
					val = 0;
				}
			}
		}
		if (cell.setSortValue(val) || !cell.isValid()) {
			cell.setText(DisplayFormatters.formatByteCountToKiBEtc(val));
		}
	}
}
