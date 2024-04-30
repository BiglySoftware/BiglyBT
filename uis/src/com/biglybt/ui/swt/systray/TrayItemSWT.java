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

import java.io.File;

import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.TrayItem;

import com.biglybt.ui.swt.MenuBuildUtils;
import com.biglybt.ui.swt.MenuBuildUtils.MenuBuilder;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.imageloader.ImageLoader;

/**
 * Created by TuxPaper on 6/29/2017.
 */
public class TrayItemSWT
	implements TrayItemDelegate
{
	private final TrayItem item;

	public TrayItemSWT(TrayDelegate tray, int style) {
		item = new TrayItem(((TraySWT) tray).getSWT(), style);
	}

	@Override
	public void setImage(String imageID, File file) {
		item.setImage(ImageLoader.getInstance().getImage(imageID));
	}

	@Override
	public void setVisible(boolean b) {
		item.setVisible(b);
	}

	@Override
	public boolean isVisible() {
		return item.getVisible();
	}

	@Override
	public void addListener(int id, Listener listener) {
		item.addListener(id, listener);
	}

	@Override
	public boolean isDisposed() {
		return item.isDisposed();
	}

	@Override
	public void dispose() {
		item.dispose();
	}

	@Override
	public void setToolTipText(String s) {
		Utils.setTT(item,s);
	}

	@Override
	public void setMenu(Menu menu, MenuBuilder menuBuilder) {
		MenuBuildUtils.addMaintenanceListenerForMenu(menu, menuBuilder);
		menuBuilder.buildMenu(menu, null);
	}

	@Override
	public String toString() {
		String s = getClass().getName() + "@" + Integer.toHexString(hashCode());
		if (item != null) {
			s += " " + item.getVisible() + ";" + item.getText() + ";" + item.getParent();
		}
		return "{" + s + "}";
	}
}
