/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.ui.swt.views.tableitems.mytorrents;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;


public class
UploadPriorityItem
	extends CoreTableColumnSWT
    implements TableCellRefreshListener
{
	public static final Class DATASOURCE_TYPE = Download.class;

	public static final String COLUMN_ID = "upload.priority";

	public
	UploadPriorityItem(
		String sTableID)
	{
		super( DATASOURCE_TYPE, COLUMN_ID, ALIGN_CENTER, 70, sTableID );
		setRefreshInterval(INTERVAL_LIVE);
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_CONNECTION
		});
	}


	@Override
	public void
	refresh(
		TableCell cell)
	{
		DownloadManager dm = (DownloadManager)cell.getDataSource();
	  	
	  	long	sort_val;
	  	String	str;
	  	
	  	if ( dm == null ){
	  	
	  		sort_val 	= -1;
	  		str			= "";
	  	}else{

	  		int manual = dm.getDownloadState().getIntParameter( DownloadManagerState.PARAM_UPLOAD_PRIORITY );
	  		
	  		int effective = dm.getEffectiveUploadPriority();
	  		
	  		sort_val = effective;
	  		
	  		str = effective + "/" + manual;
	  	}

	    if ( !cell.setSortValue(sort_val) && cell.isValid()){

	    	return;
	    }

	    cell.setText( str );
	}
}
