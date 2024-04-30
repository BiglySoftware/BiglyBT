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

package com.biglybt.ui.swt.views.tableitems.files;

import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

public class LastPieceItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{
  /** Default Constructor */
  public LastPieceItem() {
    super("lastpiece", ALIGN_TRAIL, POSITION_LAST, 75, TableManager.TABLE_TORRENT_FILES);
	  setDefaultSortAscending(true);
  }

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_PROTOCOL,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_ADVANCED);
	}

  @Override
  public void refresh(TableCell cell) {
    DiskManagerFileInfo fileInfo = (DiskManagerFileInfo)cell.getDataSource();
    long sort_value;

    if ( fileInfo == null ){
    	sort_value = 0;
    }else{
    	sort_value = fileInfo.getLastPieceNumber();

    	if ( cell.isSecondarySortEnabled()){
    		
	    	if ( sort_value >= 0 ){
	
	    		int index = fileInfo.getIndex();
	    		
	    		if ( index < 0 ){
	    			index = 0;
	    		}
	    		
	    		sort_value = (sort_value << 32) + index;
	    	}
    	}
    }


    if( !cell.setSortValue( sort_value ) && cell.isValid() ) {
      return;
    }

		// < 0 -> unknown skeleton value

    cell.setText( sort_value<0 || fileInfo == null?"":(""+fileInfo.getLastPieceNumber()));
  }
}
