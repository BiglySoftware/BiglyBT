/*
 * Created on 28-Nov-2004
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

package com.biglybt.pif.installer;

import java.io.File;
import java.util.Map;

import com.biglybt.pif.PluginException;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.update.UpdateCheckInstance;

/**
 * @author parg
 *
 */

public interface
PluginInstaller
{
		/**
		 * Gives access to the list of standard plugins listed on the client's website
		 * @return
		 */

	public StandardPlugin[]
	getStandardPlugins()

		throws PluginException;

	public StandardPlugin
  	getStandardPlugin(
  		String		id )

  		throws PluginException;

		/**
		 * Requests any registered listeners to initiate a plugin install process
		 * @param plugin
		 * @throws PluginException
		 */

	public void
	requestInstall(
		String				reason,
		InstallablePlugin 	plugin )

		throws PluginException;

		/**
		 * Install one of more plugins in a single operation
		 * @param plugins
		 */

	public void
	install(
		InstallablePlugin[]	plugins,
		boolean				shared )

		throws PluginException;

	public UpdateCheckInstance
	install(
		InstallablePlugin[]			plugins,
		boolean						shared,
		Map<Integer,Object>			properties,
		PluginInstallationListener	listener )

		throws PluginException;

		/**
		 * Installs a plugin from a file - must be either a ZIP file or a JAR file as per
		 * normal plugin update semantics. Name of file must be of the form:
		 *     <plugin_id> "_" <plugin_version> "." ["jar" | "zip" ].
		 * For example
		 *     myplugin_1.0.jar
		 * @param file
		 * @throws PluginException
		 */

	public FilePluginInstaller
	installFromFile(
		File		file )

		throws PluginException;

	public void
	uninstall(
		PluginInterface		plugin_interface )


		throws PluginException;

	public void
	uninstall(
		PluginInterface[]	plugin_interfaces )

		throws PluginException;

	public void
	uninstall(
		PluginInterface[]			plugin_interfaces,
		PluginInstallationListener	listener )

		throws PluginException;

	public UpdateCheckInstance
	uninstall(
		PluginInterface[]			plugin_interfaces,
		PluginInstallationListener	listener,
		Map<Integer,Object>			properties )

		throws PluginException;

	public void
	addListener(
		PluginInstallerListener		l );

	public void
	removeListener(
		PluginInstallerListener		l );
}
