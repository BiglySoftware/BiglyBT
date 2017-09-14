/*
 * Created on Sep 19, 2008
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

package com.biglybt.pif.ui.tables;

/**
 * @author TuxPaper
 * @created Sep 19, 2008
 *
 */
public interface TableColumnCreationListener
{
	/**
	 * Triggered when a new column is created.  Use the column parameter to
	 * get information about the new column, such as which table created it
	 *
	 * @param column
	 *
	 * @since 1.0.0.0
	 */
	public void tableColumnCreated(TableColumn column);
}
