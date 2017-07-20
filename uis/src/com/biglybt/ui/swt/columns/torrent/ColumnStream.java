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

package com.biglybt.ui.swt.columns.torrent;

import com.biglybt.ui.common.table.impl.TableColumnManager;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.views.skin.TorrentListViewsUtils;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AEThread2;
import com.biglybt.pif.download.DownloadTypeIncomplete;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.ui.swt.views.table.TableCellSWTPaintListener;

import com.biglybt.ui.common.table.TableCellCore;
import com.biglybt.ui.common.table.TableRowCore;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.util.PlayUtils;

/**
 * @author TuxPaper
 * @created Sep 25, 2008
 *
 */
public class ColumnStream
	extends CoreTableColumnSWT
	implements TableCellSWTPaintListener, TableCellAddedListener,
	TableCellRefreshListener, TableCellMouseListener, TableCellToolTipListener
{
	public static final String COLUMN_ID = "TorrentStream";

	public static final Class[] DATASOURCE_TYPES = {
		DownloadTypeIncomplete.class,
		com.biglybt.pif.disk.DiskManagerFileInfo.class
	};

	private static final int WIDTH = 62; // enough to fit title

	private static Image imgGreen;

	private static Image imgDisabled;

	private static Image imgBlue;

	private static final Object  firstLock = new Object();

	private static boolean first = true;

	static boolean skipPaint = true;

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_ESSENTIAL,
			CAT_CONTENT
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	public ColumnStream(String tableID) {
		super(COLUMN_ID, tableID);
		addDataSourceTypes(DATASOURCE_TYPES);

		initializeAsGraphic(WIDTH);
		setAlignment(ALIGN_CENTER);

		synchronized( ColumnStream.class ){
			if (imgGreen == null) {
		  		imgGreen = ImageLoader.getInstance().getImage("column.image.play.green");
		  		imgDisabled = ImageLoader.getInstance().getImage("column.image.play.off");
		  		imgBlue = ImageLoader.getInstance().getImage("column.image.play.blue");
			}
		}
	}

	// @see TableColumnImpl#preAdd()
	@Override
	public void preAdd() {
		if (!isFirstLoad() || getPosition() >= 0 || getColumnAdded()) {
			return;
		}
		TableColumnManager tcManager = TableColumnManager.getInstance();
		TableColumnInfo columnInfoTAN = tcManager.getColumnInfo(null, getTableID(),
				ColumnThumbAndName.COLUMN_ID);
		if (columnInfoTAN != null) {
			TableColumn column = columnInfoTAN.getColumn();
			if (column != null) {
				int position = column.getPosition();
				if (position >= 0) {
					setPosition(position + 1);
				}
			}
		}
	}

	private boolean noIconForYou(Object ds, TableCell cell) {
		if (!(ds instanceof DownloadManager)) {
			return false;
		}
		if (!(cell instanceof TableCellCore)) {
			return false;
		}
		DownloadManager dm = (DownloadManager) ds;
		TableRowCore rowCore = ((TableCellCore) cell).getTableRowCore();
		return rowCore != null && (dm.getNumFileInfos() > 1 && rowCore.isExpanded());

	}

	// @see com.biglybt.ui.swt.views.table.TableCellSWTPaintListener#cellPaint(org.eclipse.swt.graphics.GC, com.biglybt.pif.ui.tables.TableCell)
	@Override
	public void cellPaint(GC gc, TableCellSWT cell) {

		Object ds = cell.getDataSource();
		if (noIconForYou(ds, cell)) {
			return;
		}

		Comparable sortValue = cell.getSortValue();
		if (!(sortValue instanceof Number)) {
			return;
		}
		int sortVal = ((Number) sortValue).intValue();
		boolean canStream = (sortVal & 2) > 0;
		boolean canPlay = (sortVal & 1) > 0;
		// for now, always use green
		Image img = canStream ? imgBlue : canPlay ? imgGreen : imgDisabled;

		Rectangle cellBounds = cell.getBounds();

		if (img != null && !img.isDisposed()) {
			Utils.drawImageCenterScaleDown(gc, img, cellBounds);
		}
	}

	// @see com.biglybt.pif.ui.tables.TableCellAddedListener#cellAdded(com.biglybt.pif.ui.tables.TableCell)
	@Override
	public void cellAdded(final TableCell cell) {
		cell.setMarginWidth(0);
		cell.setMarginHeight(0);

		synchronized ( firstLock ) {
			if (first) {
				first = false;
				new AEThread2("WaitForMS", true) {
					@Override
					public void run() {
						Object ds = cell.getDataSource();
						// first call may take forever
						PlayUtils.canStreamDS(ds, -1,true);
						skipPaint = false;
					}
				};
			}
		}
	}

	// @see com.biglybt.pif.ui.tables.TableCellRefreshListener#refresh(com.biglybt.pif.ui.tables.TableCell)
	@Override
	public void refresh(TableCell cell) {
		int sortVal;
		Object ds = cell.getDataSource();
		if (noIconForYou(ds, cell)) {
			sortVal = 0;
		} else {
			boolean canStream = PlayUtils.canStreamDS(ds, -1,false);
			boolean canPlay = PlayUtils.canPlayDS(ds, -1,false);
			sortVal = (canStream ? 2 : 0) + (canPlay ? 1 : 0);
		}

		if (cell.setSortValue(sortVal)) {
			cell.invalidate();
		}
	}

	// @see com.biglybt.pif.ui.tables.TableCellMouseListener#cellMouseTrigger(com.biglybt.pif.ui.tables.TableCellMouseEvent)
	@Override
	public void cellMouseTrigger(final TableCellMouseEvent event) {
		if (event.eventType == TableRowMouseEvent.EVENT_MOUSEDOWN
				&& event.button == 1) {
			Object ds = event.cell.getDataSource();
			if (PlayUtils.canStreamDS(ds, -1,true) || PlayUtils.canPlayDS(ds, -1,true)) {
				TorrentListViewsUtils.playOrStreamDataSource(ds, "column", true, false);
			}
		}
	}

	// @see com.biglybt.pif.ui.tables.TableCellToolTipListener#cellHover(com.biglybt.pif.ui.tables.TableCell)
	@Override
	public void cellHover(TableCell cell) {
		Object ds = cell.getDataSource();
		if (noIconForYou(ds, cell)) {
			cell.setToolTip(null);
			return;
		}
		if (PlayUtils.canStreamDS(ds, -1,false) || PlayUtils.canPlayDS(ds, -1,false)) {
			cell.setToolTip(null);
			return;
		}
		String id = "TableColumn.TorrentStream.tooltip.disabled";
		if ((ds instanceof DownloadManager) && ((DownloadManager)ds).getNumFileInfos() > 1) {
			id = "TableColumn.TorrentStream.tooltip.expand";
		}

		cell.setToolTip(MessageText.getString(id));
	}

	// @see com.biglybt.pif.ui.tables.TableCellToolTipListener#cellHoverComplete(com.biglybt.pif.ui.tables.TableCell)
	@Override
	public void cellHoverComplete(TableCell cell) {
		cell.setToolTip(null);
	}
}
