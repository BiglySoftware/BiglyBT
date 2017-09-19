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

package com.biglybt.ui.swt.pifimpl;

import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;

import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.AERunnable;
import com.biglybt.pifimpl.local.ui.menus.MenuContextImpl;
import com.biglybt.ui.common.util.MenuItemManager;
import com.biglybt.ui.swt.MenuBuildUtils;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.MainStatusBar;
import com.biglybt.ui.swt.mainwindow.MainStatusBar.CLabelPadding;
import com.biglybt.ui.swt.pif.UISWTStatusEntry;
import com.biglybt.ui.swt.pif.UISWTStatusEntryListener;

import com.biglybt.ui.swt.imageloader.ImageLoader;

import com.biglybt.pif.ui.menus.MenuContext;
import com.biglybt.pif.ui.menus.MenuItem;

/**
 * @author Allan Crooks
 *
 */
public class UISWTStatusEntryImpl implements UISWTStatusEntry, MainStatusBar.CLabelUpdater {

	private AEMonitor this_mon = new AEMonitor("UISWTStatusEntryImpl@" + Integer.toHexString(this.hashCode()));

	private UISWTStatusEntryListener listener = null;
	private MenuContextImpl menu_context = MenuContextImpl.create("status_entry");

	// Used by "update".
	private boolean needs_update = false;
	private boolean needs_layout = false;
	private String text = null;
	private String tooltip = null;
	private boolean image_enabled = false;
	private Image original_image = null;
	private boolean	check_scaled_image;
	private Image scaled_image = null;
	private boolean is_visible = false;
	private boolean needs_disposing = false;
	private boolean is_destroyed = false;

	private Menu menu;

	private CopyOnWriteArrayList<String> imageIDstoDispose = new CopyOnWriteArrayList<>();
	private String imageID = null;

	private void checkDestroyed() {
		if (is_destroyed) {throw new RuntimeException("object is destroyed, cannot be reused");}
	}

	@Override
	public MenuContext getMenuContext() {
		return this.menu_context;
	}

	@Override
	public boolean update(CLabelPadding label) {
		if (needs_disposing && !label.isDisposed()) {
			if (menu != null && !menu.isDisposed()) {
				menu.dispose();
				menu = null;
			}
			label.dispose();

			if (imageID != null) {
				imageIDstoDispose.add(imageID);
			}
			releaseOldImages();
			if ( scaled_image != null){
				scaled_image.dispose();
				scaled_image = null;
			}

			return( true );
		}

		boolean do_layout = needs_layout;

		needs_layout = false;

		if (menu_context.is_dirty) {needs_update = true; menu_context.is_dirty = false;}
		if (!needs_update) {return do_layout;}

		// This is where we do a big update.
		try {
			this_mon.enter();
			update0(label);
		}
		finally {
			this_mon.exit();
		}

		return do_layout;
	}

	/**
	 *
	 *
	 * @since 1.0.0.0
	 */
	private void releaseOldImages() {
		if (imageIDstoDispose.size() > 0) {
			ImageLoader imageLoader = ImageLoader.getInstance();

			for (Iterator iter = imageIDstoDispose.iterator(); iter.hasNext();) {
				String id = (String) iter.next();
				imageLoader.releaseImage(id);
				iter.remove();
			}
		}
	}

	private void update0(CLabelPadding label) {
		label.setText(text);
		label.setToolTipText(tooltip);
		if ( check_scaled_image ){
			check_scaled_image = false;
			if ( scaled_image != null ){
				scaled_image.dispose();
				scaled_image = null;
			}
			if ( original_image != null &&  Utils.adjustPXForDPIRequired( original_image )){
				scaled_image = Utils.adjustPXForDPI( label.getDisplay(), original_image );
			}
		}
		label.setImage(image_enabled ? (scaled_image==null?original_image:scaled_image) : null);
		label.setVisible(this.is_visible);

		releaseOldImages();

		MenuItem[] items = MenuItemManager.getInstance().getAllAsArray(menu_context.context);
		if (items.length > 0 & menu == null) {
			menu = new Menu(label);
			label.setMenu(menu);

			MenuBuildUtils.addMaintenanceListenerForMenu(menu,
			    new MenuBuildUtils.MenuBuilder() {
					@Override
					public void buildMenu(Menu menu, MenuEvent menuEvent) {
						MenuItem[] items = MenuItemManager.getInstance().getAllAsArray(menu_context.context);
						MenuBuildUtils.addPluginMenuItems(items, menu, true, true,
							MenuBuildUtils.BASIC_MENU_ITEM_CONTROLLER);
					}
				}
			);
		}
		else if (menu != null && items.length == 0) {
			label.setMenu(null);
			if (!menu.isDisposed()) {menu.dispose();}
			this.menu = null;
		}

		this.needs_update = false;
	}

	void onClick() {
		UISWTStatusEntryListener listener0 = listener; // Avoid race conditions.
		if (listener0 != null) {listener.entryClicked(this);}
	}

	@Override
	public void destroy() {
		try {
			this_mon.enter();
			this.is_visible = false;
			this.listener = null;
			this.original_image = null;
			this.needs_disposing = true;
			this.is_destroyed = true;

			// Remove any existing menu items.
			MenuItemManager.getInstance().removeAllMenuItems(this.menu_context.context);
		}
		finally {
			this_mon.exit();
		}
	}

	@Override
	public void setImage(int image_id) {
		// we can't release the old image here because the label is still using it
		// Put it into a list until the label is updated with the new image, then
		// release the old
		if (imageID != null) {
			imageIDstoDispose.add(imageID);
		}

		switch (image_id) {
			case IMAGE_LED_GREEN:
				imageID = "greenled";
				break;
			case IMAGE_LED_RED:
				imageID = "redled";
				break;
			case IMAGE_LED_YELLOW:
				imageID = "yellowled";
				break;
			default:
				imageID = "grayled";
				break;
		}
		ImageLoader imageLoader = ImageLoader.getInstance();
		this.setImage(imageLoader.getImage(imageID));
	}

	@Override
	public void setImage(Image image) {
		checkDestroyed();
		this_mon.enter();
		if( image != this.original_image ){
			needs_layout = true;
			check_scaled_image = true;
			this.original_image = image;
		}
		this.needs_update = true;
		this_mon.exit();
	}

	@Override
	public void setImageEnabled(boolean enabled) {
		checkDestroyed();
		this_mon.enter();
		if ( enabled != image_enabled ){
			needs_layout = true;
		}
		this.image_enabled = enabled;
		this.needs_update = true;
		this_mon.exit();
	}

	@Override
	public void setListener(UISWTStatusEntryListener listener) {
		checkDestroyed();
		this.listener = listener;
	}

	@Override
	public void setText(String text) {
		checkDestroyed();
		this_mon.enter();
		this.text = text;
		this.needs_update = true;
		this_mon.exit();
	}

	@Override
	public void setTooltipText(String text) {
		checkDestroyed();
		this_mon.enter();
		this.tooltip = text;
		this.needs_update = true;
		this_mon.exit();
	}

	@Override
	public void setVisible(boolean visible) {
		checkDestroyed();
		this_mon.enter();
		if ( is_visible != visible ){
			needs_layout = true;
		}
		this.is_visible = visible;
		this.needs_update = true;
		this_mon.exit();
	}

	@Override
	public void created(final MainStatusBar.CLabelPadding label) {
		final Listener click_listener = new Listener() {
			@Override
			public void handleEvent(Event e) {
				onClick();
			}
		};

		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				label.addListener(SWT.MouseDoubleClick, click_listener);
			}
		}, true);
	}
}
