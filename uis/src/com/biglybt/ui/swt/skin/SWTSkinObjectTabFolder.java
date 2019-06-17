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

package com.biglybt.ui.swt.skin;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.widgets.Composite;

import com.biglybt.core.util.RegExUtil;

public class SWTSkinObjectTabFolder
	extends SWTSkinObjectContainer
{

	private CTabFolder tabFolder;

	public SWTSkinObjectTabFolder(SWTSkin skin, SWTSkinProperties properties,
			String sID, String sConfigID, SWTSkinObject parent) {
		super(skin, properties, null, sID, sConfigID, "tabfolder", parent);
		createTabFolder(null);
	}

	public SWTSkinObjectTabFolder(SWTSkin skin, SWTSkinProperties properties,
			String sID, String sConfigID, Composite createOn) {
		super(skin, properties, null, sID, sConfigID, "tabfolder", null);
		createTabFolder(createOn);
	}

	private void createTabFolder(Composite createOn) {
		if (createOn == null) {
  		if (parent == null) {
  			createOn = skin.getShell();
  		} else {
  			createOn = (Composite) parent.getControl();
  		}
		}

		int style = SWT.NONE;
		if (properties.getIntValue(sConfigID + ".border", 0) == 1) {
			style = SWT.BORDER;
		}

		String sStyle = properties.getStringValue("style");
		if (sStyle != null && sStyle.length() > 0) {
			String[] styles = RegExUtil.PAT_SPLIT_COMMA.split(sStyle);
			for (String aStyle : styles) {
				if (aStyle.equalsIgnoreCase("close")) {
					style |= SWT.CLOSE;
				}
			}
		}


		tabFolder = new CTabFolder(createOn, style);

		triggerListeners(SWTSkinObjectListener.EVENT_CREATED);
		setControl(tabFolder);
	}

	@Override
	protected boolean setIsVisible(boolean visible, boolean walkup) {
		boolean isVisible = superSetIsVisible(visible, walkup);
		// Todo: ensure correct tabfolder child comp is visible
		return isVisible;
	}

	@Override
	public void childAdded(SWTSkinObject soChild) {
//		super.childAdded(soChild);
//		CTabItem tabItem = new CTabItem(tabFolder, SWT.NONE);
//		tabItem.setText("WOW");
//		tabItem.setControl(soChild.getControl());
	}

	public CTabFolder getTabFolder() {
		return tabFolder;
	}
}
