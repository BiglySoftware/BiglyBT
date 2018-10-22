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
	}
}
