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

package com.biglybt.ui.swt.views.tableitems.mytorrents;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.DisplayFormatters;

import com.biglybt.pif.download.DownloadTypeIncomplete;
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
	public static final Class DATASOURCE_TYPE = DownloadTypeIncomplete.class;

	public static final String COLUMN_ID = "maxdownspeed";

	/** Default Constructor */
	public DownSpeedLimitItem(String sTableID) {
	  super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_TRAIL, 35, sTableID);
	  setRefreshInterval(INTERVAL_LIVE);
    setMinWidthAuto(true);
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_SETTINGS,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_ADVANCED);
	}

	@Override
	public void refresh(TableCell cell) {
	  DownloadManager dm = (DownloadManager)cell.getDataSource();
	  long value = (dm == null) ? 0 : dm.getStats().getDownloadRateLimitBytesPerSecond();
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
