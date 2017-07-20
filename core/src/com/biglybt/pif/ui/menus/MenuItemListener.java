/*
 * Created on 19-Apr-2004
 * Created by Paul Gardner
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */

package com.biglybt.pif.ui.menus;

import com.biglybt.pif.download.Download;

/** A listener that is triggered when the user selects a menu item
 *
 * @author parg (Original ContextMenuItemListener)
 * @author tuxpaper (generic-izing and comments)
 */
public interface
MenuItemListener
{
  /** Menu item has been selected by the user.
   *
   * Note - this method will be called when a "deselection" takes place, like
   * if the MenuItem has <tt>STYLE_CHECK</tt> or <tt>STYLE_RADIO</tt> (where a
   * previously selected item has been deselected because another item has been
   * selected instead).
   *
   * Therefore, you should check the state of the MenuItem, rather than assuming
   * that it has been "activated".
   *
   * @param menu Which menu item was selected
   * @param target What this menu item applies to.  For the default
   *               implementation, target is null.  Implementing classes
   *               may provide an object related to the menu selection.
   *               <P>
	 * For table context menu items this will be TableRow[] of selected rows <BR>
	 * For {@link MenuManager#MENU_DOWNLOAD_CONTEXT} this will be an array of {@link Download} <BR>
   */
	public void
	selected(
		MenuItem			menu,
		Object 				target );
}
