/*
 * Created on Jan 3, 2009
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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

package com.biglybt.ui.swt.views.columnsetup;

import com.biglybt.core.internat.MessageText;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

import com.biglybt.ui.common.table.TableColumnCore;

/**
 * @author TuxPaper
 * @created Jan 3, 2009
 *
 */
public class ColumnTC_ChosenColumn
	extends CoreTableColumnSWT
	implements TableCellRefreshListener
{
	public static final String COLUMN_ID = "TableColumnChosenColumn";

	/**
	 * @param name
	 * @param tableID
	 */
	public ColumnTC_ChosenColumn(String tableID) {
		super(COLUMN_ID, tableID);
		initialize(ALIGN_LEAD | ALIGN_TOP, POSITION_INVISIBLE, 175, INTERVAL_INVALID_ONLY);
	}

	// @see com.biglybt.pif.ui.tables.TableCellRefreshListener#refresh(com.biglybt.pif.ui.tables.TableCell)
	@Override
	public void refresh(TableCell cell) {
		TableColumnCore column = (TableColumnCore) cell.getDataSource();
		int colPos = column.getPosition();
		// colPos can have gaps in numbers
		if (!cell.setSortValue(colPos) && cell.isValid()) {
			return;
		}
		String key = column.getTitleLanguageKey();
		String s = MessageText.getString(key, column.getName());
		//s = column.getPosition() + "] " + s;
		cell.setText(s);
		String info = MessageText.getString(key + ".info", "");
		cell.setToolTip(info);
	}
}
