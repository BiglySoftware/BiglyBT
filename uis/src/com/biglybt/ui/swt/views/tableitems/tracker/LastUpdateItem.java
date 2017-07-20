/*
 * Created on 05-May-2006
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

package com.biglybt.ui.swt.views.tableitems.tracker;

import com.biglybt.ui.swt.views.table.utils.TableColumnCreator;
import com.biglybt.ui.swt.views.tableitems.ColumnDateSizer;

import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableColumnInfo;

import com.biglybt.core.tracker.TrackerPeerSource;

public class LastUpdateItem
	extends ColumnDateSizer
{
	public static final Class DATASOURCE_TYPE = TrackerPeerSource.class;

	public static final String COLUMN_ID = "last_update";

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] { CAT_TIME, CAT_TRACKER });
		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE);
	}

	public
	LastUpdateItem(
		String sTableID)
	{
		super( DATASOURCE_TYPE, COLUMN_ID, TableColumnCreator.DATE_COLUMN_WIDTH, sTableID);

		setMultiline(false);

		setRefreshInterval(INTERVAL_GRAPHIC );

		setShowTime( true );
	}


	@Override
	public void refresh(TableCell cell, long timestamp) {
		TrackerPeerSource ps = (TrackerPeerSource)cell.getDataSource();

		timestamp = (ps == null) ? 0 : ps.getLastUpdate();

		super.refresh(cell, timestamp*1000);
	}
}
