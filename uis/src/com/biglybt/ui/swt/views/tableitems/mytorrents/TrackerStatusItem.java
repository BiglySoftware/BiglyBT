/*
 * File    : TrackerStatusItem.java
 * Created : 24 nov. 2003
 * By      : Olivier
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

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.tracker.client.TRTrackerAnnouncerResponse;
import com.biglybt.core.tracker.client.TRTrackerScraperResponse;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.tables.*;

/**
 * @author Olivier
 *
 */
public class TrackerStatusItem extends CoreTableColumnSWT implements
		TableCellAddedListener, TableCellToolTipListener
{
	public static final Class DATASOURCE_TYPE = Download.class;

	public static final String COLUMN_ID = "tracker";

	public TrackerStatusItem(String sTableID) {
		super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_LEAD, 90, sTableID);
		setRefreshInterval(15); // Slow update while no responses from tracker
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_TRACKER,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE);
	}

	@Override
	public void cellAdded(TableCell cell) {
		new Cell(cell);
	}

	private static class Cell extends AbstractTrackerCell {
		public Cell(TableCell cell) {
			super(cell);
		}

		@Override
		public void refresh(TableCell cell) {
			super.refresh(cell);

			DownloadManager dm = (DownloadManager) cell.getDataSource();
			String status = dm == null ? "" : dm.getTrackerStatus();

			// status sometimes contains multiline text (e.g. HTML) on failure
			// - trim to end of first line break if present (see bug 1337563)

			int nl_pos = status.indexOf('\n');
			if (nl_pos >= 0)
				status = status.substring(0, nl_pos);

	    if (cell.setText(status) || !cell.isValid()) {
	    	TrackerCellUtils.updateColor(cell, dm, true);
	    }
		}

		@Override
		public void scrapeResult(TRTrackerScraperResponse response) {
			checkScrapeResult(response);
		}

		@Override
		public void announceResult(TRTrackerAnnouncerResponse response) {
			cell.invalidate();
		}
	}

	@Override
	public void cellHover(TableCell cell) {
		DownloadManager dm = (DownloadManager) cell.getDataSource();
		cell.setToolTip(TrackerCellUtils.getTooltipText(cell, dm, true));
	}

	@Override
	public void cellHoverComplete(TableCell cell) {
		cell.setToolTip(null);
	}
}
