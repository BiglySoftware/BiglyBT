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

package com.biglybt.ui.common;

import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.config.COConfigurationManager;

/**
 * Remembers Decisions (usually from message boxes)
 *
 * @author TuxPaper
 * @created May 6, 2007
 *
 */
public class RememberedDecisionsManager
{
	static{
		COConfigurationManager.addResetToDefaultsListener(
			new COConfigurationManager.ResetToDefaultsListener()
			{
				@Override
				public void
				reset()
				{
					clearAll();
				}
			});
	}

	public static void
	ensureLoaded()
	{
	}

	public static void
	clearAll()
	{
		COConfigurationManager.setParameter("MessageBoxWindow.decisions", new HashMap());
	}

	public static int getRememberedDecision(String id) {
		return getRememberedDecision(id, -1);
	}

	public static int getRememberedDecision(String id, int onlyIfInMask) {
		if (id == null || onlyIfInMask == 0) {
			return -1;
		}
		Map remembered_decisions = COConfigurationManager.getMapParameter(
				"MessageBoxWindow.decisions", new HashMap());

		Long l = (Long) remembered_decisions.get(id);
		//System.out.println("getR " + id + " -> " + l);
		if (l != null) {
			int i = l.intValue();
			if (onlyIfInMask == -1 || (i & onlyIfInMask) != 0) {
				return i;
			}
		}

		return -1;
	}


	/**
	 * Set a remembered value
	 *
	 * @param id remember id
	 * @param value value to store.  -1 to remove
	 *
	 * @since 3.0.1.3
	 */
	public static void setRemembered(String id, int value) {
		if (id == null) {
			return;
		}

		Map remembered_decisions = COConfigurationManager.getMapParameter(
				"MessageBoxWindow.decisions", new HashMap());

		if (value == -1) {
			remembered_decisions.remove(id);
		} else {
			remembered_decisions.put(id, new Long(value));
		}

		// System.out.println("setR " + id + " -> " + value);
		COConfigurationManager.setParameter("MessageBoxWindow.decisions",
				remembered_decisions);
		COConfigurationManager.save();
	}
}
