/*
 * File    : PRDownloadStats.java
 * Created : 30-Jan-2004
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

package com.biglybt.pifimpl.remote.download;

/**
 * @author parg
 *
 */

import com.biglybt.pif.download.DownloadStats;
import com.biglybt.pifimpl.remote.RPException;
import com.biglybt.pifimpl.remote.RPObject;
import com.biglybt.pifimpl.remote.RPReply;
import com.biglybt.pifimpl.remote.RPRequest;


public class
RPDownloadStats
	extends		RPObject
	implements 	DownloadStats
{
	protected transient DownloadStats		delegate;

		// don't change these field names as they are visible on XML serialisation

	public long				downloaded;
	public long				uploaded;
	public int				completed;
	public int				downloadCompletedLive;
	public int				downloadCompletedStored;
	public String			status;
	public String			status_localised;
	public long				upload_average;
	public long				download_average;
	public String			eta;
	public int				share_ratio;
	public float			availability;
	public long				bytesUnavailable;
	public int				health;

	public static RPDownloadStats
	create(
		DownloadStats		_delegate )
	{
		RPDownloadStats	res =(RPDownloadStats)_lookupLocal( _delegate );

		if ( res == null ){

			res = new RPDownloadStats( _delegate );
		}

		return( res );
	}

	protected
	RPDownloadStats(
		DownloadStats		_delegate )
	{
		super( _delegate );
	}

	@Override
	protected void
	_setDelegate(
		Object		_delegate )
	{
		delegate = (DownloadStats)_delegate;

		downloaded					= delegate.getDownloaded();
		uploaded					= delegate.getUploaded();
		completed					= delegate.getCompleted();
		downloadCompletedLive		= delegate.getDownloadCompleted(true);
		downloadCompletedStored		= delegate.getDownloadCompleted(false);
		status						= delegate.getStatus();
		status_localised			= delegate.getStatus(true);
		upload_average				= delegate.getUploadAverage();
		download_average			= delegate.getDownloadAverage();
		eta							= delegate.getETA();
		share_ratio					= delegate.getShareRatio();
		availability				= delegate.getAvailability();
		bytesUnavailable = delegate.getBytesUnavailable();
		health						= delegate.getHealth();
	}

	@Override
	public Object
	_setLocal()

		throws RPException
	{
		return( _fixupLocal());
	}

	@Override
	public RPReply
	_process(
		RPRequest	request	)
	{
		String	method = request.getMethod();

		throw( new RPException( "Unknown method: " + method ));
	}


		// ***************************************************

	@Override
	public String
	getStatus()
	{
		return( status );
	}

	@Override
	public String
	getStatus(boolean localised)
	{
		return (localised)? status_localised : status;
	}

	@Override
	public String
	getDownloadDirectory()
	{
		notSupported();

		return( null );
	}

	@Override
	public String
	getTargetFileOrDir()
	{
		notSupported();

		return( null );
	}

	@Override
	public String
	getTrackerStatus()
	{
		notSupported();

		return( null );
	}

	@Override
	public int
	getCompleted()
	{
		return( completed );
	}

	@Override
	public int
	getDownloadCompleted(boolean bLive)
	{
		return( bLive ? downloadCompletedLive : downloadCompletedStored );
	}


	@Override
	public int
	getCheckingDoneInThousandNotation()
	{
		notSupported();

		return( 0 );
	}

	@Override
	public void
	resetUploadedDownloaded(
		long l1,
		long l2 )
	{
		notSupported();
	}

	@Override
	public long
	getDownloaded()
	{
		return( downloaded );
	}

	@Override
	public long
	getDownloaded(
		boolean	include_protocol )
	{
		notSupported();

		return( 0 );
	}

	@Override
	public long
	getUploaded()
	{
		return( uploaded );
	}

	@Override
	public long
	getUploaded(
		boolean	include_protocol )
	{
		notSupported();

		return( 0 );
	}

	@Override
	public long
	getRemaining()
	{
		notSupported();

		return( 0 );
	}

	@Override
	public long
	getDiscarded()
	{
		notSupported();

		return( 0 );
	}

	@Override
	public long
	getDownloadAverage()
	{
		return( download_average );
	}

	@Override
	public long
	getDownloadAverage(
		boolean	include_protocol )
	{
		notSupported();

		return( 0 );
	}

	@Override
	public long
	getUploadAverage()
	{
		return( upload_average );
	}

	@Override
	public long
	getUploadAverage(
		boolean	include_protocol )
	{
		notSupported();

		return( 0 );
	}

	@Override
	public long
	getTotalAverage()
	{
		notSupported();

		return( 0 );
	}

	@Override
	public String
	getElapsedTime()
	{
		notSupported();

		return( null );
	}

	@Override
	public String
	getETA()
	{
		return( eta );
	}

	@Override
	public long
	getETASecs()
	{
		notSupported();
		return(0);
	}

	@Override
	public long
	getHashFails()
	{
		notSupported();

		return( 0 );
	}

	@Override
	public int
	getShareRatio()
	{
		return( share_ratio );
	}

	@Override
	public long
	getTimeStarted()
	{
		 notSupported();
		 return ( 0 );
	}

	@Override
	public float
	getAvailability()
	{
		return( availability );
	}

  @Override
  public long getSecondsDownloading() {
		 notSupported();
		 return ( 0 );
  }

  @Override
  public long getSecondsOnlySeeding() {
		 notSupported();
		 return ( 0 );
  }

  @Override
  public long getTimeStartedSeeding() {
		 notSupported();
		 return ( 0 );
  }

	@Override
	public long
	getSecondsSinceLastDownload()
	{
		notSupported();
		return ( 0 );
	}

	@Override
	public long
	getSecondsSinceLastUpload()
	{
		notSupported();
		return ( 0 );
	}

	@Override
	public int
	getHealth()
	{
		return( health );
	}

	// @see com.biglybt.pif.download.DownloadStats#getBytesUnavailable()
	@Override
	public long
	getBytesUnavailable()
	{
		return bytesUnavailable;
	}

	// @see com.biglybt.pif.download.DownloadStats#getRemainingExcludingDND()
	@Override
	public long getRemainingExcludingDND() {
		notSupported();

		return( 0 );
	}
}