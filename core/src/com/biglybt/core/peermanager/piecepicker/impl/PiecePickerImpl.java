/*
 * Created by Joseph Bridgewater
 * Created on Jan 2, 2006
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

package com.biglybt.core.peermanager.piecepicker.impl;

import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.core.disk.*;
import com.biglybt.core.disk.impl.piecemapper.DMPieceList;
import com.biglybt.core.disk.impl.piecemapper.DMPieceMap;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.peer.*;
import com.biglybt.core.peer.impl.PEPeerControl;
import com.biglybt.core.peer.impl.PEPeerTransport;
import com.biglybt.core.peer.impl.PEPieceImpl;
import com.biglybt.core.peermanager.control.PeerControlScheduler;
import com.biglybt.core.peermanager.control.PeerControlSchedulerFactory;
import com.biglybt.core.peermanager.control.SpeedTokenDispenser;
import com.biglybt.core.peermanager.piecepicker.*;
import com.biglybt.core.peermanager.piecepicker.util.BitFlags;
import com.biglybt.core.util.*;

/**
 * @author MjrTom
 *
 */

public class PiecePickerImpl
implements PiecePicker
{
	private static final boolean	LOG_RTA				= false;
	private static final boolean 	EGM_IS_BLOCK_BASED	= true;
	
	private static final LogIDs LOGID = LogIDs.PIECES;

	/** min ms for recalculating availability - reducing this has serious ramifications */
	private static final long TIME_MIN_AVAILABILITY	= 974;
	private static final long TIME_MIN_FILE_AVAILABILITY	= 5*1000;
	/** min ms for recalculating base priorities */
	private static final long TIME_MIN_PRIORITIES	= 999;
	/** min ms for forced availability rebuild */
	private static final long TIME_AVAIL_REBUILD	= 5*60*1000 -24;

	// The following are added to the base User setting based priorities (for all inspected pieces)
	/** user select prioritize first/last */
	private static final int PRIORITY_W_FIRSTLAST	= 999;
	/** min # pieces in file for first/last prioritization */
	private static final long FIRST_PIECE_MIN_NB	= 4;
	/** number of pieces for first pieces prioritization */
	// private static final int FIRST_PIECE_RANGE_PERCENT= 10;
	/** user sets file as "High" */
	private static final int PRIORITY_W_FILE_BASE	= 1000;	// min amount added for priority file. > first/last piece priority offset to give file priority priority over f/l piece priority
	private static final int PRIORITY_W_FILE_RANGE	= 1000;	// priority range added for prioritized files
	/** Additional boost for more completed High priority */
	private static final int PRIORITY_W_COMPLETION	= 2000;

	// The following are only used when resuming already running pieces
	/** priority boost due to being too old */
	private static final int PRIORITY_W_AGE			= 900;
	/** ms a block is expected to complete in */
	private static final int PRIORITY_DW_AGE		= 60*1000;
	/** ms since last write */
	private static final int PRIORITY_DW_STALE		= 120*1000;
	/** finish pieces already almost done */
	private static final int PRIORITY_W_PIECE_DONE	= 900;
	/** keep working on same piece */
	private static final int PRIORITY_W_SAME_PIECE	= 700;

	/** currently webseeds + other explicit priorities are around 10000 or more - at this point we ignore rarity */

	private static final int PRIORITY_OVERRIDES_RAREST	= 9000;

    private static final int PRIORITY_REQUEST_HINT	= 3000;

    private static final int PRIORITY_SEQUENTIAL_START = 100000;
    
	/** priority at and above which pieces require real-time scheduling */

	private static final int PRIORITY_REALTIME		= 9999999;

	private static final int PRIORITY_FORCED		= 1000000;

	/** Min number of requests sent to a peer */
	private static final int REQUESTS_MIN_MIN	= 2;
	private static final int REQUESTS_MIN_MAX	= 8;
	/** Max number of request sent to a peer */
	private static final int REQUESTS_MAX	= 512;
	/** Default number of requests sent to a peer, (for each X B/s another request will be used) */
	private static final int SLOPE_REQUESTS	= 4*1024;

	private static final long RTA_END_GAME_MODE_SIZE_TRIGGER	= 16 * DiskManager.BLOCK_SIZE;
	private static final long END_GAME_MODE_RESERVED_TRIGGER	= 5 * 1024*1024;
	private static final long END_GAME_MODE_SIZE_TRIGGER		= 20 * 1024*1024;
	
	private static final long RTA_END_GAME_MODE_SIZE_TRIGGER_BLOCKS	= RTA_END_GAME_MODE_SIZE_TRIGGER / DiskManager.BLOCK_SIZE;
	private static final long END_GAME_MODE_RESERVED_TRIGGER_BLOCKS	= END_GAME_MODE_RESERVED_TRIGGER / DiskManager.BLOCK_SIZE;
	private static final long END_GAME_MODE_SIZE_TRIGGER_BLOCKS		= END_GAME_MODE_SIZE_TRIGGER / DiskManager.BLOCK_SIZE;
	
	private static final long END_GAME_MODE_TIMEOUT				= 120*1000;
	
	private static final int	NO_REQUEST_BACKOFF_MAX_MILLIS	= 5*1000;
	private static final int	NO_REQUEST_BACKOFF_MAX_LOOPS	= NO_REQUEST_BACKOFF_MAX_MILLIS / PeerControlScheduler.SCHEDULE_PERIOD_MILLIS;

	static final Random 	random = new Random();

	private final DiskManager			diskManager;
	private final PEPeerControl			peerControl;

	private final DiskManagerListenerImpl	diskManagerListener;

	protected final Map					peerListeners;
	private final PEPeerManagerListener	peerManagerListener;

	private final int					nbPieces;
	private final DiskManagerPiece[]	dmPieces;
	private final PEPiece[]				pePieces;
	private final int					pieceSize;

	private final List<PEPiece>	rarestStartedPieces; //List of pieces started as rarest first

	protected final AEMonitor availabilityMon = new AEMonitor("PiecePicker:avail");
	
	private final Object endGameModeChunkLock = new Object();

	protected volatile int	nbPiecesDone;

	/** asyncronously updated availability */
	protected volatile int[]	availabilityAsynch;
	/** indicates availability needs to be recomputed due to detected drift */
	protected volatile long		availabilityDrift;
	private long				timeAvailRebuild =TIME_AVAIL_REBUILD;

	/** periodically updated consistent view of availability for calculating */
	protected volatile int[]	availability;

	private long				time_last_avail;
	protected volatile long	availabilityChange;
	private volatile long		availabilityComputeChange;
	private long			time_last_rebuild;

	private long	timeAvailLessThanOne;
	private float	globalAvail;
	private float	globalAvgAvail;
	private int		nbRarestActive;
	private int		globalMin;
	private int		globalMax;
	private long bytesUnavailable;
	/**
	 * The rarest availability level of pieces that we affirmatively want to try to request from others soonest
	 * ie; our prime targets for requesting rarest pieces
	 */
	private volatile int		globalMinOthers;

	/** event # of user file priority settings changes */
	protected volatile long		filePriorityChange;

	protected volatile int			sequentialDownload 	= 0;
	
	/** last user parameter settings event # when priority bases were calculated */
	private volatile long		priorityParamChange;
	/** last user priority event # when priority bases were calculated */
	private volatile long		priorityFileChange;
	/** last availability event # when priority bases were calculated */
	private volatile long		priorityAvailChange;

	private boolean 			priorityRTAexists;

	/** time that base priorities were last computed */
	private long				timeLastPriorities;

	/** the priority for starting each piece/base priority for resuming */
	private int[]				startPriorities;

	protected volatile boolean	hasNeededUndonePiece;
	protected volatile long		neededUndonePieceChange;

	/** A flag to indicate when we're in endgame mode */
	private volatile boolean	endGameMode;
	private volatile boolean	endGameModeAbandoned;
	private volatile long		timeEndGameModeEntered;
	/** The list of chunks needing to be downloaded (the mechanism change when entering end-game mode) */
	private LinkedList<EndGameModeChunk> 	endGameModeChunks;
	private Map<Long,EndGameModeChunk>		endGameModeChunkMap;
	
	private long				lastProviderRecalcTime;
	private final CopyOnWriteList		rta_providers = new CopyOnWriteList();
	private long[]				provider_piece_rtas;
	private final CopyOnWriteList		priority_providers = new CopyOnWriteList();
	private long[]				provider_piece_priorities;

	private int					allocate_request_loop_count;

	private int					max_file_priority;
	private int					min_file_priority;

	private boolean				reverse_block_order;
	private int[]				global_request_hint;

	private static boolean		enable_request_hints;
	private static boolean		includeLanPeersInReqLimiting;

	private final CopyOnWriteList<PiecePickerListener>		listeners = new CopyOnWriteList<>();

	private volatile float[]	fileAvailabilities;
	private volatile long		fileAvailabilitiesCalcTime;

	private volatile CopyOnWriteSet<Integer>	forced_pieces;

	protected static volatile boolean	firstPiecePriority;
	protected static volatile long		firstPriorityBytes;
	protected static volatile boolean	firstPiecePriorityForce;
	protected static volatile boolean	completionPriority;
	
		/** event # of user settings controlling priority changes */
	
	protected static volatile long		paramPriorityChange = Long.MIN_VALUE;

	static{

		COConfigurationManager.addAndFireParameterListeners(
			new String[]{
				"Prioritize Most Completed Files",
				ConfigKeys.Transfer.BCFG_PRIORITIZE_FIRST_PIECE,	
				ConfigKeys.Transfer.ICFG_PRIORITIZE_FIRST_MB,	
				ConfigKeys.Transfer.BCFG_PRIORITIZE_FIRST_PIECE_FORCE	
			},
			(n)->{
				long MB = DisplayFormatters.getMinB();
				
				completionPriority 		= COConfigurationManager.getBooleanParameter( "Prioritize Most Completed Files" );			
				firstPiecePriority 		= COConfigurationManager.getBooleanParameter( ConfigKeys.Transfer.BCFG_PRIORITIZE_FIRST_PIECE );
				firstPriorityBytes 		= COConfigurationManager.getIntParameter( ConfigKeys.Transfer.ICFG_PRIORITIZE_FIRST_MB )*MB;
				firstPiecePriorityForce = COConfigurationManager.getBooleanParameter( ConfigKeys.Transfer.BCFG_PRIORITIZE_FIRST_PIECE_FORCE );
				
				paramPriorityChange++;	// this is a user's priority change event
			  
			});
		
		COConfigurationManager.addAndFireParameterListeners(
				new String[]{
					"Piece Picker Request Hint Enabled",
					"LAN Speed Enabled"
				},
				(n)->{
					
			    	enable_request_hints			= COConfigurationManager.getBooleanParameter( "Piece Picker Request Hint Enabled" );
					includeLanPeersInReqLimiting 	= !COConfigurationManager.getBooleanParameter( "LAN Speed Enabled" );
				});
	}


	public PiecePickerImpl(final PEPeerControl pc)
	{
		// class administration first

		peerControl	= pc;
		diskManager = peerControl.getDiskManager();
		dmPieces =diskManager.getPieces();
		nbPieces =diskManager.getNbPieces();
		pieceSize = diskManager.getPieceLength();
		nbPiecesDone =0;

		pePieces = pc.getPieces();

		// now do stuff related to availability
		availability =new int[nbPieces];  //always needed


		hasNeededUndonePiece =false;
		neededUndonePieceChange =Long.MIN_VALUE;

		// ensure all periodic calculaters perform operations at least once
		time_last_avail =Long.MIN_VALUE;
		availabilityChange =Long.MIN_VALUE +1;
		availabilityComputeChange =Long.MIN_VALUE;
		availabilityDrift =nbPieces;

		// initialize each piece; on going changes will use event driven tracking
		for (int i =0; i <nbPieces; i++)
		{
			if (dmPieces[i].isDone()){
				availability[i]++;
				nbPiecesDone++;
			}else{
				hasNeededUndonePiece |=dmPieces[i].calcNeeded();
			}
		}
		if (hasNeededUndonePiece)
			neededUndonePieceChange++;

		updateAvailability();

		// with availability charged and primed, ready for peer messages
		peerListeners =new HashMap();
		peerManagerListener =new PEPeerManagerListenerImpl();
		peerControl.addListener(peerManagerListener);


		// now do stuff related to starting/continuing pieces
		rarestStartedPieces = new ArrayList();

//		startPriorities =new long[nbPieces];
		filePriorityChange =Long.MIN_VALUE;

		priorityParamChange =Long.MIN_VALUE;
		priorityFileChange =Long.MIN_VALUE;
		priorityAvailChange =Long.MIN_VALUE;

		timeLastPriorities =Long.MIN_VALUE;


		endGameMode =false;
		endGameModeAbandoned =false;
		timeEndGameModeEntered =0;

//		computeBasePriorities();

		// with priorities charged and primed, ready for dm messages
		diskManagerListener =new DiskManagerListenerImpl();

		syncFilePriorities();

		diskManager.addListener(diskManagerListener);
	}

	@Override
	public PEPeerManager getPeerManager(){
		return( peerControl);
	}
	
	@Override
	public final void addHavePiece(final PEPeer peer, final int pieceNumber)
	{
		// peer is null if called from disk-manager callback
		try
		{	availabilityMon.enter();
		if ( availabilityAsynch == null ){
			availabilityAsynch = (int[])availability.clone();
		}
		++availabilityAsynch[pieceNumber];
		availabilityChange++;
		} finally {availabilityMon.exit();}

		// if this is an interesting piece then clear any record of "no requests" so the peer gets
		// scheduled next loop

		if ( peer != null && dmPieces[pieceNumber].isDownloadable()){
			peer.setConsecutiveNoRequestCount(0);
		}
	}

	/**
	 * This methd will compute the pieces' overall availability (including ourself)
	 * and the _globalMinOthers & _globalAvail
	 */
	@Override
	public final void updateAvailability()
	{
		final long now =SystemTime.getCurrentTime();
		if (now >=time_last_avail &&now <time_last_avail +TIME_MIN_AVAILABILITY ){
			return;
		}

		if ( availabilityDrift >0 || now < time_last_rebuild ||  (now - time_last_rebuild) > timeAvailRebuild){
			try
			{	availabilityMon.enter();

			time_last_rebuild	= now;
			final int[]	new_availability = recomputeAvailability();

			if (Constants.isCVSVersion())
			{
				final int[]   old_availability =availabilityAsynch ==null ?availability :availabilityAsynch;
				int	errors	= 0;

				for (int i=0;i<new_availability.length;i++){
					if ( new_availability[i] != old_availability[i]){
						errors++;
					}
				}
				if (errors >0 &&errors !=nbPieces)
				{
					if (Logger.isEnabled())
						Logger.log(new LogEvent(peerControl, LOGID, LogEvent.LT_ERROR,
								"updateAvailability(): availability rebuild errors = " +errors
								+" timeAvailRebuild =" +timeAvailRebuild
						));
					timeAvailRebuild -=errors;
				} else
					timeAvailRebuild++;
			}

			availabilityAsynch	= new_availability;

			availabilityDrift =0;
			availabilityChange++;
			} finally {availabilityMon.exit();}

		}else if (availabilityComputeChange >=availabilityChange){
			return;
		}

		try
		{	availabilityMon.enter();
		time_last_avail =now;
		availabilityComputeChange =availabilityChange;

		// take a snapshot of availabilityAsynch
		if ( availabilityAsynch != null ){
			availability 		= availabilityAsynch;
			availabilityAsynch	= null;
		}
		} finally {availabilityMon.exit();}

		int i;
		int allMin =Integer.MAX_VALUE;
		int allMax =0;
		int rarestMin =Integer.MAX_VALUE;
		for (i =0; i <nbPieces; i++)
		{
			final int avail =availability[i];
			final DiskManagerPiece dmPiece =dmPieces[i];
			final PEPiece	pePiece = pePieces[i];

			if (avail >0 &&avail <rarestMin && dmPiece.isDownloadable() && (pePiece == null || pePiece.isRequestable()))
				rarestMin =avail;	// most important targets for near future requests from others

			if (avail <allMin)
				allMin =avail;
			if (avail > allMax)
				allMax =avail;
		}
		// copy updated local variables into globals
		globalMin =allMin;
		globalMax =allMax;
		globalMinOthers =rarestMin;

		int total =0;
		int rarestActive =0;
		long totalAvail =0;
		long newBytesUnavailable = 0;
		for (i =0; i <nbPieces; i++ )
		{
			final int avail =availability[i];
			final DiskManagerPiece dmPiece =dmPieces[i];
			final PEPiece	pePiece = pePieces[i];

			if (avail >0)
			{
				if (avail >allMin)
					total++;
				if (avail <=rarestMin &&dmPiece.isDownloadable() && pePiece != null && !pePiece.isRequested())
					rarestActive++;
				totalAvail +=avail;
			} else {
				newBytesUnavailable += dmPiece.getLength();
			}
		}
		// copy updated local variables into globals
		float newGlobalAvail = (total /(float) nbPieces) +allMin;
		if ( globalAvail >= 1.0 &&  newGlobalAvail < 1.0 ){
			timeAvailLessThanOne = now;
		}else if ( newGlobalAvail >= 1.0 ){
			timeAvailLessThanOne= 0;
		}

		bytesUnavailable = newBytesUnavailable;
		globalAvail = newGlobalAvail;
		nbRarestActive =rarestActive;
		globalAvgAvail =totalAvail /(float)(nbPieces)
		/(1 +peerControl.getNbSeeds() +peerControl.getNbPeers());
	}

	private int[] recomputeAvailability()
	{
		if (availabilityDrift >0 &&availabilityDrift !=nbPieces &&Logger.isEnabled())
			Logger.log(new LogEvent(diskManager.getTorrent(), LOGID, LogEvent.LT_INFORMATION,
					"Recomputing availabiliy. Drift="+availabilityDrift+":"+peerControl.getDisplayName()));
		final List peers =peerControl.getPeers();

		final int[]	newAvailability = new int[nbPieces];
		int j;
		int i;
		// first our pieces
		for (j =0; j <nbPieces; j++)
			newAvailability[j] =dmPieces[j].isDone() ?1 :0;
		//for all peers
		final int peersSize =peers.size();
		for (i =0; i <peersSize; i++)
		{	//get the peer connection
			final PEPeer peer =(PEPeerTransport)peers.get(i);
			if (peer !=null &&peer.getPeerState() ==PEPeer.TRANSFERING)
			{
				//cycle trhough the pieces they actually have
				final BitFlags peerHavePieces =peer.getAvailable();
				if (peerHavePieces !=null &&peerHavePieces.nbSet >0)
				{
					for (j =peerHavePieces.start; j <=peerHavePieces.end; j++)
					{
						if (peerHavePieces.flags[j])
							++newAvailability[j];
					}
				}
			}
		}

		return newAvailability;
	}

	@Override
	public int
	getNumberOfPieces()
	{
		return( nbPieces );
	}

	@Override
	public final int[] getAvailability()
	{
		return availability;
	}

	@Override
	public final int getAvailability(final int pieceNumber)
	{
		return availability[pieceNumber];
	}

	//this only gets called when the My Torrents view is displayed
	@Override
	public final float getMinAvailability()
	{
		return globalAvail;
	}

	@Override
	public float
	getMinAvailability( int fileIndex )
	{
		float[]	avails = fileAvailabilities;

		if ( avails == null ){

			DiskManagerFileInfo[] files = diskManager.getFiles();

			avails = new float[ files.length ];
		}

		if ( avails.length == 1 ){

			if ( fileAvailabilities == null ){

				fileAvailabilities = avails;
			}

			return( getMinAvailability());
		}

		long now = SystemTime.getMonotonousTime();

		if ( fileAvailabilities == null || now - fileAvailabilitiesCalcTime > TIME_MIN_FILE_AVAILABILITY ){

			int[]	current_avail = availability;

			if ( current_avail == null ){

				return( 0 );
			}

			DiskManagerFileInfo[] files = diskManager.getFiles();

			for ( int i=0;i<files.length;i++ ){

				DiskManagerFileInfo file = files[i];

				int	start	= file.getFirstPieceNumber();
				int	end		= start + file.getNbPieces();

				int	min_avail = Integer.MAX_VALUE;

				for ( int j=start; j<end; j++ ){

					int a = current_avail[j];

					min_avail = Math.min( a, min_avail );
				}

				int total = 0;

				for ( int j=start; j<end; j++ ){

					int a = current_avail[j];

					if (a > 0 ){

						if ( a > min_avail ){

							total++;
						}
					}
				}

				avails[i] = (total /(float)(end-start+1)) + min_avail;
			}

			fileAvailabilities 			= avails;
			fileAvailabilitiesCalcTime	= now;
		}

		return( avails[ fileIndex ]);
	}

	@Override
	public final long getBytesUnavailable() {
		return bytesUnavailable;
	}

	@Override
	public final long getAvailWentBadTime()
	{
		return(timeAvailLessThanOne);
	}

	@Override
	public final int getMaxAvailability()
	{
		return globalMax;
	}

	@Override
	public final float getAvgAvail()
	{
		return globalAvgAvail;
	}

	@Override
	public int getNbPiecesDone()
	{
		return nbPiecesDone;
	}


	/**
	 * Early-outs when finds a downloadable piece
	 * Either way sets hasNeededUndonePiece and neededUndonePieceChange if necessary
	 */
	protected final void checkDownloadablePiece()
	{
		for (int i =0; i <nbPieces; i++)
		{
			if (dmPieces[i].isInteresting())
			{
				if (!hasNeededUndonePiece)
				{
					hasNeededUndonePiece =true;
					neededUndonePieceChange++;
				}
				return;
			}
		}
		if (hasNeededUndonePiece)
		{
			hasNeededUndonePiece =false;
			neededUndonePieceChange++;
		}
	}

	/**
	 * one reason requests don't stem from the individual peers is so the connections can be
	 * sorted by best uploaders, providing some ooprtunity to download the most important
	 * (ie; rarest and/or highest priority) pieces faster and more reliably
	 */
	@Override
	public final void allocateRequests()
	{
		if (!hasNeededUndonePiece){
			return;
		}

		allocate_request_loop_count++;

		final List peers =peerControl.getPeers();
		final int peersSize =peers.size();

		//final long[] upRates =new long[peersSize];
		final ArrayList<PEPeerTransport> bestUploaders = new ArrayList<>(peersSize);

		for (int i =0; i <peersSize; i++){

			final PEPeerTransport peer =(PEPeerTransport) peers.get(i);

			if (peer.isDownloadPossible())
			{

				int	no_req_count 	= peer.getConsecutiveNoRequestCount();

				if ( 	no_req_count == 0 ||
						allocate_request_loop_count % ( no_req_count + 1 ) == 0 )
				{
					bestUploaders.add(peer);

					//final long upRate =peer.getStats().getSmoothDataReceiveRate();
					//UnchokerUtil.updateLargestValueFirstSort(upRate, upRates, peer, bestUploaders, 0);
				}
			}
		}

		/* sort all peers we're currently downloading from
		 * with the most favorable for the next request one as 1st entry
		 * randomize list first to not pick the same candidates if the list of best doesn't return conclusive results
		 */
		Collections.shuffle(bestUploaders);

		for ( int i=0;i<3;i++){
			try{

				Collections.sort(bestUploaders, new Comparator<PEPeerTransport>() {
					@Override
					public int compare(PEPeerTransport pt1, PEPeerTransport pt2) {
						if ( pt1 == pt2 ){
							return( 0 );
						}

						PEPeerStats stats2 = pt2.getStats();
						PEPeerStats stats1 = pt1.getStats();
						/* pt1 comes first if we want to request data from it more than from pt2
						 * it is "smaller", i.e. return is < 0
						 */
						int toReturn = 0;

						// lan peers to the front of the queue as they'll ignore request limiting
						if(pt1.isLANLocal() && !pt2.isLANLocal())
							toReturn = -1;
						else if(!pt1.isLANLocal() && pt2.isLANLocal())
							toReturn = 1;

						// try to download from the currently fastest, this is important for the request focusing
						if(toReturn == 0)
							toReturn = (int)(stats2.getSmoothDataReceiveRate() - stats1.getSmoothDataReceiveRate());

						// we're here because we're not requesting anything from both peers
						// which means we might have to send the next request to this peer

						// first try to download from peers that we're uploading to, that should stabilize tit-for-tat a bit
						if(toReturn == 0 && (!pt2.isChokedByMe() || !pt1.isChokedByMe()))
							toReturn = (int)(stats2.getDataSendRate() - stats1.getDataSendRate());

						// ok, we checked all downloading and uploading peers by now
						// avoid snubbed ones for the next step here
						if(toReturn == 0 && pt2.isSnubbed() && !pt1.isSnubbed())
							toReturn = -1;
						if(toReturn == 0 && !pt2.isSnubbed() && pt1.isSnubbed())
							toReturn = 1;

						// try some peer we haven't downloaded from yet (this should allow us to taste all peers)
						if(toReturn == 0 && stats2.getTotalDataBytesReceived() == 0 && stats1.getTotalDataBytesReceived() > 0)
							toReturn = 1;
						if(toReturn == 0 && stats1.getTotalDataBytesReceived() == 0 && stats2.getTotalDataBytesReceived() > 0)
							toReturn = -1;

						/*

						// still nothing, next try peers from which we have downloaded most in the past
						// NO, we don't want to focus on what may have become a bad peer
						if(toReturn == 0)
							toReturn = (int)(stats2.getTotalDataBytesReceived() - stats1.getTotalDataBytesReceived());
						*/

						return toReturn;
					}
				});

				break;

			}catch( IllegalArgumentException e ){
				// jdk1.7 introduced this exception
				// java.lang.IllegalArgumentException: Comparison method violates its general contract!
				// under contract violation. We have an unstable comparator here as it uses all sorts of
				// data that can change during the sort. To fix this properly we would need to cache this
				// data for the duration of the sort, which is expensive given that we don't hugely care
				// for the accuracy of this sort. So swallow the occasional error
			}
		}

		final int uploadersSize =bestUploaders.size();

		if ( uploadersSize == 0 ){

			// no usable peers, bail out early
			return;
		}

		int REQUESTS_MIN;

		boolean	done_priorities = false;

		if ( priorityRTAexists ){

			REQUESTS_MIN = REQUESTS_MIN_MIN;

			final Map[] peer_randomiser = { null };

				// to keep the ordering consistent we need to use a fixed metric unless
				// we remove + re-add a peer, at which point we need to take account of
				// the fact that it has a new request allocated

			final Map block_time_order_peers_metrics = new HashMap( uploadersSize );

			Set	block_time_order_peers =
				new TreeSet(
						new Comparator()
						{
							@Override
							public int
							compare(
								Object arg1,
								Object arg2)
							{
								if ( arg1 == arg2 ){

									return( 0 );
								}

								PEPeerTransport pt1	= (PEPeerTransport)arg1;
								PEPeerTransport pt2	= (PEPeerTransport)arg2;

								Integer m1 = (Integer)block_time_order_peers_metrics.get( pt1 );

								if ( m1 == null ){

									m1 = new Integer( getNextBlockETAFromNow( pt1 ));

									block_time_order_peers_metrics.put( pt1, m1 );
								}

								Integer m2 = (Integer)block_time_order_peers_metrics.get( pt2 );

								if ( m2 == null ){

									m2 = new Integer( getNextBlockETAFromNow( pt2 ));

									block_time_order_peers_metrics.put( pt2, m2 );
								}

								int	result = m1.intValue() - m2.intValue();

								if ( result == 0 ){

									Map	pr = peer_randomiser[0];

									if ( pr == null ){

										pr = peer_randomiser[0] = new LightHashMap( bestUploaders.size());
									}

									Integer	r_1 = (Integer)pr.get( pt1 );

									if ( r_1 == null ){

										r_1 = new Integer(random.nextInt());

										pr.put( pt1, r_1 );
									}

									Integer	r_2 = (Integer)pr.get( pt2 );

									if ( r_2 == null ){

										r_2 = new Integer(random.nextInt());

										pr.put( pt2, r_2 );
									}

									result = r_1.intValue() - r_2.intValue();

									if ( result == 0 ){

										result = pt1.hashCode() - pt2.hashCode();

										if ( result == 0 ){

												// v unlikely - inconsistent but better than losing a peer

											result = 1;
										}
									}
								}

								return( result );
							}
						});

			block_time_order_peers.addAll( bestUploaders );

			PEPeerTransport	best_uploader = (PEPeerTransport)bestUploaders.get(0);

			long best_block_eta = SystemTime.getCurrentTime() + getNextBlockETAFromNow( best_uploader );

			// give priority pieces the first look-in
			// we need to sort by how quickly the peer can get a block, not just its base speed

			boolean	allocated_request = true;

			Set	allocations_started	= new HashSet();

			try{
				while( allocated_request && priorityRTAexists ){

					allocated_request = false;

					while( !block_time_order_peers.isEmpty()){

						Iterator	it = block_time_order_peers.iterator();

						PEPeerTransport pt =(PEPeerTransport)it.next();

						it.remove();

						if ( !pt.isDownloadPossible() || pt.isSnubbed()){

							continue;
						}

						// ignore request number advice from peers in RTA mode, we gotta do what we can

						int maxRequests = REQUESTS_MIN +(int)( pt.getStats().getDataReceiveRate() /SLOPE_REQUESTS ) + 1;

						if ( maxRequests > REQUESTS_MAX || maxRequests < 0 ){

							maxRequests = REQUESTS_MAX;
						}

						int currentRequests = pt.getNbRequests();

						int	allowed_requests = maxRequests - currentRequests;

						if ( allowed_requests > 0 ){

							if ( !done_priorities ){

								done_priorities	= true;

								computeBasePriorities();

								if ( !priorityRTAexists ){

										// might have stopped RTA as this is calculated in computeBasePriorities

									break;
								}
							}

							if ( !allocations_started.contains( pt )){

								pt.requestAllocationStarts( startPriorities );

								allocations_started.add( pt );
							}

							if ( findRTAPieceToDownload( pt, pt == best_uploader, best_block_eta )){

									// add back in to see if we can allocate a further request

								if ( allowed_requests > 1 ){

									block_time_order_peers_metrics.remove( pt );

									block_time_order_peers.add( pt );
								}
							}
						}
					}
				}
			}finally{
				Iterator	it = allocations_started.iterator();

				while( it.hasNext()){
					((PEPeerTransport)it.next()).requestAllocationComplete();
				}
			}
		}else{

			int	required_blocks = (int)( diskManager.getRemainingExcludingDND()/DiskManager.BLOCK_SIZE );

			int	blocks_per_uploader = required_blocks / uploadersSize;

				// if we have plenty of blocks outstanding we can afford to be more generous in the
				// minimum number of requests we allocate

			REQUESTS_MIN = Math.max( REQUESTS_MIN_MIN, Math.min( REQUESTS_MIN_MAX, blocks_per_uploader/2 ));
		}

		checkEndGameMode();

		//dispenser.refill();

		for (int i =0; i <uploadersSize; i++){

			final PEPeerTransport pt =(PEPeerTransport) bestUploaders.get(i);

			// only request when there are still free tokens in the bucket or when it's a lan peer (which get sorted to the front of the queue)
			if(dispenser.peek(DiskManager.BLOCK_SIZE) < 1 && (!pt.isLANLocal() || includeLanPeersInReqLimiting))
				break;

			//System.out.println("#"+i+" "+pt.getStats().getSmoothDataReceiveRate());

			// can we transfer something?
			if (pt.isDownloadPossible()){
				int	peer_request_num = pt.getMaxNbRequests();

				// If request queue is too low, enqueue another request
				int maxRequests;
				if ( peer_request_num != -1 ){
					maxRequests = peer_request_num;
				}else{
					if (!pt.isSnubbed()){
						if (!endGameMode){

							int	peer_requests_min;

							if ( pt.getUnchokedForMillis() < 10*1000 ){

								peer_requests_min = REQUESTS_MIN;

							}else{

								peer_requests_min = REQUESTS_MIN_MIN;
							}

							maxRequests =peer_requests_min +(int) (pt.getStats().getDataReceiveRate() /SLOPE_REQUESTS);
							if (maxRequests >REQUESTS_MAX ||maxRequests <0)
								maxRequests =REQUESTS_MAX;
						}else{
							maxRequests =2;
						}
					}else{

						maxRequests = pt.getNetwork()==AENetworkClassifier.AT_PUBLIC?1:2;
					}
				}

				// Only loop when 3/5 of the queue is empty, in order to make more consecutive requests,
				// and improve cache efficiency

				if ( pt.getNbRequests() <=(maxRequests *3) /5){
				//if ( pt.getNbRequests() <= maxRequests){
				//	System.out.println("\treqesting from peer; speed:"+pt.getStats().getDataReceiveRate()+" outstanding requests:"+pt.getNbRequests()+" max:"+maxRequests);

					if ( !done_priorities ){

						done_priorities	= true;

						computeBasePriorities();
					}

					int	total_allocated = 0;

					try{
						boolean	peer_managing_requests = pt.requestAllocationStarts( startPriorities );

						while ( pt.isDownloadPossible() && pt.getNbRequests() < maxRequests ){

							// is there anything else to download?

							int	allocated;

							if ( peer_managing_requests || !endGameMode ){

								allocated = findPieceToDownload(pt, maxRequests );

							}else{

								allocated = findPieceInEndGameMode(pt, maxRequests);
							}

							if ( allocated == 0 ){
								
								break;
								
							}else{
								
								total_allocated += allocated;
							}
						}
					}finally{

						pt.requestAllocationComplete();
					}

					if ( total_allocated == 0 ){

						// there are various reasons that we might not allocate any requests to a peer
						// such as them not having any pieces we're interested in. Keep track of the
						// number of consecutive "no requests" outcomes so we can reduce the scheduling
						// frequency of such peers

						int	no_req_count = pt.getConsecutiveNoRequestCount();

						if ( no_req_count < NO_REQUEST_BACKOFF_MAX_LOOPS ){

							pt.setConsecutiveNoRequestCount( no_req_count + 1 );
						}

						// System.out.println( pt.getIp() + ": nb=" + pt.getNbRequests() + ",max=" + maxRequests + ",nrc=" + no_req_count +",loop=" + allocate_request_loop_count);

					}else{

						pt.setConsecutiveNoRequestCount( 0 );
					}
				}
			}
		}
	}



	protected int
	getNextBlockETAFromNow(
			PEPeerTransport	pt )
	{
		long upRate = pt.getStats().getDataReceiveRate();

		if ( upRate < 1 ){

			upRate = 1;
		}

		int	next_block_bytes = ( pt.getNbRequests() + 1 ) * DiskManager.BLOCK_SIZE;

		return((int)(( next_block_bytes * 1000 )/ upRate));
	}

	/**
	 * Count current global min avail pieces in progress
	 * (not counting non-rarest pieces but keep them to compensate high churn, remove completed ones, ignore idle ones)
	 * @return number of pieces that may be started due to the "rarest first" picking rule
	 */
	private int calcRarestAllowed()
	{
		// initial adjustment depending on swarm needs
		int     RarestAllowed = 1;
		if	(globalMinOthers < 20)	{RarestAllowed = 2;}
		if	(globalMinOthers < 8)	{RarestAllowed = 3;}
		if	(globalMinOthers < 4)	{RarestAllowed = 4;}
		//avoid rarest start until we have finished some pieces to share
		if(nbPiecesDone < 4 ) {RarestAllowed = 0;}
		// avoid rarest start during startup (high churn, inaccurate data)
		if(SystemTime.getCurrentTime()-peerControl.getTimeStarted( false ) < 180*1000) {RarestAllowed = 0;}
		// more churn avoidance
		if(rarestStartedPieces.size() > RarestAllowed+2) {RarestAllowed = 0;}

		//decrement number of allowed rarest by already running rarest pieces
		PEPiece rarestStarted;
		for (int i=0;i<rarestStartedPieces.size();i++)
		{
			rarestStarted = (PEPiece)rarestStartedPieces.get(i);
			if (pePieces[rarestStarted.getPieceNumber()] == null) {rarestStartedPieces.remove(i);i--;continue;}
			if (
				(
					rarestStarted.getAvailability() <= globalMinOthers ||
					globalMinOthers > globalMin
				) && (
					SystemTime.getCurrentTime()-rarestStarted.getLastDownloadTime(SystemTime.getCurrentTime()) < 60*1000 ||
					rarestStarted.getNbWritten() == 0
				) && !rarestStarted.isDownloaded()
			) RarestAllowed--;
		}

		return RarestAllowed;
	}

	private void
	syncFilePriorities()
	{
		DiskManagerFileInfo[] files = diskManager.getFiles();

		int max = 0;
		int min = 0;

		for ( DiskManagerFileInfo file: files ){

			int p = file.getPriority();

			if ( p > max ){

				max = p;

			}else if ( p < min ){

				min = p;
			}
		}

		max_file_priority = max;
		min_file_priority = min;
	}
	/** This computes the base priority for all pieces that need requesting if there's
	 * been any availability change or user priority setting changes since the last
	 * call, which will be most of the time since availability changes so dynamicaly
	 * It will change startPriorities[] (unless there was nothing to do)
	 */
	private void
	computeBasePriorities()
	{
		final long now =SystemTime.getCurrentTime();

		if ( now < lastProviderRecalcTime || now - lastProviderRecalcTime > 1000 ){

			lastProviderRecalcTime = now;

			priorityRTAexists = computeProviderPriorities();
		}

		if ( !priorityRTAexists ){
			if (	startPriorities !=null &&
					(	(now >timeLastPriorities &&now <timeLastPriorities +TIME_MIN_PRIORITIES) ||
						(priorityParamChange >=paramPriorityChange &&priorityFileChange >=filePriorityChange &&priorityAvailChange >=availabilityChange))){

				return;		// *somehow* nothing changed, so nothing to do
			}
		}

		// store the latest change indicators before we start making dependent calculations so that a
		// further change while computing stuff doesn't get lost

		timeLastPriorities =now;
		priorityParamChange =paramPriorityChange;
		priorityFileChange =filePriorityChange;
		priorityAvailChange =availabilityChange;

		boolean			foundPieceToDownload =false;
		final int[]		newPriorities   =new int[nbPieces];

		// locals are a tiny bit faster
		
		final boolean firstPiecePriorityL 		= firstPiecePriority;
		final boolean firstPiecePriorityForceL 	= firstPiecePriorityForce;
		final boolean completionPriorityL 		= completionPriority;

		final DMPieceMap	pieceMap = diskManager.getPieceMap();

		final int priorityPieces;
		
		if ( firstPriorityBytes > 0 ){
			
			priorityPieces = (int)(( firstPriorityBytes + pieceSize -1 ) / pieceSize);
			
		}else{
			
			priorityPieces = 1;
		}
		
		CopyOnWriteSet<Integer>	forced = forced_pieces;

		try
		{
			final boolean rarestOverride = calcRarestAllowed() < 1;
			
				// calculate all base (starting) priorities for all pieces needing requesting
			
			final int nbConnects =peerControl.getNbPeers() +peerControl.getNbSeeds();
			
			for (int i =0; i <nbPieces; i++){
				
				final DiskManagerPiece dmPiece =dmPieces[i];

				if ( dmPiece.isDone()){

					if ( forced != null && forced.contains( i )){

						if ( forced.remove(i) && forced.size() == 0 ){

							synchronized( this ){

								if ( forced_pieces != null && forced_pieces.size() == 0 ){

									forced_pieces =  null;
								}
							}
						}
					}
					
					continue;	// nothing to do for pieces not needing requesting
				}

				int startPriority = Integer.MIN_VALUE;

				final DMPieceList pieceList = pieceMap.getPieceList(dmPiece.getPieceNumber());
				
				final int pieceListSize = pieceList.size();
				
				boolean pieceHasFirstLastPriority = false;
				
				for ( int j=0; j<pieceListSize; j++ ){
					
					final DiskManagerFileInfo fileInfo = pieceList.get(j).getFile();
					
					final long downloaded = fileInfo.getDownloaded();
					
					final long length = fileInfo.getLength();
					
					if ( length > 0 && downloaded < length && !fileInfo.isSkipped()){
						
						int priority = 0;
						
						// user option "prioritize first and last piece"
						// TODO: should prioritize ~10% from edges of file

						boolean hasFirstLastPriority = false;
						int		flpOffset = 0;
						
						int nbPieces = fileInfo.getNbPieces();
								
						if ( firstPiecePriorityL && nbPieces > FIRST_PIECE_MIN_NB ){
							
							/* backed out for the moment - reverting to old first/last piece only
                        	int lastFirstPiece = fileInfo.getFirstPieceNumber() + FIRST_PIECE_RANGE_PERCENT * (fileInfo.getLastPieceNumber() - fileInfo.getFirstPieceNumber()) / 100;

                        	if ( (i >=fileInfo.getFirstPieceNumber() && i<= lastFirstPiece ) ) {
                                priority +=PRIORITY_W_FIRSTLAST + 10 * (lastFirstPiece - i) ;
						}

                            if( i ==fileInfo.getLastPieceNumber() ) {
                            	priority +=PRIORITY_W_FIRSTLAST;
                            }
							 */

							
							if ( priorityPieces > 1 ){
								
								int fp = fileInfo.getFirstPieceNumber();
								int lp = fileInfo.getLastPieceNumber();
								
								int pp = Math.min( nbPieces, priorityPieces);
								
								if ( i >= fp && i < fp + pp ){
									
									pieceHasFirstLastPriority = hasFirstLastPriority = true;
									
									flpOffset = fp - i;	// 0 -> negative pp
									
								}else if ( i <= lp && i > lp - pp ){
											
									pieceHasFirstLastPriority = hasFirstLastPriority = true;
									
									flpOffset = i - lp;	// 0 -> negative pp
								}
							}else{

								if  ( i == fileInfo.getFirstPieceNumber() || i == fileInfo.getLastPieceNumber()){
									
									pieceHasFirstLastPriority = hasFirstLastPriority = true;
								}
							}
						}

						// if the file is high-priority
						// startPriority +=(1000 *fileInfo.getPriority()) /255;

						int file_priority = fileInfo.getPriority();

						int max = Math.max( file_priority, max_file_priority );
						int min = Math.min( file_priority, min_file_priority );

						int	range = max - min;

						if ( range > 0 ){

							int	relative_file_priority = file_priority - min;

							priority += PRIORITY_W_FILE_BASE;

							int adjustment;

							if ( hasFirstLastPriority ){

									// one less than the next higher priority file

								adjustment = (( PRIORITY_W_FILE_RANGE * ( relative_file_priority+1 )) / range ) - 1;

								adjustment += flpOffset;
								
							}else{

								adjustment = ( PRIORITY_W_FILE_RANGE*relative_file_priority ) / range;
							}

							priority += adjustment;

						}else{

							if ( hasFirstLastPriority ){

								priority += PRIORITY_W_FIRSTLAST + flpOffset;
							}
						}

						if ( completionPriorityL ){

							final long percent =(1000 *downloaded) /length;

							if ( percent >=900 ){

								priority +=(PRIORITY_W_COMPLETION *downloaded) /diskManager.getTotalLength();
							}
						}
						
						if ( priority > startPriority ){

							startPriority = priority;
						}
					}
				}

				if ( startPriority >=0 ){
					
					if ( pieceHasFirstLastPriority && firstPiecePriorityForceL ){
						
						startPriority += PRIORITY_FORCED;
					}

					dmPiece.setNeeded();
					
					foundPieceToDownload = true;
					
					final int avail = availability[i];
					
						// nbconnects is async calculate so may be wrong - make sure we don't decrease pri by accident
					
					if (avail >0 && nbConnects > avail ){ 
						
							// boost priority for rarity
						
						startPriority += nbConnects -avail;
						
							//	startPriority +=(PRIORITY_W_RARE +peerControl.getNbPeers()) /avail;
							// Boost priority even a little more if it's a globally rarest piece
						
						if ( !rarestOverride &&avail <=globalMinOthers){
							
							startPriority += nbConnects /avail;
						}
					}

					if ( provider_piece_rtas != null ){

						if ( provider_piece_rtas[i] > 0 ){

							startPriority 	= PRIORITY_REALTIME;
						}
					}else if ( provider_piece_priorities != null ){

						startPriority += provider_piece_priorities[i];

					}else if ( forced != null && forced.contains( i )){

						startPriority += PRIORITY_FORCED;
					}
				}else{

					dmPiece.clearNeeded();
				}

				newPriorities[i] = startPriority;
			}
		} catch (Throwable e){
			
			Debug.printStackTrace(e);
		}

		if ( sequentialDownload != 0 && !priorityRTAexists ) {
			
			int seq_pri = PRIORITY_SEQUENTIAL_START;
			
			boolean do_file_priorities = min_file_priority != max_file_priority;
			
			int	file_priority_start = nbPieces*10;
			
			int	loop_start;
			int loop_end;
			int loop_dir;
			
			if ( sequentialDownload > 0 ){
				
				loop_start 	= nbPieces;
				loop_end	= sequentialDownload - 1;
				loop_dir	= -1;
			}else{
				loop_start	= -1;
				loop_end	= -(sequentialDownload+1);
				loop_dir	= +1;
			}
		
			int	loop_pos = loop_start;
			
			do{
				loop_pos += loop_dir;
				
				int priority = newPriorities[loop_pos];
				
				if ( priority == Integer.MIN_VALUE ){
					
					continue;
				}
				
				if ( priority < PRIORITY_FORCED ){
				
					if ( do_file_priorities ){
						
						final DiskManagerPiece dmPiece =dmPieces[loop_pos];

						int	highest = Integer.MIN_VALUE;
						
						final DMPieceList pieceList =pieceMap.getPieceList(dmPiece.getPieceNumber());
						final int pieceListSize =pieceList.size();
						for (int j =0; j <pieceListSize; j++){
							final DiskManagerFileInfo fileInfo =pieceList.get(j).getFile();
							final long downloaded =fileInfo.getDownloaded();
							final long length =fileInfo.getLength();
							if (length >0 &&downloaded <length &&!fileInfo.isSkipped()){
								
								highest = Math.max( highest, fileInfo.getPriority());
							}
						}
						
						if ( highest == Integer.MIN_VALUE ){
							
							newPriorities[loop_pos] = seq_pri;
							
						}else{
						
							int	rel = highest - min_file_priority;

							newPriorities[loop_pos] = file_priority_start + nbPieces*rel + seq_pri;
						}
					}else{
					
						newPriorities[loop_pos] = seq_pri;
					}
				}
				
				seq_pri += 10;
				
			}while( loop_pos != loop_end );
		}
		
		if (foundPieceToDownload !=hasNeededUndonePiece)
		{
			hasNeededUndonePiece =foundPieceToDownload;
			neededUndonePieceChange++;
		}

		startPriorities =newPriorities;
	}


	/*
	private boolean isRarestOverride()
	{
		final int nbSeeds =peerControl.getNbSeeds();
		final int nbPeers =peerControl.getNbPeers();
		final int nbMost =(nbPeers >nbSeeds ?nbPeers :nbSeeds);
		final int nbActive =peerControl.getNbActivePieces();

		// Dont seek rarest under a few circumstances, so that other factors work better
		// never seek rarest when bootstrapping torrent
		boolean rarestOverride =nbPiecesDone <4 ||endGameMode
		||(globalMinOthers >1 &&(nbRarestActive >=nbMost ||nbActive >=nbMost));
		if (!rarestOverride &&nbRarestActive >1 &&globalMinOthers >1)
		{
			// if already getting some rarest, dont get more if swarm is healthy or too many pieces running
			rarestOverride =globalMinOthers >globalMin
			||(globalMinOthers >=(2 *nbSeeds) &&(2 *globalMinOthers) >=nbPeers);
			// Interest in Rarest pieces (compared to user priority settings) could be influenced by several factors;
			// less demand closer to 0% and 100% of torrent completion/farther from 50% of torrent completion
			// less demand closer to 0% and 100% of peers interestd in us/farther from 50% of peers interested in us
			// less demand the more pieces are in progress (compared to swarm size)
			// less demand the farther ahead from absolute global minimum we're at already
			// less demand the healthier a swarm is (rarity compared to # seeds and # peers)
		}
		return rarestOverride;
	}
	*/
	
	private final SpeedTokenDispenser dispenser = PeerControlSchedulerFactory.getSingleton(0).getSpeedTokenDispenser();

	/**
	 * @param pt the PEPeerTransport we're working on
	 * @return int # of blocks that were requested (0 if no requests were made)
	 */
	protected final int findPieceToDownload(PEPeerTransport pt, int nbWanted)
	{
		final int pieceNumber = getRequestCandidate(pt);
		if (pieceNumber <0)
		{
			// probaly should have found something since chose to try; probably not interested anymore
			// (or maybe Needed but not Done pieces are otherwise not requestable)
			// pt.checkInterested();
			return 0;
		}

		int peerSpeed =(int) pt.getStats().getDataReceiveRate() /1000;
		if(peerSpeed < 0)
			peerSpeed = 0;
		if(pt.isSnubbed())
			peerSpeed = 0;

		final PEPiece pePiece;
		if (pePieces[pieceNumber] != null){
			pePiece = pePieces[pieceNumber];
		}else{
				//create piece manually
			int[]	peer_priority_offsets = pt.getPriorityOffsets();

			int	this_offset = peer_priority_offsets==null?0:peer_priority_offsets[pieceNumber];

			//create piece manually

			pePiece =new PEPieceImpl(this, dmPieces[pieceNumber], peerSpeed >>1);

			// Assign the created piece to the pieces array.
			peerControl.addPiece(pePiece, pieceNumber, pt);
			if (startPriorities !=null){
				pePiece.setResumePriority(startPriorities[pieceNumber] + this_offset);
			}else{
				pePiece.setResumePriority( this_offset );
			}
			if (availability[pieceNumber] <=globalMinOthers)
				nbRarestActive++;
		}

		int[]	request_hint = null;

		if ( enable_request_hints ){

			request_hint = pt.getRequestHint();

			if ( request_hint != null ){

				if ( request_hint[0] != pieceNumber ){

					request_hint = null;
				}
			}

			if ( request_hint == null ){

				request_hint = global_request_hint;

				if ( request_hint != null && request_hint[0] != pieceNumber ){

					request_hint = null;
				}
			}
		}


		if (!pt.isLANLocal() || includeLanPeersInReqLimiting){
			nbWanted = dispenser.dispense(nbWanted, DiskManager.BLOCK_SIZE);
		}

		final int[] blocksFound = pePiece.getAndMarkBlocks(pt, nbWanted, request_hint, reverse_block_order  );

		final int blockNumber 	= blocksFound[0];
		final int nbBlocks 		= blocksFound[1];

		if((!pt.isLANLocal() || includeLanPeersInReqLimiting) && nbBlocks != nbWanted){
			dispenser.returnUnusedChunks(nbWanted-nbBlocks, DiskManager.BLOCK_SIZE);
		}

		if (nbBlocks <=0)
			return 0;

		int requested =0;

			// really try to send the request to the peer

		if ( reverse_block_order ){

			for (int i = nbBlocks-1; i >= 0; i--){

				final int thisBlock = blockNumber + i;

				DiskManagerReadRequest request = pt.request(pieceNumber, thisBlock *DiskManager.BLOCK_SIZE, pePiece.getBlockSize(thisBlock),true);
				
				if ( request != null ){
					peerControl.requestAdded(pePiece,pt,request);
					requested++;
					pt.setLastPiece(pieceNumber);

					pePiece.setLastRequestedPeerSpeed( peerSpeed );
					// have requested a block
				} else{
					pePiece.clearRequested(thisBlock);
				}
			}
		}else{
			for (int i =0; i <nbBlocks; i++){

				final int thisBlock =blockNumber +i;

				DiskManagerReadRequest request = pt.request(pieceNumber, thisBlock *DiskManager.BLOCK_SIZE, pePiece.getBlockSize(thisBlock),true);
				
				if ( request != null ){
					peerControl.requestAdded(pePiece,pt,request);
					requested++;
					pt.setLastPiece(pieceNumber);

					pePiece.setLastRequestedPeerSpeed( peerSpeed );
					// have requested a block
				} else{
					pePiece.clearRequested(thisBlock);
				}
			}
		}

		if (requested > 0
				&& pePiece.getAvailability() <= globalMinOthers
				&& calcRarestAllowed() > 0
				&& !rarestStartedPieces.contains(pePiece)){

			rarestStartedPieces.add(pePiece);
		}

		return requested;
	}




	protected final boolean
	findRTAPieceToDownload(
		PEPeerTransport 	pt,
		boolean				best_uploader,
		long				best_uploader_next_block_eta )
	{
		if ( pt == null || pt.getPeerState() != PEPeer.TRANSFERING ){

			return( false );
		}

		final BitFlags  peerHavePieces =pt.getAvailable();

		if ( peerHavePieces ==null || peerHavePieces.nbSet <=0 ){

			return( false );
		}

		String rta_log_str = LOG_RTA?pt.getIp():null;

		try{
			final int   peerSpeed =(int) pt.getStats().getDataReceiveRate() /1024;  // how many KB/s has the peer has been sending

			final int	startI 	= peerHavePieces.start;
			final int	endI 	= peerHavePieces.end;

			int	piece_min_rta_index	= -1;
			int piece_min_rta_block	= 0;
			long piece_min_rta_time	= Long.MAX_VALUE;

			long	now = SystemTime.getCurrentTime();

			long	my_next_block_eta = now + getNextBlockETAFromNow( pt );


			for ( int i=startI; i <=endI; i++){

				long piece_rta = provider_piece_rtas[i];

				if ( peerHavePieces.flags[i] && startPriorities[i] == PRIORITY_REALTIME && piece_rta > 0 ){

					final DiskManagerPiece dmPiece =dmPieces[i];

					if ( !dmPiece.isDownloadable()){

						continue;
					}

					final PEPiece pePiece = pePieces[i];

					if ( pePiece != null && pePiece.isDownloaded()){

						continue;
					}

					Object realtime_data = null;

					boolean	try_allocate_even_though_late =
						my_next_block_eta > piece_rta && best_uploader_next_block_eta > piece_rta;

					if ( piece_rta >= piece_min_rta_time  ){

						// piece is less urgent than an already found one

					}else if ( my_next_block_eta > piece_rta && !( best_uploader || best_uploader_next_block_eta > piece_rta )){

						// only allocate if we have a chance of getting this block in time or we're
						// the best uploader we've got/even the best uploader can't get it

						// the second part is important for when we get to the point whereby no peers
						// can get a block in time. Here we need to allocate someone to get it as
						// otherwise we'll concentrate on getting lower priority pieces that we can
						// get in time and leave the stuck ones for just the best uploader to get

					}else if ( pePiece == null || ( realtime_data = pePiece.getRealTimeData()) == null ){


						if ( LOG_RTA ) rta_log_str += "{alloc_new=" + i + ",time=" + (piece_rta-now)+"}";

						// no real-time block allocated yet

						piece_min_rta_time 	= piece_rta;
						piece_min_rta_index = i;
						piece_min_rta_block	= 0;

					}else{

						RealTimeData	rtd = (RealTimeData)realtime_data;

						// check the blocks to see if any are now lagging behind their ETA given current peer speed

						List[]	peer_requests = rtd.getRequests();

						for (int j=0;j<peer_requests.length;j++){

							if ( pePiece.isDownloaded( j ) || pePiece.isWritten( j )){

								// this block is already downloaded, ignore

								continue;
							}

							List	block_peer_requests = peer_requests[j];


							long best_eta = Long.MAX_VALUE;

							boolean	pt_already_present  = false;

							// tidy up existing request data

							Iterator	it = block_peer_requests.iterator();

							while( it.hasNext()){

								RealTimePeerRequest	pr = (RealTimePeerRequest)it.next();

								PEPeerTransport	this_pt = pr.getPeer();

								if ( this_pt.getPeerState() != PEPeer.TRANSFERING ){

										// peer's dead

									if ( LOG_RTA ) rta_log_str += "{peer_dead=" + this_pt.getIp()+"}";

									it.remove();

									continue;

								}

								DiskManagerReadRequest	this_request = pr.getRequest();

								int	request_index = this_pt.getRequestIndex( this_request );

								if ( request_index == -1 ){

										// request's gone

									if ( LOG_RTA ) rta_log_str += "{request_lost=" + this_request.getPieceNumber()+"}";

									it.remove();

									continue;
								}

								if ( this_pt == pt ){

									pt_already_present	= true;

									break;
								}

								long this_up_bps = this_pt.getStats().getDataReceiveRate();

								if ( this_up_bps < 1 ){

									this_up_bps = 1;
								}

								int	next_block_bytes = ( request_index + 1 ) * DiskManager.BLOCK_SIZE;

								long	this_peer_eta = now + (( next_block_bytes * 1000 ) / this_up_bps );

								best_eta = Math.min( best_eta, this_peer_eta );
							}

								// if we've not already requested this block

							if ( !pt_already_present ){

									// and there are no outstanding requests or outstanding requests are lagging

								if ( block_peer_requests.size() == 0 ){

									if ( LOG_RTA ) rta_log_str += "{alloc as no req=" + i + ",block=" + j+ ",time=" + ( piece_rta-now) + "}";

									piece_min_rta_time 	= piece_rta;
									piece_min_rta_index = i;
									piece_min_rta_block = j;

									break;	// earlier blocks always have priority

								}else if ( best_eta > piece_rta && ( best_uploader || !try_allocate_even_though_late )){

										// don't re-allocate when we're already running late as we'll end up
										// with a lot of discard

									if ( LOG_RTA ) rta_log_str += "{lagging=" + i + ",block=" + j+ ",time=" + ( best_eta - piece_rta) + "}";

										// if we can do better than existing best effort allocate

									if ( my_next_block_eta < best_eta ){


										if ( LOG_RTA ) rta_log_str += "{taking over, time=" + ( best_eta - my_next_block_eta) + "}";

										piece_min_rta_time 	= piece_rta;
										piece_min_rta_index = i;
										piece_min_rta_block = j;

										break;	// earlier blocks always have priority
									}
								}
							}
						}
					}
				}
			}

			if ( piece_min_rta_index != -1 ){

				if ( LOG_RTA ) rta_log_str += ",{select_piece=" + piece_min_rta_index + ",block=" + piece_min_rta_block + ",time=" + (piece_min_rta_time-now) + "}";

				if ( dispenser.dispense(1, DiskManager.BLOCK_SIZE) == 1 || (pt.isLANLocal() && !includeLanPeersInReqLimiting)){

					PEPiece pePiece = pePieces[piece_min_rta_index];

					if ( pePiece == null ){

						// create piece manually

						pePiece = new PEPieceImpl( this, dmPieces[piece_min_rta_index], peerSpeed >>1 );

						// Assign the created piece to the pieces array.

						peerControl.addPiece(pePiece, piece_min_rta_index, pt);

						pePiece.setResumePriority( PRIORITY_REALTIME );

						if ( availability[piece_min_rta_index] <=globalMinOthers ){

							nbRarestActive++;
						}
					}

					RealTimeData	rtd = (RealTimeData)pePiece.getRealTimeData();

					if ( rtd == null ){

						rtd = new RealTimeData( pePiece );

						pePiece.setRealTimeData( rtd );
					}

					pePiece.getAndMarkBlock( pt, piece_min_rta_block );

					DiskManagerReadRequest	request = pt.request(piece_min_rta_index, piece_min_rta_block *DiskManager.BLOCK_SIZE, pePiece.getBlockSize(piece_min_rta_block),true);

					if ( request != null ){

						peerControl.requestAdded(pePiece,pt,request);
						
						List	real_time_requests = rtd.getRequests()[piece_min_rta_block];

						real_time_requests.add( new RealTimePeerRequest( pt, request ));

						pt.setLastPiece(piece_min_rta_index);

						pePiece.setLastRequestedPeerSpeed( peerSpeed );

						return( true );

					}else{

						if ( LOG_RTA ) rta_log_str += "{request failed}";

						if(!pt.isLANLocal() || includeLanPeersInReqLimiting)
							dispenser.returnUnusedChunks(1, DiskManager.BLOCK_SIZE);

						return( false );
					}

				}else{

					if ( LOG_RTA ) rta_log_str += "{dispenser denied}";

					return( false );
				}

			}else{

				if ( LOG_RTA ) rta_log_str += "{no piece found}";

				return( false );
			}
		}finally{

			if ( LOG_RTA ){

				System.out.println( rta_log_str );
			}
		}
	}




	/**
	 * This method is the downloading core. It decides, for a given peer,
	 * which block should be requested. Here is the overall algorithm :
	 * 0. If there a FORCED_PIECE or reserved piece, that will be started/resumed if possible
	 * 1. Scan all the active pieces and find the rarest piece (and highest priority among equally rarest)
	 *	that can possibly be continued by this peer, if any
	 * 2. While scanning the active pieces, develop a list of equally highest priority pieces
	 *	(and equally rarest among those) as candidates for starting a new piece
	 * 3. If it can't find any piece, this means all pieces are
	 *	already downloaded/full requested
	 * 4. Returns int[] pieceNumber, blockNumber if a request to be made is found,
	 *	or null if none could be found
	 * @param pc PEPeerTransport to work with
	 *
	 * @return int with pieceNumberto be requested or -1 if no request could be found
	 */
	private int getRequestCandidate(final PEPeerTransport pt )
	{
		if (pt ==null ||pt.getPeerState() !=PEPeer.TRANSFERING)
			return -1;
		final BitFlags	peerHavePieces =pt.getAvailable();
		if (peerHavePieces ==null ||peerHavePieces.nbSet <=0)
			return -1;

		// piece number and its block number that we'll try to DL

		int[] reservedPieceNumbers = pt.getReservedPieceNumbers();

		// If there's a piece seserved to this peer resume it and only it (if possible)

		if ( reservedPieceNumbers != null ){

			for ( int reservedPieceNumber: reservedPieceNumbers ){

				PEPiece pePiece = pePieces[reservedPieceNumber];

				if ( pePiece != null ){

					String peerReserved = pePiece.getReservedBy();

					if ( peerReserved != null && peerReserved.equals( pt.getIp())){

						if ( peerHavePieces.flags[reservedPieceNumber] &&pePiece.isRequestable()){

							return reservedPieceNumber;
						}else{

							pePiece.setReservedBy( null );
						}
					}
				}

					// reserved piece is no longer valid, dump it

				pt.removeReservedPieceNumber( reservedPieceNumber );
			}

			// note, pieces reserved to peers that get disconnected are released in pepeercontrol
		}

		int	reservedPieceNumber	= -1;

		final int			peerSpeed =(int) pt.getStats().getDataReceiveRate() /1024;	// how many KB/s has the peer has been sending
		final int			lastPiece =pt.getLastPiece();
		//final boolean   rarestOverride = calcRarestAllowed() > 0;
		final int		nbSnubbed =peerControl.getNbPeersSnubbed();

		long		resumeMinAvail =Long.MAX_VALUE;
		int         resumeMaxPriority =Integer.MIN_VALUE;
		boolean		resumeIsRarest = false; // can the peer continuea piece with lowest avail of all pieces we want

		int			secondChoiceResume = -1;

		BitFlags	startCandidates =null;
		int         startMaxPriority =Integer.MIN_VALUE;
		int         startMinAvail =Integer.MAX_VALUE;
		boolean     startIsRarest =false;
        boolean		forceStart=false;

		int         priority;   // aggregate priority of piece under inspection (start priority or resume priority for pieces to be resumed)
		int         		avail =0;   // the swarm-wide availability level of the piece under inspection
		long 				pieceAge;	// how long since the PEPiece first started downloading (requesting, actually)

		final boolean rarestAllowed = calcRarestAllowed() > 0;
		final int	startI =peerHavePieces.start;
		final int	endI =peerHavePieces.end;
		int 		i;

		final int[]		peerPriorities	= pt.getPriorityOffsets();

		final long	now = SystemTime.getCurrentTime();

        int[] 	request_hint = pt.getRequestHint();
        int		request_hint_piece_number;

        if ( request_hint != null ){

        	request_hint_piece_number = request_hint[0];

        	if ( dmPieces[request_hint_piece_number].isDone()){

        		pt.clearRequestHint();

        		request_hint_piece_number	= -1;
        	}
        }else{

        	request_hint_piece_number = -1;
        }

        if ( request_hint_piece_number == -1 ){

        	int[] g_hint = global_request_hint;

        	if ( g_hint != null ){

        		request_hint_piece_number = g_hint[0];

        		if ( dmPieces[request_hint_piece_number].isDone()){

        			g_hint = null;

            		request_hint_piece_number	= -1;
            	}
        	}
        }

        CopyOnWriteSet<Integer>	forced = forced_pieces;

			// Try to continue a piece already loaded, according to priority

        for (i =startI; i <=endI; i++){

        		// is the piece available from this peer?

        	if ( peerHavePieces.flags[i]){

        		priority = startPriorities[i];

        		final DiskManagerPiece dmPiece = dmPieces[i];

        		if ( priority >=0 && dmPiece.isDownloadable()){

        			if ( peerPriorities != null ){

           				int peer_priority = peerPriorities[i];

           				if ( peer_priority < 0 ){

           					continue;
           				}

           				priority += peer_priority;
        			}

        			if ( enable_request_hints && i == request_hint_piece_number ){

        				priority += PRIORITY_REQUEST_HINT;

        				PEPiece pePiece = pePieces[i];

        				if ( pePiece == null ){
        					forceStart	= true;
        				}else{
       						pePiece.setReservedBy( pt.getIp());
       						pt.addReservedPieceNumber( i );
        				}
        			}

        			final PEPiece pePiece = pePieces[i];

        			// if we are considering realtime pieces then don't bother with non-realtime ones

        			if ( pePiece == null || pePiece.isRequestable())
        			{
        				// if this priority exceeds the priority-override threshold then  we override rarity
        				boolean	pieceRarestOverride = priority>=PRIORITY_OVERRIDES_RAREST?true:rarestAllowed;

        				// piece is: Needed, not fully: Requested, Downloaded, Written, hash-Checking or Done

        				avail = availability[i];
        				if (avail ==0)
        				{   // maybe we didn't know we could get it before
        					availability[i] = 1;    // but the peer says s/he has it
        					avail =1;
        				}else if ( forced != null && forced.contains( i )){
        					avail = globalMinOthers;	// temp override for avail for force
        				}else if ( sequentialDownload != 0 && globalMinOthers > 1 ) {
        					avail = globalMinOthers;	// temp override for seq download
        				}

        				// is the piece active
        				if (pePiece !=null)
        				{
        					if ( priority != startPriorities[i])
        						pePiece.setResumePriority( priority );	// maintained for display purposes only

        					boolean startedRarest =  rarestStartedPieces.contains(pePiece);
        					boolean rarestPrio    = avail <=globalMinOthers && ( startedRarest || rarestAllowed);

        					// How many requests can still be made on this piece?
        					final int freeReqs =pePiece.getNbUnrequested();
        					if (freeReqs <=0)
        					{
        						pePiece.setRequested();
        						continue;
        					}


        					// Don't touch pieces reserved for others
        					final String peerReserved =pePiece.getReservedBy();
        					if (peerReserved !=null)
        					{
        						if (!peerReserved.equals(pt.getIp()))
        							continue;   //reserved to somebody else
        						// the peer forgot this is reserved to him; re-associate it
        						pt.addReservedPieceNumber(i);
        						return i;
        					}

        					int pieceSpeed =pePiece.getSpeed();

        					// ### Piece/Peer speed checks
        					boolean mayResume = true;

        					if(pt.isSnubbed())
        					{
        						// snubbed peers shouldn't stall fast pieces under ANY condition
        						// may lead to trouble when the snubbed peer is the only seed, needs further testing
        						mayResume &= pieceSpeed < 1;
        						mayResume &= freeReqs > 2 || avail <= nbSnubbed;
        					} else
        					{
        						// slower peers are allowed as long as there is enough free room
        						mayResume &= freeReqs*peerSpeed >= pieceSpeed/2; //|| rarestPrio;
        						// prevent non-subbed peers from resuming on snubbed-peer-pieces but still allow them to resume stalled pieces
        						mayResume &= peerSpeed < 2 || pieceSpeed > 0 || pePiece.getNbRequests() == 0;
        						mayResume |= i == pt.getLastPiece();
        					}

        					// find a fallback piece in case the peer could theoretically contribute
        					// to an existing piece but is prevented by the snubbing rules etc.
        					// this will prevent unecessary piece starting
        					if(secondChoiceResume == -1 || avail > availability[secondChoiceResume])
        						secondChoiceResume = i;

        					if(!mayResume)
        						continue;
        					if (avail > resumeMinAvail)
        						continue;

        					priority +=pieceSpeed;
        					priority +=(i ==lastPiece) ?PRIORITY_W_SAME_PIECE :0;
        					// Adjust priority for purpose of continuing pieces
        					// how long since last written to (if written to)
        					priority +=pePiece.getTimeSinceLastActivity() /PRIORITY_DW_STALE;
        					// how long since piece was started
        					pieceAge =now -pePiece.getCreationTime();
        					if (pieceAge >0)
        						priority +=PRIORITY_W_AGE *pieceAge /(PRIORITY_DW_AGE *dmPiece.getNbBlocks());
        					// how much is already written to disk
        					priority +=(PRIORITY_W_PIECE_DONE *dmPiece.getNbWritten()) /dmPiece.getNbBlocks();

        					pePiece.setResumePriority(priority);  // this is only for display

        					if (avail < resumeMinAvail || (avail == resumeMinAvail && priority > resumeMaxPriority))
        					{	// this piece seems like best choice for resuming
        						// Verify it's still possible to get a block to request from this piece
        						if (pePiece.hasUnrequestedBlock())
        						{	// change the different variables to reflect interest in this block
        							reservedPieceNumber	= i;
        							resumeMinAvail =avail;
        							resumeMaxPriority	= priority;
        							resumeMinAvail	= avail;
        							resumeIsRarest	= rarestPrio;
        						}
        					}
        				} else if (avail <=globalMinOthers && rarestAllowed)
        				{   // rarest pieces only from now on
        					if (!startIsRarest)
        					{   // 1st rarest piece
        						if (startCandidates ==null)
        							startCandidates =new BitFlags(nbPieces);
        						startMaxPriority =priority;
        						startMinAvail =avail;
        						startIsRarest =avail <=globalMinOthers;
        						startCandidates.setOnly(i); // clear the non-rarest bits in favor of only rarest
        					} else if (priority >startMaxPriority)
        					{   // continuing rarest, higher priority level
        						if (startCandidates ==null)
        							startCandidates =new BitFlags(nbPieces);
        						startMaxPriority =priority;
        						startCandidates.setOnly(i);
        					} else if (priority ==startMaxPriority)
        					{   // continuing rares, same priority level
        						startCandidates.setEnd(i);
        					}
        				} else if (!startIsRarest ||!rarestAllowed)
        				{   // not doing rarest pieces
        					if (priority >startMaxPriority)
        					{   // new priority level
        						if (startCandidates ==null)
        							startCandidates =new BitFlags(nbPieces);
        						startMaxPriority =priority;
        						startMinAvail =avail;
        						startIsRarest =avail <=globalMinOthers;
        						startCandidates.setOnly(i);
        					} else if (priority ==startMaxPriority)
        					{   // continuing same priority level
        						if (startCandidates ==null)
        							startCandidates =new BitFlags(nbPieces);

        						if (avail <startMinAvail)
        						{   // same priority, new availability level
        							startMinAvail =avail;
        							startIsRarest =avail <=globalMinOthers;
        							startCandidates.setOnly(i);
        						} else if (avail ==startMinAvail)
        						{   // same priority level, same availability level
        							startCandidates.setEnd(i);
        						}
        					}
        				}
        			}
        		}
        	}
        }

		/*
		// don't start pieces when snubbed, unless it's the only peer with that piece
		// returns -1 if no piece to resume is found
		if(pt.isSnubbed() && startMinAvail > 1)
			return pieceNumber;
		 */

		if ( !forceStart || startCandidates ==null ||startCandidates.nbSet <=0 ){

			// can & should or must resume a piece?
			if (reservedPieceNumber >=0 &&(resumeIsRarest ||!startIsRarest || !rarestAllowed  || startCandidates ==null ||startCandidates.nbSet <=0))
				return reservedPieceNumber;

			if(secondChoiceResume != -1 && (startCandidates ==null ||startCandidates.nbSet <=0))
			{
				//System.out.println("second choice resume:"+secondChoiceResume);
				return secondChoiceResume;
			}


//			this would allow more non-rarest pieces to be resumed so they get completed so they can be re-shared,
//			which can make us intersting to more peers, and generally improve the speed of the swarm,
//			however, it can sometimes be hard to get the rarest pieces, such as when a holder unchokes very infrequently
//			20060312[MjrTom] this can lead to TOO many active pieces, so do the extra check with arbitrary # of active pieces
			final boolean resumeIsBetter;
			if (reservedPieceNumber >=0 &&globalMinOthers >0 &&peerControl.getNbActivePieces() >32)	// check at arbitrary figure of 32 pieces
			{
				resumeIsBetter =(resumeMaxPriority /resumeMinAvail) >(startMaxPriority /globalMinOthers);

				if (Constants.isCVSVersion() &&Logger.isEnabled())
					Logger.log(new LogEvent(new Object[] {pt, peerControl}, LOGID,
						"Start/resume choice; piece #:" +reservedPieceNumber +" resumeIsBetter:" +resumeIsBetter
						+" globalMinOthers=" +globalMinOthers
						+" startMaxPriority=" +startMaxPriority +" startMinAvail=" +startMinAvail
						+" resumeMaxPriority=" +resumeMaxPriority +" resumeMinAvail=" +resumeMinAvail
						+" : " +pt));

				if (resumeIsBetter)
					return reservedPieceNumber;

			}
		}

		// start a new piece; select piece from start candidates bitfield
		return getPieceToStart(startCandidates);
	}


	/**
	 * @param startCandidates BitFlags of potential candidates to choose from
	 * @return int the piece number that was chosen to be started. Note it's possible for
	 * the chosen piece to have been started already (by another thread).
	 * This method considers that potential to not be relevant.
	 */
	protected final int getPieceToStart(final BitFlags startCandidates)
	{
		if (startCandidates ==null ||startCandidates.nbSet <=0)
			return -1;
		if (startCandidates.nbSet ==1 )
			return startCandidates.start;
		if ( sequentialDownload != 0 ){
			if ( sequentialDownload > 0 ){
				return startCandidates.start;
			}else {
				return startCandidates.end;
			}
		}
		final int direction =RandomUtils.generateRandomPlusMinus1();
		final int startI;
		if (direction ==1)
			startI =startCandidates.start;
		else
			startI =startCandidates.end;

		// randomly select a bit flag to be the one
		final int targetNb =RandomUtils.generateRandomIntUpto(startCandidates.nbSet);

		// figure out the piece number of that selected bitflag
		int foundNb =-1;
		for (int i =startI; i <=startCandidates.end &&i >=startCandidates.start; i +=direction)
		{
			// is piece flagged
			if (startCandidates.flags[i])
			{
				foundNb++;
				if (foundNb >=targetNb)
					return i;
			}
		}
		return -1;
	}

	@Override
	public final boolean hasDownloadablePiece()
	{
		return hasNeededUndonePiece;
	}

	@Override
	public final long getNeededUndonePieceChange()
	{
		return neededUndonePieceChange;
	}


	private void checkEndGameMode()
	{
		if (peerControl.getNbSeeds() +peerControl.getNbPeers() <3){
			return;
		}

		final long mono_now =SystemTime.getMonotonousTime();

		if ( endGameMode ||endGameModeAbandoned ){

			if ( !endGameModeAbandoned ){

				if ( mono_now - timeEndGameModeEntered  > END_GAME_MODE_TIMEOUT){

					abandonEndGameMode();
				}
			}

			return;
		}

			// test a block-based version as logic should be piece-size dependent
		
		if ( EGM_IS_BLOCK_BASED ){
			
				// when doing RTA we don't want EGM to kick in too early as it interfers with progressive
				// playback by increasing discard. So we use a smaller trigger value to limit the impact
		
			boolean	use_rta_egm = rta_providers.size() > 0;
		
			long	trigger_blocks	= use_rta_egm?RTA_END_GAME_MODE_SIZE_TRIGGER_BLOCKS:END_GAME_MODE_SIZE_TRIGGER_BLOCKS;
	
		
			int active_blocks 	= 0;
			int reserved_blocks	= 0;
	
			for (int i =0; i <nbPieces; i++){
	
				final DiskManagerPiece dmPiece =dmPieces[i];
	
					// If the piece isn't even Needed, or doesn't need more downloading, simply continue
	
				if (!dmPiece.isDownloadable()){
	
					continue;
				}
	
				final PEPiece pePiece = pePieces[i];
	
				if ( pePiece != null ){
	
					if ( pePiece.isDownloaded()){
	
						continue;
					}
	
					if ( dmPiece.isNeeded()){
	
							// If the piece is being downloaded (fully requested), count it and continue
	
						if ( pePiece.isRequested() ){
	
							boolean written[] = dmPiece.getWritten();
	
							if ( written == null ){
	
								if (!dmPiece.isDone()){
	
									active_blocks += pePiece.getNbBlocks();
								}
							}else{
	
								for (int j =0; j <written.length; j++ ){
	
									if (!written[j]){
										
										active_blocks++;
									}
								}
							}
							
							if ( active_blocks > trigger_blocks ){
								
								return;
							}
	
							continue;
						}
	
							// If we have a piece reserved to a slow peer then this can prevent end-game
							// mode from being entered and result poopy end-of-dl speeds
	
						if ( pePiece.getReservedBy() != null ){
	
							boolean written[] = dmPiece.getWritten();
	
							if ( written ==null){
	
								if (!dmPiece.isDone()){
	
									reserved_blocks += pePiece.getNbBlocks();
								}
							}else{
	
								for (int j =0; j <written.length; j++ ){
	
									if (!written[j]){
										
										reserved_blocks++;
									}
								}
							}
	
							if ( reserved_blocks  > END_GAME_MODE_RESERVED_TRIGGER_BLOCKS ){
	
								return;
							}
	
							continue;
						}
					}
				}
	
					// Else, some piece is Needed, not downloaded/fully requested; this isn't end game mode
	
				return;
			}
	
			synchronized( endGameModeChunkLock ){
	
				endGameModeChunks = new LinkedList<>();
	
				endGameModeChunkMap	= new HashMap<>();
				
				timeEndGameModeEntered = mono_now;
	
				endGameMode = true;
	
				computeEndGameModeChunks();
	
				if (Logger.isEnabled())
					Logger.log(new LogEvent(diskManager.getTorrent(), LOGID, "Entering end-game mode: "
							+peerControl.getDisplayName()));
				// System.out.println("End-Game Mode activated");
			
			}
		}else{
			
			int active_pieces 	= 0;
			int reserved_pieces	= 0;
	
			for (int i =0; i <nbPieces; i++){
	
				final DiskManagerPiece dmPiece =dmPieces[i];
	
					// If the piece isn't even Needed, or doesn't need more downloading, simply continue
	
				if (!dmPiece.isDownloadable()){
	
					continue;
				}
	
				final PEPiece pePiece = pePieces[i];
	
				if ( pePiece != null ){
	
					if ( pePiece.isDownloaded()){
	
						continue;
					}
	
					if ( dmPiece.isNeeded()){
	
							// If the piece is being downloaded (fully requested), count it and continue
	
						if ( pePiece.isRequested() ){
	
							active_pieces++ ;
	
							continue;
						}
	
							// If we have a piece reserved to a slow peer then this can prevent end-game
							// mode from being entered and result poopy end-of-dl speeds
	
						if ( pePiece.getReservedBy() != null ){
	
							reserved_pieces++;
	
							if ( reserved_pieces * pieceSize > END_GAME_MODE_RESERVED_TRIGGER ){
	
								return;
							}
	
							continue;
						}
					}
				}
	
					// Else, some piece is Needed, not downloaded/fully requested; this isn't end game mode
	
				return;
			}
	
				// when doing RTA we don't want EGM to kick in too early as it interfers with progressive
				// playback by increasing discard. So we use a smaller trigger value to limit the impact
	
			boolean	use_rta_egm = rta_providers.size() > 0;
	
			long	remaining = active_pieces * (long)pieceSize;
	
			long	trigger	= use_rta_egm?RTA_END_GAME_MODE_SIZE_TRIGGER:END_GAME_MODE_SIZE_TRIGGER;
	
				// only flip into end-game mode if < trigger size left
	
			if ( remaining <= trigger ){
	
				synchronized( endGameModeChunkLock ){
	
					endGameModeChunks = new LinkedList<>();
	
					endGameModeChunkMap = new HashMap<>();
					
					timeEndGameModeEntered = mono_now;
	
					endGameMode = true;
	
					computeEndGameModeChunks();
	
					if (Logger.isEnabled())
						Logger.log(new LogEvent(diskManager.getTorrent(), LOGID, "Entering end-game mode: "
								+peerControl.getDisplayName()));
				// System.out.println("End-Game Mode activated");
				
				}
			}
		}
	}

	private void computeEndGameModeChunks()
	{
		synchronized( endGameModeChunkLock ){

			for (int i =0; i <nbPieces; i++ ){

				final DiskManagerPiece dmPiece =dmPieces[i];

					// Pieces not Needed or not needing more downloading are of no interest

				if (!dmPiece.isInteresting()){

					continue;
				}

				PEPiece pePiece = pePieces[i];

				if (pePiece ==null){

					pePiece = new PEPieceImpl(this,dmPiece,0);

					peerControl.addPiece(pePiece,i, null);
				}

				final boolean written[] =dmPiece.getWritten();

				if (written ==null){

					if (!dmPiece.isDone()){

						for (int j =0; j <pePiece.getNbBlocks(); j++ ){

							EndGameModeChunk chunk = new EndGameModeChunk(pePiece, j);
							
							endGameModeChunks.add( chunk );
							
							endGameModeChunkMap.put( new Long( ((long)i) << 32 | j ), chunk );
						}
					}
				}else{

					for (int j =0; j <written.length; j++ ){

						if (!written[j]){
							
							EndGameModeChunk chunk = new EndGameModeChunk(pePiece, j);
							
							endGameModeChunks.add( chunk );
						
							endGameModeChunkMap.put( new Long( ((long)i) << 32 | j ), chunk );
						}
					}
				}
			}
			
			if ( reverse_block_order ){
				
				Collections.reverse( endGameModeChunks );
			}
		}
	}

	@Override
	public final boolean
	isInEndGameMode()
	{
		return endGameMode;
	}

    @Override
    public boolean
    hasEndGameModeBeenAbandoned()
    {
    	return( endGameModeAbandoned );
    }

	/** adds every block from the piece to the list of chuncks to be selected for egm requesting
	 *
	 */
	@Override
	public final void
	addEndGameChunks(
		final PEPiece pePiece )
	{
		if (!endGameMode){
			return;
		}

		synchronized( endGameModeChunkLock ){

			final int nbChunks =pePiece.getNbBlocks();

			int piece_number = pePiece.getPieceNumber();
			
			for (int i=0; i <nbChunks; i++ ){

				EndGameModeChunk chunk = new EndGameModeChunk(pePiece, i);
				
				endGameModeChunks.add( chunk );
				
				endGameModeChunkMap.put( new Long(((long)piece_number) << 32 | i ), chunk );
			}
		}
	}

	protected final int
	findPieceInEndGameMode(
		final PEPeerTransport 	pt,
		final int 				wants)
	{
		//loopage??? it is calling pt.request, I can only assume that either max_req is huge or pr.request
		//is returning true but not incrementing num requests?

		if ( pt == null || wants <= 0 || pt.getPeerState() !=PEPeer.TRANSFERING ){

			return 0;
		}

		synchronized( endGameModeChunkLock ){

			Iterator<EndGameModeChunk>	it = endGameModeChunks.iterator();
						
			while( it.hasNext()){

				EndGameModeChunk chunk = it.next();

				int pieceNumber = chunk.getPieceNumber();

				if ( dmPieces[pieceNumber].isWritten(chunk.getBlockNumber())){

					it.remove();

					endGameModeChunkMap.remove( new Long(((long)pieceNumber) << 32 | chunk.getBlockNumber()));
				
				}else{

					PEPiece	pePiece = pePieces[pieceNumber];

					if ( pt.isPieceAvailable(pieceNumber) && pePiece != null ){

						if ( !pt.isSnubbed() || availability[pieceNumber] <=peerControl.getNbPeersSnubbed()){
							
							DiskManagerReadRequest request = pt.request(pieceNumber, chunk.getOffset(), chunk.getLength(),false);
							
							if ( request != null ){
	
								peerControl.requestAdded( pePiece, pt, request );
								
								pePiece.setRequested(pt, chunk.getBlockNumber());
		
								pt.setLastPiece(pieceNumber);
		
								chunk.requested();
								
									// stick it at the end
								
								it.remove();
								
								endGameModeChunks.add( chunk );
															
								return( 1 );
							}
						}
					}
				}
			}
						
			if ( endGameModeChunks.isEmpty()){
				
				leaveEndGameMode();
			}
			
			return( 0 );
		}
	}

	@Override
	public final void
	removeFromEndGameModeChunks(
		final int pieceNumber,
		final int offset )
	{
		if (!endGameMode){

			return;
		}

		synchronized( endGameModeChunkLock ){

			final Iterator iter =endGameModeChunks.iterator();

			while (iter.hasNext()){

				EndGameModeChunk chunk =(EndGameModeChunk) iter.next();

				if ( chunk.equals(pieceNumber, offset)){

					iter.remove();
					
					endGameModeChunkMap.remove( new Long( ((long)(pieceNumber)) << 32 | chunk.getBlockNumber()));
				}
			}
		}
	}

	@Override
	public final void
	clearEndGameChunks()
	{
		if ( !endGameMode ){

			return;
		}

		synchronized( endGameModeChunkLock ){

			endGameModeChunks.clear();

			endGameModeChunkMap.clear();
			
			endGameMode = false;
		}
	}

	protected void
	leaveEndGameMode()
	{
		synchronized( endGameModeChunkLock ){

			if ( endGameMode ){

				if (Logger.isEnabled()){

					Logger.log(new LogEvent(diskManager.getTorrent(), LOGID, "Leaving end-game mode: "
							+peerControl.getDisplayName()));
				}

				endGameMode = false;

				endGameModeChunks.clear();

				endGameModeChunkMap.clear();
				
				timeEndGameModeEntered = 0;
			}
		}
	}

	protected void
	abandonEndGameMode()
	{
		if ( !endGameModeAbandoned ){

			synchronized( endGameModeChunkLock ){

				endGameModeAbandoned = true;

				endGameMode = false;

				clearEndGameChunks();

				if (Logger.isEnabled())
					Logger.log(new LogEvent(diskManager.getTorrent(), LOGID, "Abandoning end-game mode: "
							+peerControl.getDisplayName()));
			}
		}
	}

	private boolean
	computeProviderPriorities()
	{
		List	p_ps = priority_providers.getList();

		if ( p_ps.size() == 0 ){

			if ( provider_piece_priorities != null ){

				paramPriorityChange++;

				provider_piece_priorities = null;
			}
		}else{

			paramPriorityChange++;

			provider_piece_priorities = new long[nbPieces];

			for (int i=0;i<p_ps.size();i++){

				PiecePriorityProvider	shaper = (PiecePriorityProvider)p_ps.get(i);

				final long[] priorities = shaper.updatePriorities( this );

				if ( priorities == null ){

					continue;
				}

				for (int j=0;j<priorities.length;j++){

					long priority = priorities[j];

					if ( priority != 0 ){

						provider_piece_priorities[j] += priority;
					}
				}
			}
		}

		List	rta_ps = rta_providers.getList();

		if ( rta_ps.size() == 0 ){

			if ( provider_piece_rtas != null ){

				// coming out of real-time mode - clear down

				for (int i=0;i<pePieces.length;i++){

					PEPiece	piece = pePieces[i];

					if ( piece != null ){

						piece.setRealTimeData(null);
					}
				}

				provider_piece_rtas = null;
			}

			return( false );

		}else{

			boolean	has_rta = false;

			// prolly more efficient to reallocate than reset to 0

			provider_piece_rtas = new long[nbPieces];

			for (int i=0;i<rta_ps.size();i++){

				PieceRTAProvider	shaper = (PieceRTAProvider)rta_ps.get(i);

				final long[]	offsets = shaper.updateRTAs( this );

				if ( offsets == null ){

					continue;
				}

				for (int j=0;j<offsets.length;j++){

					long rta = offsets[j];

					if ( rta > 0 ){

						if ( provider_piece_rtas[j] == 0 ){

							provider_piece_rtas[j] = rta;

						}else{

							provider_piece_rtas[j] = Math.min( provider_piece_rtas[j], rta );
						}

						has_rta	= true;
					}
				}
			}

			return( has_rta );
		}
	}

	@Override
	public void
	addRTAProvider(
		PieceRTAProvider		provider )
	{
		rta_providers.add( provider );

		Iterator	it = listeners.iterator();

		while( it.hasNext()){

			try{
				((PiecePickerListener)it.next()).providerAdded( provider );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}

			// we don't want end-game mode kicking in and screwing with the RTA logic
			// at the end of the download. simplest way is to abandon it for this
			// download. if someone gives up RT later then the download will just complete
			// without EGM

		leaveEndGameMode();
	}

	@Override
	public void
	removeRTAProvider(
		PieceRTAProvider		provider )
	{
		rta_providers.remove( provider );

		Iterator	it = listeners.iterator();

		while( it.hasNext()){

			try{
				((PiecePickerListener)it.next()).providerRemoved( provider );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	@Override
	public List
	getRTAProviders()
	{
		return( rta_providers.getList());
	}

	@Override
	public void
	addPriorityProvider(
		PiecePriorityProvider		provider )
	{
		priority_providers.add( provider );

		Iterator	it = listeners.iterator();

		while( it.hasNext()){

			try{
				((PiecePickerListener)it.next()).providerAdded( provider );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	@Override
	public void
	removePriorityProvider(
		PiecePriorityProvider		provider )
	{
		priority_providers.remove( provider );

		Iterator	it = listeners.iterator();

		while( it.hasNext()){

			try{
				((PiecePickerListener)it.next()).providerRemoved( provider );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	@Override
	public List
	getPriorityProviders()
	{
		return( rta_providers.getList());
	}

	@Override
	public void
	addListener(
			PiecePickerListener		listener )
	{
		listeners.add( listener );

		Iterator	it = rta_providers.iterator();

		while( it.hasNext()){

			listener.providerAdded((PieceRTAProvider)it.next());
		}
	}

	@Override
	public void
	removeListener(
			PiecePickerListener		listener )
	{
		listeners.remove( listener );
	}

	/**
	 * An instance of this listener is registered with peerControl
	 * Through this, we learn of peers joining and leaving
	 * and attach/detach listeners to them
	 */
	private class PEPeerManagerListenerImpl
	extends PEPeerManagerListenerAdapter
	{
		@Override
		public final void peerAdded(final PEPeerManager manager, PEPeer peer )
		{
			PEPeerListenerImpl peerListener;
			peerListener =(PEPeerListenerImpl)peerListeners.get(peer);
			if (peerListener ==null)
			{
				peerListener =new PEPeerListenerImpl();
				peerListeners.put(peer, peerListener);
			}
			peer.addListener(peerListener);
		}

		@Override
		public final void peerRemoved(final PEPeerManager manager, PEPeer peer)
		{
			// remove this listener from list of listeners and from the peer
			final PEPeerListenerImpl peerListener =(PEPeerListenerImpl)peerListeners.remove(peer);
			peer.removeListener(peerListener);
		}
	}

	/**
	 * An instance of this listener is registered with each peer
	 */
	private class PEPeerListenerImpl
	implements PEPeerListener
	{
		@Override
		public final void stateChanged(PEPeer peer, final int newState)
		{
			/*
			switch (newState)
			{
			case PEPeer.CONNECTING:
				return;

			case PEPeer.HANDSHAKING:
				return;

			case PEPeer.TRANSFERING:
				return;

			case PEPeer.CLOSING:
				return;

			case PEPeer.DISCONNECTED:
				return;
			}
			 */
		}

		@Override
		public final void sentBadChunk(final PEPeer peer, final int piece_num, final int total_bad_chunks )
		{
			/* nothing to do here */
		}

		@Override
		public final void addAvailability(final PEPeer peer, final BitFlags peerHavePieces)
		{
			if (peerHavePieces ==null ||peerHavePieces.nbSet <=0)
				return;
			try
			{	availabilityMon.enter();
			if ( availabilityAsynch == null ){
				availabilityAsynch = (int[])availability.clone();
			}
			for (int i =peerHavePieces.start; i <=peerHavePieces.end; i++)
			{
				if ( peerHavePieces.flags[i] ){
					++availabilityAsynch[i];
				}
			}
			availabilityChange++;
			} finally {availabilityMon.exit();}
		}

		/**
		 * Takes away the given pieces from global availability
		 * @param PEPeer peer this is about
		 * @param peerHasPieces BitFlags of the pieces
		 */
		@Override
		public final void removeAvailability(final PEPeer peer, final BitFlags peerHavePieces)
		{
			if (peerHavePieces ==null ||peerHavePieces.nbSet <=0)
				return;
			try
			{	availabilityMon.enter();
			if (availabilityAsynch ==null)
			{
				availabilityAsynch = (int[])availability.clone();
			}
			for (int i =peerHavePieces.start; i <=peerHavePieces.end; i++)
			{
				if (peerHavePieces.flags[i])
				{
					if (availabilityAsynch[i] >(dmPieces[i].isDone() ?1 :0))
						--availabilityAsynch[i];
					else
						availabilityDrift++;
				}
			}
			availabilityChange++;
			} finally {availabilityMon.exit();}
		}
	}

	/**
	 * An instance of this listener is registered with peerControl
	 * @author MjrTom
	 */
	private class DiskManagerListenerImpl
	implements DiskManagerListener
	{
		@Override
		public final void stateChanged(int oldState, int newState)
		{
			//starting torrent
		}

		@Override
		public final void filePriorityChanged(DiskManagerFileInfo file)
		{
			syncFilePriorities();
			// record that user-based priorities changed
			filePriorityChange++;	// this is a user's priority change event

			// only need to re-calc Needed on file's pieces; priority is calculated seperatly
			boolean foundPieceToDownload =false;
			// if didn't have anything to do before, now only need to check if we need
			// to DL from this file, but if had something to do before,
			// must rescan all pieces to see if now nothing to do
			final int startI;
			final int endI;
			if (hasNeededUndonePiece)
			{
				startI =0;
				endI =nbPieces;
			} else
			{
				startI =file.getFirstPieceNumber();
				endI =file.getLastPieceNumber() +1;
			}
			for (int i =startI; i <endI; i++)
			{
				final DiskManagerPiece dmPiece =dmPieces[i];
				if (!dmPiece.isDone())
					foundPieceToDownload |=dmPiece.calcNeeded();
			}
			if (foundPieceToDownload ^hasNeededUndonePiece)
			{
				hasNeededUndonePiece =foundPieceToDownload;
				neededUndonePieceChange++;
			}
		}


		@Override
		public final void pieceDoneChanged(DiskManagerPiece dmPiece)
		{
			final int pieceNumber =dmPiece.getPieceNumber();
			if (dmPiece.isDone())
			{
				addHavePiece(null,pieceNumber);
				nbPiecesDone++;
				if (nbPiecesDone >=nbPieces)
					checkDownloadablePiece();
			}else
			{
				try
				{   availabilityMon.enter();
				if ( availabilityAsynch == null ){
					availabilityAsynch = (int[])availability.clone();
				}
				if (availabilityAsynch[pieceNumber] >0)
					--availabilityAsynch[pieceNumber];
				else
					availabilityDrift++;
				availabilityChange++;
				} finally {availabilityMon.exit();}
				nbPiecesDone--;
				if (dmPiece.calcNeeded() &&!hasNeededUndonePiece)
				{
					hasNeededUndonePiece =true;
					neededUndonePieceChange++;
				}
			}
		}
	}

	@Override
	public void
	setForcePiece(
		int			pieceNumber,
		boolean		forced )
	{
		if ( pieceNumber < 0 || pieceNumber >= nbPieces ){

			Debug.out( "Invalid piece number: " +pieceNumber );

			return;
		}

		synchronized( this ){

			CopyOnWriteSet<Integer>	set = forced_pieces;

			if ( set == null ){

				if ( !forced ){

					return;
				}

				set = new CopyOnWriteSet<>(false);

				forced_pieces = set;
			}

			if ( forced ){

				set.add( pieceNumber );

			}else{

				set.remove( pieceNumber );

				if ( set.size() == 0 ){

					forced_pieces = null;
				}
			}
		}
		
		Iterator<PiecePickerListener>	it = listeners.iterator();

		while( it.hasNext()){

			try{
				((PiecePickerListener)it.next()).somethingChanged( this, PiecePickerListener.THING_FORCE_PIECE, pieceNumber );

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
		
		paramPriorityChange++;

		computeBasePriorities();
	}

	@Override
	public boolean
	isForcePiece(
		int			pieceNumber )
	{
		if ( pieceNumber < 0 || pieceNumber >= nbPieces ){

			Debug.out( "Invalid piece number: " +pieceNumber );

			return( false );
		}

		CopyOnWriteSet<Integer>	set = forced_pieces;

		return( set != null && set.contains( pieceNumber ));
	}

	private void
	setSequentialDownload(
		int	val )
	{
		if ( sequentialDownload != val ) {
			
			sequentialDownload = val;
		
			filePriorityChange++;
		}
	}
	
	public void
	setSequentialAscendingFrom(
		int		start_piece )
	{
		setSequentialDownload( start_piece + 1 );
	}
	
	public void
	setSequentialDescendingFrom(
		int		start_piece )
	{
		setSequentialDownload( -(start_piece + 1 ));
	}
	
	public void
	clearSequential()
	{
		setSequentialDownload( 0 );
	}
	
	public int
	getSequentialInfo()
	{
		return( sequentialDownload );
	}
	
	public String
	getEGMInfo()
	{
		if ( endGameModeAbandoned ){
			
			return( "abandoned" );
			
		}else if ( endGameMode ){
			
			synchronized( endGameModeChunkLock ){
			
				long elapsed = SystemTime.getMonotonousTime()- timeEndGameModeEntered;
					 
				String str = "rem=" + (END_GAME_MODE_TIMEOUT-elapsed)/1000 + "s";

				str += ",chunks=" + endGameModeChunks.size();
				
				return( str );
			}
		}else{
			
			return( null );
		}
	}
	
	@Override
	public int 
	getEGMRequestCount(
		int piece_number, int block_number)
	{
		synchronized( endGameModeChunkLock ){
		
			EndGameModeChunk chunk = endGameModeChunkMap.get( new Long( ((long)piece_number) << 32 | block_number ));
			
			if ( chunk == null ){
				
				return( 0 );
			}
			
			return( chunk.getRequestCount());
		}
	}
	
	@Override
	public void
	setGlobalRequestHint(
		int	piece_number,
		int	start_bytes,
		int	byte_count )
	{
		if ( piece_number < 0 ){

			global_request_hint = null;

		}else{

			global_request_hint = new int[]{ piece_number, start_bytes, byte_count };
		}
	}

	@Override
	public int[]
	getGlobalRequestHint()
	{
		return( global_request_hint );
	}

	@Override
	public void
	setReverseBlockOrder(
		boolean		is_reverse )
	{
		reverse_block_order = is_reverse;
	}

	@Override
	public boolean
	getReverseBlockOrder()
	{
		return( reverse_block_order );
	}

	@Override
	public void
	destroy()
	{

	}

	@Override
	public String
	getPieceString(
		int	piece_number )
	{
		String	str;

		long priority = startPriorities==null?0:startPriorities[piece_number];

		if  ( priority == PRIORITY_REALTIME ){

			long[]	rta = provider_piece_rtas;

			str = "pri=rta:" + (rta==null?"?":("" + (rta[piece_number] - SystemTime.getCurrentTime())));

		}else{
			PEPiece pe_piece = pePieces[ piece_number ];

			if ( pe_piece != null ){

				priority = pe_piece.getResumePriority();
			}

			str = "pri=" + priority;
		}

		str += ",avail=" + availability[piece_number];

		long[] exts = provider_piece_priorities;

		if ( exts != null ){

			str += ",ext=" + exts[piece_number];
		}

		CopyOnWriteSet<Integer>	forced = forced_pieces;

		if ( forced != null && forced.contains( piece_number )){

			str += ", forced";
		}

		return( str );
	}

	@Override
	public void
	generateEvidence(
			IndentWriter	writer )
	{
		writer.println( "Piece Picker" );

		try{
			writer.indent();

			writer.println( "globalAvail: " + globalAvail );
			writer.println( "globalAvgAvail: " + globalAvgAvail );
			writer.println( "nbRarestActive: " + nbRarestActive );
			writer.println( "globalMin: " + globalMin );
			writer.println( "globalMinOthers: " + globalMinOthers );
			writer.println( "hasNeededUndonePiece: " + hasNeededUndonePiece );
			writer.println( "endGameMode: " + endGameMode );
			writer.println( "endGameModeAbandoned: " + endGameModeAbandoned );
			writer.println( "endGameModeChunks: " + endGameModeChunks );

		}finally{

			writer.exdent();
		}
	}

	protected static class
	RealTimeData
	{
		private final List[]	peer_requests;

		protected
		RealTimeData(
				PEPiece		piece )
		{
			int	nb = piece.getNbBlocks();

			peer_requests 	= new List[nb];

			for (int i=0;i<peer_requests.length;i++){
				peer_requests[i] = new ArrayList(1);
			}
		}

		public final List[]
		                  getRequests()
		{
			return( peer_requests );
		}
	}

	private static class
	RealTimePeerRequest
	{
		private final PEPeerTransport			peer;
		private final DiskManagerReadRequest	request;

		protected
		RealTimePeerRequest(
				PEPeerTransport			_peer,
				DiskManagerReadRequest	_request )
		{
			peer		= _peer;
			request		= _request;
		}

		protected PEPeerTransport
		getPeer()
		{
			return( peer );
		}

		protected DiskManagerReadRequest
		getRequest()
		{
			return( request );
		}
	}
}
