/*
 * File    : PluginInterface.java
 * Created : 2 nov. 2003 18:48:47
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

import java.util.Properties;

import com.biglybt.pif.clientid.ClientIDManager;
import com.biglybt.pif.ddb.DistributedDatabase;
import com.biglybt.pif.dht.mainline.MainlineDHTManager;
import com.biglybt.pif.download.DownloadManager;
import com.biglybt.pif.ipc.IPCInterface;
import com.biglybt.pif.ipfilter.IPFilter;
import com.biglybt.pif.logging.Logger;
import com.biglybt.pif.messaging.MessageManager;
import com.biglybt.pif.network.ConnectionManager;
import com.biglybt.pif.platform.PlatformManager;
import com.biglybt.pif.sharing.ShareException;
import com.biglybt.pif.sharing.ShareManager;
import com.biglybt.pif.torrent.TorrentManager;
import com.biglybt.pif.tracker.Tracker;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.update.UpdateManager;
import com.biglybt.pif.utils.ShortCuts;
import com.biglybt.pif.utils.Utilities;


/**
 * Defines the communication interface between the client and Plugins
 * @author Olivier
 */
public interface PluginInterface {

	/**
	 * Retrieve the name of the application.
   *
   * @return the Application's name
   *
   * @since 2.1.0.0
   */
	public String getAzureusName();

	/**
	 * Returns the name of the application that the user sees - if you need to
	 * display the name of the program, you should use this method.
	 *
	 * @since 3.0.5.3
	 */
	public String getApplicationName();

	/** Retrieve the Application's version as a string.
	 *
	 * @return Application's version.  Typically in the following formats (regexp):<br>
	 *         [0-9]+\.[0-9]+\.[0-9]+\.[0-9]+<br>
	 *         [0-9]+\.[0-9]+\.[0-9]+\.[0-9]+_CVS<br>
	 *         [0-9]+\.[0-9]+\.[0-9]+\.[0-9]+_B[0-9]+
   *
   * @since 2.1.0.0
	 */
	public String
	getApplicationVersion();

	/**
   * Gives access to the tracker functionality
   * @return The tracker
   *
   * @since 2.0.6.0
   */
  public Tracker getTracker();

  /**
   * Gives access to the logger
   * @return The logger
   *
   * @since 2.0.7.0
   */
  public Logger getLogger();

  /**
   * Gives access to the IP filter
   * @return An object that allows access to IP Filtering
   *
   * @since 2.0.8.0
   */
  public IPFilter
  getIPFilter();

  /**
   * Gives access to the download manager
   * @return An object that allows management of downloads
   *
   * @since 2.0.7.0
   */
  public DownloadManager
  getDownloadManager();

  /**
   * Gives access to the sharing functionality
   *
   * @since 2.0.7.0
   */
  public ShareManager
  getShareManager()
  	throws ShareException;

  /**
   * Gives access to the torrent manager
   * @return An object to manage torrents
   *
   * @since 2.0.8.0
   */
  public TorrentManager
  getTorrentManager();

  /**
   * access to various utility functions
   *
   * @since 2.1.0.0
   */
  public Utilities
  getUtilities();

  /**
   * access to a set of convenience routines for doing things in a quicker, although less
   * structured, fashion
   *
   * @since 2.1.0.0
   */
  public ShortCuts
  getShortCuts();

  /**
   * access to UI extension features
   *
   * @since 2.1.0.0
   */
  public UIManager
  getUIManager();

  /**
   * access to the update manager used to update plugins. required for non-Azureus SF hosted
   * plugins (SF ones are managed automatically)
   *
   * @since 2.1.0.0
   */
  public UpdateManager
  getUpdateManager();

	/**
   * gives access to the plugin properties
   * @return the properties from the file plugin.properties
   *
   * @since 2.0.4.0
   */
  public Properties getPluginProperties();

  /**
   * Gives access to the plugin installation path - note, if you want to use this
   * path to store data files in, it would be better for you to use
   * {@link PluginConfig#getPluginUserFile(String)} instead.
   * @return the full path the plugin is installed in
   *
   * @since 2.0.4.0
   */
  public String getPluginDirectoryName();

  /**
   * gives access to the per-user plugin directory. Useful for shared plugins that need to store
   * per-user state. Will be same as getPluginDirectoryName for per-user installed plugins
   * directory may not yet exist
   */
  public String getPerUserPluginDirectoryName();

  /**
   * Returns the value of "plugin.name" if it exists in the properties file, otherwise the directory name is returned.
   * @since 2.1.0.0
   */
  public String getPluginName();

  /**
   * Returns the version number of the plugin it if can be deduced from either the name of
   * the jar file it is loaded from or the properties file. null otherwise
   *
   * @return Version number as a string, or null
   *
   * @since 2.1.0.0
   */
  public String
  getPluginVersion();

  /**
   * Returns an identifier used to identify this particular plugin
   *
   * @since 2.1.0.0
   */
  public String
  getPluginID();

	/**
   * Gives access to the plugin config interface
   * @return the PluginConfig object associated with this plugin
   */
  public PluginConfig getPluginconfig();


	/**
   * gives access to the ClassLoader used to load the plugin
   *
   * @since 2.0.8.0
   */
  public ClassLoader
  getPluginClassLoader();

	/**
	 * Returns an initialised plugin instance with its own scope (e.g. for config params).
	 * Designed for loading secondary plugins directly from a primary one.
	 * Note - ensure that the bundled secondary plugins do *not* contain a plugin.properties as
	 * this will cause no end of problems.
	 * @param plugin	must implement Plugin
	 * @param id        the unique id of this plugin (used to scope config params etc)
	 */
  public PluginInterface
  getLocalPluginInterface(
	Class		plugin,
	String		id )

  	throws PluginException;

	/**
	 * Get the inter-plugin-communications interface for this plugin
	 */
  public IPCInterface
  getIPC ();

  /**
   * Gives access to the plugin itself
   *
   * @since 2.1.0.0
   */
  public Plugin
  getPlugin();


	/**
 	 * Indicates whether or not the current thread is the one responsible for running
 	 * plugin initialisation
 	 */
  public boolean
  isInitialisationThread();

  /**
   * gives access to the plugin manager
   *
   * @since 2.1.0.0
   */
  public PluginManager
  getPluginManager();

	/**
	 *
	 * @since 2.2.0.3
	 */
  public ClientIDManager
  getClientIDManager();


  /**
   * Get the connection manager.
   * @since 2.2.0.3
   * @return manager
   */
  public ConnectionManager getConnectionManager();


  /**
   * Get the peer messaging manager.
   * @since 2.2.0.3
   * @return manager
   */
  public MessageManager getMessageManager();


  /**
   * Get the distributed database
   * @since 2.2.0.3
   */
  public DistributedDatabase
  getDistributedDatabase();

  /**
   * Gets the platform manager that gives access to native functionality
   */
  public PlatformManager
  getPlatformManager();

  /**
   *
   * @since 2.0.7.0
   */
  public void
  addListener(
  	PluginListener	l );

  /**
   *
   * @since 2.0.7.0
   */
  public void
  removeListener(
  	PluginListener	l );

	/**
	 * Fire a plugin-specific event. See PluginEvent for details of type values to use
	 * @since 2.4.0.3
	 * @param event plugin event
	 */
  public void
  firePluginEvent(
	PluginEvent		event );

  /**
   *
   * @since 2.0.8.0
   */
  public void
  addEventListener(
  	PluginEventListener	l );

  /**
   *
   * @since 2.0.8.0
   */
  public void
  removeEventListener(
  	PluginEventListener	l );

  /**
   * Returns the manager object for registering plugins that connect to the
   * Mainline DHT.
   *
   * @since 3.0.4.3
   */
  public MainlineDHTManager getMainlineDHTManager();

  /**
   * Returns an object that provides information the current state of the plugin,
   * and provides various mechanisms to query and control plugins and their
   * integration with the client at a low-level.
   *
   * @since 3.1.1.1
   */
  public PluginState getPluginState();
}
