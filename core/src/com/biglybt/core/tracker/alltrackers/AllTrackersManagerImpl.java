package com.biglybt.core.tracker.alltrackers;

import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentAnnounceURLSet;
import com.biglybt.core.torrent.TOTorrentListener;
import com.biglybt.core.tracker.AllTrackersManager.AllTrackers;
import com.biglybt.core.tracker.AllTrackersManager.AllTrackersEvent;
import com.biglybt.core.tracker.AllTrackersManager.AllTrackersListener;
import com.biglybt.core.tracker.AllTrackersManager.AllTrackersTracker;
import com.biglybt.core.tracker.client.TRTrackerAnnouncerResponse;
import com.biglybt.core.tracker.client.TRTrackerScraperResponse;
import com.biglybt.core.util.CopyOnWriteList;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SimpleTimer;
import com.biglybt.core.util.TimerEvent;
import com.biglybt.core.util.TimerEventPerformer;

public class 
AllTrackersManagerImpl
	implements AllTrackers, TOTorrentListener
{
	final private static AllTrackersManagerImpl singleton = new AllTrackersManagerImpl();
	
	public static AllTrackers
	getSingleton()
	{
		return( singleton );
	}
	
	private Map<String,AllTrackersTrackerImpl>		host_map = new ConcurrentHashMap<>();
	
	private ConcurrentLinkedDeque<Object[]>			update_queue = new ConcurrentLinkedDeque<>();
	
	private CopyOnWriteList<AllTrackersListener>	listeners = new CopyOnWriteList<>();
	
	private
	AllTrackersManagerImpl()
	{
		SimpleTimer.addPeriodicEvent(
			"AllTrackers",
			2500,
			new TimerEventPerformer(){
				
				@Override
				public void 
				perform(
					TimerEvent event )
				{
					Set<AllTrackersTracker>	updates = new HashSet<>();
							
					while( !update_queue.isEmpty()){
						
						Object[] entry = update_queue.remove();
						
						AllTrackersTrackerImpl 		tracker = (AllTrackersTrackerImpl)entry[0];
						
						if ( host_map.containsKey( tracker.getTrackerName())){

							Object	resp 	= entry[1];
						
							String status;
							
							if ( resp instanceof TRTrackerAnnouncerResponse ){
						
								status = ((TRTrackerAnnouncerResponse)resp).getStatusString();
								
							}else{
								
								status = ((TRTrackerScraperResponse)resp).getStatusString();
							}
							
							if ( !tracker.getStatusString().equals( status )){
							
								tracker.setStatusString( status );
							
								updates.add( tracker );
							}
						}
					}
					
					if ( !updates.isEmpty()){
						
						List<AllTrackersTracker> trackers = new ArrayList<>( updates );
						
						for ( AllTrackersListener listener: listeners ){
							
							try{
								listener.trackerEventOccurred(	new AllTrackersEventImpl( AllTrackersEvent.ET_TRACKER_UPDATED, trackers ));
								
							}catch( Throwable e ){
								
								Debug.out( e );
							}
						}
					}
				}
			});
	}
	
	@Override
	public void 
	registerTorrent(
		TOTorrent torrent)
	{
		if ( torrent == null ){
			
			return;
		}
		
		torrent.addListener( this );
			
		registerTorrentSupport( torrent );
	}
	
	private void 
	registerTorrentSupport(
		TOTorrent torrent)
	{
		registerTracker( torrent.getAnnounceURL());
		
		TOTorrentAnnounceURLSet[] sets = torrent.getAnnounceURLGroup().getAnnounceURLSets();
					
		for ( TOTorrentAnnounceURLSet set: sets ){
				
			URL[] urls = set.getAnnounceURLs();
				
			for ( URL url: urls ){
					
				register( url );
			}
		}
	}
	
	public void
	torrentChanged(
		TOTorrent		torrent,
		int				change_type )
	{
		registerTorrentSupport( torrent );
	}
	
	@Override
	public void
	registerTracker(
		URL			url )
	{
		if ( url == null ){
			
			return;
		}
				
		register( url );
	}
	
	@Override
	public void
	registerTrackers(
		List<List<URL>>			trackers )
	{
		for ( List<URL> urls: trackers ){
			
			for ( URL url: urls ){
				
				register( url );
			}
		}
	}
	
	private AllTrackersTrackerImpl
	register(
		URL		url )
	{
		String 	name = url.getHost();
		
		if ( name != null && !name.endsWith( ".dht" )){
			
			int	port = url.getPort();
			
			if ( port == -1 ){
				
				port = url.getDefaultPort();
			}
			
			name = url.getProtocol() + "://" + name + (port>0?(":" + port):"");
			
			AllTrackersTrackerImpl existing_tracker = host_map.get( name );
			
			if ( existing_tracker == null ){ 
				
				AllTrackersTrackerImpl new_tracker = new AllTrackersTrackerImpl( name );
				
				existing_tracker = host_map.putIfAbsent( name, new_tracker );
				
				if ( existing_tracker == null ){
				
					for ( AllTrackersListener listener: listeners ){
						
						List<AllTrackersTracker>	trackers = new ArrayList<>();
						
						trackers.add( new_tracker );
						
						try{
							listener.trackerEventOccurred( new AllTrackersEventImpl( AllTrackersEvent.ET_TRACKER_ADDED, trackers ));

						}catch( Throwable e ){
							
							Debug.out( e );
						}
					}
					
					return( new_tracker );
				}
			}
				
			return( existing_tracker );
			
		}else{
			
			return( null );
		}
	}
	
	@Override
	public void 
	updateTracker(
		URL 							url, 
		TRTrackerAnnouncerResponse	 	response )
	{
		AllTrackersTrackerImpl tracker = register( url );
		
		if ( tracker != null ){
			
			update_queue.add( new Object[]{ tracker, response } );
		}
	}
	
	@Override
	public void 
	updateTracker(
		URL 							url, 
		TRTrackerScraperResponse	 	response )
	{
		AllTrackersTrackerImpl tracker = register( url );
		
		if ( tracker != null ){
			
			update_queue.add( new Object[]{ tracker, response } );
		}
	}
	
	
	@Override
	public void
	addListener(
		AllTrackersListener		listener,
		boolean					fire_for_existing )
	{
		listeners.add( listener );
		
		if ( fire_for_existing ){
		
			List<AllTrackersTracker> existing = (List<AllTrackersTracker>)new ArrayList( host_map.values());
			
			if ( !existing.isEmpty()){
				
				listener.trackerEventOccurred( new AllTrackersEventImpl( AllTrackersEvent.ET_TRACKER_ADDED, existing ));
			}
		}
	}

	@Override
	public void
	removeListener(
		AllTrackersListener		listener )
	{
		listeners.remove( listener );
	}
	
	private class
	AllTrackersTrackerImpl
		implements AllTrackersTracker
	{
		final private String	name;
		
		private String		status = "";
		
		private
		AllTrackersTrackerImpl(
			String		_name )
		{
			name	= _name;
		}
		
		@Override
		public String 
		getTrackerName()
		{
			return( name );
		}
		
		@Override
		public String 
		getStatusString()
		{
			return( status );
		}
		
		protected void
		setStatusString(
			String	str )
		{
			if ( str == null ){
				
				str = "";
			}
			
			status = str;
		}
	}
	
	private static class
	AllTrackersEventImpl
		implements AllTrackersEvent
	{
		final private int						type;
		final private List<AllTrackersTracker>	trackers;
		
		private
		AllTrackersEventImpl(
			int			_type,
			List<AllTrackersTracker>	_trackers )
		{
			type		= _type;
			trackers	= _trackers;
		}
		
		public int
		getEventType()
		{
			return( type );
		}
		
		public List<AllTrackersTracker>
		getTrackers()
		{
			return( trackers );
		}
	}
}
