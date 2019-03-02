/*
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

package com.biglybt.ui.swt.shells.opentorrent;

import com.biglybt.core.torrent.impl.TorrentOpenFileOptions;
import com.biglybt.pif.ui.tables.*;

import com.biglybt.ui.swt.columns.ColumnCheckBox;

public class TableColumnOTOF_Download
	extends ColumnCheckBox
{
	public static final String COLUMN_ID = "download";

	/** Default Constructor */
	public TableColumnOTOF_Download(TableColumn column) {
		super(column, 60);
		column.setPosition(TableColumn.POSITION_LAST);
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			TableColumn.CAT_ESSENTIAL,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	@Override
	protected Boolean getCheckBoxState(Object ds) {
		if (!(ds instanceof TorrentOpenFileOptions)) {
			return false;
		}
		return ((TorrentOpenFileOptions) ds).isToDownload();
	}

	@Override
	protected void setCheckBoxState(Object ds, boolean set) {
		if (!(ds instanceof TorrentOpenFileOptions)) {
			return;
		}
		TorrentOpenFileOptions file = (TorrentOpenFileOptions)ds;
		
		file.setToDownload(set);
		
		file.getTorrentOptions().applyAutoTagging();
	}
}
