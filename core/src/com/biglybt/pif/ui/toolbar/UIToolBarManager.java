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

package com.biglybt.pif.ui.toolbar;

public interface UIToolBarManager
{
	public final static String GROUP_BIG = "big";

	public final static String GROUP_MAIN = "main";

	/**
	 * Create a new {@link UIToolBarItem}.  You will still need to add it
	 * via {@link #addToolBarItem(UIToolBarItem)}, after setting the item's
	 * properties
	 *
	 * @param id unique id
	 * @return newly created toolbar
	 */
	public UIToolBarItem createToolBarItem(String id);

	/**
	 * Adds a {@link UIToolBarItem} to the UI.  Make sure you at least set the
	 * icon before adding
	 *
	 * @param item
	 */
	public void addToolBarItem(UIToolBarItem item);

	public UIToolBarItem getToolBarItem(String id);

	public UIToolBarItem[] getAllToolBarItems();

	public void removeToolBarItem(String id);


}
