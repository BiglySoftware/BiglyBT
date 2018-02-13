/*
 * Created on 14 Sep 2007
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
import com.biglybt.core.peer.PEPeerManager;

import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.debug.ObfuscateCellText;
import com.biglybt.ui.swt.debug.UIDebugGenerator;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;


/**
 * @author Allan Crooks
 *
 */
public class DownloadNameItem extends CoreTableColumnSWT implements TableCellRefreshListener, ObfuscateCellText{
	public static final String COLUMN_ID = "name";

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_CONTENT,
		});
	}

	/** Default Constructor */
	public DownloadNameItem(String table_id) {
		super(COLUMN_ID, 250, table_id);
		this.setPosition(0);
		//setObfuscation(true);
		setRefreshInterval(INTERVAL_LIVE);
		setType(TableColumn.TYPE_TEXT);
		setMinWidth(100);
		setObfuscation(true);
	}


	/* (non-Javadoc)
	 * @see com.biglybt.pif.ui.tables.TableCellRefreshListener#refresh(com.biglybt.pif.ui.tables.TableCell)
	 */
	@Override
	public void refresh(TableCell cell) {
		PEPeer peer = (PEPeer)cell.getDataSource();
		if (peer == null) {cell.setText(""); return;}
		PEPeerManager manager = peer.getManager();
		if (manager == null) {cell.setText(""); return;}
		cell.setText(manager.getDisplayName());
	}

	@Override
	public String getObfuscatedText(TableCell cell) {
		return( UIDebugGenerator.obfuscateDownloadName((PEPeer)cell.getDataSource()));
	}
}
