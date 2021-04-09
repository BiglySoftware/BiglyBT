/*
 * Created on Dec 9, 2009
 * Created by Paul Gardner
 *
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
 */


package com.biglybt.core.tracker;

import java.net.URL;

import com.biglybt.core.util.Debug;

public abstract class
TrackerPeerSourceAdapter
	implements TrackerPeerSource
{
	@Override
	public int
	getType()
	{
		return( TP_UNKNOWN );
	}

	@Override
	public String
	getName()
	{
		return( "" );
	}

	@Override
	public String 
	getDetails()
	{
		return( null );
	}
	
	@Override
	public URL 
	getURL()
	{
		return( null );
	}
	
	@Override
	public int
	getStatus()
	{
		return( ST_UNKNOWN );
	}

	@Override
	public String
	getStatusString()
	{
		return( null );
	}

	@Override
	public int
	getSeedCount()
	{
		return( -1 );
	}

	@Override
	public int
	getLeecherCount()
	{
		return( -1 );
	}

	@Override
	public int
	getPeers()
	{
		return( -1 );
	}

	@Override
	public int
	getCompletedCount()
	{
		return( -1 );
	}

	@Override
	public int
	getLastUpdate()
	{
		return( 0 );
	}

	@Override
	public int
	getSecondsToUpdate()
	{
		return( Integer.MIN_VALUE );
	}

	@Override
	public int
	getInterval()
	{
		return( -1 );
	}

	@Override
	public int
	getMinInterval()
	{
		return( -1 );
	}

	@Override
	public boolean
	isUpdating()
	{
		return( false );
	}

	@Override
	public boolean
	canManuallyUpdate()
	{
		return( false );
	}

	@Override
	public void
	manualUpdate()
	{
		Debug.out( "derp" );
	}

	@Override
	public long[]
	getReportedStats()
	{
		return( null );
	}
	
	@Override
	public boolean
	canDelete()
	{
		return( false );
	}

	@Override
	public void
	delete()
	{
		Debug.out( "derp" );
	}
}
