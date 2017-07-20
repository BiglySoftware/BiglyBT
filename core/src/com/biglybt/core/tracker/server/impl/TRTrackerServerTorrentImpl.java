/*
 * File    : TRTrackerServerTorrent.java
 * Created : 26-Oct-2003
 * By      : stuff
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

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.*;

import com.biglybt.core.dht.netcoords.DHTNetworkPosition;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.tracker.server.*;
import com.biglybt.core.util.*;


public class
TRTrackerServerTorrentImpl
	implements TRTrackerServerTorrent
{
	private static final LogIDs LOGID = LogIDs.TRACKER;
		// no point in caching replies smaller than that below

	public static final int	MIN_CACHE_ENTRY_SIZE		= 10;

	public static final int MAX_UPLOAD_BYTES_PER_SEC	= 3*1024*1024;  //3MBs
	public static final int MAX_DOWNLOAD_BYTES_PER_SEC	= MAX_UPLOAD_BYTES_PER_SEC;

	public static final boolean	USE_LIGHTWEIGHT_SEEDS	= true;

	public static final int		MAX_IP_OVERRIDE_PEERS	= 64;

	public static final byte	COMPACT_MODE_NONE		= 0;
	public static final byte	COMPACT_MODE_NORMAL		= 1;
	public static final byte	COMPACT_MODE_AZ			= 2;
	public static final byte	COMPACT_MODE_AZ_2		= 3;
	public static final byte	COMPACT_MODE_XML		= 16;

	private static final int	QUEUED_PEERS_MAX_SWARM_SIZE	= 32;
	private static final int	QUEUED_PEERS_MAX			= 32;
	private static final int	QUEUED_PEERS_ADD_MAX		= 3;


	private final TRTrackerServerImpl	server;
	private final HashWrapper			hash;

	private Map<HashWrapper,TRTrackerServerPeerImpl>		peer_map 		= new HashMap<>();

	private Map<String,TRTrackerServerPeerImpl>				peer_reuse_map	= new HashMap<>();

	private List<TRTrackerServerPeerImpl>					peer_list		= new ArrayList<>();

	private int				peer_list_hole_count;
	private boolean			peer_list_compaction_suspended;

	private List			biased_peers			= null;
	private int				min_biased_peers		= 0;

	private final Map				lightweight_seed_map	= new HashMap();

	private int				seed_count;
	private int				removed_count;

	private int				ip_override_count;

	private int				bad_NAT_count;	// calculated periodically

	private final Random			random		= new Random( SystemTime.getCurrentTime());

	private long			last_scrape_calc_time;
	private Map				last_scrape;

	private final LinkedHashMap		announce_cache	= new LinkedHashMap();

	private final TRTrackerServerTorrentStatsImpl	stats;

	private final List			listeners	= new ArrayList();
	private List			peer_listeners;
	private boolean			deleted;
	private boolean			enabled;

	private boolean			map_size_diff_reported;
	private boolean			ip_override_limit_exceeded_reported;

	private byte			duplicate_peer_checker_index	= 0;
	private byte[]			duplicate_peer_checker			= new byte[0];

	private URL[]			redirects;

	private boolean			caching_enabled	= true;

	private LinkedList		queued_peers;

	protected final AEMonitor this_mon 	= new AEMonitor( "TRTrackerServerTorrent" );

	private List	explicit_manual_biased_peers;

	private int 	explicit_next_peer;

	public
	TRTrackerServerTorrentImpl(
		TRTrackerServerImpl		_server,
		HashWrapper				_hash,
		boolean					_enabled )
	{
		server		= _server;
		hash		= _hash;
		enabled		= _enabled;

		stats		= new TRTrackerServerTorrentStatsImpl( this );
	}

	@Override
	public void
	setEnabled(
		boolean		_enabled )
	{
		enabled	= _enabled;
	}

	@Override
	public boolean
	isEnabled()
	{
		return( enabled );
	}

	@Override
	public void
	setMinBiasedPeers(
		int	num )
	{
		min_biased_peers	= num;
	}

	@Override
	public void
	importPeers(
		List		peers )
	{
		try{
			this_mon.enter();

				// only currently support import when torrent "empty"

			if ( peer_map.size() > 0 ){

				System.out.println( "TRTrackerServerTorrent: ignoring peer import as torrent already active" );

				return;
			}

			for (int i=0;i<peers.size();i++){

				TRTrackerServerPeerImpl peer = TRTrackerServerPeerImpl.importPeer((Map)peers.get(i));

				if ( peer != null ){

					try{
						String	reuse_key = new String( peer.getIPAsRead(), Constants.BYTE_ENCODING ) + ":" + peer.getTCPPort();

						peer_map.put( peer.getPeerId(), peer );

						peer_list.add( peer );

						peer_reuse_map.put( reuse_key, peer );

						if ( peer.isSeed()){

							seed_count++;
						}

						if ( peer.isBiased()){

							if ( biased_peers == null ){

								biased_peers = new ArrayList();
							}

							biased_peers.add( peer );
						}
					}catch( Throwable e ){
					}
				}
			}
		}finally{

			this_mon.exit();
		}
	}

	public TRTrackerServerPeerImpl
	peerContact(
		String				url_parameters,
		String				event,
		HashWrapper			peer_id,
		int					tcp_port,
		int					udp_port,
		int					http_port,
		byte				crypto_level,
		byte				az_ver,
		String				original_address,
		String				ip_address,
		boolean				ip_override,
		boolean				loopback,
		String				tracker_key,
		long				uploaded,
		long				downloaded,
		long				left,
		long				interval_requested,
		int					up_speed,
		DHTNetworkPosition	network_position )

		throws TRTrackerServerException
	{
		if ( !enabled ){

			throw( new TRTrackerServerException( "Torrent temporarily disabled" ));
		}

			// we can safely resolve the client_ip_address here as it is either already resolved or that of
			// the tracker. We need it resolved so we canonically store peers, otherwise we can get two
			// entries for a dns name and the corresponding ip

		if ( !HostNameToIPResolver.isNonDNSName( ip_address )){

			try{
				ip_address	= HostNameToIPResolver.syncResolve( ip_address ).getHostAddress();

			}catch( UnknownHostException e ){
			}
		}

		TRTrackerServerException	deferred_failure = null;

		try{
			this_mon.enter();

			handleRedirects( url_parameters, ip_address, false  );

			// System.out.println( "TRTrackerServerTorrent: peerContact, ip = " + ip_address );

			int	event_type = TRTrackerServerTorrentPeerListener.ET_UPDATED;

			if ( event != null && event.length() > 2){

				char	c = event.charAt(2);

				if ( c == 'm' ){	// "coMpleted"

					event_type	= TRTrackerServerTorrentPeerListener.ET_COMPLETE;

				}else if ( c == 'o' ){	// "stOpped"

					event_type = TRTrackerServerTorrentPeerListener.ET_STOPPED;

				}else{

					event_type = TRTrackerServerTorrentPeerListener.ET_STARTED;
				}
			}

			long	now = SystemTime.getCurrentTime();

			int		tracker_key_hash_code	= tracker_key==null?0:tracker_key.hashCode();

			TRTrackerServerPeerImpl	peer = (TRTrackerServerPeerImpl)peer_map.get( peer_id );

			boolean		new_peer 				= false;
			boolean		peer_already_removed	= false;

			boolean		already_completed	= false;
			long		last_contact_time	= 0;

			long	ul_diff = 0;
			long	dl_diff	= 0;
			long	le_diff = 0;

			byte[]	ip_address_bytes = ip_address.getBytes( Constants.BYTE_ENCODING );

			if ( peer == null ){

				String	reuse_key = new String( ip_address_bytes, Constants.BYTE_ENCODING ) + ":" + tcp_port;

				byte	last_NAT_status	= loopback?TRTrackerServerPeer.NAT_CHECK_OK:TRTrackerServerPeer.NAT_CHECK_UNKNOWN;

				new_peer	= true;

				// check to see if this peer already has an entry against this torrent
				// and if so delete it (assumption is that the client has quit and
				// restarted with new peer id

				//System.out.println( "new peer" );


				TRTrackerServerPeerImpl old_peer	= (TRTrackerServerPeerImpl)peer_reuse_map.get( reuse_key );

				if ( old_peer != null ){

						// don't allow an ip_override to grab a non-override entry as this is a way for
						// a malicious client to remove, say, a seed (simply send in a "stopped" command
						// with override set to the seed you want to remove)

					if ( ip_override && !old_peer.isIPOverride()){

						throw( new TRTrackerServerException( "IP Override denied (existing '" + reuse_key + "' is not override)" ));
					}

					last_contact_time	= old_peer.getLastContactTime();

					already_completed	= old_peer.getDownloadCompleted();

					removePeer( old_peer,  TRTrackerServerTorrentPeerListener.ET_REPLACED, null );

					lightweight_seed_map.remove( old_peer.getPeerId());

				}else{

					lightweightSeed lws = (lightweightSeed)lightweight_seed_map.remove( peer_id );

					if ( lws != null ){

						last_contact_time	= lws.getLastContactTime();

						ul_diff	= uploaded - lws.getUploaded();

						if ( ul_diff < 0 ){

							ul_diff	= 0;
						}

						last_NAT_status = lws.getNATStatus();

					}else{

						last_contact_time	= now;
					}
				}

				if ( event_type != TRTrackerServerTorrentPeerListener.ET_STOPPED ){

					Set	biased_peer_set = server.getBiasedPeers();

					boolean biased = biased_peer_set != null && biased_peer_set.contains( ip_address );

					if ( ip_override && ip_override_count >= MAX_IP_OVERRIDE_PEERS && !( loopback || biased )){

							// bail out - the peer will still get an announce response but we don't
							// want too many override peers on a torrent as these can be spoofed
							// to cause trouble

						if ( !ip_override_limit_exceeded_reported ){

							ip_override_limit_exceeded_reported	= true;

							Debug.out( "Too many ip-override peers for " + ByteFormatter.encodeString( hash.getBytes()));
						}

						return( null );
					}

					peer = new TRTrackerServerPeerImpl(
									peer_id,
									tracker_key_hash_code,
									ip_address_bytes,
									ip_override,
									tcp_port,
									udp_port,
									http_port,
									crypto_level,
									az_ver,
									last_contact_time,
									already_completed,
									last_NAT_status,
									up_speed,
									network_position );

					if ( ip_override ){

							// never allow an ip-override to take on the guise of a biased peer

						if ( biased ){

								// UNLESS the originating IP is biased too

							if ( !biased_peer_set.contains( original_address )){

								throw( new TRTrackerServerException( "IP Override denied (you are " + original_address + ")" ));
							}
						}

						ip_override_count++;
					}

					peer_map.put( peer_id, peer );

					peer_list.add( peer );

					peer_reuse_map.put( reuse_key, peer );

					if ( biased ){

						peer.setBiased( true );

						if ( biased_peers == null ){

							biased_peers = new ArrayList();
						}

						biased_peers.add( peer );
					}

					if ( queued_peers != null ){

						if ( peer_map.size() > QUEUED_PEERS_MAX_SWARM_SIZE ){

							queued_peers = null;

						}else{

								// peer has become active, remove the queued peer info

							Iterator	it = queued_peers.iterator();

							while( it.hasNext()){

								QueuedPeer	qp = (QueuedPeer)it.next();

								if ( qp.sameAs( peer )){

									it.remove();

									break;
								}
							}
						}
					}
				}
			}else{

				int	existing_tracker_key_hash_code = peer.getKeyHashCode();

				// System.out.println( "tracker_key:" + existing_tracker_key + "/" + tracker_key );

				if ( existing_tracker_key_hash_code != tracker_key_hash_code ){

					if ( server.isKeyEnabled()){

						throw( new TRTrackerServerException( "Unauthorised: key mismatch "));
					}
				}

				if ( ip_override ){

						// biased peers are never ip-override

					if ( peer.isBiased()){

							// UNLESS the originating IP is biased too

						Set	biased_peer_set = server.getBiasedPeers();

						if ( biased_peer_set == null || !biased_peer_set.contains( original_address )){

							throw( new TRTrackerServerException( "IP Override denied (you are " + original_address + ")"));
						}
					}

						// prevent an ip_override peer from affecting a non-override entry

					if ( !peer.isIPOverride()){

						throw( new TRTrackerServerException( "IP Override denied (existing entry not override)" ));
					}
				}

				already_completed	= peer.getDownloadCompleted();

				last_contact_time	= peer.getLastContactTime();

				if ( event_type == TRTrackerServerTorrentPeerListener.ET_STOPPED ){

					removePeer( peer, event_type, url_parameters );

					peer_already_removed	= true;

				}else{

						// IP may have changed - update if required

						// it is possible for two az clients to have the same peer id. Unlikely but possible
						// or indeed some hacked versions could do it on purpose. If this is the case then all we
						// will see here is address/port changes as each peer announces

					byte[]	old_ip 		= peer.getIPAsRead();
					int		old_port	= peer.getTCPPort();

					if ( peer.update( ip_address_bytes, tcp_port, udp_port, http_port, crypto_level, az_ver, up_speed, network_position )){

							// same peer id so same port

						String 	old_key = new String( old_ip, Constants.BYTE_ENCODING ) + ":" + old_port;

						String	new_key = new String( ip_address_bytes, Constants.BYTE_ENCODING ) + ":" + tcp_port;

							// it is possible, on address change, that the target address already exists and is
							// (was) being used by another peer. Given that this peer has taken over its address
							// the assumption is that the other peer has also had an address change and has yet
							// to report it. The only action here is to delete the other peer

						TRTrackerServerPeerImpl old_peer = (TRTrackerServerPeerImpl)peer_reuse_map.get( new_key );

						if ( old_peer != null ){

							removePeer( old_peer, TRTrackerServerTorrentPeerListener.ET_REPLACED, null );
						}

							// now swap the keys

						if ( peer_reuse_map.remove( old_key ) == null ){

							Debug.out( "TRTrackerServerTorrent: IP address change: '" + old_key + "' -> '" + new_key + "': old key not found" );
						}

						peer_reuse_map.put( new_key, peer );
					}
				}
			}

				// a null peer here signifies a new peer whose first state was "stopped"

			long	new_timeout = now + ( interval_requested * 1000 * TRTrackerServerImpl.CLIENT_TIMEOUT_MULTIPLIER );

			if ( peer != null ){

				peer.setTimeout( now, new_timeout );

					// if this is the first time we've heard from this peer then we don't want to
					// use existing ul/dl value diffs as they will have been reported previously
					// (either the client's changed peer id by stop/start (in which case the values
					// should be 0 anyway as its a per-session total), or the tracker's been
					// stopped and started).

				if ( !new_peer ){

					ul_diff = uploaded 		- peer.getUploaded();
					dl_diff = downloaded 	- peer.getDownloaded();
				}

					// simple rate control

				long	elapsed_time	= now - last_contact_time;

				if ( elapsed_time == 0 ){

					elapsed_time = SystemTime.TIME_GRANULARITY_MILLIS;
				}

				long	ul_rate = (ul_diff*1000)/elapsed_time;	// bytes per second
				long	dl_rate	= (dl_diff*1000)/elapsed_time;

				if ( ul_rate > MAX_UPLOAD_BYTES_PER_SEC ){

					if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID, "TRTrackerPeer: peer "
								+ peer.getIPRaw() + "/"
								+ new String(peer.getPeerId().getHash())
								+ " reported an upload rate of " + ul_rate / 1024
								+ " KiB/s per second"));

					ul_diff	= 0;
				}

				if ( dl_rate > MAX_DOWNLOAD_BYTES_PER_SEC ){
					if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID, "TRTrackerPeer: peer "
								+ peer.getIPRaw() + "/"
								+ new String(peer.getPeerId().getHash())
								+ " reported a download rate of " + dl_rate / 1024
								+ " KiB/s per second"));

					dl_diff	= 0;
				}
						// when the peer is removed its "left" amount will dealt with

				le_diff = (event_type==TRTrackerServerTorrentPeerListener.ET_STOPPED)?0:(left - peer.getAmountLeft());

				boolean	was_seed 	= new_peer?false:peer.isSeed();

				peer.setStats( uploaded, downloaded, left );

				boolean	is_seed		= peer.isSeed();

				if (!(event_type == TRTrackerServerTorrentPeerListener.ET_STOPPED || was_seed || !is_seed )){

					seed_count++;
				}

					// report event *after* updating totals above so listeners get a valid initial
					// view of the peer (e.g. is it a seed)

					// if the peer has already been removed above then it will have reported the
					// event already

				if ( !peer_already_removed ){

					try{
						peerEvent( peer, event_type, url_parameters );

					}catch( TRTrackerServerException	e ){

						deferred_failure = e;
					}
				}
			}

			stats.addAnnounce( ul_diff, dl_diff, le_diff, peer != null && peer.isBiased());

			if ( event_type==TRTrackerServerTorrentPeerListener.ET_COMPLETE && !already_completed ){

				peer.setDownloadCompleted();

				stats.addCompleted();
			}

			if ( peer != null && peer.isSeed()){

				int	seed_limit		= TRTrackerServerImpl.getSeedLimit();

				if ( seed_limit != 0 && seed_count > seed_limit && !loopback ){

					if ( !peer_already_removed ){

						removePeer( peer, TRTrackerServerTorrentPeerListener.ET_TOO_MANY_PEERS, null );
					}

						// this is picked up by AZ client removal rules and causes the torrent to
						// be removed

					throw( new TRTrackerServerException( "too many seeds" ));
				}

				int	seed_retention = TRTrackerServerImpl.getMaxSeedRetention();

				if ( seed_retention != 0 && seed_count > seed_retention ){

						// remove 5% of the seeds

					int	to_remove = (seed_retention/20)+1;

					try{
						peer_list_compaction_suspended	= true;

							// remove bad NAT ones in preference to others

						for (int bad_nat_loop=TRTrackerServerNATChecker.getSingleton().isEnabled()?0:1;bad_nat_loop<2;bad_nat_loop++){

							for (int i=0;i<peer_list.size();i++){

								TRTrackerServerPeerImpl	this_peer = (TRTrackerServerPeerImpl)peer_list.get(i);

								if ( this_peer != null && this_peer.isSeed() && !this_peer.isBiased()){

									boolean	bad_nat = this_peer.isNATStatusBad();

									if ( 	( bad_nat_loop == 0 && bad_nat ) ||
											( bad_nat_loop == 1 )){

										if ( USE_LIGHTWEIGHT_SEEDS ){

											lightweight_seed_map.put(
													this_peer.getPeerId(),
													new lightweightSeed(
															now,
															new_timeout,
															this_peer.getUploaded(),
															this_peer.getNATStatus()));
										}

										removePeer( this_peer, i, TRTrackerServerTorrentPeerListener.ET_TOO_MANY_PEERS, null );

										if ( --to_remove == 0 ){

											break;
										}
									}
								}
							}

							if ( to_remove == 0 ){

								break;
							}
						}
					}finally{

						peer_list_compaction_suspended	= false;
					}

					checkForPeerListCompaction( false );
				}
			}

			if ( deferred_failure != null ){

				if ( peer != null && !peer_already_removed ){

					removePeer( peer, TRTrackerServerTorrentPeerListener.ET_FAILED, url_parameters );
				}

				throw( deferred_failure );
			}

			return( peer );

		}catch( UnsupportedEncodingException e ){

			throw( new TRTrackerServerException( "Encoding fails", e ));

		}finally{

				// note we can bail out here through a return when there are too many IP overrides

			this_mon.exit();
		}
	}

	public void
	peerQueued(
		String		ip,
		int			tcp_port,
		int			udp_port,
		int			http_port,
		byte		crypto_level,
		byte		az_ver,
		long		timeout_secs,
		boolean		seed )
	{
		// System.out.println( "peerQueued: " + ip + "/" + tcp_port + "/" + udp_port + "/" + crypto_level );

		if ( peer_map.size() >= QUEUED_PEERS_MAX_SWARM_SIZE || tcp_port == 0 ){

			return;
		}

		try{
			this_mon.enter();

			Set	biased_peer_set = server.getBiasedPeers();

			boolean biased = biased_peer_set != null && biased_peer_set.contains( ip );

			QueuedPeer	new_qp =
				new QueuedPeer( ip, tcp_port, udp_port, http_port, crypto_level,
						az_ver, (int)timeout_secs, seed, biased );

			String	reuse_key = new_qp.getIP() + ":" + tcp_port;

				// if still active then drop it

			if ( peer_reuse_map.containsKey( reuse_key )){

				return;
			}

			boolean	add = true;

			if ( queued_peers != null ){

				Iterator	it = queued_peers.iterator();

				while( it.hasNext()){

					QueuedPeer	qp = (QueuedPeer)it.next();

					if ( qp.sameAs( new_qp )){

						it.remove();

						queued_peers.add( new_qp );

						return;
					}
				}

				if ( queued_peers.size() >= QUEUED_PEERS_MAX ){

					QueuedPeer	oldest = null;

					it = queued_peers.iterator();

					while( it.hasNext()){

						QueuedPeer	qp = (QueuedPeer)it.next();

							// never drop biased peers

						if ( qp.isBiased()){

							continue;
						}

						if ( oldest == null ){

							oldest = qp;

						}else{

							if ( qp.getCreateTime() < oldest.getCreateTime()){

								oldest	= qp;
							}
						}
					}

					if ( oldest == null ){

						add = false;

					}else{

						queued_peers.remove( oldest );
					}
				}
			}else{

				queued_peers = new LinkedList();
			}

			if ( add ){

				queued_peers.addFirst( new_qp );
			}

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	remove(
		TRTrackerServerPeerBase		peer )
	{
		try{
			this_mon.enter();

			if ( peer instanceof TRTrackerServerPeerImpl ){

				TRTrackerServerPeerImpl	pi = (TRTrackerServerPeerImpl)peer;

				if ( peer_map.containsKey( pi.getPeerId())){

					int	index = peer_list.indexOf( pi );

					if ( index != -1 ){

						removePeer( pi, index, TRTrackerServerTorrentPeerListener.ET_FAILED, null );
					}
				}
			}else{

				if ( queued_peers != null ){

					queued_peers.remove( peer );

					if ( queued_peers.size() == 0 ){

						queued_peers = null;
					}
				}
			}
		}finally{

			this_mon.exit();
		}
	}
	protected void
	removePeer(
		TRTrackerServerPeerImpl	peer,
		int						reason,
		String					url_parameters )
	{
		removePeer( peer, -1, reason, url_parameters );
	}

	protected void
	removePeer(
		TRTrackerServerPeerImpl	peer,
		int						peer_list_index,
		int						reason,
		String					url_parameters )	// -1 if not known
	{
		try{
			this_mon.enter();

			if ( peer.isIPOverride()){

				ip_override_count--;
			}

			stats.removeLeft( peer.getAmountLeft());

			if ( peer_map.size() != peer_reuse_map.size()){

				if ( !map_size_diff_reported ){

					map_size_diff_reported	= true;

					Debug.out( "TRTrackerServerTorrent::removePeer: maps size different ( " + peer_map.size() + "/" + peer_reuse_map.size() +")");
				}
			}

			{
				Object o = peer_map.remove( peer.getPeerId());

				if ( o == null ){

					Debug.out(" TRTrackerServerTorrent::removePeer: peer_map doesn't contain peer");
				}else{

					try{
						peerEvent( peer, reason, url_parameters );

					}catch( TRTrackerServerException e ){
						// ignore during peer removal
					}
				}
			}

			if ( peer_list_index == -1 ){

				int	peer_index = peer_list.indexOf( peer );

				if ( peer_index == -1){

					Debug.out(" TRTrackerServerTorrent::removePeer: peer_list doesn't contain peer");
				}else{

					peer_list.set( peer_index, null );
				}
			}else{

				if ( peer_list.get( peer_list_index ) == peer ){

					peer_list.set( peer_list_index, null );

				}else{

					Debug.out(" TRTrackerServerTorrent::removePeer: peer_list doesn't contain peer at index");

				}
			}

			peer_list_hole_count++;

			checkForPeerListCompaction( false );

			try{
				Object o = peer_reuse_map.remove( new String( peer.getIPAsRead(), Constants.BYTE_ENCODING ) + ":" + peer.getTCPPort());

				if ( o == null ){

					Debug.out(" TRTrackerServerTorrent::removePeer: peer_reuse_map doesn't contain peer");
				}

			}catch( UnsupportedEncodingException e ){
			}

			if ( biased_peers != null ){

				biased_peers.remove( peer );
			}

			if ( peer.isSeed()){

				seed_count--;
			}

			removed_count++;

		}finally{

			this_mon.exit();
		}
	}

	protected void
	updateBiasedPeers(
		Set	biased_peers_set )
	{
		try{
			this_mon.enter();

			Iterator it = peer_list.iterator();

			if ( it.hasNext() && biased_peers == null ){

				biased_peers = new ArrayList();
			}

			while( it.hasNext()){

				TRTrackerServerPeerImpl	this_peer = (TRTrackerServerPeerImpl)it.next();

				if ( this_peer != null ){

					 boolean	biased = biased_peers_set.contains( this_peer.getIPRaw());

					 this_peer.setBiased( biased );

					 if ( biased ){

						 if ( !biased_peers.contains( this_peer )){

							 biased_peers.add( this_peer );
						 }
					 }else{

						 biased_peers.remove( this_peer );
					 }
				}
			}

			if ( queued_peers != null ){

				it = queued_peers.iterator();

				while( it.hasNext()){

					QueuedPeer	peer = (QueuedPeer)it.next();

					peer.setBiased( biased_peers_set.contains(  peer.getIP()));
				}
			}
		}finally{

			this_mon.exit();
		}
	}

	@Override
	public TRTrackerServerTorrent
	addLink(
		String	link )
	{
		return( server.addLink( link, this ));
	}

	@Override
	public void
	removeLink(
		String	link )
	{
		server.removeLink( link, this );
	}

	public Map
	exportAnnounceToMap(
		String						ip_address,
		HashMap						preprocess_map,
		TRTrackerServerPeerImpl		requesting_peer,		// maybe null for an initial announce from a stopped peer
		boolean						include_seeds,
		int							num_want,
		long						interval,
		long						min_interval,
		boolean						no_peer_id,
		byte						compact_mode,
		byte						crypto_level,
		DHTNetworkPosition			network_position )
	{
		try{
			this_mon.enter();

			long	now = SystemTime.getCurrentTime();

			// we have to force non-caching for nat_warnings responses as they include
			// peer-specific data

			boolean	nat_warning = requesting_peer != null && requesting_peer.getNATStatus() == TRTrackerServerPeerImpl.NAT_CHECK_FAILED;

			int		total_peers			= peer_map.size();
			int		cache_millis	 	= TRTrackerServerImpl.getAnnounceCachePeriod();

			boolean	send_peer_ids 		= TRTrackerServerImpl.getSendPeerIds();

				// override if client has explicitly not requested them

			if ( no_peer_id || compact_mode != COMPACT_MODE_NONE ){

				send_peer_ids	= false;
			}

			boolean	add_to_cache	= false;

			int		max_peers	= TRTrackerServerImpl.getMaxPeersToSend();

				// num_want < 0 -> not supplied so give them max

			if ( num_want < 0 ){

				num_want = total_peers;
			}

				// trim back to max_peers if specified

			if ( max_peers > 0 && num_want > max_peers ){

				num_want	= max_peers;
			}

				// if set this list contains the only peers that are to be returned. It allows a manual
				// external peer selection algorithm

			List<TRTrackerServerSimplePeer> 	explicit_limited_peers 	= null;
			List<TRTrackerServerSimplePeer>		explicit_biased_peers	= null;

			Set		remove_ips				= null;

			if ( requesting_peer != null ){

				if ( peer_listeners != null ){

					for (int i=0;i<peer_listeners.size();i++){

						try{
							Map reply = ((TRTrackerServerTorrentPeerListener)peer_listeners.get(i)).eventOccurred( this, requesting_peer, TRTrackerServerTorrentPeerListener.ET_ANNOUNCE, null );

							if ( reply != null ){

								List	limited_peers = (List)reply.get( "limited_peers" );

								if ( limited_peers != null ){

									if ( explicit_limited_peers == null ){

										explicit_limited_peers = new ArrayList<>();
									}

									for (int j=0;j<limited_peers.size();j++){

										Map peer_map = (Map)limited_peers.get(j);

										String	ip 		= (String)peer_map.get("ip");
										int		port 	= ((Long)peer_map.get( "port")).intValue();

										String	reuse_key = ip + ":" + port;

										TRTrackerServerPeerImpl peer	= (TRTrackerServerPeerImpl)peer_reuse_map.get( reuse_key );

										if ( peer != null && !explicit_limited_peers.contains( peer )){

											explicit_limited_peers.add( peer );
										}
									}
								}

								List	biased_peers = (List)reply.get( "biased_peers" );

								if ( biased_peers != null ){

									if ( explicit_biased_peers == null ){

										explicit_biased_peers = new ArrayList<>();
									}

									for (int j=0;j<biased_peers.size();j++){

										Map peer_map = (Map)biased_peers.get(j);

										String	ip 		= (String)peer_map.get("ip");
										int		port 	= ((Long)peer_map.get( "port")).intValue();

										String	reuse_key = ip + ":" + port;

										TRTrackerServerSimplePeer peer	= peer_reuse_map.get( reuse_key );

										if ( peer == null ){

											peer = new temporaryBiasedSeed( ip, port );
										}

										if ( !explicit_biased_peers.contains( peer )){

											explicit_biased_peers.add( peer );
										}
									}
								}

								remove_ips = (Set)reply.get( "remove_ips" );
							}
						}catch( Throwable e ){

							Debug.printStackTrace(e);
						}
					}
				}
			}

			boolean	requester_is_biased;

			if ( requesting_peer == null ){

				Set bp = server.getBiasedPeers();

				if ( bp == null ){

					requester_is_biased = false;

				}else{

					requester_is_biased = bp.contains( ip_address );
				}
			}else{

				requester_is_biased = requesting_peer.isBiased();
			}

			if ( 	caching_enabled &&
					explicit_limited_peers == null &&
					explicit_biased_peers == null &&
					!requester_is_biased &&
					remove_ips == null &&
					(!nat_warning) &&
					preprocess_map.size() == 0 &&	// don't cache if we've got pre-process stuff to add
					cache_millis > 0 &&
					num_want >= MIN_CACHE_ENTRY_SIZE &&
					total_peers >= TRTrackerServerImpl.getAnnounceCachePeerThreshold() &&
					crypto_level != TRTrackerServerPeer.CRYPTO_REQUIRED ){	// no cache for crypto required peers

					// too busy to bother with network position stuff

				network_position = null;

					// note that we've got to select a cache entry that is somewhat
					// relevant to the num_want param (but NOT greater than it)

					// remove stuff that's too old

				Iterator	it = announce_cache.keySet().iterator();

				while( it.hasNext() ){

					Integer	key = (Integer)it.next();

					announceCacheEntry	entry = (announceCacheEntry)announce_cache.get( key );

					if ( now - entry.getTime() > cache_millis ){

						it.remove();
					}
				}

					// look for an entry with a reasonable num_want
					// e.g. for 100 look between 50 and 100

				for (int i=num_want/10;i>num_want/20;i--){

					announceCacheEntry	entry = (announceCacheEntry)announce_cache.get(new Integer(i));

					if( entry != null ){

						if ( now - entry.getTime() > cache_millis ){

							announce_cache.remove( new Integer(i));

						}else{

								// make sure this is compatible

							if ( 	entry.getSendPeerIds() == send_peer_ids &&
									entry.getCompactMode() == compact_mode ){

								return( entry.getData());
							}
						}
					}
				}

				add_to_cache	= true;
			}


			LinkedList	rep_peers = new LinkedList();


			// System.out.println( "exportPeersToMap: num_want = " + num_want + ", max = " + max_peers );

				// if they want them all simply give them the set

			if ( num_want > 0 && explicit_limited_peers == null ){

				if ( num_want >= total_peers){

						// if they want them all simply give them the set

					for (int i=0;i<peer_list.size();i++){

						TRTrackerServerPeerImpl	peer = (TRTrackerServerPeerImpl)peer_list.get(i);

						if ( peer == null || peer == requesting_peer ){

						}else if ( now > peer.getTimeout()){

								// System.out.println( "removing timed out client '" + peer.getString());

							removePeer( peer, i, TRTrackerServerTorrentPeerListener.ET_TIMEOUT, null );

						}else if ( peer.getTCPPort() == 0 ){

							// a port of 0 means that the peer definitely can't accept incoming connections

						}else if ( crypto_level == TRTrackerServerPeer.CRYPTO_NONE && peer.getCryptoLevel() == TRTrackerServerPeer.CRYPTO_REQUIRED ){

							// don't return "crypto required" peers to those that can't correctly connect to them

							/* change this to make the explicit ones additional, not replacing
						}else if ( 	explicit_biased_peers != null &&
									peer.isBiased()){
							*/
								// if we have an explicit biased peer list and this peer is biased
								// skip here as we add them later

						}else if ( remove_ips != null && remove_ips.contains( new String( peer.getIP()))){

								// skippy skippy

						}else if ( include_seeds || !peer.isSeed()){

							Map rep_peer = new HashMap(3);

							if ( send_peer_ids ){

								rep_peer.put( "peer id", peer.getPeerId().getHash());
							}

							if ( compact_mode != COMPACT_MODE_NONE ){

								byte[]	peer_bytes = peer.getIPAddressBytes();

								if ( peer_bytes == null ){

									continue;
								}

								rep_peer.put( "ip", peer_bytes );

								if ( compact_mode >= COMPACT_MODE_AZ ){

									rep_peer.put( "azver", new Long( peer.getAZVer()));

									rep_peer.put( "azudp", new Long( peer.getUDPPort()));

									if ( peer.isSeed()){

										rep_peer.put( "azhttp", new Long( peer.getHTTPPort()));
									}

									if ( compact_mode >= COMPACT_MODE_XML ){

										rep_peer.put( "ip", peer.getIPAsRead() );

									}else{

										rep_peer.put( "azup", new Long( peer.getUpSpeed()));

										if ( peer.isBiased()){

											rep_peer.put( "azbiased", "" );
										}

										if ( network_position != null ){

											DHTNetworkPosition	peer_pos = peer.getNetworkPosition();

											if ( peer_pos != null && network_position.getPositionType() == peer_pos.getPositionType()){

												rep_peer.put( "azrtt", new Long( (long)peer_pos.estimateRTT(network_position )));
											}
										}
									}
								}
							}else{

								rep_peer.put( "ip", peer.getIPAsRead() );
							}

							rep_peer.put( "port", new Long( peer.getTCPPort()));

							if ( crypto_level != TRTrackerServerPeer.CRYPTO_NONE ){

								rep_peer.put( "crypto_flag", new Long( peer.getCryptoLevel() == TRTrackerServerPeer.CRYPTO_REQUIRED?1:0));
							}

							if ( peer.isBiased()){

								rep_peers.addFirst( rep_peer );

							}else{

								rep_peers.addLast( rep_peer );
							}
						}
					}
				}else{

					int	peer_list_size	= peer_list.size();

						// to avoid returning duplicates when doing the two-loop check
						// for nat selection we maintain an array of markers

					if ( duplicate_peer_checker.length < peer_list_size ){

						duplicate_peer_checker	= new byte[peer_list_size*2];

						duplicate_peer_checker_index	= 1;

					}else if ( duplicate_peer_checker.length > (peer_list_size*2)){

						duplicate_peer_checker	= new byte[(3*peer_list_size)/2];

						duplicate_peer_checker_index	= 1;

					}else{

						duplicate_peer_checker_index++;

						if ( duplicate_peer_checker_index == 0 ){

							Arrays.fill( duplicate_peer_checker, (byte)0);

							duplicate_peer_checker_index	= 1;
						}
					}

					boolean	peer_removed	= false;

					try{
							// got to suspend peer list compaction as we rely on the
							// list staying the same size during processing below

						peer_list_compaction_suspended	= true;

							// too costly to randomise as below. use more efficient but slightly less accurate
							// approach

							// two pass process if bad nat detection enabled

						int	added			= 0;
						//int	bad_nat_added	= 0;

						for (int bad_nat_loop=TRTrackerServerNATChecker.getSingleton().isEnabled()?0:1;bad_nat_loop<2;bad_nat_loop++){

							int	limit 	= num_want*2;	// some entries we find might not be usable
														// so in the limit search for more

							if ( num_want*3 > total_peers ){

								limit++;
							}

							int	biased_peers_count = 0;

							if ( biased_peers != null ){ // explicit are additional && explicit_biased_peers == null ){

								if ( biased_peers.size() > 1 ){

										// juggle things a bit

									Object	x = biased_peers.remove(0);

									biased_peers.add( random.nextInt( biased_peers.size() + 1 ), x);
								}

								biased_peers_count = Math.min( min_biased_peers, biased_peers.size());
							}

							for (int i=0;i<limit && added < num_want;i++){

								int	peer_index;

								TRTrackerServerPeerImpl	peer;

									// deal with bias up front

								if ( bad_nat_loop == 1 && i < biased_peers_count ){

									peer = (TRTrackerServerPeerImpl)biased_peers.get(i);

									peer_index = -1;	// don't know actual index and don't need to as biased peers processed separately

								}else{

									peer_index = random.nextInt(peer_list_size);

									peer = (TRTrackerServerPeerImpl)peer_list.get(peer_index);

									if ( peer == null || peer.isBiased()){

										continue;
									}
								}

								if ( now > peer.getTimeout()){

									removePeer( peer, TRTrackerServerTorrentPeerListener.ET_TIMEOUT, null );

									peer_removed	= true;

								}else if ( requesting_peer == peer || peer.getTCPPort() == 0 ){

										// a port of 0 means that the peer definitely can't accept incoming connections

								}else if ( crypto_level == TRTrackerServerPeer.CRYPTO_NONE && peer.getCryptoLevel() == TRTrackerServerPeer.CRYPTO_REQUIRED ){

									// don't return "crypto required" peers to those that can't correctly connect to them

								}else if ( remove_ips != null && remove_ips.contains( new String( peer.getIP()))){

									// skippy skippy

								}else if ( include_seeds || !peer.isSeed()){

									boolean	bad_nat = peer.isNATStatusBad();

									if ( 	( bad_nat_loop == 0 && !bad_nat ) ||
											( bad_nat_loop == 1 )){

										if ( peer_index == -1 || duplicate_peer_checker[peer_index] != duplicate_peer_checker_index ){

											if ( peer_index != -1 ){

												duplicate_peer_checker[peer_index] = duplicate_peer_checker_index;
											}

											//if ( bad_nat ){
											//
											//	bad_nat_added++;
											//}

											added++;

											Map rep_peer = new HashMap(3);

											if ( send_peer_ids ){

												rep_peer.put( "peer id", peer.getPeerId().getHash());
											}

											if ( compact_mode != COMPACT_MODE_NONE ){

												byte[]	peer_bytes = peer.getIPAddressBytes();

												if ( peer_bytes == null ){

													continue;
												}

												rep_peer.put( "ip", peer_bytes );

												if ( compact_mode >= COMPACT_MODE_AZ ){

													rep_peer.put( "azver", new Long( peer.getAZVer()));

													rep_peer.put( "azudp", new Long( peer.getUDPPort()));

													if ( peer.isSeed()){

														rep_peer.put( "azhttp", new Long( peer.getHTTPPort()));
													}

													if ( compact_mode >= COMPACT_MODE_XML ){

														rep_peer.put( "ip", peer.getIPAsRead() );

													}else{

														rep_peer.put( "azup", new Long( peer.getUpSpeed()));

														if ( peer.isBiased()){

															rep_peer.put( "azbiased", "" );
														}

														if ( network_position != null ){

															DHTNetworkPosition	peer_pos = peer.getNetworkPosition();

															if ( peer_pos != null && network_position.getPositionType() == peer_pos.getPositionType()){

																rep_peer.put( "azrtt", new Long( (long)peer_pos.estimateRTT(network_position )));
															}
														}
													}
												}
											}else{

												rep_peer.put( "ip", peer.getIPAsRead() );
											}

											rep_peer.put( "port", new Long( peer.getTCPPort()));

											if ( crypto_level != TRTrackerServerPeer.CRYPTO_NONE ){

												rep_peer.put( "crypto_flag", new Long( peer.getCryptoLevel() == TRTrackerServerPeer.CRYPTO_REQUIRED?1:0));
											}

											if ( peer.isBiased()){

												rep_peers.addFirst( rep_peer );

											}else{

												rep_peers.addLast( rep_peer );
											}
										}
									}
								}
							}
						}

						// System.out.println( "num_want = " + num_want + ", added = " + added + ", bad_nat = " + bad_nat_added );

					}finally{

						peer_list_compaction_suspended	= false;

						if ( peer_removed ){

							checkForPeerListCompaction( false );
						}
					}
				/*
				}else{
						// given up on this approach for the moment as too costly

						// randomly select the peers to return

					LinkedList	peers = new LinkedList( peer_map.keySet());

					int	added = 0;

					while( added < num_want && peers.size() > 0 ){

						String	key = (String)peers.remove(random.nextInt(peers.size()));

						TRTrackerServerPeerImpl	peer = (TRTrackerServerPeerImpl)peer_map.get(key);

						if ( now > peer.getTimeout()){

							removePeer( peer, TRTrackerServerTorrentPeerListener.ET_TIMEOUT, null );

						}else if ( peer.getTCPPort() == 0 ){

								// a port of 0 means that the peer definitely can't accept incoming connections

						}else if ( crypto_level == TRTrackerServerPeer.CRYPTO_NONE && peer.getCryptoLevel() == TRTrackerServerPeer.CRYPTO_REQUIRED ){

							// don't return "crypto required" peers to those that can't correctly connect to them

						}else if ( include_seeds || !peer.isSeed()){

							added++;

							Map rep_peer = new HashMap(3);	// don't use TreeMap as default is "compact"
															// so we never actually encode anyway

							if ( send_peer_ids ){

								rep_peer.put( "peer id", peer.getPeerId().getHash());
							}

							if ( compact_mode != COMPACT_MODE_NONE ){

								byte[]	peer_bytes = peer.getIPBytes();

								if ( peer_bytes == null ){

									continue;
								}

								rep_peer.put( "ip", peer_bytes );

								if ( compact_mode >= COMPACT_MODE_AZ ){

									rep_peer.put( "azver", new Long( peer.getAZVer()));

									rep_peer.put( "azudp", new Long( peer.getUDPPort()));

									if ( peer.isSeed()){

										rep_peer.put( "azhttp", new Long( peer.getHTTPPort()));
									}
								}
							}else{
								rep_peer.put( "ip", peer.getIPAsRead() );
							}

							rep_peer.put( "port", new Long( peer.getTCPPort()));

							if ( crypto_level != TRTrackerServerPeer.CRYPTO_NONE ){

								rep_peer.put( "crypto_flag", new Long( peer.getCryptoLevel() == TRTrackerServerPeer.CRYPTO_REQUIRED?1:0));
							}

							rep_peers.add( rep_peer );

						}
					}*/
				}
			}

			if ( 	include_seeds &&
					explicit_limited_peers == null &&
					!send_peer_ids &&
					seed_count < 3 &&
					queued_peers != null ){

				Iterator	it = queued_peers.iterator();

				List	added = new ArrayList( QUEUED_PEERS_ADD_MAX );

				while( it.hasNext() && num_want > rep_peers.size() && added.size() < QUEUED_PEERS_ADD_MAX ){

					QueuedPeer	peer = (QueuedPeer)it.next();

					if ( peer.isTimedOut( now )){

						it.remove();

					}else if ( crypto_level == TRTrackerServerPeer.CRYPTO_NONE && peer.getCryptoLevel() == TRTrackerServerPeer.CRYPTO_REQUIRED ){

							// don't return "crypto required" peers to those that can't correctly connect to them

					}else if ( remove_ips != null && remove_ips.contains( peer.getIP())){

							// skippy skippy

					}else{

						Map rep_peer = new HashMap(3);

						if ( compact_mode != COMPACT_MODE_NONE ){

							byte[]	peer_bytes = peer.getIPAddressBytes();

							if ( peer_bytes == null ){

								continue;
							}

							rep_peer.put( "ip", peer_bytes );

							if ( compact_mode >= COMPACT_MODE_AZ ){

								rep_peer.put( "azver", new Long( peer.getAZVer()));

								rep_peer.put( "azudp", new Long( peer.getUDPPort()));

								if ( peer.isSeed()){

									rep_peer.put( "azhttp", new Long( peer.getHTTPPort()));
								}

								if ( compact_mode >= COMPACT_MODE_XML ){

									rep_peer.put( "ip", peer.getIPAsRead());
								}
							}

						}else{

							rep_peer.put( "ip", peer.getIPAsRead());
						}

						rep_peer.put( "port", new Long( peer.getTCPPort()));

						if ( crypto_level != TRTrackerServerPeer.CRYPTO_NONE ){

							rep_peer.put( "crypto_flag", new Long( peer.getCryptoLevel() == TRTrackerServerPeer.CRYPTO_REQUIRED?1:0));
						}

						// System.out.println( "added queued peer " + peer.getString());

						rep_peers.addLast( rep_peer );

						added.add( peer );

							// it'll be added back in below, don't worry!

						it.remove();
					}
				}

				for (int i=0;i<added.size();i++){

					queued_peers.add( added.get(i));
				}
			}

			Map	root = new TreeMap();	// user TreeMap to pre-sort so encoding quicker

			if ( preprocess_map.size() > 0 ){

				root.putAll( preprocess_map );
			}

			if ( explicit_limited_peers != null ){

				for (int i=0;i<explicit_limited_peers.size();i++){

					num_want--;

					TRTrackerServerSimplePeer  peer = explicit_limited_peers.get(i);

					exportPeer(rep_peers, peer, send_peer_ids, compact_mode, crypto_level, network_position);
				}
			}

			if ( explicit_biased_peers != null ){

				for (int i=0;i<explicit_biased_peers.size();i++){

					num_want--;

					TRTrackerServerSimplePeer peer = explicit_biased_peers.get(i);

					exportPeer(rep_peers, peer, send_peer_ids, compact_mode, crypto_level, network_position);
				}
			}

			if ( explicit_manual_biased_peers != null ){

				if ( requesting_peer != null && !requesting_peer.isSeed()){

					Object[]	explicit_peer = (Object[])explicit_manual_biased_peers.get( explicit_next_peer++ );

					if ( explicit_next_peer == explicit_manual_biased_peers.size()){

						explicit_next_peer = 0;
					}

					Map rep_peer = new HashMap(3);

					if ( send_peer_ids ){

						byte[]	peer_id = new byte[20];

						random.nextBytes( peer_id );

						rep_peer.put( "peer id", peer_id );
					}

					if ( compact_mode != COMPACT_MODE_NONE ){

						byte[]	peer_bytes = (byte[])explicit_peer[1];

						rep_peer.put( "ip", peer_bytes );

						if ( compact_mode >= COMPACT_MODE_AZ ){

							rep_peer.put( "azver", new Long( 0 ));	// non-az

							rep_peer.put( "azudp", new Long( 0 ));

							rep_peer.put( "azup", new Long( 0 ));

							rep_peer.put( "azbiased", "" );
						}
					}else{

						rep_peer.put( "ip", ((String)explicit_peer[0]).getBytes());
					}

					rep_peer.put( "port", new Long( ((Integer)explicit_peer[2]).intValue()));

					if ( crypto_level != TRTrackerServerPeer.CRYPTO_NONE ){

						rep_peer.put( "crypto_flag", new Long( 0 ));
					}

					rep_peers.addFirst( rep_peer );
				}
			}

			int			num_peers_returned	= rep_peers.size();
			Iterator	it					= rep_peers.iterator();

			if ( compact_mode == COMPACT_MODE_AZ ){

				byte[]	compact_peers = new byte[num_peers_returned*9];

				int	index = 0;

				while( it.hasNext()){

					Map	rep_peer = (Map)it.next();

					byte[] 	ip 				= (byte[])rep_peer.get( "ip" );
					int		tcp_port		= ((Long)rep_peer.get( "port" )).intValue();
					int		udp_port		= ((Long)rep_peer.get( "azudp" )).intValue();
					Long	crypto_flag_l	= (Long)rep_peer.get( "crypto_flag" );
					byte	crypto_flag		= crypto_flag_l==null?0:crypto_flag_l.byteValue();

					int	pos = index*9;

					System.arraycopy( ip, 0, compact_peers, pos, 4 );

					pos += 4;

					compact_peers[pos++] = (byte)(tcp_port>>8);
					compact_peers[pos++] = (byte)(tcp_port&0xff);
					compact_peers[pos++] = (byte)(udp_port>>8);
					compact_peers[pos++] = (byte)(udp_port&0xff);
					compact_peers[pos++] = crypto_flag;

					index++;
				}

				root.put( "peers", compact_peers );

				root.put( "azcompact", new Long(1));

			}else if ( compact_mode == COMPACT_MODE_AZ_2 ){

				List	compact_peers = new ArrayList( num_peers_returned );

				while( it.hasNext()){

					Map	rep_peer = (Map)it.next();

					Map	peer = new HashMap();

					compact_peers.add( peer );

					byte[] 	ip 				= (byte[])rep_peer.get( "ip" );

					peer.put( "i", ip );

					int		tcp_port		= ((Long)rep_peer.get( "port" )).intValue();

					peer.put( "t", new byte[]{ (byte)(tcp_port>>8), (byte)(tcp_port&0xff) });

					int		udp_port		= ((Long)rep_peer.get( "azudp" )).intValue();

					if ( udp_port != 0 ){

						if ( udp_port == tcp_port ){

							peer.put( "u", new byte[0] );

						}else{

							peer.put( "u", new byte[]{ (byte)(udp_port>>8), (byte)(udp_port&0xff) });
						}
					}

					Long	http_port_l	= (Long)rep_peer.get( "azhttp" );

					if ( http_port_l != null ){

						int	http_port = http_port_l.intValue();

						if ( http_port != 0 ){

							peer.put( "h", new byte[]{ (byte)(http_port>>8), (byte)(http_port&0xff) });
						}
					}

					Long	crypto_flag_l	= (Long)rep_peer.get( "crypto_flag" );
					byte	crypto_flag		= crypto_flag_l==null?0:crypto_flag_l.byteValue();

					if ( crypto_flag != 0 ){

						peer.put( "c", new byte[]{ crypto_flag } );
					}

					Long	az_ver_l	= (Long)rep_peer.get( "azver" );
					byte	az_ver		= az_ver_l==null?0:az_ver_l.byteValue();

					if ( az_ver != 0 ){

						peer.put( "v", new Long(az_ver));
					}

					Long up_speed = (Long)rep_peer.get( "azup" );

					if ( up_speed != null && up_speed.longValue() != 0 ){

						peer.put( "s", up_speed );
					}

					Long rtt = (Long)rep_peer.get( "azrtt" );

					if ( rtt != null ){

						peer.put( "r", rtt );
					}

					if ( rep_peer.containsKey("azbiased")){

						peer.put( "b", new Long(1));
					}
				}

				root.put( "peers", compact_peers );

				root.put( "azcompact", new Long(2));

			}else if ( compact_mode == COMPACT_MODE_XML ){

				List	xml_peers = new ArrayList( num_peers_returned );

				while( it.hasNext()){

					Map	rep_peer = (Map)it.next();

					Map	peer = new HashMap();

					xml_peers.add( peer );

					peer.put( "ip", rep_peer.get( "ip" ) );

					peer.put( "tcp", rep_peer.get( "port" ));

					int		udp_port	= ((Long)rep_peer.get( "azudp" )).intValue();

					if ( udp_port != 0 ){

						peer.put( "udp", new Long( udp_port ));
					}

					Long	http_port_l	= (Long)rep_peer.get( "azhttp" );

					if ( http_port_l != null ){

						int	http_port = http_port_l.intValue();

						if ( http_port != 0 ){

							peer.put( "http", new Long( http_port ));
						}
					}
				}

				root.put( "peers", xml_peers );

			}else{

				byte[]	crypto_flags = null;

				if ( crypto_level != TRTrackerServerPeer.CRYPTO_NONE ){

					crypto_flags = new byte[num_peers_returned];
				}

				if ( compact_mode == COMPACT_MODE_NORMAL ){

					byte[]	compact_peers = new byte[num_peers_returned*6];

					int	index = 0;

					int	num_ipv4 = 0;
					int num_ipv6 = 0;

					while( it.hasNext()){

						Map	rep_peer = (Map)it.next();

						byte[] 	ip 		= (byte[])rep_peer.get( "ip" );

						if ( ip.length > 4 ){

							num_ipv6++;

								// continue and fill in crypto return

						}else{

							num_ipv4++;

							if ( num_ipv6 == 0 ){

								int		port	= ((Long)rep_peer.get( "port" )).intValue();

								int	pos = index*6;

								System.arraycopy( ip, 0, compact_peers, pos, 4 );

								pos += 4;

								compact_peers[pos++] = (byte)(port>>8);
								compact_peers[pos++] = (byte)(port&0xff);
							}
						}

						if ( crypto_flags != null ){

							Long	crypto_flag = (Long)rep_peer.remove( "crypto_flag" );

							crypto_flags[index] = crypto_flag.byteValue();
						}

						index++;
					}

						// inefficient hack to support ipv6 compact for the moment

					if ( num_ipv6 > 0 ){

						byte[]	compact_peers_v4 = new byte[num_ipv4*6];
						byte[]	compact_peers_v6 = new byte[num_ipv6*18];

						it	= rep_peers.iterator();

						int	v4_index	= 0;
						int v6_index	= 0;

						while( it.hasNext()){

							Map	rep_peer = (Map)it.next();

							byte[] 	ip 		= (byte[])rep_peer.get( "ip" );

							int		port	= ((Long)rep_peer.get( "port" )).intValue();

							if ( ip.length > 4 ){

								int	pos = v6_index*18;

								System.arraycopy( ip, 0, compact_peers_v6, pos, 16 );

								pos += 16;

								compact_peers_v6[pos++] = (byte)(port>>8);
								compact_peers_v6[pos++] = (byte)(port&0xff);

								v6_index++;

							}else{

								int	pos = v4_index*6;

								System.arraycopy( ip, 0, compact_peers_v4, pos, 4 );

								pos += 4;

								compact_peers_v4[pos++] = (byte)(port>>8);
								compact_peers_v4[pos++] = (byte)(port&0xff);

								v4_index++;
							}
						}

						if ( compact_peers_v4.length > 0 ){

							root.put( "peers", compact_peers_v4 );
						}

						if ( compact_peers_v6.length > 0 ){

							root.put( "peers6", compact_peers_v6 );
						}
					}else{

						root.put( "peers", compact_peers );
					}
				}else{

					int	index = 0;

					while( it.hasNext()){

						Map	rep_peer = (Map)it.next();

						if ( crypto_flags != null ){

							Long	crypto_flag = (Long)rep_peer.remove( "crypto_flag" );

							crypto_flags[index] = crypto_flag.byteValue();
						}

						index++;
					}

					root.put( "peers", rep_peers );
				}

				if ( crypto_flags != null ){

					root.put( "crypto_flags", crypto_flags );
				}
			}

			root.put( "interval", new Long( interval ));

			root.put( "min interval", new Long( min_interval ));

			if ( nat_warning ){

				requesting_peer.setNATStatus( TRTrackerServerPeerImpl.NAT_CHECK_FAILED_AND_REPORTED );

				root.put(
						"warning message",
						("Unable to connect to your incoming data port (" + requesting_peer.getIP() + ":" + requesting_peer.getTCPPort() +"). " +
						 "This will result in slow downloads. Please check your firewall/router settings").getBytes());
			}

				// also include scrape details

			root.put( "complete", new Long( getSeedCountForScrape( requester_is_biased )));
			root.put( "incomplete", new Long( getLeecherCount() ));
			root.put( "downloaded", new Long(stats.getCompletedCount()));

			if ( add_to_cache ){

				announce_cache.put( new Integer((num_peers_returned+9)/10), new announceCacheEntry( root, send_peer_ids, compact_mode ));
			}

			return( root );

		}finally{

			this_mon.exit();
		}
	}


	private void
	exportPeer(
		LinkedList					rep_peers,
		TRTrackerServerSimplePeer	peer,
		boolean						send_peer_ids,
		byte						compact_mode,
		byte						crypto_level,
		DHTNetworkPosition			network_position )
	{
		Map rep_peer = new HashMap(3);

		if ( send_peer_ids ){

			rep_peer.put( "peer id", peer.getPeerId().getHash());
		}

		if ( compact_mode != COMPACT_MODE_NONE ){

			byte[]	peer_bytes = peer.getIPAddressBytes();

			if ( peer_bytes == null ){

				return;
			}

			rep_peer.put( "ip", peer_bytes );

			if ( compact_mode >= COMPACT_MODE_AZ ){

				rep_peer.put( "azver", new Long( peer.getAZVer()));

				rep_peer.put( "azudp", new Long( peer.getUDPPort()));

				if ( peer.isSeed()){

					rep_peer.put( "azhttp", new Long( peer.getHTTPPort()));
				}

				if ( compact_mode >= COMPACT_MODE_XML ){

					rep_peer.put( "ip", peer.getIPAsRead() );

				}else{

					rep_peer.put( "azup", new Long( peer.getUpSpeed()));

					if ( peer.isBiased()){

						rep_peer.put( "azbiased", "" );
					}

					if ( network_position != null ){

						DHTNetworkPosition	peer_pos = peer.getNetworkPosition();

						if ( peer_pos != null && network_position.getPositionType() == peer_pos.getPositionType()){

							rep_peer.put( "azrtt", new Long( (long)peer_pos.estimateRTT(network_position )));
						}
					}
				}
			}
		}else{

			rep_peer.put( "ip", peer.getIPAsRead() );
		}

		rep_peer.put( "port", new Long( peer.getTCPPort()));

		if ( crypto_level != TRTrackerServerPeer.CRYPTO_NONE ){

			rep_peer.put( "crypto_flag", new Long( peer.getCryptoLevel() == TRTrackerServerPeer.CRYPTO_REQUIRED?1:0));
		}

		if ( peer.isBiased()){

			rep_peers.addFirst( rep_peer );

		}else{

			rep_peers.addLast( rep_peer );
		}
	}

	public Map
	exportScrapeToMap(
		String		url_parameters,
		String		ip_address,
		boolean		allow_cache )

		throws TRTrackerServerException
	{
		try{
			this_mon.enter();

			handleRedirects( url_parameters, ip_address, true );

			stats.addScrape();

			long now = SystemTime.getCurrentTime();

            long diff = now - last_scrape_calc_time;

			if( allow_cache && last_scrape != null && diff < TRTrackerServerImpl.getScrapeCachePeriod() && !(diff < 0) ){

			  return( last_scrape );
			}

			last_scrape 			= new TreeMap();
			last_scrape_calc_time	= now;

			boolean requester_is_biased;

			Set bp = server.getBiasedPeers();

			if ( bp == null ){

				requester_is_biased = false;

			}else{

				requester_is_biased = bp.contains( ip_address );
			}

			last_scrape.put( "complete", new Long( getSeedCountForScrape( requester_is_biased )));
			last_scrape.put( "incomplete", new Long( getLeecherCount()));
			last_scrape.put( "downloaded", new Long(stats.getCompletedCount()));

			return( last_scrape );

		}finally{

			this_mon.exit();
		}
	}

	protected void
	checkTimeouts()
	{
		try{
			this_mon.enter();

			long	now = SystemTime.getCurrentTime();

			int	new_bad_NAT_count	= 0;

				// recalc seed count as this drifts for some reason (maybe seeds switching back to leechers
				// on recheck fail, not sure)

			int new_seed_count 	= 0;

			try{
				peer_list_compaction_suspended	= true;

				for (int i=0;i<peer_list.size();i++){

					TRTrackerServerPeerImpl	peer = (TRTrackerServerPeerImpl)peer_list.get(i);

					if ( peer == null ){

						continue;
					}

					if ( now > peer.getTimeout()){

						removePeer( peer, i, TRTrackerServerTorrentPeerListener.ET_TIMEOUT, null );

					}else{

						if ( peer.isSeed()){

							new_seed_count++;
						}

						if ( peer.isNATStatusBad()){

							new_bad_NAT_count++;
						}
					}
				}
			}finally{

				peer_list_compaction_suspended	= false;
			}

			bad_NAT_count	= new_bad_NAT_count;
			seed_count		= new_seed_count;

			if ( removed_count > 1000 ){

				removed_count = 0;

				checkForPeerListCompaction( true );

					// rehash

				HashMap	new_peer_map 		= new HashMap(peer_map);
				HashMap	new_peer_reuse_map	= new HashMap(peer_reuse_map);

				peer_map 		= new_peer_map;
				peer_reuse_map	= new_peer_reuse_map;

			}else{

				checkForPeerListCompaction( false );
			}

			Iterator	it = lightweight_seed_map.values().iterator();

			while( it.hasNext()){

				lightweightSeed	lws = (lightweightSeed)it.next();

				if ( now > lws.getTimeout()){

					it.remove();
				}
			}
		}finally{

			this_mon.exit();
		}
	}

	protected void
	checkForPeerListCompaction(
		boolean	force )
	{
		if ( peer_list_hole_count > 0 && !peer_list_compaction_suspended ){

			if ( force || peer_list_hole_count > peer_map.size()/10 ){

				ArrayList	new_peer_list = new ArrayList( peer_list.size() - (peer_list_hole_count/2));

				int	holes_found = 0;

				for (int i=0;i<peer_list.size();i++){

					Object	obj = peer_list.get(i);

					if ( obj == null ){

						holes_found++;
					}else{

						new_peer_list.add( obj );
					}
				}

				if( holes_found != peer_list_hole_count ){

					Debug.out( "TRTrackerTorrent:compactHoles: count mismatch" );
				}

				peer_list	= new_peer_list;

				peer_list_hole_count	= 0;
			}
		}
	}

	protected void
	updateXferStats(
		int		bytes_in,
		int		bytes_out )
	{
		stats.addXferStats( bytes_in, bytes_out );
	}

	@Override
	public TRTrackerServerTorrentStats
	getStats()
	{
		return( stats );
	}

	protected int
	getPeerCount()
	{
		return( peer_map.size() + lightweight_seed_map.size());
	}

	protected int
	getSeedCount()
	{
		if ( seed_count < 0 ){

			Debug.out( "seed count negative" );
		}

		return( seed_count + lightweight_seed_map.size());
	}

	protected int
	getSeedCountForScrape(
		boolean	requester_is_biased )
	{
		int seeds	= getSeedCount();

		if ( biased_peers != null && !requester_is_biased ){

			int	bpc = 0;

			Iterator it = biased_peers.iterator();

			while( it.hasNext()){

				TRTrackerServerPeerImpl bp = (TRTrackerServerPeerImpl)it.next();

				if ( bp.isSeed()){

					seeds--;

					bpc++;
				}
			}

			if ( seeds < 0 ){

				seeds = 0;
			}

				// retain at least one biased seed

			if ( bpc > 0 ){

				seeds++;
			}
		}

			// if we have any queued then lets add at least one in to indicate potential

		int	queued = getQueuedCount();

		if ( queued > 0 ){

			seeds++;
		}

		return( seeds );
	}

	protected int
	getLeecherCount()
	{
			// this isn't synchronised so could possible end up negative

		int	res = peer_map.size() - seed_count;

		return( res<0?0:res );
	}

	@Override
	public TRTrackerServerPeer[]
	getPeers()
	{
		try{
			this_mon.enter();

			TRTrackerServerPeer[]	res = new TRTrackerServerPeer[peer_map.size()];

			peer_map.values().toArray( res );

			return( res );

		}finally{

			this_mon.exit();
		}
	}

	protected int
	getQueuedCount()
	{
		List	l = queued_peers;

		if ( l == null ){

			return( 0 );
		}

		return( l.size());
	}

	@Override
	public TRTrackerServerPeerBase[]
	getQueuedPeers()
	{
		try{
			this_mon.enter();

			if ( queued_peers == null ){

				return( new TRTrackerServerPeerBase[0] );
			}

			TRTrackerServerPeerBase[]	res = new TRTrackerServerPeerBase[queued_peers.size()];

			queued_peers.toArray( res );

			return( res );

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public HashWrapper
	getHash()
	{
		return( hash );
	}

	@Override
	public void
	addExplicitBiasedPeer(
		String		ip,
		int			port )
	{
		byte[]	bytes = HostNameToIPResolver.hostAddressToBytes( ip );

		if ( bytes != null ){

			try{
				this_mon.enter();

				if ( explicit_manual_biased_peers == null  ){

					explicit_manual_biased_peers = new ArrayList();
				}

				explicit_manual_biased_peers.add( new Object[]{ ip, bytes, new Integer( port )});

			}finally{

				this_mon.exit();
			}
		}
	}
	@Override
	public void
	setRedirects(
		URL[]		urls )
	{
		try{
			this_mon.enter();

			redirects	= urls;

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public URL[]
	getRedirects()
	{
		return( redirects );
	}

	protected void
	handleRedirects(
		String	url_parameters,
		String	real_ip_address,
		boolean	scrape )

		throws TRTrackerServerException
	{
		if ( redirects != null ){

			if (url_parameters.contains("permredirect")){

				Debug.out( "redirect recursion" );

				throw( new TRTrackerServerException( "redirection recursion not supported" ));
			}

			URL	redirect = redirects[(real_ip_address.hashCode()&0x7fffffff)%redirects.length];

			Map	headers = new HashMap();

			String	redirect_str = redirect.toString();

			if ( scrape ){

				int	pos = redirect_str.indexOf( "/announce" );

				if ( pos == -1 ){

					return;
				}

				redirect_str	= redirect_str.substring( 0, pos ) + "/scrape" + redirect_str.substring( pos + 9 );
			}

			if ( redirect_str.indexOf('?' ) == -1 ){

				redirect_str += "?";

			}else{

				redirect_str += "&";
			}

			redirect_str += "permredirect=1";

			if ( url_parameters.length() > 0 ){

				redirect_str += "&" + url_parameters;
			}

			System.out.println( "redirect -> " + redirect_str );

			headers.put( "Location", redirect_str);

			throw( new TRTrackerServerException(301, "Moved Permanently", headers ));
		}
	}
	@Override
	public void
	addListener(
		TRTrackerServerTorrentListener	l )
	{
		listeners.add(l);

		if ( deleted ){

			l.deleted(this);
		}
	}

	@Override
	public void
	removeListener(
		TRTrackerServerTorrentListener	l )
	{
		listeners.remove(l);
	}

	protected void
	peerEvent(
		TRTrackerServerPeer		peer,
		int						event,
		String					url_parameters )

		throws TRTrackerServerException
	{
		if ( peer_listeners != null ){

			for (int i=0;i<peer_listeners.size();i++){

				try{
					((TRTrackerServerTorrentPeerListener)peer_listeners.get(i)).eventOccurred( this, peer, event, url_parameters );

				}catch( TRTrackerServerException e ){

					throw( e );

				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}
			}
		}
	}

	@Override
	public void
	addPeerListener(
		TRTrackerServerTorrentPeerListener	l )
	{
		if ( peer_listeners == null ){

			peer_listeners = new ArrayList();
		}

		peer_listeners.add( l );
	}

	@Override
	public void
	removePeerListener(
		TRTrackerServerTorrentPeerListener	l )
	{
		if ( peer_listeners != null ){

			peer_listeners.remove(l);
		}
	}

	@Override
	public void
	disableCaching()
	{
		caching_enabled	= false;
	}

	public boolean
	isCachingEnabled()
	{
		return( caching_enabled );
	}

	public int
	getBadNATPeerCount()
	{
		return( bad_NAT_count );
	}

	protected void
	delete()
	{
		deleted	= true;

		for (int i=0;i<listeners.size();i++){

			((TRTrackerServerTorrentListener)listeners.get(i)).deleted(this);
		}
	}

	static class
	announceCacheEntry
	{
		protected final Map		data;
		protected final boolean	send_peer_ids;
		protected final byte		compact_mode;
		protected final long		time;

		protected
		announceCacheEntry(
			Map		_data,
			boolean	_send_peer_ids,
			byte	_compact_mode )
		{
			data			= _data;
			send_peer_ids	= _send_peer_ids;
			compact_mode	= _compact_mode;
			time			= SystemTime.getCurrentTime();
		}

		protected boolean
		getSendPeerIds()
		{
			return( send_peer_ids );
		}

		protected byte
		getCompactMode()
		{
			return( compact_mode );
		}

		protected long
		getTime()
		{
			return( time );
		}

		protected Map
		getData()
		{
			return( data );
		}
	}

	protected static class
	lightweightSeed
	{
		final long	timeout;
		final long	last_contact_time;
		final long	uploaded;
		final byte	nat_status;

		protected
		lightweightSeed(
			long	_now,
			long	_timeout,
			long	_uploaded,
			byte	_nat_status )
		{
			last_contact_time	= _now;
			timeout				= _timeout;
			uploaded			= _uploaded;
			nat_status			= _nat_status;
		}

		protected long
		getTimeout()
		{
			return( timeout );
		}
		protected long
		getLastContactTime()
		{
			return( last_contact_time );
		}

		protected long
		getUploaded()
		{
			return( uploaded );
		}

		protected byte
		getNATStatus()
		{
			return( nat_status );
		}
	}

	protected static class
	QueuedPeer
		implements TRTrackerServerPeerBase
	{
		private static final byte	FLAG_SEED			= 0x01;
		private static final byte	FLAG_BIASED			= 0x02;

		private final short	tcp_port;
		private final short	udp_port;
		private final short	http_port;
		private byte[]	ip;
		private final byte	crypto_level;
		private final byte	az_ver;
		private int		create_time_secs;
		private final int		timeout_secs;
		private byte	flags;

		protected
		QueuedPeer(
			String		_ip_str,
			int			_tcp_port,
			int			_udp_port,
			int			_http_port,
			byte		_crypto_level,
			byte		_az_ver,
			int			_timeout_secs,
			boolean		_seed,
			boolean		_biased )
		{
			try{
				ip = _ip_str.getBytes( Constants.BYTE_ENCODING );

			}catch( UnsupportedEncodingException e  ){

				Debug.printStackTrace(e);
			}

			tcp_port	= (short)_tcp_port;
			udp_port	= (short)_udp_port;
			http_port	= (short)_http_port;
			crypto_level	= _crypto_level;
			az_ver			= _az_ver;

			setFlag( FLAG_SEED, 		_seed );
			setFlag( FLAG_BIASED, 		_biased );

			create_time_secs 	= (int)( SystemTime.getCurrentTime()/1000 );

			timeout_secs		= _timeout_secs * TRTrackerServerImpl.CLIENT_TIMEOUT_MULTIPLIER;
		}

		protected boolean
		sameAs(
			TRTrackerServerPeerImpl	peer )
		{
			return( tcp_port == peer.getTCPPort() &&
					Arrays.equals( ip, peer.getIPAsRead()) &&
					isIPOverride() == peer.isIPOverride());
		}

		protected boolean
		sameAs(
			QueuedPeer	other )
		{
			return( tcp_port == other.tcp_port &&
					Arrays.equals( ip,other.ip ));
		}

		protected byte[]
        getIPAsRead()
		{
			return( ip );
		}

		@Override
		public String
        getIP()
		{
			try{
				return( new String( ip, Constants.BYTE_ENCODING ));

			}catch( UnsupportedEncodingException e ){

				return( new String( ip ));
			}
		}

		protected boolean
		isSeed()
		{
			return( getFlag( FLAG_SEED ));
		}

		protected void
		setBiased(
			boolean	_biased )
		{
			setFlag( FLAG_BIASED, _biased );
		}

		protected boolean
		isBiased()
		{
			return( getFlag( FLAG_BIASED ));
		}

		protected boolean
		isIPOverride()
		{
				// we never allow IP override queued peers

			return( false );
		}

		protected void
		setFlag(
			byte		flag,
			boolean		value )
		{
			if ( value ){

				flags |= flag;

			}else{

				flags &= ~flag;
			}
		}

		protected boolean
		getFlag(
			byte		flag )
		{
			return((flags & flag ) != 0 );
		}

		protected byte[]
		getIPAddressBytes()
		{
			try{
				return( HostNameToIPResolver.hostAddressToBytes( new String( ip, Constants.BYTE_ENCODING )));

			}catch( UnsupportedEncodingException e  ){

				Debug.printStackTrace(e);

				return( null );
			}
		}

		@Override
		public int
		getTCPPort()
		{
			return( tcp_port & 0xffff );
		}

		public int
		getUDPPort()
		{
			return( udp_port & 0xffff );
		}

		@Override
		public int
		getHTTPPort()
		{
			return( http_port & 0xffff );
		}

		protected byte
		getCryptoLevel()
		{
			return( crypto_level );
		}

		protected byte
		getAZVer()
		{
			return( az_ver );
		}

		protected int
		getCreateTime()
		{
			return( create_time_secs );
		}

		protected boolean
		isTimedOut(
			long	now_millis )
		{
			int	now_secs = (int)(now_millis/1000);

			if ( now_secs < create_time_secs ){

				create_time_secs = now_secs;
			}

			return( create_time_secs + timeout_secs < now_secs );
		}

		@Override
		public int
		getSecsToLive()
		{
			int	now_secs = (int)(SystemTime.getCurrentTime()/1000);

			if ( now_secs < create_time_secs ){

				create_time_secs = now_secs;
			}

			return(( create_time_secs + timeout_secs ) - now_secs );
		}

		protected String
		getString()
		{
			return( new String(ip) + ":" + getTCPPort() + "/" + getUDPPort() + "/" + getCryptoLevel());
		}
	}

	private static class
	temporaryBiasedSeed
		implements TRTrackerServerSimplePeer
	{
		private final String			ip;
		private final int				tcp_port;
		private final HashWrapper		peer_id;

		protected
		temporaryBiasedSeed(
			String			_ip,
			int				_tcp_port )
		{
			ip			= _ip;
			tcp_port	= _tcp_port;

			peer_id = new HashWrapper( RandomUtils.nextHash());
		}

		@Override
		public byte[]
    	getIPAsRead()
		{
			try{

				return( ip.getBytes( Constants.BYTE_ENCODING ));

			}catch( Throwable e ){

    			return( ip.getBytes());
    		}
		}

    	@Override
	    public byte[]
    	getIPAddressBytes()
    	{
    		try{
    			return( InetAddress.getByName( ip ).getAddress());

    		}catch( Throwable e ){

    			return( null );
    		}
    	}

    	@Override
	    public HashWrapper
       	getPeerId()
    	{
    		return( peer_id );
    	}

    	@Override
	    public int
    	getTCPPort()
    	{
    		return( tcp_port );
    	}

    	@Override
	    public int
    	getUDPPort()
    	{
    		return( 0 );
    	}

    	@Override
	    public int
    	getHTTPPort()
    	{
    		return( 0 );
    	}

    	@Override
	    public boolean
    	isSeed()
    	{
    		return( true );
    	}

    	@Override
	    public boolean
    	isBiased()
    	{
    		return( true );
    	}

    	@Override
	    public byte
    	getCryptoLevel()
    	{
    		return( TRTrackerServerPeer.CRYPTO_NONE );
    	}

    	@Override
	    public byte
    	getAZVer()
    	{
    		return( 0 );
    	}

    	@Override
	    public int
    	getUpSpeed()
    	{
    		return( 0 );
    	}

    	@Override
	    public DHTNetworkPosition
    	getNetworkPosition()
    	{
    		return( null );
    	}
	}

	@Override
	public String
	getString()
	{
		String	redirect;

		if ( redirects == null ){

			redirect = "none";

		}else{

			redirect	= "";

			for (int i=0;i<redirects.length;i++){

				redirect += (i==0?"":",") + redirects[i];
			}
		}

		return( "seeds=" + getSeedCount() + ",leechers=" + getLeecherCount() + ", redirect=" + redirect );
	}
}
