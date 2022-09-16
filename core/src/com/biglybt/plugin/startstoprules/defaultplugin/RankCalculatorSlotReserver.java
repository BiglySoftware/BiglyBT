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

import java.util.concurrent.atomic.AtomicInteger;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerStateAttributeListener;
import com.biglybt.core.logging.LogRelation;
import com.biglybt.core.tag.TagFeatureRateLimit;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.LightHashMap;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadException;
import com.biglybt.pif.download.DownloadScrapeResult;

public class 
RankCalculatorSlotReserver 
	extends LogRelation
	implements DefaultRankCalculator
{
	private final static AtomicInteger	uuid_gen = new AtomicInteger( 1 );
	
	private final int uid = uuid_gen.getAndIncrement();
	
	@Override
	public int 
	compareToIgnoreStopped(
		DefaultRankCalculator obj) 
	{
		return( compareTo( obj ));
	}
	
	@Override
	public int 
	compareTo(
		DefaultRankCalculator o)
	{
		if ( o instanceof RankCalculatorSlotReserver ){
			
			RankCalculatorSlotReserver other = (RankCalculatorSlotReserver)o;
			
			return( uid - other.uid );
			
		}else{
			
			return( -1 );
		}
	}
	
	public int
	getState()
	{
		return( Download.ST_SEEDING );
	}
	
	public int
	getCoreState()
	{
		return( DownloadManager.STATE_SEEDING );
	}
		
	public String
	getName()
	{
		return( "Light-Seed: Slot " + uid );
	}
		
	public boolean
	supportsPosition()
	{
		return( false );
	}
	
	public int
	getPosition()
	{
		return( -1 );
	}
	
	public void
	setPosition(
		int		pos )
	{
		Debug.out( "no" );
	}
	
	public void
	moveTo(
		int	pos )
	{
		Debug.out( "no" );
	}
	
	@Override
	public boolean 
	isControllable()
	{
		return( false );
	}
	
	public boolean 
	isForceActive()
	{
		return( false );
	}

	public boolean
	isQueued()
	{
		return( false );
	}

	public boolean
	isDownloading()
	{
		return( false );
	}
	
	public boolean
	isChecking()
	{
		return( false );
	}
	
	public boolean
	isMoving()
	{
		return( false );
	}
	
	public boolean
	isForceStart()
	{
		return( false );
	}
	
	public boolean
	isComplete()
	{
		return( true );
	}
	
	public void
	initialize()
	
		throws DownloadException
	{
		Debug.out( "no" );
	}

	public void
	start()
	
		throws DownloadException
	{
		Debug.out( "no" );
	}
	
	public void
	restart()
	
		throws DownloadException
	{
		Debug.out( "no" );	
	}

	public void
	stopAndQueue()
		
		throws DownloadException
	{
		Debug.out( "no" );
	}
	
	public boolean
	isFirstPriority()
	{
		return( false );
	}
	
	public boolean
	getCachedIsFP()
	{
		return( false );
	}
	
	public int
	getSeedingRank()
	{
		return( SR_TIMED_QUEUED_ENDS_AT + 2 );
	}
	
	public boolean
	getActivelySeeding()
	{
		return( true );
	}
	
	public boolean
	getActivelyDownloading()
	{
		return( false );
	}
	
	public int
	getShareRatio()
	{
		return( 0 );
	}
	
	public long
	getUploadAverage()
	{
		return( 0 );
	}
	
	public long
	getDownloadAverage()
	{
		return( 0 );
	}
	
	public long
	getTimeStarted()
	{
		return( 0 );
	}
	
	public long
	getSizeExcludingDND()
	{
		return( 0 );
	}
	
	@Override
	public int[] 
	getFilePriorityStats()
	{
		return( null );
	}
	
	public DownloadScrapeResult
	getAggregatedScrapeResult(
		boolean 		b )
	{
		return( null );
	}
	
	public boolean 
	scrapeResultOk()
	{
		return( false );
	}

	public int
	calcSeedsNoUs()
	{
		return( 0 );
	}
	
	public boolean
	changeChecker()
	{
		return( false );
	}
	
	public void
	recalcSeedingRank()
	{
	}
	
	public void
	resetSeedingRank()
	{
	}
	
	public long
	getLightSeedEligibility()
	{
		return( Long.MAX_VALUE );
	}
	
	public boolean
	updateLightSeedEligibility(
		boolean	b )
	{
		return( false );
	}
	
	public TagFeatureRateLimit[]
	getTagsWithDLLimits()
	{
		return( new TagFeatureRateLimit[0] );
	}
	
	public TagFeatureRateLimit[]
	getTagsWithCDLimits()
	{
		return( new TagFeatureRateLimit[0] );
	}
	
	public RankCalculatorSlotReserver
	getReservedSlot()
	{
		return( null );
	}
	
	public void
	setReservedSlot(
		RankCalculatorSlotReserver	slot )
	{
		Debug.out( "no" );
	}
	
	@Override
	public boolean 
	activationRequest(
		Runnable to_do )
	{
		return( false );
	}
	
	public boolean
	getLastScrapeResultOk()
	{
		return( false );
	}
	
	public void
	scrapeReceived(
		DownloadScrapeResult		result )
	{
	}
		
	public int
	getLastModifiedScrapeResultPeers()
	{
		return( 0 );
	}
	
	public int
	getLastModifiedScrapeResultSeeds()
	{
		return( 0 );
	}
	
		// DLR stuff
	
	public void
	setDLRInactive()
	{
		Debug.out( "no" );
	}
	
	public void
	setDLRActive(
		long	time )
	{
		Debug.out( "no" );
	}
	
	public void
	setDLRComplete(
		long	time )
	{
		Debug.out( "no" );
	}
	
	public long
	getDLRLastTestTime()
	{
		return( 0 );
	}
	
	public int
	getDLRLastTestSpeed()
	{
		return( 0 );
	}
	
	public long
	getDLRLastTestETA()
	{
		return( 0 );
	}
	
	public String
	getDLRTrace()
	{
		return( "" );
	}
	
	public void
	addStateAttributeListener(
		DownloadManagerStateAttributeListener l, String attribute, int event_type )
	{
	}
	
	public void
	removeStateAttributeListener(
		DownloadManagerStateAttributeListener l, String attribute, int event_type )
	{
	}

	public String
	getExplainFP()
	{
		return( "" );
	}
	
	public String
	getExplainSR()
	{
		return( "" );
	}
	
	public void
	resetTrace()
	{
	}
	
	public void
	appendTrace(
		String	str )
	{
	}
	
	public String
	getTrace()
	{
		return( "" );
	}
	
	public Object
	getRelatedTo()
	{
		return( this );
	}
	
	public String
	getRelationText() 
	{
		return( getName());
	}
	
	@Override
	public void 
	setUserData(
		Object key, 
		Object value) 
	{
	}
	
	@Override
	public Object 
	getUserData(
		Object key)
	{
		return( null );
	}
		
	public void
	destroy()
	{
	}
}
