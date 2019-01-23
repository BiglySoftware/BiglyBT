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
 * @author Olivier
 *
 */

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.pif.ui.config.IntParameter;
import com.biglybt.pifimpl.local.PluginConfigImpl;

public class IntParameterImpl extends ParameterImpl implements IntParameter
{
	private boolean limited;
	private int min_value;
	private int max_value;
	public IntParameterImpl(PluginConfigImpl config,String key, String label, int defaultValue)
	{
		super(config,key, label);
		config.notifyParamExists(getKey());
		COConfigurationManager.setIntDefault( getKey(), defaultValue );

		this.limited = false;
	}

	public IntParameterImpl(PluginConfigImpl config,String key, String label, int defaultValue, int min_value, int max_value)
	{
		this(config,key, label, defaultValue);
		this.min_value = min_value;
		this.max_value = max_value;
		this.limited = true;
	}


	@Override
	public int
	getValue()
	{
		return( config.getUnsafeIntParameter( getKey()));
	}

	@Override
	public void
	setValue(
		int	b )
	{
		config.setUnsafeIntParameter( getKey(), b );
	}

	public boolean isLimited() {return limited;}
	public int getMinValue() {return this.min_value;}
	public int getMaxValue() {return this.max_value;}
}
