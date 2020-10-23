/*
 * Created on 04-Jun-2004
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

package com.biglybt.pifimpl.local.ui.config;

/**
 * @author parg
 *
 */

import com.biglybt.core.config.COConfigurationManager;

import com.biglybt.pif.ui.config.InfoParameter;


public class
InfoParameterImpl
	extends 	ParameterImpl
	implements 	InfoParameter
{
	// Used when no config key
	String value = null;
	private boolean textSelectable;

	/**
	 *
	 * If configKey != null:<br>
	 *   [label][config value]<br>
	 * If configKey == null:<br>
	 *   [label][value]<br>
	 */
	public InfoParameterImpl(String configKey, String labelKey, String value) {
		super(configKey, labelKey);


			// not sure we should even be setting a config value on 'info' params...
		
		if (configKey == null) {
			setValue( value );
		}else if ( value != null ){
			setValue( value );
		}
	}

	@Override
	public String
	getValue()
	{
		if (configKey == null) {
			return value;
		}
		return COConfigurationManager.getStringParameter(configKey);
	}

	@Override
	public Object getValueObject() {
		return getValue();
	}

	@Override
	public void
	setValue(
		String	s )
	{
		if (configKey == null) {
			value = s;
			fireParameterChanged();
			return;
		}
		COConfigurationManager.setParameter(configKey, s);
	}


	/**
	 * Whether portions of the text are selectable by the user
	 *
	 * @since BiglyBT 1.9.0.1
	 */
	public void setTextSelectable(boolean selectable) {
		this.textSelectable = selectable;
	}

	/**
	 * @since BiglyBT 1.9.0.1
	 */
	public boolean isTextSelectable() {
		return textSelectable;
	}
}
