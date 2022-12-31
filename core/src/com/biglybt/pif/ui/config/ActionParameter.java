/*
 * Created on 17-Jun-2004
 * Created by Paul Gardner
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
 *
 */

package com.biglybt.pif.ui.config;

/**
 * Parameter representing an action. 
 * Not backed by a config key.
 * Usually a displayed as button or a link.
 * 
 * @see com.biglybt.pif.ui.model.BasicPluginConfigModel#addActionParameter2(String, String)
 */
public interface
ActionParameter
	extends Parameter
{
	/**
	 * Action Parameter will be styled as a button
	 *
	 * @since BiglyBT 1.0.0.0
	 */
	public static final int STYLE_BUTTON		= 1;

	/**
	 * Action Parameter will be styled as a hyperlink
	 *
	 * @since BiglyBT 1.0.0.0
	 */
	public static final int STYLE_LINK			= 2;

	/**
	 * Returns the messagebundle key for the action's text
	 *
	 * @since BiglyBT 1.0.0.0
	 */
	String
	getActionResource();

	/**
	 * Set the action's text
	 *
	 * @param action_resource messagebundle key
	 *
	 * @since BiglyBT 1.0.0.0
	 */
	void setActionResource(String action_resource);

	/**
	 * unique id for the action.  Used for UIs without widgets, like console ui
	 * <p/>
	 * By default, the actionid is the same as {@link #getActionResource()}
	 *
	 * @since BiglyBT 1.9.0.1
	 */
	String getActionID();

	/**
	 * Set unique id for the action.  Used for UIs without widgets, like console ui
	 *
	 * @since BiglyBT 1.9.0.1
	 */
	void setActionID(String actionID);

	public void
	setStyle(
		int		style );

	public int
	getStyle();
	
	public void
	setImageID(
		String	id );
	
	public String
	getImageID();
}
