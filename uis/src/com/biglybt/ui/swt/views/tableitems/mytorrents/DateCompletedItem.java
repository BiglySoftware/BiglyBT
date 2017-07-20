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
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.TimeFormatter;
import com.biglybt.pif.download.DownloadTypeComplete;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.ui.swt.views.table.utils.TableColumnCreator;
import com.biglybt.ui.swt.views.tableitems.ColumnDateSizer;

public class DateCompletedItem
	extends ColumnDateSizer
{
	public static final Class DATASOURCE_TYPE = DownloadTypeComplete.class;

	public static final String COLUMN_ID = "DateCompleted";

	private static final long SHOW_ETA_AFTER_MS = 30000;

	public DateCompletedItem(String sTableID) {
		super(DATASOURCE_TYPE, COLUMN_ID, TableColumnCreator.DATE_COLUMN_WIDTH, sTableID);

		setMultiline(false);
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_TIME,
			CAT_CONTENT,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	/**
	 * @param tableID
	 * @param b
	 */
	public DateCompletedItem(String tableID, boolean v) {
		this(tableID);
		setVisible(v);
	}

	@Override
	public void refresh(TableCell cell, long timestamp) {
		DownloadManager dm = (DownloadManager) cell.getDataSource();
		long value = 0;
		if (dm == null) {
			return;
		}
		if (dm.isDownloadComplete(false)) {
			long completedTime = dm.getDownloadState().getLongParameter(
					DownloadManagerState.PARAM_DOWNLOAD_COMPLETED_TIME);
			if (completedTime <= 0) {
				value = dm.getDownloadState().getLongParameter(
						DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME);
			} else {
				value = completedTime;
			}
		} else {
			long diff = SystemTime.getCurrentTime() - dm.getStats().getTimeStarted();
			if (diff > SHOW_ETA_AFTER_MS) {
				long eta = dm.getStats().getSmoothedETA();
				if (eta > 0) {
					String sETA = TimeFormatter.format(eta);
					value = eta << 42;
					if (value < 0) {
						value = Long.MAX_VALUE;
					}
					cell.setText(MessageText.getString(
							"MyTorrents.column.ColumnProgressETA.2ndLine", new String[] {
								sETA
							}));
				} else {
					cell.setText("");
					// make above
					value = SystemTime.getCurrentTime() / 1000 * 1001;
				}
			} else {
				cell.setText("");
				value = SystemTime.getCurrentTime() / 1000 * 1002;
			}

			cell.invalidate();

			cell.setSortValue(value);
			return;
		}

		super.refresh(cell, value);
	}

	/* (non-Javadoc)
	 * @see com.biglybt.ui.swt.views.tableitems.ColumnDateSizer#cellHover(com.biglybt.pif.ui.tables.TableCell)
	 */
	@Override
	public void cellHover(TableCell cell) {
		super.cellHover(cell);
		Object oTooltip = cell.getToolTip();
		String s = (oTooltip instanceof String) ? (String) oTooltip + "\n" : "";
		DownloadManager dm = (DownloadManager) cell.getDataSource();
		long dateAdded = (dm == null) ? 0 : dm.getDownloadState().getLongParameter(
				DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME);
		if (dateAdded != 0) {
			s += MessageText.getString("TableColumn.header.date_added") + ": "
					+ DisplayFormatters.formatDate(dateAdded) + " ("
					+ DisplayFormatters.formatETA((SystemTime.getCurrentTime() - dateAdded) / 1000,
							false)
					+ ")";
			cell.setToolTip(s);
		}
	}
}
