/*
 * Created on 6 May 2008
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
package com.biglybt.pif.config;

import java.io.File;

import com.biglybt.pif.Plugin;
import com.biglybt.pif.PluginConfig;
import com.biglybt.pif.PluginInterface;

/**
 * This interface provides a mechanism for plugins to store configuration
 * settings in a separate configuration file (rather than the main client
 * configuration file).
 *
 * <p>
 *
 * <b>Important:</b> If you are using this class for a plugin which previously
 * used to store configuration settings in the main configuration file
 * (which is what most existing plugins do), you should call the
 * {@link #forceSettingsMigration()} method to get existing settings moved
 * over - please read the comments for that file if you need to do that.
 *
 * <p>
 *
 * To create an instance of this file, you need to call
 * {@link PluginConfig#enableExternalConfigSource()}. Once you have an instance,
 * you then need to call {@link #initialize()} to configure it - though there
 * are additional methods that you can call for additional configuration.
 *
 * <p><b>Note:</b> Only for implementation by Core, not plugins.</p>
 */
public interface PluginConfigSource {

	/**
	 * This initializes this configuration object and gets the external
	 * configuration file integrated with the client.
	 *
	 * <p>
	 *
	 * It performs the following steps:
	 * <ul>
	 *   <li>Loads the data of any existing config file into the client.
	 *   <li>Registers all parameters in the file to be stored inside
	 *       this configuration file (so all changes will be stored here).
	 *   <li>Adds a hook to allow it to be automatically saved when
	 *       the client autosaves its own internal configuration files.
	 *   <li>Adds a hook to intercept any configuration settings created
	 *       and used by the plugin and stores it internally (rather than
	 *       being saved in the main the client config file).
	 * </ul>
	 */
	public void initialize();

	/**
	 * This method sets the filename for the configuration file that this
	 * object links to - this must be done before the {@link #initialize()}
	 * method is called.
	 *
	 * @param filename The filename to use.
	 */
	public void setConfigFilename(String filename);

	/**
	 * Returns a file object which represents the location of the configuration
	 * file that this object interacts with.
	 */
	public File getConfigFile();

	/**
	 * Manually saves the configuration settings recorded by this object to
	 * disk. This isn't normally required, as the client will automatically
	 * save the configuration file when the main configuration file is
	 * saved, but you can choose to save on demand if you wish.
	 *
	 * <p>
	 *
	 * @param force <tt>true</tt> if you want the file to be written to
	 *    regardless of whether there are any changes, <tt>false</tt> if
	 *    you only want to save the file if there are unsaved changes.
	 */
	public void save(boolean force);

	/**
	 * If your plugin previously used to store data in the main configuration file,
	 * you can call this method (which needs to be done soon after initialization)
	 * which will move all monitored parameters over to this object.
	 *
	 * <p>
	 *
	 * You have to call this method before you initialize the object. It's also
	 * recommended that if you call this method, that you call {@link #save(boolean)}
	 * to save any settings copied across - probably best to be done as the last thing
	 * of the {@link Plugin#initialize(PluginInterface)} method.
	 */
	public void forceSettingsMigration();
}
