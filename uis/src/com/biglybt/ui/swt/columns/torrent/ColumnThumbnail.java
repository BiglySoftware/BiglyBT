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

package com.biglybt.ui.swt.columns.torrent;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;

import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.utils.TorrentUIUtilsV3;
import com.biglybt.ui.swt.utils.TorrentUIUtilsV3.ContentImageLoadedListener;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.ui.swt.views.table.TableCellSWTPaintListener;
import com.biglybt.util.DataSourceUtils;

/**
 * A non-interactive (no click no hover) thumbnail column
 * @author khai
 *
 */

public class ColumnThumbnail
	extends CoreTableColumnSWT
	implements TableCellRefreshListener, TableCellSWTPaintListener, TableCellToolTipListener
{
	public static final String COLUMN_ID = "Thumbnail";

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] { CAT_CONTENT });
	}

	private static final int WIDTH_SMALL = 35;

	private static final int WIDTH_BIG = 60;

	private static final int WIDTH_ACTIVITY = 80;

	/**
	 * Each cell is mapped to a torrent
	 */
	private Map mapCellTorrent = new HashMap();

	/** Default Constructor */
	public ColumnThumbnail(String sTableID) {
		super(COLUMN_ID, ALIGN_CENTER, POSITION_LAST, 0, sTableID);
		if (TableManager.TABLE_ACTIVITY_BIG.equals(sTableID)) {
			initializeAsGraphic(WIDTH_ACTIVITY);
		} else {
			initializeAsGraphic(sTableID.endsWith(".big") ? WIDTH_BIG : WIDTH_SMALL);
		}
	}

	/**
	 * @param column
	 */
	public ColumnThumbnail(TableColumn column) {
		super(null, null);

		column.initialize(ALIGN_CENTER, POSITION_LAST, WIDTH_BIG);
		column.addListeners(this);
	}

	public void dispose(TableCell cell) {
		mapCellTorrent.remove(cell);
	}

	@Override
	public void refresh(final TableCell cell) {

		Object ds = cell.getDataSource();
		TOTorrent newTorrent = DataSourceUtils.getTorrent(ds);

		//System.out.println("REF");
		//TableCellImpl c1 = ((TableCellImpl) cell);
		//TableRowSWT tableRowSWT = c1.getTableRowSWT();
		//TableViewSWTImpl view = (TableViewSWTImpl) tableRowSWT.getView();
		//System.out.println(view.getComposite());
		//view.getTableComposite().redraw(0, 0, 5000, 5000, true);
		//view.getTableComposite().update();

		/*
		 * Get the torrent for this cell
		 */
		TOTorrent torrent = (TOTorrent) mapCellTorrent.get(cell);

		torrent = newTorrent;
		mapCellTorrent.put(cell, torrent);
	}

	// @see com.biglybt.ui.swt.views.table.TableCellSWTPaintListener#cellPaint(org.eclipse.swt.graphics.GC, com.biglybt.ui.swt.views.table.TableCellSWT)
	@Override
	public void cellPaint(GC gc, final TableCellSWT cell) {
		Object ds = cell.getDataSource();

		Rectangle cellBounds = cell.getBounds();

		Image[] imgThumbnail = TorrentUIUtilsV3.getContentImage(ds,
				cellBounds.width >= 20 && cellBounds.height >= 20,
				new ContentImageLoadedListener() {
					@Override
					public void contentImageLoaded(Image image, boolean wasReturned) {
						if (!wasReturned) {
							// this may be triggered many times, so only invalidate and don't
							// force a refresh()
							cell.invalidate();
						}
					}
				});

		if (imgThumbnail == null || imgThumbnail[0] == null) {
			// don't need to release a null image
			return;
		}

		if (cellBounds.height > 30) {
			cellBounds.y += 2;
			cellBounds.height -= 4;
		}

		Rectangle imgBounds = imgThumbnail[0].getBounds();

		int dstWidth;
		int dstHeight;
		if (imgBounds.height > cellBounds.height) {
			dstHeight = cellBounds.height;
			dstWidth = imgBounds.width * cellBounds.height / imgBounds.height;
		} else if (imgBounds.width > cellBounds.width)  {
			dstWidth = cellBounds.width - 4;
			dstHeight = imgBounds.height * cellBounds.width / imgBounds.width;
		} else {
			dstWidth = imgBounds.width;
			dstHeight = imgBounds.height;
		}

		try {
			gc.setAdvanced(true);
			gc.setInterpolation(SWT.HIGH);
		} catch (Exception e) {
		}
		int x = cellBounds.x + ((cellBounds.width - dstWidth + 1) / 2);
		int y = cellBounds.y + ((cellBounds.height - dstHeight + 1) / 2);
		if (dstWidth > 0 && dstHeight > 0 && !imgBounds.isEmpty()) {
			Rectangle dst = new Rectangle(x, y, dstWidth, dstHeight);
			Rectangle lastClipping = gc.getClipping();
			try {
				Utils.setClipping(gc, cellBounds);

				for (int i = 0; i < imgThumbnail.length; i++) {
					Image image = imgThumbnail[i];
					if (image == null) {
						continue;
					}
					Rectangle srcBounds = image.getBounds();
					if (i == 0) {
						int w = dstWidth;
						int h = dstHeight;
						if (imgThumbnail.length > 1) {
							w = w * 9 / 10;
							h = h * 9 / 10;
						}
  					gc.drawImage(image, srcBounds.x, srcBounds.y, srcBounds.width,
  							srcBounds.height, x, y, w, h);
					} else {
						int w = dstWidth * 3 / 8;
						int h = dstHeight * 3 / 8;
						gc.drawImage(image, srcBounds.x, srcBounds.y, srcBounds.width,
								srcBounds.height, x + dstWidth - w, y + dstHeight - h, w, h);
					}
				}
			} catch (Exception e) {
				Debug.out(e);
			} finally {
				Utils.setClipping(gc, lastClipping);
			}
		}

		TorrentUIUtilsV3.releaseContentImage(ds);
	}

	// @see com.biglybt.pif.ui.tables.TableCellToolTipListener#cellHover(com.biglybt.pif.ui.tables.TableCell)
	@Override
	public void cellHover(TableCell cell) {
		final Object ds = cell.getDataSource();
		Image[] imgThumbnail = TorrentUIUtilsV3.getContentImage(ds, true, new ContentImageLoadedListener() {
			@Override
			public void contentImageLoaded(Image image, boolean wasReturned) {
				TorrentUIUtilsV3.releaseContentImage(ds);
			}
		});

		cell.setToolTip(imgThumbnail == null ? null : imgThumbnail[0]);
	}

	// @see com.biglybt.pif.ui.tables.TableCellToolTipListener#cellHoverComplete(com.biglybt.pif.ui.tables.TableCell)
	@Override
	public void cellHoverComplete(TableCell cell) {
		Object ds = cell.getDataSource();
		TorrentUIUtilsV3.releaseContentImage(ds);
	}
}
