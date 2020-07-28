/*
 * Created by Joseph Bridgewater
 * Created on Feb 05, 2006
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */

package com.biglybt.ui.swt.views.tableitems.pieces;

import com.biglybt.core.peer.PEPiece;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.views.PiecesView;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

/**
 * @author MjrTom
 * Shows if more requests can be made on the piece or not
 */
public class RequestedItem
    extends CoreTableColumnSWT
    implements TableCellRefreshListener
{
    /** Default Constructor */
    public RequestedItem(String table_id)
    {
        super("Requested", ALIGN_CENTER, POSITION_INVISIBLE, 20, table_id );
        setRefreshInterval(INTERVAL_LIVE);
    }

  	@Override
	  public void fillTableColumnInfo(TableColumnInfo info) {
  		info.addCategories(new String[] {
  			CAT_SWARM,
  		});
  	}

    @Override
    public void refresh(TableCell cell)
    {
        boolean value =false;
        final PEPiece pePiece =(PEPiece) cell.getDataSource();
        
    	boolean is_uploading = pePiece instanceof PiecesView.PEPieceUploading;

    	if ( is_uploading ){
    		cell.setText("");
    		return;
    	}
    	
        if (pePiece !=null)
        {
             value = pePiece.isRequested();
        }
        if (!cell.setSortValue(value ?1 :0) &&cell.isValid())
            return;
        cell.setText(value?"*" :"");
    }
}
