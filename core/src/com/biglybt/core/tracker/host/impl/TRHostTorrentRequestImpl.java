/*
 * File    : TRHostTorrentRequestImpl.java
 * Created : 13-Dec-2003
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

package com.biglybt.core.tracker.host.impl;

/**
 * @author parg
 *
 */

import java.util.Map;

import com.biglybt.core.tracker.host.TRHostPeer;
import com.biglybt.core.tracker.host.TRHostTorrent;
import com.biglybt.core.tracker.host.TRHostTorrentRequest;
import com.biglybt.core.tracker.server.TRTrackerServerRequest;

public class
TRHostTorrentRequestImpl
	implements TRHostTorrentRequest
{
	protected final TRHostTorrent				torrent;
	protected final TRHostPeer				peer;
	protected final TRTrackerServerRequest	request;

	protected
	TRHostTorrentRequestImpl(
		TRHostTorrent			_torrent,
		TRHostPeer				_peer,
		TRTrackerServerRequest	_request )
	{
		torrent		= _torrent;
		peer		= _peer;
		request		= _request;
	}

	@Override
	public TRHostPeer
	getPeer()
	{
		return( peer );
	}

	@Override
	public TRHostTorrent
	getTorrent()
	{
		return( torrent );
	}

	@Override
	public int
	getRequestType()
	{
		if ( request.getType() == TRTrackerServerRequest.RT_ANNOUNCE ){

			return( RT_ANNOUNCE );

		}else if ( request.getType() == TRTrackerServerRequest.RT_SCRAPE ){

			return( RT_SCRAPE );

		}else{

			return( RT_FULL_SCRAPE );
		}
	}

	@Override
	public String
	getRequest()
	{
		return( request.getRequest());
	}

	@Override
	public Map
	getResponse()
	{
		return( request.getResponse());
	}
}
