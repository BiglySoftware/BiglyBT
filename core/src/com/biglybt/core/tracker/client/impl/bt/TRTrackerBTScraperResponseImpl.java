/*
 * Created on 14-Feb-2005
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

package com.biglybt.core.tracker.client.impl.bt;

import java.net.URL;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.tracker.client.TRTrackerScraperResponse;
import com.biglybt.core.tracker.client.impl.TRTrackerScraperResponseImpl;
import com.biglybt.core.util.HashWrapper;

/**
 * @author parg
 *
 */

public class
TRTrackerBTScraperResponseImpl
	extends TRTrackerScraperResponseImpl
{
	private final TrackerStatus ts;

	private boolean	is_dht_backup;

	protected
	TRTrackerBTScraperResponseImpl(
		TrackerStatus _ts,
		HashWrapper _hash)
	{
		this(_ts, _hash, -1, -1, -1,-1);
	}

	protected
	TRTrackerBTScraperResponseImpl(
		TrackerStatus _ts,
		HashWrapper _hash,
		int  _seeds,
		int  _peers,
		int completed,
		long _scrapeStartTime)
	{
		super( _hash, _seeds, _peers, completed, _scrapeStartTime );

		ts	= _ts;
	}

	public TrackerStatus
	getTrackerStatus()
	{
		return ts;
	}

	@Override
	public void
	setSeedsPeers(
		int iSeeds, int iPeers )
	{
		setSeeds( iSeeds );
		setPeers( iPeers );

		if (isValid()){
			setStatus(TRTrackerScraperResponse.ST_ONLINE);
			setStatus( MessageText.getString("Scrape.status.ok"));
		} else {
			setStatus(TRTrackerScraperResponse.ST_INITIALIZING);
		}
		// XXX Is this a good idea?
		ts.scrapeReceived(this);
	}

	@Override
	public URL
	getURL()
	{
		return( ts.getTrackerURL());
	}

	@Override
	public void
	setDHTBackup(
		boolean	is_backup )
	{
		is_dht_backup	= is_backup;
	}

	@Override
	public boolean
	isDHTBackup()
	{
		return is_dht_backup;
	}
}
