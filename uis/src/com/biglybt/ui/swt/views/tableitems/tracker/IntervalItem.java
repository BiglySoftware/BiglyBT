/*
 * File    : TypeItem.java
 * Created : 24 nov. 2003
 * By      : Olivier
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.ui.swt.views.tableitems.tracker;

import com.biglybt.core.util.TimeFormatter;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

import com.biglybt.core.tracker.TrackerPeerSource;


public class
IntervalItem
	extends CoreTableColumnSWT
    implements TableCellRefreshListener
{
	public
	IntervalItem(String tableID)
	{
		super( "interval", ALIGN_CENTER, POSITION_LAST, 75, tableID );

		setRefreshInterval( INTERVAL_GRAPHIC );
	}

	@Override
	public void
	fillTableColumnInfo(
		TableColumnInfo info )
	{
		info.addCategories( new String[]{
			CAT_ESSENTIAL,
		});
	}

	@Override
	public void
	refresh(
		TableCell cell )
	{
		TrackerPeerSource ps = (TrackerPeerSource)cell.getDataSource();

		long	interval 		= 0;
		long	min_interval 	= 0;

		if ( ps != null ){

			interval		= ps.getInterval();
			min_interval	= ps.getMinInterval();
		}

		long	sort = ( interval<<31 ) | (min_interval&0xffffffffL);

		if (!cell.setSortValue(sort) && cell.isValid()){

			return;
		}

		String	str;

		if ( interval <= 0 && min_interval <= 0 ){

			str = "";

		}else if ( interval <= 0 ){

			str = "(" + format(min_interval) + ")";

		}else if ( min_interval <= 0 ){

			str = format(interval);

		}else{

			str = format(interval) + " (" + format(min_interval) + ")";
		}

		cell.setText( str );
	}

	private String
	format(
		long	secs )
	{
		return( TimeFormatter.format2( secs, secs < 300 && secs%60 != 0));
	}
}
