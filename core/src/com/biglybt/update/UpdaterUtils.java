/*
 * Created on May 31, 2006 1:21:29 PM
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
package com.biglybt.update;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.FileUtil;
import com.biglybt.pif.PluginInterface;

/**
 * Utility functions for Updater Plugin.
 * <p>
 * Moved from UpdateUpdateChecker to reduce (cyclical) references
 */
public class UpdaterUtils
{
	public static String AZUPDATER_PLUGIN_ID 			= "azupdater";
	public static String AZUPDATERPATCHER_PLUGIN_ID 	= "azupdaterpatcher";

	protected static String AZUPNPAV_PLUGIN_ID 			= "azupnpav";

	public static boolean
	disableNativeCode(
		String version )
	{
		try {
			File plugin_dir = null;

			// we can't check the user-dir here due to crazy recursion problems
			// during startup (platform manager init etc)

			File shared_plugin_dir = FileUtil.getApplicationFile("plugins");

			File shared_updater_plugin = new File(shared_plugin_dir, AZUPDATER_PLUGIN_ID);

			if (shared_updater_plugin.exists()) {

				plugin_dir = shared_updater_plugin;
			}

			if ( plugin_dir == null ){

				return (false);
			}

			return (new File(plugin_dir, "disnat" + version).exists());

		} catch (Throwable e) {

			e.printStackTrace();
		}

		return (false);
	}

	public static void
	checkBootstrapPlugins()
	{
		try{
			File	target_props = getPropsIfNotPresent( AZUPDATER_PLUGIN_ID, true );

			if ( target_props != null ){

				writePluginProperties(
					target_props,
					new String[]{
						"plugin.class=com.biglybt.update.UpdaterUpdateChecker;com.biglybt.update.UpdaterPatcher",
						"plugin.name=BiglyBT Update Support;BiglyBT Updater Support Patcher" });

			}

				// has to go into USER dir as currently VISTA plugin install into shared is BROKEN!

			target_props = getPropsIfNotPresent( AZUPNPAV_PLUGIN_ID, false );

			if ( target_props != null ){

				writePluginProperties(
					target_props,
					new String[]{
						"plugin.class=com.aelitis.azureus.plugins.upnpmediaserver.UPnPMediaServer",
						"plugin.name=UPnP Media Server",
						"plugin.id=azupnpav" });
			}

		}catch( Throwable e){

			Debug.printStackTrace(e);
		}
	}

	protected static void
	writePluginProperties(
		File		target,
		String[]	lines )
	{
		try{
			PrintWriter pw = null;

			try{
				target.getParentFile().mkdirs();

				pw = new PrintWriter(new FileWriter(target));

				for (int i=0;i<lines.length;i++){

					pw.println( lines[i] );
				}

				pw.println( "plugin.install_if_missing=yes" );

			}finally{

				if ( pw != null ){

					pw.close();
				}
			}

			if (!target.exists()) {

				throw (new Exception("Failed to write '" + target.toString() + "'"));
			}
		} catch (Throwable e) {

			Logger.log(
				new LogAlert(
					LogAlert.UNREPEATABLE,
					"Plugin bootstrap: initialisation error for " + target, e ));
		}
	}

	protected static File
	getPropsIfNotPresent(
		String		id,
		boolean		use_shared )
	{
		File user_plugin_dir = FileUtil.getUserFile("plugins");

		File user_plugin = new File(user_plugin_dir, id);

		File user_props = new File( user_plugin, "plugin.properties" );

		if ( user_props.exists()){

			return( null );
		}

		File shared_plugin_dir = FileUtil.getApplicationFile("plugins");

		File shared_plugin = new File(shared_plugin_dir, id);

		File shared_props = new File(shared_plugin, "plugin.properties");

		if ( shared_props.exists()){

			return( null );
		}

		if ( use_shared ){

			FileUtil.mkdirs( shared_plugin );

			return( shared_props );

		}else{

			FileUtil.mkdirs( user_plugin );

			return( user_props );
		}
	}

	public static String
	getUpdaterPluginVersion()
	{
		try {
  		PluginInterface pi = CoreFactory.getSingleton().getPluginManager().getPluginInterfaceByID(AZUPDATER_PLUGIN_ID, false );
  		if (pi != null) {
  			String version = pi.getPluginVersion();
  			if (version != null) {
  				return version;
  			}
  		}
		} catch (Throwable t) {
		}
		return "0";
	}
}
