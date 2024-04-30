/* 
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.ui.swt.views.tableitems.pieces;

import com.biglybt.core.disk.DiskManagerPiece;
import com.biglybt.core.disk.impl.piecemapper.DMPieceList;
import com.biglybt.core.disk.impl.piecemapper.DMPieceMapEntry;
import com.biglybt.core.peer.PEPiece;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;


public class FilesItem
    extends CoreTableColumnSWT
    implements TableCellRefreshListener
{
    /** Default Constructor */
    public FilesItem(String table_id)
    {
        super("Files", ALIGN_LEAD, POSITION_INVISIBLE, 200, table_id);
        setRefreshInterval(INTERVAL_LIVE);
    }

  	@Override
	  public void fillTableColumnInfo(TableColumnInfo info) {
  		info.addCategories(new String[] {
  			CAT_CONTENT,
  		});
  	}

    @Override
    public void refresh(TableCell cell)
    {
        PEPiece pePiece =(PEPiece) cell.getDataSource();
        
        String value = "";
        
        if ( pePiece != null ){
        	
             DiskManagerPiece dmp = pePiece.getDMPiece();
             
             if ( dmp != null ){
            	 
            	 DMPieceList l = dmp.getManager().getPieceList(pePiece.getPieceNumber());
            	 
            	 for ( int i=0;i<l.size();i++) {
            		 
            		 DMPieceMapEntry entry = l.get( i );
            		 
            		 String name = entry.getFile().getTorrentFile().getRelativePath();
            		 
            		 value += ( value.isEmpty()?"":"; " ) + name;
            	 }
             }
        }
        if (!cell.setSortValue( value ) &&cell.isValid())
            return;
        cell.setText( value );
    }
}
