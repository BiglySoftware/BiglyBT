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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import com.biglybt.pifimpl.local.utils.FormattersImpl;
import com.biglybt.ui.common.util.MenuItemManager;
import com.biglybt.ui.swt.MenuBuildUtils;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.UIFunctionsSWT;
import com.biglybt.ui.swt.pif.UISWTInstance;
import com.biglybt.ui.swt.pifimpl.BasicPluginViewImpl;
import com.biglybt.ui.swt.pifimpl.UISWTViewBuilderCore;
import com.biglybt.ui.swt.views.ViewManagerSWT;

import com.biglybt.pif.ui.menus.MenuManager;

public class PluginsMenuHelper
{
	private final static Comparator<String>	alpha_comparator = new FormattersImpl().getAlphanumericComparator( true );

	public static void buildPluginLogsMenu(Menu parentMenu) {
		createViewInfoMenuItems(parentMenu,
				getLogViewBuilders(ViewManagerSWT.getInstance()));
	}

	private static void
	sort(
		com.biglybt.pif.ui.menus.MenuItem[] plugin_items )
	{
		Arrays.sort(
			plugin_items,
			(o1, o2) -> ( alpha_comparator.compare( o1.getText(), o2.getText())));
	}

	public static boolean buildToolsMenu(Menu toolsMenu) {
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

	public static boolean buildViewMenu(Menu viewMenu) {

		int itemCount = viewMenu.getItemCount();
		MenuItemManager menuItemManager = MenuItemManager.getInstance();
		com.biglybt.pif.ui.menus.MenuItem[] plugin_items = menuItemManager.getAllAsArray(
				MenuManager.MENU_MENUBAR);
		if (plugin_items.length > 0) {
			sort( plugin_items );

			MenuBuildUtils.addPluginMenuItems(plugin_items, viewMenu, true,
					true, MenuBuildUtils.BASIC_MENU_ITEM_CONTROLLER);
		}

		List<UISWTViewBuilderCore> mainViewBuilders = getMainViewBuilder(
				ViewManagerSWT.getInstance());
		if ( plugin_items.length > 0 && mainViewBuilders.size() > 0 ){
			new MenuItem( viewMenu, SWT.SEPARATOR );
		}
		createViewInfoMenuItems(viewMenu, mainViewBuilders);

		if ( viewMenu.getItemCount() > itemCount ){
		
			MenuFactory.addSeparatorMenuItem( viewMenu );
		}
		
		MenuItem menu_plugin_logViews = MenuFactory.addLogsViewMenuItem(viewMenu);
		ViewManagerSWT vi = ViewManagerSWT.getInstance();
		List<UISWTViewBuilderCore> logViewBuilders = getLogViewBuilders(vi);
		createViewInfoMenuItems(menu_plugin_logViews.getMenu(),	logViewBuilders);
				
		return viewMenu.getItemCount() > itemCount;
	}

	public static void buildPluginMenu(Menu pluginMenu, boolean showPluginViews) {

		/*
		if (showPluginViews) {
			ViewManagerSWT vi = ViewManagerSWT.getInstance();
			List<UISWTViewBuilderCore> mainViewBuilders = getMainViewBuilder(vi);
			createViewInfoMenuItems(pluginMenu, mainViewBuilders);

			MenuItem menu_plugin_logViews = MenuFactory.addLogsViewMenuItem(pluginMenu);
			List<UISWTViewBuilderCore> logViewBuilders = getLogViewBuilders(vi);
			createViewInfoMenuItems(menu_plugin_logViews.getMenu(),	logViewBuilders);
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
		*/
		
		MenuFactory.addGetPluginsMenuItem(pluginMenu);
		
		MenuFactory.addPluginInstallFromFileItem( pluginMenu );		
		
		MenuFactory.addPluginUnInstallMenuItem(pluginMenu);

		/*
		if ( !showPluginViews ){
			
			MenuFactory.addSeparatorMenuItem(pluginMenu);
			
			MenuItem menu_plugin_logViews = MenuFactory.addLogsViewMenuItem(pluginMenu);

			List<UISWTViewBuilderCore> logViewBuilders = getLogViewBuilders(
				ViewManagerSWT.getInstance());
			createViewInfoMenuItems(menu_plugin_logViews.getMenu(), logViewBuilders);
		}
		*/
	}

	private static List<UISWTViewBuilderCore> getMainViewBuilder(
			ViewManagerSWT vi) {
		List<UISWTViewBuilderCore> mainViewBuilders = vi.getBuilders(
				UISWTInstance.VIEW_MAIN);
		mainViewBuilders.removeIf(
				builder -> builder.isListenerOfClass(BasicPluginViewImpl.class));
		return mainViewBuilders;
	}
	
	private static List<UISWTViewBuilderCore> getLogViewBuilders(
			ViewManagerSWT vi) {
		return vi.getBuildersOfClass(UISWTInstance.VIEW_MAIN,
				BasicPluginViewImpl.class);
	}

	/**
	 * Populates the client's menu bar
	 * @param locales
	 * @param parent
	 */
	private static void createViewInfoMenuItem(Menu parent,
			UISWTViewBuilderCore builder) {
		MenuItem item = new MenuItem(parent, SWT.NULL);
		item.setText(builder.getInitialTitle());
		item.setData("ViewID", builder.getViewID());
		item.addListener(SWT.Selection, e -> {
			UIFunctionsSWT uiFunctions = UIFunctionsManagerSWT.getUIFunctionsSWT();
			if (uiFunctions != null) {
				uiFunctions.openPluginView(builder, true);
			}
		});
	}

	private static void createViewInfoMenuItems(Menu parent,
			List<UISWTViewBuilderCore> list) {
		
		list = new ArrayList<>( list );
		
		Comparator<String> comp = FormattersImpl.getAlphanumericComparator2(true);
		
		Collections.sort( list, (m1,m2)->{
			return( comp.compare(m1.getInitialTitle(),m2.getInitialTitle()));
		});
		
		for (UISWTViewBuilderCore builder : list) {
			createViewInfoMenuItem(parent, builder);
		}
	}
}
