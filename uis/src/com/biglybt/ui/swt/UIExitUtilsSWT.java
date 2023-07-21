/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.biglybt.ui.swt;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.security.SESecurityManager;
import com.biglybt.core.util.*;
import com.biglybt.ui.swt.shells.MessageBoxShell;

/**
 * @author TuxPaper
 * @created Nov 6, 2006
 *
 */
public class UIExitUtilsSWT
{
	private static boolean skipCloseCheck = false;
	private static boolean skipCloseChecksForUpdate = false;

	private static CopyOnWriteList<canCloseListener>	listeners	= new CopyOnWriteList<>();

	public static void
	addListener(
		canCloseListener	l )
	{
		listeners.add( l );
	}

	public static void
	removeListener(
		canCloseListener	l )
	{
		listeners.remove( l );
	}

	public static void setSkipCloseCheck(boolean b) {
		skipCloseCheck = b;
	}

	public static void setSkipCloseChecksForUpdate(boolean b) {
		skipCloseChecksForUpdate = b;
	}

	
	/**
	 * @return
	 */
	public static boolean canClose(GlobalManager globalManager,
			boolean bForRestart) {
		if (skipCloseCheck) {
			return true;
		}

		if ( !skipCloseChecksForUpdate ){
			Shell mainShell = UIFunctionsManagerSWT.getUIFunctionsSWT().getMainShell();
			if (mainShell != null
					&& (!mainShell.isVisible() || mainShell.getMinimized())
					&& COConfigurationManager.getBooleanParameter("Password enabled")) {
	
				if (!PasswordWindow.showPasswordWindow(Display.getCurrent())) {
					return false;
				}
			}
	
	
			if (COConfigurationManager.getBooleanParameter("confirmationOnExit")) {
				if (!getExitConfirmation(bForRestart)) {
					return false;
				}
			}
		}
		
		for ( canCloseListener listener: listeners ){

			if ( !listener.canClose()){

				return( false );
			}
		}

		return true;
	}

	/**
	 * @return true, if the user chose OK in the exit dialog
	 *
	 * @author Rene Leonhardt
	 */
	private static boolean getExitConfirmation(boolean for_restart) {
		MessageBoxShell mb = new MessageBoxShell(SWT.ICON_WARNING | SWT.YES
				| SWT.NO, for_restart ? "MainWindow.dialog.restartconfirmation"
				: "MainWindow.dialog.exitconfirmation", (String[]) null);
		mb.open(null);

		return mb.waitUntilClosed() == SWT.YES;
	}

	public static void uiShutdown() {
		// problem with closing down web start as AWT threads don't close properly
		if (SystemProperties.isJavaWebStartInstance()) {

			Thread close = new AEThread("JWS Force Terminate") {
				@Override
				public void runSupport() {
					try {
						Thread.sleep(2500);

					} catch (Throwable e) {

						Debug.printStackTrace(e);
					}

					SESecurityManager.exitVM(1);
				}
			};

			close.setDaemon(true);

			close.start();
		}
	}

	public interface
	canCloseListener
	{
		public boolean
		canClose();
	}
}
