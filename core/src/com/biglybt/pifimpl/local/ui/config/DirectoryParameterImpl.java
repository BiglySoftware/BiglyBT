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


/**
 * @author epall
 *
 */

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.pif.ui.config.DirectoryParameter;
import com.biglybt.pifimpl.local.PluginConfigImpl;

public class
DirectoryParameterImpl
	extends		ParameterImpl
	implements 	DirectoryParameter
{
	private String defaultValue;

	public
	DirectoryParameterImpl(
		PluginConfigImpl 	config,
		String 			key,
		String 			label,
		String 			defaultValue)
	{
		super( config, key, label );

		this.defaultValue = defaultValue;

		config.notifyParamExists(getKey());
		COConfigurationManager.setStringDefault(getKey(), getDefaultValue());
	}
	/**
	 * @return Returns the defaultValue.
	 */
	public String getDefaultValue()
	{
		return defaultValue;
	}

	@Override
	public String
	getValue()
	{
		return( config.getUnsafeStringParameter( getKey(), getDefaultValue()));
	}

	@Override
	public void
	setValue(
		String		str )
	{
		config.setUnsafeStringParameter( getKey(), str );
	}
}
