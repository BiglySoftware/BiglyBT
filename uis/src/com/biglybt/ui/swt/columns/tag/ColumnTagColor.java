/*
 * Copyright (C) 2013 Azureus Software, Inc. All Rights Reserved.
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
 *
 */

package com.biglybt.ui.swt.columns.tag;

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Rectangle;

import com.biglybt.core.tag.Tag;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.ui.swt.views.table.TableCellSWTPaintListener;

public class ColumnTagColor
	implements TableCellRefreshListener, TableCellSWTPaintListener, TableColumnExtraInfoListener
{
	public static String COLUMN_ID = "tag.color";

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			TableColumn.CAT_ESSENTIAL,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	/** Default Constructor */
	public ColumnTagColor(TableColumn column) {
		column.setWidth(30);
		column.addListeners(this);
	}

	@Override
	public void refresh(TableCell cell) {
		Tag tag = (Tag) cell.getDataSource();
		if (tag == null) {
			return;
		}
		int[] color;
		color = tag.getColor();

		if (color == null || color.length < 3) {
			return;
		}

		int sortVal = color[0] + color[1] << 8 + color[2] << 16;

		if (!cell.setSortValue(sortVal) && cell.isValid()) {
			return;
		}

		if (!cell.isShown()) {
			return;
		}

		cell.setForeground(color);
	}

	// @see com.biglybt.ui.swt.views.table.TableCellSWTPaintListener#cellPaint(org.eclipse.swt.graphics.GC, com.biglybt.ui.swt.views.table.TableCellSWT)
	@Override
	public void cellPaint(GC gc, TableCellSWT cell) {
		Rectangle bounds = cell.getBounds();
		Color foregroundSWT = cell.getForegroundSWT();
		if (foregroundSWT != null) {
			gc.setBackground(foregroundSWT);
			bounds.x+=1;
			bounds.y+=1;
			bounds.width-=1;
			bounds.height-=1;
			gc.fillRectangle(bounds);
		}
	}
}
