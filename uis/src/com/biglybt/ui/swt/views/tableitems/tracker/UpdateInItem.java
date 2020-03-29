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
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

import com.biglybt.core.tracker.TrackerPeerSource;

public class
UpdateInItem
	extends CoreTableColumnSWT
    implements TableCellRefreshListener
{
	public
	UpdateInItem(String tableID)
	{
		super( "updatein", ALIGN_CENTER, POSITION_LAST, 75, tableID );

		setRefreshInterval( INTERVAL_LIVE );
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

		boolean show_blank = false;
		
		int secs;

		if ( ps == null ){

			secs = -1;

		}else{

			int	state = ps.getStatus();

			if ( state != TrackerPeerSource.ST_DISABLED &&  !ps.isUpdating()){

				secs = ps.getSecondsToUpdate();

			}else{

				secs = -1;
				
				show_blank = true;
			}
		}

		if (!cell.setSortValue(secs) && cell.isValid()){

			return;
		}

		if ( show_blank ){
			
			cell.setForeground( null );
			
			cell.setText( "" );
			
		}else{
			
			if ( secs >= -1 ){
	
				cell.setForeground( null );
				
				cell.setText( TimeFormatter.formatColon( secs<0?0:secs ));	// negative value render as blank, leave as 0
				
			}else{
				
	
					// things are lagging, timer should have run already
				
				cell.setForeground( secs<-5?Utils.colorToIntArray(Colors.colorWarning):null);
				
				cell.setText( "-" + TimeFormatter.formatColon( -secs ));
			}
		}
	}
}
