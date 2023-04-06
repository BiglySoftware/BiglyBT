/*
 * Created on 21-Jun-2004
 * Created by Paul Gardner
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */

package com.biglybt.pifimpl.remote.tracker;

import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.tracker.*;
import com.biglybt.pifimpl.remote.*;
import com.biglybt.pifimpl.remote.torrent.RPTorrent;


/**
 * @author parg
 *
 */

public class
RPTrackerTorrent
	extends		RPObject
	implements 	TrackerTorrent
{
	protected transient TrackerTorrent		delegate;

		// don't change the names of these, they appear in XML serialisation

	public RPTorrent				torrent;

	public int		status;
	public long		total_uploaded;
	public long		total_downloaded;
	public long		average_uploaded;
	public long		average_downloaded;
	public long		total_left;
	public long 	completed_count;
	public long		total_bytes_in;
	public long		average_bytes_in;
	public long		total_bytes_out;
	public long 	average_bytes_out;
	public long		scrape_count;
	public long		average_scrape_count;
	public long		announce_count;
	public long		average_announce_count;
	public int		seed_count;
	public int		leecher_count;
	public int		bad_NAT_count;


	public static RPTrackerTorrent
	create(
		TrackerTorrent		_delegate )
	{
		RPTrackerTorrent	res =(RPTrackerTorrent)_lookupLocal( _delegate );

		if ( res == null ){

			res = new RPTrackerTorrent( _delegate );
		}

		return( res );
	}

	protected
	RPTrackerTorrent(
		TrackerTorrent		_delegate )
	{
		super( _delegate );

		if ( delegate.getTorrent() != null ){

			torrent = (RPTorrent)_lookupLocal( delegate.getTorrent());

			if ( torrent == null ){

				torrent = RPTorrent.create( delegate.getTorrent());
			}
		}
	}

	@Override
	protected void
	_setDelegate(
		Object		_delegate )
	{
		delegate = (TrackerTorrent)_delegate;

		status					= delegate.getStatus();
		total_uploaded			= delegate.getTotalUploaded();
		total_downloaded		= delegate.getTotalDownloaded();
		average_uploaded		= delegate.getAverageUploaded();
		average_downloaded		= delegate.getAverageDownloaded();
		total_left				= delegate.getTotalLeft();
		completed_count			= delegate.getCompletedCount();
		total_bytes_in			= delegate.getTotalBytesIn();
		average_bytes_in		= delegate.getAverageBytesIn();
		total_bytes_out			= delegate.getTotalBytesOut();
		average_bytes_out		= delegate.getAverageBytesOut();
		scrape_count			= delegate.getScrapeCount();
		average_scrape_count	= delegate.getAverageScrapeCount();
		announce_count			= delegate.getAnnounceCount();
		average_announce_count	= delegate.getAverageAnnounceCount();
		seed_count				= delegate.getSeedCount();
		leecher_count			= delegate.getLeecherCount();
		bad_NAT_count			= delegate.getBadNATCount();
	}

	@Override
	public Object
	_setLocal()

		throws RPException
	{
		Object res = _fixupLocal();

		if ( torrent != null ){

			torrent._setLocal();
		}

		return( res );
	}

	@Override
	public void
	_setRemote(
		RPRequestDispatcher		dispatcher )
	{
		super._setRemote( dispatcher );

		if ( torrent != null ){

			torrent._setRemote( dispatcher );
		}
	}

	@Override
	public RPReply
	_process(
		RPRequest	request	)
	{
		String		method 	= request.getMethod();
		// Object[]	params	= request.getParams();

		throw( new RPException( "Unknown method: " + method ));
	}

		//***************************************************************************8

	@Override
	public void
	start()

		throws TrackerException
	{
		notSupported();
	}

	@Override
	public void
	stop()

		throws TrackerException
	{
		notSupported();
	}

	@Override
	public void
	remove()

		throws TrackerTorrentRemovalVetoException
	{
		notSupported();

	}

	@Override
	public boolean
	canBeRemoved()

		throws TrackerTorrentRemovalVetoException
	{
		notSupported();

		return( false );
	}


	@Override
	public Torrent
	getTorrent()
	{
		return( torrent );
	}

	@Override
	public TrackerPeer[]
	getPeers()
	{
		notSupported();

		return( null );
	}

	@Override
	public int
	getStatus()
	{
		return( status );
	}

	@Override
	public long
	getTotalUploaded()
	{
		return( total_uploaded );
	}

	@Override
	public long
	getTotalDownloaded()
	{
		return( total_downloaded );
	}

	@Override
	public long
	getAverageUploaded()
	{
		return( average_uploaded );
	}

	@Override
	public long
	getAverageDownloaded()
	{
		return( average_downloaded );
	}

	@Override
	public long
	getTotalLeft()
	{
		return( total_left );
	}

	@Override
	public long
	getCompletedCount()
	{
		return( completed_count );
	}

	@Override
	public long
	getTotalBytesIn()
	{
		return( total_bytes_in );
	}

	@Override
	public long
	getAverageBytesIn()
	{
		return( average_bytes_in );
	}

	@Override
	public long
	getTotalBytesOut()
	{
		return( total_bytes_out );
	}

	@Override
	public long
	getAverageBytesOut()
	{
		return( average_bytes_out );
	}

	@Override
	public long
	getScrapeCount()
	{
		return( scrape_count );
	}

	@Override
	public long
	getAverageScrapeCount()
	{
		return( average_scrape_count );
	}

	@Override
	public long
	getAnnounceCount()
	{
		return( announce_count );
	}

	@Override
	public long
	getAverageAnnounceCount()
	{
		return( average_announce_count );
	}

	@Override
	public int
	getSeedCount()
	{
		return( seed_count );
	}

	@Override
	public int
	getLeecherCount()
	{
		return( leecher_count);
	}

	@Override
	public int
	getBadNATCount()
	{
		return( bad_NAT_count );
	}

	@Override
	public void
	disableReplyCaching()
	{
		notSupported();
	}

	@Override
	public boolean
	isPassive()
	{
		notSupported();

		return( false );
	}

	@Override
	public boolean
	isExternal()
	{
		notSupported();

		return( false );
	}
	
	@Override
	public long
	getDateAdded()
	{
		notSupported();

		return( 0 );
	}

	@Override
	public void
	addListener(
		TrackerTorrentListener	listener )
	{
		notSupported();
	}

	@Override
	public void
	removeListener(
		TrackerTorrentListener	listener )
	{
		notSupported();
	}

	@Override
	public void
	addRemovalListener(
		TrackerTorrentWillBeRemovedListener	listener )
	{
		notSupported();
	}


	@Override
	public void
	removeRemovalListener(
		TrackerTorrentWillBeRemovedListener	listener )
	{
		notSupported();
	}
}
