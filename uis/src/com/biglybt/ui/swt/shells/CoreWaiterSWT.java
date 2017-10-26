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

package com.biglybt.ui.swt.shells;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.ui.swt.UIFunctionsSWT;
import org.eclipse.swt.widgets.Shell;

import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.AEThread2;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.Utils;

import com.biglybt.ui.swt.UIFunctionsManagerSWT;

public class CoreWaiterSWT
{
	private static final boolean DEBUG = false;

	public enum TriggerInThread {
		SWT_THREAD, ANY_THREAD, NEW_THREAD
	}

	private static boolean	startupAbandoned;
	
	private static Shell shell;

	public static void waitForCoreRunning(final CoreRunningListener l) {
		waitForCore(TriggerInThread.SWT_THREAD, l);
	}

	public static void waitForCore(final TriggerInThread triggerInThread,
			final CoreRunningListener l) {
		CoreFactory.addCoreRunningListener(new CoreRunningListener() {
			@Override
			public void coreRunning(final Core core) {
				if (triggerInThread == TriggerInThread.ANY_THREAD) {
					l.coreRunning(core);
				} else if (triggerInThread == TriggerInThread.NEW_THREAD) {
					new AEThread2("CoreWaiterInvoke", true) {
						@Override
						public void run() {
							l.coreRunning(core);
						}
					}.start();
				}
				Utils.execSWTThread(new AERunnable() {
					@Override
					public void runSupport() {
						// TODO: Need to detect cancel (can't rely on shell status since it may never open)
						if (shell != null && !shell.isDisposed()) {
							shell.dispose();
							shell = null;
						}

						if (triggerInThread == TriggerInThread.SWT_THREAD) {
							l.coreRunning(core);
						}
					}
				});
			}
		});

		if (!CoreFactory.isCoreRunning()) {
			if (DEBUG) {
				System.out.println("NOT AVAIL FOR " + Debug.getCompressedStackTrace());
			}
			Utils.execSWTThread(new AERunnable() {
				@Override
				public void runSupport() {
					showWaitWindow();
				}
			});
		} else if (DEBUG) {
			System.out.println("NO NEED TO WAIT.. CORE AVAIL! "
					+ Debug.getCompressedStackTrace());
		}

	}

	public static void
	startupAbandoned()
	{
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				startupAbandoned = true;
			
				if ( shell != null ) {
					shell.dispose();
				}
			}});
	}
	
	private static void showWaitWindow() {
		if ( startupAbandoned ) {
			return;
		}
		if (shell != null && !shell.isDisposed()) {
			shell.forceActive();
			return;
		}

		UIFunctionsSWT uiFunctionsSWT = UIFunctionsManagerSWT.getUIFunctionsSWT();
		if (uiFunctionsSWT != null) {
			shell = uiFunctionsSWT.showCoreWaitDlg();
		}
	}
}
