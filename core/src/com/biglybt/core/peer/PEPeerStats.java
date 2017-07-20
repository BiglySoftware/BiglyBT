/*
 * File    : PEPeerStats
 * Created : 15-Oct-2003
 * By      : stuff
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
 * Provides peer statistics.
 * It uses Average to compute its different averages.
 */

public interface
PEPeerStats
{
  public PEPeer getPeer();
  public void setPeer(PEPeer p);

  ////// SET METHODS ///////
  /**
   * The given number of data (payload) bytes have been sent to the peer.
   * @param num_bytes
   */
  public void dataBytesSent( int num_bytes );

  /**
   * The given number of protocol (overhead) bytes have been sent to the peer.
   * @param num_bytes
   */
  public void protocolBytesSent( int num_bytes );

  /**
   * The given number of data (payload) bytes have been received from the peer.
   * @param num_bytes
   */
  public void dataBytesReceived( int num_bytes );

  /**
   * The given number of protocol (overhead) bytes have been received from the peer.
   * @param num_bytes
   */
  public void protocolBytesReceived( int num_bytes );


  /**
   * The given number of bytes received from the peer were discarded.
   * @param num_bytes
   */
  public void bytesDiscarded( int num_bytes );

  /**
   * The peer has completed a piece of the given byte size.
   * @param piece_size
   */
  public void hasNewPiece( int piece_size );


  /**
   * The peer has statistically sent a piecce of the given byte size.
   * @param piece_size
   */
  public void statisticalSentPiece( int piece_size );



  //////GET METHODS ///////
  /**
   * Get the the average bytes-per-second speed that we are receiving piece data from the peer.
   * @return average speed.
   */
  public long getDataReceiveRate();

  /**
   * Get the the average bytes-per-second speed that we are receiving protocol messages from the peer.
   * @return average speed.
   */
  public long getProtocolReceiveRate();


  /**
   * Get the total number of data (payload) bytes received from the peer.
   * @return total
   */
  public long getTotalDataBytesReceived();

  /**
   * Get the total number of protocol (overhead) bytes received from the peer.
   * @return total
   */
  public long getTotalProtocolBytesReceived();


  /**
   * Get the the average bytes-per-second speed that we are sending piece data to the peer.
   * @return average speed.
   */
  public long getDataSendRate();

  /**
   * Get the the average bytes-per-second speed that we are sending protocol messages to the peer.
   * @return average speed.
   */
  public long getProtocolSendRate();


  /**
   * Get the total number of data (payload) bytes sent to the peer.
   * @return total
   */
  public long getTotalDataBytesSent();

  /**
   * Get the total number of protocol (overhead) bytes sent to the peer.
   * @return total
   */
  public long getTotalProtocolBytesSent();



  /**
   * Get the the longer-average bytes-per-second speed at which the peer is uploading data to us.
   * @return average speed
   */
  public long getSmoothDataReceiveRate();

  /**
   * Get the total number of discarded bytes received from the peer.
   * @return total discarded
   */
  public long getTotalBytesDiscarded();

  /**
   * Get the estimated total download rate of the peer.
   * @return estimated rate in bytes-per-second
   */
  public long getEstimatedDownloadRateOfPeer();

  /**
   * Get the estimated total upload rate of the peer.
   * @return estimated rate in bytes-per-second
   */
  public long getEstimatedUploadRateOfPeer();

  public long getEstimatedSecondsToCompletion();

  /**
   * Get the number of bytes downloaded in total by this peer
   * (includes data downloaded from all other peers).
   * @return total download bytes done
   */
  public long getTotalBytesDownloadedByPeer();

  /**
   * Disk access stats methods
   * @param bytes
   */
  public void diskReadComplete( long bytes );
  public int getTotalDiskReadCount();
  public int getAggregatedDiskReadCount();
  public long getTotalDiskReadBytes();

  public void setUploadRateLimitBytesPerSecond( int bytes );
  public void setDownloadRateLimitBytesPerSecond( int bytes );
  public int getUploadRateLimitBytesPerSecond();
  public int getDownloadRateLimitBytesPerSecond();

  	// external rate control

  public int getPermittedBytesToSend();
  public void permittedSendBytesUsed( int num );

  public int getPermittedBytesToReceive();
  public void permittedReceiveBytesUsed( int num );

}