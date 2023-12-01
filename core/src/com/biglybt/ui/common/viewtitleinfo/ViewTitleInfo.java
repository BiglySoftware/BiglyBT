/*
 * Created on Jul 8, 2008
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.ui.common.viewtitleinfo;


/**
 * @author TuxPaper
 * @created Jul 8, 2008
 *
 */
public interface ViewTitleInfo
{
	public static int TITLE_TEXT = 5;

	public static int TITLE_INDICATOR_TEXT = 0;

	/**
	 * Return int[3] of {R, G, B} for color of Indicator Bubble
	 */
	public static int TITLE_INDICATOR_COLOR = 8;


	public static int TITLE_ACTIVE_STATE = 9;	// -> Long: 0 - not supported; 1 - active; 2 - inactive

	public static int TITLE_INDICATOR_TEXT_TOOLTIP = 1;

	public static int TITLE_IMAGEID = 2;

	public static int TITLE_IMAGE_TOOLTIP = 3;

	//Not Used Anymore
	//public static int TITLE_LOGID	= 7;

	/**
	 * Return an exportable version of the datasource.  Usually String, but
	 * can be Map and List
	 */
	public static int TITLE_EXPORTABLE_DATASOURCE	= 10;

	public static int TITLE_TEXT_ID = 11;	// String, resource id for title if relevant

	/**
	 *
	 * @param propertyID TITLE_*
	 * @return value, or null if you don't want to set it
	 *
	 * @since 3.1.1.1
	 */
	public Object getTitleInfoProperty(int propertyID);
}
