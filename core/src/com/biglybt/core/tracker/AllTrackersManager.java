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

import java.net.URL;
import java.util.List;

import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.tracker.alltrackers.AllTrackersManagerImpl;
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
		public int
		getTrackerCount();
		
		public void
		registerTorrent(
			TOTorrent	torrent );
		
		public void
		registerTracker(
			URL			tracker );
			
		public void
		registerTrackers(
			List<List<URL>>			trackers );
		
		public void
		updateTracker(
			URL							tracker,
			TRTrackerAnnouncerResponse	response );
		
		public void
		updateTracker(
			URL							tracker,
			TRTrackerScraperResponse	response );
	
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
		public final int ET_TRACKER_ADDED	= 0;
		public final int ET_TRACKER_UPDATED	= 1;
		public final int ET_TRACKER_REMOVED	= 2;
		
		public int
		getEventType();
		
		public List<AllTrackersTracker>
		getTrackers();
	}
	
	public interface
	AllTrackersTracker
	{
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
	}
}
