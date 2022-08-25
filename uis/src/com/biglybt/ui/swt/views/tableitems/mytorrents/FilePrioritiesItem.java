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

import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerStats;

import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;


public class 
FilePrioritiesItem
	extends CoreTableColumnSWT
	implements TableCellRefreshListener
{
	public static final Class DATASOURCE_TYPE = Download.class;

	public static final String COLUMN_ID = "file.priorities";

	public FilePrioritiesItem(String sTableID) {
		super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_TRAIL, 70, sTableID);
		addDataSourceType(DiskManagerFileInfo.class);
		setRefreshInterval(INTERVAL_LIVE);
		setPosition(POSITION_INVISIBLE);
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] { CAT_SETTINGS });
		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE);
	}

	@Override
	public void 
	refresh(
		TableCell cell ) 
	{
		String text = "";
		long sortVal = 0;
		
		Object ds = cell.getDataSource();
		
		if (ds instanceof DownloadManager){
			
			DownloadManager dm = (DownloadManager)ds;
			
			DownloadManagerStats stats = dm.getStats();
			
			int[] vals = stats.getFilePriorityStats();

			CellComparator sorter = new CellComparator( vals );
			
			Comparable<?>  old = cell.getSortValue();
			
			if ( old instanceof CellComparator && sorter.compareTo((CellComparator)old ) == 0 && cell.isValid()){
				
				return;
			}
			
			cell.setSortValue( sorter );
						
			text = (vals[0]==Integer.MIN_VALUE?"-":vals[0])+ "/" + (vals[1]==Integer.MIN_VALUE?"-":vals[1]) + " (" + vals[2] + "/" + vals[3] + ")";
			
		}else if (ds instanceof DiskManagerFileInfo){
			
			DiskManagerFileInfo fileInfo = (DiskManagerFileInfo)ds;
			
			sortVal = fileInfo.getPriority();
			
			if (!cell.setSortValue(sortVal) && cell.isValid()){
				
				return;
			}
			
			text = String.valueOf(sortVal);
			
		}else{
			return;
		}
		
		cell.setText(text);
	}
	
	class
	CellComparator
		implements Comparable<CellComparator>
	{
		int[] vals;
		
		CellComparator(
			int[]		_vals )
		{
			vals = _vals;
		}
		
		@Override
		public int 
		compareTo(
			CellComparator o )
		{
			for ( int i=0;i<vals.length;i++){
			
				int res = Integer.compare( vals[i], o.vals[i] );
			
				if ( res != 0 ){
					
					return( res );
				}
			}
			
			return( 0 );
		}	
	}
}
