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

import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

import com.biglybt.core.subs.Subscription;
import com.biglybt.core.subs.SubscriptionHistory;

/**
 * @author Olivier Chalouhi
 * @created Mar 15, 2009
 *
 */
public class ColumnSubscriptionAutoDownload
	extends CoreTableColumnSWT
	implements TableCellRefreshListener
{
	public static String COLUMN_ID = "auto-download";

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_ESSENTIAL,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	/** Default Constructor */
	public ColumnSubscriptionAutoDownload(String sTableID) {
		super(COLUMN_ID, ALIGN_CENTER, POSITION_LAST, 100, sTableID);
		setRefreshInterval(INTERVAL_LIVE);
	}

	@Override
	public void refresh(TableCell cell) {
		boolean autoDownload = false;
		Subscription sub = (Subscription) cell.getDataSource();
		if (sub != null) {
			SubscriptionHistory history = sub.getHistory();
			if(history != null) {
				autoDownload = history.isAutoDownload();
			}
		}

		if (!cell.setSortValue(autoDownload?1:0) && cell.isValid()) {
			return;
		}

		if (!cell.isShown()) {
			return;
		}

		if ( sub.isAutoDownloadSupported()){
			cell.setText( DisplayFormatters.getYesNo( autoDownload ));
		}else{
			if (!cell.setSortValue(-1) && cell.isValid()) {
				return;
			}
			cell.setText( "" );
		}
		return;

	}
}
