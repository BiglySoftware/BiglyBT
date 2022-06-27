/*
 * Azureus - a Java Bittorrent client
 * 2004/May/16 TuxPaper
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

package com.biglybt.pif.ui.tables;

import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.ui.common.table.TableView;

/** Represents on context menu item for a table.
 */
public interface TableContextMenuItem
       extends MenuItem {

   	/**
   	 * Adds a selection listener for this menu item.
   	 *
   	 * The {@link MenuItemListener#selected(MenuItem, Object)} method invoked
   	 * with the <tt>target</tt> being a {@link TableRow} instance. This will be one of
   	 * the items which was selected - this method will be invoked multiple
   	 * times with each item that was selected - if you want the entire selection
   	 * of items in one go, you should register the listener via
   	 * {@link #addMultiListener(MenuItemListener)}.
   	 *
   	 * @param l listener to be notified when user has selected the menu item.
   	 */
   	@Override
    public void	addListener(MenuItemListener l);

   	/**
   	 * Adds a selection listener for this menu item.
   	 *
   	 * This differs from {@link #addListener(MenuItemListener)}, in that the
   	 * <tt>target</tt> object which will be passed to the listener will be an
   	 * array of {@link TableRow} objects, rather than just a single object.
   	 *
   	 * @param l listener to be notified when user has selected the menu item.
   	 * @since 2.5.0.2
   	 */
   	@Override
    public void addMultiListener(MenuItemListener l);

  /**
   * Retrieve the Table ID that the menu item belongs to
   * @return {@link TableManager}.TABLE_ constant
   */
  public String getTableID();
  
  public TableView<?>
  getTable();
}