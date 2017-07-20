/*
 * File    : PluginConfigUIFactory.java
 * Created : 17 nov. 2003
 * By      : Olivier
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

package com.biglybt.pif.ui.config;

import com.biglybt.pif.PluginInterface;

/** Functions to create various plugin UI Config Parameters.<p>
 *
 * The <i>label</i> parameter passed to these functions is a lookup name.
 * The UI label's text will be assigned the value looked up in the language file
 * using the label parameter as the key name.
 * <p>
 * If you wish your configuration option to be displaying in console mode,
 * add the following to your key:<PRE>
 * _b      Boolean Value
 * _i      Integer Value
 * _s      String Value
 * </PRE> the above tags must be added before any other "_" characters.
 *
 * @see PluginInterface#addConfigUIParameters PluginInterface.addConfigUIParameters
 *
 * @author Olivier
 *
 */
public interface PluginConfigUIFactory {

  /**
   * Creates a boolean parameter.<br>
   * The UI component used will be a checkBox.<br>
   * The parameter can be accessed using the PluginConfig.getPluginBooleanParameter(String key).<br>
   * The return object, and EnablerParameter, can be used to add dependency to other parameters.
   * For example, you can use a boolean parameter to choose from logging or not, and a file parameter
   * to choose the logging file. You can call the EnablerParameter.addEnabledOnSelection method with the
   * file Parameter in argument, so that the file parameter will only be enabled if the 'logging' (boolean) one is.
   * @param key the parameter key
   * @param label the label for this checkBox (cf. i18n)
   * @param defaultValue the default value of the parameter
   * @return an EnablerParameter
   */
  public EnablerParameter createBooleanParameter(
      String key,
			String label,
			boolean defaultValue);

  /**
   * Creates an int parameter.<br>
   * The UI component will be a Text field, but only accepting int values.<br>
   * The parameter can be accessed using the PluginConfig.getPluginIntParameter(String key).<br>
   * @param key the parameter key
   * @param label the label for this field (cf. i18n)
   * @param defaultValue the default value of the parameter
   * @return a Parameter
   */
  public Parameter createIntParameter(
      String key,
			String label,
			int defaultValue);

  /**
   * Creates an int parameter.<br>
   * The UI component will be a List.<br>
   * The parameter can be accessed using the PluginConfig.getPluginIntParameter(String key).<br>
   * @param key the parameter key
   * @param label the label for this field (cf. i18n)
   * @param defaultValue the default value of the parameter
   * @param values the list of values
   * @param labels the list of labels (no i18n here)
   * @return a Parameter
   */
  public Parameter createIntParameter(
      String key,
			String label,
			int defaultValue,
			int[] values,
			String labels[]);

  /**
   * Creates a String parameter.<br>
   * The UI Component will be a Text field.<br>
   * The parameter can be accessed using the PluginConfig.getPluginStringParameter(String key).<br>
   * @param key the parameter key
   * @param label the label for this field (cf. i18n)
   * @param defaultValue the default value of the parameter
   * @return a Parameter
   */
  public Parameter createStringParameter(
      String key,
			String label,
			String defaultValue);

  /**
   * Creates an String parameter.<br>
   * The UI component will be a List.<br>
   * The parameter can be accessed using the PluginConfig.getPluginStringParameter(String key).<br>
   * @param key the parameter key
   * @param label The label for this field (cf. i18n)
   * @param defaultValue the default value of the parameter
   * @param values the list of values
   * @param labels the list of labels (no i18n here)
   * @return a Parameter
   */
  public Parameter createStringParameter(
      String key,
			String label,
			String defaultValue,
			String[] values,
			String labels[]);

  /**
   * Creates a File Parameter.<br>
   * The UI component will be a Text field with a browse button.<br>
   * The parameter can be accessed using the PluginConfig.getPluginStringParameter(String key).<br>
   * @param key the parameter key
   * @param label the label for this field (cf. i18n)
   * @param defaultValue the default value of the parameter
   * @return a File Parameter
   */
  public Parameter createFileParameter(
      String key,
			String label,
			String defaultValue);

  /**
   * Creates a Directory Parameter.<br>
   * The UI component will be a Text field with a browse button.<br>
   * The parameter can be accessed using the PluginConfig.getPluginStringParameter(String key).<br>
   * @param key the parameter key
   * @param label the label for this field (cf. i18n)
   * @param defaultValue the default value of the parameter
   * @return a File Parameter
   */
  public Parameter createDirectoryParameter(
      String key,
			String label,
			String defaultValue);

  /**
   * Creates a Color Parameter.<br>
   * The UI component will be a button with a Color area.<br>
   * The parameter is in fact separacted in 3 parameters:<br>
   * key.red<br>
   * key.green<br>
   * key.blue<br>
   * Each color component is stored as an int parameter and can be accessed using PluginConfig.getPluginIntParameter(String key.(red|green|blue)).<br>
   * @param key the parameter key
   * @param label the label for this field (cf. i18n)
   * @param defaultValueRed the red component of the default color (0-255)
   * @param defaultValueGreen the green component of the default color (0-255)
   * @param defaultValueBlue the blue component of the default color (0-255)
   * @return a Color Parameter
   */
  public Parameter createColorParameter(
      String key,
			String label,
			int defaultValueRed,
			int defaultValueGreen,
			int defaultValueBlue);
}
