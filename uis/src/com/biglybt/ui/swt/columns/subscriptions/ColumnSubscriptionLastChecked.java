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
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.ui.swt.views.table.utils.TableColumnCreator;
import com.biglybt.ui.swt.views.tableitems.ColumnDateSizer;

import com.biglybt.core.subs.Subscription;

/**
 * @author Olivier Chalouhi
 * @created Oct 7, 2008
 *
 */
public class
ColumnSubscriptionLastChecked
	extends ColumnDateSizer
{
	public static String COLUMN_ID = "last-checked";

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
                CAT_ESSENTIAL,
                CAT_TIME
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE);
	}

	public
	ColumnSubscriptionLastChecked(
		String sTableID )
	{
		super(Subscription.class, COLUMN_ID, TableColumnCreator.DATE_COLUMN_WIDTH,
				sTableID);
		setRefreshInterval(INTERVAL_LIVE);
		setPosition( POSITION_INVISIBLE );
		setMinWidth(100);

		setMultiline( false );

		setShowTime( true );
	}

	@Override
	public void
	refresh(
		TableCell 	cell,
		long 		timestamp )
	{
		timestamp = 0;

		Subscription sub = (Subscription) cell.getDataSource();

		if ( sub != null ){

			timestamp = sub.getHistory().getLastScanTime();
		}

		if (!cell.setSortValue(timestamp) && cell.isValid()) {
			return;
		}

		if (!cell.isShown()) {
			return;
		}

		if ( sub.isSearchTemplate()){

			cell.setText( "" );

		}else  if ( timestamp <= 0 ){

			cell.setText( "--" );

		}else{

			super.refresh( cell, timestamp );
		}
	}
}
