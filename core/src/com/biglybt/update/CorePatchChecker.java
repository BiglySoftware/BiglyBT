/*
 * Created on 25-May-2004
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

package com.biglybt.update;

/**
 * @author parg
 *
 */

import java.io.*;
import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.AETemporaryFileHandler;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemProperties;
import com.biglybt.pif.Plugin;
import com.biglybt.pif.PluginException;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.logging.LoggerChannel;
import com.biglybt.pif.update.*;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloader;


public class
CorePatchChecker
	implements Plugin, UpdatableComponent, UpdateCheckInstanceListener
{
	private static final LogIDs LOGID = LogIDs.CORE;
	public static final boolean	TESTING	= false;

	protected PluginInterface	plugin_interface;

	private Map<UpdateCheckInstance,Update>	my_updates = new HashMap<>(1);

	@Override
	public void
	initialize(
		PluginInterface _plugin_interface )

	  	throws PluginException
	{
		plugin_interface	= _plugin_interface;

		plugin_interface.getPluginProperties().setProperty( "plugin.version", 	"1.0" );
		plugin_interface.getPluginProperties().setProperty( "plugin.name", 		"Core Patcher (level=" + CorePatchLevel.getCurrentPatchLevel() + ")" );

		if ( TESTING || !Constants.isCVSVersion()){

			if ( TESTING ){

				System.out.println( "CorePatchChecker: TESTING !!!!" );
			}

			plugin_interface.getUpdateManager().registerUpdatableComponent( this, false );
		}
	}

	@Override
	public String
	getName()
	{
		return( "Core Patch Checker");
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
		try{
			UpdateCheckInstance	inst = checker.getCheckInstance();

			inst.addListener( this );

			my_updates.put(
				inst,
				checker.addUpdate( "Core Patch Checker", new String[0], "", "",
								new ResourceDownloader[0],
								Update.RESTART_REQUIRED_MAYBE ));
		}finally{

			checker.completed();
		}
	}

	@Override
	public void
	cancelled(
		UpdateCheckInstance		instance )
	{
		Update update = my_updates.remove( instance );

		if ( update != null ){

			update.cancel();
		}
	}

	@Override
	public void
	complete(
		final UpdateCheckInstance		instance )
	{
		Update my_update = my_updates.remove( instance );

		if ( my_update != null ){

			my_update.complete( true );
		}

		Update[]	updates = instance.getUpdates();

		final PluginInterface updater_plugin = plugin_interface.getPluginManager().getPluginInterfaceByClass( UpdaterUpdateChecker.class );

		for (int i=0;i<updates.length;i++){

			final Update	update = updates[i];

			Object	user_object = update.getUserObject();

			if ( user_object != null && user_object == updater_plugin ){

				// OK, we have an updater update

				if (Logger.isEnabled())
					Logger.log(new LogEvent(LOGID, "Core Patcher: updater update found"));

				update.setRestartRequired( Update.RESTART_REQUIRED_MAYBE );

				update.addListener(new UpdateListener() {
					@Override
					public void complete(Update update) {
						if (Logger.isEnabled())
							Logger.log(new LogEvent(LOGID,
									"Core Patcher: updater update complete"));

						patch(instance, update, updater_plugin);
					}

					@Override
					public void
					cancelled(
						Update update )
					{
					}
				});
			}
		}
	}

	protected void
	patch(
		UpdateCheckInstance		instance,
		Update					updater_update,
		PluginInterface 		updater_plugin )
	{
		try{
				// use the update plugin to log stuff....

			ResourceDownloader rd_log = updater_update.getDownloaders()[0];

			File[]	files = new File(updater_plugin.getPluginDirectoryName()).listFiles();

			if ( files == null ){

				if (Logger.isEnabled())
					Logger.log(new LogEvent(LOGID,
							"Core Patcher: no files in plugin dir!!!"));

				return;
			}

			String	patch_prefix = "BiglyBT_" + Constants.getBaseVersion() + "_P";

			int		highest_p		= -1;
			File	highest_p_file 	= null;

			for (int i=0;i<files.length;i++){

				String	name = files[i].getName();

				if ( name.startsWith( patch_prefix ) && name.endsWith( ".pat" )){

					if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID, "Core Patcher: found patch file '"
								+ name + "'"));

					try{
						int	this_p = Integer.parseInt( name.substring( patch_prefix.length(), name.indexOf( ".pat" )));

						if ( this_p > highest_p ){

							highest_p = this_p;

							highest_p_file	= files[i];
						}
					}catch( Throwable e ){

						Debug.printStackTrace( e );
					}
				}
			}

			if ( CorePatchLevel.getCurrentPatchLevel() >= highest_p ){

				if (Logger.isEnabled())
					Logger.log(new LogEvent(LOGID,
							"Core Patcher: no applicable patch found (highest = " + highest_p
									+ ")"));

					// flip back from maybe as we now know it isn't needed

				if ( updater_update.getRestartRequired() == Update.RESTART_REQUIRED_MAYBE ){

					updater_update.setRestartRequired( Update.RESTART_REQUIRED_NO );
				}
			}else{

				rd_log.reportActivity( "Applying patch '" + highest_p_file.getName() + "'");

				if (Logger.isEnabled())
					Logger.log(new LogEvent(LOGID, "Core Patcher: applying patch '"
							+ highest_p_file.toString() + "'"));

				InputStream pis = new FileInputStream( highest_p_file );

				try{
					patchBiglyBT( instance, pis, "P" + highest_p, plugin_interface.getLogger().getChannel( "CorePatcher" ));

					Logger.log(new LogAlert(LogAlert.UNREPEATABLE, LogAlert.AT_INFORMATION,
							"Patch " + highest_p_file.getName() + " ready to be applied"));

					String done_file = highest_p_file.toString();

					done_file = done_file.substring(0,done_file.length()-1) + "x";

					highest_p_file.renameTo( new File( done_file ));

						// flip the original update over to 'restart required'

					updater_update.setRestartRequired( Update.RESTART_REQUIRED_YES );
				}finally{
					try{
						pis.close();
					}catch( Throwable e ){
					}
				}
			}
		}catch( Throwable e ){

			Debug.printStackTrace( e );
			Logger.log(new LogAlert(LogAlert.UNREPEATABLE, "Core Patcher failed", e));
		}
	}

	public static void
	patchBiglyBT(
		UpdateCheckInstance	instance,
		InputStream			pis,
		String				resource_tag,
		LoggerChannel		log )

		throws Exception
	{
		OutputStream	os 	= null;
		InputStream		is	= null;

		try{
			String resource_name = "BiglyBT_" + resource_tag + ".jar";

			UpdateInstaller	installer = instance.createInstaller();

			File	tmp = AETemporaryFileHandler.createTempFile();

			os = new FileOutputStream( tmp );

			String	jarPath = installer.getInstallDir();

			File jarFile = new File(jarPath, "BiglyBT.jar");

			is 	= new FileInputStream( jarFile );

			new UpdateJarPatcher( is, pis, os, log );

			is.close();

			is = null;

			pis.close();

			pis = null;

			os.close();

			os = null;

			installer.addResource(  resource_name,
									new FileInputStream( tmp ));

			tmp.delete();

			installer.addMoveAction( resource_name, jarFile.getAbsolutePath() );

		}finally{

			if ( is != null ){
				try{
					is.close();
				}catch( Throwable e ){
				}
			}
			if ( os != null ){
				try{
					os.close();
				}catch( Throwable e ){
				}
			}
			if ( pis != null ){
				try{
					pis.close();
				}catch( Throwable e ){
				}
			}
		}
	}
}
