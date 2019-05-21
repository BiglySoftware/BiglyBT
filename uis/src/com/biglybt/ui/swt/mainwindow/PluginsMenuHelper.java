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

package com.biglybt.ui.swt.mainwindow;

import java.util.List;
import java.util.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.AERunnable;
import com.biglybt.pifimpl.local.utils.FormattersImpl;
import com.biglybt.ui.common.util.MenuItemManager;
import com.biglybt.ui.swt.MenuBuildUtils;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.UIFunctionsSWT;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.pif.UISWTInstance;
import com.biglybt.ui.swt.pif.UISWTViewEventListener;
import com.biglybt.ui.swt.pifimpl.BasicPluginViewImpl;
import com.biglybt.ui.swt.pifimpl.UISWTViewEventListenerHolder;
import com.biglybt.ui.swt.pifimpl.UISWTViewImpl;

import com.biglybt.pif.ui.menus.MenuManager;

public class PluginsMenuHelper
{
	private static PluginsMenuHelper INSTANCE = null;

	private final AEMonitor plugin_helper_mon = new AEMonitor("plugin_helper_mon");

	private final Comparator<String>	alpha_comparator = new FormattersImpl().getAlphanumericComparator( true );

	private final Map<String, IViewInfo> plugin_view_info_map = new TreeMap<>(alpha_comparator);

	private Map<String, IViewInfo> plugin_logs_view_info_map = new TreeMap<>(alpha_comparator);

	private List<PluginAddedViewListener> pluginAddedViewListener = new ArrayList<>();

	private PluginsMenuHelper() {
		//Making this private
	}

	public static PluginsMenuHelper getInstance() {
		if (null == INSTANCE) {
			INSTANCE = new PluginsMenuHelper();
		}
		return INSTANCE;
	}

	public static void disposeInstance() {
		INSTANCE = null;
	}

	public void buildPluginLogsMenu(Menu parentMenu) {
		try {

			plugin_helper_mon.enter();

			createViewInfoMenuItems(parentMenu, plugin_logs_view_info_map);

		} finally {
			plugin_helper_mon.exit();
		}
	}
	private void
	sort(
		com.biglybt.pif.ui.menus.MenuItem[] plugin_items )
	{
		Arrays.sort(
			plugin_items,
			(o1, o2) -> ( alpha_comparator.compare( o1.getText(), o2.getText())));
	}

	public boolean buildToolsMenu(Menu toolsMenu) {
		MenuItemManager menuItemManager = MenuItemManager.getInstance();
		com.biglybt.pif.ui.menus.MenuItem[] plugin_items = menuItemManager.getAllAsArray(
			MenuManager.MENU_MENUBAR_TOOLS);
		if (plugin_items.length > 0) {
			sort( plugin_items );

			MenuBuildUtils.addPluginMenuItems(plugin_items, toolsMenu, true,
				true, MenuBuildUtils.BASIC_MENU_ITEM_CONTROLLER);
		}
		return false;
	}

	public boolean buildViewMenu(Menu viewMenu, Shell parent) {

		int itemCount = viewMenu.getItemCount();
		MenuItemManager menuItemManager = MenuItemManager.getInstance();
		com.biglybt.pif.ui.menus.MenuItem[] plugin_items = menuItemManager.getAllAsArray(
				MenuManager.MENU_MENUBAR);
		if (plugin_items.length > 0) {
			sort( plugin_items );

			MenuBuildUtils.addPluginMenuItems(plugin_items, viewMenu, true,
					true, MenuBuildUtils.BASIC_MENU_ITEM_CONTROLLER);
		}

		try {

			plugin_helper_mon.enter();

			if ( plugin_items.length > 0 && plugin_view_info_map.size() > 0 ){
				new MenuItem( viewMenu, SWT.SEPARATOR );
			}
			createViewInfoMenuItems(viewMenu, plugin_view_info_map);

		} finally {
			plugin_helper_mon.exit();
		}


		return viewMenu.getItemCount() > itemCount;
	}

	public void buildPluginMenu(Menu pluginMenu, boolean showPluginViews) {

		try {

			plugin_helper_mon.enter();
			if (showPluginViews) {
				createViewInfoMenuItems(pluginMenu, plugin_view_info_map);

				MenuItem menu_plugin_logViews = MenuFactory.addLogsViewMenuItem(pluginMenu);
				createViewInfoMenuItems(menu_plugin_logViews.getMenu(),
					plugin_logs_view_info_map);
			}
		} finally {
			plugin_helper_mon.exit();
		}

		if (showPluginViews) {
			MenuFactory.addSeparatorMenuItem(pluginMenu);

			com.biglybt.pif.ui.menus.MenuItem[] plugin_items;
			plugin_items = MenuItemManager.getInstance().getAllAsArray("mainmenu");
			if (plugin_items.length > 0) {
				sort(plugin_items);

				MenuBuildUtils.addPluginMenuItems(plugin_items, pluginMenu, true,
						true, MenuBuildUtils.BASIC_MENU_ITEM_CONTROLLER);
				MenuFactory.addSeparatorMenuItem(pluginMenu);
			}
		}

		MenuFactory.addGetPluginsMenuItem(pluginMenu);
		
		MenuFactory.addPluginInstallFromFileItem( pluginMenu );		
		
		MenuFactory.addPluginUnInstallMenuItem(pluginMenu);
		
		if ( !showPluginViews ){
			
			MenuFactory.addSeparatorMenuItem(pluginMenu);
			
			try{

				plugin_helper_mon.enter();
				
				MenuItem menu_plugin_logViews = MenuFactory.addLogsViewMenuItem(pluginMenu);
				
				createViewInfoMenuItems(menu_plugin_logViews.getMenu(),	plugin_logs_view_info_map);
				
			} finally {
				plugin_helper_mon.exit();
			}
		}
	}

	public void addPluginView(String sViewID, UISWTViewEventListener l) {
		IViewInfo view_info = new IViewInfo();
		view_info.viewID = sViewID;
		view_info.event_listener = l;

		String name = null;

		String sResourceID = UISWTViewImpl.CFG_PREFIX + sViewID + ".title";
		boolean bResourceExists = MessageText.keyExists(sResourceID);
		if (!bResourceExists) {
			if (l instanceof UISWTViewEventListenerHolder) {
				name = ((UISWTViewEventListenerHolder) l).getPluginInterface().getPluginconfig().getPluginStringParameter(
						sResourceID, null);
			}
		}

		if (bResourceExists) {
			name = MessageText.getString(sResourceID);
		} else if (name == null) {
			// try plain resource
			sResourceID = sViewID;
			bResourceExists = MessageText.keyExists(sResourceID);

			if (bResourceExists) {
				name = MessageText.getString(sResourceID);
			} else {
				name = sViewID.replace('.', ' '); // support old plugins
			}
		}

		view_info.name = name;

		Map<String, IViewInfo> map_to_use;

		if ( 	( l instanceof BasicPluginViewImpl ) ||
				(	( l instanceof UISWTViewEventListenerHolder )) && ((UISWTViewEventListenerHolder)l).isLogView()){

			map_to_use = plugin_logs_view_info_map;

		}else{
			map_to_use = plugin_view_info_map;
		}

		try {
			plugin_helper_mon.enter();
			map_to_use.put(name, view_info);
		} finally {
			plugin_helper_mon.exit();
		}
		triggerPluginAddedViewListeners(view_info);
	}

	private void removePluginViewsWithID(String sViewID, Map map) {
		if (sViewID == null) {
			return;
		}
		Iterator itr = map.values().iterator();
		IViewInfo view_info = null;
		while (itr.hasNext()) {
			view_info = (IViewInfo) itr.next();
			if (sViewID.equals(view_info.viewID)) {
				itr.remove();
			}
		}
	}

	public void removePluginViews(final String sViewID) {
		try {
			plugin_helper_mon.enter();
			removePluginViewsWithID(sViewID, plugin_view_info_map);
			removePluginViewsWithID(sViewID, plugin_logs_view_info_map);
		} finally {
			plugin_helper_mon.exit();
		}
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				UIFunctionsSWT uiFunctions = UIFunctionsManagerSWT.getUIFunctionsSWT();
				if (uiFunctions != null) {
					uiFunctions.closePluginViews(sViewID);
				}
			}
		});
	}

	/**
	 * Populates the client's menu bar
	 * @param locales
	 * @param parent
	 */

	private void createViewInfoMenuItem(Menu parent, final IViewInfo info) {
		MenuItem item = new MenuItem(parent, SWT.NULL);
		item.setText(info.name);
		if (info.viewID != null) {
			item.setData("ViewID", info.viewID);
		}
		item.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event e) {
				UIFunctionsSWT uiFunctions = UIFunctionsManagerSWT.getUIFunctionsSWT();
				if (uiFunctions != null) {
					info.openView(uiFunctions);
				}
			}
		});
	}

	private void createViewInfoMenuItems(Menu parent, Map menu_data) {
		Iterator itr = menu_data.values().iterator();
		while (itr.hasNext()) {
			createViewInfoMenuItem(parent, (IViewInfo) itr.next());
		}
	}

	public IViewInfo[] getPluginViewsInfo() {
		return plugin_view_info_map.values().toArray(new IViewInfo[0]);
	}

	public IViewInfo[] getPluginLogViewsInfo() {
		return plugin_logs_view_info_map.values().toArray(new IViewInfo[0]);
	}

	public static class IViewInfo
	{
		public String name;

		public String viewID;

		public UISWTViewEventListener event_listener;

		public void openView(UIFunctionsSWT uiFunctions) {
			if (event_listener != null) {
				uiFunctions.openPluginView(UISWTInstance.VIEW_MAIN, viewID,
						event_listener, null, true);
			}
		}

	}

	public void addPluginAddedViewListener(PluginAddedViewListener l) {
		pluginAddedViewListener.add(l);

		IViewInfo[] viewsInfo = getPluginViewsInfo();
		for (IViewInfo info : viewsInfo) {
			l.pluginViewAdded(info);
		}
		viewsInfo = getPluginLogViewsInfo();
		for (IViewInfo info : viewsInfo) {
			l.pluginViewAdded(info);
		}
	}

	public void triggerPluginAddedViewListeners(final IViewInfo viewInfo) {
		final Object[] listeners = pluginAddedViewListener.toArray();
		if (pluginAddedViewListener.size() > 0) {
			Utils.execSWTThread(new AERunnable() {
				@Override
				public void runSupport() {
					for (int i = 0; i < listeners.length; i++) {
						PluginAddedViewListener l = (PluginAddedViewListener) listeners[i];
						l.pluginViewAdded(viewInfo);
					}
				}
			});
		}
	}

	public static interface PluginAddedViewListener {
		public void pluginViewAdded(IViewInfo viewInfo);
	}

	public IViewInfo findIViewInfo(UISWTViewEventListener l) {
		IViewInfo foundViewInfo = null;

		IViewInfo[] pluginViewsInfo = getPluginViewsInfo();
		for (int i = 0; i < pluginViewsInfo.length; i++) {
			IViewInfo viewInfo = pluginViewsInfo[i];
			if (viewInfo.event_listener == l) {
				foundViewInfo = viewInfo;
				break;
			}
		}
		if (foundViewInfo == null) {
			pluginViewsInfo = getPluginLogViewsInfo();
			for (int i = 0; i < pluginViewsInfo.length; i++) {
				IViewInfo viewInfo = pluginViewsInfo[i];
				if (viewInfo.event_listener == l) {
					foundViewInfo = viewInfo;
					break;
				}
			}
		}
		return foundViewInfo;
	}
}
