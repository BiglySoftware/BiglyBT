/*
 * File    : TRTrackerServerRequestImpl.java
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

package com.biglybt.core.tracker.server.impl;

/**
 * @author parg
 *
 */

import java.util.Map;

import com.biglybt.core.tracker.server.TRTrackerServerPeer;
import com.biglybt.core.tracker.server.TRTrackerServerRequest;
import com.biglybt.core.tracker.server.TRTrackerServerTorrent;

public class
TRTrackerServerRequestImpl
	implements TRTrackerServerRequest
{
	protected final TRTrackerServerImpl			server;
	protected final TRTrackerServerPeer			peer;
	protected final TRTrackerServerTorrent		torrent;
	protected final int							type;
	protected final String						request;
	protected final Map							response;

	public
	TRTrackerServerRequestImpl(
		TRTrackerServerImpl				_server,
		TRTrackerServerPeer				_peer,
		TRTrackerServerTorrent			_torrent,
		int								_type,
		String							_request,
		Map								_response )
	{
		server		= _server;
		peer		= _peer;
		torrent		= _torrent;
		type		= _type;
		request		= _request;
		response	= _response;
	}

	@Override
	public int
	getType()
	{
		return( type );
	}

	@Override
	public TRTrackerServerPeer
	getPeer()
	{
		return( peer );
	}

	@Override
	public TRTrackerServerTorrent
	getTorrent()
	{
		return( torrent );
	}

	@Override
	public String
	getRequest()
	{
		return( request );
	}

	@Override
	public Map
	getResponse()
	{
		return( response );
	}
}
