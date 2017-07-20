/*
 * Created on Jun 26, 2006 7:28:15 PM
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

import org.eclipse.swt.graphics.Color;

import com.biglybt.ui.skin.SkinProperties;

/**
 * Extends SkinProperties with SWT specific methods
 *
 * @author TuxPaper
 * @created Jun 26, 2006
 *
 */
public interface SWTSkinProperties
	extends SkinProperties
{

	/**
	 * Retrieve a color property in as a SWT Color
	 * @param name Property Name
	 * @return a Color, or null
	 */
	public Color getColor(String name);


	public Color getColor(String name, Color def);


	/**
	 * @param sID
	 * @return
	 *
	 * @since 4.4.0.7
	 */
	SWTColorWithAlpha getColorWithAlpha(String sID);


	/**
	 * Get value in px, adjusted for dpi
	 */
	public int getPxValue(String name, int def);
}