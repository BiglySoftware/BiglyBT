/*
 * File    : SavePathItem.java
 * Created : 01 febv. 2004
 * By      : TuxPaper
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.ui.swt.views.tableitems.mytorrents;

import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.util.StringInterner;

import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;


public class FilesDoneItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{
	public static final Class DATASOURCE_TYPE = Download.class;

  public static final String COLUMN_ID = "filesdone";

	public FilesDoneItem(String sTableID) {
	  super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_CENTER, 50, sTableID);
	  setRefreshInterval(5);
    setMinWidthAuto(true);
  }

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_CONTENT,
			CAT_PROGRESS
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE);
	}

  @Override
  public void refresh(TableCell cell) {
    DownloadManager dm = (DownloadManager)cell.getDataSource();

    String	text = "";

    if ( dm != null ){
    	int	complete 			= 0;
    	int	skipped				= 0;
    	int	skipped_complete	= 0;

    	DiskManagerFileInfo[]	files = dm.getDiskManagerFileInfo();

    	int	total	= files.length;

    	for (int i=0;i<files.length;i++){
    		DiskManagerFileInfo	file = files[i];

    		if ( file.getLength() == file.getDownloaded()){
    			complete++;
    			if ( file.isSkipped()){
    				skipped++;
    				skipped_complete++;
    			}
    		}else if ( file.isSkipped()){
    			skipped++;
    		}
    	}

    	if ( skipped == 0 ){
    		text = StringInterner.intern(complete + "/" + total);
    	}else{
    		text = (complete-skipped_complete) + "(" + complete + ")/" + (total-skipped) + "(" + total + ")";
    	}
    }

    cell.setText( text );
  }
}
