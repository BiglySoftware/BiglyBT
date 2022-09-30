/*
 * File    : PEPeerTransport
 * Created : 15-Oct-2003
 * By      : Olivier
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

  /*
 * Created on 4 juil. 2003
 *
 */
package com.biglybt.core.peer.impl;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;

import com.biglybt.core.disk.DiskManagerReadRequest;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peermanager.peerdb.PeerItem;
import com.biglybt.core.torrent.TOTorrentFileHashTree;
import com.biglybt.core.util.IndentWriter;

public interface
PEPeerTransport
	extends PEPeer
{

  public static final int CONNECTION_PENDING                = 0;
  public static final int CONNECTION_CONNECTING             = 1;
  public static final int CONNECTION_WAITING_FOR_HANDSHAKE  = 2;
  public static final int CONNECTION_FULLY_ESTABLISHED      = 4;

  public static final int CP_UNKNOWN				= 0;
  public static final int CP_CONNECTING				= 1;
  public static final int CP_CONNECT_OK				= 2;
  public static final int CP_CONNECT_FAILED			= 3;
  public static final int CP_RECEIVED_DATA			= 4;

	public int
	getOutboundConnectionProgress();

  		/**
  		 * Start message processing for the peer
  		 */

  	public void
  	start();

	@Override
	public void
	sendChoke();

	@Override
	public void
	sendUnChoke();

	public void
	sendHave(
		int		piece );

	public void
	sendCancel(
		DiskManagerReadRequest	request );

	public void
	sendBadPiece(
		int		piece_number );

	@Override
	public void
	sendStatsRequest(
		Map		request );

	public void
	sendStatsReply(
		Map		reply );

		/**
		 * Two methods that allow a peer to aggregate the individual requests generated during an
		 * allocation cycle if so desired
		 * @return true if the peer is managing request priorities and doesn't want end-game random
		 * allocation behaviour
		 */

	public boolean
	requestAllocationStarts(
		int[]	base_priorities );

	public void
	requestAllocationComplete();

  /**
   *
   * @param pieceNumber
   * @param pieceOffset
   * @param pieceLength
   * @param return_duplicates - if true and request already exists it will be returned, if false -> null
   * @return request if actually requested, null otherwise
   */

	public DiskManagerReadRequest
	request(
		int 		pieceNumber,
		int 		pieceOffset,
		int		 	pieceLength,
		boolean		return_duplicates );

	/**
	 * Returns the index of this request in the peer's queue or -1 if not found
	 * @return
	 */

	public int
	getRequestIndex(
		DiskManagerReadRequest request );

  /**
   * Close the peer connection
   * @param reason for closure
   */
	public void closeConnection( String reason, int reason_code );
	
	@Override
	public boolean
	transferAvailable();

	public long
	getLastMessageSentTimeMono();

	public List
	getExpiredRequests();

		/**
		 * peer-specific request max. return -1 to use the default piece-picker allocation method
		 * @return
		 */

	public int
	getMaxNbRequests();

	public int
	getNbRequests();

	public PEPeerControl
	getControl();

		/**
		 * Any priority offsets this peer has, or null if none
		 * @return
		 */

	public int[]
	getPriorityOffsets();

	/**
	 * Check if we need to send a keep-alive message.
	 * A keep-alive is sent if no other message has been sent within the last 2min.
	 */
	public void doKeepAliveCheck();

  /**
   * Check for possible connection timeouts.
   * @return true if the connection has been timed-out, false if not
   */
  public boolean doTimeoutChecks();


  /**
   * Perform checks related to performance optimizations,
   * i.e. tune buffering related to send/receive speed.
   */
  public void doPerformanceTuningCheck();


  /**
   * Get the specific peer connection state.
   * @return connection state
   */
  public int getConnectionState();


  /**
   * Get the time since the last (most-recent) data (payload) message was received.
   * @return time count in ms, or -1 if we've never received a data message from them
   */
  public long getTimeSinceLastDataMessageReceived();

  /**
   * Get the time since the most-recent data that was actually written to disk was received.
   * @return time count in ms, or -1 if we've never received usefull data from them
   */
  public long getTimeSinceGoodDataReceived();

  /**
   * Get the time since the last (most-recent) data (payload) message was sent.
   * @return time count in ms, or -1 if we've never sent them a data message
   */
  public long getTimeSinceLastDataMessageSent();


  public long getUnchokedForMillis();

  public long getLatency();

  /**
   * Do any peer exchange processing/updating.
   */
  public void updatePeerExchange();


  /**
   * Get the peer's address + port identification item.
   * @return id
   */
  public PeerItem getPeerItemIdentity();

  /**
   * is peer waiting for a disk read with no network writes queued
   * @return
   */

  public boolean isStalledPendingLoad();

  /**
   * Is the connection within the local LAN network.
   * @return true if within LAN, false of outside the LAN segment
   */
  @Override
  public boolean isLANLocal();

  public boolean
  isTCP();

	/**
	 * if it doesn't go as expected when trying to find a piece to ask a peer for,
	 * need to double check if we're still interested in them, and get the BT protocol sycnhed
	 */
	public void checkInterested();

		/**
		 * Attempts to reconnect to the same peer
		 * @param tryUDP try to initate a UDP connection if true, just reestablish the previous state otherwise
		 * @param tryIPv6 TODO
		 * @return null if reconnect not possible, reconnected peer otherwise
		 */

	public PEPeerTransport
	reconnect(
		boolean tryUDP, 
		boolean tryIPv6,
		Map		user_data );

	public boolean
	isReconnect();
	
	public int
	getIncomingRequestedPieceNumberCount();
	
	/**
	 * This method is called to check if it is safe to reconnect to a peer, i.e. avoid hammering
	 * exchanging data with a peer should work as it takes time to setup the connection and negotiate things before that happens
	 * @return true if we exchanged payload data with the peer during the current connection
	 */
	public boolean isSafeForReconnect();

	public String
	getNetwork();

	public void
	sendHashRequest(
		TOTorrentFileHashTree.HashRequest		req );
	
	public boolean
	canSendHolePunch();
	
	public void
	sendHolePunch(
		InetAddress		address,
		int				port );

	public void
	generateEvidence(
		IndentWriter	writer );
}