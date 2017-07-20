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

import com.biglybt.pif.ui.tables.TableColumn;

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
}
