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

package com.biglybt.ui.swt.osx;

import java.lang.reflect.Field;

import com.biglybt.core.Core;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

import com.biglybt.platform.macosx.access.jnilib.OSXAccess;
import com.biglybt.ui.swt.Utils;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;

public class OSXFileOpen
{

	public static void fileOpen(String[] files) {
		for (String file : files) {
			fileOpen(file);
		}
	}

	public static void fileOpen(final String file) {
		CoreFactory.addCoreRunningListener(new CoreRunningListener() {
			@Override
			public void coreRunning(Core core) {
				UIFunctionsManagerSWT.getUIFunctionsSWT().openTorrentOpenOptions(
						Utils.findAnyShell(), null, new String[] { file }, false, false);
			}
		});
	}

	/**
	 * Called by OSXAccess by reflection to do some SWT hooks
	 *
	 * @since 1.0.0.0
	 */
	public static void initLight() {
		try {
  		Display display = new Display();

  		// hook opendoc
  		try {
  			Field fldOpenDoc = SWT.class.getDeclaredField("OpenDocument");
  			int SWT_OpenDocument = fldOpenDoc.getInt(null);

  			display.addListener(SWT_OpenDocument, new Listener() {
  				@Override
				  public void handleEvent(final Event event) {
  					try {
  						OSXAccess.passParameter(event.text);
  					} catch (Throwable e) {
  					}
  				}
  			});
  		} catch (Throwable t) {
  		}

  		// OpenDoc will trigger almost immediately (in first loop).  However,
  		// do it for at least 300ms just in case
  		for (int i = 0; i < 10; i ++) {
  			while (display.readAndDispatch()) {
  			}
  			Thread.sleep(30);
  		}
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}
}
