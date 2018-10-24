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

package com.biglybt.core.tracker.alltrackers;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreLifecycleAdapter;
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
import com.biglybt.core.util.FileUtil;
import com.biglybt.core.util.SimpleTimer;
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.TimerEvent;
import com.biglybt.core.util.TimerEventPerformer;
import com.biglybt.util.MapUtils;

public class 
AllTrackersManagerImpl
	implements AllTrackers, TOTorrentListener
{
	final static int	MAX_TRACKERS	= 1024;
	
	final static int 	TICK_PERIOD	= 2500;
	final static int	SAVE_PERIOD	= 5*60*1000;
	final static int	SAVE_TICKS	= SAVE_PERIOD/TICK_PERIOD;
	
	private static final String	CONFIG_FILE 				= "alltrackers.config";

	
	final private static AllTrackersManagerImpl singleton = new AllTrackersManagerImpl();
	
	public static AllTrackers
	getSingleton()
	{
		return( singleton );
	}
	
	private Map<String,AllTrackersTrackerImpl>		host_map = new ConcurrentHashMap<>();
	
	private ConcurrentLinkedDeque<Object[]>			update_queue = new ConcurrentLinkedDeque<>();
	
	private CopyOnWriteList<AllTrackersListener>	listeners = new CopyOnWriteList<>();
	
	private boolean	got_running;
	
	private
	AllTrackersManagerImpl()
	{
		loadConfig();
		
		CoreFactory.getSingleton().addLifecycleListener(
				new CoreLifecycleAdapter()
				{
					@Override
					public void
					stopped(
						Core core )
					{
						saveConfig( true );
					}
				});
		
		SimpleTimer.addPeriodicEvent(
			"AllTrackers",
			TICK_PERIOD,
			new TimerEventPerformer(){
					
				private int	tick_count;
				
				private List<TOTorrent>	pending_torrents = new ArrayList<>();
						
				@Override
				public void 
				perform(
					TimerEvent event )
				{
					tick_count++;
					
					if ( pending_torrents != null && CoreFactory.isCoreRunning()){
					
						for ( TOTorrent torrent: pending_torrents ){
							
							torrent.addListener( AllTrackersManagerImpl.this );
						}
						
						got_running = true;
						
						pending_torrents = null;
					}
					
					Set<AllTrackersTracker>	updates = new HashSet<>();
							
					while( !update_queue.isEmpty()){
						
						Object[] entry = update_queue.remove();
						
						Object	e0 = entry[0];
						
						if ( e0 instanceof TOTorrent ){
						
							TOTorrent torrent = (TOTorrent)e0;
							
							if ( pending_torrents == null ){
								
								torrent.addListener( AllTrackersManagerImpl.this );
								
							}else{
								
								pending_torrents.add( torrent );
							}
							
							continue;
						}
								
						AllTrackersTrackerImpl 		tracker = (AllTrackersTrackerImpl)e0;
						
						if ( host_map.containsKey( tracker.getTrackerName())){

							Object	resp 	= entry[1];
						
							String status;
							
							boolean	updated = false;
							
							if ( resp instanceof TRTrackerAnnouncerResponse ){
						
								TRTrackerAnnouncerResponse a_resp = (TRTrackerAnnouncerResponse)resp;
										
								status = a_resp.getStatusString();
								
								if ( tracker.setOK( a_resp.getStatus() == TRTrackerAnnouncerResponse.ST_ONLINE )){
									
									updated = true;
								}
							}else{
								
									// announce status trumps scrape 
								
								if ( tracker.hasStatus()){
									
									continue;
								}
								
								TRTrackerScraperResponse s_resp = (TRTrackerScraperResponse)resp;							
															
								status = s_resp.getStatusString();
								
								if ( tracker.setOK( s_resp.getStatus() == TRTrackerScraperResponse.ST_ONLINE )){
									
									updated = true;
								}
							}
														
							if ( tracker.setStatusString( status )){
								
								updated = true;		
							}
							
							if ( updated ){
								
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
					
					if ( tick_count % SAVE_PERIOD == 0 ){
						
						saveConfig( false );
					}
				}
			});
	}
	
	private synchronized void
	loadConfig()
	{
		try{
			Map map = FileUtil.readResilientConfigFile( CONFIG_FILE );
			
			List<Map>	trackers = (List<Map>)map.get( "trackers" );
			
			if ( trackers != null ){
				
				for ( Map t: trackers ){
					
					try{
						AllTrackersTrackerImpl tracker = new AllTrackersTrackerImpl( t );
						
						host_map.put( tracker.getTrackerName(), tracker );
						
						if ( host_map.size() > MAX_TRACKERS ){
							
							Debug.out( "Too many trackers - " + trackers.size());
							
							return;
						}
					}catch( Throwable e ){
						
						Debug.out( e );
					}
				}
			}
		}catch( Throwable e ){
			
			Debug.out( e );
		}
	}
	
	private synchronized void
	saveConfig(
		boolean	closing )
	{
		boolean skip_unregistered = closing && got_running;
		
		try{
			Map map = new HashMap();
			
			List<Map>	trackers = new ArrayList<>( host_map.size() + 32 );
			
			map.put( "trackers", trackers ); 
			
			for ( AllTrackersTrackerImpl tracker: host_map.values()){
			
				if ( skip_unregistered && !tracker.isRegistered()){
					
					continue;
				}
				
				try{
					trackers.add( tracker.exportToMap());
					
					if ( trackers.size() > MAX_TRACKERS ){
						
						Debug.out( "Too many trackers - " + trackers.size());
						
						break;
					}
					
				}catch( Throwable e ){
					
					Debug.out( e );
				}
			}
			
			FileUtil.writeResilientConfigFile( CONFIG_FILE, map );
			
		}catch( Throwable e ){
			
			Debug.out( e );
		}
	}
	
	@Override
	public int 
	getTrackerCount()
	{
		return( host_map.size());
	}
	
	@Override
	public void 
	registerTorrent(
		TOTorrent torrent)
	{
		if ( torrent == null ){
			
			return;
		}
					
		registerTorrentSupport( torrent );
		
		update_queue.add( new Object[]{ torrent } );
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
					
					new_tracker.setRegistered();
					
					return( new_tracker );
				}
			}
			
			existing_tracker.setRegistered();
				
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
			
			int scrape_state = response.getStatus();
			
			if ( 	scrape_state == TRTrackerScraperResponse.ST_INITIALIZING ||
					scrape_state == TRTrackerScraperResponse.ST_SCRAPING ){
				
					// ignore
			}else{
				
				update_queue.add( new Object[]{ tracker, response } );
			}
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
		
		private	long		last_good;
		private	long		last_bad;
		private	long		bad_since;
		private	long		consec_fails;
		
		private boolean		registered;
		
		private
		AllTrackersTrackerImpl(
			String		_name )
		{
			name	= _name;
		}
		
		private
		AllTrackersTrackerImpl(
			Map			map )
		
			throws IOException
		{
			name = MapUtils.getMapString( map, "name", null );
			
			if ( name == null ){
				
				throw( new IOException( "Invalid" ));
			}
			
			status = MapUtils.getMapString( map, "status", "" );
			
			last_good = MapUtils.getMapLong( map, "lg", 0 );
			last_bad = MapUtils.getMapLong( map, "lb", 0 );
			bad_since = MapUtils.getMapLong( map, "bs", 0 );
			consec_fails = MapUtils.getMapLong( map, "cf", 0 );
		}
		
		private Map
		exportToMap()
		{
			Map	map = new HashMap();
			
			map.put( "name", name );
			map.put( "status", status );
			map.put( "lg",  last_good );
			map.put( "lb",  last_bad );
			map.put( "bs",  bad_since );
			map.put( "cf",  consec_fails );
			
			return( map );
		}
		
		private void
		setRegistered()
		{
			registered	= true;
		}
		
		private boolean
		isRegistered()
		{
			return( registered );
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
		
		protected boolean
		hasStatus()
		{
			return( !status.isEmpty());
		}
		
		protected boolean
		setStatusString(
			String	str )
		{
			if ( str == null ){
				
				str = "";
			}
			
			if ( str.equals( status )){
				
				return( false );
				
			}else{
				
				status = str;
				
				return( true );
			}
		}
		
		protected boolean
		setOK(
			boolean	is_ok )
		{
			long	now = SystemTime.getCurrentTime();
			
			boolean	was_ok = consec_fails == 0 && last_good > 0 ;
			
			if ( was_ok == is_ok ){
				
					// reduce updates when things don't change
				
				now = (now/(60*1000))*(60*1000);
				
				if ( is_ok ){
					
					if ( last_good == now ){
						
						return( false );
					}
				}else{
										
					if ( last_bad == now ){
					
						consec_fails++;	// keep track of this though

						return( false );
					}
				}
			}
			
			if ( is_ok ){
				
				last_good		= now;
				
				bad_since		= 0;
				
				consec_fails	= 0;
				
			}else{
				
				last_bad = now;
				
				if ( consec_fails == 0 ){
					
					bad_since	= now;
				}
				
				consec_fails++;
			}
			
			return( true );
		}
		
		public long
		getLastGoodTime()
		{
			return( last_good );
		}
		
		public long
		getLastFailTime()
		{
			return( last_bad );
		}
		
		public long
		getFailingSinceTime()
		{
			return( bad_since );
		}
		
		public long
		getConsecutiveFails()
		{
			return( consec_fails );
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
