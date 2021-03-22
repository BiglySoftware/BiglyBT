/*
 * File    : TRTrackerResponseImpl.java
 * Created : 5 Oct. 2003
 * By      : Parg
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

package com.biglybt.core.tracker.client.impl;

import java.net.URL;
import java.util.Map;

import com.biglybt.core.tracker.client.TRTrackerAnnouncerRequest;
import com.biglybt.core.tracker.client.TRTrackerAnnouncerResponse;
import com.biglybt.core.tracker.client.TRTrackerAnnouncerResponsePeer;
import com.biglybt.core.util.HashWrapper;

public class
TRTrackerAnnouncerResponseImpl
	implements TRTrackerAnnouncerResponse
{
	private final URL				url;
	private final HashWrapper		hash;
	private final int				status;
	private final long				time_to_wait;
	private String					additional_info;

	private boolean			was_udp_probe		= false;
	private int				scrape_complete		= -1;
	private int				scrape_incomplete	= -1;
	private int				scrape_downloaded	= -1;

	protected TRTrackerAnnouncerResponsePeer[]	peers;

	protected Map						extensions;

	private TRTrackerAnnouncerRequest		request;
	
	public
	TRTrackerAnnouncerResponseImpl(
		URL			_url,
		HashWrapper	_hash,
		int			_status,
		long		_time_to_wait  )
	{
		url				= _url;
		hash			= _hash;
		status			= _status;
		time_to_wait	= _time_to_wait;
	}

	public
	TRTrackerAnnouncerResponseImpl(
		URL			_url,
		HashWrapper	_hash,
		int			_status,
		long		_time_to_wait,
		String		_additional_info )
	{
		url				= _url;
		hash			= _hash;
		status			= _status;
		time_to_wait	= _time_to_wait;
		additional_info	= _additional_info;
	}

	public
	TRTrackerAnnouncerResponseImpl(
		URL									_url,
		HashWrapper							_hash,
		int									_status,
		long								_time_to_wait,
		TRTrackerAnnouncerResponsePeer[]	_peers )
	{
		url				= _url;
		hash			= _hash;
		status			= _status;
		time_to_wait	= _time_to_wait;
		peers			= _peers;
	}

	public void
	setRequest(
		TRTrackerAnnouncerRequest	_request )
	{
		request = _request;
	}
	
	public TRTrackerAnnouncerRequest
	getRequest()
	{
		return( request );
	}
	
	@Override
	public HashWrapper
	getHash()
	{
		return( hash );
	}

	@Override
	public int
	getStatus()
	{
		return( status );
	}

	@Override
	public String
	getStatusString()
	{
		String	str = "";

		if ( status == ST_OFFLINE ){

			str = "Offline";

		}else if  (status == ST_ONLINE ){

			str = "OK";

			if ( was_udp_probe ){

				str += " (UDP Probe)";
			}
		}else{

			str = "Failed";
		}

		if ( additional_info != null && additional_info.length() > 0 ){

				// we need to use () here as code in StatusItem expects this...
			
			str += " (" + additional_info + ")";
		}

		return( str );
	}

	public void
	setAdditionalInfo(
		String info )
	{
		additional_info = info;
	}

	public void
	setWasProbe()
	{
		was_udp_probe = true;
	}

	public boolean
	wasProbe()
	{
		return( was_udp_probe );
	}

	@Override
	public long
	getTimeToWait()
	{
		return( time_to_wait );
	}

	@Override
	public String
	getAdditionalInfo()
	{
		return( additional_info );
	}

	@Override
	public void
	setPeers(
		TRTrackerAnnouncerResponsePeer[]		_peers )
	{
		peers	= _peers;
	}

	@Override
	public TRTrackerAnnouncerResponsePeer[]
	getPeers()
	{
		return( peers );
	}

	public void
	setExtensions(
		Map		_extensions )
	{
		extensions = _extensions;
	}

	@Override
	public Map
	getExtensions()
	{
		return( extensions );
	}

	@Override
	public URL
	getURL()
	{
		return( url );
	}

	@Override
	public int
	getScrapeCompleteCount()
	{
		return( scrape_complete );
	}

	@Override
	public int
	getScrapeIncompleteCount()
	{
		return( scrape_incomplete );
	}

	@Override
	public int
	getScrapeDownloadedCount()
	{
		return( scrape_downloaded );
	}

	public void
	setScrapeResult(
		int		_complete,
		int		_incomplete,
		int		_downloaded )
	{
		scrape_complete		= _complete;
		scrape_incomplete	= _incomplete;

		if ( _downloaded >= 0 ){

			scrape_downloaded = _downloaded;
		}
	}

	@Override
	public void
	print()
	{
		System.out.println( "TRTrackerResponse::print");
		System.out.println( "\tstatus = " + getStatus() + ", probe = " + was_udp_probe );
		System.out.println( "\tfail msg = " + getAdditionalInfo());
		System.out.println( "\tpeers:" );

		if ( peers != null ){

			for (int i=0;i<peers.length;i++){

				TRTrackerAnnouncerResponsePeer	peer = peers[i];

				System.out.println( "\t\t" + peer.getAddress() + ":" + peer.getPort());
			}
		}
	}

	public String
	getString()
	{
		String	str = "url=" + url + ", status=" + getStatus() + ", probe=" + was_udp_probe;

		String info = getAdditionalInfo();
		
		if ( getStatus() != ST_ONLINE ){

			str +=", error=" + info;
			
		}else if ( info != null && !info.isEmpty()){
			
			str +=", info=" + info;
		}

		str += ", time_to_wait=" + time_to_wait;

		str += ", scrape_comp=" + scrape_complete + ", scrape_incomp=" + scrape_incomplete;

		return( str );
	}
}
