/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.plugin.startstoprules.defaultplugin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.biglybt.core.config.COConfigurationListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.download.DownloadManagerStateAttributeListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagFeatureRateLimit;
import com.biglybt.core.tag.TagType;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.util.FileUtil;
import com.biglybt.core.util.LightHashMap;
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.ThreadPool;
import com.biglybt.core.util.TimeFormatter;
import com.biglybt.core.util.TorrentUtils;
import com.biglybt.pif.PluginConfig;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadAnnounceResult;
import com.biglybt.pif.download.DownloadException;
import com.biglybt.pif.download.DownloadScrapeResult;
import com.biglybt.pif.download.DownloadStats;
import com.biglybt.pif.logging.LoggerChannel;
import com.biglybt.pif.peers.PeerManager;
import com.biglybt.pifimpl.local.PluginCoreUtils;

/**
 * @author TuxPaper
 * @created Dec 13, 2005
 *
 */
public class
RankCalculatorReal 
	implements DefaultRankCalculator, DownloadManagerStateAttributeListener 
{
	private final static TagFeatureRateLimit[] NO_TAG_LIMITS = new TagFeatureRateLimit[0];
			
	/**
	 * Force torrent to be "Actively Seeding/Downloading" for this many ms upon
	 * start of torrent.
	 */
	private static final int FORCE_ACTIVE_FOR = 30000;

	/**
	 * Wait XX ms before really changing activity (DL or CDing) state when
	 * state changes via speed change
	 */
	private static final int ACTIVE_CHANGE_WAIT = 10000;

	/** Maximium ranking that a torrent can get using the SPRATIO ranking type */
	private static int SPRATIO_BASE_LIMIT = 99999;

	/**
	 * Amount to shift over the rank of the SEEDONLY ranking type, to make room
	 * in case the user has fallback to SPRATIO set.
	 */
	private static int SEEDONLY_SHIFT = SPRATIO_BASE_LIMIT + 1;

	private static final long HIGH_LATENCY_MILLIS 			= 2500;
	private static final long HIGH_LATENCY_RECOVERY_MILLIS 	= 60*1000;
	
	/**
	 * For loading config settings
	 */
	private static COConfigurationListener configListener = null;



	private static final long STALE_REFRESH_INTERVAL = 1000 * 60;

	//
	// Static config values

	/** Ranking System to use */
	private static int iRankType = -1;

	/** Min # of Peers needed before boosting the rank of downloads with no seeds */
	private static int minPeersToBoostNoSeeds;

	/** Min Speed needed to count a incomplete download as being actively downloading */
	private static int minSpeedForActiveDL;

	/** Min speed needed to count a complete download as being actively seeding */
	private static int minSpeedForActiveSeeding;

	// Ignore torrent if seed count is at least..
	private static int iIgnoreSeedCount;

	// Ignore even when First Priority
	private static boolean bIgnore0Peers;

	private static int iIgnoreShareRatio;

	private static int iIgnoreShareRatio_SeedStart;

	private static int iIgnoreRatioPeers;

	private static int iIgnoreRatioPeers_SeedStart;

	private static int iRankTypeSeedFallback;

	private static boolean bPreferLargerSwarms;

	private static int minQueueingShareRatio;

	// Ignore First Priority
	private static int iFirstPriorityIgnoreSPRatio;

	private static boolean bFirstPriorityIgnore0Peer;

	private static int iFirstPriorityType;

	private static int iFirstPrioritySeedingMinutes;

	private static int iFirstPriorityActiveMinutes;

	private static int iFirstPriorityIgnoreIdleMinutes;

	private static long minTimeAlive;

	private static boolean bAutoStart0Peers;

	private static int iTimed_MinSeedingTimeWithPeers;

	// count x peers as a full copy, but..
	private static int numPeersAsFullCopy;

	// don't count x peers as a full copy if seeds below
	private static int iFakeFullCopySeedStart;

	//
	// Class variables

	private final Download 			dl;
	private final DownloadManager	core_dm;
	
		// downloading rate controls
	
	private boolean bActivelyDownloading;
	private long 	lDLActivelyChangedOnMono;
	private long	lDownloadingHighLatencyTimeMono = -1;

		// seeding rate controls
	
	private SR		downloadSR = new SR();
	
	private boolean bActivelySeeding;
	private long 	lCDActivelyChangedOnMono;
	private long	lSeedingHighLatencyTimeMono = -1;

	private long staleCDSinceMono;

	private long staleCDOffset;

	private long lastStaleCDRefreshMono;

	private boolean bIsFirstPriority;
	
	private int	dlSpecificMinShareRatio;
	private int	dlSpecificMaxShareRatio;

	private long dlLastActiveTime;

	private String _sExplainFP = "";

	private String _sExplainSR = "";

	private String sTrace = "";

	private AEMonitor downloadData_this_mon = new AEMonitor( "StartStopRules:downloadData");

	private final StartStopRulesDefaultPlugin rules;

	private TagFeatureRateLimit[] tagsWithDLLimits = {};
	private TagFeatureRateLimit[] tagsWithCDLimits = {};

	// state-caches for sorting

	int lastModifiedScrapeResultPeers = 0;
	int lastModifiedScrapeResultSeeds = 0;
	int lastModifiedShareRatio = 0;
	boolean lastScrapeResultOk = false;

	private RankCalculatorSlotReserver	reservedSlot;
	private long	lastActivationAnnounce;
	
	private Map<Object,Object> userData;

	/**
	 * Default Initializer
	 *
	 * @param _rules
	 * @param _dl
	 */
	public RankCalculatorReal(StartStopRulesDefaultPlugin _rules, Download _dl) {
		rules = _rules;
		dl = _dl;

		dl.setSeedingRank( downloadSR );
		
		core_dm = PluginCoreUtils.unwrap( dl );

		DownloadManagerState dm_state = core_dm.getDownloadState();

		dlSpecificMinShareRatio = dm_state.getIntParameter( DownloadManagerState.PARAM_MIN_SHARE_RATIO );
		dlSpecificMaxShareRatio = dm_state.getIntParameter( DownloadManagerState.PARAM_MAX_SHARE_RATIO );
		dlLastActiveTime = dm_state.getLongParameter( DownloadManagerState.PARAM_DOWNLOAD_LAST_ACTIVE_TIME);
		if (dlLastActiveTime <= 0) {
			dlLastActiveTime = dm_state.getLongParameter(DownloadManagerState.PARAM_DOWNLOAD_COMPLETED_TIME);
		}

		dm_state.addListener( this, DownloadManagerState.AT_PARAMETERS, DownloadManagerStateAttributeListener.WRITTEN );

			// one of the main reasons to use the cache is for 0-peers ignore. a lot of instability in
			// seed rank can be caused by seeds that aren't first-priority due to 0 peers but on startup
			// they get flagged as FP until a scrape comes in at which point they switch to 'ignored'
		
		DownloadScrapeResult sr = dl.getAggregatedScrapeResult( false );

		int	srSeeds = Math.max( sr.getSeedCount(), 0 );
		int	srPeers = Math.max( sr.getNonSeedCount(), 0 );
		
		if ( sr.getScrapeStartTime() > 0 || srSeeds > 0 || srPeers > 0 ){

				// we don't know if the cached seeds includes us or not so assume it doesn't
			
			lastScrapeResultOk	= true;
			
			lastModifiedScrapeResultSeeds	= srSeeds;
			lastModifiedScrapeResultPeers	= srPeers;	
		}
				
		setupTagData();
		
		try {
			downloadData_this_mon.enter();

			if (configListener == null) {

				configListener = new COConfigurationListener() {
					@Override
					public void configurationSaved() {
						reloadConfigParams(rules.plugin_config);
					}
				};

				COConfigurationManager.addListener(configListener);
				configListener.configurationSaved();
			}
		} finally {
			downloadData_this_mon.exit();
		}
	}

	@Override
	public void
	attributeEventOccurred(
		DownloadManager 	download,
		String 				attribute,
		int 				event_type)
	{
		DownloadManagerState dm_state = core_dm.getDownloadState();

		dlSpecificMinShareRatio = dm_state.getIntParameter( DownloadManagerState.PARAM_MIN_SHARE_RATIO );
		dlSpecificMaxShareRatio = dm_state.getIntParameter( DownloadManagerState.PARAM_MAX_SHARE_RATIO );
		dlLastActiveTime = dm_state.getLongParameter( DownloadManagerState.PARAM_DOWNLOAD_LAST_ACTIVE_TIME);
		if (dlLastActiveTime <= 0) {
			dlLastActiveTime = dm_state.getLongParameter(DownloadManagerState.PARAM_DOWNLOAD_COMPLETED_TIME);
		}
	}

	public void
	destroy()
	{
		DownloadManagerState dm_state = core_dm.getDownloadState();

		dm_state.removeListener( this, DownloadManagerState.AT_PARAMETERS, DownloadManagerStateAttributeListener.WRITTEN );
	}

	/**
	 * Load config values into the static variables
	 *
	 * @param cfg
	 */
	public static void reloadConfigParams(PluginConfig cfg) {
		final String PREFIX = "StartStopManager_";

		iRankType = cfg.getUnsafeIntParameter(PREFIX + "iRankType");

		minPeersToBoostNoSeeds = cfg.getUnsafeIntParameter(PREFIX
				+ "iMinPeersToBoostNoSeeds");
		minSpeedForActiveDL = cfg.getUnsafeIntParameter(PREFIX + "iMinSpeedForActiveDL");
		minSpeedForActiveSeeding = cfg.getUnsafeIntParameter(PREFIX
				+ "iMinSpeedForActiveSeeding");

		iRankTypeSeedFallback = cfg.getUnsafeIntParameter(PREFIX
				+ "iRankTypeSeedFallback");
		bPreferLargerSwarms = cfg.getUnsafeBooleanParameter(PREFIX
				+ "bPreferLargerSwarms");
		minTimeAlive = cfg.getUnsafeIntParameter(PREFIX + "iMinSeedingTime") * 1000;
		bAutoStart0Peers = cfg.getUnsafeBooleanParameter(PREFIX + "bAutoStart0Peers");

		// Ignore torrent if seed count is at least..
		iIgnoreSeedCount = cfg.getUnsafeIntParameter(PREFIX + "iIgnoreSeedCount");
		bIgnore0Peers = cfg.getUnsafeBooleanParameter(PREFIX + "bIgnore0Peers");
		iIgnoreShareRatio = (int) (1000 * cfg.getUnsafeFloatParameter("Stop Ratio"));
		iIgnoreShareRatio_SeedStart = cfg.getUnsafeIntParameter(PREFIX
				+ "iIgnoreShareRatioSeedStart");
		iIgnoreRatioPeers = cfg.getUnsafeIntParameter("Stop Peers Ratio", 0);
		iIgnoreRatioPeers_SeedStart = cfg.getUnsafeIntParameter(PREFIX
				+ "iIgnoreRatioPeersSeedStart", 0);

		numPeersAsFullCopy = cfg.getUnsafeIntParameter("StartStopManager_iNumPeersAsFullCopy");
		iFakeFullCopySeedStart = cfg.getUnsafeIntParameter("StartStopManager_iFakeFullCopySeedStart");

		minQueueingShareRatio = cfg.getUnsafeIntParameter(PREFIX
				+ "iFirstPriority_ShareRatio");
		iFirstPriorityType = cfg.getUnsafeIntParameter(PREFIX + "iFirstPriority_Type");
		iFirstPrioritySeedingMinutes = cfg.getUnsafeIntParameter(PREFIX
				+ "iFirstPriority_SeedingMinutes");
		iFirstPriorityActiveMinutes = cfg.getUnsafeIntParameter(PREFIX
				+ "iFirstPriority_DLMinutes");
		// Ignore FP
		iFirstPriorityIgnoreSPRatio = cfg.getUnsafeIntParameter(PREFIX
				+ "iFirstPriority_ignoreSPRatio");
		bFirstPriorityIgnore0Peer = cfg.getUnsafeBooleanParameter(PREFIX
				+ "bFirstPriority_ignore0Peer");
		
		iFirstPriorityIgnoreIdleMinutes = cfg.getUnsafeIntParameter(PREFIX
				+ "iFirstPriority_ignoreIdleMinutes");
		
		iTimed_MinSeedingTimeWithPeers = cfg.getUnsafeIntParameter(PREFIX
				+ "iTimed_MinSeedingTimeWithPeers") * 1000;
	}

	/** Sort first by SeedingRank Descending, then by Position Ascending.
	 */
	@Override
	public int 
	compareTo(
		DefaultRankCalculator obj) 
	{
		if ( !( obj instanceof RankCalculatorReal )){
			
			return( 1 );
		}
		
		RankCalculatorReal dlData = (RankCalculatorReal)obj;
		
			// stopped/error always to bottom
		
		int state1 = dl.getState();
		boolean stopped1 = state1 == Download.ST_STOPPED || state1 == Download.ST_ERROR;
		int state2 = dlData.dl.getState();
		boolean stopped2 = state2 == Download.ST_STOPPED || state2 == Download.ST_ERROR;
		
		if ( stopped1 && stopped2 ){
			return( dl.getPosition() - dlData.dl.getPosition());
		}else if ( stopped1 ){
			return( 1 );
		}else if ( stopped2 ){
			return( -1 );
		}
		
		return( compareToIgnoreStopped(obj));
	}
	
	@Override
	public int 
	compareToIgnoreStopped(
		DefaultRankCalculator obj) 
	{
		if ( !( obj instanceof RankCalculatorReal )){
			
			return( 1 );
		}
		
		RankCalculatorReal dlData = (RankCalculatorReal)obj;
		
		// Test FP.  FP goes to top
		if (dlData.bIsFirstPriority && !bIsFirstPriority)
			return 1;
		if (!dlData.bIsFirstPriority && bIsFirstPriority)
			return -1;

		// Test Completeness.  Complete go to bottom
		boolean aIsComplete = dlData.dl.isComplete();
		boolean bIsComplete = dl.isComplete();
		if (aIsComplete && !bIsComplete)
			return -1;
		if (!aIsComplete && bIsComplete)
			return 1;

		if (iRankType == StartStopRulesDefaultPlugin.RANK_NONE) {
			return dl.getPosition() - dlData.dl.getPosition();
		}

		// Check Rank. Large to top
		int value = dlData.dl.getSeedingRank().getRank() - dl.getSeedingRank().getRank();
		if (value != 0)
			return value;

		if (iRankType != StartStopRulesDefaultPlugin.RANK_TIMED) {
			// Test Large/Small Swarm pref
			int numPeersThem = dlData.lastModifiedScrapeResultPeers;
			int numPeersUs = lastModifiedScrapeResultPeers;
			if (bPreferLargerSwarms)
				value = numPeersThem - numPeersUs;
			else
				value = numPeersUs - numPeersThem;
			if (value != 0)
				return value;

			// Test Share Ratio
			value = lastModifiedShareRatio - dlData.lastModifiedShareRatio;
			if (value != 0)
				return value;
		}

		// Test Position
		return dl.getPosition() - dlData.dl.getPosition();
	}
	
	public Object
	getRelatedTo()
	{
		return( dl.getTorrent());
	}
	
	public String
	getName()
	{
		return( dl.getName());
	}
	
	public int
	getState()
	{
		return( dl.getState());
	}
	
	public int
	getCoreState()
	{
		return( core_dm.getState());
	}
	
	public void
	addStateAttributeListener(
		DownloadManagerStateAttributeListener l, String attribute, int event_type )
	{
		core_dm.getDownloadState().addListener( l, attribute, event_type );
	}
	
	public void
	removeStateAttributeListener(
		DownloadManagerStateAttributeListener l, String attribute, int event_type )
	{
		core_dm.getDownloadState().removeListener( l, attribute, event_type );
	}
	
	public boolean
	supportsPosition()
	{
		return( true );
	}
	
	public int
	getPosition()
	{
		return( dl.getPosition());
	}
	
	public void
	setPosition(
		int		pos )
	{
		dl.setPosition( pos );
	}
	
	public void
	moveTo(
		int	pos )
	{
		dl.moveTo( pos );
	}
	
	@Override
	public boolean 
	isControllable()
	{
		return( true );
	}
	
	public boolean 
	isForceActive() 
	{
		DownloadStats stats = dl.getStats();
		return SystemTime.getCurrentTime() - stats.getTimeStarted() <= FORCE_ACTIVE_FOR;
	}

	public boolean
	isQueued()
	{
		return( dl.getState() == Download.ST_QUEUED );
	}

	public boolean
	isDownloading()
	{
		return( dl.getState() == Download.ST_DOWNLOADING );
	}
	
	public boolean
	isChecking()
	{
		return( dl.isChecking());
	}
	
	public boolean
	isMoving()
	{
		return( dl.isMoving() || FileUtil.hasTask( core_dm ));
	}
	
	public boolean
	isForceStart()
	{
		return( dl.isForceStart());
	}
	
	public boolean
	isComplete()
	{
		return( dl.isComplete());
	}
	
	public void
	initialize()
	
		throws DownloadException
	{
		dl.initialize();
	}
	public void
	start()
	
		throws DownloadException
	{
		dl.start();
	}
	
	public void
	restart()
	
		throws DownloadException
	{
		dl.restart();
	}
	
	public void
	stopAndQueue()
		
		throws DownloadException
	{
		dl.stopAndQueue();
	}
	
	public int
	getShareRatio()
	{
		return( dl.getStats().getShareRatio());
	}
	
	public long
	getUploadAverage()
	{
		return( dl.getStats().getUploadAverage());
	}
	
	public long
	getDownloadAverage()
	{
		return( dl.getStats().getDownloadAverage());
	}
	
	public long
	getTimeStarted()
	{
		return( dl.getStats().getTimeStarted());
	}
	
	public long
	getSizeExcludingDND()
	{
		return( core_dm.getStats().getSizeExcludingDND());
	}
	
	public long
	getRemainingExcludingDND()
	{
		return( core_dm.getStats().getRemainingExcludingDND());
	}
	
	@Override
	public int[] 
	getFilePriorityStats()
	{
		return( core_dm.getStats().getFilePriorityStats());
	}
	
	public DownloadScrapeResult
	getAggregatedScrapeResult(
		boolean 		b )
	{
		return( dl.getAggregatedScrapeResult( b ));
	}
	
	public boolean 
	scrapeResultOk() 
	{	
		DownloadScrapeResult sr = dl.getAggregatedScrapeResult( false );
		
		return (sr.getResponseType() == DownloadScrapeResult.RT_SUCCESS);
	}

	public int
	calcSeedsNoUs()
	{
		return( calcSeedsNoUs( dl, dl.getAggregatedScrapeResult( false )));
	}
	
	/** Get # of seeds, not including us, AND including fake full copies
	 *
	 * @param download Download to get # of seeds for
	 * @return seed count
	 */
	
	
	private int calcSeedsNoUs(Download download, DownloadScrapeResult sr ) {
		return calcSeedsNoUs(download, sr, calcPeersNoUs( download, sr ));
	}

	/** Get # of seeds, not including us, AND including fake full copies
	 *
	 * @param download Download to get # of seeds for
	 * @param numPeers # peers we know of, required to calculate Fake Full Copies
	 * @return seed count
	 */
	private int calcSeedsNoUs(Download download, DownloadScrapeResult sr, int numPeers) {
		int numSeeds = 0;
		if (sr.getScrapeStartTime() > 0) {
			long seedingStartedOn = download.getStats().getTimeStartedSeeding();
			numSeeds = sr.getSeedCount();
			// If we've scraped after we started seeding
			// Remove ourselves from count
			if ((numSeeds > 0) && (seedingStartedOn > 0)
					&& (download.getState() == Download.ST_SEEDING)
					&& (sr.getScrapeStartTime() > seedingStartedOn))
				numSeeds--;
		}
		if (numSeeds == 0) {
			// Fallback to the # of seeds we know of
			DownloadAnnounceResult ar = download.getLastAnnounceResult();
			if (ar != null
					&& ar.getResponseType() == DownloadAnnounceResult.RT_SUCCESS)
				numSeeds = ar.getSeedCount();
		}

		if (numPeersAsFullCopy != 0 && numSeeds >= iFakeFullCopySeedStart)
			numSeeds += numPeers /numPeersAsFullCopy;

		return Math.max(numSeeds,0);
	}

	/**
	 * Get # of peers not including us
	 *
	 */
	
	private int calcPeersNoUs()
	{
		return( calcPeersNoUs(dl,dl.getAggregatedScrapeResult( false )));
	}
	
	private int calcPeersNoUs(Download download, DownloadScrapeResult sr) {
		int numPeers = 0;
		if (sr.getScrapeStartTime() > 0) {
			numPeers = sr.getNonSeedCount();
			// If we've scraped after we started downloading
			// Remove ourselves from count
			if ((numPeers > 0) && (download.getState() == Download.ST_DOWNLOADING)
					&& (sr.getScrapeStartTime() > download.getStats().getTimeStarted()))
				numPeers--;
		}
		if (numPeers == 0) {
			// Fallback to the # of peers we know of
			DownloadAnnounceResult ar = download.getLastAnnounceResult();
			if (ar != null
					&& ar.getResponseType() == DownloadAnnounceResult.RT_SUCCESS)
				numPeers = ar.getNonSeedCount();

			/*
			 The problem here is that we don't know if the activation attempt came from a seed or a peer
			 so assuming it is a peer opens us up to lots of invalid transitions out of a 0-peers state
			 Reworked to force an announce on an activation attempt
			if (numPeers == 0) {
				DownloadActivationEvent activationState = download.getActivationState();
				if (activationState != null) {
					numPeers = activationState.getActivationCount();
				}
			}
			*/
		}
		return Math.max(numPeers,0);
	}

	/**
	 * Retrieves whether the torrent is "actively" downloading
	 *
	 * @return true: actively downloading
	 */
	public boolean 
	getActivelyDownloading() 
	{
		boolean bIsActive = false;
		DownloadStats stats = dl.getStats();
		int state = dl.getState();

		// In order to be active,
		// - Must be downloading (and thus incomplete)
		// - Must be above speed threshold, or started less than 30s ago
		if (state != Download.ST_DOWNLOADING) {
			bIsActive = false;
		} else if (SystemTime.getCurrentTime() - stats.getTimeStarted() <= FORCE_ACTIVE_FOR) {
			bIsActive = true;
		} else {
			// activity based on DL Average
			
			long nowMono = SystemTime.getMonotonousTime();

			bIsActive = (stats.getDownloadAverage() >= minSpeedForActiveDL);

			if ( !bIsActive ){
				
					// check downloading isn't being choked by high disk latency
				
				DiskManager dm = core_dm.getDiskManager();
				
				if ( dm != null ){
					
					long[] latency = dm.getLatency();
					
					if ( latency[1] > HIGH_LATENCY_MILLIS ){
						
						if ( lDownloadingHighLatencyTimeMono == -1 ){
							
							if (rules.bDebugLog){
								
								rules.log.log(dl.getTorrent(), LoggerChannel.LT_INFORMATION,
										"download speed is low but write latency high, ignoring");
							}
						}
						
						lDownloadingHighLatencyTimeMono = nowMono;
						
						bIsActive = true;	// treat as still active
						
					}else{
						
						if ( lDownloadingHighLatencyTimeMono != -1 ){
							
								// give things time to recover
							
							if ( nowMono - lDownloadingHighLatencyTimeMono > HIGH_LATENCY_RECOVERY_MILLIS ){
								
								lDownloadingHighLatencyTimeMono = -1;
								
							}else{
								
								bIsActive = true;
							}
						}
					}
				}
			}
			
			if (bActivelyDownloading != bIsActive) {
				// Change
				if (lDLActivelyChangedOnMono == -1) {
					// Start Timer
					lDLActivelyChangedOnMono = nowMono;
					bIsActive = !bIsActive;
				} else if (nowMono - lDLActivelyChangedOnMono < ACTIVE_CHANGE_WAIT) {
					// Continue as old state until timer finishes
					bIsActive = !bIsActive;
				}
			} else {
				// no change, reset timer
				lDLActivelyChangedOnMono = -1;
			}
		}

		if (bActivelyDownloading != bIsActive) {
			bActivelyDownloading = bIsActive;
			if (rules != null) {
				rules.requestProcessCycle(null);
				if (rules.bDebugLog)
					rules.log.log(dl.getTorrent(), LoggerChannel.LT_INFORMATION,
							"somethingChanged: ActivelyDownloading changed");
			}
		}
		return bActivelyDownloading;
	}

	/**
	 * Retrieves whether the torrent is "actively" seeding
	 *
	 * @return true: actively seeding
	 */
	public boolean 
	getActivelySeeding() 
	{
		boolean bIsActive = false;
		DownloadStats stats = dl.getStats();
		int state = dl.getState();
		// Timed torrents don't use a speed threshold, since they are based on time!
		// However, First Priorities need to be checked for activity so that
		// timed ones can start when FPs are below threshold.  Ditto for 0 Peers
		// when bAutoStart0Peers
		if (iRankType == StartStopRulesDefaultPlugin.RANK_TIMED
				&& !isFirstPriority()
				&& !(bAutoStart0Peers && calcPeersNoUs() == 0 && lastScrapeResultOk)) {
			bIsActive = (state == Download.ST_SEEDING);

		} else if (state != Download.ST_SEEDING
				|| (bAutoStart0Peers && calcPeersNoUs() == 0)) {
			// Not active if we aren't seeding
			// Not active if we are AutoStarting 0 Peers, and peer count == 0
			bIsActive = false;
			staleCDSinceMono = -1;
		} else if (SystemTime.getCurrentTime() - stats.getTimeStarted() <= FORCE_ACTIVE_FOR) {
			bIsActive = true;
			staleCDSinceMono = -1;
		} else {
			bIsActive = (stats.getUploadAverage() >= minSpeedForActiveSeeding);

			long nowMono = SystemTime.getMonotonousTime();

				// check seeding isn't being choked by high disk latency
				
			DiskManager dm = core_dm.getDiskManager();
			
			if ( dm != null ){
				
				long[] latency = dm.getLatency();
				
				if ( latency[0] > HIGH_LATENCY_MILLIS ){
					
					if ( lSeedingHighLatencyTimeMono == -1 ){
						
						if (rules.bDebugLog){
							
							rules.log.log(dl.getTorrent(), LoggerChannel.LT_INFORMATION,
									"seeding speed is low but read latency high, ignoring");
						}
					}
					
					lSeedingHighLatencyTimeMono = nowMono;
					
					bIsActive = true;	// treat as still active
					
				}else{
					
					if ( lSeedingHighLatencyTimeMono != -1 ){
						
							// give things time to recover
						
						if ( nowMono - lSeedingHighLatencyTimeMono > HIGH_LATENCY_RECOVERY_MILLIS ){
							
							lSeedingHighLatencyTimeMono = -1;
							
						}else{
							
							bIsActive = true;
						}
					}
				}
			}
		
			if (bActivelySeeding != bIsActive) {
				// Change
				if (lCDActivelyChangedOnMono < 0) {
					// Start Timer
					lCDActivelyChangedOnMono = nowMono;
					bIsActive = !bIsActive;
				} else if (nowMono - lCDActivelyChangedOnMono < ACTIVE_CHANGE_WAIT) {
					// Continue as old state until timer finishes
					bIsActive = !bIsActive;
				}

				if (bActivelySeeding != bIsActive) {
  				if (bIsActive) {
  					staleCDSinceMono = -1;
  					staleCDOffset = 0;
  				} else {
  					staleCDSinceMono = nowMono;
  				}
				}

			} else {
				// no change, reset timer
				lCDActivelyChangedOnMono = -1;
			}
		}

		if (bActivelySeeding != bIsActive) {
			bActivelySeeding = bIsActive;

			if (rules != null) {
				rules.requestProcessCycle(null);
				if (rules.bDebugLog)
					rules.log.log(dl.getTorrent(), LoggerChannel.LT_INFORMATION,
							"somethingChanged: ActivelySeeding changed");
			}
		}
		return bActivelySeeding;
	}
	
	private void
	setupTagData()
	{
		if ( rules.hasTagDLorCDLimits()){
			
			List<Tag> tags = rules.getTagManager().getTagsForTaggable( TagType.TT_DOWNLOAD_MANUAL, core_dm );

			if ( tags.isEmpty()){
				
				tagsWithDLLimits = tagsWithCDLimits = NO_TAG_LIMITS;
				
			}else{
				boolean comp = dl.isComplete();
				
				List<TagFeatureRateLimit> lims = new ArrayList<>();
				
				for ( Tag tag: tags ){
					if ( tag instanceof TagFeatureRateLimit ){
						TagFeatureRateLimit t = (TagFeatureRateLimit)tag;
					
						if ( comp ){
							if ( t.getMaxActiveSeeds() > 0 ){
							
								lims.add( t );
							}
						}else{
							if ( t.getMaxActiveDownloads() > 0 ){
							
								lims.add( t );
							}
						}
					}
				}
				
				if ( comp ){
					tagsWithDLLimits = NO_TAG_LIMITS;
					tagsWithCDLimits = lims.toArray( new TagFeatureRateLimit[lims.size()] );
	
				}else{
					tagsWithDLLimits = lims.toArray( new TagFeatureRateLimit[lims.size()] );
					tagsWithCDLimits = NO_TAG_LIMITS;
				}
			}
		}else{
			tagsWithDLLimits = tagsWithCDLimits = NO_TAG_LIMITS;
		}
	}
	
	public TagFeatureRateLimit[]
	getTagsWithDLLimits()
	{
		return( tagsWithDLLimits );
	}
	
	public TagFeatureRateLimit[]
	getTagsWithCDLimits()
	{
		return( tagsWithCDLimits );
	}
	
	public boolean
	getLastScrapeResultOk()
	{
		return( lastScrapeResultOk );
	}
	
	public void
	scrapeReceived(
		DownloadScrapeResult		result )
	{
		if ( result.getResponseType() == DownloadScrapeResult.RT_SUCCESS ){
					
			updateScrapeCache();
			
			lastScrapeResultOk = true;
		}
	}
	
	private void
	updateScrapeCache()
	{
		DownloadScrapeResult sr = dl.getAggregatedScrapeResult( false );
		
		lastModifiedScrapeResultPeers = calcPeersNoUs(dl,sr);
		lastModifiedScrapeResultSeeds = calcSeedsNoUs(dl,sr);
	}
	
	public int
	getLastModifiedScrapeResultPeers()
	{
		return( lastModifiedScrapeResultPeers );
	}
	
	public int
	getLastModifiedScrapeResultSeeds()
	{
		return( lastModifiedScrapeResultSeeds );
	}

	public String
	getExplainFP()
	{
		return( _sExplainFP );
	}
	
	public String
	getExplainSR()
	{
		return( _sExplainSR );
	}
	
	public void
	resetTrace()
	{
		sTrace = "";
	}
	
	public void
	appendTrace(
		String	str )
	{
		sTrace += str;
	}
	
	public String
	getTrace()
	{
		return( sTrace );
	}
	
	/** Assign Seeding Rank based on RankType
	 * @return New Seeding Rank Value
	 */
	
	public int
	getSeedingRank()
	{
		return( downloadSR.getRank());
	}
	
	public void 
	recalcSeedingRank() 
	{
		try {
			downloadData_this_mon.enter();

			boolean ignore0Peers = bIgnore0Peers;
			
			int lastSR	= downloadSR.getRank();
				
			int newSR = _recalcSeedingRankSupport( ignore0Peers, lastSR, false );

			int newSRIgnoringIgnore0Peers;
			
			if ( newSR == SR_0PEERS ){
				
				newSRIgnoringIgnore0Peers = _recalcSeedingRankSupport( false, lastSR, true );
				
			}else{
				
				newSRIgnoringIgnore0Peers = newSR;
			}
			
			downloadSR.update( newSR, newSRIgnoringIgnore0Peers );
			
		} finally {

			downloadData_this_mon.exit();
		}
	}

	private int 
	_recalcSeedingRankSupport( 
		boolean		ignore0Peers,
		int 		oldSR,
		boolean		is_test )
	{

		String sExplainSR = "";

		try{
			DownloadStats stats = dl.getStats();
	
			int newSR;
	
			setupTagData();
			
			// make undownloaded sort to top so they can start first.
			if (!dl.isComplete()) {
				newSR = SR_COMPLETE_STARTS_AT + (10000 - dl.getPosition());
				// make sure we capture FP being turned off when torrent does from
				// complete to incomplete
				isFirstPriority();
				if ( rules.bDebugLog ){
					sExplainSR += "  not complete. SetSR " + newSR + "\n";
				}
				return newSR;
			}
	
			// here we are seeding
	
			lastModifiedShareRatio = stats.getShareRatio();
			
			updateScrapeCache();
	
			boolean bScrapeResultsOk = (lastModifiedScrapeResultPeers > 0 || lastModifiedScrapeResultSeeds > 0
					|| lastScrapeResultOk) && (lastModifiedScrapeResultPeers >= 0 && lastModifiedScrapeResultSeeds >= 0);
	
			if (!isFirstPriority()) {
				// Check Ignore Rules
				// never apply ignore rules to First Priority Matches
				// (we don't want leechers circumventing the 0.5 rule)
	
				//0 means unlimited
				int	activeMaxSR = dlSpecificMaxShareRatio;
				if ( activeMaxSR <= 0 ){
					activeMaxSR = iIgnoreShareRatio;
				}
				if (activeMaxSR != 0 && lastModifiedShareRatio >= activeMaxSR
						&& (lastModifiedScrapeResultSeeds >= iIgnoreShareRatio_SeedStart || !bScrapeResultsOk)
						&& lastModifiedShareRatio != -1) {
	
					if (rules.bDebugLog)
						sExplainSR += "  shareratio met: shareRatio(" + lastModifiedShareRatio
								+ ") >= " + activeMaxSR + "\n";
	
					return  SR_SHARERATIOMET;
				} else if (rules.bDebugLog && activeMaxSR != 0
						&& lastModifiedShareRatio >= activeMaxSR) {
					sExplainSR += "  shareratio NOT met: ";
					if (lastModifiedScrapeResultSeeds >= iIgnoreShareRatio_SeedStart)
						sExplainSR += lastModifiedScrapeResultSeeds + " below seed threshold of "
								+ iIgnoreShareRatio_SeedStart;
					sExplainSR += "\n";
				}
	
				if (lastModifiedScrapeResultPeers == 0 && bScrapeResultsOk) {
					// If both bIgnore0Peers and bFirstPriorityIgnore0Peer are on,
					// we won't know which one it is at this point.
					// We have to use the normal SR_0PEERS in case it isn't FP
					if (ignore0Peers) {
						if (rules.bDebugLog)
							sExplainSR += "  Ignore 0 Peers criteria met\n";
	
						return SR_0PEERS;
					}
	
	//					if (bFirstPriorityIgnore0Peer) {
	//						if (rules.bDebugLog)
	//							sExplainSR += "  Ignore 0 Peers criteria for FP met\n";
	//
	//						return SR_FP0PEERS;
	//					}
				} else if (rules.bDebugLog && lastModifiedScrapeResultPeers == 0) {
					sExplainSR += "  0 Peer Ignore rule NOT applied: Scrape invalid\n";
				}
	
	//				if (numPeers != 0 && iFirstPriorityIgnoreSPRatio != 0
	//						&& numSeeds / numPeers >= iFirstPriorityIgnoreSPRatio) {
	//					if (rules.bDebugLog)
	//						sExplainSR += "  Ignore rule for S:P Ratio for FP met.  Current: ("
	//								+ (numSeeds / numPeers)
	//								+ ") >= Threshold("
	//								+ iFirstPriorityIgnoreSPRatio + ")\n";
	//
	//					return SR_FP_SPRATIOMET;
	//				}
	
				//0 means disabled
				if ((iIgnoreSeedCount != 0) && (lastModifiedScrapeResultSeeds >= iIgnoreSeedCount)) {
					if (rules.bDebugLog)
						sExplainSR += "  SeedCount Ignore rule met.  numSeeds("
								+ lastModifiedScrapeResultSeeds + " >= iIgnoreSeedCount(" + iIgnoreSeedCount + ")\n";
	
					return SR_NUMSEEDSMET;
				}
	
				// Ignore when P:S ratio met
				// (More Peers for each Seed than specified in Config)
				//0 means never stop
				if (iIgnoreRatioPeers != 0 && lastModifiedScrapeResultSeeds != 0) {
					float ratio = (float) lastModifiedScrapeResultPeers / lastModifiedScrapeResultSeeds;
					if (ratio <= iIgnoreRatioPeers
							&& lastModifiedScrapeResultSeeds >= iIgnoreRatioPeers_SeedStart) {
	
						if (rules.bDebugLog)
							sExplainSR += "  P:S Ignore rule met.  ratio(" + ratio
									+ " <= threshold(" + iIgnoreRatioPeers_SeedStart + ")\n";
	
						return SR_RATIOMET;
					}
				}
			}
	
			// Never do anything with rank type of none
			if (iRankType == StartStopRulesDefaultPlugin.RANK_NONE) {
				if (rules.bDebugLog)
					sExplainSR += "  Ranking Type set to none.. blanking seeding rank\n";
	
				// everythink ok!
				
				return 0;
			}
	
			if (iRankType == StartStopRulesDefaultPlugin.RANK_TIMED) {
				if (bIsFirstPriority) {
					newSR = SR_TIMED_QUEUED_ENDS_AT + 1;
					return newSR;
				}
	
				int state = dl.getState();
				if (state == Download.ST_STOPPING || state == Download.ST_STOPPED
						|| state == Download.ST_ERROR) {
					if (rules.bDebugLog)
						sExplainSR += "  Download stopping, stopped or in error\n";
					return SR_NOTQUEUED;
				} else if (state == Download.ST_SEEDING || state == Download.ST_READY
						|| state == Download.ST_WAITING || state == Download.ST_PREPARING) {
					// force sort to top
					long lMsElapsed = 0;
					long lMsTimeToSeedFor = minTimeAlive;
					if (state == Download.ST_SEEDING && !dl.isForceStart()) {
						lMsElapsed = (SystemTime.getCurrentTime() - stats
								.getTimeStartedSeeding());
						if (iTimed_MinSeedingTimeWithPeers > 0) {
	  					PeerManager peerManager = dl.getPeerManager();
	  					if (peerManager != null) {
	  						int connectedLeechers = peerManager.getStats().getConnectedLeechers();
	  						if (connectedLeechers > 0) {
	  							lMsTimeToSeedFor = iTimed_MinSeedingTimeWithPeers;
	  						}
	  					}
						}
					}
	
					if (lMsElapsed >= lMsTimeToSeedFor) {
						newSR = 1;
						if (oldSR > SR_TIMED_QUEUED_ENDS_AT) {
							if ( !is_test ){
								rules.requestProcessCycle(null);
							}
							if (rules.bDebugLog)
								rules.log.log(dl.getTorrent(), LoggerChannel.LT_INFORMATION,
										"somethingChanged: TimeUp");
						}
					} else {
						newSR = SR_TIMED_QUEUED_ENDS_AT + 1 + (int) (lMsElapsed / 1000);
						if (oldSR <= SR_TIMED_QUEUED_ENDS_AT) {
							if ( !is_test ){
								rules.requestProcessCycle(null);
							}
							if (rules.bDebugLog)
								rules.log.log(dl.getTorrent(), LoggerChannel.LT_INFORMATION,
										"somethingChanged: strange timer change");
						}
					}
					
					return newSR;
				} else { // ST_QUEUED
					// priority goes to ones who haven't been seeded for long
					// maybe share ratio might work well too
					long diff;
					if (dlLastActiveTime == 0) {
						diff = dl.getStats().getSecondsOnlySeeding();
						if (diff > SR_TIMED_QUEUED_ENDS_AT - 100000) {
							// close to overrunning.. so base off position
							diff = SR_TIMED_QUEUED_ENDS_AT - 100000 + dl.getPosition();
						}
						newSR = SR_TIMED_QUEUED_ENDS_AT - (int) diff;
					} else {
						diff = ((System.currentTimeMillis() / 1000) - (dlLastActiveTime / 1000));
						if (diff >= SR_TIMED_QUEUED_ENDS_AT) {
							newSR = SR_TIMED_QUEUED_ENDS_AT - 1;
						} else {
							newSR = (int) diff;
						}
					}
					return newSR;
				}
			}
	
			/**
			 * Add to SeedingRank based on Rank Type
			 */
	
			// SeedCount and SPRatio require Scrape Results..
			if (bScrapeResultsOk) {
				if ( iRankType == StartStopRulesDefaultPlugin.RANK_PEERCOUNT ){
					if (rules.bDebugLog){
						sExplainSR += "  PeerCount seeds=" + lastModifiedScrapeResultSeeds + "peers=" + lastModifiedScrapeResultPeers +"\n";
					}
					
					if(lastModifiedScrapeResultPeers > lastModifiedScrapeResultSeeds * 10){
						
						newSR = 100 * lastModifiedScrapeResultPeers * 10;
						
					}else{
						
						newSR = (int)((long)100 * lastModifiedScrapeResultPeers * lastModifiedScrapeResultPeers/(lastModifiedScrapeResultSeeds+1));
					}
				}else if ((iRankType == StartStopRulesDefaultPlugin.RANK_SEEDCOUNT)
						&& (iRankTypeSeedFallback == 0 || iRankTypeSeedFallback > lastModifiedScrapeResultSeeds)){
				
					if (rules.bDebugLog){
						sExplainSR += "  SeedCount seeds=" + lastModifiedScrapeResultSeeds +"\n";
					}

					if (lastModifiedScrapeResultSeeds < 10000){
						newSR = 10000 - lastModifiedScrapeResultSeeds;
					}else{
						newSR = 1;
					}
					
					// shift over to make way for fallback
					newSR *= SEEDONLY_SHIFT;
	
				}else{ // iRankType == RANK_SPRATIO or we are falling back
					
					if (lastModifiedScrapeResultPeers != 0) {
						if (lastModifiedScrapeResultSeeds == 0) {
							if (lastModifiedScrapeResultPeers >= minPeersToBoostNoSeeds){
								newSR = SPRATIO_BASE_LIMIT;
								
								if (rules.bDebugLog){
									sExplainSR += "  Seed:Peer ratio=" + lastModifiedScrapeResultSeeds + ":" + lastModifiedScrapeResultPeers +"\n";
								}
							}else{
								newSR = 0;
							}
						} else { // numSeeds != 0 && numPeers != 0
							float x = (float) lastModifiedScrapeResultSeeds / lastModifiedScrapeResultPeers;
							newSR = (int)( SPRATIO_BASE_LIMIT / ((x + 1) * (x + 1)));
							
							if (rules.bDebugLog){
								sExplainSR += "  Seed:Peer ratio=" + lastModifiedScrapeResultSeeds + ":" + lastModifiedScrapeResultPeers +"\n";
							}
						}
					}else{
						newSR = 0;
					}
				}
			} else {
								
				if (rules.bDebugLog)
					sExplainSR += "  Can't calculate SR, no scrape results\n";
				
				return( SR_NOSCRAPE );
			}
	
			if (staleCDOffset > 0) {
				// every 10 minutes of not being active, subtract one SR
				if (newSR > staleCDOffset) {
					newSR -= staleCDOffset;
					sExplainSR += "  subtracted " + staleCDOffset + " due to non-activeness\n";
				} else {
					if ( !is_test ){
						staleCDOffset = 0;
					}
				}
			}
	
			if (newSR < 0)
				newSR = 1;
	
			return newSR;
		}finally{
			
			if ( !is_test ){
							
				_sExplainSR = sExplainSR;
			}
		}
	}

	/** Does the torrent match First Priority criteria?
	 * @return FP State
	 */
	public boolean isFirstPriority() {
		
		boolean ignore0Peers= bFirstPriorityIgnore0Peer;
		
		boolean bFP = pisFirstPriority( ignore0Peers, false, false );
		
		if ( rules.getTagFP()){
			
			rules.setFPTagStatus( core_dm, pisFirstPriority( ignore0Peers, true, true ));
		}
		
		if (bIsFirstPriority != bFP) {
			bIsFirstPriority = bFP;
			rules.requestProcessCycle(null);
			if (rules.bDebugLog)
				rules.log.log(dl.getTorrent(), LoggerChannel.LT_INFORMATION,
						"somethingChanged: FP changed");
		}
		return bIsFirstPriority;
	}

	private boolean 
	pisFirstPriority( 
		boolean		ignore0Peers,
		boolean 	is_test,
		boolean		for_tag_fp )
	{
		String sExplainFP = "";
		
		try{
			if (rules.bDebugLog)
				sExplainFP = "FP if "
						+ (iFirstPriorityType == FIRSTPRIORITY_ALL ? "all" : "any")
						+ " criteria match:\n";
	
			DownloadManagerState dm_state = core_dm.getDownloadState();
			
			if ( 	( dm_state.getTransientFlags() & 
						( 	DownloadManagerState.TRANSIENT_FLAG_FRIEND_FP | 
							DownloadManagerState.TRANSIENT_FLAG_TAG_FP )) != 0 ){
				
				if (rules.bDebugLog)
					sExplainFP += "Is FP: Friend(s) have interest or Tag is FP\n";
				
				return( true );
			}
			
			if (!dl.isPersistent()) {
				if (rules.bDebugLog)
					sExplainFP += "Not FP: Download not persistent\n";
				return false;
			}
	
			if ( !for_tag_fp ) {
				int state = dl.getState();
				if (state == Download.ST_ERROR || state == Download.ST_STOPPED) {
					if (rules.bDebugLog)
						sExplainFP += "Not FP: Download is ERROR or STOPPED\n";
					return false;
				}
			}
	
			// FP only applies to completed
			if (!dl.isComplete()) {
				if (rules.bDebugLog)
					sExplainFP += "Not FP: Download not complete\n";
				return false;
			}
	
			List listeners = rules.getFPListeners();
			StringBuffer fp_listener_debug = null;
			if (!listeners.isEmpty())
			{
				if (rules.bDebugLog)
					fp_listener_debug = new StringBuffer();
				for (Iterator iter = listeners.iterator(); iter.hasNext();)
				{
					StartStopRulesFPListener l = (StartStopRulesFPListener) iter.next();
					boolean result = l.isFirstPriority(dl, lastModifiedScrapeResultSeeds, lastModifiedScrapeResultPeers, fp_listener_debug);
					if (fp_listener_debug != null && fp_listener_debug.length() > 0)
					{
						char last_ch = fp_listener_debug.charAt(fp_listener_debug.length() - 1);
						if (last_ch != '\n')
							fp_listener_debug.append('\n');
						sExplainFP += fp_listener_debug;
						fp_listener_debug.setLength(0);
					}
					if (result)
					{
						return true;
					}
				}
			}
	
	
			// FP doesn't apply when S:P >= set SPratio (SPratio = 0 means ignore)
			if (lastModifiedScrapeResultPeers > 0 && lastModifiedScrapeResultSeeds > 0
					&& (lastModifiedScrapeResultSeeds / lastModifiedScrapeResultPeers) >= iFirstPriorityIgnoreSPRatio
					&& iFirstPriorityIgnoreSPRatio != 0) {
				if (rules.bDebugLog)
					sExplainFP += "Not FP: S:P >= " + iFirstPriorityIgnoreSPRatio + ":1\n";
				return false;
			}
	
			//not FP if no peers  //Nolar, 2105 - Gouss, 2203
			if (lastModifiedScrapeResultPeers == 0 && lastScrapeResultOk && ignore0Peers) {
				if (rules.bDebugLog)
					sExplainFP += "Not FP: 0 peers\n";
				return false;
			}
	
			if (iFirstPriorityIgnoreIdleMinutes > 0) {
				long lastUploadSecs = dl.getStats().getSecondsSinceLastUpload();
				if (lastUploadSecs < 0) {
					lastUploadSecs = dl.getStats().getSecondsOnlySeeding();
				}
				if (lastUploadSecs > 60 * (long)iFirstPriorityIgnoreIdleMinutes) {
					if (rules.bDebugLog)
						sExplainFP += "Not FP: " + lastUploadSecs + "s > "
								+ iFirstPriorityIgnoreIdleMinutes + "m of no upload\n";
					return false;
				}
			}
	
			int shareRatio = dl.getStats().getShareRatio();
	
			int	activeMinSR = dlSpecificMinShareRatio;
			if ( activeMinSR <= 0 ){
				activeMinSR = minQueueingShareRatio;
			}
			boolean bLastMatched = (shareRatio != -1)
					&& (shareRatio < activeMinSR);
	
			if (rules.bDebugLog)
				sExplainFP += "  shareRatio(" + shareRatio + ") < "
						+ activeMinSR + "=" + bLastMatched + "\n";
	
			if (!bLastMatched && iFirstPriorityType == FIRSTPRIORITY_ALL) {
				if (rules.bDebugLog)
					sExplainFP += "..Not FP.  Exit Early\n";
				return false;
			}
			if (bLastMatched && iFirstPriorityType == FIRSTPRIORITY_ANY) {
				if (rules.bDebugLog)
					sExplainFP += "..Is FP.  Exit Early\n";
				return true;
			}
	
			bLastMatched = (iFirstPrioritySeedingMinutes == 0);
			if (!bLastMatched) {
				long timeSeeding = dl.getStats().getSecondsOnlySeeding();
				if (timeSeeding >= 0) {
					bLastMatched = (timeSeeding < (iFirstPrioritySeedingMinutes * 60));
					if (rules.bDebugLog)
						sExplainFP += "  SeedingTime(" + timeSeeding + ") < "
								+ (iFirstPrioritySeedingMinutes * 60) + "=" + bLastMatched + "\n";
					if (!bLastMatched && iFirstPriorityType == FIRSTPRIORITY_ALL) {
						if (rules.bDebugLog)
							sExplainFP += "..Not FP.  Exit Early\n";
						return false;
					}
					if (bLastMatched && iFirstPriorityType == FIRSTPRIORITY_ANY) {
						if (rules.bDebugLog)
							sExplainFP += "..Is FP.  Exit Early\n";
						return true;
					}
				}
			} else if (rules.bDebugLog) {
				sExplainFP += "  Skipping Seeding Time check (user disabled)\n";
			}
	
			bLastMatched = (iFirstPriorityActiveMinutes == 0);
			if (!bLastMatched) {
				long timeActive = dl.getStats().getSecondsDownloading()
						+ dl.getStats().getSecondsOnlySeeding();
				if (timeActive >= 0) {
					bLastMatched = (timeActive < (iFirstPriorityActiveMinutes * 60));
					if (rules.bDebugLog)
						sExplainFP += "  ActiveTime(" + timeActive + ") < "
								+ (iFirstPriorityActiveMinutes * 60) + "=" + bLastMatched + "\n";
					if (!bLastMatched && iFirstPriorityType == FIRSTPRIORITY_ALL) {
						if (rules.bDebugLog)
							sExplainFP += "..Not FP.  Exit Early\n";
						return false;
					}
					if (bLastMatched && iFirstPriorityType == FIRSTPRIORITY_ANY) {
						if (rules.bDebugLog)
							sExplainFP += "..Is FP.  Exit Early\n";
						return true;
					}
				}
			} else if (rules.bDebugLog) {
				sExplainFP += "  Skipping DL Time check (user disabled)\n";
			}
	
			if (iFirstPriorityType == FIRSTPRIORITY_ALL) {
				if (rules.bDebugLog)
					sExplainFP += "..Is FP\n";
				return true;
			}
	
			if (rules.bDebugLog)
				sExplainFP += "..Not FP\n";
			return false;
			
		}finally{
			
			if ( !is_test ){
				
				_sExplainFP = sExplainFP;
			}
		}
	}

	/**
	 *
	 * @return last calculated FP state
	 */
	public boolean getCachedIsFP() {
		return bIsFirstPriority;
	}

	private boolean	dlr_test_active;
	private long 	dlr_test_start_time;
	private long 	dlr_test_bytes_start;
	private int		dlr_test_average_bytes_per_sec = -1;
	
	private long	dlr_test_eta	= -1;
	

	public void
	setDLRInactive()
	{
		dlr_test_active = false;
	}

	public void
	setDLRActive(
		long	time )
	{
		if (rules.bDebugLog) {
			rules.log.log(
				dl.getTorrent(), LoggerChannel.LT_INFORMATION,
				"download speed test starts");
		}

		dlr_test_active = true;

		dlr_test_start_time = time;

		dl.moveTo( 1 );

		dlr_test_bytes_start = dl.getStats().getDownloaded( true );
	}

	public void
	setDLRComplete(
		long	time )
	{
		long dlr_test_bytes_end = dl.getStats().getDownloaded( true );

		long elapsed = time - dlr_test_start_time;

		if ( elapsed >= 1000 ){

			dlr_test_average_bytes_per_sec = (int)((dlr_test_bytes_end-dlr_test_bytes_start)*1000/elapsed);

			dlr_test_eta = core_dm.getStats().getSmoothedETA();
			
			if (rules.bDebugLog) {
				rules.log.log(
					dl.getTorrent(), LoggerChannel.LT_INFORMATION,
					"download speed test ends - average=" + dlr_test_average_bytes_per_sec + ", eta=" + dlr_test_eta );
			}
		}

		dlr_test_active = false;
	}

	public long
	getDLRLastTestTime()
	{
		return( dlr_test_start_time );
	}

	public int
	getDLRLastTestSpeed()
	{
		return( dlr_test_average_bytes_per_sec );
	}

	public long
	getDLRLastTestETA()
	{
		if ( dlr_test_eta == -1 ){
			
			if ( isDownloading()){
				
					// if we haven't tested yet then use current
				
				long current = core_dm.getStats().getSmoothedETA();
				 
				if ( current > 0 ){
					 
					return( current );
				}
			}
		}
		
		return( dlr_test_eta );
	}
	
	public String
	getDLRTrace()
	{
		if ( dlr_test_active ){

			return( "test in progress" );

		}else if ( dlr_test_start_time > 0 ){

			if ( dlr_test_average_bytes_per_sec >= 0 ){

				return(
					"tested; " +
					TimeFormatter.format(( SystemTime.getMonotonousTime() - dlr_test_start_time )/1000) + " ago; " +
					"rate=" + DisplayFormatters.formatByteCountToKiBEtcPerSec( dlr_test_average_bytes_per_sec ) +
					", eta=" + dlr_test_eta );

			}else{

				return(
						"tested; " +
						TimeFormatter.format(( SystemTime.getMonotonousTime() - dlr_test_start_time )/1000) + " ago; " +
						"test did not complete" );
			}
		}else{
			return( "" );
		}
	}

	public String toString() {
		return String.valueOf(dl.getSeedingRank().getRank());
	}

	/**
	 * Check Seeders for various changes not triggered by listeners
	 *
	 * @return True: something changed
	 */
	public boolean changeChecker() {
		if (getActivelySeeding()) {
			int shareRatio = dl.getStats().getShareRatio();
			int numSeeds = calcSeedsNoUs(dl,dl.getAggregatedScrapeResult( false ));

			int	activeMaxSR = dlSpecificMaxShareRatio;
			if ( activeMaxSR <= 0 ){
				activeMaxSR = iIgnoreShareRatio;
			}
			if (activeMaxSR != 0 && shareRatio >= activeMaxSR
					&& (numSeeds >= iIgnoreShareRatio_SeedStart || !lastScrapeResultOk)
					&& shareRatio != -1) {
				if (rules.bDebugLog) {
					rules.log.log(dl.getTorrent(), LoggerChannel.LT_INFORMATION,
							"somethingChanged: shareRatio changeChecker");
				}
				return true;
			}
		}

		/* READY downloads are usually waiting for a seeding torrent to
		 stop (the seeding torrent probably is within the "Minimum Seeding
		 Time" setting)

		 The rules may go through several cycles before a READY torrent is
		 processed
		 */
		if (dl.getState() == Download.ST_READY) {
			if (rules.bDebugLog)
				rules.log.log(dl.getTorrent(), LoggerChannel.LT_INFORMATION,
						"somethingChanged: Download is ready");
			return true;
		}

		if (staleCDSinceMono > 0) {
			long nowMono = SystemTime.getMonotonousTime();
			if (nowMono - lastStaleCDRefreshMono > STALE_REFRESH_INTERVAL) {
				staleCDOffset += (nowMono - lastStaleCDRefreshMono) / STALE_REFRESH_INTERVAL;
				lastStaleCDRefreshMono = nowMono;
				if (rules.bDebugLog) {
					rules.log.log(dl.getTorrent(), LoggerChannel.LT_INFORMATION,
							"somethingChanged: staleCD changeChecker");
				}
				return true;
			}
		}

		return false;
	}
	
	/*
	public void
	resetSeedingRank()
	{
		downloadSR.reset();
	}
	*/
	
	public long
	getLightSeedEligibility()
	{
		return( downloadSR.getLightSeedEligibility());
	}
	
	public boolean
	updateLightSeedEligibility(
		boolean		has_slots )
	{
		return( downloadSR.updateLightSeedEligibility( has_slots ));
	}
	
	public RankCalculatorSlotReserver
	getReservedSlot()
	{
		return( reservedSlot );
	}
	
	public void
	setReservedSlot(
		RankCalculatorSlotReserver	slot )
	{
		if ( slot != null ){
		
			if ( reservedSlot != null ){
				
				Debug.out( "hmm" );
			}
			
			reservedSlot = slot;
			
		}else{ 
			
			if ( reservedSlot == null ){
				
				Debug.out( "hmm" );
			}
			
			reservedSlot = null;			
		}
	}
	
    private static final ThreadPool	activate_pool = new ThreadPool( "StartStopRules:activate", 32, true );

	@Override
	public boolean 
	activationRequest(
		Runnable		to_do )
	{
		if ( dl.isComplete()){
						
			int peers = calcPeersNoUs();
			
			if ( peers <= 0 && downloadSR.getRank() == SR_0PEERS ){
						
					// try and kick us out of 0-peers state - we don't know how valid the activation
					// request is so need to rely on the tracker
				
				long now = SystemTime.getMonotonousTime();
					
				if ( lastActivationAnnounce == 0 || now - lastActivationAnnounce > 5*60*1000 ){
						
					if ( !activate_pool.isFull()){
					
						lastActivationAnnounce = now;
						
							// we're on a core network thread here, no blocking

						activate_pool.run( AERunnable.create(()->{
							
							dl.requestTrackerAnnounce( true );
							
							to_do.run();
							
						}));
					}
				}
				
				return( true );
			}			
		}

		return( false );	
	}
	
	private class
	SR
		implements Download.SeedingRank
	{
		private int			rank;
		private int			rankNP;
		
			// Long.MAX_VALUE = never eligible
			// -1 = currently eligible
			// other = mono time that we transitioned from eligible -> ineligible
		
		private long		light_seed_eligible = Long.MAX_VALUE;
		
		private String		activation_status = "";
		
		private
		SR()
		{
		}
		
		/* I can't think of a good reason ever to set the rank back to 0, when things change a recalculation
		 * will update as required. Setting to 0 just risks instability in sort order and associated
		 * start/stop functionality
		 
		private void
		reset()
		{
			rank	= 0;
			rankNP	= 0;
			
				// we get this relatively frequently due to config saves (not always user-invoked either)
				// don't mess with light seed eligibility as this will cause all light-seeds to be periodically
				// reset for no reason
		}
		*/
		
		private void
		update(
			int		_rank,
			int		_rankNP )
		{
			rank	= _rank;
			rankNP	= _rankNP;
			
			if ( rank != SR_0PEERS || rankNP < SR_IGNORED_LESS_THAN ){
			
					// not eligible
				
				if ( light_seed_eligible == -1 ){
				
					light_seed_eligible = SystemTime.getMonotonousTime();
				}
			}
		}
		
		private boolean
		updateLightSeedEligibility(
			boolean		has_slots )
		{
			boolean avail = 
					has_slots && 
					rank == SR_0PEERS && 
					rankNP >= SR_IGNORED_LESS_THAN && 
					TorrentUtils.getPrivate(core_dm.getTorrent());
			
			if ( avail ){
				
					// we are eligible
				
				if ( light_seed_eligible == -1 ){
				
						// already eligible, no change
					
					return( false );
				}
				
				if ( light_seed_eligible != Long.MAX_VALUE ){
				
						// bit of a grace period to stop flip-flopping
					
					long time_since_not_eligible = SystemTime.getMonotonousTime() - light_seed_eligible;
					
					if ( time_since_not_eligible < 2*60*1000 ){
						
						return( false );
					}
				}
				
				light_seed_eligible = -1;
				
				return( true );
				
			}else{
				
					// not eligible
				
				boolean result = light_seed_eligible == -1;
				
				if ( result ){
				
						// we were eligible, start timer
					
					light_seed_eligible = SystemTime.getMonotonousTime();
				}
				
				return( result );
			}
		}
		
		@Override
		public int 
		getRank()
		{
			return( rank );
		}
		
		@Override
		public long
		getLightSeedEligibility()
		{
			if ( light_seed_eligible == -1 ){
				
				return( 0 );
				
			}else if ( light_seed_eligible == Long.MAX_VALUE ){
				
				return( light_seed_eligible );
				
			}else{
			
				return( SystemTime.getMonotonousTime() - light_seed_eligible );
			}
		}
				
		@Override
		public void 
		setActivationStatus(
			String str)
		{
			activation_status = str;
		}
		
		@Override
		public String[]
		getStatus(
			boolean	verbose )
		{
			long sr = rank;
			
			String sText = "";
			if (sr >= 0) {
				if (getCachedIsFP())
					sText += MessageText.getString("StartStopRules.firstPriority") + " ";

				if (iRankType == StartStopRulesDefaultPlugin.RANK_TIMED) {
					//sText += "" + sr + " ";
					if (sr > DefaultRankCalculator.SR_TIMED_QUEUED_ENDS_AT) {
						long timeStarted = dl.getStats().getTimeStartedSeeding();
						long timeLeft;

						long lMsTimeToSeedFor = minTimeAlive;
						if (iTimed_MinSeedingTimeWithPeers > 0) {
	  					PeerManager peerManager = dl.getPeerManager();
	  					if (peerManager != null) {
	  						int connectedLeechers = peerManager.getStats().getConnectedLeechers();
	  						if (connectedLeechers > 0) {
	  							lMsTimeToSeedFor = iTimed_MinSeedingTimeWithPeers;
	  						}
	  					}
						}

						if (dl.isForceStart())
							timeLeft = Constants.CRAPPY_INFINITY_AS_INT;
						else if (timeStarted <= 0)
							timeLeft = lMsTimeToSeedFor;
						else
							timeLeft = (lMsTimeToSeedFor - (SystemTime.getCurrentTime() - timeStarted));

						sText += TimeFormatter.format(timeLeft / 1000);
					} else if (sr > 0) {
						sText += MessageText.getString("StartStopRules.waiting");
					}
				} else if (sr > 0) {
					if ( verbose ){
						sText += MessageText.getString( "SubscriptionResults.column.rank" ) + " " + sr;
					}else{
						sText += String.valueOf(sr);
					}
				}
			} else if (sr == DefaultRankCalculator.SR_FP0PEERS)
				sText = MessageText.getString("StartStopRules.FP0Peers");
			else if (sr == DefaultRankCalculator.SR_FP_SPRATIOMET)
				sText = MessageText.getString("StartStopRules.SPratioMet");
			else if (sr == DefaultRankCalculator.SR_RATIOMET)
				sText = MessageText.getString("StartStopRules.ratioMet");
			else if (sr == DefaultRankCalculator.SR_NUMSEEDSMET)
				sText = MessageText.getString("StartStopRules.numSeedsMet");
			else if (sr == DefaultRankCalculator.SR_NOTQUEUED)
				sText = "";
			else if (sr == DefaultRankCalculator.SR_0PEERS)
				sText = MessageText.getString("StartStopRules.0Peers");
			else if (sr == DefaultRankCalculator.SR_SHARERATIOMET)
				sText = MessageText.getString("StartStopRules.shareRatioMet");
			else if (sr == DefaultRankCalculator.SR_NOSCRAPE)
				sText = MessageText.getString("StartStopRules.noScrape");
			else {
				sText = "ERR" + sr;
			}
			// Add a Star if it's before minTimeAlive
			if (SystemTime.getCurrentTime() - dl.getStats().getTimeStartedSeeding() < minTimeAlive){
				if ( verbose ){
				
					sText = "< " + MessageText.getString( "ConfigView.label.minSeedingTime" ) + "; " + sText;
				}else{
					sText = "* " + sText;
				}
			}
			
			String ls_str 	= "Light-Seeding eligible";
			
			if ( activation_status != null && !activation_status.isEmpty()){
				
				ls_str += ": " + activation_status;
			}
			
			String has_slot = "Light-Seeding active";
			
			if ( verbose ){
				if ( downloadSR.getLightSeedEligibility() == 0 ){
					sText += "\n" + ls_str;
				}
				if ( reservedSlot != null ){
					sText += "\n" + has_slot;
				}
			}
			
			String tt;
			
			if (rules.bDebugLog) {
				tt = 	"FP:\n" + _sExplainFP + "\n" + 
						"SR:" + _sExplainSR + 
						(verbose||downloadSR.getLightSeedEligibility()!=0?"":(ls_str+"\n"))+ 
						(verbose||reservedSlot==null?"":(has_slot+"\n"))+ "\n" +
						"TRACE:\n" + sTrace +
						"  Free Slots: " + rules.getSlotStatus();
			}else{
				tt = null;
			}
			
			return( new String[]{ sText, tt });
		}
	}
	
	@Override
	public void 
	setUserData(
		Object key, 
		Object value) 
	{
		synchronized( this ){
			
			if ( userData == null ){
				
				userData = new LightHashMap<>(2);
			}
			
			userData.put( key,  value );
		}
	}
	
	@Override
	public Object 
	getUserData(
		Object key)
	{
		synchronized( this ){
			
			if ( userData == null ){
				
				return( null );
			}
			
			return( userData.get( key ));
		}
	}
}