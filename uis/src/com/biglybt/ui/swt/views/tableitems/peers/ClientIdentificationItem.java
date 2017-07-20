/*
 * Created on 5 Sep 2007
 * Created by Allan Crooks
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
 */
package com.biglybt.ui.swt.views.tableitems.peers;

import com.biglybt.core.peer.PEPeer;

import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;


/**
 * @author Allan Crooks
 *
 */
public class ClientIdentificationItem extends CoreTableColumnSWT implements TableCellRefreshListener {

	public static final String COLUMN_ID = "client_identification";

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_PEER_IDENTIFICATION,
		});
	}

	public ClientIdentificationItem(String table_id) {
		super(COLUMN_ID, POSITION_INVISIBLE, 200, table_id);
		setRefreshInterval(INTERVAL_LIVE);
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.tables.TableCellRefreshListener#refresh(com.biglybt.pif.ui.tables.TableCell)
	 */
	@Override
	public void refresh(TableCell cell) {
	    PEPeer peer = (PEPeer)cell.getDataSource();
	    if (peer == null) {cell.setText(""); return;}
	    String peer_id_name = peer.getClientNameFromPeerID();
	    String peer_handshake_name = peer.getClientNameFromExtensionHandshake();

	    if (peer_id_name == null) {peer_id_name = "";}
	    if (peer_handshake_name == null) {peer_handshake_name = "";}

	    if (peer_id_name.equals("") && peer_handshake_name.equals("")) {
	    	cell.setText(""); return;
	    }

	    String result = peer_id_name;
	    if (!peer_handshake_name.equals("")) {result += " / " + peer_handshake_name;}
	    cell.setText(result);
	}

}
