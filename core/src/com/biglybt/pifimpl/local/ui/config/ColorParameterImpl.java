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
import com.biglybt.pif.ui.config.ColorParameter;
import com.biglybt.pifimpl.local.PluginConfigImpl;

/**
 * @author Allan Crooks
 *
 */
public class ColorParameterImpl extends ParameterImpl implements ColorParameter {

	private int r;
	private int g;
	private int b;

	private final int orig_r;
	private final int orig_g;
	private final int orig_b;

	public ColorParameterImpl(PluginConfigImpl config, String key, String label, int _r, int _g, int _b) {
		super(config, key, label);

		config.notifyRGBParamExists(getKey());
		COConfigurationManager.setIntDefault(getKey() + ".red", r);
		COConfigurationManager.setIntDefault(getKey() + ".green", g);
		COConfigurationManager.setIntDefault(getKey() + ".blue", b);
		COConfigurationManager.setBooleanDefault(getKey() + ".override", false);

		orig_r = r = _r;
		orig_g = g = _g;
		orig_b = b = _b;
	}

	@Override
	public int getRedValue() {return this.r;}
	@Override
	public int getGreenValue() {return this.g;}
	@Override
	public int getBlueValue() {return this.b;}

	public void reloadParamDataFromConfig(boolean override) {
		int[] rgb = config.getUnsafeColorParameter(getKey());
		this.r = rgb[0];
		this.g = rgb[1];
		this.b = rgb[2];
		config.setUnsafeBooleanParameter(getKey() + ".override", override);
	}

	@Override
	public void setRGBValue(int r, int g, int b) {
		this.r = r; this.g = g; this.b = b;
		config.setUnsafeColorParameter(getKey(), new int[] {r, g, b}, true);
	}

	public void resetToDefault() {
		config.setUnsafeColorParameter(getKey(), new int[] {orig_r, orig_g, orig_b}, false);
	}

	public boolean isOverridden() {
		return config.getUnsafeBooleanParameter(getKey() + ".override");
	}

}
