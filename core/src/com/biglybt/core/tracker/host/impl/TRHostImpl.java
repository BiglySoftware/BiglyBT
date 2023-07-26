/*
 * File    : TRHostImpl.java
 * Created : 24-Oct-2003
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

package com.biglybt.core.tracker.host.impl;

/**
 * @author parg
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentException;
import com.biglybt.core.tracker.client.*;
import com.biglybt.core.tracker.host.*;
import com.biglybt.core.tracker.server.*;
import com.biglybt.core.tracker.util.TRTrackerUtils;
import com.biglybt.core.util.*;

public class
TRHostImpl
	implements 	TRHost, TRTrackerAnnouncerFactoryListener,
				TRTrackerServerListener2, TRTrackerServerListener,
				TRTrackerServerFactoryListener,
				TRTrackerServerRequestListener, TRTrackerServerAuthenticationListener
{
	private static final LogIDs LOGID = LogIDs.TRACKER;
	private static final int URL_DEFAULT_PORT		= 80;	// port to use if none in announce URL
	private static final int URL_DEFAULT_PORT_SSL	= 443;	// port to use if none in announce URL

	public static final int STATS_PERIOD_SECS		= 60;
	private static final int TICK_PERIOD_SECS			= 10;
	private static final int TICKS_PER_STATS_PERIOD	= STATS_PERIOD_SECS/TICK_PERIOD_SECS;

	private static TRHostImpl	singleton;
	private static final AEMonitor 	class_mon 	= new AEMonitor( "TRHost:class" );

	private TRHostConfigImpl		config;

	private final Hashtable				server_map 	= new Hashtable();

	final List	host_torrents			= new ArrayList();
	private final Map	host_torrent_hash_map	= new HashMap();

	private final Map	host_torrent_map		= new HashMap();
	private final Map	tracker_client_map		= new HashMap();

	private static final int LDT_TORRENT_ADDED			= 1;
	private static final int LDT_TORRENT_REMOVED		= 2;
	private static final int LDT_TORRENT_CHANGED		= 3;

	private final ListenerManager<TRHostListener>	listeners 	= ListenerManager.createAsyncManager(
		"TRHost:ListenDispatcher",
		new ListenerManagerDispatcher<TRHostListener>()
		{
			@Override
			public void
			dispatch(
				TRHostListener	_listener,
				int				type,
				Object			value )
			{
				TRHostListener	target = (TRHostListener)_listener;

				if ( type == LDT_TORRENT_ADDED ){

					target.torrentAdded((TRHostTorrent)value);

				}else if ( type == LDT_TORRENT_REMOVED ){

					target.torrentRemoved((TRHostTorrent)value);

				}else if ( type == LDT_TORRENT_CHANGED ){

					target.torrentChanged((TRHostTorrent)value);
				}
			}
		});

	private final CopyOnWriteList<TRHostListener2>	listeners2 = new CopyOnWriteList<>();

	private static boolean host_add_announce_urls;

	static{
		COConfigurationManager.addAndFireParameterListener(
				"Tracker Host Add Our Announce URLs",
				new ParameterListener()
				{
					@Override
					public void
					parameterChanged(
						String name )
					{
						host_add_announce_urls = COConfigurationManager.getBooleanParameter( name );
					}
				});
	}

	private final List<TRHostAuthenticationListener>	auth_listeners		= new ArrayList<>();

	private boolean	server_factory_listener_added;

	protected final AEMonitor this_mon 	= new AEMonitor( "TRHost" );

	private volatile boolean	closed;

	public static TRHost
	create()
	{
		try{
			class_mon.enter();

			if ( singleton == null ){

				singleton = new TRHostImpl();
			}

			return( singleton );

		}finally{

			class_mon.exit();
		}
	}

	protected
	TRHostImpl()
	{
			// we need to synchronize this so that the async (possible) establishment of
			// a server within the stats loop (to deal with public trackers with no locally
			// hosted torrents) doesn't get ahead of the reading of persisted torrents
			// If we allow the server to start early then it can potentially receive an
			// announce/scrape and result in the creation of an "external" torrent when
			// it should really be using an existing torrent

		try{
			this_mon.enter();

			config = new TRHostConfigImpl(this);

			TRTrackerAnnouncerFactory.addListener( this );

			Thread t = new AEThread("TRHost::stats.loop")
						{
							private int	tick_count = 0;

							private final Set	failed_ports = new HashSet();

							@Override
							public void
							runSupport()
							{
								while(true){

									try{

										URL[][]	url_sets = TRTrackerUtils.getAnnounceURLs();

										for (int i=0;i<url_sets.length;i++){

											URL[]	urls = url_sets[i];

											for (int j=0;j<urls.length;j++){

												URL	url = urls[j];

												int port = url.getPort();

												if ( port == -1 ){

													port = url.getDefaultPort();
												}

												String	protocol = url.getProtocol().toLowerCase();

												try{
													if ( protocol.equals( "http" )){

														startServer( TRTrackerServerFactory.PR_TCP, port, false );

													}else if ( protocol.equals( "udp" )){

														startServer( TRTrackerServerFactory.PR_UDP, port, false );

													}else if ( protocol.equals( "https" )){

														startServer( TRTrackerServerFactory.PR_TCP, port, true );

													}else{

														Debug.out( "Unknown protocol '" + protocol + "'" );
													}

												}catch( Throwable e ){

													Integer port_i = new Integer(port);

													if ( !failed_ports.contains(port_i)){

														failed_ports.add( port_i );

														Logger.log(
																new LogEvent(LOGID,
																"Tracker Host: failed to start server", e));
													}
												}
											}
										}

										Thread.sleep( TICK_PERIOD_SECS*1000 );

										if ( closed ){

											break;
										}

										if ( tick_count % TICKS_PER_STATS_PERIOD == 0 ){

											try{
												this_mon.enter();

												for (int i=0;i<host_torrents.size();i++){

													TRHostTorrent	ht = (TRHostTorrent)host_torrents.get(i);

													if ( ht instanceof TRHostTorrentHostImpl ){

														((TRHostTorrentHostImpl)ht).updateStats();

													}else{

														((TRHostTorrentPublishImpl)ht).updateStats();

													}
												}
											}finally{

												this_mon.exit();
											}

											config.saveConfig( true );

										}else{

											config.saveConfig( false );
										}

									}catch( InterruptedException e ){

										Debug.printStackTrace( e );

										break;
									}finally{

										tick_count++;
									}
								}
							}
						};

			t.setDaemon(true);

				// try to ensure that the tracker stats are collected reasonably
				// regularly

			t.setPriority( Thread.MAX_PRIORITY -1);

			t.start();

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	initialise(
		TRHostTorrentFinder	finder )
	{
		config.loadConfig( finder );
	}

	@Override
	public String
	getName()
	{
		return( TRTrackerServer.DEFAULT_NAME );
	}

	@Override
	public TRHostTorrent
	hostTorrent(
		TOTorrent		torrent,
		boolean			persistent,
		boolean			passive )

		throws TRHostException
	{
		return( addTorrent( torrent, TRHostTorrent.TS_STARTED, persistent, passive, SystemTime.getCurrentTime() ));
	}

	@Override
	public TRHostTorrent
	publishTorrent(
		TOTorrent		torrent )

		throws TRHostException
	{
		return( addTorrent( torrent, TRHostTorrent.TS_PUBLISHED, true, false, SystemTime.getCurrentTime()));
	}

	protected TRHostTorrent
	addTorrent(
		TOTorrent		torrent,
		int				state,
		boolean			persistent,
		boolean			passive,
		long			date_added )

		throws TRHostException
	{
		try{
			this_mon.enter();

				// non-persistent additions should know what they're doing regarding
				// announce URL

			if ( persistent && state != TRHostTorrent.TS_PUBLISHED ){

				if ( host_add_announce_urls ){

					addTrackerAnnounce( torrent );
				}
			}

			TRHostTorrent	ht = lookupHostTorrent( torrent );

			if ( ht != null ){

				// check that this isn't the explicit publish/host of a torrent already there
				// as an external torrent. If so then just replace the torrent

				try{

					ht = lookupHostTorrentViaHash( torrent.getHash());

					if ( ht instanceof TRHostTorrentHostImpl ){

						TRHostTorrentHostImpl hti = (TRHostTorrentHostImpl)ht;

						if ( hti.getTorrent() != torrent ){

							hti.setTorrentInternal( torrent );

							if ( persistent && !hti.isPersistent()){

								hti.setPersistent( true );
							}

							if ( passive && !hti.isPassive()){

								hti.setPassive( true );
							}

							if ( state != TRHostTorrent.TS_PUBLISHED ){

								startHosting( hti );

								if ( state == TRHostTorrent.TS_STARTED ){

									hti.start();
								}
							}

							listeners.dispatch( LDT_TORRENT_CHANGED, ht );
						}
					}
				}catch( TOTorrentException e ){

					Debug.printStackTrace( e );
				}

				return( ht );
			}

			int		port;
			boolean	ssl;
			int		protocol	= TRTrackerServerFactory.PR_TCP;

			if ( state == TRHostTorrent.TS_PUBLISHED ){

				port = COConfigurationManager.getIntParameter("Tracker Port", TRHost.DEFAULT_PORT );

				ssl	= false;
			}else{

				URL	announce_url = torrent.getAnnounceURL();

				String	protocol_str = announce_url.getProtocol();

				ssl = protocol_str.equalsIgnoreCase("https");

				if ( protocol_str.equalsIgnoreCase("udp")){

					protocol = TRTrackerServerFactory.PR_UDP;

				}else if ( TorrentUtils.isDecentralised( torrent )){

					protocol = TRTrackerServerFactory.PR_DHT;
				}

				boolean force_external = COConfigurationManager.getBooleanParameter("Tracker Port Force External");

				port = announce_url.getPort();

				if ( force_external ){

					String 	tracker_ip 		= COConfigurationManager.getStringParameter("Tracker IP", "");

					if ( 	tracker_ip.length() > 0 &&
							!announce_url.getHost().equalsIgnoreCase( tracker_ip )){

						if ( ssl ){

							port = COConfigurationManager.getIntParameter("Tracker Port SSL", TRHost.DEFAULT_PORT_SSL );

						}else{

							port = COConfigurationManager.getIntParameter("Tracker Port", TRHost.DEFAULT_PORT );

						}
					}
				}

				if ( port == -1 ){

					port = ssl?URL_DEFAULT_PORT_SSL:URL_DEFAULT_PORT;
				}
			}

			TRTrackerServer server = startServer( protocol, port, ssl );

			TRHostTorrent host_torrent;

			if ( state == TRHostTorrent.TS_PUBLISHED ){

				TRHostTorrentPublishImpl new_torrent = new TRHostTorrentPublishImpl( this, torrent, date_added );

				new_torrent.setPersistent( persistent );

				host_torrent	= new_torrent;
			}else{

				TRHostTorrentHostImpl	new_torrent = new TRHostTorrentHostImpl( this, server, torrent, port, date_added );

				new_torrent.setPersistent( persistent );

				new_torrent.setPassive( passive );

				host_torrent	= new_torrent;
			}

			host_torrents.add( host_torrent );

			try{
				host_torrent_hash_map.put( new HashWrapper( torrent.getHash()), host_torrent );

			}catch( TOTorrentException e ){

				Debug.printStackTrace( e );
			}

			host_torrent_map.put( torrent, host_torrent );

			if ( state != TRHostTorrent.TS_PUBLISHED ){

				startHosting((TRHostTorrentHostImpl)host_torrent );

				if ( state == TRHostTorrent.TS_STARTED ){

					host_torrent.start();
				}

					// if not persistent, see if we can recover the stats

				if ( !persistent ){

					config.recoverStats( (TRHostTorrentHostImpl)host_torrent );
				}
			}

			listeners.dispatch( LDT_TORRENT_ADDED, host_torrent );

			config.saveRequired();

			return( host_torrent );

		}finally{

			this_mon.exit();
		}
	}

	protected void
	torrentUpdated(
		TRHostTorrentHostImpl hti )
	{
		int state = hti.getStatus();

		if ( state != TRHostTorrent.TS_PUBLISHED ){

			startHosting( hti );

			if ( state == TRHostTorrent.TS_STARTED ){

				hti.start();
			}
		}

		listeners.dispatch( LDT_TORRENT_CHANGED, hti );
	}

	@Override
	public InetAddress
	getBindIP()
	{
		return( null );
	}

	protected TRTrackerServer
	startServer(
		int		protocol,
		int		port,
		boolean	ssl )

		throws TRHostException
	{
		try{
			this_mon.enter();

			String	key = ""+protocol+ ":" + port;

			TRTrackerServer	server = (TRTrackerServer)server_map.get( key );

			if ( server == null ){

				try{

					if ( ssl ){

						server = TRTrackerServerFactory.createSSL( "tracker", protocol, port, true, true );

					}else{

						server = TRTrackerServerFactory.create( "tracker", protocol, port, true, true );
					}

					server_map.put( key, server );

					if ( auth_listeners.size() > 0 ){

						server.addAuthenticationListener( this );
					}

					server.addListener( this );
					server.addListener2( this );

				}catch( TRTrackerServerException e ){

					throw( new TRHostException( "startServer failed", e ));
				}
			}

			return( server );

		}finally{

			this_mon.exit();
		}
	}

	protected TRHostTorrent
	lookupHostTorrent(
		TOTorrent	torrent )
	{
	  if (torrent == null)
	    return null;

		try{
			return((TRHostTorrent)host_torrent_hash_map.get( torrent.getHashWrapper()));

		}catch( TOTorrentException e ){

			Debug.printStackTrace( e );
		}

		return( null );
	}

	protected void
	startHosting(
		TRHostTorrentHostImpl	host_torrent )
	{
		TOTorrent	torrent = host_torrent.getTorrent();

		TRTrackerAnnouncer tc = (TRTrackerAnnouncer)tracker_client_map.get( torrent );

		if ( tc != null ){

			startHosting( host_torrent, tc );
		}
	}

	protected void
	startHosting(
		TRTrackerAnnouncer	tracker_client )
	{
		TRHostTorrent	host_torrent = (TRHostTorrent)host_torrent_map.get( tracker_client.getTorrent());

		if ( host_torrent instanceof TRHostTorrentHostImpl ){

			startHosting( (TRHostTorrentHostImpl)host_torrent, tracker_client );
		}
	}

	protected void
	startHosting(
		TRHostTorrentHostImpl	host_torrent,
		final TRTrackerAnnouncer 	tracker_client )
	{
		final TOTorrent	torrent = host_torrent.getTorrent();

			// set the ip override so that we announce ourselves to other peers via the
			// real external address, not the local one used to connect to the tracker

		URL	announce = torrent.getAnnounceURL();

		if ( host_add_announce_urls ){

			tracker_client.setIPOverride( announce.getHost());

		}else{

				// prolly a backup tracker, we only want to override the IP if we're hosting it

			if ( TRTrackerUtils.isHosting( announce )){

				tracker_client.setIPOverride( announce.getHost());

			}
		}

			// hook into the client so that when the announce succeeds after the refresh below
			// we can force a rescrape to pick up the new status

		TRTrackerAnnouncerListener	listener =
			new TRTrackerAnnouncerListener()
			{
				@Override
				public void
				receivedTrackerResponse(
					TRTrackerAnnouncerRequest	request,
					TRTrackerAnnouncerResponse	response	)
				{
					try{
							// Changed this to not force an actual scrape as we will have updated
							// the scrape value in the announce-code already and a synchronous forced scrape
							// can block things for a while and creates unnecessary load
						
						TRTrackerScraperFactory.getSingleton().scrape( torrent, false );

					}finally{

						tracker_client.removeListener( this );
					}
				}

				@Override
				public void
				urlChanged(
					TRTrackerAnnouncer	announcer,
					URL					old_url,
					URL					new_url,
					boolean				explicit )
				{
				}

				@Override
				public void
				urlRefresh()
				{
				}
			};

		tracker_client.addListener(listener);

		tracker_client.refreshListeners();
	}

	protected void
	remove(
		TRHostTorrent	host_torrent )
	{
		try{
			this_mon.enter();

			if ( !host_torrents.contains( host_torrent )){

				return;
			}

			host_torrents.remove( host_torrent );

			TOTorrent	torrent = host_torrent.getTorrent();

			try{
				host_torrent_hash_map.remove(new HashWrapper(torrent.getHash()));

			}catch( TOTorrentException e ){

				Debug.printStackTrace( e );
			}

			host_torrent_map.remove( torrent );

			if ( host_torrent instanceof TRHostTorrentHostImpl ){

				stopHosting((TRHostTorrentHostImpl)host_torrent );
			}

			listeners.dispatch( LDT_TORRENT_REMOVED, host_torrent );

				// this'll get saved sometime soon anyway - performance problems
				// here when removing multiple torrents from a large set (e.g. 1000)

			// config.saveConfig();

		}finally{

			this_mon.exit();
		}
	}

	protected void
	stopHosting(
		TRHostTorrentHostImpl	host_torrent )
	{
		TOTorrent	torrent = host_torrent.getTorrent();

		TRTrackerAnnouncer tc = (TRTrackerAnnouncer)tracker_client_map.get( torrent );

		if ( tc != null ){

			stopHosting( host_torrent, tc );
		}
	}

	protected void
	stopHosting(
		TRTrackerAnnouncer	tracker_client )
	{
		TRHostTorrent	host_torrent = (TRHostTorrent)host_torrent_map.get( tracker_client.getTorrent());

		if ( host_torrent instanceof TRHostTorrentHostImpl ){

				// we only "connect" the announcer and the hosted torrent if it isn't passive. This allows
				// us to make a torrent passive without losing the tracker stats by
				// 1) making it passive
				// 2) removing the Download

			//if ( !host_torrent.isPassive()){

				stopHosting( (TRHostTorrentHostImpl)host_torrent, tracker_client );
			//}
		}
	}

	final AsyncDispatcher dispatcher = new AsyncDispatcher( "TRHost:stopHosting" );

	protected void
	stopHosting(
		final TRHostTorrentHostImpl		host_torrent,
		final TRTrackerAnnouncer 		tracker_client )
	{
			// unfortunately a lot of the "stop" operations that occur when a tracker client
			// connection is closed happen async. In particular the "stopped" message to the
			// tracker. Hence, if we switch the URL back here the "stopped" doesn't get
			// through.

			// for the moment stick a delay in to allow any async stuff to complete

		SimpleTimer.addEvent(
			"StopHosting",
			SystemTime.getOffsetTime( 2500 ),
			new TimerEventPerformer() {

				@Override
				public void
				perform(TimerEvent event)
				{
					dispatcher.dispatch(
						new AERunnable()
						{
							@Override
							public void
							runSupport()
							{
								try{
									this_mon.enter();

										// got to look up the host torrent again as may have been
										// removed and re-added

									TRHostTorrent	ht = lookupHostTorrent( host_torrent.getTorrent());

										// check it's still in stopped state and hasn't been restarted

									if ( ht == null ||
											( 	ht == host_torrent &&
											 	ht.getStatus() == TRHostTorrent.TS_STOPPED )){

										tracker_client.clearIPOverride();
									}
								}finally{

									this_mon.exit();
								}
							}
						});
				}
			});
	}

	protected TRTrackerAnnouncer
	getTrackerClient(
		TRHostTorrent host_torrent )
	{
		try{
			this_mon.enter();

			return((TRTrackerAnnouncer)tracker_client_map.get( host_torrent.getTorrent()));

		}finally{

			this_mon.exit();
		}
	}

	protected void
	hostTorrentStateChange(
		TRHostTorrent host_torrent )
	{
		try{
			this_mon.enter();

			TOTorrent	torrent = host_torrent.getTorrent();

			TRTrackerAnnouncer tc = (TRTrackerAnnouncer)tracker_client_map.get( torrent );

			if ( tc != null ){

				tc.refreshListeners();
			}

			// config will get saved soon anyway (periodic or on closedown) - perf issues
			// here with multiple torrent removal if we save each time
			// config.saveConfig();

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public TRHostTorrent[]
	getTorrents()
	{
		try{
			this_mon.enter();

			TRHostTorrent[]	res = new TRHostTorrent[host_torrents.size()];

			host_torrents.toArray( res );

			return( res );

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public int 
	getTorrentCount()
	{
		try{
			this_mon.enter();
			
			return( host_torrents.size());
			
		}finally{

			this_mon.exit();
		}
	}
	
	@Override
	public void
	clientCreated(
		TRTrackerAnnouncer		client )
	{
		try{
			this_mon.enter();

			tracker_client_map.put( client.getTorrent(), client );

			startHosting( client );

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	clientDestroyed(
		TRTrackerAnnouncer		client )
	{
		try{
			this_mon.enter();

			tracker_client_map.remove( client.getTorrent());

			stopHosting( client );

		}finally{

			this_mon.exit();
		}
	}

	protected TRHostTorrent
	lookupHostTorrentViaHash(
		byte[]		hash )
	{
		return((TRHostTorrent)host_torrent_hash_map.get(new HashWrapper(hash)));
	}

		// reports from TRTrackerServer regarding state of hashes
		// if we get a "permitted" event for a torrent we know nothing about
		// the the server is allowing public hosting and this is a new hash
		// create an 'external' entry for it

	@Override
	public boolean
	permitted(
		String		originator,
		byte[]		hash,
		boolean		explicit  )
	{
		try{
			this_mon.enter();

			TRHostTorrent ht = lookupHostTorrentViaHash( hash );

			if ( ht != null ){

				if ( !explicit ){

					if ( ht.getStatus() != TRHostTorrent.TS_STARTED ){

						return( false );
					}
				}

				return( true );
			}

			addExternalTorrent( hash, TRHostTorrent.TS_STARTED, SystemTime.getCurrentTime());

			return( true );

		}finally{

			this_mon.exit();
		}
	}

	protected void
	addExternalTorrent(
		byte[]		hash,
		int			state,
		long		date_added )
	{
		try{
			this_mon.enter();

			if ( lookupHostTorrentViaHash( hash ) != null ){

				return;
			}

			String 	tracker_ip 		= COConfigurationManager.getStringParameter("Tracker IP", "127.0.0.1");

				// external torrents don't care whether ssl or not so just assume non-ssl for simplicity

			int port = COConfigurationManager.getIntParameter("Tracker Port", TRHost.DEFAULT_PORT );

			try{
				TOTorrent	external_torrent = new TRHostExternalTorrent(hash, new URL( "http://" + UrlUtils.convertIPV6Host(tracker_ip) + ":" + port + "/announce"));

				addTorrent( external_torrent, state, true, false, date_added );

			}catch( Throwable e ){

				Debug.printStackTrace( e );
			}

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public boolean
	denied(
		byte[]		hash,
		boolean		permitted )
	{
		return( true );
	}

	@Override
	public boolean
	handleExternalRequest(
		InetSocketAddress	client_address,
		String				user,
		String				url,
		URL					absolute_url,
		String				header,
		InputStream			is,
		OutputStream		os,
		AsyncController		async )

		throws IOException
	{
		List<TRHostListener>	listeners_copy = listeners.getListenersCopy();

		for (int i=0;i<listeners_copy.size();i++){

			TRHostListener	listener = listeners_copy.get(i);

			try{
				if ( listener.handleExternalRequest( client_address, user, url, absolute_url, header, is, os, async )){

					return( true );
				}
			}catch( Throwable e ){

				Debug.out( e );
			}
		}

		return( false );
	}

	@Override
	public boolean
	handleExternalRequest(
		ExternalRequest 	request )

			throws IOException
	{
		Iterator<TRHostListener2> it = listeners2.iterator();

		while( it.hasNext()){

			try{
				if ( it.next().handleExternalRequest(request)){

					return( true );
				}
			}catch( Throwable e ){

				Debug.out( e );
			}
		}

		return( false );
	}

	@Override
	public TRHostTorrent
	getHostTorrent(
		TOTorrent		torrent )
	{
		return( lookupHostTorrent( torrent ));
	}

	/**
	 * Add and fire listener for each torrent already hosted
	 */
	@Override
	public void
	addListener(
		TRHostListener	l )
	{
		try{
			this_mon.enter();

			listeners.addListener( l );

			for (int i=0;i<host_torrents.size();i++){

				listeners.dispatch( l, LDT_TORRENT_ADDED, host_torrents.get(i));
			}
		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	removeListener(
		TRHostListener	l )
	{
		listeners.removeListener( l );
	}

	@Override
	public void
	addListener2(
		TRHostListener2	l )
	{
		listeners2.add(l);
	}

	@Override
	public void
	removeListener2(
		TRHostListener2	l )
	{
		listeners2.remove(l);
	}

	protected void
	torrentListenerRegistered()
	{
		try{
			this_mon.enter();

			if ( !server_factory_listener_added ){

				server_factory_listener_added	= true;

				TRTrackerServerFactory.addListener( this );
			}
		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	serverCreated(
		TRTrackerServer	server )
	{
		server.addRequestListener(this);
	}

	@Override
	public void
	serverDestroyed(
		TRTrackerServer	server )
	{
		server.removeRequestListener(this);
	}

	@Override
	public void
	preProcess(
		TRTrackerServerRequest	request )

		throws TRTrackerServerException
	{
		if ( 	request.getType() 	== TRTrackerServerRequest.RT_ANNOUNCE  ||
				request.getType() 	== TRTrackerServerRequest.RT_SCRAPE ){

			TRTrackerServerTorrent ts_torrent = request.getTorrent();

			HashWrapper	hash_wrapper = ts_torrent.getHash();

			TRHostTorrent h_torrent = lookupHostTorrentViaHash( hash_wrapper.getHash());

			if ( h_torrent != null ){

				TRHostTorrentRequest	req = new TRHostTorrentRequestImpl( h_torrent, new TRHostPeerHostImpl(request.getPeer()), request );

				try{
					if ( h_torrent instanceof TRHostTorrentHostImpl ){

						((TRHostTorrentHostImpl)h_torrent).preProcess( req );
					}else{

						((TRHostTorrentPublishImpl)h_torrent).preProcess( req );
					}
				}catch( TRHostException e ){

					throw( new TRTrackerServerException( e.getMessage(), e ));

				}catch( Throwable e ){

					throw( new TRTrackerServerException( "Pre-process fails", e ));
				}
			}
		}
	}

	@Override
	public void
	postProcess(
		TRTrackerServerRequest	request )

		throws TRTrackerServerException
	{
		if ( 	request.getType() 	== TRTrackerServerRequest.RT_ANNOUNCE  ||
				request.getType() 	== TRTrackerServerRequest.RT_SCRAPE ){

			TRTrackerServerTorrent ts_torrent = request.getTorrent();

				// can be null for multi-hash scrapes... should fix this sometime I guess

			if ( ts_torrent != null ){

				HashWrapper	hash_wrapper = ts_torrent.getHash();

				TRHostTorrent h_torrent = lookupHostTorrentViaHash( hash_wrapper.getHash());

				if ( h_torrent != null ){

					TRHostTorrentRequest	req = new TRHostTorrentRequestImpl( h_torrent, new TRHostPeerHostImpl(request.getPeer()), request );

					try{
						if ( h_torrent instanceof TRHostTorrentHostImpl ){

							((TRHostTorrentHostImpl)h_torrent).postProcess( req );
						}else{

							((TRHostTorrentPublishImpl)h_torrent).postProcess( req );
						}
					}catch( TRHostException e ){

						throw( new TRTrackerServerException( "Post process fails", e ));
					}
				}
			}
		}
	}

	@Override
	public void
	close()
	{
		closed	= true;

		config.saveConfig( true );
	}

	@Override
	public boolean
	authenticate(
		String		headers,
		URL			resource,
		String		user,
		String		password )
	{
		for (int i=0;i<auth_listeners.size();i++){

			try{
				boolean res = auth_listeners.get(i).authenticate( headers, resource, user, password );

				if ( res ){

					return(true );
				}
			}catch( Throwable e ){

				Debug.printStackTrace( e );
			}
		}

		return( false );
	}

	@Override
	public byte[]
	authenticate(
		URL			resource,
		String		user )
	{
		for (int i=0;i<auth_listeners.size();i++){

			try{
				byte[] res = auth_listeners.get(i).authenticate( resource, user );

				if ( res != null ){

					return( res );
				}
			}catch( Throwable e ){

				Debug.printStackTrace( e );
			}
		}

		return( null );
	}

	@Override
	public void
	addAuthenticationListener(
		TRHostAuthenticationListener	l )
	{
		try{
			this_mon.enter();

			auth_listeners.add(l);

			if ( auth_listeners.size() == 1 ){

				Iterator it = server_map.values().iterator();

				while( it.hasNext()){

					((TRTrackerServer)it.next()).addAuthenticationListener( this );
				}
			}
		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	removeAuthenticationListener(
		TRHostAuthenticationListener	l )
	{
		try{
			this_mon.enter();

			auth_listeners.remove(l);

			if ( auth_listeners.size() == 0 ){

				Iterator it = server_map.values().iterator();

				while( it.hasNext()){

					((TRTrackerServer)it.next()).removeAuthenticationListener( this );
				}
			}
		}finally{

			this_mon.exit();
		}
	}

		// see comment in TRHostTorrentHost impl for reason for this delegation + monitor
		// acquisition

	protected void
	startTorrent(
		TRHostTorrentHostImpl	torrent )
	{
		try{
			this_mon.enter();

			torrent.startSupport();

		}finally{

			this_mon.exit();
		}
	}

	protected void
	stopTorrent(
		TRHostTorrentHostImpl	torrent )
	{
		try{
			this_mon.enter();

			torrent.stopSupport();

		}finally{

			this_mon.exit();
		}
	}

	protected void
	addTrackerAnnounce(
		TOTorrent	torrent )
	{
		if ( TorrentUtils.isDecentralised( torrent )){

			return;
		}

			// ensure that the tracker's announce details are in the torrent

		URL[][]	url_sets = TRTrackerUtils.getAnnounceURLs();

		if ( url_sets.length == 0 ){

				// fall back to decentralised, no tracker defined

			TorrentUtils.setDecentralised( torrent );

		}else{

			URL[]	primary_urls = url_sets[0];

				// backwards so that they end up in right order

			for (int i=primary_urls.length-1;i>=0;i--){

				String	url_str = primary_urls[i].toString();

				if ( TorrentUtils.announceGroupsContainsURL( torrent, url_str )){

					TorrentUtils.announceGroupsSetFirst( torrent, url_str );

				}else{

					TorrentUtils.announceGroupsInsertFirst( torrent, url_str );
				}
			}
		}
	}
}
