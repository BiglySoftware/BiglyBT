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

package com.biglybt.ui.swt.columns.subscriptions;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.subs.Subscription;
import com.biglybt.core.subs.SubscriptionResult;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.util.SystemTime;
import com.biglybt.ui.swt.views.table.utils.TableColumnCreator;
import com.biglybt.ui.swt.views.tableitems.ColumnDateSizer;

import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableColumnInfo;

public class ColumnSubscriptionNewestDate
	extends ColumnDateSizer
{
	public static String COLUMN_ID = "newest-date";

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_TIME,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE);
	}

	/** Default Constructor */
	public ColumnSubscriptionNewestDate(String sTableID) {
		super(Subscription.class, COLUMN_ID, TableColumnCreator.DATE_COLUMN_WIDTH,
				sTableID);
		setPosition(POSITION_INVISIBLE);
		setRefreshInterval(INTERVAL_LIVE);
		setShowTime(true);
		setMultiline(false);
	}

	@Override
	public void refresh(TableCell cell, long timestamp) {
		Subscription sub = (Subscription) cell.getDataSource();
		if (sub == null) {
			return;
		}
		
		// SortVal will be: ((latest & 0x7FFFFFFFL) << 32) + (scanTime & 0xFFFFFFFFL) 

		long scanTime = (sub.getHistory().getLastScanTime() / 1000) & 0xFFFFFFFFL;

		if ( !cell.isSecondarySortEnabled()){
			
			scanTime = 0;	// remove from consideration
		}
		
		if (cell.isValid()) {
			Comparable lastSortVal = cell.getSortValue();
			if (lastSortVal instanceof Long) {
				long lastScanTime = ((Long) lastSortVal) & 0xFFFFFFFFL;
				if (lastScanTime == scanTime) {
					return;
				}
			}
		}

		long latest = 0;
		SubscriptionResult[] results = sub.getResults(true);
		for (SubscriptionResult result : results) {
			if (result.isDeleted() || result.getRead()) {
				continue;
			}
			long timeFound = result.getTimeFound();
			if (timeFound > latest) {
				latest = timeFound;
			}
		}
		
		long sortVal = (((latest / 1000) & 0x7FFFFFFFL) << 32) + scanTime;

		super.refresh(cell, latest, sortVal, null);
	}

	@Override
	public void cellHover(TableCell cell) {
		Object ds = cell.getSortValue();
		if (ds instanceof Number) {
			long timestamp_secs = ((Number) ds).longValue() >> 32;

			if ( timestamp_secs > 0 ){
				long eta = (SystemTime.getCurrentTime() / 1000) - timestamp_secs;
				if (eta > 0) {
					cell.setToolTip(DisplayFormatters.formatETA(eta, false) + " "
						+ MessageText.getString("label.ago"));
				}
			}
		}
	}
}
