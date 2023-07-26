/*
 * Created on Jun 21, 2006 1:22:57 PM
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
package com.biglybt.ui.swt.skin;

import java.util.ArrayList;

/**
 * @author TuxPaper
 * @created Jun 21, 2006
 *
 */
public class SWTSkinObjectTab
	extends SWTSkinObjectContainer
{
	SWTSkinObject[] activeWidgets = null;

	SWTSkinObject activeWidgetsParent;

	SWTSkinTabSet tabset;

	public SWTSkinObjectTab(SWTSkin skin, SWTSkinProperties properties,
			String sID, String sConfigID, SWTSkinObject parent) {
		super(skin, properties, sID, sConfigID, parent);
		type = "tab";
	}

	public String[] getActiveWidgetIDs() {
		String[] sIDs = properties.getStringArray(getConfigID() + ".active-widgets");
		return sIDs;
	}

	public SWTSkinObject[] getActiveWidgets(boolean create) {
		if (activeWidgets == null) {

			String[] sIDs = getActiveWidgetIDs();
			ArrayList skinObjectArray = new ArrayList();

			if (sIDs != null) {
				for (int i = 0; i < sIDs.length; i++) {
					//					System.out.println("Looking for " + sIDs[i] + " w/Parent "
					//							+ activeWidgetsParent);
					SWTSkinObject skinObject = getSkin().getSkinObjectByID(sIDs[i],
							activeWidgetsParent);

					if (skinObject == null && create) {
						SWTSkinObject soParent = skin.getSkinObjectByID(
								properties.getStringValue(getConfigID() + ".contentarea",
										(String) null), activeWidgetsParent);
						if (soParent != null) {
							skinObject = skin.createSkinObject(sIDs[i], sIDs[i], soParent);
							skin.layout();
							skin.getShell().layout(true, true);
						}
					}

					if (skinObject != null) {
						skinObjectArray.add(skinObject);
					}
				}
			}

			if (skinObjectArray.size() == 0) {
				return new SWTSkinObject[0];
			}

			activeWidgets = new SWTSkinObject[skinObjectArray.size()];
			activeWidgets = (SWTSkinObject[]) skinObjectArray.toArray(activeWidgets);
		}

		return activeWidgets;
	}

	public void setActiveWidgets(SWTSkinObject[] skinObjects) {
		activeWidgets = skinObjects;
	}

	/**
	 * Retrieve the parent skin object to which the active widgets belong to.
	 *
	 * @return Parent skin object, or null if it doesn't matter
	 */
	public SWTSkinObject getActiveWidgetsParent() {
		return activeWidgetsParent;
	}

	/**
	 * Sets the parent skin object to which the active widgets belong to.
	 * <P>
	 * This is useful when there are multiple widgets with the same ID
	 *
	 * @param activeWidgetsParent
	 */
	public void setActiveWidgetsParent(SWTSkinObject activeWidgetsParent) {
		this.activeWidgetsParent = activeWidgetsParent;
		activeWidgets = null;
	}

	public SWTSkinTabSet getTabset() {
		return tabset;
	}

	public void setTabset(SWTSkinTabSet tabset) {
		this.tabset = tabset;
	}
}
