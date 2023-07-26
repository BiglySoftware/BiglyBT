/*
 * Created on Dec 4, 2009
 * Created by Paul Gardner
 *
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
 */


package com.biglybt.core.tracker.client.impl;

import java.net.URL;
import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.peer.PEPeerSource;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentAnnounceURLSet;
import com.biglybt.core.torrent.TOTorrentException;
import com.biglybt.core.tracker.TrackerPeerSource;
import com.biglybt.core.tracker.client.*;
import com.biglybt.core.tracker.client.impl.bt.TRTrackerBTAnnouncerImpl;
import com.biglybt.core.tracker.client.impl.dht.TRTrackerDHTAnnouncerImpl;
import com.biglybt.core.util.*;
import com.biglybt.pif.download.DownloadAnnounceResult;

public class
TRTrackerAnnouncerMuxer
	extends TRTrackerAnnouncerImpl
{
	private static final int ACT_CHECK_INIT_DELAY				= 2500;
	private static final int ACT_CHECK_INTERIM_DELAY			= 10*1000;
	private static final int ACT_CHECK_IDLE_DELAY				= 30*1000;
	private static final int ACT_CHECK_SEEDING_SHORT_DELAY		= 60*1000;
	private static final int ACT_CHECK_SEEDING_LONG_DELAY		= 3*60*1000;
	private static final int ACT_CHECK_NOT_SMART_DELAY			= 60*1000;

	private static boolean	smart_activation;
	
	static{
		
		COConfigurationManager.addAndFireParameterListener(
			ConfigKeys.Tracker.BCFG_TRACKER_CLIENT_SMART_ACTIVATION,
			(n)->{
				smart_activation = COConfigurationManager.getBooleanParameter( n );
			});
	}

	private TRTrackerAnnouncerFactory.DataProvider		f_provider;
	private boolean										is_manual;

	private final long				create_time = SystemTime.getMonotonousTime();

	private final CopyOnWriteList<TRTrackerAnnouncerHelper>	announcers 	= new CopyOnWriteList<>();
	private final Set<TRTrackerAnnouncerHelper>				activated	= new HashSet<>();
	private long										last_activation_time;
	private final Set<String>									failed_urls	= new HashSet<>();

	private volatile TimerEvent					event;

	private TRTrackerAnnouncerDataProvider		provider;
	private String								ip_override;
	private boolean								complete;
	private boolean								stopped;
	private boolean								destroyed;

	private String[]							current_networks;

	private TRTrackerAnnouncerHelper			last_best_active;
	private long								last_best_active_set_time;

	final Map<String,StatusSummary>			recent_responses = new HashMap<>();

	private TRTrackerAnnouncerResponse			last_response_informed;


	protected
	TRTrackerAnnouncerMuxer(
		TOTorrent								_torrent,
		TRTrackerAnnouncerFactory.DataProvider	_f_provider,
		boolean									_manual )

		throws TRTrackerAnnouncerException
	{
		super( _torrent );

		try{
			last_response_informed = new TRTrackerAnnouncerResponseImpl( null, _torrent.getHashWrapper(), TRTrackerAnnouncerResponse.ST_OFFLINE, TRTrackerAnnouncer.REFRESH_MINIMUM_SECS, "Initialising" );

		}catch( TOTorrentException e ){

			Logger.log(new LogEvent( _torrent, LOGID, "Torrent hash retrieval fails", e));

			throw( new TRTrackerAnnouncerException( "TRTrackerAnnouncer: URL encode fails"));
		}

		is_manual 	= _manual;
		f_provider	= _f_provider;

		split( true );
	}

	protected void
	split(
		boolean	first_time )

		throws TRTrackerAnnouncerException
	{
		String[]	networks = f_provider==null?null:f_provider.getNetworks();

		boolean	force_recreate = false;

		if ( !first_time ){

			if ( current_networks != networks ){

				if ( current_networks == null || networks == null ){

					force_recreate = true;

				}else{

					if ( networks.length != current_networks.length ){

						force_recreate = true;

					}else{

						for ( String net1: current_networks ){

							boolean	match = false;

							for ( String net2: networks ){

								if ( net1 == net2 ){

									match = true;
								}
							}

							if ( !match ){

								force_recreate = true;

								break;
							}
						}
					}
				}
			}
		}

		current_networks = networks;

		TRTrackerAnnouncerHelper to_activate = null;

		synchronized( this ){

			if ( stopped || destroyed ){

				return;
			}

			TOTorrent torrent = getTorrent();

			TOTorrentAnnounceURLSet[]	sets = torrent.getAnnounceURLGroup().getAnnounceURLSets();

			if ( sets.length == 0 ){

				sets = new TOTorrentAnnounceURLSet[]{ torrent.getAnnounceURLGroup().createAnnounceURLSet( new URL[]{ torrent.getAnnounceURL()})};

			}else{

				List<TOTorrentAnnounceURLSet> s_list = new ArrayList<>();

					// tidy up decentralised into its own group, remove nulls
				
				boolean decentralised_done = false;
				
				for ( int i=0;i<sets.length;i++ ){

					TOTorrentAnnounceURLSet set = sets[i];

					URL[] urls = set.getAnnounceURLs().clone();	// clone as we can modify array below

					boolean has_nulls = false;
					
					for ( int j=0; j<urls.length; j++){

						URL u = urls[j];

						if ( u == null ){
							
							has_nulls = true;
							
						}else{
							
							if ( TorrentUtils.isDecentralised( u )){
					
								if ( decentralised_done ){
									
									urls[j] = null;
									
									has_nulls = true;
									
								}else{
									
									decentralised_done = true;
									
									if ( urls.length == 1 ){
										
										s_list.add( set );	// fine as it is
										
										set = null;	// indicated processed
										
									}else{
										
										urls[j] = null;
									
										has_nulls = true;
										
										s_list.add( torrent.getAnnounceURLGroup().createAnnounceURLSet( new URL[]{ u } ));	
									}
								}
							}
						}
					}
					
					if ( set != null ){
						
						if ( has_nulls ){
							
							List<URL> l_u = new ArrayList<>( urls.length );
							
							for ( URL u: urls ){
								
								if ( u != null ){
									
									l_u.add( u );
								}
							}
							
							int num = l_u.size();
							
							if ( num > 0 ){
								
								s_list.add( torrent.getAnnounceURLGroup().createAnnounceURLSet( l_u.toArray( new URL[num] )));

							}
						}else{
							
							s_list.add( set );
						}
					}
				}
				
				sets = s_list.toArray( new TOTorrentAnnounceURLSet[ s_list.size() ]);
			}

			List<TOTorrentAnnounceURLSet[]>	new_sets = new ArrayList<>();

			if ( is_manual || sets.length < 2 ){

				new_sets.add( sets );

			}else{

				List<TOTorrentAnnounceURLSet> list = new ArrayList<>(Arrays.asList(sets));

					// often we have http:/xxxx/ and udp:/xxxx/ as separate groups - keep these together

				while( list.size() > 0 ){

					TOTorrentAnnounceURLSet set1 = list.remove(0);

					boolean	done = false;

					URL[] urls1 = set1.getAnnounceURLs();

					if ( urls1.length == 1 ){

						URL url1 = urls1[0];

						String prot1 = url1.getProtocol().toLowerCase();
						String host1	= url1.getHost();

						for (int i=0;i<list.size();i++){

							TOTorrentAnnounceURLSet set2 = list.get(i);

							URL[] urls2 = set2.getAnnounceURLs();

							if ( urls2.length == 1 ){

								URL url2 = urls2[0];

								String prot2 = url2.getProtocol().toLowerCase();
								String host2 = url2.getHost();

								if ( host1.equals( host2 )){

									if (	( prot1.equals( "udp" ) && prot2.startsWith( "http" )) ||
											( prot2.equals( "udp" ) && prot1.startsWith( "http" ))){

										list.remove( i );

										new_sets.add( new TOTorrentAnnounceURLSet[]{ set1, set2 });

										done	= true;
									}
								}
							}
						}
					}

					if ( !done ){

						new_sets.add( new TOTorrentAnnounceURLSet[]{ set1 });
					}
				}
			}

				// work out the difference

			Iterator<TOTorrentAnnounceURLSet[]> ns_it = new_sets.iterator();

				// need to copy list as we modify it and returned list ain't thread safe

			List<TRTrackerAnnouncerHelper> existing_announcers 	= new ArrayList<>(announcers.getList());

			List<TRTrackerAnnouncerHelper> new_announcers 		= new ArrayList<>();

				// first look for unchanged sets

			if ( !force_recreate ){

				while( ns_it.hasNext()){

					TOTorrentAnnounceURLSet[] ns = ns_it.next();

					Iterator<TRTrackerAnnouncerHelper> a_it = existing_announcers.iterator();

					while( a_it.hasNext()){

						TRTrackerAnnouncerHelper a = a_it.next();

						TOTorrentAnnounceURLSet[] os = a.getAnnounceSets();

						if ( same( ns, os )){

							ns_it.remove();
							a_it.remove();

							new_announcers.add( a );

							break;
						}
					}
				}
			}

				// first remove dht ones from the equation

			TRTrackerAnnouncerHelper 	existing_dht_announcer 	= null;
			TOTorrentAnnounceURLSet[]	new_dht_set				= null;

			ns_it = new_sets.iterator();

			while( ns_it.hasNext()){

				TOTorrentAnnounceURLSet[] x = ns_it.next();

				if ( TorrentUtils.isDecentralised( x[0].getAnnounceURLs()[0])){

					new_dht_set = x;

					ns_it.remove();

					break;
				}
			}

			Iterator<TRTrackerAnnouncerHelper>	an_it = existing_announcers.iterator();

			while( an_it.hasNext()){

				TRTrackerAnnouncerHelper a = an_it.next();

				TOTorrentAnnounceURLSet[] x = a.getAnnounceSets();

				if ( TorrentUtils.isDecentralised( x[0].getAnnounceURLs()[0])){

					existing_dht_announcer = a;

					an_it.remove();

					break;
				}
			}

			if ( existing_dht_announcer != null && new_dht_set != null ){

				new_announcers.add( existing_dht_announcer );

			}else if ( existing_dht_announcer != null ){

				activated.remove( existing_dht_announcer );

				existing_dht_announcer.destroy();

			}else if ( new_dht_set != null ){

				TRTrackerAnnouncerHelper a = create( torrent, networks, new_dht_set );

				new_announcers.add( a );
			}

				// create any new ones required

			ns_it = new_sets.iterator();

			while( ns_it.hasNext()){

				TOTorrentAnnounceURLSet[] s = ns_it.next();

				TRTrackerAnnouncerHelper a = create( torrent, networks, s );

				new_announcers.add( a );
			}

				// finally fix up the announcer list to represent the new state

			Iterator<TRTrackerAnnouncerHelper>	a_it = announcers.iterator();

			while( a_it.hasNext()){

				TRTrackerAnnouncerHelper a = a_it.next();

				if ( !new_announcers.contains( a )){

					a_it.remove();

					try{
						if ( 	activated.contains( a ) &&
								torrent.getPrivate() &&
								a instanceof TRTrackerBTAnnouncerImpl ){

							URL url = a.getTrackerURL();

							if ( url != null ){

								forceStop((TRTrackerBTAnnouncerImpl)a, networks, url );
							}
						}
					}finally{

						if (Logger.isEnabled()) {
							Logger.log(new LogEvent(getTorrent(), LOGID, "Deactivating " + getString( a.getAnnounceSets())));
						}

						activated.remove( a );

						a.destroy();
					}
				}
			}

			a_it = new_announcers.iterator();

			while( a_it.hasNext()){

				TRTrackerAnnouncerHelper a = a_it.next();

				if ( !announcers.contains( a )){

					announcers.add( a );
				}
			}

			if ( !is_manual && announcers.size() > 0 ){

				if ( activated.size() == 0 ){

					TRTrackerAnnouncerHelper a = announcers.get(0);

					if (Logger.isEnabled()) {
						Logger.log(new LogEvent(getTorrent(), LOGID, "Activating " + getString( a.getAnnounceSets())));
					}

					activated.add( a );

					last_activation_time = SystemTime.getMonotonousTime();

					if ( provider != null ){

						to_activate = a;
					}
				}

				setupActivationCheck( ACT_CHECK_INIT_DELAY );
			}
		}

		if ( to_activate != null ){

			if ( complete ){

				to_activate.complete( true );

			}else{

				to_activate.update( false );
			}
		}
	}

	protected void
	setupActivationCheck(
		int		delay )
	{
		if ( announcers.size() > activated.size()){

			event = SimpleTimer.addEvent(
				"TRMuxer:check",
				SystemTime.getOffsetTime( delay ),
				new TimerEventPerformer()
				{
					@Override
					public void
					perform(
						TimerEvent event )
					{
						checkActivation( false );
					}
				});
		}
	}

	protected void
	checkActivation(
		boolean		force )
	{
		synchronized( this ){

			int	next_check_delay;

			if ( 	destroyed ||
					stopped ||
					announcers.size() <= activated.size()){

				return;
			}

			if ( provider == null ){

				next_check_delay = ACT_CHECK_INIT_DELAY;

			}else{

				boolean	activate = force;

				if ( smart_activation ){
					
					boolean	seeding = provider.getRemaining() == 0;
	
					if ( seeding && activated.size() > 0 ){
	
							// when seeding we only activate on tracker fail or major lack of connections
							// as normally we rely on downloaders rotating and finding us
	
						int	connected	= provider.getConnectedConnectionCount();
	
						if ( connected < 1 ){
	
							activate = SystemTime.getMonotonousTime() - last_activation_time >= 60*1000;
	
							next_check_delay = ACT_CHECK_SEEDING_SHORT_DELAY;
	
						}else if ( connected < 3 ){
	
							next_check_delay = ACT_CHECK_SEEDING_LONG_DELAY;
	
						}else{
	
							next_check_delay = 0;
						}
					}else{
	
						int	allowed		= provider.getMaxNewConnectionsAllowed("");	// -1 -> unlimited
						int	pending		= provider.getPendingConnectionCount();
						int	connected	= provider.getConnectedConnectionCount();
	
						int	online = 0;
	
						for ( TRTrackerAnnouncerHelper a: activated ){
	
							TRTrackerAnnouncerResponse response = a.getLastResponse();
	
							if ( 	response != null &&
									response.getStatus() == TRTrackerAnnouncerResponse.ST_ONLINE ){
	
								online++;
							}
						}
	
						/*
						System.out.println(
							"checkActivation: announcers=" + announcers.size() +
							", active=" + activated.size() +
							", online=" + online +
							", allowed=" + allowed +
							", pending=" + pending +
							", connected=" + connected +
							", seeding=" + seeding );
						*/
	
						if ( online == 0 ){
	
							activate = true;
	
								// no trackers online, start next and recheck soon
	
							next_check_delay = ACT_CHECK_INIT_DELAY;
	
						}else{
	
							int	potential = connected + pending;
	
							if ( potential < 10 ){
	
									// minimal connectivity
	
								activate = true;
	
								next_check_delay = ACT_CHECK_INIT_DELAY;
	
							}else if ( allowed < 0 || ( allowed >= 5 && pending < 3*allowed/4 )){
	
									// not enough to fulfill our needs
	
								activate = true;
	
								next_check_delay = ACT_CHECK_INTERIM_DELAY;
	
							}else{
									// things look good, recheck in a bit
	
								next_check_delay = ACT_CHECK_IDLE_DELAY;
							}
						}
					}
				}else{
					
					if ( announcers.size() > activated.size()){
						
						activate			= true;
						next_check_delay 	= ACT_CHECK_NOT_SMART_DELAY;
						
					}else{
						
						activate			= false;
						next_check_delay 	= 0;
					}
				}
				
				if ( activate ){

					for ( TRTrackerAnnouncerHelper a: announcers ){

						if ( !activated.contains( a )){

							if (Logger.isEnabled()) {
								Logger.log(new LogEvent(getTorrent(), LOGID, "Activating " + getString( a.getAnnounceSets())));
							}

							activated.add( a );

							last_activation_time = SystemTime.getMonotonousTime();

							if ( complete ){

								a.complete( true );

							}else{

								a.update( false );
							}

							if ( smart_activation ){
							
								break;
							}
						}
					}
				}
			}

			if ( next_check_delay > 0 ){

				setupActivationCheck( next_check_delay );
			}
		}
	}

	private String
	getString(
		TOTorrentAnnounceURLSet[]	sets )
	{
		StringBuilder str = new StringBuilder();

		str.append( "[" );

		int	num1 = 0;

		for ( TOTorrentAnnounceURLSet s: sets ){

			if ( num1++ > 0 ){
				str.append( ", ");
			}

			str.append( "[" );

			URL[]	urls = s.getAnnounceURLs();

			int	num2 = 0;

			for ( URL u: urls ){

				if ( num2++ > 0 ){
					str.append( ", ");
				}

				str.append( u.toExternalForm());
			}

			str.append( "]" );
		}

		str.append( "]" );

		return( str.toString());
	}

	private boolean
	same(
		TOTorrentAnnounceURLSet[]	s1,
		TOTorrentAnnounceURLSet[]	s2 )
	{
		boolean	res = sameSupport( s1, s2 );

		// System.out.println( "same->" + res + ": " + getString(s1) + "/" + getString(s2));

		return( res );
	}

	private boolean
	sameSupport(
		TOTorrentAnnounceURLSet[]	s1,
		TOTorrentAnnounceURLSet[]	s2 )
	{
		if ( s1.length != s2.length ){

			return( false );
		}

		for (int i=0;i<s1.length;i++){

			URL[] u1 = s1[i].getAnnounceURLs();
			URL[] u2 = s2[i].getAnnounceURLs();

			if ( u1.length != u2.length ){

				return( false );
			}

			if ( u1.length == 1 ){

				return( u1[0].toExternalForm().equals( u2[0].toExternalForm()));
			}

			Set<String> set1 = new HashSet<>();

			for ( URL u: u1 ){

				set1.add( u.toExternalForm());
			}

			Set<String> set2 = new HashSet<>();

			for ( URL u: u2 ){

				set2.add( u.toExternalForm());
			}

			if ( !set1.equals( set2 )){

				return( false );
			}
		}

		return( true );
	}

	protected void
	forceStop(
		final TRTrackerBTAnnouncerImpl		announcer,
		final String[]						networks,
		final URL							url )
	{
		if (Logger.isEnabled()) {
			Logger.log(new LogEvent(getTorrent(), LOGID, "Force stopping " + url + " as private torrent" ));
		}

		new AEThread2( "TRMux:fs", true )
		{
			@Override
			public void
			run()
			{
				try{
					TRTrackerBTAnnouncerImpl an =
						new TRTrackerBTAnnouncerImpl( getTorrentHashOverride(), getTorrent(), new TOTorrentAnnounceURLSet[0], networks, true, getHelper());

					an.cloneFrom( announcer );

					an.setTrackerURL( url );

					an.stop( false );

					an.destroy();

				}catch( Throwable e ){

				}
			}
		}.start();
	}

	private TRTrackerAnnouncerHelper
	create(
		TOTorrent						torrent,
		String[]						networks,
		TOTorrentAnnounceURLSet[]		sets )

		throws TRTrackerAnnouncerException
	{
		TRTrackerAnnouncerHelper announcer;

		boolean	decentralised;

		if ( sets.length == 0 ){

			decentralised = TorrentUtils.isDecentralised( torrent.getAnnounceURL());

		}else{

			decentralised = TorrentUtils.isDecentralised( sets[0].getAnnounceURLs()[0]);
		}

		if ( decentralised ){

			announcer	= new TRTrackerDHTAnnouncerImpl( torrent, networks, is_manual, getHelper());

		}else{

			announcer = new TRTrackerBTAnnouncerImpl( getTorrentHashOverride(), torrent, sets, networks, is_manual, getHelper());
		}

		for ( TOTorrentAnnounceURLSet set: sets ){

			URL[] urls = set.getAnnounceURLs();

			for ( URL u: urls ){

				String key = u.toExternalForm();

				StatusSummary summary = recent_responses.get( key );

				if ( summary == null ){

					summary = new StatusSummary( announcer, u );

					recent_responses.put( key, summary );

				}else{

					summary.setHelper( announcer );
				}
			}
		}

		if ( provider != null ){

			announcer.setAnnounceDataProvider( provider );
		}

		if ( ip_override != null ){

			announcer.setIPOverride( ip_override );
		}

		return( announcer );
	}


	@Override
	public TRTrackerAnnouncerResponse
	getLastResponse()
	{
		TRTrackerAnnouncerResponse	result = null;

		TRTrackerAnnouncerHelper best = getBestActive();

		if ( best != null ){

			result = best.getLastResponse();
		}

		if ( result == null ){

			result = last_response_informed;
		}

		return( result );
	}


	@Override
	protected void
	informResponse(
		TRTrackerAnnouncerHelper		helper,
		TRTrackerAnnouncerRequest		request,
		TRTrackerAnnouncerResponse		response )
	{
		URL	url = response.getURL();

			// can be null for external plugins (e.g. mldht...)

		if ( url != null ){

			synchronized( this ){

				String key = url.toExternalForm();

				StatusSummary summary = recent_responses.get( key );

				if ( summary != null ){

					summary.updateFrom( request, response );
				}
			}
		}

		last_response_informed = response;

			// force recalc of best active next time

		last_best_active_set_time = 0;

		super.informResponse( helper, request, response );

		if ( response.getStatus() != TRTrackerAnnouncerResponse.ST_ONLINE ){

			URL	u = response.getURL();

			if ( u != null ){

				String s = u.toExternalForm();

				synchronized( failed_urls ){

					if ( failed_urls.contains( s )){

						return;
					}

					failed_urls.add( s );
				}
			}

			checkActivation( true );
		}
	}

	@Override
	public boolean
	isManual()
	{
		return( is_manual );
	}

	@Override
	public void
	setAnnounceDataProvider(
		TRTrackerAnnouncerDataProvider		_provider )
	{
		List<TRTrackerAnnouncerHelper>	to_set;

		synchronized( this ){

			provider	= _provider;

			to_set = announcers.getList();
		}

		for ( TRTrackerAnnouncer announcer: to_set ){

			announcer.setAnnounceDataProvider( provider );
		}
	}

	protected TRTrackerAnnouncerHelper
	getBestActive()
	{
		long	now = SystemTime.getMonotonousTime();

		if ( now - last_best_active_set_time < 1000 ){

			return( last_best_active );
		}

		last_best_active = getBestActiveSupport();

		last_best_active_set_time = now;

		return( last_best_active );
	}

	protected TRTrackerAnnouncerHelper
	getBestActiveSupport()
	{
		List<TRTrackerAnnouncerHelper> x = announcers.getList();

		TRTrackerAnnouncerHelper error_resp = null;

		for ( TRTrackerAnnouncerHelper announcer: x ){

			TRTrackerAnnouncerResponse response = announcer.getLastResponse();

			if ( response != null ){

				int	resp_status = response.getStatus();

				if ( resp_status == TRTrackerAnnouncerResponse.ST_ONLINE ){

					return( announcer );

				}else if ( error_resp == null && resp_status == TRTrackerAnnouncerResponse.ST_REPORTED_ERROR ){

					error_resp = announcer;
				}
			}
		}

		if ( error_resp != null ){

			return( error_resp );
		}

		if ( x.size() > 0 ){

			return( x.get(0));
		}

		return( null );
	}

	private HashWrapper
	getTorrentHashOverride()
	{
		if ( f_provider != null ){
			
			return( f_provider.getTorrentHashOverride());
			
		}else{
			
			return( null );
		}
	}
	
	@Override
	public URL
	getTrackerURL()
	{
		TRTrackerAnnouncerHelper	active = getBestActive();

		if ( active != null ){

			return( active.getTrackerURL());
		}

		return( null );
	}

	@Override
	public void
	setTrackerURL(
		URL		url )
	{
		List<List<String>> groups = new ArrayList<>();

		List<String> group = new ArrayList<>();

		group.add( url.toExternalForm());

		groups.add( group );

		TorrentUtils.listToAnnounceGroups( groups, getTorrent());

		resetTrackerUrl( false );
	}

	@Override
	public void
	resetTrackerUrl(
		boolean	shuffle )
	{
		try{
			split( false );

		}catch( Throwable e ){

			Debug.out( e );
		}

		for ( TRTrackerAnnouncer announcer: announcers ){

			announcer.resetTrackerUrl( shuffle );
		}
	}

	@Override
	public void
	setIPOverride(
		String		override )
	{
		List<TRTrackerAnnouncerHelper>	to_set;

		synchronized( this ){

			to_set	= announcers.getList();

			ip_override	= override;
		}

		for ( TRTrackerAnnouncer announcer: to_set ){

			announcer.setIPOverride( override );
		}
	}

	@Override
	public void
	clearIPOverride()
	{
		List<TRTrackerAnnouncerHelper>	to_clear;

		synchronized( this ){

			to_clear	= announcers.getList();

			ip_override	= null;
		}

		for ( TRTrackerAnnouncer announcer: to_clear ){

			announcer.clearIPOverride();
		}
	}

	@Override
	public void
	setRefreshDelayOverrides(
		int		percentage )
	{
		for ( TRTrackerAnnouncer announcer: announcers ){

			announcer.setRefreshDelayOverrides( percentage );
		}
	}

	@Override
	public int
	getTimeUntilNextUpdate()
	{
		TRTrackerAnnouncerHelper	active = getBestActive();

		if ( active != null ){

			return( active.getTimeUntilNextUpdate());
		}

		return( Integer.MAX_VALUE );
	}

	@Override
	public int
	getLastUpdateTime()
	{
		TRTrackerAnnouncerHelper	active = getBestActive();

		if ( active != null ){

			return( active.getLastUpdateTime());
		}

		return( 0 );
	}

	@Override
	public void
	update(
		boolean	force )
	{
		List<TRTrackerAnnouncerHelper> to_update;

		synchronized( this ){

			to_update = is_manual?announcers.getList(): new ArrayList<>(activated);
		}

		for ( TRTrackerAnnouncer announcer: to_update ){

			announcer.update(force);
		}
	}

	@Override
	public void
	complete(
		boolean	already_reported )
	{
		List<TRTrackerAnnouncerHelper> to_complete;

		synchronized( this ){

			complete	= true;

			to_complete = is_manual?announcers.getList(): new ArrayList<>(activated);
		}

		for ( TRTrackerAnnouncer announcer: to_complete ){

			announcer.complete( already_reported );
		}
	}

	@Override
	public void
	stop(
		boolean	for_queue )
	{
		List<TRTrackerAnnouncerHelper> to_stop;

		synchronized( this ){

			stopped	= true;

			to_stop = is_manual?announcers.getList(): new ArrayList<>(activated);

			activated.clear();
		}

		for ( TRTrackerAnnouncer announcer: to_stop ){

			announcer.stop( for_queue );
		}
	}

	@Override
	public void
	destroy()
	{
		TRTrackerAnnouncerFactoryImpl.destroy( this );

		List<TRTrackerAnnouncerHelper> to_destroy;

		synchronized( this ){

			destroyed = true;

			to_destroy = announcers.getList();
		}

		for ( TRTrackerAnnouncer announcer: to_destroy ){

			announcer.destroy();
		}

		TimerEvent	ev = event;

		if ( ev != null ){

			ev.cancel();
		}
	}

	@Override
	public int
	getStatus()
	{
		TRTrackerAnnouncer	max_announcer = getBestAnnouncer();

		return( max_announcer==null?-1:max_announcer.getStatus());
	}

	@Override
	public String
	getStatusString()
	{
		TRTrackerAnnouncer	max_announcer = getBestAnnouncer();

		return( max_announcer==null?"":max_announcer.getStatusString());
	}

	@Override
	public TRTrackerAnnouncer
	getBestAnnouncer()
	{
		int	max = -1;

		TRTrackerAnnouncer	max_announcer = null;

		for ( TRTrackerAnnouncer announcer: announcers ){

			int	status = announcer.getStatus();

			if ( status > max ){

				max_announcer 	= announcer;
				max				= status;
			}
		}

		return( max_announcer==null?this:max_announcer );
	}

	@Override
	public void
	refreshListeners()
	{
		informURLRefresh();
	}

	@Override
	public void
	setAnnounceResult(
		DownloadAnnounceResult	result )
	{
			// this is only used for setting DHT results

		for ( TRTrackerAnnouncer announcer: announcers ){

			if ( announcer instanceof TRTrackerDHTAnnouncerImpl ){

				announcer.setAnnounceResult( result );

				return;
			}
		}

			// TODO: we should always create a DHT entry and have it denote DHT tracking for all circumstances
			// have the DHT plugin set it to offline if disabled

		List<TRTrackerAnnouncerHelper> x = announcers.getList();

		if ( x.size() > 0 ){

			x.get(0).setAnnounceResult( result );
		}
	}

	@Override
	protected int
	getPeerCacheLimit()
	{
		synchronized( this ){

			if ( activated.size() < announcers.size()){

				return( 0 );
			}
		}

		if ( SystemTime.getMonotonousTime() - create_time < 15*1000 ){

			return( 0 );
		}

		TRTrackerAnnouncer active = getBestActive();

		if ( active != null && provider != null && active.getStatus() == TRTrackerAnnouncerResponse.ST_ONLINE ){

			if ( 	provider.getMaxNewConnectionsAllowed( "" ) != 0 &&
					provider.getPendingConnectionCount() == 0 ){

				return( 5 );

			}else{

				return( 0 );
			}
		}

		return( 10 );
	}

	@Override
	public TrackerPeerSource
	getTrackerPeerSource(
		final TOTorrentAnnounceURLSet		set )
	{
		URL[]	urls = set.getAnnounceURLs();

		final String[] url_strs = new String[ urls.length ];

		for ( int i=0;i<urls.length;i++ ){

			url_strs[i] = urls[i].toExternalForm();
		}

		return(
			new TrackerPeerSource()
			{
				private StatusSummary		_summary;
				private boolean				enabled;
				private long				fixup_time;

				private StatusSummary
				fixup()
				{
					long now = SystemTime.getMonotonousTime();

					if ( now - fixup_time > 1000 ){

						long			most_recent	= 0;
						StatusSummary	summary	 	= null;

						synchronized( TRTrackerAnnouncerMuxer.this ){

							for ( String str: url_strs ){

								StatusSummary s = recent_responses.get( str );

								if ( s != null ){

									if ( summary == null || s.getTime() > most_recent ){

										summary		= s;
										most_recent	= s.getTime();
									}
								}
							}
						}

						if ( provider != null ){

							enabled = provider.isPeerSourceEnabled( PEPeerSource.PS_BT_TRACKER );
						}

						if ( summary != null ){

							_summary = summary;
						}

						fixup_time = now;
					}

					return( _summary );
				}

				@Override
				public int
				getType()
				{
					return( TrackerPeerSource.TP_TRACKER );
				}

				@Override
				public String
				getName()
				{
					StatusSummary summary = fixup();

					if ( summary != null ){

						String str =summary.getURL().toExternalForm();

						int pos = str.indexOf( '?' );

						if ( pos != -1 ){

							str = str.substring( 0, pos );
						}

						return( str );
					}

					return( url_strs[0] );
				}

				@Override
				public String
				getDetails()
				{
					return( null);
				}

				@Override
				public URL
				getURL()
				{
					StatusSummary summary = fixup();

					if ( summary != null ){

						return( summary.getURL());
					}

					return( urls[0] );
				}
				
				@Override
				public int
				getStatus()
				{
					StatusSummary summary = fixup();

					if ( !enabled ){

						return( ST_DISABLED );
					}

					if ( summary != null ){

						return( summary.getStatus());
					}

					return( ST_QUEUED );
				}

				@Override
				public String
				getStatusString()
				{
					StatusSummary summary = fixup();

					if ( summary != null && enabled ){

						return( summary.getStatusString());
					}

					return( null );
				}

				@Override
				public int
				getSeedCount()
				{
					StatusSummary summary = fixup();

					if ( summary != null ){

						return( summary.getSeedCount());
					}

					return( -1 );
				}

				@Override
				public int
				getLeecherCount()
				{
					StatusSummary summary = fixup();

					if ( summary != null ){

						return( summary.getLeecherCount());
					}

					return( -1 );
				}

				@Override
				public int
				getCompletedCount()
				{
					StatusSummary summary = fixup();

					if ( summary != null ){

						return( summary.getCompletedCount());
					}

					return( -1 );
				}

				@Override
				public int
				getPeers()
				{
					StatusSummary summary = fixup();

					if ( summary != null ){

						return( summary.getPeers());
					}

					return( -1 );
				}

				@Override
				public int
				getLastUpdate()
				{
					StatusSummary summary = fixup();

					if ( summary != null ){

						long time = summary.getTime();

						if ( time == 0 ){

							return( 0 );
						}

						long elapsed = SystemTime.getMonotonousTime() - time;

						return((int)( (SystemTime.getCurrentTime() - elapsed ) / 1000 ));
					}

					return( 0 );
				}

				@Override
				public int
				getSecondsToUpdate()
				{
					StatusSummary summary = fixup();

					if ( summary != null ){

						return( summary.getSecondsToUpdate());
					}

					return( Integer.MIN_VALUE );
				}

				@Override
				public int
				getInterval()
				{
					StatusSummary summary = fixup();

					if ( summary != null ){

						return( summary.getInterval());
					}

					return( -1 );
				}

				@Override
				public int
				getMinInterval()
				{
					StatusSummary summary = fixup();

					if ( summary != null && enabled ){

						return( summary.getMinInterval());
					}

					return( -1 );
				}

				@Override
				public boolean
				isUpdating()
				{
					StatusSummary summary = fixup();

					if ( summary != null && enabled  ){

						return( summary.isUpdating());
					}

					return( false );
				}

				@Override
				public boolean
				canManuallyUpdate()
				{
					StatusSummary summary = fixup();

					if ( summary == null ){

						return( false );
					}

					return( summary.canManuallyUpdate());
				}

				@Override
				public void
				manualUpdate()
				{
					StatusSummary summary = fixup();

					if ( summary != null ){

						summary.manualUpdate();
					}
				}

				@Override
				public long[]
				getReportedStats()
				{
					StatusSummary summary = fixup();

					if ( summary != null && enabled ){

						return( summary.getReportedStats());
					}

					return( null );
				}
				
				@Override
				public boolean
				canDelete()
				{
					return( false );
				}

				@Override
				public void
				delete()
				{
					Debug.out( "derp" );
				}
			});
	}

	@Override
	public void
	generateEvidence(
		IndentWriter writer )
	{
		for ( TRTrackerAnnouncer announcer: announcers ){

			announcer.generateEvidence(writer);
		}
	}

	private static class
	StatusSummary
	{
		private TRTrackerAnnouncerHelper		helper;

		private long		time;
		private final URL			url;
		private int			status;
		private String		status_str;
		private int			seeds		= -1;
		private int			leechers	= -1;
		private int			peers		= -1;
		private int			completed	= -1;

		private int			interval;
		private int			min_interval;

		private long		reported_sent;
		private long		reported_received;
		
		protected
		StatusSummary(
			TRTrackerAnnouncerHelper		_helper,
			URL								_url )
		{
			helper	= _helper;
			url		= _url;

			status = TrackerPeerSource.ST_QUEUED;
		}

		protected void
		setHelper(
			TRTrackerAnnouncerHelper		_helper )
		{
			helper	= _helper;
		}

		protected void
		updateFrom(
			TRTrackerAnnouncerRequest		request,
			TRTrackerAnnouncerResponse		response )
		{
			time	= SystemTime.getMonotonousTime();

			int	state = response.getStatus();

			if ( state == TRTrackerAnnouncerResponse.ST_ONLINE ){

				status = TrackerPeerSource.ST_ONLINE;

				seeds		= response.getScrapeCompleteCount();
				leechers	= response.getScrapeIncompleteCount();
				completed	= response.getScrapeDownloadedCount();
				peers		= response.getPeers().length;
					
				reported_sent		= request.getReportedUpload();
				reported_received 	= request.getReportedDownload();
				
			}else{

				status = TrackerPeerSource.ST_ERROR;
			}

			status_str = response.getStatusString();

			interval 		= (int)helper.getInterval();
			min_interval 	= (int)helper.getMinInterval();
		}

		public long
		getTime()
		{
			return( time );
		}

		public URL
		getURL()
		{
			return( url );
		}

		public int
		getStatus()
		{
			return( status );
		}

		public String
		getStatusString()
		{
			return( status_str );
		}

		public int
		getSeedCount()
		{
			return( seeds );
		}

		public int
		getLeecherCount()
		{
			return( leechers );
		}

		public int
		getCompletedCount()
		{
			return( completed );
		}

		public int
		getPeers()
		{
			return( peers );
		}

		public boolean
		isUpdating()
		{
			return( helper.isUpdating());
		}

		public int
		getInterval()
		{
			return( interval );
		}

		public int
		getMinInterval()
		{
			return( min_interval );
		}

		public int
		getSecondsToUpdate()
		{
			return( helper.getTimeUntilNextUpdate());
		}

		public boolean
		canManuallyUpdate()
		{
			return( ((SystemTime.getCurrentTime() / 1000 - helper.getLastUpdateTime() >= TRTrackerAnnouncer.REFRESH_MINIMUM_SECS)));
		}

		public void
		manualUpdate()
		{
			helper.update( true );
		}
		
		public long[]
		getReportedStats()
		{
			return( new long[]{ reported_sent, reported_received });
		}
	}
}
