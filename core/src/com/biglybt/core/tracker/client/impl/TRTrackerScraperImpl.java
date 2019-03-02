/*
 * File    : TRTrackerScraperImpl.java
 * Created : 09-Oct-2003
 * By      : parg
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.core.tracker.client.impl;

/**
 * @author parg
 *
 */

import java.net.URL;

import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.tracker.AllTrackersManager;
import com.biglybt.core.tracker.AllTrackersManager.AllTrackers;
import com.biglybt.core.tracker.client.*;
import com.biglybt.core.tracker.client.impl.bt.TRTrackerBTScraperImpl;
import com.biglybt.core.tracker.client.impl.dht.TRTrackerDHTScraperImpl;
import com.biglybt.core.util.*;
import com.biglybt.pif.download.DownloadScrapeResult;

public class
TRTrackerScraperImpl
	implements TRTrackerScraper
{
	private static TRTrackerScraperImpl		singleton;
	private static final AEMonitor 				class_mon 	= new AEMonitor( "TRTrackerScraper" );

	private final TRTrackerBTScraperImpl		bt_scraper;
	private final TRTrackerDHTScraperImpl		dht_scraper;

	private TRTrackerScraperClientResolver		client_resolver;

	// DiskManager listeners

	private static final int LDT_SCRAPE_RECEIVED		= 1;

	private final ListenerManager	listeners 	= ListenerManager.createManager(
			"TrackerScraper:ListenDispatcher",
			new ListenerManagerDispatcher()
			{
				@Override
				public void
				dispatch(
					Object		_listener,
					int			type,
					Object		value )
				{
					TRTrackerScraperListener	listener = (TRTrackerScraperListener)_listener;

					listener.scrapeReceived((TRTrackerScraperResponse)value);
				}
			});

	private static final AllTrackers	all_trackers = AllTrackersManager.getAllTrackers();

	public static TRTrackerScraperImpl
	create()
	{
		try{
			class_mon.enter();

			if ( singleton == null ){

				singleton =  new TRTrackerScraperImpl();
			}

			return( singleton );
		}finally{

			class_mon.exit();
		}
	}

	protected
	TRTrackerScraperImpl()
	{
		bt_scraper 	= TRTrackerBTScraperImpl.create( this );

		dht_scraper	= TRTrackerDHTScraperImpl.create( this );
	}

	@Override
	public TRTrackerScraperResponse
	scrape(
		TOTorrent		torrent )
	{
		return( scrape( torrent, false ));
	}

	@Override
	public TRTrackerScraperResponse
	scrape(
		TOTorrent		torrent,
		URL				target_url )
	{
		return( scrape( torrent, target_url, false ));
	}

	@Override
	public TRTrackerScraperResponse
	scrape(
		TOTorrent		torrent,
		boolean			force )
	{
		return( scrape( torrent, null, force ));
	}

	@Override
	public void
	setScrape(
		TOTorrent				torrent,
		URL						target_url,
		DownloadScrapeResult	result )
	{
		if ( torrent != null ){

			if ( 	( target_url == null && TorrentUtils.isDecentralised( torrent )) ||
					TorrentUtils.isDecentralised( target_url )){

				dht_scraper.setScrape( torrent, target_url, result );

			}else{

				bt_scraper.setScrape( torrent, target_url, result );
			}
		}
	}

	public TRTrackerScraperResponse
	scrape(
		TOTorrent		torrent,
		URL				target_url,
		boolean			force )
	{
		if ( torrent == null ){

			return( null );
		}

		if ( 	( target_url == null && TorrentUtils.isDecentralised( torrent )) ||
				TorrentUtils.isDecentralised( target_url )){

			return( dht_scraper.scrape( torrent, target_url, force ));

		}else{

			return( bt_scraper.scrape( torrent, target_url, force ));
		}
	}

	@Override
	public TRTrackerScraperResponse
	peekScrape(
		TOTorrent		torrent,
		URL				target_url )
	{
		if ( torrent == null ){

			return( null );
		}

		if ( 	( target_url == null && TorrentUtils.isDecentralised( torrent )) ||
				TorrentUtils.isDecentralised( target_url )){

			return( dht_scraper.peekScrape( torrent, target_url ));

		}else{

			return( bt_scraper.peekScrape( torrent, target_url ));
		}
	}

	@Override
	public TRTrackerScraperResponse
	scrape(
		TRTrackerAnnouncer	tracker_client )
	{
		TOTorrent	torrent = tracker_client.getTorrent();

		if ( TorrentUtils.isDecentralised( torrent )){

			return( dht_scraper.scrape( tracker_client ));

		}else{

			return( bt_scraper.scrape( tracker_client ));
		}
	}

	@Override
	public void
	remove(
		TOTorrent		torrent )
	{
		if ( TorrentUtils.isDecentralised( torrent )){

			dht_scraper.remove( torrent );

		}else{

			bt_scraper.remove( torrent );
		}
	}

	public void
	scrapeReceived(
		TRTrackerScraperResponse		response )
	{
		all_trackers.updateTracker( response.getURL(), response );
		
		listeners.dispatch( LDT_SCRAPE_RECEIVED, response );
	}

	@Override
	public void
	setClientResolver(
		TRTrackerScraperClientResolver	resolver )
	{
		client_resolver	= resolver;
	}

	public TRTrackerScraperClientResolver
	getClientResolver()
	{
		return( client_resolver );
	}

	public boolean
	isTorrentScrapable(
		HashWrapper		hash )
	{
		if ( client_resolver == null ){

			return( false );
		}

		return( client_resolver.isScrapable( hash ));
	}

	public boolean
	isNetworkEnabled(
		HashWrapper	hash,
		URL			url )
	{
		if ( client_resolver == null ){

			return( false );
		}

		return( client_resolver.isNetworkEnabled( hash, url ));
	}

	public String[]
	getEnabledNetworks(
		HashWrapper	hash )
	{
		if ( client_resolver == null ){

			return( null );
		}

		return( client_resolver.getEnabledNetworks( hash ));
	}

	public Object[]
	getExtensions(
		HashWrapper	hash )
	{
		if ( client_resolver == null ){

			return( null );
		}

		return( client_resolver.getExtensions( hash ));
	}

	public boolean
	redirectTrackerUrl(
		HashWrapper		hash,
		URL				old_url,
		URL				new_url )
	{
		return( client_resolver.redirectTrackerUrl( hash, old_url, new_url ));
	}

	@Override
	public void
	addListener(
		TRTrackerScraperListener	l )
	{
		listeners.addListener(l);
	}

	@Override
	public void
	removeListener(
		TRTrackerScraperListener	l )
	{
		listeners.removeListener(l);
	}
}
