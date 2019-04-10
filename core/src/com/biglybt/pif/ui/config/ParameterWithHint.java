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

public interface ParameterWithHint {
	/**
	 * Get the messagebundle key for the Parameter's hint.  Hints are usually
	 * only displayed when the field is empty.
	 *
	 * @since BiglyBT 1.9.0.1
	 */
	String getHintKey();

	/**
	 * Sets the widget message to a messagebundle key.
	 * The message text is displayed as a hint for the user, indicating the purpose of the field.
	 * Hints are usually only displayed when the field is empty.
	 *
	 * @since BiglyBT 1.9.0.1
	 */
	void setHintKey(String hintKey);

	/**
	 * Sets the widget message.
	 * The text is displayed as a hint for the user, indicating the purpose of the field.
	 * Hints are usually only displayed when the field is empty.
	 *
	 * @since BiglyBT 1.9.0.1
	 */
	void setHintText(String text);
}
