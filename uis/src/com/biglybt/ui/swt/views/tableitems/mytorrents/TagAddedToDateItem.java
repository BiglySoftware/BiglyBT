/*
 * Created on 05-May-2006
 * Created by Paul Gardner
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

package com.biglybt.ui.swt.views.tableitems.mytorrents;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.ui.swt.views.MyTorrentsView;
import com.biglybt.ui.swt.views.table.utils.TableColumnCreator;
import com.biglybt.ui.swt.views.tableitems.ColumnDateSizer;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableColumnInfo;

import com.biglybt.core.tag.Tag;
import com.biglybt.ui.common.table.TableView;
import com.biglybt.ui.common.table.TableViewCreator;

public class TagAddedToDateItem
	extends ColumnDateSizer
{
	public static final Class DATASOURCE_TYPE = Download.class;

	public static final String COLUMN_ID = "tag_added_to";

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] { CAT_TIME, CAT_CONTENT });
		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE);
	}

	public TagAddedToDateItem(String sTableID) {
		super(DATASOURCE_TYPE, COLUMN_ID, TableColumnCreator.DATE_COLUMN_WIDTH, sTableID);

		setMultiline(false);

		setRefreshInterval(INTERVAL_INVALID_ONLY);

		setShowTime( true );
	}

	/**
	 * @param tableID
	 * @param b
	 */
	public TagAddedToDateItem(String tableID, boolean v) {
		this(tableID);
		setVisible(v);
	}

	@Override
	public void refresh(TableCell cell, long timestamp) {
		DownloadManager dm = (DownloadManager) cell.getDataSource();
		TableView<?> tv =  cell.getTableRow().getView();
		TableViewCreator tvc =  tv==null?null:cell.getTableRow().getView().getTableViewCreator();
		if ( dm != null && tvc instanceof MyTorrentsView ){
			MyTorrentsView mtv = (MyTorrentsView)tvc;
			Tag[] tags = mtv.getCurrentTags();
			if ( tags != null && tags.length == 1 ){
				long time = tags[0].getTaggableAddedTime( dm );
				super.refresh(cell, time);
				return;
			}
		}
		super.refresh(cell, -1 );
	}
}
