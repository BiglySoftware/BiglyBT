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

package com.biglybt.ui.common.table;

/**
 * @author TuxPaper
 * @created Feb 6, 2007
 *
 */
public abstract class TableSelectionAdapter
	implements TableSelectionListener
{
	// @see TableSelectionListener#defaultSelected(TableRowCore[])
	@Override
	public void defaultSelected(TableRowCore[] rows, int stateMask) {
	}

	// @see TableSelectionListener#deselected(TableRowCore)
	@Override
	public void deselected(TableRowCore[] rows) {
	}

	// @see TableSelectionListener#focusChanged(TableRowCore)
	@Override
	public void focusChanged(TableRowCore focus) {
	}

	// @see TableSelectionListener#selected(TableRowCore)
	@Override
	public void selected(TableRowCore[] rows) {
	}

	// @see TableSelectionListener#mouseEnter(TableRowCore)
	@Override
	public void mouseEnter(TableRowCore row) {
	}

	// @see TableSelectionListener#mouseExit(TableRowCore)
	@Override
	public void mouseExit(TableRowCore row) {
	}
}
