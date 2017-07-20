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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.*;

/**
 * @author TuxPaper
 * @created Mar 21, 2007
 *
 */
public class UISwitcherUtil
{
	private static final long UPTIME_NEWUSER = 60 * 60 * 1; // 1 hour

	private static ArrayList listeners = new ArrayList();

	private static String switchedToUI = null;

	public static void addListener(UISwitcherListener l) {
		listeners.add(l);
		if (switchedToUI != null) {
			triggerListeners(switchedToUI);
		}
	}

	public static void removeListener(UISwitcherListener l) {
		listeners.remove(l);
	}

	public static void openSwitcherWindow() {
		_openSwitcherWindow();
	}

	public static void triggerListeners(String ui) {
		Object[] array = listeners.toArray();
		for (int i = 0; i < array.length; i++) {
			UISwitcherListener l = (UISwitcherListener) array[i];
			l.uiSwitched(ui);
		}
	}

	public static String calcUIMode() {
		// Can't use Constants.isSafeMode - it's not set by the time we
		// get here.
		if ("1".equals(System.getProperty(SystemProperties.SYSPROP_SAFEMODE))) {
			// If we are in safe-mode, prefer the classic UI - less likely to cause problems.
			return "az2";
		}

		String lastUI = COConfigurationManager.getStringParameter("ui", "az2");
		COConfigurationManager.setParameter("lastUI", lastUI);

		String forceUI = System.getProperty("force.ui");
		if (forceUI != null) {
			COConfigurationManager.setParameter("ui", forceUI);
			return forceUI;
		}

		// Flip people who install this client over top of an existing az
		// to az3ui.  The installer will write a file to the program dir,
		// while an upgrade won't
		boolean installLogExists = FileUtil.getApplicationFile("installer.log").exists();
		boolean alreadySwitched = COConfigurationManager.getBooleanParameter(
				"installer.ui.alreadySwitched", false);
		if (!alreadySwitched && installLogExists) {
			COConfigurationManager.setParameter("installer.ui.alreadySwitched", true);
			COConfigurationManager.setParameter("ui", "az3");
			COConfigurationManager.setParameter("az3.virgin.switch", true);

			return "az3";
		}

		boolean asked = COConfigurationManager.getBooleanParameter( "ui.asked", false );

		if ( asked || COConfigurationManager.hasParameter("ui", true)){

			return COConfigurationManager.getStringParameter("ui", "az3");
		}

		COConfigurationManager.setParameter("ui", "az3");

		return( "az3" );
	}

	public static void _openSwitcherWindow() {
		Class uiswClass = null;
		try {
			uiswClass = Class.forName("com.biglybt.ui.swt.shells.uiswitcher.UISwitcherWindow");
		} catch (ClassNotFoundException e1) {
		}
		if (uiswClass == null) {
			return;
		}

		// either !asked or forceAsked at this point

		try {

			final Constructor constructor = uiswClass.getConstructor(new Class[] {});

			Object object = constructor.newInstance(new Object[] {});

			Method method = uiswClass.getMethod("open", new Class[] {});

			method.invoke(object, new Object[] {});

		} catch (Exception e) {
			Debug.printStackTrace(e);
		}

		return;
	}
}
