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

import com.biglybt.core.internat.MessageText;
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
		
		if ( tableID.equals( TableColumnSetupWindow.TABLEID_ROW_DETAILS )){
			String existing = getNameOverride();
			if ( existing == null || existing.isEmpty()){
				setNameOverride(MessageText.getString( "label.value" ));
			}
		}
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

				Cell c = new Cell(cell, column, tv.getTableComposite(), sampleRow);
				
				cell.addListeners(c);
				
				cell.setData("Cell", c );
			}
		});
	}

	private static class Cell
		implements TableCellRefreshListener, TableCellSWTPaintListener,
		TableCellVisibilityListener, TableCellDisposeListener, TableCellClipboardListener
	{
		private final TableColumnCore column;
		private FakeTableCell sampleCell;

		public Cell(TableCell parentCell, TableColumnCore column, Composite c, TableRowCore sampleRow) {
			this.column = column;
			if (sampleRow == null) {
				return;
			}
			Object ds = sampleRow.getDataSource(true);

			sampleCell = new FakeTableCell(column, ds);

			Rectangle bounds = ((TableCellSWT)parentCell).getBounds();
			
			sampleCell.setControl(c, bounds, false);
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
			bounds.x += 2;
			bounds.width -=4;
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

		@Override
		public String getClipboardText(TableCell cell){
			FakeTableCell sampleCell = this.sampleCell;

			if (sampleCell == null) {
				return( null );
			}
			return( sampleCell.getClipboardText());
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
		TableRowCore row1, 
		TableRowCore row2)
	{		
		final String IS_GRAPHIC = "GRAPHIC";
		
		TableCell cell1 = row1.getTableCell( this );
		TableCell cell2 = row2.getTableCell( this );
				
		Cell c1 = (Cell)cell1.getData( "Cell" );
		Cell c2 = (Cell)cell2.getData( "Cell" );
		
		String str1 = null;
		String str2 = null;
		
		if ( c1 != null ){

			FakeTableCell fc1 = c1.sampleCell;
					
			str1 = fc1.getText();
			
			if ( str1 == null ){
				
				str1 = fc1.getTextEquivalent();
			}
			
			if ( str1 == null ){
				
				str1 = fc1.getClipboardText();
			}
			
			if ( str1 == null || str1.isEmpty()){
				
				if ( fc1.getIcon() != null || fc1.getGraphic() != null ){
					
					str1 = IS_GRAPHIC;
				}else{
					
					str1 = "";
				}
			}
			
			//System.out.println( fc1.getTableColumn().getName() + " -> " + str1 );
		}else{
			
			//System.out.println( "c1 is null" );
		}
		
		if ( c2 != null ){

			FakeTableCell fc2 = c2.sampleCell;
			
			str2 = fc2.getText();
			
			if ( str2 == null ){
				
				str2 = fc2.getTextEquivalent();
			}
			
			if ( str2 == null || str2.isEmpty()){
				
				str2 = fc2.getClipboardText();
			}
			
			if ( str2 == null || str2.isEmpty()){
				
				if ( fc2.getIcon() != null || fc2.getGraphic() != null ){
					
					str2 = IS_GRAPHIC;
				}else{
					
					str2 = "";
				}
			}
			
			//System.out.println( fc2.getTableColumn().getName() + " -> " + str2 );
		}else{
			
			//System.out.println( "c2 is null" );
		}

		if ( str1 == null ){
			
			str1 = cell1.getClipboardText();
		}
		
		if ( str2 == null ){
			
			str2 = cell2.getClipboardText();
		}
		
		int res = 0;
		
		if ( str1 == IS_GRAPHIC && str2 == IS_GRAPHIC ){
			
		}else if ( str1 == IS_GRAPHIC ){
			
			res = 1;
			
		}else if ( str2 == IS_GRAPHIC ){
			
			res = -1;
		}else{
			
			res = str1.compareTo(str2 );
					
		}
		return((isSortAscending()?1:-1)*res );
	}
}
