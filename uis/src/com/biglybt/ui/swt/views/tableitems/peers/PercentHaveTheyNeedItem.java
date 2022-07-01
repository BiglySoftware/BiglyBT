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

package com.biglybt.ui.swt.views.tableitems.peers;

import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.disk.DiskManagerPiece;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peermanager.piecepicker.util.BitFlags;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;


public class PercentHaveTheyNeedItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{
  /** Default Constructor */
  public PercentHaveTheyNeedItem(String table_id) {
    super("havetheyneed", ALIGN_TRAIL, POSITION_INVISIBLE, 55, table_id);
    setRefreshInterval(INTERVAL_LIVE);
  }

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_PROGRESS,
		});
		info.setProficiency( TableColumnInfo.PROFICIENCY_INTERMEDIATE);
	}

  @Override
  public void refresh(TableCell cell) {
    PEPeer peer = (PEPeer)cell.getDataSource();
    
    int value = -1;
    
    if ( peer != null && !peer.isSeed() && !peer.isMyPeer()){
    	
    	PEPeerManager pm = peer.getManager();
    	
    	if ( pm != null ){
    		    			
			BitFlags bf = peer.getAvailable();
			
			if ( bf != null ){
				
				boolean[] peer_has = bf.flags;
				
				if ( peer_has != null ){
			
						// we have to assume the peer wants everything as there is no way to
						// figure out if they have skipped some files
					
					int we_have				= 0;
					int we_have_they_want	= 0;
					
					DiskManagerPiece[] dm_pieces = pm.getDiskManager().getPieces();
					
					for ( int i=0; i<dm_pieces.length;i++){
						
						DiskManagerPiece piece = dm_pieces[i];
													
						if ( piece.isDone()){
								
							we_have++;
						
							if ( !peer_has[i] ){
									
								we_have_they_want++;
							}
						}
					}
					
					if ( we_have_they_want > 0 ){
						   					
						value = (1000*we_have_they_want)/we_have;
						
					}else{
						
						value = 0;
					}
				}
			}
    	}
    }
    
    if (!cell.setSortValue(value) && cell.isValid()){
    	
      return;
    }
    
    cell.setText(value<0?"":DisplayFormatters.formatPercentFromThousands(value));
  }
}
