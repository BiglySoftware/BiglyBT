/*
 * File    : RPDownloadAnnounceResult.java
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

import java.net.URL;
import java.util.Map;

import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadAnnounceResult;
import com.biglybt.pif.download.DownloadAnnounceResultPeer;
import com.biglybt.pifimpl.remote.RPException;
import com.biglybt.pifimpl.remote.RPObject;
import com.biglybt.pifimpl.remote.RPReply;
import com.biglybt.pifimpl.remote.RPRequest;


public class
RPDownloadAnnounceResult
	extends		RPObject
	implements 	DownloadAnnounceResult
{
	protected transient DownloadAnnounceResult		delegate;

		// don't change these field names as they are visible on XML serialisation

	public int				seed_count;
	public int				non_seed_count;

	public static RPDownloadAnnounceResult
	create(
		DownloadAnnounceResult		_delegate )
	{
		RPDownloadAnnounceResult	res =(RPDownloadAnnounceResult)_lookupLocal( _delegate );

		if ( res == null ){

			res = new RPDownloadAnnounceResult( _delegate );
		}

		return( res );
	}

	protected
	RPDownloadAnnounceResult(
		DownloadAnnounceResult		_delegate )
	{
		super( _delegate );
	}

	@Override
	protected void
	_setDelegate(
		Object		_delegate )
	{
		delegate = (DownloadAnnounceResult)_delegate;

		seed_count		= delegate.getSeedCount();
		non_seed_count	= delegate.getNonSeedCount();
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
	public Download
	getDownload()
	{
		notSupported();

		return( null );
	}

	@Override
	public int
	getResponseType()
	{
		notSupported();

		return( 0 );
	}

	@Override
	public int
	getReportedPeerCount()
	{
		notSupported();

		return( 0 );
	}

	@Override
	public int
	getSeedCount()
	{
		return( seed_count );
	}

	@Override
	public int
	getNonSeedCount()
	{
		return( non_seed_count );
	}

	@Override
	public String
	getError()
	{
		notSupported();

		return( null );
	}

	@Override
	public URL
	getURL()
	{
		notSupported();

		return( null );
	}

	@Override
	public DownloadAnnounceResultPeer[]
	getPeers()
	{
		notSupported();

		return( null );
	}

	@Override
	public long
	getTimeToWait()
	{
		notSupported();

		return( 0 );
	}

	@Override
	public Map
	getExtensions()
	{
		notSupported();

		return( null );
	}
}