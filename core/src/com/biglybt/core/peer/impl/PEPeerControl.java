/*
 * File    : PEPeerControl.java
 * Created : 21-Oct-2003
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

package com.biglybt.core.peer.impl;

/**
 * @author parg
 *
 */


import java.util.Map;

import com.biglybt.core.disk.DiskManagerReadRequest;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.PEPiece;
import com.biglybt.core.peermanager.peerdb.PeerItem;


public interface
PEPeerControl
	extends PEPeerManager
{
	public static final int		SNUB_MILLIS 						= 60 *1000;

	public boolean
	validateReadRequest(
		PEPeerTransport	originator,
		int 			pieceNumber,
		int 			offset,
		int 			length );

	public boolean
	validateHintRequest(
		PEPeerTransport	originator,
		int 			pieceNumber,
		int 			offset,
		int 			length );

	public void
	requestAdded(
		PEPiece					piece,
		PEPeerTransport			peer,
		DiskManagerReadRequest	request);
	
	public void
	havePiece(
		int pieceNumber,
		int pieceLength,
		PEPeer pcOrigin );

	public void
	updateSuperSeedPiece(
	    PEPeer peer,
	    int pieceNumber);

	public int
	getTCPListeningPortNumber();
	
		/**
		 * Get torrent hash including any overrides
		 * @return
		 */
	
	public byte[]
	getTargetHash();
	
	public boolean
	isPrivateTorrent();

	public int
	getExtendedMessagingMode();

	public boolean
	isPeerExchangeEnabled();

	public byte[][]
	getSecrets(
		int	crypto_level );

	public int
	getUploadPriority();

	public void addPeerTransport( PEPeerTransport transport );

	public int
	getConnectTimeout(
		int		ct_def );

	public int[]
	getMaxConnections();

    public boolean
    doOptimisticDisconnect(
    	boolean pending_lan_local_peer,
    	boolean	force,
    	String	network );

	public int getNbActivePieces();

	public int getNbPeersStalledPendingLoad();

	// Snubbed peers accounting
	public void incNbPeersSnubbed();
	public void decNbPeersSnubbed();
	public void setNbPeersSnubbed(int n);
	public int getNbPeersSnubbed();
	
	public void 
	checkSnubbing(
		PEPeerTransport	peer );

	public void
	badPieceReported(
		PEPeerTransport		originator,
		int					piece_number );

	public boolean
	isFastExtensionPermitted(
		PEPeerTransport		originator );

	public void
	reportBadFastExtensionUse(
		PEPeerTransport		originator );

	public void
	statsRequest(
		PEPeerTransport		originator,
		Map					request );

	public void
	statsReply(
		PEPeerTransport		originator,
		Map					reply );
	
	public boolean isRTA();

	public void
	peerDiscovered(
		PEPeerTransport		finder,
		PeerItem			pi );
	
	public PEPeerControlHashHandler
	getHashHandler();
	
	public boolean
	isHolePunchOperationOK(
		PEPeerTransport		peer,
		boolean				is_connect );
}