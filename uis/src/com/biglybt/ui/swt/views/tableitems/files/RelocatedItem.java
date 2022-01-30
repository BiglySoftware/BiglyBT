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

package com.biglybt.ui.swt.views.tableitems.files;

import java.io.File;

import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;


public class RelocatedItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{
	public RelocatedItem() {
		super("relocated", ALIGN_CENTER, POSITION_INVISIBLE, 70, TableManager.TABLE_TORRENT_FILES);
		setRefreshInterval(INTERVAL_LIVE);
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_CONTENT
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE);
	}

  @Override
  public void refresh(TableCell cell) {
    DiskManagerFileInfo fileInfo = (DiskManagerFileInfo)cell.getDataSource();

    boolean	relocated;

    if ( fileInfo == null ){

    	relocated = false;

    }else{

    	int index = fileInfo.getIndex();
    	
    	if ( index < 0 ){
    		
    		relocated = false;
    		
    	}else{
    		
        	File source = fileInfo.getFile( false );

	    	File target = fileInfo.getDownloadManager().getDownloadState().getFileLink( index, source );
	
	    	if ( target == null ){
	
	    		relocated = false;
	
	    	}else{
	
		    	if ( target == source ){
	
		    		relocated = false;
	
		    	}else{
	
		    		relocated = !target.equals( source );
		    	}
	    	}
    	}
    }

    if ( !cell.setSortValue( relocated?1:0 ) && cell.isValid()){

    	return;
    }

    String text = relocated?"*":"";

    cell.setText( text );
  }
}
