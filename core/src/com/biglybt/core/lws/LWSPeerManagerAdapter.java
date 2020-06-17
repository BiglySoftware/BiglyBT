/*
 * Created on 14-Dec-2005
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

package com.biglybt.core.lws;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.biglybt.core.disk.DiskManagerReadRequest;
import com.biglybt.core.disk.DiskManagerReadRequestListener;
import com.biglybt.core.logging.LogRelation;
import com.biglybt.core.networkmanager.NetworkManager;
import com.biglybt.core.networkmanager.impl.tcp.TCPNetworkManager;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManagerAdapter;
import com.biglybt.core.peer.PEPiece;
import com.biglybt.core.peermanager.PeerManagerRegistration;
import com.biglybt.core.peermanager.messaging.bittorrent.BTHandshake;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.tracker.client.TRTrackerScraperResponse;
import com.biglybt.core.util.AENetworkClassifier;
import com.biglybt.core.util.BEncoder;
import com.biglybt.core.util.Debug;
import com.biglybt.pifimpl.local.PluginCoreUtils;


public class
LWSPeerManagerAdapter
	extends 	LogRelation
	implements 	PEPeerManagerAdapter
{
	private final LightWeightSeed			lws;

	private final PeerManagerRegistration	peer_manager_registration;

	private final String[]	enabled_networks;

	private int	md_info_dict_size;

	private WeakReference<byte[]>	md_info_dict_ref = new WeakReference<>(null);

	public
	LWSPeerManagerAdapter(
		LightWeightSeed				_lws,
		PeerManagerRegistration		_peer_manager_registration )
	{
		lws		= _lws;

		String main_net = lws.getNetwork();

		if ( main_net.equals( AENetworkClassifier.AT_PUBLIC )){

			enabled_networks = AENetworkClassifier.AT_NETWORKS;

		}else{

			enabled_networks = AENetworkClassifier.AT_NON_PUBLIC;
		}

		peer_manager_registration = _peer_manager_registration;
	}

	@Override
	public String
	getDisplayName()
	{
		return( lws.getName());
	}

	@Override
	public byte[] getTargetHash()
	{
		return( lws.getHash().getBytes());
	}
	
	@Override
	public int getTCPListeningPortNumber(){
		return( TCPNetworkManager.getSingleton().getDefaultTCPListeningPortNumber());
	}
	
	@Override
	public PeerManagerRegistration
	getPeerManagerRegistration()
	{
		return( peer_manager_registration );
	}

	@Override
	public int
	getEffectiveUploadRateLimitBytesPerSecond()
	{
		return( 0 );
	}

	@Override
	public int
	getUploadRateLimitBytesPerSecond()
	{
		return( 0 );
	}
	
	@Override
	public void setUploadRateLimitBytesPerSecond(int b)
	{
	}

	@Override
	public int
	getDownloadRateLimitBytesPerSecond()
	{
		return( 0 );
	}

	@Override
	public void setDownloadRateLimitBytesPerSecond(int b)
	{
	}
	
	@Override
	public int
	getPermittedBytesToReceive()
	{
		return( Integer.MAX_VALUE );
	}

	@Override
	public void
	permittedReceiveBytesUsed(
		int bytes )
	{
	}

	@Override
	public int
	getPermittedBytesToSend()
	{
		return( Integer.MAX_VALUE );
	}

	@Override
	public void
	permittedSendBytesUsed(
		int bytes )
	{
	}

	@Override
	public int
	getUploadPriority()
	{
		return( 0 );
	}

	@Override
	public int
	getMaxUploads()
	{
		return( 4 );
	}

	@Override
	public int[]
	getMaxConnections()
	{
		return( new int[]{ 0, 0 } );
	}

	@Override
	public int[]
	getMaxSeedConnections()
	{
		return( new int[]{ 0, 0 } );
	}

	@Override
	public int
	getExtendedMessagingMode()
	{
		return( BTHandshake.AZ_RESERVED_MODE );
	}

	@Override
	public boolean
	isPeerExchangeEnabled()
	{
		return( true );
	}

	@Override
	public boolean
	isNetworkEnabled(
		String	network )
	{
		for ( String net: enabled_networks ){

			if ( net == network ){

				return( true );
			}
		}

		return( false );
	}

	@Override
	public String[]
	getEnabledNetworks()
	{
		return( enabled_networks );
	}

	@Override
	public int
	getCryptoLevel()
	{
		return( NetworkManager.CRYPTO_OVERRIDE_NONE );
	}

	@Override
	public long
	getRandomSeed()
	{
		return 0;
	}

	@Override
	public boolean
	isPeriodicRescanEnabled()
	{
		return( false );
	}

	@Override
	public void
	setStateFinishing()
	{
	}

	@Override
	public void
	setStateSeeding(
		boolean	never_downloaded )
	{
	}

	@Override
	public void
	restartDownload(
		boolean	recheck )
	{
		Debug.out( "restartDownload called for " + getDisplayName());
	}

	@Override
	public TRTrackerScraperResponse
	getTrackerScrapeResponse()
	{
		return( null );
	}

	@Override
	public String
	getTrackerClientExtensions()
	{
		return( null );
	}

	@Override
	public void
	setTrackerRefreshDelayOverrides(
		int	percent )
	{

	}

	@Override
	public boolean
	isMetadataDownload()
	{
		return( false );
	}

	@Override
	public int
	getTorrentInfoDictSize()
	{
		synchronized( this ){

			if ( md_info_dict_size == 0 ){

				byte[] data = getTorrentInfoDict( null );

				if ( data == null ){

					md_info_dict_size = -1;

				}else{

					md_info_dict_size = data.length;
				}
			}

			return( md_info_dict_size );
		}
	}

	@Override
	public byte[]
	getTorrentInfoDict(
		PEPeer	peer )
	{
		try{
			byte[] data = md_info_dict_ref.get();

			if ( data == null ){

				TOTorrent torrent = PluginCoreUtils.unwrap( lws.getTorrent());

				data = BEncoder.encode((Map)torrent.serialiseToMap().get( "info" ));

				md_info_dict_ref = new WeakReference<>(data);
			}

			return( data );

		}catch( Throwable e ){

			return( null );
		}
	}

	@Override
	public boolean
	isNATHealthy()
	{
		return( true );
	}

	@Override
	public void
	addPeer(
		PEPeer	peer )
	{
	}

	@Override
	public void
	removePeer(
		PEPeer	peer )
	{
	}

	@Override
	public void
	addPiece(
		PEPiece	piece )
	{
	}

	@Override
	public void
	removePiece(
		PEPiece	piece )
	{
	}

	@Override
	public void
	discarded(
		PEPeer		peer,
		int			bytes )
	{
	}

	@Override
	public void
	protocolBytesReceived(
		PEPeer		peer,
		int			bytes )
	{
	}

	@Override
	public void
	dataBytesReceived(
		PEPeer		peer,
		int			bytes )
	{
	}

	@Override
	public void
	protocolBytesSent(
		PEPeer		peer,
		int			bytes )
	{
	}

	@Override
	public void
	dataBytesSent(
		PEPeer		peer,
		int			bytes )
	{
	}

	@Override
	public void
	statsRequest(
		PEPeer 	originator,
		Map 	request,
		Map		reply )
	{
	}

	@Override
	public void
	addHTTPSeed(
		String	address,
		int		port )
	{
	}

	@Override
	public byte[][]
	getSecrets(
		int crypto_level )
	{
		return( lws.getSecrets());
	}

	@Override
	public void
	enqueueReadRequest(
		PEPeer							peer,
		DiskManagerReadRequest 			request,
		DiskManagerReadRequestListener 	listener )
	{
		lws.enqueueReadRequest( peer, request, listener );
	}

	@Override
	public int getPosition()
	{
		return( Integer.MAX_VALUE );
	}

	@Override
	public boolean
	isPeerSourceEnabled(
		String peer_source )
	{
		return( true );
	}


	@Override
	public boolean
	hasPriorityConnection()
	{
		return( false );
	}

	@Override
	public void
	priorityConnectionChanged(
		boolean added )
	{
	}

	@Override
	public void 
	saveTorrentState()
	{
	}
	
	@Override
	public LogRelation
	getLogRelation()
	{
		return( this );
	}

	@Override
	public String
	getRelationText()
	{
		return( lws.getRelationText());
	}

	@Override
	public Object[]
	getQueryableInterfaces()
	{
		List	interfaces = new ArrayList();

		Object[]	intf = lws.getQueryableInterfaces();

		for (int i=0;i<intf.length;i++){

			if( intf[i] != null ){

				interfaces.add( intf[i] );
			}
		}

		interfaces.add( lws.getRelation());

		return( interfaces.toArray());
	}
}
