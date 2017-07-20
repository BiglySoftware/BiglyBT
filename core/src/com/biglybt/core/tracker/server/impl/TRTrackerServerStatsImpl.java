/*
 * File    : TRTrackerServerStatsImpl.java
 * Created : 09-Feb-2004
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

import com.biglybt.core.tracker.server.TRTrackerServerRequest;
import com.biglybt.core.tracker.server.TRTrackerServerStats;

public class
TRTrackerServerStatsImpl
	implements TRTrackerServerStats
{
	private final TRTrackerServerImpl	server;

	private long		announces;
	private long		scrapes;

	private long		bytes_in;
	private long		bytes_out;

	private long		announce_time;
	private long		scrape_time;

	protected
	TRTrackerServerStatsImpl(
		TRTrackerServerImpl	_server )
	{
		server	= _server;
	}

	@Override
	public int
	getTorrentCount()
	{
		return( server.getTorrentCount());
	}

	@Override
	public long
	getBytesIn()
	{
		return( bytes_in );
	}

	@Override
	public long
	getBytesOut()
	{
		return( bytes_out );
	}

	protected void
	update(
		int		request_type,
		int		in,
		int		out )
	{
		bytes_in		+= in;
		bytes_out		+= out;

		if ( request_type == TRTrackerServerRequest.RT_ANNOUNCE || request_type == TRTrackerServerRequest.RT_QUERY ){

			announces++;
		}else{

			scrapes++;
		}
	}

	protected void
	updateTime(
		int		request_type,
		long	time )
	{
		if ( request_type == TRTrackerServerRequest.RT_ANNOUNCE ){

			announce_time += time;

		}else{

			scrape_time += time;
		}
	}

	@Override
	public long
	getAnnounceCount()
	{
		return( announces );
	}

	@Override
	public long
	getScrapeCount()
	{
		return( scrapes );
	}

	@Override
	public long
	getAnnounceTime()
	{
		return( announce_time );
	}

	@Override
	public long
	getScrapeTime()
	{
		return( scrape_time );
	}
}
