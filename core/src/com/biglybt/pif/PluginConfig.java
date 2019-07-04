/*
 * File    : PluginConfig.java
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

package com.biglybt.pif;

/**
 * @author Olivier
 *
 */

import java.io.File;
import java.net.NetworkInterface;
import java.util.List;
import java.util.Map;

/**
 * This class provides a way for a plugin to get and set configuration settings - both for the plugin
 * itself and for core settings as well.
 *
 * <p>
 *
 * This class interface contains four different variations of <tt>get</tt> and <tt>set</tt> parameter methods:
 * <ul>
 *   <li>getCore<code>&lt;type></code>Parameter</li>
 *   <li>getUnsafe<code>&lt;type></code>Parameter</li>
 *   <li>getPlugin<code>&lt;type></code>Parameter</li>
 * </ul>
 *
 * The "Core" set of methods get or set some core parameters. 
 * You should use the parameter names defined as constants in this interface
 * (the ones labelled <tt>CORE_PARAM</tt>). 
 * These parameters will be properly supported by the client, even if the way these
 * values are stored or handled differently in the the client core itself.
 * <br/>
 * Attempting to set or get parameters not mentioned here should raise an error.
 * <p/>
 * The "Unsafe" set of methods allow you to modify configuration 
 * settings which are stored directly inside the client.
 * These settings may change (without warning) between versions, 
 * so there is no guarantee that plugins that use these values
 * will behave properly in different versions of the client.
 * <p/>
 * The "Plugin" set of methods are used to store and retrieve data intended 
 * exclusively for the use of the plugin itself which is what you will be using 
 * most of the time.
 */
public interface
PluginConfig
{
	public static final String CORE_PARAM_INT_MAX_UPLOAD_SPEED_KBYTES_PER_SEC			= "Max Upload Speed KBs";
	public static final String CORE_PARAM_INT_MAX_UPLOAD_SPEED_SEEDING_KBYTES_PER_SEC 	= "Max Upload Speed When Only Seeding KBs";
 	public static final String CORE_PARAM_INT_MAX_DOWNLOAD_SPEED_KBYTES_PER_SEC			= "Max Download Speed KBs";
 	public static final String CORE_PARAM_INT_MAX_CONNECTIONS_PER_TORRENT				= "Max Connections Per Torrent";
 	public static final String CORE_PARAM_INT_MAX_CONNECTIONS_GLOBAL					= "Max Connections Global";
 	public static final String CORE_PARAM_INT_MAX_DOWNLOADS								= "Max Downloads";
 	public static final String CORE_PARAM_INT_MAX_ACTIVE								= "Max Active Torrents";
 	public static final String CORE_PARAM_INT_MAX_ACTIVE_SEEDING						= "Max Active Torrents When Only Seeding";
 	public static final String CORE_PARAM_INT_MAX_UPLOADS								= "Max Uploads";
 	public static final String CORE_PARAM_INT_MAX_UPLOADS_SEEDING						= "Max Uploads Seeding";
 	public static final String CORE_PARAM_BOOLEAN_AUTO_SPEED_ON 						= "Auto Upload Speed Enabled";
 	public static final String CORE_PARAM_BOOLEAN_AUTO_SPEED_SEEDING_ON 				= "Auto Upload Speed Seeding Enabled";
 	public static final String CORE_PARAM_BOOLEAN_MAX_UPLOAD_SPEED_SEEDING 				= "Max Upload Speed When Only Seeding Enabled";
 	public static final String CORE_PARAM_BOOLEAN_MAX_ACTIVE_SEEDING 					= "Max Active Torrents When Only Seeding Enabled";
	public static final String CORE_PARAM_BOOLEAN_SOCKS_PROXY_NO_INWARD_CONNECTION		= "SOCKS Proxy No Inward Connection";
	public static final String CORE_PARAM_BOOLEAN_NEW_SEEDS_START_AT_TOP				= "Newly Seeding Torrents Get First Priority";

	/**
	 * Semicolon seperated list containing:
	 * IP<br>
	 * {@link NetworkInterface} name (ex. eth10) <br>
	 * NetworkInterface name with IP index in square brackets (ex. eth6[0])
	 *
	 * @since 2.3.0.5
	 */
	public static final String CORE_PARAM_STRING_LOCAL_BIND_IP							= "CORE_PARAM_STRING_LOCAL_BIND_IP";
	
	/**
	 * @deprecated 2.0.0.1 (removed from core)
	 */
	public static final String CORE_PARAM_BOOLEAN_FRIENDLY_HASH_CHECKING				= "CORE_PARAM_BOOLEAN_FRIENDLY_HASH_CHECKING";

	/**
	 * @since 3.0.4.3
	 */
	public static final String GUI_PARAM_INT_SWT_REFRESH_IN_MS = "GUI_PARAM_INT_SWT_REFRESH_IN_MS";

	/**
	 * @since 3.0.4.3
	 */
	public static final String CORE_PARAM_BOOLEAN_NEW_TORRENTS_START_AS_STOPPED = "CORE_PARAM_BOOLEAN_NEW_TORRENTS_START_AS_STOPPED";

	/**
	 * @since 3.0.5.3
	 */
	public static final String CORE_PARAM_INT_INCOMING_TCP_PORT = "Incoming TCP Port";

	/**
	 * @since 3.0.5.3
	 */
	public static final String CORE_PARAM_INT_INCOMING_UDP_PORT = "Incoming UDP Port";

	/**
	 * @since 3.1.1.1
	 */
	public static final String CORE_PARAM_STRING_DEFAULT_SAVE_PATH = "Default save path";


	/**
   * Returns the value of a core boolean parameter.
   *
   * @param key The parameter name.
   * @return The value of the parameter.
   *
   * @since 3.0.4.3
   */
  public boolean getCoreBooleanParameter(String key);

  /**
   * Returns the value of a core byte array parameter.
   *
   * @param key The parameter name.
   * @return The value of the parameter.
   *
   * @since 3.0.4.3
   */
  public byte[] getCoreByteParameter(String key);

  /**
   * Returns the value of a core color parameter.
   *
   * <p>
   *
   * It will return <tt>null</tt> if no color parameter is stored, or an
   * integer array of size 4 representing the red, green and blue values,
   * and a flag indicating if the color is an override of the default or
   * not (<tt>0</tt> indicates no override, <tt>1</tt> means it is overridden).
   *
   * <p>
   *
   * In many cases, the override flag can just be ignored.
   *
   * @param key The parameter name.
   * @return The value of the parameter.
   *
   * @since 3.0.5.3
   */
  public int[] getCoreColorParameter(String key);

  /**
   * Returns the value of a core float parameter.
   *
   * @param key The parameter name.
   * @return The value of the parameter.
   *
   * @since 3.0.4.3
   */
  public float getCoreFloatParameter(String key);

  /**
   * Returns the value of a core int parameter.
   *
   * @param key The parameter name.
   * @return The value of the parameter.
   *
   * @since 3.0.4.3
   */
  public int getCoreIntParameter(String key);

  /**
   * Returns the value of a core long parameter.
   *
   * @param key The parameter name.
   * @return The value of the parameter.
   *
   * @since 3.0.4.3
   */
  public long getCoreLongParameter(String key);

  /**
   * Returns the value of a core string parameter.
   *
   * @param key The parameter name.
   * @return The value of the parameter.
   *
   * @since 3.0.4.3
   */
  public String getCoreStringParameter(String key);

  /**
   * Sets the value of a core boolean parameter.
   *
   * @param key	The parameter name, which must be one defined from the above core constants.
   * @param value The new value for the parameter.
   *
   * @since 3.0.4.2
   */
  public void setCoreBooleanParameter(String key, boolean value);

  /**
   * Sets the value of a core byte array parameter.
   *
   * @param key	The parameter name, which must be one defined from the above core constants.
   * @param value The new value for the parameter.
   *
   * @since 3.0.4.2
   */
  public void setCoreByteParameter(String key, byte[] value);

  /**
   * Sets the value of a core byte array parameter.
   *
   * <p>
   *
   * The value should be an integer array of size 3 representing
   * the red, green and blue values - or <tt>null</tt> to disable it.
   *
   * @param key	The parameter name, which must be one defined from the above core constants.
   * @param value The new value for the parameter.
   *
   * @since 3.0.5.3
   */
  public void setCoreColorParameter(String key, int[] value);

  /**
   * Sets the value of a core byte array parameter.
   *
   * <p>
   *
   * The value should be an integer array of size 3 representing
   * the red, green and blue values - or <tt>null</tt> to disable it.
   *
   * <p>
   *
   * The override flag is used to indicate if the value being set is overriding
   * the default value. This is mainly used for interface purposes.
   *
   * @param key	The parameter name, which must be one defined from the above core constants.
   * @param value The new value for the parameter.
   * @param override <tt>true</tt> if the value is overridden from the default.
   *
   * @since 3.0.5.3
   */
  public void setCoreColorParameter(String key, int[] value, boolean override);


  /**
   * Sets the value of a core float parameter.
   *
   * @param key	The parameter name, which must be one defined from the above core constants.
   * @param value The new value for the parameter.
   *
   * @since 3.0.4.2
   */
  public void setCoreFloatParameter(String key, float value);

  /**
   * Sets the value of a core int parameter.
   *
   * @param key	The parameter name, which must be one defined from the above core constants.
   * @param value The new value for the parameter.
   *
   * @since 3.0.4.2
   */
  public void setCoreIntParameter(String key, int value);

  /**
   * Sets the value of a core long parameter.
   *
   * @param key	The parameter name, which must be one defined from the above core constants.
   * @param value The new value for the parameter.
   *
   * @since 3.0.4.2
   */
  public void setCoreLongParameter(String key, long value);

  /**
   * Sets the value of a core string parameter.
   *
   * @param key	The parameter name, which must be one defined from the above core constants.
   * @param value The new value for the parameter.
   *
   * @since 3.0.4.2
   */
  public void setCoreStringParameter(String key, String value);


  /**
   * Returns the value of a plugin boolean parameter.
   *
   * @param key The parameter name.
   * @return The value of the parameter.
   *
   * @since 2.0.4.2
   */
  public boolean getPluginBooleanParameter(String key);

  /**
   * Returns the value of a plugin boolean parameter.
   *
   * @param key The parameter name.
   * @param default_value The default value to return if one is not defined.
   * @return The value of the parameter.
   *
   * @since 2.0.4.2
   */
  public boolean getPluginBooleanParameter(String key, boolean default_value);

  /**
   * Returns the value of a plugin byte array parameter.
   *
   * @param key The parameter name.
   * @return The value of the parameter.
   *
   * @since 3.0.0.7
   */
  public byte[] getPluginByteParameter(String key);

  /**
   * Returns the value of a plugin byte array parameter.
   *
   * @param key The parameter name.
   * @param default_value The default value to return if one is not defined.
   * @return The value of the parameter.
   *
   * @since 2.2.0.3
   */
  public byte[] getPluginByteParameter(String key, byte[] default_value);

  /**
   * Returns the value of a plugin color parameter.
   *
   * <p>
   *
   * It will return <tt>null</tt> if no color parameter is stored, or an
   * integer array of size 4 representing the red, green and blue values,
   * and a flag indicating if the color is an override of the default or
   * not (<tt>0</tt> indicates no override, <tt>1</tt> means it is overridden).
   *
   * <p>
   *
   * In many cases, the override flag can just be ignored.
   *
   * @param key The parameter name.
   * @return The value of the parameter.
   *
   * @since 3.0.5.3
   */
  public int[] getPluginColorParameter(String key);

  /**
   * Returns the value of a plugin color parameter.
   *
   * <p>
   *
   * It will return <tt>null</tt> if no color parameter is stored, or an
   * integer array of size 4 representing the red, green and blue values,
   * and a flag indicating if the color is an override of the default or
   * not (<tt>0</tt> indicates no override, <tt>1</tt> means it is overridden).
   *
   * <p>
   *
   * In many cases, the override flag can just be ignored.
   *
   * @param key The parameter name.
   * @param default_value The default value to return if one is not defined.
   * @return The value of the parameter.
   *
   * @since 3.0.5.3
   */
  public int[] getPluginColorParameter(String key, int[] default_value);


  /**
   * Returns the value of a plugin float parameter.
   *
   * @param key The parameter name.
   * @return The value of the parameter.
   *
   * @since 3.0.0.7
   */
  public float getPluginFloatParameter(String key);

  /**
   * Returns the value of a plugin float parameter.
   *
   * @param key The parameter name.
   * @param default_value The default value to return if one is not defined.
   * @return The value of the parameter.
   *
   * @since 3.0.0.7
   */
  public float getPluginFloatParameter(String key, float default_value);

  /**
   * Returns the value of a plugin int parameter.
   *
   * @param key The parameter name.
   * @return The value of the parameter.
   *
   * @since 2.0.4.2
   */
  public int getPluginIntParameter(String key);

  /**
   * Returns the value of a plugin int parameter.
   *
   * @param key The parameter name.
   * @param default_value The default value to return if one is not defined.
   * @return The value of the parameter.
   *
   * @since 2.0.4.2
   */
  public int getPluginIntParameter(String key, int default_value);

  /**
   * Returns the value of a plugin list parameter. The contents of the list must conform
   * to <i>bencodable</i> rules (e.g. <tt>Map</tt>, <tt>Long</tt>, <tt>byte[]</tt>, <tt>List</tt>)
   *
   * @param key The parameter name.
   * @param default_value The default value to return if one is not defined.
   * @return The value of the parameter.
   *
   * @since 2.3.0.1
   */
  public List getPluginListParameter(String key, List default_value);

  /**
   * Returns the value of a plugin long parameter.
   *
   * @param key The parameter name.
   * @return The value of the parameter.
   *
   * @since 3.0.0.7
   */
  public long getPluginLongParameter(String key);

  /**
   * Returns the value of a plugin long parameter.
   *
   * @param key The parameter name.
   * @param default_value The default value to return if one is not defined.
   * @return The value of the parameter.
   *
   * @since 3.0.0.7
   */
  public long getPluginLongParameter(String key, long default_value);

  /**
   * Returns the value of a plugin map parameter. The contents of the map must conform
   * to <i>bencodable</i> rules (e.g. <tt>Map</tt>, <tt>Long</tt>, <tt>byte[]</tt>, <tt>List</tt>)
   *
   * @param key The parameter name.
   * @param default_value The default value to return if one is not defined.
   * @return The value of the parameter.
   *
   * @since 2.3.0.1
   */
  public Map getPluginMapParameter(String key, Map default_value);


  /**
   * Returns the value of a plugin string parameter.
   *
   * @param key The parameter name.
   * @return The value of the parameter.
   *
   * @since 2.0.4.2
   */
  public String getPluginStringParameter(String key);

  /**
   * Returns the value of a plugin string parameter.
   *
   * @param key The parameter name.
   * @param default_value The default value to return if one is not defined.
   * @return The value of the parameter.
   *
   * @since 2.0.4.2
   */
  public String getPluginStringParameter(String key, String default_value);

  /**
   * Returns the value of a plugin string-list parameter. If no value is set,
   * an empty string array will be returned.
   *
   * @param key The parameter name.
   * @return The value of the parameter.
   * @since 3.0.5.3
   */
  public String[] getPluginStringListParameter(String key);

  /**
   * Sets the value of a plugin boolean parameter.
   *
   * @param key	The parameter name.
   * @param value The new value for the parameter.
   *
   * @since 2.0.4.2
   */
  public void setPluginParameter(String key, boolean value);

  /**
   * Sets the value of a plugin byte array parameter.
   *
   * @param key	The parameter name.
   * @param value The new value for the parameter.
   *
   * @since 2.1.0.2
   */
  public void setPluginParameter(String key, byte[] value);


  /**
   * Sets the value of a plugin float parameter.
   *
   * @param key	The parameter name.
   * @param value The new value for the parameter.
   *
   * @since 3.0.0.7
   */
  public void setPluginParameter(String key, float value);

  /**
   * Sets the value of a plugin int parameter.
   *
   * @param key	The parameter name.
   * @param value The new value for the parameter.
   *
   * @since 2.0.4.2
   */
  public void setPluginParameter(String key, int value);

  /**
   * Sets the value of a plugin int parameter.
   *
   * @param key	The parameter name.
   * @param value The new value for the parameter.
   * @param global Whether or not this parameter should be made externally accessible.
   *
   * @since 2.5.0.1
   */
  public void setPluginParameter(String key, int value, boolean global);


  /**
   * Sets the value of a plugin long parameter.
   *
   * @param key	The parameter name.
   * @param value The new value for the parameter.
   *
   * @since 3.0.0.7
   */
  public void setPluginParameter(String key, long value);

  /**
   * Sets the value of a plugin string parameter.
   *
   * @param key	The parameter name.
   * @param value The new value for the parameter.
   *
   * @since 2.0.4.2
   */
  public void setPluginParameter(String key, String value);

  /**
   * Sets the value of a plugin string-list parameter.
   *
   * @param key The parameter name.
   * @param value The new value of the parameter.
   * @since 3.0.5.3
   */
  public void setPluginStringListParameter(String key, String[] value);

  /**
   * Sets the value of a plugin color parameter.
   *
   * <p>
   *
   * The value should be an integer array of size 3 representing
   * the red, green and blue values - or <tt>null</tt> to disable it.
   *
   * @param key The parameter name.
   * @param value The new value for the parameter.
   * @return The value of the parameter.
   *
   * @since 3.0.5.3
   */
  public void setPluginColorParameter(String key, int[] value);

  /**
   * Sets the value of a plugin color parameter.
   *
   * <p>
   *
   * The value should be an integer array of size 3 representing
   * the red, green and blue values - or <tt>null</tt> to disable it.
   *
   * <p>
   *
   * The override flag is used to indicate if the value being set is overriding
   * the default value. This is mainly used for interface purposes.
   *
   * @param key The parameter name.
   * @param value The new value for the parameter.
   * @param override <tt>true</tt> if the value is overridden from the default.
   * @return The value of the parameter.
   *
   * @since 3.0.5.3
   */
  public void setPluginColorParameter(String key, int[] value, boolean override);


  /**
   * Sets the value of a plugin list parameter. The contents of the list must conform
   * to <i>bencodable</i> rules (e.g. <tt>Map</tt>, <tt>Long</tt>, <tt>byte[]</tt>, <tt>List</tt>)
   *
   * @param key	The parameter name.
   * @param value The new value for the parameter.
   *
   * @since 2.3.0.1
   */
  public void setPluginListParameter(String key, List value);

  /**
   * Sets the value of a plugin map parameter. The contents of the map must conform
   * to <i>bencodable</i> rules (e.g. <tt>Map</tt>, <tt>Long</tt>, <tt>byte[]</tt>, <tt>List</tt>)
   *
   * @param key	The parameter name.
   * @param value The new value for the parameter.
   *
   * @since 2.3.0.1
   */
  public void setPluginMapParameter(String key, Map value);

  /**
   * Returns the value of a core boolean parameter. Note: the semantics of this
   * method will not be guaranteed - core parameter names may change in the future,
   * and this method will not do any parameter name mapping for you, so take care when
   * using this method.
   *
   * @param key The parameter name.
   * @param default_value The default value to return if one is not defined.
   * @return The value of the parameter.
   *
   * @since 3.0.0.7
   */
  public boolean getUnsafeBooleanParameter(String key);

  /**
   * Returns the value of a core boolean parameter. Note: the semantics of this
   * method will not be guaranteed - core parameter names may change in the future,
   * and this method will not do any parameter name mapping for you, so take care when
   * using this method.
   *
   * @param key The parameter name.
   * @param default_value The default value to return if one is not defined.
   * @return The value of the parameter.
   *
   * @since 3.0.0.5
   */
  public boolean getUnsafeBooleanParameter(String key, boolean default_value);

  /**
   * Returns the value of a core byte array parameter. Note: the semantics of this
   * method will not be guaranteed - core parameter names may change in the future,
   * and this method will not do any parameter name mapping for you, so take care when
   * using this method.
   *
   * @param key The parameter name.
   * @return The value of the parameter.
   *
   * @since 3.0.0.7
   */
  public byte[] getUnsafeByteParameter(String key);

  /**
   * Returns the value of a core byte array parameter. Note: the semantics of this
   * method will not be guaranteed - core parameter names may change in the future,
   * and this method will not do any parameter name mapping for you, so take care when
   * using this method.
   *
   * @param key The parameter name.
   * @param default_value The default value to return if one is not defined.
   * @return The value of the parameter.
   *
   * @since 3.0.0.7
   */
  public byte[] getUnsafeByteParameter(String key, byte[] default_value);

  /**
   * Returns the value of a core color parameter. Note: the semantics of this
   * method will not be guaranteed - core parameter names may change in the future,
   * and this method will not do any parameter name mapping for you, so take care when
   * using this method.
   *
   * <p>
   *
   * It will return <tt>null</tt> if no color parameter is stored, or an
   * integer array of size 4 representing the red, green and blue values,
   * and a flag indicating if the color is an override of the default or
   * not (<tt>0</tt> indicates no override, <tt>1</tt> means it is overridden).
   *
   * <p>
   *
   * In many cases, the override flag can just be ignored.
   *
   * @param key The parameter name.
   * @return The value of the parameter.
   *
   * @since 3.0.5.3
   */
  public int[] getUnsafeColorParameter(String key);

  /**
   * Returns the value of a core color parameter. Note: the semantics of this
   * method will not be guaranteed - core parameter names may change in the future,
   * and this method will not do any parameter name mapping for you, so take care when
   * using this method.
   *
   * <p>
   *
   * It will return <tt>null</tt> if no color parameter is stored, or an
   * integer array of size 4 representing the red, green and blue values,
   * and a flag indicating if the color is an override of the default or
   * not (<tt>0</tt> indicates no override, <tt>1</tt> means it is overridden).
   *
   * <p>
   *
   * In many cases, the override flag can just be ignored.
   *
   * @param key The parameter name.
   * @param default_value The default value to return if one is not defined.
   * @return The value of the parameter.
   *
   * @since 3.0.5.3
   */
  public int[] getUnsafeColorParameter(String key, int[] default_value);

  /**
   * Returns the value of a core float parameter. Note: the semantics of this
   * method will not be guaranteed - core parameter names may change in the future,
   * and this method will not do any parameter name mapping for you, so take care when
   * using this method.
   *
   * @param key The parameter name.
   * @return The value of the parameter.
   *
   * @since 3.0.0.7
   */
  public float getUnsafeFloatParameter(String key);

  /**
   * Returns the value of a core float parameter. Note: the semantics of this
   * method will not be guaranteed - core parameter names may change in the future,
   * and this method will not do any parameter name mapping for you, so take care when
   * using this method.
   *
   * @param key The parameter name.
   * @param default_value The default value to return if one is not defined.
   * @return The value of the parameter.
   *
   * @since 3.0.0.5
   */
  public float getUnsafeFloatParameter(String key, float default_value);

  /**
   * Returns the value of a core int parameter. Note: the semantics of this
   * method will not be guaranteed - core parameter names may change in the future,
   * and this method will not do any parameter name mapping for you, so take care when
   * using this method.
   *
   * @param key The parameter name.
   * @return The value of the parameter.
   *
   * @since 3.0.0.7
   */
  public int getUnsafeIntParameter(String key);

  /**
   * Returns the value of a core int parameter. Note: the semantics of this
   * method will not be guaranteed - core parameter names may change in the future,
   * and this method will not do any parameter name mapping for you, so take care when
   * using this method.
   *
   * @param key The parameter name.
   * @param default_value The default value to return if one is not defined.
   * @return The value of the parameter.
   *
   * @since 3.0.0.5
   */
  public int getUnsafeIntParameter(String key, int default_value);

  /**
   * Returns the value of a core long parameter. Note: the semantics of this
   * method will not be guaranteed - core parameter names may change in the future,
   * and this method will not do any parameter name mapping for you, so take care when
   * using this method.
   *
   * @param key The parameter name.
   * @return The value of the parameter.
   *
   * @since 3.0.0.7
   */
  public long getUnsafeLongParameter(String key);

  /**
   * Returns the value of a core long parameter. Note: the semantics of this
   * method will not be guaranteed - core parameter names may change in the future,
   * and this method will not do any parameter name mapping for you, so take care when
   * using this method.
   *
   * @param key The parameter name.
   * @param default_value The default value to return if one is not defined.
   * @return The value of the parameter.
   *
   * @since 3.0.0.5
   */
  public long getUnsafeLongParameter(String key, long default_value);

  /**
   * Returns the value of a core string parameter. Note: the semantics of this
   * method will not be guaranteed - core parameter names may change in the future,
   * and this method will not do any parameter name mapping for you, so take care when
   * using this method.
   *
   * @param key The parameter name.
   * @return The value of the parameter.
   *
   * @since 3.0.0.7
   */
  public String getUnsafeStringParameter(String key);

  /**
   * Returns the value of a core string parameter. Note: the semantics of this
   * method will not be guaranteed - core parameter names may change in the future,
   * and this method will not do any parameter name mapping for you, so take care when
   * using this method.
   *
   * @param key The parameter name.
   * @param default_value The default value to return if one is not defined.
   * @return The value of the parameter.
   *
   * @since 3.0.0.5
   */
  public String getUnsafeStringParameter(String key, String default_value);

  /**
   * Sets the value of a core boolean parameter. Note: the semantics of this
   * method will not be guaranteed - core parameter names may change in the future,
   * and this method will not do any parameter name mapping for you, so take care when
   * using this method.
   *
   * @param key	The parameter name, which must be one defined from the above core constants.
   * @param value The new value for the parameter.
   *
   * @since 3.0.0.5
   */
  public void setUnsafeBooleanParameter(String key, boolean value);

  /**
   * Sets the value of a core byte array parameter. Note: the semantics of this
   * method will not be guaranteed - core parameter names may change in the future,
   * and this method will not do any parameter name mapping for you, so take care when
   * using this method.
   *
   * @param key	The parameter name, which must be one defined from the above core constants.
   * @param value The new value for the parameter.
   *
   * @since 3.0.0.7
   */
  public void setUnsafeByteParameter(String key, byte[] value);

  /**
   * Returns the value of a core color parameter. Note: the semantics of this
   * method will not be guaranteed - core parameter names may change in the future,
   * and this method will not do any parameter name mapping for you, so take care when
   * using this method.
   *
   * <p>
   *
   * The value should be an integer array of size 3 representing
   * the red, green and blue values - or <tt>null</tt> to disable it.
   *
   * @param key The parameter name.
   * @param value The new value for the parameter.
   *
   * @since 3.0.5.3
   */
  public void setUnsafeColorParameter(String key, int[] value);

  /**
   * Returns the value of a core color parameter. Note: the semantics of this
   * method will not be guaranteed - core parameter names may change in the future,
   * and this method will not do any parameter name mapping for you, so take care when
   * using this method.
   *
   * <p>
   *
   * The value should be an integer array of size 3 representing
   * the red, green and blue values - or <tt>null</tt> to disable it.
   *
   * <p>
   *
   * The override flag is used to indicate if the value being set is overriding
   * the default value. This is mainly used for interface purposes.
   *
   * @param key The parameter name.
   * @param value The default value to return if one is not defined.
   * @param override <tt>true</tt> if the value is overridden from the default.
   *
   * @since 3.0.5.3
   */
  public void setUnsafeColorParameter(String key, int[] value, boolean override);

  /**
   * Sets the value of a core float parameter. Note: the semantics of this
   * method will not be guaranteed - core parameter names may change in the future,
   * and this method will not do any parameter name mapping for you, so take care when
   * using this method.
   *
   * @param key	The parameter name, which must be one defined from the above core constants.
   * @param value The new value for the parameter.
   *
   * @since 3.0.0.5
   */
  public void setUnsafeFloatParameter(String key, float value);

  /**
   * Sets the value of a core int parameter. Note: the semantics of this
   * method will not be guaranteed - core parameter names may change in the future,
   * and this method will not do any parameter name mapping for you, so take care when
   * using this method.
   *
   * @param key	The parameter name, which must be one defined from the above core constants.
   * @param value The new value for the parameter.
   *
   * @since 3.0.0.5
   */
  public void setUnsafeIntParameter(String key, int value);

  /**
   * Sets the value of a core long parameter. Note: the semantics of this
   * method will not be guaranteed - core parameter names may change in the future,
   * and this method will not do any parameter name mapping for you, so take care when
   * using this method.
   *
   * @param key	The parameter name, which must be one defined from the above core constants.
   * @param value The new value for the parameter.
   *
   * @since 3.0.0.5
   */
  public void setUnsafeLongParameter(String key, long value);

  /**
   * Sets the value of a core string parameter. Note: the semantics of this
   * method will not be guaranteed - core parameter names may change in the future,
   * and this method will not do any parameter name mapping for you, so take care when
   * using this method.
   *
   * @param key	The parameter name, which must be one defined from the above core constants.
   * @param value The new value for the parameter.
   *
   * @since 3.0.0.5
   */
  public void setUnsafeStringParameter(String key, String value);

	/**
	 * Removes the plugin parameter with the given name.
	 *
	 * @param key Name of the parameter.
	 * @return <tt>true</tt> if the parameter was found and removed.
	 */
	public boolean removePluginParameter(String key);

	/**
	 * Removes the plugin color parameter with the given name.
	 *
	 * @param key Name of the parameter.
	 * @return <tt>true</tt> if the parameter was found and removed.
	 *
	 * @since 3.0.5.3
	 */
	public boolean removePluginColorParameter(String key);

  /**
   * @return the prefix used when storing configuration values in the config file for
   * this plugin's config parameters
   *
   * @since 2.1.0.0
   */

	public String
	getPluginConfigKeyPrefix();

	public com.biglybt.pif.config.ConfigParameter
	getParameter(
			String		key );

	public com.biglybt.pif.config.ConfigParameter
	getPluginParameter(
			String		key );

	public boolean
	isNewInstall();



	/**
	 * Returns a map<String,Object> giving parameter names -> parameter values. Value can be Long or String
	 * as the type is actually not known by the core (might fix one day). Therefore, float values are actually
	 * represented by their String format:
	 *
	 * boolean - Long 0 or 1
	 * int     - Long.intValue
	 * float   - String value
	 * String  - String
	 *
	 * Unsafe methods - existence/semantics of parameters not guaranteed to be maintained across versions
	 * If something changes and breaks your plugin, don't come complaining to me
	 * @since 2.5.0.3
	 */

	public Map
	getUnsafeParameterList();

  /**
   * make sure you save it after making changes!
   *
   * @since 2.0.8.0
   */
	public void save() throws PluginException;

		/**
		 * Returns a file that can be used by the plugin to save user-specific state.
		 * <p>
		 *
		 * This will be <tt>client-user-dir/plugins/plugin-name/name</tt>.
		 * @param name
		 * @return
		 */

	public File
	getPluginUserFile(
		String	name );

	  /**
	   * Returns true if a core parameter with the given name exists.
	   * @param key The name of the parameter to check.
	   * @since 2.5.0.2
	   */
	public boolean hasParameter(String param_name);

	  /**
	   * Returns true if a plugin parameter with the given name exists.
	   * @param key The name of the parameter to check.
	   * @since 2.5.0.2
	   */
	public boolean hasPluginParameter(String param_name);

	public void
	addListener(
			PluginConfigListener	l );

	public void
	removeListener(
			PluginConfigListener	l );

	/**
	 * @param _key
	 *
	 * @since 2.5.0.1
	 */
	public void setPluginConfigKeyPrefix(String _key);

	/**
	 * Enable the plugin to store configuration parameters into a separate
	 * external configuration file. <b>Note:</b> once this method is called,
	 * you need to invoke {@link com.biglybt.pif.config.PluginConfigSource#initialize()} for the
	 * external configuration file to be properly integrated with the client.
	 *
	 * <p>
	 *
	 * When a plugin is first initialised, it should call this method as
	 * soon as possible during the initialization stage. This then configures
	 * the PluginConfig object to store any parameter values into an external
	 * configuration file (rather than storing it directly with the main
	 * configuration file used by the client).
	 *
	 * <p>
	 *
	 * When this method is invoked, it will return an object which allows
	 * the filename to be chosen - it allows a limited amount of manipulation
	 * of the configuration file. This method only needs to be invoked once.
	 *
	 * <p>
	 *
	 * All methods which get and set plugin parameters on this object will store
	 * data in the external configuration file. The use of classes like
	 * {@link com.biglybt.pif.ui.model.BasicPluginConfigModel BasicPluginConfigModel}
	 * will automatically integrate parameters to the external configuration
	 * source.
	 *
	 * <p>
	 *
	 * However, if you use any other mechanism to store parameter data, you may need to call
	 * the {@link com.biglybt.pif.ui.config.PluginConfigSource#registerParameter registerParameter}
	 * to integrate the parameter properly.
	 *
	 * @since 3.0.5.3
	 * @return The <tt>PluginConfigSource</tt> object representing the external configuration file.
	 */
	public com.biglybt.pif.config.PluginConfigSource enableExternalConfigSource();

	/**
	 * Returns the <tt>PluginConfigSource</tt> object used for this plugin configuration (or
	 * <tt>null</tt> if an external configuration object isn't used).
	 *
	 * @since 3.0.5.3
	 * @return The PluginConfigSource object.
	 */
	public com.biglybt.pif.config.PluginConfigSource getPluginConfigSource();

	/**
	 * Sets the plugin configuration source object to use for storing parameters for this
	 * plugin config object.
	 *
	 * <p>
	 *
	 * This method should only be used as an alternative to {@link #enableExternalConfigSource()}.
	 * You will only need to use this method if you use the
	 * {@link PluginInterface#getLocalPluginInterface(Class, String) getLocalPluginInterface}
	 * method to store data in a separate namespace, but want to use the same configuration file to
	 * store data in.
	 *
	 * @param source The PluginConfigSource object to use.
	 * @since 3.0.5.3
	 */
	public void setPluginConfigSource(com.biglybt.pif.config.PluginConfigSource source);
}
