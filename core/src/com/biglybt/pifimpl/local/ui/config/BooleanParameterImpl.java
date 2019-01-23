/*
 * File    : GenericParameter.java
 * Created : Nov 21, 2003
 * By      : epall
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.pifimpl.local.ui.config;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.impl.ConfigurationDefaults;
import com.biglybt.core.config.impl.ConfigurationParameterNotFoundException;
import com.biglybt.pif.ui.config.BooleanParameter;
import com.biglybt.pifimpl.local.PluginConfigImpl;

/**
 * @author Olivier
 *
 */
public class
BooleanParameterImpl
	extends 	ParameterImpl
	implements 	BooleanParameter
{
	public
	BooleanParameterImpl(
		PluginConfigImpl	config,
		String 			key,
		String 			label,
		boolean 		defaultValue)
	{
		super( config, key, label);
		COConfigurationManager.setBooleanDefault( getKey(), defaultValue );
		config.notifyParamExists(getKey());
	}

	public boolean getDefaultValue() {
		try {
			return ConfigurationDefaults.getInstance().getBooleanParameter(getKey());
		} catch (ConfigurationParameterNotFoundException e) {
			return false;
		}
	}

	@Override
	public boolean getValue() {
		return config.getUnsafeBooleanParameter(getKey(), getDefaultValue());
	}

	@Override
	public void setValue(boolean b) {
		config.setUnsafeBooleanParameter(getKey(), b);
	}

	@Override
	public void
	setDefaultValue(
		boolean		defaultValue )
	{
		COConfigurationManager.setBooleanDefault( getKey(), defaultValue );
	}
}
