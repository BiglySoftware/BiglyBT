/*
 * Created on Mar 20, 2013
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


package com.biglybt.core.tag;

public interface
TagFeatureRateLimit
	extends TagFeature
{
	public static final int SR_ACTION_QUEUE					= 0;
	public static final int SR_ACTION_PAUSE					= 1;
	public static final int SR_ACTION_STOP					= 2;
	public static final int SR_ACTION_ARCHIVE				= 3;
	public static final int SR_ACTION_REMOVE_FROM_LIBRARY	= 4;
	public static final int SR_ACTION_REMOVE_FROM_COMPUTER	= 5;

	public static final int SR_INDIVIDUAL_ACTION_DEFAULT = SR_ACTION_QUEUE;
	public static final int SR_AGGREGATE_ACTION_DEFAULT	 = SR_ACTION_PAUSE;

	public static final boolean AT_RATELIMIT_MAX_AGGREGATE_SR_PRIORITY_DEFAULT	= true;

	public boolean
	supportsTagRates();

	public boolean
	supportsTagUploadLimit();

	public boolean
	supportsTagDownloadLimit();

	/**
	 * @return Max Upload Speed in bytes/sec
	 */
	public int
	getTagUploadLimit();

	public void
	setTagUploadLimit(
		int		bps );

	public int
	getTagCurrentUploadRate();

	/**
	 * @return Max Download Speed in bytes/sec
	 */
	public int
	getTagDownloadLimit();

	public void
	setTagDownloadLimit(
		int		bps );

	public int
	getTagCurrentDownloadRate();

	public long[]
	getTagSessionUploadTotal();

	public void
	resetTagSessionUploadTotal();

	public long[]
	getTagSessionDownloadTotal();

	public void
	resetTagSessionDownloadTotal();

	public long[]
	getTagUploadTotal();

	public long[]
	getTagDownloadTotal();

	public void
	setRecentHistoryRetention(
		boolean	enable );

	public int[][]
	getRecentHistory();

	public int
	getTagUploadPriority();

	public void
	setTagUploadPriority(
		int		priority );
	
	public boolean
	getTagBoost();

	public void
	setTagBoost(
		boolean		boost );

		// min share ratio

	public int
	getTagMinShareRatio();

	public void
	setTagMinShareRatio(
		int		ratio_in_thousandths );

		// max share ratio

	public int
	getTagMaxShareRatio();

	public void
	setTagMaxShareRatio(
		int		ratio_in_thousandths );

	public int
	getTagMaxShareRatioAction();

	public void
	setTagMaxShareRatioAction(
		int		action );

		// aggregate share ratio

	public int
	getTagAggregateShareRatio();

	public int
	getTagMaxAggregateShareRatio();

	public void
	setTagMaxAggregateShareRatio(
		int		ratio_in_thousandths );

	public int
	getTagMaxAggregateShareRatioAction();

	public void
	setTagMaxAggregateShareRatioAction(
		int		action );

	public boolean
	getTagMaxAggregateShareRatioHasPriority();

	public void
	setTagMaxAggregateShareRatioHasPriority(
		boolean	has_priority );
	
	public boolean
	getFirstPrioritySeeding();

	public void
	setFirstPrioritySeeding(
		boolean	has_priority );
	
	public int
	getMaxActiveDownloads();
	
	public void
	setMaxActiveDownloads(
		int		max );
	
	public int
	getMaxActiveSeeds();
	
	public void
	setMaxActiveSeeds(
		int		max );
}
