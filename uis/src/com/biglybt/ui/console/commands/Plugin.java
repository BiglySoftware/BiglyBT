/*
 * Created on 22 Aug 2008
 * Created by Allan Crooks
 * Copyright (C) 2008 Vuze Inc., All Rights Reserved.
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
package com.biglybt.ui.console.commands;

import java.io.File;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import com.biglybt.core.util.AESemaphore;
import com.biglybt.core.util.AEThread2;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.FileUtil;
import com.biglybt.pif.PluginException;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.PluginManager;
import com.biglybt.pif.installer.InstallablePlugin;
import com.biglybt.pif.installer.PluginInstallationListener;
import com.biglybt.pif.installer.PluginInstaller;
import com.biglybt.pif.installer.StandardPlugin;
import com.biglybt.pif.update.Update;
import com.biglybt.pif.update.UpdateCheckInstance;
import com.biglybt.pif.update.UpdateCheckInstanceListener;
import com.biglybt.pif.update.UpdateManager;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloader;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderAdapter;
import com.biglybt.pifimpl.update.sf.SFPluginDetails;
import com.biglybt.pifimpl.update.sf.SFPluginDetailsLoaderFactory;
import com.biglybt.ui.console.ConsoleInput;
import com.biglybt.ui.console.util.TextWrap;

public class Plugin extends IConsoleCommand {

	public Plugin()
	{
		super("plugin");
	}

	@Override
	public String getCommandDescriptions()
	{
		return("plugin [various options]\t\tRun with no parameter for more help.");
	}

	@Override
	public void printHelpExtra(PrintStream out, List args) {
		out.println("> -----");
		out.println("Subcommands:");
		out.println("install\t[pluginid]\tLists plugins available to install or installs a given plugin");
		out.println("location\t\tLists where plugins are being loaded from");
		out.println("list\t\t\tList all running plugins");
		out.println("listall\t\t\tList all plugins - running or not");
		out.println("status pluginid\t\tPrints the status of a given plugin");
		out.println("startup pluginid on|off\tEnables or disables the plugin running at startup");
		out.println("uninstall pluginid\t\tUninstalls a plugin");
		out.println("update\t\tUpdates all plugins with outstanding updates");
		out.println("> -----");
	}

	@Override
	public void execute(String commandName, final ConsoleInput ci, List args) {
		if (args.isEmpty()) {
			printHelpExtra(ci.out, args);
			return;
		}

		String subcmd = (String)args.get(0);
		if (!java.util.Arrays.asList(new String[] {
				"location", "list", "listall", "status", "startup", "install", "uninstall", "update"
			}).contains(subcmd)) {
			ci.out.println("Invalid subcommand: " + subcmd);
			ci.out.println();
			return;
		}

		PluginManager plugin_manager = ci.getCore().getPluginManager();

		if (subcmd.equals("list") || subcmd.equals("listall")) {
			boolean all_plugins = subcmd.equals("listall");
			ci.out.println("> -----");
			PluginInterface[] plugins = plugin_manager.getPluginInterfaces();
			TreeSet plugin_ids = new TreeSet(String.CASE_INSENSITIVE_ORDER);
			for (int i=0; i<plugins.length; i++) {
				if (!all_plugins && !plugins[i].getPluginState().isOperational()) {continue;}
				String plugin_id = plugins[i].getPluginID();
				plugin_ids.add(plugin_id);
			}
			TextWrap.printList(plugin_ids.iterator(), ci.out, "   ");
			ci.out.println("> -----");
			return;
		}

		if (subcmd.equals("location")) {
			// Taken from ConfigSectionPlugins.
			File fUserPluginDir = FileUtil.getUserFile("plugins");
			String sep = File.separator;

			String sUserPluginDir;

			try{
				sUserPluginDir = fUserPluginDir.getCanonicalPath();
			}catch( Throwable e ){
				sUserPluginDir = fUserPluginDir.toString();
			}

			if (!sUserPluginDir.endsWith(sep)) {
				sUserPluginDir += sep;
			}

			File fAppPluginDir = FileUtil.getApplicationFile("plugins");

			String sAppPluginDir;

			try{
				sAppPluginDir = fAppPluginDir.getCanonicalPath();
			}catch( Throwable e ){
				sAppPluginDir = fAppPluginDir.toString();
			}

			if (!sAppPluginDir.endsWith(sep)) {
				sAppPluginDir += sep;
			}

			ci.out.println("Shared plugin location:");
			ci.out.println("  " + sAppPluginDir);
			ci.out.println("User plugin location:");
			ci.out.println("  " + sUserPluginDir);
			ci.out.println();
			return;
		}

		if ( subcmd.equals( "update" )){

			if ( args.size() != 1 ){

				ci.out.println( "Usage: update" );

				return;
			}

			UpdateManager update_manager = plugin_manager.getDefaultPluginInterface().getUpdateManager();

			final UpdateCheckInstance	checker = update_manager.createUpdateCheckInstance();

			checker.addListener(
				new UpdateCheckInstanceListener()
				{
					@Override
					public void
					cancelled(
						UpdateCheckInstance		instance )
					{

					}

					@Override
					public void
					complete(
						UpdateCheckInstance		instance )
					{
						Update[] 	updates = instance.getUpdates();

						try{

							for ( Update update: updates ){

								ci.out.println( "Updating " + update.getName());

								for ( ResourceDownloader rd: update.getDownloaders()){

									rd.addListener(
		 								new ResourceDownloaderAdapter()
		 								{
		 									@Override
										  public void
		 									reportActivity(
		 										ResourceDownloader	downloader,
		 										String				activity )
		 									{
		 										ci.out.println( "\t" + activity );
		 									}

		 									@Override
										  public void
		 									reportPercentComplete(
		 										ResourceDownloader	downloader,
		 										int					percentage )
		 									{
		 										ci.out.println( "\t" + percentage + "%" );

		 									}
		 								});

									rd.download();
								}
							}

							boolean	restart_required = false;

							for (int i=0;i<updates.length;i++){

								if ( updates[i].getRestartRequired() == Update.RESTART_REQUIRED_YES ){

									restart_required = true;
								}
							}

							if ( restart_required ){

								ci.out.println( "**** Restart required to complete update ****" );
							}
						}catch( Throwable e ){

							ci.out.println( "Plugin update failed: " + Debug.getNestedExceptionMessage( e ));
						}
					}
				});

			checker.start();

			return;
		}

		if ( subcmd.equals( "install" )){

			if ( args.size() == 1 ){

				ci.out.println( "Contacting plugin repository for list of available plugins..." );

				try{
					SFPluginDetails[] plugins = SFPluginDetailsLoaderFactory.getSingleton().getPluginDetails();

					for ( SFPluginDetails p: plugins ){

						String category = p.getCategory();

						if ( category != null ){

							if ( category.equalsIgnoreCase( "hidden" ) || ( category.equalsIgnoreCase( "core" ))){

								continue;
							}
						}

						String id = p.getId();

						if (  plugin_manager.getPluginInterfaceByID( id, false ) == null ){

							String desc = p.getDescription();

							int pos = desc.indexOf( "<br" );

							if ( pos > 0 ){

								desc = desc.substring( 0, pos );
							}

							ci.out.println( "\t" + id + ": \t\t" + desc );
						}
					}

				}catch( Throwable e ){

					ci.out.println( "Failed to list plugins: " + Debug.getNestedExceptionMessage( e ));
				}
			}else{

				String target_id = (String)args.get(1);

				if (  plugin_manager.getPluginInterfaceByID( target_id, false ) != null ){

					ci.out.println( "Plugin '" + target_id + "' already installed" );

					return;
				}

				final PluginInstaller installer = plugin_manager.getPluginInstaller();

				try{
			 		final StandardPlugin sp = installer.getStandardPlugin( target_id );

			 		if ( sp == null ){

			 			ci.out.println( "Plugin '" + target_id + "' is unknown" );

						return;
			 		}

			 		new AEThread2( "Plugin Installer" )
			 		{
			 			@Override
					  public void
			 			run()
			 			{
			 				try{
								Map<Integer, Object> properties = new HashMap<>();

								properties.put( UpdateCheckInstance.PT_UI_STYLE, UpdateCheckInstance.PT_UI_STYLE_NONE );

								properties.put(UpdateCheckInstance.PT_UI_DISABLE_ON_SUCCESS_SLIDEY, true);

								final AESemaphore sem = new AESemaphore( "plugin-install" );

								final boolean[] restart_required = { false };

								UpdateCheckInstance instance =
									installer.install(
										new InstallablePlugin[]{ sp },
										false,
										properties,
										new PluginInstallationListener() {

											@Override
											public void
											completed()
											{
												ci.out.println( "Installation complete" );

												sem.release();
											}

											@Override
											public void
											cancelled()
											{
												ci.out.println( "Installation cancelled" );

												sem.release();
											}

											@Override
											public void
											failed(
												PluginException	e )
											{
												ci.out.println( "Installation failed: " + Debug.getNestedExceptionMessage( e ));

												sem.release();
											}
										});

								instance.addListener(
									new UpdateCheckInstanceListener() {

										@Override
										public void
										cancelled(
											UpdateCheckInstance		instance )
										{
											ci.out.println( "Installation cancelled" );
										}

										@Override
										public void
										complete(
											UpdateCheckInstance		instance )
										{
						  					Update[] updates = instance.getUpdates();

						 					for ( final Update update: updates ){

						 						ResourceDownloader[] rds = update.getDownloaders();

						 						for ( ResourceDownloader rd: rds ){

						 							rd.addListener(
						 								new ResourceDownloaderAdapter()
						 								{
						 									@Override
														  public void
						 									reportActivity(
						 										ResourceDownloader	downloader,
						 										String				activity )
						 									{
						 										ci.out.println( "\t" + activity );
						 									}

						 									@Override
														  public void
						 									reportPercentComplete(
						 										ResourceDownloader	downloader,
						 										int					percentage )
						 									{
						 										ci.out.println( "\t" + percentage + "%" );

						 									}
						 								});

						 								// go ahead and actually initiate the install, someone has to do it!

						 							try{
						 								rd.download();

						 							}catch( Throwable e ){
						 							}
						 						}

						 						if ( update.getRestartRequired() != Update.RESTART_REQUIRED_NO ){

						 							restart_required[0] = true;
						 						}
					 						}
										}
									});

								sem.reserve();

								if ( restart_required[0] ){

									ci.out.println( "**** Restart required to complete installation ****" );
								}
			 				}catch( Throwable e ){

								ci.out.println( "Install failed: " + Debug.getNestedExceptionMessage( e ));
							}
			 			}
			 		}.start();

				}catch( Throwable e ){

					ci.out.println( "Install failed: " + Debug.getNestedExceptionMessage( e ));
				}
			}
			return;
		}

		// Commands from this point require a plugin ID.
		if (args.size() == 1) {
			ci.out.println("No plugin ID given.");
			ci.out.println();
			return;
		}

		String plugin_id = (String)args.get(1);
		PluginInterface plugin = plugin_manager.getPluginInterfaceByID(plugin_id, false);
		if (plugin == null) {
			ci.out.println("Invalid plugin ID: " + plugin_id);
			ci.out.println();
			return;
		}

		if (subcmd.equals("status")) {
			ci.out.println("ID     : " + plugin.getPluginID());
			ci.out.println("Name   : " + plugin.getPluginName());
			ci.out.println("Version: " + plugin.getPluginVersion());
			ci.out.println("Running: " + plugin.getPluginState().isOperational());
			ci.out.println("Runs at startup: " + plugin.getPluginState().isLoadedAtStartup());
			if (!plugin.getPluginState().isBuiltIn()) {
				ci.out.println("Location: " + plugin.getPluginDirectoryName());
			}
			ci.out.println();
			return;
		}

		if (subcmd.equals("startup")) {
			if (args.size() == 2) {
				ci.out.println("Need to pass either \"on\" or \"off\"");
				ci.out.println();
				return;
			}
			String enabled_mode = (String)args.get(2);
			if (enabled_mode.equals("on")) {
				plugin.getPluginState().setLoadedAtStartup(true);
			}
			else if (enabled_mode.equals("off")) {
				plugin.getPluginState().setLoadedAtStartup(false);
			}
			else {
				ci.out.println("Need to pass either \"on\" or \"off\"");
				ci.out.println();
				return;
			}
			ci.out.println("Done.");
			ci.out.println();
			return;
		}

		if ( subcmd.equals( "uninstall" )){

			PluginInterface pi = plugin_manager.getPluginInterfaceByID( plugin_id, false );

			if (  pi == null ){

				ci.out.println( "Plugin '" + plugin_id + "' is not installed" );

				return;
			}

			final PluginInstaller installer = plugin_manager.getPluginInstaller();

			try{
		 		final StandardPlugin sp = installer.getStandardPlugin( plugin_id );

		 		if ( sp == null ){

		 			ci.out.println( "Plugin '" + plugin_id + "' is not a standard plugin" );

					return;
		 		}

				final PluginInstaller uninstaller = plugin_manager.getPluginInstaller();

				Map<Integer, Object> properties = new HashMap<>();

				final AESemaphore sem = new AESemaphore( "plugin-uninstall" );

				UpdateCheckInstance instance =
					uninstaller.uninstall(
						new PluginInterface[]{ pi },
						new PluginInstallationListener() {

							@Override
							public void
							completed()
							{
								ci.out.println( "Uninstallation complete" );

								sem.release();
							}

							@Override
							public void
							cancelled()
							{
								ci.out.println( "Uninstallation cancelled" );

								sem.release();
							}

							@Override
							public void
							failed(
								PluginException	e )
							{
								ci.out.println( "Uninstallation failed: " + Debug.getNestedExceptionMessage( e ));

								sem.release();
							}
						},
						properties );

				instance.addListener(
						new UpdateCheckInstanceListener() {

							@Override
							public void
							cancelled(
								UpdateCheckInstance		instance )
							{
								ci.out.println( "InsUninstallationtallation cancelled" );
							}

							@Override
							public void
							complete(
								UpdateCheckInstance		instance )
							{
			  					Update[] updates = instance.getUpdates();

			 					for ( final Update update: updates ){

			 						ResourceDownloader[] rds = update.getDownloaders();

			 						for ( ResourceDownloader rd: rds ){

			 							try{
			 								rd.download();

			 							}catch( Throwable e ){
			 							}
			 						}
		 						}
							}
						});

				sem.reserve();

				Object obj = properties.get( UpdateCheckInstance.PT_UNINSTALL_RESTART_REQUIRED );

				if ( obj instanceof Boolean && (Boolean)obj ){

					ci.out.println( "**** Restart required to complete uninstallation ****" );
				}
			}catch( Throwable  e ){

				ci.out.println( "Uninstall failed: " + Debug.getNestedExceptionMessage( e ));

			}
		}
	}
}
