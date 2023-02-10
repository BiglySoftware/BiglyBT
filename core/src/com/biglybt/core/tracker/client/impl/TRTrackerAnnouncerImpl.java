/*
 * Created on 14-Feb-2005
 * Created by Paul Gardner
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
 *
 */

package com.biglybt.core.tracker.client.impl;

import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.tracker.TrackerPeerSource;
import com.biglybt.core.tracker.TrackerPeerSourceAdapter;
import com.biglybt.core.tracker.client.*;
import com.biglybt.core.util.*;
import com.biglybt.pif.clientid.ClientIDException;
import com.biglybt.pifimpl.local.clientid.ClientIDManagerImpl;

/**
 * @author parg
 *
 */

public abstract class
TRTrackerAnnouncerImpl
	implements TRTrackerAnnouncer
{
  // Used to be componentID 2
	public final static LogIDs LOGID = LogIDs.TRACKER;

	// 	listener

	private static final int LDT_TRACKER_RESPONSE		= 1;
	private static final int LDT_URL_CHANGED			= 2;
	private static final int LDT_URL_REFRESH			= 3;

	private static final String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

	private static final int	   	key_id_length	= 8;

	private static AtomicLong		session_id_next = new AtomicLong(0);	// 0 reserved for consolidation
	
	private static String
	createKeyID()
	{
		String	key_id = "";

		for (int i = 0; i < key_id_length; i++) {
			int pos = RandomUtils.nextInt( chars.length());
		    key_id +=  chars.charAt(pos);
		}

		return( key_id );
	}

	protected final ListenerManager<TRTrackerAnnouncerListener>	listeners 	= ListenerManager.createManager(
			"TrackerClient:ListenDispatcher",
			new ListenerManagerDispatcher<TRTrackerAnnouncerListener>()
			{
				@Override
				public void
				dispatch(
					TRTrackerAnnouncerListener		listener,
					int								type,
					Object							value )
				{
					if ( type == LDT_TRACKER_RESPONSE ){

						Object[] temp = (Object[])value;
						
						listener.receivedTrackerResponse((TRTrackerAnnouncerRequest)temp[0], (TRTrackerAnnouncerResponse)temp[1]);

					}else if ( type == LDT_URL_CHANGED ){

						Object[]	x = (Object[])value;

						URL			old_url 	= (URL)x[0];
						URL			new_url 	= (URL)x[1];
						boolean		explicit	= ((Boolean)x[2]).booleanValue();

						listener.urlChanged( TRTrackerAnnouncerImpl.this, old_url, new_url, explicit );

					}else{

						listener.urlRefresh();
					}
				}
			});

	final Map<String,TRTrackerAnnouncerResponsePeer>	tracker_peer_cache		= new LinkedHashMap<>();	// insertion order - most recent at end
	
	private final AEMonitor tracker_peer_cache_mon 	= new AEMonitor( "TRTrackerClientClassic:PC" );
	private int	cache_peers_used;

	final private TOTorrent						torrent;
	final private byte[]						peer_id;
	final private long							session_id;
	final private String						tracker_key;
	final private int							udp_key;


	protected
	TRTrackerAnnouncerImpl(
		TOTorrent	_torrent )

		throws TRTrackerAnnouncerException
	{
		torrent	= _torrent;
		
		session_id = session_id_next.incrementAndGet();
		
		tracker_key	= createKeyID();

		udp_key	= RandomUtils.nextInt();

		try{
			byte[] 	hash = null;

			try{
				hash = torrent.getHash();

			}catch( Throwable e ){
			}

			peer_id		= ClientIDManagerImpl.getSingleton().generatePeerID( hash, false );

		}catch( ClientIDException e ){

			 throw( new TRTrackerAnnouncerException( "TRTrackerAnnouncer: Peer ID generation fails", e ));
		}
	}
	
	@Override
	public TOTorrent
	getTorrent()
	{
		return( torrent );
	}

	public Helper
	getHelper()
	{
		return(
			new Helper()
			{
				@Override
				public byte[]
				getPeerID()
				{
					return( peer_id );
				}

				@Override
				public long 
				getSessionID()
				{
					return( session_id );
				}
				
				@Override
				public String
				getTrackerKey()
				{
					return( tracker_key );
				}

				@Override
				public int
				getUDPKey()
				{
					return( udp_key );
				}

				@Override
				public void
				addToTrackerCache(
					TRTrackerAnnouncerResponsePeerImpl[]		peers )
				{
					TRTrackerAnnouncerImpl.this.addToTrackerCache( peers );
				}

				@Override
				public TRTrackerAnnouncerResponsePeer[]
		      	getPeersFromCache(
		      		int			num_want )
				{
					return( TRTrackerAnnouncerImpl.this.getPeersFromCache(num_want));
				}

				@Override
				public void
				setTrackerResponseCache(
					Map 		map	)
				{
					TRTrackerAnnouncerImpl.this.setTrackerResponseCache( map );
				}

				@Override
				public void
				removeFromTrackerResponseCache(
					String ip, int tcpPort )
				{
					TRTrackerAnnouncerImpl.this.removeFromTrackerResponseCache( ip,tcpPort );
				}

				@Override
				public Map
				getTrackerResponseCache()
				{
					return( TRTrackerAnnouncerImpl.this.getTrackerResponseCache());
				}

				@Override
				public void
				informResponse(
					TRTrackerAnnouncerHelper		helper,
					TRTrackerAnnouncerRequest		request,
					TRTrackerAnnouncerResponse		response )
				{
					TRTrackerAnnouncerImpl.this.informResponse( helper, request, response );
				}

				@Override
				public void
				informURLChange(
					URL		old_url,
					URL		new_url,
					boolean	explicit )
				{
					listeners.dispatch(	LDT_URL_CHANGED,
							new Object[]{old_url, new_url, Boolean.valueOf(explicit)});
				}

				@Override
				public void
				informURLRefresh()
				{
					TRTrackerAnnouncerImpl.this.informURLRefresh();
				}

			 	@Override
			  public void
				addListener(
					TRTrackerAnnouncerListener	l )
			 	{
			 		TRTrackerAnnouncerImpl.this.addListener( l );
			 	}

				@Override
				public void
				removeListener(
					TRTrackerAnnouncerListener	l )
				{
					TRTrackerAnnouncerImpl.this.removeListener( l );
				}
			});
	}

	@Override
	public byte[]
	getPeerId()
	{
		return( peer_id );
	}


		// NOTE: tracker_cache is cleared out in DownloadManager when opening a torrent for the
		// first time as a DOS prevention measure

	@Override
	public Map
	getTrackerResponseCache()
	{
		return( exportTrackerCache());
	}


	@Override
	public void
	setTrackerResponseCache(
		Map		map )
	{
		int	num = importTrackerCache( map );

		if (Logger.isEnabled())
			Logger.log(new LogEvent(getTorrent(), LOGID, "TRTrackerClient: imported "
					+ num + " cached peers"));
	}

	protected Map
	exportTrackerCache()
	{
		Map	res = new LightHashMap(1);

		List	peers = new ArrayList();

		res.put( "tracker_peers", peers );

		try{
			tracker_peer_cache_mon.enter();

			Iterator it = tracker_peer_cache.values().iterator();

			while( it.hasNext()){

				TRTrackerAnnouncerResponsePeer	peer = (TRTrackerAnnouncerResponsePeer)it.next();

				LightHashMap entry = new LightHashMap();

				entry.put( "ip", peer.getAddress().getBytes());
				entry.put( "src", peer.getSource().getBytes());
				entry.put( "port", new Long(peer.getPort()));

				int	udp_port = peer.getUDPPort();
				if ( udp_port != 0 ){
					entry.put( "udpport", new Long( udp_port));
				}
				int	http_port = peer.getHTTPPort();
				if ( http_port != 0 ){
					entry.put( "httpport", new Long( http_port));
				}

				entry.put( "prot", new Long(peer.getProtocol()));

				byte	az_ver = peer.getAZVersion();

				if ( az_ver != TRTrackerAnnouncer.AZ_TRACKER_VERSION_1 ){
					entry.put( "azver", new Long( az_ver ));
				}

				entry.compactify(0.9f);

				peers.add( entry );
			}

			if (Logger.isEnabled())
				Logger.log(new LogEvent(getTorrent(), LOGID,
						"TRTrackerClient: exported " + tracker_peer_cache.size()
								+ " cached peers"));
		}finally{

			tracker_peer_cache_mon.exit();
		}

		return( res );
	}

	protected int
	importTrackerCache(
		Map		map )
	{
		if ( !COConfigurationManager.getBooleanParameter("File.save.peers.enable")){

			return( 0 );
		}

		try{
			if ( map == null ){

				return( 0 );
			}

			try{
				tracker_peer_cache_mon.enter();

				List<TRTrackerAnnouncerResponsePeer>	peers = TRTrackerAnnouncerFactoryImpl.getCachedPeers( map );

				for ( TRTrackerAnnouncerResponsePeer peer: peers ){
					
					tracker_peer_cache.put( peer.getKey(), peer );
				}

				return( tracker_peer_cache.size());

			}finally{

				tracker_peer_cache_mon.exit();
			}
		}catch( Throwable e ){

			Debug.printStackTrace( e );

			return( tracker_peer_cache.size());
		}
	}

	protected void
	addToTrackerCache(
		TRTrackerAnnouncerResponsePeerImpl[]		peers )
	{
		if ( !COConfigurationManager.getBooleanParameter("File.save.peers.enable")){

			return;
		}

		int	max = COConfigurationManager.getIntParameter( "File.save.peers.max", DEFAULT_PEERS_TO_CACHE );

		// System.out.println( "max peers= " + max );

		try{
			tracker_peer_cache_mon.enter();

			for (int i=0;i<peers.length;i++){

				TRTrackerAnnouncerResponsePeerImpl	peer = peers[i];

				peer = peer.getClone();
				
				peer.setCached( true );
					
					// remove and reinsert to maintain most recent last

				tracker_peer_cache.remove( peer.getKey());

				tracker_peer_cache.put( peer.getKey(), peer );
			}

			Iterator	it = tracker_peer_cache.keySet().iterator();

			if ( max > 0 ){

				while ( tracker_peer_cache.size() > max ){

					it.next();

					it.remove();
				}
			}
		}finally{

			tracker_peer_cache_mon.exit();
		}
	}

	@Override
	public void
	removeFromTrackerResponseCache(
		String		ip,
		int			tcp_port )
	{
		try{
			tracker_peer_cache_mon.enter();

				// create a fake peer so we can get the key

			TRTrackerAnnouncerResponsePeerImpl peer =
				new TRTrackerAnnouncerResponsePeerImpl( "", new byte[0], ip, tcp_port, 0, 0, (short)0, (byte)0, (short)0 );

			if ( tracker_peer_cache.remove( peer.getKey()) != null ){

				if (Logger.isEnabled())
					Logger.log(new LogEvent( getTorrent(), LOGID, "Explicit removal of peer cache for " + ip + ":" + tcp_port ));
			}

		}finally{

			tracker_peer_cache_mon.exit();
		}
	}

	public static Map
	mergeResponseCache(
		Map		map1,
		Map		map2 )
	{
		if ( map1 == null && map2 == null ){
			return( new HashMap());
		}else if ( map1 == null ){
			return( map2 );
		}else if ( map2 == null ){
			return( map1 );
		}

		Map	res = new HashMap();

		List	peers = (List)map1.get( "tracker_peers" );

		if ( peers == null ){

			peers = new ArrayList();
		}

		List	p2 = (List)map2.get( "tracker_peers" );

		if ( p2 != null ){

			if (Logger.isEnabled())
				Logger.log(new LogEvent(LOGID,
						"TRTrackerClient: merged peer sets: p1 = " + peers.size()
								+ ", p2 = " + p2.size()));

			peers.addAll(p2);
		}

		res.put( "tracker_peers", peers );

		return( res );
	}

	protected abstract int
	getPeerCacheLimit();

	protected TRTrackerAnnouncerResponsePeer[]
	getPeersFromCache(
		int	num_want )
	{
		int	limit = getPeerCacheLimit();

		if ( limit <= 0 ){

			return( new TRTrackerAnnouncerResponsePeer[0] );
		}

			// limit peers returned to avoid multi-tracker torrents from getting swamped
			// by out-of-date peers from a failed tracker

		num_want = Math.min( limit, num_want );

		try{
			tracker_peer_cache_mon.enter();

			TRTrackerAnnouncerResponsePeerImpl[]	res;

			if ( tracker_peer_cache.size() <= num_want ){

				res = new TRTrackerAnnouncerResponsePeerImpl[tracker_peer_cache.size()];

				tracker_peer_cache.values().toArray( res );

			}else{

				res = new TRTrackerAnnouncerResponsePeerImpl[num_want];

				Iterator	it = tracker_peer_cache.keySet().iterator();

					// take 'em out and put them back in so we cycle through the peers
					// over time

				for (int i=0;i<num_want;i++){

					String	key = (String)it.next();

					res[i] = (TRTrackerAnnouncerResponsePeerImpl)tracker_peer_cache.get(key);

					it.remove();
				}

				for (int i=0;i<num_want;i++){

					tracker_peer_cache.put( res[i].getKey(), res[i] );
				}
			}

			if (Logger.isEnabled()){

				for (int i=0;i<res.length;i++){

					Logger.log(new LogEvent(getTorrent(), LOGID, "CACHED PEER: " + res[i].getString()));
				}

				Logger.log(new LogEvent(getTorrent(), LOGID,
						"TRTrackerClient: returned " + res.length + " cached peers"));
			}

			cache_peers_used += res.length;

			return( res );

		}finally{

			tracker_peer_cache_mon.exit();
		}
	}

	@Override
	public TrackerPeerSource
	getCacheTrackerPeerSource()
	{
		return(
			new TrackerPeerSourceAdapter()
			{
				@Override
				public String
				getName()
				{
					return( MessageText.getString( "tps.tracker.cache1", new String[]{ String.valueOf( cache_peers_used )}));
				}

				@Override
				public int
				getPeers()
				{
					return( tracker_peer_cache.size() );
				}
			});
	}

	protected void
	informResponse(
		TRTrackerAnnouncerHelper		helper,
		TRTrackerAnnouncerRequest		request,
		TRTrackerAnnouncerResponse		response )
	{
		listeners.dispatch( LDT_TRACKER_RESPONSE, new Object[]{ request, response });
	}

	protected void
	informURLRefresh()
	{
		listeners.dispatch( LDT_URL_REFRESH, null );
	}

 	@Override
  public void
	addListener(
		TRTrackerAnnouncerListener	l )
	{
		listeners.addListener( l );
	}

	@Override
	public void
	removeListener(
		TRTrackerAnnouncerListener	l )
	{
		listeners.removeListener(l);
	}

	public interface
	Helper
	{
		public byte[]
		getPeerID();

		public long
		getSessionID();
		
		public String
		getTrackerKey();

		public int
		getUDPKey();

		public void
		addToTrackerCache(
			TRTrackerAnnouncerResponsePeerImpl[]		peers );

		public TRTrackerAnnouncerResponsePeer[]
      	getPeersFromCache(
      		int			num_want );

		public void
		setTrackerResponseCache(
			Map map	);

		public void
		removeFromTrackerResponseCache(
			String ip, int tcpPort );

		public Map
		getTrackerResponseCache();

		public void
		informResponse(
			TRTrackerAnnouncerHelper		helper,
			TRTrackerAnnouncerRequest		request,
			TRTrackerAnnouncerResponse		response );

		public void
		informURLChange(
			URL			old_url,
			URL			new_url,
			boolean		explicit );

		public void
		informURLRefresh();

	 	public void
		addListener(
			TRTrackerAnnouncerListener	l );

		public void
		removeListener(
			TRTrackerAnnouncerListener	l );
	}
}
