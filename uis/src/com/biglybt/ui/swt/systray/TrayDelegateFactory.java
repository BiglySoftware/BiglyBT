/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.ui.swt.systray;

import java.lang.reflect.Method;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;

import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;

/**
 * Created by TuxPaper on 6/29/2017.
 */
public class TrayDelegateFactory
{

	static TrayDelegate getTray(Display display) {
		if (Constants.isUnix) {
			try {
				TrayDelegate delegate = (TrayDelegate) Class.forName(
						"com.biglybt.ui.swt.systray.TrayDork").newInstance();
				if (delegate != null) {
					Method mGetSystemTray = delegate.getClass().getDeclaredMethod("getSystemTray");
					Object o = mGetSystemTray.invoke(delegate);

					Method mGetMenu = o.getClass().getMethod("getMenu");
					Object menu = mGetMenu.invoke(o);
					if (menu != null) {
						return delegate;
					}
				}
			} catch (Throwable ignore) {
				Debug.outNoStack(ignore.getMessage());
			}
		}
		return new TraySWT(display);
	}

	static TrayItemDelegate createTrayItem(TrayDelegate tray) {
		if (Constants.isUnix && !(tray instanceof TraySWT)) {
			try {
				return (TrayItemDelegate) Class.forName(
						"com.biglybt.ui.swt.systray.TrayItemDork").getConstructor(
								TrayDelegate.class).newInstance(tray);
			} catch (Throwable ignore) {
			}
		}
		return new TrayItemSWT(tray, SWT.NONE);
	}
}
