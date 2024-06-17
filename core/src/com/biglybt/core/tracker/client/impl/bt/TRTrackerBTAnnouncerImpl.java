/*
 * File    : TRTrackerBTAnnouncerImpl.java
 * Created : 5 Oct. 2003
 * By      : Parg
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

package com.biglybt.core.tracker.client.impl.bt;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.dht.netcoords.DHTNetworkPosition;
import com.biglybt.core.dht.netcoords.DHTNetworkPositionManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.networkmanager.NetworkManager;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.networkmanager.impl.udp.UDPNetworkManager;
import com.biglybt.core.peer.PEPeerSource;
import com.biglybt.core.peermanager.utils.PeerClassifier;
import com.biglybt.core.proxy.AEProxyFactory;
import com.biglybt.core.proxy.AEProxyFactory.PluginProxy;
import com.biglybt.core.proxy.impl.AEPluginProxyHandler;
import com.biglybt.core.security.SESecurityManager;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentAnnounceURLSet;
import com.biglybt.core.torrent.TOTorrentException;
import com.biglybt.core.tracker.AllTrackersManager;
import com.biglybt.core.tracker.AllTrackersManager.AllTrackers;
import com.biglybt.core.tracker.AllTrackersManager.AllTrackersTracker;
import com.biglybt.core.tracker.AllTrackersManager.AnnounceStats;
import com.biglybt.core.tracker.TrackerPeerSource;
import com.biglybt.core.tracker.client.*;
import com.biglybt.core.tracker.client.impl.*;
import com.biglybt.core.tracker.protocol.PRHelpers;
import com.biglybt.core.tracker.protocol.udp.*;
import com.biglybt.core.tracker.util.TRTrackerUtils;
import com.biglybt.core.util.*;
import com.biglybt.core.util.Timer;
import com.biglybt.net.udp.uc.*;
import com.biglybt.pif.clientid.ClientIDException;
import com.biglybt.pif.clientid.ClientIDGenerator;
import com.biglybt.pif.download.DownloadAnnounceResult;
import com.biglybt.pif.download.DownloadAnnounceResultPeer;
import com.biglybt.pifimpl.local.clientid.ClientIDManagerImpl;

/**
 *
 * This class handles communication with the tracker
 *
 * @author Olivier
 *
 */
public class
TRTrackerBTAnnouncerImpl
	implements TRTrackerAnnouncerHelper
{
	public final static LogIDs LOGID = LogIDs.TRACKER;

	private static final int OVERRIDE_PERIOD			= 10*1000;

	private static final Timer	tracker_timer_public 	= new Timer( "Tracker Announce Public Timer", COConfigurationManager.getIntParameter( ConfigKeys.Tracker.ICFG_TRACKER_CLIENT_CONCURRENT_ANNOUNCE ));
	private static final Timer	tracker_timer_private	= new Timer( "Tracker Announce Private Timer", COConfigurationManager.getIntParameter( ConfigKeys.Tracker.ICFG_TRACKER_CLIENT_CONCURRENT_ANNOUNCE ));

	private static final AllTrackers	all_trackers = AllTrackersManager.getAllTrackers();

	static void
	analyseTimerEvents()
	{
		analyseTimerEvents( tracker_timer_public );
		analyseTimerEvents( tracker_timer_private );
	}
	
	static void
	analyseTimerEvents(
		Timer	timer )
	{	
		if ( all_trackers.isStopping()){
			
			return;
		}
		
		int total_events = timer.getEventCount();
				
		if ( total_events < 30 || total_events > 5000 ){
			
				// no point in doing anything if there are masses of events
			
			return;
		}
		
			// even the events out a bit to flatten peaks
		
		long	now = SystemTime.getCurrentTime();
		
		final int	secs_to_check		= 15*60;
		final int	bucket_secs			= 60;

		List<TimerEvent>	events = timer.getEvents( now + secs_to_check*1000 );
		
		List[]	buckets = new List[secs_to_check/bucket_secs];
		
		for ( int i=0;i<buckets.length;i++){
			
			buckets[i] = new LinkedList();
		}
		
		for ( TimerEvent ev: events ){
			
			long when = ev.getWhen();
			
			if ( when < now ){
				
				continue;
			}
			
			int slot = (int)((when - now)/(bucket_secs*1000));
			
			if ( slot >= buckets.length ){
				
				break;
			}
			
			buckets[slot].add( ev );
		}
		
		int	max_size = 0;
		
		for ( List l: buckets ){
		
			max_size = Math.max( max_size, l.size());
		}

		if ( max_size < 10 ){
			
			return;
		}
		
		int	limit = max_size/2;
				
		//System.out.println( "Buckets, max=" + max_size + ", limit=" + limit + ", events=" + events.size());
		
		for ( int i=0;i<buckets.length;i++){

			List<TimerEvent> list = buckets[i];
			
			int	num_events = list.size();

			//System.out.println( "    " + i + " - " + num_events );
			
			// ignore first bucket as contains active events

			if ( i == 0 || num_events < limit ){
				
				continue;
			}
			
			long 	start 	= now 	+ i*bucket_secs*1000;					
			long	end 	= start + ((3*bucket_secs)/2)*1000;
					
			TimerEvent	first = list.get(0);
			
			if ( first.getWhen() > start ){
				
				start = first.getWhen();
			}
			
			long	inc = (end-start)/num_events;
						
			if ( inc > 100 ){

				long	new_time = start;

				for ( TimerEvent ev: list ){
					
					long change = new_time - ev.getWhen();
					
					if ( change > 0 ){
						
						// System.out.println( "        Modifying event: offset=" + change );
						
						timer.modifyWhen( ev, new_time );
					}
					
					new_time += inc;
				}
			}
		}
	}
	
	static{
		all_trackers.registerAnnounceStatsProvider(
			new AllTrackersManager.AnnounceStatsProvider(){
				
				private volatile AnnounceStats	last_stats = null;
				private volatile long			last_stats_time;
				
				@Override
				public AnnounceStats 
				getStats()
				{
					if ( last_stats == null || SystemTime.getMonotonousTime() - last_stats_time > 900 ){
						
						last_stats = 
							new AnnounceStats()
							{
								final private long	public_lag 		= tracker_timer_public.getLag();
								final private long	private_lag 	= tracker_timer_private.getLag();
		
								final private int	public_sched	= tracker_timer_public.getEventCount();
								final private int	private_sched	= tracker_timer_private.getEventCount();
								
								final private int	public_pending	= tracker_timer_public.getEventCount( SystemTime.getCurrentTime());
								final private int	private_pending	= tracker_timer_private.getEventCount( SystemTime.getCurrentTime() );
								
								final List<String>	public_active;
								final List<String>	private_active;
								
								{
									List<TimerEvent> events = tracker_timer_public.getActiveEvents();
									
									long mono_now = SystemTime.getMonotonousTime();
									
									public_active = new ArrayList<String>( events.size());
									
									for ( TimerEvent e: events ){
																				
										public_active.add( e.getName() + " (" + (mono_now - e.getExecutionStartMonoTime()) + " " + MessageText.getString( "ConfigView.section.stats.millis.short" ) + ")");									}
									
									events = tracker_timer_private.getActiveEvents();
									
									private_active = new ArrayList<String>( events.size());
									
									for ( TimerEvent e: events ){
										
										private_active.add( e.getName() + " (" + (mono_now - e.getExecutionStartMonoTime()) + " " + MessageText.getString( "ConfigView.section.stats.millis.short" ) + ")");						
									}
								}
								
								public long
								getPublicLagMillis()
								{
									return( public_lag );
								}
								
								public long
								getPrivateLagMillis()
								{
									return( private_lag );
								}
								
								@Override
								public List<String> 
								getPublicActive()
								{
									return( public_active );
								}
								
								@Override
								public List<String> 
								getPrivateActive()
								{	
									return( private_active );
								}
								
								public int
								getPublicScheduledCount()
								{
									return( public_sched );
								}
								
								public int
								getPrivateScheduledCount()
								{
									return( private_sched );
								}
								
								public int
								getPublicPendingCount()
								{
									return( public_pending );
								}
								
								public int
								getPrivatePendingCount()
								{
									return( private_pending );
								}
							};
		
						 last_stats_time = SystemTime.getMonotonousTime();
					}
					
					return( last_stats );
				}
			});
		
		SimpleTimer.addEvent( "TA Timer Checker", SystemTime.getOffsetTime( 60*1000 ), (e)->
		{
			analyseTimerEvents();
			
			SimpleTimer.addPeriodicEvent(
				"TA Timer Checker",
				2*60*1000,
				(ev)->{
					analyseTimerEvents();
				});
		});
	}
	
	static{
		COConfigurationManager.addParameterListener(
			ConfigKeys.Tracker.ICFG_TRACKER_CLIENT_CONCURRENT_ANNOUNCE,
			(name)->{
				tracker_timer_public.getThreadPool().setMaxThreads( COConfigurationManager.getIntParameter( name ));
				tracker_timer_private.getThreadPool().setMaxThreads( COConfigurationManager.getIntParameter( name ));
			});
	}
	
	public static final String 	UDP_REALM = "UDP Tracker";

	private static int userMinInterval = 0;
	private static int userMaxNumwant = 100;
	private static boolean tcpAnnounceEnabled;
	private static boolean udpAnnounceEnabled;
	private static boolean udpProbeEnabled;

    static{
	  	PRUDPTrackerCodecs.registerCodecs();
	  	COConfigurationManager.addAndFireParameterListeners(
	  		new String[] {
	  			"Tracker Client Min Announce Interval",
	  			"Tracker Client Numwant Limit",
	  			"Tracker Client Enable TCP",
	  			"Server Enable UDP",
	  			"Tracker UDP Probe Enable"
	  			},
	  		new ParameterListener()
	  		{
	  			@Override
				  public void parameterChanged(final String parameterName)
	  			{
	  				userMinInterval = COConfigurationManager.getIntParameter("Tracker Client Min Announce Interval");
	  				userMaxNumwant = COConfigurationManager.getIntParameter("Tracker Client Numwant Limit");
					tcpAnnounceEnabled = COConfigurationManager.getBooleanParameter("Tracker Client Enable TCP");
	  				udpAnnounceEnabled = COConfigurationManager.getBooleanParameter("Server Enable UDP");
					udpProbeEnabled  = COConfigurationManager.getBooleanParameter("Tracker UDP Probe Enable");
	  			}
	  		});
    }

	private static final AEMonitor 	class_mon 			= new AEMonitor( "TRTrackerBTAnnouncer:class" );
	private static final Map			tracker_report_map	= new HashMap();

	private final Timer						tracker_timer;
	private final TOTorrent					torrent;
	
	private final TOTorrentAnnounceURLSet[]	announce_urls;

	private TRTrackerAnnouncerImpl.Helper	helper;

	private TimerEvent				current_timer_event;
	private TimerEventPerformer		timer_event_action;

	protected int				tracker_state 			= TRTrackerAnnouncer.TS_INITIALISED;
	private String				tracker_status_str		= "";
	private TRTrackerAnnouncerResponseImpl	last_response			= null;
	private long				last_update_time_secs;
	private long				current_time_to_wait_secs;
	private final boolean		manual_control;

	private long		tracker_interval;
	private long		tracker_min_interval;


	private long 		min_interval = 0;

	private int  failure_added_time = 0;
    private long failure_time_last_updated = 0;

	private boolean			stopped;
	private boolean			stopped_for_queue;
	private boolean			completed;
	private boolean			complete_reported	= false;

	private boolean			update_in_progress	= false;

	private long			rd_last_override = 0;
	private int				rd_override_percentage	= 100;

	private long			min_interval_override	= 0;

  	private List<List<URL>> trackerUrlLists;

  	private URL lastUsedUrl;
  	private URL	lastAZTrackerCheckedURL;

  	final private HashWrapper			torrent_hash_actual;
  	final private HashWrapper			torrent_hash_target;

	private String	last_tracker_message;		// per torrent memory

	private String info_hash = "info_hash=";
	private byte[] tracker_peer_id;
	private String tracker_peer_id_str = "&peer_id=";

	private byte[] data_peer_id;


	private int announceCount;
	private int announceFailCount;

	private byte autoUDPprobeEvery = 1;
	private int autoUDPProbeSuccessCount;


	private String tracker_id = "";



	private String 		ip_override;
	private final String[]	peer_networks;

	private TRTrackerAnnouncerDataProvider 	announce_data_provider;

	protected final AEMonitor this_mon 	= new AEMonitor( "TRTrackerBTAnnouncer" );

	private boolean	az_tracker;
	private boolean	enable_sni_hack;
	private boolean internal_error_hack;
	private boolean	dh_hack;

	private boolean	destroyed;



  public
  TRTrackerBTAnnouncerImpl(
	HashWrapper						_torrent_hash_override,
   	TOTorrent						_torrent,
   	TOTorrentAnnounceURLSet[]		_announce_urls,
	String[]						_peer_networks,
	boolean							_manual,
	TRTrackerAnnouncerImpl.Helper	_helper )

  	throws TRTrackerAnnouncerException
  {
	torrent				= _torrent;
	announce_urls		= _announce_urls;
  	peer_networks		= _peer_networks;
  	manual_control		= _manual;
  	helper				= _helper;

  	tracker_timer = torrent.getPrivate()?tracker_timer_private:tracker_timer_public;
  	
	try {
		torrent_hash_actual = _torrent.getHashWrapper();

	}catch( TOTorrentException e ){

		Logger.log(new LogEvent(torrent, LOGID, "Torrent hash retrieval fails", e));

		throw( new TRTrackerAnnouncerException( "TRTrackerAnnouncer: Torrent hash retrieval fails"));
	}

	torrent_hash_target	= _torrent_hash_override==null?torrent_hash_actual:_torrent_hash_override;

		//Get the Tracker url

	constructTrackerUrlLists( true );

		//Create our unique peerId

	try{
		data_peer_id = helper.getPeerID();

	    if ( COConfigurationManager.getBooleanParameter("Tracker Separate Peer IDs")){

	    	tracker_peer_id = ClientIDManagerImpl.getSingleton().generatePeerID( torrent_hash_target.getBytes(), true );

	    }else{

	    	tracker_peer_id	= data_peer_id;
	    }
	}catch( ClientIDException e ){

		 throw( new TRTrackerAnnouncerException( "TRTrackerAnnouncer: Peer ID generation fails", e ));
	}

	try {

			info_hash += URLEncoder.encode(
					new String(torrent_hash_target.getBytes(), Constants.BYTE_ENCODING_CHARSET),
					Constants.BYTE_ENCODING_CHARSET.name()).replaceAll("\\+", "%20");

			tracker_peer_id_str += URLEncoder.encode(
					new String(tracker_peer_id, Constants.BYTE_ENCODING_CHARSET),
					Constants.BYTE_ENCODING_CHARSET.name()).replaceAll("\\+", "%20");

	}catch (UnsupportedEncodingException e){

		Logger.log(new LogEvent(torrent, LOGID, "URL encode fails", e));

	  throw( new TRTrackerAnnouncerException( "TRTrackerAnnouncer: URL encode fails"));
	}

	timer_event_action =
		new TimerEventPerformer()
		{
			@Override
			public void
			perform(
				TimerEvent	this_event )
			{
				if ( manual_control ){

					requestUpdateSupport( this_event );

					return;
				}
				
				long	secs_to_wait = getErrorRetryInterval();

				try{

					secs_to_wait = requestUpdateSupport( this_event );

					if ( tracker_state != TRTrackerAnnouncer.TS_STOPPED ){
						
						if (Logger.isEnabled())
							Logger.log(new LogEvent(torrent, LOGID,
									"Next tracker announce (unadjusted) will be in " + secs_to_wait
											+ "s"));
					}
				}finally{

					current_time_to_wait_secs	= secs_to_wait;

					if ( tracker_state == TRTrackerAnnouncer.TS_STOPPED ){

						// System.out.println( "\tperform: stopped so no more events");

					}else{


						try{
							this_mon.enter();

								// it is possible that the current event was being processed
								// when another thread cancelled it and created a further timer
								// event. if this is the case we don't want to go ahead and
								// create another timer as one already exists

							if ( this_event.isCancelled()){

								// System.out.println( "\tperform: cancelled so no new event");

							}else{


								secs_to_wait = getAdjustedSecsToWait();

								if (Logger.isEnabled())
									Logger.log(new LogEvent(torrent, LOGID,
											"Next tracker announce (adjusted) will be in "
													+ secs_to_wait + "s"));

								long target_time = SystemTime.getCurrentTime() + (secs_to_wait*1000);

								if ( current_timer_event != null && !current_timer_event.isCancelled()){

									if ( 	current_timer_event != this_event &&
											current_timer_event.getWhen() < target_time ){

											// existing event is earlier then current target, use it

										return;
									}

									current_timer_event.cancel();
								}

								if ( !destroyed ){

									current_timer_event =
										tracker_timer.addEvent( target_time, this );
								}
							}
						}finally{

							this_mon.exit();
						}
					}
				}
			}
		};

		if (Logger.isEnabled())
			Logger.log(new LogEvent(torrent, LOGID,
					"Tracker Announcer Created using url : " + trackerURLListToString()));
  }

	public void
	cloneFrom(
		TRTrackerBTAnnouncerImpl	other )
	{
		helper					= other.helper;
		data_peer_id			= other.data_peer_id;
		tracker_peer_id			= other.tracker_peer_id;
		tracker_peer_id_str		= other.tracker_peer_id_str;
		tracker_id				= other.tracker_id;

		announce_data_provider	= other.announce_data_provider;
	}

	protected long
	getAdjustedSecsToWait()
	{

		long	secs_to_wait = current_time_to_wait_secs;

		if ( last_response != null && last_response.getStatus() != TRTrackerAnnouncerResponse.ST_ONLINE ){

			if ( last_response.getStatus() == TRTrackerAnnouncerResponse.ST_REPORTED_ERROR ){

					//the tracker has explicitly reported an error (torrent is unauthorized for example),
					//so there's no need to keep trying to re-announce as if it were actually offline
	
					//there's no "min interval" returned, so start the re-announce backoff timings at 15min
				
				if ( failure_added_time < 900 ){
					
					failure_added_time = 900;
				}
				
				secs_to_wait = getErrorRetryInterval();

				if (Logger.isEnabled()) {
					Logger.log(new LogEvent(torrent, LOGID,
							"MIN INTERVAL CALC: tracker reported error, " +
							"adjusting to error retry interval: " + secs_to_wait ));
				}
			}else{
					//tracker is OFFLINE
				
				secs_to_wait = getErrorRetryInterval();
				
				if (Logger.isEnabled()) {
					Logger.log(new LogEvent(torrent, LOGID,
							"MIN INTERVAL CALC: tracker seems to be offline, " +
							"adjusting to error retry interval: " + secs_to_wait ));
				}
			}
		}else{

			if ( rd_override_percentage == 0 ){
				
				secs_to_wait = TRTrackerAnnouncer.REFRESH_MINIMUM_SECS;

				if (Logger.isEnabled())
					Logger.log(new LogEvent(torrent, LOGID,
							"MIN INTERVAL CALC: override, perc = 0: " + secs_to_wait ));
								
			}else{

				if ( rd_override_percentage != 100 ){
					
					secs_to_wait = (secs_to_wait * rd_override_percentage) /100;
					
					if (Logger.isEnabled()){
						Logger.log(new LogEvent(torrent, LOGID,
								"MIN INTERVAL CALC: override, perc = " + rd_override_percentage + ": " + secs_to_wait ));
					}
				}
	
				if ( secs_to_wait < TRTrackerAnnouncer.REFRESH_MINIMUM_SECS ){
	
					secs_to_wait = TRTrackerAnnouncer.REFRESH_MINIMUM_SECS;
				}
	
					//use 'min interval' for calculation
				
				if ( min_interval != 0 && secs_to_wait < min_interval ){
					
					float percentage = (float)min_interval / secs_to_wait;  //percentage of original interval
	
					//long orig_override = secs_to_wait;
	
					int added_secs = (int)(((min_interval - secs_to_wait) * percentage)/100);  //increase by x percentage of difference
	
					secs_to_wait += added_secs;
	
					if ( secs_to_wait > tracker_interval ){
	
						secs_to_wait = tracker_interval;
					}
	
					if (Logger.isEnabled())
						Logger.log(new LogEvent(torrent, LOGID,
								"MIN INTERVAL CALC: min_interval=" + min_interval + ", interval="
										+ current_time_to_wait_secs + ", orig=" +  current_time_to_wait_secs
										+ ", new=" + secs_to_wait + ", added=" + added_secs
										+ ", perc=" + percentage));
				}
			}

		}

		return( secs_to_wait );
	}


	@Override
	public int
  	getStatus()
  	{
  		return( tracker_state );
  	}

  	@Override
	  public String
  	getStatusString()
  	{
  		return( tracker_status_str );
  	}

	@Override
	public TRTrackerAnnouncer
	getBestAnnouncer()
	{
		return( this );
	}

	@Override
	public void
	setRefreshDelayOverrides(
		int		percentage )
	{
		if ( percentage > 100 ){

			percentage = 100;

		}else if ( percentage < 50 ){

			percentage	= 50;
		}

		long	now = SystemTime.getCurrentTime();
		//only start overriding once the tracker announce update has been called
		boolean override_allowed = rd_last_override > 0 && now - rd_last_override > OVERRIDE_PERIOD;

		if( now < rd_last_override )  override_allowed = true;  //time went backwards

		if ( override_allowed && rd_override_percentage != percentage ){

			try{
				this_mon.enter();

				rd_last_override	= now;

				rd_override_percentage	= percentage;

				if ( current_timer_event != null && !current_timer_event.isCancelled()){

					long	expiry	= current_timer_event.getWhen();

					if ( expiry < now + 10*1000 ){
						
						// event is about to run or should have already run. Let it complete
						
					}else{
						
						long	start 	= current_timer_event.getCreatedTime();
	
						long	secs_to_wait = getAdjustedSecsToWait();
	
						long target_time = start + (secs_to_wait*1000);
	
						if ( target_time < now ){
							
							target_time = now;
						}
						
						if ( target_time != expiry ){
	
							current_timer_event.cancel();
	
							if ( !destroyed ){
	
								if (Logger.isEnabled())
									Logger.log(new LogEvent(torrent, LOGID,
											"Changed next tracker announce to " + secs_to_wait
													+ "s via " + Debug.getStackTrace(true, false, 0, 3)));
	
								current_timer_event =
									tracker_timer.addEvent(
										start,
										target_time,
										timer_event_action );
							}
						}
						
					}
				}
			}finally{

				this_mon.exit();
			}
		}
	}

	@Override
	public boolean
	isUpdating()
	{
		return( update_in_progress );
	}

	@Override
	public long
	getInterval()
	{
		return( tracker_interval );
	}

	@Override
	public long
	getMinInterval()
	{
		return( tracker_min_interval );
	}

	@Override
	public int
	getTimeUntilNextUpdate()
	{
		try{
			this_mon.enter();

			if ( current_timer_event == null ){

				return( getErrorRetryInterval() );
			}

			int rem = (int)((current_timer_event.getWhen() - SystemTime.getCurrentTime())/1000);

			return( rem );

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public int
	getLastUpdateTime()
	{
		return( (int)last_update_time_secs );
	}

	@Override
	public void
	update(
		boolean		force )
	{
		long now = SystemTime.getCurrentTime() / 1000;

        if ( now < last_update_time_secs )  force = true;  //time went backwards

        long	effective_min = min_interval_override>0?min_interval_override:TRTrackerAnnouncer.REFRESH_MINIMUM_SECS;

		if ( manual_control || force || ( now - last_update_time_secs >= effective_min )){

		  requestUpdate();
		}
	}


	@Override
	public void
	complete(
		boolean	already_reported )
	{
		complete_reported	= (complete_reported || already_reported );

		completed			= true;

		requestUpdate();
	}

	@Override
	public void
	stop(
		boolean	for_queue )
	{
		stopped				= true;
        stopped_for_queue	= for_queue;

		requestUpdate();
	}

	protected void
	requestUpdate()
	{
		try{
			this_mon.enter();

			if ( current_timer_event != null ){

				long now = SystemTime.getCurrentTime();
				
				if ( current_timer_event.getWhen() <= now ){
				
						// event is about to run or should have already run. Let it complete

					return;
				}
				
				current_timer_event.cancel();
			}

			rd_last_override = SystemTime.getCurrentTime();  //"pause" overrides for 10s

			if ( !destroyed ){

				if (Logger.isEnabled())
					Logger.log(new LogEvent(torrent, LOGID,
							"Forcing tracker announce now via "
									+ Debug.getStackTrace(true, false, 0, 3)));

				current_timer_event =
					tracker_timer.addEvent(
						SystemTime.getCurrentTime(),
						timer_event_action );
			}
		}finally{

			this_mon.exit();
		}
	}

	protected long
	requestUpdateSupport(
		TimerEvent		timer_event )
	{

		boolean	clear_progress = true;

		try{
			try{
				this_mon.enter();

					// can't continue if the data provider hasn't been set yet...

				if ( update_in_progress || announce_data_provider == null ){

					clear_progress = false;

					return( getErrorRetryInterval() );
				}

				update_in_progress = true;

			}finally{

				this_mon.exit();
			}

			last_update_time_secs	= SystemTime.getCurrentTime()/1000;

			tracker_status_str = MessageText.getString("PeerManager.status.checking") + "..."; //$NON-NLS-1$ //$NON-NLS-2$

			TRTrackerAnnouncerResponseImpl	response = null;

			if ( stopped ){

					// if manual control then we assume that a stop request is required, even if we
					// are in an init state. needed for explicit stop based on URL

				if ( tracker_state == TRTrackerAnnouncer.TS_INITIALISED && !manual_control ){

						// never started

					tracker_state = TRTrackerAnnouncer.TS_STOPPED;

				}else if ( tracker_state != TRTrackerAnnouncer.TS_STOPPED ){

					response = stopSupport( timer_event );

					if ( response.getStatus() == TRTrackerAnnouncerResponse.ST_ONLINE ){

						tracker_state = TRTrackerAnnouncer.TS_STOPPED;

					}else{

							// just have one go at sending a stop event as we don't want to sit here
							// forever trying to send stop to a stuffed tracker

						tracker_state = TRTrackerAnnouncer.TS_STOPPED;
					}
				}
			}else if ( tracker_state == TRTrackerAnnouncer.TS_INITIALISED ){

					// always go through the "start" phase, even if we're already complete
					// as some trackers insist on the initial "start"

				response = startSupport( timer_event );

				if ( response.getStatus() == TRTrackerAnnouncerResponse.ST_ONLINE ){

					tracker_state = TRTrackerAnnouncer.TS_DOWNLOADING;
				}
			}else if ( completed ){

				if ( !complete_reported ){

					response = completeSupport( timer_event );

						// treat the "complete" as processed if the tracker replies either OK or an explicit
						// error. In particular, if the tracker has returned an error to control seed limits
						// we don't want to treat this as an error and report completed again next time around
						// as this causes the tracker to double count stats

					if ( response.getStatus() != TRTrackerAnnouncerResponse.ST_OFFLINE ){

						complete_reported	= true;

						tracker_state = TRTrackerAnnouncer.TS_COMPLETED;
					}
				}else{
					tracker_state = TRTrackerAnnouncer.TS_COMPLETED;

					response = updateSupport( timer_event );
				}

			}else{

				response = updateSupport( timer_event );
			}

			if ( response != null ){

				int	rs = response.getStatus();

				if ( rs == TRTrackerAnnouncerResponse.ST_OFFLINE ){

					tracker_status_str = MessageText.getString("PeerManager.status.offline");

				}else if ( rs == TRTrackerAnnouncerResponse.ST_REPORTED_ERROR ){

					tracker_status_str = MessageText.getString("PeerManager.status.error");

						// move state back to initialised to next time around a "started"
						// event it resent. Required for trackers like 123torrents.com that
						// will fail peers that don't start with a "started" event after a
						// tracker restart

					tracker_state	= TRTrackerAnnouncer.TS_INITIALISED;

				}else{

					if ( announce_data_provider.isPeerSourceEnabled( PEPeerSource.PS_BT_TRACKER )){

						tracker_status_str = MessageText.getString("PeerManager.status.ok");

						if ( response.wasProbe()){

							tracker_status_str += " (" + MessageText.getString( "label.udp_probe" ) + ")";
						}
					}else{

						tracker_status_str = MessageText.getString("PeerManager.status.ps_disabled");

						response.setPeers(new TRTrackerAnnouncerResponsePeerImpl[0]);
					}
				}

				String	reason = response.getAdditionalInfo();

				if ( reason != null ){
					tracker_status_str += " (" + reason + ")";
				}

				last_response = response;

				TRTrackerAnnouncerRequest request = response.getRequest();
				
				if ( request == null ){
					
					request = new TRTrackerAnnouncerRequestImpl();
				}
				
				helper.informResponse( this, request, response );

				return( response.getTimeToWait());
			}

			tracker_status_str = "";

			return( getErrorRetryInterval() );

		}catch( Throwable e ){

			Debug.printStackTrace( e );

			return( getErrorRetryInterval() );

		}finally{

			try{
				this_mon.enter();

				if ( clear_progress ){

					update_in_progress = false;
				}
			}finally{

				this_mon.exit();
			}
		}
	}

	protected TRTrackerAnnouncerResponseImpl startSupport( TimerEvent timer_event ) {
		if (Logger.isEnabled())
			Logger.log(new LogEvent(torrent, LOGID, "Tracker Announcer is sending "
					+ "a start Request"));

    return (update("started", timer_event ));
  }

  protected TRTrackerAnnouncerResponseImpl completeSupport( TimerEvent timer_event ) {
  	if (Logger.isEnabled())
			Logger.log(new LogEvent(torrent, LOGID, "Tracker Announcer is sending "
					+ "a completed Request"));

		return (update("completed", timer_event));
  }

  protected TRTrackerAnnouncerResponseImpl stopSupport( TimerEvent timer_event ) {
  	if (Logger.isEnabled())
			Logger.log(new LogEvent(torrent, LOGID, "Tracker Announcer is sending "
					+ "a stopped Request"));

    return (update("stopped", timer_event));
  }

  protected TRTrackerAnnouncerResponseImpl updateSupport( TimerEvent timer_event ) {
  	if (Logger.isEnabled())
			Logger.log(new LogEvent(torrent, LOGID, "Tracker Announcer is sending "
					+ "an update Request"));

    return update("", timer_event);
  }

  	private TRTrackerAnnouncerResponseImpl
	update(
		String 		evt,
		TimerEvent	timer_event )
  	{
  		// this method filters out any responses incompatible with the network selection

  		TRTrackerAnnouncerResponseImpl	resp = update2( evt, timer_event );

  		TRTrackerAnnouncerResponsePeer[]	peers = resp.getPeers();

  		if ( peers != null ){
	  		List	p = new ArrayList();

	  		for (int i=0;i<peers.length;i++){

	  			TRTrackerAnnouncerResponsePeer	peer = peers[i];

	  			if ( peer_networks == null ){

	  				p.add( peer );

	  			}else{

		  			String	peer_address = peer.getAddress();

		  			String	peer_network = AENetworkClassifier.categoriseAddress( peer_address );

		  			boolean	added = false;

		  			for (int j=0;j<peer_networks.length;j++){

		  				if ( peer_networks[j] == peer_network ){

		  					p.add( peer );

		  					added = true;

		  					break;
		  				}
		  			}

		  			if (!added && Logger.isEnabled())
							Logger.log(new LogEvent(torrent, LOGID, LogEvent.LT_WARNING,
									"Tracker Announcer dropped peer '" + peer_address
											+ "' as incompatible " + "with network selection"));
	  			}
	  		}

	  		peers = new TRTrackerAnnouncerResponsePeer[ p.size()];

	  		p.toArray( peers );

	  		resp.setPeers( peers );
  		}

  		return( resp );
  	}

    private TRTrackerAnnouncerResponseImpl
    update2(String evt, TimerEvent timer_event )
    {
    	TRTrackerAnnouncerResponseImpl resp = update2Support( evt, timer_event );
    
    	URL url = resp.getURL();
    	
    	if ( url != null ){
    		
    		all_trackers.updateTracker( url, resp );
    	}
    	
    	return( resp );
    }
    
  private TRTrackerAnnouncerResponseImpl
  update2Support(String evt, TimerEvent event )
  {
  	TRTrackerAnnouncerResponseImpl	last_failure_resp = null;

  	String	skip_host = null;

  outer:

	for (int i = 0 ; i < trackerUrlLists.size() ; i++) {

		List<URL> urls = trackerUrlLists.get(i);

		for (int j = 0 ; j < urls.size() ; j++) {

		  final URL original_url = (URL)urls.get(j);

		  if ( skip_host != null && skip_host.equals( original_url.getHost())){

			if (Logger.isEnabled())
					Logger.log(
						new LogEvent(
							torrent,
							LOGID,
							LogEvent.LT_WARNING,
							"Tracker Announcer is ignoring '" + original_url + "' as already received overloaded response from this host" ));

			continue;
		  }

		  lastUsedUrl = original_url;

		  if ( lastUsedUrl != lastAZTrackerCheckedURL ){

			  az_tracker = TRTrackerUtils.isAZTracker( lastUsedUrl  );
		  }

		  URL	request_url = null;

		  if ( last_failure_resp != null ){

			  	// report this now as it is about to be lost

			  helper.informResponse( this, new TRTrackerAnnouncerRequestImpl(), last_failure_resp );
		  }

		  TRTrackerAnnouncerRequestImpl request_obj = null;
		  
		  try{

			request_obj =  constructRequest( evt, original_url, event );
			
			all_trackers.addActiveRequest( request_obj );
			
			long start = SystemTime.getMonotonousTime();

			try{				
			  	request_url = request_obj.getURL();
	
			  	URL[]	tracker_url = { original_url };
	
			  	int	prev_udp_probes_ok = autoUDPProbeSuccessCount;
	
			  	byte[]	result_bytes = updateOld( tracker_url, request_url);
	
			  	lastUsedUrl = tracker_url[0];	// url may have redirected, use this value as it will be correct
	
				TRTrackerAnnouncerResponseImpl resp = decodeTrackerResponse( lastUsedUrl, result_bytes );
	
				resp.setRequest( request_obj );	// easiest way to transport the request around internally is to tie it to the response
				
				int	resp_status = resp.getStatus();
	
			    if ( resp_status == TRTrackerAnnouncerResponse.ST_ONLINE ){
	
			    	if ( autoUDPProbeSuccessCount > prev_udp_probes_ok ){
	
			    		resp.setWasProbe();
			    	}
			    	try{
			    			// tracker looks ok, make any redirection permanent
	
			    		if ( !original_url.toString().equals(lastUsedUrl.toString())){
	
			    			if (Logger.isEnabled())
								Logger.log(new LogEvent(torrent, LOGID,
										"announce url permanently redirected: old = " + original_url + ", new = " + lastUsedUrl ));
	
	
							TorrentUtils.replaceAnnounceURL( torrent, original_url, lastUsedUrl );
	
						}
			    	}catch( Throwable e ){
	
			    		Debug.printStackTrace(e);
			    	}
	
		            urls.remove(j);
	
		            urls.add(0, lastUsedUrl );
	
		            trackerUrlLists.remove(i);
	
		            trackerUrlLists.add(0,urls);
	
		            informURLChange( original_url, lastUsedUrl, false );
	
		            	//and return the result
	
		            return( resp );
	
				 }else  if ( resp_status == TRTrackerAnnouncerResponse.ST_REPORTED_ERROR ){
	
				 	last_failure_resp = resp;
	
				 	String	reason = resp.getAdditionalInfo();
	
				 		// avoid re-hitting a host with multiple ports if reporting overloaded. This is
				 		// particularly "interesting" when reporting the "completed" event and we get a
				 		// "overloaded" response - when we hit another port we record the event twice
				 		// as the tracker has discarded this peer and therefore doesn't know to ignore the
				 		// second "completed" event...
	
				 	if ( reason != null &&
				 			(reason.contains("too many seeds") ||
								reason.contains("too many peers"))){
	
				 		skip_host	= original_url.getHost();
				 	}
	
				 }else{
	
					 announceFailCount++;
	
					 last_failure_resp = resp;
				 }
			}finally{
				
				request_obj.setElapsed( SystemTime.getMonotonousTime() - start );
				
				all_trackers.removeActiveRequest( request_obj );
			}

		  }catch( MalformedURLException e ){

			announceFailCount++;

		  	Debug.printStackTrace( e );

		  	last_failure_resp =
		  		new TRTrackerAnnouncerResponseImpl(
		  				original_url,
		  				torrent_hash_actual,
		  				TRTrackerAnnouncerResponse.ST_OFFLINE,
						getErrorRetryInterval(),
						"malformed URL '" + (request_url==null?"<null>":request_url.toString()) + "'" );

		  	last_failure_resp.setRequest( request_obj );
		  	
		  }catch( Throwable e ){

			announceFailCount++;

		  	last_failure_resp =
		  		new TRTrackerAnnouncerResponseImpl(
		  				original_url,
		  				torrent_hash_actual,
		  				TRTrackerAnnouncerResponse.ST_OFFLINE,
						getErrorRetryInterval(),
						e.getMessage()==null?e.toString():e.getMessage());
		  	
		  	last_failure_resp.setRequest( request_obj );
		  }

	  	  if ( destroyed ){

	  		  // we've had a go at one url, give up
	  		  
	  		break outer;
	  	  }
		}
	  }

		// things no good here

		if ( last_failure_resp == null ){

		  	last_failure_resp =
		  		new TRTrackerAnnouncerResponseImpl(
		  				null,
		  				torrent_hash_actual,
		  				TRTrackerAnnouncerResponse.ST_OFFLINE,
						getErrorRetryInterval(),
						"Reason Unknown" );

		}

		// use 4* the num_want as no doubt a fair few connections will fail and
		// we want to get a decent reconnect rate

	  int	num_want = calculateNumWant() * 4;


      TRTrackerAnnouncerResponsePeer[]	cached_peers = helper.getPeersFromCache(num_want);

      if ( cached_peers.length > 0 ){

      	// System.out.println( "cached peers used:" + cached_peers.length );

      	last_failure_resp.setPeers( cached_peers );
      }

      return( last_failure_resp );
  }

 	private byte[]
 	updateOld(
 		URL[]		tracker_url,
 		URL 		reqUrl )

  		throws Exception
	{
   			// set context in case authentication dialog is required

 		boolean errorLevel = true;

 		try{
	    	TorrentUtils.setTLSTorrentHash( torrent_hash_actual );

	 			// loop to possibly retry update on SSL certificate install

	 		for (int i=0;i<2;i++){

		  		String	failure_reason = null;

				String	protocol = reqUrl.getProtocol();

				try{

					if (Logger.isEnabled()){
						Logger.log(new LogEvent(torrent, LOGID,
								"Tracker Announcer is Requesting: " + reqUrl));
					}

			  		ByteArrayOutputStream message = new ByteArrayOutputStream();


			  		URL udpAnnounceURL = null;

			  		boolean	udp_probe = false;

			  			// if we have multiple tracker URLs then do something sensible about

			  		if ( protocol.equalsIgnoreCase("udp")){

			  			if ( udpAnnounceEnabled ){

			  				udpAnnounceURL = reqUrl;

			  			}else{

			  				throw( new IOException( "UDP Tracker protocol disabled" ));
			  			}

					}else if (	protocol.equalsIgnoreCase("http") &&
								( !az_tracker )	&&
								( !TorrentUtils.isReallyPrivate( torrent )) &&
								announceCount % autoUDPprobeEvery == 0 &&
								udpProbeEnabled && udpAnnounceEnabled ){

							// if we don't know this tracker supports UDP then don't probe on
							// first announce as we don't want a large delay on torrent startup
							// also if we are stopping we don't want to initiate a probe as
							// we want the stop instruction to get to tracker if possible

						if (	( 	stopped ||
									announceCount == 0 ||
									( announceCount < trackerUrlLists.size() && announceFailCount == announceCount )) &&
								!TRTrackerUtils.isUDPProbeOK(reqUrl)){

							// skip probe

						}else{

				  			String	tracker_network	= AENetworkClassifier.categoriseAddress( reqUrl.getHost());

				  			if ( tracker_network == AENetworkClassifier.AT_PUBLIC ){

				  				udpAnnounceURL = new URL(reqUrl.toString().replaceFirst("^http", "udp"));

				  				udp_probe = true;
				  			}
						}
					}

			  		if ( udpAnnounceURL != null ){

						failure_reason = announceUDP( reqUrl, message, udp_probe );

						if ((failure_reason != null || message.size() == 0) && udp_probe){

								// automatic UDP probe failed, use HTTP again

							udpAnnounceURL = null;

							if ( autoUDPprobeEvery < 16 ){

								autoUDPprobeEvery <<= 1;
							}else{

									// unregister in case the tracker somehow changed its capabilities

								TRTrackerUtils.setUDPProbeResult(reqUrl, false);
							}

							if (Logger.isEnabled()){
								Logger.log(new LogEvent(torrent, LOGID, LogEvent.LT_INFORMATION, "redirection of http announce [" + tracker_url[0] + "] to udp failed, will retry in " + autoUDPprobeEvery + " announces"));
							}
						}else if (failure_reason == null && udp_probe){

							TRTrackerUtils.setUDPProbeResult(reqUrl, true);

							if (Logger.isEnabled()){
								Logger.log(new LogEvent(torrent, LOGID, LogEvent.LT_INFORMATION, "redirection of http announce [" + tracker_url[0] + "] to udp successful"));
							}

							autoUDPprobeEvery = 1;
							autoUDPProbeSuccessCount++;
						}
					}

			  		announceCount++;

			  		if ( udpAnnounceURL == null){

			  			boolean	failed = false;

			  			if ( !az_tracker && !tcpAnnounceEnabled ){

			  				String	tracker_network	= AENetworkClassifier.categoriseAddress( reqUrl.getHost());

				  			if ( tracker_network == AENetworkClassifier.AT_PUBLIC ){

				  				failure_reason = "HTTP Tracker protocol disabled";

				  				failed = true;
				  			}
			  			}

			  			if ( !failed ){

			  				failure_reason = announceHTTP( tracker_url, reqUrl, message, i==0 );
			  			}
			  		}

			  			// if we've got some kind of response then return it

			  		int size = message.size();
			  		
					if ( size > 0 ){

						return( message.toByteArray());
						
					}else if ( stopped && failure_reason== null ){
						
							// some trackers don't bother returning anything on a 'stopped' event, grrr
						
						return( BEncoder.encode( new HashMap()));
					}

					if ( failure_reason == null ){

						failure_reason = "No data received from tracker";

						if ( reqUrl.getProtocol().equalsIgnoreCase( "udp" )){

							errorLevel = false;
						}
					}
				}catch( SSLException e ){

					// e.printStackTrace();

						// try and install certificate regardless of error (as this changed in JDK1.5
						// and broke this...)

					if ( i == 0 ){//&& e.getMessage().indexOf("No trusted certificate found") != -1 ){

						if ( SESecurityManager.installServerCertificates( reqUrl ) != null ){

								// certificate has been installed

							continue;	// retry with new certificate

						}

						failure_reason = exceptionToString( e );

					}else{

						failure_reason = exceptionToString( e );

					}
				}catch( IOException e ){

					if ( e instanceof UnknownHostException || e instanceof ConnectException ){

						errorLevel = false;
					}

		     		if ( i == 0 && protocol.toLowerCase().startsWith( "http" )){

		      			URL retry_url = UrlUtils.getIPV4Fallback( reqUrl );

		      			if ( retry_url != null ){

		      				reqUrl = retry_url;

		      				continue;
		      			}
		     		}

		     		failure_reason = exceptionToString( e );

				}catch (Exception e){

			  		// e.printStackTrace();

			  		failure_reason = exceptionToString( e );
				}

				if ( failure_reason != null ){
					
					String lcfr = failure_reason.toLowerCase( Locale.US );
							
					if ( lcfr.contains("401")){
				
						failure_reason = "Tracker authentication failed";
						
						errorLevel = false;
						
					}else if ( lcfr.contains( "unresolved" ) || lcfr.contains( "no data" )){
						
						errorLevel = false;
					}
				}

				if (Logger.isEnabled())
					Logger.log(new LogEvent(torrent, LOGID, errorLevel ? LogEvent.LT_ERROR : LogEvent.LT_WARNING,
							"Exception while processing the Tracker Request for " + reqUrl + ": "
									+ failure_reason));

				throw( new Exception( failure_reason));
	 		}

	 			// should never get here as second iteration of loop will always cause an exit

	 		throw( new Exception( "Internal Error: should never get here" ));

		}finally{

			TorrentUtils.setTLSTorrentHash( null );
		}
  	}

	private String
 	announceHTTP(
 		URL[]					tracker_url,	// overwritten if redirected
 		URL						original_reqUrl,
 		ByteArrayOutputStream	message,
 		boolean					first_effort )

 		throws Exception
 	{
		try{
			return( announceHTTPSupport( tracker_url, original_reqUrl, null, first_effort, message ));

		}catch( Exception e ){

			if ( 	first_effort &&
					AENetworkClassifier.categoriseAddress( original_reqUrl.getHost() ) != AENetworkClassifier.AT_PUBLIC ){

				Map<String,Object>	opts = new HashMap<>();

				if ( peer_networks != null ){

					opts.put( AEProxyFactory.PO_PEER_NETWORKS, peer_networks );
				}

				PluginProxy proxy = AEProxyFactory.getPluginProxy( "Tracker update", original_reqUrl, opts, true );

				if ( proxy != null ){

					boolean	ok = false;

					try{

						String result =  announceHTTPSupport( tracker_url, proxy.getURL(), proxy.getProxy(), first_effort, message );

						ok = true;

						return( result );

					}catch( Throwable f ){

					}finally{

						proxy.setOK( ok );
					}
				}
			}

			throw( e );
		}
	}

 	private String
 	announceHTTPSupport(
 		URL[]					tracker_url,	// overwritten if redirected
 		URL						original_reqUrl,
 		Proxy					proxy,
 		boolean					first_effort,
 		ByteArrayOutputStream	message )

 		throws IOException
 	{
 		TRTrackerUtils.checkForBlacklistedURLs( original_reqUrl );

 		URL reqUrl = TRTrackerUtils.adjustURLForHosting( original_reqUrl );

 		reqUrl = AddressUtils.adjustURL( reqUrl );

 		if ( reqUrl != original_reqUrl ){
			if (Logger.isEnabled()){
				Logger.log(new LogEvent(torrent, LOGID,
						"    HTTP: url adjusted to " + reqUrl ));
			}
 		}

 		String	failure_reason = null;

 		HttpURLConnection con;

 		Properties	http_properties = new Properties();

 		http_properties.put( ClientIDGenerator.PR_URL, reqUrl );

 		if ( proxy != null ){

 			http_properties.put( ClientIDGenerator.PR_PROXY, proxy );
 		}

 		if ( enable_sni_hack ){

 			http_properties.put( ClientIDGenerator.PR_SNI_HACK, true );
 		}

 		try{
 			ClientIDManagerImpl.getSingleton().generateHTTPProperties( torrent_hash_target.getBytes(), http_properties );

 		}catch( ClientIDException e ){

 			throw( new IOException( e.getMessage()));
 		}

 		reqUrl = (URL)http_properties.get( ClientIDGenerator.PR_URL );

 		boolean	is_https = reqUrl.getProtocol().equalsIgnoreCase("https");

 		if ( is_https ){

 			// see ConfigurationChecker for SSL client defaults

 			HttpsURLConnection ssl_con;

 			if ( proxy == null ){

 				ssl_con = (HttpsURLConnection)reqUrl.openConnection();

 			}else{

 				ssl_con = (HttpsURLConnection)reqUrl.openConnection( proxy );

 			}

 				// allow for certs that contain IP addresses rather than dns names

 			if ( !internal_error_hack ){

	 			ssl_con.setHostnameVerifier(
	 					new HostnameVerifier()
	 					{
	 						@Override
						  public boolean
	 						verify(
	 								String		host,
									SSLSession	session )
	 						{
	 							return( true );
	 						}
	 					});
 			}

			if ( dh_hack ){

				UrlUtils.DHHackIt( ssl_con );
			}

 			if ( !first_effort ){

 					// meh, some https trackers are just screwed

				TrustManager[] trustAllCerts = SESecurityManager.getAllTrustingTrustManager();

				try{
					SSLContext sc = SSLContext.getInstance("SSL");

					sc.init(null, trustAllCerts, RandomUtils.SECURE_RANDOM);

					SSLSocketFactory factory = sc.getSocketFactory();

					ssl_con.setSSLSocketFactory( factory );

				}catch( Throwable e ){
				}
 			}

 			con = ssl_con;

 		}else{

 			if ( proxy == null ){

 				con = (HttpURLConnection) reqUrl.openConnection();

 			}else{

				con = (HttpURLConnection) reqUrl.openConnection( proxy );
 			}
 		}

			// we want this true but some plugins (grrr) set the global default not to follow
			// redirects

		con.setInstanceFollowRedirects( true );

 		String	user_agent = (String)http_properties.get( ClientIDGenerator.PR_USER_AGENT );

 		if ( user_agent != null ){

 			con.setRequestProperty("User-Agent", user_agent );
 		}

 		con.setRequestProperty("Connection", "close" );

 		// some trackers support gzip encoding of replies

 		con.addRequestProperty("Accept-Encoding","gzip");

 		try{

 			try{
 				con.connect();

			}catch( AEProxyFactory.UnknownHostException e ){

				throw( new UnknownHostException( e.getMessage()));

			}catch( IOException e ){

				if ( is_https ){

					String msg = Debug.getNestedExceptionMessage( e );

					if ( msg.contains( "unrecognized_name" )){

						// SNI borkage - used to fix by globally disabling SNI but this screws too many other things

						enable_sni_hack = true;

					}else if ( msg.contains( "internal_error" ) || msg.contains( "handshake_failure" )){

						internal_error_hack = true;

					}else if ( msg.contains( "DH keypair" )){

						dh_hack = true;
					}
				}

				throw( e );
			}

 			InputStream is = null;

 			try{

 				is = con.getInputStream();

 				String	resulting_url_str = con.getURL().toString();

 				if ( !reqUrl.toString().equals( resulting_url_str )){

 						// some kind of redirect has occurred. Unfortunately we can't get at the underlying
 						// redirection reason (temp, perm etc) so we support the use of an explicit indicator
 						// in the resulting url

 					String	marker = "permredirect=1";

 					int	pos = resulting_url_str.indexOf( marker );

 					if ( pos != -1 ){

 						pos = pos-1;	// include the '&' or '?'

 						try{
 							URL	redirect_url =
 								new URL( resulting_url_str.substring(0,pos));

  							tracker_url[0]	= redirect_url;

 						}catch( Throwable e ){
 							Debug.printStackTrace(e);
 						}
 					}
 				}

 				String encoding = con.getHeaderField( "content-encoding");

 				boolean	gzip = encoding != null && encoding.equalsIgnoreCase("gzip");

 				// System.out.println( "encoding = " + encoding );

 				if ( gzip ){

 					is = new GZIPInputStream( is );
 				}

 					// there are some trackers out there that don't set content length correctly
 					// so we can't reliably use it :(

 				int content_length = -1; //con.getContentLength();

 				//      System.out.println(length);

 				byte[] data = new byte[1024];

 				int	num_read = 0;

 				// some trackers don't return content-length

 				while ( content_length <= 0 || num_read < content_length ){

 					try{
 						int	len = is.read(data);

 						if ( len > 0 ){

 							message.write(data, 0, len);

 							num_read += len;

 							if ( num_read > 128*1024 ){

 									// someone's sending us junk, bail out

 								message.reset();

 								throw( new Exception( "Tracker response invalid (too large)" ));

 							}
 						}else if ( len == 0 ){

 							Thread.sleep(20);

 						}else{

 							break;
 						}

 					}catch (Exception e){

 						if (Logger.isEnabled()) {
							Logger.log(new LogEvent(torrent, LOGID,
									"Exception while Requesting Tracker", e));
							
							String str = message.toString();
							
							if ( str.length() > 128 ){
								
								str = str.substring( 0, 128 ) + "...";
							}
							
							Logger.log(new LogEvent(torrent, LOGID, LogEvent.LT_ERROR,
									"Message received was : " + str));
						}

 						failure_reason = exceptionToString( con, e );

 						break;
 					}
 				}

 			}catch( SSLException e ){

 					// gotta let this through as an exception as it drives the auto-cert install process

 				throw( e );

 			}catch (Exception e){

 				//e.printStackTrace();
 				
 				if ( e instanceof FileNotFoundException ){
 					
 					e = new FileNotFoundException( original_reqUrl.toExternalForm());
 				}
 				
 				failure_reason = exceptionToString( con, e );

 			}finally{

 				if (is != null) {

 					try {
 						is.close();

 					}catch (Exception e) {
 					}

 					is = null;
 				}
 			}
 		}finally{
 			con.disconnect();
 		}

 		return( failure_reason );
 	}

 	protected String
 	announceUDP(
 		URL						original_reqUrl,
		ByteArrayOutputStream	message,
		boolean                 is_probe )
 	{
 		long timeout = is_probe?10000: PRUDPPacket.DEFAULT_UDP_TIMEOUT;

 		URL reqUrl = TRTrackerUtils.adjustURLForHosting( original_reqUrl );

 		if ( reqUrl != original_reqUrl ){
			if (Logger.isEnabled()){
				Logger.log(new LogEvent(torrent, LOGID,
						"    UDP: url adjusted to " + reqUrl ));
			}
 		}

 		String	failure_reason = null;

 		PasswordAuthentication	auth = null;

 		try{
 			if ( (!is_probe) && UrlUtils.queryHasParameter(reqUrl.getQuery(), "auth", false)) {
 				 auth = SESecurityManager.getPasswordAuthentication( UDP_REALM, reqUrl );
 			}

 			int handler_port = UDPNetworkManager.getSingleton().getUDPNonDataListeningPortNumber();
 			
 			List<InetSocketAddress>	url_addresses = UrlUtils.getURLAddresses( reqUrl );
 			
 			Throwable last_error = null;
 			
 			boolean all_skipped = true;
 			
 			boolean ipv6_enabled =  NetworkAdmin.getSingleton().isIPV6Enabled();
 			
 			for ( InetSocketAddress destination: url_addresses ){
 			
 				if ( destination.getAddress() instanceof Inet6Address && !ipv6_enabled ){
 					
 					continue;
 				}
 				
 				all_skipped = false;
 				
 				try{
	 	 			PRUDPPacketHandler handler = PRUDPPacketHandlerFactory.getHandler( handler_port );
	
	 	 			handler = handler.openSession( destination );
	
		 			try{
		
			 			for (int retry_loop=0;retry_loop<PRUDPPacketTracker.DEFAULT_RETRY_COUNT;retry_loop++){
		
			 				try{
		
					 			PRUDPPacket connect_request = new PRUDPPacketRequestConnect();
		
					 			PRUDPPacket reply = handler.sendAndReceive( auth, connect_request, destination, timeout );
		
					 			if ( reply.getAction() == PRUDPPacketTracker.ACT_REPLY_CONNECT ){
		
					 				PRUDPPacketReplyConnect connect_reply = (PRUDPPacketReplyConnect)reply;
		
					 				long	my_connection = connect_reply.getConnectionId();
		
					 				PRUDPPacketRequest request;
		
					 				if ( PRUDPPacketTracker.VERSION == 1 ){
		
					 					PRUDPPacketRequestAnnounce announce_request = new PRUDPPacketRequestAnnounce( my_connection );
		
					 					request = announce_request;
		
						 					// bit of a hack this...
		
						 				String	url_str = reqUrl.toString();
		
						 				int		p_pos = url_str.indexOf("?");
		
						 				url_str	= url_str.substring(p_pos+1);
		
						 				String event_str = getURLParam( url_str, "event" );
		
						 				int	event = PRUDPPacketRequestAnnounce.EV_UPDATE;
		
						 				if ( event_str != null ){
		
						 					if ( event_str.equals( "started" )){
		
						 						event = PRUDPPacketRequestAnnounce.EV_STARTED;
		
						 					}else if ( event_str.equals( "stopped" )){
		
						 						event = PRUDPPacketRequestAnnounce.EV_STOPPED;
		
						 					}else if ( event_str.equals( "completed" )){
		
						 						event = PRUDPPacketRequestAnnounce.EV_COMPLETED;
						 					}
						 				}
		
						 				String	ip_str = getURLParam( url_str, "ip" );
		
						 				int	ip = 0;
		
						 				if ( ip_str != null ){
		
						 					ip = PRHelpers.addressToInt( ip_str);
						 				}
		
						 				announce_request.setDetails(
						 					torrent_hash_target.getBytes(),
						 					tracker_peer_id,
											getLongURLParam( url_str, "downloaded" ),
											event,
											ip,
											(int)getLongURLParam( url_str, "numwant" ),
											getLongURLParam( url_str, "left" ),
											(short)getLongURLParam( url_str, "port" ),
											getLongURLParam( url_str, "uploaded" ));
		
					 				}else{
					 					PRUDPPacketRequestAnnounce2 announce_request = new PRUDPPacketRequestAnnounce2( my_connection );
		
					 					request = announce_request;
		
					 					// bit of a hack this...
		
						 				String	url_str = reqUrl.toString();
		
						 				int		p_pos = url_str.indexOf("?");
		
						 				url_str	= url_str.substring(p_pos+1);
		
						 				String event_str = getURLParam( url_str, "event" );
		
						 				int	event = PRUDPPacketRequestAnnounce.EV_UPDATE;
		
						 				if ( event_str != null ){
		
						 					if ( event_str.equals( "started" )){
		
						 						event = PRUDPPacketRequestAnnounce.EV_STARTED;
		
						 					}else if ( event_str.equals( "stopped" )){
		
						 						event = PRUDPPacketRequestAnnounce.EV_STOPPED;
		
						 					}else if ( event_str.equals( "completed" )){
		
						 						event = PRUDPPacketRequestAnnounce.EV_COMPLETED;
						 					}
						 				}
		
						 				String	ip_str = getURLParam( url_str, "ip" );
		
						 				int	ip = 0;
		
						 				if ( ip_str != null ){
		
						 					ip = PRHelpers.addressToInt( ip_str);
						 				}
		
						 				announce_request.setDetails(
						 					torrent_hash_target.getBytes(),
						 					tracker_peer_id,
											getLongURLParam( url_str, "downloaded" ),
											event,
											ip,
											helper.getUDPKey(),
											(int)getLongURLParam( url_str, "numwant" ),
											getLongURLParam( url_str, "left" ),
											(short)getLongURLParam( url_str, "port" ),
											getLongURLParam( url_str, "uploaded" ));
					 				}
		
					 				reply = handler.sendAndReceive( auth, request, destination );
		
					 				if ( reply.getAction() == PRUDPPacketTracker.ACT_REPLY_ANNOUNCE ){
		
					 					if ( auth != null ){
		
					 						SESecurityManager.setPasswordAuthenticationOutcome( UDP_REALM, reqUrl, true );
					 					}
		
					 					if ( PRUDPPacketTracker.VERSION == 1 ){
						 					PRUDPPacketReplyAnnounce	announce_reply = (PRUDPPacketReplyAnnounce)reply;
		
						 					Map	map = new HashMap();
		
						 					map.put( "interval", new Long( announce_reply.getInterval()));
		
						 					int[]	addresses 	= announce_reply.getAddresses();
						 					short[]	ports		= announce_reply.getPorts();
		
						 					List	peers = new ArrayList();
		
						 					map.put( "peers", peers );
		
						 					for (int i=0;i<addresses.length;i++){
		
						 						Map	peer = new HashMap();
		
						 						peers.add( peer );
		
						 						peer.put( "ip", PRHelpers.intToAddress(addresses[i]).getBytes());
						 						peer.put( "port", new Long( ports[i]));
						 					}
		
						 					byte[] data = BEncoder.encode( map );
		
						 					message.write( data );
		
						 					return( null );
		
					 					}
		
					 					PRUDPPacketReplyAnnounce2	announce_reply = (PRUDPPacketReplyAnnounce2)reply;
		
					 					Map	map = new HashMap();
		
					 					map.put( "interval", new Long( announce_reply.getInterval()));
		
					 					byte[][]	addresses 	= announce_reply.getAddresses();
					 					short[]		ports		= announce_reply.getPorts();
		
					 					map.put( "complete", new Long(announce_reply.getSeeders()));
					 					map.put( "incomplete", new Long(announce_reply.getLeechers()));
		
					 					if ( announce_reply.isIPV6()){
					 						
					 						byte[] v6peers = new byte[addresses.length*18];
					 						
					 						map.put( "peers6", v6peers );
					 						
					 						int pos = 0;
					 						
						 					for (int i=0;i<v6peers.length;i+=18){
			
						 						System.arraycopy( addresses[pos], 0, v6peers, i, 16 );
			
						 						short port = ports[pos];
						 						
						 						v6peers[i+16] = (byte)(port>>8);
						 						v6peers[i+17] = (byte)(port&0xff);
						 						
						 						pos++;
						 					}
					 					}else{
					 						
						 					List	peers = new ArrayList();
		
						 					map.put( "peers", peers );
			
						 					for (int i=0;i<addresses.length;i++){
			
						 						Map	peer = new HashMap();
			
						 						peers.add( peer );
			
						 						peer.put( "ip", PRHelpers.bytesToAddress(addresses[i]).getBytes());
						 						peer.put( "port", new Long( ports[i]));
						 					}
					 					}
		
					 					byte[] data = BEncoder.encode( map );
		
					 					message.write( data );
		
					 					return( null );
		
					 				}
		
					 				failure_reason = ((PRUDPPacketReplyError)reply).getMessage();
		
					 			}else{
		
					 				failure_reason = ((PRUDPPacketReplyError)reply).getMessage();
					 			}
					 		}catch( PRUDPPacketHandlerException e ){
		
					 			if ( e.getMessage() == null ||
									!e.getMessage().contains("timed out")){
		
					 				throw( e );
					 			}
					 		}
			 			}
			 		}finally{
		
			 			handler.closeSession();
			 		}
 				}catch( Throwable e){
 					
 					last_error = e;
 				}
 			}
 			
 			if ( all_skipped ){
 				
 				throw( new Exception( "IPv6 disabled" ));
 			}
 			
 			if ( last_error != null ){
 				
 				throw( last_error );
 			}
 		}catch( Throwable e ){

 			failure_reason = exceptionToString(e);
 		}

		if ( auth != null ){

			SESecurityManager.setPasswordAuthenticationOutcome( UDP_REALM, reqUrl, false );
		}

 		return( failure_reason );
 	}

 	protected long
 	getLongURLParam(
 		String		url,
		String		param )
 	{
 		String	val = getURLParam( url, param );

 		if( val == null ){

 			return(0);
 		}

 		return( Long.parseLong( val ));
 	}

 	protected String
 	getURLParam(
 		String		url,
		String		param )
 	{
 		int	p1 = url.indexOf( param + "=" );

 		if ( p1 == -1 ){

 			return( null );
 		}

 		int	p2 = url.indexOf( "&", p1 );

 		if ( p2 == -1 ){

 			return( url.substring(p1+param.length()+1));
 		}

 		return( url.substring(p1+param.length()+1,p2));
 	}

 	protected String
 	exceptionToString(
 		Throwable 			e )
 	{
 		return( exceptionToString( null, e ));
 	}
 	
 	protected String
 	exceptionToString(
 		HttpURLConnection	con,
 		Throwable 			e )
 	{
 		String	str;

 		if ( e instanceof BoringException || e instanceof FileNotFoundException || e instanceof UnknownHostException){

 			str = Debug.getNestedExceptionMessage(e);

 		}else{

 			String class_name = e.getClass().getName();

 			int	pos = class_name.lastIndexOf( '.' );

 			if ( pos != -1 ){

 				class_name = class_name.substring(pos+1);
 			}

 			pos = class_name.indexOf( '$' );

 			if ( pos != -1 ){

 				class_name = class_name.substring(pos+1);
 			}

 			str = class_name + ": " + Debug.getNestedExceptionMessage(e);
 		}

 		if (str.contains("timed out")){

 			str  = "timeout";
 		}

 		if ( con != null ){
 			
 			try{
 				String error = FileUtil.readInputStreamAsString( con.getErrorStream(), 512 ).trim();
 				
 				if ( !error.isEmpty()){
 					
 					str += " [error=" + error + "]";
 				}
 			}catch( Throwable f ){
 			}
 		}
 		return( str );
 	}

  private TRTrackerAnnouncerRequestImpl
  constructRequest(
  	String 		evt,
	URL			_url,
	TimerEvent	timer_event )

  	throws Exception
  {
  	String	url = _url.toString();

  	StringBuffer request = new StringBuffer(url);

  		// if url already has a query component then just append our parameters on the end
  	if ( url.indexOf('?') != -1 ){
  		request.append('&');
  	}else{
  		request.append('?');
  	}

  		// the client-id stuff RELIES on info_hash being the FIRST parameter added by
  		// us to the URL, so don't change it!

  	request.append(info_hash);
  	request.append(tracker_peer_id_str);

  	int tcp_port = announce_data_provider.getTCPListeningPortNumber();
  	
  	AllTrackersTracker att = all_trackers.getTracker( _url );
  	
  	boolean disable_crypto_port = false;
  	
  	if ( att != null ){
  
  		if ( timer_event != null ){
  		
  			timer_event.setName( att.getTrackerName());
  		}
  		
  		Map<String,Object>	options = att.getOptions();
  		
  		if ( options != null ){
  			
  			Number cp = (Number)options.get( AllTrackersTracker.OPT_CRYPTO_PORT );
	  		
	  		if ( cp != null && cp.intValue() == 2 ){
	  			
	  			disable_crypto_port = true;
	  		}
	  	}
  	}
  	
	String	port_details;
	
	if ( announce_data_provider.getCryptoLevel()==NetworkManager.CRYPTO_OVERRIDE_REQUIRED ){
		
		port_details = TRTrackerUtils.getPortsForURLFullCrypto( tcp_port );
		
	}else{
		
		port_details = TRTrackerUtils.getPortsForURL( tcp_port, disable_crypto_port );
	}
	

		// nasty hack to deal with fact that the update tracker doesn't support port=0 (which is default with SOCKS)
				
	try{
		if ( _url.getHost().endsWith( ".amazonaws.com" )){
			
			port_details = port_details.replaceAll( "&port=0", "&port=" + tcp_port );
		}
	}catch( Throwable e ){
	}
				
  	request.append(port_details);
  	
  	long total_sent 	= announce_data_provider.getTotalSent();
  	long total_received	= announce_data_provider.getTotalReceived();
  	  	
  	request.append("&uploaded=").append( total_sent );
  	request.append("&downloaded=").append( total_received );

  	if ( Constants.DOWNLOAD_SOURCES_PRETEND_COMPLETE ){

  	 	request.append("&left=0");

  	}else{

  		request.append("&left=").append(announce_data_provider.getRemaining());
  	}

  		// 3017: added at request of tracker admins who want to be able to monitor swarm poisoning

  	request.append("&corrupt=").append(announce_data_provider.getFailedHashCheck());


    //TrackerID extension
    if( tracker_id.length() > 0 ) {
      request.append( "&trackerid=").append(tracker_id );
    }


    if (evt.length() != 0) {
    	request.append("&event=").append(evt);
    }

    boolean	stopped = evt.equals("stopped");

    if ( stopped ){

    	request.append("&numwant=0");

    	if ( stopped_for_queue ){

    		request.append( "&azq=1" );
    	}
    }else {

      //calculate how many peers we should ask for

      int numwant = Math.min(calculateNumWant(),userMaxNumwant);


      request.append("&numwant=").append(numwant);

      //no_peer_id has been made obsolete by 'compact'
    }

    	// actually, leave this in, ask PARG why!

    request.append("&no_peer_id=1");

    String	tracker_network_host	= _url.getHost();
    
    String	tracker_network			= AENetworkClassifier.categoriseAddress( tracker_network_host );

    	// latest space saving measure, a compact return type where peers are returned
    	// as 6 byte entries in a single byte[] (4 bytes ip, 2 byte port)
    	// leave this as always supplied, ask PARG why

    request.append( "&compact=1" );

    	// any explicit override takes precedence over any implicit override added
    	// when hosting torrents

    String explicit_ips = COConfigurationManager.getStringParameter( "Override Ip", "" );

    String 	ip					= null;

    	// make sure this tracker network is enabled

    boolean	network_ok			= false;
    boolean	has_public_net		= false;

    if ( peer_networks == null ){

    	network_ok			= true;
    	has_public_net		= true;

    }else{
	    for (int i=0;i<peer_networks.length;i++){

	    	if ( peer_networks[i] == AENetworkClassifier.AT_PUBLIC ){

	    		has_public_net = true;
	    	}

	    	if ( peer_networks[i] == tracker_network ){

	    		network_ok	= true;
	    	}
	    }
    }

    if ( !network_ok ){

    	throw( new Exception( "Network not enabled for url '" + _url + "'" ));
    }

    String	public_ip_override = null;

   	if ( explicit_ips.length() > 0 ){

   			// gotta select an appropriate override based on network type

   			// UDP protocol only support IPv4 overrides so select IPv4 over IPv6 if possible
   		
   		boolean is_udp = _url.getProtocol().equalsIgnoreCase( "udp" );
   		
   		boolean found_invalid_udp = false;
   		
		StringTokenizer	tok = new StringTokenizer( explicit_ips, ";" );

		while( tok.hasMoreTokens()){

			String	this_address = tok.nextToken().trim();

			if ( this_address.length() > 0 ){

				String	cat = AENetworkClassifier.categoriseAddress( this_address );

				if ( cat == AENetworkClassifier.AT_PUBLIC ){

					if ( is_udp ){
						
						try{
							InetAddress i_address = HostNameToIPResolver.syncResolve(this_address);
						
							if ( i_address.getAddress().length != 4 ){
					
								found_invalid_udp = true;
								
								continue;
							}
						}catch( Throwable e ){
							
						}
					}
					
					public_ip_override	= this_address;
				}

				if ( tracker_network == cat ){

					ip = this_address;

					break;
				}
			}
		}
		
		if ( ip == null && found_invalid_udp && tracker_network == AENetworkClassifier.AT_PUBLIC ){
			
			if ( Constants.IS_CVS_VERSION ){
				
				Debug.outNoStack( "Announce for " + _url + ": no valid IPv4 override found");
			}
		}
   	}

   	if ( ip == null ){

   			// if we have a normal explicit override and this is enabled then use it

   		if ( has_public_net && public_ip_override != null ){

   			ip = public_ip_override;

   		}else{

	   		if ( ip_override != null && !TorrentUtils.isDecentralised( ip_override )){

	   			ip = ip_override;
	   		}
   		}
   	}
   	
   	if ( ip == null && tracker_network == AENetworkClassifier.AT_TOR ){
   		   				
   		InetSocketAddress tor_local = AEPluginProxyHandler.getLocalAddress( tracker_network_host, has_public_net?27657:27658 );
   		
   		if ( tor_local != null ){
   			
   			ip = tor_local.getHostString();
   			
   			request = new StringBuffer( request.toString().replaceAll( "&port=[0-9]+", "&port=" + tor_local.getPort()));
   			
   		}else{
   			
   			request = new StringBuffer( request.toString().replaceAll( "&port=[0-9]+", "&port=0"));
   			
   			if (Logger.isEnabled())
				Logger.log(new LogEvent(torrent, LOGID, LogEvent.LT_ERROR,
						"IP Override missing for Tor tracker, incoming connections disabled"));
   		}
   	}

    if ( ip != null ){

    	if ( tracker_network == AENetworkClassifier.AT_PUBLIC ){

    		try{
    			ip = PRHelpers.DNSToIPAddress( ip );

    		}catch( UnknownHostException e){

    			if (Logger.isEnabled())
						Logger.log(new LogEvent(torrent, LOGID, LogEvent.LT_ERROR,
								"IP Override host resolution of '" + ip
										+ "' fails, using unresolved address"));
    		}
    	}

    	request.append("&ip=").append(ip);
    }

    if ( COConfigurationManager.getBooleanParameter("Tracker Key Enable Client", true )){

      	request.append( "&key=").append( helper.getTrackerKey());
    }

	String	ext = announce_data_provider.getExtensions();

	if ( ext != null ){

			// sanitise it

		while ( ext.startsWith("&")){

			ext = ext.substring( 1 );
		}

		request.append( "&" );

		request.append( ext );
	}

	request.append( "&azver=" + TRTrackerAnnouncer.AZ_TRACKER_VERSION_CURRENT );

	if ( az_tracker && !stopped ){

		int up = announce_data_provider.getUploadSpeedKBSec( evt.equals( "started" ));

		if ( up > 0 ){

			request.append("&azup=").append(up);
		}

		String as = NetworkAdmin.getSingleton().getCurrentASN().getAS();

	    if ( as.length() > 0 ){

	    	request.append("&azas=").append(URLEncoder.encode(as, "UTF8"));
	    }

		DHTNetworkPosition	best_position = DHTNetworkPositionManager.getBestLocalPosition();

		if ( best_position != null ){

			try{
				byte[]	bytes = DHTNetworkPositionManager.serialisePosition( best_position );

				request.append("&aznp=").append(Base32.encode(bytes));

			}catch( Throwable e ){

				Debug.printStackTrace( e );
			}
		}
	}

		// bah, issue with an i2p tracker regarding what is passed it seems: truncate to minimum required and order for the moment...

	if ( tracker_network == AENetworkClassifier.AT_I2P ){

		String	temp = request.toString();

		int	pos = temp.indexOf( '?' );

		String head = temp.substring( 0, pos );
		String tail = temp.substring( pos+1 );

		String[]	bits = tail.split( "&" );

		Map<String,String>	map = new HashMap<>();

		for ( String bit: bits ){

			String[] arg = bit.split( "=" );

			map.put( arg[0], arg[1] );
		}

		tail = "";

		for ( String str: new String[]{ "info_hash", "peer_id", "port", "ip", "uploaded", "downloaded", "left", "compact", "event", "numwant" }){

			String val = map.get( str );

			if ( val != null ){

				tail += (tail.length()==0?"":"&") + str + "=" + map.get( str );
			}
		}

		request = new StringBuffer( head + "?" + tail );
	}

  	return( new TRTrackerAnnouncerRequestImpl( helper.getSessionID(), torrent_hash_target, stopped, new URL( request.toString()), total_sent, total_received ));
  }

  protected int
  calculateNumWant()
  {
	if ( !announce_data_provider.isPeerSourceEnabled( PEPeerSource.PS_BT_TRACKER )){

		return( 0 );
	}

    int MAX_PEERS = 100;

    	// ask for a bit more than our max to allow for connections failures

    int maxAllowed = 3*announce_data_provider.getMaxNewConnectionsAllowed( "" )/2;

    if ( maxAllowed < 0 || maxAllowed > MAX_PEERS ) {
      maxAllowed = MAX_PEERS;
    }

    return maxAllowed;
  }

  @Override
  public byte[]
  getPeerId()
  {
  	return( data_peer_id );
  }

 	@Override
  public void
 	setAnnounceDataProvider(
 		TRTrackerAnnouncerDataProvider _provider)
 	{
		try{
			this_mon.enter();

			announce_data_provider = _provider;

		}finally{

			this_mon.exit();
		}
 	}

	@Override
	public TOTorrent
	getTorrent()
	{
		return( torrent );
	}

	@Override
	public URL
	getTrackerURL()
	{
		return( lastUsedUrl );
	}

	@Override
	public void
	setTrackerURL(
		URL new_url )
	{
		try{
			new_url = new URL( new_url.toString().replaceAll(" ", ""));

			List list = new ArrayList(1);

			list.add( new_url );

			trackerUrlLists.clear();

			trackerUrlLists.add( list );

			informURLChange( lastUsedUrl, new_url, true );

		}catch( Throwable e ){

			Debug.printStackTrace(e);
		}
	}

	@Override
	public TOTorrentAnnounceURLSet[]
	getAnnounceSets()
	{
		return( announce_urls );
	}

	@Override
	public void
	resetTrackerUrl(
		boolean		shuffle )
	{
		String	old_list = trackerURLListToString();

		constructTrackerUrlLists(shuffle);

		if ( trackerUrlLists.size() == 0 ){

			return;
		}

		if ( !old_list.equals(trackerURLListToString())){

			URL	first_url = (URL)((List)trackerUrlLists.get(0)).get(0);

			informURLChange( lastUsedUrl, first_url, true );
		}
	}

	@Override
	public void
	refreshListeners()
	{
		informURLRefresh();
	}

	@Override
	public void
	setIPOverride(
		String		override )
	{
		ip_override = override;
	}

	@Override
	public void
	clearIPOverride()
	{
		ip_override = null;
	}

	private void
	constructTrackerUrlLists(
		boolean	shuffle )
	{
		try{
			trackerUrlLists = new ArrayList<>(1);

				//This entry is present on multi-tracker torrents

			if ( announce_urls.length == 0 ){

					//If not present, we use the default specification

				URL url = torrent.getAnnounceURL();

					//We then construct a list of one element, containing this url, and put this list
					//into the list of lists of urls.

				List<URL> list = new ArrayList();

				list.add(url);

				trackerUrlLists.add(list);
			}else{

					//Ok we have a multi-tracker torrent

				for(int i = 0 ; i < announce_urls.length ; i++){

				  	//Each list contains a list of urls

					URL[]	urls = announce_urls[i].getAnnounceURLs();

				 	List<URL> random_urls = new ArrayList<>();

				 	for(int j = 0 ; j < urls.length; j++){

						//System.out.println(urls.get(j).getClass());

						URL url = urls[j];

							//Shuffle

						int pos = shuffle?(int)(Math.random() *  (random_urls.size()+1)):j;

						random_urls.add(pos,url);
				  	}

				  		//Add this list to the list

				 	trackerUrlLists.add(random_urls);
				}
			}
		}catch(Exception e){

			Debug.printStackTrace( e );
		}
		
		all_trackers.registerTrackers( torrent, trackerUrlLists );
	}

	protected String
	trackerURLListToString()
	{
		String trackerUrlListString = "[";

		for (int i=0;i<trackerUrlLists.size();i++){

			List	group = (List)trackerUrlLists.get(i);

			trackerUrlListString	+= (i==0?"":",") + "[";

			for (int j=0;j<group.size();j++){

				URL	u = (URL)group.get(j);

				trackerUrlListString	+= (j==0?"":",") + u.toString();
			}

			trackerUrlListString	+= "]";
		}

		trackerUrlListString += "]";

		return( trackerUrlListString );
	}

  	protected TRTrackerAnnouncerResponseImpl
  	decodeTrackerResponse(
  		URL			url,
  		byte[]		data )
  	{
  		String	failure_reason;

  		if ( data == null ){

  			failure_reason = "no response";

  		}else{

	 		try{
					   //parse the metadata

	 			Map metaData = null;

	 			try{
	 				metaData = BDecoder.decode(data);

 					// obey any peers source restrictions

	 				Object o = metaData.get( "az_ps" );

	 				if ( o instanceof List ){

	 					List peer_sources = (List)o;

	 					List	x = new ArrayList();

	 					for (int i=0;i<peer_sources.size();i++){

	 						Object o1 = peer_sources.get(i);

	 						if ( o1 instanceof byte[] ){

	 							x.add( new String((byte[])o1));
	 						}
	 					}

	 					String[]	y = new String[x.size()];

	 					x.toArray( y );

	 					announce_data_provider.setPeerSources( y );
	 				}

	 					// handle any user warnings in the response

	 				String warning_message = null;
	 				
	 				try{
	 					byte[]	b_warning_message = (byte[])metaData.get( "warning message" );
	 					
	 					if ( b_warning_message != null ){
	 						
	 						warning_message = new String(b_warning_message);
	 					
	 						if ( COConfigurationManager.getBooleanParameter( "Tracker Client Show Warnings" )){
	
									// don't report the same message twice per torrent
	
								if ( !warning_message.equals( last_tracker_message )){
	
									last_tracker_message	= warning_message;
	
									boolean	log_it = false;
	
										// only report a given message once per tracker
	
									try{
										class_mon.enter();
	
										String last_warning_message = (String)tracker_report_map.get( url.getHost());
	
										if ( 	last_warning_message == null ||
												!warning_message.equals( last_warning_message )){
	
											log_it	= true;
	
											tracker_report_map.put( url.getHost(), warning_message );
										}
									}finally{
	
										class_mon.exit();
									}
	
									if ( log_it ){
			 							Logger.logTextResource(new LogAlert(torrent,
			 									LogAlert.UNREPEATABLE, LogAlert.AT_WARNING,
												"TrackerClient.announce.warningmessage"), new String[] {
												announce_data_provider.getName() + " - " + UrlUtils.getBaseURL( url ), warning_message });
			 						}
								}
							}
	 					}
	 				}catch( Throwable e ){

	 					Debug.printStackTrace( e );
	 				}

					long	time_to_wait;

					try {
						if ( !metaData.containsKey( "interval" )){
							
								// deal with trackers that return empty response on 'stop'
							
							if ( stopped ){
							
								time_to_wait = 60;	// irrelevant as stopped
								
							}else{
								
								throw( new Exception( "interval missing" ));
							}
						}else{

							tracker_interval = time_to_wait = ((Long) metaData.get("interval")).longValue();
	
							Long raw_min_interval = (Long) metaData.get("min interval");
	
							if (Logger.isEnabled()) {
								Logger.log(new LogEvent(torrent, LOGID, LogEvent.LT_INFORMATION,
										"Received from announce: 'interval' = " + time_to_wait
												+ "; 'min interval' = " + raw_min_interval));
							}
	
							// guard against crazy return values
							if (time_to_wait < 0 || time_to_wait > 0xffffffffL) {
								time_to_wait = 0xffffffffL;
							}
	
							if (raw_min_interval != null) {
								tracker_min_interval = min_interval = raw_min_interval.longValue();
	
								// ignore useless values
								// Note: Many trackers set min_interval and interval the same.
								if (min_interval < 1) {
									if (Logger.isEnabled()) {
										Logger.log(new LogEvent(
												torrent,
												LOGID,
												LogEvent.LT_INFORMATION,
												"Tracker being silly and "
														+ "returning a 'min interval' of less than 1 second ("
														+ min_interval + ")"));
									}
									min_interval = 0;
								} else if (min_interval > time_to_wait) {
									if (Logger.isEnabled()) {
										Logger.log(new LogEvent(
												torrent,
												LOGID,
												LogEvent.LT_INFORMATION,
												"Tracker being silly and "
														+ "returning a 'min interval' ("
														+ min_interval
														+ ") greater than recommended announce 'interval'"
														+ " (" + time_to_wait + ")"));
									}
									min_interval = 0;
								}
							} else {
								// tracker owners complain we announce too much but then never
								// implement "min interval".  So take it into our own hands
								// and enforce a min_interval of interval when there is no
								// "min interval"
								min_interval = time_to_wait > 30 ? time_to_wait - 10 : time_to_wait;
							}
	
							if(userMinInterval != 0)
							{
								time_to_wait = Math.max(userMinInterval, time_to_wait);
								min_interval = Math.max(min_interval, userMinInterval);
								if (Logger.isEnabled()) {
									Logger.log(new LogEvent(torrent, LOGID, LogEvent.LT_INFORMATION,
											"Overriding with user settings: 'interval' = " + time_to_wait
													+ "; 'min interval' = " + min_interval));
								}
							}


							// roll back 10 seconds to make sure we announce before the tracker
							// times us out.  This is done after min_interval in order not to
							// mess up the "ignore useless values"
							if (time_to_wait > 30){
								time_to_wait -= 10;
							}
						}
						
						if ( metaData.containsKey( "failure reason")){
							
							throw( new Exception( "Tracker reported 'failure reason'" ));
						}

					} catch (Exception e) {

    				     byte[]	failure_reason_bytes = (byte[]) metaData.get("failure reason");

    				     if ( failure_reason_bytes == null ){

    				    	 if (Logger.isEnabled())
    								Logger.log(new LogEvent(torrent, LOGID, LogEvent.LT_WARNING,
    										"Problems with Tracker, will retry in "
    												+ getErrorRetryInterval() + "ms"));

    				       return( new TRTrackerAnnouncerResponseImpl( url, torrent_hash_actual, TRTrackerAnnouncerResponse.ST_OFFLINE, getErrorRetryInterval(), "Unknown cause: " + Debug.getNestedExceptionMessage(e)));

    				     }

    			     		// explicit failure from the tracker

    			       failure_reason = new String( failure_reason_bytes, Constants.DEFAULT_ENCODING_CHARSET);

    			       return( new TRTrackerAnnouncerResponseImpl( url, torrent_hash_actual, TRTrackerAnnouncerResponse.ST_REPORTED_ERROR, getErrorRetryInterval(), failure_reason ));

				   }

				   	//System.out.println("Response from Announce: " + new String(data));

				   Long incomplete_l 	= getLong( metaData, "incomplete");
				   Long complete_l 		= getLong( metaData, "complete");
				   Long downloaded_l 	= getLong( metaData, "downloaded");

				   if ( incomplete_l != null || complete_l != null  ){

				  	 if (Logger.isEnabled())
							Logger.log(new LogEvent(torrent, LOGID,
									"ANNOUNCE SCRAPE1: seeds=" + complete_l + " peers="
											+ incomplete_l));
				   }


			           //TrackerID extension, used by phpbt trackers.
			           //We reply with '&trackerid=1234' when we receive
			           //'10:tracker id4:1234e' on announce reply.
			           //NOTE: we receive as 'tracker id' but reply as 'trackerid'
			           byte[] trackerid = (byte[])metaData.get( "tracker id" );
			           if( trackerid != null ) {
			             tracker_id = new String( trackerid );
			           }

				    byte[]	crypto_flags = (byte[])metaData.get( "crypto_flags" );

						//build the list of peers
					List valid_meta_peers = new ArrayList();

				    Object	meta_peers_peek = metaData.get( "peers" );


			    	Long	az_compact_l 	= (Long)metaData.get( "azcompact" );
			    	long	az_compact		= az_compact_l==null?0:az_compact_l.longValue();

					boolean	this_is_az_tracker = az_compact == 2;

					if ( az_tracker != this_is_az_tracker || lastUsedUrl != lastAZTrackerCheckedURL ){

						lastAZTrackerCheckedURL = lastUsedUrl;

						az_tracker	= this_is_az_tracker;

						TRTrackerUtils.setAZTracker( url, az_tracker );
					}

			    	if ( az_compact == 2 ){


			    			// latest return to dictionary based data

						List meta_peers = (List)meta_peers_peek;

						int peers_length = meta_peers.size();

				  	 if (Logger.isEnabled()) {
								Logger.log(new LogEvent(torrent, LOGID,
										"ANNOUNCE CompactPeers2: num=" + peers_length));
					   }

						if ( peers_length > 1 ){
								// calculate average rtt to use for those with no rtt

							long	total_rtt 	= 0;
							int		rtt_count	= 0;

							for ( int i = 0; i < peers_length; i++ ){

								Map peer = (Map) meta_peers.get(i);

					    		Long	l_rtt = (Long)peer.get( "r" );

					    		if ( l_rtt != null ){

					    			long rtt = l_rtt.longValue();

					    			if ( rtt <= 0 ){

					    					// invalid, remove

					    				peer.remove( "r" );

					    			}else{

					    				total_rtt 	+= rtt;
					    			}

					    			rtt_count++;
					    		}
							}

							final int average_rtt = (int)( rtt_count==0?0:(total_rtt/rtt_count));

								// sort into smallest rtt order with biased at front

							Collections.sort(
									meta_peers,
									new Comparator()
									{
										@Override
										public int
										compare(
											Object	o1,
											Object	o2 )
										{
											Map	map1 = (Map)o1;
											Map map2 = (Map)o2;

								    		Long	l_rtt1 = (Long)map1.get( "r" );
								    		Long	l_rtt2 = (Long)map2.get( "r" );

								    		boolean	biased_1 = map1.containsKey( "b" );
								    		boolean	biased_2 = map2.containsKey( "b" );

								    		if ( biased_1 == biased_2 ){

								    			int	rtt1 = l_rtt1==null?average_rtt:l_rtt1.intValue();
								    			int	rtt2 = l_rtt2==null?average_rtt:l_rtt2.intValue();

								    			return( rtt1 - rtt2 );

								    		}else if ( biased_1 ){

								    			return( -1 );
								    		}else{

								    			return( +1 );
								    		}
										}
									});

								// interleave non-biased peers with good rtt

							int	biased_pos		= peers_length;
							int	non_biased_pos	= peers_length;

							for ( int i = 0; i < peers_length; i++ ){

								Map peer = (Map) meta_peers.get(i);

								if ( peer.containsKey( "b" )){

									if ( i == 0 ){

										biased_pos	= i;
									}
								}else{

									non_biased_pos = i;

									break;
								}
							}

							List	new_peers = new ArrayList(peers_length);

							int		non_biased_start = non_biased_pos;

							boolean	last_biased	= true;

							while( biased_pos < non_biased_start || non_biased_pos < peers_length ){

								if ( biased_pos < non_biased_start ){

									if ( non_biased_pos < peers_length ){

										Map biased 		= (Map) meta_peers.get(biased_pos);
										Map non_biased 	= (Map) meta_peers.get(non_biased_pos);

										boolean	use_biased;

										if ( !last_biased ){

											use_biased = true;

										}else{

									    	Long	l_rtt_biased 		= (Long)biased.get( "r" );
								    		Long	l_rtt_non_biased 	= (Long)non_biased.get( "r" );

							    			int	biased_rtt 		= l_rtt_biased==null?average_rtt:l_rtt_biased.intValue();
							    			int	non_biased_rtt 	= l_rtt_non_biased==null?average_rtt:l_rtt_non_biased.intValue();

							    			use_biased = non_biased_rtt >= biased_rtt;
										}

										if ( use_biased ){

											new_peers.add( biased );

											biased_pos++;

										}else{

											new_peers.add( non_biased );

											non_biased_pos++;
										}

										last_biased = use_biased;
									}else{

										new_peers.add(  meta_peers.get( biased_pos++ ));
									}
								}else{

									new_peers.add( meta_peers.get( non_biased_pos++ ));
								}
							}

							meta_peers = new_peers;
						}

						for ( int i = 0; i < peers_length; i++ ){

							Map peer = (Map) meta_peers.get(i);

							try{
								byte[]		ip_bytes = (byte[])peer.get("i");

								String	ip;

								if ( ip_bytes.length == 4 ){

						    		int	ip1 = 0xff & ip_bytes[0];
						    		int	ip2 = 0xff & ip_bytes[1];
						    		int	ip3 = 0xff & ip_bytes[2];
						    		int	ip4 = 0xff & ip_bytes[3];

						    		ip 	= ip1 + "." + ip2 + "." + ip3 + "." + ip4;
								}else{

									StringBuilder sb = new StringBuilder(39);

									for ( int j=0; j<16; j+=2 ){

										sb.append(
									    	Integer.toHexString(((ip_bytes[j]<<8) & 0xff00) | (ip_bytes[j+1]&0x00ff)));

									    if (j < 14 ){

									       sb.append( ":" );
									    }
									}

									ip = sb.toString();
								}

					    		byte[]	tcp_bytes	= (byte[])peer.get("t");

					    		int		tcp_port 	= ((tcp_bytes[0]&0xff) << 8 ) + (tcp_bytes[1]&0xff );

					    		byte[]	peer_peer_id = TRTrackerAnnouncerFactoryImpl.getAnonymousPeerId( ip, tcp_port );

					    		int		udp_port 	= 0;

					    		byte[]	udp_bytes = (byte[])peer.get("u");

					    		if ( udp_bytes != null ){

					    			if ( udp_bytes.length == 0 ){

					    				udp_port = tcp_port;

					    			}else{

					    				udp_port	= ((udp_bytes[0]&0xff) << 8 ) + (udp_bytes[1]&0xff );
					    			}
					    		}

					    		int	http_port = 0;

					    		byte[]	http_bytes = (byte[])peer.get("h");

					    		if ( http_bytes != null ){

					    			http_port	= ((http_bytes[0]&0xff) << 8 ) + (http_bytes[1]&0xff );
					    		}

					    		short	protocol = DownloadAnnounceResultPeer.PROTOCOL_NORMAL;

					    		byte[]	protocol_bytes = (byte[])peer.get("c");

					    		if ( protocol_bytes != null ){

					    			protocol = (protocol_bytes[0]&0x01)==0?DownloadAnnounceResultPeer.PROTOCOL_NORMAL:DownloadAnnounceResultPeer.PROTOCOL_CRYPT;
					    		}

					    		Long	l_azver = (Long)peer.get("v" );

					    		byte	az_ver = l_azver==null?TRTrackerAnnouncer.AZ_TRACKER_VERSION_1:l_azver.byteValue();

					    		Long	l_up_speed = (Long)peer.get( "s" );

					    		boolean	biased = peer.containsKey("b");

					    		if ( biased ){

					    			PeerClassifier.setAzureusIP( ip );
					    		}

								TRTrackerAnnouncerResponsePeerImpl new_peer =
									new TRTrackerAnnouncerResponsePeerImpl(
										PEPeerSource.PS_BT_TRACKER,
										peer_peer_id,
										ip,
										tcp_port,
										udp_port,
										http_port,
										protocol,
										az_ver,
										l_up_speed==null?0:l_up_speed.shortValue());

								if (Logger.isEnabled()){

						    		String	extra = "";

						    		Long	l_rtt = (Long)peer.get( "r" );

						    		if ( l_rtt != null ){

						    			extra = ",rtt=" + l_rtt;
						    		}

						    		if ( biased ){

						    			extra += ",biased";
						    		}

									Logger.log(new LogEvent(torrent, LOGID,
											"AZ2-COMPACT PEER: " + new_peer.getString() + extra));
								}

								valid_meta_peers.add( new_peer );

							}catch( Throwable e ){

				    			if (Logger.isEnabled())
				    				Logger.log(
				    					new LogEvent(
				    						torrent, LOGID, LogEvent.LT_ERROR,
				    						"Invalid az2 peer received: " + peer ));
							}
						}

			    	}else if ( meta_peers_peek instanceof List ){

				    		// old style non-compact

						List meta_peers = (List)meta_peers_peek;

							//for every peer

						int peers_length = meta_peers.size();

				  	 if (Logger.isEnabled()) {
								Logger.log(new LogEvent(torrent, LOGID,
										"ANNOUNCE old style non-compact: num=" + peers_length));
					   }

						if ( crypto_flags != null && peers_length != crypto_flags.length ){

							crypto_flags = null;

                           	if (Logger.isEnabled())
                        		Logger.log(new LogEvent(torrent, LOGID, LogEvent.LT_ERROR,
                        				"Invalid crypto_flags returned: length mismatch" ));
						}

						for (int i = 0; i < peers_length; i++) {

							Map peer = (Map) meta_peers.get(i);

							Object s_peerid	= peer.get("peer id");
							Object s_ip		= peer.get("ip");
							Object s_port	= peer.get("port");

								// Assert that all ip and port are available

							if ( s_ip != null && s_port != null ){

									//get the peer ip address

								String base_ip = new String((byte[]) s_ip, Constants.DEFAULT_ENCODING_CHARSET);

								String ip = AddressUtils.convertToShortForm( base_ip );

								if ( ip == null ){

									// corrupt address, skip

									Logger.log(new LogEvent(torrent, LOGID, LogEvent.LT_ERROR,
                            				"Skipping invalid address: " + base_ip ));

									continue;
								}

									//get the peer port number - should be Long but have seen byte[] on occasion

								int peer_port = s_port instanceof byte[]?Integer.parseInt(new String((byte[])s_port)):((Long) s_port).intValue();

                                // try to repair invalid peer ports; worst that can happen is we
                                // still can't make outgoing connections that we already can't make
                                if (peer_port >65535)
                                    peer_port -=65536;
                                if (peer_port <0)
                                    peer_port +=65536;

                                if (peer_port < 0 || peer_port > 65535) {
                                	if (Logger.isEnabled())
                                		Logger.log(new LogEvent(torrent, LOGID, LogEvent.LT_ERROR,
                                				"Invalid peer port given: " + ip + ": " + peer_port));
                                	continue;
                                }

								byte[] peer_peer_id;

								// extension - if peer id is missing then the tracker isn't sending
								// peer ids to save on bandwidth. However, we need something "unique" to
								// work on internally so make an ID up from the ip and port

								if ( s_peerid == null ){

									// Debug.out(ip + ": tracker did not give peerID in reply");

									peer_peer_id = TRTrackerAnnouncerFactoryImpl.getAnonymousPeerId( ip, peer_port );

									// System.out.println("generated peer id" + new String(peerId) + "/" + ByteFormatter.nicePrint( peerId, true ));
								}else{

									peer_peer_id = (byte[])s_peerid ;
								}

								short 	protocol;

								if ( crypto_flags == null ){

									protocol 	= DownloadAnnounceResultPeer.PROTOCOL_NORMAL;

								}else{

									protocol = crypto_flags[i]==0?DownloadAnnounceResultPeer.PROTOCOL_NORMAL:DownloadAnnounceResultPeer.PROTOCOL_CRYPT;
								}

								int		udp_port	= 0;
								int		http_port	= 0;

								TRTrackerAnnouncerResponsePeerImpl new_peer =
									new TRTrackerAnnouncerResponsePeerImpl(
										PEPeerSource.PS_BT_TRACKER,
										peer_peer_id,
										ip,
										peer_port,
										udp_port,
										http_port,
										protocol,
										TRTrackerAnnouncer.AZ_TRACKER_VERSION_1,
										(short)0 );

								if (Logger.isEnabled())
									Logger.log(new LogEvent(torrent, LOGID,
											"NON-COMPACT PEER: " + new_peer.getString()));

								valid_meta_peers.add( new_peer );


							}
						}
				    }else if ( meta_peers_peek instanceof byte[] ){

				    		// byte[] for compact returns


				    	byte[]	meta_peers = (byte[])meta_peers_peek;


			  			String	tracker_network	= AENetworkClassifier.categoriseAddress( url.getHost());

			  			if ( tracker_network == AENetworkClassifier.AT_I2P && ( meta_peers.length % 32 == 0 )){

			  					// 32 byte SHA256 b32 addresses

			  				for ( int i=0;i<meta_peers.length;i+=32 ){

			  					byte[] i2p_id 		= new byte[32];
			  					byte[] peer_peer_id = new byte[20];

			  					System.arraycopy( meta_peers, i, i2p_id, 0, 32 );
			  					System.arraycopy( meta_peers, i, peer_peer_id, 0, 20 );

			  					String hostname = Base32.encode( i2p_id ).toLowerCase(Locale.US) + ".b32.i2p";

					    		TRTrackerAnnouncerResponsePeerImpl peer =
					    			new TRTrackerAnnouncerResponsePeerImpl(
				    					PEPeerSource.PS_BT_TRACKER,
				    					peer_peer_id,
				    					hostname,
				    					6881,
				    					0,
				    					0,
				    					DownloadAnnounceResultPeer.PROTOCOL_NORMAL,
				    					TRTrackerAnnouncer.AZ_TRACKER_VERSION_1,
				    					(short)0 );

					    		if (Logger.isEnabled())
									Logger.log(new LogEvent(torrent, LOGID, "COMPACT PEER: " + peer.getString()));

					    		valid_meta_peers.add( peer );
			  				}

			  			}else{
					    	int	entry_size = az_compact==1?9:6;

					    	if ( crypto_flags != null && meta_peers.length/entry_size != crypto_flags.length ){

								crypto_flags = null;

	                           	if (Logger.isEnabled())
	                        		Logger.log(new LogEvent(torrent, LOGID, LogEvent.LT_ERROR,
	                        				"Invalid crypto_flags returned: length mismatch" ));
							}

					    	int	peer_number = 0;

						  	 if (Logger.isEnabled()) {
										Logger.log(new LogEvent(torrent, LOGID,
												"ANNOUNCE CompactPeers: num=" + (meta_peers.length/entry_size)));
							   }

						  	int	peers_length = meta_peers.length;

					    	for (int i=0;i<(peers_length/entry_size)*entry_size;i+=entry_size){

					    		peer_number++;

					    		int	ip1 = 0xFF & meta_peers[i];
					    		int	ip2 = 0xFF & meta_peers[i+1];
					    		int	ip3 = 0xFF & meta_peers[i+2];
					    		int	ip4 = 0xFF & meta_peers[i+3];
					    		int	po1 = 0xFF & meta_peers[i+4];
					    		int	po2 = 0xFF & meta_peers[i+5];

					    		String	ip 			= "" + ip1 + "." + ip2 + "." + ip3 + "." + ip4;
					    		int		tcp_port 	= po1*256+po2;

					    		if (tcp_port < 0 || tcp_port > 65535) {
					    			if (Logger.isEnabled())
					    				Logger.log(new LogEvent(torrent, LOGID, LogEvent.LT_ERROR,
					    						"Invalid compact peer port given: " + ip + ": "
					    						+ tcp_port));
					    			continue;
					    		}

					    		byte[]	peer_peer_id = TRTrackerAnnouncerFactoryImpl.getAnonymousPeerId( ip, tcp_port );

					    		short 	protocol;
					    		int		udp_port;

					    		if ( az_compact == 1 ){

						    		int	upo1 = 0xFF & meta_peers[i+6];
						    		int	upo2 = 0xFF & meta_peers[i+7];

						    		udp_port 	= upo1*256+upo2;

						    		byte	flags = meta_peers[i+8];

						    		protocol = (flags&0x01)==0?DownloadAnnounceResultPeer.PROTOCOL_NORMAL:DownloadAnnounceResultPeer.PROTOCOL_CRYPT;

					    		}else{


									if ( crypto_flags == null ){

						    			protocol 	= DownloadAnnounceResultPeer.PROTOCOL_NORMAL;

									}else{

										protocol = crypto_flags[peer_number-1]==0?DownloadAnnounceResultPeer.PROTOCOL_NORMAL:DownloadAnnounceResultPeer.PROTOCOL_CRYPT;
									}

					    			udp_port	= 0;
					    		}

					    		int	http_port = 0;

					    		TRTrackerAnnouncerResponsePeerImpl peer =
					    			new TRTrackerAnnouncerResponsePeerImpl(
				    					PEPeerSource.PS_BT_TRACKER,
				    					peer_peer_id,
				    					ip,
				    					tcp_port,
				    					udp_port,
				    					http_port,
				    					protocol,
				    					TRTrackerAnnouncer.AZ_TRACKER_VERSION_1,
				    					(short)0 );

					    		if (Logger.isEnabled())
									Logger.log(new LogEvent(torrent, LOGID, "COMPACT PEER: " + peer.getString()));

					    		valid_meta_peers.add( peer );
					    	}
				    	}
				    }else if ( meta_peers_peek instanceof Map ){

				    		// some trackers incorrectly return an empty Map when no peers available

				    	if (((Map)meta_peers_peek).size() != 0 ){

							throw( new IOException( "peers missing from response" ));
				    	}
				    }else if (!metaData.containsKey("peers6")){

				    		// 	we got nothing useful under peers and no peers6 either

				    	// meh, seen a tracker that doesn't return anything when it has nothing
						// throw( new IOException( "peers missing from response" ));
				    }


			    	final byte[] v6peers = (byte[])metaData.get("peers6");

				    if ( v6peers != null ){

				    		// 16 bytes for v6 + 2 bytes for port

				    	try{
							final int entry_size = 18;
	
							final byte[] rawAddr = new byte[16];
	
							for (int i=0; i<v6peers.length; i+=entry_size ){
	
								System.arraycopy( v6peers, i, rawAddr, 0, 16 );
	
								String ip = InetAddress.getByAddress(rawAddr).getHostAddress();
	
					    		int	po1 = 0xFF & v6peers[i+16];
					    		int	po2 = 0xFF & v6peers[i+17];
	
								int tcp_port = po1*256 + po2;
	
								if (tcp_port < 0 || tcp_port > 65535){
	
									if (Logger.isEnabled()){
	
										Logger.log(new LogEvent(torrent, LOGID, LogEvent.LT_ERROR, "Invalid compactv6 peer port given: " + ip + ": " + tcp_port));
									}
	
									continue;
								}
	
								byte[] peer_peer_id = TRTrackerAnnouncerFactoryImpl.getAnonymousPeerId( ip, tcp_port );
	
								short protocol = DownloadAnnounceResultPeer.PROTOCOL_NORMAL;
	
								TRTrackerAnnouncerResponsePeerImpl peer = new TRTrackerAnnouncerResponsePeerImpl(PEPeerSource.PS_BT_TRACKER, peer_peer_id, ip, tcp_port, 0, 0, protocol, TRTrackerAnnouncer.AZ_TRACKER_VERSION_1, (short) 0);
	
								if (Logger.isEnabled()){
	
									Logger.log(new LogEvent(torrent, LOGID, "COMPACTv6 PEER: " + peer.getString()));
								}
	
								valid_meta_peers.add(peer);
							}
				    	}catch( Throwable e ){
				    		
				    		// seen corrupt v6 peers being returned
				    		
							if (Logger.isEnabled()){
								
								Logger.log(new LogEvent(torrent, LOGID, LogEvent.LT_ERROR, "Invalid compactv6: " + Base32.encode( v6peers )));
							}
				    	}
					}


					TRTrackerAnnouncerResponsePeerImpl[] peers=new TRTrackerAnnouncerResponsePeerImpl[valid_meta_peers.size()];

					valid_meta_peers.toArray(peers);

					helper.addToTrackerCache( peers);

					TRTrackerAnnouncerResponseImpl resp = new TRTrackerAnnouncerResponseImpl( url, torrent_hash_actual, TRTrackerAnnouncerResponse.ST_ONLINE, time_to_wait, peers );

					if ( warning_message != null ){
						
						resp.setAdditionalInfo( warning_message );
					}
						//reset failure retry interval on successful connect

					failure_added_time = 0;

					Map extensions = (Map)metaData.get( "extensions" );

					resp.setExtensions(extensions);

					if ( extensions != null ){

						if ( complete_l == null) {
							complete_l = (Long)extensions.get("complete");
						}

						if ( incomplete_l == null) {
							incomplete_l = (Long)extensions.get("incomplete");
						}

						if (Logger.isEnabled())
							Logger.log(new LogEvent(torrent, LOGID,
									"ANNOUNCE SCRAPE2: seeds=" + complete_l + " peers="
											+ incomplete_l));

						Object	override = extensions.get( "min interval override" );

						if ( override != null && override instanceof Long ){

								// this is to allow specific torrents to be refreshed more quickly
								// if the tracker permits. Parg

							min_interval_override = ((Long)override).longValue();
						}
					}

					if (complete_l != null || incomplete_l != null || downloaded_l != null ) {

						int complete 	= complete_l == null ? 0 : complete_l.intValue();
						int incomplete 	= incomplete_l == null ? 0 : incomplete_l.intValue();
						int downloaded 	= downloaded_l == null ? -1 : downloaded_l.intValue();

						if (complete < 0 || incomplete < 0) {
							resp.setAdditionalInfo(MessageText.getString(
									"Tracker.announce.ignorePeerSeed",
									new String[] { (complete < 0
											? MessageText.getString("MyTorrentsView.seeds") + " == "
													+ complete + ". " : "")
											+ (incomplete < 0
													? MessageText.getString("MyTorrentsView.peers")
															+ " == " + incomplete + ". " : "") }));
						} else {

							resp.setScrapeResult( complete, incomplete, downloaded );

							TRTrackerScraper scraper = TRTrackerScraperFactory.getSingleton();

							if (scraper != null) {
								TRTrackerScraperResponse scrapeResponse = scraper.scrape( torrent, getTrackerURL());
								if (scrapeResponse != null) {
									long lNextScrapeTime = scrapeResponse.getNextScrapeStartTime();

									long now = SystemTime.getCurrentTime();

									long lNewNextScrapeTime =
											now +
											TRTrackerScraperResponseImpl.calcScrapeIntervalSecs( 0, complete) * 1000L;

									// make it look as if the scrape has just run. Important
									// as seeding rules may make calculations on when the
									// scrape value were set

									scrapeResponse.setScrapeStartTime( now );

									if ( lNextScrapeTime < lNewNextScrapeTime ){

										scrapeResponse.setNextScrapeStartTime(lNewNextScrapeTime);
									}

									scrapeResponse.setSeedsPeers(complete, incomplete);

									if ( downloaded >= 0 ){

										scrapeResponse.setCompleted( downloaded );
									}
								}
							}
						}
					}

					return (resp);

				}catch( IOException e ){

					if ( metaData != null ){

						byte[]	failure_reason_bytes = (byte[]) metaData.get("failure reason");

			     			// explicit failure from the tracker

						if (failure_reason_bytes == null) {
				 			Debug.printStackTrace( e );

							failure_reason = "error: " + e.getMessage();

						} else {
  						failure_reason = new String( failure_reason_bytes, Constants.DEFAULT_ENCODING_CHARSET);
						}

						return(
								new TRTrackerAnnouncerResponseImpl(
										url,
										torrent_hash_actual,
										TRTrackerAnnouncerResponse.ST_REPORTED_ERROR,
										Math.max( tracker_interval, getErrorRetryInterval()),
										failure_reason ));
					}

						// decode could fail if the tracker's returned, say, an HTTP response
						// indicating server overload

	 				String	trace_data;

	 				if ( data.length <= 150 ){

	 					trace_data = new String(data);

	 				}else{

	 					trace_data = new String(data,0,150) + "...";
	 				}

	 				if (Logger.isEnabled())
						Logger.log(new LogEvent(torrent, LOGID, LogEvent.LT_ERROR,
								"TRTrackerAnnouncer::invalid reply: " + trace_data));

	 				failure_reason = "invalid reply: " + trace_data;
	 			}
	 		}catch( Throwable e ){

	 			Debug.printStackTrace( e );

				failure_reason = "error: " + e.getMessage();
			}
  		}

		return( new TRTrackerAnnouncerResponseImpl( url, torrent_hash_actual, TRTrackerAnnouncerResponse.ST_OFFLINE, getErrorRetryInterval(), failure_reason ));
  	}

  	private Long
  	getLong(
  		Map		map,
  		String	key )
  	{
  		Object o = map.get( key );

  		if ( o instanceof Long ){

  			return((Long)o);
  		}

  		return( null );
  	}

	protected void
	informURLChange(
		URL		old_url,
		URL		new_url,
		boolean	explicit  )
	{
		if ( old_url != new_url ){
			
			all_trackers.registerTracker( torrent, new_url );
		}
		
		helper.informURLChange( old_url, new_url, explicit );
	}

	protected void
	informURLRefresh()
	{
		helper.informURLRefresh();
	}

	@Override
	public TRTrackerAnnouncerResponse
	getLastResponse()
	{
		if( last_response == null ){

			return new TRTrackerAnnouncerResponseImpl( null, torrent_hash_actual, TRTrackerAnnouncerResponse.ST_OFFLINE, TRTrackerAnnouncer.REFRESH_MINIMUM_SECS, "Initialising" );
		}

		return( last_response );
	}

	@Override
	public boolean
	isManual()
	{
		return( manual_control );
	}

	@Override
	public void
	destroy()
	{
		destroyed	= true;

		try{
			this_mon.enter();

			if ( current_timer_event != null ){

					// cancel any events that are a way off being triggered. note that
					// we don't want to cancel all events as the "stopped" event that
					// is scheduled on stop of a download may still be lurking

				if ( current_timer_event.getWhen() - SystemTime.getCurrentTime() > 10*1000 ){

					if (Logger.isEnabled())
						Logger.log(new LogEvent(torrent, LOGID,
								"Canceling announce trigger"));

					current_timer_event.cancel();
				}
			}
		}finally{

			this_mon.exit();
		}
	}


  /**
   * Retrieve the retry interval to use on announce errors.
   */
  protected int getErrorRetryInterval() {

    long currentTime = SystemTime.getCurrentTime() /1000;

    long diff = currentTime - failure_time_last_updated;

    //use previously calculated interval if it's not time to update
    if( diff < failure_added_time && !(diff < 0) ) {
      return failure_added_time;
    }

    //update previous change time
    failure_time_last_updated = currentTime;

    if( failure_added_time == 0 ) { //start
      failure_added_time = 10;
    }
    else if( failure_added_time < 30 ) {
      //three 10-sec retries
      failure_added_time += 10;
    }
    else if( failure_added_time < 60 ) {
      //two 15-sec retries
      failure_added_time += 15;
    }
    else if( failure_added_time < 120 ) {
      //two 30-sec retries
      failure_added_time += 30;
    }
    else if( failure_added_time < 600 ) {
      //eight 60-sec retries
      failure_added_time += 60;
    }
    else {
      //2-3min random retry
      failure_added_time += 120 + new Random().nextInt( 60 );
    }

    boolean is_seed = (announce_data_provider == null) ? false : announce_data_provider.getRemaining() == 0;

    if( is_seed ) failure_added_time = failure_added_time * 2; //no need to retry as often

    //make sure we're not waiting longer than 30min
    if( !is_seed && failure_added_time > 1800) {
      failure_added_time = 1800;
    }
    else if ( is_seed && failure_added_time > 3600) { //or 60min if seed
      failure_added_time = 3600;
    }

    return failure_added_time;
  }


	@Override
	public void
	setAnnounceResult(
		DownloadAnnounceResult	result )
	{
			// this is how the results from "external" announces get into the system
			// really should refactor so that "normal" and "external" mechanisms are
			// just instances of the same generic approach

		TRTrackerAnnouncerResponseImpl 	response;
		String							status;

		if ( result.getResponseType() == DownloadAnnounceResult.RT_ERROR ){

			status = MessageText.getString("PeerManager.status.error");

			String	reason = result.getError();

			if ( reason != null ){

				status += " (" + reason + ")";
			}

	  		response = new TRTrackerAnnouncerResponseImpl(
				  				result.getURL(),
				  				torrent_hash_actual,
				  				TRTrackerAnnouncerResponse.ST_OFFLINE,
								result.getTimeToWait(),
								reason );
		}else{
			DownloadAnnounceResultPeer[]	ext_peers = result.getPeers();

			List	l_peers = new ArrayList(ext_peers.length);

			boolean ps_enabled = 	announce_data_provider != null &&
									announce_data_provider.isPeerSourceEnabled( PEPeerSource.PS_BT_TRACKER );

			for (int i=0;i<ext_peers.length;i++){

				DownloadAnnounceResultPeer	ext_peer = ext_peers[i];

				String	ps = ext_peer.getSource();

					// filter out any disabled peers

				if ( !ps_enabled && ps.equals( PEPeerSource.PS_BT_TRACKER )){

					continue;

				}else{

					int		http_port	= 0;
					byte	az_version 	= TRTrackerAnnouncer.AZ_TRACKER_VERSION_1;

					TRTrackerAnnouncerResponsePeerImpl p =
						new TRTrackerAnnouncerResponsePeerImpl(
									ext_peer.getSource(),
									ext_peer.getPeerID(),
									ext_peer.getAddress(),
									ext_peer.getPort(),
									ext_peer.getUDPPort(),
									http_port,
									ext_peer.getProtocol(),
									az_version,
									(short)0 );

					l_peers.add( p );

					if (Logger.isEnabled())
						Logger.log(new LogEvent(torrent, LOGID, "EXTERNAL PEER: " + p.getString()));
				}
			}

			TRTrackerAnnouncerResponsePeerImpl[] peers = new TRTrackerAnnouncerResponsePeerImpl[ l_peers.size()];

			l_peers.toArray( peers );

			helper.addToTrackerCache( peers);

			if ( ps_enabled || peers.length > 0 || ext_peers.length == 0 ){

				status = MessageText.getString("PeerManager.status.ok");

			}else{

				status = MessageText.getString("PeerManager.status.ps_disabled");

				peers = new TRTrackerAnnouncerResponsePeerImpl[0];
			}

			response =
				new TRTrackerAnnouncerResponseImpl(
						result.getURL(),
						torrent_hash_actual,
						TRTrackerAnnouncerResponse.ST_ONLINE,
						result.getTimeToWait(),
						peers );
		}

			// only make the user aware of the status if the underlying announce is
			// failing

		if ( 	last_response == null ||
				last_response.getStatus() != TRTrackerAnnouncerResponse.ST_ONLINE ){

			URL result_url = result.getURL();

			boolean	update_is_dht	= TorrentUtils.isDecentralised( result_url );

			tracker_status_str	= status + " (" + (update_is_dht?MessageText.getString( "dht.backup.only" ):(result_url==null?"<null>":result_url.getHost())) + ")";
		}

		helper.informResponse( this, new TRTrackerAnnouncerRequestImpl(), response );
	}

	@Override
	public void
	addListener(
		TRTrackerAnnouncerListener l )
	{
		helper.addListener( l );
	}

	@Override
	public void
	removeListener(
		TRTrackerAnnouncerListener l )
	{
		helper.removeListener( l );
	}

	@Override
	public void
	setTrackerResponseCache(
		Map map	)
	{
		helper.setTrackerResponseCache( map );
	}

	@Override
	public void
	removeFromTrackerResponseCache(
		String ip, int tcpPort)
	{
		helper.removeFromTrackerResponseCache( ip, tcpPort );
	}

	@Override
	public Map
	getTrackerResponseCache()
	{
		return( helper.getTrackerResponseCache());
	}

	@Override
	public TrackerPeerSource
	getTrackerPeerSource(
		TOTorrentAnnounceURLSet set)
	{
		Debug.out( "not implemented" );

		return null;
	}

	@Override
	public TrackerPeerSource
	getCacheTrackerPeerSource()
	{
		Debug.out( "not implemented" );

		return null;
	}

	@Override
	public void
	generateEvidence(
		IndentWriter writer )
	{
		writer.println( "BT announce:" );

		try{
			writer.indent();

			writer.println( "state: " + tracker_state + ", in_progress=" + update_in_progress );

			writer.println( "current: " + (lastUsedUrl==null?"null":lastUsedUrl.toString()));

			writer.println( "last: " + (last_response==null?"null":last_response.getString()));

			writer.println( "last_update_secs: " + last_update_time_secs );

			writer.println( "secs_to_wait: " + current_time_to_wait_secs  + (manual_control?" - manual":""));

			writer.println( "t_interval: " + tracker_interval );

			writer.println( "t_min_interval: " + tracker_min_interval );

			writer.println( "min_interval: " + min_interval );

			writer.println( "min_interval_override: " + min_interval_override );

			writer.println( "rd: last_override=" + rd_last_override + ",percentage=" + rd_override_percentage );

			writer.println( "event: " + ( current_timer_event==null?"null":current_timer_event.getString()));

			writer.println( "stopped: " + stopped + ", for_q=" + stopped_for_queue );

			writer.println( "complete: " + completed + ", reported=" + complete_reported );

		}finally{
			writer.exdent();
		}
	}
}
