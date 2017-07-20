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

package com.biglybt.ui.swt.pifimpl;

import java.util.*;

import com.biglybt.pif.ui.toolbar.UIToolBarItem;
import com.biglybt.pif.ui.toolbar.UIToolBarManager;

import com.biglybt.ui.common.ToolBarItem;
import com.biglybt.ui.selectedcontent.ISelectedContent;
import com.biglybt.ui.selectedcontent.SelectedContentListener;
import com.biglybt.ui.selectedcontent.SelectedContentManager;
import com.biglybt.ui.swt.Utils;

/**
 * @author TuxPaper
 * @created Feb 19, 2015
 *
 */
public class UIToolBarManagerImpl
	implements UIToolBarManagerCore
{
	private static UIToolBarManagerImpl instance;
	private final SelectedContentListener selectedContentListener;

	private Map<String, UIToolBarItem> items = new LinkedHashMap<>();

	private Map<String, List<String>> mapGroupToItemIDs = new HashMap<>();

	public List<ToolBarManagerListener> listListeners = new ArrayList<>();

	public static UIToolBarManager getInstance() {
		if (instance == null) {
			instance = new UIToolBarManagerImpl();
		}
		return instance;
	}

	public static void destroyInstance() {
		if (instance != null) {
			instance.destroy();
			instance = null;
		}
	}

	public static interface ToolBarManagerListener
	{
		public void toolbarItemRemoved(UIToolBarItem item);

		public void toolbarItemAdded(UIToolBarItem item);
	}


	public UIToolBarManagerImpl() {
		selectedContentListener = new SelectedContentListener() {
			@Override
			public void currentlySelectedContentChanged(
					ISelectedContent[] currentContent, String viewID) {
				if (Utils.isDisplayDisposed()) {
					return;
				}
				if (viewID == null) {
					ToolBarItem[] allSWTToolBarItems = getAllSWTToolBarItems();
					for (ToolBarItem item : allSWTToolBarItems) {
						item.setState(0);
					}
				}
			}
		};
		SelectedContentManager.addCurrentlySelectedContentListener(selectedContentListener);
	}

	private void destroy() {
		SelectedContentManager.removeCurrentlySelectedContentListener(selectedContentListener);
	}

	@Override
	public void addListener(ToolBarManagerListener l) {
		synchronized (listListeners) {
			listListeners.add(l);
		}
	}

	@Override
	public void removeListener(ToolBarManagerListener l) {
		synchronized (listListeners) {
			listListeners.remove(l);
		}
	}

	@Override
	public UIToolBarItem getToolBarItem(String itemID) {
		return items.get(itemID);
	}

	@Override
	public UIToolBarItem[] getAllToolBarItems() {
		return items.values().toArray(new UIToolBarItem[0]);
	}

	@Override
	public ToolBarItem[] getAllSWTToolBarItems() {
		return items.values().toArray(new ToolBarItem[0]);
	}

	// @see com.biglybt.pif.ui.toolbar.UIToolBarManager#createToolBarItem(java.lang.String)
	@Override
	public UIToolBarItem createToolBarItem(String id) {
		UIToolBarItemImpl base = new UIToolBarItemImpl(id);
		return base;
	}

	// @see com.biglybt.pif.ui.toolbar.UIToolBarManager#addToolBarItem(com.biglybt.pif.ui.toolbar.UIToolBarItem)
	@Override
	public void addToolBarItem(UIToolBarItem item) {
		addToolBarItem(item, true);
	}

	// @see com.biglybt.pif.ui.toolbar.UIToolBarManager#addToolBarItem(com.biglybt.pif.ui.toolbar.UIToolBarItem)
	@Override
	public void addToolBarItem(UIToolBarItem item, boolean trigger) {
		if (item == null) {
			return;
		}
		if (items.containsKey(item.getID())) {
			return;
		}

		items.put(item.getID(), item);

		String groupID = item.getGroupID();
		synchronized (mapGroupToItemIDs) {
			List<String> list = mapGroupToItemIDs.get(groupID);
			if (list == null) {
				list = new ArrayList<>();
				mapGroupToItemIDs.put(groupID, list);
			}
			list.add(item.getID());
		}

		if (trigger) {
  		ToolBarManagerListener[] listeners = listListeners.toArray(new ToolBarManagerListener[0]);
  		for (ToolBarManagerListener l : listeners) {
  			l.toolbarItemAdded(item);
  		}
		}
	}

	@Override
	public String[] getToolBarIDsByGroup(String groupID) {
		synchronized (mapGroupToItemIDs) {
			List<String> list = mapGroupToItemIDs.get(groupID);
			if (list == null) {
				return new String[0];
			}
			return list.toArray(new String[0]);
		}
	}

	public UIToolBarItem[] getToolBarItemsByGroup(String groupID) {
		synchronized (mapGroupToItemIDs) {
			List<String> list = mapGroupToItemIDs.get(groupID);
			if (list == null) {
				return new UIToolBarItem[0];
			}
			UIToolBarItem[] items = new UIToolBarItem[list.size()];
			int i = 0;
			for (String id : list) {
				items[i] = getToolBarItem(id);
				i++;
			}
			return items;
		}
	}

	// @see com.biglybt.pif.ui.toolbar.UIToolBarManager#removeToolBarItem(java.lang.String)
	@Override
	public void removeToolBarItem(String id) {
		UIToolBarItem toolBarItem = items.remove(id);
		if (toolBarItem != null) {

			synchronized (mapGroupToItemIDs) {
				List<String> list = mapGroupToItemIDs.get(toolBarItem.getGroupID());
				if (list != null) {
					list.remove(toolBarItem.getID());
				}
			}

			ToolBarManagerListener[] listeners = listListeners.toArray(new ToolBarManagerListener[0]);
			for (ToolBarManagerListener l : listeners) {
				l.toolbarItemRemoved(toolBarItem);
			}
		}
	}

	// @see com.biglybt.ui.swt.pifimpl.UIToolBarManagerCore#getGroupIDs()
	@Override
	public String[] getGroupIDs() {
		return mapGroupToItemIDs.keySet().toArray(new String[0]);
	}
}
