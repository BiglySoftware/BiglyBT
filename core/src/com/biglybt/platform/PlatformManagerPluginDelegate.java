/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.biglybt.platform;

import java.util.Properties;

import com.biglybt.pif.Plugin;
import com.biglybt.pif.PluginException;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.update.UpdatableComponent;
import com.biglybt.pif.update.UpdateChecker;

/**
 * @author TuxPaper
 * @created Jul 24, 2007
 *
 */
public class PlatformManagerPluginDelegate
	implements Plugin, UpdatableComponent	// we have to implement this as it used as a mixin to declare that this plugin handles its own update process
{

	public static void
	load(
		PluginInterface		plugin_interface )
	{
			// name it during initialisation

		plugin_interface.getPluginProperties().setProperty( "plugin.name", 	"Platform-Specific Support" );
	}

	// @see com.biglybt.pif.Plugin#initialize(com.biglybt.pif.PluginInterface)
	@Override
	public void initialize(PluginInterface pluginInterface)
			throws PluginException {
		try {
			PlatformManager platform = PlatformManagerFactory.getPlatformManager();
	
			int platformType = platform.getPlatformType();
			if ( platformType == PlatformManager.PT_WINDOWS ){
				Plugin plugin = (Plugin) Class.forName("com.biglybt.platform.win32.PlatformManagerUpdateChecker").newInstance();
				plugin.initialize(pluginInterface);
			}else if ( platformType == PlatformManager.PT_MACOSX ){
				Plugin plugin = (Plugin) Class.forName("com.biglybt.platform.macosx.PlatformManagerUpdateChecker").newInstance();
				plugin.initialize(pluginInterface);
			}else if ( platformType == PlatformManager.PT_UNIX ){
				Plugin plugin = (Plugin) Class.forName("com.biglybt.platform.unix.PlatformManagerUnixPlugin").newInstance();
				plugin.initialize(pluginInterface);
			}else{
				Properties pluginProperties = pluginInterface.getPluginProperties();
				pluginProperties.setProperty("plugin.name", "Platform-Specific Support");
				pluginProperties.setProperty("plugin.version", "1.0");
				pluginProperties.setProperty("plugin.version.info",
						"Not required for this platform");
			}
		} catch (PluginException t) {
			throw t;
		} catch (Throwable t) {
			throw new PluginException(t);
		}
	}

	@Override
	public String
	getName()
	{
		return( "Mixin only" );
	}


	@Override
	public int
	getMaximumCheckTime()
	{
		return( 0 );
	}

	@Override
	public void
	checkForUpdate(
		UpdateChecker	checker )
	{
	}
}
