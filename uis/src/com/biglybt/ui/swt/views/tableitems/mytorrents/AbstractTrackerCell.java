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
  */
package com.biglybt.ui.swt.views.tableitems.mytorrents;

import java.net.URL;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerTrackerListener;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.tracker.client.TRTrackerAnnouncerResponse;
import com.biglybt.core.tracker.client.TRTrackerScraperResponse;

import com.biglybt.pif.ui.tables.*;

/**
 * Base cell class for cells listening to the tracker listener
 */
abstract class AbstractTrackerCell implements TableCellRefreshListener,
		TableCellToolTipListener, TableCellDisposeListener,
		DownloadManagerTrackerListener {

	TableCell cell;

	private DownloadManager dm;

	/**
	 * Initialize
	 *
	 * @param cell
	 */
	public AbstractTrackerCell(TableCell cell) {
		this.cell = cell;
		cell.addListeners(this);

		dm = (DownloadManager) cell.getDataSource();
		if (dm == null)
			return;
		dm.addTrackerListener(this);
	}

	@Override
	public void announceResult(TRTrackerAnnouncerResponse response) {
		// Don't care about announce
	}

	public boolean checkScrapeResult(final TRTrackerScraperResponse response) {
		if (response != null) {
			TableCell cell_ref = cell;

			if ( cell_ref == null ){
				return( false );
			}
			// Exit if this scrape result is not from the tracker currently being used.
			DownloadManager dm = (DownloadManager) cell.getDataSource();
			if (dm == null || dm != this.dm)
				return false;

			TOTorrent	torrent = dm.getTorrent();

			if ( torrent == null ){
				return( false );
			}
			URL announceURL = torrent.getAnnounceURL();
			URL responseURL = response.getURL();
			if (announceURL != responseURL && announceURL != null
					&& responseURL != null
					&& !announceURL.toString().equals(responseURL.toString()))
				return false;


			cell_ref.invalidate();

			return response.isValid();
		}

		return false;
	}

	@Override
	public void refresh(TableCell cell) {
		DownloadManager oldDM = dm;
		dm = (DownloadManager) cell.getDataSource();

		// datasource changed, change listener
		if (dm != oldDM) {
			if (oldDM != null)
				oldDM.removeTrackerListener(this);

			if (dm != null)
				dm.addTrackerListener(this);
		}
	}

	@Override
	public void cellHover(TableCell cell) {
	}

	@Override
	public void cellHoverComplete(TableCell cell) {
		cell.setToolTip(null);
	}

	@Override
	public void dispose(TableCell cell) {
		if (dm != null)
			dm.removeTrackerListener(this);
		DownloadManager dm = (DownloadManager) cell.getDataSource();
		if (dm != null && dm != this.dm)
			dm.removeTrackerListener(this);
		dm = null;
		cell = null;
	}
}
