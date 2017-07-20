/*
 * File    : PeerStats.java
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
 * Statistical information for a Peer.
 */
public interface PeerStats
{
	/**
	 * Get the the average bytes-per-second speed that we are receiving piece
	 * data from the peer.
	 *
	 * @return average speed.
	 *
	 * @since 2.0.6.0
	 */
	public int getDownloadAverage();

	/**
	 * Get the the longer-average bytes-per-second speed at which the peer is
	 * uploading data to us.
	 *
	 * @return average speed
	 *
	 * @since 2.0.6.0
	 */
	public int getReception();

	/**
	 * Get the the average bytes-per-second speed that we are sending piece data
	 * to the peer.
	 *
	 * @return average speed.
	 *
	 * @since 2.0.6.0
	 */
	public int getUploadAverage();

	/**
	 * Get the estimated total download rate of the peer.
	 * @return estimated rate in bytes-per-second
	 */
	public int getTotalAverage();

	/**
	 * Get the total number of discarded bytes received from the peer.
	 * @return total discarded
	 *
	 * @since 2.0.6.0
	 */
	public long getTotalDiscarded();

	/**
	 * Get the total number of data (payload) bytes sent to the peer.
	 * @return total
	 *
	 * @since 2.0.6.0
	 */
	public long getTotalSent();

	/**
	 * Get the total number of data (payload) bytes received from the peer.
	 * @return total
	 *
	 * @since 2.0.6.0
	 */
	public long getTotalReceived();

	/**
	 * Get the estimated total upload rate of the peer.
	 * @return estimated rate in bytes-per-second
	 *
	 * @since 2.0.6.0
	 */
	public int getStatisticSentAverage();


	/**
	 * For an external process receiving bytes on behalf of this peer this gives the current
	 * rate-limited number of bytes that can be received. Update with actual send using 'permittedReceiveBytesUsed' below.
	 * @since 4.4.0.7
	 * @return
	 */

	public int getPermittedBytesToReceive();

	public void permittedReceiveBytesUsed( int bytes );

	/**
	 * For an external process sending bytes on behalf of this peer this gives the current
	 * rate-limited number of bytes that can be sent. Update with actual send using 'sent' below.
	 * @since 4.4.0.7
	 * @return
	 */

	public int getPermittedBytesToSend();

	public void permittedSendBytesUsed( int bytes );

	/**
	 * The given number of data (payload) bytes have been sent to the peer.
	 * This number gets added to the total and is used to calculate the rate.
	 * <p>
	 * Use this if you are talking to the peer outside of Azureus' API, and
	 * want your stats added into Azureus'
	 *
	 * @param bytes
	 *
	 * @since 4.4.0.7
	 */

	public void received(int bytes);

	/**
	 * The given number of data (payload) bytes have been received from the peer.
	 * This number gets added to the total and is used to calculate the rate.
	 * <p>
	 * Use this if you are talking to the peer outside of Azureus' API, and
	 * want your stats added into Azureus'
	 *
	 * @param bytes
	 *
	 * @since 2.1.0.0
	 */

	public void sent(int bytes);

	/**
	 * The given number of bytes received from the peer were discarded.
	 * This number gets added to the total and is used to calculate rates that
	 * include disgarded inforamtion.
	 * <p>
	 * Use this if you are talking to the peer outside of Azureus' API, and
	 * want your stats added into Azureus'
	 *
	 * @param bytes
	 *
	 * @since 2.1.0.0
	 */
	public void discarded(int bytes);

	/**
	 * Get the amount of time that has elapsed since the connection with the
	 * peer has been established.
	 *
	 * @return Amount of time in ms.
	 *
	 * @since 2.4.0.0
	 */
	public long getTimeSinceConnectionEstablished();

	public void
	setDownloadRateLimit(
		int	bytes_per_sec );

	public int
	getDownloadRateLimit();

	/**
	 * @since 4.7.0.1
	 * @param bytes_per_sec
	 */

	public void
	setUploadRateLimit(
		int	bytes_per_sec );

	/**
	 * @since 4.7.0.1
	 * @param bytes_per_sec
	 */

	public int
	getUploadRateLimit();

	/**
	 * @since 4.4.0.1
	 * @return
	 */
	public long
	getOverallBytesRemaining();
}
