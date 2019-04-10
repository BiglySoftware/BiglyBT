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

package com.biglybt.ui.swt.config;

/**
 * Processes value changes from a {@link SwtParameter}
 * 
 * <p/>
 * For COConfigurationManager backed parameters, see {@link SwtConfigParameterValueProcessor}
 */
public interface SwtParameterValueProcessor<PARAMTYPE extends SwtParameter<VALUETYPE>, VALUETYPE>
{
	/**
	 * Return the value for Parameter p
	 */
	VALUETYPE getValue(PARAMTYPE p);

	/**
	 * New value for Parameter p.  Handle things like storing value.
	 *
	 * @return Whether the value changed from stored value.
	 * Returning true will trigger change listeners, and typically refresh UI controls
	 */
	boolean setValue(PARAMTYPE p, VALUETYPE value);

	/**
	 * Whether the Parameter is currently set to the default value. This may be
	 * used to enable ui related to reseting value
	 */
	default boolean isDefaultValue(PARAMTYPE p) {
		return true;
	}

	/**
	 * Return the default value. <br>
	 * This may be used to enable ui related to reseting value, or to show the default value to the user
	 */
	default VALUETYPE getDefaultValue(PARAMTYPE p) {
		return null;
	}

	/**
	 * Reset key to default value
	 *
	 * @return Whether the value was reset.
	 * Returning true will trigger change listeners, and typically refresh UI controls
	 */
	default boolean resetToDefault(PARAMTYPE p) {
		return false;
	}

	/**
	 * Clean up after yourself, yo
	 */
	default void dispose(PARAMTYPE p) {
	}
}
