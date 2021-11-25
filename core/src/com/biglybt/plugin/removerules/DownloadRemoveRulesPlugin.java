/*
 * Created on 06-Jul-2004
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

package com.biglybt.plugin.removerules;

/**
 * @author parg
 *
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DelayedEvent;
import com.biglybt.core.util.SystemTime;
import com.biglybt.pif.Plugin;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.disk.DiskManagerFileInfo;
import com.biglybt.pif.download.*;
import com.biglybt.pif.logging.LoggerChannel;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.ui.config.BooleanParameter;
import com.biglybt.pif.ui.model.BasicPluginConfigModel;

public class
DownloadRemoveRulesPlugin
	implements Plugin, DownloadManagerListener
{
	public static final int			INITIAL_DELAY			= 60*1000;
	public static final int			DELAYED_REMOVAL_PERIOD	= 60*1000;

	public static final int			AELITIS_BIG_TORRENT_SEED_LIMIT		= 10000;
	public static final int			AELITIS_SMALL_TORRENT_SEED_LIMIT	= 1000;

	public static final int			MAX_SEED_TO_PEER_RATIO	= 10;	// 10 to 1

	protected PluginInterface plugin_interface;
	protected boolean				closing;

	protected Map					dm_listener_map		= new HashMap(10);
	protected List					monitored_downloads	= new ArrayList();

	protected LoggerChannel 		log;

	protected BooleanParameter 	remove_unauthorised;
	protected BooleanParameter 	remove_unauthorised_seeding_only;
	protected BooleanParameter 	remove_unauthorised_data;

	protected BooleanParameter 	remove_update_torrents;

	public static void
	load(
		PluginInterface		plugin_interface )
	{
		plugin_interface.getPluginProperties().setProperty( "plugin.version", 	"1.0" );
		plugin_interface.getPluginProperties().setProperty( "plugin.name", "Download Remove Rules" );
	}

	@Override
	public void
	initialize(
		PluginInterface 	_plugin_interface )
	{
		plugin_interface	= _plugin_interface;

		log = plugin_interface.getLogger().getChannel("DLRemRules");

		BasicPluginConfigModel	config = plugin_interface.getUIManager().createBasicPluginConfigModel( "torrents", "download.removerules.name" );

		config.addLabelParameter2( "download.removerules.unauthorised.info" );

		remove_unauthorised =
			config.addBooleanParameter2( "download.removerules.unauthorised", "download.removerules.unauthorised", false );

		remove_unauthorised_seeding_only =
			config.addBooleanParameter2( "download.removerules.unauthorised.seedingonly", "download.removerules.unauthorised.seedingonly", true );

		remove_unauthorised_data =
			config.addBooleanParameter2( "download.removerules.unauthorised.data", "download.removerules.unauthorised.data", false );

		remove_unauthorised.addEnabledOnSelection( remove_unauthorised_seeding_only );
		remove_unauthorised.addEnabledOnSelection( remove_unauthorised_data );

		remove_update_torrents =
			config.addBooleanParameter2( "download.removerules.updatetorrents", "download.removerules.updatetorrents", true );

		new DelayedEvent(
				"DownloadRemovalRules",
				INITIAL_DELAY,
				new AERunnable()
				{
					@Override
					public void
					runSupport()
					{
						plugin_interface.getDownloadManager().addListener( DownloadRemoveRulesPlugin.this );
					}
				});
	}

	@Override
	public void
	downloadAdded(
		final Download	download )
	{
			// we don't auto-remove non-persistent downloads as these are managed
			// elsewhere (e.g. shares)

		if ( !download.isPersistent()){

			return;
		}

			// auto remove low noise torrents if their data is missing

		if ( download.getFlag( Download.FLAG_LOW_NOISE )){

			DiskManagerFileInfo[] files = download.getDiskManagerFileInfo();

			if ( files.length == 1 ){

				DiskManagerFileInfo file = files[0];

					// completed only

				if ( 	file.getDownloaded() == file.getLength() &&
						!file.getFile().exists()){

					log.log( "Removing low-noise download '" + download.getName() + " as data missing" );

					removeDownload( download, false );
				}
			}
		}

		DownloadTrackerListener	listener =
			new DownloadTrackerListener()
			{
				@Override
				public void
				scrapeResult(
					DownloadScrapeResult	response )
				{
					if ( closing ){

						return;
					}

					handleScrape( download, response );
				}

				@Override
				public void
				announceResult(
					DownloadAnnounceResult			response )
				{
					if ( closing ){

						return;
					}

					handleAnnounce( download, response );
				}
			};

		monitored_downloads.add( download );

		dm_listener_map.put( download, listener );

		download.addTrackerListener( listener );
	}

	protected void
	handleScrape(
		Download				download,
		DownloadScrapeResult	response )
	{
		String	status = response.getStatus();

		if ( status == null ){

			status = "";
		}

		handleAnnounceScrapeStatus( download, status );
	}

	protected void
	handleAnnounce(
		Download				download,
		DownloadAnnounceResult	response )
	{
		String	reason = "";

		if ( response.getResponseType() == DownloadAnnounceResult.RT_ERROR ){

			reason = response.getError();

			if ( reason == null ){

				reason = "";
			}
		}

		handleAnnounceScrapeStatus( download, reason );
	}

	protected void
	handleAnnounceScrapeStatus(
		Download		download,
		String			status )
	{
		if ( !monitored_downloads.contains( download )){

			return;
		}

		status = status.toLowerCase();

		boolean	download_completed = download.isComplete();

		if (status.contains("not authori") ||
			status.toLowerCase().contains("unauthori")){

			if ( remove_unauthorised.getValue() &&
				 (	(!remove_unauthorised_seeding_only.getValue()) ||
				 	download_completed )){

				log.log(download.getTorrent(), LoggerChannel.LT_INFORMATION, "Download '"
						+ download.getName() + "' is unauthorised and removal triggered");

				removeDownload( download, remove_unauthorised_data.getValue() );

				return;
			}
		}
	}

	protected void
	removeDownloadDelayed(
		final Download		download,
		final boolean		remove_data )
	{
		monitored_downloads.remove( download );

			// we need to delay this because other actions may be being performed
			// on the download (e.g. completion may trigger update install)

		plugin_interface.getUtilities().createThread(
			"delayedRemoval",
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					try{
						Thread.sleep( DELAYED_REMOVAL_PERIOD );

						removeDownload( download, remove_data );

					}catch( Throwable e ){

						Debug.printStackTrace( e );
					}
				}
			});
	}

	protected void
	removeDownload(
		final Download		download,
		final boolean		remove_data )
	{
		monitored_downloads.remove( download );

		if ( download.getState() == Download.ST_STOPPED ){

			try{
				download.remove( false, remove_data );

			}catch( Throwable e ){

				log.logAlert( "Automatic removal of download '" + download.getName() + "' failed", e );
			}
		}else{

			download.addListener(
				new DownloadListener()
				{
					@Override
					public void
					stateChanged(
						Download		download,
						int				old_state,
						int				new_state )
					{
						log.log(download.getTorrent(), LoggerChannel.LT_INFORMATION,
							"download state changed to '" + new_state + "'");

						if ( new_state == Download.ST_STOPPED ){

							try{
								download.remove( false, remove_data );

								String msg = plugin_interface.getUtilities().getLocaleUtilities().getLocalisedMessageText(
										"download.removerules.removed.ok", new String[] {
											download.getName()
										});

								if (download.getFlag(Download.FLAG_LOW_NOISE)) {
									log.log(download.getTorrent(), LoggerChannel.LT_INFORMATION,
											msg);
								} else {
									log.logAlert(LoggerChannel.LT_INFORMATION, msg);
								}
							}catch( Throwable e ){

								log.logAlert( "Automatic removal of download '" + download.getName() + "' failed", e );
							}
						}
					}

					@Override
					public void
					positionChanged(
						Download	download,
						int oldPosition,
						int newPosition )
					{
					}
				});

			try{
				download.stop();

			}catch( DownloadException e ){

				log.logAlert( "Automatic removal of download '" + download.getName() + "' failed", e );
			}
		}
	}

	@Override
	public void
	downloadRemoved(
		Download	download )
	{
		monitored_downloads.remove( download );

		DownloadTrackerListener	listener = (DownloadTrackerListener)dm_listener_map.remove(download);

		if ( listener != null ){

			download.removeTrackerListener( listener );
		}
	}

	public void
	destroyInitiated()
	{
		closing	= true;
	}

	public void
	destroyed()
	{
	}
}
