/*
 * File    : PEPeerManager
 * Created : 5 Oct. 2003
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

package com.biglybt.core.peer;

/**
 * @author parg
 * @author MjrTom
 *			2005/Oct/08: pieceAdded => addPiece to simplify new piece-picking, getAvgAvail
 *
 */

import java.util.List;
import java.util.Map;

import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.disk.DiskManagerReadRequest;
import com.biglybt.core.networkmanager.LimitedRateGroup;
import com.biglybt.core.peer.impl.PEPeerTransport;
import com.biglybt.core.peer.util.PeerIdentityDataID;
import com.biglybt.core.peermanager.peerdb.PeerExchangerItem;
import com.biglybt.core.peermanager.piecepicker.PiecePicker;
import com.biglybt.core.tracker.TrackerPeerSource;
import com.biglybt.core.tracker.client.TRTrackerAnnouncerResponse;
import com.biglybt.core.util.DirectByteBuffer;
import com.biglybt.core.util.IndentWriter;
import com.biglybt.pif.peers.PeerDescriptor;


public interface
PEPeerManager
{
	public int
	getUID();
	
	public DiskManager getDiskManager();
	public PiecePicker getPiecePicker();

	public PEPeerManagerAdapter	getAdapter();

	public void
	start();

	public void
	stopAll();

	public byte[]
	getHash();

	public String
	getDisplayName();

	public PeerIdentityDataID
	getPeerIdentityDataID();

	public byte[]
	getPeerId();

	public int[] getAvailability();

	public int getAvailability(int pieceNumber);

	public float getAvgAvail();

	public float getMinAvailability();

	public float getMinAvailability( int file_index );

	public long getAvailWentBadTime();

	public long getBytesUnavailable();

	public boolean hasDownloadablePiece();

    public int	getBytesQueuedForUpload();
    public int	getNbPeersWithUploadQueued();
    public int	getNbPeersWithUploadBlocked();
    public int	getNbPeersUnchoked();
    public int	getNbPeersUnchoking();

    public int	getNbPieces();
    
    /** Often better to use getPiece(pieceNumber)
     */
	public PEPiece[]	getPieces();

    /** @return PEPiece or null if piece not currently active
     */
	public PEPiece		getPiece(int pieceNumber);


	public PEPeerManagerStats
	getStats();

	public void
	processTrackerResponse(
		TRTrackerAnnouncerResponse	response );

	public int getNbPeers();

	public int getNbSeeds();

	public int getPieceLength(int pieceNumber);

	public long getRemaining();

	public int getHiddenPiece();

	public long getHiddenBytes();

	public long getETA( boolean smoothed );

	public String getElapsedTime();

	// Time Started in ms
	public long getTimeStarted( boolean mono_time );

	public long getTimeStartedSeeding( boolean mono_time );

	public String
	getConnectHealth(
		boolean	verbose );
	
	public void
	addListener(
		PEPeerManagerListener	l );

	public void
	removeListener(
		PEPeerManagerListener	l );

	public void addPiece(PEPiece piece, int pieceNumber, PEPeer for_peer );

  public boolean needsMD5CheckOnCompletion(int pieceNumber);

  public boolean
  isSeeding();

  public boolean
  isMetadataDownload();

  public int
  getTorrentInfoDictSize();

  public void
  setTorrentInfoDictSize(
	int	size );

  public boolean
  isSuperSeedMode();

  public boolean
  canToggleSuperSeedMode();

  public void
  setSuperSeedMode( boolean on );

  public boolean
  seedPieceRecheck();

  public int getNbRemoteTCPConnections();
  public int getNbRemoteUDPConnections();
  public int getNbRemoteUTPConnections();

  public long getLastRemoteConnectionTime();

  public int
  getMaxNewConnectionsAllowed( String network );

  public boolean
  hasPotentialConnections();

  /**
   * Data bytes received.
   * @param l
   */
	public void	dataBytesReceived( PEPeer peer, int	l );

  /**
   * Data bytes sent.
   * @param l
   */
	public void	dataBytesSent( PEPeer peer, int	l );

  /**
   * Protocol bytes sent.
   * @param length
   */
  public void protocolBytesSent( PEPeer peer, int length );

  /**
   * Protocol bytes received.
   * @param length
   */
  public void protocolBytesReceived( PEPeer peer, int length );



	public void
	discarded(
		PEPeer peer,
		int		l );

	public PEPeerStats
	createPeerStats(
		PEPeer	owner );

	public PEPeer
	getMyPeer();
	
	public List<PEPeer>
	getPeers();

	public List<PEPeer>
	getPeers(
		String	address );

	public int
	getPendingPeerCount();

	public PeerDescriptor[]
   	getPendingPeers();

	public PeerDescriptor[]
	getPendingPeers(
		String	address );

	public void
	addPeer(
		PEPeer	peer );


  /**
   * Add a new peer, using the default internal PEPeer implementation
   * (like for peers given in announce reply), using the given address
   * and port.
   * @param ip_address of peer to inject
   * @param tcp_port of peer to inject
   * @param udp_port of peer to inject (0 if unknown)
   * @param use_crypto use encrypted transport
   */

	public void
	addPeer(
		String 		ip_address,
		int 		tcp_port,
		int			udp_port,
		boolean 	use_crypto,
		Map			user_data );

	public void
	peerDiscovered(
		String		peer_source,
		String 		ip_address,
		int			tcp_port,
		int			udp_port,
		boolean 	use_crypto );

	public void
	removePeer(
		PEPeer	peer );

	public void
	removePeer(
		PEPeer	peer,
		String	reason );

	public void
	informFullyConnected(
		PEPeer	peer );
	
	public DiskManagerReadRequest
	createDiskManagerRequest(
	   int pieceNumber,
	   int offset,
	   int length );

	public void
	requestCanceled(
		DiskManagerReadRequest	item );

	public boolean
	requestExists(
		String			peer_ip,
		int				piece_number,
		int				offset,
		int				length );

	public boolean
	validatePieceReply(
		PEPeerTransport		originator,
		int 				pieceNumber,
		int 				offset,
		DirectByteBuffer 	data );

	public void
	writeBlock(
		int 				pieceNumber,
		int 				offset,
		DirectByteBuffer 	data,
		Object 				sender,			// either a PEPeer or a String
        boolean     		cancel);

//  public void writeBlockAndCancelOutstanding(int pieceNumber, int offset, DirectByteBuffer data,PEPeer sender);

  public boolean isWritten( int piece_number, int offset );

  /**
   * Are we in end-game mode?
   * @return true if in end game mode, false if not
   */
  public boolean isInEndGameMode();

  /**
   * Notify the manager that the given peer connection has been closed.
   * @param peer closed
   */
  public void peerConnectionClosed( PEPeerTransport peer, boolean connect_failed, boolean network_failed );



  /**
   * Register a peer connection for peer exchange handling.
   * NOTE: Creation could fail if the peer is not eligible for peer exchange (like if it's remote port is unknown).
   * @param base_peer exchaning with
   * @return peer database connection item, or null if creation failed
   */
  public PeerExchangerItem createPeerExchangeConnection( PEPeerTransport base_peer );


  /**
   * Notify that the given peer connection represents our own client.
   * @param self peer
   */
  public void peerVerifiedAsSelf( PEPeerTransport self );


  /**
   * Get the limited rate group used for upload limiting.
   * @return upload limit group
   */
  public LimitedRateGroup getUploadLimitedRateGroup();

  /**
   * Get the limited rate group used for download limiting.
   * @return download limit group
   */
  public LimitedRateGroup getDownloadLimitedRateGroup();

  public int getEffectiveUploadRateLimitBytesPerSecond();
  
  public int getUploadRateLimitBytesPerSecond();

  public int getDownloadRateLimitBytesPerSecond();

  /** To retreive arbitrary objects against this object. */
  public Object getData (String key);
  /** To store arbitrary objects against this object. */
  public void setData (String key, Object value);


  /**
   * Get the average completion percentage of connected peers.
   * @return average percent complete in thousand notation
   */
  public int getAverageCompletionInThousandNotation();

  /**
   * Max completion of connected peers (doesn't factor in our completion)
   * @return
   */
  
  public int getMaxCompletionInThousandNotation( boolean never_include_seeds );
  
	/**
	 * Locate an existing transport via peer id byte identity.
	 * @param peer_id to look for
	 * @return transport with matching identity, or null if no match is found
	 */
	public PEPeerTransport getTransportFromIdentity( byte[] peer_id );

	/**
	 * Locate an existing transport via [IP] Address.
	 * @param peer String to look for
	 * @return PEPeerTransport with matching address String, or null if no match is found
	 */
	public PEPeerTransport getTransportFromAddress(String peer);

	public boolean
	getPreferUDP();

	public void
	setPreferUDP(
		boolean	prefer );

	public void
	addRateLimiter(
		LimitedRateGroup	group,
		boolean				upload );

	public void
	removeRateLimiter(
		LimitedRateGroup	group,
		boolean				upload );

	public TrackerPeerSource
	getTrackerPeerSource();

	public boolean
	isPeerSourceEnabled(
		String	peer_source );

	public boolean
	isNetworkEnabled(
		String	network );

	public int
	getPartitionID();

	public void
	setMaskDownloadCompletion(
		boolean	mask );
	
	public void
	removeAllPeers(
		String		reason );
	
	public boolean
	isDestroyed();

	public void
	generateEvidence(
		IndentWriter		writer );

	public void
	setStatsReceiver(
		StatsReceiver	receiver );

	public interface
	StatsReceiver
	{
		public void
		receiveStats(
			PEPeer		peer,
			Map			stats );
	}

}
