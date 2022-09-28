/**
 * File: PluginState.java
 * Date: 19 Aug 2008
 * Author: Allan Crooks
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the COPYING file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package com.biglybt.pif;

/**
 * This object provides information the current state of the plugin, and
 * provides various mechanisms to query and control plugins and their integration
 * with the client at a low-level.
 *
 * Most plugins will have no need to interact with this object - it is primarily
 * used by the client for plugin management.
 *
 * @since 3.1.1.1
 */
public interface PluginState {

	  /**
	   * Returns <tt>true</tt> if the plugin is set to load at startup, <tt>false</tt> otherwise.
	   */
	  public boolean isLoadedAtStartup();

	  /**
	   * Sets whether the plugin is loaded at startup or not.
	   */
	  public void setLoadedAtStartup(boolean load_at_startup);

	  /**
	   * Returns <tt>true</tt> if there was a problem loading or initialising the plugin.
	   */
	  public boolean hasFailed();

	  /**
	   * Returns <tt>true</tt> if the plugin has been marked as disabled, and prevented
	   * from initialising.
	   */
	  public boolean isDisabled();

	  /**
	   * Sets whether the plugin can be loaded or not. If you are trying to affect if the plugin
	   * can be loaded at startup - use {@link #setLoadedAtStartup(boolean)} instead. This needs
	   * to be called prior to a plugin's initialisation to take effect.
	   *
	   * @param disabled
	   */
	  public void setDisabled(boolean disabled);

	  /**
	   * Built-in plugins are those used internally by the client, for example
	   * the UPnP plugin.
	   */
	  public boolean isBuiltIn();

	  /**
	   * Whether or not this is a mandatory plugin. Mandatory plugins take priority over update checks, for example,
	   * over optional ones.
	   */
	  public boolean isMandatory();

	  /**
	   * Returns <tt>true</tt> if the plugin is running, returns <tt>false</tt> if the
	   * plugin isn't running for some reason.
	   */
	  public boolean isOperational();

	  public boolean isInitialisationComplete();

	  public boolean isRestartPending();
	  
	  public void setRestartPending( boolean b );
	  
	  /**
	   * Uninstall this plugin if it has been loaded from a plugin directory.
	   * Deletes the plugin directory.
	   */
	  public void uninstall() throws PluginException;

	  public boolean isShared();

	  public boolean isUnloadable();

	  public boolean isUnloaded();

	  public void unload() throws PluginException;

	  public void reload() throws PluginException;
}
