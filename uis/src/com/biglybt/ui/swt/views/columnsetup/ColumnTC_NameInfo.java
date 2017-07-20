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

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.*;

import com.biglybt.core.internat.MessageText;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.shells.GCStringPrinter;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.ui.swt.views.table.TableCellSWTPaintListener;

import com.biglybt.ui.common.table.TableCellCore;
import com.biglybt.ui.common.table.TableColumnCore;
import com.biglybt.ui.common.table.TableView;
import com.biglybt.ui.common.table.impl.TableColumnManager;
import com.biglybt.ui.swt.utils.ColorCache;

/**
 * @author TuxPaper
 * @created Jan 3, 2009
 *
 */
public class ColumnTC_NameInfo
	extends CoreTableColumnSWT
	implements TableCellRefreshListener, TableCellSWTPaintListener,
	TableCellMouseMoveListener, TableCellToolTipListener
{
	public static final String COLUMN_ID = "TableColumnNameInfo";

	public static Font fontHeader = null;

	private static String[] profText = { "beginner", "intermediate", "advanced" };

	/**
	 * @param name
	 * @param tableID
	 */
	public ColumnTC_NameInfo(String tableID) {
		super(COLUMN_ID, tableID);
		initialize(ALIGN_LEAD | ALIGN_TOP, POSITION_INVISIBLE, 415,
				INTERVAL_INVALID_ONLY);
		setType(TYPE_GRAPHIC);
		setDefaultSortAscending( true );
	}

	// @see com.biglybt.pif.ui.tables.TableCellRefreshListener#refresh(com.biglybt.pif.ui.tables.TableCell)
	@Override
	public void refresh(TableCell cell) {
		TableColumnCore column = (TableColumnCore) cell.getDataSource();
		String key = column.getTitleLanguageKey();
		cell.setSortValue(MessageText.getString(key, column.getName()));
	}

	// @see com.biglybt.ui.swt.views.table.TableCellSWTPaintListener#cellPaint(org.eclipse.swt.graphics.GC, com.biglybt.ui.swt.views.table.TableCellSWT)
	@Override
	public void cellPaint(GC gc, TableCellSWT cell) {
		TableColumnCore column = (TableColumnCore) cell.getDataSource();
		String raw_key 		= column.getTitleLanguageKey( false );
		String current_key 	= column.getTitleLanguageKey( true );
		Rectangle bounds = cell.getBounds();
		if (bounds == null || bounds.isEmpty()) {
			return;
		}

		Font fontDefault = gc.getFont();
		if (fontHeader == null) {
			FontData[] fontData = gc.getFont().getFontData();
			fontData[0].setStyle(SWT.BOLD);
			fontData[0].setHeight(fontData[0].getHeight() + 1);
			fontHeader = new Font(gc.getDevice(), fontData);
		}

		gc.setFont(fontHeader);

		bounds.y += 3;
		bounds.x += 7;
		bounds.width -= 14;
		String name = MessageText.getString(raw_key, column.getName());

		if ( !raw_key.equals( current_key )){
			String rename = MessageText.getString(current_key, "");
			if ( rename.length() > 0 ){
				name += " (->" + rename + ")";
			}
		}
		GCStringPrinter sp = new GCStringPrinter(gc, name, bounds, GCStringPrinter.FLAG_SKIPCLIP, SWT.TOP);
		sp.printString();

		Point titleSize = sp.getCalculatedSize();

		gc.setFont(fontDefault);
		String info = MessageText.getString(raw_key + ".info", "");
		Rectangle infoBounds = new Rectangle(bounds.x + 10, bounds.y + titleSize.y
				+ 5, bounds.width - 15, bounds.height - 20);
		GCStringPrinter.printString(gc, info, infoBounds, true, false);

		TableColumnInfo columnInfo = (TableColumnInfo) cell.getTableRow().getData(
				"columninfo");
		if (columnInfo == null) {
			final TableColumnManager tcm = TableColumnManager.getInstance();
			columnInfo = tcm.getColumnInfo(column.getForDataSourceType(),
					column.getTableID(), column.getName());
			cell.getTableRowCore().setData("columninfo", columnInfo);
		}
		Rectangle profBounds = new Rectangle(bounds.width - 100, bounds.y - 2, 100, 20);
		byte proficiency = columnInfo.getProficiency();
		if (proficiency > 0 && proficiency < profText.length) {
			int alpha = gc.getAlpha();
			gc.setAlpha(0xA0);
			GCStringPrinter.printString(gc,
					MessageText.getString("ConfigView.section.mode."
							+ profText[proficiency]), profBounds, true,
					false, SWT.RIGHT | SWT.TOP);
			gc.setAlpha(alpha);
		}

		Rectangle hitArea;
		TableView<?> tv = ((TableCellCore) cell).getTableRowCore().getView();
		TableColumnSetupWindow tvs = (TableColumnSetupWindow) tv.getParentDataSource();
		if (tvs.isColumnAdded(column)) {
			hitArea = Utils.EMPTY_RECT;
		} else {
			int x = bounds.x + titleSize.x + 15;
			int y = bounds.y - 1;
			int h = 15;

			String textAdd = MessageText.getString("Button.add");
			GCStringPrinter sp2 = new GCStringPrinter(gc, textAdd,
					new Rectangle(x, y, 500, h), true, false, SWT.CENTER);
			sp2.calculateMetrics();
			int w = sp2.getCalculatedSize().x + 12;

			gc.setAdvanced(true);
			gc.setAntialias(SWT.ON);
			gc.setBackground(ColorCache.getColor(gc.getDevice(), 255, 255, 255));
			gc.fillRoundRectangle(x, y, w, h, 15, h);
			gc.setBackground(ColorCache.getColor(gc.getDevice(), 215, 215, 215));
			gc.fillRoundRectangle(x + 2, y + 2, w, h, 15, h);
			gc.setForeground(ColorCache.getColor(gc.getDevice(), 145, 145, 145));
			gc.drawRoundRectangle(x, y, w, h, 15, h);

			gc.setForeground(ColorCache.getColor(gc.getDevice(), 50, 50, 50));
			hitArea = new Rectangle(x, y, w + 2, h);
			sp2.printString(gc, hitArea, SWT.CENTER);
			bounds = cell.getBounds();
			hitArea.x -= bounds.x;
			hitArea.y -= bounds.y;
		}
		cell.getTableRowCore().setData("AddHitArea", hitArea);
	}

	// @see com.biglybt.pif.ui.tables.TableCellMouseListener#cellMouseTrigger(com.biglybt.pif.ui.tables.TableCellMouseEvent)
	@Override
	public void cellMouseTrigger(TableCellMouseEvent event) {
		if (event.button == 1
				&& event.eventType == TableRowMouseEvent.EVENT_MOUSEUP
				&& (event.cell instanceof TableCellCore)) {
			Object data = event.cell.getTableRow().getData("AddHitArea");
			if (data instanceof Rectangle) {
				Rectangle hitArea = (Rectangle) data;
				if (hitArea.contains(event.x, event.y)) {
					TableView<?> tv = ((TableCellCore) event.cell).getTableRowCore().getView();
					TableColumnSetupWindow tvs = (TableColumnSetupWindow) tv.getParentDataSource();
					Object dataSource = event.cell.getDataSource();
					if (dataSource instanceof TableColumnCore) {
						TableColumnCore column = (TableColumnCore) dataSource;
						tvs.chooseColumn(column);
					}
				}
			}
		} else if (event.eventType == TableRowMouseEvent.EVENT_MOUSEMOVE) {
			Object data = event.cell.getTableRow().getData("AddHitArea");
			if (data instanceof Rectangle) {
				Rectangle hitArea = (Rectangle) data;
				if (hitArea.contains(event.x, event.y)) {
					((TableCellSWT)event.cell).setCursorID(SWT.CURSOR_HAND);
					return;
				}
			}
			((TableCellSWT)event.cell).setCursorID(SWT.CURSOR_ARROW);
		}
	}

	// @see com.biglybt.pif.ui.tables.TableCellToolTipListener#cellHover(com.biglybt.pif.ui.tables.TableCell)
	@Override
	public void cellHover(TableCell cell) {
	}

	// @see com.biglybt.pif.ui.tables.TableCellToolTipListener#cellHoverComplete(com.biglybt.pif.ui.tables.TableCell)
	@Override
	public void cellHoverComplete(TableCell cell) {
	}
}
