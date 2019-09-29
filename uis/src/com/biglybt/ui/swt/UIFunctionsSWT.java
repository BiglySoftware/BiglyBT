/*
 * Created on Jul 12, 2006 3:11:00 PM
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
package com.biglybt.ui.swt;

import java.util.Map;

import org.eclipse.swt.widgets.Shell;
import com.biglybt.pif.ui.toolbar.UIToolBarManager;

import com.biglybt.ui.swt.mainwindow.IMainMenu;
import com.biglybt.ui.swt.mainwindow.IMainStatusBar;
import com.biglybt.ui.swt.mainwindow.IMainWindow;
import com.biglybt.ui.swt.pif.UISWTInstance;
import com.biglybt.ui.swt.pif.UISWTView;

import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.swt.mdi.MultipleDocumentInterfaceSWT;
import com.biglybt.ui.swt.pifimpl.UISWTViewBuilderCore;

/**
 * @author TuxPaper
 * @created Jul 12, 2006
 *
 */
public interface UIFunctionsSWT
	extends UIFunctions
{
	public Shell getMainShell();

	/**
	 *
	 */
	public void closeDownloadBars();

	public boolean isGlobalTransferBarShown();

	public void showGlobalTransferBar();

	public void closeGlobalTransferBar();

	/**
	 * @return
	 */
	public UISWTView[] getPluginViews();

	public void openPluginView(UISWTViewBuilderCore builder, boolean bSetFocus);

	public UISWTInstance getUISWTInstance();

	public void refreshTorrentMenu();

	public IMainStatusBar getMainStatusBar();

	/**
	 * Creates the main application menu and attach it to the given <code>Shell</code>;
	 * this is only used for OSX so that we can attach the global menu to popup dialogs which
	 * is the expected behavior on OSX.  Windows and Linux do not require this since they do not have
	 * a global menu and because their main menu is already attached to the main application window.
	 * @param shell
	 * @return
	 */
	public IMainMenu createMainMenu(Shell shell);

	public IMainWindow getMainWindow();

	/**
	 *
	 *
	 * @since 3.1.1.1
	 */
	public void closeAllDetails();

	/**
	 * @return
	 *
	 * @since 3.1.1.1
	 */
	public boolean hasDetailViews();

	public Shell showCoreWaitDlg();

	public MultipleDocumentInterfaceSWT getMDISWT();

	public void promptForSearch();

	public UIToolBarManager getToolBarManager();

	public void setHideAll( boolean hidden );

	public boolean getHideAll();
	
	/**
	 *
	 * @since 5.0.0.1
	 */
	public void openTorrentWindow();

	/**
	 * @since 5.0.0.1
	 */
	public void openTorrentOpenOptions(Shell shell, String sPathOfFilesToOpen,
			String[] sFilesToOpen, boolean defaultToStopped, boolean forceOpen);

	/**
	 * @since 5.6.0.1
	 * @param shell
	 * @param sPathOfFilesToOpen
	 * @param sFilesToOpen
	 * @param options	See UIFunctions constants
	 */
	public void
	openTorrentOpenOptions(
		Shell 					shell,
		String 					sPathOfFilesToOpen,
		String[] 				sFilesToOpen,
		Map<String,Object>		options );

}
