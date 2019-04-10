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
 * See {@link Parameter#addValidator(ParameterValidator)}
 *
 * @since BiglyBT 1.9.0.1
 */
public interface ParameterValidator<VALUETYPE>
{
	/**
	 * Determine validility of parameter value.  Called before permanently
	 * setting config value, as well as when user is editing field
	 *
	 * @since BiglyBT 1.9.0.1
	 */
	ValidationInfo isValidParameterValue(Parameter p, VALUETYPE toValue);

	/**
	 * Validation Info that you pass back to core to tell it whether things are valid
	 *
	 * @since BiglyBT 1.9.0.1
	 */
	class ValidationInfo
	{
		public String info;

		public boolean valid;

		public ValidationInfo(boolean valid) {
			this.valid = valid;
		}

		public ValidationInfo(boolean valid, String info) {
			this.valid = valid;
			this.info = info;
		}
	}
}
