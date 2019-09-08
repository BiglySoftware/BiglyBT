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

import com.biglybt.core.internat.MessageText;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

import com.biglybt.core.subs.Subscription;
import com.biglybt.core.subs.SubscriptionManagerFactory;

public class ColumnSubscriptionMaxResults
	extends CoreTableColumnSWT
	implements TableCellRefreshListener
{
	public static String COLUMN_ID = "max-results";

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_SETTINGS,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE);
	}

	/** Default Constructor */
	public ColumnSubscriptionMaxResults(String sTableID) {
		super(COLUMN_ID, POSITION_INVISIBLE, 100, sTableID);
		setRefreshInterval(INTERVAL_LIVE);
		setAlignment(ALIGN_TRAIL);
	}

	@Override
	public void refresh(TableCell cell) {
		int maxResults = 0;
		Subscription sub = (Subscription) cell.getDataSource();
		if (sub != null) {
			maxResults = sub.getHistory().getMaxNonDeletedResults();
		}

		if ( maxResults < 0 ){

			maxResults = SubscriptionManagerFactory.getSingleton().getMaxNonDeletedResults();
		}

		boolean is_st = sub.isSearchTemplate();

		if (!cell.setSortValue(is_st?-1:maxResults) && cell.isValid()) {
			return;
		}

		if (!cell.isShown()) {
			return;
		}

		if ( is_st ){

			cell.setText( "" );

		}else{
			if ( maxResults == 0 ){

				cell.setText(MessageText.getString( "ConfigView.unlimited" ));

			}else{

				cell.setText("" + maxResults);
			}
		}
		return;

	}
}
