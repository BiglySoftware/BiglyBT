/*
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

package com.biglybt.ui.swt.views.tableitems.files;

import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

import com.biglybt.core.internat.MessageText;

/**
 *
 * @author TuxPaper
 * @since 2.0.8.5
 */
public class PriorityItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{
  /** Default Constructor */
  public PriorityItem() {
    super("priority", ALIGN_LEAD, POSITION_LAST, 70, TableManager.TABLE_TORRENT_FILES);
    setRefreshInterval(INTERVAL_LIVE);
  }

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_CONTENT,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

  @Override
  public void refresh(TableCell cell) {
		DiskManagerFileInfo fileInfo = (DiskManagerFileInfo) cell.getDataSource();
		String tmp;
		int sortval = 0;

		if (fileInfo == null){

			tmp = "";

		}else{

			int	st = fileInfo.getStorageType();

			if (	(	st == DiskManagerFileInfo.ST_COMPACT ||
						st == DiskManagerFileInfo.ST_REORDER_COMPACT ) &&
					fileInfo.isSkipped()){

				tmp = MessageText.getString("FileItem.delete");

				sortval = Integer.MIN_VALUE;

			}else if ( fileInfo.isSkipped()){

				tmp = MessageText.getString("FileItem.donotdownload");

				sortval = Integer.MIN_VALUE+1;

			}else{

				int pri = fileInfo.getPriority();

				sortval = pri;

				if ( pri > 0 ) {

					tmp = MessageText.getString("FileItem.high");

					if ( pri > 1 ){

						tmp += " (" + pri + ")";
					}
				}else if ( pri < 0 ){

					tmp = MessageText.getString("FileItem.low");

					if ( pri < -1 ){

						tmp += " (" + pri + ")";
					}
				}else{

					tmp = MessageText.getString("FileItem.normal");
				}
			}
		}
		cell.setText(tmp);
		cell.setSortValue(sortval);
	}
}
