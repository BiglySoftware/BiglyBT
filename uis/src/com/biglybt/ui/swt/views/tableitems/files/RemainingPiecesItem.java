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

import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.disk.DiskManagerPiece;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.views.FilesView;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

/**
 *
 * @author TuxPaper
 * @since 2.0.8.5
 */
public class RemainingPiecesItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{
  /** Default Constructor */
  public RemainingPiecesItem() {
    super("remaining", ALIGN_TRAIL, POSITION_LAST, 60, TableManager.TABLE_TORRENT_FILES);
    setRefreshInterval(INTERVAL_LIVE);
  }

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_PROGRESS,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE);
	}

  @Override
  public void refresh(TableCell cell) {
    DiskManagerFileInfo fileInfo 	= (DiskManagerFileInfo)cell.getDataSource();

    int remaining = 0;

    if( fileInfo != null ){
    	
    	if ( fileInfo instanceof FilesView.FilesViewTreeNode && !((FilesView.FilesViewTreeNode)fileInfo).isLeaf()){
	
    		remaining	= -1;
    		
    	}else{
    		
    		DiskManagerPiece[] pieces = null;
    		
    	    DiskManager	dm	= fileInfo.getDiskManager();

    	    if ( dm != null ){
    	    	
    	    	pieces = dm.getPieces();
    	    	
    	    }else{
    	    	
    	    	DownloadManager manager = fileInfo.getDownloadManager();
    	    
    	    	if ( manager != null ){
    	    		
    	    		pieces = manager.getDiskManagerPiecesSnapshot();
    	    	}
    	    }
    	    
    	    if ( pieces == null ){
    	    	
    	    	remaining = -1;
    	    	
    	    }else{
	    		int start = fileInfo.getFirstPieceNumber();
	    		
	    		int end = start + fileInfo.getNbPieces();
	    		
	    		for ( int i = start; i < end; i++ ){
	    			
	    			if ( !pieces[ i ].isDone() ){
	    				
	    				remaining++;
	    			}
	    		}
    	    }
    	}
    }else{

		remaining	= -1;	// unknown
    }

    if( !cell.setSortValue( remaining ) && cell.isValid() ) {
      return;
    }

    cell.setText( "" + ( remaining<0?"":(""+remaining)));
  }
}
