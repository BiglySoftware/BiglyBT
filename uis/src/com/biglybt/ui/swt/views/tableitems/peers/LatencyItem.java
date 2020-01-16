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

package com.biglybt.ui.swt.views.tableitems.peers;

import com.biglybt.core.peer.impl.PEPeerTransport;
import com.biglybt.core.util.TimeFormatter;

import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;



public class LatencyItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{
	public static final String COLUMN_ID = "latency";

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_TIME,
		});
	}

	/** Default Constructor */
  public LatencyItem(String table_id) {
    super(COLUMN_ID, ALIGN_TRAIL, POSITION_INVISIBLE, 70, table_id);
    setRefreshInterval(INTERVAL_LIVE);
  }

  @Override
  public void refresh(TableCell cell) {
	Object ds = cell.getDataSource();
	
	long value;
	
	if ( ds instanceof PEPeerTransport ){
		
		PEPeerTransport peer = (PEPeerTransport)ds;

		value = peer.getLatency();
		
	}else{
		
		value = 0;
	}
	
    if( !cell.setSortValue( value ) && cell.isValid() ) {
      return;
    }

    cell.setText( value==0?"":TimeFormatter.format100ths( value  ));
  }
}
