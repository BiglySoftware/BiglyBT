/*
 * File    : DownloadStatsImpl.java
 * Created : 08-Jan-2004
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

package com.biglybt.pifimpl.local.download;

/**
 * @author parg
 *
 */

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerStats;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.pif.download.DownloadStats;

public class
DownloadStatsImpl
	implements DownloadStats
{
	protected DownloadManager		dm;
	protected DownloadManagerStats	dm_stats;

	protected
	DownloadStatsImpl(
		DownloadManager	_dm )
	{
		dm 			= _dm;
		dm_stats	= dm.getStats();
	}

	@Override
	public String
	getStatus()
	{
		return( DisplayFormatters.formatDownloadStatusDefaultLocale( dm));
	}

	@Override
	public String
	getStatus(boolean localised)
	{
		return (localised)? DisplayFormatters.formatDownloadStatus( dm ) : getStatus();

	}

	@Override
	public String
	getDownloadDirectory()
	{
		return( dm.getSaveLocation().getParent());
	}

	@Override
	public String
	getTargetFileOrDir()
	{
		return( dm.getSaveLocation().toString());
	}

	@Override
	public String
	getTrackerStatus()
	{
		return(dm.getTrackerStatus());
	}

	@Override
	public int
	getCompleted()
	{
		return( dm_stats.getCompleted());
	}

	@Override
	public int
	getDownloadCompleted(boolean bLive)
	{
		return( dm_stats.getDownloadCompleted(bLive) );
	}


	@Override
	public int
	getCheckingDoneInThousandNotation()
	{
		com.biglybt.core.disk.DiskManager	disk = dm.getDiskManager();

 		if ( disk != null ){

 			return( disk.getCompleteRecheckStatus());
 		}

 		return( -1 );
	}

	@Override
	public void
	resetUploadedDownloaded(
		long 	new_up,
		long 	new_down )
	{
		dm_stats.resetTotalBytesSentReceived( new_up, new_down );
	}

	@Override
	public long
	getDownloaded()
	{
		return( dm_stats.getTotalDataBytesReceived());
	}

	@Override
	public long
	getDownloaded(
		boolean	include_protocol )
	{
		long res = dm_stats.getTotalDataBytesReceived();

		if ( include_protocol ){

			res += dm_stats.getTotalProtocolBytesReceived();
		}

		return( res );
	}

	@Override
	public long
	getRemaining()
	{
		return( dm_stats.getRemaining());
	}

	@Override
	public long getRemainingExcludingDND() {
		return dm_stats.getRemainingExcludingDND();
	}

	@Override
	public long
	getUploaded()
	{
		return( dm_stats.getTotalDataBytesSent());
	}

	@Override
	public long
	getUploaded(
		boolean	include_protocol )
	{
		long res = dm_stats.getTotalDataBytesSent();

		if ( include_protocol ){

			res += dm_stats.getTotalProtocolBytesSent();
		}

		return( res );
	}

	@Override
	public long
	getDiscarded()
	{
		return( dm_stats.getDiscarded());
	}

	@Override
	public long
	getDownloadAverage()
	{
		return( dm_stats.getDataReceiveRate());
	}

	@Override
	public long
	getDownloadAverage(
		boolean	include_protocol )
	{
		long res = dm_stats.getDataReceiveRate();

		if ( include_protocol ){

			res += dm_stats.getProtocolReceiveRate();
		}

		return( res );
	}

	@Override
	public long
	getUploadAverage()
	{
		return( dm_stats.getDataSendRate());
	}

	@Override
	public long
	getUploadAverage(
		boolean	include_protocol )
	{
		long res = dm_stats.getDataSendRate();

		if ( include_protocol ){

			res += dm_stats.getProtocolSendRate();
		}

		return( res );
	}

	@Override
	public long
	getTotalAverage()
	{
		return( dm_stats.getTotalAverage());
	}

	@Override
	public String
	getElapsedTime()
	{
		return( dm_stats.getElapsedTime());
	}

	/* (non-Javadoc)
	 * @see com.biglybt.pif.download.DownloadStats#getETA()
	 */
	@Override
	public String
	getETA()
	{
		return(DisplayFormatters.formatETA(dm_stats.getSmoothedETA()));
	}

	@Override
	public long
	getETASecs()
	{
		return( dm_stats.getSmoothedETA());
	}

	@Override
	public long
	getHashFails()
	{
		return( dm_stats.getHashFailCount());
	}

	@Override
	public int
	getShareRatio()
	{
		return( dm_stats.getShareRatio());
	}

	// in ms
	@Override
	public long
	getTimeStarted()
	{
		return (dm_stats.getTimeStarted());
	}

	@Override
	public float
	getAvailability()
	{
		return( dm_stats.getAvailability());
	}

	@Override
	public long
	getBytesUnavailable()
	{
		return( dm_stats.getBytesUnavailable());
	}

	@Override
	public long getSecondsOnlySeeding() {
		return dm_stats.getSecondsOnlySeeding();
	}

	@Override
	public long getSecondsDownloading() {
	  return dm_stats.getSecondsDownloading();
	}

	@Override
	public long getTimeStartedSeeding() {
	  return dm_stats.getTimeStartedSeeding();
	}

	@Override
	public long
	getSecondsSinceLastDownload()
	{
		return(dm_stats.getTimeSinceLastDataReceivedInSeconds());
	}

	@Override
	public long
	getSecondsSinceLastUpload()
	{
		return(dm_stats.getTimeSinceLastDataSentInSeconds());
	}

	@Override
	public int
	getHealth()
	{
		switch( dm.getHealthStatus()){

			case DownloadManager.WEALTH_STOPPED:
			{
				return( DownloadStats.HEALTH_STOPPED );

			}
			case DownloadManager.WEALTH_NO_TRACKER:
			{
				return( DownloadStats.HEALTH_NO_TRACKER );

			}
			case DownloadManager.WEALTH_NO_REMOTE:
			{
				return( DownloadStats.HEALTH_NO_REMOTE );

			}
			case DownloadManager.WEALTH_OK:
			{
				return( DownloadStats.HEALTH_OK );

			}
			case DownloadManager.WEALTH_KO:
			{
				return( DownloadStats.HEALTH_KO );

			}
			case DownloadManager.WEALTH_ERROR:
			{
				return( DownloadStats.HEALTH_ERROR );
			}
			default:
			{
				Debug.out( "Invalid health status" );

				return( dm.getHealthStatus());

			}
		}
	}
}
