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
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

import com.biglybt.core.peermanager.peerdb.PeerItem;


public class ColumnPeerNetwork
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{
  /** Default Constructor */
  public ColumnPeerNetwork(String table_id) {
    super("network", POSITION_INVISIBLE, 65, table_id);
    setRefreshInterval(INTERVAL_INVALID_ONLY);
  }

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_PROTOCOL,
			CAT_CONNECTION,
		});
	}

  @Override
  public void refresh(TableCell cell) {
  	Object ds = cell.getDataSource();
  	String text = "";
  	Comparable val = null;
  	if (ds instanceof PEPeerTransport) {
			PEPeerTransport peer = (PEPeerTransport) ds;

			PeerItem identity = peer.getPeerItemIdentity();

			if (identity != null) {
				val = text = identity.getNetwork();
			}
    }

    if( !cell.setSortValue( val ) && cell.isValid() ) {
      return;
    }

    cell.setText( text );
  }
}
