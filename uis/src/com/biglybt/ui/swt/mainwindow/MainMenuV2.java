/*
 * Created on 4 mai 2004
 * Created by Olivier Chalouhi
 *
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

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemProperties;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.MenuBuildUtils;
import com.biglybt.ui.swt.Utils;

/**
 * @author Olivier Chalouhi
 */
public class MainMenuV2
	implements IMainMenu
{
	private Menu menuBar;

	/**
	 * <p>Creates the main menu bar and attaches it to the given shell</p>
	 * @param shell A shell
	 */
	public MainMenuV2(Shell shell) {
		createMenus(shell);
	}

	private void createMenus(final Shell parent) {

		//The Main Menu
		menuBar = parent.getDisplay().getMenuBar();
		if (menuBar == null) {
			menuBar = new Menu(parent, SWT.BAR);
			parent.setMenuBar(menuBar);
		}else{
			Utils.disposeSWTObjects((Object[])menuBar.getItems());
		}


		addFileMenu();

		/* ======= View, Transfers, Torrent, Tools menus =====
		 * hig compliance for OSX dictates that more granular actions are positioned farther
		 * on the right of the menu and more general actions are closer to the left
		 * NOTE: we keep the original menu order (for non-OSX) in-tact so as to not disturb existing non-OSX users
		 */
		if (Constants.isOSX) {
			addViewMenu(parent);
			addTransferMenu();
			addTorrentMenu(parent);
		} else {
			addTransferMenu();
			addTorrentMenu(parent);
			addViewMenu(parent);

			/*
			 * The Tools menu is only present on non-OSX systems
			 */
			addToolsMenu();
		}

		addPluginsMenu(parent);

		// ========== Windows menu (OSX only)================
		if (Constants.isOSX) {
			addWindowMenu(parent);
		}

		// =========== Debug menu (development only)=========
		if (Constants.isCVSVersion()) {
			DebugMenuHelper.createDebugMenuItem(menuBar);
		}

		addCommunityMenu( parent);
		
		addV2HelpMenu(parent);

		/*
		 * Enabled/disable menus based on what ui mode we're in
		 * NOTE: This is largely superfluous here since all menu items in the classic UI
		 * are available at all time; this method is left here for completeness should we
		 * add dynamic behavior to the menu in the future.
		 */
		MenuFactory.updateEnabledStates(menuBar);

	}

	/**
	 * Creates the Window menu and all its children
	 * @param parent
	 */
	private void addWindowMenu(final Shell parent) {
		MenuItem menu_window = MenuFactory.createWindowMenuItem(menuBar);
		Menu windowMenu = menu_window.getMenu();

		MenuFactory.addMinimizeWindowMenuItem(windowMenu);
		MenuFactory.addZoomWindowMenuItem(windowMenu);

		MenuFactory.addSeparatorMenuItem(windowMenu);
		MenuFactory.addBlockedIPsMenuItem(windowMenu);

		MenuFactory.addSeparatorMenuItem(windowMenu);
		MenuFactory.addBringAllToFrontMenuItem(windowMenu);

		MenuFactory.addSeparatorMenuItem(windowMenu);
		MenuFactory.appendWindowMenuItems(windowMenu);
	}

	/**
	 * Creates the File menu and all its children
	 */
	private void addFileMenu() {
		MenuItem fileItem = MenuFactory.createFileMenuItem(menuBar);
		Menu fileMenu = fileItem.getMenu();

		MenuFactory.addCreateMenuItem(fileMenu);

		MenuItem openMenuItem = MenuFactory.createOpenMenuItem(fileMenu);

		Menu openSubMenu = openMenuItem.getMenu();
		MenuFactory.addOpenTorrentMenuItem(openSubMenu);
		MenuFactory.addOpenURIMenuItem(openSubMenu);
		MenuFactory.addOpenTorrentForTrackingMenuItem(openSubMenu);
		MenuFactory.addOpenVuzeFileMenuItem(openSubMenu);

		MenuItem shareMenuItem = MenuFactory.createShareMenuItem(fileMenu);

		Menu shareSubMenu = shareMenuItem.getMenu();
		MenuFactory.addShareFileMenuItem(shareSubMenu);
		MenuFactory.addShareFolderMenuItem(shareSubMenu);
		MenuFactory.addShareFolderContentMenuItem(shareSubMenu);
		MenuFactory.addShareFolderContentRecursiveMenuItem(shareSubMenu);

		MenuFactory.addSearchMenuItem(fileMenu);

		MenuFactory.addSeparatorMenuItem(fileMenu);
		MenuFactory.addImportMenuItem(fileMenu);
		MenuFactory.addExportMenuItem(fileMenu);

		MenuFactory.addSeparatorMenuItem(fileMenu);
		MenuFactory.addCloseWindowMenuItem(fileMenu);
		MenuFactory.addCloseTabMenuItem(fileMenu);
		MenuFactory.addCloseDetailsMenuItem(fileMenu);
		MenuFactory.addCloseDownloadBarsToMenu(fileMenu);

		/*
		 * No need for restart and exit on OSX in the File menu since it is moved to the gobla application
		 */
		if (!Constants.isOSX) {
			MenuFactory.addSeparatorMenuItem(fileMenu);
			MenuFactory.addRestartMenuItem(fileMenu);
			MenuFactory.addExitMenuItem(fileMenu);
		}

	}

	/**
	 * Creates the Transfer menu and all its children
	 */
	private void addTransferMenu() {
		MenuFactory.createTransfersMenuItem(menuBar);
	}

	/**
	 * Creates the View menu and all its children
	 * @param parent
	 */
	private void addViewMenu(final Shell parent) {
		try {
			MenuItem viewItem = MenuFactory.createViewMenuItem(menuBar);
			final Menu viewMenu = viewItem.getMenu();

			viewMenu.addListener(SWT.Show, new Listener() {
				@Override
				public void handleEvent(Event event) {
					Utils.disposeSWTObjects(viewMenu.getItems());
					buildSimpleViewMenu(viewMenu);
				}
			});
		} catch (Exception e) {
			Debug.out("Error creating View Menu", e);
		}
	}

	protected void buildSimpleViewMenu(final Menu viewMenu) {
		try {
			MenuFactory.addMyTorrentsMenuItem(viewMenu);
			MenuFactory.addMyTrackerMenuItem(viewMenu);
			MenuFactory.addMySharesMenuItem(viewMenu);
			MenuFactory.addViewToolbarMenuItem(viewMenu);
			MenuFactory.addTransferBarToMenu(viewMenu);
			//MenuFactory.addAllPeersMenuItem(viewMenu);
			MenuFactory.addClientStatsMenuItem(viewMenu);
			MenuFactory.addPairingMenuItem(viewMenu);
			//MenuFactory.addDetailedListMenuItem(viewMenu);
			//MenuFactory.addDeviceManagerMenuItem(viewMenu);
			MenuFactory.addSubscriptionMenuItem(viewMenu);

			boolean enabled = COConfigurationManager.getBooleanParameter("Beta Programme Enabled");
			if (enabled) {
				
				MenuFactory.addSeparatorMenuItem(viewMenu);
				
				MenuFactory.addMenuItem(viewMenu, SWT.CHECK, "MainWindow.menu.view.beta",
						new Listener() {
							@Override
							public void handleEvent(Event event) {
								MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();
          			MdiEntry entry = mdi.createEntryFromSkinRef(null,
          					"BetaProgramme", "main.area.beta",
          					"{Sidebar.beta.title}", null, null,
          					true, "");

          			entry.setImageLeftID( "image.sidebar.beta" );
          			mdi.showEntry(entry);
							}
				});
			}

			MenuFactory.addSeparatorMenuItem(viewMenu);
			
			if (PluginsMenuHelper.buildViewMenu(viewMenu) && Constants.isOSX) {
				MenuFactory.addSeparatorMenuItem(viewMenu);
			}

			/*
			 * These 4 menus resides on the Tools menu on non-OSX platforms;
			 * since the Tools menu is not present in the OSX version these menus are added here to the View menu
			 */
			if (Constants.isOSX) {
				MenuFactory.addConsoleMenuItem(viewMenu);
				MenuFactory.addStatisticsMenuItem(viewMenu);
				MenuFactory.addSpeedLimitsToMenu(viewMenu);

				PluginsMenuHelper.buildToolsMenu(viewMenu);
			}

		} catch (Exception e) {
			Debug.out("Error creating View Menu", e);
		}

	}

	/**
	 * Creates the Torrent menu and all its children
	 * @param parent
	 */
	private void addTorrentMenu(final Shell parent) {

		/*
		 * The Torrents menu is now a user-configured option
		 */
		if (COConfigurationManager.getBooleanParameter("show_torrents_menu")) {
			MenuFactory.createTorrentMenuItem(menuBar);
		}

	}

	/**
	 * Creates the Tools menu and all its children
	 * @param parent
	 */
	private void addToolsMenu() {
		MenuItem item = MenuFactory.createToolsMenuItem(menuBar);
		MenuBuildUtils.addMaintenanceListenerForMenu(item.getMenu(),
				(toolsMenu, menuEvent) -> {

					MenuFactory.addBlockedIPsMenuItem(toolsMenu);
					MenuFactory.addConsoleMenuItem(toolsMenu);
					MenuFactory.addStatisticsMenuItem(toolsMenu);
					MenuFactory.addSpeedLimitsToMenu(toolsMenu);
					MenuFactory.addNatTestMenuItem(toolsMenu);
					MenuFactory.addSpeedTestMenuItem(toolsMenu);

					PluginsMenuHelper.buildToolsMenu(toolsMenu);

					MenuFactory.addSeparatorMenuItem(toolsMenu);
					MenuFactory.addConfigWizardMenuItem(toolsMenu);
					MenuFactory.addOptionsMenuItem(toolsMenu);
				}, true);
	}

	/**
	 * Creates the Plugins menu and all its children
	 * @param parent
	 */
	private void addPluginsMenu(final Shell parent) {
		MenuFactory.createPluginsMenuItem(menuBar,true);
	}

	private void addCommunityMenu( final Shell parent ) {
		MenuFactory.createCommunityMenuItem( menuBar );
	}
	
	/**
	 * Creates the Help menu and all its children
	 * @param parent
	 */
	private void addV2HelpMenu(final Shell parent) {
		MenuItem helpItem = MenuFactory.createHelpMenuItem(menuBar);

		Menu helpMenu = helpItem.getMenu();

		if (!Constants.isOSX) {
			MenuFactory.addAboutMenuItem(helpMenu);
			MenuFactory.addSeparatorMenuItem(helpMenu);
		}

		MenuFactory.addReleaseNotesMenuItem(helpMenu);
		MenuFactory.addWhatsNewMenuItem(helpMenu);

		MenuFactory.addWikiMenuItem(helpMenu);
		MenuFactory.addGetPluginsMenuItem(helpMenu);

		MenuFactory.addSeparatorMenuItem(helpMenu);

		if (!SystemProperties.isJavaWebStartInstance()) {
			MenuFactory.addCheckUpdateMenuItem(helpMenu);
			MenuFactory.addBetaMenuItem(helpMenu);
		}
		MenuFactory.addDonationMenuItem(helpMenu);

		MenuFactory.addSeparatorMenuItem(helpMenu);
		MenuFactory.addAdvancedHelpMenuItem(helpMenu);
		MenuFactory.addDebugHelpMenuItem(helpMenu);

	}

	@Override
	public Menu getMenu(String id) {
		if (IMenuConstants.MENU_ID_MENU_BAR.equals(id)) {
			return menuBar;
		}
		return MenuFactory.findMenu(menuBar, id);
	}
}
