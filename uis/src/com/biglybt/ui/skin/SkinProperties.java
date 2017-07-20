/*
 * Created on Jun 26, 2006 8:34:56 PM
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
package com.biglybt.ui.skin;

import java.util.ResourceBundle;


/**
 * Interface for reading Skin properties (might be better)
 *
 * @author TuxPaper
 * @created Jun 26, 2006
 *
 */
public interface SkinProperties
{

	/**
	 * Retrieve all the properties
	 *
	 * @return all the properties
	 */
	//Properties getProperties();

	/**
	 * Add a property key/value pair to the list
	 *
	 * @param name Name of Property
	 * @param value Value of Property
	 */
	void addProperty(String name, String value);

	/**
	 * Retrieve a property's int value
	 *
	 * @param name Name of property
	 * @param def Default value if property not found
	 * @return value
	 */
	int getIntValue(String name, int def);

	/**
	 * Retrieve a string value
	 *
	 * @param name Name of property
	 * @return the String value, or null if not found
	 */
	String getStringValue(String name);

	String getStringValue(String name, String def);

	String[] getStringArray(String name);

	String getStringValue(String name, String[] params);

	String getStringValue(String name, String[] params, String def);

	String[] getStringArray(String name, String[] params);

	int[] getColorValue(String name);

	boolean getBooleanValue(String name, boolean def);

	/**
	 *
	 *
	 * @since 3.1.1.1
	 */
	void clearCache();

	/**
	 * @param name
	 * @return
	 *
	 * @since 3.1.1.1
	 */
	boolean hasKey(String name);

	/**
	 * @param name
	 * @return
	 *
	 * @since 3.1.1.1
	 */
	String getReferenceID(String name);

	/**
	 * @param subBundle
	 * @param skinPath TODO
	 *
	 * @since 4.0.0.5
	 */
	void addResourceBundle(ResourceBundle subBundle, String skinPath);

	/**
	 * @param subBundle
	 * @param skinPath
	 * @param loader
	 * @since 4315
	 */

	void addResourceBundle(ResourceBundle subBundle, String skinPath, ClassLoader loader );

	/**
	 * @return
	 *
	 * @since 4.0.0.5
	 */
	ClassLoader getClassLoader();

	/**
	 * @return
	 *
	 * @since 5.6.2.1
	 */
	int getEmHeightPX();
}
