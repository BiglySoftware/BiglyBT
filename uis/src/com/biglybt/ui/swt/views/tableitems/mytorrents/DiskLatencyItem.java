/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.ui.swt.views.tableitems.mytorrents;

import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.download.DownloadManager;

import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;


public class
DiskLatencyItem
	extends CoreTableColumnSWT
    implements TableCellRefreshListener
{
	public static final Class DATASOURCE_TYPE = Download.class;

	public static final String COLUMN_ID = "disk.latency";

	public
	DiskLatencyItem(
		String sTableID)
	{
		super( DATASOURCE_TYPE, COLUMN_ID, ALIGN_CENTER, 70, sTableID );
		
		setRefreshInterval(INTERVAL_LIVE);
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_TIME
		});
	}


	@Override
	public void
	refresh(
		TableCell cell)
	{
		DownloadManager dm = (DownloadManager)cell.getDataSource();

		DiskManager disk_man = dm==null?null:dm.getDiskManager();
		
	    long[] value = disk_man == null?null:disk_man.getLatency();

	    long 	sort;
	    String	str;
	    
	    if ( value == null ){
	    	sort 	= -1;
	    	str		= "";
	    }else{
	    	sort = value[0]<<32 | value[1];
	    	str	= value[0] + "/" + value[1];
	    }
	    
	    if ( !cell.setSortValue(sort) && cell.isValid()){

	    	return;
	    }

	    cell.setText( str );
	}
}
