/*
 * File    : TRTrackerServerImpl.java
 * Created : 19-Jan-2004
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

package com.biglybt.core.tracker.server.impl;

/**
 * @author parg
 *
 */


import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;

import com.biglybt.core.config.COConfigurationListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.ipfilter.IpFilter;
import com.biglybt.core.ipfilter.IpFilterManagerFactory;
import com.biglybt.core.proxy.AEProxyFactory;
import com.biglybt.core.tracker.server.*;
import com.biglybt.core.util.*;

public abstract class
TRTrackerServerImpl
	implements 	TRTrackerServer
{
	public static final int RETRY_MINIMUM_SECS			= 60;
	public static final int RETRY_MINIMUM_MILLIS		= RETRY_MINIMUM_SECS*1000;
	public static final int CLIENT_TIMEOUT_MULTIPLIER	= 3;

	public static final int TIMEOUT_CHECK 				= RETRY_MINIMUM_MILLIS*CLIENT_TIMEOUT_MULTIPLIER;

	public static int		max_peers_to_send			= 0;
	public static boolean	send_peer_ids				= true;
	public static int		announce_cache_period		= TRTrackerServer.DEFAULT_ANNOUNCE_CACHE_PERIOD;
	public static int		scrape_cache_period			= TRTrackerServer.DEFAULT_SCRAPE_CACHE_PERIOD;
	public static int		announce_cache_threshold	= TRTrackerServer.DEFAULT_ANNOUNCE_CACHE_PEER_THRESHOLD;
	public static int		max_seed_retention			= 0;
	public static int		seed_limit					= 0;
	public static boolean	full_scrape_enable			= true;
	public static boolean	restrict_non_blocking_requests	= true;

	public static boolean	all_networks_permitted		= true;
	public static String[]	permitted_networks			= {};

	public static boolean	support_experimental_extensions;

	public static String	redirect_on_not_found		= "";

	public static final List<String>	banned_clients = new ArrayList<>();

		// torrent map is static across all protocol servers

	private static final Map		torrent_map = new HashMap();

	private static final Map		link_map	= new HashMap();

	protected final AEMonitor class_mon 	= new AEMonitor( "TRTrackerServer:class" );


	static{

		COConfigurationManager.addListener(
			new COConfigurationListener()
			{
				@Override
				public void
				configurationSaved()
				{
					readConfig();
				}
			});

		readConfig();
		
		final AsyncDispatcher	network_dispatcher = new AsyncDispatcher( "tracker:netdispatch", 5000 );
	    
		COConfigurationManager.addAndFireParameterListeners(
			new String[] {
				"Tracker Port Enable",
				"Tracker I2P Enable",
				"Tracker Tor Enable",
				"Tracker Port"
			}, 
			new ParameterListener(){
				
				@Override
				public void 
				parameterChanged(String parameterName)
				{					
					network_dispatcher.dispatch(
						new AERunnable()
						{
							@Override
							public void
							runSupport()
							{
								boolean	tr_enable 	= COConfigurationManager.getBooleanParameter( "Tracker Port Enable" );
								boolean	i2p_enable 	= COConfigurationManager.getBooleanParameter( "Tracker I2P Enable" );
								boolean	tor_enable 	= COConfigurationManager.getBooleanParameter( "Tracker Tor Enable" );
								int		port		= COConfigurationManager.getIntParameter( "Tracker Port" );
								
								String old_i2p_str 	= COConfigurationManager.getStringParameter( "Tracker I2P Host Port", "" );
								String new_i2p_str	= "";
								
								if ( tr_enable ){
									
									if ( i2p_enable ) {
										
										Map<String,Object>	options = new HashMap<>();
					
										options.put( AEProxyFactory.SP_PORT, port );
					
										Map<String,Object> reply =
												AEProxyFactory.getPluginServerProxy(
													"Tracker",
													AENetworkClassifier.AT_I2P,
													"tracker",
													options );
					
										if ( reply != null ){
					
											String host = (String)reply.get( "host" );
					
											new_i2p_str = host + ":" + port;
											
										}else{
											
											new_i2p_str = old_i2p_str;
										}
									}
									
									if ( !old_i2p_str.equals( new_i2p_str )) {
										
										COConfigurationManager.setParameter( "Tracker I2P Host Port", new_i2p_str );
									}
									
									String old_tor_str 	= COConfigurationManager.getStringParameter( "Tracker Tor Host Port", "" );
									String new_tor_str	= "";
								
									if ( tor_enable ){
																				
										Map<String,Object>	options = new HashMap<>();
					
										options.put( AEProxyFactory.SP_PORT, port );
					
										Map<String,Object> reply =
												AEProxyFactory.getPluginServerProxy(
													"Tracker",
													AENetworkClassifier.AT_TOR,
													"tracker",
													options );
					
										if ( reply != null ){
					
											String host = (String)reply.get( "host" );
					
											new_tor_str = host;	// NO Port - onions all on port 80
											
										}else{
											
											new_tor_str = old_tor_str;
										}
									}
									
									if ( !old_tor_str.equals( new_tor_str )) {
										
										COConfigurationManager.setParameter( "Tracker Tor Host Port", new_tor_str );
									}

								}
							}
						});
				}});
	}

	protected static void
	readConfig()
	{
		send_peer_ids = COConfigurationManager.getBooleanParameter( "Tracker Send Peer IDs" );

		max_peers_to_send = COConfigurationManager.getIntParameter( "Tracker Max Peers Returned" );

		scrape_cache_period = COConfigurationManager.getIntParameter( "Tracker Scrape Cache", TRTrackerServer.DEFAULT_SCRAPE_CACHE_PERIOD );

		announce_cache_period = COConfigurationManager.getIntParameter( "Tracker Announce Cache", TRTrackerServer.DEFAULT_ANNOUNCE_CACHE_PERIOD );

		announce_cache_threshold = COConfigurationManager.getIntParameter( "Tracker Announce Cache Min Peers", TRTrackerServer.DEFAULT_ANNOUNCE_CACHE_PEER_THRESHOLD );

		max_seed_retention = COConfigurationManager.getIntParameter( "Tracker Max Seeds Retained", 0 );

		seed_limit = COConfigurationManager.getIntParameter( "Tracker Max Seeds", 0 );

		List	nets = new ArrayList();

		for (int i=0;i<AENetworkClassifier.AT_NETWORKS.length;i++){

			String	net = AENetworkClassifier.AT_NETWORKS[i];

			boolean	enabled =
				COConfigurationManager.getBooleanParameter(
						"Tracker Network Selection Default." + net );

			if ( enabled ){

				nets.add( net );
			}
		}

		String[]	s_nets = new String[nets.size()];

		nets.toArray(s_nets);

		permitted_networks	= s_nets;

		all_networks_permitted = s_nets.length == AENetworkClassifier.AT_NETWORKS.length;

		full_scrape_enable = COConfigurationManager.getBooleanParameter( "Tracker Server Full Scrape Enable" );

		redirect_on_not_found = COConfigurationManager.getStringParameter( "Tracker Server Not Found Redirect" ).trim();

		support_experimental_extensions = COConfigurationManager.getBooleanParameter( "Tracker Server Support Experimental Extensions" );

		restrict_non_blocking_requests = COConfigurationManager.getBooleanParameter( "Tracker TCP NonBlocking Restrict Request Types" );

		String banned = COConfigurationManager.getStringParameter( "Tracker Banned Clients", "" ).trim();

		banned_clients.clear();

		if ( banned.length() > 0 ){

			banned = banned.toLowerCase( Locale.US ).replaceAll( ";", "," );

			String[] bits = banned.split( "," );

			for ( String b: bits ){

				b = b.trim();

				if ( b.length() > 0 ){

					banned_clients.add( b );
				}
			}
		}
	}

	protected static boolean
	getSendPeerIds()
	{
		return( send_peer_ids );
	}

	protected static int
	getMaxPeersToSend()
	{
		return( max_peers_to_send );
	}

	protected static int
	getScrapeCachePeriod()
	{
		return( scrape_cache_period );
	}

	protected static int
	getAnnounceCachePeriod()
	{
		return( announce_cache_period );
	}

	protected static int
	getAnnounceCachePeerThreshold()
	{
		return( announce_cache_threshold );
	}

	protected static int
	getMaxSeedRetention()
	{
		return( max_seed_retention );
	}

	protected static int
	getSeedLimit()
	{
		return( seed_limit );
	}

	public static boolean
	isFullScrapeEnabled()
	{
		return( full_scrape_enable );
	}

	protected static boolean
	getAllNetworksSupported()
	{
		return( all_networks_permitted );
	}

	protected static String[]
	getPermittedNetworks()
	{
		return( permitted_networks );
	}

	public static boolean
	supportsExtensions()
	{
		return( support_experimental_extensions );
	}

	protected final IpFilter	ip_filter	= IpFilterManagerFactory.getSingleton().getIPFilter();

	private long		current_announce_retry_interval;
	private long		current_scrape_retry_interval;
	private long		current_total_clients;

	private int		current_min_poll_interval;
	private final int		current_min_seed_announce_mult;

	private final TRTrackerServerStatsImpl	stats = new TRTrackerServerStatsImpl( this );

	private final String	name;
	private boolean	web_password_enabled;
	private boolean	web_password_https_only;

	private boolean	tracker_password_enabled;
	private String	password_user;
	private byte[]	password_pw;
	private boolean	compact_enabled;
	private boolean	key_enabled;

	private boolean	enabled	= true;

	private boolean	keep_alive_enabled	= false;


	protected final CopyOnWriteList<TRTrackerServerListener>	listeners 	= new CopyOnWriteList<>();
	protected final CopyOnWriteList<TRTrackerServerListener2>	listeners2 	= new CopyOnWriteList<>();

	private final List<TRTrackerServerAuthenticationListener>		auth_listeners		= new ArrayList<>();

	private final Vector<TRTrackerServerRequestListener>	request_listeners 	= new Vector<>();

	protected AEMonitor this_mon 	= new AEMonitor( "TRTrackerServer" );

	private final COConfigurationListener		config_listener;
	private boolean						destroyed;

	private Set							biased_peers;

	private boolean						is_ready;

	public
	TRTrackerServerImpl(
		String		_name,
		boolean		_start_up_ready )
	{
		name		= _name==null?DEFAULT_NAME:_name;
		is_ready	= _start_up_ready;


		config_listener =
			new COConfigurationListener()
			{
				@Override
				public void
				configurationSaved()
				{
					readConfigSettings();
				}
			};

		COConfigurationManager.addListener( config_listener );

		readConfigSettings();

		current_min_poll_interval	= COConfigurationManager.getIntParameter("Tracker Poll Interval Min", DEFAULT_MIN_RETRY_DELAY );

		if ( current_min_poll_interval < RETRY_MINIMUM_SECS ){

			current_min_poll_interval = RETRY_MINIMUM_SECS;
		}

		current_min_seed_announce_mult = COConfigurationManager.getIntParameter( "Tracker Poll Seed Interval Mult" );

		current_announce_retry_interval = current_min_poll_interval;

		int	scrape_percentage 		= COConfigurationManager.getIntParameter("Tracker Scrape Retry Percentage", DEFAULT_SCRAPE_RETRY_PERCENTAGE );

		current_scrape_retry_interval	= (current_announce_retry_interval*scrape_percentage)/100;

		Thread timer_thread =
			new AEThread("TrackerServer:timer.loop")
			{
				@Override
				public void
				runSupport( )
				{
					timerLoop();
				}
			};

		timer_thread.setDaemon( true );

		timer_thread.start();
	}

	protected void
	readConfigSettings()
	{
		web_password_enabled 		= COConfigurationManager.getBooleanParameter("Tracker Password Enable Web");
		tracker_password_enabled 	= COConfigurationManager.getBooleanParameter("Tracker Password Enable Torrent");

		web_password_https_only		= COConfigurationManager.getBooleanParameter("Tracker Password Web HTTPS Only");

		if ( web_password_enabled || tracker_password_enabled ){

			password_user	= COConfigurationManager.getStringParameter("Tracker Username", "");
			password_pw		= COConfigurationManager.getByteParameter("Tracker Password", new byte[0]);
		}

		compact_enabled = COConfigurationManager.getBooleanParameter("Tracker Compact Enable" );

		key_enabled = COConfigurationManager.getBooleanParameter("Tracker Key Enable Server");
	}

	@Override
	public void
	setReady()
	{
		is_ready	= true;
	}

	public final boolean
	isReady()
	{
		return( is_ready );
	}

	@Override
	public void
	setEnabled(
		boolean	e )
	{
		enabled	= e;
	}

	public boolean
	isEnabled()
	{
		return( enabled );
	}

	@Override
	public void
	setEnableKeepAlive(
		boolean	enable )
	{
		keep_alive_enabled = enabled;
	}

	public boolean
	isKeepAliveEnabled()
	{
		return( keep_alive_enabled );
	}

	public TRTrackerServerTorrent
	addLink(
		String					link,
		TRTrackerServerTorrent	target )
	{
		try{
			class_mon.enter();

			return((TRTrackerServerTorrent)link_map.put( link, target ));

		}finally{

			class_mon.exit();
		}
	}

	public void
	removeLink(
		String					link,
		TRTrackerServerTorrent	target )
	{
		try{
			class_mon.enter();

			link_map.remove( link );

		}finally{

			class_mon.exit();
		}
	}

	@Override
	public void
	setBiasedPeers(
		Set		peers )
	{
		if ( biased_peers != null && peers.equals( biased_peers )){

			return;
		}

		String	str = "";

		Iterator	it = peers.iterator();

		while( it.hasNext()){

			str += " " + it.next();
		}

		System.out.println( "biased peers: " + str );

		try{
			class_mon.enter();

			biased_peers = new HashSet( peers );

			Iterator	tit = torrent_map.values().iterator();

			while(tit.hasNext()){

				TRTrackerServerTorrentImpl	this_torrent = (TRTrackerServerTorrentImpl)tit.next();

				this_torrent.updateBiasedPeers( biased_peers );
			}

		}finally{

			class_mon.exit();
		}
	}

	protected Set
	getBiasedPeers()
	{
		return( biased_peers );
	}

	public boolean
	isWebPasswordEnabled()
	{
		return( web_password_enabled || auth_listeners.size() > 0 );
	}

	public boolean
	isTrackerPasswordEnabled()
	{
		return( tracker_password_enabled || auth_listeners.size() > 0 );
	}

	public boolean
	isWebPasswordHTTPSOnly()
	{
		return( web_password_https_only );
	}

	public boolean
	hasExternalAuthorisation()
	{
		return( auth_listeners.size() > 0 );
	}

	public boolean
	hasInternalAuthorisation()
	{
		return( web_password_enabled || tracker_password_enabled );
	}

	public boolean
	performExternalAuthorisation(
		InetSocketAddress	remote_ip,
		String				headers,
		URL					resource,
		String				user,
		String				password )
	{
		if ( headers.toLowerCase( Locale.US ).contains( "x-real-ip")){
		
				// someone messing about
			
			return( false );
		}
		
		headers = headers.trim() + "\r\nX-Real-IP: " + AddressUtils.getHostAddress( remote_ip ) + "\r\n\r\n";

		for (int i=0;i<auth_listeners.size();i++){

			try{

				if ( ((TRTrackerServerAuthenticationListener)auth_listeners.get(i)).authenticate( headers, resource, user, password )){

					return( true );
				}
			}catch( Throwable e ){

				Debug.printStackTrace( e );
			}
		}

		return( false );
	}

	public byte[]
	performExternalAuthorisation(
		URL			resource,
		String		user )
	{
		for (int i=0;i<auth_listeners.size();i++){

			try{

				byte[] sha_pw =  ((TRTrackerServerAuthenticationListener)auth_listeners.get(i)).authenticate( resource, user );

				if ( sha_pw != null ){

					return( sha_pw );
				}
			}catch( Throwable e ){

				Debug.printStackTrace( e );
			}
		}

		return( null );
	}

	@Override
	public String
	getName()
	{
		return( name );
	}

	public boolean
	isCompactEnabled()
	{
		return( compact_enabled );
	}
	public boolean
	isKeyEnabled()
	{
		return( key_enabled );
	}

	public String
	getUsername()
	{
		return( password_user );
	}

	public byte[]
	getPassword()
	{
		return( password_pw );
	}

	public long
	getMinAnnounceRetryInterval()
	{
		return( current_min_poll_interval );
	}

	public long
	getAnnounceRetryInterval(
		TRTrackerServerTorrentImpl	torrent )
	{
		long	clients = current_total_clients;

		if ( clients == 0 ){

			return( current_announce_retry_interval );
		}

		long	res = ( torrent.getPeerCount() * current_announce_retry_interval ) / clients;

		if ( res < current_min_poll_interval ){

			res = current_min_poll_interval;
		}

		return( res );
	}

	public long
	getSeedAnnounceIntervalMultiplier()
	{
		return( current_min_seed_announce_mult );
	}

	public long
	getScrapeRetryInterval(
		TRTrackerServerTorrentImpl	torrent )
	{
		long	clients = current_total_clients;

		if ( torrent == null || clients == 0 ){

			return( current_scrape_retry_interval );
		}

		long	res = ( torrent.getPeerCount() * current_scrape_retry_interval ) / clients;

		if ( res < current_min_poll_interval ){

			res = current_min_poll_interval;
		}

		return( res );
	}

	public long
	getMinScrapeRetryInterval()
	{
		return( current_min_poll_interval );
	}

	@Override
	public TRTrackerServerStats
	getStats()
	{
		return( stats );
	}

	public void
	updateStats(
		int							request_type,
		TRTrackerServerTorrentImpl	torrent,
		int							bytes_in,
		int							bytes_out )
	{
			// non-synced, but doesn't really matter too much and we don't want to bring all parallel
			// requests down to a single monitor here

		stats.update( request_type, bytes_in, bytes_out );

		if ( torrent != null ){

			torrent.updateXferStats( bytes_in, bytes_out );

		}else{

			int	num = torrent_map.size();

				// this gets too expensive to do when we have a lot of torrents so just ignore
				// stats if so

			if ( num < 256 ){

				try{
					class_mon.enter();


					if ( num > 0 ){

						// full scrape or error - spread the reported bytes across the torrents

						int	ave_in	= bytes_in/num;
						int	ave_out	= bytes_out/num;

						int	rem_in 	= bytes_in-(ave_in*num);
						int rem_out	= bytes_out-(ave_out*num);

						Iterator	it = torrent_map.values().iterator();

						while(it.hasNext()){

							TRTrackerServerTorrentImpl	this_torrent = (TRTrackerServerTorrentImpl)it.next();

							if ( it.hasNext()){

								this_torrent.updateXferStats( ave_in, ave_out );

							}else{

								this_torrent.updateXferStats( ave_in+rem_in, ave_out+rem_out );

							}
						}
					}
				}finally{

					class_mon.exit();
				}
			}
		}
	}

	protected void
	updateTime(
		int		request_type,
		long	time )
	{
		stats.updateTime( request_type, time );
	}


	protected void
	timerLoop()
	{
		long	time_to_go = TIMEOUT_CHECK;

		while( !destroyed ){

			try{
				Thread.sleep( RETRY_MINIMUM_MILLIS );

				time_to_go -= RETRY_MINIMUM_MILLIS;

				// recalc tracker interval every minute

				current_min_poll_interval 	= COConfigurationManager.getIntParameter("Tracker Poll Interval Min", DEFAULT_MIN_RETRY_DELAY );

				if ( current_min_poll_interval < RETRY_MINIMUM_SECS ){

					current_min_poll_interval = RETRY_MINIMUM_SECS;
				}

				int	min		= current_min_poll_interval;
				int	max 	= COConfigurationManager.getIntParameter("Tracker Poll Interval Max", DEFAULT_MAX_RETRY_DELAY );
				int	inc_by 	= COConfigurationManager.getIntParameter("Tracker Poll Inc By", DEFAULT_INC_BY );
				int	inc_per = COConfigurationManager.getIntParameter("Tracker Poll Inc Per", DEFAULT_INC_PER );

				int	scrape_percentage = COConfigurationManager.getIntParameter("Tracker Scrape Retry Percentage", DEFAULT_SCRAPE_RETRY_PERCENTAGE );

				int	retry = min;

				int	clients = 0;

				try{
					class_mon.enter();

					Iterator	it = torrent_map.values().iterator();

					while(it.hasNext()){

						TRTrackerServerTorrentImpl	t = (TRTrackerServerTorrentImpl)it.next();

						clients += t.getPeerCount();
					}
				}finally{

					class_mon.exit();
				}

				if ( inc_by > 0 && inc_per > 0 ){

					retry += inc_by * (clients/inc_per);
				}

				if ( max > 0 && retry > max ){

					retry = max;
				}

				if ( retry < RETRY_MINIMUM_SECS ){

					retry = RETRY_MINIMUM_SECS;
				}

				current_announce_retry_interval = retry;

				current_scrape_retry_interval	= (current_announce_retry_interval*scrape_percentage)/100;

				current_total_clients	= clients;

				// timeout dead clients

				if ( time_to_go <= 0 ){

					time_to_go = TIMEOUT_CHECK;

					try{
						class_mon.enter();

						Iterator	it = torrent_map.values().iterator();

						while(it.hasNext()){

							TRTrackerServerTorrentImpl	t = (TRTrackerServerTorrentImpl)it.next();

							t.checkTimeouts();
						}
					}finally{

						class_mon.exit();
					}
				}

			}catch( InterruptedException e ){

				Debug.printStackTrace( e );
			}

		}
	}

	@Override
	public TRTrackerServerTorrent
	permit(
		String		_originator,
		byte[]		_hash,
		boolean		_explicit )

		throws TRTrackerServerException
	{
		return( permit( _originator, _hash, _explicit, true ));
	}

	@Override
	public TRTrackerServerTorrent
	permit(
		String		_originator,
		byte[]		_hash,
		boolean		_explicit,
		boolean		_enabled )

		throws TRTrackerServerException
	{
		// System.out.println( "TRTrackerServerImpl::permit( " + _explicit + ")");

		HashWrapper	hash = new HashWrapper( _hash );

			// don't invoke listeners when synched, deadlock possible

		TRTrackerServerTorrentImpl	entry;

		try{
			class_mon.enter();

			entry = (TRTrackerServerTorrentImpl)torrent_map.get( hash );

		}finally{

			class_mon.exit();
		}

		if ( entry == null ){

			Iterator<TRTrackerServerListener>	it = listeners.iterator();

			while( it.hasNext()){

				if ( !it.next().permitted( _originator, _hash, _explicit )){

					throw( new TRTrackerServerException( "operation denied"));
				}
			}

			try{
				class_mon.enter();

					// double check in-case added in parallel

				entry = (TRTrackerServerTorrentImpl)torrent_map.get( hash );

				if ( entry == null ){

					entry = new TRTrackerServerTorrentImpl( this, hash, _enabled );

					torrent_map.put( hash, entry );
				}
			}finally{

				class_mon.exit();
			}
		}

		return( entry );
	}

	@Override
	public void
	deny(
		byte[]		_hash,
		boolean		_explicit )

		throws TRTrackerServerException
	{
		// System.out.println( "TRTrackerServerImpl::deny( " + _explicit + ")");

		HashWrapper	hash = new HashWrapper( _hash );

		Iterator<TRTrackerServerListener>	it = listeners.iterator();

		while( it.hasNext()){

			if ( !it.next().denied( _hash, _explicit )){

				throw( new TRTrackerServerException( "operation denied"));
			}
		}

		try{
			class_mon.enter();

			TRTrackerServerTorrentImpl	entry = (TRTrackerServerTorrentImpl)torrent_map.get( hash );

			if ( entry != null ){

				entry.delete();
			}

			torrent_map.remove( hash );

		}finally{

			class_mon.exit();
		}
	}

	public TRTrackerServerTorrentImpl
	getTorrent(
		byte[]		hash )
	{
		try{
			class_mon.enter();

			return((TRTrackerServerTorrentImpl)torrent_map.get(new HashWrapper(hash)));

		}finally{

			class_mon.exit();
		}
	}

	public TRTrackerServerTorrentImpl
	getTorrent(
		String		link )
	{
		try{
			class_mon.enter();

			return((TRTrackerServerTorrentImpl)link_map.get( link ));

		}finally{

			class_mon.exit();
		}
	}

	public TRTrackerServerTorrentImpl[]
	getTorrents()
	{
		try{
			class_mon.enter();

			TRTrackerServerTorrentImpl[]	res = new TRTrackerServerTorrentImpl[torrent_map.size()];

			torrent_map.values().toArray( res );

			return( res );
		}finally{

			class_mon.exit();
		}
	}

	public int
	getTorrentCount()
	{
		return( torrent_map.size());
	}

	@Override
	public TRTrackerServerTorrentStats
	getStats(
		byte[]		hash )
	{
		TRTrackerServerTorrentImpl	torrent = getTorrent( hash );

		if ( torrent == null ){

			return( null );
		}

		return( torrent.getStats());
	}

	@Override
	public TRTrackerServerPeer[]
	getPeers(
		byte[]		hash )
	{
		TRTrackerServerTorrentImpl	torrent = getTorrent( hash );

		if ( torrent == null ){

			return( null );
		}

		return( torrent.getPeers());
	}

	@Override
	public void
	addListener(
		TRTrackerServerListener	l )
	{
		listeners.add( l );
	}

	@Override
	public void
	removeListener(
		TRTrackerServerListener	l )
	{
		listeners.remove(l);
	}

	@Override
	public void
	addListener2(
		TRTrackerServerListener2	l )
	{
		listeners2.add( l );
	}

	@Override
	public void
	removeListener2(
		TRTrackerServerListener2	l )
	{
		listeners2.remove(l);
	}

	@Override
	public void
	addAuthenticationListener(
		TRTrackerServerAuthenticationListener	l )
	{
		auth_listeners.add( l );
	}

	@Override
	public void
	removeAuthenticationListener(
		TRTrackerServerAuthenticationListener	l )
	{
		auth_listeners.remove(l);
	}

	public void
	preProcess(
		TRTrackerServerPeer			peer,
		TRTrackerServerTorrent		torrent,
		int							type,
		String						request,
		Map							response )

		throws TRTrackerServerException
	{
		if ( request_listeners.size() > 0 ){

				// if this is a scrape then we need to patch up stuff as it may be multi-scrape

			if ( type == TRTrackerServerRequest.RT_SCRAPE ){

				try{
					int	request_pos = 10;

					while( true ){

						int	p = request.indexOf( "info_hash=", request_pos );

						String	bit;

						if ( p == -1 ){

							if ( request_pos == 10 ){

								break;	// only one entry, nothing to do
							}

							bit = request.substring( request_pos );

						}else{

							bit = request.substring( request_pos, p );
						}

						int	pos = bit.indexOf('&');

						String	hash_str = pos==-1?bit:bit.substring(0,pos);

						hash_str = URLDecoder.decode( hash_str, Constants.BYTE_ENCODING );

						byte[]	hash = hash_str.getBytes(Constants.BYTE_ENCODING);

						if ( Arrays.equals( hash, torrent.getHash().getBytes())){

							request = "info_hash=" + bit;

							if ( request.endsWith("&")){

								request = request.substring(0,request.length()-1);
							}

							break;
						}

						if ( p == -1 ){

							break;
						}

						request_pos = p + 10;
					}
				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}
			}

			TRTrackerServerRequestImpl	req = new TRTrackerServerRequestImpl( this, peer, torrent, type, request, response );

			for (int i=0;i<request_listeners.size();i++){

				try{
					((TRTrackerServerRequestListener)request_listeners.elementAt(i)).preProcess( req );

				}catch( TRTrackerServerException e ){

					throw( e );

				}catch( Throwable e ){

					Debug.printStackTrace( e );
				}
			}
		}
	}

	public void
	postProcess(
		TRTrackerServerPeer			peer,
		TRTrackerServerTorrentImpl	torrent,
		int							type,
		String						request,
		Map							response )

		throws TRTrackerServerException
	{
		if ( request_listeners.size() > 0 ){

			TRTrackerServerRequestImpl	req = new TRTrackerServerRequestImpl( this, peer, torrent, type, request, response );

			for (int i=0;i<request_listeners.size();i++){

				try{
					((TRTrackerServerRequestListener)request_listeners.elementAt(i)).postProcess( req );

				}catch( TRTrackerServerException e ){

					throw( e );

				}catch( Throwable e ){

					Debug.printStackTrace( e );
				}
			}
		}
	}

	@Override
	public void
	addRequestListener(
		TRTrackerServerRequestListener	l )
	{
		request_listeners.addElement( l );
	}

	@Override
	public void
	removeRequestListener(
		TRTrackerServerRequestListener	l )
	{
		request_listeners.removeElement(l);
	}

	@Override
	public void
	close()
	{
		TRTrackerServerFactoryImpl.close( this );
	}

	protected abstract void
	closeSupport();

	protected void
	destroySupport()
	{
		destroyed	= true;

		COConfigurationManager.removeListener( config_listener );
	}
}
