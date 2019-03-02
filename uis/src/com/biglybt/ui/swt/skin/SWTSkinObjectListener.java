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

/**
 * Allows monitoring of {@link SWTSkinObject}'s events
 *
 * @author TuxPaper
 * @created Sep 30, 2006
 *
 */
public interface SWTSkinObjectListener
{
	/**
	 * Skin Object was shown
	 */
	public static int EVENT_SHOW = 0;

	/**
	 * Skin Object was hidden
	 */
	public static int EVENT_HIDE = 1;

	/**
	 * Skin Object was selected (activated)
	 */
	public static int EVENT_SELECT = 2;

	/**
	 * Skin Object was destroyed
	 */
	public static int EVENT_DESTROY = 3;

	/**
	 * Skin Object was created.  All children are guaranteed to be created.
	 */
	public static int EVENT_CREATED = 4;

	/**
	 * skinObject will be null, params will be an array { View ID, Config ID }
	 * function who creates the object should return a SWTSkinObject
	 */
	public static int EVENT_CREATE_REQUEST = 5;

	/**
	 * skinObject needs to update any text
	 */
	public static int EVENT_LANGUAGE_CHANGE = 6;

	public static int EVENT_DATASOURCE_CHANGED = 7;
	
	public static int EVENT_OBFUSCATE = 8;


	/**
	 * Friendly names of events, useful for debug
	 */
	public static String[] NAMES = {
		"Show",
		"Hide",
		"Select",
		"Destroy",
		"Created",
		"Create Request",
		"Lang Change",
		"DS Change",
		"Obfuscate"
	};

	/**
	 * Called when an event occurs
	 *
	 * @param skinObject skin object the event occurred on
	 * @param eventType EVENT_* constant
	 * @param params Any parameters the event needs to send you
	 */
	public Object eventOccured(SWTSkinObject skinObject, int eventType,
			Object params);
}
