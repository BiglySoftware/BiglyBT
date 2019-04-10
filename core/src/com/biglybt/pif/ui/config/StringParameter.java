/*
 * Created on 02-Jun-2004
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
StringParameter
	extends Parameter, ParameterWithSuffix, ParameterWithHint
{
	public void
	setValue(
		String	value );

	public String
	getValue();

		/**
		 * @since 5201
		 * @param visible_line_count
		 */

	public void
	setMultiLine(
		int	visible_line_count );

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

	/**
	 * Limit characters to a list
	 *
	 * @since BiglyBT 1.9.0.1
	 */
	void setValidChars(String chars, boolean caseSensitive);

	/**
	 * Same as {@link #addValidator(ParameterValidator)}, but
	 * casts the "toValue" to String.
	 *
	 * @see #addValidator(ParameterValidator)
	 * @since BiglyBT 1.9.0.1
	 */
	void addStringValidator(
			ParameterValidator<String> stringParamValidator);

	/**
	 * Limit the number of characters for Parameter
	 *
	 * @since BiglyBT 1.9.0.1
	 */
	void setTextLimit(int textLimit);

	/**
	 * Get the character limit for Parameter
	 *
	 * @since BiglyBT 1.9.0.1
	 */
	int getTextLimit();
}
