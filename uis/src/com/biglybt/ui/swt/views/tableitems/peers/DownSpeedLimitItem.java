/*
 * Created on 17-Nov-2004
 * Created by Paul Gardner
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

package com.biglybt.ui.swt.views.tableitems.peers;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.DisplayFormatters;

import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;


/**
 * @author parg
 *
 */

public class DownSpeedLimitItem
extends CoreTableColumnSWT
implements TableCellRefreshListener
{
	public static final String COLUMN_ID = "maxdownspeed";

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_BYTES,
			CAT_SETTINGS,
		});
	}

	/** Default Constructor */
	public DownSpeedLimitItem(String table_id) {
	  super(COLUMN_ID, ALIGN_TRAIL, POSITION_INVISIBLE, 35, table_id);
	  setRefreshInterval(INTERVAL_LIVE);
	}

	@Override
	public void refresh(TableCell cell) {
	  PEPeer peer = (PEPeer)cell.getDataSource();
	  long value = (peer == null) ? 0 : peer.getStats().getDownloadRateLimitBytesPerSecond();
	  if (!cell.setSortValue(value) && cell.isValid())
	    return;

	  if(value == -1) {
	    cell.setText(MessageText.getString("MyTorrents.items.DownSpeedLimit.disabled"));
	  } else if(value  == 0) {
	    cell.setText(Constants.INFINITY_STRING);
	  } else {
	    cell.setText(DisplayFormatters.formatByteCountToKiBEtcPerSec(value));
	  }
	}
}
