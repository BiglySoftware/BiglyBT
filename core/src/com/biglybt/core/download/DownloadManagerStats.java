/*
 * File    : DownloadManagerStats.java
 * Created : 22-Oct-2003
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

package com.biglybt.core.download;

import java.net.URL;

/**
 * @author parg
 *
 */
public interface
DownloadManagerStats
{
  /** Find out percentage done of current state
   * <P>
   * Use getDownloadCompleted() if you wish to find out a torrents download completion level
   *
   * @return 0 to 1000, 0% to 100% respectively
   *         When state is Allocating, Checking, or Initializing, this
   *         returns the % done of that task
   *         Any other state MAY return getDownloadCompleted()
   */
	public int
	getCompleted();

  /** Retrieve the level of download completion, *including* DND files.
   * <P>
   * To understand the bLive parameter, you must know a bit about the
   * Torrent activation process:<BR>
   * 1) Torrent goes into ST_WAITING<BR>
   * 2) Torrent moves to ST_PREPARING<BR>
   * 3) Torrent moves to ST_DOWNLOADING or ST_SEEDING
   * <P>
   * While in ST_PREPARING, Completion Level is rebuilt (either via Fast Resume
   * or via piece checking). Quite often, the download completion level before
   * ST_PREPARING and after ST_PREPARING are identical.
   * <P>
   * Before going into ST_PREPARING, we store the download completion level.
   * If you wish to retrieve this value instead of the live "building" one,
   * pass false for the parameter.
   *
   * @param bLive
   * 	true - Always returns the known completion level of the torrent
   * <P>
   *  false - In the case of ST_PREPARING, return completion level
   *          before of the torrent ST_PREPARING started.
   *          Otherwise, same as true.
   *
   * @return 0 - 1000
   */
	public int
	getDownloadCompleted(boolean bLive);

	public void
	setDownloadCompletedBytes(long completedBytes);

	/**
	 * Get the number of bytes of the download that we currently have.
	 * <P>
	 * Includes bytes downloaded for files marked as DND/Skipped
	 */
	public long
	getDownloadCompletedBytes();

	public void
	recalcDownloadCompleteBytes();

  /**
   * Get the total number of bytes ever downloaded.
   * @return total bytes downloaded
   */
	public long	getTotalDataBytesReceived();

	/**
	 * data bytes received minus discards and hashfails
	 * @return
	 */

	public long	getTotalGoodDataBytesReceived();

	public long getTotalProtocolBytesReceived();

	public long	getSessionDataBytesReceived();


  /**
   * Get the total number of bytes ever uploaded.
   * @return total bytes uploaded
   */
	public long	getTotalDataBytesSent();

	public long getTotalProtocolBytesSent();

	public long	getSessionDataBytesSent();

	/**
	 * Resets the total bytes sent/received - will stop and start the download if it is running
	 */
	public void resetTotalBytesSentReceived( long sent, long received );

	/**
	 * Returns the bytes remaining.  *Includes* DND files
	 */
	public long getRemaining();

	public long
	getDiscarded();

	public long
	getHashFailBytes();

	public long
	getHashFailCount();

	/**
	 * Gives the share ratio of the torrent in 1000ths (i.e. 1000 = share ratio of 1)
	 * -1 if actually infinite (downloaded = 0, uploaded > 0 ), Integer.MAX_VALUE if massive
	 */
	public int
	getShareRatio();

	public void
	setShareRatio(
		int		ratio );

	public long getDataReceiveRate();

	public long getProtocolReceiveRate();


	public long getDataSendRate();

	public long getProtocolSendRate();

	/**
	 * Swarm speed
	 * @return
	 */
	public long
	getTotalAverage();

		/**
		 * Average for a peer in the swarm
		 * @return
		 */
	public long
	getTotalAveragePerPeer();

		/**
		 * In general history isn't available, however if this method is called it will start retention for a certain period of time
		 * @return
		 */

	public void
	setRecentHistoryRetention(
		boolean		required );

		/**
		 * Get any recent history. Returned values are send rate, receive rate, peer-swarm average and eta
		 * @return
		 */

	public int[][]
	getRecentHistory();

	public String
	getElapsedTime();

	// in ms
	public long
	getTimeStarted();

  /* -1 if not seeding */
	public long
	getTimeStartedSeeding();

	/**
	 * Returns the ETA time in seconds.
	 *   0 = download is complete.
	 * < 0 = download is complete and it took -xxx time to complete.
	 * 	-1 = unknown eta (no peer manager) or download completed 1s ago
	 * Constants.CRAPPY_INFINITE_AS_LONG = incomplete and 0 average speed
	 */

	/**
	 * *deprecated - use getSmoothedETA as the unstability of getETA is pretty bad
	 * @return
	 */
	public long
	getETA();

	public long
	getSmoothedETA();

	public long
	getPeakDataReceiveRate();

	public long
	getPeakDataSendRate();

	public long
	getSmoothedDataReceiveRate();

	public long
	getSmoothedDataSendRate();

	public float
	getAvailability();


	public long
	getSecondsDownloading();

	public long
	getSecondsOnlySeeding();

	public void
	setCompleted(
		int		c );

  /**
   * Get the max upload rate allowed for this download.
   * @return upload rate in bytes per second, 0 for unlimited, -1 for upload disabled
   */
  public int getUploadRateLimitBytesPerSecond();

  /**
   * Set the max upload rate allowed for this download.
   * @param max_rate_bps limit in bytes per second, 0 for unlimited, -1 for upload disabled
   */
  public void setUploadRateLimitBytesPerSecond( int max_rate_bps );


  /**
   * Get the max download rate allowed for this download.
   * @return download rate in bytes per second, 0 for unlimited, -1 for download disabled
   */
  public int getDownloadRateLimitBytesPerSecond();

  /**
   * Set the max download rate allowed for this download.
   * @param max_rate_bps limit in bytes per second, 0 for unlimited, -1 for download disabled
   */
  public void setDownloadRateLimitBytesPerSecond( int max_rate_bps );

	public default int getTimeSinceLastDataReceivedInSeconds(){
		return(getTimeSinceLastDataReceivedInSeconds( false ));
	}
	public default int getTimeSinceLastDataSentInSeconds(){
		return( getTimeSinceLastDataSentInSeconds( false ));
	}

	public int getTimeSinceLastDataReceivedInSeconds( boolean this_session );
	public int getTimeSinceLastDataSentInSeconds( boolean this_session );

	public long getAvailWentBadTime();
	public long getBytesUnavailable();

	/*
	public long getEstimatedDownloaded();
	public long getEstimatedUploaded();
	*/
	
	public void
	restoreSessionTotals(
		long		_saved_data_bytes_downloaded,
		long		_saved_data_bytes_uploaded,
		long		_saved_discarded,
		long		_saved_hashfails,
		long		_saved_SecondsDownloading,
		long		_saved_SecondsOnlySeeding );

	public long getRemainingExcludingDND();

	public long getSizeExcludingDND();

	public int getPercentDoneExcludingDND();

	public int[] getFilePriorityStats();
}
