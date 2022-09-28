/*
 * Created on 28-Apr-2004
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

package com.biglybt.pifimpl.update;

/**
 * @author parg
 *
 */

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.biglybt.core.html.HTMLUtils;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.*;
import com.biglybt.core.versioncheck.VersionCheckClient;
import com.biglybt.pif.*;
import com.biglybt.pif.installer.InstallablePlugin;
import com.biglybt.pif.installer.PluginInstallationListener;
import com.biglybt.pif.installer.PluginInstaller;
import com.biglybt.pif.installer.StandardPlugin;
import com.biglybt.pif.logging.LoggerChannel;
import com.biglybt.pif.logging.LoggerChannelListener;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.model.BasicPluginConfigModel;
import com.biglybt.pif.ui.model.BasicPluginViewModel;
import com.biglybt.pif.update.*;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloader;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderAdapter;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderException;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderFactory;
import com.biglybt.pifimpl.PluginUtils;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.pifimpl.update.sf.SFPluginDetails;
import com.biglybt.pifimpl.update.sf.SFPluginDetailsLoader;
import com.biglybt.pifimpl.update.sf.SFPluginDetailsLoaderFactory;
import com.biglybt.pifimpl.update.sf.SFPluginDetailsLoaderListener;
import com.biglybt.plugin.I2PHelpers;
import com.biglybt.update.CorePatchChecker;

public class
PluginUpdatePlugin
	implements Plugin
{
	private static final String PLUGIN_CONFIGSECTION_ID = "plugins.update";
	private static final String PLUGIN_RESOURCE_ID		= "ConfigView.section.plugins.update";

	public static final int	RD_SIZE_RETRIES	= 3;
	public static final int	RD_SIZE_TIMEOUT	= 10000;

	private PluginInterface		plugin_interface;
	private LoggerChannel 		log;

	private boolean	loader_listener_added;

	private String	last_id_info	= "";

	@Override
	public void
	initialize(
		PluginInterface	_plugin_interface )
	{
		plugin_interface	= _plugin_interface;

		plugin_interface.getPluginProperties().setProperty( "plugin.version", 	"1.0" );
		plugin_interface.getPluginProperties().setProperty( "plugin.name", 		"Plugin Updater" );

		log = plugin_interface.getLogger().getChannel("Plugin Update");

		log.setDiagnostic();

		log.setForce( true );

		UIManager	ui_manager = plugin_interface.getUIManager();

		final BasicPluginViewModel model =
			ui_manager.createBasicPluginViewModel( PLUGIN_RESOURCE_ID );

		final PluginConfig	plugin_config = plugin_interface.getPluginconfig();

		boolean enabled = plugin_config.getPluginBooleanParameter( "enable.update", true );

		model.setConfigSectionID(PLUGIN_CONFIGSECTION_ID);
		model.getStatus().setText( enabled?"Running":"Optional checks disabled" );
		model.getActivity().setVisible( false );
		model.getProgress().setVisible( false );

		log.addListener(
			new LoggerChannelListener()
			{
				@Override
				public void
				messageLogged(
					int		type,
					String	message )
				{
					model.getLogArea().appendText( message+"\n");
				}

				@Override
				public void
				messageLogged(
					String		str,
					Throwable	error )
				{
					model.getLogArea().appendText( error.toString()+"\n");
				}
			});

		BasicPluginConfigModel config = ui_manager.createBasicPluginConfigModel(ConfigSection.SECTION_PLUGINS, PLUGIN_CONFIGSECTION_ID);

		config.addBooleanParameter2( "enable.update", "Plugin.pluginupdate.enablecheck", true );

		plugin_interface.addEventListener(
			new PluginEventListener()
			{
				@Override
				public void
				handleEvent(
					PluginEvent 	ev )
				{
					if ( ev.getType() == PluginEvent.PEV_ALL_PLUGINS_INITIALISED ){

						plugin_interface.removeEventListener( this );

						initComplete( plugin_config );
					}
				}
			});
	}

	protected void
	initComplete(
		final PluginConfig		plugin_config )
	{
		UpdateManager	update_manager = plugin_interface.getUpdateManager();

		update_manager.addListener(
			new UpdateManagerListener()
			{
				@Override
				public void
				checkInstanceCreated(
					UpdateCheckInstance	inst )
				{
					SFPluginDetailsLoaderFactory.getSingleton().reset();
				}

			});

		final PluginManager	plugin_manager = plugin_interface.getPluginManager();

		PluginInterface[]	plugins = plugin_manager.getPlugins();

		int mandatory_count 	= 0;
		int non_mandatory_count	= 0;

		for (int i=0;i<plugins.length;i++){

			PluginInterface	pi = plugins[i];

			boolean	pi_mandatory = pi.getPluginState().isMandatory();

			if ( pi_mandatory ){

				mandatory_count++;

			}else{

				non_mandatory_count++;
			}
		}

		final int f_non_mandatory_count	= non_mandatory_count;
		final int f_mandatory_count		= mandatory_count;

		update_manager.registerUpdatableComponent(
			new UpdatableComponent()
			{
				@Override
				public String
				getName()
				{
					return( "Non-mandatory plugins" );
				}

				@Override
				public int
				getMaximumCheckTime()
				{
					return( f_non_mandatory_count * (( RD_SIZE_RETRIES * RD_SIZE_TIMEOUT )/1000));
				}

				@Override
				public void
				checkForUpdate(
					UpdateChecker	checker )
				{
					if ( checkForUpdateSupport( checker, null, false ) == 0 ){

						VersionCheckClient vc = VersionCheckClient.getSingleton();

						String[] rps = vc.getRecommendedPlugins();

						boolean	found_one = false;

						for (int i=0;i<rps.length;i++){

							String	rp_id = rps[i];

							if ( plugin_manager.getPluginInterfaceByID( rp_id, false ) != null ){

									// already installed

								continue;
							}

							final String	config_key = "recommended.processed." + rp_id;

							if ( !plugin_config.getPluginBooleanParameter( config_key, false )){

								try{
									final PluginInstaller installer = plugin_interface.getPluginManager().getPluginInstaller();

									StandardPlugin[]	sps = installer.getStandardPlugins();

									for (int j=0;j<sps.length;j++){

										final StandardPlugin	sp = sps[j];

										if ( sp.getId().equals( rp_id )){

											found_one = true;

											checker.getCheckInstance().addListener(
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
														if ( instance.getUpdates().length == 0 ){

															installRecommendedPlugin( installer, sp );

															plugin_config.setPluginParameter( config_key, true );
														}
													}
												});

											break;
										}
									}

								}catch( Throwable e ){

								}
							}

							if ( found_one ){

								break;
							}
						}

						if ( !found_one ){

							Set<String>	auto_install = vc.getAutoInstallPluginIDs();

							final List<String>	to_do = new ArrayList<>();

							for ( String pid: auto_install ){

								if ( plugin_manager.getPluginInterfaceByID( pid, false ) == null ){

									to_do.add( pid );
								}
							}

							if ( to_do.size() > 0 ){

								new AEThread2( "pup:autoinst" )
								{
									@Override
									public void
									run()
									{
										try{
											Thread.sleep( 120*1000 );

										}catch( Throwable e ){

											Debug.out( e );

											return;
										}

										UpdateManager update_manager = plugin_interface.getUpdateManager();

										final List<UpdateCheckInstance>	l_instances = new ArrayList<>();

										update_manager.addListener(
											new UpdateManagerListener()
											{
												@Override
												public void
												checkInstanceCreated(
													UpdateCheckInstance	instance )
												{
													synchronized( l_instances ){

														l_instances.add( instance );
													}
												}
											});

										UpdateCheckInstance[] instances = update_manager.getCheckInstances();

										l_instances.addAll( Arrays.asList( instances ));

										long start = SystemTime.getMonotonousTime();

										while( true ){

											if ( SystemTime.getMonotonousTime() - start >= 5*60*1000 ){

												break;
											}

											try{
												Thread.sleep(5000);

											}catch( Throwable e ){

												Debug.out( e );

												return;
											}

											if ( l_instances.size() > 0 ){

												boolean	all_done = true;

												for ( UpdateCheckInstance instance: l_instances ){

													if ( !instance.isCompleteOrCancelled()){

														all_done = false;

														break;
													}
												}

												if ( all_done ){

													break;
												}
											}
										}

										if ( update_manager.getInstallers().length > 0 ){

											return;
										}

										PluginInstaller installer = plugin_interface.getPluginManager().getPluginInstaller();

										List<InstallablePlugin>	sps = new ArrayList<>();

										for ( String pid: to_do ){

											try{
												StandardPlugin sp = installer.getStandardPlugin( pid );

												if ( sp != null ){

													log.log( "Auto-installing " + pid );

													sps.add( sp );

												}else{

													log.log( "Standard plugin '" + pid + "' missing" );
												}
											}catch( Throwable e ){

												log.log( "Standard plugin '" + pid + "' missing", e );
											}
										}

										if ( sps.size() > 0 ){

											Map<Integer, Object> properties = new HashMap<>();

											properties.put( UpdateCheckInstance.PT_UI_STYLE, UpdateCheckInstance.PT_UI_STYLE_NONE );

											properties.put(UpdateCheckInstance.PT_UI_DISABLE_ON_SUCCESS_SLIDEY, true);

											try{
												installer.install(
													sps.toArray( new InstallablePlugin[ sps.size()]),
													false,
													properties,
													new PluginInstallationListener() {

														@Override
														public void
														completed()
														{
														}

														@Override
														public void
														cancelled()
														{
														}

														@Override
														public void
														failed(
															PluginException e )
														{

														}
													});

											}catch( Throwable e ){

												log.log( "Auto install failed", e );
											}
										}
									}
								}.start();
							}
						}
					}
				}

			}, false );

		update_manager.registerUpdatableComponent(
				new UpdatableComponent()
				{
					@Override
					public String
					getName()
					{
						return( "Mandatory plugins" );
					}

					@Override
					public int
					getMaximumCheckTime()
					{
						return( f_mandatory_count * (( RD_SIZE_RETRIES * RD_SIZE_TIMEOUT )/1000));
					}

					@Override
					public void
					checkForUpdate(
						UpdateChecker	checker )
					{
						checkForUpdateSupport( checker, null, true );
					}
				}, true );

		update_manager.addListener(
			new UpdateManagerListener()
			{
				@Override
				public void
				checkInstanceCreated(
					UpdateCheckInstance	instance )
				{
					log.log( LoggerChannel.LT_INFORMATION, "**** Update check starts ****" );
				}
			});
	}

	protected void
	installRecommendedPlugin(
		PluginInstaller	installer,
		StandardPlugin	plugin )
	{
		try{
			installer.requestInstall( MessageText.getString("plugin.installer.recommended.plugin"), plugin );

		}catch( Throwable e ){

			log.log(e);
		}
	}

	public UpdatableComponent
	getCustomUpdateableComponent(
		final String		id,
		final boolean		mandatory )
	{
		return(
			new UpdatableComponent()
			{
				@Override
				public String
				getName()
				{
					return( "Installation of '" + id + "'" );
				}

				@Override
				public int
				getMaximumCheckTime()
				{
					return( ( RD_SIZE_RETRIES * RD_SIZE_TIMEOUT )/1000 );
				}

				@Override
				public void
				checkForUpdate(
					UpdateChecker	checker )
				{
					checkForUpdateSupport( checker, new String[]{ id }, mandatory );
				}
			});
	}

	protected  int
	checkForUpdateSupport(
		UpdateChecker	checker,
		String[]		ids_to_check,	// explicit ids or null for all
		boolean			mandatory )
	{
		int	num_updates_found = 0;

		try{
			if ( 	(!mandatory) &&
					(ids_to_check == null ) && 	// allow custom actions through
					(!plugin_interface.getPluginconfig().getPluginBooleanParameter( "enable.update", true ))){

				return( num_updates_found );
			}

			PluginInterface[]	plugins = plugin_interface.getPluginManager().getPlugins();

			List	plugins_to_check 			= new ArrayList();
			List	plugins_to_check_ids		= new ArrayList();
			Map		plugins_to_check_names		= new HashMap();

			for (int i=0;i<plugins.length;i++){

				PluginInterface	pi = plugins[i];

				String	id 		= pi.getPluginID();
				String	version = pi.getPluginVersion();
				String	name	= pi.getPluginName();

				PluginState ps = pi.getPluginState();
								
				if ( ps.isDisabled()){

						// if it is disabled because it failed to load, carry on and check for updates as the newer version
						// may be there to fix the load failure!

					if ( !ps.hasFailed()){

						continue;
					}
				}

				String	mand = pi.getPluginProperties().getProperty( "plugin.mandatory");

				boolean	pi_mandatory = mand != null && mand.trim().toLowerCase().equals("true");

				if ( pi_mandatory != mandatory ){

					continue;
				}

				if ( ids_to_check != null ){

					boolean	id_selected = false;

					for (int j=0;j<ids_to_check.length;j++){

						if ( ids_to_check[j].equals( id )){

							id_selected = true;

							break;
						}
					}

					if ( !id_selected ){

						continue;
					}
				}

				if ( ps.isRestartPending()){
					
					log.log( LoggerChannel.LT_INFORMATION, (mandatory?"*":"-") + pi.getPluginName() + ", id = " + id + (version==null?"":(", version = " + pi.getPluginVersion())) + ": Restart pending, skipping update check" );
					
					continue;
				}

				if ( version != null ){

					if ( plugins_to_check_ids.contains( id )){

						String	s = (String)plugins_to_check_names.get(id);

						if ( !name.equals( id )){

							plugins_to_check_names.put( id, s+","+name);
						}
					}else{
						plugins_to_check_ids.add( id );

						plugins_to_check.add( pi );

						plugins_to_check_names.put( id, name.equals(id)?"":name);
					}
				}

				String location = pi.getPluginDirectoryName();

				log.log( LoggerChannel.LT_INFORMATION, (mandatory?"*":"-") + pi.getPluginName() + ", id = " + id + (version==null?"":(", version = " + pi.getPluginVersion())) + (location==null?"":( ", loc = " + location)));
			}

			SFPluginDetailsLoader loader = SFPluginDetailsLoaderFactory.getSingleton();

			if ( !loader_listener_added ){

				loader_listener_added = true;

				loader.addListener(
					new SFPluginDetailsLoaderListener()
					{
						@Override
						public void
						log(
							String	str )
						{
							log.log( LoggerChannel.LT_INFORMATION, "[" + str + "]" );

						}
					});
			}

			String[]	ids = loader.getPluginIDs();

			String	id_info = "";

			for (int i=0;i<ids.length;i++){

				String	id = ids[i];

				SFPluginDetails	details = loader.getPluginDetails( id );

				id_info += (i==0?"":",") + ids[i] + "=" + details.getVersion()+"/"+details.getCVSVersion();
			}

			if ( !id_info.equals( last_id_info )){

				last_id_info	= id_info;

				log.log( LoggerChannel.LT_INFORMATION, "Downloaded plugin info = " + id_info );
			}

			for ( int i=0;i<plugins_to_check.size();i++){

				if ( checker.getCheckInstance().isCancelled()){

					throw( new Exception( "Update check cancelled" )); // XXX: Uh?
				}

				final PluginInterface	pi_being_checked 	= (PluginInterface)plugins_to_check.get(i);
				final String			plugin_id 			= pi_being_checked.getPluginID();

				boolean	found	= false;

				for (int j=0;j<ids.length;j++){

					if ( ids[j].equalsIgnoreCase( plugin_id )){

						found	= true;

						break;
					}
				}

				if ( !found ){

					if ( !pi_being_checked.getPluginState().isBuiltIn()){

						log.log( LoggerChannel.LT_INFORMATION, "Skipping " + plugin_id + " as not listed on web site");
					}

					continue;
				}

				String			plugin_names		= (String)plugins_to_check_names.get( plugin_id );
				//final boolean	plugin_unloadable 	= ((Boolean)plugins_to_check_unloadable.get( plugin_id )).booleanValue();

				log.log( LoggerChannel.LT_INFORMATION, "Checking " + plugin_id);

				try{
					checker.reportProgress( "Loading details for " + plugin_id + "/" + pi_being_checked.getPluginName());

					SFPluginDetails	details = loader.getPluginDetails( plugin_id );

					if ( plugin_names.length() == 0 ){

						plugin_names = details.getName();
					}

					boolean az_cvs = plugin_interface.getUtilities().isCVSVersion();

					String pi_version_info = pi_being_checked.getPluginProperties().getProperty( "plugin.version.info" );

					String az_plugin_version	= pi_being_checked.getPluginVersion();

					String sf_plugin_version	= details.getVersion();

					String sf_comp_version		= sf_plugin_version;

					if ( az_cvs ){

						String	sf_cvs_version = details.getCVSVersion();

						if ( sf_cvs_version.length() > 0 ){

								// sf cvs version ALWAYS ends in _CVS

							sf_plugin_version	= sf_cvs_version;

							sf_comp_version = sf_plugin_version.substring(0,sf_plugin_version.length()-4);
						}
					}

					if (	 sf_comp_version.length() == 0 ||
							!Character.isDigit(sf_comp_version.charAt(0))){

						log.log( LoggerChannel.LT_INFORMATION, "Skipping " + plugin_id + " as no valid version to check");

						continue;
					}

					// 	System.out.println("comp version = " + sf_comp_version );

					int	comp = PluginUtils.comparePluginVersions( az_plugin_version, sf_comp_version );

						// if they're the same version and latest is CVS then stick a _CVS on
						// the end of current to avoid confusion

					log.log( LoggerChannel.LT_INFORMATION,
								"Plugin: " + plugin_id + " versions (current=" + az_plugin_version +
								(comp==0&&sf_plugin_version.endsWith( "_CVS")?"_CVS":"")+
								", latest=" + sf_plugin_version + (pi_version_info==null?"":" [" + pi_version_info + "]") + ")");

					checker.reportProgress( "Plugin: " + plugin_id + "versions (current=" + az_plugin_version +
								(comp==0&&sf_plugin_version.endsWith( "_CVS")?"_CVS":"") + ", latest=" + sf_plugin_version + ")");

					if ( comp < 0 && ! ( pi_being_checked.getPlugin() instanceof UpdatableComponent)){

							// only update if newer version + plugin itself doesn't handle
							// the update

						String sf_plugin_download	= details.getDownloadURL();

						if ( az_cvs ){

							String	sf_cvs_version = details.getCVSVersion();

							if ( sf_cvs_version.length() > 0 ){

								sf_plugin_download	= details.getCVSDownloadURL();
							}
						}

						log.log( LoggerChannel.LT_INFORMATION, "    Description:" );

						List	update_desc = new ArrayList();

						List	desc_lines = HTMLUtils.convertHTMLToText( "", details.getDescription());

						logMultiLine( "        ", desc_lines );

						update_desc.addAll( desc_lines );

						log.log( LoggerChannel.LT_INFORMATION, "    Comment:" );

						List	comment_lines = HTMLUtils.convertHTMLToText( "    ", details.getComment());

						logMultiLine( "    ", comment_lines );

						update_desc.addAll( comment_lines );

						String msg =   "A newer version (version " + sf_plugin_version + ") of plugin '" +
										plugin_id + "' " +
										(plugin_names.length()==0?"":"(" + plugin_names + ") " ) +
										"is available. ";

						log.log( LoggerChannel.LT_INFORMATION, "" );

						log.log( 	LoggerChannel.LT_INFORMATION, "        " + msg + "Download from "+
									sf_plugin_download);

						ResourceDownloaderFactory rdf =  plugin_interface.getUtilities().getResourceDownloaderFactory();

						ResourceDownloader direct_rdl 		= rdf.create( new URL( sf_plugin_download ));
						ResourceDownloader direct_ap_rdl 	= rdf.createWithAutoPluginProxy( new URL( sf_plugin_download ));

							// work out what the torrent download will be, if it exists
							// sf_plugin_download will be something like ../plugins/safepeer_2.4.zip
							//     torrent is safepeer_2.4.zip.torrent

						String	torrent_download = Constants.URL_PLUGINS_TORRENT_BASE;

						int	slash_pos = sf_plugin_download.lastIndexOf("/");

						if ( slash_pos == -1 ){

							torrent_download += sf_plugin_download;

						}else{

							torrent_download += sf_plugin_download.substring( slash_pos + 1 );
						}

						torrent_download	+= ".torrent";
						
						if ( I2PHelpers.isI2PInstalled()){
							
							torrent_download += "?i2p=1";
						}

						ResourceDownloader torrent_rdl 		= rdf.create( new URL( torrent_download ));
						//ResourceDownloader torrent_ap_rdl 	= rdf.createWithAutoPluginProxy( new URL( torrent_download ));

						torrent_rdl	= rdf.getSuffixBasedDownloader( torrent_rdl );

							// create an alternate downloader with torrent attempt first

						ResourceDownloader alternate_rdl = rdf.getAlternateDownloader( new ResourceDownloader[]{ torrent_rdl, direct_rdl, direct_ap_rdl });

							// get size so it is cached

						rdf.getTimeoutDownloader(rdf.getRetryDownloader(alternate_rdl,RD_SIZE_RETRIES),RD_SIZE_TIMEOUT).getSize();

						String[]	update_d = new String[update_desc.size()];

						update_desc.toArray( update_d );

						num_updates_found++;

							// see if unloadable

						boolean	plugin_unloadable 	= true;

						for (int j=0;j<plugins.length;j++){

							PluginInterface pi = plugins[j];

							if ( pi.getPluginID().equals( plugin_id )){

								plugin_unloadable &= pi.getPluginState().isUnloadable();
							}
						}

						if ( plugin_unloadable ){

							checker.reportProgress( "Plugin is unloadable" );
						}

						Update update =
							addUpdate(
								pi_being_checked,
								checker,
								plugin_id + "/" + plugin_names,
								update_d,
								az_plugin_version,
								sf_plugin_version,
								alternate_rdl,
								sf_plugin_download.toLowerCase().endsWith(".jar"),
								plugin_unloadable?Update.RESTART_REQUIRED_NO:Update.RESTART_REQUIRED_YES,
								true );

						update.setRelativeURLBase( details.getRelativeURLBase());
						update.setDescriptionURL(details.getInfoURL());
					}
				}catch( Throwable e ){

					checker.reportProgress( "Failed to load details for plugin '" + plugin_id + "': " + Debug.getNestedExceptionMessage(e));

					log.log("    Plugin check failed", e );
				}
			}

		}catch( Throwable e ){
			if (!"Update check cancelled".equals(e.getMessage())) {
				log.log("Failed to load plugin details", e );
			}

			checker.reportProgress( "Failed to load plugin details: " + Debug.getNestedExceptionMessage(e));

			checker.setFailed( new Exception( "Failed to load plugin details", e ));

		}finally{

				// any prior failure will take precedence

			checker.completed();
		}

		return( num_updates_found );
	}

	public Update
	addUpdate(
		final PluginInterface			pi_for_update,
		final UpdateChecker				checker,
		final String					update_name,
		final String[]					update_details,
		final String					old_version,
		final String					new_version,
		final ResourceDownloader		resource_downloader,
		final boolean					is_jar,
		final int						restart_type,
		final boolean					verify )
	{
		final Update update = checker.addUpdate(
				update_name,
				update_details,
				old_version,
				new_version,
				resource_downloader,
				restart_type );

		update.setUserObject( pi_for_update );

		resource_downloader.addListener(
			new ResourceDownloaderAdapter()
			{
				@Override
				public boolean
				completed(
					final ResourceDownloader	downloader,
					InputStream					data )
				{
						// during the update phase report any messages
						// to the downloader

					LoggerChannelListener	list =
						new LoggerChannelListener()
						{
						@Override
						public void
							messageLogged(
								int		type,
								String	content )
							{
								downloader.reportActivity( content );
							}

							@Override
							public void
							messageLogged(
								String		str,
								Throwable	error )
							{
								downloader.reportActivity( str );
							}
						};

					try{

						log.addListener(list);

						installUpdate(
								checker,
								update,
								pi_for_update,
								restart_type == Update.RESTART_REQUIRED_NO,
								is_jar,
								old_version,
								new_version,
								data,
								verify );

						return( true );
					}finally{

						log.removeListener( list );
					}
				}

				@Override
				public void
				failed(
					ResourceDownloader			downloader,
					ResourceDownloaderException e )
				{
					if ( !downloader.isCancelled()){

						Debug.out( downloader.getName() + " failed", e );
					}

					update.complete( false );
				}
			});

		return( update );
	}


	protected void
	installUpdate(
			UpdateChecker		checker,
			Update				update,
			PluginInterface plugin,    // note this will be first one if > 1 defined
			boolean				unloadable,
			boolean				is_jar,    // false -> zip
			String				old_version,
			String				new_version,
			InputStream			data,
			boolean				verify )
	{
		log.log( LoggerChannel.LT_INFORMATION,
				 "Installing plugin '" + update.getName() + "', version " + new_version );

		String	target_version = new_version.endsWith("_CVS")?new_version.substring(0,new_version.length()-4):new_version;

		UpdateInstaller	installer	= null;

		boolean	update_successful = false;

		try{

			data = update.verifyData( data, verify );

			log.log( "    Data verification stage complete" );

			boolean update_txt_found	= false;

			String plugin_dir_name = plugin.getPluginDirectoryName();

			if ( plugin_dir_name == null || plugin_dir_name.length() == 0 ){

					// update to a built-in plugin

				log.log( LoggerChannel.LT_INFORMATION, "    This is a built-in plugin, updating core" );

				CorePatchChecker.patchBiglyBT( update.getCheckInstance(), data, plugin.getPluginID() + "_" + new_version, log );

					// always need to restart for this

				update.setRestartRequired( Update.RESTART_REQUIRED_YES );

			}else{

				final File	plugin_dir	= new File( plugin_dir_name );
				final File	user_dir	= new File( plugin_interface.getUtilities().getUserDir());
				final File	prog_dir	= new File( plugin_interface.getUtilities().getProgramDir());

				Map<String,List<String[]>> install_properties = new HashMap<>();

					// .jar files get copied straight in with the right version number
					// .zip files need to be unzipped. There are various possibilities for
					// target dir depending on the contents of the zip file. Basically we
					// need to remove any zip paths to ensure it ends up in the right place
					// There's also the issue of overwriting stuff like "plugin.properties"
					// and any other config files....

				boolean	force_indirect_install = false;

					// for windows Vista we may not have write access to the plugin directory and have
					// to use an installer + restart to copy the files (as the restart can elevate
					// permissions)

				if ( Constants.isWindowsVistaOrHigher || Constants.isUnix ){

						// test with .dll as this will fail write to virtual-store as required

					File	test_file = new File( plugin_dir, "_aztest45.dll" );

					boolean	ok = false;

					try{
						if ( test_file.exists()){

							test_file.delete();
						}

						FileOutputStream os = new FileOutputStream(test_file);

						try{
							os.write(32);

						}finally{

							os.close();
						}

						ok = test_file.delete();
					}catch( Throwable e ){
					}

					if ( !ok ){

						log.log( "Can't write directly to the plugin directroy, installing indirectly" );

						force_indirect_install = true;
					}
				}

				File	target_plugin_dir;
				File	target_prog_dir;
				File	target_user_dir;

				if ( force_indirect_install ){

					File temp_dir = AETemporaryFileHandler.createTempDir();

					target_plugin_dir	= new File( temp_dir, "plugin" );
					target_user_dir		= new File( temp_dir, "user" );
					target_prog_dir		= new File( temp_dir, "prog" );

					target_plugin_dir.mkdirs();
					target_user_dir.mkdirs();
					target_prog_dir.mkdirs();

					installer = update.getCheckInstance().createInstaller();

					update.setRestartRequired( Update.RESTART_REQUIRED_YES );

				}else{

					target_plugin_dir	= plugin_dir;
					target_user_dir		= user_dir;
					target_prog_dir		= prog_dir;
				}

				File	target_jar_zip = new File( target_plugin_dir, plugin.getPluginID() + "_" + target_version + (is_jar?".jar":".zip"));


				FileUtil.copyFile( data, new FileOutputStream(target_jar_zip));

				if ( !is_jar ){

					ZipInputStream	zis =
						new ZipInputStream(
								new BufferedInputStream( new FileInputStream( target_jar_zip ) ));


						// first look for a common dir prefix and platform-specific stuff

					String	common_prefix = null;

					String	selected_platform		= null;
					List	selected_sub_platforms	= new ArrayList();

					try{
						while( true ){

							ZipEntry	entry = zis.getNextEntry();

							if ( entry == null ){

								break;
							}

							String	name = entry.getName();


							if ( name.equals( "plugin_install.properties" )){

								ByteArrayOutputStream baos = new ByteArrayOutputStream( 32*1024 );

								byte[]	buffer = new byte[65536];

								while( true ){

									int	len = zis.read( buffer );

									if ( len <= 0 ){

										break;
									}

									baos.write( buffer, 0, len );
								}

								try{
									LineNumberReader lnr = new LineNumberReader( new InputStreamReader( new ByteArrayInputStream( baos.toByteArray()), "UTF-8"));

									while( true ){

										String	line = lnr.readLine();

										if ( line == null ){

											break;
										}

										line = line.trim();

										if ( line.endsWith( "defer_install" )){

											force_indirect_install = true;

										}else{

											String[] command = line.split( "," );

											if ( command.length > 1 ){

												List<String[]> commands = install_properties.get( command[0] );

												if ( commands == null ){

													commands = new ArrayList<>();

													install_properties.put( command[0], commands );
												}

												commands.add( command );
											}
										}
									}
								}catch( Throwable e ){

									Debug.out( e );
								}

								continue;

							}else if ( !( name.equals( "azureus.sig" ) || name.endsWith("/"))){

								if ( common_prefix == null ){

									common_prefix = name;

								}else{
									int	len = 0;

									for (int i=0;i<Math.min(common_prefix.length(), name.length());i++){

										if ( common_prefix.charAt(i) == name.charAt(i)){

											len++;

										}else{

											break;
										}
									}

									common_prefix = common_prefix.substring(0,len);
								}

								int	plat_pos = name.indexOf( "platform/" );

								if ( plat_pos != -1 ){

									plat_pos += 9;

									int	plat_end_pos = name.indexOf( "/", plat_pos );

									if ( plat_end_pos != -1 ){

										String	platform 		= name.substring( plat_pos, plat_end_pos );
										String	sub_platform 	= null;

										int	sub_plat_pos = platform.indexOf("_");

										if ( sub_plat_pos != -1 ){

											sub_platform = platform.substring( sub_plat_pos+1 );

											platform	= platform.substring(0,sub_plat_pos);
										}

										if ( 	(Constants.isWindows && platform.equalsIgnoreCase( "windows" )) ||
												(Constants.isLinux && platform.equalsIgnoreCase( "linux" ))	||
												(Constants.isUnix && platform.equalsIgnoreCase( "unix" ))	||
												(Constants.isFreeBSD && platform.equalsIgnoreCase( "freebsd" ))	||
												(Constants.isSolaris && platform.equalsIgnoreCase( "solaris" ))	||
												(Constants.isOSX && platform.equalsIgnoreCase( "osx" ))){


											selected_platform = platform;

											if ( sub_platform != null ){

												if ( !selected_sub_platforms.contains( sub_platform )){

													selected_sub_platforms.add( sub_platform );
												}
											}
										}
									}
								}
							}

							byte[]	buffer = new byte[65536];

							while( true ){

								int	len = zis.read( buffer );

								if ( len <= 0 ){

									break;
								}
							}
						}
					}finally{

						zis.close();
					}

					if ( selected_platform != null ){

						String[]	options = new String[selected_sub_platforms.size()];

						selected_sub_platforms.toArray( options );

						if ( options.length == 1 ){

							selected_platform += "_" + options[0];

							log.log( LoggerChannel.LT_INFORMATION,
									"platform is '" + selected_platform +"'" );

						}else if ( options.length > 1 ){

							String selected_sub_platform = (String)
								update.getDecision(
										UpdateManagerDecisionListener.DT_STRING_ARRAY_TO_STRING,
										"Select Platform",
										"Multiple platform options exist for this plugin, please select required one",
										options );

							if ( selected_sub_platform == null ){

								throw( new Exception( "Valid sub-platform selection not selected" ));

							}else{

								selected_platform += "_" + selected_sub_platform;

								log.log( LoggerChannel.LT_INFORMATION,
										"platform is '" + selected_platform +"'" );
							}
						}
					}

					if ( common_prefix != null ){

						int	pos = common_prefix.lastIndexOf("/");

						if ( pos == -1 ){

							common_prefix = "";
						}else{

							common_prefix = common_prefix.substring(0,pos+1);
						}

						zis = new ZipInputStream(
									new BufferedInputStream( new FileInputStream( target_jar_zip ) ));

						try{
							while( true ){

								ZipEntry	entry = zis.getNextEntry();

								if ( entry == null ){

									break;
								}

								String	name = entry.getName();

								OutputStream	entry_os = null;

								File			origin					= null;
								File			initial_target			= null;
								File			final_target			= null;
								boolean			is_plugin_properties 	= false;

								try{
									if ( 	name.length() >= common_prefix.length() &&
											!( name.equals( "azureus.sig") || name.endsWith("/"))){

										boolean	skip_file = false;

										String	file_name = entry.getName().substring( common_prefix.length());

										if ( selected_platform != null ){

											if (file_name.contains("platform/")){

												String	bit_to_remove = "platform/" + selected_platform;

												int	pp = file_name.indexOf( bit_to_remove );

												if ( pp != -1 ){

													file_name = file_name.substring(0,pp) + file_name.substring(pp+bit_to_remove.length()+1);

												}else{

														// stuff from other platform, ignore it

													skip_file	= true;
												}
											}
										}

										File	install_root;
										File	origin_root;

										if ( file_name.startsWith( "shared/lib" )){

												// updating shared resources, force restart

											update.setRestartRequired( Update.RESTART_REQUIRED_YES );

												// indicate that we can't go ahead and load the plugin
												// later in the code

											unloadable	= false;

											if ( plugin.getPluginState().isShared()){

												origin_root		= prog_dir;
												install_root 	= target_prog_dir;

											}else{

												origin_root		= user_dir;
												install_root 	= target_user_dir;
											}
										}else{

											origin_root		= plugin_dir;
											install_root 	= target_plugin_dir;
										}

										origin 			= new File( origin_root, file_name );
										initial_target 	= new File( install_root, file_name );

										final_target	= initial_target;

										if ( origin.exists()){

											if ( 	file_name.indexOf( '/' ) == -1 &&
													(	file_name.toLowerCase(MessageText.LOCALE_ENGLISH).endsWith(".properties") ||
														file_name.toLowerCase(MessageText.LOCALE_ENGLISH).endsWith(".config" ))){

													// don't trash properties and config files in root as users may well
													// have modified them

												is_plugin_properties	= file_name.toLowerCase(MessageText.LOCALE_ENGLISH).equals("plugin.properties");

												String	old_file_name = file_name;

												file_name = file_name + "_" + target_version;

												final_target = new File( install_root, file_name );

												log.log( LoggerChannel.LT_INFORMATION,
															"saving new file '" + old_file_name + "'as '" + file_name +"'" );
											}else{

													// if it is a versioned artifact then we don't need to do anything as the update contains
													// the same version of the file

												if ( isVersioned( file_name )){

													log.log( LoggerChannel.LT_INFORMATION,
															"Version '" + file_name +"' already present, skipping" );

													skip_file	= true;

												}else{

													log.log( LoggerChannel.LT_INFORMATION,
															"overwriting '" + file_name +"'" );

													File	backup = new File( origin.getParentFile(), origin.getName() + ".bak" );

														// back up just in case

													if ( force_indirect_install ){

														if ( backup.exists()){

															installer.addRemoveAction( backup.getAbsolutePath());
														}

														installer.addMoveAction( origin.getAbsolutePath(), backup.getAbsolutePath());

													}else{

														if ( backup.exists()){

															backup.delete();
														}

														if ( !initial_target.renameTo( backup )){

															log.log( LoggerChannel.LT_INFORMATION,
																	"    failed to backup '" + file_name +"', deferring until restart" );

															if ( installer == null ){

																update.setRestartRequired( Update.RESTART_REQUIRED_YES );

																installer = update.getCheckInstance().createInstaller();
															}

															File	tmp = new File( initial_target.getParentFile(), initial_target.getName() + ".tmp" );

															tmp.delete();

															installer.addMoveAction( tmp.getAbsolutePath(), initial_target.getAbsolutePath());

															final_target = tmp;
														}
													}
												}
											}
										}

										if ( !skip_file ){

											FileUtil.mkdirs(final_target.getParentFile());

											entry_os = new FileOutputStream( final_target );
										}
									}

									byte[]	buffer = new byte[65536];

									while( true ){

										int	len = zis.read( buffer );

										if ( len <= 0 ){

											break;
										}

										if ( entry_os != null ){

											entry_os.write( buffer, 0, len );
										}
									}
								}finally{

									if ( entry_os != null ){

										entry_os.close();
									}
								}

								if ( is_plugin_properties ){

										// we've got to modify the plugin.version in the existing
										// file (if it exists) otherwise we keep downloading the new
										// version! Or, if the key doesn't exist, add it!

										// if we were smarter we could merge values from the
										// old one into the new one, however this is too much like
										// hard work

										// hmm, we really need to at least merge in the new
										// predefined values such as
										//	plugin.name, plugin.names
										//	plugin.class, plugin.classes
										//  plugin.langfile

									Properties	old_props 	= new Properties();
									Properties	new_props	= new Properties();

									List	props_to_delete		= new ArrayList();
									Map		props_to_replace	= new HashMap();
									Map		props_to_insert		= new HashMap();

									try{
										FileInputStream fis = new FileInputStream( origin );

										try{
											old_props.load( fis );

										}catch( Throwable e ){

										}finally{

											fis.close();

										}

										fis = new FileInputStream( final_target );

										try{
											new_props.load( fis );

										}catch( Throwable e ){

										}finally{

											fis.close();
										}
									}catch( Throwable e ){

										Debug.printStackTrace( e );
									}

									new_props.put( "plugin.version", target_version );

									String[]	prop_names =
										{ 	"plugin.name", "plugin.names",
											"plugin.class", "plugin.classes",
											"plugin.version",
											"plugin.langfile"
										};

									for (int z=0;z<prop_names.length;z++){

										String	prop_name = prop_names[z];

										String	old_name = old_props.getProperty( prop_name );
										String	new_name = new_props.getProperty( prop_name );

										if ( new_name != null ){

											if ( prop_name.equals( "plugin.name")){
												props_to_delete.add( "plugin.names" );
											}else if ( prop_name.equals( "plugin.names")){
												props_to_delete.add( "plugin.name" );
											}else if ( prop_name.equals( "plugin.class")){
												props_to_delete.add( "plugin.classes" );
											}else if ( prop_name.equals( "plugin.classes")){
												props_to_delete.add( "plugin.class" );
											}

											if ( old_name == null ){

												props_to_insert.put( prop_name, new_name );

											}else if ( !new_name.equals( old_name )){

												props_to_replace.put( prop_name, new_name );
											}
										}
									}

									File	tmp_file;

									if ( force_indirect_install ){

											// install into temp dir so we don't need to create a temp file and then rename
											// later as this installer will do this

										tmp_file = initial_target;

									}else{

										tmp_file = new File( initial_target.getParentFile(), initial_target.getName() + ".tmp" );
									}

									LineNumberReader	lnr = null;

									PrintWriter			tmp = null;

									try{
										lnr = new LineNumberReader(new FileReader( origin ));

										tmp = new PrintWriter(new FileWriter( tmp_file ));

										Iterator	it = props_to_insert.keySet().iterator();

										while( it.hasNext()){

											String	pn = (String)it.next();

											String	pv = (String)props_to_insert.get(pn);

											log.log("    Inserting property:" + pn + "=" + pv );

											tmp.println( pn + "=" + pv );
										}

										while(true){

											String	line = lnr.readLine();

											if ( line == null ){

												break;
											}

											int	ep = line.indexOf('=');

											if( ep != -1 ){

												String	pn = line.substring(0,ep).trim();

												if ( props_to_delete.contains(pn)){

													log.log("    Deleting property:" + pn );

												}else{

													String rv = (String)props_to_replace.get( pn );

													if ( rv != null ){

														log.log("    Replacing property:" + pn + " with " + rv );

														tmp.println( pn + "=" + rv );

													}else{

														tmp.println( line );
													}
												}
											}else{

												tmp.println( line );
											}
										}
									}finally{

										lnr.close();

										if ( tmp != null ){

											tmp.close();
										}
									}

									File	bak_file	= new File( origin.getParentFile(), origin.getName() + ".bak" );

									if ( force_indirect_install ){

										if ( bak_file.exists()){

											installer.addRemoveAction( bak_file.getAbsolutePath());
										}

										installer.addMoveAction( origin.getAbsolutePath(), bak_file.getAbsolutePath());

									}else{

										if ( bak_file.exists()){

											bak_file.delete();
										}

										if ( !initial_target.renameTo( bak_file)){

											throw( new IOException( "Failed to rename '" + initial_target.toString() + "' to '" + bak_file.toString() + "'" ));
										}

										if ( !tmp_file.renameTo( initial_target )){

											bak_file.renameTo( initial_target );

											throw( new IOException( "Failed to rename '" + tmp_file.toString() + "' to '" + initial_target.toString() + "'" ));
										}

										bak_file.delete();
									}

								}else if ( final_target != null && final_target.getName().equalsIgnoreCase( "update.txt" )){

									update_txt_found	= true;

									LineNumberReader lnr = null;

									try{
										lnr = new LineNumberReader( new FileReader( final_target ));

										while(true){

											String	line = lnr.readLine();

											if (line == null ){

												break;
											}

											log.log( LoggerChannel.LT_INFORMATION, line );
										}

									}catch( Throwable e ){

										Debug.printStackTrace( e );

									}finally{

										if ( lnr != null ){

											lnr.close();
										}
									}
								}
							}
						}finally{

							zis.close();
						}
					}
				}

				if ( unloadable ){

						// 	check unloadability AGAIN in case changed during activities

					String	plugin_id = plugin.getPluginID();

					PluginInterface[]	plugins = plugin.getPluginManager().getPlugins();

					boolean	plugin_unloadable 	= true;

					for (int j=0;j<plugins.length;j++){

						PluginInterface pi = plugins[j];

						if ( pi.getPluginID().equals( plugin_id )){

							plugin_unloadable &= pi.getPluginState().isUnloadable();
						}
					}

					if ( !plugin_unloadable ){

						log.log( "Switching unloadability for " + plugin_id + " as changed during update" );

						update.setRestartRequired( Update.RESTART_REQUIRED_YES );

						unloadable = false;
					}
				}

				if ( force_indirect_install ){

						// create installation move actions for the files that have been installed
						// into temp location

					boolean	defer_restart = false;

					if ( addInstallationActions( installer, install_properties, "%plugin%", target_plugin_dir, plugin_dir )){

						defer_restart = true;
					}

					if ( addInstallationActions( installer, install_properties, "%app%", target_prog_dir, prog_dir )){

						defer_restart = true;
					}

					if ( addInstallationActions( installer, install_properties, "%user%", target_user_dir, user_dir )){

						defer_restart = true;
					}

					if ( defer_restart && update.getRestartRequired() == Update.RESTART_REQUIRED_YES ){

						plugin.getPluginState().setRestartPending( true );
						
						log.log( "Deferring restart for '" + plugin.getPluginID() + "'" );

						update.setRestartRequired( Update.RESTART_REQUIRED_NO );
					}

						// don't delete temp store, it'll get deleted on restart

				}else{

					boolean	defer_restart = false;

					if ( applyInstallProperties( install_properties, "%plugin%", plugin_dir )){

						defer_restart = true;
					}

					if ( applyInstallProperties( install_properties, "%app%", prog_dir )){

						defer_restart = true;
					}

					if ( applyInstallProperties( install_properties, "%user%", user_dir )){

						defer_restart = true;
					}

					if ( unloadable ){

						log.log( "Plugin initialising, please wait... " );

						plugin.getPluginState().reload();	// this will reload all if > 1 defined

						log.log( "... initialisation complete." );

					}else{

						if ( defer_restart && update.getRestartRequired() == Update.RESTART_REQUIRED_YES ){

							plugin.getPluginState().setRestartPending( true );
							
							log.log( "Deferring restart for '" + plugin.getPluginID() + "'" );

							update.setRestartRequired( Update.RESTART_REQUIRED_NO );
						}
					}
				}
			}

			Boolean b_disable = (Boolean)update.getCheckInstance().getProperty( UpdateCheckInstance.PT_UI_DISABLE_ON_SUCCESS_SLIDEY );

			if ( update_txt_found || b_disable == null || !b_disable ){

				String msg =
					MessageText.getString(
						"plugin.update.ok",
						new String[]{ new_version, update.getName() });

				if ( update_txt_found ){

					msg += " - " + MessageText.getString( "plugin.update.ok.msg" );
				}

				log.logAlertRepeatable( update_txt_found?LoggerChannel.LT_WARNING:LoggerChannel.LT_INFORMATION, msg );
			}

			try{
				String plugin_id = plugin.getPluginID();

				PluginInitializer.fireEvent(
					checker.getCheckInstance().getType() == UpdateCheckInstance.UCI_INSTALL?PluginEvent.PEV_PLUGIN_INSTALLED: PluginEvent.PEV_PLUGIN_UPDATED,
					plugin_id );

			}catch( Throwable e ){

				Debug.out( e );
			}
			update_successful = true;

		}catch( Throwable e ){

			String msg =
					MessageText.getString(
						"plugin.update.fail",
						new String[]{ new_version, update.getName(), e.getMessage() });

			log.logAlertRepeatable( LoggerChannel.LT_ERROR, msg );

		}finally{

			if ( update_successful && update.getRestartRequired() != Update.RESTART_REQUIRED_NO ){
				
				plugin.getPluginState().setRestartPending( true );
			}
			
			update.complete( update_successful );

			if ( data != null ){

				try{
					data.close();

				}catch( Throwable e ){
				}
			}
		}
	}

	protected boolean
	addInstallationActions(
		UpdateInstaller				installer,
		Map<String,List<String[]>>	install_properties,
		String						prefix,
		File						from_file,
		File						to_file )

		throws UpdateException
	{
		boolean	defer_restart = false;

		if ( from_file.isDirectory()){

			File[]	files = from_file.listFiles();

			if ( files != null ){

				for (int i=0;i<files.length;i++){

					if ( addInstallationActions( installer, install_properties, prefix + "/" + files[i].getName(), files[i], new File( to_file, files[i].getName()))){

						defer_restart = true;
					}
				}
			}
		}else{

			installer.addMoveAction( from_file.getAbsolutePath(), to_file.getAbsolutePath());

			List<String[]> commands = install_properties.get( prefix );

			if ( commands != null ){

				for( String[] command: commands ){

					String	cmd = command[1];

					if ( cmd.equals( "chmod" )){

						if ( !Constants.isWindows ){

							log.log( "Applying " + cmd + " " + command[2] + " to " + to_file );

							installer.addChangeRightsAction( command[2], to_file.getAbsolutePath());
						}
					}else if ( cmd.equals( "rm" )){

						log.log( "Deleting " + to_file );

						installer.addRemoveAction( to_file.getAbsolutePath());

					}else if ( cmd.equals( "defer_restart" )){

						defer_restart = true;
					}
				}
			}
		}

		return( defer_restart );
	}

	protected boolean
	applyInstallProperties(
		Map<String,List<String[]>>	install_properties,
		String						prefix,
		File						to_file )
	{
		boolean	defer_restart = false;

		if ( to_file.isDirectory()){

			File[]	files = to_file.listFiles();

			if ( files != null ){

				for (int i=0;i<files.length;i++){

					File file = files[i];

					String	file_name = file.getName();

					if ( file_name.equals( "." ) || file_name.equals( ".." )){

						continue;
					}

					String	new_prefix = prefix + "/" + file_name;

					boolean	match = false;

					for ( String s: install_properties.keySet()){

						if ( s.startsWith( new_prefix )){

							match = true;

							break;
						}
					}

					if ( match ){

						if ( applyInstallProperties( install_properties, new_prefix, files[i] )){

							defer_restart = true;
						}
					}
				}
			}
		}else{

			List<String[]> commands = install_properties.get( prefix );

			if ( commands != null ){

				for( String[] command: commands ){

					String	cmd = command[1];

					if ( cmd.equals( "chmod" )){

						if ( !Constants.isWindows ){

							runCommand(
								new String[]{
									"chmod",
									command[2],
									to_file.getAbsolutePath().replaceAll(" ", "\\ ")
								});
						}
					}else if ( cmd.equals( "rm" )){

						log.log( "Deleting " + to_file );

						to_file.delete();

					}else if ( cmd.equals( "defer_restart" )){

						defer_restart = true;
					}
				}
			}
		}

		return( defer_restart );
	}

	private void
	runCommand(
		String[]	command )
	{
		try{
			command[0] = findCommand( command[0] );

			String	str = "";

			for ( String s: command ){

				str += " " + s;
			}

			log.log( "Executing" + str );

			Runtime.getRuntime().exec( command ).waitFor();

		}catch( Throwable e ){

			log.log( "Failed to execute command", e );
		}
	}

	private String
	findCommand(
		String	name )
	{
		final String[]  locations = { "/bin", "/usr/bin" };

		for ( String s: locations ){

			File f = new File( s, name );

			if ( f.exists() && f.canRead()){

				return( f.getAbsolutePath());
			}
		}

		return( name );
	}

	protected boolean
	isVersioned(
		String	name )
	{
			// assumption: versioned names are of the form <prefix>_<version>.<extension>
			// where version is mixture of digits and .s

		int	pos = name.lastIndexOf('_');

		if ( pos == -1 || name.endsWith("_")){

			return( false );
		}

			// remove everything up to _

		String	rem = name.substring(pos+1);

		pos = rem.lastIndexOf( '.' );

			// remove extension (e.g. .jar)

		if ( pos != -1 ){

			rem = rem.substring(0,pos);
		}

		for (int i=0;i<rem.length();i++){

			char	c = rem.charAt(i);

			if ( c != '.' && !Character.isDigit( c )){

				return( false );
			}
		}

		return( true );
	}

	protected void
	logMultiLine(
		String		indent,
		List		lines )
	{
		for (int i=0;i<lines.size();i++){

			log.log( LoggerChannel.LT_INFORMATION, indent + (String)lines.get(i) );
		}
	}
}
