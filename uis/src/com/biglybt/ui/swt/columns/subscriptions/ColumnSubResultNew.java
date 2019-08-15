/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */

package com.biglybt.ui.swt.columns.subscriptions;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;

import com.biglybt.core.subs.util.SubscriptionResultFilterable;
import com.biglybt.core.util.SystemTime;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.ui.swt.views.table.TableCellSWTPaintListener;

/**
 * @author TuxPaper
 * @created Sep 25, 2008
 *
 */
public class ColumnSubResultNew
	implements TableCellSWTPaintListener, TableCellAddedListener,
	TableCellRefreshListener, TableCellMouseListener
{
	public static final String COLUMN_ID = "new";

	private static final int WIDTH = 38; // enough to fit title

	private static Image imgNew;

	private static Image imgOld;


	public ColumnSubResultNew(TableColumn column ) {

		column.initialize(TableColumn.ALIGN_CENTER, TableColumn.POSITION_LAST, WIDTH );
		column.addListeners(this);
		column.setRefreshInterval(TableColumn.INTERVAL_GRAPHIC);
		column.setType(TableColumn.TYPE_GRAPHIC);

		imgNew = ImageLoader.getInstance().getImage("image.activity.unread");
		imgOld = ImageLoader.getInstance().getImage("image.activity.read");
	}

	@Override
	public void cellPaint(GC gc, TableCellSWT cell) {
		SubscriptionResultFilterable entry = (SubscriptionResultFilterable) cell.getDataSource();

		Rectangle cellBounds = cell.getBounds();
		Image img = entry== null || entry.getRead() ? imgOld: imgNew;

		if (img != null && !img.isDisposed()) {
			Rectangle imgBounds = img.getBounds();
			gc.drawImage(img, cellBounds.x
					+ ((cellBounds.width - imgBounds.width) / 2), cellBounds.y
					+ ((cellBounds.height - imgBounds.height) / 2));
		}
	}

	@Override
	public void cellAdded(TableCell cell) {
		cell.setMarginWidth(0);
		cell.setMarginHeight(0);

		if ( cell instanceof TableCellSWT ){

			((TableCellSWT)cell).setCursorID( SWT.CURSOR_HAND );
		}
	}

	@Override
	public void refresh(TableCell cell) {
		SubscriptionResultFilterable entry = (SubscriptionResultFilterable)cell.getDataSource();

		if ( entry != null ){

			boolean unread = !entry.getRead();

			long sortVal = ((unread ? 2 : 1L) << 61)
					+ (SystemTime.getCurrentTime() - entry.getTime()) / 1000;

			cell.setSortValue(sortVal);
		}
	}

	@Override
	public void cellMouseTrigger(final TableCellMouseEvent event) {
		if (event.eventType == TableRowMouseEvent.EVENT_MOUSEDOWN
				&& event.button == 1) {
			SubscriptionResultFilterable entry = (SubscriptionResultFilterable) event.cell.getDataSource();

			if ( entry != null ){

				entry.setRead(!entry.getRead());

				event.cell.invalidate();
			}
		}
	}
}
