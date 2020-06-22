/*
 * Created on 30-Nov-2004
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

package com.biglybt.pifimpl.local.installer;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.FileUtil;
import com.biglybt.pif.Plugin;
import com.biglybt.pif.PluginException;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.installer.FilePluginInstaller;
import com.biglybt.pif.update.UpdatableComponent;
import com.biglybt.pif.update.Update;
import com.biglybt.pif.update.UpdateCheckInstance;
import com.biglybt.pif.update.UpdateChecker;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloader;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.pifimpl.update.PluginUpdatePlugin;
import com.biglybt.pifimpl.update.sf.SFPluginDetailsLoader;
import com.biglybt.pifimpl.update.sf.SFPluginDetailsLoaderFactory;

/**
 * @author parg
 *
 */

public class
FilePluginInstallerImpl
	extends InstallablePluginImpl
	implements FilePluginInstaller
{
	protected PluginInstallerImpl		installer;
	protected File						file;
	protected String					id;
	protected String					version;
	protected String					name;
	protected boolean					is_jar;

	protected
	FilePluginInstallerImpl(
		PluginInstallerImpl	_installer,
		File				_file )

		throws PluginException
	{
		super( _installer );

		installer	= _installer;
		file		= _file;

		String	name = file.getName();

		int	pos = name.lastIndexOf( "." );

		boolean	ok = false;

		if ( pos != -1 ){

			String	prefix = name.substring(0,pos);
			String	suffix = name.substring(pos+1);


			// If the name part contains "_src" in it, then strip it out,
			// it'll just cause us more hassle to deal with it later.
			if (prefix.lastIndexOf("_src") != -1) {
				if (prefix.endsWith("_src")) {
					prefix = prefix.substring(0, prefix.length()-4);
				}
				else {
					int src_bit_pos = prefix.lastIndexOf("_src");
					prefix = prefix.substring(0, src_bit_pos) + prefix.substring(src_bit_pos+1);
				}
			}

			if ( 	suffix.toLowerCase(MessageText.LOCALE_ENGLISH).equals( "jar") ||
					suffix.toLowerCase(MessageText.LOCALE_ENGLISH).equals( "zip" )){

				is_jar		= suffix.toLowerCase(MessageText.LOCALE_ENGLISH).equals( "jar");

					// See if we can get at the plugin.properties in the file

				Properties	properties	= null;

				ZipInputStream	zis = null;

				try{
					zis =
						new ZipInputStream(
								new BufferedInputStream( FileUtil.newFileInputStream( file ) ));


						while( properties == null ){

							ZipEntry	entry = zis.getNextEntry();

							if ( entry == null ){

								break;
							}

							String	zip_name = entry.getName().toLowerCase( MessageText.LOCALE_ENGLISH );

							// System.out.println( "zis1:" + zip_name );

							if ( zip_name.equals( "plugin.properties" ) || zip_name.endsWith( "/plugin.properties")){

								properties	= new Properties();

								properties.load( zis );

							}else if ( zip_name.endsWith( ".jar" )){

								ZipInputStream	zis2 = new ZipInputStream( zis );

								while( properties == null ){

									ZipEntry	entry2 = zis2.getNextEntry();

									if ( entry2 == null ){

										break;
									}

									String	zip_name2 = entry2.getName().toLowerCase( MessageText.LOCALE_ENGLISH );

									// System.out.println( "    zis2:" + zip_name2 );

									if ( zip_name2.equals( "plugin.properties" )){

										properties	= new Properties();

										properties.load( zis2 );

									}
								}
							}
						}
				}catch( Throwable e ){

					throw( new PluginException( "Failed to read plugin file", e ));

				}finally{

					if ( zis != null ){

						try{
							zis.close();

						}catch( Throwable e ){

							Debug.printStackTrace(e);
						}
					}
				}

				pos = prefix.lastIndexOf("_");
				String filename_id = null, filename_version = null;
				if ( pos != -1 ){
					filename_id 			= prefix.substring(0,pos);
					filename_version		= prefix.substring(pos+1);
				}

				if ( properties == null ){

						// one valid possibility here, this is a built-in plugin. this doesn't have
						// a plugin.properties

					if (filename_id != null) {

						id 			= filename_id;
						version		= filename_version;

						PluginInterface pi = installer.getPluginManager().getPluginInterfaceByID( id );

						ok =  	pi != null &&
								(	pi.getPluginDirectoryName() == null ||
								    pi.getPluginDirectoryName().length() == 0 );
					}

					if ( !ok ){

						throw( new PluginException( "Mandatory file 'plugin.properties' not found in plugin file" ));
					}
				}else{ // properties != null

					// unfortunately plugin.id isn't mandatory for the properties, and neither is plugin.version

					PluginInitializer.checkJDKVersion( "", properties, false );
					PluginInitializer.checkCoreAppVersion("", properties, false);

					id		= properties.getProperty( "plugin.id" );
					version	= properties.getProperty( "plugin.version" );

					// Force both versions to be the same if they are both defined.
					String prop_version = version;
					if (prop_version != null && filename_version != null && !filename_version.equals(prop_version)) {
					    throw new PluginException("inconsistent versions [file=" + filename_version + ", prop=" + prop_version + "]");
					}

				}

				if ( id == null ){

					// see if plugin is already loaded, if so we can get the id from it

					String	plugin_class = properties.getProperty("plugin.class");

					if ( plugin_class == null ){

						String	plugin_classes = properties.getProperty( "plugin.classes" );

						if ( plugin_classes != null ){

							int	semi_pos = plugin_classes.indexOf(";");

							if ( semi_pos == -1 ){

								plugin_class	= plugin_classes;

							}else{

								plugin_class = plugin_classes.substring( 0, semi_pos );
							}
						}
					}

					if ( plugin_class != null ){

						try{
							PluginInterface pi = installer.getPluginManager().getPluginInterfaceByClass( plugin_class );

							if ( pi != null ){

								id	= pi.getPluginID();
							}
						}catch( Throwable ignore ){

						}
					}
				}

				pos = prefix.lastIndexOf("_");

				if ( pos != -1 ){

					id 			= id==null?prefix.substring(0,pos):id;

						// see if we can normalise the ID based on SF values

						// special case for aznettor as we want to avoid triggering another probably failure to list plugins ids
						// when updating anonymously
					
					if ( !id.equals( "aznettor" )){
						
						try{
							SFPluginDetailsLoader	loader = SFPluginDetailsLoaderFactory.getSingleton();
	
							String[]	ids = loader.getPluginIDs();
	
							for (int i=0;i<ids.length;i++){
	
								if ( ids[i].equalsIgnoreCase(id)){
	
									id = ids[i];
	
									break;
								}
							}
						}catch( Throwable e ){
	
							Debug.printStackTrace(e);
						}
					}
					
					version		= version == null?prefix.substring(pos+1):version;

				}

				this.name = id;

				if ( properties != null ){

					String plugin_name = properties.getProperty( "plugin.name" );

					if ( plugin_name != null ){

						this.name = plugin_name;
					}
				}

				ok	= id != null && version != null;
			}
		}

		if ( !ok ){

			throw( new PluginException( "Invalid plugin file name: must be of form <pluginid>_<version>.[jar|zip]" ));
		}
	}

	@Override
	public File
	getFile()
	{
		return( file );
	}

	@Override
	public String
	getId()
	{
		return( id );
	}

	@Override
	public String
	getVersion()
	{
		return( version );
	}

	@Override
	public String
	getName()
	{
		return( name );
	}

	@Override
	public String
	getDescription()
	{
		return( file.toString());
	}

	@Override
	public String
	getRelativeURLBase()
	{
		return( "" );
	}

	@Override
	public void
	addUpdate(
		UpdateCheckInstance	inst,
		final PluginUpdatePlugin	plugin_update_plugin,
		final Plugin				plugin,
		final PluginInterface		plugin_interface )
	{
		inst.addUpdatableComponent(
				new UpdatableComponent()
				{
					@Override
					public String
					getName()
					{
						return( name );
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
							ResourceDownloader rd =
								plugin_interface.getUtilities().getResourceDownloaderFactory().create( file );

							plugin_update_plugin.addUpdate(
								plugin_interface,
								checker,
								getName(),
								new String[]{"Installation from file: " + file.toString()},
								"",	// old version
								version,
								rd,
								is_jar,
								plugin_interface.getPluginState().isUnloadable()?Update.RESTART_REQUIRED_NO:Update.RESTART_REQUIRED_YES,
								false );

						}finally{

							checker.completed();
						}

					}
				}, false );
	}
}
