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

import java.io.File;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.ui.swt.views.table.utils.TableColumnCreator;
import com.biglybt.ui.swt.views.tableitems.ColumnDateSizer;

import com.biglybt.ui.common.table.TableRowCore;

import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.pif.ui.tables.TableContextMenuItem;

public class DateAddedItem
	extends ColumnDateSizer
{
	public static final Class DATASOURCE_TYPE = Download.class;

	public static final String COLUMN_ID = "date_added";

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] { CAT_TIME, CAT_CONTENT });
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	public DateAddedItem(String sTableID) {
		super(DATASOURCE_TYPE, COLUMN_ID, TableColumnCreator.DATE_COLUMN_WIDTH, sTableID);

		setMultiline(false);


		TableContextMenuItem menuReset = addContextMenuItem("TableColumn.menu.date_added.reset");
		menuReset.addListener(new MenuItemListener() {
			@Override
			public void selected(MenuItem menu, Object target) {
				if (target instanceof TableRowCore) {
					TableRowCore row = (TableRowCore) target;
					Object dataSource = row.getDataSource(true);
					if (dataSource instanceof DownloadManager) {
						DownloadManager dm = (DownloadManager) dataSource;

						DownloadManagerState state = dm.getDownloadState();

						try {
							long add_time = new File(dm.getTorrentFileName()).lastModified();

							if (add_time >= 0) {
								state.setLongParameter(
										DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME, add_time);
							}

						} catch (Throwable e) {
						}
					}
					row.getTableCell("date_added").invalidate();
				}
			}
		});
	}

	/**
	 * @param tableID
	 * @param b
	 */
	public DateAddedItem(String tableID, boolean v) {
		this(tableID);
		setVisible(v);
	}

	@Override
	public void refresh(TableCell cell, long timestamp) {
		DownloadManager dm = (DownloadManager) cell.getDataSource();
		timestamp = (dm == null) ? 0 : dm.getDownloadState().getLongParameter(
				DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME);
		super.refresh(cell, timestamp);
		//cell.setText(DisplayFormatters.formatDate(timestamp));
	}
}
