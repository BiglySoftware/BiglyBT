/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.ui.common.table.impl;

import java.util.Comparator;

import com.biglybt.ui.common.table.TableRowCore;

public class TableRowCoreSorter
	implements Comparator<TableRowCore>
{
	@Override
	@SuppressWarnings("null")
	public int compare(TableRowCore o1, TableRowCore o2) {
		TableRowCore parent1 = o1.getParentRowCore();
		TableRowCore parent2 = o2.getParentRowCore();
		boolean hasParent1 = parent1 != null;
		boolean hasParent2 = parent2 != null;

		if (parent1 == parent2 || (!hasParent1 && !hasParent2)) {
			return o1.getIndex() - o2.getIndex();
		}
		if (hasParent1 && hasParent2) {
			return parent1.getIndex() - parent2.getIndex();
		}
		if (hasParent1) {
			if (parent1 == o2) {
				return 1;
			}
			return parent1.getIndex() - o2.getIndex();
		}
		if (o1 == parent2) {
			return 0;
		}
		return o1.getIndex() - parent2.getIndex();
	}
}
