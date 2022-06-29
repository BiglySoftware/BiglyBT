/*
 * Created on Jan 3, 2009
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

package com.biglybt.ui.swt.views.columnsetup;

import com.biglybt.ui.common.table.TableCellCore;
import com.biglybt.ui.common.table.TableColumnCore;
import com.biglybt.ui.common.table.TableRowCore;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;

import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.ui.swt.views.table.TableCellSWTPaintListener;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.ui.swt.views.table.impl.FakeTableCell;

/**
 * @author TuxPaper
 * @created Jan 3, 2009
 *
 */
public class ColumnTC_Sample
	extends CoreTableColumnSWT
	implements TableCellAddedListener
{
	public static final String COLUMN_ID = "TableColumnSample";

	public ColumnTC_Sample(String tableID) {
		super(COLUMN_ID, tableID);
		setPosition(POSITION_INVISIBLE);
		setRefreshInterval(INTERVAL_LIVE);
		setWidth(120);
	}

	// @see com.biglybt.pif.ui.tables.TableCellAddedListener#cellAdded(com.biglybt.pif.ui.tables.TableCell)
	@Override
	public void cellAdded(final TableCell cell) {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (cell.isDisposed()) {
					return;
				}
				TableColumnCore column = (TableColumnCore) cell.getDataSource();
				TableViewSWT<?> tv = (TableViewSWT<?>) ((TableCellCore) cell).getTableRowCore().getView();
				TableColumnSetupWindow tvs = (TableColumnSetupWindow) tv.getParentDataSource();
				TableRowCore sampleRow = (TableRowCore) tvs.getSampleRow();

				cell.addListeners(new Cell(cell, column, tv.getTableComposite(), sampleRow));
			}
		});
	}

	private static class Cell
		implements TableCellRefreshListener, TableCellSWTPaintListener,
		TableCellVisibilityListener, TableCellDisposeListener
	{
		private final TableColumnCore column;
		private FakeTableCell sampleCell;

		public Cell(TableCell parentCell, TableColumnCore column, Composite c, TableRowCore sampleRow) {
			this.column = column;
			if (sampleRow == null) {
				return;
			}
			Object ds = sampleRow.getDataSource(true);
			Object pds = sampleRow.getDataSource(false);
			if (column.handlesDataSourceType(pds.getClass())) {
  			sampleCell = new FakeTableCell(column, ds);

  			Rectangle bounds = ((TableCellSWT)parentCell).getBounds();
  			sampleCell.setControl(c, bounds, false);
			}
		}

		@Override
		public void dispose(TableCell cell) {
			sampleCell = null;
		}

		// @see com.biglybt.ui.swt.views.table.TableCellSWTPaintListener#cellPaint(org.eclipse.swt.graphics.GC, com.biglybt.ui.swt.views.table.TableCellSWT)
		@Override
		public void cellPaint(GC gc, TableCellSWT cell) {

			FakeTableCell sampleCell = this.sampleCell;

			if (sampleCell == null) {
				return;
			}
			Rectangle bounds = cell.getBounds();
			sampleCell.setCellArea(bounds);
			try {
				sampleCell.refresh();
				sampleCell.doPaint(gc);
			} catch (Throwable e) {
				Debug.out(e);
			}
		}

		// @see com.biglybt.pif.ui.tables.TableCellVisibilityListener#cellVisibilityChanged(com.biglybt.pif.ui.tables.TableCell, int)
		@Override
		public void cellVisibilityChanged(TableCell cell, int visibility) {

			FakeTableCell sampleCell = this.sampleCell;

			if (sampleCell == null) {
				return;
			}
			try {
				column.invokeCellVisibilityListeners(sampleCell, visibility);
			} catch (Throwable e) {
				Debug.out(e);
			}
		}

		// @see com.biglybt.pif.ui.tables.TableCellRefreshListener#refresh(com.biglybt.pif.ui.tables.TableCell)
		@Override
		public void refresh(TableCell cell) {

			FakeTableCell sampleCell = this.sampleCell;

			if (sampleCell == null) {
				return;
			}
			if (!cell.isShown()) {
				return;
			}
			sampleCell.refresh(true, true, true);
			cell.setSortValue(sampleCell.getSortValue());
			cell.invalidate();
			if (cell instanceof TableCellSWT) {
				((TableCellSWT) cell).redraw();
			}
			//cell.setText(sampleCell.getText());
		}

	}
	
	@Override
	public int 
	compare(
		TableRowCore arg0, 
		TableRowCore arg1)
	{
			// can't sensibly compare the random cell sample values
		
		return( arg0.getIndex() - arg1.getIndex());
	}
}
