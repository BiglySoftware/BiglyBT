/*
 * Created on 28-Apr-2004
 * Created by Paul Gardner
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
 *
 */

package com.biglybt.pif.ui.model;

import com.biglybt.pif.ui.components.UITextArea;
import com.biglybt.pif.ui.config.*;

/**
 * This object represents a configuration section.
 */
public interface
BasicPluginConfigModel
	extends PluginConfigModel {

	/**
	 * Add a new Boolean Plugin Parameter to the Config Model.
	 * <br>
	 * Typically shown as a checkbox
	 *
	 * @param key Plugin config key
	 * @param resource_name Label resource key
	 *
	 * @since Azureus 2.1.0.2
	 */
	public BooleanParameter
	addBooleanParameter2(
		String 		key,
		String 		resource_name,
		boolean 	defaultValue );

	/**
	 * Add a new String Plugin Parameter to the Config Model
	 *
	 * @param key Plugin config key
	 * @param resource_name Label resource key
	 *
	 * @since Azurues 2.1.0.2
	 */
	public StringParameter
	addStringParameter2(
		String 		key,
		String 		resource_name,
		String	 	defaultValue );

	/**
	 * Add to the Config Model a new String plugin parameter which is limited to a set list
	 * <br>
	 * Typically shown as a drop down list, or a list box
	 *
	 * @param key Plugin config key
	 * @param resource_name Label resource key
	 * @param values List of available values
	 *
	 * @since Azureus 2.1.0.2
	 *
	 * @deprecated Use {@link BasicPluginConfigModel#addStringListParameter2(String, String, String[], String[], String)}
	 */
	public StringListParameter
	addStringListParameter2(
		String 		key,
		String 		resource_name,
		String[]	values,
		String	 	defaultValue );

	/**
	 * Add to the Config Model a new String plugin parameter which is limited to a set list
	 * <br>
	 * Typically shown as a drop down list, or a list box
	 *
	 * @param key Plugin config key
	 * @param resource_name Label resource key
	 * @param labels A list of localised message strings corresponding to each value.
	 *
	 * @since Azureus 2.3.0.6
	 */
	public StringListParameter
	addStringListParameter2(
		String 		key,
		String 		resource_name,
		String[]	values,
		String[]	labels,
		String	 	defaultValue );

	/**
	 * Add to the Config Model a new integer plugin parameter which is limited to a set list
	 * <br>
	 * Typically shown as a set of radio buttons
	 *
	 * @param key Plugin config key
	 * @param resource_name Label resource key
	 * @param labels A list of localised message strings corresponding to each value.
	 *
	 * @since BiglyBT 1.7.0.1
	 */
	public IntListParameter
	addIntListParameter2(
			String    key,
			String    resource_name,
			int[]     values,
			String[]  labels,
			int	      defaultValue );

	/**
	 * Add a new float Plugin Parameter to the Config Model
	 *
	 * @param key Plugin config key
	 * @param resource_name Label resource key
	 *
	 * @since BiglyBT 1.7.0.1
	 */
	public FloatParameter
	addFloatParameter2(
			String    key,
			String    resource_name,
			float	    defaultValue,
			float     minValue,
			float     maxValue,
			boolean   allowZero,
			int digitsAfterDecimal);

	/**
	 *
	 * @param key
	 * @param resource_name
	 * @param encoding_type
	 * @param defaultValue
	 * @return
	 * @since 2.1.0.2
	 */
	public PasswordParameter
	addPasswordParameter2(
		String 		key,
		String 		resource_name,
		int			encoding_type,		// see PasswordParameter.ET_ constants
		byte[]	 	defaultValue );		// plain default value

	/**
	 *
	 * @param key
	 * @param resource_name
	 * @param defaultValue
	 * @return
	 * @since 2.1.0.2
	 */
	public IntParameter
	addIntParameter2(
		String 		key,
		String 		resource_name,
		int	 		defaultValue );

	/**
	 *
	 * @param key
	 * @param resource_name
	 * @param defaultValue
	 * @param min_value Minimum allowed value
	 * @param max_value Maximum allowed value
	 * @return
	 * @since 3.0.3.5
	 */
	public IntParameter
	addIntParameter2(
		String 		key,
		String 		resource_name,
		int	 		defaultValue,
		int         min_value,
		int         max_value);

	/**
	 * Displays a single label. Not linked to a config key.
	 *
	 * @param resource_name  MessageBundle key
	 * @since Azureus 2.1.0.2
	 */
	public LabelParameter
	addLabelParameter2(
		String 		resource_name );

	/**
	 * Display a label and a value together.  Not linked to a config key.
	 *
	 * @since Vuze 4005
	 * @param resource_name  MessageBundle key
	 * @param value String value
	 */

	public InfoParameter
	addInfoParameter2(
		String 		resource_name,
		String		value );

	/**
	 * @since 2.5.0.2
	 */
	public HyperlinkParameter addHyperlinkParameter2(String resource_name, String url_location);

	/**
	 *
	 * @param key
	 * @param resource_name  MessageBundle key
	 * @param defaultValue
	 * @return
	 * @since Azureus 2.1.0.2
	 */
	public DirectoryParameter
	addDirectoryParameter2(
		String 		key,
		String 		resource_name,
		String	 	defaultValue );

	/**
	 *
	 * @param key
	 * @param resource_name
	 * @param defaultValue
	 * @return
	 * @since 2.5.0.1
	 */
	public FileParameter
	addFileParameter2(
		String 		key,
		String 		resource_name,
		String	 	defaultValue );

	/**
	 *
	 * @param key
	 * @param resource_name
	 * @param defaultValue
	 * @param file_extensions Allowed list of file extensions.
	 * @return
	 * @since 2.5.0.1
	 */
	public FileParameter
	addFileParameter2(
		String 		key,
		String 		resource_name,
		String	 	defaultValue,
		String[]    file_extensions);

	/**
	 * Adds an actionable UI widget, usually a displayed as button or a link
	 *
	 * @param label_resource_name Text before the button
	 * @param action_resource_name Button Text

	 * @since Azureus 2.1.0.2
	 */
	public ActionParameter
	addActionParameter2(
		String 		label_resource_name,
		String		action_resource_name );

	/**
	 * @since 3.0.3.5
	 * @param key
	 * @param resource_name
	 * @param r
	 * @param g
	 * @param b
	 * @return
	 */
	public ColorParameter addColorParameter2(String key, String resource_name, int r, int g, int b);

	/**
	 * Creates a {@link UIParameter} object to add to this config model object.
	 *
	 * @since Vuze 3.0.5.3
	 *
	 * @param resource_name Not Used. null recommended.
	 */
	public UIParameter addUIParameter2(UIParameterContext context, String resource_name);

	/**
	 * Creates a read-only text area similar to a label, but typically allows
	 * scrollbars and copying of portions of text.
	 */
	public UITextArea
	addTextArea(
		String	resource_name );

	/**
	 * Creates a group around a list of Parameters.
	 * Typically displayed in a border when there's a resource_name, or borderless
	 * when there isn't.
	 *
	 * @since Azureus 2.3.0.0
	 */
	// Note: Up until BiglyBT 1.9.0.0, parameters was Parameter[]. varargs generate the same signature
	public ParameterGroup
	createGroup(
		String		resource_name,
		Parameter...	parameters );

	/**
	 * @since 5601
	 * @param resource_name
	 * @return
	 */

	public ParameterTabFolder
	createTabFolder();

	/**
	 *
	 * @return
	 * @since 2.3.0.5
	 */
	public String
	getSection();

	/**
	 *
	 * @return
	 * @since 2.3.0.5
	 */
	public String
	getParentSection();

	/**
	 * Retrieve all the parameters added to this plugin config
	 *
	 * @return parameter list
	 * @since 2.3.0.5
	 */
	public Parameter[]
	getParameters();

	/**
	 * Sets the name of the configuration model - this is useful in
	 * situations where the configuration section is being dynamically
	 * created (perhaps with user input).
	 *
	 * @since 3.0.5.3
	 */
	public void setLocalizedName(String name);
}
