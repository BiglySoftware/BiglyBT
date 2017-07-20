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

package com.biglybt.ui.swt.views.tableitems.mytorrents;

import org.eclipse.swt.graphics.Image;

import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.ui.swt.views.table.TableCellSWT;

import com.biglybt.ui.swt.imageloader.ImageLoader;

/**
 * @author TuxPaper
 * @created Jul 11, 2010
 *
 */
public class ColumnTorrentSpeed
	extends CoreTableColumnSWT
	implements TableCellRefreshListener
{
	public static final Class DATASOURCE_TYPE = Download.class;

	public static final String COLUMN_ID = "torrentspeed";

	private Image imgUp;

	private Image imgDown;

	public ColumnTorrentSpeed(String tableID) {
		super(COLUMN_ID, 80, tableID);
		setAlignment(ALIGN_TRAIL);
		setType(TableColumn.TYPE_TEXT);
    setRefreshInterval(INTERVAL_LIVE);
    setUseCoreDataSource(false);

    ImageLoader imageLoader = ImageLoader.getInstance();
    imgUp = imageLoader.getImage("image.torrentspeed.up");
    imgDown = imageLoader.getImage("image.torrentspeed.down");
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_ESSENTIAL,
			CAT_BYTES,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

  @Override
  public void refresh(TableCell cell) {
	Object ds = cell.getDataSource();
  	if (!(ds instanceof Download)) {
  		return;
  	}
    Download dm = (Download)ds;
    long value;
    long sortValue;
    String prefix = "";

    int iState;
    iState = dm.getState();
    if (iState == Download.ST_DOWNLOADING) {
    	value = dm.getStats().getDownloadAverage();
    	((TableCellSWT)cell).setIcon(imgDown);
    } else if (iState == Download.ST_SEEDING) {
    	value = dm.getStats().getUploadAverage();
    	((TableCellSWT)cell).setIcon(imgUp);
    } else {
    	((TableCellSWT)cell).setIcon(null);
    	value = 0;
    }
    sortValue = (value << 4) | iState;


    if (cell.setSortValue(sortValue) || !cell.isValid()) {
    	cell.setText(value > 0 ? prefix + DisplayFormatters.formatByteCountToKiBEtcPerSec(value) : "");
    }
  }

}
