/*
 * File    : PeerManager.java
 * Created : 28-Dec-2003
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

import java.util.Map;

import com.biglybt.core.networkmanager.Transport;
import com.biglybt.pif.disk.DiskManager;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadException;
import com.biglybt.pif.utils.PooledByteBuffer;

/**
 * @author parg
 *
 */
public interface
PeerManager
{
	public Download
	getDownload()

		throws DownloadException;

	public void
	addPeer(
		Peer		peer );


	  /**
	   * Add a new peer, using the default internal Peer implementation
	   * (like for peers given in announce reply), using the given address
	   * and port.
	   * @param ip_address of peer to inject
	   * @param port of peer to inject
	   */

	public void
	addPeer(
		String 		ip_address,
		int 		port );

	public void
	addPeer(
		String 		ip_address,
		int 		tcp_port,
		boolean 	use_crypto );

	public void
	addPeer(
		String 		ip_address,
		int 		tcp_port,
		int			udp_port,
		boolean 	use_crypto );

	public void
	addPeer(
		String 				ip_address,
		int 				tcp_port,
		int					udp_port,
		boolean 			use_crypto,
		Map<Object,Object>	user_data );

	public void
	peerDiscovered(
		String				peer_source,
		String 				ip_address,
		int 				tcp_port,
		int					udp_port,
		boolean 			use_crypto );

	public default void
	removePeer(
		Peer		peer )
	{
		removePeer( peer, "Unknown", Transport.CR_NONE );
	}
	
	public void
	removePeer(
		Peer		peer,
		String		reason,
		int			reason_code );

	public Peer[]
	getPeers();

		/**
		 * returns the peers for the given address
		 * @param address
		 * @return
		 */

	public Peer[]
	getPeers(
		String		address );

		/**
		 * Get the list of currently pending peers
		 * @since 4005
		 * @return
		 */

	public PeerDescriptor[]
   	getPendingPeers();
		/**
		 * returns the pending connections to the given address
		 * @param address
		 * @return
		 */

	public PeerDescriptor[]
	getPendingPeers(
		String		address );

	public DiskManager
	getDiskManager();

	public PeerManagerStats
	getStats();

	public boolean
	isSeeding();

	public boolean
	isSuperSeeding();

	public PeerStats
	createPeerStats(
		Peer		peer );

	public void
	requestComplete(
		PeerReadRequest		request,
		PooledByteBuffer 	data,
		Peer 				sender);

	public void
	requestCancelled(
		PeerReadRequest		request,
		Peer				sender );

	public Piece[]
	getPieces();

    public int getUploadRateLimitBytesPerSecond();

    public int getDownloadRateLimitBytesPerSecond();

	public void
	addListener(
		PeerManagerListener2	l );

	public void
	removeListener(
		PeerManagerListener2	l );
}
