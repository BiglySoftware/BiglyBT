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

package com.biglybt.pifimpl.local.ui.config;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.pifimpl.local.PluginConfigImpl;

import com.biglybt.pif.ui.config.IntListParameter;

public class IntListParameterImpl
	extends ParameterImpl
	implements IntListParameter
{
	private int[] values;

	private String[] labels;

	public IntListParameterImpl(PluginConfigImpl config, String key, String label,
			int defaultValue, int[] values, String[] labels) {
		super(config, key, label);
		COConfigurationManager.setIntDefault(getKey(), defaultValue);
		this.values = values;
		this.labels = labels;
		config.notifyParamExists(getKey());
	}

	public int[] getValues() {
		return values;
	}

	public String[] getLabels() {
		return labels;
	}

	@Override
	public void setLabels(String[] _labels) {
		labels = _labels;
	}

	@Override
	public int getValue() {
		return config.getUnsafeIntParameter(getKey());
	}

	@Override
	public void setValue(int value) {
		config.setUnsafeIntParameter(getKey(), value);
	}
}
