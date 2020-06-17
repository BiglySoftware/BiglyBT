/*
 * Created on 11-Dec-2005
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

package com.biglybt.core.peer;

import java.util.Map;

import com.biglybt.core.disk.DiskManagerReadRequest;
import com.biglybt.core.disk.DiskManagerReadRequestListener;
import com.biglybt.core.logging.LogRelation;
import com.biglybt.core.peermanager.PeerManagerRegistration;
import com.biglybt.core.tracker.client.TRTrackerScraperResponse;

public interface
PEPeerManagerAdapter
{
	public String
	getDisplayName();

	public byte[]
	getTargetHash();
	
	public int 
	getTCPListeningPortNumber();
	
	public int
	getEffectiveUploadRateLimitBytesPerSecond();

	public int
	getUploadRateLimitBytesPerSecond();

	public void
	setUploadRateLimitBytesPerSecond( int b );
	
	public int
	getDownloadRateLimitBytesPerSecond();
	
	public void
	setDownloadRateLimitBytesPerSecond( int b );

	public int
	getMaxUploads();

	public int[]
	getMaxConnections();

	public int[]
	getMaxSeedConnections();

	public int
	getExtendedMessagingMode();

	public boolean
	isPeerExchangeEnabled();

	public boolean
	isMetadataDownload();

	public int
	getUploadPriority();

	public int
	getTorrentInfoDictSize();

	public byte[]
	getTorrentInfoDict(
		PEPeer		peer );
		/**
		 * See NetworkManager.CRYPTO_OVERRIDE constants
		 * @return
		 */

	public int
	getCryptoLevel();

	public long
	getRandomSeed();

	public boolean
	isPeriodicRescanEnabled();

	public void
	setStateFinishing();

	public void
	setStateSeeding(
		boolean	never_downloaded );

	public void
	restartDownload(boolean forceRecheck);

	public TRTrackerScraperResponse
	getTrackerScrapeResponse();

	public String
	getTrackerClientExtensions();

	public void
	setTrackerRefreshDelayOverrides(
		int	percent );

	public boolean
	isNATHealthy();

	public void
	addPeer(
		PEPeer	peer );

	public void
	removePeer(
		PEPeer	peer );

	public void
	addPiece(
		PEPiece	piece );

	public void
	removePiece(
		PEPiece	piece );

	public void
	discarded(
		PEPeer		peer,
		int			bytes );

	public void
	protocolBytesReceived(
		PEPeer		peer,
		int			bytes );

	public void
	dataBytesReceived(
		PEPeer		peer,
		int			bytes );

	public void
	protocolBytesSent(
		PEPeer		peer,
		int			bytes );

	public void
	dataBytesSent(
		PEPeer		peer,
		int			bytes );

	public void
	statsRequest(
		PEPeer			 	originator,
		Map 				request,
		Map					reply );

	public PeerManagerRegistration
	getPeerManagerRegistration();

	public void
	addHTTPSeed(
		String	address,
		int		port );

	public byte[][]
	getSecrets(
		int	crypto_level );

	public void
	enqueueReadRequest(
		PEPeer							peer,
		DiskManagerReadRequest 			request,
		DiskManagerReadRequestListener 	listener );

	public LogRelation
	getLogRelation();

	public int getPosition();

	public boolean
	isPeerSourceEnabled(
		String peer_source );

	public boolean
	isNetworkEnabled(
		String	network );

	public String[]
	getEnabledNetworks();

	public void
	priorityConnectionChanged(
		boolean	added );

	public boolean
	hasPriorityConnection();

	public int getPermittedBytesToReceive();
	public void permittedReceiveBytesUsed( int bytes );

	public int getPermittedBytesToSend();
	public void	permittedSendBytesUsed(	int bytes );
	
	public void
	saveTorrentState();
}
