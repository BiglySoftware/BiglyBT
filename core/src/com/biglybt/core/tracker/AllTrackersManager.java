/* 
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.core.tracker;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Map;

import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.tracker.alltrackers.AllTrackersManagerImpl;
import com.biglybt.core.tracker.client.TRTrackerAnnouncerRequest;
import com.biglybt.core.tracker.client.TRTrackerAnnouncerResponse;
import com.biglybt.core.tracker.client.TRTrackerScraperResponse;

public interface 
AllTrackersManager
{
	public static AllTrackers
	getAllTrackers()
	{
		return( AllTrackersManagerImpl.getSingleton());
	}
	
	public interface
	AllTrackers
	{
		public String
		ingestURL(
			URL		url );
		
		public int
		getTrackerCount();
		
		public void
		registerTorrent(
			TOTorrent	torrent );
		
		public void
		unregisterTorrent(
			TOTorrent	torrent );
		
		public void
		registerTracker(
			TOTorrent	torrent,
			URL			tracker );
			
		public void
		registerTrackers(
			TOTorrent				torrent,
			List<List<URL>>			trackers );
		
		public void
		updateTracker(
			URL							tracker,
			TRTrackerAnnouncerResponse	response );
		
		public void
		updateTracker(
			String						key,
			TRTrackerAnnouncerRequest	request );
	
		public void
		updateTracker(
			URL							tracker,
			TRTrackerScraperResponse	response );
	
		public void
		addActiveRequest(
			TRTrackerAnnouncerRequest	request );
		
		public void
		removeActiveRequest(
			TRTrackerAnnouncerRequest	request );
		
		public boolean
		isStopping();
		
		public void
		addScrapeRequest();
		
		public void
		removeScrapeRequest();
		
		public int
		getActiveRequestCount();
		
		public float
		getAnnouncesPerSecond();
			
		public float
		getScrapesPerSecond();
			
		public AnnounceStats
		getAnnounceStats();
		
		public ScrapeStats
		getScrapeStats();
		
		public AllTrackersTracker
		getTracker(
			String	name  );
		
		public AllTrackersTracker
		getTracker(
			URL		url );
		
		public boolean
		getLoggingEnabled(
			String		short_key );
		
		public void
		setLoggingEnabled(
			String		short_key,
			boolean		enabled );
		
		public File
		getLogFile(
			String		short_key );
		
		public long
		getOptionsMutationCount();
		
		public void
		registerAnnounceStatsProvider(
			AnnounceStatsProvider		provider );
		
		public void
		registerScrapeStatsProvider(
			ScrapeStatsProvider			provider );
		
		public void
		addListener(
			AllTrackersListener		listener,
			boolean					fire_for_existing );
	
		public void
		removeListener(
			AllTrackersListener		listener );
	}
	
	public interface
	AllTrackersListener
	{
		public void
		trackerEventOccurred(
			AllTrackersEvent		event );
	}
	
	public interface
	AllTrackersEvent
	{
		public final int ET_TRACKER_ADDED			= 0;
		public final int ET_TRACKER_UPDATED			= 1;
		public final int ET_TRACKER_REMOVED			= 2;
		
		public int
		getEventType();
		
		public List<AllTrackersTracker>
		getTrackers();
	}
	
	public interface
	AllTrackersTracker
	{
		public static final String OPT_CRYPTO_PORT		= "cp";	// Number 0=default;1=enable;2=disable
		public static final String OPT_LIGHT_SEEDING	= "ls";	// Number 0=default;1=enable;2=disable
		public static final String OPT_SCRAPE_LEVEL		= "sl";	// Number 0=default;1=enable;2=disable
		
			// when adding new options check that their semantics are compatible with use of OPT_ALL
			// (esp if they aren't "Number 0=default;1=enable;2=disable"...)
		
		public static final String[] OPT_ALL = { OPT_CRYPTO_PORT, OPT_LIGHT_SEEDING, OPT_SCRAPE_LEVEL };
		
		public String
		getTrackerName();
		
		public String
		getStatusString();
		
		/**
		 * 
		 * @return 0 = never worked
		 */
		
		public long
		getLastGoodTime();
		
		/**
		 * 
		 * @return 0 = never failed
		 */
		
		public long
		getLastFailTime();
		
		public long
		getFailingSinceTime();
		
		public long
		getConsecutiveFails();
		
		public void
		resetReportedStats();
		
		public long
		getTotalReportedUp();
		
		public long
		getTotalReportedDown();
		
		public Map<String,Object>
		getOptions();
		
		public void
		setOptions(
			Map<String,Object>		options );
		
		public String
		getShortKey();
		
		public long
		getAverageRequestDuration();
		
		public int
		getPrivatePercentage();
		
		public int
		getTorrentCount();
		
		public int
		getActiveRequestCount();
		
		/**
		 * Not persisted - i.e. session total
		 * @return
		 */
		
		public long
		getPeersReceived();
		
		public boolean
		isRemovable();
		
		public void
		remove();
	}
	
	public interface
	AnnounceStatsProvider
	{
		public AnnounceStats
		getStats();
	}
	
	public interface
	AnnounceStats
	{
		public long
		getPublicLagMillis();
		
		public long
		getPrivateLagMillis();
		
		public List<String>
		getPublicActive();
		
		public List<String>
		getPrivateActive();
		
		public int
		getPublicScheduledCount();
		
		public int
		getPrivateScheduledCount();
		
		public int
		getPublicPendingCount();
		
		public int
		getPrivatePendingCount();
	}
	
	public interface
	ScrapeStatsProvider
	{
		public ScrapeStats
		getStats();
	}
	
	public interface
	ScrapeStats
	{
		public long
		getLagMillis();
	}
}
