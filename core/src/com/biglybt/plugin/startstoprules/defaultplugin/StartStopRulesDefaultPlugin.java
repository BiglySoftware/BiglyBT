/*
 * File    : StartStopRulesDefaultPlugin.java
 * Created : 12-Jan-2004
 * By      : TuxPaper
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.plugin.startstoprules.defaultplugin;

import java.util.*;

import com.biglybt.core.config.COConfigurationListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.download.DownloadManagerStateAttributeListener;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagFeatureProperties;
import com.biglybt.core.tag.TagManager;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.tag.TagType;
import com.biglybt.core.tag.TagTypeAdapter;
import com.biglybt.core.tag.Taggable;
import com.biglybt.core.tag.TagFeatureProperties.TagProperty;
import com.biglybt.core.tag.TagFeatureRateLimit;
import com.biglybt.core.tag.TagListener;
import com.biglybt.core.util.*;
import com.biglybt.core.util.average.AverageFactory;
import com.biglybt.pif.Plugin;
import com.biglybt.pif.PluginConfig;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.PluginListener;
import com.biglybt.pif.disk.DiskManagerFileInfo;
import com.biglybt.pif.download.*;
import com.biglybt.pif.logging.LoggerChannel;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.UIManagerListener;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pif.ui.tables.*;
import com.biglybt.pifimpl.local.PluginCoreUtils;

/** Handles Starting and Stopping of torrents.
 *
 * TODO: RANK_TIMED is quite a hack and is spread all over.  It needs to be
 *       redone, probably with a timer on each seeding torrent which triggers
 *       when time is up and it needs to stop.
 *
 * BUG: When "AutoStart 0 Peers" is on, and minSpeedForActivelySeeding is
 *      enabled, the 0 peer torrents will continuously switch from seeding to
 *      queued, probably due to the connection attempt registering speed.
 *      This might be fixed by the "wait XX ms before switching active state"
 *      code.
 *
 * Other Notes:
 * "CD" is often used to refer to "Seed" or "Seeding", because "C" sounds like
 * "See"
 */
public class StartStopRulesDefaultPlugin implements Plugin,
		COConfigurationListener, AEDiagnosticsEvidenceGenerator
{
	// for debugging
	private static final String sStates = " WPRDS.XEQ";

	/** Do not rank completed torrents */
	public static final int RANK_NONE = 0;

	/** Rank completed torrents using Seeds:Peer Ratio */
	public static final int RANK_SPRATIO = 1;

	/** Rank completed torrents using Seed Count method */
	public static final int RANK_SEEDCOUNT = 2;

	/** Rank completed torrents using a timed rotation of minTimeAlive */
	public static final int RANK_TIMED = 3;

	/** Rank completed torrents using the peers count, weighted by the seeds to peers ratio */
	public static final int RANK_PEERCOUNT = 4;

	/**
	 * Force at least one check every period of time (in ms).
	 * Used in ChangeFlagCheckerTask
	 */
	private static final int FORCE_CHECK_PERIOD = 60000;

	/**
	 * Check for non triggerable changes ever period of time (in ms)
	 */
	private static final int CHECK_FOR_GROSS_CHANGE_PERIOD = 30000;

	/**
	 * Interval in ms between checks to see if the {@link #somethingChanged}
	 * flag changed
	 */
	private static final int PROCESS_CHECK_PERIOD = 1500;

	/** Wait xx ms before starting completed torrents (so scrapes can come in) */
	private static final int MIN_SEEDING_STARTUP_WAIT = 20000;

	/** Wait at least xx ms for first scrape, before starting completed torrents */
	private static final int MIN_FIRST_SCRAPE_WAIT = 90000;

	private static final float IGNORE_SLOT_THRESHOLD_FACTOR = 0.9f;

	private static final int MIN_DOWNLOADING_STARTUP_WAIT 	= 30*1000;

	private static final int SMOOTHING_PERIOD_SECS 	= 15;
	private static final int SMOOTHING_PERIOD 		= SMOOTHING_PERIOD_SECS*1000;

	private static final Object DEBUG_LINE_KEY = new Object();
	
	private TagManager tag_manager;
		
	private volatile boolean tagsHaveDLorCDLimits;
	
	private com.biglybt.core.util.average.Average globalDownloadSpeedAverage = AverageFactory.MovingImmediateAverage(SMOOTHING_PERIOD/PROCESS_CHECK_PERIOD );

	// Core/Plugin classes
	private AEMonitor this_mon = new AEMonitor("StartStopRules");

	private PluginInterface pi;

	protected PluginConfig plugin_config;

	private DownloadManager download_manager;

	protected LoggerChannel log;
	
	private final boolean ENABLE_DLOG = false;
	protected LoggerChannel dlog;

	/** Used only for RANK_TIMED. Recalculate ranks on a timer */
	private RecalcSeedingRanksTask recalcSeedingRanksTask;

	/** Map to relate downloadData to a Download OR reserved slot  */
	private static Map<Object, DefaultRankCalculator> rankCalculatorMap = Collections.synchronizedMap(new HashMap<>());

	/**
	 * this is used to reduce the number of comperator invocations
	 * by keeping a mostly sorted copy around, must be nulled whenever the map is changed
	 */
	private volatile DefaultRankCalculator[] sortedArrayCache;

	private volatile boolean closingDown;

	private volatile boolean somethingChanged;

	private Set<DefaultRankCalculator> ranksToRecalc = new LightHashSet();

	private AEMonitor ranksToRecalc_mon = new AEMonitor("ranksToRecalc");

	/** When rules class started.  Used for initial waiting logic */
	private long monoStartedOn;

	// Config Settings
	/** Whether Debug Info is written to the log and tooltip */
	protected boolean bDebugLog;

	/** Ranking System to use.  One of RANK_* constants */
	private int iRankType = -1;

	private int minSpeedForActiveSeeding;

	/** Maximum # of stalled torrents that are in seeding mode */
	private int maxStalledSeeding;
	private int maxOverLimitSeeding;
	private boolean stalledSeedingIgnoreZP;
	
	private int _maxActive;

	private boolean _maxActiveWhenSeedingEnabled;

	private int _maxActiveWhenSeeding;

	private int globalDownloadLimit;
	private int globalUploadLimit;
	private int globalUploadWhenSeedingLimit;

	private int maxConfiguredDownloads;
	private boolean bMaxDownloadIgnoreChecking;

	private int minDownloads;

	private boolean bAutoReposition;

	private long minTimeAlive;

	private boolean bAutoStart0Peers;

	private boolean bStopOnceBandwidthMet = false;

	private boolean bStartNoMoreSeedsWhenUpLimitMet			= false;
	private boolean	bStartNoMoreSeedsWhenUpLimitMetPercent	= true;
	private int		bStartNoMoreSeedsWhenUpLimitMetSlack	= 95;

		// downloading params

	//private boolean	bDownloadAutoReposition;

	private int		iDownloadSortType;
	private int		iDownloadTestTimeMillis;
	private int		iDownloadReTestMillis;
	private boolean bDownloadTestActive;
	
	private boolean	bTagFirstPriority;
	
	private static boolean bAlreadyInitialized = false;

	// UI
	private TableContextMenuItem debugMenuItem = null;

	private UIAdapter	swt_ui;

	private CopyOnWriteList listenersFP = new CopyOnWriteList();

	public static boolean pauseChangeFlagChecker = false;

	private Tag		fp_tag;
	
	private volatile int							numReservedSeedingSlots	= 0;
	private LinkedList<RankCalculatorSlotReserver>	reservedSlots 			= new LinkedList<>();
	private Set<DefaultRankCalculator>				reservedSlotsAllocated 	= new IdentityHashSet<>();
	
	private String slotStatus = "";
	
	public static void
	load(
		PluginInterface		plugin_interface )
	{
		plugin_interface.getPluginProperties().setProperty("plugin.version", "1.0");
		plugin_interface.getPluginProperties().setProperty("plugin.name", "Start/Stop Rules");
	}

	@Override
	public void initialize(PluginInterface _plugin_interface) {
		if (bAlreadyInitialized) {
			System.err.println("StartStopRulesDefaultPlugin Already initialized!!");
		} else {
			bAlreadyInitialized = true;
		}

		AEDiagnostics.addWeakEvidenceGenerator(this);

		monoStartedOn = SystemTime.getMonotonousTime();

		pi = _plugin_interface;

		plugin_config = pi.getPluginconfig();

		plugin_config.setPluginConfigKeyPrefix("");

		download_manager = pi.getDownloadManager();

		tag_manager  = TagManagerFactory.getTagManager();
		
		if ( tag_manager != null && tag_manager.isEnabled()){
			
			Map<TagFeatureRateLimit,Integer>	tags_with_limit	= new IdentityHashMap<>();

			TagType tt = tag_manager.getTagType( TagType.TT_DOWNLOAD_MANUAL );
			
			TagListener tag_listener =
				new TagListener()
				{
					public void
					taggableAdded(
						Tag			tag,
						Taggable	tagged )
					{
						requestProcessCycle( null );
					}
	
					public void
					taggableSync(
						Tag			tag )
					{
					}
	
					public void
					taggableRemoved(
						Tag			tag,
						Taggable	tagged )
					{
						requestProcessCycle( null );
					}
				};
				
			tt.addTagTypeListener(
				new TagTypeAdapter()
				{
					public void
					tagAdded(
						Tag			tag )
					{
						if ( tag instanceof TagFeatureRateLimit ){
						
							TagFeatureRateLimit t = (TagFeatureRateLimit)tag;
							
							int max = t.getMaxActiveDownloads() + t.getMaxActiveSeeds();
							
							if ( max > 0 ){
									
								synchronized( tags_with_limit ){
								
									tags_with_limit.put( t, max );
									
									tag.addTagListener( tag_listener, false );
									
									tagsHaveDLorCDLimits = true;
								}
								
								requestProcessCycle( null );
							}
						}
					}

					public void
					tagChanged(
						Tag			tag )
					{
						if ( tag instanceof TagFeatureRateLimit ){
							
							TagFeatureRateLimit t = (TagFeatureRateLimit)tag;
							
							int max = t.getMaxActiveDownloads() + t.getMaxActiveSeeds();

							if ( max > 0 ){
								
								boolean changed = false;
								
								synchronized( tags_with_limit ){
								
									Integer old = tags_with_limit.get( t );
									
									if ( old == null ){
										
										tags_with_limit.put( t, max );
										
										tag.addTagListener( tag_listener, false );
										
										tagsHaveDLorCDLimits = true;
										
									}else if ( old != max ){
										
										tags_with_limit.put( t, max );
									}
								}
								
								if ( changed ){
									
									requestProcessCycle( null );
								}
							}else{
							
								boolean removed = false;
								
								synchronized( tags_with_limit ){
								
									if ( tags_with_limit.remove( t ) != null ){
									
										tag.removeTagListener( tag_listener );
										
										if ( tags_with_limit.isEmpty()){
											
											tagsHaveDLorCDLimits = false;
										}
										
										removed = true;
									}
								}
								
								if ( removed ){
									
									requestProcessCycle( null );
								}
							}
						}
					}

					public void
					tagRemoved(
						Tag			tag )
					{
						if ( tag instanceof TagFeatureRateLimit ){
							
							boolean removed = false;
							
							synchronized( tags_with_limit ){
								
								if ( tags_with_limit.remove((TagFeatureRateLimit)tag ) != null ){
								
									tag.removeTagListener( tag_listener );
									
									if ( tags_with_limit.isEmpty()){
										
										tagsHaveDLorCDLimits = false;
									}
									
									removed = true;
								}
							}
							
							if ( removed ){
								
								requestProcessCycle( null );
							}
						}
					}
				},
				true );
		}
		// Create a configModel for StartStopRules
		// We always need to do this in order to set up configuration defaults

		new StartStopConfigModel(pi);

		pi.addListener(new PluginListener() {
			@Override
			public void initializationComplete() {}

			@Override
			public void closedownInitiated() {
				closingDown = true;

				// we don't want to go off recalculating stuff when config is saved
				// on closedown
				COConfigurationManager.removeListener(StartStopRulesDefaultPlugin.this);
			}

			@Override
			public void closedownComplete() { /* not implemented */
			}
		});
		
		Runnable r = new Runnable() {
			@Override
			public void run() {
				download_manager.addListener(new StartStopDMListener());
				SimpleTimer.addPeriodicEvent("StartStop:gross",
						CHECK_FOR_GROSS_CHANGE_PERIOD, new ChangeCheckerTimerTask());
				SimpleTimer.addPeriodicEvent("StartStop:check",
						PROCESS_CHECK_PERIOD, new ChangeFlagCheckerTask());
			}
		};

		pi.getUtilities().createDelayedTask(r).queue();

		log = pi.getLogger().getTimeStampedChannel("StartStopRules");
		log.log(LoggerChannel.LT_INFORMATION,
				"Default StartStopRules Plugin Initialisation");

		if ( ENABLE_DLOG ){
			dlog = pi.getLogger().getTimeStampedChannel("SSR_Debug");
			dlog.setDiagnostic();
			dlog.setForce( true );
		}
		
		COConfigurationManager.addListener(this);

		try {
			pi.getUIManager().createLoggingViewModel(log, true);
			pi.getUIManager().addUIListener(new UIManagerListener() {
				@Override
				public void UIAttached(UIInstance instance) {
					TableManager tm = pi.getUIManager().getTableManager();

					String[] tables1 = { TableManager.TABLE_MYTORRENTS_COMPLETE, TableManager.TABLE_MYTORRENTS_ALL_SMALL };
					
					for ( String table: tables1 ){
						
						TableColumn seedingRankColumn = tm.createColumn( table, "SeedingRank");
						
						seedingRankColumn.initialize(TableColumn.ALIGN_TRAIL, TableColumn.POSITION_LAST, 80, TableColumn.INTERVAL_LIVE);
	
						TableCellRefreshListener columnListener = new SeedingRankColumnListener();
						
						seedingRankColumn.addCellRefreshListener(columnListener);
	
						tm.addColumn(seedingRankColumn);
					}
					
					String[] tables2 = { TableManager.TABLE_MYTORRENTS_INCOMPLETE, TableManager.TABLE_MYTORRENTS_ALL_SMALL };
					
					for ( String table: tables2 ){
						
						TableColumn downloadingRankColumn = tm.createColumn( table, "DownloadingRank");
	
						downloadingRankColumn.setMinimumRequiredUserMode( 1 );
	
						downloadingRankColumn.initialize(TableColumn.ALIGN_TRAIL, TableColumn.POSITION_INVISIBLE, 80, TableColumn.INTERVAL_LIVE);
	
						TableCellRefreshListener columnListener = new DownloadingRankColumnListener( StartStopRulesDefaultPlugin.this );
	
						downloadingRankColumn.addCellRefreshListener(columnListener);
	
						tm.addColumn( downloadingRankColumn );
					}

					if ( instance.getUIType().equals(UIInstance.UIT_SWT) ){

						try{
							swt_ui = (UIAdapter)Class.forName("com.biglybt.plugin.startstoprules.defaultplugin.ui.swt.StartStopRulesDefaultPluginSWTUI").newInstance();

						}catch( Throwable e ){

							Debug.out( e );
						}
					}
				}

				@Override
				public void UIDetached(UIInstance instance) {

				}
			});
		} catch (Throwable e) {
			Debug.printStackTrace(e);
		}
		reloadConfigParams();
	}

	public static DefaultRankCalculator getRankCalculator(Download dl) {
		return rankCalculatorMap.get(dl);
	}

	public TagManager
	getTagManager()
	{
		return( tag_manager );
	}
	
	public boolean 
	hasTagDLorCDLimits()
	{
		return( tagsHaveDLorCDLimits );
	}
	
	private void recalcAllSeedingRanks() {
		if (closingDown) {
			return;
		}

		try {
			this_mon.enter();

			DefaultRankCalculator[] dlDataArray;
			synchronized (rankCalculatorMap) {
				dlDataArray = rankCalculatorMap.values().toArray(
						new DefaultRankCalculator[0]);
			}

			// Check Group #1: Ones that always should run since they set things
			
			for (int i = 0; i < dlDataArray.length; i++) {
				
				dlDataArray[i].recalcSeedingRank();
			}
		} finally {

			this_mon.exit();
		}
	}

	/** A simple timer task to recalculate all seeding ranks.
	 */
	private class RecalcSeedingRanksTask implements TimerEventPerformer
	{
		boolean bCancel = false;

		@Override
		public void perform(TimerEvent event) {
			if (bCancel) {
				event.cancel();
				return;
			}
			// System.out.println("RecalcAllSeedingRanks");
			recalcAllSeedingRanks();
		}

		/**
		 *
		 */
		public void cancel() {
			bCancel = true;
		}
	}

	/** This class check if the somethingChanged flag and call process() when
	 * its set.  This allows pooling of changes, thus cutting down on the number
	 * of sucessive process() calls.
	 */
	private class ChangeFlagCheckerTask implements TimerEventPerformer
	{
		final long FORCE_CHECK_CYCLES = FORCE_CHECK_PERIOD / PROCESS_CHECK_PERIOD;

		final DownloadManagerStats dmStats = download_manager.getStats();

		long  prevReceived = -1;

		long cycleNo = 0;

		@Override
		public void perform(TimerEvent event) {

			long recv = dmStats.getDataBytesReceived() + dmStats.getProtocolBytesReceived();

			if ( prevReceived != -1 ){

				globalDownloadSpeedAverage.update( recv - prevReceived );
			}

			prevReceived = recv;

			if (closingDown || pauseChangeFlagChecker ) {
				return;
			}

			cycleNo++;
			if (cycleNo > FORCE_CHECK_CYCLES) {
				if (bDebugLog) {
					log.log(LoggerChannel.LT_INFORMATION, ">>force process");
				}
				somethingChanged = true;
			}

			if (somethingChanged) {
				try {
					cycleNo = 0;
					process();
				} catch (Exception e) {
					Debug.printStackTrace(e);
				}
			}
		}
	}

	private volatile boolean immediateProcessingScheduled = false;

	/** Listen to Download changes and recalc SR if needed
	 */
	private class StartStopDownloadListener implements DownloadListener
	{
		@Override
		public void stateChanged(Download download, int old_state, int new_state) {
			DefaultRankCalculator dlData = rankCalculatorMap.get(download);

			if (dlData != null) {
				// force a SR recalc, so that it gets position properly next process()
				requestProcessCycle(dlData);
				if ((new_state == Download.ST_READY || new_state == Download.ST_WAITING)) {
					if (immediateProcessingScheduled) {
						requestProcessCycle(dlData);
					} else {
						immediateProcessingScheduled = true;
						new AEThread2("processReady", true) {
							@Override
							public void run() {
								process();
							}
						}.start();
					}
				}

				if (bDebugLog)
					log.log(dlData.getRelatedTo(), LoggerChannel.LT_INFORMATION,
							"somethingChanged: stateChange from " + sStates.charAt(old_state)
									+ " (" + old_state + ") to " + sStates.charAt(new_state)
									+ " (" + new_state + ")");
			}
		}

		@Override
		public void positionChanged(Download download, int oldPosition,
		                            int newPosition) {
			DefaultRankCalculator dlData = rankCalculatorMap.get(download);
			if (dlData != null) {
				requestProcessCycle(dlData);
				if (bDebugLog)
					log.log(dlData.getRelatedTo(), LoggerChannel.LT_INFORMATION,
							"somethingChanged: positionChanged from " + oldPosition + " to "
									+ newPosition);
			}
		}
	}
	
	private class StartStopDownloadStateAttributeListener implements DownloadManagerStateAttributeListener
	{
		@Override
		public void attributeEventOccurred(com.biglybt.core.download.DownloadManager download, String attribute,
				int event_type){
			DefaultRankCalculator dlData = rankCalculatorMap.get(PluginCoreUtils.wrap( download ));
			
			if ( dlData != null ){
				requestProcessCycle( dlData );
			}
		}
	}

	/** Update SeedingRank when a new scrape result comes in.
	 */
	private class StartStopDMTrackerListener implements DownloadTrackerListener
	{
		@Override
		public void scrapeResult(DownloadScrapeResult result) {
			Download dl = result.getDownload();
			if ( dl == null ){
				return;
			}

			DefaultRankCalculator dlData = rankCalculatorMap.get(dl);

			// Skip if error (which happens when listener is first added and the
			// torrent isn't scraped yet)
			if (result.getResponseType() == DownloadScrapeResult.RT_ERROR) {
				if (bDebugLog)
					log.log(dl.getTorrent(), LoggerChannel.LT_INFORMATION,
							"Ignored somethingChanged: new scrapeResult (RT_ERROR)");
				if (dlData != null)
					dlData.scrapeReceived( result );
				return;
			}

			if (dlData != null) {
				dlData.scrapeReceived( result );
				requestProcessCycle(dlData);
				if (bDebugLog)
					log.log(dl.getTorrent(), LoggerChannel.LT_INFORMATION,
							"somethingChanged: new scrapeResult S:" + result.getSeedCount()
									+ ";P:" + result.getNonSeedCount());
			}
		}

		@Override
		public void announceResult(DownloadAnnounceResult result) {
			// Announces are useless to us.  Even if the announce contains seed/peer
			// count, they are not stored in the DownloadAnnounceResult.  Instead,
			// they are passed off to the DownloadScrapeResult, and a scrapeResult
			// is triggered
		}
	}

	private class StartStopDownloadActivationListener implements
			DownloadActivationListener
	{
		@Override
		public boolean activationRequested(DownloadActivationEvent event) {
			//System.out.println("StartStop: activation request: count = "
			//		+ event.getActivationCount());
			
			Download download = event.getDownload();
			
			DefaultRankCalculator dlData = rankCalculatorMap.get(download);

			if (bDebugLog) {
				log.log(download, LoggerChannel.LT_INFORMATION, ">> somethingChanged: ActivationRequest");
			}

			if ( dlData != null ){
			
				if ( dlData.activationRequest(()->{requestProcessCycle( dlData );})){
					
					requestProcessCycle( dlData );

					return( true );
				}
			}
				
			return( false );
		}
	}

	/* Create/Remove downloadData object when download gets added/removed.
	 * RecalcSeedingRank & process if necessary.
	 */
	private class StartStopDMListener implements DownloadManagerListener
	{
		private DownloadTrackerListener download_tracker_listener;

		private DownloadListener download_listener;

		private DownloadActivationListener download_activation_listener;

		private StartStopDownloadStateAttributeListener download_state_attribute_listener;
		
		public StartStopDMListener() {
			download_tracker_listener = new StartStopDMTrackerListener();
			download_listener = new StartStopDownloadListener();
			download_activation_listener = new StartStopDownloadActivationListener();
			download_state_attribute_listener = new StartStopDownloadStateAttributeListener();
		}

		@Override
		public void downloadAdded(Download download) {
			DefaultRankCalculator dlData = null;
			if (rankCalculatorMap.containsKey(download)) {
				dlData = rankCalculatorMap.get(download);
			} else {
				dlData = new RankCalculatorReal( StartStopRulesDefaultPlugin.this, download);
				synchronized( rankCalculatorMap ){
					rankCalculatorMap.put(download, dlData);
					sortedArrayCache = null;
				}
				download.addListener(download_listener);
				download.addTrackerListener(download_tracker_listener, false);
				download.addActivationListener(download_activation_listener);
				
				dlData.addStateAttributeListener(
						download_state_attribute_listener, DownloadManagerState.AT_TRANSIENT_FLAGS, DownloadManagerStateAttributeListener.WRITTEN );
			}

			if (dlData != null) {
				requestProcessCycle(dlData);
				if (bDebugLog)
					log.log(download.getTorrent(), LoggerChannel.LT_INFORMATION,
							"somethingChanged: downloadAdded, state: "
									+ sStates.charAt(download.getState()));
			}
		}

		@Override
		public void downloadRemoved(Download download) {
			download.removeListener(download_listener);
			download.removeTrackerListener(download_tracker_listener);
			download.removeActivationListener(download_activation_listener);

			DefaultRankCalculator dlData;
			
			synchronized( rankCalculatorMap ){
				dlData = rankCalculatorMap.remove( download );
				sortedArrayCache = null;
			}
			
			if ( dlData != null ) {
				dlData.removeStateAttributeListener(
						download_state_attribute_listener, DownloadManagerState.AT_TRANSIENT_FLAGS, DownloadManagerStateAttributeListener.WRITTEN );
													
				try{
					this_mon.enter();
						
					RankCalculatorSlotReserver reservedSlot = dlData.getReservedSlot();
						
					if ( reservedSlot != null ){

						reservedSlotsAllocated.remove( dlData );
						
						if ( reservedSlots.size() + reservedSlotsAllocated.size() < numReservedSeedingSlots ){
							
							reservedSlots.add(reservedSlot);
							
						}else{
							
							synchronized( rankCalculatorMap ){
								
								rankCalculatorMap.remove( reservedSlot );
								
								sortedArrayCache = null;
							}
						}
					}
				}finally{
						
					this_mon.exit();
				}
				
				dlData.destroy();
			}

			requestProcessCycle(null);
			if (bDebugLog)
				log.log(download.getTorrent(), LoggerChannel.LT_INFORMATION,
						"somethingChanged: downloadRemoved");
		}
	}

	private long changeCheckCount = 0;

	private long changeCheckTotalMS = 0;

	private long changeCheckMaxMS = 0;

	private class ChangeCheckerTimerTask implements TimerEventPerformer
	{
		long lLastRunTime = 0;

		@Override
		public void perform(TimerEvent event) {
			long now = 0;

			// make sure process isn't running and stop it from running while we do stuff
			try {
				this_mon.enter();

				now = SystemTime.getCurrentTime();

				//System.out.println(SystemTime.getCurrentTime() - lLastRunTime);
				if (now > lLastRunTime && now - lLastRunTime < 1000) {

					return;
				}

				lLastRunTime = now;

				DefaultRankCalculator[] dlDataArray;
				synchronized (rankCalculatorMap) {
					dlDataArray = rankCalculatorMap.values().toArray(
							new DefaultRankCalculator[0]);
				}

				int iNumDLing = 0;
				int iNumCDing = 0;
				for (int i = 0; i < dlDataArray.length; i++) {
					if (dlDataArray[i].changeChecker()) {
						requestProcessCycle(dlDataArray[i]);
					}

					// Check DLs for change in activeness (speed threshold)
					// (The call sets somethingChanged it was changed)
					if (dlDataArray[i].getActivelyDownloading())
						iNumDLing++;

					// Check Seeders for change in activeness (speed threshold)
					// (The call sets somethingChanged it was changed)
					if (dlDataArray[i].getActivelySeeding()) {
						iNumCDing++;
					}
				}

				int iMaxSeeders = calcMaxSeeders(iNumDLing);
				if (iNumCDing > iMaxSeeders) {
					requestProcessCycle(null);
					if (bDebugLog)
						log.log(LoggerChannel.LT_INFORMATION,
								"somethingChanged: More Seeding than limit");
				}

			} finally {
				if (now > 0) {
					changeCheckCount++;
					long timeTaken = (SystemTime.getCurrentTime() - now);
					changeCheckTotalMS += timeTaken;
					if (timeTaken > changeCheckMaxMS) {
						changeCheckMaxMS = timeTaken;
					}
				}

				this_mon.exit();
			}
		}
	}

	// ConfigurationListener
	@Override
	public void configurationSaved() {
		new AEThread2("reloadConfigParams", true) {
			// @see com.biglybt.core.util.AEThread2#run()
			@Override
			public void run() {
				reloadConfigParams();
			}
		}.start();
	}

	private void reloadConfigParams() {
		try {
			this_mon.enter();

			int iNewRankType = plugin_config.getUnsafeIntParameter("StartStopManager_iRankType");
			minSpeedForActiveSeeding = plugin_config.getUnsafeIntParameter("StartStopManager_iMinSpeedForActiveSeeding");
			if (minSpeedForActiveSeeding == 0) {
				maxStalledSeeding = 0;
				stalledSeedingIgnoreZP = false;
			} else {
				maxStalledSeeding = plugin_config.getUnsafeIntParameter("StartStopManager_iMaxStalledSeeding");
				if (maxStalledSeeding <= 0) {
					// insanity :)
					maxStalledSeeding = 999;
				}
				stalledSeedingIgnoreZP = plugin_config.getUnsafeBooleanParameter("StartStopManager_bMaxStalledSeedingIgnoreZP");
			}
			_maxActive = plugin_config.getUnsafeIntParameter("max active torrents");
			_maxActiveWhenSeedingEnabled = plugin_config.getUnsafeBooleanParameter("StartStopManager_bMaxActiveTorrentsWhenSeedingEnabled");
			_maxActiveWhenSeeding = plugin_config.getUnsafeIntParameter("StartStopManager_iMaxActiveTorrentsWhenSeeding");

			maxConfiguredDownloads = plugin_config.getUnsafeIntParameter("max downloads");
			
			boolean min_eq_max = plugin_config.getUnsafeBooleanParameter("StartStopManager_bMaxMinDLLinked" );

			if ( min_eq_max ){
				
				minDownloads = maxConfiguredDownloads;
				
			}else{
			
				minDownloads = plugin_config.getUnsafeIntParameter("min downloads");
			}
			
			bMaxDownloadIgnoreChecking	= plugin_config.getUnsafeBooleanParameter("StartStopManager_bMaxDownloadIgnoreChecking" );

			bAutoReposition = plugin_config.getUnsafeBooleanParameter("StartStopManager_bAutoReposition");
			minTimeAlive = plugin_config.getUnsafeIntParameter("StartStopManager_iMinSeedingTime") * 1000;
			bDebugLog = plugin_config.getUnsafeBooleanParameter("StartStopManager_bDebugLog");

			bAutoStart0Peers = plugin_config.getUnsafeBooleanParameter("StartStopManager_bAutoStart0Peers");


			globalDownloadLimit = plugin_config.getUnsafeIntParameter("Max Download Speed KBs", 0);
			globalUploadLimit = plugin_config.getUnsafeIntParameter("Max Upload Speed KBs", 0);
			globalUploadWhenSeedingLimit = plugin_config.getUnsafeBooleanParameter("enable.seedingonly.upload.rate") ? plugin_config.getUnsafeIntParameter("Max Upload Speed Seeding KBs", 0) : globalUploadLimit;

			bStopOnceBandwidthMet = plugin_config.getUnsafeBooleanParameter("StartStopManager_bStopOnceBandwidthMet");

			bStartNoMoreSeedsWhenUpLimitMet			= plugin_config.getUnsafeBooleanParameter("StartStopManager_bStartNoMoreSeedsWhenUpLimitMet" );
			bStartNoMoreSeedsWhenUpLimitMetPercent	= plugin_config.getUnsafeBooleanParameter("StartStopManager_bStartNoMoreSeedsWhenUpLimitMetPercent" );
			bStartNoMoreSeedsWhenUpLimitMetSlack	= plugin_config.getUnsafeIntParameter("StartStopManager_bStartNoMoreSeedsWhenUpLimitMetSlack" );

			boolean move_top = plugin_config.getUnsafeBooleanParameter("StartStopManager_bNewSeedsMoveTop");
			plugin_config.setCoreBooleanParameter(
					PluginConfig.CORE_PARAM_BOOLEAN_NEW_SEEDS_START_AT_TOP, move_top);

			if (iNewRankType != iRankType) {
				iRankType = iNewRankType;

				// shorten recalc for timed rank type, since the calculation is fast and we want to stop on the second
				if (iRankType == RANK_TIMED) {
					if (recalcSeedingRanksTask == null) {
						recalcAllSeedingRanks();
						recalcSeedingRanksTask = new RecalcSeedingRanksTask();
						SimpleTimer.addPeriodicEvent("StartStop:recalcSR", 1000,
								recalcSeedingRanksTask);
					}
				} else if (recalcSeedingRanksTask != null) {
					recalcSeedingRanksTask.cancel();
					recalcSeedingRanksTask = null;
				}
			}

			iDownloadSortType		= plugin_config.getUnsafeIntParameter("StartStopManager_Downloading_iSortType", -1 );
				// migrate from old boolean setting
			if ( iDownloadSortType == -1 ){

				boolean bDownloadAutoReposition = plugin_config.getUnsafeBooleanParameter("StartStopManager_Downloading_bAutoReposition");

				iDownloadSortType= bDownloadAutoReposition?DefaultRankCalculator.DOWNLOAD_ORDER_SPEED:DefaultRankCalculator.DOWNLOAD_ORDER_INDEX;

				plugin_config.setCoreIntParameter("StartStopManager_Downloading_iSortType", iDownloadSortType);
			}

			iDownloadTestTimeMillis	= plugin_config.getUnsafeIntParameter("StartStopManager_Downloading_iTestTimeSecs")*1000;
			iDownloadReTestMillis	= plugin_config.getUnsafeIntParameter("StartStopManager_Downloading_iRetestTimeMins")*60*1000;
			bDownloadTestActive		= plugin_config.getUnsafeBooleanParameter("StartStopManager_Downloading_bTestActive");

			bTagFirstPriority = plugin_config.getUnsafeBooleanParameter("StartStopManager_bTagFirstPriority");

			if ( bTagFirstPriority ){
				
				TagManager tag_manager = TagManagerFactory.getTagManager();
				
				if ( tag_manager != null && tag_manager.isEnabled()){
					
					TagType tt = tag_manager.getTagType( TagType.TT_DOWNLOAD_MANUAL );
					
					if ( fp_tag == null ){
						
						fp_tag = tt.getTag( "First Priority", true );
						
						if ( fp_tag == null ){
							
							try {
								fp_tag = tt.createTag( "First Priority", true );
								
								fp_tag.setPublic( false );
								
							}catch( Throwable e ){
								
								Debug.out( e );
							}
						}
					}
					
					Tag not_fp_tag = tt.getTag( "Not First Priority", true );
					
					if ( not_fp_tag == null ){
					
						try{
							not_fp_tag = tt.createTag( "Not First Priority", true );
								
							not_fp_tag.setPublic( false );
							
						}catch( Throwable e ){
							
							Debug.out( e );
						}
					}
					
					if ( not_fp_tag != null ) {
						
						TagProperty constraint = ((TagFeatureProperties)not_fp_tag).getProperty( TagFeatureProperties.PR_CONSTRAINT);

						constraint.setStringList(
							new String[]{
								"isComplete() && !hasTag( \"First Priority\" )"
							});
					}
				}
			}
			
			maxOverLimitSeeding = plugin_config.getUnsafeIntParameter( "Flexible Seed Slots" );
			
			int numslots = plugin_config.getUnsafeIntParameter( "Light Seed Slots Reserved" );
			
			int diff = numslots - numReservedSeedingSlots;
			
			numReservedSeedingSlots = numslots;
			
			if ( diff > 0 ){
				
				for ( int i=0;i<diff;i++){
					
					RankCalculatorSlotReserver slot = new RankCalculatorSlotReserver();
					
					reservedSlots.add( slot );
					
					synchronized( rankCalculatorMap ){
						
						rankCalculatorMap.put( slot, slot );
						
						sortedArrayCache = null;
					}
				}
			}else if ( diff < 0 ){
				
				for ( int i=-diff; i > 0 && !reservedSlots.isEmpty();i-- ){
					
					RankCalculatorSlotReserver slot = reservedSlots.removeFirst();
					
					synchronized( rankCalculatorMap ){
					
						rankCalculatorMap.remove( slot );
						
						sortedArrayCache = null;
					}
				}
			}
				
			/*
			 // limit _maxActive and maxDownloads based on TheColonel's specs

			 // maxActive = max_upload_speed / (slots_per_torrent * min_speed_per_peer)
			 if (_maxActive > 0) {
			 int iSlotsPerTorrent = plugin_config.getUnsafeIntParameter("Max Uploads");
			 // TODO: Track upload speed, storing the max upload speed over a minute
			 //        and use that for "unlimited" setting, or huge settings (like 200)
			 if (iSlotsPerTorrent > 0) {
			 int iMinSpeedPerPeer = 3; // for now.  TODO: config value
			 int _maxActiveLimit = iMaxUploadSpeed / (iSlotsPerTorrent * iMinSpeedPerPeer);
			 if (_maxActive > _maxActiveLimit) {
			 _maxActive = _maxActiveLimit;
			 plugin_config.setCoreIntParameter(PluginConfig.CORE_PARAM_INT_MAX_ACTIVE, _maxActive);
			 }
			 }

			 if (maxDownloads > _maxActive) {
			 maxDownloads = _maxActive;
			 plugin_config.setCoreIntParameter(PluginConfig.CORE_PARAM_INT_MAX_DOWNLOADS, maxDownloads);
			 }
			 }
			 */

			// force a recalc on all downloads by setting SR to 0, scheduling
			// a recalc on next process, and requsting a process cycle
			Collection<DefaultRankCalculator> allDownloads = rankCalculatorMap.values();
			
			//DefaultRankCalculator[] dlDataArray = allDownloads.toArray(new DefaultRankCalculator[0]);
			
			//for (int i = 0; i < dlDataArray.length; i++) {
			//	dlDataArray[i].resetSeedingRank();
			//}
			
			try {
				ranksToRecalc_mon.enter();

				synchronized (rankCalculatorMap) {
					ranksToRecalc.addAll(allDownloads);
				}

			} finally {
				ranksToRecalc_mon.exit();
			}
			requestProcessCycle(null);

			if (bDebugLog) {
				log.log(LoggerChannel.LT_INFORMATION, "somethingChanged: config reload");
				try {
					if (debugMenuItem == null) {
						final String DEBUG_MENU_ID = "StartStopRules.menu.viewDebug";
						MenuItemListener menuListener = new MenuItemListener() {
							@Override
							public void selected(MenuItem menu, Object target) {
								if (!(target instanceof TableRow))
									return;

								TableRow tr = (TableRow) target;
								Object ds = tr.getDataSource();

								if (!(ds instanceof Download))
									return;

								DefaultRankCalculator dlData = rankCalculatorMap.get(ds);

								if (dlData != null) {
									if ( swt_ui != null )
										swt_ui.openDebugWindow(dlData);
									else
										pi.getUIManager().showTextMessage(
												null,
												null,
												"FP:\n" + dlData.getExplainFP() + "\n" + "SR:"
														+ dlData.getExplainSR() + "\n" + "TRACE:\n"
														+ dlData.getTrace());
								}
							}
						};
						TableManager tm = pi.getUIManager().getTableManager();

						debugMenuItem = tm.addContextMenuItem(
								TableManager.TABLE_MYTORRENTS_COMPLETE, DEBUG_MENU_ID);
						debugMenuItem.setHeaderCategory(MenuItem.HEADER_CONTROL);
						debugMenuItem.addListener(menuListener);
						debugMenuItem = tm.addContextMenuItem(
								TableManager.TABLE_MYTORRENTS_INCOMPLETE, DEBUG_MENU_ID);
						debugMenuItem.setHeaderCategory(MenuItem.HEADER_CONTROL);
						debugMenuItem.addListener(menuListener);
					}
				} catch (Throwable t) {
					Debug.printStackTrace(t);
				}
			}

		} finally {
			this_mon.exit();
		}
	}

	private int calcMaxSeeders(int iDLs) {
		// XXX put in subtraction logic here
		int maxActive = getMaxActive();
		if (maxActive == 0) {
			return 999999;
		}
		return( maxActive - iDLs + numReservedSeedingSlots );
	}

	protected int getMaxActive() {
		if (!_maxActiveWhenSeedingEnabled)
			return (_maxActive);

		if (download_manager.isSeedingOnly()) {

			if (_maxActiveWhenSeeding <= _maxActive)
				return (_maxActiveWhenSeeding);

			// danger here if we are in a position where allowing more to start when seeding
			// allows a non-seeding download to start (looping occurs)

			Download[] downloads = download_manager.getDownloads();

			boolean danger = false;

			for (int i = 0; i < downloads.length && !danger; i++) {

				Download download = downloads[i];

				int state = download.getState();

				if (state == Download.ST_DOWNLOADING || state == Download.ST_SEEDING
						|| state == Download.ST_STOPPED || state == Download.ST_STOPPING
						|| state == Download.ST_ERROR) {

					// not interesting, they can't potentially cause trouble

				} else {

					// look for incomplete files

					DiskManagerFileInfo[] files = download.getDiskManagerFileInfo();

					for (int j = 0; j < files.length; j++) {

						DiskManagerFileInfo file = files[j];

						if ((!file.isSkipped()) && file.getDownloaded() != file.getLength()) {

							danger = true;

							break;
						}
					}
				}
			}

			if (!danger)
				return (_maxActiveWhenSeeding);
		}

		return (_maxActive);
	}

	private class TotalsStats
	{
		// total Forced Seeding doesn't include stalled torrents
		int forcedSeeding = 0;

		int forcedSeedingNonFP = 0;

		int waitingToSeed = 0;

		int waitingToDL = 0;

		int downloading = 0;

		int activelyDLing = 0;

		int activelyCDing = 0;

		int complete = 0;

		int incompleteQueued = 0;

		int firstPriority = 0;

		int stalledSeeders = 0;

		int stalledFPSeeders = 0;

		int forcedActive = 0;

		/**
		 * Indicate whether it's ok to start seeding.
		 * <p>
		 * Seeding can start right away when there's no auto-ranking or we are on
		 * timed ranking. Otherwise, we wait until one of the following happens:
		 * <ul>
		 * <li>Any non-stopped/errored torrent gets a scrape result AND it's after
		 *   {@link #MIN_SEEDING_STARTUP_WAIT}
		 * <li>All scrape results come in for completed, non-stopped/errored torrent
		 * <li>Any completed non-stopped/errored torrent is FP
		 * <li>Any torrent has 0 seeds (which, in most cases means it's the highest
		 *   rank)
		 * </ul>
		 * <p>
		 * If none of the above happen, then after {@link #MIN_FIRST_SCRAPE_WAIT},
		 * the flag will turned on.
		 */
		// not a total :)
		boolean bOkToStartSeeding;

		int maxSeeders;

		int maxActive;

		int maxTorrents;

		boolean upLimitProhibitsNewSeeds;

		public int maxUploadSpeed()
		{
			return downloading == 0 ? globalUploadWhenSeedingLimit : globalUploadLimit;
		}

		/**
		 * Default Constructor
		 *
		 * @param dlDataArray list of download data (rank calculators) objects
		 *                     to base calculations on.
		 */
		public TotalsStats(DefaultRankCalculator[] dlDataArray) {
			bOkToStartSeeding = (iRankType == RANK_NONE) || (iRankType == RANK_TIMED)
					|| (SystemTime.getMonotonousTime() - monoStartedOn > MIN_FIRST_SCRAPE_WAIT);

			// count the # of ok scrapes when !bOkToStartSeeding, and flip to true
			// if all scrapes for non-stopped/errored completes are okay.
			int totalOKScrapes = 0;

			// - Build a SeedingRank list for sorting
			// - Build Count Totals
			// - Do anything that doesn't need to be done in Queued order
			for (int i = 0; i < dlDataArray.length; i++) {
				DefaultRankCalculator dlData = dlDataArray[i];
				if (dlData == null) {
					continue;
				}

				int state = dlData.getState();
				
				// No stats colllected on error or stopped torrents
				if (state == Download.ST_ERROR || state == Download.ST_STOPPED) {
					continue;
				}

				boolean completed = dlData.isComplete();
				boolean bIsFirstP = false;

				// Count forced seedings as using a slot
				// Don't count forced downloading as using a slot
				if (!completed && dlData.isForceStart())
					continue;

				if (completed) {
					// Only used when !bOkToStartSeeding.. set only to make compiler happy
					boolean bScrapeOk = true;
					if (!bOkToStartSeeding) {
						bScrapeOk = dlData.scrapeResultOk();
						if ( dlData.calcSeedsNoUs() == 0 && bScrapeOk)
							bOkToStartSeeding = true;
						else if ((dlData.getSeedingRank() > 0)
								&& (state == Download.ST_QUEUED || state == Download.ST_READY)
								&& (SystemTime.getMonotonousTime() - monoStartedOn > MIN_SEEDING_STARTUP_WAIT))
							bOkToStartSeeding = true;
					}

					complete++;

					if (!bOkToStartSeeding && bScrapeOk)
						totalOKScrapes++;

					if (dlData.isFirstPriority()) {
						if (!bOkToStartSeeding)
							bOkToStartSeeding = true;

						firstPriority++;
						bIsFirstP = true;
					}

					if (dlData.getActivelySeeding()) {
						if (dlData.isForceActive()) {
							forcedActive++;
						}

						activelyCDing++;
						if (dlData.isForceStart()) {
							forcedSeeding++;
							if (!bIsFirstP)
								forcedSeedingNonFP++;
						}
					} else if (state == Download.ST_SEEDING) {
						if (bIsFirstP) {
							stalledFPSeeders++;
						}
						if ( stalledSeedingIgnoreZP && dlData.getLastModifiedScrapeResultPeers() == 0 && dlData.getLastScrapeResultOk()) {
							// ignore 
						}else{
							stalledSeeders++;
						}
					}

					if (state == Download.ST_READY || state == Download.ST_WAITING
							|| state == Download.ST_PREPARING) {
						waitingToSeed++;
					}

				} else { // !completed
					if (state == Download.ST_DOWNLOADING) {
						downloading++;

						if (dlData.getActivelyDownloading())
							activelyDLing++;
					}

					if (state == Download.ST_READY || state == Download.ST_WAITING
							|| state == Download.ST_PREPARING) {
						waitingToDL++;
					} else if (state == Download.ST_QUEUED) {
						incompleteQueued++;
					} //if state
				} // if completionLevel
			} // for

			if (!bOkToStartSeeding && totalOKScrapes == complete)
				bOkToStartSeeding = true;

			maxSeeders = calcMaxSeeders(activelyDLing + waitingToDL);
			maxActive = getMaxActive();

			if (maxActive == 0) {
				maxTorrents = 9999;
			} else if (maxUploadSpeed() == 0) {
				maxTorrents = maxActive + 4;
			} else {
				// Don't allow more "seeding/downloading" torrents than there is enough
				// bandwidth for.  There needs to be enough bandwidth for at least
				// each torrent to get to its minSpeedForActiveSeeding
				// (we buffer it at 2x just to be safe).
				int minSpeedPerActive = (minSpeedForActiveSeeding * 2) / 1024;
				// Even more buffering/limiting.  Limit to enough bandwidth for
				// each torrent to have potentially 3kbps.
				if (minSpeedPerActive < 3)
					minSpeedPerActive = 3;
				maxTorrents = (maxUploadSpeed() / minSpeedPerActive);
				// Allow user to do stupid things like have more slots than their
				// upload speed can handle
				if (maxTorrents < maxActive)
					maxTorrents = maxActive;
				//System.out.println("maxTorrents = " + maxTorrents + " = " + iMaxUploadSpeed + " / " + minSpeedPerActive);
				//System.out.println("totalTorrents = " + (activeSeedingCount + totalStalledSeeders + totalDownloading));
			}

				// parg - added a new option here to simply not start any more seeds if upload limit (roughly) met

			long	up_limit = maxUploadSpeed();

			if ( bStartNoMoreSeedsWhenUpLimitMet && up_limit > 0 ){

				long current_up_kbps = download_manager.getStats().getSmoothedSendRate()/1024;

				long target;

				if ( bStartNoMoreSeedsWhenUpLimitMetPercent ){

					target = up_limit * bStartNoMoreSeedsWhenUpLimitMetSlack / 100;

				}else{

					target = up_limit - bStartNoMoreSeedsWhenUpLimitMetSlack;
				}

				if ( current_up_kbps > target ){

					upLimitProhibitsNewSeeds = true;
				}
			}
		} // constructor
	}

	/**
	 * Running totals and stuff that gets used during a process run.
	 * Split into class so complete/incomplete can be seperated into functions
	 */
	
	private static class ProcessVarsIncomplete
	{
		int numWaitingOrDLing; // Running Count

		boolean higherDLtoStart;
	}
	
	private static class ProcessVarsComplete
	{
		/** Running count of torrents waiting or seeding, not including stalled */
		int numWaitingOrSeeding; // Running Count

		/**
		 * store whether there's a torrent higher in the list that is queued
		 * We don't want to start a torrent lower in the list if there's a higherQueued
		 */

		boolean higherCDtoStart;

		int stalledSeeders; // Running Count
	}

	
	private static class ProcessVars
	{
		long accumulatedDownloadSpeed;
		long accumulatedUploadSpeed;
			
		final ProcessVarsIncomplete	incomp 	= new ProcessVarsIncomplete();
		final ProcessVarsComplete	comp 	= new ProcessVarsComplete();
	}

	private static class 
	ProcessTagVarsIncomplete
	{
		final int		maxDLs;
		final boolean	isStrictLimit;	// if strict then stalled downloads aren't included in calcs
		
		int numWaitingOrDLing; // Running Count
		
		int stalledDownloaders; // Running Count

		boolean	higherDLtoStart;
		
		ProcessTagVarsIncomplete(
			int			max,
			boolean		strict )
		{
			maxDLs 			= max;
			isStrictLimit	= strict;
		}
	}
	
	private static class 
	ProcessTagVarsComplete
	{
		final int		maxCDs;
		final boolean	isStrictLimit;	// if strict then stalled downloads aren't included in calcs

		int numWaitingOrSeeding; // Running Count
			
		int stalledSeeders; // Running Count
		
		ProcessTagVarsComplete(
			int			max,
			boolean		strict )
		{
			maxCDs 			= max;
			isStrictLimit	= strict;
		}
	}
	
	private long processCount = 0;

	private long processTotalMS = 0;

	private long processMaxMS = 0;

	private long processLastCompleteMono = -1;

	private long processTotalGap = 0;

	private long processTotalRecalcs = 0;

	private long processTotalZeroRecalcs = 0;

	private long lastDebugNoChangesMono = -1;
	
	protected void process() {
		long nowMono = -1;
		try {
			this_mon.enter();

			nowMono = SystemTime.getMonotonousTime();

			boolean debugNoChange = lastDebugNoChangesMono == -1 || nowMono - lastDebugNoChangesMono >= 60000;
				
			if ( debugNoChange ){
				
				lastDebugNoChangesMono = nowMono;
			}
						
			somethingChanged = false;
			Object[] recalcArray;
			try {
				ranksToRecalc_mon.enter();

				recalcArray = ranksToRecalc.toArray();
				ranksToRecalc.clear();
			} finally {
				ranksToRecalc_mon.exit();
			}
			for (int i = 0; i < recalcArray.length; i++) {
				DefaultRankCalculator rankObj = (DefaultRankCalculator) recalcArray[i];
				if (bDebugLog) {
					long oldSR = rankObj.getSeedingRank();
					
					rankObj.recalcSeedingRank();
					
					int newSR=rankObj.getSeedingRank();
					
					if ( oldSR != newSR ){
						String s = "recalc seeding rank.  old/new=" + oldSR + "/" + newSR;
						log.log(rankObj.getRelatedTo(), LoggerChannel.LT_INFORMATION, s);
					}
				} else {
					rankObj.recalcSeedingRank();
				}
			}
			processTotalRecalcs += recalcArray.length;
			if (recalcArray.length == 0) {
				processTotalZeroRecalcs++;
			}

				// reclaim any reserved slots that are no longer active
			
			Iterator<DefaultRankCalculator> it = reservedSlotsAllocated.iterator();
			
			while( it.hasNext()){
				
				DefaultRankCalculator dlData = it.next();
					
				int	state = dlData.getState();
				
				if ( 	state != Download.ST_SEEDING &&
						state != Download.ST_PREPARING &&
						state != Download.ST_READY &&
						state != Download.ST_WAITING ){
				
					RankCalculatorSlotReserver reservedSlot = dlData.getReservedSlot();
					
					if ( reservedSlot != null ){
						
						dlData.setReservedSlot( null );
						
						it.remove();

						if ( reservedSlots.size() + reservedSlotsAllocated.size() < numReservedSeedingSlots ){
						
							reservedSlots.add(reservedSlot);
							
						}else{
							
							synchronized( rankCalculatorMap ){
						
								rankCalculatorMap.remove( reservedSlot );
								
								sortedArrayCache = null;
							}
						}
					}else{
						
						Debug.out( "eh?" );
					}
				}
			}
			
			// pull the data into a local array, so we don't have to lock/synchronize
			
			DefaultRankCalculator[] dlDataArray;
			
			synchronized( rankCalculatorMap ){
				
				dlDataArray = sortedArrayCache;
			
				if ( dlDataArray == null ){
				
					dlDataArray = sortedArrayCache = rankCalculatorMap.values().toArray(
							new DefaultRankCalculator[rankCalculatorMap.size()]);
				}
			}

			TotalsStats totals = new TotalsStats(dlDataArray);

			String[] mainDebugEntries = null;
			if (bDebugLog) {
				log.log(LoggerChannel.LT_INFORMATION, ">>process()");
				mainDebugEntries = new String[] {
					"ok2Start=" + boolDebug(totals.bOkToStartSeeding),
					"tFrcdCding=" + totals.forcedSeeding,
					"actvCDs=" + totals.activelyCDing,
					"tW8tingToCd=" + totals.waitingToSeed,
					"tDLing=" + totals.downloading,
					"actvDLs=" + totals.activelyDLing,
					"tW8tingToDL=" + totals.waitingToDL,
					"tCom=" + totals.complete,
					"tIncQd=" + totals.incompleteQueued,
					"mxCdrs=" + totals.maxSeeders,
					"tFP=" + totals.firstPriority,
					"maxT=" + totals.maxTorrents,
					"maxA=" + totals.maxActive,
				};
			}

			// Sort: SeedingRank Desc, Position Desc
			
			// unfortunately the comparables aren't stable and can result in TimSort throwing a wobbly and
			// 'general contract violated' exception. We don't really care so...
			
			for ( int i=0;i<10;i++){
				
				try{
					Arrays.sort(dlDataArray);
					
					break;
					
				}catch( IllegalArgumentException e ){
				}
			}
			
			ProcessVars vars = new ProcessVars();

			ProcessVarsComplete	cvars = vars.comp;
			
			Map<TagFeatureRateLimit,ProcessTagVarsIncomplete>	tvarsIncompleteMap = new IdentityHashMap<>();
			Map<TagFeatureRateLimit,ProcessTagVarsComplete>		tvarsCompleteMap = new IdentityHashMap<>();
			
			// pre-included Forced Start torrents so a torrent "above" it doesn't
			// start (since normally it would start and assume the torrent below it
			// would stop)
			
			cvars.numWaitingOrSeeding = totals.forcedSeeding; // Running Count
			
			if ( ENABLE_DLOG ){
				dlog.log( "***** START" );
			}
			
			int posComplete = 0;
			
			List<DefaultRankCalculator>	incompleteDownloads = new ArrayList<>(dlDataArray.length);
			List<DefaultRankCalculator>	completeDownloads 	= new ArrayList<>(dlDataArray.length);

			// Loop 2 of 2:
			// - Start/Stop torrents based on criteria

			for (int i = 0; i < dlDataArray.length; i++) {
				DefaultRankCalculator dlData = dlDataArray[i];
				
				dlData.resetTrace();

				// Initialize STATE_WAITING torrents
				if ((dlData.getState() == Download.ST_WAITING)) {
					try {
						dlData.initialize();

						if (bDebugLog){
							String s = "initialize: state is waiting";
							log.log(dlData.getRelatedTo(), LoggerChannel.LT_INFORMATION, s);
						}
					} catch (Exception ignore) {
						/*ignore*/
					}
					if (bDebugLog && dlData.getState() == Download.ST_WAITING) {
						dlData.appendTrace( "still in waiting state after initialize!\n" );
					}
				}

				if (	bAutoReposition && 
						(iRankType != RANK_NONE) &&
						dlData.isComplete() &&
						dlData.supportsPosition()&&
						(totals.bOkToStartSeeding || totals.firstPriority > 0))
					dlData.setPosition(++posComplete);

				int state = dlData.getState();

				// Never do anything to stopped entries
				if (state == Download.ST_STOPPING || state == Download.ST_STOPPED
						|| state == Download.ST_ERROR) {
					continue;
				}

				if (dlData.isForceStart()) {
					if (state == Download.ST_STOPPED || state == Download.ST_QUEUED) {
						try {
							dlData.restart();
							
							if (bDebugLog){
								String s = "restart: isForceStart";
								log.log(dlData.getRelatedTo(), LoggerChannel.LT_INFORMATION, s);
								dlData.appendTrace( s + "\n" );
							}
						} catch (DownloadException e) {
						}

						state = dlData.getState();
					}

					if (state == Download.ST_READY) {
						try {
							dlData.start();
							
							if (bDebugLog){
								String s = "Start: isForceStart";
								log.log(dlData.getRelatedTo(), LoggerChannel.LT_INFORMATION, s);
								dlData.appendTrace( s + "\n" );
							}
						} catch (DownloadException e) {
							/* ignore */
						}
					}
				}

					// Handle incomplete DLs
				
				if (!dlData.isComplete()){
					
					incompleteDownloads.add( dlData );
					
					TagFeatureRateLimit[] tagLimits = dlData.getTagsWithDLLimits();
					
					ProcessTagVarsIncomplete[] tvars;
					
					if ( tagLimits.length == 0 ){
						
						tvars = new ProcessTagVarsIncomplete[0];
						
					}else{
						
						List<ProcessTagVarsIncomplete> temp = new ArrayList<>( tagLimits.length );
						
						for ( TagFeatureRateLimit tag: tagLimits ){
							
							ProcessTagVarsIncomplete t = tvarsIncompleteMap.get( tag );
							
							if ( t == null ){
								
								int maxDLs = tag.getMaxActiveDownloads();
								
								if ( maxDLs > 0 ){
									
									t = new ProcessTagVarsIncomplete( maxDLs, tag.getStrictActivityLimits());
									
									tvarsIncompleteMap.put( tag,  t );
									
									temp.add( t );
								}
							}else{
								
								temp.add(t);
							}
						}
						tvars = temp.toArray(new ProcessTagVarsIncomplete[temp.size()]);
					}
					
					handleInCompleteDownload(dlData, vars, tvars, totals);
					
				}else{
					
					completeDownloads.add( dlData );
					
					TagFeatureRateLimit[] tagLimits = dlData.getTagsWithCDLimits();
					
					ProcessTagVarsComplete[] tvars;
					
					if ( tagLimits.length == 0 ){
						
						tvars = new ProcessTagVarsComplete[0];
						
					}else{
						
						List<ProcessTagVarsComplete> temp = new ArrayList<>( tagLimits.length );
						
						for ( TagFeatureRateLimit tag: tagLimits ){
							
							ProcessTagVarsComplete t = tvarsCompleteMap.get( tag );
							
							if ( t == null ){
								
								int maxCDs = tag.getMaxActiveSeeds();
								
								if ( maxCDs > 0 ){
									
									t = new ProcessTagVarsComplete( maxCDs, tag.getStrictActivityLimits());
									
									tvarsCompleteMap.put( tag,  t );
									
									temp.add( t );
								}
							}else{
								
								temp.add(t);
							}
						}
						tvars = temp.toArray(new ProcessTagVarsComplete[temp.size()]);
					}
						
					handleCompletedDownload( dlData, vars, tvars, totals, debugNoChange );
				}
			} // Loop 2/2 (Start/Stopping)
			
				// handle light-seeding availability
			
			int lightSeedingSlots =  reservedSlots.size();

			if ( !completeDownloads.isEmpty()){
								
				boolean hasFreeSeedingSlots =  lightSeedingSlots > 0;
				
				if ( hasFreeSeedingSlots ){
								
					int	numNewLightSeeds = 1; // lightSeedingSlots * 2;
					
					int	numComp = completeDownloads.size();
					
						// randomise things a bit
					
					int pos = RandomUtils.nextInt( numComp );
					
					for ( int i=0; i < numComp && numNewLightSeeds > 0; i++ ){
							
						DefaultRankCalculator data = completeDownloads.get( pos );
						
						if ( ++pos >= numComp ){
							
							pos = 0;
						}
						
						if ( data.updateLightSeedEligibility( true )){
														
							numNewLightSeeds--;
						}
					}
				}else{
						
					for ( DefaultRankCalculator data: completeDownloads ){
							
						data.updateLightSeedEligibility( false );
					}
				}
			}
			
			if ( bDebugLog ){
				if ( totals.maxActive==0){
					slotStatus = "Unlimited";
				}else{
					int currFree	= ( totals.maxSeeders + maxStalledSeeding ) - ( cvars.numWaitingOrSeeding + cvars.stalledSeeders );
					int globFree	= ( totals.maxSeeders + maxStalledSeeding + maxOverLimitSeeding ) - ( totals.activelyCDing + totals.waitingToSeed + totals.stalledSeeders );

					slotStatus = 	"Seed=" + Math.max( Math.min( currFree, globFree ), 0 ) + ", " +
									"Light=" + Math.min( reservedSlots.size(), numReservedSeedingSlots );
				}
			}else{
				slotStatus = "";
			}
			
			processDownloadingRules( incompleteDownloads );

			if ( ENABLE_DLOG ){
				dlog.log( "***** END" );
			}

			if (bDebugLog) {
				String[] mainDebugEntries2 = new String[] {
					"ok2Start=" + boolDebug(totals.bOkToStartSeeding),
					"tFrcdCding=" + totals.forcedSeeding,
					"actvCDs=" + totals.activelyCDing,
					"tW8tingToCd=" + totals.waitingToSeed,
					"tDLing=" + totals.downloading,
					"actvDLs=" + totals.activelyDLing,
					"tW8tingToDL=" + totals.waitingToDL,
					"tCom=" + totals.complete,
					"tIncQd=" + totals.incompleteQueued,
					"mxCdrs=" + totals.maxSeeders,
					"tFP=" + totals.firstPriority,
					"maxT=" + totals.maxTorrents,
					"maxA=" + totals.maxActive,
				};
				printDebugChanges("<<process() ", mainDebugEntries, mainDebugEntries2,
						"freeS=" + lightSeedingSlots, "", true, null);
			}
		} finally {
			if (nowMono >= 0) {
				processCount++;
				long timeTaken = (SystemTime.getMonotonousTime() - nowMono);
				if (bDebugLog) {
					log.log(LoggerChannel.LT_INFORMATION, "process() took " + timeTaken);
				}
				processTotalMS += timeTaken;
				if (timeTaken > processMaxMS) {
					processMaxMS = timeTaken;
				}
				if (processLastCompleteMono >= 0) {
					processTotalGap += (nowMono - processLastCompleteMono);
				}
				processLastCompleteMono = nowMono;
			}

			immediateProcessingScheduled = false;

			this_mon.exit();
		}
	} // process()

	private DefaultRankCalculator 	dlr_current_active;
	private long					dlr_max_rate_time;

	private void
	processDownloadingRules(
		List<DefaultRankCalculator>		downloads )
	{
		long mono_now = SystemTime.getMonotonousTime();

		if ( mono_now - monoStartedOn < MIN_DOWNLOADING_STARTUP_WAIT ){

			return;
		}

		if ( 	iDownloadSortType != DefaultRankCalculator.DOWNLOAD_ORDER_SPEED &&
				iDownloadSortType != DefaultRankCalculator.DOWNLOAD_ORDER_ETA ){

				// cancel any existing speed ordering stuff

			if ( dlr_current_active != null ){

				dlr_current_active.setDLRInactive();

				dlr_current_active = null;
			}
		}

		if ( iDownloadSortType == DefaultRankCalculator.DOWNLOAD_ORDER_INDEX ){

				// nothing to do, index is natural order

			return;

		}else if ( 	iDownloadSortType == DefaultRankCalculator.DOWNLOAD_ORDER_SEED_COUNT || 
					iDownloadSortType == DefaultRankCalculator.DOWNLOAD_ORDER_REVERSE_SEED_COUNT ){

			Collections.sort(
				downloads,
				new Comparator<DefaultRankCalculator>()
				{
					@Override
					public int
					compare(
						DefaultRankCalculator d1,
						DefaultRankCalculator d2)
					{
						DownloadScrapeResult s1 = d1.getAggregatedScrapeResult( true );
						DownloadScrapeResult s2 = d2.getAggregatedScrapeResult( true );

						int result = s2.getSeedCount() - s1.getSeedCount();

						if ( result == 0 ){

							result = s2.getNonSeedCount() - s1.getNonSeedCount();
						}
						
						if ( iDownloadSortType == DefaultRankCalculator.DOWNLOAD_ORDER_SEED_COUNT ){

							return( result );
							
						}else{
							
							return( -result );
						}
					}
				});

			for ( int i=0;i<downloads.size();i++){

				DefaultRankCalculator drc = downloads.get(i);

				if ( drc.supportsPosition() && drc.getPosition() != (i+1)){

					drc.moveTo( i+1 );
				}
			}
		}else if ( 	iDownloadSortType == DefaultRankCalculator.DOWNLOAD_ORDER_SIZE || 
					iDownloadSortType == DefaultRankCalculator.DOWNLOAD_ORDER_REVERSE_SIZE ||
					iDownloadSortType == DefaultRankCalculator.DOWNLOAD_ORDER_REMAINING ){

			Collections.sort(
				downloads,
				new Comparator<DefaultRankCalculator>()
				{
					@Override
					public int
					compare(
						DefaultRankCalculator d1,
						DefaultRankCalculator d2)
					{
						if ( iDownloadSortType == DefaultRankCalculator.DOWNLOAD_ORDER_REMAINING  ){
							long l1 = d1.getRemainingExcludingDND();
							long l2 = d2.getRemainingExcludingDND();
		
							int result = Long.compare( l1, l2 );

							return( result );
							
						}else{
							long l1 = d1.getSizeExcludingDND();
							long l2 = d2.getSizeExcludingDND();
		
							int result = Long.compare( l2, l1 );
							
							if ( iDownloadSortType == DefaultRankCalculator.DOWNLOAD_ORDER_SIZE ){
		
								return( result );
								
							}else{
								
								return( -result );
							}
						}
					}
				});
	
			for ( int i=0;i<downloads.size();i++){
	
				DefaultRankCalculator drc = downloads.get(i);
	
				if ( drc.getPosition() != (i+1)){
	
					drc.moveTo( i+1 );
				}
			}
		}else if ( iDownloadSortType == DefaultRankCalculator.DOWNLOAD_ORDER_FILE_PRIORITIES ){ 

			Collections.sort(
				downloads,
				new Comparator<DefaultRankCalculator>()
				{
					@Override
					public int
					compare(
						DefaultRankCalculator d1,
						DefaultRankCalculator d2)
					{
						int[] p1 = d1.getFilePriorityStats();
						int[] p2 = d2.getFilePriorityStats();
	
						int result = Integer.compare( p2[1],  p1[1] );
						
						if ( result == 0 ){
							
							result = Integer.compare( p2[3],  p1[3] );
						}
						
						return( result );
					}
				});
	
			for ( int i=0;i<downloads.size();i++){
	
				DefaultRankCalculator drc = downloads.get(i);
	
				if ( drc.getPosition() != (i+1)){
	
					drc.moveTo( i+1 );
				}
			}
		}else{

				// speed ordering

			if ( dlr_current_active != null ){

				if ( !downloads.contains( dlr_current_active )){

					dlr_current_active.setDLRInactive();

					dlr_current_active = null;
				}
			}

			if ( downloads.size() < 2 ){

				return;
			}

			if ( globalDownloadLimit > 0 ){

				int	downloadKBSec = (int)((globalDownloadSpeedAverage.getAverage()*1000/PROCESS_CHECK_PERIOD)/1024);

				if ( globalDownloadLimit - downloadKBSec < 5 ){

					if ( dlr_max_rate_time == 0 ){

						dlr_max_rate_time = mono_now;

					}else if ( mono_now - dlr_max_rate_time >= 60*1000 ){

							// been at max a while, kill any remaining test that might be running as result
							// is inaccurate due to saturation

						if ( dlr_current_active != null ){

							dlr_current_active.setDLRInactive();

							dlr_current_active = null;
						}

						return;
					}
				}else{

					dlr_max_rate_time = 0;
				}
			}else{

				dlr_max_rate_time = 0;
			}

			if ( dlr_current_active != null ){

				long last_test = dlr_current_active.getDLRLastTestTime();

				long	tested_ago = mono_now - last_test;

				if ( tested_ago < iDownloadTestTimeMillis ){

					return;
				}

				dlr_current_active.setDLRComplete( mono_now );

				dlr_current_active = null;
			}

			boolean is_rate = iDownloadSortType == DefaultRankCalculator.DOWNLOAD_ORDER_SPEED;

			if ( dlr_current_active == null ){

				DefaultRankCalculator	to_test = null;

				long	oldest_test = 0;

					// add in the time required to run a cycle of tests

				long adjustedReTest = iDownloadReTestMillis + iDownloadTestTimeMillis * downloads.size();

					// note that downloads are already ordered appropriately (by position by default)

				for ( DefaultRankCalculator drc: downloads ){

					if ( drc.isQueued()){

						long last_test = drc.getDLRLastTestTime();

						if ( last_test == 0 ){

								// never tested, take first we find

							to_test = drc;

							break;

						}else{

							if ( iDownloadReTestMillis > 0 ){

									// see if it qualifies for a retest, take oldest test

								long	tested_ago = mono_now - last_test;

								if ( tested_ago >= adjustedReTest ){

									if ( tested_ago > oldest_test ){

										oldest_test = tested_ago;
										to_test		= drc;
									}
								}
							}
						}
					}
				}
				
				if ( to_test == null && bDownloadTestActive){
					
						// test running ones at least once
						
					for ( DefaultRankCalculator drc: downloads ){

						if ( drc.isDownloading()){

							long last_test = drc.getDLRLastTestTime();

							if ( last_test == 0 ){

									// never tested, take first we find

								to_test = drc;

								break;

							}else{

								if ( iDownloadReTestMillis > 0 ){

										// see if it qualifies for a retest, take oldest test

									long	tested_ago = mono_now - last_test;

									if ( tested_ago >= adjustedReTest ){

										if ( tested_ago > oldest_test ){

											oldest_test = tested_ago;
											to_test		= drc;
										}
									}
								}
							}
						}
					}
				}

				if ( to_test != null ){

					dlr_current_active = to_test;

					to_test.setDLRActive( mono_now );
				}
			}

				// tidy up queue order so the fastest n (or n-1 is testing) occupy the top queue slots

			Collections.sort(
				downloads,
				new Comparator<DefaultRankCalculator>()
				{
					private Map<DefaultRankCalculator,Long>	eta_map = new HashMap<>();
					
					@Override
					public int
					compare(
						DefaultRankCalculator o1,
						DefaultRankCalculator o2 )
					{
						if ( o1 == dlr_current_active ){

							return( -1 );

						}else if ( o2 == dlr_current_active ){

							return( 1 );
						}
						
						int	res;
						
						if ( is_rate ){
							
							int	speed1 = o1.getDLRLastTestSpeed();
							int	speed2 = o2.getDLRLastTestSpeed();
	
							res = speed2 - speed1;
							
						}else{
							
							long	eta1 = getETA( o1 );
							long	eta2 = getETA( o2 );
	
							res = Long.compare( eta1,  eta2 );
						}
						
						if ( res == 0  ){

							res = o1.getPosition() - o2.getPosition();

						}

						return( res );
					}
					
					private long
					getETA(
						DefaultRankCalculator	o )
					{
						Long l = eta_map.get( o );
						
						if ( l == null ){
							
							l = o.getDLRLastTestETA();
						
							eta_map.put( o, l );
						}
						
						return( l );
					}
				});
			
			for ( int i=0;i<downloads.size();i++){

				DefaultRankCalculator drc = downloads.get(i);

				if ( is_rate?drc.getDLRLastTestSpeed() > 0 : drc.getDLRLastTestETA() > 0 ){

					if ( drc.getPosition() != (i+1)){

						drc.moveTo( i+1 );
					}
				}
			}
		}
	}

	private int
	getMaxDownloads()
	{
		if ( dlr_current_active == null ){

			return( maxConfiguredDownloads );

		}else{

			return( maxConfiguredDownloads + 1 );
		}
	}

	/**
	 * @param dlData
	 * @param vars
	 * @param totals
	 */
	private void 
	handleInCompleteDownload(
		DefaultRankCalculator 		dlData,
		ProcessVars 				vars, 
		ProcessTagVarsIncomplete[]	tagVars,
		TotalsStats 				totals ) 
	{
		int state = dlData.getState();

		if (dlData.isForceStart()) {
			if (bDebugLog) {
				String s = "isForceStart.. rules skipped";
				log.log(dlData.getRelatedTo(), LoggerChannel.LT_INFORMATION, s);
				dlData.appendTrace( s + "\n" );
			}
			return;
		}

		if ( bMaxDownloadIgnoreChecking ){
				// unfortunately download.isChecking only returns true when rechecking and seeding :(

			if ( dlData.getCoreState() == com.biglybt.core.download.DownloadManager.STATE_CHECKING ){

				if (bDebugLog) {
					String s = "isChecking.. rules skipped";
					log.log(dlData.getRelatedTo(), LoggerChannel.LT_INFORMATION, s);
					dlData.appendTrace( s + "\n" );
				}
				return;
			}
		}
		
		ProcessVarsIncomplete	ivars = vars.incomp;
		
		// Don't mess with preparing torrents.  they could be in the
		// middle of resume-data building, or file allocating.
		if (state == Download.ST_PREPARING){
			ivars.numWaitingOrDLing++;
			for (ProcessTagVarsIncomplete tvars: tagVars ){
				tvars.numWaitingOrDLing++;
			}
			if (bDebugLog) {
				String s = "ST_PREPARING.. rules skipped. numW8tngorDLing="
						+ ivars.numWaitingOrDLing;
				log.log(dlData.getRelatedTo(), LoggerChannel.LT_INFORMATION, s);
				dlData.appendTrace( s + "\n" );
			}
			return;
		}

		int maxDLs = 0;
		int maxDownloads = getMaxDownloads();
		if (totals.maxActive == 0) {
			maxDLs = maxDownloads;
		} else {
			int DLmax = 0;
				// PARG what the heck does the number of stalled first priority seeders have to do 
				// with the price of cheese?
			DLmax = totals.stalledFPSeeders + totals.forcedActive + totals.maxActive
					- totals.firstPriority - totals.forcedSeedingNonFP;
			maxDLs = (DLmax <= 0) ? 0 : maxDownloads - DLmax <= 0 ? maxDownloads
					: DLmax;
		}

		if (maxDLs < minDownloads) {
			maxDLs = minDownloads;
		}

		int tagMaxDLs;
		if ( tagVars.length == 0 ){
			tagMaxDLs = 0;
		}else{
			tagMaxDLs = Integer.MAX_VALUE;
	
			for (ProcessTagVarsIncomplete tvars: tagVars ){
				tagMaxDLs = Math.min( tagMaxDLs, tvars.maxDLs );
			}
		}
		
		boolean bActivelyDownloading = dlData.getActivelyDownloading();
		boolean globalDownLimitReached;
		boolean globalRateAdjustedActivelyDownloading;
		boolean fakedActively;
		if (bStopOnceBandwidthMet) {
  		boolean isRunning = dlData.getState() == Download.ST_DOWNLOADING;
  		globalDownLimitReached = globalDownloadLimit > 0 && ((double)vars.accumulatedDownloadSpeed)/1024 > globalDownloadLimit * IGNORE_SLOT_THRESHOLD_FACTOR;
  		globalRateAdjustedActivelyDownloading = bActivelyDownloading || (isRunning && globalDownLimitReached);
  		fakedActively = globalRateAdjustedActivelyDownloading && !bActivelyDownloading;
  		if(fakedActively)
  		{
  			totals.activelyDLing++;
  			totals.maxSeeders = calcMaxSeeders(totals.activelyDLing + totals.waitingToDL);
  		}
		} else {
			globalDownLimitReached = false;
			globalRateAdjustedActivelyDownloading = bActivelyDownloading;
			fakedActively = false;
		}


		if (bDebugLog) {
			String s = ">> DL state=" + sStates.charAt(dlData.getState())
					+ ";shareRatio=" + dlData.getShareRatio()
					+ ";numW8tngorDLing=" + ivars.numWaitingOrDLing + ";maxCDrs="
					+ totals.maxSeeders + ";forced=" + boolDebug(dlData.isForceStart())
					+ ";actvDLs=" + totals.activelyDLing + ";maxDLs=" + maxDLs + ";tagMaxDLs=" + tagMaxDLs
					+ ";ActDLing=" + boolDebug(bActivelyDownloading) + ";globDwnRchd=" + boolDebug(globalDownLimitReached)
					+ ";hgherQd=" + boolDebug(ivars.higherDLtoStart) + ";isCmplt="
					+ boolDebug(dlData.isComplete());
			log.log(dlData.getRelatedTo(), LoggerChannel.LT_INFORMATION, s);
			dlData.appendTrace( s + "\n" );
		}

		// must use fresh getActivelyDownloading() in case state changed to
		// downloading
		if ((state == Download.ST_DOWNLOADING && globalRateAdjustedActivelyDownloading)
				|| state == Download.ST_READY || state == Download.ST_WAITING
				|| state == Download.ST_PREPARING) {
			ivars.numWaitingOrDLing++;
			for (ProcessTagVarsIncomplete tvars: tagVars ){
				tvars.numWaitingOrDLing++;
			}
		}else{
			if ( state == Download.ST_DOWNLOADING ){
				for (ProcessTagVarsIncomplete tvars: tagVars ){
					tvars.stalledDownloaders++;
				}
			}
		}

		if ( state == Download.ST_READY || state == Download.ST_DOWNLOADING	|| state == Download.ST_WAITING ){

			// Stop torrent if over limit
			boolean bOverLimit = 	ivars.numWaitingOrDLing > maxDLs ||
									(ivars.numWaitingOrDLing >= maxDLs && ivars.higherDLtoStart);

			for (ProcessTagVarsIncomplete tvars: tagVars ){
				int active = tvars.numWaitingOrDLing + (tvars.isStrictLimit?tvars.stalledDownloaders:0);
				
				if ( 	active > tvars.maxDLs || 
						(active == tvars.maxDLs && tvars.higherDLtoStart)){
					
					bOverLimit = true;
					
					break;
				}
			}
			
			boolean bDownloading = state == Download.ST_DOWNLOADING;

			if (	maxDownloads != 0 &&
					bOverLimit &&
					!( dlData.isChecking() || dlData.isMoving()) &&
					(	globalRateAdjustedActivelyDownloading ||
						!bDownloading ||
						(bDownloading && totals.maxActive != 0 && !globalRateAdjustedActivelyDownloading &&	totals.activelyCDing + totals.activelyDLing >= totals.maxActive))){
				try
				{
					if (bDebugLog)
					{
						String s = "   stopAndQueue: " + ivars.numWaitingOrDLing + " waiting or downloading, when limit is " + maxDLs + "(" + maxDownloads + ")";
						if (ivars.higherDLtoStart)
						{
							s += " and higher DL is starting";
						}
						log.log(dlData.getRelatedTo(), LoggerChannel.LT_INFORMATION, s);
						dlData.appendTrace( s + "\n" );
					}
					dlData.stopAndQueue();
					// reduce counts
					ivars.numWaitingOrDLing--;
					for (ProcessTagVarsIncomplete tvars: tagVars ){
						tvars.numWaitingOrDLing--;
					}
					if (state == Download.ST_DOWNLOADING)
					{
						totals.downloading--;
						if (bActivelyDownloading || fakedActively)
							totals.activelyDLing--;
					} else
					{
						totals.waitingToDL--;
					}
					totals.maxSeeders = calcMaxSeeders(totals.activelyDLing + totals.waitingToDL);
				} catch (Exception ignore)
				{
					/* ignore */
				}

				state = dlData.getState();

			} else if (bDebugLog) {
				String s = "NOT queuing: ";
				if (maxDownloads == 0) {
					s += "maxDownloads = " + maxDownloads;
				} else if (!bOverLimit) {
					s += "not over limit.  numWaitingOrDLing(" + ivars.numWaitingOrDLing
							+ ") <= maxDLs(" + maxDLs + ")";
				} else if (!bActivelyDownloading || bDownloading) {
					s += "not actively downloading";
				} else if (totals.maxActive == 0) {
					s += "unlimited active allowed (set)";
				} else {
					s += "# active(" + (totals.activelyCDing + totals.activelyDLing)
							+ ") < maxActive(" + totals.maxActive + ")";
				}
				log.log(dlData.getRelatedTo(), LoggerChannel.LT_INFORMATION, s);
				dlData.appendTrace( s + "\n" );
			}
		}

		if (state == Download.ST_READY) {
			if ((maxDownloads == 0) || (totals.activelyDLing < maxDLs)) {
				try {
					if (bDebugLog) {
						String s = "   start: READY && activelyDLing ("
								+ totals.activelyDLing + ") < maxDLs (" + maxDownloads + ")";
						log.log(dlData.getRelatedTo(), LoggerChannel.LT_INFORMATION, s);
						dlData.appendTrace( s + "\n" );
					}
					dlData.start();

					// adjust counts
					totals.waitingToDL--;
					totals.activelyDLing++;
					totals.maxSeeders = calcMaxSeeders(totals.activelyDLing
							+ totals.waitingToDL);
				} catch (Exception e) {
					
					Debug.out( e );
				}
				state = dlData.getState();
			}
		}

		boolean tvarsMaxDLsExceeded = false;
		
		if ( state == Download.ST_QUEUED ){
			if ((maxDownloads == 0) || 
				(ivars.numWaitingOrDLing < maxDLs)) {
								
				for (ProcessTagVarsIncomplete tvars: tagVars ){
					if ( tvars.numWaitingOrDLing + (tvars.isStrictLimit?tvars.stalledDownloaders:0) >= tvars.maxDLs){						
						tvarsMaxDLsExceeded = true;
						break;
					}
				}
				
				if ( !tvarsMaxDLsExceeded ){
					try {
						if (bDebugLog) {
							String s = "   restart: QUEUED && numWaitingOrDLing ("
									+ ivars.numWaitingOrDLing + ") < maxDLS (" + maxDLs + ")";
							log.log(LoggerChannel.LT_INFORMATION, s);
							dlData.appendTrace( s + "\n" );
						}
						dlData.restart();
	
						// increase counts
						ivars.numWaitingOrDLing++;
						for (ProcessTagVarsIncomplete tvars: tagVars ){
							tvars.numWaitingOrDLing++;
						}
						totals.waitingToDL++;
						totals.maxSeeders = calcMaxSeeders(totals.activelyDLing
								+ totals.waitingToDL);
					} catch (Exception ignore) {/*ignore*/
					}
					state = dlData.getState();
				}else{
					if ( bDebugLog ){
						String s = "Not starting, tag max exceeded";
						log.log(dlData.getRelatedTo(), LoggerChannel.LT_INFORMATION, s);
						dlData.appendTrace( s + "\n" );
					}
				}
			}
		}

		int oldState = state;
		state = dlData.getState();

		if (oldState != state) {
			if (bDebugLog) {
				log.log(LoggerChannel.LT_INFORMATION, ">> somethingChanged: state");
			}
			somethingChanged = true;
		}

		if (dlData.getSeedingRank() >= 0
				&& (state == Download.ST_QUEUED || state == Download.ST_READY
						|| state == Download.ST_WAITING || state == Download.ST_PREPARING)) {
			
			if ( tvarsMaxDLsExceeded ){
				for (ProcessTagVarsIncomplete tvars: tagVars ){
					if ( tvars.numWaitingOrDLing + ( tvars.isStrictLimit?tvars.stalledDownloaders:0) >= tvars.maxDLs ){
						tvars.higherDLtoStart = true;
					}
				}
			}else{
				ivars.higherDLtoStart = true;
			}
		}

		if (bDebugLog) {
			String s = "<< DL state=" + sStates.charAt(dlData.getState())
					+ ";shareRatio=" + dlData.getShareRatio()
					+ ";numW8tngorDLing=" + ivars.numWaitingOrDLing + ";maxCDrs="
					+ totals.maxSeeders + ";forced=" + boolDebug(dlData.isForceStart())
					+ ";actvDLs=" + totals.activelyDLing + ";hgherQd="
					+ boolDebug(ivars.higherDLtoStart) + ";ActDLing="
					+ boolDebug(dlData.getActivelyDownloading());
			log.log(dlData.getRelatedTo(), LoggerChannel.LT_INFORMATION, s);
			dlData.appendTrace( s + "\n" );
		}

		if (bStopOnceBandwidthMet) {
			vars.accumulatedDownloadSpeed += dlData.getDownloadAverage();
			vars.accumulatedUploadSpeed += dlData.getUploadAverage();
		}
	}

	/**
	 * Process Completed (Seeding) downloads, starting and stopping as needed
	 *
	 * @param dlDataArray All download data (rank objects) we handle
	 * @param dlData Current download data (rank object) we are processing
	 * @param vars Running calculations
	 * @param totals Summary values used in logic
	 */
	private void 
	handleCompletedDownload(
		DefaultRankCalculator 		dlData, 
		ProcessVars 				vars,
		ProcessTagVarsComplete[]	tagVars,
		TotalsStats 				totals,
		boolean						debugNoChange )
	{
		if (!totals.bOkToStartSeeding)
			return;

		int state = dlData.getState();
		boolean stateReadyOrSeeding = state == Download.ST_READY || state == Download.ST_SEEDING;

		String[] debugEntries = null;
		String sDebugLine = "";
		// Queuing process:
		// 1) Torrent is Queued (Stopped)
		// 2) Slot becomes available
		// 3) Queued Torrent changes to Waiting
		// 4) Waiting Torrent changes to Ready
		// 5) Ready torrent changes to Seeding (with startDownload)
		// 6) Trigger stops Seeding torrent
		//    a) Queue Ranking drops
		//    b) User pressed stop
		//    c) other
		// 7) Seeding Torrent changes to Queued.  Go to step 1.

		int numPeers = dlData.getLastModifiedScrapeResultPeers();
		boolean isFP = false;

		ProcessVarsComplete	cvars = vars.comp;

		if ( ENABLE_DLOG && false ){
			
			dlog.log( 
				dlData.getName() + 
				": tot:activelyCDing=" + totals.activelyCDing +
				", tot:waitingToSeed=" + totals.waitingToSeed +
				", tot:stalledSeeders=" + totals.stalledSeeders +
				", tot:maxSeeders=" 		+ totals.maxSeeders +
				", cva:numWaitingOrSeeding=" 	+ cvars.numWaitingOrSeeding +
				", cva:stalledSeeders=" 		+ cvars.stalledSeeders +
				", max=" + maxStalledSeeding + "/" + maxOverLimitSeeding +
				", scrape=" + dlData.getLastScrapeResultOk());
		}
		
		if (bDebugLog) {
			isFP = dlData.isFirstPriority();
			if ( dlData.isControllable()){
				debugEntries = new String[] {
					"CD state=" + sStates.charAt(state),
					"shareR=" + dlData.getShareRatio(),
					"nWorCDing=" + cvars.numWaitingOrSeeding,
					"sr=" + dlData.getSeedingRank(),
					"hgherQd=" + boolDebug(cvars.higherCDtoStart),
					"maxCDrs=" + totals.maxSeeders,
					"FP=" + boolDebug(isFP),
					"nActCDing=" + totals.activelyCDing,
					"ActCDing=" + boolDebug(dlData.getActivelySeeding()),
					"nSeeds=" + dlData.getLastModifiedScrapeResultSeeds(),
					"nPeers=" + dlData.getLastModifiedScrapeResultPeers()
				};
			}
		}

		try {
			boolean bScrapeOk = dlData.getLastScrapeResultOk();

			// Ignore rules and other auto-starting rules do not apply when
			// bAutoStart0Peers and peers == 0. So, handle starting 0 peers
			// right at the beginning, and loop early
			if (bAutoStart0Peers && numPeers == 0 && bScrapeOk) {
				if (state == Download.ST_QUEUED) {
					try {
						if (bDebugLog)
							sDebugLine += "\nrestart() 0Peers";
						dlData.restart(); // set to Waiting
						totals.waitingToSeed++;
						cvars.numWaitingOrSeeding++;
						
						for ( ProcessTagVarsComplete tv: tagVars ){
							tv.numWaitingOrSeeding++;
						}

						state = dlData.getState();
						if (state == Download.ST_READY) {
							if (bDebugLog)
								sDebugLine += "\nstart(); 0Peers";
							dlData.start();
							totals.activelyCDing++;
						}
					} catch (Exception ignore) {/*ignore*/
					}
				}
				if (state == Download.ST_READY) {
					try {
						if (bDebugLog)
							sDebugLine += "\nstart(); 0Peers";
						dlData.start();
						totals.activelyCDing++;
						cvars.numWaitingOrSeeding++;
						
						for ( ProcessTagVarsComplete tv: tagVars ){
							tv.numWaitingOrSeeding++;
						}
					} catch (Exception ignore) {/*ignore*/
					}
				}
				return;
			}

			int rank = dlData.getSeedingRank();

			// Short Circuit: if rank is set to IGNORED, we can skip everything
			// except when:
			// (1) torrent is force started
			// (2) the torrent is in ready or seeding state (we have to stop the torrent)
			// (3) we auto start 0 peers
			
			if (rank < DefaultRankCalculator.SR_IGNORED_LESS_THAN
					&& !dlData.isForceStart() && !stateReadyOrSeeding
					&& !bAutoStart0Peers) {
				if (bDebugLog) {
					sDebugLine += "\n  Skip !forceStart";
					int idx = rank * -1;
					if (idx < DefaultRankCalculator.SR_NEGATIVE_DEBUG.length) {
						sDebugLine += " && " + DefaultRankCalculator.SR_NEGATIVE_DEBUG[idx];
					}
				}
				return;
			}

			// Short Circuit: if seed higher in the queue is marked to start,
			// we can skip everything, except when:
			// (1) torrent is force started
			// (2) we auto start 0 peers
			// (3) the torrent is in ready or seeding state (we have to stop the torrent)
			if (cvars.higherCDtoStart && !dlData.isForceStart() && !bAutoStart0Peers
					&& !stateReadyOrSeeding) {
				sDebugLine += " a torrent with a higher rank is queued or starting";
			}

			if (bDebugLog && bAutoStart0Peers && numPeers == 0 && !bScrapeOk
					&& (state == Download.ST_QUEUED || state == Download.ST_READY)) {
				sDebugLine += "\n  NOT starting 0 Peer torrent because scrape isn't ok";
			}

			if (!bDebugLog) {
				// In debug mode, we already calculated FP
				isFP = dlData.isFirstPriority();
			}

		boolean bActivelySeeding = dlData.getActivelySeeding();
		boolean globalDownLimitReached;
		boolean globalUpLimitReached;
		boolean globalRateAdjustedActivelySeeding;
		boolean fakedActively;
		if (bStopOnceBandwidthMet) {
			boolean isRunning = dlData.getState() == Download.ST_SEEDING;
			globalUpLimitReached = totals.maxUploadSpeed() > 0 && ((double)vars.accumulatedUploadSpeed)/1024 > totals.maxUploadSpeed() * IGNORE_SLOT_THRESHOLD_FACTOR;
			globalDownLimitReached = globalDownloadLimit > 0 && ((double)vars.accumulatedDownloadSpeed)/1024 > globalDownloadLimit * IGNORE_SLOT_THRESHOLD_FACTOR;
			globalRateAdjustedActivelySeeding = bActivelySeeding || (isRunning && (globalUpLimitReached || globalDownLimitReached));
			fakedActively = globalRateAdjustedActivelySeeding && !bActivelySeeding;
			if(fakedActively)
				totals.activelyCDing++;
		} else {
			globalUpLimitReached = false;
			globalRateAdjustedActivelySeeding = bActivelySeeding;
			globalDownLimitReached = false;
			fakedActively = false;
		}

		boolean	increasedStalledSeeders = false;
		
		if ( state != Download.ST_QUEUED ){
			
	  		if ( state == Download.ST_SEEDING && !bActivelySeeding ){
	  			
	  			if ( stalledSeedingIgnoreZP && numPeers == 0 && bScrapeOk ){
	  				
	  				// ignore
	  			}else{
	  				
	  				cvars.stalledSeeders++;
	  				
	  				for ( ProcessTagVarsComplete tv: tagVars ){
  						tv.stalledSeeders++;
					}
	  				
	  				increasedStalledSeeders = true;
	  			}
	  		}
	  		
			if (	( 	state == Download.ST_READY || 
						state == Download.ST_WAITING ||
						state == Download.ST_PREPARING ||
					
						(	state == Download.ST_SEEDING && 
							globalRateAdjustedActivelySeeding && 
							!dlData.isForceStart()))){	// Forced Start torrents are pre-included in count
				
				cvars.numWaitingOrSeeding++;
				
				for ( ProcessTagVarsComplete tv: tagVars ){
					tv.numWaitingOrSeeding++;
				}
				
				if (bDebugLog)
					sDebugLine += "\n  Torrent is waiting or seeding";
			}else{
					// for tag limits we need to ensure we either increment stalled or waiting/seeding for all
					// downloads regardless as we use this to apply strict per-tag limits
				
				if ( !increasedStalledSeeders && tagVars.length > 0 ){
					
					if ( 	state == Download.ST_READY || 
							state == Download.ST_WAITING ||
							state == Download.ST_PREPARING ||
							state == Download.ST_SEEDING ){
						
						for ( ProcessTagVarsComplete tv: tagVars ){
							tv.numWaitingOrSeeding++;
						}
					}
				}
			}
			
			if ( bDebugLog && dlData.getReservedSlot() != null ){
				
				sDebugLine += "\n  Has reserved slot";
			}
		}


			// Is it OK to set this download to a queued state?
			// It is if:
			//   1) It is either READY or SEEDING; and
			//   2) It is either one of the following; and
			//        a) Not a first priority torrent; or
			//        b) There is a limit to the number of active torrents, and the number of
			//           waiting and seeding torrents is already higher (or equal to) the number
			//           of maximum allowed active torrents (taking away the number of minimum
			//           required downloads).
			//
			//           So I understand that to mean - it isn't first priority and leaving
			//           this torrent running would mean that there aren't enough slots to
			//           fulfil the minimum downloads requirement, because there are so many
			//           torrents seeding (or waiting to seed) already. Or, in the case there
			//           is no minimum downloads requirement - it's just overrun the maximum
			//           active torrents count.
			//
			//   3) It hasn't been force started and is controllable
  		
			boolean underCurrentLimit 	= totals.maxActive == 0 || cvars.numWaitingOrSeeding + cvars.stalledSeeders < totals.maxSeeders + maxStalledSeeding ;
			
			ProcessTagVarsComplete tagLimitExceeded = null;
			
			if ( tagVars.length > 0 ){
				for ( ProcessTagVarsComplete tv: tagVars ){
					if ( tv.numWaitingOrSeeding + tv.stalledSeeders < tv.maxCDs + (tv.isStrictLimit?0:maxStalledSeeding )){
						
					}else{
						underCurrentLimit = false;
						tagLimitExceeded = tv;
						break;
					}
				}
			}
			
			boolean	underGlobalLimit	= totals.maxActive == 0 || totals.activelyCDing + totals.waitingToSeed + totals.stalledSeeders <  totals.maxSeeders + maxStalledSeeding + maxOverLimitSeeding;
			
			boolean atLimit = !( underCurrentLimit && underGlobalLimit );
			
			boolean controllable = dlData.isControllable();
			
			boolean forceStart = dlData.isForceStart();
			
			boolean okToQueue = 
					controllable &&
					stateReadyOrSeeding &&
					(!isFP || (isFP && atLimit )) && 
					!forceStart;

				// XXX do we want changes to take effect immediately  ?
			
			if (okToQueue && (state == Download.ST_SEEDING)) {
				
				long timeAlive = (SystemTime.getCurrentTime() - dlData.getTimeStarted());
				
				okToQueue = (timeAlive >= minTimeAlive);

				if (!okToQueue && bDebugLog)
					sDebugLine += "\n  Torrent can't be stopped yet, timeAlive("
							+ timeAlive + ") < minTimeAlive(" + minTimeAlive + ")";
			}


			boolean	up_limit_prohibits = false;

			if ( controllable && !okToQueue && !forceStart ){

				if ( totals.upLimitProhibitsNewSeeds ){

					okToQueue = true;

					up_limit_prohibits = true;
				}
			}

			// Note: First Priority are sorted to the top,
			//       so they will always start first

			boolean okToStart =
					controllable &&
					!okToQueue &&
					tagLimitExceeded == null &&  
					(state == Download.ST_QUEUED) &&
					(rank >= DefaultRankCalculator.SR_IGNORED_LESS_THAN ); 
					//&& (cvars.stalledSeeders < maxStalledSeeding)	// This test is bad as it stops all new seeds from starting once stalled max is hit. 
																	// regardless of maxSeeders. Remember that stalled are not counted in numWaitingOrSeeding
																	// fixed by incorporating in above maxActive test

			boolean higherCDPrevents = cvars.higherCDtoStart;
			
			boolean slotAvailable = underCurrentLimit && underGlobalLimit;
			
			RankCalculatorSlotReserver	reservedSlot = null;
						
			if ( okToStart && ( higherCDPrevents || !slotAvailable )){
				
				if ( 	dlData.getLightSeedEligibility() < 30*1000 && 
						dlData.getReservedSlot() == null && 
						!reservedSlots.isEmpty()){
									
					reservedSlot = reservedSlots.removeFirst();
					
					slotAvailable = true;
					
					higherCDPrevents = false;	// override this
				}
			}
			
			try{
				if ( okToStart && slotAvailable && !higherCDPrevents ){
					
					try {
						if (bDebugLog)
							sDebugLine += "\n  restart: ok2Q=" + okToQueue
									+ "; QUEUED && numWaitingOrSeeding( "
									+ cvars.numWaitingOrSeeding + ") < maxSeeders ("
									+ totals.maxSeeders + ")";
	
						if ( ENABLE_DLOG ){
							dlog.log( "    starting " + dlData.getName() + 
											": rank=" + rank + 
											", cvars=" + cvars.numWaitingOrSeeding + ", " + cvars.stalledSeeders + 
											", totals=" + totals.maxSeeders + ", " + totals.activelyCDing + ", " + totals.waitingToSeed + ", " + totals.stalledSeeders + 
											", limits=" + maxStalledSeeding + ", " + maxOverLimitSeeding );
						}
						
							/* If you come here wondering why you have so many active seeds when the max
							 * seeds and stalled seeds config say you should have a lot less then it is most
							 * likely to do with the fact that we process the seeds in their 'sort order' and
							 * accumulate totals (numWaitingOrSeeding + stalledSeeders) as we go through them.
							 * The order of the seeds can change a lot due to peers/seeds etc (depending on ranking
							 * algorithm) and this can lead to a new seed being started under the assumption that
							 * a lower ranked one (yet to be processed) will stop to compensate. However there is
							 * a 'minimum seeding time' that can prevent this from happening straight away. Thus
							 * seeds keep on being started until the minimum seeding time starts kicking in and
							 * allows others to stop...
							 * Obviously could use pre-computed totals to control this instead of accumulated ones 
							 * but this would stop priority seeds from starting while some crappy one sat around
							 * waiting for the seeding timer to expire.
							 * Also note that force-start seeds are treated as taking a slot - having this 'flexibility'
							 * at least allows other seeds a chance of starting if force-started > allowable seeds.
							 * Really I'm not sure if force-start should count at all... 
							 */
						
						dlData.restart(); // set to Waiting
						
						okToQueue = false;

						if ( reservedSlot != null ){
							
							dlData.setReservedSlot( reservedSlot );
							
							reservedSlotsAllocated.add( dlData );
							
							reservedSlot = null;
							
								// reserved slot already counted as seeding so no totals to update
						}else{
							
							totals.waitingToSeed++;
							cvars.numWaitingOrSeeding++;
							
							for ( ProcessTagVarsComplete tv: tagVars ){
								tv.numWaitingOrSeeding++;
							}
						}
						
						if (iRankType == RANK_TIMED)
							dlData.recalcSeedingRank();
					} catch (Exception ignore) {/*ignore*/
					}
					state = dlData.getState();
				} else if (bDebugLog && state == Download.ST_QUEUED){
					
					sDebugLine += "\n  NOT restarting:";
					
					if (rank < DefaultRankCalculator.SR_IGNORED_LESS_THAN) {
						sDebugLine += " torrent is being ignored";
						int idx = rank * -1;
						if (idx < DefaultRankCalculator.SR_NEGATIVE_DEBUG.length) {
							sDebugLine += ": " + DefaultRankCalculator.SR_NEGATIVE_DEBUG[idx];
						}
					} else if (cvars.higherCDtoStart) {
						sDebugLine += " a torrent with a higher rank is queued or starting";
					} else {
						if (okToQueue)
							sDebugLine += " no starting of okToQueue'd;";
	
						if ( tagLimitExceeded != null ){
							
							sDebugLine += " at tag limit, numWaitingOrSeeding + stalledSeeders("
									+ ( tagLimitExceeded.numWaitingOrSeeding + "+" + tagLimitExceeded.stalledSeeders ) + ") >= maxSeeders("
									+ ( tagLimitExceeded.maxCDs ) + ")";

						}else if ( cvars.numWaitingOrSeeding + cvars.stalledSeeders >= totals.maxSeeders + maxStalledSeeding ){
														
							sDebugLine += " at current limit, numWaitingOrSeeding + stalledSeeders("
									+ ( cvars.numWaitingOrSeeding + "+" + cvars.stalledSeeders ) + ") >= maxSeeders + maxStalledSeeding("
									+ ( totals.maxSeeders + "+" + maxStalledSeeding ) + ")";
					
						}else if ( totals.activelyCDing + totals.waitingToSeed + totals.stalledSeeders >=  totals.maxSeeders + maxStalledSeeding + maxOverLimitSeeding ){
							
							sDebugLine += " at global limit, activelyCDing + waitingToSeed + stalledSeeders("
									+ ( totals.activelyCDing + totals.waitingToSeed + totals.stalledSeeders ) + ") >= maxSeeders + maxStalledSeeding + maxOverLimitSeeding("
									+ ( totals.maxSeeders + maxStalledSeeding + maxOverLimitSeeding ) + ")";
					
						} else if ( up_limit_prohibits ){
	
							sDebugLine += " upload rate prohibits starting new seeds";
	
						} else {
	
							sDebugLine += "huh? qd="
									+ (state == Download.ST_QUEUED)
									+ "; "
									+ totals.maxActive
									+ ";"
									+ (cvars.numWaitingOrSeeding < totals.maxSeeders)
									+ ";"
									+ (cvars.stalledSeeders <= maxStalledSeeding)
									+ ";ignore?"
									+ (rank >= DefaultRankCalculator.SR_IGNORED_LESS_THAN);
						}
					}
				}
			}finally{
			
				if ( reservedSlot != null ){
					
						// not used in the end
					
					reservedSlots.add( reservedSlot );
				}
			}
			
			boolean bForceStop = false;
			// Start download if ready and slot is available
			if (	state == Download.ST_READY &&
					controllable ){
				if (rank >= DefaultRankCalculator.SR_IGNORED_LESS_THAN
						|| dlData.isForceStart()) {
					try {
						if (bDebugLog)
							sDebugLine += "\n  start: READY && total activelyCDing("
									+ totals.activelyCDing + ") < maxSeeders("
									+ totals.maxSeeders + ")";

						dlData.start();
						okToQueue = false;
					} catch (Exception ignore) {
						/*ignore*/
					}
					state = dlData.getState();
					totals.activelyCDing++;
					globalRateAdjustedActivelySeeding = bActivelySeeding = true;
					cvars.numWaitingOrSeeding++;
					
					for ( ProcessTagVarsComplete tv: tagVars ){
						tv.numWaitingOrSeeding++;
					}
				} else if (okToQueue) {
					// In between switching from STATE_WAITING and STATE_READY,
					// and ignore rule was met, so move it back to Queued
					bForceStop = true;
				}
			}

			// if there's more torrents waiting/seeding than our max, or if
			// there's a higher ranked torrent queued, stop this one
			if (okToQueue || bForceStop) {

				boolean okToStop = bForceStop;
				if (!okToStop) {
						// break up the logic into variables to make more readable
						// parg: added the "or up-limit-prohibits & higherCD" case because if we're not starting any new
						// seeds and we're below the seed limit we will never get to stop any lower priority seeds in order
						// to cause the up-rate to fall to the point where we can start a higher priority seed...

					
					boolean overCurrentLimit 	= totals.maxActive != 0 && cvars.numWaitingOrSeeding + cvars.stalledSeeders > totals.maxSeeders + maxStalledSeeding ;
					
					if ( !overCurrentLimit && tagVars.length > 0 ){
						for ( ProcessTagVarsComplete tv: tagVars ){
							if ( tv.numWaitingOrSeeding + tv.stalledSeeders > tv.maxCDs ){ //  + maxStalledSeeding ){
								overCurrentLimit = true;
								break;
							}
						}
					}
					
					boolean overGlobalLimit		= totals.maxActive != 0 && totals.activelyCDing + totals.waitingToSeed + totals.stalledSeeders >  totals.maxSeeders + maxStalledSeeding + maxOverLimitSeeding;
					
					boolean overLimit = overCurrentLimit || overGlobalLimit;
					
					overLimit |= 	( !bActivelySeeding && cvars.stalledSeeders > maxStalledSeeding ) ||
									(	( 	cvars.numWaitingOrSeeding >= totals.maxSeeders ||
											totals.upLimitProhibitsNewSeeds ) && 
										cvars.higherCDtoStart);

					boolean bSeeding = state == Download.ST_SEEDING;

						// not checking AND (at limit of seeders OR rank is set to ignore) AND
					
					okToStop = 	!dlData.isChecking() &&
								!dlData.isMoving() &&
								(overLimit || rank < DefaultRankCalculator.SR_IGNORED_LESS_THAN);

					if (bDebugLog) {
						if (okToStop) {
							sDebugLine += "\n  stopAndQueue: ";
							if (overLimit) {
								if (cvars.higherCDtoStart) {
									sDebugLine += "higherQueued (it should be seeding instead of this one)";
								} else if (!bActivelySeeding && cvars.stalledSeeders > totals.maxSeeders) {
									sDebugLine += "over stale seeds limit";
								} else {
									sDebugLine += "over limit";
								}
							} else if (rank < DefaultRankCalculator.SR_IGNORED_LESS_THAN)
								sDebugLine += "ignoreRule met";

							sDebugLine += " && ";
							if (bActivelySeeding)
								sDebugLine += "activelySeeding";
							else if (!bSeeding)
								sDebugLine += "not SEEDING";
							else if (!bActivelySeeding && bSeeding)
								sDebugLine += "SEEDING, but not actively";
						}
					} else {
						sDebugLine += "\n  NOT queuing: ";
						if (dlData.isChecking()){
							sDebugLine += "can't auto-queue a checking torrent";
						}else if (dlData.isMoving()){
							sDebugLine += "can't auto-queue a moving torrent";
						}else if (!overLimit){
							sDebugLine += "not over limit.  numWaitingOrSeeding("
									+ cvars.numWaitingOrSeeding + ") <= maxSeeders("
									+ totals.maxSeeders + ")";
						}else{
							sDebugLine += "bActivelySeeding=" + bActivelySeeding
									+ ";bSeeding" + bSeeding;
						}
					}
				} else {
					if (bDebugLog)
						sDebugLine += "\n  Forcing a stop..";
				}

				if (okToStop) {
					try {
						if (state == Download.ST_READY)
							totals.waitingToSeed--;

						if ( ENABLE_DLOG ){
							dlog.log( "    stopping" );
						}

						dlData.stopAndQueue();

						// okToQueue only allows READY and SEEDING state.. and in both cases
						// we have to reduce counts
						if (bActivelySeeding || fakedActively) {
							totals.activelyCDing--;
							bActivelySeeding = false;
						}
						if(globalRateAdjustedActivelySeeding)
						{
							cvars.numWaitingOrSeeding--;
							for ( ProcessTagVarsComplete tv: tagVars ){
								tv.numWaitingOrSeeding--;
							}
							globalRateAdjustedActivelySeeding = false;
							
						}else if ( increasedStalledSeeders ){
							cvars.stalledSeeders--;
			  				for ( ProcessTagVarsComplete tv: tagVars ){
		  						tv.stalledSeeders--;
							}
						}

						// force stop allows READY states in here, so adjust counts
						if (state == Download.ST_READY)
							totals.waitingToSeed--;
					} catch (Exception ignore) {
						/*ignore*/
					}

					state = dlData.getState();
				}
			}

			state = dlData.getState();
			if (rank >= 0
					&& (state == Download.ST_QUEUED || state == Download.ST_READY
							|| state == Download.ST_WAITING || state == Download.ST_PREPARING)) {
				cvars.higherCDtoStart = true;
			}

		} finally {
			if (bDebugLog) {
				if ( dlData.isControllable()){
					String[] debugEntries2 = new String[] {
						"CD state=" + sStates.charAt(dlData.getState()),
						"shareR=" + dlData.getShareRatio(),
						"nWorCDing=" + cvars.numWaitingOrSeeding,
						"sr=" + dlData.getSeedingRank(),
						"hgherQd=" + boolDebug(cvars.higherCDtoStart),
						"maxCDrs=" + totals.maxSeeders,
						"FP=" + boolDebug(isFP),
						"nActCDing=" + totals.activelyCDing,
						"ActCDing=" + boolDebug(dlData.getActivelySeeding()),
						"nSeeds=" + dlData.getLastModifiedScrapeResultSeeds(),
						"nPeers=" + dlData.getLastModifiedScrapeResultPeers()
					};
					
					String lastDebug = (String)dlData.getUserData( DEBUG_LINE_KEY );
					
					if ( lastDebug == null || !sDebugLine.equals( lastDebug )){
						
						dlData.setUserData( DEBUG_LINE_KEY, sDebugLine );
						
						debugNoChange = true;
					}
					printDebugChanges("", debugEntries, debugEntries2, sDebugLine, "  ", debugNoChange, dlData);
				}
			}
		}


		if (bStopOnceBandwidthMet) {
			vars.accumulatedUploadSpeed += dlData.getUploadAverage();
		}
	}

	private String boolDebug(boolean b) {
		return b ? "Y" : "N";
	}

	private void printDebugChanges(String sPrefixFirstLine, String[] oldEntries,
			String[] newEntries, String sDebugLine, String sPrefix,
			boolean bAlwaysPrintNoChangeLine, DefaultRankCalculator dlData) {
		boolean bAnyChanged = false;
		String sDebugLineNoChange = sPrefixFirstLine;
		StringBuilder sDebugLineOld = new StringBuilder(120);
		StringBuilder sDebugLineNew = new StringBuilder(120);
		for (int j = 0; j < oldEntries.length; j++) {
			if (oldEntries[j].equals(newEntries[j]))
				sDebugLineNoChange += oldEntries[j] + ";";
			else {
				sDebugLineOld.append(oldEntries[j]);sDebugLineOld.append(";");
				sDebugLineNew.append(newEntries[j]);sDebugLineNew.append(";");
				bAnyChanged = true;
			}
		}
		String sDebugLineOut = ((bAlwaysPrintNoChangeLine || bAnyChanged)
				? sDebugLineNoChange : "")
				+ (bAnyChanged ? "\nOld:" + sDebugLineOld + "\nNew:" + sDebugLineNew
						: "") + sDebugLine;
		if (!sDebugLineOut.equals("")) {
			String[] lines = sDebugLineOut.split("\n");
			for (int i = 0; i < lines.length; i++) {
				String s = sPrefix + ((i > 0) ? "  " : "") + lines[i];
				if (dlData == null) {
					log.log(LoggerChannel.LT_INFORMATION, s);
				} else {
					log.log(dlData.getRelatedTo(), LoggerChannel.LT_INFORMATION, s);
					dlData.appendTrace( s + "\n" );
				}
			}
		}
	}

	/**
	 * Request that the startstop rules process.  Used when it's known that
	 * something has changed that will effect torrent's state/position/rank.
	 */
	private long processMergeCount = 0;

	public void requestProcessCycle(DefaultRankCalculator rankToRecalc) {
		if (rankToRecalc != null) {
			try {
				ranksToRecalc_mon.enter();

				ranksToRecalc.add(rankToRecalc);
			} finally {
				ranksToRecalc_mon.exit();
			}
		}

		if (somethingChanged) {
			processMergeCount++;
		} else {
			somethingChanged = true;
		}
	}

	protected boolean
	getTagFP()
	{
		return( bTagFirstPriority );
	}
	
	protected void
	setFPTagStatus(
		com.biglybt.core.download.DownloadManager		dm,
		boolean											is_fp )
	{
		if ( fp_tag != null && bTagFirstPriority ){
			
			if ( is_fp ){
				
				if ( !fp_tag.hasTaggable( dm )){
				
					fp_tag.addTaggable( dm );
				}
			}else{
				
				if ( fp_tag.hasTaggable( dm )){
				
					fp_tag.removeTaggable( dm );
				}
			}
		}
	}
	
	protected String
	getSlotStatus()
	{
		return( slotStatus );
	}
	
	@Override
	public void generate(IndentWriter writer) {
		writer.println("StartStopRules Manager");

		try {
			writer.indent();
			writer.println("Started " + TimeFormatter.format100ths(SystemTime.getMonotonousTime() - monoStartedOn)
					+ " ago");
			writer.println("debugging = " + bDebugLog);
			writer.println("downloadDataMap size = " + rankCalculatorMap.size());
			if (changeCheckCount > 0) {
				writer.println("changeCheck CPU ms: avg="
						+ (changeCheckTotalMS / changeCheckCount) + "; max = "
						+ changeCheckMaxMS);
			}

			if (processCount > 0) {
				writer.println("# process cycles: " + processCount);

				writer.println("process CPU ms: avg=" + (processTotalMS / processCount)
						+ "; max = " + processMaxMS);
				if (processCount > 1) {
					writer.println("process avg gap: "
							+ (processTotalGap / ((processCount - 1))) + "ms");
				}
				writer.println("Avg # recalcs per process cycle: "
						+ (processTotalRecalcs / processCount));
				if (processTotalZeroRecalcs > 0) {
					writer.println("# process cycle with 0 recalcs: "
							+ processTotalZeroRecalcs);
				}
			}

		} catch (Exception e) {
			// ignore
		} finally {
			writer.exdent();
		}
	}

	public void addListener(StartStopRulesFPListener listener) {
		listenersFP.add(listener);
	}

	public void removeListener(StartStopRulesFPListener listener) {
		listenersFP.remove(listener);
	}

	public List getFPListeners() {
		return listenersFP.getList();
	}

	public interface
	UIAdapter
	{
		public void
		openDebugWindow(
			DefaultRankCalculator		dlData );
	}
} // class

