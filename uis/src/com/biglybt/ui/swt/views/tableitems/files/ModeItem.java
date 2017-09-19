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
 * @since 1.0.0.0
 */
public class ModeItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{
  /** Default Constructor */
  public ModeItem() {
    super("mode", ALIGN_LEAD, POSITION_LAST, 60, TableManager.TABLE_TORRENT_FILES);
    setRefreshInterval(INTERVAL_LIVE);
    setMinWidthAuto(true);
  }

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_CONTENT,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE);
	}

  @Override
  public void refresh(TableCell cell) {
    DiskManagerFileInfo fileInfo = (DiskManagerFileInfo)cell.getDataSource();
    long value = (fileInfo == null) ? 0 : fileInfo.getAccessMode();

    if( !cell.setSortValue( value ) && cell.isValid() ) {
      return;
    }


    String sText =
      MessageText.getString( value == DiskManagerFileInfo.WRITE ? "FileItem.write" : "FileItem.read" );
    cell.setText(sText);
  }
}
