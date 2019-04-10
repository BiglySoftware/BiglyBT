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
 * Config parameter representing a float value
 *
 * @see com.biglybt.pif.ui.model.BasicPluginConfigModel#addFloatParameter2(String, String, float, float, float, boolean, int)
 */
public interface FloatParameter
	extends Parameter, ParameterWithSuffix
{
	boolean isAllowZero();

	void setAllowZero(boolean allowZero);

	int getNumDigitsAfterDecimal();

	float getValue();

	void setValue(float v);

	float getMinValue();

	float getMaxValue();
}
