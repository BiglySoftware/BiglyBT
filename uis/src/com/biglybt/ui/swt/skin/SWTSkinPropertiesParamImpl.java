/*
 * Created on Jun 27, 2006 1:48:36 AM
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
package com.biglybt.ui.swt.skin;

import java.util.ResourceBundle;

import org.eclipse.swt.graphics.Color;

/**
 * SWTSkinProperties delegator that always passes a set of parameters
 * to string requests.
 *
 * @author TuxPaper
 * @created Jun 27, 2006
 *
 */
public class SWTSkinPropertiesParamImpl
	implements SWTSkinPropertiesParam
{
	private final SWTSkinProperties properties;

	private final String[] sCloneParams;

	/**
	 * @param properties
	 */
	public SWTSkinPropertiesParamImpl(SWTSkinProperties properties,
			String[] sCloneParams) {
		this.properties = properties;
		this.sCloneParams = sCloneParams;
	}

	@Override
	public void addProperty(String name, String value) {
		properties.addProperty(name, value);
	}

	@Override
	public Color getColor(String name) {
		return properties.getColor(name);
	}

	@Override
	public SWTColorWithAlpha getColorWithAlpha(String sID) {
		return properties.getColorWithAlpha(sID);
	}

	@Override
	public int[] getColorValue(String name) {
		return properties.getColorValue(name);
	}

	@Override
	public int getIntValue(String name, int def) {
		return properties.getIntValue(name, def);
	}

	@Override
	public String[] getStringArray(String name, String[] params) {
		return properties.getStringArray(name, params);
	}

	@Override
	public String[] getStringArray(String name) {
		return properties.getStringArray(name, sCloneParams);
	}

	@Override
	public String getStringValue(String name, String def) {
		return properties.getStringValue(name, sCloneParams, def);
	}

	@Override
	public String getStringValue(String name, String[] params, String def) {
		return properties.getStringValue(name, params, def);
	}

	@Override
	public String getStringValue(String name, String[] params) {
		return properties.getStringValue(name, params);
	}

	@Override
	public String getStringValue(String name) {
		return properties.getStringValue(name, sCloneParams);
	}

	// @see SkinProperties#getBooleanValue(java.lang.String, boolean)
	@Override
	public boolean getBooleanValue(String name, boolean def) {
		return properties.getBooleanValue(name, def);
	}

	@Override
	public String[] getParamValues() {
		return sCloneParams;
	}

	@Override
	public void clearCache() {
		properties.clearCache();
	}

	// @see SkinProperties#contains(java.lang.String)
	@Override
	public boolean hasKey(String name) {
		return properties.hasKey(name);
	}

	@Override
	public Color getColor(String name, Color def) {
		Color color = getColor(name);
		if (color == null) {
			return def;
		}
		return color;
	}

	// @see SkinProperties#getEmHeightPX()
	@Override
	public int getEmHeightPX() {
		return properties.getEmHeightPX();
	}

	// @see SWTSkinProperties#getPxValue(java.lang.String, int)
	@Override
	public int getPxValue(String name, int def) {
		return properties.getPxValue(name, def);
	}

	// @see SkinProperties#getReferenceID(java.lang.String)
	@Override
	public String getReferenceID(String name) {
		return properties.getReferenceID(name);
	}

	// @see SkinProperties#addResourceBundle(java.util.ResourceBundle)
	@Override
	public void addResourceBundle(ResourceBundle subBundle, String skinPath) {
		properties.addResourceBundle(subBundle, skinPath);
	}

	@Override
	public void addResourceBundle(ResourceBundle subBundle, String skinPath,
	                              ClassLoader loader) {
		properties.addResourceBundle(subBundle, skinPath,loader);
	}
	// @see SkinProperties#getClassLoader()
	@Override
	public ClassLoader getClassLoader() {
		return properties.getClassLoader();
	}
}
