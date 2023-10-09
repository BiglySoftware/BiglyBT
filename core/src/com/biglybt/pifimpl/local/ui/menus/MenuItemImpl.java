/*
 * Created on 6 Feb 2007
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

import java.util.Iterator;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.CopyOnWriteList;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.Graphic;
import com.biglybt.pif.ui.menus.MenuBuilder;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemFillListener;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.ui.common.util.MenuItemManager;

/**
 * amc1: This class was largely derived from TableContextMenuImpl.
 */
public class MenuItemImpl implements MenuItem {

	private PluginInterface pi;

	private String sMenuID;

	private String sName;

	private int style = STYLE_PUSH;

	private boolean enabled = true;

	private Object data;

	private Graphic graphic;

	private CopyOnWriteList<MenuItemListener> listeners = new CopyOnWriteList<>(1);
	private CopyOnWriteList m_listeners = new CopyOnWriteList(1);

	private CopyOnWriteList fill_listeners = new CopyOnWriteList(1);

	private CopyOnWriteList children = new CopyOnWriteList();

	private MenuItemImpl parent = null;

	private String display_text = null;

	private boolean visible = true;

	private MenuContextImpl menu_context = null;

	private MenuBuilder builder;
	private String headerCategory;
	private int minUserMode;
	private String uiType;

	public MenuItemImpl(PluginInterface _pi, String menuID, String key) {
		pi = _pi;
		if (pi == null) {
			pi = PluginInitializer.getDefaultInterface();
		}
		sMenuID = menuID;
		sName = key;
	}

	public MenuItemImpl(MenuItemImpl ti, String key) {
		pi = ti.pi;
		this.parent = ti;
		this.parent.addChildMenuItem(this);
		this.sMenuID = this.parent.getMenuID();
		this.sName = key;
	}

	@Override
	public String getResourceKey() {
		return sName;
	}

	@Override
	public String getMenuID() {
		return sMenuID;
	}

	@Override
	public int getStyle() {
		return (style);
	}

	@Override
	public void setStyle(int _style) {
		if (this.style == MenuItem.STYLE_MENU && _style != MenuItem.STYLE_MENU) {
			throw new RuntimeException(
					"cannot revert menu style MenuItem object to another style");
		}
		style = _style;
	}

	@Override
	public Object getData() {
		return (data);
	}

	@Override
	public void setData(Object _data) {
		data = _data;
	}

	@Override
	public boolean isEnabled() {
		return (enabled);
	}

	@Override
	public void setEnabled(boolean _enabled) {
		enabled = _enabled;
	}

	@Override
	public void setGraphic(Graphic _graphic) {
		graphic = _graphic;
	}

	@Override
	public Graphic getGraphic() {
		return (graphic);
	}

	public void invokeMenuWillBeShownListeners(Object target) {
		for (Iterator iter = fill_listeners.iterator(); iter.hasNext();) {
			try {
				MenuItemFillListener l = (MenuItemFillListener) iter.next();
				l.menuWillBeShown(this, target);
			} catch (Throwable e) {
				Debug.printStackTrace(e);
			}
		}
	}

	@Override
	public void addFillListener(MenuItemFillListener listener) {
		fill_listeners.add(listener);
	}

	@Override
	public void removeFillListener(MenuItemFillListener listener) {
		fill_listeners.remove(listener);
	}

	// Currently used by TableView (and other places).
	public void invokeListenersMulti(Object[] rows) {
		// We invoke the multi listeners first...
		invokeListenersOnList(this.m_listeners, rows);
		if (rows == null || rows.length == 0) {
			invokeListenersSingle(null);
			return;
		}
		for (int i = 0; i < rows.length; i++) {
			invokeListenersSingle(rows[i]);
		}
	}

	  @Override
	  public void addMultiListener(MenuItemListener l) {
		  m_listeners.add(l);
	  }

	  @Override
	  public void removeMultiListener(MenuItemListener l) {
		  m_listeners.remove(l);
	  }

	private void invokeListenersSingle(Object o) {
		invokeListenersOnList(this.listeners, o);
	}

	@Override
	public void addListener(MenuItemListener l) {
		listeners.add(l);
	}

	@Override
	public void removeListener(MenuItemListener l) {
		listeners.remove(l);
	}

	@Override
	public MenuItem getParent() {
		return this.parent;
	}

	@Override
	public MenuItem[] getItems() {
		if (this.style != MenuItem.STYLE_MENU) {
			return null;
		}
		return (MenuItem[]) this.children.toArray(new MenuItem[this.children
				.size()]);
	}

	@Override
	public int getItemCount() {
		return children.size();
	}

	@Override
	public MenuItem getItem(String key) {
		if (this.style != MenuItem.STYLE_MENU) {
			return null;
		}
		java.util.Iterator itr = this.children.iterator();
		MenuItem result = null;
		while (itr.hasNext()) {
			result = (MenuItem) itr.next();
			if (key.equals(result.getResourceKey())) {
				return result;
			}
		}
		return null;
	}

	private void addChildMenuItem(MenuItem child) {
		if (this.style != MenuItem.STYLE_MENU) {
			throw new RuntimeException("cannot add to non-container MenuItem");
		}
		this.children.add(child);
	}

	@Override
	public String getText() {
		if (this.display_text == null) {
			return MessageText.getString(this.getResourceKey());
		}
		return this.display_text;
	}

	@Override
	public void setText(String text) {
		this.display_text = text;
	}

	protected void invokeListenersOnList(CopyOnWriteList<MenuItemListener> listeners_to_notify,
			Object target) {
		for (Iterator<MenuItemListener> iter = listeners_to_notify.iterator(); iter.hasNext();) {
			try {
				MenuItemListener l = iter.next();
				l.selected(this, target);
			} catch (Throwable e) {
				Debug.printStackTrace(e);
			}
		}
	}

	@Override
	public void remove() {
		removeAllChildItems();
		if (this.parent != null) {
			parent.children.remove(this);
			this.parent = null;
		}
		else {
			removeSelf();
		}
		this.data = null;
		this.graphic = null;
		this.listeners.clear();
		this.fill_listeners.clear();
		this.m_listeners.clear();

		if (this.menu_context != null) {menu_context.dirty();}
	}

	protected void removeSelf() {
		MenuItemManager.getInstance().removeMenuItem(this);
	}

	@Override
	public void removeAllChildItems() {
		// This should make this.children be empty...
		MenuItem[] children = this.getItems();
		if (children != null) {
			for (int i=0; i<children.length; i++) {children[i].remove();}
		}
	}

	@Override
	public boolean isVisible() {return visible;}
	@Override
	public void setVisible(boolean visible) {this.visible = visible;}

	@Override
	public boolean isSelected() {
		if (style != STYLE_CHECK && style != STYLE_RADIO) {
			throw new RuntimeException("Style is not STYLE_CHECK or STYLE_RADIO");
		}
		if (data == null) {
			throw new RuntimeException("Item is neither selected or deselected");
		}
		if (!(data instanceof Boolean)) {
			throw new RuntimeException("Invalid data assigned to menu item, should be boolean: " + data);
		}
		return ((Boolean)data).booleanValue();
	}

	@Override
	public void setHeaderCategory(String header) {
		headerCategory = header;
	}

	@Override
	public String getHeaderCategory() {
		return headerCategory;
	}

	@Override
	public void setMinUserMode(int minUserMode) {
		this.minUserMode = minUserMode;
	}

	@Override
	public void setDisposeWithUIDetach(String uiType) {
		this.uiType = uiType;
	}

	@Override
	public String getDisposeWithUIDetach() {
		return uiType;
	}

	@Override
	public int getMinUserMode() {
		return minUserMode;
	}

	public void setContext(MenuContextImpl context) {
		this.menu_context = context;
	}

	// @see com.biglybt.pif.ui.menus.MenuItem#setSubmenuBuilder(com.biglybt.pif.ui.menus.MenuBuilder)
	@Override
	public void setSubmenuBuilder(MenuBuilder builder) {
		this.builder = builder;
	}

	public MenuBuilder getSubmenuBuilder() {
		return builder;
	}
}
