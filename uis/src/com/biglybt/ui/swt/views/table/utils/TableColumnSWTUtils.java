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
 *
 */

package com.biglybt.ui.swt.views.table.utils;

import org.eclipse.swt.SWT;

import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.ui.common.table.TableStructureEventDispatcher;
import com.biglybt.ui.common.table.TableStructureModificationListener;
import com.biglybt.ui.common.table.impl.TableColumnManager;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.ui.swt.views.table.TableViewSWT;

/**
 * @author TuxPaper
 * @created Dec 30, 2007
 *
 */
public class TableColumnSWTUtils
{
	public static int convertColumnAlignmentToSWT(int align) {
		int swt = 0;
		int hAlign = align & 3;
		if (hAlign == TableColumn.ALIGN_CENTER) {
			swt = SWT.CENTER;
		} else if (hAlign == TableColumn.ALIGN_LEAD) {
			swt = SWT.LEAD;
		} else if (hAlign == TableColumn.ALIGN_TRAIL) {
			swt = SWT.TRAIL;
		} else {
			swt = SWT.LEAD;
		}
		int vAlign = align & ~3;
		if (vAlign == TableColumn.ALIGN_TOP) {
			swt |= SWT.TOP;
		} else if (vAlign == TableColumn.ALIGN_BOTTOM) {
			swt |= SWT.BOTTOM;
		}
		return swt;
	}

	/*
    private static int convertSWTAlignmentToColumn(int align) {
		if ((align & SWT.LEAD) != 0) {
			return TableColumn.ALIGN_LEAD;
		} else if ((align & SWT.CENTER) != 0) {
			return TableColumn.ALIGN_CENTER;
		} else if ((align & SWT.RIGHT) != 0) {
			return TableColumn.ALIGN_TRAIL;
		}
		return TableColumn.ALIGN_LEAD;
	}
    */
	
    public static void
    changeColumnVisiblity(
    	TableViewSWT<?>		tv,
    	TableColumn			tc,
    	boolean				b )
    {
    	tc.setVisible( b );
		TableColumnManager tcm = TableColumnManager.getInstance();
		String tableID = tv.getTableID();
		tcm.saveTableColumns(tv.getDataSourceType(), tableID);
		if (tv instanceof TableStructureModificationListener) {
			((TableStructureModificationListener<?>) tv).tableStructureChanged(
					true, null);
		}
		TableStructureEventDispatcher.getInstance(tableID).tableStructureChanged(true, null);
    }
    
    public static void
    setSizeAlpha(
    	TableCell		cell,
    	long			size )
    {
		
		if (Utils.getUserMode() > 0 && (cell instanceof TableCellSWT)) {
			if (size >= 0x40000000l) {
				((TableCellSWT) cell).setTextAlpha(200 | 0x100);
			} else if (size < 0x100000 && size >= 0 ) {
				((TableCellSWT) cell).setTextAlpha(180);
			} else {
				((TableCellSWT) cell).setTextAlpha(255);
			}
		}
    }
}
