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

package com.biglybt.ui.swt.shells.main;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.config.impl.ConfigurationDefaults;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemProperties;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.common.table.TableRowCore;
import com.biglybt.ui.common.table.TableStructureEventDispatcher;
import com.biglybt.ui.common.table.TableView;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.selectedcontent.SelectedContentManager;
import com.biglybt.ui.skin.SkinConstants;
import com.biglybt.ui.swt.*;
import com.biglybt.ui.swt.mainwindow.IMainMenu;
import com.biglybt.ui.swt.mainwindow.IMenuConstants;
import com.biglybt.ui.swt.mainwindow.MenuFactory;
import com.biglybt.ui.swt.mainwindow.PluginsMenuHelper;
import com.biglybt.ui.swt.pif.UISWTInstance;
import com.biglybt.ui.swt.pifimpl.UIToolBarManagerImpl;
import com.biglybt.ui.swt.skin.SWTSkin;
import com.biglybt.ui.swt.skin.SWTSkinFactory;
import com.biglybt.ui.swt.skin.SWTSkinObject;
import com.biglybt.ui.swt.skin.SWTSkinUtils;
import com.biglybt.ui.swt.views.QuickLinksView;
import com.biglybt.ui.swt.views.columnsetup.TableColumnSetupWindow;
import com.biglybt.ui.swt.views.skin.SkinViewManager;
import com.biglybt.ui.swt.views.skin.sidebar.SideBar;

import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.UIException;
import com.biglybt.pif.ui.UIInstanceFactory;
import com.biglybt.pif.ui.toolbar.UIToolBarActivationListener;
import com.biglybt.pif.ui.toolbar.UIToolBarItem;
import com.biglybt.pif.ui.toolbar.UIToolBarManager;

public class MainMenuV3
	implements IMainMenu, IMenuConstants
{
	private final static String PREFIX_V2 = "MainWindow.menu";

	private final static String PREFIX_V3 = "v3.MainWindow.menu";

	private Menu menuBar;

	/**
	 * Creates the main menu on the supplied shell
	 *
	 * @param shell
	 */
	public MainMenuV3(SWTSkin skin, final Shell shell) {
		if (null == skin) {
			System.err.println("MainMenuV3: The parameter [SWTSkin skin] can not be null");
			return;
		}

		buildMenu(shell);

	}

	private void buildMenu(Shell parent) {

		//The Main Menu
		menuBar = parent.getDisplay().getMenuBar();
		if (menuBar == null) {
			menuBar = new Menu(parent, SWT.BAR);
			parent.setMenuBar(menuBar);
		} else {
			Utils.disposeSWTObjects((Object[])menuBar.getItems());
		}

		addFileMenu();
		//addViewMenu();
		addSimpleViewMenu();

		addCommunityMenu();

		addToolsMenu();

		/*
		 * The Torrents menu is a user-configured option
		 */
		if (COConfigurationManager.getBooleanParameter("show_torrents_menu")) {
			addTorrentMenu();
		}

		if (!Constants.isWindows) {
			addWindowMenu();
		}

		// ===== Debug menu (development only)====
		if (com.biglybt.core.util.Constants.isCVSVersion()) {
			final Menu menuDebug = com.biglybt.ui.swt.mainwindow.DebugMenuHelper.createDebugMenuItem(menuBar);
			menuDebug.addMenuListener(new MenuListener() {

				@Override
				public void menuShown(MenuEvent e) {
					MenuItem[] items = menuDebug.getItems();
					Utils.disposeSWTObjects(items);

					DebugMenuHelper.createDebugMenuItem(menuDebug);
					MenuFactory.addSeparatorMenuItem(menuDebug);
					MenuItem menuItem = new MenuItem(menuDebug, SWT.PUSH);
					menuItem.setText("Log Views");
					menuItem.setEnabled(false);
					PluginsMenuHelper.buildPluginLogsMenu(menuDebug);
				}

				@Override
				public void menuHidden(MenuEvent e) {
				}
			});
		}

		addV3HelpMenu();

		/*
		 * Enabled/disable menus based on what ui mode we're in; this method call controls
		 * which menus are enabled when we're in Vuze vs. Vuze Advanced
		 */
		MenuFactory.updateEnabledStates(menuBar);
	}

	/**
	 * Creates the File menu and all its children
	 */
	private void addFileMenu() {
		MenuItem fileItem = MenuFactory.createFileMenuItem(menuBar);
		final Menu fileMenu = fileItem.getMenu();
		builFileMenu(fileMenu);

		fileMenu.addListener(SWT.Show, new Listener() {
			@Override
			public void handleEvent(Event event) {
				MenuItem[] menuItems = fileMenu.getItems();
				for (int i = 0; i < menuItems.length; i++) {
					menuItems[i].dispose();
				}

				builFileMenu(fileMenu);
			}
		});
	}

	/**
	 * Builds the File menu dynamically
	 * @param fileMenu
	 */
	private void builFileMenu(Menu fileMenu) {

		MenuItem openMenuItem = MenuFactory.createOpenMenuItem(fileMenu);
		Menu openSubMenu = openMenuItem.getMenu();
		MenuFactory.addOpenTorrentMenuItem(openSubMenu);
		MenuFactory.addOpenURIMenuItem(openSubMenu);
		MenuFactory.addOpenTorrentForTrackingMenuItem(openSubMenu);
		MenuFactory.addOpenVuzeFileMenuItem(openSubMenu);

		int userMode = COConfigurationManager.getIntParameter("User Mode");

		if ( userMode > 0 ){
			Menu shareSubMenu = MenuFactory.createShareMenuItem(fileMenu).getMenu();
			MenuFactory.addShareFileMenuItem(shareSubMenu);
			MenuFactory.addShareFolderMenuItem(shareSubMenu);
			MenuFactory.addShareFolderContentMenuItem(shareSubMenu);
			MenuFactory.addShareFolderContentRecursiveMenuItem(shareSubMenu);
		}

		MenuFactory.addCreateMenuItem(fileMenu);

		if (!Constants.isOSX) {
			MenuFactory.addSeparatorMenuItem(fileMenu);
			MenuFactory.addCloseWindowMenuItem(fileMenu);
			MenuFactory.addCloseDetailsMenuItem(fileMenu);
			MenuFactory.addCloseDownloadBarsToMenu(fileMenu);
		}

		MenuFactory.addSeparatorMenuItem(fileMenu);
		MenuFactory.createTransfersMenuItem(fileMenu);

		/*
		 * No need for restart and exit on OS X since it's already handled on the application menu
		 */
		if (!Constants.isOSX) {
			MenuFactory.addSeparatorMenuItem(fileMenu);
			MenuFactory.addRestartMenuItem(fileMenu);
		}

		if (Constants.isCVSVersion() && !UI.isFirstUI()) {
			MenuItem itemLogout = new MenuItem(fileMenu, SWT.PUSH);
			itemLogout.setText("Shutdown UI (Keep " + Constants.APP_NAME + " running)");
			itemLogout.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					UIFunctionsSWT uiFunctions = UIFunctionsManagerSWT.getUIFunctionsSWT();
					if (uiFunctions != null) {
						UISWTInstance uiSWTInstance = uiFunctions.getUISWTInstance();
						if (uiSWTInstance instanceof UIInstanceFactory) {
							try {
								PluginInterface pi = PluginInitializer.getDefaultInterface();
								pi.getUIManager().detachUI((UIInstanceFactory) uiSWTInstance);
							} catch (UIException e1) {
								e1.printStackTrace();
							}
						}
					}
				}
			});
		}

		if (!Constants.isOSX) {
			MenuFactory.addExitMenuItem(fileMenu);
		}
	}

	private void addSimpleViewMenu() {
		try {
			MenuItem viewItem = MenuFactory.createViewMenuItem(menuBar);
			final Menu viewMenu = viewItem.getMenu();

			viewMenu.addListener(SWT.Show, new Listener() {
				@Override
				public void handleEvent(Event event) {
					Utils.disposeSWTObjects(viewMenu.getItems());
					buildSimpleViewMenu(viewMenu,-1);
				}
			});

				// hack to handle key binding before menu is actually created...

			final KeyBindings.KeyBindingInfo binding_info = KeyBindings.getKeyBindingInfo( "v3.MainWindow.menu.view." + SkinConstants.VIEWID_PLUGINBAR );

			if ( binding_info != null ){
				Display.getDefault().addFilter(SWT.KeyDown, new Listener() {
					@Override
					public void handleEvent(Event event) {
						if (event.keyCode == binding_info.accelerator ){
							if ( !viewMenu.isDisposed()){
								Utils.disposeSWTObjects(viewMenu.getItems());
								buildSimpleViewMenu(viewMenu, event.keyCode);
							}
						}
					}
				});
			}
		} catch (Exception e) {
			Debug.out("Error creating View Menu", e);
		}
	}

	/**
	 * @param viewMenu
	 *
	 * @since 4.5.0.3
	 */
	private void buildSimpleViewMenu(final Menu viewMenu, int accelerator) {
		try {

			SWTSkin skin = SWTSkinFactory.getInstance();

			MenuFactory.addMenuItem(viewMenu, SWT.CHECK, PREFIX_V3 + ".view.sidebar",
					new Listener() {
						@Override
						public void handleEvent(Event event) {
							SideBar sidebar = (SideBar) SkinViewManager.getByClass(SideBar.class);
							if (sidebar != null) {
								sidebar.flipSideBarVisibility();
							}
						}
					});

			if (COConfigurationManager.getIntParameter("User Mode") > 1) {

				SWTSkinObject plugin_bar = skin.getSkinObject(SkinConstants.VIEWID_PLUGINBAR);

				if ( plugin_bar != null ){

					MenuItem mi =
						MainMenuV3.createPluginBarMenuItem(skin, viewMenu,
							"v3.MainWindow.menu.view." + SkinConstants.VIEWID_PLUGINBAR,
							SkinConstants.VIEWID_PLUGINBAR + ".visible",
							SkinConstants.VIEWID_PLUGINBAR );

					if ( accelerator != -1 && mi.getAccelerator() == accelerator ){

						Listener[] listeners = mi.getListeners( SWT.Selection );

						for ( Listener l: listeners ){

							try{
								l.handleEvent( null );

							}catch( Throwable e ){
							}
						}
					}
				}
			}
			
			SWTSkinObject right_bar = skin.getSkinObject(SkinConstants.VIEWID_RIGHTBAR);

			if ( right_bar != null ){

				MenuItem mi =
					MainMenuV3.createPluginBarMenuItem(skin, viewMenu,
						"v3.MainWindow.menu.view." + SkinConstants.VIEWID_RIGHTBAR,
						SkinConstants.VIEWID_RIGHTBAR + ".visible",
						SkinConstants.VIEWID_RIGHTBAR );
			}
			
			MenuFactory.addViewToolbarMenuItem(viewMenu);

			MenuItem mi =
					MainMenuV3.createQuickLinksMenuItem(skin, viewMenu,
						"v3.MainWindow.menu.view." + SkinConstants.VIEWID_QUICK_LINKS,
						SkinConstants.VIEWID_QUICK_LINKS + ".visible",
						SkinConstants.VIEWID_QUICK_LINKS );
			
			/////////

			MenuItem itemStatusBar = MenuFactory.createTopLevelMenuItem(viewMenu,
					"v3.MainWindow.menu.view.statusbar");
			itemStatusBar.setText(itemStatusBar.getText());
			Menu menuStatusBar = itemStatusBar.getMenu();

			final String[] statusAreaLangs = {
				"ConfigView.section.style.status.show_sr",
				"ConfigView.section.style.status.show_nat",
				"ConfigView.section.style.status.show_ddb",
				"ConfigView.section.style.status.show_ipf",
			};
			final String[] statusAreaConfig = {
				"Status Area Show SR",
				"Status Area Show NAT",
				"Status Area Show DDB",
				"Status Area Show IPF",
			};

			for (int i = 0; i < statusAreaConfig.length; i++) {
				final String configID = statusAreaConfig[i];
				String langID = statusAreaLangs[i];

				final MenuItem item = new MenuItem(menuStatusBar, SWT.CHECK);
				Messages.setLanguageText(item, langID);
				item.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						COConfigurationManager.setParameter(configID,
								!COConfigurationManager.getBooleanParameter(configID));
					}
				});
				menuStatusBar.addListener(SWT.Show, new Listener() {
					@Override
					public void handleEvent(Event event) {
						item.setSelection(COConfigurationManager.getBooleanParameter(configID));
					}
				});
			}

			/////////

			if (Constants.isWindows) {
				MenuFactory.addSeparatorMenuItem(viewMenu);
			}

			boolean needsSep = false;
			boolean enabled = COConfigurationManager.getBooleanParameter("Beta Programme Enabled");
			if (enabled) {
				MenuFactory.addMenuItem(viewMenu, PREFIX_V2 + ".view.beta", new Listener() {
					@Override
					public void handleEvent(Event event) {
						MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();
						if (mdi != null) {
							mdi.showEntryByID(MultipleDocumentInterface.SIDEBAR_SECTION_BETAPROGRAM);
						}
					}
				});
				needsSep = true;
			}

			if (needsSep) {
				MenuFactory.addSeparatorMenuItem(viewMenu);
			}

			needsSep = PluginsMenuHelper.buildViewMenu(viewMenu);

			if ( COConfigurationManager.getBooleanParameter( "Library.EnableSimpleView" )){

				if (needsSep) {
					MenuFactory.addSeparatorMenuItem(viewMenu);
				}

					// Ubuntu Unity (14.04) with SWT 4508 crashes when global View menu triggered as it appears
					// that radio menu items aren't supported
					// https://bugs.eclipse.org/bugs/show_bug.cgi?id=419729#c9

				int simple_advanced_menu_type = Constants.isLinux?SWT.CHECK:SWT.RADIO;

				MenuFactory.addMenuItem(viewMenu, simple_advanced_menu_type, PREFIX_V3
						+ ".view.asSimpleList", new Listener() {
					@Override
					public void handleEvent(Event event) {
						UIToolBarManager tb = UIToolBarManagerImpl.getInstance();
						if (tb != null) {
							UIToolBarItem item = tb.getToolBarItem("modeBig");
							if (item != null) {
								item.triggerToolBarItem(
										UIToolBarActivationListener.ACTIVATIONTYPE_NORMAL,
										SelectedContentManager.convertSelectedContentToObject(null));
							}
						}
					}
				});
				MenuFactory.addMenuItem(viewMenu, simple_advanced_menu_type, PREFIX_V3
						+ ".view.asAdvancedList", new Listener() {
					@Override
					public void handleEvent(Event event) {
						UIToolBarManager tb = UIToolBarManagerImpl.getInstance();
						if (tb != null) {
							UIToolBarItem item = tb.getToolBarItem("modeSmall");
							if (item != null) {
								item.triggerToolBarItem(
										UIToolBarActivationListener.ACTIVATIONTYPE_NORMAL,
										SelectedContentManager.convertSelectedContentToObject(null));
							}
						}
					}
				});
				
				needsSep = true;
			}

			if (needsSep) {
				MenuFactory.addSeparatorMenuItem(viewMenu);
			}
			
			MenuFactory.addMenuItem(viewMenu, PREFIX_V2 + ".view.columnsetup", new Listener() {
				@Override
				public void handleEvent(Event event) {
					
					TableView<?> tv = SelectedContentManager.getCurrentlySelectedTableView();
							
					if ( tv == null ){
						return;
					}
					
					TableRowCore focusedRow = tv.getFocusedRow();
					if (focusedRow == null || focusedRow.isRowDisposed()) {
						focusedRow = tv.getRow(0);
					}
					String tableID = tv.getTableID();
					new TableColumnSetupWindow(tv.getDataSourceType(), tableID, null, focusedRow,
							TableStructureEventDispatcher.getInstance(tableID)).open();
				}
			});
			
			viewMenu.addMenuListener(new MenuListener() {

				@Override
				public void menuShown(MenuEvent e) {

					MenuItem sidebarMenuItem = MenuFactory.findMenuItem(viewMenu,
							PREFIX_V3 + ".view.sidebar");
					if (sidebarMenuItem != null) {
						MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();
						if (mdi != null) {
							sidebarMenuItem.setSelection(mdi.isVisible());
						}
					}

					if ( COConfigurationManager.getBooleanParameter( "Library.EnableSimpleView" )){

						MenuItem itemShowAsSimple = MenuFactory.findMenuItem(viewMenu,
								PREFIX_V3 + ".view.asSimpleList");
						if (itemShowAsSimple != null) {
							UIToolBarManager tb = UIToolBarManagerImpl.getInstance();
							if (tb != null) {
								UIToolBarItem item = tb.getToolBarItem("modeBig");
								long state = item == null ? 0 : item.getState();
								itemShowAsSimple.setEnabled((state & UIToolBarItem.STATE_ENABLED) > 0);
								itemShowAsSimple.setSelection((state & UIToolBarItem.STATE_DOWN) > 0);
							}
						}
						MenuItem itemShowAsAdv = MenuFactory.findMenuItem(viewMenu, PREFIX_V3
								+ ".view.asAdvancedList");
						if (itemShowAsAdv != null) {
							UIToolBarManager tb = UIToolBarManagerImpl.getInstance();
							if (tb != null) {
								UIToolBarItem item = tb.getToolBarItem("modeSmall");
								long state = item == null ? 0 : item.getState();
								itemShowAsAdv.setEnabled((state & UIToolBarItem.STATE_ENABLED) > 0);
								itemShowAsAdv.setSelection((state & UIToolBarItem.STATE_DOWN) > 0);
							}
						}
					}
					
					TableView<?> tv = SelectedContentManager.getCurrentlySelectedTableView();
					
					MenuItem itemColumnSetup = MenuFactory.findMenuItem(viewMenu,
							PREFIX_V2 + ".view.columnsetup");
					
					itemColumnSetup.setEnabled( tv != null );
				}

				@Override
				public void menuHidden(MenuEvent e) {
				}
			});
		} catch (Exception e) {
			Debug.out("Error creating View Menu", e);
		}
	}
	
	private void
	addCommunityMenu()
	{
		MenuFactory.createCommunityMenuItem(menuBar);
	}

	/**
	 * Creates the Tools menu and all its children
	 */
	private void addToolsMenu() {
		MenuItem toolsItem = MenuFactory.createToolsMenuItem(menuBar);
		MenuBuildUtils.addMaintenanceListenerForMenu(toolsItem.getMenu(),
				(toolsMenu, menuEvent) -> {
					MenuFactory.addMyTrackerMenuItem(toolsMenu);
					MenuFactory.addMySharesMenuItem(toolsMenu);
					MenuFactory.addConsoleMenuItem(toolsMenu);
					MenuFactory.addStatisticsMenuItem(toolsMenu);
					MenuFactory.addSpeedLimitsToMenu(toolsMenu);

					MenuFactory.addTransferBarToMenu(toolsMenu);
					//MenuFactory.addAllPeersMenuItem(toolsMenu);	moved to View menu
					MenuFactory.addClientStatsMenuItem(toolsMenu);
					MenuFactory.addBlockedIPsMenuItem(toolsMenu);

					PluginsMenuHelper.buildToolsMenu(toolsMenu);

					MenuFactory.addSeparatorMenuItem(toolsMenu);
					MenuFactory.createPluginsMenuItem(toolsMenu, false);

					MenuFactory.addPairingMenuItem(toolsMenu);

					MenuFactory.addOptionsMenuItem(toolsMenu);
				}, true);
	}

	/**
	 * Creates the Help menu and all its children
	 */
	private void addV3HelpMenu() {
		MenuItem helpItem = MenuFactory.createHelpMenuItem(menuBar);
		Menu helpMenu = helpItem.getMenu();

		if (!Constants.isOSX) {
			/*
			 * The 'About' menu is on the application menu on OSX
			 */
			MenuFactory.addAboutMenuItem(helpMenu);
			MenuFactory.addSeparatorMenuItem(helpMenu);
		}

		MenuFactory.addMenuItem(helpMenu, PREFIX_V3 + ".getting_started",
				new Listener() {
					@Override
					public void handleEvent(Event event) {
						MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();
						if (mdi != null) {
							mdi.showEntryByID(SideBar.SIDEBAR_SECTION_WELCOME);
						}
					}
				});

		MenuFactory.addReleaseNotesMenuItem(helpMenu);

		if (!SystemProperties.isJavaWebStartInstance()) {
			MenuFactory.addSeparatorMenuItem(helpMenu);
			MenuFactory.addCheckUpdateMenuItem(helpMenu);
			MenuFactory.addBetaMenuItem(helpMenu);
		}

		MenuFactory.addDonationMenuItem(helpMenu);

		MenuFactory.addSeparatorMenuItem(helpMenu);
		MenuFactory.addConfigWizardMenuItem(helpMenu);
		MenuFactory.addNatTestMenuItem(helpMenu);
		MenuFactory.addNetStatusMenuItem(helpMenu);
		MenuFactory.addSpeedTestMenuItem(helpMenu);
		MenuFactory.addAdvancedHelpMenuItem(helpMenu);

		MenuFactory.addSeparatorMenuItem(helpMenu);
		MenuFactory.addDebugHelpMenuItem(helpMenu);

	}

	/**
	 * Creates the Window menu and all its children
	 */
	private void addWindowMenu() {
		MenuItem menu_window = MenuFactory.createWindowMenuItem(menuBar);
		Menu windowMenu = menu_window.getMenu();

		MenuFactory.addMinimizeWindowMenuItem(windowMenu);
		MenuFactory.addZoomWindowMenuItem(windowMenu);
		MenuFactory.addCloseWindowMenuItem(windowMenu);

		MenuFactory.addSeparatorMenuItem(windowMenu);
		MenuFactory.addBringAllToFrontMenuItem(windowMenu);
		MenuFactory.addCloseDetailsMenuItem(windowMenu);
		MenuFactory.addCloseDownloadBarsToMenu(windowMenu);

		MenuFactory.addSeparatorMenuItem(windowMenu);
		MenuFactory.appendWindowMenuItems(windowMenu);
	}

	/**
	 * Creates the Torrent menu and all its children
	 */
	private void addTorrentMenu() {
		MenuFactory.createTorrentMenuItem(menuBar);
	}

	@Override
	public Menu getMenu(String id) {
		if (MENU_ID_MENU_BAR.equals(id)) {
			return menuBar;
		}
		return MenuFactory.findMenu(menuBar, id);
	}

	//====================================

	/**
	 * @param viewMenu
	 */
	public static MenuItem createPluginBarMenuItem(final SWTSkin skin, Menu viewMenu,
			final String textID, final String configID, final String viewID){
		MenuItem item;

		if (!ConfigurationDefaults.getInstance().doesParameterDefaultExist(configID)) {
			COConfigurationManager.setBooleanDefault(configID, true);
		}

		item = MenuFactory.addMenuItem(viewMenu, SWT.CHECK, textID,
				new Listener() {
					@Override
					public void handleEvent(Event event) {
						SWTSkinObject skinObject = skin.getSkinObject(viewID);
						if (skinObject != null) {
							boolean newVisibility = !skinObject.isVisible();

							SWTSkinUtils.setVisibility(skin, configID, viewID, newVisibility, true);
						}
					}
				});
		SWTSkinUtils.setVisibility(skin, configID, viewID,
				COConfigurationManager.getBooleanParameter(configID), false);

		final MenuItem itemViewPluginBar = item;
		final ParameterListener listener = new ParameterListener() {
			@Override
			public void parameterChanged(String parameterName) {
				itemViewPluginBar.setSelection(COConfigurationManager.getBooleanParameter(parameterName));
			}
		};

		COConfigurationManager.addAndFireParameterListener(configID, listener);
		item.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				COConfigurationManager.removeParameterListener(configID, listener);
			}
		});

		return item;
	}

	public static MenuItem createQuickLinksMenuItem(final SWTSkin skin, Menu viewMenu,
			final String textID, final String configID, final String viewID) {
		
		if (!ConfigurationDefaults.getInstance().doesParameterDefaultExist(configID)) {
			COConfigurationManager.setBooleanDefault(configID, true);
		}

		MenuItem item = MenuFactory.addMenuItem(viewMenu, SWT.CHECK, textID,
				new Listener() {
					@Override
					public void handleEvent(Event event) {
						SWTSkinObject skinObject = skin.getSkinObject(viewID);
						if (skinObject != null) {
							boolean newVisibility = !skinObject.isVisible();
							
							QuickLinksView.setVisible( newVisibility );
						}
					}
				});
	
		item.setEnabled(COConfigurationManager.getBooleanParameter( "IconBar.enabled" ));
		
		final ParameterListener listener = new ParameterListener() {
			@Override
			public void parameterChanged(String parameterName) {
				item.setSelection(COConfigurationManager.getBooleanParameter(parameterName));
			}
		};

		COConfigurationManager.addAndFireParameterListener(configID, listener);
		item.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				COConfigurationManager.removeParameterListener(configID, listener);
			}
		});

		return item;
	}
	
	/*
	// backward compat..
	public static void setVisibility(SWTSkin skin, String configID,
			String viewID, boolean visible) {
		SWTSkinUtils.setVisibility(skin, configID, viewID, visible, true, false);
	}

	// backward compat..
	public static void setVisibility(SWTSkin skin, String configID,
			String viewID, boolean visible, boolean save) {
		SWTSkinUtils.setVisibility(skin, configID, viewID, visible, save, false);
	}
	*/
}
