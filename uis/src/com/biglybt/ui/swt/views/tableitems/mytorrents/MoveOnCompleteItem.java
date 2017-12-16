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

package com.biglybt.ui.swt.views.tableitems.mytorrents;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;

public class MoveOnCompleteItem
extends CoreTableColumnSWT
implements TableCellRefreshListener
{
	public static final Class DATASOURCE_TYPE = Download.class;

	public static final String COLUMN_ID = "moveoncomplete";

	public MoveOnCompleteItem(String sTableID)
	{
		super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_LEAD, 120, sTableID);

		setRefreshInterval(INTERVAL_LIVE);

		setMinWidthAuto(true);
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
				CAT_CONTENT
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE);
	}

	@Override
	public void refresh(TableCell cell) {
		DownloadManager dm = (DownloadManager)cell.getDataSource();

		String target = (dm == null) ? null : dm.getDownloadState().getAttribute( DownloadManagerState.AT_MOVE_ON_COMPLETE_DIR );

		if ( target == null ){
			
			target = "";
		}
		
		if ( !cell.setSortValue(target) && cell.isValid()){
			return;
		}
		
		cell.setText( target );
	}
}
