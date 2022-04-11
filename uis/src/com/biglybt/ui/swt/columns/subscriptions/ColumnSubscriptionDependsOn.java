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


import java.util.List;

import com.biglybt.core.subs.Subscription;

import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;


public class ColumnSubscriptionDependsOn
	extends CoreTableColumnSWT
	implements TableCellRefreshListener
{
	public static String COLUMN_ID = "depends-on";


	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_ESSENTIAL,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE);
	}

	/** Default Constructor */
	public ColumnSubscriptionDependsOn(String sTableID) {
		super(COLUMN_ID, POSITION_INVISIBLE, 150, sTableID);
		setRefreshInterval(INTERVAL_LIVE);
		setAlignment(ALIGN_LEAD);
	}

	@Override
	public void refresh(TableCell cell) {
		String str = "";
		Subscription sub = (Subscription) cell.getDataSource();
		if (sub != null) {
			List<Subscription> deps = sub.getDependsOn();
			
			for ( Subscription dep: deps ){
				str += (str.isEmpty()?"":", ") + dep.getName();
			}
		}

		if (!cell.setSortValue(str) && cell.isValid()) {
			return;
		}

		if (!cell.isShown()) {
			return;
		}

		cell.setText( str );
	}
}
