/*
 * Created on Feb 2, 2008
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

package com.biglybt.ui.common.table;


/**
 * If cell.setSortValue is set to an object of this type, the column will
 * not be set to "Live".  Live columns get invalidated every single refresh.
 * If you use this object, it's wise to {@link TableColumnCore#setLastSortValueChange(long)}
 * when you know you've changed the sort value.
 *
 *
 * @author TuxPaper
 * @created Feb 2, 2008
 *
 */
public interface TableColumnSortObject extends Comparable
{
}
