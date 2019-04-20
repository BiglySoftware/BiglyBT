/*
 * Created on 23 Oct 2007
 * Created by Allan Crooks
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
 */
package com.biglybt.pifimpl.local.ui.config;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.html.HTMLUtils;

import com.biglybt.pif.ui.config.ColorParameter;

/**
 * @author Allan Crooks
 *
 */
public class ColorParameterImpl
	extends ParameterImpl
	implements ColorParameter
{

	private String suffixLabelKey;

	public ColorParameterImpl(String configKey, String label) {
		super(configKey, label);
	}


	@Override
	public Object getValueObject() {
		return "#" + HTMLUtils.toColorHexString(getRedValue(), getGreenValue(),
				getBlueValue(), 255);
	}

	@Override
	public int getRedValue() {
		return COConfigurationManager.getIntParameter(configKey + ".red");}
	@Override
	public int getGreenValue() {
		return COConfigurationManager.getIntParameter(configKey + ".green");}
	@Override
	public int getBlueValue() {
		return COConfigurationManager.getIntParameter(configKey + ".blue");}

	@Override
	public void setRGBValue(int r, int g, int b) {
		COConfigurationManager.setRGBParameter(configKey, r, g, b, true);
	}

	@Override
	public boolean resetToDefault() {
		if (configKey == null) {
			return false;
		}
		return COConfigurationManager.removeRGBParameter(configKey);
	}

	public boolean isOverridden() {
		return COConfigurationManager.getBooleanParameter(configKey + ".override");
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
		this.suffixLabelKey = text == null ? null : "!" + text + "!";
		refreshControl();
	}
}
