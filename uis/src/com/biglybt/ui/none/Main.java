/*
 * Created on Jun 20, 2013
 * Created by Paul Gardner
 *
 * Copyright 2013 Azureus Software, Inc.  All rights reserved.
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


package com.biglybt.ui.none;

import java.io.File;

import com.biglybt.core.Core;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.impl.TransferSpeedValidator;
import com.biglybt.core.util.SystemProperties;
import com.biglybt.pif.PluginManager;
import com.biglybt.pif.PluginManagerDefaults;

import com.biglybt.core.CoreFactory;

public class
Main
{

	public static void
	main(
		String[]	args )
	{
        System.setProperty( "az.factory.internat.bundle", "com.biglybt.ui.none.internat.MessagesBundle" );

		COConfigurationManager.initialise();

		if ( System.getProperty(SystemProperties.SYSPROP_LOW_RESOURCE_MODE, "false" ).equals( "true" )){

			System.out.println( "Low resource mode enabled" );

			COConfigurationManager.setParameter( "Start In Low Resource Mode", true );
			COConfigurationManager.setParameter( "DHT.protocol.version.min", 51 );

			COConfigurationManager.setParameter( TransferSpeedValidator.AUTO_UPLOAD_ENABLED_CONFIGKEY, false );
			COConfigurationManager.setParameter( TransferSpeedValidator.AUTO_UPLOAD_SEEDING_ENABLED_CONFIGKEY, false );

		    COConfigurationManager.setParameter( "dht.net.cvs_v4.enable", false );
		    COConfigurationManager.setParameter( "dht.net.main_v6.enable", false );

			COConfigurationManager.setParameter( "network.tcp.read.select.time", 500 );
			COConfigurationManager.setParameter( "network.tcp.read.select.min.time", 500 );
			COConfigurationManager.setParameter( "network.tcp.write.select.time", 500 );
	        COConfigurationManager.setParameter( "network.tcp.write.select.min.time", 500);
			COConfigurationManager.setParameter( "network.tcp.connect.select.time", 500 );
	        COConfigurationManager.setParameter( "network.tcp.connect.select.min.time", 500);

	        COConfigurationManager.setParameter( "network.udp.poll.time", 100 );

	        COConfigurationManager.setParameter( "network.utp.poll.time", 100 );


			COConfigurationManager.setParameter( "network.control.read.idle.time", 100 );
			COConfigurationManager.setParameter( "network.control.write.idle.time", 100 );

			COConfigurationManager.setParameter( "diskmanager.perf.cache.enable", true );
			COConfigurationManager.setParameter( "diskmanager.perf.cache.size", 4 );
			COConfigurationManager.setParameter( "diskmanager.perf.cache.enable.read", false );

			COConfigurationManager.setParameter( "peermanager.schedule.time", 500 );

		    PluginManagerDefaults defaults = PluginManager.getDefaults();

		    defaults.setDefaultPluginEnabled( PluginManagerDefaults.PID_BUDDY, false );
		    defaults.setDefaultPluginEnabled( PluginManagerDefaults.PID_SHARE_HOSTER, false );
		    defaults.setDefaultPluginEnabled( PluginManagerDefaults.PID_RSS, false );
		    defaults.setDefaultPluginEnabled( PluginManagerDefaults.PID_NET_STATUS, false );

		}

		String download_dir = System.getProperty(SystemProperties.SYSPROP_FOLDER_DOWNLOAD, "" );

		if ( download_dir.length() > 0 ){

			File dir = new File( download_dir );

			dir.mkdirs();

			System.out.println( "Download directory set to '" + dir + "'" );

			COConfigurationManager.setParameter( "Default save path", dir.getAbsolutePath());
		}

		String torrent_dir = System.getProperty(SystemProperties.SYSPROP_FOLDER_TORRENT, "" );

		if ( torrent_dir.length() > 0 ){

			File dir = new File( torrent_dir );

			dir.mkdirs();

			System.out.println( "Torrent directory set to '" + dir + "'" );

			COConfigurationManager.setParameter( "Save Torrent Files", true );

			COConfigurationManager.setParameter( "General_sDefaultTorrent_Directory", dir.getAbsolutePath());
		}

		Core core = CoreFactory.create();

		core.start();
	}
}
