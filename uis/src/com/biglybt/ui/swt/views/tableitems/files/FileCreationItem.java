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

package com.biglybt.ui.swt.views.tableitems.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;

import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.ui.swt.views.tableitems.ColumnDateSizer;

import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.pif.ui.tables.TableManager;

public class FileCreationItem
	extends ColumnDateSizer
{
	public FileCreationItem(){
		super( "filecreated", ALIGN_TRAIL, POSITION_INVISIBLE, 60, TableManager.TABLE_TORRENT_FILES);

		setRefreshInterval(2);
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_TIME,
			CAT_CONTENT,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE);
	}

	@Override
	public void refresh(TableCell cell, long timestamp) {
		DiskManagerFileInfo file = (DiskManagerFileInfo)cell.getDataSource();
		
		if ( file == null ){
			
			timestamp = 0;
			
		}else{

			try {
				FileTime creationTime = (FileTime) Files.getAttribute(file.getFile(true).toPath(), "creationTime");
				timestamp = creationTime.toMillis();
			} catch (IOException ex) {
			}
		}
		
		super.refresh(cell, timestamp);
	}
}
