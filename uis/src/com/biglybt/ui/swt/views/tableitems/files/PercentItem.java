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
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.views.FilesView;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

/**
 *
 * @author TuxPaper
 * @since 2.0.8.5
 */
public class PercentItem
	extends CoreTableColumnSWT
	implements TableCellRefreshListener
{
	/** Default Constructor */
	public PercentItem() {
		super("%", ALIGN_TRAIL, POSITION_LAST, 60, TableManager.TABLE_TORRENT_FILES);
		setRefreshInterval(INTERVAL_LIVE);
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_PROGRESS,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	@Override
	public void refresh(TableCell cell) {

		DiskManagerFileInfo fileInfo = (DiskManagerFileInfo) cell.getDataSource();

		boolean internal = fileInfo instanceof FilesView.FilesViewTreeNode && !((FilesView.FilesViewTreeNode)fileInfo).isLeaf();

		long percent = 0;

		if (fileInfo != null) {
			long bytesDownloaded = fileInfo.getDownloaded();

			if (bytesDownloaded < 0) {

				percent = -1; // unknown skeleton value

			} else {
				long length = fileInfo.getLength();

				if (length != 0) {

					percent = (1000 * bytesDownloaded) / length;

				} else {

					percent = 1000;
				}
			}

		} else {

			percent = -1; // unknown skeleton value
		}

		if (!cell.setSortValue(percent) && cell.isValid()) {

			return;
		}

		String text;
		
		if ( percent < 0 ){
			text = "";
			
			cell.setNumeric(Double.NaN);
		}else{
			text = DisplayFormatters.formatPercentFromThousands((int) percent);
			
			cell.setNumeric( percent/10d );
			
			if ( internal ){
				
				//text = "(" + text + ")";
			}
		}
		cell.setText( text );
	}
}
