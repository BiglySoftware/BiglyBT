/*
 * Created on 25 January 2007
 * Created by Allan Crooks
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
 */
package com.biglybt.pif.ui.menus;

import java.util.List;

import com.biglybt.pif.disk.DiskManagerFileInfo;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.UIManager;

/**
 * Helper class to allow plugins to register their own menus. If you want to
 * add menus to be available in tables, you should use the <tt>TableManager</tt>
 * class.
 * <P>
 * To get an instance of MenuManager, use {@link UIManager#getMenuManager()}
 *
 * @author amc1
 * @see com.biglybt.pif.local.ui.tables.TableManager TableManager
 * @since 3.0.0.7
 */
public interface MenuManager {

	/**
	 * The menu used for tables - this value cannot be used directly to create
	 * menus used by tables, you need to use the <tt>TableManager</tt> class
	 * to create such menu items.
	 *
	 * @see com.biglybt.pif.local.ui.tables.TableManager TableManager
	 */
	public static final String MENU_TABLE = "table";

	/**
	 * The menu used for the system tray icon.
	 */
	public static final String MENU_SYSTRAY = "systray";

	/**
	 * The menu used on download bars.
	 */
	public static final String MENU_DOWNLOAD_BAR = "downloadbar";

	/**
	 * The "Plugins" menu on the menu bar.
	 */
	public static final String MENU_MENUBAR = "mainmenu";

	/**
	 * The "Tools" menu on the menu bar.
	 */
	public static final String MENU_MENUBAR_TOOLS = "tools";

	/**
	 * The "transfers bar".
	 *
	 * @since 3.0.1.3
	 */
	public static final String MENU_TRANSFERSBAR = "transfersbar";

	/**
	 * The "Torrents" menu.
	 *
	 * @since 3.0.2
	 */
	public static final String MENU_TORRENT_MENU = "torrentmenu";

	/**
	 * All menus which are {@link Download} specific, such as download bars, the
	 * Torrent menu, torrent tables etc.
	 * <P>
	 * data or target parameter in listener triggers will be an array of
	 * {@link Download}
	 *
	 * @since 3.0.2
	 */
	public static final String MENU_DOWNLOAD_CONTEXT = "download_context";

	/**
	 * All menus which are {@link DiskManagerFileInfo} specific, such as the
	 * Files tab in Torrent Details view, or the file row within the library view
	 * <P>
	 * data or target parameter in listener triggers will be an array of
	 * {@link DiskManagerFileInfo}
	 *
	 * @since 5.6
	 */
	public static final String MENU_FILE_CONTEXT = "file_context";


	/**
	 * @since 5.6
	 */
	public static final String MENU_TAG_CONTEXT = "tag_content";

	public static final String MENU_SUBSCRIPTION_RESULT_CONTEXT = "subscription_result_context";

	
	/**
	 * Creates a menu item for the appropriate menu.
	 * <P>
	 * On plugin unload, use {@link MenuItem#remove()} or {@link MenuItem#removeAllChildItems()}
	 *
	 * @param menuID The <tt>MENU_</tt> identifier as defined above.
	 * @param resource_key ID of the menu, which is also used to retrieve the
	 *                     textual name from the plugin language file.
	 * @return The newly created menu item.
	 */
    public MenuItem addMenuItem(String menuID, String resource_key);

    /**
     * Creates a menu item in a particular context. {@link MenuContext}
     * instances can be retrieved from some plugin objects that support
     * menu items to be added to it.
     * <P>
     * Example: When adding menus to {@link UISWTStatusEntry}, call
     * {@link UISWTStatusEntry#getMenuContext()} to get the MenuContext, and
     * then pass it into this function.
	 * <P>
	 * On plugin unload, use {@link MenuItem#remove()} or {@link MenuItem#removeAllChildItems()}
     *
     * @param context The menu context object which represents the place to
     *                add a menu item.
	 * @param resource_key ID of the menu, which is also used to retrieve the
	 *                     textual name from the plugin language file.
	 * @return The newly created menu item.
	 * @since 3.0.5.3
     */
    public MenuItem addMenuItem(MenuContext context, String resource_key);

    /**
     * Creates a menu item as a sub-item of the given menu item.
	 * <P>
	 * On plugin unload, use {@link MenuItem#remove()} or {@link MenuItem#removeAllChildItems()}
     *
     * @param parent The MenuItem to add this new item to. The parent MenuItem
     *               must have its style attribute to be set to "menu".
     * @param resource_key ID of the menu, which is also used to retrieve the
	 *                     textual name from the plugin language file.
	 * @return The newly created menu item.
     */
    public MenuItem addMenuItem(MenuItem parent, String resource_key);
    
    public List<MenuItem>
    getMenuItems(
    	String		menu_id,
    	String		resource_key );
}
