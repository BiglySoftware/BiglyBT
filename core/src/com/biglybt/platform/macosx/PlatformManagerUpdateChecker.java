/*
 * Created on 07-May-2004
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

package com.biglybt.platform.macosx;

/**
 * @author parg
 *
 */

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.biglybt.core.html.HTMLUtils;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemProperties;
import com.biglybt.pif.Plugin;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.update.UpdatableComponent;
import com.biglybt.pif.update.Update;
import com.biglybt.pif.update.UpdateChecker;
import com.biglybt.pif.update.UpdateInstaller;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloader;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderAdapter;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderException;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderFactory;
import com.biglybt.pifimpl.local.utils.resourcedownloader.ResourceDownloaderFactoryImpl;
import com.biglybt.pifimpl.update.sf.SFPluginDetails;
import com.biglybt.pifimpl.update.sf.SFPluginDetailsLoaderFactory;
import com.biglybt.platform.PlatformManager;
import com.biglybt.platform.PlatformManagerCapabilities;
import com.biglybt.platform.PlatformManagerFactory;
import com.biglybt.plugin.I2PHelpers;

public class
PlatformManagerUpdateChecker
	implements Plugin, UpdatableComponent
{
	private static final LogIDs LOGID = LogIDs.CORE;
	public static final String UPDATE_NAME	= "Platform-specific support";

	public static final int	RD_SIZE_RETRIES	= 3;
	public static final int	RD_SIZE_TIMEOUT	= 10000;

	protected PluginInterface			plugin_interface;

	@Override
	public void
	initialize(
		PluginInterface _plugin_interface)
	{
		plugin_interface	= _plugin_interface;

		plugin_interface.getPluginProperties().setProperty( "plugin.name", "Platform-Specific Support" );

		String	version = "1.0";	// default version if plugin not present

		PlatformManager platform	= PlatformManagerFactory.getPlatformManager();

		if ( platform.getPlatformType() == PlatformManager.PT_MACOSX ){

			if ( platform.hasCapability( PlatformManagerCapabilities.GetVersion )){

				try{
					version = platform.getVersion();

				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}
			}

			plugin_interface.getUpdateManager().registerUpdatableComponent( this, false );

		}else{

			plugin_interface.getPluginProperties().setProperty( "plugin.version.info", "Not required for this platform" );

		}

		plugin_interface.getPluginProperties().setProperty( "plugin.version", version );
	}

	@Override
	public String
	getName()
	{
		return( UPDATE_NAME );
	}

	@Override
	public int
	getMaximumCheckTime()
	{
		return(( RD_SIZE_RETRIES * RD_SIZE_TIMEOUT )/1000);
	}

	@Override
	public void
	checkForUpdate(
		final UpdateChecker	checker )
	{
		try{
			SFPluginDetails	sf_details = SFPluginDetailsLoaderFactory.getSingleton().getPluginDetails( plugin_interface.getPluginID());

			String	current_version = plugin_interface.getPluginVersion();

			if (Logger.isEnabled())
				Logger.log(new LogEvent(LOGID,
						"PlatformManager:OSX update check starts: current = "
								+ current_version));

			boolean current_az_is_cvs	= Constants.isCVSVersion();

			String sf_plugin_version	= sf_details.getVersion();

			String sf_comp_version	 	= sf_plugin_version;

			if ( current_az_is_cvs ){

				String	sf_cvs_version = sf_details.getCVSVersion();

				if ( sf_cvs_version.length() > 0 ){

						// sf cvs version ALWAYS entry in _CVS

					sf_plugin_version	= sf_cvs_version;

					sf_comp_version = sf_plugin_version.substring(0,sf_plugin_version.length()-4);
				}
			}

			String	target_version	= null;

			if (	 sf_comp_version.length() == 0 ||
					!Character.isDigit(sf_comp_version.charAt(0))){

				if (Logger.isEnabled())
					Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
							"PlatformManager:OSX no valid version to check against ("
									+ sf_comp_version + ")"));

			}else if ( Constants.compareVersions( current_version, sf_comp_version ) < 0 ){

				target_version	= sf_comp_version;
			}

			checker.reportProgress( "OSX: current = " + current_version + ", latest = " + sf_comp_version );

			if (Logger.isEnabled())
				Logger.log(new LogEvent(LOGID,
						"PlatformManager:OSX update required = "
								+ (target_version != null)));

			if ( target_version != null ){

				String target_download		= sf_details.getDownloadURL();

				if ( current_az_is_cvs ){

					String	sf_cvs_version = sf_details.getCVSVersion();

					if ( sf_cvs_version.length() > 0 ){

						target_download	= sf_details.getCVSDownloadURL();
					}
				}

				ResourceDownloaderFactory rdf = ResourceDownloaderFactoryImpl.getSingleton();

				ResourceDownloader direct_rdl = rdf.create( new URL( target_download ));

				String	torrent_download = Constants.URL_PLUGINS_TORRENT_BASE;

				int	slash_pos = target_download.lastIndexOf("/");

				if ( slash_pos == -1 ){

					torrent_download += target_download;

				}else{

					torrent_download += target_download.substring( slash_pos + 1 );
				}

				torrent_download	+= ".torrent";

				if ( I2PHelpers.isI2PInstalled()){
					
					torrent_download += "?i2p=1";
				}
				
				ResourceDownloader torrent_rdl = rdf.create( new URL( torrent_download ));

				torrent_rdl	= rdf.getSuffixBasedDownloader( torrent_rdl );

					// create an alternate downloader with torrent attempt first

				ResourceDownloader alternate_rdl = rdf.getAlternateDownloader( new ResourceDownloader[]{ torrent_rdl, direct_rdl });

					// get size here so it is cached

				rdf.getTimeoutDownloader(rdf.getRetryDownloader(alternate_rdl,RD_SIZE_RETRIES),RD_SIZE_TIMEOUT).getSize();


				List	update_desc = new ArrayList();

				List	desc_lines = HTMLUtils.convertHTMLToText( "", sf_details.getDescription());

				update_desc.addAll( desc_lines );

				List	comment_lines = HTMLUtils.convertHTMLToText( "    ", sf_details.getComment());

				update_desc.addAll( comment_lines );

				String[]	update_d = new String[update_desc.size()];

				update_desc.toArray( update_d );

				final Update	update =
					checker.addUpdate(
						UPDATE_NAME,
						update_d,
						current_version,
						target_version,
						alternate_rdl,
						Update.RESTART_REQUIRED_YES );

				update.setDescriptionURL(sf_details.getInfoURL());

				alternate_rdl.addListener(
						new ResourceDownloaderAdapter()
						{
							@Override
							public boolean
							completed(
								final ResourceDownloader	downloader,
								InputStream					data )
							{
								installUpdate( checker, update, downloader, data );

								return( true );
							}

							@Override
							public void
							failed(
								ResourceDownloader			downloader,
								ResourceDownloaderException e )
							{
								Debug.out( downloader.getName() + " failed", e );

								update.complete( false );
							}
						});
			}
		}catch( Throwable e ){

			Debug.printStackTrace( e );

			checker.reportProgress( "Failed to load plugin details for the platform manager: " + Debug.getNestedExceptionMessage(e));

			checker.setFailed( new Exception( "Failed to load plugin details for the platform manager", e ));

		}finally{

			checker.completed();
		}
	}

	protected void
	installUpdate(
		UpdateChecker		checker,
		Update 				update,
		ResourceDownloader	rd,
		InputStream			data )
	{
		ZipInputStream zip = null;

		try {
			data = update.verifyData( data, true );

			rd.reportActivity( "Data verified successfully" );

			UpdateInstaller installer = checker.createInstaller();

			zip = new ZipInputStream(data);

			ZipEntry entry = null;

			while ((entry = zip.getNextEntry()) != null) {

				String name = entry.getName();

				String target_dir = Constants.isArm?"osx_arm/":"osx/";
				
				if ( name.toLowerCase().startsWith( target_dir )){

						// OSX only files

					name = name.substring(target_dir.length());

					// skip the directory entry

					if ( name.length() > 0 ){

						rd.reportActivity("Adding update action for '" + name + "'");

						if (Logger.isEnabled())
							Logger.log(new LogEvent(LOGID,
									"PlatformManager:OSX adding action for '" + name + "'"));

							// handle sub-dirs

						String	resource_name = name.replaceAll( "/", "-" );

						installer.addResource( resource_name, zip, false );

						String appDir = installer.getInstallDir();
						
						String dotBiglyBT = ".biglybt/";
						
						if ( name.startsWith( dotBiglyBT )) {
							
							if ( appDir.endsWith( ".biglybt" )){
								
									// this is the expected case
								
								name = name.substring( dotBiglyBT.length());
	
								String target = appDir + File.separator + name;
	
								installer.addMoveAction( resource_name, target );
	
								if ( name.endsWith( ".jnilib" ) || name.endsWith( "JavaApplicationStub" )){
	
									installer.addChangeRightsAction( "755", target );
								}
							}else{
								
									// dump directly into appdir
								
								name = name.substring( name.lastIndexOf( "/" ) + 1 );
								
								String target = appDir + File.separator + name;
	
								installer.addMoveAction( resource_name, target );
	
								if ( name.endsWith( ".jnilib" ) || name.endsWith( "JavaApplicationStub" )){
	
									installer.addChangeRightsAction( "755", target );
								}
							}
							
						}else {
						
								// old layout
							
							String contentsResourceJava = "Contents/Resources/Java/";
							if (name.startsWith(contentsResourceJava)) {
								// trying to install something into the "Java" dir
								// New installs have the "Java" dir as the Install dir.
								name = name.substring(contentsResourceJava.length());
							}
	
							String target = appDir + File.separator + name;
	
							installer.addMoveAction( resource_name, target );
	
							if ( name.endsWith( ".jnilib" ) || name.endsWith( "JavaApplicationStub" )){
	
								installer.addChangeRightsAction( "755", target );
							}
						}
					}
				}
			}

			update.complete( true );

		} catch (Throwable e) {

			update.complete( false );

			rd.reportActivity("Update install failed:" + e.getMessage());

		}finally{

			if ( zip != null ){

				try{
					zip.close();

				}catch( Throwable e ){
				}
			}
		}
	}

	protected List
	splitMultiLine(
		String		indent,
		String		text )
	{
		int		pos = 0;

		String	lc_text = text.toLowerCase();

		List	lines = new ArrayList();

		while( true ){

			String	line;

			int	p1 = lc_text.indexOf( "<br>", pos );

			if ( p1 == -1 ){

				line = text.substring(pos);

			}else{

				line = text.substring(pos,p1);

				pos = p1+4;
			}

			lines.add( indent + line );

			if ( p1 == -1 ){

				break;
			}
		}

		return( lines );
	}
}
