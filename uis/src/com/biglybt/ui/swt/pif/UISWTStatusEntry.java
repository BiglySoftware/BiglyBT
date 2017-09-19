/*
 * Created on 03-Feb-2007
 * Created by Allan Crooks
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
package com.biglybt.ui.swt.pif;

import com.biglybt.pif.ui.menus.MenuContext;
import com.biglybt.pif.ui.menus.MenuManager;

import org.eclipse.swt.graphics.Image;

/**
 * This interface represents a status entry indicator in the status bar. Examples
 * of such indicators are the <i>Share Ratio</i> indicator and the <i>DHT Status</i>
 * indicator. Plugins can create their own indicators via {@link UISWTInstance#createStatusEntry()}.
 * <p>
 * <b>Note:</b> When a status entry is first created, it is set to be invisible, with no status
 * text or tool tip text and no image to be associated with it. Once you set some content, you
 * <b>must</b> call <tt>setVisible(true)</tt> for it to become visible.
 *
 * @see UISWTInstance#createStatusEntry()
 * @author amc1
 * @since 1.0.0.0
 */
public interface UISWTStatusEntry {

	public static final int IMAGE_LED_GREY = 0;
	public static final int IMAGE_LED_RED = 1;
	public static final int IMAGE_LED_YELLOW = 2;
	public static final int IMAGE_LED_GREEN = 3;

	/**
	 * Toggles the visibility of the entry in the status bar.
	 */
	public void setVisible(boolean visible);

	/**
	 * Sets the text to display in the status bar. If you want to prevent any text
	 * being displayed, pass <tt>null</tt> as a parameter.
	 */
	public void setText(String text);

	/**
	 * Sets the tooltip text to associate with the status bar. If you want to remove
	 * any tooltip text, pass <tt>null</tt> as a parameter.
	 */
	public void setTooltipText(String text);

	/**
	 * Sets a listener to be informed when the status entry has been clicked on.
	 */
	public void setListener(UISWTStatusEntryListener listener);

	/**
	 * Indicates whether an image should be displayed or not.
	 */
	public void setImageEnabled(boolean enabled);

	/**
	 * Sets the image to display - the value here must be one of the <tt>IMAGE_</tt>
	 * values defined above. You must call {@link #setImageEnabled(boolean)} to enable
	 * images to be displayed for this status entry.
	 */
	public void setImage(int image_id);

	/**
	 * Sets the image to display. You must call {@link #setImageEnabled(boolean)} to enable
	 * images to be displayed for this status entry.
	 */
	public void setImage(Image image);

	/**
	 * Destroys the status entry.
	 */
	public void destroy();

	/**
	 * Returns a menu context object to allow you to add menu items to the status entry.
	 *
	 * @see MenuManager#addMenuItem(MenuContext, String)
	 * @since 1.0.0.0
	 */
	public MenuContext getMenuContext();

}
