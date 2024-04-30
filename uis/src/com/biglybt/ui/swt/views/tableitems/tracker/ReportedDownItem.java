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

package com.biglybt.ui.swt.views.tableitems.tracker;

import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

import com.biglybt.core.tracker.TrackerPeerSource;
import com.biglybt.core.util.DisplayFormatters;


public class
ReportedDownItem
	extends CoreTableColumnSWT
    implements TableCellRefreshListener
{
	public
	ReportedDownItem(String tableID)
	{
		super( "reported_down", ALIGN_TRAIL, POSITION_INVISIBLE, 75, tableID );

		setRefreshInterval( INTERVAL_GRAPHIC );
	}

	@Override
	public void
	fillTableColumnInfo(
		TableColumnInfo info )
	{
		info.addCategories( new String[]{
			CAT_CONTENT,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	@Override
	public void
	refresh(
		TableCell cell )
	{
		TrackerPeerSource ps = (TrackerPeerSource)cell.getDataSource();

		long[] stats = (ps==null)?null:ps.getReportedStats();

		long 	sort;
		String 	str;
		
		if ( stats != null ){
			long gu = stats[1];
			long uu = stats[3];
			long su = stats[5];
			
			sort = gu!=0?gu:(uu!=0?uu:su);
			
			if ( sort == 0 ){
				str = "";
			}else{
				if ( uu>0 ){
					str = DisplayFormatters.formatByteCountToKiBEtc( uu );
				}else{
					str = "";
				}
				if ( gu != 0 && gu != uu ){
					str = DisplayFormatters.formatByteCountToKiBEtc( gu ) + (str.isEmpty()?"":("/" + str ));
				}
				if ( su > 0 ){
					str += " (" +  DisplayFormatters.formatByteCountToKiBEtc( su ) + ")";
				}
			}
		}else{
			sort 	= -1;
			str		= "";
		}
				
		if (!cell.setSortValue(sort) && cell.isValid()){

			return;
		}

		cell.setText( str );	
	}
}
