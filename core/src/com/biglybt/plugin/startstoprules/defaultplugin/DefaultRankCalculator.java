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

import java.util.Iterator;
import java.util.List;

import com.biglybt.core.config.COConfigurationListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.download.DownloadManagerStateAttributeListener;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.TimeFormatter;
import com.biglybt.pif.PluginConfig;
import com.biglybt.pif.download.Download;
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
public class DefaultRankCalculator implements DownloadManagerStateAttributeListener, Comparable {
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

	/**
	 * For loading config settings
	 */
	private static COConfigurationListener configListener = null;

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

	public static final String[] SR_NEGATIVE_DEBUG = {
		"?",
		"Not Qd",
		"FP SPRatioMet",
		"Ratio Met",
		"# CDs Met",
		"FP 0 Peers",
		"0 Peers",
		"Share Ratio Met"
	};

	private static final long STALE_REFRESH_INTERVAL = 1000 * 60;

	//
	// Static config values

	/** Ranking System to use */
	protected static int iRankType = -1;

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

	private static int iFirstPriorityIgnoreIdleHours;

	private static long minTimeAlive;

	private static boolean bAutoStart0Peers;

	private static int iTimed_MinSeedingTimeWithPeers;

	//
	// Class variables

	protected final Download dl;
	private final DownloadManager	core_dm;
	private boolean bActivelyDownloading;

	private long lDLActivelyChangedOn;

	private boolean bActivelySeeding;

	private long lCDActivelyChangedOn;

	private long staleCDSince;

	private long staleCDOffset;

	private long lastStaleCDRefresh;

	private boolean bIsFirstPriority;

	private int	dlSpecificMinShareRatio;
	private int	dlSpecificMaxShareRatio;

	private long dlLastActiveTime;

	/** Public for tooltip to access it */
	public String sExplainFP = "";

	/** Public for tooltip to access it */
	public String sExplainSR = "";

	/** Public for tooltip to access it */
	public String sTrace = "";

	private AEMonitor downloadData_this_mon = new AEMonitor(
			"StartStopRules:downloadData");

	private final StartStopRulesDefaultPlugin rules;



	// state-caches for sorting

	int lastModifiedScrapeResultPeers = 0;
	int lastModifiedScrapeResultSeeds = 0;
	int lastModifiedShareRatio = 0;
	// modified by a listener in StartStopRulesDefaultPlugin
	boolean lastScrapeResultOk = false;

	/**
	 * Default Initializer
	 *
	 * @param _rules
	 * @param _dl
	 */
	public DefaultRankCalculator(StartStopRulesDefaultPlugin _rules, Download _dl) {
		rules = _rules;
		dl = _dl;

		core_dm = PluginCoreUtils.unwrap( dl );

		DownloadManagerState dm_state = core_dm.getDownloadState();

		dlSpecificMinShareRatio = dm_state.getIntParameter( DownloadManagerState.PARAM_MIN_SHARE_RATIO );
		dlSpecificMaxShareRatio = dm_state.getIntParameter( DownloadManagerState.PARAM_MAX_SHARE_RATIO );
		dlLastActiveTime = dm_state.getLongParameter( DownloadManagerState.PARAM_DOWNLOAD_LAST_ACTIVE_TIME);
		if (dlLastActiveTime <= 0) {
			dlLastActiveTime = dm_state.getLongParameter(DownloadManagerState.PARAM_DOWNLOAD_COMPLETED_TIME);
		}

		dm_state.addListener( this, DownloadManagerState.AT_PARAMETERS, DownloadManagerStateAttributeListener.WRITTEN );

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

	protected void
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
		iFirstPriorityIgnoreIdleHours = cfg.getUnsafeIntParameter(PREFIX
				+ "iFirstPriority_ignoreIdleHours");
		iTimed_MinSeedingTimeWithPeers = cfg.getUnsafeIntParameter(PREFIX
				+ "iTimed_MinSeedingTimeWithPeers") * 1000;
	}

	/** Sort first by SeedingRank Descending, then by Position Ascending.
	 */
	@Override
	public int compareTo(Object obj) {
		if (!(obj instanceof DefaultRankCalculator)) {
			return -1;
		}

		DefaultRankCalculator dlData = (DefaultRankCalculator) obj;

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
		int value = dlData.dl.getSeedingRank() - dl.getSeedingRank();
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

	public Download getDownloadObject() {
		return dl;
	}

	public DownloadManager getCoreDownloadObject(){
		return( core_dm );
	}
	
	public boolean isForceActive() {
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
	
	/**
	 * Retrieves whether the torrent is "actively" downloading
	 *
	 * @return true: actively downloading
	 */
	public boolean getActivelyDownloading() {
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
			bIsActive = (stats.getDownloadAverage() >= minSpeedForActiveDL);

			if (bActivelyDownloading != bIsActive) {
				long now = SystemTime.getCurrentTime();
				// Change
				if (lDLActivelyChangedOn == -1) {
					// Start Timer
					lDLActivelyChangedOn = now;
					bIsActive = !bIsActive;
				} else if (now - lDLActivelyChangedOn < ACTIVE_CHANGE_WAIT) {
					// Continue as old state until timer finishes
					bIsActive = !bIsActive;
				}
			} else {
				// no change, reset timer
				lDLActivelyChangedOn = -1;
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
	public boolean getActivelySeeding() {
		boolean bIsActive = false;
		DownloadStats stats = dl.getStats();
		int state = dl.getState();
		// Timed torrents don't use a speed threshold, since they are based on time!
		// However, First Priorities need to be checked for activity so that
		// timed ones can start when FPs are below threshold.  Ditto for 0 Peers
		// when bAutoStart0Peers
		if (iRankType == StartStopRulesDefaultPlugin.RANK_TIMED
				&& !isFirstPriority()
				&& !(bAutoStart0Peers && rules.calcPeersNoUs(dl,dl.getAggregatedScrapeResult( false )) == 0 && lastScrapeResultOk)) {
			bIsActive = (state == Download.ST_SEEDING);

		} else if (state != Download.ST_SEEDING
				|| (bAutoStart0Peers && rules.calcPeersNoUs(dl,dl.getAggregatedScrapeResult( false )) == 0)) {
			// Not active if we aren't seeding
			// Not active if we are AutoStarting 0 Peers, and peer count == 0
			bIsActive = false;
			staleCDSince = -1;
		} else if (SystemTime.getCurrentTime() - stats.getTimeStarted() <= FORCE_ACTIVE_FOR) {
			bIsActive = true;
			staleCDSince = -1;
		} else {
			bIsActive = (stats.getUploadAverage() >= minSpeedForActiveSeeding);

			if (bActivelySeeding != bIsActive) {
				long now = SystemTime.getCurrentTime();
				// Change
				if (lCDActivelyChangedOn < 0) {
					// Start Timer
					lCDActivelyChangedOn = now;
					bIsActive = !bIsActive;
				} else if (now - lCDActivelyChangedOn < ACTIVE_CHANGE_WAIT) {
					// Continue as old state until timer finishes
					bIsActive = !bIsActive;
				}

				if (bActivelySeeding != bIsActive) {
  				if (bIsActive) {
  					staleCDSince = -1;
  					staleCDOffset = 0;
  				} else {
  					staleCDSince = System.currentTimeMillis();
  				}
				}

			} else {
				// no change, reset timer
				lCDActivelyChangedOn = -1;
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

	/** Assign Seeding Rank based on RankType
	 * @return New Seeding Rank Value
	 */
	public int recalcSeedingRank() {
		try {
			downloadData_this_mon.enter();

			int	oldSR = dl.getSeedingRank();

			int newSR = _recalcSeedingRankSupport( oldSR );

			if ( newSR != oldSR ){

				dl.setSeedingRank( newSR );
			}
			return( newSR );

		} finally {

			downloadData_this_mon.exit();
		}
	}

	private int _recalcSeedingRankSupport( int oldSR ) {

		sExplainSR = "";

		DownloadStats stats = dl.getStats();

		int newSR = 0;

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
		DownloadScrapeResult sr = dl.getAggregatedScrapeResult( false );
		lastModifiedScrapeResultPeers = rules.calcPeersNoUs(dl,sr);
		lastModifiedScrapeResultSeeds = rules.calcSeedsNoUs(dl,sr);

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

				return SR_SHARERATIOMET;
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
				if (bIgnore0Peers) {
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
			return newSR;
		}

		if (iRankType == StartStopRulesDefaultPlugin.RANK_TIMED) {
			if (bIsFirstPriority) {
				newSR += SR_TIMED_QUEUED_ENDS_AT + 1;
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
						rules.requestProcessCycle(null);
						if (rules.bDebugLog)
							rules.log.log(dl.getTorrent(), LoggerChannel.LT_INFORMATION,
									"somethingChanged: TimeUp");
					}
				} else {
					newSR = SR_TIMED_QUEUED_ENDS_AT + 1 + (int) (lMsElapsed / 1000);
					if (oldSR <= SR_TIMED_QUEUED_ENDS_AT) {
						rules.requestProcessCycle(null);
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
			if ( iRankType == StartStopRulesDefaultPlugin.RANK_PEERCOUNT )
			{
				if(lastModifiedScrapeResultPeers > lastModifiedScrapeResultSeeds * 10)
					newSR = 100 * lastModifiedScrapeResultPeers * 10;
				else
					newSR = (int)((long)100 * lastModifiedScrapeResultPeers * lastModifiedScrapeResultPeers/(lastModifiedScrapeResultSeeds+1));
			}
			else if ((iRankType == StartStopRulesDefaultPlugin.RANK_SEEDCOUNT)
					&& (iRankTypeSeedFallback == 0 || iRankTypeSeedFallback > lastModifiedScrapeResultSeeds))
			{
				if (lastModifiedScrapeResultSeeds < 10000)
					newSR = 10000 - lastModifiedScrapeResultSeeds;
				else
					newSR = 1;
				// shift over to make way for fallback
				newSR *= SEEDONLY_SHIFT;

			} else { // iRankType == RANK_SPRATIO or we are falling back
				if (lastModifiedScrapeResultPeers != 0) {
					if (lastModifiedScrapeResultSeeds == 0) {
						if (lastModifiedScrapeResultPeers >= minPeersToBoostNoSeeds)
							newSR += SPRATIO_BASE_LIMIT;
					} else { // numSeeds != 0 && numPeers != 0
						float x = (float) lastModifiedScrapeResultSeeds / lastModifiedScrapeResultPeers;
						newSR += SPRATIO_BASE_LIMIT / ((x + 1) * (x + 1));
					}
				}
			}
		} else {
			if (rules.bDebugLog)
				sExplainSR += "  Can't calculate SR, no scrape results\n";
		}

		if (staleCDOffset > 0) {
			// every 10 minutes of not being active, subtract one SR
			if (newSR > staleCDOffset) {
				newSR -= staleCDOffset;
				sExplainSR += "  subtracted " + staleCDOffset + " due to non-activeness\n";
			} else {
				staleCDOffset = 0;
			}
		}

		if (newSR < 0)
			newSR = 1;

		return newSR;
	}

	/** Does the torrent match First Priority criteria?
	 * @return FP State
	 */
	public boolean isFirstPriority() {
		
		boolean bFP = pisFirstPriority( false );

		if ( rules.getTagFP()){
			
			rules.setFPTagStatus( core_dm, pisFirstPriority( true ));
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

	private boolean pisFirstPriority( boolean is_test ) {
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

		if ( !is_test ) {
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
		if (lastModifiedScrapeResultPeers == 0 && lastScrapeResultOk && bFirstPriorityIgnore0Peer) {
			if (rules.bDebugLog)
				sExplainFP += "Not FP: 0 peers\n";
			return false;
		}

		if (iFirstPriorityIgnoreIdleHours > 0) {
			long lastUploadSecs = dl.getStats().getSecondsSinceLastUpload();
			if (lastUploadSecs < 0) {
				lastUploadSecs = dl.getStats().getSecondsOnlySeeding();
			}
			if (lastUploadSecs > 60 * 60 * (long)iFirstPriorityIgnoreIdleHours) {
				if (rules.bDebugLog)
					sExplainFP += "Not FP: " + lastUploadSecs + "s > "
							+ iFirstPriorityIgnoreIdleHours + "h of no upload\n";
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
		return String.valueOf(dl.getSeedingRank());
	}

	/**
	 * Check Seeders for various changes not triggered by listeners
	 *
	 * @return True: something changed
	 */
	public boolean changeChecker() {
		if (getActivelySeeding()) {
			int shareRatio = dl.getStats().getShareRatio();
			int numSeeds = rules.calcSeedsNoUs(dl,dl.getAggregatedScrapeResult( false ));

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

		if (staleCDSince > 0) {
			long now = SystemTime.getCurrentTime();
			if (now - lastStaleCDRefresh > STALE_REFRESH_INTERVAL) {
				staleCDOffset += (now - lastStaleCDRefresh) / STALE_REFRESH_INTERVAL;
				lastStaleCDRefresh = now;
				if (rules.bDebugLog) {
					rules.log.log(dl.getTorrent(), LoggerChannel.LT_INFORMATION,
							"somethingChanged: staleCD changeChecker");
				}
				return true;
			}
		}

		return false;
	}
}
