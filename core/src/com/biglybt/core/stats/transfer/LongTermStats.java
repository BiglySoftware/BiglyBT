/*
 * Created on Feb 1, 2013
 * Created by Paul Gardner
 *
 * Copyright 2013 Azureus Software, Inc.  All rights reserved.
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


package com.biglybt.core.stats.transfer;

import java.util.Date;

public interface
LongTermStats
{
	public static final int ST_PROTOCOL_UPLOAD		= 0;
	public static final int ST_DATA_UPLOAD			= 1;
	public static final int ST_PROTOCOL_DOWNLOAD	= 2;
	public static final int ST_DATA_DOWNLOAD		= 3;
	public static final int ST_DHT_UPLOAD			= 4;
	public static final int ST_DHT_DOWNLOAD			= 5;

	public static final int PT_CURRENT_HOUR			= 0;
	public static final int PT_CURRENT_DAY			= 1;
	public static final int PT_CURRENT_WEEK			= 2;	// sun is start of week
	public static final int PT_CURRENT_MONTH		= 3;

	public static final int PT_SLIDING_HOUR			= 10;
	public static final int PT_SLIDING_DAY			= 11;
	public static final int PT_SLIDING_WEEK			= 12;

	public static final String[] PT_NAMES =
		{ "hour", "day", "week", "month", "", "", "", "", "", "",
		  "sliding hour", "sliding day", "sliding week"
		};

	public boolean
	isEnabled();

	public long[]
	getCurrentRateBytesPerSecond();

	public long
	getOverallStartTime();
	
	public long[]
	getTotalUsageInPeriod(
		Date		start_date,
		Date		end_date );

	public long[]
	getTotalUsageInPeriod(
		Date			start_date,
		Date			end_date,
		RecordAccepter	accepter );

	public long[]
	getTotalUsageInPeriod(
		int			period_type,
		double		multiplier );

	public long[]
	getTotalUsageInPeriod(
		int					period_type,
		double				multiplier,
		RecordAccepter		accepter );

	public void
	addListener(
		long						min_delta_bytes,
		LongTermStatsListener		listener );

	public void
	removeListener(
		LongTermStatsListener		listener );

	public void
	reset();

	public interface
	RecordAccepter
	{
		public boolean
		acceptRecord(
			long		timestamp );
	}

	public interface
	GenericStatsSource
	{
		public int
		getEntryCount();

		public long[]
		getStats(
			String		id );
	}
}
