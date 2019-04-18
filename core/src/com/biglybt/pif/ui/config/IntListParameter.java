/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.pif.ui.config;

/**
 * An integer config parameter that's limited to a list of values.  Values are
 * usually shown to user in friendly text representations.
 *
 * @see StringListParameter
 * @see com.biglybt.pif.ui.model.BasicPluginConfigModel#addIntListParameter2(String, String, int[], String[], int)
 *
 * @since BiglyBT 1.0.0.0
 */
public interface IntListParameter
	extends Parameter, ParameterWithSuffix
{
	/**
	 * Dropdown style.  Default.
	 *
	 * @since BiglyBT 1.0.0.0
	 */
	int TYPE_DROPDOWN = 0;

	/**
	 * Compact style of radio buttons. Typically will display all options in one row
	 *
	 * @since BiglyBT 1.9.0.1
	 */
	int TYPE_RADIO_COMPACT = 1;

	/**
	 * List style of radio buttons. Typically will display each option on a new row
	 *
	 * @since BiglyBT 1.9.0.1
	 */
	int TYPE_RADIO_LIST = 2;

	// Future (same int value as StringListParameter)
	//int TYPE_LISTBOX = 3;

	void setValue(int value);

	int getValue();

	/**
	 * List of labels displayed to user
	 * 
	 * @since BiglyBT 1.9.0.1
	 */
	String[] getLabels();

	/**
	 * Set list of labels displayed to user
	 *
	 * @since BiglyBT 1.0.0.0
	 */
	void setLabels(String[] labels);

	/**
	 * List of values that can be stored to config
	 * @since BiglyBT 1.9.0.1
	 */
	int[] getValues();

	/**
	 * Set List type.
	 *
	 * @see #TYPE_DROPDOWN
	 * @see #TYPE_RADIO_COMPACT
	 * @see #TYPE_RADIO_LIST
	 *
	 * @since BiglyBT 1.9.0.1
	 */
	void setListType(int listType);

	/**
	 * Get List type.
	 *
	 * @see #TYPE_DROPDOWN
	 * @see #TYPE_RADIO_COMPACT
	 * @see #TYPE_RADIO_LIST
	 *
	 * @since BiglyBT 1.9.0.1
	 */
	int getListType();
}
