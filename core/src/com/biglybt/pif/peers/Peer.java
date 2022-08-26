/*
 * File    : Peer.java
 * Created : 01-Dec-2003
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

package com.biglybt.pif.peers;

/**
 * @author parg
 *
 */

import java.util.List;

import com.biglybt.core.peer.PEPeer;
import com.biglybt.pif.messaging.Message;
import com.biglybt.pif.network.Connection;
import com.biglybt.pif.network.ConnectionStub;
import com.biglybt.pif.network.RateLimiter;


public interface
Peer
{
	public final static int CONNECTING 		= PEPeer.CONNECTING;
	public final static int HANDSHAKING 	= PEPeer.HANDSHAKING;
	public final static int TRANSFERING 	= PEPeer.TRANSFERING;
	public final static int CLOSING 		= PEPeer.CLOSING;
	public final static int DISCONNECTED 	= PEPeer.DISCONNECTED;


	public final static Object PR_PRIORITY_CONNECTION 	= new Object();
	public final static Object PR_PROTOCOL				= new Object();
	public final static Object PR_PROTOCOL_QUALIFIER	= new Object();
	public final static Object PR_FORCE_CONNECTION		= new Object();
	public final static Object PR_PREFER_UTP			= new Object();
	public final static Object PR_PEER_SOURCE			= new Object();
	
	public void
	bindConnection(
		ConnectionStub		stub );

	public PeerManager
	getManager();

	public boolean
	isMyPeer();
	
	public int getState();	// from above set

	public byte[] getId();


	/**
	 * Get the peer's local TCP connection port.
	 * @return local port
	 */

	public String getIp();

	/**
	 * Get the TCP port this peer is listening for incoming connections on.
	 * @return TCP port, or 0 if port is unknown
	 */
	public int getTCPListenPort();

	/**
	 * Get the UDP port this peer is listening for incoming connections on.
	 * @return UDP port, or 0 if port is unknown
	 */
	public int getUDPListenPort();

	/**
	 * Get the UDP port this peer is listening on for non-data connections
	 * @return
	 */

	public int
	getUDPNonDataListenPort();

	public int getPort();

	public boolean
	isLANLocal();

	public void
	resetLANLocalStatus();
	
	public boolean[] getAvailable();
	/**
	 * @param pieceNumber int
	 * @return true if this peers makes this piece available
	 */
	public boolean isPieceAvailable(int pieceNumber);

	public boolean
	isTransferAvailable();

		/**
		 * Rate control - gives the maximum number of bytes that can be read from this
		 * connection at this time and returns the actual number read
		 * @param max
		 * @return
		 */

	public int
	readBytes(
		int	max );

	public int
	writeBytes(
		int	max );

	/**
	 * This is much list isTransferAvailable(), except is more comprehensive.
	 * That is; it checks a few more factors, within the object for speed,
	 * so that a more timely status is considered and the caller doesn't need
	 * to try to check each thing on it's own.
	 * @return true if several factors say downloading can be tried.
	 */
	public boolean isDownloadPossible();

	public boolean isChoked();

	public boolean isChoking();

	public boolean isInterested();

	public boolean isInteresting();

	public boolean isSeed();

	public boolean isSnubbed();

	public long getSnubbedTime();

	public void setSnubbed( boolean b);

	public PeerStats getStats();

	public boolean isIncoming();

	public int getPercentDoneInThousandNotation();

	public String getClient();

	public boolean isOptimisticUnchoke();

	public void setOptimisticUnchoke( boolean is_optimistic );

	public List<PeerReadRequest>
	getExpiredRequests();

	public List<PeerReadRequest>
	getRequests();

	public int
	getMaximumNumberOfRequests();

	public int
	getNumberOfRequests();

	public void
	cancelRequest(
		PeerReadRequest	request );

	public boolean
	requestAllocationStarts(
		int[]	base_priorities );

	public int[]
	getPriorityOffsets();

	public void
	requestAllocationComplete();

	public boolean
	addRequest(
		PeerReadRequest	request );


	public void
	close(
		String 		reason,
		boolean 	closedOnError,
		boolean 	attemptReconnect );

	public int[]
	getCurrentIncomingRequestProgress();

	public int[]
	getOutgoingRequestedPieceNumbers();

	public int
	getOutgoingRequestCount();

	public int[]
	getCurrentOutgoingRequestProgress();


	/**
	   * Add peer listener.
	   * @param listener
	   */
	public void	addListener( PeerListener2	listener );


  /**
   * Remove peer listener.
   * @param listener
   */
	public void removeListener(	PeerListener2 listener );


  /**
   * Get the network connection that backs this peer.
   * @return connection
   */
  public Connection getConnection();


  /**
   * Whether or not this peer supports the advanced messaging API.
   * @return true if extended messaging is supported, false if not
   */
  public boolean supportsMessaging();


  /**
   * Get the list of messages that this peer and us mutually understand.
   * @return messages available for use, or null of supported is yet unknown
   */
  public Message[] getSupportedMessages();

  public void
  setUserData(
	Object	key,
	Object	value );

  public Object
  getUserData(
	Object	key );

  public byte[] getHandshakeReservedBytes();

  public boolean
  isPriorityConnection();

  public void
  setPriorityConnection(
		boolean		is_priority );

	  /**
	   * @since 4.7.0.3
	   * @param limiter		create via ConnectionManager
	   * @param is_upload		false -> download limit
	   */

  public void
  addRateLimiter(
		  RateLimiter		limiter,
		  boolean			is_upload );

  public void
  removeRateLimiter(
		  RateLimiter		limiter,
		  boolean			is_upload );

  public RateLimiter[]
  getRateLimiters(
   		 boolean	is_upload );
}
