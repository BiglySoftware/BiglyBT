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

import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.disk.DiskManagerPiece;
import com.biglybt.core.internat.MessageText;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

/**
 *
 * @author TuxPaper
 * @since 2.0.8.5
 */
public class PiecesDoneAndCountItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{
  /** Default Constructor */
  public PiecesDoneAndCountItem() {
    super("piecesdoneof", ALIGN_CENTER, POSITION_INVISIBLE, 60, TableManager.TABLE_TORRENT_FILES);
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

    int total 	= -1;
    int	done	= -1;
    
	if ( fileInfo != null ){

		total = fileInfo.getNbPieces();

		DiskManager			dm			= fileInfo==null?null:fileInfo.getDiskManager();

		if ( dm != null ){
	    
			done = 0;
		
			int start = fileInfo.getFirstPieceNumber();
			
			int end = start + total;
			
			DiskManagerPiece[] pieces = dm.getPieces();
			
			for( int i = start; i < end; i++ ){
				
				if( pieces[ i ].isDone()){
					
					done++;
				}
			}
		}
	}

    if( !cell.setSortValue( done ) && cell.isValid() ) {
      return;
    }

    cell.setText( done < 0 || total < 0 ?"": MessageText.getString( "v3.MainWindow.xofx", new String[]{ String.valueOf( done ), String.valueOf( total ) } ));
  }
}
