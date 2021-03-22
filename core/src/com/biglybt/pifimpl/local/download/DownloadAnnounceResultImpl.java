/*
 * File    : DownloadAnnounceResultImpl.java
 * Created : 12-Jan-2004
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

import java.net.URL;
import java.util.Map;

import com.biglybt.core.tracker.client.TRTrackerAnnouncerResponse;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadAnnounceResult;
import com.biglybt.pif.download.DownloadAnnounceResultPeer;
import com.biglybt.pif.peers.PeerManager;

public class
DownloadAnnounceResultImpl
	implements DownloadAnnounceResult
{
	protected Download							download;
	protected TRTrackerAnnouncerResponse		response;

		// public for psueudoDownload usage

	public
	DownloadAnnounceResultImpl(
		Download					_download,
		TRTrackerAnnouncerResponse	_response )
	{
		download	= _download;
		response	= _response;
	}

		// public for psueudoDownload usage

	public void
	setContent(
		TRTrackerAnnouncerResponse	_response )
	{
		response = _response;
	}

	@Override
	public Download
	getDownload()
	{
		return( download );
	}

	@Override
	public int
	getResponseType()
	{
		if ( response == null ){

			return( RT_ERROR );
		}

		int status = response.getStatus();

		if ( status == TRTrackerAnnouncerResponse.ST_ONLINE ){

			return( RT_SUCCESS );
		}else{

			return( RT_ERROR );
		}
	}

	@Override
	public int
	getReportedPeerCount()
	{
		return( response==null||response.getPeers()==null?0:response.getPeers().length );
	}

	@Override
	public int
	getSeedCount()
	{
		PeerManager	pm = download.getPeerManager();

		if ( pm != null ){

			return( pm.getStats().getConnectedSeeds());
		}

		return( 0 );
	}

	@Override
	public int
	getNonSeedCount()
	{
		PeerManager	pm = download.getPeerManager();

		if ( pm != null ){

			return( pm.getStats().getConnectedLeechers());
		}

		return( 0 );
	}

	@Override
	public String
	getError()
	{
		return( response==null?"No Response":response.getAdditionalInfo());
	}

	@Override
	public String 
	getAdditionalInfo()
	{
		return( response==null?null:response.getAdditionalInfo());
	}
	
	@Override
	public URL
	getURL()
	{
		return( response==null?null:response.getURL());
	}

	@Override
	public DownloadAnnounceResultPeer[]
	getPeers()
	{
		if ( response == null ){

			return( new DownloadAnnounceResultPeer[0]);
		}

		return( response.getPeers());
	}

	@Override
	public long
	getTimeToWait()
	{
		if ( response == null ){

			return( -1 );
		}

		return( response.getTimeToWait());
	}

	@Override
	public Map
	getExtensions()
	{
		if ( response == null ){

			return( null );
		}

		return( response.getExtensions());
	}
}
