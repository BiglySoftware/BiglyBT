/*
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

package com.biglybt.ui.swt.views.tableitems.mytracker;


import com.biglybt.core.internat.MessageText;
import com.biglybt.core.tracker.host.*;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

/**
 *
 * @author TuxPaper
 * @since 2.0.8.5
 */
public class ExternalItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{
  /** Default Constructor */
  public ExternalItem() {
    super("external", ALIGN_TRAIL, POSITION_INVISIBLE, 60, TableManager.TABLE_MYTRACKER);
    setRefreshInterval(INTERVAL_LIVE);
  }

  @Override
  public void refresh(TableCell cell) {

    TRHostTorrent item = (TRHostTorrent)cell.getDataSource();

    String status_text = "";

    if( item != null ) {

    	boolean external = item.isExternal();
    	
     if( !cell.setSortValue( external?1:0 ) && cell.isValid() ) {
        return;
     }

     if ( external ){
	     status_text = MessageText.getString( "Button.yes" ).replaceAll("&", "");
     }else{
		 status_text = MessageText.getString( "Button.no" ).replaceAll("&", "");
     }

    }

    cell.setText( status_text );
  }

}
