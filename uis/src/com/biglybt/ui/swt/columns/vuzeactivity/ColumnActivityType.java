/*
 * Created on Sep 25, 2008
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.ui.swt.columns.vuzeactivity;

import java.text.SimpleDateFormat;
import java.util.Date;

import com.biglybt.activities.ActivitiesEntry;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;

import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.ui.swt.views.table.TableCellSWTPaintListener;

import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.imageloader.ImageLoader.ImageDownloaderListener;

import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;

/**
 * @author TuxPaper
 * @created Sep 25, 2008
 *
 */
public class ColumnActivityType
	extends CoreTableColumnSWT
	implements TableCellSWTPaintListener, TableCellRefreshListener
{
	public static final String COLUMN_ID = "activityType";

	private static final int WIDTH = 42; // enough to fit title in most cases

	private static SimpleDateFormat timeFormat = new SimpleDateFormat(
			"h:mm:ss a, EEEE, MMMM d, yyyy");

	public ColumnActivityType(String tableID) {
		super(COLUMN_ID, tableID);

		initializeAsGraphic(WIDTH);
	}

	// @see com.biglybt.ui.swt.views.table.TableCellSWTPaintListener#cellPaint(org.eclipse.swt.graphics.GC, com.biglybt.pif.ui.tables.TableCell)
	@Override
	public void cellPaint(GC gc, final TableCellSWT cell) {
		ActivitiesEntry entry = (ActivitiesEntry) cell.getDataSource();

		Image imgIcon;
		String iconID = entry.getIconID();
		if (iconID != null) {
			ImageLoader imageLoader = ImageLoader.getInstance();
			if (iconID.startsWith("http")) {
				imgIcon = imageLoader.getUrlImage(iconID,
						new ImageDownloaderListener() {
							@Override
							public void imageDownloaded(Image image,
							                            boolean returnedImmediately) {
								if (returnedImmediately) {
									return;
								}
								cell.invalidate();
							}
						});
				if (imgIcon == null) {
					return;
				}
			} else {
				imgIcon = imageLoader.getImage(iconID);
			}

			if (ImageLoader.isRealImage(imgIcon)) {
				Rectangle cellBounds = cell.getBounds();
				Rectangle imgBounds = imgIcon.getBounds();
				gc.drawImage(imgIcon, cellBounds.x
						+ ((cellBounds.width - imgBounds.width) / 2), cellBounds.y
						+ ((cellBounds.height - imgBounds.height) / 2));
			}
			imageLoader.releaseImage(iconID);
		}
	}

	// @see com.biglybt.pif.ui.tables.TableCellRefreshListener#refresh(com.biglybt.pif.ui.tables.TableCell)
	@Override
	public void refresh(TableCell cell) {
		ActivitiesEntry entry = (ActivitiesEntry) cell.getDataSource();
		String sort = entry.getTypeID() + entry.getIconID();

		if (cell.setSortValue(sort) || !cell.isValid()) {
			String ts = timeFormat.format(new Date(entry.getTimestamp()));
			cell.setToolTip("Activity occurred on " + ts);
		}
	}

}
