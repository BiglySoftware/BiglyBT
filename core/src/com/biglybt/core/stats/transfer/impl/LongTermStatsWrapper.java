/*
 * Created on Sep 18, 2015
 * Created by Paul Gardner
 *
 * Copyright 2015 Azureus Software, Inc.  All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.biglybt.core.stats.transfer.impl;

import java.util.Date;
import java.util.IdentityHashMap;
import java.util.Map;

import com.biglybt.core.Core;
import com.biglybt.core.global.GlobalManagerStats;
import com.biglybt.core.stats.transfer.LongTermStats;
import com.biglybt.core.stats.transfer.LongTermStatsListener;

public class
LongTermStatsWrapper
	implements LongTermStats
{
	private Core core;
	private GlobalManagerStats	gm_stats;

	private String									id;
	private LongTermStats.GenericStatsSource		source;

	private LongTermStatsWrapperHelper	delegate;

	private final Map<LongTermStatsListener,Long>	listeners = new IdentityHashMap<>();

	public
	LongTermStatsWrapper(
		Core _core,
		GlobalManagerStats	_stats )
	{
		core		= _core;
		gm_stats	= _stats;

		delegate = new LongTermStatsImpl( core, gm_stats );
	}

	public
	LongTermStatsWrapper(
		String									_id,
		LongTermStats.GenericStatsSource		_source )
	{
		id		= _id;
		source	= _source;

		delegate = new LongTermStatsGenericImpl( id, source );
	}

	@Override
	public synchronized boolean
	isEnabled()
	{
		return( delegate.isEnabled());
	}

	@Override
	public synchronized long[]
	getCurrentRateBytesPerSecond()
	{
		return( delegate.getCurrentRateBytesPerSecond());
	}

	@Override
	public long 
	getOverallStartTime()
	{
		return( delegate.getOverallStartTime());
	}
	
	@Override
	public synchronized long[]
	getTotalUsageInPeriod(
		Date		start_date,
		Date		end_date )
	{
		return( delegate.getTotalUsageInPeriod(start_date, end_date));
	}

	@Override
	public synchronized long[]
	getTotalUsageInPeriod(
		Date			start_date,
		Date			end_date,
		RecordAccepter	accepter )
	{
		return( delegate.getTotalUsageInPeriod(start_date, end_date, accepter ));
	}
	
	@Override
	public synchronized long[]
	getTotalUsageInPeriod(
		int		period_type,
		double	multiplier )
	{
		return( delegate.getTotalUsageInPeriod(period_type,multiplier));
	}

	@Override
	public synchronized long[]
	getTotalUsageInPeriod(
		int					period_type,
		double				multiplier,
		RecordAccepter		accepter )
	{
		return( delegate.getTotalUsageInPeriod(period_type, multiplier, accepter));
	}

	@Override
	public synchronized void
	addListener(
		long						min_delta_bytes,
		LongTermStatsListener		listener )
	{
		listeners.put( listener, min_delta_bytes );

		delegate.addListener(min_delta_bytes, listener);
	}

	@Override
	public synchronized void
	removeListener(
		LongTermStatsListener		listener )
	{
		listeners.remove( listener );

		delegate.removeListener(listener);
	}

	@Override
	public synchronized void
	reset()
	{
		delegate.destroyAndDeleteData();

		if ( core != null ){

			delegate = new LongTermStatsImpl( core, gm_stats );

		}else{

			delegate = new LongTermStatsGenericImpl( id, source );
		}

		for ( Map.Entry<LongTermStatsListener,Long> entry: listeners.entrySet()){

			delegate.addListener(entry.getValue(), entry.getKey());
		}
	}

	public interface
	LongTermStatsWrapperHelper
		extends LongTermStats
	{
		public void
		destroyAndDeleteData();
	}
}
