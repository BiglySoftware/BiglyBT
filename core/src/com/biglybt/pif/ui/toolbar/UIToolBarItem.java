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

package com.biglybt.pif.ui.toolbar;

public interface UIToolBarItem
{
	public final static long STATE_ENABLED = 0x1;

	public static final long STATE_DOWN = 0x2;

	/**
	 * Retrieve the ID of the toolbar item
	 *
	 * @since 4.6.0.5
	 */
	public String getID();

	/**
	 * Return the message bundle ID for the button text
	 *
	 * @since 4.6.0.5
	 */
	public String getTextID();

	/**
	 * Sets the button's text to a messagebundle value looked up using the id
	 *
	 * @param id
	 * @since 4.6.0.5
	 */
	public void setTextID(String id);

	/**
	 * Get the ID of the image used
	 *
	 * @since 4.6.0.5
	 */
	public String getImageID();

	/**
	 * Sets the toolbar item to use the specified image
	 *
	 * @since 4.6.0.5
	 */
	public void setImageID(String id);

	/**
	 * Returns if the toolbar item is always available (enabled)
	 *
	 * @since 4.6.0.5
	 */
	public boolean isAlwaysAvailable();

	public long getState();

	public void setState(long state);

	public boolean triggerToolBarItem(long activationType, Object datasource);

	public void setDefaultActivationListener(
			UIToolBarActivationListener defaultActivation);

	/**
	 * @return Group that item belongs to
	 *
	 * @since 5.0.0.1
	 */
	public String getGroupID();

	/**
	 * @param groupID
	 *
	 * @since 5.0.0.1
	 */
	public void setGroupID(String groupID);

	/**
	 * @param string
	 *
	 * @since 5.0.0.1
	 */
	public void setToolTip(String text);

	/**
	 * @return
	 *
	 * @since 5.0.0.1
	 */
	String getToolTip();
	
	/**
	 * @since BiglyBT 1.2.0.1
	 * @param id
	 */
	
	public void
	setToolTipID(
		String		id );
	
	/**
	 * @since BiglyBT 1.2.0.1
	 * @param id
	 */
	
	public String
	getToolTipID();
}
