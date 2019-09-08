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
 *
 */

package com.biglybt.ui.swt.columns.subscriptions;

import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

import com.biglybt.core.subs.Subscription;

/**
 * @author Olivier Chalouhi
 * @created Oct 7, 2008
 *
 */
public class ColumnSubscriptionCategory
	extends CoreTableColumnSWT
	implements TableCellRefreshListener
{
	public static String COLUMN_ID = "category";

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_ESSENTIAL,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE);
	}

	/** Default Constructor */
	public ColumnSubscriptionCategory(String sTableID) {
		super(COLUMN_ID, POSITION_INVISIBLE, 100, sTableID);
		setRefreshInterval(INTERVAL_LIVE);
	}

	@Override
	public void refresh(TableCell cell) {

		Subscription sub = (Subscription) cell.getDataSource();
		String category = null;
		if (sub != null) {
			category = sub.getCategory();
		}

		if ( category == null ){
			category = "";
		}

		if (!cell.setSortValue(category) && cell.isValid()) {
			return;
		}

		if (!cell.isShown()) {
			return;
		}

		cell.setText(category);
		return;

	}
}
