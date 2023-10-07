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

import com.biglybt.pif.ui.Graphic;

/**
 * Menu item access for the UI.
 *
 * @see MenuManager#addMenuItem(String, String)
 * @see MenuManager#addMenuItem(MenuItem, String)
 * @see MenuManager#addMenuItem(MenuContext, String)
 *
 * @author parg (Original ContextMenuItem code)
 * @author TuxPaper (Generic-izing, commenting)
 */
public interface MenuItem
{
		/**
		 * normal selection menu, no Data value required
		 */
	public static final int STYLE_PUSH				= 1;

		/**
		 * check box style menu item - data must be of type Boolean
		 */

	public static final int STYLE_CHECK				= 2;

		/**
		 * radio style - data must be Boolean
		 */

	public static final int STYLE_RADIO				= 3;

		/**
		 * separator line
		 */

	public static final int STYLE_SEPARATOR			= 4;

	   /**
	    * menu containing submenu items
	    */
	public static final int STYLE_MENU              = 5;

	String HEADER_CONTROL = "Control";

	String HEADER_SOCIAL = "Social";

	String HEADER_ORGANIZE = "Organize";

	String HEADER_OTHER = "Other";

	String HEADER_CONTENT = "Content";

  /** Retrieve the resource key ("name") of this menu item
   *
   * @return resource key for this menu
   */
	public String
	getResourceKey();

		/**
		 * Get the type of the menu item
		 */

	public int
	getStyle();

		/**
		 * Set the style of the menu item (see STYLE_ constants)
		 * @param style
		 */

	public void
	setStyle(
		int		style );

		/**
		 * Get the current data value associated with the menu: Boolean for CHECK style
		 * @return
		 */

	public Object
	getData();

		/**
		 * Set the current data value associated with the menu: Boolean for CHECK style
		 * @param data
		 */

	public void
	setData(
		Object	data );

		/**
		 * Whether or not this item is enabled or not
		 * @return
		 */

	public boolean
	isEnabled();

		/**
		 * Set the enabled status of the menu item
		 * @param enabled
		 */

	public void
	setEnabled(
		boolean	enabled );

		/**
		 * set the menu item's icon
		 * @param graphic
		 */

	public void
	setGraphic(
		Graphic		graphic );

		/**
		 * get the menu's graphic
		 * @return
		 */

	public Graphic
	getGraphic();

	/**
	 * Adds a listener to be notified when the menu item is about to be
	 * displayed. The "context" object provided is always going to be either
	 * be <tt>null</tt> (if there is no context) or an array of objects
	 * (such as an array of TableRows or an array of Download objects).
	 * @param listener
	 */
	public void	addFillListener(MenuItemFillListener listener);

	public void	removeFillListener(MenuItemFillListener	listener);

	/**
	 * For {@link #STYLE_MENU}, builder is called when user wants to see the
	 * submenu items.  Setting a builder will force clear all submenu items.
	 *
	 * @since 5.5.0.1
	 */
	public void setSubmenuBuilder(MenuBuilder builder);

   	/**
   	 * Adds a selection listener for this menu item.
   	 *
   	 * This differs from {@link #addListener(MenuItemListener)}, in that the
   	 * <tt>target</tt> object which will be passed to the listener will be an
   	 * array of objects, rather than just a single object.
   	 *
   	 * @param l listener to be notified when user has selected the menu item.
   	 * @since 3.0.2
   	 */
   	public void addMultiListener(MenuItemListener l);

    /**
     * Removes a selection listener from this menu item.
     *
     * You only use this method to remove a listener added via
     * {@link #addMultiListener(MenuItemListener)}.
     *
     * @param l listener to remove
     * @since 3.0.2
     */
 	public void removeMultiListener(MenuItemListener l);

	/**
	 * Adds a selection listener for this menu item.
	 * @param l listener to be notified when user has selected the menu item.
	 */
	public void	addListener(MenuItemListener l);

   /**
    * Removes a selection listener from this menu item.
    * @param l listener to remove
    */
	public void	removeListener(MenuItemListener	l);

	/**
	 * Retrieve the parent MenuItem.
	 *
	 * @return parent menu object, or null if no parent
	 */
	public MenuItem
	getParent();


	/**
	 * Get all child items currently associated with this MenuItem.
	 *
	 * @return An array of items (if this object has the menu style
	 * associated) or null otherwise.
	 */
	public MenuItem[] getItems();

	/**
	 * Returns the number of menu items
	 */
	int getItemCount();

	/**
	 * Get the child item with the given resource key.
	 *
	 * @return The child MenuItem object which has the resource key
	 * specified, or null otherwise.
	 */
	public MenuItem getItem(String key_id);

	/**
	 * Gets the text to display for this menu item.
	 */
	public String getText();

	/**
	 * Sets the text to display for this menu item. You can also
	 * pass null to revert back to the default behaviour.
	 */
	public void setText(String text);

	/**
	 * Retrieve the menu ID that the menu item belongs to
	 * @return {@link MenuManager}.MENU_ constant.
	 *
	 * @since 3.0.0.7
	 */
	public String getMenuID();

	/**
	 * Removes the menu item.
	 *
	 * Calling this will remove the item from the menus, as well as removing all
	 * listeners and removing all child menu items (if any exist).
	 *
	 * The behaviour of this object is undefined after this method has been called.
	 * If you need to interact with this object when you are about to destroy it,
	 * you should do it before you call the <tt>remove</tt> method.
	 *
	 * @since 3.0.0.7
	 */
	public void remove();

	/**
	 * Removes all child menu items from this menu (if any exist).
	 *
	 * @since 3.0.0.7
	 */
	public void removeAllChildItems();

	/**
	 * Sets whether the menu item is visible or not.
	 * @since 3.0.2.0
	 */
	public void setVisible(boolean visible);

	/**
	 * Returns whether the menu item is visible or not.
	 * @since 3.0.2.0
	 */
	public boolean isVisible();

	/**
	 * Returns whether the menu item is selected or not.
	 *
	 * This method should only be called if the menu is of type <tt>STYLE_RADIO</tt> or
	 * type <tt>STYLE_CHECK</tt> and if the menu item has already had a selected or
	 * deselected state assigned to it.
	 *
	 * @since 3.0.2.4
	 */
	public boolean isSelected();

	/**
	 * Sets which header to place the menu item under for top level fancy menu
	 */
	public void setHeaderCategory(String header);

	/**
	 * The header that a top menu item will be placed under for the fancy menu
	 */
	public String getHeaderCategory();

	int getMinUserMode();

	void setMinUserMode(int minUserMode);

	/**
	 * Auto-dispose of MenuItem when a specific UI is detached
	 * (See {@link com.biglybt.pif.ui.UIInstance#UIT_*} for types
	 *
	 * @param uiType
	 */
	void setDisposeWithUIDetach(String uiType);

	String getDisposeWithUIDetach();
}
