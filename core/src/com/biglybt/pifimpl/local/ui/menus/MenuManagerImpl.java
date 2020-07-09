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
package com.biglybt.pifimpl.local.ui.menus;

import java.util.List;

import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.UIRuntimeException;
import com.biglybt.pif.ui.menus.MenuContext;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuManager;
import com.biglybt.pifimpl.local.ui.UIManagerImpl;
import com.biglybt.ui.common.util.MenuItemManager;

/**
 * @author Allan Crooks
 *
 */
public class MenuManagerImpl implements MenuManager {

	private UIManagerImpl ui_manager;

	public MenuManagerImpl(UIManagerImpl _ui_manager) { ui_manager = _ui_manager; }

    @Override
    public MenuItem addMenuItem(String menuID, String resource_key) {
    	PluginInterface pi = ui_manager.getPluginInterface();
    	MenuItemImpl item = new MenuItemImpl(pi, menuID, resource_key);
	    MenuItemManager.getInstance().addMenuItem(item);
    	return item;
    }

    @Override
    public MenuItem addMenuItem(MenuContext context, String resource_key) {
    	MenuContextImpl context_impl = (MenuContextImpl)context;
    	MenuItemImpl result = (MenuItemImpl)addMenuItem(context_impl.context, resource_key);
    	result.setContext(context_impl);
    	context_impl.dirty();
    	return result;
    }

	@Override
	public MenuItem addMenuItem(MenuItem parent, String resource_key) {

		if (!(parent instanceof MenuItemImpl)) {
			throw new UIRuntimeException(
					"parent must have been created by addMenuItem");
		}

		if (parent.getStyle() != MenuItemImpl.STYLE_MENU) {
			throw new UIRuntimeException(
					"parent menu item must have the menu style associated");
		}

		return new MenuItemImpl((MenuItemImpl) parent, resource_key);

	}
	
	@Override
	public List<MenuItem> getMenuItems(String menu_id, String resource_key){
    	
	    return( MenuItemManager.getInstance().getMenuItems( menu_id, resource_key ));
	}
}
