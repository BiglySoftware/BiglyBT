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

package com.biglybt.core.tracker.client.impl.dht;

import java.net.URL;

import com.biglybt.core.tracker.client.impl.TRTrackerScraperResponseImpl;
import com.biglybt.core.util.HashWrapper;

/**
 * @author parg
 *
 */

public class
TRTrackerDHTScraperResponseImpl
	extends TRTrackerScraperResponseImpl
{
	private final URL		url;

	protected
	TRTrackerDHTScraperResponseImpl(
		HashWrapper		hash,
		URL				_url )
	{
		super(hash );

		url		= _url;
	}

	@Override
	public void
	setSeedsPeers(
		int	s,
		int	p )
	{
		setSeeds( s );
		setPeers( p );
	}

	@Override
	public URL
	getURL()
	{
		return( url );
	}

	@Override
	public void
	setDHTBackup(
		boolean	is_backup )
	{
		// we're never a backup
	}

	@Override
	public boolean
	isDHTBackup()
	{
		return false;
	}
}
