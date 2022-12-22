/*
 * Created on Jan 17, 2010 2:19:53 AM
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

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Shell;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.util.AERunnable;
import com.biglybt.ui.common.table.TableCellCore;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mdi.BaseMdiEntry;
import com.biglybt.ui.swt.pifimpl.UISWTViewBuilderCore;
import com.biglybt.ui.swt.shells.GCStringPrinter;
import com.biglybt.ui.swt.shells.PopOutManager;
import com.biglybt.ui.swt.views.FilesView;
import com.biglybt.ui.swt.views.ViewManagerSWT;
import com.biglybt.ui.swt.views.skin.SkinnedDialog;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.ui.swt.views.table.TableCellSWTPaintListener;

import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.tables.*;


/**
 * @author TuxPaper
 * @created Jan 17, 2010
 *
 */
public class ColumnFileCount
	extends CoreTableColumnSWT
	implements TableCellMouseListener, TableCellSWTPaintListener,
	TableCellAddedListener
{
	public static final Class DATASOURCE_TYPE = Download.class;

	public static final String COLUMN_ID = "filecount";

	public ColumnFileCount(String sTableID) {
		super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_TRAIL, 60, sTableID);
		setRefreshInterval(INTERVAL_INVALID_ONLY);
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_CONTENT,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	// @see com.biglybt.pif.ui.tables.TableCellAddedListener#cellAdded(com.biglybt.pif.ui.tables.TableCell)
	@Override
	public void cellAdded(TableCell cell) {
		DownloadManager dm = (DownloadManager) cell.getDataSource();
		int sortVal = dm.getNumFileInfos();
		cell.setSortValue(sortVal);
	}

	@Override
	public void cellMouseTrigger(final TableCellMouseEvent event) {

		if (Utils.getUserMode() < 2) { // remove prototype for now
			return;
		}
		final DownloadManager dm = (DownloadManager) event.cell.getDataSource();

		if (event.eventType == TableRowMouseEvent.EVENT_MOUSEENTER) {
			((TableCellCore) event.cell).setCursorID(SWT.CURSOR_HAND);
		} else if (event.eventType == TableRowMouseEvent.EVENT_MOUSEENTER) {
			((TableCellCore) event.cell).setCursorID(SWT.CURSOR_ARROW);
		} else if (event.eventType == TableRowMouseEvent.EVENT_MOUSEUP
				&& event.button == 1) {
			Utils.execSWTThreadLater(0, new AERunnable() {

				@Override
				public void runSupport() {
					openFilesMiniView(dm, event.cell);
				}
			});
		}
	}

	// @see com.biglybt.ui.swt.views.table.TableCellSWTPaintListener#cellPaint(org.eclipse.swt.graphics.GC, com.biglybt.ui.swt.views.table.TableCellSWT)
	@Override
	public void cellPaint(GC gc, TableCellSWT cell) {
		DownloadManager dm = (DownloadManager) cell.getDataSource();
		if (dm == null) {
			return;
		}

		int sortVal = dm.getNumFileInfos();
		Rectangle bounds = cell.getBounds();
		Rectangle printArea = new Rectangle(bounds.x, bounds.y, bounds.width - 6,
				bounds.height);
		GCStringPrinter.printString(gc, "" + sortVal, printArea, true, true,
				SWT.RIGHT);
	}

	private void openFilesMiniView(DownloadManager dm, TableCell cell) {
		UISWTViewBuilderCore builder = ViewManagerSWT.getInstance().getBuilder(
				Download.class, FilesView.MSGID_PREFIX);
		if (builder == null) {
			return;
		}
		SkinnedDialog skinnedDialog = PopOutManager.buildSkinnedDialog("FilesView",	dm, builder);
		
		if (skinnedDialog == null) {
			return;
		}

		skinnedDialog.setTitle(dm.getDisplayName());

		Shell shell = skinnedDialog.getShell();

		Rectangle bounds = ((TableCellSWT) cell).getBoundsOnDisplay();
		bounds.y += bounds.height;
		bounds.width = 630;
		bounds.height = (16 * dm.getNumFileInfos()) + 60;
		Rectangle realBounds = shell.computeTrim(0, 0, bounds.width, bounds.height);
		realBounds.width -= realBounds.x;
		realBounds.height -= realBounds.y;
		realBounds.x = bounds.x;
		realBounds.y = bounds.y;
		if (bounds.height > 500) {
			bounds.height = 500;
		}
		shell.setBounds(realBounds);
		shell.setAlpha(230);

		Utils.verifyShellRect(shell, true);

		skinnedDialog.openUnadjusted();
	}
}
