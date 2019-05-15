/*
 * File    : HealthItem.java
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

import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.utils.ColorCache;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Rectangle;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.tracker.host.TRHost;
import com.biglybt.core.tracker.host.TRHostTorrent;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.ui.swt.views.table.TableCellSWTPaintListener;

import com.biglybt.core.CoreFactory;

import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.tables.*;

/**
 *
 * @author Olivier
 * @author TuxPaper (2004/Apr/17: modified to TableCellAdapter)
 */
public class HealthItem
	extends CoreTableColumnSWT
	implements TableCellAddedListener, TableCellRefreshListener, TableCellSWTPaintListener
{
	public static final Class DATASOURCE_TYPE = Download.class;

	static final int COLUMN_WIDTH = 16;

	public static final String COLUMN_ID = "health";

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] { CAT_ESSENTIAL });
	}

	static TRHost tracker_host = null;

	/** Default Constructor */
	public HealthItem(String sTableID) {
		super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_CENTER, COLUMN_WIDTH, sTableID);
		initializeAsGraphic(POSITION_LAST, COLUMN_WIDTH);
		setIconReference("column.image.health", true);
	}

	@Override
	public void cellAdded(TableCell cell) {
		cell.setMarginWidth(2);
		cell.setMarginHeight(2);
	}

	@Override
	public void refresh(TableCell cell) {
		if (tracker_host == null) {
			try {
			 	tracker_host = CoreFactory.getSingleton().getTrackerHost();
			} catch (Throwable t) {
			}
			if (tracker_host == null) {
				return;
			}
		}

		DownloadManager dm = (DownloadManager) cell.getDataSource();
		int health;
		TRHostTorrent ht;

		if (dm == null) {
			health = 0;
			ht = null;
		} else {
			health = dm.getHealthStatus();
			ht = tracker_host.getHostTorrent(dm.getTorrent());
		}

		if (!cell.setSortValue(health + (ht == null ? 0 : 256)) && cell.isValid())
			return;


		String sHelpID = null;

		if (health == DownloadManager.WEALTH_KO) {
			sHelpID = "health.explain.red";
		} else if (health == DownloadManager.WEALTH_OK) {
			sHelpID = "health.explain.green";
		} else if (health == DownloadManager.WEALTH_NO_TRACKER) {
			sHelpID = "health.explain.blue";
		} else if (health == DownloadManager.WEALTH_NO_REMOTE) {
			sHelpID = "health.explain.yellow";
		} else if (health == DownloadManager.WEALTH_ERROR) {
		} else {
			sHelpID = "health.explain.grey";
		}

		String sToolTip = (health == DownloadManager.WEALTH_ERROR && dm != null)
				? dm.getErrorDetails() : MessageText.getString(sHelpID);
		if (ht != null)
			sToolTip += "\n" + MessageText.getString("health.explain.share");
		cell.setToolTip(sToolTip);
	}

	// @see com.biglybt.ui.swt.views.table.TableCellSWTPaintListener#cellPaint(org.eclipse.swt.graphics.GC, com.biglybt.ui.swt.views.table.TableCellSWT)
	@Override
	public void cellPaint(GC gc, TableCellSWT cell) {

		Comparable sortValue = cell.getSortValue();
		if (!(sortValue instanceof Long)) {
			return;
		}
		boolean isShare = false;
		long health = ((Long) sortValue).longValue();
		if (health >= 256) {
			health -= 256;
			isShare = true;
		}

		String color = null;

		if (health == DownloadManager.WEALTH_KO) {
			color = "#f00";
		} else if (health == DownloadManager.WEALTH_OK) {
			color = "#0f0";
		} else if (health == DownloadManager.WEALTH_NO_TRACKER) {
			color = "#0ff";
		} else if (health == DownloadManager.WEALTH_NO_REMOTE) {
			color = "#00f";
		} else if (health == DownloadManager.WEALTH_ERROR) {
			color = "#800";
		} else {
			return;
		}

		Color swtColor = ColorCache.getColor(gc.getDevice(), color);
		if (swtColor != null) {
			Rectangle bounds = cell.getBounds();
			int x = bounds.x;
			int y = bounds.y;
			int width = bounds.width;
			int height = bounds.height;

			gc.setAdvanced(true);
			gc.setAntialias(SWT.ON);
			if (isShare) {
				gc.setForeground(Colors.getInstance().getSlightlyFadedColor(swtColor));
				if ( width < height){
					int pad = (height-width)/2;
					gc.fillGradientRectangle(x, y+pad, width, width, true);
				}else{
					gc.fillGradientRectangle(x, y, width, height, true);
				}
			} else {
				gc.setBackground(Colors.getInstance().getSlightlyFadedColor(swtColor));

				if ( width < height){
					int pad = (height-width)/2;
					gc.fillOval(x, y+pad, width, width);
				}else{
					gc.fillRoundRectangle(x, y, width, height, height, height);
				}
			}
		}
	}
}
