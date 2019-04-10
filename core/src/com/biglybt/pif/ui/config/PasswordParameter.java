/*
 * Created on 10-Jun-2004
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
 * @author parg
 *
 */

public interface
PasswordParameter
	extends Parameter
{
	public static final int	ET_PLAIN		= 1;
	public static final int ET_SHA1			= 2;
	public static final int ET_MD5			= 3;

		// note that even for encoded parameters, an empty value ("") will be returned
		// as "" (not an encoded "")

	public byte[]
	getValue();

	public void
	setValue(
		String	plain_password );

	/**
	 * Set a width hint for displaying the text field
	 *
	 * @since BiglyBT 1.9.0.1
	 */
	void setWidthInCharacters(int widthInCharacters);

	/**
	 * @return width hint in characters for displaying the text field
	 *
	 * @since BiglyBT 1.9.0.1
	 */
	int getWidthInCharacters();
}
