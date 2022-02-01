/*
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

package com.biglybt.plugin.startstoprules.defaultplugin;

import com.biglybt.core.download.DownloadManagerStateAttributeListener;
import com.biglybt.core.tag.TagFeatureRateLimit;
import com.biglybt.pif.download.DownloadException;
import com.biglybt.pif.download.DownloadScrapeResult;

public interface 
DefaultRankCalculator 
	extends Comparable<DefaultRankCalculator> 
{
	/** All of the First Priority rules must match */
	public static final int FIRSTPRIORITY_ALL = 0;

	/** Any of the First Priority rules must match */
	public static final int FIRSTPRIORITY_ANY = 1;

	public static final int	DOWNLOAD_ORDER_INDEX				= 0;
	public static final int	DOWNLOAD_ORDER_SEED_COUNT			= 1;
	public static final int	DOWNLOAD_ORDER_SPEED				= 2;
	public static final int	DOWNLOAD_ORDER_REVERSE_SEED_COUNT	= 3;
	public static final int	DOWNLOAD_ORDER_SIZE					= 4;
	public static final int	DOWNLOAD_ORDER_REVERSE_SIZE			= 5;
	public static final int	DOWNLOAD_ORDER_ETA					= 6;

	//
	// Seeding Rank (SR) Limits and Values

	/** Rank that complete starts at (and incomplete ends at + 1) */
	public static final int SR_COMPLETE_STARTS_AT = 1000000000; // billion

	/** Maximimum ranking for time queue mode. 1 unit is a second */
	public static final int SR_TIMED_QUEUED_ENDS_AT =199999999;

	/** Ranks below this value are for torrents to be ignored (moved to bottom & queued) */
	public static final int SR_IGNORED_LESS_THAN = -1;

	/** Seeding Rank value when download is marked as not queued */
	public static final int SR_NOTQUEUED = -2;

	/** Seeding Rank value when download is marked as S:P Ratio Met for FP */
	public static final int SR_FP_SPRATIOMET = -3;

	/** Seeding Rank value when download is marked as P:1S Ratio Met */
	public static final int SR_RATIOMET = -4;

	/** Seeding Rank value when download is marked as # Seeds Met */
	public static final int SR_NUMSEEDSMET = -5;

	/** Seeding Rank value when download is marked as 0 Peers and FP */
	public static final int SR_FP0PEERS = -6;

	/** Seeding Rank value when download is marked as 0 Peers */
	public static final int SR_0PEERS = -7;

	/** Seeding Rank value when download is marked as Share Ratio Met */
	public static final int SR_SHARERATIOMET = -8;

	public static final int SR_NOSCRAPE = -9;

	public static final String[] SR_NEGATIVE_DEBUG = {
		"?",
		"Not Qd",
		"FP SPRatioMet",
		"Ratio Met",
		"# CDs Met",
		"FP 0 Peers",
		"0 Peers",
		"Share Ratio Met",
		"No Scrape"
	};
	
	
	
	public int
	getState();
	
	public int
	getCoreState();
		
	public String
	getName();
		
	public boolean
	supportsPosition();

	public int
	getPosition();
	
	public void
	setPosition(
		int		pos );
	
	public void
	moveTo(
		int	pos );
	
	public boolean 
	isForceActive();

	public boolean
	isQueued();

	public boolean
	isDownloading();
	
	public boolean
	isChecking();
	
	public boolean
	isMoving();
	
	public boolean
	isForceStart();
	
	public boolean
	isComplete();
	
	public boolean
	isControllable();
	
	public void
	initialize()
	
		throws DownloadException;

	public void
	start()
	
		throws DownloadException;
	
	public void
	restart()
	
		throws DownloadException;

	public void
	stopAndQueue()
		
		throws DownloadException;
	
	public boolean
	isFirstPriority();
	
	public boolean
	getCachedIsFP();
	
	public int
	getSeedingRank();
	
	public boolean
	getActivelySeeding();
	
	public boolean
	getActivelyDownloading();
	
	public int
	getShareRatio();
	
	public long
	getUploadAverage();
	
	public long
	getDownloadAverage();
	
	public long
	getTimeStarted();
	
	public long
	getSizeExcludingDND();
	
	public DownloadScrapeResult
	getAggregatedScrapeResult(
		boolean 		b );
	
	public boolean 
	scrapeResultOk();

	public int
	calcSeedsNoUs();
	
	public boolean
	changeChecker();
	
	public void
	recalcSeedingRank();
	
	//public void
	//resetSeedingRank();
	
		/*
		 * Long.MAX_VALUE = not eligible
		 * otherwise time elapsed since eligible
		 */
	
	public long
	getLightSeedEligibility();
	
	public boolean
	updateLightSeedEligibility(
		boolean	b );
	
	public RankCalculatorSlotReserver
	getReservedSlot();
	
	public void
	setReservedSlot(
		RankCalculatorSlotReserver	slot );
	
	public boolean
	activationRequest();
	
	public TagFeatureRateLimit[]
	getTagsWithDLLimits();
	
	public TagFeatureRateLimit[]
	getTagsWithCDLimits();
	
	public boolean
	getLastScrapeResultOk();
	
	public void
	scrapeReceived(
		DownloadScrapeResult		result );
	
	public int
	getLastModifiedScrapeResultPeers();
	
	public int
	getLastModifiedScrapeResultSeeds();
	
		// DLR stuff
	
	public void
	setDLRInactive();
	
	public void
	setDLRActive(
		long	time );
	
	public void
	setDLRComplete(
		long	time );
	
	public long
	getDLRLastTestTime();
	
	public int
	getDLRLastTestSpeed();
	
	public long
	getDLRLastTestETA();
	
	public String
	getDLRTrace();
	
	public void
	addStateAttributeListener(
		DownloadManagerStateAttributeListener l, String attribute, int event_type );
	
	public void
	removeStateAttributeListener(
		DownloadManagerStateAttributeListener l, String attribute, int event_type );

	public String
	getExplainFP();
	
	public String
	getExplainSR();
	
	public void
	resetTrace();
	
	public void
	appendTrace(
		String	str );
	
	public String
	getTrace();
	
	public Object
	getRelatedTo();

	public int
	compareToIgnoreStopped(
		DefaultRankCalculator	other );
	
	public Object
	getUserData(
		Object		key );
	
	public void
	setUserData(
		Object		key, 
		Object		value );
	public void
	destroy();	
}
