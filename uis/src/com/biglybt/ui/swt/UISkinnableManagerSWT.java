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

import java.util.*;

/**
 * @author TuxPaper
 * @created Mar 18, 2007
 *
 * TODO: seperate into class/impl
 */
public class UISkinnableManagerSWT
{
	static UISkinnableManagerSWT instance = new UISkinnableManagerSWT();

	public static UISkinnableManagerSWT getInstance() {
		return instance;
	}

	/**
	 * Key: ID;
	 * Value: ArrayList of UISkinnableSWTListener
	 */
	private Map mapSkinnables = new HashMap();

	public UISkinnableSWTListener[] getSkinnableListeners(String id) {
		List listeners = (List) mapSkinnables.get(id);

		if (listeners == null) {
			return new UISkinnableSWTListener[0];
		}

		UISkinnableSWTListener[] skinListeners = new UISkinnableSWTListener[listeners.size()];
		skinListeners = (UISkinnableSWTListener[]) listeners.toArray(skinListeners);
		return skinListeners;
	}

	public void addSkinnableListener(String id, UISkinnableSWTListener l) {
		List listeners = (List) mapSkinnables.get(id);

		if (listeners == null) {
			listeners = new ArrayList();
			listeners.add(l);
			mapSkinnables.put(id, listeners);
		} else {
			listeners.add(l);
		}
	}

	public void removeSkinnableListener(String id, UISkinnableSWTListener l) {
		List listeners = (List) mapSkinnables.get(id);

		if (listeners != null) {
			listeners.remove(l);
		}
	}
}
