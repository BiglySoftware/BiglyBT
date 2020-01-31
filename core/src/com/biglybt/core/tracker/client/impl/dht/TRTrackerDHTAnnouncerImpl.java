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

package com.biglybt.core.tracker.client.impl.dht;

import java.net.URL;
import java.util.*;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentAnnounceURLSet;
import com.biglybt.core.torrent.TOTorrentException;
import com.biglybt.core.tracker.TrackerPeerSource;
import com.biglybt.core.tracker.client.*;
import com.biglybt.core.tracker.client.impl.TRTrackerAnnouncerHelper;
import com.biglybt.core.tracker.client.impl.TRTrackerAnnouncerImpl;
import com.biglybt.core.tracker.client.impl.TRTrackerAnnouncerRequestImpl;
import com.biglybt.core.tracker.client.impl.TRTrackerAnnouncerResponseImpl;
import com.biglybt.core.tracker.client.impl.TRTrackerAnnouncerResponsePeerImpl;
import com.biglybt.core.util.*;
import com.biglybt.pif.clientid.ClientIDException;
import com.biglybt.pif.download.DownloadAnnounceResult;
import com.biglybt.pif.download.DownloadAnnounceResultPeer;
import com.biglybt.pifimpl.local.clientid.ClientIDManagerImpl;

/**
 * @author parg
 *
 */

public class
TRTrackerDHTAnnouncerImpl
	implements TRTrackerAnnouncerHelper
{
	public final static LogIDs LOGID = LogIDs.TRACKER;

	private final TOTorrent		torrent;
	private HashWrapper		torrent_hash;

	private final TRTrackerAnnouncerImpl.Helper		helper;

	private byte[]			data_peer_id;

	private String						tracker_status_str;
	private long						last_update_time;

	private int							state = TS_INITIALISED;

	private TRTrackerAnnouncerResponseImpl	last_response;

	private final boolean			manual;

	public
	TRTrackerDHTAnnouncerImpl(
		TOTorrent						_torrent,
		String[]						_networks,
		boolean							_manual,
		TRTrackerAnnouncerImpl.Helper	_helper )

		throws TRTrackerAnnouncerException
	{
		torrent		= _torrent;
		manual		= _manual;
		helper		= _helper;

		try{
			torrent_hash	= torrent.getHashWrapper();

		}catch( TOTorrentException e ){

			Debug.printStackTrace(e);
		}
		try{
			data_peer_id = ClientIDManagerImpl.getSingleton().generatePeerID( torrent_hash.getBytes(), false );

		}catch( ClientIDException e ){

			 throw( new TRTrackerAnnouncerException( "TRTrackerAnnouncer: Peer ID generation fails", e ));
		}

		last_response =
			new TRTrackerAnnouncerResponseImpl(
				torrent.getAnnounceURL(),
				torrent_hash,
				TRTrackerAnnouncerResponse.ST_OFFLINE, 0, "Initialising" );

		tracker_status_str = MessageText.getString("PeerManager.status.checking") + "...";
	}

	@Override
	public void
	setAnnounceDataProvider(
		TRTrackerAnnouncerDataProvider		provider )
	{
	}

	@Override
	public boolean
	isManual()
	{
		return( manual );
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
		return( TorrentUtils.getDecentralisedURL( torrent ));
	}

	@Override
	public void
	setTrackerURL(
		URL		url )
	{
		Debug.out( "Not implemented" );
	}

	@Override
	public TOTorrentAnnounceURLSet[]
	getAnnounceSets()
	{
		return( new TOTorrentAnnounceURLSet[]{
					torrent.getAnnounceURLGroup().createAnnounceURLSet(
							new URL[]{ TorrentUtils.getDecentralisedURL( torrent )})} );
	}

	@Override
	public void
	resetTrackerUrl(
		boolean	shuffle )
	{
	}

	@Override
	public void
	setIPOverride(
		String		override )
	{
	}

	@Override
	public void
	clearIPOverride()
	{
	}

	public int
	getPort()
	{
		return(0);
	}

	@Override
	public byte[]
	getPeerId()
	{
		return( data_peer_id );
	}

	@Override
	public void
	setRefreshDelayOverrides(
		int		percentage )
	{
	}

	@Override
	public int
	getTimeUntilNextUpdate()
	{
		long elapsed = (SystemTime.getCurrentTime() - last_update_time)/1000;

		return( (int)(last_response.getTimeToWait()-elapsed));
	}

	@Override
	public int
	getLastUpdateTime()
	{
		return( (int)(last_update_time/1000));
	}

	@Override
	public void
	update(
		boolean	force )
	{
		state = TS_DOWNLOADING;
	}

	@Override
	public void
	complete(
		boolean	already_reported )
	{
		state	= TS_COMPLETED;
	}

	@Override
	public void
	stop(
		boolean	for_queue )
	{
		state	= TS_STOPPED;
	}

	@Override
	public void
	destroy()
	{
	}

	@Override
	public int
	getStatus()
	{
		return( state );
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
	public TRTrackerAnnouncerResponse
	getLastResponse()
	{
		return( last_response );
	}

	@Override
	public boolean
	isUpdating()
	{
		return( false );
	}

	@Override
	public long
	getInterval()
	{
		return( -1 );
	}

	@Override
	public long
	getMinInterval()
	{
		return( -1 );
	}

	@Override
	public void
	refreshListeners()
	{
	}

	@Override
	public void
	setAnnounceResult(
		DownloadAnnounceResult	result )
	{
		last_update_time	= SystemTime.getCurrentTime();

		TRTrackerAnnouncerResponseImpl response;

		if ( result.getResponseType() == DownloadAnnounceResult.RT_ERROR ){

			tracker_status_str = MessageText.getString("PeerManager.status.error");

			String	reason = result.getError();

			if ( reason != null ){

				tracker_status_str += " (" + reason + ")";
			}

	  		response = new TRTrackerAnnouncerResponseImpl(
				  				result.getURL(),
				  				torrent_hash,
				  				TRTrackerAnnouncerResponse.ST_OFFLINE,
								result.getTimeToWait(),
								reason );
		}else{
			DownloadAnnounceResultPeer[]	ext_peers = result.getPeers();

			List<TRTrackerAnnouncerResponsePeerImpl> peers_list = new ArrayList<>(ext_peers.length);

			for (int i=0;i<ext_peers.length;i++){

				DownloadAnnounceResultPeer	ext_peer	= ext_peers[i];

				if ( ext_peer == null){

					continue;
				}

				if (Logger.isEnabled()){
					Logger.log(new LogEvent(torrent, LOGID, "EXTERNAL PEER DHT: ip="
							+ ext_peer.getAddress() + ",port=" + ext_peer.getPort() +",prot=" + ext_peer.getProtocol()));
				}

				int		http_port	= 0;
				byte	az_version 	= TRTrackerAnnouncer.AZ_TRACKER_VERSION_1;

				peers_list.add( new TRTrackerAnnouncerResponsePeerImpl(
									ext_peer.getSource(),
									ext_peer.getPeerID(),
									ext_peer.getAddress(),
									ext_peer.getPort(),
									ext_peer.getUDPPort(),
									http_port,
									ext_peer.getProtocol(),
									az_version,
									(short)0 ));
			}

			TRTrackerAnnouncerResponsePeerImpl[]	peers = peers_list.toArray( new TRTrackerAnnouncerResponsePeerImpl[peers_list.size()] );

			helper.addToTrackerCache( peers);

			tracker_status_str = MessageText.getString("PeerManager.status.ok");

			response = new TRTrackerAnnouncerResponseImpl( result.getURL(), torrent_hash, TRTrackerAnnouncerResponse.ST_ONLINE, result.getTimeToWait(), peers );
		}

		last_response = response;

		TRTrackerAnnouncerResponsePeer[] peers = response.getPeers();

		if ( peers == null || peers.length < 5 ){

		     TRTrackerAnnouncerResponsePeer[]	cached_peers = helper.getPeersFromCache(100);

		     if ( cached_peers.length > 0 ){

		    	 Set<TRTrackerAnnouncerResponsePeer>	new_peers =
					     new TreeSet<>(
							     new Comparator<TRTrackerAnnouncerResponsePeer>() {
								     @Override
								     public int
								     compare(
										     TRTrackerAnnouncerResponsePeer o1,
										     TRTrackerAnnouncerResponsePeer o2) {
									     return (o1.compareTo(o2));
								     }
							     });

		    	 if ( peers != null ){

		    		 new_peers.addAll( Arrays.asList( peers ));
		    	 }

	    		 new_peers.addAll( Arrays.asList( cached_peers ));

		    	 response.setPeers( new_peers.toArray( new TRTrackerAnnouncerResponsePeer[new_peers.size()]) );
		     }
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
		writer.println( "DHT announce: " + (last_response==null?"null":last_response.getString()));
	}
}
