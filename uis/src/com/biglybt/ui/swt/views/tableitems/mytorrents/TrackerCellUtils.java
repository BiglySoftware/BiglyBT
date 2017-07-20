/*
 * File    : TrackerCellUtils.java
 * Created : Nov 24, 2005
 * By      : TuxPaper
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

import org.eclipse.swt.graphics.Color;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.tracker.client.TRTrackerScraperResponse;
import com.biglybt.core.tracker.client.impl.bt.TRTrackerBTScraperResponseImpl;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.Colors;

import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.tables.TableCell;

/**
 * @author TuxPaper
 *
 */
public class TrackerCellUtils
{
	public static final Class DATASOURCE_TYPE = Download.class;

	public static void updateColor(TableCell cell, DownloadManager dm, boolean show_errors ) {
		if (dm == null || cell == null)
			return;

		if ( show_errors ){
			if ( dm.isTrackerError()){
				cell.setForegroundToErrorColor();
				return;
			}
		}
		TRTrackerScraperResponse response = dm.getTrackerScrapeResponse();
		if (response instanceof TRTrackerBTScraperResponseImpl && response.getStatus() == TRTrackerScraperResponse.ST_ONLINE) {
			boolean bMultiHashScrapes = ((TRTrackerBTScraperResponseImpl) response).getTrackerStatus().getSupportsMultipeHashScrapes();
			Color color = (bMultiHashScrapes) ? null : Colors.grey;
			cell.setForeground(Utils.colorToIntArray(color));
		}else{
			cell.setForeground(Utils.colorToIntArray(null));
		}
	}

	public static String getTooltipText(TableCell cell, DownloadManager dm, boolean show_errors ) {
		if (dm == null || cell == null)
			return null;

		if ( show_errors ){
			if ( dm.isTrackerError()){
				return( null );
			}
		}
		String sToolTip = null;
		TRTrackerScraperResponse response = dm.getTrackerScrapeResponse();
		if (response instanceof TRTrackerBTScraperResponseImpl && response.getStatus() == TRTrackerScraperResponse.ST_ONLINE ) {
			String sPrefix = ((TRTrackerBTScraperResponseImpl) response).getTrackerStatus().getSupportsMultipeHashScrapes()
					? "" : "No";
			sToolTip = MessageText.getString("Tracker.tooltip." + sPrefix
					+ "MultiSupport");
		}
		return sToolTip;
	}
}
