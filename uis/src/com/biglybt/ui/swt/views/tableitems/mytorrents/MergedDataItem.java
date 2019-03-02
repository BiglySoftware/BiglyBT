/*
 * Created by Paul Gardner
 *
 * Copyright 2015 Azureus Software, Inc.  All rights reserved.
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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */

package com.biglybt.ui.swt.views.tableitems.mytorrents;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableCellToolTipListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

/**
 *
 * @author TuxPaper
 * @since 2.0.8.5
 */
public class MergedDataItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener, TableCellToolTipListener
{
	public static final Class DATASOURCE_TYPE = Download.class;

	public static final String COLUMN_ID = "mergeddata";

	public MergedDataItem(String sTableID)
	{
		super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_TRAIL, 70, sTableID);

		setRefreshInterval(INTERVAL_LIVE);

		setMinWidthAuto(true);
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
				CAT_BYTES
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE);
	}

	@Override
	public void refresh(TableCell cell) {
		DownloadManager dm = (DownloadManager)cell.getDataSource();

		long value = (dm == null) ? 0 : dm.getDownloadState().getLongAttribute( DownloadManagerState.AT_MERGED_DATA );

		if (!cell.setSortValue(value) && cell.isValid())
			return;

		cell.setText(value==0?"":DisplayFormatters.formatByteCountToKiBEtc( value ));
	}
	
	@Override
	public void 
	cellHover(TableCell cell)
	{
		DownloadManager dm = (DownloadManager)cell.getDataSource();
		
		String info;
		
		if ( dm == null ){
			
			info = "";
			
		}else{
			
			info = dm.isSwarmMerging();

			if ( info == null ){
				
				info = "";
			}
		}
		
		cell.setToolTip( info );
	}

	@Override
	public void
	cellHoverComplete(TableCell cell)
	{	
	}   
}
