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

import com.biglybt.activities.ActivitiesEntry;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;

import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.ui.swt.views.table.TableCellSWTPaintListener;

import com.biglybt.ui.swt.imageloader.ImageLoader;

import com.biglybt.pif.ui.tables.*;

/**
 * @author TuxPaper
 * @created Sep 25, 2008
 *
 */
public class ColumnActivityNew
	extends CoreTableColumnSWT
	implements TableCellSWTPaintListener, TableCellAddedListener,
	TableCellRefreshListener, TableCellMouseListener
{
	public static final String COLUMN_ID = "activityNew";

	private static final int WIDTH = 38; // enough to fit title

	private static Image imgNew;

	private static Image imgOld;

	public ColumnActivityNew(String tableID) {
		super(COLUMN_ID, tableID);

		initializeAsGraphic(WIDTH);
		setAlignment(ALIGN_CENTER);
		imgNew = ImageLoader.getInstance().getImage("image.activity.unread");
		imgOld = ImageLoader.getInstance().getImage("image.activity.read");
	}

	// @see com.biglybt.ui.swt.views.table.TableCellSWTPaintListener#cellPaint(org.eclipse.swt.graphics.GC, com.biglybt.pif.ui.tables.TableCell)
	@Override
	public void cellPaint(GC gc, TableCellSWT cell) {
		ActivitiesEntry entry = (ActivitiesEntry) cell.getDataSource();

		Rectangle cellBounds = cell.getBounds();
		Image img = entry.getReadOn() <= 0 ? imgNew : imgOld;

		if (img != null && !img.isDisposed()) {
			Rectangle imgBounds = img.getBounds();
			gc.drawImage(img, cellBounds.x
					+ ((cellBounds.width - imgBounds.width) / 2), cellBounds.y
					+ ((cellBounds.height - imgBounds.height) / 2));
		}
	}

	// @see com.biglybt.pif.ui.tables.TableCellAddedListener#cellAdded(com.biglybt.pif.ui.tables.TableCell)
	@Override
	public void cellAdded(TableCell cell) {
		cell.setMarginWidth(0);
		cell.setMarginHeight(0);
	}

	// @see com.biglybt.pif.ui.tables.TableCellRefreshListener#refresh(com.biglybt.pif.ui.tables.TableCell)
	@Override
	public void refresh(TableCell cell) {
		ActivitiesEntry entry = (ActivitiesEntry) cell.getDataSource();

		boolean isRead = entry.getReadOn() > 0;
		int sortVal = isRead ? 1 : 0;

		if (cell.setSortValue(sortVal)) {
			cell.invalidate();
		}
	}

	// @see com.biglybt.pif.ui.tables.TableCellMouseListener#cellMouseTrigger(com.biglybt.pif.ui.tables.TableCellMouseEvent)
	@Override
	public void cellMouseTrigger(final TableCellMouseEvent event) {
		if (event.eventType == TableRowMouseEvent.EVENT_MOUSEDOWN
				&& event.button == 1) {
			ActivitiesEntry entry = (ActivitiesEntry) event.cell.getDataSource();

			if (entry.canFlipRead()) {
				entry.setRead(!entry.isRead());
				event.cell.invalidate();
			}
		}
	}
}
