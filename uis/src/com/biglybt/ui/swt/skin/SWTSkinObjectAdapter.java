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

package com.biglybt.ui.swt.skin;

import com.biglybt.core.util.Debug;

/**
 * Converts {@link SWTSkinObjectListener} events to method calls
 *
 * @author TuxPaper
 * @created Sep 30, 2006
 *
 */
public class SWTSkinObjectAdapter
	implements SWTSkinObjectListener
{
	/**
	 * Skin Object was shown
	 */
	public Object skinObjectShown(SWTSkinObject skinObject, Object params) {
		return null;
	}

	/**
	 * Skin Object was hidden
	 */
	public Object skinObjectHidden(SWTSkinObject skinObject, Object params) {
		return null;
	}

	/**
	 * Skin Object was selected (activated)
	 */
	public Object skinObjectSelected(SWTSkinObject skinObject, Object params) {
		return null;
	}

	/**
	 * Skin Object was destroyed
	 */
	public Object skinObjectDestroyed(SWTSkinObject skinObject, Object params) {
		return null;
	}

	/**
	 * Skin Object was created.  All children are guaranteed to be created.
	 */
	public Object skinObjectCreated(SWTSkinObject skinObject, Object params) {
		return null;
	}

	/**
	 * skinObject needs to update any text
	 */
	public Object updateLanguage(SWTSkinObject skinObject, Object params) {
		return null;
	}

	public Object dataSourceChanged(SWTSkinObject skinObject, Object params) {
		return null;
	}

	/* (non-Javadoc)
	 * @see SWTSkinObjectListener#eventOccured(SWTSkinObject, int, java.lang.Object)
	 */
	@Override
	public Object eventOccured(SWTSkinObject skinObject, int eventType,
	                           Object params) {
		try {
			switch (eventType) {
				case EVENT_SHOW:
					return skinObjectShown(skinObject, params);

				case EVENT_HIDE:
					return skinObjectHidden(skinObject, params);

				case EVENT_SELECT:
					return skinObjectSelected(skinObject, params);

				case EVENT_DESTROY:
					return skinObjectDestroyed(skinObject, params);

				case EVENT_CREATED:
					return skinObjectCreated(skinObject, params);

				case EVENT_LANGUAGE_CHANGE:
					return updateLanguage(skinObject, params);

				case EVENT_DATASOURCE_CHANGED:
					return dataSourceChanged(skinObject, params);

				default:
					return null;
			}

		} catch (Exception e) {
			Debug.out("Skin Event " + NAMES[eventType] + " caused an error", e);
		}
		return null;
	}

}
