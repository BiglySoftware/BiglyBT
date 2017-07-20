/*
 * Created on 27-Apr-2004
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

package com.biglybt.pif.ui.components;

/**
 * @author parg
 *
 */
public interface
UITextArea
	extends UIComponent

{
	public static final int	DEFAULT_MAX_SIZE	= 60000;

	/**
	 * Setting values results in a PT_VALUE property change with a String value
	 * @param precentage
	 */

	public void
	setText(
		String		text );

	/**
	 * Appends the supplied text to the existing text value
	 * @param text
	 */

	public void
	appendText(
		String		text );

	public String
	getText();

	/**
	 * Limits the maximum size of text held by the area. When then size is exceeded the text
	 * will be truncated (text at the start of the string is removed, NOT the end)
	 * All areas have a default max size as defined by the constant above
	 */

	public void
	setMaximumSize(
		int	max_size );
}
