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

import com.biglybt.pif.ui.config.FloatParameter;

public class FloatParameterImpl
	extends ParameterImpl
	implements FloatParameter
{
	private int numDigitsAfterDecimal = 1;

	private boolean limited;

	private float min_value = 0;

	private float max_value = -1;

	private boolean allowZero = true;

	private String suffixLabelKey;

	public FloatParameterImpl(String configKey, String labelKey) {
		super(configKey, labelKey);

		this.limited = false;
	}

	public FloatParameterImpl(String configKey, String labelKey, float minValue,
			float maxValue, int numDigitsAfterDecimal) {
		this(configKey, labelKey);
		this.min_value = minValue;
		this.max_value = maxValue;
		this.numDigitsAfterDecimal = numDigitsAfterDecimal;
		this.limited = true;
	}

	@Override
	public boolean isAllowZero() {
		return allowZero;
	}

	@Override
	public void setAllowZero(boolean allowZero) {
		this.allowZero = allowZero;
	}

	@Override
	public int getNumDigitsAfterDecimal() {
		return numDigitsAfterDecimal;
	}

	@Override
	public float getValue() {
		return COConfigurationManager.getFloatParameter(configKey);
	}

	@Override
	public Object getValueObject() {
		return getValue();
	}

	@Override
	public void setValue(float b) {
		COConfigurationManager.setParameter(configKey, b);
	}

	public boolean isLimited() {
		return limited;
	}

	@Override
	public float getMinValue() {
		return min_value;
	}

	@Override
	public float getMaxValue() {
		return max_value;
	}

	@Override
	public String getSuffixLabelKey() {
		return suffixLabelKey;
	}

	@Override
	public void setSuffixLabelKey(String suffixLabelKey) {
		this.suffixLabelKey = suffixLabelKey;
		refreshControl();
	}

	@Override
	public void setSuffixLabelText(String text) {
		this.suffixLabelKey = "!" + text + "!";
		refreshControl();
	}
}
