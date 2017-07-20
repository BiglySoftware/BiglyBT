/*
 * Created on Jan 5, 2009
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

package com.biglybt.ui.common.table.impl;

import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.ui.common.table.TableColumnCore;

/**
 * @author TuxPaper
 * @created Jan 5, 2009
 *
 */
public class TableColumnInfoImpl
	implements TableColumnInfo
{
	String[] categories;

	byte proficiency = TableColumnInfo.PROFICIENCY_INTERMEDIATE;

	private final TableColumnCore column;

	/**
	 * @param column
	 */
	public TableColumnInfoImpl(TableColumnCore column) {
		this.column = column;
	}

	@Override
	public TableColumnCore getColumn() {
		return column;
	}

	// @see com.biglybt.ui.swt.views.table.utils.TableColumnInfo#getCategories()
	@Override
	public String[] getCategories() {
		return categories;
	}

	// @see com.biglybt.ui.swt.views.table.utils.TableColumnInfo#setCategories(java.lang.String[])
	@Override
	public void addCategories(String[] categories) {
		if (categories == null || categories.length == 0) {
			return;
		}
		int pos;
		String[] newCategories;
		if (this.categories == null) {
			newCategories = new String[categories.length];
			pos = 0;
		} else {
			newCategories = new String[categories.length + this.categories.length];
			pos = this.categories.length;
			System.arraycopy(this.categories, 0, newCategories, 0, pos);
		}
		System.arraycopy(categories, pos, newCategories, 0, categories.length);
		this.categories = newCategories;
	}

	// @see com.biglybt.ui.swt.views.table.utils.TableColumnInfo#getProficiency()
	@Override
	public byte getProficiency() {
		return proficiency;
	}

	// @see com.biglybt.ui.swt.views.table.utils.TableColumnInfo#setProficiency(int)
	@Override
	public void setProficiency(byte proficiency) {
		this.proficiency = proficiency;
	}
}
