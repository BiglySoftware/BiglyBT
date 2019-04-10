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
 * @see com.biglybt.pif.ui.model.BasicPluginConfigModel#addIntParameter2(String, String, int)
 * @see com.biglybt.pif.ui.model.BasicPluginConfigModel#addIntParameter2(String, String, int, int, int)
 */
public interface
IntParameter
	extends Parameter, ParameterWithSuffix
{
	/**
	 * @since BiglyBT 1.0.0.0
	 */
	public int
	getValue();

	/**
	 * @since BiglyBT 1.0.0.0
	 */
	public void
	setValue(
		int	v );

	/**
	 * @return Whether the parameter has a min and max value set
	 * @since BiglyBT 1.9.0.1
	 */
	boolean isLimited();

	/**
	 * @since BiglyBT 1.9.0.1
	 */
	int getMinValue();

	/**
	 * @since BiglyBT 1.9.0.1
	 */
	int getMaxValue();

	/**
	 * @since BiglyBT 1.9.0.1
	 */
	void setMinValue(int min_value);

	/**
	 * @since BiglyBT 1.9.0.1
	 */
	void setMaxValue(int max_value);

	/**
	 * Same as {@link #addValidator(ParameterValidator)}, but
	 * casts the "toValue" to Integer.
	 *
	 * @since BiglyBT 1.9.0.1
	 */
	void addIntegerValidator(ParameterValidator<Integer> validator);
}
