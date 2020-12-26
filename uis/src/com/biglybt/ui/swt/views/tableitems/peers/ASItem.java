/*
 * File    : ASItem.java
 * Created : 24 dec 2008
 * By      : Parg
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

package com.biglybt.ui.swt.views.tableitems.peers;

import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.util.PeerUtils;

import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

public class ASItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{
  public static final String COLUMN_ID = "as";

  public ASItem(String table_id) {
    super(COLUMN_ID, ALIGN_LEAD, POSITION_INVISIBLE, 100, table_id);
    setRefreshInterval(INTERVAL_LIVE);
  }

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_PEER_IDENTIFICATION,
		});
	}

  @Override
  public void refresh(TableCell cell) {
    final PEPeer peer = (PEPeer)cell.getDataSource();

    String	text = "";

    if ( peer != null ){

    	text = PeerUtils.getASN( peer );
    	
    	if ( text == null ){
    		
    		text = "";
    	}
    }

    if ( !cell.setSortValue(text) && cell.isValid()){

      return;
    }

    cell.setText( text );
  }
}
