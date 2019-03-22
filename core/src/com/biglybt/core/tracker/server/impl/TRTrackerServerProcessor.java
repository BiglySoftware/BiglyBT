/*
 * File    : TRTrackerServerProcessor.java
 * Created : 20-Jan-2004
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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.dht.netcoords.DHTNetworkPosition;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.tracker.server.TRTrackerServerException;
import com.biglybt.core.tracker.server.TRTrackerServerPeer;
import com.biglybt.core.tracker.server.TRTrackerServerRequest;
import com.biglybt.core.tracker.util.TRTrackerUtils;
import com.biglybt.core.util.*;

public abstract class
TRTrackerServerProcessor
	extends ThreadPoolTask
{
	private static final boolean QUEUE_TEST	= false;

	static{
		if ( QUEUE_TEST ){
			System.out.println( "**** TRTrackerServerProcessor::QUEUE_TEST ****" );
		}
	}

	private TRTrackerServerImpl		server;

	private long					start;
	private int						request_type;

	protected TRTrackerServerTorrentImpl
	processTrackerRequest(
		TRTrackerServerImpl			_server,
		String						request,
		Map[]						root_out,		// output
		TRTrackerServerPeerImpl[]	peer_out,		// output
		int							_request_type,
		byte[][]					hashes,
		String						link,
		String						scrape_flags,
		HashWrapper					peer_id,
		boolean						no_peer_id,
		byte						compact_mode,
		String						key,
		String						event,
		boolean						stop_to_queue,
		int							port,
		int							udp_port,
		int							http_port,
		String						real_ip_address,
		String						original_client_ip_address,
		long						downloaded,
		long						uploaded,
		long						left,
		int							num_want,
		byte						crypto_level,
		byte						az_ver,
		int							up_speed,
		DHTNetworkPosition			network_position )

		throws TRTrackerServerException
	{
		server			= _server;
		request_type	= _request_type;

		if ( !server.isReady()){

			throw( new TRTrackerServerException( "Tracker initialising, please wait" ));
		}

		start = SystemTime.getHighPrecisionCounter();

		boolean	ip_override = real_ip_address != original_client_ip_address;

		boolean	loopback	= TRTrackerUtils.isLoopback( real_ip_address );

		if ( loopback ){

				// any override is purely for routing purposes for loopback connections and we don't
				// want to apply the ip-override precedence rules against us

			ip_override	= false;
		}

			// translate any 127.0.0.1 local addresses back to the tracker address. Note this
			// fixes up .i2p and onion addresses back to their real values when needed

		String client_ip_address = TRTrackerUtils.adjustHostFromHosting( original_client_ip_address );

		if ( client_ip_address != original_client_ip_address ){

			if ( Logger.isEnabled()){

				Logger.log(
					new LogEvent(LogIDs.TRACKER,
						"    address adjusted: original=" +	original_client_ip_address +
						", real=" + real_ip_address +
						", adjusted=" + client_ip_address +
						", loopback=" + loopback ));
			}
		}

		if ( !TRTrackerServerImpl.getAllNetworksSupported()){

			String	network = AENetworkClassifier.categoriseAddress( client_ip_address );

			String[]	permitted_networks = TRTrackerServerImpl.getPermittedNetworks();

			boolean ok = false;

			for (int i=0;i<permitted_networks.length;i++){

				if ( network == permitted_networks[i] ){

					ok = true;

					break;
				}
			}

			if ( !ok ){

				throw( new TRTrackerServerException( "Network '" + network + "' not supported" ));
			}
		}

		TRTrackerServerTorrentImpl	torrent = null;

		if ( request_type != TRTrackerServerRequest.RT_FULL_SCRAPE ){

			// System.out.println( "TRTrackerServerProcessor::request:" + request_type + ",event:" + event + " - " + client_ip_address + ":" + port );

			// System.out.println( "    hash = " + ByteFormatter.nicePrint(hash));

			if ( request_type == TRTrackerServerRequest.RT_ANNOUNCE ){

				if ( hashes == null || hashes.length == 0 ){

					throw( new TRTrackerServerException( "Hash missing from request "));
				}

				if ( hashes.length != 1 ){

					throw( new TRTrackerServerException( "Too many hashes for announce"));
				}

				byte[]	hash = hashes[0];

				torrent = server.getTorrent( hash );

				if ( torrent == null ){

					if ( !COConfigurationManager.getBooleanParameter( "Tracker Public Enable")){

						throw( new TRTrackerServerException( "Torrent unauthorised" ));

					}else{

						try{

							torrent = (TRTrackerServerTorrentImpl)server.permit( real_ip_address, hash, false );

						}catch( Throwable e ){

							throw( new TRTrackerServerException( "Torrent unauthorised", e ));
						}
					}
				}

				if ( peer_id == null ){

					throw( new TRTrackerServerException( "peer_id missing from request"));
				}

				boolean	queue_it = stop_to_queue;

				if ( queue_it ){

					Set biased = server.getBiasedPeers();

					if ( biased != null && biased.contains( real_ip_address )){

							// biased peers get to queue whatever

					}else{

						if ( loopback || ip_override ){

							queue_it = false;
						}
					}
				}

				long	interval;
				long	min_interval;

				if ( queue_it ){

						// when queued we use the scrape timeouts as it is scrape operations that
						// will keep the entry alive from this point on

					interval 		= server.getScrapeRetryInterval( torrent );
					min_interval	= server.getMinScrapeRetryInterval();

				}else{

					interval 		= server.getAnnounceRetryInterval( torrent );
					min_interval 	= server.getMinAnnounceRetryInterval();

					if ( left == 0 ){

						long	mult = server.getSeedAnnounceIntervalMultiplier();

						interval 		*= mult;
						min_interval	*= mult;
					}
				}

				TRTrackerServerPeerImpl peer =
					torrent.peerContact(
						request,
						event,
						peer_id, port, udp_port, http_port, crypto_level, az_ver,
						real_ip_address, client_ip_address, ip_override, loopback, key,
						uploaded, downloaded, left,
						interval,
						up_speed, network_position );

				if ( queue_it ){

					torrent.peerQueued( client_ip_address, port, udp_port, http_port, crypto_level, az_ver, interval, left==0 );
				}

				HashMap	pre_map = new HashMap();

				TRTrackerServerPeer	pre_process_peer = peer;

				if ( pre_process_peer == null ){

						// can be null for stop events received without a previous start

					pre_process_peer = new lightweightPeer(client_ip_address,port,peer_id);
				}

				server.preProcess( pre_process_peer, torrent, request_type, request, pre_map );

					// set num_want to 0 for stopped events as no point in returning peers

				boolean	stopped 	= event != null && event.equalsIgnoreCase("stopped");

				root_out[0] = torrent.exportAnnounceToMap( client_ip_address, pre_map, peer, left > 0, stopped?0:num_want, interval, min_interval, no_peer_id, compact_mode, crypto_level, network_position );

				peer_out[0]	= peer;

			}else if ( request_type == TRTrackerServerRequest.RT_QUERY ){

				if ( link == null ){

					if ( hashes == null || hashes.length == 0 ){

						throw( new TRTrackerServerException( "Hash missing from request "));
					}

					if ( hashes.length != 1 ){

						throw( new TRTrackerServerException( "Too many hashes for query"));
					}

					byte[]	hash = hashes[0];

					torrent = server.getTorrent( hash );

				}else{

					torrent = server.getTorrent( link );
				}

				if ( torrent == null ){

					throw( new TRTrackerServerException( "Torrent unauthorised" ));
				}

				long	interval = server.getAnnounceRetryInterval( torrent );

				root_out[0] = torrent.exportAnnounceToMap( client_ip_address, new HashMap(), null, true, num_want, interval, server.getMinAnnounceRetryInterval(), true, compact_mode, crypto_level, network_position );

			}else{

				if ( hashes == null || hashes.length == 0 ){

					throw( new TRTrackerServerException( "Hash missing from request "));
				}

				boolean	local_scrape = client_ip_address.equals( "127.0.0.1" );

				long	max_interval	= server.getMinScrapeRetryInterval();

				Map	root = new HashMap();

				root_out[0] = root;

				Map	files = new ByteEncodedKeyHashMap();

				root.put( "files", files );

				char[]	scrape_chars = scrape_flags==null?null:scrape_flags.toCharArray();

				if ( scrape_chars != null && scrape_chars.length != hashes.length ){

					scrape_chars	= null;
				}

				for (int i=0;i<hashes.length;i++){

					byte[]	hash = hashes[i];

					String	str_hash;

					str_hash = new String(hash, Constants.BYTE_ENCODING_CHARSET);

					// skip duplicates
					if (i > 0 && files.get(str_hash) != null) {
						continue;
					}


					torrent = server.getTorrent( hash );

					if ( torrent == null ){

						if ( !COConfigurationManager.getBooleanParameter( "Tracker Public Enable")){

							continue;

						}else{

							try{
								torrent = (TRTrackerServerTorrentImpl)server.permit( real_ip_address, hash, false );

							}catch( Throwable e ){

								continue;
							}
						}
					}

					long	interval = server.getScrapeRetryInterval( torrent );

					if ( interval > max_interval ){

						max_interval	= interval;
					}

					if ( scrape_chars != null && ( QUEUE_TEST || !( loopback || ip_override ))){

							// note, 'Q' is complete+queued so we set seed true below

						if ( scrape_chars[i] == 'Q' ){

							torrent.peerQueued(  client_ip_address, port, udp_port, http_port, crypto_level, az_ver, (int)interval, true );
						}
					}

					if ( torrent.getRedirects() != null ){

						if ( hashes.length > 1 ){

								// just drop this from the set. this will cause the client to revert
								// to single-hash scrapes and subsequently pick up the redirect

							continue;
						}
					}

					server.preProcess( new lightweightPeer(client_ip_address,port,peer_id), torrent, request_type, request, null );

					// we don't cache local scrapes as if we do this causes the hosting of
					// torrents to retrieve old values initially. Not a fatal error but not
					// the best behaviour as the (local) seed isn't initially visible.

					Map	hash_entry = torrent.exportScrapeToMap( request, client_ip_address, !local_scrape );

						// System.out.println( "tracker - encoding: " + ByteFormatter.nicePrint(torrent_hash) + " -> " + ByteFormatter.nicePrint( str_hash.getBytes( Constants.BYTE_ENCODING )));

					files.put( str_hash, hash_entry );
				}

				if ( hashes.length > 1 ){

					torrent	= null;	// no specific torrent
				}

				// System.out.println( "scrape: hashes = " + hashes.length + ", files = " + files.size() + ", tim = " + max_interval );

				addScrapeInterval( max_interval, root );
			}
		}else{


			if ( !TRTrackerServerImpl.isFullScrapeEnabled()){

				throw( new TRTrackerServerException( "Full scrape disabled" ));
			}

			Map	files = new ByteEncodedKeyHashMap();

			TRTrackerServerTorrentImpl[] torrents = server.getTorrents();

			for (int i=0;i<torrents.length;i++){

				TRTrackerServerTorrentImpl	this_torrent = torrents[i];

				if ( this_torrent.getRedirects() != null ){

						// not visible to a full-scrape

					continue;
				}

				server.preProcess( new lightweightPeer(client_ip_address,port,peer_id), this_torrent, request_type, request, null );

				byte[]	torrent_hash = this_torrent.getHash().getHash();

				String str_hash = new String(torrent_hash, Constants.BYTE_ENCODING_CHARSET);

				// System.out.println( "tracker - encoding: " + ByteFormatter.nicePrint(torrent_hash) + " -> " + ByteFormatter.nicePrint( str_hash.getBytes( Constants.BYTE_ENCODING )));

				Map hash_entry = this_torrent.exportScrapeToMap(request, client_ip_address, true);

				files.put(str_hash, hash_entry);
			}

			Map	root = new HashMap();

			root_out[0] = root;

			addScrapeInterval( null, root );

			root.put( "files", files );
		}

		return( torrent );
	}

	protected void
	addScrapeInterval(
		TRTrackerServerTorrentImpl	torrent,
		Map							root )
	{
		long interval = server.getScrapeRetryInterval( torrent );

		addScrapeInterval( interval, root );
	}

	protected void
	addScrapeInterval(
		long		interval,
		Map			root )
	{
		if ( interval > 0 ){

			Map	flags = new HashMap();

			flags.put("min_request_interval", new Long(interval));

			root.put( "flags", flags );
		}
	}

	@Override
	public void
	taskCompleted()
	{
		if ( start > 0 ){

			long	time = SystemTime.getHighPrecisionCounter() - start;

			server.updateTime( request_type, time );
		}
	}

	protected static class
	lightweightPeer
		implements TRTrackerServerPeer
	{
		private final String	ip;
		private final int		port;
		private final byte[]	peer_id;

		public
		lightweightPeer(
			String		_ip,
			int			_port,
			HashWrapper	_peer_id )
		{
			ip		= _ip;
			port	= _port;
			peer_id	= _peer_id==null?null:_peer_id.getBytes();
		}

		@Override
		public long
		getUploaded()
		{
			return( -1 );
		}

		@Override
		public long
		getDownloaded()
		{
			return( -1 );
		}

		@Override
		public long
		getAmountLeft()
		{
			return( -1 );
		}

		@Override
		public String
		getIP()
		{
			return( ip );
		}

		@Override
		public String
		getIPRaw()
		{
			return( ip );
		}

		@Override
		public byte
		getNATStatus()
		{
			return( NAT_CHECK_UNKNOWN );
		}

		@Override
		public int
		getTCPPort()
		{
			return( port );
		}

		@Override
		public int
		getHTTPPort()
		{
			return( 0 );
		}

		public int
		getUDPPort()
		{
			return( 0 );
		}

		@Override
		public byte[]
		getPeerID()
		{
			return( peer_id );
		}

		@Override
		public boolean
		isBiased()
		{
			return( false );
		}

		@Override
		public void
		setBiased(
			boolean		biased )
		{
		}

		@Override
		public void
		setUserData(
			Object		key,
			Object		data )
		{
		}

		@Override
		public Object
		getUserData(
			Object		key )
		{
			return( null );
		}

		@Override
		public int
		getSecsToLive()
		{
			return( -1 );
		}

		@Override
		public Map
		export()
		{
			return( null );
		}
	}
}
